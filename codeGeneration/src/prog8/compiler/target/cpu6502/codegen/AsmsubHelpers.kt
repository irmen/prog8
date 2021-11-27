package prog8.compiler.target.cpu6502.codegen

import prog8.ast.base.Cx16VirtualRegisters
import prog8.ast.base.RegisterOrPair
import prog8.ast.expressions.*
import prog8.ast.statements.Subroutine


internal fun asmsub6502ArgsEvalOrder(sub: Subroutine): List<Int> {
    val order = mutableListOf<Int>()
    // order is:
    //  1) cx16 virtual word registers,
    //  2) paired CPU registers,
    //  3) single CPU registers (X last),
    //  4) CPU Carry status flag
    val args = sub.parameters.zip(sub.asmParameterRegisters).withIndex()
    val (cx16regs, args2) = args.partition { it.value.second.registerOrPair in Cx16VirtualRegisters }
    val pairedRegisters = arrayOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)
    val (pairedRegs , args3) = args2.partition { it.value.second.registerOrPair in pairedRegisters }
    val (regs, rest) = args3.partition { it.value.second.registerOrPair != null }

    cx16regs.forEach { order += it.index }
    pairedRegs.forEach { order += it.index }
    regs.forEach {
        if(it.value.second.registerOrPair != RegisterOrPair.X)
            order += it.index
    }
    regs.firstOrNull { it.value.second.registerOrPair==RegisterOrPair.X } ?.let { order += it.index}
    rest.forEach { order += it.index }
    require(order.size==sub.parameters.size)
    return order
}

internal fun asmsub6502ArgsHaveRegisterClobberRisk(args: List<Expression>): Boolean {
    fun isClobberRisk(expr: Expression): Boolean {
        if (expr.isSimple && expr !is PrefixExpression)
            return false

        if (expr is FunctionCall) {
            if (expr.target.nameInSource == listOf("lsb") || expr.target.nameInSource == listOf("msb"))
                return isClobberRisk(expr.args[0])
            if (expr.target.nameInSource == listOf("mkword"))
                return isClobberRisk(expr.args[0]) && isClobberRisk(expr.args[1])
        }

        return true
    }

    return args.size>1 && args.any { isClobberRisk(it) }
}
