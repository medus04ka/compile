sealed class Type {
    object Number : Type() {
        override fun toString(): kotlin.String = "Number"
    }

    object String : Type() {
        override fun toString(): kotlin.String = "String"
    }

    object Boolean : Type() {
        override fun toString(): kotlin.String = "Boolean"
    }

    object Null : Type() {
        override fun toString(): kotlin.String = "Null"
    }

    data class Array(val elementType: Type) : Type() {
        override fun toString(): kotlin.String = "Array<$elementType>"
    }

    data class Function(
        val params: List<Type>,
        val returnType: Type
    ) : Type() {
        override fun toString(): kotlin.String =
            "(${params.joinToString(", ")}) -> $returnType"
    }
}
