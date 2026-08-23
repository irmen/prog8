package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldNotBe
import prog8.code.target.Qemu68kTarget
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
})
