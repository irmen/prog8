package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import prog8.compiler.astprocessing.M68kSaveRules
import prog8tests.helpers.resourcesDir
import kotlin.io.path.*

class TestM68kSaveRules: FunSpec({

    test("write to a callee-saved register without a save is reported") {
        M68kSaveRules.unsavedWrites("moveq #0,d2\nrts", emptySet()) shouldBe setOf("d2")
    }

    test("push and pop around the write is accepted") {
        M68kSaveRules.unsavedWrites("move.l d2,-(sp)\nmoveq #0,d2\nmove.l (sp)+,d2\nrts", emptySet()).shouldBeEmpty()
    }

    test("movem save and restore lists are expanded") {
        M68kSaveRules.unsavedWrites("movem.l d2-d3/a2,-(sp)\nmoveq #0,d3\nlea x,a2\nmovem.l (sp)+,d2-d3/a2", emptySet()).shouldBeEmpty()
    }

    test("a pop of a register that was never pushed is reported") {
        M68kSaveRules.unsavedWrites("move.l (sp)+,d3\nrts", emptySet()) shouldBe setOf("d3")
    }

    test("result registers may be written without a save") {
        M68kSaveRules.unsavedWrites("moveq #0,d2\nrts", setOf("d2")).shouldBeEmpty()
    }

    test("scratch registers may be written freely") {
        M68kSaveRules.unsavedWrites("move.b d0,d1\nlea x,a0\nadd.l d0,a1\nfmove.x fp0,fp1\nrts", emptySet()).shouldBeEmpty()
    }

    test("compares and branches do not write") {
        M68kSaveRules.unsavedWrites("cmp.l d2,d0\nbeq.s x\ntst.l d3\nbra.s x\nx: rts", emptySet()).shouldBeEmpty()
    }

    test("stores to memory do not write the source registers") {
        M68kSaveRules.unsavedWrites("move.l d2,(a0)\nmovem.l d2-d3,-(a0)\nrts", emptySet()).shouldBeEmpty()
    }

    test("auto-increment and decrement write the address register") {
        M68kSaveRules.unsavedWrites("move.b d0,(a3)+", emptySet()) shouldBe setOf("a3")
        M68kSaveRules.unsavedWrites("move.b d0,-(a4)", emptySet()) shouldBe setOf("a4")
    }

    test("reserved registers D7 and A5 must not be changed") {
        M68kSaveRules.unsavedWrites("moveq #0,d7\nmove.l #1,a5\nrts", emptySet()) shouldBe setOf("d7", "a5")
    }

    test("dbra writes its counter") {
        M68kSaveRules.unsavedWrites("dbra d4,x\nx: rts", emptySet()) shouldBe setOf("d4")
    }

    test("labels in front of instructions are ignored") {
        M68kSaveRules.unsavedWrites(".loop:  moveq #0,d2\nrts", emptySet()) shouldBe setOf("d2")
    }

    test("floating point callee-saved registers are checked") {
        M68kSaveRules.unsavedWrites("fmove.x fp0,fp2\nrts", emptySet()) shouldBe setOf("fp2")
        M68kSaveRules.unsavedWrites("fmove.x fp2,-(sp)\nfmove.x fp0,fp2\nfmove.x (sp)+,fp2\nrts", emptySet()).shouldBeEmpty()
    }

    test("an OS library call through A6 counts as a D6 write") {
        M68kSaveRules.unsavedWrites("move.l sys.DOSBase,a6\njsr -942(a6)\nrts", emptySet()) shouldBe setOf("d6")
        M68kSaveRules.unsavedWrites("move.l d6,-(sp)\nmove.l a6,-(sp)\nmove.l 4.w,a6\njsr exec.CopyMem(a6)\nmove.l (sp)+,a6\nmove.l (sp)+,d6\nrts", emptySet())
            .shouldBeEmpty()
        M68kSaveRules.unsavedWrites("move.l d6,-(sp)\nmove.l sys.GfxBase,a6\njmp -270(a6)\nmove.l (sp)+,d6\nrts", emptySet())
            .shouldBeEmpty()
        // a plain label call is not an OS vector call
        M68kSaveRules.unsavedWrites("jsr sys.memclear\njmp label\nrts", emptySet()).shouldBeEmpty()
    }

    test("link and unlk write their register") {
        M68kSaveRules.unsavedWrites("link a5,#-8\nmove.l a5,d0\nunlk a5\nrts", emptySet()) shouldBe setOf("a5")
    }

    test("m68k library asm routines save every callee-saved register they change") {
        val prog8lib = resourcesDir / "prog8lib"
        val files = listOf("amiga500", "amiga1200", "qemu68k").flatMap { (prog8lib / it).listDirectoryEntries("*.p8") } +
                prog8lib.listDirectoryEntries("shared_m68k_*.p8") +
                prog8lib.listDirectoryEntries("shared_amiga_*.p8")

        val checked = mutableListOf<String>()
        val violations = mutableListOf<String>()
        for (file in files) {
            for (sub in asmOnlySubroutines(file.readText())) {
                checked += sub.name
                val exempt = allowed.filter { it.second == sub.name }.map { it.first }
                val unsaved = M68kSaveRules.unsavedWrites(sub.asm, sub.returns) - exempt.toSet()
                if (unsaved.isNotEmpty()) violations += "${file.name}:${sub.name} changes ${unsaved.sorted()}"
            }
        }
        // sanity: the extraction must see routines from several files, including ones only reached by the library
        checked.shouldNotBeEmpty()
        checked shouldContain "die"
        checked shouldContain "rndw"
        checked shouldContain "init_system_phase2"
        if (violations.isNotEmpty())
            throw AssertionError("m68k library register-rule violations:\n" + violations.joinToString("\n"))
    }
})

// (register, routine) pairs whose register change is deliberate.
private val allowed = setOf(
    "a3" to "putcharproc",                  // exec RawDoFmt callback: A3 is the output pointer by protocol
    "d2" to "init_system_phase2",           // startup only: clears every register on purpose
    "d3" to "init_system_phase2",
    "d4" to "init_system_phase2",
    "d5" to "init_system_phase2",
    "d6" to "init_system_phase2",
    "d7" to "init_system_phase2",
    "a2" to "init_system_phase2",
    "a3" to "init_system_phase2",
    "a4" to "init_system_phase2",
    "a5" to "init_system_phase2",
    "a2" to "die",                          // never returns: shows the alert, then jumps to sys.exit
    "a3" to "die",
    "d2" to "die",
    "d3" to "die",
    "d4" to "die",
    "d6" to "die",
    "d6" to "reset_system",                 // never returns: resets or reboots the machine
    "d6" to "no_ks20_alert",                // never returns: shows the alert, then jumps to sys.exit
)

private class AsmOnlySub(val name: String, val asm: String, val returns: Set<String>)

private val m68kRegisters = Regex("^(d[0-7]|a[0-7]|fp[0-7])$")
private val declaration = Regex("""^\s*(?:(?:private|public|inline)\s+)*(?:asmsub|sub)\s+(\w+)\s*\(""", RegexOption.MULTILINE)
private val asmBlock = Regex("""%asm\s*\{\{(.*?)\}\}""", RegexOption.DOT_MATCHES_ALL)

// Subroutines whose body is only inline assembly (plus %option directives), the case the AstChecker rule covers.
private fun asmOnlySubroutines(source: String): List<AsmOnlySub> {
    val result = mutableListOf<AsmOnlySub>()
    for (match in declaration.findAll(source)) {
        val open = source.indexOf('{', match.range.last)
        if (open < 0) continue
        var depth = 0
        var close = open
        while (close < source.length) {
            if (source[close] == '{') depth++
            if (source[close] == '}') {
                depth--
                if (depth == 0) break
            }
            close++
        }
        val header = source.substring(match.range.first, open)
        val body = source.substring(open + 1, close)
        val blocks = asmBlock.findAll(body).map { it.groupValues[1] }.toList()
        val remainder = body.replace(asmBlock, "").lines()
            .map { it.substringBefore(';').trim() }
            .filter { it.isNotEmpty() && !it.startsWith("%option") }
        if (blocks.isEmpty() || remainder.isNotEmpty()) continue
        val returns = if ("->" in header) {
            Regex("@(\\w+)").findAll(header.substringAfter("->")).map { it.groupValues[1].lowercase() }
                .filter { m68kRegisters.matches(it) }.toSet()
        } else emptySet()
        result += AsmOnlySub(match.groupValues[1], blocks.joinToString("\n"), returns)
    }
    return result
}
