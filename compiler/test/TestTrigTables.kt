package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import prog8.code.ast.PtNamedNode
import prog8.code.ast.PtNode
import prog8.code.core.ICompilationTarget
import prog8.code.target.Cx16Target
import prog8.code.target.Qemu68kTarget
import prog8.code.target.VMTarget
import prog8tests.helpers.compileText


private fun PtNode.allNames(): List<String> {
    val names = mutableListOf<String>()
    if(this is PtNamedNode)
        names.add(name)
    children.forEach { names.addAll(it.allNames()) }
    return names
}

private val trigSubs = setOf("sin8u", "cos8u", "sin8", "cos8", "sinr8u", "cosr8u", "sinr8", "cosr8")
private val sharedTrigTables = setOf("sincos8u_table", "sincos8_table", "sincosr8u_table", "sincosr8_table")
private val separateTrigTables = setOf("sin8u_table", "cos8u_table", "sin8_table", "cos8_table", "sinr8u_table", "cosr8u_table", "sinr8_table", "cosr8_table")


class TestTrigTables: FunSpec({

    val outputDir = tempdir().toPath()

    // Uses math but no trig routines. @shared defeats const-folding.
    val noTrigSrc = """
        %import math
        main {
            ubyte @shared b1
            ubyte @shared b2
            ubyte @shared result
            sub start() {
                b1 = 10
                b2 = 30
                result = math.diff(b1, b2)
            }
        }
    """

    // Uses all trig routines with simple args so every call gets inlined.
    // Distinct result vars to avoid duplicate-assignment cleanup.
    val trigSrc = """
        %import math
        main {
            ubyte @shared angle
            ubyte @shared radians
            ubyte @shared u1
            ubyte @shared u2
            ubyte @shared ur1
            ubyte @shared ur2
            byte @shared s1
            byte @shared s2
            byte @shared sr1
            byte @shared sr2
            sub start() {
                angle = 64
                radians = 45
                u1 = math.sin8u(angle)
                u2 = math.cos8u(angle)
                s1 = math.sin8(angle)
                s2 = math.cos8(angle)
                ur1 = math.sinr8u(radians)
                ur2 = math.cosr8u(radians)
                sr1 = math.sinr8(radians)
                sr2 = math.cosr8(radians)
            }
        }
    """

    // vasm is not available everywhere; the codegen AST is still produced with assemble=false
    fun compileFor(target: ICompilationTarget, src: String) =
        compileText(target, optimize=true, src, outputDir, writeAssembly=true, assemble=target !is Qemu68kTarget)!!

    val targets = listOf(VMTarget() to sharedTrigTables, Cx16Target() to separateTrigTables, Qemu68kTarget() to separateTrigTables)

    test("unused trig tables are absent from simpleAst") {
        for((target, trigTables) in targets) {
            val names = compileFor(target, noTrigSrc).codegenAst!!.allNames()
            withClue("no trig table should remain on ${target.name}, found: ${names.filter { it.substringAfterLast('.') in trigTables }}") {
                names.none { it.substringAfterLast('.') in trigTables } shouldBe true
            }
            withClue("no trig subroutine should remain on ${target.name}") {
                names.none { it.substringAfterLast('.') in trigSubs } shouldBe true
            }
        }
    }

    test("used trig tables appear exactly once in simpleAst") {
        for((target, trigTables) in targets) {
            val names = compileFor(target, trigSrc).codegenAst!!.allNames()
            for(table in trigTables) {
                withClue("table $table should occur exactly once on ${target.name}, found: ${names.filter { table in it }}") {
                    names.count { table in it } shouldBe 1
                }
            }
            withClue("all trig calls should be inlined on ${target.name}, no trig subroutine should remain") {
                names.none { it.substringAfterLast('.') in trigSubs } shouldBe true
            }
            val otherTables = (sharedTrigTables + separateTrigTables) - trigTables
            withClue("other table layout should be absent on ${target.name}") {
                names.none { it.substringAfterLast('.') in otherTables } shouldBe true
            }
        }
    }
})
