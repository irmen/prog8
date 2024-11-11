package prog8.codegen.cpu6502

import prog8.code.ast.PtAsmSub
import prog8.code.core.Cx16VirtualRegisters
import prog8.code.core.RegisterOrPair


fun asmsub6502ArgsEvalOrder(sub: PtAsmSub): List<Int> {
    val order = mutableListOf<Int>()
    // order is:
    //  1) cx16 virtual word registers,
    //  2) paired CPU registers,
    //  3) single CPU registers (order Y,X,A),
    //  4) floating point registers (FAC1, FAC2),
    //  5) CPU Carry status flag
    val args = sub.parameters.withIndex()
    val (cx16regs, args2) = args.partition { it.value.first.registerOrPair in Cx16VirtualRegisters }
    val pairedRegisters = arrayOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)
    val (pairedRegs , args3) = args2.partition { it.value.first.registerOrPair in pairedRegisters }
    val (singleRegsMixed, rest) = args3.partition { it.value.first.registerOrPair != null }
    val (singleCpuRegs, floatRegs) = singleRegsMixed.partition {it.value.first.registerOrPair != RegisterOrPair.FAC1 && it.value.first.registerOrPair != RegisterOrPair.FAC2  }

    cx16regs.forEach { order += it.index }
    pairedRegs.forEach { order += it.index }
    singleCpuRegs.sortedBy { it.value.first.registerOrPair!!.asCpuRegister() }.asReversed().forEach { order += it.index }
    require(rest.all { it.value.first.registerOrPair==null && it.value.first.statusflag!=null})
    floatRegs.forEach { order += it.index }
    rest.forEach { order += it.index }
    require(order.size==sub.parameters.size)

    return order
}
