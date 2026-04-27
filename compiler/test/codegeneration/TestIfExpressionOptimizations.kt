package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText
import prog8tests.helpers.shouldContainInOrder
import kotlin.io.path.readText

class TestIfExpressionOptimizations : FunSpec({
    val outputDir = tempdir().toPath()

    test("if-expression operand swapping 1000 == w") {
        val text = """
            main {
                sub start() {
                    uword @shared w = cx16.r0
                    ubyte result = if 1000 == w then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // 1000 == w should be swapped to w == 1000
        // and generate optimized word comparison using AY registers:
        // lda p8v_w | cmp #<1000 | bne false | ldy p8v_w+1 | cpy #>1000 | bne false
        asm.shouldContainInOrder("ldy", "p8v_w+1", "lda", "p8v_w", "cmp", "#<1000", "bne", "ifexpr_false", "cpy", "#>1000", "bne", "ifexpr_false")
    }

    test("if-expression direct branching for byte") {
        val text = """
            main {
                sub start() {
                    ubyte @shared b = cx16.r0L
                    ubyte result = if b < 20 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // b < 20 should use lda b | cmp #20 | bcs false
        asm.shouldContainInOrder("lda", "p8v_b", "cmp", "#20", "bcs", "ifexpr_false")
    }

test("if-expression bitwise AND optimization (BIT instruction)") {
        val text = """
            main {
                sub start() {
                    ubyte @shared b = cx16.r0L
                    ubyte result = if (b & 128) != 0 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // b & 128 should use bit p8v_b | bpl false
        asm.shouldContainInOrder("bit", "p8v_b", "bpl", "ifexpr_false")
    }

    test("if-expression pointer dereference condition") {
        val text = """
            main {
                sub start() {
                    ubyte @shared x = 42
                    ^^ubyte p = &&x
                    ubyte result = if p^^ == 0 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // p^^ should load indirect byte from pointer and branch on zero
        asm.shouldContainInOrder("lda", "(p8v_p)", "cmp", "#0")
    }

    test("if-expression for word equals number") {
        val text = """
            main {
                sub start() {
                    long @shared l = 0
                    l = l + 1
                    ubyte result = if l != 0 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // long != 0 should use ora chain
        asm.shouldContainInOrder("lda", "p8v_l", "ora", "p8v_l+1", "ora", "p8v_l+2", "ora", "p8v_l+3", "beq", "ifexpr_false")
    }

    test("if-expression long comparison optimization") {
        val text = """
            main {
                sub start() {
                    long @shared l = 0
                    l = l + 1
                    ubyte result = if l < 200000 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // 200000 = 0x030d40
        // Signed long comparison uses sbc with lowercase hex literals and signed branch sequence
        asm.shouldContainInOrder(
            "sec",
            "lda", "p8v_l", "sbc", "#\$40",
            "lda", "p8v_l+1", "sbc", "#\$0d",
            "lda", "p8v_l+2", "sbc", "#\$03",
            "lda", "p8v_l+3", "sbc", "#\$00",
            "bvc", "eor", "#128", "bpl", "ifexpr_false"
        )
    }

    test("if-expression long == 0 optimization") {
        val text = """
            main {
                sub start() {
                    long @shared l = 0
                    l = l + 1
                    ubyte result = if l == 0 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // long == 0 should use ora chain + bne false
        asm.shouldContainInOrder("lda", "p8v_l", "ora", "p8v_l+1", "ora", "p8v_l+2", "ora", "p8v_l+3", "bne", "ifexpr_false")
    }

    test("if-expression long == constant optimization") {
        val text = """
            main {
                sub start() {
                    long @shared l = 100000
                    ubyte result = if l == 100000 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // 100000 = $0186a0 -> $a0, $86, $01, $00
        asm.shouldContainInOrder(
            "lda", "p8v_l", "cmp", "#\$a0", "bne", "ifexpr_false",
            "lda", "p8v_l+1", "cmp", "#\$86", "bne", "ifexpr_false",
            "lda", "p8v_l+2", "cmp", "#\$01", "bne", "ifexpr_false",
            "lda", "p8v_l+3", "cmp", "#\$00", "bne", "ifexpr_false"
        )
    }

    test("if-expression long < 0 optimization") {
        val text = """
            main {
                sub start() {
                    long @shared l = -1
                    ubyte result = if l < 0 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // long < 0 should use lda l+3 | bpl false
        asm.shouldContainInOrder("lda", "p8v_l+3", "bpl", "ifexpr_false")
    }

    test("if-expression long >= 0 optimization") {
        val text = """
            main {
                sub start() {
                    long @shared l = 1
                    ubyte result = if l >= 0 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // long >= 0 should use lda l+3 | bmi false
        asm.shouldContainInOrder("lda", "p8v_l+3", "bmi", "ifexpr_false")
    }

    test("if-expression long > 0 optimization") {
        val text = """
            main {
                sub start() {
                    long @shared l = 1
                    ubyte result = if l > 0 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // long > 0 should use lda l+3 | bmi false | ora l+2 | ora l+1 | ora l | beq false
        asm.shouldContainInOrder("lda", "p8v_l+3", "bmi", "ifexpr_false", "ora", "p8v_l+2", "ora", "p8v_l+1", "ora", "p8v_l", "beq", "ifexpr_false")
    }

    test("if-expression word indexed < 0 optimization") {
        val text = """
            main {
                word[10] @shared words
                sub start() {
                    ubyte i = 5
                    ubyte result = if words[i] < 0 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // words[i] < 0 should use lda words_msb,y | bpl false
        // it uses ldy p8v_i directly because it's a split array (no scaling needed)
        asm.shouldContainInOrder("ldy", "p8v_i", "lda", "p8v_words_msb,y", "bpl", "ifexpr_false")
    }

    test("if-expression long indexed > 0 optimization") {
        val text = """
            main {
                long[10] @shared longs
                sub start() {
                    ubyte i = 2
                    ubyte result = if longs[i] > 0 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // longs[i] > 0 should use direct indexed access:
        // lda longs+3,y | bmi false | ora longs+2,y | ora longs+1,y | ora longs,y | beq false
        asm.shouldContainInOrder("lda", "p8v_longs+3,y", "bmi", "ifexpr_false", "ora", "p8v_longs+2,y", "ora", "p8v_longs+1,y", "ora", "p8v_longs,y", "beq", "ifexpr_false")
    }

    test("if-expression long from function < 0 optimization") {
        val text = """
            main {
                sub getlong() -> long { return -1 }
                sub start() {
                    ubyte result = if getlong() < 0 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // getlong() result will be in R14R15 for long return
        asm.shouldContainInOrder("jsr", "p8s_getlong", "lda", "cx16.r14+3", "bpl", "ifexpr_false")
    }

    test("if-expression word == word optimization") {
        val text = """
            main {
                sub start() {
                    uword @shared w1 = cx16.r0
                    uword @shared w2 = cx16.r1
                    ubyte result = if w1 == w2 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // w1 == w2 should use optimized direct comparison using AY registers
        asm.shouldContainInOrder("cmp", "p8v_w2", "bne", "ifexpr_false", "cpy", "p8v_w2+1", "bne", "ifexpr_false")
    }

    test("if-expression word < word optimization") {
        val text = """
            main {
                sub start() {
                    uword @shared w1 = cx16.r0
                    uword @shared w2 = cx16.r1
                    ubyte result = if w1 < w2 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // w1 < w2 should use cmp + bcs for unsigned or signed comparison
        asm.shouldContainInOrder("lda", "p8v_w1", "cmp", "p8v_w2", "bcs", "ifexpr_false")
    }

    test("if-expression word equals zero optimization") {
        val text = """
            main {
                sub start() {
                    uword @shared w = cx16.r0
                    ubyte result = if w == 0 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // w == 0 should use ora + bne for simple zero check
        asm.shouldContainInOrder("ora", "p8v_w+1", "bne", "ifexpr_false")
    }

    test("if-expression long == long optimization") {
        val text = """
            main {
                sub start() {
                    long @shared l1 = 12345
                    long @shared l2 = 67890
                    ubyte result = if l1 == l2 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // l1 == l2 should use optimized variable comparison (branch to skip when differ)
        asm.shouldContainInOrder("cmp", "p8v_l2", "bne", "+", "cmp", "p8v_l2+1", "bne", "+", "cmp", "p8v_l2+2", "bne", "+", "cmp", "p8v_l2+3", "+")
    }

    test("if-expression long != long optimization") {
        val text = """
            main {
                sub start() {
                    long @shared l1 = 12345
                    long @shared l2 = 67890
                    ubyte result = if l1 != l2 then 1 else 0
                    cx16.r1L = result
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        // l1 != l2 should use optimized variable comparison with skip label pattern:
        // cmp p8v_l2, bne skip, ..., cmp p8v_l2+3, beq ifexpr_false, skip:
        asm.shouldContainInOrder("cmp", "p8v_l2", "bne", "skip", "cmp", "p8v_l2+3", "beq", "ifexpr_false")
    }
})
