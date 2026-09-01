package prog8.codegen.intermediate

import prog8.code.ast.*

internal data class StructArrayIndexInfo(
    val arrayName: String,
    val idxExpr: PtExpression,
    val structSize: Int,
    val fieldOffset: Int
)

internal fun extractStructArrayIndexInfo(
    addressExpr: PtExpression,
    codeGen: IRCodeGen
): StructArrayIndexInfo? {
    fun unwrapCast(expr: PtExpression): PtExpression {
        var cur = expr
        while(cur is PtTypeCast) cur = cur.value
        return cur
    }
    fun asAddressOf(expr: PtExpression): PtAddressOf? {
        return unwrapCast(expr) as? PtAddressOf
    }
    val baseAddrOf: PtAddressOf
    val fieldOffset: Int
    val unwrapped = unwrapCast(addressExpr)
    when {
        unwrapped is PtAddressOf -> {
            baseAddrOf = unwrapped
            fieldOffset = 0
        }
        unwrapped is PtBinaryExpression && unwrapped.operator == "+" -> {
            val leftAddr = asAddressOf(unwrapped.left) ?: return null
            val rightNum = (unwrapCast(unwrapped.right) as? PtNumber)?.number?.toInt() ?: return null
            baseAddrOf = leftAddr
            fieldOffset = rightNum
        }
        else -> return null
    }
    if(!baseAddrOf.isFromArrayElement) return null
    val arrayIdent = baseAddrOf.identifier ?: return null
    val idxExpr = baseAddrOf.arrayIndexExpr ?: return null
    if(idxExpr is PtNumber) return null
    val arrayType = arrayIdent.type
    if(!arrayType.isArray) return null
    val elemType = arrayType.elementType()
    if(!elemType.isStructInstance) return null
    val structSize = codeGen.program.memsizer.memorySize(elemType, null)
    if(structSize <= 0 || structSize > 65535) return null
    if(fieldOffset < 0 || fieldOffset > 65535) return null
    return StructArrayIndexInfo(arrayIdent.name, idxExpr, structSize, fieldOffset)
}
