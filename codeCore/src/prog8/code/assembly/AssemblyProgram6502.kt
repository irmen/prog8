package prog8.code.assembly

import prog8.code.GENERATED_LABEL_PREFIX
import prog8.code.core.*
import prog8.code.target.C128Target
import prog8.code.target.C64Target
import prog8.code.target.PETTarget


/**
 * The exact 64tass command prog8 runs to assemble the asm file of [name], or null when the output
 * type is not assembled this way. The generated asm header embeds the same command as a comment.
 */
fun assemblerCommand6502(options: CompilationOptions, name: String): List<String>? {
    val outs = OutputFiles.of(options, name)

    val command = when(options.output) {
        OutputType.PRG -> mutableListOf("64tass", "--cbm-prg", "--ascii", "--case-sensitive", "--long-branch",
            "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=${outs.viceMonList()}")
        OutputType.XEX -> mutableListOf("64tass", "--atari-xex", "--case-sensitive", "--long-branch",
            "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=${outs.viceMonList()}")
        OutputType.RAW -> mutableListOf("64tass", "--nostart", "--case-sensitive", "--long-branch",
            "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=${outs.viceMonList()}")
        OutputType.LIBRARY -> mutableListOf("64tass", "--ascii", "--case-sensitive", "--long-branch",
            "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=${outs.viceMonList()}")
        else -> return null
    }

    if(options.warnSymbolShadowing)
        command.add("-Wshadow")
    else
        command.add("-Wno-shadow")

    if(options.asmQuiet)
        command.add("--quiet")

    if(options.asmListfile)
        command.add("--list=${outs.list()}")

    // keep additionalAssemblerOptions BEFORE --output: a target config may contain its own --output
    if(options.compTarget.additionalAssemblerOptions.isNotEmpty())
        command.addAll(options.compTarget.additionalAssemblerOptions)

    command.addAll(listOf("--output", outs.programFile.toString(), outs.asm().toString()))

    if(options.output == OutputType.LIBRARY) {
        if(options.compTarget.name in listOf(C64Target.NAME, C128Target.NAME, PETTarget.NAME))
            command.add("--cbm-prg")
        else
            command.add("--nostart")       // should be headerless bin, because basic has problems doing a normal LOAD"lib",8,1 - need to use BLOAD
    }

    return command
}


class AssemblyProgram6502(
    override val name: String,
    private val options: CompilationOptions,
    private val compTarget: ICompilationTarget) : IAssemblyProgram {

    override val irInstructionCount: Int = 0
    override val irChunkCount: Int = 0
    override val irRegisterCount: Int = 0

    override fun assemble(options: CompilationOptions, errors: IErrorReporter): Boolean {

        val outs = OutputFiles.of(options, name)
        val assemblerCommand = assemblerCommand6502(options, name)
            ?: error("Unsupported output type: ${compTarget.defaultOutputType}")

        if(!options.quiet) {
            when(options.output) {
                OutputType.PRG -> println("\nCreating prg for target ${compTarget.name}.")
                OutputType.XEX -> println("\nCreating xex for target ${compTarget.name}.")
                OutputType.RAW -> println("\nCreating raw binary for target ${compTarget.name}.")
                OutputType.LIBRARY ->
                    if(compTarget.name in listOf(C64Target.NAME, C128Target.NAME, PETTarget.NAME))
                        println("\nCreating binary library file with header for target ${compTarget.name}.")
                    else
                        println("\nCreating binary library file without header for target ${compTarget.name}.")
                else -> { }
            }
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

