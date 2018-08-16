package il65

import java.nio.file.Paths
import il65.ast.*
import il65.parser.*
import il65.compiler.*
import il65.optimizing.optimizeExpressions
import il65.optimizing.optimizeStatements
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    try {
        println("\nIL65 compiler by Irmen de Jong (irmen@razorvine.net)")
        println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")

        val filepath = Paths.get(args[0]).normalize()
        val moduleAst = importModule(filepath)
        moduleAst.linkParents()
        val globalNamespace = moduleAst.namespace()
        // globalNamespace.debugPrint()

        moduleAst.optimizeExpressions(globalNamespace)
        moduleAst.optimizeStatements(globalNamespace)
        val globalNamespaceAfterOptimize = moduleAst.namespace()  // it could have changed in the meantime
        moduleAst.checkValid(globalNamespaceAfterOptimize)        // check if final tree is valid

        // determine special compiler options
        val options = moduleAst.statements.filter { it is Directive && it.directive=="%option" }.flatMap { (it as Directive).args }.toSet()
        val optionEnableFloats = options.contains(DirectiveArg(null, "enable_floats", null))

        val compilerOptions = CompilationOptions(OutputType.PRG,
                Launcher.BASIC,
                Zeropage.COMPATIBLE,
                optionEnableFloats)

        val intermediate = moduleAst.compileToIntermediate(compilerOptions, globalNamespaceAfterOptimize)
        intermediate.optimize()

//        val assembler = intermediate.compileToAssembly()
//        assembler.assemble(compilerOptions, "input", "output")
//        val monitorfile = assembler.genereateBreakpointList()

        // start the vice emulator
//        val program = "foo"
//        val cmdline = listOf("x64", "-moncommands", monitorfile,
//                "-autostartprgmode", "1", "-autostart-warp", "-autostart", program)
//        ProcessBuilder(cmdline).inheritIO().start()

    } catch (px: ParsingFailedError) {
        System.err.println(px.message)
        exitProcess(1)
    }
}


