package prog8.compiler.target.c64

// note: to put stuff on the stack, we use Absolute,X  addressing mode which is 3 bytes / 4 cycles
// possible space optimization is to use zeropage (indirect),Y  which is 2 bytes, but 5 cycles

import prog8.ast.DataType
import prog8.ast.escape
import prog8.compiler.*
import prog8.compiler.intermediate.*
import prog8.stackvm.Syscall
import prog8.stackvm.syscallsForStackVm
import java.io.File
import java.util.*
import kotlin.math.abs


class AssemblyError(msg: String) : RuntimeException(msg)


class AsmGen(val options: CompilationOptions, val program: IntermediateProgram, val heap: HeapValues, val trace: Boolean) {
    private val globalFloatConsts = mutableMapOf<Double, String>()
    private val assemblyLines = mutableListOf<String>()
    private lateinit var block: IntermediateProgram.ProgramBlock
    private var breakpointCounter = 0

    init {
        // Because 64tass understands scoped names via .proc / .block,
        // we'll strip the block prefix from all scoped names in the program.
        // Also, convert invalid label names (such as "<<<anonymous-1>>>") to something that's allowed.
        val newblocks = mutableListOf<IntermediateProgram.ProgramBlock>()
        for(block in program.blocks) {
            val newvars = block.variables.map { symname(it.key, block) to it.value }.toMap().toMutableMap()
            val newlabels = block.labels.map { symname(it.key, block) to it.value}.toMap().toMutableMap()
            val newinstructions = block.instructions.asSequence().map {
                when {
                    it is LabelInstr -> LabelInstr(symname(it.name, block))
                    it.opcode == Opcode.INLINE_ASSEMBLY -> it
                    else ->
                        Instruction(it.opcode, it.arg, it.arg2,
                            callLabel = if (it.callLabel != null) symname(it.callLabel, block) else null,
                            callLabel2 = if (it.callLabel2 != null) symname(it.callLabel2, block) else null)
                }
            }.toMutableList()
            val newConstants = block.memoryPointers.map { symname(it.key, block) to it.value }.toMap().toMutableMap()
            newblocks.add(IntermediateProgram.ProgramBlock(
                    block.scopedname,
                    block.shortname,
                    block.address,
                    newinstructions,
                    newvars,
                    newConstants,
                    newlabels))
        }
        program.blocks.clear()
        program.blocks.addAll(newblocks)

        // make a list of all const floats that are used
        for(block in program.blocks) {
            for(ins in block.instructions.filter{it.arg?.type==DataType.FLOAT}) {
                val float = ins.arg!!.numericValue().toDouble()
                if(float !in globalFloatConsts)
                    globalFloatConsts[float] = "prog8_const_float_${globalFloatConsts.size}"
            }
        }
    }

    fun compileToAssembly(): AssemblyProgram {
        println("\nGenerating assembly code from intermediate code... ")

        header()
        for(b in program.blocks)
            block2asm(b)

        optimizeAssembly(assemblyLines)

        File("${program.name}.asm").printWriter().use {
            for (line in assemblyLines) { it.println(line) }
        }

        return AssemblyProgram(program.name)
    }

    private fun optimizeAssembly(lines: MutableList<String>) {
        // sometimes, iny+dey / inx+dex / dey+iny / dex+inx sequences are generated, these can be eliminated.
        val removeLines = mutableListOf<Int>()
        for(pair in lines.withIndex().windowed(2)) {
            val first = pair[0].value
            val second = pair[1].value
            if(first.trimStart().startsWith(';') || second.trimStart().startsWith(';'))
                continue        // skip over asm comments

            if((" iny" in first || "\tiny" in first) && (" dey" in second || "\tdey" in second)
                    || (" inx" in first || "\tinx" in first) && (" dex" in second || "\tdex" in second)
                    || (" dey" in first || "\tdey" in first) && (" iny" in second || "\tiny" in second)
                    || (" dex" in first || "\tdex" in first) && (" inx" in second || "\tinx" in second))
            {
                removeLines.add(pair[0].index)
                removeLines.add(pair[1].index)
            }
        }

        for(i in removeLines.reversed())
            lines.removeAt(i)
    }

    private fun out(str: String) = assemblyLines.add(str)

    private fun symname(scoped: String, block: IntermediateProgram.ProgramBlock): String {
        if(' ' in scoped)
            return scoped

        val blockLocal: Boolean
        var name = if (scoped.startsWith("${block.shortname}.")) {
            blockLocal = true
            scoped.substring(block.shortname.length+1)
        } else {
            blockLocal = false
            scoped
        }
        name = name.replace("<<<", "prog8_").replace(">>>", "")
        if(name=="-")
            return "-"
        if(blockLocal)
            name = name.replace(".", "_")
        else {
            val parts = name.split(".", limit=2)
            if(parts.size>1)
                name = "${parts[0]}.${parts[1].replace(".", "_")}"
        }
        return name.replace("-", "")
    }

    private fun makeFloatFill(flt: Mflpt5): String {
        val b0 = "$"+flt.b0.toString(16).padStart(2, '0')
        val b1 = "$"+flt.b1.toString(16).padStart(2, '0')
        val b2 = "$"+flt.b2.toString(16).padStart(2, '0')
        val b3 = "$"+flt.b3.toString(16).padStart(2, '0')
        val b4 = "$"+flt.b4.toString(16).padStart(2, '0')
        return "$b0, $b1, $b2, $b3, $b4"
    }

    private fun header() {
        val ourName = this.javaClass.name
        out("; 6502 assembly code for '${program.name}'")
        out("; generated by $ourName on ${Date()}")
        out("; assembler syntax is for the 64tasm cross-assembler")
        out("; output options: output=${options.output} launcher=${options.launcher} zp=${options.zeropage}")
        out("\n.cpu  '6502'\n.enc  'none'\n")

        if(program.loadAddress==0)   // fix load address
            program.loadAddress = if(options.launcher==LauncherType.BASIC) BASIC_LOAD_ADDRESS else RAW_LOAD_ADDRESS

        when {
            options.launcher == LauncherType.BASIC -> {
                if (program.loadAddress != 0x0801)
                    throw AssemblyError("BASIC output must have load address $0801")
                out("; ---- basic program with sys call ----")
                out("* = ${program.loadAddress.toHex()}")
                val year = Calendar.getInstance().get(Calendar.YEAR)
                out("\t.word  (+), $year")
                out("\t.null  $9e, format(' %d ', _prog8_entrypoint), $3a, $8f, ' prog8 by idj'")
                out("+\t.word  0")
                out("_prog8_entrypoint\t; assembly code starts here\n")
                out("\tjsr  c64utils.init_system")
            }
            options.output == OutputType.PRG -> {
                out("; ---- program without sys call ----")
                out("* = ${program.loadAddress.toHex()}\n")
                out("\tjsr  c64utils.init_system")
            }
            options.output == OutputType.RAW -> {
                out("; ---- raw assembler program ----")
                out("* = ${program.loadAddress.toHex()}\n")
            }
        }

        out("\tldx  #\$ff\t; init estack pointer")
        out("\tclc")
        out("\tjmp  main.start\t; jump to program entrypoint")
        out("")

        // the global list of all floating point constants for the whole program
        for(flt in globalFloatConsts) {
            val floatFill = makeFloatFill(Mflpt5.fromNumber(flt.key))
            out("${flt.value}\t.byte  $floatFill  ; float ${flt.key}")
        }
    }

    private fun block2asm(blk: IntermediateProgram.ProgramBlock) {
        block = blk
        out("\n; ---- block: '${block.shortname}' ----")
        if(block.address!=null) {
            out(".cerror * > ${block.address?.toHex()}, 'block address overlaps by ', *-${block.address?.toHex()},' bytes'")
            out("* = ${block.address?.toHex()}")
        }
        out("${block.shortname}\t.proc\n")
        out("\n; memdefs and kernel subroutines")
        memdefs2asm(block)
        out("\n; variables")
        vardecls2asm(block)
        out("")

        val instructionPatternWindowSize = 6
        var processed = 0

        if(trace) println("BLOCK: ${block.scopedname}  ${block.address ?: ""}")
        for (ins in block.instructions.windowed(instructionPatternWindowSize, partialWindows = true)) {
            if(trace) println("\t${ins[0].toString().trim()}")
            if (processed == 0) {
                processed = instr2asm(ins)
                if (processed == 0)
                // the instructions are not recognised yet and can't be translated into assembly
                    throw CompilerException("no asm translation found for instruction pattern: $ins")
            }
            processed--
        }
        if (trace) println("END BLOCK: ${block.scopedname}")
        out("\n\t.pend\n")
    }

    private fun memdefs2asm(block: IntermediateProgram.ProgramBlock) {
        for(m in block.memoryPointers) {
            out("\t${m.key} = ${m.value.first.toHex()}")
        }
    }

    private fun vardecls2asm(block: IntermediateProgram.ProgramBlock) {
        val sortedVars = block.variables.toList().sortedBy { it.second.type }
        for (v in sortedVars) {
            when (v.second.type) {
                DataType.UBYTE -> out("${v.first}\t.byte  0")
                DataType.BYTE -> out("${v.first}\t.char  0")
                DataType.UWORD -> out("${v.first}\t.word  0")
                DataType.WORD -> out("${v.first}\t.sint  0")
                DataType.FLOAT -> out("${v.first}\t.fill  5  ; float")
                DataType.STR,
                DataType.STR_P,
                DataType.STR_S,
                DataType.STR_PS -> {
                    val rawStr = heap.get(v.second.heapId).str!!
                    val bytes = encodeStr(rawStr, v.second.type).map { "$" + it.toString(16).padStart(2, '0') }
                    out("${v.first}\t; ${v.second.type} \"${escape(rawStr)}\"")
                    for (chunk in bytes.chunked(16))
                        out("\t.byte  " + chunk.joinToString())
                }
                DataType.ARRAY_UB -> {
                    // unsigned integer byte arrayspec
                    val data = makeArrayFillDataUnsigned(v.second)
                    if (data.size <= 16)
                        out("${v.first}\t.byte  ${data.joinToString()}")
                    else {
                        out(v.first)
                        for (chunk in data.chunked(16))
                            out("\t.byte  " + chunk.joinToString())
                    }
                }
                DataType.ARRAY_B -> {
                    // signed integer byte arrayspec
                    val data = makeArrayFillDataSigned(v.second)
                    if (data.size <= 16)
                        out("${v.first}\t.char  ${data.joinToString()}")
                    else {
                        out(v.first)
                        for (chunk in data.chunked(16))
                            out("\t.char  " + chunk.joinToString())
                    }
                }
                DataType.ARRAY_UW -> {
                    // unsigned word arrayspec
                    val data = makeArrayFillDataUnsigned(v.second)
                    if (data.size <= 16)
                        out("${v.first}\t.word  ${data.joinToString()}")
                    else {
                        out(v.first)
                        for (chunk in data.chunked(16))
                            out("\t.word  " + chunk.joinToString())
                    }
                }
                DataType.ARRAY_W -> {
                    // signed word arrayspec
                    val data = makeArrayFillDataSigned(v.second)
                    if (data.size <= 16)
                        out("${v.first}\t.sint  ${data.joinToString()}")
                    else {
                        out(v.first)
                        for (chunk in data.chunked(16))
                            out("\t.sint  " + chunk.joinToString())
                    }
                }
                DataType.ARRAY_F -> {
                    // float arrayspec
                    val array = heap.get(v.second.heapId).doubleArray!!
                    val floatFills = array.map { makeFloatFill(Mflpt5.fromNumber(it)) }
                    out(v.first)
                    for(f in array.zip(floatFills))
                        out("\t.byte  ${f.second}  ; float ${f.first}")
                }
            }
        }
    }

    private fun encodeStr(str: String, dt: DataType): List<Short> {
        when(dt) {
            DataType.STR -> {
                val bytes = Petscii.encodePetscii(str, true)
                return bytes.plus(0)
            }
            DataType.STR_P -> {
                val result = listOf(str.length.toShort())
                val bytes = Petscii.encodePetscii(str, true)
                return result.plus(bytes)
            }
            DataType.STR_S -> {
                val bytes = Petscii.encodeScreencode(str, true)
                return bytes.plus(0)
            }
            DataType.STR_PS -> {
                val result = listOf(str.length.toShort())
                val bytes = Petscii.encodeScreencode(str, true)
                return result.plus(bytes)
            }
            else -> throw AssemblyError("invalid str type")
        }
    }

    private fun makeArrayFillDataUnsigned(value: Value): List<String> {
        val array = heap.get(value.heapId).array!!
        return if (value.type == DataType.ARRAY_UB || value.type == DataType.ARRAY_UW)
            array.map { "$"+it.toString(16).padStart(2, '0') }
        else
            throw AssemblyError("invalid arrayspec type")
    }

    private fun makeArrayFillDataSigned(value: Value): List<String> {
        val array = heap.get(value.heapId).array!!
        return if (value.type == DataType.ARRAY_B || value.type == DataType.ARRAY_W) {
            array.map {
                if(it>=0)
                    "$"+it.toString(16).padStart(2, '0')
                else
                    "-$"+abs(it).toString(16).padStart(2, '0')
            }
        }
        else throw AssemblyError("invalid arrayspec type")
    }

    private fun instr2asm(ins: List<Instruction>): Int {
        // find best patterns (matching the most of the lines, then with the smallest weight)
        val fragments = findPatterns(ins).sortedByDescending { it.segmentSize }
        if(fragments.isEmpty()) {
            // we didn't find any matching patterns (complex multi-instruction fragments), try simple ones
            val firstIns = ins[0]
            val singleAsm = simpleInstr2Asm(firstIns)
            if(singleAsm != null) {
                outputAsmFragment(singleAsm)
                return 1
            }
            return 0
        }
        val best = fragments[0]
        outputAsmFragment(best.asm)
        return best.segmentSize
    }

    private fun outputAsmFragment(singleAsm: String) {
        if (singleAsm.isNotEmpty()) {
            if(singleAsm.startsWith("@inline@"))
                out(singleAsm.substring(8))
            else {
                val withNewlines = singleAsm.replace('|', '\n')
                for (line in withNewlines.split('\n')) {
                    if (line.isNotEmpty()) {
                        var trimmed = if (line.startsWith(' ')) "\t" + line.trim() else line.trim()
                        trimmed = trimmed.replace(Regex("^\\+\\s+"), "+\t")  // sanitize local label indentation
                        out(trimmed)
                    }
                }
            }
        }
    }

    private fun getFloatConst(value: Value): String =
            globalFloatConsts[value.numericValue().toDouble()]
                    ?: throw AssemblyError("should have a global float const for number $value")

    private fun simpleInstr2Asm(ins: Instruction): String? {
        // a label 'instruction' is simply translated into a asm label
        if(ins is LabelInstr) {
            if(ins.name==block.shortname)
                return ""
            return if(ins.name.startsWith("${block.shortname}."))
                ins.name.substring(block.shortname.length+1)
            else
                ins.name
        }

        // simple opcodes that are translated directly into one or a few asm instructions
        return when(ins.opcode) {
            Opcode.LINE -> " ;\tsrc line: ${ins.callLabel}"
            Opcode.NOP -> " nop"      // shouldn't be present anymore though
            Opcode.TERMINATE -> " brk"
            Opcode.SEC -> " sec"
            Opcode.CLC -> " clc"
            Opcode.SEI -> " sei"
            Opcode.CLI -> " cli"
            Opcode.JUMP -> " jmp  ${ins.callLabel}"
            Opcode.CALL -> " jsr  ${ins.callLabel}"
            Opcode.RETURN -> " rts"
            Opcode.RSAVE -> {
                // save cpu status flag and all registers A, X, Y.
                // see http://6502.org/tutorials/register_preservation.html
                """
                php
                sta  ${C64Zeropage.SCRATCH_REG}
                pha
                txa
                pha
                tya
                pha
                lda  ${C64Zeropage.SCRATCH_REG}
                """
            }
            Opcode.RRESTORE -> {
                // restore all registers and cpu status flag
                " pla |  tay |  pla |  tax |  pla |  plp"
            }
            Opcode.DISCARD_BYTE -> " inx"
            Opcode.DISCARD_WORD -> " inx"
            Opcode.DISCARD_FLOAT -> " inx |  inx |  inx"
            Opcode.INLINE_ASSEMBLY ->  "@inline@" + (ins.callLabel ?: "")      // All of the inline assembly is stored in the calllabel property.
            Opcode.SYSCALL -> {
                if (ins.arg!!.numericValue() in syscallsForStackVm.map { it.callNr })
                    throw CompilerException("cannot translate vm syscalls to real assembly calls - use *real* subroutine calls instead. Syscall ${ins.arg.numericValue()}")
                val call = Syscall.values().find { it.callNr==ins.arg.numericValue() }
                " jsr  prog8_lib.${call.toString().toLowerCase()}"
            }
            Opcode.BREAKPOINT -> {
                breakpointCounter++
                "_prog8_breakpoint_$breakpointCounter\tnop"
            }

            Opcode.PUSH_BYTE -> {
                " lda  #${hexVal(ins)} |  sta  ${ESTACK_LO.toHex()},x |  dex"
            }
            Opcode.PUSH_WORD -> {
                val value = hexVal(ins)
                " lda  #<$value |  sta  ${ESTACK_LO.toHex()},x |  lda  #>$value |  sta  ${ESTACK_HI.toHex()},x |  dex"
            }
            Opcode.PUSH_FLOAT -> {
                val floatConst = getFloatConst(ins.arg!!)
                " lda  #<$floatConst |  ldy  #>$floatConst |  jsr  prog8_lib.push_float"
            }
            Opcode.PUSH_VAR_BYTE -> {
                when(ins.callLabel) {
                    "X" -> throw CompilerException("makes no sense to push X, it's used as a stack pointer itself. You should probably not use the X register (or only in trivial assignments)")
                    "A" -> " sta  ${ESTACK_LO.toHex()},x |  dex"
                    "Y" -> " tya |  sta  ${ESTACK_LO.toHex()},x |  dex"
                    else -> " lda  ${ins.callLabel} |  sta  ${ESTACK_LO.toHex()},x |  dex"
                }
            }
            Opcode.PUSH_VAR_WORD -> {
                " lda  ${ins.callLabel} |  ldy  ${ins.callLabel}+1 |  sta  ${ESTACK_LO.toHex()},x |  pha |  tya |  sta  ${ESTACK_HI.toHex()},x |  pla |  dex"
            }
            Opcode.PUSH_VAR_FLOAT -> " lda  #<${ins.callLabel} |  ldy  #>${ins.callLabel}|  jsr  prog8_lib.push_float"
            Opcode.PUSH_MEM_B, Opcode.PUSH_MEM_UB -> {
                """
                lda  ${hexVal(ins)}
                sta  ${ESTACK_LO.toHex()},x
                dex
                """
            }
            Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_UW -> {
                """
                lda  ${hexVal(ins)}
                sta  ${ESTACK_LO.toHex()},x
                lda  ${hexValPlusOne(ins)}
                sta  ${ESTACK_HI.toHex()},x
                dex
                """
            }
            Opcode.PUSH_MEM_FLOAT -> {
                " lda  #<${hexVal(ins)} |  ldy  #>${hexVal(ins)}|  jsr  prog8_lib.push_float"
            }

            Opcode.PUSH_REGAY_WORD -> {
                " sta  ${ESTACK_LO.toHex()},x |  tya |  sta  ${ESTACK_HI.toHex()},x |  dex "
            }
            Opcode.POP_REGAX_WORD -> throw AssemblyError("cannot load X register from stack because it's used as the stack pointer itself")
            Opcode.POP_REGXY_WORD -> throw AssemblyError("cannot load X register from stack because it's used as the stack pointer itself")
            Opcode.POP_REGAY_WORD -> {
                " inx |  lda  ${ESTACK_LO.toHex()},x |  ldy  ${ESTACK_HI.toHex()},x "
            }

            Opcode.READ_INDEXED_VAR_BYTE -> {
                """
                ldy  ${(ESTACK_LO+1).toHex()},x
                lda  ${ins.callLabel},y
                sta  ${(ESTACK_LO+1).toHex()},x
                """
            }
            Opcode.READ_INDEXED_VAR_WORD -> {
                """
                lda  ${(ESTACK_LO+1).toHex()},x
                asl  a
                tay
                lda  ${ins.callLabel},y
                sta  ${(ESTACK_LO+1).toHex()},x
                lda  ${ins.callLabel}+1,y
                sta  ${(ESTACK_HI+1).toHex()},x
                """
            }
            Opcode.READ_INDEXED_VAR_FLOAT -> {
                """
                lda  #<${ins.callLabel}
                ldy  #>${ins.callLabel}
                jsr  prog8_lib.push_float_from_indexed_var
                """
            }
            Opcode.WRITE_INDEXED_VAR_BYTE -> {
                """
                inx
                lda  ${ESTACK_LO.toHex()},x
                inx
                ldy  ${ESTACK_LO.toHex()},x
                sta  ${ins.callLabel},y
                """
            }
            Opcode.WRITE_INDEXED_VAR_WORD -> {
                """
                inx
                inx
                lda  ${ESTACK_LO.toHex()},x
                asl  a
                tay
                lda  ${(ESTACK_LO-1).toHex()},x
                sta  ${ins.callLabel},y
                lda  ${(ESTACK_HI-1).toHex()},x
                sta  ${ins.callLabel}+1,y
                """
            }
            Opcode.WRITE_INDEXED_VAR_FLOAT -> {
                """
                lda  #<${ins.callLabel}
                ldy  #>${ins.callLabel}
                jsr  prog8_lib.pop_float_to_indexed_var
                """
            }
            Opcode.POP_MEM_BYTE -> {
                """
                inx
                lda  ${ESTACK_LO.toHex()},x
                sta  ${hexVal(ins)}
                """
            }
            Opcode.POP_MEM_WORD -> {
                """
                inx
                lda  ${ESTACK_LO.toHex()},x
                sta  ${hexVal(ins)}
                lda  ${ESTACK_HI.toHex()},x
                sta  ${hexValPlusOne(ins)}
                """
            }
            Opcode.POP_MEM_FLOAT -> {
                " lda  ${hexVal(ins)} |  ldy  ${hexValPlusOne(ins)} |  jsr  prog8_lib.pop_mem_float"
            }
            Opcode.POP_VAR_BYTE -> {
                when (ins.callLabel) {
                    "X" -> throw CompilerException("makes no sense to pop X, it's used as a stack pointer itself")
                    "A" -> " inx |  lda  ${ESTACK_LO.toHex()},x"
                    "Y" -> " inx |  ldy  ${ESTACK_LO.toHex()},x"
                    else -> " inx |  lda  ${ESTACK_LO.toHex()},x |  sta  ${ins.callLabel}"
                }
            }
            Opcode.POP_VAR_WORD -> {
                " inx |  lda  ${ESTACK_LO.toHex()},x |  ldy  ${ESTACK_HI.toHex()},x |  sta  ${ins.callLabel} |  sty  ${ins.callLabel}+1"
            }
            Opcode.POP_VAR_FLOAT -> {
                " lda  #<${ins.callLabel} |  ldy  #>${ins.callLabel} |  jsr  prog8_lib.pop_float"
            }

            Opcode.INC_VAR_UB, Opcode.INC_VAR_B -> {
                when (ins.callLabel) {
                    "A" -> " clc |  adc  #1"
                    "X" -> " inx"
                    "Y" -> " iny"
                    else -> " inc  ${ins.callLabel}"
                }
            }
            Opcode.INC_VAR_UW -> {
                " inc  ${ins.callLabel} |  bne  + |  inc  ${ins.callLabel}+1 |+"
            }
            Opcode.INC_VAR_F -> {
                """
                lda  #<${ins.callLabel}
                ldy  #>${ins.callLabel}
                jsr  prog8_lib.inc_var_f
                """
            }
            Opcode.DEC_VAR_UB, Opcode.DEC_VAR_B -> {
                when (ins.callLabel) {
                    "A" -> " sec |  sbc  #1"
                    "X" -> " dex"
                    "Y" -> " dey"
                    else -> " dec  ${ins.callLabel}"
                }
            }
            Opcode.DEC_VAR_UW -> {
                " lda  ${ins.callLabel} |  bne  + |  dec  ${ins.callLabel}+1 |+ |  dec  ${ins.callLabel}"
            }
            Opcode.DEC_VAR_F -> {
                """
                lda  #<${ins.callLabel}
                ldy  #>${ins.callLabel}
                jsr  prog8_lib.dec_var_f
                """
            }
            Opcode.NEG_B -> {
                """
                lda  ${(ESTACK_LO+1).toHex()},x
                eor  #255
                sec
                adc  #0
                sta  ${(ESTACK_LO+1).toHex()},x
                """
            }
            Opcode.NEG_F -> " jsr  prog8_lib.neg_f"
            Opcode.INV_BYTE -> {
                """
                lda  ${(ESTACK_LO + 1).toHex()},x
                eor  #255
                sta  ${(ESTACK_LO + 1).toHex()},x
                """
            }
            Opcode.INV_WORD -> {
                """
                lda  ${(ESTACK_LO+1).toHex()},x
                eor  #255
                sta  ${(ESTACK_LO+1).toHex()},x
                lda  ${(ESTACK_HI+1).toHex()},x
                eor  #255
                sta  ${(ESTACK_HI+1).toHex()},x
                """
            }
            Opcode.NOT_BYTE -> {
                """
                lda  ${(ESTACK_LO+1).toHex()},x
                beq  +
                lda  #0
                beq ++
+               lda  #1
+               sta  ${(ESTACK_LO+1).toHex()},x
                """
            }
            Opcode.NOT_WORD -> {
                """
                lda  ${(ESTACK_LO + 1).toHex()},x
                ora  ${(ESTACK_HI + 1).toHex()},x
                beq  +
                lda  #0
                beq  ++
+               lda  #1
+               sta  ${(ESTACK_LO + 1).toHex()},x |  sta  ${(ESTACK_HI + 1).toHex()},x
                """
            }

            Opcode.BCS -> " bcs  ${ins.callLabel}"
            Opcode.BCC -> " bcc  ${ins.callLabel}"
            Opcode.BNEG -> " bmi  ${ins.callLabel}"
            Opcode.BPOS -> " bpl  ${ins.callLabel}"
            Opcode.BVC -> " bvc  ${ins.callLabel}"
            Opcode.BVS -> " bvs  ${ins.callLabel}"
            Opcode.BZ -> " beq  ${ins.callLabel}"
            Opcode.BNZ -> " bne  ${ins.callLabel}"
            Opcode.JZ -> {
                """
                inx
                lda  ${(ESTACK_LO).toHex()},x
                beq  ${ins.callLabel}
                """
            }
            Opcode.JZW -> {
                """
                inx
                lda  ${(ESTACK_LO).toHex()},x
                beq  ${ins.callLabel}
                lda  ${(ESTACK_HI).toHex()},x
                beq  ${ins.callLabel}
                """
            }
            Opcode.JNZ -> {
                """
                inx
                lda  ${(ESTACK_LO).toHex()},x
                bne  ${ins.callLabel}
                """
            }
            Opcode.JNZW -> {
                """
                inx
                lda  ${(ESTACK_LO).toHex()},x
                bne  ${ins.callLabel}
                lda  ${(ESTACK_HI).toHex()},x
                bne  ${ins.callLabel}
                """
            }
            Opcode.UB2FLOAT -> " jsr  prog8_lib.ub2float"
            Opcode.B2FLOAT -> " jsr  prog8_lib.b2float"
            Opcode.UW2FLOAT -> " jsr  prog8_lib.uw2float"
            Opcode.W2FLOAT -> " jsr  prog8_lib.w2float"
            Opcode.B2UB -> ""   // is a no-op, just carry on with the byte as-is
            Opcode.UB2B -> ""   // is a no-op, just carry on with the byte as-is
            Opcode.UB2UWORD -> " lda  #0 |  sta  ${ESTACK_HI+1},x"
            Opcode.B2WORD ->  " ${signExtendA("${ESTACK_HI+1},x")}"

            Opcode.DIV_UB -> "  jsr  prog8_lib.div_ub"
            Opcode.DIV_B -> "  jsr  prog8_lib.div_b"
            Opcode.DIV_F -> "  jsr  prog8_lib.div_f"
            Opcode.DIV_W -> "  jsr  prog8_lib.div_w"
            Opcode.DIV_UW -> "  jsr  prog8_lib.div_uw"
            Opcode.ADD_UB, Opcode.ADD_B -> {
                """
                lda  ${(ESTACK_LO + 2).toHex()},x
                clc
                adc  ${(ESTACK_LO + 1).toHex()},x
                inx
                sta  ${(ESTACK_LO + 1).toHex()},x
                """
            }
            Opcode.SUB_UB, Opcode.SUB_B -> {
                """
                lda  ${(ESTACK_LO + 2).toHex()},x
                sec
                sbc  ${(ESTACK_LO + 1).toHex()},x
                inx
                sta  ${(ESTACK_LO + 1).toHex()},x
                """
            }
            Opcode.ADD_W -> "  jsr  prog8_lib.add_w"    // todo inline?
            Opcode.ADD_UW -> "  jsr  prog8_lib.add_uw"  // todo inline?
            Opcode.ADD_F -> "  jsr  prog8_lib.add_f"
            Opcode.SUB_W -> "  jsr  prog8_lib.sub_w"    // todo inline?
            Opcode.SUB_UW -> "  jsr  prog8_lib.sub_uw"    // todo inline?
            Opcode.SUB_F -> "  jsr  prog8_lib.sub_f"
            Opcode.MUL_B -> "  jsr  prog8_lib.mul_b"
            Opcode.MUL_UB -> "  jsr  prog8_lib.mul_ub"
            Opcode.MUL_W -> "  jsr  prog8_lib.mul_w"
            Opcode.MUL_UW -> "  jsr  prog8_lib.mul_uw"
            Opcode.MUL_F -> "  jsr  prog8_lib.mul_f"

            Opcode.AND_BYTE -> {
                """
                lda  ${(ESTACK_LO + 2).toHex()},x
                and  ${(ESTACK_LO + 1).toHex()},x
                inx
                sta  ${(ESTACK_LO + 1).toHex()},x
                """
            }
            Opcode.OR_BYTE -> {
                """
                lda  ${(ESTACK_LO + 2).toHex()},x
                ora  ${(ESTACK_LO + 1).toHex()},x
                inx
                sta  ${(ESTACK_LO + 1).toHex()},x
                """
            }
            Opcode.REMAINDER_B -> "  jsr prog8_lib.remainder_b"
            Opcode.REMAINDER_UB -> "  jsr prog8_lib.remainder_ub"
            Opcode.REMAINDER_W -> "  jsr prog8_lib.remainder_w"
            Opcode.REMAINDER_UW -> "  jsr prog8_lib.remainder_uw"
            Opcode.REMAINDER_F -> "  jsr prog8_lib.remainder_f"

            Opcode.GREATER_B -> "  jsr prog8_lib.greater_b"
            Opcode.GREATER_UB -> "  jsr prog8_lib.greater_ub"
            Opcode.GREATER_W -> "  jsr prog8_lib.greater_w"
            Opcode.GREATER_UW -> "  jsr prog8_lib.greater_uw"
            Opcode.GREATER_F -> "  jsr prog8_lib.greater_f"

            Opcode.GREATEREQ_B -> "  jsr prog8_lib.greatereq_b"
            Opcode.GREATEREQ_UB -> "  jsr prog8_lib.greatereq_ub"
            Opcode.GREATEREQ_W -> "  jsr prog8_lib.greatereq_w"
            Opcode.GREATEREQ_UW -> "  jsr prog8_lib.greatereq_uw"
            Opcode.GREATEREQ_F -> "  jsr prog8_lib.greatereq_f"

            Opcode.EQUAL_BYTE -> "  jsr prog8_lib.equal_b"
            Opcode.EQUAL_WORD -> "  jsr prog8_lib.equal_w"
            Opcode.EQUAL_F -> "  jsr prog8_lib.equal_f"

            Opcode.LESS_UB -> "  jsr  prog8_lib.less_ub"
            Opcode.LESS_B -> "  jsr  prog8_lib.less_b"
            Opcode.LESS_UW -> "  jsr  prog8_lib.less_uw"
            Opcode.LESS_W -> "  jsr  prog8_lib.less_w"
            Opcode.LESS_F -> "  jsr  prog8_lib.less_f"

            Opcode.LESSEQ_UB -> "  jsr  prog8_lib.lesseq_ub"
            Opcode.LESSEQ_B -> "  jsr  prog8_lib.lesseq_b"
            Opcode.LESSEQ_UW -> "  jsr  prog8_lib.lesseq_uw"
            Opcode.LESSEQ_W -> "  jsr  prog8_lib.lesseq_w"
            Opcode.LESSEQ_F -> "  jsr  prog8_lib.lesseq_f"

            else -> null
        }
    }

    private fun findPatterns(segment: List<Instruction>): List<AsmFragment> {
        val opcodes = segment.map { it.opcode }
        val result = mutableListOf<AsmFragment>()

        // check for operations that modify a single value, by putting it on the stack (and popping it afterwards)
        if((opcodes[0]==Opcode.PUSH_VAR_BYTE && opcodes[2]==Opcode.POP_VAR_BYTE) ||
                (opcodes[0]==Opcode.PUSH_VAR_WORD && opcodes[2]==Opcode.POP_VAR_WORD) ||
                (opcodes[0]==Opcode.PUSH_VAR_FLOAT && opcodes[2]==Opcode.POP_VAR_FLOAT)) {
            if (segment[0].callLabel == segment[2].callLabel) {
                val fragment = sameVarOperation(segment[0].callLabel!!, segment[1])
                if (fragment != null) {
                    fragment.segmentSize = 3
                    result.add(fragment)
                }
            }
        }
        else if((opcodes[0]==Opcode.PUSH_MEM_UB && opcodes[2]==Opcode.POP_MEM_BYTE) ||
                (opcodes[0]==Opcode.PUSH_MEM_B && opcodes[2]==Opcode.POP_MEM_BYTE) ||
                (opcodes[0]==Opcode.PUSH_MEM_UW && opcodes[2]==Opcode.POP_MEM_WORD) ||
                (opcodes[0]==Opcode.PUSH_MEM_W && opcodes[2]==Opcode.POP_MEM_WORD) ||
                (opcodes[0]==Opcode.PUSH_MEM_FLOAT && opcodes[2]==Opcode.POP_MEM_FLOAT)) {
            if(segment[0].arg==segment[2].arg) {
                val fragment = sameMemOperation(segment[0].arg!!.integerValue(), segment[1])
                if(fragment!=null) {
                    fragment.segmentSize = 3
                    result.add(fragment)
                }
            }
        }
        else if((opcodes[0]==Opcode.PUSH_BYTE && opcodes[1]==Opcode.READ_INDEXED_VAR_BYTE &&
                        opcodes[3]==Opcode.PUSH_BYTE && opcodes[4]==Opcode.WRITE_INDEXED_VAR_BYTE) ||
                (opcodes[0]==Opcode.PUSH_BYTE && opcodes[1]==Opcode.READ_INDEXED_VAR_WORD &&
                        opcodes[3]==Opcode.PUSH_BYTE && opcodes[4]==Opcode.WRITE_INDEXED_VAR_WORD)) {
            if(segment[0].arg==segment[3].arg && segment[1].callLabel==segment[4].callLabel) {
                val fragment = sameConstantIndexedVarOperation(segment[1].callLabel!!, segment[0].arg!!.integerValue(), segment[2])
                if(fragment!=null){
                    fragment.segmentSize = 5
                    result.add(fragment)
                }
            }
        }
        else if((opcodes[0]==Opcode.PUSH_VAR_BYTE && opcodes[1]==Opcode.READ_INDEXED_VAR_BYTE &&
                        opcodes[3]==Opcode.PUSH_VAR_BYTE && opcodes[4]==Opcode.WRITE_INDEXED_VAR_BYTE) ||
                (opcodes[0]==Opcode.PUSH_VAR_BYTE && opcodes[1]==Opcode.READ_INDEXED_VAR_WORD &&
                        opcodes[3]==Opcode.PUSH_VAR_BYTE && opcodes[4]==Opcode.WRITE_INDEXED_VAR_WORD)) {
            if(segment[0].callLabel==segment[3].callLabel && segment[1].callLabel==segment[4].callLabel) {
                val fragment = sameIndexedVarOperation(segment[1].callLabel!!, segment[0].callLabel!!, segment[2])
                if(fragment!=null){
                    fragment.segmentSize = 5
                    result.add(fragment)
                }
            }
        }

        for(pattern in patterns.filter { it.sequence.size <= segment.size || (it.altSequence != null && it.altSequence.size <= segment.size)}) {
            val opcodesList = opcodes.subList(0, pattern.sequence.size)
            if(pattern.sequence == opcodesList) {
                val asm = pattern.asm(segment)
                if(asm!=null)
                    result.add(AsmFragment(asm, pattern.sequence.size))
            } else if(pattern.altSequence == opcodesList) {
                val asm = pattern.asm(segment)
                if(asm!=null)
                    result.add(AsmFragment(asm, pattern.sequence.size))
            }
        }

        return result
    }

    private fun sameConstantIndexedVarOperation(variable: String, index: Int, ins: Instruction): AsmFragment? {
        // an in place operation that consists of a push-value / op / push-index-value / pop-into-indexed-var
        return when(ins.opcode) {
            Opcode.SHL_BYTE -> AsmFragment(" asl  $variable+$index", 8)
            Opcode.SHR_BYTE -> AsmFragment(" lsr  $variable+$index", 8)
            Opcode.SHL_WORD -> AsmFragment(" asl  $variable+$index |  rol  $variable+${index+1}", 8)
            Opcode.SHR_WORD -> AsmFragment(" lsr  $variable+${index+1},x |  ror  $variable+$index", 8)
            Opcode.ROL_BYTE -> AsmFragment(" rol  $variable+$index", 8)
            Opcode.ROR_BYTE -> AsmFragment(" ror  $variable+$index", 8)
            Opcode.ROL_WORD -> AsmFragment(" rol  $variable+$index |  rol  $variable+${index+1}", 8)
            Opcode.ROR_WORD -> AsmFragment(" ror  $variable+${index+1} |  ror  $variable+$index", 8)
            Opcode.ROL2_BYTE -> AsmFragment(" lda  $variable+$index |  cmp  #\$80 |  rol  $variable+$index", 8)
            Opcode.ROR2_BYTE -> AsmFragment(" lda  $variable+$index |  lsr  a |  bcc  + |  ora  #\$80 |+ |  sta  $variable+$index", 10)
            Opcode.ROL2_WORD -> AsmFragment(" asl  $variable+$index |  rol  $variable+${index+1} |  bcc  + |  inc  $variable+$index |+",20)
            Opcode.ROR2_WORD -> AsmFragment(" lsr  $variable+${index+1} |  ror  $variable+$index |  bcc  + |  lda  $variable+${index+1} |  ora  #\$80 |  sta  $variable+${index+1} |+", 30)
            else -> null
        }
    }

    private fun sameIndexedVarOperation(variable: String, indexVar: String, ins: Instruction): AsmFragment? {
        // an in place operation that consists of a push-value / op / push-index-var / pop-into-indexed-var
        val saveX = " stx  ${C64Zeropage.SCRATCH_B1} |"
        val restoreX = " | ldx  ${C64Zeropage.SCRATCH_B1}"
        val loadXWord: String
        val loadX: String

        when(indexVar) {
            "X" -> {
                loadX = ""
                loadXWord = " txa |  asl  a |  tax |"
            }
            "Y" -> {
                loadX = " tya |  tax |"
                loadXWord = " tya |  asl  a |  tax |"
            }
            "A" -> {
                loadX = " tax |"
                loadXWord = " asl  a |  tax |"
            }
            else -> {
                // the indexvar is a real variable, not a register
                loadX = " ldx  $indexVar |"
                loadXWord = " lda  $indexVar |  asl  a |  tax |"
            }
        }

        return when (ins.opcode) {
            Opcode.SHL_BYTE -> AsmFragment(" txa |  $loadX  asl  $variable,x |  tax", 10)
            Opcode.SHR_BYTE -> AsmFragment(" txa |  $loadX  lsr  $variable,x |  tax", 10)
            Opcode.SHL_WORD -> AsmFragment("$saveX $loadXWord  asl  $variable,x |  rol  $variable+1,x  $restoreX", 10)
            Opcode.SHR_WORD -> AsmFragment("$saveX $loadXWord  lsr  $variable+1,x |  ror  $variable,x  $restoreX", 10)
            Opcode.ROL_BYTE -> AsmFragment(" txa |  $loadX  rol  $variable,x |  tax", 10)
            Opcode.ROR_BYTE -> AsmFragment(" txa |  $loadX  ror  $variable,x |  tax", 10)
            Opcode.ROL_WORD -> AsmFragment("$saveX $loadXWord  rol  $variable,x |  rol  $variable+1,x  $restoreX", 10)
            Opcode.ROR_WORD -> AsmFragment("$saveX $loadXWord  ror  $variable+1,x |  ror  $variable,x  $restoreX", 10)
            Opcode.ROL2_BYTE -> AsmFragment("$saveX $loadX  lda  $variable,x |  cmp  #\$80 |  rol  $variable,x  $restoreX", 10)
            Opcode.ROR2_BYTE -> AsmFragment("$saveX $loadX  lda  $variable,x |  lsr  a |  bcc  + |  ora  #\$80 |+ |  sta  $variable,x  $restoreX", 10)
            Opcode.ROL2_WORD -> AsmFragment(" txa |  $loadXWord  asl  $variable,x |  rol  $variable+1,x |  bcc  + |  inc  $variable,x  |+  |  tax", 30)
            Opcode.ROR2_WORD -> AsmFragment("$saveX $loadXWord  lsr  $variable+1,x |  ror  $variable,x |  bcc  + |  lda  $variable+1,x |  ora  #\$80 |  sta  $variable+1,x |+  $restoreX", 30)
            else -> null
        }
    }

    private fun sameMemOperation(address: Int, ins: Instruction): AsmFragment? {
        // an in place operation that consists of a push-mem / op / pop-mem sequence
        val addr = address.toHex()
        val addrHi = (address+1).toHex()
        return when(ins.opcode) {
            Opcode.SHL_BYTE -> AsmFragment(" asl  $addr", 10)
            Opcode.SHR_BYTE -> AsmFragment(" lsr  $addr", 10)
            Opcode.SHL_WORD -> AsmFragment(" asl  $addr |  rol  $addrHi", 10)
            Opcode.SHR_WORD -> AsmFragment(" lsr  $addrHi |  ror  $addr", 10)
            Opcode.ROL_BYTE -> AsmFragment(" rol  $addr", 10)
            Opcode.ROR_BYTE -> AsmFragment(" ror  $addr", 10)
            Opcode.ROL_WORD -> AsmFragment(" rol  $addr |  rol  $addrHi", 10)
            Opcode.ROR_WORD -> AsmFragment(" ror  $addrHi |  ror  $addr", 10)
            Opcode.ROL2_BYTE -> AsmFragment(" lda  $addr |  cmp  #\$80 |  rol  $addr", 10)
            Opcode.ROR2_BYTE -> AsmFragment(" lda  $addr |  lsr  a |  bcc  + |  ora  #\$80 |+ |  sta  $addr", 10)
            Opcode.ROL2_WORD -> AsmFragment(" lda  $addr |  cmp #\$80 |  rol  $addr |  rol  $addrHi", 10)
            Opcode.ROR2_WORD -> AsmFragment(" lsr  $addrHi |  ror  $addr |  bcc  + |  lda  $addrHi |  ora  #$80 |  sta  $addrHi |+", 20)
            else -> null
        }
    }

    private fun sameVarOperation(variable: String, ins: Instruction): AsmFragment? {
        // an in place operation that consists of a push-var / op / pop-var
        return when(ins.opcode) {
            Opcode.SHL_BYTE -> {
                when (variable) {
                    "A" -> AsmFragment(" asl  a", 10)
                    "X" -> AsmFragment(" txa |  asl  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  asl  a |  tay", 10)
                    else -> AsmFragment(" asl  $variable", 10)
                }
            }
            Opcode.SHR_BYTE -> {
                when (variable) {
                    "A" -> AsmFragment(" lsr  a", 10)
                    "X" -> AsmFragment(" txa |  lsr  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  lsr  a |  tay", 10)
                    else -> AsmFragment(" lsr  $variable", 10)
                }
            }
            Opcode.SHL_WORD -> {
                AsmFragment(" asl  $variable |  rol  $variable+1", 10)
            }
            Opcode.SHR_WORD -> {
                AsmFragment(" lsr  $variable+1 |  ror  $variable", 10)
            }
            Opcode.ROL_BYTE -> {
                when (variable) {
                    "A" -> AsmFragment(" rol  a", 10)
                    "X" -> AsmFragment(" txa |  rol  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  rol  a |  tay", 10)
                    else -> AsmFragment(" rol  $variable", 10)
                }
            }
            Opcode.ROR_BYTE -> {
                when (variable) {
                    "A" -> AsmFragment(" ror  a", 10)
                    "X" -> AsmFragment(" txa |  ror  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  ror  a |  tay", 10)
                    else -> AsmFragment(" ror  $variable", 10)
                }
            }
            Opcode.ROL_WORD -> {
                AsmFragment(" rol  $variable |  rol  $variable+1", 10)
            }
            Opcode.ROR_WORD -> {
                AsmFragment(" ror  $variable+1 |  ror  $variable", 10)
            }
            Opcode.ROL2_BYTE -> {       // 8-bit rol
                when (variable) {
                    "A" -> AsmFragment(" cmp  #\$80 |  rol  a", 10)
                    "X" -> AsmFragment(" txa |  cmp  #\$80 |  rol  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  cmp  #\$80 |  rol  a |  tay", 10)
                    else -> AsmFragment(" lda  $variable |  cmp  #\$80  | rol  $variable", 10)
                }
            }
            Opcode.ROR2_BYTE -> {       // 8-bit ror
                when (variable) {
                    "A" -> AsmFragment(" lsr  a | bcc  + |  ora  #\$80  |+", 10)
                    "X" -> AsmFragment(" txa |  lsr  a |  bcc  + |  ora  #\$80  |+ |  tax", 10)
                    "Y" -> AsmFragment(" tya |  lsr  a |  bcc  + |  ora  #\$80  |+ |  tay", 10)
                    else -> AsmFragment(" lda  $variable |  lsr  a |  bcc  + |  ora  #\$80 |+ |  sta  $variable", 10)
                }
            }
            Opcode.ROL2_WORD -> {
                AsmFragment(" lda  $variable |  cmp #\$80 |  rol  $variable |  rol  $variable+1", 10)
            }
            Opcode.ROR2_WORD -> {
                // todo: ror2_word is very slow; it requires a library routine
                AsmFragment(" lda  $variable |  sta  ${C64Zeropage.SCRATCH_W1} |  lda  $variable+1 |  sta  ${C64Zeropage.SCRATCH_W1+1} |  jsr  prog8_lib.ror2_word |  lda  ${C64Zeropage.SCRATCH_W1} |  sta  $variable |  lda  ${C64Zeropage.SCRATCH_W1+1} |  sta  $variable+1", 30)
            }
//            Opcode.SYSCALL -> {
//                TODO("optimize SYSCALL $ins in-place on variable $variable")
//            }
            else -> null
        }
    }

    private class AsmFragment(val asm: String, var segmentSize: Int=0)

    private class AsmPattern(val sequence: List<Opcode>, val altSequence: List<Opcode>?=null, val asm: (List<Instruction>)->String?)

    private fun loadAFromIndexedByVar(idxVarInstr: Instruction, readArrayInstr: Instruction): String {
        // A =  readArrayInstr [ idxVarInstr ]
        return when (idxVarInstr.callLabel) {
            "A" -> " tay |  lda  ${readArrayInstr.callLabel},y"
            "X" -> " txa |  tay |  lda  ${readArrayInstr.callLabel},y"
            "Y" -> " lda  ${readArrayInstr.callLabel},y"
            else -> " ldy  ${idxVarInstr.callLabel} |  lda  ${readArrayInstr.callLabel},y"
        }
    }

    private fun loadAYFromWordIndexedByVar(idxVarInstr: Instruction, readArrayInstr: Instruction): String {
        // AY = readWordArrayInstr [ idxVarInstr ]
        return when(idxVarInstr.callLabel) {
            "A" ->
                """
                stx  ${C64Zeropage.SCRATCH_B1}
                asl  a
                tax
                lda  ${readArrayInstr.callLabel},x
                ldy  ${readArrayInstr.callLabel}+1,x
                ldx  ${C64Zeropage.SCRATCH_B1}
                """
            "X" ->
                """
                stx  ${C64Zeropage.SCRATCH_B1}
                txa
                asl  a
                tax
                lda  ${readArrayInstr.callLabel},x
                ldy  ${readArrayInstr.callLabel}+1,x
                ldx  ${C64Zeropage.SCRATCH_B1}
                """
            "Y" ->
                """
                stx  ${C64Zeropage.SCRATCH_B1}
                tya
                asl  a
                tax
                lda  ${readArrayInstr.callLabel},x
                ldy  ${readArrayInstr.callLabel}+1,x
                ldx  ${C64Zeropage.SCRATCH_B1}
                """
            else ->
                """
                stx  ${C64Zeropage.SCRATCH_B1}
                lda  ${idxVarInstr.callLabel}
                asl  a
                tax
                lda  ${readArrayInstr.callLabel},x
                ldy  ${readArrayInstr.callLabel}+1,x
                ldx  ${C64Zeropage.SCRATCH_B1}
                """
        }
    }

    private fun storeAToIndexedByVar(idxVarInstr: Instruction, writeArrayInstr: Instruction): String {
        // writeArrayInstr [ idxVarInstr ] =  A
        return when (idxVarInstr.callLabel) {
            "A" -> " tay |  sta  ${writeArrayInstr.callLabel},y"
            "X" -> " stx  ${C64Zeropage.SCRATCH_B1} |  ldy  ${C64Zeropage.SCRATCH_B1} |  sta  ${writeArrayInstr.callLabel},y"
            "Y" -> " sta  ${writeArrayInstr.callLabel},y"
            else -> " ldy  ${idxVarInstr.callLabel} |  sta  ${writeArrayInstr.callLabel},y"
        }
    }

    private fun intVal(valueInstr: Instruction) = valueInstr.arg!!.integerValue()
    private fun hexVal(valueInstr: Instruction) = valueInstr.arg!!.integerValue().toHex()
    private fun hexValPlusOne(valueInstr: Instruction) = (valueInstr.arg!!.integerValue()+1).toHex()
    private fun signExtendA(into: String) =
            """
            ora  #$7f
            bmi  +
            lda  #0
+           sta  $into
            """

    private val patterns = listOf(
            // ----------- assignment to BYTE VARIABLE ----------------
            // var = (u)bytevalue
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                when (segment[1].callLabel) {
                    "A", "X", "Y" ->
                        " ld${segment[1].callLabel!!.toLowerCase()}  #${hexVal(segment[0])}"
                    else ->
                        " lda  #${hexVal(segment[0])} |  sta  ${segment[1].callLabel}"
                }
            },
            // var = other var
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                when(segment[1].callLabel) {
                    "A" ->
                        when(segment[0].callLabel) {
                            "A" -> null
                            "X" -> "  txa"
                            "Y" -> "  tya"
                            else -> "  lda  ${segment[0].callLabel}"
                        }
                    "X" ->
                        when(segment[0].callLabel) {
                            "A" -> "  tax"
                            "X" -> null
                            "Y" -> "  tya |  tax"
                            else -> "  ldx  ${segment[0].callLabel}"
                        }
                    "Y" ->
                        when(segment[0].callLabel) {
                            "A" -> "  tay"
                            "X" -> "  txa |  tay"
                            "Y" -> null
                            else -> "  ldy  ${segment[0].callLabel}"
                        }
                    else ->
                        when(segment[0].callLabel) {
                            "A" -> "  sta  ${segment[1].callLabel}"
                            "X" -> "  stx  ${segment[1].callLabel}"
                            "Y" -> "  sty  ${segment[1].callLabel}"
                            else -> "  lda  ${segment[0].callLabel} |  sta ${segment[1].callLabel}"
                        }
                }
            },
            // var = mem (u)byte
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.POP_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.POP_VAR_BYTE)) { segment ->
                when(segment[1].callLabel) {
                    "A", "X", "Y" -> " ld${segment[1].callLabel!!.toLowerCase()}  ${hexVal(segment[0])}"
                    else -> " lda  ${hexVal(segment[0])} |  sta  ${segment[1].callLabel}"
                }
            },
            // var = (u)bytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                val index = intVal(segment[0])
                when (segment[2].callLabel) {
                    "A", "X", "Y" ->
                        " ld${segment[2].callLabel!!.toLowerCase()}  ${segment[1].callLabel}+$index"
                    else ->
                        " lda  ${segment[1].callLabel}+$index |  sta  ${segment[2].callLabel}"
                }
            },
            // var = (u)bytearray[indexvar]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                val loadByteA = loadAFromIndexedByVar(segment[0], segment[1])
                when(segment[2].callLabel) {
                    "A" -> " $loadByteA"
                    "X" -> " $loadByteA |  tax"
                    "Y" -> " $loadByteA |  tay"
                    else -> " $loadByteA |  sta  ${segment[2].callLabel}"
                }
            },
            // var = (u)bytearray[mem index var]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                val loadByteA = " ldy  ${hexVal(segment[0])} |  lda  ${segment[1].callLabel},y"
                when(segment[2].callLabel) {
                    "A" -> " $loadByteA"
                    "X" -> " $loadByteA |  tax"
                    "Y" -> " $loadByteA |  tay"
                    else -> " $loadByteA |  sta  ${segment[2].callLabel}"
                }
            },


            // ----------- assignment to BYTE MEMORY ----------------
            // mem = (u)byte value
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
                " lda  #${hexVal(segment[0])} |  sta  ${hexVal(segment[1])}"
            },
            // mem = (u)bytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
                when(segment[0].callLabel) {
                    "A" -> " sta  ${hexVal(segment[1])}"
                    "X" -> " stx  ${hexVal(segment[1])}"
                    "Y" -> " sty  ${hexVal(segment[1])}"
                    else -> " lda  ${segment[0].callLabel} |  sta  ${hexVal(segment[1])}"
                }
            },
            // mem = mem (u)byte
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.POP_MEM_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.POP_MEM_BYTE)) { segment ->
                " lda  ${hexVal(segment[0])} |  sta  ${hexVal(segment[1])}"
            },
            // mem = (u)bytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
                val address = hexVal(segment[2])
                val index = intVal(segment[0])
                " lda  ${segment[1].callLabel}+$index |  sta  $address"
            },
            // mem = (u)bytearray[indexvar]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_MEM_BYTE)) { segment->
                val loadByteA = loadAFromIndexedByVar(segment[0], segment[1])
                " $loadByteA |  sta  ${hexVal(segment[2])}"
            },
            // mem = (u)bytearray[mem index var]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_MEM_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
                """
                ldy  ${hexVal(segment[0])}
                lda  ${segment[1].callLabel},y
                sta  ${hexVal(segment[2])}
                """
            },


            // ----------- assignment to BYTE ARRAY ----------------
            // bytearray[index] = (u)byte value
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val index = intVal(segment[1])
                val value = hexVal(segment[0])
                " lda  #$value |  sta  ${segment[2].callLabel}+$index"
            },
            // bytearray[index] = (u)bytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val index = intVal(segment[1])
                when(segment[0].callLabel) {
                    "A" -> " sta  ${segment[2].callLabel}+$index"
                    "X" -> " stx  ${segment[2].callLabel}+$index"
                    "Y" -> " sty  ${segment[2].callLabel}+$index"
                    else -> " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel}+$index"
                }
            },
            // bytearray[index] = mem(u)byte
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val index = intVal(segment[1])
                " lda  ${hexVal(segment[0])} |  sta  ${segment[2].callLabel}+$index"
            },

            // bytearray[index var] = (u)byte value
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val storeA = storeAToIndexedByVar(segment[1], segment[2])
                " lda  #${hexVal(segment[0])} |  $storeA"
            },
            // (u)bytearray[index var] = (u)bytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val storeA = storeAToIndexedByVar(segment[1], segment[2])
                when(segment[0].callLabel) {
                    "A" -> " $storeA"
                    "X" -> " txa |  $storeA"
                    "Y" -> " tya |  $storeA"
                    else -> " lda  ${segment[0].callLabel} |  $storeA"
                }
            },
            // (u)bytearray[index var] = mem (u)byte
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val storeA = storeAToIndexedByVar(segment[1], segment[2])
                " lda  ${hexVal(segment[0])} |  $storeA"
            },

            // bytearray[index mem] = (u)byte value
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                """
                lda  #${hexVal(segment[0])}
                ldy  ${hexVal(segment[1])}
                sta  ${segment[2].callLabel},y
                """
            },
            // bytearray[index mem] = (u)byte var
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val loadY = " ldy  ${hexVal(segment[1])}"
                when(segment[0].callLabel) {
                    "A" -> " $loadY |  sta  ${segment[2].callLabel},y"
                    "X" -> " txa |  $loadY |  sta  ${segment[2].callLabel},y"
                    "Y" -> " tya |  $loadY |  sta  ${segment[2].callLabel},y"
                    else -> " lda  ${segment[0].callLabel} |  $loadY |  sta  ${segment[2].callLabel},y"
                }
            },
            // bytearray[index mem] = mem(u)byte
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                """
                ldy  ${hexVal(segment[1])}
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel},y
                """
            },

            // (u)bytearray2[index2] = (u)bytearray1[index1]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment->
                val index1 = intVal(segment[0])
                val index2 = intVal(segment[2])
                " lda  ${segment[1].callLabel}+$index1 |  sta  ${segment[3].callLabel}+$index2"
            },
            // (u)bytearray2[index2] = (u)bytearray[indexvar]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val loadByteA = loadAFromIndexedByVar(segment[0], segment[1])
                val index2 = intVal(segment[2])
                " $loadByteA |  sta  ${segment[3].callLabel}+$index2"
            },
            // (u)bytearray[index2] = (u)bytearray[mem ubyte]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val index2 = intVal(segment[2])
                """
                ldy  ${hexVal(segment[0])}
                lda  ${segment[1].callLabel},y
                sta  ${segment[3].callLabel}+$index2
                """
            },

            // (u)bytearray2[idxvar2] = (u)bytearray1[index1]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val storeA = storeAToIndexedByVar(segment[2], segment[3])
                val index1 = intVal(segment[0])
                " lda  ${segment[1].callLabel}+$index1 |  $storeA"
            },
            // (u)bytearray2[idxvar2] = (u)bytearray1[idxvar]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val loadA = loadAFromIndexedByVar(segment[0], segment[1])
                val storeA = storeAToIndexedByVar(segment[2], segment[3])
                " $loadA |  $storeA"
            },
            // (u)bytearray2[idxvar2] = (u)bytearray1[mem ubyte]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val storeA = storeAToIndexedByVar(segment[2], segment[3])
                " ldy  ${hexVal(segment[0])} |  lda  ${segment[1].callLabel},y |  $storeA"
            },

            // (u)bytearray2[index mem] = (u)bytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val index1 = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index1
                ldy  ${hexVal(segment[2])}
                sta  ${segment[3].callLabel},y
                """
            },



            // ----------- assignment to WORD VARIABLE ----------------
            // var = wordvalue
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_VAR_WORD)) { segment ->
                val number = hexVal(segment[0])
                """
                lda  #<$number
                sta  ${segment[1].callLabel}
                lda  #>$number
                sta  ${segment[1].callLabel}+1
                """
            },
            // var = ubytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.UB2UWORD, Opcode.POP_VAR_WORD)) { segment ->
                when(segment[0].callLabel) {
                    "A" -> " sta  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
                    "X" -> " stx  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
                    "Y" -> " sty  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
                    else -> " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
                }
            },
            // var = other var
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                ldy  ${segment[0].callLabel}+1
                sta  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
            },
            // var = address-of other var
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  #<${segment[0].callLabel}
                ldy  #>${segment[0].callLabel}
                sta  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
            },
            // var = mem ubyte
            AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.UB2UWORD, Opcode.POP_VAR_WORD)) { segment ->
                " lda  ${hexVal(segment[0])} |  sta  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
            },
            // var = mem (u)word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_W, Opcode.POP_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UW, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[1].callLabel}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${segment[1].callLabel}+1
                """
            },
            // var = ubytearray[index_byte]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2UWORD, Opcode.POP_VAR_WORD)) { segment ->
                val index = hexVal(segment[0])
                " lda  ${segment[1].callLabel}+$index |  sta  ${segment[3].callLabel} |  lda  #0 |  sta  ${segment[3].callLabel}+1"
            },
            // var = ubytearray[index var]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2UWORD, Opcode.POP_VAR_WORD)) { segment ->
                val loadA = loadAFromIndexedByVar(segment[0], segment[1])
                " $loadA |  sta  ${segment[3].callLabel} |  lda  #0 |  sta  ${segment[3].callLabel}+1"
            },
            // var = ubytearray[index mem]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2UWORD, Opcode.POP_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2UWORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel}
                lda  #0
                sta  ${segment[3].callLabel}+1
                """
            },
            // var = (u)wordarray[index_byte]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
                val index = segment[0].arg!!.integerValue()*2
                " lda  ${segment[1].callLabel}+$index |  sta  ${segment[2].callLabel} |  lda  ${segment[1].callLabel}+${index+1} |  sta  ${segment[2].callLabel}+1"
            },
            // var = (u)wordarray[index var]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
                val loadAY = loadAYFromWordIndexedByVar(segment[0], segment[1])
                """
                $loadAY
                sta  ${segment[2].callLabel}
                sty  ${segment[2].callLabel}+1
                """
            },
            // var = (u)wordarray[index mem]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                asl  a
                tay
                lda  ${segment[1].callLabel},y
                sta  ${segment[2].callLabel}
                lda  ${segment[1].callLabel}+1,y
                sta  ${segment[2].callLabel}+1
                """
            },
            // mem = (u)word value
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  #<${hexVal(segment[0])}
                sta  ${hexVal(segment[1])}
                lda  #>${hexVal(segment[0])}
                sta  ${hexValPlusOne(segment[1])}
                """
            },
            // mem uword = ubyte var
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.UB2UWORD, Opcode.POP_MEM_WORD)) { segment ->
                when(segment[0].callLabel) {
                    "A" -> " sta  ${hexVal(segment[2])} |  lda  #0 |  sta  ${hexValPlusOne(segment[2])}"
                    "X" -> " stx  ${hexVal(segment[2])} |  lda  #0 |  sta  ${hexValPlusOne(segment[2])}"
                    "Y" -> " sty  ${hexVal(segment[2])} |  lda  #0 |  sta  ${hexValPlusOne(segment[2])}"
                    else -> " lda  ${segment[0].callLabel} ||  sta  ${hexVal(segment[2])} |  lda  #0 |  sta  ${hexValPlusOne(segment[2])}"
                }
            },
            // mem uword = mem ubyte
            AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.UB2UWORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${hexVal(segment[2])}
                lda  #0
                sta  ${hexValPlusOne(segment[2])}
                """
            },
            // mem uword = uword var
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_MEM_WORD)) { segment ->
                " lda  ${segment[0].callLabel} ||  sta  ${hexVal(segment[1])} |  lda  ${segment[0].callLabel}+1 |  sta  ${hexValPlusOne(segment[1])}"
            },
            // mem uword = address-of var
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_MEM_WORD)) { segment ->
                " lda  #<${segment[0].callLabel} ||  sta  ${hexVal(segment[1])} |  lda  #>${segment[0].callLabel} |  sta  ${hexValPlusOne(segment[1])}"
            },
            // mem (u)word = mem (u)word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UW, Opcode.POP_MEM_WORD),
                    listOf(Opcode.PUSH_MEM_W, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                ldy  ${hexValPlusOne(segment[0])}
                sta  ${hexVal(segment[1])}
                sty  ${hexValPlusOne(segment[1])}
                """
            },
            // mem uword = ubytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2UWORD, Opcode.POP_MEM_WORD)) { segment ->
                val index = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${hexVal(segment[3])}
                lda  #0
                sta  ${hexValPlusOne(segment[3])}
                """
            },
            // mem uword = bytearray[index]  (sign extended)
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2WORD, Opcode.POP_MEM_WORD)) { segment ->
                val index = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${hexVal(segment[3])}
                ${signExtendA(hexValPlusOne(segment[3]))}
                """
            },
            // mem uword = bytearray[index var]  (sign extended)
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2WORD, Opcode.POP_MEM_WORD)) { segment ->
                val loadA = loadAFromIndexedByVar(segment[0], segment[1])
                """
                $loadA
                sta  ${hexVal(segment[3])}
                ${signExtendA(hexValPlusOne(segment[3]))}
                """
            },
            // mem uword = bytearray[mem (u)byte]  (sign extended)
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2WORD, Opcode.POP_MEM_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${hexVal(segment[3])}
                ${signExtendA(hexValPlusOne(segment[3]))}
                """
            },
            // mem uword = ubytearray[index var]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2UWORD, Opcode.POP_MEM_WORD)) { segment ->
                val loadA = loadAFromIndexedByVar(segment[0], segment[1])
                " $loadA |  sta  ${hexVal(segment[3])} |  lda  #0 |  sta  ${hexValPlusOne(segment[3])}"
            },
            // mem uword = ubytearray[mem (u)bute]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2UWORD, Opcode.POP_MEM_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2UWORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${hexVal(segment[3])}
                lda  #0
                sta  ${hexValPlusOne(segment[3])}
                """
            },
            // mem uword = (u)wordarray[indexvalue]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_MEM_WORD)) { segment ->
                val index = segment[0].arg!!.integerValue()*2
                """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+1+$index
                sta  ${hexVal(segment[2])}
                sty  ${hexValPlusOne(segment[2])}
                """
            },
            // mem uword = (u)wordarray[index var]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_MEM_WORD)) { segment ->
                val loadAY = loadAYFromWordIndexedByVar(segment[0], segment[1])
                """
                $loadAY
                sta  ${hexVal(segment[2])}
                sty  ${hexValPlusOne(segment[2])}
                """
            },
            // mem uword = (u)wordarray[mem index]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_MEM_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                asl  a
                tay
                lda  ${segment[1].callLabel},y
                sta  ${hexVal(segment[2])}
                lda  ${segment[1].callLabel}+1,y
                sta  ${hexValPlusOne(segment[2])}
                """
            },
            // word var = bytevar (sign extended)
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.B2WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${segment[2].callLabel}
                ${signExtendA(segment[2].callLabel + "+1")}
                """
            },
            // mem word = bytevar (sign extended)
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.B2WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${hexVal(segment[2])}
                ${signExtendA(hexValPlusOne(segment[2]))}
                """
            },
            // mem word = mem byte (sign extended)
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.B2WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${hexVal(segment[2])}
                ${signExtendA(hexValPlusOne(segment[2]))}
                """
            },
            // var = membyte (sign extended)
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.B2WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel}
                ${signExtendA(segment[2].callLabel + "+1")}
                """
            },
            // var = bytearray[index_byte]  (sign extended)
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2WORD, Opcode.POP_VAR_WORD)) { segment ->
                val index = hexVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${segment[3].callLabel}
                ${signExtendA(segment[3].callLabel + "+1")}
                """
            },
            // var = bytearray[index var]  (sign extended)
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2WORD, Opcode.POP_VAR_WORD)) { segment ->
                val loadByteA = loadAFromIndexedByVar(segment[0], segment[1])
                """
                $loadByteA
                sta  ${segment[3].callLabel}
                ${signExtendA(segment[3].callLabel + "+1")}
                """
            },
            // var = bytearray[mem (u)byte]  (sign extended)
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2WORD, Opcode.POP_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel}
                ${signExtendA(segment[3].callLabel + "+1")}
                """
            },

            // ----------- assignment to UWORD ARRAY ----------------
            // uwordarray[index] = (u)word value
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = segment[1].arg!!.integerValue()*2
                val value = hexVal(segment[0])
                """
                lda  #<$value
                ldy  #>$value
                sta  ${segment[2].callLabel}+$index
                sty  ${segment[2].callLabel}+${index+1}
                """
            },
            // uwordarray[index mem] = (u)word value
            AsmPattern(
                    listOf(Opcode.PUSH_WORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_WORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val value = hexVal(segment[0])
                """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  #<$value
                sta  ${segment[2].callLabel},y
                lda  #>$value
                sta  ${segment[2].callLabel}+1,y
                """
            },
            // uwordarray[index mem] = mem (u)word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel},y
                lda  ${hexValPlusOne(segment[0])}
                sta  ${segment[2].callLabel}+1,y
                """
            },
            // uwordarray[index mem] = (u)word var
            AsmPattern(
                    listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  ${segment[0].callLabel}
                sta  ${segment[2].callLabel},y
                lda  ${segment[0].callLabel}+1
                sta  ${segment[2].callLabel}+1,y
                """
            },
            // uwordarray[index mem] = address-of var
            AsmPattern(
                    listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  #<${segment[0].callLabel}
                sta  ${segment[2].callLabel},y
                lda  #>${segment[0].callLabel}
                sta  ${segment[2].callLabel}+1,y
                """
            },
            // uwordarray[index] = ubytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.UB2UWORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = segment[2].arg!!.integerValue()*2
                when(segment[0].callLabel) {
                    "A" -> " sta  ${segment[3].callLabel}+$index |  lda  #0 |  sta  ${segment[3].callLabel}+${index+1}"
                    "X" -> " stx  ${segment[3].callLabel}+$index |  lda  #0 |  sta  ${segment[3].callLabel}+${index+1}"
                    "Y" -> " sty  ${segment[3].callLabel}+$index |  lda  #0 |  sta  ${segment[3].callLabel}+${index+1}"
                    else -> " lda  ${segment[0].callLabel} |  sta  ${segment[3].callLabel}+$index |  lda  #0 |  sta  ${segment[3].callLabel}+${index+1}"
                }
            },
            // wordarray[index] = bytevar  (extend sign)
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.B2WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = segment[2].arg!!.integerValue()*2
                when(segment[0].callLabel) {
                    "A" ->
                        """
                        sta  ${segment[3].callLabel}+$index
                        ${signExtendA(segment[3].callLabel + "+${index+1}")}
                        """
                    "X" ->
                        """
                        txa
                        sta  ${segment[3].callLabel}+$index
                        ${signExtendA(segment[3].callLabel + "+${index+1}")}
                        """
                    "Y" ->
                        """
                        tya
                        sta  ${segment[3].callLabel}+$index
                        ${signExtendA(segment[3].callLabel + "+${index+1}")}
                        """
                    else ->
                        """
                        lda  ${segment[0].callLabel}
                        sta  ${segment[3].callLabel}+$index
                        ${signExtendA(segment[3].callLabel + "+${index+1}")}
                        """
                }
            },
            // wordarray[index mem] = bytevar  (extend sign)
            AsmPattern(
                    listOf(Opcode.PUSH_VAR_BYTE, Opcode.B2WORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_VAR_BYTE, Opcode.B2WORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                when(segment[0].callLabel) {
                    "A" ->
                        """
                        pha
                        sty  ${C64Zeropage.SCRATCH_B1}
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        pla
                        sta  ${segment[3].callLabel},y
                        ${signExtendA(segment[3].callLabel + "+1,y")}
                        """
                    "X" ->
                        """
                        stx  ${C64Zeropage.SCRATCH_B1}
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        lda  ${C64Zeropage.SCRATCH_B1}
                        sta  ${segment[3].callLabel},y
                        ${signExtendA(segment[3].callLabel + "+1,y")}
                        """
                    "Y" ->
                        """
                        sty  ${C64Zeropage.SCRATCH_B1}
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        lda  ${C64Zeropage.SCRATCH_B1}
                        sta  ${segment[3].callLabel},y
                        ${signExtendA(segment[3].callLabel + "+1,y")}
                        """
                    else ->
                        """
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        lda  ${segment[0].callLabel}
                        sta  ${segment[3].callLabel},y
                        ${signExtendA(segment[3].callLabel + "+1,y")}
                        """
                }
            },
            // wordarray[memory (u)byte] = ubyte mem
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.B2WORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_B, Opcode.B2WORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[2])}
                asl  a
                tay
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel},y
                ${signExtendA(segment[3].callLabel + "+1,y")}
                """
            },
            // wordarray[index] = mem byte  (extend sign)
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.B2WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = segment[2].arg!!.integerValue()*2
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel}+$index
                ${signExtendA(segment[3].callLabel + "+${index+1}")}
                """
            },
            // wordarray2[index2] = bytearray1[index1] (extend sign)
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index1 = intVal(segment[0])
                val index2 = segment[3].arg!!.integerValue()*2
                """
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[4].callLabel}+$index2
                ${signExtendA(segment[4].callLabel + "+${index2+1}")}
                """
            },
            // wordarray2[mem (u)byte] = bytearray1[index1] (extend sign)
            AsmPattern(
                    listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2WORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2WORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index1 = intVal(segment[0])
                """
                lda  ${hexVal(segment[3])}
                asl  a
                tay
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[4].callLabel},y
                ${signExtendA(segment[4].callLabel + "+1,y")}
                """
            },

            // wordarray[indexvar]  = byte  var (sign extended)
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.B2WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadValueOnStack = when(segment[0].callLabel) {
                    "A" -> " pha"
                    "X" -> " txa |  pha"
                    "Y" -> " tya |  pha"
                    else -> " lda  ${segment[0].callLabel} |  pha"
                }
                val loadIndexY = when(segment[2].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
                }
                """
                $loadValueOnStack
                $loadIndexY
                pla
                sta  ${segment[3].callLabel},y
                ${signExtendA(segment[3].callLabel + "+1,y")}
                """
            },
            // wordarray[indexvar]  = byte  mem (sign extended)
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.B2WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadIndexY = when(segment[2].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
                }
                """
                $loadIndexY
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel},y
                ${signExtendA(segment[3].callLabel + "+1,y")}
                """
            },
            // wordarray[index] = mem word
            AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = segment[1].arg!!.integerValue()*2
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel}+$index
                lda  ${hexValPlusOne(segment[0])}
                sta  ${segment[2].callLabel}+$index+1
                """
            },
            // wordarray2[indexvar] = bytearay[index] (sign extended)
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index = intVal(segment[0])
                val loadIndex2Y = when(segment[3].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[3].callLabel} |  asl  a |  tay"
                }
                """
                $loadIndex2Y
                lda  ${segment[1].callLabel}+$index
                sta  ${segment[4].callLabel},y
                lda  ${segment[1].callLabel}+$index+1
                sta  ${segment[4].callLabel}+1,y
                """
            },
            // uwordarray[mem (u)byte] = mem word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel},y
                lda  ${hexValPlusOne(segment[0])}
                sta  ${segment[2].callLabel}+1,y
                """
            },

            // uwordarray[index mem] = ubytevar
            AsmPattern(
                    listOf(Opcode.PUSH_VAR_BYTE, Opcode.UB2UWORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_VAR_BYTE, Opcode.UB2UWORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                when(segment[0].callLabel) {
                    "A" ->
                        """
                        pha
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        pla
                        sta  ${segment[3].callLabel},y
                        lda  #0
                        sta  ${segment[3].callLabel}+1,y
                        """
                    "X" ->
                        """
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        txa
                        sta  ${segment[3].callLabel},y
                        lda  #0
                        sta  ${segment[3].callLabel}+1,y
                        """
                    "Y" ->
                        """
                        lda  ${hexVal(segment[2])}
                        asl  a
                        stx  ${C64Zeropage.SCRATCH_B1}
                        tax
                        tya
                        sta  ${segment[3].callLabel},x
                        lda  #0
                        sta  ${segment[3].callLabel}+1,x
                        ldx  ${C64Zeropage.SCRATCH_B1}
                        """
                    else ->
                        """
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        lda  ${segment[0].callLabel}
                        sta  ${segment[3].callLabel},y
                        lda  #0
                        sta  ${segment[3].callLabel}+1,y
                        """
                }
            },
            // uwordarray[index mem] = ubyte mem
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UB, Opcode.UB2UWORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.UB2UWORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[2])}
                asl  a
                tay
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel},y
                lda  #0
                sta  ${segment[3].callLabel}+1,y
                """
            },
            // uwordarray[index] = mem ubyte
            AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.UB2UWORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = segment[2].arg!!.integerValue()*2
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel}+$index
                lda  #0
                sta  ${segment[3].callLabel}+${index+1}
                """
            },
            // uwordarray[index] = (u)wordvar
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = segment[1].arg!!.integerValue()*2
                " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel}+$index |  lda  ${segment[0].callLabel}+1 |  sta  ${segment[2].callLabel}+${index+1}"
            },
            // uwordarray[index] = address-of var
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = segment[1].arg!!.integerValue()*2
                " lda  #<${segment[0].callLabel} |  sta  ${segment[2].callLabel}+$index |  lda  #>${segment[0].callLabel} |  sta  ${segment[2].callLabel}+${index+1}"
            },
            // uwordarray[index] = mem uword
            AsmPattern(listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = segment[1].arg!!.integerValue()*2
                """
                lda  ${hexVal(segment[0])}
                ldy  ${hexValPlusOne(segment[0])}
                sta  ${segment[2].callLabel}+$index
                sty  ${segment[2].callLabel}+${index+1}
                """
            },
            // uwordarray2[index2] = ubytearray1[index1]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2UWORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index1 = intVal(segment[0])
                val index2 = segment[3].arg!!.integerValue()*2
                """
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[4].callLabel}+$index2
                lda  #0
                sta  ${segment[4].callLabel}+${index2+1}
                """
            },
            // uwordarray2[index2] = (u)wordarray1[index1]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index1 = intVal(segment[0])
                val index2 = segment[2].arg!!.integerValue()*2
                """
                lda  ${segment[1].callLabel}+$index1
                ldy  ${segment[1].callLabel}+${index1+1}
                sta  ${segment[3].callLabel}+$index2
                sta  ${segment[3].callLabel}+${index2+1}
                """
            },
            // uwordarray[indexvar] = (u)word value
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val value = hexVal(segment[0])
                val loadIndexY = when(segment[1].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[1].callLabel} |  asl  a |  tay"
                }
                " $loadIndexY |  lda  #<$value |  sta  ${segment[2].callLabel},y |  lda  #>$value |  sta  ${segment[2].callLabel}+1,y"
            },
            // uwordarray[indexvar]  = ubyte  var
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.UB2UWORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadValueOnStack = when(segment[0].callLabel) {
                    "A" -> " pha"
                    "X" -> " txa |  pha"
                    "Y" -> " tya |  pha"
                    else -> " lda  ${segment[0].callLabel} |  pha"
                }
                val loadIndexY = when(segment[2].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
                }
                " $loadValueOnStack | $loadIndexY |  pla |  sta  ${segment[3].callLabel},y |  lda  #0 |  sta  ${segment[3].callLabel}+1,y"
            },
            // uwordarray[indexvar]  = uword  var
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadIndexY = when(segment[1].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[1].callLabel} |  asl  a |  tay"
                }
                " $loadIndexY |  lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel},y |  lda  ${segment[0].callLabel}+1 |  sta  ${segment[2].callLabel}+1,y"
            },
            // uwordarray[indexvar]  = address-of var
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadIndexY = when(segment[1].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[1].callLabel} |  asl  a |  tay"
                }
                " $loadIndexY |  lda  #<${segment[0].callLabel} |  sta  ${segment[2].callLabel},y |  lda  #>${segment[0].callLabel} |  sta  ${segment[2].callLabel}+1,y"
            },
            // uwordarray[indexvar]  = mem ubyte
            AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.UB2UWORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadIndexY = when(segment[2].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
                }
                " $loadIndexY |  lda  ${hexVal(segment[0])} |  sta  ${segment[3].callLabel},y |  lda  #0 |  sta  ${segment[3].callLabel}+1,y"
            },
            // uwordarray[indexvar]  = mem (u)word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadIndexY = when(segment[1].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[1].callLabel} |  asl  a |  tay"
                }
                " $loadIndexY |  lda  ${hexVal(segment[0])} |  sta  ${segment[2].callLabel},y |  lda  ${hexValPlusOne(segment[0])} |  sta  ${segment[2].callLabel}+1,y"
            },
            // uwordarray2[indexvar] = ubytearay[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2UWORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index = intVal(segment[0])
                val loadIndex2Y = when(segment[3].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[3].callLabel} |  asl  a |  tay"
                }
                " $loadIndex2Y |  lda  ${segment[1].callLabel}+$index |  sta  ${segment[4].callLabel},y |  lda  #0 |  sta  ${segment[4].callLabel}+1,y"
            },
            // uwordarray2[indexvar] = uwordarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index = segment[0].arg!!.integerValue()*2
                val loadIndex2Y = when(segment[2].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
                }
                " $loadIndex2Y |  lda  ${segment[1].callLabel}+$index |  sta  ${segment[3].callLabel},y |  lda  ${segment[1].callLabel}+${index+1} |  sta  ${segment[3].callLabel}+1,y"
            },
            // uwordarray2[index mem] = ubytearray1[index1]
            AsmPattern(
                    listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2UWORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2UWORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index1 = intVal(segment[0])
                """
                lda  ${hexVal(segment[3])}
                asl  a
                tay
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[4].callLabel},y
                lda  #0
                sta  ${segment[4].callLabel}+1,y
                """
            },
            // uwordarray2[index mem] = uwordarray1[index1]
            AsmPattern(
                    listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index1 = intVal(segment[0])
                """
                lda  ${hexVal(segment[2])}
                asl  a
                tay
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[3].callLabel},y
                lda  ${segment[1].callLabel}+${index1+1}
                sta  ${segment[3].callLabel}+1,y
                """
            },


            // ----------- assignment to FLOAT VARIABLE ----------------
            // floatvar  = ubytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.UB2FLOAT, Opcode.POP_VAR_FLOAT)) { segment->
                val loadByteA = when(segment[0].callLabel) {
                    "A" -> ""
                    "X" -> "txa"
                    "Y" -> "tya"
                    else -> "lda  ${segment[0].callLabel}"
                }
                """
                $loadByteA
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  prog8_lib.ub2float
                """
            },
            // floatvar = uwordvar
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.UW2FLOAT, Opcode.POP_VAR_FLOAT)) { segment->
                """
                lda  ${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${segment[0].callLabel}+1
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  prog8_lib.uw2float
                """
            },
            // floatvar = bytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.B2FLOAT, Opcode.POP_VAR_FLOAT)) { segment->
                val loadByteA = when(segment[0].callLabel) {
                    "A" -> ""
                    "X" -> "txa"
                    "Y" -> "tya"
                    else -> "lda  ${segment[0].callLabel}"
                }
                """
                $loadByteA
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  prog8_lib.b2float
                """
            },
            // floatvar = wordvar
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.W2FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${segment[0].callLabel}+1
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  prog8_lib.w2float
                """
            },
            // floatvar = float value
            AsmPattern(listOf(Opcode.PUSH_FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
                val floatConst = getFloatConst(segment[0].arg!!)
                """
                lda  #<$floatConst
                ldy  #>$floatConst
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[1].callLabel}
                ldy  #>${segment[1].callLabel}
                jsr  prog8_lib.copy_float
                """
            },
            // floatvar = float var
            AsmPattern(listOf(Opcode.PUSH_VAR_FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
                """
                lda  #<${segment[0].callLabel}
                ldy  #>${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[1].callLabel}
                ldy  #>${segment[1].callLabel}
                jsr  prog8_lib.copy_float
                """
            },
            // floatvar = mem float
            AsmPattern(listOf(Opcode.PUSH_MEM_FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
                """
                lda  #<${hexVal(segment[0])}
                ldy  #>${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[1].callLabel}
                ldy  #>${segment[1].callLabel}
                jsr  prog8_lib.copy_float
                """
            },
            // floatvar = mem byte
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.B2FLOAT, Opcode.POP_VAR_FLOAT)) { segment->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  prog8_lib.b2float
                """
            },
            // floatvar = mem ubyte
            AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.UB2FLOAT, Opcode.POP_VAR_FLOAT)) { segment->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  prog8_lib.ub2float
                """
            },
            // floatvar = mem word
            AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.W2FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  prog8_lib.w2float
                """
            },
            // floatvar = mem uword
            AsmPattern(listOf(Opcode.PUSH_MEM_UW, Opcode.UW2FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  prog8_lib.uw2float
                """
            },

            // floatvar = bytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2FLOAT, Opcode.POP_VAR_FLOAT)) { segment->
                val index = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[3].callLabel}
                ldy  #>${segment[3].callLabel}
                jsr  prog8_lib.b2float
                """
            },
            // floatvar = ubytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2FLOAT, Opcode.POP_VAR_FLOAT)) { segment->
                val index = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[3].callLabel}
                ldy  #>${segment[3].callLabel}
                jsr  prog8_lib.ub2float
                """
            },
            // floatvar = wordarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.W2FLOAT, Opcode.POP_VAR_FLOAT)) { segment->
                val index = segment[0].arg!!.integerValue()*2
                """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index+1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[3].callLabel}
                ldy  #>${segment[3].callLabel}
                jsr  prog8_lib.w2float
                """
            },
            // floatvar = uwordarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.UW2FLOAT, Opcode.POP_VAR_FLOAT)) { segment->
                val index = segment[0].arg!!.integerValue()*2
                """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index+1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[3].callLabel}
                ldy  #>${segment[3].callLabel}
                jsr  prog8_lib.uw2float
                """
            },
            // floatvar = floatarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
                val index = intVal(segment[0]) * Mflpt5.MemorySize
                """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index+1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  prog8_lib.copy_float
                """
            },

            // memfloat = float value
            AsmPattern(listOf(Opcode.PUSH_FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
                val floatConst = getFloatConst(segment[0].arg!!)
                """
                lda  #<$floatConst
                ldy  #>$floatConst
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[1])}
                ldy  #>${hexVal(segment[1])}
                jsr  prog8_lib.copy_float
                """
            },
            // memfloat = float var
            AsmPattern(listOf(Opcode.PUSH_VAR_FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
                """
                lda  #<${segment[0].callLabel}
                ldy  #>${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[1])}
                ldy  #>${hexVal(segment[1])}
                jsr  prog8_lib.copy_float
                """
            },
            // memfloat  = ubytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.UB2FLOAT, Opcode.POP_MEM_FLOAT)) { segment->
                val loadByteA = when(segment[0].callLabel) {
                    "A" -> ""
                    "X" -> "txa"
                    "Y" -> "tya"
                    else -> "lda  ${segment[0].callLabel}"
                }
                """
                $loadByteA
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  prog8_lib.ub2float
                """
            },
            // memfloat = uwordvar
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.UW2FLOAT, Opcode.POP_MEM_FLOAT)) { segment->
                """
                lda  ${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${segment[0].callLabel}+1
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  prog8_lib.uw2float
                """
            },
            // memfloat = bytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.B2FLOAT, Opcode.POP_MEM_FLOAT)) { segment->
                val loadByteA = when(segment[0].callLabel) {
                    "A" -> ""
                    "X" -> "txa"
                    "Y" -> "tya"
                    else -> "lda  ${segment[0].callLabel}"
                }
                """
                $loadByteA
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  prog8_lib.b2float
                """
            },
            // memfloat = wordvar
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.W2FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${segment[0].callLabel}+1
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  prog8_lib.w2float
                """
            },
            // memfloat = mem byte
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.B2FLOAT, Opcode.POP_MEM_FLOAT)) { segment->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  prog8_lib.b2float
                """
            },
            // memfloat = mem ubyte
            AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.UB2FLOAT, Opcode.POP_MEM_FLOAT)) { segment->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  prog8_lib.ub2float
                """
            },
            // memfloat = mem word
            AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.W2FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  prog8_lib.w2float
                """
            },
            // memfloat = mem uword
            AsmPattern(listOf(Opcode.PUSH_MEM_UW, Opcode.UW2FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  prog8_lib.uw2float
                """
            },
            // memfloat = mem float
            AsmPattern(listOf(Opcode.PUSH_MEM_FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
                """
                lda  #<${hexVal(segment[0])}
                ldy  #>${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[1])}
                ldy  #>${hexVal(segment[1])}
                jsr  prog8_lib.copy_float
                """
            },
            // memfloat = bytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.B2FLOAT, Opcode.POP_MEM_FLOAT)) { segment->
                val index = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[3])}
                ldy  #>${hexVal(segment[3])}
                jsr  prog8_lib.b2float
                """
            },
            // memfloat = ubytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.UB2FLOAT, Opcode.POP_MEM_FLOAT)) { segment->
                val index = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[3])}
                ldy  #>${hexVal(segment[3])}
                jsr  prog8_lib.ub2float
                """
            },
            // memfloat = wordarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.W2FLOAT, Opcode.POP_MEM_FLOAT)) { segment->
                val index = segment[0].arg!!.integerValue()*2
                """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index+1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[3])}
                ldy  #>${hexVal(segment[3])}
                jsr  prog8_lib.w2float
                """
            },
            // memfloat = uwordarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.UW2FLOAT, Opcode.POP_MEM_FLOAT)) { segment->
                val index = segment[0].arg!!.integerValue()*2
                """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index+1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[3])}
                ldy  #>${hexVal(segment[3])}
                jsr  prog8_lib.uw2float
                """
            },
            // memfloat = floatarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
                val index = intVal(segment[0]) * Mflpt5.MemorySize
                """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index+1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  prog8_lib.copy_float
                """
            },
            // floatarray[idxbyte] = float
            AsmPattern(listOf(Opcode.PUSH_FLOAT, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_FLOAT)) { segment ->
                val floatConst = getFloatConst(segment[0].arg!!)
                val index = intVal(segment[1]) * Mflpt5.MemorySize
                """
                lda  #<$floatConst
                ldy  #>$floatConst
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<(${segment[2].callLabel}+$index)
                ldy  #>(${segment[2].callLabel}+$index)
                jsr  prog8_lib.copy_float
                """
            },
            // floatarray[idxbyte] = floatvar
            AsmPattern(listOf(Opcode.PUSH_VAR_FLOAT, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_FLOAT)) { segment ->
                val index = intVal(segment[1]) * Mflpt5.MemorySize
                """
                lda  #<${segment[0].callLabel}
                ldy  #>${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<(${segment[2].callLabel}+$index)
                ldy  #>(${segment[2].callLabel}+$index)
                jsr  prog8_lib.copy_float
                """
            },
            //  floatarray[idxbyte] = memfloat
            AsmPattern(listOf(Opcode.PUSH_MEM_FLOAT, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_FLOAT)) { segment ->
                val index = intVal(segment[1]) * Mflpt5.MemorySize
                """
                lda  #<${hexVal(segment[0])}
                ldy  #>${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<(${segment[2].callLabel}+$index)
                ldy  #>(${segment[2].callLabel}+$index)
                jsr  prog8_lib.copy_float
                """
            },
            //  floatarray[idx2] = floatarray[idx1]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_FLOAT, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_FLOAT)) { segment ->
                val index1 = intVal(segment[0]) * Mflpt5.MemorySize
                val index2 = intVal(segment[2]) * Mflpt5.MemorySize
                """
                lda  #<(${segment[1].callLabel}+$index1)
                ldy  #>(${segment[1].callLabel}+$index1)
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<(${segment[3].callLabel}+$index2)
                ldy  #>(${segment[3].callLabel}+$index2)
                jsr  prog8_lib.copy_float
                """
            },

            // ---------- some special operations ------------------
            // var word = AX register pair
            AsmPattern(listOf(Opcode.PUSH_REGAX_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                sta  ${segment[1].callLabel}
                stx  ${segment[1].callLabel}+1
                """
            },
            // var word = AY register pair
            AsmPattern(listOf(Opcode.PUSH_REGAY_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                sta  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
            },
            // var word = XY register pair
            AsmPattern(listOf(Opcode.PUSH_REGXY_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                stx  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
            },
            // mem word = AX register pair
            AsmPattern(listOf(Opcode.PUSH_REGAX_WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                sta  ${hexVal(segment[1])}
                stx  ${hexValPlusOne(segment[1])}
                """
            },
            // mem word = AY register pair
            AsmPattern(listOf(Opcode.PUSH_REGAY_WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                sta  ${hexVal(segment[1])}
                sty  ${hexValPlusOne(segment[1])}
                """
            },
            // mem word = XY register pair
            AsmPattern(listOf(Opcode.PUSH_REGXY_WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                stx  ${hexVal(segment[1])}
                sty  ${hexValPlusOne(segment[1])}
                """
            },

            // AX register pair = word value
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_REGAX_WORD)) { segment ->
                val value = hexVal(segment[0])
                " lda  #<$value |  ldx  #>$value"
            },
            // AY register pair = word value
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_REGAY_WORD)) { segment ->
                val value = hexVal(segment[0])
                " lda  #<$value |  ldy  #>$value"
            },
            // XY register pair = word value
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_REGXY_WORD)) { segment ->
                val value = hexVal(segment[0])
                " ldx  #<$value |  ldy  #>$value"
            },
            // AX register pair = word var
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_REGAX_WORD)) { segment ->
                " lda  ${segment[0].callLabel} |  ldx  ${segment[0].callLabel}+1"
            },
            // AY register pair = word var
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_REGAY_WORD)) { segment ->
                " lda  ${segment[0].callLabel} |  ldy  ${segment[0].callLabel}+1"
            },
            // XY register pair = word var
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_REGXY_WORD)) { segment ->
                " ldx  ${segment[0].callLabel} |  ldy  ${segment[0].callLabel}+1"
            },
            // AX register pair = mem word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UW, Opcode.POP_REGAX_WORD),
                    listOf(Opcode.PUSH_MEM_W, Opcode.POP_REGAX_WORD)) { segment ->
                " lda  ${hexVal(segment[0])} |  ldx  ${hexValPlusOne(segment[0])}"
            },
            // AY register pair = mem word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UW, Opcode.POP_REGAY_WORD),
                    listOf(Opcode.PUSH_MEM_W, Opcode.POP_REGAY_WORD)) { segment ->
                " lda  ${hexVal(segment[0])} |  ldy  ${hexValPlusOne(segment[0])}"
            },
            // XY register pair = mem word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UW, Opcode.POP_REGXY_WORD),
                    listOf(Opcode.PUSH_MEM_W, Opcode.POP_REGXY_WORD)) { segment ->
                " ldx  ${hexVal(segment[0])} |  ldy  ${hexValPlusOne(segment[0])}"
            },


            // byte var = lsb(word var)
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.LSB, Opcode.POP_VAR_BYTE)) { segment ->
                " lda  #<${segment[0].callLabel} |  sta  ${segment[2].callLabel}"
            },
            // byte var = msb(word var)
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.MSB, Opcode.POP_VAR_BYTE)) { segment ->
                " lda  #>${segment[0].callLabel} |  sta  ${segment[2].callLabel}"
            },
            // push lsb(word var)
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.LSB)) { segment ->
                " lda  #<${segment[0].callLabel} |  sta  ${ESTACK_LO.toHex()},x |  dex "
            },
            // push msb(word var)
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.MSB)) { segment ->
                " lda  #>${segment[0].callLabel} |  sta  ${ESTACK_LO.toHex()},x |  dex "
            },

            // set a register pair to a certain memory address (of a variable)
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_REGAX_WORD)) { segment ->
                " lda  #<${segment[0].callLabel} |  ldx  #>${segment[0].callLabel} "
            },
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_REGAY_WORD)) { segment ->
                " lda  #<${segment[0].callLabel} |  ldy  #>${segment[0].callLabel} "
            },
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_REGXY_WORD)) { segment ->
                " ldx  #<${segment[0].callLabel} |  ldy  #>${segment[0].callLabel} "
            },
            // set a register pair to a certain memory address (of a literal string value)
            AsmPattern(listOf(Opcode.PUSH_ADDR_STR, Opcode.POP_REGAX_WORD)) { segment ->
                TODO("$segment")
            },
            AsmPattern(listOf(Opcode.PUSH_ADDR_STR, Opcode.POP_REGAY_WORD)) { segment ->
                TODO("$segment")
            },
            AsmPattern(listOf(Opcode.PUSH_ADDR_STR, Opcode.POP_REGXY_WORD)) { segment ->
                TODO("$segment")
            }
    )

}
