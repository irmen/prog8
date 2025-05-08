package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldNotBe
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8.vm.VmRunner
import prog8tests.helpers.compileText
import kotlin.io.path.readText


class TestPointers: FunSpec( {

    val outputDir = tempdir().toPath()

    test("block scoping still parsed correctly") {
        val src="""
main {
    sub start() {
        readbyte(&thing.name)       
        readbyte(&thing.name[1])    
        readbyte(&thing.array[1])   
    }

    sub readbyte(uword @requirezp ptr) {
        ptr=0
    }
}

thing {
    str name = "error"
    ubyte[10] array
}"""
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText(), true)
    }

})