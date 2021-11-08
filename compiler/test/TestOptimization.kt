package prog8tests

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
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
        result.program.entrypoint shouldBeSameInstanceAs startSub
        withClue("only start sub should remain") {
            startSub.name shouldBe "start"
        }
        withClue("compiler has inserted return in empty subroutines") {
            startSub.statements.single() shouldBe instanceOf<Return>()
        }
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
        result.program.entrypoint shouldBeSameInstanceAs startSub
        startSub.name shouldBe "start"
        emptySub.name shouldBe "empty"
        withClue("compiler has inserted return in empty subroutines") {
            emptySub.statements.single() shouldBe instanceOf<Return>()
        }
    }

    test("testGeneratedConstvalueInheritsProperParentLinkage") {
        val number = NumericLiteralValue(DataType.UBYTE, 11, Position.DUMMY)
        val tc = TypecastExpression(number, DataType.BYTE, false, Position.DUMMY)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        tc.linkParents(ParentSentinel)
        tc.parent shouldNotBe null
        number.parent shouldNotBe null
        tc shouldBeSameInstanceAs number.parent
        val constvalue = tc.constValue(program)!!
        constvalue shouldBe instanceOf<NumericLiteralValue>()
        constvalue.number.toInt() shouldBe 11
        constvalue.type shouldBe DataType.BYTE
        tc shouldBeSameInstanceAs constvalue.parent
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
        mainsub.statements.size shouldBe 10
        val declTest = mainsub.statements[0] as VarDecl
        val declX1 = mainsub.statements[1] as VarDecl
        val initX1 = mainsub.statements[2] as Assignment
        val declX2 = mainsub.statements[3] as VarDecl
        val initX2 = mainsub.statements[4] as Assignment
        val declY1 = mainsub.statements[5] as VarDecl
        val initY1 = mainsub.statements[6] as Assignment
        val declY2 = mainsub.statements[7] as VarDecl
        val initY2 = mainsub.statements[8] as Assignment
        mainsub.statements[9] shouldBe instanceOf<Return>()
        (declTest.value as NumericLiteralValue).number.toDouble() shouldBe 10.0
        declX1.value shouldBe null
        declX2.value shouldBe null
        declY1.value shouldBe null
        declY2.value shouldBe null
        (initX1.value as NumericLiteralValue).type shouldBe DataType.BYTE
        (initX1.value as NumericLiteralValue).number.toDouble() shouldBe 11.0
        (initX2.value as NumericLiteralValue).type shouldBe DataType.BYTE
        (initX2.value as NumericLiteralValue).number.toDouble() shouldBe 11.0
        (initY1.value as NumericLiteralValue).type shouldBe DataType.UBYTE
        (initY1.value as NumericLiteralValue).number.toDouble() shouldBe 11.0
        (initY2.value as NumericLiteralValue).type shouldBe DataType.UBYTE
        (initY2.value as NumericLiteralValue).number.toDouble() shouldBe 11.0
    }
})
