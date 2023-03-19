package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.ast.PtAssignment
import prog8.code.ast.PtBuiltinFunctionCall
import prog8.code.ast.PtRpn
import prog8.code.core.DataType
import prog8.code.target.C64Target
import prog8tests.helpers.compileText

class TestRPNCodeGen: FunSpec({
    test("rpn 6502") {
        val text="""
main {
    sub start() {
        uword pointer = 4000
        uword a = 11
        uword b = 22
        uword c = 33
        cx16.r0 = peekw(pointer+a+b*c+42)
        pokew(pointer+a+b*c+42, 4242)
    }
}"""
        val result = compileText(C64Target(), false, text, writeAssembly = true, useRPN = true)!!
        val ast = result.codegenAst!!
        val statements = ast.entrypoint()!!.children
        statements.size shouldBe 11
        val peekw = (statements[8] as PtAssignment).value as PtBuiltinFunctionCall
        val pokew = (statements[9] as PtBuiltinFunctionCall)
        val rpn1 = peekw.args.first() as PtRpn
        val rpn2 = pokew.args.first() as PtRpn
        rpn1.children.size shouldBe 7
        val depth1 = rpn1.maxDepth()
        depth1.getValue(DataType.UBYTE) shouldBe 0
        depth1.getValue(DataType.UWORD) shouldBe 3
        depth1.getValue(DataType.FLOAT) shouldBe 0
        rpn2.children.size shouldBe 7
        val depth2 = rpn2.maxDepth()
        depth2.getValue(DataType.UBYTE) shouldBe 0
        depth2.getValue(DataType.UWORD) shouldBe 3
        depth2.getValue(DataType.FLOAT) shouldBe 0
    }

})