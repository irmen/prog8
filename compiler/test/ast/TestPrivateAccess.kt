package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestPrivateAccess : FunSpec({
    val outputDir = tempdir().toPath()

    test("cannot access private variable from outside its block") {
        val text = """
            M {
                private ubyte x = 10
            }
            main {
                sub start() {
                    ubyte v = M.x
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "cannot access private variable 'M.x' from outside its block"
    }

    test("cannot access private subroutine from outside its block") {
        val text = """
            M {
                private sub foo() {}
            }
            main {
                sub start() {
                    M.foo()
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "cannot access private subroutine 'M.foo' from outside its block"
    }

    test("cannot access private struct from outside its block") {
        val text = """
            M {
                private struct S { ubyte f }
            }
            main {
                sub start() {
                    ubyte v = sizeof(M.S)
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors) shouldBe null
        errors.errors.any { it.contains("cannot access private struct 'M.S' from outside its block") } shouldBe true
    }

    test("cannot access private enum from outside its block") {
        val text = """
            M {
                private enum E { A, B }
            }
            main {
                sub start() {
                    ubyte v = M.E::A
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors) shouldBe null
        // Enum members are reported as private variable 'M.E::A' because they're converted to constant variables
        // that inherit the private status of the enum.
        errors.errors.any { it.contains("cannot access private variable 'M.E::A' from outside its block") } shouldBe true
    }

    test("cannot access private asmsub from outside its block") {
        val text = """
            M {
                private asmsub foo() {
                    %asm {{
                        rts
                    }}
                }
            }
            main {
                sub start() {
                    M.foo()
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "cannot access private subroutine 'M.foo' from outside its block"
    }

    test("cannot access private extsub from outside its block") {
        val text = $$"""
            M {
                private extsub $c000 = foo()
            }
            main {
                sub start() {
                    M.foo()
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "cannot access private subroutine 'M.foo' from outside its block"
    }

    test("can access private elements from within the same block") {
        val text = """
            other {
                private ubyte x = 10
                private sub foo() {}
                private asmsub bar_asm() {
                    rts
                }
                private struct S { ubyte f }
                
                sub bar() {
                    x = 20
                    foo()
                    bar_asm()
                    S* myS = 0
                }
            }
            main {
                sub start() {
                    other.bar()
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors, writeAssembly = false)
        errors.errors.size shouldBe 0
    }

    test("cannot use both private and public on same declaration") {
        val text = """
            main {
                private public ubyte x = 10
                sub start() {
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors) shouldBe null
        errors.printedErrors.any { it.contains("cannot use both") } shouldBe true
    }

    test("public keyword is accepted in default mode (no-op)") {
        val text = """
            M {
                public ubyte x = 10
                public sub foo() {}
                public struct S { ubyte f }
                public enum E { A, B }
            }
            main {
                sub start() {
                    ubyte v = M.x
                    M.foo()
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors, writeAssembly = false)
        errors.errors.size shouldBe 0
    }

    test("private is allowed in private_symbols mode (redundant)") {
        val text = """
            %option private_symbols
            M {
                private ubyte x = 10
            }
            main {
                sub start() {
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors, writeAssembly = false)
        errors.errors.size shouldBe 0
    }

    test("module-level private_symbols makes all symbols private by default") {
        val text = """
            %option private_symbols
            M {
                ubyte x = 10
                sub foo() {}
            }
            main {
                sub start() {
                    M.foo()
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors) shouldBe null
        errors.errors.any { it.contains("is not public") } shouldBe true
    }

    test("public overrides module-level private_symbols") {
        val text = """
            %option private_symbols
            M {
                public ubyte x = 10
                public sub foo() {}
            }
            main {
                sub start() {
                    ubyte v = M.x
                    M.foo()
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors, writeAssembly = false)
        errors.errors.size shouldBe 0
    }

    test("block-level private_symbols makes block symbols private by default") {
        val text = """
            M {
                ubyte x = 10
                sub foo() {}
            }
            main {
                %option private_symbols
                sub start() {
                    ubyte v = M.x
                    M.foo()
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, text, outputDir, errors = errors, writeAssembly = false)
        errors.errors.size shouldBe 0
    }
})
