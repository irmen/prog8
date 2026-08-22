package prog8tests.codegen.m68k

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.code.core.*
import prog8.code.target.Amiga500Target
import prog8.code.target.Qemu68kTarget
import prog8.codegen.m68k.AsmGen
import prog8.codegen.m68k.optimizeAssembly
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
            .optimize(true)
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

    test("indirect jumps and calls use memory-indirect addressing on 68020") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-indirect"),
            listOf(
                IRInstruction(Opcode.JUMPI, reg1 = 1),
                IRInstruction(Opcode.CALLI, reg1 = 2)
            )
        )

        lines.count { it.startsWith("jmp  ([p8_regfile+") } shouldBe 1
        lines.count { it.startsWith("jsr  ([p8_regfile+") } shouldBe 1
        lines.count { it.startsWith("move.l  p8_regfile+") && it.endsWith(",a0") } shouldBe 0
    }

    test("signed word divmod omits redundant zero extension") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-divmod-word"),
            listOf(
                IRInstruction(Opcode.DIVMODR, IRDataType.WORD, reg1 = 1, reg2 = 2),
                IRInstruction(Opcode.SDIVMODR, IRDataType.WORD, reg1 = 3, reg2 = 4)
            )
        )

        lines.count { it.startsWith("moveq  #0,d0") && "clear upper word" in it } shouldBe 1
        lines.count { it.startsWith("ext.l  d0") } shouldBe 1
        lines.count { it.startsWith("divu.w  p8_regfile+") } shouldBe 1
        lines.count { it.startsWith("divs.w  p8_regfile+") } shouldBe 1
    }

    test("byte and word extraction uses direct register-file loads") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-extract"),
            listOf(
                IRInstruction(Opcode.MSIGB, IRDataType.WORD, reg1 = 1, reg2 = 2),
                IRInstruction(Opcode.MSIGB, IRDataType.LONG, reg1 = 3, reg2 = 4),
                IRInstruction(Opcode.LSIGB, IRDataType.WORD, reg1 = 5, reg2 = 6),
                IRInstruction(Opcode.LSIGB, IRDataType.LONG, reg1 = 7, reg2 = 8),
                IRInstruction(Opcode.MSIGW, IRDataType.LONG, reg1 = 9, reg2 = 10),
                IRInstruction(Opcode.LSIGW, IRDataType.LONG, reg1 = 11, reg2 = 12),
                IRInstruction(Opcode.BSIGB, IRDataType.LONG, reg1 = 13, reg2 = 14),
                IRInstruction(Opcode.MIDB, IRDataType.LONG, reg1 = 15, reg2 = 16)
            )
        )

        lines.count { it.startsWith("lsr") } shouldBe 0
        lines.count { it.startsWith("swap") } shouldBe 0
        lines.count { it.startsWith("clr.w") } shouldBe 0
        lines.count { it.startsWith("move.b  p8_regfile+") && ",d0" in it } shouldBe 4
    }

    test("lsb/msb on words and longs use direct byte loads") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-lsb-msb"),
            listOf(
                IRInstruction(Opcode.LSIGB, IRDataType.WORD, reg1 = 1, reg2 = 2),
                IRInstruction(Opcode.LSIGB, IRDataType.LONG, reg1 = 3, reg2 = 4),
                IRInstruction(Opcode.MSIGB, IRDataType.WORD, reg1 = 5, reg2 = 6),
                IRInstruction(Opcode.MSIGB, IRDataType.LONG, reg1 = 7, reg2 = 8)
            )
        )

        lines.count { it.startsWith("lsr") } shouldBe 0
        lines.count { it.startsWith("swap") } shouldBe 0
        lines.count { it.startsWith("move.b  p8_regfile+") && ",d0" in it } shouldBe 2
    }

    test("lsw/msw on longs use direct word loads") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-lsw-msw"),
            listOf(
                IRInstruction(Opcode.LSIGW, IRDataType.LONG, reg1 = 1, reg2 = 2),
                IRInstruction(Opcode.MSIGW, IRDataType.LONG, reg1 = 3, reg2 = 4)
            )
        )

        lines.count { it.startsWith("lsr") } shouldBe 0
        lines.count { it.startsWith("swap") } shouldBe 0
        lines.count { it.startsWith("clr.w") } shouldBe 0
        lines.count { it.startsWith("move.w  p8_regfile+") && ",d0" in it } shouldBe 1
    }

    test("lmh uses BSIGB, MIDB, LSIGB") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-lmh"),
            listOf(
                IRInstruction(Opcode.BSIGB, IRDataType.LONG, reg1 = 1, reg2 = 2),
                IRInstruction(Opcode.MIDB, IRDataType.LONG, reg1 = 3, reg2 = 4),
                IRInstruction(Opcode.LSIGB, IRDataType.LONG, reg1 = 5, reg2 = 6)
            )
        )

        lines.count { it.startsWith("lsr") } shouldBe 0
        lines.count { it.startsWith("move.b  p8_regfile+") && ",d0" in it } shouldBe 2
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

    test("retains immediate loads for named call arguments") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec("value", null, FunctionCallArgs.RegSpec(IRDataType.BYTE, RegisterNum(1), null, null))
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-named-argument"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = 1, immediate = 42),
                IRInstruction(Opcode.CALL, labelSymbol = "callee", fcallArgs = args)
            )
        )

        lines.any { it == "move.b  #42,p8_regfile+0" } shouldBe true
        lines.any { it == "move.b  p8_regfile+0,callee.value" } shouldBe true
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

    test("uses moveq for forwarded small immediate call arguments") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null)),
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.BYTE, RegisterNum(2), CallingConventionSlot(11), null))
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-moveq"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 100),
                IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = 2, immediate = 48),
                IRInstruction(Opcode.CALL, labelSymbol = "copper.wait", fcallArgs = args)
            )
        )

        lines.any { it == "moveq  #100,d0" } shouldBe true
        lines.any { it == "moveq  #48,d1" } shouldBe true
        lines.any { it == "move.w  #100,d0" } shouldBe false
        lines.any { it == "move.b  #48,d1" } shouldBe false
    }

    test("maps byte values 128-255 to signed moveq range for forwarded arguments") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.BYTE, RegisterNum(1), CallingConventionSlot(10), null))
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-moveq-signed"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = 1, immediate = 200),
                IRInstruction(Opcode.CALL, labelSymbol = "copper.wait", fcallArgs = args)
            )
        )

        lines.any { it == "moveq  #-56,d0" } shouldBe true
        lines.any { it == "move.b  #200,d0" } shouldBe false
    }

    test("keeps move.w for forwarded immediates that do not fit moveq range") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null))
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-nomoveq"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 384),
                IRInstruction(Opcode.CALL, labelSymbol = "copper.move", fcallArgs = args)
            )
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "moveq  #384,d0" } shouldBe false
    }

    // === Floating-point immediate call-argument forwarding and dead-store removal ===

    test("forwards an immediate float load into a following FPU hardware-register call argument") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec(
                    "",
                    null,
                    FunctionCallArgs.RegSpec(IRDataType.FLOAT, RegisterNum(1), CallingConventionSlot(25), null)
                )
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-float"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = RegisterNum(1), immediateFp = 1.0),
                IRInstruction(Opcode.CALL, labelSymbol = "math.func", fcallArgs = args)
            )
        )

        lines.any { it == "fmovecr  #\$32,fp0" } shouldBe true
        lines.any { it == "fmove.s  fp0,p8_fregfile+0" } shouldBe false
        lines.any { it == "fmove.s  p8_fregfile+0,fp0" } shouldBe false
    }

    test("forwards an immediate float load into a different FPU register and drops the dead fregfile store") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec(
                    "",
                    null,
                    FunctionCallArgs.RegSpec(IRDataType.FLOAT, RegisterNum(1), CallingConventionSlot(26), null)
                )
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-float-fp1"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = RegisterNum(1), immediateFp = 100.0),
                IRInstruction(Opcode.CALL, labelSymbol = "math.func", fcallArgs = args)
            )
        )

        lines.any { it == "fmovecr  #\$34,fp1" } shouldBe true
        lines.any { it == "fmove.s  fp0,p8_fregfile+0" } shouldBe false
        lines.any { it == "fmove.s  p8_fregfile+0,fp1" } shouldBe false
    }

    test("forwards both integer and float immediate loads for a mixed-argument call") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null)),
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.FLOAT, RegisterNum(2), CallingConventionSlot(25), null))
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-mixed"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 384),
                IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = RegisterNum(2), immediateFp = 1.0),
                IRInstruction(Opcode.CALL, labelSymbol = "mixed.func", fcallArgs = args)
            )
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe false
        lines.any { it == "fmovecr  #\$32,fp0" } shouldBe true
        lines.any { it == "fmove.s  fp0,p8_fregfile+0" } shouldBe false
        lines.any { it == "fmove.s  p8_fregfile+0,fp0" } shouldBe false
    }

    test("retains the fregfile store when a forwarded float value is used after the call") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.FLOAT, RegisterNum(1), CallingConventionSlot(25), null))
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-float-live"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = RegisterNum(1), immediateFp = 1.0),
                IRInstruction(Opcode.CALL, labelSymbol = "math.func", fcallArgs = args),
                IRInstruction(Opcode.STOREM, IRDataType.FLOAT, fpReg1 = RegisterNum(1), labelSymbol = "p8b_test.p8v_value")
            )
        )

        lines.any { it == "fmove.s  fp0,p8_fregfile+0" } shouldBe true
        lines.count { it == "fmove.s  p8_fregfile+0,fp0" } shouldBe 1
    }

    test("does not partially forward a mixed-argument call when a float arg has no immediate load") {
        val args = FunctionCallArgs(
            listOf(
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), CallingConventionSlot(10), null)),
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.FLOAT, RegisterNum(2), CallingConventionSlot(25), null))
            ),
            emptyList()
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-mixed-atomic"),
            listOf(
                IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 1, immediate = 384),
                IRInstruction(Opcode.CALL, labelSymbol = "mixed.func", fcallArgs = args)
            )
        )

        lines.any { it == "move.w  p8_regfile+0,d0" } shouldBe true
        lines.any { it == "fmove.s  p8_fregfile+0,fp0" } shouldBe true
    }

    test("loads pointers into a0 with movea and without a +0 offset") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-pointer-a0"),
            listOf(
                IRInstruction(Opcode.STOREZI, IRDataType.WORD, reg1 = 1, immediate = 1)
            )
        )

        lines.any { it == "movea.l  p8_regfile,a0" } shouldBe true
        lines.any { it.startsWith("move.l  p8_regfile") } shouldBe false
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

    test("removes jmp to immediately following label") {
        val chunk1 = IRCodeChunk(null, null)
        chunk1.instructions.add(IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = 1, immediate = 42))
        chunk1.instructions.add(IRInstruction(Opcode.JUMP, labelSymbol = "test.end"))
        val chunk2 = IRCodeChunk("test.end", null)
        chunk2.instructions.add(IRInstruction(Opcode.RETURN))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-jmp-to-next-label"),
            listOf(chunk1, chunk2)
        )

        lines.any { it.startsWith("bra  test.end") } shouldBe false
        lines.any { it == "rts" } shouldBe true
    }

    test("removes jmp to immediately following label but keeps label on jmp line") {
        val chunk1 = IRCodeChunk(null, null)
        chunk1.instructions.add(IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = 1, immediate = 42))
        val chunk2 = IRCodeChunk("test.mid", null)
        chunk2.instructions.add(IRInstruction(Opcode.JUMP, labelSymbol = "test.end"))
        val chunk3 = IRCodeChunk("test.end", null)
        chunk3.instructions.add(IRInstruction(Opcode.RETURN))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-jmp-to-next-label-with-label"),
            listOf(chunk1, chunk2, chunk3)
        )

        lines.any { it.startsWith("bra  test.end") } shouldBe false
        lines.any { it.startsWith("test.mid:") } shouldBe true
        lines.any { it == "rts" } shouldBe true
    }

    test("optimizes bsr+rts to bra (tail call)") {
        // When bsr+rts is followed immediately by the target label, both bsr and rts are removed
        // (optimizeJmpToNextLabel removes the bra that optimizeTailCall created)
        val chunk1 = IRCodeChunk(null, null)
        chunk1.instructions.add(IRInstruction(Opcode.CALL, labelSymbol = "test.target"))
        chunk1.instructions.add(IRInstruction(Opcode.RETURN))
        val chunk2 = IRCodeChunk("test.target", null)
        chunk2.instructions.add(IRInstruction(Opcode.RETURN))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-tail-call"),
            listOf(chunk1, chunk2)
        )

        lines.any { it.startsWith("bsr  test.target") } shouldBe false
        // The bra is removed by optimizeJmpToNextLabel because test.target: immediately follows
        lines.any { it.startsWith("bra  test.target") } shouldBe false
        // Count rts only in the test subroutine (after "test.start:"), not in startup code
        val testStartIdx = lines.indexOfFirst { it.startsWith("test.start:") }
        val testLines = lines.drop(testStartIdx)
        testLines.count { it == "rts" } shouldBe 1
    }

    test("optimizes bsr+rts to bra when target is not immediately following") {
        // When there's code between the bsr+rts and the target label, the bra is kept
        val chunk1 = IRCodeChunk(null, null)
        chunk1.instructions.add(IRInstruction(Opcode.CALL, labelSymbol = "test.target"))
        chunk1.instructions.add(IRInstruction(Opcode.RETURN))
        val chunk2 = IRCodeChunk(null, null)
        chunk2.instructions.add(IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = 1, immediate = 99))
        val chunk3 = IRCodeChunk("test.target", null)
        chunk3.instructions.add(IRInstruction(Opcode.RETURN))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-tail-call-with-gap"),
            listOf(chunk1, chunk2, chunk3)
        )

        lines.any { it.startsWith("bsr  test.target") } shouldBe false
        lines.any { it.startsWith("bra  test.target") } shouldBe true
        // Count rts only in the test subroutine
        val testStartIdx = lines.indexOfFirst { it.startsWith("test.start:") }
        val testLines = lines.drop(testStartIdx)
        testLines.count { it == "rts" } shouldBe 1
    }

    test("optimizes bsr+rts to bra but keeps label on bsr line") {
        val chunk1 = IRCodeChunk(null, null)
        chunk1.instructions.add(IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = 1, immediate = 42))
        val chunk2 = IRCodeChunk("test.caller", null)
        chunk2.instructions.add(IRInstruction(Opcode.CALL, labelSymbol = "test.target"))
        chunk2.instructions.add(IRInstruction(Opcode.RETURN))
        val chunk3 = IRCodeChunk(null, null)
        chunk3.instructions.add(IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = 2, immediate = 99))
        val chunk4 = IRCodeChunk("test.target", null)
        chunk4.instructions.add(IRInstruction(Opcode.RETURN))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-tail-call-with-label"),
            listOf(chunk1, chunk2, chunk3, chunk4)
        )

        lines.any { it.startsWith("bsr  test.target") } shouldBe false
        lines.any { it.startsWith("bra  test.target") } shouldBe true
        lines.any { it.startsWith("test.caller:") } shouldBe true
        // Count rts only in the test subroutine
        val testStartIdx = lines.indexOfFirst { it.startsWith("test.start:") }
        val testLines = lines.drop(testStartIdx)
        testLines.count { it == "rts" } shouldBe 1
    }

    test("removes redundant tst after move to same location") {
        // Test the optimizer directly on raw assembly lines
        val lines = mutableListOf(
            "    move.b  d0, p8_regfile+198",
            "    tst.b   p8_regfile+198",
            "    beq     somewhere"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("tst.b") } shouldBe false
        lines.any { it.contains("move.b") } shouldBe true
        lines.any { it.contains("beq") } shouldBe true
    }

    test("removes redundant tst after move with label on move line") {
        val lines = mutableListOf(
            "mylabel:",
            "    move.w  d0, p8_regfile+200",
            "    tst.w   p8_regfile+200",
            "    bne     somewhere"
        )
        optimizeAssembly(lines)
        // tst should be removed, label should stay on move line
        lines.any { it.contains("tst.w") } shouldBe false
        lines.any { it.contains("mylabel:") } shouldBe true
        lines.any { it.contains("move.w") } shouldBe true
    }

    test("does not remove tst when target is different from move destination") {
        val lines = mutableListOf(
            "    move.b  d0, p8_regfile+198",
            "    tst.b   p8_regfile+200",
            "    beq     somewhere"
        )
        optimizeAssembly(lines)
        // tst should NOT be removed because it tests a different location
        lines.any { it.contains("tst.b") } shouldBe true
    }

    test("does not remove tst when size differs from move") {
        val lines = mutableListOf(
            "    move.b  d0, p8_regfile+198",
            "    tst.w   p8_regfile+198",
            "    beq     somewhere"
        )
        optimizeAssembly(lines)
        // tst should NOT be removed because sizes differ
        lines.any { it.contains("tst.w") } shouldBe true
    }

    // === Tests for status flag return handling fixes (commit 1286b6cc8 follow-up) ===

    test("single-return expression call with @A0 (slot 18) emits store instruction") {
        // Bug fix: single-return expression calls with @A0-A6/@FP0-FP7 returns
        // should emit the store instruction because the IR doesn't generate LOADHR
        // for single-return calls.
        val args = FunctionCallArgs(
            emptyList(),
            listOf(
                FunctionCallArgs.RegSpec(IRDataType.POINTER, RegisterNum(0), CallingConventionSlot(18), null)
            )
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-single-return-a0"),
            listOf(
                IRInstruction(Opcode.CALL, labelSymbol = "test.asmsub", fcallArgs = args)
            )
        )
        // Should emit a store from a0 to virtual register
        lines.any { it.contains("move.l") && it.contains("a0") && it.contains("p8_regfile") } shouldBe true
    }

    test("single-return expression call with @FP0 (slot 25) emits store instruction") {
        // Bug fix: single-return expression calls with float register returns
        // should emit the store instruction.
        val args = FunctionCallArgs(
            emptyList(),
            listOf(
                FunctionCallArgs.RegSpec(IRDataType.FLOAT, RegisterNum(0), CallingConventionSlot(25), null)
            )
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-single-return-fp0"),
            listOf(
                IRInstruction(Opcode.CALL, labelSymbol = "test.asmsub", fcallArgs = args)
            )
        )
        // Should emit fmove.s from fp0 to virtual register
        lines.any { it.contains("fmove.s") && it.contains("fp0") } shouldBe true
    }

    test("multi-assign with @D0 + @Pz does not emit move between jsr and branch") {
        // Bug fix: in multi-assign context, slot returns should be skipped because
        // the IR generates LOADHR for them. Emitting move here would clobber CPU flags
        // before the IR's branch pattern can read them.
        val args = FunctionCallArgs(
            emptyList(),
            listOf(
                FunctionCallArgs.RegSpec(IRDataType.BYTE, RegisterNum(0), CallingConventionSlot(10), null),
                FunctionCallArgs.RegSpec(IRDataType.BYTE, RegisterNum(1), null, Statusflag.Pz)
            )
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-multi-assign-d0-pz"),
            listOf(
                IRInstruction(Opcode.CALL, labelSymbol = "test.asmsub", fcallArgs = args)
            )
        )
        // Find the bsr line
        val jsrIndex = lines.indexOfFirst { it.startsWith("bsr") && it.contains("test.asmsub") }
        jsrIndex shouldBeGreaterThan -1
        // There should be NO move from d0 between jsr and any branch instruction (beq/bne)
        val afterJsr = lines.subList(jsrIndex + 1, lines.size)
        val branchIndex = afterJsr.indexOfFirst { it.startsWith("beq") || it.startsWith("bne") || it.startsWith("bmi") || it.startsWith("bpl") }
        if (branchIndex > 0) {
            val betweenJsrAndBranch = afterJsr.subList(0, branchIndex)
            betweenJsrAndBranch.any { it.contains("move") && it.contains("d0") && it.contains("p8_regfile") } shouldBe false
        }
    }

    test("CALLFAR multi-assign with slot + flag return does not emit move between jsr and branch") {
        // Bug fix: CALLFAR in multi-assign context should also skip slot returns.
        // CALLFAR only works for amiga targets, so we need a separate helper.
        val args = FunctionCallArgs(
            emptyList(),
            listOf(
                FunctionCallArgs.RegSpec(IRDataType.POINTER, RegisterNum(0), CallingConventionSlot(18), null),
                FunctionCallArgs.RegSpec(IRDataType.BYTE, RegisterNum(1), null, Statusflag.Pz)
            )
        )
        // For amiga CALLFAR, address is the LVO offset (negative as Int, but stored as UInt bits)
        // -30 as Int = 0xFFFFFFE2 as UInt bits
        val negativeOffset = (-30).toUInt()
        val callfarInsn = IRInstruction(
            Opcode.CALLFAR,
            labelSymbol = "test.libfunc",
            fcallArgs = args,
            address = MemoryAddress(negativeOffset),
            immediate = 1  // bank number (1=exec.library)
        ).apply { extSubName = "test.libfunc" }

        val target = Amiga500Target()
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.FLOATSAFE)
            .floats(false)
            .compilerVersion("test")
            .memtopAddress(0xffffu)
            .optimize(true)
            .build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        program.options.outputDir = tempRoot.resolve("test-m68k-callfar-multi")
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(callfarInsn)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val output = program.options.outputDir.toFile()
        output.deleteRecursively()
        output.mkdirs()
        AsmGen(program, target).generate()
        val asmFile = program.options.outputDir.resolve("test.asm")
        check(asmFile.exists()) { "Assembly file not written: $asmFile" }
        val lines = asmFile.readText().lines().map { it.trim() }

        // Find the jsr line
        val jsrIndex = lines.indexOfFirst { it.startsWith("jsr") }
        jsrIndex shouldBeGreaterThan -1
        // There should be NO move from a0 between jsr and any branch instruction
        val afterJsr = lines.subList(jsrIndex + 1, lines.size)
        val branchIndex = afterJsr.indexOfFirst { it.startsWith("beq") || it.startsWith("bne") }
        if (branchIndex > 0) {
            val betweenJsrAndBranch = afterJsr.subList(0, branchIndex)
            betweenJsrAndBranch.any { it.contains("move") && it.contains("a0") && it.contains("p8_regfile") } shouldBe false
        }
    }
})
