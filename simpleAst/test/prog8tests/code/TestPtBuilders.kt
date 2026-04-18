package prog8tests.code

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import prog8.code.ast.*
import prog8.code.core.*

class TestPtBuilders : FunSpec({

    test("PtVariable builder") {
        val pos = Position.DUMMY
        val variable = PtVariable.builder("x", DataType.UBYTE, pos)
            .zeropage(ZeropageWish.REQUIRE_ZEROPAGE)
            .align(2u)
            .dirty(true)
            .arraySize(10u)
            .build()

        variable.name shouldBe "x"
        variable.type shouldBe DataType.UBYTE
        variable.zeropage shouldBe ZeropageWish.REQUIRE_ZEROPAGE
        variable.align shouldBe 2u
        variable.dirty shouldBe true
        variable.arraySize shouldBe 10u
        variable.position shouldBe pos
    }

    test("PtAsmSub builder") {
        val pos = Position.DUMMY
        val sub = PtAsmSub.builder("my_asm_sub", pos)
            .clobbers(setOf(CpuRegister.A, CpuRegister.X))
            .inline(true)
            .addReturn(RegisterOrStatusflag(RegisterOrPair.A, null), DataType.UBYTE)
            .build()

        sub.name shouldBe "my_asm_sub"
        sub.clobbers shouldBe setOf(CpuRegister.A, CpuRegister.X)
        sub.inline shouldBe true
        sub.returns shouldHaveSize 1
        sub.returns[0].first.registerOrPair shouldBe RegisterOrPair.A
        sub.returns[0].second shouldBe DataType.UBYTE
    }

    test("PtConstant builder") {
        val pos = Position.DUMMY
        val constant = PtConstant.builder("MY_CONST", DataType.FLOAT, pos)
            .value(3.14)
            .build()

        constant.name shouldBe "MY_CONST"
        constant.type shouldBe DataType.FLOAT
        constant.value shouldBe 3.14
        constant.memorySlab shouldBe null
    }

    test("PtMemMapped builder") {
        val pos = Position.DUMMY
        val mem = PtMemMapped.builder("SCREEN", DataType.UBYTE, 0x0400u, pos)
            .arraySize(1000u)
            .build()

        mem.name shouldBe "SCREEN"
        mem.type shouldBe DataType.UBYTE
        mem.address shouldBe 0x0400u
        mem.arraySize shouldBe 1000u
    }

    test("PtSub builder") {
        val pos = Position.DUMMY
        val sub = PtSub.builder("my_sub", pos)
            .addReturntype(DataType.UWORD)
            .addParameter(PtSubroutineParameter("p1", DataType.UBYTE, null, pos))
            .build()

        sub.name shouldBe "my_sub"
        sub.signature.returns shouldBe listOf(DataType.UWORD)
        sub.signature.children shouldHaveSize 1
        (sub.signature.children[0] as PtSubroutineParameter).name shouldBe "p1"
    }

    test("PtStructDecl builder") {
        val pos = Position.DUMMY
        val struct = PtStructDecl.builder("Point", pos)
            .addField(DataType.UBYTE, "x")
            .addField(DataType.UBYTE, "y")
            .build()

        struct.name shouldBe "Point"
        struct.fields shouldHaveSize 2
        struct.fields[0] shouldBe (DataType.UBYTE to "x")
        struct.fields[1] shouldBe (DataType.UBYTE to "y")
    }
})
