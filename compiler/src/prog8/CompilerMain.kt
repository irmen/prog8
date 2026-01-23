package prog8

import kotlinx.cli.*
import prog8.ast.AstException
import prog8.code.source.ImportFileSystem
import prog8.code.source.ImportFileSystem.expandTilde
import prog8.code.target.CompilationTargets
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8.code.target.getCompilationTargetByName
import prog8.compiler.CompilationResult
import prog8.compiler.CompilerArguments
import prog8.compiler.ErrorReporter
import prog8.compiler.compileProgram
import java.io.File
import java.net.URI
import java.nio.file.*
import java.time.LocalDateTime
import kotlin.io.path.*
import kotlin.system.exitProcess


fun main(args: Array<String>) {

    // NOTE: if your compiler/IDE complains here about "Unresolved reference: buildversion",
    //       it means that you have to run the gradle task once to generate this file.
    //       Do that with this command:  gradle createVersionFile

    val succes = compileMain(args)
    if(!succes)
        exitProcess(1)
}


fun pathFrom(stringPath: String, vararg rest: String): Path  = FileSystems.getDefault().getPath(stringPath, *rest)


private fun compileMain(args: Array<String>): Boolean {
    val cli = ArgParser("prog8c", prefixStyle = ArgParser.OptionPrefixStyle.JVM)
    val asmListfile by cli.option(ArgType.Boolean, fullName = "asmlist", description = "make the assembler produce a listing file as well")
    val breakpointCpuInstruction by cli.option(ArgType.Choice(listOf("brk", "stp"), { it }), fullName = "breakinstr", description = "the CPU instruction to use as well for %breakpoint")
    val bytes2float by cli.option(ArgType.String, fullName = "bytes2float", description = "convert a comma separated list of bytes from the target system to a float value. NOTE: you need to supply a target option too, and also still have to supply a dummy module file name as well!")
    val checkSource by cli.option(ArgType.Boolean, fullName = "check", description = "quickly check program for errors, no output will be produced")
    val symbolDefs by cli.option(ArgType.String, fullName = "D", description = "define assembly symbol(s) with -D SYMBOL=VALUE").multiple()
    val dumpSymbols by cli.option(ArgType.Boolean, fullName = "dumpsymbols", description = "print a dump of the variable declarations and subroutine signatures")
    val dumpVariables by cli.option(ArgType.Boolean, fullName = "dumpvars", description = "print a dump of the variables in the program")
    val libSearch by cli.option(ArgType.String, fullName = "libsearch", description = "search for a regex pattern in the embedded library files")
    val libDump by cli.option(ArgType.String, fullName = "libdump", description = "dump all the embedded library files into the specified output directory")
    val startEmulator1 by cli.option(ArgType.Boolean, fullName = "emu", description = "auto-start emulator after successful compilation")
    val startEmulator2 by cli.option(ArgType.Boolean, fullName = "emu2", description = "auto-start alternative emulator after successful compilation")
    val experimentalCodegen by cli.option(ArgType.Boolean, fullName = "expericodegen", description = "use experimental/alternative codegen")
    val float2bytes by cli.option(ArgType.String, fullName = "float2bytes", description = "convert floating point number to a list of bytes for the target system. NOTE: you need to supply a target option too, and also still have to supply a dummy module file name as well!")
    val ignoreFootguns by cli.option(ArgType.Boolean, fullName = "ignorefootguns", description = "don't print warnings for 'footgun' issues:  'Yes I know I'm treading on mighty thin ice here'.")
    val dontWriteAssembly by cli.option(ArgType.Boolean, fullName = "noasm", description="don't create assembly code")
    val dontOptimize by cli.option(ArgType.Boolean, fullName = "noopt", description = "don't perform code optimizations")
    val outputDir by cli.option(ArgType.String, fullName = "out", description = "directory for output files instead of current directory").default(".")
    val plainText by cli.option(ArgType.Boolean, fullName = "plaintext", description = "output only plain text, no colors or fancy symbols")
    val printAst1 by cli.option(ArgType.Boolean, fullName = "printast1", description = "print out the internal compiler AST")
    val printAst2 by cli.option(ArgType.Boolean, fullName = "printast2", description = "print out the simplified AST that is used for code generation")
    val quietAll by cli.option(ArgType.Boolean, fullName = "quiet", description = "don't print compiler and assembler messages, except warnings and errors")
    val quietAssembler by cli.option(ArgType.Boolean, fullName = "quietasm", description = "don't print assembler messages")
    val slabsGolden by cli.option(ArgType.Boolean, fullName = "slabsgolden", description = "put memory() slabs in 'golden ram' memory area instead of at the end of the program. On the cx16 target this is $0400-07ff. This is unavailable on other systems.")
    val slabsHighBank by cli.option(ArgType.Int, fullName = "slabshigh", description = "put memory() slabs in high memory area instead of at the end of the program. On the cx16 target the value specifies the HIRAM bank to use, on other systems this value is ignored.")
    val dontIncludeSourcelines by cli.option(ArgType.Boolean, fullName = "nosourcelines", description = "do not include original Prog8 source lines in generated asm code")
    val sourceDirs by cli.option(ArgType.String, fullName="srcdirs", description = "list of extra paths, separated with ${File.pathSeparator}, to search in for imported modules").multiple().delimiter(File.pathSeparator)
    val compilationTarget by cli.option(ArgType.String, fullName = "target", description = "target output of the compiler (one of ${CompilationTargets.joinToString(",")} or a custom target properties file) (required)")
    val showTimings by cli.option(ArgType.Boolean, fullName = "timings", description = "show internal compiler timings (for performance analysis)")
    val varsGolden by cli.option(ArgType.Boolean, fullName = "varsgolden", description = "put uninitialized variables in 'golden ram' memory area instead of at the end of the program. On the cx16 target this is $0400-07ff. This is unavailable on other systems.")
    val varsHighBank by cli.option(ArgType.Int, fullName = "varshigh", description = "put uninitialized variables in high memory area instead of at the end of the program. On the cx16 target the value specifies the HIRAM bank to use, on other systems this value is ignored.")
    val startVm by cli.option(ArgType.Boolean, fullName = "vm", description = "run a .p8ir IR source file in the embedded VM")
    val warnSymbolShadowing by cli.option(ArgType.Boolean, fullName = "warnshadow", description="show assembler warnings about symbol shadowing")
    val warnImplicitTypeCasts by cli.option(ArgType.Boolean, fullName = "warnimplicitcasts", description="show compiler warnings about implicit casts from a smaller to a larger type")
    val watchMode by cli.option(ArgType.Boolean, fullName = "watch", description = "continuous compilation mode (watch for file changes)")
    val version by cli.option(ArgType.Boolean, fullName = "version", description = "print compiler version and exit")
    val moduleFiles by cli.argument(ArgType.String, fullName = "modules", description = "main module file(s) to compile").optional().multiple(999)

    try {
        cli.parse(args)
    } catch (e: IllegalStateException) {
        banner()
        System.err.println(e.message)
        return false
    }

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
    if(!outputPath.toFile().isDirectory) {
        System.err.println("Output path doesn't exist")
        return false
    }

    val faultyOption = moduleFiles.firstOrNull { it.startsWith('-') }
    if(faultyOption!=null) {
        System.err.println("Unknown command line option given: $faultyOption")
        return false
    }

    val srcdirs = sourceDirs.map { expandTilde(it) }.toMutableList()
    if(sourceDirs.firstOrNull()!=".")
        srcdirs.add(0, ".")

    if(startVm==null) {
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
        runVm(moduleFiles.first(), quietAll==true)
        return true
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
                    experimentalCodegen == true,
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
                    ignoreFootguns == true,
                    processedSymbols,
                    srcdirs,
                    outputPath,
                    errors = ErrorReporter(txtcolors)
                )
                val compilationResult = compileProgram(compilerArgs)
                ImportFileSystem.clearCaches()

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
                    experimentalCodegen == true,
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
                    ignoreFootguns == true,
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

            if (startEmulator1 == true)
                compilationResult.compilationOptions.compTarget.launchEmulator(1, programNameInPath, quietAll==true)
            else if (startEmulator2 == true)
                compilationResult.compilationOptions.compTarget.launchEmulator(2, programNameInPath, quietAll==true)
        }
    }

    return true
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

fun runVm(irFilename: String, quiet: Boolean) {
    val irFile = Path(irFilename)
    val vmdef = VMTarget()
    vmdef.launchEmulator(0, irFile, quiet)
}



private fun scanLibraryFiles(dump: String?, searchPattern: String?) {
    val libraryPrefix = "/prog8lib"

    val dumpPath = if(dump!=null) pathFrom(dump) else null
    val pattern = searchPattern?.toRegex(RegexOption.IGNORE_CASE)
    if(pattern!=null) {
        println("You can also have a look in the documentation for the libraries at https://prog8.readthedocs.io/en/latest/libraries.html")
        println("The library source files are available in the Github repository at https://github.com/irmen/prog8/tree/master/compiler/res/prog8lib")
        println("Searching for pattern '$pattern' in embedded library files.\n")
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

    fun search(path: Path) {
        val hits = mutableListOf<String>()
        path.useLines { lines ->
            lines.forEachIndexed { index, line ->
                if (pattern!!.containsMatchIn(line)) {
                    hits.add("${(index + 1).toString().padStart(6)}:  ${line.trimStart()}")
                }
            }
        }
        if(hits.isNotEmpty()) {
            println("Found in Library file '${path.absolutePathString().drop(libraryPrefix.length+1)}':")
            hits.forEach(::println)
            println()
        }
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
        Files.walk(dir).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { path ->
                if(pattern!=null)
                    search(path)
                else
                    dump(path)
            }
        }
    }
}