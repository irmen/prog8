package prog8tests.optimizer

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Assignment
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText

class TestExpressionSimplifier : FunSpec({
    val outputDir = tempdir().toPath()

    test("long & -1 is correctly simplified") {
        val text = """
            main {
                sub start() {
                    long @shared x = 1000
                    long @shared y = x & -1
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, text, outputDir, writeAssembly = false)!!
        val s = result.compilerAst.entrypoint.statements
        val expr = (s[3] as Assignment).value as BinaryExpression
        expr.operator shouldBe "&"
        (expr.left as IdentifierReference).nameInSource shouldBe listOf("x")
        (expr.right as NumericLiteral).number shouldBe -1.0
    }
    
    test("long & 255 is correctly handled") {
        val text = """
            main {
                sub start() {
                    long @shared x = 1000
                    long @shared y = x & 255
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, text, outputDir, writeAssembly = false)!!
        val s = result.compilerAst.entrypoint.statements
        val expr = (s[3] as Assignment).value as BinaryExpression
        expr.operator shouldBe "&"
        (expr.left as IdentifierReference).nameInSource shouldBe listOf("x")
        (expr.right as NumericLiteral).number shouldBe 255.0
    }
})
