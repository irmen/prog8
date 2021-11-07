package prog8tests

import io.kotest.core.spec.style.FunSpec
import prog8.ast.GlobalNamespace
import prog8.ast.base.ParentSentinel
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.*
import prog8.compiler.target.C64Target
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText
import kotlin.test.*


class TestScoping: FunSpec({

    test("testModulesParentIsGlobalNamespace") {
        val src = """
            main {
                sub start() {
                }
            }
        """

        val result = compileText(C64Target, false, src, writeAssembly = false).assertSuccess()
        val module = result.program.toplevelModule
        assertIs<GlobalNamespace>(module.parent)
        assertSame(result.program, module.program)
        assertIs<ParentSentinel>(module.parent.parent)
    }

    test("testAnonScopeVarsMovedIntoSubroutineScope") {
        val src = """
            main {
                sub start() {
                    repeat {
                        ubyte xx = 99
                        xx++
                    }
                }
            }
        """

        val result = compileText(C64Target, false, src, writeAssembly = false).assertSuccess()
        val module = result.program.toplevelModule
        val mainBlock = module.statements.single() as Block
        val start = mainBlock.statements.single() as Subroutine
        val repeatbody = start.statements.filterIsInstance<RepeatLoop>().single().body
        assertFalse(mainBlock.statements.any { it is VarDecl }, "no vars moved to main block")
        val subroutineVars = start.statements.filterIsInstance<VarDecl>()
        assertEquals(1, subroutineVars.size, "var from repeat anonscope must be moved up to subroutine")
        assertEquals("xx", subroutineVars[0].name)
        assertFalse(repeatbody.statements.any { it is VarDecl }, "var should have been removed from repeat anonscope")
        val initassign = repeatbody.statements[0] as? Assignment
        assertEquals(listOf("xx"), initassign?.target?.identifier?.nameInSource, "vardecl in repeat should be replaced by init assignment")
        assertEquals(99, (initassign?.value as? NumericLiteralValue)?.number?.toInt(), "vardecl in repeat should be replaced by init assignment")
        assertTrue(repeatbody.statements[1] is PostIncrDecr)
    }

    test("testLabelsWithAnonScopes") {
        val src = """
            main {
                sub start() {
                    uword addr
                    goto labeloutside
        
                    if true {
                        if true {
                            addr = &iflabel
                            addr = &labelinside
                            addr = &labeloutside
                            addr = &main.start.nested.nestedlabel
                            addr = &nested.nestedlabel
                            goto labeloutside
                            goto iflabel
                            goto main.start.nested.nestedlabel
                            goto nested.nestedlabel
                        }
            iflabel:
                    }
        
                    repeat {
                        addr = &iflabel
                        addr = &labelinside
                        addr = &labeloutside
                        addr = &main.start.nested.nestedlabel
                        addr = &nested.nestedlabel
                        goto iflabel
                        goto labelinside
                        goto main.start.nested.nestedlabel
                        goto nested.nestedlabel
            labelinside:
                    }

                    sub nested () {
            nestedlabel:
                        addr = &nestedlabel
                        goto nestedlabel
                        goto main.start.nested.nestedlabel
                    }

            labeloutside:
                    addr = &iflabel
                    addr = &labelinside
                    addr = &labeloutside
                    addr = &main.start.nested.nestedlabel
                    addr = &nested.nestedlabel
                    goto main.start.nested.nestedlabel
                    goto nested.nestedlabel
                }
            }
        """

        val result = compileText(C64Target, false, src, writeAssembly = true).assertSuccess()
        val module = result.program.toplevelModule
        val mainBlock = module.statements.single() as Block
        val start = mainBlock.statements.single() as Subroutine
        val labels = start.statements.filterIsInstance<Label>()
        assertEquals(1, labels.size, "only one label in subroutine scope")
    }

})
