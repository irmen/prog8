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
})
