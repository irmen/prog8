package prog8.compiler.target.c64

import prog8.compiler.CompilationOptions
import prog8.compiler.OutputType
import java.io.File
import kotlin.system.exitProcess

class AssemblyProgram(val name: String) {
    private val assemblyFile = "$name.asm"
    private val viceMonListFile = "$name.vice-mon-list"

    fun assemble(options: CompilationOptions) {
        println("Generating machine code program...")

        val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "--long-branch", "-Wall", "-Wno-strict-bool", "-Wlong-branch",
                "-Werror", "-Wno-error=long-branch", "--dump-labels", "--vice-labels", "-l", viceMonListFile, "--no-monitor")

        val outFile = when(options.output) {
            OutputType.PRG -> {
                command.add("--cbm-prg")
                println("\nCreating C-64 prg.")
                "$name.prg"
            }
            OutputType.RAW -> {
                command.add("--nostart")
                println("\nCreating raw binary.")
                "$name.bin"
            }
        }
        command.addAll(listOf("--output", outFile, assemblyFile))

        val proc = ProcessBuilder(command).inheritIO().start()
        val result = proc.waitFor()
        if(result!=0) {
            System.err.println("assembler failed with returncode $result")
            exitProcess(result)
        }

        generateBreakpointList()
    }

    private fun generateBreakpointList() {
        // builds list of breakpoints, appends to monitor list file
        val breakpoints = mutableListOf<String>()
        val pattern = Regex("""al (\w+) \S+_prog8_breakpoint_\d+.?""")      // gather breakpoints by the source label that's generated for them
        for(line in File(viceMonListFile).readLines()) {
            val match = pattern.matchEntire(line)
            if(match!=null)
            breakpoints.add("break \$" + match.groupValues[1])
        }
        val num = breakpoints.size
        breakpoints.add(0, "; vice monitor breakpoint list now follows")
        breakpoints.add(1, "; $num breakpoints have been defined")
        breakpoints.add(2, "del")
        File(viceMonListFile).appendText(breakpoints.joinToString("\n")+"\n")
    }
}