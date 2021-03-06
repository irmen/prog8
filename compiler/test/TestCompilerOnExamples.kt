package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.*
import kotlin.io.path.*

import prog8.compiler.target.Cx16Target
import prog8.compiler.compileProgram
import prog8.compiler.target.ICompilationTarget


/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 * What's more: in case of failure (to compile and assemble) - which is when tests should help you -
 * these tests will actually be ignored (ie. NOT fail),
 * because in the code there are calls to Process.exit, making it essentially untestable.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCompilerOnExamples {
    val workingDir = Path("").absolute()    // Note: Path(".") does NOT work..!
    val examplesDir = workingDir.resolve("../examples")
    val outputDir = workingDir.resolve("build/tmp/test")

    @Test
    fun testDirectoriesSanityCheck() {
        assertEquals("compiler", workingDir.fileName.toString())
        assertTrue(examplesDir.isDirectory(), "sanity check; should be directory: $examplesDir")
        assertTrue(outputDir.isDirectory(), "sanity check; should be directory: $outputDir")
    }

    // TODO: make assembly stage testable - in case of failure (eg of 64tass) it Process.exit s

    fun testExample(nameWithoutExt: String, platform: ICompilationTarget, optimize: Boolean) {
        val filepath = examplesDir.resolve("$nameWithoutExt.p8")
        val result = compileProgram(
            filepath,
            optimize,
            writeAssembly = true,
            slowCodegenWarnings = false,
            compilationTarget = platform.name,
            libdirs = listOf(),
            outputDir
        )
        assertTrue(result.success, "${platform.name}, optimize=$optimize: \"$filepath\"")
    }


    @Test
    fun test_cxLogo_noopt() {
        testExample("cxlogo", Cx16Target, false)
    }
    @Test
    fun test_cxLogo_opt() {
        testExample("cxlogo", Cx16Target, true)
    }

    @Test
    fun test_swirl_noopt() {
        testExample("swirl", Cx16Target, false)
    }
    @Test
    fun test_swirl_opt() {
        testExample("swirl", Cx16Target, true)
    }

    @Test
    fun test_animals_noopt() {
        testExample("animals", Cx16Target, false)
    }
    @Test
    fun test_animals_opt() {
        testExample("animals", Cx16Target, true)
    }

}