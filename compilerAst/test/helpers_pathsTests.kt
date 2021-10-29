package prog8tests.ast

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8tests.ast.helpers.*
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.assertFailsWith


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
            fun `on non-existing path`() {
                val path = fixturesDir.div("i_do_not_exist")
                assertThat("should return the path",
                    assumeNotExists(path), `is`(path))
            }

            @Test
            fun `on existing file`() {
                assertFailsWith<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir.div("simple_main.p8"))
                }
            }

            @Test
            fun `on existing directory`() {
                assertFailsWith<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir)
                }
            }
        }

        @Nested
        inner class WithOneStringArg {

            @Test
            fun `on non-existing path`() {
                val path = fixturesDir.div("i_do_not_exist")
                assertThat("should return the path",
                    assumeNotExists("$path"), `is`(path))
            }

            @Test
            fun `on existing file`() {
                val path = fixturesDir.div("simple_main.p8")
                assertFailsWith<java.lang.AssertionError> {
                    assumeNotExists("$path")
                }
            }

            @Test
            fun `on existing directory`() {
                assertFailsWith<java.lang.AssertionError> {
                    assumeNotExists("$fixturesDir")
                }
            }
        }

        @Nested
        inner class WithPathAndStringArgs {

            @Test
            fun `on non-existing path`() {
                val path = fixturesDir.div("i_do_not_exist")
                assertThat("should return the path",
                    assumeNotExists(fixturesDir, "i_do_not_exist"), `is`(path))
            }

            @Test
            fun `on existing file`() {
                assertFailsWith<java.lang.AssertionError> {
                    assumeNotExists(fixturesDir, "simple_main.p8")
                }
            }

            @Test
            fun `on existing directory`() {
                assertFailsWith<java.lang.AssertionError> {
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
            fun `on non-existing path`() {
                val path = fixturesDir.div("i_do_not_exist")
                assertFailsWith<AssertionError> {
                    assumeDirectory(path)
                }
            }

            @Test
            fun `on existing file`() {
                val path = fixturesDir.div("simple_main.p8")
                assertFailsWith<AssertionError> {
                    assumeDirectory(path)
                }
            }

            @Test
            fun `on existing directory`() {
                val path = workingDir
                assertThat("should return the path", assumeDirectory(path), `is`(path))
            }
        }

        @Nested
        inner class WithOneStringArg {
            @Test
            fun `on non-existing path`() {
                val path = fixturesDir.div("i_do_not_exist")
                assertFailsWith<AssertionError> {
                    assumeDirectory("$path")
                }
            }

            @Test
            fun `on existing file`() {
                val path = fixturesDir.div("simple_main.p8")
                assertFailsWith<AssertionError> {
                    assumeDirectory("$path")
                }
            }

            @Test
            fun `on existing directory`() {
                val path = workingDir
                assertThat("should return the path",
                    assumeDirectory("$path"), `is`(path))
            }
        }

        @Nested
        inner class WithPathAndStringArgs {
            @Test
            fun `on non-existing path`() {
                assertFailsWith<AssertionError> {
                    assumeDirectory(fixturesDir, "i_do_not_exist")
                }
            }

            @Test
            fun `on existing file`() {
                assertFailsWith<AssertionError> {
                    assumeDirectory(fixturesDir, "simple_main.p8")
                }
            }

            @Test
            fun `on existing directory`() {
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
            fun `on non-existing path`() {
                assertFailsWith<AssertionError> {
                    assumeDirectory("$fixturesDir", "i_do_not_exist")
                }
            }

            @Test
            fun `on existing file`() {
                assertFailsWith<AssertionError> {
                    assumeDirectory("$fixturesDir", "simple_main.p8")
                }
            }

            @Test
            fun `on existing directory`() {
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
            fun `on non-existing path`() {
                assertFailsWith<AssertionError> {
                    assumeDirectory("$fixturesDir", Path("i_do_not_exist"))
                }
            }

            @Test
            fun `on existing file`() {
                assertFailsWith<AssertionError> {
                    assumeDirectory("$fixturesDir", Path("simple_main.p8"))
                }
            }

            @Test
            fun `on existing directory`() {
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
            fun `on non-existing path`() {
                val path = fixturesDir.div("i_do_not_exist")
                assertFailsWith<AssertionError> {
                    assumeReadableFile(path)
                }
            }

            @Test
            fun `on readable file`() {
                val path = fixturesDir.div("simple_main.p8")
                assertThat("should return the path",
                    assumeReadableFile(path), `is`(path))
            }

            @Test
            fun `on directory`() {
                assertFailsWith<AssertionError> {
                    assumeReadableFile(fixturesDir)
                }
            }
        }

        @Nested
        inner class WithOneStringArg {

            @Test
            fun `on non-existing path`() {
                val path = fixturesDir.div("i_do_not_exist")
                assertFailsWith<AssertionError> {
                    assumeReadableFile("$path")
                }
            }

            @Test
            fun `on readable file`() {
                val path = fixturesDir.div("simple_main.p8")
                assertThat("should return the resulting path",
                    assumeReadableFile("$path"), `is`(path))
            }

            @Test
            fun `on directory`() {
                assertFailsWith<AssertionError> {
                    assumeReadableFile("$fixturesDir")
                }
            }
        }

        @Nested
        inner class WithPathAndStringArgs {
            @Test
            fun `on non-existing path`() {
                assertFailsWith<java.lang.AssertionError> {
                    assumeReadableFile(fixturesDir, "i_do_not_exist")
                }
            }

            @Test
            fun `on readable file`() {
                val path = fixturesDir.div("simple_main.p8")
                assertThat("should return the resulting path",
                    assumeReadableFile(fixturesDir, "simple_main.p8"), `is`(path))
            }

            @Test
            fun `on directory`() {
                assertFailsWith<AssertionError> {
                    assumeReadableFile(fixturesDir, "..")
                }
            }
        }

        @Nested
        inner class WithPathAndPathArgs {
            @Test
            fun `on non-existing path`() {
                assertFailsWith<java.lang.AssertionError> {
                    assumeReadableFile(fixturesDir, Path("i_do_not_exist"))
                }
            }

            @Test fun `on readable file`() {
                assertThat("should return the resulting path",
                    assumeReadableFile(fixturesDir, Path("simple_main.p8")),
                        `is`(fixturesDir.div("simple_main.p8"))
                )
            }

            @Test
            fun `on directory`() {
                assertFailsWith<AssertionError> {
                    assumeReadableFile(fixturesDir, Path(".."))
                }
            }
        }

        @Nested
        inner class WithStringAndStringArgs {
            @Test
            fun `on non-existing path`() {
                assertFailsWith<java.lang.AssertionError> {
                    assumeReadableFile("$fixturesDir", "i_do_not_exist")
                }
            }

            @Test
            fun `on readable file`() {
                assertThat("should return the resulting path",
                    assumeReadableFile(fixturesDir.toString(), "simple_main.p8"),
                    `is`(fixturesDir.div("simple_main.p8"))
                )
            }

            @Test
            fun `on directory`() {
                assertFailsWith<AssertionError> {
                    assumeReadableFile("$fixturesDir", "..")
                }
            }
        }
    }
}
