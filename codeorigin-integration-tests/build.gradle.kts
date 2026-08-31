import org.gradle.api.tasks.PathSensitivity
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":codeorigin"))
    testImplementation(libs.kotlin.test.junit)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val compilerPluginJar = project(":codeorigin-compiler-plugin").tasks.named<Jar>("jar")
val compilerPluginFile = compilerPluginJar.flatMap { it.archiveFile }

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(compilerPluginJar)
    inputs.file(compilerPluginFile)
        .withPropertyName("codeoriginCompilerPlugin")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    compilerOptions.freeCompilerArgs.addAll(
        provider {
            val jar = compilerPluginFile.get().asFile.absolutePath
            val root = URLEncoder.encode(rootProject.projectDir.absolutePath.replace('\\', '/'), StandardCharsets.UTF_8)
            listOf(
                "-Xplugin=$jar",
                "-P",
                "plugin:li.songe.codeorigin:projectRoot=$root",
            )
        }
    )
}

tasks.test {
    useJUnitPlatform()
}
