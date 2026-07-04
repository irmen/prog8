package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.concurrency.TestExecutionMode
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
 *
 * IMPORTANT: The file size checks (cx16SizeOptimized, cx16SizeUnoptimized, etc.) are FRAGILE.
 * These tests should be SKIPPED during regular test runs.
 * Only run these tests explicitly when you have INTENTIONALLY changed the code generator
 * and need to update the expected file sizes ON USER REQUEST.
 * To skip these tests during normal development, do NOT run tests with "TestCompilerOnExamples" in the name.
 * To explicitly run these tests to update expected values:
 *   gradle :compiler:test --tests "prog8tests.compiler.TestCompilerOnExamplesCx16"
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
        newCodegen = false,
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

    this.blockingTest = true
    this.testExecutionMode = TestExecutionMode.LimitedConcurrency(kotlin.math.max(1, Runtime.getRuntime().availableProcessors()/2))

    val onlyC64 = cartesianProduct(
        listOf(
            ExampleSizes("balloonflight", c64SizeOptimized=1674, c64SizeUnoptimized=1733),
            ExampleSizes("banking", c64SizeOptimized=680, c64SizeUnoptimized=1356),
            ExampleSizes("bdmusic-irq", c64SizeOptimized=1001, c64SizeUnoptimized=1019),
            ExampleSizes("bdmusic", c64SizeOptimized=938, c64SizeUnoptimized=1011),
            ExampleSizes("charset", c64SizeOptimized=835, c64SizeUnoptimized=860),
            ExampleSizes("cube3d-sprites", c64SizeOptimized=2664, c64SizeUnoptimized=2769),
            ExampleSizes("fileselector", c64SizeOptimized=3523, c64SizeUnoptimized=4510),
            ExampleSizes("library/main", c64SizeOptimized=321, c64SizeUnoptimized=1147),
            ExampleSizes("multiplexer", c64SizeOptimized=1356, c64SizeUnoptimized=1507),
            ExampleSizes("petswirl", c64SizeOptimized=939, c64SizeUnoptimized=1005),
            ExampleSizes("plasma", c64SizeOptimized=1682, c64SizeUnoptimized=1723),
            ExampleSizes("rasterbars", c64SizeOptimized=845, c64SizeUnoptimized=892),
            ExampleSizes("simplemultiplexer", c64SizeOptimized=843, c64SizeUnoptimized=900),
            ExampleSizes("sprites", c64SizeOptimized=963, c64SizeUnoptimized=1028),
            ExampleSizes("starfield", c64SizeOptimized=743, c64SizeUnoptimized=758),
            ExampleSizes("turtle-gfx", c64SizeOptimized=2758, c64SizeUnoptimized=2858),
            ExampleSizes("wizzine", c64SizeOptimized=1124, c64SizeUnoptimized=1224),
            // boingball, cube3d, tehtriz are in BothC64andCx16
        ),
        listOf(false, true)
    )

    val target = C64Target()
    onlyC64.forEach { (params, optimize) ->
        val prep = prepareTestFiles(params.name, optimize, target)
        test(prep.first) {
            val testOutputDir = tempdir().toPath()
            val filepath = prep.second
            val expectedSize = if (optimize) params.c64SizeOptimized else params.c64SizeUnoptimized
            val result = compileTheThing(filepath, optimize, target, testOutputDir)
            result shouldNotBe null
            if (expectedSize > 0) {
                verifyOutputFileSize(result!!, expectedSize)
            }
        }
    }
})

class TestCompilerOnExamplesCx16: FunSpec({

    this.blockingTest = true
    this.testExecutionMode = TestExecutionMode.LimitedConcurrency(kotlin.math.max(1, Runtime.getRuntime().availableProcessors()/2))

    val onlyCx16 = cartesianProduct(
        listOf(
            ExampleSizes("vtui/testvtui", cx16SizeOptimized=3041, cx16SizeUnoptimized=3041),
            ExampleSizes("pcmaudio/play-adpcm", cx16SizeOptimized=36147, cx16SizeUnoptimized=36645),
            ExampleSizes("pcmaudio/stream-wav", cx16SizeOptimized=5442, cx16SizeUnoptimized=7634),
            ExampleSizes("pcmaudio/stream-simple-aflow", cx16SizeOptimized=1842, cx16SizeUnoptimized=3612),
            ExampleSizes("pcmaudio/stream-simple-poll", cx16SizeOptimized=1556, cx16SizeUnoptimized=3319),
            ExampleSizes("pcmaudio/vumeter", cx16SizeOptimized=3880, cx16SizeUnoptimized=6236),
            ExampleSizes("sprites/dragon", cx16SizeOptimized=1858, cx16SizeUnoptimized=4318),
            ExampleSizes("sprites/dragons", cx16SizeOptimized=1503, cx16SizeUnoptimized=3626),
            ExampleSizes("zsmkit_v1/demo1", cx16SizeOptimized=7546, cx16SizeUnoptimized=7984),
            ExampleSizes("zsmkit_v1/demo2", cx16SizeOptimized=708, cx16SizeUnoptimized=2516),
            // zsmkit_v1/zsmkit_high and zsmkit_v1/zsmkit_low don't compile (empty source files)
            ExampleSizes("zsmkit_v2/demo", cx16SizeOptimized=790, cx16SizeUnoptimized=2868),
            // zsmkit_v2/zsmkit doesn't compile (empty source file)
            ExampleSizes("banking/program", cx16SizeOptimized=665, cx16SizeUnoptimized=2510),
            ExampleSizes("dynamicbanking/program", cx16SizeOptimized=1211, cx16SizeUnoptimized=3089),
            ExampleSizes("fileselector/standalone", cx16SizeOptimized=4135, cx16SizeUnoptimized=6070),
            ExampleSizes("fileselector/main", cx16SizeOptimized=480, cx16SizeUnoptimized=2314),
            // fileselector/fselector and fileselector/namesorting don't compile (empty source files)
            ExampleSizes("pointers/fountain-cx16", cx16SizeOptimized=1179, cx16SizeUnoptimized=1520),
            ExampleSizes("amiga", cx16SizeOptimized=7631, cx16SizeUnoptimized=11628),
            ExampleSizes("audioroutines", cx16SizeOptimized=948, cx16SizeUnoptimized=1244),
            ExampleSizes("automatons", cx16SizeOptimized=1037, cx16SizeUnoptimized=1342),
            ExampleSizes("balloonflight", cx16SizeOptimized=3187, cx16SizeUnoptimized=4202),
            ExampleSizes("bdmusic", cx16SizeOptimized=2427, cx16SizeUnoptimized=2792),
            ExampleSizes("bobs", cx16SizeOptimized=2800, cx16SizeUnoptimized=3414),
            ExampleSizes("bubbleuniverse", cx16SizeOptimized=1450, cx16SizeUnoptimized=1967),
            ExampleSizes("charfade", cx16SizeOptimized=1054, cx16SizeUnoptimized=1376),
            ExampleSizes("charsets", cx16SizeOptimized=1125, cx16SizeUnoptimized=1417),
            ExampleSizes("circles", cx16SizeOptimized=2377, cx16SizeUnoptimized=4495),
            ExampleSizes("cobramk3-gfx", cx16SizeOptimized=7756, cx16SizeUnoptimized=10434),
            ExampleSizes("colorbars", cx16SizeOptimized=1606, cx16SizeUnoptimized=1904),
            ExampleSizes("cxlogo", cx16SizeOptimized=599, cx16SizeUnoptimized=895),
            ExampleSizes("diskspeed", cx16SizeOptimized=6134, cx16SizeUnoptimized=8105),
            ExampleSizes("fileseek", cx16SizeOptimized=2468, cx16SizeUnoptimized=4297),
            ExampleSizes("floatparse", cx16SizeOptimized=8317, cx16SizeUnoptimized=9186),
            ExampleSizes("interpolation", cx16SizeOptimized=3783, cx16SizeUnoptimized=4719),
            ExampleSizes("kefrenbars", cx16SizeOptimized=2386, cx16SizeUnoptimized=4625),
            ExampleSizes("keyboardhandler", cx16SizeOptimized=412, cx16SizeUnoptimized=717),
            ExampleSizes("landscape", cx16SizeOptimized=1665, cx16SizeUnoptimized=2019),
            ExampleSizes("life", cx16SizeOptimized=1320, cx16SizeUnoptimized=1751),
            ExampleSizes("mandelbrot", cx16SizeOptimized=1120, cx16SizeUnoptimized=1434),
            ExampleSizes("multi-irq-new", cx16SizeOptimized=1073, cx16SizeUnoptimized=1519),
            ExampleSizes("multi-irq-old", cx16SizeOptimized=962, cx16SizeUnoptimized=1410),
            ExampleSizes("plasma", cx16SizeOptimized=1731, cx16SizeUnoptimized=2066),
            ExampleSizes("rasterbars", cx16SizeOptimized=1268, cx16SizeUnoptimized=1685),
            ExampleSizes("serialdownload", cx16SizeOptimized=2903, cx16SizeUnoptimized=4052),
            ExampleSizes("showbmx", cx16SizeOptimized=2745, cx16SizeUnoptimized=5093),
            ExampleSizes("snow", cx16SizeOptimized=3112, cx16SizeUnoptimized=5240),
            ExampleSizes("sortingbench", cx16SizeOptimized=2928, cx16SizeUnoptimized=3857),
            ExampleSizes("spotlight", cx16SizeOptimized=908, cx16SizeUnoptimized=1364),
            ExampleSizes("starszoom", cx16SizeOptimized=2399, cx16SizeUnoptimized=4820),
            ExampleSizes("test_gfx_hires", cx16SizeOptimized=4760, cx16SizeUnoptimized=7444),
            ExampleSizes("test_gfx_lores", cx16SizeOptimized=5820, cx16SizeUnoptimized=7004),
            ExampleSizes("testmonogfx", cx16SizeOptimized=10255, cx16SizeUnoptimized=11631),
            ExampleSizes("textspotlight", cx16SizeOptimized=3431, cx16SizeUnoptimized=3788),
        ),
        listOf(false, true)
    )

    val target = Cx16Target()
    onlyCx16.forEach { (params, optimize) ->
        val prep = prepareTestFiles(params.name, optimize, target)
        test(prep.first) {
            val testOutputDir = tempdir().toPath()
            val filepath = prep.second
            val expectedSize = if (optimize) params.cx16SizeOptimized else params.cx16SizeUnoptimized
            val result = compileTheThing(filepath, optimize, target, testOutputDir)
            result shouldNotBe null
            if (expectedSize > 0) {
                verifyOutputFileSize(result!!, expectedSize)
            }
        }
    }
})

class TestCompilerOnExamplesBothC64andCx16: FunSpec({

    this.blockingTest = true
    this.testExecutionMode = TestExecutionMode.LimitedConcurrency(kotlin.math.max(1, Runtime.getRuntime().availableProcessors()/2))

    val bothCx16AndC64 = cartesianProduct(
        listOf(
            ExampleSizes("balls", c64SizeOptimized=885, c64SizeUnoptimized=927, cx16SizeOptimized=874, cx16SizeUnoptimized=1200),
            ExampleSizes("boingball", c64SizeOptimized=1513, c64SizeUnoptimized=1579, cx16SizeOptimized=1486, cx16SizeUnoptimized=1968),
            ExampleSizes("cube3d", c64SizeOptimized=2436, c64SizeUnoptimized=2513, cx16SizeOptimized=2373, cx16SizeUnoptimized=2704),
            ExampleSizes("cube3d-float", c64SizeOptimized=2654, c64SizeUnoptimized=2703, cx16SizeOptimized=2511, cx16SizeUnoptimized=2835),
            ExampleSizes("cube3d-gfx", c64SizeOptimized=3528, c64SizeUnoptimized=3625, cx16SizeOptimized=2198, cx16SizeUnoptimized=2522),
            ExampleSizes("dirlist", c64SizeOptimized=1515, c64SizeUnoptimized=2291, cx16SizeOptimized=1525, cx16SizeUnoptimized=3270),
            ExampleSizes("fibonacci", c64SizeOptimized=570, c64SizeUnoptimized=585, cx16SizeOptimized=626, cx16SizeUnoptimized=925),
            ExampleSizes("fractal-tree", c64SizeOptimized=2832, c64SizeUnoptimized=2869, cx16SizeOptimized=1220, cx16SizeUnoptimized=1511),
            ExampleSizes("maze", c64SizeOptimized=3088, c64SizeUnoptimized=3277, cx16SizeOptimized=2873, cx16SizeUnoptimized=3251),
            ExampleSizes("mandelbrot", c64SizeOptimized=1173, c64SizeUnoptimized=1213, cx16SizeOptimized=1120, cx16SizeUnoptimized=1434),
            ExampleSizes("mandelbrot-gfx", c64SizeOptimized=1492, c64SizeUnoptimized=1514, cx16SizeOptimized=957, cx16SizeUnoptimized=1250),
            ExampleSizes("multitasking", c64SizeOptimized=1900, c64SizeUnoptimized=1955, cx16SizeOptimized=1812, cx16SizeUnoptimized=2145),
            ExampleSizes("numbergame", c64SizeOptimized=1034, c64SizeUnoptimized=1050, cx16SizeOptimized=1079, cx16SizeUnoptimized=1382),
            ExampleSizes("primes", c64SizeOptimized=520, c64SizeUnoptimized=541, cx16SizeOptimized=565, cx16SizeUnoptimized=870),
            ExampleSizes("queens", c64SizeOptimized=1053, c64SizeUnoptimized=1085, cx16SizeOptimized=1103, cx16SizeUnoptimized=1419),
            ExampleSizes("screencodes", c64SizeOptimized=639, c64SizeUnoptimized=655, cx16SizeOptimized=647, cx16SizeUnoptimized=951),
            ExampleSizes("swirl", c64SizeOptimized=828, c64SizeUnoptimized=839, cx16SizeOptimized=923, cx16SizeUnoptimized=1220),
            ExampleSizes("swirl-float", c64SizeOptimized=732, c64SizeUnoptimized=759, cx16SizeOptimized=595, cx16SizeUnoptimized=906),
            ExampleSizes("tehtriz", c64SizeOptimized=4325, c64SizeUnoptimized=4600, cx16SizeOptimized=6244, cx16SizeUnoptimized=6773),
            ExampleSizes("textelite", c64SizeOptimized=11190, c64SizeUnoptimized=12083, cx16SizeOptimized=10683, cx16SizeUnoptimized=12683),
            ExampleSizes("pointers/animalgame", c64SizeOptimized=1570, c64SizeUnoptimized=2126, cx16SizeOptimized=1605, cx16SizeUnoptimized=2412),
            ExampleSizes("pointers/binarytree", c64SizeOptimized=2562, c64SizeUnoptimized=2627, cx16SizeOptimized=2085, cx16SizeUnoptimized=2389),
            ExampleSizes("pointers/hashtable", c64SizeOptimized=3294, c64SizeUnoptimized=4125, cx16SizeOptimized=2832, cx16SizeUnoptimized=3699),
            ExampleSizes("pointers/sortedlist", c64SizeOptimized=1262, c64SizeUnoptimized=1304, cx16SizeOptimized=1215, cx16SizeUnoptimized=1528),
            ExampleSizes("pointers/sorting", c64SizeOptimized=1776, c64SizeUnoptimized=2523, cx16SizeOptimized=1788, cx16SizeUnoptimized=2653),
        ),
        listOf(false, true),
        listOf(C64Target(), Cx16Target())
    )

    bothCx16AndC64.forEach { (exampleSizes, optimize, target) ->
        val prep = prepareTestFiles(exampleSizes.name, optimize, target)
        test(prep.first) {
            val testOutputDir = tempdir().toPath()
            val filepath = prep.second
            val expectedSize = when (target) {
                is C64Target -> if (optimize) exampleSizes.c64SizeOptimized else exampleSizes.c64SizeUnoptimized
                is Cx16Target -> if (optimize) exampleSizes.cx16SizeOptimized else exampleSizes.cx16SizeUnoptimized
                else -> 0
            }
            val result = compileTheThing(filepath, optimize, target, testOutputDir)
            result shouldNotBe null
            if (expectedSize > 0) {
                verifyOutputFileSize(result!!, expectedSize)
            }
        }
    }
})

class TestCompilerOnExamplesVirtual: FunSpec({

    this.blockingTest = true
    this.testExecutionMode = TestExecutionMode.LimitedConcurrency(kotlin.math.max(1, Runtime.getRuntime().availableProcessors()/2))

    val onlyVirtual = cartesianProduct(
        listOf(
            ExampleSizes("bouncegfx", virtualInstrCountOptimized=240, virtualInstrCountUnoptimized=860, virtualRegCountOptimized=97, virtualRegCountUnoptimized=386),
            ExampleSizes("bsieve", virtualInstrCountOptimized=273, virtualInstrCountUnoptimized=2001, virtualRegCountOptimized=74, virtualRegCountUnoptimized=866),
            ExampleSizes("fountain", virtualInstrCountOptimized=207, virtualInstrCountUnoptimized=829, virtualRegCountOptimized=80, virtualRegCountUnoptimized=369),
            ExampleSizes("pixelshader", virtualInstrCountOptimized=93, virtualInstrCountUnoptimized=308, virtualRegCountOptimized=17, virtualRegCountUnoptimized=115),
            ExampleSizes("sincos", virtualInstrCountOptimized=335, virtualInstrCountUnoptimized=934, virtualRegCountOptimized=138, virtualRegCountUnoptimized=409),
            ExampleSizes("pointers/animalgame", virtualInstrCountOptimized=330, virtualInstrCountUnoptimized=2145, virtualRegCountOptimized=111, virtualRegCountUnoptimized=939),
            ExampleSizes("pointers/binarytree", virtualInstrCountOptimized=652, virtualInstrCountUnoptimized=2321, virtualRegCountOptimized=260, virtualRegCountUnoptimized=1026),
            ExampleSizes("pointers/hashtable", virtualInstrCountOptimized=700, virtualInstrCountUnoptimized=2326, virtualRegCountOptimized=300, virtualRegCountUnoptimized=1051),
            ExampleSizes("pointers/sortedlist", virtualInstrCountOptimized=404, virtualInstrCountUnoptimized=2488, virtualRegCountOptimized=130, virtualRegCountUnoptimized=1092),
            ExampleSizes("pointers/fountain-virtual", virtualInstrCountOptimized=203, virtualInstrCountUnoptimized=824, virtualRegCountOptimized=75, virtualRegCountUnoptimized=363),
            ExampleSizes("pointers/sorting", virtualInstrCountOptimized=530, virtualInstrCountUnoptimized=2322, virtualRegCountOptimized=183, virtualRegCountUnoptimized=1016)
        ),
        listOf(false, true)
    )

    val target = VMTarget()
    onlyVirtual.forEach { (params, optimize) ->
        val prep = prepareTestFiles(params.name, optimize, target)
        test(prep.first) {
            val testOutputDir = tempdir().toPath()
            val filepath = prep.second
            val expectedInstrCount = if (optimize) params.virtualInstrCountOptimized else params.virtualInstrCountUnoptimized
            val expectedRegCount = if (optimize) params.virtualRegCountOptimized else params.virtualRegCountUnoptimized
            val result = compileTheThing(filepath, optimize, target, testOutputDir)
            result shouldNotBe null
            if (expectedInstrCount > 0) {
                verifyIrInstructionCount(result!!, expectedInstrCount)
                verifyIrRegisterCount(result, expectedRegCount)
            }
        }
    }
})

class TestCompilerOnExamplesPET32: FunSpec({

    this.blockingTest = true
    this.testExecutionMode = TestExecutionMode.LimitedConcurrency(kotlin.math.max(1, Runtime.getRuntime().availableProcessors()/2))

    val onlyPET32 = cartesianProduct(
        listOf(
            ExampleSizes("boingball", pet32SizeOptimized=1514, pet32SizeUnoptimized=1570),
            ExampleSizes("petswirl", pet32SizeOptimized=895, pet32SizeUnoptimized=961),
            ExampleSizes("music", pet32SizeOptimized=876, pet32SizeUnoptimized=925),
        ),
        listOf(false, true)
    )

    val target = PETTarget()
    onlyPET32.forEach { (params, optimize) ->
        val prep = prepareTestFiles(params.name, optimize, target)
        test(prep.first) {
            val testOutputDir = tempdir().toPath()
            val filepath = prep.second
            val expectedSize = if (optimize) params.pet32SizeOptimized else params.pet32SizeUnoptimized
            val result = compileTheThing(filepath, optimize, target, testOutputDir)
            result shouldNotBe null
            if (expectedSize > 0) {
                verifyOutputFileSize(result!!, expectedSize)
            }
        }
    }
})
