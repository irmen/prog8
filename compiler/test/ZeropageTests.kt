package prog8tests

import org.junit.jupiter.api.*
import kotlin.test.*

import prog8.ast.base.DataType
import prog8.compiler.*
import prog8.compiler.target.C64Target
import prog8.compiler.target.Cx16Target
import prog8.compiler.target.c64.C64MachineDefinition.C64Zeropage
import prog8.compiler.target.cx16.CX16MachineDefinition.CX16Zeropage


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestC64Zeropage {

    private val errors = ErrorReporter()

    @Test
    fun testNames() {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false, C64Target))

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
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, C64Target))
        assertFailsWith<CompilerException> {
            zp.allocate("", DataType.FLOAT, null, errors)
        }
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.DONTUSE, emptyList(), true, false, C64Target))
        assertFailsWith<CompilerException> {
            zp2.allocate("", DataType.FLOAT, null, errors)
        }
        val zp3 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), true, false, C64Target))
        zp3.allocate("", DataType.FLOAT, null, errors)
    }

    @Test
    fun testZpModesWithFloats() {
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, C64Target))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false, C64Target))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false, C64Target))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), false, false, C64Target))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, C64Target))
        C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), true, false, C64Target))
        assertFailsWith<CompilerException> {
            C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), true, false, C64Target))
        }
        assertFailsWith<CompilerException> {
            C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), true, false, C64Target))
        }
    }

    @Test
    fun testZpDontuse() {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.DONTUSE, emptyList(), false, false, C64Target))
        println(zp.free)
        assertEquals(0, zp.availableBytes())
        assertFailsWith<CompilerException> {
            zp.allocate("", DataType.BYTE, null, errors)
        }
    }

    @Test
    fun testFreeSpacesBytes() {
        val zp1 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, C64Target))
        assertEquals(18, zp1.availableBytes())
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), false, false, C64Target))
        assertEquals(85, zp2.availableBytes())
        val zp3 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false, C64Target))
        assertEquals(125, zp3.availableBytes())
        val zp4 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, C64Target))
        assertEquals(238, zp4.availableBytes())
        zp4.allocate("test", DataType.UBYTE, null, errors)
        assertEquals(237, zp4.availableBytes())
        zp4.allocate("test2", DataType.UBYTE, null, errors)
        assertEquals(236, zp4.availableBytes())
    }

    @Test
    fun testFreeSpacesWords() {
        val zp1 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, C64Target))
        assertEquals(6, zp1.availableWords())
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FLOATSAFE, emptyList(), false, false, C64Target))
        assertEquals(38, zp2.availableWords())
        val zp3 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false, C64Target))
        assertEquals(57, zp3.availableWords())
        val zp4 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, C64Target))
        assertEquals(116, zp4.availableWords())
        zp4.allocate("test", DataType.UWORD, null, errors)
        assertEquals(115, zp4.availableWords())
        zp4.allocate("test2", DataType.UWORD, null, errors)
        assertEquals(114, zp4.availableWords())
    }

    @Test
    fun testReservedSpace() {
        val zp1 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, C64Target))
        assertEquals(238, zp1.availableBytes())
        assertTrue(50 in zp1.free)
        assertTrue(100 in zp1.free)
        assertTrue(49 in zp1.free)
        assertTrue(101 in zp1.free)
        assertTrue(200 in zp1.free)
        assertTrue(255 in zp1.free)
        assertTrue(199 in zp1.free)
        val zp2 = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, listOf(50 .. 100, 200..255), false, false, C64Target))
        assertEquals(139, zp2.availableBytes())
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
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, C64Target))
        assertEquals(18, zp.availableBytes())
        assertTrue(zp.hasByteAvailable())
        assertTrue(zp.hasWordAvailable())

        assertFailsWith<ZeropageDepletedError> {
            // in regular zp there aren't 5 sequential bytes free
            zp.allocate("", DataType.FLOAT, null, errors)
        }

        for (i in 0 until zp.availableBytes()) {
            val loc = zp.allocate("", DataType.UBYTE, null, errors)
            assertTrue(loc > 0)
        }
        assertEquals(0, zp.availableBytes())
        assertFalse(zp.hasByteAvailable())
        assertFalse(zp.hasWordAvailable())
        assertFailsWith<ZeropageDepletedError> {
            zp.allocate("", DataType.UBYTE, null, errors)
        }
        assertFailsWith<ZeropageDepletedError> {
            zp.allocate("", DataType.UWORD, null, errors)
        }
    }

    @Test
    fun testFullAllocation() {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, C64Target))
        assertEquals(238, zp.availableBytes())
        assertTrue(zp.hasByteAvailable())
        assertTrue(zp.hasWordAvailable())
        val loc = zp.allocate("", DataType.UWORD, null, errors)
        assertTrue(loc > 3)
        assertFalse(loc in zp.free)
        val num = zp.availableBytes() / 2

        for(i in 0..num-4) {
            zp.allocate("", DataType.UWORD, null, errors)
        }
        assertEquals(6,zp.availableBytes())

        assertFailsWith<ZeropageDepletedError> {
            // can't allocate because no more sequential bytes, only fragmented
            zp.allocate("", DataType.UWORD, null, errors)
        }

        for(i in 0..5) {
            zp.allocate("", DataType.UBYTE, null, errors)
        }

        assertEquals(0, zp.availableBytes())
        assertFalse(zp.hasByteAvailable())
        assertFalse(zp.hasWordAvailable())
        assertFailsWith<ZeropageDepletedError> {
            // no more space
            zp.allocate("", DataType.UBYTE, null, errors)
        }
    }

    @Test
    fun testEfficientAllocation() {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(),  true, false, C64Target))
        assertEquals(18, zp.availableBytes())
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
        assertEquals(0, zp.availableBytes())
    }

    @Test
    fun testReservedLocations() {
        val zp = C64Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false, C64Target))
        assertEquals(zp.SCRATCH_REG, zp.SCRATCH_B1+1, "zp _B1 and _REG must be next to each other to create a word")
    }
}


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCx16Zeropage {
    private val errors = ErrorReporter()

    @Test
    fun testReservedLocations() {
        val zp = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), false, false, Cx16Target))
        assertEquals(zp.SCRATCH_REG, zp.SCRATCH_B1+1, "zp _B1 and _REG must be next to each other to create a word")
    }

    @Test
    fun testFreeSpacesBytes() {
        val zp1 = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, Cx16Target))
        assertEquals(88, zp1.availableBytes())
        val zp2 = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false, Cx16Target))
        assertEquals(175, zp2.availableBytes())
        val zp3 = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, Cx16Target))
        assertEquals(216, zp3.availableBytes())
        zp3.allocate("test", DataType.UBYTE, null, errors)
        assertEquals(215, zp3.availableBytes())
        zp3.allocate("test2", DataType.UBYTE, null, errors)
        assertEquals(214, zp3.availableBytes())
    }

    @Test
    fun testFreeSpacesWords() {
        val zp1 = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, Cx16Target))
        assertEquals(108, zp1.availableWords())
        val zp2 = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.KERNALSAFE, emptyList(), false, false, Cx16Target))
        assertEquals(87, zp2.availableWords())
        val zp3 = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.BASICSAFE, emptyList(), true, false, Cx16Target))
        assertEquals(44, zp3.availableWords())
        zp3.allocate("test", DataType.UWORD, null, errors)
        assertEquals(43, zp3.availableWords())
        zp3.allocate("test2", DataType.UWORD, null, errors)
        assertEquals(42, zp3.availableWords())
    }

    @Test
    fun testReservedSpace() {
        val zp1 = CX16Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, false, Cx16Target))
        assertEquals(216, zp1.availableBytes())
        assertTrue(0x22 in zp1.free)
        assertTrue(0x80 in zp1.free)
        assertTrue(0xff in zp1.free)
        assertFalse(0x02 in zp1.free)
        assertFalse(0x21 in zp1.free)
    }
}
