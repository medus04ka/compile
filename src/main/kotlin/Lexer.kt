import org.example.Token
import org.example.TokenType

class Lexer {
    private lateinit var input: String
    private val length: Int
    private var position: Int

    constructor(input: String) {
        this.input = input
        this.length = input.length
        this.position = 0
    }

    fun tokenize(): Iterable<Token> {
        val result: MutableList<Token> = mutableListOf()

        while (position < length) {
            var current = peek(input)

            if (current.isWhitespace()) {
                next()
                continue
            } else if (current.isDigit()) {
                tokenizeNumber(result)
                continue
            } else if (current.isLetter()) {
                tokenizeWord(result)
                continue
            } else {
                tokenizeWord(result)
                continue
            }
        }
        return result
    }

    private fun tokenizeNumber(result: MutableList<Token>) {

        var start = position;

        while (peek(input).isLetterOrDigit()) next()

        var number = input.substring(start, position)

        addToken(result, TokenType.NUMBER, number.toString(), start)


    }

    private fun peek(input: String): Char {
        if (position >= input.length) {
            return '\u0000'
        }
        return input[position]
    }

    private fun next(): Char {
        if (position >= input.length) {
            return '\u0000'
        }
        return input[position++]
    }

    private fun tokenizeWord(result: MutableList<Token>) {
        var start = position
        while (peek(input).isLetterOrDigit()) next()

        var word = input.substring(start, position+1)

        when (word) {
            "+" -> {
                next()
                addToken(result, TokenType.PLUS, "+", start)
            }
            "-" -> {
                next()
                addToken(result, TokenType.MINUS, "-", start)
            }
            "*" -> {
                next()
                addToken(result, TokenType.STAR, "*", start)
            }
            "/" -> {
                next()
                addToken(result, TokenType.SLASH, "/", start)
            }
            "=" -> {
                next()
                addToken(result, TokenType.EQ, "=", start)
            }
            "if" -> {
                next()
                addToken(result, TokenType.IF, "if", start)
            }
            "else" -> {
                next()
                addToken(result, TokenType.ELSE, "else", start)
            }
        }

    }

    private fun addToken(result: MutableList<Token>, type: TokenType, value: String, start: Int) {
        result.add(Token(type, value,start))
    }

}
