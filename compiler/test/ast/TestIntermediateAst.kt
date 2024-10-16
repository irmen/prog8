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