package prog8tests

import prog8.ast.IBuiltinFunctions
import prog8.ast.IMemSizer
import prog8.ast.IStringEncoding
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteralValue
import prog8.parser.ModuleImporter
import prog8.parser.ParseError
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.io.path.*
import kotlin.test.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestModuleImporter {

    object DummyEncoding: IStringEncoding {
        override fun encodeString(str: String, altEncoding: Boolean): List<Short> {
            TODO("Not yet implemented")
        }

        override fun decodeString(bytes: List<Short>, altEncoding: Boolean): String {
            TODO("Not yet implemented")
        }
    }

    object DummyFunctions: IBuiltinFunctions {
        override val names: Set<String> = emptySet()
        override val purefunctionNames: Set<String> = emptySet()
        override fun constValue(name: String, args: List<Expression>, position: Position, memsizer: IMemSizer): NumericLiteralValue? = null
        override fun returnType(name: String, args: MutableList<Expression>) = InferredTypes.InferredType.unknown()
    }

    object DummyMemsizer: IMemSizer {
        override fun memorySize(dt: DataType): Int = 0
    }


    @Test
    fun testImportModuleWithNonExistingPath() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, DummyEncoding, "blah", listOf("./test/fixtures"))

        val srcPath = Path.of("test", "fixtures", "i_do_not_exist")

        assertFalse(srcPath.exists(), "sanity check: file should not exist")
        assertFailsWith<java.nio.file.NoSuchFileException> { importer.importModule(srcPath) }
    }

    @Test
    fun testImportModuleWithDirectoryPath() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, DummyEncoding, "blah", listOf("./test/fixtures"))

        val srcPath = Path.of("test", "fixtures")

        assertTrue(srcPath.isDirectory(), "sanity check: should be a directory")

        // fn importModule(Path) used to check *.isReadable()*, but NOT .isRegularFile():
        assertTrue(srcPath.isReadable(), "sanity check: should still be readable")

        assertFailsWith<java.nio.file.AccessDeniedException> { importer.importModule(srcPath) }
    }

    @Test
    fun testImportLibraryModuleWithNonExistingPath() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, DummyEncoding, "blah", listOf("./test/fixtures"))

        val srcPath = Path.of("i_do_not_exist.p8")

        assertFalse(srcPath.exists(), "sanity check: file should not exist")
        assertFailsWith<java.nio.file.NoSuchFileException> { importer.importLibraryModule(srcPath.nameWithoutExtension) }
    }

    @Test
    fun testImportModuleWithSyntaxError() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, DummyEncoding, "blah", listOf("./test/fixtures"))

        val filename = "file_with_syntax_error.p8"
        val act = { importer.importModule(Path.of("test", "fixtures", filename )) }

        assertFailsWith<ParseError> { act() }
        try {
            act()
        } catch (e: ParseError) {
            assertEquals(filename, e.position.file, "provenance; should be the path's filename, incl. extension '.p8'")
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based" )
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
        }
    }

    @Test
    fun testImportModuleWithImportingModuleWithSyntaxError() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, DummyEncoding, "blah", listOf("./test/fixtures"))

        val importing = Path.of("test", "fixtures", "import_file_with_syntax_error.p8")
        val imported = Path.of("test", "fixtures", "file_with_syntax_error.p8")

        val act = { importer.importModule(importing) }

        assertTrue(importing.isRegularFile(), "sanity check: should be regular file")
        assertFailsWith<ParseError> { act() }
        try {
            act()
        } catch (e: ParseError) {
            assertEquals(imported.fileName.toString(), e.position.file, "provenance; should be the importED file's filename, incl. extension '.p8'")
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based" )
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
        }
    }


    @Test
    fun testImportLibraryModuleWithSyntaxError() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, DummyEncoding, "blah", listOf("./test/fixtures"))

        val filename = "file_with_syntax_error"
        val act = { importer.importLibraryModule(filename) }

        assertFailsWith<ParseError> { act() }
        try {
            act()
        } catch (e: ParseError) {
            assertEquals(
                filename + ".p8",
                e.position.file,
                "provenance; should be the path's filename, incl. extension '.p8'"
            )
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based")
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
        }
    }

    @Test
    fun testImportLibraryModuleWithImportingBadModule() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, DummyEncoding, "blah", listOf("./test/fixtures"))

        val importing = "import_file_with_syntax_error"
        val imported = "file_with_syntax_error"
        val act = { importer.importLibraryModule(importing) }

        assertFailsWith<ParseError> { act() }
        try {
            act()
        } catch (e: ParseError) {
            assertEquals(
                imported + ".p8",
                e.position.file,
                "provenance; should be the importED file's name, incl. extension '.p8'"
            )
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based")
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
        }
    }

}
