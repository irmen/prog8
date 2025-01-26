package prog8.compiler

import com.github.michaelbull.result.onFailure
import prog8.ast.*
import prog8.ast.expressions.Expression
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Directive
import prog8.code.SymbolTableMaker
import prog8.code.ast.PtProgram
import prog8.code.ast.printAst
import prog8.code.ast.verifyFinalAstBeforeAsmGen
import prog8.code.core.*
import prog8.code.optimize.optimizeSimplifiedAst
import prog8.code.source.ImportFileSystem.expandTilde
import prog8.code.target.*
import prog8.codegen.vm.VmCodeGen
import prog8.compiler.astprocessing.*
import prog8.optimizer.*
import prog8.parser.ParseError
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.math.round
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis


class CompilationResult(val compilerAst: Program,   // deprecated, use codegenAst instead
                        val codegenAst: PtProgram?,
                        val compilationOptions: CompilationOptions,
                        val importedFiles: List<Path>)

class CompilerArguments(val filepath: Path,
                        val optimize: Boolean,
                        val writeAssembly: Boolean,
                        val warnSymbolShadowing: Boolean,
                        val quietAssembler: Boolean,
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
                        val dontSplitWordArrays: Boolean,
                        val breakpointCpuInstruction: String?,
                        val printAst1: Boolean,
                        val printAst2: Boolean,
                        val ignoreFootguns: Boolean,
                        val symbolDefs: Map<String, String>,
                        val sourceDirs: List<String> = emptyList(),
                        val outputDir: Path = Path(""),
                        val errors: IErrorReporter = ErrorReporter(ErrorReporter.AnsiColors))


fun compileProgram(args: CompilerArguments): CompilationResult? {

    var compilationOptions: CompilationOptions
    var ast: PtProgram? = null
    var resultingProgram: Program? = null
    var importedFiles: List<Path>

    val targetConfigFile = expandTilde(Path(args.compilationTarget))
    val compTarget = if(targetConfigFile.isRegularFile()) {
        ConfigFileTarget.fromConfigFile(targetConfigFile)
    } else {
        getCompilationTargetByName(args.compilationTarget)
    }

    try {
        val totalTime = measureTimeMillis {
            val sourceDirs = if(compTarget.libraryPath!=null) listOf(compTarget.libraryPath.toString()) + args.sourceDirs else args.sourceDirs
            val (program, options, imported) = parseMainModule(args.filepath, args.errors, compTarget, sourceDirs)
            compilationOptions = options

            with(compilationOptions) {
                warnSymbolShadowing = args.warnSymbolShadowing
                optimize = args.optimize
                asmQuiet = args.quietAssembler
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
                dontSplitWordArrays = args.dontSplitWordArrays
                outputDir = args.outputDir.normalize()
                symbolDefs = args.symbolDefs
            }
            resultingProgram = program
            importedFiles = imported

            processAst(program, args.errors, compilationOptions)
//            println("*********** COMPILER AST RIGHT BEFORE OPTIMIZING *************")
//            printProgram(program)

            if (compilationOptions.optimize) {
                optimizeAst(
                    program,
                    compilationOptions,
                    args.errors,
                    BuiltinFunctionsFacade(BuiltinFunctions),
                )
            }

            determineProgramLoadAddress(program, compilationOptions, args.errors)
            args.errors.report()
            postprocessAst(program, args.errors, compilationOptions)
            args.errors.report()

//            println("*********** COMPILER AST BEFORE ASSEMBLYGEN *************")
//            printProgram(program)

            if (args.writeAssembly) {

                // re-initialize memory areas with final compilationOptions
                compilationOptions.compTarget.initializeMemoryAreas(compilationOptions)
                program.processAstBeforeAsmGeneration(compilationOptions, args.errors)
                args.errors.report()

                if(args.printAst1) {
                    println("\n*********** COMPILER AST *************")
                    printProgram(program)
                    println("*********** COMPILER AST END *************\n")
                }

                val intermediateAst = SimplifiedAstMaker(program, args.errors).transform()
                val stMaker = SymbolTableMaker(intermediateAst, compilationOptions)
                val symbolTable = stMaker.make()

                postprocessSimplifiedAst(intermediateAst, symbolTable, args.errors)
                args.errors.report()

                if(compilationOptions.optimize) {
                    optimizeSimplifiedAst(intermediateAst, compilationOptions, symbolTable, args.errors)
                    args.errors.report()
                }

                if(args.printAst2) {
                    println("\n*********** SIMPLIFIED AST *************")
                    printAst(intermediateAst, true, ::println)
                    println("*********** SIMPLIFIED AST END *************\n")
                }

                verifyFinalAstBeforeAsmGen(intermediateAst, compilationOptions, symbolTable, args.errors)
                args.errors.report()

                if(!createAssemblyAndAssemble(intermediateAst, args.errors, compilationOptions, program.generatedLabelSequenceNumber)) {
                    System.err.println("Error in codegeneration or assembler")
                    return null
                }
                ast = intermediateAst
            } else {
                if(args.printAst1) {
                    println("\n*********** COMPILER AST *************")
                    printProgram(program)
                    println("*********** COMPILER AST END *************\n")
                }
                if(args.printAst2) {
                    System.err.println("There is no simplified Ast available if assembly generation is disabled.")
                }
            }
        }

        System.out.flush()
        System.err.flush()
        val seconds = totalTime/1000.0
        println("\nTotal compilation+assemble time: ${round(seconds*100.0)/100.0} sec.")
        return CompilationResult(resultingProgram!!, ast, compilationOptions, importedFiles)
    } catch (px: ParseError) {
        System.out.flush()
        System.err.print("\n\u001b[91m")  // bright red
        System.err.println("${px.position.toClickableStr()} parse error: ${px.message}".trim())
        System.err.print("\u001b[0m")  // reset
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
            System.out.flush()
            System.err.print("\n\u001b[91m")  // bright red
            System.err.println(ac.message)
            System.err.print("\u001b[0m")  // reset
        }
    } catch (nsf: NoSuchFileException) {
        System.out.flush()
        System.err.print("\n\u001b[91m")  // bright red
        System.err.println("File not found: ${nsf.message}")
        System.err.print("\u001b[0m")  // reset
    } catch (ax: AstException) {
        System.out.flush()
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
            OutputType.RAW -> {
                if(options.compTarget.name==Neo6502Target.NAME)
                    loadAddress = options.compTarget.PROGRAM_LOAD_ADDRESS
                // for all other targets, RAW has no predefined load address.
            }
            OutputType.PRG -> {
                if(options.launcher==CbmPrgLauncherType.BASIC) {
                    loadAddress = options.compTarget.PROGRAM_LOAD_ADDRESS
                }
            }
            OutputType.XEX -> {
                if(options.launcher!=CbmPrgLauncherType.NONE)
                    throw AssemblyError("atari xex output can't contain BASIC launcher")
                loadAddress = options.compTarget.PROGRAM_LOAD_ADDRESS
            }
            OutputType.LIBRARY -> {
                if(options.launcher!=CbmPrgLauncherType.NONE)
                    throw AssemblyError("library output can't contain BASIC launcher")
                if(options.zeropage!=ZeropageType.DONTUSE)
                    throw AssemblyError("library output can't use zeropage")
                if(options.noSysInit==false)
                    throw AssemblyError("library output can't have sysinit")
                // LIBRARY has no predefined load address.
            }
        }
    }

    if(options.output==OutputType.PRG && options.launcher==CbmPrgLauncherType.BASIC) {
        val expected = options.compTarget.PROGRAM_LOAD_ADDRESS
        if(loadAddress!=expected) {
            errors.err("BASIC output must have load address ${expected.toHex()}", specifiedAddress?.second ?: program.toplevelModule.position)
        }
    }

    if(loadAddress==null) {
        errors.err("load address must be specified for the selected output/launcher options", program.toplevelModule.position)
        return
    }

    options.loadAddress = loadAddress

    options.memtopAddress = program.toplevelModule.memtopAddress?.first ?: options.compTarget.PROGRAM_MEMTOP_ADDRESS

    if(loadAddress>options.memtopAddress) {
        errors.warn("program load address ${loadAddress.toHex()} is beyond default memtop address ${options.memtopAddress.toHex()}. " +
                "Memtop has been adjusted to ${'$'}ffff to avoid assembler error. Set a valid %memtop yourself to get rid of this warning.", program.toplevelModule.position)
        options.memtopAddress = 0xffffu
    }
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
        if(compilerOptions.noSysInit == false)
            errors.err("library cannot use sysinit", program.toplevelModule.position)
    } else {
        if (compilerOptions.launcher == CbmPrgLauncherType.BASIC && compilerOptions.output != OutputType.PRG)
            errors.err("BASIC launcher requires output type PRG", program.toplevelModule.position)
        if (compilerOptions.launcher == CbmPrgLauncherType.BASIC && compTarget.name == AtariTarget.NAME)
            errors.err("atari target cannot use CBM BASIC launcher, use NONE", program.toplevelModule.position)
    }

    errors.report()

    return Triple(program, compilerOptions, importedFiles)
}

internal fun determineCompilationOptions(program: Program, compTarget: ICompilationTarget): CompilationOptions {
    val toplevelModule = program.toplevelModule
    val outputDirective = (toplevelModule.statements.singleOrNull { it is Directive && it.directive == "%output" } as? Directive)
    val launcherDirective = (toplevelModule.statements.singleOrNull { it is Directive && it.directive == "%launcher" } as? Directive)
    val outputTypeStr = outputDirective?.args?.single()?.name?.uppercase()
    val launcherTypeStr = launcherDirective?.args?.single()?.name?.uppercase()
    val zpoption: String? = (toplevelModule.statements.singleOrNull { it is Directive && it.directive == "%zeropage" }
            as? Directive)?.args?.single()?.name?.uppercase()
    val allOptions = program.modules.flatMap { it.options() }.toSet()
    val floatsEnabled = "enable_floats" in allOptions
    var noSysInit = "no_sysinit" in allOptions
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
        if(compTarget is AtariTarget)
            OutputType.XEX
        else
            OutputType.PRG
    } else {
        try {
            OutputType.valueOf(outputTypeStr)
        } catch (_: IllegalArgumentException) {
            // set default value; actual check and error handling of invalid option is handled in the AstChecker later
            OutputType.PRG
        }
    }
    var launcherType = if (launcherTypeStr == null) {
        when(compTarget) {
            is AtariTarget -> CbmPrgLauncherType.NONE
            else -> CbmPrgLauncherType.BASIC
        }
    } else {
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
        zpType, zpReserved, zpAllowed, floatsEnabled, noSysInit,
        compTarget, 0u, 0xffffu
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
    program.reorderStatements(errors)
    errors.report()
    program.desugaring(errors)
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
        val remover = UnusedCodeRemover(program, errors, compilerOptions.compTarget)
        remover.visit(program)
        while (errors.noErrors() && remover.applyModifications() > 0) {
            remover.visit(program)
        }
    }

    removeUnusedCode(program, errors,compilerOptions)
    for(numCycles in 0..10000) {
        // keep optimizing expressions and statements until no more steps remain
        val optsDone1 = program.simplifyExpressions(errors)
        val optsDone2 = program.optimizeStatements(errors, functions, compilerOptions)
        val optsDone3 = program.inlineSubroutines(compilerOptions)
        program.constantFold(errors, compilerOptions) // because simplified statements and expressions can result in more constants that can be folded away
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
        // last round of optimizations because constFold may have enabled more...
        program.simplifyExpressions(errors)
        program.optimizeStatements(errors, functions, compilerOptions)
        program.constantFold(errors, compilerOptions) // because simplified statements and expressions can result in more constants that can be folded away
    }

    if(errors.noErrors()) {
        // certain optimization steps could have introduced a "not" in an if statement, postprocess those again.
        var changer = NotExpressionAndIfComparisonExprChanger(program, errors, compilerOptions.compTarget)
        changer.visit(program)
        if(errors.noErrors())
            changer.applyModifications()
    }

    errors.report()
}

private fun postprocessAst(program: Program, errors: IErrorReporter, compilerOptions: CompilationOptions) {
    program.desugaring(errors)
    program.addTypecasts(errors, compilerOptions)
    errors.report()
    program.variousCleanups(errors, compilerOptions)
    val callGraph = CallGraph(program)
    callGraph.checkRecursiveCalls(errors)
    program.verifyFunctionArgTypes(errors, compilerOptions)
    errors.report()
    program.moveMainBlockAsFirst(compilerOptions.compTarget)
    program.checkValid(errors, compilerOptions)          // check if final tree is still valid
    errors.report()
}

private fun createAssemblyAndAssemble(program: PtProgram,
                                      errors: IErrorReporter,
                                      compilerOptions: CompilationOptions,
                                      lastGeneratedLabelSequenceNr: Int
): Boolean {

    val asmgen = if(compilerOptions.experimentalCodegen)
        prog8.codegen.experimental.ExperiCodeGen()
    else if (compilerOptions.compTarget.cpu in arrayOf(CpuType.CPU6502, CpuType.CPU65C02))
        prog8.codegen.cpu6502.AsmGen6502(prefixSymbols = true, lastGeneratedLabelSequenceNr+1)
    else if (compilerOptions.compTarget.name == VMTarget.NAME)
        VmCodeGen()
    else
        throw NotImplementedError("no code generator for cpu ${compilerOptions.compTarget.cpu}")

    // need to make a new symboltable here to capture possible changes made by optimization steps performed earlier!
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
