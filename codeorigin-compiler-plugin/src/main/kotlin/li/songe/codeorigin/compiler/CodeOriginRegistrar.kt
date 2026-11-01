package li.songe.codeorigin.compiler

import li.songe.codeorigin.compiler.ir.CodeOriginIrGenerationExtension
import li.songe.codeorigin.compiler.source.SourceSnapshotRegistrar
import li.songe.codeorigin.compiler.source.SourceSnapshots
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

class CodeOriginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true
    override val pluginId: String = CODEORIGIN_PLUGIN_ID

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val projectRoot = configuration[CodeOriginConfigurationKeys.projectRoot].orEmpty()
        val messageCollector = configuration.get(
            CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
            MessageCollector.NONE,
        )
        val snapshots = SourceSnapshots()
        FirExtensionRegistrarAdapter.registerExtension(SourceSnapshotRegistrar(snapshots))
        IrGenerationExtension.registerExtension(
            CodeOriginIrGenerationExtension(messageCollector, projectRoot, snapshots)
        )
    }
}

