package prog8.compiler

import com.github.michaelbull.result.*
import prog8.ast.AstToSourceTextConverter
import prog8.ast.IBuiltinFunctions
import prog8.ast.Program
import prog8.ast.base.AstException
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.Directive
import prog8.compiler.astprocessing.*
import prog8.codegen.target.C64Target
import prog8.codegen.target.Cx16Target
import prog8.codegen.target.cpu6502.codegen.AsmGen
import prog8.compilerinterface.*
import prog8.optimizer.*
import prog8.parser.ParseError
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.system.measureTimeMillis


class CompilationResult(val success: Boolean,
                        val program: Program,
                        val programName: String,
                        val compTarget: ICompilationTarget,
                        val importedFiles: List<Path>)

class CompilerArguments(val filepath: Path,
                        val optimize: Boolean,
                        val optimizeFloatExpressions: Boolean,
                        val writeAssembly: Boolean,
                        val slowCodegenWarnings: Boolean,
                        val quietAssembler: Boolean,
                        val asmListfile: Boolean,
                        val compilationTarget: String,
                        val sourceDirs: List<String> = emptyList(),
                        val outputDir: Path = Path(""),
                        val errors: IErrorReporter = ErrorReporter())


fun compileProgram(args: CompilerArguments): CompilationResult {
    var programName = ""
    lateinit var program: Program
    lateinit var importedFiles: List<Path>

    val optimizeFloatExpr = if(args.optimize) args.optimizeFloatExpressions else false

    val compTarget =
        when(args.compilationTarget) {
            C64Target.name -> C64Target
            Cx16Target.name -> Cx16Target
            else -> throw IllegalArgumentException("invalid compilation target")
        }

    try {
        val totalTime = measureTimeMillis {
            // import main module and everything it needs
            val (programresult, compilationOptions, imported) = parseImports(args.filepath, args.errors, compTarget, args.sourceDirs)
            with(compilationOptions) {
                slowCodegenWarnings = args.slowCodegenWarnings
                optimize = args.optimize
                optimizeFloatExpressions = optimizeFloatExpr
                asmQuiet = args.quietAssembler
                asmListfile = args.asmListfile
            }
            program = programresult
            importedFiles = imported
            processAst(program, args.errors, compilationOptions)
            if (compilationOptions.optimize) {
//                println("*********** AST RIGHT BEFORE OPTIMIZING *************")
//                printProgram(program)

                optimizeAst(
                    program,
                    compilationOptions,
                    args.errors,
                    BuiltinFunctionsFacade(BuiltinFunctions),
                    compTarget
                )
            }
            postprocessAst(program, args.errors, compilationOptions)

//            println("*********** AST BEFORE ASSEMBLYGEN *************")
//            printProgram(program)

            if (args.writeAssembly) {
                when (val result = writeAssembly(program, args.errors, args.outputDir, compilationOptions)) {
                    is WriteAssemblyResult.Ok -> programName = result.filename
                    is WriteAssemblyResult.Fail -> {
                        System.err.println(result.error)
                        return CompilationResult(false, program, programName, compTarget, importedFiles)
                    }
                }
            }
        }
        System.out.flush()
        System.err.flush()
        println("\nTotal compilation+assemble time: ${totalTime / 1000.0} sec.")
        return CompilationResult(true, program, programName, compTarget, importedFiles)
    } catch (px: ParseError) {
        System.err.print("\u001b[91m")  // bright red
        System.err.println("${px.position.toClickableStr()} parse error: ${px.message}".trim())
        System.err.print("\u001b[0m")  // reset
    } catch (ac: AbortCompilation) {
        if(!ac.message.isNullOrEmpty()) {
            System.err.print("\u001b[91m")  // bright red
            System.err.println(ac.message)
            System.err.print("\u001b[0m")  // reset
        }
    } catch (nsf: NoSuchFileException) {
        System.err.print("\u001b[91m")  // bright red
        System.err.println("File not found: ${nsf.message}")
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

    val failedProgram = Program("failed", BuiltinFunctionsFacade(BuiltinFunctions), compTarget, compTarget)
    return CompilationResult(false, failedProgram, programName, compTarget, emptyList())
}

private class BuiltinFunctionsFacade(functions: Map<String, FSignature>): IBuiltinFunctions {
    lateinit var program: Program

    override val names = functions.keys
    override val purefunctionNames = functions.filter { it.value.pure }.map { it.key }.toSet()

    override fun constValue(name: String, args: List<Expression>, position: Position): NumericLiteralValue? {
        val func = BuiltinFunctions[name]
        if(func!=null) {
            val exprfunc = func.constExpressionFunc
            if(exprfunc!=null) {
                return try {
                    exprfunc(args, position, program)
                } catch(x: NotConstArgumentException) {
                    // const-evaluating the builtin function call failed.
                    null
                } catch(x: CannotEvaluateException) {
                    // const-evaluating the builtin function call failed.
                    null
                }
            }
            else if(func.known_returntype==null)
                return null  // builtin function $name can't be used here because it doesn't return a value
        }
        return null
    }
    override fun returnType(name: String, args: MutableList<Expression>) =
        builtinFunctionReturnType(name, args, program)
}

fun parseImports(filepath: Path,
                 errors: IErrorReporter,
                 compTarget: ICompilationTarget,
                 sourceDirs: List<String>): Triple<Program, CompilationOptions, List<Path>> {
    println("Compiler target: ${compTarget.name}. Parsing...")
    val bf = BuiltinFunctionsFacade(BuiltinFunctions)
    val program = Program(filepath.nameWithoutExtension, bf, compTarget, compTarget)
    bf.program = program

    val importer = ModuleImporter(program, compTarget.name, errors, sourceDirs)
    val importedModuleResult = importer.importModule(filepath)
    importedModuleResult.onFailure { throw it }
    errors.report()

    val importedFiles = program.modules.map { it.source }
        .filter { it.isFromFilesystem }
        .map { Path(it.origin) }
    val compilerOptions = determineCompilationOptions(program, compTarget)
    // depending on the machine and compiler options we may have to include some libraries
    for(lib in compTarget.machine.importLibs(compilerOptions, compTarget.name))
        importer.importLibraryModule(lib)

    // always import prog8_lib and math
    importer.importLibraryModule("math")
    importer.importLibraryModule("prog8_lib")

    if (compilerOptions.launcher == LauncherType.BASIC && compilerOptions.output != OutputType.PRG)
        errors.err("BASIC launcher requires output type PRG", program.toplevelModule.position)
    errors.report()

    return Triple(program, compilerOptions, importedFiles)
}

fun determineCompilationOptions(program: Program, compTarget: ICompilationTarget): CompilationOptions {
    val toplevelModule = program.toplevelModule
    val outputDirective = (toplevelModule.statements.singleOrNull { it is Directive && it.directive == "%output" } as? Directive)
    val launcherDirective = (toplevelModule.statements.singleOrNull { it is Directive && it.directive == "%launcher" } as? Directive)
    val outputTypeStr = outputDirective?.args?.single()?.name?.uppercase()
    val launcherTypeStr = launcherDirective?.args?.single()?.name?.uppercase()
    val zpoption: String? = (toplevelModule.statements.singleOrNull { it is Directive && it.directive == "%zeropage" }
            as? Directive)?.args?.single()?.name?.uppercase()
    val allOptions = program.modules.flatMap { it.statements }.filter { it is Directive && it.directive == "%option" }
        .flatMap { (it as Directive).args }.toSet()
    val floatsEnabled = allOptions.any { it.name == "enable_floats" }
    val noSysInit = allOptions.any { it.name == "no_sysinit" }
    var zpType: ZeropageType =
        if (zpoption == null)
            if (floatsEnabled) ZeropageType.FLOATSAFE else ZeropageType.KERNALSAFE
        else
            try {
                ZeropageType.valueOf(zpoption)
            } catch (x: IllegalArgumentException) {
                ZeropageType.KERNALSAFE
                // error will be printed by the astchecker
            }

    if (zpType == ZeropageType.FLOATSAFE && compTarget.name == Cx16Target.name) {
        System.err.println("Warning: zp option floatsafe changed to basicsafe for cx16 target")
        zpType = ZeropageType.BASICSAFE
    }

    val zpReserved = toplevelModule.statements
        .asSequence()
        .filter { it is Directive && it.directive == "%zpreserved" }
        .map { (it as Directive).args }
        .map { it[0].int!!..it[1].int!! }
        .toList()

    val outputType = if (outputTypeStr == null) OutputType.PRG else {
        try {
            OutputType.valueOf(outputTypeStr)
        } catch (x: IllegalArgumentException) {
            // set default value; actual check and error handling of invalid option is handled in the AstChecker later
            OutputType.PRG
        }
    }
    val launcherType = if (launcherTypeStr == null) LauncherType.BASIC else {
        try {
            LauncherType.valueOf(launcherTypeStr)
        } catch (x: IllegalArgumentException) {
            // set default value; actual check and error handling of invalid option is handled in the AstChecker later
            LauncherType.BASIC
        }
    }

    return CompilationOptions(
        outputType,
        launcherType,
        zpType, zpReserved, floatsEnabled, noSysInit,
        compTarget
    )
}

private fun processAst(program: Program, errors: IErrorReporter, compilerOptions: CompilationOptions) {
    // perform initial syntax checks and processings
    println("Processing for target ${compilerOptions.compTarget.name}...")
    program.preprocessAst(program, errors)
    program.checkIdentifiers(errors, program, compilerOptions)
    errors.report()
    // TODO: turning char literals into UBYTEs via an encoding should really happen in code gen - but for that we'd need DataType.CHAR
    //       ...but what do we gain from this? We can leave it as it is now: where a char literal is no more than syntactic sugar for an UBYTE value.
    //       By introduction a CHAR dt, we will also lose the opportunity to do constant-folding on any expression containing a char literal.
    //       Yes this is different from strings that are only encoded in the code gen phase.
    program.charLiteralsToUByteLiterals(compilerOptions.compTarget)
    program.constantFold(errors, compilerOptions.compTarget)
    errors.report()
    program.desugaring(errors)
    errors.report()
    program.reorderStatements(errors, compilerOptions)
    errors.report()
    program.addTypecasts(errors, compilerOptions)
    errors.report()
    program.variousCleanups(program, errors)
    errors.report()
    program.checkValid(errors, compilerOptions)
    errors.report()
    program.checkIdentifiers(errors, program, compilerOptions)
    errors.report()
}

private fun optimizeAst(program: Program, compilerOptions: CompilationOptions, errors: IErrorReporter, functions: IBuiltinFunctions, compTarget: ICompilationTarget) {
    // optimize the parse tree
    println("Optimizing...")

    val remover = UnusedCodeRemover(program, errors, compTarget)
    remover.visit(program)
    remover.applyModifications()

    while (true) {
        // keep optimizing expressions and statements until no more steps remain
        val optsDone1 = program.simplifyExpressions()
        val optsDone2 = program.splitBinaryExpressions(compilerOptions, compTarget)
        val optsDone3 = program.optimizeStatements(errors, functions, compTarget)
        program.constantFold(errors, compTarget) // because simplified statements and expressions can result in more constants that can be folded away
        errors.report()
        if (optsDone1 + optsDone2 + optsDone3 == 0)
            break
    }
    errors.report()
}

private fun postprocessAst(program: Program, errors: IErrorReporter, compilerOptions: CompilationOptions) {
    program.desugaring(errors)
    program.addTypecasts(errors, compilerOptions)
    errors.report()
    program.variousCleanups(program, errors)
    val callGraph = CallGraph(program)
    callGraph.checkRecursiveCalls(errors)
    errors.report()
    program.verifyFunctionArgTypes()
    program.moveMainAndStartToFirst()
    program.checkValid(errors, compilerOptions)          // check if final tree is still valid
    errors.report()
}

private sealed class WriteAssemblyResult {
    class Ok(val filename: String): WriteAssemblyResult()
    class Fail(val error: String): WriteAssemblyResult()
}

private fun writeAssembly(program: Program,
                          errors: IErrorReporter,
                          outputDir: Path,
                          compilerOptions: CompilationOptions
): WriteAssemblyResult {
    // asm generation directly from the Ast
    program.processAstBeforeAsmGeneration(compilerOptions, errors)
    errors.report()

//    println("*********** AST RIGHT BEFORE ASM GENERATION *************")
//    printProgram(program)

    compilerOptions.compTarget.machine.initializeZeropage(compilerOptions)
    val assembly = asmGeneratorFor(compilerOptions.compTarget,
            program,
            errors,
            compilerOptions.compTarget.machine.zeropage,
            compilerOptions,
            outputDir).compileToAssembly()
    errors.report()

    return if(assembly.valid && errors.noErrors()) {
        val assemblerReturnStatus = assembly.assemble(compilerOptions)
        if(assemblerReturnStatus!=0)
            WriteAssemblyResult.Fail("assembler step failed with return code $assemblerReturnStatus")
        else {
            WriteAssemblyResult.Ok(assembly.name)
        }
    } else {
        WriteAssemblyResult.Fail("compiler failed with errors")
    }
}

fun printProgram(program: Program) {
    println()
    val printer = AstToSourceTextConverter(::print, program)
    printer.visit(program)
    println()
}

internal fun asmGeneratorFor(
    compTarget: ICompilationTarget,
    program: Program,
    errors: IErrorReporter,
    zp: Zeropage,
    options: CompilationOptions,
    outputDir: Path
): IAssemblyGenerator
{
    // at the moment we only have one code generation backend (for 6502 and 65c02)
    return AsmGen(program, errors, zp, options, compTarget, outputDir)
}
