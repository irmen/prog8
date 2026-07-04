package prog8.codegen.m68k

import prog8.code.core.*
import prog8.intermediate.*

internal class AsmGen(val program: IRProgram, private val target: ICompilationTarget) {
    private val output = StringBuilder()
    private val cpu get() = target.cpu

    companion object {
        const val REGFILE_LABEL = "p8_regfile"
    }

    init {
        require(target.cpu == CpuType.M68030) { "M68k codegen requires M68030 CPU" }
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

    // === variable-size virtual register file layout ===
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

    fun regAddr(reg: Int): String {
        val offset = regFileLayout.offsets[reg] ?: error("register r$reg has no layout info")
        return "$REGFILE_LABEL+$offset"
    }

    fun regAddrByte(reg: Int, byteOffset: Int): String {
        val offset = regFileLayout.offsets[reg] ?: error("register r$reg has no layout info")
        return "$REGFILE_LABEL+${offset + byteOffset}"
    }

    fun dataTypeSize(dt: IRDataType): Int = when (dt) {
        IRDataType.BYTE -> 1
        IRDataType.WORD -> 2
        IRDataType.LONG -> 4
        IRDataType.FLOAT -> target.FLOAT_MEM_SIZE.toInt()
    }

    // === label/symbol helpers ===

    fun fixNameSymbols(name: String): String {
        var n = name
        n = n.replace("::", "_")
        n = n.replace(".", "_")
        n = n.replace("-", "_")
        return n
    }

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

    // === FPU helpers (M68030 with 68881/68882) ===

    fun fpuRegName(regNum: RegisterNum): String = "fp${regNum.value}"

    fun fpuRegAddr(regNum: RegisterNum): String {
        return fpuRegName(regNum)
    }

    // === size suffix helpers ===

    fun dtSuffix(type: IRDataType): String = when (type) {
        IRDataType.BYTE -> ".b"
        IRDataType.WORD -> ".w"
        IRDataType.LONG -> ".l"
        IRDataType.FLOAT -> ".d"     // double precision (64-bit) for FPU
    }

    fun floatLoadSuffix(type: IRDataType): String = when (type) {
        IRDataType.FLOAT -> ".d"
        else -> dtSuffix(type)
    }

    // === main entry point ===

    fun generate(): Boolean {
        emitHeader()
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

    // === header ===

    private fun emitHeader() {
        val options = program.options
        emitRaw("; Program: ${program.name}")
        emitRaw("; Generated by prog8-m68kgen (Motorola 68000 codegen)")
        emitRaw("; Target CPU: ${cpu.name}")
        emitRaw("; Output: ${options.output.name}")
        emitRaw("")
        emitRaw("; vasm syntax: Motorola")
        emitRaw("")

        val loadAddr = options.loadAddress
        emitRaw("    ORG  ${loadAddr.toHex()}")
        emitRaw("")

        // user-supplied symbol definitions
        if (options.symbolDefs.isNotEmpty()) {
            emitRaw("; -- user supplied symbols on the command line")
            for ((name, value) in options.symbolDefs) {
                emitRaw("$name = $value")
            }
            emitRaw("")
        }

        // Set up stack pointer and jump to program start
        emitLabel("prog8_program_start")
        emitLine("move.l  #${options.memtopAddress.toHex()}, sp", "initialize stack pointer")
        // clear BSS section
        emitLine("lea  prog8_bss_section_start, a0")
        emitLine("lea  prog8_program_end, a1")
        emitLine("sub.l  a0, a1", "a1 = bss size in bytes")
        emitLine("beq  .bss_done")
        emitLine("moveq  #0, d0")
        emitLabel(".bss_loop")
        emitLine("move.b  d0, (a0)+")
        emitLine("subq.l  #1, a1")
        emitLine("bne  .bss_loop")
        emitLabel(".bss_done")
        emitLine("jsr  ${fixNameSymbols("p8_sys_startup.init_system")}")
        emitLine("jsr  ${fixNameSymbols("p8_sys_startup.init_system_phase2")}")
        emitLine("jsr  run_global_inits")
        emitLine("jsr  ${fixNameSymbols("p8b_main.p8s_start")}")
        emitLine("jmp  ${fixNameSymbols("p8_sys_startup.cleanup_at_exit")}")
    }

    // === code emission ===

    private fun emitCode() {
        emitLabel("run_global_inits")
        translateChunk(program.globalInits)
        emitLine("rts")
        emitRaw("")

        for (block in program.blocks) {
            val addr = block.options.address
            if (addr != null) {
                emitRaw("    ORG  ${addr.toHex()}")
            }
            val blockLabel = fixNameSymbols(block.label)
            emitRaw("; Block: $blockLabel")
            val topLevelSubLabels = block.children.filterIsInstance<IRSubroutine>().map { fixNameSymbols(it.label) }.toSet()
            for (element in block.children) {
                when (element) {
                    is IRSubroutine -> {
                        val elLabel = fixNameSymbols(element.label)
                        val isNested = topLevelSubLabels.any { subLabel ->
                            subLabel != elLabel && elLabel.startsWith("$subLabel.")
                        }
                        if (!isNested) emitSubroutine(element)
                    }
                    is IRAsmSubroutine -> {
                        if (element.isInline) continue
                        val elLabel = fixNameSymbols(element.label)
                        val isNested = topLevelSubLabels.any { subLabel -> elLabel.startsWith("$subLabel.") }
                        if (!isNested) emitAsmSubroutine(element)
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
        emitLabel(subLabel)
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
        val addr = sub.address
        if (addr != null) {
            emitLine("ORG  $addr")
        }
        emitRaw("")
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

    // === data section ===

    private fun emitDataSection() {
        val initdVars = program.st.allVariables().filter { !it.inBss }.toList()
        if (initdVars.isNotEmpty()) {
            emitRaw("; static variables with initial values")
            for (v in initdVars) {
                emitInitializedVariable(v)
            }
            emitRaw("")
        }

        if (dataFloatConstants.isNotEmpty()) {
            emitRaw("; float constants (double precision, 8 bytes each)")
            for ((label, value) in dataFloatConstants) {
                val bits = value.toRawBits()
                emitRaw("$label:")
                emitRaw("    dc.b  ${(bits ushr 56).toUByte().toString(10)}")
                emitRaw("    dc.b  ${(bits ushr 48).toUByte().toString(10)}")
                emitRaw("    dc.b  ${(bits ushr 40).toUByte().toString(10)}")
                emitRaw("    dc.b  ${(bits ushr 32).toUByte().toString(10)}")
                emitRaw("    dc.b  ${(bits ushr 24).toUByte().toString(10)}")
                emitRaw("    dc.b  ${(bits ushr 16).toUByte().toString(10)}")
                emitRaw("    dc.b  ${(bits ushr 8).toUByte().toString(10)}")
                emitRaw("    dc.b  ${bits.toUByte().toString(10)}")
            }
            emitRaw("")
        }
    }

    private fun emitInitializedVariable(v: IRStStaticVariable) {
        val dt = v.dt
        val label = fixNameSymbols(v.name)
        val init = v.initializationValue
        when {
            dt.isString && init is IRVariableInitializer.Str -> {
                val bytes = program.encoding.encodeString(init.text, init.encoding)
                val bytesStr = bytes.joinToString(",") { it.toString(10) }
                emitLine("$label:")
                emitLine("dc.b  $bytesStr,0", v.name)
            }
            dt.isArray && init is IRVariableInitializer.Array -> {
                val elemDt = dt.elementType()
                val elemSize = if(elemDt.isByte || elemDt.isBool) 1 else 2
                val values = init.elements.map { elt ->
                    when(elt) {
                        is IRStSymbolicReference.Numeric -> elt.value.toInt().toString()
                        is IRStSymbolicReference.Symbol -> fixNameSymbols(elt.name)
                        is IRStSymbolicReference.BoolValue -> if(elt.value) "1" else "0"
                    }
                }
                if(elemSize == 1) {
                    emitLine("$label:")
                    emitLine("dc.b  ${values.joinToString(",")}", v.name)
                } else {
                    emitLine("$label:")
                    emitLine("dc.w  ${values.joinToString(",")}", v.name)
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

    private fun emitBssSection() {
        emitRaw("; bss section")
        emitLabel("prog8_bss_section_start")
        // uninitialized variables
        val bssVars = program.st.allVariables().filter { it.inBss }.toList()
        for (v in bssVars) {
            emitLabel(fixNameSymbols(v.name))
            emitLine("ds.b  ${dataTypeSizeForVar(v)}")
        }
        // register file (always at the end of BSS variables)
        emitLabel(REGFILE_LABEL)
        emitLine("ds.b  ${regFileLayout.totalSize}")
        emitLabel("prog8_program_end")
        emitRaw("")
    }

    private fun dataTypeSizeForVar(v: IRStStaticVariable): Int {
        val len = v.length
        return when {
            v.dt.isString && len != null -> len.toInt()
            v.dt.isArray && len != null -> len.toInt() * if(v.dt.elementType().isByte) 1 else 2
            v.dt.isWord || v.dt.isPointer -> 2
            v.dt.isLong -> 4
            v.dt.isFloat -> 8
            else -> 1  // byte/bool
        }
    }


}
