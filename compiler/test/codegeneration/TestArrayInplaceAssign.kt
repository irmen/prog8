package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import prog8.code.target.C64Target
import prog8tests.helpers.compileText

class TestArrayInplaceAssign: FunSpec({
    test("assign prefix var to array should compile fine and is not split into inplace array modification") {
        val text = """
            main {
                sub start() {
                    byte[5] array
                    byte bb
                    array[1] = -bb
                }
            }
        """
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("array in-place negation (integer types)") {
        val text = """
%import floats

main {
  byte[10] foo
  ubyte[10] foou
  word[10] foow
  uword[10] foowu
  float[10] flt

  sub start() {
    foo[1] = 42
    foo[1] = -foo[1]

    foow[1] = 4242
    foow[1] = -foow[1]

    ; TODO floating point in-place negation is not yet implemented in the code generator
    ; flt[1] = 42.42
    ; flt[1] = -flt[1]
  }
}"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    // TODO implement this in codegen and enable test
    xtest("array in-place negation (float type) - ignored for now because not implemented in codegen yet") {
        val text = """
%import floats

main {
  float[10] flt

  sub start() {
    flt[1] = 42.42
    flt[1] = -flt[1]
  }
}"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("array in-place invert") {
        val text = """
main {
  ubyte[10] foo
  uword[10] foow

  sub start() {
    foo[1] = 42
    foo[1] = ~foo[1]

    foow[1] = 4242
    foow[1] = ~foow[1]
  }
}"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }
})

