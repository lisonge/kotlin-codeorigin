package li.songe.codeorigin.compiler.ir

import li.songe.codeorigin.compiler.source.KotlinSourceLexer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.types.classifierOrNull

internal object NameResolver {
    fun resolve(expression: IrExpression): String? = when (expression) {
        is IrGetValue -> expression.symbol.owner.name.sourceName()
        is IrGetField -> (
            expression.symbol.owner.correspondingPropertySymbol?.owner?.name
                ?: expression.symbol.owner.name
            ).sourceName()?.takeIf { isNamedReceiver(expression.receiver) }
        is IrGetObjectValue -> expression.symbol.owner.name.sourceName()
        is IrGetEnumValue -> expression.symbol.owner.name.sourceName()
        is IrPropertyReference -> expression.symbol.owner.name.sourceName()
        is IrFunctionReference -> expression.symbol.owner.name.sourceName()
        is IrCall -> expression.symbol.owner.correspondingPropertySymbol?.owner?.name?.sourceName()
            ?.takeIf { expression.hasNamedReceivers() }
        else -> null
    }

    fun resolveType(call: IrCall, callSource: String?): String? {
        callSource?.let(::typeNameFromCall)?.let { return it }
        return (call.typeArguments.firstOrNull()?.classifierOrNull?.owner as? IrDeclarationWithName)
            ?.name
            ?.sourceName()
    }

    private fun typeNameFromCall(source: String): String? {
        val tokens = KotlinSourceLexer.tokenize(source).filterNot { token ->
            token.kind == KotlinSourceLexer.Kind.Trivia ||
                token.kind == KotlinSourceLexer.Kind.Documentation
        }
        val functionName = tokens.indexOfFirst { token ->
            token.kind == KotlinSourceLexer.Kind.Identifier && token.text(source) == "nameOf"
        }
        if (functionName < 0) return null
        val open = (functionName + 1 until tokens.size)
            .firstOrNull { tokens[it].kind == KotlinSourceLexer.Kind.Less }
            ?: -1
        if (open < 0) return null

        var depth = 1
        var closed = false
        val outerTypeTokens = mutableListOf<KotlinSourceLexer.Token>()
        for (index in open + 1 until tokens.size) {
            val token = tokens[index]
            when (token.kind) {
                KotlinSourceLexer.Kind.Less -> depth++
                KotlinSourceLexer.Kind.Greater -> {
                    depth--
                    if (depth == 0) {
                        closed = true
                        break
                    }
                }
                else -> if (depth == 1) outerTypeTokens += token
            }
        }
        if (!closed || outerTypeTokens.isEmpty()) return null
        if (outerTypeTokens.first().kind == KotlinSourceLexer.Kind.LeftParenthesis) return null
        if (outerTypeTokens.any { it.kind == KotlinSourceLexer.Kind.Arrow }) return null

        return outerTypeTokens.lastOrNull { it.kind == KotlinSourceLexer.Kind.Identifier }
            ?.text(source)
            ?.removeSurrounding("`")
    }

    private fun isNamedReceiver(expression: IrExpression?): Boolean = when (expression) {
        null -> true
        is IrGetValue -> true
        is IrGetObjectValue -> true
        is IrGetEnumValue -> true
        is IrGetField -> isNamedReceiver(expression.receiver)
        is IrCall ->
            expression.symbol.owner.correspondingPropertySymbol != null &&
                expression.hasNamedReceivers()
        is IrTypeOperatorCall -> expression.operator in implicitReceiverOperators &&
            isNamedReceiver(expression.argument)
        else -> false
    }

    private fun IrCall.hasNamedReceivers(): Boolean =
        symbol.owner.parameters.indices.all { index ->
            symbol.owner.parameters[index].kind == org.jetbrains.kotlin.ir.declarations.IrParameterKind.Regular ||
                isNamedReceiver(arguments[index])
        }

    private val implicitReceiverOperators = setOf(
        IrTypeOperator.IMPLICIT_CAST,
        IrTypeOperator.IMPLICIT_NOTNULL,
        IrTypeOperator.IMPLICIT_DYNAMIC_CAST,
    )

    private fun org.jetbrains.kotlin.name.Name.sourceName(): String? {
        if (isSpecial) return null
        return asString().removeSurrounding("`")
    }
}
