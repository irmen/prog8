package prog8tests.helpers

import prog8.ast.Program
import prog8.code.core.*
import prog8.code.target.C64Target
import prog8.code.target.c64.C64Zeropage
import prog8.codegen.cpu6502.AsmGen
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.astprocessing.SymbolTableMaker
import prog8.compiler.compileProgram
import prog8.compiler.determineProgramLoadAddress
import java.nio.file.Path
import kotlin.io.path.name


internal fun compileFile(
    platform: ICompilationTarget,
    optimize: Boolean,
    fileDir: Path,
    fileName: String,
    outputDir: Path = prog8tests.helpers.Helpers.outputDir,
    errors: IErrorReporter? = null,
    writeAssembly: Boolean = true,
    optFloatExpr: Boolean = true
) : CompilationResult? {
    val filepath = fileDir.resolve(fileName)
    Helpers.assumeReadableFile(filepath)
    val args = CompilerArguments(
        filepath,
        optimize,
        optimizeFloatExpressions = optFloatExpr,
        dontReinitGlobals = false,
        writeAssembly = writeAssembly,
        slowCodegenWarnings = false,
        quietAssembler = true,
        asmListfile = false,
        experimentalCodegen = false,
        platform.name,
        outputDir = outputDir,
        errors = errors ?: ErrorReporterForTests()
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
    optFloatExpr: Boolean = true
) : CompilationResult? {
    val filePath = Helpers.outputDir.resolve("on_the_fly_test_" + sourceText.hashCode().toUInt().toString(16) + ".p8")
    // we don't assumeNotExists(filePath) - should be ok to just overwrite it
    filePath.toFile().writeText(sourceText)
    return compileFile(platform, optimize, filePath.parent, filePath.name, errors=errors, writeAssembly=writeAssembly, optFloatExpr = optFloatExpr)
}


internal fun generateAssembly(
    program: Program,
    options: CompilationOptions? = null
): IAssemblyProgram? {
    val coptions = options ?: CompilationOptions(OutputType.RAW, CbmPrgLauncherType.BASIC, ZeropageType.DONTUSE, emptyList(),
        floats = true,
        noSysInit = true,
        compTarget = C64Target(),
        loadAddress = 0u, outputDir = Helpers.outputDir)
    coptions.compTarget.machine.zeropage = C64Zeropage(coptions)
    val st = SymbolTableMaker().makeFrom(program)
    val errors = ErrorReporterForTests()
    determineProgramLoadAddress(program, coptions, errors)
    errors.report()
    val asmgen = AsmGen(program, st, coptions, errors)
    errors.report()
    return asmgen.compileToAssembly()
}
