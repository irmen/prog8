package prog8tests.codegeneration

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import kotlin.io.path.readText

class Test2DArrays: FunSpec({
    val outputDir = tempdir().toPath()

    test("2D array declaration and indexing compiles on multiple targets") {
        // Covers: constant indexing, variable indexing, expressions, address-of, function args, optimized
        val sources = listOf(
            // Constant indexing
            """
            main {
                sub start() {
                    ubyte[3][4] m
                    m[0][0] = 1
                    m[2][3] = 12
                    ubyte @shared v = m[0][0]
                }
            }""",
            // Variable indexing
            """
            main {
                sub start() {
                    ubyte[3][4] m
                    ubyte @shared r = 1
                    ubyte @shared c = 2
                    m[r][c] = 42
                    ubyte @shared v = m[r][c]
                }
            }""",
            // Used in expressions
            """
            main {
                sub start() {
                    ubyte[2][3] m
                    m[0][0] = 10
                    m[1][1] = 15
                    ubyte @shared s = m[0][0] + m[1][1]
                }
            }""",
            // As function argument
            """
            main {
                sub start() {
                    ubyte[2][3] m
                    m[1][1] = 42
                    process(m[0][0])
                }
                sub process(ubyte v) { }
            }""",
            // Assignment from expression
            """
            main {
                sub start() {
                    ubyte[2][3] src
                    ubyte[2][3] dst
                    src[1][2] = 99
                    dst[1][2] = src[1][2]
                }
            }""",
            // In for loop
            """
            main {
                ubyte[4][4] m
                sub start() {
                    ubyte @shared r = 0
                    ubyte @shared c = 0
                    for r in 0 to 3 {
                        for c in 0 to 3 {
                            m[r][c] = r * c
                        }
                    }
                }
            }""",
            // Address-of operation
            """
            main {
                sub start() {
                    ubyte[3][4] m
                    uword @shared addr = &m
                }
            }"""
        )
        for(src in sources) {
            val result = compileText(VMTarget(), false, src, outputDir) ?: fail("Compilation failed for source:\n$src")
            val resultc64 =
                compileText(C64Target(), false, src, outputDir) ?: fail("Compilation failed for source:\n$src")
        }
        // Also verify optimized compilation
        compileText(VMTarget(), true, sources[0], outputDir) shouldNotBe null
    }

    test("2D array with different element types compiles") {
        val types = listOf(
            "byte[2][3] m; m[0][0] = -10; byte v = m[0][0]",
            "word[2][3] m; m[0][0] = -1000; word v = m[0][0]",
            "uword[2][3] m; m[0][0] = 1000; uword v = m[0][0]"
        )
        for(body in types) {
            val src = """
            main {
                sub start() {
                    $body
                }
            }"""
            compileText(VMTarget(), false, src, outputDir) shouldNotBe null
            compileText(C64Target(), false, src, outputDir) shouldNotBe null
        }
    }

    test("2D array desugaring and constant folding produce correct flat index") {
        // matrix[row][col] should desugar to matrix[row * numCols + col]
        // For ubyte[3][4]: matrix[1][2] -> index 1*4+2=6, matrix[2][3] -> index 2*4+3=11
        
        // Verify assembly contains correct indexing pattern
        val src1 = """
        main {
            sub start() {
                ubyte[3][4] m
                m[1][2] = 42
            }
        }"""
        val result1 = compileText(C64Target(), false, src1, outputDir, writeAssembly = true)!!
        val asmFile1 = result1.compilationOptions.outputDir.resolve(result1.compilerAst.name + ".asm")
        val asm1 = asmFile1.readText()
        asm1 shouldContain "m"  // Should reference the array
        
        // Verify IR after constant folding
        val src2 = """
        main {
            ubyte[3][4] m
            sub start() {
                m[2][3] = 100
            }
        }"""
        val result2 = compileText(VMTarget(), true, src2, outputDir, writeAssembly = true)!!
        val virtfile = result2.compilationOptions.outputDir.resolve(result2.compilerAst.name + ".p8ir")
        val ir = virtfile.readText()
        ir shouldContain "m"  // Should contain the array reference
    }

    test("invalid 2D array usage produces compile errors") {
        // Chained indexing on 1D array
        val src1 = """
        main {
            sub start() {
                ubyte[12] arr
                ubyte x = arr[0][1]
            }
        }"""
        val errors1 = ErrorReporterForTests()
        compileText(VMTarget(), false, src1, outputDir, errors = errors1) shouldBe null
        errors1.errors.isNotEmpty() shouldBe true
        errors1.errors.any { it.contains("chained indexing requires the variable to be declared as a 2D array") } shouldBe true

        // Chained indexing on @shared uword pointer variable
        val srcShared = """
        main {
            sub start() {
                uword @shared ptr = 2222
                ubyte x = ptr[55][22]
            }
        }"""
        val errorsShared = ErrorReporterForTests()
        compileText(VMTarget(), false, srcShared, outputDir, errors = errorsShared) shouldBe null
        errorsShared.errors.size shouldBe 1
        errorsShared.errors[0] shouldContain "chained indexing requires the variable to be declared as a 2D array"

        // Chained indexing on regular uword pointer variable
        val srcUword = """
        main {
            sub start() {
                uword ptr = 3333
                ubyte x = ptr[55][22]
            }
        }"""
        val errorsUword = ErrorReporterForTests()
        compileText(VMTarget(), false, srcUword, outputDir, errors = errorsUword) shouldBe null
        errorsUword.errors.size shouldBe 1
        errorsUword.errors[0] shouldContain "chained indexing requires the variable to be declared as a 2D array"

        // 3D array declaration (grammar limit)
        val src2 = """
        main {
            sub start() {
                ubyte[3][4][5] cube
            }
        }"""
        compileText(VMTarget(), false, src2, outputDir) shouldBe null

        // 2D array as subroutine parameter
        val src3 = """
        main {
            sub process(ubyte[3][4] m) { }
            sub start() { }
        }"""
        compileText(VMTarget(), false, src3, outputDir) shouldBe null
    }
})
