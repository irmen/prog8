package prog8tests

import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.statements.Block
import prog8.ast.statements.Return
import prog8.ast.statements.Subroutine
import prog8.compiler.target.C64Target
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
        val startSub = mainBlock.statements.single() as Subroutine
        assertSame(result.programAst.entrypoint, startSub)
        assertEquals("start", startSub.name, "only start sub should remain")
        assertTrue(startSub.statements.single() is Return, "compiler has inserted return in empty subroutines")
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
        assertTrue(emptySub.statements.single() is Return, "compiler has inserted return in empty subroutines")
    }
}
