package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import prog8.ast.GlobalNamespace
import prog8.ast.ParentSentinel
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.*
import prog8.code.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestScoping: FunSpec({

    test("modules parent is global namespace") {
        val src = """
            main {
                sub start() {
                }
            }
        """

        val result = compileText(C64Target(), false, src, writeAssembly = false)!!
        val module = result.compilerAst.toplevelModule
        module.parent shouldBe instanceOf<GlobalNamespace>()
        module.program shouldBeSameInstanceAs result.compilerAst
        module.parent.parent shouldBe instanceOf<ParentSentinel>()
    }

    test("anon scope vars moved into subroutine scope") {
        val src = """
            main {
                sub start() {
                    repeat 10 {
                        ubyte xx = 99
                        rol(xx)
                    }
                }
            }
        """

        val result = compileText(C64Target(), false, src, writeAssembly = false)!!
        val mainBlock = result.compilerAst.entrypoint.definingBlock
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
            (initassign?.value as? NumericLiteral)?.number?.toInt() shouldBe 99
        }
        repeatbody.statements[1] shouldBe instanceOf<FunctionCallStatement>()
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
                            goto labeloutside
                            goto iflabel
                            goto main.start.nested.nestedlabel
                        }
            iflabel:
                    }
        
                    repeat 10 {
                        addr = &iflabel
                        addr = &labelinside
                        addr = &labeloutside
                        addr = &main.start.nested.nestedlabel
                        goto iflabel
                        goto labelinside
                        goto main.start.nested.nestedlabel
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
                    goto main.start.nested.nestedlabel
                }
            }
        """

        val result = compileText(C64Target(), false, src, writeAssembly = true)!!
        val mainBlock = result.compilerAst.entrypoint.definingBlock
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
        compileText(C64Target(), false, text, writeAssembly = false) shouldNotBe null
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
        compileText(C64Target(), false, text, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "undefined"
        errors.errors[0] shouldContain "routine2"
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
        compileText(C64Target(), false, text, writeAssembly = false) shouldNotBe null
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
        compileText(C64Target(), false, text, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 4
        errors.errors[0] shouldContain "undefined"
        errors.errors[0] shouldContain "start.routine2"
        errors.errors[1] shouldContain "undefined"
        errors.errors[1] shouldContain "wrong.start.routine2"
        errors.errors[2] shouldContain "undefined"
        errors.errors[2] shouldContain "start.routine2"
        errors.errors[3] shouldContain "undefined"
        errors.errors[3] shouldContain "wrong.start.routine2"
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
        compileText(C64Target(), false, text, writeAssembly = false) shouldNotBe null
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
        compileText(C64Target(), false, text, writeAssembly = false, errors=errors) shouldBe null
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
        compileText(C64Target(), false, text, writeAssembly = false) shouldNotBe null
    }

    test("wrong variable refs with qualified names 1 (not from root)") {
        val text="""
            main {
                sub start() {
                    uword xx 
                    xx = &routine
                    routine(5)
                    routine.value = 5
                    routine.arg = 5
                    routine.nested.arg2 = 5
                    routine.nested.nestedvalue = 5
                    nested.nestedvalue = 5
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
        compileText(C64Target(), false, text, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 5
        errors.errors[0] shouldContain "undefined symbol: routine.value"
        errors.errors[1] shouldContain "undefined symbol: routine.arg"
        errors.errors[2] shouldContain "undefined symbol: routine.nested.arg2"
        errors.errors[3] shouldContain "undefined symbol: routine.nested.nestedvalue"
        errors.errors[4] shouldContain "undefined symbol: nested.nestedvalue"
    }

    test("various good goto targets") {
        val text="""
            main {
                sub start() {
                    uword address = $4000
                    
                    goto ${'$'}c000
                    goto address        ; indirect jump
                    goto main.routine
                    goto main.jumplabel
                    
                    if_cc
                        goto ${'$'}c000
                    if_cc
                        goto address        ; indirect jump
                    if_cc
                        goto main.routine
                    if_cc
                        goto main.jumplabel
                }

            jumplabel:
                %asm {{
                    rts
                }}
                sub routine() {
                }
            }
        """
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("various wrong goto targets") {
        val text = """
            main {
                sub start() {
                    byte wrongaddress = 100
                    
                    goto wrongaddress   ; must be uword
                    goto main.routine   ; can't take args
                }

                sub routine(ubyte arg) {
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "wrong address"
        errors.errors[1] shouldContain "takes parameters"
    }

    test("name shadowing and redefinition errors") {
        val text = """
            main {
                ubyte var1Warn
                
                sub start() {
                    ubyte var1Warn
                    ubyte var1Warn
                    ubyte main 
                    ubyte start
                    ubyte outer         ; is ok
                    ubyte internalOk
                    ubyte internalOk    ; double defined
                }
                
                sub outer() {
                    ubyte var1Warn
                    ubyte internalOk
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, writeAssembly = false, errors = errors) shouldBe null
        /*
There are 4 errors and 3 warnings.
ERROR name conflict 'start', also defined...
ERROR name conflict 'var1Warn', also defined...
ERROR name conflict 'main', also defined...
ERROR name conflict 'internalOk', also defined...
WARN name 'var1Warn' shadows occurrence at...
WARN name 'var1Warn' shadows occurrence at...
WARN name 'var1Warn' shadows occurrence at...
         */
        errors.warnings.size shouldBe 3
        errors.warnings[0] shouldContain "var1Warn"
        errors.warnings[0] shouldContain "shadows"
        errors.warnings[0] shouldContain "line 3"
        errors.warnings[1] shouldContain "var1Warn"
        errors.warnings[1] shouldContain "shadows"
        errors.warnings[1] shouldContain "line 3"
        errors.warnings[2] shouldContain "var1Warn"
        errors.warnings[2] shouldContain "shadows"
        errors.warnings[2] shouldContain "line 3"
        errors.errors.size shouldBe 4
        errors.errors[0] shouldContain "name conflict"
        errors.errors[0] shouldContain "start"
        errors.errors[0] shouldContain "line 5"
        errors.errors[1] shouldContain "name conflict"
        errors.errors[1] shouldContain "var1Warn"
        errors.errors[1] shouldContain "line 6"
        errors.errors[2] shouldContain "name conflict"
        errors.errors[2] shouldContain "main"
        errors.errors[2] shouldContain "line 2"
        errors.errors[3] shouldContain "name conflict"
        errors.errors[3] shouldContain "internalOk"
        errors.errors[3] shouldContain "line 11"
    }
})
