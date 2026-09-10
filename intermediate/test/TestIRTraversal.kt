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
        head += IRInstructions.load(IRDataType.BYTE, 1, 10)
        sub += head

        // inner-most body uses r99 exclusively inside nested loop
        val innerBody = IRCodeChunk(null, null)
        innerBody += IRInstructions.load(IRDataType.BYTE, 99, 42)
        innerBody += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, 99, IRMemory.direct("sym"))
        val innerLoop = IRLoopChunk("p8_label_inner", 2, mutableListOf(innerBody))

        val outerBody1 = IRCodeChunk(null, null)
        outerBody1 += IRInstructions.simple(Opcode.NOP)
        val outerBody2 = IRCodeChunk(null, null)
        outerBody2 += IRInstructions.simple(Opcode.NOP)
        val outerLoop = IRLoopChunk("p8_label_outer", 3, mutableListOf(outerBody1, innerLoop, outerBody2))
        sub += outerLoop

        val tail = IRCodeChunk(null, null)
        tail += IRInstructions.returnVoid()
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
        val regs = mutableSetOf<VirtualRegister>()
        sub.forEachInstruction { regs += it.registerAccesses.map { access -> access.register } }
        (VirtualRegister.int(99) in regs) shouldBe true
    }

    test("program forEachInstruction and registersUsed find loop-body register") {
        val prog = makeProgram(makeNestedLoopSub())
        val regs = mutableSetOf<VirtualRegister>()
        prog.forEachInstruction { regs += it.registerAccesses.map { access -> access.register } }
        (VirtualRegister.int(99) in regs) shouldBe true

        val used = prog.registersUsed()
        (VirtualRegister.int(99) in used.readRegs || VirtualRegister.int(99) in used.writeRegs) shouldBe true
    }

    test("program forEachInstruction visits top-level chunks and nested loops") {
        val prog = makeProgram(makeNestedLoopSub())
        val block = prog.blocks.first()
        val topLevel = IRCodeChunk(null, null)
        topLevel += IRInstructions.load(IRDataType.BYTE, 77, 1)
        block += topLevel
        val loopBody = IRCodeChunk(null, null)
        loopBody += IRInstructions.load(IRDataType.BYTE, 88, 2)
        block += IRLoopChunk("p8_label_top_level", 2, mutableListOf(loopBody))

        val regs = mutableSetOf<VirtualRegister>()
        prog.forEachInstruction { regs += it.registerAccesses.map { access -> access.register } }

        (VirtualRegister.int(77) in regs) shouldBe true
        (VirtualRegister.int(88) in regs) shouldBe true
    }

    test("chunk recursive helper visits self then body") {
        val body = IRCodeChunk(null, null)
        body += IRInstructions.simple(Opcode.NOP)
        val loop = IRLoopChunk("p8_label_gen_1", 5, mutableListOf(body))
        val visited = mutableListOf<IRCodeChunkBase>()
        loop.forEachChunkRecursive { visited += it }
        visited.size shouldBe 2
        (visited[0] is IRLoopChunk) shouldBe true
        (visited[1] is IRCodeChunk) shouldBe true
    }

    test("register traversal keeps integer and float files separate") {
        val sub = IRSubroutine("main.start", emptyList(), emptyList(), Position.DUMMY)
        val chunk = IRCodeChunk("main.start", null)
        chunk += IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 5, 6)
        chunk += IRInstructions.binary(Opcode.FSIN, IRDataType.FLOAT, 5, 6)
        chunk += IRInstructions.returnVoid()
        sub += chunk
        val program = makeProgram(sub)

        val used = program.registersUsed()
        used.readRegs.keys shouldBe setOf(
            VirtualRegister.int(5), VirtualRegister.int(6), VirtualRegister.float(6)
        )
        used.writeRegs.keys shouldBe setOf(VirtualRegister.int(5), VirtualRegister.float(5))
    }

    test("program resolves structured code targets") {
        val sub = IRSubroutine("main.start", emptyList(), emptyList(), Position.DUMMY)
        val source = IRCodeChunk("main.start", null)
        source += IRInstructions.jump(codeLabel("main.target"))
        val target = IRCodeChunk("main.target", null)
        target += IRInstructions.returnVoid()
        sub += source
        sub += target
        val program = makeProgram(sub)

        program.linkChunks()

        program.resolveCodeTarget(CodeReference.Label("main.target")) shouldBe target
    }
})
