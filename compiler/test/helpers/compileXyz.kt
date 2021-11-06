package prog8tests.helpers

import prog8.compiler.CompilationResult
import prog8.compiler.compileProgram
import prog8.compilerinterface.ICompilationTarget
import prog8.compilerinterface.IErrorReporter
import prog8tests.ast.helpers.assumeReadableFile
import prog8tests.ast.helpers.outputDir
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.test.assertFalse
import kotlin.test.assertTrue


internal fun CompilationResult.assertSuccess(description: String = ""): CompilationResult {
    assertTrue(success, "expected successful compilation but failed $description")
    return this
}

internal fun CompilationResult.assertFailure(description: String = ""): CompilationResult {
    assertFalse(success, "expected failure to compile but succeeded $description")
    return this
}

/**
 * @see CompilationResult.assertSuccess
 * @see CompilationResult.assertFailure
 */
internal fun compileFile(
    platform: ICompilationTarget,
    optimize: Boolean,
    fileDir: Path,
    fileName: String,
    outputDir: Path = prog8tests.ast.helpers.outputDir,
    errors: IErrorReporter? = null,
    writeAssembly: Boolean = true
) : CompilationResult {
    val filepath = fileDir.resolve(fileName)
    assumeReadableFile(filepath)
    return compileProgram(
        filepath,
        optimize,
        optimizeFloatExpressions = false,
        writeAssembly = writeAssembly,
        slowCodegenWarnings = false,
        quietAssembler = true,
        platform.name,
        sourceDirs = listOf(),
        outputDir,
        errors = errors ?: ErrorReporterForTests()
    )
}

/**
 * Takes a [sourceText] as a String, writes it to a temporary
 * file and then runs the compiler on that.
 * @see compileFile
 */
internal fun compileText(
    platform: ICompilationTarget,
    optimize: Boolean,
    sourceText: String,
    errors: IErrorReporter? = null,
    writeAssembly: Boolean = true
) : CompilationResult {
    val filePath = outputDir.resolve("on_the_fly_test_" + sourceText.hashCode().toUInt().toString(16) + ".p8")
    // we don't assumeNotExists(filePath) - should be ok to just overwrite it
    filePath.toFile().writeText(sourceText)
    return compileFile(platform, optimize, filePath.parent, filePath.name, errors=errors, writeAssembly=writeAssembly)
}
