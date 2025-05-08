package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldNotBe
import prog8.code.target.VMTarget
import prog8tests.helpers.compileText


class TestPointers: FunSpec( {

    val outputDir = tempdir().toPath()

    xtest("block scoping still parsed correctly") {
        val src="""
main {
    sub start() {
        readbyte(&thing.name)       ; ok
        readbyte(&thing.name[1])    ; TODO fix error
        readbyte(&thing.array[1])   ; TODO fix error
    }

    sub readbyte(uword @requirezp ptr) {
        ptr=0
    }
}

thing {
    str name = "error"
    ubyte[10] array
}"""
        compileText(VMTarget(), false, src, outputDir, writeAssembly = false) shouldNotBe null

    }
})