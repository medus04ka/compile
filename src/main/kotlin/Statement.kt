sealed class Statement {
    data class ExpressionStatement(val expression: Expression) : Statement()

    data class PrintStatement(val expression: Expression) : Statement()

    data class VarStatement(
        val name: String,
        val initializer: Expression?
    ) : Statement()

    data class BlockStatement(val statements: List<Statement>) : Statement()

    data class IfStatement(
        val condition: Expression,
        val thenBranch: Statement,
        val elseBranch: Statement? = null
    ) : Statement()

    data class WhileStatement(
        val condition: Expression,
        val body: Statement
    ) : Statement()

    data class FunctionStatement(
        val name: String,
        val params: List<Parameter>,
        val returnType: Type,
        val body: List<Statement>
    ) : Statement()

    data class ReturnStatement(val value: Expression?) : Statement()

    data class Parameter(val name: String, val type: Type)
}
