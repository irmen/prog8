package prog8.compiler.intermediate

import prog8.ast.*
import prog8.compiler.CompilerException
import prog8.compiler.HeapValues
import java.io.PrintStream


class IntermediateProgram(val name: String, var loadAddress: Int, val heap: HeapValues) {

    class ProgramBlock(val scopedname: String,
                       val shortname: String,
                       var address: Int?,
                       val instructions: MutableList<Instruction> = mutableListOf(),
                       val variables: MutableMap<String, Value> = mutableMapOf(),
                       val memoryPointers: MutableMap<String, Pair<Int, DataType>> = mutableMapOf(),
                       val labels: MutableMap<String, Instruction> = mutableMapOf(),
                       val force_output: Boolean)
    {
        val numVariables: Int
            get() { return variables.size }
        val numInstructions: Int
            get() { return instructions.filter { it.opcode!= Opcode.LINE }.size }
    }

    val blocks = mutableListOf<ProgramBlock>()
    val memory = mutableMapOf<Int, List<Value>>()
    private lateinit var currentBlock: ProgramBlock

    val numVariables: Int
        get() = blocks.sumBy { it.numVariables }
    val numInstructions: Int
        get() = blocks.sumBy { it.numInstructions }

    fun optimize() {
        println("Optimizing stackVM code...")
        // remove nops (that are not a label)
        for (blk in blocks) {
            blk.instructions.removeIf { it.opcode== Opcode.NOP && it !is LabelInstr }
        }

        optimizeDataConversionAndUselessDiscards()
        optimizeVariableCopying()
        optimizeMultipleSequentialLineInstrs()
        optimizeCallReturnIntoJump()
        optimizeRestoreXYSaveXYIntoRestoreXY()
        // todo: optimize stackvm code more

        optimizeRemoveNops()    //  must be done as the last step
        optimizeMultipleSequentialLineInstrs()      // once more
        optimizeRemoveNops()    // once more
    }

    private fun optimizeRemoveNops() {
        // remove nops (that are not a label)
        for (blk in blocks)
            blk.instructions.removeIf { it.opcode== Opcode.NOP && it !is LabelInstr }
    }

    private fun optimizeRestoreXYSaveXYIntoRestoreXY() {
        // replace rrestorex/y+rsavex/y combo by only rrestorex/y
        for(blk in blocks) {
            val instructionsToReplace = mutableMapOf<Int, Instruction>()

            blk.instructions.asSequence().withIndex().filter {it.value.opcode!=Opcode.LINE}.windowed(2).toList().forEach {
                if(it[0].value.opcode==Opcode.RRESTOREX && it[1].value.opcode==Opcode.RSAVEX) {
                    instructionsToReplace[it[1].index] = Instruction(Opcode.NOP)
                }
                else if(it[0].value.opcode==Opcode.RRESTOREY && it[1].value.opcode==Opcode.RSAVEY) {
                    instructionsToReplace[it[1].index] = Instruction(Opcode.NOP)
                }
            }

            for (rins in instructionsToReplace) {
                blk.instructions[rins.key] = rins.value
            }
        }
    }

    private fun optimizeCallReturnIntoJump() {
        // replaces call X followed by return, by jump X
        for(blk in blocks) {
            val instructionsToReplace = mutableMapOf<Int, Instruction>()

            blk.instructions.asSequence().withIndex().filter {it.value.opcode!=Opcode.LINE}.windowed(2).toList().forEach {
                if(it[0].value.opcode==Opcode.CALL && it[1].value.opcode==Opcode.RETURN) {
                    instructionsToReplace[it[1].index] = Instruction(Opcode.JUMP, callLabel = it[0].value.callLabel)
                    instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
                }
            }

            for (rins in instructionsToReplace) {
                blk.instructions[rins.key] = rins.value
            }
        }
    }

    private fun optimizeMultipleSequentialLineInstrs() {
        for(blk in blocks) {
            val instructionsToReplace = mutableMapOf<Int, Instruction>()

            blk.instructions.asSequence().withIndex().windowed(2).toList().forEach {
                if (it[0].value.opcode == Opcode.LINE && it[1].value.opcode == Opcode.LINE)
                    instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
            }

            for (rins in instructionsToReplace) {
                blk.instructions[rins.key] = rins.value
            }
        }
    }

    private fun optimizeVariableCopying() {
        for(blk in blocks) {

            val instructionsToReplace = mutableMapOf<Int, Instruction>()

            blk.instructions.asSequence().withIndex().windowed(2).toList().forEach {
                when (it[0].value.opcode) {
                    Opcode.PUSH_VAR_BYTE ->
                        if (it[1].value.opcode == Opcode.POP_VAR_BYTE) {
                            if (it[0].value.callLabel == it[1].value.callLabel) {
                                instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
                                instructionsToReplace[it[1].index] = Instruction(Opcode.NOP)
                            }
                        }
                    Opcode.PUSH_VAR_WORD ->
                        if (it[1].value.opcode == Opcode.POP_VAR_WORD) {
                            if (it[0].value.callLabel == it[1].value.callLabel) {
                                instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
                                instructionsToReplace[it[1].index] = Instruction(Opcode.NOP)
                            }
                        }
                    Opcode.PUSH_VAR_FLOAT ->
                        if (it[1].value.opcode == Opcode.POP_VAR_FLOAT) {
                            if (it[0].value.callLabel == it[1].value.callLabel) {
                                instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
                                instructionsToReplace[it[1].index] = Instruction(Opcode.NOP)
                            }
                        }
                    Opcode.PUSH_MEM_B, Opcode.PUSH_MEM_UB ->
                        if(it[1].value.opcode == Opcode.POP_MEM_BYTE) {
                            if(it[0].value.arg == it[1].value.arg) {
                                instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
                                instructionsToReplace[it[1].index] = Instruction(Opcode.NOP)
                            }
                        }
                    Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_UW ->
                        if(it[1].value.opcode == Opcode.POP_MEM_WORD) {
                            if(it[0].value.arg == it[1].value.arg) {
                                instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
                                instructionsToReplace[it[1].index] = Instruction(Opcode.NOP)
                            }
                        }
                    Opcode.PUSH_MEM_FLOAT ->
                        if(it[1].value.opcode == Opcode.POP_MEM_FLOAT) {
                            if(it[0].value.arg == it[1].value.arg) {
                                instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
                                instructionsToReplace[it[1].index] = Instruction(Opcode.NOP)
                            }
                        }
                    else -> {}
                }
            }

            for (rins in instructionsToReplace) {
                blk.instructions[rins.key] = rins.value
            }
        }
    }

    private fun optimizeDataConversionAndUselessDiscards() {
        // - push value followed by a data type conversion -> push the value in the correct type and remove the conversion
        // - push something followed by a discard -> remove both
        val instructionsToReplace = mutableMapOf<Int, Instruction>()

        fun optimizeDiscardAfterPush(index0: Int, index1: Int, ins1: Instruction) {
            if (ins1.opcode == Opcode.DISCARD_FLOAT || ins1.opcode == Opcode.DISCARD_WORD || ins1.opcode == Opcode.DISCARD_BYTE) {
                instructionsToReplace[index0] = Instruction(Opcode.NOP)
                instructionsToReplace[index1] = Instruction(Opcode.NOP)
            }
        }

        fun optimizeFloatConversion(index0: Int, index1: Int, ins1: Instruction) {
            when (ins1.opcode) {
                Opcode.DISCARD_FLOAT -> {
                    instructionsToReplace[index0] = Instruction(Opcode.NOP)
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.DISCARD_BYTE, Opcode.DISCARD_WORD -> throw CompilerException("invalid discard type following a float")
                else -> throw CompilerException("invalid conversion opcode ${ins1.opcode} following a float")
            }
        }

        fun optimizeWordConversion(index0: Int, ins0: Instruction, index1: Int, ins1: Instruction) {
            when (ins1.opcode) {
                Opcode.CAST_B_TO_W, Opcode.CAST_B_TO_UW -> TODO("cast byte to (u)word")
                Opcode.CAST_UB_TO_W, Opcode.CAST_UB_TO_UW -> TODO("cast ubyte to (u)word")
                Opcode.CAST_W_TO_B, Opcode.CAST_UW_TO_B -> TODO("cast (u)word to byte")
                Opcode.CAST_W_TO_UB, Opcode.CAST_UW_TO_UB -> {
                    val ins = Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, ins0.arg!!.integerValue() and 255))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.MSB -> {
                    val ins = Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, ins0.arg!!.integerValue() ushr 8 and 255))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_W_TO_F, Opcode.CAST_UW_TO_F -> {
                    val ins = Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, ins0.arg!!.integerValue().toDouble()))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_UW_TO_W -> {
                    val cv = ins0.arg!!.cast(DataType.WORD)
                    instructionsToReplace[index0] = Instruction(Opcode.PUSH_WORD, cv)
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_W_TO_UW -> {
                    val cv = ins0.arg!!.cast(DataType.UWORD)
                    instructionsToReplace[index0] = Instruction(Opcode.PUSH_WORD, cv)
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.DISCARD_WORD -> {
                    instructionsToReplace[index0] = Instruction(Opcode.NOP)
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.DISCARD_BYTE, Opcode.DISCARD_FLOAT -> throw CompilerException("invalid discard type following a byte")
                else -> throw CompilerException("invalid conversion opcode ${ins1.opcode} following a word")
            }
        }

        fun optimizeByteConversion(index0: Int, ins0: Instruction, index1: Int, ins1: Instruction) {
            when (ins1.opcode) {
                Opcode.CAST_B_TO_UB, Opcode.CAST_UB_TO_B,
                Opcode.CAST_W_TO_B, Opcode.CAST_W_TO_UB,
                Opcode.CAST_UW_TO_B, Opcode.CAST_UW_TO_UB -> instructionsToReplace[index1] = Instruction(Opcode.NOP)
                Opcode.MSB -> throw CompilerException("msb of a byte")
                Opcode.CAST_UB_TO_UW -> {
                    val ins = Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, ins0.arg!!.integerValue()))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_B_TO_W -> {
                    val ins = Instruction(Opcode.PUSH_WORD, Value(DataType.WORD, ins0.arg!!.integerValue()))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_B_TO_UW, Opcode.CAST_UB_TO_W -> TODO("cast byte to (u)word")
                Opcode.CAST_B_TO_F, Opcode.CAST_UB_TO_F-> {
                    val ins = Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, ins0.arg!!.integerValue().toDouble()))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_W_TO_F, Opcode.CAST_UW_TO_F-> throw CompilerException("invalid conversion following a byte")
                Opcode.DISCARD_BYTE -> {
                    instructionsToReplace[index0] = Instruction(Opcode.NOP)
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.DISCARD_WORD, Opcode.DISCARD_FLOAT -> throw CompilerException("invalid discard type following a byte")
                else -> throw CompilerException("invalid conversion opcode ${ins1.opcode}")
            }
        }

        for(blk in blocks) {
            instructionsToReplace.clear()

            val typeConversionOpcodes = setOf(
                    Opcode.MSB,
                    Opcode.CAST_UB_TO_B,
                    Opcode.CAST_UB_TO_UW,
                    Opcode.CAST_UB_TO_W,
                    Opcode.CAST_UB_TO_F,
                    Opcode.CAST_B_TO_UB,
                    Opcode.CAST_B_TO_UW,
                    Opcode.CAST_B_TO_W,
                    Opcode.CAST_B_TO_F,
                    Opcode.CAST_UW_TO_UB,
                    Opcode.CAST_UW_TO_B,
                    Opcode.CAST_UW_TO_W,
                    Opcode.CAST_UW_TO_F,
                    Opcode.CAST_W_TO_UB,
                    Opcode.CAST_W_TO_B,
                    Opcode.CAST_W_TO_UW,
                    Opcode.CAST_W_TO_F,
                    Opcode.CAST_F_TO_UB,
                    Opcode.CAST_F_TO_B,
                    Opcode.CAST_F_TO_UW,
                    Opcode.CAST_F_TO_W,
                    Opcode.DISCARD_BYTE,
                    Opcode.DISCARD_WORD,
                    Opcode.DISCARD_FLOAT
            )
            blk.instructions.asSequence().withIndex().windowed(2).toList().forEach {
                if (it[1].value.opcode in typeConversionOpcodes) {
                    when (it[0].value.opcode) {
                        Opcode.PUSH_BYTE -> optimizeByteConversion(it[0].index, it[0].value, it[1].index, it[1].value)
                        Opcode.PUSH_WORD -> optimizeWordConversion(it[0].index, it[0].value, it[1].index, it[1].value)
                        Opcode.PUSH_FLOAT -> optimizeFloatConversion(it[0].index, it[1].index, it[1].value)
                        Opcode.PUSH_VAR_FLOAT,
                        Opcode.PUSH_VAR_WORD,
                        Opcode.PUSH_VAR_BYTE,
                        Opcode.PUSH_MEM_B, Opcode.PUSH_MEM_UB,
                        Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_UW,
                        Opcode.PUSH_MEM_FLOAT -> optimizeDiscardAfterPush(it[0].index, it[1].index, it[1].value)
                        else -> {
                        }
                    }
                }
            }

            for (rins in instructionsToReplace) {
                blk.instructions[rins.key] = rins.value
            }
        }
    }

    fun variable(scopedname: String, decl: VarDecl) {
        when(decl.type) {
            VarDeclType.VAR -> {
                val value = when(decl.datatype) {
                    DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD, DataType.FLOAT -> Value(decl.datatype, (decl.value as LiteralValue).asNumericValue!!)
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                        val litval = (decl.value as LiteralValue)
                        if(litval.heapId==null)
                            throw CompilerException("string should already be in the heap")
                        Value(decl.datatype, litval.heapId)
                    }
                    DataType.ARRAY_B, DataType.ARRAY_W,
                    DataType.ARRAY_UB, DataType.ARRAY_UW, DataType.ARRAY_F -> {
                        val litval = (decl.value as LiteralValue)
                        if(litval.heapId==null)
                            throw CompilerException("array should already be in the heap")
                        Value(decl.datatype, litval.heapId)
                    }
                }
                currentBlock.variables[scopedname] = value
            }
            VarDeclType.MEMORY -> {
                // note that constants are all folded away, but assembly code may still refer to them
                val lv = decl.value as LiteralValue
                if(lv.type!=DataType.UWORD && lv.type!=DataType.UBYTE)
                    throw CompilerException("expected integer memory address $lv")
                currentBlock.memoryPointers[scopedname] = Pair(lv.asIntegerValue!!, decl.datatype)
            }
            VarDeclType.CONST -> {
                // note that constants are all folded away, but assembly code may still refer to them (if their integers)
                // floating point constants are not generated at all!!
                val lv = decl.value as LiteralValue
                if(lv.type in IntegerDatatypes)
                    currentBlock.memoryPointers[scopedname] = Pair(lv.asIntegerValue!!, decl.datatype)
            }
        }
    }

    fun instr(opcode: Opcode, arg: Value? = null, callLabel: String? = null) {
        currentBlock.instructions.add(Instruction(opcode, arg,  callLabel = callLabel))
    }

    fun label(labelname: String) {
        val instr = LabelInstr(labelname)
        currentBlock.instructions.add(instr)
        currentBlock.labels[labelname] = instr
    }

    fun line(position: Position) {
        currentBlock.instructions.add(Instruction(Opcode.LINE, callLabel = "${position.line} ${position.file}"))
    }

    fun removeLastInstruction() {
        currentBlock.instructions.removeAt(currentBlock.instructions.lastIndex)
    }

    fun memoryPointer(name: String, address: Int, datatype: DataType) {
        currentBlock.memoryPointers[name] = Pair(address, datatype)
    }

    fun newBlock(scopedname: String, shortname: String, address: Int?, options: Set<String>) {
        currentBlock = ProgramBlock(scopedname, shortname, address, force_output="force_output" in options)
        blocks.add(currentBlock)
    }

    fun writeCode(out: PrintStream, embeddedLabels: Boolean=true) {
        out.println("; stackVM program code for '$name'")
        out.println("%memory")
        if(memory.isNotEmpty()) {
            TODO("output initial memory values")
        }
        out.println("%end_memory")
        out.println("%heap")
        heap.allEntries().forEach {
            when {
                it.value.str!=null ->
                    out.println("${it.index}  ${it.value.type.toString().toLowerCase()}  \"${escape(it.value.str!!)}\"")
                it.value.array!=null ->
                    out.println("${it.index}  ${it.value.type.toString().toLowerCase()}  ${it.value.array!!.toList()}")
                it.value.doubleArray!=null ->
                    out.println("${it.index}  ${it.value.type.toString().toLowerCase()}  ${it.value.doubleArray!!.toList()}")
                else -> throw CompilerException("invalid heap entry $it")
            }
        }
        out.println("%end_heap")
        for(blk in blocks) {
            out.println("\n%block ${blk.scopedname} ${blk.address?.toString(16) ?: ""}")

            out.println("%variables")
            for(variable in blk.variables) {
                val valuestr = variable.value.toString()
                out.println("${variable.key}  ${variable.value.type.toString().toLowerCase()}  $valuestr")
            }
            out.println("%end_variables")
            out.println("%memorypointers")
            for(iconst in blk.memoryPointers) {
                out.println("${iconst.key}  ${iconst.value.second.toString().toLowerCase()}  uw:${iconst.value.first.toString(16)}")
            }
            out.println("%end_memorypointers")
            out.println("%instructions")
            val labels = blk.labels.entries.associateBy({it.value}) {it.key}
            for(instr in blk.instructions) {
                if(!embeddedLabels) {
                    val label = labels[instr]
                    if (label != null)
                        out.println("$label:")
                } else {
                    out.println(instr)
                }
            }
            out.println("%end_instructions")

            out.println("%end_block")
        }
    }
}
