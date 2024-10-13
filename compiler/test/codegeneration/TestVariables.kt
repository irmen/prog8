package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestVariables: FunSpec({

    test("shared variables without refs not removed for inlined asm") {
        val text = """
            main {
                sub start() {
                    ubyte[] @shared arrayvar = [1,2,3,4]
                    str @shared stringvar = "test"
                    ubyte @shared bytevar = 0
            
                    %asm {{
                        lda  p8v_arrayvar
                        lda  p8v_stringvar
                        lda  p8v_bytevar
                    }}
                }
            }
        """
        compileText(C64Target(), true, text, writeAssembly = true) shouldNotBe null
    }

    test("array initialization with array literal") {
        val text = """
            main {
                sub start() {
                    ubyte[] @shared arrayvar = [1,2,3,4]
                }
            }
        """
        compileText(C64Target(), true, text, writeAssembly = true) shouldNotBe null
    }

    test("pipe character in string literal") {
        val text = """
            main {
                sub start() {
                    str name = "first|second"
                    str name2 = "first | second"
                }
            }
        """
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }
    
    test("negation of unsigned via casts") {
        val text = """
            main {
                sub start() {
                    cx16.r0L = -(cx16.r0L as byte) as ubyte
                    cx16.r0 = -(cx16.r0 as word) as uword
                    ubyte ub
                    uword uw
                    ub = -(ub as byte) as ubyte
                    uw = -(uw as word) as uword
                }
            }
        """
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("initialization of boolean array with array") {
        val text = """
            main {
                sub start() {
                    bool[3] sieve0 = [true, false, true]
                }
            }
        """
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("initialization of boolean array with wrong array type should fail") {
        val text = """
            main {
                sub start() {
                    bool[] sieve0 = [true, false, 1]
                    bool[] sieve1 = [true, false, 42]
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, writeAssembly = true, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "value has incompatible type"
        errors.errors[1] shouldContain "value has incompatible type"
    }
})
