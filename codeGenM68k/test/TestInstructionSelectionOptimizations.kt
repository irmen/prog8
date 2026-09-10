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

    fun generateAsmChunks(outputDir: Path, chunks: List<IRCodeChunkBase>, target: ICompilationTarget = Qemu68kTarget()): List<String> {
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

    fun generateAsm(outputDir: Path, instructions: List<IRInstruction>, target: ICompilationTarget = Qemu68kTarget()): List<String> {
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.addAll(instructions)
        return generateAsmChunks(outputDir, listOf(chunk), target)
    }

    fun hwArg(register: Int, type: IRDataType, slot: Int): CallArgument =
        Calls.argument(register, type, CallLocation.HardwareRegister(CallingConventionSlot(slot)))

    fun hwResult(register: Int, type: IRDataType, slot: Int): CallResult =
        Calls.result(register, type, CallLocation.HardwareRegister(CallingConventionSlot(slot)))

    fun flagResult(register: Int, flag: Statusflag): CallResult =
        Calls.result(register, IRDataType.BYTE, CallLocation.StatusFlag(flag))

    fun callSite(
        label: String,
        arguments: List<CallArgument> = emptyList(),
        results: List<CallResult> = emptyList()
    ): CallSite = CallSite(CallTarget.Direct(codeLabel(label)), arguments, results)

    test("uses quick address adjustments and preserves large offsets") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-offsets"),
            listOf(
                IRInstructions.storeZero(Opcode.STOREZI, IRDataType.FLOAT, IRMemory.indirect(1, 1)),
                IRInstructions.storeZero(Opcode.STOREZI, IRDataType.FLOAT, IRMemory.indirect(1, 65535))
            )
        )

        lines.count { it == "addq.l  #1,a0" } shouldBe 1
        lines.count { it == "adda.l  #65535,a0" } shouldBe 1
    }

    test("uses moveq for zero extension and only representable immediate returns") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-control"),
            listOf(
                IRInstructions.binary(Opcode.EXT, IRDataType.BYTE, 2, 1),
                IRInstructions.binary(Opcode.EXT, IRDataType.WORD, 3, 2),
                IRInstructions.returnImmediate(IRDataType.BYTE, 42),
                IRInstructions.returnImmediate(IRDataType.BYTE, 255)
            )
        )

        lines.count { it == "moveq  #0,d0" } shouldBe 2
        lines.any { it == "moveq  #42,d0" } shouldBe true
        lines.any { it == "move.b  #255,d0" } shouldBe true
    }

    test("compares a virtual register directly against memory") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-compare"),
            listOf(IRInstructions.compare(IRDataType.BYTE, 1, 2))
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
                IRInstructions.binary(Opcode.MODR, IRDataType.BYTE, 1, 2),
                IRInstructions.binary(Opcode.MODSR, IRDataType.BYTE, 3, 4)
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
                IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 1, 2),
                IRInstructions.binary(Opcode.MULR, IRDataType.LONG, 3, 4)
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
                IRInstructions.jumpIndirect(1),
                IRInstructions.call(Opcode.CALLI, CallSite(CallTarget.Direct(codeIndirect(2))))
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
                IRInstructions.divmodRegister(Opcode.DIVMODR, IRDataType.WORD, 1, 2),
                IRInstructions.divmodRegister(Opcode.SDIVMODR, IRDataType.WORD, 3, 4)
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
                IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, 1, 2),
                IRInstructions.binary(Opcode.MSIGB, IRDataType.LONG, 3, 4),
                IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, 5, 6),
                IRInstructions.binary(Opcode.LSIGB, IRDataType.LONG, 7, 8),
                IRInstructions.binary(Opcode.MSIGW, IRDataType.LONG, 9, 10),
                IRInstructions.binary(Opcode.LSIGW, IRDataType.LONG, 11, 12),
                IRInstructions.binary(Opcode.BSIGB, IRDataType.LONG, 13, 14),
                IRInstructions.binary(Opcode.MIDB, IRDataType.LONG, 15, 16)
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
                IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, 1, 2),
                IRInstructions.binary(Opcode.LSIGB, IRDataType.LONG, 3, 4),
                IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, 5, 6),
                IRInstructions.binary(Opcode.MSIGB, IRDataType.LONG, 7, 8)
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
                IRInstructions.binary(Opcode.LSIGW, IRDataType.LONG, 1, 2),
                IRInstructions.binary(Opcode.MSIGW, IRDataType.LONG, 3, 4)
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
                IRInstructions.binary(Opcode.BSIGB, IRDataType.LONG, 1, 2),
                IRInstructions.binary(Opcode.MIDB, IRDataType.LONG, 3, 4),
                IRInstructions.binary(Opcode.LSIGB, IRDataType.LONG, 5, 6)
            )
        )

        lines.count { it.startsWith("lsr") } shouldBe 0
        lines.count { it.startsWith("move.b  p8_regfile+") && ",d0" in it } shouldBe 2
    }

    test("forwards an immediate load into a following hardware-register call argument") {
        val site = callSite("copper.move", arguments = listOf(hwArg(1, IRDataType.WORD, 10)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward"),
            listOf(
                IRInstructions.load(IRDataType.WORD, 1, 384),
                IRInstructions.call(site)
            )
        )

        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe false
        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  p8_regfile+0,d0" } shouldBe false
    }

    test("forwards all immediate loads for a multi-argument call") {
        val site = callSite("copper.move", arguments = listOf(hwArg(1, IRDataType.WORD, 10), hwArg(2, IRDataType.WORD, 11)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-multi"),
            listOf(
                IRInstructions.load(IRDataType.WORD, 1, 384),
                IRInstructions.load(IRDataType.WORD, 2, 1220),
                IRInstructions.call(site)
            )
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  #1220,d1" } shouldBe true
        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe false
        lines.any { it == "move.w  #1220,p8_regfile+2" } shouldBe false
    }

    test("does not partially forward a multi-argument call") {
        val site = callSite("copper.move", arguments = listOf(hwArg(1, IRDataType.WORD, 10), hwArg(2, IRDataType.WORD, 11)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-atomic"),
            listOf(
                IRInstructions.load(IRDataType.WORD, 1, 384),
                IRInstructions.call(site)
            )
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  p8_regfile+0,d0" } shouldBe false
    }

    test("retains immediate loads for named call arguments") {
        val site = callSite(
            "callee",
            arguments = listOf(Calls.argument(1, IRDataType.BYTE, CallLocation.ParameterMemory("value")))
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-named-argument"),
            listOf(
                IRInstructions.load(IRDataType.BYTE, 1, 42),
                IRInstructions.call(site)
            )
        )

        lines.any { it == "move.b  #42,p8_regfile+0" } shouldBe true
        lines.any { it == "move.b  p8_regfile+0,callee.value" } shouldBe true
    }

    test("retains the register-file store when the value is used after the call") {
        val site = callSite("copper.move", arguments = listOf(hwArg(1, IRDataType.WORD, 10)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-live"),
            listOf(
                IRInstructions.load(IRDataType.WORD, 1, 384),
                IRInstructions.call(site),
                IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_value"))
            )
        )

        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe true
        lines.any { it == "move.w  #384,d0" } shouldBe true
    }

    test("uses moveq for forwarded small immediate call arguments") {
        val site = callSite("copper.wait", arguments = listOf(hwArg(1, IRDataType.WORD, 10), hwArg(2, IRDataType.BYTE, 11)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-moveq"),
            listOf(
                IRInstructions.load(IRDataType.WORD, 1, 100),
                IRInstructions.load(IRDataType.BYTE, 2, 48),
                IRInstructions.call(site)
            )
        )

        lines.any { it == "moveq  #100,d0" } shouldBe true
        lines.any { it == "moveq  #48,d1" } shouldBe true
        lines.any { it == "move.w  #100,d0" } shouldBe false
        lines.any { it == "move.b  #48,d1" } shouldBe false
    }

    test("maps byte values 128-255 to signed moveq range for forwarded arguments") {
        val site = callSite("copper.wait", arguments = listOf(hwArg(1, IRDataType.BYTE, 10)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-moveq-signed"),
            listOf(
                IRInstructions.load(IRDataType.BYTE, 1, 200),
                IRInstructions.call(site)
            )
        )

        lines.any { it == "moveq  #-56,d0" } shouldBe true
        lines.any { it == "move.b  #200,d0" } shouldBe false
    }

    test("keeps move.w for forwarded immediates that do not fit moveq range") {
        val site = callSite("copper.move", arguments = listOf(hwArg(1, IRDataType.WORD, 10)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-nomoveq"),
            listOf(
                IRInstructions.load(IRDataType.WORD, 1, 384),
                IRInstructions.call(site)
            )
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "moveq  #384,d0" } shouldBe false
    }

    // === Floating-point immediate call-argument forwarding and dead-store removal ===

    test("forwards an immediate float load into a following FPU hardware-register call argument") {
        val site = callSite("math.func", arguments = listOf(hwArg(1, IRDataType.FLOAT, 25)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-float"),
            listOf(
                IRInstructions.loadFloat(1, 1.0),
                IRInstructions.call(site)
            )
        )

        lines.any { it == $$"fmovecr  #$32,fp0" } shouldBe true
        lines.any { it == "fmove.s  fp0,p8_fregfile+0" } shouldBe false
        lines.any { it == "fmove.s  p8_fregfile+0,fp0" } shouldBe false
    }

    test("forwards an immediate float load into a different FPU register and drops the dead fregfile store") {
        val site = callSite("math.func", arguments = listOf(hwArg(1, IRDataType.FLOAT, 26)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-float-fp1"),
            listOf(
                IRInstructions.loadFloat(1, 100.0),
                IRInstructions.call(site)
            )
        )

        lines.any { it == $$"fmovecr  #$34,fp1" } shouldBe true
        lines.any { it == "fmove.s  fp0,p8_fregfile+0" } shouldBe false
        lines.any { it == "fmove.s  p8_fregfile+0,fp1" } shouldBe false
    }

    test("forwards both integer and float immediate loads for a mixed-argument call") {
        val site = callSite("mixed.func", arguments = listOf(hwArg(1, IRDataType.WORD, 10), hwArg(2, IRDataType.FLOAT, 25)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-mixed"),
            listOf(
                IRInstructions.load(IRDataType.WORD, 1, 384),
                IRInstructions.loadFloat(2, 1.0),
                IRInstructions.call(site)
            )
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe false
        lines.any { it == $$"fmovecr  #$32,fp0" } shouldBe true
        lines.any { it == "fmove.s  fp0,p8_fregfile+0" } shouldBe false
        lines.any { it == "fmove.s  p8_fregfile+0,fp0" } shouldBe false
    }

    test("retains the fregfile store when a forwarded float value is used after the call") {
        val site = callSite("math.func", arguments = listOf(hwArg(1, IRDataType.FLOAT, 25)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-float-live"),
            listOf(
                IRInstructions.loadFloat(1, 1.0),
                IRInstructions.call(site),
                IRInstructions.storeMemory(Opcode.STOREM, IRDataType.FLOAT, 1, IRMemory.direct("p8b_test.p8v_value"))
            )
        )

        lines.any { it == "fmove.s  fp0,p8_fregfile+0" } shouldBe true
        lines.count { it == "fmove.s  p8_fregfile+0,fp0" } shouldBe 1
    }

    // === sys.memcopy inlining ===

    fun memcopySite(): CallSite = callSite(
        "sys.memcopy",
        arguments = listOf(
            hwArg(1, IRDataType.LONG, 18),
            hwArg(2, IRDataType.LONG, 19),
            hwArg(3, IRDataType.LONG, 10)
        )
    )

    fun generateMemcopyAsm(outputDir: Path, srcImm: UInt?, tgtImm: UInt?, countImm: UInt?): List<String> {
        val instructions = mutableListOf<IRInstruction>()
        if (srcImm != null)
            instructions.add(IRInstructions.load(IRDataType.LONG, 1, srcImm.toInt()))
        if (tgtImm != null)
            instructions.add(IRInstructions.load(IRDataType.LONG, 2, tgtImm.toInt()))
        if (countImm != null)
            instructions.add(IRInstructions.load(IRDataType.LONG, 3, countImm.toInt()))
        instructions.add(IRInstructions.call(memcopySite()))
        return generateAsm(outputDir, instructions)
    }

    test("inlines sys.memcopy for small constant byte counts") {
        val lines = generateMemcopyAsm(tempRoot.resolve("test-m68k-memcopy-byte"), 0x1000u, 0x2000u, 5u)

        lines.any { it == "bsr  sys.memcopy" } shouldBe false
        lines.count { it == "move.b  (a0)+,(a1)+" } shouldBe 5
        lines.any { it.endsWith("inline memcopy 5 bytes") } shouldBe true
    }

    test("inlines sys.memcopy with long moves when both pointers are long-aligned") {
        val lines = generateMemcopyAsm(tempRoot.resolve("test-m68k-memcopy-word"), 0x1000u, 0x2000u, 8u)

        lines.any { it == "bsr  sys.memcopy" } shouldBe false
        lines.count { it == "move.l  (a0)+,(a1)+" } shouldBe 2
        lines.count { it == "move.b  (a0)+,(a1)+" } shouldBe 0
    }

    test("uses dbra long loop for long counts above unrolled limit") {
        val lines = generateMemcopyAsm(tempRoot.resolve("test-m68k-memcopy-long-loop"), 0x1000u, 0x2000u, 36u)

        lines.any { it == "bsr  sys.memcopy" } shouldBe false
        lines.count { it == "move.l  (a0)+,(a1)+" } shouldBe 1
        lines.any { it.contains("dbra") } shouldBe true
    }

    test("calls sys.memcopy when long count exceeds inline threshold") {
        val lines = generateMemcopyAsm(tempRoot.resolve("test-m68k-memcopy-long-threshold"), 0x1000u, 0x2000u, 68u)

        lines.any { it == "bsr  sys.memcopy" } shouldBe true
        lines.count { it == "move.l  (a0)+,(a1)+" } shouldBe 0
    }

    test("falls back to byte loop when source pointer is odd") {
        val lines = generateMemcopyAsm(tempRoot.resolve("test-m68k-memcopy-unaligned"), 0x1001u, 0x2000u, 8u)

        lines.any { it == "bsr  sys.memcopy" } shouldBe false
        lines.count { it == "move.b  (a0)+,(a1)+" } shouldBe 1
        lines.any { it.contains("dbra") } shouldBe true
    }

    test("uses dbra byte loop for byte counts above unrolled limit") {
        val lines = generateMemcopyAsm(tempRoot.resolve("test-m68k-memcopy-byte-loop"), 0x1001u, 0x2000u, 12u)

        lines.any { it == "bsr  sys.memcopy" } shouldBe false
        lines.count { it == "move.b  (a0)+,(a1)+" } shouldBe 1
        lines.any { it.contains("dbra") } shouldBe true
    }

    test("calls sys.memcopy when byte count exceeds inline threshold") {
        val lines = generateMemcopyAsm(tempRoot.resolve("test-m68k-memcopy-large"), 0x1000u, 0x2000u, 17u)

        lines.any { it == "bsr  sys.memcopy" } shouldBe true
        lines.count { it == "move.b  (a0)+,(a1)+" } shouldBe 0
    }

    test("calls sys.memcopy when count is not a forwarded immediate") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-memcopy-variable"),
            listOf(
                IRInstructions.load(IRDataType.LONG, 1, 0x1000),
                IRInstructions.load(IRDataType.LONG, 2, 0x2000),
                // no LOAD for r3, count comes from elsewhere (simulated by loading from a made-up value)
                IRInstructions.loadMemory(Opcode.LOADM, IRDataType.LONG, 3, IRMemory.direct("p8b_test.p8v_count")),
                IRInstructions.call(memcopySite())
            )
        )

        lines.any { it == "bsr  sys.memcopy" } shouldBe true
    }

    test("inlines sys.memcopy zero bytes as no-op") {
        val lines = generateMemcopyAsm(tempRoot.resolve("test-m68k-memcopy-zero"), 0x1000u, 0x2000u, 0u)

        lines.any { it == "bsr  sys.memcopy" } shouldBe false
        lines.count { it == "move.b  (a0)+,(a1)+" } shouldBe 0
    }

    test("does not partially forward a mixed-argument call when a float arg has no immediate load") {
        val site = callSite("mixed.func", arguments = listOf(hwArg(1, IRDataType.WORD, 10), hwArg(2, IRDataType.FLOAT, 25)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-mixed-atomic"),
            listOf(
                IRInstructions.load(IRDataType.WORD, 1, 384),
                IRInstructions.call(site)
            )
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "fmove.s  p8_fregfile+0,fp0" } shouldBe true
    }

    test("loads pointers into a0 with movea and without a +0 offset") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-pointer-a0"),
            listOf(
                IRInstructions.storeZero(Opcode.STOREZI, IRDataType.WORD, IRMemory.indirect(1, 1))
            )
        )

        lines.any { it == "movea.l  p8_regfile,a0" } shouldBe true
        lines.any { it.startsWith("move.l  p8_regfile") } shouldBe false
    }

    test("ADDR.P zero-extends a word source") {
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.load(IRDataType.POINTER, 1, 0x10000))
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 2, 10))
        chunk.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.POINTER, 1, 2))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-addr-p-word-src"),
            listOf(chunk)
        )
        lines.any { it == "moveq  #0,d0" } shouldBe true
        lines.any { it == "move.w  p8_regfile+4,d0" } shouldBe true
        lines.any { it == "add.l  d0,p8_regfile+0" } shouldBe true
        lines.any { it == "move.l  p8_regfile+4,d0" } shouldBe false
    }

    test("ADDR.P does not zero-extend a long source") {
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.load(IRDataType.POINTER, 1, 0x10000))
        chunk.instructions.add(IRInstructions.load(IRDataType.LONG, 2, 10))
        chunk.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.POINTER, 1, 2))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-addr-p-long-src"),
            listOf(chunk)
        )
        lines.any { it == "move.l  p8_regfile+4,d0" } shouldBe true
        lines.any { it == "add.l  d0,p8_regfile+0" } shouldBe true
        lines.any { it == "moveq  #0,d0" } shouldBe false
    }

    test("SUBR.P zero-extends a word source") {
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.load(IRDataType.POINTER, 1, 0x10000))
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 2, 10))
        chunk.instructions.add(IRInstructions.binary(Opcode.SUBR, IRDataType.POINTER, 1, 2))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-subr-p-word-src"),
            listOf(chunk)
        )
        lines.any { it == "moveq  #0,d0" } shouldBe true
        lines.any { it == "move.w  p8_regfile+4,d0" } shouldBe true
        lines.any { it == "sub.l  d0,p8_regfile+0" } shouldBe true
        lines.any { it == "move.l  p8_regfile+4,d0" } shouldBe false
    }

    test("does not forward across an intervening instruction") {
        val site = callSite("copper.move", arguments = listOf(hwArg(1, IRDataType.WORD, 10)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-boundary"),
            listOf(
                IRInstructions.load(IRDataType.WORD, 1, 384),
                IRInstructions.simple(Opcode.NOP),
                IRInstructions.call(site)
            )
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  p8_regfile+0,d0" } shouldBe false
    }

    test("retains the register-file store when the value is read via a backward jump") {
        val site = callSite("copper.move", arguments = listOf(hwArg(1, IRDataType.WORD, 10)))
        val loopHead = IRCodeChunk("test.start.loop", null)
        loopHead.instructions.add(
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out"))
        )
        val loopBody = IRCodeChunk(null, null)
        loopBody.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 384))
        loopBody.instructions.add(IRInstructions.call(site))
        loopBody.instructions.add(IRInstructions.jump(codeLabel("test.start.loop")))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-call-forward-backjump"),
            listOf(loopHead, loopBody)
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe true
    }

    test("suppresses the register-file stores inside a loop when the registers are only used by the call") {
        val site = callSite("copper.move", arguments = listOf(hwArg(1, IRDataType.WORD, 10)))
        val loopHead = IRCodeChunk("test.start.loop", null)
        loopHead.instructions.add(IRInstructions.load(IRDataType.WORD, 2, 1))
        val loopBody = IRCodeChunk(null, null)
        loopBody.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 384))
        loopBody.instructions.add(IRInstructions.call(site))
        loopBody.instructions.add(IRInstructions.jump(codeLabel("test.start.loop")))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-call-forward-loop-singleuse"),
            listOf(loopHead, loopBody)
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe false
    }

    test("retains the register-file store when the value is read by a later inline asm chunk") {
        val site = callSite("copper.move", arguments = listOf(hwArg(1, IRDataType.WORD, 10)))
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 384))
        chunk.instructions.add(IRInstructions.call(site))
        val inlineAsm = IRInlineAsmChunk(null, "move.w  p8_regfile+0,d1", false, null)
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-call-forward-inlineasm"),
            listOf(chunk, inlineAsm)
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe true
    }

    test("still suppresses the register-file store when an inline asm chunk does not read the register file") {
        val site = callSite("copper.move", arguments = listOf(hwArg(1, IRDataType.WORD, 10)))
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 384))
        chunk.instructions.add(IRInstructions.call(site))
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
        chunk1.instructions.add(IRInstructions.load(IRDataType.BYTE, 1, 42))
        chunk1.instructions.add(IRInstructions.jump(codeLabel("test.end")))
        val chunk2 = IRCodeChunk("test.end", null)
        chunk2.instructions.add(IRInstructions.returnVoid())
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-jmp-to-next-label"),
            listOf(chunk1, chunk2)
        )

        lines.any { it.startsWith("bra  test.end") } shouldBe false
        lines.any { it == "rts" } shouldBe true
    }

    test("removes jmp to immediately following label but keeps label on jmp line") {
        val chunk1 = IRCodeChunk(null, null)
        chunk1.instructions.add(IRInstructions.load(IRDataType.BYTE, 1, 42))
        val chunk2 = IRCodeChunk("test.mid", null)
        chunk2.instructions.add(IRInstructions.jump(codeLabel("test.end")))
        val chunk3 = IRCodeChunk("test.end", null)
        chunk3.instructions.add(IRInstructions.returnVoid())
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
        chunk1.instructions.add(IRInstructions.call(callSite("test.target")))
        chunk1.instructions.add(IRInstructions.returnVoid())
        val chunk2 = IRCodeChunk("test.target", null)
        chunk2.instructions.add(IRInstructions.returnVoid())
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
        chunk1.instructions.add(IRInstructions.call(callSite("test.target")))
        chunk1.instructions.add(IRInstructions.returnVoid())
        val chunk2 = IRCodeChunk(null, null)
        chunk2.instructions.add(IRInstructions.load(IRDataType.BYTE, 1, 99))
        val chunk3 = IRCodeChunk("test.target", null)
        chunk3.instructions.add(IRInstructions.returnVoid())
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
        chunk1.instructions.add(IRInstructions.load(IRDataType.BYTE, 1, 42))
        val chunk2 = IRCodeChunk("test.caller", null)
        chunk2.instructions.add(IRInstructions.call(callSite("test.target")))
        chunk2.instructions.add(IRInstructions.returnVoid())
        val chunk3 = IRCodeChunk(null, null)
        chunk3.instructions.add(IRInstructions.load(IRDataType.BYTE, 2, 99))
        val chunk4 = IRCodeChunk("test.target", null)
        chunk4.instructions.add(IRInstructions.returnVoid())
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
        val site = callSite("test.asmsub", results = listOf(hwResult(0, IRDataType.POINTER, 18)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-single-return-a0"),
            listOf(IRInstructions.call(site))
        )
        // Should emit a store from a0 to virtual register
        lines.any { it.contains("move.l") && it.contains("a0") && it.contains("p8_regfile") } shouldBe true
    }

    test("single-return expression call with @FP0 (slot 25) emits store instruction") {
        // Bug fix: single-return expression calls with float register returns
        // should emit the store instruction.
        val site = callSite("test.asmsub", results = listOf(hwResult(0, IRDataType.FLOAT, 25)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-single-return-fp0"),
            listOf(IRInstructions.call(site))
        )
        // Should emit fmove.s from fp0 to virtual register
        lines.any { it.contains("fmove.s") && it.contains("fp0") } shouldBe true
    }

    test("multi-assign with @D0 + @Pz does not emit move between jsr and branch") {
        // Bug fix: in multi-assign context, slot returns should be skipped because
        // the IR generates LOADHR for them. Emitting move here would clobber CPU flags
        // before the IR's branch pattern can read them.
        val site = callSite(
            "test.asmsub",
            results = listOf(hwResult(0, IRDataType.BYTE, 10), flagResult(1, Statusflag.Pz))
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-multi-assign-d0-pz"),
            listOf(IRInstructions.call(site))
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
        // For amiga CALLFAR the target is an automatic library call: library number 1 (exec.library)
        // and a negative LVO offset into its jump table.
        val callfarSite = CallSite(
            CallTarget.AmigaLibrary(library = 1, lvo = -30, name = "test.libfunc"),
            results = listOf(hwResult(0, IRDataType.POINTER, 18), flagResult(1, Statusflag.Pz))
        )
        val callfarInsn = IRInstructions.call(Opcode.CALLFAR, callfarSite)

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

    test("dbra via IRLoopChunk for repeat word loop 1000") {
        val body = IRCodeChunk(null, null).also { it += IRInstructions.memoryOp(Opcode.INCM, IRDataType.WORD, IRMemory.direct("p8b_main.p8v_sum")) }
        val loop = IRLoopChunk("p8_label_gen_2", 1000, mutableListOf(body))
        val lines = generateAsmChunks(tempRoot, listOf(loop))
        lines.any { it.contains("move.w  #999,d7") } shouldBe true
        lines.any { it.contains("dbra  d7,p8_label_gen_2") } shouldBe true
    }

    test("dbra via IRLoopChunk for repeat byte loop 100") {
        val body = IRCodeChunk(null, null).also { it += IRInstructions.memoryOp(Opcode.INCM, IRDataType.WORD, IRMemory.direct("p8b_main.p8v_sum")) }
        val loop = IRLoopChunk("p8_label_gen_1", 100, mutableListOf(body))
        val lines = generateAsmChunks(tempRoot, listOf(loop))
        lines.any { it.contains("move.w  #99,d7") } shouldBe true
        lines.any { it.contains("dbra  d7,p8_label_gen_1") } shouldBe true
    }

    test("dbra via IRLoopChunk for repeat 256 special case") {
        val body = IRCodeChunk(null, null).also { it += IRInstructions.memoryOp(Opcode.INCM, IRDataType.WORD, IRMemory.direct("p8b_main.p8v_sum")) }
        val loop = IRLoopChunk("p8_label_gen_1", 256, mutableListOf(body))
        val lines = generateAsmChunks(tempRoot, listOf(loop))
        lines.any { it.contains("move.w  #255,d7") } shouldBe true
        lines.any { it.contains("dbra  d7,p8_label_gen_1") } shouldBe true
    }

    test("dbra peephole does not trigger when body uses d7") {
        val lines = mutableListOf(
            "    move.w  #100,p8_regfile+4",
            "p8_label_gen_2:",
            "    move.w  d7,p8b_main.p8v_sum",
            "    subq.w  #1,p8_regfile+4",
            "    bne  p8_label_gen_2"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("dbra") } shouldBe false
    }

    test("dbra peephole does not trigger when body contains bsr call") {
        val lines = mutableListOf(
            "    move.w  #100,p8_regfile+4",
            "p8_label_gen_2:",
            "    bsr  some_sub",
            "    subq.w  #1,p8_regfile+4",
            "    bne  p8_label_gen_2"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("dbra") } shouldBe false
    }

    test("dbra via IRLoopChunk preserves label on init line") {
        val body = IRCodeChunk(null, null).also { it += IRInstructions.memoryOp(Opcode.INCM, IRDataType.WORD, IRMemory.direct("p8b_main.p8v_sum")) }
        val outerChunk = IRCodeChunk("outer", null)
        val loop = IRLoopChunk("p8_label_gen_2", 100, mutableListOf(body))
        val lines = generateAsmChunks(tempRoot, listOf(outerChunk, loop))
        lines.any { it.contains("outer:") } shouldBe true
        lines.any { it.contains("dbra  d7,p8_label_gen_2") } shouldBe true
        lines.any { it.contains("move.w  #99,d7") } shouldBe true
    }

    test("dbra via IRLoopChunk preserves label on loop") {
        val body = IRCodeChunk(null, null).also { it += IRInstructions.memoryOp(Opcode.INCM, IRDataType.WORD, IRMemory.direct("p8b_main.p8v_sum")) }
        val loop = IRLoopChunk("p8_label_gen_2", 100, mutableListOf(body))
        val lines = generateAsmChunks(tempRoot, listOf(loop))
        lines.any { it.contains("p8_label_gen_2:") } shouldBe true
        lines.any { it.contains("dbra  d7,p8_label_gen_2") } shouldBe true
    }

    test("IRLoopChunk saves d7 around sqrt helper call") {
        val body = IRCodeChunk(null, null).also {
            it += IRInstructions.binary(Opcode.SQRT, IRDataType.BYTE, 1, 2)
        }
        val loop = IRLoopChunk("p8_label_gen_2", 5, mutableListOf(body))
        val lines = generateAsmChunks(tempRoot, listOf(loop))
        lines.any { it.contains("dbra  d7,p8_label_gen_2") } shouldBe true
        lines.any { it.contains("move.w  d7,-(sp)") } shouldBe true
        lines.any { it.contains("move.w  (sp)+,d7") } shouldBe true
    }

    test("IRLoopChunk saves d7 around long multiply helper on 68000") {
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
        val outputDir = tempRoot.resolve("test-m68k-loop-longmul")
        program.options.outputDir = outputDir
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        val body = IRCodeChunk(null, null).also {
            it += IRInstructions.binaryImmediate(Opcode.MUL, IRDataType.LONG, 1, 3)
        }
        sub.chunks.add(IRLoopChunk("p8_label_gen_2", 5, mutableListOf(body)))
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        outputDir.toFile().deleteRecursively()
        outputDir.toFile().mkdirs()
        AsmGen(program, target).generate()
        val asmFile = outputDir.resolve("test.asm")
        val lines = asmFile.readText().lines().map { it.trim() }
        lines.any { it.contains("bsr  p8_umult32") } shouldBe true
        lines.any { it.contains("move.w  d7,-(sp)") } shouldBe true
        lines.any { it.contains("move.w  (sp)+,d7") } shouldBe true
    }

    test("dbra peephole does not trigger when body reads the counter slot") {
        val lines = mutableListOf(
            "    move.w  #100,p8_regfile+4",
            "p8_label_gen_2:",
            "    move.w  p8_regfile+4,d0",
            "    subq.w  #1,p8_regfile+4",
            "    bne  p8_label_gen_2"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("dbra") } shouldBe false
        lines.any { it.contains("move.w  p8_regfile+4,d0") } shouldBe true
    }

    // === on..goto / on..call dispatch fusion (LOADX+JUMPI/CALLI) ===

    test("fuses LOADX+JUMPI on qemu68k (68020 memory-indirect jmp)") {
        val lines = mutableListOf(
            "    lea  mytable,a0",
            "    move.l  (a0,d0.w),d0",
            "    move.l  d0,p8_regfile+6",
            "    jmp  ([p8_regfile+6])"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("move.l  (a0,d0.w), a0") } shouldBe true
        lines.any { it.contains("move.l  d0,p8_regfile+6") } shouldBe false
        lines.any { it.contains("jmp  ([p8_regfile+6])") } shouldBe false
        lines.any { it.trim() == "jmp  (a0)" } shouldBe true
    }

    test("fuses LOADX+JUMPI on amiga500 (68000 movea reload)") {
        val lines = mutableListOf(
            "    lea  mytable,a0",
            "    move.l  (a0,d0.w),d0",
            "    move.l  d0,p8_regfile+6",
            "    movea.l  p8_regfile+6,a0",
            "    jmp  (a0)"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("move.l  (a0,d0.w), a0") } shouldBe true
        lines.any { it.contains("move.l  d0,p8_regfile+6") } shouldBe false
        lines.any { it.contains("movea.l  p8_regfile+6,a0") } shouldBe false
        lines.count { it.trim() == "jmp  (a0)" } shouldBe 1
    }

    test("fuses LOADX+CALLI on qemu68k (68020 memory-indirect jsr)") {
        val lines = mutableListOf(
            "    lea  mytable,a0",
            "    move.l  (a0,d0.w),d0",
            "    move.l  d0,p8_regfile+6",
            "    jsr  ([p8_regfile+6])"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("move.l  (a0,d0.w), a0") } shouldBe true
        lines.any { it.contains("move.l  d0,p8_regfile+6") } shouldBe false
        lines.any { it.contains("jsr  ([p8_regfile+6])") } shouldBe false
        lines.any { it.trim() == "jsr  (a0)" } shouldBe true
    }

    test("fuses LOADX+CALLI on amiga500 (68000 movea reload)") {
        val lines = mutableListOf(
            "    lea  mytable,a0",
            "    move.l  (a0,d0.w),d0",
            "    move.l  d0,p8_regfile+6",
            "    movea.l  p8_regfile+6,a0",
            "    jsr  (a0)"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("move.l  (a0,d0.w), a0") } shouldBe true
        lines.any { it.contains("move.l  d0,p8_regfile+6") } shouldBe false
        lines.any { it.contains("movea.l  p8_regfile+6,a0") } shouldBe false
        lines.any { it.trim() == "jsr  (a0)" } shouldBe true
    }

    test("does not fuse LOADX+JUMPI when spill slot is a jump target") {
        val lines = mutableListOf(
            "    lea  mytable,a0",
            "    move.l  (a0,d0.w),d0",
            "target:  move.l  d0,p8_regfile+6",
            "    jmp  ([p8_regfile+6])"
        )
        optimizeAssembly(lines)
        // spill has label, must be kept
        lines.any { it.contains("move.l  d0,p8_regfile+6") } shouldBe true
        lines.any { it.contains("move.l  (a0,d0.w), a0") } shouldBe false
    }

    // === D0 peephole cache ===

    test("skips redundant d0 load when the value is already cached") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-d0-cache-skip"),
            listOf(
                IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 3, 1),
                IRInstructions.memoryOp(Opcode.ADDM, IRDataType.WORD, IRMemory.direct("p8b_test.p8v_value"), source = 1)
            )
        )

        // ADDR loads r1 into d0 and leaves it there; ADDM reuses the cached r1.
        lines.count { it.startsWith("move.w  p8_regfile+") && it.endsWith(",d0") } shouldBe 1
    }

    test("re-emits d0 load after the cached slot is written directly") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-d0-cache-invalidate-slot"),
            listOf(
                IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 3, 1),
                IRInstructions.unary(Opcode.INC, IRDataType.WORD, 1),
                IRInstructions.memoryOp(Opcode.ADDM, IRDataType.WORD, IRMemory.direct("p8b_test.p8v_value"), source = 1)
            )
        )

        // ADDR caches r1; INC writes r1 directly; ADDM must reload.
        lines.count { it.startsWith("move.w  p8_regfile+") && it.endsWith(",d0") } shouldBe 2
    }

    test("re-emits d0 load after d0 is clobbered by another register load") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-d0-cache-invalidate-d0"),
            listOf(
                IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 3, 1),
                IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 4, 2),
                IRInstructions.memoryOp(Opcode.ADDM, IRDataType.WORD, IRMemory.direct("p8b_test.p8v_value"), source = 1)
            )
        )

        // ADDR caches r1, second ADDR overwrites d0 with r2; ADDM must reload r1.
        lines.count { it.startsWith("move.w  p8_regfile+") && it.endsWith(",d0") } shouldBe 3
    }

    test("d0 cache is invalidated at chunk boundaries") {
        val chunk1 = IRCodeChunk(null, null)
        chunk1.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 3, 1))
        val chunk2 = IRCodeChunk("test.next", null)
        chunk2.instructions.add(IRInstructions.memoryOp(Opcode.ADDM, IRDataType.WORD, IRMemory.direct("p8b_test.p8v_value"), source = 1))
        val lines = generateAsmChunks(
            tempRoot.resolve("test-m68k-d0-cache-boundary"),
            listOf(chunk1, chunk2)
        )

        // ADDR in chunk1 caches r1, but chunk2 is a new basic block.
        lines.count { it.startsWith("move.w  p8_regfile+") && it.endsWith(",d0") } shouldBe 2
    }

    // === centralized MemoryReference lowering (Direct/Indexed/Indirect) ===

    test("indexed memory lowering scales the index in the addressing mode on 68020") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-indexed-68020"),
            listOf(
                IRInstructions.loadMemory(Opcode.LOADX, IRDataType.WORD, 6, IRMemory.indexed("arr", 3, IRDataType.WORD, 2, 4)),
                IRInstructions.storeMemory(Opcode.STOREX, IRDataType.WORD, 8, IRMemory.indexed("arr", 3, IRDataType.WORD, 4)),
                IRInstructions.storeZero(Opcode.STOREZX, IRDataType.BYTE, IRMemory.indexed("arr", 3, IRDataType.WORD, 8))
            )
        )

        lines.any { it == "lea  arr+4,a0" } shouldBe true
        lines.any { it == "move.w  (a0,d0.w*2),d0" } shouldBe true
        // the stored value is staged in d1 because a0/d0 hold the effective address
        lines.any { it == "move.w  d1,(a0,d0.w*4)" } shouldBe true
        lines.any { it == "clr.b  (a0,d0.w*8)" } shouldBe true
    }

    test("indexed memory lowering scales the index by hand on 68000") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-indexed-68000"),
            listOf(
                IRInstructions.loadMemory(Opcode.LOADX, IRDataType.WORD, 6, IRMemory.indexed("arr", 3, IRDataType.WORD, 2)),
                IRInstructions.storeMemory(Opcode.STOREX, IRDataType.WORD, 8, IRMemory.indexed("arr", 3, IRDataType.WORD, 4)),
                IRInstructions.storeZero(Opcode.STOREZX, IRDataType.BYTE, IRMemory.indexed("arr", 3, IRDataType.WORD, 8))
            ),
            target = Amiga500Target()
        )

        lines.any { it == "add.w  d0,d0" } shouldBe true
        lines.any { it == "lsl.w  #2,d0" } shouldBe true
        lines.any { it == "muls.w  #8,d0" } shouldBe true
        lines.count { it.contains("(a0,d0.w*") } shouldBe 0
    }

    test("indirect memory lowering picks displacement addressing or explicit pointer adjustment") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-indirect-displacement"),
            listOf(
                IRInstructions.loadMemory(Opcode.LOADI, IRDataType.WORD, 9, IRMemory.indirect(4, 0)),
                IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, 11, IRMemory.indirect(4, 6)),
                IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, 10, IRMemory.indirect(4, 40000))
            )
        )

        lines.any { it.startsWith("movea.l  p8_regfile") && it.endsWith(",a0") } shouldBe true
        lines.any { it == "move.w  (a0),d0" } shouldBe true
        lines.any { it.endsWith(",(6,a0)") } shouldBe true
        // displacements beyond the signed 16-bit range need an explicit adda
        lines.any { it == "adda.l  #40000,a0" } shouldBe true
    }

    test("float indexed memory lowering uses full width d0.l indexing") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-indexed-float"),
            listOf(
                IRInstructions.loadMemory(Opcode.LOADX, IRDataType.FLOAT, 1, IRMemory.indexed("arr", 2, IRDataType.WORD, 4)),
                IRInstructions.storeMemory(Opcode.STOREX, IRDataType.FLOAT, 1, IRMemory.indexed("arr", 2, IRDataType.WORD, 8))
            )
        )

        lines.any { it == "lsl.l  #2,d0" } shouldBe true
        lines.any { it == "lsl.l  #3,d0" } shouldBe true
        lines.any { it == "fmove.s  (0,a0,d0.l),fp0" } shouldBe true
        lines.any { it == "fmove.s  fp0,(0,a0,d0.l)" } shouldBe true
    }

    test("direct memory lowering resolves symbol displacements and absolute addresses") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-direct-memory"),
            listOf(
                IRInstructions.loadMemory(Opcode.LOADM, IRDataType.WORD, 12, IRMemory.direct("var", 4)),
                IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 12, IRMemory.direct(0xdff180u)),
                IRInstructions.storeImmediate(IRDataType.WORD, 1234, IRMemory.direct("var"))
            )
        )

        lines.any { it == "move.w  var+4,p8_regfile+0" } shouldBe true
        lines.any { it == "move.w  p8_regfile+0,\$00dff180" } shouldBe true
        lines.any { it == "move.w  #1234,var" } shouldBe true
    }

    // === extb.l gate robustification ===

    test("M68020 sign-extends byte to long with extb.l") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-extbl-qemu"),
            listOf(IRInstructions.binary(Opcode.EXTLS, IRDataType.BYTE, 1, 2))
        )

        lines.count { it == "extb.l  d0" } shouldBe 1
        lines.count { it == "ext.w  d0" } shouldBe 0
        lines.count { it == "ext.l  d0" } shouldBe 0
    }

    test("M68000 sign-extends byte to long with ext.w plus ext.l") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-extbl-amiga"),
            listOf(IRInstructions.binary(Opcode.EXTLS, IRDataType.BYTE, 1, 2)),
            target = Amiga500Target()
        )

        lines.count { it == "extb.l  d0" } shouldBe 0
        lines.count { it == "ext.w  d0" } shouldBe 1
        lines.count { it == "ext.l  d0" } shouldBe 1
    }
})
