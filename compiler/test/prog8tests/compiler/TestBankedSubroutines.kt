package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import prog8.code.target.Cx16Target
import prog8.code.target.PETTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import kotlin.io.path.exists
import kotlin.io.path.readText

class TestBankedSubroutines : FunSpec({
    val outputDir = tempdir().toPath()

    test("banking subroutine success") {
        val text = $$"""
            main {
                asmsub get_bank(ubyte call_id @A) -> ubyte @A {
                    %asm {{
                        lda #0
                        rts
                    }}
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

    test("regular sub success") {
        val text = $$"""
            main {
                sub get_bank(ubyte call_id) -> ubyte {
                    return call_id + 1
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

    test("asmsub banking subroutine success") {
        val text = $$"""
            main {
                asmsub get_bank(ubyte call_id @A) -> ubyte @A {
                    %asm {{
                        lda #1
                        rts
                    }}
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

    test("banking subroutine must have one ubyte parameter") {
        val text = $$"""
            main {
                asmsub get_bank() -> ubyte @A {
                    %asm {{ rts }}
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
        errors.errors[0] shouldContain "bank subroutine must have exactly one ubyte parameter (call id)"
    }

    test("banking subroutine must return ubyte") {
        val text = $$"""
            main {
                asmsub get_bank(ubyte id @A) -> uword @AX {
                    %asm {{ rts }}
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
        errors.errors[0] shouldContain "@bank must be a ubyte variable or a subroutine(ubyte callid) returning ubyte"
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

    test(".bankedcalls file creation and content (6502)") {
        val text = $$"""
            main {
                sub selektor(ubyte id) -> ubyte { return 0 }
                extsub @bank selektor $ffd2 = chrout(ubyte char @A)
                extsub @bank selektor $a000 = other_sub()
                sub start() {
                    chrout('A')
                    other_sub()
                }
            }
        """
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        val res = compileText(Cx16Target(), false, text, outputDir, writeAssembly = true, errors = errors)
        if (res == null) {
            println("Errors: ${errors.errors}")
        }
        
        val bankedcallsFile = outputDir.resolve("on_the_fly_test_${text.hashCode().toUInt().toString(16)}.bankedcalls")
        bankedcallsFile.exists() shouldBe true
        val content = bankedcallsFile.readText()
        content shouldContain "ID  Address Name"
        content shouldContain "BankManager"
        content shouldContain "0"
        content shouldContain "main.chrout"
        content shouldContain $$"$ffd2"
        content shouldNotContain $$$"$$ffd2"
        content shouldContain "main.selektor"
        content shouldContain "1"
        content shouldContain "main.other_sub"
        content shouldContain $$"$a000"
        content shouldNotContain $$$"$$a000"
        
        errors.infos.any { it.contains("extsub banking call-site IDs written to") } shouldBe true
    }

    test("experimental codegen crash regression") {
        val text = $$"""
            main {
                sub selektor(ubyte id) -> ubyte { return 0 }
                extsub @bank selektor $ffd2 = chrout(ubyte char @A)
                sub start() {
                    chrout('A')
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, text, outputDir, errors = errors, writeAssembly = false, experimentalCodegen = true).shouldNotBeNull()
        errors.errors.size shouldBe 0
    }
})
