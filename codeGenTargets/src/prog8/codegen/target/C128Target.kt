package prog8.codegen.target

import prog8.ast.expressions.Expression
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.Subroutine
import prog8.codegen.target.c128.C128MachineDefinition
import prog8.codegen.target.cbm.CbmMemorySizer
import prog8.codegen.target.cbm.asmsub6502ArgsEvalOrder
import prog8.codegen.target.cbm.asmsub6502ArgsHaveRegisterClobberRisk
import prog8.compilerinterface.Encoding
import prog8.compilerinterface.ICompilationTarget
import prog8.compilerinterface.IMemSizer
import prog8.compilerinterface.IStringEncoding


class C128Target: ICompilationTarget, IStringEncoding by Encoder, IMemSizer by CbmMemorySizer {
    override val name = NAME
    override val machine = C128MachineDefinition()
    override val supportedEncodings = setOf(Encoding.PETSCII, Encoding.SCREENCODES)
    override val defaultEncoding = Encoding.PETSCII

    companion object {
        const val NAME = "c128"
    }

    override fun asmsubArgsEvalOrder(sub: Subroutine): List<Int> =
        asmsub6502ArgsEvalOrder(sub)
    override fun asmsubArgsHaveRegisterClobberRisk(args: List<Expression>, paramRegisters: List<RegisterOrStatusflag>) =
        asmsub6502ArgsHaveRegisterClobberRisk(args, paramRegisters)
}
