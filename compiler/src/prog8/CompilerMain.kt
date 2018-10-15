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
import java.nio.file.Paths
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    println("\nProg8 compiler by Irmen de Jong (irmen@razorvine.net)")
    // @todo software license string
    // println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")
    println("**** This is a prerelease version. Please do not distribute! ****\n")

    if(args.size != 1) {
        System.err.println("requires one argument: name of module file")
        exitProcess(1)
    }

    val startTime = System.currentTimeMillis()
    val filepath = Paths.get(args[0]).normalize()

    try {
        // import main module and process additional imports
        println("Parsing...")
        val moduleAst = importModule(filepath)
        moduleAst.linkParents()
        var namespace = moduleAst.definingScope()

        // determine special compiler options

        val options = moduleAst.statements.filter { it is Directive && it.directive=="%option" }.flatMap { (it as Directive).args }.toSet()
        val outputType = (moduleAst.statements.singleOrNull { it is Directive && it.directive=="%output"}
                as? Directive)?.args?.single()?.name?.toUpperCase()
        val launcherType = (moduleAst.statements.singleOrNull { it is Directive && it.directive=="%launcher"}
                as? Directive)?.args?.single()?.name?.toUpperCase()
        moduleAst.loadAddress = (moduleAst.statements.singleOrNull { it is Directive && it.directive=="%address"}
                as? Directive)?.args?.single()?.int ?: 0
        val zpoption: String? = (moduleAst.statements.singleOrNull { it is Directive && it.directive=="%zeropage"}
                as? Directive)?.args?.single()?.name?.toUpperCase()
        val zpType: ZeropageType =
                    if(zpoption==null)
                        ZeropageType.KERNALSAFE
                    else
                        try {
                            ZeropageType.valueOf(zpoption)
                        } catch(x: IllegalArgumentException) {
                            ZeropageType.KERNALSAFE
                            // error will be printed by the astchecker
                        }
        val zpReserved = moduleAst.statements
                .filter{it is Directive && it.directive=="%zpreserved"}
                .map{ (it as Directive).args }
                .map{ it[0].int!! .. it[1].int!! }

        val compilerOptions = CompilationOptions(
                if(outputType==null) OutputType.PRG else OutputType.valueOf(outputType),
                if(launcherType==null) LauncherType.BASIC else LauncherType.valueOf(launcherType),
                zpType, zpReserved,
                options.any{ it.name=="enable_floats"})


        // perform initial syntax checks and constant folding
        val heap = HeapValues()
        moduleAst.checkIdentifiers()
        moduleAst.constantFold(namespace, heap)
        StatementReorderer(namespace, heap).process(moduleAst)     // reorder statements to please the compiler later
        moduleAst.checkValid(namespace, compilerOptions, heap)          // check if tree is valid

        // optimize the parse tree
        println("Optimizing...")
        val allScopedSymbolDefinitions = moduleAst.checkIdentifiers()
        while(true) {
            // keep optimizing expressions and statements until no more steps remain
            val optsDone1 = moduleAst.simplifyExpressions(namespace, heap)
            val optsDone2 = moduleAst.optimizeStatements(namespace, heap)
            if(optsDone1 + optsDone2 == 0)
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
        println("Debug: ${intermediate.numVariables} allocated variables and constants")
        println("Debug: ${heap.size()} heap values")
        println("Debug: ${intermediate.numInstructions} vm instructions")

        val stackVmFilename =  intermediate.name + "_stackvm.txt"
        val stackvmFile = PrintStream(File(stackVmFilename), "utf-8")
        intermediate.writeCode(stackvmFile)
        stackvmFile.close()
        println("StackVM program code written to '$stackVmFilename'")

        val assembly = AsmGen(compilerOptions, intermediate, heap).compileToAssembly()
        assembly.assemble(compilerOptions)

        val endTime = System.currentTimeMillis()
        println("\nTotal compilation+assemble time: ${(endTime-startTime)/1000.0} sec.")

//        // todo start the vice emulator
//        val program = "foo"
//        val cmdline = listOf("x64", "-moncommands", monitorfile,
//                "-autostartprgmode", "1", "-autostart-warp", "-autostart", program)
//        ProcessBuilder(cmdline).inheritIO().start()

    } catch (px: ParsingFailedError) {
        System.err.println(px.message)
        exitProcess(1)
    }
}
