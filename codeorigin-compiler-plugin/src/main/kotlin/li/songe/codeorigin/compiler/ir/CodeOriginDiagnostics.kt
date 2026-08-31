package li.songe.codeorigin.compiler.ir

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.path

internal class CodeOriginDiagnostics(
    private val messageCollector: MessageCollector,
) {
    fun report(file: IrFile?, statement: IrStatement, message: String) {
        val location = if (file == null) {
            null
        } else {
            val (line, column) = file.fileEntry.getLineAndColumnNumbers(statement.startOffset)
            CompilerMessageLocation.create(file.path, line + 1, column + 1, null)
        }
        messageCollector.report(
            CompilerMessageSeverity.ERROR,
            "[CodeOrigin] $message",
            location,
        )
    }
}
