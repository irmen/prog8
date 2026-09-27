package prog8.codegen.m68k

import prog8.code.assembly.IAssemblyProgram
import prog8.code.core.*
import prog8.code.target.Amiga1200Target
import prog8.code.target.Amiga500Target
import prog8.code.target.Qemu68kTarget
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

class AssemblyProgramM68k(override val name: String, private val outputDir: Path) : IAssemblyProgram {

    override val irInstructionCount: Int = 0
    override val irChunkCount: Int = 0
    override val irRegisterCount: Int = 0

    private val assemblyFile = outputDir.resolve("$name.asm")

    fun elfFile(): Path = outputDir.resolve("$name.elf")

    /**
     * A target supplies its own memory layout as a "link.ld" file.  A target defined by a
     * config file (i.e. living outside of the compiler) keeps it in its library directory, so
     * that is looked at first.  Targets built into the compiler are looked up as a
     * /prog8lib/<targetname>/link.ld resource.  Anything else falls back to the qemu68k layout.
     */
    private fun resolveLinkerScript(target: ICompilationTarget): String {
        target.libraryPath?.resolve("link.ld")?.takeIf { Files.isReadable(it) }?.let { return it.readText() }
        val targetResource = "/prog8lib/${target.name}/link.ld"
        AssemblyProgramM68k::class.java.getResource(targetResource)?.let { return it.readText() }
        return AssemblyProgramM68k::class.java.getResource("/prog8lib/${Qemu68kTarget.NAME}/link.ld")?.readText()
            ?: error("cannot find $targetResource resource, and no qemu68k fallback either")
    }

    private fun runProcess(command: List<String>, quiet: Boolean, tool: String? = null): Boolean {
        val proc = ProcessBuilder(command).redirectErrorStream(true)
        if (quiet)
            proc.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        val process = try {
            proc.start()
        } catch (e: Exception) {
            when {
                tool=="vasm" -> {
                    System.err.println("Cannot find '${command[0]}' (vasm assembler for m68k). Install it via your package manager if it's on there, or build it from source: http://sun.hasenbraten.de/vasm/")
                }
                tool=="vlink" -> {
                    System.err.println("Cannot find 'vlink' (linker). Install it via your package manager if it's on there, or build it from source: http://sun.hasenbraten.de/vlink/")
                }
                else -> {
                    System.err.println("process failed to start: ${e.message}")
                }
            }
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
        val cpu = when(options.compTarget.cpu) {
            CpuType.M68000 -> "68000"
            CpuType.M68020 -> "68020"
            else -> error("invalid cpu type for m68k codegen ${options.compTarget.cpu}")
        }
        val assemblerCpu = if(options.compTarget.name == "amiga500" && options.floats) "68020" else cpu

        when(options.output) {
            OutputType.ELF -> {
                // Step 1: assemble to ELF object file
                val objFile = outputDir.resolve("$name.o")
                val listFile = outputDir.resolve("$name.list")
                val assembleCmd = mutableListOf(
                    "vasmm68k_mot",
                    "-m$assemblerCpu",
                    "-m68881",  // enable FPU
                    "-Felf",
                    "-opt-speed",
                    "-warnunaligned",
                    "-ldots",
                    "-spaces",
                    "-o", objFile.toString(),
                    assemblyFile.toString()
                )
                if (options.asmListfile)
                    assembleCmd.addAll(listOf("-L", listFile.toString()))
                if (options.asmQuiet)
                    assembleCmd.add("-quiet")
                if (!runProcess(assembleCmd, options.quiet, "vasm"))
                    return false
                // clean up any leftover ELF/obj files from previous builds
                Files.deleteIfExists(outputDir.resolve("$name.bin"))

                // Step 2: write linker script and link to ELF executable
                val linkScript = outputDir.resolve("$name.link.ld")
                val elfFile = elfFile()
                Files.writeString(linkScript, resolveLinkerScript(options.compTarget))

                val linkCmd = listOf(
                    "vlink",
                    "-b", "elf32m68k",
                    "-n",
                    "-T", linkScript.toString(),
                    "-o", elfFile.toString(),
                    objFile.toString()
                )
                val linkOk = runProcess(linkCmd, options.quiet, "vlink")
                Files.deleteIfExists(linkScript)
                if(linkOk && !options.quiet)
                    println("Executable written to '$elfFile'")
                return linkOk
            }
            OutputType.AMIGAHUNK -> {
                // Step 1: assemble directly to AmigaHunk executable file
                val exefile = outputDir.resolve(name)
                val listfile = outputDir.resolve("$name.list")
                val assembleCmd = when(options.compTarget) {
                    is Amiga500Target -> {
                        // amiga 500 with kickstart 1.3
                        mutableListOf(
                            "vasmm68k_mot",
                            "-m$assemblerCpu",
                            *if(options.floats) arrayOf("-m68881") else emptyArray(),
                            "-Fhunkexe",
                            "-kick1hunks",   // old hunk format compatible with AmigaDOS 1.3
                            "-opt-speed",
                            "-warnunaligned",
                            "-ldots",
                            "-spaces",
                            "-nosym",       // no debug symbols
                            "-o", exefile.toString(),
                            assemblyFile.toString()
                        ).also { cmd ->
                            if (options.asmListfile) {
                                cmd.add("-L")
                                cmd.add(listfile.toString())
                                cmd.add("-Lns")
                            }
                            if (options.asmQuiet)
                                cmd.add("-quiet")
                        }
                    }
                    is Amiga1200Target -> {
                        // amiga 1200 with 68020 and optional FPU
                        mutableListOf(
                            "vasmm68k_mot",
                            "-m$assemblerCpu",
                            "-m68881",  // enable FPU
                            "-Fhunkexe",
                            "-opt-speed",
                            "-warnunaligned",
                            "-ldots",
                            "-spaces",
                            "-nosym",       // no debug symbols
                            "-o", exefile.toString(),
                            assemblyFile.toString()
                        ).also { cmd ->
                            if (options.asmListfile) {
                                cmd.add("-L")
                                cmd.add(listfile.toString())
                            }
                            if (options.asmQuiet)
                                cmd.add("-quiet")
                        }
                    }
                    else -> error("unsupported target for AMIGAHUNK output: ${options.compTarget.name}")
                }
                if (!runProcess(assembleCmd, options.quiet, "vasm"))
                    return false
                if(!options.quiet)
                    println("Executable written to '$exefile'")
                return true
            }
            else -> error("Unsupported output type: ${options.output}")
        }
    }
}
