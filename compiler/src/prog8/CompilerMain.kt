package prog8

import prog8.ast.*
import prog8.compiler.*
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
        val zpType = (moduleAst.statements.singleOrNull { it is Directive && it.directive=="%zeropage"}
                as? Directive)?.args?.single()?.name?.toUpperCase()

        val compilerOptions = CompilationOptions(
                if(outputType==null) OutputType.PRG else OutputType.valueOf(outputType),
                if(launcherType==null) LauncherType.BASIC else LauncherType.valueOf(launcherType),
                if(zpType==null) ZeropageType.KERNALSAFE else ZeropageType.valueOf(zpType),
                options.any{ it.name=="enable_floats"})


        // perform syntax checks and optimizations
        moduleAst.checkIdentifiers()

        println("Optimizing...")
        moduleAst.constantFold(namespace)
        moduleAst.checkValid(namespace, compilerOptions)          // check if tree is valid
        val allScopedSymbolDefinitions = moduleAst.checkIdentifiers()
        while(true) {
            // keep optimizing expressions and statements until no more steps remain
            val optsDone1 = moduleAst.simplifyExpressions(namespace)
            val optsDone2 = moduleAst.optimizeStatements(namespace)
            if(optsDone1 + optsDone2 == 0)
                break
        }

        StatementReorderer().process(moduleAst)     // reorder statements to please the compiler later
        namespace = moduleAst.definingScope()       // create it again, it could have changed in the meantime
        moduleAst.checkValid(namespace, compilerOptions)          // check if final tree is valid
        moduleAst.checkRecursion(namespace)         // check if there are recursive subroutine calls

        // namespace.debugPrint()

        // compile the syntax tree into stackvmProg form, and optimize that
        val compiler = Compiler(compilerOptions)
        val intermediate = compiler.compile(moduleAst)
        intermediate.optimize()

        val stackVmFilename =  intermediate.name + "_stackvm.txt"
        val stackvmFile = PrintStream(File(stackVmFilename), "utf-8")
        intermediate.writeAsText(stackvmFile)
        stackvmFile.close()
        println("StackVM program code written to '$stackVmFilename'")

//        val assembly = stackvmProg.compileToAssembly()
//
//        assembly.assemble(compilerOptions, "input", "output")
//        val monitorfile = assembly.generateBreakpointList()

        val endTime = System.currentTimeMillis()
        println("\nTotal compilation time: ${(endTime-startTime)/1000.0} sec.")

//        // start the vice emulator
//        val program = "foo"
//        val cmdline = listOf("x64", "-moncommands", monitorfile,
//                "-autostartprgmode", "1", "-autostart-warp", "-autostart", program)
//        ProcessBuilder(cmdline).inheritIO().start()

    } catch (px: ParsingFailedError) {
        System.err.println(px.message)
        exitProcess(1)
    }
}
