package prog8tests.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.ast.base.ExpressionError
import prog8.ast.expressions.ArrayLiteral
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteral
import prog8.ast.expressions.StringLiteral
import prog8.code.core.BaseDataType
import prog8.code.core.DataTypeFull
import prog8.code.core.Encoding
import prog8.code.core.Position


class TestNumericLiteral: FunSpec({

    fun sameValueAndType(lv1: NumericLiteral, lv2: NumericLiteral): Boolean {
        return lv1.type==lv2.type && lv1==lv2
    }

    val dummyPos = Position("test", 0, 0, 0)

    test("testIdentity") {
        val v = NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos)
        (v==v) shouldBe true
        (v != v) shouldBe false
        (v <= v) shouldBe true
        (v >= v) shouldBe true
        (v < v ) shouldBe false
        (v > v ) shouldBe false

        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos), NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos)) shouldBe true
    }

    test("test truncating") {
        shouldThrow<ExpressionError> {
            NumericLiteral(BaseDataType.BYTE, -2.345, dummyPos)
        }.message shouldContain "refused truncating"
        shouldThrow<ExpressionError> {
            NumericLiteral(BaseDataType.BYTE, -2.6, dummyPos)
        }.message shouldContain "refused truncating"
        shouldThrow<ExpressionError> {
            NumericLiteral(BaseDataType.UWORD, 2222.345, dummyPos)
        }.message shouldContain "refused truncating"
        NumericLiteral(BaseDataType.UBYTE, 2.0, dummyPos).number shouldBe 2.0
        NumericLiteral(BaseDataType.BYTE, -2.0, dummyPos).number shouldBe -2.0
        NumericLiteral(BaseDataType.UWORD, 2222.0, dummyPos).number shouldBe 2222.0
        NumericLiteral(BaseDataType.FLOAT, 123.456, dummyPos)
    }

    test("testEqualsAndNotEquals") {
        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) == NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) == NumericLiteral(BaseDataType.UWORD, 100.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) == NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos) == NumericLiteral(BaseDataType.UBYTE, 254.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos) == NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos) == NumericLiteral(BaseDataType.FLOAT, 12345.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos) == NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 22239.0, dummyPos) == NumericLiteral(BaseDataType.UWORD, 22239.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 9.99, dummyPos) == NumericLiteral(BaseDataType.FLOAT, 9.99, dummyPos)) shouldBe true

        sameValueAndType(NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos), NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe true
        sameValueAndType(NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos), NumericLiteral(BaseDataType.UWORD, 100.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos), NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos), NumericLiteral(BaseDataType.UBYTE, 254.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos), NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos)) shouldBe true
        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos), NumericLiteral(BaseDataType.FLOAT, 12345.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos), NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.FLOAT, 22239.0, dummyPos), NumericLiteral(BaseDataType.UWORD, 22239.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.FLOAT, 9.99, dummyPos), NumericLiteral(BaseDataType.FLOAT, 9.99, dummyPos)) shouldBe true

        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) != NumericLiteral(BaseDataType.UBYTE, 101.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) != NumericLiteral(BaseDataType.UWORD, 101.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) != NumericLiteral(BaseDataType.FLOAT, 101.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 245.0, dummyPos) != NumericLiteral(BaseDataType.UBYTE, 246.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos) != NumericLiteral(BaseDataType.UWORD, 12346.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos) != NumericLiteral(BaseDataType.FLOAT, 12346.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 9.99, dummyPos) != NumericLiteral(BaseDataType.UBYTE, 9.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 9.99, dummyPos) != NumericLiteral(BaseDataType.UWORD, 9.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 9.99, dummyPos) != NumericLiteral(BaseDataType.FLOAT, 9.0, dummyPos)) shouldBe true

        sameValueAndType(NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos), NumericLiteral(BaseDataType.UBYTE, 101.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos), NumericLiteral(BaseDataType.UWORD, 101.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos), NumericLiteral(BaseDataType.FLOAT, 101.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 245.0, dummyPos), NumericLiteral(BaseDataType.UBYTE, 246.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos), NumericLiteral(BaseDataType.UWORD, 12346.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 12345.0, dummyPos), NumericLiteral(BaseDataType.FLOAT, 12346.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.FLOAT, 9.99, dummyPos), NumericLiteral(BaseDataType.UBYTE, 9.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.FLOAT, 9.99, dummyPos), NumericLiteral(BaseDataType.UWORD, 9.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.FLOAT, 9.99, dummyPos), NumericLiteral(BaseDataType.FLOAT, 9.0, dummyPos)) shouldBe false


    }

    test("testEqualsRef") {
        (StringLiteral.create("hello", Encoding.PETSCII, dummyPos) == StringLiteral.create("hello", Encoding.PETSCII, dummyPos)) shouldBe true
        (StringLiteral.create("hello", Encoding.PETSCII, dummyPos) != StringLiteral.create("bye", Encoding.PETSCII, dummyPos)) shouldBe true
        (StringLiteral.create("hello", Encoding.SCREENCODES, dummyPos) == StringLiteral.create("hello", Encoding.SCREENCODES, dummyPos)) shouldBe true
        (StringLiteral.create("hello", Encoding.SCREENCODES, dummyPos) != StringLiteral.create("bye", Encoding.SCREENCODES, dummyPos)) shouldBe true
        (StringLiteral.create("hello", Encoding.SCREENCODES, dummyPos) != StringLiteral.create("hello", Encoding.PETSCII, dummyPos)) shouldBe true

        val lvOne = NumericLiteral(BaseDataType.UBYTE, 1.0, dummyPos)
        val lvTwo = NumericLiteral(BaseDataType.UBYTE, 2.0, dummyPos)
        val lvThree = NumericLiteral(BaseDataType.UBYTE, 3.0, dummyPos)
        val lvOneR = NumericLiteral(BaseDataType.UBYTE, 1.0, dummyPos)
        val lvTwoR = NumericLiteral(BaseDataType.UBYTE, 2.0, dummyPos)
        val lvThreeR = NumericLiteral(BaseDataType.UBYTE, 3.0, dummyPos)
        val lvFour= NumericLiteral(BaseDataType.UBYTE, 4.0, dummyPos)
        val lv1 = ArrayLiteral(InferredTypes.InferredType.known(DataTypeFull.arrayFor(BaseDataType.UBYTE)), arrayOf(lvOne, lvTwo, lvThree), dummyPos)
        val lv2 = ArrayLiteral(InferredTypes.InferredType.known(DataTypeFull.arrayFor(BaseDataType.UBYTE)), arrayOf(lvOneR, lvTwoR, lvThreeR), dummyPos)
        val lv3 = ArrayLiteral(InferredTypes.InferredType.known(DataTypeFull.arrayFor(BaseDataType.UBYTE)), arrayOf(lvOneR, lvTwoR, lvFour), dummyPos)
        lv1 shouldBe lv2
        lv1 shouldNotBe lv3
    }

    test("testGreaterThan") {
        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) > NumericLiteral(BaseDataType.UBYTE, 99.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos) > NumericLiteral(BaseDataType.UWORD, 253.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos) > NumericLiteral(BaseDataType.FLOAT, 99.9, dummyPos)) shouldBe true

        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) >= NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos) >= NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos) >= NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos)) shouldBe true

        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) > NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe false
        (NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos) > NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos)) shouldBe false
        (NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos) > NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos)) shouldBe false

        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) >= NumericLiteral(BaseDataType.UBYTE, 101.0, dummyPos)) shouldBe false
        (NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos) >= NumericLiteral(BaseDataType.UWORD, 255.0, dummyPos)) shouldBe false
        (NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos) >= NumericLiteral(BaseDataType.FLOAT, 100.1, dummyPos)) shouldBe false
    }

    test("testLessThan") {
        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) < NumericLiteral(BaseDataType.UBYTE, 101.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos) < NumericLiteral(BaseDataType.UWORD, 255.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos) < NumericLiteral(BaseDataType.FLOAT, 100.1, dummyPos)) shouldBe true

        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) <= NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos) <= NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos) <= NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos)) shouldBe true

        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) < NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos)) shouldBe false
        (NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos) < NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos)) shouldBe false
        (NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos) < NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos)) shouldBe false

        (NumericLiteral(BaseDataType.UBYTE, 100.0, dummyPos) <= NumericLiteral(BaseDataType.UBYTE, 99.0, dummyPos)) shouldBe false
        (NumericLiteral(BaseDataType.UWORD, 254.0, dummyPos) <= NumericLiteral(BaseDataType.UWORD, 253.0, dummyPos)) shouldBe false
        (NumericLiteral(BaseDataType.FLOAT, 100.0, dummyPos) <= NumericLiteral(BaseDataType.FLOAT, 99.9, dummyPos)) shouldBe false
    }

    test("optimalInteger") {
        NumericLiteral.optimalInteger(10, Position.DUMMY).type shouldBe BaseDataType.UBYTE
        NumericLiteral.optimalInteger(10, Position.DUMMY).number shouldBe 10.0
        NumericLiteral.optimalInteger(-10, Position.DUMMY).type shouldBe BaseDataType.BYTE
        NumericLiteral.optimalInteger(-10, Position.DUMMY).number shouldBe -10.0
        NumericLiteral.optimalInteger(1000, Position.DUMMY).type shouldBe BaseDataType.UWORD
        NumericLiteral.optimalInteger(-1000, Position.DUMMY).number shouldBe -1000.0
        NumericLiteral.optimalInteger(1000u, Position.DUMMY).type shouldBe BaseDataType.UWORD
        NumericLiteral.optimalInteger(1000u, Position.DUMMY).number shouldBe 1000.0
    }

    test("optimalNumeric") {
        NumericLiteral.optimalNumeric(10, Position.DUMMY).type shouldBe BaseDataType.UBYTE
        NumericLiteral.optimalNumeric(10, Position.DUMMY).number shouldBe 10.0
        NumericLiteral.optimalNumeric(-10, Position.DUMMY).type shouldBe BaseDataType.BYTE
        NumericLiteral.optimalNumeric(-10, Position.DUMMY).number shouldBe -10.0
        NumericLiteral.optimalNumeric(1000, Position.DUMMY).type shouldBe BaseDataType.UWORD
        NumericLiteral.optimalNumeric(1000, Position.DUMMY).number shouldBe 1000.0
        NumericLiteral.optimalNumeric(-1000, Position.DUMMY).type shouldBe BaseDataType.WORD
        NumericLiteral.optimalNumeric(-1000, Position.DUMMY).number shouldBe -1000.0
        NumericLiteral.optimalNumeric(1.123, Position.DUMMY).type shouldBe BaseDataType.FLOAT
        NumericLiteral.optimalNumeric(1.123, Position.DUMMY).number shouldBe 1.123
        NumericLiteral.optimalNumeric(1.0, Position.DUMMY).type shouldBe BaseDataType.UBYTE
        NumericLiteral.optimalNumeric(1.0, Position.DUMMY).number shouldBe 1.0
        NumericLiteral.optimalNumeric(-1.0, Position.DUMMY).type shouldBe BaseDataType.BYTE
        NumericLiteral.optimalNumeric(-1.0, Position.DUMMY).number shouldBe -1.0
        NumericLiteral.optimalNumeric(1234.0, Position.DUMMY).type shouldBe BaseDataType.UWORD
        NumericLiteral.optimalNumeric(1234.0, Position.DUMMY).number shouldBe 1234.0
        NumericLiteral.optimalNumeric(-1234.0, Position.DUMMY).type shouldBe BaseDataType.WORD
        NumericLiteral.optimalNumeric(-1234.0, Position.DUMMY).number shouldBe -1234.0
    }
})
