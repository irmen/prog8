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

    private val zpAllocator by lazy { ZeropageAllocator(program, target) }
    private val zpAllocations by lazy { zpAllocator.allocate() }
    private fun isZpVar(name: String) = zpAllocator.isZpVar(name)

    // === variable-size virtual register file layout ===
    // Each virtual register (r0-r199) has exactly one datatype throughout the entire program.
    // The regfile layout is computed by scanning all instructions for their register types.
    // Note: fp registers (fr0-fr?) use the FAC accumulator mechanism, not the regfile.
    private data class RegFileLayout(val offsets: Map<Int, Int>, val totalSize: Int)

    private val regFileLayout: RegFileLayout by lazy {
        val allRegs = program.registersUsed().regsTypes
        val offsets = mutableMapOf<Int, Int>()
        var currentOffset = 0
        for ((regNum, type) in allRegs.entries.sortedBy { it.key.value }) {
            if (regNum.value !in 0 until NUM_REGISTERS) continue
            offsets[regNum.value] = currentOffset
            currentOffset += dataTypeSize(type)
        }
        RegFileLayout(offsets, currentOffset)
    }

    override fun generate(): Boolean {
        emitHeader()
        emitConstants()
        emitZeropageVariables()
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
        output.append("    ")
        output.append(code)
        if (comment.isNotEmpty()) {
            output.append("        ; ")
            output.append(comment)
        }
        output.appendLine()
    }

    fun emitLabel(label: String) {
        // 64tass only allows single + or - as anonymous forward/backward labels
        require(label.length == 1 || label.any { it != '+' && it != '-' }) {
            "Invalid anonymous label '$label': only single '+' or '-' are allowed in 64tass"
        }
        if (label == "+" || label == "-")
            output.appendLine(label)
        else {
            output.appendLine()
            output.appendLine("$label:")
        }
    }

    fun emitRaw(code: String) {
        output.appendLine(code)
    }

    // === register helpers ===
    // Virtual register file layout: variable-size registers at p8_regfile + offset[reg]

    fun regAddrLo(reg: Int): String {
        val offset = regFileLayout.offsets[reg] ?: error("register r$reg has no layout info")
        return "$REGFILE_LABEL+$offset"
    }
    fun regAddrHi(reg: Int): String {
        val offset = regFileLayout.offsets[reg] ?: error("register r$reg has no layout info")
        return "$REGFILE_LABEL+${offset + 1}"
    }
    fun regAddr(reg: Int): String {
        val offset = regFileLayout.offsets[reg] ?: error("register r$reg has no layout info")
        return "$REGFILE_LABEL+$offset"
    }
    fun regAddrByte(reg: Int, byteOffset: Int): String {
        val offset = regFileLayout.offsets[reg] ?: error("register r$reg has no layout info")
        return "$REGFILE_LABEL+${offset + byteOffset}"
    }

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
    val ZP_TEMP: String get() = target.zeropage.SCRATCH_PTR.toHex()

    /** Format a byte value as `$xx` hex for .byte directives (always hex, even for 0-15). */
    private fun asmHexByte(v: Int): String = "\$${v.toUByte().toString(16).padStart(2,'0')}"

    // === CPU-aware instruction helpers ===
    // 65C02 supports stz; plain 6502 needs lda #0 / sta instead

    fun emitStoreZero(target: String, comment: String = "") {
        if (cpu == CpuType.CPU65C02)
            emitLine("stz  $target")
        else {
            emitLine("lda  #0")
            emitLine("sta  $target")
        }
    }

    fun is65C02() = cpu == CpuType.CPU65C02

    /** Look up an asmsub parameter that maps to a CX16 virtual register. */
    fun asmSubParamTarget(fnLabel: String, argIndex: Int): String? {
        for (block in program.blocks) {
            for (element in block.children) {
                if (element is IRAsmSubroutine && element.label == fnLabel) {
                    if (argIndex < element.parameters.size) {
                        val reg = element.parameters[argIndex].reg.registerOrPair
                        if (reg != null && reg in Cx16VirtualRegisters) {
                            return "cx16.${reg.name.lowercase()}"
                        }
                    }
                    return null
                }
            }
        }
        return null
    }

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
            CpuType.CPU65C02 -> emitRaw(".cpu  'w65c02'")
            CpuType.CPU6502 -> emitRaw(".cpu  '6502'")
            CpuType.VIRTUAL -> emitRaw(".cpu  '6502'")
        }
        emitRaw(".enc 'none'")
        emitRaw("")
        val zp = target.zeropage
        emitRaw("; zero-page scratch registers (used for address computation and temp values)")
        emitRaw("P8ZP_SCRATCH_B1  = ${zp.SCRATCH_B1}    ; byte")
        emitRaw("P8ZP_SCRATCH_REG = ${zp.SCRATCH_REG}    ; byte  (must be B1+1)")
        emitRaw("P8ZP_SCRATCH_W1  = ${zp.SCRATCH_W1}    ; word  (2 bytes)")
        emitRaw("P8ZP_SCRATCH_W2  = ${zp.SCRATCH_W2}    ; word  (2 bytes)")
        emitRaw("P8ZP_SCRATCH_PTR = ${zp.SCRATCH_PTR}    ; word  (pointer)")
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
                emitRaw("* = ${loadAddr.toHex()}")
                emitLabel("prog8_program_start")
                emitLine("jmp  p8b_main.p8s_start")
                emitRaw("")
            }

            OutputType.RAW -> {
                emitRaw("; ---- raw assembler program ----")
                emitRaw("* = ${loadAddr.toHex()}")
                emitLabel("prog8_program_start")
                emitStartupSequence()
            }

            OutputType.PRG -> {
                when (options.launcher) {
                    CbmPrgLauncherType.BASIC -> {
                        emitRaw("; ---- basic program with sys call ----")
                        emitRaw("* = ${loadAddr.toHex()}")
                        emitLabel("prog8_program_start")
                        emitRaw("    .word  (+), ${java.time.LocalDate.now().year}")
                        val entryAddr = "prog8_entrypoint"
                        emitRaw("    .null  \$9e, format(' %d ', prog8_entrypoint), \$3a, \$8f, ' prog8'")
                        emitLabel("+")
                        emitRaw("    .word  0")
                        emitLabel("prog8_entrypoint")
                        emitStartupSequence()
                    }
                    CbmPrgLauncherType.NONE -> {
                        emitRaw("; ---- program without basic sys call ----")
                        emitRaw("* = ${loadAddr.toHex()}")
                        emitLabel("prog8_program_start")
                        emitStartupSequence()
                    }
                }
            }

            OutputType.XEX -> {
                emitRaw("; ---- atari xex program ----")
                emitRaw("* = ${loadAddr.toHex()}")
                emitLabel("prog8_program_start")
                emitStartupSequence()
            }
        }
    }

    private fun emitStartupSequence() {
        emitLine("cld")
        emitLine("tsx")
        emitLine("stx  prog8_lib.orig_stackpointer")
        if (!program.options.noSysInit) {
            emitLine("jsr  p8_sys_startup.init_system")
            emitLine("jsr  p8_sys_startup.init_system_phase2")
        }
            emitLine("jsr  run_global_inits")
        emitLine("jsr  p8b_main.p8s_start")
        emitLine("jmp  p8_sys_startup.cleanup_at_exit")
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
                emitRaw("${fixNameSymbols(c.name)} = ${cv.toLong()}")
            } else if (csn != null) {
                val slab = program.st.lookup(csn) as? IRStMemorySlab
                if (slab != null) {
                    val label = constLabel(c.name)
                    val slabRef = fixNameSymbols(slab.name)
                    if (!emitted.add(label)) continue
                    emitRaw("$label = $slabRef")
                    emitRaw("${fixNameSymbols(c.name)} = $slabRef")
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
                emitRaw("${mm.name} = ${mm.address.toHex()}")
            }
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
                emitRaw("* = ${addr.toHex()}")
            }
            val scopeDirective = if (block.options.forceOutput) ".block" else ".proc"
            emitRaw("${block.label}  $scopeDirective")
            emitRaw("; Block: ${block.label}")
            for (element in block.children) {
                when (element) {
                    is IRSubroutine -> emitSubroutine(element)
                    is IRAsmSubroutine -> emitAsmSubroutine(element)
                    is IRInlineAsmChunk -> emitRaw(element.assembly)
                    is IRInlineBinaryChunk -> emitRaw("    .byte  ${element.data.joinToString(",") { asmHexByte(it.toInt()) }}")
                    else -> {}
                }
            }
            val endDirective = if (block.options.forceOutput) ".bend" else ".pend"
            emitRaw("  $endDirective")
            emitRaw("")
        }
    }

    private fun unscopedName(scopedName: String): String =
        scopedName.substringAfterLast('.')

    private fun emitSubroutine(sub: IRSubroutine) {
        emitRaw("; Subroutine: ${sub.label}")
        val firstChunk = sub.chunks.filterIsInstance<IRCodeChunk>().firstOrNull()
        if (firstChunk != null)
            emitSourceComment(firstChunk.sourceLinesPositions)
        emitRaw("")
        emitRaw("${unscopedName(sub.label)}  .proc")
        if (sub.label == "p8b_main.p8s_start") {
            // BSS clearing needs to happen even if something calls main.start directly (without startup logic - for example if this is a library)
            emitLine("jsr  prog8_lib.program_startup_clear_bss")
        }
        for (chunk in sub.chunks) {
            when (chunk) {
                is IRCodeChunk -> {
                    // skip chunk label if it matches the subroutine name (already defined by .proc)
                    val uname = unscopedName(sub.label)
                    if (chunk.label != sub.label && chunk.label != uname) {
                        val cl = chunk.label
                        if (cl != null) emitLabel(cl)
                    }
                    translateChunk(chunk)
                }
                is IRInlineAsmChunk -> {
                    val cl = chunk.label
                    // skip label if it matches the subroutine name (already defined by .proc)
                    val uname = unscopedName(sub.label)
                    if (cl != null && cl != sub.label && cl != uname) emitLabel(cl)
                    emitRaw(chunk.assembly)
                }
                is IRInlineBinaryChunk -> {
                    val cl = chunk.label
                    if (cl != null) emitLabel(cl)
                    emitRaw("    .byte  ${chunk.data.joinToString(",") { asmHexByte(it.toInt()) }}")
                }
            }
        }
        emitRaw(".pend")
        emitRaw("")
    }

    private fun emitAsmSubroutine(sub: IRAsmSubroutine) {
        val addr = sub.address
        if (addr != null) {
            emitLine("* = $addr")
        }
        emitRaw("")
        emitRaw("${unscopedName(sub.label)}  .proc")
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
        emitRaw("        ; $insn")
        if (insn.opcode == Opcode.LOADHR || insn.opcode == Opcode.STOREHR) {
            val slotNames = mapOf(0 to "A", 1 to "X", 2 to "Y", 3 to "AX", 4 to "AY", 5 to "XY")
            val slot = insn.immediate
            slot?.let { slotNames[it]?.let { name -> emitRaw("        ; slot s$it == $name") } }
        }
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
            emitRaw("    .endstruct")
            emitRaw("")
        }
        emitRaw("")
    }

    // === data section ===

    private fun emitDataSection() {
        emitStructDefs()
        emitRaw("")

        // static variables with initialized values (non-BSS, inline with code)
        val initdVars = program.st.allVariables().filter { !it.inBss && !isZpVar(it.name) }.toList()
        if (initdVars.isNotEmpty()) {
            emitRaw("; static variables with initial values")
            // Group by scope prefix and wrap non-block scopes in .block/.bend
            // (so dots in labels are properly resolvable from inside .proc blocks)
            val blockLabels = program.blocks.map { it.label }.toSet()
            var currentScope: String? = null
            for (v in initdVars) {
                val scope = v.name.substringBefore('.', "")
                if (scope.isNotEmpty() && scope != v.name) {
                    val scopeBlock = blockLabels.firstOrNull { it == scope || it.endsWith(".$scope") }
                    if (scopeBlock == null) {
                        if (currentScope != scope) {
                            if (currentScope != null) emitRaw("    .bend")
                            emitRaw("$scope .block")
                            currentScope = scope
                        }
                    }
                }
                emitAlign(v.align)
                emitInitializedVariable(v)
            }
            if (currentScope != null) emitRaw("    .bend")
            emitRaw("")
        }

        // memory slabs in BSS_SLABS section
        val slabs = program.st.allMemorySlabs().toList()
        if (slabs.isNotEmpty()) {
            emitRaw("    .section BSS_SLABS")
            emitRaw("prog8_slabs  .block")
            for (slab in slabs) {
                emitAlign(slab.align)
                val label = fixNameSymbols(slab.name)
                val localLabel = label.substringAfter('.')
                emitLine("$localLabel  .fill  ${slab.size}")
            }
            emitRaw("    .bend")
            emitRaw("    .send BSS_SLABS")
            emitRaw("")
        }

        // struct instances without init values -> BSS (zeroed at startup)
        val allInstances = program.st.allStructInstances().toList()
        val (instancesNoInit, instancesWithInit) = allInstances.partition { it.values.isEmpty() }
        if (instancesNoInit.isNotEmpty()) {
            emitRaw("    .section BSS")
            emitRaw("${REGFILE_LABEL}_structinst_bss  .block")
            for (si in instancesNoInit) {
                val label = fixNameSymbols(si.name)
                emitLine("$label  .fill  ${si.size}")
            }
            emitRaw("    .bend")
            emitRaw("    .send BSS")
            emitRaw("")
        }

        // struct instances with init values -> STRUCTINSTANCES section
        if (instancesWithInit.isNotEmpty()) {
            emitRaw("    .section STRUCTINSTANCES")
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
                            emitLine("    .word  ${fixNameSymbols(fv.name)}")
                        }
                        is IRStSymbolicReference.BoolValue -> {
                            val v = if (fv.value) 1 else 0
                            emitLine("    .byte  $v")
                        }
                    }
                }
            }
            emitRaw("    .bend")
            emitRaw("    .send STRUCTINSTANCES")
            emitRaw("")
        }
    }

    private fun emitAlign(align: UInt) {
        if (align > 1u) {
            emitLine(".align  ${align.toHex()}")
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
                        emitLine("$label  .null  \"${init.text}\"")
                    }
                    else -> emitLine("$label  .byte  ?")
                }
            }

            dt.isArray -> {
                val hasExplicitInit = init is IRVariableInitializer.Array
                val values = when (init) {
                    is IRVariableInitializer.Array -> init.elements.map {
                        when (it) {
                            is IRStSymbolicReference.Numeric -> {
                                val v = it.value.toInt()
                                if(dt.elementType().isByteOrBool)
                                    asmHexByte(v)
                                else
                                    "${v}"
                            }
                            is IRStSymbolicReference.BoolValue -> if (it.value) "1" else "0"
                            is IRStSymbolicReference.Symbol -> asmSymbolRef(it.name)
                        }
                    }
                    else -> {
                        val numElements = v.length?.toInt() ?: 0
                        List(numElements.coerceAtLeast(1)) { "0" }
                    }
                }
                val directive = when {
                    dt.elementType().isUnsignedByte || dt.elementType().isBool -> ".byte"
                    dt.elementType().isSignedByte -> ".char"
                    dt.elementType().isUnsignedWord || dt.elementType().isPointer -> ".word"
                    dt.elementType().isSignedWord -> ".sint"
                    dt.elementType().isLong -> ".dint"
                    else -> ".byte"
                }
                // Use .fill for zero-initialized arrays (no explicit init values)
                if (!hasExplicitInit && values.all { it == "0" || it == "\$00" }) {
                    emitLine("$label  .fill  ${values.size}")
                } else if (values.size <= 16) {
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
                emitLine("$label  .byte  $bytes")
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

    private fun emitZeropageVariables() {
        val currentVarNames = program.st.allVariables().map { it.name }.toSet()
        val zpVars = zpAllocations
            .filter { it.key in currentVarNames }
            .toList()
            .sortedBy { it.second.address }

        if (zpVars.isEmpty()) return

        emitRaw("; zeropage variables")
        for ((scopedName, alloc) in zpVars) {
            if (scopedName.startsWith("cx16.r")) continue
            val label = fixNameSymbols(scopedName)
            if (alloc.dt.isSplitWordArray) {
                val lsbAddr = alloc.address
                val msbAddr = alloc.address + (alloc.size.toUInt() / 2u)
                emitLine("${label}_lsb  = $lsbAddr")
                emitLine("${label}_msb  = $msbAddr")
            } else {
                emitLine("$label  = ${alloc.address.toHex()}")
            }
        }
        emitRaw("")
    }

    private fun emitBssSection() {
        val options = program.options

        // define section contents (collected by .dsection in the footer)
        val bssVars = program.st.allVariables().filter { it.inBss && !isZpVar(it.name) }.toList()
        emitBssVars(bssVars)

        emitRaw("")
        emitRaw("  .dsection STRUCTINSTANCES")
        emitRaw("")
        emitRaw("; bss sections")
        emitRaw("PROG8_VARSHIGH_RAMBANK = ${options.varsHighBank ?: 1}")

        val relocateVars = options.varsGolden || options.varsHighBank != null
        val relocateSlabs = options.slabsGolden || options.slabsHighBank != null

        if (relocateVars) {
            if (!relocateSlabs)
                emitRaw("  .dsection BSS_SLABS")
            emitLabel("prog8_program_end")
            val relocatedStart = if (options.varsGolden) options.compTarget.BSSGOLDENRAM_START
                                 else options.compTarget.BSSHIGHRAM_START
            emitRaw("  * = ${relocatedStart.toHex()}")
            emitRaw("  .dsection BSS_NOCLEAR")
            emitLabel("prog8_bss_section_start")
            emitRaw("  .dsection BSS")
            if (relocateSlabs)
                emitRaw("  .dsection BSS_SLABS")
            val relocatedEnd = if (options.varsGolden) options.compTarget.BSSGOLDENRAM_END
                               else options.compTarget.BSSHIGHRAM_END
            emitLine("    .cerror * > ${relocatedEnd.toHex()}, \"too many variables/data for BSS section\"")
            emitRaw("prog8_bss_section_size = * - prog8_bss_section_start")
        } else {
            emitRaw("  .dsection BSS_NOCLEAR")
            emitLabel("prog8_bss_section_start")
            emitRaw("  .dsection BSS")
            emitRaw("prog8_bss_section_size = * - prog8_bss_section_start")
            if (!relocateSlabs)
                emitRaw("  .dsection BSS_SLABS")
            emitLabel("prog8_program_end")
            if (relocateSlabs) {
                val relocatedStart = if (options.slabsGolden) options.compTarget.BSSGOLDENRAM_START
                                     else options.compTarget.BSSHIGHRAM_START
                val relocatedEnd = if (options.slabsGolden) options.compTarget.BSSGOLDENRAM_END
                                   else options.compTarget.BSSHIGHRAM_END
                emitRaw("  * = ${relocatedStart.toHex()}")
                emitRaw("  .dsection BSS_SLABS")
                emitLine("    .cerror * > ${relocatedEnd.toHex()}, \"too many data for BSS_SLABS section\"")
            }
        }

        emitRaw("")
        emitAsmSymbols()
        emitRaw("")
        if (options.memtopAddress > 0u) {
            val memtopHex = "${options.memtopAddress.toHex()}"
            emitLine("    .cerror * >= $memtopHex, \"Program too long by \", * - ${(options.memtopAddress - 1u).toHex()}, \" bytes, memtop=${options.memtopAddress.toHex()}\"")
        }
        emitRaw("")
    }

    private fun emitBssVars(vars: List<IRStStaticVariable>) {
        // BSS_NOCLEAR: dirty variables first, then the register file.
        // Both go into the same .section/.send block so the regfile label
        // sits AFTER the dirty vars and the regfile data starts cleanly.
        val (dirty, clean) = vars.partition { it.dirty }
        if (dirty.isNotEmpty() || regFileLayout.totalSize > 0) {
            emitRaw("    .section BSS_NOCLEAR")
            if (dirty.isNotEmpty()) {
                emitRaw("; dirty variables (not cleared at subroutine entry)")
                for (v in dirty) {
                    emitAlign(v.align)
                    emitUninitializedVariable(v)
                }
            }
            // Virtual register file label and data must be inside the same
            // section so the label resolves to the start of the regfile data,
            // not to the start of the section (which would overlap with the
            // dirty variables above and corrupt them).
            emitLabel(REGFILE_LABEL)
            emitLine(".fill  ${regFileLayout.totalSize}")
            emitRaw("    .send BSS_NOCLEAR")
            emitRaw("")
        }

        if (clean.isNotEmpty()) {
            emitRaw("    .section BSS")
            for (v in clean) {
                emitAlign(v.align)
                emitUninitializedVariable(v)
            }
            emitRaw("    .send BSS")
            emitRaw("")
        }
    }

    private fun emitUninitializedVariable(v: IRStStaticVariable) {
        val label = fixNameSymbols(v.name)
        val dt = v.dt

        val (directive, count) = when {
            dt.isSplitWordArray -> {
                // two separate byte arrays: _lsb and _msb
                val numElements = v.length?.toInt() ?: 1
                emitLine("${label}_lsb  .fill  $numElements")
                emitLine("${label}_msb  .fill  $numElements")
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
            emitLine("$label  .fill  $count")
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
