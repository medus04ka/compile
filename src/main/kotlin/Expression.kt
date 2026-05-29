sealed class Expression {
    data class NumberExpression(val value: Double) : Expression()

    data class StringExpression(val value: String) : Expression()

    data class BooleanExpression(val value: Boolean) : Expression()

    data class VariableExpression(val name: String) : Expression()

    data class BinaryExpression(
        val left: Expression,
        val operator: TokenType,
        val right: Expression
    ) : Expression()

    data class UnaryExpression(
        val operator: TokenType,
        val right: Expression
    ) : Expression()

    data class AssignExpression(
        val name: String,
        val value: Expression
    ) : Expression()

    data class CallExpression(
        val callee: Expression,
        val arguments: List<Expression>
    ) : Expression()

    data class ArrayExpression(val elements: List<Expression>) : Expression()

    data class IndexExpression(
        val array: Expression,
        val index: Expression
    ) : Expression()

    data class IndexAssignExpression(
        val array: Expression,
        val index: Expression,
        val value: Expression
    ) : Expression()
}
