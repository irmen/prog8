package prog8tests.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8tests.helpers.*
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
                val path = fixturesDir / "i_do_not_exist"
                withClue("should return the path") {
                    assumeNotExists(path) shouldBe path
                }
            }

            test("on existing file") {
                shouldThrow<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir / "ast_simple_main.p8")
                }
            }

            test("on existing directory") {
                shouldThrow<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir)
                }
            }
        }

        context("WithOneStringArg") {

            test("on non-existing path") {
                val path = fixturesDir / "i_do_not_exist"
                withClue("should return the path") {
                    assumeNotExists("$path") shouldBe path
                }
            }

            test("on existing file") {
                val path = fixturesDir / "ast_simple_main.p8"
                shouldThrow<java.lang.AssertionError> {
                    assumeNotExists("$path")
                }
            }

            test("on existing directory") {
                shouldThrow<java.lang.AssertionError> {
                    assumeNotExists("$fixturesDir")
                }
            }
        }

        context("WithPathAndStringArgs") {

            test("on non-existing path") {
                val path = fixturesDir / "i_do_not_exist"
                withClue("should return the path") {
                    assumeNotExists(fixturesDir / "i_do_not_exist") shouldBe path
                }
            }

            test("on existing file") {
                shouldThrow<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir, "ast_simple_main.p8")
                }
            }

            test("on existing directory") {
                shouldThrow<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir, "..")
                }
            }
        }
    }

    context("AssumeDirectory") {

        context("WithOnePathArg") {
            test("on non-existing path") {
                val path = fixturesDir / "i_do_not_exist"
                shouldThrow<AssertionError> {
                    assumeDirectory(path)
                }
            }

            test("on existing file") {
                val path = fixturesDir / "ast_simple_main.p8"
                shouldThrow<AssertionError> {
                    assumeDirectory(path)
                }
            }

            test("on existing directory") {
                val path = workingDir
                withClue("should return the path") {
                    assumeDirectory(path) shouldBe path
                }
            }
        }

        context("WithOneStringArg") {
            test("on non-existing path") {
                val path = fixturesDir / "i_do_not_exist"
                shouldThrow<AssertionError> {
                    assumeDirectory("$path")
                }
            }

            test("on existing file") {
                val path = fixturesDir / "ast_simple_main.p8"
                shouldThrow<AssertionError> {
                    assumeDirectory("$path")
                }
            }

            test("on existing directory") {
                val path = workingDir
                withClue("should return the path") {
                    assumeDirectory("$path") shouldBe path
                }
            }
        }

        context("WithPathAndStringArgs") {
            test("on non-existing path") {
                shouldThrow<AssertionError> {
                    assumeDirectory(fixturesDir, "i_do_not_exist")
                }
            }

            test("on existing file") {
                shouldThrow<AssertionError> {
                    assumeDirectory(fixturesDir, "ast_simple_main.p8")
                }
            }

            test("on existing directory") {
                val path = workingDir / ".."
                withClue("should return resulting path") {
                    assumeDirectory(workingDir / "..") shouldBe path
                }
            }
        }

        context("WithStringAndStringArgs") {
            test("on non-existing path") {
                shouldThrow<AssertionError> {
                    assumeDirectory("$fixturesDir", "i_do_not_exist")
                }
            }

            test("on existing file") {
                shouldThrow<AssertionError> {
                    assumeDirectory("$fixturesDir", "ast_simple_main.p8")
                }
            }

            test("on existing directory") {
                val path = workingDir / ".."
                withClue("should return resulting path") {
                    assumeDirectory(workingDir / "..") shouldBe path
                }
            }
        }

        context("WithStringAndPathArgs") {
            test("on non-existing path") {
                shouldThrow<AssertionError> {
                    assumeDirectory("$fixturesDir", Path("i_do_not_exist"))
                }
            }

            test("on existing file") {
                shouldThrow<AssertionError> {
                    assumeDirectory("$fixturesDir", Path("ast_simple_main.p8"))
                }
            }

            test("on existing directory") {
                val path = workingDir / ".."
                withClue("should return resulting path") {
                    assumeDirectory(workingDir / Path("..")) shouldBe path
                }
            }
        }
    }

    context("AssumeReadableFile") {

        context("WithOnePathArg") {

            test("on non-existing path") {
                val path = fixturesDir / "i_do_not_exist"
                shouldThrow<AssertionError> {
                    assumeReadableFile(path)
                }
            }

            test("on readable file") {
                val path = fixturesDir / "ast_simple_main.p8"
                withClue("should return the path") {
                    assumeReadableFile(path) shouldBe path
                }
            }

            test("on directory") {
                shouldThrow<AssertionError> {
                    assumeReadableFile(fixturesDir)
                }
            }
        }

        context("WithOneStringArg") {

            test("on non-existing path") {
                val path = fixturesDir / "i_do_not_exist"
                shouldThrow<AssertionError> {
                    assumeReadableFile("$path")
                }
            }

            test("on readable file") {
                val path = fixturesDir / "ast_simple_main.p8"
                withClue("should return the resulting path") {
                    assumeReadableFile("$path") shouldBe path
                }
            }

            test("on directory") {
                shouldThrow<AssertionError> {
                    assumeReadableFile("$fixturesDir")
                }
            }
        }

        context("WithPathAndStringArgs") {
            test("on non-existing path") {
                shouldThrow<java.lang.AssertionError> {
                    assumeReadableFile(fixturesDir, "i_do_not_exist")
                }
            }

            test("on readable file") {
                val path = fixturesDir / "ast_simple_main.p8"
                withClue("should return the resulting path") {
                    assumeReadableFile(fixturesDir / "ast_simple_main.p8") shouldBe path
                }
            }

            test("on directory") {
                shouldThrow<AssertionError> {
                    assumeReadableFile(fixturesDir, "..")
                }
            }
        }

        context("WithPathAndPathArgs") {
            test("on non-existing path") {
                shouldThrow<java.lang.AssertionError> {
                    assumeReadableFile(fixturesDir, Path("i_do_not_exist"))
                }
            }

            test("on readable file") {
                withClue("should return the resulting path") {
                    assumeReadableFile(fixturesDir / Path("ast_simple_main.p8")) shouldBe fixturesDir / "ast_simple_main.p8"
                }
            }

            test("on directory") {
                shouldThrow<AssertionError> {
                    assumeReadableFile(fixturesDir, Path(".."))
                }
            }
        }

        context("WithStringAndStringArgs") {
            test("on non-existing path") {
                shouldThrow<java.lang.AssertionError> {
                    assumeReadableFile("$fixturesDir", "i_do_not_exist")
                }
            }

            test("on readable file") {
                withClue("should return the resulting path") {
                    assumeReadableFile(fixturesDir / "ast_simple_main.p8") shouldBe  fixturesDir / "ast_simple_main.p8"
                }
            }

            test("on directory") {
                shouldThrow<AssertionError> {
                    assumeReadableFile("$fixturesDir", "..")
                }
            }
        }
    }
})
