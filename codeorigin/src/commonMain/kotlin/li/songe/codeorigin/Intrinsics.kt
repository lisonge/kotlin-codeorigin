package li.songe.codeorigin

/**
 * Returns the source-level simple name of [value].
 *
 * The compiler plugin replaces this call with a string constant and does not evaluate [value].
 * Only named references such as variables, parameters, properties, enum entries, objects, and
 * callable references are accepted.
 */
@Suppress("UNUSED_PARAMETER")
public fun <T> nameOf(value: T): String = codeOriginPluginRequired("nameOf")

/**
 * Returns the source-level simple name of [T].
 *
 * The compiler plugin replaces this call with a string constant.
 */
public fun <T> nameOf(): String = codeOriginPluginRequired("nameOf")

/**
 * Returns the original source text of [expression].
 *
 * The compiler plugin replaces this call with a string constant and does not evaluate
 * [expression]. Line endings in the returned text are normalized to `\n`.
 */
@Suppress("UNUSED_PARAMETER")
public fun <T> sourceOf(expression: T): String = codeOriginPluginRequired("sourceOf")

/**
 * Evaluates [expression] exactly once and returns its original source text together with its value.
 *
 * The returned pair contains the source text in [Pair.first] and the evaluated value in
 * [Pair.second]. Exceptions thrown by [expression] propagate normally. Line endings in the source
 * text are normalized to `\n`.
 */
@Suppress("UNUSED_PARAMETER")
public fun <T> evalSourceOf(expression: T): Pair<String, T> = codeOriginPluginRequired("evalSourceOf")

/**
 * Returns the original source text of the declaration for [T].
 *
 * [T] must resolve to a class, interface, object, enum, or annotation class whose source is readable
 * in the current compiler invocation. The declaration may be in another source file. The compiler
 * plugin replaces this call with a string constant.
 *
 * Cross-file capture does not register an incremental dependency on the declaration's source text.
 * Source-only changes may therefore leave a previously generated value stale, and some incremental
 * compiler invocations may not expose the target source at all. Callers are responsible for forcing
 * a clean or otherwise invalidated compilation when they require an up-to-date cross-file value.
 */
public fun <T> declarationSourceOf(): String = codeOriginPluginRequired("declarationSourceOf")

/**
 * Returns the original source text of the function or property referenced by [reference].
 *
 * The referenced declaration must have readable source in the current compiler invocation and may
 * belong to another source file. The compiler plugin replaces this call with a string constant and
 * does not evaluate [reference].
 *
 * Cross-file capture does not register an incremental dependency on the declaration's source text.
 * Source-only changes may therefore leave a previously generated value stale, and some incremental
 * compiler invocations may not expose the target source at all. Callers are responsible for forcing
 * a clean or otherwise invalidated compilation when they require an up-to-date cross-file value.
 */
@Suppress("UNUSED_PARAMETER")
public fun <T> declarationSourceOf(reference: T): String =
    codeOriginPluginRequired("declarationSourceOf")

private fun codeOriginPluginRequired(intrinsic: String): Nothing = error("$intrinsic requires the li.songe.codeorigin compiler plugin")
