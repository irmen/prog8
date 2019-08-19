package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.DataType
import prog8.vm.RuntimeValue
import kotlin.test.*


private fun sameValueAndType(v1: RuntimeValue, v2: RuntimeValue): Boolean {
    return v1.type==v2.type && v1==v2
}


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestRuntimeValue {

    @Test
    fun testValueRanges() {
        assertEquals(0, RuntimeValue(DataType.UBYTE, 0).integerValue())
        assertEquals(255, RuntimeValue(DataType.UBYTE, 255).integerValue())
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.UBYTE, -1)}
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.UBYTE, 256)}

        assertEquals(0, RuntimeValue(DataType.BYTE, 0).integerValue())
        assertEquals(-128, RuntimeValue(DataType.BYTE, -128).integerValue())
        assertEquals(127, RuntimeValue(DataType.BYTE, 127).integerValue())
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.BYTE, -129)}
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.BYTE, 128)}

        assertEquals(0, RuntimeValue(DataType.UWORD, 0).integerValue())
        assertEquals(65535, RuntimeValue(DataType.UWORD, 65535).integerValue())
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.UWORD, -1)}
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.UWORD, 65536)}

        assertEquals(0, RuntimeValue(DataType.WORD, 0).integerValue())
        assertEquals(-32768, RuntimeValue(DataType.WORD, -32768).integerValue())
        assertEquals(32767, RuntimeValue(DataType.WORD, 32767).integerValue())
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.WORD, -32769)}
        assertFailsWith<IllegalArgumentException> { RuntimeValue(DataType.WORD, 32768)}
    }

    @Test
    fun testTruthiness()
    {
        assertFalse(RuntimeValue(DataType.BYTE, 0).asBoolean)
        assertFalse(RuntimeValue(DataType.UBYTE, 0).asBoolean)
        assertFalse(RuntimeValue(DataType.WORD, 0).asBoolean)
        assertFalse(RuntimeValue(DataType.UWORD, 0).asBoolean)
        assertFalse(RuntimeValue(DataType.FLOAT, 0.0).asBoolean)

        assertTrue(RuntimeValue(DataType.BYTE, 42).asBoolean)
        assertTrue(RuntimeValue(DataType.UBYTE, 42).asBoolean)
        assertTrue(RuntimeValue(DataType.WORD, 42).asBoolean)
        assertTrue(RuntimeValue(DataType.UWORD, 42).asBoolean)
        assertTrue(RuntimeValue(DataType.FLOAT, 42.0).asBoolean)
        assertTrue(RuntimeValue(DataType.BYTE, -42).asBoolean)
        assertTrue(RuntimeValue(DataType.WORD, -42).asBoolean)
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

    @Test
    fun arithmetictestUbyte() {
        assertEquals(255, RuntimeValue(DataType.UBYTE, 200).add(RuntimeValue(DataType.UBYTE, 55)).integerValue())
        assertEquals(0, RuntimeValue(DataType.UBYTE, 200).add(RuntimeValue(DataType.UBYTE, 56)).integerValue())
        assertEquals(1, RuntimeValue(DataType.UBYTE, 200).add(RuntimeValue(DataType.UBYTE, 57)).integerValue())

        assertEquals(1, RuntimeValue(DataType.UBYTE, 2).sub(RuntimeValue(DataType.UBYTE, 1)).integerValue())
        assertEquals(0, RuntimeValue(DataType.UBYTE, 2).sub(RuntimeValue(DataType.UBYTE, 2)).integerValue())
        assertEquals(255, RuntimeValue(DataType.UBYTE, 2).sub(RuntimeValue(DataType.UBYTE, 3)).integerValue())

        assertEquals(255, RuntimeValue(DataType.UBYTE, 254).inc().integerValue())
        assertEquals(0, RuntimeValue(DataType.UBYTE, 255).inc().integerValue())
        assertEquals(0, RuntimeValue(DataType.UBYTE, 1).dec().integerValue())
        assertEquals(255, RuntimeValue(DataType.UBYTE, 0).dec().integerValue())

        assertEquals(255, RuntimeValue(DataType.UBYTE, 0).inv().integerValue())
        assertEquals(0b00110011, RuntimeValue(DataType.UBYTE, 0b11001100).inv().integerValue())
//        assertEquals(0, RuntimeValue(DataType.UBYTE, 0).neg().integerValue())
//        assertEquals(0, RuntimeValue(DataType.UBYTE, 0).neg().integerValue())
        assertEquals(1, RuntimeValue(DataType.UBYTE, 0).not().integerValue())
        assertEquals(0, RuntimeValue(DataType.UBYTE, 1).not().integerValue())
        assertEquals(0, RuntimeValue(DataType.UBYTE, 111).not().integerValue())
        assertEquals(0, RuntimeValue(DataType.UBYTE, 255).not().integerValue())

        assertEquals(200, RuntimeValue(DataType.UBYTE, 20).mul(RuntimeValue(DataType.UBYTE, 10)).integerValue())
        assertEquals(144, RuntimeValue(DataType.UBYTE, 20).mul(RuntimeValue(DataType.UBYTE, 20)).integerValue())

        assertEquals(25, RuntimeValue(DataType.UBYTE, 5).pow(RuntimeValue(DataType.UBYTE, 2)).integerValue())
        assertEquals(125, RuntimeValue(DataType.UBYTE, 5).pow(RuntimeValue(DataType.UBYTE, 3)).integerValue())
        assertEquals(113, RuntimeValue(DataType.UBYTE, 5).pow(RuntimeValue(DataType.UBYTE, 4)).integerValue())

        assertEquals(100, RuntimeValue(DataType.UBYTE, 50).shl().integerValue())
        assertEquals(200, RuntimeValue(DataType.UBYTE, 100).shl().integerValue())
        assertEquals(144, RuntimeValue(DataType.UBYTE, 200).shl().integerValue())
    }

    @Test
    fun arithmetictestUWord() {
        assertEquals(65535, RuntimeValue(DataType.UWORD, 60000).add(RuntimeValue(DataType.UWORD, 5535)).integerValue())
        assertEquals(0, RuntimeValue(DataType.UWORD, 60000).add(RuntimeValue(DataType.UWORD, 5536)).integerValue())
        assertEquals(1, RuntimeValue(DataType.UWORD, 60000).add(RuntimeValue(DataType.UWORD, 5537)).integerValue())

        assertEquals(1, RuntimeValue(DataType.UWORD, 2).sub(RuntimeValue(DataType.UWORD, 1)).integerValue())
        assertEquals(0, RuntimeValue(DataType.UWORD, 2).sub(RuntimeValue(DataType.UWORD, 2)).integerValue())
        assertEquals(65535, RuntimeValue(DataType.UWORD, 2).sub(RuntimeValue(DataType.UWORD, 3)).integerValue())

        assertEquals(65535, RuntimeValue(DataType.UWORD, 65534).inc().integerValue())
        assertEquals(0, RuntimeValue(DataType.UWORD, 65535).inc().integerValue())
        assertEquals(0, RuntimeValue(DataType.UWORD, 1).dec().integerValue())
        assertEquals(65535, RuntimeValue(DataType.UWORD, 0).dec().integerValue())

        assertEquals(65535, RuntimeValue(DataType.UWORD, 0).inv().integerValue())
        assertEquals(0b0011001101010101, RuntimeValue(DataType.UWORD, 0b1100110010101010).inv().integerValue())
//        assertEquals(0, RuntimeValue(DataType.UWORD, 0).neg().integerValue())
//        assertEquals(0, RuntimeValue(DataType.UWORD, 0).neg().integerValue())
        assertEquals(1, RuntimeValue(DataType.UWORD, 0).not().integerValue())
        assertEquals(0, RuntimeValue(DataType.UWORD, 1).not().integerValue())
        assertEquals(0, RuntimeValue(DataType.UWORD, 11111).not().integerValue())
        assertEquals(0, RuntimeValue(DataType.UWORD, 65535).not().integerValue())

        assertEquals(2000, RuntimeValue(DataType.UWORD, 200).mul(RuntimeValue(DataType.UWORD, 10)).integerValue())
        assertEquals(40000, RuntimeValue(DataType.UWORD, 200).mul(RuntimeValue(DataType.UWORD, 200)).integerValue())
        assertEquals(14464, RuntimeValue(DataType.UWORD, 200).mul(RuntimeValue(DataType.UWORD, 400)).integerValue())

        assertEquals(15625, RuntimeValue(DataType.UWORD, 5).pow(RuntimeValue(DataType.UWORD, 6)).integerValue())
        assertEquals(12589, RuntimeValue(DataType.UWORD, 5).pow(RuntimeValue(DataType.UWORD, 7)).integerValue())

        assertEquals(10000, RuntimeValue(DataType.UWORD, 5000).shl().integerValue())
        assertEquals(60000, RuntimeValue(DataType.UWORD, 30000).shl().integerValue())
        assertEquals(14464, RuntimeValue(DataType.UWORD, 40000).shl().integerValue())
    }

    @Test
    fun arithmetictestByte() {
        assertEquals(127, RuntimeValue(DataType.BYTE, 100).add(RuntimeValue(DataType.BYTE, 27)).integerValue())
        assertEquals(-128, RuntimeValue(DataType.BYTE, 100).add(RuntimeValue(DataType.BYTE, 28)).integerValue())
        assertEquals(-127, RuntimeValue(DataType.BYTE, 100).add(RuntimeValue(DataType.BYTE, 29)).integerValue())

        assertEquals(1, RuntimeValue(DataType.BYTE, 2).sub(RuntimeValue(DataType.BYTE, 1)).integerValue())
        assertEquals(0, RuntimeValue(DataType.BYTE, 2).sub(RuntimeValue(DataType.BYTE, 2)).integerValue())
        assertEquals(-1, RuntimeValue(DataType.BYTE, 2).sub(RuntimeValue(DataType.BYTE, 3)).integerValue())
        assertEquals(-128, RuntimeValue(DataType.BYTE, -100).sub(RuntimeValue(DataType.BYTE, 28)).integerValue())
        assertEquals(127, RuntimeValue(DataType.BYTE, -100).sub(RuntimeValue(DataType.BYTE, 29)).integerValue())

        assertEquals(127, RuntimeValue(DataType.BYTE, 126).inc().integerValue())
        assertEquals(-128, RuntimeValue(DataType.BYTE, 127).inc().integerValue())
        assertEquals(0, RuntimeValue(DataType.BYTE, 1).dec().integerValue())
        assertEquals(-1, RuntimeValue(DataType.BYTE, 0).dec().integerValue())
        assertEquals(-128, RuntimeValue(DataType.BYTE, -127).dec().integerValue())
        assertEquals(127, RuntimeValue(DataType.BYTE, -128).dec().integerValue())

        assertEquals(-1, RuntimeValue(DataType.BYTE, 0).inv().integerValue())
        assertEquals(-103, RuntimeValue(DataType.BYTE, 0b01100110).inv().integerValue())
        assertEquals(0, RuntimeValue(DataType.BYTE, 0).neg().integerValue())
        assertEquals(-2, RuntimeValue(DataType.BYTE, 2).neg().integerValue())
        assertEquals(1, RuntimeValue(DataType.BYTE, 0).not().integerValue())
        assertEquals(0, RuntimeValue(DataType.BYTE, 1).not().integerValue())
        assertEquals(0, RuntimeValue(DataType.BYTE, 111).not().integerValue())
        assertEquals(0, RuntimeValue(DataType.BYTE, -33).not().integerValue())

        assertEquals(100, RuntimeValue(DataType.BYTE, 10).mul(RuntimeValue(DataType.BYTE, 10)).integerValue())
        assertEquals(-56, RuntimeValue(DataType.BYTE, 20).mul(RuntimeValue(DataType.BYTE, 10)).integerValue())

        assertEquals(25, RuntimeValue(DataType.BYTE, 5).pow(RuntimeValue(DataType.BYTE, 2)).integerValue())
        assertEquals(125, RuntimeValue(DataType.BYTE, 5).pow(RuntimeValue(DataType.BYTE, 3)).integerValue())
        assertEquals(113, RuntimeValue(DataType.BYTE, 5).pow(RuntimeValue(DataType.BYTE, 4)).integerValue())

        assertEquals(100, RuntimeValue(DataType.BYTE, 50).shl().integerValue())
        assertEquals(-56, RuntimeValue(DataType.BYTE, 100).shl().integerValue())
        assertEquals(-2, RuntimeValue(DataType.BYTE, -1).shl().integerValue())
    }

    @Test
    fun arithmetictestWorrd() {
        assertEquals(32767, RuntimeValue(DataType.WORD, 32700).add(RuntimeValue(DataType.WORD, 67)).integerValue())
        assertEquals(-32768, RuntimeValue(DataType.WORD, 32700).add(RuntimeValue(DataType.WORD, 68)).integerValue())
        assertEquals(-32767, RuntimeValue(DataType.WORD, 32700).add(RuntimeValue(DataType.WORD, 69)).integerValue())

        assertEquals(1, RuntimeValue(DataType.WORD, 2).sub(RuntimeValue(DataType.WORD, 1)).integerValue())
        assertEquals(0, RuntimeValue(DataType.WORD, 2).sub(RuntimeValue(DataType.WORD, 2)).integerValue())
        assertEquals(-1, RuntimeValue(DataType.WORD, 2).sub(RuntimeValue(DataType.WORD, 3)).integerValue())
        assertEquals(-32768, RuntimeValue(DataType.WORD, -32700).sub(RuntimeValue(DataType.WORD, 68)).integerValue())
        assertEquals(32767, RuntimeValue(DataType.WORD, -32700).sub(RuntimeValue(DataType.WORD, 69)).integerValue())

        assertEquals(32767, RuntimeValue(DataType.WORD, 32766).inc().integerValue())
        assertEquals(-32768, RuntimeValue(DataType.WORD, 32767).inc().integerValue())
        assertEquals(0, RuntimeValue(DataType.WORD, 1).dec().integerValue())
        assertEquals(-1, RuntimeValue(DataType.WORD, 0).dec().integerValue())
        assertEquals(-32768, RuntimeValue(DataType.WORD, -32767).dec().integerValue())
        assertEquals(32767, RuntimeValue(DataType.WORD, -32768).dec().integerValue())

        assertEquals(-1, RuntimeValue(DataType.WORD, 0).inv().integerValue())
        assertEquals(-103, RuntimeValue(DataType.WORD, 0b01100110).inv().integerValue())
        assertEquals(0, RuntimeValue(DataType.WORD, 0).neg().integerValue())
        assertEquals(-2, RuntimeValue(DataType.WORD, 2).neg().integerValue())
        assertEquals(1, RuntimeValue(DataType.WORD, 0).not().integerValue())
        assertEquals(0, RuntimeValue(DataType.WORD, 1).not().integerValue())
        assertEquals(0, RuntimeValue(DataType.WORD, 111).not().integerValue())
        assertEquals(0, RuntimeValue(DataType.WORD, -33).not().integerValue())

        assertEquals(10000, RuntimeValue(DataType.WORD, 100).mul(RuntimeValue(DataType.WORD, 100)).integerValue())
        assertEquals(-25536, RuntimeValue(DataType.WORD, 200).mul(RuntimeValue(DataType.WORD, 200)).integerValue())

        assertEquals(15625, RuntimeValue(DataType.WORD, 5).pow(RuntimeValue(DataType.WORD, 6)).integerValue())
        assertEquals(-6487, RuntimeValue(DataType.WORD, 9).pow(RuntimeValue(DataType.WORD, 5)).integerValue())

        assertEquals(18000, RuntimeValue(DataType.WORD, 9000).shl().integerValue())
        assertEquals(-25536, RuntimeValue(DataType.WORD, 20000).shl().integerValue())
        assertEquals(-2, RuntimeValue(DataType.WORD, -1).shl().integerValue())
    }
}
