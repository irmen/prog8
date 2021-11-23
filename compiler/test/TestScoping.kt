package prog8tests

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import prog8.ast.GlobalNamespace
import prog8.ast.base.ParentSentinel
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.*
import prog8.compiler.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.assertFailure
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText


class TestScoping: FunSpec({

    test("modules parent is global namespace") {
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

    test("anon scope vars moved into subroutine scope") {
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

    test("labels with anon scopes") {
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

    test("good subroutine call without qualified names") {
        val text="""
            main {
                sub start() {
                    routine() 
                    routine2()
                    
                    sub routine2() {
                    }
                }
                sub routine() {
                    start()
                }
            }
        """
        compileText(C64Target, false, text, writeAssembly = false).assertSuccess()
    }

    test("wrong subroutine call without qualified names") {
        val text="""
            main {
                sub start() {
                    sub routine2() {
                    }
                }
                sub routine() {
                    routine2()
                }
            }
        """
        val errors= ErrorReporterForTests()
        compileText(C64Target, false, text, writeAssembly = false, errors = errors).assertFailure()
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "undefined symbol: routine2"
    }

    test("good subroutine calls with qualified names (from root)") {
        val text="""
            main {
                sub start() {
                    main.routine() 
                    main.start.routine2()
                    
                    sub routine2() {
                    }
                }
                sub routine() {
                    main.start.routine2()
                }
            }
        """
        compileText(C64Target, false, text, writeAssembly = false).assertSuccess()
    }

    test("wrong subroutine calls with qualified names (not from root)") {
        val text="""
            main {
                sub start() {
                    start.routine2()     
                    wrong.start.routine2()
                    sub routine2() {
                    }
                }
                sub routine() {
                    start.routine2()
                    wrong.start.routine2()
                }
            }
        """
        val errors= ErrorReporterForTests()
        compileText(C64Target, false, text, writeAssembly = false, errors=errors).assertFailure()
        errors.errors.size shouldBe 4
        errors.errors[0] shouldContain "undefined symbol: start.routine2"
        errors.errors[1] shouldContain "undefined symbol: wrong.start.routine2"
        errors.errors[2] shouldContain "undefined symbol: start.routine2"
        errors.errors[3] shouldContain "undefined symbol: wrong.start.routine2"
    }

    test("good variables without qualified names") {
        val text="""
            main {
                ubyte v1
                
                sub start() {
                    ubyte v2
                    v1=1
                    v2=2
                    
                    sub routine2() {
                        ubyte v3
                        v1=1
                        v2=2
                        v3=3
                    }
                }
                sub routine() {
                    ubyte v4
                    v1=1
                    v4=4
                }
            }
        """
        compileText(C64Target, false, text, writeAssembly = false).assertSuccess()
    }

    test("wrong variables without qualified names") {
        val text="""
            main {
                ubyte v1
                
                sub start() {
                    ubyte v2
                    v1=1
                    v2=2
                    v3=3    ; can't access
                    v4=4    ; can't access
                    sub routine2() {
                        ubyte v3
                        v1=1
                        v2=2
                        v3=3
                        v4=3    ;can't access
                    }
                }
                sub routine() {
                    ubyte v4
                    v1=1
                    v2=2    ; can't access
                    v3=3    ; can't access
                    v4=4
                }
            }
        """
        val errors= ErrorReporterForTests()
        compileText(C64Target, false, text, writeAssembly = false, errors=errors).assertFailure()
        errors.errors.size shouldBe 5
        errors.errors[0] shouldContain "undefined symbol: v3"
        errors.errors[1] shouldContain "undefined symbol: v4"
        errors.errors[2] shouldContain "undefined symbol: v4"
        errors.errors[3] shouldContain "undefined symbol: v2"
        errors.errors[4] shouldContain "undefined symbol: v3"
    }

    test("good variable refs with qualified names (from root)") {
        val text="""
            main {
                sub start() {
                    uword xx 
                    xx = &main.routine
                    main.routine(5)
                    main.routine.value = 5
                    main.routine.arg = 5
                    xx = &main.routine.nested
                    main.routine.nested(5)
                    main.routine.nested.nestedvalue = 5
                    main.routine.nested.arg2 = 5
                }
                
                sub routine(ubyte arg) {
                    ubyte value
                    
                    sub nested(ubyte arg2) {
                        ubyte nestedvalue
                    }
                }
            }
        """
        compileText(C64Target, false, text, writeAssembly = false).assertSuccess()
    }

    test("wrong variable refs with qualified names (not from root)") {
        val text="""
            main {
                sub start() {
                    uword xx 
                    xx = &routine
                    routine(5)
                    routine.value = 5
                    routine.arg = 5
                    xx = &routine.nested
                    routine.nested(5)
                    routine.nested.nestedvalue = 5
                    routine.nested.arg2 = 5
                    xx = &wrong.routine
                    wrong.routine.value = 5
                }
                
                sub routine(ubyte arg) {
                    ubyte value
                    
                    sub nested(ubyte arg2) {
                        ubyte nestedvalue
                    }
                }
            }
        """
        val errors= ErrorReporterForTests()
        compileText(C64Target, false, text, writeAssembly = false, errors=errors).assertFailure()
        errors.errors.size shouldBe 10
        errors.errors[0] shouldContain "undefined symbol: routine"
        errors.errors[1] shouldContain "undefined symbol: routine"
        errors.errors[2] shouldContain "undefined symbol: routine.value"
        errors.errors[3] shouldContain "undefined symbol: routine.arg"
        errors.errors[4] shouldContain "undefined symbol: routine.nested"
        errors.errors[5] shouldContain "undefined symbol: routine.nested"
        errors.errors[6] shouldContain "undefined symbol: routine.nested.nestedvalue"
        errors.errors[7] shouldContain "undefined symbol: routine.nested.arg2"
        errors.errors[8] shouldContain "undefined symbol: wrong.routine"
        errors.errors[9] shouldContain "undefined symbol: wrong.routine.value"
    }
})
