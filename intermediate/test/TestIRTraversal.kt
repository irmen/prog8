import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.CompilationOptions
import prog8.code.core.OutputType
import prog8.code.core.Position
import prog8.code.core.ZeropageType
import prog8.code.target.VMTarget
import prog8.intermediate.*

class TestIRTraversal: FunSpec({

    fun makeProgram(sub: IRSubroutine): IRProgram {
        val target = VMTarget()
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.DONTUSE)
            .noSysInit(true)
            .compilerVersion("99.99")
            .build()
        val prog = IRProgram("test-traversal", IRSymbolTable(), options, target)
        val block = IRBlock("p8b_main", false, IRBlock.Options(), Position.DUMMY)
        block += sub
        prog.addBlock(block)
        return prog
    }

    fun makeNestedLoopSub(): IRSubroutine {
        val sub = IRSubroutine("p8b_main.p8s_start", emptyList(), emptyList(), Position.DUMMY)
        val head = IRCodeChunk("p8b_main.p8s_start", null)
        head += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=1, immediate=10)
        sub += head

        // inner-most body uses r99 exclusively inside nested loop
        val innerBody = IRCodeChunk(null, null)
        innerBody += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=99, immediate=42)
        innerBody += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=99, labelSymbol="sym")
        val innerLoop = IRLoopChunk("p8_label_inner", 2, mutableListOf(innerBody))

        val outerBody1 = IRCodeChunk(null, null)
        outerBody1 += IRInstruction(Opcode.NOP)
        val outerBody2 = IRCodeChunk(null, null)
        outerBody2 += IRInstruction(Opcode.NOP)
        val outerLoop = IRLoopChunk("p8_label_outer", 3, mutableListOf(outerBody1, innerLoop, outerBody2))
        sub += outerLoop

        val tail = IRCodeChunk(null, null)
        tail += IRInstruction(Opcode.RETURN)
        sub += tail
        return sub
    }

    test("forEachChunk visits nested loop bodies in deterministic order") {
        val sub = makeNestedLoopSub()
        val visited = mutableListOf<IRCodeChunkBase>()
        sub.forEachChunk { visited += it }
        // head, outer loop, outerBody1, inner loop, innerBody, outerBody2, tail
        visited.size shouldBe 7
        visited[0].label shouldBe "p8b_main.p8s_start"
        (visited[1] as IRLoopChunk).label shouldBe "p8_label_outer"
        (visited[3] as IRLoopChunk).label shouldBe "p8_label_inner"
        visited[4].instructions.size shouldBe 2
    }

    test("forEachInstruction finds register used only inside nested loop") {
        val sub = makeNestedLoopSub()
        val regs = mutableSetOf<Int?>()
        sub.forEachInstruction { regs += it.reg1 }
        (99 in regs) shouldBe true
    }

    test("program forEachInstruction and registersUsed find loop-body register") {
        val prog = makeProgram(makeNestedLoopSub())
        val regs = mutableSetOf<Int?>()
        prog.forEachInstruction { regs += it.reg1 }
        (99 in regs) shouldBe true

        val used = prog.registersUsed()
        (RegisterNum(99) in used.readRegs || RegisterNum(99) in used.writeRegs) shouldBe true
    }

    test("program forEachInstruction visits top-level chunks and nested loops") {
        val prog = makeProgram(makeNestedLoopSub())
        val block = prog.blocks.first()
        val topLevel = IRCodeChunk(null, null)
        topLevel += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=77, immediate=1)
        block += topLevel
        val loopBody = IRCodeChunk(null, null)
        loopBody += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=88, immediate=2)
        block += IRLoopChunk("p8_label_top_level", 2, mutableListOf(loopBody))

        val regs = mutableSetOf<Int?>()
        prog.forEachInstruction { regs += it.reg1 }

        (77 in regs) shouldBe true
        (88 in regs) shouldBe true
    }

    test("chunk recursive helper visits self then body") {
        val body = IRCodeChunk(null, null)
        body += IRInstruction(Opcode.NOP)
        val loop = IRLoopChunk("p8_label_gen_1", 5, mutableListOf(body))
        val visited = mutableListOf<IRCodeChunkBase>()
        loop.forEachChunkRecursive { visited += it }
        visited.size shouldBe 2
        (visited[0] is IRLoopChunk) shouldBe true
        (visited[1] is IRCodeChunk) shouldBe true
    }
})
