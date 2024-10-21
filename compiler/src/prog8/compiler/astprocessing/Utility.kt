package prog8.compiler.astprocessing

import prog8.ast.base.FatalAstException
import prog8.code.ast.PtExpression
import prog8.code.ast.PtFunctionCall
import prog8.code.ast.PtTypeCast
import prog8.code.core.DataType
import prog8.code.core.PassByReferenceDatatypes


internal fun makePushPopFunctionCalls(value: PtExpression): Pair<PtFunctionCall, PtExpression> {
    var popTypecast: DataType? = null
    var pushTypecast: DataType? = null
    var pushWord = false
    var pushFloat = false

    when(value.type) {
        DataType.BOOL -> {
            pushTypecast = DataType.UBYTE
            popTypecast = DataType.BOOL
        }
        DataType.BYTE -> {
            pushTypecast = DataType.UBYTE
            popTypecast = DataType.BYTE
        }
        DataType.WORD -> {
            pushWord = true
            pushTypecast = DataType.UWORD
            popTypecast = DataType.WORD
        }
        DataType.UBYTE -> {}
        DataType.UWORD, in PassByReferenceDatatypes -> pushWord = true
        DataType.FLOAT -> pushFloat = true
        else -> throw FatalAstException("unsupported return value type ${value.type} with defer")
    }

    val pushFunc = if(pushFloat) "floats.push" else if(pushWord) "sys.pushw" else "sys.push"
    val popFunc = if(pushFloat) "floats.pop" else if(pushWord) "sys.popw" else "sys.pop"
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
            it.add(PtFunctionCall(popFunc, false, if(pushWord) DataType.UWORD else DataType.UBYTE, value.position))
        }
    } else
        PtFunctionCall(popFunc, false, value.type, value.position)

    return Pair(pushCall, popCall)
}

