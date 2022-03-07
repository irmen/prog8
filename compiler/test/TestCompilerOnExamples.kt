package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import prog8.codegen.target.C64Target
import prog8.codegen.target.Cx16Target
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.compileProgram
import prog8.compilerinterface.ICompilationTarget
import prog8tests.helpers.assumeDirectory
import prog8tests.helpers.cartesianProduct
import prog8tests.helpers.outputDir
import prog8tests.helpers.workingDir
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists


/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */

private val examplesDir = assumeDirectory(workingDir, "../examples")

private fun compileTheThing(filepath: Path, optimize: Boolean, target: ICompilationTarget): CompilationResult? {
    val args = CompilerArguments(
        filepath,
        optimize,
        optimizeFloatExpressions = true,
        dontReinitGlobals = false,
        writeAssembly = true,
        slowCodegenWarnings = false,
        quietAssembler = true,
        asmListfile = false,
        experimentalCodegen = false,
        compilationTarget = target.name,
        outputDir = outputDir
    )
    return compileProgram(args)
}

private fun prepareTestFiles(source: String, optimize: Boolean, target: ICompilationTarget): Pair<String, Path> {
    val searchIn = mutableListOf(examplesDir)
    if (target is Cx16Target) {
        searchIn.add(0, assumeDirectory(examplesDir, "cx16"))
    }
    val filepath = searchIn.asSequence()
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
        val target = C64Target()
        val (displayName, filepath) = prepareTestFiles(source, optimize, target)
        test(displayName) {
            compileTheThing(filepath, optimize, target) shouldNotBe null
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
            "rasterbars",
            "sincos",
            "tehtriz",
            "testgfx2",
        ),
        listOf(false, true)
    )

    onlyCx16.forEach {
        val (source, optimize) = it
        val target = Cx16Target()
        val (displayName, filepath) = prepareTestFiles(source, optimize, target)
        test(displayName) {
            compileTheThing(filepath, optimize, target) shouldNotBe null
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
        val c64target = C64Target()
        val cx16target = Cx16Target()
        val (displayNameC64, filepathC64) = prepareTestFiles(source, optimize, c64target)
        val (displayNameCx16, filepathCx16) = prepareTestFiles(source, optimize, cx16target)
        test(displayNameC64) {
            compileTheThing(filepathC64, optimize, c64target) shouldNotBe null
        }
        test(displayNameCx16) {
            compileTheThing(filepathCx16, optimize, cx16target) shouldNotBe null
        }
    }
})
