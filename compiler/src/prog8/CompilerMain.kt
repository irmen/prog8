package prog8

import kotlinx.cli.*
import prog8.ast.base.AstException
import prog8.code.core.*
import prog8.code.target.*
import prog8.code.target.virtual.VirtualMachineDefinition
import prog8.codegen.virtual.VmCodeGen
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.compileProgram
import java.io.File
import java.nio.file.*
import java.time.LocalDateTime
import kotlin.system.exitProcess
import kotlin.io.path.Path


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
    val asmListfile by cli.option(ArgType.Boolean, fullName = "asmlist", description = "make the assembler produce a listing file as well")
    val symbolDefs by cli.option(ArgType.String, fullName = "D", description = "define assembly symbol(s) with -D SYMBOL=VALUE").multiple()
    val evalStackAddrString by cli.option(ArgType.String, fullName = "esa", description = "override the eval-stack base address (must be page aligned)")
    val startEmulator1 by cli.option(ArgType.Boolean, fullName = "emu", description = "auto-start emulator after successful compilation")
    val startEmulator2 by cli.option(ArgType.Boolean, fullName = "emu2", description = "auto-start alternative emulator after successful compilation")
    val experimentalCodegen by cli.option(ArgType.Boolean, fullName = "expericodegen", description = "use experimental/alternative codegen")
    val keepIR by cli.option(ArgType.Boolean, fullName = "keepIR", description = "keep the IR code file (for targets that use it)")
    val dontWriteAssembly by cli.option(ArgType.Boolean, fullName = "noasm", description="don't create assembly code")
    val dontOptimize by cli.option(ArgType.Boolean, fullName = "noopt", description = "don't perform any optimizations")
    val dontReinitGlobals by cli.option(ArgType.Boolean, fullName = "noreinit", description = "don't create code to reinitialize globals on multiple runs of the program (experimental!)")
    val outputDir by cli.option(ArgType.String, fullName = "out", description = "directory for output files instead of current directory").default(".")
    val optimizeFloatExpressions by cli.option(ArgType.Boolean, fullName = "optfloatx", description = "optimize float expressions (warning: can increase program size)")
    val quietAssembler by cli.option(ArgType.Boolean, fullName = "quietasm", description = "don't print assembler output results")
    val slowCodegenWarnings by cli.option(ArgType.Boolean, fullName = "slowwarn", description="show debug warnings about slow/problematic assembly code generation")
    val sourceDirs by cli.option(ArgType.String, fullName="srcdirs", description = "list of extra paths, separated with ${File.pathSeparator}, to search in for imported modules").multiple().delimiter(File.pathSeparator)
    val compilationTarget by cli.option(ArgType.String, fullName = "target", description = "target output of the compiler (one of '${C64Target.NAME}', '${C128Target.NAME}', '${Cx16Target.NAME}', '${AtariTarget.NAME}', '${VMTarget.NAME}')").default(C64Target.NAME)
    val startVm by cli.option(ArgType.Boolean, fullName = "vm", description = "load and run a p8-virt or p8-ir listing in the VM instead")
    val watchMode by cli.option(ArgType.Boolean, fullName = "watch", description = "continuous compilation mode (watch for file changes)")
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

    if (compilationTarget !in setOf(C64Target.NAME, C128Target.NAME, Cx16Target.NAME, AtariTarget.NAME, VMTarget.NAME)) {
        System.err.println("Invalid compilation target: $compilationTarget")
        return false
    }

    if(startVm==true) {
        return runVm(moduleFiles.first())
    }

    var evalStackAddr: UInt? = null
    if(evalStackAddrString!=null) {
        try {
            evalStackAddr = if (evalStackAddrString!!.startsWith("0x"))
                evalStackAddrString!!.substring(2).toUInt(16)
            else if (evalStackAddrString!!.startsWith("$"))
                evalStackAddrString!!.substring(1).toUInt(16)
            else
                evalStackAddrString!!.toUInt()
        } catch(nx: NumberFormatException) {
            System.err.println("invalid address for evalstack: $evalStackAddrString")
            return false
        }
        if(evalStackAddr !in 256u..65536u-512u || (evalStackAddr and 255u != 0u)) {
            System.err.println("invalid address for evalstack: ${evalStackAddr.toHex()}")
            return false
        }
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
                    dontOptimize != true,
                    optimizeFloatExpressions == true,
                    dontReinitGlobals == true,
                    dontWriteAssembly != true,
                    slowCodegenWarnings == true,
                    quietAssembler == true,
                    asmListfile == true,
                    experimentalCodegen == true,
                    keepIR == true,
                    compilationTarget,
                    evalStackAddr,
                    processedSymbols,
                    srcdirs,
                    outputPath
                )
                val compilationResult = compileProgram(compilerArgs)
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
                    keepIR == true,
                    compilationTarget,
                    evalStackAddr,
                    processedSymbols,
                    srcdirs,
                    outputPath
                )
                val result = compileProgram(compilerArgs)
                if(result==null)
                    return false
                else
                    compilationResult = result
            } catch (x: AstException) {
                return false
            }

            if(startEmulator1==true || startEmulator2==true) {
                if (compilationResult.program.name.isEmpty()) {
                    println("\nCan't start emulator because no program was assembled.")
                    return true
                }
            }

            val programNameInPath = outputPath.resolve(compilationResult.program.name)

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
    if(irFilename.endsWith(".p8ir")) {
        val withoutSuffix = irFilename.substring(0, irFilename.length-5)
        val compiled = VmCodeGen.compileIR(irFile)
        if (!compiled.assemble(CompilationOptions(  // these are just dummy options, the actual options are inside the .p8ir file itself:
                OutputType.PRG,
                CbmPrgLauncherType.NONE,
                ZeropageType.DONTUSE,
                emptyList(),
                floats = true,
                noSysInit = true,
                compTarget = VMTarget(),
                loadAddress = VMTarget().machine.PROGRAM_LOAD_ADDRESS
            ))
        ) {
            return false
        }
        val vmdef = VirtualMachineDefinition()
        vmdef.launchEmulator(0, Paths.get(withoutSuffix))
        return true
    }
    val vmdef = VirtualMachineDefinition()
    vmdef.launchEmulator(0, irFile)
    return true
}
