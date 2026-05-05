package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.expressions.DirectMemoryRead
import prog8.ast.expressions.Expression
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.walk.AstModification
import prog8.ast.walk.AstWalker
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestPointerFolding : FunSpec({
    val outputDir = tempdir().toPath()

    test("pointer arithmetic constant folding") {
        val src = """
            %import textio
            main {
                const ^^uword bptr = ${'$'}2000
                const ^^ubyte byteptr = ${'$'}2000
                const ^^float fptr = ${'$'}4000
                const ^^long lptr = ${'$'}3000
                
                sub start() {
                    uword p1
                    uword p2
                    uword p3
                    uword p4
                    uword p5
                    uword p6
                    uword p7
                    uword b1
                    ubyte b2
                    p1 = bptr
                    p2 = bptr + 4
                    p3 = bptr - 2
                    p4 = 10 + byteptr
                    p5 = fptr + 2
                    p6 = lptr + 1
                    p7 = byteptr + sizeof(float)
                    b1 = bptr[${'$'}1000]
                    b2 = byteptr[${'$'}1000]
                    
                    txt.print_uw(p1)
                    txt.print_uw(p2)
                    txt.print_uw(p3)
                    txt.print_uw(p4)
                    txt.print_uw(p5)
                    txt.print_uw(p6)
                    txt.print_uw(p7)
                    txt.print_uw(b1)
                    txt.print_ub(b2)
                }
            }
        """.trimIndent()
        
        val errors = ErrorReporterForTests()
        val result = compileText(VMTarget(), true, src, outputDir, errors = errors)
        withClue("Compiler errors: " + errors.errors.joinToString("\n")) {
            result shouldNotBe null
        }
        val program = result!!.compilerAst
        
        val allCalls = mutableListOf<IFunctionCall>()
        val allDirectReads = mutableListOf<DirectMemoryRead>()
        val walker = object: AstWalker() {
            override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<AstModification> {
                allCalls.add(functionCallExpr)
                return noModifications
            }
            override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<AstModification> {
                allCalls.add(functionCallStatement)
                return noModifications
            }
            override fun after(memread: DirectMemoryRead, parent: Node): Iterable<AstModification> {
                allDirectReads.add(memread)
                return noModifications
            }
        }
        walker.visit(program)

        fun findPrintArgument(index: Int): Expression {
            val calls = allCalls.filter { it.target.nameInSource.last() == "print_uw" || it.target.nameInSource.last() == "print_ub" }
            return calls[index].args[0]
        }

        // p1 = bptr -> folded to $2000 (8192)
        val p1Value = findPrintArgument(0)
        p1Value shouldBe instanceOf<NumericLiteral>()
        (p1Value as NumericLiteral).number shouldBe 8192.0

        // p2 = bptr + 4 -> folded to $2000 + 4*2 = $2008 (8200)
        val p2Value = findPrintArgument(1)
        p2Value shouldBe instanceOf<NumericLiteral>()
        (p2Value as NumericLiteral).number shouldBe 8200.0

        // p3 = bptr - 2 -> folded to $2000 - 2*2 = $1ffc (8188)
        val p3Value = findPrintArgument(2)
        p3Value shouldBe instanceOf<NumericLiteral>()
        (p3Value as NumericLiteral).number shouldBe 8188.0

        // p4 = 10 + byteptr -> folded to $2000 + 10*1 = $200a (8202)
        val p4Value = findPrintArgument(3)
        p4Value shouldBe instanceOf<NumericLiteral>()
        (p4Value as NumericLiteral).number shouldBe 8202.0

        // p5 = fptr + 2 -> folded to $4000 + 2*8 = $4010 (16400) on VMTarget
        val p5Value = findPrintArgument(4)
        p5Value shouldBe instanceOf<NumericLiteral>()
        (p5Value as NumericLiteral).number shouldBe 16400.0

        // p6 = lptr + 1 -> folded to $3000 + 1*4 = $3004 (12292)
        val p6Value = findPrintArgument(5)
        p6Value shouldBe instanceOf<NumericLiteral>()
        (p6Value as NumericLiteral).number shouldBe 12292.0

        // p7 = byteptr + sizeof(float) -> folded to $2000 + 8 = $2008 (8200)
        val p7Value = findPrintArgument(6)
        p7Value shouldBe instanceOf<NumericLiteral>()
        (p7Value as NumericLiteral).number shouldBe 8200.0
        
        // b1 = bptr[$1000] -> folded to peekw at $2000 + $1000*2 = $4000 (16384)
        val b1Call = allCalls.find { it.target.nameInSource.last() == "peekw" }
        b1Call shouldNotBe null
        val b1Addr = b1Call!!.args[0]
        b1Addr shouldBe instanceOf<NumericLiteral>()
        (b1Addr as NumericLiteral).number shouldBe 16384.0

        // b2 = byteptr[$1000] -> folded to DirectMemoryRead(peek) at $2000 + $1000*1 = $3000 (12288)
        val b2Read = allDirectReads.firstOrNull()
        b2Read shouldNotBe null
        val b2Addr = b2Read!!.addressExpression
        b2Addr shouldBe instanceOf<NumericLiteral>()
        (b2Addr as NumericLiteral).number shouldBe 12288.0
    }
})
