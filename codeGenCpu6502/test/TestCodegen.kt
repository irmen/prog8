package prog8tests.codegencpu6502

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import prog8.code.StMemVar
import prog8.code.SymbolTable
import prog8.code.SymbolTableMaker
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.source.SourceCode
import prog8.code.target.C64Target
import prog8.codegen.cpu6502.AsmGen6502
import prog8.codegen.cpu6502.VariableAllocator
import java.nio.file.Files
import kotlin.io.path.Path

class TestCodegen: FunSpec({

    fun getTestOptions(): CompilationOptions {
        val target = C64Target()
        return CompilationOptions(
            OutputType.RAW,
            CbmPrgLauncherType.NONE,
            ZeropageType.DONTUSE,
            zpReserved = emptyList(),
            zpAllowed = CompilationOptions.AllZeropageAllowed,
            floats = true,
            noSysInit = false,
            romable = false,
            compTarget = target,
            loadAddress = target.PROGRAM_LOAD_ADDRESS,
            memtopAddress = 0xffffu
        )
    }

    test("augmented assign on arrays") {
//main {
//    sub start() {
//        ubyte[] particleX = [1,2,3]
//        ubyte[] particleDX = [1,2,3]
//        particleX[2] += particleDX[2]
//
//        word @shared xx = 1
//        xx = -xx
//        xx += 42
//        xx += cx16.r0
//    }
//}
        val codegen = AsmGen6502(prefixSymbols = false, 0)
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val block = PtBlock("main",false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val sub = PtSub("start", emptyList(), emptyList(), Position.DUMMY)
        sub.add(PtVariable(
            "pi",
            DataType.UBYTE,
            ZeropageWish.DONTCARE,
            0u,
            null,
            null,
            Position.DUMMY
        ))
        sub.add(PtVariable(
            "particleX",
            DataType.arrayFor(BaseDataType.UBYTE),
            ZeropageWish.DONTCARE,
            0u,
            null,
            3u,
            Position.DUMMY
        ))
        sub.add(PtVariable(
            "particleDX",
            DataType.arrayFor(BaseDataType.UBYTE),
            ZeropageWish.DONTCARE,
            0u,
            null,
            3u,
            Position.DUMMY
        ))
        sub.add(PtVariable(
            "xx",
            DataType.WORD,
            ZeropageWish.DONTCARE,
            0u,
            null,
            null,
            Position.DUMMY
        ))

        val assign = PtAugmentedAssign("+=", Position.DUMMY)
        val target = PtAssignTarget(false, Position.DUMMY).also {
            val targetIdx = PtArrayIndexer(DataType.UBYTE, Position.DUMMY).also { idx ->
                idx.add(PtIdentifier("main.start.particleX", DataType.arrayFor(BaseDataType.UBYTE), Position.DUMMY))
                idx.add(PtNumber(BaseDataType.UBYTE, 2.0, Position.DUMMY))
            }
            it.add(targetIdx)
        }
        val value = PtArrayIndexer(DataType.UBYTE, Position.DUMMY)
        value.add(PtIdentifier("main.start.particleDX", DataType.arrayFor(BaseDataType.UBYTE), Position.DUMMY))
        value.add(PtNumber(BaseDataType.UBYTE, 2.0, Position.DUMMY))
        assign.add(target)
        assign.add(value)
        sub.add(assign)

        val prefixAssign = PtAugmentedAssign("-", Position.DUMMY)
        val prefixTarget = PtAssignTarget(false, Position.DUMMY).also {
            it.add(PtIdentifier("main.start.xx", DataType.WORD, Position.DUMMY))
        }
        prefixAssign.add(prefixTarget)
        prefixAssign.add(PtIdentifier("main.start.xx", DataType.WORD, Position.DUMMY))
        sub.add(prefixAssign)

        val numberAssign = PtAugmentedAssign("-=", Position.DUMMY)
        val numberAssignTarget = PtAssignTarget(false, Position.DUMMY).also {
            it.add(PtIdentifier("main.start.xx", DataType.WORD, Position.DUMMY))
        }
        numberAssign.add(numberAssignTarget)
        numberAssign.add(PtNumber(BaseDataType.WORD, 42.0, Position.DUMMY))
        sub.add(numberAssign)

        val cxregAssign = PtAugmentedAssign("+=", Position.DUMMY)
        val cxregAssignTarget = PtAssignTarget(false, Position.DUMMY).also {
            it.add(PtIdentifier("main.start.xx", DataType.WORD, Position.DUMMY))
        }
        cxregAssign.add(cxregAssignTarget)
        cxregAssign.add(PtIdentifier("cx16.r0", DataType.UWORD, Position.DUMMY))
        sub.add(cxregAssign)

        block.add(sub)
        program.add(block)

        // define the "cx16.r0" virtual register
        val cx16block = PtBlock("cx16", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        cx16block.add(PtMemMapped("r0", DataType.UWORD, 100u, null, Position.DUMMY))
        program.add(cx16block)

        val options = getTestOptions()
        val st = SymbolTableMaker(program, options).make()
        val errors = ErrorReporterForTests()
        val result = codegen.generate(program, st, options, errors)!!
        result.name shouldBe "test"
        Files.deleteIfExists(Path("${result.name}.asm"))
    }

    test("64tass assembler available? - if this fails you need to install 64tass version 1.58 or newer in the path") {
        val command = mutableListOf("64tass", "--version")
        shouldNotThrowAny {
            val proc = ProcessBuilder(command).start()
            val output = String(proc.inputStream.readBytes())
            val result = proc.waitFor()
            result.shouldBe(0)
            val (_, version) = output.split('V')
            val (major, minor, _) = version.split('.')
            val majorNum = major.toInt()
            val minorNum = minor.toInt()
            withClue("64tass version should be 1.58 or newer") {
                majorNum shouldBeGreaterThanOrEqual 1
                if (majorNum == 1)
                    minorNum shouldBeGreaterThanOrEqual 58
            }
        }
    }

    test("memory mapped zp var is correctly considered to be zp var") {
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val st = SymbolTable(program)
        st.add(StMemVar("zpmemvar", DataType.WORD, 0x20u, null, null))
        st.add(StMemVar("normalmemvar", DataType.WORD, 0x9000u, null, null))
        val allocator = VariableAllocator(st, getTestOptions(), ErrorReporterForTests())
        allocator.isZpVar("zpmemvar") shouldBe true
        allocator.isZpVar("normalmemvar") shouldBe false
    }
})

