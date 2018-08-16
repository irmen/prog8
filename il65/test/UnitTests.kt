package il65tests

import il65.ast.DataType
import il65.ast.VarDecl
import il65.ast.VarDeclType
import il65.compiler.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestZeropage {
    @Test
    fun testNames() {
        val zp = Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.COMPATIBLE, false))

        assertFailsWith<AssertionError> {
            zp.allocate(VarDecl(VarDeclType.MEMORY, DataType.BYTE, null, "", null))
        }

        zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "", null))
        zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "", null))
        zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "varname", null))
        assertFailsWith<AssertionError> {
            zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "varname", null))
        }
        zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "varname2", null))
    }

    @Test
    fun testZpFloatEnable() {
        val zp = Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, false))
        assertFailsWith<UnsupportedOperationException> {
            zp.allocate(VarDecl(VarDeclType.VAR, DataType.FLOAT, null, "", null))
        }
        val zp2 = Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, true))
        zp2.allocate(VarDecl(VarDeclType.VAR, DataType.FLOAT, null, "", null))
    }

    @Test
    fun testCompatibleAllocation() {
        val zp = Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.COMPATIBLE, true))
        assert(zp.available() == 9)
        assertFailsWith<UnsupportedOperationException> {
            // in regular zp there aren't 5 sequential bytes free
            zp.allocate(VarDecl(VarDeclType.VAR, DataType.FLOAT, null, "", null))
        }
        for (i in 0 until zp.available()) {
            val loc = zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "", null))
            assert(loc > 0)
        }
        assert(zp.available() == 0)
        assertFailsWith<UnsupportedOperationException> {
            zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "", null))
        }
        assertFailsWith<UnsupportedOperationException> {
            zp.allocate(VarDecl(VarDeclType.VAR, DataType.WORD, null, "", null))
        }
    }

    @Test
    fun testFullAllocation() {
        val zp = Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, true))
        assert(zp.available() == 239)
        val loc = zp.allocate(VarDecl(VarDeclType.VAR, DataType.FLOAT, null, "", null))
        assert(loc > 3)
        assert(!zp.free.contains(loc))
        val num = zp.available() / 5
        val rest = zp.available() % 5

        for(i in 0..num-4) {
            zp.allocate(VarDecl(VarDeclType.VAR, DataType.FLOAT, null, "", null))
        }
        assert(zp.available() == 19)

        assertFailsWith<UnsupportedOperationException> {
            // can't allocate because no more sequential bytes, only fragmented
            zp.allocate(VarDecl(VarDeclType.VAR, DataType.FLOAT, null, "", null))
        }

        for(i in 0..13) {
            zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "", null))
        }
        zp.allocate(VarDecl(VarDeclType.VAR, DataType.WORD, null, "", null))
        zp.allocate(VarDecl(VarDeclType.VAR, DataType.WORD, null, "", null))

        assert(zp.available() == 1)
        zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "", null))
        assertFailsWith<UnsupportedOperationException> {
            // no more space
            zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "", null))
        }
    }

    @Test
    fun testEfficientAllocation() {
        //  free = [0x04, 0x05, 0x06, 0x2a, 0x52, 0xf7, 0xf8, 0xf9, 0xfa]
        val zp = Zeropage(CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.COMPATIBLE, true))
        assert(zp.available()==9)
        assert(0x2a == zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "", null)))
        assert(0x52 == zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "", null)))
        assert(0x04 == zp.allocate(VarDecl(VarDeclType.VAR, DataType.WORD, null, "", null)))
        assert(0xf7 == zp.allocate(VarDecl(VarDeclType.VAR, DataType.WORD, null, "", null)))
        assert(0x06 == zp.allocate(VarDecl(VarDeclType.VAR, DataType.BYTE, null, "", null)))
        assert(0xf9 == zp.allocate(VarDecl(VarDeclType.VAR, DataType.WORD, null, "", null)))
        assert(zp.available()==0)
    }
}
