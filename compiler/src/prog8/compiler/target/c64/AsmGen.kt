package prog8.compiler.target.c64

// note: to put stuff on the stack, we use Absolute,X  addressing mode which is 3 bytes / 4 cycles
// possible space optimization is to use zeropage (indirect),Y  which is 2 bytes, but 5 cycles

import prog8.ast.antlr.escape
import prog8.ast.base.DataType
import prog8.ast.base.initvarsSubName
import prog8.ast.statements.ZeropageWish
import prog8.vm.RuntimeValue
import prog8.compiler.*
import prog8.compiler.intermediate.*
import java.io.File
import java.util.*
import kotlin.math.abs


class AssemblyError(msg: String) : RuntimeException(msg)



internal fun intVal(valueInstr: Instruction) = valueInstr.arg!!.integerValue()
internal fun hexVal(valueInstr: Instruction) = valueInstr.arg!!.integerValue().toHex()
internal fun hexValPlusOne(valueInstr: Instruction) = (valueInstr.arg!!.integerValue()+1).toHex()
internal fun getFloatConst(value: RuntimeValue): String =
        globalFloatConsts[value.numericValue().toDouble()]
                ?: throw AssemblyError("should have a global float const for number $value")

internal val globalFloatConsts = mutableMapOf<Double, String>()

internal fun signExtendA(into: String) =
        """
        ora  #$7f
        bmi  +
        lda  #0
+       sta  $into
        """

class AsmGen(private val options: CompilationOptions, private val program: IntermediateProgram,
             private val heap: HeapValues, private val zeropage: Zeropage) {
    private val assemblyLines = mutableListOf<String>()
    private lateinit var block: IntermediateProgram.ProgramBlock

    init {
        // Convert invalid label names (such as "<anon-1>") to something that's allowed.
        val newblocks = mutableListOf<IntermediateProgram.ProgramBlock>()
        for(block in program.blocks) {
            val newvars = block.variables.map { Triple(symname(it.first, block), it.second, it.third) }.toMutableList()
            val newvarsZeropaged = block.variablesMarkedForZeropage.map{symname(it, block)}.toMutableSet()
            val newlabels = block.labels.map { symname(it.key, block) to it.value}.toMap().toMutableMap()
            val newinstructions = block.instructions.asSequence().map {
                when {
                    it is LabelInstr -> LabelInstr(symname(it.name, block), it.asmProc)
                    it.opcode == Opcode.INLINE_ASSEMBLY -> it
                    else ->
                        Instruction(it.opcode, it.arg, it.arg2,
                            callLabel = if (it.callLabel != null) symname(it.callLabel, block) else null,
                            callLabel2 = if (it.callLabel2 != null) symname(it.callLabel2, block) else null)
                }
            }.toMutableList()
            val newMempointers = block.memoryPointers.map { symname(it.key, block) to it.value }.toMap().toMutableMap()
            val newblock = IntermediateProgram.ProgramBlock(
                    block.name,
                    block.address,
                    newinstructions,
                    newvars,
                    newMempointers,
                    newlabels,
                    force_output = block.force_output)
            newblock.variablesMarkedForZeropage.clear()
            newblock.variablesMarkedForZeropage.addAll(newvarsZeropaged)
            newblocks.add(newblock)
        }
        program.blocks.clear()
        program.blocks.addAll(newblocks)

        val newAllocatedZp = program.allocatedZeropageVariables.map { symname(it.key, null) to it.value}
        program.allocatedZeropageVariables.clear()
        program.allocatedZeropageVariables.putAll(newAllocatedZp)

        // make a list of all const floats that are used
        for(block in program.blocks) {
            for(ins in block.instructions.filter{it.arg?.type== DataType.FLOAT}) {
                val float = ins.arg!!.numericValue().toDouble()
                if(float !in globalFloatConsts)
                    globalFloatConsts[float] = "prog8_const_float_${globalFloatConsts.size}"
            }
        }
    }

    fun compileToAssembly(optimize: Boolean): AssemblyProgram {
        println("Generating assembly code from intermediate code... ")

        assemblyLines.clear()
        header()
        for(b in program.blocks)
            block2asm(b)

        if(optimize) {
            var optimizationsDone = 1
            while (optimizationsDone > 0) {
                optimizationsDone = optimizeAssembly(assemblyLines)
            }
        }

        File("${program.name}.asm").printWriter().use {
            for (line in assemblyLines) { it.println(line) }
        }

        return AssemblyProgram(program.name)
    }

    private fun out(str: String, splitlines: Boolean=true) {
        if(splitlines) {
            for (line in str.split('\n')) {
                val trimmed = if (line.startsWith(' ')) "\t" + line.trim() else line.trim()
                // trimmed = trimmed.replace(Regex("^\\+\\s+"), "+\t")  // sanitize local label indentation
                assemblyLines.add(trimmed)
            }
        } else assemblyLines.add(str)
    }


    // convert a fully scoped name (defined in the given block) to a valid assembly symbol name
    private fun symname(scoped: String, block: IntermediateProgram.ProgramBlock?): String {
        if(' ' in scoped)
            return scoped
        val blockLocal: Boolean
        var name = if (block!=null && scoped.startsWith("${block.name}.")) {
            blockLocal = true
            scoped.substring(block.name.length+1)
        }
        else {
            blockLocal = false
            scoped
        }
        name = name.replace("<", "prog8_").replace(">", "")     // take care of the autogenerated invalid (anon) label names
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

    private fun makeFloatFill(flt: MachineDefinition.Mflpt5): String {
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
            program.loadAddress = if(options.launcher==LauncherType.BASIC)
                MachineDefinition.BASIC_LOAD_ADDRESS else MachineDefinition.RAW_LOAD_ADDRESS

        when {
            options.launcher == LauncherType.BASIC -> {
                if (program.loadAddress != 0x0801)
                    throw AssemblyError("BASIC output must have load address $0801")
                out("; ---- basic program with sys call ----")
                out("* = ${program.loadAddress.toHex()}")
                val year = Calendar.getInstance().get(Calendar.YEAR)
                out("  .word  (+), $year")
                out("  .null  $9e, format(' %d ', _prog8_entrypoint), $3a, $8f, ' prog8 by idj'")
                out("+\t.word  0")
                out("_prog8_entrypoint\t; assembly code starts here\n")
                out("  jsr  prog8_lib.init_system")
            }
            options.output == OutputType.PRG -> {
                out("; ---- program without basic sys call ----")
                out("* = ${program.loadAddress.toHex()}\n")
                out("  jsr  prog8_lib.init_system")
            }
            options.output == OutputType.RAW -> {
                out("; ---- raw assembler program ----")
                out("* = ${program.loadAddress.toHex()}\n")
            }
        }

        if(zeropage.exitProgramStrategy!=Zeropage.ExitProgramStrategy.CLEAN_EXIT) {
            // disable shift-commodore charset switching and run/stop key
            out("  lda  #$80")
            out("  lda  #$80")
            out("  sta  657\t; disable charset switching")
            out("  lda  #239")
            out("  sta  808\t; disable run/stop key")
        }

        out("  ldx  #\$ff\t; init estack pointer")
        out("  ; initialize the variables in each block")
        for(block in program.blocks) {
            val initVarsLabel = block.instructions.firstOrNull { it is LabelInstr && it.name== initvarsSubName } as? LabelInstr
            if(initVarsLabel!=null)
                out("  jsr  ${block.name}.${initVarsLabel.name}")
        }
        out("  clc")
        when(zeropage.exitProgramStrategy) {
            Zeropage.ExitProgramStrategy.CLEAN_EXIT -> {
                out("  jmp  main.start\t; jump to program entrypoint")
            }
            Zeropage.ExitProgramStrategy.SYSTEM_RESET -> {
                out("  jsr  main.start\t; call program entrypoint")
                out("  jmp  (c64.RESET_VEC)\t; cold reset")
            }
        }
        out("")

        // the global list of all floating point constants for the whole program
        for(flt in globalFloatConsts) {
            val floatFill = makeFloatFill(MachineDefinition.Mflpt5.fromNumber(flt.key))
            out("${flt.value}\t.byte  $floatFill  ; float ${flt.key}")
        }
    }

    private fun block2asm(blk: IntermediateProgram.ProgramBlock) {
        block = blk
        out("\n; ---- block: '${block.name}' ----")
        if(!blk.force_output)
            out("${block.name}\t.proc\n")
        if(block.address!=null) {
            out(".cerror * > ${block.address?.toHex()}, 'block address overlaps by ', *-${block.address?.toHex()},' bytes'")
            out("* = ${block.address?.toHex()}")
        }

        // deal with zeropage variables
        for((varname, value, parameters) in blk.variables) {
            val sym = symname(blk.name+"."+varname, null)
            val zpVar = program.allocatedZeropageVariables[sym]
            if(zpVar==null) {
                // This var is not on the ZP yet. Attempt to move it there (if it's not a float, those take up too much space)
                if(parameters.zp != ZeropageWish.NOT_IN_ZEROPAGE &&
                        value.type in zeropage.allowedDatatypes
                        && value.type != DataType.FLOAT) {
                    try {
                        val address = zeropage.allocate(sym, value.type, null)
                        out("$varname = $address\t; auto zp ${value.type}")
                        // make sure we add the var to the set of zpvars for this block
                        blk.variablesMarkedForZeropage.add(varname)
                        program.allocatedZeropageVariables[sym] = Pair(address, value.type)
                    } catch (x: ZeropageDepletedError) {
                        // leave it as it is.
                    }
                }
            }
            else {
                // it was already allocated on the zp
                out("$varname = ${zpVar.first}\t; zp ${zpVar.second}")
            }
        }

        out("\n; memdefs and kernel subroutines")
        memdefs2asm(block)
        out("\n; non-zeropage variables")
        vardecls2asm(block)
        out("")

        val instructionPatternWindowSize = 8        // increase once patterns occur longer than this.
        var processed = 0

        for (ins in block.instructions.windowed(instructionPatternWindowSize, partialWindows = true)) {
            if (processed == 0) {
                processed = instr2asm(ins)
                if (processed == 0) {
                    // the instructions are not recognised yet and can't be translated into assembly
                    throw CompilerException("no asm translation found for instruction pattern: $ins")
                }
            }
            processed--
        }
        if(!blk.force_output)
            out("\n\t.pend\n")
    }

    private fun memdefs2asm(block: IntermediateProgram.ProgramBlock) {
        for(m in block.memoryPointers) {
            out("  ${m.key} = ${m.value.first.toHex()}")
        }
    }

    private fun vardecls2asm(block: IntermediateProgram.ProgramBlock) {
        val uniqueNames = block.variables.map { it.first }.toSet()
        if (uniqueNames.size != block.variables.size)
            throw AssemblyError("not all variables have unique names")

        // these are the non-zeropage variables.
        // first get all the flattened struct members, they MUST remain in order
        val (structMembers, normalVars) = block.variables.partition { it.third.memberOfStruct!=null }
        structMembers.forEach { vardecl2asm(it.first, it.second, it.third) }

        // leave outsort the other variables by type
        val sortedVars = normalVars.sortedBy { it.second.type }
        for ((varname, value, parameters) in sortedVars) {
            if(varname in block.variablesMarkedForZeropage)
                continue  // skip the ones that belong in the zero page
            vardecl2asm(varname, value, parameters)
        }
    }

    private fun vardecl2asm(varname: String, value: RuntimeValue, parameters: IntermediateProgram.VariableParameters) {
        when (value.type) {
            DataType.UBYTE -> out("$varname\t.byte  0")
            DataType.BYTE -> out("$varname\t.char  0")
            DataType.UWORD -> out("$varname\t.word  0")
            DataType.WORD -> out("$varname\t.sint  0")
            DataType.FLOAT -> out("$varname\t.byte  0,0,0,0,0  ; float")
            DataType.STR, DataType.STR_S -> {
                val rawStr = heap.get(value.heapId!!).str!!
                val bytes = encodeStr(rawStr, value.type).map { "$" + it.toString(16).padStart(2, '0') }
                out("$varname\t; ${value.type} \"${escape(rawStr).replace("\u0000", "<NULL>")}\"")
                for (chunk in bytes.chunked(16))
                    out("  .byte  " + chunk.joinToString())
            }
            DataType.ARRAY_UB -> {
                // unsigned integer byte arraysize
                val data = makeArrayFillDataUnsigned(value)
                if (data.size <= 16)
                    out("$varname\t.byte  ${data.joinToString()}")
                else {
                    out(varname)
                    for (chunk in data.chunked(16))
                        out("  .byte  " + chunk.joinToString())
                }
            }
            DataType.ARRAY_B -> {
                // signed integer byte arraysize
                val data = makeArrayFillDataSigned(value)
                if (data.size <= 16)
                    out("$varname\t.char  ${data.joinToString()}")
                else {
                    out(varname)
                    for (chunk in data.chunked(16))
                        out("  .char  " + chunk.joinToString())
                }
            }
            DataType.ARRAY_UW -> {
                // unsigned word arraysize
                val data = makeArrayFillDataUnsigned(value)
                if (data.size <= 16)
                    out("$varname\t.word  ${data.joinToString()}")
                else {
                    out(varname)
                    for (chunk in data.chunked(16))
                        out("  .word  " + chunk.joinToString())
                }
            }
            DataType.ARRAY_W -> {
                // signed word arraysize
                val data = makeArrayFillDataSigned(value)
                if (data.size <= 16)
                    out("$varname\t.sint  ${data.joinToString()}")
                else {
                    out(varname)
                    for (chunk in data.chunked(16))
                        out("  .sint  " + chunk.joinToString())
                }
            }
            DataType.ARRAY_F -> {
                // float arraysize
                val array = heap.get(value.heapId!!).doubleArray!!
                val floatFills = array.map { makeFloatFill(MachineDefinition.Mflpt5.fromNumber(it)) }
                out(varname)
                for(f in array.zip(floatFills))
                    out("  .byte  ${f.second}  ; float ${f.first}")
            }
            DataType.STRUCT -> throw AssemblyError("vars of type STRUCT should have been removed because flattened")
        }
    }


    private fun encodeStr(str: String, dt: DataType): List<Short> {
        return when(dt) {
            DataType.STR -> {
                val bytes = Petscii.encodePetscii(str, true)
                bytes.plus(0)
            }
            DataType.STR_S -> {
                val bytes = Petscii.encodeScreencode(str, true)
                bytes.plus(0)
            }
            else -> throw AssemblyError("invalid str type")
        }
    }

    private fun makeArrayFillDataUnsigned(value: RuntimeValue): List<String> {
        val array = heap.get(value.heapId!!).array!!
        return when {
            value.type== DataType.ARRAY_UB ->
                // byte array can never contain pointer-to types, so treat values as all integers
                array.map { "$"+it.integer!!.toString(16).padStart(2, '0') }
            value.type== DataType.ARRAY_UW -> array.map {
                when {
                    it.integer!=null -> "$"+it.integer.toString(16).padStart(2, '0')
                    it.addressOf!=null -> symname(it.addressOf.scopedname!!, block)
                    else -> throw AssemblyError("weird type in array")
                }
            }
            else -> throw AssemblyError("invalid arraysize type")
        }
    }

    private fun makeArrayFillDataSigned(value: RuntimeValue): List<String> {
        val array = heap.get(value.heapId!!).array!!
        // note: array of signed value can never contain pointer-to type, so simply accept values as being all integers
        return if (value.type == DataType.ARRAY_B || value.type == DataType.ARRAY_W) {
            array.map {
                if(it.integer!!>=0)
                    "$"+it.integer.toString(16).padStart(2, '0')
                else
                    "-$"+abs(it.integer).toString(16).padStart(2, '0')
            }
        }
        else throw AssemblyError("invalid arraysize type")
    }

    private fun instr2asm(ins: List<Instruction>): Int {
        // find best patterns (matching the most of the lines, then with the smallest weight)
        val fragments = findPatterns(ins).sortedByDescending { it.segmentSize }
        if(fragments.isEmpty()) {
            // we didn't find any matching patterns (complex multi-instruction fragments), try simple ones
            val firstIns = ins[0]
            val singleAsm = simpleInstr2Asm(firstIns, block)
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
                out(singleAsm.substring(8), false)
            else {
                val withNewlines = singleAsm.replace('|', '\n')
                out(withNewlines)
            }
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
        else if((opcodes[0]==Opcode.PUSH_BYTE && opcodes[1] in setOf(Opcode.INC_INDEXED_VAR_B, Opcode.INC_INDEXED_VAR_UB,
                        Opcode.INC_INDEXED_VAR_UW, Opcode.INC_INDEXED_VAR_W, Opcode.INC_INDEXED_VAR_FLOAT,
                        Opcode.DEC_INDEXED_VAR_B, Opcode.DEC_INDEXED_VAR_UB, Opcode.DEC_INDEXED_VAR_W,
                        Opcode.DEC_INDEXED_VAR_UW, Opcode.DEC_INDEXED_VAR_FLOAT))) {
            val fragment = sameConstantIndexedVarOperation(segment[1].callLabel!!, segment[0].arg!!.integerValue(), segment[1])
            if(fragment!=null) {
                fragment.segmentSize=2
                result.add(fragment)
            }
        }
        else if((opcodes[0]==Opcode.PUSH_VAR_BYTE && opcodes[1] in setOf(Opcode.INC_INDEXED_VAR_B, Opcode.INC_INDEXED_VAR_UB,
                        Opcode.INC_INDEXED_VAR_UW, Opcode.INC_INDEXED_VAR_W, Opcode.INC_INDEXED_VAR_FLOAT,
                        Opcode.DEC_INDEXED_VAR_B, Opcode.DEC_INDEXED_VAR_UB, Opcode.DEC_INDEXED_VAR_W,
                        Opcode.DEC_INDEXED_VAR_UW, Opcode.DEC_INDEXED_VAR_FLOAT))) {
            val fragment = sameIndexedVarOperation(segment[1].callLabel!!, segment[0].callLabel!!, segment[1])
            if(fragment!=null) {
                fragment.segmentSize=2
                result.add(fragment)
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

        // add any matching patterns from the big list
        for(pattern in patterns) {
            if(pattern.sequence.size > segment.size || (pattern.altSequence!=null && pattern.altSequence.size > segment.size))
                continue        //  don't accept patterns that don't fit
            val opcodesList = opcodes.subList(0, pattern.sequence.size)
            if(pattern.sequence == opcodesList) {
                val asm = pattern.asm(segment)
                if(asm!=null)
                    result.add(AsmFragment(asm, pattern.sequence.size))
            } else if(pattern.altSequence!=null) {
                val opcodesListAlt = opcodes.subList(0, pattern.altSequence.size)
                if(pattern.altSequence == opcodesListAlt) {
                    val asm = pattern.asm(segment)
                    if (asm != null)
                        result.add(AsmFragment(asm, pattern.sequence.size))
                }
            }
        }

        return result
    }

    private fun sameConstantIndexedVarOperation(variable: String, index: Int, ins: Instruction): AsmFragment? {
        // an in place operation that consists of a push-value / op / push-index-value / pop-into-indexed-var
        return when(ins.opcode) {
            Opcode.SHL_BYTE -> AsmFragment(" asl  $variable+$index", 8)
            Opcode.SHR_UBYTE -> AsmFragment(" lsr  $variable+$index", 8)
            Opcode.SHR_SBYTE -> AsmFragment(" lda  $variable+$index |  asl  a |  ror  $variable+$index")
            Opcode.SHL_WORD -> AsmFragment(" asl  $variable+${index*2+1} |  rol  $variable+${index*2}", 8)
            Opcode.SHR_UWORD -> AsmFragment(" lsr  $variable+${index*2+1} |  ror  $variable+${index*2}", 8)
            Opcode.SHR_SWORD -> AsmFragment(" lda  $variable+${index*2+1} |  asl  a |  ror  $variable+${index*2+1} |  ror  $variable+${index*2}", 8)
            Opcode.ROL_BYTE -> AsmFragment(" rol  $variable+$index", 8)
            Opcode.ROR_BYTE -> AsmFragment(" ror  $variable+$index", 8)
            Opcode.ROL_WORD -> AsmFragment(" rol  $variable+${index*2+1} |  rol  $variable+${index*2}", 8)
            Opcode.ROR_WORD -> AsmFragment(" ror  $variable+${index*2+1} |  ror  $variable+${index*2}", 8)
            Opcode.ROL2_BYTE -> AsmFragment(" lda  $variable+$index |  cmp  #\$80 |  rol  $variable+$index", 8)
            Opcode.ROR2_BYTE -> AsmFragment(" lda  $variable+$index |  lsr  a |  bcc  + |  ora  #\$80 |+ |  sta  $variable+$index", 10)
            Opcode.ROL2_WORD -> AsmFragment(" asl  $variable+${index*2+1} |  rol  $variable+${index*2} |  bcc  + |  inc  $variable+${index*2+1} |+",20)
            Opcode.ROR2_WORD -> AsmFragment(" lsr  $variable+${index*2+1} |  ror  $variable+${index*2} |  bcc  + |  lda  $variable+${index*2+1} |  ora  #\$80 |  sta  $variable+${index*2+1} |+", 30)
            Opcode.INC_INDEXED_VAR_B, Opcode.INC_INDEXED_VAR_UB -> AsmFragment(" inc  $variable+$index", 2)
            Opcode.DEC_INDEXED_VAR_B, Opcode.DEC_INDEXED_VAR_UB -> AsmFragment(" dec  $variable+$index", 5)
            Opcode.INC_INDEXED_VAR_W, Opcode.INC_INDEXED_VAR_UW -> AsmFragment(" inc  $variable+${index*2} |  bne  + |  inc  $variable+${index*2+1} |+")
            Opcode.DEC_INDEXED_VAR_W, Opcode.DEC_INDEXED_VAR_UW -> AsmFragment(" lda  $variable+${index*2} |  bne  + |  dec  $variable+${index*2+1} |+ |  dec  $variable+${index*2}")
            Opcode.INC_INDEXED_VAR_FLOAT -> AsmFragment(
                """
                lda  #<($variable+${index* MachineDefinition.Mflpt5.MemorySize})
                ldy  #>($variable+${index* MachineDefinition.Mflpt5.MemorySize})
                jsr  c64flt.inc_var_f
                """)
            Opcode.DEC_INDEXED_VAR_FLOAT -> AsmFragment(
                """
                lda  #<($variable+${index* MachineDefinition.Mflpt5.MemorySize})
                ldy  #>($variable+${index* MachineDefinition.Mflpt5.MemorySize})
                jsr  c64flt.dec_var_f
                """)

            else -> null
        }
    }

    private fun sameIndexedVarOperation(variable: String, indexVar: String, ins: Instruction): AsmFragment? {
        // an in place operation that consists of a push-value / op / push-index-var / pop-into-indexed-var
        val saveX = " stx  ${MachineDefinition.C64Zeropage.SCRATCH_B1} |"
        val restoreX = " | ldx  ${MachineDefinition.C64Zeropage.SCRATCH_B1}"
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
            Opcode.SHR_UBYTE -> AsmFragment(" txa |  $loadX  lsr  $variable,x |  tax", 10)
            Opcode.SHR_SBYTE -> AsmFragment("$saveX  $loadX  lda  $variable,x |  asl a |  ror  $variable,x  $restoreX", 10)
            Opcode.SHL_WORD -> AsmFragment("$saveX $loadXWord  asl  $variable,x |  rol  $variable+1,x  $restoreX", 10)
            Opcode.SHR_UWORD -> AsmFragment("$saveX $loadXWord  lsr  $variable+1,x |  ror  $variable,x  $restoreX", 10)
            Opcode.SHR_SWORD -> AsmFragment("$saveX $loadXWord  lda  $variable+1,x |  asl a |  ror  $variable+1,x |  ror  $variable,x  $restoreX", 10)
            Opcode.ROL_BYTE -> AsmFragment(" txa |  $loadX  rol  $variable,x |  tax", 10)
            Opcode.ROR_BYTE -> AsmFragment(" txa |  $loadX  ror  $variable,x |  tax", 10)
            Opcode.ROL_WORD -> AsmFragment("$saveX $loadXWord  rol  $variable,x |  rol  $variable+1,x  $restoreX", 10)
            Opcode.ROR_WORD -> AsmFragment("$saveX $loadXWord  ror  $variable+1,x |  ror  $variable,x  $restoreX", 10)
            Opcode.ROL2_BYTE -> AsmFragment("$saveX $loadX  lda  $variable,x |  cmp  #\$80 |  rol  $variable,x  $restoreX", 10)
            Opcode.ROR2_BYTE -> AsmFragment("$saveX $loadX  lda  $variable,x |  lsr  a |  bcc  + |  ora  #\$80 |+ |  sta  $variable,x  $restoreX", 10)
            Opcode.ROL2_WORD -> AsmFragment(" txa |  $loadXWord  asl  $variable,x |  rol  $variable+1,x |  bcc  + |  inc  $variable,x  |+  |  tax", 30)
            Opcode.ROR2_WORD -> AsmFragment("$saveX $loadXWord  lsr  $variable+1,x |  ror  $variable,x |  bcc  + |  lda  $variable+1,x |  ora  #\$80 |  sta  $variable+1,x |+  $restoreX", 30)
            Opcode.INC_INDEXED_VAR_B, Opcode.INC_INDEXED_VAR_UB -> AsmFragment(" txa |  $loadX  inc  $variable,x |  tax", 10)
            Opcode.DEC_INDEXED_VAR_B, Opcode.DEC_INDEXED_VAR_UB -> AsmFragment(" txa |  $loadX  dec  $variable,x |  tax", 10)
            Opcode.INC_INDEXED_VAR_W, Opcode.INC_INDEXED_VAR_UW -> AsmFragment("$saveX $loadXWord  inc  $variable,x |  bne  + |  inc  $variable+1,x  |+  $restoreX", 10)
            Opcode.DEC_INDEXED_VAR_W, Opcode.DEC_INDEXED_VAR_UW -> AsmFragment("$saveX $loadXWord  lda  $variable,x |  bne  + |  dec  $variable+1,x |+ |  dec  $variable,x  $restoreX", 10)
            Opcode.INC_INDEXED_VAR_FLOAT -> AsmFragment(" lda  #<$variable |  ldy  #>$variable |  $saveX   $loadX   jsr  c64flt.inc_indexed_var_f  $restoreX")
            Opcode.DEC_INDEXED_VAR_FLOAT -> AsmFragment(" lda  #<$variable |  ldy  #>$variable |  $saveX   $loadX   jsr  c64flt.dec_indexed_var_f  $restoreX")

            else -> null
        }
    }

    private fun sameMemOperation(address: Int, ins: Instruction): AsmFragment? {
        // an in place operation that consists of  push-mem / op / pop-mem
        val addr = address.toHex()
        val addrHi = (address+1).toHex()
        return when(ins.opcode) {
            Opcode.SHL_BYTE -> AsmFragment(" asl  $addr", 10)
            Opcode.SHR_UBYTE -> AsmFragment(" lsr  $addr", 10)
            Opcode.SHR_SBYTE -> AsmFragment(" lda  $addr |  asl  a |  ror  $addr", 10)
            Opcode.SHL_WORD -> AsmFragment(" asl  $addr |  rol  $addrHi", 10)
            Opcode.SHR_UWORD -> AsmFragment(" lsr  $addrHi |  ror  $addr", 10)
            Opcode.SHR_SWORD -> AsmFragment(" lda  $addrHi |  asl a |  ror  $addrHi |  ror  $addr", 10)
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
            Opcode.SHR_UBYTE -> {
                when (variable) {
                    "A" -> AsmFragment(" lsr  a", 10)
                    "X" -> AsmFragment(" txa |  lsr  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  lsr  a |  tay", 10)
                    else -> AsmFragment(" lsr  $variable", 10)
                }
            }
            Opcode.SHR_SBYTE -> {
                // arithmetic shift right (keep sign bit)
                when (variable) {
                    "A" -> AsmFragment(" cmp  #$80 |  ror  a", 10)
                    "X" -> AsmFragment(" txa |  cmp  #$80 |  ror  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  cmp  #$80 |  ror  a |  tay", 10)
                    else -> AsmFragment(" lda  $variable |  asl  a  | ror  $variable", 10)
                }
            }
            Opcode.SHL_WORD -> {
                AsmFragment(" asl  $variable |  rol  $variable+1", 10)
            }
            Opcode.SHR_UWORD -> {
                AsmFragment(" lsr  $variable+1 |  ror  $variable", 10)
            }
            Opcode.SHR_SWORD -> {
                // arithmetic shift right (keep sign bit)
                AsmFragment(" lda  $variable+1 |  asl  a |  ror  $variable+1 |  ror  $variable", 10)
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
                AsmFragment(" lsr  $variable+1 |  ror  $variable |  bcc  + |  lda  $variable+1 |  ora  #\$80 |  sta  $variable+1 |+", 30)
            }
            else -> null
        }
    }

    private class AsmFragment(val asm: String, var segmentSize: Int=0)
}
