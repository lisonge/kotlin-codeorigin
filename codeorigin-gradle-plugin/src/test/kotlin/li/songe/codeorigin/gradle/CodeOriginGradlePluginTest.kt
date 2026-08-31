package li.songe.codeorigin.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeOriginGradlePluginTest {
    private val repository: Path = Path.of(
        requireNotNull(System.getProperty("codeorigin.functional.test.repository"))
    )
    private val kotlinVersion: String =
        requireNotNull(System.getProperty("codeorigin.functional.test.kotlin.version"))
    private val apiCoordinates: String =
        "$CODEORIGIN_COMPILER_PLUGIN_GROUP:$CODEORIGIN_API_ARTIFACT:$CODEORIGIN_VERSION"

    @Test
    fun runsPublishedPluginsAndReusesCompilationCacheAfterProjectRelocation() {
        val directory = Files.createTempDirectory("codeorigin-relocation")
        try {
            val cache = directory.resolve("cache")
            val first = directory.resolve("first")
            val second = directory.resolve("second")
            writeFixture(first, cache)
            writeFixture(second, cache)

            val firstResult = runner(
                first,
                "run",
                "--build-cache",
                "--configuration-cache",
            ).build()
            assertEquals(TaskOutcome.SUCCESS, firstResult.task(":compileKotlin")?.outcome)
            assertTrue(firstResult.output.contains("CODEORIGIN_OK:Main.kt:"), firstResult.output)

            val secondResult = runner(second, "compileKotlin", "--build-cache").build()
            assertEquals(
                TaskOutcome.FROM_CACHE,
                secondResult.task(":compileKotlin")?.outcome,
                secondResult.output,
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun compilesMultiplatformIntrinsicsForJsAndWasm() {
        val directory = Files.createTempDirectory("codeorigin-multiplatform")
        try {
            writeMultiplatformFixture(directory)

            val result = runner(directory, "compileKotlinJs", "compileKotlinWasmJs").build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinJs")?.outcome, result.output)
            assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinWasmJs")?.outcome, result.output)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun runner(project: Path, vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(project.toFile())
        .withArguments(*arguments, "--stacktrace")

    private fun writeFixture(project: Path, cache: Path) {
        val cachePath = cache.toAbsolutePath().toString().replace('\\', '/')
        Files.createDirectories(project.resolve("src/main/kotlin"))
        writeSettings(project, "codeorigin-functional-test", cachePath)
        Files.writeString(
            project.resolve("build.gradle.kts"),
            """
                plugins {
                    kotlin("jvm") version "$kotlinVersion"
                    application
                    id("$CODEORIGIN_COMPILER_PLUGIN_ID") version "$CODEORIGIN_VERSION"
                }

                dependencies {
                    implementation("$apiCoordinates")
                }

                kotlin {
                    jvmToolchain(21)
                }

                application {
                    mainClass.set("MainKt")
                }
            """.trimIndent(),
        )
        Files.writeString(
            project.resolve("src/main/kotlin/Main.kt"),
            """
                import li.songe.codeorigin.CallSite
                import li.songe.codeorigin.nameOf
                import li.songe.codeorigin.sourceOf

                fun capture(@CallSite("{file}:{line}") location: String = ""): String = location

                fun main() {
                    check(nameOf</* comment */ String>() == "String")
                    check(sourceOf(1 + 2) == "1 + 2")
                    val location = capture()
                    check(location.startsWith("Main.kt:"))
                    println("CODEORIGIN_OK:${'$'}location")
                }
            """.trimIndent(),
        )
    }

    private fun writeMultiplatformFixture(project: Path) {
        Files.createDirectories(project.resolve("src/commonMain/kotlin"))
        writeSettings(project, "codeorigin-multiplatform-test")
        Files.writeString(
            project.resolve("build.gradle.kts"),
            """
                @file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

                plugins {
                    kotlin("multiplatform") version "$kotlinVersion"
                    id("$CODEORIGIN_COMPILER_PLUGIN_ID") version "$CODEORIGIN_VERSION"
                }

                kotlin {
                    js().nodejs()
                    wasmJs().nodejs()

                    sourceSets.commonMain.dependencies {
                        implementation("$apiCoordinates")
                    }
                }
            """.trimIndent(),
        )
        Files.writeString(
            project.resolve("src/commonMain/kotlin/Common.kt"),
            """
                import li.songe.codeorigin.CallSite
                import li.songe.codeorigin.SourceLocation
                import li.songe.codeorigin.declarationSourceOf
                import li.songe.codeorigin.evalSourceOf
                import li.songe.codeorigin.nameOf
                import li.songe.codeorigin.sourceOf

                private data class SharedUser(val name: String)

                private fun capture(
                    @CallSite location: SourceLocation = SourceLocation(),
                ): SourceLocation = location

                fun commonSmoke(value: Int): Pair<String, Int> {
                    check(nameOf</* source comment */ SharedUser>() == "SharedUser")
                    check(sourceOf(value + 1) == "value + 1")
                    check(declarationSourceOf<SharedUser>().startsWith("private data class SharedUser"))
                    capture()
                    return evalSourceOf(value + 1)
                }
            """.trimIndent(),
        )
    }

    private fun writeSettings(project: Path, name: String, cachePath: String? = null) {
        val repositoryPath = repository.toAbsolutePath().toString().replace('\\', '/')
        val buildCache = cachePath?.let {
            """

                buildCache {
                    local { directory = file("$it") }
                }
            """.trimEnd()
        }.orEmpty()
        Files.writeString(
            project.resolve("settings.gradle.kts"),
            """
                pluginManagement {
                    repositories {
                        maven { url = uri("$repositoryPath") }
                        gradlePluginPortal()
                    }
                }

                dependencyResolutionManagement {
                    repositories {
                        maven { url = uri("$repositoryPath") }
                        mavenCentral()
                    }
                }
                $buildCache

                rootProject.name = "$name"
            """.trimIndent(),
        )
    }
}
