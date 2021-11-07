package prog8tests

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.compiler.target.C64Target
import prog8.compilerinterface.CallGraph
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText

class TestCallgraph: FunSpec({
    test("testGraphForEmptySubs") {
        val sourcecode = """
            %import string
            main {
                sub start() {
                }
                sub empty() {
                }
            }
        """
        val result = compileText(C64Target, false, sourcecode).assertSuccess()
        val graph = CallGraph(result.program)

        graph.imports.size shouldBe 1
        graph.importedBy.size shouldBe 1
        val toplevelModule = result.program.toplevelModule
        val importedModule = graph.imports.getValue(toplevelModule).single()
        importedModule.name shouldBe "string"
        val importedBy = graph.importedBy.getValue(importedModule).single()
        importedBy.name.startsWith("on_the_fly_test") shouldBe true

        graph.unused(toplevelModule) shouldBe false
        graph.unused(importedModule) shouldBe false

        val mainBlock = toplevelModule.statements.filterIsInstance<Block>().single()
        for(stmt in mainBlock.statements) {
            val sub = stmt as Subroutine
            graph.calls shouldNotContainKey sub
            graph.calledBy shouldNotContainKey sub

            if(sub === result.program.entrypoint)
                withClue("start() should always be marked as used to avoid having it removed") {
                    graph.unused(sub) shouldBe false
                }
            else
                graph.unused(sub) shouldBe true
        }
    }

    test("testGraphForEmptyButReferencedSub") {
        val sourcecode = """
            %import string
            main {
                sub start() {
                    uword xx = &empty
                    xx++
                }
                sub empty() {
                }
            }
        """
        val result = compileText(C64Target, false, sourcecode).assertSuccess()
        val graph = CallGraph(result.program)

        graph.imports.size shouldBe 1
        graph.importedBy.size shouldBe 1
        val toplevelModule = result.program.toplevelModule
        val importedModule = graph.imports.getValue(toplevelModule).single()
        importedModule.name shouldBe "string"
        val importedBy = graph.importedBy.getValue(importedModule).single()
        importedBy.name.startsWith("on_the_fly_test") shouldBe true

        graph.unused(toplevelModule) shouldBe false
        graph.unused(importedModule) shouldBe false

        val mainBlock = toplevelModule.statements.filterIsInstance<Block>().single()
        val startSub = mainBlock.statements.filterIsInstance<Subroutine>().single{it.name=="start"}
        val emptySub = mainBlock.statements.filterIsInstance<Subroutine>().single{it.name=="empty"}

        withClue("start 'calls' (references) empty") {
            graph.calls shouldContainKey startSub
        }
        withClue("empty doesn't call anything") {
            graph.calls shouldNotContainKey emptySub
        }
        withClue("empty gets 'called'") {
            graph.calledBy shouldContainKey emptySub
        }
        withClue( "start doesn't get called (except as entrypoint ofc.)") {
            graph.calledBy shouldNotContainKey startSub
        }
    }
})
