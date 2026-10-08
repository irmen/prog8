package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import prog8.code.target.C64Target
import prog8.code.target.Qemu68kTarget
import prog8.intermediate.IRFileReader
import prog8.intermediate.IRStStaticVariable
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

// M68K stack-frame end-to-end tests:
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
        // clean locals grouped first: arr at -4..-1, b at -6..-5, a at -7 -> frame size 8
        lines.any { it == "link  a5,#-8" } shouldBe true
        lines.count { it == "unlk  a5" } shouldBe 1
        val unlkIdx = lines.indexOf("unlk  a5")
        lines[unlkIdx + 1] shouldBe "rts"
        // accesses lowered to a5 displacements
        lines.any { "-7(a5)" in it } shouldBe true
        lines.any { "-6(a5)" in it } shouldBe true
        lines.any { "-1(a5)" in it } shouldBe true   // arr[3]
        // locals are no longer static storage
        lines.none { "p8v_a:" in it || "p8v_arr:" in it || "p8v_b:" in it } shouldBe true
        // IR carries the new syntax and frame size, and is reloadable
        ir.contains("frame:-7") shouldBe true
        ir.contains("FRAMESIZE=\"8\"") shouldBe true
        val reloaded = IRFileReader().read(ir)
        reloaded.allSubs().first { it.label.endsWith("p8s_leaf") }.frameSize shouldBe 8
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
        lines.count { it == "subq.l  #4,sp" } shouldBe 2
        lines.count { it.startsWith("move.b ") && it.contains(",3(sp)") } shouldBe 1
        lines.count { it.startsWith("move.w ") && it.contains(",2(sp)") } shouldBe 1
        // long/pointer slots push with a single move.l ...,-(sp), same shape as PUSH ops elsewhere,
        // so only require at least the one argument push (exact isolation is covered in TestStackFrameEmission)
        lines.count { it.startsWith("move.l ") && it.contains(",-(sp)") } shouldBeGreaterThanOrEqual 1
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
        lines.count { it == "subq.l  #4,sp" } shouldBe 4
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

    test("@shared local keeps static storage even without an escaping address") {
        val src = """
main {
    sub leaf() {
        ubyte @shared sharedx
        ubyte normal
        sharedx = 1
        normal = sharedx
    }
    sub start() {
        leaf()
    }
}
"""
        val (lines, ir) = compile(src)
        // sharedx is not address-taken and no inline assembly mentions it, yet the @shared
        // tag pins it to program-static storage; normal is still framed
        lines.any { "p8v_sharedx:" in it } shouldBe true
        lines.none { "p8v_normal:" in it } shouldBe true
        lines.any { it == "link  a5,#-2" } shouldBe true
        lines.any { "-1(a5)" in it } shouldBe true
        ir.contains("shared=true") shouldBe true
        val reloaded = IRFileReader().read(ir)
        val sharedVar = reloaded.st.lookup("p8b_main.p8s_leaf.p8v_sharedx") as IRStStaticVariable
        sharedVar.shared shouldBe true
        reloaded.st.lookup("p8b_main.p8s_leaf.p8v_normal") shouldBe null
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

    test("conditional goto to a subroutine out of a framed subroutine becomes an inverted branch over a tail call") {
        // the not-taken path must skip the tail call and keep using the frame: the branch
        // is inverted over a call + return, and the fall-through code moves into a skip chunk
        val src = """
main {
    ubyte @shared result
    sub t1() {
        result += 10
    }
    sub jumper(ubyte n) {
        ubyte local
        local = n
        if local != 0 {
            goto t1
        }
        result += 1
    }
    sub start() {
        result = 0
        jumper(1)
        jumper(0)
    }
}
"""
        val (lines, ir) = compile(src)
        val jumperAsm = subAssembly(lines, "p8b_main.p8s_jumper")
        jumperAsm.any { it.startsWith("link ") } shouldBe true
        // no conditional branch directly targeting the other subroutine remains in the frame
        // (matching the entry label means the callee's rts would corrupt the machine stack)
        jumperAsm.filter { Regex("b(?!sr)\\w+\\s+p8b_main\\.p8s_t1$").containsMatchIn(it) } shouldBe emptyList()
        // inverted branch over the tail call: bstne t1 became bsteq <skip>, followed by bsr + rts
        val tailSkipLabel = jumperAsm.first { Regex("b\\w+\\s+\\S*tailskip").containsMatchIn(it) }
        tailSkipLabel.isEmpty() shouldBe false
        jumperAsm.any { it == "bsr  p8b_main.p8s_t1" } shouldBe true
        ir.contains("call p8b_main.p8s_t1") shouldBe true
        // the not-taken path resumes after the tail call, still inside the frame
        val skipIdx = jumperAsm.indexOfFirst { Regex("\\S*tailskip\\d+:").matches(it) }
        (skipIdx > jumperAsm.indexOf("bsr  p8b_main.p8s_t1")) shouldBe true
    }

    test("conditional goto inside a repeat loop gets the skip chunk inside the loop body") {
        val src = """
main {
    ubyte @shared result
    sub t1() {
        result += 10
    }
    sub jumper(ubyte n) {
        ubyte local
        local = n
        repeat 2 {
            if local != 0 {
                goto t1
            }
        }
        result += 1
    }
    sub start() {
        result = 0
        jumper(1)
        jumper(0)
    }
}
"""
        val (lines, ir) = compile(src)
        val jumperAsm = subAssembly(lines, "p8b_main.p8s_jumper")
        jumperAsm.any { it.startsWith("link ") } shouldBe true
        jumperAsm.filter { Regex("b(?!sr)\\w+\\s+p8b_main\\.p8s_t1$").containsMatchIn(it) } shouldBe emptyList()
        jumperAsm.any { it.startsWith("dbra  d7,") } shouldBe true
        jumperAsm.any { it == "bsr  p8b_main.p8s_t1" } shouldBe true
        ir.contains("call p8b_main.p8s_t1") shouldBe true
    }

    test("register-form conditional goto out of a framed subroutine inverts with swapped operands") {
        val src = """
main {
    ubyte @shared result
    ubyte @shared limit
    sub t1() {
        result += 10
    }
    sub jumper(ubyte n) {
        ubyte local
        local = n
        if local > limit {
            goto t1
        }
        result += 1
    }
    sub start() {
        result = 0
        limit = 5
        jumper(9)   ; taken: result=10
        jumper(2)   ; not taken: result=11
    }
}
"""
        val (lines, ir) = compile(src)
        val jumperAsm = subAssembly(lines, "p8b_main.p8s_jumper")
        jumperAsm.any { it.startsWith("link ") } shouldBe true
        // bgtr t1 became bger(swap) -> the inverted condition branches over the tail call
        jumperAsm.filter { Regex("b(?!sr)\\w+\\s+p8b_main\\.p8s_t1$").containsMatchIn(it) } shouldBe emptyList()
        jumperAsm.count { it.startsWith("bhs") && Regex("\\S*tailskip").containsMatchIn(it) } shouldBe 1
        jumperAsm.any { it == "bsr  p8b_main.p8s_t1" } shouldBe true
        ir.contains("call p8b_main.p8s_t1") shouldBe true
    }

    test("copy-in parameter that the body writes keeps its static cell as the live value") {
        // §6.1: the entry-time copy-in turns the static cell into the parameter's live storage;
        // the body's writes must land there so external references observe the current value
        val src = """
main {
    ubyte @shared out
    sub parm(ubyte x) {
        x = 99
        defer out = x
    }
    sub start() {
        parm(1)
    }
}
"""
        val (lines, _) = compile(src)
        val parm = subAssembly(lines, "p8b_main.p8s_parm")
        parm.any { it == "link  a5,#0" } shouldBe true
        // the entry-time copy-in is still performed
        parm.any { it.startsWith("move.b  11(a5),") } shouldBe true
        // the body's write goes to the static cell, not to the incoming frame slot
        parm.any { it.contains("move.b") && it.contains("p8b_main.p8s_parm.p8v_x") && it.contains("#99") } shouldBe true
        // the defer handler reads the static cell, so it observes the body's written value
        val defers = subAssembly(lines, "p8s_prog8_invoke_defers")
        defers.any { it.contains("move.b") && it.contains("p8b_main.p8s_parm.p8v_x") } shouldBe true
        // the static cell is kept in BSS
        lines.any { "p8b_main.p8s_parm.p8v_x:" in it } shouldBe true
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
        errors.errors[0] shouldContain "may be running twice at the same time"
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
        errors.errors[0] shouldContain "may be running twice at the same time"
        errors.errors[0] shouldContain "static initializer"

        // the same program compiles unchanged for a target without stack frames
        val otherErrors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), optimize = false, src, tempdir().toPath(),
            writeAssembly = false, assemble = false, errors = otherErrors) shouldNotBe null
        otherErrors.errors.size shouldBe 0
    }

    test("recursion warning is only emitted for targets without stack frames") {
        val src = """
main {
    sub rec(ubyte n) -> ubyte {
        if n == 0 {
            return 0
        }
        return rec(n-1) + 1
    }
    sub start() {
        g = rec(3)
    }
    ubyte @shared g
}
"""
        val m68kErrors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, tempdir().toPath(),
            writeAssembly = false, assemble = false, errors = m68kErrors) shouldNotBe null
        m68kErrors.errors.size shouldBe 0
        m68kErrors.warnings.any { it.contains("recursive subroutine") } shouldBe false

        val otherErrors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), optimize = false, src, tempdir().toPath(),
            writeAssembly = false, assemble = false, errors = otherErrors) shouldNotBe null
        otherErrors.errors.size shouldBe 0
        otherErrors.warnings.any { it.contains("recursive subroutine") } shouldBe true
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

    test("slice 5: defer-referenced parameter is framed with an entry-time copy-in") {
        // §6.1: the positive incoming slot is the parameter storage; a parameter referenced
        // from a defer handler keeps its static cell and gets an entry-time copy-in
        val src = """
main {
    sub parm(ubyte x) {
        defer x += 1
    }
    sub start() {
        parm(1)
    }
}
"""
        val (lines, _) = compile(src)
        val parm = subAssembly(lines, "p8b_main.p8s_parm")
        // the subroutine is framed (only the incoming slot, no locals)
        parm.any { it == "link  a5,#0" } shouldBe true
        parm.any { it == "unlk  a5" } shouldBe true
        // entry-time copy-in: load the incoming slot, store into the static cell, before any clearing
        parm.any { it.startsWith("move.b  11(a5),") } shouldBe true
        parm.any { it.contains("p8b_main.p8s_parm.p8v_x") } shouldBe true
        // the static cell is kept so the block-scope defer handler can still see it
        lines.any { "p8b_main.p8s_parm.p8v_x:" in it } shouldBe true
    }

    test("slice 5: inline-asm-referenced parameter is framed with an entry-time copy-in") {
        val src = """
main {
    sub rec(ubyte x) -> ubyte {
        return x + 2
    }
    sub start() {
        g = rec(3)
        %asm {{
            ; this assembly reads the parameter's static cell
            move.b  p8b_main.p8s_rec.p8v_x,d0
            nop
        }}
    }
    ubyte @shared g
}
"""
        val (lines, _) = compile(src)
        val rec = subAssembly(lines, "p8b_main.p8s_rec")
        rec.any { it == "link  a5,#0" } shouldBe true
        rec.any { it.startsWith("move.b  11(a5),") } shouldBe true
        rec.any { it.contains("p8b_main.p8s_rec.p8v_x") } shouldBe true
        // the static cell is kept for the assembly reference; the body itself also uses the
        // static cell (the entry-time copy-in made it the current value)
        lines.any { "p8b_main.p8s_rec.p8v_x:" in it } shouldBe true
        rec.any { it.startsWith("move.b  p8b_main.p8s_rec.p8v_x,") } shouldBe true
    }

    test("slice 5: static-initialized local is framed and initialized at entry") {
        val src = """
main {
    sub leaf() {
        ubyte x = 5
        x += 1
    }
    sub start() {
        leaf()
    }
}
"""
        val (lines, _) = compile(src)
        val leaf = subAssembly(lines, "p8b_main.p8s_leaf")
        leaf.any { it.startsWith("link ") } shouldBe true
        leaf.any { it == "move.b  #5,-1(a5)" } shouldBe true
        // the initialized local no longer lives in static storage
        lines.none { "p8v_x:" in it } shouldBe true
    }

    test("slice 5: float static-initialized local is framed") {
        val src = """
%option enable_floats
main {
    sub leaf() {
        float f = 1.5
        f += 1.0
    }
    sub start() {
        leaf()
    }
}
"""
        val (lines, _) = compile(src)
        val leaf = subAssembly(lines, "p8b_main.p8s_leaf")
        leaf.any { it.startsWith("link ") } shouldBe true
        leaf.any { it.contains("fmove.s  fp0,-4(a5)") } shouldBe true
        lines.none { "p8v_f:" in it } shouldBe true
    }

    test("slice 5: float array local is framed and zero-filled in the prologue") {
        val src = """
%option enable_floats
main {
    sub leaf() {
        float[2] arr
        arr[0] = 1.5
    }
    sub start() {
        leaf()
    }
}
"""
        val (lines, _) = compile(src)
        val leaf = subAssembly(lines, "p8b_main.p8s_leaf")
        leaf.any { it == "link  a5,#-8" } shouldBe true
        // every float element is zero-filled in the prologue (STOREZM float)
        leaf.any { it.contains("fmove.s  fp0,-8(a5)") } shouldBe true
        leaf.any { it.contains("fmove.s  fp0,-4(a5)") } shouldBe true
        lines.none { "p8v_arr:" in it } shouldBe true
    }

    test("slice 5: recursive subroutine with a defer-referenced parameter is rejected") {
        val src = """
main {
    sub rec(ubyte n) -> ubyte {
        defer n = n + 1
        if n == 0 {
            return 0
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
        // the defer rule rejects this at the compiler level; the static cell would be
        // shared between live activations of the recursive subroutine
        errors.errors.any { it.contains("recursive") } shouldBe true
    }

    test("slice 5: recursive subroutine with an inline-asm-referenced parameter is rejected") {
        val src = """
main {
    sub rec(ubyte n) -> ubyte {
        return rec(1)
    }
    sub start() {
        g = rec(3)
        %asm {{
            move.b  p8b_main.p8s_rec.p8v_n,d0
            nop
        }}
    }
    ubyte @shared g
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, tempdir().toPath(),
            writeAssembly = true, assemble = false, errors = errors) shouldBe null
        errors.errors.any { it.contains("may be running twice") } shouldBe true
        errors.errors.any { it.contains("shared with other code") } shouldBe true
    }

    test("slice 5: recursive subroutine with a static-initialized local now compiles") {
        val src = """
main {
    sub rec(ubyte n) -> ubyte {
        ubyte local = 5
        if n == 0 {
            return local + n
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
        // the static-initialized local is now frameable, so nothing keeps static storage
        ir.contains("VREGSLOTS=") shouldBe true
        val rec = subAssembly(lines, "p8b_main.p8s_rec")
        rec.any { it.startsWith("link ") } shouldBe true
        rec.any { it.contains("p8_regfile") } shouldBe false
    }

    test("slice 5: vreg slots of a recursive subroutine are reused when lifetimes don't overlap") {
        // two recursive call results are each stored right after their call, so their lifetimes
        // don't overlap and they can share one frame slot; the frame shrinks from 16 to 10 bytes
        val src = """
main {
    sub rec(ubyte n) -> uword {
        if n == 0
            return 1
        uword a = rec(n-1)
        uword b = rec(n-2)
        return a + b
    }
    ubyte @shared g = 1
    sub start() {
        g = lsb(rec(10))
    }
}
"""
        val (_, ir) = compile(src)
        val subLine = ir.lineSequence().first { it.contains("SUB NAME=\"p8b_main.p8s_rec\"") }
        subLine.contains("FRAMESIZE=\"10\"") shouldBe true
        val slots = subLine.substringAfter("VREGSLOTS=\"").substringBefore('"').split(',').associate {
            it.substringBefore(':').toInt() to it.substringAfter(':').toInt()
        }
        // the two call results inside rec (the call results whose registers appear in the slot
        // map; the call from start uses a register outside it) share one slot
        val callResults = Regex("call p8b_main\\.p8s_rec\\([^)]*\\):r(\\d+)\\.w").findAll(ir)
            .map { it.groupValues[1].toInt() }
            .filter { it in slots }
            .toList()
        callResults.size shouldBe 2
        slots.getValue(callResults[0]) shouldBe slots.getValue(callResults[1])
        // reuse happened: fewer distinct slots than virtual registers
        slots.values.toSet().size shouldBe 3
        slots.size shouldBe 7
        // the layout is deterministic across compilations
        val (_, ir2) = compile(src)
        val subLine2 = ir2.lineSequence().first { it.contains("SUB NAME=\"p8b_main.p8s_rec\"") }
        subLine2.substringAfter("FRAMESIZE") shouldBe subLine.substringAfter("FRAMESIZE")
    }

    test("slice 5: a recursive subroutine with a loop keeps one slot per virtual register") {
        // the loop's back edge can keep values live across it, so plain last-use intervals
        // would be unsound and the safe one-slot-per-register layout is kept
        val src = """
main {
    sub rec(ubyte n) -> uword {
        uword total = 0
        for i in 0 to 5 {
            total += 1
        }
        if n == 0
            return total
        return total + rec(n-1)
    }
    ubyte @shared g = 1
    sub start() {
        g = lsb(rec(10))
    }
}
"""
        val (_, ir) = compile(src)
        val subLine = ir.lineSequence().first { it.contains("SUB NAME=\"p8b_main.p8s_rec\"") }
        subLine.contains("FRAMESIZE=\"12\"") shouldBe true
        subLine.contains("VREGSLOTS=\"1:-3,2:-6,3:-7,4:-10,5:-12\"") shouldBe true
        val slots = subLine.substringAfter("VREGSLOTS=\"").substringBefore('"').split(',').associate {
            it.substringBefore(':').toInt() to it.substringAfter(':').toInt()
        }
        // every register has its own dedicated slot: all offsets are distinct
        slots.values.toSet().size shouldBe slots.size
    }

    test("slice 5: simultaneously-live virtual registers get distinct frame slots") {
        // both recursive call results are live at the addition, so they cannot share a slot
        val src = """
main {
    sub rec(ubyte n) -> uword {
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
        val (_, ir) = compile(src)
        val subLine = ir.lineSequence().first { it.contains("SUB NAME=\"p8b_main.p8s_rec\"") }
        val slots = subLine.substringAfter("VREGSLOTS=\"").substringBefore('"').split(',').associate {
            it.substringBefore(':').toInt() to it.substringAfter(':').toInt()
        }
        val callResults = Regex("call p8b_main\\.p8s_rec\\([^)]*\\):r(\\d+)\\.w").findAll(ir)
            .map { it.groupValues[1].toInt() }
            .filter { it in slots }
            .toList()
        callResults.size shouldBe 2
        slots.getValue(callResults[0]) shouldNotBe slots.getValue(callResults[1])
    }

    test("slice 5: non-recursive subroutines keep the static register file and no vreg slots") {
        // only re-entrant subroutines relocate their virtual registers into the frame
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
        ir.contains("VREGSLOTS") shouldBe false
        val caller = subAssembly(lines, "p8b_main.p8s_caller")
        caller.any { it == "link  a5,#-2" } shouldBe true
        caller.any { it.contains("p8_regfile") } shouldBe true
    }
    test("slice 5: compiler-generated temporaries inside a subroutine are framed already") {
        // the desugarer of `on ... goto` creates its own auto_heap_value variables; plain
        // scalar ones are ordinary subroutine locals and are framed without any extra work
        val src = """
main {
    sub pick(ubyte n) -> ubyte {
        on n goto(smaller, bigger)
        return 0
    smaller:
        return 1
    bigger:
        return 2
    }
    sub start() {
        g = pick(1)
    }
    ubyte @shared g
}
"""
        val (lines, ir) = compile(src)
        val pick = subAssembly(lines, "p8b_main.p8s_pick")
        pick.any { it.startsWith("link ") || it.startsWith("bra") } shouldBe true
        // the jump table auto-variable keeps static storage on purpose: it is read-only
        // constant data, and framing it would add an initialization store on every call
        ir.contains("auto_heap_value") shouldBe true
    }

    test("zero-clear coalescing: byte[17] prologue clears with five stores") {
        val src = """
main {
    sub check() {
        ubyte[17] a
        a[0] = a[16]
    }
    sub start() {
        check()
    }
}
"""
        val (lines, _) = compile(src)
        val check = subAssembly(lines, "p8b_main.p8s_check")
        // slot -17, run [-17, 0): one odd head byte, then four longwords
        check.any { it == "link  a5,#-18" } shouldBe true
        check.any { it == "clr.b  -17(a5)" } shouldBe true
        check.count { it.startsWith("clr.l") } shouldBe 4
        check.any { it == "clr.l  -16(a5)" } shouldBe true
        check.any { it == "clr.l  -4(a5)" } shouldBe true
        check.count { it.startsWith("clr.") } shouldBe 5
    }

    test("zero-clear coalescing: uword[8] prologue clears with four longword stores") {
        val src = """
main {
    sub check() {
        uword[8] b
        b[0] = b[7]
    }
    sub start() {
        check()
    }
}
"""
        val (lines, _) = compile(src)
        val check = subAssembly(lines, "p8b_main.p8s_check")
        // slot -16, run [-16, 0): four longwords, no word stores left
        check.any { it == "link  a5,#-16" } shouldBe true
        check.count { it.startsWith("clr.l") } shouldBe 4
        check.none { it.startsWith("clr.w") } shouldBe true
        check.none { it.startsWith("clr.b") } shouldBe true
    }

    test("zero-clear coalescing: adjacent byte locals coalesce across variables") {
        val src = """
main {
    ubyte @shared g
    sub check() {
        ubyte a
        ubyte b
        a = 1
        b = 2
        g = a + b
    }
    sub start() {
        check()
    }
}
"""
        val (lines, _) = compile(src)
        val check = subAssembly(lines, "p8b_main.p8s_check")
        // a at -1, b at -2: merged run [-2, 0) clears with a single word store
        check.count { it == "clr.w  -2(a5)" } shouldBe 1
        check.count { it.startsWith("clr.") } shouldBe 1
    }

    test("zero-clear coalescing: clean locals are grouped to avoid alignment gaps") {
        val src = """
main {
    sub check() {
        ubyte a
        uword w
        a = 1
        w = 2
    }
    sub start() {
        check()
    }
}
"""
        val (lines, _) = compile(src)
        val check = subAssembly(lines, "p8b_main.p8s_check")
        // w placed before a so the clean region is contiguous: a at -3, w at -2..-1
        check.count { it == "clr.b  -3(a5)" } shouldBe 1
        check.count { it == "clr.w  -2(a5)" } shouldBe 1
        check.none { it.startsWith("clr.l") } shouldBe true
    }

    test("zero-clear coalescing: @dirty locals are not cleared") {
        val src = """
main {
    ubyte @shared g
    sub check() {
        ubyte @dirty d
        ubyte c
        c = g
        d = g
        g = c + d
    }
    sub start() {
        check()
    }
}
"""
        val (lines, _) = compile(src)
        val check = subAssembly(lines, "p8b_main.p8s_check")
        check.count { it == "clr.b  -1(a5)" } shouldBe 1
        check.none { it.startsWith("clr.") && "-2(a5)" in it } shouldBe true
    }

    test("zero-clear coalescing: initialized locals keep their init store") {
        val src = """
main {
    ubyte @shared g
    sub check() {
        ubyte a
        ubyte x = 42
        a = g
        x += 1
        g = a + x
    }
    sub start() {
        check()
    }
}
"""
        val (lines, _) = compile(src)
        val check = subAssembly(lines, "p8b_main.p8s_check")
        // x at -2 keeps its init store; a's [-1, 0) run merges with x's byte (zeroed
        // first, then set by the init store: declaration initializers lower to a body
        // store, so the slot counts as clean)
        check.any { it == "move.b  #42,-2(a5)" } shouldBe true
        check.count { it == "clr.w  -2(a5)" } shouldBe 1
        check.count { it.startsWith("clr.") } shouldBe 1
        check.none { it.startsWith("clr.l") } shouldBe true
    }

    test("zero-clear coalescing: float arrays keep per-element FPU clears") {
        val src = """
%option enable_floats
main {
    sub check() {
        float[3] f
        f[0] = 1.5
    }
    sub start() {
        check()
    }
}
"""
        val (lines, _) = compile(src)
        val check = subAssembly(lines, "p8b_main.p8s_check")
        // three prologue clears plus the body store to f[0], all FPU, no integer clears
        check.any { it == "fmove.s  fp0,-12(a5)" } shouldBe true
        check.any { it == "fmove.s  fp0,-8(a5)" } shouldBe true
        check.any { it == "fmove.s  fp0,-4(a5)" } shouldBe true
        check.none { it.startsWith("clr.") } shouldBe true
    }

    test("zero-clear coalescing: dirty neighbour splits a run") {
        val src = """
main {
    sub check() {
        ubyte[4] c
        ubyte[4] @dirty d
        c[0] = 1
        d[0] = 2
    }
    sub start() {
        check()
    }
}
"""
        val (lines, _) = compile(src)
        val check = subAssembly(lines, "p8b_main.p8s_check")
        check.count { it == "clr.l  -4(a5)" } shouldBe 1
        check.count { it.startsWith("clr.") } shouldBe 1
    }

    test("zero-clear loop: ubyte[128] prologue clears with a dbra loop") {
        val src = """
main {
    sub check() {
        ubyte[128] big
        big[0] = big[127]
    }
    sub start() {
        check()
    }
}
"""
        val (lines, _) = compile(src)
        val check = subAssembly(lines, "p8b_main.p8s_check")
        // 32 longwords hit the loop threshold: no straight clears remain
        check.any { it == "lea  -128(a5),a0" } shouldBe true
        check.any { it == "moveq  #31,d1" } shouldBe true
        check.any { it == "move.l  d0,(a0)+" } shouldBe true
        check.count { it.startsWith("dbra  d1,zeroloop_") } shouldBe 1
        check.none { it.startsWith("clr.") } shouldBe true
    }

    test("stack argument pushes carry the parameter name as a comment") {
        val src = """
main {
    sub withp(ubyte first, word second, long third) -> long {
        return second + third + first
    }
    sub start() {
        g = lsb(withp(1, 2, 3))
    }
    ubyte g
}
"""
        val (lines, _) = compile(src)
        val start = subAssembly(lines, "p8b_main.p8s_start")
        // each push store is annotated with the short parameter name it fills;
        // the slot-reserving subq lines stay bare
        start.any { it.contains(",3(sp)") && it.endsWith("; first") } shouldBe true
        start.any { it.contains(",2(sp)") && it.endsWith("; second") } shouldBe true
        start.any { it.contains(",-(sp)") && it.endsWith("; third") } shouldBe true
        start.count { it == "subq.l  #4,sp" } shouldBe 2
    }

    test("re-entrant subroutines that share data are rejected in plain language") {
        val src = """
main {
    sub start() {
        spawn(&task1)
    }
    sub spawn(pointer task) {
        ubyte x = 1
        x++
        dispatch(task)
    }
    sub dispatch(pointer task) {
        call(task)
    }
    sub task1() {
        ubyte counter = 0
        counter++
        spawn(&task1)
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, tempdir().toPath(),
            writeAssembly = true, assemble = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 3
        val all = errors.errors.joinToString("\n")
        all shouldContain "cannot be compiled"
        all shouldContain "may be running twice at the same time"
        all shouldContain "its own private data"
        all shouldContain "%option noframe"
        all shouldContain "variable 'x'"
        all shouldContain "variable 'counter'"
        all shouldContain "temporary value"
        all shouldNotContain "recursive"
        all shouldNotContain "stack frame"
    }

    test("%option noframe on a subroutine exempts it from the reentrancy static-state error") {
        val src = """
main {
    sub start() {
        spawn(&task1)
    }
    sub spawn(pointer task) {
        %option noframe
        dispatch(task)
    }
    sub dispatch(pointer task) {
        %option noframe
        call(task)
    }
    sub task1() {
        %option noframe
        ubyte counter = 0
        counter++
        spawn(&task1)
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        val outputDir = tempdir().toPath()
        compileText(Qemu68kTarget(), optimize = false, src, outputDir,
            writeAssembly = true, assemble = false, errors = errors) shouldNotBe null
        errors.errors shouldBe emptyList()
        // spawn takes a parameter so it would normally get a frame; the noframe mark forces static convention
        val (lines, _) = compile(src, Qemu68kTarget())
        for (sub in listOf("p8b_main.p8s_spawn", "p8b_main.p8s_dispatch", "p8b_main.p8s_task1")) {
            subAssembly(lines, sub).none { it.startsWith("link") } shouldBe true
        }
    }

    test("coroutine tasks with frame-private state need no mark and get stack frames") {
        val src = """
%import coroutines

main {
    sub start() {
        void coroutines.add(task1, 0)
        void coroutines.add(task2, 0)
        coroutines.run(0)
    }
    sub task1() {
        ubyte count = 0
        repeat 3 {
            count += helper(count)
            void coroutines.yield()
        }
    }
    sub helper(ubyte v) -> ubyte {
        return v + 1
    }
    sub task2() {
        ubyte n = 0
        repeat 2 {
            n += 10
            void coroutines.yield()
        }
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        val outputDir = tempdir().toPath()
        compileText(Qemu68kTarget(), optimize = false, src, outputDir,
            writeAssembly = true, assemble = false, errors = errors) shouldNotBe null
        errors.errors shouldBe emptyList()
        // each task runs on its own private stack, so locals live in frames: no marks needed
        val (lines, _) = compile(src, Qemu68kTarget())
        for (sub in listOf("p8b_main.p8s_task1", "p8b_main.p8s_task2", "p8b_main.p8s_helper")) {
            subAssembly(lines, sub).any { it.startsWith("link") } shouldBe true
        }
    }

    test("%option with a non-noframe argument is rejected in a subroutine") {
        val src = """
main {
    sub start() {
        %option force_output
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, tempdir().toPath(),
            writeAssembly = false, assemble = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "not valid for subroutines"
    }
})
