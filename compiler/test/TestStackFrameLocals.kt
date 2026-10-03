package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.C64Target
import prog8.code.target.Qemu68kTarget
import prog8.intermediate.IRFileReader
import prog8tests.helpers.ErrorReporterForTests
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

    // the assembly lines of a single subroutine, using the subroutine boundary markers the
    // asm peephole optimizer relies on
    fun subAssembly(lines: List<String>, subLabel: String): List<String> {
        val start = lines.indexOfFirst { it.startsWith("; ---- Subroutine:") && it.contains(subLabel) }
        require(start >= 0) { "no assembly found for subroutine $subLabel" }
        val end = lines.indexOfFirst { it.startsWith("; End of subroutine:") && it.contains(subLabel) }
        return lines.subList(start, end)
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

    test("recursive subroutine gets a frame with per-activation virtual registers") {
        // slice 3: a subroutine on a call-graph cycle moves its virtual registers into the frame,
        // so two live activations no longer share the flat program-static regfile
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
        val (lines, ir) = compile(src)
        val rec = subAssembly(lines, "p8b_main.p8s_rec")
        rec.any { it.startsWith("link ") } shouldBe true
        rec.any { it == "unlk  a5" } shouldBe true
        // its parameter arrives in the incoming argument area, its temporary value in the frame
        ir.contains("VREGSLOTS=") shouldBe true
        rec.any { it.contains("(a5)") && it.startsWith("move") } shouldBe true
        // the recursive subroutine's own body never touches the static register file
        rec.any { it.contains("p8_regfile") } shouldBe false
    }

    test("mutually recursive subroutines both get frames") {
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
        val ping = subAssembly(lines, "p8b_main.p8s_ping")
        val pong = subAssembly(lines, "p8b_main.p8s_pong")
        ping.any { it.startsWith("link ") } shouldBe true
        pong.any { it.startsWith("link ") } shouldBe true
        // both keep their state per-activation, so neither uses the static register file
        ping.any { it.contains("p8_regfile") } shouldBe false
        pong.any { it.contains("p8_regfile") } shouldBe false
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

    test("slice 3: tail call out of a framed subroutine becomes a call and a return") {
        // a framed subroutine cannot branch out to another subroutine: its `link` moved the stack
        // pointer, so the other subroutine's `rts` would return into the local area
        val src = """
main {
    sub target(ubyte n) -> ubyte {
        return n + 1
    }
    sub caller(ubyte n) -> ubyte {
        ubyte local = n
        if local == 0 {
            return 0
        }
        return target(local)
    }
    sub start() {
        g = caller(3)
    }
    ubyte @shared g
}
"""
        val (lines, ir) = compile(src)
        val callerAsm = subAssembly(lines, "p8b_main.p8s_caller")
        callerAsm.any { it.startsWith("link ") } shouldBe true
        // no outgoing branch to another subroutine is left inside the frame
        callerAsm.any { it.startsWith("bra") } shouldBe false
        callerAsm.any { it.startsWith("bsr") } shouldBe true
        ir.contains("call p8b_main.p8s_target") shouldBe true
    }

    test("slice 3: recursive subroutine with an address-taken local is rejected") {
        val src = """
main {
    sub rec(ubyte n) -> ubyte {
        ubyte buf = 1
        pointer p = &buf
        if n == 0 {
            return 1
        }
        if p != 0 {
            return rec(n-1)
        }
        return rec(n-1)
    }
    sub start() {
        g = rec(3)
    }
    ubyte @shared g
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, tempdir().toPath(),
            writeAssembly = true, assemble = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "recursive subroutine"
        errors.errors[0] shouldContain "address is taken"
    }

    test("slice 3: recursive subroutine that keeps static storage is rejected, other targets are not") {
        val src = """
main {
    sub rec(ubyte n) -> ubyte {
        ubyte[4] buf = [1, 2, 3, 4]
        buf[0] = n
        if n == 0 {
            return buf[0]
        }
        return rec(n-1) + buf[0]
    }
    sub start() {
        g = rec(3)
    }
    ubyte @shared g
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, tempdir().toPath(),
            writeAssembly = true, assemble = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "recursive subroutine"
        errors.errors[0] shouldContain "static initializer"

        // the same program compiles unchanged for a target without stack frames
        val otherErrors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), optimize = false, src, tempdir().toPath(),
            writeAssembly = false, assemble = false, errors = otherErrors) shouldNotBe null
        otherErrors.errors.size shouldBe 0
    }

    test("slice 3: defer in a recursive subroutine is rejected on m68k only") {
        val src = """
main {
    sub rec(ubyte n) -> ubyte {
        ubyte hits = 0
        defer hits = hits + 1
        if n == 0 {
            return hits
        }
        return rec(n-1)
    }
    sub start() {
        g = rec(3)
    }
    ubyte @shared g
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, tempdir().toPath(),
            writeAssembly = false, assemble = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "defer in a recursive subroutine"

        val otherErrors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), optimize = false, src, tempdir().toPath(),
            writeAssembly = false, assemble = false, errors = otherErrors) shouldNotBe null
        otherErrors.errors.size shouldBe 0
    }

    test("slice 3: recursive subroutine using only its own state is accepted") {
        val src = """
main {
    sub rec(ubyte n) -> ubyte {
        ubyte local = n + 1
        if n == 0 {
            return local
        }
        return rec(n-1) + local
    }
    sub start() {
        g = rec(3)
    }
    ubyte @shared g
}
"""
        val (lines, ir) = compile(src)
        ir.contains("VREGSLOTS=") shouldBe true
        val rec = subAssembly(lines, "p8b_main.p8s_rec")
        rec.any { it.startsWith("link ") } shouldBe true
        rec.any { it.contains("p8_regfile") } shouldBe false
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
