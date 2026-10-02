package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.core.*
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.compileProgram
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.assumeReadableFile
import prog8tests.helpers.fixturesDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*


private val minimalProgram = """
    %zeropage basicsafe
    %option no_sysinit
    main {
        sub start() {
            cx16.r0++
        }
    }
"""

private fun writeSourceTo(dir: Path, source: String): Path {
    val filepath = dir.resolve("testoutput.p8")
    filepath.toFile().writeText(source)
    return filepath
}

// local copy of the compileTheThing pattern: the shared compileText/compileFile helpers cannot pass an outputFile
private fun compileWithOutputFile(
    filepath: Path,
    target: ICompilationTarget,
    outputDir: Path,
    outputFile: Path,
    assemble: Boolean = false
): CompilationResult? {
    val args = CompilerArguments(
        filepath,
        optimize = false,
        writeAssembly = true,
        warnSymbolShadowing = false,
        warnImplicitTypeCasts = false,
        quietAll = true,
        quietAssembler = true,
        showTimings = false,
        asmListfile = false,
        includeSourcelines = false,
        newCodegen = false,
        dumpVariables = false,
        dumpSymbols = false,
        varsHighBank = null,
        varsGolden = false,
        varsAddress = null,
        compilationTarget = target.name,
        breakpointCpuInstruction = null,
        printAst1 = false,
        printAst2 = false,
        ignoreFootguns = false,
        profilingInstrumentation = false,
        symbolDefs = emptyMap(),
        outputDir = outputDir,
        outputFile = outputFile,
        errors = ErrorReporterForTests(),
        assemble = assemble
    )
    return compileProgram(args)
}

class TestOutputFileName : FunSpec({

    test("outputStemOf strips the last extension") {
        outputStemOf(Path.of("game.pgz")) shouldBe "game"
        outputStemOf(Path.of("my.game")) shouldBe "my"
    }

    test("outputStemOf keeps names without an extension unchanged") {
        outputStemOf(Path.of("game")) shouldBe "game"
    }

    test("outputStemOf treats a leading dot as not an extension") {
        outputStemOf(Path.of(".pgz")) shouldBe ".pgz"
    }

    test("programFile maps each OutputType to its legacy extension") {
        val outputDir = tempdir().toPath()
        fun options(output: OutputType) = CompilationOptions.builder(C64Target())
            .output(output)
            .outputDir(outputDir)
            .build()
        options(OutputType.PRG).programFile("prog") shouldBe outputDir.resolve("prog.prg")
        options(OutputType.XEX).programFile("prog") shouldBe outputDir.resolve("prog.xex")
        options(OutputType.RAW).programFile("prog") shouldBe outputDir.resolve("prog.bin")
        options(OutputType.LIBRARY).programFile("prog") shouldBe outputDir.resolve("prog.bin")
        options(OutputType.ELF).programFile("prog") shouldBe outputDir.resolve("prog.elf")
        options(OutputType.AMIGAHUNK).programFile("prog") shouldBe outputDir.resolve("prog")
    }

    test("programFile special-cases VMTarget to the p8ir artifact") {
        val outputDir = tempdir().toPath()
        val options = CompilationOptions.builder(VMTarget())
            .output(OutputType.ELF)
            .outputDir(outputDir)
            .build()
        options.programFile("prog") shouldBe outputDir.resolve("prog.p8ir")
    }

    test("programFile enforces the .p8ir extension for -o on the virtual target") {
        val tmp = tempdir().toPath()
        val builder = { ir: Path ->
            CompilationOptions.builder(VMTarget()).outputDir(tmp).outputFile(ir).build()
        }
        // the VM identifies the IR by its extension, so an extension-less -o must get one appended;
        // only the exact lowercase ".p8ir" is recognized as already being an IR file
        builder(tmp.resolve("game")).programFile("prog") shouldBe tmp.resolve("game.p8ir")
        builder(tmp.resolve("game.p8ir")).programFile("prog") shouldBe tmp.resolve("game.p8ir")
        builder(tmp.resolve("game.P8IR")).programFile("prog") shouldBe tmp.resolve("game.P8IR.p8ir")
        builder(tmp.resolve("sub/game")).programFile("prog") shouldBe tmp.resolve("sub/game.p8ir")

        // ... and the stem must follow the resolved name, so the aux files stay consistent with the artifact
        OutputFiles.of(builder(tmp.resolve("game")), "prog").ir() shouldBe tmp.resolve("game.p8ir")
        OutputFiles.of(builder(tmp.resolve("game")), "prog").stem shouldBe "game"
    }

    test("OutputFiles.of with -o resolves the artifact verbatim and all aux files from its stem") {
        val tmp = tempdir().toPath()
        val outDir = tmp.resolve("out")
        val options = CompilationOptions.builder(Cx16Target())
            .outputDir(tmp)
            .outputFile(outDir.resolve("custom.pgz"))
            .build()
        val outs = OutputFiles.of(options, "prog")
        outs.programFile shouldBe outDir.resolve("custom.pgz")
        outs.stem shouldBe "custom"
        outs.dir shouldBe outDir
        outs.asm() shouldBe outDir.resolve("custom.asm")
        outs.list() shouldBe outDir.resolve("custom.list")
        outs.viceMonList() shouldBe outDir.resolve("custom.vice-mon-list")
        outs.binFile() shouldBe outDir.resolve("custom.bin")
        outs.ir() shouldBe outDir.resolve("custom.p8ir")
        outs.bankedCalls() shouldBe outDir.resolve("custom.bankedcalls")
        outs.obj() shouldBe outDir.resolve("custom.o")
        outs.linkScript() shouldBe outDir.resolve("custom.link.ld")
    }

    test("viceMonList is derived from the stem, never from the program file name") {
        val tmp = tempdir().toPath()
        val outDir = tmp.resolve("out")
        val options = CompilationOptions.builder(Cx16Target())
            .outputDir(tmp)
            .outputFile(outDir.resolve("custom.pgz"))
            .build()
        val outs = OutputFiles.of(options, "prog")
        withClue("vice-mon-list must not keep the program file extension") {
            outs.viceMonList() shouldNotBe outDir.resolve("custom.pgz.vice-mon-list")
        }
    }

    test("OutputFiles.of with a bare -o filename resolves aux files relative to outputDir") {
        val tmp = tempdir().toPath()
        val options = CompilationOptions.builder(Cx16Target())
            .outputDir(tmp)
            .outputFile(Path.of("game.pgz"))
            .build()
        val outs = OutputFiles.of(options, "prog")
        outs.programFile shouldBe Path.of("game.pgz")
        outs.dir shouldBe tmp
        outs.asm() shouldBe tmp.resolve("game.asm")
        outs.ir() shouldBe tmp.resolve("game.p8ir")
    }

    test("legacy naming keeps the full dotted program stem when -o is not used") {
        val tmp = tempdir().toPath()
        val options = CompilationOptions.builder(Cx16Target()).outputDir(tmp).build()
        val outs = OutputFiles.of(options, "my.game")
        withClue("legacy stem must be used verbatim, not re-derived by outputStemOf") {
            outs.stem shouldBe "my.game"
            outs.asm() shouldBe tmp.resolve("my.game.asm")
            outs.list() shouldBe tmp.resolve("my.game.list")
            outs.viceMonList() shouldBe tmp.resolve("my.game.vice-mon-list")
            outs.ir() shouldBe tmp.resolve("my.game.p8ir")
            outs.bankedCalls() shouldBe tmp.resolve("my.game.bankedcalls")
            outs.obj() shouldBe tmp.resolve("my.game.o")
            outs.linkScript() shouldBe tmp.resolve("my.game.link.ld")
        }
        outs.asm() shouldNotBe tmp.resolve("my.asm")
    }

    test("legacy program file path is unchanged without -o") {
        val tmp = tempdir().toPath()
        val options = CompilationOptions.builder(Cx16Target()).outputDir(tmp).build()
        OutputFiles.of(options, "prog").programFile shouldBe tmp.resolve("prog.prg")
    }

    test("compiling with -o writes the aux files next to the artifact") {
        val outputDir = tempdir().toPath()
        val outDir = outputDir.resolve("out")
        outDir.createDirectories()
        val outputFile = outDir.resolve("custom.pgz")
        val filepath = writeSourceTo(outputDir, minimalProgram)
        val result = compileWithOutputFile(filepath, Cx16Target(), outputDir, outputFile)
        result shouldNotBe null
        withClue("no assembler ran, so the artifact must not exist yet") {
            outputFile.exists() shouldBe false
        }
        withClue("the aux .asm must be named after the -o stem") {
            outDir.resolve("custom.asm").exists() shouldBe true
        }
        withClue("no source-named .asm may appear in the output directory") {
            outDir.resolve("testoutput.asm").exists() shouldBe false
            val topLevelAsms = Files.list(outputDir).use { stream ->
                stream.filter { it.name.endsWith(".asm") }.toList()
            }
            topLevelAsms shouldBe emptyList()
        }
        result!!.compilationOptions.outputFile shouldBe outputFile
    }

    test("compiling with -o and assemble=true writes the final artifact to the -o path") {
        val outputDir = tempdir().toPath()
        val outDir = outputDir.resolve("out")
        outDir.createDirectories()
        val outputFile = outDir.resolve("custom.prg")
        val filepath = writeSourceTo(outputDir, minimalProgram)
        val result = compileWithOutputFile(filepath, Cx16Target(), outputDir, outputFile, assemble = true)
        result shouldNotBe null
        withClue("the assembled artifact must exist at the -o path") {
            outputFile.exists() shouldBe true
            outputFile.isRegularFile() shouldBe true
        }
        withClue("the aux .asm must be named after the -o stem") {
            outDir.resolve("custom.asm").exists() shouldBe true
        }
    }

    test("%asmbinary under -o keeps the .binary path relative to the generated .asm") {
        val outputDir = tempdir().toPath()
        val outputFile = outputDir.resolve("game.prg")
        val p8Path = assumeReadableFile(fixturesDir, "asmBinaryFromSameFolder.p8")
        val result = compileWithOutputFile(p8Path, Cx16Target(), outputDir, outputFile)
        result shouldNotBe null
        val asmPath = OutputFiles.of(result!!.compilationOptions, result.compilerAst.name).asm()
        asmPath.exists() shouldBe true
        val asmLines = asmPath.readLines().map { it.trim() }
        val binaryLine = asmLines.first { it.contains(".binary") }
        val binPath = binaryLine.substringAfter(".binary \"").substringBefore('"')
        withClue("the .binary path must be relative to the .asm file, not absolute: $binaryLine") {
            Path.of(binPath).isAbsolute shouldBe false
        }
    }

    test("virtual target -o writes the .p8ir artifact") {
        val outputDir = tempdir().toPath()
        val outputFile = outputDir.resolve("ir.p8ir")
        val filepath = writeSourceTo(outputDir, minimalProgram)
        val result = compileWithOutputFile(filepath, VMTarget(), outputDir, outputFile, assemble = true)
        result shouldNotBe null
        outputFile.exists() shouldBe true
        outputFile.isRegularFile() shouldBe true
    }

    test("virtual target -o without extension still writes a .p8ir artifact") {
        val outputDir = tempdir().toPath()
        val filepath = writeSourceTo(outputDir, minimalProgram)
        val result = compileWithOutputFile(filepath, VMTarget(), outputDir, outputDir.resolve("ir"), assemble = true)
        result shouldNotBe null
        outputDir.resolve("ir.p8ir").exists() shouldBe true
        outputDir.resolve("ir").exists() shouldBe false
    }
})
