package li.songe.codeorigin.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

internal class CodeOriginIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val projectRoot: String,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(
            CodeOriginTransformer(
                pluginContext,
                messageCollector,
                projectRoot,
                moduleFragment.files.toHashSet(),
            )
        )
    }
}
