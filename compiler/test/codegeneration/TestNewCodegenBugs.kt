package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.target.C64Target
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate
import java.nio.file.Files
import kotlin.io.path.readText


class TestNewCodegenBugs : FunSpec({
    val outputDir = Files.createTempDirectory("prog8test")

    test("float memory += / -= constant (ADDIM/SUBIM) compiles and emits correct sequences") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %import floats
            main {
                &ubyte result = $0340
                sub start() {
                    float @shared f = 1.0
                    f += 0.5
                    f -= 0.25
                    f += 10.0
                    f -= 0.25
                    if f == 11.0 {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        // with optimize=false the whole floats library (incl. atan2's `atn += pi`)
        // is kept alive, which is what originally triggered the ADDIM crash
        val compileResult = compileText(C64Target(), false, src, outputDir, newCodegen = true, writeAssembly = true)!!
        val asm = compileResult.compilationOptions.outputDir.resolve(compileResult.compilerAst.name + ".asm").readText()
        val lines = asm.lines().map { it.trim() }
        // each ADDIM emits MOVFM + FADD + MOVMF, each SUBIM emits push/pop + FSUBT + MOVMF
        fun sequenceAfter(marker: String, window: Int, instruction: String): Boolean =
            lines.withIndex().any { (i, line) ->
                line.contains(marker) && lines.drop(i + 1).take(window).any { it == instruction }
            }
        sequenceAfter("; addim.f", 10, "jsr  floats.FADD") shouldBe true
        sequenceAfter("; subim.f", 14, "jsr  floats.FSUBT") shouldBe true
    }

    test("BSS struct instance and union instance assemble and work correctly") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                struct S {
                    uword w
                }
                union U {
                    uword a
                    uword b
                }
                sub start() {
                    ^^S s = ^^S : []
                    ^^U u = ^^U : []
                    s^^.w = $1234
                    u^^.b = $beef
                    &ubyte result = $0340
                    if s^^.w == $1234 and u^^.b == $beef {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val compileResult = compileText(C64Target(), false, src, outputDir, newCodegen = true, writeAssembly = true)!!
        // every generated code reference must use the emitted prog8_struct_instances_bss block
        val asm = compileResult.compilationOptions.outputDir.resolve(compileResult.compilerAst.name + ".asm").readText()
        val lines = asm.lines().map { it.trim() }
        lines.any { it == "prog8_struct_instances_bss  .block" } shouldBe true
        lines.none { !it.startsWith(";") && it.contains("prog8_struct_instances.main_") } shouldBe true
        val machine = compileResult.simulate()
        machine.assertMemory(0x340, 1)
    }

    test("initialized struct instance keeps float and long fields intact") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %import floats
            main {
                struct P {
                    float x
                    ubyte y
                }
                struct L {
                    long n
                    ubyte b
                }
                sub start() {
                    ^^P p = ^^P : [1.5, 42]
                    ^^L l = ^^L : [123456, 7]
                    &ubyte result = $0340
                    if p^^.y == 42 and p^^.x == 1.5 and l^^.n == 123456 and l^^.b == 7 {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val compileResult = compileText(C64Target(), false, src, outputDir, newCodegen = true, writeAssembly = true)!!
        val asm = compileResult.compilationOptions.outputDir.resolve(compileResult.compilerAst.name + ".asm").readText()
        val lines = asm.lines().map { it.trim() }
        lines.any { it.contains(".byte") && it.contains("[$81, $40, $00, $00, $00]") } shouldBe true
        lines.any { it.contains(".dint") && it.contains("123456") } shouldBe true
    }
})
