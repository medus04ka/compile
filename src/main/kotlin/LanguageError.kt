open class LanguageError(message: String) : RuntimeException(message)

class LexerError(message: String) : LanguageError(message)

class ParserError(message: String) : LanguageError(message)

class TypeCheckError(message: String) : LanguageError(message)

class RuntimeLanguageError(message: String) : LanguageError(message)
