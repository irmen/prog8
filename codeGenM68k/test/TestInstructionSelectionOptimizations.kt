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

    fun generateAsmChunks(outputDir: Path, chunks: List<IRCodeChunkBase>, target: ICompilationTarget = Qemu68kTarget(), irComments: Boolean = false): List<String> {
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.FLOATSAFE)
            .floats(false)
            .compilerVersion("test")
            .memtopAddress(0xffffu)
            .optimize(true)
            .irComments(irComments)
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

    fun generateAsm(outputDir: Path, instructions: List<IRInstruction>, target: ICompilationTarget = Qemu68kTarget(), irComments: Boolean = false): List<String> {
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.addAll(instructions)
        return generateAsmChunks(outputDir, listOf(chunk), target, irComments)
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

    // Slot-agnostic assertions: instruction shapes and counts, never a regfile offset.
    val hardwareRegister = Regex("""^(d[0-7]|a[0-7]|fp[0-9]+)$""")

    fun String.firstOperand() = substringAfter("  ", "").substringBefore(',').trim()

    fun String.operandIsHardwareRegister() = firstOperand().matches(hardwareRegister)

    fun List<String>.subroutineLines(): List<String> {
        val start = indexOfFirst { it == "test.start:" }
        val end = indexOfFirst { it == "; End of subroutine: test.start" }
        return subList(start + 1, if (end >= 0) end else size)
    }

    test("uses quick address adjustments and preserves large offsets") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-offsets"),
            listOf(
                IRInstructions.storeZero(Opcode.STOREZI, IRDataType.FLOAT, IRMemory.indirect(1, 1)),
                IRInstructions.storeZero(Opcode.STOREZI, IRDataType.FLOAT, IRMemory.indirect(1, 65535))
            )
        )


        // the pointer value comes from memory: small offsets use addq, large ones
        // adda, and neither may be folded into a lea of the regfile address
        lines.any { it == "addq.l  #1,a0" } shouldBe true
        lines.any { it == "adda.l  #65535,a0" } shouldBe true
        lines.none { it.startsWith("lea  p8_regfile") } shouldBe true
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

        val sub = lines.subroutineLines()
        sub.count { it.startsWith("move.b  ") && it.endsWith(",d0") } shouldBe 1
        sub.count { it.startsWith("cmp.b  ") && !it.operandIsHardwareRegister() && it.endsWith(",d0") } shouldBe 1
        sub.count { it.startsWith("moveq  #0") } shouldBe 0
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

        val sub = lines.subroutineLines()
        sub.count { it.startsWith("mulu.w  ") && !it.operandIsHardwareRegister() } shouldBe 1
        sub.count { it.startsWith("mulu.l  ") && !it.operandIsHardwareRegister() } shouldBe 1
        sub.count { it.startsWith("move.w  ") && it.endsWith(",d1") } shouldBe 0
        sub.count { it.startsWith("move.l  ") && it.endsWith(",d1") } shouldBe 0
    }

    test("indirect jumps and calls use memory-indirect addressing on 68020") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-indirect"),
            listOf(
                IRInstructions.jumpIndirect(1),
                IRInstructions.call(Opcode.CALLI, CallSite(CallTarget.Direct(codeIndirect(2))))
            )
        )

        val sub = lines.subroutineLines()
        sub.count { it.startsWith("jmp  ([") && it.endsWith("])") } shouldBe 1
        sub.count { it.startsWith("jsr  ([") && it.endsWith("])") } shouldBe 1
        sub.count { it.startsWith("move.l  ") && it.endsWith(",a0") } shouldBe 0
    }

    test("indirect jumps and calls load the target into a0 on 68000") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-indirect-68000"),
            listOf(
                IRInstructions.jumpIndirect(1),
                IRInstructions.call(Opcode.CALLI, CallSite(CallTarget.Direct(codeIndirect(2))))
            ),
            Amiga500Target()
        )

        val sub = lines.subroutineLines()
        sub.count { it.startsWith("movea.l  ") && it.endsWith(",a0") } shouldBe 2
        sub.count { it == "jmp  (a0)" } shouldBe 1
        sub.count { it == "jsr  (a0)" } shouldBe 1
        sub.count { it.contains("([") } shouldBe 0
    }

    test("signed word divmod omits redundant zero extension") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-divmod-word"),
            listOf(
                IRInstructions.divmodRegister(Opcode.DIVMODR, IRDataType.WORD, 1, 2),
                IRInstructions.divmodRegister(Opcode.SDIVMODR, IRDataType.WORD, 3, 4)
            )
        )

        val sub = lines.subroutineLines()
        lines.count { it.startsWith("moveq  #0,d0") && "clear upper word" in it } shouldBe 1
        lines.count { it.startsWith("ext.l  d0") } shouldBe 1
        sub.count { it.startsWith("divu.w  ") && !it.operandIsHardwareRegister() } shouldBe 1
        sub.count { it.startsWith("divs.w  ") && !it.operandIsHardwareRegister() } shouldBe 1
    }

    test("immediate call arguments load directly into hardware registers") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-immediate"),
            listOf(
                IRInstructions.call(
                    Opcode.CALL,
                    callSite(
                        "main.foo",
                        arguments = listOf(
                            Calls.argument(ImmediateOperand.Integer(42, IRDataType.BYTE), CallLocation.HardwareRegister(CallingConventionSlot(10))),
                            Calls.argument(ImmediateOperand.SymbolAddress("main.buffer"), CallLocation.HardwareRegister(CallingConventionSlot(18)))
                        )
                    )
                )
            )
        )

        val sub = lines.subroutineLines()
        sub.any { it == "moveq  #42,d0" } shouldBe true
        sub.any { it == "movea.l  #main.buffer,a0" } shouldBe true
        sub.none { it.startsWith("move.b  p8_regfile") } shouldBe true
        sub.none { it.startsWith("movea.l  a2,") } shouldBe true
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
        val sub = lines.subroutineLines()
        sub.count { it.startsWith("move.b  ") && !it.operandIsHardwareRegister() && it.endsWith(",d0") } shouldBe 4
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
        val sub = lines.subroutineLines()
        sub.count { it.startsWith("move.b  ") && !it.operandIsHardwareRegister() && it.endsWith(",d0") } shouldBe 2
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
        val sub = lines.subroutineLines()
        sub.count { it.startsWith("move.w  ") && !it.operandIsHardwareRegister() && it.endsWith(",d0") } shouldBe 1
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
        val sub = lines.subroutineLines()
        sub.count { it.startsWith("move.b  ") && !it.operandIsHardwareRegister() && it.endsWith(",d0") } shouldBe 2
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

        val sub = lines.subroutineLines()
        sub.count { it.startsWith("move.w  #384,") } shouldBe 1
        sub.count { it == "move.w  #384,d0" } shouldBe 1
        sub.count { it.startsWith("move.w  ") && !it.contains("#") && it.endsWith(",d0") } shouldBe 0
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

        val sub = lines.subroutineLines()
        sub.count { it == "move.w  #384,d0" } shouldBe 1
        sub.count { it == "move.w  #1220,d1" } shouldBe 1
        sub.count { it.startsWith("move.w  #384,") } shouldBe 1
        sub.count { it.startsWith("move.w  #1220,") } shouldBe 1
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

        val sub = lines.subroutineLines()
        sub.count { it == "move.w  #384,d0" } shouldBe 1
        sub.count { it.startsWith("move.w  ") && it.endsWith(",d0") } shouldBe 1
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

        val sub = lines.subroutineLines()
        sub.count { it.startsWith("move.b  #42,") } shouldBe 1
        sub.count { it == "move.b  #42,d0" } shouldBe 0
        sub.count { it.startsWith("move.b  ") && it.endsWith(",callee.value") && !it.firstOperand().startsWith("#") } shouldBe 1
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

        val sub = lines.subroutineLines()
        sub.count { it.startsWith("move.w  #384,") && !it.endsWith(",d0") } shouldBe 1
        sub.count { it == "move.w  #384,d0" } shouldBe 1
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

        val sub = lines.subroutineLines()
        sub.count { it == $$"fmovecr  #$32,fp0" } shouldBe 1
        sub.count { it.startsWith("fmove.s  ") } shouldBe 0
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

        val sub = lines.subroutineLines()
        sub.count { it == $$"fmovecr  #$34,fp1" } shouldBe 1
        sub.count { it.startsWith("fmove.s  ") } shouldBe 0
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

        val sub = lines.subroutineLines()
        sub.count { it == "move.w  #384,d0" } shouldBe 1
        sub.count { it.startsWith("move.w  #384,") } shouldBe 1
        sub.count { it == $$"fmovecr  #$32,fp0" } shouldBe 1
        sub.count { it.startsWith("fmove.s  ") } shouldBe 0
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

        val sub = lines.subroutineLines()
        sub.count { it == $$"fmovecr  #$32,fp0" } shouldBe 2
        sub.count { it.startsWith("fmove.s  fp0,") && !it.endsWith("p8b_test.p8v_value") } shouldBe 1
        sub.count { it.startsWith("fmove.s  ") && it.endsWith(",fp0") } shouldBe 1
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

        val sub = lines.subroutineLines()
        sub.count { it == "move.w  #384,d0" } shouldBe 1
        sub.count { it.startsWith("fmove.s  ") && it.endsWith(",fp0") } shouldBe 1
    }

    test("loads pointers into a0 with movea and without a +0 offset") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-pointer-a0"),
            listOf(
                IRInstructions.storeZero(Opcode.STOREZI, IRDataType.WORD, IRMemory.indirect(1, 1))
            )
        )

        val sub = lines.subroutineLines()
        sub.count { it.startsWith("movea.l  ") && it.endsWith(",a0") } shouldBe 1
        sub.count { it.startsWith("move.l  ") } shouldBe 0
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
        val sub = lines.subroutineLines()
        sub.count { it == "moveq  #0,d0" } shouldBe 1
        sub.count { it.startsWith("move.w  ") && !it.operandIsHardwareRegister() && it.endsWith(",d0") } shouldBe 1
        sub.count { it.startsWith("add.l  d0,") } shouldBe 1
        sub.count { it.startsWith("move.l  ") && it.endsWith(",d0") } shouldBe 0
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
        val sub = lines.subroutineLines()
        sub.count { it.startsWith("move.l  ") && it.endsWith(",d0") } shouldBe 1
        sub.count { it.startsWith("add.l  d0,") } shouldBe 1
        sub.count { it == "moveq  #0,d0" } shouldBe 0
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
        val sub = lines.subroutineLines()
        sub.count { it == "moveq  #0,d0" } shouldBe 1
        sub.count { it.startsWith("move.w  ") && !it.operandIsHardwareRegister() && it.endsWith(",d0") } shouldBe 1
        sub.count { it.startsWith("sub.l  d0,") } shouldBe 1
        sub.count { it.startsWith("move.l  ") && it.endsWith(",d0") } shouldBe 0
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

        val sub = lines.subroutineLines()
        sub.count { it == "move.w  #384,d0" } shouldBe 1
        sub.count { it.startsWith("move.w  ") && it.endsWith(",d0") } shouldBe 1
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

        val sub = lines.subroutineLines()
        sub.count { it == "move.w  #384,d0" } shouldBe 1
        sub.count { it.startsWith("move.w  #384,") && !it.endsWith(",d0") } shouldBe 1
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

        val sub = lines.subroutineLines()
        sub.count { it == "move.w  #384,d0" } shouldBe 1
        sub.count { it.startsWith("move.w  #384,") } shouldBe 1
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

        val sub = lines.subroutineLines()
        sub.count { it == "move.w  #384,d0" } shouldBe 1
        sub.count { it.startsWith("move.w  #384,") && !it.endsWith(",d0") } shouldBe 1
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

        val sub = lines.subroutineLines()
        sub.count { it == "move.w  #384,d0" } shouldBe 1
        sub.count { it.startsWith("move.w  #384,") } shouldBe 1
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
            "    move.b  d0, p8b_test.p8v_value",
            "    tst.b   p8b_test.p8v_value",
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
            "    move.w  d0, p8b_test.p8v_other",
            "    tst.w   p8b_test.p8v_other",
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
            "    move.b  d0, p8b_test.p8v_value",
            "    tst.b   p8b_test.p8v_other",
            "    beq     somewhere"
        )
        optimizeAssembly(lines)
        // tst should NOT be removed because it tests a different location
        lines.any { it.contains("tst.b") } shouldBe true
    }

    test("does not remove tst when size differs from move") {
        val lines = mutableListOf(
            "    move.b  d0, p8b_test.p8v_value",
            "    tst.w   p8b_test.p8v_value",
            "    beq     somewhere"
        )
        optimizeAssembly(lines)
        // tst should NOT be removed because sizes differ
        lines.any { it.contains("tst.w") } shouldBe true
    }

    test("removes redundant tst after move to a data register") {
        val lines = mutableListOf(
            "    move.b  d0,d3",
            "    tst.b   d3",
            "    beq     somewhere"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("tst.b") } shouldBe false
        lines.any { it.contains("move.b") } shouldBe true
    }

    test("removes redundant tst after swap, clr and ext") {
        val lines = mutableListOf(
            "    swap    d0",
            "    tst.l   d0",
            "    clr.l   d1",
            "    tst.l   d1",
            "    ext.w   d2",
            "    tst.w   d2",
            "    beq     somewhere"
        )
        optimizeAssembly(lines)
        lines.none { it.contains("tst.") } shouldBe true
    }

    test("does not remove tst after add, sub or cmp") {
        // add/sub/cmp set V/C/X from the computation while tst clears them:
        // only a liveness proof could show no V/C/X reader follows, which this
        // pass does not attempt.
        val lines = mutableListOf(
            "    add.b   d0,d1",
            "    tst.b   d1",
            "    sub.w   d2,d3",
            "    tst.w   d3",
            "    cmp.l   d4,d5",
            "    tst.l   d5",
            "    beq     somewhere"
        )
        optimizeAssembly(lines)
        lines.count { it.contains("tst.") } shouldBe 3
    }

    test("does not remove tst after movea which sets no flags") {
        val lines = mutableListOf(
            "    movea.l  d0,a0",
            "    tst.l   d0",
            "    beq     somewhere"
        )
        optimizeAssembly(lines)
        // the tst here tests d0 (movea wrote a0 and set no flags): keep it.
        // (tst on an address register would be invalid input and is never produced.)
        lines.any { it.contains("tst.l") } shouldBe true
    }

    // === lsl #1 and redundant extension peepholes ===

    test("rewrites lsl #1 on data registers to add dN,dN") {
        val lines = mutableListOf(
            "    lsl.b  #1,d0",
            "    lsl.w  #1,d1",
            "    lsl.l  #1,d2",
            "    lsl.l  #2,d3",
            "    lsl.l  d1,d3",
            "    lsl.w  (a0)",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it == "    add.b  d0,d0" } shouldBe true
        lines.any { it == "    add.w  d1,d1" } shouldBe true
        lines.any { it == "    add.l  d2,d2" } shouldBe true
        lines.count { it.trimStart().startsWith("lsl.") } shouldBe 3
    }

    test("rewrites labelled lsl #1 and keeps the label") {
        val lines = mutableListOf(
            "mylabel:",
            "    lsl.w  #1,d3",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it == "mylabel:" } shouldBe true
        lines.any { it == "    add.w  d3,d3" } shouldBe true
        lines.none { it.trimStart().startsWith("lsl.") } shouldBe true
    }

    test("removes duplicate consecutive ext into the same data register") {
        val lines = mutableListOf(
            "    ext.w  d2",
            "    ext.w  d2",
            "    ext.l  d3",
            "    ext.l  d3",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it == "    ext.w  d2" } shouldBe 1
        lines.count { it == "    ext.l  d3" } shouldBe 1
    }

    test("rewrites cmpi #0 on data registers to tst") {
        val lines = mutableListOf(
            "    cmpi.b  #0,d0",
            "    cmpi.w  #0,d1",
            "    cmpi.l  #0,d2",
            "    beq     somewhere"
        )
        optimizeAssembly(lines)
        lines.any { it == "    tst.b  d0" } shouldBe true
        lines.any { it == "    tst.w  d1" } shouldBe true
        lines.any { it == "    tst.l  d2" } shouldBe true
        lines.none { it.trimStart().startsWith("cmpi.") } shouldBe true
    }

    test("rewrites cmp #0 alias on data registers to tst") {
        val lines = mutableListOf(
            "    cmp.b  #0,d3",
            "    bne     somewhere"
        )
        optimizeAssembly(lines)
        lines.any { it == "    tst.b  d3" } shouldBe true
        lines.none { it.trimStart().startsWith("cmp.") } shouldBe true
    }

    test("keeps label when rewriting cmpi #0") {
        val lines = mutableListOf(
            "mylabel:",
            "    cmpi.w  #0,d4",
            "    beq     somewhere"
        )
        optimizeAssembly(lines)
        lines.any { it == "mylabel:" } shouldBe true
        lines.any { it == "    tst.w  d4" } shouldBe true
        lines.none { it.trimStart().startsWith("cmpi.") } shouldBe true
    }

    test("does not rewrite cmp with non-zero immediate") {
        val lines = mutableListOf(
            "    cmpi.b  #1,d0",
            "    cmp.w  #42,d1",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it.trimStart().startsWith("cmpi.") } shouldBe 1
        lines.count { it.trimStart().startsWith("cmp.") } shouldBe 1
    }

    test("retargets branch chains through standalone labels") {
        val lines = mutableListOf(
            "    bra  first",
            "    rts",
            "first:",
            "    bra  second",
            "    rts",
            "second:",
            "    bra  done",
            "    rts",
            "done:",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.filter { it.trimStart().startsWith("bra") }.map { it.trim() } shouldBe
            listOf("bra  done", "bra  done", "bra  done")
        lines.any { it == "first:" } shouldBe true
        lines.any { it == "second:" } shouldBe true
    }

    test("keeps a loaded register across a branch when a later compare reads it") {
        val lines = mutableListOf(
            "    move.b  8(a5),d2",
            "    cmpi.b  #1,d2",
            "    beq  one",
            "    cmpi.b  #2,d2",
            "    beq  two",
            "    rts",
            "one:",
            "    moveq  #1,d0",
            "    rts",
            "two:",
            "    moveq  #2,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.b  8(a5),d2" } shouldBe true
    }

    test("keeps a loaded register across a branch when a later arithmetic op reads it") {
        val lines = mutableListOf(
            "    move.l  8(a5),d2",
            "    cmpi.l  #1,d2",
            "    beq  one",
            "    addq.l  #1,d2",
            "    rts",
            "one:",
            "    moveq  #1,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.l  8(a5),d2" } shouldBe true
    }

    test("does not rewrite cmp register-register") {
        val lines = mutableListOf(
            "    cmp.b  d0,d1",
            "    cmpi.w  #0,d2",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it == "    cmp.b  d0,d1" } shouldBe true
        lines.any { it == "    tst.w  d2" } shouldBe true
    }

    test("does not collapse ext.w plus ext.l or move.w plus ext.l") {
        val lines = mutableListOf(
            "    ext.w  d0",
            "    ext.l  d0",
            "    move.w  d1,d0",
            "    ext.l  d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it == "    ext.w  d0" } shouldBe 1
        lines.count { it == "    ext.l  d0" } shouldBe 2
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
        // Should emit a store from a0 to the virtual register location
        lines.subroutineLines().count { it.startsWith("move.l  a0,") } shouldBe 1
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
        val sub = lines.subroutineLines()
        val jsrIndex = sub.indexOfFirst { it.startsWith("bsr") && it.contains("test.asmsub") }
        jsrIndex shouldBeGreaterThan -1
        // There should be NO move from d0 between jsr and any branch instruction (beq/bne)
        val afterJsr = sub.drop(jsrIndex + 1)
        val branchIndex = afterJsr.indexOfFirst { it.startsWith("beq") || it.startsWith("bne") || it.startsWith("bmi") || it.startsWith("bpl") }
        val betweenJsrAndBranch = if (branchIndex >= 0) afterJsr.subList(0, branchIndex) else afterJsr
        betweenJsrAndBranch.count { it.startsWith("move") && it.firstOperand() == "d0" } shouldBe 0
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
        val subLines = lines.subroutineLines()
        val jsrIndex = subLines.indexOfFirst { it.startsWith("jsr") }
        jsrIndex shouldBeGreaterThan -1
        // There should be NO move from a0 between jsr and any branch instruction
        val afterJsr = subLines.drop(jsrIndex + 1)
        val branchIndex = afterJsr.indexOfFirst { it.startsWith("beq") || it.startsWith("bne") }
        val betweenJsrAndBranch = if (branchIndex >= 0) afterJsr.subList(0, branchIndex) else afterJsr
        betweenJsrAndBranch.count { it.startsWith("move") && it.firstOperand() == "a0" } shouldBe 0
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
        lines.any { it.contains("moveq  #99,d7") } shouldBe true
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
            "    move.w  #100,p8b_main.p8v_count",
            "p8_label_gen_2:",
            "    move.w  d7,p8b_main.p8v_sum",
            "    subq.w  #1,p8b_main.p8v_count",
            "    bne  p8_label_gen_2"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("dbra") } shouldBe false
    }

    test("dbra peephole does not trigger when body contains bsr call") {
        val lines = mutableListOf(
            "    move.w  #100,p8b_main.p8v_count",
            "p8_label_gen_2:",
            "    bsr  some_sub",
            "    subq.w  #1,p8b_main.p8v_count",
            "    bne  p8_label_gen_2"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("dbra") } shouldBe false
    }

    test("inverts a conditional branch over an unconditional jump") {
        val lines = mutableListOf(
            "    bhi  then_branch",
            "    bra  join",
            "then_branch:",
            "    moveq  #1,d0",
            "join:"
        )

        optimizeAssembly(lines)

        lines.any { it.trim() == "bls  join" } shouldBe true
        lines.any { it.trim() == "then_branch:" } shouldBe true
        lines.none { it.trim() == "bra  join" } shouldBe true
    }

    test("dbra via IRLoopChunk preserves label on init line") {
        val body = IRCodeChunk(null, null).also { it += IRInstructions.memoryOp(Opcode.INCM, IRDataType.WORD, IRMemory.direct("p8b_main.p8v_sum")) }
        val outerChunk = IRCodeChunk("outer", null)
        val loop = IRLoopChunk("p8_label_gen_2", 100, mutableListOf(body))
        val lines = generateAsmChunks(tempRoot, listOf(outerChunk, loop))
        lines.any { it.contains("outer:") } shouldBe true
        lines.any { it.contains("dbra  d7,p8_label_gen_2") } shouldBe true
        lines.any { it.contains("moveq  #99,d7") } shouldBe true
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
            "    move.w  #100,p8b_main.p8v_count",
            "p8_label_gen_2:",
            "    move.w  p8b_main.p8v_count,d0",
            "    subq.w  #1,p8b_main.p8v_count",
            "    bne  p8_label_gen_2"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("dbra") } shouldBe false
        lines.any { it.contains("move.w  p8b_main.p8v_count,d0") } shouldBe true
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
        lines.subroutineLines().count { it.startsWith("move.w  ") && it.endsWith(",d0") } shouldBe 1
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
        lines.subroutineLines().count { it.startsWith("move.w  ") && it.endsWith(",d0") } shouldBe 2
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
        lines.subroutineLines().count { it.startsWith("move.w  ") && it.endsWith(",d0") } shouldBe 3
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
        lines.subroutineLines().count { it.startsWith("move.w  ") && it.endsWith(",d0") } shouldBe 2
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
        lines.any { it.startsWith("move.w  (a0,d0.w*2),p8_regfile+") } shouldBe true
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

        val sub = lines.subroutineLines()
        sub.any { it.startsWith("movea.l  ") && it.endsWith(",a0") } shouldBe true
        sub.any { it.startsWith("move.w  (a0),p8_regfile+") } shouldBe true
        sub.any { it.endsWith(",(6,a0)") } shouldBe true
        // the pointer is loaded from memory, so a large displacement needs an
        // explicit adda; folding it into a lea would address the regfile itself
        sub.any { it == "adda.l  #40000,a0" } shouldBe true
        sub.none { it.startsWith("lea  p8_regfile") } shouldBe true
    }

    test("float indexed memory lowering uses full width d0.l indexing") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-indexed-float"),
            listOf(
                IRInstructions.loadMemory(Opcode.LOADX, IRDataType.FLOAT, 1, IRMemory.indexed("arr", 2, IRDataType.WORD, 4)),
                IRInstructions.storeMemory(Opcode.STOREX, IRDataType.FLOAT, 1, IRMemory.indexed("arr", 2, IRDataType.WORD, 8))
            )
        )

        // 68020 folds the scale into the full-extension form, no manual scaling.
        lines.any { it == "fmove.s  (0,a0,d0.l*4),fp0" } shouldBe true
        lines.any { it == "fmove.s  fp0,(0,a0,d0.l*8)" } shouldBe true
        lines.none { it.startsWith("lsl.l") } shouldBe true
        lines.none { it.startsWith("add.l  d0,d0") } shouldBe true
    }

    test("float indexed memory lowering scales manually on 68000") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-indexed-float-000"),
            listOf(
                IRInstructions.loadMemory(Opcode.LOADX, IRDataType.FLOAT, 1, IRMemory.indexed("arr", 2, IRDataType.WORD, 4)),
                IRInstructions.storeMemory(Opcode.STOREX, IRDataType.FLOAT, 1, IRMemory.indexed("arr", 2, IRDataType.WORD, 8))
            ),
            Amiga500Target()
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

        val sub = lines.subroutineLines()
        sub.count { it.startsWith("move.w  var+4,") } shouldBe 1
        sub.count { it == "move.w  var+4,d0" } shouldBe 0
        sub.count { it.endsWith(",\$00dff180") } shouldBe 1
        sub.any { it == "move.w  #1234,var" } shouldBe true
    }

    // === extb.l gate hardening ===

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

    test("LOADMSL sign-extends byte to long with extb.l on M68020") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-loadmsl-68020"),
            listOf(IRInstructions.loadMemory(Opcode.LOADMSL, IRDataType.BYTE, 1, IRMemory.direct("p8b_test.p8v_value")))
        )

        lines.count { it == "extb.l  d0" } shouldBe 1
        lines.count { it == "ext.w  d0" } shouldBe 0
        lines.count { it == "ext.l  d0" } shouldBe 0
    }

    test("LOADMSL sign-extends byte to long with ext.w plus ext.l on M68000") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-loadmsl-68000"),
            listOf(IRInstructions.loadMemory(Opcode.LOADMSL, IRDataType.BYTE, 1, IRMemory.direct("p8b_test.p8v_value"))),
            target = Amiga500Target()
        )

        lines.count { it == "extb.l  d0" } shouldBe 0
        lines.count { it == "ext.w  d0" } shouldBe 1
        lines.count { it == "ext.l  d0" } shouldBe 1
    }

    test("LOADISL sign-extends byte to long with extb.l on M68020") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-loadisl-68020"),
            listOf(IRInstructions.loadMemory(Opcode.LOADISL, IRDataType.BYTE, 1, IRMemory.indirect(2)))
        )

        lines.count { it == "extb.l  d0" } shouldBe 1
        lines.count { it == "ext.w  d0" } shouldBe 0
        lines.count { it == "ext.l  d0" } shouldBe 0
    }

    test("M5 drops a lea when a0 already holds the same base") {
        val lines = mutableListOf(
            "    lea  arr,a0",
            "    move.w  d1,(a0,d0.w)",
            "    lea  arr,a0",
            "    clr.w  (a0,d0.w)"
        )
        optimizeAssembly(lines)
        lines.count { it.trimStart().startsWith("lea  arr,a0") } shouldBe 1
        lines.any { it.trimStart().startsWith("clr.w") } shouldBe true
    }

    test("M5 keeps a lea when a different base was loaded into a0") {
        val lines = mutableListOf(
            "    lea  arr,a0",
            "    move.w  d1,(a0,d0.w)",
            "    lea  other,a0",
            "    lea  arr,a0",
            "    clr.w  (a0,d0.w)"
        )
        optimizeAssembly(lines)
        lines.count { it.trimStart().startsWith("lea  arr,a0") } shouldBe 2
        lines.count { it.trimStart().startsWith("lea  other,a0") } shouldBe 1
    }

    test("M5 keeps a lea when a0 is written in between") {
        val lines = mutableListOf(
            "    lea  arr,a0",
            "    movea.l  other,a0",
            "    lea  arr,a0",
            "    clr.w  (a0,d0.w)"
        )
        optimizeAssembly(lines)
        lines.count { it.trimStart().startsWith("lea  arr,a0") } shouldBe 2
    }

    test("M5 keeps a lea when a call intervenes") {
        val lines = mutableListOf(
            "    lea  arr,a0",
            "    bsr  some.sub",
            "    lea  arr,a0",
            "    clr.w  (a0,d0.w)"
        )
        optimizeAssembly(lines)
        lines.count { it.trimStart().startsWith("lea  arr,a0") } shouldBe 2
        lines.any { it.trimStart().startsWith("bsr") } shouldBe true
    }

    test("lea removal inside the loop enables conditional-over-jump") {
        // the redundant lea blocks the bne/bra/JOIN window; only a refiring
        // loop can remove the lea first and invert the branch afterwards.
        val lines = mutableListOf(
            "    lea  arr,a0",
            "    move.w  d1,(a0,d0.w)",
            "    bne  THEN",
            "    lea  arr,a0",
            "    bra  JOIN",
            "THEN:",
            "    move.w  d2,d3",
            "JOIN:",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "beq  JOIN" } shouldBe true
        lines.none { it.trim() == "bra  JOIN" } shouldBe true
        lines.count { it.trimStart().startsWith("lea  arr,a0") } shouldBe 1
    }

    test("folds movea plus address arithmetic into lea") {
        val lines = mutableListOf(
            "    movea.l  a0,a2",
            "    addq.l  #1,a2",
            "    move.w  (a2),d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "lea  (1,a0),a2" } shouldBe true
        lines.none { it.trimStart().startsWith("movea") } shouldBe true
        lines.none { it.trimStart().startsWith("addq") } shouldBe true
    }

    test("does not fold a symbol memory load plus arithmetic into lea") {
        // `move.l arr,a2` loads the value of arr, `lea arr+2,a2` would take its address.
        val lines = mutableListOf(
            "    move.l  arr,a2",
            "    addq.l  #2,a2",
            "    move.w  (a2),d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.l  arr,a2" } shouldBe true
        lines.any { it.trim() == "addq.l  #2,a2" } shouldBe true
        lines.none { it.trimStart().startsWith("lea") } shouldBe true
    }

    test("folds an immediate symbol base plus arithmetic into lea with offset") {
        val lines = mutableListOf(
            "    movea.l  #arr,a2",
            "    addq.l  #2,a2",
            "    move.w  (a2),d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "lea  arr+2,a2" } shouldBe true
    }

    test("does not fold move plus arithmetic over a flag consumer") {
        val lines = mutableListOf(
            "    move.l  #arr,a2",
            "    addq.l  #2,a2",
            "    beq  somewhere",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.l  #arr,a2" } shouldBe true
        lines.any { it.trim() == "addq.l  #2,a2" } shouldBe true
    }

    test("does not fold absolute address sources that may be hardware") {
        val lines = mutableListOf(
            "    movea.l  \$00dff096,a2",
            "    addq.l  #2,a2",
            "    move.w  (a2),d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trimStart().startsWith("movea") } shouldBe true
        lines.any { it.trimStart().startsWith("addq") } shouldBe true
    }

    test("does not fold a frame slot load plus arithmetic into lea") {
        // `movea.l (-8,a5),a2` loads a pointer from the slot; `lea (-6,a5),a2`
        // would instead point into the stack frame. This broke circles.p8.
        val lines = mutableListOf(
            "    movea.l  (-8,a5),a2",
            "    addq.l  #2,a2",
            "    move.w  (a2),d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "movea.l  (-8,a5),a2" } shouldBe true
        lines.any { it.trim() == "addq.l  #2,a2" } shouldBe true
        lines.none { it.trimStart().startsWith("lea") } shouldBe true
    }

    test("does not fold an unsigned frame slot load plus arithmetic into lea") {
        // real codegen emits `8(a5)`, not `(8,a5)`, for positive offsets.
        val lines = mutableListOf(
            "    movea.l  8(a5),a2",
            "    addq.l  #2,a2",
            "    move.w  (a2),d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "movea.l  8(a5),a2" } shouldBe true
        lines.any { it.trim() == "addq.l  #2,a2" } shouldBe true
        lines.none { it.trimStart().startsWith("lea") } shouldBe true
    }

    test("M5 forgets a0 across a label") {
        val lines = mutableListOf(
            "    lea  arr,a0",
            "somewhere:",
            "    lea  arr,a0",
            "    clr.w  (a0,d0.w)"
        )
        optimizeAssembly(lines)
        lines.count { it.trimStart().startsWith("lea  arr,a0") } shouldBe 2
        lines.any { it.trimStart().startsWith("somewhere:") } shouldBe true
    }

    test("M5 drops a lea across a conditional branch to a label") {
        // A conditional branch does not modify a0, and its target is a label where the tracked
        // value is dropped, so the fallthrough still knows a0 holds the base.
        val lines = mutableListOf(
            "    lea  arr,a0",
            "    move.w  (a0,d0.w),d0",
            "    bge  clamp.done",
            "    lea  arr,a0",
            "    clr.w  (a0,d0.w)",
            "clamp.done:"
        )
        optimizeAssembly(lines)
        lines.count { it.trimStart().startsWith("lea  arr,a0") } shouldBe 1
        lines.any { it.trimStart().startsWith("clr.w") } shouldBe true
    }

    test("qemu68k word right shifts avoid the memory forms its 68020 model mis-emulates") {
        // QEMU's 68020 sign-extends the operand for both `asr.w <abs>` and
        // `lsr.w (a0)`, so both memory forms give the wrong result. The
        // register form round-trips through d0 and is correct.
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-qemu-word-rshift"),
            listOf(
                IRInstructions.memoryOp(Opcode.ASRM, IRDataType.WORD, IRMemory.direct("p8b_test.p8v_value")),
                IRInstructions.memoryOp(Opcode.LSRM, IRDataType.WORD, IRMemory.direct("p8b_test.p8v_other"))
            ),
            Qemu68kTarget()
        )

        lines.count { it.startsWith("asr.w") } shouldBe 1
        lines.count { it.startsWith("lsr.w") } shouldBe 1
        lines.count { it == "asr.w  #1,d0" } shouldBe 1
        lines.count { it == "lsr.w  #1,d0" } shouldBe 1
        lines.count { it == "move.w  p8b_test.p8v_value,d0" } shouldBe 1
        lines.count { it == "move.w  d0,p8b_test.p8v_value" } shouldBe 1
        lines.count { it.startsWith("lea") } shouldBe 0
    }

    test("amiga500 keeps the compact word right shift memory form") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-amiga-word-rshift"),
            listOf(
                IRInstructions.memoryOp(Opcode.ASRM, IRDataType.WORD, IRMemory.direct("p8b_test.p8v_value")),
                IRInstructions.memoryOp(Opcode.LSRM, IRDataType.WORD, IRMemory.direct("p8b_test.p8v_other"))
            ),
            Amiga500Target()
        )

        lines.count { it.startsWith("asr.w  p8b_test.p8v_value") } shouldBe 1
        lines.count { it.startsWith("lsr.w  p8b_test.p8v_other") } shouldBe 1
        lines.count { it == "asr.w  #1,d0" } shouldBe 0
        lines.count { it == "lsr.w  #1,d0" } shouldBe 0
    }

    test("large constant shifts use moveq when the count fits") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-large-shift-moveq"),
            listOf(IRInstructions.binaryImmediate(Opcode.LSLI, IRDataType.LONG, 1, 12))
        )

        lines.count { it == "moveq  #12,d1" } shouldBe 1
        lines.count { it == "move.w  #12,d1" } shouldBe 0
        lines.count { it == "lsl.l  d1,d0" } shouldBe 1
    }

    // === A3 swap-back peephole ===

    test("A3 drops a swap-back move pair") {
        val lines = mutableListOf(
            "    move.l  d0,d1",
            "    move.l  d1,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it.contains("move.l") } shouldBe 1
        lines.count { it.startsWith("    move.l  d0,d1") } shouldBe 1
    }

    test("A3 keeps moves when sizes differ") {
        val lines = mutableListOf(
            "    move.l  d0,d1",
            "    move.b  d1,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it.startsWith("    move") } shouldBe 2
    }

    test("A3 keeps a move pair when a label sits on the second move") {
        val lines = mutableListOf(
            "    move.w  d0,d1",
            "somewhere:",
            "    move.w  d1,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it.startsWith("    move.w") } shouldBe 2
    }

    test("A3 drops a mirrored move pair through a symbol") {
        // `dmask` merely starts with d: not a register, but the mirror itself
        // is still redundant (second writes back what the first just stored).
        val lines = mutableListOf(
            "    move.w  d0,dmask",
            "    move.w  dmask,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it.startsWith("    move.w") } shouldBe 1
        lines.count { it.startsWith("    move.w  d0,dmask") } shouldBe 1
    }

    test("drops a mirrored move pair across address registers") {
        // real bootinfo_ptr shape: a2 is unused between the two moves.
        val lines = mutableListOf(
            "    move.l  d0,a2",
            "    move.l  a2,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it.startsWith("    move.l") } shouldBe 1
        lines.count { it.startsWith("    move.l  d0,a2") } shouldBe 1
    }

    test("drops a duplicated consecutive move") {
        val lines = mutableListOf(
            "    move.l  p8b_test.p8v_x,d0",
            "    move.l  p8b_test.p8v_x,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it.startsWith("    move.l") } shouldBe 1
    }

    test("drops movea and exg self-moves") {
        val lines = mutableListOf(
            "    movea.l  a0,a0",
            "    exg     d2,d2",
            "    move.l  d0,d1",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("movea.l  a0,a0") } shouldBe false
        lines.any { it.contains("exg") } shouldBe false
        lines.count { it.startsWith("    move.l  d0,d1") } shouldBe 1
    }

    test("keeps a flag-setting self-move") {
        // `move.l d0,d0` sets flags like `tst.l d0`: removing it needs a
        // liveness proof this pass does not attempt.
        val lines = mutableListOf(
            "    move.l  d0,d0",
            "    beq     somewhere"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("move.l  d0,d0") } shouldBe true
    }

    test("keeps mirrors with pointer-advancing operands") {
        val lines = mutableListOf(
            "    move.l  (a0)+,d0",
            "    move.l  d0,(a0)+",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it.startsWith("    move.l") } shouldBe 2
    }

    test("keeps mixed move and movea mirrors") {
        // movea sets no flags while move does: flag states would differ.
        val lines = mutableListOf(
            "    movea.l  a0,a1",
            "    move.l  a1,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it.trimStart().startsWith("move") } shouldBe 2
    }

    test("does not merge moves through case-differing symbols") {
        val lines = mutableListOf(
            "    move.l  Foo,d0",
            "    move.l  foo,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it.startsWith("    move.l") } shouldBe 2
    }

    // === A2 store-reload peephole ===

    test("A2 replaces a store-reload pair with a register copy") {
        val lines = mutableListOf(
            "    move.l  d0,p8b_test.p8v_x",
            "    move.l  p8b_test.p8v_x,d1",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it == "    move.l  d0,p8b_test.p8v_x" } shouldBe 1
        lines.count { it == "    move.l  d0,d1" } shouldBe 1
        lines.count { it.contains("p8b_test.p8v_x,d1") } shouldBe 0
    }

    test("A2 replaces a frame-slot store-reload with a register copy") {
        val lines = mutableListOf(
            "    move.l  d0,8(a5)",
            "    move.l  8(a5),d1",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it == "    move.l  d0,8(a5)" } shouldBe 1
        lines.count { it == "    move.l  d0,d1" } shouldBe 1
        lines.count { it == "    move.l  8(a5),d1" } shouldBe 0
    }

    test("A2 replaces a negative-offset frame-slot store-reload") {
        val lines = mutableListOf(
            "    move.b  d2,(-5,a5)",
            "    move.b  (-5,a5),d3",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it == "    move.b  d2,(-5,a5)" } shouldBe 1
        lines.count { it == "    move.b  d2,d3" } shouldBe 1
    }

    test("A2 keeps mismatched frame-slot offsets untouched") {
        val lines = mutableListOf(
            "    move.l  d0,8(a5)",
            "    move.l  4(a5),d1",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it == "    move.l  8(a5),d0" } shouldBe 0
        lines.count { it == "    move.l  4(a5),d1" } shouldBe 1
    }

    test("A2 keeps post-increment store-reloads untouched") {
        val lines = mutableListOf(
            "    move.l  d0,(a0)+",
            "    move.l  (a0)+,d1",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it == "    move.l  d0,(a0)+" } shouldBe 1
        lines.count { it == "    move.l  (a0)+,d1" } shouldBe 1
        lines.count { it == "    move.l  d0,d1" } shouldBe 0
    }

    test("A2 drops a store-reload-into-source pair as a mirror") {
        // The old store-reload pass deliberately skipped these; the mirror
        // pass now removes the reload (it writes back what was just stored).
        val lines = mutableListOf(
            "    move.l  d0,p8b_test.p8v_x",
            "    move.l  p8b_test.p8v_x,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it == "    move.l  d0,p8b_test.p8v_x" } shouldBe 1
        lines.count { it == "    move.l  p8b_test.p8v_x,d0" } shouldBe 0
    }

    test("A2 does not treat symbols starting with d as data registers") {
        val lines = mutableListOf(
            "    move.b  d0,data",
            "    move.b  data,dtmp",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it == "    move.b  data,dtmp" } shouldBe 1
    }

    test("A2 leaves absolute hardware-address reloads untouched") {
        // Reading a hardware register can have side effects, so the reload must not be removed.
        val lines = mutableListOf(
            "    move.w  d0,\$dff180",
            "    move.w  \$dff180,d1",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it == "    move.w  d0,\$dff180" } shouldBe 1
        lines.count { it == "    move.w  \$dff180,d1" } shouldBe 1
        lines.count { it == "    move.w  d0,d1" } shouldBe 0
    }

    // === const-operate folding peephole ===

    test("folds move-const plus or into immediate form") {
        val lines = mutableListOf(
            "    move.b  #8,d5",
            "    or.b  d5,d4",
            "    move.l  d0,d5",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "ori.b  #8,d4" } shouldBe true
        lines.none { it.contains("move.b  #8,d5") } shouldBe true
    }

    test("folds move-const plus push into immediate push") {
        val lines = mutableListOf(
            "    move.l  #sym,d2",
            "    move.l  d2,-(sp)",
            "    move.l  d7,d2",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.l  #sym,-(sp)" } shouldBe true
        lines.none { it.contains("move.l  #sym,d2") } shouldBe true
    }

    test("folds moveq plus narrow op") {
        val lines = mutableListOf(
            "    moveq  #-1,d2",
            "    move.w  d2,d1",
            "    moveq  #0,d2",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.none { it.trim() == "moveq  #-1,d2" } shouldBe true
        lines.any { it.contains("d1") && it.contains("#-1") } shouldBe true
    }

    test("keeps the pair when the temp is read afterwards") {
        val lines = mutableListOf(
            "    move.l  #sym,d2",
            "    move.l  d2,-(sp)",
            "    move.l  d2,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.l  #sym,d2" } shouldBe true
        lines.any { it.trim() == "move.l  d2,-(sp)" } shouldBe true
    }

    test("keeps addi-form targets that cannot address An") {
        // `add.l d0,a2` is legal but `addi.l #4,a2` is not: keep the pair
        // (the `move.l #4` itself may still become `moveq` via 2.3).
        val lines = mutableListOf(
            "    move.l  #4,d0",
            "    add.l  d0,a2",
            "    move.l  d7,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.none { it.trimStart().startsWith("addi.") } shouldBe true
        lines.any { it.trim() == "add.l  d0,a2" } shouldBe true
    }

    test("keeps the pair across a label") {
        val lines = mutableListOf(
            "    move.b  #8,d5",
            "    or.b  d5,d4",
            "somelabel:",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.b  #8,d5" } shouldBe true
        lines.any { it.trim() == "or.b  d5,d4" } shouldBe true
    }


    // === A5 moveq word-form peephole ===

    test("A5 rewrites move.w with small immediate to moveq") {
        val lines = mutableListOf(
            "    move.w  #42,d0",
            "    move.w  d0,d1"
        )
        optimizeAssembly(lines)
        lines.count { it.trimStart() == "moveq  #42,d0" } shouldBe 1
        lines.count { it.contains("move.w  #42,d0") } shouldBe 0
    }

    test("A5 keeps move.w for immediate outside moveq range") {
        val lines = mutableListOf(
            "    move.w  #384,d0",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it.trimStart() == "move.w  #384,d0" } shouldBe 1
        lines.count { it.contains("moveq  #384,d0") } shouldBe 0
    }

    test("A5 blocks rewrite when a longword read follows") {
        val lines = mutableListOf(
            "    move.w  #42,d0",
            "    move.l  d0,d1",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.count { it.trimStart() == "move.w  #42,d0" } shouldBe 1
        lines.count { it.contains("moveq  #42,d0") } shouldBe 0
    }

    test("A5 allows rewrite when a full write precedes the longword read") {
        val lines = mutableListOf(
            "    move.w  #42,d0",
            "    move.l  #0,d0",
            "    move.l  d0,d1"
        )
        optimizeAssembly(lines)
        lines.count { it.trimStart() == "moveq  #42,d0" } shouldBe 1
    }

    test("A5 handles tab-separated operands") {
        val lines = mutableListOf(
            "\tmove.w\t#42,d0",
            "\tmove.w\td0,d1"
        )
        optimizeAssembly(lines)
        lines.count { it.trim() == "moveq  #42,d0" } shouldBe 1
    }

    test("A5 handles label on the move.w line") {
        val lines = mutableListOf(
            "label:  move.w  #42,d0",
            "    move.w  d0,d1"
        )
        optimizeAssembly(lines)
        lines.count { it.contains("label:") } shouldBe 1
        lines.any { it.contains("moveq  #42,d0") } shouldBe true
    }

    // === zero stores avoid clr on I/O addresses (68000 dummy-read hazard) ===

    test("zero store to Amiga custom-chip address uses move instead of clr") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-zero-io-amiga"),
            listOf(
                IRInstructions.storeImmediate(IRDataType.WORD, 0, IRMemory.direct(0xdff096u)),
                IRInstructions.storeZero(Opcode.STOREZM, IRDataType.WORD, IRMemory.direct(0xdff180u))
            ),
            Amiga500Target()
        )

        lines.any { it == "move.w  #0,$00dff096" } shouldBe true
        lines.any { it == "move.w  #0,$00dff180" } shouldBe true
        lines.none { it.startsWith("clr") } shouldBe true
    }

    test("zero store to the same addresses keeps clr on qemu68k (not I/O there)") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-zero-io-qemu68k"),
            listOf(
                IRInstructions.storeImmediate(IRDataType.WORD, 0, IRMemory.direct(0xdff096u)),
                IRInstructions.storeZero(Opcode.STOREZM, IRDataType.WORD, IRMemory.direct(0xdff180u))
            )
        )

        lines.any { it == "clr.w  $00dff096" } shouldBe true
        lines.any { it == "clr.w  $00dff180" } shouldBe true
    }

    test("zero store to plain RAM keeps clr even on Amiga") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-zero-ram-amiga"),
            listOf(
                IRInstructions.storeImmediate(IRDataType.WORD, 0, IRMemory.direct(0x1000u)),
                IRInstructions.storeZero(Opcode.STOREZM, IRDataType.WORD, IRMemory.direct(0x2000u))
            ),
            Amiga500Target()
        )

        lines.any { it == "clr.w  $1000" } shouldBe true
        lines.any { it == "clr.w  $2000" } shouldBe true
    }

    test("zero store to a memvar symbol on an I/O address uses move instead of clr") {
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
        program.options.outputDir = tempRoot.resolve("test-m68k-zero-io-memvar")
        program.st.add(IRStMemVar("custom.DMACON", DataType.UWORD, 0xdff096u, null))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.storeImmediate(IRDataType.WORD, 0, IRMemory.direct("custom.DMACON")))
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

        lines.any { it == "move.w  #0,$00dff096" } shouldBe true
        lines.none { it.startsWith("clr") } shouldBe true
    }

    // === data-register zeroing uses moveq instead of clr ===

    test("forwarded zero call argument uses moveq instead of clr") {
        val site = callSite("copper.wait", arguments = listOf(hwArg(1, IRDataType.WORD, 10)))
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-call-forward-zero-moveq"),
            listOf(
                IRInstructions.load(IRDataType.WORD, 1, 0),
                IRInstructions.call(site)
            )
        )

        lines.any { it == "moveq  #0,d0" } shouldBe true
        lines.none { it.startsWith("clr") } shouldBe true
    }

    // === immediate form selection (2.3): moveq for longs, addq/subq for small immediates ===

    test("rewrites move.l with a small immediate to moveq") {
        val lines = mutableListOf(
            "    move.l  #100,d3",
            "    move.l  #\$ffffff9c,d4",
            "    move.l  #1000,d5",
            "    move.l  #somelabel,d6",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "moveq  #100,d3" } shouldBe true
        lines.any { it.trim() == "moveq  #-100,d4" } shouldBe true
        lines.any { it.trim() == "move.l  #1000,d5" } shouldBe true
        lines.any { it.trim() == "move.l  #somelabel,d6" } shouldBe true
    }

    test("rewrites small add/sub immediates to addq/subq with sign flip") {
        val lines = mutableListOf(
            "    add.b  #5,d0",
            "    sub.w  #8,d1",
            "    add.l  #-3,d2",
            "    sub.l  #-8,d3",
            "    add.w  #300,d4",
            "    add.b  #0,d5",
            "    add.b  #1,ccr"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "addq.b  #5,d0" } shouldBe true
        lines.any { it.trim() == "subq.w  #8,d1" } shouldBe true
        lines.any { it.trim() == "subq.l  #3,d2" } shouldBe true
        lines.any { it.trim() == "addq.l  #8,d3" } shouldBe true
        lines.any { it.trim() == "add.w  #300,d4" } shouldBe true
        lines.any { it.trim() == "add.b  #0,d5" } shouldBe true
        lines.any { it.trim() == "add.b  #1,ccr" } shouldBe true
    }

    // === IR echo comments are opt-in via irComments (off by default) ===
    test("IR echo comments are omitted by default and kept with irComments") {
        val instructions = listOf(IRInstructions.load(IRDataType.WORD, 1, 42))
        val quiet = generateAsm(tempRoot.resolve("test-m68k-no-ircomments"), instructions)
        quiet.none { it.trimStart().startsWith("; load") } shouldBe true
        val verbose = generateAsm(tempRoot.resolve("test-m68k-ircomments"), instructions, irComments = true)
        verbose.any { it.trimStart().startsWith("; load.w") } shouldBe true
    }

    // === instruction lines use lowercase mnemonics with two-space gaps ===
    test("emitted instructions are normalized to lowercase with two-space gaps") {
        val lines = generateAsm(
            tempRoot.resolve("test-m68k-normalize"),
            listOf(
                IRInstructions.loadMemory(Opcode.LOADMZ, IRDataType.BYTE, 1, IRMemory.direct("p8b_test.p8v_value"))
            )
        )

        // byte zero-extension is emitted directly as moveq/move.b
        lines.any { it == "moveq  #0,d0" } shouldBe true
        lines.any { it == "move.b  p8b_test.p8v_value,d0" } shouldBe true
        // every indented line the codegen emits starts with a lowercase mnemonic
        lines.filter { it.startsWith("    ") }.none { it[4].isUpperCase() } shouldBe true
    }

    // === dead zero-extend writeback (A.2) and pea for stack args (A.4) ===

    test("drops a dead zero-extend writeback when the next instruction sets flags") {
        val lines = mutableListOf(
            "    moveq  #0,d0",
            "    move.b  -1(a5),d0",
            "    move.w  d0,d2",
            "    add.w  d0,d0",
            "    move.b  -1(a5),d2",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.none { it.trim() == "move.w  d0,d2" } shouldBe true
        lines.any { it.trim() == "moveq  #0,d0" } shouldBe true
    }

    test("keeps a zero-extend writeback whose register is read first") {
        val lines = mutableListOf(
            "    moveq  #0,d0",
            "    move.b  -1(a5),d0",
            "    move.w  d0,d2",
            "    add.w  d0,d0",
            "    move.w  d2,-(sp)",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.w  d0,d2" } shouldBe true
    }

    test("keeps a zero-extend writeback when a compare reads its home register") {
        val lines = mutableListOf(
            "    moveq  #0,d0",
            "    move.b  -1(a5),d0",
            "    move.w  d0,d2",
            "    add.w  d0,d0",
            "    cmpi.w  #1,d2",
            "    move.b  -1(a5),d2",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.w  d0,d2" } shouldBe true
    }

    test("keeps a zero-extend writeback when the next instruction does not set flags") {
        val lines = mutableListOf(
            "    moveq  #0,d0",
            "    move.b  -1(a5),d0",
            "    move.w  d0,d2",
            "    lea  (a0,d0.w),a1",
            "    move.b  -1(a5),d2",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.w  d0,d2" } shouldBe true
    }

    test("replaces a staged constant stack argument with pea") {
        val lines = mutableListOf(
            "    move.l  #p8v_vals,d2",
            "    moveq  #16,d3",
            "    move.l  d2,-(sp)",
            "    bsr  f",
            "    lea  4(sp),sp",
            "    move.b  -1(a5),d2",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "pea p8v_vals" } shouldBe true
        lines.none { it.trim() == "move.l  #p8v_vals,d2" } shouldBe true
        lines.any { it.trim() == "move.l  d2,-(sp)" } shouldBe false
    }

    test("keeps the pea label and trailing comment") {
        val lines = mutableListOf(
            "    move.l  #p8v_vals,d2",
            "    moveq  #16,d3",
            "mypush:  move.l  d2,-(sp)    ; values",
            "    bsr  f",
            "    move.b  -1(a5),d2",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.contains("mypush:") && it.contains("pea p8v_vals") } shouldBe true
        lines.any { it.contains("; values") } shouldBe true
    }

    test("does not use pea when the staged register is still read") {
        val lines = mutableListOf(
            "    move.l  #p8v_vals,d2",
            "    moveq  #16,d3",
            "    move.l  d2,-(sp)",
            "    bsr  f",
            "    move.w  d2,d6",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.l  #p8v_vals,d2" } shouldBe true
        lines.none { it.trim() == "pea p8v_vals" } shouldBe true
    }

    test("does not use pea without an intervening flag overwrite") {
        val lines = mutableListOf(
            "    move.l  #p8v_vals,d2",
            "    move.l  d2,-(sp)",
            "    bsr  f",
            "    move.b  -1(a5),d2",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.l  #p8v_vals,d2" } shouldBe true
        lines.none { it.trim() == "pea p8v_vals" } shouldBe true
    }

    test("does not use pea for a numeric immediate") {
        val lines = mutableListOf(
            "    move.l  #1234,d2",
            "    moveq  #16,d3",
            "    move.l  d2,-(sp)",
            "    bsr  f",
            "    move.b  -1(a5),d2",
            "    rts"
        )
        optimizeAssembly(lines)
        lines.any { it.trim() == "move.l  #1234,d2" } shouldBe true
        lines.none { it.trim() == "pea 1234" } shouldBe true
    }
})
