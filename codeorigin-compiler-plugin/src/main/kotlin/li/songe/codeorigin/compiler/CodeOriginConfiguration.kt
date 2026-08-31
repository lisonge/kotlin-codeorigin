package li.songe.codeorigin.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal object CodeOriginConfigurationKeys {
    val projectRoot: CompilerConfigurationKey<String> =
        CompilerConfigurationKey(PROJECT_ROOT_OPTION)
}

class CodeOriginCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = CODEORIGIN_PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            optionName = PROJECT_ROOT_OPTION,
            valueDescription = "<path>",
            description = "The Gradle root project path used to make source paths relative",
            required = false,
            allowMultipleOccurrences = false,
        )
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            PROJECT_ROOT_OPTION -> configuration.put(
                CodeOriginConfigurationKeys.projectRoot,
                URLDecoder.decode(value, StandardCharsets.UTF_8),
            )
            else -> error("Unknown CodeOrigin compiler option: ${option.optionName}")
        }
    }
}

