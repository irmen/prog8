package prog8.codegen.m68k

import prog8.code.core.*
import prog8.intermediate.*
import kotlin.math.max

internal const val FP_ACC = "fp0"   // primary FPU scratch / accumulator
internal const val FP_SRC = "fp1"   // secondary FPU scratch / source operand

/**
 * Describes an atomic immediate-argument forwarding opportunity for one call.
 *
 * [loads] holds the contiguous run of immediate LOAD instructions that directly
 * precede the call (scanned backwards until a non-immediate-LOAD instruction).
 * It may contain registers that are NOT call arguments.
 * For every call argument register, the loaded immediate value is forwarded
 * directly into the argument's hardware register instead of going through the
 * register file (move #imm,dX instead of move p8_regfile+N,dX).
 *
 * [deadRegisters] are the registers from [loads] that are not read anywhere
 * else in the subroutine (their only use is the forwarded call argument);
 * their register-file stores are omitted entirely. Note that this can also
 * remove stores for registers that are not call arguments at all.
 *
 * The read check covers the subroutine's whole flattened instruction list
 * rather than only the part after the call (see `isRegisterReadElsewhere`),
 * so it is independent of control flow and remains sound in the presence of
 * backward branches (loops) and arbitrary branch targets. Suppression is
 * disabled for subroutines containing inline assembly chunks that can read
 * virtual registers, because those reads are invisible to the analysis
 * (see `canSuppressDeadStores`); the immediate forwarding itself does not
 * depend on liveness and is still applied in those cases.
 *
 * Floating-point registers are intentionally not included; they use the
 * separate floating-point register file and require FPU-specific constants.
 */
internal data class ImmediateCallOptimization(
    val loads: Map<Int, IRInstruction>,
    val deadRegisters: Set<Int>
)

/**
 * Returns the 68881 fmovecr ROM constant encoding for common float values
 * (from the Motorola MC68881/MC68882 User's Manual Table 4-2),
 * or null if the value is not a native constant and must go through the constant pool.
 *
 * This lets the codegen emit a single `fmovecr #imm, fpN` instruction instead of
 * loading from the constant pool (lea + fmove.s).
 */
internal fun nativeFloatConst(value: Double): String? = when (value) {
    0.0 -> "\$0f"               // 0.0
    1.0 -> "\$32"               // 10^0 = 1.0
    10.0 -> "\$33"              // 10^1
    100.0 -> "\$34"             // 10^2
    10000.0 -> "\$35"           // 10^4
    1.0e8 -> "\$36"             // 10^8
    1.0e16 -> "\$37"            // 10^16
    1.0e32 -> "\$38"            // 10^32
    1.0e64 -> "\$39"            // 10^64
    1.0e128 -> "\$3a"           // 10^128
    1.0e256 -> "\$3b"           // 10^256
    1.0e512 -> "\$3c"           // 10^512
    kotlin.math.PI -> "\$00"    // pi
    kotlin.math.E -> "\$0c"     // e
    kotlin.math.ln(2.0) -> "\$30"        // ln(2)
    kotlin.math.ln(10.0) -> "\$31"       // ln(10)
    kotlin.math.log10(kotlin.math.E) -> "\$0d"   // log2(e)
    kotlin.math.log10(2.0) -> "\$0b"     // log10(2)
    kotlin.math.log10(kotlin.math.E) -> "\$0e"   // log10(e)
    else -> null
}

/**
 * M68k codegen.
 * Targeting the QEMU M68k 'virt' system simulator.
 * For more info and a fully working example kernal assembly source as reference, see the documentation in the 'docs' directory of this module!
 *
 * Calling convention: follows the Prog8 calling convention as described in the docs.
 * There is NO stack handling involved in the calling convention (the CPU stack holds only return addresses from jsr/rts).
 * Arguments are put into the subroutine's parameter variables (BSS) by the caller before the jsr.
 * For asmsub/extsub calls with hardware register slots, the argument is loaded into the mapped register (D0-D2, FP0-FP1)
 * instead. Return values are passed via virtual registers mapped back to the caller's result register.
 */

internal class AsmGen(val program: IRProgram, internal val target: ICompilationTarget) {
    private val output = StringBuilder()
    private val regsUsed by lazy { program.registersUsed() }
    internal val cpu get() = target.cpu

    companion object {
        const val REGFILE_LABEL = "p8_regfile"
        const val FLOAT_REGFILE_LABEL = "p8_fregfile"
    }

    init {
        require(target.cpu.is68k) { "M68k codegen requires M680x0 cpu, got ${target.cpu}" }
    }

    private var labelSeqCounter = 0
    private var lastSourceLine = -1
    val dataFloatConstants = mutableListOf<Pair<String, Double>>()

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
        emitRaw("; source: $fileOnly:${pos.line}")
    }

    fun emitLine(code: String, comment: String = "") {
        val clean = code.replace(", ", ",")
        output.append("    ")
        output.append(clean)
        if (comment.isNotEmpty()) {
            output.append("        ; ")
            output.append(comment)
        }
        output.appendLine()
    }

    fun emitLabel(label: String) {
        output.appendLine()
        output.appendLine("$label:")
    }

    fun emitRaw(code: String) {
        output.appendLine(code)
    }

    // === virtual register file layout (1, 2 or 4 bytes per slot depending on type, word-aligned) ===
    private data class RegFileLayout(val offsets: Map<Int, Int>, val totalSize: Int)

    private fun slotSizeForType(type: IRDataType): Int = when(type) {
        IRDataType.BYTE -> 1
        IRDataType.WORD -> 2
        IRDataType.LONG -> 4
        IRDataType.FLOAT -> target.FLOAT_MEM_SIZE.toInt()
        IRDataType.POINTER -> target.POINTER_MEM_SIZE.toInt()
    }

    private val regFileLayout: RegFileLayout by lazy {
        val allRegs = regsUsed.regsTypes
        val offsets = mutableMapOf<Int, Int>()
        var currentOffset = 0
        for ((regNum, regType) in allRegs.entries.sortedBy { it.key.value }) {
            if (regNum.value < 0) continue
            // word-align each slot
            currentOffset = (currentOffset + 1) / 2 * 2
            offsets[regNum.value] = currentOffset
            currentOffset += slotSizeForType(regType)
        }
        RegFileLayout(offsets, currentOffset)
    }

    private val floatRegFileLayout: RegFileLayout by lazy {
        val used = regsUsed
        val allFpRegs = mutableMapOf<RegisterNum, IRDataType>()
        for (reg in used.readFpRegs.keys + used.writeFpRegs.keys) {
            allFpRegs[reg] = IRDataType.FLOAT
        }
        val offsets = mutableMapOf<Int, Int>()
        var currentOffset = 0
        for ((regNum, _) in allFpRegs.entries.sortedBy { it.key.value }) {
            // word-align each slot
            currentOffset = (currentOffset + 1) / 2 * 2
            offsets[regNum.value] = currentOffset
            currentOffset += target.FLOAT_MEM_SIZE.toInt()
        }
        RegFileLayout(offsets, currentOffset)
    }

    fun regAddr(reg: Int): String {
        val offset = regFileLayout.offsets[reg] ?: error("register r$reg has no layout info")
        return "$REGFILE_LABEL+$offset"
    }

    fun regAddrByte(reg: Int, byteOffset: Int): String {
        val offset = regFileLayout.offsets[reg] ?: error("register r$reg has no layout info")
        return "$REGFILE_LABEL+${offset + byteOffset}"
    }

    fun loadPointerToA0(reg: Int) {
        // load a 32-bit pointer from the register file into a0
        val offset = regFileLayout.offsets[reg] ?: error("register r$reg has no layout info")
        val addr = if (offset == 0) REGFILE_LABEL else "$REGFILE_LABEL+$offset"
        emitLine("movea.l  $addr, a0")
    }


    // === label/symbol helpers ===

    internal fun fixNameSymbols(name: String): String = name.replace("::", "_")

    
    fun resolveSymbolRef(name: String): String {
        val node = program.st.lookup(name)
        return when (node) {
            is IRStMemVar -> {
                val addr = node.address.toInt()
                $$"$${addr.toUInt().toString(16).padStart(8, '0')}"
            }
            is IRStConstant -> {
                if (node.memorySlabName != null)
                    constLabel(name)
                else {
                    val addr = node.value!!.toInt()
                    $$"$${addr.toUInt().toString(16).padStart(8, '0')}"
                }
            }
            is IRStStructInstance, is IRStStaticVariable, is IRStMemorySlab -> fixNameSymbols(name)
            else -> fixNameSymbols(name)
        }
    }

    fun constLabel(name: String): String = "p8c_${fixNameSymbols(name)}"

    fun resolveAddress(addr: MemoryAddress?, label: String?, offset: Int? = null): String {
        return when {
            label != null -> {
                val resolved = resolveSymbolRef(label)
                if (offset != null && offset != 0) "$resolved+$offset" else resolved
            }
            addr != null -> addr.value.toHex()
            else -> "0"
        }
    }

    // === FPU helpers (M680x0 with 68881/68882) ===

    fun floatRegFileAddr(reg: RegisterNum): String {
        val offset = floatRegFileLayout.offsets[reg.value] ?: error("float register fr${reg.value} has no layout info")
        return "$FLOAT_REGFILE_LABEL+$offset"
    }

    fun emitFloadConstantToAcc(value: Double) {
        val native = nativeFloatConst(value)
        if (native != null) {
            emitLine("fmovecr  #$native, $FP_ACC")
        } else {
            val label = makeFloatConstLabel(value)
            emitLine("lea  $label, a0")
            emitLine("fmove.s  (a0), $FP_ACC")
        }
    }

    // === size suffix helpers ===

    fun dtSuffix(type: IRDataType): String = when (type) {
        IRDataType.BYTE -> ".b"
        IRDataType.WORD -> ".w"
        IRDataType.LONG -> ".l"
        IRDataType.FLOAT -> ".f"     // IR float suffix (not the m68k asm ".s" suffix)
        IRDataType.POINTER -> ".l"
    }

    // === main entry point ===

    fun generate(): Boolean {
        emitHeader()
        emitCode()
        emitDataSection()
        emitBssSection()

        if(target.name=="amiga500") {
            emitRaw("prog8_program_end:     ; end of the program")
        } else {
            // label prog8_program_end is defined by the linker script
        }

        val options = program.options
        val asmFile = options.outputDir.resolve("${program.name}.asm")
        try {
            if (options.optimize) {
                val asmLines = output.toString().lines().toMutableList()
                optimizeAssembly(asmLines)
                asmFile.toFile().writeText(asmLines.joinToString("\n") + "\n")
            } else {
                // write the unmodified code
                asmFile.toFile().writeText(output.toString())
            }
        } catch (e: Exception) {
            System.err.println("Failed to write assembly file: ${e.message}")
            return false
        }

        if (!options.quiet)
            println("Assembly written to $asmFile")
        
        return true
    }

    // === header ===

    private fun emitHeader() {
        val options = program.options
        emitRaw("; Program: ${program.name}")
        emitRaw("; Generated by prog8-m68kgen (Motorola 68000 codegen)")
        emitRaw("; Target CPU: ${cpu.name}")
        emitRaw("; Output: ${options.output.name}")
        emitRaw("")
        emitRaw("; Assembler: vasm with Motorola syntax (http://sun.hasenbraten.de/vasm/release/vasm.html)")
        emitRaw("; NOTE: M68k is BIG-ENDIAN — bytes within words/longs are MSB-first")
        emitRaw("; Motorola syntax rules:")
        emitRaw(";   - Operations:  mnemonic  src,dst  (src is first operand, dst is second)")
        emitRaw(";   - NOTE: vasm preferes NO space after the comma between operands (but with -spaces it allows it)")
        emitRaw(";   - Addressing:  Dn=datareg, An=addrreg, (An)=indirect, imm=#value")
        emitRaw(";   - Labels: global = alphanumeric+underscore (add -ldots for dots in labels)")
        emitRaw(";     local = prefix '.' or suffix '$', valid between two global labels")
        emitRaw(";   - Directives:  DC.B, DC.W, DC.L, EQU, '=' for constants, etc.")
        emitRaw("")
        emitRaw("; ASM PEEPHOLE OPTIMIZER")
        emitRaw(";   This codegen runs an asm-level peephole optimizer over the emitted")
        emitRaw(";   instructions. It uses the '; Subroutine:' markers (see below) to delimit")
        emitRaw(";   code units for scope-bounded analyses (e.g. dead register-slot detection).")
        emitRaw(";   Do NOT rename, remove, or otherwise alter those markers, or the")
        emitRaw(";   optimizer may produce incorrect output.")
        emitRaw("")
        emitRaw("    section .text,code")

        // user-supplied symbol definitions
        if (options.symbolDefs.isNotEmpty()) {
            emitRaw("; -- user supplied symbols on the command line")
            for ((name, value) in options.symbolDefs) {
                emitRaw("$name = $value")
            }
            emitRaw("")
        }

        // emit all Prog8 constants as vasm symbols for inline asm use
        emitConstants()

        // Set up stack pointer and jump to program start
        emitLabel("prog8_program_start")
        
        if(options.compTarget.name == "qemu68k")
            emitLine("move.l  #${options.memtopAddress.toHex()}, sp", "initialize stack pointer")
        
        // NOTE: the executable loader already zero-fills the BSS section (LoadSeg on Amiga HUNK, ELF loader on qemu68k); manual clear_bss_section call not needed here.
        
        if (!options.noSysInit)
            emitLine("jsr  ${fixNameSymbols("p8_sys_startup.init_system")}")
        emitLine("jsr  ${fixNameSymbols("p8_sys_startup.init_system_phase2")}")
        emitLine("jsr  ${fixNameSymbols("p8b_main.p8s_start")}")
        emitLine("moveq  #0, d0", "normal return status 0")
        emitLine("jmp  ${fixNameSymbols("p8_sys_startup.cleanup_at_exit")}")
    }

    // === code emission ===

    // Sign-extend a byte to a 32-bit long.
    // extb.l is a 68020+ instruction; on the 68000 use the two-step EXT sequence
    // (ext.w then ext.l) which is functionally equivalent.
    internal fun AsmGen.emitSignExtendByteToLong(reg: String) {
        if (cpu == CpuType.M68000) {
            emitLine("ext.w  $reg")
            emitLine("ext.l  $reg")
        } else {
            emitLine("extb.l  $reg")
        }
    }
    

    private fun emitCode() {
        emitLabel("run_global_inits")
        translateChunk(program.globalInits)
        emitLine("rts")
        emitRaw("")

        for (block in program.blocks) {
            val blockLabel = fixNameSymbols(block.label)
            val chipram = target.name == "amiga500" && block.options.amigaChipram
            if (chipram) {
                emitRaw("    SECTION code_c,code,chip   ; amiga CHIP ram code")
            }
            emitRaw("; Block: $blockLabel")
            emitLabel(blockLabel)
            for (element in block.children) {
                when (element) {
                    is IRSubroutine -> emitSubroutine(element)
                    is IRAsmSubroutine -> {
                        if (element.isInline) continue
                        emitAsmSubroutine(element)
                    }
                    is IRCodeChunk -> {
                        val cl = element.label?.let { fixNameSymbols(it) }
                        if (cl != null) emitLabel(cl)
                        translateChunk(element)
                    }
                    is IRInlineAsmChunk -> emitRaw(element.assembly)
                    is IRInlineBinaryChunk -> {
                        val bytes = element.data.joinToString(",") { "$${it.toString(16).padStart(2, '0')}" }
                        emitLine("dc.b  $bytes")
                    }
                }
            }
            if (chipram) {
                emitRaw("    SECTION .text,code  ; back to normal code section")
            }
            emitRaw("")
        }
    }

    private fun emitSubroutine(sub: IRSubroutine) {
        val subLabel = fixNameSymbols(sub.label)
        val subUnscoped = unscopedName(sub.label)
        // asm-peephole boundary marker — the '; Subroutine:' and '; End of subroutine:'
        // lines emitted here are parsed by the asm peephole optimizer to delimit code
        // units (e.g. for dead register-slot detection). Do not alter/rename/remove them.
        emitRaw("; ---- Subroutine: $subLabel ----")
        val firstChunk = sub.chunks.filterIsInstance<IRCodeChunk>().firstOrNull()
        if (firstChunk != null)
            emitSourceComment(firstChunk.sourceLinesPositions)
        emitRaw("")
        emitLine("ALIGN 2")
        emitLabel(subLabel)
        val entrypointNames = setOf("p8b_main.p8s_start", "main.start")
        if(sub.label in entrypointNames)
            emitLine("jsr  run_global_inits")
        val livenessInstructions = sub.chunks.filterIsInstance<IRCodeChunk>().flatMap { it.instructions }
        val deadStoreSuppressionAllowed = canSuppressDeadStores(sub)
        var instructionOffset = 0
        for (chunk in sub.chunks) {
            when (chunk) {
                is IRCodeChunk -> {
                    val chunkLabel = chunk.label?.let { fixNameSymbols(it) }
                    if (chunkLabel != null && chunkLabel != subLabel && chunkLabel != subUnscoped)
                        emitLabel(chunkLabel)
                    translateChunk(chunk, livenessInstructions, instructionOffset, deadStoreSuppressionAllowed)
                    instructionOffset += chunk.instructions.size
                }
                is IRInlineAsmChunk -> {
                    val cl = chunk.label?.let { fixNameSymbols(it) }
                    if (cl != null && cl != subLabel && cl != subUnscoped) emitLabel(cl)
                    emitRaw(chunk.assembly)
                }
                is IRInlineBinaryChunk -> {
                    val cl = chunk.label?.let { fixNameSymbols(it) }
                    if (cl != null) emitLabel(cl)
                    val bytes = chunk.data.joinToString(",") { "$${it.toString(16).padStart(2, '0')}" }
                    emitLine("dc.b  $bytes")
                }
            }
        }
        emitRaw("; End of subroutine: $subLabel")
        emitRaw("")
    }

    private fun emitAsmSubroutine(sub: IRAsmSubroutine) {
        val asmLabel = fixNameSymbols(sub.label)
        // asm-peephole boundary marker — the '; Subroutine:' and '; End of subroutine:'
        // lines emitted here are parsed by the asm peephole optimizer to delimit code
        // units (e.g. for dead register-slot detection). Do not alter/rename/remove them.
        emitRaw("")
        emitRaw("; ---- Subroutine: $asmLabel ----")
        emitLine("ALIGN 2")
        emitLabel(asmLabel)
        emitRaw(sub.asmChunk.assembly)
        emitRaw("; End of subroutine: $asmLabel")
        emitRaw("")
    }

    private fun unscopedName(scopedName: String): String =
        scopedName.substringAfterLast('.')

    // Dead-store suppression checks whether a register is read anywhere else in the
    // subroutine (see isRegisterReadElsewhere), which is independent of control
    // flow and therefore sound regardless of branch topology (loops, indirect
    // jumps). Inline assembly chunks need special care because their contents are
    // invisible to the analysis; they are only assumed to read virtual registers
    // when they actually can (see inlineAsmMayReadRegisters).
    private fun canSuppressDeadStores(sub: IRSubroutine): Boolean =
        sub.chunks.none { it is IRInlineAsmChunk && inlineAsmMayReadRegisters(it) }

    // The only way inline assembly can access virtual registers is through the
    // register file symbols: IR-form assembly is analyzed for register reads,
    // and raw assembly can only refer to the regfile symbols textually.
    private fun inlineAsmMayReadRegisters(chunk: IRInlineAsmChunk): Boolean {
        if (chunk.isIR) {
            val used = chunk.usedRegisters()
            return used.readRegs.isNotEmpty() || used.readFpRegs.isNotEmpty()
        }
        return REGFILE_LABEL in chunk.assembly || FLOAT_REGFILE_LABEL in chunk.assembly
    }

    private fun translateChunk(
        chunk: IRCodeChunk,
        livenessInstructions: List<IRInstruction> = chunk.instructions,
        instructionOffset: Int = 0,
        deadStoreSuppressionAllowed: Boolean = true
    ) {
        emitSourceComment(chunk.sourceLinesPositions)
        val callOptimizations = mutableMapOf<Int, ImmediateCallOptimization>()
        val deadLoadIndices = mutableSetOf<Int>()
        for (index in chunk.instructions.indices) {
            val insn = chunk.instructions[index]
            if (insn.opcode != Opcode.CALL)
                continue
            val optimization = immediateLoadsForCall(livenessInstructions, instructionOffset + index, instructionOffset, insn.fcallArgs)
                ?: continue
            val effectiveOptimization =
                if (deadStoreSuppressionAllowed) optimization
                else optimization.copy(deadRegisters = emptySet())
            callOptimizations[index] = effectiveOptimization
            var loadIndex = index - 1
            while (loadIndex >= 0) {
                val load = chunk.instructions[loadIndex]
                if (load.opcode != Opcode.LOAD || load.reg1 == null || load.immediate == null)
                    break
                if (load.reg1 in effectiveOptimization.deadRegisters)
                    deadLoadIndices.add(loadIndex)
                loadIndex--
            }
        }
        for (index in chunk.instructions.indices) {
            val insn = chunk.instructions[index]
            translateInstruction(
                insn,
                callOptimizations[index],
                index in deadLoadIndices
            )
        }
    }

    private fun isRegisterReadElsewhere(instructions: List<IRInstruction>, callIndex: Int, register: Int): Boolean {
        for (index in instructions.indices) {
            if (index == callIndex)
                continue
            val insn = instructions[index]
            if ((insn.reg1 == register && insn.reg1direction in setOf(OperandDirection.READ, OperandDirection.READWRITE)) ||
                (insn.reg2 == register && insn.reg2direction in setOf(OperandDirection.READ, OperandDirection.READWRITE)) ||
                (insn.reg3 == register && insn.reg3direction in setOf(OperandDirection.READ, OperandDirection.READWRITE)) ||
                insn.fcallArgs?.arguments?.any { it.reg.registerNum.value == register } == true)
                return true
        }
        return false
    }

    private fun immediateLoadsForCall(
        instructions: List<IRInstruction>,
        callIndex: Int,
        chunkStartIndex: Int,
        args: FunctionCallArgs?
    ): ImmediateCallOptimization? {
        // Float arguments use p8_fregfile and need separate FPU constant handling.
        if (args == null || args.arguments.isEmpty() || args.arguments.any {
                it.reg.callingConventionSlot == null || it.reg.dt == IRDataType.FLOAT
            })
            return null

        val loads = mutableMapOf<Int, IRInstruction>()
        var index = callIndex - 1
        while (index >= chunkStartIndex) {
            val load = instructions[index]
            val reg = load.reg1
            if (load.opcode != Opcode.LOAD || reg == null || load.immediate == null)
                break
            loads.putIfAbsent(reg, load)
            index--
        }

        if (!args.arguments.all {
                val load = loads[it.reg.registerNum.value]
                load != null && load.type == it.reg.dt
            })
            return null

        val deadRegisters = loads.keys.filterTo(mutableSetOf()) {
            !isRegisterReadElsewhere(instructions, callIndex, it)
        }
        return ImmediateCallOptimization(loads, deadRegisters)
    }

    // === instruction dispatch ===

    private fun translateInstruction(
        insn: IRInstruction,
        forwardedImmediateCall: ImmediateCallOptimization? = null,
        suppressRegfileStore: Boolean = false
    ) {
        emitRaw("        ; $insn")
        when (insn.opcode) {
            Opcode.NOP -> {}
            Opcode.BREAKPOINT -> emitLine("illegal")

            Opcode.LOAD, Opcode.LOADM, Opcode.LOADR, Opcode.LOADX, Opcode.LOADHR, Opcode.LOADI,
            Opcode.STOREM, Opcode.STOREX, Opcode.STOREZM, Opcode.STOREZI, Opcode.STOREIM, Opcode.STOREZX, Opcode.STOREHR, Opcode.STOREI,
            Opcode.LOADHFACZERO, Opcode.LOADHFACONE,
            Opcode.STOREHFACZERO, Opcode.STOREHFACONE ->
                translateLoadStore(insn, suppressRegfileStore)

            Opcode.INC, Opcode.INCM, Opcode.DEC, Opcode.DECM,
            Opcode.NEG, Opcode.NEGM,
            Opcode.ADDR, Opcode.ADD, Opcode.ADDM, Opcode.ADDIM,
            Opcode.SUBR, Opcode.SUB, Opcode.SUBM, Opcode.SUBIM,
            Opcode.MULR, Opcode.MUL, Opcode.MULM,
            Opcode.MULSR, Opcode.MULS, Opcode.MULSM,
            Opcode.DIVR, Opcode.DIV, Opcode.DIVM,
            Opcode.DIVSR, Opcode.DIVS, Opcode.DIVSM,
            Opcode.MODR, Opcode.MOD, Opcode.MODSR, Opcode.MODS,
            Opcode.DIVMODR, Opcode.DIVMOD, Opcode.SDIVMODR, Opcode.SDIVMOD,
            Opcode.CMP, Opcode.CMPI,
            Opcode.SQRT, Opcode.SQUARE -> translateArithmetic(insn)

            Opcode.ANDR, Opcode.AND, Opcode.ANDM,
            Opcode.ORR, Opcode.OR, Opcode.ORM,
            Opcode.XORR, Opcode.XOR, Opcode.XORM,
            Opcode.INV, Opcode.INVM,
            Opcode.ASRN, Opcode.ASRNM, Opcode.LSRN, Opcode.LSRNM, Opcode.LSLN, Opcode.LSLNM,
            Opcode.ASRI, Opcode.LSRI, Opcode.LSLI,
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
            Opcode.EXT, Opcode.EXTS, Opcode.EXTL, Opcode.EXTLS,
            Opcode.SGN,
            Opcode.FFROMUB, Opcode.FFROMSB, Opcode.FFROMUW, Opcode.FFROMSW, Opcode.FFROMSL,
            Opcode.FTOUB, Opcode.FTOSB, Opcode.FTOUW, Opcode.FTOSW, Opcode.FTOSL,
            Opcode.FABS, Opcode.FSIN, Opcode.FCOS, Opcode.FTAN, Opcode.FATAN,
            Opcode.FPOW, Opcode.FLN, Opcode.FLOG,
            Opcode.FROUND, Opcode.FFLOOR, Opcode.FCEIL,
            Opcode.FCOMP -> translateControl(insn, forwardedImmediateCall)
        }
    }

    // === data section ===

    private fun emitDataSection() {
        val chipramBlocks = if (target.name == "amiga500") chipramBlockLabels() else emptySet()
        val initdVars: List<IRStStaticVariable> = program.st.allVariables().filter { !it.inBss }.toList()
        val (chipramVars: List<IRStStaticVariable>, normalVars: List<IRStStaticVariable>) =
            if (chipramBlocks.isNotEmpty()) {
                initdVars.partition { it.name.substringBefore('.') in chipramBlocks }
            } else {
                emptyList<IRStStaticVariable>() to initdVars
            }
        val structInstancesWithInit: List<IRStStructInstance> = program.st.allStructInstances().filter { it.values.isNotEmpty() }.toList()
        val (chipramStructs: List<IRStStructInstance>, normalStructs: List<IRStStructInstance>) =
            if (chipramBlocks.isNotEmpty()) {
                structInstancesWithInit.partition { it.name.substringBefore('.') in chipramBlocks }
            } else {
                emptyList<IRStStructInstance>() to structInstancesWithInit
            }

        if (chipramVars.isNotEmpty() || chipramStructs.isNotEmpty()) {
            emitRaw("    SECTION data_c,data,chip   ; amiga CHIP ram initialized data")
        }
        if (chipramVars.isNotEmpty()) {
            emitRaw("; static variables with initial values (amiga chip ram)")
            for (v in chipramVars) {
                emitInitializedVariable(v)
            }
            emitRaw("")
        }
        if (chipramStructs.isNotEmpty()) {
            emitRaw("; struct instances with initial values (amiga chip ram)")
            for (si in chipramStructs) {
                emitRaw("    ALIGN  2")
                emitLabel(fixNameSymbols(si.name))
                for (fieldValue in si.values) {
                    val m68kSize = when (fieldValue.dt) {
                        BaseDataType.POINTER, BaseDataType.LONG, BaseDataType.FLOAT -> 4
                        BaseDataType.UWORD, BaseDataType.WORD -> 2
                        else -> 1
                    }
                    when (val fv = fieldValue.value) {
                        is IRStSymbolicReference.Numeric -> {
                            when {
                                fieldValue.dt == BaseDataType.FLOAT -> emitLine("dc.s  ${fv.value}")
                                m68kSize == 4 -> emitLine("dc.l  ${fv.value.toInt()}")
                                m68kSize == 2 -> emitLine("dc.w  ${fv.value.toInt()}")
                                else -> emitLine("dc.b  ${fv.value.toInt()}")
                            }
                        }
                        is IRStSymbolicReference.Symbol -> {
                            when (m68kSize) {
                                4 -> emitLine("dc.l  ${fixNameSymbols(fv.name)}")
                                2 -> emitLine("dc.w  ${fixNameSymbols(fv.name)}")
                                else -> emitLine("dc.b  ${fixNameSymbols(fv.name)}")
                            }
                        }
                        is IRStSymbolicReference.BoolValue -> {
                            val v = if (fv.value) 1 else 0
                            emitLine("dc.b  $v")
                        }
                    }
                }
            }
            emitRaw("")
        }
        if (chipramVars.isNotEmpty() || chipramStructs.isNotEmpty()) {
            emitRaw("    SECTION .data,data  ; initialized variables (writable)")
        }
        if (normalVars.isNotEmpty()) {
            emitRaw("; static variables with initial values")
            for (v in normalVars) {
                emitInitializedVariable(v)
            }
            emitRaw("")
        }

        if (dataFloatConstants.isNotEmpty()) {
            emitRaw("; float constants (single precision, 4 bytes each)")
            for ((label, value) in dataFloatConstants) {
                emitRaw("    ALIGN  4")
                emitRaw("$label:")
                emitRaw("    dc.s  $value")
            }
            emitRaw("")
        }

        // struct instances with init values
        if (normalStructs.isNotEmpty()) {
            emitRaw("; struct instances with initial values")
            for (si in normalStructs) {
                emitRaw("    ALIGN  2")
                emitLabel(fixNameSymbols(si.name))
                for (fieldValue in si.values) {
                    val m68kSize = when (fieldValue.dt) {
                        BaseDataType.POINTER, BaseDataType.LONG, BaseDataType.FLOAT -> 4
                        BaseDataType.UWORD, BaseDataType.WORD -> 2
                        else -> 1
                    }
                    when (val fv = fieldValue.value) {
                        is IRStSymbolicReference.Numeric -> {
                            when {
                                fieldValue.dt == BaseDataType.FLOAT -> emitLine("dc.s  ${fv.value}")
                                m68kSize == 4 -> emitLine("dc.l  ${fv.value.toInt()}")
                                m68kSize == 2 -> emitLine("dc.w  ${fv.value.toInt()}")
                                else -> emitLine("dc.b  ${fv.value.toInt()}")
                            }
                        }
                        is IRStSymbolicReference.Symbol -> {
                            when (m68kSize) {
                                4 -> emitLine("dc.l  ${fixNameSymbols(fv.name)}")
                                2 -> emitLine("dc.w  ${fixNameSymbols(fv.name)}")
                                else -> emitLine("dc.b  ${fixNameSymbols(fv.name)}")
                            }
                        }
                        is IRStSymbolicReference.BoolValue -> {
                            val v = if (fv.value) 1 else 0
                            emitLine("dc.b  $v")
                        }
                    }
                }
            }
            emitRaw("")
        }

        if (normalVars.isNotEmpty() || dataFloatConstants.isNotEmpty() || normalStructs.isNotEmpty()) {
            emitRaw("    SECTION .text,code  ; back to code section")
        }
    }

    private fun chipramBlockLabels(): Set<String> =
        program.blocks.filter { it.options.amigaChipram }.map { it.label }.toSet()

    private fun emitInitializedVariable(v: IRStStaticVariable) {
        val dt = v.dt
        val label = fixNameSymbols(v.name)
        val init = v.initializationValue
        when {
            dt.isString && init is IRVariableInitializer.Str -> {
                val bytes = program.encoding.encodeString(init.text, init.encoding)
                val bytesStr = if(bytes.isNotEmpty()) bytes.joinToString(",") { it.toString(10) } + "," else ""
                emitLine("    ALIGN  2")
                emitLine("$label:")
                emitLine("dc.b  ${bytesStr}0", v.name)
            }
            dt.isArray && init is IRVariableInitializer.Array -> {
                val elemDt = dt.elementType()
                val elemSize = if(elemDt.isByte || elemDt.isBool) 1 else if(elemDt.isLong) 4 else {
                    // on 32-bit targets, arrays of string pointers (stored as uword[]) need 4 bytes per element
                    if(target.POINTER_MEM_SIZE > 2u && init.elements.any { it is IRStSymbolicReference.Symbol } && elemDt.isWord) target.POINTER_MEM_SIZE.toInt()
                    else target.memorySize(elemDt.base)
                }
                val values = init.elements.map { elt ->
                    when(elt) {
                        is IRStSymbolicReference.Numeric -> elt.value.toInt().toString()
                        is IRStSymbolicReference.Symbol -> fixNameSymbols(elt.name)
                        is IRStSymbolicReference.BoolValue -> if(elt.value) "1" else "0"
                    }
                }
                when (elemSize) {
                    1 -> {
                        emitLine("    ALIGN  2")
                        emitLine("$label:")
                        emitLine("dc.b  ${values.joinToString(",")}", v.name)
                    }
                    2 -> {
                        emitLine("    ALIGN  2")
                        emitLine("$label:")
                        emitLine("dc.w  ${values.joinToString(",")}", v.name)
                    }
                    4 -> {
                        emitLine("    ALIGN  $elemSize")
                        emitLine("$label:")
                        emitLine("dc.l  ${values.joinToString(",")}", v.name)
                    }
                    else -> error("expected array element size 1,2 or 4 for ${v.name}")
                }
            }
            dt.isNumeric || dt.isBool -> {
                val initValue = when(init) {
                    is IRVariableInitializer.Numeric -> init.value.toInt()
                    is IRVariableInitializer.Array -> 0
                    is IRVariableInitializer.Str -> 0
                    null -> 0
                }
                val initFloat = when(init) {
                    is IRVariableInitializer.Numeric -> init.value
                    else -> 0.0
                }
                when(dt) {
                    DataType.BYTE, DataType.UBYTE, DataType.BOOL -> {
                        emitLine("$label:")
                        emitLine("dc.b  $initValue", v.name)
                    }
                    DataType.WORD, DataType.UWORD -> {
                        emitLine("    ALIGN  2")
                        emitLine("$label:")
                        emitLine("dc.w  $initValue", v.name)
                    }
                    DataType.LONG -> {
                        emitLine("    ALIGN  4")
                        emitLine("$label:")
                        emitLine("dc.l  $initValue", v.name)
                    }
                    DataType.FLOAT -> {
                        emitLine("    ALIGN  4")
                        emitLine("$label:")
                        emitLine("dc.s  $initFloat", v.name)
                    }
                    else -> TODO("initialization value for dt $dt variable ${v.name}")
                }
            }
            else -> {
                emitLine("$label:")
                emitLine("dc.b  0", v.name)
            }
        }
    }

    // === BSS section ===

    private fun emitConstants() {
        val emitted = mutableSetOf<String>()
        emitRaw("; Constants")
        for (c in program.st.allConstants()) {
            val cv = c.value
            val csn = c.memorySlabName
            if (cv != null) {
                if(c.dt.isFloat) continue     // float constants are emitted as data, not as integer = values
                if (!c.noPrefix) {
                    val label = fixNameSymbols(constLabel(c.name))
                    if (!emitted.add(label)) continue
                    emitRaw("$label = ${cv.toLong()}")
                }
                emitRaw("${fixNameSymbols(c.name)} = ${cv.toLong()}")
            } else if (csn != null) {
                val slab = program.st.lookup(csn) as? IRStMemorySlab
                if (slab != null) {
                    val slabRef = fixNameSymbols(slab.name)
                    if (!c.noPrefix) {
                        val label = fixNameSymbols(constLabel(c.name))
                        if (!emitted.add(label)) continue
                        emitRaw("$label = $slabRef")
                    }
                    emitRaw("${fixNameSymbols(c.name)} = $slabRef")
                }
            }
        }

        // memory-mapped variables (fixed addresses)
        val memvars = program.st.allMemMappedVariables()
        for (mv in memvars.sortedBy { it.address }) {
            emitRaw("${fixNameSymbols(mv.name)} = $${mv.address.toString(16)}")
        }

        if (emitted.size > 0 || memvars.count() > 0)
            emitRaw("")
    }


    private fun emitBssSection() {
        val chipramBlocks = if (target.name == "amiga500") chipramBlockLabels() else emptySet()

        // 1. Map variables to their sizes and actual M68k alignment requirements
        val allBssVars: List<IRStStaticVariable> = program.st.allVariables().filter { it.inBss }.toList()
        val (chipramBssVars: List<IRStStaticVariable>, normalBssVars: List<IRStStaticVariable>) =
            if (chipramBlocks.isNotEmpty()) {
                allBssVars.partition { it.name.substringBefore('.') in chipramBlocks }
            } else {
                emptyList<IRStStaticVariable>() to allBssVars
            }
        val allStructsNoInit: List<IRStStructInstance> = program.st.allStructInstances().filter { it.values.isEmpty() }.toList()
        val (chipramStructsNoInit: List<IRStStructInstance>, normalStructsNoInit: List<IRStStructInstance>) =
            if (chipramBlocks.isNotEmpty()) {
                allStructsNoInit.partition { it.name.substringBefore('.') in chipramBlocks }
            } else {
                emptyList<IRStStructInstance>() to allStructsNoInit
            }
        val allSlabs: List<IRStMemorySlab> = program.st.allMemorySlabs().toList()
        val (chipramSlabs: List<IRStMemorySlab>, normalSlabs: List<IRStMemorySlab>) =
            if (chipramBlocks.isNotEmpty()) {
                allSlabs.partition { it.name.substringBefore('.') in chipramBlocks }
            } else {
                emptyList<IRStMemorySlab>() to allSlabs
            }

        fun layout(v: IRStStaticVariable): Triple<IRStStaticVariable, Int, Int> {
            val size = target.memorySize(v.dt, v.length?.toInt())
            // M68K alignment rules:
            // - Objects containing 32-bit types need 4-byte alignment
            // - Objects containing 16-bit types need 2-byte alignment
            // - Pure byte arrays/scalars only need 1-byte alignment
            val alignment = when {
                v.dt.isPointer || v.dt.isLong || v.dt.isFloat -> 4
                v.dt.isWord -> 2
                v.dt.isArray -> 2
                else -> 1
            }
            return Triple(v, size, alignment)
        }

        fun emitBssVars(vars: List<IRStStaticVariable>) {
            val sorted = vars.map(::layout)
                .sortedWith(compareByDescending<Triple<IRStStaticVariable, Int, Int>> { it.third }.thenByDescending { it.second })
            for ((v, size, alignment) in sorted) {
                if (alignment >= 2) {
                    emitRaw("    ALIGN  $alignment")
                }
                emitLabel(fixNameSymbols(v.name))
                emitLine("ds.b  $size")
            }
        }

        fun emitStructsNoInit(structs: List<IRStStructInstance>) {
            if (structs.isEmpty()) return
            emitRaw("")
            emitRaw("; struct instances (zeroed)")
            for (si in structs) {
                emitRaw("    ALIGN  2")
                emitLabel(fixNameSymbols(si.name))
                emitLine("ds.b  ${si.size}")
            }
        }

        fun emitSlabs(slabs: List<IRStMemorySlab>) {
            if (slabs.isEmpty()) return
            emitRaw("")
            emitRaw("; memory slabs")
            for (slab in slabs) {
                val alignment = max(2, slab.align.toInt())
                emitRaw("    ALIGN  $alignment")
                emitLabel(fixNameSymbols(slab.name))
                emitLine("ds.b  ${slab.size}")
            }
        }

        // amiga CHIP ram BSS (variables, structs and slabs belonging to chipram blocks)
        if (chipramBssVars.isNotEmpty() || chipramStructsNoInit.isNotEmpty() || chipramSlabs.isNotEmpty()) {
            emitRaw("    SECTION bss_c,bss,chip   ; amiga CHIP ram bss")
            emitRaw("    ALIGN  4")
            emitBssVars(chipramBssVars)
            emitStructsNoInit(chipramStructsNoInit)
            emitSlabs(chipramSlabs)
        }

        // normal bss section
        emitRaw("    SECTION .bss,bss    ; bss section")
        emitRaw("    ALIGN  4")
        emitLabel("prog8_bss_section_start")
        emitBssVars(normalBssVars)
        emitStructsNoInit(normalStructsNoInit)
        emitSlabs(normalSlabs)

        // register file (always at the end of BSS variables)
        emitRaw("    ALIGN  4")
        emitLabel(REGFILE_LABEL)
        emitLine("ds.b  ${regFileLayout.totalSize}")

        // float register file
        if (floatRegFileLayout.totalSize > 0) {
            emitRaw("    ALIGN  4")
            emitLabel(FLOAT_REGFILE_LABEL)
            emitLine("ds.b  ${floatRegFileLayout.totalSize}")
        }

        // define the end of the program (used by startup code for BSS clearing)
        // only needed for RAW (no linker script); ELF uses the linker file instead
        if(program.options.output == OutputType.RAW)
            emitLabel("prog8_program_end")

        emitRaw("    SECTION .text,code  ; end of bss section")
        emitRaw("")
    }
}
