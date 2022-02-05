package prog8.codegen.cpu6502

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import prog8.compilerinterface.CompilationOptions
import prog8.compilerinterface.IAssemblyProgram
import prog8.compilerinterface.OutputType
import prog8.compilerinterface.viceMonListName
import prog8.parser.SourceCode
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile


class AssemblyProgram(
        override val valid: Boolean,
        override val name: String,
        outputDir: Path,
        private val compTarget: String) : IAssemblyProgram {

    private val assemblyFile = outputDir.resolve("$name.asm")
    private val prgFile = outputDir.resolve("$name.prg")
    private val binFile = outputDir.resolve("$name.bin")
    private val viceMonListFile = outputDir.resolve(viceMonListName(name))
    private val listFile = outputDir.resolve("$name.list")

    override fun assemble(options: CompilationOptions): Int {
        // add "-Wlong-branch"  to see warnings about conversion of branch instructions to jumps (default = do this silently)
        val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "--long-branch",
                "-Wall", "-Wno-strict-bool", "-Wno-shadow", // "-Werror",
                "--dump-labels", "--vice-labels", "--labels=$viceMonListFile", "--no-monitor"
            )

        if(options.asmQuiet)
            command.add("--quiet")

        if(options.asmListfile)
            command.add("--list=$listFile")

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


internal fun loadAsmIncludeFile(filename: String, source: SourceCode): Result<String, NoSuchFileException> {
    return if (filename.startsWith(SourceCode.libraryFilePrefix)) {
        return com.github.michaelbull.result.runCatching {
            SourceCode.Resource("/prog8lib/${filename.substring(SourceCode.libraryFilePrefix.length)}").text
        }.mapError { NoSuchFileException(File(filename)) }
    } else {
        val sib = Path(source.origin).resolveSibling(filename)
        if (sib.isRegularFile())
            Ok(SourceCode.File(sib).text)
        else
            Ok(SourceCode.File(Path(filename)).text)
    }
}
