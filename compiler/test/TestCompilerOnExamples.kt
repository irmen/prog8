package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import prog8.code.core.ICompilationTarget
import prog8.code.target.*
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.compileProgram
import prog8tests.helpers.*
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.readText


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
        writeAssembly = true,
        slowCodegenWarnings = false,
        quietAssembler = true,
        asmListfile = false,
        experimentalCodegen = false,
        varsHigh = false,
        useNewExprCode = false,
        compilationTarget = target.name,
        evalStackBaseAddress = null,
        splitWordArrays = false,
        symbolDefs = emptyMap(),
        outputDir = outputDir
    )
    return compileProgram(args)
}

private fun prepareTestFiles(source: String, optimize: Boolean, target: ICompilationTarget): Pair<String, Path> {
    val searchIn = mutableListOf(examplesDir)
    when (target) {
        is Cx16Target -> searchIn.add(0, assumeDirectory(examplesDir, "cx16"))
        is VMTarget -> searchIn.add(0, assumeDirectory(examplesDir, "vm"))
        is C128Target -> searchIn.add(0, assumeDirectory(examplesDir, "c128"))
        is AtariTarget -> searchIn.add(0, assumeDirectory(examplesDir, "atari"))
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
            "starfield",
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
            "bdmusic",
            "bobs",
            "circles",
            "cobramk3-gfx",
            "colorbars",
            "cube3d",
            "datetime",
            "diskspeed",
            "fileseek",
            "highresbitmap",
            "kefrenbars",
            "keyboardhandler",
            "mandelbrot",
            "mandelbrot-gfx-colors",
            "multipalette",
            "rasterbars",
            "sincos",
            "snow",
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
            "bsieve",
            "cube3d",
            "cube3d-float",
            "cube3d-gfx",
            "cxlogo",
            "dirlist",
            "fibonacci",
            "line-circle-gfx",
            "line-circle-txt",
            "maze",
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

class TestCompilerOnExamplesVirtual: FunSpec({

    val onlyVirtual = listOf(
            "bouncegfx",
            "bsieve",
            "pixelshader",
            "sincos",
            "textelite"
        )

    onlyVirtual.forEach {
        val target = VMTarget()
        val (displayName, filepath) = prepareTestFiles(it, false, target)
        test(displayName) {
            val src = filepath.readText()
            compileText(target, true, src, writeAssembly = true) shouldNotBe null
            compileText(target, true, src, writeAssembly = true) shouldNotBe null
        }
    }
})
