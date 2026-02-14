fun main() {
    val example: String = "x = 5"
    val lexer: Lexer = Lexer(example)
    println(lexer.tokenize())
}