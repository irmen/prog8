package prog8.codegen.virtual

import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.vm.Instruction
import prog8.vm.Opcode
import prog8.vm.VmDataType


class CodeGen(internal val program: PtProgram,
              internal val symbolTable: SymbolTable,
              internal val options: CompilationOptions,
              internal val errors: IErrorReporter
): IAssemblyGenerator {

    internal val allocations = VariableAllocator(symbolTable, program, errors)
    private val expressionEval = ExpressionGen(this)

    init {
        if(options.dontReinitGlobals)
            TODO("support no globals re-init in vm")
    }

    override fun compileToAssembly(): IAssemblyProgram? {
        val vmprog = AssemblyProgram(program.name, allocations)

        // collect global variables initializers
        program.allBlocks().forEach {
            val chunk = VmCodeChunk()
            it.children.filterIsInstance<PtAssignment>().forEach { assign -> chunk += translate(assign, RegisterUsage(0)) }
            vmprog.addGlobalInits(chunk)
        }

        val regUsage = RegisterUsage(0)
        for (block in program.allBlocks()) {
            vmprog.addBlock(translate(block, regUsage))
        }

        return vmprog
    }


    private fun translateNode(node: PtNode, regUsage: RegisterUsage): VmCodeChunk {
        val chunk = when(node) {
            is PtBlock -> translate(node, regUsage)
            is PtSub -> translate(node, regUsage)
            is PtScopeVarsDecls -> VmCodeChunk() // vars should be looked up via symbol table
            is PtVariable -> VmCodeChunk() // var should be looked up via symbol table
            is PtMemMapped -> VmCodeChunk() // memmapped var should be looked up via symbol table
            is PtConstant -> VmCodeChunk() // constants have all been folded into the code
            is PtAssignment -> translate(node, regUsage)
            is PtNodeGroup -> translateGroup(node.children, regUsage)
            is PtBuiltinFunctionCall -> expressionEval.translate(node, regUsage.nextFree(), regUsage)
            is PtFunctionCall -> expressionEval.translate(node, regUsage.nextFree(), regUsage)
            is PtNop -> VmCodeChunk()
            is PtReturn -> translate(node)
            is PtJump -> translate(node)
            is PtConditionalBranch -> TODO()
            is PtPipe -> TODO()
            is PtForLoop -> TODO()
            is PtIfElse -> TODO()
            is PtPostIncrDecr -> translate(node, regUsage)
            is PtRepeatLoop -> translate(node, regUsage)
            is PtWhen -> TODO()
            is PtLabel -> VmCodeChunk(VmCodeLabel(node.scopedName))
            is PtBreakpoint -> VmCodeChunk(VmCodeInstruction(Instruction(Opcode.BREAKPOINT)))
            is PtAddressOf,
            is PtContainmentCheck,
            is PtMemoryByte,
            is PtProgram,
            is PtArrayIndexer,
            is PtBinaryExpression,
            is PtIdentifier,
            is PtWhenChoice,
            is PtPrefix,
            is PtRange,
            is PtAssignTarget,
            is PtTypeCast,
            is PtSubroutineParameter,
            is PtNumber,
            is PtArrayLiteral,
            is PtString -> throw AssemblyError("$node should not occur as separate statement node")
            is PtAsmSub -> throw AssemblyError("asmsub not supported on virtual machine target")
            is PtInlineAssembly -> throw AssemblyError("inline assembly not supported on virtual machine target")
            is PtIncludeBinary -> throw AssemblyError("inline binary data not supported on virtual machine target")
            else -> TODO("missing codegen for $node")
        }
        chunk.lines.add(0, VmCodeComment(node.position.toString()))
        return chunk
    }

    private fun translate(postIncrDecr: PtPostIncrDecr, regUsage: RegisterUsage): VmCodeChunk {
        val chunk = VmCodeChunk()
        val operation = when(postIncrDecr.operator) {
            "++" -> Opcode.INC
            "--" -> Opcode.DEC
            else -> throw AssemblyError("weird operator")
        }
        val ident = postIncrDecr.target.identifier
        val memory = postIncrDecr.target.memory
        val array = postIncrDecr.target.array
        val vmDt = vmType(postIncrDecr.target.type)
        val resultReg = regUsage.nextFree()
        if(ident!=null) {
            val address = allocations.get(ident.targetName)
            chunk += VmCodeInstruction(Instruction(Opcode.LOADM, vmDt, reg1=resultReg, value = address))
            chunk += VmCodeInstruction(Instruction(operation, vmDt, reg1=resultReg))
            chunk += VmCodeInstruction(Instruction(Opcode.STOREM, vmDt, reg1=resultReg, value = address))
        } else if(memory!=null) {
            val addressReg = regUsage.nextFree()
            chunk += expressionEval.translateExpression(memory.address, addressReg, regUsage)
            chunk += VmCodeInstruction(Instruction(Opcode.LOADI, vmDt, reg1=resultReg, reg2=addressReg))
            chunk += VmCodeInstruction(Instruction(operation, vmDt, reg1=resultReg))
            chunk += VmCodeInstruction(Instruction(Opcode.STOREI, vmDt, reg1=resultReg, reg2=addressReg))
        } else if (array!=null) {
            TODO("postincrdecr array")
        } else
            throw AssemblyError("weird assigntarget")

        return chunk
    }

    private fun translate(repeat: PtRepeatLoop, regUsage: RegisterUsage): VmCodeChunk {
        if((repeat.count as? PtNumber)?.number==0.0)
            return VmCodeChunk()
        if((repeat.count as? PtNumber)?.number==1.0)
            return translateGroup(repeat.children, regUsage)
        if((repeat.count as? PtNumber)?.number==256.0) {
            // 256 iterations can still be done with just a byte counter if you set it to zero as starting value.
            repeat.children[0] = PtNumber(DataType.UBYTE, 0.0, repeat.count.position)
        }

        val chunk = VmCodeChunk()
        val counterReg = regUsage.nextFree()
        val vmDt = vmType(repeat.count.type)
        chunk += expressionEval.translateExpression(repeat.count, counterReg, regUsage)
        val repeatLabel = createLabelName()
        chunk += VmCodeLabel(repeatLabel)
        chunk += translateNode(repeat.statements, regUsage)
        chunk += VmCodeInstruction(Instruction(Opcode.DEC, vmDt, reg1=counterReg))
        chunk += VmCodeInstruction(Instruction(Opcode.BNZ, vmDt, reg1=counterReg), labelArg = repeatLabel)
        return chunk
    }

    private fun translate(jump: PtJump): VmCodeChunk {
        val chunk = VmCodeChunk()
        if(jump.address!=null)
            throw AssemblyError("cannot jump to memory location in the vm target")
        chunk += if(jump.generatedLabel!=null)
            VmCodeInstruction(Instruction(Opcode.JUMP), labelArg = listOf(jump.generatedLabel!!))
        else if(jump.identifier!=null)
            VmCodeInstruction(Instruction(Opcode.JUMP), labelArg = jump.identifier!!.targetName)
        else
            throw AssemblyError("weird jump")
        return chunk
    }

    private fun translateGroup(group: List<PtNode>, regUsage: RegisterUsage): VmCodeChunk {
        val chunk = VmCodeChunk()
        group.forEach { chunk += translateNode(it, regUsage) }
        return chunk
    }

    private fun translate(assignment: PtAssignment, regUsage: RegisterUsage): VmCodeChunk {
        // TODO optimize in-place assignments (assignment.augmentable = true)

        val chunk = VmCodeChunk()
        val resultRegister = regUsage.nextFree()
        chunk += expressionEval.translateExpression(assignment.value, resultRegister, regUsage)
        val ident = assignment.target.identifier
        val memory = assignment.target.memory
        val array = assignment.target.array
        val vmDt = vmType(assignment.value.type)
        if(ident!=null) {
            val address = allocations.get(ident.targetName)
            val ins = Instruction(Opcode.STOREM, vmDt, reg1=resultRegister, value=address)
            chunk += VmCodeInstruction(ins)
        }
        else if(array!=null) {
            TODO("assign to array")
        }
        else if(memory!=null) {
            val ins =
                if(memory.address is PtNumber) {
                    Instruction(Opcode.STOREM, vmDt, reg1=resultRegister, value=(memory.address as PtNumber).number.toInt())
                } else {
                    val addressRegister = regUsage.nextFree()
                    chunk += expressionEval.translateExpression(assignment.value, addressRegister, regUsage)
                    Instruction(Opcode.STOREI, vmDt, reg1=resultRegister, reg2=addressRegister)
                }
            chunk += VmCodeInstruction(ins)
        }
        else
            throw AssemblyError("weird assigntarget")
        return chunk
    }

    private fun translate(ret: PtReturn): VmCodeChunk {
        val chunk = VmCodeChunk()
        val value = ret.value
        if(value!=null) {
            // Call Convention: return value is always returned in r0
            chunk += expressionEval.translateExpression(value, 0, RegisterUsage(1))
        }
        chunk += VmCodeInstruction(Instruction(Opcode.RETURN))
        return chunk
    }

    private fun translate(sub: PtSub, regUsage: RegisterUsage): VmCodeChunk {
        // TODO actually inline subroutines marked as inline
        val chunk = VmCodeChunk()
        chunk += VmCodeComment("SUB: ${sub.scopedName} -> ${sub.returntype}")
        chunk += VmCodeLabel(sub.scopedName)
        for (child in sub.children) {
            chunk += translateNode(child, regUsage)
        }
        chunk += VmCodeComment("SUB-END '${sub.name}'")
        return chunk
    }

    private fun translate(block: PtBlock, regUsage: RegisterUsage): VmCodeChunk {
        val chunk = VmCodeChunk()
        chunk += VmCodeComment("BLOCK '${block.name}'  addr=${block.address}  lib=${block.library}")
        for (child in block.children) {
            if(child !is PtAssignment) // global variable initialization is done elsewhere
                chunk += translateNode(child, regUsage)
        }
        chunk += VmCodeComment("BLOCK-END '${block.name}'")
        return chunk
    }


    internal fun vmType(type: DataType): VmDataType {
        return when(type) {
            DataType.UBYTE,
            DataType.BYTE -> VmDataType.BYTE
            DataType.UWORD,
            DataType.WORD -> VmDataType.WORD
            in PassByReferenceDatatypes -> VmDataType.WORD
            else -> throw AssemblyError("no vm datatype for $type")
        }
    }

    private var labelSequenceNumber = 0
    internal fun createLabelName(): List<String> {
        labelSequenceNumber++
        return listOf("generated$labelSequenceNumber")
    }
}
