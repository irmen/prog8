package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.target.Amiga500Target
import prog8.code.target.Qemu68kTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestM68k : FunSpec({

    val outputDir = tempdir().toPath()

    test("module sub returning uword computed from a long expression assembles on qemu68k") {
        // Regression test for a m68k backend bug: a module-level sub whose return
        // type is uword, but whose return expression is a 'long' value cast to uword,
        // caused the return-value slot to be sized as a single byte (.b) instead of a
        // word. This made the assembler emit invalid 'move.l d0, mod.sub.b' and fail.
        val src = """
%zeropage basicsafe

bugrepro {
    %option no_symbol_prefixing

    sub env_samples(ubyte param) -> uword {
        long b = (param as long) * 100000 / 65025
        return ((param as long) * b) as uword
    }

    sub useit(ubyte p) -> uword {
        return env_samples(p)
    }
}

main {
    uword @shared sink
    sub start() {
        sink = bugrepro.useit(100)
    }
}
"""
        val result = compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = true, assemble = false)
        result shouldNotBe null
    }

    test("variables whose scoped name ends with vasm size extension assemble on qemu68k") {
        // Regression test for vasm size-extension clash: a scoped symbol such as
        // "mymod.b" is emitted as a label `mymod.b:` and operands `move.b mymod.b,...`.
        // Vasm interprets trailing ".b/.w/.l/.s/.d/.x/.p/.q" as a size extension, yielding
        // "bad size extension", "label redefined" and "unknown mnemonic <.b:>" errors.
        // The m68k backend must mangle the last dot when the suffix is a size extension.
        val src = """
mymod {
    %option no_symbol_prefixing
    ubyte b
    uword w
    ubyte l
    ubyte s
    ubyte d
    ubyte x
    ubyte p
    ubyte q
    ubyte w2
    ubyte l2

    sub use() {
        b = 1
        w = 1000
        l = 2
        s = 3
        d = 4
        x = 5
        p = 6
        q = 7
        w2 = 8
        l2 = 9
    }
}

main {
    sub start() {
        mymod.use()
    }
}
"""
        val result = compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = true, assemble = false)
        result shouldNotBe null

        // Also verify that the mangled names don't contain a trailing size extension
        // and that the assembler didn't treat them as size overrides.
        // The raw names would be "mymod.b", "mymod.w", etc.; the emitted asm must use "mymod_b" etc.
        val asm = outputDir.toFile().walkTopDown()
            .filter { it.extension == "asm" }
            .map { it.readText() }
            .joinToString("\n")
        // there must be no bare label `mymod.b:` (would be mis-parsed as size extension)
        // we check the asm lines after fix contain the mangled form
        // use lines.any with exact label to avoid dumping whole asm on failure
        val lines = asm.lines().map { it.trim() }
        // mangled labels should exist
        lines.any { it == "mymod_b:" } shouldNotBe false
        lines.any { it == "mymod_w:" } shouldNotBe false
        lines.any { it == "mymod_l:" } shouldNotBe false
        lines.any { it == "mymod_s:" } shouldNotBe false
        lines.any { it == "mymod_d:" } shouldNotBe false
        lines.any { it == "mymod_x:" } shouldNotBe false
        lines.any { it == "mymod_p:" } shouldNotBe false
        lines.any { it == "mymod_q:" } shouldNotBe false
        // raw size-extension labels must NOT exist (they would trigger vasm errors)
        lines.any { it == "mymod.b:" } shouldNotBe true
        lines.any { it == "mymod.w:" } shouldNotBe true
        lines.any { it == "mymod.l:" } shouldNotBe true
    }

    test("local variables named after size extensions assemble on qemu68k") {
        // Local variable `b` inside a sub becomes scoped name `mymod2.sub.b` which also
        // ends with ".b". This is the exact pattern of the original bugrepro (long b local).
        val src = """
%zeropage basicsafe

mymod2 {
    sub foo(ubyte param) -> uword {
        ubyte b = param
        uword w = b as uword
        ubyte l = b
        ubyte s = 1
        return w
    }
}

main {
    uword @shared sink
    sub start() {
        sink = mymod2.foo(42)
    }
}
"""
        val result = compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = true, assemble = false)
        result shouldNotBe null
    }

    test("long/pointer type casts and assignments on 32-bit target") {
        val src = """
            main {
                sub start() {
                    uword @shared uw = 12345
                    long @shared lg
                    ^^ubyte @shared typedptr

                    lg = uw
                    uw = lg as uword

                    lg = typedptr
                    typedptr = lg as ^^ubyte

                    typedptr = uw as ^^ubyte
                    uw = typedptr as uword

                    lg = uw as ^^ubyte as long

                    long @shared lresult = uw + 5
                    uw = lresult as uword

                    uw = subRetLong() as uword
                    lg = subRetUword()
                }
                sub subRetLong() -> long {
                    long @shared val_ = 99999
                    return val_
                }
                sub subRetUword() -> uword {
                    uword @shared val_ = 54321
                    return val_
                }
            }"""
        compileText(Qemu68kTarget(), false, src, outputDir, writeAssembly = false) shouldNotBe null
        compileText(Qemu68kTarget(), true, src, outputDir, writeAssembly = false) shouldNotBe null
        compileText(Amiga500Target(), false, src, outputDir, writeAssembly = false) shouldNotBe null
        compileText(Amiga500Target(), true, src, outputDir, writeAssembly = false) shouldNotBe null
    }

    test("long/pointer indexing and type errors on 32-bit target") {
        val src = """
            main {
                sub start() {
                    long @shared lptr
                    ubyte @shared idx

                    lptr[10] = 42
                    lptr[idx] = idx
                    ubyte @shared a = lptr[10]
                    ubyte @shared b = lptr[idx]
                }
            }"""
        compileText(Qemu68kTarget(), false, src, outputDir, writeAssembly = false) shouldNotBe null
        compileText(Qemu68kTarget(), true, src, outputDir, writeAssembly = false) shouldNotBe null

        val srcUword = """
            main {
                sub start() {
                    uword @shared uptr
                    ubyte @shared dummy = uptr[10]
                }
            }"""
        val errorsUword = ErrorReporterForTests()
        compileText(Qemu68kTarget(), false, srcUword, outputDir, writeAssembly = false, errors = errorsUword) shouldBe null
        errorsUword.errors.any { it.contains("indexing requires an iterable, address long, or pointer variable") } shouldBe true

        val srcWord = """
            main {
                sub start() {
                    word @shared wptr
                    ubyte @shared dummy = wptr[10]
                }
            }"""
        val errorsWord = ErrorReporterForTests()
        compileText(Qemu68kTarget(), false, srcWord, outputDir, writeAssembly = false, errors = errorsWord) shouldBe null
        errorsWord.errors.any { it.contains("indexing requires an iterable, address long, or pointer variable") } shouldBe true

        val srcUbyte = """
            main {
                sub start() {
                    ubyte @shared bptr
                    ubyte @shared dummy = bptr[10]
                }
            }"""
        val errorsUbyte = ErrorReporterForTests()
        compileText(Qemu68kTarget(), false, srcUbyte, outputDir, writeAssembly = false, errors = errorsUbyte) shouldBe null
        errorsUbyte.errors.any { it.contains("indexing requires an iterable, address long, or pointer variable") } shouldBe true

        val srcByte = """
            main {
                sub start() {
                    byte @shared bptr
                    ubyte @shared dummy = bptr[10]
                }
            }"""
        val errorsByte = ErrorReporterForTests()
        compileText(Qemu68kTarget(), false, srcByte, outputDir, writeAssembly = false, errors = errorsByte) shouldBe null
        errorsByte.errors.any { it.contains("indexing requires an iterable, address long, or pointer variable") } shouldBe true

        val srcNarrow = """
            main {
                sub start() {
                    long @shared lg = 999
                    uword @shared uw
                    uw = lg
                }
            }"""
        val errorsNarrow = ErrorReporterForTests()
        compileText(Qemu68kTarget(), false, srcNarrow, outputDir, writeAssembly = false, errors = errorsNarrow) shouldBe null
        errorsNarrow.errors.any { it.contains("doesn't match target type") } shouldBe true
    }

    test("large constant index on long pointer compiles on qemu68k") {
        val src = """
            main {
                sub start() {
                    long @shared lptr
                    lptr[999999] = 42
                    ubyte @shared val_ = lptr[999999]
                }
            }"""
        compileText(Qemu68kTarget(), false, src, outputDir, writeAssembly = false) shouldNotBe null
    }

    test("pointer post-increment fuses to (a0)+ on qemu68k") {
        // #3.6: loadm+loadi+incm and loadm+storei+incm on same pointer variable
        // should fuse to loadp_inc/storep_inc and lower to m68k (a0)+
        val src = """
            main {
                sub start() {
                    ubyte[4] @shared src = [1,2,3,4]
                    ubyte[4] @shared dst = [0,0,0,0]
                    pointer pSrc = &src
                    pointer pDst = &dst
                    ubyte i
                    for i in 0 to 3 {
                        @(pDst) = @(pSrc)
                        pSrc++
                        pDst++
                    }
                    ubyte @shared dummy = dst[0]
                }
            }"""
        // without optimization the peephole is disabled, so no post-inc fusion
        val resNoOpt = compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = true, assemble = false)
        resNoOpt shouldNotBe null
        // with optimization the IR should contain the new post-inc ops
        val resOpt = compileText(Qemu68kTarget(), optimize = true, src, outputDir, writeAssembly = true, assemble = false)
        resOpt shouldNotBe null
        val optimized = resOpt!!
        val p8ir = optimized.compilationOptions.outputDir.resolve("${optimized.compilerAst.name}.p8ir").toFile().readText()
        // check that the optimized IR uses the fused ops (single type specifier, no second type field)
        (p8ir.contains("loadp_inc.b") || p8ir.contains("loadp_inc")) shouldBe true
        (p8ir.contains("storep_inc.b") || p8ir.contains("storep_inc")) shouldBe true
        // and that the m68k assembly uses post-increment addressing (a0)+
        val asm = optimized.compilationOptions.outputDir.resolve("${optimized.compilerAst.name}.asm").toFile().readText()
        val lines = asm.lines().map { it.trim() }
        lines.any { it.contains("(a0)+") } shouldBe true
        // cx16 must still compile (fallback lowers loadp_inc to $22 indirect + inc)
        val resCx16 = compileText(prog8.code.target.Cx16Target(), optimize = true, src, outputDir, writeAssembly = true, assemble = false)
        resCx16 shouldNotBe null
    }
})
