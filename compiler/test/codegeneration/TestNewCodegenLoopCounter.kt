package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import prog8.code.target.C64Target
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate
import java.nio.file.Files

class TestNewCodegenLoopCounter: FunSpec({
    val outputDir = Files.createTempDirectory("prog8test_loopcounter")

    test("repeat loop with a conditional branch and indexed pointer store keeps its counter (new6502)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte hits = $0340
                &ubyte iters = $0341
                sub start() {
                    ubyte @shared ch = 1
                    uword @shared ptr = $2000
                    ubyte @shared x = 0
                    repeat 25 {
                        iters++
                        ptr[x] = ch
                        ptr += 40
                        ch++
                        if ch == 10 {
                            ch = 3
                            hits++
                        }
                    }
                }
            }
        """.trimIndent()
        val result = compileText(C64Target(), true, src, outputDir, newCodegen = true, writeAssembly = true)!!
        val machine = result.simulate()
        machine.assertMemory(0x0341, 25)
        machine.assertMemory(0x0340, 3)
    }
})
