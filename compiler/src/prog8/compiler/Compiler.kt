package prog8.compiler

import com.github.michaelbull.result.onErr
import prog8.ast.*
import prog8.ast.expressions.Expression
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Directive
import prog8.buildversion.VERSION
import prog8.code.SymbolTable
import prog8.code.SymbolTableMaker
import prog8.code.ast.PtProgram
import prog8.code.ast.printAst
import prog8.code.ast.verifyFinalAstBeforeAsmGen
import prog8.code.core.*
import prog8.code.optimize.optimizeSimplifiedAst
import prog8.code.source.ImportFileSystem.expandTilde
import prog8.code.target.ConfigFileTarget
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8.code.target.getCompilationTargetByName
import prog8.codegen.vm.VmCodeGen
import prog8.compiler.astprocessing.*
import prog8.compiler.simpleastprocessing.profilingInstrumentation
import prog8.optimizer.*
import prog8.parser.ParseError
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.measureTimedValue


private data class AssemblyResult(val success: Boolean, val irInstructionCount: Int, val irChunkCount: Int, val irRegisterCount: Int)


class CompilationResult(val compilerAst: Program,   // deprecated, use codegenAst instead
                        val codegenAst: PtProgram?,
                        val codegenSymboltable: SymbolTable?,
                        val compilationOptions: CompilationOptions,
                        val importedFiles: List<Path>,
                        val irInstructionCount: Int = 0,
                        val irRegisterCount: Int = 0)

data class CompilerError(
    val message: String,
    val file: String? = null,
    val line: Int? = null,
    val column: Int? = null
)

data class CompilerWarning(
    val message: String,
    val file: String? = null,
    val line: Int? = null,
    val column: Int? = null
)

data class CompilationAndRunResult(
    val success: Boolean,
    val irProgram: prog8.intermediate.IRProgram? = null,
    val errors: List<CompilerError> = emptyList(),
    val warnings: List<CompilerWarning> = emptyList(),
    val executionOutput: String? = null,
    val executionSteps: Int = 0,
    val exitCode: Int = 0,
    val fatalException: String? = null
)

class CompilerArguments(val filepath: Path,
                        val optimize: Boolean,
                        val writeAssembly: Boolean,
                        val warnSymbolShadowing: Boolean,
                        val warnImplicitTypeCasts: Boolean,
                        val quietAll: Boolean,
                        val quietAssembler: Boolean,
                        val showTimings: Boolean,
                        val asmListfile: Boolean,
                        val includeSourcelines: Boolean,
                        val experimentalCodegen: Boolean,
                        val dumpVariables: Boolean,
                        val dumpSymbols: Boolean,
                        val varsHighBank: Int?,
                        val varsGolden: Boolean,
                        val slabsHighBank: Int?,
                        val slabsGolden: Boolean,
                        val compilationTarget: String,
                        val breakpointCpuInstruction: String?,
                        val printAst1: Boolean,
                        val printAst2: Boolean,
                        val ignoreFootguns: Boolean,
                        val profilingInstrumentation: Boolean,
                        val nostdlib: Boolean,
                        val symbolDefs: Map<String, String>,
                        val sourceDirs: List<String> = emptyList(),
                        val outputDir: Path = Path(""),
                        val errors: IErrorReporter = ErrorReporter(ErrorReporter.AnsiColors))


fun compileProgram(args: CompilerArguments): CompilationResult? {

    var compilationOptions: CompilationOptions
    var ast: PtProgram? = null
    var resultingProgram: Program? = null
    var importedFiles: List<Path>
    var irInstructionCount = 0
    var irRegisterCount = 0

    val targetConfigFile = expandTilde(Path(args.compilationTarget))
    val compTarget = if(targetConfigFile.isRegularFile()) {
        ConfigFileTarget.fromConfigFile(targetConfigFile)
    } else {
        getCompilationTargetByName(args.compilationTarget)
    }

    if(args.varsGolden || args.slabsGolden) {
        if(compTarget.BSSGOLDENRAM_END-compTarget.BSSGOLDENRAM_START==0u) {
            System.err.println("The current compilation target doesn't support Golden Ram.")
            return null
        }
    }

    try {
        var symbolTable: SymbolTable? = null

        val totalTime = measureTime {
            val libraryDirs =  if(compTarget.libraryPath!=null) listOf(compTarget.libraryPath.toString()) else emptyList()
            val (parseresult, parseDuration) = measureTimedValue {
                 parseMainModule(
                    args.filepath,
                    args.errors,
                    compTarget,
                    args.sourceDirs,
                    libraryDirs,
                    args.quietAll,
                    args.nostdlib
                )
            }

            val (program, options, imported) = parseresult
            compilationOptions = options

            with(compilationOptions) {
                warnSymbolShadowing = args.warnSymbolShadowing
                warnImplicitTypeCast = args.warnImplicitTypeCasts
                optimize = args.optimize
                asmQuiet = args.quietAssembler
                quiet = args.quietAll
                profilingInstrumentation = args.profilingInstrumentation
                asmListfile = args.asmListfile
                includeSourcelines = args.includeSourcelines
                experimentalCodegen = args.experimentalCodegen
                dumpVariables = args.dumpVariables
                dumpSymbols = args.dumpSymbols
                breakpointCpuInstruction = args.breakpointCpuInstruction
                ignoreFootguns = args.ignoreFootguns
                varsHighBank = args.varsHighBank
                varsGolden = args.varsGolden
                slabsHighBank = args.slabsHighBank
                slabsGolden = args.slabsGolden
                outputDir = args.outputDir.normalize()
                symbolDefs = args.symbolDefs
            }
            resultingProgram = program
            importedFiles = imported

            if(compilationOptions.romable) {
                if (!compilationOptions.varsGolden && compilationOptions.varsHighBank==null)
                    args.errors.err("When ROMable code is selected, variables should be moved to a RAM memory region using either -varsgolden or -varshigh option", program.toplevelModule.position)
                if (!compilationOptions.slabsGolden && compilationOptions.slabsHighBank==null)
                    args.errors.err("When ROMable code is selected, memory() blocks should be moved to a RAM memory region using either -slabsgolden or -slabshigh option", program.toplevelModule.position)
                args.errors.report()
            }


            val processDuration = measureTime {
                processAst(program, args.errors, compilationOptions)
            }

//            println("*********** COMPILER AST RIGHT BEFORE OPTIMIZING *************")
//            printProgram(program)

            val optimizeDuration = measureTime {
                if (compilationOptions.optimize) {
                    optimizeAst(
                        program,
                        compilationOptions,
                        args.errors,
                        BuiltinFunctionsFacade(BuiltinFunctions),
                    )
                }
            }

            val postprocessDuration = measureTime {
                determineProgramLoadAddress(program, compilationOptions, args.errors)
                args.errors.report()
                postprocessAst(program, args.errors, compilationOptions)
                args.errors.report()
            }

//            println("*********** COMPILER AST BEFORE ASSEMBLYGEN *************")
//            printProgram(program)

            var createAssemblyDuration = Duration.ZERO
            var simplifiedAstDuration = Duration.ZERO

            if (args.writeAssembly) {

                // re-initialize memory areas with final compilationOptions
                compilationOptions.compTarget.initializeMemoryAreas(compilationOptions)

                if (args.printAst1) {
                    println("\n*********** COMPILER AST *************")
                    printProgram(program)
                    println("*********** COMPILER AST END *************\n")
                }

                val (intermediateAst, simplifiedAstDuration2) = measureTimedValue {
                    val intermediateAst = SimplifiedAstMaker(program, args.errors).transform()
                    val stMaker = SymbolTableMaker(intermediateAst, compilationOptions)
                    symbolTable = stMaker.make()

                    postprocessSimplifiedAst(intermediateAst, symbolTable!!, compilationOptions, args.errors)
                    args.errors.report()
                    symbolTable = stMaker.make()        // need an updated ST because the postprocessing changes stuff

                    /*
                     * IMPORTANT: Optimization order matters!
                     * 
                     * 1. optimizeSimplifiedAst() - Runs the main optimization passes (algebraic identities,
                     *    boolean simplifications, comparison optimizations, etc.). These optimizations
                     *    need to see the original AST patterns including typecasts to create optimization
                     *    opportunities (e.g., pointer arithmetic patterns like `ptr + (value as uword)`).
                     * 
                     * 2. removeRedundantPointerCasts() - Removes redundant (pointer as uword) typecasts.
                     *    This MUST run AFTER optimizeSimplifiedAst() because:
                     *    - The optimizer needs to see typecast patterns to match optimization rules
                     *    - Removing typecasts too early prevents pattern matching in the optimizer
                     *    - But typecasts must be removed before code generation to produce efficient code
                     *    
                     *    This step runs regardless of the -noopt flag because redundant pointer typecasts
                     *    would otherwise generate inefficient assembly code (extra loads/stores to temp vars).
                     */

                    if (compilationOptions.optimize) {
                        optimizeSimplifiedAst(intermediateAst, compilationOptions, symbolTable!!, args.errors)
                        args.errors.report()
                        // symbolTable = stMaker.make()        // need an updated ST because the optimization changes stuff
                    }

                    // Remove redundant pointer typecasts - must run AFTER optimization, BEFORE code generation
                    SubtypeResolver.removeRedundantPointerCasts(intermediateAst)
                    args.errors.report()
                    symbolTable = stMaker.make()        // need an updated ST because the typecast removal changes stuff

                    if (compilationOptions.profilingInstrumentation) {
                        require(compilationOptions.compTarget.name == Cx16Target.NAME)
                        profilingInstrumentation(intermediateAst, symbolTable, args.errors)
                        args.errors.report()
                    }

                    if (args.printAst2) {
                        println("\n*********** SIMPLIFIED AST *************")
                        printAst(intermediateAst, true, ::println)
                        println("*********** SIMPLIFIED AST END *************\n")
                    }

                    verifyFinalAstBeforeAsmGen(intermediateAst, compilationOptions, symbolTable, args.errors)
                    args.errors.report()
                    intermediateAst
                }
                simplifiedAstDuration = simplifiedAstDuration2

                createAssemblyDuration = measureTime {
                    val result = createAssemblyAndAssemble(
                            intermediateAst,
                            symbolTable!!,
                            args.errors,
                            compilationOptions,
                            program.generatedLabelSequenceNumber
                        )
                    irInstructionCount = result.irInstructionCount
                    irRegisterCount = result.irRegisterCount
                    if (!result.success) {
                        System.err.println("Error in codegeneration or assembler")
                    }
                }
                if (irInstructionCount < 0) {
                    return null
                }
                ast = intermediateAst
            } else {
                if (args.printAst1) {
                    println("\n*********** COMPILER AST *************")
                    printProgram(program)
                    println("*********** COMPILER AST END *************\n")
                }
                if (args.printAst2) {
                    System.err.println("There is no simplified Ast available if assembly generation is disabled.")
                }
            }

            System.out.flush()
            System.err.flush()

            if(!args.quietAll && args.showTimings) {
                println("\n**** TIMINGS ****")
                println("source parsing   : ${parseDuration.toString(DurationUnit.SECONDS, 3)}")
                println("ast processing   : ${processDuration.toString(DurationUnit.SECONDS, 3)}")
                println("ast optimizing   : ${optimizeDuration.toString(DurationUnit.SECONDS, 3)}")
                println("ast postprocess  : ${postprocessDuration.toString(DurationUnit.SECONDS, 3)}")
                println("code prepare     : ${simplifiedAstDuration.toString(DurationUnit.SECONDS, 3)}")
                println("code generation  : ${createAssemblyDuration.toString(DurationUnit.SECONDS, 3)}")
                val totalDuration = parseDuration + processDuration + optimizeDuration + postprocessDuration + simplifiedAstDuration + createAssemblyDuration
                println("          total  : ${totalDuration.toString(DurationUnit.SECONDS, 3)}")
            }
        }

        if(!args.quietAll) {
            println("\nTotal compilation+assemble time: ${totalTime.toString(DurationUnit.SECONDS, 3)}.")
        }
        return CompilationResult(resultingProgram!!, ast, symbolTable, compilationOptions, importedFiles, irInstructionCount, irRegisterCount)
    } catch (px: ParseError) {
        args.errors.printSingleError("ERROR ${px.position.toClickableStr()} parse error: ${px.message}".trim())
    } catch (ac: ErrorsReportedException) {
        if(args.printAst1 && resultingProgram!=null) {
            println("\n*********** COMPILER AST *************")
            printProgram(resultingProgram)
            println("*********** COMPILER AST END *************\n")
        }
        if (args.printAst2) {
            if(ast==null)
                println("There is no simplified AST available because of compilation errors.")
            else {
                println("\n*********** SIMPLIFIED AST *************")
                printAst(ast, true, ::println)
                println("*********** SIMPLIFIED AST END *************\n")
            }
        }
        if(!ac.message.isNullOrEmpty()) {
            args.errors.printSingleError(ac.message!!)
        }
    } catch (nsf: NoSuchFileException) {
        args.errors.printSingleError("File not found: ${nsf.message}")
    } catch (ax: AstException) {
        args.errors.printSingleError(ax.toString())
    } catch(fx: FileSystemException) {
        if(fx.cause!=null) {
            args.errors.printSingleError("\nfile I/O error: ${fx.file}: ${fx.cause}")
        } else {
            args.errors.printSingleError("\nfile I/O error")
            throw fx
        }
    } catch (x: Exception) {
        args.errors.printSingleError("\ninternal error")
        throw x
    } catch (x: NotImplementedError) {
        args.errors.printSingleError("\ninternal error: missing feature/code")
        throw x
    }

    return null
}


internal fun determineProgramLoadAddress(program: Program, options: CompilationOptions, errors: IErrorReporter) {
    val specifiedAddress = program.toplevelModule.loadAddress
    val loadAddress = specifiedAddress?.first ?: options.compTarget.PROGRAM_LOAD_ADDRESS


    if(options.output==OutputType.PRG && options.launcher==CbmPrgLauncherType.BASIC && options.compTarget.customLauncher.isEmpty()) {
        val expected = options.compTarget.PROGRAM_LOAD_ADDRESS
        if(loadAddress!=expected) {
            errors.err("BASIC output must have load address ${expected.toHex()}", specifiedAddress?.second ?: program.toplevelModule.position)
        }
    }

    options.loadAddress = loadAddress

    options.memtopAddress = program.toplevelModule.memtopAddress?.first ?: options.compTarget.PROGRAM_MEMTOP_ADDRESS

    if(loadAddress>options.memtopAddress) {
        errors.warn("program load address ${loadAddress.toHex()} is beyond default memtop address ${options.memtopAddress.toHex()}. " +
                $$"Memtop has been adjusted to $ffff to avoid assembler error. Set a valid %memtop yourself to get rid of this warning.", program.toplevelModule.position)
        options.memtopAddress = 0xffffu
    }
}


private class BuiltinFunctionsFacade(functions: Map<String, FSignature>): IBuiltinFunctions {
    lateinit var program: Program

    override val names = functions.keys
    override val purefunctionNames = functions.filter { it.value.pure }.map { it.key }.toSet()

    override fun constValue(funcName: String, args: List<Expression>, position: Position): NumericLiteral? {
        if(funcName=="msw" && !args[0].inferType(program).isLong)
            return NumericLiteral.optimalInteger(0, position)

        val func = BuiltinFunctions[funcName]
        if(func!=null) {
            val exprfunc = constEvaluatorsForBuiltinFuncs[funcName]
            if(exprfunc!=null) {
                return try {
                    exprfunc(args, position, program)
                } catch(_: NotConstArgumentException) {
                    // const-evaluating the builtin function call failed.
                    null
                } catch(_: CannotEvaluateException) {
                    // const-evaluating the builtin function call failed.
                    null
                }
            }
        }
        return null
    }
    override fun returnTypes(funcName: String) = builtinFunctionReturnTypes(funcName)
}

fun parseMainModule(filepath: Path,
                    errors: IErrorReporter,
                    compTarget: ICompilationTarget,
                    sourceDirs: List<String>,
                    libraryDirs: List<String>,
                    quiet: Boolean,
                    nostdlib: Boolean): Triple<Program, CompilationOptions, List<Path>> {
    val bf = BuiltinFunctionsFacade(BuiltinFunctions)
    val program = Program(filepath.nameWithoutExtension, bf, compTarget, compTarget)
    bf.program = program

    val importer = ModuleImporter(program, compTarget.name, errors, sourceDirs, libraryDirs, quiet, nostdlib)
    val importedModuleResult = importer.importMainModule(filepath)
    importedModuleResult.onErr { throw it }
    errors.report()

    val importedFiles = program.modules.map { it.source }
        .filter { it.isFromFilesystem }
        .map { Path(it.origin) }
    val compilerOptions = determineCompilationOptions(program, compTarget)

    // import the default modules
    importer.importImplicitLibraryModule("syslib")
    importer.importImplicitLibraryModule("prog8_math")
    importer.importImplicitLibraryModule("prog8_lib")
    if(program.allBlocks.any { it.options().any { option->option=="verafxmuls" } }) {
        if(compTarget.name==Cx16Target.NAME)
            importer.importImplicitLibraryModule("verafx")
    }

    if(compilerOptions.output==OutputType.LIBRARY) {
        if(compilerOptions.launcher != CbmPrgLauncherType.NONE)
            errors.err("library must not use a launcher", program.toplevelModule.position)
        if(compilerOptions.zeropage != ZeropageType.DONTUSE)
            errors.err("library cannot use zeropage", program.toplevelModule.position)
        if(!compilerOptions.noSysInit)
            errors.err("library cannot use sysinit", program.toplevelModule.position)
    } else {
        if (compilerOptions.launcher == CbmPrgLauncherType.BASIC && compilerOptions.output != OutputType.PRG)
            errors.err("BASIC launcher requires output type PRG", program.toplevelModule.position)
    }

    if(compilerOptions.romable && compilerOptions.floats)
        errors.err("When ROMable code is selected, floating point support is not available", program.toplevelModule.position)

    errors.report()

    return Triple(program, compilerOptions, importedFiles)
}

internal fun determineCompilationOptions(program: Program, compTarget: ICompilationTarget): CompilationOptions {
    val toplevelModule = program.toplevelModule
    val outputDirective = (toplevelModule.statements.singleOrNull { it is Directive && it.directive == "%output" } as? Directive)
    val launcherDirective = (toplevelModule.statements.singleOrNull { it is Directive && it.directive == "%launcher" } as? Directive)
    val outputTypeStr = outputDirective?.args?.single()?.string?.uppercase()
    val launcherTypeStr = launcherDirective?.args?.single()?.string?.uppercase()
    val zpoption: String? = (toplevelModule.statements.singleOrNull { it is Directive && it.directive == "%zeropage" }
            as? Directive)?.args?.single()?.string?.uppercase()
    val allOptions = program.modules.flatMap { it.options() }.toSet()
    val floatsEnabled = "enable_floats" in allOptions
    var noSysInit = "no_sysinit" in allOptions
    val rombale = "romable" in allOptions
    var zpType: ZeropageType =
        if (zpoption == null)
            if (floatsEnabled) ZeropageType.FLOATSAFE else ZeropageType.KERNALSAFE
        else
            try {
                ZeropageType.valueOf(zpoption)
            } catch (_: IllegalArgumentException) {
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

    val zpAllowed = toplevelModule.statements
        .asSequence()
        .filter { it is Directive && it.directive == "%zpallowed" }
        .map { (it as Directive).args }
        .filter { it.size==2 && it[0].int!=null && it[1].int!=null }
        .map { it[0].int!!..it[1].int!! }
        .toList()

    val outputType = if (outputTypeStr == null) {
        compTarget.defaultOutputType
    } else {
        try {
            OutputType.valueOf(outputTypeStr)
        } catch (_: IllegalArgumentException) {
            // set default value; actual check and error handling of invalid option is handled in the AstChecker later
            compTarget.defaultOutputType
        }
    }
    var launcherType = if (launcherTypeStr == null)
        CbmPrgLauncherType.BASIC
    else {
        try {
            CbmPrgLauncherType.valueOf(launcherTypeStr)
        } catch (_: IllegalArgumentException) {
            // set default value; actual check and error handling of invalid option is handled in the AstChecker later
            CbmPrgLauncherType.BASIC
        }
    }

    if(outputType == OutputType.LIBRARY) {
        launcherType = CbmPrgLauncherType.NONE
        zpType = ZeropageType.DONTUSE
        noSysInit = true
    }

    return CompilationOptions(
        outputType, launcherType,
        zpType, zpReserved, zpAllowed, floatsEnabled, noSysInit, rombale,
        compTarget, VERSION, 0u, 0xffffu
    )
}

private fun processAst(program: Program, errors: IErrorReporter, compilerOptions: CompilationOptions) {
    program.preprocessAst(errors, compilerOptions)
    if(errors.noErrors() && compilerOptions.dumpSymbols) {
        printSymbols(program)
        exitProcess(0)
    }

    program.checkIdentifiers(errors, compilerOptions)
    errors.report()
    program.charLiteralsToUByteLiterals(compilerOptions.compTarget, errors)
    errors.report()
    program.constantFold(errors, compilerOptions)
    errors.report()
    program.reorderStatements(compilerOptions, errors)
    errors.report()
    program.desugaring(errors, compilerOptions)
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

private fun optimizeAst(program: Program, compilerOptions: CompilationOptions, errors: IErrorReporter, functions: IBuiltinFunctions) {
    fun removeUnusedCode(program: Program, errors: IErrorReporter, compilerOptions: CompilationOptions) {
        val remover = UnusedCodeRemover(program, errors, compilerOptions)
        remover.visit(program)
        while (errors.noErrors() && remover.applyModifications() > 0) {
            remover.visit(program)
        }
    }

    removeUnusedCode(program, errors,compilerOptions)
    program.constantFold(errors, compilerOptions)

    for(numCycles in 0..10000) {
        // keep optimizing expressions and statements until no more steps remain
        val optsDone1 = program.simplifyExpressions(errors)
        val optsDone2 = program.optimizeStatements(errors, functions, compilerOptions)
        program.constantFold(errors, compilerOptions) // because simplified statements and expressions can result in more constants that can be folded away
        val optsDone3 = program.inlineSubroutines(compilerOptions)  // inlining can expose new calls to inline
        if(!errors.noErrors()) {
            errors.report()
            break
        }
        val numOpts = optsDone1 + optsDone2 + optsDone3
        if (numOpts == 0)
            break

        if(numCycles==10000) {
            throw InternalCompilerException("optimizeAst() is looping endlessly, numOpts = $numOpts")
        }
    }
    
    removeUnusedCode(program, errors, compilerOptions)
    if(errors.noErrors()) {
        // last round of optimizations because inlining may have enabled more...
        program.simplifyExpressions(errors)
        program.optimizeStatements(errors, functions, compilerOptions)
        program.constantFold(errors, compilerOptions) // because simplified statements and expressions can result in more constants that can be folded away
    }

    if(errors.noErrors()) {
        // certain optimization steps could have introduced a "not" in an if statement, postprocess those again.
        val changer = NotExpressionAndIfComparisonExprChanger(program, errors, compilerOptions.compTarget)
        changer.visit(program)
        if(errors.noErrors())
            changer.applyModifications()
    }

    errors.report()
}

private fun postprocessAst(program: Program, errors: IErrorReporter, compilerOptions: CompilationOptions) {
    program.desugaring(errors, compilerOptions)
    program.addTypecasts(errors, compilerOptions)
    errors.report()
    program.variousCleanups(errors, compilerOptions)
    val callGraph = CallGraph(program)
    callGraph.checkRecursiveCalls(errors)
    program.verifyFunctionArgTypes(errors, compilerOptions)
    errors.report()
    program.moveMainBlockAsFirst(compilerOptions.compTarget)

    val fixer = BeforeAsmAstChanger(program, compilerOptions, errors)
    fixer.visit(program)
    while (errors.noErrors() && fixer.applyModifications() > 0) {
        fixer.visit(program)
    }

    program.checkValid(errors, compilerOptions)          // check if final tree is still valid
    errors.report()

    val cleaner = BeforeAsmTypecastCleaner(program, errors)
    cleaner.visit(program)
    while (errors.noErrors() && cleaner.applyModifications() > 0) {
        cleaner.visit(program)
    }
}

private fun createAssemblyAndAssemble(program: PtProgram,
                                      symbolTable: SymbolTable,
                                      errors: IErrorReporter,
                                      compilerOptions: CompilationOptions,
                                      lastGeneratedLabelSequenceNr: Int
): AssemblyResult {

    val retainSSAforIR = true

    val asmgen = if(compilerOptions.experimentalCodegen)
        prog8.codegen.experimental.ExperiCodeGen(retainSSAforIR)
    else if (compilerOptions.compTarget.cpu in arrayOf(CpuType.CPU6502, CpuType.CPU65C02))
        prog8.codegen.cpu6502.AsmGen6502(prefixSymbols = true, lastGeneratedLabelSequenceNr+1)
    else if (compilerOptions.compTarget.name == VMTarget.NAME)
        VmCodeGen(retainSSAforIR)
    else
        throw NotImplementedError("no code generator for cpu ${compilerOptions.compTarget.cpu}")

    val assembly = asmgen.generate(program, symbolTable, compilerOptions, errors)
    errors.report()

    val instructionCount = assembly?.irInstructionCount ?: 0
    val chunkCount = assembly?.irChunkCount ?: 0
    val registerCount = assembly?.irRegisterCount ?: 0

    val success = if(assembly!=null && errors.noErrors()) {
        assembly.assemble(compilerOptions, errors)
    } else {
        false
    }
    return AssemblyResult(success, instructionCount, chunkCount, registerCount)
}

@JvmOverloads
fun compileAndRun(
    source: String,
    target: String = "virtual",
    execute: Boolean = true,
    optimize: Boolean = false,
    extraImports: List<String> = listOf("math", "strings", "conv", "textio")
): CompilationAndRunResult {
    val errors = mutableListOf<CompilerError>()
    val warnings = mutableListOf<CompilerWarning>()
    var irProgram: prog8.intermediate.IRProgram? = null
    var executionOutput: String? = null
    var executionSteps = 0
    var exitCode = 0
    var fatalException: String? = null

    try {
        val wrappedSource = wrapSourceForRepl(source, extraImports)
        val tempFile = kotlin.io.path.createTempFile(
            kotlin.io.path.Path(System.getProperty("java.io.tmpdir")),
            "repl_", ".p8"
        ).toFile()
        tempFile.writeText(wrappedSource)

        val compArgs = CompilerArguments(
            filepath = tempFile.toPath(),
            optimize = optimize,
            writeAssembly = true,
            warnSymbolShadowing = false,
            warnImplicitTypeCasts = false,
            quietAll = true,
            quietAssembler = true,
            showTimings = false,
            asmListfile = false,
            includeSourcelines = false,
            experimentalCodegen = false,
            dumpVariables = false,
            dumpSymbols = false,
            varsHighBank = null,
            varsGolden = false,
            slabsHighBank = null,
            slabsGolden = false,
            compilationTarget = target,
            breakpointCpuInstruction = null,
            printAst1 = false,
            printAst2 = false,
            ignoreFootguns = true,
            profilingInstrumentation = false,
            nostdlib = false,
            symbolDefs = emptyMap(),
            outputDir = kotlin.io.path.Path(System.getProperty("java.io.tmpdir")),
            errors = object : IErrorReporter {
                override fun err(msg: String, position: prog8.code.core.Position) {
                    errors.add(CompilerError(msg, position.file, position.line, position.line))
                }
                override fun warn(msg: String, position: prog8.code.core.Position) {
                    warnings.add(CompilerWarning(msg, position.file, position.line, position.line))
                }
                override fun info(msg: String, position: prog8.code.core.Position) {}
                override fun undefined(symbol: List<String>, suggestImport: Boolean, position: prog8.code.core.Position) {
                    val msg = if (suggestImport && symbol.size > 1) {
                        "undefined symbol: ${symbol.joinToString(".")} (maybe you forgot to import a module that defines ${symbol.first()}?)"
                    } else {
                        "undefined symbol: ${symbol.joinToString(".")}"
                    }
                    errors.add(CompilerError(msg, position.file, position.line, position.line))
                }
                override fun noErrors(): Boolean = errors.isEmpty()
                override fun report() {}
                override fun noErrorForLine(position: prog8.code.core.Position): Boolean = true
                override fun printSingleError(errormessage: String) {}
            }
        )

        val result = compileProgram(compArgs)

        if (result == null || errors.isNotEmpty()) {
            return CompilationAndRunResult(
                success = false,
                errors = errors.toList(),
                warnings = warnings.toList()
            )
        }

        val irFile = kotlin.io.path.Path(System.getProperty("java.io.tmpdir"))
            .resolve(result.compilerAst.name + ".p8ir")
        
        if (!irFile.toFile().exists()) {
            return CompilationAndRunResult(
                success = false,
                errors = errors + CompilerError("IR file not generated"),
                warnings = warnings.toList()
            )
        }

        irProgram = prog8.intermediate.IRFileReader().read(irFile)

        if (execute && irProgram != null) {
            val oldOut = System.out
            val capturedOut = java.io.ByteArrayOutputStream()
            System.setOut(java.io.PrintStream(capturedOut))

            try {
                val vm = prog8.vm.VirtualMachine(irProgram)
                vm.run(true)
                executionSteps = vm.stepCount
                exitCode = 0
            } catch (e: Exception) {
                exitCode = -1
                executionOutput = "Error: ${e.message}"
            } finally {
                System.setOut(oldOut)
                executionOutput = capturedOut.toString()
            }
        }

        return CompilationAndRunResult(
            success = errors.isEmpty(),
            irProgram = irProgram,
            errors = errors.toList(),
            warnings = warnings.toList(),
            executionOutput = executionOutput,
            executionSteps = executionSteps,
            exitCode = exitCode
        )

    } catch (e: FatalAstException) {
        return CompilationAndRunResult(
            success = false,
            irProgram = irProgram,
            errors = errors + CompilerError("Internal error: ${e.message}"),
            warnings = warnings.toList(),
            fatalException = e.message
        )
    } catch (e: Exception) {
        return CompilationAndRunResult(
            success = false,
            irProgram = irProgram,
            errors = errors + CompilerError("Error: ${e.message}"),
            warnings = warnings.toList(),
            fatalException = e.message
        )
    }
}

private fun wrapSourceForRepl(
    userCode: String,
    extraImports: List<String>
): String {
    val trimmed = userCode.trim()
    val hasMain = trimmed.contains(Regex("main\\s*\\{"))
    val hasImports = trimmed.startsWith("%import")

    val imports = extraImports.joinToString("\n") { "%import $it" }

    return when {
        hasMain && !hasImports -> """
$imports

$trimmed
""".trimIndent()

        hasMain && hasImports -> """
$imports

$trimmed
""".trimIndent()

        !hasImports -> """
$imports

main {
    sub start() {
${userCode.lines().joinToString("\n") { "        $it" }}
    }
}
""".trimIndent()

        else -> trimmed
    }
}
