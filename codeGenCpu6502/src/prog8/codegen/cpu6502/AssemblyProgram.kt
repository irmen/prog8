package prog8.codegen.cpu6502

import prog8.code.GENERATED_LABEL_PREFIX
import prog8.code.IAssemblyProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.ICompilationTarget
import prog8.code.core.IErrorReporter
import prog8.code.core.OutputType
import prog8.code.target.C128Target
import prog8.code.target.C64Target
import prog8.code.target.PETTarget
import java.nio.file.Path


internal class AssemblyProgram(
        override val name: String,
        outputDir: Path,
        private val compTarget: ICompilationTarget) : IAssemblyProgram {

    private val assemblyFile = outputDir.resolve("$name.asm")
    private val prgFile = outputDir.resolve("$name.prg")        // CBM prg executable program
    private val xexFile = outputDir.resolve("$name.xex")        // Atari xex executable program
    private val binFile = outputDir.resolve("$name.bin")
    private val viceMonListFile = outputDir.resolve(C64Target.viceMonListName(name))
    private val listFile = outputDir.resolve("$name.list")

    override fun assemble(options: CompilationOptions, errors: IErrorReporter): Boolean {

        val assemblerCommand: List<String>

        when(options.output) {
            OutputType.PRG -> {
                // CBM machines .prg generation.

                val command = mutableListOf("64tass", "--cbm-prg", "--ascii", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=$viceMonListFile")

                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile) {
                    command.add("--list=$listFile")
                }

                command.addAll(listOf("--output", prgFile.toString(), assemblyFile.toString()))
                assemblerCommand = command
                if(!options.quiet)
                    println("\nCreating prg for target ${compTarget.name}.")
            }
            OutputType.XEX -> {
                // Atari800XL .xex generation.

                val command = mutableListOf("64tass", "--atari-xex", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=$viceMonListFile")

                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile)
                    command.add("--list=$listFile")

                command.addAll(listOf("--output", xexFile.toString(), assemblyFile.toString()))
                assemblerCommand = command
                if(!options.quiet)
                    println("\nCreating xex for target ${compTarget.name}.")
            }
            OutputType.RAW -> {
                // Neo6502/headerless raw program generation.
                val command = mutableListOf("64tass", "--nostart", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=$viceMonListFile")

                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile)
                    command.add("--list=$listFile")

                command.addAll(listOf("--output", binFile.toString(), assemblyFile.toString()))
                assemblerCommand = command
                if(!options.quiet)
                    println("\nCreating raw binary for target ${compTarget.name}.")
            }
            OutputType.LIBRARY -> {
                // CBM machines library (.bin) generation (with or without 2 byte load address header depending on the compilation target machine)

                val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=$viceMonListFile")

                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile)
                    command.add("--list=$listFile")

                if(compTarget.name in listOf(C64Target.NAME, C128Target.NAME, PETTarget.NAME)) {
                    if(!options.quiet)
                        println("\nCreating binary library file with header for target ${compTarget.name}.")
                    command.add("--cbm-prg")
                } else {
                    if(!options.quiet)
                        println("\nCreating binary library file without header for target ${compTarget.name}.")
                    command.add("--nostart")       // should be headerless bin, because basic has problems doing a normal LOAD"lib",8,1 - need to use BLOAD
                }

                command.addAll(listOf("--output", binFile.toString(), assemblyFile.toString()))
                assemblerCommand = command
            }
        }

        if(options.compTarget.additionalAssemblerOptions!=null)
            assemblerCommand.add(options.compTarget.additionalAssemblerOptions!!)

        val proc = ProcessBuilder(assemblerCommand)
        if(!options.quiet)
            proc.inheritIO()
        val process = proc.start()
        val result = process.waitFor()
        if (result == 0) {
            removeGeneratedLabelsFromMonlist()
            generateBreakpointList()
        }
        return result==0
    }

    private fun removeGeneratedLabelsFromMonlist() {
        val pattern = Regex("""al (\w+) \S+$GENERATED_LABEL_PREFIX.+?""")
        val lines = viceMonListFile.toFile().readLines()
        viceMonListFile.toFile().outputStream().bufferedWriter().use {
            for (line in lines) {
                if(pattern.matchEntire(line)==null)
                    it.write(line+"\n")
            }
        }
    }

    private fun generateBreakpointList() {
        // builds list of breakpoints, appends to monitor list file
        val breakpoints = mutableListOf<String>()
        val pattern = Regex("""al (\w+) \S+_prog8_breakpoint_\d+.?""")      // gather breakpoints by the source label that's generated for them
        for (line in viceMonListFile.toFile().readLines()) {
            val match = pattern.matchEntire(line)
            if (match != null)
                breakpoints.add("break \$" + match.groupValues[1])
        }
        val num = breakpoints.size
        breakpoints.add(0, "; breakpoint list now follows")
        breakpoints.add(1, "; $num breakpoints have been defined")
        breakpoints.add(2, "del")
        viceMonListFile.toFile().appendText(breakpoints.joinToString("\n") + "\n")
    }
}
