package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.DataType
import prog8.vm.RuntimeValueNumeric
import kotlin.test.*


private fun sameValueAndType(v1: RuntimeValueNumeric, v2: RuntimeValueNumeric): Boolean {
    return v1.type==v2.type && v1==v2
}


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestRuntimeValueNumeric {

    @Test
    fun testValueRanges() {
        assertEquals(0, RuntimeValueNumeric(DataType.UBYTE, 0).integerValue())
        assertEquals(255, RuntimeValueNumeric(DataType.UBYTE, 255).integerValue())
        assertFailsWith<IllegalArgumentException> { RuntimeValueNumeric(DataType.UBYTE, -1)}
        assertFailsWith<IllegalArgumentException> { RuntimeValueNumeric(DataType.UBYTE, 256)}

        assertEquals(0, RuntimeValueNumeric(DataType.BYTE, 0).integerValue())
        assertEquals(-128, RuntimeValueNumeric(DataType.BYTE, -128).integerValue())
        assertEquals(127, RuntimeValueNumeric(DataType.BYTE, 127).integerValue())
        assertFailsWith<IllegalArgumentException> { RuntimeValueNumeric(DataType.BYTE, -129)}
        assertFailsWith<IllegalArgumentException> { RuntimeValueNumeric(DataType.BYTE, 128)}

        assertEquals(0, RuntimeValueNumeric(DataType.UWORD, 0).integerValue())
        assertEquals(65535, RuntimeValueNumeric(DataType.UWORD, 65535).integerValue())
        assertFailsWith<IllegalArgumentException> { RuntimeValueNumeric(DataType.UWORD, -1)}
        assertFailsWith<IllegalArgumentException> { RuntimeValueNumeric(DataType.UWORD, 65536)}

        assertEquals(0, RuntimeValueNumeric(DataType.WORD, 0).integerValue())
        assertEquals(-32768, RuntimeValueNumeric(DataType.WORD, -32768).integerValue())
        assertEquals(32767, RuntimeValueNumeric(DataType.WORD, 32767).integerValue())
        assertFailsWith<IllegalArgumentException> { RuntimeValueNumeric(DataType.WORD, -32769)}
        assertFailsWith<IllegalArgumentException> { RuntimeValueNumeric(DataType.WORD, 32768)}
    }

    @Test
    fun testTruthiness()
    {
        assertFalse(RuntimeValueNumeric(DataType.BYTE, 0).asBoolean)
        assertFalse(RuntimeValueNumeric(DataType.UBYTE, 0).asBoolean)
        assertFalse(RuntimeValueNumeric(DataType.WORD, 0).asBoolean)
        assertFalse(RuntimeValueNumeric(DataType.UWORD, 0).asBoolean)
        assertFalse(RuntimeValueNumeric(DataType.FLOAT, 0.0).asBoolean)

        assertTrue(RuntimeValueNumeric(DataType.BYTE, 42).asBoolean)
        assertTrue(RuntimeValueNumeric(DataType.UBYTE, 42).asBoolean)
        assertTrue(RuntimeValueNumeric(DataType.WORD, 42).asBoolean)
        assertTrue(RuntimeValueNumeric(DataType.UWORD, 42).asBoolean)
        assertTrue(RuntimeValueNumeric(DataType.FLOAT, 42.0).asBoolean)
        assertTrue(RuntimeValueNumeric(DataType.BYTE, -42).asBoolean)
        assertTrue(RuntimeValueNumeric(DataType.WORD, -42).asBoolean)
        assertTrue(RuntimeValueNumeric(DataType.FLOAT, -42.0).asBoolean)
    }


    @Test
    fun testIdentity() {
        val v = RuntimeValueNumeric(DataType.UWORD, 12345)
        assertEquals(v, v)
        assertFalse(v != v)
        assertTrue(v<=v)
        assertTrue(v>=v)
        assertFalse(v<v)
        assertFalse(v>v)

        assertTrue(sameValueAndType(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.UBYTE, 100)))
    }

    @Test
    fun testEqualsAndNotEquals() {
        assertEquals(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.UBYTE, 100))
        assertEquals(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.UWORD, 100))
        assertEquals(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.FLOAT, 100))
        assertEquals(RuntimeValueNumeric(DataType.UWORD, 254), RuntimeValueNumeric(DataType.UBYTE, 254))
        assertEquals(RuntimeValueNumeric(DataType.UWORD, 12345), RuntimeValueNumeric(DataType.UWORD, 12345))
        assertEquals(RuntimeValueNumeric(DataType.UWORD, 12345), RuntimeValueNumeric(DataType.FLOAT, 12345))
        assertEquals(RuntimeValueNumeric(DataType.FLOAT, 100.0), RuntimeValueNumeric(DataType.UBYTE, 100))
        assertEquals(RuntimeValueNumeric(DataType.FLOAT, 22239.0), RuntimeValueNumeric(DataType.UWORD, 22239))
        assertEquals(RuntimeValueNumeric(DataType.FLOAT, 9.99), RuntimeValueNumeric(DataType.FLOAT, 9.99))

        assertTrue(sameValueAndType(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.UBYTE, 100)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.UWORD, 100)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.FLOAT, 100)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.UWORD, 254), RuntimeValueNumeric(DataType.UBYTE, 254)))
        assertTrue(sameValueAndType(RuntimeValueNumeric(DataType.UWORD, 12345), RuntimeValueNumeric(DataType.UWORD, 12345)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.UWORD, 12345), RuntimeValueNumeric(DataType.FLOAT, 12345)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.FLOAT, 100.0), RuntimeValueNumeric(DataType.UBYTE, 100)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.FLOAT, 22239.0), RuntimeValueNumeric(DataType.UWORD, 22239)))
        assertTrue(sameValueAndType(RuntimeValueNumeric(DataType.FLOAT, 9.99), RuntimeValueNumeric(DataType.FLOAT, 9.99)))

        assertNotEquals(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.UBYTE, 101))
        assertNotEquals(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.UWORD, 101))
        assertNotEquals(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.FLOAT, 101))
        assertNotEquals(RuntimeValueNumeric(DataType.UWORD, 245), RuntimeValueNumeric(DataType.UBYTE, 246))
        assertNotEquals(RuntimeValueNumeric(DataType.UWORD, 12345), RuntimeValueNumeric(DataType.UWORD, 12346))
        assertNotEquals(RuntimeValueNumeric(DataType.UWORD, 12345), RuntimeValueNumeric(DataType.FLOAT, 12346))
        assertNotEquals(RuntimeValueNumeric(DataType.FLOAT, 9.99), RuntimeValueNumeric(DataType.UBYTE, 9))
        assertNotEquals(RuntimeValueNumeric(DataType.FLOAT, 9.99), RuntimeValueNumeric(DataType.UWORD, 9))
        assertNotEquals(RuntimeValueNumeric(DataType.FLOAT, 9.99), RuntimeValueNumeric(DataType.FLOAT, 9.0))

        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.UBYTE, 101)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.UWORD, 101)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.UBYTE, 100), RuntimeValueNumeric(DataType.FLOAT, 101)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.UWORD, 245), RuntimeValueNumeric(DataType.UBYTE, 246)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.UWORD, 12345), RuntimeValueNumeric(DataType.UWORD, 12346)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.UWORD, 12345), RuntimeValueNumeric(DataType.FLOAT, 12346)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.FLOAT, 9.99), RuntimeValueNumeric(DataType.UBYTE, 9)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.FLOAT, 9.99), RuntimeValueNumeric(DataType.UWORD, 9)))
        assertFalse(sameValueAndType(RuntimeValueNumeric(DataType.FLOAT, 9.99), RuntimeValueNumeric(DataType.FLOAT, 9.0)))
    }

    @Test
    fun testGreaterThan(){
        assertTrue(RuntimeValueNumeric(DataType.UBYTE, 100) > RuntimeValueNumeric(DataType.UBYTE, 99))
        assertTrue(RuntimeValueNumeric(DataType.UWORD, 254) > RuntimeValueNumeric(DataType.UWORD, 253))
        assertTrue(RuntimeValueNumeric(DataType.FLOAT, 100.0) > RuntimeValueNumeric(DataType.FLOAT, 99.9))

        assertTrue(RuntimeValueNumeric(DataType.UBYTE, 100) >= RuntimeValueNumeric(DataType.UBYTE, 100))
        assertTrue(RuntimeValueNumeric(DataType.UWORD, 254) >= RuntimeValueNumeric(DataType.UWORD, 254))
        assertTrue(RuntimeValueNumeric(DataType.FLOAT, 100.0) >= RuntimeValueNumeric(DataType.FLOAT, 100.0))

        assertFalse(RuntimeValueNumeric(DataType.UBYTE, 100) > RuntimeValueNumeric(DataType.UBYTE, 100))
        assertFalse(RuntimeValueNumeric(DataType.UWORD, 254) > RuntimeValueNumeric(DataType.UWORD, 254))
        assertFalse(RuntimeValueNumeric(DataType.FLOAT, 100.0) > RuntimeValueNumeric(DataType.FLOAT, 100.0))

        assertFalse(RuntimeValueNumeric(DataType.UBYTE, 100) >= RuntimeValueNumeric(DataType.UBYTE, 101))
        assertFalse(RuntimeValueNumeric(DataType.UWORD, 254) >= RuntimeValueNumeric(DataType.UWORD, 255))
        assertFalse(RuntimeValueNumeric(DataType.FLOAT, 100.0) >= RuntimeValueNumeric(DataType.FLOAT, 100.1))
    }

    @Test
    fun testLessThan() {
        assertTrue(RuntimeValueNumeric(DataType.UBYTE, 100) < RuntimeValueNumeric(DataType.UBYTE, 101))
        assertTrue(RuntimeValueNumeric(DataType.UWORD, 254) < RuntimeValueNumeric(DataType.UWORD, 255))
        assertTrue(RuntimeValueNumeric(DataType.FLOAT, 100.0) < RuntimeValueNumeric(DataType.FLOAT, 100.1))

        assertTrue(RuntimeValueNumeric(DataType.UBYTE, 100) <= RuntimeValueNumeric(DataType.UBYTE, 100))
        assertTrue(RuntimeValueNumeric(DataType.UWORD, 254) <= RuntimeValueNumeric(DataType.UWORD, 254))
        assertTrue(RuntimeValueNumeric(DataType.FLOAT, 100.0) <= RuntimeValueNumeric(DataType.FLOAT, 100.0))

        assertFalse(RuntimeValueNumeric(DataType.UBYTE, 100) < RuntimeValueNumeric(DataType.UBYTE, 100))
        assertFalse(RuntimeValueNumeric(DataType.UWORD, 254) < RuntimeValueNumeric(DataType.UWORD, 254))
        assertFalse(RuntimeValueNumeric(DataType.FLOAT, 100.0) < RuntimeValueNumeric(DataType.FLOAT, 100.0))

        assertFalse(RuntimeValueNumeric(DataType.UBYTE, 100) <= RuntimeValueNumeric(DataType.UBYTE, 99))
        assertFalse(RuntimeValueNumeric(DataType.UWORD, 254) <= RuntimeValueNumeric(DataType.UWORD, 253))
        assertFalse(RuntimeValueNumeric(DataType.FLOAT, 100.0) <= RuntimeValueNumeric(DataType.FLOAT, 99.9))
    }

    @Test
    fun testNoDtConversion() {
        assertFailsWith<ArithmeticException> {
            RuntimeValueNumeric(DataType.UWORD, 100).add(RuntimeValueNumeric(DataType.UBYTE, 120))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValueNumeric(DataType.UBYTE, 100).add(RuntimeValueNumeric(DataType.UWORD, 120))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValueNumeric(DataType.FLOAT, 100.22).add(RuntimeValueNumeric(DataType.UWORD, 120))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValueNumeric(DataType.UWORD, 1002).add(RuntimeValueNumeric(DataType.FLOAT, 120.22))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValueNumeric(DataType.FLOAT, 100.22).add(RuntimeValueNumeric(DataType.UBYTE, 120))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValueNumeric(DataType.UBYTE, 12).add(RuntimeValueNumeric(DataType.FLOAT, 120.22))
        }
    }

    @Test
    fun testNoAutoFloatConversion() {
        assertFailsWith<ArithmeticException> {
            RuntimeValueNumeric(DataType.UBYTE, 233).add(RuntimeValueNumeric(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValueNumeric(DataType.UWORD, 233).add(RuntimeValueNumeric(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValueNumeric(DataType.UBYTE, 233).mul(RuntimeValueNumeric(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValueNumeric(DataType.UWORD, 233).mul(RuntimeValueNumeric(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValueNumeric(DataType.UBYTE, 233).div(RuntimeValueNumeric(DataType.FLOAT, 1.234))
        }
        assertFailsWith<ArithmeticException> {
            RuntimeValueNumeric(DataType.UWORD, 233).div(RuntimeValueNumeric(DataType.FLOAT, 1.234))
        }
        val result = RuntimeValueNumeric(DataType.FLOAT, 233.333).add(RuntimeValueNumeric(DataType.FLOAT, 1.234))
    }

    @Test
    fun arithmetictestUbyte() {
        assertEquals(255, RuntimeValueNumeric(DataType.UBYTE, 200).add(RuntimeValueNumeric(DataType.UBYTE, 55)).integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UBYTE, 200).add(RuntimeValueNumeric(DataType.UBYTE, 56)).integerValue())
        assertEquals(1, RuntimeValueNumeric(DataType.UBYTE, 200).add(RuntimeValueNumeric(DataType.UBYTE, 57)).integerValue())

        assertEquals(1, RuntimeValueNumeric(DataType.UBYTE, 2).sub(RuntimeValueNumeric(DataType.UBYTE, 1)).integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UBYTE, 2).sub(RuntimeValueNumeric(DataType.UBYTE, 2)).integerValue())
        assertEquals(255, RuntimeValueNumeric(DataType.UBYTE, 2).sub(RuntimeValueNumeric(DataType.UBYTE, 3)).integerValue())

        assertEquals(255, RuntimeValueNumeric(DataType.UBYTE, 254).inc().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UBYTE, 255).inc().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UBYTE, 1).dec().integerValue())
        assertEquals(255, RuntimeValueNumeric(DataType.UBYTE, 0).dec().integerValue())

        assertEquals(255, RuntimeValueNumeric(DataType.UBYTE, 0).inv().integerValue())
        assertEquals(0b00110011, RuntimeValueNumeric(DataType.UBYTE, 0b11001100).inv().integerValue())
//        assertEquals(0, RuntimeValueNumeric(DataType.UBYTE, 0).neg().integerValue())
//        assertEquals(0, RuntimeValueNumeric(DataType.UBYTE, 0).neg().integerValue())
        assertEquals(1, RuntimeValueNumeric(DataType.UBYTE, 0).not().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UBYTE, 1).not().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UBYTE, 111).not().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UBYTE, 255).not().integerValue())

        assertEquals(200, RuntimeValueNumeric(DataType.UBYTE, 20).mul(RuntimeValueNumeric(DataType.UBYTE, 10)).integerValue())
        assertEquals(144, RuntimeValueNumeric(DataType.UBYTE, 20).mul(RuntimeValueNumeric(DataType.UBYTE, 20)).integerValue())

        assertEquals(25, RuntimeValueNumeric(DataType.UBYTE, 5).pow(RuntimeValueNumeric(DataType.UBYTE, 2)).integerValue())
        assertEquals(125, RuntimeValueNumeric(DataType.UBYTE, 5).pow(RuntimeValueNumeric(DataType.UBYTE, 3)).integerValue())
        assertEquals(113, RuntimeValueNumeric(DataType.UBYTE, 5).pow(RuntimeValueNumeric(DataType.UBYTE, 4)).integerValue())

        assertEquals(100, RuntimeValueNumeric(DataType.UBYTE, 50).shl().integerValue())
        assertEquals(200, RuntimeValueNumeric(DataType.UBYTE, 100).shl().integerValue())
        assertEquals(144, RuntimeValueNumeric(DataType.UBYTE, 200).shl().integerValue())
    }

    @Test
    fun arithmetictestUWord() {
        assertEquals(65535, RuntimeValueNumeric(DataType.UWORD, 60000).add(RuntimeValueNumeric(DataType.UWORD, 5535)).integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UWORD, 60000).add(RuntimeValueNumeric(DataType.UWORD, 5536)).integerValue())
        assertEquals(1, RuntimeValueNumeric(DataType.UWORD, 60000).add(RuntimeValueNumeric(DataType.UWORD, 5537)).integerValue())

        assertEquals(1, RuntimeValueNumeric(DataType.UWORD, 2).sub(RuntimeValueNumeric(DataType.UWORD, 1)).integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UWORD, 2).sub(RuntimeValueNumeric(DataType.UWORD, 2)).integerValue())
        assertEquals(65535, RuntimeValueNumeric(DataType.UWORD, 2).sub(RuntimeValueNumeric(DataType.UWORD, 3)).integerValue())

        assertEquals(65535, RuntimeValueNumeric(DataType.UWORD, 65534).inc().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UWORD, 65535).inc().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UWORD, 1).dec().integerValue())
        assertEquals(65535, RuntimeValueNumeric(DataType.UWORD, 0).dec().integerValue())

        assertEquals(65535, RuntimeValueNumeric(DataType.UWORD, 0).inv().integerValue())
        assertEquals(0b0011001101010101, RuntimeValueNumeric(DataType.UWORD, 0b1100110010101010).inv().integerValue())
//        assertEquals(0, RuntimeValueNumeric(DataType.UWORD, 0).neg().integerValue())
//        assertEquals(0, RuntimeValueNumeric(DataType.UWORD, 0).neg().integerValue())
        assertEquals(1, RuntimeValueNumeric(DataType.UWORD, 0).not().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UWORD, 1).not().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UWORD, 11111).not().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.UWORD, 65535).not().integerValue())

        assertEquals(2000, RuntimeValueNumeric(DataType.UWORD, 200).mul(RuntimeValueNumeric(DataType.UWORD, 10)).integerValue())
        assertEquals(40000, RuntimeValueNumeric(DataType.UWORD, 200).mul(RuntimeValueNumeric(DataType.UWORD, 200)).integerValue())
        assertEquals(14464, RuntimeValueNumeric(DataType.UWORD, 200).mul(RuntimeValueNumeric(DataType.UWORD, 400)).integerValue())

        assertEquals(15625, RuntimeValueNumeric(DataType.UWORD, 5).pow(RuntimeValueNumeric(DataType.UWORD, 6)).integerValue())
        assertEquals(12589, RuntimeValueNumeric(DataType.UWORD, 5).pow(RuntimeValueNumeric(DataType.UWORD, 7)).integerValue())

        assertEquals(10000, RuntimeValueNumeric(DataType.UWORD, 5000).shl().integerValue())
        assertEquals(60000, RuntimeValueNumeric(DataType.UWORD, 30000).shl().integerValue())
        assertEquals(14464, RuntimeValueNumeric(DataType.UWORD, 40000).shl().integerValue())
    }

    @Test
    fun arithmetictestByte() {
        assertEquals(127, RuntimeValueNumeric(DataType.BYTE, 100).add(RuntimeValueNumeric(DataType.BYTE, 27)).integerValue())
        assertEquals(-128, RuntimeValueNumeric(DataType.BYTE, 100).add(RuntimeValueNumeric(DataType.BYTE, 28)).integerValue())
        assertEquals(-127, RuntimeValueNumeric(DataType.BYTE, 100).add(RuntimeValueNumeric(DataType.BYTE, 29)).integerValue())

        assertEquals(1, RuntimeValueNumeric(DataType.BYTE, 2).sub(RuntimeValueNumeric(DataType.BYTE, 1)).integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.BYTE, 2).sub(RuntimeValueNumeric(DataType.BYTE, 2)).integerValue())
        assertEquals(-1, RuntimeValueNumeric(DataType.BYTE, 2).sub(RuntimeValueNumeric(DataType.BYTE, 3)).integerValue())
        assertEquals(-128, RuntimeValueNumeric(DataType.BYTE, -100).sub(RuntimeValueNumeric(DataType.BYTE, 28)).integerValue())
        assertEquals(127, RuntimeValueNumeric(DataType.BYTE, -100).sub(RuntimeValueNumeric(DataType.BYTE, 29)).integerValue())

        assertEquals(127, RuntimeValueNumeric(DataType.BYTE, 126).inc().integerValue())
        assertEquals(-128, RuntimeValueNumeric(DataType.BYTE, 127).inc().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.BYTE, 1).dec().integerValue())
        assertEquals(-1, RuntimeValueNumeric(DataType.BYTE, 0).dec().integerValue())
        assertEquals(-128, RuntimeValueNumeric(DataType.BYTE, -127).dec().integerValue())
        assertEquals(127, RuntimeValueNumeric(DataType.BYTE, -128).dec().integerValue())

        assertEquals(-1, RuntimeValueNumeric(DataType.BYTE, 0).inv().integerValue())
        assertEquals(-103, RuntimeValueNumeric(DataType.BYTE, 0b01100110).inv().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.BYTE, 0).neg().integerValue())
        assertEquals(-2, RuntimeValueNumeric(DataType.BYTE, 2).neg().integerValue())
        assertEquals(1, RuntimeValueNumeric(DataType.BYTE, 0).not().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.BYTE, 1).not().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.BYTE, 111).not().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.BYTE, -33).not().integerValue())

        assertEquals(100, RuntimeValueNumeric(DataType.BYTE, 10).mul(RuntimeValueNumeric(DataType.BYTE, 10)).integerValue())
        assertEquals(-56, RuntimeValueNumeric(DataType.BYTE, 20).mul(RuntimeValueNumeric(DataType.BYTE, 10)).integerValue())

        assertEquals(25, RuntimeValueNumeric(DataType.BYTE, 5).pow(RuntimeValueNumeric(DataType.BYTE, 2)).integerValue())
        assertEquals(125, RuntimeValueNumeric(DataType.BYTE, 5).pow(RuntimeValueNumeric(DataType.BYTE, 3)).integerValue())
        assertEquals(113, RuntimeValueNumeric(DataType.BYTE, 5).pow(RuntimeValueNumeric(DataType.BYTE, 4)).integerValue())

        assertEquals(100, RuntimeValueNumeric(DataType.BYTE, 50).shl().integerValue())
        assertEquals(-56, RuntimeValueNumeric(DataType.BYTE, 100).shl().integerValue())
        assertEquals(-2, RuntimeValueNumeric(DataType.BYTE, -1).shl().integerValue())
    }

    @Test
    fun arithmetictestWorrd() {
        assertEquals(32767, RuntimeValueNumeric(DataType.WORD, 32700).add(RuntimeValueNumeric(DataType.WORD, 67)).integerValue())
        assertEquals(-32768, RuntimeValueNumeric(DataType.WORD, 32700).add(RuntimeValueNumeric(DataType.WORD, 68)).integerValue())
        assertEquals(-32767, RuntimeValueNumeric(DataType.WORD, 32700).add(RuntimeValueNumeric(DataType.WORD, 69)).integerValue())

        assertEquals(1, RuntimeValueNumeric(DataType.WORD, 2).sub(RuntimeValueNumeric(DataType.WORD, 1)).integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.WORD, 2).sub(RuntimeValueNumeric(DataType.WORD, 2)).integerValue())
        assertEquals(-1, RuntimeValueNumeric(DataType.WORD, 2).sub(RuntimeValueNumeric(DataType.WORD, 3)).integerValue())
        assertEquals(-32768, RuntimeValueNumeric(DataType.WORD, -32700).sub(RuntimeValueNumeric(DataType.WORD, 68)).integerValue())
        assertEquals(32767, RuntimeValueNumeric(DataType.WORD, -32700).sub(RuntimeValueNumeric(DataType.WORD, 69)).integerValue())

        assertEquals(32767, RuntimeValueNumeric(DataType.WORD, 32766).inc().integerValue())
        assertEquals(-32768, RuntimeValueNumeric(DataType.WORD, 32767).inc().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.WORD, 1).dec().integerValue())
        assertEquals(-1, RuntimeValueNumeric(DataType.WORD, 0).dec().integerValue())
        assertEquals(-32768, RuntimeValueNumeric(DataType.WORD, -32767).dec().integerValue())
        assertEquals(32767, RuntimeValueNumeric(DataType.WORD, -32768).dec().integerValue())

        assertEquals(-1, RuntimeValueNumeric(DataType.WORD, 0).inv().integerValue())
        assertEquals(-103, RuntimeValueNumeric(DataType.WORD, 0b01100110).inv().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.WORD, 0).neg().integerValue())
        assertEquals(-2, RuntimeValueNumeric(DataType.WORD, 2).neg().integerValue())
        assertEquals(1, RuntimeValueNumeric(DataType.WORD, 0).not().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.WORD, 1).not().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.WORD, 111).not().integerValue())
        assertEquals(0, RuntimeValueNumeric(DataType.WORD, -33).not().integerValue())

        assertEquals(10000, RuntimeValueNumeric(DataType.WORD, 100).mul(RuntimeValueNumeric(DataType.WORD, 100)).integerValue())
        assertEquals(-25536, RuntimeValueNumeric(DataType.WORD, 200).mul(RuntimeValueNumeric(DataType.WORD, 200)).integerValue())

        assertEquals(15625, RuntimeValueNumeric(DataType.WORD, 5).pow(RuntimeValueNumeric(DataType.WORD, 6)).integerValue())
        assertEquals(-6487, RuntimeValueNumeric(DataType.WORD, 9).pow(RuntimeValueNumeric(DataType.WORD, 5)).integerValue())

        assertEquals(18000, RuntimeValueNumeric(DataType.WORD, 9000).shl().integerValue())
        assertEquals(-25536, RuntimeValueNumeric(DataType.WORD, 20000).shl().integerValue())
        assertEquals(-2, RuntimeValueNumeric(DataType.WORD, -1).shl().integerValue())
    }
}
