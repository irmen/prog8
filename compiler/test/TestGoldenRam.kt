package prog8tests.compiler

import com.github.michaelbull.result.expectError
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.*
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests


class TestGoldenRam: FunSpec({

    val options = CompilationOptions(
        OutputType.RAW,
        CbmPrgLauncherType.NONE,
        ZeropageType.FULL,
        listOf((0x00u..0xffu)),
        CompilationOptions.AllZeropageAllowed,
        floats = true,
        noSysInit = false,
        compTarget = VMTarget(),
        loadAddress = 999u,
        memtopAddress = 0xffffu
    )

    test("empty golden ram allocations") {
        val errors = ErrorReporterForTests()
        val golden = GoldenRam(options, UIntRange.EMPTY)
        val result = golden.allocate("test", DataType.forDt(BaseDataType.UBYTE), null, null, errors)
        result.expectError { "should not be able to allocate anything" }
    }

    test("regular golden ram allocations") {
        val errors = ErrorReporterForTests()
        val golden = GoldenRam(options, 0x400u until 0x800u)

        var result = golden.allocate("test", DataType.forDt(BaseDataType.UBYTE), null, null, errors)
        var alloc = result.getOrThrow()
        alloc.size shouldBe 1
        alloc.address shouldBe 0x400u
        result = golden.allocate("test", DataType.forDt(BaseDataType.STR), 100, null, errors)
        alloc = result.getOrThrow()
        alloc.size shouldBe 100
        alloc.address shouldBe 0x401u

        repeat(461) {
            result = golden.allocate("test", DataType.forDt(BaseDataType.UWORD), null, null, errors)
            alloc = result.getOrThrow()
            alloc.size shouldBe 2
        }

        result = golden.allocate("test", DataType.forDt(BaseDataType.UWORD), null, null, errors)
        result.expectError { "just 1 more byte available" }
        result = golden.allocate("test", DataType.forDt(BaseDataType.UBYTE), null, null, errors)
        alloc = result.getOrThrow()
        alloc.size shouldBe 1
        alloc.address shouldBe golden.region.last
        result = golden.allocate("test", DataType.forDt(BaseDataType.UBYTE), null, null, errors)
        result.expectError { "nothing more available" }

    }
})
