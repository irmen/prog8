package prog8.codegen.m68k

import prog8.code.core.*
import prog8.intermediate.*
import kotlin.math.max

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

internal class AsmGen(val program: IRProgram, private val target: ICompilationTarget) {
    private val output = StringBuilder()
    private val cpu get() = target.cpu

    companion object {
        const val REGFILE_LABEL = "p8_regfile"
    }

    init {
        require(target.cpu == CpuType.M68000 || target.cpu == CpuType.M68020) { "M68k codegen requires M68000 or M68020 cpu, got ${target.cpu}" }
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
        val allRegs = program.registersUsed().regsTypes
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
        emitLine("move.l  ${regAddr(reg)}, a0")
    }

    fun storeA0ToPointer(reg: Int) {
        // store a0 (a 32-bit pointer) to the register file
        emitLine("move.l  a0, ${regAddr(reg)}")
    }


    fun loadIndexToD0(idx: Int) {
        // load an index register into d0, zero-extending to 32 bits
        val idxType = program.registersUsed().regsTypes[RegisterNum(idx)]
        emitLine("moveq.l  #0,d0")      // clear everything for any caller that uses (a0,d0.l) addressing
        when (idxType) {
            IRDataType.BYTE -> emitLine("move.b  ${regAddr(idx)}, d0")
            IRDataType.POINTER, IRDataType.LONG -> emitLine("move.l  ${regAddr(idx)}, d0")
            else -> emitLine("move.w  ${regAddr(idx)}, d0")
        }
    }

    // === label/symbol helpers ===

    internal fun fixNameSymbols(name: String): String = name   // for now, no mangling needed {

    
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

    fun fpuRegName(regNum: RegisterNum): String = "fp${regNum.value}"

    // === size suffix helpers ===

    fun dtSuffix(type: IRDataType): String = when (type) {
        IRDataType.BYTE -> ".b"
        IRDataType.WORD -> ".w"
        IRDataType.LONG -> ".l"
        IRDataType.FLOAT -> ".f"     // single precision (64-bit) for FPU 
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
            asmFile.toFile().writeText(output.toString())
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
        
        emitLine("jsr  ${fixNameSymbols("p8_sys_startup.clear_bss_section")}")
        if (!options.noSysInit)
            emitLine("jsr  ${fixNameSymbols("p8_sys_startup.init_system")}")
        emitLine("jsr  ${fixNameSymbols("p8_sys_startup.init_system_phase2")}")
        emitLine("jsr  ${fixNameSymbols("p8b_main.p8s_start")}")
        emitLine("moveq  #0, d0", "normal return status 0")
        emitLine("jmp  ${fixNameSymbols("p8_sys_startup.cleanup_at_exit")}")
    }

    // === code emission ===

    private fun emitCode() {
        emitLabel("run_global_inits")
        translateChunk(program.globalInits)
        emitLine("rts")
        emitRaw("")

        for (block in program.blocks) {
            val blockLabel = fixNameSymbols(block.label)
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
            emitRaw("")
        }
    }

    private fun emitSubroutine(sub: IRSubroutine) {
        val subLabel = fixNameSymbols(sub.label)
        val subUnscoped = unscopedName(sub.label)
        emitRaw("; Subroutine: $subLabel")
        val firstChunk = sub.chunks.filterIsInstance<IRCodeChunk>().firstOrNull()
        if (firstChunk != null)
            emitSourceComment(firstChunk.sourceLinesPositions)
        emitRaw("")
        emitLine("ALIGN 2")
        emitLabel(subLabel)
        val entrypointNames = setOf("p8b_main.p8s_start", "main.start")
        if(sub.label in entrypointNames)
            emitLine("jsr  run_global_inits")
        for (chunk in sub.chunks) {
            when (chunk) {
                is IRCodeChunk -> {
                    val chunkLabel = chunk.label?.let { fixNameSymbols(it) }
                    if (chunkLabel != null && chunkLabel != subLabel && chunkLabel != subUnscoped)
                        emitLabel(chunkLabel)
                    translateChunk(chunk)
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
        emitRaw("")
    }

    private fun emitAsmSubroutine(sub: IRAsmSubroutine) {
        emitRaw("")
        emitLine("ALIGN 2")
        emitLabel(fixNameSymbols(sub.label))
        emitRaw(sub.asmChunk.assembly)
        emitRaw("")
    }

    private fun unscopedName(scopedName: String): String =
        scopedName.substringAfterLast('.')

    private fun translateChunk(chunk: IRCodeChunk) {
        emitSourceComment(chunk.sourceLinesPositions)
        for (insn in chunk.instructions) {
            translateInstruction(insn)
        }
    }

    // === instruction dispatch ===

    private fun translateInstruction(insn: IRInstruction) {
        emitRaw("        ; $insn")
        when (insn.opcode) {
            Opcode.NOP -> {}
            Opcode.BREAKPOINT -> emitLine("illegal")

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
            Opcode.CMP, Opcode.CMPI,
            Opcode.SQRT, Opcode.SQUARE -> translateArithmetic(insn)

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
            Opcode.SGN,
            Opcode.FFROMUB, Opcode.FFROMSB, Opcode.FFROMUW, Opcode.FFROMSW, Opcode.FFROMSL,
            Opcode.FTOUB, Opcode.FTOSB, Opcode.FTOUW, Opcode.FTOSW, Opcode.FTOSL,
            Opcode.FABS, Opcode.FSIN, Opcode.FCOS, Opcode.FTAN, Opcode.FATAN,
            Opcode.FPOW, Opcode.FLN, Opcode.FLOG,
            Opcode.FROUND, Opcode.FFLOOR, Opcode.FCEIL,
            Opcode.FCOMP -> translateControl(insn)
        }
    }

    // === data section ===

    private fun emitDataSection() {
        val initdVars = program.st.allVariables().filter { !it.inBss }.toList()
        val structInstancesWithInit = program.st.allStructInstances().filter { it.values.isNotEmpty() }.toList()
        if (initdVars.isNotEmpty() || dataFloatConstants.isNotEmpty() || structInstancesWithInit.isNotEmpty()) {
            emitRaw("    SECTION .data,data  ; initialized variables (writable)")
        }
        if (initdVars.isNotEmpty()) {
            emitRaw("; static variables with initial values")
            for (v in initdVars) {
                emitRaw("    ALIGN  2")
                emitInitializedVariable(v)
            }
            emitRaw("")
        }

        if (dataFloatConstants.isNotEmpty()) {
            emitRaw("; float constants (single precision, 4 bytes each)")
            for ((label, value) in dataFloatConstants) {
                val bits = value.toFloat().toRawBits()
                emitRaw("    ALIGN  4")
                emitRaw("$label:")
                emitRaw("    dc.b  ${(bits ushr 24).toUByte().toString(10)}")
                emitRaw("    dc.b  ${(bits ushr 16).toUByte().toString(10)}")
                emitRaw("    dc.b  ${(bits ushr 8).toUByte().toString(10)}")
                emitRaw("    dc.b  ${bits.toUByte().toString(10)}")
            }
            emitRaw("")
        }

        // struct instances with init values
        if (structInstancesWithInit.isNotEmpty()) {
            emitRaw("; struct instances with initial values")
            for (si in structInstancesWithInit) {
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
                            val v = fv.value.toInt()
                            when (m68kSize) {
                                4 -> emitLine("dc.l  $v")
                                2 -> emitLine("dc.w  $v")
                                else -> emitLine("dc.b  $v")
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

        if (initdVars.isNotEmpty() || dataFloatConstants.isNotEmpty() || structInstancesWithInit.isNotEmpty()) {
            emitRaw("    SECTION .text,code  ; back to code section")
        }
    }

    private fun emitInitializedVariable(v: IRStStaticVariable) {
        val dt = v.dt
        val label = fixNameSymbols(v.name)
        val init = v.initializationValue
        when {
            dt.isString && init is IRVariableInitializer.Str -> {
                val bytes = program.encoding.encodeString(init.text, init.encoding)
                val bytesStr = if(bytes.isNotEmpty()) bytes.joinToString(",") { it.toString(10) } + "," else ""
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
                        emitLine("$label:")
                        emitLine("dc.b  ${values.joinToString(",")}", v.name)
                    }
                    4 -> {
                        emitLine("    ALIGN  4")
                        emitLine("$label:")
                        emitLine("dc.l  ${values.joinToString(",")}", v.name)
                    }
                    else -> {
                        emitLine("    ALIGN  2")
                        emitLine("$label:")
                        emitLine("dc.w  ${values.joinToString(",")}", v.name)
                    }
                }
            }
            dt.isNumeric || dt.isBool -> {
                val initValue = when(init) {
                    is IRVariableInitializer.Numeric -> init.value.toInt()
                    is IRVariableInitializer.Array -> 0
                    is IRVariableInitializer.Str -> 0
                    null -> 0
                }
                when(dt) {
                    DataType.BYTE, DataType.UBYTE, DataType.BOOL -> {
                        emitLine("$label:")
                        emitLine("dc.b  $initValue", v.name)
                    }
                    else -> {
                        emitLine("$label:")
                        emitLine("dc.w  $initValue", v.name)
                    }
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
        emitRaw("    SECTION .bss,bss    ; bss section")
        emitRaw("    ALIGN  4")
        emitLabel("prog8_bss_section_start")

        // 1. Map variables to their sizes and actual M68k alignment requirements
        val bssVars = program.st.allVariables()
            .filter { it.inBss }
            .map { v ->
                val size = target.memorySize(v.dt, v.length?.toInt())
                // M68K alignment rules: 
                // - Objects containing 32-bit types need 4-byte alignment
                // - Objects containing 16-bit types need 2-byte alignment
                // - Pure byte arrays/scalars only need 1-byte alignment
                val alignment = when {
                    v.dt.isPointer || v.dt.isLong || v.dt.isFloat -> 4
                    v.dt.isWord -> 2
                    else -> 1
                }
                Triple(v, size, alignment)
            }
            // 2. Sort primary by alignment descending, secondary by size descending
            .sortedWith(compareByDescending<Triple<IRStStaticVariable, Int, Int>> { it.third }.thenByDescending { it.second })

        // Keep track of the current alignment block we are emitting to avoid redundant lines
        var currentBlockAlignment = 4

        // 3. Emit sorted variables
        for ((v, size, alignment) in bssVars) {
            // Only emit an ALIGN directive if the variable requires alignment 
            // AND we haven't already established that alignment boundary for this group.
            if (alignment < currentBlockAlignment) {
                if (alignment >= 2) {
                    emitRaw("    ALIGN  2")
                }
                // Update our tracked alignment group state
                currentBlockAlignment = alignment
            }

            emitLabel(fixNameSymbols(v.name))
            emitLine("ds.b  $size")
        }

        // struct instances without init values (zeroed at startup)
        val structInstancesNoInit = program.st.allStructInstances().filter { it.values.isEmpty() }.toList()
        if (structInstancesNoInit.isNotEmpty()) {
            emitRaw("")
            emitRaw("; struct instances (zeroed)")
            for (si in structInstancesNoInit) {
                emitRaw("    ALIGN  2")
                emitLabel(fixNameSymbols(si.name))
                emitLine("ds.b  ${si.size}")
            }
        }

        // memory slabs
        val slabs = program.st.allMemorySlabs().toList()
        if(slabs.isNotEmpty()) {
            emitRaw("")
            emitRaw("; memory slabs")
            for(slab in slabs) {
                val alignment = max(2, slab.align.toInt())
                emitRaw("    ALIGN  $alignment")
                emitLabel(fixNameSymbols(slab.name))
                emitLine("ds.b  ${slab.size}")
            }
        }

        // register file (always at the end of BSS variables)
        emitRaw("    ALIGN  4")
        emitLabel(REGFILE_LABEL)
        emitLine("ds.b  ${regFileLayout.totalSize}")

        // define the end of the program (used by startup code for BSS clearing)
        // only needed for RAW (no linker script); ELF uses the linker file instead
        if(program.options.output == OutputType.RAW)
            emitLabel("prog8_program_end")

        emitRaw("    SECTION .text,code  ; end of bss section")
        emitRaw("")
    }
}
