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

    test("zp pointer variable with a dereference is NOT promoted to const") {
        val src = """
            main {
                sub start() {
                    ^^ubyte @zp ptr = 9999
                    ubyte @shared a = ptr^^
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir)
        val mainBlock = result!!.compilerAst.allBlocks.first { it.name == "main" }
        val startSub = mainBlock.subScope("start")!!
        val ptrVar = startSub.lookup(listOf("ptr"))!!
        (ptrVar as VarDecl).type shouldBe VarDeclType.VAR
    }

    test("nonzp pointer variable with a dereference is promoted to const") {
        val src = """
            main {
                sub start() {
                    ^^ubyte @nozp ptr = 9999
                    ubyte @shared a = ptr^^
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir)
        val mainBlock = result!!.compilerAst.allBlocks.first { it.name == "main" }
        val startSub = mainBlock.subScope("start")!!
        val ptrVar = startSub.lookup(listOf("ptr"))!!
        (ptrVar as VarDecl).type shouldBe VarDeclType.CONST
    }

    test("pointer variable with memory is not promoted to const") {
        val src = """
            main {
                sub start() {
                    ^^ubyte ptr = memory("slab", 10, 1)
                    ubyte @shared a = ptr^^
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
            main {
                sub start() {
                    ^^ubyte ptr = 9999
                    ubyte @shared a = ptr^^
                    ubyte @shared b = ptr^^
                    ubyte @shared c = ptr^^
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
