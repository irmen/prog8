package prog8

import kotlinx.cli.*
import prog8.ast.base.AstException
import prog8.codegen.target.AtariTarget
import prog8.codegen.target.C128Target
import prog8.codegen.target.C64Target
import prog8.codegen.target.Cx16Target
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.compileProgram
import prog8.compilerinterface.LauncherType
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.time.LocalDateTime
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    val buildVersion = object {}.javaClass.getResource("/version.txt")?.readText()?.trim()
    println("\nProg8 compiler v$buildVersion by Irmen de Jong (irmen@razorvine.net)")
    println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")

    val succes = compileMain(args)
    if(!succes)
        exitProcess(1)
}


fun pathFrom(stringPath: String, vararg rest: String): Path  = FileSystems.getDefault().getPath(stringPath, *rest)


private fun compileMain(args: Array<String>): Boolean {
    val cli = ArgParser("prog8compiler", prefixStyle = ArgParser.OptionPrefixStyle.JVM)
    val startEmulator1 by cli.option(ArgType.Boolean, fullName = "emu", description = "auto-start emulator after successful compilation")
    val startEmulator2 by cli.option(ArgType.Boolean, fullName = "emu2", description = "auto-start alternative emulator after successful compilation")
    val outputDir by cli.option(ArgType.String, fullName = "out", description = "directory for output files instead of current directory").default(".")
    val dontWriteAssembly by cli.option(ArgType.Boolean, fullName = "noasm", description="don't create assembly code")
    val dontOptimize by cli.option(ArgType.Boolean, fullName = "noopt", description = "don't perform any optimizations")
    val dontReinitGlobals by cli.option(ArgType.Boolean, fullName = "noreinit", description = "don't create code to reinitialize globals on multiple runs of the program (experimental!)")
    val optimizeFloatExpressions by cli.option(ArgType.Boolean, fullName = "optfloatx", description = "optimize float expressions (warning: can increase program size)")
    val watchMode by cli.option(ArgType.Boolean, fullName = "watch", description = "continuous compilation mode (watches for file changes), greatly increases compilation speed")
    val slowCodegenWarnings by cli.option(ArgType.Boolean, fullName = "slowwarn", description="show debug warnings about slow/problematic assembly code generation")
    val quietAssembler by cli.option(ArgType.Boolean, fullName = "quietasm", description = "don't print assembler output results")
    val asmListfile by cli.option(ArgType.Boolean, fullName = "asmlist", description = "make the assembler produce a listing file as well")
    val experimentalCodegen by cli.option(ArgType.Boolean, fullName = "expericodegen", description = "use experimental codegen")
    val compilationTarget by cli.option(ArgType.String, fullName = "target", description = "target output of the compiler (one of '${C64Target.NAME}', '${C128Target.NAME}', '${Cx16Target.NAME}', '${AtariTarget.NAME}')").default(C64Target.NAME)
    val sourceDirs by cli.option(ArgType.String, fullName="srcdirs", description = "list of extra paths, separated with ${File.pathSeparator}, to search in for imported modules").multiple().delimiter(File.pathSeparator)
    val moduleFiles by cli.argument(ArgType.String, fullName = "modules", description = "main module file(s) to compile").multiple(999)

    try {
        cli.parse(args)
    } catch (e: IllegalStateException) {
        System.err.println(e.message)
        return false
    }

    val outputPath = pathFrom(outputDir)
    if(!outputPath.toFile().isDirectory) {
        System.err.println("Output path doesn't exist")
        return false
    }

    val faultyOption = moduleFiles.firstOrNull { it.startsWith('-') }
    if(faultyOption!=null) {
        System.err.println("Unknown command line option given: $faultyOption")
        return false
    }

    val srcdirs = sourceDirs.toMutableList()
    if(srcdirs.firstOrNull()!=".")
        srcdirs.add(0, ".")

    if (compilationTarget !in setOf(C64Target.NAME, C128Target.NAME, Cx16Target.NAME, AtariTarget.NAME)) {
        System.err.println("Invalid compilation target: $compilationTarget")
        return false
    }

    if(watchMode==true) {
        val watchservice = FileSystems.getDefault().newWatchService()
        val allImportedFiles = mutableSetOf<Path>()

        while(true) {
            println("Continuous watch mode active. Modules: $moduleFiles")
            val results = mutableListOf<CompilationResult>()
            for(filepathRaw in moduleFiles) {
                val filepath = pathFrom(filepathRaw).normalize()
                val compilerArgs = CompilerArguments(
                    filepath,
                    dontOptimize != true,
                    optimizeFloatExpressions == true,
                    dontReinitGlobals == true,
                    dontWriteAssembly != true,
                    slowCodegenWarnings == true,
                    quietAssembler == true,
                    asmListfile == true,
                    experimentalCodegen == true,
                    compilationTarget,
                    srcdirs,
                    outputPath
                )
                val compilationResult = compileProgram(compilerArgs)
                results.add(compilationResult)
            }

            val allNewlyImportedFiles = results.flatMap { it.importedFiles }
            allImportedFiles.addAll(allNewlyImportedFiles)

            println("Imported files (now watching:)")
            for (importedFile in allImportedFiles) {
                print("  ")
                println(importedFile)
                val watchDir = importedFile.parent ?: Path.of("")
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
                val compilerArgs = CompilerArguments(
                    filepath,
                    dontOptimize != true,
                    optimizeFloatExpressions == true,
                    dontReinitGlobals == true,
                    dontWriteAssembly != true,
                    slowCodegenWarnings == true,
                    quietAssembler == true,
                    asmListfile == true,
                    experimentalCodegen == true,
                    compilationTarget,
                    srcdirs,
                    outputPath
                )
                compilationResult = compileProgram(compilerArgs)
                if (!compilationResult.success)
                    return false
            } catch (x: AstException) {
                return false
            }

            if(startEmulator1==true || startEmulator2==true) {
                if (compilationResult.programName.isEmpty()) {
                    println("\nCan't start emulator because no program was assembled.")
                    return true
                }
            }

            val programNameInPath = outputPath.resolve(compilationResult.programName)

            if(startEmulator1==true || startEmulator2==true) {
                if (compilationResult.compilationOptions.launcher != LauncherType.NONE) {
                    if (startEmulator1 == true)
                        compilationResult.compilationOptions.compTarget.machine.launchEmulator(1, programNameInPath)
                    else if (startEmulator2 == true)
                        compilationResult.compilationOptions.compTarget.machine.launchEmulator(2, programNameInPath)
                } else {
                    println("\nCan't start emulator because program has no launcher type.")
                }
            }
        }
    }

    return true
}
