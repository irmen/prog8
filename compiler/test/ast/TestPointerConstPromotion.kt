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

    test("pointer constant promotion scenarios") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                sub start() {
                    ^^uword ptr1 = 4444         ; size 2 > 1, no promotion
                    ^^ubyte ptr2 = 5555         ; size 1, promotion
                    uword mem1 = memory("mem1", 10, 0)   ; not a pointer, promotion
                    ^^uword mem1b  = memory("mem2", 10, 0) ; size 2 > 1, no promotion
                    ^^ubyte mem3  = memory("mem3", 10, 0) ; size 1, promotion
                    
                    cx16.r0 = ptr1
                    cx16.r1 = ptr2 as uword
                    cx16.r2 = mem1
                    cx16.r3 = mem1b
                    cx16.r4 = mem3 as uword
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir)!!
        val mainSub = result.compilerAst.entrypoint
        val decls = mainSub.statements.filterIsInstance<VarDecl>().associateBy { it.name }
        
        decls["ptr1"]?.type shouldBe VarDeclType.VAR
        decls["ptr2"]?.type shouldBe VarDeclType.CONST
        decls["mem1"]?.type shouldBe VarDeclType.CONST
        decls["mem1b"]?.type shouldBe VarDeclType.VAR
        decls["mem3"]?.type shouldBe VarDeclType.CONST
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
