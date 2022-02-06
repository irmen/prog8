package prog8.codegen.target

import prog8.ast.expressions.Expression
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.Subroutine
import prog8.codegen.target.c64.C64MachineDefinition
import prog8.codegen.target.cbm.CbmMemorySizer
import prog8.codegen.target.cbm.asmsub6502ArgsEvalOrder
import prog8.codegen.target.cbm.asmsub6502ArgsHaveRegisterClobberRisk
import prog8.compilerinterface.ICompilationTarget
import prog8.compilerinterface.IMemSizer
import prog8.compilerinterface.IStringEncoding


class C64Target: ICompilationTarget, IStringEncoding by Encoder, IMemSizer by CbmMemorySizer {
    override val name = NAME
    override val machine = C64MachineDefinition()

    companion object {
        const val NAME = "c64"
    }

    override fun asmsubArgsEvalOrder(sub: Subroutine): List<Int> =
        asmsub6502ArgsEvalOrder(sub)
    override fun asmsubArgsHaveRegisterClobberRisk(args: List<Expression>, paramRegisters: List<RegisterOrStatusflag>) =
        asmsub6502ArgsHaveRegisterClobberRisk(args, paramRegisters)
}
