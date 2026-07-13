package prog8.codegen.m68k

import prog8.code.assembly.IAssemblyProgram
import prog8.code.core.*
import prog8.code.target.Amiga500Target
import java.nio.file.Files
import java.nio.file.Path

class AssemblyProgramM68k(override val name: String, private val outputDir: Path) : IAssemblyProgram {

    override val irInstructionCount: Int = 0
    override val irChunkCount: Int = 0
    override val irRegisterCount: Int = 0

    private val assemblyFile = outputDir.resolve("$name.asm")

    fun elfFile(): Path = outputDir.resolve("$name.elf")

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

        val loadAddr = options.compTarget.PROGRAM_LOAD_ADDRESS.toInt()
        when(options.output) {
            OutputType.RAW -> {
                val rawFile = outputDir.resolve("$name.bin")
                val assembleCmd = mutableListOf(
                    "vasmm68k_mot",
                    "-m$cpu",
                    "-m68881",  // enable FPU
                    "-Fbin",
                    "-opt-speed",
                    "-ldots",
                    "-spaces",
                    "-join=0x${loadAddr.toString(16)}",
                    "-o", rawFile.toString(),
                    assemblyFile.toString()
                )
                if (options.asmQuiet)
                    assembleCmd.add("-quiet")
                // clean up any leftover ELF/obj files from previous builds
                Files.deleteIfExists(outputDir.resolve("$name.elf"))
                Files.deleteIfExists(outputDir.resolve("$name.o"))
                val ok = runProcess(assembleCmd, options.quiet, "vasm")
                if(ok && !options.quiet)
                    println("Executable written to $rawFile")
                return ok
            }
            OutputType.ELF -> {
                // Step 1: assemble to ELF object file
                val objFile = outputDir.resolve("$name.o")
                val assembleCmd = mutableListOf(
                    "vasmm68k_mot",
                    "-m$cpu",
                    "-m68881",  // enable FPU
                    "-Felf",
                    "-opt-speed",
                    "-ldots",
                    "-spaces",
                    "-o", objFile.toString(),
                    assemblyFile.toString()
                )
                if (options.asmQuiet)
                    assembleCmd.add("-quiet")
                if (!runProcess(assembleCmd, options.quiet, "vasm"))
                    return false
                // clean up any leftover ELF/obj files from previous builds
                Files.deleteIfExists(outputDir.resolve("$name.bin"))

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
                val linkOk = runProcess(linkCmd, options.quiet, "vlink")
                Files.deleteIfExists(linkScript)
                if(linkOk && !options.quiet)
                    println("Executable written to $elfFile")
                return linkOk
            }
            OutputType.AMIGAHUNK -> {
                // Step 1: assemble directly to AmigaHunk executable file
                val exefile = outputDir.resolve(name)
                val assembleCmd = when(options.compTarget) {
                    is Amiga500Target -> {
                        // amiga 500 with kickstart 1.3
                        mutableListOf(
                            "vasmm68k_mot",
                            "-m$cpu",
                            "-Fhunkexe",
                            "-kick1hunks",   // old hunk format compatible with AmigaDOS 1.3
                            "-opt-speed",
                            "-ldots",
                            "-spaces",
                            "-nosym",       // no debug symbols
                            "-o", exefile.toString(),
                            assemblyFile.toString()
                        )
                    }
                    else -> {
                        // assume at least an amiga 1200 with 68020 and optional FPU
                        mutableListOf(
                            "vasmm68k_mot",
                            "-m$cpu",
                            "-m68881",  // enable FPU
                            "-Fhunkexe",
                            "-opt-speed",
                            "-ldots",
                            "-spaces",
                            "-o", exefile.toString(),
                            assemblyFile.toString()
                        )
                    }
                }
                if (options.asmQuiet)
                    assembleCmd.add("-quiet")
                if (!runProcess(assembleCmd, options.quiet, "vasm"))
                    return false
                if(!options.quiet)
                    println("Executable written to $exefile")
                return true
            }
            else -> error("Unsupported output type: ${options.output}")
        }
    }
}
