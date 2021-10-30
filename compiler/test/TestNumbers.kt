package prog8tests

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.toHex
import prog8.compiler.target.c64.C64MachineDefinition.FLOAT_MAX_NEGATIVE
import prog8.compiler.target.c64.C64MachineDefinition.FLOAT_MAX_POSITIVE
import prog8.compiler.target.c64.C64MachineDefinition.Mflpt5
import prog8.compilerinterface.InternalCompilerException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestNumbers {
    @Test
    fun testToHex() {
        assertEquals("0", 0.toHex())
        assertEquals("1", 1.toHex())
        assertEquals("1", 1.234.toHex())
        assertEquals("10", 10.toHex())
        assertEquals("10", 10.99.toHex())
        assertEquals("15", 15.toHex())
        assertEquals("\$10", 16.toHex())
        assertEquals("\$ff", 255.toHex())
        assertEquals("\$0100", 256.toHex())
        assertEquals("\$4e5c", 20060.toHex())
        assertEquals("\$c382", 50050.toHex())
        assertEquals("\$ffff", 65535.toHex())
        assertEquals("\$ffff", 65535L.toHex())
        assertEquals("0", 0.toHex())
        assertEquals("-1", (-1).toHex())
        assertEquals("-1", (-1.234).toHex())
        assertEquals("-10", (-10).toHex())
        assertEquals("-10", (-10.99).toHex())
        assertEquals("-15", (-15).toHex())
        assertEquals("-\$10", (-16).toHex())
        assertEquals("-\$ff", (-255).toHex())
        assertEquals("-\$0100", (-256).toHex())
        assertEquals("-\$4e5c", (-20060).toHex())
        assertEquals("-\$c382", (-50050).toHex())
        assertEquals("-\$ffff", (-65535).toHex())
        assertEquals("-\$ffff", (-65535L).toHex())
        assertFailsWith<IllegalArgumentException> { 65536.toHex()  }
        assertFailsWith<IllegalArgumentException> { 65536L.toHex()  }
    }

    @Test
    fun testFloatToMflpt5() {
        assertThat(Mflpt5.fromNumber(0), equalTo(Mflpt5(0x00, 0x00, 0x00, 0x00, 0x00)))
        assertThat(Mflpt5.fromNumber(3.141592653), equalTo(Mflpt5(0x82, 0x49, 0x0F, 0xDA, 0xA1)))
        assertThat(Mflpt5.fromNumber(3.141592653589793), equalTo(Mflpt5(0x82, 0x49, 0x0F, 0xDA, 0xA2)))
        assertThat(Mflpt5.fromNumber(32768), equalTo(Mflpt5(0x90, 0x00, 0x00, 0x00, 0x00)))
        assertThat(Mflpt5.fromNumber(-32768), equalTo(Mflpt5(0x90, 0x80, 0x00, 0x00, 0x00)))
        assertThat(Mflpt5.fromNumber(1), equalTo(Mflpt5(0x81, 0x00, 0x00, 0x00, 0x00)))
        assertThat(Mflpt5.fromNumber(0.7071067812), equalTo(Mflpt5(0x80, 0x35, 0x04, 0xF3, 0x34)))
        assertThat(Mflpt5.fromNumber(0.7071067811865476), equalTo(Mflpt5(0x80, 0x35, 0x04, 0xF3, 0x33)))
        assertThat(Mflpt5.fromNumber(1.4142135624), equalTo(Mflpt5(0x81, 0x35, 0x04, 0xF3, 0x34)))
        assertThat(Mflpt5.fromNumber(1.4142135623730951), equalTo(Mflpt5(0x81, 0x35, 0x04, 0xF3, 0x33)))
        assertThat(Mflpt5.fromNumber(-.5), equalTo(Mflpt5(0x80, 0x80, 0x00, 0x00, 0x00)))
        assertThat(Mflpt5.fromNumber(0.69314718061), equalTo(Mflpt5(0x80, 0x31, 0x72, 0x17, 0xF8)))
        assertThat(Mflpt5.fromNumber(0.6931471805599453), equalTo(Mflpt5(0x80, 0x31, 0x72, 0x17, 0xF7)))
        assertThat(Mflpt5.fromNumber(10), equalTo(Mflpt5(0x84, 0x20, 0x00, 0x00, 0x00)))
        assertThat(Mflpt5.fromNumber(1000000000), equalTo(Mflpt5(0x9E, 0x6E, 0x6B, 0x28, 0x00)))
        assertThat(Mflpt5.fromNumber(.5), equalTo(Mflpt5(0x80, 0x00, 0x00, 0x00, 0x00)))
        assertThat(Mflpt5.fromNumber(1.4426950408889634), equalTo(Mflpt5(0x81, 0x38, 0xAA, 0x3B, 0x29)))
        assertThat(Mflpt5.fromNumber(1.5707963267948966), equalTo(Mflpt5(0x81, 0x49, 0x0F, 0xDA, 0xA2)))
        assertThat(Mflpt5.fromNumber(6.283185307179586), equalTo(Mflpt5(0x83, 0x49, 0x0F, 0xDA, 0xA2)))
        assertThat(Mflpt5.fromNumber(.25), equalTo(Mflpt5(0x7F, 0x00, 0x00, 0x00, 0x00)))
        assertThat(Mflpt5.fromNumber(123.45678e22), equalTo(Mflpt5(0xd1, 0x02, 0xb7, 0x06, 0xfb)))
        assertThat(Mflpt5.fromNumber(-123.45678e-22), equalTo(Mflpt5(0x3e, 0xe9, 0x34, 0x09, 0x1b)))
    }

    @Test
    fun testFloatRange() {
        assertThat(Mflpt5.fromNumber(FLOAT_MAX_POSITIVE), equalTo(Mflpt5(0xff, 0x7f, 0xff, 0xff, 0xff)))
        assertThat(Mflpt5.fromNumber(FLOAT_MAX_NEGATIVE), equalTo(Mflpt5(0xff, 0xff, 0xff, 0xff, 0xff)))
        assertThat(Mflpt5.fromNumber(1.7e-38), equalTo(Mflpt5(0x03, 0x39, 0x1d, 0x15, 0x63)))
        assertThat(Mflpt5.fromNumber(1.7e-39), equalTo(Mflpt5(0x00, 0x00, 0x00, 0x00, 0x00)))
        assertThat(Mflpt5.fromNumber(-1.7e-38), equalTo(Mflpt5(0x03, 0xb9, 0x1d, 0x15, 0x63)))
        assertThat(Mflpt5.fromNumber(-1.7e-39), equalTo(Mflpt5(0x00, 0x00, 0x00, 0x00, 0x00)))
        assertFailsWith<InternalCompilerException> { Mflpt5.fromNumber(1.7014118346e+38) }
        assertFailsWith<InternalCompilerException> { Mflpt5.fromNumber(-1.7014118346e+38) }
        assertFailsWith<InternalCompilerException> { Mflpt5.fromNumber(1.7014118347e+38) }
        assertFailsWith<InternalCompilerException> { Mflpt5.fromNumber(-1.7014118347e+38) }
    }

    @Test
    fun testMflpt5ToFloat() {
        val epsilon=0.000000001
        assertThat(Mflpt5(0x00, 0x00, 0x00, 0x00, 0x00).toDouble(), equalTo(0.0))
        assertThat(Mflpt5(0x82, 0x49, 0x0F, 0xDA, 0xA1).toDouble(), closeTo(3.141592653, epsilon))
        assertThat(Mflpt5(0x82, 0x49, 0x0F, 0xDA, 0xA2).toDouble(), closeTo(3.141592653589793, epsilon))
        assertThat(Mflpt5(0x90, 0x00, 0x00, 0x00, 0x00).toDouble(), equalTo(32768.0))
        assertThat(Mflpt5(0x90, 0x80, 0x00, 0x00, 0x00).toDouble(), equalTo(-32768.0))
        assertThat(Mflpt5(0x81, 0x00, 0x00, 0x00, 0x00).toDouble(), equalTo(1.0))
        assertThat(Mflpt5(0x80, 0x35, 0x04, 0xF3, 0x34).toDouble(), closeTo(0.7071067812, epsilon))
        assertThat(Mflpt5(0x80, 0x35, 0x04, 0xF3, 0x33).toDouble(), closeTo(0.7071067811865476, epsilon))
        assertThat(Mflpt5(0x81, 0x35, 0x04, 0xF3, 0x34).toDouble(), closeTo(1.4142135624, epsilon))
        assertThat(Mflpt5(0x81, 0x35, 0x04, 0xF3, 0x33).toDouble(), closeTo(1.4142135623730951, epsilon))
        assertThat(Mflpt5(0x80, 0x80, 0x00, 0x00, 0x00).toDouble(), equalTo(-.5))
        assertThat(Mflpt5(0x80, 0x31, 0x72, 0x17, 0xF8).toDouble(), closeTo(0.69314718061, epsilon))
        assertThat(Mflpt5(0x80, 0x31, 0x72, 0x17, 0xF7).toDouble(), closeTo(0.6931471805599453, epsilon))
        assertThat(Mflpt5(0x84, 0x20, 0x00, 0x00, 0x00).toDouble(), equalTo(10.0))
        assertThat(Mflpt5(0x9E, 0x6E, 0x6B, 0x28, 0x00).toDouble(), equalTo(1000000000.0))
        assertThat(Mflpt5(0x80, 0x00, 0x00, 0x00, 0x00).toDouble(), equalTo(.5))
        assertThat(Mflpt5(0x81, 0x38, 0xAA, 0x3B, 0x29).toDouble(), closeTo(1.4426950408889634, epsilon))
        assertThat(Mflpt5(0x81, 0x49, 0x0F, 0xDA, 0xA2).toDouble(), closeTo(1.5707963267948966, epsilon))
        assertThat(Mflpt5(0x83, 0x49, 0x0F, 0xDA, 0xA2).toDouble(), closeTo(6.283185307179586, epsilon))
        assertThat(Mflpt5(0x7F, 0x00, 0x00, 0x00, 0x00).toDouble(), equalTo(.25))
        assertThat(Mflpt5(0xd1, 0x02, 0xb7, 0x06, 0xfb).toDouble(), closeTo(123.45678e22, 1.0e15))
        assertThat(Mflpt5(0x3e, 0xe9, 0x34, 0x09, 0x1b).toDouble(), closeTo(-123.45678e-22, epsilon))
    }
}
