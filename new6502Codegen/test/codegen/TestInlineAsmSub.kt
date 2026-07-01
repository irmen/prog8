package codegen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.*
import prog8.code.target.Cx16Target
import prog8.intermediate.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class TestInlineAsmSub : FunSpec({

    fun buildTestProgramWithInlineAsmSub(
        inline: Boolean,
        asmBody: String = "lda #42"
    ): Pair<IRProgram, ICompilationTarget> {
        val target = Cx16Target()
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.FLOATSAFE)
            .floats(false)
            .compilerVersion("test")
            .memtopAddress(0xffffu)
            .build()

        val st = IRSymbolTable()

        val program = IRProgram("test", st, options, DummyStringEncoder)

        // Create the inline/regular asmsub
        val asmChunk = IRInlineAsmChunk("main.test", asmBody, isIR = false, next = null)
        val asmSub = IRAsmSubroutine(
            label = "main.test",
            address = null,
            clobbers = emptySet(),
            parameters = emptyList(),
            returns = emptyList(),
            asmChunk = asmChunk,
            position = Position.DUMMY,
            isInline = inline
        )

        // Create the main subroutine that calls the asmsub
        val callChunk = IRCodeChunk(null, null)
        callChunk.instructions.add(IRInstruction(Opcode.CALL, labelSymbol = "main.test"))
        val mainSub = IRSubroutine("main.start", emptyList(), emptyList(), Position.DUMMY)
        mainSub.chunks.add(callChunk)

        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(mainSub)
        block.children.add(asmSub)
        program.blocks.add(block)

        return Pair(program, target)
    }

    fun generateAsm(outputDir: Path, program: IRProgram, target: ICompilationTarget): String {
        val codegen = CodeGenerator(program, target)
        codegen.generate()
        val asmFile = outputDir.resolve("${program.name}.asm")
        check(asmFile.exists()) { "Assembly file not written: $asmFile" }
        return asmFile.readText()
    }

    test("inline asmsub is inlined at call site, no jsr") {
        val (program, target) = buildTestProgramWithInlineAsmSub(inline = true)
        val outputDir = Path("/tmp/test-inline-asmsub-new")
        outputDir.toFile().deleteRecursively()
        outputDir.toFile().mkdirs()
        program.options.outputDir = outputDir

        val asm = generateAsm(outputDir, program, target)
        val lines = asm.lines().map { it.trim() }

        // The assembly body should appear in the caller
        lines.any { it == "lda #42" } shouldBe true
        // Should NOT have a jsr to the inline asmsub
        lines.count { it.startsWith("jsr") && it.contains("main.test") } shouldBe 0
        // Should NOT have a separate .proc block for the inline asmsub
        lines.count { it.contains("main.test") && it.contains(".proc") } shouldBe 0
    }

    test("regular asmsub is called via jsr, not inlined") {
        val (program, target) = buildTestProgramWithInlineAsmSub(inline = false, asmBody = "lda #99\nrts")
        val outputDir = Path("/tmp/test-regular-asmsub-new")
        outputDir.toFile().deleteRecursively()
        outputDir.toFile().mkdirs()
        program.options.outputDir = outputDir

        val asm = generateAsm(outputDir, program, target)
        val lines = asm.lines().map { it.trim() }

        // Should have a jsr to the regular asmsub
        lines.any { it.startsWith("jsr") && it.contains("main.test") } shouldBe true
        // The body should be in a separate .proc block
        lines.any { it.contains("main.test") && it.contains(".proc") } shouldBe true
    }
})
