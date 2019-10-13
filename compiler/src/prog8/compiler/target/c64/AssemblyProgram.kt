package prog8.compiler.target.c64

import prog8.compiler.CompilationOptions
import prog8.compiler.OutputType
import java.nio.file.Path
import kotlin.system.exitProcess

class AssemblyProgram(val name: String, outputDir: Path) {
    private val assemblyFile = outputDir.resolve("$name.asm")
    private val prgFile = outputDir.resolve("$name.prg")
    private val binFile = outputDir.resolve("$name.bin")
    private val viceMonListFile = outputDir.resolve("$name.vice-mon-list")

    companion object {
        // 6502 opcodes (including aliases and illegal opcodes), these cannot be used as variable or label names
        val opcodeNames = setOf("adc", "ahx", "alr", "anc", "and", "ane", "arr", "asl", "asr", "axs", "bcc", "bcs",
                "beq", "bge", "bit", "blt", "bmi", "bne", "bpl", "brk", "bvc", "bvs", "clc",
                "cld", "cli", "clv", "cmp", "cpx", "cpy", "dcm", "dcp", "dec", "dex", "dey",
                "eor", "gcc", "gcs", "geq", "gge", "glt", "gmi", "gne", "gpl", "gvc", "gvs",
                "inc", "ins", "inx", "iny", "isb", "isc", "jam", "jmp", "jsr", "lae", "las",
                "lax", "lda", "lds", "ldx", "ldy", "lsr", "lxa", "nop", "ora", "pha", "php",
                "pla", "plp", "rla", "rol", "ror", "rra", "rti", "rts", "sax", "sbc", "sbx",
                "sec", "sed", "sei", "sha", "shl", "shr", "shs", "shx", "shy", "slo", "sre",
                "sta", "stx", "sty", "tas", "tax", "tay", "tsx", "txa", "txs", "tya", "xaa")
    }


    fun assemble(options: CompilationOptions) {
        // add "-Wlong-branch"  to see warnings about conversion of branch instructions to jumps
        val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "--long-branch",
                "-Wall", "-Wno-strict-bool", "-Wno-shadow", "-Werror", "-Wno-error=long-branch",
                "--dump-labels", "--vice-labels", "-l", viceMonListFile.toString(), "--no-monitor")

        val outFile = when(options.output) {
            OutputType.PRG -> {
                command.add("--cbm-prg")
                println("\nCreating C-64 prg.")
                prgFile
            }
            OutputType.RAW -> {
                command.add("--nostart")
                println("\nCreating raw binary.")
                binFile
            }
        }
        command.addAll(listOf("--output", outFile.toString(), assemblyFile.toString()))

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
        val pattern = Regex("""al (\w+) \S+_prog8_breakpoint_\d+.?""")      // gather breakpoints by the source label that"s generated for them
        for(line in viceMonListFile.toFile().readLines()) {
            val match = pattern.matchEntire(line)
            if(match!=null)
            breakpoints.add("break \$" + match.groupValues[1])
        }
        val num = breakpoints.size
        breakpoints.add(0, "; vice monitor breakpoint list now follows")
        breakpoints.add(1, "; $num breakpoints have been defined")
        breakpoints.add(2, "del")
        viceMonListFile.toFile().appendText(breakpoints.joinToString("\n")+"\n")
    }
}
