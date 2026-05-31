package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText

class TestLongAugmentedAssignment : FunSpec({
    val outputDir = tempdir().toPath()

    test("long augmented assignment with literal") {
        val text = """
            main {
                sub start() {
                    long @shared a = 1000
                    a += 500
                }
            }
        """.trimIndent()
        // Should compile successfully
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)
    }

    test("long augmented assignment with expression") {
        val text = """
            main {
                sub start() {
                    long @shared a = 1000
                    long @shared b = 500
                    a += b + 100
                }
            }
        """.trimIndent()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)
    }
    
    test("long augmented assignment with ubyte") {
        val text = """
            main {
                sub start() {
                    long @shared a = 1000
                    ubyte @shared b = 200
                    a += b
                }
            }
        """.trimIndent()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = true)
    }
})
