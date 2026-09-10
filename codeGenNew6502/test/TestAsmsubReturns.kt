package prog8tests.codegen.new6502

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.code.core.*
import prog8.code.target.Cx16Target
import prog8.codegen.new6502.AsmGen
import prog8.intermediate.*
import prog8tests.helpers.ErrorReporterForTests
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class TestAsmsubReturns : FunSpec({

    val tempRoot = tempdir().toPath()

    fun buildTestProgramWithCall(
        results: List<CallResult>
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

        // Derive the asmsub's return declarations from the structured call results
        val asmParams = results.map { ret ->
            val location = ret.location
            val regOrFlag = RegisterOrStatusflag(
                registerOrPair = when ((location as? CallLocation.HardwareRegister)?.slot?.value) {
                    0 -> RegisterOrPair.A
                    1 -> RegisterOrPair.X
                    2 -> RegisterOrPair.Y
                    3 -> RegisterOrPair.AX
                    4 -> RegisterOrPair.AY
                    5 -> RegisterOrPair.XY
                    6 -> RegisterOrPair.FAC1
                    7 -> RegisterOrPair.FAC2
                    else -> null
                },
                statusflag = (location as? CallLocation.StatusFlag)?.flag
            )
            val dt = when (ret.type) {
                IRDataType.BYTE -> DataType.forDt(BaseDataType.BOOL)
                IRDataType.WORD -> DataType.forDt(BaseDataType.UWORD)
                IRDataType.POINTER -> DataType.forDt(BaseDataType.POINTER)
                IRDataType.LONG -> DataType.forDt(BaseDataType.LONG)
                IRDataType.FLOAT -> DataType.forDt(BaseDataType.FLOAT)
                null -> DataType.forDt(BaseDataType.BOOL)
            }
            IRAsmSubroutine.IRAsmParam(regOrFlag, dt)
        }

        // Create the asmsub that will be called
        val asmChunk = IRInlineAsmChunk("main.testsub", "lda #42\nrts", isIR = false, next = null)
        val asmSub = IRAsmSubroutine(
            label = "main.testsub",
            address = null,
            clobbers = emptySet(),
            parameters = emptyList(),
            returns = asmParams,
            asmChunk = asmChunk,
            position = Position.DUMMY,
            isInline = false
        )

        // Create the main subroutine that calls the asmsub
        val callSite = CallSite(CallTarget.Direct(codeLabel("main.testsub")), emptyList(), results)
        val callChunk = IRCodeChunk(null, null)
        callChunk.instructions.add(IRInstructions.call(callSite))
        val mainSub = IRSubroutine("main.start", emptyList(), emptyList(), Position.DUMMY)
        mainSub.chunks.add(callChunk)

        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(mainSub)
        block.children.add(asmSub)
        program.blocks.add(block)

        return Pair(program, target)
    }

    fun generateAsm(outputDir: Path, program: IRProgram, target: ICompilationTarget): String {
        val codegen = AsmGen(program, target, ErrorReporterForTests())
        codegen.generate()
        val asmFile = outputDir.resolve("${program.name}.asm")
        check(asmFile.exists()) { "Assembly file not written: $asmFile" }
        return asmFile.readText()
    }

    test("multi-assign with @A + @Pz does not extract status flag in backend") {
        // Bug fix: the IR now emits branch patterns for status flags,
        // so the backend should NOT extract them with php/pla/and sequences.
        val results = listOf(
            Calls.result(0, IRDataType.BYTE, CallLocation.HardwareRegister(CallingConventionSlot(0))),
            Calls.result(1, IRDataType.BYTE, CallLocation.StatusFlag(Statusflag.Pz))
        )
        val (program, target) = buildTestProgramWithCall(results)
        val outputDir = tempRoot.resolve("test-new6502-multi-assign-pz")
        outputDir.toFile().deleteRecursively()
        outputDir.toFile().mkdirs()
        program.options.outputDir = outputDir

        val asm = generateAsm(outputDir, program, target)
        val lines = asm.lines().map { it.trim() }

        // Find the jsr line
        val jsrIndex = lines.indexOfFirst { it.startsWith("jsr") && it.contains("main.testsub") }
        jsrIndex shouldBeGreaterThan -1

        // After the jsr, there should be NO php/pla/and sequence for status flag extraction
        val afterJsr = lines.subList(jsrIndex + 1, lines.size)
        // The backend should not emit php (push processor status) for flag extraction
        afterJsr.any { it == "php" } shouldBe false
        // The backend should not emit pla followed by and #2 (Pz mask)
        afterJsr.any { it == "pla" && afterJsr.indexOf(it) < afterJsr.size - 1 && afterJsr[afterJsr.indexOf(it) + 1].contains("and") } shouldBe false
    }

    test("multi-assign with @A + @Pn does not extract status flag in backend") {
        // Bug fix: same as Pz test but for Pn (negative flag)
        val results = listOf(
            Calls.result(0, IRDataType.BYTE, CallLocation.HardwareRegister(CallingConventionSlot(0))),
            Calls.result(1, IRDataType.BYTE, CallLocation.StatusFlag(Statusflag.Pn))
        )
        val (program, target) = buildTestProgramWithCall(results)
        val outputDir = tempRoot.resolve("test-new6502-multi-assign-pn")
        outputDir.toFile().deleteRecursively()
        outputDir.toFile().mkdirs()
        program.options.outputDir = outputDir

        val asm = generateAsm(outputDir, program, target)
        val lines = asm.lines().map { it.trim() }

        // Find the jsr line
        val jsrIndex = lines.indexOfFirst { it.startsWith("jsr") && it.contains("main.testsub") }
        jsrIndex shouldBeGreaterThan -1

        // After the jsr, there should be NO php/pla/and sequence for status flag extraction
        val afterJsr = lines.subList(jsrIndex + 1, lines.size)
        afterJsr.any { it == "php" } shouldBe false
    }

    test("single-return expression call with @A emits store instruction") {
        // Single-return expression calls should still emit the store instruction
        // because the IR doesn't generate LOADHR for single-return calls.
        val results = listOf(
            Calls.result(0, IRDataType.BYTE, CallLocation.HardwareRegister(CallingConventionSlot(0)))
        )
        val (program, target) = buildTestProgramWithCall(results)
        val outputDir = tempRoot.resolve("test-new6502-single-return-a")
        outputDir.toFile().deleteRecursively()
        outputDir.toFile().mkdirs()
        program.options.outputDir = outputDir

        val asm = generateAsm(outputDir, program, target)
        val lines = asm.lines().map { it.trim() }

        // Should emit sta to store A to virtual register
        lines.any { it.startsWith("sta") && it.contains("p8_regfile") } shouldBe true
    }
})
