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
    //  4) CPU Carry status flag
    val args = sub.parameters.withIndex()
    val (cx16regs, args2) = args.partition { it.value.first.registerOrPair in Cx16VirtualRegisters }
    val pairedRegisters = arrayOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)
    val (pairedRegs , args3) = args2.partition { it.value.first.registerOrPair in pairedRegisters }
    val (singleRegs, rest) = args3.partition { it.value.first.registerOrPair != null }

    cx16regs.forEach { order += it.index }
    pairedRegs.forEach { order += it.index }
    singleRegs.sortedBy { it.value.first.registerOrPair!!.asCpuRegister() }.asReversed().forEach { order += it.index }
    require(rest.all { it.value.first.registerOrPair==null && it.value.first.statusflag!=null})
    rest.forEach { order += it.index }
    require(order.size==sub.parameters.size)
    return order
}
