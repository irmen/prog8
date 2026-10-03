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

    test("subroutine with parameters passes them on the stack") {
        val src = """
main {
    sub withp(ubyte a, word b, long c) -> long {
        return b + c + a
    }
    sub start() {
        g = lsb(withp(1, 2, 3))
    }
    ubyte g
}
"""
        val (lines, ir) = compile(src)
        // incoming-only frame: no locals, but the parameters need a frame pointer
        lines.any { it == "link  a5,#0" } shouldBe true
        lines.any { it == "unlk  a5" } shouldBe true
        // narrow parameters are right-justified inside their longword slot (offsets 8/12/16)
        lines.any { it.contains("19(a5)") } shouldBe true     // ubyte at slot 16 + 3
        lines.any { it.contains("14(a5)") } shouldBe true     // word at slot 12 + 2
        lines.any { it.contains("8(a5)") } shouldBe true      // long at slot 8
        // parameters are no longer static storage
        lines.none { "p8v_a:" in it || "p8v_b:" in it || "p8v_c:" in it } shouldBe true
        // the caller pushes every argument into its own slot, then pops the whole area
        lines.count { it == "subq.l  #4,sp" } shouldBe 3
        lines.count { it.startsWith("move.b ") && it.endsWith(",3(sp)") } shouldBe 1
        lines.count { it.startsWith("move.w ") && it.endsWith(",2(sp)") } shouldBe 1
        lines.count { it.startsWith("move.l ") && it.endsWith(",(sp)") } shouldBe 1
        lines.any { it == "lea  12(sp),sp" } shouldBe true
        // IR carries the incoming area size and the frame-slot argument locations
        ir.contains("INCOMING=\"12\"") shouldBe true
        ir.contains("frame:16=") shouldBe true
        val reloaded = IRFileReader().read(ir)
        val sub = reloaded.allSubs().first { it.label.endsWith("p8s_withp") }
        sub.frameSize shouldBe 0
        sub.incomingSize shouldBe 12
    }

    test("many parameters use one cleanup for the whole argument area") {
        val src = """
main {
    sub five(ubyte a, uword b, long c, ubyte d, uword e) -> long {
        return a + b + c + d + e
    }
    sub start() {
        g = lsb(five(1, 2, 3, 4, 5))
    }
    ubyte g
}
"""
        val (lines, ir) = compile(src)
        lines.count { it == "subq.l  #4,sp" } shouldBe 5
        lines.any { it == "lea  20(sp),sp" } shouldBe true     // 20 bytes: the addq form maxes out at 8
        // 5 parameters: slots at 24/20/16/12/8, narrow values right-justified inside their slot
        lines.any { it.contains("27(a5)") } shouldBe true       // ubyte at slot 24 + 3
        lines.any { it.contains("22(a5)") } shouldBe true       // uword at slot 20 + 2
        lines.any { it.contains("16(a5)") } shouldBe true       // long at slot 16
        ir.contains("INCOMING=\"20\"") shouldBe true
    }

    test("subroutine that calls another is framed when it is not recursive") {
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
        val (lines, ir) = compile(src)
        lines.any { it == "link  a5,#-2" } shouldBe true
        lines.none { "p8v_a:" in it } shouldBe true
        ir.contains("FRAMESIZE=\"2\"") shouldBe true
    }

    test("recursive subroutine keeps the static path") {
        // call-live virtual registers still live in the flat program-static regfile (design-doc
        // slice 3), so a subroutine on a call-graph cycle must not be framed yet
        val src = """
main {
    sub rec(ubyte n) -> long {
        if n < 2
            return n
        return rec(n-1) + rec(n-2)
    }
    ubyte @shared g = 1
    sub start() {
        g = lsb(rec(10))
    }
}
"""
        val (lines, _) = compile(src)
        lines.any { it.startsWith("link ") } shouldBe false
        lines.any { "p8s_rec.p8v_n:" in it } shouldBe true
    }

    test("mutually recursive subroutines keep the static path") {
        val src = """
main {
    sub ping(ubyte n) -> long {
        if n == 0
            return 0
        return pong(n-1)
    }
    sub pong(ubyte n) -> long {
        if n == 0
            return 0
        return ping(n-1)
    }
    sub start() {
        g = lsb(ping(4))
    }
    ubyte @shared g
}
"""
        val (lines, _) = compile(src)
        lines.any { it.startsWith("link ") } shouldBe false
    }

    test("indirectly called subroutine keeps the static path") {
        val src = """
main {
    sub t1() { g = 1 }
    sub t2() { g = 2 }
    sub start() {
        on 1 call (
            t1,
            t2
        )
    }
    ubyte @shared g
}
"""
        val (lines, _) = compile(src)
        // t1/t2 are dispatch-table targets: they could be re-entered through the indirect call
        lines.any { it.startsWith("link ") } shouldBe false
    }

    test("defer-referenced local stays static, deferring sub with parameters keeps the static convention") {
        val src = """
main {
    sub leaf() {
        ubyte a = 1
        defer a += 1
    }
    sub parm(ubyte x) {
        defer x += 1
    }
    sub start() {
        leaf()
        parm(1)
    }
}
"""
        val (lines, _) = compile(src)
        // the defer handler is emitted at block scope and reads the parent local, so that local
        // keeps static storage (§11 option 2)
        lines.any { "p8s_leaf.p8v_a:" in it } shouldBe true
        // a defer body that reads a parameter keeps the whole subroutine on the static convention
        lines.any { "p8s_parm.p8v_x:" in it } shouldBe true
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
        // the defer body reads the parent local from a block-scope handler, so that local keeps
        // static storage; the subroutine itself may still get a frame for its per-activation
        // defer mask, but the defer-referenced variable must never move
        lines.any { "p8s_leaf.p8v_a:" in it } shouldBe true
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
