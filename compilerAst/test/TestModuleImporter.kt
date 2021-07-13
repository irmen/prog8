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

        val srcPath = fixturesDir.resolve("i_do_not_exist")
        assumeNotExists(srcPath)

        assertFailsWith<NoSuchFileException> { importer.importModule(srcPath) }
    }

    @Test
    fun testImportModuleWithDirectoryPath() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))

        val srcPath = fixturesDir
        assumeDirectory(srcPath)

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
        val path = fixturesDir.resolve(filename)
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

        val importing = fixturesDir.resolve("import_file_with_syntax_error.p8")
        val imported = fixturesDir.resolve("file_with_syntax_error.p8")
        assumeReadableFile(importing)
        assumeReadableFile(imported)

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
        val filenameNoExt = "i_do_not_exist"
        val filenameWithExt = filenameNoExt + ".p8"
        val srcPathNoExt = fixturesDir.resolve(filenameNoExt)
        val srcPathWithExt = fixturesDir.resolve(filenameWithExt)
        assumeNotExists(srcPathNoExt)
        assumeNotExists(srcPathWithExt)

        assertFailsWith<NoSuchFileException> { importer.importLibraryModule(filenameNoExt) }
        assertFailsWith<NoSuchFileException> { importer.importLibraryModule(filenameWithExt) }
    }

    @Test
    fun testImportLibraryModuleWithSyntaxError() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
        val importer = ModuleImporter(program, "blah", listOf(searchIn))
        val srcPath = fixturesDir.resolve("file_with_syntax_error.p8")
        assumeReadableFile(srcPath)

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

        val importing = fixturesDir.resolve("import_file_with_syntax_error.p8")
        val imported = fixturesDir.resolve("file_with_syntax_error.p8")
        assumeReadableFile(importing)
        assumeReadableFile(imported)

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
