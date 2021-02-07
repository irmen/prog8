package prog8tests

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.IBuiltinFunctions
import prog8.ast.Program
import prog8.ast.Module
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.toHex
import prog8.compiler.*
import prog8.compiler.target.C64Target
import prog8.compiler.target.CompilationTarget
import prog8.compiler.target.c64.C64MachineDefinition.C64Zeropage
import prog8.compiler.target.c64.C64MachineDefinition.FLOAT_MAX_NEGATIVE
import prog8.compiler.target.c64.C64MachineDefinition.FLOAT_MAX_POSITIVE
import prog8.compiler.target.c64.C64MachineDefinition.Mflpt5
import prog8.compiler.target.c64.Petscii
import prog8.compiler.target.cx16.CX16MachineDefinition
import java.io.CharConversionException
import java.nio.file.Path
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCompiler {
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
        assertFailsWith<CompilerException> { Mflpt5.fromNumber(1.7014118346e+38) }
        assertFailsWith<CompilerException> { Mflpt5.fromNumber(-1.7014118346e+38) }
        assertFailsWith<CompilerException> { Mflpt5.fromNumber(1.7014118347e+38) }
        assertFailsWith<CompilerException> { Mflpt5.fromNumber(-1.7014118347e+38) }
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


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestC64Zeropage {

    private val errors = ErrorReporter()

    @Test
    fun testNames() {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false))

        zp.allocate("", DataType.UBYTE, null, errors)
        zp.allocate("", DataType.UBYTE, null, errors)
        zp.allocate("varname", DataType.UBYTE, null, errors)
        assertFailsWith<AssertionError> {
            zp.allocate("varname", DataType.UBYTE, null, errors)
        }
        zp.allocate("varname2", DataType.UBYTE, null, errors)
    }

    @Test
    fun testZpFloatEnable() {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false))
        assertFailsWith<CompilerException> {
            zp.allocate("", DataType.FLOAT, null, errors)
        }
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.DONTUSE, emptyList(), true, false))
        assertFailsWith<CompilerException> {
            zp2.allocate("", DataType.FLOAT, null, errors)
        }
        val zp3 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), true, false))
        zp3.allocate("", DataType.FLOAT, null, errors)
    }

    @Test
    fun testZpModesWithFloats() {
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), false, false))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), true, false))
        assertFailsWith<CompilerException> {
            C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), true, false))
        }
        assertFailsWith<CompilerException> {
            C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), true, false))
        }
    }

    @Test
    fun testZpDontuse() {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.DONTUSE, emptyList(), false, false))
        println(zp.free)
        assertEquals(0, zp.available())
        assertFailsWith<CompilerException> {
            zp.allocate("", DataType.BYTE, null, errors)
        }
    }

    @Test
    fun testFreeSpaces() {
        val zp1 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false))
        assertEquals(18, zp1.available())
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), false, false))
        assertEquals(89, zp2.available())
        val zp3 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false))
        assertEquals(125, zp3.available())
        val zp4 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false))
        assertEquals(238, zp4.available())
    }

    @Test
    fun testReservedSpace() {
        val zp1 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false))
        assertEquals(238, zp1.available())
        assertTrue(50 in zp1.free)
        assertTrue(100 in zp1.free)
        assertTrue(49 in zp1.free)
        assertTrue(101 in zp1.free)
        assertTrue(200 in zp1.free)
        assertTrue(255 in zp1.free)
        assertTrue(199 in zp1.free)
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, listOf(50 .. 100, 200..255), false, false))
        assertEquals(139, zp2.available())
        assertFalse(50 in zp2.free)
        assertFalse(100 in zp2.free)
        assertTrue(49 in zp2.free)
        assertTrue(101 in zp2.free)
        assertFalse(200 in zp2.free)
        assertFalse(255 in zp2.free)
        assertTrue(199 in zp2.free)
    }

    @Test
    fun testBasicsafeAllocation() {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false))
        assertEquals(18, zp.available())

        assertFailsWith<ZeropageDepletedError> {
            // in regular zp there aren't 5 sequential bytes free
            zp.allocate("", DataType.FLOAT, null, errors)
        }

        for (i in 0 until zp.available()) {
            val loc = zp.allocate("", DataType.UBYTE, null, errors)
            assertTrue(loc > 0)
        }
        assertEquals(0, zp.available())
        assertFailsWith<ZeropageDepletedError> {
            zp.allocate("", DataType.UBYTE, null, errors)
        }
        assertFailsWith<ZeropageDepletedError> {
            zp.allocate("", DataType.UWORD, null, errors)
        }
    }

    @Test
    fun testFullAllocation() {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false))
        assertEquals(238, zp.available())
        val loc = zp.allocate("", DataType.UWORD, null, errors)
        assertTrue(loc > 3)
        assertFalse(loc in zp.free)
        val num = zp.available() / 2

        for(i in 0..num-4) {
            zp.allocate("", DataType.UWORD, null, errors)
        }
        assertEquals(6,zp.available())

        assertFailsWith<ZeropageDepletedError> {
            // can't allocate because no more sequential bytes, only fragmented
            zp.allocate("", DataType.UWORD, null, errors)
        }

        for(i in 0..5) {
            zp.allocate("", DataType.UBYTE, null, errors)
        }

        assertEquals(0, zp.available())
        assertFailsWith<ZeropageDepletedError> {
            // no more space
            zp.allocate("", DataType.UBYTE, null, errors)
        }
    }

    @Test
    fun testEfficientAllocation() {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(),  true, false))
        assertEquals(18, zp.available())
        assertEquals(0x04, zp.allocate("", DataType.WORD, null, errors))
        assertEquals(0x06, zp.allocate("", DataType.UBYTE, null, errors))
        assertEquals(0x0a, zp.allocate("", DataType.UBYTE, null, errors))
        assertEquals(0x9b, zp.allocate("", DataType.UWORD, null, errors))
        assertEquals(0x9e, zp.allocate("", DataType.UWORD, null, errors))
        assertEquals(0xa5, zp.allocate("", DataType.UWORD, null, errors))
        assertEquals(0xb0, zp.allocate("", DataType.UWORD, null, errors))
        assertEquals(0xbe, zp.allocate("", DataType.UWORD, null, errors))
        assertEquals(0x0e, zp.allocate("", DataType.UBYTE, null, errors))
        assertEquals(0x92, zp.allocate("", DataType.UBYTE, null, errors))
        assertEquals(0x96, zp.allocate("", DataType.UBYTE, null, errors))
        assertEquals(0xf9, zp.allocate("", DataType.UBYTE, null, errors))
        assertEquals(0, zp.available())
    }

    @Test
    fun testReservedLocations() {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false))
        assertEquals(zp.SCRATCH_REG, zp.SCRATCH_B1+1, "zp _B1 and _REG must be next to each other to create a word")
    }
}


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCx16Zeropage {
    @Test
    fun testReservedLocations() {
        val zp = CX16MachineDefinition.CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false))
        assertEquals(zp.SCRATCH_REG, zp.SCRATCH_B1+1, "zp _B1 and _REG must be next to each other to create a word")
    }
}


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestPetscii {

    @Test
    fun testZero() {
        assertThat(Petscii.encodePetscii("\u0000", true), equalTo(listOf<Short>(0)))
        assertThat(Petscii.encodePetscii("\u0000", false), equalTo(listOf<Short>(0)))
        assertThat(Petscii.decodePetscii(listOf(0), true), equalTo("\u0000"))
        assertThat(Petscii.decodePetscii(listOf(0), false), equalTo("\u0000"))
    }

    @Test
    fun testLowercase() {
        assertThat(Petscii.encodePetscii("hello WORLD 123 @!£", true), equalTo(
                listOf<Short>(72, 69, 76, 76, 79, 32, 0xd7, 0xcf, 0xd2, 0xcc, 0xc4, 32, 49, 50, 51, 32, 64, 33, 0x5c)))
        assertThat(Petscii.encodePetscii("\uf11a", true), equalTo(listOf<Short>(0x12)))   // reverse vid
        assertThat(Petscii.encodePetscii("✓", true), equalTo(listOf<Short>(0xfa)))
        assertFailsWith<CharConversionException> { Petscii.encodePetscii("π", true) }
        assertFailsWith<CharConversionException> { Petscii.encodePetscii("♥", true) }

        assertThat(Petscii.decodePetscii(listOf(72, 0xd7, 0x5c, 0xfa, 0x12), true), equalTo("hW£✓\uF11A"))
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodePetscii(listOf(-1), true) }
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodePetscii(listOf(256), true) }
    }

    @Test
    fun testUppercase() {
        assertThat(Petscii.encodePetscii("HELLO 123 @!£"), equalTo(
                listOf<Short>(72, 69, 76, 76, 79, 32, 49, 50, 51, 32, 64, 33, 0x5c)))
        assertThat(Petscii.encodePetscii("\uf11a"), equalTo(listOf<Short>(0x12)))   // reverse vid
        assertThat(Petscii.encodePetscii("♥"), equalTo(listOf<Short>(0xd3)))
        assertThat(Petscii.encodePetscii("π"), equalTo(listOf<Short>(0xff)))
        assertFailsWith<CharConversionException> { Petscii.encodePetscii("✓") }

        assertThat(Petscii.decodePetscii(listOf(72, 0x5c, 0xd3, 0xff)), equalTo("H£♥π"))
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodePetscii(listOf(-1)) }
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodePetscii(listOf(256)) }
    }

    @Test
    fun testScreencodeLowercase() {
        assertThat(Petscii.encodeScreencode("hello WORLD 123 @!£", true), equalTo(
                listOf<Short>(0x08, 0x05, 0x0c, 0x0c, 0x0f, 0x20, 0x57, 0x4f, 0x52, 0x4c, 0x44, 0x20, 0x31, 0x32, 0x33, 0x20, 0x00, 0x21, 0x1c)
        ))
        assertThat(Petscii.encodeScreencode("✓", true), equalTo(listOf<Short>(0x7a)))
        assertFailsWith<CharConversionException> { Petscii.encodeScreencode("♥", true) }
        assertFailsWith<CharConversionException> { Petscii.encodeScreencode("π", true) }

        assertThat(Petscii.decodeScreencode(listOf(0x08, 0x57, 0x1c, 0x7a), true), equalTo("hW£✓"))
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodeScreencode(listOf(-1), true) }
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodeScreencode(listOf(256), true) }
    }

    @Test
    fun testScreencodeUppercase() {
        assertThat(Petscii.encodeScreencode("WORLD 123 @!£"), equalTo(
                listOf<Short>(0x17, 0x0f, 0x12, 0x0c, 0x04, 0x20, 0x31, 0x32, 0x33, 0x20, 0x00, 0x21, 0x1c)))
        assertThat(Petscii.encodeScreencode("♥"), equalTo(listOf<Short>(0x53)))
        assertThat(Petscii.encodeScreencode("π"), equalTo(listOf<Short>(0x5e)))
        assertFailsWith<CharConversionException> { Petscii.encodeScreencode("✓") }
        assertFailsWith<CharConversionException> { Petscii.encodeScreencode("hello") }

        assertThat(Petscii.decodeScreencode(listOf(0x17, 0x1c, 0x53, 0x5e)), equalTo("W£♥π"))
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodeScreencode(listOf(-1)) }
        assertFailsWith<ArrayIndexOutOfBoundsException> { Petscii.decodeScreencode(listOf(256)) }
    }

    @Test
    fun testLiteralValueComparisons() {
        val ten = NumericLiteralValue(DataType.UWORD, 10, Position.DUMMY)
        val nine = NumericLiteralValue(DataType.UBYTE, 9, Position.DUMMY)
        assertEquals(ten, ten)
        assertNotEquals(ten, nine)
        assertFalse(ten != ten)
        assertTrue(ten != nine)

        assertTrue(ten > nine)
        assertTrue(ten >= nine)
        assertTrue(ten >= ten)
        assertFalse(ten > ten)

        assertFalse(ten < nine)
        assertFalse(ten <= nine)
        assertTrue(ten <= ten)
        assertFalse(ten < ten)

        val abc = StringLiteralValue("abc", false, Position.DUMMY)
        val abd = StringLiteralValue("abd", false, Position.DUMMY)
        assertEquals(abc, abc)
        assertTrue(abc!=abd)
        assertFalse(abc!=abc)
    }
}


class TestMemory {
    private class DummyFunctions: IBuiltinFunctions {
        override val names: Set<String> = emptySet()
        override fun constValue(name: String, args: List<Expression>, position: Position): NumericLiteralValue? = null
        override fun returnType(name: String, args: MutableList<Expression>) = InferredTypes.InferredType.unknown()
    }

    @Test
    fun testInValidRamC64_memory_addresses() {
        CompilationTarget.instance = C64Target

        var memexpr = NumericLiteralValue.optimalInteger(0x0000, Position.DUMMY)
        var target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        val program = Program("test", mutableListOf(), DummyFunctions())
        assertTrue(CompilationTarget.instance.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0x1000, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertTrue(CompilationTarget.instance.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0x9fff, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertTrue(CompilationTarget.instance.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0xc000, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertTrue(CompilationTarget.instance.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0xcfff, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertTrue(CompilationTarget.instance.isInRegularRAM(target, program))
    }

    @Test
    fun testNotInValidRamC64_memory_addresses() {
        CompilationTarget.instance = C64Target

        var memexpr = NumericLiteralValue.optimalInteger(0xa000, Position.DUMMY)
        var target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        val program = Program("test", mutableListOf(), DummyFunctions())
        assertFalse(CompilationTarget.instance.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0xafff, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertFalse(CompilationTarget.instance.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0xd000, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertFalse(CompilationTarget.instance.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0xffff, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertFalse(CompilationTarget.instance.isInRegularRAM(target, program))
    }

    @Test
    fun testInValidRamC64_memory_identifiers() {
        CompilationTarget.instance = C64Target
        var target = createTestProgramForMemoryRefViaVar(0x1000, VarDeclType.VAR)
        val program = Program("test", mutableListOf(), DummyFunctions())

        assertTrue(CompilationTarget.instance.isInRegularRAM(target, program))
        target = createTestProgramForMemoryRefViaVar(0xd020, VarDeclType.VAR)
        assertFalse(CompilationTarget.instance.isInRegularRAM(target, program))
        target = createTestProgramForMemoryRefViaVar(0x1000, VarDeclType.CONST)
        assertTrue(CompilationTarget.instance.isInRegularRAM(target, program))
        target = createTestProgramForMemoryRefViaVar(0xd020, VarDeclType.CONST)
        assertFalse(CompilationTarget.instance.isInRegularRAM(target, program))
        target = createTestProgramForMemoryRefViaVar(0x1000, VarDeclType.MEMORY)
        assertFalse(CompilationTarget.instance.isInRegularRAM(target, program))
    }

    @Test
    private fun createTestProgramForMemoryRefViaVar(address: Int, vartype: VarDeclType): AssignTarget {
        val decl = VarDecl(vartype, DataType.BYTE, ZeropageWish.DONTCARE, null, "address", null, NumericLiteralValue.optimalInteger(address, Position.DUMMY), false, false, Position.DUMMY)
        val memexpr = IdentifierReference(listOf("address"), Position.DUMMY)
        val target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, false, Path.of(""))
        module.linkParents(ParentSentinel)
        return target
    }

    @Test
    fun testInValidRamC64_memory_expression() {
        CompilationTarget.instance = C64Target
        val memexpr = PrefixExpression("+", NumericLiteralValue.optimalInteger(0x1000, Position.DUMMY), Position.DUMMY)
        val target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        val program = Program("test", mutableListOf(), DummyFunctions())
        assertFalse(CompilationTarget.instance.isInRegularRAM(target, program))
    }

    @Test
    fun testInValidRamC64_variable() {
        CompilationTarget.instance = C64Target
        val decl = VarDecl(VarDeclType.VAR, DataType.BYTE, ZeropageWish.DONTCARE, null, "address", null, null, false, false, Position.DUMMY)
        val target = AssignTarget(IdentifierReference(listOf("address"), Position.DUMMY), null, null, Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, false, Path.of(""))
        val program = Program("test", mutableListOf(module), DummyFunctions())
        module.linkParents(ParentSentinel)
        assertTrue(CompilationTarget.instance.isInRegularRAM(target, program))
    }

    @Test
    fun testInValidRamC64_memmap_variable() {
        CompilationTarget.instance = C64Target
        val address = 0x1000
        val decl = VarDecl(VarDeclType.MEMORY, DataType.UBYTE, ZeropageWish.DONTCARE, null, "address", null, NumericLiteralValue.optimalInteger(address, Position.DUMMY), false, false, Position.DUMMY)
        val target = AssignTarget(IdentifierReference(listOf("address"), Position.DUMMY), null, null, Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, false, Path.of(""))
        val program = Program("test", mutableListOf(module), DummyFunctions())
        module.linkParents(ParentSentinel)
        assertTrue(CompilationTarget.instance.isInRegularRAM(target, program))
    }

    @Test
    fun testNotInValidRamC64_memmap_variable() {
        CompilationTarget.instance = C64Target
        val address = 0xd020
        val decl = VarDecl(VarDeclType.MEMORY, DataType.UBYTE, ZeropageWish.DONTCARE, null, "address", null, NumericLiteralValue.optimalInteger(address, Position.DUMMY), false, false, Position.DUMMY)
        val target = AssignTarget(IdentifierReference(listOf("address"), Position.DUMMY), null, null, Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, false, Path.of(""))
        val program = Program("test", mutableListOf(module), DummyFunctions())
        module.linkParents(ParentSentinel)
        assertFalse(CompilationTarget.instance.isInRegularRAM(target, program))
    }

    @Test
    fun testInValidRamC64_array() {
        CompilationTarget.instance = C64Target
        val decl = VarDecl(VarDeclType.VAR, DataType.ARRAY_UB, ZeropageWish.DONTCARE, null, "address", null, null, false, false, Position.DUMMY)
        val arrayindexed = ArrayIndexedExpression(IdentifierReference(listOf("address"), Position.DUMMY), ArrayIndex(NumericLiteralValue.optimalInteger(1, Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val target = AssignTarget(null, arrayindexed, null, Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, false, Path.of(""))
        val program = Program("test", mutableListOf(module), DummyFunctions())
        module.linkParents(ParentSentinel)
        assertTrue(CompilationTarget.instance.isInRegularRAM(target, program))
    }

    @Test
    fun testInValidRamC64_array_memmapped() {
        CompilationTarget.instance = C64Target
        val address = 0x1000
        val decl = VarDecl(VarDeclType.MEMORY, DataType.ARRAY_UB, ZeropageWish.DONTCARE, null, "address", null, NumericLiteralValue.optimalInteger(address, Position.DUMMY), false, false, Position.DUMMY)
        val arrayindexed = ArrayIndexedExpression(IdentifierReference(listOf("address"), Position.DUMMY), ArrayIndex(NumericLiteralValue.optimalInteger(1, Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val target = AssignTarget(null, arrayindexed, null, Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, false, Path.of(""))
        val program = Program("test", mutableListOf(module), DummyFunctions())
        module.linkParents(ParentSentinel)
        assertTrue(CompilationTarget.instance.isInRegularRAM(target, program))
    }

    @Test
    fun testNotValidRamC64_array_memmapped() {
        CompilationTarget.instance = C64Target
        val address = 0xe000
        val decl = VarDecl(VarDeclType.MEMORY, DataType.ARRAY_UB, ZeropageWish.DONTCARE, null, "address", null, NumericLiteralValue.optimalInteger(address, Position.DUMMY), false, false, Position.DUMMY)
        val arrayindexed = ArrayIndexedExpression(IdentifierReference(listOf("address"), Position.DUMMY), ArrayIndex(NumericLiteralValue.optimalInteger(1, Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val target = AssignTarget(null, arrayindexed, null, Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, false, Path.of(""))
        val program = Program("test", mutableListOf(module), DummyFunctions())
        module.linkParents(ParentSentinel)
        assertFalse(CompilationTarget.instance.isInRegularRAM(target, program))
    }
}
