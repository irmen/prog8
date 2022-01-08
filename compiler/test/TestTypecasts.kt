package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.codegen.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.assertFailure
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText


class TestTypecasts: FunSpec({

    test("correct typecasts") {
        val text = """
            %import floats
            
            main {
                sub start() {
                    float @shared fl = 3.456
                    uword @shared uw = 5555
                    byte @shared bb = -44

                    bb = uw as byte
                    uw = bb as uword
                    fl = uw as float
                    fl = bb as float
                    bb = fl as byte
                    uw = fl as uword
                }
            }
        """
        val result = compileText(C64Target, false, text, writeAssembly = true).assertSuccess()
        result.program.entrypoint.statements.size shouldBe 13
    }

    test("invalid typecasts of numbers") {
        val text = """
            %import floats
            
            main {
                sub start() {
                    ubyte @shared bb

                    bb = 5555 as ubyte
                    routine(5555 as ubyte)
                }
                
                sub routine(ubyte bb) {
                    bb++
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target, false, text, writeAssembly = true, errors=errors).assertFailure()
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "can't cast"
        errors.errors[1] shouldContain "can't cast"
    }

    test("refuse to round float literal 1") {
        val text = """
            %option enable_floats
            main {
                sub start() {
                    float @shared fl = 3.456 as uword
                    fl = 1.234 as uword
                }
            }"""
        val errors = ErrorReporterForTests()
        compileText(C64Target, false, text, errors=errors).assertFailure()
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "can't cast"
        errors.errors[1] shouldContain "can't cast"
    }

    test("refuse to round float literal 2") {
        val text = """
            %option enable_floats
            main {
                sub start() {
                    float @shared fl = 3.456
                    fl++
                    fl = fl as uword
                }
            }"""
        val errors = ErrorReporterForTests()
        compileText(C64Target, false, text, errors=errors).assertFailure()
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "in-place makes no sense"
    }

    test("refuse to round float literal 3") {
        val text = """
            %option enable_floats
            main {
                sub start() {
                    uword @shared ww = 3.456 as uword
                    ww++
                    ww = 3.456 as uword
                }
            }"""
        val errors = ErrorReporterForTests()
        compileText(C64Target, false, text, errors=errors).assertFailure()
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "can't cast"
        errors.errors[1] shouldContain "can't cast"
    }
})
