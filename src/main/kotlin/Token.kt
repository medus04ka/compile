data class Token(var type: TokenType, var value: String, var position: Int, val line: Int, val column: Int) {
}