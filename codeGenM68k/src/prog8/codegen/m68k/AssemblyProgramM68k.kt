package prog8.codegen.m68k

import prog8.code.assembly.IAssemblyProgram
import prog8.code.core.*
import prog8.code.target.Amiga1200Target
import prog8.code.target.Amiga500Target
import prog8.code.target.Qemu68kTarget
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The exact vasm command prog8 runs to assemble the asm file of [name], or null when the target and
 * output combination is not assembled by vasm directly. The generated asm header embeds the same command.
 */
fun assemblerCommandM68k(options: CompilationOptions, name: String): List<String>? {
    val cpu = when(options.compTarget.cpu) {
        CpuType.M68000 -> "68000"
        CpuType.M68020 -> "68020"
        else -> return null
    }
    val assemblerCpu = if(options.compTarget.name == "amiga500" && options.floats) "68020" else cpu
    val outs = OutputFiles.of(options, name)

    return when(options.output) {
        OutputType.ELF -> mutableListOf(
            "vasmm68k_mot",
            "-m$assemblerCpu",
            "-m68881",  // enable FPU
            "-Felf",
            "-opt-speed",
            "-warnunaligned",
            "-ldots",
            "-spaces",
            "-o", outs.obj().toString(),
            outs.asm().toString()
        ).also { cmd ->
            if (options.asmListfile)
                cmd.addAll(listOf("-L", outs.list().toString()))
            if (options.asmQuiet)
                cmd.add("-quiet")
        }
        OutputType.AMIGAHUNK -> when(options.compTarget) {
            is Amiga500Target -> mutableListOf(
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
                "-o", outs.programFile.toString(),
                outs.asm().toString()
            ).also { cmd ->
                if (options.asmListfile) {
                    cmd.add("-L")
                    cmd.add(outs.list().toString())
                    cmd.add("-Lns")
                }
                if (options.asmQuiet)
                    cmd.add("-quiet")
            }
            is Amiga1200Target -> mutableListOf(
                "vasmm68k_mot",
                "-m$assemblerCpu",
                "-m68881",  // enable FPU
                "-Fhunkexe",
                "-opt-speed",
                "-warnunaligned",
                "-ldots",
                "-spaces",
                "-nosym",       // no debug symbols
                "-o", outs.programFile.toString(),
                outs.asm().toString()
            ).also { cmd ->
                if (options.asmListfile) {
                    cmd.add("-L")
                    cmd.add(outs.list().toString())
                }
                if (options.asmQuiet)
                    cmd.add("-quiet")
            }
            else -> null
        }
        else -> null
    }
}

class AssemblyProgramM68k(override val name: String) : IAssemblyProgram {

    override val irInstructionCount: Int = 0
    override val irChunkCount: Int = 0
    override val irRegisterCount: Int = 0

    private fun elfFile(outs: OutputFiles): Path = outs.programFile

    /**
     * A target supplies its own memory layout as a "link.ld" file: a config-file target keeps it in
     * its library directory (looked at first), a built-in one as a /prog8lib/<targetname>/link.ld
     * resource; anything else falls back to the qemu68k layout.
     */
    private fun resolveLinkerScript(target: ICompilationTarget): String {
        target.libraryPath?.resolve("link.ld")?.takeIf { it.exists() }?.let { return it.readText() }
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
            when (tool) {
                "vasm" -> {
                    System.err.println("Cannot find '${command[0]}' (vasm assembler for m68k). Install it via your package manager if it's on there, or build it from source: http://sun.hasenbraten.de/vasm/")
                }
                "vlink" -> {
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
        val outs = OutputFiles.of(options, name)
        val assembleCmd = assemblerCommandM68k(options, name)
            ?: error("Unsupported output type for m68k: ${options.output}")

        return when(options.output) {
            OutputType.ELF -> {
                // Step 1: assemble to ELF object file
                val objFile = outs.obj()
                if (!runProcess(assembleCmd, options.quiet, "vasm"))
                    return false
                // clean up any leftover ELF/obj files from previous builds
                outs.binFile().deleteIfExists()

                // Step 2: write linker script and link to ELF executable
                val linkScript = outs.linkScript()
                val elfFile = elfFile(outs)
                linkScript.writeText(resolveLinkerScript(options.compTarget))

                val linkCmd = listOf(
                    "vlink",
                    "-b", "elf32m68k",
                    "-n",
                    "-T", linkScript.toString(),
                    "-o", elfFile.toString(),
                    objFile.toString()
                )
                val linkOk = runProcess(linkCmd, options.quiet, "vlink")
                linkScript.deleteIfExists()
                if(linkOk && !options.quiet)
                    println("Executable written to '$elfFile'")
                linkOk
            }
            OutputType.AMIGAHUNK -> {
                // assemble directly to AmigaHunk executable file
                val exefile = outs.programFile
                if (!runProcess(assembleCmd, options.quiet, "vasm"))
                    return false
                if(!options.quiet)
                    println("Executable written to '$exefile'")
                true
            }
            else -> error("Unsupported output type: ${options.output}")
        }
    }
}
