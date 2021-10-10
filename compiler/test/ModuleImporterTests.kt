package prog8tests

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.*
import prog8.ast.Program
import prog8.compiler.ModuleImporter
import prog8.parser.ParseError
import prog8tests.helpers.*
import kotlin.io.path.*
import kotlin.test.assertContains


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestModuleImporter {
    private val count = listOf("1st", "2nd", "3rd", "4th", "5th")

    private lateinit var program: Program
    @BeforeEach
    fun beforeEach() {
        program = Program("foo", DummyFunctions, DummyMemsizer)
    }

    private fun makeImporter(vararg searchIn: String): ModuleImporter = makeImporter(searchIn.asList())

    private fun makeImporter(searchIn: Iterable<String>) =
        ModuleImporter(program, "blah", searchIn.toList())

    @Nested
    inner class Constructor {

        @Test
        @Disabled("TODO: invalid entries in search list")
        fun testInvalidEntriesInSearchList() {}

        @Test
        @Disabled("TODO: literal duplicates in search list")
        fun testLiteralDuplicatesInSearchList() {}

        @Test
        @Disabled("TODO: factual duplicates in search list")
        fun testFactualDuplicatesInSearchList() {}
    }

    @Nested
    inner class ImportModule {

        @Nested
        inner class WithInvalidPath {
            @Test
            fun testNonexisting() {
                val dirRel = assumeDirectory(".", workingDir.relativize(fixturesDir))
                val importer = makeImporter(dirRel.invariantSeparatorsPathString)
                val srcPathRel = assumeNotExists(dirRel, "i_do_not_exist")
                val srcPathAbs = srcPathRel.absolute()

                assertThrows<NoSuchFileException> { importer.importModule(srcPathRel) }
                    .let {
                        assertThat(
                            ".file should be normalized",
                            "${it.file}", equalTo("${it.file.normalize()}")
                        )
                        assertThat(
                            ".file should point to specified path",
                            it.file.absolutePath, equalTo("${srcPathAbs.normalize()}")
                        )
                    }
                assertThat(program.modules.size, equalTo(1))

                assertThrows<NoSuchFileException> { importer.importModule(srcPathAbs) }
                    .let {
                        assertThat(
                            ".file should be normalized",
                            "${it.file}", equalTo("${it.file.normalize()}")
                        )
                        assertThat(
                            ".file should point to specified path",
                            it.file.absolutePath, equalTo("${srcPathAbs.normalize()}")
                        )
                    }
                assertThat(program.modules.size, equalTo(1))
            }

            @Test
            fun testDirectory() {
                val srcPathRel = assumeDirectory(workingDir.relativize(fixturesDir))
                val srcPathAbs = srcPathRel.absolute()
                val searchIn = Path(".", "$srcPathRel").invariantSeparatorsPathString
                val importer = makeImporter(searchIn)

                assertThrows<AccessDeniedException> { importer.importModule(srcPathRel) }
                    .let {
                        assertThat(
                            ".file should be normalized",
                            "${it.file}", equalTo("${it.file.normalize()}")
                        )
                        assertThat(
                            ".file should point to specified path",
                            it.file.absolutePath, equalTo("${srcPathAbs.normalize()}")
                        )
                    }
                assertThat(program.modules.size, equalTo(1))

                assertThrows<AccessDeniedException> { importer.importModule(srcPathAbs) }
                    .let {
                        assertThat(
                            ".file should be normalized",
                            "${it.file}", equalTo("${it.file.normalize()}")
                        )
                        assertThat(
                            ".file should point to specified path",
                            it.file.absolutePath, equalTo("${srcPathAbs.normalize()}")
                        )
                    }
                assertThat(program.modules.size, equalTo(1))
            }
        }

        @Nested
        inner class WithValidPath {

            @Test
            fun testAbsolute() {
                val searchIn = listOf(
                    Path(".").div(workingDir.relativize(fixturesDir)), // we do want a dot "." in front
                ).map { it.invariantSeparatorsPathString }
                val importer = makeImporter(searchIn)
                val fileName = "simple_main.p8"
                val path = assumeReadableFile(searchIn[0], fileName)

                val module = importer.importModule(path.absolute())
                assertThat(program.modules.size, equalTo(2))
                assertContains(program.modules, module)
                assertThat(module.program, equalTo(program))
            }

            @Test
            fun testRelativeToWorkingDir() {
                val searchIn = listOf(
                    Path(".").div(workingDir.relativize(fixturesDir)), // we do want a dot "." in front
                ).map { it.invariantSeparatorsPathString }
                val importer = makeImporter(searchIn)
                val fileName = "simple_main.p8"
                val path = assumeReadableFile(searchIn[0], fileName)
                assertThat("sanity check: path should NOT be absolute", path.isAbsolute, equalTo(false))

                val module = importer.importModule(path)
                assertThat(program.modules.size, equalTo(2))
                assertContains(program.modules, module)
                assertThat(module.program, equalTo(program))
            }

            @Test
            fun testRelativeTo1stDirInSearchList() {
                val searchIn = Path(".")
                    .div(workingDir.relativize(fixturesDir))
                    .invariantSeparatorsPathString
                val importer = makeImporter(searchIn)
                val fileName = "simple_main.p8"
                val path = Path(".", fileName)
                assumeReadableFile(searchIn, path)

                val module = importer.importModule(path)
                assertThat(program.modules.size, equalTo(2))
                assertContains(program.modules, module)
                assertThat(module.program, equalTo(program))
            }

            @Test
            @Disabled("TODO: relative to 2nd in search list")
            fun testRelativeTo2ndDirInSearchList() {}

            @Test
            @Disabled("TODO: ambiguous - 2 or more really different candidates")
            fun testAmbiguousCandidates() {}

            @Nested
            inner class WithBadFile {
                @Test
                fun testWithSyntaxError() {
                    val searchIn = assumeDirectory("./", workingDir.relativize(fixturesDir))
                    val importer = makeImporter(searchIn.invariantSeparatorsPathString)
                    val srcPath = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

                    val act = { importer.importModule(srcPath) }

                    repeat(2) { n ->
                        assertThrows<ParseError>(count[n] + " call") { act() }.let {
                            assertThat(it.position.file, equalTo(srcPath.absolutePathString()))
                            assertThat("line; should be 1-based", it.position.line, equalTo(2))
                            assertThat("startCol; should be 0-based", it.position.startCol, equalTo(6))
                            assertThat("endCol; should be 0-based", it.position.endCol, equalTo(6))
                        }
                        assertThat(program.modules.size, equalTo(1))
                    }
                }

                @Test
                fun testImportingFileWithSyntaxError_once() {
                    doTestImportingFileWithSyntaxError(1)
                }

                @Test
                @Disabled("TODO: module that imports faulty module should not be kept in Program.modules")
                fun testImportingFileWithSyntaxError_twice() {
                    doTestImportingFileWithSyntaxError(2)
                }

                private fun doTestImportingFileWithSyntaxError(repetitions: Int) {
                    val searchIn = assumeDirectory("./", workingDir.relativize(fixturesDir))
                    val importer = makeImporter(searchIn.invariantSeparatorsPathString)
                    val importing = assumeReadableFile(fixturesDir, "import_file_with_syntax_error.p8")
                    val imported = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

                    val act = { importer.importModule(importing) }

                    repeat(repetitions) { n ->
                        assertThrows<ParseError>(count[n] + " call") { act() }.let {
                            assertThat(it.position.file, equalTo(imported.absolutePathString()))
                            assertThat("line; should be 1-based", it.position.line, equalTo(2))
                            assertThat("startCol; should be 0-based", it.position.startCol, equalTo(6))
                            assertThat("endCol; should be 0-based", it.position.endCol, equalTo(6))
                        }
                        assertThat(program.modules.size, equalTo(2))
                    }
                }
            }
        }

    }

    @Nested
    inner class ImportLibraryModule {
        @Nested
        inner class WithInvalidName {
            @Test
            fun testWithNonExistingName() {
                val searchIn = assumeDirectory("./", workingDir.relativize(fixturesDir))
                val importer = makeImporter(searchIn.invariantSeparatorsPathString)
                val filenameNoExt = assumeNotExists(fixturesDir, "i_do_not_exist").name
                val filenameWithExt = assumeNotExists(fixturesDir, "i_do_not_exist.p8").name

                repeat(2) { n ->
                    assertThrows<NoSuchFileException>(count[n] + " call / NO .p8 extension")
                    { importer.importLibraryModule(filenameNoExt) }.let {
                        assertThat(it.message!!, containsString(filenameWithExt))
                    }
                    assertThat(program.modules.size, equalTo(1))

                    assertThrows<NoSuchFileException>(count[n] + " call / with .p8 extension")
                    { importer.importLibraryModule(filenameWithExt) }.let {
                        assertThat(it.message!!, containsString(filenameWithExt))
                    }
                    assertThat(program.modules.size, equalTo(1))
                }
            }
        }

        @Nested
        inner class WithValidName {
            @Nested
            inner class WithBadFile {
                @Test
                fun testWithSyntaxError() {
                    val searchIn = assumeDirectory("./", workingDir.relativize(fixturesDir))
                    val importer = makeImporter(searchIn.invariantSeparatorsPathString)
                    val srcPath = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

                    repeat(2) { n ->
                        assertThrows<ParseError>(count[n] + " call")
                            { importer.importLibraryModule(srcPath.nameWithoutExtension) }.let {
                                assertThat(it.position.file, equalTo(srcPath.absolutePathString()))
                                assertThat("line; should be 1-based", it.position.line, equalTo(2))
                                assertThat("startCol; should be 0-based", it.position.startCol, equalTo(6))
                                assertThat("endCol; should be 0-based", it.position.endCol, equalTo(6))
                            }
                        assertThat(program.modules.size, equalTo(1))
                    }
                }


                private fun doTestImportingFileWithSyntaxError(repetitions: Int) {
                    val searchIn = assumeDirectory("./", workingDir.relativize(fixturesDir))
                    val importer = makeImporter(searchIn.invariantSeparatorsPathString)
                    val importing = assumeReadableFile(fixturesDir, "import_file_with_syntax_error.p8")
                    val imported = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

                    val act = { importer.importLibraryModule(importing.nameWithoutExtension) }

                    repeat(repetitions) { n ->
                        assertThrows<ParseError>(count[n] + " call") { act() }.let {
                            assertThat(it.position.file, equalTo(imported.normalize().absolutePathString()))
                            assertThat("line; should be 1-based", it.position.line, equalTo(2))
                            assertThat("startCol; should be 0-based", it.position.startCol, equalTo(6))
                            assertThat("endCol; should be 0-based", it.position.endCol, equalTo(6))
                        }
                        assertThat(program.modules.size, equalTo(2))
                    }
                }

                @Test
                fun testImportingFileWithSyntaxError_once() {
                    doTestImportingFileWithSyntaxError(1)
                }

                @Test
                @Disabled("TODO: module that imports faulty module should not be kept in Program.modules")
                fun testImportingFileWithSyntaxError_twice() {
                    doTestImportingFileWithSyntaxError(2)
                }
            }
        }
    }
}
