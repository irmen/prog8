package prog8tests.codegen.m68k

import io.kotest.core.spec.style.FunSpec
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
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class TestInstructionSelectionOptimizations : FunSpec({

    fun generateAsm(outputDir: Path, instructions: List<IRInstruction>): List<String> {
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
        AsmGen(program, target).generate()
        val asmFile = outputDir.resolve("test.asm")
        check(asmFile.exists()) { "Assembly file not written: $asmFile" }
        return asmFile.readText().lines().map { it.trim() }
    }

    test("uses quick address adjustments and preserves large offsets") {
        val lines = generateAsm(
            Path("/tmp/test-m68k-offsets"),
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
            Path("/tmp/test-m68k-field-comments"),
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
            Path("/tmp/test-m68k-control"),
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
})
