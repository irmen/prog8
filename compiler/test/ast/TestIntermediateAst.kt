package prog8tests.ast

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.C64Target
import prog8.compiler.astprocessing.IntermediateAstMaker
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
                    cc = sqrt16(lsb(cc))
                }
            }
        """
        val target = C64Target()
        val options = CompilationOptions(
            OutputType.RAW,
            CbmPrgLauncherType.NONE,
            ZeropageType.DONTUSE,
            emptyList(),
            floats = false,
            noSysInit = true,
            compTarget = target,
            loadAddress = target.machine.PROGRAM_LOAD_ADDRESS
        )
        val result = compileText(target, false, text, writeAssembly = false)!!
        val ast = IntermediateAstMaker(result.compilerAst, options).transform()
        ast.name shouldBe result.compilerAst.name
        ast.allBlocks().any() shouldBe true
        val entry = ast.entrypoint() ?: fail("no main.start() found")
        entry.children.size shouldBe 5
        entry.name shouldBe "start"
        entry.scopedName shouldBe "main.start"
        val blocks = ast.allBlocks().toList()
        blocks.size shouldBeGreaterThan 1
        blocks[0].name shouldBe "main"
        blocks[0].scopedName shouldBe "main"

        val ccInit = entry.children[2] as PtAssignment
        ccInit.target.identifier?.name shouldBe "main.start.cc"
        (ccInit.value as PtNumber).number shouldBe 0.0

        val ccdecl = entry.children[0] as PtVariable
        ccdecl.name shouldBe "cc"
        ccdecl.scopedName shouldBe "main.start.cc"
        ccdecl.type shouldBe DataType.UBYTE
        val arraydecl = entry.children[1] as IPtVariable
        arraydecl.name shouldBe "array"
        arraydecl.type shouldBe DataType.ARRAY_UB

        val containment = (entry.children[3] as PtAssignment).value as PtContainmentCheck
        (containment.element as PtNumber).number shouldBe 11.0
        val fcall = (entry.children[4] as PtAssignment).value as PtFunctionCall
        fcall.void shouldBe false
        fcall.type shouldBe DataType.UBYTE
        ast.print()
    }

})