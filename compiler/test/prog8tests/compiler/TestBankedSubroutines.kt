package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.VMTarget
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
        compileText(VMTarget(), false, text, outputDir, writeAssembly = false, errors = errors).shouldNotBeNull()
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
        compileText(VMTarget(), false, text, outputDir, writeAssembly = false, errors = errors) shouldBe null
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
        compileText(VMTarget(), false, text, outputDir, writeAssembly = false, errors = errors) shouldBe null
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
        compileText(VMTarget(), false, text, outputDir, writeAssembly = false, errors = errors) shouldBe null
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
        compileText(VMTarget(), false, text, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "@bank must be a ubyte variable or a parameterless subroutine returning ubyte"
    }
})
