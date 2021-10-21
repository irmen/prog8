package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.DataType
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.parser.Prog8Parser.parseModule
import prog8.parser.SourceCode
import kotlin.test.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestSubroutines {

    @Test
    fun stringParameterAcceptedInParser() {
        // note: the *parser* should accept this as it is valid *syntax*,
        // however, the compiler itself may or may not complain about it later.
        val text = """
            main {
                asmsub asmfunc(str thing @AY) {
                }

                sub func(str thing) {
                }
            }
        """
        val src = SourceCode.Text(text)
        val module = parseModule(src)
        val mainBlock = module.statements.single() as Block
        val asmfunc = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="asmfunc"}
        val func = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="func"}
        assertTrue(asmfunc.isAsmSubroutine)
        assertEquals(DataType.STR, asmfunc.parameters.single().type)
        assertTrue(asmfunc.statements.isEmpty())
        assertFalse(func.isAsmSubroutine)
        assertEquals(DataType.STR, func.parameters.single().type)
        assertTrue(func.statements.isEmpty())
    }

    @Test
    fun arrayParameterAcceptedInParser() {
        // note: the *parser* should accept this as it is valid *syntax*,
        // however, the compiler itself may or may not complain about it later.
        val text = """
            main {
                asmsub asmfunc(ubyte[] thing @AY) {
                }

                sub func(ubyte[22] thing) {
                }
            }
        """
        val src = SourceCode.Text(text)
        val module = parseModule(src)
        val mainBlock = module.statements.single() as Block
        val asmfunc = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="asmfunc"}
        val func = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="func"}
        assertTrue(asmfunc.isAsmSubroutine)
        assertEquals(DataType.ARRAY_UB, asmfunc.parameters.single().type)
        assertTrue(asmfunc.statements.isEmpty())
        assertFalse(func.isAsmSubroutine)
        assertEquals(DataType.ARRAY_UB, func.parameters.single().type)
        assertTrue(func.statements.isEmpty())
    }
}
