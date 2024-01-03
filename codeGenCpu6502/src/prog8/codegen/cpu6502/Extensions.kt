package prog8.codegen.cpu6502

import prog8.code.ast.IPtSubroutine
import prog8.code.ast.PtAsmSub
import prog8.code.ast.PtSub
import prog8.code.core.*


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
                val register = when (returntype!!) {
                    in ByteDatatypesWithBoolean -> RegisterOrStatusflag(RegisterOrPair.A, null)
                    in WordDatatypes -> RegisterOrStatusflag(RegisterOrPair.AY, null)
                    DataType.FLOAT -> RegisterOrStatusflag(RegisterOrPair.FAC1, null)
                    else -> RegisterOrStatusflag(RegisterOrPair.AY, null)
                }
                listOf(Pair(register, returntype!!))
            }
        }
    }
}


internal fun PtSub.returnRegister(): RegisterOrStatusflag? {
    return when(returntype) {
        in ByteDatatypes -> RegisterOrStatusflag(RegisterOrPair.A, null)
        in WordDatatypes -> RegisterOrStatusflag(RegisterOrPair.AY, null)
        DataType.FLOAT -> RegisterOrStatusflag(RegisterOrPair.FAC1, null)
        null -> null
        else -> RegisterOrStatusflag(RegisterOrPair.AY, null)
    }
}
