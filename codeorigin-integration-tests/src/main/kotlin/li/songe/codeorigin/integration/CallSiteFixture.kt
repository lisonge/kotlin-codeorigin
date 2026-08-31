package li.songe.codeorigin.integration

import li.songe.codeorigin.CallSite
import li.songe.codeorigin.SourceLocation

data class CapturedCallSite(
    val location: SourceLocation,
    val text: String,
    val line: Int,
)

fun captureCallSite(
    @CallSite location: SourceLocation = SourceLocation(),
    @CallSite("{file}:{line}") text: String = "",
    @CallSite("{line}") line: Int = -1,
): CapturedCallSite = CapturedCallSite(location, text, line)

fun captureDefaultCallSite(
    @CallSite text: String = "",
): String = text

