package prog8tests

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.ast.base.DataType
import prog8.ast.expressions.Expression
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.Subroutine
import prog8.codegen.target.C64Target
import prog8.codegen.target.Cx16Target
import prog8.codegen.target.c64.C64Zeropage
import prog8.codegen.target.cx16.CX16Zeropage
import prog8.compilerinterface.*
import prog8tests.helpers.ErrorReporterForTests


class TestAbstractZeropage: FunSpec({

    class DummyCompilationTarget: ICompilationTarget {
        override val name: String = "dummy"
        override val machine: IMachineDefinition
            get() = throw NotImplementedError("dummy")

        override fun encodeString(str: String, altEncoding: Boolean): List<UByte> {
            throw NotImplementedError("dummy")
        }

        override fun decodeString(bytes: List<UByte>, altEncoding: Boolean): String {
            throw NotImplementedError("dummy")
        }

        override fun asmsubArgsEvalOrder(sub: Subroutine): List<Int> {
            throw NotImplementedError("dummy")
        }

        override fun asmsubArgsHaveRegisterClobberRisk(args: List<Expression>,
                                                       paramRegisters: List<RegisterOrStatusflag>): Boolean {
            throw NotImplementedError("dummy")
        }

        override fun memorySize(dt: DataType): Int {
            throw NotImplementedError("dummy")
        }

    }

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
        val compTarget = DummyCompilationTarget()
        val zp = DummyZeropage(
            CompilationOptions(
                OutputType.RAW,
                LauncherType.NONE,
                ZeropageType.FULL,
                listOf((0x50u..0x5fu)),
                false,
                false,
                compTarget
            )
        )
        zp.free.size shouldBe 256-6-16
    }

})


class TestC64Zeropage: FunSpec({

    val errors = ErrorReporterForTests()

    test("testNames") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false, C64Target))

        zp.allocate("", DataType.UBYTE, null, errors)
        zp.allocate("", DataType.UBYTE, null, errors)
        zp.allocate("varname", DataType.UBYTE, null, errors)
        shouldThrow<IllegalArgumentException> {
            zp.allocate("varname", DataType.UBYTE, null, errors)
        }
        zp.allocate("varname2", DataType.UBYTE, null, errors)
    }

    test("testZpFloatEnable") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, C64Target))
        shouldThrow<InternalCompilerException> {
            zp.allocate("", DataType.FLOAT, null, errors)
        }
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.DONTUSE, emptyList(), true, false, C64Target))
        shouldThrow<InternalCompilerException> {
            zp2.allocate("", DataType.FLOAT, null, errors)
        }
        val zp3 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), true, false, C64Target))
        zp3.allocate("", DataType.FLOAT, null, errors)
    }

    test("testZpModesWithFloats") {
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, C64Target))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false, C64Target))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false, C64Target))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), false, false, C64Target))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, C64Target))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), true, false, C64Target))
        shouldThrow<InternalCompilerException> {
            C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), true, false, C64Target))
        }
        shouldThrow<InternalCompilerException> {
            C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), true, false, C64Target))
        }
    }

    test("testZpDontuse") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.DONTUSE, emptyList(), false, false, C64Target))
        println(zp.free)
        zp.availableBytes() shouldBe 0
        shouldThrow<InternalCompilerException> {
            zp.allocate("", DataType.BYTE, null, errors)
        }
    }

    test("testFreeSpacesBytes") {
        val zp1 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, C64Target))
        zp1.availableBytes() shouldBe 18
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), false, false, C64Target))
        zp2.availableBytes() shouldBe 85
        val zp3 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false, C64Target))
        zp3.availableBytes() shouldBe 125
        val zp4 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, C64Target))
        zp4.availableBytes() shouldBe 239
        zp4.allocate("test", DataType.UBYTE, null, errors)
        zp4.availableBytes() shouldBe 238
        zp4.allocate("test2", DataType.UBYTE, null, errors)
        zp4.availableBytes() shouldBe 237
    }

    test("testReservedSpace") {
        val zp1 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, C64Target))
        zp1.availableBytes() shouldBe 239
        50u shouldBeIn zp1.free
        100u shouldBeIn zp1.free
        49u shouldBeIn zp1.free
        101u shouldBeIn zp1.free
        200u shouldBeIn zp1.free
        255u shouldBeIn zp1.free
        199u shouldBeIn zp1.free
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, listOf(50u .. 100u, 200u..255u), false, false, C64Target))
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
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, C64Target))
        zp.availableBytes() shouldBe 18
        zp.hasByteAvailable() shouldBe true
        zp.hasWordAvailable() shouldBe true

        shouldThrow<ZeropageDepletedError> {
            // in regular zp there aren't 5 sequential bytes free
            zp.allocate("", DataType.FLOAT, null, errors)
        }

        for (i in 0 until zp.availableBytes()) {
            val loc = zp.allocate("", DataType.UBYTE, null, errors)
            loc shouldBeGreaterThan 0u
        }
        zp.availableBytes() shouldBe 0
        zp.hasByteAvailable() shouldBe false
        zp.hasWordAvailable() shouldBe false
        shouldThrow<ZeropageDepletedError> {
            zp.allocate("", DataType.UBYTE, null, errors)
        }
        shouldThrow<ZeropageDepletedError> {
            zp.allocate("", DataType.UWORD, null, errors)
        }
    }

    test("testFullAllocation") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, C64Target))
        zp.availableBytes() shouldBe 239
        zp.hasByteAvailable() shouldBe true
        zp.hasWordAvailable() shouldBe true
        val loc = zp.allocate("", DataType.UWORD, null, errors)
        loc shouldBeGreaterThan 3u
        loc shouldNotBeIn zp.free
        val num = zp.availableBytes() / 2

        for(i in 0..num-3) {
            zp.allocate("", DataType.UWORD, null, errors)
        }
        zp.availableBytes() shouldBe 5

        shouldThrow<ZeropageDepletedError> {
            // can't allocate because no more sequential bytes, only fragmented
            zp.allocate("", DataType.UWORD, null, errors)
        }

        for(i in 0..4) {
            zp.allocate("", DataType.UBYTE, null, errors)
        }

        zp.availableBytes() shouldBe 0
        zp.hasByteAvailable() shouldBe false
        zp.hasWordAvailable() shouldBe false
        shouldThrow<ZeropageDepletedError> {
            // no more space
            zp.allocate("", DataType.UBYTE, null, errors)
        }
    }

    test("testEfficientAllocation") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(),  true, false, C64Target))
        zp.availableBytes() shouldBe 18
        zp.allocate("", DataType.WORD, null, errors) shouldBe 0x04u
        zp.allocate("", DataType.UBYTE, null, errors) shouldBe 0x06u
        zp.allocate("", DataType.UBYTE, null, errors) shouldBe 0x0au
        zp.allocate("", DataType.UWORD, null, errors) shouldBe 0x9bu
        zp.allocate("", DataType.UWORD, null, errors) shouldBe 0x9eu
        zp.allocate("", DataType.UWORD, null, errors) shouldBe 0xa5u
        zp.allocate("", DataType.UWORD, null, errors) shouldBe 0xb0u
        zp.allocate("", DataType.UWORD, null, errors) shouldBe 0xbeu
        zp.allocate("", DataType.UBYTE, null, errors) shouldBe 0x0eu
        zp.allocate("", DataType.UBYTE, null, errors) shouldBe 0x92u
        zp.allocate("", DataType.UBYTE, null, errors) shouldBe 0x96u
        zp.allocate("", DataType.UBYTE, null, errors) shouldBe 0xf9u
        zp.availableBytes() shouldBe 0
    }

    test("testReservedLocations") {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false, C64Target))
        withClue("zp _B1 and _REG must be next to each other to create a word") {
            zp.SCRATCH_B1 + 1u shouldBe zp.SCRATCH_REG
        }
    }
})


class TestCx16Zeropage: FunSpec({
    val errors = ErrorReporterForTests()

    test("testReservedLocations") {
        val zp = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false, Cx16Target))
        withClue("zp _B1 and _REG must be next to each other to create a word") {
            zp.SCRATCH_B1 + 1u shouldBe zp.SCRATCH_REG
        }
    }

    test("testFreeSpacesBytes") {
        val zp1 = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, Cx16Target))
        zp1.availableBytes() shouldBe 88
        val zp2 = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false, Cx16Target))
        zp2.availableBytes() shouldBe 175
        val zp3 = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, Cx16Target))
        zp3.availableBytes() shouldBe 216
        zp3.allocate("test", DataType.UBYTE, null, errors)
        zp3.availableBytes() shouldBe 215
        zp3.allocate("test2", DataType.UBYTE, null, errors)
        zp3.availableBytes() shouldBe 214
    }

    test("testReservedSpace") {
        val zp1 = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, Cx16Target))
        zp1.availableBytes() shouldBe 216
        0x22u shouldBeIn zp1.free
        0x80u shouldBeIn zp1.free
        0xffu shouldBeIn zp1.free
        0x02u shouldNotBeIn zp1.free
        0x21u shouldNotBeIn zp1.free
    }
})
