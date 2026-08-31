plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.maven.publish)
}

dependencies {
    implementation(libs.kotlin.gradle.plugin.api)
    testImplementation(libs.kotlin.test.junit)
    testRuntimeOnly(libs.junit.platform.launcher)
}

buildConfig {
    packageName("li.songe.codeorigin.gradle")
    useKotlinOutput { topLevelConstants = true }
    buildConfigField("CODEORIGIN_COMPILER_PLUGIN_ID", "li.songe.codeorigin")
    buildConfigField("CODEORIGIN_COMPILER_PLUGIN_GROUP", project.group.toString())
    buildConfigField("CODEORIGIN_COMPILER_PLUGIN_ARTIFACT", project(":codeorigin-compiler-plugin").name)
    buildConfigField("CODEORIGIN_API_ARTIFACT", project(":codeorigin").name)
    buildConfigField("CODEORIGIN_VERSION", project.version.toString())
}

gradlePlugin {
    plugins {
        create("CodeOrigin") {
            id = "li.songe.codeorigin"
            displayName = "CodeOrigin for Kotlin"
            description = "Compile-time source introspection for Kotlin"
            implementationClass = "li.songe.codeorigin.gradle.CodeOriginGradlePlugin"
        }
    }
}

val functionalTestRepository = rootProject.layout.buildDirectory.dir("functional-test-repository")

tasks.test {
    dependsOn(
        ":codeorigin:publishKotlinMultiplatformPublicationToFunctionalTestRepository",
        ":codeorigin:publishJvmPublicationToFunctionalTestRepository",
        ":codeorigin:publishJsPublicationToFunctionalTestRepository",
        ":codeorigin:publishWasmJsPublicationToFunctionalTestRepository",
        ":codeorigin-compiler-plugin:publishMavenPublicationToFunctionalTestRepository",
        ":codeorigin-gradle-plugin:publishPluginMavenPublicationToFunctionalTestRepository",
        ":codeorigin-gradle-plugin:publishCodeOriginPluginMarkerMavenPublicationToFunctionalTestRepository",
    )
    useJUnitPlatform()
    systemProperty(
        "codeorigin.functional.test.repository",
        functionalTestRepository.get().asFile.absolutePath,
    )
    systemProperty("codeorigin.functional.test.kotlin.version", libs.versions.kotlin.get())
}
