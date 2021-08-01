package prog8tests

import prog8tests.helpers.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows

import kotlin.io.path.*
import kotlin.test.assertContains

// Do not move into folder helpers/!
// This folder is also used by compiler/test
// but the testing of the helpers themselves must be performed ONLY HERE.
//
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PathsHelpersTests {

    @Nested
    inner class AssumeNotExists {

        @Nested
        inner class WithOnePathArg {

            @Test
            fun testOnNonExistingPath() {
                val path = fixturesDir.div("i_do_not_exist")
                assertThat("should return the path",
                    assumeNotExists(path), `is`(path))
            }

            @Test
            fun testOnExistingFile() {
                assertThrows<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir.div("simple_main.p8"))
                }
            }

            @Test
            fun testOnExistingDirectory() {
                assertThrows<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir)
                }
            }
        }

        @Nested
        inner class WithOneStringArg {

            @Test
            fun testOnNonExistingPath() {
                val path = fixturesDir.div("i_do_not_exist")
                assertThat("should return the path",
                    assumeNotExists("$path"), `is`(path))
            }

            @Test
            fun testOnExistingFile() {
                val path = fixturesDir.div("simple_main.p8")
                assertThrows<java.lang.AssertionError> {
                    assumeNotExists("$path")
                }
            }

            @Test
            fun testOnExistingDirectory() {
                assertThrows<java.lang.AssertionError> {
                    assumeNotExists("$fixturesDir")
                }
            }
        }

        @Nested
        inner class WithPathAndStringArgs {

            @Test
            fun testOnNonExistingPath() {
                val path = fixturesDir.div("i_do_not_exist")
                assertThat("should return the path",
                    assumeNotExists(fixturesDir, "i_do_not_exist"), `is`(path))
            }

            @Test
            fun testOnExistingFile() {
                assertThrows<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir, "simple_main.p8")
                }
            }

            @Test
            fun testOnExistingDirectory() {
                assertThrows<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir, "..")
                }
            }
        }
    }

    @Nested
    inner class AssumeDirectory {

        @Nested
        inner class WithOnePathArg {
            @Test
            fun testOnNonExistingPath() {
                val path = fixturesDir.div("i_do_not_exist")
                assertThrows<AssertionError> {
                    assumeDirectory(path)
                }
            }

            @Test
            fun testOnExistingFile() {
                val path = fixturesDir.div("simple_main.p8")
                assertThrows<AssertionError> {
                    assumeDirectory(path)
                }
            }

            @Test
            fun testOnExistingDirectory() {
                val path = workingDir
                assertThat("should return the path", assumeDirectory(path), `is`(path))
            }
        }

        @Nested
        inner class WithOneStringArg {
            @Test
            fun testOnNonExistingPath() {
                val path = fixturesDir.div("i_do_not_exist")
                assertThrows<AssertionError> {
                    assumeDirectory("$path")
                }
            }

            @Test
            fun testOnExistingFile() {
                val path = fixturesDir.div("simple_main.p8")
                assertThrows<AssertionError> {
                    assumeDirectory("$path")
                }
            }

            @Test
            fun testOnExistingDirectory() {
                val path = workingDir
                assertThat("should return the path",
                    assumeDirectory("$path"), `is`(path))
            }
        }

        @Nested
        inner class WithPathAndStringArgs {
            @Test
            fun testOnNonExistingPath() {
                assertThrows<AssertionError> {
                    assumeDirectory(fixturesDir, "i_do_not_exist")
                }
            }

            @Test
            fun testOnExistingFile() {
                assertThrows<AssertionError> {
                    assumeDirectory(fixturesDir, "simple_main.p8")
                }
            }

            @Test
            fun testOnExistingDirectory() {
                val path = workingDir.div("..")
                assertThat(
                    "should return resulting path",
                    assumeDirectory(workingDir, ".."), `is`(path)
                )
            }
        }

        @Nested
        inner class WithStringAndStringArgs {
            @Test
            fun testOnNonExistingPath() {
                assertThrows<AssertionError> {
                    assumeDirectory("$fixturesDir", "i_do_not_exist")
                }
            }

            @Test
            fun testOnExistingFile() {
                assertThrows<AssertionError> {
                    assumeDirectory("$fixturesDir", "simple_main.p8")
                }
            }

            @Test
            fun testOnExistingDirectory() {
                val path = workingDir.div("..")
                assertThat(
                    "should return resulting path",
                    assumeDirectory("$workingDir", ".."), `is`(path)
                )
            }
        }

        @Nested
        inner class WithStringAndPathArgs {
            @Test
            fun testOnNonExistingPath() {
                assertThrows<AssertionError> {
                    assumeDirectory("$fixturesDir", Path("i_do_not_exist"))
                }
            }

            @Test
            fun testOnExistingFile() {
                assertThrows<AssertionError> {
                    assumeDirectory("$fixturesDir", Path("simple_main.p8"))
                }
            }

            @Test
            fun testOnExistingDirectory() {
                val path = workingDir.div("..")
                assertThat(
                    "should return resulting path",
                    assumeDirectory("$workingDir", Path("..")), `is`(path)
                )
            }
        }
    }

    @Nested
    inner class AssumeReadableFile {

        @Nested
        inner class WithOnePathArg {

            @Test
            fun testOnNonExistingPath() {
                val path = fixturesDir.div("i_do_not_exist")
                assertThrows<AssertionError> {
                    assumeReadableFile(path)
                }
            }

            @Test
            fun testOnReadableFile() {
                val path = fixturesDir.div("simple_main.p8")
                assertThat("should return the path",
                    assumeReadableFile(path), `is`(path))
            }

            @Test
            fun testOnDirectory() {
                assertThrows<AssertionError> {
                    assumeReadableFile(fixturesDir)
                }
            }
        }

        @Nested
        inner class WithOneStringArg {

            @Test
            fun testOnNonExistingPath() {
                val path = fixturesDir.div("i_do_not_exist")
                assertThrows<AssertionError> {
                    assumeReadableFile("$path")
                }
            }

            @Test
            fun testOnReadableFile() {
                val path = fixturesDir.div("simple_main.p8")
                assertThat("should return the resulting path",
                    assumeReadableFile("$path"), `is`(path))
            }

            @Test
            fun testOnDirectory() {
                assertThrows<AssertionError> {
                    assumeReadableFile("$fixturesDir")
                }
            }
        }

        @Nested
        inner class WithPathAndStringArgs {
            @Test
            fun testOnNonexistingPath() {
                assertThrows<java.lang.AssertionError> {
                    assumeReadableFile(fixturesDir, "i_do_not_exist")
                }
            }

            @Test
            fun testOnReadableFile() {
                val path = fixturesDir.div("simple_main.p8")
                assertThat("should return the resulting path",
                    assumeReadableFile(fixturesDir, "simple_main.p8"), `is`(path))
            }

            @Test
            fun testOnDirectory() {
                assertThrows<AssertionError> {
                    assumeReadableFile(fixturesDir, "..")
                }
            }
        }

        @Nested
        inner class WithPathAndPathArgs {
            @Test
            fun testOnNonexistingPath() {
                assertThrows<java.lang.AssertionError> {
                    assumeReadableFile(fixturesDir, Path("i_do_not_exist"))
                }
            }

            @Test fun testOnReadableFile() {
                assertThat("should return the resulting path",
                    assumeReadableFile(fixturesDir, Path("simple_main.p8")),
                        `is`(fixturesDir.div("simple_main.p8"))
                )
            }

            @Test
            fun testOnDirectory() {
                assertThrows<AssertionError> {
                    assumeReadableFile(fixturesDir, Path(".."))
                }
            }
        }

        @Nested
        inner class WithStringAndStringArgs {
            @Test
            fun testOnNonexistingPath() {
                assertThrows<java.lang.AssertionError> {
                    assumeReadableFile("$fixturesDir", "i_do_not_exist")
                }
            }

            @Test
            fun testOnReadableFile() {
                assertThat("should return the resulting path",
                    assumeReadableFile(fixturesDir.toString(), "simple_main.p8"),
                    `is`(fixturesDir.div("simple_main.p8"))
                )
            }

            @Test
            fun testOnDirectory() {
                assertThrows<AssertionError> {
                    assumeReadableFile("$fixturesDir", "..")
                }
            }
        }
    }
}
