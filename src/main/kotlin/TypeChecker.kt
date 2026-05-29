class TypeEnvironment(private val parent: TypeEnvironment? = null) {
    private val values = mutableMapOf<String, Type>()

    fun define(name: String, type: Type) {
        values[name] = type
    }

    fun get(name: String): Type =
        values[name] ?: parent?.get(name)
        ?: throw TypeCheckError("Ошибка типов: переменная '$name' не объявлена.")

    fun assign(name: String, newType: Type) {
        if (values.containsKey(name)) {
            val oldType = values.getValue(name)

            if (oldType == Type.Null) {
                values[name] = newType
                return
            }

            if (oldType != newType) {
                throw TypeCheckError(
                    "Ошибка типов: переменная '$name' имеет тип $oldType, " +
                            "нельзя присвоить значение типа $newType."
                )
            }

            return
        }

        parent?.assign(name, newType) ?: throw TypeCheckError(
            "Ошибка типов: переменная '$name' не объявлена."
        )
    }
}

class TypeChecker {
    private var env = TypeEnvironment()
    private var currentReturnType: Type? = null

    fun check(statements: List<Statement>) {
        registerFunctions(statements)
        statements.forEach { checkStatement(it) }
    }

    private fun registerFunctions(statements: List<Statement>) {
        for (statement in statements) {
            if (statement is Statement.FunctionStatement) {
                env.define(statement.name, functionTypeOf(statement))
            }
        }
    }

    private fun functionTypeOf(statement: Statement.FunctionStatement): Type.Function =
        Type.Function(statement.params.map { it.type }, statement.returnType)

    private fun checkStatement(statement: Statement) {
        when (statement) {
            is Statement.ExpressionStatement -> inferType(statement.expression)
            is Statement.PrintStatement -> inferType(statement.expression)

            is Statement.VarStatement -> {
                val type = statement.initializer?.let { inferType(it) } ?: Type.Null
                env.define(statement.name, type)
            }

            is Statement.BlockStatement -> inNestedScope {
                statement.statements.forEach { checkStatement(it) }
            }

            is Statement.IfStatement -> {
                requireType(inferType(statement.condition), Type.Boolean, "условие 'if'")
                checkStatement(statement.thenBranch)
                statement.elseBranch?.let { checkStatement(it) }
            }

            is Statement.WhileStatement -> {
                requireType(inferType(statement.condition), Type.Boolean, "условие 'while'")
                checkStatement(statement.body)
            }

            is Statement.FunctionStatement -> checkFunction(statement)

            is Statement.ReturnStatement -> checkReturn(statement)
        }
    }

    private fun checkFunction(statement: Statement.FunctionStatement) {
        env.define(statement.name, functionTypeOf(statement))

        val previousReturnType = currentReturnType
        currentReturnType = statement.returnType

        inNestedScope {
            statement.params.forEach { param -> env.define(param.name, param.type) }
            statement.body.forEach { checkStatement(it) }
        }

        currentReturnType = previousReturnType
    }

    private fun checkReturn(statement: Statement.ReturnStatement) {
        val expected = currentReturnType
            ?: throw TypeCheckError("Ошибка типов: 'return' вне тела функции.")

        val actual = statement.value?.let { inferType(it) } ?: Type.Null

        if (actual != expected) {
            throw TypeCheckError(
                "Ошибка типов: 'return' должен возвращать $expected, получено $actual."
            )
        }
    }

    fun inferType(expression: Expression): Type {
        return when (expression) {
            is Expression.NumberExpression -> Type.Number
            is Expression.StringExpression -> Type.String
            is Expression.BooleanExpression -> Type.Boolean
            is Expression.VariableExpression -> env.get(expression.name)

            is Expression.AssignExpression -> {
                val valueType = inferType(expression.value)
                env.assign(expression.name, valueType)
                valueType
            }

            is Expression.CallExpression -> inferCallType(expression)
            is Expression.ArrayExpression -> inferArrayType(expression)
            is Expression.IndexExpression -> inferIndexType(expression)
            is Expression.IndexAssignExpression -> inferIndexAssignType(expression)
            is Expression.UnaryExpression -> inferUnaryType(expression)
            is Expression.BinaryExpression -> inferBinaryType(expression)
        }
    }

    private fun inferCallType(expression: Expression.CallExpression): Type {
        val calleeType = inferType(expression.callee)

        if (calleeType !is Type.Function) {
            throw TypeCheckError(
                "Ошибка типов: вызываемое значение не является функцией, получен тип $calleeType."
            )
        }

        if (expression.arguments.size != calleeType.params.size) {
            throw TypeCheckError(
                "Ошибка типов: ожидается ${calleeType.params.size} аргументов, " +
                        "получено ${expression.arguments.size}."
            )
        }

        for (i in expression.arguments.indices) {
            val actual = inferType(expression.arguments[i])
            val expected = calleeType.params[i]

            if (actual != expected) {
                throw TypeCheckError(
                    "Ошибка типов: аргумент ${i + 1} должен быть $expected, получено $actual."
                )
            }
        }

        return calleeType.returnType
    }

    private fun inferArrayType(expression: Expression.ArrayExpression): Type {
        if (expression.elements.isEmpty()) {
            throw TypeCheckError("Ошибка типов: невозможно вывести тип пустого массива.")
        }

        val elementType = inferType(expression.elements.first())

        for (element in expression.elements.drop(1)) {
            val currentType = inferType(element)
            if (currentType != elementType) {
                throw TypeCheckError(
                    "Ошибка типов: элементы массива должны быть одного типа, " +
                            "ожидался $elementType, получен $currentType."
                )
            }
        }

        return Type.Array(elementType)
    }

    private fun inferIndexType(expression: Expression.IndexExpression): Type {
        val arrayType = inferType(expression.array)

        if (arrayType !is Type.Array) {
            throw TypeCheckError(
                "Ошибка типов: индексация применима только к массиву, получен $arrayType."
            )
        }

        requireType(inferType(expression.index), Type.Number, "индекс массива")
        return arrayType.elementType
    }

    private fun inferIndexAssignType(expression: Expression.IndexAssignExpression): Type {
        val arrayType = inferType(expression.array)

        if (arrayType !is Type.Array) {
            throw TypeCheckError(
                "Ошибка типов: индексное присваивание применимо только к массиву, получен $arrayType."
            )
        }

        requireType(inferType(expression.index), Type.Number, "индекс массива")

        val valueType = inferType(expression.value)
        if (valueType != arrayType.elementType) {
            throw TypeCheckError(
                "Ошибка типов: нельзя присвоить $valueType элементу массива типа ${arrayType.elementType}."
            )
        }

        return valueType
    }

    private fun inferUnaryType(expression: Expression.UnaryExpression): Type {
        val rightType = inferType(expression.right)

        return when (expression.operator) {
            TokenType.MINUS -> {
                requireType(rightType, Type.Number, "оператор '-'")
                Type.Number
            }

            TokenType.EXCL -> {
                requireType(rightType, Type.Boolean, "оператор '!'")
                Type.Boolean
            }

            else -> throw TypeCheckError(
                "Ошибка типов: неизвестный унарный оператор ${expression.operator}."
            )
        }
    }

    private fun inferBinaryType(expression: Expression.BinaryExpression): Type {
        val leftType = inferType(expression.left)
        val rightType = inferType(expression.right)

        return when (expression.operator) {
            TokenType.PLUS -> when {
                leftType == Type.Number && rightType == Type.Number -> Type.Number
                leftType == Type.String && rightType == Type.String -> Type.String
                else -> throw TypeCheckError("Ошибка типов: нельзя применить '+' к $leftType и $rightType.")
            }

            TokenType.MINUS,
            TokenType.STAR,
            TokenType.SLASH -> {
                requireNumberPair(leftType, rightType, "арифметический оператор")
                Type.Number
            }

            TokenType.LT,
            TokenType.LTEQ,
            TokenType.GT,
            TokenType.GTEQ -> {
                requireNumberPair(leftType, rightType, "оператор сравнения")
                Type.Boolean
            }

            TokenType.EQEQ,
            TokenType.NEQ -> {
                if (leftType != rightType) {
                    throw TypeCheckError("Ошибка типов: нельзя сравнивать $leftType и $rightType.")
                }
                Type.Boolean
            }

            TokenType.AND,
            TokenType.OR -> {
                requireType(leftType, Type.Boolean, "левый операнд логического оператора")
                requireType(rightType, Type.Boolean, "правый операнд логического оператора")
                Type.Boolean
            }

            else -> throw TypeCheckError("Ошибка типов: неизвестный оператор ${expression.operator}.")
        }
    }

    private fun requireNumberPair(left: Type, right: Type, place: String) {
        if (left != Type.Number || right != Type.Number) {
            throw TypeCheckError(
                "Ошибка типов: $place требует Number и Number, получено $left и $right."
            )
        }
    }

    private fun requireType(actual: Type, expected: Type, place: String) {
        if (actual != expected) {
            throw TypeCheckError(
                "Ошибка типов: $place должен иметь тип $expected, получено $actual."
            )
        }
    }

    private inline fun inNestedScope(action: () -> Unit) {
        val previous = env
        env = TypeEnvironment(previous)
        try {
            action()
        } finally {
            env = previous
        }
    }
}
