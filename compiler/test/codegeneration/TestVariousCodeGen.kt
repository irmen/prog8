package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.instanceOf
import prog8.code.ast.PtAssignment
import prog8.code.ast.PtBinaryExpression
import prog8.code.ast.PtVariable
import prog8.code.core.DataType
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import kotlin.io.path.readText

class TestVariousCodeGen: FunSpec({
    test("nested scoping") {
        val text="""
main {
    sub start() {
        testscope.duplicate()
        cx16.r0L = testscope.duplicate2()
    }
}

testscope {

    sub sub1() {
        ubyte @shared duplicate
        ubyte @shared duplicate2
    }

    sub duplicate() {
        ; do nothing
    }

    sub duplicate2() -> ubyte {
        return cx16.r0L
    }
}"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("word array indexing") {
        val text="""
main {
    sub start() {
        uword[3] seed
        cx16.r0 = seed[0] + seed[1] + seed[2]
    }
}"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("ast result from compileText") {
        val text="""
main {
    sub start() {
        uword[3] seed
        cx16.r0 = seed[0] + seed[1] + seed[2]
    }
}"""
        val result = compileText(C64Target(), false, text, writeAssembly = true)!!
        result.compilerAst.name shouldStartWith  "on_the_fly"
        result.codegenAst!!.name shouldBe result.compilerAst.name
        result.codegenAst!!.children.size shouldBeGreaterThan 2
        val start = result.codegenAst!!.entrypoint()!!
        start.name shouldBe "p8s_start"
        start.children.size shouldBeGreaterThan 2
        val seed = start.children[0] as PtVariable
        seed.name shouldBe "p8v_seed"
        seed.value shouldBe null
        seed.type shouldBe DataType.ARRAY_UW
        val assign = start.children[1] as PtAssignment
        assign.target.identifier!!.name shouldBe "cx16.r0"
        assign.value shouldBe instanceOf<PtBinaryExpression>()
    }

    test("peek and poke argument types") {
        val text="""
main {
    sub start() {
        uword[3] arr
        ubyte i = 42
        uword ww = peekw(arr[i])
        ubyte xx = peek(arr[i])
        xx = @(arr[i])
        
        @(arr[i]) = 42
        poke(arr[i], 42)
        pokew(arr[i], 4242)
    }
}"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("assigning memory byte into arrays works") {
        val text="""
main {
    sub start() {
        uword factor1
        ubyte[3] bytearray
        uword[3] wordarray
        @(factor1) = bytearray[0]
        bytearray[0] = @(factor1)
        @(factor1) = lsb(wordarray[0])
        wordarray[0] = @(factor1)
        @(5000) = bytearray[0]
        @(5000) = lsb(wordarray[0])
        bytearray[0] = @(5000)
        wordarray[0] = @(5000)
    }
}"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("reading memory from unknown var gives proper error") {
        val text="""
main {
    sub start() {
        cx16.r0L = @(doesnotexist)
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, writeAssembly = true, errors = errors)
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "isn't uword"
        errors.errors[1] shouldContain "undefined symbol: doesnotexist"
    }

    test("shifting by word value is ok") {
        val text="""
main {
    sub start() {
        ubyte @shared c = 1
        @(15000 + c<<${'$'}0003) = 42
        @(15000 + (c<<${'$'}0003)) = 42
        @(15000 + c*${'$'}0008) = 42       ; *8 becomes a shift after opt

        uword @shared qq = 15000 + c<<${'$'}0003
        qq = 15000 + (c<<${'$'}0003)
        qq = 16000 + c*${'$'}0008
    }
}"""
        compileText(C64Target(), true, text, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), true, text, writeAssembly = true) shouldNotBe null
    }

    test("builtin func in float expression") {
        val src="""
%import floats
main {
    sub start() {
        float @shared fl =25.1
        fl = abs(fl)+0.5
        fl = sqrt(fl)+0.5
    }
}"""
        compileText(C64Target(), false, src, writeAssembly = true) shouldNotBe null
    }

    test("string vars in inlined subroutines are ok") {
        val src="""
main {
    sub start() {
        void block2.curdir()
        void block2.other()
    }
}

block2 {
    str result="zzzz"
    sub curdir() -> str {
        return result
    }

    sub other() -> str {
        return "other"
    }
}"""

        compileText(C64Target(), true, src, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), true, src, writeAssembly = true) shouldNotBe null
    }

    test("array with pointers") {
        val src = """
main {
    sub start() {
        str localstr = "hello"
        ubyte[] otherarray = [1,2,3]
        uword[] words = [1111,2222,"three",&localstr,&otherarray]
        uword @shared zz = &words
        bool @shared result = 2222 in words
        zz = words[2]
        zz++
        zz = words[3]
    }
}"""
        val othertarget = Cx16Target()
        compileText(othertarget, true, src, writeAssembly = true) shouldNotBe null
    }

    test("case sensitive symbols") {
        val src = """
main {
    sub start() {
        ubyte bytevar = 11      ; var at 0
        ubyte byteVAR = 22      ; var at 1
        ubyte ByteVar = 33      ; var at 2
        ubyte @shared total = bytevar+byteVAR+ByteVar   ; var at 3
        goto skipLABEL
SkipLabel:
        return
skipLABEL:
        bytevar = 42
    }
}"""
        val target = Cx16Target()
        compileText(target, true, src, writeAssembly = true) shouldNotBe null
    }

    test("addresses from labels/subroutines") {
        val src = """
main {
    sub start() {

mylabel:
        ubyte @shared variable
        uword @shared pointer1 = &main.start
        uword @shared pointer2 = &start
        uword @shared pointer3 = &main.start.mylabel
        uword @shared pointer4 = &mylabel
        uword[] @shared ptrs = [&variable, &start, &main.start, &mylabel, &main.start.mylabel]
    }
}

"""
        compileText(Cx16Target(), true, src, writeAssembly = true) shouldNotBe null
    }

    test("duplicate symbols okay other block and variable") {
        val src = """
main {
    ubyte derp
    sub start() {
        derp++
        foo.bar()
    }
}

foo {
    sub bar() {
        derp.print()
    }
}

derp {
    sub print() {
        cx16.r0++
        cx16.r1++
    }
}"""

        compileText(VMTarget(), false, src, writeAssembly = true) shouldNotBe null
        compileText(Cx16Target(), false, src, writeAssembly = true) shouldNotBe null
    }

    test("ambiguous symbol name variable vs block") {
        val src = """
main {
    sub start() {
        uword module
        module++
        module.test++
    }
}

module {
    ubyte @shared test
}
"""
        val errors=ErrorReporterForTests()
        compileText(VMTarget(), false, src, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "ambiguous symbol"
    }

    test("prefix expressions with typecasting") {
        val src="""
main
{
    sub start()
    {
        uword uw = 54321
        ubyte ub = 123
        word sw = -12345
        byte sb = -123

        func_uw(~ub as uword) 
        func_ub(~uw as ubyte) 
        func_uw(~sb as uword) 
        func_ub(~sw as ubyte) 
        func_w(-sb as word)   
        func_b(-sw as byte)   
    }
    
    sub func_uw(uword arg) {
    }
    sub func_w(word arg) {
    }
    sub func_ub(ubyte arg) {
    }
    sub func_b(byte arg) {
    }
}"""

        compileText(VMTarget(), false, src, writeAssembly = true) shouldNotBe null
        compileText(Cx16Target(), false, src, writeAssembly = true) shouldNotBe null
    }

    test("inlining sub with 2 statements") {
        val src="""
main {
    sub start() {
        init()
    }

    sub init() {
        init_handler()
        return
    }

    sub init_handler() {
        cx16.r0++
    }
}"""
        compileText(VMTarget(), true, src, writeAssembly = false) shouldNotBe null
        compileText(Cx16Target(), true, src, writeAssembly = false) shouldNotBe null
    }

    test("push pop are inlined also with noopt") {
        val text = """
main {
    sub start() {
        sys.push(11)
        sys.pushw(2222)
        cx16.r2++
        cx16.r1 = sys.popw()
        cx16.r0L = sys.pop()
    } 
}"""
        val result = compileText(C64Target(), false, text, writeAssembly = true)!!
        val assemblyFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val assembly = assemblyFile.readText()
        assembly shouldContain "inlined routine follows: push"
        assembly shouldContain "inlined routine follows: pushw"
        assembly shouldContain "inlined routine follows: pop"
        assembly shouldContain "inlined routine follows: popw"
    }
})