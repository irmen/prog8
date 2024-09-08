package prog8tests.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.ast.PtArray
import prog8.code.ast.PtNumber
import prog8.code.ast.PtString
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.Encoding
import prog8.code.core.Position


class TestPtNumber: FunSpec({

    fun sameValueAndType(lv1: PtNumber, lv2: PtNumber): Boolean {
        return lv1.type==lv2.type && lv1==lv2
    }

    val dummyPos = Position("test", 0, 0, 0)

    test("testIdentity") {
        val v = PtNumber(BaseDataType.UWORD, 12345.0, dummyPos)
        (v==v) shouldBe true
        (v != v) shouldBe false
        (v <= v) shouldBe true
        (v >= v) shouldBe true
        (v < v ) shouldBe false
        (v > v ) shouldBe false

        sameValueAndType(PtNumber(BaseDataType.UWORD, 12345.0, dummyPos), PtNumber(BaseDataType.UWORD, 12345.0, dummyPos)) shouldBe true
    }

    test("test truncating") {
        shouldThrow<IllegalArgumentException> {
            PtNumber(BaseDataType.BYTE, -2.345, dummyPos)
        }.message shouldContain "refused truncating"
        shouldThrow<IllegalArgumentException> {
            PtNumber(BaseDataType.BYTE, -2.6, dummyPos)
        }.message shouldContain "refused truncating"
        shouldThrow<IllegalArgumentException> {
            PtNumber(BaseDataType.UWORD, 2222.345, dummyPos)
        }.message shouldContain "refused truncating"
        PtNumber(BaseDataType.UBYTE, 2.0, dummyPos).number shouldBe 2.0
        PtNumber(BaseDataType.BYTE, -2.0, dummyPos).number shouldBe -2.0
        PtNumber(BaseDataType.UWORD, 2222.0, dummyPos).number shouldBe 2222.0
        PtNumber(BaseDataType.FLOAT, 123.456, dummyPos)
    }

    test("testEqualsAndNotEquals") {
        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) == PtNumber(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) == PtNumber(BaseDataType.UWORD, 100.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) == PtNumber(BaseDataType.FLOAT, 100.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 254.0, dummyPos) == PtNumber(BaseDataType.UBYTE, 254.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 12345.0, dummyPos) == PtNumber(BaseDataType.UWORD, 12345.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 12345.0, dummyPos) == PtNumber(BaseDataType.FLOAT, 12345.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 100.0, dummyPos) == PtNumber(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 22239.0, dummyPos) == PtNumber(BaseDataType.UWORD, 22239.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 9.99, dummyPos) == PtNumber(BaseDataType.FLOAT, 9.99, dummyPos)) shouldBe true

        sameValueAndType(PtNumber(BaseDataType.UBYTE, 100.0, dummyPos), PtNumber(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe true
        sameValueAndType(PtNumber(BaseDataType.UBYTE, 100.0, dummyPos), PtNumber(BaseDataType.UWORD, 100.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UBYTE, 100.0, dummyPos), PtNumber(BaseDataType.FLOAT, 100.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UWORD, 254.0, dummyPos), PtNumber(BaseDataType.UBYTE, 254.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UWORD, 12345.0, dummyPos), PtNumber(BaseDataType.UWORD, 12345.0, dummyPos)) shouldBe true
        sameValueAndType(PtNumber(BaseDataType.UWORD, 12345.0, dummyPos), PtNumber(BaseDataType.FLOAT, 12345.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.FLOAT, 100.0, dummyPos), PtNumber(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.FLOAT, 22239.0, dummyPos), PtNumber(BaseDataType.UWORD, 22239.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.FLOAT, 9.99, dummyPos), PtNumber(BaseDataType.FLOAT, 9.99, dummyPos)) shouldBe true

        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) != PtNumber(BaseDataType.UBYTE, 101.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) != PtNumber(BaseDataType.UWORD, 101.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) != PtNumber(BaseDataType.FLOAT, 101.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 245.0, dummyPos) != PtNumber(BaseDataType.UBYTE, 246.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 12345.0, dummyPos) != PtNumber(BaseDataType.UWORD, 12346.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 12345.0, dummyPos) != PtNumber(BaseDataType.FLOAT, 12346.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 9.99, dummyPos) != PtNumber(BaseDataType.UBYTE, 9.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 9.99, dummyPos) != PtNumber(BaseDataType.UWORD, 9.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 9.99, dummyPos) != PtNumber(BaseDataType.FLOAT, 9.0, dummyPos)) shouldBe true

        sameValueAndType(PtNumber(BaseDataType.UBYTE, 100.0, dummyPos), PtNumber(BaseDataType.UBYTE, 101.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UBYTE, 100.0, dummyPos), PtNumber(BaseDataType.UWORD, 101.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UBYTE, 100.0, dummyPos), PtNumber(BaseDataType.FLOAT, 101.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UWORD, 245.0, dummyPos), PtNumber(BaseDataType.UBYTE, 246.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UWORD, 12345.0, dummyPos), PtNumber(BaseDataType.UWORD, 12346.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UWORD, 12345.0, dummyPos), PtNumber(BaseDataType.FLOAT, 12346.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.FLOAT, 9.99, dummyPos), PtNumber(BaseDataType.UBYTE, 9.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.FLOAT, 9.99, dummyPos), PtNumber(BaseDataType.UWORD, 9.0, dummyPos)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.FLOAT, 9.99, dummyPos), PtNumber(BaseDataType.FLOAT, 9.0, dummyPos)) shouldBe false


    }

    test("testEqualsRef") {
        (PtString("hello", Encoding.PETSCII, dummyPos) == PtString("hello", Encoding.PETSCII, dummyPos)) shouldBe true
        (PtString("hello", Encoding.PETSCII, dummyPos) != PtString("bye", Encoding.PETSCII, dummyPos)) shouldBe true
        (PtString("hello", Encoding.SCREENCODES, dummyPos) == PtString("hello", Encoding.SCREENCODES, dummyPos)) shouldBe true
        (PtString("hello", Encoding.SCREENCODES, dummyPos) != PtString("bye", Encoding.SCREENCODES, dummyPos)) shouldBe true
        (PtString("hello", Encoding.SCREENCODES, dummyPos) != PtString("hello", Encoding.PETSCII, dummyPos)) shouldBe true

        val lvOne = PtNumber(BaseDataType.UBYTE, 1.0, dummyPos)
        val lvTwo = PtNumber(BaseDataType.UBYTE, 2.0, dummyPos)
        val lvThree = PtNumber(BaseDataType.UBYTE, 3.0, dummyPos)
        val lvOneR = PtNumber(BaseDataType.UBYTE, 1.0, dummyPos)
        val lvTwoR = PtNumber(BaseDataType.UBYTE, 2.0, dummyPos)
        val lvThreeR = PtNumber(BaseDataType.UBYTE, 3.0, dummyPos)
        val lvFour= PtNumber(BaseDataType.UBYTE, 4.0, dummyPos)
        val lv1 = PtArray(DataType.arrayFor(BaseDataType.UBYTE), dummyPos)
        arrayOf(lvOne, lvTwo, lvThree).forEach { lv1.add(it) }
        val lv2 = PtArray(DataType.arrayFor(BaseDataType.UBYTE), dummyPos)
        arrayOf(lvOneR, lvTwoR, lvThreeR).forEach { lv2.add(it) }
        val lv3 = PtArray(DataType.arrayFor(BaseDataType.UBYTE), dummyPos)
        arrayOf(lvOneR, lvTwoR, lvFour).forEach { lv3.add(it) }
        lv1 shouldBe lv2
        lv1 shouldNotBe lv3
    }

    test("testGreaterThan") {
        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) > PtNumber(BaseDataType.UBYTE, 99.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 254.0, dummyPos) > PtNumber(BaseDataType.UWORD, 253.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 100.0, dummyPos) > PtNumber(BaseDataType.FLOAT, 99.9, dummyPos)) shouldBe true

        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) >= PtNumber(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 254.0, dummyPos) >= PtNumber(BaseDataType.UWORD, 254.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 100.0, dummyPos) >= PtNumber(BaseDataType.FLOAT, 100.0, dummyPos)) shouldBe true

        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) > PtNumber(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe false
        (PtNumber(BaseDataType.UWORD, 254.0, dummyPos) > PtNumber(BaseDataType.UWORD, 254.0, dummyPos)) shouldBe false
        (PtNumber(BaseDataType.FLOAT, 100.0, dummyPos) > PtNumber(BaseDataType.FLOAT, 100.0, dummyPos)) shouldBe false

        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) >= PtNumber(BaseDataType.UBYTE, 101.0, dummyPos)) shouldBe false
        (PtNumber(BaseDataType.UWORD, 254.0, dummyPos) >= PtNumber(BaseDataType.UWORD, 255.0, dummyPos)) shouldBe false
        (PtNumber(BaseDataType.FLOAT, 100.0, dummyPos) >= PtNumber(BaseDataType.FLOAT, 100.1, dummyPos)) shouldBe false
    }

    test("testLessThan") {
        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) < PtNumber(BaseDataType.UBYTE, 101.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 254.0, dummyPos) < PtNumber(BaseDataType.UWORD, 255.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 100.0, dummyPos) < PtNumber(BaseDataType.FLOAT, 100.1, dummyPos)) shouldBe true

        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) <= PtNumber(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 254.0, dummyPos) <= PtNumber(BaseDataType.UWORD, 254.0, dummyPos)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 100.0, dummyPos) <= PtNumber(BaseDataType.FLOAT, 100.0, dummyPos)) shouldBe true

        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) < PtNumber(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe false
        (PtNumber(BaseDataType.UWORD, 254.0, dummyPos) < PtNumber(BaseDataType.UWORD, 254.0, dummyPos)) shouldBe false
        (PtNumber(BaseDataType.FLOAT, 100.0, dummyPos) < PtNumber(BaseDataType.FLOAT, 100.0, dummyPos)) shouldBe false

        (PtNumber(BaseDataType.UBYTE, 100.0, dummyPos) <= PtNumber(BaseDataType.UBYTE, 99.0, dummyPos)) shouldBe false
        (PtNumber(BaseDataType.UWORD, 254.0, dummyPos) <= PtNumber(BaseDataType.UWORD, 253.0, dummyPos)) shouldBe false
        (PtNumber(BaseDataType.FLOAT, 100.0, dummyPos) <= PtNumber(BaseDataType.FLOAT, 99.9, dummyPos)) shouldBe false
    }

})
