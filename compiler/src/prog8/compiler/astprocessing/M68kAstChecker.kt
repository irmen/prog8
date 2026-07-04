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
import prog8.code.core.SplitWish
import prog8.code.core.ZeropageWish


internal class M68kAstChecker(private val errors: IErrorReporter) : IAstVisitor {

    override fun visit(program: Program) {
        for (module in program.modules) {
            module.accept(this)
        }
    }

    override fun visit(module: Module) {
        if (module.isLibrary)
            return
        for (statement in module.statements) {
            statement.accept(this)
        }
    }

    override fun visit(directive: Directive) {
        when (directive.directive) {
            "%zeropage", "%zpreserved", "%zpallowed" -> {
                errors.err("the ${directive.directive} directive is not available on the m68k target (no zero page)", directive.position)
            }
            "%launcher" -> {
                errors.err("the %launcher directive is not available on the m68k target", directive.position)
            }
            "%align" -> {
                errors.err("the %align directive is not available on the m68k target", directive.position)
            }
            "%address" -> {
                errors.err("the %address directive is not available on the m68k target", directive.position)
            }
            "%memtop" -> {
                errors.err("the %memtop directive is not available on the m68k target", directive.position)
            }
            "%output" -> {
                val arg = directive.args.singleOrNull()?.string?.uppercase()
                if (arg != null && arg != "RAW") {
                    errors.err("output types other than 'raw' are not available on the m68k target", directive.position)
                }
            }
            "%option" -> {
                for (arg in directive.args) {
                    when (arg.string) {
                        "verafxmuls" -> errors.err("%option verafxmuls is not available on the m68k target", arg.position)
                        "enable_floats" -> errors.err("%option enable_floats is not available on the m68k target (floats are always enabled)", arg.position)
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
                errors.err("@zp, @requirezp, and @nozp are not available on the m68k target (no zero page)", decl.position)
            }
            ZeropageWish.DONTCARE -> {}
        }
        if (decl.alignment == 256u) {
            errors.err("@alignpage is not available on the m68k target", decl.position)
        }
        if (decl.splitwordarray == SplitWish.NOSPLIT) {
            errors.err("@nosplit is not available on the m68k target (there are no split word arrays)", decl.position)
        }
        decl.value?.accept(this)
        decl.arraysize?.accept(this)
    }

    override fun visit(subroutine: Subroutine) {
        if (subroutine.isAsmSubroutine) {
            if (subroutine.asmAddress != null) {
                errors.err("extsub is not available on the m68k target", subroutine.position)
            } else {
                errors.err("asmsub is not available YET on the m68k target", subroutine.position)
            }
            return
        }
        for (param in subroutine.parameters) {
            if (param.registerOrPair != null) {
                errors.err("subroutine parameter register annotations (@${param.registerOrPair}) are not available on the m68k target", param.position)
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
