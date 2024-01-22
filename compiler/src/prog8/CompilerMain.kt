package prog8

import kotlinx.cli.*
import prog8.ast.base.AstException
import prog8.code.core.CbmPrgLauncherType
import prog8.code.target.*
import prog8.code.target.virtual.VirtualMachineDefinition
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.compileProgram
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.time.LocalDateTime
import kotlin.io.path.Path
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    println("\nProg8 compiler v${prog8.buildversion.VERSION} by Irmen de Jong (irmen@razorvine.net)")
    if('-' in prog8.buildversion.VERSION) {
        println("Prerelease version from git commit ${prog8.buildversion.GIT_SHA.take(8)} in branch ${prog8.buildversion.GIT_BRANCH}")
    }
    println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")

    val succes = compileMain(args)
    if(!succes)
        exitProcess(1)
}


fun pathFrom(stringPath: String, vararg rest: String): Path  = FileSystems.getDefault().getPath(stringPath, *rest)


private fun compileMain(args: Array<String>): Boolean {
    val cli = ArgParser("prog8compiler", prefixStyle = ArgParser.OptionPrefixStyle.JVM)
    val asmListfile by cli.option(ArgType.Boolean, fullName = "asmlist", description = "make the assembler produce a listing file as well")
    val checkSource by cli.option(ArgType.Boolean, fullName = "check", description = "quickly check program for errors, no output will be produced")
    val symbolDefs by cli.option(ArgType.String, fullName = "D", description = "define assembly symbol(s) with -D SYMBOL=VALUE").multiple()
    val startEmulator1 by cli.option(ArgType.Boolean, fullName = "emu", description = "auto-start emulator after successful compilation")
    val startEmulator2 by cli.option(ArgType.Boolean, fullName = "emu2", description = "auto-start alternative emulator after successful compilation")
    val experimentalCodegen by cli.option(ArgType.Boolean, fullName = "expericodegen", description = "use experimental/alternative codegen")
    val dontWriteAssembly by cli.option(ArgType.Boolean, fullName = "noasm", description="don't create assembly code")
    val dontOptimize by cli.option(ArgType.Boolean, fullName = "noopt", description = "don't perform code optimizations")
    val outputDir by cli.option(ArgType.String, fullName = "out", description = "directory for output files instead of current directory").default(".")
    val quietAssembler by cli.option(ArgType.Boolean, fullName = "quietasm", description = "don't print assembler output results")
    val warnSymbolShadowing by cli.option(ArgType.Boolean, fullName = "warnshadow", description="show assembler warnings about symbol shadowing")
    val sourceDirs by cli.option(ArgType.String, fullName="srcdirs", description = "list of extra paths, separated with ${File.pathSeparator}, to search in for imported modules").multiple().delimiter(File.pathSeparator)
    val includeSourcelines by cli.option(ArgType.Boolean, fullName = "sourcelines", description = "include original Prog8 source lines in generated asm code")
    val splitWordArrays by cli.option(ArgType.Boolean, fullName = "splitarrays", description = "treat all word arrays as tagged with @split to make them lsb/msb split in memory")
    val printAst1 by cli.option(ArgType.Boolean, fullName = "printast1", description = "print out the compiler AST")
    val printAst2 by cli.option(ArgType.Boolean, fullName = "printast2", description = "print out the intermediate AST that is used for code generation")
    val breakpointCpuInstruction by cli.option(ArgType.Choice(listOf("brk", "stp"), { it }), fullName = "breakinstr", description = "the CPU instruction to use as well for %breakpoint")
    val compilationTarget by cli.option(ArgType.String, fullName = "target", description = "target output of the compiler (one of '${C64Target.NAME}', '${C128Target.NAME}', '${Cx16Target.NAME}', '${AtariTarget.NAME}', '${PETTarget.NAME}', '${VMTarget.NAME}') (required)")
    val startVm by cli.option(ArgType.Boolean, fullName = "vm", description = "load and run a .p8ir IR source file in the VM")
    val watchMode by cli.option(ArgType.Boolean, fullName = "watch", description = "continuous compilation mode (watch for file changes)")
    val varsGolden by cli.option(ArgType.Boolean, fullName = "varsgolden", description = "put uninitialized variables in 'golden ram' memory area instead of at the end of the program. On the cx16 target this is $0400-07ff. This is unavailable on other systems.")
    val varsHighBank by cli.option(ArgType.Int, fullName = "varshigh", description = "put uninitialized variables in high memory area instead of at the end of the program. On the cx16 target the value specifies the HiRAM bank to use, on other systems this value is ignored.")
    val slabsGolden by cli.option(ArgType.Boolean, fullName = "slabsgolden", description = "put memory() slabs in 'golden ram' memory area instead of at the end of the program. On the cx16 target this is $0400-07ff. This is unavailable on other systems.")
    val slabsHighBank by cli.option(ArgType.Int, fullName = "slabshigh", description = "put memory() slabs in high memory area instead of at the end of the program. On the cx16 target the value specifies the HiRAM bank to use, on other systems this value is ignored.")
    val moduleFiles by cli.argument(ArgType.String, fullName = "modules", description = "main module file(s) to compile").multiple(999)

    try {
        cli.parse(args)
    } catch (e: IllegalStateException) {
        System.err.println(e.message)
        return false
    }

    println("BREAKPOINTINSTR=$breakpointCpuInstruction")

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

    if(startVm==null) {
        if(compilationTarget==null) {
            System.err.println("No compilation target specified")
            return false
        }

        if (compilationTarget !in setOf(C64Target.NAME, C128Target.NAME, Cx16Target.NAME, AtariTarget.NAME, PETTarget.NAME, VMTarget.NAME)) {
            System.err.println("Invalid compilation target: $compilationTarget")
            return false
        }
    }

    if(varsHighBank==0 && compilationTarget==Cx16Target.NAME) {
        System.err.println("On the Commander X16, HiRAM bank 0 is used by the kernal and can't be used.")
        return false
    }

    if (varsGolden==true) {
        if (compilationTarget != Cx16Target.NAME) {
            System.err.println("Golden Ram is only available on the Commander X16 target.")
            return false
        }
        if (varsHighBank!=null || slabsHighBank!=null) {
            System.err.println("Either use varsgolden or varshigh (and slabsgolden or slabshigh), not both or mixed.")
            return false
        }
    }
    if (slabsGolden==true) {
        if (compilationTarget != Cx16Target.NAME) {
            System.err.println("Golden Ram is only available on the Commander X16 target.")
            return false
        }
        if (varsHighBank!=null || slabsHighBank!=null) {
            System.err.println("Either use golden or high ram, not both.")
            return false
        }
    }

    if(varsHighBank!=null && slabsHighBank!=null && varsHighBank!=slabsHighBank) {
        System.err.println("Vars and slabs high memory bank must be the same.")
        return false
    }

    if(startVm==true) {
        return runVm(moduleFiles.first())
    }

    val processedSymbols = processSymbolDefs(symbolDefs) ?: return false

    if(watchMode==true) {
        val watchservice = FileSystems.getDefault().newWatchService()
        val allImportedFiles = mutableSetOf<Path>()
        val results = mutableListOf<CompilationResult>()
        while(true) {
            println("Continuous watch mode active. Modules: $moduleFiles")
            results.clear()
            for(filepathRaw in moduleFiles) {
                val filepath = pathFrom(filepathRaw).normalize()
                val compilerArgs = CompilerArguments(
                    filepath,
                    if(checkSource==true) false else dontOptimize != true,
                    if(checkSource==true) false else dontWriteAssembly != true,
                    warnSymbolShadowing == true,
                    quietAssembler == true,
                    asmListfile == true,
                    includeSourcelines == true,
                    experimentalCodegen == true,
                    varsHighBank,
                    varsGolden == true,
                    slabsHighBank,
                    slabsGolden == true,
                    compilationTarget!!,
                    splitWordArrays == true,
                    breakpointCpuInstruction,
                    printAst1 == true,
                    printAst2 == true,
                    processedSymbols,
                    srcdirs,
                    outputPath
                )
                val compilationResult = compileProgram(compilerArgs)

                if(checkSource==true)
                    println("No output produced.")

                if(compilationResult!=null)
                    results.add(compilationResult)
            }

            val allNewlyImportedFiles = results.flatMap { it.importedFiles }
            allImportedFiles.addAll(allNewlyImportedFiles)

            println("Imported files (now watching:)")
            for (importedFile in allImportedFiles) {
                print("  ")
                println(importedFile)
                val watchDir = importedFile.parent ?: Path("")
                watchDir.register(watchservice, StandardWatchEventKinds.ENTRY_MODIFY)
            }
            println("[${LocalDateTime.now().withNano(0)}]  Waiting for file changes.")

            fun determineRecompilationNeeded(event: WatchKey): Boolean {
                if(event.isValid) {
                    for (changed in event.pollEvents()) {
                        if (changed.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            val changedPath = changed.context() as Path
                            if (allImportedFiles.any { it.fileName == changedPath.fileName }) {
                                println("  change detected: $changedPath")
                                return true
                            }
                        }
                    }
                }
                return false
            }

            var recompile=false
            while(!recompile) {
                val event = watchservice.take()
                Thread.sleep(50)    // avoid multiple events on same file
                recompile = determineRecompilationNeeded(event)
                event.reset()
            }

            println("\u001b[H\u001b[2J")      // clear the screen
        }

    } else {
        if((startEmulator1==true || startEmulator2==true) && moduleFiles.size>1) {
            System.err.println("can't start emulator when multiple module files are specified")
            return false
        }
        for(filepathRaw in moduleFiles) {
            val filepath = pathFrom(filepathRaw).normalize()
            val compilationResult: CompilationResult
            try {
                val compilerArgs = CompilerArguments(
                    filepath,
                    if(checkSource==true) false else dontOptimize != true,
                    if(checkSource==true) false else dontWriteAssembly != true,
                    warnSymbolShadowing == true,
                    quietAssembler == true,
                    asmListfile == true,
                    includeSourcelines == true,
                    experimentalCodegen == true,
                    varsHighBank,
                    varsGolden == true,
                    slabsHighBank,
                    slabsGolden == true,
                    compilationTarget!!,
                    splitWordArrays == true,
                    breakpointCpuInstruction,
                    printAst1 == true,
                    printAst2 == true,
                    processedSymbols,
                    srcdirs,
                    outputPath
                )
                val result = compileProgram(compilerArgs)

                if(checkSource==true)
                    println("No output produced.")

                if(result==null)
                    return false
                else
                    compilationResult = result
            } catch (x: AstException) {
                return false
            }

            if(startEmulator1==true || startEmulator2==true) {
                if (compilationResult.compilerAst.name.isEmpty()) {
                    println("\nCan't start emulator because no program was assembled.")
                    return true
                }
            }

            val programNameInPath = outputPath.resolve(compilationResult.compilerAst.name)

            if(startEmulator1==true || startEmulator2==true) {
                if (compilationResult.compilationOptions.launcher != CbmPrgLauncherType.NONE || compilationTarget=="atari") {
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

private fun processSymbolDefs(symbolDefs: List<String>): Map<String, String>? {
    val result = mutableMapOf<String, String>()
    val defPattern = """(.+)\s*=\s*(.+)""".toRegex()
    for(def in symbolDefs) {
        val match = defPattern.matchEntire(def.trim())
        if(match==null) {
            System.err.println("invalid symbol definition (expected NAME=VALUE): $def")
            return null
        }
        val (_, name, value) = match.groupValues
        result[name.trim()] = value.trim()
    }
    return result
}

fun runVm(irFilename: String): Boolean {
    val irFile = Path(irFilename)
    val vmdef = VirtualMachineDefinition()
    vmdef.launchEmulator(0, irFile)
    return true
}
