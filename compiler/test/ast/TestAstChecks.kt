package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestAstChecks: FunSpec({

    test("conditional expression w/float works") {
        val text = """
            %import floats
            main {
                sub start() {
                    uword xx
                    if xx+99.99 == xx+1.234 {
                        xx++
                    }
                }
            }
        """
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), true, text, writeAssembly = true, errors=errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.size shouldBe 2
        errors.warnings[0] shouldContain "converted to float"
        errors.warnings[1] shouldContain "converted to float"
    }

    test("can't assign label or subroutine without using address-of") {
        val text = """
            main {
                sub start() {
            
            label:
                    uword @shared addr
                    addr = label
                    addr = thing
                    addr = &label
                    addr = &thing
                }
            
                sub thing() {
                }
            }
            """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), true, text, writeAssembly = true, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.warnings.size shouldBe 0
        errors.errors[0] shouldContain ":7:28: invalid assignment value, maybe forgot '&'"
        errors.errors[1] shouldContain ":8:28: invalid assignment value, maybe forgot '&'"
    }

    test("can't do str or array expression without using address-of") {
        val text = """
            %import textio
            main {
                sub start() {
                    ubyte[] array = [1,2,3,4]
                    str s1 = "test"
                    ubyte ff = 1
                    txt.print(s1+ff)
                    txt.print(array+ff)
                    txt.print_uwhex(s1+ff, true)
                    txt.print_uwhex(array+ff, true)
                }
            }
            """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.filter { it.contains("missing &") }.size shouldBe 4
    }

    test("str or array expression with address-of") {
        val text = """
            %import textio
            main {
                sub start() {
                    ubyte[] array = [1,2,3,4]
                    str s1 = "test"
                    ubyte ff = 1
                    txt.print(&s1+ff)
                    txt.print(&array+ff)
                    txt.print_uwhex(&s1+ff, true)
                    txt.print_uwhex(&array+ff, true)
                    ; also good:
                    ff = (s1 == "derp")
                    ff = (s1 != "derp")
                }
            }
            """
        compileText(C64Target(), false, text, writeAssembly = false) shouldNotBe null
    }

    test("const is not allowed on arrays") {
        val text = """
            main {
                sub start() {
                    const ubyte[5] a = 5
                    a[2]=42
                }
            }
        """
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), true, text, writeAssembly = true, errors=errors)
        errors.errors.size shouldBe 1
        errors.warnings.size shouldBe 0
        errors.errors[0] shouldContain "const can only be used"
    }

    test("array indexing is not allowed on a memory mapped variable") {
        val text = """
            main {
                sub start() {
                    &ubyte a = 10000
                    uword z = 500
                    a[4] = (z % 3) as ubyte
                }
            }
        """
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), true, text, writeAssembly = true, errors=errors)
        errors.errors.size shouldBe 1
        errors.warnings.size shouldBe 0
        errors.errors[0] shouldContain "indexing requires"
    }

    test("array decl with expression as size can be initialized with a single value") {
        val text = """
            main {
                sub start() {
                    const ubyte n = 40
                    const ubyte half = n / 2
                    ubyte[half] @shared a = 5
                }
            }
        """
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), true, text, writeAssembly = true, errors=errors)  shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.size shouldBe 0
    }

    test("unicode in identifier names is working") {
        val text = """
%import floats

main {
    ubyte приблизительно = 99
    ubyte นี่คือตัวอักษรภาษาไท = 42
    
    sub start() {
        str knäckebröd = "crunchy"  ; with composed form
        prt(knäckebröd)             ; with decomposed form
        printf(2*floats.π)
    }

    sub prt(str message) {
        приблизительно++
    }

    sub printf(float fl) {
        นี่คือตัวอักษรภาษาไท++
    }
}"""
        compileText(C64Target(), false, text, writeAssembly = true)  shouldNotBe null
        compileText(Cx16Target(), false, text, writeAssembly = true)  shouldNotBe null
        compileText(VMTarget(), false, text, writeAssembly = true)  shouldNotBe null
    }
})
