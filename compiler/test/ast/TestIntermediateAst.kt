package prog8tests.ast

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.code.ast.PtVariable
import prog8.code.target.C64Target
import prog8.compiler.IntermediateAstMaker
import prog8tests.helpers.compileText

class TestIntermediateAst: FunSpec({

    test("creation") {
        val text="""
            %import textio
            %import graphics
            main {
                sub start() {
                    ubyte cc
                    ubyte[] array = [1,2,3]
                    cc = 11 in array
                    cc = cc |> sin8u() |> cos8u()
                }
            }
        """
        val result = compileText(C64Target(),  false, text, writeAssembly = false)!!
        val ast = IntermediateAstMaker(result.program).transform()
        ast.name shouldBe result.program.name
        val entry = ast.entrypoint() ?: fail("no main.start() found")
        entry.name shouldBe "start"
        entry.scopedName shouldBe listOf("main", "start")
        val blocks = ast.allBlocks().toList()
        blocks.size shouldBeGreaterThan 1
        blocks[0].name shouldBe "main"
        blocks[0].scopedName shouldBe listOf("main")
        val ccdecl = entry.children[0] as PtVariable
        ccdecl.name shouldBe "cc"
        ccdecl.scopedName shouldBe listOf("main", "start", "cc")
        ast.print()
    }

})