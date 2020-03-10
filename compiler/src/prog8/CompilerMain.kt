package prog8

import kotlinx.cli.*
import prog8.ast.base.AstException
import prog8.compiler.*
import prog8.compiler.target.CompilationTarget
import prog8.compiler.target.c64.C64MachineDefinition
import prog8.compiler.target.c64.Petscii
import prog8.compiler.target.c64.codegen.AsmGen
import prog8.parser.ParsingFailedError
import prog8.vm.astvm.AstVm
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.time.LocalDateTime
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    printSoftwareHeader("compiler")

    compileMain(args)
}

internal fun printSoftwareHeader(what: String) {
    val buildVersion = object {}.javaClass.getResource("/version.txt").readText().trim()
    println("\nProg8 $what v$buildVersion by Irmen de Jong (irmen@razorvine.net)")
    println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")
}


fun pathFrom(stringPath: String, vararg rest: String): Path  = FileSystems.getDefault().getPath(stringPath, *rest)


private fun compileMain(args: Array<String>) {
    val cli = CommandLineInterface("prog8compiler")
    val startEmulator by cli.flagArgument("-emu", "auto-start the Vice C-64 emulator after successful compilation")
    val outputDir by cli.flagValueArgument("-out", "directory", "directory for output files instead of current directory", ".")
    val dontWriteAssembly by cli.flagArgument("-noasm", "don't create assembly code")
    val dontOptimize by cli.flagArgument("-noopt", "don't perform any optimizations")
    val launchSimulator by cli.flagArgument("-sim", "launch the builtin execution simulator after compilation")
    val watchMode by cli.flagArgument("-watch", "continuous compilation mode (watches for file changes), greatly increases compilation speed")
    val compilationTarget by cli.flagValueArgument("-target", "compilertarget", "target output of the compiler, currently only 'c64' (C64 6502 assembly) available", "c64")
    val moduleFiles by cli.positionalArgumentsList("modules", "main module file(s) to compile", minArgs = 1)

    try {
        cli.parse(args)
    } catch (e: Exception) {
        exitProcess(1)
    }

    when(compilationTarget) {
        "c64" -> {
            with(CompilationTarget) {
                name = "c64"
                machine = C64MachineDefinition
                encodeString = { str, altEncoding ->
                    if(altEncoding) Petscii.encodeScreencode(str, true) else Petscii.encodePetscii(str, true)
                }
                decodeString = { bytes, altEncoding ->
                    if(altEncoding) Petscii.decodeScreencode(bytes, true) else Petscii.decodePetscii(bytes, true)
                }
                asmGenerator = ::AsmGen
            }
        }
        else -> {
            System.err.println("invalid compilation target")
            exitProcess(1)
        }
    }

    val outputPath = pathFrom(outputDir)
    if(!outputPath.toFile().isDirectory) {
        System.err.println("Output path doesn't exist")
        exitProcess(1)
    }

    if(watchMode && moduleFiles.size<=1) {
        val watchservice = FileSystems.getDefault().newWatchService()

        while(true) {
            val filepath = pathFrom(moduleFiles.single()).normalize()
            println("Continuous watch mode active. Main module: $filepath")

            try {
                val compilationResult = compileProgram(filepath, !dontOptimize, !dontWriteAssembly, outputDir=outputPath)
                println("Imported files (now watching:)")
                for (importedFile in compilationResult.importedFiles) {
                    print("  ")
                    println(importedFile)
                    importedFile.parent.register(watchservice, StandardWatchEventKinds.ENTRY_MODIFY)
                }
                println("[${LocalDateTime.now().withNano(0)}]  Waiting for file changes.")
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
        for(filepathRaw in moduleFiles) {
            val filepath = pathFrom(filepathRaw).normalize()
            val compilationResult: CompilationResult
            try {
                compilationResult = compileProgram(filepath, !dontOptimize, !dontWriteAssembly, outputDir=outputPath)
                if(!compilationResult.success)
                    exitProcess(1)
            } catch (x: ParsingFailedError) {
                exitProcess(1)
            } catch (x: AstException) {
                exitProcess(1)
            }

            if (launchSimulator) {
//                val c64 = razorvine.c64emu.C64Machine("C64 emulator launched from Prog8 compiler")
//                c64.cpu.addBreakpoint(0xea31) { cpu, address ->
//                    println("zz")
//                    Cpu6502.BreakpointResultAction()
//                }
//                c64.start()
                println("\nLaunching AST-based simulator...")
                val vm = AstVm(compilationResult.programAst, compilationTarget)
                vm.run()
            }

            if (startEmulator) {
                if (compilationResult.programName.isEmpty())
                    println("\nCan't start emulator because no program was assembled.")
                else if(startEmulator) {
                    for(emulator in listOf("x64sc", "x64")) {
                        println("\nStarting C-64 emulator $emulator...")
                        val cmdline = listOf(emulator, "-silent", "-moncommands", "${compilationResult.programName}.vice-mon-list",
                                "-autostartprgmode", "1", "-autostart-warp", "-autostart", compilationResult.programName + ".prg")
                        val processb = ProcessBuilder(cmdline).inheritIO()
                        val process: Process
                        try {
                            process=processb.start()
                        } catch(x: IOException) {
                            continue  // try the next emulator executable
                        }
                        process.waitFor()
                        break
                    }
                }
            }
        }
    }
}
