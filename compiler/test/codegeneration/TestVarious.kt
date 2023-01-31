package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
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

})