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

    test("testIdentity") {
        val v = PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY)
        (v==v) shouldBe true
        (v != v) shouldBe false
        (v <= v) shouldBe true
        (v >= v) shouldBe true
        (v < v ) shouldBe false
        (v > v ) shouldBe false

        sameValueAndType(PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY), PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY)) shouldBe true
    }

    test("test truncating") {
        shouldThrow<IllegalArgumentException> {
            PtNumber(BaseDataType.BYTE, -2.345, Position.DUMMY)
        }.message shouldContain "refused truncating"
        shouldThrow<IllegalArgumentException> {
            PtNumber(BaseDataType.BYTE, -2.6, Position.DUMMY)
        }.message shouldContain "refused truncating"
        shouldThrow<IllegalArgumentException> {
            PtNumber(BaseDataType.UWORD, 2222.345, Position.DUMMY)
        }.message shouldContain "refused truncating"
        PtNumber(BaseDataType.UBYTE, 2.0, Position.DUMMY).number shouldBe 2.0
        PtNumber(BaseDataType.BYTE, -2.0, Position.DUMMY).number shouldBe -2.0
        PtNumber(BaseDataType.UWORD, 2222.0, Position.DUMMY).number shouldBe 2222.0
        PtNumber(BaseDataType.FLOAT, 123.456, Position.DUMMY)
    }

    test("testEqualsAndNotEquals") {
        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) == PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) == PtNumber(BaseDataType.UWORD, 100.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) == PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY) == PtNumber(BaseDataType.UBYTE, 254.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY) == PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY) == PtNumber(BaseDataType.FLOAT, 12345.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY) == PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 22239.0, Position.DUMMY) == PtNumber(BaseDataType.UWORD, 22239.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 9.99, Position.DUMMY) == PtNumber(BaseDataType.FLOAT, 9.99, Position.DUMMY)) shouldBe true

        sameValueAndType(PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY), PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe true
        sameValueAndType(PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY), PtNumber(BaseDataType.UWORD, 100.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY), PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY), PtNumber(BaseDataType.UBYTE, 254.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY), PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY)) shouldBe true
        sameValueAndType(PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY), PtNumber(BaseDataType.FLOAT, 12345.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY), PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.FLOAT, 22239.0, Position.DUMMY), PtNumber(BaseDataType.UWORD, 22239.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.FLOAT, 9.99, Position.DUMMY), PtNumber(BaseDataType.FLOAT, 9.99, Position.DUMMY)) shouldBe true

        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) != PtNumber(BaseDataType.UBYTE, 101.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) != PtNumber(BaseDataType.UWORD, 101.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) != PtNumber(BaseDataType.FLOAT, 101.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 245.0, Position.DUMMY) != PtNumber(BaseDataType.UBYTE, 246.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY) != PtNumber(BaseDataType.UWORD, 12346.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY) != PtNumber(BaseDataType.FLOAT, 12346.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 9.99, Position.DUMMY) != PtNumber(BaseDataType.UBYTE, 9.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 9.99, Position.DUMMY) != PtNumber(BaseDataType.UWORD, 9.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 9.99, Position.DUMMY) != PtNumber(BaseDataType.FLOAT, 9.0, Position.DUMMY)) shouldBe true

        sameValueAndType(PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY), PtNumber(BaseDataType.UBYTE, 101.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY), PtNumber(BaseDataType.UWORD, 101.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY), PtNumber(BaseDataType.FLOAT, 101.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UWORD, 245.0, Position.DUMMY), PtNumber(BaseDataType.UBYTE, 246.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY), PtNumber(BaseDataType.UWORD, 12346.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.UWORD, 12345.0, Position.DUMMY), PtNumber(BaseDataType.FLOAT, 12346.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.FLOAT, 9.99, Position.DUMMY), PtNumber(BaseDataType.UBYTE, 9.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.FLOAT, 9.99, Position.DUMMY), PtNumber(BaseDataType.UWORD, 9.0, Position.DUMMY)) shouldBe false
        sameValueAndType(PtNumber(BaseDataType.FLOAT, 9.99, Position.DUMMY), PtNumber(BaseDataType.FLOAT, 9.0, Position.DUMMY)) shouldBe false


    }

    test("testEqualsRef") {
        (PtString("hello", Encoding.PETSCII, Position.DUMMY) == PtString("hello", Encoding.PETSCII, Position.DUMMY)) shouldBe true
        (PtString("hello", Encoding.PETSCII, Position.DUMMY) != PtString("bye", Encoding.PETSCII, Position.DUMMY)) shouldBe true
        (PtString("hello", Encoding.SCREENCODES, Position.DUMMY) == PtString("hello", Encoding.SCREENCODES, Position.DUMMY)) shouldBe true
        (PtString("hello", Encoding.SCREENCODES, Position.DUMMY) != PtString("bye", Encoding.SCREENCODES, Position.DUMMY)) shouldBe true
        (PtString("hello", Encoding.SCREENCODES, Position.DUMMY) != PtString("hello", Encoding.PETSCII, Position.DUMMY)) shouldBe true

        val lvOne = PtNumber(BaseDataType.UBYTE, 1.0, Position.DUMMY)
        val lvTwo = PtNumber(BaseDataType.UBYTE, 2.0, Position.DUMMY)
        val lvThree = PtNumber(BaseDataType.UBYTE, 3.0, Position.DUMMY)
        val lvOneR = PtNumber(BaseDataType.UBYTE, 1.0, Position.DUMMY)
        val lvTwoR = PtNumber(BaseDataType.UBYTE, 2.0, Position.DUMMY)
        val lvThreeR = PtNumber(BaseDataType.UBYTE, 3.0, Position.DUMMY)
        val lvFour= PtNumber(BaseDataType.UBYTE, 4.0, Position.DUMMY)
        val lv1 = PtArray(DataType.arrayFor(BaseDataType.UBYTE), Position.DUMMY)
        arrayOf(lvOne, lvTwo, lvThree).forEach { lv1.add(it) }
        val lv2 = PtArray(DataType.arrayFor(BaseDataType.UBYTE), Position.DUMMY)
        arrayOf(lvOneR, lvTwoR, lvThreeR).forEach { lv2.add(it) }
        val lv3 = PtArray(DataType.arrayFor(BaseDataType.UBYTE), Position.DUMMY)
        arrayOf(lvOneR, lvTwoR, lvFour).forEach { lv3.add(it) }
        lv1 shouldBe lv2
        lv1 shouldNotBe lv3
    }

    test("testGreaterThan") {
        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) > PtNumber(BaseDataType.UBYTE, 99.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY) > PtNumber(BaseDataType.UWORD, 253.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY) > PtNumber(BaseDataType.FLOAT, 99.9, Position.DUMMY)) shouldBe true

        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) >= PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY) >= PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY) >= PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY)) shouldBe true

        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) > PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe false
        (PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY) > PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY)) shouldBe false
        (PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY) > PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY)) shouldBe false

        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) >= PtNumber(BaseDataType.UBYTE, 101.0, Position.DUMMY)) shouldBe false
        (PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY) >= PtNumber(BaseDataType.UWORD, 255.0, Position.DUMMY)) shouldBe false
        (PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY) >= PtNumber(BaseDataType.FLOAT, 100.1, Position.DUMMY)) shouldBe false
    }

    test("testLessThan") {
        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) < PtNumber(BaseDataType.UBYTE, 101.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY) < PtNumber(BaseDataType.UWORD, 255.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY) < PtNumber(BaseDataType.FLOAT, 100.1, Position.DUMMY)) shouldBe true

        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) <= PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY) <= PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY)) shouldBe true
        (PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY) <= PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY)) shouldBe true

        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) < PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe false
        (PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY) < PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY)) shouldBe false
        (PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY) < PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY)) shouldBe false

        (PtNumber(BaseDataType.UBYTE, 100.0, Position.DUMMY) <= PtNumber(BaseDataType.UBYTE, 99.0, Position.DUMMY)) shouldBe false
        (PtNumber(BaseDataType.UWORD, 254.0, Position.DUMMY) <= PtNumber(BaseDataType.UWORD, 253.0, Position.DUMMY)) shouldBe false
        (PtNumber(BaseDataType.FLOAT, 100.0, Position.DUMMY) <= PtNumber(BaseDataType.FLOAT, 99.9, Position.DUMMY)) shouldBe false
    }

})
