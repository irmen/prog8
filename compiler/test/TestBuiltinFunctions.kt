package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText

class TestBuiltinFunctions: FunSpec({

    test("push pop") {
        val src="""
            main  {
                sub start () { 
                    pushw(cx16.r0)
                    push(cx16.r1L)
                    pop(cx16.r1L)
                    popw(cx16.r0)
                }
            }"""
        compileText(Cx16Target(), false, src, writeAssembly = true) shouldNotBe null
    }
})

