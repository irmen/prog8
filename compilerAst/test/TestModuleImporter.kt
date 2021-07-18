package prog8tests

import prog8tests.helpers.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.*

import prog8.ast.Program
import prog8.parser.ParseError

import prog8.parser.ModuleImporter


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestModuleImporter {

    private val count = listOf("1st", "2nd", "3rd", "4th", "5th")

    @Test
    fun testImportModuleWithExistingPath_absolute() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = listOf(
            Path(".").div(workingDir.relativize(fixturesDir)), // we do want a dot "." in front
        ).map { it.invariantSeparatorsPathString }
        val importer = ModuleImporter(program, "blah", searchIn)
        val fileName = "simple_main.p8"
        val path = assumeReadableFile(searchIn[0], fileName)

        val module = importer.importModule(path.absolute())
        assertThat(module.program, `is`(program))
    }

    @Test
    fun testImportModuleWithExistingPath_relativeToWorkingDir() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = listOf(
            Path(".").div(workingDir.relativize(fixturesDir)), // we do want a dot "." in front
        ).map { it.invariantSeparatorsPathString }
        val importer = ModuleImporter(program, "blah", searchIn)
        val fileName = "simple_main.p8"
        val path = assumeReadableFile(searchIn[0], fileName)
        assertThat("sanity check: path should NOT be absolute", path.isAbsolute, `is`(false))

        val module = importer.importModule(path)
        assertThat(module.program, `is`(program))
    }

    @Test
    fun testImportModuleWithExistingPath_relativeTo1stDirInSearchList() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = listOf(
            Path(".").div(workingDir.relativize(fixturesDir)), // we do want a dot "." in front
        ).map { it.invariantSeparatorsPathString }
        val importer = ModuleImporter(program, "blah", searchIn)
        val fileName = "simple_main.p8"
        val path = Path(".", fileName)
        assumeReadableFile(searchIn[0], path)

        val module = importer.importModule(path)
        assertThat(module.program, `is`(program))
    }

    @Test
    fun testImportModuleWithNonExistingPath() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))
        val srcPath = assumeNotExists(fixturesDir, "i_do_not_exist")

        assertThrows<NoSuchFileException> { importer.importModule(srcPath) }
    }

    @Test
    fun testImportModuleWithDirectoryPath() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))
        val srcPath = assumeDirectory(fixturesDir)

        assertThrows<AccessDeniedException> { importer.importModule(srcPath) }
            .let {
                assertThat(it.message!!, containsString("$srcPath"))
                assertThat(it.file, `is`(srcPath.toFile()))
            }
    }

    @Test
    fun testImportModuleWithSyntaxError() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))
        val srcPath = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

        val act = { importer.importModule(srcPath) }

        repeat (2) { n ->
            assertThrows<ParseError>(count[n] + " call") { act() }.let {
                assertThat(it.position.file, `is`(srcPath.absolutePathString()))
                assertThat("line; should be 1-based",       it.position.line,       `is`(2))
                assertThat("startCol; should be 0-based",   it.position.startCol,   `is`(6))
                assertThat("endCol; should be 0-based",     it.position.endCol,     `is`(6))
            }
        }
    }

    @Test
    fun testImportModuleWithImportingModuleWithSyntaxError() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))
        val importing = assumeReadableFile(fixturesDir, "import_file_with_syntax_error.p8")
        val imported = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

        val act = { importer.importModule(importing) }

        repeat (2) { n ->
            assertThrows<ParseError>(count[n] + " call") { act() }.let {
                assertThat(it.position.file, `is`(imported.absolutePathString()))
                assertThat("line; should be 1-based",       it.position.line,       `is`(2))
                assertThat("startCol; should be 0-based",   it.position.startCol,   `is`(6))
                assertThat("endCol; should be 0-based",     it.position.endCol,     `is`(6))
            }
        }
    }

    @Test
    fun testImportLibraryModuleWithNonExistingName() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))
        val filenameNoExt = assumeNotExists(fixturesDir, "i_do_not_exist").name
        val filenameWithExt = assumeNotExists(fixturesDir, "i_do_not_exist.p8").name

        repeat (2) { n ->
            assertThrows<NoSuchFileException>(count[n] + " call / NO .p8 extension")
                { importer.importLibraryModule(filenameNoExt) }.let {
                    assertThat(it.message!!, containsString(filenameWithExt))
                }
            assertThrows<NoSuchFileException>(count[n] + " call / with .p8 extension")
                { importer.importLibraryModule(filenameWithExt) }.let {
                    assertThat(it.message!!, containsString(filenameWithExt))
                }
        }
    }

    @Test
    fun testImportLibraryModuleWithSyntaxError() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))
        val srcPath = assumeReadableFile(fixturesDir,"file_with_syntax_error.p8")

        repeat (2) { n ->
            assertThrows<ParseError> (count[n] + " call")
                { importer.importLibraryModule(srcPath.nameWithoutExtension) } .let {
                    assertThat(it.position.file, `is`(srcPath.absolutePathString()))
                    assertThat("line; should be 1-based",       it.position.line,       `is`(2))
                    assertThat("startCol; should be 0-based",   it.position.startCol,   `is`(6))
                    assertThat("endCol; should be 0-based",     it.position.endCol,     `is`(6))
                }
        }
    }

    @Test
    fun testImportLibraryModuleWithImportingBadModule() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))
        val importing = assumeReadableFile(fixturesDir, "import_file_with_syntax_error.p8")
        val imported = assumeReadableFile(fixturesDir,"file_with_syntax_error.p8")

        val act = { importer.importLibraryModule(importing.nameWithoutExtension) }

        repeat(2) { n ->
            assertThrows<ParseError>(count[n] + " call") { act() }.let {
                assertThat(it.position.file, `is`(imported.normalize().absolutePathString()))
                assertThat("line; should be 1-based", it.position.line, `is`(2))
                assertThat("startCol; should be 0-based", it.position.startCol, `is`(6))
                assertThat("endCol; should be 0-based", it.position.endCol, `is`(6))
            }
        }
    }

}
