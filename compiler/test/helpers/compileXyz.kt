package prog8tests.helpers

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import prog8.ast.Program
import prog8.codegen.cpu6502.AsmGen
import prog8.codegen.target.C64Target
import prog8.codegen.target.c64.C64Zeropage
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.compileProgram
import prog8.compilerinterface.*
import java.nio.file.Path
import kotlin.io.path.name


internal fun CompilationResult.assertSuccess(description: String = ""): CompilationResult {
    withClue("expected successful compilation but failed $description") {
        success shouldBe true
    }
    return this
}

internal fun CompilationResult.assertFailure(description: String = ""): CompilationResult {
    withClue("expected failure to compile but succeeded $description") {
        success shouldBe false
    }
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
    outputDir: Path = prog8tests.helpers.outputDir,
    errors: IErrorReporter? = null,
    writeAssembly: Boolean = true,
    optFloatExpr: Boolean = true
) : CompilationResult {
    val filepath = fileDir.resolve(fileName)
    assumeReadableFile(filepath)
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
) : CompilationResult {
    val filePath = outputDir.resolve("on_the_fly_test_" + sourceText.hashCode().toUInt().toString(16) + ".p8")
    // we don't assumeNotExists(filePath) - should be ok to just overwrite it
    filePath.toFile().writeText(sourceText)
    return compileFile(platform, optimize, filePath.parent, filePath.name, errors=errors, writeAssembly=writeAssembly, optFloatExpr = optFloatExpr)
}


internal fun generateAssembly(
    program: Program,
    variables: IVariablesAndConsts,
    options: CompilationOptions? = null
): IAssemblyProgram? {
    val coptions = options ?: CompilationOptions(OutputType.RAW, LauncherType.CBMBASIC, ZeropageType.DONTUSE, emptyList(), true, true, C64Target(), outputDir = outputDir)
    coptions.compTarget.machine.zeropage = C64Zeropage(coptions)
    val asmgen = AsmGen(program, ErrorReporterForTests(), variables, coptions)
    return asmgen.compileToAssembly()
}
