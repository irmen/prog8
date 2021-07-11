package prog8tests

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import kotlin.test.*
import prog8tests.helpers.*

import prog8.compiler.compileProgram
import prog8.compiler.target.C64Target
import prog8.compiler.target.Cx16Target
import prog8.compiler.target.ICompilationTarget

/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */
//@Disabled("to save some time")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCompilerOnExamples {
    private val examplesDir = workingDir.resolve("../examples")

    @BeforeAll
    fun setUp() {
        sanityCheckDirectories("compiler")
        assumeDirectory(examplesDir)
    }

    // TODO: make assembly stage testable - in case of failure (eg of 64tass) it Process.exit s

    private fun testExample(nameWithoutExt: String, platform: ICompilationTarget, optimize: Boolean) {
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
        assertTrue(result.success,
            "compilation should succeed; ${platform.name}, optimize=$optimize: \"$filepath\"")
    }


    @Test
    fun test_cxLogo_cx16_noopt() {
        testExample("cxlogo", Cx16Target, false)
    }
    @Test
    fun test_cxLogo_cx16_opt() {
        testExample("cxlogo", Cx16Target, true)
    }
    @Test
    fun test_cxLogo_c64_noopt() {
        testExample("cxlogo", C64Target, false)
    }
    @Test
    fun test_cxLogo_c64_opt() {
        testExample("cxlogo", C64Target, true)
    }

    @Test
    fun test_swirl_cx16_noopt() {
        testExample("swirl", Cx16Target, false)
    }
    @Test
    fun test_swirl_cx16_opt() {
        testExample("swirl", Cx16Target, true)
    }
    @Test
    fun test_swirl_c64_noopt() {
        testExample("swirl", C64Target, false)
    }
    @Test
    fun test_swirl_c64_opt() {
        testExample("swirl", C64Target, true)
    }

    @Test
    fun test_animals_cx16_noopt() {
        testExample("animals", Cx16Target, false)
    }
    @Test
    fun test_animals_cx16_opt() {
        testExample("animals", Cx16Target, true)
    }
    @Test
    fun test_animals_c64_noopt() {
        testExample("animals", C64Target, false)
    }
    @Test
    fun test_animals_c64_opt() {
        testExample("animals", C64Target, true)
    }

    @Test
    fun test_tehtriz_cx16_noopt() {
        testExample("cx16/tehtriz", Cx16Target, false)
    }
    @Test
    fun test_tehtriz_cx16_opt() {
        testExample("cx16/tehtriz", Cx16Target, true)
    }
    @Test
    fun test_tehtriz_c64_noopt() {
        testExample("tehtriz", C64Target, false)
    }
    @Test
    fun test_tehtriz_c64_opt() {
        testExample("tehtriz", C64Target, true)
    }

    // textelite.p8 is the largest example (~36KB)
    @Test
    fun test_textelite_cx16_noopt() {
        testExample("textelite", Cx16Target, false)
    }
    @Test
    fun test_textelite_cx16_opt() {
        testExample("textelite", Cx16Target, true)
    }
    @Test
    fun test_textelite_c64_noopt() {
        testExample("textelite", C64Target, false)
    }
    @Test
    fun test_textelite_c64_opt() {
        testExample("textelite", C64Target, true)
    }
}
