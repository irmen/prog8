package prog8tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.DataType
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.assertFailure
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText
import kotlin.test.*


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
        val module = result.program.toplevelModule
        val mainBlock = module.statements.single() as Block
        val asmfunc = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="asmfunc"}
        val func = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="func"}
        assertTrue(asmfunc.isAsmSubroutine)
        assertEquals(DataType.STR, asmfunc.parameters.single().type)
        assertTrue(asmfunc.statements.isEmpty())
        assertFalse(func.isAsmSubroutine)
        assertEquals(DataType.STR, func.parameters.single().type)
        assertEquals(4, func.statements.size)
        val paramvar = func.statements[0] as VarDecl
        assertEquals("thing", paramvar.name)
        assertEquals(DataType.STR, paramvar.datatype)
        val assign = func.statements[2] as Assignment
        assertEquals(listOf("t2"), assign.target.identifier!!.nameInSource)
        assertTrue(assign.value is TypecastExpression, "str param in function body should not be transformed by normal compiler steps")
        assertEquals(DataType.UWORD, (assign.value as TypecastExpression).type)
        val call = func.statements[3] as FunctionCallStatement
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
        val module = result.program.toplevelModule
        val mainBlock = module.statements.single() as Block
        val asmfunc = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="asmfunc"}
        val func = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="func"}
        assertTrue(asmfunc.isAsmSubroutine)
        assertEquals(DataType.STR, asmfunc.parameters.single().type)
        assertTrue(asmfunc.statements.single() is Return)
        assertFalse(func.isAsmSubroutine)
        assertEquals(DataType.UWORD, func.parameters.single().type, "asmgen should have changed str to uword type")
        assertTrue(asmfunc.statements.last() is Return)

        assertEquals(5, func.statements.size)
        assertTrue(func.statements[4] is Return)
        val paramvar = func.statements[0] as VarDecl
        assertEquals("thing", paramvar.name)
        assertEquals(DataType.UWORD, paramvar.datatype, "pre-asmgen should have changed str to uword type")
        val assign = func.statements[2] as Assignment
        assertEquals(listOf("t2"), assign.target.identifier!!.nameInSource)
        assertTrue(assign.value is IdentifierReference, "str param in function body should be treated as plain uword before asmgen")
        assertEquals("thing", (assign.value as IdentifierReference).nameInSource.single())
        val call = func.statements[3] as FunctionCallStatement
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
        val module = result.program.toplevelModule
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
    fun testUwordParameterAndNormalVarIndexedAsArrayWorkAsDirectMemoryRead() {
        val text="""
            main {
              sub thing(uword rr) {
                ubyte xx = rr[1]    ; should still work as var initializer that will be rewritten
                ubyte yy
                yy = rr[2]
                uword other
                ubyte zz = other[3]
              }
            
              sub start() {
                ubyte[] array=[1,2,3]
                thing(array)
              }
            }
        """

        val result = compileText(C64Target, false, text, writeAssembly = true).assertSuccess()
        val module = result.program.toplevelModule
        val block = module.statements.single() as Block
        val thing = block.statements.filterIsInstance<Subroutine>().single {it.name=="thing"}
        assertEquals("main", block.name)
        assertEquals(10, thing.statements.size, "rr, xx, xx assign, yy, yy assign, other, other assign 0, zz, zz assign, return")
        val xx = thing.statements[1] as VarDecl
        assertNull(xx.value, "vardecl init values must have been moved to separate assignments")
        val assignXX = thing.statements[2] as Assignment
        val assignYY = thing.statements[4] as Assignment
        val assignZZ = thing.statements[8] as Assignment
        assertEquals(listOf("xx"), assignXX.target.identifier!!.nameInSource)
        assertEquals(listOf("yy"), assignYY.target.identifier!!.nameInSource)
        assertEquals(listOf("zz"), assignZZ.target.identifier!!.nameInSource)
        val valueXXexpr = (assignXX.value as DirectMemoryRead).addressExpression as BinaryExpression
        val valueYYexpr = (assignYY.value as DirectMemoryRead).addressExpression as BinaryExpression
        val valueZZexpr = (assignZZ.value as DirectMemoryRead).addressExpression as BinaryExpression
        assertEquals(listOf("rr"), (valueXXexpr.left as IdentifierReference).nameInSource)
        assertEquals(listOf("rr"), (valueYYexpr.left as IdentifierReference).nameInSource)
        assertEquals(listOf("other"), (valueZZexpr.left as IdentifierReference).nameInSource)
        assertEquals(1, (valueXXexpr.right as NumericLiteralValue).number.toInt())
        assertEquals(2, (valueYYexpr.right as NumericLiteralValue).number.toInt())
        assertEquals(3, (valueZZexpr.right as NumericLiteralValue).number.toInt())
    }

    @Test
    fun testUwordParameterAndNormalVarIndexedAsArrayWorkAsMemoryWrite() {
        val text="""
            main {
              sub thing(uword rr) {
                rr[10] = 42
              }
            
              sub start() {
                ubyte[] array=[1,2,3]
                thing(array)
              }
            }
        """

        val result = compileText(C64Target, false, text, writeAssembly = true).assertSuccess()
        val module = result.program.toplevelModule
        val block = module.statements.single() as Block
        val thing = block.statements.filterIsInstance<Subroutine>().single {it.name=="thing"}
        assertEquals("main", block.name)
        assertEquals(3, thing.statements.size, "rr, rr assign, return void")
        val assignRR = thing.statements[1] as Assignment
        assertEquals(42, (assignRR.value as NumericLiteralValue).number.toInt())
        val memwrite = assignRR.target.memoryAddress
        assertNotNull(memwrite, "memwrite to set byte array value")
        val addressExpr = memwrite.addressExpression as BinaryExpression
        assertEquals(listOf("rr"), (addressExpr.left as IdentifierReference).nameInSource)
        assertEquals("+", addressExpr.operator)
        assertEquals(10, (addressExpr.right as NumericLiteralValue).number.toInt())
    }
}
