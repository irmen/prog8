package prog8tests.codegen.m68k

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import prog8.code.core.CompilationOptions
import prog8.code.core.OutputType
import prog8.code.core.Position
import prog8.code.core.ZeropageType
import prog8.code.target.Qemu68kTarget
import prog8.codegen.m68k.AsmGen
import prog8.intermediate.*
import prog8tests.helpers.DummyStringEncoder
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class TestInstructionSelectionOptimizations : FunSpec({

    val tempRoot = tempdir().toPath()

    fun generateAsmChunks(outputDir: Path, chunks: List<IRCodeChunkBase>): List<String> {
        val target = Qemu68kTarget()
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.FLOATSAFE)
            .floats(false)
            .compilerVersion("test")
            .memtopAddress(0xffffu)
            .build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        program.options.outputDir = outputDir
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.addAll(chunks)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val output = outputDir.toFile()
        output.deleteRecursively()
        output.mkdirs()
        AsmGen(program, target).generate()
        val asmFile = outputDir.resolve("test.asm")
        check(asmFile.exists()) { "Assembly file not written: $asmFile" }
        return asmFile.readText().lines().map { it.trim() }
    }

    fun generateAsm(outputDir: Path, instructions: List<IRInstruction>): List<String> {
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.addAll(instructions)
        return generateAsmChunks(outputDir, listOf(chunk))
    }

    test("uses quick address adjustments and preserves large offsets") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-offsets"),
            listOf(
                IRInstruction(Opcode.STOREZI, IRDataType.FLOAT, reg1 = 1, immediate = 1),
                IRInstruction(Opcode.STOREZI, IRDataType.FLOAT, reg1 = 1, immediate = 65535)
            )
        )

        lines.count { it == "addq.l  #1,a0" } shouldBe 1
        lines.count { it == "adda.l  #65535,a0" } shouldBe 1
    }

    test("annotates direct static field accesses") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-field-comments"),
            listOf(
                IRInstruction(Opcode.LOADM, IRDataType.WORD, reg1 = 1, labelSymbol = "p8b_ship.p8v_cash"),
                IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1 = 1, labelSymbol = "p8b_ship.p8v_cash")
            )
        )

        lines.any { it.endsWith("; p8b_ship.p8v_cash") } shouldBe true
        lines.count { it.endsWith("; p8b_ship.p8v_cash") } shouldBe 2
    }

    test("uses moveq for zero extension and only representable immediate returns") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-control"),
            listOf(
                IRInstruction(Opcode.EXT, IRDataType.BYTE, reg1 = 2, reg2 = 1),
                IRInstruction(Opcode.EXT, IRDataType.WORD, reg1 = 3, reg2 = 2),
                IRInstruction(Opcode.RETURNI, IRDataType.BYTE, immediate = 42),
                IRInstruction(Opcode.RETURNI, IRDataType.BYTE, immediate = 255)
            )
        )

        lines.count { it == "moveq  #0,d0" } shouldBe 2
        lines.any { it == "moveq  #42,d0" } shouldBe true
        lines.any { it == "move.b  #255,d0" } shouldBe true
    }

    test("compares a virtual register directly against memory") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-compare"),
            listOf(IRInstruction(Opcode.CMP, IRDataType.BYTE, reg1 = 1, reg2 = 2))
        )

        lines.any { it == "move.b  p8_regfile+0,d0" } shouldBe true
        lines.any { it == "cmp.b  p8_regfile+2,d0" } shouldBe true
        lines.any { it == "moveq  #0,d0" } shouldBe false
        lines.any { it == "moveq  #0,d1" } shouldBe false
    }

    test("byte modulus takes the remainder via swap without corrupting shifts") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-byte-mod"),
            listOf(
                IRInstruction(Opcode.MODR, IRDataType.BYTE, reg1 = 1, reg2 = 2),
                IRInstruction(Opcode.MODSR, IRDataType.BYTE, reg1 = 3, reg2 = 4)
            )
        )

        lines.count { it == "divu.w  d1,d0" } shouldBe 1
        lines.count { it == "divs.w  d1,d0" } shouldBe 1
        lines.count { it.startsWith("lsr.l") } shouldBe 0
        lines.count { it.startsWith("swap  d0") } shouldBe 2
    }

    test("word and long multiplication use memory sources instead of loading both operands") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-mul-memsrc"),
            listOf(
                IRInstruction(Opcode.MULR, IRDataType.WORD, reg1 = 1, reg2 = 2),
                IRInstruction(Opcode.MULR, IRDataType.LONG, reg1 = 3, reg2 = 4)
            )
        )

        lines.count { it == "mulu.w  p8_regfile+2,d0" } shouldBe 1
        lines.count { it == "mulu.l  p8_regfile+8,d0" } shouldBe 1
        lines.count { it.startsWith("move.w  p8_regfile+2,d1") } shouldBe 0
        lines.count { it.startsWith("move.l  p8_regfile+8,d1") } shouldBe 0
    }

    test("forwards an immediate load into a following hardware-register call argument") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec(
                    "",
                    null,
                    FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null)
                )
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 384),
                IRInstruction(Opcode.CALL, labelSymbol = "copper.move", fcallArgs = args)
            )
        )

        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe false
        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  p8_regfile+0,d0" } shouldBe false
    }

    test("forwards all immediate loads for a multi-argument call") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null)),
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(2), CallingConventionSlot(11), null))
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-multi"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 384),
                IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 2, immediate = 1220),
                IRInstruction(Opcode.CALL, labelSymbol = "copper.move", fcallArgs = args)
            )
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  #1220,d1" } shouldBe true
        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe false
        lines.any { it == "move.w  #1220,p8_regfile+2" } shouldBe false
    }

    test("does not partially forward a multi-argument call") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null)),
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(2), CallingConventionSlot(11), null))
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-atomic"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 384),
                IRInstruction(Opcode.CALL, labelSymbol = "copper.move", fcallArgs = args)
            )
        )

        lines.any { it == "move.w  p8_regfile+0,d0" } shouldBe true
    }

    test("retains the register-file store when the value is used after the call") {
        val args = FunctionCallArgs(
            listOf(FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null))),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-live"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 384),
                IRInstruction(Opcode.CALL, labelSymbol = "copper.move", fcallArgs = args),
                IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1 = 1, labelSymbol = "p8b_test.p8v_value")
            )
        )

        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe true
        lines.any { it == "move.w  #384,d0" } shouldBe true
    }

    test("does not forward across an intervening instruction") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec(
                    "",
                    null,
                    FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null)
                )
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-boundary"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 384),
                IRInstruction(Opcode.NOP),
                IRInstruction(Opcode.CALL, labelSymbol = "copper.move", fcallArgs = args)
            )
        )

        lines.any { it == "move.w  p8_regfile+0,d0" } shouldBe true
    }

    test("retains the register-file store when the value is read via a backward jump") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec(
                    "",
                    null,
                    FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null)
                )
            ),
            emptyList()
        )
        val loopHead = IRCodeChunk("test.start.loop", null)
        loopHead.instructions.add(
            IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1 = 1, labelSymbol = "p8b_test.p8v_out")
        )
        val loopBody = IRCodeChunk(null, null)
        loopBody.instructions.add(IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 384))
        loopBody.instructions.add(IRInstruction(Opcode.CALL, labelSymbol = "copper.move", fcallArgs = args))
        loopBody.instructions.add(IRInstruction(Opcode.JUMP, labelSymbol = "test.start.loop"))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-call-forward-backjump"),
            listOf(loopHead, loopBody)
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe true
    }

    test("suppresses the register-file stores inside a loop when the registers are only used by the call") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec(
                    "",
                    null,
                    FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null)
                )
            ),
            emptyList()
        )
        val loopHead = IRCodeChunk("test.start.loop", null)
        loopHead.instructions.add(IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 2, immediate = 1))
        val loopBody = IRCodeChunk(null, null)
        loopBody.instructions.add(IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 384))
        loopBody.instructions.add(IRInstruction(Opcode.CALL, labelSymbol = "copper.move", fcallArgs = args))
        loopBody.instructions.add(IRInstruction(Opcode.JUMP, labelSymbol = "test.start.loop"))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-call-forward-loop-singleuse"),
            listOf(loopHead, loopBody)
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe false
    }

    test("retains the register-file store when the value is read by a later inline asm chunk") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec(
                    "",
                    null,
                    FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null)
                )
            ),
            emptyList()
        )
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 384))
        chunk.instructions.add(IRInstruction(Opcode.CALL, labelSymbol = "copper.move", fcallArgs = args))
        val inlineAsm = IRInlineAsmChunk(null, "move.w  p8_regfile+0,d1", false, null)
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-call-forward-inlineasm"),
            listOf(chunk, inlineAsm)
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe true
    }

    test("still suppresses the register-file store when an inline asm chunk does not read the register file") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec(
                    "",
                    null,
                    FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null)
                )
            ),
            emptyList()
        )
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 384))
        chunk.instructions.add(IRInstruction(Opcode.CALL, labelSymbol = "copper.move", fcallArgs = args))
        val inlineAsm = IRInlineAsmChunk(null, "move.w  custom.INTREQR,d1", false, null)
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-call-forward-inlineasm-harmless"),
            listOf(chunk, inlineAsm)
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe false
    }
})
