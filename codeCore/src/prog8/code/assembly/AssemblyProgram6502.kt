package prog8.code.assembly

import prog8.code.GENERATED_LABEL_PREFIX
import prog8.code.core.*
import prog8.code.target.C128Target
import prog8.code.target.C64Target
import prog8.code.target.PETTarget
import java.nio.file.Path


class AssemblyProgram6502(
    override val name: String,
    private val options: CompilationOptions,
    private val compTarget: ICompilationTarget) : IAssemblyProgram {

    override val irInstructionCount: Int = 0
    override val irChunkCount: Int = 0
    override val irRegisterCount: Int = 0

    override fun assemble(options: CompilationOptions, errors: IErrorReporter): Boolean {

        val assemblerCommand: List<String>
        val outs = OutputFiles.of(options, name)

        fun addRemainingOptions(command: MutableList<String>, program: Path, assembly: Path): List<String> {
            // keep additionalAssemblerOptions BEFORE --output: a target config may contain its own --output
            if(options.compTarget.additionalAssemblerOptions.isNotEmpty())
                command.addAll(options.compTarget.additionalAssemblerOptions)

            command.addAll(listOf("--output", program.toString(), assembly.toString()))
            return command
        }

        when(options.output) {
            OutputType.PRG -> {
                // CBM machines .prg generation.

                val command = mutableListOf("64tass", "--cbm-prg", "--ascii", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=${outs.viceMonList()}")

                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile) {
                    command.add("--list=${outs.list()}")
                }

                assemblerCommand = addRemainingOptions(command, outs.programFile, outs.asm())
                if(!options.quiet)
                    println("\nCreating prg for target ${compTarget.name}.")
            }
            OutputType.XEX -> {
                // Atari800XL .xex generation.

                val command = mutableListOf("64tass", "--atari-xex", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=${outs.viceMonList()}")

                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile)
                    command.add("--list=${outs.list()}")

                assemblerCommand = addRemainingOptions(command, outs.programFile, outs.asm())
                if(!options.quiet)
                    println("\nCreating xex for target ${compTarget.name}.")
            }
            OutputType.RAW -> {
                // Neo6502/headerless raw program generation.
                val command = mutableListOf("64tass", "--nostart", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=${outs.viceMonList()}")

                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile)
                    command.add("--list=${outs.list()}")

                assemblerCommand = addRemainingOptions(command, outs.programFile, outs.asm())
                if(!options.quiet)
                    println("\nCreating raw binary for target ${compTarget.name}.")
            }
            OutputType.LIBRARY -> {
                // CBM machines library (.bin) generation (with or without 2 byte load address header depending on the compilation target machine)

                val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=${outs.viceMonList()}")

                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile)
                    command.add("--list=${outs.list()}")

                if(compTarget.name in listOf(C64Target.NAME, C128Target.NAME, PETTarget.NAME)) {
                    if(!options.quiet)
                        println("\nCreating binary library file with header for target ${compTarget.name}.")
                    command.add("--cbm-prg")
                } else {
                    if(!options.quiet)
                        println("\nCreating binary library file without header for target ${compTarget.name}.")
                    command.add("--nostart")       // should be headerless bin, because basic has problems doing a normal LOAD"lib",8,1 - need to use BLOAD
                }

                assemblerCommand = addRemainingOptions(command, outs.programFile, outs.asm())
            }
            else -> error("Unsupported output type: ${compTarget.defaultOutputType}")
        }

        val proc = ProcessBuilder(assemblerCommand)
            .redirectErrorStream(true)

        if (options.quiet) {
            proc.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        }

        val process = try {
            proc.start()
        } catch (e: Exception) {
            System.err.println("daemon: assembler failed to start: ${e.message}")
            return false
        }

        if (!options.quiet) {
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine {
                    println(it)
                }
            }
        }

        val result = process.waitFor()
        if (result == 0) {
            removeGeneratedLabelsFromMonlist(outs)
            generateBreakpointList(outs)
        }
        return result==0
    }

    private fun removeGeneratedLabelsFromMonlist(outs: OutputFiles) {
        val pattern = Regex("""al (\w+) \S+$GENERATED_LABEL_PREFIX.+?""")
        val lines = outs.viceMonList().toFile().readLines()
        outs.viceMonList().toFile().outputStream().bufferedWriter().use {
            for (line in lines) {
                if(pattern.matchEntire(line)==null)
                    it.write(line+"\n")
            }
        }
    }

    private fun generateBreakpointList(outs: OutputFiles) {
        // builds list of breakpoints, appends to monitor list file
        val breakpoints = mutableListOf<String>()
        val pattern = Regex("""al (\w+) \S+_prog8_breakpoint_\d+.?""")      // gather breakpoints by the source label that's generated for them
        for (line in outs.viceMonList().toFile().readLines()) {
            val match = pattern.matchEntire(line)
            if (match != null)
                breakpoints.add("break $" + match.groupValues[1])
        }
        val num = breakpoints.size
        breakpoints.add(0, "; breakpoint list now follows")
        breakpoints.add(1, "; $num breakpoints have been defined")
        breakpoints.add(2, "del")
        outs.viceMonList().toFile().appendText(breakpoints.joinToString("\n") + "\n")
    }
}

