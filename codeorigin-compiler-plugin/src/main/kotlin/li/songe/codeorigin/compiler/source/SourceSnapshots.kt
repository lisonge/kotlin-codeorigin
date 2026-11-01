package li.songe.codeorigin.compiler.source

import li.songe.codeorigin.compiler.NAME_OF_FQ_NAME
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.backend.FirMetadataSource
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.abbreviatedTypeOrSelf
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.text

/** Captured during FIR analysis, before FIR-to-IR can discard original type references. */
internal class SourceSnapshots {
    private data class CallRange(val start: Int, val end: Int)

    private data class Snapshot(val source: String?, val names: Map<CallRange, String>)

    private val snapshots = HashMap<FirFile, Snapshot>()

    fun capture(file: FirFile) {
        snapshots[file] = Snapshot(file.source?.text?.toString(), collect(file))
    }

    fun source(file: IrFile): String? = snapshot(file)?.source

    private fun snapshot(file: IrFile): Snapshot? =
        (file.metadata as? FirMetadataSource.File)?.fir?.let(snapshots::get)

    fun typeName(call: IrCall, file: IrFile?): String? {
        if (file == null) return null
        return snapshot(file)?.names?.get(CallRange(call.startOffset, call.endOffset))
    }

    private fun collect(fir: FirFile): Map<CallRange, String> {
        val names = mutableMapOf<CallRange, String>()
        fir.accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                element.acceptChildren(this)
            }

            override fun visitFunctionCall(functionCall: FirFunctionCall) {
                val symbol = (functionCall.calleeReference as? FirResolvedNamedReference)
                    ?.resolvedSymbol as? FirCallableSymbol<*>
                if (symbol?.callableId?.asSingleFqName()?.asString() == NAME_OF_FQ_NAME &&
                    functionCall.argumentList.arguments.isEmpty()
                ) {
                    val typeRef = (functionCall.typeArguments.singleOrNull() as? FirTypeProjectionWithVariance)
                        ?.typeRef
                    val userType = (typeRef as? FirResolvedTypeRef)?.delegatedTypeRef as? FirUserTypeRef
                        ?: typeRef as? FirUserTypeRef
                    val resolvedType = (typeRef as? FirResolvedTypeRef)?.coneType?.abbreviatedTypeOrSelf
                    val name = userType?.qualifier?.lastOrNull()?.name
                        ?: (resolvedType as? ConeClassLikeType)?.lookupTag?.classId?.shortClassName
                    val source = functionCall.source
                    if (source != null && name != null && !name.isSpecial) {
                        names[CallRange(source.startOffset, source.endOffset)] = name.asString()
                    }
                }
                super.visitFunctionCall(functionCall)
            }
        })
        return names
    }
}
