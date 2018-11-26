package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.DataType
import prog8.ast.ExpressionError
import prog8.ast.LiteralValue
import prog8.ast.Position
import prog8.compiler.intermediate.Value
import prog8.stackvm.VmExecutionException
import kotlin.test.*


private fun sameValueAndType(v1: Value, v2: Value): Boolean {
    return v1.type==v2.type && v1==v2
}

private fun sameValueAndType(lv1: LiteralValue, lv2: LiteralValue): Boolean {
    return lv1.type==lv2.type && lv1==lv2
}


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestStackVmValue {

    @Test
    fun testIdentity() {
        val v = Value(DataType.UWORD, 12345)
        assertEquals(v, v)
        assertFalse(v != v)
        assertTrue(v<=v)
        assertTrue(v>=v)
        assertFalse(v<v)
        assertFalse(v>v)

        assertTrue(sameValueAndType(Value(DataType.UBYTE, 100), Value(DataType.UBYTE, 100)))
    }

    @Test
    fun testEqualsAndNotEquals() {
        assertEquals(Value(DataType.UBYTE, 100), Value(DataType.UBYTE, 100))
        assertEquals(Value(DataType.UBYTE, 100), Value(DataType.UWORD, 100))
        assertEquals(Value(DataType.UBYTE, 100), Value(DataType.FLOAT, 100))
        assertEquals(Value(DataType.UWORD, 254), Value(DataType.UBYTE, 254))
        assertEquals(Value(DataType.UWORD, 12345), Value(DataType.UWORD, 12345))
        assertEquals(Value(DataType.UWORD, 12345), Value(DataType.FLOAT, 12345))
        assertEquals(Value(DataType.FLOAT, 100.0), Value(DataType.UBYTE, 100))
        assertEquals(Value(DataType.FLOAT, 22239.0), Value(DataType.UWORD, 22239))
        assertEquals(Value(DataType.FLOAT, 9.99), Value(DataType.FLOAT, 9.99))

        assertTrue(sameValueAndType(Value(DataType.UBYTE, 100), Value(DataType.UBYTE, 100)))
        assertFalse(sameValueAndType(Value(DataType.UBYTE, 100), Value(DataType.UWORD, 100)))
        assertFalse(sameValueAndType(Value(DataType.UBYTE, 100), Value(DataType.FLOAT, 100)))
        assertFalse(sameValueAndType(Value(DataType.UWORD, 254), Value(DataType.UBYTE, 254)))
        assertTrue(sameValueAndType(Value(DataType.UWORD, 12345), Value(DataType.UWORD, 12345)))
        assertFalse(sameValueAndType(Value(DataType.UWORD, 12345), Value(DataType.FLOAT, 12345)))
        assertFalse(sameValueAndType(Value(DataType.FLOAT, 100.0), Value(DataType.UBYTE, 100)))
        assertFalse(sameValueAndType(Value(DataType.FLOAT, 22239.0), Value(DataType.UWORD, 22239)))
        assertTrue(sameValueAndType(Value(DataType.FLOAT, 9.99), Value(DataType.FLOAT, 9.99)))

        assertNotEquals(Value(DataType.UBYTE, 100), Value(DataType.UBYTE, 101))
        assertNotEquals(Value(DataType.UBYTE, 100), Value(DataType.UWORD, 101))
        assertNotEquals(Value(DataType.UBYTE, 100), Value(DataType.FLOAT, 101))
        assertNotEquals(Value(DataType.UWORD, 245), Value(DataType.UBYTE, 246))
        assertNotEquals(Value(DataType.UWORD, 12345), Value(DataType.UWORD, 12346))
        assertNotEquals(Value(DataType.UWORD, 12345), Value(DataType.FLOAT, 12346))
        assertNotEquals(Value(DataType.FLOAT, 9.99), Value(DataType.UBYTE, 9))
        assertNotEquals(Value(DataType.FLOAT, 9.99), Value(DataType.UWORD, 9))
        assertNotEquals(Value(DataType.FLOAT, 9.99), Value(DataType.FLOAT, 9.0))

        assertFalse(sameValueAndType(Value(DataType.UBYTE, 100), Value(DataType.UBYTE, 101)))
        assertFalse(sameValueAndType(Value(DataType.UBYTE, 100), Value(DataType.UWORD, 101)))
        assertFalse(sameValueAndType(Value(DataType.UBYTE, 100), Value(DataType.FLOAT, 101)))
        assertFalse(sameValueAndType(Value(DataType.UWORD, 245), Value(DataType.UBYTE, 246)))
        assertFalse(sameValueAndType(Value(DataType.UWORD, 12345), Value(DataType.UWORD, 12346)))
        assertFalse(sameValueAndType(Value(DataType.UWORD, 12345), Value(DataType.FLOAT, 12346)))
        assertFalse(sameValueAndType(Value(DataType.FLOAT, 9.99), Value(DataType.UBYTE, 9)))
        assertFalse(sameValueAndType(Value(DataType.FLOAT, 9.99), Value(DataType.UWORD, 9)))
        assertFalse(sameValueAndType(Value(DataType.FLOAT, 9.99), Value(DataType.FLOAT, 9.0)))
    }

    @Test
    fun testEqualsAndNotEqualsHeapTypes()
    {
        assertTrue(sameValueAndType(Value(DataType.STR, 999), Value(DataType.STR, 999)))
        assertFalse(sameValueAndType(Value(DataType.STR, 999), Value(DataType.STR_P, 999)))
        assertFalse(sameValueAndType(Value(DataType.STR, 999), Value(DataType.STR, 222)))

        assertTrue(sameValueAndType(Value(DataType.ARRAY_UB, 99), Value(DataType.ARRAY_UB, 99)))
        assertFalse(sameValueAndType(Value(DataType.ARRAY_UB, 99), Value(DataType.ARRAY_UB, 22)))

        assertTrue(sameValueAndType(Value(DataType.ARRAY_UW, 999), Value(DataType.ARRAY_UW, 999)))
        assertFalse(sameValueAndType(Value(DataType.ARRAY_UW, 999), Value(DataType.ARRAY_UW, 222)))

        assertTrue(sameValueAndType(Value(DataType.ARRAY_F, 999), Value(DataType.ARRAY_F, 999)))
        assertFalse(sameValueAndType(Value(DataType.ARRAY_F, 999), Value(DataType.ARRAY_UW, 999)))
        assertFalse(sameValueAndType(Value(DataType.ARRAY_F, 999), Value(DataType.ARRAY_F, 222)))
    }

    @Test
    fun testGreaterThan(){
        assertTrue(Value(DataType.UBYTE, 100) > Value(DataType.UBYTE, 99))
        assertTrue(Value(DataType.UWORD, 254) > Value(DataType.UWORD, 253))
        assertTrue(Value(DataType.FLOAT, 100.0) > Value(DataType.FLOAT, 99.9))

        assertTrue(Value(DataType.UBYTE, 100) >= Value(DataType.UBYTE, 100))
        assertTrue(Value(DataType.UWORD, 254) >= Value(DataType.UWORD, 254))
        assertTrue(Value(DataType.FLOAT, 100.0) >= Value(DataType.FLOAT, 100.0))

        assertFalse(Value(DataType.UBYTE, 100) > Value(DataType.UBYTE, 100))
        assertFalse(Value(DataType.UWORD, 254) > Value(DataType.UWORD, 254))
        assertFalse(Value(DataType.FLOAT, 100.0) > Value(DataType.FLOAT, 100.0))

        assertFalse(Value(DataType.UBYTE, 100) >= Value(DataType.UBYTE, 101))
        assertFalse(Value(DataType.UWORD, 254) >= Value(DataType.UWORD, 255))
        assertFalse(Value(DataType.FLOAT, 100.0) >= Value(DataType.FLOAT, 100.1))
    }

    @Test
    fun testLessThan() {
        assertTrue(Value(DataType.UBYTE, 100) < Value(DataType.UBYTE, 101))
        assertTrue(Value(DataType.UWORD, 254) < Value(DataType.UWORD, 255))
        assertTrue(Value(DataType.FLOAT, 100.0) < Value(DataType.FLOAT, 100.1))

        assertTrue(Value(DataType.UBYTE, 100) <= Value(DataType.UBYTE, 100))
        assertTrue(Value(DataType.UWORD, 254) <= Value(DataType.UWORD, 254))
        assertTrue(Value(DataType.FLOAT, 100.0) <= Value(DataType.FLOAT, 100.0))

        assertFalse(Value(DataType.UBYTE, 100) < Value(DataType.UBYTE, 100))
        assertFalse(Value(DataType.UWORD, 254) < Value(DataType.UWORD, 254))
        assertFalse(Value(DataType.FLOAT, 100.0) < Value(DataType.FLOAT, 100.0))

        assertFalse(Value(DataType.UBYTE, 100) <= Value(DataType.UBYTE, 99))
        assertFalse(Value(DataType.UWORD, 254) <= Value(DataType.UWORD, 253))
        assertFalse(Value(DataType.FLOAT, 100.0) <= Value(DataType.FLOAT, 99.9))
    }

    @Test
    fun testNoDtConversion() {
        assertFailsWith<VmExecutionException> {
            Value(DataType.UWORD, 100).add(Value(DataType.UBYTE, 120))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.UBYTE, 100).add(Value(DataType.UWORD, 120))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.FLOAT, 100.22).add(Value(DataType.UWORD, 120))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.UWORD, 1002).add(Value(DataType.FLOAT, 120.22))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.FLOAT, 100.22).add(Value(DataType.UBYTE, 120))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.UBYTE, 12).add(Value(DataType.FLOAT, 120.22))
        }
    }

    @Test
    fun testNoAutoFloatConversion() {
        assertFailsWith<VmExecutionException> {
            Value(DataType.UBYTE, 233).add(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.UWORD, 233).add(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.UBYTE, 233).mul(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.UWORD, 233).mul(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.UBYTE, 233).div(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<VmExecutionException> {
            Value(DataType.UWORD, 233).div(Value(DataType.FLOAT, 1.234))
        }
        val result = Value(DataType.FLOAT, 233.333).add(Value(DataType.FLOAT, 1.234))
    }
}


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestParserLiteralValue {

    private val dummyPos = Position("test", 0,0,0)

    @Test
    fun testIdentity() {
        val v = LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos)
        assertEquals(v, v)
        assertFalse(v != v)
        assertTrue(v <= v)
        assertTrue(v >= v)
        assertFalse(v < v)
        assertFalse(v > v)

        assertTrue(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos)))
    }

    @Test
    fun testEqualsAndNotEquals() {
        assertEquals(LiteralValue(DataType.UBYTE, 100, position=dummyPos), LiteralValue(DataType.UBYTE, 100, position=dummyPos))
        assertEquals(LiteralValue(DataType.UBYTE, 100, position=dummyPos), LiteralValue(DataType.UWORD, wordvalue=100, position=dummyPos))
        assertEquals(LiteralValue(DataType.UBYTE, 100, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos))
        assertEquals(LiteralValue(DataType.UWORD, wordvalue=254, position=dummyPos), LiteralValue(DataType.UBYTE, 254, position=dummyPos))
        assertEquals(LiteralValue(DataType.UWORD, wordvalue=12345, position=dummyPos), LiteralValue(DataType.UWORD, wordvalue=12345, position=dummyPos))
        assertEquals(LiteralValue(DataType.UWORD, wordvalue=12345, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=12345.0, position=dummyPos))
        assertEquals(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos), LiteralValue(DataType.UBYTE, 100, position=dummyPos))
        assertEquals(LiteralValue(DataType.FLOAT, floatvalue=22239.0, position=dummyPos), LiteralValue(DataType.UWORD,wordvalue=22239, position=dummyPos))
        assertEquals(LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos))

        assertTrue(sameValueAndType(LiteralValue(DataType.UBYTE, 100, position=dummyPos), LiteralValue(DataType.UBYTE, 100, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UBYTE, 100, position=dummyPos), LiteralValue(DataType.UWORD, wordvalue=100, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UBYTE, 100, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue=254, position=dummyPos), LiteralValue(DataType.UBYTE, 254, position=dummyPos)))
        assertTrue(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue=12345, position=dummyPos), LiteralValue(DataType.UWORD, wordvalue=12345, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue=12345, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=12345.0, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos), LiteralValue(DataType.UBYTE, 100, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.FLOAT, floatvalue=22239.0, position=dummyPos), LiteralValue(DataType.UWORD,wordvalue=22239, position=dummyPos)))
        assertTrue(sameValueAndType(LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos)))

        assertNotEquals(LiteralValue(DataType.UBYTE, 100, position=dummyPos), LiteralValue(DataType.UBYTE, 101, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.UBYTE, 100, position=dummyPos), LiteralValue(DataType.UWORD, wordvalue=101, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.UBYTE, 100, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=101.0, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.UWORD, wordvalue=245, position=dummyPos), LiteralValue(DataType.UBYTE, 246, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.UWORD, wordvalue=12345, position=dummyPos), LiteralValue(DataType.UWORD, wordvalue=12346, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.UWORD, wordvalue=12345, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=12346.0, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos), LiteralValue(DataType.UBYTE, 9, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos), LiteralValue(DataType.UWORD, wordvalue=9, position=dummyPos))
        assertNotEquals(LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=9.0, position=dummyPos))

        assertFalse(sameValueAndType(LiteralValue(DataType.UBYTE, 100, position=dummyPos), LiteralValue(DataType.UBYTE, 101, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UBYTE, 100, position=dummyPos), LiteralValue(DataType.UWORD, wordvalue=101, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UBYTE, 100, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=101.0, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue=245, position=dummyPos), LiteralValue(DataType.UBYTE, 246, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue=12345, position=dummyPos), LiteralValue(DataType.UWORD, wordvalue=12346, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue=12345, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=12346.0, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos), LiteralValue(DataType.UBYTE, 9, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos), LiteralValue(DataType.UWORD, wordvalue=9, position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.FLOAT, floatvalue=9.99, position=dummyPos), LiteralValue(DataType.FLOAT, floatvalue=9.0, position=dummyPos)))

        assertTrue(sameValueAndType(LiteralValue(DataType.STR, strvalue = "hello", position=dummyPos), LiteralValue(DataType.STR, strvalue="hello", position=dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.STR, strvalue = "hello", position=dummyPos), LiteralValue(DataType.STR, strvalue="bye", position=dummyPos)))

        val lvOne = LiteralValue(DataType.UBYTE, 1, position=dummyPos)
        val lvTwo = LiteralValue(DataType.UBYTE, 2, position=dummyPos)
        val lvThree = LiteralValue(DataType.UBYTE, 3, position=dummyPos)
        val lvOneR = LiteralValue(DataType.UBYTE, 1, position=dummyPos)
        val lvTwoR = LiteralValue(DataType.UBYTE, 2, position=dummyPos)
        val lvThreeR = LiteralValue(DataType.UBYTE, 3, position=dummyPos)
        val lvFour= LiteralValue(DataType.UBYTE, 4, position=dummyPos)
        val lv1 = LiteralValue(DataType.ARRAY_UB, arrayvalue = arrayOf(lvOne, lvTwo, lvThree), position=dummyPos)
        val lv2 = LiteralValue(DataType.ARRAY_UB, arrayvalue = arrayOf(lvOneR, lvTwoR, lvThreeR), position=dummyPos)
        val lv3 = LiteralValue(DataType.ARRAY_UB, arrayvalue = arrayOf(lvOneR, lvTwoR, lvFour), position=dummyPos)
        assertEquals(lv1, lv2)
        assertNotEquals(lv1, lv3)
    }

    @Test
    fun testGreaterThan(){
        assertTrue(LiteralValue(DataType.UBYTE, 100, position=dummyPos) > LiteralValue(DataType.UBYTE, 99, position=dummyPos))
        assertTrue(LiteralValue(DataType.UWORD, wordvalue=254, position=dummyPos) > LiteralValue(DataType.UWORD, wordvalue=253, position=dummyPos))
        assertTrue(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) > LiteralValue(DataType.FLOAT, floatvalue=99.9, position=dummyPos))

        assertTrue(LiteralValue(DataType.UBYTE, 100, position=dummyPos) >= LiteralValue(DataType.UBYTE, 100, position=dummyPos))
        assertTrue(LiteralValue(DataType.UWORD, wordvalue=254, position=dummyPos) >= LiteralValue(DataType.UWORD,wordvalue= 254, position=dummyPos))
        assertTrue(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) >= LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos))

        assertFalse(LiteralValue(DataType.UBYTE, 100, position=dummyPos) > LiteralValue(DataType.UBYTE, 100, position=dummyPos))
        assertFalse(LiteralValue(DataType.UWORD, wordvalue=254, position=dummyPos) > LiteralValue(DataType.UWORD, wordvalue=254, position=dummyPos))
        assertFalse(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) > LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos))

        assertFalse(LiteralValue(DataType.UBYTE, 100, position=dummyPos) >= LiteralValue(DataType.UBYTE, 101, position=dummyPos))
        assertFalse(LiteralValue(DataType.UWORD, wordvalue=254, position=dummyPos) >= LiteralValue(DataType.UWORD,wordvalue= 255, position=dummyPos))
        assertFalse(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) >= LiteralValue(DataType.FLOAT, floatvalue=100.1, position=dummyPos))
    }

    @Test
    fun testLessThan() {
        assertTrue(LiteralValue(DataType.UBYTE, 100, position=dummyPos) < LiteralValue(DataType.UBYTE, 101, position=dummyPos))
        assertTrue(LiteralValue(DataType.UWORD, wordvalue=254, position=dummyPos) < LiteralValue(DataType.UWORD, wordvalue=255, position=dummyPos))
        assertTrue(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) < LiteralValue(DataType.FLOAT, floatvalue=100.1, position=dummyPos))

        assertTrue(LiteralValue(DataType.UBYTE, 100, position=dummyPos) <= LiteralValue(DataType.UBYTE, 100, position=dummyPos))
        assertTrue(LiteralValue(DataType.UWORD, wordvalue=254, position=dummyPos) <= LiteralValue(DataType.UWORD,wordvalue= 254, position=dummyPos))
        assertTrue(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) <= LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos))

        assertFalse(LiteralValue(DataType.UBYTE, 100, position=dummyPos) < LiteralValue(DataType.UBYTE, 100, position=dummyPos))
        assertFalse(LiteralValue(DataType.UWORD, wordvalue=254, position=dummyPos) < LiteralValue(DataType.UWORD, wordvalue=254, position=dummyPos))
        assertFalse(LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos) < LiteralValue(DataType.FLOAT, floatvalue=100.0, position=dummyPos))

        assertFalse(LiteralValue(DataType.UBYTE, 100, position=dummyPos) <= LiteralValue(DataType.UBYTE, 99, position=dummyPos))
        assertFalse(LiteralValue(DataType.UWORD,wordvalue= 254, position=dummyPos) <= LiteralValue(DataType.UWORD,wordvalue= 253, position=dummyPos))
        assertFalse(LiteralValue(DataType.FLOAT,floatvalue= 100.0, position=dummyPos) <= LiteralValue(DataType.FLOAT, floatvalue=99.9, position=dummyPos))
    }

}

