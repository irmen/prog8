package prog8tests

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import kotlin.test.*
import prog8tests.helpers.*

import kotlin.io.path.*

import prog8.ast.Program
import prog8.parser.ModuleImporter
import prog8.parser.ParseError


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestModuleImporter {

    @Test
    fun testImportModuleWithNonExistingPath() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))
        val srcPath = assumeNotExists(fixturesDir, "i_do_not_exist")

        assertFailsWith<NoSuchFileException> { importer.importModule(srcPath) }
    }

    @Test
    fun testImportModuleWithDirectoryPath() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))

        val srcPath = assumeDirectory(fixturesDir)

        // fn importModule(Path) used to check *.isReadable()*, but NOT .isRegularFile():
        assumeReadable(srcPath)

        assertFailsWith<AccessDeniedException> { importer.importModule(srcPath) }
    }

    @Test
    fun testImportModuleWithSyntaxError() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))

        val filename = "file_with_syntax_error.p8"
        val path = assumeReadableFile(fixturesDir, filename)
        val act = { importer.importModule(path) }

        assertFailsWith<ParseError> { act() }
        try {
            act()
        } catch (e: ParseError) {
            assertEquals(path.absolutePathString(), e.position.file)
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based" )
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
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

        assertFailsWith<ParseError> { act() }
        try {
            act()
        } catch (e: ParseError) {
            val expectedProvenance = imported.absolutePathString()
            assertEquals(expectedProvenance, e.position.file)
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based" )
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
        }
    }

    @Test
    fun testImportLibraryModuleWithNonExistingName() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))
        val filenameNoExt = assumeNotExists(fixturesDir, "i_do_not_exist").name
        val filenameWithExt = assumeNotExists(fixturesDir, "i_do_not_exist.p8").name

        assertFailsWith<NoSuchFileException> { importer.importLibraryModule(filenameNoExt) }
        assertFailsWith<NoSuchFileException> { importer.importLibraryModule(filenameWithExt) }
    }

    @Test
    fun testImportLibraryModuleWithSyntaxError() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))
        val srcPath = assumeReadableFile(fixturesDir,"file_with_syntax_error.p8")

        val act = { importer.importLibraryModule(srcPath.nameWithoutExtension) }

        assertFailsWith<ParseError> { act() }
        try {
            act()
        } catch (e: ParseError) {
            val expectedProvenance = srcPath.absolutePathString()
            assertEquals(expectedProvenance, e.position.file)
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based")
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
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

        assertFailsWith<ParseError> { act() }
        try {
            act()
        } catch (e: ParseError) {
            val expectedProvenance = imported.normalize().absolutePathString()
            assertEquals(expectedProvenance, e.position.file)
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based")
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
        }
    }

}
