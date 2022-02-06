package prog8.codegen.target

import prog8.ast.expressions.Expression
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.Subroutine
import prog8.codegen.target.cbm.CbmMemorySizer
import prog8.codegen.target.cbm.asmsub6502ArgsEvalOrder
import prog8.codegen.target.cbm.asmsub6502ArgsHaveRegisterClobberRisk
import prog8.codegen.target.cx16.CX16MachineDefinition
import prog8.compilerinterface.ICompilationTarget
import prog8.compilerinterface.IMemSizer
import prog8.compilerinterface.IStringEncoding


class Cx16Target: ICompilationTarget, IStringEncoding by Encoder, IMemSizer by CbmMemorySizer {
    override val name = NAME
    override val machine = CX16MachineDefinition()

    companion object {
        const val NAME = "cx16"
    }

    override fun asmsubArgsEvalOrder(sub: Subroutine): List<Int> =
        asmsub6502ArgsEvalOrder(sub)
    override fun asmsubArgsHaveRegisterClobberRisk(args: List<Expression>, paramRegisters: List<RegisterOrStatusflag>) =
        asmsub6502ArgsHaveRegisterClobberRisk(args, paramRegisters)
}
