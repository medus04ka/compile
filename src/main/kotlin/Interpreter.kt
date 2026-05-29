class Interpreter {
    private var env = Environment()

    fun interpret(statements: List<Statement>) {
        registerFunctions(statements)
        statements.forEach { execute(it) }
    }

    private fun registerFunctions(statements: List<Statement>) {
        for (statement in statements) {
            if (statement is Statement.FunctionStatement) {
                env.define(statement.name, RuntimeValue.FunctionVal(statement, env))
            }
        }
    }

    private fun execute(statement: Statement) {
        when (statement) {
            is Statement.ExpressionStatement -> evaluate(statement.expression)
            is Statement.PrintStatement -> println(evaluate(statement.expression))

            is Statement.VarStatement -> {
                val value = statement.initializer?.let { evaluate(it) } ?: RuntimeValue.Null
                env.define(statement.name, value)
            }

            is Statement.BlockStatement -> executeBlock(statement.statements, Environment(env))

            is Statement.IfStatement -> {
                if (isTruthy(evaluate(statement.condition))) {
                    execute(statement.thenBranch)
                } else {
                    statement.elseBranch?.let { execute(it) }
                }
            }

            is Statement.WhileStatement -> {
                while (isTruthy(evaluate(statement.condition))) {
                    execute(statement.body)
                }
            }

            is Statement.FunctionStatement -> {
                env.define(statement.name, RuntimeValue.FunctionVal(statement, env))
            }

            is Statement.ReturnStatement -> {
                val value = statement.value?.let { evaluate(it) } ?: RuntimeValue.Null
                throw ReturnSignal(value)
            }
        }
    }

    private fun executeBlock(statements: List<Statement>, blockEnv: Environment) {
        val previous = env
        env = blockEnv

        try {
            statements.forEach { execute(it) }
        } finally {
            env = previous
        }
    }

    private fun evaluate(expression: Expression): RuntimeValue {
        return when (expression) {
            is Expression.NumberExpression -> RuntimeValue.Number(expression.value)
            is Expression.StringExpression -> RuntimeValue.StringVal(expression.value)
            is Expression.BooleanExpression -> RuntimeValue.Boolean(expression.value)
            is Expression.VariableExpression -> env.get(expression.name)

            is Expression.AssignExpression -> {
                val value = evaluate(expression.value)
                env.assign(expression.name, value)
                value
            }

            is Expression.UnaryExpression -> evaluateUnary(expression)
            is Expression.BinaryExpression -> evaluateBinary(expression)
            is Expression.CallExpression -> evaluateCall(expression)

            is Expression.ArrayExpression -> RuntimeValue.ArrayVal(
                expression.elements.map { evaluate(it) }.toMutableList()
            )

            is Expression.IndexExpression -> {
                val array = requireArray(evaluate(expression.array))
                array.elements[checkedIndex(array, expression.index)]
            }

            is Expression.IndexAssignExpression -> {
                val array = requireArray(evaluate(expression.array))
                val index = checkedIndex(array, expression.index)
                val value = evaluate(expression.value)
                array.elements[index] = value
                value
            }
        }
    }

    private fun evaluateUnary(expression: Expression.UnaryExpression): RuntimeValue {
        val right = evaluate(expression.right)

        return when (expression.operator) {
            TokenType.MINUS -> RuntimeValue.Number(-requireNumber(right))
            TokenType.EXCL -> RuntimeValue.Boolean(!requireBoolean(right))
            else -> throw RuntimeLanguageError("Ошибка выполнения: неизвестный унарный оператор ${expression.operator}.")
        }
    }

    private fun evaluateBinary(expression: Expression.BinaryExpression): RuntimeValue {
        if (expression.operator == TokenType.AND) {
            val left = requireBoolean(evaluate(expression.left))
            if (!left) return RuntimeValue.Boolean(false)
            return RuntimeValue.Boolean(requireBoolean(evaluate(expression.right)))
        }

        if (expression.operator == TokenType.OR) {
            val left = requireBoolean(evaluate(expression.left))
            if (left) return RuntimeValue.Boolean(true)
            return RuntimeValue.Boolean(requireBoolean(evaluate(expression.right)))
        }

        val left = evaluate(expression.left)
        val right = evaluate(expression.right)

        return when (expression.operator) {
            TokenType.PLUS -> plus(left, right)
            TokenType.MINUS -> RuntimeValue.Number(requireNumber(left) - requireNumber(right))
            TokenType.STAR -> RuntimeValue.Number(requireNumber(left) * requireNumber(right))

            TokenType.SLASH -> {
                val divisor = requireNumber(right)
                if (divisor == 0.0) {
                    throw RuntimeLanguageError("Ошибка выполнения: деление на ноль.")
                }
                RuntimeValue.Number(requireNumber(left) / divisor)
            }

            TokenType.LT -> RuntimeValue.Boolean(requireNumber(left) < requireNumber(right))
            TokenType.LTEQ -> RuntimeValue.Boolean(requireNumber(left) <= requireNumber(right))
            TokenType.GT -> RuntimeValue.Boolean(requireNumber(left) > requireNumber(right))
            TokenType.GTEQ -> RuntimeValue.Boolean(requireNumber(left) >= requireNumber(right))
            TokenType.EQEQ -> RuntimeValue.Boolean(left == right)
            TokenType.NEQ -> RuntimeValue.Boolean(left != right)

            else -> throw RuntimeLanguageError("Ошибка выполнения: неизвестный оператор ${expression.operator}.")
        }
    }

    private fun plus(left: RuntimeValue, right: RuntimeValue): RuntimeValue {
        return when {
            left is RuntimeValue.Number && right is RuntimeValue.Number ->
                RuntimeValue.Number(left.value + right.value)

            left is RuntimeValue.StringVal && right is RuntimeValue.StringVal ->
                RuntimeValue.StringVal(left.value + right.value)

            else -> throw RuntimeLanguageError(
                "Ошибка выполнения: нельзя применить '+' к ${left::class.simpleName} и ${right::class.simpleName}."
            )
        }
    }

    private fun evaluateCall(expression: Expression.CallExpression): RuntimeValue {
        val callee = evaluate(expression.callee)

        if (callee !is RuntimeValue.FunctionVal) {
            throw RuntimeLanguageError("Ошибка выполнения: вызываемое значение не является функцией.")
        }

        val function = callee.declaration
        val arguments = expression.arguments.map { evaluate(it) }

        if (arguments.size != function.params.size) {
            throw RuntimeLanguageError(
                "Ошибка выполнения: функция '${function.name}' ожидает ${function.params.size} аргументов, " +
                        "получено ${arguments.size}."
            )
        }

        val functionEnv = Environment(callee.closure)
        for (i in function.params.indices) {
            functionEnv.define(function.params[i].name, arguments[i])
        }

        return try {
            executeBlock(function.body, functionEnv)
            RuntimeValue.Null
        } catch (returnSignal: ReturnSignal) {
            returnSignal.value
        }
    }

    private fun requireArray(value: RuntimeValue): RuntimeValue.ArrayVal =
        value as? RuntimeValue.ArrayVal
            ?: throw RuntimeLanguageError("Ошибка выполнения: значение не является массивом.")

    private fun checkedIndex(array: RuntimeValue.ArrayVal, indexExpression: Expression): Int {
        val rawIndex = requireNumber(evaluate(indexExpression))
        val index = rawIndex.toInt()

        if (rawIndex != index.toDouble()) {
            throw RuntimeLanguageError("Ошибка выполнения: индекс должен быть целым, получено $rawIndex.")
        }

        if (index < 0 || index >= array.elements.size) {
            throw RuntimeLanguageError(
                "Ошибка выполнения: индекс $index вне границ массива [0, ${array.elements.size})."
            )
        }

        return index
    }

    private fun requireNumber(value: RuntimeValue): Double =
        (value as? RuntimeValue.Number)?.value
            ?: throw RuntimeLanguageError("Ошибка выполнения: ожидалось число, получено ${value::class.simpleName}.")

    private fun requireBoolean(value: RuntimeValue): Boolean =
        (value as? RuntimeValue.Boolean)?.value
            ?: throw RuntimeLanguageError("Ошибка выполнения: ожидалось Boolean, получено ${value::class.simpleName}.")

    private fun isTruthy(value: RuntimeValue): Boolean {
        return when (value) {
            is RuntimeValue.Boolean -> value.value
            is RuntimeValue.Number -> value.value != 0.0
            RuntimeValue.Null -> false
            else -> true
        }
    }
}
