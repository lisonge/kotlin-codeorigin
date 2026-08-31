package li.songe.codeorigin.compiler.source

import org.jetbrains.kotlin.backend.common.serialization.mangle.ir.isAnonymous
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.declarations.path
import java.nio.file.Path

internal data class SourceContext(
    val path: String,
    val file: String,
    val packageName: String,
    val name: String,
    val owner: String,
    val qualifiedOwner: String,
    val type: String,
    val function: String,
    val line: Int,
    val column: Int,
    val offset: Int,
)

internal class SourceContextFactory(projectRoot: String) {
    private data class FileContext(
        val path: String,
        val file: String,
        val packageName: String,
    )

    private data class DeclarationContext(
        val name: String,
        val owner: String,
        val qualifiedOwner: String,
        val type: String,
        val function: String,
    )

    private val root: Path? = normalizedProjectRoot(projectRoot)
    private val fileContexts = HashMap<IrFile, FileContext>()
    private val declarationContexts = HashMap<IrDeclarationBase, DeclarationContext>()

    fun create(file: IrFile, declarations: List<IrDeclarationBase>, element: IrElement): SourceContext {
        val fileContext = fileContexts.getOrPut(file) {
            FileContext(
                path = relativeSourcePath(root, file.path),
                file = file.name,
                packageName = file.packageFqName.asString(),
            )
        }
        val declarationContext = declarations.lastOrNull()?.let { declaration ->
            declarationContexts.getOrPut(declaration) {
                createDeclarationContext(fileContext.packageName, declarations)
            }
        } ?: createDeclarationContext(fileContext.packageName, declarations)

        return SourceContext(
            path = fileContext.path,
            file = fileContext.file,
            packageName = fileContext.packageName,
            name = declarationContext.name,
            owner = declarationContext.owner,
            qualifiedOwner = declarationContext.qualifiedOwner,
            type = declarationContext.type,
            function = declarationContext.function,
            line = file.fileEntry.getLineNumber(element.startOffset) + 1,
            column = file.fileEntry.getColumnNumber(element.startOffset) + 1,
            offset = element.startOffset,
        )
    }

    private fun createDeclarationContext(
        packageName: String,
        declarations: List<IrDeclarationBase>,
    ): DeclarationContext {
        val ownerSegments = buildOwnerSegments(declarations)
        val owner = ownerSegments.joinToString(".")
        val qualifiedOwner = when {
            packageName.isEmpty() -> owner
            owner.isEmpty() -> packageName
            else -> "$packageName.$owner"
        }

        val type = declarations
            .filterIsInstance<IrClass>()
            .mapNotNull { declarationName(it) }
            .joinToString(".")

        val function = declarations
            .filterIsInstance<IrFunction>()
            .lastOrNull()
            ?.let { functionName(it) }
            .orEmpty()

        return DeclarationContext(
            name = ownerSegments.lastOrNull().orEmpty(),
            owner = owner,
            qualifiedOwner = qualifiedOwner,
            type = type,
            function = function,
        )
    }

    private fun buildOwnerSegments(declarations: List<IrDeclarationBase>): List<String> = buildList {
        for (declaration in declarations) {
            when (declaration) {
                is IrClass -> declarationName(declaration)?.let(::add)
                is IrProperty -> declarationName(declaration)?.let(::add)
                is IrConstructor -> add("constructor")
                is IrSimpleFunction -> {
                    val property = declaration.correspondingPropertySymbol?.owner
                    if (property != null) {
                        val propertyName = property.name.asString()
                        if (lastOrNull() != propertyName) add(propertyName)
                        add(if (declaration == property.getter) "get" else "set")
                    } else {
                        declarationName(declaration)?.let(::add)
                    }
                }
                is IrFunction -> declarationName(declaration)?.let(::add)
                is IrAnonymousInitializer -> Unit
            }
        }
    }

    private fun functionName(function: IrFunction): String = when (function) {
        is IrConstructor -> "constructor"
        is IrSimpleFunction -> {
            val property = function.correspondingPropertySymbol?.owner
            if (property == null) {
                declarationName(function).orEmpty()
            } else {
                val suffix = if (function == property.getter) "get" else "set"
                "${property.name.asString()}.$suffix"
            }
        }
    }

    private fun declarationName(declaration: org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName): String? {
        val name = declaration.name
        if (name.isSpecial || name.isAnonymous) return null
        return name.asString()
    }

}

internal fun relativeSourcePath(root: Path?, rawPath: String): String {
    val raw = runCatching { Path.of(rawPath) }.getOrNull()
    val source = raw?.let { path ->
        if (path.isAbsolute) path.normalize() else root?.resolve(path)?.normalize() ?: path.normalize()
    }
    val result = when {
        source == null -> rawPath
            .substringAfterLast('/')
            .substringAfterLast('\\')
        root != null -> runCatching { root.relativize(source).toString() }
            .getOrElse { source.fileName?.toString().orEmpty() }
        raw?.isAbsolute == true -> source.fileName?.toString().orEmpty()
        else -> source.toString()
    }
    return result.replace('\\', '/')
}
