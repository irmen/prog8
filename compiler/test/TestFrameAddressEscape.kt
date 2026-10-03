package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.target.C64Target
import prog8.code.target.Qemu68kTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestFrameAddressEscape : FunSpec({
    val outputDir = tempdir().toPath()

    // m68k stack frames make subroutine locals per-activation,
    // so a pointer to a local dangles after the subroutine returns. The compiler only warns, best-effort,
    // on the m68k targets, and only when the frame address actually escapes the subroutine.

    test("storing the address of a local into a shared variable warns on qemu68k") {
        val src = """
main {
    ^^ubyte @shared shared_ptr

    sub start() {
        ubyte buf = 42
        shared_ptr = &buf
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.any { "the address of local 'buf' in subroutine 'start' is stored in 'shared_ptr'" in it } shouldBe true
        errors.warnings.any { "a stack frame address is only valid while the subroutine is running" in it } shouldBe true
    }

    test("returning the address of a local warns on qemu68k") {
        val src = """
main {
    sub start() {
        ^^ubyte p = getbuf()
    }

    sub getbuf() -> ^^ubyte {
        ubyte buf = 42
        return &buf
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.any { "the address of local 'buf' in subroutine 'getbuf' escapes the subroutine (returned)" in it } shouldBe true
    }

    test("passing the address of a local to a subroutine call warns on qemu68k") {
        val src = """
main {
    ubyte @shared result

    sub start() {
        ubyte buf = 42
        consume(&buf)
    }

    sub consume(^^ubyte p) {
        result = p^^
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.any { "the address of local 'buf' in subroutine 'start' escapes the subroutine (passed to a call)" in it } shouldBe true
    }

    test("warning fires only once per escaping variable even with multiple uses") {
        val src = """
main {
    ^^ubyte @shared shared_ptr

    sub start() {
        ubyte buf = 42
        shared_ptr = &buf
        shared_ptr = &buf
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.warnings.count { "the address of local 'buf' in subroutine 'start'" in it } shouldBe 1
    }

    test("no warning when the pointer stays inside the same subroutine") {
        val src = """
main {
    sub start() {
        ubyte buf = 42
        ^^ubyte p = &buf
        p^^ = 99
        buf = p^^
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.none { "address of local" in it } shouldBe true
    }

    test("no escape warnings at all on a 6502 target") {
        val src = """
main {
    ^^ubyte @shared shared_ptr

    sub start() {
        ubyte buf = 42
        shared_ptr = &buf
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.none { "address of local" in it } shouldBe true
    }

    test("inline assembly using p8_regfile warns when the program is recursive on qemu68k") {
        val src = """
main {
    ubyte @shared g

    sub start() {
        g = fib(5)
    }

    sub fib(ubyte n) -> ubyte {
        %asm {{
            move.l p8_regfile, d0
        }}
        if n < 2 {
            return n
        }
        return fib(n - 1) + fib(n - 2)
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.any { "inline assembly uses p8_regfile" in it } shouldBe true
        errors.warnings.any { "registers now live in stack frames" in it } shouldBe true
    }

    test("inline assembly using p8_fregfile also warns when the program is recursive on qemu68k") {
        val src = """
main {
    ubyte @shared g

    sub start() {
        g = fib(5)
    }

    sub fib(ubyte n) -> ubyte {
        %asm {{
            fmove.s p8_fregfile, fp0
        }}
        if n < 2 {
            return n
        }
        return fib(n - 1) + fib(n - 2)
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.any { "inline assembly uses p8_fregfile" in it } shouldBe true
    }

    test("inline assembly using p8_regfile does not warn when the program is not recursive") {
        val src = """
main {
    sub start() {
        %asm {{
            move.l p8_regfile, d0
        }}
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.none { "p8_regfile" in it } shouldBe true
    }
})
