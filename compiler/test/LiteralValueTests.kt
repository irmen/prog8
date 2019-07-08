package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.DataType
import prog8.ast.expressions.LiteralValue
import prog8.ast.base.Position
import kotlin.test.*


private fun sameValueAndType(lv1: LiteralValue, lv2: LiteralValue): Boolean {
    return lv1.type==lv2.type && lv1==lv2
}


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestParserLiteralValue {

    private val dummyPos = Position("test", 0, 0, 0)

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
        assertEquals(LiteralValue(DataType.UBYTE, 100, position = dummyPos), LiteralValue(DataType.UBYTE, 100, position = dummyPos))
        assertEquals(LiteralValue(DataType.UBYTE, 100, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 100, position = dummyPos))
        assertEquals(LiteralValue(DataType.UBYTE, 100, position = dummyPos), LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos))
        assertEquals(LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos), LiteralValue(DataType.UBYTE, 254, position = dummyPos))
        assertEquals(LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos))
        assertEquals(LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos), LiteralValue(DataType.FLOAT, floatvalue = 12345.0, position = dummyPos))
        assertEquals(LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos), LiteralValue(DataType.UBYTE, 100, position = dummyPos))
        assertEquals(LiteralValue(DataType.FLOAT, floatvalue = 22239.0, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 22239, position = dummyPos))
        assertEquals(LiteralValue(DataType.FLOAT, floatvalue = 9.99, position = dummyPos), LiteralValue(DataType.FLOAT, floatvalue = 9.99, position = dummyPos))

        assertTrue(sameValueAndType(LiteralValue(DataType.UBYTE, 100, position = dummyPos), LiteralValue(DataType.UBYTE, 100, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UBYTE, 100, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 100, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UBYTE, 100, position = dummyPos), LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos), LiteralValue(DataType.UBYTE, 254, position = dummyPos)))
        assertTrue(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos), LiteralValue(DataType.FLOAT, floatvalue = 12345.0, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos), LiteralValue(DataType.UBYTE, 100, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.FLOAT, floatvalue = 22239.0, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 22239, position = dummyPos)))
        assertTrue(sameValueAndType(LiteralValue(DataType.FLOAT, floatvalue = 9.99, position = dummyPos), LiteralValue(DataType.FLOAT, floatvalue = 9.99, position = dummyPos)))

        assertNotEquals(LiteralValue(DataType.UBYTE, 100, position = dummyPos), LiteralValue(DataType.UBYTE, 101, position = dummyPos))
        assertNotEquals(LiteralValue(DataType.UBYTE, 100, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 101, position = dummyPos))
        assertNotEquals(LiteralValue(DataType.UBYTE, 100, position = dummyPos), LiteralValue(DataType.FLOAT, floatvalue = 101.0, position = dummyPos))
        assertNotEquals(LiteralValue(DataType.UWORD, wordvalue = 245, position = dummyPos), LiteralValue(DataType.UBYTE, 246, position = dummyPos))
        assertNotEquals(LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 12346, position = dummyPos))
        assertNotEquals(LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos), LiteralValue(DataType.FLOAT, floatvalue = 12346.0, position = dummyPos))
        assertNotEquals(LiteralValue(DataType.FLOAT, floatvalue = 9.99, position = dummyPos), LiteralValue(DataType.UBYTE, 9, position = dummyPos))
        assertNotEquals(LiteralValue(DataType.FLOAT, floatvalue = 9.99, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 9, position = dummyPos))
        assertNotEquals(LiteralValue(DataType.FLOAT, floatvalue = 9.99, position = dummyPos), LiteralValue(DataType.FLOAT, floatvalue = 9.0, position = dummyPos))

        assertFalse(sameValueAndType(LiteralValue(DataType.UBYTE, 100, position = dummyPos), LiteralValue(DataType.UBYTE, 101, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UBYTE, 100, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 101, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UBYTE, 100, position = dummyPos), LiteralValue(DataType.FLOAT, floatvalue = 101.0, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue = 245, position = dummyPos), LiteralValue(DataType.UBYTE, 246, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 12346, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.UWORD, wordvalue = 12345, position = dummyPos), LiteralValue(DataType.FLOAT, floatvalue = 12346.0, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.FLOAT, floatvalue = 9.99, position = dummyPos), LiteralValue(DataType.UBYTE, 9, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.FLOAT, floatvalue = 9.99, position = dummyPos), LiteralValue(DataType.UWORD, wordvalue = 9, position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.FLOAT, floatvalue = 9.99, position = dummyPos), LiteralValue(DataType.FLOAT, floatvalue = 9.0, position = dummyPos)))

        assertTrue(sameValueAndType(LiteralValue(DataType.STR, strvalue = "hello", position = dummyPos), LiteralValue(DataType.STR, strvalue = "hello", position = dummyPos)))
        assertFalse(sameValueAndType(LiteralValue(DataType.STR, strvalue = "hello", position = dummyPos), LiteralValue(DataType.STR, strvalue = "bye", position = dummyPos)))

        val lvOne = LiteralValue(DataType.UBYTE, 1, position = dummyPos)
        val lvTwo = LiteralValue(DataType.UBYTE, 2, position = dummyPos)
        val lvThree = LiteralValue(DataType.UBYTE, 3, position = dummyPos)
        val lvOneR = LiteralValue(DataType.UBYTE, 1, position = dummyPos)
        val lvTwoR = LiteralValue(DataType.UBYTE, 2, position = dummyPos)
        val lvThreeR = LiteralValue(DataType.UBYTE, 3, position = dummyPos)
        val lvFour= LiteralValue(DataType.UBYTE, 4, position = dummyPos)
        val lv1 = LiteralValue(DataType.ARRAY_UB, arrayvalue = arrayOf(lvOne, lvTwo, lvThree), position = dummyPos)
        val lv2 = LiteralValue(DataType.ARRAY_UB, arrayvalue = arrayOf(lvOneR, lvTwoR, lvThreeR), position = dummyPos)
        val lv3 = LiteralValue(DataType.ARRAY_UB, arrayvalue = arrayOf(lvOneR, lvTwoR, lvFour), position = dummyPos)
        assertEquals(lv1, lv2)
        assertNotEquals(lv1, lv3)
    }

    @Test
    fun testGreaterThan(){
        assertTrue(LiteralValue(DataType.UBYTE, 100, position = dummyPos) > LiteralValue(DataType.UBYTE, 99, position = dummyPos))
        assertTrue(LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos) > LiteralValue(DataType.UWORD, wordvalue = 253, position = dummyPos))
        assertTrue(LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos) > LiteralValue(DataType.FLOAT, floatvalue = 99.9, position = dummyPos))

        assertTrue(LiteralValue(DataType.UBYTE, 100, position = dummyPos) >= LiteralValue(DataType.UBYTE, 100, position = dummyPos))
        assertTrue(LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos) >= LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos))
        assertTrue(LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos) >= LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos))

        assertFalse(LiteralValue(DataType.UBYTE, 100, position = dummyPos) > LiteralValue(DataType.UBYTE, 100, position = dummyPos))
        assertFalse(LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos) > LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos))
        assertFalse(LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos) > LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos))

        assertFalse(LiteralValue(DataType.UBYTE, 100, position = dummyPos) >= LiteralValue(DataType.UBYTE, 101, position = dummyPos))
        assertFalse(LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos) >= LiteralValue(DataType.UWORD, wordvalue = 255, position = dummyPos))
        assertFalse(LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos) >= LiteralValue(DataType.FLOAT, floatvalue = 100.1, position = dummyPos))
    }

    @Test
    fun testLessThan() {
        assertTrue(LiteralValue(DataType.UBYTE, 100, position = dummyPos) < LiteralValue(DataType.UBYTE, 101, position = dummyPos))
        assertTrue(LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos) < LiteralValue(DataType.UWORD, wordvalue = 255, position = dummyPos))
        assertTrue(LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos) < LiteralValue(DataType.FLOAT, floatvalue = 100.1, position = dummyPos))

        assertTrue(LiteralValue(DataType.UBYTE, 100, position = dummyPos) <= LiteralValue(DataType.UBYTE, 100, position = dummyPos))
        assertTrue(LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos) <= LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos))
        assertTrue(LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos) <= LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos))

        assertFalse(LiteralValue(DataType.UBYTE, 100, position = dummyPos) < LiteralValue(DataType.UBYTE, 100, position = dummyPos))
        assertFalse(LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos) < LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos))
        assertFalse(LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos) < LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos))

        assertFalse(LiteralValue(DataType.UBYTE, 100, position = dummyPos) <= LiteralValue(DataType.UBYTE, 99, position = dummyPos))
        assertFalse(LiteralValue(DataType.UWORD, wordvalue = 254, position = dummyPos) <= LiteralValue(DataType.UWORD, wordvalue = 253, position = dummyPos))
        assertFalse(LiteralValue(DataType.FLOAT, floatvalue = 100.0, position = dummyPos) <= LiteralValue(DataType.FLOAT, floatvalue = 99.9, position = dummyPos))
    }

}

