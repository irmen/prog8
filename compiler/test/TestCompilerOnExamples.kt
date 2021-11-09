package prog8tests

import io.kotest.core.spec.style.FunSpec
import prog8.compiler.compileProgram
import prog8.compiler.target.C64Target
import prog8.compiler.target.Cx16Target
import prog8.compilerinterface.ICompilationTarget
import prog8tests.ast.helpers.*
import prog8tests.helpers.assertSuccess
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists


/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */

private val examplesDir = assumeDirectory(workingDir, "../examples")

private fun compileTheThing(filepath: Path, optimize: Boolean, target: ICompilationTarget) = compileProgram(
    filepath,
    optimize,
    optimizeFloatExpressions = true,
    writeAssembly = true,
    slowCodegenWarnings = false,
    quietAssembler = true,
    compilationTarget = target.name,
    sourceDirs = listOf(),
    outputDir
)

private fun prepareTestFiles(source: String, optimize: Boolean, target: ICompilationTarget): Pair<String, Path> {
    val searchIn = mutableListOf(examplesDir)
    if (target == Cx16Target) {
        searchIn.add(0, assumeDirectory(examplesDir, "cx16"))
    }
    val filepath = searchIn
        .map { it.resolve("$source.p8") }
        .map { it.normalize().absolute() }
        .map { workingDir.relativize(it) }
        .first { it.exists() }
    val displayName = "${examplesDir.relativize(filepath.absolute())}: ${target.name}, optimize=$optimize"
    return Pair(displayName, filepath)
}


class TestCompilerOnExamplesC64: FunSpec({

    val onlyC64 = cartesianProduct(
        listOf(
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
        listOf(false, true)
    )

    onlyC64.forEach {
        val (source, optimize) = it
        val (displayName, filepath) = prepareTestFiles(source, optimize, C64Target)
        test(displayName) {
            compileTheThing(filepath, optimize, C64Target).assertSuccess()
        }
    }
})

class TestCompilerOnExamplesCx16: FunSpec({

    val onlyCx16 = cartesianProduct(
        listOf(
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
        listOf(false, true)
    )

    onlyCx16.forEach {
        val (source, optimize) = it
        val (displayName, filepath) = prepareTestFiles(source, optimize, Cx16Target)
        test(displayName) {
            compileTheThing(filepath, optimize, Cx16Target).assertSuccess()
        }
    }
})

class TestCompilerOnExamplesBothC64andCx16: FunSpec({

    val bothCx16AndC64 = cartesianProduct(
        listOf(
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
            "textelite",
        ),
        listOf(false, true)
    )

    bothCx16AndC64.forEach {
        val (source, optimize) = it
        val (displayNameC64, filepathC64) = prepareTestFiles(source, optimize, C64Target)
        val (displayNameCx16, filepathCx16) = prepareTestFiles(source, optimize, Cx16Target)
        test(displayNameC64) {
            compileTheThing(filepathC64, optimize, C64Target).assertSuccess()
        }
        test(displayNameCx16) {
            compileTheThing(filepathCx16, optimize, Cx16Target).assertSuccess()
        }
    }
})
