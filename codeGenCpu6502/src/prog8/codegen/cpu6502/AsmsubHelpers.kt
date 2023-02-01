package prog8.codegen.cpu6502

import prog8.code.ast.*
import prog8.code.core.Cx16VirtualRegisters
import prog8.code.core.RegisterOrPair
import prog8.code.core.RegisterOrStatusflag


fun asmsub6502ArgsEvalOrder(sub: PtAsmSub): List<Int> {
    val order = mutableListOf<Int>()
    // order is:
    //  1) cx16 virtual word registers,
    //  2) paired CPU registers,
    //  3) single CPU registers (X last), except A,
    //  4) CPU Carry status flag
    //  5) the A register itself last   (so everything before it can use the accumulator without having to save its value)
    val args = sub.parameters.withIndex()
    val (cx16regs, args2) = args.partition { it.value.second.registerOrPair in Cx16VirtualRegisters }
    val pairedRegisters = arrayOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)
    val (pairedRegs , args3) = args2.partition { it.value.second.registerOrPair in pairedRegisters }
    val (regsWithoutA, args4) = args3.partition { it.value.second.registerOrPair != RegisterOrPair.A }
    val (regA, rest) = args4.partition { it.value.second.registerOrPair != null }

    cx16regs.forEach { order += it.index }
    pairedRegs.forEach { order += it.index }
    regsWithoutA.forEach {
        if(it.value.second.registerOrPair != RegisterOrPair.X)
            order += it.index
    }
    regsWithoutA.firstOrNull { it.value.second.registerOrPair==RegisterOrPair.X } ?.let { order += it.index}
    rest.forEach { order += it.index }
    regA.forEach { order += it.index }
    require(order.size==sub.parameters.size)
    return order
}

fun asmsub6502ArgsHaveRegisterClobberRisk(
    args: List<PtExpression>,
    params: List<Pair<PtSubroutineParameter, RegisterOrStatusflag>>
): Boolean {
    fun isClobberRisk(expr: PtExpression): Boolean {
        when (expr) {
            is PtArrayIndexer -> {
                return params.any {
                    it.second.registerOrPair in listOf(RegisterOrPair.Y, RegisterOrPair.AY, RegisterOrPair.XY)
                }
            }
            is PtBuiltinFunctionCall -> {
                if (expr.name == "lsb" || expr.name == "msb")
                    return isClobberRisk(expr.args[0])
                if (expr.name == "mkword")
                    return isClobberRisk(expr.args[0]) && isClobberRisk(expr.args[1])
                return !expr.isSimple()
            }
            else -> return !expr.isSimple()
        }
    }

    return args.size>1 && args.any { isClobberRisk(it) }
}
