class Parser(tokens: Sequence<Token>) {
    private val tokens: List<Token> = tokens.toList()
    private var position = 0

    fun parse(): List<Statement> {
        val result = mutableListOf<Statement>()

        while (!isAtEnd()) {
            result.add(parseDeclaration())
        }

        return result
    }

    private fun parseDeclaration(): Statement {
        return when {
            match(TokenType.FUN) -> parseFunctionDeclaration()
            match(TokenType.VAR) -> parseVarDeclaration()
            else -> parseStatement()
        }
    }

    private fun parseStatement(): Statement {
        return when {
            match(TokenType.IF) -> parseIfStatement()
            match(TokenType.WHILE) -> parseWhileStatement()
            match(TokenType.RETURN) -> parseReturnStatement()
            match(TokenType.PRINT) -> parsePrintStatement()
            match(TokenType.LBRACE) -> Statement.BlockStatement(parseBlock())
            else -> parseExpressionStatement()
        }
    }

    private fun parseFunctionDeclaration(): Statement.FunctionStatement {
        val name = consume(TokenType.ID, "Ожидается имя функции.")
        consume(TokenType.LPAREN, "Ожидается '(' после имени функции.")

        val params = mutableListOf<Statement.Parameter>()
        if (!check(TokenType.RPAREN)) {
            do {
                val paramName = consume(TokenType.ID, "Ожидается имя параметра.")
                consume(TokenType.COLON, "Ожидается ':' после имени параметра.")
                params.add(Statement.Parameter(paramName.value, parseType()))
            } while (match(TokenType.COMMA))
        }

        consume(TokenType.RPAREN, "Ожидается ')' после списка параметров.")

        val returnType = if (match(TokenType.COLON)) parseType() else Type.Null

        consume(TokenType.LBRACE, "Ожидается '{' перед телом функции.")
        return Statement.FunctionStatement(name.value, params, returnType, parseBlock())
    }

    private fun parseType(): Type {
        val name = consume(TokenType.ID, "Ожидается имя типа.")

        return when (name.value) {
            "Number" -> Type.Number
            "String" -> Type.String
            "Boolean" -> Type.Boolean
            "Array" -> {
                consume(TokenType.LT, "Ожидается '<' после Array.")
                val elementType = parseType()
                consume(TokenType.GT, "Ожидается '>' после типа элемента массива.")
                Type.Array(elementType)
            }
            else -> throw ParserError(
                "Ошибка парсера: строка ${name.line}, колонка ${name.column}: " +
                        "неизвестный тип '${name.value}'."
            )
        }
    }

    private fun parseVarDeclaration(): Statement.VarStatement {
        val name = consume(TokenType.ID, "Ожидается имя переменной.")
        val initializer = if (match(TokenType.EQ)) parseExpression() else null

        consume(TokenType.SEMICOLON, "Ожидается ';' после объявления переменной.")
        return Statement.VarStatement(name.value, initializer)
    }

    private fun parseIfStatement(): Statement.IfStatement {
        consume(TokenType.LPAREN, "Ожидается '(' после 'if'.")
        val condition = parseExpression()
        consume(TokenType.RPAREN, "Ожидается ')' после условия 'if'.")

        val thenBranch = parseStatement()
        val elseBranch = if (match(TokenType.ELSE)) parseStatement() else null

        return Statement.IfStatement(condition, thenBranch, elseBranch)
    }

    private fun parseWhileStatement(): Statement.WhileStatement {
        consume(TokenType.LPAREN, "Ожидается '(' после 'while'.")
        val condition = parseExpression()
        consume(TokenType.RPAREN, "Ожидается ')' после условия 'while'.")

        return Statement.WhileStatement(condition, parseStatement())
    }

    private fun parseReturnStatement(): Statement.ReturnStatement {
        val value = if (!check(TokenType.SEMICOLON)) parseExpression() else null
        consume(TokenType.SEMICOLON, "Ожидается ';' после 'return'.")
        return Statement.ReturnStatement(value)
    }

    private fun parsePrintStatement(): Statement.PrintStatement {
        consume(TokenType.LPAREN, "Ожидается '(' после 'print'.")
        val expression = parseExpression()
        consume(TokenType.RPAREN, "Ожидается ')' после выражения в 'print'.")
        consume(TokenType.SEMICOLON, "Ожидается ';' после 'print(...)'.")

        return Statement.PrintStatement(expression)
    }

    private fun parseExpressionStatement(): Statement.ExpressionStatement {
        val expression = parseExpression()
        consume(TokenType.SEMICOLON, "Ожидается ';' после выражения.")
        return Statement.ExpressionStatement(expression)
    }

    private fun parseBlock(): List<Statement> {
        val statements = mutableListOf<Statement>()

        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statements.add(parseDeclaration())
        }

        consume(TokenType.RBRACE, "Ожидается '}' после блока.")
        return statements
    }

    private fun parseExpression(): Expression = parseAssignment()

    private fun parseAssignment(): Expression {
        val target = parseLogicalOr()

        if (!match(TokenType.EQ)) {
            return target
        }

        val equals = previous()
        val value = parseAssignment()

        return when (target) {
            is Expression.VariableExpression ->
                Expression.AssignExpression(target.name, value)

            is Expression.IndexExpression ->
                Expression.IndexAssignExpression(target.array, target.index, value)

            else -> throw ParserError(
                "Ошибка парсера: строка ${equals.line}, колонка ${equals.column}: " +
                        "левая часть не может быть целью присваивания."
            )
        }
    }

    private fun parseLogicalOr(): Expression {
        var expression = parseLogicalAnd()

        while (match(TokenType.OR)) {
            expression = Expression.BinaryExpression(expression, previous().type, parseLogicalAnd())
        }

        return expression
    }

    private fun parseLogicalAnd(): Expression {
        var expression = parseEquality()

        while (match(TokenType.AND)) {
            expression = Expression.BinaryExpression(expression, previous().type, parseEquality())
        }

        return expression
    }

    private fun parseEquality(): Expression {
        var expression = parseComparison()

        while (match(TokenType.EQEQ, TokenType.NEQ)) {
            expression = Expression.BinaryExpression(expression, previous().type, parseComparison())
        }

        return expression
    }

    private fun parseComparison(): Expression {
        var expression = parseTerm()

        while (match(TokenType.LT, TokenType.LTEQ, TokenType.GT, TokenType.GTEQ)) {
            expression = Expression.BinaryExpression(expression, previous().type, parseTerm())
        }

        return expression
    }

    private fun parseTerm(): Expression {
        var expression = parseFactor()

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            expression = Expression.BinaryExpression(expression, previous().type, parseFactor())
        }

        return expression
    }

    private fun parseFactor(): Expression {
        var expression = parseUnary()

        while (match(TokenType.STAR, TokenType.SLASH)) {
            expression = Expression.BinaryExpression(expression, previous().type, parseUnary())
        }

        return expression
    }

    private fun parseUnary(): Expression {
        if (match(TokenType.EXCL, TokenType.MINUS)) {
            return Expression.UnaryExpression(previous().type, parseUnary())
        }

        return parseCall()
    }

    private fun parseCall(): Expression {
        var expression = parsePrimary()

        while (true) {
            expression = when {
                match(TokenType.LPAREN) -> finishCall(expression)
                match(TokenType.LBRACKET) -> finishIndex(expression)
                else -> return expression
            }
        }
    }

    private fun finishCall(callee: Expression): Expression.CallExpression {
        val arguments = mutableListOf<Expression>()

        if (!check(TokenType.RPAREN)) {
            do {
                arguments.add(parseExpression())
            } while (match(TokenType.COMMA))
        }

        consume(TokenType.RPAREN, "Ожидается ')' после аргументов вызова.")
        return Expression.CallExpression(callee, arguments)
    }

    private fun finishIndex(array: Expression): Expression.IndexExpression {
        val index = parseExpression()
        consume(TokenType.RBRACKET, "Ожидается ']' после индекса.")
        return Expression.IndexExpression(array, index)
    }

    private fun parsePrimary(): Expression {
        if (match(TokenType.NUMBER)) {
            return Expression.NumberExpression(previous().value.toDouble())
        }

        if (match(TokenType.STRING)) {
            return Expression.StringExpression(previous().value)
        }

        if (match(TokenType.TRUE)) return Expression.BooleanExpression(true)
        if (match(TokenType.FALSE)) return Expression.BooleanExpression(false)

        if (match(TokenType.ID)) {
            return Expression.VariableExpression(previous().value)
        }

        if (match(TokenType.LPAREN)) {
            val expression = parseExpression()
            consume(TokenType.RPAREN, "Ожидается ')' после выражения.")
            return expression
        }

        if (match(TokenType.LBRACKET)) {
            val elements = mutableListOf<Expression>()

            if (!check(TokenType.RBRACKET)) {
                do {
                    elements.add(parseExpression())
                } while (match(TokenType.COMMA))
            }

            consume(TokenType.RBRACKET, "Ожидается ']' после элементов массива.")
            return Expression.ArrayExpression(elements)
        }

        val token = peek()
        throw ParserError(
            "Ошибка парсера: строка ${token.line}, колонка ${token.column}: ожидается выражение."
        )
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean = !isAtEnd() && peek().type == type

    private fun advance(): Token {
        if (!isAtEnd()) position++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens[position]

    private fun previous(): Token = tokens[position - 1]

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()

        val token = peek()
        throw ParserError(
            "Ошибка парсера: строка ${token.line}, колонка ${token.column}: $message"
        )
    }
}
