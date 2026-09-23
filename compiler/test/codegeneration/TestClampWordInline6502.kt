package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate
import razorvine.ksim65.testing.TestMachine
import kotlin.io.path.readText


/**
 * Tests for the 6502 codegen inline of clamp() on word/uword with compile-time constant bounds.
 * Constant bounds (numeric literals or fixed label addresses) are inlined instead of lowering
 * to the shared func_clamp_word / func_clamp_uword subroutine calls.
 */
class TestClampWordInline6502 : FunSpec({
    val outputDir = tempdir().toPath()

    fun assertWord(machine: TestMachine, address: Int, value: Int) {
        machine.assertMemory(address, value and 0xff)
        machine.assertMemory(address + 1, (value shr 8) and 0xff)
    }

    fun mainBlockAsmLines(compilation: prog8.compiler.CompilationResult): List<String> {
        val asmFile = compilation.compilationOptions.outputDir.resolve(compilation.compilerAst.name + ".asm")
        val lines = asmFile.readText().lines()
        val start = lines.indexOfFirst { it.contains("; ---- block: 'p8b_main'") }
        if (start < 0)
            return lines.map { it.trim() }
        val end = lines.indexOfFirst { it.startsWith("; ---- block:") && it != lines[start] }
        return (if (end > start) lines.subList(start, end) else lines).map { it.trim() }
    }

    test("uword clamp with constant bounds is inlined and clamps correctly") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword r1 = $02
                &uword r2 = $04
                &uword r3 = $06
                &uword r4 = $08
                &uword r5 = $0a
                &uword r6 = $0c
                &uword r7 = $0e
                &uword r8 = $10
                &uword r9 = $12
                &uword r10 = $14
                &uword r11 = $16
                &uword r12 = $18
                &uword r13 = $1a
                &uword r14 = $1c
                &uword r15 = $1e
                &uword r16 = $20

                sub start() {
                    uword @shared v = 200
                    r1 = clamp(v, 100, 500)
                    uword @shared v1 = 50
                    r2 = clamp(v1, 100, 500)
                    uword @shared v2 = 1000
                    r3 = clamp(v2, 100, 500)
                    uword @shared v3 = 500
                    r4 = clamp(v3, 100, 500)
                    uword @shared v4 = 100
                    r5 = clamp(v4, 100, 500)

                    uword @shared v5 = 65000
                    r6 = clamp(v5, 0, 500)
                    uword @shared v6 = 300
                    r7 = clamp(v6, 0, 500)

                    uword @shared v7 = 100
                    r8 = clamp(v7, 100, 65535)
                    uword @shared v8 = 0
                    r9 = clamp(v8, 100, 65535)
                    uword @shared v9 = 65535
                    r10 = clamp(v9, 100, 65535)

                    uword @shared v10 = 456
                    r11 = clamp(v10, 0, 65535)

                    uword @shared v11 = $ffff
                    r12 = clamp(v11, $fff0, $ffff)
                    uword @shared v12 = $fff1
                    r13 = clamp(v12, $fff0, $fff2)
                    uword @shared v13 = $ff00
                    r14 = clamp(v13, $fff0, $fff2)
                    uword @shared v14 = $fffe
                    r15 = clamp(v14, $fff0, $fff2)
                    uword @shared v15 = 400
                    r16 = clamp(v15, 0, 319)

                    poweroff = 1
                }
            }
        """.trimIndent()

        val result = compileText(Cx16Target(), false, src, outputDir)
        result shouldNotBe null

        val machine = result!!.simulate()
        assertWord(machine, 0x02, 200)
        assertWord(machine, 0x04, 100)
        assertWord(machine, 0x06, 500)
        assertWord(machine, 0x08, 500)
        assertWord(machine, 0x0a, 100)
        assertWord(machine, 0x0c, 500)
        assertWord(machine, 0x0e, 300)
        assertWord(machine, 0x10, 100)
        assertWord(machine, 0x12, 100)
        assertWord(machine, 0x14, 65535)
        assertWord(machine, 0x16, 456)
        assertWord(machine, 0x18, 0xffff)
        assertWord(machine, 0x1a, 0xfff1)
        assertWord(machine, 0x1c, 0xfff0)
        assertWord(machine, 0x1e, 0xfff2)
        assertWord(machine, 0x20, 319)
    }

    test("signed word clamp with constant bounds is inlined and clamps correctly") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &word r1 = $02
                &word r2 = $04
                &word r3 = $06
                &word r4 = $08
                &word r5 = $0a
                &word r6 = $0c
                &word r7 = $0e
                &word r8 = $10
                &word r9 = $12
                &word r10 = $14
                &word r11 = $16
                &word r12 = $18
                &word r13 = $1a
                &word r14 = $1c
                &word r15 = $1e
                &word r16 = $20
                &word r17 = $22
                &word r18 = $24
                &word r19 = $26
                &word r20 = $28

                sub start() {
                    word @shared v = 500
                    r1 = clamp(v, -100, 100)
                    word @shared v1 = -500
                    r2 = clamp(v1, -100, 100)
                    word @shared v2 = 0
                    r3 = clamp(v2, -100, 100)
                    word @shared v3 = -100
                    r4 = clamp(v3, -100, 100)
                    word @shared v4 = 100
                    r5 = clamp(v4, -100, 100)

                    word @shared v5 = 32767
                    r6 = clamp(v5, -100, 100)
                    word @shared v6 = -32768
                    r7 = clamp(v6, -100, 100)

                    word @shared v7 = 20000
                    r8 = clamp(v7, -30000, 30000)
                    word @shared v8 = -32000
                    r9 = clamp(v8, -30000, 30000)
                    word @shared v9 = -29999
                    r10 = clamp(v9, -30000, 30000)
                    word @shared v10 = 29999
                    r11 = clamp(v10, -30000, 30000)
                    word @shared v11 = 30000
                    r12 = clamp(v11, -30000, 30000)

                    word @shared v12 = -20000
                    r13 = clamp(v12, -32768, 10000)
                    word @shared v13 = 20000
                    r14 = clamp(v13, -32768, 10000)
                    word @shared v14 = -5000
                    r15 = clamp(v14, -10000, 32767)
                    word @shared v15 = -20000
                    r16 = clamp(v15, -10000, 32767)

                    word @shared v16 = -12345
                    r17 = clamp(v16, -32768, 32767)
                    word @shared v17 = 1
                    r18 = clamp(v17, 0, 32767)
                    word @shared v18 = 30000
                    r19 = clamp(v18, 0, 30000)
                    word @shared v19 = -30000
                    r20 = clamp(v19, 0, 5000)

                    poweroff = 1
                }
            }
        """.trimIndent()

        val result = compileText(Cx16Target(), false, src, outputDir)
        result shouldNotBe null

        val machine = result!!.simulate()
        assertWord(machine, 0x02, 100)
        assertWord(machine, 0x04, -100)
        assertWord(machine, 0x06, 0)
        assertWord(machine, 0x08, -100)
        assertWord(machine, 0x0a, 100)
        assertWord(machine, 0x0c, 100)
        assertWord(machine, 0x0e, -100)
        assertWord(machine, 0x10, 20000)
        assertWord(machine, 0x12, -30000)
        assertWord(machine, 0x14, -29999)
        assertWord(machine, 0x16, 29999)
        assertWord(machine, 0x18, 30000)
        assertWord(machine, 0x1a, -20000)
        assertWord(machine, 0x1c, 10000)
        assertWord(machine, 0x1e, -5000)
        assertWord(machine, 0x20, -10000)
        assertWord(machine, 0x22, -12345)
        assertWord(machine, 0x24, 1)
        assertWord(machine, 0x26, 30000)
        assertWord(machine, 0x28, 0)
    }

    test("uword clamp with constant bounds no longer emits a subroutine call") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                sub start() {
                    uword @shared v = 200
                    uword @shared r = clamp(v, 100, 500)
                    uword @shared r2 = clamp(v, 0, 500)
                    uword @shared r3 = clamp(v, 100, 65535)
                    uword @shared r4 = clamp(v, 0, 65535)
                }
            }
        """.trimIndent()

        val result = compileText(Cx16Target(), false, src, outputDir)
        result shouldNotBe null
        val lines = mainBlockAsmLines(result!!)

        // inlined: only the upper-clamp part is emitted for the (0,500) and lower-clamp for (100,65535) variants
        lines.any { it.startsWith("jsr") && it.contains("func_clamp") } shouldBe false
        lines.any { it.startsWith("cpy") } shouldBe true        // the unsigned compare uses cpy
        lines.any { it == "stz  P8ZP_SCRATCH_W1" } shouldBe false  // unsigned inlining needs no scratch
    }

    test("signed word clamp with constant bounds no longer emits a subroutine call and uses the signed compare") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                sub start() {
                    word @shared v = -200
                    word @shared r = clamp(v, -100, 100)
                    word @shared r2 = clamp(v, -32768, 100)
                    word @shared r3 = clamp(v, -100, 32767)
                    word @shared r4 = clamp(v, -32768, 32767)
                }
            }
        """.trimIndent()

        val result = compileText(Cx16Target(), false, src, outputDir)
        result shouldNotBe null
        val lines = mainBlockAsmLines(result!!)

        // no calls to the shared word clamp subroutines
        lines.any { it.startsWith("jsr") && it.contains("func_clamp") } shouldBe false
        // the signed compare sequence uses the overflow-compensating bvc/eor trick
        lines.any { it == "eor  #\$80" } shouldBe true
        lines.any { it == "sta  P8ZP_SCRATCH_W1" } shouldBe true  // signed inlining saves the value in W1 scratch
    }

    test("word clamp with variable (non-constant) bounds still uses the subroutine call") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                sub start() {
                    uword @shared v = 200
                    uword @shared lo = 100
                    uword @shared hi = 500
                    uword @shared r = clamp(v, lo, hi)
                    word @shared wv = -200
                    word @shared wlo = -100
                    word @shared whi = 100
                    word @shared wr = clamp(wv, wlo, whi)
                }
            }
        """.trimIndent()

        val result = compileText(Cx16Target(), false, src, outputDir)
        result shouldNotBe null
        val lines = mainBlockAsmLines(result!!)

        // variable bounds are not constants, so do NOT inline
        lines.count { it == "jsr  prog8_lib.func_clamp_uword" } shouldBe 1
        lines.count { it == "jsr  prog8_lib.func_clamp_word" } shouldBe 1
    }

    test("word clamp with fixed label address bounds is inlined") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                ubyte @shared lowbyte
                ubyte @shared highbyte
                sub start() {
                    uword @shared v = 100
                    uword @shared v2 = 50
                    uword @shared r = clamp(v, &lowbyte, &highbyte)
                    uword @shared r2 = clamp(v2, &lowbyte, &highbyte)
                }
            }
        """.trimIndent()

        val result = compileText(Cx16Target(), false, src, outputDir)
        result shouldNotBe null
        val text = mainBlockAsmLines(result!!).joinToString("\n")

        // address-of bounds are compile-time constant label references, inlined with #< / #> immediates
        text.lines().any { it.startsWith("jsr") && it.contains("func_clamp") } shouldBe false
        text shouldContain "#<p8b_main.p8v_lowbyte"
        text shouldContain "#>p8b_main.p8v_lowbyte"
        text shouldContain "#<p8b_main.p8v_highbyte"
        text shouldContain "#>p8b_main.p8v_highbyte"
    }
})