package prog8.compiler.astprocessing

import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.statements.Subroutine
import prog8.ast.walk.IAstVisitor
import prog8.code.core.ICompilationTarget
import prog8.code.core.IErrorReporter
import prog8.code.core.Position
import prog8.code.core.RegisterOrPair
import prog8.code.target.Qemu68kTarget
import prog8.code.target.VMTarget


internal class AsmSubRegisterChecker(private val errors: IErrorReporter, private val target: ICompilationTarget) : IAstVisitor {

    private val m68kRegisters = setOf(
        RegisterOrPair.D0, RegisterOrPair.D1, RegisterOrPair.D2, RegisterOrPair.D3,
        RegisterOrPair.D4, RegisterOrPair.D5, RegisterOrPair.D6, RegisterOrPair.D7,
        RegisterOrPair.A0, RegisterOrPair.A1, RegisterOrPair.A2, RegisterOrPair.A3,
        RegisterOrPair.A4, RegisterOrPair.A5, RegisterOrPair.A6,
        RegisterOrPair.FP0, RegisterOrPair.FP1, RegisterOrPair.FP2, RegisterOrPair.FP3,
        RegisterOrPair.FP4, RegisterOrPair.FP5, RegisterOrPair.FP6, RegisterOrPair.FP7
    )

    override fun visit(program: Program) {
        for (module in program.modules) {
            module.accept(this)
        }
    }

    override fun visit(module: Module) {
        for (statement in module.statements) {
            statement.accept(this)
        }
    }

    override fun visit(subroutine: Subroutine) {
        if (!subroutine.isAsmSubroutine)
            return
        for (reg in subroutine.asmParameterRegisters) {
            if (reg.statusflag != null) continue
            val r = reg.registerOrPair ?: continue
            validateAsmSubRegister(r, subroutine.position)
        }
        for (reg in subroutine.asmReturnvaluesRegisters) {
            if (reg.statusflag != null) continue
            val r = reg.registerOrPair ?: continue
            validateAsmSubRegister(r, subroutine.position)
        }
    }

    private fun validateAsmSubRegister(register: RegisterOrPair, pos: Position) {
        val isM68kTarget = target.name == Qemu68kTarget.NAME
        val isM68kReg = register in m68kRegisters

        if (!isM68kTarget && isM68kReg && target.name != VMTarget.NAME) {
            errors.err("register $register is not available on the ${target.name} target", pos)
        }
    }
}
