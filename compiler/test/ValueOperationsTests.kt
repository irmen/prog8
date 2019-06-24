package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.DataType
import prog8.compiler.intermediate.Value
import prog8.compiler.intermediate.ValueException
import kotlin.test.*


private fun sameValueAndType(v1: Value, v2: Value): Boolean {
    return v1.type==v2.type && v1==v2
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
        assertFailsWith<ValueException> {
            Value(DataType.UWORD, 100).add(Value(DataType.UBYTE, 120))
        }
        assertFailsWith<ValueException> {
            Value(DataType.UBYTE, 100).add(Value(DataType.UWORD, 120))
        }
        assertFailsWith<ValueException> {
            Value(DataType.FLOAT, 100.22).add(Value(DataType.UWORD, 120))
        }
        assertFailsWith<ValueException> {
            Value(DataType.UWORD, 1002).add(Value(DataType.FLOAT, 120.22))
        }
        assertFailsWith<ValueException> {
            Value(DataType.FLOAT, 100.22).add(Value(DataType.UBYTE, 120))
        }
        assertFailsWith<ValueException> {
            Value(DataType.UBYTE, 12).add(Value(DataType.FLOAT, 120.22))
        }
    }

    @Test
    fun testNoAutoFloatConversion() {
        assertFailsWith<ValueException> {
            Value(DataType.UBYTE, 233).add(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ValueException> {
            Value(DataType.UWORD, 233).add(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ValueException> {
            Value(DataType.UBYTE, 233).mul(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ValueException> {
            Value(DataType.UWORD, 233).mul(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ValueException> {
            Value(DataType.UBYTE, 233).div(Value(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ValueException> {
            Value(DataType.UWORD, 233).div(Value(DataType.FLOAT, 1.234))
        }
        val result = Value(DataType.FLOAT, 233.333).add(Value(DataType.FLOAT, 1.234))
    }
}
