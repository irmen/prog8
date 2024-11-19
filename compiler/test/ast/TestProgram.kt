package prog8tests.ast

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.statements.Block
import prog8.code.ast.PtBlock
import prog8.code.ast.PtBool
import prog8.code.core.Position
import prog8.code.core.SourceCode
import prog8.code.core.internedStringsModuleName
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8tests.helpers.*

class TestProgram: FunSpec({

    context("Constructor") {
        test("withNameBuiltinsAndMemsizer") {
            val program = Program("foo", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            program.modules.size shouldBe 1
            program.modules[0].name shouldBe internedStringsModuleName
            program.modules[0].program shouldBeSameInstanceAs program
            program.modules[0].parent shouldBeSameInstanceAs program.namespace
        }
    }

    context("AddModule") {
        test("withEmptyModule") {
            val program = Program("foo", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            val m1 = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("bar"))

            val retVal = program.addModule(m1)

            retVal shouldBeSameInstanceAs program
            program.modules.size shouldBe 2
            m1 shouldBeIn program.modules
            m1.program shouldBeSameInstanceAs program
            m1.parent shouldBeSameInstanceAs program.namespace

            withClue("module may not occur multiple times") {
                val ex = shouldThrow<IllegalArgumentException> { program.addModule(m1) }
                ex.message shouldContain m1.name
            }

            val m2 = Module(mutableListOf(), m1.position, m1.source)
            withClue("other module but with same name may not occur multiple times") {
                val ex = shouldThrow<IllegalArgumentException> { program.addModule(m2) }
                ex.message shouldContain m1.name
            }
        }
    }

    context("MoveModuleToFront") {
        test("withInternedStringsModule") {
            val program = Program("foo", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            val m = program.modules[0]
            m.name shouldBe internedStringsModuleName

            val retVal = program.moveModuleToFront(m)
            retVal shouldBeSameInstanceAs program
            program.modules[0] shouldBeSameInstanceAs m
        }

        test("withForeignModule") {
            val program = Program("foo", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            val m = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("bar"))

            shouldThrow<IllegalArgumentException> { program.moveModuleToFront(m) }
        }

        test("withFirstOfPreviouslyAddedModules") {
            val program = Program("foo", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            val m1 = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("bar"))
            val m2 = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("qmbl"))
            program.addModule(m1)
            program.addModule(m2)

            val retVal = program.moveModuleToFront(m1)
            retVal shouldBeSameInstanceAs program
            program.modules.indexOf(m1) shouldBe 0
        }

        test("withSecondOfPreviouslyAddedModules") {
            val program = Program("foo", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            val m1 = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("bar"))
            val m2 = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("qmbl"))
            program.addModule(m1)
            program.addModule(m2)

            val retVal = program.moveModuleToFront(m2)
            retVal shouldBeSameInstanceAs program
            program.modules.indexOf(m2) shouldBe 0
        }
    }

    context("Properties") {
        test("modules") {
            val program = Program("foo", DummyFunctions, DummyMemsizer, DummyStringEncoder)

            val ms1 = program.modules
            val ms2 = program.modules
            ms2 shouldBeSameInstanceAs ms1
        }
    }

    context("block merge") {
        test("merge works") {
            val src = """
%import textio

main {

    sub start() {
        blah.test()
    }
}

txt {
    ; merges this block into the txt block coming from the textio library
    %option merge

    sub schrijf(str arg) {
        print(arg)
    }
}

blah {
    ; merges this block into the other 'blah' one
    %option merge

    sub test() {
        printit("test merge")
    }
}

blah {
    sub printit(str arg) {
        txt.schrijf(arg)
    }
}"""
            compileText(C64Target(), optimize=false, src, writeAssembly=false) shouldNotBe null
        }

        test("merge override existing subroutine") {
            val src="""
%import textio

main {

    sub start() {
        txt.print("sdfdsf")
    }
}

txt {
    %option merge

    sub print(str text) {
        cx16.r0++
        ; just some dummy implementation to replace existing print
    }
}"""

            val result = compileText(VMTarget(), optimize=false, src, writeAssembly=false)
            result shouldNotBe null
        }

        test("merge doesn't override existing subroutine if signature differs") {
            val src="""
%import textio

main {

    sub start() {
        txt.print("sdfdsf")
    }
}

txt {
    %option merge

    sub print(str anotherparamname) {
        cx16.r0++
        ; just some dummy implementation to replace existing print
    }
}"""
            val errors = ErrorReporterForTests()
            compileText(VMTarget(), optimize=false, src, writeAssembly=false, errors = errors) shouldBe null
            errors.errors.size shouldBe 1
            errors.errors[0] shouldContain "name conflict"
        }
    }

    test("block sort order") {
        val src="""
%import textio

main ${'$'}0a00 {
    sub start() {
        otherblock1.foo()
        otherblock2.foo()
    }
}

otherblock1 {
    sub foo() {
        txt.print("main.start:       ")
        txt.print_uwhex(&main.start, true)
        txt.nl()
        txt.print("datablock1 array: ")
        txt.print_uwhex(&datablock1.array1, true)
        txt.nl()
        txt.print("datablock2 array: ")
        txt.print_uwhex(&datablock2.array2, true)
        txt.nl()
        txt.print("otherblock1.foo:  ")
        txt.print_uwhex(&foo, true)
        txt.nl()
    }
}

otherblock2 {
    sub foo() {
        txt.print("otherblock2.foo:  ")
        txt.print_uwhex(&otherblock2.foo, true)
        txt.nl()
    }
}

datablock1 ${'$'}9000 {
    ubyte[5] @shared array1 = [1,2,3,4,5]
}

datablock2 ${'$'}8000 {
    ubyte[5] @shared array2 = [1,2,3,4,5]
}
"""

        val result = compileText(C64Target(), optimize=false, src, writeAssembly=true)!!
        result.compilerAst.allBlocks.size shouldBeGreaterThan 5
        result.compilerAst.modules.drop(2).all { it.isLibrary } shouldBe true
        val mainMod = result.compilerAst.modules[0]
        mainMod.name shouldStartWith "on_the_fly"
        result.compilerAst.modules[1].name shouldBe "prog8_interned_strings"
        val mainBlocks = mainMod.statements.filterIsInstance<Block>()
        mainBlocks.size shouldBe 6
        mainBlocks[0].name shouldBe "main"
        mainBlocks[1].name shouldBe "p8_sys_startup"
        mainBlocks[2].name shouldBe "otherblock1"
        mainBlocks[3].name shouldBe "otherblock2"
        mainBlocks[4].name shouldBe "datablock2"
        mainBlocks[5].name shouldBe "datablock1"

        result.codegenAst!!.children.size shouldBeGreaterThan 5
        val blocks = result.codegenAst.children.filterIsInstance<PtBlock>()
        blocks.size shouldBe 15
        blocks[0].name shouldBe "p8b_main"
        blocks[1].name shouldBe "p8_sys_startup"
        blocks[2].name shouldBe "p8b_otherblock1"
        blocks[3].name shouldBe "p8b_otherblock2"
        blocks[4].name shouldBe "prog8_interned_strings"
        blocks[5].name shouldBe "txt"
        blocks[5].library shouldBe true
        blocks[13].name shouldBe "p8b_datablock2"
        blocks[14].name shouldBe "p8b_datablock1"
    }
})
