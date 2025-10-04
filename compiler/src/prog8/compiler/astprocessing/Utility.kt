package prog8.compiler.astprocessing

import prog8.ast.FatalAstException
import prog8.code.ast.PtExpression
import prog8.code.ast.PtFunctionCall
import prog8.code.ast.PtTypeCast
import prog8.code.core.DataType


internal fun makePushPopFunctionCalls(value: PtExpression): Pair<PtFunctionCall, PtExpression> {
    var popTypecast: DataType? = null
    var pushTypecast: DataType? = null
    var pushWord = false
    var pushFloat = false
    var pushLong = false

    when {
        value.type.isBool -> {
            pushTypecast = DataType.UBYTE
            popTypecast = DataType.BOOL
        }
        value.type.isSignedByte -> {
            pushTypecast = DataType.UBYTE
            popTypecast = DataType.BYTE
        }
        value.type.isSignedWord -> {
            pushWord = true
            pushTypecast = DataType.UWORD
            popTypecast = DataType.WORD
        }
        value.type.isUnsignedByte -> {}
        value.type.isUnsignedWord -> pushWord = true
        value.type.isLong -> pushLong = true
        value.type.isPassByRef -> pushWord = true
        value.type.isFloat -> pushFloat = true
        else -> throw FatalAstException("unsupported return value type ${value.type} with defer")
    }

    val pushFunc = if(pushFloat) "floats.push" else if(pushWord) "sys.pushw" else if (pushLong) "sys.pushl" else "sys.push"
    val popFunc = if(pushFloat) "floats.pop" else if(pushWord) "sys.popw" else if(pushLong) "sys.popl" else "sys.pop"
    val pushCall = PtFunctionCall(pushFunc, true, DataType.UNDEFINED, value.position)
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
            val returnDt = if(pushWord) DataType.UWORD else if(pushLong) DataType.LONG else if(pushFloat) DataType.FLOAT else DataType.UBYTE
            it.add(PtFunctionCall(popFunc, false, returnDt, value.position))
        }
    } else
        PtFunctionCall(popFunc, false, value.type, value.position)

    return Pair(pushCall, popCall)
}

