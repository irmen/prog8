package prog8tests.ast


import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.Position
import prog8.ast.internedStringsModuleName
import prog8.parser.SourceCode
import prog8tests.helpers.DummyFunctions
import prog8tests.helpers.DummyMemsizer
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertSame


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgramTests {

    @Nested
    inner class Constructor {
        @Test
        fun withNameBuiltinsAndMemsizer() {
            val program = Program("foo", DummyFunctions, DummyMemsizer)
            assertThat(program.modules.size, equalTo(1))
            assertThat(program.modules[0].name, equalTo(internedStringsModuleName))
            assertSame(program, program.modules[0].program)
            assertSame(program.namespace, program.modules[0].parent)
        }

    }

    @Nested
    inner class AddModule {
        @Test
        fun withEmptyModule() {
            val program = Program("foo", DummyFunctions, DummyMemsizer)
            val m1 = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("bar"))

            val retVal = program.addModule(m1)

            assertSame(program, retVal)
            assertThat(program.modules.size, equalTo(2))
            assertContains(program.modules, m1)
            assertSame(program, m1.program)
            assertSame(program.namespace, m1.parent)

            assertFailsWith<IllegalArgumentException> { program.addModule(m1) }
                .let { assertThat(it.message, containsString(m1.name)) }

            val m2 = Module(mutableListOf(), m1.position, m1.source)
            assertFailsWith<IllegalArgumentException> { program.addModule(m2) }
                .let { assertThat(it.message, containsString(m2.name)) }
        }
    }

    @Nested
    inner class MoveModuleToFront {
        @Test
        fun withInternedStringsModule() {
            val program = Program("foo", DummyFunctions, DummyMemsizer)
            val m = program.modules[0]
            assertThat(m.name, equalTo(internedStringsModuleName))

            val retVal = program.moveModuleToFront(m)
            assertSame(program, retVal)
            assertSame(m, program.modules[0])
        }
        @Test
        fun withForeignModule() {
            val program = Program("foo", DummyFunctions, DummyMemsizer)
            val m = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("bar"))

            assertFailsWith<IllegalArgumentException> { program.moveModuleToFront(m) }
        }
        @Test
        fun withFirstOfPreviouslyAddedModules() {
            val program = Program("foo", DummyFunctions, DummyMemsizer)
            val m1 = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("bar"))
            val m2 = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("qmbl"))
            program.addModule(m1)
            program.addModule(m2)

            val retVal = program.moveModuleToFront(m1)
            assertSame(program, retVal)
            assertThat(program.modules.indexOf(m1), equalTo(0))
        }
        @Test
        fun withSecondOfPreviouslyAddedModules() {
            val program = Program("foo", DummyFunctions, DummyMemsizer)
            val m1 = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("bar"))
            val m2 = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("qmbl"))
            program.addModule(m1)
            program.addModule(m2)

            val retVal = program.moveModuleToFront(m2)
            assertSame(program, retVal)
            assertThat(program.modules.indexOf(m2), equalTo(0))
        }
    }

    @Nested
    inner class Properties {
        @Test
        fun modules() {
            val program = Program("foo", DummyFunctions, DummyMemsizer)

            val ms1 = program.modules
            val ms2 = program.modules
            assertSame(ms1, ms2)
        }
    }
}
