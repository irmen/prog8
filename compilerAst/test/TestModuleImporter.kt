package prog8tests

import prog8tests.helpers.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.*

import prog8.ast.Program
import prog8.parser.ParseError

import prog8.parser.ModuleImporter


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestModuleImporter {
    private val count = listOf("1st", "2nd", "3rd", "4th", "5th")

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
                val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
                val dirRel = assumeDirectory(".", workingDir.relativize(fixturesDir))
                val searchIn = dirRel.invariantSeparatorsPathString
                val importer = ModuleImporter(program, "blah", listOf(searchIn))
                val srcPathRel = assumeNotExists(dirRel, "i_do_not_exist")
                val srcPathAbs = srcPathRel.absolute()

                assertThrows<NoSuchFileException> { importer.importModule(srcPathRel) }
                    .let {
                        assertThat(
                            ".file should be normalized",
                            "${it.file}", `is`("${it.file.normalize()}")
                        )
                        assertThat(
                            ".file should point to specified path",
                            it.file.absolutePath, `is`("${srcPathAbs.normalize()}")
                        )
                    }

                assertThrows<NoSuchFileException> { importer.importModule(srcPathAbs) }
                    .let {
                        assertThat(
                            ".file should be normalized",
                            "${it.file}", `is`("${it.file.normalize()}")
                        )
                        assertThat(
                            ".file should point to specified path",
                            it.file.absolutePath, `is`("${srcPathAbs.normalize()}")
                        )
                    }
            }

            @Test
            fun testDirectory() {
                val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
                val dirRel = assumeDirectory(workingDir.relativize(fixturesDir))
                val searchIn = Path(".", "$dirRel").invariantSeparatorsPathString
                val importer = ModuleImporter(program, "blah", listOf(searchIn))
                val srcPathRel = dirRel
                val srcPathAbs = srcPathRel.absolute()

                assertThrows<AccessDeniedException> { importer.importModule(srcPathRel) }
                    .let {
                        assertThat(
                            ".file should be normalized",
                            "${it.file}", `is`("${it.file.normalize()}")
                        )
                        assertThat(
                            ".file should point to specified path",
                            it.file.absolutePath, `is`("${srcPathAbs.normalize()}")
                        )
                    }

                assertThrows<AccessDeniedException> { importer.importModule(srcPathAbs) }
                    .let {
                        assertThat(
                            ".file should be normalized",
                            "${it.file}", `is`("${it.file.normalize()}")
                        )
                        assertThat(
                            ".file should point to specified path",
                            it.file.absolutePath, `is`("${srcPathAbs.normalize()}")
                        )
                    }
            }
        }

        @Nested
        inner class WithValidPath {

            @Test
            fun testAbsolute() {
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
            fun testRelativeToWorkingDir() {
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
            fun testRelativeTo1stDirInSearchList() {
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
            @Disabled("TODO: relative to 2nd in search list")
            fun testRelativeTo2ndDirInSearchList() {}

            @Test
            @Disabled("TODO: ambiguous - 2 or more really different candidates")
            fun testAmbiguousCandidates() {}

            @Nested
            inner class WithBadFile {
                @Test
                fun testWithSyntaxError() {
                    val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
                    val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
                    val importer = ModuleImporter(program, "blah", listOf(searchIn))
                    val srcPath = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

                    val act = { importer.importModule(srcPath) }

                    repeat(2) { n ->
                        assertThrows<ParseError>(count[n] + " call") { act() }.let {
                            assertThat(it.position.file, `is`(srcPath.absolutePathString()))
                            assertThat("line; should be 1-based", it.position.line, `is`(2))
                            assertThat("startCol; should be 0-based", it.position.startCol, `is`(6))
                            assertThat("endCol; should be 0-based", it.position.endCol, `is`(6))
                        }
                    }
                }

                @Test
                fun testImportingFileWithSyntaxError() {
                    val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
                    val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
                    val importer = ModuleImporter(program, "blah", listOf(searchIn))
                    val importing = assumeReadableFile(fixturesDir, "import_file_with_syntax_error.p8")
                    val imported = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

                    val act = { importer.importModule(importing) }

                    repeat(2) { n ->
                        assertThrows<ParseError>(count[n] + " call") { act() }.let {
                            assertThat(it.position.file, `is`(imported.absolutePathString()))
                            assertThat("line; should be 1-based", it.position.line, `is`(2))
                            assertThat("startCol; should be 0-based", it.position.startCol, `is`(6))
                            assertThat("endCol; should be 0-based", it.position.endCol, `is`(6))
                        }
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
                val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
                val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
                val importer = ModuleImporter(program, "blah", listOf(searchIn))
                val filenameNoExt = assumeNotExists(fixturesDir, "i_do_not_exist").name
                val filenameWithExt = assumeNotExists(fixturesDir, "i_do_not_exist.p8").name

                repeat(2) { n ->
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
        }

        @Nested
        inner class WithValidName {
            @Nested
            inner class WithBadFile {
                @Test
                fun testWithSyntaxError() {
                    val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
                    val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
                    val importer = ModuleImporter(program, "blah", listOf(searchIn))
                    val srcPath = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

                    repeat(2) { n ->
                        assertThrows<ParseError>(count[n] + " call")
                        { importer.importLibraryModule(srcPath.nameWithoutExtension) }.let {
                            assertThat(it.position.file, `is`(srcPath.absolutePathString()))
                            assertThat("line; should be 1-based", it.position.line, `is`(2))
                            assertThat("startCol; should be 0-based", it.position.startCol, `is`(6))
                            assertThat("endCol; should be 0-based", it.position.endCol, `is`(6))
                        }
                    }
                }

                @Test
                fun testImportingFileWithSyntaxError() {
                    val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
                    val searchIn = "./" + workingDir.relativize(fixturesDir).toString().replace("\\", "/")
                    val importer = ModuleImporter(program, "blah", listOf(searchIn))
                    val importing = assumeReadableFile(fixturesDir, "import_file_with_syntax_error.p8")
                    val imported = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

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
        }
    }
}
