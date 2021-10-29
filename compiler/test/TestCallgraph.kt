package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.compiler.target.C64Target
import prog8.compilerinterface.CallGraph
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCallgraph {
    @Test
    fun testGraphForEmptySubs() {
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

        assertEquals(1, graph.imports.size)
        assertEquals(1, graph.importedBy.size)
        val toplevelModule = result.program.toplevelModule
        val importedModule = graph.imports.getValue(toplevelModule).single()
        assertEquals("string", importedModule.name)
        val importedBy = graph.importedBy.getValue(importedModule).single()
        assertTrue(importedBy.name.startsWith("on_the_fly_test"))

        assertFalse(graph.unused(toplevelModule))
        assertFalse(graph.unused(importedModule))

        val mainBlock = toplevelModule.statements.filterIsInstance<Block>().single()
        for(stmt in mainBlock.statements) {
            val sub = stmt as Subroutine
            assertFalse(sub in graph.calls)
            assertFalse(sub in graph.calledBy)

            if(sub === result.program.entrypoint)
                assertFalse(graph.unused(sub), "start() should always be marked as used to avoid having it removed")
            else
                assertTrue(graph.unused(sub))
        }
    }

    @Test
    fun testGraphForEmptyButReferencedSub() {
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

        assertEquals(1, graph.imports.size)
        assertEquals(1, graph.importedBy.size)
        val toplevelModule = result.program.toplevelModule
        val importedModule = graph.imports.getValue(toplevelModule).single()
        assertEquals("string", importedModule.name)
        val importedBy = graph.importedBy.getValue(importedModule).single()
        assertTrue(importedBy.name.startsWith("on_the_fly_test"))

        assertFalse(graph.unused(toplevelModule))
        assertFalse(graph.unused(importedModule))

        val mainBlock = toplevelModule.statements.filterIsInstance<Block>().single()
        val startSub = mainBlock.statements.filterIsInstance<Subroutine>().single{it.name=="start"}
        val emptySub = mainBlock.statements.filterIsInstance<Subroutine>().single{it.name=="empty"}

        assertTrue(startSub in graph.calls, "start 'calls' (references) empty")
        assertFalse(emptySub in graph.calls, "empty doesn't call anything")
        assertTrue(emptySub in graph.calledBy, "empty gets 'called'")
        assertFalse(startSub in graph.calledBy, "start doesn't get called (except as entrypoint ofc.)")
    }
}
