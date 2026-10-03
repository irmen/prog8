package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.C64Target
import prog8.code.target.Qemu68kTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestDeferRecursionCheck : FunSpec({
    val outputDir = tempdir().toPath()

    // m68k stack frames make defer-referenced locals static (ideas/m68k-stack-memory-model.md section 11),
    // so a deferring subroutine that can have two live activations must be rejected.
    // The check only runs on m68k targets and only when a defer body touches subroutine-local state.

    test("directly recursive subroutine with defer on a local is rejected on qemu68k") {
        val src = """
main {
    sub start() {
        counter(0)
    }

    sub counter(ubyte n) {
        ubyte x = 1
        defer x = x + 1
        if n < 10 {
            counter(n + 1)
        }
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "defer"
        errors.errors[0] shouldContain "recursive"
    }

    test("same recursive program is unaffected on a 6502 target") {
        val src = """
main {
    sub start() {
        counter(0)
    }

    sub counter(ubyte n) {
        ubyte x = 1
        defer x = x + 1
        if n < 10 {
            counter(n + 1)
        }
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
    }

    test("recursive subroutine whose defer only touches a global shared variable is allowed on qemu68k") {
        val src = """
main {
    ubyte @shared result

    sub start() {
        counter(0)
    }

    sub counter(ubyte n) {
        defer result = result + 1
        if n < 10 {
            counter(n + 1)
        }
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
    }

    test("non-recursive subroutine with defer on a local is allowed on qemu68k") {
        val src = """
main {
    sub start() {
        helper()
    }

    sub helper() {
        ubyte x = 1
        defer x = x + 1
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
    }

    test("mutually recursive subroutine with defer on a local is rejected on qemu68k") {
        val src = """
main {
    sub start() {
        a(0)
    }

    sub a(ubyte n) {
        ubyte x = 1
        defer x = x + 1
        if n < 10 {
            b(n + 1)
        }
    }

    sub b(ubyte n) {
        a(n)
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "defer"
        errors.errors[0] shouldContain "recursive"
    }

    test("deferring subroutine whose address is taken is conservatively rejected on qemu68k") {
        val src = """
main {
    long @shared sub_address

    sub start() {
        sub_address = &helper
    }

    sub helper() {
        ubyte x = 1
        defer x = x + 1
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "defer"
        errors.errors[0] shouldContain "recursive"
    }

    test("defer nested inside a loop in a recursive subroutine is also detected on qemu68k") {
        val src = """
main {
    sub start() {
        counter(0)
    }

    sub counter(ubyte n) {
        ubyte x = 1
        repeat 2 {
            defer x = x + 1
        }
        if n < 10 {
            counter(n + 1)
        }
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "defer"
        errors.errors[0] shouldContain "recursive"
    }

    test("pointer dereference through a local pointer in a defer is detected on qemu68k") {
        val src = """
main {
    sub start() {
        counter(0)
    }

    sub counter(ubyte n) {
        ubyte @shared storage
        ^^ubyte p = &storage
        defer p^^ = 42
        if n < 10 {
            counter(n + 1)
        }
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "defer"
        errors.errors[0] shouldContain "recursive"
    }

    test("defer referencing a parameter of a recursive subroutine is detected on qemu68k") {
        val src = """
main {
    sub start() {
        counter(0)
    }

    sub counter(ubyte n) {
        defer n = n + 1
        if n < 10 {
            counter(n + 1)
        }
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "defer"
        errors.errors[0] shouldContain "recursive"
    }

    test("indexed write through a local array in a defer is detected on qemu68k") {
        val src = """
main {
    sub start() {
        counter(0)
    }

    sub counter(ubyte n) {
        ubyte[4] arr
        defer arr[0] = 1
        if n < 10 {
            counter(n + 1)
        }
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "defer"
        errors.errors[0] shouldContain "recursive"
    }

    test("recursive subroutine referenced via on-call dispatch with defer on a local is rejected on qemu68k") {
        val src = """
main {
    sub start() {
        ubyte i = 0
        on i call (helper, other)
    }

    sub helper() {
        ubyte x = 1
        defer x = x + 1
    }

    sub other() {
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "defer"
        errors.errors[0] shouldContain "recursive"
    }

    test("defer touching only constants and globals in a recursive subroutine is allowed on qemu68k") {
        val src = """
main {
    ubyte @shared out

    sub start() {
        counter(0)
    }

    sub counter(ubyte n) {
        const X = 5
        defer out = X
        if n < 10 {
            counter(n + 1)
        }
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
        errors.errors.size shouldBe 0
    }
})