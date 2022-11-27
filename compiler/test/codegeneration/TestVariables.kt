package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import prog8.code.target.C64Target
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
                        lda  arrayvar
                        lda  stringvar
                        lda  bytevar
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

    test("array initialization with array var assignment") {
        val text = """
            main {
                sub start() {
                    ubyte[3] @shared arrayvar = main.values
                }
                
                ubyte[] values = [1,2,3]
            }
        """
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
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

})
