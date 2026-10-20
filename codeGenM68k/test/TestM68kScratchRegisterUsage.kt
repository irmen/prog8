package prog8tests.codegen.m68k

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.ints.shouldBeGreaterThan
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
})
