package prog8tests

import com.github.michaelbull.result.getErrorOrElse
import com.github.michaelbull.result.getOrElse
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.hamcrest.core.Is
import prog8.ast.Program
import prog8.ast.internedStringsModuleName
import prog8.compiler.ModuleImporter
import prog8.compilerinterface.IErrorReporter
import prog8.parser.ParseError
import prog8.parser.SourceCode
import prog8tests.ast.helpers.*
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.DummyFunctions
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder
import kotlin.io.path.*
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain


class TestModuleImporter: FunSpec({
    val count = listOf("1st", "2nd", "3rd", "4th", "5th")

    lateinit var program: Program

    beforeTest {
        program = Program("foo", DummyFunctions, DummyMemsizer, DummyStringEncoder)
    }

    fun makeImporter(errors: IErrorReporter? = null, searchIn: Iterable<String>) =
        ModuleImporter(program, "blah", errors ?: ErrorReporterForTests(false), searchIn.toList())

    fun makeImporter(errors: IErrorReporter?, vararg searchIn: String): ModuleImporter {
        return makeImporter(errors, searchIn.asList())
    }

    context("Constructor") {

        //Disabled("TODO: invalid entries in search list")
        xtest("testInvalidEntriesInSearchList") {
        }

        //Disabled("TODO: literal duplicates in search list")
        xtest("testLiteralDuplicatesInSearchList") {
        }

        //Disabled("TODO: factual duplicates in search list")
        xtest("testFactualDuplicatesInSearchList") {
        }
    }

    context("ImportModule") {

        context("WithInvalidPath") {
            test("testNonexisting") {
                val dirRel = assumeDirectory(".", workingDir.relativize(fixturesDir))
                val importer = makeImporter(null, dirRel.invariantSeparatorsPathString)
                val srcPathRel = assumeNotExists(dirRel, "i_do_not_exist")
                val srcPathAbs = srcPathRel.absolute()
                val error1 = importer.importModule(srcPathRel).getErrorOrElse { fail("should have import error") }
                assertThat(
                    ".file should be normalized",
                    "${error1.file}", equalTo("${error1.file.normalize()}")
                )
                assertThat(
                    ".file should point to specified path",
                    error1.file.absolutePath, equalTo("${srcPathAbs.normalize()}")
                )
                assertThat(program.modules.size, equalTo(1))

                val error2 = importer.importModule(srcPathAbs).getErrorOrElse { fail("should have import error") }
                assertThat(
                    ".file should be normalized",
                    "${error2.file}", equalTo("${error2.file.normalize()}")
                )
                assertThat(
                    ".file should point to specified path",
                    error2.file.absolutePath, equalTo("${srcPathAbs.normalize()}")
                )
                assertThat(program.modules.size, equalTo(1))
            }

            test("testDirectory") {
                val srcPathRel = assumeDirectory(workingDir.relativize(fixturesDir))
                val srcPathAbs = srcPathRel.absolute()
                val searchIn = Path(".", "$srcPathRel").invariantSeparatorsPathString
                val importer = makeImporter(null, searchIn)

                shouldThrow<AccessDeniedException> { importer.importModule(srcPathRel) }
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

                shouldThrow<AccessDeniedException> { importer.importModule(srcPathAbs) }
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

        context("WithValidPath") {

            test("testAbsolute") {
                val searchIn = listOf(
                    Path(".").div(workingDir.relativize(fixturesDir)), // we do want a dot "." in front
                ).map { it.invariantSeparatorsPathString }
                val importer = makeImporter(null, searchIn)
                val fileName = "simple_main.p8"
                val path = assumeReadableFile(searchIn[0], fileName)

                val module = importer.importModule(path.absolute()).getOrElse { throw it }
                assertThat(program.modules.size, equalTo(2))
                module shouldBeIn program.modules
                assertThat(module.program, equalTo(program))
            }

            test("testRelativeToWorkingDir") {
                val searchIn = listOf(
                    Path(".").div(workingDir.relativize(fixturesDir)), // we do want a dot "." in front
                ).map { it.invariantSeparatorsPathString }
                val importer = makeImporter(null, searchIn)
                val fileName = "simple_main.p8"
                val path = assumeReadableFile(searchIn[0], fileName)
                assertThat("sanity check: path should NOT be absolute", path.isAbsolute, equalTo(false))

                val module = importer.importModule(path).getOrElse { throw it }
                assertThat(program.modules.size, equalTo(2))
                module shouldBeIn program.modules
                assertThat(module.program, equalTo(program))
            }

            test("testRelativeTo1stDirInSearchList") {
                val searchIn = Path(".")
                    .div(workingDir.relativize(fixturesDir))
                    .invariantSeparatorsPathString
                val importer = makeImporter(null, searchIn)
                val fileName = "simple_main.p8"
                val path = Path(".", fileName)
                assumeReadableFile(searchIn, path)

                val module = importer.importModule(path).getOrElse { throw it }
                assertThat(program.modules.size, equalTo(2))
                module shouldBeIn program.modules
                assertThat(module.program, equalTo(program))
            }

            //Disabled("TODO: relative to 2nd in search list")
            xtest("testRelativeTo2ndDirInSearchList") {}

            //Disabled("TODO: ambiguous - 2 or more really different candidates")
            xtest("testAmbiguousCandidates") {}

            context("WithBadFile") {
                test("testWithSyntaxError") {
                    val searchIn = assumeDirectory("./", workingDir.relativize(fixturesDir))
                    val importer = makeImporter(null, searchIn.invariantSeparatorsPathString)
                    val srcPath = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

                    val act = { importer.importModule(srcPath) }

                    repeat(2) { n -> withClue(count[n] + " call") {
                            shouldThrow<ParseError>() { act() }.let {
                                assertThat(it.position.file, equalTo(SourceCode.relative(srcPath).toString()))
                                assertThat("line; should be 1-based", it.position.line, equalTo(2))
                                assertThat("startCol; should be 0-based", it.position.startCol, equalTo(6))
                                assertThat("endCol; should be 0-based", it.position.endCol, equalTo(6))
                            }
                        }
                        assertThat(program.modules.size, equalTo(1))
                    }
                }

                fun doTestImportingFileWithSyntaxError(repetitions: Int) {
                    val searchIn = assumeDirectory("./", workingDir.relativize(fixturesDir))
                    val importer = makeImporter(null, searchIn.invariantSeparatorsPathString)
                    val importing = assumeReadableFile(fixturesDir, "import_file_with_syntax_error.p8")
                    val imported = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

                    val act = { importer.importModule(importing) }

                    repeat(repetitions) { n -> withClue(count[n] + " call") {
                        shouldThrow<ParseError>() { act() }.let {
                            assertThat(it.position.file, equalTo(SourceCode.relative(imported).toString()))
                            assertThat("line; should be 1-based", it.position.line, equalTo(2))
                            assertThat("startCol; should be 0-based", it.position.startCol, equalTo(6))
                            assertThat("endCol; should be 0-based", it.position.endCol, equalTo(6))
                        }
                    }
                        assertThat("imported module with error in it should not be present", program.modules.size, equalTo(1))
                        assertThat(program.modules[0].name, equalTo(internedStringsModuleName))
                    }
                }

                test("testImportingFileWithSyntaxError_once") {
                    doTestImportingFileWithSyntaxError(1)
                }

                test("testImportingFileWithSyntaxError_twice") {
                    doTestImportingFileWithSyntaxError(2)
                }
            }
        }
    }

    context("ImportLibraryModule") {
        context("WithInvalidName") {
            test("testWithNonExistingName") {
                val searchIn = assumeDirectory("./", workingDir.relativize(fixturesDir))
                val errors = ErrorReporterForTests(false)
                val importer = makeImporter(errors, searchIn.invariantSeparatorsPathString)
                val filenameNoExt = assumeNotExists(fixturesDir, "i_do_not_exist").name
                val filenameWithExt = assumeNotExists(fixturesDir, "i_do_not_exist.p8").name

                repeat(2) { n ->
                    val result = importer.importLibraryModule(filenameNoExt)
                    assertThat(count[n] + " call / NO .p8 extension", result, Is(nullValue()))
                    withClue(count[n] + " call / NO .p8 extension") {
                        errors.noErrors() shouldBe false
                    }
                    errors.errors.single() shouldContain "0:0: no module found with name i_do_not_exist"
                    errors.report()
                    assertThat(program.modules.size, equalTo(1))

                    val result2 = importer.importLibraryModule(filenameWithExt)
                    assertThat(count[n] + " call / with .p8 extension", result2, Is(nullValue()))
                    withClue(count[n] + " call / with .p8 extension") {
                        importer.errors.noErrors() shouldBe false
                    }
                    errors.errors.single() shouldContain "0:0: no module found with name i_do_not_exist.p8"
                    errors.report()
                    assertThat(program.modules.size, equalTo(1))
                }
            }
        }

        context("WithValidName") {
            context("WithBadFile") {
                test("testWithSyntaxError") {
                    val searchIn = assumeDirectory("./", workingDir.relativize(fixturesDir))
                    val importer = makeImporter(null, searchIn.invariantSeparatorsPathString)
                    val srcPath = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

                    repeat(2) { n -> withClue(count[n] + " call") {
                            shouldThrow<ParseError>()
                            {
                                importer.importLibraryModule(srcPath.nameWithoutExtension) }.let {
                                assertThat(it.position.file, equalTo(SourceCode.relative(srcPath).toString()))
                                assertThat("line; should be 1-based", it.position.line, equalTo(2))
                                assertThat("startCol; should be 0-based", it.position.startCol, equalTo(6))
                                assertThat("endCol; should be 0-based", it.position.endCol, equalTo(6))
                            }
                        }
                        assertThat(program.modules.size, equalTo(1))
                    }
                }


                fun doTestImportingFileWithSyntaxError(repetitions: Int) {
                    val searchIn = assumeDirectory("./", workingDir.relativize(fixturesDir))
                    val importer = makeImporter(null, searchIn.invariantSeparatorsPathString)
                    val importing = assumeReadableFile(fixturesDir, "import_file_with_syntax_error.p8")
                    val imported = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

                    val act = { importer.importLibraryModule(importing.nameWithoutExtension) }

                    repeat(repetitions) { n -> withClue(count[n] + " call") {
                            shouldThrow<ParseError>() {
                                act() }.let {
                                assertThat(it.position.file, equalTo(SourceCode.relative(imported).toString()))
                                assertThat("line; should be 1-based", it.position.line, equalTo(2))
                                assertThat("startCol; should be 0-based", it.position.startCol, equalTo(6))
                                assertThat("endCol; should be 0-based", it.position.endCol, equalTo(6))
                            }
                        }
                        assertThat("imported module with error in it should not be present", program.modules.size, equalTo(1))
                        assertThat(program.modules[0].name, equalTo(internedStringsModuleName))
                        importer.errors.report()
                    }
                }

                test("testImportingFileWithSyntaxError_once") {
                    doTestImportingFileWithSyntaxError(1)
                }

                test("testImportingFileWithSyntaxError_twice") {
                    doTestImportingFileWithSyntaxError(2)
                }
            }
        }
    }
})
