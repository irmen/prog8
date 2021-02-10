package prog8

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.multiple
import prog8.ast.base.AstException
import prog8.compiler.CompilationResult
import prog8.compiler.compileProgram
import prog8.compiler.target.C64Target
import prog8.compiler.target.ICompilationTarget
import prog8.compiler.target.Cx16Target
import prog8.parser.ParsingFailedError
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
    val cli = ArgParser("prog8compiler", prefixStyle = ArgParser.OptionPrefixStyle.JVM)
    val startEmulator by cli.option(ArgType.Boolean, fullName = "emu", description = "auto-start emulator after successful compilation")
    val outputDir by cli.option(ArgType.String, fullName = "out", description = "directory for output files instead of current directory").default(".")
    val dontWriteAssembly by cli.option(ArgType.Boolean, fullName = "noasm", description="don't create assembly code")
    val dontOptimize by cli.option(ArgType.Boolean, fullName = "noopt", description = "don't perform any optimizations")
    val watchMode by cli.option(ArgType.Boolean, fullName = "watch", description = "continuous compilation mode (watches for file changes), greatly increases compilation speed")
    val slowCodegenWarnings by cli.option(ArgType.Boolean, fullName = "slowwarn", description="show debug warnings about slow/problematic assembly code generation")
    val compilationTarget by cli.option(ArgType.String, fullName = "target", description = "target output of the compiler, currently '${C64Target.name}' and '${Cx16Target.name}' available").default(C64Target.name)
    val moduleFiles by cli.argument(ArgType.String, fullName = "modules", description = "main module file(s) to compile").multiple(999)

    try {
        cli.parse(args)
    } catch (e: IllegalStateException) {
        System.err.println(e.message)
        exitProcess(1)
    }

    val outputPath = pathFrom(outputDir)
    if(!outputPath.toFile().isDirectory) {
        System.err.println("Output path doesn't exist")
        exitProcess(1)
    }

    if(watchMode==true) {
        val watchservice = FileSystems.getDefault().newWatchService()
        val allImportedFiles = mutableSetOf<Path>()

        while(true) {
            println("Continuous watch mode active. Modules: $moduleFiles")
            val results = mutableListOf<CompilationResult>()
            for(filepathRaw in moduleFiles) {
                val filepath = pathFrom(filepathRaw).normalize()
                val compilationResult = compileProgram(filepath, dontOptimize!=true, dontWriteAssembly!=true, slowCodegenWarnings==true, compilationTarget, outputPath)
                results.add(compilationResult)
            }

            val allNewlyImportedFiles = results.flatMap { it.importedFiles }
            allImportedFiles.addAll(allNewlyImportedFiles)

            println("Imported files (now watching:)")
            for (importedFile in allImportedFiles) {
                print("  ")
                println(importedFile)
                val watchDir = importedFile.parent ?: Path.of(".")
                watchDir.register(watchservice, StandardWatchEventKinds.ENTRY_MODIFY)
            }
            println("[${LocalDateTime.now().withNano(0)}]  Waiting for file changes.")

            var recompile=false
            while(!recompile) {
                val event = watchservice.take()
                for (changed in event.pollEvents()) {
                    val changedPath = changed.context() as Path
                    if(allImportedFiles.any { it.fileName == changedPath.fileName }) {
                        println("  change detected: $changedPath")
                        recompile = true
                    }
                }
                event.reset()
            }

            println("\u001b[H\u001b[2J")      // clear the screen
        }

    } else {
        for(filepathRaw in moduleFiles) {
            val filepath = pathFrom(filepathRaw).normalize()
            val compilationResult: CompilationResult
            try {
                compilationResult = compileProgram(filepath, dontOptimize!=true, dontWriteAssembly!=true, slowCodegenWarnings==true, compilationTarget, outputPath)
                if(!compilationResult.success)
                    exitProcess(1)
            } catch (x: ParsingFailedError) {
                exitProcess(1)
            } catch (x: AstException) {
                exitProcess(1)
            }

            if (startEmulator==true) {
                if (compilationResult.programName.isEmpty())
                    println("\nCan't start emulator because no program was assembled.")
                else {
                    ICompilationTarget.instance.machine.launchEmulator(compilationResult.programName)
                }
            }
        }
    }
}
