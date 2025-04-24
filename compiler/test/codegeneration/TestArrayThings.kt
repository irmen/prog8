package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.code.ast.*
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import kotlin.io.path.readText

class TestArrayThings: FunSpec({
    val outputDir = tempdir().toPath()
    
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
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
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("split word arrays in asm as lsb/msb, nosplit as single linear") {
        val text = """
main {
  uword[10] @shared uw
  word[10] @shared sw
  uword[10] @shared @nosplit normal

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
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
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
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("split array in zeropage is okay") {
        val text = """
main {
    sub start() {
        uword[3] @zp @shared thearray
        uword[3] @zp @nosplit @shared thearray2
    } 
}"""
        val result = compileText(C64Target(), false, text, outputDir, writeAssembly = true)!!
        val assemblyFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val assembly = assemblyFile.readText()
        assembly shouldContain "thearray_lsb"
        assembly shouldContain "thearray_msb"
        assembly shouldContain "thearray2"
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
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
        compileText(C64Target(), false, src, outputDir, writeAssembly = true) shouldNotBe null
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
        compileText(VMTarget(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
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
        compileText(VMTarget(), false, src, outputDir, writeAssembly = false) shouldNotBe null
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
        compileText(VMTarget(), false, src, outputDir, writeAssembly = false) shouldNotBe null
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
        compileText(VMTarget(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
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
        compileText(VMTarget(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
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
        compileText(VMTarget(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 3
        errors.errors[0] shouldContain "out of bounds"
        errors.errors[1] shouldContain "out of bounds"
        errors.errors[2] shouldContain "out of bounds"
    }

    test("array and string initializer with multiplication") {
        val src="""
%option enable_floats

main {
    sub start() {
        str name = "xyz" * 3
        bool[3] boolarray   = [true] * 3
        ubyte[3] bytearray  = [42] * 3
        uword[3] wordarray  = [5555] * 3
        float[3] floatarray = [123.45] * 3
    }
}"""
        val result = compileText(C64Target(), false, src, outputDir, writeAssembly = true)!!
        val x = result.codegenAst!!.entrypoint()!!
        x.children.size shouldBe 6
        ((x.children[0] as PtVariable).value as PtString).value shouldBe "xyzxyzxyz"
        val array1 = (x.children[1] as PtVariable).value as PtArray
        val array2 = (x.children[2] as PtVariable).value as PtArray
        val array3 = (x.children[3] as PtVariable).value as PtArray
        val array4 = (x.children[4] as PtVariable).value as PtArray
        array1.children.map { (it as PtBool).value } shouldBe listOf(true, true, true)
        array2.children.map { (it as PtNumber).number } shouldBe listOf(42, 42, 42)
        array3.children.map { (it as PtNumber).number } shouldBe listOf(5555, 5555, 5555)
        array4.children.map { (it as PtNumber).number } shouldBe listOf(123.45, 123.45, 123.45)
    }

    test("array initializer with range") {
        val src="""
%option enable_floats

main {
    sub start() {
        ubyte[3] bytearray2 = 10 to 12
        uword[3] wordarray2 = 5000 to 5002
        float[3] floatarray2 = 100 to 102
    }
}"""
        val result = compileText(C64Target(), false, src, outputDir, writeAssembly = true)!!
        val x = result.codegenAst!!.entrypoint()!!
        x.children.size shouldBe 4
        val array1 = (x.children[0] as PtVariable).value as PtArray
        val array2 = (x.children[1] as PtVariable).value as PtArray
        val array3 = (x.children[2] as PtVariable).value as PtArray
        array1.children.map { (it as PtNumber).number } shouldBe listOf(10, 11, 12)
        array2.children.map { (it as PtNumber).number } shouldBe listOf(5000, 5001, 5002)
        array3.children.map { (it as PtNumber).number } shouldBe listOf(100, 101, 102)
    }

    test("identifiers in array literals getting implicit address-of") {
        val src="""
main {
    sub start() {
label:
        str @shared name = "name"
        uword[] @shared array1 = [name, label, start, main]
        uword[] @shared array2 = [&name, &label, &start, &main]
    }
}"""
        val result = compileText(C64Target(), false, src, outputDir, writeAssembly = true)!!
        val x = result.codegenAst!!.entrypoint()!!
        x.children.size shouldBe 5
        val array1 = (x.children[1] as PtVariable).value as PtArray
        val array2 = (x.children[2] as PtVariable).value as PtArray
        array1.children.forEach {
            it shouldBe instanceOf<PtAddressOf>()
        }
        array2.children.forEach {
            it shouldBe instanceOf<PtAddressOf>()
        }
    }

    test("variable identifiers in array literals not getting implicit address-of") {
        val src="""
main {
    sub start() {
label:
        str @shared name = "name"
        ubyte @shared bytevar
        uword[] @shared array1 = [cx16.r0]  ; error, is variables
        uword[] @shared array2 = [bytevar]  ; error, is variables
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = true, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "contains non-constant"
        errors.errors[1] shouldContain "contains non-constant"
    }

    test("memsizing in codegen of array return values") {
        val src="""
main {
    sub start() {
        cx16.r1 = give_array1()
        cx16.r2 = give_array2()
    }

    sub give_array1() -> uword {
        return [1,2,3,4]
    }
    sub give_array2() -> uword {
        return [1000,2000,3000,4000]
    }
}"""
        compileText(VMTarget(), false, src, outputDir, writeAssembly = true) shouldNotBe null
        compileText(C64Target(), false, src, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("taking address of split arrays works") {
        val src="""
main {
    sub start() {
        cx16.r0L=0
        if cx16.r0L==0 {
            uword[] addresses = [scores2, start]
            uword[] scores1 = [10, 25, 50, 100]
            uword[] scores2 = [100, 250, 500, 1000]

            cx16.r0 = &scores1
            cx16.r1 = &scores2
            cx16.r2 = &addresses
        }
    }
}"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=true, errors=errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.size shouldBe 0
    }
})

