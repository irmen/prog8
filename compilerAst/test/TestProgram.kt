package prog8tests.ast

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.Position
import prog8.ast.internedStringsModuleName
import prog8.parser.SourceCode
import prog8tests.ast.helpers.DummyFunctions
import prog8tests.ast.helpers.DummyMemsizer
import prog8tests.ast.helpers.DummyStringEncoder

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
})
