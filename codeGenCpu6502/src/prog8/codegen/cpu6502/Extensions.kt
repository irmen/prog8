package prog8.codegen.cpu6502

import prog8.code.ast.IPtSubroutine
import prog8.code.ast.PtAsmSub
import prog8.code.ast.PtSub
import prog8.code.core.DataType
import prog8.code.core.RegisterOrPair
import prog8.code.core.RegisterOrStatusflag


internal fun IPtSubroutine.returnsWhatWhere(): List<Pair<RegisterOrStatusflag, DataType>> {
    when(this) {
        is PtAsmSub -> {
            return returns
        }
        is PtSub -> {
            // for non-asm subroutines, determine the return registers based on the type of the return value
            return if(returntype==null)
                emptyList()
            else {
                val register = when {
                    returntype!!.isByteOrBool -> RegisterOrStatusflag(RegisterOrPair.A, null)
                    returntype!!.isWord -> RegisterOrStatusflag(RegisterOrPair.AY, null)
                    returntype!!.isFloat -> RegisterOrStatusflag(RegisterOrPair.FAC1, null)
                    else -> RegisterOrStatusflag(RegisterOrPair.AY, null)
                }
                listOf(Pair(register, returntype!!))
            }
        }
    }
}


internal fun PtSub.returnRegister(): RegisterOrStatusflag? {
    return when {
        returntype?.isByteOrBool==true -> RegisterOrStatusflag(RegisterOrPair.A, null)
        returntype?.isWord==true -> RegisterOrStatusflag(RegisterOrPair.AY, null)
        returntype?.isFloat==true -> RegisterOrStatusflag(RegisterOrPair.FAC1, null)
        returntype==null -> null
        else -> RegisterOrStatusflag(RegisterOrPair.AY, null)
    }
}
