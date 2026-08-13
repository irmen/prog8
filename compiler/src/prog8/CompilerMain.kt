package prog8

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import prog8.ast.AstException
import prog8.code.source.ImportFileSystem
import prog8.code.source.ImportFileSystem.expandTilde
import prog8.code.target.CompilationTargets
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8.code.target.getCompilationTargetByName
import prog8.compiler.*
import prog8.intermediate.IRFileReader
import prog8.intermediate.Opcode
import java.io.*
import java.net.ConnectException
import java.net.StandardProtocolFamily
import java.net.URI
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.*
import java.time.LocalDateTime
import kotlin.io.path.*
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    if (args.contains("--daemon-server")) {
        val socketPath = CompilerDaemon.getDefaultSocketPath()
        CompilerDaemon(socketPath).run()
        return
    }

    // NOTE: if your compiler/IDE complains here about "Unresolved reference: buildversion",
    //       it means that you have to run the gradle task once to generate this file.
    //       Do that with this command:  gradle createVersionFile

    val succes = compileMain(args)
    if(!succes)
        exitProcess(1)
}


fun pathFrom(stringPath: String, vararg rest: String): Path  = FileSystems.getDefault().getPath(stringPath, *rest)


private class CompilerCli : CliktCommand(name = "prog8c") {
    init {
        context {
            helpOptionNames = setOf("-h", "-help", "--help")
        }
    }

    val asmListfile by option("-asmlist", "--asmlist", help = "make the assembler produce a listing file as well").flag()
    val breakpointCpuInstruction by option("-breakinstr", "--breakinstr", help = "the CPU instruction to use as well for %breakpoint").choice("brk", "stp")
    val bytes2float by option("-bytes2float", "--bytes2float", help = "convert a comma separated list of bytes from the target system to a float value")
    val checkSource by option("-check", "--check", help = "quickly check program for errors, no output will be produced").flag()
    val symbolDefs by option("-D", "--D", help = "define assembly symbol(s) with -D SYMBOL=VALUE").multiple()
    val dumpSymbols by option("-dumpsymbols", "--dumpsymbols", help = "print a dump of the variable declarations and subroutine signatures").flag()
    val dumpVariables by option("-dumpvars", "--dumpvars", help = "print a dump of the variables in the program").flag()
    val libSearch by option("-libsearch", "--libsearch", help = "search for a regex pattern in the embedded library files")
    val libDump by option("-libdump", "--libdump", help = "dump all the embedded library files into the specified output directory")
    val startEmulator1 by option("-emu", "--emu", help = "auto-start emulator after successful compilation").flag()
    val startEmulator2 by option("-emu2", "--emu2", help = "auto-start alternative emulator after successful compilation").flag()
    val newCodegen by option("-newcodegen", "--newcodegen", help = "use experimental/alternative 6502 codegen based on IR instead of AST").flag()
    val float2bytes by option("-float2bytes", "--float2bytes", help = "convert floating point number to a list of bytes for the target system")
    val ignoreFootguns by option("-ignorefootguns", "--ignorefootguns", help = "don't print warnings for 'footgun' issues").flag()
    val profilingInstrumentation by option("-profiling", "--profiling", help = "add subroutine profiling instrumentation (cx16 only)").flag()
    val dontWriteAssembly by option("-noasm", "--noasm", help = "don't create assembly code").flag()
    val dontOptimize by option("-noopt", "--noopt", help = "don't perform code optimizations").flag()
    val outputDir by option("-out", "--out", help = "directory for output files instead of current directory").default(".")
    val plainText by option("-plaintext", "--plaintext", help = "output only plain text, no colors or fancy symbols").flag()
    val printAst1 by option("-printast1", "--printast1", help = "print out the internal compiler AST").flag()
    val printAst2 by option("-printast2", "--printast2", help = "print out the simplified AST that is used for code generation").flag()
    val quietAll by option("-quiet", "--quiet", help = "don't print compiler and assembler messages, except warnings and errors").flag()
    val quietAssembler by option("-quietasm", "--quietasm", help = "don't print assembler messages").flag()
    val slabsGolden by option("-slabsgolden", "--slabsgolden", help = "put memory() slabs in golden ram").flag()
    val slabsHighBank by option("-slabshigh", "--slabshigh", help = "put memory() slabs in high memory area").int()
    val dontIncludeSourcelines by option("-nosourcelines", "--nosourcelines", help = "do not include original Prog8 source lines in generated asm code").flag()
    val sourceDirs by option("-srcdirs", "--srcdirs", help = "extra paths to search for imported modules").multiple()
    val compilationTarget by option("-target", "--target", help = "target output of the compiler (one of ${CompilationTargets.joinToString(",")} or a custom target properties file)")
    val showTimings by option("-timings", "--timings", help = "show internal compiler timings").flag()
    val varsGolden by option("-varsgolden", "--varsgolden", help = "put uninitialized variables in golden ram").flag()
    val varsHighBank by option("-varshigh", "--varshigh", help = "put uninitialized variables in high memory area").int()
    val startVm by option("-vm", "--vm", help = "run a .p8ir IR source file in the embedded VM").flag()
    val vmTrace by option("-vmtrace", "--vmtrace", help = "trace VM execution instruction by instruction").flag()
    val traceImports by option("-traceimports", "--traceimports", help = "trace all module imports and loads").flag()
    val warnSymbolShadowing by option("-warnshadow", "--warnshadow", help = "show assembler warnings about symbol shadowing").flag()
    val warnImplicitTypeCasts by option("-warnimplicitcasts", "--warnimplicitcasts", help = "show compiler warnings about implicit casts").flag()
    val watchMode by option("-watch", "--watch", help = "continuous compilation mode").flag()
    val version by option("-version", "--version", help = "print compiler version and exit").flag()
    val compareIR by option("-compareir", "--compareir", help = "compare generated .p8ir file with existing .p8ir file")
    val daemonMode by option("-daemon", "--daemon", help = "use the prog8c compilation daemon").flag()
    val moduleFiles by argument().multiple()

    override fun run() = Unit
}


private fun compileMain(args: Array<String>): Boolean {
    val cli = CompilerCli()
    try {
        cli.parse(args)
    } catch (e: CliktError) {
        cli.echoFormattedHelp(e)
        return e.statusCode == 0
    }

    with(cli) {
    if(version==true) {
        banner()
        return true
    }

    if(quietAll!=true)
        banner()

    if(libDump!=null) {
        scanLibraryFiles(libDump, null)
        return true
    }

    if(libSearch!=null) {
        scanLibraryFiles(null, libSearch)
        return true
    }

    val outputPath = pathFrom(outputDir)
    outputPath.createDirectories()

    if(profilingInstrumentation==true && compilationTarget!=Cx16Target.NAME) {
        System.err.println("Profiling instrumentation is only available on the cx16 target.")
        return false
    }

    val faultyOption = moduleFiles.firstOrNull { it.startsWith('-') }
    if(faultyOption!=null) {
        System.err.println("Unknown command line option given: $faultyOption")
        return false
    }

    val srcdirs = sourceDirs.flatMap { it.split(File.pathSeparator) }.map { expandTilde(it) }

    if(!startVm) {
        if(compilationTarget==null) {
            System.err.println("No compilation target specified")
            return false
        }

        if (compilationTarget !in CompilationTargets) {
            val configfile = expandTilde(Path(compilationTarget!!))
            if(!configfile.isReadable()) {
                System.err.println("Invalid compilation target: $compilationTarget")
                return false
            }
        }
    }

    if(bytes2float!=null) {
        convertBytesToFloat(bytes2float!!, compilationTarget!!)
        return true
    }
    if(float2bytes!=null) {
        convertFloatToBytes(float2bytes!!, compilationTarget!!)
        return true
    }

    if(moduleFiles.isEmpty()) {
        System.err.println("No module file(s) specified")
        return false
    }

    if(varsHighBank==0 && compilationTarget==Cx16Target.NAME) {
        System.err.println("On the Commander X16, HIRAM bank 0 is used by the kernal and can't be used.")
        return false
    }

    if (varsGolden==true) {
        if (varsHighBank!=null || slabsHighBank!=null) {
            System.err.println("Either use varsgolden or varshigh (and slabsgolden or slabshigh), not both or mixed.")
            return false
        }
    }
    if (slabsGolden==true) {
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
        runVm(moduleFiles.first(), quietAll==true, vmTrace==true)
        return true
    }

    val processedSymbols = processSymbolDefs(symbolDefs) ?: return false

    if(watchMode==true) {
        val watchservice = FileSystems.getDefault().newWatchService()
        val allImportedFiles = mutableSetOf<Path>()
        val results = mutableListOf<CompilationResult>()
        while(true) {
            println("Continuous watch mode active. Modules: $moduleFiles")
            ImportFileSystem.clearCaches()
            results.clear()
            for(filepathRaw in moduleFiles) {
                val filepath = pathFrom(filepathRaw).normalize()
                val txtcolors = if(plainText==true) ErrorReporter.PlainText else ErrorReporter.AnsiColors
                val compilerArgs = CompilerArguments(
                    filepath,
                    if(checkSource==true) false else dontOptimize != true,
                    if(checkSource==true) false else dontWriteAssembly != true,
                    warnSymbolShadowing == true,
                    warnImplicitTypeCasts == true,
                    quietAll == true,
                    quietAll == true || quietAssembler == true,
                    showTimings == true,
                    asmListfile == true,
                    dontIncludeSourcelines != true,
                    newCodegen == true,
                    dumpVariables == true,
                    dumpSymbols == true,
                    varsHighBank,
                    varsGolden == true,
                    slabsHighBank,
                    slabsGolden == true,
                    compilationTarget!!,
                    breakpointCpuInstruction,
                    printAst1 == true,
                    printAst2 == true,
                    printCompileInfo = true,
                    ignoreFootguns == true,
                    profilingInstrumentation == true,
                    traceImports == true,
                    processedSymbols,
                    srcdirs,
                    outputPath,
                    errors = ErrorReporter(txtcolors)
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

    } else if (daemonMode == true) {
        if((startEmulator1==true || startEmulator2==true) && moduleFiles.size>1) {
            System.err.println("can't start emulator when multiple module files are specified")
            return false
        }
        for(filepathRaw in moduleFiles) {
            val filepath = pathFrom(filepathRaw).normalize().toAbsolutePath()
            val txtcolors = if(plainText==true) ErrorReporter.PlainText else ErrorReporter.AnsiColors
            val absoluteSrcDirs = srcdirs.map { Path.of(it).toAbsolutePath().toString() }
            val compilerArgs = CompilerArguments(
                filepath,
                if(checkSource==true) false else dontOptimize != true,
                if(checkSource==true) false else dontWriteAssembly != true,
                warnSymbolShadowing == true,
                warnImplicitTypeCasts == true,
                quietAll == true,
                quietAll == true || quietAssembler == true,
                showTimings == true,
                asmListfile == true,
                dontIncludeSourcelines != true,
                newCodegen == true,
                dumpVariables == true,
                dumpSymbols == true,
                varsHighBank,
                varsGolden == true,
                slabsHighBank,
                slabsGolden == true,
                compilationTarget!!,
                breakpointCpuInstruction,
                printAst1 == true,
                printAst2 == true,
                true,
                ignoreFootguns == true,
                profilingInstrumentation == true,
                traceImports == true,
                processedSymbols,
                absoluteSrcDirs,
                outputPath,
                cwd = Path.of(System.getProperty("user.dir")),
                errors = ErrorReporter(txtcolors)
            )
            val response = compileViaDaemon(compilerArgs, plainText == true)
            if (response == null || !response.ok)
                return false
            
            if (startEmulator1 == true || startEmulator2 == true) {
                if (response.outputFiles.isEmpty()) {
                    println("\nCan't start emulator because no program was assembled.")
                } else {
                    val programPathRaw = response.outputFiles.first()
                    val programPath = Path.of(programPathRaw.removeSuffix(".prg"))
                    
                    val target = getCompilationTargetByName(compilationTarget!!)
                    if (startEmulator1 == true) {
                        if (target is VMTarget) {
                            target.launchEmulatorWithTrace(programPath, quietAll==true, vmTrace==true)
                        } else {
                            target.launchEmulator(1, programPath, quietAll==true)
                        }
                    } else {
                        target.launchEmulator(2, programPath, quietAll==true)
                    }
                }
            }
        }
        return true
    } else {
        if((startEmulator1==true || startEmulator2==true) && moduleFiles.size>1) {
            System.err.println("can't start emulator when multiple module files are specified")
            return false
        }
        for(filepathRaw in moduleFiles) {
            val filepath = pathFrom(filepathRaw).normalize()
            val compilationResult: CompilationResult
            try {
                val txtcolors = if(plainText==true) ErrorReporter.PlainText else ErrorReporter.AnsiColors
                val compilerArgs = CompilerArguments(
                    filepath,
                    if(checkSource==true) false else dontOptimize != true,
                    if(checkSource==true) false else dontWriteAssembly != true,
                    warnSymbolShadowing == true,
                    warnImplicitTypeCasts == true,
                    quietAll == true,
                    quietAll == true || quietAssembler == true,
                    showTimings == true,
                    asmListfile == true,
                    dontIncludeSourcelines != true,
                    newCodegen == true,
                    dumpVariables == true,
                    dumpSymbols==true,
                    varsHighBank,
                    varsGolden == true,
                    slabsHighBank,
                    slabsGolden == true,
                    compilationTarget!!,
                    breakpointCpuInstruction,
                    printAst1 == true,
                    printAst2 == true,
                    true,
                    ignoreFootguns == true,
                    profilingInstrumentation == true,
                    traceImports == true,
                    processedSymbols,
                    srcdirs,
                    outputPath,
                    errors = ErrorReporter(txtcolors)
                )
                val result = compileProgram(compilerArgs)

                if(checkSource==true)
                    println("No output produced.")

                if(result==null)
                    return false
                else
                    compilationResult = result
            } catch (_: AstException) {
                return false
            }

            if(startEmulator1==true || startEmulator2==true) {
                if (compilationResult.compilerAst.name.isEmpty()) {
                    println("\nCan't start emulator because no program was assembled.")
                    return true
                }
            }

            val programNameInPath = outputPath.resolve(compilationResult.compilerAst.name)
            val programPath = Path.of(programNameInPath.toString().removeSuffix(".prg"))

            if (compareIR != null) {
                compareIrFiles(outputPath.resolve("${compilationResult.compilerAst.name}.p8ir"), Path(compareIR!!))
            }

            if (startEmulator1 == true) {
                if (compilationResult.compilationOptions.compTarget is VMTarget) {
                    (compilationResult.compilationOptions.compTarget as VMTarget).launchEmulatorWithTrace(
                        programPath, quietAll==true, vmTrace==true)
                } else {
                    compilationResult.compilationOptions.compTarget.launchEmulator(1, programPath, quietAll==true)
                }
            } else if (startEmulator2 == true)
                compilationResult.compilationOptions.compTarget.launchEmulator(2, programPath, quietAll==true)
        }
    }

    return true
    }
}

private fun banner() {
    println("\nProg8 compiler v${prog8.buildversion.VERSION} by Irmen de Jong (irmen@razorvine.net)")
    if('-' in prog8.buildversion.VERSION) {
        println("Prerelease version from git commit ${prog8.buildversion.GIT_SHA.take(8)} in branch ${prog8.buildversion.GIT_BRANCH}")
    }
    println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")
}

fun convertFloatToBytes(number: String, target: String) {
    val tgt = getCompilationTargetByName(target)
    val dbl = number.toDouble()
    val bytes = tgt.convertFloatToBytes(dbl)
    print("$dbl in bytes on '$target': ")
    println(bytes.joinToString(","))
}

fun convertBytesToFloat(bytelist: String, target: String) {
    val tgt = getCompilationTargetByName(target)
    val bytes = bytelist.split(',').map { it.trim().toUByte() }
    val number = tgt.convertBytesToFloat(bytes)
    println("floating point value on '$target': $number")
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

fun runVm(irFilename: String, quiet: Boolean, traceEnabled: Boolean = false) {
    val irFile = Path(irFilename)
    val vmdef = VMTarget()
    vmdef.launchEmulatorWithTrace(irFile, quiet, traceEnabled)
}

private fun compareIrFiles(newFile: Path, baselineFile: Path) {
    if (!baselineFile.toFile().exists()) {
        System.err.println("Compare error: baseline file not found: $baselineFile")
        return
    }
    if (!newFile.toFile().exists()) {
        System.err.println("Compare error: generated file not found: $newFile")
        return
    }

    // Parse both IR files properly using IRFileReader
    val baselineProgram = IRFileReader().read(baselineFile)
    val newProgram = IRFileReader().read(newFile)

    // Extract metrics from parsed IR programs
    val baselineMetrics = extractIrMetrics(baselineProgram)
    val newMetrics = extractIrMetrics(newProgram)

    println("\nIR File Comparison: ${newFile.fileName} vs ${baselineFile.fileName}")
    println("=".repeat(70))
    
    println("\nSummary:")
    println("  Instructions:  ${formatMetric(newMetrics.instructions, baselineMetrics.instructions)}")
    println("  Chunks:        ${formatMetric(newMetrics.chunks, baselineMetrics.chunks)}")
    println("  Registers:     ${formatMetric(newMetrics.registers, baselineMetrics.registers)}")
    println("  File size:     ${formatMetric(newFile.toFile().length(), baselineFile.toFile().length())}")
    
    // Compare instruction sequences
    val baselineCode = extractCodeInstructions(baselineProgram)
    val newCode = extractCodeInstructions(newProgram)
    
    if (baselineCode == newCode) {
        println("\n✓ No code differences (generated IR instructions are identical).")
    } else {
        val differences = findCodeDifferences(baselineCode, newCode)
        println("\n✗ Found ${differences.size} instruction difference(s) in code chunks.")
        
        // Show first few differences
        if (differences.isNotEmpty()) {
            println("\nFirst differences:")
            differences.take(10).forEach { (index, baselineIns, newIns) ->
                println("  [$index] $baselineIns  →  $newIns")
            }
            if (differences.size > 10) {
                println("  ... and ${differences.size - 10} more differences")
            }
        }
        println("\n  (Use 'diff' command on the .p8ir files for detailed comparison)")
    }
    println()
}

private fun formatMetric(newVal: Int, baselineVal: Int): String {
    val delta = newVal - baselineVal
    val pct = if (baselineVal != 0) (delta * 100.0 / baselineVal) else 0.0
    val deltaStr = if (delta == 0) "(no change)" else if (delta > 0) "(+${delta}, +${pct.toInt()}%)" else "(${delta}, ${pct.toInt()}%)"
    return "$newVal (new) vs $baselineVal (baseline)  $deltaStr"
}

private fun formatMetric(newVal: Long, baselineVal: Long): String {
    val delta = newVal - baselineVal
    val pct = if (baselineVal != 0L) (delta * 100.0 / baselineVal) else 0.0
    val deltaStr = if (delta == 0L) "(no change)" else if (delta > 0L) "(+${delta}, +${pct.toInt()}%)" else "(${delta}, ${pct.toInt()}%)"
    return "$newVal bytes (new) vs $baselineVal bytes (baseline)  $deltaStr"
}

data class InstructionDiff(val index: Int, val baseline: String, val new: String)

private fun findCodeDifferences(baseline: List<String>, new: List<String>): List<InstructionDiff> {
    val diffs = mutableListOf<InstructionDiff>()
    val maxLines = maxOf(baseline.size, new.size)
    for (i in 0 until maxLines) {
        val newLine = if (i < new.size) new[i] else "<end>"
        val baselineLine = if (i < baseline.size) baseline[i] else "<end>"
        if (newLine != baselineLine) {
            diffs.add(InstructionDiff(i, baselineLine, newLine))
        }
    }
    return diffs
}

data class IrMetrics(val instructions: Int, val chunks: Int, val registers: Int)

private fun extractIrMetrics(program: prog8.intermediate.IRProgram): IrMetrics {
    var instructions = 0
    var chunks = 0
    val readRegs = mutableSetOf<Int>()
    val writeRegs = mutableSetOf<Int>()
    
    // Count chunks and instructions from all subroutines
    program.allSubs().forEach { sub ->
        sub.chunks.forEach { chunk ->
            chunks++
            instructions += chunk.instructions.size
            
            // Extract register usage from each chunk
            chunk.instructions.forEach { ins ->
                ins.reg1?.let { readRegs.add(it) }
                ins.reg2?.let { readRegs.add(it) }
                ins.reg3?.let { 
                    if (ins.opcode == Opcode.CONCAT || ins.opcode == Opcode.EXT || 
                        ins.opcode == Opcode.EXTS) {
                        readRegs.add(it)
                    } else {
                        writeRegs.add(it)
                    }
                }
                ins.fpReg1?.let { readRegs.add(it.value) }
                ins.fpReg2?.let { readRegs.add(it.value) }
            }
        }
    }
    
    // Also count global init chunks
    program.globalInits.instructions.forEach { ins ->
        ins.reg1?.let { readRegs.add(it) }
        ins.reg2?.let { readRegs.add(it) }
        ins.reg3?.let { writeRegs.add(it) }
    }
    
    val registers = readRegs.size + writeRegs.size
    return IrMetrics(instructions, chunks, registers)
}

private fun extractCodeInstructions(program: prog8.intermediate.IRProgram): List<String> {
    val code = mutableListOf<String>()
    
    // Get all instructions from all subroutines
    program.allSubs().forEach { sub ->
        sub.chunks.forEach { chunk ->
            chunk.instructions.forEach { ins ->
                code.add(ins.toString())
            }
        }
    }
    
    return code
}



private fun scanLibraryFiles(dump: String?, searchPattern: String?) {
    val libraryPrefix = "/prog8lib"

    val dumpPath = if(dump!=null) pathFrom(dump) else null
    val pattern = searchPattern?.toRegex(RegexOption.IGNORE_CASE)
    if(pattern!=null) {
        println("You can also have a look in the documentation for the libraries at https://prog8.readthedocs.io/en/latest/libraries.html")
        println("The library source files are available in the Github repository at https://github.com/irmen/prog8/tree/master/compiler/res/prog8lib")
        println("Searching for pattern '$searchPattern' in embedded library files.\n")
    }
    if(dumpPath!=null) {
        println("Dumping embedded library files into $dumpPath\n")
        dumpPath.createDirectories()
        val license = dumpPath / "${libraryPrefix.drop(1)}-${prog8.buildversion.VERSION}/LICENSE.txt"
        license.parent.createDirectories()
        license.writeText("These library files belong to the Prog8 compiler project, see https://github.com/irmen/prog8/\n" +
        "They are licensed under the GNU GPL 3.0 software license, see https://www.gnu.org/licenses/gpl.html\n")
        println("Note: the exported library source files have the same software license as the compiler itself.\n")
    }

    fun search(path: Path, currentPattern: Regex, maxHits: Int = Int.MAX_VALUE): Int {
        val hits = mutableListOf<String>()
        path.useLines { lines ->
            lines.forEachIndexed { index, line ->
                if (currentPattern.containsMatchIn(line)) {
                    hits.add("${(index + 1).toString().padStart(6)}:  ${line.trimStart()}")
                    if (hits.size >= maxHits) return@useLines
                }
            }
        }
        if (hits.isNotEmpty()) {
            println("Found in Library file '${path.absolutePathString().drop(libraryPrefix.length+1)}':")
            hits.forEach(::println)
            println()
        }
        return hits.size
    }

    fun dump(path: Path) {
        val targetfolder = dumpPath!! / (path.first().pathString + "-${prog8.buildversion.VERSION}")
        val target = targetfolder / path.pathString.drop(libraryPrefix.length+1)
        target.parent.createDirectories()
        target.writeBytes(path.readBytes())
    }

    // Get the location of the current JAR
    val jarUrl = object {}.javaClass.protectionDomain.codeSource.location
    FileSystems.newFileSystem(URI.create("jar:$jarUrl"), emptyMap<String, String>()).use { fs ->
        val dir = fs.getPath(libraryPrefix)
        val allFiles = Files.walk(dir).use { paths -> paths.filter { Files.isRegularFile(it) }.toList() }
        if (searchPattern != null) {
            val p = pattern!!
            var totalHits = 0
            allFiles.forEach { totalHits += search(it, p) }
            if (totalHits == 0 && searchPattern.length >= 2 && searchPattern.none { it in "\\.[]{}()^$|?*+" }) {
                val fuzzyPatternString = searchPattern.map { Regex.escape(it.toString()) }.joinToString(".{0,3}?")
                val fuzzyPattern = fuzzyPatternString.toRegex(RegexOption.IGNORE_CASE)
                println("No exact matches found. Trying fuzzy search...\n")
                var totalFuzzyHits = 0
                for (file in allFiles) {
                    val remaining = 100 - totalFuzzyHits
                    if (remaining <= 0) break
                    totalFuzzyHits += search(file, fuzzyPattern, remaining)
                }
                if (totalFuzzyHits >= 100) {
                    println("... (stopped after 100 hits)")
                }
            }
        } else if (dumpPath != null) {
            allFiles.forEach { dump(it) }
        }
    }
}


// ---- daemon client ----

private fun compileViaDaemon(compilerArgs: CompilerArguments, plainText: Boolean): DaemonResponse? {
    val socketPath = CompilerDaemon.getDefaultSocketPath()
    var channel = connectToDaemon(socketPath)
    var wasExisting = true
    // Suppress the informational daemon messages when output should be quiet,
    // or when only a symbol/variable dump is requested (its output must not be polluted).
    val silent = compilerArgs.quietAll || compilerArgs.dumpSymbols || compilerArgs.dumpVariables

    if (channel == null) {
        wasExisting = false
        if (!silent) println("Starting prog8c daemon...")
        if (!startDaemonProcess()) {
            System.err.println("Failed to start prog8c daemon")
            return null
        }
        channel = connectToDaemon(socketPath, 4_000)
        if (channel == null) {
            System.err.println("prog8c daemon did not start in time")
            return null
        }
        if (!silent) println("prog8c daemon started.")
    } else {
        if (!silent) println("Using existing prog8c daemon at $socketPath")
    }

    printCompileStart(compilerArgs)
    val response = communicateWithDaemon(channel, compilerArgs, plainText)
    if (response != null && (response.ok || !wasExisting || response.versionError == null)) return response

    // Existing daemon rejected us (version mismatch) and has self-terminated.
    // Start a fresh daemon.
    if (!silent) println("Starting new prog8c daemon (previous was stale)...")
    if (!startDaemonProcess()) {
        System.err.println("Failed to start prog8c daemon")
        return null
    }
    channel = connectToDaemon(socketPath, 4_000)
    if (channel == null) {
        System.err.println("prog8c daemon did not start in time")
        return null
    }
    if (!silent) println("prog8c daemon (new) started.")

    return communicateWithDaemon(channel, compilerArgs, plainText)
}

private fun printCompileStart(args: CompilerArguments) {
    if (args.quietAll)
        return
    val programPath = args.filepath.toAbsolutePath().normalize()
    val display = args.cwd.toAbsolutePath().relativize(programPath)
    println("Compiling program $display")
    println("Compiler target: ${getCompilationTargetByName(args.compilationTarget).name}")
}

private fun startDaemonProcess(): Boolean {
    return try {
        val javaHome = System.getProperty("java.home")
        val javaCmd = "$javaHome/bin/java"
        val classpath = System.getProperty("java.class.path")

        // Shadow JAR (fat JAR with Main-Class manifest) takes priority
        val shadowJar = classpath.split(File.pathSeparatorChar)
            .firstOrNull { it.contains("prog8c") && it.endsWith("-all.jar") }

        val cmd = mutableListOf<String>()
        cmd.add(javaCmd)
        if (shadowJar != null) {
            cmd.addAll(listOf("-jar", shadowJar))
        } else {
            cmd.addAll(listOf("-cp", classpath, "prog8.CompilerMainKt"))
        }
        cmd.add("--daemon-server")

        val pb = ProcessBuilder(cmd)
        pb.directory(ImportFileSystem.userHome.toFile())
        pb.inheritIO()
        pb.start()
        true
    } catch (e: Exception) {
        System.err.println("Cannot start daemon: ${e.message}")
        false
    }
}

private fun connectToDaemon(socketPath: Path): SocketChannel? {
    return try {
        val address = UnixDomainSocketAddress.of(socketPath)
        val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
        channel.connect(address)
        channel
    } catch (_: ConnectException) {
        null
    } catch (_: Exception) {
        null
    }
}

private fun connectToDaemon(socketPath: Path, timeoutMs: Long): SocketChannel? {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        connectToDaemon(socketPath)?.let { return it }
        Thread.sleep(100)
    }
    return null
}

private fun communicateWithDaemon(channel: SocketChannel, compilerArgs: CompilerArguments, plainText: Boolean): DaemonResponse? {
    try {
        val writer = BufferedWriter(OutputStreamWriter(Channels.newOutputStream(channel), Charsets.UTF_8))
        val reader = BufferedReader(InputStreamReader(Channels.newInputStream(channel), Charsets.UTF_8))

        val request = DaemonRequest(
            version = prog8.buildversion.BUILD_UNIX_TIME.toString(),
            plainText = plainText,
            filepath = compilerArgs.filepath.toString(),
            optimize = compilerArgs.optimize,
            writeAssembly = compilerArgs.writeAssembly,
            warnSymbolShadowing = compilerArgs.warnSymbolShadowing,
            warnImplicitTypeCasts = compilerArgs.warnImplicitTypeCasts,
            quietAll = compilerArgs.quietAll,
            quietAssembler = compilerArgs.quietAssembler,
            showTimings = compilerArgs.showTimings,
            asmListfile = compilerArgs.asmListfile,
            includeSourcelines = compilerArgs.includeSourcelines,
            newCodegen = compilerArgs.newCodegen,
            dumpVariables = compilerArgs.dumpVariables,
            dumpSymbols = compilerArgs.dumpSymbols,
            varsHighBank = compilerArgs.varsHighBank,
            varsGolden = compilerArgs.varsGolden,
            slabsHighBank = compilerArgs.slabsHighBank,
            slabsGolden = compilerArgs.slabsGolden,
            compilationTarget = compilerArgs.compilationTarget,
            breakpointCpuInstruction = compilerArgs.breakpointCpuInstruction,
            printAst1 = compilerArgs.printAst1,
            printAst2 = compilerArgs.printAst2,
            ignoreFootguns = compilerArgs.ignoreFootguns,
            profilingInstrumentation = compilerArgs.profilingInstrumentation,
            traceImports = compilerArgs.traceImports,
            symbolDefs = compilerArgs.symbolDefs,
            sourceDirs = compilerArgs.sourceDirs,
            outputDir = compilerArgs.outputDir.toString(),
            cwd = compilerArgs.cwd.toString()
        )

        val requestJson = DaemonProtocol.encodeRequest(request)
        writer.write(requestJson)
        writer.newLine()
        writer.flush()

        val responseJson = try {
            reader.readLine()
        } catch (e: IOException) {
            System.err.println("daemon connection lost: ${e.message}")
            return null
        }
        if (responseJson == null) {
            System.err.println("daemon connection lost")
            return null
        }

        val response = DaemonProtocol.decodeResponse(responseJson)

        if (response.versionError != null) {
            System.err.println(response.versionError)
            return response
        }

        System.out.print(response.stdout)
        System.out.flush()
        System.err.print(response.stderr)
        System.err.flush()

        return response
    } finally {
        try { channel.close() } catch (_: Exception) {}
    }
}
