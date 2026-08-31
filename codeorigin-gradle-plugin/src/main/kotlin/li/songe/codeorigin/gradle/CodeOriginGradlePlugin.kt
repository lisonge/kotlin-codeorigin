package li.songe.codeorigin.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.InternalSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Suppress("unused")
public class CodeOriginGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = CODEORIGIN_COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = CODEORIGIN_COMPILER_PLUGIN_GROUP,
        artifactId = CODEORIGIN_COMPILER_PLUGIN_ARTIFACT,
        version = CODEORIGIN_VERSION,
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        return kotlinCompilation.target.project.provider {
            val projectRoot = kotlinCompilation.project.rootDir.absolutePath.replace('\\', '/')
            listOf(
                InternalSubpluginOption(
                    key = "projectRoot",
                    value = URLEncoder.encode(projectRoot, StandardCharsets.UTF_8),
                )
            )
        }
    }
}
