package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Assignment
import prog8.ast.statements.FunctionCallStatement
import prog8.code.core.BaseDataType
import prog8.code.core.BuiltinFunctions
import prog8.code.core.RegisterOrPair
import prog8.code.core.isNumeric
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText

class TestBuiltinFunctions: FunSpec({

    val outputDir = tempdir().toPath()
    
    test("pure func with fixed type") {
        val func = BuiltinFunctions.getValue("sgn")
        func.parameters.size shouldBe 1
        func.parameters[0].name shouldBe "value"
        func.parameters[0].possibleDatatypes. shouldForAll { it.isNumeric }
        func.pure shouldBe true
        func.returnType shouldBe BaseDataType.BYTE

        val conv = func.callConvention(listOf(BaseDataType.UBYTE))
        conv.params.size shouldBe 1
        conv.params[0].dt shouldBe BaseDataType.UBYTE
        conv.params[0].reg shouldBe RegisterOrPair.A
        conv.params[0].variable shouldBe false
        conv.returns.dt shouldBe BaseDataType.BYTE
        conv.returns.reg shouldBe RegisterOrPair.A
    }

    test("not-pure func with varying result value type") {
        val func = BuiltinFunctions.getValue("cmp")
        func.parameters.size shouldBe 2
        func.pure shouldBe false
        func.returnType shouldBe null

        val conv = func.callConvention(listOf(BaseDataType.UWORD, BaseDataType.UWORD))
        conv.params.size shouldBe 2
        conv.returns.dt shouldBe null
        conv.returns.reg shouldBe null
    }

    test("func without return type") {
        val func = BuiltinFunctions.getValue("poke")
        func.parameters.size shouldBe 2
        func.parameters[0].name shouldBe "address"
        func.parameters[0].possibleDatatypes shouldBe arrayOf(BaseDataType.UWORD)
        func.parameters[1].name shouldBe "value"
        func.parameters[1].possibleDatatypes shouldBe arrayOf(BaseDataType.UBYTE)
        func.pure shouldBe false
        func.returnType shouldBe null
    }

    test("certain builtin functions should be compile time evaluated") {
        val src="""
main {
    sub start() {
        uword[] array = [1,2,3]
        str name = "hello"
        cx16.r0L = len(array)
        cx16.r1L = len(name)
        cx16.r2L = sizeof(array)
        cx16.r4 = mkword(200,100)
        test(sizeof(array))
    }
    sub test(uword value) {
        value++
    }
}"""
        val result = compileText(Cx16Target(), false, src, outputDir, writeAssembly = false)
        val statements = result!!.compilerAst.entrypoint.statements
        statements.size shouldBe 7
        val a1 = statements[2] as Assignment
        val a2 = statements[3] as Assignment
        val a3 = statements[4] as Assignment
        val a4 = statements[5] as Assignment
        val a5 = statements[6] as FunctionCallStatement
        (a1.value as NumericLiteral).number shouldBe 3.0
        (a2.value as NumericLiteral).number shouldBe 5.0
        (a3.value as NumericLiteral).number shouldBe 6.0
        (a4.value as NumericLiteral).number shouldBe 200*256+100
        (a5.args[0] as NumericLiteral).number shouldBe 6.0
    }

    test("divmod target args should be treated as variables that are written") {
        val src="""
main {
    ubyte c
    ubyte l

    sub start() {
        divmod(99, 10, c, l)
    }
}"""

        compileText(Cx16Target(), true, src, outputDir, writeAssembly = true) shouldNotBe null
    }
})

