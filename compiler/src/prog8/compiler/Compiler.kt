package prog8.compiler

import com.github.michaelbull.result.onErr
import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.buildversion.VERSION
import prog8.code.SymbolTable
import prog8.code.SymbolTableMaker
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.optimize.optimizeSimplifiedAst
import prog8.code.source.ImportFileSystem.expandTilde
import prog8.code.target.*
import prog8.codegen.vm.VmCodeGen
import prog8.compiler.astprocessing.*
import prog8.compiler.simpleastprocessing.profilingInstrumentation
import prog8.optimizer.*
import prog8.parser.MultipleParseErrors
import prog8.parser.ParseError
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
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
                        val newCodegen: Boolean,
                        val dumpVariables: Boolean,
                        val dumpSymbols: Boolean,
                        val varsHighBank: Int?,
                        val varsGolden: Boolean,
                        val varsAddress: UInt? = null,
                        val compilationTarget: String,
                        val breakpointCpuInstruction: String?,
                        val printAst1: Boolean,
                        val printAst2: Boolean,
                        val printCompileInfo: Boolean = true,
                        val ignoreFootguns: Boolean,
                        val profilingInstrumentation: Boolean,
                        val traceImports: Boolean = false,
                        val symbolDefs: Map<String, String>,
                        val sourceDirs: List<String> = emptyList(),
                        val outputDir: Path = Path(""),
                        val outputFile: Path? = null,
                        val cwd: Path = Path("").absolute(),
                        val errors: IErrorReporter = ErrorReporter(ErrorReporter.AnsiColors),
                        val assemble: Boolean = true,
                        val generateDocumentation: Boolean = false)


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

    if(args.varsGolden) {
        if(compTarget.BSSGOLDENRAM_END-compTarget.BSSGOLDENRAM_START==0u) {
            System.err.println("The current compilation target doesn't support Golden Ram.")
            return null
        }
    }

    if(compTarget.cpu.is68k) {
        if(args.varsGolden || args.varsHighBank!=null || args.varsAddress!=null) {
            System.err.println("The -varsgolden/-varshigh/-varsaddress options are not available on the m68k target")
            return null
        }
    }

    try {
        var symbolTable: SymbolTable? = null

        val totalTime = measureTime {
            val libraryDirs = if(compTarget.libraryPath!=null) listOf(compTarget.libraryPath.toString()) else emptyList()
            val (parseresult, parseDuration) = measureTimedValue {
                 parseMainModule(
                    args.filepath,
                    args.errors,
                    compTarget,
                    args.sourceDirs,
                    libraryDirs,
                    args.cwd,
                    args.quietAll,
                    args.printCompileInfo,
                    args.traceImports
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
                newCodegen = args.newCodegen
                dumpVariables = args.dumpVariables
                dumpSymbols = args.dumpSymbols
                breakpointCpuInstruction = args.breakpointCpuInstruction
                ignoreFootguns = args.ignoreFootguns
                varsHighBank = args.varsHighBank
                varsGolden = args.varsGolden
                if(args.varsAddress!=null)
                    varsAddress = args.varsAddress
                outputDir = args.outputFile?.parent ?: args.outputDir.normalize()
                // the .asm and outputDir must move together: 64tass resolves .binary relative to the .asm
                outputFile = args.outputFile
                symbolDefs = args.symbolDefs
            }
            // apply custom target default for vars address now so it participates in the ROMable check
            if(compilationOptions.varsAddress==null && compilationOptions.varsGolden==false && compilationOptions.varsHighBank==null) {
                (compilationOptions.compTarget as? ConfigFileTarget)?.varsAddress?.let {
                    compilationOptions.varsAddress = it
                }
            }
            resultingProgram = program
            importedFiles = imported

            if(compilationOptions.romable) {
                val hasVars = compilationOptions.varsAddress != null || program.toplevelModule.varsAddress != null || compilationOptions.varsGolden || compilationOptions.varsHighBank!=null
                if (!hasVars)
                    args.errors.err("When ROMable code is selected, variables and memory slabs should be moved to a RAM memory region using either -varsgolden, -varshigh or -varsaddress option or %varsaddress directive", program.toplevelModule.position)
                args.errors.report()
            }


            val processDuration = measureTime {
                processAst(program, args.errors, compilationOptions)
            }

            if(compilationOptions.dumpSymbols) {
                // symbol dump was printed, skip rest of compilation
                // (import files have no main block, so optimization would crash)
                return CompilationResult(
                    resultingProgram, null, null, compilationOptions, importedFiles
                )
            }

            if(args.generateDocumentation && args.errors.noErrors()) {
                printDocumentation(resultingProgram)
                return CompilationResult(
                    resultingProgram, null, null, compilationOptions, importedFiles
                )
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
                if(compilationOptions.compTarget.cpu.is6502)
                    compilationOptions.compTarget.zeropage.checkScratchConflicts(args.errors)

                if (args.printAst1) {
                    println("\n*********** COMPILER AST *************")
                    printProgram(program)
                    println("*********** COMPILER AST END *************\n")
                }

                val (intermediateAst, simplifiedAstDuration2) = measureTimedValue {
                    val intermediateAst = SimplifiedAstMaker(program, args.errors, compilationOptions).transform()
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

                writeBankedCallsFile(intermediateAst, symbolTable!!, compilationOptions, args.errors)

                createAssemblyDuration = measureTime {
                    val result = createAssemblyAndAssemble(
                            intermediateAst,
                            symbolTable,
                            args.errors,
                            compilationOptions,
                            program.generatedLabelSequenceNumber,
                            args.assemble
                        )
                    irInstructionCount = result.irInstructionCount
                    irRegisterCount = result.irRegisterCount
                    if (!result.success) {
                        System.out.flush()
                        System.err.println("Error in codegeneration or assembler")
                        System.err.flush()
                        return null
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
    } catch (mpe: MultipleParseErrors) {
        mpe.errors.forEach { error ->
            args.errors.printSingleError("ERROR ${error.position.toClickableStr()} parse error: ${error.message}")
        }
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
        val maxAddress = if(options.compTarget.POINTER_MEM_SIZE > 2u) 0xFFFFFFFFu else 0xFFFFu
        errors.warn("program load address ${loadAddress.toHex()} is beyond default memtop address ${options.memtopAddress.toHex()}. " +
                "Memtop has been adjusted to ${maxAddress.toHex()} to avoid assembler error. Set a valid %memtop yourself to get rid of this warning.", program.toplevelModule.position)
        options.memtopAddress = maxAddress
    }

    // determine final varsAddress precedence:
    // 1. %varsaddress directive overrides everything
    // 2. CLI -varsaddress / -varsgolden / -varshigh override target default
    // 3. target config default vars_address
    val sourceDirective = program.toplevelModule.varsAddress
    val hasCliRelocation = options.varsGolden || options.varsHighBank != null
    val targetDefaultAddress = (options.compTarget as? ConfigFileTarget)?.varsAddress

    val finalAddress = when {
        sourceDirective != null -> sourceDirective.first
        options.varsAddress != null -> options.varsAddress
        hasCliRelocation -> null  // CLI golden/high takes precedence over target default
        else -> targetDefaultAddress
    }
    options.varsAddress = finalAddress

    if(options.varsAddress != null && hasCliRelocation) {
        val pos = sourceDirective?.second ?: program.toplevelModule.position
        errors.err("cannot combine %varsaddress directive or -varsaddress option with -varsgolden or -varshigh option", pos)
    }

    val maxAddress = if(options.compTarget.POINTER_MEM_SIZE > 2u) 0xFFFFFFFFu else 0xFFFFu
    options.varsAddress?.let {
        if(it > maxAddress) {
            val pos = sourceDirective?.second ?: program.toplevelModule.position
            errors.err("vars address must be valid integer 0..${maxAddress.toHex()}", pos)
        }
    }
}


private class BuiltinFunctionsFacade(functions: Map<String, FSignature>): IBuiltinFunctions {
    lateinit var program: Program

    override val names = functions.keys
    override val purefunctionNames = functions.filter { it.value.pure }.mapTo(mutableSetOf()) { it.key }

    override fun constValues(funcName: String, args: List<Expression>, position: Position): List<NumericLiteral>? {
        if(funcName=="msw" && !args[0].inferType(program).isLong)
            return listOf(NumericLiteral.optimalInteger(0, position))

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
                    cwd: Path,
                    quiet: Boolean,
                    printCompileInfo: Boolean = true,
                     traceImports: Boolean = false): Triple<Program, CompilationOptions, List<Path>> {
    val bf = BuiltinFunctionsFacade(BuiltinFunctions)
    val program = Program(filepath.nameWithoutExtension, bf, compTarget)
    bf.program = program

    val importer = ModuleImporter(program, compTarget, errors, sourceDirs, libraryDirs, cwd, quiet, printCompileInfo, traceImports)
    val importedModuleResult = importer.importMainModule(filepath)
    importedModuleResult.onErr { throw it }
    errors.report()

    val importedFiles = program.modules.map { it.source }
        .filter { it.isFromFilesystem }
        .map { Path(it.origin) }
    val compilerOptions = determineCompilationOptions(program, compTarget)

    if(compTarget.name == Amiga500Target.NAME && compilerOptions.floats) {
        errors.warn(
            "floating-point code requires a 68020 or better CPU with an 68881/68882 FPU; assembler options set to -m68020 -m68881. This program will NOT run on an Amiga 500/600 or 1200 without fp coprocessor!",
            program.toplevelModule.position
        )
    }

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
        if (compilerOptions.launcher == CbmPrgLauncherType.BASIC && compilerOptions.output != OutputType.PRG && compTarget.customLauncher.isEmpty())
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
    val romable = "romable" in allOptions
    val privateSymbols = "private_symbols" in allOptions
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

    // On the non-6502 targets there's no zero page concept, so disable it
    if(compTarget.cpu !in setOf(CpuType.CPU6502, CpuType.CPU65C02))
        zpType = ZeropageType.DONTUSE

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
        compTarget.defaultLauncherType
    else {
        try {
            CbmPrgLauncherType.valueOf(launcherTypeStr)
        } catch (_: IllegalArgumentException) {
            // set default value; actual check and error handling of invalid option is handled in the AstChecker later
            compTarget.defaultLauncherType
        }
    }

    if(outputType == OutputType.LIBRARY) {
        launcherType = CbmPrgLauncherType.NONE
        zpType = ZeropageType.DONTUSE
        noSysInit = true
    }

    return CompilationOptions.builder(compTarget)
        .output(outputType)
        .launcher(launcherType)
        .zeropage(zpType)
        .zpReserved(zpReserved)
        .zpAllowed(if (zpAllowed.isEmpty()) CompilationOptions.AllZeropageAllowed else zpAllowed)
        .floats(floatsEnabled)
        .noSysInit(noSysInit)
        .romable(romable)
        .compilerVersion(VERSION)
        .build()
        .apply { this.privateSymbols = privateSymbols }
}

private fun processAst(program: Program, errors: IErrorReporter, compilerOptions: CompilationOptions) {
    program.checkVarDeclsOnOwnLine(errors)
    errors.report()
    program.preprocessAst(errors, compilerOptions)
    if(errors.noErrors() && compilerOptions.dumpSymbols) {
        printSymbols(program)
        return
    }

    if(compilerOptions.compTarget.cpu.is68k) {
        program.checkM68kSyntax(errors, compilerOptions.compTarget)
        errors.report()
    }

    program.checkAsmSubRegisters(errors, compilerOptions.compTarget)
    errors.report()

    program.checkPrivateAccess(errors)
    errors.report()
    program.flattenNamedStructInitializers(errors)
    errors.report()
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
        for(numCycles in 0..2000) {
            if (errors.noErrors() && remover.applyModifications() > 0)
                remover.visit(program)
            else
                break

            if(numCycles==2000)
                throw InternalCompilerException("removeUnusedCode() is looping endlessly")
        }
    }

    removeUnusedCode(program, errors, compilerOptions)
    program.constantFold(errors, compilerOptions)

    for(numCycles in 0..10000) {
        // keep optimizing expressions and statements until no more steps remain
        val optsDone1 = program.simplifyExpressions(errors, compilerOptions)
        val optsDone2 = program.optimizeStatements(errors, functions, compilerOptions)
        program.constantFold(errors, compilerOptions) // because simplified statements and expressions can result in more constants that can be folded away
        val optsDone3 = program.inlineSubroutines(errors, compilerOptions)  // inlining can expose new calls to inline
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
        program.simplifyExpressions(errors, compilerOptions)
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
    if(compilerOptions.compTarget.cpu.is68k) {
        checkDeferInReentrantSubroutines(program, callGraph, errors)
        checkFrameAddressEscapes(program, errors)
        checkRegfileAsmInRecursivePrograms(program, callGraph, errors)
    }
    program.verifyFunctionArgTypes(errors, compilerOptions)
    errors.report()

    val fixer = BeforeAsmAstChanger(program, compilerOptions, errors)
    fixer.visit(program)
    for(numCycles in 0..2000) {
        if (errors.noErrors() && fixer.applyModifications() > 0)
            fixer.visit(program)
        else
            break

        if(numCycles==2000)
            throw InternalCompilerException("BeforeAsmAstChanger() is looping endlessly")
    }

    program.checkValid(errors, compilerOptions)          // check if final tree is still valid
    errors.report()

    val cleaner = BeforeAsmTypecastCleaner(program, errors)
    cleaner.visit(program)
    for(numCycles in 0..2000) {
        if (errors.noErrors() && cleaner.applyModifications() > 0)
            cleaner.visit(program)
        else
            break

        if(numCycles==2000)
            throw InternalCompilerException("BeforeAsmTypecastCleaner() is looping endlessly")
    }
}

// m68k stack-frame rule: defer-referenced
// subroutine locals and parameters stay in program-static storage, so two live activations
// of a re-entrant deferring subroutine would silently share that storage. Reject the
// unsound case at compile time on the m68k targets only.
private fun checkDeferInReentrantSubroutines(program: Program, callGraph: CallGraph, errors: IErrorReporter) {
    val checker = DeferInReentrantSubroutineChecker(program, callGraph, errors)
    checker.visit(program)
    checker.checkCollectedDefers()
}

private class DeferInReentrantSubroutineChecker(
    private val program: Program,
    private val callGraph: CallGraph,
    private val errors: IErrorReporter
) : IAstVisitor {
    private var currentSubroutine: Subroutine? = null
    private val addressTakenSubroutines = mutableSetOf<Subroutine>()
    private val collectedDefers = mutableListOf<Pair<Subroutine, Defer>>()

    override fun visit(subroutine: Subroutine) {
        val previous = currentSubroutine
        currentSubroutine = subroutine
        subroutine.asmAddress?.varbank?.accept(this)
        subroutine.statements.forEach { it.accept(this) }
        currentSubroutine = previous
    }

    override fun visit(addressOf: AddressOf) {
        addressOf.identifier?.targetSubroutine()?.let { addressTakenSubroutines.add(it) }
        super.visit(addressOf)
    }

    override fun visit(defer: Defer) {
        currentSubroutine?.let { collectedDefers.add(it to defer) }
        defer.scope.accept(this)     // nested defers inside this defer body also belong to this subroutine
    }

    // the defer bodies are only judged after the whole program was walked: whether a subroutine can
    // be re-entered depends on address-taking that may appear anywhere (also after the defer itself)
    fun checkCollectedDefers() {
        for ((subroutine, defer) in collectedDefers) {
            if (!isPotentiallyReentrant(subroutine))
                continue
            val localStateChecker = DeferLocalStateChecker(program, subroutine)
            defer.scope.accept(localStateChecker)
            if (localStateChecker.referencesLocalState)
                errors.err("defer in a recursive subroutine is not yet supported (deferred code references subroutine-local state)", defer.position)
        }
    }

    // conservative re-entrancy estimate: on a call-graph cycle, or referenced in a way that
    // could allow a second live activation (address taken, indirect dispatch such as on..call)
    private fun isPotentiallyReentrant(subroutine: Subroutine): Boolean =
        callGraph.hasRecursionCycle(subroutine) ||
            subroutine in addressTakenSubroutines ||
            (subroutine in callGraph.notCalledButReferenced && subroutine !in callGraph.calledBy)
}

private class DeferLocalStateChecker(
    private val program: Program,
    private val subroutine: Subroutine
) : IAstVisitor {
    var referencesLocalState = false
        private set

    override fun visit(subroutine: Subroutine) {
        // don't descend into subroutines declared inside a defer body
    }

    override fun visit(inlineAssembly: InlineAssembly) {
        // inline assembly can reference locals by name; treat it as local state (conservative)
        referencesLocalState = true
    }

    override fun visit(identifier: IdentifierReference) {
        if(referencesSubroutineLocalState(identifier))
            referencesLocalState = true
    }

    override fun visit(deref: PtrDereference) {
        if(referencesSubroutineLocalState(deref))
            referencesLocalState = true
        super.visit(deref)
    }

    override fun visit(deref: ArrayIndexedPtrDereference) {
        if(referencesSubroutineLocalState(deref))
            referencesLocalState = true
        super.visit(deref)
    }

    private fun referencesSubroutineLocalState(identifier: IdentifierReference): Boolean {
        val target = identifier.targetStatement(program.builtinFunctions) ?: return false
        if(target is StructFieldRef) {
            // struct field access through a variable; the base variable decides whether it is subroutine-local
            val baseName = target.pointer.nameInSource.first()
            val base = target.pointer.definingScope.lookup(listOf(baseName))
            return isSubroutineLocalVar(resolveToVarDecl(base))
        }
        return isSubroutineLocalVar(resolveToVarDecl(target))
    }

    private fun referencesSubroutineLocalState(deref: PtrDereference): Boolean {
        val chain = deref.chain.toMutableList()
        while(chain.isNotEmpty()) {
            if(isSubroutineLocalVar(resolveToVarDecl(deref.definingScope.lookup(chain))))
                return true
            chain.removeLastOrNull()
        }
        return false
    }

    private fun referencesSubroutineLocalState(deref: ArrayIndexedPtrDereference): Boolean {
        val chain = deref.chain.map { it.first }.toMutableList()
        while(chain.isNotEmpty()) {
            if(isSubroutineLocalVar(resolveToVarDecl(deref.definingScope.lookup(chain))))
                return true
            chain.removeLastOrNull()
        }
        return false
    }

    private fun resolveToVarDecl(stmt: Statement?): VarDecl? = when(stmt) {
        is VarDecl -> stmt
        is Alias -> stmt.target.targetVarDecl()
        else -> null
    }

    private fun isSubroutineLocalVar(decl: VarDecl?): Boolean =
        decl!=null && decl.type != VarDeclType.CONST && decl.definingSubroutine === subroutine
}

private fun createAssemblyAndAssemble(program: PtProgram,
                                      symbolTable: SymbolTable,
                                      errors: IErrorReporter,
                                      compilerOptions: CompilationOptions,
                                      lastGeneratedLabelSequenceNr: Int,
                                      assemble: Boolean = true
): AssemblyResult {

    val retainSSAforIR = true

    // single pass to assign call site IDs for all backends
    val bankedExtsubs = findBankSelectorExtsubs(program, symbolTable)
    val asm6502CallIds = mutableMapOf<PtAsmSub, UByte>()
    val irCallIds = mutableMapOf<String, UByte>()
    bankedExtsubs.forEachIndexed { index, node ->
        if (index > 255) {
            errors.err("too many extsub banking call sites (max 255)", node.position)
        } else {
            asm6502CallIds[node] = index.toUByte()
            irCallIds[node.scopedName] = index.toUByte()
        }
    }
    errors.report()

    val asmgen = when {
        compilerOptions.compTarget.cpu in arrayOf(CpuType.CPU6502, CpuType.CPU65C02) -> {
            if(compilerOptions.newCodegen)
                prog8.codegen.new6502.New6502CodeGenerator(retainSSAforIR, irCallIds)
            else
                prog8.codegen.cpu6502.AsmGen6502(lastGeneratedLabelSequenceNr+1, asm6502CallIds)
        }
        compilerOptions.compTarget.cpu.is68k -> prog8.codegen.m68k.M68kCodeGenerator(retainSSAforIR)
        compilerOptions.compTarget.name == VMTarget.NAME -> VmCodeGen(retainSSAforIR, irCallIds)
        else -> throw NotImplementedError("no code generator for cpu ${compilerOptions.compTarget.cpu}")
    }

    val assembly = asmgen.generate(program, symbolTable, compilerOptions, errors)
    errors.report()

    val instructionCount = assembly?.irInstructionCount ?: 0
    val chunkCount = assembly?.irChunkCount ?: 0
    val registerCount = assembly?.irRegisterCount ?: 0

    val success = if(assembly!=null && errors.noErrors()) {
        if(assemble) assembly.assemble(compilerOptions, errors) else true
    } else {
        false
    }
    return AssemblyResult(success, instructionCount, chunkCount, registerCount)
}
