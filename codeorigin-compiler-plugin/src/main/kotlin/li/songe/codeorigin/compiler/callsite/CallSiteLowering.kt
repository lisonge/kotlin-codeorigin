package li.songe.codeorigin.compiler.callsite

import li.songe.codeorigin.compiler.CALL_SITE_FQ_NAME
import li.songe.codeorigin.compiler.DEFAULT_CALL_SITE_FORMAT
import li.songe.codeorigin.compiler.SOURCE_LOCATION_CLASS_ID
import li.songe.codeorigin.compiler.SOURCE_LOCATION_FQ_NAME
import li.songe.codeorigin.compiler.ir.CodeOriginDiagnostics
import li.songe.codeorigin.compiler.source.SourceContext
import li.songe.codeorigin.compiler.source.SourceContextFactory
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation

internal class CallSiteLowering(
    private val pluginContext: IrPluginContext,
    projectRoot: String,
    private val diagnostics: CodeOriginDiagnostics,
) {
    private data class ParameterMetadata(
        val typeName: String?,
        val formatValue: String,
    )

    private sealed interface ParameterResolution {
        data object None : ParameterResolution
        data object Conflict : ParameterResolution
        data class Resolved(
            val parameter: IrValueParameter,
            val metadata: ParameterMetadata,
        ) : ParameterResolution
    }

    private val invalidParameters = hashSetOf<IrValueParameterSymbol>()
    private val parameterMetadata = HashMap<IrValueParameterSymbol, ParameterMetadata>()
    private val inheritedResolutions = HashMap<IrValueParameterSymbol, ParameterResolution>()
    private val formatCache = HashMap<String, Result<CallSiteFormat>>()
    private val sourceLocationConstructors = HashMap<IrFile, IrConstructorSymbol>()
    private val contextFactory = SourceContextFactory(projectRoot)

    private val stringType get() = pluginContext.irBuiltIns.stringType
    private val intType get() = pluginContext.irBuiltIns.intType

    fun validate(function: IrFunction, file: IrFile?) {
        for (parameter in function.parameters) {
            if (parameter.kind != IrParameterKind.Regular || !parameter.hasAnnotation(CALL_SITE_FQ_NAME)) {
                continue
            }
            val error = validateParameter(parameter, requireDefault = true)
            if (error != null) {
                invalidParameters += parameter.symbol
                diagnostics.report(file, parameter, error)
            }
        }
    }

    fun inject(
        expression: IrCall,
        file: IrFile?,
        declarations: List<IrDeclarationBase>,
    ) {
        val function = expression.symbol.owner
        val parameters = function.parameters
        var context: SourceContext? = null
        var regularIndex = 0
        for (index in parameters.indices) {
            val parameter = parameters[index]
            if (parameter.kind != IrParameterKind.Regular) continue
            val currentRegularIndex = regularIndex++
            if (
                expression.arguments[index] != null ||
                parameter.symbol in invalidParameters
            ) {
                continue
            }
            val resolution = resolveParameter(function, parameter, currentRegularIndex)
            val resolved = when (resolution) {
                ParameterResolution.None -> continue
                ParameterResolution.Conflict -> {
                    diagnostics.report(
                        file,
                        expression,
                        "conflicting inherited @CallSite annotations for parameter ${parameter.name}",
                    )
                    continue
                }
                is ParameterResolution.Resolved -> resolution
            }
            if (resolved.parameter.symbol in invalidParameters) continue
            val error = validateParameter(resolved.parameter, requireDefault = false)
            if (error != null) {
                diagnostics.report(file, expression, error)
                continue
            }
            val sourceContext = context ?: run {
                val sourceFile = file ?: return
                contextFactory.create(sourceFile, declarations, expression).also { context = it }
            }
            expression.arguments[index] = callSiteValue(
                resolved.metadata,
                sourceContext,
                expression,
                file,
                declarations,
            )
        }
    }

    private fun resolveParameter(
        function: IrFunction,
        parameter: IrValueParameter,
        regularIndex: Int,
    ): ParameterResolution {
        if (parameter.hasAnnotation(CALL_SITE_FQ_NAME)) {
            return ParameterResolution.Resolved(parameter, metadata(parameter))
        }
        if (function !is IrSimpleFunction || function.overriddenSymbols.isEmpty()) {
            return ParameterResolution.None
        }
        return inheritedResolutions.getOrPut(parameter.symbol) {
            resolveInheritedParameter(function, regularIndex)
        }
    }

    private fun resolveInheritedParameter(
        function: IrSimpleFunction,
        regularIndex: Int,
    ): ParameterResolution {
        var resolved: ParameterResolution.Resolved? = null
        val seenFunctions = hashSetOf(function.symbol)
        val pending = ArrayDeque<IrSimpleFunction>()
        function.overriddenSymbols.mapTo(pending) { it.owner }

        while (pending.isNotEmpty()) {
            val overridden = pending.removeFirst()
            if (!seenFunctions.add(overridden.symbol)) continue
            val inheritedParameter = overridden.parameters
                .asSequence()
                .filter { it.kind == IrParameterKind.Regular }
                .elementAtOrNull(regularIndex)
            if (
                inheritedParameter != null &&
                inheritedParameter.hasAnnotation(CALL_SITE_FQ_NAME)
            ) {
                val inheritedMetadata = metadata(inheritedParameter)
                val previous = resolved
                if (previous != null && previous.metadata != inheritedMetadata) {
                    return ParameterResolution.Conflict
                }
                if (previous == null) {
                    resolved = ParameterResolution.Resolved(inheritedParameter, inheritedMetadata)
                }
            }
            overridden.overriddenSymbols.mapTo(pending) { it.owner }
        }
        return resolved ?: ParameterResolution.None
    }

    private fun callSiteValue(
        metadata: ParameterMetadata,
        context: SourceContext,
        expression: IrCall,
        file: IrFile?,
        declarations: List<IrDeclarationBase>,
    ): IrExpression = when (metadata.typeName) {
        "kotlin.String" -> {
            val callSiteFormat = format(
                metadata.formatValue.ifEmpty { DEFAULT_CALL_SITE_FORMAT }
            ).getOrThrow()
            IrConstImpl.string(
                expression.startOffset,
                expression.endOffset,
                stringType,
                callSiteFormat.render(context),
            )
        }
        "kotlin.Int" -> {
            val callSiteFormat = format(metadata.formatValue).getOrThrow()
            IrConstImpl.int(
                expression.startOffset,
                expression.endOffset,
                intType,
                callSiteFormat.renderInt(context),
            )
        }
        SOURCE_LOCATION_FQ_NAME -> createSourceLocation(context, expression, file, declarations)
        else -> error("unsupported @CallSite parameter type: ${metadata.typeName}")
    }

    private fun createSourceLocation(
        context: SourceContext,
        expression: IrCall,
        file: IrFile?,
        declarations: List<IrDeclarationBase>,
    ): IrExpression {
        val sourceFile = file ?: error("SourceLocation cannot be created outside a source file")
        val constructor = sourceLocationConstructor(sourceFile)
        val scope = declarations.lastOrNull()?.symbol
            ?: error("SourceLocation cannot be created outside a declaration")
        val builder = DeclarationIrBuilder(
            pluginContext,
            scope,
            expression.startOffset,
            expression.endOffset,
        )
        val call = builder.irCallConstructor(constructor, emptyList())
        constructor.owner.parameters.forEachIndexed { index, parameter ->
            if (parameter.kind != IrParameterKind.Regular) return@forEachIndexed
            call.arguments[index] = when (parameter.name.asString()) {
                "path" -> builder.irString(context.path)
                "file" -> builder.irString(context.file)
                "packageName" -> builder.irString(context.packageName)
                "name" -> builder.irString(context.name)
                "owner" -> builder.irString(context.owner)
                "qualifiedOwner" -> builder.irString(context.qualifiedOwner)
                "type" -> builder.irString(context.type)
                "function" -> builder.irString(context.function)
                "line" -> builder.irInt(context.line)
                "column" -> builder.irInt(context.column)
                "offset" -> builder.irInt(context.offset)
                else -> error("Unknown SourceLocation constructor parameter: ${parameter.name}")
            }
        }
        return call
    }

    private fun validateParameter(
        parameter: IrValueParameter,
        requireDefault: Boolean,
    ): String? {
        if (requireDefault && !hasEffectiveDefault(parameter)) {
            return "@CallSite requires a parameter with a default value"
        }
        if (parameter.varargElementType != null) {
            return "@CallSite cannot be used on a vararg parameter"
        }

        val metadata = metadata(parameter)
        return when (metadata.typeName) {
            "kotlin.String" -> format(metadata.formatValue.ifEmpty { DEFAULT_CALL_SITE_FORMAT })
                .exceptionOrNull()
                ?.message
            "kotlin.Int" -> when {
                metadata.formatValue.isEmpty() ->
                    "an Int @CallSite parameter requires {line}, {column}, or {offset}"
                else -> format(metadata.formatValue).fold(
                    onSuccess = {
                        if (it.isInteger) {
                            null
                        } else {
                            "an Int @CallSite format must be exactly {line}, {column}, or {offset}"
                        }
                    },
                    onFailure = { it.message },
                )
            }
            SOURCE_LOCATION_FQ_NAME -> if (metadata.formatValue.isEmpty()) {
                null
            } else {
                "a SourceLocation @CallSite parameter does not accept a custom format"
            }
            else ->
                "@CallSite parameter type must be kotlin.String, kotlin.Int, or $SOURCE_LOCATION_FQ_NAME"
        }
    }

    private fun hasEffectiveDefault(parameter: IrValueParameter): Boolean =
        hasEffectiveDefault(parameter, hashSetOf())

    private fun hasEffectiveDefault(
        parameter: IrValueParameter,
        visited: MutableSet<IrValueParameterSymbol>,
    ): Boolean {
        if (!visited.add(parameter.symbol)) return false
        if (parameter.defaultValue != null) return true

        val function = parameter.parent as? IrSimpleFunction ?: return false
        val regularIndex = function.parameters
            .asSequence()
            .filter { it.kind == IrParameterKind.Regular }
            .indexOfFirst { it === parameter }
        if (regularIndex < 0) return false
        return function.overriddenSymbols.any { symbol ->
            val overriddenParameter = symbol.owner.parameters
                .asSequence()
                .filter { it.kind == IrParameterKind.Regular }
                .elementAtOrNull(regularIndex)
            overriddenParameter != null && hasEffectiveDefault(overriddenParameter, visited)
        }
    }

    private fun annotationFormat(parameter: IrValueParameter): String =
        (parameter.getAnnotation(CALL_SITE_FQ_NAME)?.arguments?.firstOrNull() as? IrConst)
            ?.value as? String ?: ""

    private fun metadata(parameter: IrValueParameter): ParameterMetadata =
        parameterMetadata.getOrPut(parameter.symbol) {
            ParameterMetadata(
                typeName = parameter.type.classFqName?.asString(),
                formatValue = annotationFormat(parameter),
            )
        }

    private fun sourceLocationConstructor(file: IrFile): IrConstructorSymbol =
        sourceLocationConstructors.getOrPut(file) {
            pluginContext.finderForSource(file).findConstructors(SOURCE_LOCATION_CLASS_ID)
                .single { constructor ->
                    constructor.owner.parameters.count { it.kind == IrParameterKind.Regular } == 11
                }
        }

    private fun format(value: String): Result<CallSiteFormat> =
        formatCache.getOrPut(value) { CallSiteFormat.parse(value) }
}
