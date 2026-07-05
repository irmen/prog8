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
 * TEMPORARY: All virtual registers are spilled to memory in the register file.
 * This is extremely wasteful - every operation on a LONG value requires
 * 4 separate load/op/store sequences from/to memory. A proper register
 * allocator should assign frequently-used virtual registers to real CPU
 * registers (zeropage or A/X/Y) to eliminate this spilling overhead.
 * The current approach is a placeholder until register allocation is implemented.
 *
 * IMPORTANT: inline asmsubs must be inlined at the call site, NOT emitted as subroutines.
 * An `inline asmsub` in Prog8 source has its assembly body inserted directly at the call
 * location - no jsr, no .proc/.pend, no rts. The old codegen (FunctionCallAsmGen.kt)
 * checks `sub.inline` and handles this. The IR does NOT preserve the `inline` flag, so
 * the new codegen must either:
 *   a) Add an INLINE attribute to ASMSUB in the IR format, or
 *   b) Rely on the optimizer to inline these before IR generation.
 * Currently inline asmsubs are emitted as regular subroutines which is WRONG - they lack
 * an rts and their return values in A/X/Y are not captured correctly.
 *
 * Instruction translation is dispatched to extension functions in separate files:
 *   InstrLoadStore.kt, InstrArithmetic.kt, InstrBitwise.kt, InstrBranch.kt, InstrControl.kt
 */

package prog8.codegen.new6502

import prog8.code.core.*
import prog8.codegen.new6502.optimization.PeepholeOptimizer
import prog8.intermediate.*

internal class AsmGen(val program: IRProgram, private val target: ICompilationTarget) {
    private val output = StringBuilder()
    private val cpu get() = target.cpu
    val floatMemSize: Int get() = target.FLOAT_MEM_SIZE.toInt()

    companion object {
        const val REGFILE_LABEL = "p8_regfile"
        const val FP_REGFILE_LABEL = "p8_fpregfile"
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

    // On C64/PET32 the ROM float math routines that use both accumulators (FADDT, FMULTT, FDIVT)
    // skip CONUPK and need the NZ wrapper to set up arisgn and Z flag beforehand.
    // CX16 ROM entries are already fixed and handle this internally.
    val floatTMathSuffix: String get() =
        if(target.name in listOf("c64", "pet32")) "_NZ" else ""

    // On C64/PET32, FAC2 must be loaded last via CONUPK for correct math results
    // and bare T-variant entries need arisgn+Z flag set up (NZ wrapper).
    // On CX16, MOVAF rounds the LSB ("rounded the least significant bit"), so we don't use MOVAF.
    // CONUPK directly loads into FAC2 and is safe on all targets.
    // We use pushFAC1/popFAC (via CONUPK) on C64/PET32 for correct operand ordering (CONUPK quirk),
    // and direct CONUPK loading into FAC2 on other targets.
    val useC64PushPopOperands: Boolean get() =
        target.name in listOf("c64", "pet32")

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
            if (regNum.value < 0) continue
            offsets[regNum.value] = currentOffset
            currentOffset += dataTypeSize(type)
        }
        RegFileLayout(offsets, currentOffset)
    }

    fun generate(): Boolean {
        PeepholeOptimizer(program).optimize()
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

        return true
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
        // 64tass anonymous labels are single + (forward) or - (backward) only in label definitions.
        // In references you can use ++, +++, --, --- to refer to subsequent ones.
        require(label.length == 1 || label.any { it != '+' && it != '-' }) {
            "Invalid anonymous label '$label': only single '+' or '-' are allowed as label definitions"
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

    /** Emit CONUPK to load a float from an fp register directly into FAC2. */
    fun emitLoadFAC2FromFpReg(fpReg: Int) {
        emitLine("lda  #<${fpRegAddr(fpReg)}")
        emitLine("ldy  #>${fpRegAddr(fpReg)}")
        emitLine("jsr  floats.CONUPK")
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

    // === float constant emission ===

    private val floatConsts = mutableMapOf<Double, String>()

    fun getFloatConstLabel(value: Double): String {
        val existing = floatConsts[value]
        if (existing != null) return existing
        val label = "prog8_float_const_${floatConsts.size}"
        floatConsts[value] = label
        return label
    }

    // === FP register file layout ===

    private val fpRegFileLayout: RegFileLayout by lazy {
        val regs = program.registersUsed()
        val allFpNums = (regs.readFpRegs.keys + regs.writeFpRegs.keys).map { it.value }.distinct().sorted()
        val offsets = mutableMapOf<Int, Int>()
        var currentOffset = 0
        val floatSize = target.FLOAT_MEM_SIZE.toInt()
        for (regNum in allFpNums) {
            offsets[regNum] = currentOffset
            currentOffset += floatSize
        }
        RegFileLayout(offsets, currentOffset)
    }

    fun fpRegAddr(reg: Int): String {
        val offset = fpRegFileLayout.offsets[reg]
            ?: error("fp register fr$reg has no layout info")
        return "$FP_REGFILE_LABEL+$offset"
    }

    private val fpRegFileTotalSize: Int get() = fpRegFileLayout.totalSize

    /** Zero-page temporary pointer address for address computation (2 bytes). */
    val ZP_TEMP: String get() = target.zeropage.SCRATCH_PTR.toHex()

    /** Format a byte value as `$xx` hex for .byte directives (always hex, even for 0-15). */
    private fun asmHexByte(v: Int): String = $$"$$${v.toUByte().toString(16).padStart(2, '0')}"

    // === CPU-aware instruction helpers ===
    // 65C02 supports stz, ina, dea; plain 6502 needs longer sequences

    fun emitStoreZero(target: String) {
        if (cpu == CpuType.CPU65C02)
            emitLine("stz  $target")
        else {
            emitLine("lda  #0")
            emitLine("sta  $target")
        }
    }

    fun emitIncrementA() {
        if (cpu == CpuType.CPU65C02)
            emitLine("ina")
        else {
            emitLine("clc")
            emitLine("adc  #1")
        }
    }

    fun emitDecrementA() {
        if (cpu == CpuType.CPU65C02)
            emitLine("dea")
        else {
            emitLine("sec")
            emitLine("sbc  #1")
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
            else -> throw IllegalArgumentException("invalid cpu type for this code generator: $cpu")
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
        if(target.name=="c64") {
            if(options.floats)
                emitRaw("PROG8_C64_BANK_CONFIG=31  ; basic+IO+kernal")
            else
                emitRaw("PROG8_C64_BANK_CONFIG=30  ; IO+kernal, no basic")
        }
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
                        emitRaw($$"    .null  $9e, format(' %d ', prog8_entrypoint), $3a, $8f, ' prog8'")
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
        }
        emitLine("jsr  p8_sys_startup.init_system_phase2")
        if (program.options.zeropage !in arrayOf(ZeropageType.BASICSAFE, ZeropageType.DONTUSE)) {
            emitLine("lda  #>sys.reset_system")
            emitLine("pha")
            emitLine("lda  #<sys.reset_system")
            emitLine("pha")
        }
        if (target.name=="cx16" && program.options.floats) {
            emitLine("lda  #4")
            emitLine("sta  $01")    // to use floats, make sure Basic rom is banked in
        }
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

    private fun emitZpVariableInits() {
        val zpInitVars = program.st.allVariables().filter { v ->
            isZpVar(v.name) && v.initializationValue != null
        }.toList()
        if (zpInitVars.isEmpty()) return

        emitRaw("; initialize zeropage variables with initial values")
        for (v in zpInitVars.sortedBy { it.name }) {
            val label = fixNameSymbols(v.name)
            val init = v.initializationValue ?: continue
            when (init) {
                is IRVariableInitializer.Numeric -> {
                    val value = init.value.toInt()
                    when {
                        v.dt.isByte || v.dt.isBool -> {
                            emitZeroOrValue(value, label)
                        }
                        v.dt.isWord || v.dt.isPointer -> {
                            if (value == 0) {
                                emitStoreZero(label)
                                emitStoreZero("${label}+1")
                            } else {
                                emitLine("lda  #<${value.toHex()}")
                                emitLine("sta  $label")
                                emitLine("lda  #>${value.toHex()}")
                                emitLine("sta  ${label}+1")
                            }
                        }
                        v.dt.isLong -> {
                            for (i in 0 until 4) {
                                val byteVal = (value shr (i * 8)) and 0xFF
                                emitZeroOrValue(byteVal, "${label}+$i")
                            }
                        }
                        v.dt.isFloat -> {
                            val bytes = target.getFloatAsmBytes(init.value)
                            for ((i, b) in bytes.split(",").withIndex()) {
                                emitLine("lda  #$b")
                                emitLine("sta  ${label}+$i")
                            }
                        }
                        else -> {}
                    }
                }
                is IRVariableInitializer.Str, is IRVariableInitializer.Array -> {
                    // String/array ZP inits are handled by generateStringArrayInits in IRCodeGen
                    // which creates shadow variables and MEMCOPY instructions.
                }
            }
        }
    }

    private fun emitZeroOrValue(value: Int, label: String) {
        if (value == 0)
            emitStoreZero(label)
        else {
            emitLine("lda  #${value.toHex()}")
            emitLine("sta  $label")
        }
    }

    private fun emitCode() {
        emitLabel("run_global_inits")
        translateChunk(program.globalInits)
        emitZpVariableInits()
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
            // Collect all top-level subroutine labels to check for nesting
            val topLevelSubLabels = block.children.filterIsInstance<IRSubroutine>().map { it.label }.toSet()
            for (element in block.children) {
                when (element) {
                    is IRSubroutine -> {
                        // Skip subroutines that are nested inside another top-level subroutine
                        val isNested = topLevelSubLabels.any { subLabel ->
                            subLabel != element.label && element.label.startsWith("$subLabel.")
                        }
                        if (!isNested) emitSubroutine(element)
                    }
                    is IRAsmSubroutine -> {
                        // Skip inline ASMSUBs - they are inlined at the call site, not emitted as subroutines
                        if (element.isInline) continue
                        // Skip ASMSUBs that are nested inside a top-level subroutine
                        val isNested = topLevelSubLabels.any { subLabel -> element.label.startsWith("$subLabel.") }
                        if (!isNested) emitAsmSubroutine(element)
                    }
                    is IRCodeChunk -> {
                        if (element.label != null) emitLabel(element.label!!)
                        translateChunk(element)
                    }
                    is IRInlineAsmChunk -> emitRaw(element.assembly)
                    is IRInlineBinaryChunk -> emitRaw("    .byte  ${element.data.joinToString(",") { asmHexByte(it.toInt()) }}")
                }
            }
            val endDirective = if (block.options.forceOutput) ".bend" else ".pend"
            emitRaw("  $endDirective")
            emitRaw("")
        }
    }

    private fun unscopedName(scopedName: String): String =
        scopedName.substringAfterLast('.')

    /** Check if a child element (SUB or ASMSUB) is nested inside a parent subroutine */
    private fun isNestedChild(childLabel: String, parentSub: IRSubroutine): Boolean =
        childLabel.startsWith(parentSub.label + ".")

    private fun emitSubroutine(sub: IRSubroutine) {
        emitRaw("; Subroutine: ${sub.label}")
        val firstChunk = sub.chunks.filterIsInstance<IRCodeChunk>().firstOrNull()
        if (firstChunk != null)
            emitSourceComment(firstChunk.sourceLinesPositions)
        emitRaw("")
        emitRaw("${sub.label}  .proc")
        if (sub.label == "p8b_main.p8s_start") {
            // these need to happen even if something calls main.start directly (without startup logic - for example if this is a library)
            emitLine("jsr  prog8_lib.program_startup_clear_bss")
            emitLine("jsr  run_global_inits")
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
        // Emit nested SUBs and ASMSUBs (those whose name starts with this subroutine's label + ".")
        for (block in program.blocks) {
            for (element in block.children) {
                if (element is IRSubroutine && isNestedChild(element.label, sub)) {
                    emitNestedSubroutine(element)
                } else if (element is IRAsmSubroutine && isNestedChild(element.label, sub)) {
                    emitRaw("")
                    emitRaw("    ; source: ${element.position}")
                    emitRaw("    ${unscopedName(element.label)}  .proc")
                    emitRaw(element.asmChunk.assembly)
                    emitRaw("    .pend")
                }
            }
        }
        emitRaw(".pend")
        emitRaw("")
    }

    /** Emit a nested subroutine (uses unscoped name since it's inside parent scope) */
    private fun emitNestedSubroutine(sub: IRSubroutine) {
        emitRaw("")
        emitRaw("    ; source: ${sub.position}")
        emitRaw("    ${unscopedName(sub.label)}  .proc")
        for (chunk in sub.chunks) {
            when (chunk) {
                is IRCodeChunk -> {
                    val cl = chunk.label
                    if (cl != null && cl != sub.label) emitRaw("    $cl:")
                    for (insn in chunk.instructions) {
                        emitRaw("        ; $insn")
                        translateInstruction(insn)
                    }
                }
                is IRInlineAsmChunk -> {
                    val cl = chunk.label
                    if (cl != null && cl != sub.label) emitRaw("    $cl:")
                    chunk.assembly.lineSequence().forEach { line ->
                        if (line.isNotBlank()) emitRaw("    $line")
                    }
                }
                is IRInlineBinaryChunk -> {
                    val cl = chunk.label
                    if (cl != null) emitRaw("    $cl:")
                    emitRaw("    .byte  ${chunk.data.joinToString(",") { asmHexByte(it.toInt()) }}")
                }
            }
        }
        emitRaw("    .pend")
    }

    private fun emitAsmSubroutine(sub: IRAsmSubroutine) {
        // Skip ASMSUBs that are nested inside a subroutine - they are emitted by emitSubroutine
        for (block in program.blocks) {
            for (element in block.children) {
                if (element is IRSubroutine && isNestedChild(sub.label, element))
                    return
            }
        }
        val addr = sub.address
        if (addr != null) {
            emitLine("* = $addr")
        }
        emitRaw("")
        emitRaw("${sub.label}  .proc")
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
            Opcode.ADDR, Opcode.PTRADD, Opcode.ADD, Opcode.ADDM,
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

    private fun emitFloatConstants() {
        if (floatConsts.isEmpty()) return
        emitRaw("; float constants")
        for ((value, label) in floatConsts) {
            val bytes = target.getFloatAsmBytes(value)
            emitLine("$label  .byte  $bytes", "float $value")
        }
        emitRaw("")
    }

    // === data section ===

    private fun emitDataSection() {
        emitStructDefs()
        emitRaw("")
        emitFloatConstants()

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
            emitRaw("prog8_struct_instances_bss  .block")
            for (si in instancesNoInit) {
                val label = fixNameSymbols(si.name)
                val instName = label.substringAfter('.')
                emitLine("$instName  .fill  ${si.size}")
            }
            emitRaw("    .bend")
            emitRaw("    .send BSS")
            emitRaw("")
        }

        // struct instances with init values -> STRUCTINSTANCES section
        if (instancesWithInit.isNotEmpty()) {
            emitRaw("    .section STRUCTINSTANCES")
            emitRaw("prog8_struct_instances  .block")
            for (si in instancesWithInit) {
                val label = fixNameSymbols(si.name)
                val instName = label.substringAfter('.')
                emitLabel(instName)
                for (fieldValue in si.values) {
                    when (val fv = fieldValue.value) {
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
                val values: List<String> = when (init) {
                    is IRVariableInitializer.Array -> init.elements.map {
                        when (it) {
                            is IRStSymbolicReference.Numeric -> it.value.toInt().toString()
                            is IRStSymbolicReference.BoolValue -> if (it.value) "1" else "0"
                            is IRStSymbolicReference.Symbol -> fixNameSymbols(it.name)
                        }
                    }
                    else -> List(halfBytes) { "0" }
                }
                val parts = values.joinToString(",")
                val arrLabel = label.replace(".", "_") + "_init_words"
                emitRaw("$arrLabel := $parts")
                emitLine("${label}_lsb  .byte  <$arrLabel")
                emitLine("${label}_msb  .byte  >$arrLabel")
            }

            dt.isString -> {
                when (init) {
                    is IRVariableInitializer.Str -> {
                        val bytes = program.encoding.encodeString(init.text, init.encoding) + 0.toUByte()
                        val hexBytes = bytes.map { "$${it.toString(16).padStart(2, '0')}" }
                        emitRaw("$label\t; ${init.encoding.name}:\"${init.text.escape()}\"")
                        for (chunk in hexBytes.chunked(16)) {
                            emitLine(".byte  ${chunk.joinToString(", ")}")
                        }
                    }
                    else -> emitLine("$label  .byte  ?")
                }
            }

            dt.isArray -> {
                val hasExplicitInit = init is IRVariableInitializer.Array
                if (dt.elementType().isFloat) {
                    val bytes = when (init) {
                        is IRVariableInitializer.Array -> init.elements.map {
                            when (it) {
                                is IRStSymbolicReference.Numeric -> target.getFloatAsmBytes(it.value)
                                else -> List(target.FLOAT_MEM_SIZE.toInt()) { 0 }.joinToString(",") { $$"$00" }
                            }
                        }.flatMap { it.split(",").map { s -> s.trim() } }
                        else -> {
                            val numElements = v.length?.toInt() ?: 0
                            (0 until numElements).flatMap {
                                target.getFloatAsmBytes(0.0).split(",").map { s -> s.trim() }
                            }
                        }
                    }
                    val floatSize = target.FLOAT_MEM_SIZE.toInt()
                    emitLabel(label)
                    for (chunk in bytes.chunked(floatSize)) {
                        emitLine("  .byte  ${chunk.joinToString(", ")}")
                    }
                } else {
                    val values = when (init) {
                        is IRVariableInitializer.Array -> init.elements.map {
                            when (it) {
                                is IRStSymbolicReference.Numeric -> {
                                    val v = it.value.toInt()
                                    if(dt.elementType().isByteOrBool)
                                        asmHexByte(v)
                                    else
                                        "$v"
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
                    if (!hasExplicitInit && values.all { it == "0" || it == $$"$00" }) {
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
            }

            dt.isFloat -> {
                when (init) {
                    is IRVariableInitializer.Numeric -> {
                        val bytes = target.getFloatAsmBytes(init.value)
                        emitLine("$label  .byte  $bytes  ; float ${init.value}")
                    }
                    else -> {
                        val bytes = List(target.FLOAT_MEM_SIZE.toInt()) { 0 }.joinToString(",") { $$"$00" }
                        emitLine("$label  .byte  $bytes  ; float 0")
                    }
                }
            }

            else -> {
                // single numeric value (byte/word/long)
                val value = when (init) {
                    is IRVariableInitializer.Numeric -> init.value.toInt()
                    else -> 0
                }
                val directive = when {
                    dt.isByteOrBool -> ".byte"
                    dt.isWord || dt.isPointer -> ".word"
                    dt.isLong -> ".long"
                    else -> TODO("unexpected dt for var: $dt  ${v.name}")
                }
                emitLine("$label  $directive $value")
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
            val memtopHex = options.memtopAddress.toHex()
            emitLine("    .cerror * >= $memtopHex, \"Program too long by \", * - ${(options.memtopAddress - 1u).toHex()}, \" bytes, memtop=${options.memtopAddress.toHex()}\"")
        }
        emitRaw("")
    }

    private fun emitBssVars(vars: List<IRStStaticVariable>) {
        // BSS_NOCLEAR: dirty variables first, then the register file.
        // Both go into the same .section/.send block so the regfile label
        // sits AFTER the dirty vars and the regfile data starts cleanly.
        // TODO: replace the register file with proper register allocation
        // so that frequently-used virtual registers are mapped to real CPU
        // registers instead of always spilling to memory.
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
            // FP register file (separate from integer regs, same size per register)
            if (fpRegFileTotalSize > 0) {
                emitLabel(FP_REGFILE_LABEL)
                emitLine(".fill  $fpRegFileTotalSize")
            }
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

        // Emit temporary float storage (5 bytes in BSS) for FFROMSL/FTOSL
        emitRaw("    .section BSS")
        emitRaw("prog8_fp_temp  .byte  0,0,0,0,0")
        emitRaw("    .send BSS")
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
                val elementSize = if (dt.elementType().isFloat) target.FLOAT_MEM_SIZE.toInt() else 1
                val sz = (v.length ?: 1u).toInt() * elementSize
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
}
