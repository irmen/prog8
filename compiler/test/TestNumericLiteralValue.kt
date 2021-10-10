package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.ArrayLiteralValue
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.StringLiteralValue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestNumericLiteralValue {

    private fun sameValueAndType(lv1: NumericLiteralValue, lv2: NumericLiteralValue): Boolean {
        return lv1.type==lv2.type && lv1==lv2
    }

    private val dummyPos = Position("test", 0, 0, 0)

    @Test
    fun testIdentity() {
        val v = NumericLiteralValue(DataType.UWORD, 12345, dummyPos)
        assertEquals(v, v)
        assertFalse(v != v)
        assertTrue(v <= v)
        assertTrue(v >= v)
        assertFalse(v < v)
        assertFalse(v > v)

        assertTrue(sameValueAndType(NumericLiteralValue(DataType.UWORD, 12345, dummyPos), NumericLiteralValue(DataType.UWORD, 12345, dummyPos)))
    }

    @Test
    fun testEqualsAndNotEquals() {
        assertEquals(NumericLiteralValue(DataType.UBYTE, 100, dummyPos), NumericLiteralValue(DataType.UBYTE, 100, dummyPos))
        assertEquals(NumericLiteralValue(DataType.UBYTE, 100, dummyPos), NumericLiteralValue(DataType.UWORD, 100, dummyPos))
        assertEquals(NumericLiteralValue(DataType.UBYTE, 100, dummyPos), NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos))
        assertEquals(NumericLiteralValue(DataType.UWORD, 254, dummyPos), NumericLiteralValue(DataType.UBYTE, 254, dummyPos))
        assertEquals(NumericLiteralValue(DataType.UWORD, 12345, dummyPos), NumericLiteralValue(DataType.UWORD, 12345, dummyPos))
        assertEquals(NumericLiteralValue(DataType.UWORD, 12345, dummyPos), NumericLiteralValue(DataType.FLOAT, 12345.0, dummyPos))
        assertEquals(NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos), NumericLiteralValue(DataType.UBYTE, 100, dummyPos))
        assertEquals(NumericLiteralValue(DataType.FLOAT, 22239.0, dummyPos), NumericLiteralValue(DataType.UWORD, 22239, dummyPos))
        assertEquals(NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos), NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos))

        assertTrue(sameValueAndType(NumericLiteralValue(DataType.UBYTE, 100, dummyPos), NumericLiteralValue(DataType.UBYTE, 100, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.UBYTE, 100, dummyPos), NumericLiteralValue(DataType.UWORD, 100, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.UBYTE, 100, dummyPos), NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.UWORD, 254, dummyPos), NumericLiteralValue(DataType.UBYTE, 254, dummyPos)))
        assertTrue(sameValueAndType(NumericLiteralValue(DataType.UWORD, 12345, dummyPos), NumericLiteralValue(DataType.UWORD, 12345, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.UWORD, 12345, dummyPos), NumericLiteralValue(DataType.FLOAT, 12345.0, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos), NumericLiteralValue(DataType.UBYTE, 100, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.FLOAT, 22239.0, dummyPos), NumericLiteralValue(DataType.UWORD, 22239, dummyPos)))
        assertTrue(sameValueAndType(NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos), NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos)))

        assertNotEquals(NumericLiteralValue(DataType.UBYTE, 100, dummyPos), NumericLiteralValue(DataType.UBYTE, 101, dummyPos))
        assertNotEquals(NumericLiteralValue(DataType.UBYTE, 100, dummyPos), NumericLiteralValue(DataType.UWORD, 101, dummyPos))
        assertNotEquals(NumericLiteralValue(DataType.UBYTE, 100, dummyPos), NumericLiteralValue(DataType.FLOAT, 101.0, dummyPos))
        assertNotEquals(NumericLiteralValue(DataType.UWORD, 245, dummyPos), NumericLiteralValue(DataType.UBYTE, 246, dummyPos))
        assertNotEquals(NumericLiteralValue(DataType.UWORD, 12345, dummyPos), NumericLiteralValue(DataType.UWORD, 12346, dummyPos))
        assertNotEquals(NumericLiteralValue(DataType.UWORD, 12345, dummyPos), NumericLiteralValue(DataType.FLOAT, 12346.0, dummyPos))
        assertNotEquals(NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos), NumericLiteralValue(DataType.UBYTE, 9, dummyPos))
        assertNotEquals(NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos), NumericLiteralValue(DataType.UWORD, 9, dummyPos))
        assertNotEquals(NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos), NumericLiteralValue(DataType.FLOAT, 9.0, dummyPos))

        assertFalse(sameValueAndType(NumericLiteralValue(DataType.UBYTE, 100, dummyPos), NumericLiteralValue(DataType.UBYTE, 101, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.UBYTE, 100, dummyPos), NumericLiteralValue(DataType.UWORD, 101, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.UBYTE, 100, dummyPos), NumericLiteralValue(DataType.FLOAT, 101.0, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.UWORD, 245, dummyPos), NumericLiteralValue(DataType.UBYTE, 246, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.UWORD, 12345, dummyPos), NumericLiteralValue(DataType.UWORD, 12346, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.UWORD, 12345, dummyPos), NumericLiteralValue(DataType.FLOAT, 12346.0, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos), NumericLiteralValue(DataType.UBYTE, 9, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos), NumericLiteralValue(DataType.UWORD, 9, dummyPos)))
        assertFalse(sameValueAndType(NumericLiteralValue(DataType.FLOAT, 9.99, dummyPos), NumericLiteralValue(DataType.FLOAT, 9.0, dummyPos)))


    }

    @Test
    fun testEqualsRef() {
        assertEquals(StringLiteralValue("hello", false, dummyPos), StringLiteralValue("hello", false, dummyPos))
        assertNotEquals(StringLiteralValue("hello", false, dummyPos), StringLiteralValue("bye", false, dummyPos))
        assertEquals(StringLiteralValue("hello", true, dummyPos), StringLiteralValue("hello", true, dummyPos))
        assertNotEquals(StringLiteralValue("hello", true, dummyPos), StringLiteralValue("bye", true, dummyPos))
        assertNotEquals(StringLiteralValue("hello", true, dummyPos), StringLiteralValue("hello", false, dummyPos))

        val lvOne = NumericLiteralValue(DataType.UBYTE, 1, dummyPos)
        val lvTwo = NumericLiteralValue(DataType.UBYTE, 2, dummyPos)
        val lvThree = NumericLiteralValue(DataType.UBYTE, 3, dummyPos)
        val lvOneR = NumericLiteralValue(DataType.UBYTE, 1, dummyPos)
        val lvTwoR = NumericLiteralValue(DataType.UBYTE, 2, dummyPos)
        val lvThreeR = NumericLiteralValue(DataType.UBYTE, 3, dummyPos)
        val lvFour= NumericLiteralValue(DataType.UBYTE, 4, dummyPos)
        val lv1 = ArrayLiteralValue(InferredTypes.InferredType.known(DataType.ARRAY_UB), arrayOf(lvOne, lvTwo, lvThree), dummyPos)
        val lv2 = ArrayLiteralValue(InferredTypes.InferredType.known(DataType.ARRAY_UB), arrayOf(lvOneR, lvTwoR, lvThreeR), dummyPos)
        val lv3 = ArrayLiteralValue(InferredTypes.InferredType.known(DataType.ARRAY_UB), arrayOf(lvOneR, lvTwoR, lvFour), dummyPos)
        assertEquals(lv1, lv2)
        assertNotEquals(lv1, lv3)
    }

    @Test
    fun testGreaterThan(){
        assertTrue(NumericLiteralValue(DataType.UBYTE, 100, dummyPos) > NumericLiteralValue(DataType.UBYTE, 99, dummyPos))
        assertTrue(NumericLiteralValue(DataType.UWORD, 254, dummyPos) > NumericLiteralValue(DataType.UWORD, 253, dummyPos))
        assertTrue(NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) > NumericLiteralValue(DataType.FLOAT, 99.9, dummyPos))

        assertTrue(NumericLiteralValue(DataType.UBYTE, 100, dummyPos) >= NumericLiteralValue(DataType.UBYTE, 100, dummyPos))
        assertTrue(NumericLiteralValue(DataType.UWORD, 254, dummyPos) >= NumericLiteralValue(DataType.UWORD, 254, dummyPos))
        assertTrue(NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) >= NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos))

        assertFalse(NumericLiteralValue(DataType.UBYTE, 100, dummyPos) > NumericLiteralValue(DataType.UBYTE, 100, dummyPos))
        assertFalse(NumericLiteralValue(DataType.UWORD, 254, dummyPos) > NumericLiteralValue(DataType.UWORD, 254, dummyPos))
        assertFalse(NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) > NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos))

        assertFalse(NumericLiteralValue(DataType.UBYTE, 100, dummyPos) >= NumericLiteralValue(DataType.UBYTE, 101, dummyPos))
        assertFalse(NumericLiteralValue(DataType.UWORD, 254, dummyPos) >= NumericLiteralValue(DataType.UWORD, 255, dummyPos))
        assertFalse(NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) >= NumericLiteralValue(DataType.FLOAT, 100.1, dummyPos))
    }

    @Test
    fun testLessThan() {
        assertTrue(NumericLiteralValue(DataType.UBYTE, 100, dummyPos) < NumericLiteralValue(DataType.UBYTE, 101, dummyPos))
        assertTrue(NumericLiteralValue(DataType.UWORD, 254, dummyPos) < NumericLiteralValue(DataType.UWORD, 255, dummyPos))
        assertTrue(NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) < NumericLiteralValue(DataType.FLOAT, 100.1, dummyPos))

        assertTrue(NumericLiteralValue(DataType.UBYTE, 100, dummyPos) <= NumericLiteralValue(DataType.UBYTE, 100, dummyPos))
        assertTrue(NumericLiteralValue(DataType.UWORD, 254, dummyPos) <= NumericLiteralValue(DataType.UWORD, 254, dummyPos))
        assertTrue(NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) <= NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos))

        assertFalse(NumericLiteralValue(DataType.UBYTE, 100, dummyPos) < NumericLiteralValue(DataType.UBYTE, 100, dummyPos))
        assertFalse(NumericLiteralValue(DataType.UWORD, 254, dummyPos) < NumericLiteralValue(DataType.UWORD, 254, dummyPos))
        assertFalse(NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) < NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos))

        assertFalse(NumericLiteralValue(DataType.UBYTE, 100, dummyPos) <= NumericLiteralValue(DataType.UBYTE, 99, dummyPos))
        assertFalse(NumericLiteralValue(DataType.UWORD, 254, dummyPos) <= NumericLiteralValue(DataType.UWORD, 253, dummyPos))
        assertFalse(NumericLiteralValue(DataType.FLOAT, 100.0, dummyPos) <= NumericLiteralValue(DataType.FLOAT, 99.9, dummyPos))
    }

}

