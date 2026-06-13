package prog8.codegen.cpu6502

import prog8.code.ast.PtAsmSub
import prog8.code.ast.PtSubroutineParameter
import prog8.code.core.CombinedLongRegisters
import prog8.code.core.Cx16VirtualRegisters
import prog8.code.core.RegisterOrPair
import prog8.code.core.RegisterOrStatusflag


fun asmsub6502ArgsEvalOrder(sub: PtAsmSub, argComplexity: List<Boolean>): List<Int> {
    val order = mutableListOf<Int>()
    // order is:
    //  1) combined cx16 virtual registers (longs),
    //  2) cx16 virtual word registers,
    //  3) paired CPU registers,
    //  4) single CPU registers (order Y,X,A),
    //  5) floating point registers (FAC1, FAC2),
    //  6) CPU Carry status flag
    // Within each group, complex expressions are evaluated first (they may clobber registers,
    // so running them before simple expressions avoids needing to save/restore already-loaded values).
    val args = sub.parameters.withIndex()
    val (combinedLongRegs, args1) = args.partition { it.value.first.registerOrPair in CombinedLongRegisters }
    val (cx16regs, args2) = args1.partition { it.value.first.registerOrPair in Cx16VirtualRegisters }
    val pairedRegisters = arrayOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)
    val (pairedRegs , args3) = args2.partition { it.value.first.registerOrPair in pairedRegisters }
    val (singleRegsMixed, rest) = args3.partition { it.value.first.registerOrPair != null }
    val (singleCpuRegs, floatRegs) = singleRegsMixed.partition {it.value.first.registerOrPair != RegisterOrPair.FAC1 && it.value.first.registerOrPair != RegisterOrPair.FAC2  }

    combinedLongRegs.sortedByDescending { argComplexity[it.index] }.forEach { order += it.index }
    cx16regs.sortedByDescending { argComplexity[it.index] }.forEach { order += it.index }
    pairedRegs.sortedByDescending { argComplexity[it.index] }.forEach { order += it.index }
    singleCpuRegs
        .sortedWith(
            compareByDescending<IndexedValue<Pair<RegisterOrStatusflag, PtSubroutineParameter>>> { argComplexity[it.index] }
                .thenByDescending { it.value.first.registerOrPair!!.asCpuRegister() }
        )
        .forEach { order += it.index }
    require(rest.all { it.value.first.registerOrPair==null && it.value.first.statusflag!=null})
    floatRegs.sortedByDescending { argComplexity[it.index] }.forEach { order += it.index }
    rest.forEach { order += it.index }
    require(order.size==sub.parameters.size)

    return order
}
