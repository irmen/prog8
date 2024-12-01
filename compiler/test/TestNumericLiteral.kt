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
import prog8.ast.statements.AnonymousScope
import prog8.code.core.DataType
import prog8.code.core.Encoding
import prog8.code.core.Position


class TestNumericLiteral: FunSpec({

    fun sameValueAndType(lv1: NumericLiteral, lv2: NumericLiteral): Boolean {
        return lv1.type==lv2.type && lv1==lv2
    }

    val dummyPos = Position("test", 0, 0, 0)

    test("testIdentity") {
        val v = NumericLiteral(DataType.UWORD, 12345.0, dummyPos)
        (v==v) shouldBe true
        (v != v) shouldBe false
        (v <= v) shouldBe true
        (v >= v) shouldBe true
        (v < v ) shouldBe false
        (v > v ) shouldBe false

        sameValueAndType(NumericLiteral(DataType.UWORD, 12345.0, dummyPos), NumericLiteral(DataType.UWORD, 12345.0, dummyPos)) shouldBe true
    }

    test("test truncating") {
        shouldThrow<ExpressionError> {
            NumericLiteral(DataType.BYTE, -2.345, dummyPos)
        }.message shouldContain "refused truncating"
        shouldThrow<ExpressionError> {
            NumericLiteral(DataType.BYTE, -2.6, dummyPos)
        }.message shouldContain "refused truncating"
        shouldThrow<ExpressionError> {
            NumericLiteral(DataType.UWORD, 2222.345, dummyPos)
        }.message shouldContain "refused truncating"
        NumericLiteral(DataType.UBYTE, 2.0, dummyPos).number shouldBe 2.0
        NumericLiteral(DataType.BYTE, -2.0, dummyPos).number shouldBe -2.0
        NumericLiteral(DataType.UWORD, 2222.0, dummyPos).number shouldBe 2222.0
        NumericLiteral(DataType.FLOAT, 123.456, dummyPos)
    }

    test("testEqualsAndNotEquals") {
        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) == NumericLiteral(DataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) == NumericLiteral(DataType.UWORD, 100.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) == NumericLiteral(DataType.FLOAT, 100.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UWORD, 254.0, dummyPos) == NumericLiteral(DataType.UBYTE, 254.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UWORD, 12345.0, dummyPos) == NumericLiteral(DataType.UWORD, 12345.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UWORD, 12345.0, dummyPos) == NumericLiteral(DataType.FLOAT, 12345.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.FLOAT, 100.0, dummyPos) == NumericLiteral(DataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.FLOAT, 22239.0, dummyPos) == NumericLiteral(DataType.UWORD, 22239.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.FLOAT, 9.99, dummyPos) == NumericLiteral(DataType.FLOAT, 9.99, dummyPos)) shouldBe true

        sameValueAndType(NumericLiteral(DataType.UBYTE, 100.0, dummyPos), NumericLiteral(DataType.UBYTE, 100.0, dummyPos)) shouldBe true
        sameValueAndType(NumericLiteral(DataType.UBYTE, 100.0, dummyPos), NumericLiteral(DataType.UWORD, 100.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.UBYTE, 100.0, dummyPos), NumericLiteral(DataType.FLOAT, 100.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.UWORD, 254.0, dummyPos), NumericLiteral(DataType.UBYTE, 254.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.UWORD, 12345.0, dummyPos), NumericLiteral(DataType.UWORD, 12345.0, dummyPos)) shouldBe true
        sameValueAndType(NumericLiteral(DataType.UWORD, 12345.0, dummyPos), NumericLiteral(DataType.FLOAT, 12345.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.FLOAT, 100.0, dummyPos), NumericLiteral(DataType.UBYTE, 100.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.FLOAT, 22239.0, dummyPos), NumericLiteral(DataType.UWORD, 22239.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.FLOAT, 9.99, dummyPos), NumericLiteral(DataType.FLOAT, 9.99, dummyPos)) shouldBe true

        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) != NumericLiteral(DataType.UBYTE, 101.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) != NumericLiteral(DataType.UWORD, 101.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) != NumericLiteral(DataType.FLOAT, 101.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UWORD, 245.0, dummyPos) != NumericLiteral(DataType.UBYTE, 246.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UWORD, 12345.0, dummyPos) != NumericLiteral(DataType.UWORD, 12346.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UWORD, 12345.0, dummyPos) != NumericLiteral(DataType.FLOAT, 12346.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.FLOAT, 9.99, dummyPos) != NumericLiteral(DataType.UBYTE, 9.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.FLOAT, 9.99, dummyPos) != NumericLiteral(DataType.UWORD, 9.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.FLOAT, 9.99, dummyPos) != NumericLiteral(DataType.FLOAT, 9.0, dummyPos)) shouldBe true

        sameValueAndType(NumericLiteral(DataType.UBYTE, 100.0, dummyPos), NumericLiteral(DataType.UBYTE, 101.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.UBYTE, 100.0, dummyPos), NumericLiteral(DataType.UWORD, 101.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.UBYTE, 100.0, dummyPos), NumericLiteral(DataType.FLOAT, 101.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.UWORD, 245.0, dummyPos), NumericLiteral(DataType.UBYTE, 246.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.UWORD, 12345.0, dummyPos), NumericLiteral(DataType.UWORD, 12346.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.UWORD, 12345.0, dummyPos), NumericLiteral(DataType.FLOAT, 12346.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.FLOAT, 9.99, dummyPos), NumericLiteral(DataType.UBYTE, 9.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.FLOAT, 9.99, dummyPos), NumericLiteral(DataType.UWORD, 9.0, dummyPos)) shouldBe false
        sameValueAndType(NumericLiteral(DataType.FLOAT, 9.99, dummyPos), NumericLiteral(DataType.FLOAT, 9.0, dummyPos)) shouldBe false


    }

    test("testEqualsRef") {
        (StringLiteral.create("hello", Encoding.PETSCII, dummyPos) == StringLiteral.create("hello", Encoding.PETSCII, dummyPos)) shouldBe true
        (StringLiteral.create("hello", Encoding.PETSCII, dummyPos) != StringLiteral.create("bye", Encoding.PETSCII, dummyPos)) shouldBe true
        (StringLiteral.create("hello", Encoding.SCREENCODES, dummyPos) == StringLiteral.create("hello", Encoding.SCREENCODES, dummyPos)) shouldBe true
        (StringLiteral.create("hello", Encoding.SCREENCODES, dummyPos) != StringLiteral.create("bye", Encoding.SCREENCODES, dummyPos)) shouldBe true
        (StringLiteral.create("hello", Encoding.SCREENCODES, dummyPos) != StringLiteral.create("hello", Encoding.PETSCII, dummyPos)) shouldBe true

        val lvOne = NumericLiteral(DataType.UBYTE, 1.0, dummyPos)
        val lvTwo = NumericLiteral(DataType.UBYTE, 2.0, dummyPos)
        val lvThree = NumericLiteral(DataType.UBYTE, 3.0, dummyPos)
        val lvOneR = NumericLiteral(DataType.UBYTE, 1.0, dummyPos)
        val lvTwoR = NumericLiteral(DataType.UBYTE, 2.0, dummyPos)
        val lvThreeR = NumericLiteral(DataType.UBYTE, 3.0, dummyPos)
        val lvFour= NumericLiteral(DataType.UBYTE, 4.0, dummyPos)
        val lv1 = ArrayLiteral(InferredTypes.InferredType.known(DataType.ARRAY_UB), arrayOf(lvOne, lvTwo, lvThree), dummyPos)
        val lv2 = ArrayLiteral(InferredTypes.InferredType.known(DataType.ARRAY_UB), arrayOf(lvOneR, lvTwoR, lvThreeR), dummyPos)
        val lv3 = ArrayLiteral(InferredTypes.InferredType.known(DataType.ARRAY_UB), arrayOf(lvOneR, lvTwoR, lvFour), dummyPos)
        lv1 shouldBe lv2
        lv1 shouldNotBe lv3
    }

    test("testGreaterThan") {
        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) > NumericLiteral(DataType.UBYTE, 99.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UWORD, 254.0, dummyPos) > NumericLiteral(DataType.UWORD, 253.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.FLOAT, 100.0, dummyPos) > NumericLiteral(DataType.FLOAT, 99.9, dummyPos)) shouldBe true

        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) >= NumericLiteral(DataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UWORD, 254.0, dummyPos) >= NumericLiteral(DataType.UWORD, 254.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.FLOAT, 100.0, dummyPos) >= NumericLiteral(DataType.FLOAT, 100.0, dummyPos)) shouldBe true

        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) > NumericLiteral(DataType.UBYTE, 100.0, dummyPos)) shouldBe false
        (NumericLiteral(DataType.UWORD, 254.0, dummyPos) > NumericLiteral(DataType.UWORD, 254.0, dummyPos)) shouldBe false
        (NumericLiteral(DataType.FLOAT, 100.0, dummyPos) > NumericLiteral(DataType.FLOAT, 100.0, dummyPos)) shouldBe false

        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) >= NumericLiteral(DataType.UBYTE, 101.0, dummyPos)) shouldBe false
        (NumericLiteral(DataType.UWORD, 254.0, dummyPos) >= NumericLiteral(DataType.UWORD, 255.0, dummyPos)) shouldBe false
        (NumericLiteral(DataType.FLOAT, 100.0, dummyPos) >= NumericLiteral(DataType.FLOAT, 100.1, dummyPos)) shouldBe false
    }

    test("testLessThan") {
        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) < NumericLiteral(DataType.UBYTE, 101.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UWORD, 254.0, dummyPos) < NumericLiteral(DataType.UWORD, 255.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.FLOAT, 100.0, dummyPos) < NumericLiteral(DataType.FLOAT, 100.1, dummyPos)) shouldBe true

        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) <= NumericLiteral(DataType.UBYTE, 100.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.UWORD, 254.0, dummyPos) <= NumericLiteral(DataType.UWORD, 254.0, dummyPos)) shouldBe true
        (NumericLiteral(DataType.FLOAT, 100.0, dummyPos) <= NumericLiteral(DataType.FLOAT, 100.0, dummyPos)) shouldBe true

        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) < NumericLiteral(DataType.UBYTE, 100.0, dummyPos)) shouldBe false
        (NumericLiteral(DataType.UWORD, 254.0, dummyPos) < NumericLiteral(DataType.UWORD, 254.0, dummyPos)) shouldBe false
        (NumericLiteral(DataType.FLOAT, 100.0, dummyPos) < NumericLiteral(DataType.FLOAT, 100.0, dummyPos)) shouldBe false

        (NumericLiteral(DataType.UBYTE, 100.0, dummyPos) <= NumericLiteral(DataType.UBYTE, 99.0, dummyPos)) shouldBe false
        (NumericLiteral(DataType.UWORD, 254.0, dummyPos) <= NumericLiteral(DataType.UWORD, 253.0, dummyPos)) shouldBe false
        (NumericLiteral(DataType.FLOAT, 100.0, dummyPos) <= NumericLiteral(DataType.FLOAT, 99.9, dummyPos)) shouldBe false
    }

    test("optimalInteger") {
        NumericLiteral.optimalInteger(10, Position.DUMMY).type shouldBe DataType.UBYTE
        NumericLiteral.optimalInteger(10, Position.DUMMY).number shouldBe 10.0
        NumericLiteral.optimalInteger(-10, Position.DUMMY).type shouldBe DataType.BYTE
        NumericLiteral.optimalInteger(-10, Position.DUMMY).number shouldBe -10.0
        NumericLiteral.optimalInteger(1000, Position.DUMMY).type shouldBe DataType.UWORD
        NumericLiteral.optimalInteger(-1000, Position.DUMMY).number shouldBe -1000.0
        NumericLiteral.optimalInteger(1000u, Position.DUMMY).type shouldBe DataType.UWORD
        NumericLiteral.optimalInteger(1000u, Position.DUMMY).number shouldBe 1000.0

        NumericLiteral.optimalInteger(DataType.UBYTE, DataType.UWORD, 1, Position.DUMMY).type shouldBe DataType.UWORD
        NumericLiteral.optimalInteger(DataType.UWORD, DataType.UBYTE, 1, Position.DUMMY).type shouldBe DataType.UWORD
        NumericLiteral.optimalInteger(DataType.UWORD, null, 1, Position.DUMMY).type shouldBe DataType.UWORD
        NumericLiteral.optimalInteger(DataType.UBYTE, DataType.UBYTE, -1, Position.DUMMY).type shouldBe DataType.BYTE
        NumericLiteral.optimalInteger(DataType.UBYTE, null, -1, Position.DUMMY).type shouldBe DataType.BYTE
        NumericLiteral.optimalInteger(DataType.UWORD, DataType.UWORD, -1, Position.DUMMY).type shouldBe DataType.WORD
        NumericLiteral.optimalInteger(DataType.UWORD, null, -1, Position.DUMMY).type shouldBe DataType.WORD
    }

    test("optimalNumeric") {
        NumericLiteral.optimalNumeric(10, Position.DUMMY).type shouldBe DataType.UBYTE
        NumericLiteral.optimalNumeric(10, Position.DUMMY).number shouldBe 10.0
        NumericLiteral.optimalNumeric(-10, Position.DUMMY).type shouldBe DataType.BYTE
        NumericLiteral.optimalNumeric(-10, Position.DUMMY).number shouldBe -10.0
        NumericLiteral.optimalNumeric(1000, Position.DUMMY).type shouldBe DataType.UWORD
        NumericLiteral.optimalNumeric(1000, Position.DUMMY).number shouldBe 1000.0
        NumericLiteral.optimalNumeric(-1000, Position.DUMMY).type shouldBe DataType.WORD
        NumericLiteral.optimalNumeric(-1000, Position.DUMMY).number shouldBe -1000.0
        NumericLiteral.optimalNumeric(1.123, Position.DUMMY).type shouldBe DataType.FLOAT
        NumericLiteral.optimalNumeric(1.123, Position.DUMMY).number shouldBe 1.123
        NumericLiteral.optimalNumeric(1.0, Position.DUMMY).type shouldBe DataType.UBYTE
        NumericLiteral.optimalNumeric(1.0, Position.DUMMY).number shouldBe 1.0
        NumericLiteral.optimalNumeric(-1.0, Position.DUMMY).type shouldBe DataType.BYTE
        NumericLiteral.optimalNumeric(-1.0, Position.DUMMY).number shouldBe -1.0
        NumericLiteral.optimalNumeric(1234.0, Position.DUMMY).type shouldBe DataType.UWORD
        NumericLiteral.optimalNumeric(1234.0, Position.DUMMY).number shouldBe 1234.0
        NumericLiteral.optimalNumeric(-1234.0, Position.DUMMY).type shouldBe DataType.WORD
        NumericLiteral.optimalNumeric(-1234.0, Position.DUMMY).number shouldBe -1234.0

        NumericLiteral.optimalNumeric(DataType.UBYTE, DataType.UWORD, 1.0, Position.DUMMY).type shouldBe DataType.UWORD
        NumericLiteral.optimalNumeric(DataType.UWORD, DataType.UBYTE, 1.0, Position.DUMMY).type shouldBe DataType.UWORD
        NumericLiteral.optimalNumeric(DataType.UWORD, null, 1.0, Position.DUMMY).type shouldBe DataType.UWORD
        NumericLiteral.optimalNumeric(DataType.UBYTE, DataType.UBYTE, -1.0, Position.DUMMY).type shouldBe DataType.BYTE
        NumericLiteral.optimalNumeric(DataType.UBYTE, null, -1.0, Position.DUMMY).type shouldBe DataType.BYTE
        NumericLiteral.optimalNumeric(DataType.UWORD, DataType.UWORD, -1.0, Position.DUMMY).type shouldBe DataType.WORD
        NumericLiteral.optimalNumeric(DataType.UWORD, null, -1.0, Position.DUMMY).type shouldBe DataType.WORD

        NumericLiteral.optimalNumeric(DataType.UBYTE, DataType.UWORD, 1.234, Position.DUMMY).type shouldBe DataType.FLOAT
        NumericLiteral.optimalNumeric(DataType.UWORD, DataType.UBYTE, 1.234, Position.DUMMY).type shouldBe DataType.FLOAT
        NumericLiteral.optimalNumeric(DataType.UWORD, null, 1.234, Position.DUMMY).type shouldBe DataType.FLOAT
        NumericLiteral.optimalNumeric(DataType.UBYTE, DataType.UBYTE, -1.234, Position.DUMMY).type shouldBe DataType.FLOAT
        NumericLiteral.optimalNumeric(DataType.UBYTE, null, -1.234, Position.DUMMY).type shouldBe DataType.FLOAT
        NumericLiteral.optimalNumeric(DataType.UWORD, DataType.UWORD, -1.234, Position.DUMMY).type shouldBe DataType.FLOAT
        NumericLiteral.optimalNumeric(DataType.UWORD, null, -1.234, Position.DUMMY).type shouldBe DataType.FLOAT
    }

    test("cast can change value") {
        fun num(dt: DataType, num: Double): NumericLiteral {
            val n = NumericLiteral(dt, num, Position.DUMMY)
            n.linkParents(AnonymousScope(mutableListOf(), Position.DUMMY))
            return n
        }
        val cast1 = num(DataType.UBYTE, 200.0).cast(DataType.BYTE, false)
        cast1.isValid shouldBe true
        cast1.valueOrZero().number shouldBe -56.0
        val cast2 = num(DataType.BYTE, -50.0).cast(DataType.UBYTE, false)
        cast2.isValid shouldBe true
        cast2.valueOrZero().number shouldBe 206.0
        val cast3 = num(DataType.UWORD, 55555.0).cast(DataType.WORD, false)
        cast3.isValid shouldBe true
        cast3.valueOrZero().number shouldBe -9981.0
        val cast4 = num(DataType.WORD, -3333.0).cast(DataType.UWORD, false)
        cast4.isValid shouldBe true
        cast4.valueOrZero().number shouldBe 62203.0
    }

    test("convert cannot change value") {
        fun num(dt: DataType, num: Double): NumericLiteral {
            val n = NumericLiteral(dt, num, Position.DUMMY)
            n.linkParents(AnonymousScope(mutableListOf(), Position.DUMMY))
            return n
        }
        num(DataType.UBYTE, 200.0).convertTypeKeepValue(DataType.BYTE).isValid shouldBe false
        num(DataType.BYTE, -50.0).convertTypeKeepValue(DataType.UBYTE).isValid shouldBe false
        num(DataType.UWORD, 55555.0).convertTypeKeepValue(DataType.WORD).isValid shouldBe false
        num(DataType.WORD, -3333.0).convertTypeKeepValue(DataType.UWORD).isValid shouldBe false

        num(DataType.UBYTE, 42.0).convertTypeKeepValue(DataType.BYTE).isValid shouldBe true
        num(DataType.BYTE, 42.0).convertTypeKeepValue(DataType.UBYTE).isValid shouldBe true
        num(DataType.UWORD, 12345.0).convertTypeKeepValue(DataType.WORD).isValid shouldBe true
        num(DataType.WORD, 12345.0).convertTypeKeepValue(DataType.UWORD).isValid shouldBe true
    }
})
