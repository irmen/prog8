package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.DataType
import prog8.ast.ExpressionError
import prog8.ast.LiteralValue
import prog8.ast.Position
import prog8.stackvm.Value
import prog8.stackvm.VmExecutionException
import kotlin.test.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestStackVmValue {

    @Test
    fun testIdentity() {
        val v = Value(DataType.WORD, 12345)
        assertEquals(v, v)
        assertFalse(v != v)
        assertTrue(v<=v)
        assertTrue(v>=v)
        assertFalse(v<v)
        assertFalse(v>v)

        assertEquals(Value(DataType.BYTE, 100), Value(DataType.BYTE, 100))
    }

    @Test
    fun testEqualsAndNotEquals() {
        assertEquals(Value(DataType.BYTE, 100), Value(DataType.BYTE, 100))
        assertEquals(Value(DataType.BYTE, 100), Value(DataType.WORD, 100))
        assertEquals(Value(DataType.BYTE, 100), Value(DataType.FLOAT, 100))
        assertEquals(Value(DataType.WORD, 254), Value(DataType.BYTE, 254))
        assertEquals(Value(DataType.WORD, 12345), Value(DataType.WORD, 12345))
        assertEquals(Value(DataType.WORD, 12345), Value(DataType.FLOAT, 12345))
        assertEquals(Value(DataType.FLOAT, 100.0), Value(DataType.BYTE, 100))
        assertEquals(Value(DataType.FLOAT, 22239.0), Value(DataType.WORD, 22239))
        assertEquals(Value(DataType.FLOAT, 9.99), Value(DataType.FLOAT, 9.99))

        assertNotEquals(Value(DataType.BYTE, 100), Value(DataType.BYTE, 101))
        assertNotEquals(Value(DataType.BYTE, 100), Value(DataType.WORD, 101))
        assertNotEquals(Value(DataType.BYTE, 100), Value(DataType.FLOAT, 101))
        assertNotEquals(Value(DataType.WORD, 245), Value(DataType.BYTE, 246))
        assertNotEquals(Value(DataType.WORD, 12345), Value(DataType.WORD, 12346))
        assertNotEquals(Value(DataType.WORD, 12345), Value(DataType.FLOAT, 12346))
        assertNotEquals(Value(DataType.FLOAT, 9.99), Value(DataType.BYTE, 9))
        assertNotEquals(Value(DataType.FLOAT, 9.99), Value(DataType.WORD, 9))
        assertNotEquals(Value(DataType.FLOAT, 9.99), Value(DataType.FLOAT, 9.0))

        assertFailsWith<VmExecutionException> {
            assertEquals(Value(DataType.STR, null, "hello"), Value(DataType.STR, null, "hello"))
        }
        assertFailsWith<VmExecutionException> {
            assertEquals(Value(DataType.ARRAY, null, arrayvalue = intArrayOf(1,2,3)), Value(DataType.ARRAY, null, arrayvalue = intArrayOf(1,2,3)))
        }
    }

    @Test
    fun testGreaterThan(){
        assertTrue(Value(DataType.BYTE, 100) > Value(DataType.BYTE, 99))
        assertTrue(Value(DataType.WORD, 254) > Value(DataType.WORD, 253))
        assertTrue(Value(DataType.FLOAT, 100.0) > Value(DataType.FLOAT, 99.9))

        assertTrue(Value(DataType.BYTE, 100) >= Value(DataType.BYTE, 100))
        assertTrue(Value(DataType.WORD, 254) >= Value(DataType.WORD, 254))
        assertTrue(Value(DataType.FLOAT, 100.0) >= Value(DataType.FLOAT, 100.0))

        assertFalse(Value(DataType.BYTE, 100) > Value(DataType.BYTE, 100))
        assertFalse(Value(DataType.WORD, 254) > Value(DataType.WORD, 254))
        assertFalse(Value(DataType.FLOAT, 100.0) > Value(DataType.FLOAT, 100.0))

        assertFalse(Value(DataType.BYTE, 100) >= Value(DataType.BYTE, 101))
        assertFalse(Value(DataType.WORD, 254) >= Value(DataType.WORD, 255))
        assertFalse(Value(DataType.FLOAT, 100.0) >= Value(DataType.FLOAT, 100.1))
    }

    @Test
    fun testLessThan() {
        assertTrue(Value(DataType.BYTE, 100) < Value(DataType.BYTE, 101))
        assertTrue(Value(DataType.WORD, 254) < Value(DataType.WORD, 255))
        assertTrue(Value(DataType.FLOAT, 100.0) < Value(DataType.FLOAT, 100.1))

        assertTrue(Value(DataType.BYTE, 100) <= Value(DataType.BYTE, 100))
        assertTrue(Value(DataType.WORD, 254) <= Value(DataType.WORD, 254))
        assertTrue(Value(DataType.FLOAT, 100.0) <= Value(DataType.FLOAT, 100.0))

        assertFalse(Value(DataType.BYTE, 100) < Value(DataType.BYTE, 100))
        assertFalse(Value(DataType.WORD, 254) < Value(DataType.WORD, 254))
        assertFalse(Value(DataType.FLOAT, 100.0) < Value(DataType.FLOAT, 100.0))

        assertFalse(Value(DataType.BYTE, 100) <= Value(DataType.BYTE, 99))
        assertFalse(Value(DataType.WORD, 254) <= Value(DataType.WORD, 253))
        assertFalse(Value(DataType.FLOAT, 100.0) <= Value(DataType.FLOAT, 99.9))
    }

    @Test
    fun testArithmeticByteFirstOperand() {
        var r = Value(DataType.BYTE, 100).add(Value(DataType.BYTE, 120))
        assertEquals(DataType.BYTE, r.type)
        assertEquals(220, r.integerValue())

        r = Value(DataType.BYTE, 100).add(Value(DataType.BYTE, 199))
        assertEquals(DataType.WORD, r.type)
        assertEquals(299, r.integerValue())

        r = Value(DataType.BYTE, 100).sub(Value(DataType.BYTE, 88))
        assertEquals(DataType.BYTE, r.type)
        assertEquals(12, r.integerValue())

        r = Value(DataType.BYTE, 100).sub(Value(DataType.BYTE, 188))
        assertEquals(DataType.BYTE, r.type)
        assertEquals(168, r.integerValue())

        r = Value(DataType.BYTE, 5).mul(Value(DataType.BYTE, 33))
        assertEquals(DataType.BYTE, r.type)
        assertEquals(165, r.integerValue())

        r = Value(DataType.BYTE, 22).mul(Value(DataType.BYTE, 33))
        assertEquals(DataType.WORD, r.type)
        assertEquals(726, r.integerValue())

        r = Value(DataType.BYTE, 233).div(Value(DataType.BYTE, 12))
        assertEquals(DataType.BYTE, r.type)
        assertEquals(19, r.integerValue())

        r = Value(DataType.BYTE, 233).div(Value(DataType.BYTE, 0))
        assertEquals(DataType.BYTE, r.type)
        assertEquals(255, r.integerValue())

        r = Value(DataType.BYTE, 233).floordiv(Value(DataType.BYTE, 19))
        assertEquals(DataType.BYTE, r.type)
        assertEquals(12, r.integerValue())


        r = Value(DataType.BYTE, 100).add(Value(DataType.WORD, 120))
        assertEquals(DataType.WORD, r.type)
        assertEquals(220, r.integerValue())

        r = Value(DataType.BYTE, 100).add(Value(DataType.WORD, 199))
        assertEquals(DataType.WORD, r.type)
        assertEquals(299, r.integerValue())

        r = Value(DataType.BYTE, 100).sub(Value(DataType.WORD, 88))
        assertEquals(DataType.WORD, r.type)
        assertEquals(12, r.integerValue())

        r = Value(DataType.BYTE, 100).sub(Value(DataType.WORD, 188))
        assertEquals(DataType.WORD, r.type)
        assertEquals(65448, r.integerValue())

        r = Value(DataType.BYTE, 5).mul(Value(DataType.WORD, 33))
        assertEquals(DataType.WORD, r.type)
        assertEquals(165, r.integerValue())

        r = Value(DataType.BYTE, 22).mul(Value(DataType.WORD, 33))
        assertEquals(DataType.WORD, r.type)
        assertEquals(726, r.integerValue())

        r = Value(DataType.BYTE, 233).div(Value(DataType.WORD, 12))
        assertEquals(DataType.BYTE, r.type)
        assertEquals(19, r.integerValue())

        r = Value(DataType.BYTE, 233).div(Value(DataType.WORD, 0))
        assertEquals(DataType.BYTE, r.type)
        assertEquals(255, r.integerValue())

        r = Value(DataType.BYTE, 233).floordiv(Value(DataType.WORD, 19))
        assertEquals(DataType.BYTE, r.type)
        assertEquals(12, r.integerValue())
    }

    @Test
    fun testArithmeticWordFirstOperand() {
        var r = Value(DataType.WORD, 100).add(Value(DataType.BYTE, 120))
        assertEquals(DataType.WORD, r.type)
        assertEquals(220, r.integerValue())

        r = Value(DataType.WORD, 100).div(Value(DataType.BYTE, 10))
        assertEquals(DataType.WORD, r.type)
        assertEquals(10, r.integerValue())

        r = Value(DataType.WORD, 100).div(Value(DataType.BYTE, 0))
        assertEquals(DataType.WORD, r.type)
        assertEquals(65535, r.integerValue())

        r = Value(DataType.WORD, 100).div(Value(DataType.WORD, 0))
        assertEquals(DataType.WORD, r.type)
        assertEquals(65535, r.integerValue())

        r = Value(DataType.WORD, 33445).floordiv(Value(DataType.WORD, 123))
        assertEquals(DataType.WORD, r.type)
        assertEquals(271, r.integerValue())

        r = Value(DataType.WORD, 33445).floordiv(Value(DataType.WORD, 999))
        assertEquals(DataType.WORD, r.type)
        assertEquals(33, r.integerValue())
    }

    @Test
    fun testNoAutoFloatConversion() {
        assertFailsWith<VmExecutionException> {
            Value(DataType.BYTE, 233).add(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.WORD, 233).add(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.BYTE, 233).mul(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.WORD, 233).mul(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.BYTE, 233).div(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.WORD, 233).div(Value(DataType.FLOAT, 1.234))
        }
        val result = Value(DataType.FLOAT, 233.333).add(Value(DataType.FLOAT, 1.234))
    }
}


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestParserLiteralValue {

    private val dummyPos = Position("test", 0,0,0)

    @Test
    fun testIdentity() {
        val v = LiteralValue(DataType.WORD, wordvalue = 12345, position = dummyPos)
        assertEquals(v, v)
        assertFalse(v != v)
        assertTrue(v <= v)
        assertTrue(v >= v)
        assertFalse(v < v)
        assertFalse(v > v)

        assertEquals(LiteralValue(DataType.WORD, wordvalue = 12345, position = dummyPos), LiteralValue(DataType.WORD, wordvalue = 12345, position = dummyPos))
    }

    @Test
    fun testEqualsAndNotEquals() {
        assertEquals(LiteralValue(DataType.BYTE, 100, position=dummyPos), LiteralValue(DataType.BYTE, 100, position=dummyPos))
        assertEquals(LiteralValue(DataType.BYTE, 100, position=dummyPos), LiteralValue(DataType.WORD, wordvalue=100, position=dummyPos))
        assertEquals(LiteralValue(DataType.BYTE, 100, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos))
        assertEquals(LiteralValue(DataType.WORD, wordvalue=254, position=dummyPos), LiteralValue(DataType.BYTE, 254, position=dummyPos))
        assertEquals(LiteralValue(DataType.WORD, wordvalue=12345, position=dummyPos), LiteralValue(DataType.WORD, wordvalue=12345, position=dummyPos))
        assertEquals(LiteralValue(DataType.WORD, wordvalue=12345, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=12345.0, position=dummyPos))
        assertEquals(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos), LiteralValue(DataType.BYTE, 100, position=dummyPos))
        assertEquals(LiteralValue(DataType.FLOAT, floatvalue=22239.0, position=dummyPos), LiteralValue(DataType.WORD,wordvalue=22239, position=dummyPos))
        assertEquals(LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos))

        assertNotEquals(LiteralValue(DataType.BYTE, 100, position=dummyPos), LiteralValue(DataType.BYTE, 101, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.BYTE, 100, position=dummyPos), LiteralValue(DataType.WORD, wordvalue=101, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.BYTE, 100, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=101.0, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.WORD, wordvalue=245, position=dummyPos), LiteralValue(DataType.BYTE, 246, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.WORD, wordvalue=12345, position=dummyPos), LiteralValue(DataType.WORD, wordvalue=12346, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.WORD, wordvalue=12345, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=12346.0, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos), LiteralValue(DataType.BYTE, 9, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos), LiteralValue(DataType.WORD, wordvalue=9, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=9.0, position=dummyPos))

        assertEquals(LiteralValue(DataType.STR, strvalue = "hello", position=dummyPos), LiteralValue(DataType.STR, strvalue="hello", position=dummyPos))
        assertNotEquals(LiteralValue(DataType.STR, strvalue = "hello", position=dummyPos), LiteralValue(DataType.STR, strvalue="bye", position=dummyPos))

        val lvOne = LiteralValue(DataType.BYTE, 1, position=dummyPos)
        val lvTwo = LiteralValue(DataType.BYTE, 2, position=dummyPos)
        val lvThree = LiteralValue(DataType.BYTE, 3, position=dummyPos)
        val lvOneR = LiteralValue(DataType.BYTE, 1, position=dummyPos)
        val lvTwoR = LiteralValue(DataType.BYTE, 2, position=dummyPos)
        val lvThreeR = LiteralValue(DataType.BYTE, 3, position=dummyPos)
        val lv1 = LiteralValue(DataType.ARRAY, arrayvalue = arrayOf(lvOne, lvTwo, lvThree), position=dummyPos)
        val lv2 = LiteralValue(DataType.ARRAY, arrayvalue = arrayOf(lvOneR, lvTwoR, lvThreeR), position=dummyPos)
        assertFailsWith<ExpressionError> {
            assertEquals(lv1, lv2)
        }
    }

    @Test
    fun testGreaterThan(){
        assertTrue(LiteralValue(DataType.BYTE, 100, position=dummyPos) > LiteralValue(DataType.BYTE, 99, position=dummyPos))
        assertTrue(LiteralValue(DataType.WORD, wordvalue=254, position=dummyPos) > LiteralValue(DataType.WORD, wordvalue=253, position=dummyPos))
        assertTrue(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) > LiteralValue(DataType.FLOAT, floatvalue=99.9, position=dummyPos))

        assertTrue(LiteralValue(DataType.BYTE, 100, position=dummyPos) >= LiteralValue(DataType.BYTE, 100, position=dummyPos))
        assertTrue(LiteralValue(DataType.WORD, wordvalue=254, position=dummyPos) >= LiteralValue(DataType.WORD,wordvalue= 254, position=dummyPos))
        assertTrue(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) >= LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos))

        assertFalse(LiteralValue(DataType.BYTE, 100, position=dummyPos) > LiteralValue(DataType.BYTE, 100, position=dummyPos))
        assertFalse(LiteralValue(DataType.WORD, wordvalue=254, position=dummyPos) > LiteralValue(DataType.WORD, wordvalue=254, position=dummyPos))
        assertFalse(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) > LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos))

        assertFalse(LiteralValue(DataType.BYTE, 100, position=dummyPos) >= LiteralValue(DataType.BYTE, 101, position=dummyPos))
        assertFalse(LiteralValue(DataType.WORD, wordvalue=254, position=dummyPos) >= LiteralValue(DataType.WORD,wordvalue= 255, position=dummyPos))
        assertFalse(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) >= LiteralValue(DataType.FLOAT, floatvalue=100.1, position=dummyPos))
    }

    @Test
    fun testLessThan() {
        assertTrue(LiteralValue(DataType.BYTE, 100, position=dummyPos) < LiteralValue(DataType.BYTE, 101, position=dummyPos))
        assertTrue(LiteralValue(DataType.WORD, wordvalue=254, position=dummyPos) < LiteralValue(DataType.WORD, wordvalue=255, position=dummyPos))
        assertTrue(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) < LiteralValue(DataType.FLOAT, floatvalue=100.1, position=dummyPos))

        assertTrue(LiteralValue(DataType.BYTE, 100, position=dummyPos) <= LiteralValue(DataType.BYTE, 100, position=dummyPos))
        assertTrue(LiteralValue(DataType.WORD, wordvalue=254, position=dummyPos) <= LiteralValue(DataType.WORD,wordvalue= 254, position=dummyPos))
        assertTrue(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) <= LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos))

        assertFalse(LiteralValue(DataType.BYTE, 100, position=dummyPos) < LiteralValue(DataType.BYTE, 100, position=dummyPos))
        assertFalse(LiteralValue(DataType.WORD, wordvalue=254, position=dummyPos) < LiteralValue(DataType.WORD, wordvalue=254, position=dummyPos))
        assertFalse(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) < LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos))

        assertFalse(LiteralValue(DataType.BYTE, 100, position=dummyPos) <= LiteralValue(DataType.BYTE, 99, position=dummyPos))
        assertFalse(LiteralValue(DataType.WORD,wordvalue= 254, position=dummyPos) <= LiteralValue(DataType.WORD,wordvalue= 253, position=dummyPos))
        assertFalse(LiteralValue(DataType.FLOAT,floatvalue= 100.0, position=dummyPos) <= LiteralValue(DataType.FLOAT, floatvalue=99.9, position=dummyPos))
    }

}

