package prog8

import prog8.ast.base.AstException
import prog8.compiler.CompilationResult
import prog8.compiler.compileProgram
import prog8.parser.ParsingFailedError
import prog8.vm.astvm.AstVm
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.util.*
import kotlin.system.exitProcess


fun main(args: Array<String>) {

    printSoftwareHeader("compiler")

    if (args.isEmpty())
        usage()
    compileMain(args)
}

internal fun printSoftwareHeader(what: String) {
    val buildVersion = object {}.javaClass.getResource("/version.txt").readText().trim()
    println("\nProg8 $what v$buildVersion by Irmen de Jong (irmen@razorvine.net)")
    println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")
}


private fun compileMain(args: Array<String>) {
    var emulatorToStart = ""
    var moduleFile = ""
    var writeAssembly = true
    var optimize = true
    var launchAstVm = false
    var watchMode = false
    for (arg in args) {
        if(arg=="-emu")
            emulatorToStart = "x64"
        else if(arg=="-emu2")
            emulatorToStart = "x64sc"
        else if(arg=="-noasm")
            writeAssembly = false
        else if(arg=="-noopt")
            optimize = false
        else if(arg=="-avm")
            launchAstVm = true
        else if(arg=="-watch")
            watchMode = true
        else if(!arg.startsWith("-"))
            moduleFile = arg
        else
            usage()
    }

    if(watchMode) {
        if(moduleFile.isBlank())
            usage()

        val watchservice = FileSystems.getDefault().newWatchService()

        while(true) {
            val filepath = Paths.get(moduleFile).normalize()
            println("Continuous watch mode active. Main module: $filepath")

            try {
                val compilationResult = compileProgram(filepath, optimize, writeAssembly)
                println("Imported files (now watching:)")
                for (importedFile in compilationResult.importedFiles) {
                    print("  ")
                    println(importedFile)
                    importedFile.parent.register(watchservice, StandardWatchEventKinds.ENTRY_MODIFY)
                }
                println("${Date()}: Waiting for file changes.")
                val event = watchservice.take()
                for(changed in event.pollEvents()) {
                    val changedPath = changed.context() as Path
                    println("  change detected: $changedPath")
                }
                event.reset()
                println("\u001b[H\u001b[2J")      // clear the screen
            } catch (x: Exception) {
                throw x
            }
        }

    } else {
        if(moduleFile.isBlank())
            usage()

        val filepath = Paths.get(moduleFile).normalize()
        val compilationResult: CompilationResult

        try {
            compilationResult = compileProgram(filepath, optimize, writeAssembly)
            if(!compilationResult.success)
                exitProcess(1)
        } catch (x: ParsingFailedError) {
            exitProcess(1)
        } catch (x: AstException) {
            exitProcess(1)
        }

        if (launchAstVm) {
            println("\nLaunching AST-based vm...")
            val vm = AstVm(compilationResult.programAst)
            vm.run()
        }

        if (emulatorToStart.isNotEmpty()) {
            if (compilationResult.programName.isEmpty())
                println("\nCan't start emulator because no program was assembled.")
            else {
                println("\nStarting C-64 emulator $emulatorToStart...")
                val cmdline = listOf(emulatorToStart, "-silent", "-moncommands", "${compilationResult.programName}.vice-mon-list",
                        "-autostartprgmode", "1", "-autostart-warp", "-autostart", compilationResult.programName + ".prg")
                val process = ProcessBuilder(cmdline).inheritIO().start()
                process.waitFor()
            }
        }
    }
}


private fun usage() {
    System.err.println("Missing argument(s):")
    System.err.println("    [-noasm]        don't create assembly code")
    System.err.println("    [-noopt]        don't perform any optimizations")
    System.err.println("    [-emu]          auto-start the 'x64' C-64 emulator after successful compilation")
    System.err.println("    [-emu2]         auto-start the 'x64sc' C-64 emulator after successful compilation")
    System.err.println("    [-avm]          launch the prog8 ast-based virtual machine after compilation")
    System.err.println("    [-watch]        continuous compilation mode (watches for file changes)")
    System.err.println("    modulefile      main module file to compile")
    exitProcess(1)
}
