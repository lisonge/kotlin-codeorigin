package li.songe.codeorigin.compiler.ir

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

    fun resolveType(call: IrCall): String? {
        return (call.typeArguments.firstOrNull()?.classifierOrNull?.owner as? IrDeclarationWithName)
            ?.name
            ?.sourceName()
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
