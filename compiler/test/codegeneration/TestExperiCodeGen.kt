package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldNotBe
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText

class TestExperiCodeGen: FunSpec({

    val outputDir = tempdir().toPath()

    test("banked extsub with multiple return values (constbank)") {
        val src = $$"""
main {
    sub start() {
        uword ptr
        ubyte bank
        void, ptr, bank = mymodule.myfunc(0)
    }
}

mymodule {
    const ubyte MYBANK = 1
    extsub @bank MYBANK $A000 = myfunc(ubyte x @X) -> bool @Pc, uword @XY, ubyte @A
}"""
        compileText(Cx16Target(), false, src, outputDir, experimentalCodegen = true) shouldNotBe null
    }

    test("banked extsub with multiple return values (varbank)") {
        val src = $$"""
main {
    sub start() {
        uword ptr
        ubyte bank
        void, ptr, bank = mymodule.myfunc(0)
    }
}

mymodule {
    ubyte @shared bankvar = 1
    extsub @bank bankvar $A000 = myfunc(ubyte x @X) -> bool @Pc, uword @XY, ubyte @A
}"""
        compileText(Cx16Target(), false, src, outputDir, experimentalCodegen = true) shouldNotBe null
    }
})
