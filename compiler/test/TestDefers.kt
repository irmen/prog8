package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import prog8.code.ast.*
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8tests.helpers.compileText

class TestDefers : FunSpec({
    val outputDir = tempdir().toPath()

    test("defer syntactic sugaring") {
        val src = """
main {
    sub start() {
        void test()
    }

    sub test() -> uword {
        defer {
            cx16.r0++
            cx16.r1++
        }
        
        if cx16.r0==0 {
            defer cx16.r1++
        }

        if cx16.r0==0
            return cx16.r0+cx16.r1
        defer cx16.r2++
        return 999
    }
}"""
        val result = compileText(Cx16Target(), optimize = true, src, outputDir, writeAssembly = true)!!
        val main = result.codegenAst!!.allBlocks().single { it.name == "p8b_main" }
        val sub = main.children[1] as PtSub
        sub.scopedName shouldBe "p8b_main.p8s_test"

        // check the desugaring of the defer statements
        sub.children[0] shouldBe instanceOf<PtSubSignature>()
        (sub.children[1] as PtVariable).name shouldBe "p8v_prog8_defers_mask"

        val firstDefer = sub.children[3] as PtAugmentedAssign
        firstDefer.operator shouldBe "|="
        firstDefer.target.identifier?.name shouldBe "p8b_main.p8s_test.p8v_prog8_defers_mask"
        firstDefer.value.asConstInteger() shouldBe 4

        val firstIf = sub.children[4] as PtIfElse
        val deferInIf = firstIf.ifScope.children[0] as PtAugmentedAssign
        deferInIf.operator shouldBe "|="
        deferInIf.target.identifier?.name shouldBe "p8b_main.p8s_test.p8v_prog8_defers_mask"
        deferInIf.value.asConstInteger() shouldBe 2

        val lastDefer = sub.children[6] as PtAugmentedAssign
        lastDefer.operator shouldBe "|="
        lastDefer.target.identifier?.name shouldBe "p8b_main.p8s_test.p8v_prog8_defers_mask"
        lastDefer.value.asConstInteger() shouldBe 1

        val ifelse = sub.children[5] as PtIfElse
        val ifscope = ifelse.ifScope.children[0] as PtNodeGroup
        val ifscope_push = ifscope.children[0] as PtFunctionCall
        val ifscope_defer = ifscope.children[1] as PtFunctionCall
        val ifscope_return = ifscope.children[2] as PtReturn
        ifscope_defer.name shouldBe "p8b_main.p8s_test.p8s_prog8_invoke_defers"
        ifscope_push.name shouldBe "pushw"
        (ifscope_return.children.single() as PtFunctionCall).name shouldBe "popw"

        val ending = sub.children[7] as PtFunctionCall
        ending.name shouldBe "p8b_main.p8s_test.p8s_prog8_invoke_defers"
        sub.children[8] shouldBe instanceOf<PtReturn>()
        val handler = sub.children[9] as PtSub
        handler.name shouldBe "p8s_prog8_invoke_defers"
    }

    test("defer blocks prevent tail call optimization") {
        val src = """
main {
    sub start() {
        void test()
    }
    
    sub test() {
        defer {
            cx16.r0 = 1
        }
        return other()
    }
    
    sub other() {
        cx16.r1 = 2
    }
}"""
        // We compile for Cx16Target because tail call optimization (TCO) is a 6502 backend optimization
        // (actually it's in StatementReorderer which runs before codegen, but it targets JMP vs JSR)
        val result = compileText(Cx16Target(), optimize = true, src, outputDir, writeAssembly = true)!!
        val main = result.codegenAst!!.allBlocks().single { it.name == "p8b_main" }
        val testSub = main.children.filterIsInstance<PtSub>().find { it.scopedName == "p8b_main.p8s_test" }!!
        
        // If TCO happened, we would see a PtJump to other instead of a PtFunctionCall to other.
        // But with defer, it should remain a PtFunctionCall (or rather, it's inside a node group that calls invoke_defers)
        
        // Let's inspect the children of testSub.
        // It should have: signature, mask var, initialize mask, set mask bit, the call to other, call to invoke_defers, return.
        
        fun collectCalls(node: PtNode): List<PtFunctionCall> {
            val calls = mutableListOf<PtFunctionCall>()
            if (node is PtFunctionCall) calls.add(node)
            node.children.forEach { calls.addAll(collectCalls(it)) }
            return calls
        }
        
        val callToOther = collectCalls(testSub).find { it.name == "p8b_main.p8s_other" }
        callToOther shouldNotBe null
        
        // Verify it is NOT a PtJump (which would be used for tail call optimization)
        // Actually, TCO in StatementReorderer converts return other() into a Goto.
        // In PtSub, a Goto becomes a PtJump.
        
        testSub.children.any { it is PtJump && (it.target as? PtIdentifier)?.name == "p8b_main.p8s_other" } shouldBe false
    }
})
