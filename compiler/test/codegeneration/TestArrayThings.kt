package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.code.StStaticVariable
import prog8.code.SymbolTableMaker
import prog8.code.ast.*
import prog8.code.core.*
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

    test("array assignments should check for number of elements and element type correctness") {
        val src="""
%option enable_floats

main {
    sub start() {
        ubyte[] array = 1 to 4
        ubyte[] array2 = [1,2,3,4]
        str[] names = ["apple", "banana", "tomato"]

        array = [10,11,12,13]         ; ok!
        array = 20 to 23              ; ok!
        names = ["x1", "x2", "x3"]    ; ok!

        ubyte[] array3 = [1,2,3,4000]       ; error: element type
        array = 10 to 15                    ; error: array size
        array = 1000 to 1003                ; error: element type
        names = ["x1", "x2", "x3", "x4"]    ; error: array size
        names = [1.1, 2.2, 3.3, 4.4]        ; error: array size AND element type
        names = [1.1, 2.2, 999999.9]        ; error: element type
        names = [1.1, 2.2, 9.9]             ; error: element type
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, writeAssembly = true, errors = errors) shouldBe null
        errors.errors.size shouldBe 8
        errors.errors[0] shouldContain "incompatible type"
        errors.errors[1] shouldContain "array size mismatch"
        errors.errors[2] shouldContain "array element out of range"
        errors.errors[3] shouldContain "array size mismatch"
        errors.errors[4] shouldContain "array size mismatch"
        errors.errors[5] shouldContain "value has incompatible type"
        errors.errors[6] shouldContain "value has incompatible type"
        errors.errors[7] shouldContain "value has incompatible type"
    }

    test("array assignments should work via array copy call") {
        val src="""
%option enable_floats

main {
    sub start() {
        ubyte[] array = [1,2,3]
        ubyte[3] array2
        float[] flarray = [1.1, 2.2, 3.3]
        float[3] flarray2
        word[] warray = [-2222,42,3333]
        word[3] warray2
        str[] names = ["apple", "banana", "tomato"]
        str[3] names2

        ; 8 array assignments -> 8 arraycopies:
        array = [8,7,6]
        array = array2
        flarray = [99.9, 88.8, 77.7]
        flarray = flarray2
        warray = [4444,5555,6666]
        warray = warray2
        names = ["x1", "x2", "x3"]
        names = names2
    }
}"""
        compileText(VMTarget(), false, src, writeAssembly = true) shouldNotBe null
        val result = compileText(C64Target(), false, src, writeAssembly = true)!!
        val x = result.codegenAst!!.entrypoint()!!
        (x.children[12] as PtBuiltinFunctionCall).name shouldBe "prog8_lib_arraycopy"
        (x.children[13] as PtBuiltinFunctionCall).name shouldBe "prog8_lib_arraycopy"
        (x.children[14] as PtBuiltinFunctionCall).name shouldBe "prog8_lib_arraycopy"
        (x.children[15] as PtBuiltinFunctionCall).name shouldBe "prog8_lib_arraycopy"
        (x.children[16] as PtBuiltinFunctionCall).name shouldBe "prog8_lib_arraycopy"
        (x.children[17] as PtBuiltinFunctionCall).name shouldBe "prog8_lib_arraycopy"
        (x.children[18] as PtBuiltinFunctionCall).name shouldBe "prog8_lib_arraycopy"
        (x.children[19] as PtBuiltinFunctionCall).name shouldBe "prog8_lib_arraycopy"
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
        val result = compileText(C64Target(), false, src, writeAssembly = true)!!
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
        val result = compileText(C64Target(), false, src, writeAssembly = true)!!
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
        val result = compileText(C64Target(), false, src, writeAssembly = true)!!
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
        compileText(C64Target(), false, src, writeAssembly = true, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "contains non-constant"
        errors.errors[1] shouldContain "contains non-constant"
    }

    fun getTestOptions(): CompilationOptions {
        val target = VMTarget()
        return CompilationOptions(
            OutputType.RAW,
            CbmPrgLauncherType.NONE,
            ZeropageType.DONTUSE,
            zpReserved = emptyList(),
            zpAllowed = CompilationOptions.AllZeropageAllowed,
            floats = true,
            noSysInit = false,
            compTarget = target,
            loadAddress = target.machine.PROGRAM_LOAD_ADDRESS
        )
    }


    test("array assignments with ranges and multiplications") {
        val src="""
%option enable_floats

main {
    sub start() {
        bool[4] boolarray3
        ubyte[4] bytearray3
        uword[4] wordarray3
        float[4] floatarray3

        boolarray3 = [true] *4
        bytearray3 = [42]*4
        wordarray3 = [999]*4
        wordarray3 = [&bytearray3]*4
        wordarray3 = [bytearray3]*4
        floatarray3 = [99.77]*4
        
        bytearray3 = 10 to 13
        wordarray3 = 5000 to 5003
        floatarray3 = 100 to 103
    }
}"""
        val ast = compileText(C64Target(), false, src, writeAssembly = true)!!.codegenAst!!
        val x = ast.entrypoint()!!
        x.children.size shouldBe 23
        val assign1value = (x.children[13] as PtBuiltinFunctionCall).args[1]
        val assign2value = (x.children[14] as PtBuiltinFunctionCall).args[1]
        val assign3value = (x.children[15] as PtBuiltinFunctionCall).args[1]
        val assign4value = (x.children[16] as PtBuiltinFunctionCall).args[1]
        val assign5value = (x.children[17] as PtBuiltinFunctionCall).args[1]
        val assign6value = (x.children[18] as PtBuiltinFunctionCall).args[1]
        val assign7value = (x.children[19] as PtBuiltinFunctionCall).args[1]
        val assign8value = (x.children[20] as PtBuiltinFunctionCall).args[1]
        val assign9value = (x.children[21] as PtBuiltinFunctionCall).args[1]
        val options = getTestOptions()
        val st = SymbolTableMaker(ast, options).make()

        val heapvar1 = st.lookup((assign1value as PtIdentifier).name) as StStaticVariable
        val heapvar2 = st.lookup((assign2value as PtIdentifier).name) as StStaticVariable
        val heapvar3 = st.lookup((assign3value as PtIdentifier).name) as StStaticVariable
        val heapvar4 = st.lookup((assign4value as PtIdentifier).name) as StStaticVariable
        val heapvar5 = st.lookup((assign5value as PtIdentifier).name) as StStaticVariable
        val heapvar6 = st.lookup((assign6value as PtIdentifier).name) as StStaticVariable
        val heapvar7 = st.lookup((assign7value as PtIdentifier).name) as StStaticVariable
        val heapvar8 = st.lookup((assign8value as PtIdentifier).name) as StStaticVariable
        val heapvar9 = st.lookup((assign9value as PtIdentifier).name) as StStaticVariable
        heapvar1.length shouldBe 4
        heapvar2.length shouldBe 4
        heapvar3.length shouldBe 4
        heapvar4.length shouldBe 4
        heapvar5.length shouldBe 4
        heapvar6.length shouldBe 4
        heapvar7.length shouldBe 4
        heapvar8.length shouldBe 4
        heapvar9.length shouldBe 4
        heapvar1.dt shouldBe DataType.ARRAY_BOOL
        heapvar2.dt shouldBe DataType.ARRAY_UB
        heapvar3.dt shouldBe DataType.ARRAY_UW
        heapvar4.dt shouldBe DataType.ARRAY_UW
        heapvar5.dt shouldBe DataType.ARRAY_UW
        heapvar6.dt shouldBe DataType.ARRAY_F
        heapvar7.dt shouldBe DataType.ARRAY_UB
        heapvar8.dt shouldBe DataType.ARRAY_UW
        heapvar9.dt shouldBe DataType.ARRAY_F
    }

})

