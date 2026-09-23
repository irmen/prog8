package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
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

    test("multiple status flag returns in one call don't clobber each other") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                inline asmsub flaga(ubyte arg @A) -> bool @Pc, bool @Pz, bool @Pn {
                    %asm {{
                        cmp #0
                    }}
                }
                inline asmsub flagb(ubyte arg @A) -> bool @Pc, bool @Pz, bool @Pn {
                    %asm {{
                        cmp #1
                    }}
                }

                &ubyte r1 = $0340
                &ubyte r2 = $0341
                &ubyte r3 = $0342
                &ubyte r4 = $0343

                sub start() {
                    bool c
                    bool z
                    bool n

                    c, z, n = flaga(0)      ; C=1 Z=1 N=0
                    r1 = 0
                    if c r1 += 1
                    if z r1 += 2
                    if n r1 += 4

                    c, z, n = flaga($80)    ; C=1 Z=0 N=1
                    r2 = 0
                    if c r2 += 1
                    if z r2 += 2
                    if n r2 += 4

                    c, z, n = flagb(0)      ; C=0 Z=0 N=1
                    r3 = 0
                    if c r3 += 1
                    if z r3 += 2
                    if n r3 += 4

                    c, z, n = flagb(1)      ; C=1 Z=1 N=0
                    r4 = 0
                    if c r4 += 1
                    if z r4 += 2
                    if n r4 += 4
                }
            }
        """.trimIndent()
        // the status extraction of one flag must not affect the other flags
        val expected = listOf(3, 5, 4, 3)
        for (optimize in listOf(false, true)) {
            val newResult = compileText(C64Target(), optimize, src, outputDir, newCodegen = true, writeAssembly = true)!!
            val newMachine = newResult.simulate()
            newMachine.assertMemory(0x340, expected[0])
            newMachine.assertMemory(0x341, expected[1])
            newMachine.assertMemory(0x342, expected[2])
            newMachine.assertMemory(0x343, expected[3])

            // the legacy codegen is the reference implementation and must produce the same values
            val legacyResult = compileText(C64Target(), optimize, src, outputDir, newCodegen = false, writeAssembly = false)!!
            val legacyMachine = legacyResult.simulate()
            legacyMachine.assertMemory(0x340, expected[0])
            legacyMachine.assertMemory(0x341, expected[1])
            legacyMachine.assertMemory(0x342, expected[2])
            legacyMachine.assertMemory(0x343, expected[3])
        }
    }

    test("status flag return does not clobber an A register return value") {
        // Regression test: in a multi-assign returning both a status flag and a byte in A,
        // the flag extraction (which uses A as scratch) used to destroy the byte return.
        // The register return is now read first, protected by a PUSHST/POPST (php/plp on 6502).
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            main {
                inline asmsub f(ubyte arg @A) -> bool @Pc, ubyte @A {
                    %asm {{
                        lda #42
                        cmp #42
                    }}
                }

                sub start() {
                    bool c
                    ubyte v
                    c, v = f(42)
                    main.v = v
                    main.c = c as ubyte
                }
                ubyte @shared v
                ubyte @shared c
            }
        """.trimIndent()
        // asm-text only, so this test does not require an external assembler
        val compileResult = compileText(C64Target(), false, src, outputDir, newCodegen = true, writeAssembly = true, assemble = false)!!
        val asm = compileResult.compilationOptions.outputDir.resolve(compileResult.compilerAst.name + ".asm").readText()
        // scope to the start subroutine region
        val start = asm.indexOf("; ---- Subroutine: p8b_main.p8s_start")
        val end = asm.indexOf("; ---- Subroutine:", start + 1).let { if (it < 0) asm.length else it }
        val lines = asm.substring(start, end).lines().map { it.trim() }
        // the register read (loadhr) must happen before the flag extraction's lda #0
        val loadhrIdx = lines.indexOfFirst { it.startsWith("; loadhr.b") }
        val flagLdaIdx = lines.indexOfFirst { it == "lda  #0" }
        loadhrIdx shouldBeGreaterThan -1
        flagLdaIdx shouldBeGreaterThan -1
        (loadhrIdx < flagLdaIdx) shouldBe true
        // protected by a php/plp pair around the register read
        lines.count { it == "php" } shouldBe 1
        lines.count { it == "plp" } shouldBe 1
    }

    test("legacy 6502 word @AX/@AY return saves A across status flag extraction") {
        val src = $$"""
            %option no_sysinit
            main {
                asmsub fAX(ubyte a @A) -> bool @Pc, uword @AX {
                    %asm {{
                        lda #42
                        ldx #1
                        cmp #42
                        rts
                    }}
                }
                asmsub fAY(ubyte a @A) -> bool @Pc, uword @AY {
                    %asm {{
                        lda #42
                        ldy #1
                        cmp #42
                        rts
                    }}
                }
                sub start() {
                    bool c
                    uword w
                    c, w = fAX(1)
                    main.w = w
                    c, w = fAY(1)
                    main.w = w
                }
                uword @shared w
            }
        """.trimIndent()
        // asm-text only, so this test does not require an external assembler
        val compileResult = compileText(C64Target(), false, src, outputDir, newCodegen = false, writeAssembly = true, assemble = false)!!
        val asm = compileResult.compilationOptions.outputDir.resolve(compileResult.compilerAst.name + ".asm").readText()
        // scope to the start subroutine region (legacy asm delimits top-level subroutines with a "<name>\t.proc" label at column 0)
        val rawLines = asm.lines()
        val from = rawLines.indexOfFirst { it.startsWith("p8s_start\t.proc") }
        val to = rawLines.drop(from + 1).indexOfFirst { it.endsWith(".proc") && !it.startsWith(" ") && !it.startsWith("\t") }
            .let { if (it < 0) rawLines.size else from + 1 + it }
        val lines = rawLines.subList(from, to).map { it.trim() }
        // the flag extraction clobbers A, which holds the low byte of the word result,
        // so A must be saved with pha and restored with pla before the pair is stored
        lines.count { it == "pha" } shouldBe 2
        lines.count { it == "pla" } shouldBe 2
        val phaIdx = lines.indexOfFirst { it == "pha" }
        val flagIdx = lines.indexOfFirst { it == "lda  #0" }
        val plaIdx = lines.indexOfFirst { it == "pla" }
        // the first store of the word variable's low byte
        val staIdx = lines.indexOfFirst { it.startsWith("sta  ") && it.endsWith("p8v_w") }
        phaIdx shouldBeGreaterThan -1
        flagIdx shouldBeGreaterThan -1
        plaIdx shouldBeGreaterThan -1
        staIdx shouldBeGreaterThan -1
        (phaIdx < flagIdx) shouldBe true
        (flagIdx < plaIdx) shouldBe true
        (plaIdx < staIdx) shouldBe true
    }
})
