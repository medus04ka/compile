class Environment(private val parent: Environment? = null) {
    private val values = mutableMapOf<String, RuntimeValue>()

    fun define(name: String, value: RuntimeValue) {
        values[name] = value
    }

    fun get(name: String): RuntimeValue =
        values[name] ?: parent?.get(name)
        ?: throw RuntimeLanguageError("Ошибка выполнения: переменная '$name' не объявлена.")

    fun assign(name: String, value: RuntimeValue) {
        if (values.containsKey(name)) {
            values[name] = value
            return
        }

        if (parent != null) {
            parent.assign(name, value)
            return
        }

        throw RuntimeLanguageError("Ошибка выполнения: переменная '$name' не объявлена.")
    }
}
