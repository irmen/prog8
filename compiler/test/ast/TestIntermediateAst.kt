package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.ast.PtBinaryExpression
import prog8.code.ast.PtNumber
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.Position

class TestIntermediateAst: FunSpec({

    test("isSame on binaryExpressions") {
        val expr1 = PtBinaryExpression("/", DataType.forDt(BaseDataType.UBYTE), Position.DUMMY)
        expr1.add(PtNumber(BaseDataType.UBYTE, 1.0, Position.DUMMY))
        expr1.add(PtNumber(BaseDataType.UBYTE, 2.0, Position.DUMMY))
        val expr2 = PtBinaryExpression("/", DataType.forDt(BaseDataType.UBYTE), Position.DUMMY)
        expr2.add(PtNumber(BaseDataType.UBYTE, 1.0, Position.DUMMY))
        expr2.add(PtNumber(BaseDataType.UBYTE, 2.0, Position.DUMMY))
        (expr1 isSameAs expr2) shouldBe true
        val expr3 = PtBinaryExpression("/", DataType.forDt(BaseDataType.UBYTE), Position.DUMMY)
        expr3.add(PtNumber(BaseDataType.UBYTE, 2.0, Position.DUMMY))
        expr3.add(PtNumber(BaseDataType.UBYTE, 1.0, Position.DUMMY))
        (expr1 isSameAs expr3) shouldBe false
    }

    test("isSame on binaryExpressions with associative operators") {
        val expr1 = PtBinaryExpression("+", DataType.forDt(BaseDataType.UBYTE), Position.DUMMY)
        expr1.add(PtNumber(BaseDataType.UBYTE, 1.0, Position.DUMMY))
        expr1.add(PtNumber(BaseDataType.UBYTE, 2.0, Position.DUMMY))
        val expr2 = PtBinaryExpression("+", DataType.forDt(BaseDataType.UBYTE), Position.DUMMY)
        expr2.add(PtNumber(BaseDataType.UBYTE, 1.0, Position.DUMMY))
        expr2.add(PtNumber(BaseDataType.UBYTE, 2.0, Position.DUMMY))
        (expr1 isSameAs expr2) shouldBe true
        val expr3 = PtBinaryExpression("+", DataType.forDt(BaseDataType.UBYTE), Position.DUMMY)
        expr3.add(PtNumber(BaseDataType.UBYTE, 2.0, Position.DUMMY))
        expr3.add(PtNumber(BaseDataType.UBYTE, 1.0, Position.DUMMY))
        (expr1 isSameAs expr3) shouldBe true
    }
})