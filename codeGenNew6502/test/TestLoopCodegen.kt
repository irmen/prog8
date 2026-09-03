package prog8tests.codegen.new6502

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import prog8.code.core.CompilationOptions
import prog8.code.core.OutputType
import prog8.code.core.Position
import prog8.code.core.ZeropageType
import prog8.code.target.Cx16Target
import prog8.codegen.new6502.AsmGen
import prog8.intermediate.*
import prog8tests.helpers.ErrorReporterForTests
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Regression tests for counted-loop code generation in the new 6502 backend.
 */
class TestLoopCodegen : FunSpec({

    val tempRoot = tempdir().toPath()

    fun generateAsmForLoop(loop: IRLoopChunk): List<String> {
        val target = Cx16Target()
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.FLOATSAFE)
            .floats(false)
            .compilerVersion("test")
            .memtopAddress(0xffffu)
            .optimize(true)
            .build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val outputDir = tempRoot.resolve("test-new6502-loop-${System.nanoTime()}")
        program.options.outputDir = outputDir
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(loop)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        outputDir.toFile().deleteRecursively()
        outputDir.toFile().mkdirs()
        AsmGen(program, target, ErrorReporterForTests()).generate()
        val asmFile = outputDir.resolve("test.asm")
        check(asmFile.exists()) { "Assembly file not written: $asmFile" }
        return asmFile.readText().lines().map { it.trimStart() }
    }

    test("IRLoopChunk uses Y as counter") {
        val body = IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.INCM, IRDataType.BYTE, labelSymbol = "p8b_main.p8v_sum")
        }
        val loop = IRLoopChunk("p8_label_gen_1", 5, mutableListOf(body))
        val lines = generateAsmForLoop(loop)
        lines.any { it.startsWith("ldy  #5") } shouldBe true
        lines.any { it.startsWith("dey") } shouldBe true
        lines.any { it.startsWith("bne  p8_label_gen_1") } shouldBe true
    }

    test("IRLoopChunk preserves Y around body chunk that uses Y") {
        val body = IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.STOREI, IRDataType.BYTE, reg1 = 2, reg2 = 3, immediate = 0)
        }
        val loop = IRLoopChunk("p8_label_gen_1", 5, mutableListOf(body))
        val lines = generateAsmForLoop(loop)
        lines.any { it.startsWith("ldy  #5") } shouldBe true
        lines.any { it.startsWith("tya") } shouldBe true
        lines.any { it.startsWith("pha") } shouldBe true
        lines.any { it.startsWith("pla") } shouldBe true
        lines.any { it.startsWith("tay") } shouldBe true
    }
})
