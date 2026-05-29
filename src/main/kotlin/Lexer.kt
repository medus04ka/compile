class Lexer(source: String?) {
    private val text = source.orEmpty()
    private var index = 0
    private var line = 1
    private var column = 1

    companion object {
        private val keywords = mapOf(
            "var" to TokenType.VAR,
            "print" to TokenType.PRINT,
            "if" to TokenType.IF,
            "else" to TokenType.ELSE,
            "while" to TokenType.WHILE,
            "true" to TokenType.TRUE,
            "false" to TokenType.FALSE,
            "fun" to TokenType.FUN,
            "return" to TokenType.RETURN
        )

        private val symbols = mapOf(
            "==" to TokenType.EQEQ,
            "!=" to TokenType.NEQ,
            "<=" to TokenType.LTEQ,
            ">=" to TokenType.GTEQ,
            "&&" to TokenType.AND,
            "||" to TokenType.OR,
            "+" to TokenType.PLUS,
            "-" to TokenType.MINUS,
            "*" to TokenType.STAR,
            "/" to TokenType.SLASH,
            "=" to TokenType.EQ,
            "<" to TokenType.LT,
            ">" to TokenType.GT,
            "!" to TokenType.EXCL,
            "(" to TokenType.LPAREN,
            ")" to TokenType.RPAREN,
            "{" to TokenType.LBRACE,
            "}" to TokenType.RBRACE,
            "[" to TokenType.LBRACKET,
            "]" to TokenType.RBRACKET,
            ";" to TokenType.SEMICOLON,
            "," to TokenType.COMMA,
            ":" to TokenType.COLON
        )
    }

    fun tokenize(): Sequence<Token> = sequence {
        while (!finished()) {
            skipSpacesAndComments()
            if (finished()) break

            val token = when {
                peek() == '"' -> readString()
                peek().isDigit() -> readNumber()
                peek().isIdentifierStart() -> readIdentifier()
                else -> readSymbol()
            }

            yield(token)
        }

        yield(Token(TokenType.EOF, "\u0000", index, line, column))
    }

    private fun skipSpacesAndComments() {
        while (!finished()) {
            when {
                peek().isWhitespace() -> advance()

                peek() == '/' && peekNext() == '/' -> {
                    while (!finished() && peek() != '\n') advance()
                }

                peek() == '/' && peekNext() == '*' -> skipBlockComment()

                else -> return
            }
        }
    }

    private fun skipBlockComment() {
        val startLine = line
        val startColumn = column

        advance()
        advance()

        while (!finished()) {
            if (peek() == '*' && peekNext() == '/') {
                advance()
                advance()
                return
            }
            advance()
        }

        throw LexerError(
            "Ошибка лексера: незакрытый многострочный комментарий " +
                    "на строке $startLine, колонке $startColumn."
        )
    }

    private fun readString(): Token {
        val startIndex = index
        val startLine = line
        val startColumn = column
        val result = StringBuilder()

        advance()

        while (!finished() && peek() != '"') {
            if (peek() == '\\') {
                advance()
                if (finished()) {
                    throw LexerError(
                        "Ошибка лексера: незавершённая escape-последовательность " +
                                "на строке $line, колонке $column."
                    )
                }

                val escaped = when (val symbol = advance()) {
                    'n' -> '\n'
                    't' -> '\t'
                    'r' -> '\r'
                    '"' -> '"'
                    '\\' -> '\\'
                    else -> throw LexerError(
                        "Ошибка лексера: неизвестная escape-последовательность \\$symbol " +
                                "на строке $line, колонке $column."
                    )
                }

                result.append(escaped)
            } else {
                result.append(advance())
            }
        }

        if (finished()) {
            throw LexerError(
                "Ошибка лексера: незакрытая строка " +
                        "на строке $startLine, колонке $startColumn."
            )
        }

        advance()
        return Token(TokenType.STRING, result.toString(), startIndex, startLine, startColumn)
    }

    private fun readNumber(): Token {
        val startIndex = index
        val startLine = line
        val startColumn = column

        while (!finished() && peek().isDigit()) advance()

        if (!finished() && peek() == '.' && peekNext().isDigit()) {
            advance()
            while (!finished() && peek().isDigit()) advance()
        }

        return Token(
            TokenType.NUMBER,
            text.substring(startIndex, index),
            startIndex,
            startLine,
            startColumn
        )
    }

    private fun readIdentifier(): Token {
        val startIndex = index
        val startLine = line
        val startColumn = column

        while (!finished() && peek().isIdentifierPart()) advance()

        val word = text.substring(startIndex, index)
        return Token(keywords[word] ?: TokenType.ID, word, startIndex, startLine, startColumn)
    }

    private fun readSymbol(): Token {
        val startIndex = index
        val startLine = line
        val startColumn = column

        val twoChars = if (index + 1 < text.length) text.substring(index, index + 2) else ""
        symbols[twoChars]?.let { type ->
            advance()
            advance()
            return Token(type, twoChars, startIndex, startLine, startColumn)
        }

        val oneChar = peek().toString()
        symbols[oneChar]?.let { type ->
            advance()
            return Token(type, oneChar, startIndex, startLine, startColumn)
        }

        throw LexerError(
            "Ошибка лексера: неожиданный символ '${peek()}' " +
                    "на строке $startLine, колонке $startColumn."
        )
    }

    private fun finished(): Boolean = index >= text.length

    private fun peek(): Char = if (finished()) '\u0000' else text[index]

    private fun peekNext(): Char = if (index + 1 >= text.length) '\u0000' else text[index + 1]

    private fun advance(): Char {
        if (finished()) return '\u0000'

        val current = text[index]
        index++

        if (current == '\n') {
            line++
            column = 1
        } else {
            column++
        }

        return current
    }

    private fun Char.isIdentifierStart(): Boolean = this == '_' || isLetter()

    private fun Char.isIdentifierPart(): Boolean = this == '_' || isLetterOrDigit()
}
