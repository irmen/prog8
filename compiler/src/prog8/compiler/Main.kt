package prog8.compiler

import prog8.ast.AstToSourceCode
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.statements.Directive
import prog8.compiler.target.C64Target
import prog8.compiler.target.CompilationTarget
import prog8.compiler.target.Cx16Target
import prog8.optimizer.UnusedCodeRemover
import prog8.optimizer.constantFold
import prog8.optimizer.optimizeStatements
import prog8.optimizer.simplifyExpressions
import prog8.parser.ModuleImporter
import prog8.parser.ParsingFailedError
import prog8.parser.moduleName
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis


class CompilationResult(val success: Boolean,
                        val programAst: Program,
                        val programName: String,
                        val importedFiles: List<Path>)


fun compileProgram(filepath: Path,
                   optimize: Boolean,
                   writeAssembly: Boolean,
                   compilationTarget: String,
                   outputDir: Path): CompilationResult {
    var programName = ""
    lateinit var programAst: Program
    lateinit var importedFiles: List<Path>
    val errors = ErrorReporter()

    when(compilationTarget) {
        C64Target.name -> CompilationTarget.instance = C64Target
        Cx16Target.name -> CompilationTarget.instance = Cx16Target
        else -> {
            System.err.println("invalid compilation target")
            exitProcess(1)
        }
    }

    try {
        val totalTime = measureTimeMillis {
            // import main module and everything it needs
            val (ast, compilationOptions, imported) = parseImports(filepath, errors)
            programAst = ast
            importedFiles = imported
            processAst(programAst, errors, compilationOptions)
            if (optimize)
                optimizeAst(programAst, errors)
            postprocessAst(programAst, errors, compilationOptions)

            // printAst(programAst)

            if(writeAssembly)
                programName = writeAssembly(programAst, errors, outputDir, optimize, compilationOptions)
        }
        System.out.flush()
        System.err.flush()
        println("\nTotal compilation+assemble time: ${totalTime / 1000.0} sec.")
        return CompilationResult(true, programAst, programName, importedFiles)

    } catch (px: ParsingFailedError) {
        System.err.print("\u001b[91m")  // bright red
        System.err.println(px.message)
        System.err.print("\u001b[0m")  // reset
    } catch (ax: AstException) {
        System.err.print("\u001b[91m")  // bright red
        System.err.println(ax.toString())
        System.err.print("\u001b[0m")  // reset
    } catch (x: Exception) {
        print("\u001b[91m")  // bright red
        println("\n* internal error *")
        print("\u001b[0m")  // reset
        System.out.flush()
        throw x
    } catch (x: NotImplementedError) {
        print("\u001b[91m")  // bright red
        println("\n* internal error: missing feature/code *")
        print("\u001b[0m")  // reset
        System.out.flush()
        throw x
    }

    return CompilationResult(false, Program("failed", mutableListOf()), programName, emptyList())
}

private fun parseImports(filepath: Path, errors: ErrorReporter): Triple<Program, CompilationOptions, List<Path>> {
    println("Compiler target: ${CompilationTarget.instance.name}. Parsing...")
    val importer = ModuleImporter()
    val programAst = Program(moduleName(filepath.fileName), mutableListOf())
    importer.importModule(programAst, filepath)
    errors.handle()

    val importedFiles = programAst.modules.filter { !it.source.startsWith("@embedded@") }.map { it.source }

    val compilerOptions = determineCompilationOptions(programAst)
    if (compilerOptions.launcher == LauncherType.BASIC && compilerOptions.output != OutputType.PRG)
        throw ParsingFailedError("${programAst.modules.first().position} BASIC launcher requires output type PRG.")

    // depending on the machine and compiler options we may have to include some libraries
    CompilationTarget.instance.machine.importLibs(compilerOptions, importer, programAst)

    // always import prog8lib and math
    importer.importLibraryModule(programAst, "math")
    importer.importLibraryModule(programAst, "prog8lib")
    errors.handle()
    return Triple(programAst, compilerOptions, importedFiles)
}

private fun determineCompilationOptions(program: Program): CompilationOptions {
    val mainModule = program.modules.first()
    val outputType = (mainModule.statements.singleOrNull { it is Directive && it.directive == "%output" }
            as? Directive)?.args?.single()?.name?.toUpperCase()
    val launcherType = (mainModule.statements.singleOrNull { it is Directive && it.directive == "%launcher" }
            as? Directive)?.args?.single()?.name?.toUpperCase()
    mainModule.loadAddress = (mainModule.statements.singleOrNull { it is Directive && it.directive == "%address" }
            as? Directive)?.args?.single()?.int ?: 0
    val zpoption: String? = (mainModule.statements.singleOrNull { it is Directive && it.directive == "%zeropage" }
            as? Directive)?.args?.single()?.name?.toUpperCase()
    val allOptions = program.modules.flatMap { it.statements }.filter { it is Directive && it.directive == "%option" }.flatMap { (it as Directive).args }.toSet()
    val floatsEnabled = allOptions.any { it.name == "enable_floats" }
    var zpType: ZeropageType =
            if (zpoption == null)
                if(floatsEnabled) ZeropageType.FLOATSAFE else ZeropageType.KERNALSAFE
            else
                try {
                    ZeropageType.valueOf(zpoption)
                } catch (x: IllegalArgumentException) {
                    ZeropageType.KERNALSAFE
                    // error will be printed by the astchecker
                }

    if (zpType==ZeropageType.FLOATSAFE && CompilationTarget.instance.name == Cx16Target.name) {
        System.err.println("Warning: Cx16 target must use zp option basicsafe instead of floatsafe")
        zpType = ZeropageType.BASICSAFE
    }

    val zpReserved = mainModule.statements
            .asSequence()
            .filter { it is Directive && it.directive == "%zpreserved" }
            .map { (it as Directive).args }
            .map { it[0].int!!..it[1].int!! }
            .toList()

    return CompilationOptions(
            if (outputType == null) OutputType.PRG else OutputType.valueOf(outputType),
            if (launcherType == null) LauncherType.BASIC else LauncherType.valueOf(launcherType),
            zpType, zpReserved, floatsEnabled
    )
}

private fun processAst(programAst: Program, errors: ErrorReporter, compilerOptions: CompilationOptions) {
    // perform initial syntax checks and processings
    println("Processing for target ${CompilationTarget.instance.name}...")
    programAst.checkIdentifiers(errors)
    errors.handle()
    programAst.constantFold(errors)
    errors.handle()
    programAst.reorderStatements()
    programAst.addTypecasts(errors)
    errors.handle()
    programAst.variousCleanups()
    programAst.checkValid(compilerOptions, errors)
    errors.handle()
    programAst.checkIdentifiers(errors)
    errors.handle()
}

private fun optimizeAst(programAst: Program, errors: ErrorReporter) {
    // optimize the parse tree
    println("Optimizing...")
    while (true) {
        // keep optimizing expressions and statements until no more steps remain
        val optsDone1 = programAst.simplifyExpressions()
        val optsDone2 = programAst.optimizeStatements(errors)
        programAst.constantFold(errors) // because simplified statements and expressions could give rise to more constants that can be folded away:
        errors.handle()
        if (optsDone1 + optsDone2 == 0)
            break
    }

    val remover = UnusedCodeRemover(errors)
    remover.visit(programAst)
    remover.applyModifications()
    errors.handle()
}

private fun postprocessAst(programAst: Program, errors: ErrorReporter, compilerOptions: CompilationOptions) {
    programAst.addTypecasts(errors)
    errors.handle()
    programAst.variousCleanups()
    programAst.checkValid(compilerOptions, errors)          // check if final tree is still valid
    errors.handle()
    programAst.checkRecursion(errors)         // check if there are recursive subroutine calls
    errors.handle()
    programAst.verifyFunctionArgTypes()
}

private fun writeAssembly(programAst: Program, errors: ErrorReporter, outputDir: Path,
                          optimize: Boolean, compilerOptions: CompilationOptions): String {
    // asm generation directly from the Ast,
    programAst.processAstBeforeAsmGeneration(errors)
    errors.handle()

    // printAst(programAst)

    CompilationTarget.instance.machine.initializeZeropage(compilerOptions)
    val assembly = CompilationTarget.instance.asmGenerator(
            programAst,
            errors,
            CompilationTarget.instance.machine.zeropage,
            compilerOptions,
            outputDir).compileToAssembly(optimize)
    assembly.assemble(compilerOptions)
    errors.handle()
    return assembly.name
}

fun printAst(programAst: Program) {
    println()
    val printer = AstToSourceCode(::print, programAst)
    printer.visit(programAst)
    println()
}

