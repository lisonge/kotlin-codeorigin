package li.songe.codeorigin.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.path
import java.nio.file.Files
import java.nio.file.Path

/** Loaded before CodeOrigin only by the source-snapshot regression tests. */
class SourceMutationRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true
    override val pluginId: String = "codeorigin.test.source-mutation"

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(SourceMutationExtension())
    }
}

class SourceMutationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        for (file in moduleFragment.files) {
            val path = Path.of(file.path)
            val replacement = Path.of(file.path + ".replacement")
            if (Files.isRegularFile(replacement)) {
                Files.writeString(path, Files.readString(replacement))
                Files.writeString(Path.of(file.path + ".mutated"), "done")
            }
        }
    }
}
