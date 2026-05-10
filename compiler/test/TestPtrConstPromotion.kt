package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import prog8.ast.statements.VarDecl
import prog8.ast.statements.VarDeclType
import prog8.code.target.VMTarget
import prog8tests.helpers.compileText

class TestPtrConstPromotion : FunSpec({
    val outputDir = tempdir().toPath()
    val vmTarget = VMTarget()

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
        val result = compileText(vmTarget, true, src, outputDir)
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
        val result = compileText(vmTarget, true, src, outputDir)
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
        val result = compileText(vmTarget, true, src, outputDir)
        val mainBlock = result!!.compilerAst.allBlocks.first { it.name == "main" }
        val startSub = mainBlock.subScope("start")!!
        val ptrVar = startSub.lookup(listOf("ptr"))!!
        (ptrVar as VarDecl).type shouldBe VarDeclType.VAR
    }
})
