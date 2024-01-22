package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import prog8.code.target.Cx16Target
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.compileProgram
import prog8tests.helpers.assumeReadableFile
import prog8tests.helpers.fixturesDir
import prog8tests.helpers.outputDir
import prog8tests.helpers.workingDir
import java.nio.file.Path
import kotlin.io.path.absolute

/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */
class TestCompilerOptionSourcedirs: FunSpec({

    fun compileFile(filePath: Path, sourceDirs: List<String>): CompilationResult? {
        val args = CompilerArguments(
            filepath = filePath,
            optimize = false,
            writeAssembly = true,
            warnSymbolShadowing = false,
            quietAssembler = true,
            asmListfile = false,
            includeSourcelines = false,
            experimentalCodegen = false,
            varsHighBank = null,
            varsGolden = false,
            slabsHighBank = null,
            slabsGolden = false,
            compilationTarget = Cx16Target.NAME,
            splitWordArrays = false,
            breakpointCpuInstruction = null,
            printAst1 = false,
            printAst2 = false,
            symbolDefs = emptyMap(),
            sourceDirs,
            outputDir
        )
        return compileProgram(args)
    }

    test("testAbsoluteFilePathOutsideWorkingDir") {
        val filepath = assumeReadableFile(fixturesDir, "ast_simple_main.p8")
        compileFile(filepath.absolute(), listOf()) shouldNotBe null
    }

    test("testFilePathOutsideWorkingDirRelativeToWorkingDir") {
        val filepath = workingDir.relativize(assumeReadableFile(fixturesDir, "ast_simple_main.p8").absolute())
        compileFile(filepath, listOf()) shouldNotBe null
    }

    test("testFilePathOutsideWorkingDirRelativeTo1stInSourcedirs") {
        val filepath = assumeReadableFile(fixturesDir, "ast_simple_main.p8")
        val sourcedirs = listOf("$fixturesDir")
        compileFile(filepath.fileName, sourcedirs) shouldNotBe null
    }

})
