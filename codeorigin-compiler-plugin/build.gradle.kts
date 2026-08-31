import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

dependencies {
    compileOnly(libs.kotlin.compiler)
    testImplementation(libs.kotlin.compiler)
    testImplementation(project(":codeorigin"))
    testImplementation(libs.kotlin.test.junit)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

val compilerPluginJar = tasks.named<Jar>("jar")

tasks.test {
    dependsOn(compilerPluginJar)
    useJUnitPlatform()
    systemProperty(
        "codeorigin.compiler.jar",
        compilerPluginJar.get().archiveFile.get().asFile.absolutePath,
    )
}
