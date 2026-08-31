pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "kotlin-codeorigin"

include("codeorigin")
include("codeorigin-compiler-plugin")
include("codeorigin-gradle-plugin")
include("codeorigin-integration-tests")

