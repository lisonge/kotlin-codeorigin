package li.songe.codeorigin.compiler.ir

import li.songe.codeorigin.compiler.DECLARATION_SOURCE_OF_FQ_NAME
import li.songe.codeorigin.compiler.EVAL_SOURCE_OF_FQ_NAME
import li.songe.codeorigin.compiler.NAME_OF_FQ_NAME
import li.songe.codeorigin.compiler.SOURCE_OF_FQ_NAME
import li.songe.codeorigin.compiler.callsite.CallSiteLowering
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class CodeOriginTransformer(
    pluginContext: IrPluginContext,
    messageCollector: MessageCollector,
    projectRoot: String,
    moduleFiles: Set<IrFile>,
) : IrElementTransformerVoid() {
    private val diagnostics = CodeOriginDiagnostics(messageCollector)
    private val intrinsicLowering = IntrinsicLowering(
        pluginContext,
        projectRoot,
        moduleFiles,
        diagnostics,
    )
    private val callSiteLowering = CallSiteLowering(
        pluginContext,
        projectRoot,
        diagnostics,
    )

    private var currentFile: IrFile? = null
    private val declarations = mutableListOf<IrDeclarationBase>()

    override fun visitFile(declaration: IrFile): IrFile {
        val previousFile = currentFile
        currentFile = declaration
        return try {
            super.visitFile(declaration)
        } finally {
            currentFile = previousFile
        }
    }

    override fun visitClass(declaration: IrClass): IrStatement =
        walk(declaration) { super.visitClass(declaration) }

    override fun visitProperty(declaration: IrProperty): IrStatement =
        walk(declaration) { super.visitProperty(declaration) }

    override fun visitAnonymousInitializer(declaration: IrAnonymousInitializer): IrStatement =
        walk(declaration) { super.visitAnonymousInitializer(declaration) }

    override fun visitFunction(declaration: IrFunction): IrStatement = walk(declaration) {
        callSiteLowering.validate(declaration, currentFile)
        super.visitFunction(declaration)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val function = expression.symbol.owner
        return when (function.name.asString()) {
            "nameOf" -> if (function.fqNameWhenAvailable?.asString() == NAME_OF_FQ_NAME) {
                intrinsicLowering.transformNameOf(expression, currentFile)
            } else {
                transformRegularCall(expression)
            }
            "sourceOf" -> if (function.fqNameWhenAvailable?.asString() == SOURCE_OF_FQ_NAME) {
                intrinsicLowering.transformSourceOf(expression, currentFile)
            } else {
                transformRegularCall(expression)
            }
            "evalSourceOf" -> if (function.fqNameWhenAvailable?.asString() == EVAL_SOURCE_OF_FQ_NAME) {
                intrinsicLowering.transformEvalSourceOf(
                    expression,
                    currentFile,
                    declarations,
                ) { argument ->
                    argument.transform(this, null) as IrExpression
                }
            } else {
                transformRegularCall(expression)
            }
            "declarationSourceOf" -> if (
                function.fqNameWhenAvailable?.asString() == DECLARATION_SOURCE_OF_FQ_NAME
            ) {
                intrinsicLowering.transformDeclarationSourceOf(expression, currentFile)
            } else {
                transformRegularCall(expression)
            }
            else -> transformRegularCall(expression)
        }
    }

    private fun transformRegularCall(expression: IrCall): IrExpression {
        callSiteLowering.inject(expression, currentFile, declarations)
        return super.visitCall(expression)
    }

    private inline fun <T> walk(declaration: IrDeclarationBase, block: () -> T): T {
        declarations += declaration
        return try {
            block()
        } finally {
            declarations.removeLast()
        }
    }
}
