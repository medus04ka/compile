class Optimizer(
    private val passes: List<OptimizationPass> = listOf(
        ConstantFoldingPass(),
        AlgebraicSimplificationPass(),
        BranchSimplificationPass(),
        DeadCodeEliminationPass()
    )
) {
    fun optimize(statements: List<Statement>): List<Statement> {
        var current = statements

        for (pass in passes) {
            current = pass.optimize(current)
        }

        return current
    }
}

interface OptimizationPass {
    fun optimize(statements: List<Statement>): List<Statement>
}

abstract class TreeOptimizationPass : OptimizationPass {
    override fun optimize(statements: List<Statement>): List<Statement> =
        statements.map { optimizeStatement(it) }

    protected open fun optimizeStatement(statement: Statement): Statement {
        return when (statement) {
            is Statement.ExpressionStatement ->
                Statement.ExpressionStatement(optimizeExpression(statement.expression))

            is Statement.PrintStatement ->
                Statement.PrintStatement(optimizeExpression(statement.expression))

            is Statement.VarStatement ->
                Statement.VarStatement(
                    statement.name,
                    statement.initializer?.let { optimizeExpression(it) }
                )

            is Statement.BlockStatement ->
                Statement.BlockStatement(statement.statements.map { optimizeStatement(it) })

            is Statement.IfStatement ->
                Statement.IfStatement(
                    optimizeExpression(statement.condition),
                    optimizeStatement(statement.thenBranch),
                    statement.elseBranch?.let { optimizeStatement(it) }
                )

            is Statement.WhileStatement ->
                Statement.WhileStatement(
                    optimizeExpression(statement.condition),
                    optimizeStatement(statement.body)
                )

            is Statement.FunctionStatement ->
                Statement.FunctionStatement(
                    statement.name,
                    statement.params,
                    statement.returnType,
                    statement.body.map { optimizeStatement(it) }
                )

            is Statement.ReturnStatement ->
                Statement.ReturnStatement(statement.value?.let { optimizeExpression(it) })
        }
    }

    protected open fun optimizeExpression(expression: Expression): Expression {
        return when (expression) {
            is Expression.NumberExpression,
            is Expression.StringExpression,
            is Expression.BooleanExpression,
            is Expression.VariableExpression -> expression

            is Expression.AssignExpression ->
                Expression.AssignExpression(
                    expression.name,
                    optimizeExpression(expression.value)
                )

            is Expression.UnaryExpression ->
                Expression.UnaryExpression(
                    expression.operator,
                    optimizeExpression(expression.right)
                )

            is Expression.BinaryExpression ->
                Expression.BinaryExpression(
                    optimizeExpression(expression.left),
                    expression.operator,
                    optimizeExpression(expression.right)
                )

            is Expression.CallExpression ->
                Expression.CallExpression(
                    optimizeExpression(expression.callee),
                    expression.arguments.map { optimizeExpression(it) }
                )

            is Expression.ArrayExpression ->
                Expression.ArrayExpression(expression.elements.map { optimizeExpression(it) })

            is Expression.IndexExpression ->
                Expression.IndexExpression(
                    optimizeExpression(expression.array),
                    optimizeExpression(expression.index)
                )

            is Expression.IndexAssignExpression ->
                Expression.IndexAssignExpression(
                    optimizeExpression(expression.array),
                    optimizeExpression(expression.index),
                    optimizeExpression(expression.value)
                )
        }
    }
}

class ConstantFoldingPass : TreeOptimizationPass() {
    override fun optimizeExpression(expression: Expression): Expression {
        val optimized = super.optimizeExpression(expression)

        return when (optimized) {
            is Expression.UnaryExpression -> foldUnary(optimized)
            is Expression.BinaryExpression -> foldBinary(optimized)
            else -> optimized
        }
    }

    private fun foldUnary(expression: Expression.UnaryExpression): Expression {
        val right = expression.right

        return when {
            expression.operator == TokenType.MINUS && right is Expression.NumberExpression ->
                Expression.NumberExpression(-right.value)

            expression.operator == TokenType.EXCL && right is Expression.BooleanExpression ->
                Expression.BooleanExpression(!right.value)

            else -> expression
        }
    }

    private fun foldBinary(expression: Expression.BinaryExpression): Expression {
        val left = expression.left
        val right = expression.right
        val op = expression.operator

        if (left is Expression.NumberExpression && right is Expression.NumberExpression) {
            return when (op) {
                TokenType.PLUS -> Expression.NumberExpression(left.value + right.value)
                TokenType.MINUS -> Expression.NumberExpression(left.value - right.value)
                TokenType.STAR -> Expression.NumberExpression(left.value * right.value)
                TokenType.SLASH -> if (right.value != 0.0) {
                    Expression.NumberExpression(left.value / right.value)
                } else {
                    expression
                }
                TokenType.LT -> Expression.BooleanExpression(left.value < right.value)
                TokenType.LTEQ -> Expression.BooleanExpression(left.value <= right.value)
                TokenType.GT -> Expression.BooleanExpression(left.value > right.value)
                TokenType.GTEQ -> Expression.BooleanExpression(left.value >= right.value)
                TokenType.EQEQ -> Expression.BooleanExpression(left.value == right.value)
                TokenType.NEQ -> Expression.BooleanExpression(left.value != right.value)
                else -> expression
            }
        }

        if (left is Expression.StringExpression && right is Expression.StringExpression) {
            return when (op) {
                TokenType.PLUS -> Expression.StringExpression(left.value + right.value)
                TokenType.EQEQ -> Expression.BooleanExpression(left.value == right.value)
                TokenType.NEQ -> Expression.BooleanExpression(left.value != right.value)
                else -> expression
            }
        }

        if (left is Expression.BooleanExpression && right is Expression.BooleanExpression) {
            return when (op) {
                TokenType.AND -> Expression.BooleanExpression(left.value && right.value)
                TokenType.OR -> Expression.BooleanExpression(left.value || right.value)
                TokenType.EQEQ -> Expression.BooleanExpression(left.value == right.value)
                TokenType.NEQ -> Expression.BooleanExpression(left.value != right.value)
                else -> expression
            }
        }

        return expression
    }
}

class AlgebraicSimplificationPass : TreeOptimizationPass() {
    override fun optimizeExpression(expression: Expression): Expression {
        val optimized = super.optimizeExpression(expression)

        if (optimized !is Expression.BinaryExpression) {
            return optimized
        }

        val left = optimized.left
        val right = optimized.right

        return when (optimized.operator) {
            TokenType.PLUS -> when {
                right.isNumber(0.0) -> left
                left.isNumber(0.0) -> right
                else -> optimized
            }

            TokenType.MINUS -> when {
                right.isNumber(0.0) -> left
                else -> optimized
            }

            TokenType.STAR -> when {
                right.isNumber(1.0) -> left
                left.isNumber(1.0) -> right
                right.isNumber(0.0) && left.isPure() -> Expression.NumberExpression(0.0)
                left.isNumber(0.0) && right.isPure() -> Expression.NumberExpression(0.0)
                else -> optimized
            }

            TokenType.SLASH -> when {
                right.isNumber(1.0) -> left
                else -> optimized
            }

            TokenType.AND -> when {
                left is Expression.BooleanExpression && left.value -> right
                right is Expression.BooleanExpression && right.value -> left
                left is Expression.BooleanExpression && !left.value && right.isPure() -> left
                else -> optimized
            }

            TokenType.OR -> when {
                left is Expression.BooleanExpression && !left.value -> right
                right is Expression.BooleanExpression && !right.value -> left
                left is Expression.BooleanExpression && left.value && right.isPure() -> left
                else -> optimized
            }

            else -> optimized
        }
    }

    private fun Expression.isNumber(value: Double): Boolean =
        this is Expression.NumberExpression && this.value == value

    private fun Expression.isPure(): Boolean {
        return when (this) {
            is Expression.NumberExpression,
            is Expression.StringExpression,
            is Expression.BooleanExpression,
            is Expression.VariableExpression -> true

            is Expression.UnaryExpression -> right.isPure()
            is Expression.BinaryExpression -> left.isPure() && right.isPure()
            is Expression.ArrayExpression -> elements.all { it.isPure() }

            is Expression.AssignExpression,
            is Expression.CallExpression,
            is Expression.IndexExpression,
            is Expression.IndexAssignExpression -> false
        }
    }
}

class BranchSimplificationPass : TreeOptimizationPass() {
    override fun optimizeStatement(statement: Statement): Statement {
        val optimized = super.optimizeStatement(statement)

        return when (optimized) {
            is Statement.IfStatement -> when (val condition = optimized.condition) {
                is Expression.BooleanExpression ->
                    if (condition.value) optimized.thenBranch
                    else optimized.elseBranch ?: Statement.BlockStatement(emptyList())

                else -> optimized
            }

            is Statement.WhileStatement -> when (val condition = optimized.condition) {
                is Expression.BooleanExpression ->
                    if (!condition.value) Statement.BlockStatement(emptyList()) else optimized

                else -> optimized
            }

            else -> optimized
        }
    }
}

class DeadCodeEliminationPass : TreeOptimizationPass() {
    override fun optimize(statements: List<Statement>): List<Statement> =
        trimAfterReturn(statements).map { optimizeStatement(it) }

    override fun optimizeStatement(statement: Statement): Statement {
        return when (statement) {
            is Statement.BlockStatement ->
                Statement.BlockStatement(trimAfterReturn(statement.statements).map { optimizeStatement(it) })

            is Statement.FunctionStatement ->
                Statement.FunctionStatement(
                    statement.name,
                    statement.params,
                    statement.returnType,
                    trimAfterReturn(statement.body).map { optimizeStatement(it) }
                )

            else -> super.optimizeStatement(statement)
        }
    }

    private fun trimAfterReturn(statements: List<Statement>): List<Statement> {
        val result = mutableListOf<Statement>()

        for (statement in statements) {
            result.add(statement)
            if (statement is Statement.ReturnStatement) break
        }

        return result
    }
}
