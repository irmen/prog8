package prog8tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.DataType
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.TypecastExpression
import prog8.ast.statements.*
import prog8.compiler.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.assertFailure
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestSubroutines {

    @Test
    fun stringParameter() {
        val text = """
            main {
                sub start() {
                    str text = "test"
                    
                    asmfunc("text")
                    asmfunc(text)
                    asmfunc($2000)
                    func("text")
                    func(text)
                    func($2000)
                }
                
                asmsub asmfunc(str thing @AY) {
                }

                sub func(str thing) {
                    uword t2 = thing as uword
                    asmfunc(thing)
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
        assertEquals(3, func.statements.size)
        val paramvar = func.statements[0] as VarDecl
        assertEquals("thing", paramvar.name)
        assertEquals(DataType.STR, paramvar.datatype)
        val t2var = func.statements[1] as VarDecl
        assertEquals("t2", t2var.name)
        assertTrue(t2var.value is TypecastExpression, "str param in function body should not be transformed by normal compiler steps")
        assertEquals(DataType.UWORD, (t2var.value as TypecastExpression).type)
        val call = func.statements[2] as FunctionCallStatement
        assertEquals("asmfunc", call.target.nameInSource.single())
        assertTrue(call.args.single() is IdentifierReference, "str param in function body should not be transformed by normal compiler steps")
        assertEquals("thing", (call.args.single() as IdentifierReference).nameInSource.single())
    }

    @Test
    fun stringParameterAsmGen() {
        val text = """
            main {
                sub start() {
                    str text = "test"
                    
                    asmfunc("text")
                    asmfunc(text)
                    asmfunc($2000)
                    func("text")
                    func(text)
                    func($2000)
                }
                
                asmsub asmfunc(str thing @AY) {
                }

                sub func(str thing) {
                    uword t2 = thing as uword
                    asmfunc(thing)
                }
            }
        """
        val result = compileText(C64Target, false, text, writeAssembly = true).assertSuccess()
        val module = result.programAst.toplevelModule
        val mainBlock = module.statements.single() as Block
        val asmfunc = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="asmfunc"}
        val func = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="func"}
        assertTrue(asmfunc.isAsmSubroutine)
        assertEquals(DataType.STR, asmfunc.parameters.single().type)
        assertTrue(asmfunc.statements.single() is Return)
        assertFalse(func.isAsmSubroutine)
        assertEquals(DataType.UWORD, func.parameters.single().type, "asmgen should have changed str to uword type")
        assertTrue(asmfunc.statements.last() is Return)

        assertEquals(4, func.statements.size)
        assertTrue(func.statements[3] is Return)
        val paramvar = func.statements[0] as VarDecl
        assertEquals("thing", paramvar.name)
        assertEquals(DataType.UWORD, paramvar.datatype, "pre-asmgen should have changed str to uword type")
        val t2var = func.statements[1] as VarDecl
        assertEquals("t2", t2var.name)
        assertTrue(t2var.value is IdentifierReference, "str param in function body should be treated as plain uword before asmgen")
        assertEquals("thing", (t2var.value as IdentifierReference).nameInSource.single())
        val call = func.statements[2] as FunctionCallStatement
        assertEquals("asmfunc", call.target.nameInSource.single())
        assertTrue(call.args.single() is IdentifierReference, "str param in function body should be treated as plain uword and not been transformed")
        assertEquals("thing", (call.args.single() as IdentifierReference).nameInSource.single())
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
        assertContains(errors.errors.single(), ".p8:9:16: Non-string pass-by-reference types cannot occur as a parameter type directly")
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

    @Test
    fun testAnonScopeVarsMovedIntoSubroutineScope() {
        val src = """
            main {
                sub start() {
                    repeat {
                        ubyte xx = 99
                        xx++
                    }
                }
            }
        """

        val result = compileText(C64Target, false, src, writeAssembly = false).assertSuccess()
        val module = result.programAst.toplevelModule
        val mainBlock = module.statements.single() as Block
        val start = mainBlock.statements.single() as Subroutine
        val repeatbody = start.statements.filterIsInstance<RepeatLoop>().single().body
        assertFalse(mainBlock.statements.any { it is VarDecl }, "no vars moved to main block")
        val subroutineVars = start.statements.filterIsInstance<VarDecl>()
        assertEquals(1, subroutineVars.size, "var from repeat anonscope must be moved up to subroutine")
        assertEquals("xx", subroutineVars[0].name)
        assertFalse(repeatbody.statements.any { it is VarDecl }, "var should have been removed from repeat anonscope")
        val initassign = repeatbody.statements[0] as? Assignment
        assertEquals(listOf("xx"), initassign?.target?.identifier?.nameInSource, "vardecl in repeat should be replaced by init assignment")
        assertEquals(99, (initassign?.value as? NumericLiteralValue)?.number?.toInt(), "vardecl in repeat should be replaced by init assignment")
        assertTrue(repeatbody.statements[1] is PostIncrDecr)
    }
}
