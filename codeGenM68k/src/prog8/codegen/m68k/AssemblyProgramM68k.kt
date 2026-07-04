package prog8.codegen.m68k

import prog8.code.assembly.IAssemblyProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.ICompilationTarget
import prog8.code.core.IErrorReporter
import java.nio.file.Files
import java.nio.file.Path

class AssemblyProgramM68k(
    override val name: String,
    private val outputDir: Path,
    private val compTarget: ICompilationTarget
) : IAssemblyProgram {

    override val irInstructionCount: Int = 0
    override val irChunkCount: Int = 0
    override val irRegisterCount: Int = 0

    private val assemblyFile = outputDir.resolve("$name.asm")

    fun elfFile(): Path = outputDir.resolve("$name.elf")

    private fun runProcess(command: List<String>, quiet: Boolean): Boolean {
        val proc = ProcessBuilder(command).redirectErrorStream(true)
        if (quiet)
            proc.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        val process = try {
            proc.start()
        } catch (e: Exception) {
            System.err.println("process failed to start: ${e.message}")
            return false
        }
        if (!quiet) {
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { println(it) }
            }
        }
        return process.waitFor() == 0
    }

    override fun assemble(options: CompilationOptions, errors: IErrorReporter): Boolean {
        val objFile = outputDir.resolve("$name.o")

        // Step 1: assemble to ELF object file
        val assembleCmd = mutableListOf(
            "vasmm68k_mot",
            "-Felf",
            "-no-opt",
            "-ldots",
            "-o", objFile.toString(),
            assemblyFile.toString()
        )
        if (options.asmQuiet)
            assembleCmd.add("-quiet")
        if (!runProcess(assembleCmd, options.quiet))
            return false

        // Step 2: write linker script and link to ELF executable
        val linkScript = outputDir.resolve("$name.link.ld")
        val elfFile = elfFile()
        val resourceUrl = AssemblyProgramM68k::class.java.getResource("/prog8lib/qemu68k/link.ld")
            ?: error("cannot find /prog8lib/qemu68k/link.ld resource")
        Files.writeString(linkScript, resourceUrl.readText())

        val linkCmd = listOf(
            "vlink",
            "-b", "elf32m68k",
            "-n",
            "-T", linkScript.toString(),
            "-o", elfFile.toString(),
            objFile.toString()
        )
        val linkOk = runProcess(linkCmd, options.quiet)
        Files.deleteIfExists(linkScript)
        return linkOk
    }
}
