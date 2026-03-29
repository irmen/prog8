package prog8tests.compiler

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.core.ICompilationTarget
import prog8.code.target.*
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.compileProgram
import prog8tests.helpers.assumeDirectory
import prog8tests.helpers.cartesianProduct
import prog8tests.helpers.workingDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists


/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */

/**
 * Data class to hold example name and expected output file sizes per target and optimization setting.
 * A size value of 0 means skip the size check for that configuration.
 */
private data class ExampleSizes(
    val name: String,
    val c64SizeOptimized: Int = 0,
    val c64SizeUnoptimized: Int = 0,
    val cx16SizeOptimized: Int = 0,
    val cx16SizeUnoptimized: Int = 0,
    val pet32SizeOptimized: Int = 0,
    val pet32SizeUnoptimized: Int = 0,
    val virtualInstrCountOptimized: Int = 0,
    val virtualInstrCountUnoptimized: Int = 0,
    val virtualRegCountOptimized: Int = 0,
    val virtualRegCountUnoptimized: Int = 0
)

private val examplesDir = assumeDirectory(workingDir, "../examples")

private fun compileTheThing(filepath: Path, optimize: Boolean, target: ICompilationTarget, outputDir: Path): CompilationResult? {
    val args = CompilerArguments(
        filepath,
        optimize,
        writeAssembly = true,
        warnSymbolShadowing = false,
        warnImplicitTypeCasts= false,
        quietAll = true,
        quietAssembler = true,
        showTimings = false,
        asmListfile = false,
        includeSourcelines = true,
        experimentalCodegen = false,
        dumpVariables = false,
        dumpSymbols = false,
        varsHighBank = null,
        varsGolden = false,
        slabsHighBank = null,
        slabsGolden = false,
        compilationTarget = target.name,
        breakpointCpuInstruction = null,
        printAst1 = false,
        printAst2 = false,
        ignoreFootguns = false,
        profilingInstrumentation = false,
        nostdlib = false,
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
        is VMTarget -> searchIn.add(0, assumeDirectory(examplesDir, "virtual"))
        is C128Target -> searchIn.add(0, assumeDirectory(examplesDir, "c128"))
        is PETTarget -> searchIn.add(0, assumeDirectory(examplesDir, "pet"))
    }
    val filepath = searchIn.asSequence()
        .map { it.resolve("$source.p8") }
        .map { it.normalize() }
        .map { workingDir.relativize(it) }
        .first { it.exists() }
    val displayName = "${examplesDir.relativize(filepath)}: ${target.name}, optimize=$optimize"
    return Pair(displayName, filepath)
}

/**
 * Verifies the size of the compiled output file (.prg or .p8ir) against an expected value.
 * This helps detect any size changes in the compiler output - both increases (code bloat)
 * and decreases (possible broken optimization or missing code).
 * A small tolerance of ±5 bytes is allowed for minor non-deterministic variations.
 */
private fun verifyOutputFileSize(result: CompilationResult, expectedSize: Int) {
    val outputFile = when (result.compilationOptions.compTarget) {
        is VMTarget -> result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        else -> result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".prg")
    }
    val actualSize = Files.size(outputFile).toInt()
    val tolerance = 5  // Allow ±5 bytes for minor non-deterministic variations
    if (actualSize < expectedSize - tolerance || actualSize > expectedSize + tolerance) {
        actualSize shouldBe expectedSize
    }
}

/**
 * Verifies the IR instruction count of the compiled .p8ir file against an expected value.
 * This is a stable metric that doesn't change based on source position comments.
 * A small tolerance of ±5 instructions is allowed for minor non-deterministic variations.
 */
private fun verifyIrInstructionCount(result: CompilationResult, expectedCount: Int) {
    val actualCount = result.irInstructionCount
    val tolerance = 5  // Allow ±5 instructions for minor non-deterministic variations
    if (actualCount < expectedCount - tolerance || actualCount > expectedCount + tolerance) {
        actualCount shouldBe expectedCount
    }
}

private fun verifyIrRegisterCount(result: CompilationResult, expectedCount: Int) {
    val actualCount = result.irRegisterCount
    val tolerance = 5  // Allow ±5 registers for minor non-deterministic variations
    if (actualCount < expectedCount - tolerance || actualCount > expectedCount + tolerance) {
        actualCount shouldBe expectedCount
    }
}


class TestCompilerOnExamplesC64: FunSpec({

    @OptIn(ExperimentalKotest::class)
    //testExecutionMode = TestExecutionMode.LimitedConcurrency(6)
    this.concurrency = 4

    val outputDir = tempdir().toPath()

    val onlyC64 = cartesianProduct(
        listOf(
            ExampleSizes("balloonflight", c64SizeOptimized=1674, c64SizeUnoptimized=1745),
            ExampleSizes("banking", c64SizeOptimized=680, c64SizeUnoptimized=1577),
            ExampleSizes("bdmusic-irq", c64SizeOptimized=1001, c64SizeUnoptimized=1019),
            ExampleSizes("bdmusic", c64SizeOptimized=944, c64SizeUnoptimized=1011),
            ExampleSizes("boingball", c64SizeOptimized=1484, c64SizeUnoptimized=1544),
            ExampleSizes("charset", c64SizeOptimized=841, c64SizeUnoptimized=860),
            ExampleSizes("cube3d", c64SizeOptimized=2444, c64SizeUnoptimized=2509),
            ExampleSizes("cube3d-sprites", c64SizeOptimized=2712, c64SizeUnoptimized=2764),
            ExampleSizes("fileselector", c64SizeOptimized=3575, c64SizeUnoptimized=5084),
            ExampleSizes("library/main", c64SizeOptimized=321, c64SizeUnoptimized=1581),
            ExampleSizes("multiplexer", c64SizeOptimized=1356, c64SizeUnoptimized=1507),
            ExampleSizes("petswirl", c64SizeOptimized=959, c64SizeUnoptimized=1029),
            ExampleSizes("plasma", c64SizeOptimized=1696, c64SizeUnoptimized=1731),
            ExampleSizes("rasterbars", c64SizeOptimized=845, c64SizeUnoptimized=892),
            ExampleSizes("simplemultiplexer", c64SizeOptimized=843, c64SizeUnoptimized=914),
            ExampleSizes("sprites", c64SizeOptimized=963, c64SizeUnoptimized=1052),
            ExampleSizes("starfield", c64SizeOptimized=743, c64SizeUnoptimized=758),
            ExampleSizes("tehtriz", c64SizeOptimized=4339, c64SizeUnoptimized=4504),
            ExampleSizes("turtle-gfx", c64SizeOptimized=2757, c64SizeUnoptimized=3036),
            ExampleSizes("wizzine", c64SizeOptimized=1124, c64SizeUnoptimized=1236),
        ),
        listOf(false, true)
    )

    val target = C64Target()
    withData(
        nameFn = { "${it.first.first.name}: ${target.name}, optimize=${it.first.second}" },
        onlyC64.map { it to prepareTestFiles(it.first.name, it.second, target) }
    ) { (params, prep) ->
        val filepath = prep.second
        val optimize = params.second
        val expectedSize = if (optimize) params.first.c64SizeOptimized else params.first.c64SizeUnoptimized
        val result = compileTheThing(filepath, optimize, target, outputDir)
        result shouldNotBe null
        if (expectedSize > 0) {
            verifyOutputFileSize(result!!, expectedSize)
        }
    }
})

class TestCompilerOnExamplesCx16: FunSpec({

    @OptIn(ExperimentalKotest::class)
    //testExecutionMode = TestExecutionMode.LimitedConcurrency(6)
    this.concurrency = 4

    val outputDir = tempdir().toPath()

    val onlyCx16 = cartesianProduct(
        listOf(
            ExampleSizes("vtui/testvtui", cx16SizeOptimized=3041, cx16SizeUnoptimized=3041),
            ExampleSizes("pcmaudio/play-adpcm", cx16SizeOptimized=36147, cx16SizeUnoptimized=36645),
            ExampleSizes("pcmaudio/stream-wav", cx16SizeOptimized=5653, cx16SizeUnoptimized=7976),
            ExampleSizes("pcmaudio/stream-simple-aflow", cx16SizeOptimized=1852, cx16SizeUnoptimized=3836),
            ExampleSizes("pcmaudio/stream-simple-poll", cx16SizeOptimized=1566, cx16SizeUnoptimized=3545),
            ExampleSizes("pcmaudio/vumeter", cx16SizeOptimized=3892, cx16SizeUnoptimized=6466),
            ExampleSizes("sprites/dragon", cx16SizeOptimized=1858, cx16SizeUnoptimized=4824),
            ExampleSizes("sprites/dragons", cx16SizeOptimized=1511, cx16SizeUnoptimized=4136),
            ExampleSizes("zsmkit_v1/demo1", cx16SizeOptimized=7547, cx16SizeUnoptimized=7984),
            ExampleSizes("zsmkit_v1/demo2", cx16SizeOptimized=708, cx16SizeUnoptimized=3012),
            // zsmkit_v1/zsmkit_high and zsmkit_v1/zsmkit_low don't compile (empty source files)
            ExampleSizes("zsmkit_v2/demo", cx16SizeOptimized=790, cx16SizeUnoptimized=3374),
            // zsmkit_v2/zsmkit doesn't compile (empty source file)
            ExampleSizes("banking/program", cx16SizeOptimized=665, cx16SizeUnoptimized=3003),
            ExampleSizes("fileselector/standalone", cx16SizeOptimized=4212, cx16SizeUnoptimized=6906),
            ExampleSizes("fileselector/main", cx16SizeOptimized=480, cx16SizeUnoptimized=2805),
            // fileselector/fselector and fileselector/namesorting don't compile (empty source files)
            ExampleSizes("pointers/fountain-cx16", cx16SizeOptimized=1179, cx16SizeUnoptimized=1520),
            ExampleSizes("amiga", cx16SizeOptimized=7653, cx16SizeUnoptimized=12607),
            ExampleSizes("audioroutines", cx16SizeOptimized=948, cx16SizeUnoptimized=1244),
            ExampleSizes("automatons", cx16SizeOptimized=1037, cx16SizeUnoptimized=1342),
            ExampleSizes("balloonflight", cx16SizeOptimized=3187, cx16SizeUnoptimized=4163),
            ExampleSizes("bdmusic", cx16SizeOptimized=2390, cx16SizeUnoptimized=2766),
            ExampleSizes("bobs", cx16SizeOptimized=2825, cx16SizeUnoptimized=3440),
            ExampleSizes("boingball", cx16SizeOptimized=1486, cx16SizeUnoptimized=1962),
            ExampleSizes("bubbleuniverse", cx16SizeOptimized=1449, cx16SizeUnoptimized=1958),
            ExampleSizes("charfade", cx16SizeOptimized=1054, cx16SizeUnoptimized=1371),
            ExampleSizes("charsets", cx16SizeOptimized=1125, cx16SizeUnoptimized=1417),
            ExampleSizes("circles", cx16SizeOptimized=2432, cx16SizeUnoptimized=5234),
            ExampleSizes("cobramk3-gfx", cx16SizeOptimized=7831, cx16SizeUnoptimized=11016),
            ExampleSizes("colorbars", cx16SizeOptimized=1606, cx16SizeUnoptimized=1904),
            ExampleSizes("cube3d", cx16SizeOptimized=2381, cx16SizeUnoptimized=2712),
            ExampleSizes("cxlogo", cx16SizeOptimized=599, cx16SizeUnoptimized=895),
            ExampleSizes("diskspeed", cx16SizeOptimized=6302, cx16SizeUnoptimized=8376),
            ExampleSizes("fileseek", cx16SizeOptimized=2472, cx16SizeUnoptimized=4579),
            ExampleSizes("floatparse", cx16SizeOptimized=8303, cx16SizeUnoptimized=9270),
            ExampleSizes("interpolation", cx16SizeOptimized=3799, cx16SizeUnoptimized=4768),
            ExampleSizes("kefrenbars", cx16SizeOptimized=2415, cx16SizeUnoptimized=5435),
            ExampleSizes("keyboardhandler", cx16SizeOptimized=412, cx16SizeUnoptimized=717),
            ExampleSizes("landscape", cx16SizeOptimized=1687, cx16SizeUnoptimized=2017),
            ExampleSizes("life", cx16SizeOptimized=1332, cx16SizeUnoptimized=1759),
            ExampleSizes("mandelbrot", cx16SizeOptimized=1120, cx16SizeUnoptimized=1434),
            ExampleSizes("multi-irq-new", cx16SizeOptimized=1073, cx16SizeUnoptimized=1519),
            ExampleSizes("multi-irq-old", cx16SizeOptimized=962, cx16SizeUnoptimized=1410),
            ExampleSizes("plasma", cx16SizeOptimized=1745, cx16SizeUnoptimized=2066),
            ExampleSizes("rasterbars", cx16SizeOptimized=1268, cx16SizeUnoptimized=1685),
            ExampleSizes("showbmx", cx16SizeOptimized=2747, cx16SizeUnoptimized=5702),
            ExampleSizes("snow", cx16SizeOptimized=3150, cx16SizeUnoptimized=5976),
            ExampleSizes("sortingbench", cx16SizeOptimized=2934, cx16SizeUnoptimized=3939),
            ExampleSizes("spotlight", cx16SizeOptimized=908, cx16SizeUnoptimized=1364),
            ExampleSizes("starszoom", cx16SizeOptimized=2164, cx16SizeUnoptimized=5360),
            ExampleSizes("tehtriz", cx16SizeOptimized=6215, cx16SizeUnoptimized=6747),
            ExampleSizes("test_gfx_hires", cx16SizeOptimized=4791, cx16SizeUnoptimized=8106),
            ExampleSizes("test_gfx_lores", cx16SizeOptimized=5898, cx16SizeUnoptimized=7670),
            ExampleSizes("testmonogfx", cx16SizeOptimized=10406, cx16SizeUnoptimized=11699),
            ExampleSizes("textspotlight", cx16SizeOptimized=3431, cx16SizeUnoptimized=3788),
        ),
        listOf(false, true)
    )

    val target = Cx16Target()
    withData(
        nameFn = { "${it.first.first.name}: ${target.name}, optimize=${it.first.second}" },
        onlyCx16.map { it to prepareTestFiles(it.first.name, it.second, target) }
    ) { (params, prep) ->
        val filepath = prep.second
        val optimize = params.second
        val expectedSize = if (optimize) params.first.cx16SizeOptimized else params.first.cx16SizeUnoptimized
        val result = compileTheThing(filepath, optimize, target, outputDir)
        result shouldNotBe null
        if (expectedSize > 0) {
            verifyOutputFileSize(result!!, expectedSize)
        }
    }
})

class TestCompilerOnExamplesBothC64andCx16: FunSpec({

    @OptIn(ExperimentalKotest::class)
    //testExecutionMode = TestExecutionMode.LimitedConcurrency(6)
    this.concurrency = 4

    val outputDir = tempdir().toPath()

    val bothCx16AndC64 = cartesianProduct(
        listOf(
            ExampleSizes("balls", c64SizeOptimized=885, c64SizeUnoptimized=919, cx16SizeOptimized=874, cx16SizeUnoptimized=1188),
            ExampleSizes("boingball", c64SizeOptimized=1484, c64SizeUnoptimized=1544, cx16SizeOptimized=1486, cx16SizeUnoptimized=1962),
            ExampleSizes("cube3d", c64SizeOptimized=2444, c64SizeUnoptimized=2509, cx16SizeOptimized=2381, cx16SizeUnoptimized=2712),
            ExampleSizes("cube3d-float", c64SizeOptimized=2619, c64SizeUnoptimized=2668, cx16SizeOptimized=2511, cx16SizeUnoptimized=2835),
            ExampleSizes("cube3d-gfx", c64SizeOptimized=3548, c64SizeUnoptimized=3764, cx16SizeOptimized=2206, cx16SizeUnoptimized=2522),
            ExampleSizes("dirlist", c64SizeOptimized=1543, c64SizeUnoptimized=2740, cx16SizeOptimized=1525, cx16SizeUnoptimized=3822),
            ExampleSizes("fibonacci", c64SizeOptimized=574, c64SizeUnoptimized=589, cx16SizeOptimized=630, cx16SizeUnoptimized=929),
            ExampleSizes("fractal-tree", c64SizeOptimized=2820, c64SizeUnoptimized=3065, cx16SizeOptimized=1220, cx16SizeUnoptimized=1511),
            ExampleSizes("maze", c64SizeOptimized=3088, c64SizeUnoptimized=3189, cx16SizeOptimized=2869, cx16SizeUnoptimized=3251),
            ExampleSizes("mandelbrot", c64SizeOptimized=1138, c64SizeUnoptimized=1172, cx16SizeOptimized=1120, cx16SizeUnoptimized=1434),
            ExampleSizes("mandelbrot-gfx", c64SizeOptimized=1457, c64SizeUnoptimized=2533, cx16SizeOptimized=957, cx16SizeUnoptimized=1250),
            ExampleSizes("multitasking", c64SizeOptimized=1956, c64SizeUnoptimized=2029, cx16SizeOptimized=1826, cx16SizeUnoptimized=2159),
            ExampleSizes("numbergame", c64SizeOptimized=1034, c64SizeUnoptimized=1050, cx16SizeOptimized=1079, cx16SizeUnoptimized=1382),
            ExampleSizes("primes", c64SizeOptimized=520, c64SizeUnoptimized=541, cx16SizeOptimized=565, cx16SizeUnoptimized=870),
            ExampleSizes("queens", c64SizeOptimized=1061, c64SizeUnoptimized=1093, cx16SizeOptimized=1111, cx16SizeUnoptimized=1427),
            ExampleSizes("screencodes", c64SizeOptimized=667, c64SizeUnoptimized=687, cx16SizeOptimized=655, cx16SizeUnoptimized=959),
            ExampleSizes("swirl", c64SizeOptimized=828, c64SizeUnoptimized=831, cx16SizeOptimized=923, cx16SizeUnoptimized=1208),
            ExampleSizes("swirl-float", c64SizeOptimized=708, c64SizeUnoptimized=735, cx16SizeOptimized=595, cx16SizeUnoptimized=906),
            ExampleSizes("tehtriz", c64SizeOptimized=4339, c64SizeUnoptimized=4504, cx16SizeOptimized=6215, cx16SizeUnoptimized=6747),
            ExampleSizes("textelite", c64SizeOptimized=11455, c64SizeUnoptimized=12778, cx16SizeOptimized=10789, cx16SizeUnoptimized=13505),
            ExampleSizes("pointers/animalgame", c64SizeOptimized=1570, c64SizeUnoptimized=2208, cx16SizeOptimized=1605, cx16SizeUnoptimized=2492),
            ExampleSizes("pointers/binarytree", c64SizeOptimized=2562, c64SizeUnoptimized=2640, cx16SizeOptimized=2085, cx16SizeUnoptimized=2389),
            ExampleSizes("pointers/hashtable", c64SizeOptimized=3294, c64SizeUnoptimized=4266, cx16SizeOptimized=2832, cx16SizeUnoptimized=3866),
            ExampleSizes("pointers/sortedlist", c64SizeOptimized=1274, c64SizeUnoptimized=1319, cx16SizeOptimized=1223, cx16SizeUnoptimized=1532),
            ExampleSizes("pointers/sorting", c64SizeOptimized=1776, c64SizeUnoptimized=2802, cx16SizeOptimized=1788, cx16SizeUnoptimized=2739),
        ),
        listOf(false, true),
        listOf(C64Target(), Cx16Target())
    )

    withData(
        nameFn = { "${it.first.name}: ${it.third.first.name}, optimize=${it.second}" },
        bothCx16AndC64.map {
            val prep = prepareTestFiles(it.first.name, it.second, it.third)
            Triple(it.first, it.second, Triple(it.third, prep.first, prep.second))
        }
    ) { params ->
        val filepath = params.third.third
        val optimize = params.second
        val target = params.third.first
        val exampleSizes = params.first
        val expectedSize = when (target) {
            is C64Target -> if (optimize) exampleSizes.c64SizeOptimized else exampleSizes.c64SizeUnoptimized
            is Cx16Target -> if (optimize) exampleSizes.cx16SizeOptimized else exampleSizes.cx16SizeUnoptimized
            else -> 0
        }
        val result = compileTheThing(filepath, optimize, target, outputDir)
        result shouldNotBe null
        if (expectedSize > 0) {
            verifyOutputFileSize(result!!, expectedSize)
        }
    }
})

class TestCompilerOnExamplesVirtual: FunSpec({

    val outputDir = tempdir().toPath()

    val onlyVirtual = cartesianProduct(
        listOf(
            ExampleSizes("bouncegfx", virtualInstrCountOptimized=208, virtualInstrCountUnoptimized=883, virtualRegCountOptimized=100, virtualRegCountUnoptimized=396),
            ExampleSizes("bsieve", virtualInstrCountOptimized=158, virtualInstrCountUnoptimized=1141, virtualRegCountOptimized=66, virtualRegCountUnoptimized=468),
            ExampleSizes("fountain", virtualInstrCountOptimized=152, virtualInstrCountUnoptimized=829, virtualRegCountOptimized=77, virtualRegCountUnoptimized=371),
            ExampleSizes("pixelshader", virtualInstrCountOptimized=43, virtualInstrCountUnoptimized=291, virtualRegCountOptimized=15, virtualRegCountUnoptimized=109),
            ExampleSizes("sincos", virtualInstrCountOptimized=276, virtualInstrCountUnoptimized=935, virtualRegCountOptimized=133, virtualRegCountUnoptimized=411),
            ExampleSizes("pointers/animalgame", virtualInstrCountOptimized=221, virtualInstrCountUnoptimized=2167, virtualRegCountOptimized=103, virtualRegCountUnoptimized=943),
            ExampleSizes("pointers/binarytree", virtualInstrCountOptimized=510, virtualInstrCountUnoptimized=1455, virtualRegCountOptimized=251, virtualRegCountUnoptimized=628),
            ExampleSizes("pointers/hashtable", virtualInstrCountOptimized=578, virtualInstrCountUnoptimized=2349, virtualRegCountOptimized=293, virtualRegCountUnoptimized=1055),
            ExampleSizes("pointers/sortedlist", virtualInstrCountOptimized=267, virtualInstrCountUnoptimized=1643, virtualRegCountOptimized=118, virtualRegCountUnoptimized=702),
            ExampleSizes("pointers/fountain-virtual", virtualInstrCountOptimized=146, virtualInstrCountUnoptimized=824, virtualRegCountOptimized=71, virtualRegCountUnoptimized=365),
            ExampleSizes("pointers/sorting", virtualInstrCountOptimized=369, virtualInstrCountUnoptimized=2336, virtualRegCountOptimized=173, virtualRegCountUnoptimized=1020)
        ),
        listOf(false, true)
    )

    val target = VMTarget()
    withData(
        nameFn = { "${it.first.first.name}: ${target.name}, optimize=${it.first.second}" },
        onlyVirtual.map { it to prepareTestFiles(it.first.name, it.second, target) }
    ) { (params, prep) ->
        val filepath = prep.second
        val optimize = params.second
        val expectedInstrCount = if (optimize) params.first.virtualInstrCountOptimized else params.first.virtualInstrCountUnoptimized
        val expectedRegCount = if (optimize) params.first.virtualRegCountOptimized else params.first.virtualRegCountUnoptimized
        val result = compileTheThing(filepath, optimize, target, outputDir)
        result shouldNotBe null
        if (expectedInstrCount > 0) {
            verifyIrInstructionCount(result!!, expectedInstrCount)
            verifyIrRegisterCount(result, expectedRegCount)
        }
    }
})

class TestCompilerOnExamplesPET32: FunSpec({

    @OptIn(ExperimentalKotest::class)
    //testExecutionMode = TestExecutionMode.LimitedConcurrency(6)
    this.concurrency = 4

    val outputDir = tempdir().toPath()

    val onlyPET32 = cartesianProduct(
        listOf(
            ExampleSizes("boingball", pet32SizeOptimized=1497, pet32SizeUnoptimized=1586),
            ExampleSizes("petswirl", pet32SizeOptimized=915, pet32SizeUnoptimized=985),
        ),
        listOf(false, true)
    )

    val target = PETTarget()
    withData(
        nameFn = { "${it.first.first.name}: ${target.name}, optimize=${it.first.second}" },
        onlyPET32.map { it to prepareTestFiles(it.first.name, it.second, target) }
    ) { (params, prep) ->
        val filepath = prep.second
        val optimize = params.second
        val expectedSize = if (optimize) params.first.pet32SizeOptimized else params.first.pet32SizeUnoptimized
        val result = compileTheThing(filepath, optimize, target, outputDir)
        result shouldNotBe null
        if (expectedSize > 0) {
            verifyOutputFileSize(result!!, expectedSize)
        }
    }
})
