package prog8

import prog8.ast.*
import prog8.compiler.*
import prog8.compiler.target.c64.AsmGen
import prog8.compiler.target.c64.C64Zeropage
import prog8.optimizing.constantFold
import prog8.optimizing.optimizeStatements
import prog8.optimizing.simplifyExpressions
import prog8.parser.ParsingFailedError
import prog8.parser.importLibraryModule
import prog8.parser.importModule
import prog8.parser.moduleName
import java.io.File
import java.io.PrintStream
import java.lang.Exception
import java.nio.file.Paths
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis


fun main(args: Array<String>) {

    // check if the user wants to launch the VM instead
    if("-vm" in args) {
        val newArgs = args.toMutableList()
        newArgs.remove("-vm")
        return stackVmMain(newArgs.toTypedArray())
    }

    printSoftwareHeader("compiler")

    if (args.isEmpty())
        usage()
    compileMain(args)
}

fun printSoftwareHeader(what: String) {
    val buildVersion = object {}.javaClass.getResource("/version.txt").readText().trim()
    println("\nProg8 $what v$buildVersion by Irmen de Jong (irmen@razorvine.net)")
    println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")
}


private fun compileMain(args: Array<String>) {
    var emulatorToStart = ""
    var moduleFile = ""
    var writeVmCode = false
    var writeAssembly = true
    var optimize = true
    var optimizeInlining = true
    for (arg in args) {
        if(arg=="-emu")
            emulatorToStart = "x64"
        else if(arg=="-emu2")
            emulatorToStart = "x64sc"
        else if(arg=="-writevm")
            writeVmCode = true
        else if(arg=="-noasm")
            writeAssembly = false
        else if(arg=="-noopt")
            optimize = false
        else if(arg=="-nooptinline")
            optimizeInlining = false
        else if(!arg.startsWith("-"))
            moduleFile = arg
        else
            usage()
    }
    if(moduleFile.isBlank())
        usage()

    val filepath = Paths.get(moduleFile).normalize()
    var programname = "?"

    try {
        val totalTime = measureTimeMillis {
            // import main module and everything it needs
            println("Parsing...")
            val programAst = Program(moduleName(filepath.fileName), mutableListOf())
            importModule(programAst, filepath)

            val compilerOptions = determineCompilationOptions(programAst)
            if (compilerOptions.launcher == LauncherType.BASIC && compilerOptions.output != OutputType.PRG)
                throw ParsingFailedError("${programAst.modules.first().position} BASIC launcher requires output type PRG.")

            // if we're producing a PRG or BASIC program, include the c64utils and c64lib libraries
            if(compilerOptions.launcher==LauncherType.BASIC || compilerOptions.output==OutputType.PRG) {
                importLibraryModule(programAst, "c64lib")
                importLibraryModule(programAst, "c64utils")
            }

            // always import prog8lib and math
            importLibraryModule(programAst, "math")
            importLibraryModule(programAst, "prog8lib")


            // perform initial syntax checks and constant folding
            println("Syntax check...")
            val time1= measureTimeMillis {
                programAst.checkIdentifiers()
            }
            //println(" time1: $time1")
            val time2 = measureTimeMillis {
                programAst.constantFold()
            }
            //println(" time2: $time2")
            val time3 = measureTimeMillis {
                programAst.reorderStatements()     // reorder statements to please the compiler later
            }
            //println(" time3: $time3")
            val time4 = measureTimeMillis {
                programAst.checkValid(compilerOptions)          // check if tree is valid
            }
            //println(" time4: $time4")

            programAst.checkIdentifiers()
            if(optimize) {
                // optimize the parse tree
                println("Optimizing...")
                while (true) {
                    // keep optimizing expressions and statements until no more steps remain
                    val optsDone1 = programAst.simplifyExpressions()
                    val optsDone2 = programAst.optimizeStatements(optimizeInlining)
                    if (optsDone1 + optsDone2 == 0)
                        break
                }
            }

            programAst.checkValid(compilerOptions)          // check if final tree is valid
            programAst.checkRecursion()         // check if there are recursive subroutine calls

            // namespace.debugPrint()

            // compile the syntax tree into stackvmProg form, and optimize that
            val compiler = Compiler(programAst)
            val intermediate = compiler.compile(compilerOptions)
            if(optimize)
                intermediate.optimize()

            if(writeVmCode) {
                val stackVmFilename = intermediate.name + ".vm.txt"
                val stackvmFile = PrintStream(File(stackVmFilename), "utf-8")
                intermediate.writeCode(stackvmFile)
                stackvmFile.close()
                println("StackVM program code written to '$stackVmFilename'")
            }

            if(writeAssembly) {
                val zeropage = C64Zeropage(compilerOptions)
                intermediate.allocateZeropage(zeropage)
                val assembly = AsmGen(compilerOptions, intermediate, programAst.heap, zeropage).compileToAssembly(optimize)
                assembly.assemble(compilerOptions)
                programname = assembly.name
            }
        }
        println("\nTotal compilation+assemble time: ${totalTime / 1000.0} sec.")

    } catch (px: ParsingFailedError) {
        System.err.print("\u001b[91m")  // bright red
        System.err.println(px.message)
        System.err.print("\u001b[0m")  // reset
        exitProcess(1)
    } catch (ax: AstException) {
        System.err.print("\u001b[91m")  // bright red
        System.err.println(ax.toString())
        System.err.print("\u001b[0m")  // reset
        exitProcess(1)
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

    if(emulatorToStart.isNotEmpty()) {
        println("\nStarting C-64 emulator $emulatorToStart...")
        val cmdline = listOf(emulatorToStart, "-silent", "-moncommands", "$programname.vice-mon-list",
                "-autostartprgmode", "1", "-autostart-warp", "-autostart", programname+".prg")
        val process = ProcessBuilder(cmdline).inheritIO().start()
        process.waitFor()
    }
}


fun determineCompilationOptions(program: Program): CompilationOptions {
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

private fun usage() {
    System.err.println("Missing argument(s):")
    System.err.println("    [-emu]          auto-start the 'x64' C-64 emulator after successful compilation")
    System.err.println("    [-emu2]         auto-start the 'x64sc' C-64 emulator after successful compilation")
    System.err.println("    [-writevm]      write intermediate vm code to a file as well")
    System.err.println("    [-noasm]        don't create assembly code")
    System.err.println("    [-vm]           launch the prog8 virtual machine instead of the compiler")
    System.err.println("    [-noopt]        don't perform any optimizations")
    System.err.println("    [-nooptinline]  don't perform subroutine inlining optimizations")
    System.err.println("    modulefile      main module file to compile")
    exitProcess(1)
}
