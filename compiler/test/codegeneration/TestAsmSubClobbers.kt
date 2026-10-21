package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.ast.*
import prog8.code.target.Cx16Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestAsmSubClobbers : FunSpec({

    val outputDir = tempdir().toPath()

    test("clobbers declaration presence is preserved in the codegen AST") {
        val src = """
%zeropage basicsafe
%option no_sysinit
main {
    sub start() {
        cx16.r0 = undeclared(1)
        cx16.r0 = declared_empty(1)
        cx16.r0 = declared_regs(1)
    }

    asmsub undeclared(uword x @R0) -> uword @R0 {
        %asm {{
            rts
        }}
    }

    asmsub declared_empty(uword x @R0) clobbers() -> uword @R0 {
        %asm {{
            rts
        }}
    }

    asmsub declared_regs(uword x @R0) clobbers(X) -> uword @R0 {
        %asm {{
            rts
        }}
    }
}"""
        val errors = ErrorReporterForTests()
        val result = compileText(Cx16Target(), false, src, outputDir, errors, writeAssembly = true, assemble = false)
        errors.errors shouldBe emptyList()
        result shouldNotBe null
        val codegenAst = result!!.codegenAst!!
        val asmsubs = mutableMapOf<String, PtAsmSub>()
        walkAst(codegenAst) { node, _ ->
            if (node is PtAsmSub)
                asmsubs[node.name.removePrefix("p8s_")] = node
            true
        }

        val undeclared = asmsubs.getValue("undeclared")
        undeclared.clobbersDeclared shouldBe false
        undeclared.clobbers shouldBe emptySet()

        val declaredEmpty = asmsubs.getValue("declared_empty")
        declaredEmpty.clobbersDeclared shouldBe true
        declaredEmpty.clobbers shouldBe emptySet()

        val declaredRegs = asmsubs.getValue("declared_regs")
        declaredRegs.clobbersDeclared shouldBe true
        declaredRegs.clobbers.map { it.name } shouldBe listOf("X")
    }
})
