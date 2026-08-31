package li.songe.codeorigin.compiler.source

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.path
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal fun normalizedProjectRoot(projectRoot: String): Path? = projectRoot
    .takeIf { it.isNotBlank() }
    ?.let { runCatching { Path.of(it).toAbsolutePath().normalize() }.getOrNull() }

internal class SourceRepository(projectRoot: String) {
    private val root: Path? = normalizedProjectRoot(projectRoot)

    private val sourceCache = HashMap<IrFile, String>()
    private val tokenCache = HashMap<IrFile, List<KotlinSourceLexer.Token>>()

    fun slice(file: IrFile, element: IrElement): Result<String> {
        return slice(
            file,
            element.startOffset,
            element.endOffset,
            expandDeclarationStart = false,
        )
    }

    fun sliceDeclaration(file: IrFile, start: Int, end: Int): Result<String> {
        return slice(file, start, end, expandDeclarationStart = true)
    }

    private fun slice(
        file: IrFile,
        start: Int,
        end: Int,
        expandDeclarationStart: Boolean,
    ): Result<String> {
        if (start == UNDEFINED_OFFSET || end == UNDEFINED_OFFSET) {
            return Result.failure(
                IllegalStateException("the expression does not have source offsets")
            )
        }
        return read(file).mapCatching { source ->
            require(start >= 0 && end >= start && end <= source.length) {
                "source range [$start, $end) is outside ${file.path} (${source.length} characters)"
            }
            val actualStart = if (expandDeclarationStart) {
                declarationStart(file, source, start)
            } else {
                start
            }
            source.substring(actualStart, end)
        }
    }

    private fun declarationStart(file: IrFile, source: String, start: Int): Int {
        val tokens = tokenCache.getOrPut(file) { KotlinSourceLexer.tokenize(source) }
        var low = 0
        var high = tokens.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (tokens[middle].end <= start) low = middle + 1 else high = middle
        }

        var index = low - 1
        var actualStart = start
        while (index >= 0) {
            while (index >= 0 && tokens[index].kind == KotlinSourceLexer.Kind.Trivia) index--
            if (index < 0) break

            val token = tokens[index]
            when {
                token.kind == KotlinSourceLexer.Kind.Modifier -> {
                    actualStart = token.start
                    index--
                }
                token.kind == KotlinSourceLexer.Kind.Documentation -> {
                    actualStart = token.start
                    break
                }
                else -> break
            }
        }
        val lineStart = maxOf(
            source.lastIndexOf('\n', actualStart - 1),
            source.lastIndexOf('\r', actualStart - 1),
        ) + 1
        return if (source.substring(lineStart, actualStart).all(Char::isWhitespace)) {
            lineStart
        } else {
            actualStart
        }
    }

    private fun read(file: IrFile): Result<String> {
        sourceCache[file]?.let { return Result.success(it) }

        val path = resolve(file.path)
            ?: return Result.failure(
                IllegalStateException("source file is unavailable: ${file.path}")
            )
        return runCatching { Files.readString(path) }
            .onSuccess { sourceCache[file] = it }
    }

    private fun resolve(rawPath: String): Path? {
        val path = try {
            Path.of(rawPath)
        } catch (_: InvalidPathException) {
            return null
        }

        val candidates = buildList {
            if (path.isAbsolute) {
                add(path.normalize())
            } else {
                root?.let { add(it.resolve(path).normalize()) }
                add(path.toAbsolutePath().normalize())
            }
        }
        return candidates.distinct().firstOrNull { Files.isRegularFile(it) }
    }
}
