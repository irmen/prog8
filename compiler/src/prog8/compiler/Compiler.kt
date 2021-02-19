package prog8.compiler

import prog8.ast.AstToSourceCode
import prog8.ast.IBuiltinFunctions
import prog8.ast.IMemSizer
import prog8.ast.Program
import prog8.ast.base.AstException
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.Directive
import prog8.compiler.astprocessing.*
import prog8.compiler.functions.*
import prog8.compiler.target.C64Target
import prog8.compiler.target.Cx16Target
import prog8.compiler.target.ICompilationTarget
import prog8.compiler.target.asmGeneratorFor
import prog8.optimizer.*
import prog8.parser.ModuleImporter
import prog8.parser.ParsingFailedError
import prog8.parser.moduleName
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis


enum class OutputType {
    RAW,
    PRG
}

enum class LauncherType {
    BASIC,
    NONE
}

enum class ZeropageType {
    BASICSAFE,
    FLOATSAFE,
    KERNALSAFE,
    FULL,
    DONTUSE
}

data class CompilationOptions(val output: OutputType,
                              val launcher: LauncherType,
                              val zeropage: ZeropageType,
                              val zpReserved: List<IntRange>,
                              val floats: Boolean,
                              val noSysInit: Boolean,
                              val compTarget: ICompilationTarget) {
    var slowCodegenWarnings = false
    var optimize = false
}


class CompilerException(message: String?) : Exception(message)

class CompilationResult(val success: Boolean,
                        val programAst: Program,
                        val programName: String,
                        val compTarget: ICompilationTarget,
                        val importedFiles: List<Path>)


fun compileProgram(filepath: Path,
                   optimize: Boolean,
                   writeAssembly: Boolean,
                   slowCodegenWarnings: Boolean,
                   compilationTarget: String,
                   outputDir: Path): CompilationResult {
    var programName = ""
    lateinit var programAst: Program
    lateinit var importedFiles: List<Path>
    val errors = ErrorReporter()

    val compTarget =
        when(compilationTarget) {
            C64Target.name -> C64Target
            Cx16Target.name -> Cx16Target
            else -> {
                System.err.println("invalid compilation target")
                exitProcess(1)
            }
        }

    try {
        val totalTime = measureTimeMillis {
            // import main module and everything it needs
            val (ast, compilationOptions, imported) = parseImports(filepath, errors, compTarget)
            compilationOptions.slowCodegenWarnings = slowCodegenWarnings
            compilationOptions.optimize = optimize
            programAst = ast
            importedFiles = imported
            processAst(programAst, errors, compilationOptions)
            if (compilationOptions.optimize)
                optimizeAst(programAst, errors, BuiltinFunctionsFacade(BuiltinFunctions), compTarget)
            postprocessAst(programAst, errors, compilationOptions)

            // printAst(programAst)

            if(writeAssembly)
                programName = writeAssembly(programAst, errors, outputDir, compilationOptions)
        }
        System.out.flush()
        System.err.flush()
        println("\nTotal compilation+assemble time: ${totalTime / 1000.0} sec.")
        return CompilationResult(true, programAst, programName, compTarget, importedFiles)

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

    val failedProgram = Program("failed", mutableListOf(), BuiltinFunctionsFacade(BuiltinFunctions), compTarget)
    return CompilationResult(false, failedProgram, programName, compTarget, emptyList())
}

private class BuiltinFunctionsFacade(functions: Map<String, FSignature>): IBuiltinFunctions {
    lateinit var program: Program

    override val names = functions.keys
    override val purefunctionNames = functions.filter { it.value.pure }.map { it.key }.toSet()

    override fun constValue(name: String, args: List<Expression>, position: Position, memsizer: IMemSizer): NumericLiteralValue? {
        val func = BuiltinFunctions[name]
        if(func!=null) {
            val exprfunc = func.constExpressionFunc
            if(exprfunc!=null) {
                return try {
                    exprfunc(args, position, program, memsizer)
                } catch(x: NotConstArgumentException) {
                    // const-evaluating the builtin function call failed.
                    null
                } catch(x: CannotEvaluateException) {
                    // const-evaluating the builtin function call failed.
                    null
                }
            }
            else if(func.known_returntype==null)
                throw IllegalArgumentException("builtin function $name can't be used here because it doesn't return a value")
        }
        return null
    }
    override fun returnType(name: String, args: MutableList<Expression>) =
        builtinFunctionReturnType(name, args, program)
}

private fun parseImports(filepath: Path, errors: ErrorReporter, compTarget: ICompilationTarget): Triple<Program, CompilationOptions, List<Path>> {
    val compilationTargetName = compTarget.name
    println("Compiler target: $compilationTargetName. Parsing...")
    val importer = ModuleImporter()
    val bf = BuiltinFunctionsFacade(BuiltinFunctions)
    val programAst = Program(moduleName(filepath.fileName), mutableListOf(), bf, compTarget)
    bf.program = programAst
    importer.importModule(programAst, filepath, compTarget, compilationTargetName)
    errors.handle()

    val importedFiles = programAst.modules.filter { !it.source.startsWith("@embedded@") }.map { it.source }
    val compilerOptions = determineCompilationOptions(programAst, compTarget)
    if (compilerOptions.launcher == LauncherType.BASIC && compilerOptions.output != OutputType.PRG)
        throw ParsingFailedError("${programAst.modules.first().position} BASIC launcher requires output type PRG.")

    // depending on the machine and compiler options we may have to include some libraries
    for(lib in compTarget.machine.importLibs(compilerOptions, compilationTargetName))
        importer.importLibraryModule(programAst, lib, compTarget, compilationTargetName)

    // always import prog8_lib and math
    importer.importLibraryModule(programAst, "math", compTarget, compilationTargetName)
    importer.importLibraryModule(programAst, "prog8_lib", compTarget, compilationTargetName)
    errors.handle()
    return Triple(programAst, compilerOptions, importedFiles)
}

private fun determineCompilationOptions(program: Program, compTarget: ICompilationTarget): CompilationOptions {
    val mainModule = program.mainModule
    val outputType = (mainModule.statements.singleOrNull { it is Directive && it.directive == "%output" }
            as? Directive)?.args?.single()?.name?.toUpperCase()
    val launcherType = (mainModule.statements.singleOrNull { it is Directive && it.directive == "%launcher" }
            as? Directive)?.args?.single()?.name?.toUpperCase()
    val zpoption: String? = (mainModule.statements.singleOrNull { it is Directive && it.directive == "%zeropage" }
            as? Directive)?.args?.single()?.name?.toUpperCase()
    val allOptions = program.modules.flatMap { it.statements }.filter { it is Directive && it.directive == "%option" }.flatMap { (it as Directive).args }.toSet()
    val floatsEnabled = allOptions.any { it.name == "enable_floats" }
    val noSysInit = allOptions.any { it.name == "no_sysinit" }
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

    if (zpType==ZeropageType.FLOATSAFE && compTarget.name == Cx16Target.name) {
        System.err.println("Warning: Cx16 target must use zp option basicsafe instead of floatsafe")
        zpType = ZeropageType.BASICSAFE
    }

    val zpReserved = mainModule.statements
            .asSequence()
            .filter { it is Directive && it.directive == "%zpreserved" }
            .map { (it as Directive).args }
            .map { it[0].int!!..it[1].int!! }
            .toList()

    if(outputType!=null && !OutputType.values().any {it.name==outputType}) {
        System.err.println("invalid output type $outputType")
        exitProcess(1)
    }
    if(launcherType!=null && !LauncherType.values().any {it.name==launcherType}) {
        System.err.println("invalid launcher type $launcherType")
        exitProcess(1)
    }

    return CompilationOptions(
            if (outputType == null) OutputType.PRG else OutputType.valueOf(outputType),
            if (launcherType == null) LauncherType.BASIC else LauncherType.valueOf(launcherType),
            zpType, zpReserved, floatsEnabled, noSysInit,
            compTarget
    )
}

private fun processAst(programAst: Program, errors: ErrorReporter, compilerOptions: CompilationOptions) {
    // perform initial syntax checks and processings
    println("Processing for target ${compilerOptions.compTarget.name}...")
    programAst.checkIdentifiers(errors, compilerOptions.compTarget)
    errors.handle()
    programAst.constantFold(errors, compilerOptions.compTarget)
    errors.handle()
    programAst.reorderStatements(errors)
    errors.handle()
    programAst.addTypecasts(errors)
    errors.handle()
    programAst.variousCleanups()
    programAst.checkValid(compilerOptions, errors, compilerOptions.compTarget)
    errors.handle()
    programAst.checkIdentifiers(errors, compilerOptions.compTarget)
    errors.handle()
}

private fun optimizeAst(programAst: Program, errors: ErrorReporter, functions: IBuiltinFunctions, compTarget: ICompilationTarget) {
    // optimize the parse tree
    println("Optimizing...")
    while (true) {
        // keep optimizing expressions and statements until no more steps remain
        val optsDone1 = programAst.simplifyExpressions()
        val optsDone2 = programAst.splitBinaryExpressions(compTarget)
        val optsDone3 = programAst.optimizeStatements(errors, functions, compTarget)
        programAst.constantFold(errors, compTarget) // because simplified statements and expressions can result in more constants that can be folded away
        errors.handle()
        if (optsDone1 + optsDone2 + optsDone3 == 0)
            break
    }

    val remover = UnusedCodeRemover(programAst, errors, compTarget)
    remover.visit(programAst)
    remover.applyModifications()
    errors.handle()
}

private fun postprocessAst(programAst: Program, errors: ErrorReporter, compilerOptions: CompilationOptions) {
    programAst.addTypecasts(errors)
    errors.handle()
    programAst.variousCleanups()
    programAst.checkValid(compilerOptions, errors, compilerOptions.compTarget)          // check if final tree is still valid
    errors.handle()
    val callGraph = CallGraph(programAst)
    callGraph.checkRecursiveCalls(errors)
    errors.handle()
    programAst.verifyFunctionArgTypes()
    programAst.moveMainAndStartToFirst()
}

private fun writeAssembly(programAst: Program,
                          errors: ErrorReporter,
                          outputDir: Path,
                          compilerOptions: CompilationOptions): String {
    // asm generation directly from the Ast,
    programAst.processAstBeforeAsmGeneration(errors, compilerOptions.compTarget)
    errors.handle()

    // printAst(programAst)

    compilerOptions.compTarget.machine.initializeZeropage(compilerOptions)
    val assembly = asmGeneratorFor(compilerOptions.compTarget,
            programAst,
            errors,
            compilerOptions.compTarget.machine.zeropage,
            compilerOptions,
            outputDir).compileToAssembly()
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

fun loadAsmIncludeFile(filename: String, source: Path): String {
    return if (filename.startsWith("library:")) {
        val resource = tryGetEmbeddedResource(filename.substring(8))
            ?: throw IllegalArgumentException("library file '$filename' not found")
        resource.bufferedReader().use { it.readText() }
    } else {
        // first try in the isSameAs folder as where the containing file was imported from
        val sib = source.resolveSibling(filename)
        if (sib.toFile().isFile)
            sib.toFile().readText()
        else
            File(filename).readText()
    }
}

internal fun tryGetEmbeddedResource(name: String): InputStream? {
    return object{}.javaClass.getResourceAsStream("/prog8lib/$name")
}
