package prog8tests

import com.github.michaelbull.result.expectError
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.ast.base.DataType
import prog8.codegen.target.C64Target
import prog8.codegen.target.Cx16Target
import prog8.codegen.target.c64.C64Zeropage
import prog8.codegen.target.cx16.CX16Zeropage
import prog8.compilerinterface.*
import prog8tests.helpers.DummyCompilationTarget
import prog8tests.helpers.ErrorReporterForTests


class TestAbstractZeropage: FunSpec({

    class DummyZeropage(options: CompilationOptions) : Zeropage(options) {
        override val SCRATCH_B1 = 0x10u
        override val SCRATCH_REG = 0x11u
        override val SCRATCH_W1 = 0x20u
        override val SCRATCH_W2 = 0x30u

        init {
            free.addAll(0u..255u)

            removeReservedFromFreePool()
        }
    }


    test("testAbstractZeropage") {
        val zp = DummyZeropage(
            CompilationOptions(
                OutputType.RAW,
                CbmPrgLauncherType.NONE,
                ZeropageType.FULL,
                listOf((0x50u..0x5fu)),
                false,
                false,
                DummyCompilationTarget
            )
        )
        zp.free.size shouldBe 256-6-16
    }

})


class TestC64Zeropage: FunSpec({

    val errors = ErrorReporterForTests()
    val c64target = C64Target()

    test("testNames") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false, c64target))

        var result = zp.allocate(emptyList(), DataType.UBYTE, null, null, null, errors)
        result.onFailure { fail(it.toString()) }
        result = zp.allocate(emptyList(), DataType.UBYTE, null, null, null, errors)
        result.onFailure { fail(it.toString()) }
        result = zp.allocate(listOf("varname"), DataType.UBYTE, null, null, null, errors)
        result.onFailure { fail(it.toString()) }
        shouldThrow<IllegalArgumentException> {  zp.allocate(listOf("varname"), DataType.UBYTE,null, null, null, errors) }
        result = zp.allocate(listOf("varname2"), DataType.UBYTE, null, null, null, errors)
        result.onFailure { fail(it.toString()) }
    }

    test("testZpFloatEnable") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, c64target))
        var result = zp.allocate(emptyList(), DataType.FLOAT, null, null, null, errors)
        result.expectError { "should be allocation error due to disabled floats" }
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.DONTUSE, emptyList(), true, false, c64target))
        result = zp2.allocate(emptyList(), DataType.FLOAT, null, null, null, errors)
        result.expectError { "should be allocation error due to disabled ZP use" }
        val zp3 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), true, false, c64target))
        zp3.allocate(emptyList(), DataType.FLOAT, null, null, null, errors)
    }

    test("testZpModesWithFloats") {
        C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, c64target))
        C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false, c64target))
        C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false, c64target))
        C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), false, false, c64target))
        C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, c64target))
        C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), true, false, c64target))
        shouldThrow<InternalCompilerException> {
            C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), true, false, c64target))
        }
        shouldThrow<InternalCompilerException> {
            C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), true, false, c64target))
        }
    }

    test("testZpDontuse") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.DONTUSE, emptyList(), false, false, c64target))
        println(zp.free)
        zp.availableBytes() shouldBe 0
        val result = zp.allocate(emptyList(), DataType.BYTE, null, null, null, errors)
        result.expectError { "expected error due to disabled ZP use" }
    }

    test("testFreeSpacesBytes") {
        val zp1 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, c64target))
        zp1.availableBytes() shouldBe 18
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), false, false, c64target))
        zp2.availableBytes() shouldBe 85
        val zp3 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false, c64target))
        zp3.availableBytes() shouldBe 125
        val zp4 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, c64target))
        zp4.availableBytes() shouldBe 239
        zp4.allocate(listOf("test"), DataType.UBYTE, null, null, null, errors)
        zp4.availableBytes() shouldBe 238
        zp4.allocate(listOf("test2"), DataType.UBYTE, null, null, null, errors)
        zp4.availableBytes() shouldBe 237
    }

    test("testReservedSpace") {
        val zp1 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, c64target))
        zp1.availableBytes() shouldBe 239
        50u shouldBeIn zp1.free
        100u shouldBeIn zp1.free
        49u shouldBeIn zp1.free
        101u shouldBeIn zp1.free
        200u shouldBeIn zp1.free
        255u shouldBeIn zp1.free
        199u shouldBeIn zp1.free
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, listOf(50u .. 100u, 200u..255u), false, false, c64target))
        zp2.availableBytes() shouldBe 139
        50u shouldNotBeIn zp2.free
        100u shouldNotBeIn zp2.free
        49u shouldBeIn zp2.free
        101u shouldBeIn zp2.free
        200u shouldNotBeIn zp2.free
        255u shouldNotBeIn zp2.free
        199u shouldBeIn zp2.free
    }

    test("testBasicsafeAllocation") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, c64target))
        zp.availableBytes() shouldBe 18
        zp.hasByteAvailable() shouldBe true
        zp.hasWordAvailable() shouldBe true

        var result = zp.allocate(emptyList(), DataType.FLOAT, null, null, null, errors)
        result.expectError { "expect allocation error: in regular zp there aren't 5 sequential bytes free" }

        for (i in 0 until zp.availableBytes()) {
            val alloc = zp.allocate(emptyList(), DataType.UBYTE, null, null, null, errors)
            alloc.getOrElse { throw it }
        }
        zp.availableBytes() shouldBe 0
        zp.hasByteAvailable() shouldBe false
        zp.hasWordAvailable() shouldBe false
        result = zp.allocate(emptyList(), DataType.UBYTE, null, null, null, errors)
        result.expectError { "expected allocation error" }
        result = zp.allocate(emptyList(), DataType.UWORD, null, null, null, errors)
        result.expectError { "expected allocation error" }
    }

    test("testFullAllocation") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, c64target))
        zp.availableBytes() shouldBe 239
        zp.hasByteAvailable() shouldBe true
        zp.hasWordAvailable() shouldBe true
        var result = zp.allocate(emptyList(), DataType.UWORD, null, null, null, errors)
        val loc = result.getOrElse { throw it } .first
        loc shouldBeGreaterThan 3u
        loc shouldNotBeIn zp.free
        val num = zp.availableBytes() / 2

        for(i in 0..num-3) {
            zp.allocate(emptyList(), DataType.UWORD, null, null, null, errors)
        }
        zp.availableBytes() shouldBe 5

        // can't allocate because no more sequential bytes, only fragmented
        result = zp.allocate(emptyList(), DataType.UWORD, null, null, null, errors)
        result.expectError { "should give allocation error" }

        for(i in 0..4) {
            zp.allocate(emptyList(), DataType.UBYTE, null, null, null, errors)
        }

        zp.availableBytes() shouldBe 0
        zp.hasByteAvailable() shouldBe false
        zp.hasWordAvailable() shouldBe false
        result = zp.allocate(emptyList(), DataType.UBYTE, null, null, null, errors)
        result.expectError { "should give allocation error" }
    }

    test("testEfficientAllocation") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(),  true, false, c64target))
        zp.availableBytes() shouldBe 18
        zp.allocate(emptyList(), DataType.WORD,  null, null, null, errors).getOrElse{throw it}.first shouldBe 0x04u
        zp.allocate(emptyList(), DataType.UBYTE, null, null, null, errors).getOrElse{throw it}.first shouldBe 0x06u
        zp.allocate(emptyList(), DataType.UBYTE, null, null, null, errors).getOrElse{throw it}.first shouldBe 0x0au
        zp.allocate(emptyList(), DataType.UWORD, null, null, null, errors).getOrElse{throw it}.first shouldBe 0x9bu
        zp.allocate(emptyList(), DataType.UWORD, null, null, null, errors).getOrElse{throw it}.first shouldBe 0x9eu
        zp.allocate(emptyList(), DataType.UWORD, null, null, null, errors).getOrElse{throw it}.first shouldBe 0xa5u
        zp.allocate(emptyList(), DataType.UWORD, null, null, null, errors).getOrElse{throw it}.first shouldBe 0xb0u
        zp.allocate(emptyList(), DataType.UWORD, null, null, null, errors).getOrElse{throw it}.first shouldBe 0xbeu
        zp.allocate(emptyList(), DataType.UBYTE, null, null, null, errors).getOrElse{throw it}.first shouldBe 0x0eu
        zp.allocate(emptyList(), DataType.UBYTE, null, null, null, errors).getOrElse{throw it}.first shouldBe 0x92u
        zp.allocate(emptyList(), DataType.UBYTE, null, null, null, errors).getOrElse{throw it}.first shouldBe 0x96u
        zp.allocate(emptyList(), DataType.UBYTE, null, null, null, errors).getOrElse{throw it}.first shouldBe 0xf9u
        zp.availableBytes() shouldBe 0
    }

    test("testReservedLocations") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false, c64target))
        withClue("zp _B1 and _REG must be next to each other to create a word") {
            zp.SCRATCH_B1 + 1u shouldBe zp.SCRATCH_REG
        }
    }
})


class TestCx16Zeropage: FunSpec({
    val errors = ErrorReporterForTests()
    val cx16target = Cx16Target()

    test("testReservedLocations") {
        val zp = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false, cx16target))
        withClue("zp _B1 and _REG must be next to each other to create a word") {
            zp.SCRATCH_B1 + 1u shouldBe zp.SCRATCH_REG
        }
    }

    test("testFreeSpacesBytes") {
        val zp1 = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, cx16target))
        zp1.availableBytes() shouldBe 88
        val zp2 = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false, cx16target))
        zp2.availableBytes() shouldBe 175
        val zp3 = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, cx16target))
        zp3.availableBytes() shouldBe 216
        zp3.allocate(listOf("test"), DataType.UBYTE, null, null, null, errors)
        zp3.availableBytes() shouldBe 215
        zp3.allocate(listOf("test2"), DataType.UBYTE, null, null, null, errors)
        zp3.availableBytes() shouldBe 214
    }

    test("testReservedSpace") {
        val zp1 = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, cx16target))
        zp1.availableBytes() shouldBe 216
        0x22u shouldBeIn zp1.free
        0x80u shouldBeIn zp1.free
        0xffu shouldBeIn zp1.free
        0x02u shouldNotBeIn zp1.free
        0x21u shouldNotBeIn zp1.free
    }

    test("preallocated zp vars") {
        val zp1 = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, cx16target))
        zp1.allocatedVariables[listOf("test")] shouldBe null
        zp1.allocatedVariables[listOf("cx16", "r0")] shouldNotBe null
        zp1.allocatedVariables[listOf("cx16", "r15")] shouldNotBe null
        zp1.allocatedVariables[listOf("cx16", "r0L")] shouldNotBe null
        zp1.allocatedVariables[listOf("cx16", "r15L")] shouldNotBe null
        zp1.allocatedVariables[listOf("cx16", "r0sH")] shouldNotBe null
        zp1.allocatedVariables[listOf("cx16", "r15sH")] shouldNotBe null
    }
})
