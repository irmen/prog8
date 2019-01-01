package prog8

import prog8.ast.*
import prog8.compiler.*
import prog8.compiler.target.c64.AsmGen
import prog8.optimizing.constantFold
import prog8.optimizing.optimizeStatements
import prog8.optimizing.simplifyExpressions
import prog8.parser.ParsingFailedError
import prog8.parser.importModule
import java.io.File
import java.io.PrintStream
import java.lang.Exception
import java.nio.file.Paths
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis


fun main(args: Array<String>) {
    println("\nProg8 compiler by Irmen de Jong (irmen@razorvine.net)")
    // @todo software license string
    // println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")
    println("**** This is a prerelease version. Please do not distribute! ****\n")

    if (args.isEmpty() || args.size > 2)
        usage()
    compileMain(args)
}

private fun compileMain(args: Array<String>) {
    var startEmu = false
    var asmTrace = false
    var moduleFile = ""
    for (arg in args) {
        if(arg=="--emu")
            startEmu = true
        else if(arg=="--asmtrace")
            asmTrace = true
        else if(!arg.startsWith("--"))
            moduleFile = arg
    }
    if(moduleFile.isBlank())
        usage()

    val filepath = Paths.get(moduleFile).normalize()
    var programname = "?"

    try {
        val totalTime = measureTimeMillis {
            // import main module and process additional imports
            println("Parsing...")
            val moduleAst = importModule(filepath)
            moduleAst.linkParents()
            var namespace = moduleAst.definingScope()

            // determine special compiler options

            val options = moduleAst.statements.filter { it is Directive && it.directive == "%option" }.flatMap { (it as Directive).args }.toSet()
            val outputType = (moduleAst.statements.singleOrNull { it is Directive && it.directive == "%output" }
                    as? Directive)?.args?.single()?.name?.toUpperCase()
            val launcherType = (moduleAst.statements.singleOrNull { it is Directive && it.directive == "%launcher" }
                    as? Directive)?.args?.single()?.name?.toUpperCase()
            moduleAst.loadAddress = (moduleAst.statements.singleOrNull { it is Directive && it.directive == "%address" }
                    as? Directive)?.args?.single()?.int ?: 0
            val zpoption: String? = (moduleAst.statements.singleOrNull { it is Directive && it.directive == "%zeropage" }
                    as? Directive)?.args?.single()?.name?.toUpperCase()
            val zpType: ZeropageType =
                    if (zpoption == null)
                        ZeropageType.KERNALSAFE
                    else
                        try {
                            ZeropageType.valueOf(zpoption)
                        } catch (x: IllegalArgumentException) {
                            ZeropageType.KERNALSAFE
                            // error will be printed by the astchecker
                        }
            val zpReserved = moduleAst.statements
                    .asSequence()
                    .filter { it is Directive && it.directive == "%zpreserved" }
                    .map { (it as Directive).args }
                    .map { it[0].int!!..it[1].int!! }
                    .toList()

            val compilerOptions = CompilationOptions(
                    if (outputType == null) OutputType.PRG else OutputType.valueOf(outputType),
                    if (launcherType == null) LauncherType.BASIC else LauncherType.valueOf(launcherType),
                    zpType, zpReserved,
                    options.any { it.name == "enable_floats" })

            if (compilerOptions.launcher == LauncherType.BASIC && compilerOptions.output != OutputType.PRG)
                throw ParsingFailedError("${moduleAst.position} BASIC launcher requires output type PRG.")
            if (compilerOptions.output == OutputType.PRG || compilerOptions.launcher == LauncherType.BASIC) {
                if (namespace.lookup(listOf("c64utils"), moduleAst.statements.first()) == null)
                    throw ParsingFailedError("${moduleAst.position} When using output type PRG and/or laucher BASIC, the 'c64utils' module must be imported.")
            }

            // perform initial syntax checks and constant folding
            println("Syntax check...")
            val heap = HeapValues()
            val time1= measureTimeMillis {
                moduleAst.checkIdentifiers(heap)
            }
            //println(" time1: $time1")
            val time2 = measureTimeMillis {
                // @todo this is slow!
                moduleAst.constantFold(namespace, heap)
            }
            //println(" time2: $time2")
            val time3 = measureTimeMillis {
                StatementReorderer(namespace, heap).process(moduleAst)     // reorder statements to please the compiler later
            }
            //println(" time3: $time3")
            val time4 = measureTimeMillis {
                // @todo this is slow!
                moduleAst.checkValid(namespace, compilerOptions, heap)          // check if tree is valid
            }
            //println(" time4: $time4")

            // optimize the parse tree
            println("Optimizing...")
            val allScopedSymbolDefinitions = moduleAst.checkIdentifiers(heap)       // useful for checking symbol usage later?
            while (true) {
                // keep optimizing expressions and statements until no more steps remain
                val optsDone1 = moduleAst.simplifyExpressions(namespace, heap)
                val optsDone2 = moduleAst.optimizeStatements(namespace, heap)
                if (optsDone1 + optsDone2 == 0)
                    break
            }

            namespace = moduleAst.definingScope()       // create it again, it could have changed in the meantime
            moduleAst.checkValid(namespace, compilerOptions, heap)          // check if final tree is valid
            moduleAst.checkRecursion(namespace)         // check if there are recursive subroutine calls

            // namespace.debugPrint()

            // compile the syntax tree into stackvmProg form, and optimize that
            val compiler = Compiler(compilerOptions)
            val intermediate = compiler.compile(moduleAst, heap)
            intermediate.optimize()

            val stackVmFilename = intermediate.name + "_stackvm.txt"
            val stackvmFile = PrintStream(File(stackVmFilename), "utf-8")
            intermediate.writeCode(stackvmFile)
            stackvmFile.close()
            println("StackVM program code written to '$stackVmFilename'")

            val assembly = AsmGen(compilerOptions, intermediate, heap, asmTrace).compileToAssembly()
            assembly.assemble(compilerOptions)
            programname = assembly.name
        }
        println("\nTotal compilation+assemble time: ${totalTime / 1000.0} sec.")

    } catch (px: ParsingFailedError) {
        System.err.print("\u001b[91m")  // bright red
        System.err.println(px.message)
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

    if(startEmu) {
        println("\nStarting C64 emulator...")
        val cmdline = listOf("x64", "-silent", "-moncommands", "$programname.vice-mon-list",
                "-autostartprgmode", "1", "-autostart-warp", "-autostart", programname+".prg")
        val process = ProcessBuilder(cmdline).inheritIO().start()
        process.waitFor()
    }
}

private fun usage() {
    System.err.println("Missing argument(s):")
    System.err.println("    [--emu]       auto-start the C64 emulator after successful compilation")
    System.err.println("    [--asmtrace]  print trace output of the AsmGen for debugging purposes")
    System.err.println("    modulefile    main module file to compile")
    exitProcess(1)
}
