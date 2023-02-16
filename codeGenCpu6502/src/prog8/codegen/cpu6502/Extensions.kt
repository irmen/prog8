package prog8.codegen.cpu6502

import prog8.code.ast.IPtSubroutine
import prog8.code.ast.PtAsmSub
import prog8.code.ast.PtSub
import prog8.code.core.*


internal fun IPtSubroutine.regXasResult(): Boolean =
    (this is PtAsmSub) && this.returns.any { it.first.registerOrPair in arrayOf(RegisterOrPair.X, RegisterOrPair.AX, RegisterOrPair.XY) }

internal fun IPtSubroutine.shouldSaveX(): Boolean =
    this.regXasResult() || (this is PtAsmSub && (CpuRegister.X in this.clobbers || regXasParam()))

internal fun PtAsmSub.regXasParam(): Boolean =
    parameters.any { it.first.registerOrPair in arrayOf(RegisterOrPair.X, RegisterOrPair.AX, RegisterOrPair.XY) }

internal class KeepAresult(val saveOnEntry: Boolean, val saveOnReturn: Boolean)

internal fun PtAsmSub.shouldKeepA(): KeepAresult {
    // determine if A's value should be kept when preparing for calling the subroutine, and when returning from it

    // it seems that we never have to save A when calling? will be loaded correctly after setup.
    // but on return it depends on wether the routine returns something in A.
    val saveAonReturn = returns.any { it.first.registerOrPair==RegisterOrPair.A || it.first.registerOrPair==RegisterOrPair.AY || it.first.registerOrPair==RegisterOrPair.AX }
    return KeepAresult(false, saveAonReturn)
}

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
                    in ByteDatatypes -> RegisterOrStatusflag(RegisterOrPair.A, null)
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
