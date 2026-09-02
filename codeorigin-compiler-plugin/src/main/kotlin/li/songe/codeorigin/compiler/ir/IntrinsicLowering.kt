package li.songe.codeorigin.compiler.ir

import li.songe.codeorigin.compiler.PAIR_CLASS_ID
import li.songe.codeorigin.compiler.source.SourceRepository
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.classifierOrNull

internal class IntrinsicLowering(
    private val pluginContext: IrPluginContext,
    projectRoot: String,
    private val moduleFiles: Set<IrFile>,
    private val diagnostics: CodeOriginDiagnostics,
) {
    private val sourceRepository = SourceRepository(projectRoot)
    private var pairConstructor: IrConstructorSymbol? = null

    private val stringType get() = pluginContext.irBuiltIns.stringType

    fun transformNameOf(expression: IrCall, file: IrFile?): IrExpression {
        val regularParameters = expression.symbol.owner.parameters
            .filter { it.kind == IrParameterKind.Regular }
        val name = if (regularParameters.isEmpty()) {
            val source = file?.let { sourceRepository.slice(it, expression).getOrNull() }
            NameResolver.resolveType(expression, source)
        } else {
            val argumentIndex = expression.symbol.owner.parameters.indexOf(regularParameters.single())
            expression.arguments[argumentIndex]?.let(NameResolver::resolve)
        }

        if (name == null) {
            diagnostics.report(
                file,
                expression,
                if (regularParameters.isEmpty()) {
                    "nameOf<T>() requires a named class, interface, object, type alias, or type parameter"
                } else {
                    "nameOf(value) requires a variable, parameter, property, object, enum entry, or callable reference"
                },
            )
        }
        return IrConstImpl.string(
            expression.startOffset,
            expression.endOffset,
            stringType,
            name.orEmpty(),
        )
    }

    fun transformSourceOf(expression: IrCall, file: IrFile?): IrExpression {
        val argument = expression.regularArgument()
        val source = if (argument == null) {
            Result.failure(IllegalStateException("sourceOf argument is missing"))
        } else {
            sourceOf(file, argument, "sourceOf")
        }

        return IrConstImpl.string(
            expression.startOffset,
            expression.endOffset,
            stringType,
            sourceText(file, expression, "sourceOf", source),
        )
    }

    fun transformEvalSourceOf(
        expression: IrCall,
        file: IrFile?,
        declarations: List<IrDeclarationBase>,
        transformArgument: (IrExpression) -> IrExpression,
    ): IrExpression {
        val argument = expression.regularArgument()
        if (argument == null) {
            diagnostics.report(file, expression, "evalSourceOf argument is missing")
            return expression
        }

        val source = sourceText(
            file,
            expression,
            "evalSourceOf",
            sourceOf(file, argument, "evalSourceOf"),
        )
        val transformedArgument = transformArgument(argument)
        val valueType = expression.typeArguments.singleOrNull() ?: transformedArgument.type
        val constructor = pairConstructor()
        val scope = declarations.lastOrNull()?.symbol
            ?: error("evalSourceOf cannot be created outside a declaration")
        val builder = DeclarationIrBuilder(
            pluginContext,
            scope,
            expression.startOffset,
            expression.endOffset,
        )
        val call = builder.irCallConstructor(constructor, listOf(stringType, valueType))
        val parameterIndexes = constructor.owner.parameters.mapIndexedNotNull { index, parameter ->
            index.takeIf { parameter.kind == IrParameterKind.Regular }
        }
        call.arguments[parameterIndexes[0]] = builder.irString(source)
        call.arguments[parameterIndexes[1]] = transformedArgument
        return call
    }

    fun transformDeclarationSourceOf(expression: IrCall, file: IrFile?): IrExpression {
        val hasReferenceParameter = expression.symbol.owner.parameters.any {
            it.kind == IrParameterKind.Regular
        }
        val declaration = if (hasReferenceParameter) {
            when (val reference = expression.regularArgument()) {
                is IrFunctionReference -> if (reference.symbol.owner is IrConstructor) {
                    diagnostics.report(
                        file,
                        expression,
                        "declarationSourceOf(reference) does not support constructor references",
                    )
                    null
                } else {
                    reference.symbol.owner
                }
                is IrPropertyReference -> reference.symbol.owner
                else -> {
                    diagnostics.report(
                        file,
                        expression,
                        "declarationSourceOf(reference) requires a function or property reference",
                    )
                    null
                }
            }
        } else {
            val classifier = expression.typeArguments.singleOrNull()?.classifierOrNull?.owner
            if (classifier is IrClass) {
                classifier
            } else {
                diagnostics.report(
                    file,
                    expression,
                    "declarationSourceOf<T>() requires a class, interface, object, enum, or annotation class",
                )
                null
            }
        }

        val value = declaration?.let { target ->
            val declarationFile = declarationFile(target)
            if (declarationFile == null || declarationFile !in moduleFiles) {
                diagnostics.report(
                    file,
                    expression,
                    "declarationSourceOf requires a declaration whose source is available in the current compilation",
                )
                ""
            } else {
                declarationSourceText(
                    file,
                    expression,
                    sourceRepository.sliceDeclaration(
                        declarationFile,
                        declarationSourceStart(target),
                        target.endOffset,
                    ),
                )
            }
        }.orEmpty()

        return IrConstImpl.string(
            expression.startOffset,
            expression.endOffset,
            stringType,
            value,
        )
    }

    private fun declarationFile(declaration: IrDeclarationBase): IrFile? {
        var parent = declaration.parent
        while (parent is IrDeclaration) parent = parent.parent
        return parent as? IrFile
    }

    private fun declarationSourceStart(declaration: IrDeclarationBase): Int {
        var start = declaration.startOffset

        fun includeAnnotations(target: IrDeclarationBase?) {
            for (annotation in target?.annotations.orEmpty()) {
                val annotationStart = annotation.startOffset
                if (annotationStart >= 0 && (start < 0 || annotationStart < start)) {
                    start = annotationStart
                }
            }
        }

        includeAnnotations(declaration)
        if (declaration is IrProperty) {
            includeAnnotations(declaration.backingField)
            includeAnnotations(declaration.getter)
            includeAnnotations(declaration.setter)
        }
        return start
    }

    private fun IrCall.regularArgument(): IrExpression? {
        val parameter = symbol.owner.parameters.single { it.kind == IrParameterKind.Regular }
        return arguments[symbol.owner.parameters.indexOf(parameter)]
    }

    private fun sourceOf(
        file: IrFile?,
        argument: IrExpression,
        intrinsic: String,
    ): Result<String> {
        val sourceFile = file
            ?: return Result.failure(IllegalStateException("$intrinsic is not inside a source file"))
        return sourceRepository.slice(sourceFile, argument)
    }

    private fun sourceText(
        file: IrFile?,
        expression: IrCall,
        intrinsic: String,
        source: Result<String>,
    ): String = source.fold(
        onSuccess = { it.trim().normalizeLineEndings() },
        onFailure = {
            diagnostics.report(
                file,
                expression,
                "$intrinsic could not read the original expression: ${it.message}",
            )
            ""
        },
    )

    private fun declarationSourceText(
        file: IrFile?,
        expression: IrCall,
        source: Result<String>,
    ): String = source.fold(
        onSuccess = {
            it.normalizeLineEndings()
                .trimIndent()
                .trim()
        },
        onFailure = {
            diagnostics.report(
                file,
                expression,
                "declarationSourceOf could not read the original declaration: ${it.message}",
            )
            ""
        },
    )

    private fun String.normalizeLineEndings(): String =
        replace("\r\n", "\n").replace('\r', '\n')

    private fun pairConstructor(): IrConstructorSymbol = pairConstructor
        ?: pluginContext.finderForBuiltins().findConstructors(PAIR_CLASS_ID)
            .single { candidate ->
                candidate.owner.parameters.count { it.kind == IrParameterKind.Regular } == 2
            }
            .also { pairConstructor = it }
}
