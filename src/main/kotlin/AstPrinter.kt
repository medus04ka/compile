class AstPrinter {
    fun print(statements: List<Statement>) {
        println("Программа")
        statements.forEachIndexed { index, statement ->
            printNode(statement, "", index == statements.lastIndex, null)
        }
    }

    private fun printNode(node: Any?, indent: String, isLast: Boolean, label: String?) {
        if (node == null) return

        val connector = if (isLast) "└── " else "├── "
        val nextIndent = indent + if (isLast) "    " else "│   "
        val prefix = if (label == null) "" else "$label: "

        print(indent + connector + prefix)

        when (node) {
            is Statement.VarStatement -> {
                println("VarStatement ${node.name}")
                node.initializer?.let { printNode(it, nextIndent, true, "initializer") }
            }

            is Statement.PrintStatement -> {
                println("PrintStatement")
                printNode(node.expression, nextIndent, true, "expression")
            }

            is Statement.ExpressionStatement -> {
                println("ExpressionStatement")
                printNode(node.expression, nextIndent, true, "expression")
            }

            is Statement.BlockStatement -> {
                println("BlockStatement")
                node.statements.forEachIndexed { index, statement ->
                    printNode(statement, nextIndent, index == node.statements.lastIndex, null)
                }
            }

            is Statement.IfStatement -> {
                println("IfStatement")
                val hasElse = node.elseBranch != null
                printNode(node.condition, nextIndent, false, "condition")
                printNode(node.thenBranch, nextIndent, !hasElse, "then")
                node.elseBranch?.let { printNode(it, nextIndent, true, "else") }
            }

            is Statement.WhileStatement -> {
                println("WhileStatement")
                printNode(node.condition, nextIndent, false, "condition")
                printNode(node.body, nextIndent, true, "body")
            }

            is Statement.FunctionStatement -> {
                val params = node.params.joinToString(", ") { "${it.name}: ${it.type}" }
                println("FunctionStatement ${node.name}($params): ${node.returnType}")
                node.body.forEachIndexed { index, statement ->
                    printNode(statement, nextIndent, index == node.body.lastIndex, null)
                }
            }

            is Statement.ReturnStatement -> {
                println("ReturnStatement")
                node.value?.let { printNode(it, nextIndent, true, "value") }
            }

            is Expression.NumberExpression -> println("Number ${node.value}")
            is Expression.StringExpression -> println("String \"${node.value}\"")
            is Expression.BooleanExpression -> println("Boolean ${node.value}")
            is Expression.VariableExpression -> println("Variable ${node.name}")

            is Expression.AssignExpression -> {
                println("AssignExpression ${node.name}")
                printNode(node.value, nextIndent, true, "value")
            }

            is Expression.UnaryExpression -> {
                println("UnaryExpression ${node.operator}")
                printNode(node.right, nextIndent, true, "right")
            }

            is Expression.BinaryExpression -> {
                println("BinaryExpression ${node.operator}")
                printNode(node.left, nextIndent, false, "left")
                printNode(node.right, nextIndent, true, "right")
            }

            is Expression.CallExpression -> {
                println("CallExpression")
                val hasArguments = node.arguments.isNotEmpty()
                printNode(node.callee, nextIndent, !hasArguments, "callee")
                node.arguments.forEachIndexed { index, argument ->
                    printNode(argument, nextIndent, index == node.arguments.lastIndex, "arg[$index]")
                }
            }

            is Expression.ArrayExpression -> {
                println("ArrayExpression")
                node.elements.forEachIndexed { index, element ->
                    printNode(element, nextIndent, index == node.elements.lastIndex, "element[$index]")
                }
            }

            is Expression.IndexExpression -> {
                println("IndexExpression")
                printNode(node.array, nextIndent, false, "array")
                printNode(node.index, nextIndent, true, "index")
            }

            is Expression.IndexAssignExpression -> {
                println("IndexAssignExpression")
                printNode(node.array, nextIndent, false, "array")
                printNode(node.index, nextIndent, false, "index")
                printNode(node.value, nextIndent, true, "value")
            }

            else -> println("Unknown ${node::class.simpleName}")
        }
    }
}
