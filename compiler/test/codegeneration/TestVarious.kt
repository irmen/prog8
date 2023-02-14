package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.instanceOf
import prog8.code.ast.PtArrayIndexer
import prog8.code.ast.PtAssignment
import prog8.code.ast.PtVariable
import prog8.code.core.DataType
import prog8.code.target.C64Target
import prog8tests.helpers.compileText

class TestVarious: FunSpec({
    test("bool to byte cast in expression is correct") {
        val text="""
main {
    sub start() {
        ubyte[3] values
        func(22 in values)
        ubyte @shared qq = 22 in values
    }
    sub func(ubyte arg) {
        arg++
    }
}"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

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
        start.name shouldBe "start"
        start.children.size shouldBeGreaterThan 2
        val seed = start.children[0] as PtVariable
        seed.name shouldBe "seed"
        seed.value shouldBe null
        seed.type shouldBe DataType.ARRAY_UW
        val assign = start.children[1] as PtAssignment
        assign.target.identifier!!.name shouldBe "cx16.r0"
        assign.value shouldBe instanceOf<PtArrayIndexer>()
    }
})