package prog8.codegen.cpu6502

import prog8.code.ast.IPtSubroutine
import prog8.code.ast.PtAsmSub
import prog8.code.ast.PtSub
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.code.core.RegisterOrPair
import prog8.code.core.RegisterOrStatusflag


internal fun IPtSubroutine.returnsWhatWhere(): List<Pair<RegisterOrStatusflag, DataType>> {
    when(this) {
        is PtAsmSub -> {
            return returns
        }
        is PtSub -> {
            // for non-asm subroutines, determine the return registers based on the type of the return values

            when(returns.size) {
                0 -> return emptyList()
                1 -> {
                    val returntype = returns.single()
                    val register = when {
                        returntype.isByteOrBool -> RegisterOrStatusflag(RegisterOrPair.A, null)
                        returntype.isWord -> RegisterOrStatusflag(RegisterOrPair.AY, null)
                        returntype.isFloat -> RegisterOrStatusflag(RegisterOrPair.FAC1, null)
                        else -> RegisterOrStatusflag(RegisterOrPair.AY, null)
                    }
                    return listOf(Pair(register, returntype))
                }
                else -> {
                    // TODO for multi-value results, put the first one in register(s) and only the rest in the virtual registers?
                    throw AssemblyError("multi-value returns from a normal subroutine are not put into registers, this routine shouldn't have been called in this scenario")
                }
            }
        }
    }
}
