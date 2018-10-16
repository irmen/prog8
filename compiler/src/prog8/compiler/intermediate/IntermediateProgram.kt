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
                       val integerConstants: MutableMap<String, Int> = mutableMapOf(),
                       val labels: MutableMap<String, Instruction> = mutableMapOf())
    {
        val numVariables: Int
            get() { return variables.size }
        val numInstructions: Int
            get() { return instructions.filter { it.opcode!= Opcode.LINE }.size }

        fun getIns(idx: Int): Instruction {
            if(idx>=0 && idx <instructions.size)
                return instructions[idx]
            return Instruction(Opcode.NOP)
        }
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
        optimizeDataConversionAndUselessDiscards()
        optimizeVariableCopying()
        optimizeMultipleSequentialLineInstrs()
        // todo optimize stackvm code more

        // remove nops (that are not a label)
        for (blk in blocks) {
            blk.instructions.removeIf { it.opcode== Opcode.NOP && it !is LabelInstr }
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
                            if (it[0].value.callLabel != it[1].value.callLabel)
                                instructionsToReplace[it[0].index] = Instruction(Opcode.COPY_VAR_BYTE, null, it[0].value.callLabel, it[1].value.callLabel)
                            else
                                instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
                            instructionsToReplace[it[1].index] = Instruction(Opcode.NOP)
                        }
                    Opcode.PUSH_VAR_WORD ->
                        if (it[1].value.opcode == Opcode.POP_VAR_WORD) {
                            if (it[0].value.callLabel != it[1].value.callLabel)
                                instructionsToReplace[it[0].index] = Instruction(Opcode.COPY_VAR_WORD, null, it[0].value.callLabel, it[1].value.callLabel)
                            else
                                instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
                            instructionsToReplace[it[1].index] = Instruction(Opcode.NOP)
                        }
                    Opcode.PUSH_VAR_FLOAT ->
                        if (it[1].value.opcode == Opcode.POP_VAR_FLOAT) {
                            if (it[0].value.callLabel != it[1].value.callLabel)
                                instructionsToReplace[it[0].index] = Instruction(Opcode.COPY_VAR_FLOAT, null, it[0].value.callLabel, it[1].value.callLabel)
                            else
                                instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
                            instructionsToReplace[it[1].index] = Instruction(Opcode.NOP)
                        }
                    else -> {
                    }
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
                Opcode.LSB,
                Opcode.MSB,
                Opcode.B2WORD,
                Opcode.UB2UWORD,
                Opcode.MSB2WORD,
                Opcode.B2FLOAT,
                Opcode.UB2FLOAT,
                Opcode.UW2FLOAT,
                Opcode.W2FLOAT -> throw CompilerException("invalid conversion following a float")
                Opcode.DISCARD_FLOAT -> {
                    instructionsToReplace[index0] = Instruction(Opcode.NOP)
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.DISCARD_BYTE, Opcode.DISCARD_WORD -> throw CompilerException("invalid discard type following a float")
                else -> throw CompilerException("invalid conversion opcode ${ins1.opcode}")
            }
        }

        fun optimizeWordConversion(index0: Int, ins0: Instruction, index1: Int, ins1: Instruction) {
            when (ins1.opcode) {
                Opcode.LSB -> {
                    val ins = Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, ins0.arg!!.integerValue() and 255))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.MSB -> {
                    val ins = Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, ins0.arg!!.integerValue() ushr 8 and 255))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.B2WORD,
                Opcode.UB2UWORD,
                Opcode.MSB2WORD,
                Opcode.B2FLOAT,
                Opcode.UB2FLOAT -> throw CompilerException("invalid conversion following a word")
                Opcode.W2FLOAT, Opcode.UW2FLOAT -> {
                    val ins = Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, ins0.arg!!.integerValue().toDouble()))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.DISCARD_WORD -> {
                    instructionsToReplace[index0] = Instruction(Opcode.NOP)
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.DISCARD_BYTE, Opcode.DISCARD_FLOAT -> throw CompilerException("invalid discard type following a byte")
                else -> throw CompilerException("invalid conversion opcode ${ins1.opcode}")
            }
        }

        fun optimizeByteConversion(index0: Int, ins0: Instruction, index1: Int, ins1: Instruction) {
            when (ins1.opcode) {
                Opcode.LSB -> instructionsToReplace[index1] = Instruction(Opcode.NOP)
                Opcode.MSB -> throw CompilerException("msb of a byte")
                Opcode.UB2UWORD -> {
                    val ins = Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, ins0.arg!!.integerValue()))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.B2WORD -> {
                    val ins = Instruction(Opcode.PUSH_WORD, Value(DataType.WORD, ins0.arg!!.integerValue()))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.MSB2WORD -> {
                    val ins = Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 256 * ins0.arg!!.integerValue()))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.B2FLOAT, Opcode.UB2FLOAT -> {
                    val ins = Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, ins0.arg!!.integerValue().toDouble()))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.W2FLOAT, Opcode.UW2FLOAT -> throw CompilerException("invalid conversion following a byte")
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
                    Opcode.LSB,
                    Opcode.MSB,
                    Opcode.B2WORD,
                    Opcode.UB2UWORD,
                    Opcode.MSB2WORD,
                    Opcode.B2FLOAT,
                    Opcode.UB2FLOAT,
                    Opcode.W2FLOAT,
                    Opcode.UW2FLOAT,
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
                    DataType.ARRAY_B, DataType.ARRAY_W, DataType.MATRIX_B, DataType.MATRIX_UB,
                    DataType.ARRAY_UB, DataType.ARRAY_UW, DataType.ARRAY_F -> {
                        val litval = (decl.value as LiteralValue)
                        if(litval.heapId==null)
                            throw CompilerException("array/matrix should already be in the heap")
                        Value(decl.datatype, litval.heapId)
                    }
                }
                currentBlock.variables[scopedname] = value
            }
            VarDeclType.CONST -> {}     // constants are all folded away
            VarDeclType.MEMORY -> {
                currentBlock.integerConstants[scopedname] = (decl.value as LiteralValue).asIntegerValue!!
            }
        }
    }

    fun instr(opcode: Opcode, arg: Value? = null, callLabel: String? = null) {
        currentBlock.instructions.add(Instruction(opcode, arg, callLabel))
    }

    fun label(labelname: String) {
        val instr = LabelInstr(labelname)
        currentBlock.instructions.add(instr)
        currentBlock.labels[labelname] = instr
    }

    fun line(position: Position) {
        currentBlock.instructions.add(Instruction(Opcode.LINE, callLabel = "${position.line} ${position.file}"))
    }

    fun symbolDef(name: String, value: Int) {
        currentBlock.integerConstants[name] = value
    }

    fun newBlock(scopedname: String, shortname: String, address: Int?) {
        currentBlock = ProgramBlock(scopedname, shortname, address)
        blocks.add(currentBlock)
    }

    fun writeCode(out: PrintStream, embeddedLabels: Boolean=true) {
        out.println("; stackVM program code for '$name'")
        out.println("%memory")
        if(memory.isNotEmpty()) {
            TODO("print out initial memory load")
        }
        out.println("%end_memory")
        out.println("%heap")
        heap.allStrings().forEach {
            out.println("${it.index}  ${it.value.type.toString().toLowerCase()}  \"${it.value.str}\"")
        }
        heap.allArrays().forEach {
            out.println("${it.index}  ${it.value.type.toString().toLowerCase()}  ${it.value.array!!.toList()}")
        }
        heap.allDoubleArrays().forEach {
            out.println("${it.index}  ${it.value.type.toString().toLowerCase()}  ${it.value.doubleArray!!.toList()}")
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
