package prog8tests.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.ast.ExpressionError
import prog8.ast.expressions.ArrayLiteral
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteral
import prog8.ast.expressions.StringLiteral
import prog8.ast.statements.AnonymousScope
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.Encoding
import prog8.code.core.Position


class TestNumericLiteral: FunSpec({

    fun sameValueAndType(lv1: NumericLiteral, lv2: NumericLiteral): Boolean {
        return lv1.type==lv2.type && lv1==lv2
    }

    test("testIdentity") {
        val v = NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY)
        (v==v) shouldBe true
        (v != v) shouldBe false
        (v <= v) shouldBe true
        (v >= v) shouldBe true
        (v < v ) shouldBe false
        (v > v ) shouldBe false

        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY), NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY)) shouldBe true
    }

    test("test truncating avoidance") {
        shouldThrow<ExpressionError> {
            NumericLiteral(BaseDataType.BYTE, -2.345, Position.DUMMY)
        }.message shouldContain "float value given for integer"
        shouldThrow<ExpressionError> {
            NumericLiteral(BaseDataType.BYTE, -2.6, Position.DUMMY)
        }.message shouldContain "float value given for integer"
        shouldThrow<ExpressionError> {
            NumericLiteral(BaseDataType.UWORD, 2222.345, Position.DUMMY)
        }.message shouldContain "float value given for integer"
        NumericLiteral(BaseDataType.UBYTE, 2.0, Position.DUMMY).number shouldBe 2.0
        NumericLiteral(BaseDataType.BYTE, -2.0, Position.DUMMY).number shouldBe -2.0
        NumericLiteral(BaseDataType.UWORD, 2222.0, Position.DUMMY).number shouldBe 2222.0
        NumericLiteral(BaseDataType.FLOAT, 123.456, Position.DUMMY)
    }

    test("testEqualsAndNotEquals") {
        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) == NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) == NumericLiteral(BaseDataType.UWORD, 100.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) == NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY) == NumericLiteral(BaseDataType.UBYTE, 254.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY) == NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY) == NumericLiteral(BaseDataType.FLOAT, 12345.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY) == NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 22239.0, Position.DUMMY) == NumericLiteral(BaseDataType.UWORD, 22239.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 9.99, Position.DUMMY) == NumericLiteral(BaseDataType.FLOAT, 9.99, Position.DUMMY)) shouldBe true

        sameValueAndType(NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY), NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe true
        sameValueAndType(NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY), NumericLiteral(BaseDataType.UWORD, 100.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY), NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY), NumericLiteral(BaseDataType.UBYTE, 254.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY), NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY)) shouldBe true
        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY), NumericLiteral(BaseDataType.FLOAT, 12345.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY), NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.FLOAT, 22239.0, Position.DUMMY), NumericLiteral(BaseDataType.UWORD, 22239.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.FLOAT, 9.99, Position.DUMMY), NumericLiteral(BaseDataType.FLOAT, 9.99, Position.DUMMY)) shouldBe true

        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) != NumericLiteral(BaseDataType.UBYTE, 101.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) != NumericLiteral(BaseDataType.UWORD, 101.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) != NumericLiteral(BaseDataType.FLOAT, 101.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 245.0, Position.DUMMY) != NumericLiteral(BaseDataType.UBYTE, 246.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY) != NumericLiteral(BaseDataType.UWORD, 12346.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY) != NumericLiteral(BaseDataType.FLOAT, 12346.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 9.99, Position.DUMMY) != NumericLiteral(BaseDataType.UBYTE, 9.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 9.99, Position.DUMMY) != NumericLiteral(BaseDataType.UWORD, 9.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 9.99, Position.DUMMY) != NumericLiteral(BaseDataType.FLOAT, 9.0, Position.DUMMY)) shouldBe true

        sameValueAndType(NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY), NumericLiteral(BaseDataType.UBYTE, 101.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY), NumericLiteral(BaseDataType.UWORD, 101.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY), NumericLiteral(BaseDataType.FLOAT, 101.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 245.0, Position.DUMMY), NumericLiteral(BaseDataType.UBYTE, 246.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY), NumericLiteral(BaseDataType.UWORD, 12346.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.UWORD, 12345.0, Position.DUMMY), NumericLiteral(BaseDataType.FLOAT, 12346.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.FLOAT, 9.99, Position.DUMMY), NumericLiteral(BaseDataType.UBYTE, 9.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.FLOAT, 9.99, Position.DUMMY), NumericLiteral(BaseDataType.UWORD, 9.0, Position.DUMMY)) shouldBe false
        sameValueAndType(NumericLiteral(BaseDataType.FLOAT, 9.99, Position.DUMMY), NumericLiteral(BaseDataType.FLOAT, 9.0, Position.DUMMY)) shouldBe false


    }

    test("testEqualsRef") {
        (StringLiteral.create("hello", Encoding.PETSCII, Position.DUMMY) == StringLiteral.create("hello", Encoding.PETSCII, Position.DUMMY)) shouldBe true
        (StringLiteral.create("hello", Encoding.PETSCII, Position.DUMMY) != StringLiteral.create("bye", Encoding.PETSCII, Position.DUMMY)) shouldBe true
        (StringLiteral.create("hello", Encoding.SCREENCODES, Position.DUMMY) == StringLiteral.create("hello", Encoding.SCREENCODES, Position.DUMMY)) shouldBe true
        (StringLiteral.create("hello", Encoding.SCREENCODES, Position.DUMMY) != StringLiteral.create("bye", Encoding.SCREENCODES, Position.DUMMY)) shouldBe true
        (StringLiteral.create("hello", Encoding.SCREENCODES, Position.DUMMY) != StringLiteral.create("hello", Encoding.PETSCII, Position.DUMMY)) shouldBe true

        val lvOne = NumericLiteral(BaseDataType.UBYTE, 1.0, Position.DUMMY)
        val lvTwo = NumericLiteral(BaseDataType.UBYTE, 2.0, Position.DUMMY)
        val lvThree = NumericLiteral(BaseDataType.UBYTE, 3.0, Position.DUMMY)
        val lvOneR = NumericLiteral(BaseDataType.UBYTE, 1.0, Position.DUMMY)
        val lvTwoR = NumericLiteral(BaseDataType.UBYTE, 2.0, Position.DUMMY)
        val lvThreeR = NumericLiteral(BaseDataType.UBYTE, 3.0, Position.DUMMY)
        val lvFour= NumericLiteral(BaseDataType.UBYTE, 4.0, Position.DUMMY)
        val lv1 = ArrayLiteral(InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.UBYTE)), arrayOf(lvOne, lvTwo, lvThree), Position.DUMMY)
        val lv2 = ArrayLiteral(InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.UBYTE)), arrayOf(lvOneR, lvTwoR, lvThreeR), Position.DUMMY)
        val lv3 = ArrayLiteral(InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.UBYTE)), arrayOf(lvOneR, lvTwoR, lvFour), Position.DUMMY)
        lv1 shouldBe lv2
        lv1 shouldNotBe lv3
    }

    test("testGreaterThan") {
        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) > NumericLiteral(BaseDataType.UBYTE, 99.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY) > NumericLiteral(BaseDataType.UWORD, 253.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY) > NumericLiteral(BaseDataType.FLOAT, 99.9, Position.DUMMY)) shouldBe true

        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) >= NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY) >= NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY) >= NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY)) shouldBe true

        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) > NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe false
        (NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY) > NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY)) shouldBe false
        (NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY) > NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY)) shouldBe false

        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) >= NumericLiteral(BaseDataType.UBYTE, 101.0, Position.DUMMY)) shouldBe false
        (NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY) >= NumericLiteral(BaseDataType.UWORD, 255.0, Position.DUMMY)) shouldBe false
        (NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY) >= NumericLiteral(BaseDataType.FLOAT, 100.1, Position.DUMMY)) shouldBe false
    }

    test("testLessThan") {
        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) < NumericLiteral(BaseDataType.UBYTE, 101.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY) < NumericLiteral(BaseDataType.UWORD, 255.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY) < NumericLiteral(BaseDataType.FLOAT, 100.1, Position.DUMMY)) shouldBe true

        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) <= NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY) <= NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY)) shouldBe true
        (NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY) <= NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY)) shouldBe true

        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) < NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY)) shouldBe false
        (NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY) < NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY)) shouldBe false
        (NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY) < NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY)) shouldBe false

        (NumericLiteral(BaseDataType.UBYTE, 100.0, Position.DUMMY) <= NumericLiteral(BaseDataType.UBYTE, 99.0, Position.DUMMY)) shouldBe false
        (NumericLiteral(BaseDataType.UWORD, 254.0, Position.DUMMY) <= NumericLiteral(BaseDataType.UWORD, 253.0, Position.DUMMY)) shouldBe false
        (NumericLiteral(BaseDataType.FLOAT, 100.0, Position.DUMMY) <= NumericLiteral(BaseDataType.FLOAT, 99.9, Position.DUMMY)) shouldBe false
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

        NumericLiteral.optimalInteger(BaseDataType.UBYTE, BaseDataType.UWORD, 1, Position.DUMMY).type shouldBe BaseDataType.UWORD
        NumericLiteral.optimalInteger(BaseDataType.UWORD, BaseDataType.UBYTE, 1, Position.DUMMY).type shouldBe BaseDataType.UWORD
        NumericLiteral.optimalInteger(BaseDataType.UWORD, null, 1, Position.DUMMY).type shouldBe BaseDataType.UWORD
        NumericLiteral.optimalInteger(BaseDataType.UBYTE, BaseDataType.UBYTE, -1, Position.DUMMY).type shouldBe BaseDataType.BYTE
        NumericLiteral.optimalInteger(BaseDataType.UBYTE, null, -1, Position.DUMMY).type shouldBe BaseDataType.BYTE
        NumericLiteral.optimalInteger(BaseDataType.UWORD, BaseDataType.UWORD, -1, Position.DUMMY).type shouldBe BaseDataType.WORD
        NumericLiteral.optimalInteger(BaseDataType.UWORD, null, -1, Position.DUMMY).type shouldBe BaseDataType.WORD
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

        NumericLiteral.optimalNumeric(BaseDataType.UBYTE, BaseDataType.UWORD, 1.0, Position.DUMMY).type shouldBe BaseDataType.UWORD
        NumericLiteral.optimalNumeric(BaseDataType.UWORD, BaseDataType.UBYTE, 1.0, Position.DUMMY).type shouldBe BaseDataType.UWORD
        NumericLiteral.optimalNumeric(BaseDataType.UWORD, null, 1.0, Position.DUMMY).type shouldBe BaseDataType.UWORD
        NumericLiteral.optimalNumeric(BaseDataType.UBYTE, BaseDataType.UBYTE, -1.0, Position.DUMMY).type shouldBe BaseDataType.BYTE
        NumericLiteral.optimalNumeric(BaseDataType.UBYTE, null, -1.0, Position.DUMMY).type shouldBe BaseDataType.BYTE
        NumericLiteral.optimalNumeric(BaseDataType.UWORD, BaseDataType.UWORD, -1.0, Position.DUMMY).type shouldBe BaseDataType.WORD
        NumericLiteral.optimalNumeric(BaseDataType.UWORD, null, -1.0, Position.DUMMY).type shouldBe BaseDataType.WORD

        NumericLiteral.optimalNumeric(BaseDataType.UBYTE, BaseDataType.UWORD, 1.234, Position.DUMMY).type shouldBe BaseDataType.FLOAT
        NumericLiteral.optimalNumeric(BaseDataType.UWORD, BaseDataType.UBYTE, 1.234, Position.DUMMY).type shouldBe BaseDataType.FLOAT
        NumericLiteral.optimalNumeric(BaseDataType.UWORD, null, 1.234, Position.DUMMY).type shouldBe BaseDataType.FLOAT
        NumericLiteral.optimalNumeric(BaseDataType.UBYTE, BaseDataType.UBYTE, -1.234, Position.DUMMY).type shouldBe BaseDataType.FLOAT
        NumericLiteral.optimalNumeric(BaseDataType.UBYTE, null, -1.234, Position.DUMMY).type shouldBe BaseDataType.FLOAT
        NumericLiteral.optimalNumeric(BaseDataType.UWORD, BaseDataType.UWORD, -1.234, Position.DUMMY).type shouldBe BaseDataType.FLOAT
        NumericLiteral.optimalNumeric(BaseDataType.UWORD, null, -1.234, Position.DUMMY).type shouldBe BaseDataType.FLOAT
    }

    test("cast can change value") {
        fun num(dt: BaseDataType, num: Double): NumericLiteral {
            val n = NumericLiteral(dt, num, Position.DUMMY)
            n.linkParents(AnonymousScope.empty())
            return n
        }
        val cast1 = num(BaseDataType.UBYTE, 200.0).cast(BaseDataType.BYTE, false)
        cast1.isValid shouldBe true
        cast1.valueOrZero().number shouldBe -56.0
        val cast2 = num(BaseDataType.BYTE, -50.0).cast(BaseDataType.UBYTE, false)
        cast2.isValid shouldBe true
        cast2.valueOrZero().number shouldBe 206.0
        val cast3 = num(BaseDataType.UWORD, 55555.0).cast(BaseDataType.WORD, false)
        cast3.isValid shouldBe true
        cast3.valueOrZero().number shouldBe -9981.0
        val cast4 = num(BaseDataType.WORD, -3333.0).cast(BaseDataType.UWORD, false)
        cast4.isValid shouldBe true
        cast4.valueOrZero().number shouldBe 62203.0
    }

    test("convert cannot change value") {
        fun num(dt: BaseDataType, num: Double): NumericLiteral {
            val n = NumericLiteral(dt, num, Position.DUMMY)
            n.linkParents(AnonymousScope.empty())
            return n
        }
        num(BaseDataType.UBYTE, 200.0).convertTypeKeepValue(BaseDataType.BYTE).isValid shouldBe false
        num(BaseDataType.BYTE, -50.0).convertTypeKeepValue(BaseDataType.UBYTE).isValid shouldBe false
        num(BaseDataType.UWORD, 55555.0).convertTypeKeepValue(BaseDataType.WORD).isValid shouldBe false
        num(BaseDataType.WORD, -3333.0).convertTypeKeepValue(BaseDataType.UWORD).isValid shouldBe false

        num(BaseDataType.UBYTE, 42.0).convertTypeKeepValue(BaseDataType.BYTE).isValid shouldBe true
        num(BaseDataType.BYTE, 42.0).convertTypeKeepValue(BaseDataType.UBYTE).isValid shouldBe true
        num(BaseDataType.UWORD, 12345.0).convertTypeKeepValue(BaseDataType.WORD).isValid shouldBe true
        num(BaseDataType.WORD, 12345.0).convertTypeKeepValue(BaseDataType.UWORD).isValid shouldBe true
    }
})
