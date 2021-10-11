package prog8.compiler.target.cbm

import prog8.compiler.CompilationOptions
import prog8.compiler.OutputType
import prog8.compiler.target.IAssemblyProgram
import prog8.compiler.target.generatedLabelPrefix
import java.nio.file.Path


internal const val viceMonListPostfix = "vice-mon-list"

class AssemblyProgram(
        override val valid: Boolean,
        override val name: String,
        outputDir: Path,
        private val compTarget: String) : IAssemblyProgram {

    private val assemblyFile = outputDir.resolve("$name.asm")
    private val prgFile = outputDir.resolve("$name.prg")
    private val binFile = outputDir.resolve("$name.bin")
    private val viceMonListFile = outputDir.resolve("$name.$viceMonListPostfix")

    override fun assemble(options: CompilationOptions): Int {
        // add "-Wlong-branch"  to see warnings about conversion of branch instructions to jumps (default = do this silently)
        val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "--long-branch",
                "-Wall", "-Wno-strict-bool", "-Wno-shadow", // "-Werror",
                "--dump-labels", "--vice-labels", "-l", viceMonListFile.toString(), "--no-monitor")

        val outFile = when (options.output) {
            OutputType.PRG -> {
                command.add("--cbm-prg")
                println("\nCreating prg for target $compTarget.")
                prgFile
            }
            OutputType.RAW -> {
                command.add("--nostart")
                println("\nCreating raw binary for target $compTarget.")
                binFile
            }
        }
        command.addAll(listOf("--output", outFile.toString(), assemblyFile.toString()))

        val proc = ProcessBuilder(command).inheritIO().start()
        val result = proc.waitFor()
        if (result == 0) {
            removeGeneratedLabelsFromMonlist()
            generateBreakpointList()
        }
        return result
    }

    private fun removeGeneratedLabelsFromMonlist() {
        val pattern = Regex("""al (\w+) \S+${generatedLabelPrefix}.+?""")
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
        breakpoints.add(0, "; vice monitor breakpoint list now follows")
        breakpoints.add(1, "; $num breakpoints have been defined")
        breakpoints.add(2, "del")
        viceMonListFile.toFile().appendText(breakpoints.joinToString("\n") + "\n")
    }
}
