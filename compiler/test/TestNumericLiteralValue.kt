package prog8tests

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.ast.base.DataType
import prog8.ast.base.ExpressionError
import prog8.ast.base.Position
import prog8.ast.expressions.ArrayLiteralValue
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.StringLiteralValue
import prog8.compilerinterface.Encoding

class TestNumericLiteralValue: FunSpec({

    fun sameValueAndType(lv1: NumericLiteralValue, lv2: NumericLiteralValue): Boolean {
        return lv1.type==lv2.type && lv1==lv2
    }

    val dummyPos = Position("test", 0, 0, 0)

    test("testIdentity") {
        val v = NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos)
        (v==v) shouldBe true
        (v != v) shouldBe false
        (v <= v) shouldBe true
        (v >= v) shouldBe true
        (v < v ) shouldBe false
        (v > v ) shouldBe false

        sameValueAndType(NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos), NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos)) shouldBe true
    }

    test("test rounding") {
        shouldThrow<ExpressionError> {
            NumericLiteralValue(DataType.BYTE, -2.345, dummyPos)
        }.message shouldContain "refused rounding"
        shouldThrow<ExpressionError> {
            NumericLiteralValue(DataType.BYTE, -2.6, dummyPos)
        }.message shouldContain "refused rounding"
        shouldThrow<ExpressionError> {
            NumericLiteralValue(DataType.UWORD, 2222.345, dummyPos)
        }.message shouldContain "refused rounding"
        NumericLiteralValue(DataType.UBYTE, 2.0, dummyPos).number shouldBe 2.0
        NumericLiteralValue(DataType.BYTE, -2.0, dummyPos).number shouldBe -2.0
        NumericLiteralValue(DataType.UWORD, 2222.0, dummyPos).number shouldBe 2222.0
        NumericLiteralValue(DataType.FLOAT, 123.456, dummyPos)
    }

    test("testEqualsAndNotEquals") {
        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) == NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) == NumericLiteralValue(DataType.UWORD, 100.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) == NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UWORD, 254.0, dummyPos) == NumericLiteralValue(DataType.UBYTE, 254.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos) == NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos) == NumericLiteralValue(DataType.FLOAT, 12345.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) == NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.FLOAT, 22239.0, dummyPos) == NumericLiteralValue(DataType.UWORD, 22239.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos) == NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos)) shouldBe true

        sameValueAndType(NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos), NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos)) shouldBe true
        sameValueAndType(NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos), NumericLiteralValue(DataType.UWORD, 100.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos), NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.UWORD, 254.0, dummyPos), NumericLiteralValue(DataType.UBYTE, 254.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos), NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos)) shouldBe true
        sameValueAndType(NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos), NumericLiteralValue(DataType.FLOAT, 12345.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos), NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.FLOAT, 22239.0, dummyPos), NumericLiteralValue(DataType.UWORD, 22239.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos), NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos)) shouldBe true

        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) != NumericLiteralValue(DataType.UBYTE, 101.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) != NumericLiteralValue(DataType.UWORD, 101.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) != NumericLiteralValue(DataType.FLOAT, 101.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UWORD, 245.0, dummyPos) != NumericLiteralValue(DataType.UBYTE, 246.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos) != NumericLiteralValue(DataType.UWORD, 12346.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos) != NumericLiteralValue(DataType.FLOAT, 12346.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos) != NumericLiteralValue(DataType.UBYTE, 9.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos) != NumericLiteralValue(DataType.UWORD, 9.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos) != NumericLiteralValue(DataType.FLOAT, 9.0, dummyPos)) shouldBe true

        sameValueAndType(NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos), NumericLiteralValue(DataType.UBYTE, 101.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos), NumericLiteralValue(DataType.UWORD, 101.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos), NumericLiteralValue(DataType.FLOAT, 101.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.UWORD, 245.0, dummyPos), NumericLiteralValue(DataType.UBYTE, 246.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos), NumericLiteralValue(DataType.UWORD, 12346.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.UWORD, 12345.0, dummyPos), NumericLiteralValue(DataType.FLOAT, 12346.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos), NumericLiteralValue(DataType.UBYTE, 9.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos), NumericLiteralValue(DataType.UWORD, 9.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos), NumericLiteralValue(DataType.FLOAT, 9.0, dummyPos)) shouldBe false


    }

    test("testEqualsRef") {
        (StringLiteralValue("hello", Encoding.PETSCII, dummyPos) == StringLiteralValue("hello", Encoding.PETSCII, dummyPos)) shouldBe true
        (StringLiteralValue("hello", Encoding.PETSCII, dummyPos) != StringLiteralValue("bye", Encoding.PETSCII, dummyPos)) shouldBe true
        (StringLiteralValue("hello", Encoding.SCREENCODES, dummyPos) == StringLiteralValue("hello", Encoding.SCREENCODES, dummyPos)) shouldBe true
        (StringLiteralValue("hello", Encoding.SCREENCODES, dummyPos) != StringLiteralValue("bye", Encoding.SCREENCODES, dummyPos)) shouldBe true
        (StringLiteralValue("hello", Encoding.SCREENCODES, dummyPos) != StringLiteralValue("hello", Encoding.PETSCII, dummyPos)) shouldBe true

        val lvOne = NumericLiteralValue(DataType.UBYTE, 1.0, dummyPos)
        val lvTwo = NumericLiteralValue(DataType.UBYTE, 2.0, dummyPos)
        val lvThree = NumericLiteralValue(DataType.UBYTE, 3.0, dummyPos)
        val lvOneR = NumericLiteralValue(DataType.UBYTE, 1.0, dummyPos)
        val lvTwoR = NumericLiteralValue(DataType.UBYTE, 2.0, dummyPos)
        val lvThreeR = NumericLiteralValue(DataType.UBYTE, 3.0, dummyPos)
        val lvFour= NumericLiteralValue(DataType.UBYTE, 4.0, dummyPos)
        val lv1 = ArrayLiteralValue(InferredTypes.InferredType.known(DataType.ARRAY_UB), arrayOf(lvOne, lvTwo, lvThree), dummyPos)
        val lv2 = ArrayLiteralValue(InferredTypes.InferredType.known(DataType.ARRAY_UB), arrayOf(lvOneR, lvTwoR, lvThreeR), dummyPos)
        val lv3 = ArrayLiteralValue(InferredTypes.InferredType.known(DataType.ARRAY_UB), arrayOf(lvOneR, lvTwoR, lvFour), dummyPos)
        lv1 shouldBe lv2
        lv1 shouldNotBe lv3
    }

    test("testGreaterThan") {
        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) > NumericLiteralValue(DataType.UBYTE, 99.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UWORD, 254.0, dummyPos) > NumericLiteralValue(DataType.UWORD, 253.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) > NumericLiteralValue(DataType.FLOAT, 99.9, dummyPos)) shouldBe true

        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) >= NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UWORD, 254.0, dummyPos) >= NumericLiteralValue(DataType.UWORD, 254.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) >= NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos)) shouldBe true

        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) > NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos)) shouldBe false
        (NumericLiteralValue(DataType.UWORD, 254.0, dummyPos) > NumericLiteralValue(DataType.UWORD, 254.0, dummyPos)) shouldBe false
        (NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) > NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos)) shouldBe false

        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) >= NumericLiteralValue(DataType.UBYTE, 101.0, dummyPos)) shouldBe false
        (NumericLiteralValue(DataType.UWORD, 254.0, dummyPos) >= NumericLiteralValue(DataType.UWORD, 255.0, dummyPos)) shouldBe false
        (NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) >= NumericLiteralValue(DataType.FLOAT, 100.1, dummyPos)) shouldBe false
    }

    test("testLessThan") {
        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) < NumericLiteralValue(DataType.UBYTE, 101.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UWORD, 254.0, dummyPos) < NumericLiteralValue(DataType.UWORD, 255.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) < NumericLiteralValue(DataType.FLOAT, 100.1, dummyPos)) shouldBe true

        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) <= NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.UWORD, 254.0, dummyPos) <= NumericLiteralValue(DataType.UWORD, 254.0, dummyPos)) shouldBe true
        (NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) <= NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos)) shouldBe true

        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) < NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos)) shouldBe false
        (NumericLiteralValue(DataType.UWORD, 254.0, dummyPos) < NumericLiteralValue(DataType.UWORD, 254.0, dummyPos)) shouldBe false
        (NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) < NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos)) shouldBe false

        (NumericLiteralValue(DataType.UBYTE, 100.0, dummyPos) <= NumericLiteralValue(DataType.UBYTE, 99.0, dummyPos)) shouldBe false
        (NumericLiteralValue(DataType.UWORD, 254.0, dummyPos) <= NumericLiteralValue(DataType.UWORD, 253.0, dummyPos)) shouldBe false
        (NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) <= NumericLiteralValue(DataType.FLOAT, 99.9, dummyPos)) shouldBe false
    }

})
