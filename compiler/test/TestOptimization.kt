package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.compiler.target.C64Target
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText
import kotlin.test.assertEquals
import kotlin.test.assertSame

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestOptimization {
    @Test
    fun testRemoveEmptySubroutineExceptStart() {
        val sourcecode = """
            main {
                sub start() {
                }
                sub empty() {
                    ; going to be removed
                }
            }
        """
        val result = compileText(C64Target, true, sourcecode).assertSuccess()
        val toplevelModule = result.programAst.toplevelModule
        val mainBlock = toplevelModule.statements.single() as Block
        assertEquals(1, mainBlock.statements.size)
        val startSub = mainBlock.statements[0] as Subroutine
        assertSame(result.programAst.entrypoint, startSub)
        assertEquals("start", startSub.name)
        assertEquals(0, startSub.statements.size)
    }

    @Test
    fun testDontRemoveEmptySubroutineIfItsReferenced() {
        val sourcecode = """
            main {
                sub start() {
                    uword xx = &empty
                    xx++
                }
                sub empty() {
                    ; should not be removed
                }
            }
        """
        val result = compileText(C64Target, true, sourcecode).assertSuccess()
        val toplevelModule = result.programAst.toplevelModule
        val mainBlock = toplevelModule.statements.single() as Block
        val startSub = mainBlock.statements[0] as Subroutine
        val emptySub = mainBlock.statements[1] as Subroutine
        assertSame(result.programAst.entrypoint, startSub)
        assertEquals("start", startSub.name)
        assertEquals("empty", emptySub.name)
        assertEquals(0, emptySub.statements.size)
    }
}
