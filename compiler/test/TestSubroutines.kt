package prog8tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.DataType
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.compiler.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.assertFailure
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText
import kotlin.test.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestSubroutines {

    @Test
    fun stringParameterNotYetAllowed_ButShouldPerhapsBe() {
        // note: the *parser* accepts this as it is valid *syntax*,
        // however, it's not (yet) valid for the compiler
        val text = """
            main {
                sub start() {
                }
                
                asmsub asmfunc(str thing @AY) {
                }

                sub func(str thing) {
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target, false, text, errors, false).assertFailure("currently str type in signature is invalid")     // TODO should not be invalid
        assertEquals(0, errors.warnings.size)
        // TODO fix extra error "string var must be initialized with a string literal"
        assertTrue(errors.errors.single().startsWith("Pass-by-reference types (str, array) cannot occur as a parameter type directly."))
    }

    @Test
    fun arrayParameterNotYetAllowed_ButShouldPerhapsBe() {
        // note: the *parser* accepts this as it is valid *syntax*,
        // however, it's not (yet) valid for the compiler
        val text = """
            main {
                sub start() {
                }
                
                asmsub asmfunc(ubyte[] thing @AY) {
                }

                sub func(ubyte[22] thing) {
                }
            }
        """

        val errors = ErrorReporterForTests()
        compileText(C64Target, false, text, errors, false).assertFailure("currently array dt in signature is invalid")     // TODO should not be invalid?
        assertEquals(0, errors.warnings.size)
        assertTrue(errors.errors.single().startsWith("Pass-by-reference types (str, array) cannot occur as a parameter type directly."))
    }

    @Test
    @Disabled("TODO: allow string parameter in signature")          // TODO allow this
    fun stringParameter() {
        val text = """
            main {
                sub start() {
                    str text = "test"
                    
                    asmfunc("text")
                    asmfunc(text)
                    asmfunc($2000)
                    asmfunc(12.345)
                    func("text")
                    func(text)
                    func($2000)
                    func(12.345)
                }
                
                asmsub asmfunc(str thing @AY) {
                }

                sub func(str thing) {
                }
            }
        """
        val result = compileText(C64Target, false, text, writeAssembly = false).assertSuccess()
        val module = result.programAst.toplevelModule
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
    @Disabled("TODO: allow array parameter in signature")           // TODO allow this?
    fun arrayParameter() {
        val text = """
            main {
                sub start() {
                    ubyte[] array = [1,2,3]
                    
                    asmfunc(array)
                    asmfunc([4,5,6])
                    asmfunc($2000)
                    asmfunc(12.345)
                    func(array)
                    func([4,5,6])
                    func($2000)
                    func(12.345)
                }
                
                asmsub asmfunc(ubyte[] thing @AY) {
                }

                sub func(ubyte[22] thing) {
                }
            }
        """

        val result = compileText(C64Target, false, text, writeAssembly = false).assertSuccess()
        val module = result.programAst.toplevelModule
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
