package prog8tests

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8tests.helpers.Helpers
import kotlin.io.path.Path
import kotlin.io.path.div


// Do not move into folder helpers/!
// This folder is also used by compiler/test
// but the testing of the helpers themselves must be performed ONLY HERE.
//
class PathsHelpersTests: FunSpec({

    context("AssumeNotExists") {

        context("WithOnePathArg") {

            test("on non-existing path") {
                val path = Helpers.fixturesDir / "i_do_not_exist"
                withClue("should return the path") {
                    Helpers.assumeNotExists(path) shouldBe path
                }
            }

            test("on existing file") {
                shouldThrow<java.lang.AssertionError> {
                    Helpers.assumeNotExists(Helpers.fixturesDir / "ast_simple_main.p8")
                }
            }

            test("on existing directory") {
                shouldThrow<java.lang.AssertionError> {
                    Helpers.assumeNotExists(Helpers.fixturesDir)
                }
            }
        }

        context("WithOneStringArg") {

            test("on non-existing path") {
                val path = Helpers.fixturesDir / "i_do_not_exist"
                withClue("should return the path") {
                    Helpers.assumeNotExists("$path") shouldBe path
                }
            }

            test("on existing file") {
                val path = Helpers.fixturesDir / "ast_simple_main.p8"
                shouldThrow<java.lang.AssertionError> {
                    Helpers.assumeNotExists("$path")
                }
            }

            test("on existing directory") {
                shouldThrow<java.lang.AssertionError> {
                    Helpers.assumeNotExists("${Helpers.fixturesDir}")
                }
            }
        }

        context("WithPathAndStringArgs") {

            test("on non-existing path") {
                val path = Helpers.fixturesDir / "i_do_not_exist"
                withClue("should return the path") {
                    Helpers.assumeNotExists(Helpers.fixturesDir / "i_do_not_exist") shouldBe path
                }
            }

            test("on existing file") {
                shouldThrow<java.lang.AssertionError> {
                    Helpers.assumeNotExists(Helpers.fixturesDir, "ast_simple_main.p8")
                }
            }

            test("on existing directory") {
                shouldThrow<java.lang.AssertionError> {
                    Helpers.assumeNotExists(Helpers.fixturesDir, "..")
                }
            }
        }
    }

    context("AssumeDirectory") {

        context("WithOnePathArg") {
            test("on non-existing path") {
                val path = Helpers.fixturesDir / "i_do_not_exist"
                shouldThrow<AssertionError> {
                    Helpers.assumeDirectory(path)
                }
            }

            test("on existing file") {
                val path = Helpers.fixturesDir / "ast_simple_main.p8"
                shouldThrow<AssertionError> {
                    Helpers.assumeDirectory(path)
                }
            }

            test("on existing directory") {
                val path = Helpers.workingDir
                withClue("should return the path") {
                    Helpers.assumeDirectory(path) shouldBe path
                }
            }
        }

        context("WithOneStringArg") {
            test("on non-existing path") {
                val path = Helpers.fixturesDir / "i_do_not_exist"
                shouldThrow<AssertionError> {
                    Helpers.assumeDirectory("$path")
                }
            }

            test("on existing file") {
                val path = Helpers.fixturesDir / "ast_simple_main.p8"
                shouldThrow<AssertionError> {
                    Helpers.assumeDirectory("$path")
                }
            }

            test("on existing directory") {
                val path = Helpers.workingDir
                withClue("should return the path") {
                    Helpers.assumeDirectory("$path") shouldBe path
                }
            }
        }

        context("WithPathAndStringArgs") {
            test("on non-existing path") {
                shouldThrow<AssertionError> {
                    Helpers.assumeDirectory(Helpers.fixturesDir, "i_do_not_exist")
                }
            }

            test("on existing file") {
                shouldThrow<AssertionError> {
                    Helpers.assumeDirectory(Helpers.fixturesDir, "ast_simple_main.p8")
                }
            }

            test("on existing directory") {
                val path = Helpers.workingDir / ".."
                withClue("should return resulting path") {
                    Helpers.assumeDirectory(Helpers.workingDir / "..") shouldBe path
                }
            }
        }

        context("WithStringAndStringArgs") {
            test("on non-existing path") {
                shouldThrow<AssertionError> {
                    Helpers.assumeDirectory("${Helpers.fixturesDir}", "i_do_not_exist")
                }
            }

            test("on existing file") {
                shouldThrow<AssertionError> {
                    Helpers.assumeDirectory("${Helpers.fixturesDir}", "ast_simple_main.p8")
                }
            }

            test("on existing directory") {
                val path = Helpers.workingDir / ".."
                withClue("should return resulting path") {
                    Helpers.assumeDirectory(Helpers.workingDir / "..") shouldBe path
                }
            }
        }

        context("WithStringAndPathArgs") {
            test("on non-existing path") {
                shouldThrow<AssertionError> {
                    Helpers.assumeDirectory("${Helpers.fixturesDir}", Path("i_do_not_exist"))
                }
            }

            test("on existing file") {
                shouldThrow<AssertionError> {
                    Helpers.assumeDirectory("${Helpers.fixturesDir}", Path("ast_simple_main.p8"))
                }
            }

            test("on existing directory") {
                val path = Helpers.workingDir / ".."
                withClue("should return resulting path") {
                    Helpers.assumeDirectory(Helpers.workingDir / Path("..")) shouldBe path
                }
            }
        }
    }

    context("AssumeReadableFile") {

        context("WithOnePathArg") {

            test("on non-existing path") {
                val path = Helpers.fixturesDir / "i_do_not_exist"
                shouldThrow<AssertionError> {
                    Helpers.assumeReadableFile(path)
                }
            }

            test("on readable file") {
                val path = Helpers.fixturesDir / "ast_simple_main.p8"
                withClue("should return the path") {
                    Helpers.assumeReadableFile(path) shouldBe path
                }
            }

            test("on directory") {
                shouldThrow<AssertionError> {
                    Helpers.assumeReadableFile(Helpers.fixturesDir)
                }
            }
        }

        context("WithOneStringArg") {

            test("on non-existing path") {
                val path = Helpers.fixturesDir / "i_do_not_exist"
                shouldThrow<AssertionError> {
                    Helpers.assumeReadableFile("$path")
                }
            }

            test("on readable file") {
                val path = Helpers.fixturesDir / "ast_simple_main.p8"
                withClue("should return the resulting path") {
                    Helpers.assumeReadableFile("$path") shouldBe path
                }
            }

            test("on directory") {
                shouldThrow<AssertionError> {
                    Helpers.assumeReadableFile("${Helpers.fixturesDir}")
                }
            }
        }

        context("WithPathAndStringArgs") {
            test("on non-existing path") {
                shouldThrow<java.lang.AssertionError> {
                    Helpers.assumeReadableFile(Helpers.fixturesDir, "i_do_not_exist")
                }
            }

            test("on readable file") {
                val path = Helpers.fixturesDir / "ast_simple_main.p8"
                withClue("should return the resulting path") {
                    Helpers.assumeReadableFile(Helpers.fixturesDir / "ast_simple_main.p8") shouldBe path
                }
            }

            test("on directory") {
                shouldThrow<AssertionError> {
                    Helpers.assumeReadableFile(Helpers.fixturesDir, "..")
                }
            }
        }

        context("WithPathAndPathArgs") {
            test("on non-existing path") {
                shouldThrow<java.lang.AssertionError> {
                    Helpers.assumeReadableFile(Helpers.fixturesDir, Path("i_do_not_exist"))
                }
            }

            test("on readable file") {
                withClue("should return the resulting path") {
                    Helpers.assumeReadableFile(Helpers.fixturesDir / Path("ast_simple_main.p8")) shouldBe Helpers.fixturesDir / "ast_simple_main.p8"
                }
            }

            test("on directory") {
                shouldThrow<AssertionError> {
                    Helpers.assumeReadableFile(Helpers.fixturesDir, Path(".."))
                }
            }
        }

        context("WithStringAndStringArgs") {
            test("on non-existing path") {
                shouldThrow<java.lang.AssertionError> {
                    Helpers.assumeReadableFile("${Helpers.fixturesDir}", "i_do_not_exist")
                }
            }

            test("on readable file") {
                withClue("should return the resulting path") {
                    Helpers.assumeReadableFile(Helpers.fixturesDir / "ast_simple_main.p8") shouldBe  Helpers.fixturesDir / "ast_simple_main.p8"
                }
            }

            test("on directory") {
                shouldThrow<AssertionError> {
                    Helpers.assumeReadableFile("${Helpers.fixturesDir}", "..")
                }
            }
        }
    }
})
