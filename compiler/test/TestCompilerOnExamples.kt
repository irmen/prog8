package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
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
        writeAssembly = true,
        warnSymbolShadowing = false,
        quietAssembler = true,
        asmListfile = false,
        includeSourcelines = false,
        experimentalCodegen = false,
        dumpVariables = false,
        dumpSymbols = false,
        varsHighBank = null,
        varsGolden = false,
        slabsHighBank = null,
        slabsGolden = false,
        compilationTarget = target.name,
        splitWordArrays = false,
        breakpointCpuInstruction = null,
        printAst1 = false,
        printAst2 = false,
        ignoreFootguns = false,
        symbolDefs = emptyMap(),
        outputDir = outputDir
    )
    return compileProgram(args)
}

private fun prepareTestFiles(source: String, optimize: Boolean, target: ICompilationTarget): Pair<String, Path> {
    val searchIn = mutableListOf(examplesDir)
    when (target) {
        is C64Target -> searchIn.add(0, assumeDirectory(examplesDir, "c64"))
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
            "banking",
            "bdmusic",
            "bdmusic-irq",
            "charset",
            "cube3d",
            "cube3d-sprites",
            "plasma",
            "rasterbars",
            "sprites",
            "starfield",
            "tehtriz",
            "turtle-gfx",
            "wizzine",
        ),
        listOf(false, true)
    )

    val target = C64Target()
    withData(
        nameFn = { it.second.first },
        onlyC64.map { it to prepareTestFiles(it.first, it.second, target) }
    ) { (params, prep) ->
        val filepath = prep.second
        val optimize = params.second
        compileTheThing(filepath, optimize, target) shouldNotBe null
    }
})

class TestCompilerOnExamplesCx16: FunSpec({

    val onlyCx16 = cartesianProduct(
        listOf(
            "chunkedfile/demo",
            "vtui/testvtui",
            "pcmaudio/play-adpcm",
            "pcmaudio/stream-wav",
            "pcmaudio/stream-simple-aflow",
            "pcmaudio/stream-simple-poll",
            "pcmaudio/vumeter",
            "sprites/dragon",
            "sprites/dragons",
            "zsmkit_v1/demo1",
            "zsmkit_v1/demo2",
            "zsmkit_v2/demo",
            "banking/program",
            "amiga",
            "audioroutines",
            "automatons",
            "balloonflight",
            "bdmusic",
            "bobs",
            "bubbleuniverse",
            "charsets",
            "circles",
            "cobramk3-gfx",
            "colorbars",
            "cube3d",
            "cxlogo",
            "diskspeed",
            "fileseek",
            "interpolation",
            "kefrenbars",
            "keyboardhandler",
            "life",
            "mandelbrot",
            "multi-irq-old",
            "multi-irq-new",
            "plasma",
            "rasterbars",
            "showbmx",
            "snow",
            "spotlight",
            "starszoom",
            "tehtriz",
            "test_gfx_lores",
            "test_gfx_hires",
            "testmonogfx",
        ),
        listOf(false, true)
    )

    val target = Cx16Target()
    withData(
        nameFn = { it.second.first },
        onlyCx16.map { it to prepareTestFiles(it.first, it.second, target) }
    ) { (params, prep) ->
        val filepath = prep.second
        val optimize = params.second
        compileTheThing(filepath, optimize, target) shouldNotBe null
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
            "dirlist",
            "fibonacci",
            "fractal-tree",
            "maze",
            "mandelbrot",
            "mandelbrot-gfx",
            "numbergame",
            "primes",
            "queens",
            "screencodes",
            "swirl",
            "swirl-float",
            "tehtriz",
            "textelite",
        ),
        listOf(false, true),
        listOf(C64Target(), Cx16Target())
    )

    withData(
        nameFn = { it.third.first },
        bothCx16AndC64.map { Triple(it.second, it.third, prepareTestFiles(it.first, it.second, it.third)) }
    ) { params ->
        val filepath = params.third.second
        val optimize = params.first
        compileTheThing(filepath, optimize, params.second) shouldNotBe null
    }
})

class TestCompilerOnExamplesVirtual: FunSpec({

    val onlyVirtual = cartesianProduct(
        listOf(
            "bouncegfx",
            "bsieve",
            "pixelshader",
            "sincos"
        ),
        listOf(false, true)
    )

    val target = VMTarget()
    withData(
        nameFn = { it.second.first },
        onlyVirtual.map { it to prepareTestFiles(it.first, it.second, target) }
    ) { (params, prep) ->
        val filepath = prep.second
        val optimize = params.second
        compileTheThing(filepath, optimize, target) shouldNotBe null
    }
})
