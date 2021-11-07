package prog8tests

import io.kotest.core.spec.style.FunSpec
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.ParentSentinel
import prog8.ast.base.Position
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.TypecastExpression
import prog8.ast.statements.*
import prog8.compiler.target.C64Target
import prog8tests.helpers.DummyFunctions
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText
import kotlin.test.*

class TestOptimization: FunSpec({
    test("testRemoveEmptySubroutineExceptStart") {
        val sourcecode = """
            main {
                sub start() {
                }
                sub empty() {
                    ; going to be removed
                }
            }
        """
        val result = compileText(C64Target, true, sourcecode).assertSuccess()
        val toplevelModule = result.program.toplevelModule
        val mainBlock = toplevelModule.statements.single() as Block
        val startSub = mainBlock.statements.single() as Subroutine
        assertSame(result.program.entrypoint, startSub)
        assertEquals("start", startSub.name, "only start sub should remain")
        assertTrue(startSub.statements.single() is Return, "compiler has inserted return in empty subroutines")
    }

    test("testDontRemoveEmptySubroutineIfItsReferenced") {
        val sourcecode = """
            main {
                sub start() {
                    uword xx = &empty
                    xx++
                }
                sub empty() {
                    ; should not be removed
                }
            }
        """
        val result = compileText(C64Target, true, sourcecode).assertSuccess()
        val toplevelModule = result.program.toplevelModule
        val mainBlock = toplevelModule.statements.single() as Block
        val startSub = mainBlock.statements[0] as Subroutine
        val emptySub = mainBlock.statements[1] as Subroutine
        assertSame(result.program.entrypoint, startSub)
        assertEquals("start", startSub.name)
        assertEquals("empty", emptySub.name)
        assertTrue(emptySub.statements.single() is Return, "compiler has inserted return in empty subroutines")
    }

    test("testGeneratedConstvalueInheritsProperParentLinkage") {
        val number = NumericLiteralValue(DataType.UBYTE, 11, Position.DUMMY)
        val tc = TypecastExpression(number, DataType.BYTE, false, Position.DUMMY)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        tc.linkParents(ParentSentinel)
        assertNotNull(tc.parent)
        assertNotNull(number.parent)
        assertSame(tc, number.parent)
        val constvalue = tc.constValue(program)!!
        assertIs<NumericLiteralValue>(constvalue)
        assertEquals(11, constvalue.number.toInt())
        assertEquals(DataType.BYTE, constvalue.type)
        assertSame(tc, constvalue.parent)
    }

    test("testConstantFoldedAndSilentlyTypecastedForInitializerValues") {
        val sourcecode = """
            main {
                sub start() {
                    const ubyte TEST = 10
                    byte x1 = TEST as byte + 1
                    byte x2 = 1 + TEST as byte
                    ubyte y1 = TEST + 1 as byte
                    ubyte y2 = 1 as byte + TEST
                }
            }
        """
        val result = compileText(C64Target, true, sourcecode).assertSuccess()
        val mainsub = result.program.entrypoint
        assertEquals(10, mainsub.statements.size)
        val declTest = mainsub.statements[0] as VarDecl
        val declX1 = mainsub.statements[1] as VarDecl
        val initX1 = mainsub.statements[2] as Assignment
        val declX2 = mainsub.statements[3] as VarDecl
        val initX2 = mainsub.statements[4] as Assignment
        val declY1 = mainsub.statements[5] as VarDecl
        val initY1 = mainsub.statements[6] as Assignment
        val declY2 = mainsub.statements[7] as VarDecl
        val initY2 = mainsub.statements[8] as Assignment
        assertIs<Return>(mainsub.statements[9])
        assertEquals(10.0, (declTest.value as NumericLiteralValue).number.toDouble())
        assertNull(declX1.value)
        assertNull(declX2.value)
        assertNull(declY1.value)
        assertNull(declY2.value)
        assertEquals(DataType.BYTE, (initX1.value as NumericLiteralValue).type)
        assertEquals(11.0, (initX1.value as NumericLiteralValue).number.toDouble())
        assertEquals(DataType.BYTE, (initX2.value as NumericLiteralValue).type)
        assertEquals(11.0, (initX2.value as NumericLiteralValue).number.toDouble())
        assertEquals(DataType.UBYTE, (initY1.value as NumericLiteralValue).type)
        assertEquals(11.0, (initY1.value as NumericLiteralValue).number.toDouble())
        assertEquals(DataType.UBYTE, (initY2.value as NumericLiteralValue).type)
        assertEquals(11.0, (initY2.value as NumericLiteralValue).number.toDouble())
    }
})
