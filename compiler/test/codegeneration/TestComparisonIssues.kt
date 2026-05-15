package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText
import prog8tests.helpers.shouldContainInOrder
import kotlin.io.path.readText

class TestComparisonIssues : FunSpec({
    val outputDir = tempdir().toPath()

    test("long augmented addition generates loop") {
        val text = """
            main {
                sub start() {
                    long @shared a = 1000
                    long @shared b = 2000
                    a += b
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asmFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val asm = asmFile.readText()
        asm.shouldContainInOrder("ldx", "#252", "lda", "p8v_a+4,x", "adc", "p8v_b+4,x", "sta", "p8v_a+4,x", "inx", "bne")
    }

    test("long augmented subtraction generates loop") {
        val text = """
            main {
                sub start() {
                    long @shared a = 2000
                    long @shared b = 1000
                    a -= b
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asmFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val asm = asmFile.readText()
        asm.shouldContainInOrder("ldx", "#252", "lda", "p8v_a+4,x", "sbc", "p8v_b+4,x", "sta", "p8v_a+4,x", "inx", "bne")
    }

    test("word <= 0 signed comparison (identifier)") {
        val text = """
            main {
                sub start() {
                    word @shared w = -1
                    if w <= 0 {
                        cx16.r0 = 1
                    }
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asmFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val asm = asmFile.readText()
        // lda var+1 | bmi + | ora var | bne falseLabel | +
        asm.shouldContainInOrder("lda", "p8v_w+1", "bmi", "+", "ora", "p8v_w", "bne", "p8_label_gen")
    }

    test("word > 0 signed comparison (identifier)") {
        val text = """
            main {
                sub start() {
                    word @shared w = 1
                    if w > 0 {
                        cx16.r0 = 1
                    }
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asmFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val asm = asmFile.readText()
        // lda var+1 | bmi falseLabel | ora var | beq falseLabel
        asm.shouldContainInOrder("lda", "p8v_w+1", "bmi", "p8_label_gen", "ora", "p8v_w", "beq", "p8_label_gen")
    }

    test("unsigned word <= 0 becomes == 0") {
        val text = """
            main {
                sub start() {
                    uword @shared uw = 0
                    if uw <= 0 {
                        cx16.r0 = 1
                    }
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asmFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val asm = asmFile.readText()
        // lda var | ora var+1 | bne falseLabel
        asm.shouldContainInOrder("lda", "p8v_uw", "ora", "p8v_uw+1", "bne", "p8_label_gen")
    }

    test("word <= 0 in if-else (identifier)") {
        val text = """
            main {
                sub start() {
                    word @shared w = 1
                    if w <= 0 {
                        cx16.r0 = 1
                    } else {
                        cx16.r0 = 2
                    }
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)!!
        val asmFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val asm = asmFile.readText()
        // lda msb | bmi + | ora lsb | bne elseLabel | +
        asm.shouldContainInOrder("lda", "p8v_w+1", "bmi", "+", "ora", "p8v_w", "bne", "p8_label_gen")
    }
})
