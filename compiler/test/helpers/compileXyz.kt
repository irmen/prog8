package prog8tests.helpers

import prog8.code.core.ICompilationTarget
import prog8.code.core.IErrorReporter
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.compileProgram
import java.nio.file.Path
import kotlin.io.path.name


internal fun compileFile(
    platform: ICompilationTarget,
    optimize: Boolean,
    fileDir: Path,
    fileName: String,
    outputDir: Path = prog8tests.helpers.outputDir,
    errors: IErrorReporter? = null,
    writeAssembly: Boolean = true,
) : CompilationResult? {
    val filepath = fileDir.resolve(fileName)
    assumeReadableFile(filepath)
    val args = CompilerArguments(
        filepath,
        optimize,
        writeAssembly = writeAssembly,
        warnSymbolShadowing = false,
        quietAssembler = true,
        asmListfile = false,
        includeSourcelines = false,
        experimentalCodegen = false,
        varsHighBank = null,
        varsGolden = false,
        slabsHighBank = null,
        slabsGolden = false,
        platform.name,
        symbolDefs = emptyMap(),
        outputDir = outputDir,
        errors = errors ?: ErrorReporterForTests(),
        splitWordArrays = false,
        breakpointCpuInstruction = null,
        printAst1 = false,
        printAst2 = false
    )
    return compileProgram(args)
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
    writeAssembly: Boolean = true,
) : CompilationResult? {
    val filePath = outputDir.resolve("on_the_fly_test_" + sourceText.hashCode().toUInt().toString(16) + ".p8")
    // we don't assumeNotExists(filePath) - should be ok to just overwrite it
    filePath.toFile().writeText(sourceText)
    return compileFile(platform, optimize, filePath.parent, filePath.name,
        errors=errors, writeAssembly=writeAssembly)
}
