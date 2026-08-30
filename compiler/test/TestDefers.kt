package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.instanceOf
import prog8.code.ast.*
import prog8.code.target.Cx16Target
import prog8.code.target.Qemu68kTarget
import prog8.code.target.VMTarget
import prog8.intermediate.IRFileReader
import prog8.vm.VmRunner
import prog8.vm.VmVariableAllocator
import prog8tests.helpers.compileText
import kotlin.io.path.readText

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

        // check the desugaring of the defer statements (allow extra push nodes for program-wide stack)
        sub.children[0] shouldBe instanceOf<PtSubSignature>()
        sub.children.filterIsInstance<PtVariable>().single { it.name=="p8v_prog8_defers_mask" } shouldNotBe null

        val topDeferEnables = sub.children.filterIsInstance<PtAugmentedAssign>().filter { it.operator=="|=" && it.target.identifier?.name=="p8b_main.p8s_test.p8v_prog8_defers_mask" }
        topDeferEnables.size shouldBe 2
        topDeferEnables[0].value.asConstInteger() shouldBe 4
        topDeferEnables[1].value.asConstInteger() shouldBe 1

        // defer inside first if (conditional) - skip overflow check if present
        val firstIf = sub.children.filterIsInstance<PtIfElse>().first { it.ifScope.children.any { c -> c is PtAugmentedAssign && c.target.identifier?.name?.contains("prog8_defers_mask")==true } }
        val deferInIf = firstIf.ifScope.children.filterIsInstance<PtAugmentedAssign>().single()
        deferInIf.operator shouldBe "|="
        deferInIf.target.identifier?.name shouldBe "p8b_main.p8s_test.p8v_prog8_defers_mask"
        deferInIf.value.asConstInteger() shouldBe 2

        // second if with complex return
        val ifelse = sub.children.filterIsInstance<PtIfElse>().last()
        val ifscope = ifelse.ifScope.children[0] as PtNodeGroup
        // group contains pushw, decSp, handler call, return(popw)
        val pushInGroup = ifscope.children.filterIsInstance<PtFunctionCall>().single { it.name=="pushw" }
        pushInGroup shouldNotBe null
        val deferInGroup = ifscope.children.filterIsInstance<PtFunctionCall>().single { it.name=="p8b_main.p8s_test.p8s_prog8_invoke_defers" }
        deferInGroup shouldNotBe null
        val retInGroup = ifscope.children.filterIsInstance<PtReturn>().single()
        (retInGroup.children.single() as PtFunctionCall).name shouldBe "popw"

        // ending pop+handler+return before handler sub
        val endingCalls = sub.children.filterIsInstance<PtFunctionCall>().filter { it.name=="p8b_main.p8s_test.p8s_prog8_invoke_defers" }
        endingCalls.isNotEmpty() shouldBe true
        // should have a decSp before ending call (program-wide pop)
        val hasDecSpBeforeEnding = sub.children.filterIsInstance<PtAugmentedAssign>().any { it.operator=="-=" && it.target.identifier?.name?.contains("defer_sp")==true }
        hasDecSpBeforeEnding shouldBe true

        val handler = sub.children.filterIsInstance<PtSub>().single { it.name=="p8s_prog8_invoke_defers" }
        handler shouldNotBe null
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

    test("defer program-wide unwind via sys.exit") {
        val src = """
main {
    uword @shared result = 0

    sub start() {
        defer result = result * 10 + 3
        defer result = result * 10 + 4
        helper()
        ; should not reach here if program-wide unwind works via sys.exit
        result = 99
    }

    sub helper() {
        defer result = result * 10 + 1
        defer result = result * 10 + 2
        sys.exit(0)
    }
}"""
        val result = compileText(VMTarget(), optimize = true, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irSrc = virtfile.readText()
        // program-wide stack must be present
        irSrc shouldContain "prog8_defer"
        irSrc shouldContain "defer_unwind_all"
        val irProgram = IRFileReader().read(irSrc)
        irProgram.st.stripAllPrefixes()
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations
        VmRunner().runAndTestProgram(irSrc) { vm ->
            // helper defers 2,1 then start defers 4,3 -> ((0*10+2)*10+1)*10+4)*10+3 = 2143
            // if only local unwind, result would be 21 (only helper) or 99 (if start continued)
            vm.memory.getUW(allocations["main.result"]!!) shouldBe 2143u
        }
    }

    test("defer program-wide no overhead when unused") {
        val src = """
main {
    sub start() {
        cx16.r0++
    }
}"""
        val result = compileText(VMTarget(), optimize = true, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irSrc = virtfile.readText()
        irSrc shouldNotContain "prog8_defer"
        irSrc shouldNotContain "defer_unwind_all"
    }

    test("defer program-wide unwind via sys.exit on cx16") {
        val src = """
%zeropage basicsafe
%option no_sysinit
main {
    uword @shared result = 0
    sub start() {
        defer result = result * 10 + 3
        defer result = result * 10 + 4
        helper()
    }
    sub helper() {
        defer result = result * 10 + 1
        defer result = result * 10 + 2
        sys.exit(0)
    }
}"""
        val result = compileText(Cx16Target(), optimize = true, src, outputDir, writeAssembly = true)!!
        val prog = result.codegenAst!!
        prog.allBlocks().any { it.name.contains("prog8_defer") } shouldBe true
        prog.allBlocks().flatMap<PtBlock, PtNode> { it.children }.filterIsInstance<PtVariable>().any { it.name.contains("defer_sp") } shouldBe true
        prog.allBlocks().flatMap<PtBlock, PtNode> { it.children }.filterIsInstance<PtSub>().any { it.name.contains("defer_unwind_all") } shouldBe true
        val allSubs = prog.allBlocks().flatMap<PtBlock, PtNode> { it.children }.filterIsInstance<PtSub>()
        val subsNames = allSubs.map { it.name } + allSubs.flatMap { it.children }.filterIsInstance<PtSub>().map { it.name }
        subsNames.any { it.contains("prog8_invoke_defers") } shouldBe true
    }

    test("defer program-wide structure on qemu68k without vasm") {
        val src = """
main {
    uword @shared result = 0
    sub start() {
        defer result = result * 10 + 3
        defer result = result * 10 + 4
        helper()
    }
    sub helper() {
        defer result = result * 10 + 1
        defer result = result * 10 + 2
        sys.exit(0)
    }
}"""
        val result = compileText(Qemu68kTarget(), optimize = true, src, outputDir, writeAssembly = true, assemble = false)!!
        val prog = result.codegenAst!!
        prog.allBlocks().any { it.name.contains("prog8_defer") } shouldBe true
        prog.allBlocks().flatMap<PtBlock, PtNode> { it.children }.filterIsInstance<PtVariable>().any { it.name.contains("defer_sp") } shouldBe true
        val subs = prog.allBlocks().flatMap<PtBlock, PtNode> { it.children }.filterIsInstance<PtSub>().map { it.name }
        subs.any { it.contains("defer_unwind_all") } shouldBe true
        subs.any { it.contains("prog8_invoke_defers") } shouldBe true
    }
})
