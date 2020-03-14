package prog8.compiler

import prog8.ast.AstToSourceCode
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.statements.Directive
import prog8.compiler.target.CompilationTarget
import prog8.optimizer.constantFold
import prog8.optimizer.optimizeStatements
import prog8.optimizer.simplifyExpressions
import prog8.parser.ParsingFailedError
import prog8.parser.importLibraryModule
import prog8.parser.importModule
import prog8.parser.moduleName
import java.nio.file.Path
import kotlin.system.measureTimeMillis


class CompilationResult(val success: Boolean,
                        val programAst: Program,
                        val programName: String,
                        val importedFiles: List<Path>)


fun compileProgram(filepath: Path,
                   optimize: Boolean,
                   writeAssembly: Boolean,
                   outputDir: Path): CompilationResult {
    lateinit var programAst: Program
    var programName: String? = null

    var importedFiles: List<Path> = emptyList()
    var success=false

    try {
        val totalTime = measureTimeMillis {
            // import main module and everything it needs
            println("Parsing...")
            programAst = Program(moduleName(filepath.fileName), mutableListOf())
            importModule(programAst, filepath)

            importedFiles = programAst.modules.filter { !it.source.startsWith("@embedded@") }.map{ it.source }

            val compilerOptions = determineCompilationOptions(programAst)
            if (compilerOptions.launcher == LauncherType.BASIC && compilerOptions.output != OutputType.PRG)
                throw ParsingFailedError("${programAst.modules.first().position} BASIC launcher requires output type PRG.")

            // if we're producing a PRG or BASIC program, include the c64utils and c64lib libraries
            if (compilerOptions.launcher == LauncherType.BASIC || compilerOptions.output == OutputType.PRG) {
                importLibraryModule(programAst, "c64lib")
                importLibraryModule(programAst, "c64utils")
            }

            // always import prog8lib and math
            importLibraryModule(programAst, "math")
            importLibraryModule(programAst, "prog8lib")


            // perform initial syntax checks and constant folding
            println("Syntax check...")
            val time1 = measureTimeMillis {
                programAst.checkIdentifiers()
                programAst.makeForeverLoops()
            }

            //println(" time1: $time1")
            val time2 = measureTimeMillis {
                programAst.constantFold()
            }
            //println(" time2: $time2")
            val time3 = measureTimeMillis {
                programAst.removeNopsFlattenAnonScopes()
                programAst.reorderStatements()
                programAst.addTypecasts()
            }
            //println(" time3: $time3")
            val time4 = measureTimeMillis {
                programAst.checkValid(compilerOptions)          // check if tree is valid
            }
            //println(" time4: $time4")

            programAst.checkIdentifiers()
            if (optimize) {
                // optimize the parse tree
                println("Optimizing...")
                while (true) {
                    // keep optimizing expressions and statements until no more steps remain
                    val optsDone1 = programAst.simplifyExpressions()
                    val optsDone2 = programAst.optimizeStatements()
                    if (optsDone1 + optsDone2 == 0)
                        break
                }
            }

            programAst.addTypecasts()
            programAst.removeNopsFlattenAnonScopes()
            programAst.checkValid(compilerOptions)          // check if final tree is valid
            programAst.checkRecursion()         // check if there are recursive subroutine calls

            // printAst(programAst)

            if(writeAssembly) {
                // asm generation directly from the Ast, no need for intermediate code
                val zeropage = CompilationTarget.machine.getZeropage(compilerOptions)
                programAst.anonscopeVarsCleanup()
                val assembly = CompilationTarget.asmGenerator(programAst, zeropage, compilerOptions, outputDir).compileToAssembly(optimize)
                assembly.assemble(compilerOptions)
                programName = assembly.name
            }
            success = true
        }
        println("\nTotal compilation+assemble time: ${totalTime / 1000.0} sec.")

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
    return CompilationResult(success, programAst, programName ?: "", importedFiles)
}

fun printAst(programAst: Program) {
    println()
    val printer = AstToSourceCode(::print, programAst)
    printer.visit(programAst)
    println()
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
    val zpType: ZeropageType =
            if (zpoption == null)
                if(floatsEnabled) ZeropageType.FLOATSAFE else ZeropageType.KERNALSAFE
            else
                try {
                    ZeropageType.valueOf(zpoption)
                } catch (x: IllegalArgumentException) {
                    ZeropageType.KERNALSAFE
                    // error will be printed by the astchecker
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
