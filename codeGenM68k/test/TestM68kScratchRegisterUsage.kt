package prog8tests.codegen.m68k

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.code.core.CompilationOptions
import prog8.code.core.OutputType
import prog8.code.core.Position
import prog8.code.core.ZeropageType
import prog8.code.target.Amiga500Target
import prog8.code.target.Qemu68kTarget
import prog8.codegen.m68k.AsmGen
import prog8.intermediate.*
import prog8tests.helpers.DummyStringEncoder
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Tests that compiler-generated helper sequences use only the standard scratch
 * set (D0/D1/A0/A1/FP0/FP1) or explicitly save/restore any additional registers.
 *
 * This guards against accidentally treating D2-D6/A2-A4/FP2-FP7 as scratch once
 * the Stage 2 register allocator hands out virtual registers from those pools.
 */
class TestM68kScratchRegisterUsage : FunSpec({

    val tempRoot = tempdir().toPath()

    fun generateAsm(
        outputDir: Path,
        instructions: List<IRInstruction>,
        regAllocation: Map<VirtualRegister, String> = emptyMap(),
        floats: Boolean = false
    ): List<String> {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW)
            .zeropage(ZeropageType.FLOATSAFE)
            .floats(floats)
            .compilerVersion("test")
            .memtopAddress(0xffffu)
            .optimize(true)
            .build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        program.options.outputDir = outputDir
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.addAll(instructions)
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val output = outputDir.toFile()
        output.deleteRecursively()
        output.mkdirs()
        val asmGen = AsmGen(program, Qemu68kTarget())
        asmGen.regAllocation.putAll(regAllocation)
        asmGen.generate()
        val asmFile = outputDir.resolve("test.asm")
        check(asmFile.exists()) { "Assembly file not written: $asmFile" }
        return asmFile.readText().lines().map { it.trim() }
    }

    test("byte multiply uses standard scratch, never d2-d4") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-mul-byte"),
            listOf(
                IRInstructions.binary(Opcode.MULR, IRDataType.BYTE, 1, 2),
                IRInstructions.binary(Opcode.MULSR, IRDataType.BYTE, 3, 4)
            )
        )

        lines.none { " d2" in it || "d2," in it || it.endsWith("d2") } shouldBe true
        lines.none { " d3" in it || "d3," in it || it.endsWith("d3") } shouldBe true
        lines.none { " d4" in it || "d4," in it || it.endsWith("d4") } shouldBe true
        lines.count { "mulu.w  d1,d0" in it } shouldBe 1
        lines.count { "muls.w  d1,d0" in it } shouldBe 1
    }

    test("long modulo by immediate uses standard scratch, never d2-d4") {
        // 68020+ target exercises the immediate-divisor path in emitModOp that previously used d2.
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-mod-long-imm"),
            listOf(
                IRInstructions.binaryImmediate(Opcode.MOD, IRDataType.LONG, 1, 7),
                IRInstructions.binaryImmediate(Opcode.MODS, IRDataType.LONG, 3, -7)
            )
        )

        lines.none { " d2" in it || "d2," in it || it.endsWith("d2") } shouldBe true
        lines.none { " d3" in it || "d3," in it || it.endsWith("d3") } shouldBe true
        lines.none { " d4" in it || "d4," in it || it.endsWith("d4") } shouldBe true
        lines.count { "divul.l  d1,d1:d0" in it } shouldBe 1
        lines.count { "divsl.l  d1,d1:d0" in it } shouldBe 1
    }

    test("split word array contains saves and restores d2-d4 with movem") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-splitwordarray-contains"),
            listOf(
                IRInstructions.syscall(
                    IMSyscall.SPLIT_WORDARRAY_CONTAINS.number,
                    arguments = listOf(
                        Calls.argument(1, IRDataType.WORD),
                        Calls.argument(2, IRDataType.LONG),
                        Calls.argument(3, IRDataType.WORD)
                    ),
                    results = listOf(Calls.result(4, IRDataType.BYTE))
                )
            )
        )

        lines.any { it == "movem.l  d2-d4,-(sp)" } shouldBe true
        lines.any { it == "movem.l  (sp)+,d2-d4" } shouldBe true
        // d2-d4 are only used inside the save/restore window
        val saveIndex = lines.indexOf("movem.l  d2-d4,-(sp)")
        val restoreIndex = lines.indexOf("movem.l  (sp)+,d2-d4")
        saveIndex shouldBeGreaterThan -1
        restoreIndex shouldBeGreaterThan saveIndex
        val bodyLines = lines.subList(saveIndex + 1, restoreIndex)
        bodyLines.any { "d2" in it } shouldBe true
        bodyLines.any { "d3" in it } shouldBe true
        bodyLines.any { "d4" in it } shouldBe true
    }

    test("float indexed load uses allocated data register with hardware scale") {
        // 68020+ target; index register r2 allocated to d3.
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-float-indexed-scale"),
            listOf(
                IRInstructions.loadMemory(
                    Opcode.LOADX,
                    IRDataType.FLOAT,
                    1,
                    IRMemory.indexed("array", 2, IRDataType.WORD, scale = 4)
                )
            ),
            regAllocation = mapOf(VirtualRegister.int(2) to "d3"),
            floats = true
        )

        lines.any { it == "fmove.s  (0,a0,d3.l*4),fp0" } shouldBe true
        lines.none { it.startsWith("lsl.l") || it.startsWith("add.l") } shouldBe true
    }

    test("float indexed load falls back to scratch when index is not allocated") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-float-indexed-scratch"),
            listOf(
                IRInstructions.loadMemory(
                    Opcode.LOADX,
                    IRDataType.FLOAT,
                    1,
                    IRMemory.indexed("array", 2, IRDataType.WORD, scale = 4)
                )
            ),
            floats = true
        )

        lines.any { it == "fmove.s  (0,a0,d0.l),fp0" } shouldBe true
        lines.any { it == "lsl.l  #2,d0" } shouldBe true
    }

    test("does not forward immediates for register-allocated call arguments") {
        // Step 9: a register-resident argument must be marshalled from its allocated
        // register, not via immediate forwarding into the hardware slot.
        val site = CallSite(
            CallTarget.Direct(CodeReference.Label("copper.move", 0)),
            arguments = listOf(
                Calls.argument(1, IRDataType.WORD, CallLocation.HardwareRegister(CallingConventionSlot(10)))
            )
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-no-forward-allocated"),
            listOf(
                IRInstructions.load(IRDataType.WORD, 1, 384),
                IRInstructions.call(site)
            ),
            regAllocation = mapOf(VirtualRegister.int(1) to "d2"),
        )

        lines.any { it == "move.w  #384,d2" } shouldBe true
        lines.any { it == "move.w  d2,d0" } shouldBe true
        lines.any { it == "move.w  #384,d0" } shouldBe false
    }

    test("still forwards immediates for spilled call arguments under allocation") {
        // Step 9: a spilled (non-allocated) argument keeps the immediate-forwarding path,
        // including dead-store suppression, even with allocation enabled.
        val site = CallSite(
            CallTarget.Direct(CodeReference.Label("copper.move", 0)),
            arguments = listOf(
                Calls.argument(1, IRDataType.WORD, CallLocation.HardwareRegister(CallingConventionSlot(10)))
            )
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-forward-spilled"),
            listOf(
                IRInstructions.load(IRDataType.WORD, 1, 384),
                IRInstructions.call(site)
            ),
        )

        lines.any { it == "move.w  #384,d0" } shouldBe true
        lines.any { it == "move.w  #384,p8_regfile+0" } shouldBe false
        lines.any { it == "move.w  p8_regfile+0,d0" } shouldBe false
    }

    test("caller moves default int return from d0 to the allocated register") {
        // Step 7: single scalar result arrives in d0; the caller moves it out.
        val site = CallSite(
            CallTarget.Direct(CodeReference.Label("p8_other", 0)),
            results = listOf(Calls.result(4, IRDataType.WORD))
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-return-int"),
            listOf(IRInstructions.call(site)),
            regAllocation = mapOf(VirtualRegister.int(4) to "d2"),
        )

        lines.any { it.startsWith("bsr") } shouldBe true
        lines.any { it == "move.w  d0,d2" } shouldBe true
    }

    test("callee moves allocated source to d0 on return") {
        // Step 7: the callee produces its single scalar result in d0 before rts.
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-return-callee"),
            listOf(IRInstructions.returnRegister(IRDataType.WORD, 1)),
            regAllocation = mapOf(VirtualRegister.int(1) to "d3"),
        )

        lines.any { it == "move.w  d3,d0" } shouldBe true
        lines.any { it == "rts" } shouldBe true
    }

    test("caller moves default float return from fp0 to the allocated register") {
        // Step 7: single float result arrives in fp0; the caller moves it out.
        val site = CallSite(
            CallTarget.Direct(CodeReference.Label("p8_other", 0)),
            results = listOf(Calls.result(1, IRDataType.FLOAT))
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-return-float"),
            listOf(IRInstructions.call(site)),
            regAllocation = mapOf(VirtualRegister.float(1) to "fp2"),
            floats = true
        )

        lines.any { it.startsWith("bsr") } shouldBe true
        lines.any { it == "fmove  fp0,fp2" } shouldBe true
    }

    test("slot result already in its allocated register is elided") {
        // Step 7, section 2.8: the vreg already lives in its slot register: no move.
        val slotD2 = CallLocation.HardwareRegister(CallingConventionSlot(12))
        val site = CallSite(
            CallTarget.Direct(CodeReference.Label("p8_other", 0)),
            results = listOf(Calls.result(5, IRDataType.WORD, slotD2))
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-return-slot-elided"),
            listOf(IRInstructions.call(site)),
            regAllocation = mapOf(VirtualRegister.int(5) to "d2"),
        )

        lines.any { it.startsWith("bsr") } shouldBe true
        lines.none { it == "move.w  d2,d2" } shouldBe true
    }

    test("LOADHR into its allocated slot register is elided") {
        // Step 7, section 2.8: LOADHR r5,D2 where r5 is allocated to d2 is a no-op.
        val slotD2 = CallingConventionSlot(12)
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-loadhr-elided"),
            listOf(IRInstructions.hardwareLoad(IRDataType.WORD, 5, slotD2)),
            regAllocation = mapOf(VirtualRegister.int(5) to "d2"),
        )

        lines.none { it.startsWith("move.w") } shouldBe true
    }

    test("STOREHR from its allocated slot register is elided, other regs still move") {
        // Step 7, section 2.8: STOREHR r5,D2 where r5 is allocated to d2 is a no-op,
        // while r6 in d3 still emits the register-to-register move.
        val slotD2 = CallingConventionSlot(12)
        val elided = generateAsm(
            tempRoot.resolve("test-m68k-storehr-elided"),
            listOf(IRInstructions.hardwareStore(IRDataType.WORD, 5, slotD2)),
            regAllocation = mapOf(VirtualRegister.int(5) to "d2"),
        )
        elided.none { it.startsWith("move.w") } shouldBe true

        val moved = generateAsm(
            tempRoot.resolve("test-m68k-storehr-moved"),
            listOf(IRInstructions.hardwareStore(IRDataType.WORD, 6, slotD2)),
            regAllocation = mapOf(VirtualRegister.int(6) to "d3"),
        )
        moved.any { it == "move.w  d3,d2" } shouldBe true
    }

    test("float LOADHR into its allocated slot register is elided") {
        // Step 7, section 2.8: float LOADHR fr1,FP2 where fr1 lives in fp2 is a no-op,
        // while fr3 in fp3 still emits the FPU round trip.
        val slotFp2 = CallingConventionSlot(27)
        val elided = generateAsm(
            tempRoot.resolve("test-m68k-floadhr-elided"),
            listOf(IRInstructions.hardwareLoad(IRDataType.FLOAT, 1, slotFp2)),
            regAllocation = mapOf(VirtualRegister.float(1) to "fp2"),
            floats = true
        )
        elided.none { it.startsWith("fmove") } shouldBe true

        val moved = generateAsm(
            tempRoot.resolve("test-m68k-floadhr-moved"),
            listOf(IRInstructions.hardwareLoad(IRDataType.FLOAT, 3, slotFp2)),
            regAllocation = mapOf(VirtualRegister.float(3) to "fp3"),
            floats = true
        )
        moved.any { it == "fmove  fp2,fp3" } shouldBe true
        moved.none { "fp0" in it && it.startsWith("fmove") } shouldBe true
    }

    test("bit op on address-allocated vreg stages through d0") {
        // bset/bclr accept Dn or memory, never An: an address-resident value
        // must round-trip through d0 at full width, never touch the regfile.
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-bitop-an"),
            listOf(IRInstructions.binaryImmediate(Opcode.BITCLR, IRDataType.LONG, 5, 0)),
            regAllocation = mapOf(VirtualRegister.int(5) to "a2"),
        )
        lines.any { it == "move.l  a2,d0" } shouldBe true
        lines.any { it == "bclr  #0,d0" } shouldBe true
        lines.any { it == "move.l  d0,a2" } shouldBe true
        lines.none { "p8_regfile+" in it } shouldBe true
    }

    test("bit op on data-allocated vreg stays a single instruction") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-bitop-dn"),
            listOf(IRInstructions.binaryImmediate(Opcode.BITSET, IRDataType.LONG, 5, 3)),
            regAllocation = mapOf(VirtualRegister.int(5) to "d3"),
        )

        lines.any { it == "bset  #3,d3" } shouldBe true
        lines.none { "p8_regfile+" in it } shouldBe true
    }

    test("direct reg-reg arithmetic skips the d0 bounce") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-direct-arith"),
            listOf(
                IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 2, 1),
                IRInstructions.binary(Opcode.SUBR, IRDataType.WORD, 4, 3),
                IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 6, 5),
                IRInstruction(
                    Opcode.CMP, IRDataType.WORD,
                    srcA = RegisterOperand(VirtualRegister.int(8), IRDataType.WORD, OperandRole.LEFT, OperandDirection.USE),
                    srcB = RegisterOperand(VirtualRegister.int(7), IRDataType.WORD, OperandRole.RIGHT, OperandDirection.USE)
                )
            ),
            regAllocation = mapOf(
                VirtualRegister.int(1) to "d2", VirtualRegister.int(2) to "d3",
                VirtualRegister.int(3) to "d4", VirtualRegister.int(4) to "d5",
                VirtualRegister.int(5) to "d2", VirtualRegister.int(6) to "d3",
                VirtualRegister.int(7) to "d4", VirtualRegister.int(8) to "d5"
            ),
        )

        lines.any { it == "add.w  d2,d3" } shouldBe true
        lines.any { it == "sub.w  d4,d5" } shouldBe true
        lines.any { it == "mulu.w  d2,d3" } shouldBe true
        lines.any { it == "cmp.w  d4,d5" } shouldBe true
        lines.none { it == "move.w  d2,d0" } shouldBe true
        lines.none { it == "move.w  d0,d3" } shouldBe true
    }

    test("direct shift and extension skip the d0 bounce") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-direct-shift"),
            listOf(
                IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, 2, 1),
                IRInstructions.binary(Opcode.EXT, IRDataType.LONG, 4, 3)
            ),
            regAllocation = mapOf(
                VirtualRegister.int(1) to "d2", VirtualRegister.int(2) to "d3",
                VirtualRegister.int(3) to "d4", VirtualRegister.int(4) to "d5"
            ),
        )

        lines.any { it == "move.b  d2,d3" } shouldBe true
        lines.any { it == "move.l  d4,d5" } shouldBe true
        lines.none { it == "move.b  d2,d0" || it == "move.b  d0,d3" } shouldBe true
        lines.none { it == "move.l  d4,d0" || it == "move.l  d0,d5" } shouldBe true
    }

    test("direct float binary op computes in the destination register") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-direct-float"),
            listOf(IRInstructions.binary(Opcode.ADDR, IRDataType.FLOAT, 2, 1)),
            regAllocation = mapOf(
                VirtualRegister.float(1) to "fp2", VirtualRegister.float(2) to "fp3"
            ),
            floats = true
        )

        lines.any { it == "fadd  fp2,fp3" } shouldBe true
        lines.none { "fp0" in it } shouldBe true
    }

    test("spilled operands keep the legacy d0 path when allocation is enabled") {
        // Same instructions with an empty map: output must not use direct forms.
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-direct-spilled"),
            listOf(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 2, 1)),
        )

        lines.any { it == "move.w  p8_regfile+0,d0" || it.startsWith("move.w  p8_regfile") } shouldBe true
        lines.none { it == "add.w  d2,d3" } shouldBe true
    }

    test("address-resident source keeps the legacy d0 path") {
        // add.w An,Dn is illegal: an address-allocated source must bounce via d0.
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-direct-an-src"),
            listOf(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 2, 1)),
            regAllocation = mapOf(
                VirtualRegister.int(1) to "a2", VirtualRegister.int(2) to "d3"
            ),
        )

        lines.any { it == "move.w  a2,d0" } shouldBe true
        lines.any { it == "add.w  d0,d3" } shouldBe true
    }

    test("float moves use a source-correct size suffix") {
        // 68881 rejects fmove.s with an FP source ("illegal opcode extension"):
        // FP-to-FP moves use the bare form, memory sources use .s.
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-float-suffix"),
            listOf(
                IRInstructions.binary(Opcode.ADDR, IRDataType.FLOAT, 2, 1),
                IRInstructions.storeMemory(Opcode.STOREM, IRDataType.FLOAT, 2, IRMemory.direct("p8b_test.p8v_out")),
                IRInstructions.loadMemory(Opcode.LOADM, IRDataType.FLOAT, 3, IRMemory.direct("p8b_test.p8v_in"))
            ),
            regAllocation = mapOf(
                VirtualRegister.float(1) to "fp2", VirtualRegister.float(2) to "fp3",
                VirtualRegister.float(3) to "fp4"
            ),
            floats = true
        )

        lines.any { it == "fadd  fp2,fp3" } shouldBe true
        lines.any { it == "fmove.s  fp3,p8b_test.p8v_out" } shouldBe true
        lines.any { it == "fmove.s  p8b_test.p8v_in,fp4" } shouldBe true
        // no FP-to-FP .s move anywhere (the assembler rejects those)
        val fpToFpSized = Regex("fmovec?[.]s +fp[0-9]+,fp[0-9]+")
        val arithFpSized = Regex("f(add|sub|mul|div|sqrt|cmp)[.]s +fp[0-9]+,")
        lines.none { fpToFpSized.containsMatchIn(it) } shouldBe true
        lines.none { arithFpSized.containsMatchIn(it) } shouldBe true
    }

    test("long multiply helper saves allocated registers around the call") {
        // On 68000 (amiga500) LONG multiply bsrs to a helper with no IR call
        // edge (p8_umult32 clobbers D2-D7); allocated D2-D6 would be corrupted
        // without the movem guard, and the allocator cannot protect them.
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
        program.options.outputDir = tempRoot.resolve("test-m68k-longmath-save")
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.LONG, 1, 2))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val output = program.options.outputDir.toFile()
        output.deleteRecursively()
        output.mkdirs()
        val asmGen = AsmGen(program, target)
        asmGen.regAllocation.putAll(mapOf(VirtualRegister.int(1) to "d2", VirtualRegister.int(2) to "d3"))
        asmGen.generate()
        val asmFile = program.options.outputDir.resolve("test.asm")
        check(asmFile.exists()) { "Assembly file not written: $asmFile" }
        val asmLines = asmFile.readText().lines().map { it.trim() }

        val save = asmLines.indexOf("movem.l  d2-d7,-(sp)")
        val bsr = asmLines.indexOfFirst { it == "bsr  p8_umult32" }
        val restore = asmLines.indexOf("movem.l  (sp)+,d2-d7")
        (save >= 0) shouldBe true
        (bsr > save) shouldBe true
        (restore > bsr) shouldBe true
    }

    test("long divmod helper saves allocated registers around the call") {
        // The 68000 DIVMODR/SDIVMODR path bsrs to a helper that clobbers D2-D7, same as the multiply path.
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
        program.options.outputDir = tempRoot.resolve("test-m68k-longdivmod-save")
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.divmodRegister(Opcode.SDIVMODR, IRDataType.LONG, 1, 2))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val output = program.options.outputDir.toFile()
        output.deleteRecursively()
        output.mkdirs()
        val asmGen = AsmGen(program, target)
        asmGen.regAllocation.putAll(mapOf(VirtualRegister.int(1) to "d2", VirtualRegister.int(2) to "d3"))
        asmGen.generate()
        val asmFile = program.options.outputDir.resolve("test.asm")
        check(asmFile.exists()) { "Assembly file not written: $asmFile" }
        val asmLines = asmFile.readText().lines().map { it.trim() }

        val save = asmLines.indexOf("movem.l  d2-d7,-(sp)")
        val bsr = asmLines.indexOfFirst { it == "bsr  p8_sdivmod32" }
        val restore = asmLines.indexOf("movem.l  (sp)+,d2-d7")
        (save >= 0) shouldBe true
        (bsr > save) shouldBe true
        (restore > bsr) shouldBe true
    }

    test("spilled default return goes to memory") {
        // Step 7: a spilled destination resolves to its regfile slot, not a register.
        val site = CallSite(
            CallTarget.Direct(CodeReference.Label("p8_other", 0)),
            results = listOf(Calls.result(4, IRDataType.WORD))
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-return-spilled"),
            listOf(IRInstructions.call(site)),
        )

        lines.any { it.startsWith("bsr") } shouldBe true
        lines.any { it.startsWith("move.w  d0,p8_regfile") } shouldBe true
    }

    test("multi-value call emits no return moves") {
        // Step 7: multi-value returns stay memory-based; the call emits no d0 moves.
        val site = CallSite(
            CallTarget.Direct(CodeReference.Label("p8_other", 0)),
            results = listOf(
                Calls.result(4, IRDataType.WORD),
                Calls.result(5, IRDataType.WORD)
            )
        )
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-return-multi"),
            listOf(IRInstructions.call(site)),
            regAllocation = mapOf(
                VirtualRegister.int(4) to "d2",
                VirtualRegister.int(5) to "d3"
            ),
        )

        lines.any { it.startsWith("bsr") } shouldBe true
        lines.none { it.startsWith("move.") && "d0," in it } shouldBe true
    }
})
