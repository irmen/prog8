package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate
import razorvine.ksim65.testing.TestMachine
import kotlin.io.path.readText


/**
 * Tests for the runtime range check that prog8_math.square / prog8_math.square_long do.
 *
 * The fast path of square (Lee Davison's routine) only computes (abs(x) and 255) squared, so
 * it is only valid for -255..255.  Prog8 word semantics require the result to wrap/truncate
 * mod 2^16, so anything outside that range must produce the same value as the general
 * multiply would.  The check lives in the routine itself (rather than inline at every call
 * site) so that it is paid for in code size only once.
 */
class TestSquareRangeCheck6502 : FunSpec({
    val outputDir = tempdir().toPath()

    fun assertWord(machine: TestMachine, address: Int, value: Int) {
        machine.assertMemory(address, value and 0xff)
        machine.assertMemory(address + 1, (value shr 8) and 0xff)
    }

    fun assertLong(machine: TestMachine, address: Int, value: Long) {
        for (i in 0..3)
            machine.assertMemory(address + i, ((value shr (i * 8)) and 0xff).toInt())
    }

    test("uword x*x is correct inside and outside the fast path range") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword r0 = $02
                &uword r1 = $04
                &uword r2 = $06
                &uword r3 = $08
                &uword r4 = $0a
                &uword r5 = $0c
                &uword r6 = $0e
                &uword r7 = $10
                &uword r8 = $12
                &uword r9 = $14
                &uword r10 = $16
                &uword r11 = $18
                &uword r12 = $1a

                sub start() {
                    uword @shared a0 = 0
                    r0 = a0*a0
                    uword @shared a1 = 200
                    r1 = a1*a1
                    uword @shared a2 = 255
                    r2 = a2*a2
                    ; 256: just outside the range, 256*256 wraps to 0
                    uword @shared a3 = 256
                    r3 = a3*a3
                    ; 257: 257*257 = 66049, wraps to 513.  The unchecked fast path would
                    ; have computed (257 and 255) squared = 1 here.
                    uword @shared a4 = 257
                    r4 = a4*a4
                    ; 300: 300*300 = 90000, wraps to 24464 (fast path would give 44*44 = 1936)
                    uword @shared a5 = 300
                    r5 = a5*a5
                    ; 1000: wraps to 1000000 and 65536 = 16960
                    uword @shared a6 = 1000
                    r6 = a6*a6
                    ; -1: in range, result 1
                    uword @shared a7 = 65535
                    r7 = a7*a7
                    ; -255: in range, result 65025
                    uword @shared a8 = 65281
                    r8 = a8*a8
                    ; -256: out of range, 65536 wraps to 0
                    uword @shared a9 = 65280
                    r9 = a9*a9
                    ; -257: out of range, 66049 wraps to 513
                    uword @shared a10 = 65279
                    r10 = a10*a10
                    ; 40000: out of range, wraps to 24464
                    uword @shared a11 = 40000
                    r11 = a11*a11
                    ; 65535: out of range, 65535*65535 = 4294836225, mod 65536 = 1
                    uword @shared a12 = 65535
                    r12 = a12*a12

                    poweroff = 1
                }
            }
        """.trimIndent()

        val result = compileText(Cx16Target(), false, src, outputDir)
        result shouldNotBe null
        val machine = result!!.simulate()

        assertWord(machine, 0x02, 0)           // 0
        assertWord(machine, 0x04, 40000)       // 200
        assertWord(machine, 0x06, 65025)      // 255
        assertWord(machine, 0x08, 0)           // 256 -> 0
        assertWord(machine, 0x0a, 513)        // 257 -> 513
        assertWord(machine, 0x0c, 24464)      // 300 -> 24464
        assertWord(machine, 0x0e, 16960)      // 1000 -> 16960
        assertWord(machine, 0x10, 1)          // -1
        assertWord(machine, 0x12, 65025)      // -255
        assertWord(machine, 0x14, 0)          // -256 -> 0
        assertWord(machine, 0x16, 513)        // -257 -> 513
        assertWord(machine, 0x18, 4096)       // 40000 -> 4096
        assertWord(machine, 0x1a, 1)          // 65535 -> 1
    }

    test("long x*x is correct for values that do and do not fit in a signed word") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &long r0 = $02
                &long r1 = $06
                &long r2 = $0a
                &long r3 = $0e
                &long r4 = $12

                sub start() {
                    long @shared a0 = 200
                    r0 = a0*a0
                    long @shared a1 = 1000
                    r1 = a1*a1
                    ; 40000 fits in a long but its low word is -25536, so squaring only the
                    ; low word (the fast path) would be wrong; 40000*40000 = 1600000000.
                    long @shared a2 = 40000
                    r2 = a2*a2
                    long @shared a3 = 100000
                    r3 = a3*a3
                    long @shared a4 = 1000
                    r4 = a4*a4

                    poweroff = 1
                }
            }
        """.trimIndent()

        val result = compileText(Cx16Target(), false, src, outputDir)
        result shouldNotBe null
        val machine = result!!.simulate()

        assertLong(machine, 0x02, 200L*200)        // 40000
        assertLong(machine, 0x06, 1000L*1000)      // 1000000
        assertLong(machine, 0x0a, 40000L*40000)    // 1600000000
        assertLong(machine, 0x0e, 100000L*100000)  // 10000000000
        assertLong(machine, 0x12, 1000L*1000)      // 1000000
    }

    test("the range check is emitted in the routine, not inlined at each call site") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                sub start() {
                    uword @shared v = 200
                    uword @shared r1 = v*v
                    uword @shared r2 = v*v
                    uword @shared r3 = v*v
                }
            }
        """.trimIndent()

        val result = compileText(Cx16Target(), false, src, outputDir)
        result shouldNotBe null
        val asmFile = result!!.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        // normalize runs of spaces so 64tass's column alignment doesn't matter
        val lines = asmFile.readText().lines().map { it.trim().replace(Regex("\\s+"), " ") }

        // every call site is just a plain jsr, no inline range test
        lines.count { it == "jsr prog8_math.square" } shouldBe 3
        lines.any { it.contains("squarefast") || it.contains("squareslow") } shouldBe false

        // and the check itself exists exactly once, inside each shared routine
        val wordStart = lines.indexOfFirst { it == "square .proc" }
        (wordStart >= 0) shouldBe true
        val wordBody = lines.subList(wordStart, lines.size).takeWhile { !it.startsWith(".pend") }
        wordBody.count { it == "jmp multiply_words" } shouldBe 1

        val longStart = lines.indexOfFirst { it == "square_long .proc" }
        (longStart >= 0) shouldBe true
        val longBody = lines.subList(longStart, lines.size).takeWhile { !it.startsWith(".pend") }
        longBody.count { it == "jmp multiply_longs" } shouldBe 1
    }
})
