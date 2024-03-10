package prog8tests.ast

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import prog8.code.ast.*
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.target.C64Target
import prog8.compiler.astprocessing.IntermediateAstMaker
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestIntermediateAst: FunSpec({

    test("creation") {
        val text="""
            %import textio
            %import graphics
            main {
                sub start() {
                    bool cc
                    ubyte dd
                    ubyte[] array = [1,2,3]
                    cc = 11 in array
                    dd = sqrt(lsb(dd))
                }
            }
        """
        val target = C64Target()
        val errors = ErrorReporterForTests()
        val result = compileText(target, false, text, writeAssembly = false)!!
        val ast = IntermediateAstMaker(result.compilerAst, errors).transform()
        ast.name shouldBe result.compilerAst.name
        ast.allBlocks().any() shouldBe true
        val entry = ast.entrypoint() ?: fail("no main.start() found")
        entry.children.size shouldBe 7
        entry.name shouldBe "start"
        entry.scopedName shouldBe "main.start"
        val blocks = ast.allBlocks().toList()
        blocks.size shouldBeGreaterThan 1
        blocks[0].name shouldBe "main"
        blocks[0].scopedName shouldBe "main"

        val ccInit = entry.children[3] as PtAssignment
        ccInit.target.identifier?.name shouldBe "main.start.cc"
        (ccInit.value as PtBool).value shouldBe false
        val ddInit = entry.children[4] as PtAssignment
        ddInit.target.identifier?.name shouldBe "main.start.dd"
        (ddInit.value as PtNumber).number shouldBe 0.0

        val ccdecl = entry.children[0] as PtVariable
        ccdecl.name shouldBe "cc"
        ccdecl.scopedName shouldBe "main.start.cc"
        ccdecl.type shouldBe DataType.BOOL
        val dddecl = entry.children[1] as PtVariable
        dddecl.name shouldBe "dd"
        dddecl.scopedName shouldBe "main.start.dd"
        dddecl.type shouldBe DataType.UBYTE

        val arraydecl = entry.children[2] as IPtVariable
        arraydecl.name shouldBe "array"
        arraydecl.type shouldBe DataType.ARRAY_UB

        val ccAssignV = (entry.children[5] as PtAssignment).value
        ccAssignV shouldBe instanceOf<PtContainmentCheck>()
        val ddAssignV = (entry.children[6] as PtAssignment).value
        ddAssignV shouldBe instanceOf<PtFunctionCall>()
    }

    test("isSame on binaryExpressions") {
        val expr1 = PtBinaryExpression("/", DataType.UBYTE, Position.DUMMY)
        expr1.add(PtNumber(DataType.UBYTE, 1.0, Position.DUMMY))
        expr1.add(PtNumber(DataType.UBYTE, 2.0, Position.DUMMY))
        val expr2 = PtBinaryExpression("/", DataType.UBYTE, Position.DUMMY)
        expr2.add(PtNumber(DataType.UBYTE, 1.0, Position.DUMMY))
        expr2.add(PtNumber(DataType.UBYTE, 2.0, Position.DUMMY))
        (expr1 isSameAs expr2) shouldBe true
        val expr3 = PtBinaryExpression("/", DataType.UBYTE, Position.DUMMY)
        expr3.add(PtNumber(DataType.UBYTE, 2.0, Position.DUMMY))
        expr3.add(PtNumber(DataType.UBYTE, 1.0, Position.DUMMY))
        (expr1 isSameAs expr3) shouldBe false
    }

    test("isSame on binaryExpressions with associative operators") {
        val expr1 = PtBinaryExpression("+", DataType.UBYTE, Position.DUMMY)
        expr1.add(PtNumber(DataType.UBYTE, 1.0, Position.DUMMY))
        expr1.add(PtNumber(DataType.UBYTE, 2.0, Position.DUMMY))
        val expr2 = PtBinaryExpression("+", DataType.UBYTE, Position.DUMMY)
        expr2.add(PtNumber(DataType.UBYTE, 1.0, Position.DUMMY))
        expr2.add(PtNumber(DataType.UBYTE, 2.0, Position.DUMMY))
        (expr1 isSameAs expr2) shouldBe true
        val expr3 = PtBinaryExpression("+", DataType.UBYTE, Position.DUMMY)
        expr3.add(PtNumber(DataType.UBYTE, 2.0, Position.DUMMY))
        expr3.add(PtNumber(DataType.UBYTE, 1.0, Position.DUMMY))
        (expr1 isSameAs expr3) shouldBe true
    }
})