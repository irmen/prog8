package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.ast.statements.VarDecl
import prog8.ast.statements.VarDeclType
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestPointerConstPromotion : FunSpec({
    val outputDir = tempdir().toPath()

    test("pointer variable with 2 dereferences is promoted to const") {
        val src = """
            %import textio
            main {
                sub start() {
                    ^^ubyte ptr = 9999
                    ubyte a = ptr^^
                    ubyte b = ptr^^
                    txt.print_ub(a)
                    txt.print_ub(b)
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir)
        val mainBlock = result!!.compilerAst.allBlocks.first { it.name == "main" }
        val startSub = mainBlock.subScope("start")!!
        val ptrVar = startSub.lookup(listOf("ptr"))!!
        (ptrVar as VarDecl).type shouldBe VarDeclType.CONST
    }

    test("pointer variable with memory is notpromoted to const") {
        val src = """
            %import textio
            main {
                sub start() {
                    ^^ubyte ptr = memory("slab", 10, 1)
                    ubyte a = ptr^^
                    txt.print_ub(a)
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir)
        val mainBlock = result!!.compilerAst.allBlocks.first { it.name == "main" }
        val startSub = mainBlock.subScope("start")!!
        val ptrVar = startSub.lookup(listOf("ptr"))!!
        (ptrVar as VarDecl).type shouldBe VarDeclType.VAR
    }

    test("pointer variable with 3 dereferences is NOT promoted to const") {
        val src = """
            %import textio
            main {
                sub start() {
                    ^^ubyte ptr = 9999
                    ubyte a = ptr^^
                    ubyte b = ptr^^
                    ubyte c = ptr^^
                    txt.print_ub(a)
                    txt.print_ub(b)
                    txt.print_ub(c)
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir)
        val mainBlock = result!!.compilerAst.allBlocks.first { it.name == "main" }
        val startSub = mainBlock.subScope("start")!!
        val ptrVar = startSub.lookup(listOf("ptr"))!!
        (ptrVar as VarDecl).type shouldBe VarDeclType.VAR
    }
    
    test("explicit const pointer to >1 byte type gives error") {
        val src = """
            main {
                sub start() {
                    const ^^uword ptr1b = 4444
                    cx16.r0 = ptr1b
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors = errors)
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "pointer variables with data type size > 1 cannot be const"
    }

    test("explicit const pointer to >1 byte type (memory) gives error") {
        val src = """
            main {
                sub start() {
                    const ^^uword mem2 = memory("mem2", 10, 0)
                    cx16.r0 = mem2
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors = errors)
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "pointer variables with data type size > 1 cannot be const"
    }
})
