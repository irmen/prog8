package prog8tests.ast

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.code.ast.*
import prog8.code.core.DataType
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
                    cc = cc |> lsb() |> sqrt16()
                }
            }
        """
        val result = compileText(C64Target(),  false, text, writeAssembly = false)!!
        val ast = IntermediateAstMaker(result.program).transform()
        ast.name shouldBe result.program.name
        ast.allBlocks().any() shouldBe true
        val entry = ast.entrypoint() ?: fail("no main.start() found")
        entry.children.size shouldBe 4
        entry.name shouldBe "start"
        entry.scopedName shouldBe listOf("main", "start")
        val blocks = ast.allBlocks().toList()
        blocks.size shouldBeGreaterThan 1
        blocks[0].name shouldBe "main"
        blocks[0].scopedName shouldBe listOf("main")

        val vars = entry.children[0] as PtScopeVarsDecls
        val ccInit = entry.children[1] as PtAssignment
        ccInit.target.identifier?.targetName shouldBe listOf("main","start","cc")
        (ccInit.value as PtNumber).number shouldBe 0.0

        val ccdecl = vars.children[0] as PtVariable
        ccdecl.name shouldBe "cc"
        ccdecl.scopedName shouldBe listOf("main", "start", "cc")
        ccdecl.type shouldBe DataType.UBYTE
        val arraydecl = vars.children[1] as PtVariable
        arraydecl.name shouldBe "array"
        arraydecl.type shouldBe DataType.ARRAY_UB

        val containment = (entry.children[2] as PtAssignment).value as PtContainmentCheck
        (containment.element as PtNumber).number shouldBe 11.0
        val pipe = (entry.children[3] as PtAssignment).value as PtPipe
        pipe.void shouldBe false
        pipe.type shouldBe DataType.UBYTE
        ast.print()
    }

})