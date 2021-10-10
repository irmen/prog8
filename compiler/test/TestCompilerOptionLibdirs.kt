package prog8tests

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.AfterAll
import prog8tests.helpers.*
import kotlin.io.path.*
import java.nio.file.Path

import prog8.compiler.compileProgram
import prog8.compiler.target.*

/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCompilerOptionLibdirs {

    private lateinit var tempFileInWorkingDir: Path

    @BeforeAll
    fun setUp() {
        tempFileInWorkingDir = createTempFile(directory = workingDir, prefix = "tmp_", suffix = ".p8")
            .also { it.writeText("""
                main {
                    sub start() {
                    }
                }
            """)}
    }

    @AfterAll
    fun tearDown() {
        tempFileInWorkingDir.deleteExisting()
    }

    private fun compileFile(filePath: Path, libdirs: List<String>) =
        compileProgram(
            filepath = filePath,
            optimize = false,
            writeAssembly = true,
            slowCodegenWarnings = false,
            compilationTarget = Cx16Target.name,
            libdirs,
            outputDir
        )

    @Test
    fun testAbsoluteFilePathInWorkingDir() {
        val filepath = assumeReadableFile(tempFileInWorkingDir.absolute())
        compileFile(filepath, listOf())
            .assertSuccess()
    }

    @Test
    fun testFilePathInWorkingDirRelativeToWorkingDir() {
        val filepath = assumeReadableFile(workingDir.relativize(tempFileInWorkingDir.absolute()))
        compileFile(filepath, listOf())
            .assertSuccess()
    }

    @Test
    fun testFilePathInWorkingDirRelativeTo1stInLibdirs() {
        val filepath = assumeReadableFile(tempFileInWorkingDir)
        compileFile(filepath.fileName, listOf(workingDir.toString()))
            .assertSuccess()
    }

    @Test
    fun testAbsoluteFilePathOutsideWorkingDir() {
        val filepath = assumeReadableFile(fixturesDir, "simple_main.p8")
        compileFile(filepath.absolute(), listOf())
            .assertSuccess()
    }

    @Test
    fun testFilePathOutsideWorkingDirRelativeToWorkingDir() {
        val filepath = workingDir.relativize(assumeReadableFile(fixturesDir, "simple_main.p8").absolute())
        compileFile(filepath, listOf())
            .assertSuccess()
    }

    @Test
    fun testFilePathOutsideWorkingDirRelativeTo1stInLibdirs() {
        val filepath = assumeReadableFile(fixturesDir, "simple_main.p8")
        val libdirs = listOf("$fixturesDir")
        compileFile(filepath.fileName, libdirs)
            .assertSuccess()
    }

}
