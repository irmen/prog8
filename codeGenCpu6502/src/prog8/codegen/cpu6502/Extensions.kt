package prog8.codegen.cpu6502

import prog8.code.ast.IPtSubroutine
import prog8.code.ast.PtAsmSub
import prog8.code.ast.PtSub
import prog8.code.core.Cx16VirtualRegisters
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

            fun cpuRegisterFor(returntype: DataType): RegisterOrStatusflag = when {
                returntype.isByteOrBool -> RegisterOrStatusflag(RegisterOrPair.A, null)
                returntype.isWord -> RegisterOrStatusflag(RegisterOrPair.AY, null)
                returntype.isFloat -> RegisterOrStatusflag(RegisterOrPair.FAC1, null)
                else -> RegisterOrStatusflag(RegisterOrPair.AY, null)
            }

            when(returns.size) {
                0 -> return emptyList()
                1 -> {
                    val returntype = returns.single()
                    val register = cpuRegisterFor(returntype)
                    return listOf(Pair(register, returntype))
                }
                else -> {
                    val others = returns.zip(Cx16VirtualRegisters.reversed())
                        .map { (type, reg) -> RegisterOrStatusflag(reg, null) to type }
                    return others

                    // TODO for multi-value results, put the first one in A or AY cpu register(s) and the rest in the virtual registers starting from R15 and counting down
//                    val first = cpuRegisterFor(returns.first()) to returns.first()
//                    val others = returns.drop(1)
//                        .zip(Cx16VirtualRegisters.reversed())
//                        .map { (type, reg) -> RegisterOrStatusflag(reg, null) to type }
//                    return listOf(first) + others
                }
            }
        }
    }
}
