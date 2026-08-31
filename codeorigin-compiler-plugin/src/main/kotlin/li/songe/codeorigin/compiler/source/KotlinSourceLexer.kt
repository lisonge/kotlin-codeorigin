package li.songe.codeorigin.compiler.source

internal object KotlinSourceLexer {
    enum class Kind {
        Identifier,
        Modifier,
        Less,
        Greater,
        Arrow,
        LeftParenthesis,
        Documentation,
        Trivia,
        Other,
    }

    data class Token(
        val kind: Kind,
        val start: Int,
        val end: Int,
    ) {
        fun text(source: String): String = source.substring(start, end)
    }

    private val modifiers = setOf(
        "abstract",
        "actual",
        "annotation",
        "companion",
        "const",
        "crossinline",
        "data",
        "enum",
        "expect",
        "external",
        "final",
        "fun",
        "infix",
        "inline",
        "inner",
        "internal",
        "lateinit",
        "noinline",
        "open",
        "operator",
        "override",
        "private",
        "protected",
        "public",
        "reified",
        "sealed",
        "suspend",
        "tailrec",
        "value",
        "vararg",
    )

    fun tokenize(source: String): List<Token> = buildList {
        var index = 0
        while (index < source.length) {
            val start = index
            val kind = when {
                source[index].isWhitespace() -> {
                    index++
                    while (index < source.length && source[index].isWhitespace()) index++
                    Kind.Trivia
                }
                source.startsWith("//", index) -> {
                    index += 2
                    while (index < source.length && source[index] != '\n' && source[index] != '\r') index++
                    Kind.Trivia
                }
                source.startsWith("/*", index) -> {
                    val documentation = source.startsWith("/**", index)
                    index = blockCommentEnd(source, index)
                    if (documentation) Kind.Documentation else Kind.Trivia
                }
                source.startsWith("\"\"\"", index) -> {
                    index = quotedEnd(source, index + 3, "\"\"\"")
                    Kind.Other
                }
                source[index] == '"' -> {
                    index = escapedQuotedEnd(source, index + 1, '"')
                    Kind.Other
                }
                source[index] == '\'' -> {
                    index = escapedQuotedEnd(source, index + 1, '\'')
                    Kind.Other
                }
                source[index] == '`' -> {
                    index++
                    while (index < source.length && source[index] != '`') index++
                    if (index < source.length) index++
                    Kind.Identifier
                }
                Character.isJavaIdentifierStart(source[index]) -> {
                    index++
                    while (index < source.length && Character.isJavaIdentifierPart(source[index])) index++
                    val text = source.substring(start, index)
                    if (text in modifiers) Kind.Modifier else Kind.Identifier
                }
                source.startsWith("->", index) -> {
                    index += 2
                    Kind.Arrow
                }
                else -> {
                    index++
                    when (source[start]) {
                        '<' -> Kind.Less
                        '>' -> Kind.Greater
                        '(' -> Kind.LeftParenthesis
                        else -> Kind.Other
                    }
                }
            }
            add(Token(kind, start, index))
        }
    }

    private fun blockCommentEnd(source: String, start: Int): Int {
        var index = start + 2
        var depth = 1
        while (index < source.length && depth > 0) {
            when {
                source.startsWith("/*", index) -> {
                    depth++
                    index += 2
                }
                source.startsWith("*/", index) -> {
                    depth--
                    index += 2
                }
                else -> index++
            }
        }
        return index
    }

    private fun quotedEnd(source: String, start: Int, delimiter: String): Int {
        val end = source.indexOf(delimiter, start)
        return if (end < 0) source.length else end + delimiter.length
    }

    private fun escapedQuotedEnd(source: String, start: Int, delimiter: Char): Int {
        var index = start
        var escaped = false
        while (index < source.length) {
            val character = source[index++]
            if (!escaped && character == delimiter) break
            escaped = !escaped && character == '\\'
        }
        return index
    }
}
