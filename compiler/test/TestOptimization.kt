package prog8tests

import io.kotest.assertions.fail
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
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.BeforeAsmGenerationAstChanger
import prog8.compiler.target.C64Target
import prog8.compiler.target.c64.C64MachineDefinition
import prog8.compiler.target.cpu6502.codegen.AsmGen
import prog8.compilerinterface.*
import prog8tests.ast.helpers.outputDir
import prog8tests.helpers.DummyFunctions
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder
import prog8tests.helpers.ErrorReporterForTests
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

    test("intermediate assignment steps have correct types for codegen phase (BeforeAsmGenerationAstChanger)") {
        val src = """
            main {
                sub start() {
                    ubyte bb
                    uword ww
                    bb = not bb or not ww       ; expression combining ubyte and uword
                }
            }
        """
        val result = compileText(C64Target, false, src, writeAssembly = false).assertSuccess()

        // bb = (( not bb as uword)  or  not ww)
        val bbAssign = result.program.entrypoint.statements.last() as Assignment
        val expr = bbAssign.value as BinaryExpression
        expr.operator shouldBe "or"
        expr.left shouldBe instanceOf<TypecastExpression>() // casted to word
        expr.right shouldBe instanceOf<PrefixExpression>()
        expr.left.inferType(result.program).getOrElse { fail("dt") } shouldBe DataType.UWORD
        expr.right.inferType(result.program).getOrElse { fail("dt") } shouldBe DataType.UWORD
        expr.inferType(result.program).getOrElse { fail("dt") } shouldBe DataType.UBYTE

        val options = CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.DONTUSE, emptyList(), false, true, C64Target)
        val changer = BeforeAsmGenerationAstChanger(result.program,
            options,
            ErrorReporterForTests()
        )

        changer.visit(result.program)
        while(changer.applyModifications()>0) {
            changer.visit(result.program)
        }

        // assignment is now split into:
        //     bb =  not bb
        //     bb = (bb or  not ww)

        val assigns = result.program.entrypoint.statements.filterIsInstance<Assignment>()
        val bbAssigns = assigns.filter { it.value !is NumericLiteralValue }
        bbAssigns.size shouldBe 2
        println(bbAssigns[0])
        println(bbAssigns[1])

        bbAssigns[0].target.identifier!!.nameInSource shouldBe listOf("bb")
        bbAssigns[0].value shouldBe instanceOf<PrefixExpression>()
        (bbAssigns[0].value as PrefixExpression).operator shouldBe "not"
        (bbAssigns[0].value as PrefixExpression).expression shouldBe IdentifierReference(listOf("bb"), Position.DUMMY)
        bbAssigns[0].value.inferType(result.program).getOrElse { fail("dt") } shouldBe DataType.UBYTE

        bbAssigns[1].target.identifier!!.nameInSource shouldBe listOf("bb")
        val bbAssigns1expr = bbAssigns[1].value as BinaryExpression
        bbAssigns1expr.operator shouldBe "or"
        bbAssigns1expr.left shouldBe IdentifierReference(listOf("bb"), Position.DUMMY)
        bbAssigns1expr.right shouldBe instanceOf<PrefixExpression>()
        (bbAssigns1expr.right as PrefixExpression).operator shouldBe "not"
        (bbAssigns1expr.right as PrefixExpression).expression shouldBe IdentifierReference(listOf("ww"), Position.DUMMY)
        bbAssigns1expr.inferType(result.program).getOrElse { fail("dt") } shouldBe DataType.UBYTE

        val zp = C64MachineDefinition.C64Zeropage(options)
        options.compTarget.machine.zeropage=zp
        val asmgen = AsmGen(result.program, ErrorReporterForTests(), zp, options, C64Target, outputDir)
        val asm = asmgen.compileToAssembly()
        asm.valid shouldBe true
    }
})
