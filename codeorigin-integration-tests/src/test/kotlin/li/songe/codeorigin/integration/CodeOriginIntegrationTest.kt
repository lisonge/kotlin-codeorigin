package li.songe.codeorigin.integration

import li.songe.codeorigin.CallSite
import li.songe.codeorigin.SourceLocation
import li.songe.codeorigin.declarationSourceOf
import li.songe.codeorigin.evalSourceOf
import li.songe.codeorigin.nameOf
import li.songe.codeorigin.sourceOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class User(val displayName: String)

private typealias UserAlias = User

private enum class Mode {
    Fast,
}

private object Marker

private fun helper(): Unit = Unit

private val declarationProperty: String = "property"

private
fun splitModifierDeclaration(): Unit = Unit

@Target(AnnotationTarget.PROPERTY_GETTER)
private annotation class GetterMarker

@get:GetterMarker
private val getterAnnotatedDeclaration: String = "annotated"

/** Declaration documentation. */
private fun documentedDeclaration(): Unit = Unit

@Suppress("unused")
private fun annotatedDeclaration(): Unit = Unit

private class DeclarationContainer {
    class Nested {
        val value: Int = 1
    }
}

private interface InheritedCallSite {
    fun capture(@CallSite("{line}") line: Int = -1): Int
}

private class InheritedCallSiteImplementation : InheritedCallSite {
    override fun capture(line: Int): Int = line
}

class CodeOriginIntegrationTest {
    @Test
    fun resolvesNamesWithoutEvaluatingValues() {
        val user = User("Ada")
        val `when` = 1

        assertEquals("user", nameOf(user))
        assertEquals("displayName", nameOf(user.displayName))
        assertEquals("Fast", nameOf(Mode.Fast))
        assertEquals("Marker", nameOf(Marker))
        assertEquals("helper", nameOf(::helper))
        assertEquals("displayName", nameOf(User::displayName))
        assertEquals("User", nameOf<User>())
        assertEquals("UserAlias", nameOf<UserAlias>())
        assertEquals("UserAlias", nameOf</* source comment */ UserAlias>())
        assertEquals("when", nameOf(`when`))
    }

    @Test
    fun capturesSourceWithoutEvaluatingIt() {
        var sideEffect = 0
        val nullableName: String? = null

        assertEquals("sideEffect++", sourceOf(sideEffect++))
        assertEquals(0, sideEffect)
        assertEquals(
            "nullableName ?: \"unknown\"",
            sourceOf(nullableName ?: "unknown"),
        )
    }

    @Test
    fun capturesSourceAndEvaluatesItExactlyOnce() {
        var count = 0

        val (source, value) = evalSourceOf(count++)

        assertEquals("count++", source)
        assertEquals(0, value)
        assertEquals(1, count)

        val nullable = evalSourceOf(null as String?)
        assertEquals("null as String?", nullable.first)
        assertNull(nullable.second)

        assertFailsWith<IllegalStateException> {
            evalSourceOf(error("boom"))
        }
    }

    @Test
    fun capturesDeclarationsFromTheCurrentModule() {
        assertEquals(
            "private data class User(val displayName: String)",
            declarationSourceOf<User>(),
        )
        assertEquals("private fun helper(): Unit = Unit", declarationSourceOf(::helper))
        assertEquals(
            "private val declarationProperty: String = \"property\"",
            declarationSourceOf(::declarationProperty),
        )
        assertEquals("val displayName: String", declarationSourceOf(User::displayName))
        assertEquals(
            "private\nfun splitModifierDeclaration(): Unit = Unit",
            declarationSourceOf(::splitModifierDeclaration),
        )
        assertEquals(
            """
                @get:GetterMarker
                private val getterAnnotatedDeclaration: String = "annotated"
            """.trimIndent(),
            declarationSourceOf(::getterAnnotatedDeclaration),
        )
        assertEquals(
            """
                /** Declaration documentation. */
                private fun documentedDeclaration(): Unit = Unit
            """.trimIndent(),
            declarationSourceOf(::documentedDeclaration),
        )
        assertEquals(
            """
                @Suppress("unused")
                private fun annotatedDeclaration(): Unit = Unit
            """.trimIndent(),
            declarationSourceOf(::annotatedDeclaration),
        )
        assertEquals(
            """
                private class DeclarationContainer {
                    class Nested {
                        val value: Int = 1
                    }
                }
            """.trimIndent(),
            declarationSourceOf<DeclarationContainer>(),
        )
        assertEquals(
            """
                class Nested {
                    val value: Int = 1
                }
            """.trimIndent(),
            declarationSourceOf<DeclarationContainer.Nested>(),
        )
    }

    @Test
    fun injectsStructuredAndFormattedCallSites() {
        val captured = captureCallSite()

        assertTrue(captured.location.path.endsWith("CodeOriginIntegrationTest.kt"))
        assertEquals("CodeOriginIntegrationTest.kt", captured.location.file)
        assertEquals("li.songe.codeorigin.integration", captured.location.packageName)
        assertEquals("CodeOriginIntegrationTest.injectsStructuredAndFormattedCallSites", captured.location.owner)
        assertEquals(
            "li.songe.codeorigin.integration.CodeOriginIntegrationTest.injectsStructuredAndFormattedCallSites",
            captured.location.qualifiedOwner,
        )
        assertEquals("CodeOriginIntegrationTest", captured.location.type)
        assertEquals("injectsStructuredAndFormattedCallSites", captured.location.function)
        assertEquals(captured.location.line, captured.line)
        assertEquals("CodeOriginIntegrationTest.kt:${captured.line}", captured.text)
        assertTrue(captured.location.column > 0)
        assertTrue(captured.location.offset >= 0)

        val later = captureCallSite()
        assertEquals(captured.location.owner, later.location.owner)
        assertTrue(later.location.line > captured.location.line)
        assertTrue(later.location.offset > captured.location.offset)

        val defaultText = captureDefaultCallSite()
        assertTrue(
            defaultText.startsWith(
                "li.songe.codeorigin.integration.CodeOriginIntegrationTest" +
                    ".injectsStructuredAndFormattedCallSites(CodeOriginIntegrationTest.kt:"
            )
        )
        assertTrue(defaultText.endsWith(')'))
    }

    @Test
    fun preservesExplicitCallSiteArguments() {
        val manual = SourceLocation(file = "manual.kt", line = 7)
        val captured = captureCallSite(manual, "manual", 7)

        assertEquals(manual, captured.location)
        assertEquals("manual", captured.text)
        assertEquals(7, captured.line)
    }

    @Test
    fun inheritsCallSiteAnnotationsFromOverriddenParameters() {
        val implementation = InheritedCallSiteImplementation()

        assertTrue(implementation.capture() > 0)
        assertTrue((implementation as InheritedCallSite).capture() > 0)
        assertEquals(7, implementation.capture(7))
    }
}
