package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.target.C64Target
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate
import java.nio.file.Files
import kotlin.io.path.readText

class TestNewCodegenExtendingLoads: FunSpec({
    val outputDir = Files.createTempDirectory("prog8test_extload")

    data class Case(val cellType: String, val value: String, val expected: Int, val fusedOp: String)

    fun source(cellType: String, value: String, indirect: Boolean): String {
        val decl = if (indirect) $$"^^$$cellType pcell = $0300" else $$"&$$cellType cell = $0300"
        val access = if (indirect) "pcell^^" else "cell"
        return $$"""
            %option no_sysinit
            %launcher none
            main {
                &long result = $0340
                $$decl
                sub start() {
                    $$access = $$value
                    long @shared l = $$access as long
                    result = l
                }
            }
        """.trimIndent()
    }

    // the expected values are the sign/zero extension of the stored value to 32 bits
    val cases = listOf(
        Case("uword", "$1234", 0x1234, "loadmz.w"),
        Case("uword", "$8000", 0x8000, "loadmz.w"),
        Case("word", "1000", 1000, "loadms.w"),
        Case("word", "-256", -256, "loadms.w"),
        Case("word", "-20000", -20000, "loadms.w"),
        Case("word", "-32768", -32768, "loadms.w"),
        Case("ubyte", "200", 200, "loadmzl.b"),
        Case("byte", "-100", -100, "loadmsl.b"),
    )

    for (indirect in listOf(false, true)) {
        for (case in cases) {
            val name = "new6502 ${if (indirect) "indirect" else "direct"} ${case.cellType} ${case.value} widened to long"
            test(name) {
                val src = source(case.cellType, case.value, indirect)
                val compileResult = compileText(C64Target(), true, src, outputDir, newCodegen = true, writeAssembly = true)!!
                val asm = compileResult.compilationOptions.outputDir.resolve(compileResult.compilerAst.name + ".asm").readText()
                val fusedOp = if (indirect) case.fusedOp.replace("loadm", "loadi") else case.fusedOp
                asm.lines().map { it.trim() }.any { it.startsWith("; $fusedOp ") } shouldBe true

                val machine = compileResult.simulate()
                for (i in 0..3)
                    machine.assertMemory(0x340 + i, (case.expected shr (8 * i)) and 0xff)
            }
        }
    }
})
