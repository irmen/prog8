package prog8tests

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import prog8.ast.GlobalNamespace
import prog8.ast.base.ParentSentinel
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.*
import prog8.compiler.target.C64Target
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText


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
        module.parent shouldBe instanceOf<GlobalNamespace>()
        module.program shouldBeSameInstanceAs result.program
        module.parent.parent shouldBe instanceOf<ParentSentinel>()
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
        withClue("no vars moved to main block") {
            mainBlock.statements.any { it is VarDecl } shouldBe false
        }
        val subroutineVars = start.statements.filterIsInstance<VarDecl>()
        withClue("var from repeat anonscope must be moved up to subroutine") {
            subroutineVars.size shouldBe 1
        }
        subroutineVars[0].name shouldBe "xx"
        withClue("var should have been removed from repeat anonscope") {
            repeatbody.statements.any { it is VarDecl } shouldBe false
        }
        val initassign = repeatbody.statements[0] as? Assignment
        withClue("vardecl in repeat should be replaced by init assignment") {
            initassign?.target?.identifier?.nameInSource shouldBe listOf("xx")
        }
        withClue("vardecl in repeat should be replaced by init assignment") {
            (initassign?.value as? NumericLiteralValue)?.number?.toInt() shouldBe 99
        }
        repeatbody.statements[1] shouldBe instanceOf<PostIncrDecr>()
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
        withClue("only one label in subroutine scope") {
            labels.size shouldBe 1
        }
    }

})
