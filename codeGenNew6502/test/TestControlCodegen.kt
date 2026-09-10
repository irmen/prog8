package prog8tests.codegen.new6502

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import prog8.code.core.*
import prog8.code.target.Cx16Target
import prog8.codegen.new6502.AsmGen
import prog8.intermediate.*
import prog8tests.helpers.ErrorReporterForTests
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Tests for the control flow / call translations, which read their operands from the
 * structured call site (arguments, results, locations) and from the typed register slots.
 */
class TestControlCodegen : FunSpec({

    val tempRoot = tempdir().toPath()

    fun generateAsm(
        instructions: List<IRInstruction>,
        extraElements: List<IIRBlockElement> = emptyList(),
        name: String
    ): List<String> {
        val target = Cx16Target()
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.FLOATSAFE)
            .floats(true)
            .compilerVersion("test")
            .memtopAddress(0xffffu)
            .build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val outputDir: Path = tempRoot.resolve(name)
        outputDir.toFile().deleteRecursively()
        outputDir.toFile().mkdirs()
        program.options.outputDir = outputDir

        val chunk = IRCodeChunk(null, null)
        chunk.instructions.addAll(instructions)
        val sub = IRSubroutine("main.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        extraElements.forEach { block.children.add(it) }
        program.blocks.add(block)

        AsmGen(program, target, ErrorReporterForTests()).generate()
        val asmFile = outputDir.resolve("test.asm")
        check(asmFile.exists()) { "Assembly file not written: $asmFile" }
        // drop the '; <instruction>' trace comments so only real instructions remain
        return asmFile.readText().lines().map { it.trim() }.filter { !it.startsWith(";") }
    }

    fun regularSub(label: String): IRSubroutine {
        val sub = IRSubroutine(label, emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(IRCodeChunk(null, null).also { it += IRInstructions.returnVoid() })
        return sub
    }

    test("call arguments in hardware slots are loaded in slot order, pairs before singles") {
        // the A-slot argument comes first in the argument list but must be loaded LAST
        val site = CallSite(
            CallTarget.Direct(codeLabel("main.testsub")),
            listOf(
                Calls.argument(1, IRDataType.BYTE, CallLocation.HardwareRegister(CallingConventionSlot(0))),
                Calls.argument(2, IRDataType.WORD, CallLocation.HardwareRegister(CallingConventionSlot(3)))
            )
        )
        val lines = generateAsm(
            listOf(IRInstructions.call(site)),
            listOf(regularSub("main.testsub")),
            "call-slot-order"
        )
        val jsrIndex = lines.indexOfFirst { it == "jsr  main.testsub" }
        jsrIndex shouldBeGreaterThan -1
        // the AX pair (slot 3, register r2) is loaded with lda+ldx, the A slot (r1) with lda
        val ldxIndex = lines.indexOfFirst { it.startsWith("ldx  p8_regfile") }
        ldxIndex shouldBeGreaterThan -1
        ldxIndex shouldBeLessThan jsrIndex
        val lastLdaBeforeJsr = (jsrIndex - 1 downTo 0).first { lines[it].startsWith("lda  p8_regfile") }
        lastLdaBeforeJsr shouldBeGreaterThan ldxIndex
    }

    test("call argument passed in parameter memory is stored to the resolved symbol") {
        val site = CallSite(
            CallTarget.Direct(codeLabel("main.testsub")),
            listOf(Calls.argument(1, IRDataType.WORD, CallLocation.ParameterMemory("value")))
        )
        val lines = generateAsm(
            listOf(IRInstructions.call(site)),
            listOf(regularSub("main.testsub")),
            "call-param-memory"
        )
        lines.any { it == "sta  main.testsub.value" } shouldBe true
        lines.any { it == "sta  main.testsub.value+1" } shouldBe true
        lines.indexOfFirst { it == "jsr  main.testsub" } shouldBeGreaterThan -1
    }

    test("single call result in a hardware slot is stored back into the virtual register") {
        val site = CallSite(
            CallTarget.Direct(codeLabel("main.testsub")),
            emptyList(),
            listOf(Calls.result(1, IRDataType.WORD, CallLocation.HardwareRegister(CallingConventionSlot(4))))
        )
        val lines = generateAsm(
            listOf(IRInstructions.call(site)),
            listOf(regularSub("main.testsub")),
            "call-result-slot"
        )
        val jsrIndex = lines.indexOfFirst { it == "jsr  main.testsub" }
        jsrIndex shouldBeGreaterThan -1
        val after = lines.drop(jsrIndex + 1)
        after.any { it.startsWith("sta  p8_regfile") } shouldBe true
        after.any { it.startsWith("sty  p8_regfile") } shouldBe true
    }

    test("status flag call results are not extracted by the backend") {
        val site = CallSite(
            CallTarget.Direct(codeLabel("main.testsub")),
            emptyList(),
            listOf(Calls.result(1, IRDataType.BYTE, CallLocation.StatusFlag(Statusflag.Pc)))
        )
        val lines = generateAsm(
            listOf(IRInstructions.call(site)),
            listOf(regularSub("main.testsub")),
            "call-result-flag"
        )
        val jsrIndex = lines.indexOfFirst { it == "jsr  main.testsub" }
        jsrIndex shouldBeGreaterThan -1
        lines.drop(jsrIndex + 1).any { it == "php" } shouldBe false
    }

    test("syscall reads its argument registers from the call site") {
        val syscall = IRInstructions.syscall(
            IMSyscall.MEMCOPY.number,
            listOf(
                Calls.argument(1, IRDataType.WORD),
                Calls.argument(2, IRDataType.WORD),
                Calls.argument(3, IRDataType.WORD)
            )
        )
        val lines = generateAsm(listOf(syscall), name = "syscall-memcopy")
        lines.any { it == "jsr  prog8_lib.memcopy_small" } shouldBe true
        lines.any { it == "sta  P8ZP_SCRATCH_W1" } shouldBe true
        lines.any { it == "sta  P8ZP_SCRATCH_W2" } shouldBe true
    }

    test("returnr reads the value from the source register slot") {
        val lines = generateAsm(
            listOf(IRInstructions.returnRegister(IRDataType.WORD, 3)),
            name = "returnr-word"
        )
        val ldaIndex = lines.indexOfFirst { it.startsWith("lda  p8_regfile") }
        ldaIndex shouldBeGreaterThan -1
        lines[ldaIndex + 1].startsWith("ldy  p8_regfile") shouldBe true
        lines[ldaIndex + 2] shouldBe "rts"
    }

    test("returni.f loads the float immediate from a generated constant") {
        val lines = generateAsm(
            listOf(IRInstructions.returnImmediateFloat(3.25)),
            name = "returni-float"
        )
        lines.any { it == "lda  #<prog8_float_const_0" } shouldBe true
        lines.any { it == "jsr  floats.MOVFM" } shouldBe true
    }

    test("concat combines the msb and lsb source registers") {
        val lines = generateAsm(
            listOf(IRInstructions.concat(IRDataType.BYTE, 1, 2, 3)),
            name = "concat-byte"
        )
        // msb (r2) goes to the high byte of r1, lsb (r3) to the low byte
        val staHigh = lines.indexOfFirst { it == "sta  p8_regfile+1" }
        val staLow = lines.indexOfFirst { it == "sta  p8_regfile+0" }
        staHigh shouldBeGreaterThan -1
        staLow shouldBeGreaterThan staHigh
    }

    test("exts sign-extends the source register into the destination") {
        val lines = generateAsm(
            listOf(IRInstructions.binary(Opcode.EXTS, IRDataType.BYTE, 1, 2)),
            name = "exts-byte"
        )
        lines.any { it == "and  #128" } shouldBe true
        lines.any { it == "lda  #255" } shouldBe true
    }

    test("align uses the immediate operand") {
        val lines = generateAsm(listOf(IRInstructions.align(256)), name = "align")
        lines.any { it == ".align  \$0100" } shouldBe true
    }

    test("float compare uses both float source registers and the integer destination") {
        val lines = generateAsm(
            listOf(IRInstructions.floatCompare(1, 2, 3)),
            name = "fcomp"
        )
        lines.any { it == "jsr  floats.FSUBT" } shouldBe true
        lines.any { it == "jsr  floats.SIGN" } shouldBe true
        lines.any { it.startsWith("sta  p8_regfile") } shouldBe true
    }

    test("jumpi jumps indirectly through the pointer register of the code target") {
        val lines = generateAsm(
            listOf(IRInstructions.jumpIndirect(5)),
            name = "jumpi"
        )
        lines.any { it.startsWith("jmp  (p8_regfile") } shouldBe true
    }
})
