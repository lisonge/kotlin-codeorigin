package li.songe.codeorigin.compiler.callsite

import li.songe.codeorigin.compiler.source.SourceContext

internal class CallSiteFormat private constructor(
    val value: String,
    private val parts: List<Part>,
) {
    val isInteger: Boolean
        get() = parts.size == 1 && (parts.single() as? Part.Field)?.field?.integer == true

    fun render(context: SourceContext): String = buildString {
        for (part in parts) {
            when (part) {
                is Part.Text -> append(part.value)
                is Part.Field -> append(part.field.read(context))
            }
        }
    }

    fun renderInt(context: SourceContext): Int {
        val field = (parts.single() as Part.Field).field
        return when (field) {
            Field.Line -> context.line
            Field.Column -> context.column
            Field.Offset -> context.offset
            else -> error("$value is not an integer call-site format")
        }
    }

    private sealed interface Part {
        data class Text(val value: String) : Part
        data class Field(val field: CallSiteFormat.Field) : Part
    }

    private enum class Field(val token: String, val integer: Boolean = false) {
        Path("path"),
        File("file"),
        Package("package"),
        Name("name"),
        Owner("owner"),
        QualifiedOwner("qualifiedOwner"),
        Type("type"),
        Function("function"),
        Line("line", integer = true),
        Column("column", integer = true),
        Offset("offset", integer = true),
        ;

        fun read(context: SourceContext): Any = when (this) {
            Path -> context.path
            File -> context.file
            Package -> context.packageName
            Name -> context.name
            Owner -> context.owner
            QualifiedOwner -> context.qualifiedOwner
            Type -> context.type
            Function -> context.function
            Line -> context.line
            Column -> context.column
            Offset -> context.offset
        }

        companion object {
            fun fromToken(token: String): Field? = entries.firstOrNull { it.token == token }
        }
    }

    companion object {
        fun parse(value: String): Result<CallSiteFormat> = runCatching {
            val parts = mutableListOf<Part>()
            val text = StringBuilder()

            fun flushText() {
                if (text.isNotEmpty()) {
                    parts += Part.Text(text.toString())
                    text.clear()
                }
            }

            var index = 0
            while (index < value.length) {
                when {
                    value.startsWith("{{", index) -> {
                        text.append('{')
                        index += 2
                    }
                    value.startsWith("}}", index) -> {
                        text.append('}')
                        index += 2
                    }
                    value[index] == '{' -> {
                        val end = value.indexOf('}', index + 1)
                        require(end >= 0) { "unclosed '{' in call-site format: $value" }
                        val token = value.substring(index + 1, end)
                        val field = Field.fromToken(token)
                            ?: error("unknown call-site field {$token}")
                        flushText()
                        parts += Part.Field(field)
                        index = end + 1
                    }
                    value[index] == '}' -> error("unmatched '}' in call-site format: $value")
                    else -> {
                        text.append(value[index])
                        index++
                    }
                }
            }
            flushText()
            CallSiteFormat(value, parts)
        }
    }
}

