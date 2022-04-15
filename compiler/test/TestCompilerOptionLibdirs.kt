package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import prog8.code.target.Cx16Target
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.compileProgram
import prog8tests.helpers.Helpers
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText

/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */
class TestCompilerOptionSourcedirs: FunSpec({

    lateinit var tempFileInWorkingDir: Path

    beforeSpec {
        tempFileInWorkingDir = createTempFile(directory = Helpers.workingDir, prefix = "tmp_", suffix = ".p8")
            .also { it.writeText("""
                main {
                    sub start() {
                    }
                }
            """)}
    }

    afterSpec {
        tempFileInWorkingDir.deleteExisting()
    }

    fun compileFile(filePath: Path, sourceDirs: List<String>): CompilationResult? {
        val args = CompilerArguments(
            filepath = filePath,
            optimize = false,
            optimizeFloatExpressions = false,
            dontReinitGlobals = false,
            writeAssembly = true,
            slowCodegenWarnings = false,
            quietAssembler = true,
            asmListfile = false,
            experimentalCodegen = false,
            compilationTarget = Cx16Target.NAME,
            sourceDirs,
            Helpers.outputDir
        )
        return compileProgram(args)
    }

    test("testAbsoluteFilePathInWorkingDir") {
        val filepath = Helpers.assumeReadableFile(tempFileInWorkingDir.absolute())
        compileFile(filepath, listOf()) shouldNotBe null
    }

    test("testFilePathInWorkingDirRelativeToWorkingDir") {
        val filepath = Helpers.assumeReadableFile(Helpers.workingDir.relativize(tempFileInWorkingDir.absolute()))
        compileFile(filepath, listOf()) shouldNotBe null
    }

    test("testFilePathInWorkingDirRelativeTo1stInSourcedirs") {
        val filepath = Helpers.assumeReadableFile(tempFileInWorkingDir)
        compileFile(filepath.fileName, listOf(Helpers.workingDir.toString())) shouldNotBe null
    }

    test("testAbsoluteFilePathOutsideWorkingDir") {
        val filepath = Helpers.assumeReadableFile(Helpers.fixturesDir, "ast_simple_main.p8")
        compileFile(filepath.absolute(), listOf()) shouldNotBe null
    }

    test("testFilePathOutsideWorkingDirRelativeToWorkingDir") {
        val filepath = Helpers.workingDir.relativize(Helpers.assumeReadableFile(Helpers.fixturesDir, "ast_simple_main.p8").absolute())
        compileFile(filepath, listOf()) shouldNotBe null
    }

    test("testFilePathOutsideWorkingDirRelativeTo1stInSourcedirs") {
        val filepath = Helpers.assumeReadableFile(Helpers.fixturesDir, "ast_simple_main.p8")
        val sourcedirs = listOf("${Helpers.fixturesDir}")
        compileFile(filepath.fileName, sourcedirs) shouldNotBe null
    }

})
