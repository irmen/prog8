package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.Cx16Target
import prog8.code.target.PETTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestBankedSubroutines : FunSpec({
    val outputDir = tempdir().toPath()

    test("banking subroutine success") {
        val text = $$"""
            main {
                sub get_bank() -> ubyte {
                    return 0
                }
                extsub @bank get_bank $ffd2 = chrout(ubyte char @A)
                
                sub start() {
                    chrout('A')
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors = errors).shouldNotBeNull()
        errors.errors.size shouldBe 0
    }

    test("banking subroutine must be parameterless") {
        val text = $$"""
            main {
                sub get_bank(ubyte x) -> ubyte {
                    return x
                }
                extsub @bank get_bank $ffd2 = chrout(ubyte char @A)
                
                sub start() {
                    chrout('A')
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "bank subroutine must be parameterless"
    }

    test("banking subroutine must return ubyte") {
        val text = $$"""
            main {
                sub get_bank() -> uword {
                    return 0
                }
                extsub @bank get_bank $ffd2 = chrout(ubyte char @A)
                
                sub start() {
                    chrout('A')
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "bank subroutine must return a single ubyte"
    }

    test("banking subroutine cannot itself be banked") {
        val text = $$"""
            main {
                extsub @bank 1 $A000 = get_bank() -> ubyte @A
                extsub @bank get_bank $ffd2 = chrout(ubyte char @A)
                
                sub start() {
                    chrout('A')
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "bank subroutine cannot itself be a banked routine"
    }

    test("bank variable must be ubyte") {
        val text = $$"""
            main {
                uword mybank = 0
                extsub @bank mybank $ffd2 = chrout(ubyte char @A)
                
                sub start() {
                    mybank = 1
                    chrout('A')
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "@bank must be a ubyte variable or a parameterless subroutine returning ubyte"
    }

    test("banked subroutine not supported on pet32") {
        val text = $$"""
            main {
                extsub @bank 1 $ffd2 = chrout(ubyte char @A)
                sub start() {
                    chrout('A')
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests()
        compileText(PETTarget(), false, text, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "banked subroutine call is not supported on the selected compilation target"
    }

    test("banked subroutine definition (not called) supported on pet32") {
        val text = $$"""
            main {
                extsub @bank 1 $ffd2 = chrout(ubyte char @A)
                sub start() {
                    ; no call to chrout
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests()
        compileText(PETTarget(), false, text, outputDir, writeAssembly = false, errors = errors).shouldNotBeNull()
        errors.errors.size shouldBe 0
    }

    test("banked subroutine variable bank romable check at call site") {
        val text = $$"""
            %option romable
            main {
                ubyte bank = 1
                extsub @bank bank $ffd2 = chrout_romable(ubyte char @A)
                sub start() {
                    bank = 2
                    chrout_romable('A')
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, errors = errors, writeAssembly = false, varshigh = 1, slabshigh = 1) shouldBe null
        errors.errors.any { it.contains("variable bank extsub has no romable code-generation") } shouldBe true
    }
})
