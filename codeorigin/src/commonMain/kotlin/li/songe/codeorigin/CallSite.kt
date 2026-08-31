package li.songe.codeorigin

/**
 * Marks a default parameter that should receive information about the call site when omitted.
 *
 * Supported parameter types are [String], [Int], and [SourceLocation]. String parameters accept
 * the fields `{path}`, `{file}`, `{package}`, `{name}`, `{owner}`, `{qualifiedOwner}`, `{type}`,
 * `{function}`, `{line}`, `{column}`, and `{offset}`. Int parameters require exactly one of
 * `{line}`, `{column}`, or `{offset}`. [SourceLocation] parameters use the structured value and
 * do not accept a custom format.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
public annotation class CallSite(public val format: String = "")

/** Structured, platform-independent information about a Kotlin call site. */
public data class SourceLocation(
    public val path: String = "",
    public val file: String = "",
    public val packageName: String = "",
    public val name: String = "",
    public val owner: String = "",
    public val qualifiedOwner: String = "",
    public val type: String = "",
    public val function: String = "",
    public val line: Int = -1,
    public val column: Int = -1,
    public val offset: Int = -1,
)
