package prog8tests

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import prog8tests.helpers.*
import kotlin.io.path.*

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
    private val examplesDir = assumeDirectory(workingDir, "../examples")

    // TODO: make assembly stage testable - in case of failure (eg of 64tass) it Process.exit s

    private fun makeDynamicCompilerTest(name: String, platform: ICompilationTarget, optimize: Boolean) : DynamicTest {
        val searchIn = mutableListOf(examplesDir)
        if (platform == Cx16Target) {
            searchIn.add(0, assumeDirectory(examplesDir, "cx16"))
        }
        val filepath = searchIn
            .map { it.resolve("$name.p8") }
            .map { it.normalize().absolute() }
            .map { workingDir.relativize(it) }
            .first { it.exists() }
        val displayName = "${examplesDir.relativize(filepath.absolute())}: ${platform.name}, optimize=$optimize"
        return dynamicTest(displayName) {
            compileProgram(
                filepath,
                optimize,
                writeAssembly = true,
                slowCodegenWarnings = false,
                compilationTarget = platform.name,
                libdirs = listOf(),
                outputDir
            ).assertSuccess("; $displayName")
        }
    }

    @TestFactory
//    @Disabled
    fun bothCx16AndC64() = mapCombinations(
        dim1 = listOf(
            "animals",
            "balls",
            "cube3d",
            "cube3d-float",
            "cube3d-gfx",
            "cxlogo",
            "dirlist",
            "fibonacci",
            "line-circle-gfx",
            "line-circle-txt",
            "mandelbrot",
            "mandelbrot-gfx",
            "numbergame",
            "primes",
            "rasterbars",
            "screencodes",
            "sorting",
            "swirl",
            "swirl-float",
            "tehtriz",
            "test",
            "textelite",
        ),
        dim2 = listOf(Cx16Target, C64Target),
        dim3 = listOf(false, true),
        combine3 = ::makeDynamicCompilerTest
    )

    @TestFactory
//    @Disabled
    fun onlyC64() = mapCombinations(
        dim1 = listOf(
            "balloonflight",
            "bdmusic",
            "bdmusic-irq",
            "charset",
            "cube3d-sprites",
            "plasma",
            "sprites",
            "turtle-gfx",
            "wizzine",
        ),
        dim2 = listOf(C64Target),
        dim3 = listOf(false, true),
        combine3 = ::makeDynamicCompilerTest
    )

    @TestFactory
//    @Disabled
    fun onlyCx16() = mapCombinations(
        dim1 = listOf(
            "vtui/testvtui",
            "amiga",
            "bobs",
            "cobramk3-gfx",
            "colorbars",
            "datetime",
            "highresbitmap",
            "kefrenbars",
            "mandelbrot-gfx-colors",
            "multipalette",
            "testgfx2",
        ),
        dim2 = listOf(Cx16Target),
        dim3 = listOf(false, true),
        combine3 = ::makeDynamicCompilerTest
    )
}
