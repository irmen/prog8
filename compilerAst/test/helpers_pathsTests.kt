package prog8tests.ast

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import prog8tests.ast.helpers.*
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


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
                    assertEquals(path, assumeNotExists(path))
                }
            }

            test("on existing file") {
                assertFailsWith<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir / "simple_main.p8")
                }
            }

            test("on existing directory") {
                assertFailsWith<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir)
                }
            }
        }

        context("WithOneStringArg") {

            test("on non-existing path") {
                val path = fixturesDir / "i_do_not_exist"
                withClue("should return the path") {
                    assertEquals(path, assumeNotExists("$path"))
                }
            }

            test("on existing file") {
                val path = fixturesDir / "simple_main.p8"
                assertFailsWith<java.lang.AssertionError> {
                    assumeNotExists("$path")
                }
            }

            test("on existing directory") {
                assertFailsWith<java.lang.AssertionError> {
                    assumeNotExists("$fixturesDir")
                }
            }
        }

        context("WithPathAndStringArgs") {

            test("on non-existing path") {
                val path = fixturesDir / "i_do_not_exist"
                withClue("should return the path") {
                    assertEquals(path, assumeNotExists(fixturesDir, "i_do_not_exist"))
                }
            }

            test("on existing file") {
                assertFailsWith<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir, "simple_main.p8")
                }
            }

            test("on existing directory") {
                assertFailsWith<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir, "..")
                }
            }
        }
    }

    context("AssumeDirectory") {

        context("WithOnePathArg") {
            test("on non-existing path") {
                val path = fixturesDir / "i_do_not_exist"
                assertFailsWith<AssertionError> {
                    assumeDirectory(path)
                }
            }

            test("on existing file") {
                val path = fixturesDir / "simple_main.p8"
                assertFailsWith<AssertionError> {
                    assumeDirectory(path)
                }
            }

            test("on existing directory") {
                val path = workingDir
                withClue("should return the path") {
                    assertEquals(path, assumeDirectory(path))
                }
            }
        }

        context("WithOneStringArg") {
            test("on non-existing path") {
                val path = fixturesDir / "i_do_not_exist"
                assertFailsWith<AssertionError> {
                    assumeDirectory("$path")
                }
            }

            test("on existing file") {
                val path = fixturesDir / "simple_main.p8"
                assertFailsWith<AssertionError> {
                    assumeDirectory("$path")
                }
            }

            test("on existing directory") {
                val path = workingDir
                withClue("should return the path") {
                    assertEquals(path, assumeDirectory("$path"))
                }
            }
        }

        context("WithPathAndStringArgs") {
            test("on non-existing path") {
                assertFailsWith<AssertionError> {
                    assumeDirectory(fixturesDir, "i_do_not_exist")
                }
            }

            test("on existing file") {
                assertFailsWith<AssertionError> {
                    assumeDirectory(fixturesDir, "simple_main.p8")
                }
            }

            test("on existing directory") {
                val path = workingDir / ".."
                withClue("should return resulting path") {
                    assertEquals(path, assumeDirectory(workingDir, ".."))
                }
            }
        }

        context("WithStringAndStringArgs") {
            test("on non-existing path") {
                assertFailsWith<AssertionError> {
                    assumeDirectory("$fixturesDir", "i_do_not_exist")
                }
            }

            test("on existing file") {
                assertFailsWith<AssertionError> {
                    assumeDirectory("$fixturesDir", "simple_main.p8")
                }
            }

            test("on existing directory") {
                val path = workingDir / ".."
                withClue("should return resulting path") {
                    assertEquals(path, assumeDirectory("$workingDir", ".."))
                }
            }
        }

        context("WithStringAndPathArgs") {
            test("on non-existing path") {
                assertFailsWith<AssertionError> {
                    assumeDirectory("$fixturesDir", Path("i_do_not_exist"))
                }
            }

            test("on existing file") {
                assertFailsWith<AssertionError> {
                    assumeDirectory("$fixturesDir", Path("simple_main.p8"))
                }
            }

            test("on existing directory") {
                val path = workingDir / ".."
                withClue("should return resulting path") {
                    assertEquals(path, assumeDirectory("$workingDir", Path("..")))
                }
            }
        }
    }

    context("AssumeReadableFile") {

        context("WithOnePathArg") {

            test("on non-existing path") {
                val path = fixturesDir / "i_do_not_exist"
                assertFailsWith<AssertionError> {
                    assumeReadableFile(path)
                }
            }

            test("on readable file") {
                val path = fixturesDir / "simple_main.p8"
                withClue("should return the path") {
                    assertEquals(path, assumeReadableFile(path))
                }
            }

            test("on directory") {
                assertFailsWith<AssertionError> {
                    assumeReadableFile(fixturesDir)
                }
            }
        }

        context("WithOneStringArg") {

            test("on non-existing path") {
                val path = fixturesDir / "i_do_not_exist"
                assertFailsWith<AssertionError> {
                    assumeReadableFile("$path")
                }
            }

            test("on readable file") {
                val path = fixturesDir / "simple_main.p8"
                withClue("should return the resulting path") {
                    assertEquals(path, assumeReadableFile("$path"))
                }
            }

            test("on directory") {
                assertFailsWith<AssertionError> {
                    assumeReadableFile("$fixturesDir")
                }
            }
        }

        context("WithPathAndStringArgs") {
            test("on non-existing path") {
                assertFailsWith<java.lang.AssertionError> {
                    assumeReadableFile(fixturesDir, "i_do_not_exist")
                }
            }

            test("on readable file") {
                val path = fixturesDir / "simple_main.p8"
                withClue("should return the resulting path") {
                    assertEquals(path, assumeReadableFile(fixturesDir, "simple_main.p8"))
                }
            }

            test("on directory") {
                assertFailsWith<AssertionError> {
                    assumeReadableFile(fixturesDir, "..")
                }
            }
        }

        context("WithPathAndPathArgs") {
            test("on non-existing path") {
                assertFailsWith<java.lang.AssertionError> {
                    assumeReadableFile(fixturesDir, Path("i_do_not_exist"))
                }
            }

            test("on readable file") {
                withClue("should return the resulting path") {
                    assertEquals(fixturesDir / "simple_main.p8", assumeReadableFile(fixturesDir, Path("simple_main.p8")))
                }
            }

            test("on directory") {
                assertFailsWith<AssertionError> {
                    assumeReadableFile(fixturesDir, Path(".."))
                }
            }
        }

        context("WithStringAndStringArgs") {
            test("on non-existing path") {
                assertFailsWith<java.lang.AssertionError> {
                    assumeReadableFile("$fixturesDir", "i_do_not_exist")
                }
            }

            test("on readable file") {
                withClue("should return the resulting path") {
                    assertEquals( fixturesDir / "simple_main.p8", assumeReadableFile(fixturesDir.toString(), "simple_main.p8"))
                }
            }

            test("on directory") {
                assertFailsWith<AssertionError> {
                    assumeReadableFile("$fixturesDir", "..")
                }
            }
        }
    }
})
