package prog8.compiler.astprocessing

import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.expressions.AddressOf
import prog8.ast.statements.Block
import prog8.ast.statements.Directive
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.ast.walk.IAstVisitor
import prog8.code.core.IErrorReporter
import prog8.code.core.RegisterOrPair
import prog8.code.core.SplitWish
import prog8.code.core.ZeropageWish


internal class M68kAstChecker(private val errors: IErrorReporter) : IAstVisitor {

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

    override fun visit(directive: Directive) {
        when (directive.directive) {
            "%zeropage", "%zpreserved", "%zpallowed" -> {
                errors.info("the ${directive.directive} directive is not available on the m68k target (no zero page), ignoring", directive.position)
            }
            "%launcher" -> {
                errors.err("the %launcher directive is not available on the m68k target", directive.position)
            }
            "%address" -> {
                errors.err("the %address directive is not available on the m68k target", directive.position)
            }
            "%memtop" -> {
                errors.err("the %memtop directive is not available on the m68k target", directive.position)
            }
            "%output" -> {
                val arg = directive.args.singleOrNull()?.string?.uppercase()
                if (arg != null && arg != "RAW" && arg != "ELF") {
                    errors.err("output types other than 'raw' and 'elf' are not available on the m68k target", directive.position)
                }
            }
            "%option" -> {
                for (arg in directive.args) {
                    when (arg.string) {
                        "verafxmuls" -> errors.err("%option verafxmuls is not available on the m68k target", arg.position)
                    }
                }
            }
        }
    }

    override fun visit(decl: VarDecl) {
        when (decl.zeropage) {
            ZeropageWish.REQUIRE_ZEROPAGE,
            ZeropageWish.PREFER_ZEROPAGE,
            ZeropageWish.NOT_IN_ZEROPAGE -> {
                errors.info("@zp, @requirezp, and @nozp are redundant on the m68k target", decl.position)
            }
            ZeropageWish.DONTCARE -> {}
        }
        if (decl.splitwordarray == SplitWish.NOSPLIT) {
            errors.info("@nosplit is redundant here", decl.position)
        }
        if (decl.datatype.isSplitWordArray && decl.splitwordarray != SplitWish.DONTCARE) {
            errors.err("split word arrays are not supported on the m68k target", decl.position)
        }
        decl.value?.accept(this)
        decl.arraysize?.accept(this)
    }

    override fun visit(subroutine: Subroutine) {
        if (subroutine.isAsmSubroutine) {
            if (subroutine.asmAddress != null) {
                errors.err("extsub is not available on the m68k target", subroutine.position)
            }
            return
        }
        for (param in subroutine.parameters) {
            if (param.registerOrPair != null) {
                if (param.registerOrPair in m68kRegisters) {
                    errors.err("register annotations on normal subroutine parameters are not available on the m68k target (only used in asmsub)", param.position)
                }
            }
        }
        for (statement in subroutine.statements) {
            statement.accept(this)
        }
    }

    override fun visit(block: Block) {
        if (block.address != null) {
            errors.err("specific block addresses are not available on the m68k target", block.position)
        }
        for (statement in block.statements) {
            statement.accept(this)
        }
    }

    override fun visit(addressOf: AddressOf) {
        if (addressOf.msb) {
            errors.err("&> is not available on the m68k target (no split word arrays)", addressOf.position)
        }
    }

}
