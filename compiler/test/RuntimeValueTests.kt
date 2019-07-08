package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.DataType
import prog8.compiler.RuntimeValue
import kotlin.test.*


private fun sameValueAndType(v1: RuntimeValue, v2: RuntimeValue): Boolean {
    return v1.type==v2.type && v1==v2
}


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestRuntimeValue {

    @Test
    fun testValueRanges() {
        assertEquals(100, RuntimeValue(DataType.UBYTE, 100).integerValue())
        assertEquals(100, RuntimeValue(DataType.BYTE, 100).integerValue())
        assertEquals(10000, RuntimeValue(DataType.UWORD, 10000).integerValue())
        assertEquals(10000, RuntimeValue(DataType.WORD, 10000).integerValue())
        assertEquals(100.11, RuntimeValue(DataType.FLOAT, 100.11).numericValue())

        assertEquals(200, RuntimeValue(DataType.UBYTE, 200).integerValue())
        assertEquals(-56, RuntimeValue(DataType.BYTE, 200).integerValue())
        assertEquals(50000, RuntimeValue(DataType.UWORD, 50000).integerValue())
        assertEquals(-15536, RuntimeValue(DataType.WORD, 50000).integerValue())

        assertEquals(44, RuntimeValue(DataType.UBYTE, 300).integerValue())
        assertEquals(44, RuntimeValue(DataType.BYTE, 300).integerValue())
        assertEquals(144, RuntimeValue(DataType.UBYTE, 400).integerValue())
        assertEquals(-112, RuntimeValue(DataType.BYTE, 400).integerValue())
        assertEquals(34463, RuntimeValue(DataType.UWORD, 99999).integerValue())
        assertEquals(-31073, RuntimeValue(DataType.WORD, 99999).integerValue())

        assertEquals(156, RuntimeValue(DataType.UBYTE, -100).integerValue())
        assertEquals(-100, RuntimeValue(DataType.BYTE, -100).integerValue())
        assertEquals(55536, RuntimeValue(DataType.UWORD, -10000).integerValue())
        assertEquals(-10000, RuntimeValue(DataType.WORD, -10000).integerValue())
        assertEquals(-100.11, RuntimeValue(DataType.FLOAT, -100.11).numericValue())

        assertEquals(56, RuntimeValue(DataType.UBYTE, -200).integerValue())
        assertEquals(56, RuntimeValue(DataType.BYTE, -200).integerValue())
        assertEquals(45536, RuntimeValue(DataType.UWORD, -20000).integerValue())
        assertEquals(-20000, RuntimeValue(DataType.WORD, -20000).integerValue())

        assertEquals(212, RuntimeValue(DataType.UBYTE, -300).integerValue())
        assertEquals(-44, RuntimeValue(DataType.BYTE, -300).integerValue())
        assertEquals(42184, RuntimeValue(DataType.UWORD, -88888).integerValue())
        assertEquals(-23352, RuntimeValue(DataType.WORD, -88888).integerValue())
    }

    @Test
    fun testTruthiness()
    {
        assertFalse(RuntimeValue(DataType.BYTE, 0).asBoolean)
        assertFalse(RuntimeValue(DataType.UBYTE, 0).asBoolean)
        assertFalse(RuntimeValue(DataType.WORD, 0).asBoolean)
        assertFalse(RuntimeValue(DataType.UWORD, 0).asBoolean)
        assertFalse(RuntimeValue(DataType.FLOAT, 0.0).asBoolean)
        assertFalse(RuntimeValue(DataType.BYTE, 256).asBoolean)
        assertFalse(RuntimeValue(DataType.UBYTE, 256).asBoolean)
        assertFalse(RuntimeValue(DataType.WORD, 65536).asBoolean)
        assertFalse(RuntimeValue(DataType.UWORD, 65536).asBoolean)

        assertTrue(RuntimeValue(DataType.BYTE, 42).asBoolean)
        assertTrue(RuntimeValue(DataType.UBYTE, 42).asBoolean)
        assertTrue(RuntimeValue(DataType.WORD, 42).asBoolean)
        assertTrue(RuntimeValue(DataType.UWORD, 42).asBoolean)
        assertTrue(RuntimeValue(DataType.FLOAT, 42.0).asBoolean)
        assertTrue(RuntimeValue(DataType.BYTE, -42).asBoolean)
        assertTrue(RuntimeValue(DataType.UBYTE, -42).asBoolean)
        assertTrue(RuntimeValue(DataType.WORD, -42).asBoolean)
        assertTrue(RuntimeValue(DataType.UWORD, -42).asBoolean)
        assertTrue(RuntimeValue(DataType.FLOAT, -42.0).asBoolean)
    }


    @Test
    fun testIdentity() {
        val v = RuntimeValue(DataType.UWORD, 12345)
        assertEquals(v, v)
        assertFalse(v != v)
        assertTrue(v<=v)
        assertTrue(v>=v)
        assertFalse(v<v)
        assertFalse(v>v)

        assertTrue(sameValueAndType(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.UBYTE, 100)))
    }

    @Test
    fun testEqualsAndNotEquals() {
        assertEquals(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.UBYTE, 100))
        assertEquals(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.UWORD, 100))
        assertEquals(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.FLOAT, 100))
        assertEquals(RuntimeValue(DataType.UWORD, 254), RuntimeValue(DataType.UBYTE, 254))
        assertEquals(RuntimeValue(DataType.UWORD, 12345), RuntimeValue(DataType.UWORD, 12345))
        assertEquals(RuntimeValue(DataType.UWORD, 12345), RuntimeValue(DataType.FLOAT, 12345))
        assertEquals(RuntimeValue(DataType.FLOAT, 100.0), RuntimeValue(DataType.UBYTE, 100))
        assertEquals(RuntimeValue(DataType.FLOAT, 22239.0), RuntimeValue(DataType.UWORD, 22239))
        assertEquals(RuntimeValue(DataType.FLOAT, 9.99), RuntimeValue(DataType.FLOAT, 9.99))

        assertTrue(sameValueAndType(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.UBYTE, 100)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.UWORD, 100)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.FLOAT, 100)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.UWORD, 254), RuntimeValue(DataType.UBYTE, 254)))
        assertTrue(sameValueAndType(RuntimeValue(DataType.UWORD, 12345), RuntimeValue(DataType.UWORD, 12345)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.UWORD, 12345), RuntimeValue(DataType.FLOAT, 12345)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.FLOAT, 100.0), RuntimeValue(DataType.UBYTE, 100)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.FLOAT, 22239.0), RuntimeValue(DataType.UWORD, 22239)))
        assertTrue(sameValueAndType(RuntimeValue(DataType.FLOAT, 9.99), RuntimeValue(DataType.FLOAT, 9.99)))

        assertNotEquals(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.UBYTE, 101))
        assertNotEquals(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.UWORD, 101))
        assertNotEquals(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.FLOAT, 101))
        assertNotEquals(RuntimeValue(DataType.UWORD, 245), RuntimeValue(DataType.UBYTE, 246))
        assertNotEquals(RuntimeValue(DataType.UWORD, 12345), RuntimeValue(DataType.UWORD, 12346))
        assertNotEquals(RuntimeValue(DataType.UWORD, 12345), RuntimeValue(DataType.FLOAT, 12346))
        assertNotEquals(RuntimeValue(DataType.FLOAT, 9.99), RuntimeValue(DataType.UBYTE, 9))
        assertNotEquals(RuntimeValue(DataType.FLOAT, 9.99), RuntimeValue(DataType.UWORD, 9))
        assertNotEquals(RuntimeValue(DataType.FLOAT, 9.99), RuntimeValue(DataType.FLOAT, 9.0))

        assertFalse(sameValueAndType(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.UBYTE, 101)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.UWORD, 101)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.UBYTE, 100), RuntimeValue(DataType.FLOAT, 101)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.UWORD, 245), RuntimeValue(DataType.UBYTE, 246)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.UWORD, 12345), RuntimeValue(DataType.UWORD, 12346)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.UWORD, 12345), RuntimeValue(DataType.FLOAT, 12346)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.FLOAT, 9.99), RuntimeValue(DataType.UBYTE, 9)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.FLOAT, 9.99), RuntimeValue(DataType.UWORD, 9)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.FLOAT, 9.99), RuntimeValue(DataType.FLOAT, 9.0)))
    }

    @Test
    fun testRequireHeap()
    {
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.STR, num = 999) }
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.STR_S, num = 999) }
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.ARRAY_F, num = 999) }
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.ARRAY_W, num = 999) }
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.ARRAY_UW, num = 999) }
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.ARRAY_B, num = 999) }
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.ARRAY_UB, num = 999) }
    }

    @Test
    fun testEqualityHeapTypes()
    {
        assertTrue(sameValueAndType(RuntimeValue(DataType.STR, heapId = 999), RuntimeValue(DataType.STR, heapId = 999)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.STR, heapId = 999), RuntimeValue(DataType.STR, heapId = 222)))

        assertTrue(sameValueAndType(RuntimeValue(DataType.ARRAY_UB, heapId = 99), RuntimeValue(DataType.ARRAY_UB, heapId = 99)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.ARRAY_UB, heapId = 99), RuntimeValue(DataType.ARRAY_UB, heapId = 22)))

        assertTrue(sameValueAndType(RuntimeValue(DataType.ARRAY_UW, heapId = 999), RuntimeValue(DataType.ARRAY_UW, heapId = 999)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.ARRAY_UW, heapId = 999), RuntimeValue(DataType.ARRAY_UW, heapId = 222)))

        assertTrue(sameValueAndType(RuntimeValue(DataType.ARRAY_F, heapId = 999), RuntimeValue(DataType.ARRAY_F, heapId = 999)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.ARRAY_F, heapId = 999), RuntimeValue(DataType.ARRAY_UW, heapId = 999)))
        assertFalse(sameValueAndType(RuntimeValue(DataType.ARRAY_F, heapId = 999), RuntimeValue(DataType.ARRAY_F, heapId = 222)))
    }

    @Test
    fun testGreaterThan(){
        assertTrue(RuntimeValue(DataType.UBYTE, 100) > RuntimeValue(DataType.UBYTE, 99))
        assertTrue(RuntimeValue(DataType.UWORD, 254) > RuntimeValue(DataType.UWORD, 253))
        assertTrue(RuntimeValue(DataType.FLOAT, 100.0) > RuntimeValue(DataType.FLOAT, 99.9))

        assertTrue(RuntimeValue(DataType.UBYTE, 100) >= RuntimeValue(DataType.UBYTE, 100))
        assertTrue(RuntimeValue(DataType.UWORD, 254) >= RuntimeValue(DataType.UWORD, 254))
        assertTrue(RuntimeValue(DataType.FLOAT, 100.0) >= RuntimeValue(DataType.FLOAT, 100.0))

        assertFalse(RuntimeValue(DataType.UBYTE, 100) > RuntimeValue(DataType.UBYTE, 100))
        assertFalse(RuntimeValue(DataType.UWORD, 254) > RuntimeValue(DataType.UWORD, 254))
        assertFalse(RuntimeValue(DataType.FLOAT, 100.0) > RuntimeValue(DataType.FLOAT, 100.0))

        assertFalse(RuntimeValue(DataType.UBYTE, 100) >= RuntimeValue(DataType.UBYTE, 101))
        assertFalse(RuntimeValue(DataType.UWORD, 254) >= RuntimeValue(DataType.UWORD, 255))
        assertFalse(RuntimeValue(DataType.FLOAT, 100.0) >= RuntimeValue(DataType.FLOAT, 100.1))
    }

    @Test
    fun testLessThan() {
        assertTrue(RuntimeValue(DataType.UBYTE, 100) < RuntimeValue(DataType.UBYTE, 101))
        assertTrue(RuntimeValue(DataType.UWORD, 254) < RuntimeValue(DataType.UWORD, 255))
        assertTrue(RuntimeValue(DataType.FLOAT, 100.0) < RuntimeValue(DataType.FLOAT, 100.1))

        assertTrue(RuntimeValue(DataType.UBYTE, 100) <= RuntimeValue(DataType.UBYTE, 100))
        assertTrue(RuntimeValue(DataType.UWORD, 254) <= RuntimeValue(DataType.UWORD, 254))
        assertTrue(RuntimeValue(DataType.FLOAT, 100.0) <= RuntimeValue(DataType.FLOAT, 100.0))

        assertFalse(RuntimeValue(DataType.UBYTE, 100) < RuntimeValue(DataType.UBYTE, 100))
        assertFalse(RuntimeValue(DataType.UWORD, 254) < RuntimeValue(DataType.UWORD, 254))
        assertFalse(RuntimeValue(DataType.FLOAT, 100.0) < RuntimeValue(DataType.FLOAT, 100.0))

        assertFalse(RuntimeValue(DataType.UBYTE, 100) <= RuntimeValue(DataType.UBYTE, 99))
        assertFalse(RuntimeValue(DataType.UWORD, 254) <= RuntimeValue(DataType.UWORD, 253))
        assertFalse(RuntimeValue(DataType.FLOAT, 100.0) <= RuntimeValue(DataType.FLOAT, 99.9))
    }

    @Test
    fun testNoDtConversion() {
        assertFailsWith<ArithmeticException> {
            RuntimeValue(DataType.UWORD, 100).add(RuntimeValue(DataType.UBYTE, 120))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValue(DataType.UBYTE, 100).add(RuntimeValue(DataType.UWORD, 120))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValue(DataType.FLOAT, 100.22).add(RuntimeValue(DataType.UWORD, 120))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValue(DataType.UWORD, 1002).add(RuntimeValue(DataType.FLOAT, 120.22))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValue(DataType.FLOAT, 100.22).add(RuntimeValue(DataType.UBYTE, 120))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValue(DataType.UBYTE, 12).add(RuntimeValue(DataType.FLOAT, 120.22))
        }
    }

    @Test
    fun testNoAutoFloatConversion() {
        assertFailsWith<ArithmeticException> {
            RuntimeValue(DataType.UBYTE, 233).add(RuntimeValue(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValue(DataType.UWORD, 233).add(RuntimeValue(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValue(DataType.UBYTE, 233).mul(RuntimeValue(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValue(DataType.UWORD, 233).mul(RuntimeValue(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValue(DataType.UBYTE, 233).div(RuntimeValue(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValue(DataType.UWORD, 233).div(RuntimeValue(DataType.FLOAT, 1.234))
        }
        val result = RuntimeValue(DataType.FLOAT, 233.333).add(RuntimeValue(DataType.FLOAT, 1.234))
    }
}
