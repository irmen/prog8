package prog8.codegen.virtual

import prog8.code.StStaticVariable
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.vm.Instruction
import prog8.vm.Opcode
import prog8.vm.VmDataType
import java.lang.Math.pow
import kotlin.math.pow


internal class VmRegisterPool() {
    private var firstFree: Int=3    // registers 0,1,2 are reserved
    
    fun peekNext() = firstFree

    fun nextFree(): Int {
        val result = firstFree
        firstFree++
        if(firstFree>65535)
            throw AssemblyError("out of virtual registers")
        return result
    }
}


class CodeGen(internal val program: PtProgram,
              internal val symbolTable: SymbolTable,
              internal val options: CompilationOptions,
              internal val errors: IErrorReporter
): IAssemblyGenerator {

    internal val allocations = VariableAllocator(symbolTable, program, errors)
    private val expressionEval = ExpressionGen(this)
    private val builtinFuncGen = BuiltinFuncGen(this, expressionEval)
    internal val vmRegisters = VmRegisterPool()

    init {
        if(options.dontReinitGlobals)
            TODO("support no globals re-init in vm")
    }

    override fun compileToAssembly(): IAssemblyProgram? {
        val vmprog = AssemblyProgram(program.name, allocations)

        // collect global variables initializers
        program.allBlocks().forEach {
            val code = VmCodeChunk()
            it.children.filterIsInstance<PtAssignment>().forEach { assign -> code += translate(assign) }
            vmprog.addGlobalInits(code)
        }

        for (block in program.allBlocks()) {
            vmprog.addBlock(translate(block))
        }

        return vmprog
    }


    private fun translateNode(node: PtNode): VmCodeChunk {
        val code = when(node) {
            is PtBlock -> translate(node)
            is PtSub -> translate(node)
            is PtScopeVarsDecls -> VmCodeChunk() // vars should be looked up via symbol table
            is PtVariable -> VmCodeChunk() // var should be looked up via symbol table
            is PtMemMapped -> VmCodeChunk() // memmapped var should be looked up via symbol table
            is PtConstant -> VmCodeChunk() // constants have all been folded into the code
            is PtAssignment -> translate(node)
            is PtNodeGroup -> translateGroup(node.children)
            is PtBuiltinFunctionCall -> translateBuiltinFunc(node, 0)
            is PtFunctionCall -> expressionEval.translate(node, 0)
            is PtNop -> VmCodeChunk()
            is PtReturn -> translate(node)
            is PtJump -> translate(node)
            is PtWhen -> TODO("when")
            is PtPipe -> expressionEval.translate(node, 0)
            is PtForLoop -> translate(node)
            is PtIfElse -> translate(node)
            is PtPostIncrDecr -> translate(node)
            is PtRepeatLoop -> translate(node)
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
            is PtArray,
            is PtString -> throw AssemblyError("strings should not occur as separate statement node ${node.position}")
            is PtAsmSub -> throw AssemblyError("asmsub not supported on virtual machine target ${node.position}")
            is PtInlineAssembly -> throw AssemblyError("inline assembly not supported on virtual machine target ${node.position}")
            is PtIncludeBinary -> throw AssemblyError("inline binary data not supported on virtual machine target ${node.position}")
            is PtConditionalBranch -> throw AssemblyError("conditional branches not supported in vm target due to lack of cpu flags ${node.position}")
            else -> TODO("missing codegen for $node")
        }
        if(code.lines.isNotEmpty())
            code.lines.add(0, VmCodeComment(node.position.toString()))
        return code
    }

    private fun translate(forLoop: PtForLoop): VmCodeChunk {
        val loopvar = symbolTable.lookup(forLoop.variable.targetName) as StStaticVariable
        val iterable = forLoop.iterable
        val code = VmCodeChunk()
        when(iterable) {
            is PtRange -> {
                TODO("forloop ${loopvar.dt} ${loopvar.scopedName} in range ${iterable} ")
                iterable.printIndented(0)
                TODO("forloop over range")
            }
            is PtIdentifier -> {
                val arrayAddress = allocations.get(iterable.targetName)
                val iterableVar = symbolTable.lookup(iterable.targetName) as StStaticVariable
                val loopvarAddress = allocations.get(loopvar.scopedName)
                val indexReg = vmRegisters.nextFree()
                val loopLabel = createLabelName()
                val endLabel = createLabelName()
                if(iterableVar.dt==DataType.STR) {
                    // iterate over a zero-terminated string
                    code += VmCodeInstruction(Instruction(Opcode.LOAD, VmDataType.BYTE, reg1=indexReg, value=0))
                    code += VmCodeLabel(loopLabel)
                    code += VmCodeInstruction(Instruction(Opcode.LOADX, VmDataType.BYTE, reg1=0, reg2=indexReg, value = arrayAddress))
                    code += VmCodeInstruction(Instruction(Opcode.BZ, VmDataType.BYTE, reg1=0, symbol = endLabel))
                    code += VmCodeInstruction(Instruction(Opcode.STOREM, VmDataType.BYTE, reg1=0, value = loopvarAddress))
                    code += translateNode(forLoop.statements)
                    code += VmCodeInstruction(Instruction(Opcode.INC, VmDataType.BYTE, reg1=indexReg))
                    code += VmCodeInstruction(Instruction(Opcode.JUMP, symbol = loopLabel))
                    code += VmCodeLabel(endLabel)
                } else {
                    // iterate over array
                    val elementDt = ArrayToElementTypes.getValue(iterable.type)
                    val elementSize = program.memsizer.memorySize(elementDt).toUInt()
                    val lengthBytes = iterableVar.length!! * elementSize.toInt()
                    val lengthReg = vmRegisters.nextFree()
                    /*
                    index = 0
                    _loop:
                    if index==(length * <element_size>) goto _end
                    loopvar = read_mem(iterable+index)
                    <statements>
                    index += <elementsize>
                    goto _loop
                    _end: ...
                     */
                    code += VmCodeInstruction(Instruction(Opcode.LOAD, VmDataType.BYTE, reg1=indexReg, value=0))
                    code += VmCodeInstruction(Instruction(Opcode.LOAD, VmDataType.BYTE, reg1=lengthReg, value=lengthBytes))
                    code += VmCodeLabel(loopLabel)
                    code += VmCodeInstruction(Instruction(Opcode.BEQ, VmDataType.BYTE, reg1=indexReg, reg2=lengthReg, symbol = endLabel))
                    code += VmCodeInstruction(Instruction(Opcode.LOADX, vmType(elementDt), reg1=0, reg2=indexReg, value=arrayAddress))
                    code += VmCodeInstruction(Instruction(Opcode.STOREM, vmType(elementDt), reg1=0, value = loopvarAddress))
                    code += translateNode(forLoop.statements)
                    code += addConst(VmDataType.BYTE, indexReg, elementSize)
                    code += VmCodeInstruction(Instruction(Opcode.JUMP, symbol = loopLabel))
                    code += VmCodeLabel(endLabel)
                }
            }
            else -> throw AssemblyError("weird for iterable")
        }
        return code
    }

    private fun addConst(dt: VmDataType, reg: Int, value: UInt): VmCodeChunk {
        val code = VmCodeChunk()
        when(value) {
            0u -> { /* do nothing */ }
            1u -> {
                code += VmCodeInstruction(Instruction(Opcode.INC, dt, reg1=reg))
            }
            else -> {
                val valueReg = vmRegisters.nextFree()
                code += VmCodeInstruction(Instruction(Opcode.LOAD, dt, reg1=valueReg, value=value.toInt()))
                code += VmCodeInstruction(Instruction(Opcode.ADD, dt, reg1=reg, reg2=reg, reg3=valueReg))
            }
        }
        return code
    }

    private val powersOfTwo = (0..16).map { 2.0.pow(it.toDouble()).toInt() }

    private fun multiplyByConst(dt: VmDataType, reg: Int, factor: UInt): VmCodeChunk {
        val code = VmCodeChunk()
        val pow2 = powersOfTwo.indexOf(factor.toInt())
        if(pow2>=1) {
            // just shift bits
            code += VmCodeInstruction(Instruction(Opcode.LSL, dt, reg1=reg, reg2=reg, reg3=pow2))
        } else {
            when(factor) {
                0u -> {
                    code += VmCodeInstruction(Instruction(Opcode.LOAD, dt, reg1=reg, value=0))
                }
                1u -> { /* do nothing */ }
                else -> {
                    val factorReg = vmRegisters.nextFree()
                    code += VmCodeInstruction(Instruction(Opcode.LOAD, dt, reg1=factorReg, value=factor.toInt()))
                    code += VmCodeInstruction(Instruction(Opcode.MUL, dt, reg1=reg, reg2=reg, reg3=factorReg))
                }
            }
        }
        return code
    }

    private fun translate(ifElse: PtIfElse): VmCodeChunk {
        var branch = Opcode.BZ
        var condition = ifElse.condition

        val cond = ifElse.condition as? PtBinaryExpression
        if((cond?.right as? PtNumber)?.number==0.0) {
            if(cond.operator == "==") {
                // if X==0 ...   so we branch on Not-zero instead.
                branch = Opcode.BNZ
                condition = cond.left
            }
            else if(cond.operator == "!=") {
                // if X!=0 ...   so we keep branching on Zero.
                condition = cond.left
            }
        }

        val conditionReg = vmRegisters.nextFree()
        val vmDt = vmType(condition.type)
        val code = VmCodeChunk()
        code += expressionEval.translateExpression(condition, conditionReg)
        if(ifElse.elseScope.children.isNotEmpty()) {
            // if and else parts
            val elseLabel = createLabelName()
            val afterIfLabel = createLabelName()
            code += VmCodeInstruction(Instruction(branch, vmDt, reg1=conditionReg, symbol = elseLabel))
            code += translateNode(ifElse.ifScope)
            code += VmCodeInstruction(Instruction(Opcode.JUMP, symbol = afterIfLabel))
            code += VmCodeLabel(elseLabel)
            code += translateNode(ifElse.elseScope)
            code += VmCodeLabel(afterIfLabel)
        } else {
            // only if part
            val afterIfLabel = createLabelName()
            code += VmCodeInstruction(Instruction(branch, vmDt, reg1=conditionReg, symbol = afterIfLabel))
            code += translateNode(ifElse.ifScope)
            code += VmCodeLabel(afterIfLabel)
        }
        return code
    }

    private fun translate(postIncrDecr: PtPostIncrDecr): VmCodeChunk {
        val code = VmCodeChunk()
        val operation = when(postIncrDecr.operator) {
            "++" -> Opcode.INC
            "--" -> Opcode.DEC
            else -> throw AssemblyError("weird operator")
        }
        val ident = postIncrDecr.target.identifier
        val memory = postIncrDecr.target.memory
        val array = postIncrDecr.target.array
        val vmDt = vmType(postIncrDecr.target.type)
        val resultReg = vmRegisters.nextFree()
        if(ident!=null) {
            val address = allocations.get(ident.targetName)
            code += VmCodeInstruction(Instruction(Opcode.LOADM, vmDt, reg1=resultReg, value = address))
            code += VmCodeInstruction(Instruction(operation, vmDt, reg1=resultReg))
            code += VmCodeInstruction(Instruction(Opcode.STOREM, vmDt, reg1=resultReg, value = address))
        } else if(memory!=null) {
            val addressReg = vmRegisters.nextFree()
            code += expressionEval.translateExpression(memory.address, addressReg)
            code += VmCodeInstruction(Instruction(Opcode.LOADI, vmDt, reg1=resultReg, reg2=addressReg))
            code += VmCodeInstruction(Instruction(operation, vmDt, reg1=resultReg))
            code += VmCodeInstruction(Instruction(Opcode.STOREI, vmDt, reg1=resultReg, reg2=addressReg))
        } else if (array!=null) {
            TODO("postincrdecr array")
        } else
            throw AssemblyError("weird assigntarget")

        return code
    }

    private fun translate(repeat: PtRepeatLoop): VmCodeChunk {
        if((repeat.count as? PtNumber)?.number==0.0)
            return VmCodeChunk()
        if((repeat.count as? PtNumber)?.number==1.0)
            return translateGroup(repeat.children)
        if((repeat.count as? PtNumber)?.number==256.0) {
            // 256 iterations can still be done with just a byte counter if you set it to zero as starting value.
            repeat.children[0] = PtNumber(DataType.UBYTE, 0.0, repeat.count.position)
        }

        val code = VmCodeChunk()
        val counterReg = vmRegisters.nextFree()
        val vmDt = vmType(repeat.count.type)
        code += expressionEval.translateExpression(repeat.count, counterReg)
        val repeatLabel = createLabelName()
        code += VmCodeLabel(repeatLabel)
        code += translateNode(repeat.statements)
        code += VmCodeInstruction(Instruction(Opcode.DEC, vmDt, reg1=counterReg))
        code += VmCodeInstruction(Instruction(Opcode.BNZ, vmDt, reg1=counterReg, symbol = repeatLabel))
        return code
    }

    private fun translate(jump: PtJump): VmCodeChunk {
        val code = VmCodeChunk()
        if(jump.address!=null)
            throw AssemblyError("cannot jump to memory location in the vm target")
        code += if(jump.generatedLabel!=null)
            VmCodeInstruction(Instruction(Opcode.JUMP, symbol = listOf(jump.generatedLabel!!)))
        else if(jump.identifier!=null)
            VmCodeInstruction(Instruction(Opcode.JUMP, symbol = jump.identifier!!.targetName))
        else
            throw AssemblyError("weird jump")
        return code
    }

    private fun translateGroup(group: List<PtNode>): VmCodeChunk {
        val code = VmCodeChunk()
        group.forEach { code += translateNode(it) }
        return code
    }

    private fun translate(assignment: PtAssignment): VmCodeChunk {
        // TODO can in-place assignments (assignment.augmentable = true) be optimized more?

        val code = VmCodeChunk()
        val resultRegister = vmRegisters.nextFree()
        code += expressionEval.translateExpression(assignment.value, resultRegister)
        val ident = assignment.target.identifier
        val memory = assignment.target.memory
        val array = assignment.target.array
        val vmDt = vmType(assignment.value.type)
        if(ident!=null) {
            val address = allocations.get(ident.targetName)
            val ins = Instruction(Opcode.STOREM, vmDt, reg1=resultRegister, value=address)
            code += VmCodeInstruction(ins)
        }
        else if(array!=null) {
            val variable = array.variable.targetName
            var variableAddr = allocations.get(variable)
            val itemsize = program.memsizer.memorySize(array.type)
            val fixedIndex = (array.index as? PtNumber)?.number?.toInt()
            val vmDtArrayIdx = vmType(array.type)
            if(fixedIndex!=null) {
                variableAddr += fixedIndex*itemsize
                code += VmCodeInstruction(Instruction(Opcode.STOREM, vmDtArrayIdx, reg1 = resultRegister, value=variableAddr))
            } else {
                val indexReg = vmRegisters.nextFree()
                code += expressionEval.translateExpression(array.index, indexReg)
                code += VmCodeInstruction(Instruction(Opcode.STOREX, vmDtArrayIdx, reg1 = resultRegister, reg2=indexReg, value=variableAddr))
            }
        }
        else if(memory!=null) {
            val ins =
                if(memory.address is PtNumber) {
                    Instruction(Opcode.STOREM, vmDt, reg1=resultRegister, value=(memory.address as PtNumber).number.toInt())
                } else {
                    val addressRegister = vmRegisters.nextFree()
                    code += expressionEval.translateExpression(assignment.value, addressRegister)
                    Instruction(Opcode.STOREI, vmDt, reg1=resultRegister, reg2=addressRegister)
                }
            code += VmCodeInstruction(ins)
        }
        else
            throw AssemblyError("weird assigntarget")
        return code
    }

    private fun translate(ret: PtReturn): VmCodeChunk {
        val code = VmCodeChunk()
        val value = ret.value
        if(value!=null) {
            // Call Convention: return value is always returned in r0
            code += expressionEval.translateExpression(value, 0)
        }
        code += VmCodeInstruction(Instruction(Opcode.RETURN))
        return code
    }

    private fun translate(sub: PtSub): VmCodeChunk {
        // TODO actually inline subroutines marked as inline
        val code = VmCodeChunk()
        code += VmCodeComment("SUB: ${sub.scopedName} -> ${sub.returntype}")
        code += VmCodeLabel(sub.scopedName)
        for (child in sub.children) {
            code += translateNode(child)
        }
        code += VmCodeComment("SUB-END '${sub.name}'")
        return code
    }

    private fun translate(block: PtBlock): VmCodeChunk {
        val code = VmCodeChunk()
        code += VmCodeComment("BLOCK '${block.name}'  addr=${block.address}  lib=${block.library}")
        for (child in block.children) {
            if(child !is PtAssignment) // global variable initialization is done elsewhere
                code += translateNode(child)
        }
        code += VmCodeComment("BLOCK-END '${block.name}'")
        return code
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

    internal fun translateBuiltinFunc(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk =
        builtinFuncGen.translate(call, resultRegister)
}
