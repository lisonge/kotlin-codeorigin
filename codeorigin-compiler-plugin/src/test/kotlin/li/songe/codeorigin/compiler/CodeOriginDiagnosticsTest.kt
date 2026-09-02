package li.songe.codeorigin.compiler

import li.songe.codeorigin.SourceLocation
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLEncoder
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeOriginDiagnosticsTest {
    private data class CompilationResult(
        val exitCode: ExitCode,
        val diagnostics: String,
    )

    @Test
    fun capturesDeclarationsFromAnotherSourceFile() {
        var classSource: String? = null
        var functionSource: String? = null
        val result = compileSources(
            mapOf(
                "Other.kt" to """
                    package fixture

                    data class OtherFileDeclaration(val value: Int)

                    fun otherFileFunction(): Int = 1
                """.trimIndent(),
                "Capture.kt" to """
                    package fixture

                    import li.songe.codeorigin.declarationSourceOf

                    val classSource: String = declarationSourceOf<OtherFileDeclaration>()
                    val functionSource: String = declarationSourceOf(::otherFileFunction)
                """.trimIndent(),
            ),
        ) { classesDirectory ->
            URLClassLoader(
                arrayOf(classesDirectory.toURI().toURL()),
                javaClass.classLoader,
            ).use { loader ->
                val captureClass = loader.loadClass("fixture.CaptureKt")
                classSource = captureClass.getMethod("getClassSource").invoke(null) as String
                functionSource = captureClass.getMethod("getFunctionSource").invoke(null) as String
            }
        }

        assertEquals(ExitCode.OK, result.exitCode, result.diagnostics)
        assertEquals("data class OtherFileDeclaration(val value: Int)", classSource)
        assertEquals("fun otherFileFunction(): Int = 1", functionSource)
    }

    @Test
    fun rejectsInvalidNameAndCallSiteForms() {
        val result = compileSources(
            mapOf(
                "Other.kt" to "class OtherFileDeclaration",
                "Invalid.kt" to """
                import li.songe.codeorigin.CallSite
                import li.songe.codeorigin.declarationSourceOf
                import li.songe.codeorigin.nameOf

                fun invalidName(): String = nameOf(1 + 2)

                fun invalidDeclarationArgument(): String = declarationSourceOf(1 + 2)

                fun externalDeclaration(): String = declarationSourceOf<String>()

                fun externalReference(): String = declarationSourceOf(String::length)

                fun otherFileDeclaration(): String = declarationSourceOf<OtherFileDeclaration>()

                fun <T> genericDeclaration(): String = declarationSourceOf<T>()

                class Local

                fun constructorReference(): String = declarationSourceOf(::Local)

                fun invalidCallSite(
                    @CallSite("{file}") line: Int = 0,
                ): Int = line
            """.trimIndent(),
            ),
        )

        val diagnostics = result.diagnostics
        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, diagnostics)
        assertTrue(
            diagnostics.contains("nameOf(value) requires a variable"),
            diagnostics,
        )
        assertTrue(
            diagnostics.contains("an Int @CallSite format must be exactly"),
            diagnostics,
        )
        assertTrue(
            diagnostics.contains("declarationSourceOf(reference) requires a function or property reference"),
            diagnostics,
        )
        assertTrue(
            diagnostics.contains(
                "declarationSourceOf requires a declaration whose source is available in the current compilation"
            ),
            diagnostics,
        )
        assertTrue(
            diagnostics.contains("declarationSourceOf<T>() requires a class, interface, object, enum, or annotation class"),
            diagnostics,
        )
        assertTrue(
            diagnostics.contains("declarationSourceOf(reference) does not support constructor references"),
            diagnostics,
        )
    }

    @Test
    fun rejectsFunctionCallsInNameOfPropertyReceivers() {
        val result = compileSources(
            mapOf(
                "InvalidReceiver.kt" to """
                    import li.songe.codeorigin.nameOf

                    data class User(val displayName: String)

                    fun createUser(): User = User("Ada")

                    fun invalidReceiver(): String = nameOf(createUser().displayName)
                """.trimIndent(),
            ),
        )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.diagnostics)
        assertTrue(
            result.diagnostics.contains("nameOf(value) requires a variable"),
            result.diagnostics,
        )
    }

    @Test
    fun rejectsConflictingInheritedCallSiteAnnotations() {
        val result = compileSources(
            mapOf(
                "ConflictingCallSite.kt" to """
                    import li.songe.codeorigin.CallSite

                    interface BaseCallSite {
                        fun capture(@CallSite("{line}") value: Int = -1): Int
                    }

                    interface LineCallSite : BaseCallSite {
                        override fun capture(@CallSite("{line}") value: Int): Int
                    }

                    interface ColumnCallSite : BaseCallSite {
                        override fun capture(@CallSite("{column}") value: Int): Int
                    }

                    class ConflictingCallSite : LineCallSite, ColumnCallSite {
                        override fun capture(value: Int): Int = value
                    }

                    fun conflict(): Int = ConflictingCallSite().capture()
                """.trimIndent(),
            ),
        )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.diagnostics)
        assertTrue(
            result.diagnostics.contains("conflicting inherited @CallSite annotations"),
            result.diagnostics,
        )
    }

    private fun compileSources(
        sources: Map<String, String>,
        verifyClasses: ((File) -> Unit)? = null,
    ): CompilationResult {
        val directory = Files.createTempDirectory("codeorigin-diagnostics")
        return try {
            val sourceFiles = sources.map { (name, content) ->
                directory.resolve(name).also { Files.writeString(it, content) }
            }
            val pluginJar = System.getProperty("codeorigin.compiler.jar")
            val apiJar = SourceLocation::class.java.protectionDomain.codeSource.location.toURI().let(::File)
            val stdlibJar = Unit::class.java.protectionDomain.codeSource.location.toURI().let(::File)
            val encodedRoot = URLEncoder.encode(
                directory.toAbsolutePath().toString().replace('\\', '/'),
                StandardCharsets.UTF_8,
            )
            val output = ByteArrayOutputStream()
            val exitCode = PrintStream(output).use { stream ->
                K2JVMCompiler().exec(
                    stream,
                    *sourceFiles.map { it.toString() }.toTypedArray(),
                    "-d", directory.resolve("classes").toString(),
                    "-classpath", listOf(apiJar, stdlibJar).joinToString(File.pathSeparator),
                    "-Xplugin=$pluginJar",
                    "-P", "plugin:$CODEORIGIN_PLUGIN_ID:$PROJECT_ROOT_OPTION=$encodedRoot",
                )
            }
            val result = CompilationResult(
                exitCode = exitCode,
                diagnostics = output.toString(StandardCharsets.UTF_8),
            )
            if (exitCode == ExitCode.OK) {
                verifyClasses?.invoke(directory.resolve("classes").toFile())
            }
            result
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
