package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import prog8.code.target.C64Target
import prog8.code.target.Qemu68kTarget
import prog8.intermediate.IRFileReader
import prog8tests.helpers.compileText

// Vertical Slice Prototype end-to-end tests (m68k-stack-memory-model.md §17):
// stack-frame locals for frameable leaf subroutines on m68k targets.
class TestStackFrameLocals : FunSpec({

    fun compile(src: String, target: prog8.code.core.ICompilationTarget = Qemu68kTarget()): Pair<List<String>, String> {
        val outputDir = tempdir().toPath()
        compileText(target, optimize = false, src, outputDir, writeAssembly = true, assemble = false)
        val asmFile = outputDir.toFile().listFiles()!!.single { it.name.endsWith(".asm") }
        val irFile = outputDir.toFile().listFiles()!!.singleOrNull { it.name.endsWith(".p8ir") }
        return asmFile.readText().lines().map { it.trim() } to (irFile?.readText() ?: "")
    }

    test("frameable leaf subroutine gets an a5 frame") {
        val src = """
main {
    ubyte gv = 3
    sub leaf() {
        ubyte a
        word b
        ubyte[4] arr
        a = gv
        b = a * 2
        arr[3] = a
    }
    sub start() {
        leaf()
    }
}
"""
        val (lines, ir) = compile(src)
        // locals laid out below a5: a at -1, arr at -8..-5, b at -10..-9 -> frame size 10
        lines.any { it == "link  a5,#-10" } shouldBe true
        lines.count { it == "unlk  a5" } shouldBe 1
        val unlkIdx = lines.indexOf("unlk  a5")
        lines[unlkIdx + 1] shouldBe "rts"
        // accesses lowered to a5 displacements
        lines.any { "-1(a5)" in it } shouldBe true
        lines.any { "-10(a5)" in it } shouldBe true
        lines.any { "-5(a5)" in it } shouldBe true   // arr[3]
        // locals are no longer static storage
        lines.none { "p8v_a:" in it || "p8v_arr:" in it || "p8v_b:" in it } shouldBe true
        // IR carries the new syntax and frame size, and is reloadable
        ir.contains("frame:-1") shouldBe true
        ir.contains("FRAMESIZE=\"10\"") shouldBe true
        val reloaded = IRFileReader().read(ir)
        reloaded.allSubs().first { it.label.endsWith("p8s_leaf") }.frameSize shouldBe 10
    }

    test("subroutine with parameters keeps the static path") {
        val src = """
main {
    sub withparam(ubyte v) {
        ubyte a
        a = v
    }
    sub start() {
        withparam(1)
    }
}
"""
        val (lines, _) = compile(src)
        lines.any { it.startsWith("link ") } shouldBe false
        lines.any { "-1(a5)" in it } shouldBe false
    }

    test("subroutine that calls another is not framed") {
        val src = """
main {
    ubyte gv = 2
    sub inner() { gv += 1 }
    sub caller() {
        ubyte a
        inner()
        a = gv
    }
    sub start() {
        caller()
    }
}
"""
        val (lines, _) = compile(src)
        lines.any { it.startsWith("link ") } shouldBe false
    }

    test("address-taken local stays static, rest of the frame still works") {
        val src = """
main {
    sub leaf() {
        ubyte x
        ^^ubyte p = &x
        p^^ = 42
        x += p^^
    }
    sub start() {
        leaf()
    }
}
"""
        val (lines, _) = compile(src)
        // x is address-taken: keeps its static storage; p (a pointer scalar) gets framed
        lines.any { "p8v_x:" in it } shouldBe true
        lines.any { it == "link  a5,#-4" } shouldBe true
        lines.any { "-4(a5)" in it } shouldBe true
    }

    test("defer-referenced subroutine is not framed") {
        val src = """
main {
    sub leaf() {
        ubyte a = 1
        defer a += 1
    }
    sub start() {
        leaf()
    }
}
"""
        val (lines, _) = compile(src)
        // DeferProcessor adds handler-call instructions -> the sub contains calls -> never frameable
        lines.any { it.startsWith("link ") } shouldBe false
    }

    test("oversized local array keeps static storage while other locals are framed") {
        val src = """
main {
    sub leaf() {
        ubyte small
        ubyte[17000] big
        small = 1
    }
    sub start() {
        leaf()
    }
}
"""
        val (lines, _) = compile(src)
        lines.any { it == "link  a5,#-2" } shouldBe true   // only `small` is framed
        lines.any { "p8v_big" in it } shouldBe true        // big array remains static
    }

    test("other targets are unaffected") {
        val src = """
main {
    sub leaf() {
        ubyte a
        a = 5
    }
    sub start() {
        leaf()
    }
}
"""
        val (lines, _) = compile(src, C64Target())
        lines.any { "link " in it } shouldBe false
        lines.any { "(a5)" in it } shouldBe false
    }
})
