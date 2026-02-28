package prog8tests.compiler

import com.github.michaelbull.result.expectError
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.core.*
import prog8.code.target.C128Target
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.PETTarget
import prog8.code.target.zp.C128Zeropage
import prog8.code.target.zp.C64Zeropage
import prog8.code.target.zp.CX16Zeropage
import prog8.code.target.zp.PETZeropage
import prog8tests.helpers.ErrorReporterForTests


class TestAbstractZeropage: FunSpec({

    class DummyZeropage(options: CompilationOptions) : Zeropage(options) {
        override val SCRATCH_B1 = 0x10u
        override val SCRATCH_REG = 0x11u
        override val SCRATCH_W1 = 0x20u
        override val SCRATCH_W2 = 0x30u
        override val SCRATCH_PTR = 0x40u

        init {
            free.addAll(0u..255u)

            removeReservedFromFreePool()
            retainAllowed()
        }
    }


    test("testAbstractZeropage") {
        val zp = DummyZeropage(
            CompilationOptions(
                OutputType.RAW,
                CbmPrgLauncherType.NONE,
                ZeropageType.FULL,
                listOf((0x50u..0x5fu)),
                CompilationOptions.AllZeropageAllowed,
                floats = false,
                noSysInit = false,
                romable = false,
                compTarget = C64Target(),
                compilerVersion="99.99",
                loadAddress = 999u,
                memtopAddress = 0xffffu
            )
        )
        zp.free.size shouldBe 256-8-16
    }

})


class TestC64Zeropage: FunSpec({

    val errors = ErrorReporterForTests()
    val c64target = C64Target()

    test("testNames") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed,
            floats = false,
            noSysInit = false,
            romable = false,
            compTarget = c64target, compilerVersion="99.99", loadAddress = 999u, memtopAddress = 0xffffu
        ))

        var result = zp.allocate("", DataType.UBYTE, null, null, errors)
        result.onFailure { error(it.toString()) }
        result = zp.allocate("", DataType.UBYTE, null, null, errors)
        result.onFailure { error(it.toString()) }
        result = zp.allocate("varname", DataType.UBYTE, null, null, errors)
        result.onFailure { error(it.toString()) }
        shouldThrow<IllegalArgumentException> {  zp.allocate("varname", DataType.UBYTE,null, null, errors) }
        result = zp.allocate("varname2", DataType.UBYTE, null, null, errors)
        result.onFailure { error(it.toString()) }
    }

    test("testZpFloatEnable") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        var result = zp.allocate("", DataType.FLOAT, null, null, errors)
        result.expectError { "should be allocation error due to disabled floats" }
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.DONTUSE, emptyList(), CompilationOptions.AllZeropageAllowed, true, false, false, c64target, "99.99", 999u, 0xffffu))
        result = zp2.allocate("", DataType.FLOAT, null, null, errors)
        result.expectError { "should be allocation error due to disabled ZP use" }
        val zp3 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, true, false, false, c64target, "99.99", 999u, 0xffffu))
        zp3.allocate("", DataType.FLOAT, null, null, errors)
    }

    test("testZpModesWithFloats") {
        C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, true, false, false, c64target, "99.99", 999u, 0xffffu))
        C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, true, false, false, c64target, "99.99", 999u, 0xffffu))
        shouldThrow<InternalCompilerException> {
            C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), CompilationOptions.AllZeropageAllowed, true, false, false, c64target, "99.99", 999u, 0xffffu))
        }
        shouldThrow<InternalCompilerException> {
            C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, true, false, false, c64target, "99.99", 999u, 0xffffu))
        }
    }

    test("testZpDontuse") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.DONTUSE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        println(zp.free)
        zp.availableBytes() shouldBe 0
        val result = zp.allocate("", DataType.BYTE, null, null, errors)
        result.expectError { "expected error due to disabled ZP use" }
    }

    test("testFreeSpacesBytes") {
        val zp1 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, true, false, false, c64target, "99.99", 999u, 0xffffu))
        zp1.availableBytes() shouldBe 14
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        zp2.availableBytes() shouldBe 84
        val zp3 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        zp3.availableBytes() shouldBe 95
        val zp4 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        zp4.availableBytes() shouldBe 207
    }

    test("testReservedSpace") {
        val zp1 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        zp1.availableBytes() shouldBe 207
        4u shouldNotBeIn zp1.free
        5u shouldNotBeIn zp1.free
        6u shouldNotBeIn zp1.free
        35u shouldNotBeIn zp1.free
        50u shouldBeIn zp1.free
        100u shouldBeIn zp1.free
        49u shouldBeIn zp1.free
        101u shouldBeIn zp1.free
        200u shouldBeIn zp1.free
        255u shouldBeIn zp1.free
        199u shouldBeIn zp1.free
        0x9b shouldNotBeIn zp1.free
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, listOf(50u .. 100u, 200u..255u), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        zp2.availableBytes() shouldBe 107
        4u shouldNotBeIn zp2.free
        5u shouldNotBeIn zp2.free
        6u shouldNotBeIn zp2.free
        35u shouldNotBeIn zp2.free
        50u shouldNotBeIn zp2.free
        100u shouldNotBeIn zp2.free
        49u shouldBeIn zp2.free
        101u shouldBeIn zp2.free
        200u shouldNotBeIn zp2.free
        255u shouldNotBeIn zp2.free
        199u shouldBeIn zp2.free
        0x9b shouldNotBeIn zp2.free
        val zp3 = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FLOATSAFE, listOf(50u .. 100u, 200u..255u), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        zp2.availableBytes() shouldBe 107
        4u shouldNotBeIn zp3.free
        5u shouldNotBeIn zp3.free
        6u shouldBeIn zp3.free
        35u shouldNotBeIn zp3.free
        0x9b shouldNotBeIn zp3.free
    }

    test("testBasicsafeAllocation") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, true, false, false, c64target, "99.99", 999u, 0xffffu))
        zp.availableBytes() shouldBe 14
        zp.hasByteAvailable() shouldBe true
        zp.hasWordAvailable() shouldBe true

        var result = zp.allocate("", DataType.FLOAT, null, null, errors)
        result.expectError { "expect allocation error: in regular zp there aren't 5 sequential bytes free" }

        (0 until zp.availableBytes()).forEach {
            val alloc = zp.allocate("", DataType.UBYTE, null, null, errors)
            alloc.getOrElse { throw it }
        }
        zp.availableBytes() shouldBe 0
        zp.hasByteAvailable() shouldBe false
        zp.hasWordAvailable() shouldBe false
        result = zp.allocate("", DataType.UBYTE, null, null, errors)
        result.expectError { "expected allocation error" }
        result = zp.allocate("", DataType.UWORD, null, null, errors)
        result.expectError { "expected allocation error" }
    }

    test("testFullAllocation") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        zp.availableBytes() shouldBe 207
        zp.hasByteAvailable() shouldBe true
        zp.hasWordAvailable() shouldBe true
        var result = zp.allocate("", DataType.UWORD, null, null, errors)
        zp.availableBytes() shouldBe 205
        val loc = result.getOrElse { throw it } .address
        loc shouldBeGreaterThan 3u
        loc shouldNotBeIn zp.free
        val num = zp.availableBytes() / 2

        (0..num).forEach {
            zp.allocate("", DataType.UWORD, null, null, errors)
        }
        zp.availableBytes() shouldBe 5

        // can't allocate because no more sequential bytes, only fragmented
        result = zp.allocate("", DataType.UWORD, null, null, errors)
        result.expectError { "should give allocation error" }

        (0..10).forEach {
            zp.allocate("", DataType.UBYTE, null, null, errors)
        }

        zp.availableBytes() shouldBe 0
        zp.hasByteAvailable() shouldBe false
        zp.hasWordAvailable() shouldBe false
        result = zp.allocate("", DataType.UBYTE, null, null, errors)
        result.expectError { "should give allocation error" }
    }

    test("testEfficientAllocation") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed,  true, false, false, c64target, "99.99", 999u, 0xffffu))
        zp.availableBytes() shouldBe 14
        zp.allocate("", DataType.WORD,  null, null, errors).getOrElse{throw it}.address shouldBe 0x9eu
        zp.allocate("", DataType.UBYTE, null, null, errors).getOrElse{throw it}.address shouldBe 0x06u
        zp.allocate("", DataType.UBYTE, null, null, errors).getOrElse{throw it}.address shouldBe 0x0au
        zp.allocate("", DataType.UWORD, null, null, errors).getOrElse{throw it}.address shouldBe 0xb0u
        zp.allocate("", DataType.UWORD, null, null, errors).getOrElse{throw it}.address shouldBe 0xbeu
        zp.allocate("", DataType.UBYTE, null, null, errors).getOrElse{throw it}.address shouldBe 0x0eu
        zp.allocate("", DataType.UBYTE, null, null, errors).getOrElse{throw it}.address shouldBe 0x92u
        zp.allocate("", DataType.UBYTE, null, null, errors).getOrElse{throw it}.address shouldBe 0x96u
        zp.allocate("", DataType.UBYTE, null, null, errors).getOrElse{throw it}.address shouldBe 0x9cu
        zp.allocate("", DataType.UBYTE, null, null, errors).getOrElse{throw it}.address shouldBe 0xa6u
        zp.allocate("", DataType.UBYTE, null, null, errors).getOrElse{throw it}.address shouldBe 0xf9u
        zp.availableBytes() shouldBe 0
    }

    test("testReservedLocations") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        withClue("zp _B1 and _REG must be next to each other to create a word") {
            zp.SCRATCH_B1 + 1u shouldBe zp.SCRATCH_REG
        }
    }

    test("virtual registers") {
        val zpbasic = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        zpbasic.allocatedVariables.any {it.key=="cx16.r0"} shouldBe false

        val zpkernal = C64Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, c64target, "99.99", 999u, 0xffffu))
        zpkernal.allocatedVariables.any {it.key=="cx16.r0"} shouldBe true
        val r0bL = zpkernal.allocatedVariables.getValue("cx16.r0bL")
        r0bL.size shouldBe 1
        r0bL.dt shouldBe DataType.BOOL
        r0bL.address shouldBe 4u
        val r14r15sl = zpkernal.allocatedVariables.getValue("cx16.r14r15sl")
        r14r15sl.size shouldBe 4
        r14r15sl.dt shouldBe DataType.LONG
        r14r15sl.address shouldBe 32u
    }
})

class TestPetZeropage: FunSpec({
    test("virtual registers") {
        val zpbasic = PETZeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, PETTarget(), "99.99", 999u, 0xffffu))
        zpbasic.allocatedVariables.any {it.key=="cx16.r0"} shouldBe false

        val zpkernal = PETZeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, PETTarget(), "99.99", 999u, 0xffffu))
        zpkernal.allocatedVariables.any {it.key=="cx16.r0"} shouldBe true
        val r0bL = zpkernal.allocatedVariables.getValue("cx16.r0bL")
        r0bL.size shouldBe 1
        r0bL.dt shouldBe DataType.BOOL
        r0bL.address shouldBe 4u
        val r14r15sl = zpkernal.allocatedVariables.getValue("cx16.r14r15sl")
        r14r15sl.size shouldBe 4
        r14r15sl.dt shouldBe DataType.LONG
        r14r15sl.address shouldBe 32u
    }
})

class TestC128Zeropage: FunSpec({
    test("virtual registers") {
        val zpbasic = C128Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, C128Target(), "99.99", 999u, 0xffffu))
        zpbasic.allocatedVariables.any {it.key=="cx16.r0"} shouldBe false

        val zpkernal = C128Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, C128Target(), "99.99", 999u, 0xffffu))
        zpkernal.allocatedVariables.any {it.key=="cx16.r0"} shouldBe true
        val r0bL = zpkernal.allocatedVariables.getValue("cx16.r0bL")
        r0bL.size shouldBe 1
        r0bL.dt shouldBe DataType.BOOL
        r0bL.address shouldBe 10u
        val r14r15sl = zpkernal.allocatedVariables.getValue("cx16.r14r15sl")
        r14r15sl.size shouldBe 4
        r14r15sl.dt shouldBe DataType.LONG
        r14r15sl.address shouldBe 38u
    }
})

class TestCx16Zeropage: FunSpec({
    val errors = ErrorReporterForTests()
    val cx16target = Cx16Target()

    test("testReservedLocations") {
        val zp = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, cx16target, "99.99", 999u, 0xffffu))
        withClue("zp _B1 and _REG must be next to each other to create a word") {
            zp.SCRATCH_B1 + 1u shouldBe zp.SCRATCH_REG
        }
    }

    test("testFreeSpacesBytes") {
        val zp1 = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, true, false, false, cx16target, "99.99", 999u, 0xffffu))
        zp1.availableBytes() shouldBe 86
        val zp2 = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, cx16target, "99.99", 999u, 0xffffu))
        zp2.availableBytes() shouldBe 173
        val zp3 = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, cx16target, "99.99", 999u, 0xffffu))
        zp3.availableBytes() shouldBe 214
        zp3.allocate("test", DataType.UBYTE, null, null, errors)
        zp3.availableBytes() shouldBe 213
        zp3.allocate("test2", DataType.UBYTE, null, null, errors)
        zp3.availableBytes() shouldBe 212
    }

    test("testReservedSpace") {
        val zp1 = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, cx16target, "99.99", 999u, 0xffffu))
        zp1.availableBytes() shouldBe 214
        0x22u shouldNotBeIn zp1.free
        0x23u shouldNotBeIn zp1.free
        0x24u shouldBeIn zp1.free
        0x80u shouldBeIn zp1.free
        0xffu shouldBeIn zp1.free
        0x02u shouldNotBeIn zp1.free
        0x21u shouldNotBeIn zp1.free
    }

    test("preallocated zp vars") {
        val zp1 = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, cx16target, "99.99", 999u, 0xffffu))
        zp1.allocatedVariables["test"] shouldBe null
        zp1.allocatedVariables["cx16.r0"] shouldNotBe null
        zp1.allocatedVariables["cx16.r15"] shouldNotBe null
        zp1.allocatedVariables["cx16.r0L"] shouldNotBe null
        zp1.allocatedVariables["cx16.r15L"] shouldNotBe null
        zp1.allocatedVariables["cx16.r0sH"] shouldNotBe null
        zp1.allocatedVariables["cx16.r15sH"] shouldNotBe null
    }

    test("virtual registers") {
        val zpbasic = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, Cx16Target(), "99.99", 999u, 0xffffu))
        zpbasic.allocatedVariables.any {it.key=="cx16.r0"} shouldBe true

        val zpkernal = CX16Zeropage(CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), CompilationOptions.AllZeropageAllowed, false, false, false, Cx16Target(), "99.99", 999u, 0xffffu))
        zpkernal.allocatedVariables.any {it.key=="cx16.r0"} shouldBe true
        val r0bL = zpkernal.allocatedVariables.getValue("cx16.r0bL")
        r0bL.size shouldBe 1
        r0bL.dt shouldBe DataType.BOOL
        r0bL.address shouldBe 2u
        val r14r15sl = zpkernal.allocatedVariables.getValue("cx16.r14r15sl")
        r14r15sl.size shouldBe 4
        r14r15sl.dt shouldBe DataType.LONG
        r14r15sl.address shouldBe 30u
    }
})
