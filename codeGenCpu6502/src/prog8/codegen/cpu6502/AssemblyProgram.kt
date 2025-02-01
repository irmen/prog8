package prog8.codegen.cpu6502

import prog8.code.ast.PtLabel
import prog8.code.core.*
import prog8.code.target.AtariTarget
import prog8.code.target.C64Target
import prog8.code.target.Neo6502Target
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
    private val targetWithoutBreakpointsForEmulator = arrayOf(AtariTarget.NAME, Neo6502Target.NAME)

    override fun assemble(options: CompilationOptions, errors: IErrorReporter): Boolean {

        val assemblerCommand: List<String>

        when(compTarget.programType) {
            ProgramType.CBMPRG -> {
                // CBM machines .prg generation.

                val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "--long-branch",
                    "-Wall", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=$viceMonListFile")

                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile) {
                    command.add("--list=$listFile")
                }

                val outFile = when (options.output) {
                    OutputType.PRG -> {
                        command.add("--cbm-prg")
                        println("\nCreating prg for target ${compTarget.name}.")
                        prgFile
                    }
                    OutputType.RAW -> {
                        command.add("--nostart")
                        println("\nCreating raw binary for target ${compTarget.name}.")
                        binFile
                    }
                    OutputType.LIBRARY -> {
                        command.add("--cbm-prg")       // include the 2-byte PRG header on library .bins, so they can be easily loaded on the correct memory address even on C64
                        println("\nCreating binary library file for target ${compTarget.name}.")
                        binFile
                    }
                    else -> throw AssemblyError("invalid output type")
                }

                command.addAll(listOf("--output", outFile.toString(), assemblyFile.toString()))
                assemblerCommand = command

            }
            ProgramType.ATARIXEX -> {
                // Atari800XL .xex generation.

                val command = mutableListOf("64tass", "--case-sensitive", "--long-branch", "-Wall", "--no-monitor")

                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile)
                    command.add("--list=$listFile")

                val outFile = when (options.output) {
                    OutputType.XEX -> {
                        command.add("--atari-xex")
                        println("\nCreating xex for target ${compTarget.name}.")
                        xexFile
                    }
                    OutputType.RAW -> {
                        command.add("--nostart")
                        println("\nCreating raw binary for target ${compTarget.name}.")
                        binFile
                    }
                    else -> throw AssemblyError("invalid output type")
                }
                command.addAll(listOf("--output", outFile.toString(), assemblyFile.toString()))
                assemblerCommand = command
            }
            ProgramType.NEORAW -> {
                // Neo6502 raw program generation.

                if(options.output!=OutputType.RAW || options.loadAddress!=0x0800u || options.launcher!=CbmPrgLauncherType.NONE) {
                    throw AssemblyError("invalid program compilation options. Neo6502 requires %output raw, %launcher none, %address $0800")
                }

                val command = mutableListOf("64tass", "--case-sensitive", "--long-branch", "-Wall", "--no-monitor")

                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile)
                    command.add("--list=$listFile")

                val outFile = when (options.output) {
                    OutputType.RAW -> {
                        command.add("--nostart")
                        println("\nCreating raw binary for target ${compTarget.name}.")
                        binFile
                    }
                    else -> throw AssemblyError("invalid output type, need 'raw'")
                }
                command.addAll(listOf("--output", outFile.toString(), assemblyFile.toString()))
                assemblerCommand = command
            }
            else -> throw AssemblyError("invalid program type")
        }

        if(options.compTarget.additionalAssemblerOptions!=null)
            assemblerCommand.add(options.compTarget.additionalAssemblerOptions!!)

        val proc = ProcessBuilder(assemblerCommand).inheritIO().start()
        val result = proc.waitFor()
        if (result == 0 && compTarget.name !in targetWithoutBreakpointsForEmulator) {
            removeGeneratedLabelsFromMonlist()
            generateBreakpointList()
        }
        return result==0
    }

    private fun removeGeneratedLabelsFromMonlist() {
        val pattern = Regex("""al (\w+) \S+${PtLabel.GENERATED_LABEL_PREFIX}.+?""")
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
