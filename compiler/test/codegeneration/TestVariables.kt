package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.compiler.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText


class TestVariables: FunSpec({

    test("shared variables without refs not removed for inlined asm") {
        val text = """
            main {
                sub start() {
                    ubyte[] @shared array = [1,2,3,4]
                    str @shared name = "test"
                    ubyte @shared bytevar = 0
            
                    %asm {{
                        lda  array
                        lda  name
                        lda  bytevar
                    }}
                }
            }
        """
        compileText(C64Target, true, text, writeAssembly = true).assertSuccess()
    }
})
