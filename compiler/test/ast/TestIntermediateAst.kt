package prog8tests.ast

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.codegen.experimental6502.IntermediateAstMaker
import prog8.codegen.target.C64Target
import prog8tests.helpers.assertSuccess
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
        val result = compileText(C64Target(),  false, text, writeAssembly = false).assertSuccess()
        val ast = IntermediateAstMaker.transform(result.program)
        ast.name shouldBe result.program.name
        ast.builtinFunctions.names shouldBe result.program.builtinFunctions.names
        val entry = ast.entrypoint() ?: fail("no main.start() found")
        entry.name shouldBe "start"
        val blocks = ast.allBlocks().toList()
        blocks.size shouldBeGreaterThan 1
        blocks[0].name shouldBe "main"
        ast.print()
    }

})