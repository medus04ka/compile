sealed class RuntimeValue {
    data class Number(val value: Double) : RuntimeValue() {
        override fun toString(): String =
            if (value == kotlin.math.floor(value) && !value.isInfinite()) {
                value.toLong().toString()
            } else {
                value.toString()
            }
    }

    data class StringVal(val value: String) : RuntimeValue() {
        override fun toString(): String = value
    }

    data class Boolean(val value: kotlin.Boolean) : RuntimeValue() {
        override fun toString(): String = value.toString()
    }

    data class FunctionVal(
        val declaration: Statement.FunctionStatement,
        val closure: Environment
    ) : RuntimeValue() {
        override fun toString(): String = "<функция ${declaration.name}>"
    }

    data class ArrayVal(val elements: MutableList<RuntimeValue>) : RuntimeValue() {
        override fun toString(): String = elements.joinToString(", ", "[", "]")
    }

    object Null : RuntimeValue() {
        override fun toString(): String = "null"
    }
}

class ReturnSignal(val value: RuntimeValue) : RuntimeException(null, null, false, false)
