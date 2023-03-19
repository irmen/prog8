package prog8.compiler

import com.github.michaelbull.result.onFailure
import prog8.ast.IBuiltinFunctions
import prog8.ast.Program
import prog8.ast.base.AstException
import prog8.ast.expressions.Expression
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Directive
import prog8.code.SymbolTableMaker
import prog8.code.ast.PtProgram
import prog8.code.core.*
import prog8.code.target.*
import prog8.codegen.vm.VmCodeGen
import prog8.compiler.astprocessing.*
import prog8.optimizer.*
import prog8.parser.ParseError
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.math.round
import kotlin.system.measureTimeMillis


class CompilationResult(val compilerAst: Program,   // deprecated, use codegenAst instead
                        val codegenAst: PtProgram?,
                        val compilationOptions: CompilationOptions,
                        val importedFiles: List<Path>)

class CompilerArguments(val filepath: Path,
                        val optimize: Boolean,
                        val optimizeFloatExpressions: Boolean,
                        val writeAssembly: Boolean,
                        val slowCodegenWarnings: Boolean,
                        val quietAssembler: Boolean,
                        val asmListfile: Boolean,
                        val experimentalCodegen: Boolean,
                        val varsHigh: Boolean,
                        val useRPN: Boolean,
                        val compilationTarget: String,
                        val evalStackBaseAddress: UInt?,
                        val symbolDefs: Map<String, String>,
                        val sourceDirs: List<String> = emptyList(),
                        val outputDir: Path = Path(""),
                        val errors: IErrorReporter = ErrorReporter())


fun compileProgram(args: CompilerArguments): CompilationResult? {
    lateinit var program: Program
    lateinit var importedFiles: List<Path>

    val optimizeFloatExpr = if(args.optimize) args.optimizeFloatExpressions else false

    val compTarget =
        when(args.compilationTarget) {
            C64Target.NAME -> C64Target()
            C128Target.NAME -> C128Target()
            Cx16Target.NAME -> Cx16Target()
            AtariTarget.NAME -> AtariTarget()
            VMTarget.NAME -> VMTarget()
            else -> throw IllegalArgumentException("invalid compilation target")
        }

    var compilationOptions: CompilationOptions
    var ast: PtProgram? = null

    try {
        val totalTime = measureTimeMillis {
            val (programresult, options, imported) = parseMainModule(args.filepath, args.errors, compTarget, args.sourceDirs)
            compilationOptions = options

            with(compilationOptions) {
                slowCodegenWarnings = args.slowCodegenWarnings
                optimize = args.optimize
                optimizeFloatExpressions = optimizeFloatExpr
                asmQuiet = args.quietAssembler
                asmListfile = args.asmListfile
                experimentalCodegen = args.experimentalCodegen
                varsHigh = args.varsHigh
                useRPN = args.useRPN
                evalStackBaseAddress = args.evalStackBaseAddress
                outputDir = args.outputDir.normalize()
                symbolDefs = args.symbolDefs
            }
            program = programresult
            importedFiles = imported

            if(compilationOptions.evalStackBaseAddress!=null) {
                compTarget.machine.overrideEvalStack(compilationOptions.evalStackBaseAddress!!)
            }

            processAst(program, args.errors, compilationOptions)
            if (compilationOptions.optimize) {
//                println("*********** COMPILER AST RIGHT BEFORE OPTIMIZING *************")
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

//            println("*********** COMPILER AST BEFORE ASSEMBLYGEN *************")
//            printProgram(program)

            determineProgramLoadAddress(program, compilationOptions, args.errors)
            args.errors.report()

            if (args.writeAssembly) {

                compilationOptions.compTarget.machine.initializeMemoryAreas(compilationOptions)
                program.processAstBeforeAsmGeneration(compilationOptions, args.errors)
                args.errors.report()

                val intermediateAst = IntermediateAstMaker(program, compilationOptions).transform()
//                println("*********** COMPILER AST RIGHT BEFORE ASM GENERATION *************")
//                printProgram(program)
//                println("*********** AST RIGHT BEFORE ASM GENERATION *************")
//                printAst(intermediateAst, true, ::println)

                if(!createAssemblyAndAssemble(intermediateAst, args.errors, compilationOptions)) {
                    System.err.println("Error in codegeneration or assembler")
                    return null
                }
                ast = intermediateAst
            }
        }

        System.out.flush()
        System.err.flush()
        val seconds = totalTime/1000.0
        println("\nTotal compilation+assemble time: ${round(seconds*100.0)/100.0} sec.")
        return CompilationResult(program, ast, compilationOptions, importedFiles)
    } catch (px: ParseError) {
        System.err.print("\n\u001b[91m")  // bright red
        System.err.println("${px.position.toClickableStr()} parse error: ${px.message}".trim())
        System.err.print("\u001b[0m")  // reset
    } catch (ac: ErrorsReportedException) {
        if(!ac.message.isNullOrEmpty()) {
            System.err.print("\n\u001b[91m")  // bright red
            System.err.println(ac.message)
            System.err.print("\u001b[0m")  // reset
        }
    } catch (nsf: NoSuchFileException) {
        System.err.print("\n\u001b[91m")  // bright red
        System.err.println("File not found: ${nsf.message}")
        System.err.print("\u001b[0m")  // reset
    } catch (ax: AstException) {
        System.err.print("\n\u001b[91m")  // bright red
        System.err.println(ax.toString())
        System.err.print("\u001b[0m")  // reset
    } catch (x: Exception) {
        print("\n\u001b[91m")  // bright red
        println("\n* internal error *")
        print("\u001b[0m")  // reset
        System.out.flush()
        throw x
    } catch (x: NotImplementedError) {
        print("\n\u001b[91m")  // bright red
        println("\n* internal error: missing feature/code *")
        print("\u001b[0m")  // reset
        System.out.flush()
        throw x
    }

    return null
}


internal fun determineProgramLoadAddress(program: Program, options: CompilationOptions, errors: IErrorReporter) {
    val specifiedAddress = program.toplevelModule.loadAddress
    var loadAddress: UInt? = null
    if(specifiedAddress!=null) {
        loadAddress = specifiedAddress.first
    }
    else {
        when(options.output) {
            OutputType.RAW -> { /* no predefined load address */ }
            OutputType.PRG -> {
                if(options.launcher==CbmPrgLauncherType.BASIC) {
                    loadAddress = options.compTarget.machine.PROGRAM_LOAD_ADDRESS
                }
            }
            OutputType.XEX -> {
                if(options.launcher!=CbmPrgLauncherType.NONE)
                    throw AssemblyError("atari xex output can't contain BASIC launcher")
                loadAddress = options.compTarget.machine.PROGRAM_LOAD_ADDRESS
            }
        }
    }

    if(options.output==OutputType.PRG && options.launcher==CbmPrgLauncherType.BASIC) {
        val expected = options.compTarget.machine.PROGRAM_LOAD_ADDRESS
        if(loadAddress!=expected) {
            errors.err("BASIC output must have load address ${expected.toHex()}", specifiedAddress?.second ?: program.toplevelModule.position)
        }
    }

    if(loadAddress==null) {
        errors.err("load address must be specified with these output options", program.toplevelModule.position)
        return
    }

    options.loadAddress = loadAddress
}


private class BuiltinFunctionsFacade(functions: Map<String, FSignature>): IBuiltinFunctions {
    lateinit var program: Program

    override val names = functions.keys
    override val purefunctionNames = functions.filter { it.value.pure }.map { it.key }.toSet()

    override fun constValue(funcName: String, args: List<Expression>, position: Position): NumericLiteral? {
        val func = BuiltinFunctions[funcName]
        if(func!=null) {
            val exprfunc = constEvaluatorsForBuiltinFuncs[funcName]
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
        }
        return null
    }
    override fun returnType(funcName: String) = builtinFunctionReturnType(funcName)
}

fun parseMainModule(filepath: Path,
                    errors: IErrorReporter,
                    compTarget: ICompilationTarget,
                    sourceDirs: List<String>): Triple<Program, CompilationOptions, List<Path>> {
    val bf = BuiltinFunctionsFacade(BuiltinFunctions)
    val program = Program(filepath.nameWithoutExtension, bf, compTarget, compTarget)
    bf.program = program

    val importer = ModuleImporter(program, compTarget.name, errors, sourceDirs)
    val importedModuleResult = importer.importMainModule(filepath)
    importedModuleResult.onFailure { throw it }
    errors.report()

    val importedFiles = program.modules.map { it.source }
        .filter { it.isFromFilesystem }
        .map { Path(it.origin) }
    val compilerOptions = determineCompilationOptions(program, compTarget)
    // depending on the machine and compiler options we may have to include some libraries
    for(lib in compTarget.machine.importLibs(compilerOptions, compTarget.name))
        importer.importImplicitLibraryModule(lib)

    // always import prog8_lib and math
    importer.importImplicitLibraryModule("math")
    importer.importImplicitLibraryModule("prog8_lib")

    if (compilerOptions.launcher == CbmPrgLauncherType.BASIC && compilerOptions.output != OutputType.PRG)
        errors.err("BASIC launcher requires output type PRG", program.toplevelModule.position)
    if(compilerOptions.launcher == CbmPrgLauncherType.BASIC && compTarget.name== AtariTarget.NAME)
        errors.err("atari target cannot use CBM BASIC launcher, use NONE", program.toplevelModule.position)

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
    val zpType: ZeropageType =
        if (zpoption == null)
            if (floatsEnabled) ZeropageType.FLOATSAFE else ZeropageType.KERNALSAFE
        else
            try {
                ZeropageType.valueOf(zpoption)
            } catch (x: IllegalArgumentException) {
                ZeropageType.KERNALSAFE
                // error will be printed by the astchecker
            }

    val zpReserved = toplevelModule.statements
        .asSequence()
        .filter { it is Directive && it.directive == "%zpreserved" }
        .map { (it as Directive).args }
        .filter { it.size==2 && it[0].int!=null && it[1].int!=null }
        .map { it[0].int!!..it[1].int!! }
        .toList()

    val outputType = if (outputTypeStr == null) {
        if(compTarget is AtariTarget)
            OutputType.XEX
        else
            OutputType.PRG
    } else {
        try {
            OutputType.valueOf(outputTypeStr)
        } catch (x: IllegalArgumentException) {
            // set default value; actual check and error handling of invalid option is handled in the AstChecker later
            OutputType.PRG
        }
    }
    val launcherType = if (launcherTypeStr == null) {
        when(compTarget) {
            is AtariTarget -> CbmPrgLauncherType.NONE
            else -> CbmPrgLauncherType.BASIC
        }
    } else {
        try {
            CbmPrgLauncherType.valueOf(launcherTypeStr)
        } catch (x: IllegalArgumentException) {
            // set default value; actual check and error handling of invalid option is handled in the AstChecker later
            CbmPrgLauncherType.BASIC
        }
    }

    return CompilationOptions(
        outputType, launcherType,
        zpType, zpReserved, floatsEnabled, noSysInit,
        compTarget, 0u
    )
}

private fun processAst(program: Program, errors: IErrorReporter, compilerOptions: CompilationOptions) {
    program.preprocessAst(errors, compilerOptions)
    program.checkIdentifiers(errors, compilerOptions)
    errors.report()
    program.charLiteralsToUByteLiterals(compilerOptions.compTarget, errors)
    errors.report()
    program.constantFold(errors, compilerOptions.compTarget)
    errors.report()
    program.desugaring(errors)
    errors.report()
    program.reorderStatements(errors, compilerOptions)
    errors.report()
    program.changeNotExpressionAndIfComparisonExpr(errors, compilerOptions.compTarget)
    errors.report()
    program.addTypecasts(errors, compilerOptions)
    errors.report()
    program.variousCleanups(errors, compilerOptions)
    errors.report()
    program.checkValid(errors, compilerOptions)
    errors.report()
    program.checkIdentifiers(errors, compilerOptions)
    errors.report()
}

private fun optimizeAst(program: Program, compilerOptions: CompilationOptions, errors: IErrorReporter, functions: IBuiltinFunctions, compTarget: ICompilationTarget) {
    val remover = UnusedCodeRemover(program, errors, compTarget)
    remover.visit(program)
    remover.applyModifications()
    while (true) {
        // keep optimizing expressions and statements until no more steps remain
        val optsDone1 = program.simplifyExpressions(errors, compTarget)
        val optsDone2 = program.splitBinaryExpressions(compilerOptions)
        val optsDone3 = program.optimizeStatements(errors, functions, compTarget)
        val optsDone4 = program.inlineSubroutines()
        program.constantFold(errors, compTarget) // because simplified statements and expressions can result in more constants that can be folded away
        errors.report()
        if (optsDone1 + optsDone2 + optsDone3 + optsDone4 == 0)
            break
    }
    val remover2 = UnusedCodeRemover(program, errors, compTarget)
    remover2.visit(program)
    remover2.applyModifications()
    errors.report()
}

private fun postprocessAst(program: Program, errors: IErrorReporter, compilerOptions: CompilationOptions) {
    program.desugaring(errors)
    program.addTypecasts(errors, compilerOptions)
    errors.report()
    program.variousCleanups(errors, compilerOptions)
    val callGraph = CallGraph(program)
    callGraph.checkRecursiveCalls(errors)
    program.verifyFunctionArgTypes(errors)
    errors.report()
    program.moveMainBlockAsFirst()
    program.checkValid(errors, compilerOptions)          // check if final tree is still valid
    errors.report()
}

private fun createAssemblyAndAssemble(program: PtProgram,
                                      errors: IErrorReporter,
                                      compilerOptions: CompilationOptions
): Boolean {

    val asmgen = if(compilerOptions.experimentalCodegen)
        prog8.codegen.experimental.ExperiCodeGen()
    else if (compilerOptions.compTarget.machine.cpu in arrayOf(CpuType.CPU6502, CpuType.CPU65c02))
        prog8.codegen.cpu6502.AsmGen6502()
    else if (compilerOptions.compTarget.name == VMTarget.NAME)
        VmCodeGen()
    else
        throw NotImplementedError("no code generator for cpu ${compilerOptions.compTarget.machine.cpu}")

    val stMaker = SymbolTableMaker(program, compilerOptions)
    val symbolTable = stMaker.make()
    val assembly = asmgen.generate(program, symbolTable, compilerOptions, errors)
    errors.report()

    return if(assembly!=null && errors.noErrors()) {
        assembly.assemble(compilerOptions, errors)
    } else {
        false
    }
}
