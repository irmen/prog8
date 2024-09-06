package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import kotlin.io.path.readText

class TestArrayThings: FunSpec({
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
        compileText(VMTarget(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("array in-place negation (integer types)") {
        val text = """
main {
  byte[10] foo
  ubyte[10] foou
  word[10] foow
  uword[10] foowu

  sub start() {
    foo[1] = 42
    foo[1] = -foo[1]

    foow[1] = 4242
    foow[1] = -foow[1]
  }
}"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("array in-place negation (float type) vm target") {
        val text = """
%import floats

main {
  float[10] flt

  sub start() {
    flt[1] = 42.42
    flt[1] = -flt[1]
  }
}"""
        compileText(VMTarget(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("array in-place negation (float type) 6502 target") {
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
        compileText(VMTarget(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("split only for word arrays") {
        val srcGood = """
main {
  uword[10] @split sw
  word[10] @split sw2

  sub start() {
  }
}"""
        compileText(C64Target(), false, srcGood, writeAssembly = false) shouldNotBe null

        val srcWrong1 = """
main {
  ubyte[10] @split sb

  sub start() {
  }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, srcWrong1, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "split can only be used on word arrays"

        val srcWrong2 = """
%option enable_floats
main {
  float[10] @split sf

  sub start() {
  }
}"""
        errors.clear()
        compileText(C64Target(), false, srcWrong2, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "split can only be used on word arrays"
    }

    test("split word arrays in asm as lsb/msb") {
        val text = """
main {
  uword[10] @split @shared uw
  word[10] @split @shared sw
  uword[10] @shared normal

  sub start() {
    %asm {{
        lda  p8v_normal
        lda  p8v_uw_lsb
        lda  p8v_uw_msb
        lda  p8v_sw_lsb
        lda  p8v_sw_msb
    }}
  }
}"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("split array assignments") {
        val text = """
main {
    sub start() {
        str name1 = "name1"
        str name2 = "name2"
        uword[] @split names = [name1, name2, "name3"]
        uword[] @split names2 = [name1, name2, "name3"]
        uword[] addresses = [0,0,0]
        names = [1111,2222,3333]
        addresses = names
        names = addresses
        names2 = names
    } 
}"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("array target with expression for index") {
        val text = """
main {
    sub start() {
        ubyte[] array = [1,2,3]
        array[cx16.r0L+1] += 42
        cx16.r0L = array[cx16.r0L+1]
    } 
}"""
        compileText(VMTarget(), false, text, writeAssembly = true) shouldNotBe null
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("split array in zeropage is okay") {
        val text = """
main {
    sub start() {
        uword[3] @zp @split @shared thearray
    } 
}"""
        val result = compileText(C64Target(), false, text, writeAssembly = true)!!
        val assemblyFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val assembly = assemblyFile.readText()
        assembly shouldContain "thearray_lsb"
        assembly shouldContain "thearray_msb"
    }

    test("indexing str or pointervar with expression") {
        val text = """
main {
    sub start() {
        str name = "thing"
        modify(name)

        sub modify(str arg) {
            ubyte n=1
            uword pointervar
            arg[n+1] = arg[1]
            pointervar[n+1] = pointervar[1]
        }
    }
}"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("address of a uword pointer array expression") {
        val src="""
main {
    sub start() {
        set_state(12345, 1)
    }
    sub set_state(uword buffer, ubyte i) {
        uword addr = &buffer[i]
        addr++
    }
}"""
        compileText(C64Target(), false, src, writeAssembly = true) shouldNotBe null
    }

    test("negative array index variables are not allowed, but ptr indexing is allowed") {
        val src="""
main {
    sub start() {
        uword @shared pointer
        ubyte[10] array
        str name = "hello"
        
        byte sindex
        
        array[sindex] = 10
        cx16.r0L = array[sindex]
        name[sindex] = 0
        cx16.r0L = name[sindex]
        pointer[sindex] = 10
        cx16.r0L=pointer[sindex]
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 4
        errors.errors[0] shouldContain "signed variables"
        errors.errors[1] shouldContain "signed variables"
        errors.errors[2] shouldContain "signed variables"
        errors.errors[3] shouldContain "signed variables"
    }

    test("bounds checking for both positive and negative indexes, correct cases") {
        val src="""
main {
    sub start() {
        ubyte[10] array
        array[0] = 0
        array[9] = 0
        array[-1] = 0
        array[-10] = 0
    }
}"""
        compileText(VMTarget(), false, src, writeAssembly = false) shouldNotBe null
    }

    test("bounds checking on strings, correct cases") {
        val src="""
main {
    sub start() {
        str name = "1234567890"
        name[0] = 0
        name[9] = 0
    }
}"""
        compileText(VMTarget(), false, src, writeAssembly = false) shouldNotBe null
    }

    test("bounds checking for positive indexes, invalid case") {
        val src="""
main {
    sub start() {
        ubyte[10] array
        array[10] = 0
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "out of bounds"
    }

    test("bounds checking for negative indexes, invalid case") {
        val src="""
main {
    sub start() {
        ubyte[10] array
        array[-11] = 0
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "out of bounds"
    }

    test("bounds checking on strings invalid cases") {
        val src="""
main {
    sub start() {
        str name = "1234567890"
        name[10] = 0
        name[-1] = 0
        name[-11] = 0
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 3
        errors.errors[0] shouldContain "out of bounds"
        errors.errors[1] shouldContain "out of bounds"
        errors.errors[2] shouldContain "out of bounds"
    }
})

