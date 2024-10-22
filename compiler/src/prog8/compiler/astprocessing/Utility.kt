package prog8.compiler.astprocessing

import prog8.ast.base.FatalAstException
import prog8.code.ast.PtExpression
import prog8.code.ast.PtFunctionCall
import prog8.code.ast.PtTypeCast
import prog8.code.core.BaseDataType
import prog8.code.core.DataType


internal fun makePushPopFunctionCalls(value: PtExpression): Pair<PtFunctionCall, PtExpression> {
    var popTypecast: BaseDataType? = null
    var pushTypecast: BaseDataType? = null
    var pushWord = false
    var pushFloat = false

    when {
        value.type.isBool -> {
            pushTypecast = BaseDataType.UBYTE
            popTypecast = BaseDataType.BOOL
        }
        value.type.isSignedByte -> {
            pushTypecast = BaseDataType.UBYTE
            popTypecast = BaseDataType.BYTE
        }
        value.type.isSignedWord -> {
            pushWord = true
            pushTypecast = BaseDataType.UWORD
            popTypecast = BaseDataType.WORD
        }
        value.type.isUnsignedByte -> {}
        value.type.isUnsignedWord -> pushWord = true
        value.type.isPassByRef -> pushWord = true
        value.type.isFloat -> pushFloat = true
        else -> throw FatalAstException("unsupported return value type ${value.type} with defer")
    }

    val pushFunc = if(pushFloat) "floats.push" else if(pushWord) "sys.pushw" else "sys.push"
    val popFunc = if(pushFloat) "floats.pop" else if(pushWord) "sys.popw" else "sys.pop"
    val pushCall = PtFunctionCall(pushFunc, true, DataType.forDt(BaseDataType.UNDEFINED), value.position)
    if(pushTypecast!=null) {
        val typecast = PtTypeCast(pushTypecast, value.position).also {
            it.add(value)
        }
        pushCall.add(typecast)
    } else {
        pushCall.add(value)
    }
    val popCall = if(popTypecast!=null) {
        PtTypeCast(popTypecast, value.position).also {
            val returnDt = if(pushWord) DataType.forDt(BaseDataType.UWORD) else DataType.forDt(BaseDataType.UBYTE)
            it.add(PtFunctionCall(popFunc, false, returnDt, value.position))
        }
    } else
        PtFunctionCall(popFunc, false, value.type, value.position)

    return Pair(pushCall, popCall)
}

