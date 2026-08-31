package li.songe.codeorigin.compiler.source

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceContextTest {
    @Test
    fun createsRelocatableSourcePaths() {
        val root = Path.of("workspace", "project").toAbsolutePath().normalize()

        assertEquals(
            "src/main/kotlin/Inside.kt",
            relativeSourcePath(root, root.resolve("src/main/kotlin/Inside.kt")),
        )
        assertEquals(
            "../shared/Outside.kt",
            relativeSourcePath(root, root.parent.resolve("shared/Outside.kt")),
        )
        assertEquals(
            "Absolute.kt",
            relativeSourcePath(null, root.resolve("Absolute.kt")),
        )
        assertEquals(
            "relative/Source.kt",
            relativeSourcePath(null, Path.of("relative/Source.kt")),
        )
        assertEquals("Invalid.kt", relativeSourcePath(null, "invalid\u0000/Invalid.kt"))
    }

    private fun relativeSourcePath(root: Path?, source: Path): String =
        relativeSourcePath(root, source.toString())
}
