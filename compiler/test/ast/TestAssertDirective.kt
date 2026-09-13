package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.Cx16Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestAssertDirective: FunSpec({

    val outputDir = tempdir().toPath()

    test("assert with true constant expression compiles fine") {
        val text = """
            main {
                %assert 1 == 1
                %assert 10 > 2, "block level message"
                sub start() {
                    %assert 1
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors=errors) shouldNotBe null
        errors.errors.size shouldBe 0
    }

    test("assert at module level compiles fine") {
        val text = """
            %assert 1 == 1
            %assert 10 > 2, "module level message"
            main {
                sub start() {
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors=errors) shouldNotBe null
        errors.errors.size shouldBe 0
    }

    test("assert with false constant expression fails") {
        val text = """
            main {
                sub start() {
                    %assert 1 == 2
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "assertion failed"
    }

    test("assert failure reports the message string") {
        val text = """
            main {
                struct Enemy {
                    ubyte hp
                    ubyte x
                    ubyte y
                }
                %assert sizeof(Enemy) > 32, "enemy struct grew too large"
                sub start() {
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "assertion failed: enemy struct grew too large"
    }

    test("assert with sizeof struct passes") {
        val text = """
            main {
                struct Enemy {
                    ubyte hp
                    ubyte x
                    ubyte y
                }
                %assert sizeof(Enemy) == 3, "enemy struct size changed"
                sub start() {
                    %assert sizeof(Enemy) <= 32
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors=errors) shouldNotBe null
        errors.errors.size shouldBe 0
    }

    test("assert with constant identifiers and arithmetic") {
        val text = """
            main {
                const uword STACK_SIZE = 128
                %assert STACK_SIZE * 2 < 512
                %assert STACK_SIZE
                sub start() {
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors=errors) shouldNotBe null
        errors.errors.size shouldBe 0
    }

    test("assert with non-constant expression is an error") {
        val text = """
            main {
                sub start() {
                    ubyte @shared x = 5
                    %assert x > 3
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "not a compile-time constant"
    }

    test("assert with wrong arguments is an error") {
        val text = """
            main {
                sub start() {
                    %assert 1 > 0, 42
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "second argument should be a message string"
    }
})
