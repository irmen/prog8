package compiler.intermediate

import prog8.ast.antlr.escape
import prog8.ast.base.*
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.ReferenceLiteralValue
import prog8.ast.statements.StructDecl
import prog8.ast.statements.VarDecl
import prog8.ast.statements.ZeropageWish
import prog8.compiler.CompilerException
import prog8.compiler.HeapValues
import prog8.compiler.Zeropage
import prog8.compiler.ZeropageDepletedError
import prog8.vm.RuntimeValue
import java.io.PrintStream
import java.nio.file.Path


class IntermediateProgram(val name: String, var loadAddress: Int, val heap: HeapValues, val source: Path) {

    class VariableParameters (val zp: ZeropageWish, val memberOfStruct: StructDecl?)
    class Variable(val scopedname: String, val value: RuntimeValue, val params: VariableParameters)

    class ProgramBlock(val name: String,
                       var address: Int?,
                       val instructions: MutableList<Instruction> = mutableListOf(),
                       val variables: MutableList<Variable> = mutableListOf(),
                       val memoryPointers: MutableMap<String, Pair<Int, DataType>> = mutableMapOf(),
                       val labels: MutableMap<String, Instruction> = mutableMapOf(),        // names are fully scoped
                       val force_output: Boolean)

    val allocatedZeropageVariables = mutableMapOf<String, Pair<Int, DataType>>()
    val blocks = mutableListOf<ProgramBlock>()
    val memory = mutableMapOf<Int, List<RuntimeValue>>()
    private lateinit var currentBlock: ProgramBlock

    fun allocateZeropage(zeropage: Zeropage) {          // TODO not used anymore???
        // allocates all @zp marked variables on the zeropage (for all blocks, as long as there is space in the ZP)
        var notAllocated = 0
        for(block in blocks) {
            val zpVariables = block.variables.filter { it.params.zp==ZeropageWish.REQUIRE_ZEROPAGE || it.params.zp==ZeropageWish.PREFER_ZEROPAGE }
            if (zpVariables.isNotEmpty()) {
                for (variable in zpVariables) {
                    if(variable.params.zp==ZeropageWish.NOT_IN_ZEROPAGE || variable.params.memberOfStruct!=null)
                        throw CompilerException("zp conflict")
                    try {
                        val address = zeropage.allocate(variable.scopedname, variable.value.type, null)
                        allocatedZeropageVariables[variable.scopedname] = Pair(address, variable.value.type)
                    } catch (x: ZeropageDepletedError) {
                        printWarning(x.toString() + " variable ${variable.scopedname} type ${variable.value.type}")
                        notAllocated++
                    }
                }
            }
        }
        if(notAllocated>0)
            printWarning("$notAllocated variables marked for Zeropage could not be allocated there")
    }

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
        optimizeConditionalBranches()
        // todo: add more optimizations to intermediate code!

        optimizeRemoveNops()    //  must be done as the last step
        optimizeMultipleSequentialLineInstrs()      // once more
        optimizeRemoveNops()    // once more
    }

    private fun optimizeConditionalBranches() {
        // conditional branches that consume the value on the stack
        // sometimes these are just constant values, so we can statically determine the branch
        // or, they are preceded by a NOT instruction so we can simply remove that and flip the branch condition
        val pushvalue = setOf(Opcode.PUSH_BYTE, Opcode.PUSH_WORD)
        val notvalue = setOf(Opcode.NOT_BYTE, Opcode.NOT_WORD)
        val branchOpcodes = setOf(Opcode.JZ, Opcode.JNZ, Opcode.JZW, Opcode.JNZW)
        for(blk in blocks) {
            val instructionsToReplace = mutableMapOf<Int, Instruction>()
            blk.instructions.asSequence().withIndex().filter {it.value.opcode!= Opcode.LINE }.windowed(2).toList().forEach {
                if (it[1].value.opcode in branchOpcodes) {
                    if (it[0].value.opcode in pushvalue) {
                        val value = it[0].value.arg!!.asBoolean
                        instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
                        val replacement: Instruction =
                                if (value) {
                                    when (it[1].value.opcode) {
                                        Opcode.JNZ -> Instruction(Opcode.JUMP, callLabel = it[1].value.callLabel)
                                        Opcode.JNZW -> Instruction(Opcode.JUMP, callLabel = it[1].value.callLabel)
                                        else -> Instruction(Opcode.NOP)
                                    }
                                } else {
                                    when (it[1].value.opcode) {
                                        Opcode.JZ -> Instruction(Opcode.JUMP, callLabel = it[1].value.callLabel)
                                        Opcode.JZW -> Instruction(Opcode.JUMP, callLabel = it[1].value.callLabel)
                                        else -> Instruction(Opcode.NOP)
                                    }
                                }
                        instructionsToReplace[it[1].index] = replacement
                    }
                    else if (it[0].value.opcode in notvalue) {
                        instructionsToReplace[it[0].index] = Instruction(Opcode.NOP)
                        val replacement: Instruction =
                                when (it[1].value.opcode) {
                                    Opcode.JZ -> Instruction(Opcode.JNZ, callLabel = it[1].value.callLabel)
                                    Opcode.JZW -> Instruction(Opcode.JNZW, callLabel = it[1].value.callLabel)
                                    Opcode.JNZ -> Instruction(Opcode.JZ, callLabel = it[1].value.callLabel)
                                    Opcode.JNZW -> Instruction(Opcode.JZW, callLabel = it[1].value.callLabel)
                                    else -> Instruction(Opcode.NOP)
                                }
                        instructionsToReplace[it[1].index] = replacement
                    }
                }
            }

            for (rins in instructionsToReplace) {
                blk.instructions[rins.key] = rins.value
            }
        }
    }

    private fun optimizeRemoveNops() {
        // remove nops (that are not a label)
        for (blk in blocks)
            blk.instructions.removeIf { it.opcode== Opcode.NOP && it !is LabelInstr }
    }

    private fun optimizeCallReturnIntoJump() {
        // replaces call X followed by return, by jump X
        for(blk in blocks) {
            val instructionsToReplace = mutableMapOf<Int, Instruction>()

            blk.instructions.asSequence().withIndex().filter {it.value.opcode!= Opcode.LINE }.windowed(2).toList().forEach {
                if(it[0].value.opcode== Opcode.CALL && it[1].value.opcode== Opcode.RETURN) {
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
                Opcode.CAST_UW_TO_B, Opcode.CAST_W_TO_B -> {
                    val ins = Instruction(Opcode.PUSH_BYTE, ins0.arg!!.cast(DataType.BYTE))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_W_TO_UB, Opcode.CAST_UW_TO_UB -> {
                    val ins = Instruction(Opcode.PUSH_BYTE, RuntimeValue(DataType.UBYTE, ins0.arg!!.integerValue() and 255))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.MSB -> {
                    val ins = Instruction(Opcode.PUSH_BYTE, RuntimeValue(DataType.UBYTE, ins0.arg!!.integerValue() ushr 8 and 255))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_W_TO_F, Opcode.CAST_UW_TO_F -> {
                    val ins = Instruction(Opcode.PUSH_FLOAT, RuntimeValue(DataType.FLOAT, ins0.arg!!.integerValue().toDouble()))
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
                    val ins = Instruction(Opcode.PUSH_WORD, RuntimeValue(DataType.UWORD, ins0.arg!!.integerValue()))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_B_TO_W -> {
                    val ins = Instruction(Opcode.PUSH_WORD, RuntimeValue(DataType.WORD, ins0.arg!!.integerValue()))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_B_TO_UW -> {
                    val ins = Instruction(Opcode.PUSH_WORD, ins0.arg!!.cast(DataType.UWORD))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_UB_TO_W -> {
                    val ins = Instruction(Opcode.PUSH_WORD, ins0.arg!!.cast(DataType.WORD))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_B_TO_F, Opcode.CAST_UB_TO_F -> {
                    val ins = Instruction(Opcode.PUSH_FLOAT, RuntimeValue(DataType.FLOAT, ins0.arg!!.integerValue().toDouble()))
                    instructionsToReplace[index0] = ins
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.CAST_W_TO_F, Opcode.CAST_UW_TO_F -> throw CompilerException("invalid conversion following a byte")
                Opcode.DISCARD_BYTE -> {
                    instructionsToReplace[index0] = Instruction(Opcode.NOP)
                    instructionsToReplace[index1] = Instruction(Opcode.NOP)
                }
                Opcode.DISCARD_WORD, Opcode.DISCARD_FLOAT -> throw CompilerException("invalid discard type following a byte")
                Opcode.MKWORD -> {}
                else -> throw CompilerException("invalid conversion opcode ${ins1.opcode}")
            }
        }

        for(blk in blocks) {
            instructionsToReplace.clear()

            val typeConversionOpcodes = setOf(
                    Opcode.MSB,
                    Opcode.MKWORD,
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
                // var decls that are defined inside of a StructDecl are skipped in the output
                //   because every occurrence of the members will have a separate mangled vardecl for that occurrence
                if(decl.parent is StructDecl)
                    return

                val valueparams = VariableParameters(decl.zeropage, decl.struct)
                val value = when(decl.datatype) {
                    in NumericDatatypes -> {
                        RuntimeValue(decl.datatype, (decl.value as NumericLiteralValue).number)
                    }
                    in StringDatatypes -> {
                        val litval = (decl.value as ReferenceLiteralValue)
                        if(litval.heapId==null)
                            throw CompilerException("string should already be in the heap")
                        RuntimeValue(decl.datatype, heapId = litval.heapId)
                    }
                    in ArrayDatatypes -> {
                        val litval = (decl.value as? ReferenceLiteralValue)
                        if(litval!=null && litval.heapId==null)
                            throw CompilerException("array should already be in the heap")
                        if(litval!=null){
                            RuntimeValue(decl.datatype, heapId = litval.heapId)
                        } else {
                            throw CompilerException("initialization value expected")
                        }
                    }
                    DataType.STRUCT -> {
                        // struct variables have been flattened already
                        return
                    }
                    else -> throw CompilerException("weird datatype")
                }
                currentBlock.variables.add(Variable(scopedname, value, valueparams))
            }
            VarDeclType.MEMORY -> {
                // note that constants are all folded away, but assembly code may still refer to them
                val lv = decl.value as NumericLiteralValue
                if(lv.type!= DataType.UWORD && lv.type!= DataType.UBYTE)
                    throw CompilerException("expected integer memory address $lv")
                currentBlock.memoryPointers[scopedname] = Pair(lv.number.toInt(), decl.datatype)
            }
            VarDeclType.CONST -> {
                // note that constants are all folded away, but assembly code may still refer to them (if their integers)
                // floating point constants are not generated at all!!
                val lv = decl.value as NumericLiteralValue
                if(lv.type in IntegerDatatypes)
                    currentBlock.memoryPointers[scopedname] = Pair(lv.number.toInt(), decl.datatype)
            }
        }
    }

    fun instr(opcode: Opcode, arg: RuntimeValue? = null, arg2: RuntimeValue? = null, callLabel: String? = null, callLabel2: String? = null) {
        currentBlock.instructions.add(Instruction(opcode, arg, arg2, callLabel, callLabel2))
    }

    fun label(labelname: String, asmProc: Boolean=false) {
        val instr = LabelInstr(labelname, asmProc)
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

    fun newBlock(name: String, address: Int?, options: Set<String>) {
        currentBlock = ProgramBlock(name, address, force_output = "force_output" in options)
        blocks.add(currentBlock)
    }

    fun writeCode(out: PrintStream, embeddedLabels: Boolean=true) {
        out.println("; stackVM program code for '$name'")
        writeMemory(out)
        writeHeap(out)
        for(blk in blocks) {
            writeBlock(out, blk, embeddedLabels)
        }
    }

    private fun writeHeap(out: PrintStream) {
        out.println("%heap")
        heap.allEntries().forEach {
            out.print("${it.key}  ${it.value.type.name.toLowerCase()}  ")
            when {
                it.value.str!=null ->
                    out.println("\"${escape(it.value.str!!)}\"")
                it.value.array!=null -> {
                    // this array can contain both normal integers, and pointer values
                    val arrayvalues = it.value.array!!.map { av ->
                        when {
                            av.integer!=null -> av.integer.toString()
                            av.addressOf!=null -> {
                                if(av.addressOf.scopedname==null)
                                    throw CompilerException("AddressOf scopedname should have been set")
                                else
                                    "&${av.addressOf.scopedname}"
                            }
                            else -> throw CompilerException("weird array value")
                        }
                    }
                    out.println(arrayvalues)
                }
                it.value.doubleArray!=null ->
                    out.println(it.value.doubleArray!!.toList())
                else -> throw CompilerException("invalid heap entry $it")
            }
        }
        out.println("%end_heap")
    }

    private fun writeBlock(out: PrintStream, blk: ProgramBlock, embeddedLabels: Boolean) {
        out.println("\n%block ${blk.name} ${blk.address?.toString(16) ?: ""}")

        out.println("%variables")
        for (variable in blk.variables) {
            if(variable.params.zp==ZeropageWish.REQUIRE_ZEROPAGE)
                throw CompilerException("zp conflict")
            val valuestr = variable.value.toString()
            val struct =  if(variable.params.memberOfStruct==null) "" else "struct=${variable.params.memberOfStruct.name}"
            out.println("${variable.scopedname}  ${variable.value.type.name.toLowerCase()}  $valuestr  zp=${variable.params.zp} s=$struct")
        }
        out.println("%end_variables")
        out.println("%memorypointers")
        for (iconst in blk.memoryPointers) {
            out.println("${iconst.key}  ${iconst.value.second.name.toLowerCase()}  uw:${iconst.value.first.toString(16)}")
        }
        out.println("%end_memorypointers")
        out.println("%instructions")
        val labels = blk.labels.entries.associateBy({ it.value }) { it.key }
        for (instr in blk.instructions) {
            if (!embeddedLabels) {
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

    private fun writeMemory(out: PrintStream) {
        out.println("%memory")
        if (memory.isNotEmpty())
            TODO("add support for writing/reading initial memory values")
        out.println("%end_memory")
    }
}
