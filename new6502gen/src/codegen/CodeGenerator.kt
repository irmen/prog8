/*
 * Main 6502/65C02 code generator for the new6502gen subproject.
 * Reads an IRProgram (from .p8ir files) and outputs 64tass-compatible assembly.
 *
 * Architecture:
 *   - 200 virtual word registers (r0-r199) in BSS at `p8_regfile` (400 bytes)
 *   - Calling convention slots s0-s5 map to physical CPU registers (A, X, Y, AX, AY, XY)
 *   - LOADHR/STOREHR transfer between virtual regs and physical CPU registers via slot number
 *   - Math routines (mul/div/mod) are emitted as jsr calls to external helpers
 *
 * Instruction translation is dispatched to extension functions in separate files:
 *   InstrLoadStore.kt, InstrArithmetic.kt, InstrBitwise.kt, InstrBranch.kt, InstrControl.kt
 *
 * Not yet implemented:
 *   - Floating point operations
 *   - CALLI, CALLFAR, CALLFARVB
 *   - Some advanced math (SQRT, trig, etc.)
 *   - Some bitwise operations (ASRM, LSRM, LSLM, ROXRM, ROXLM)
 */

package codegen

import prog8.code.GENERATED_LABEL_PREFIX
import prog8.code.core.*
import prog8.intermediate.*
import java.nio.file.Path

class CodeGenerator(private val program: IRProgram, private val target: ICompilationTarget) : ICodeGenerator {
    private val output = StringBuilder()
    private val cpu get() = target.cpu

    companion object {
        const val NUM_REGISTERS = 200
        const val REGFILE_LABEL = "p8_regfile"
    }
    
    init {
        require(target.cpu != CpuType.VIRTUAL)
        require(target.FLOAT_MEM_SIZE==5u) { "only 5-byte float format supported"}
    }

    private var labelSeqCounter = 0
    private var lastSourceLine = -1

    fun makeLabel(prefix: String): String {
        val label = "${prefix}_$labelSeqCounter"
        labelSeqCounter++
        return label
    }

    fun emitSourceComment(positions: List<Position>) {
        val pos = positions.firstOrNull { it != Position.DUMMY } ?: return
        if (pos.line == lastSourceLine) return
        lastSourceLine = pos.line
        val fileOnly = pos.file.substringAfterLast('/').substringAfterLast('\\')
        emitRaw("\t; source: $fileOnly:${pos.line}")
    }

    override fun generate(): Boolean {
        emitHeader()
        emitWeakDeclarations()
        emitConstants()
        emitCode()
        emitDataSection()
        emitBssSection()

        val options = program.options
        val asmFile = options.outputDir.resolve("${program.name}.asm")
        try {
            asmFile.toFile().writeText(output.toString())
        } catch (e: Exception) {
            System.err.println("Failed to write assembly file: ${e.message}")
            return false
        }

        if (!options.quiet)
            println("Assembly written to $asmFile")

        return assemble()
    }

    private fun assemble(): Boolean {
        val options = program.options
        val name = program.name
        val outputDir = options.outputDir
        val asmFile = outputDir.resolve("$name.asm")
        val viceMonListFile = outputDir.resolve("$name.vice-mon-list")
        val listFile = outputDir.resolve("$name.list")

        val assemblerCommand: List<String>

        fun addRemainingOptions(command: MutableList<String>, programFile: Path, assembly: Path): List<String> {
            if(options.compTarget.additionalAssemblerOptions.isNotEmpty())
                command.addAll(options.compTarget.additionalAssemblerOptions)
            command.addAll(listOf("--output", programFile.toString(), assembly.toString()))
            return command
        }

        when(options.output) {
            OutputType.PRG -> {
                val prgFile = outputDir.resolve("$name.prg")
                val command = mutableListOf("64tass", "--cbm-prg", "--ascii", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=$viceMonListFile")
                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")
                if(options.asmQuiet)
                    command.add("--quiet")
                if(options.asmListfile)
                    command.add("--list=$listFile")
                assemblerCommand = addRemainingOptions(command, prgFile, asmFile)
                if(!options.quiet)
                    println("\nCreating prg for target ${options.compTarget.name}.")
            }
            OutputType.XEX -> {
                val xexFile = outputDir.resolve("$name.xex")
                val command = mutableListOf("64tass", "--atari-xex", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=$viceMonListFile")
                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")
                if(options.asmQuiet)
                    command.add("--quiet")
                if(options.asmListfile)
                    command.add("--list=$listFile")
                assemblerCommand = addRemainingOptions(command, xexFile, asmFile)
                if(!options.quiet)
                    println("\nCreating xex for target ${options.compTarget.name}.")
            }
            OutputType.RAW -> {
                val binFile = outputDir.resolve("$name.bin")
                val command = mutableListOf("64tass", "--nostart", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=$viceMonListFile")
                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")
                if(options.asmQuiet)
                    command.add("--quiet")
                if(options.asmListfile)
                    command.add("--list=$listFile")
                assemblerCommand = addRemainingOptions(command, binFile, asmFile)
                if(!options.quiet)
                    println("\nCreating raw binary for target ${options.compTarget.name}.")
            }
            OutputType.LIBRARY -> {
                val binFile = outputDir.resolve("$name.bin")
                val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-implied-reg", "--no-monitor", "--dump-labels", "--vice-labels", "--labels=$viceMonListFile")
                if(options.warnSymbolShadowing)
                    command.add("-Wshadow")
                else
                    command.add("-Wno-shadow")
                if(options.asmQuiet)
                    command.add("--quiet")
                if(options.asmListfile)
                    command.add("--list=$listFile")
                if(options.compTarget.name in listOf("c64", "c128", "pet32")) {
                    if(!options.quiet)
                        println("\nCreating binary library file with header for target ${options.compTarget.name}.")
                    command.add("--cbm-prg")
                } else {
                    if(!options.quiet)
                        println("\nCreating binary library file without header for target ${options.compTarget.name}.")
                    command.add("--nostart")
                }
                assemblerCommand = addRemainingOptions(command, binFile, asmFile)
            }
        }

        val proc = ProcessBuilder(assemblerCommand)
            .redirectErrorStream(true)

        if(options.quiet) {
            proc.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        }

        val process = try {
            proc.start()
        } catch (e: Exception) {
            System.err.println("assembler failed to start: ${e.message}")
            return false
        }

        if(!options.quiet) {
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { println(it) }
            }
        }

        val result = process.waitFor()
        if(result == 0) {
            removeGeneratedLabelsFromMonlist(viceMonListFile)
            generateBreakpointList(viceMonListFile)
        }
        return result == 0
    }

    private fun removeGeneratedLabelsFromMonlist(viceMonListFile: Path) {
        val pattern = Regex("""al (\w+) \S+$GENERATED_LABEL_PREFIX.+?""")
        val lines = viceMonListFile.toFile().readLines()
        viceMonListFile.toFile().outputStream().bufferedWriter().use {
            for(line in lines) {
                if(pattern.matchEntire(line)==null)
                    it.write(line+"\n")
            }
        }
    }

    private fun generateBreakpointList(viceMonListFile: Path) {
        val breakpoints = mutableListOf<String>()
        val pattern = Regex("""al (\w+) \S+_prog8_breakpoint_\d+.?""")
        for(line in viceMonListFile.toFile().readLines()) {
            val match = pattern.matchEntire(line)
            if(match != null)
                breakpoints.add("break $" + match.groupValues[1])
        }
        val num = breakpoints.size
        breakpoints.add(0, "; breakpoint list now follows")
        breakpoints.add(1, "; $num breakpoints have been defined")
        breakpoints.add(2, "del")
        viceMonListFile.toFile().appendText(breakpoints.joinToString("\n") + "\n")
    }

    // === emit helpers ===

    fun emitLine(code: String, comment: String = "") {
        if (comment.isNotEmpty())
            output.appendLine("  $code        ; $comment")
        else
            output.appendLine("  $code")
    }

    fun emitLabel(label: String) {
        // Anonymous labels (+, ++, -) should not have a trailing colon in 64tass
        if (label.all { it == '+' || it == '-' })
            output.appendLine(label)
        else
            output.appendLine("$label:")
    }

    fun emitRaw(code: String) {
        output.appendLine(code)
    }

    // === register helpers ===
    // Virtual register file layout: each register is 2 bytes (low, high) at p8_regfile + reg*2

    fun regAddrLo(reg: Int): String = "$REGFILE_LABEL+${reg * 2}"
    fun regAddrHi(reg: Int): String = "$REGFILE_LABEL+${reg * 2 + 1}"
    fun regAddr(reg: Int): String = "$REGFILE_LABEL+${reg * 2}"

    // === label helpers ===
    // Convert Prog8 scoped names to 64tass-compatible assembly symbols.
    // Matches the naming mechanism from the existing 6502 codegen (AsmGen.fixNameSymbols)
    // but with extra handling for :: in enum constants and byte-selector prefixes from IR.

    fun fixNameSymbols(name: String): String {
        var n = name
        // Replace angle brackets used in auto-generated label names (like existing codegen)
        n = n.replace("<", "prog8_")
        n = n.replace(">", "")
        // Replace static variable hooks (like existing codegen)
        n = n.replace("prog8_lib.P8ZP_SCRATCH_", "P8ZP_SCRATCH_")
        // Prog8 :: scope operator in enum constants
        n = n.replace("::", "_")
        // Hyphens are not valid in 64tass labels
        n = n.replace("-", "_")
        return n
    }

    fun constLabel(name: String): String = "p8c_${fixNameSymbols(name).replace(".", "_")}"

    // Handle IR symbol references that may have LSB (<) or MSB (>) byte-selector prefix
    fun asmSymbolRef(name: String): String {
        val (prefix, rest) = when {
            name.startsWith('<') -> "<" to name.drop(1)
            name.startsWith('>') -> ">" to name.drop(1)
            else -> "" to name
        }
        return "$prefix${fixNameSymbols(rest)}"
    }

    fun resolveSymbolRef(name: String): String {
        val node = program.st.lookup(name)
        return when (node) {
            is IRStConstant -> constLabel(name)
            is IRStStaticVariable, is IRStMemVar,
            is IRStMemorySlab,
            is IRStStructInstance -> fixNameSymbols(name)
            else -> name      // external/library symbol, keep as-is
        }
    }

    fun dataTypeSize(dt: IRDataType): Int = when (dt) {
        IRDataType.BYTE -> 1
        IRDataType.WORD -> 2
        IRDataType.LONG -> 4
        IRDataType.FLOAT -> target.FLOAT_MEM_SIZE.toInt()
    }

    /** Zero-page temporary pointer address for address computation (2 bytes). */
    val ZP_TEMP: String get() = "\$${target.zeropage.SCRATCH_PTR.toString(16)}"

    // === CPU-aware instruction helpers ===
    // 65C02 supports stz; plain 6502 needs lda #0 / sta instead

    fun emitStoreZero(target: String, comment: String = "") {
        if (cpu == CpuType.CPU65C02)
            emitLine("stz $target", comment)
        else {
            emitLine("lda #0", comment)
            emitLine("sta $target")
        }
    }

    fun is65C02() = cpu == CpuType.CPU65C02

    // === header and startup ===

    // track external (library) symbols referenced during code generation
    private val externalRefs = mutableSetOf<String>()

    private fun emitHeader() {
        val options = program.options
        emitRaw("; Program: ${program.name}")
        emitRaw("; Generated by prog8-newgen (new 6502 codegen)")
        emitRaw("; Target CPU: ${cpu.name}")
        emitRaw("; Output: ${options.output.name}  Launcher: ${options.launcher.name}")
        emitRaw("")
        when (cpu) {
            CpuType.CPU65C02 -> emitRaw(".cpu \"65c02\"")
            CpuType.CPU6502 -> emitRaw(".cpu \"6502\"")
            CpuType.VIRTUAL -> emitRaw(".cpu \"6502\"")
        }
        emitRaw(".enc 'none'")
        emitRaw("")
        val zp = target.zeropage
        emitRaw("; zero-page scratch registers (used for address computation and temp values)")
        emitRaw("P8ZP_SCRATCH_B1  = \$${zp.SCRATCH_B1.toString(16)}    ; byte")
        emitRaw("P8ZP_SCRATCH_REG = \$${zp.SCRATCH_REG.toString(16)}    ; byte  (must be B1+1)")
        emitRaw("P8ZP_SCRATCH_W1  = \$${zp.SCRATCH_W1.toString(16)}    ; word  (2 bytes)")
        emitRaw("P8ZP_SCRATCH_W2  = \$${zp.SCRATCH_W2.toString(16)}    ; word  (2 bytes)")
        emitRaw("P8ZP_SCRATCH_PTR = \$${zp.SCRATCH_PTR.toString(16)}    ; word  (pointer)")
        emitRaw("")

        // user-supplied symbol definitions
        if (options.symbolDefs.isNotEmpty()) {
            emitRaw("; -- user supplied symbols on the command line")
            for ((name, value) in options.symbolDefs) {
                emitRaw("$name = $value")
            }
            emitRaw("")
        }

        // launcher / output type header
        emitLauncher()
    }

    private fun emitLauncher() {
        val options = program.options
        val loadAddr = options.loadAddress

        when (options.output) {
            OutputType.LIBRARY -> {
                emitRaw("; ---- library assembler program ----")
                emitRaw("* = ${"$"}${loadAddr.toString(16)}")
                emitLabel("prog8_program_start")
                emitLine("jmp main.start")
                emitRaw("")
            }

            OutputType.RAW -> {
                emitRaw("; ---- raw assembler program ----")
                emitRaw("* = ${"$"}${loadAddr.toString(16)}")
                emitLabel("prog8_program_start")
                emitStartupSequence()
            }

            OutputType.PRG -> {
                when (options.launcher) {
                    CbmPrgLauncherType.BASIC -> {
                        emitRaw("; ---- basic program with sys call ----")
                        emitRaw("* = ${"$"}${loadAddr.toString(16)}")
                        emitLabel("prog8_program_start")
                        emitRaw("  .word  (+), ${java.time.LocalDate.now().year}")
                        val entryAddr = "prog8_entrypoint"
                        emitRaw("  .null  \$9e, format(' %d ', prog8_entrypoint), \$3a, \$8f, ' prog8'")
                        emitLabel("+")
                        emitRaw("  .word  0")
                        emitLabel("prog8_entrypoint")
                        emitStartupSequence()
                    }
                    CbmPrgLauncherType.NONE -> {
                        emitRaw("; ---- program without basic sys call ----")
                        emitRaw("* = ${"$"}${loadAddr.toString(16)}")
                        emitLabel("prog8_program_start")
                        emitStartupSequence()
                    }
                }
            }

            OutputType.XEX -> {
                emitRaw("; ---- atari xex program ----")
                emitRaw("* = ${"$"}${loadAddr.toString(16)}")
                emitLabel("prog8_program_start")
                emitStartupSequence()
            }
        }
    }

    private fun emitStartupSequence() {
        emitLine("cld")
        emitLine("tsx", "save stackpointer for sys.exit()")
        emitLine("stx prog8_lib.orig_stackpointer")
        if (!program.options.noSysInit) {
            emitLine("jsr p8_sys_startup.init_system")
            emitLine("jsr p8_sys_startup.init_system_phase2")
        }
        emitLine("jsr prog8_lib.program_startup_clear_bss", "clear BSS section")
        emitLine("jsr run_global_inits", "run block-level variable initializers")
        emitLine("jsr main.start")
        emitLine("jmp cleanup_at_exit")
        emitRaw("")
        emitLabel("cleanup_at_exit")
        emitLine("jsr sys.poweroff_system")
        emitRaw("")
    }

    private fun emitWeakDeclarations() {
        emitRaw("; External library symbol declarations")
        emitRaw(".weak")
        emitRaw("  p8_sys_startup.init_system = 0")
        emitRaw("  p8_sys_startup.init_system_phase2 = 0")
        emitRaw("  math_tmp = 0")
        emitRaw("  math_mul8 = 0")
        emitRaw("  math_mul16 = 0")
        emitRaw("  math_div8 = 0")
        emitRaw("  math_div16 = 0")
        emitRaw("  math_mod8 = 0")
        emitRaw("  math_mod16 = 0")
        emitRaw("  math_divmod8 = 0")
        emitRaw("  math_divmod16 = 0")
        emitRaw("  sys .block")
        emitRaw("    poweroff_system:")
        emitRaw("    memset:")
        emitRaw("    memcopy:")
        emitRaw("    wait:")
        emitRaw("  .bend")
        emitRaw("  txt .block")
        emitRaw("    color:")
        emitRaw("    color2:")
        emitRaw("    getchr:")
        emitRaw("    getclr:")
        emitRaw("    plot:")
        emitRaw("    print:")
        emitRaw("    print_ub:")
        emitRaw("    print_uw:")
        emitRaw("    setcc:")
        emitRaw("    setcc2:")
        emitRaw("  .bend")
        emitRaw("  cx16 .block")
        emitRaw("    set_screen_mode:")
        emitRaw("    vpoke:")
        emitRaw("    set_vsync_irq_handler:")
        emitRaw("    enable_irq_handlers:")
        emitRaw("  .bend")
        emitRaw("  cbm .block")
        emitRaw("    RDTIM16:")
        emitRaw("  .bend")
        emitRaw("  math .block")
        emitRaw("    rnd:")
        emitRaw("  .bend")
        emitRaw("  psg2 .block")
        emitRaw("    init:")
        emitRaw("    update:")
        emitRaw("    voice:")
        emitRaw("    frequency:")
        emitRaw("    envelope:")
        emitRaw("  .bend")
        emitRaw("  prog8_slabs .block")
        emitRaw("  .bend")
        emitRaw("  prog8_interned_strings .block")
        emitRaw("  .bend")
        emitRaw("  conv .block")
        emitRaw("  .bend")
        emitRaw("  prog8 .block")
        emitRaw("    clear_bss:")
        emitRaw("  .bend")
        emitRaw("  prog8_lib .block")
        emitRaw("    orig_stackpointer:")
        emitRaw("    program_startup_clear_bss:")
        emitRaw("    sqrt_long .block")
        emitRaw("      num:")
        emitRaw("      resultword:")
        emitRaw("    .bend")
        emitRaw("  .bend")
        emitRaw("  prog8_bss_section_start:")
        emitRaw("  prog8_bss_section_size:")
        emitRaw(".endweak")
        emitRaw("")
    }

    private fun emitConstants() {
        val emitted = mutableSetOf<String>()
        emitRaw("; Constants")
        for (c in program.st.allConstants()) {
            val cv = c.value
            val csn = c.memorySlabName
            if (cv != null) {
                // emit with mangled name (for generated code references via constLabel)
                val label = constLabel(c.name)
                if (!emitted.add(label)) continue
                emitRaw("$label = ${cv.toLong()}")
                // also emit with original scoped name (for inline assembly references)
                emitRaw("${c.name} = ${cv.toLong()}")
            } else if (csn != null) {
                val slab = program.st.lookup(csn) as? IRStMemorySlab
                if (slab != null) {
                    val label = constLabel(c.name)
                    val slabRef = fixNameSymbols(slab.name)
                    if (!emitted.add(label)) continue
                    emitRaw("$label = $slabRef")
                    emitRaw("${c.name} = $slabRef")
                }
            }
        }
        emitRaw("")

        // Memory-mapped I/O addresses (used by inline assembly).
        // Skip P8ZP_SCRATCH variables - they're already defined in the header.
        val memMapped = program.st.allMemMappedVariables()
            .filterNot { it.name.startsWith("P8ZP_SCRATCH") || it.name.startsWith("prog8_lib.P8ZP_SCRATCH") }
            .toList()
        if (memMapped.isNotEmpty()) {
            emitRaw("; Memory-mapped variables")
            for (mm in memMapped) {
                emitRaw("${mm.name} = ${"$"}${mm.address.toString(16)}")
            }
            emitRaw("")
        }

        // External KERNAL entry points and other symbols used by inline assembly
        // These should come from %asminclude in library source but aren't in the IR yet.
        if (target.name == "cx16") {
            emitRaw("; External KERNAL entry points (cx16)")
            emitRaw("cx16.mouse_config = \$ff68")
            emitRaw("cx16.audio_init = \$c09f")
            emitRaw("cx16.screen_mode = \$ff5f")
            emitRaw("cbm.CINT = \$ff81")
            emitRaw("cbm.IOINIT = \$ff84")
            emitRaw("cbm.RESTOR = \$ff8a")
            emitRaw("cbm.CHROUT = \$ffd2")
            emitRaw("")
        }
    }

    // === main code emission ===

    private fun emitCode() {
        emitLabel("run_global_inits")
        translateChunk(program.globalInits)
        emitLine("rts")
        emitRaw("")

        for (block in program.blocks) {
            val addr = block.options.address
            if (addr != null) {
                emitRaw("* = ${"$"}${addr.toString(16)}")
            }
            val scopeDirective = if (block.options.forceOutput) ".block" else ".proc"
            emitRaw("${block.label}  $scopeDirective")
            emitRaw("; Block: ${block.label}")
            for (element in block.children) {
                when (element) {
                    is IRSubroutine -> emitSubroutine(element)
                    is IRAsmSubroutine -> emitAsmSubroutine(element)
                    is IRInlineAsmChunk -> {
                        // skip label if it matches the block label (already defined by .proc/.block)
                        val cl = element.label
                        if (cl != null && cl != block.label) emitLabel(cl)
                        emitRaw(element.assembly)
                    }
                    is IRInlineBinaryChunk -> {
                        val cl = element.label
                        if (cl != null && cl != block.label) emitLabel(cl)
                        emitRaw("  .byte ${element.data.joinToString(",") { "${it.toInt() and 0xff}" }}")
                    }
                    else -> {}
                }
            }
            val endDirective = if (block.options.forceOutput) ".bend" else ".pend"
            emitRaw("  $endDirective")
            emitRaw("")
        }
    }

    private fun emitSubroutine(sub: IRSubroutine) {
        emitRaw("; Subroutine: ${sub.label}")
        val firstChunk = sub.chunks.filterIsInstance<IRCodeChunk>().firstOrNull()
        if (firstChunk != null) emitSourceComment(firstChunk.sourceLinesPositions)
        emitRaw("${sub.label} .proc")
        for (chunk in sub.chunks) {
            when (chunk) {
                is IRCodeChunk -> {
                    // skip chunk label if it matches the subroutine name (already defined by .proc)
                    if (chunk.label != sub.label) {
                        val cl = chunk.label
                        if (cl != null) emitLabel(cl)
                    }
                    translateChunk(chunk)
                }
                is IRInlineAsmChunk -> {
                    val cl = chunk.label
                    if (cl != null) emitLabel(cl)
                    emitRaw(chunk.assembly)
                }
                is IRInlineBinaryChunk -> {
                    val cl = chunk.label
                    if (cl != null) emitLabel(cl)
                    emitRaw("  .byte ${chunk.data.joinToString(",") { "${it.toInt() and 0xff}" }}")
                }
            }
        }
        emitRaw(".pend")
        emitRaw("")
    }

    private fun emitAsmSubroutine(sub: IRAsmSubroutine) {
        val addr = sub.address
        if (addr != null) {
            emitLine("* = $addr", "origin for asm subroutine ${sub.label}")
        }
        emitRaw("${sub.label} .proc")
        emitRaw(sub.asmChunk.assembly)
        emitRaw(".pend")
        emitRaw("")
    }

    private fun translateChunk(chunk: IRCodeChunk) {
        emitSourceComment(chunk.sourceLinesPositions)
        for (insn in chunk.instructions) {
            translateInstruction(insn)
        }
    }

    // === instruction dispatch ===

    private fun translateInstruction(insn: IRInstruction) {
        when (insn.opcode) {
            Opcode.NOP -> {}
            Opcode.BREAKPOINT -> emitLine("brk")

            Opcode.LOAD, Opcode.LOADM, Opcode.LOADR, Opcode.LOADX, Opcode.LOADHR, Opcode.LOADI,
            Opcode.STOREM, Opcode.STOREX, Opcode.STOREZM, Opcode.STOREZI, Opcode.STOREZX, Opcode.STOREHR, Opcode.STOREI,
            Opcode.LOADHFACZERO, Opcode.LOADHFACONE,
            Opcode.STOREHFACZERO, Opcode.STOREHFACONE -> translateLoadStore(insn)

            Opcode.INC, Opcode.INCM, Opcode.DEC, Opcode.DECM,
            Opcode.NEG, Opcode.NEGM,
            Opcode.ADDR, Opcode.ADD, Opcode.ADDM,
            Opcode.SUBR, Opcode.SUB, Opcode.SUBM,
            Opcode.MULR, Opcode.MUL, Opcode.MULM,
            Opcode.MULSR, Opcode.MULS, Opcode.MULSM,
            Opcode.DIVR, Opcode.DIV, Opcode.DIVM,
            Opcode.DIVSR, Opcode.DIVS, Opcode.DIVSM,
            Opcode.MODR, Opcode.MOD, Opcode.MODSR, Opcode.MODS,
            Opcode.DIVMODR, Opcode.DIVMOD, Opcode.SDIVMODR, Opcode.SDIVMOD,
            Opcode.CMP, Opcode.CMPI -> translateArithmetic(insn)

            Opcode.ANDR, Opcode.AND, Opcode.ANDM,
            Opcode.ORR, Opcode.OR, Opcode.ORM,
            Opcode.XORR, Opcode.XOR, Opcode.XORM,
            Opcode.INV, Opcode.INVM,
            Opcode.ASRN, Opcode.ASRNM, Opcode.LSRN, Opcode.LSRNM, Opcode.LSLN, Opcode.LSLNM,
            Opcode.ASR, Opcode.ASRM, Opcode.LSR, Opcode.LSRM, Opcode.LSL, Opcode.LSLM,
            Opcode.ROR, Opcode.RORM, Opcode.ROL, Opcode.ROLM,
            Opcode.ROXR, Opcode.ROXRM, Opcode.ROXL, Opcode.ROXLM,
            Opcode.BITTST, Opcode.BITSET, Opcode.BITCLR, Opcode.BITTOG -> translateBitwise(insn)

            Opcode.BSTCC, Opcode.BSTCS, Opcode.BSTEQ, Opcode.BSTNE,
            Opcode.BSTNEG, Opcode.BSTPOS, Opcode.BSTVC, Opcode.BSTVS,
            Opcode.BGTR, Opcode.BGT, Opcode.BLT, Opcode.BLE,
            Opcode.BGTSR, Opcode.BGTS, Opcode.BLTS, Opcode.BGESR, Opcode.BGES, Opcode.BLES,
            Opcode.BGER, Opcode.BGE -> translateBranch(insn)

            Opcode.JUMP, Opcode.JUMPI,
            Opcode.CALL, Opcode.CALLI, Opcode.CALLFAR, Opcode.CALLFARVB,
            Opcode.SYSCALL,
            Opcode.RETURN, Opcode.RETURNR, Opcode.RETURNI,
            Opcode.PUSH, Opcode.POP,
            Opcode.PUSHST, Opcode.POPST,
            Opcode.CLC, Opcode.SEC, Opcode.CLI, Opcode.SEI,
            Opcode.ALIGN,
            Opcode.LSIGB, Opcode.LSIGW, Opcode.MSIGB, Opcode.MSIGW, Opcode.BSIGB,
            Opcode.MIDB, Opcode.CONCAT,
            Opcode.EXT, Opcode.EXTS,
            Opcode.SQRT, Opcode.SQUARE, Opcode.SGN,
            Opcode.FFROMUB, Opcode.FFROMSB, Opcode.FFROMUW, Opcode.FFROMSW, Opcode.FFROMSL,
            Opcode.FTOUB, Opcode.FTOSB, Opcode.FTOUW, Opcode.FTOSW, Opcode.FTOSL,
            Opcode.FABS, Opcode.FSIN, Opcode.FCOS, Opcode.FTAN, Opcode.FATAN,
            Opcode.FPOW, Opcode.FLN, Opcode.FLOG,
            Opcode.FROUND, Opcode.FFLOOR, Opcode.FCEIL,
            Opcode.FCOMP -> translateControl(insn)
        }
    }

    // === struct type definitions ===

    private fun emitStructDefs() {
        val structs = program.st.allStructDefs().toList()
        if (structs.isEmpty()) return
        emitRaw("; struct type definitions")
        for (sd in structs) {
            val paramFields = sd.fields.filter { it.arraySize == null }
            val structargs = paramFields.indices.joinToString(",") { "f$it" }
            emitRaw("${fixNameSymbols(sd.name)}  .struct $structargs")
            var paramIdx = 0
            for (field in sd.fields) {
                val type = when {
                    field.type.isBool || field.type.isUnsignedByte -> ".byte"
                    field.type.isSignedByte -> ".char"
                    field.type.isUnsignedWord || field.type.isPointer -> ".word"
                    field.type.isSignedWord -> ".sint"
                    field.type.isLong -> ".dint"
                    field.type.isFloat -> ".byte"
                    else -> ".byte"
                }
                val fieldName = "p8v_${field.name}"
                val arrSize = field.arraySize
                if (arrSize != null) {
                    val anon = List(arrSize) { "?" }.joinToString(",")
                    emitRaw("  $fieldName $type $anon")
                } else {
                    emitRaw("  $fieldName $type \\f$paramIdx")
                    paramIdx++
                }
            }
            emitRaw("  .endstruct")
            emitRaw("")
        }
        emitRaw("")
    }

    // === data section ===

    private fun emitDataSection() {
        emitStructDefs()
        emitRaw("")

        // static variables with initialized values (non-BSS, inline with code)
        val initdVars = program.st.allVariables().filter { !it.inBss }.toList()
        if (initdVars.isNotEmpty()) {
            emitRaw("; static variables with initial values")
            for (v in initdVars) {
                emitAlign(v.align)
                emitInitializedVariable(v)
            }
            emitRaw("")
        }

        // memory slabs in BSS_SLABS section
        val slabs = program.st.allMemorySlabs().toList()
        if (slabs.isNotEmpty()) {
            emitRaw("  .section BSS_SLABS")
            emitLabel("${REGFILE_LABEL}_slabs")
            for (slab in slabs) {
                emitAlign(slab.align)
                val label = fixNameSymbols(slab.name)
                emitLine("$label  .fill ${slab.size}", "memory slab ${slab.name}")
            }
            emitRaw("  .send BSS_SLABS")
            emitRaw("")
        }

        // struct instances without init values -> BSS (zeroed at startup)
        val allInstances = program.st.allStructInstances().toList()
        val (instancesNoInit, instancesWithInit) = allInstances.partition { it.values.isEmpty() }
        if (instancesNoInit.isNotEmpty()) {
            emitRaw("  .section BSS")
            emitRaw("${REGFILE_LABEL}_structinst_bss  .block")
            for (si in instancesNoInit) {
                val label = fixNameSymbols(si.name)
                emitLine("$label  .fill ${si.size}", "struct instance ${si.name}")
            }
            emitRaw("  .bend")
            emitRaw("  .send BSS")
            emitRaw("")
        }

        // struct instances with init values -> STRUCTINSTANCES section
        if (instancesWithInit.isNotEmpty()) {
            emitRaw("  .section STRUCTINSTANCES")
            emitRaw("${REGFILE_LABEL}_structinst  .block")
            for (si in instancesWithInit) {
                val label = fixNameSymbols(si.name)
                val instName = label.substringAfter('.')
                emitLabel(instName)
                for (fieldValue in si.values) {
                    val fv = fieldValue.value
                    when (fv) {
                        is IRStSymbolicReference.Numeric -> {
                            val v = fv.value.toInt()
                            val size = when (fieldValue.dt) {
                                BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.BOOL -> ".byte"
                                BaseDataType.UWORD, BaseDataType.WORD -> ".word"
                                else -> ".word"
                            }
                            emitLine("  $size $v")
                        }
                        is IRStSymbolicReference.Symbol -> {
                            emitLine("  .word ${fixNameSymbols(fv.name)}")
                        }
                        is IRStSymbolicReference.BoolValue -> {
                            val v = if (fv.value) 1 else 0
                            emitLine("  .byte $v")
                        }
                    }
                }
            }
            emitRaw("  .bend")
            emitRaw("  .send STRUCTINSTANCES")
            emitRaw("")
        }
    }

    private fun emitAlign(align: UInt) {
        if (align > 1u) {
            emitLine(".align  ${"$"}${align.toString(16)}")
        }
    }

    private fun emitInitializedVariable(v: IRStStaticVariable) {
        val dt = v.dt
        val label = fixNameSymbols(v.name)
        val init = v.initializationValue

        when {
            dt.isSplitWordArray -> {
                // _lsb / _msb halves via helper array
                val numElements = v.length?.toInt() ?: 0
                val halfBytes = numElements   // each element is 1 byte per half
                val values = when (init) {
                    is IRVariableInitializer.Array -> init.elements.map {
                        when (it) {
                            is IRStSymbolicReference.Numeric -> it.value.toInt()
                            is IRStSymbolicReference.BoolValue -> if (it.value) 1 else 0
                            else -> 0
                        }
                    }
                    else -> List(halfBytes) { 0 }
                }
                val parts = values.joinToString(",")
                emitRaw("_array_$label := $parts")
                emitLine("${label}_lsb  .byte  <_array_$label")
                emitLine("${label}_msb  .byte  >_array_$label")
            }

            dt.isString -> {
                when (init) {
                    is IRVariableInitializer.Str -> {
                        emitLine("$label  .null \"${init.text}\"")
                    }
                    else -> emitLine("$label  .byte  ?")
                }
            }

            dt.isArray -> {
                val values = when (init) {
                    is IRVariableInitializer.Array -> init.elements.map {
                        when (it) {
                            is IRStSymbolicReference.Numeric -> "${it.value.toInt()}"
                            is IRStSymbolicReference.BoolValue -> if (it.value) "1" else "0"
                            is IRStSymbolicReference.Symbol -> asmSymbolRef(it.name)
                        }
                    }
                    else -> listOf("0")
                }
                val directive = when {
                    dt.elementType().isUnsignedByte || dt.elementType().isBool -> ".byte"
                    dt.elementType().isSignedByte -> ".char"
                    dt.elementType().isUnsignedWord || dt.elementType().isPointer -> ".word"
                    dt.elementType().isSignedWord -> ".sint"
                    dt.elementType().isLong -> ".dint"
                    else -> ".byte"
                }
                if (values.size <= 16) {
                    emitLine("$label  $directive ${values.joinToString(",")}")
                } else {
                    emitLabel(label)
                    for (chunk in values.chunked(16)) {
                        emitLine("  $directive ${chunk.joinToString(",")}")
                    }
                }
            }

            dt.isFloat -> {
                val bytes = when (init) {
                    is IRVariableInitializer.Numeric -> target.getFloatAsmBytes(init.value)
                    else -> List(target.FLOAT_MEM_SIZE.toInt()) { 0 }.joinToString(",") { "\$00" }
                }
                emitLine("$label  .byte $bytes")
            }

            else -> {
                // single numeric value (byte/word/long)
                val v = when (init) {
                    is IRVariableInitializer.Numeric -> init.value.toInt()
                    else -> 0
                }
                val directive = when {
                    dt.isByteOrBool -> ".byte"
                    dt.isWord -> ".word"
                    dt.isLong -> ".long"
                    else -> ".byte"
                }
                emitLine("$label  $directive $v")
            }
        }
    }

    // === BSS sections ===

    private fun emitBssSection() {
        val options = program.options
        val bssVars = program.st.allVariables().filter { it.inBss }.toList()

        emitRaw("")
        emitRaw("; bss sections")
        emitRaw("PROG8_VARSHIGH_RAMBANK = ${options.varsHighBank ?: 1}")

        // Determine if BSS needs relocation
        val relocateVars = options.varsGolden || options.varsHighBank != null
        val relocateSlabs = options.slabsGolden || options.slabsHighBank != null

        if (relocateVars) {
            emitNonrelocatableSections(relocateSlabs)
            emitLabel("prog8_program_end")
            val relocatedStart = if (options.varsGolden) options.compTarget.BSSGOLDENRAM_START
                                 else options.compTarget.BSSHIGHRAM_START
            emitRaw("  * = ${"$"}${relocatedStart.toString(16)}")
        }

        emitLabel("prog8_bss_section_start")
        emitBssVars(bssVars)

        if (relocateVars) {
            val relocatedEnd = if (options.varsGolden) options.compTarget.BSSGOLDENRAM_END
                               else options.compTarget.BSSHIGHRAM_END
            emitLine("  .cerror * > ${"$"}${relocatedEnd.toString(16)}",
                "too many variables/data for BSS section")
        }

        emitLabel("prog8_bss_section_end")
        emitRaw("prog8_bss_section_size = prog8_bss_section_end - prog8_bss_section_start")

        emitRaw("")
        emitAsmSymbols()
        emitRaw("")
        if (!relocateVars) {
            emitLabel("prog8_program_end")
        }
        // memtop overflow check
        if (options.memtopAddress > 0u) {
            val memtopHex = "${"$"}${options.memtopAddress.toString(16)}"
            emitLine("  .cerror * >= $memtopHex",
                "Program too long, memtop=$memtopHex")
        }
        emitRaw("")
    }

    private fun emitNonrelocatableSections(alsoSlabs: Boolean) {
        // emit BSS_NOCLEAR and optionally BSS_SLABS before relocation
        emitRaw("  .dsection BSS_NOCLEAR")
        if (!alsoSlabs) {
            emitRaw("  .dsection BSS_SLABS")
        }
    }

    private fun emitBssVars(vars: List<IRStStaticVariable>) {
        // BSS_NOCLEAR for dirty variables and float eval temporaries
        val (dirty, clean) = vars.partition { it.dirty }
        if (dirty.isNotEmpty()) {
            emitRaw("  .section BSS_NOCLEAR")
            emitRaw("; dirty variables (not cleared at subroutine entry)")
            for (v in dirty) {
                emitAlign(v.align)
                emitUninitializedVariable(v)
            }
            emitRaw("  .send BSS_NOCLEAR")
            emitRaw("")
        }

        // Main BSS section (cleared at startup)
        val regfileSize = NUM_REGISTERS * 2
        emitLabel(REGFILE_LABEL)
        emitLine(".fill $regfileSize", "virtual register file (${NUM_REGISTERS} words)")
        emitRaw("")

        if (clean.isNotEmpty()) {
            emitRaw("  .section BSS")
            for (v in clean) {
                emitAlign(v.align)
                emitUninitializedVariable(v)
            }
            emitRaw("  .send BSS")
            emitRaw("")
        }

        // emit BSS_NOCLEAR remainder (for library subroutines that may add vars)
        emitRaw("  .dsection BSS_NOCLEAR")
        emitRaw("  .dsection BSS_SLABS")
    }

    private fun emitUninitializedVariable(v: IRStStaticVariable) {
        val label = fixNameSymbols(v.name)
        val dt = v.dt

        val (directive, count) = when {
            dt.isSplitWordArray -> {
                // two separate byte arrays: _lsb and _msb
                val numElements = v.length?.toInt() ?: 1
                emitLine("${label}_lsb  .fill $numElements")
                emitLine("${label}_msb  .fill $numElements")
                return
            }
            dt.isBool || dt.isUnsignedByte -> ".byte" to 1
            dt.isSignedByte -> ".char" to 1
            dt.isUnsignedWord || dt.isPointer -> ".word" to 1
            dt.isSignedWord -> ".sint" to 1
            dt.isLong -> ".dint" to 1
            dt.isFloat -> ".fill" to target.FLOAT_MEM_SIZE.toInt()
            dt.isArray || dt.isString -> {
                val sz = (v.length ?: 1u).toInt()
                ".fill" to sz
            }
            else -> ".byte" to 1
        }

        if (directive == ".fill") {
            emitLine("$label  .fill $count")
        } else {
            emitLine("$label  $directive  ?")
        }
    }

    private fun emitAsmSymbols() {
        for ((name, value) in program.asmSymbols) {
            if (!name.startsWith(".")) {
                emitLabel(name)
                emitLine("= $value")
            }
        }
        for ((name, value) in program.st.getAsmSymbols()) {
            if (!name.startsWith(".")) {
                emitLabel(name)
                emitLine("= $value")
            }
        }
    }

    private val compTarget: ICompilationTarget get() = target
}
