package prog8.codegen.virtual

import prog8.code.StStaticVariable
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.vm.Opcode
import prog8.vm.VmDataType
import kotlin.math.pow


internal class VmRegisterPool {
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

        println("Vm codegen: amount of vm registers=${vmRegisters.peekNext()}")

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
            is PtBreakpoint -> VmCodeChunk(VmCodeInstruction(Opcode.BREAKPOINT))
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
        if(code.lines.isNotEmpty() && node.position.line!=0)
            code.lines.add(0, VmCodeComment(node.position.toString()))
        return code
    }

    private fun translate(forLoop: PtForLoop): VmCodeChunk {
        val loopvar = symbolTable.lookup(forLoop.variable.targetName) as StStaticVariable
        val iterable = forLoop.iterable
        val code = VmCodeChunk()
        when(iterable) {
            is PtRange -> {
                if(iterable.from is PtNumber && iterable.to is PtNumber)
                    code += translateForInConstantRange(forLoop, loopvar)
                else
                    code += translateForInNonConstantRange(forLoop, loopvar)
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
                    code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=indexReg, value=0)
                    code += VmCodeLabel(loopLabel)
                    code += VmCodeInstruction(Opcode.LOADX, VmDataType.BYTE, reg1=0, reg2=indexReg, value = arrayAddress)
                    code += VmCodeInstruction(Opcode.BZ, VmDataType.BYTE, reg1=0, symbol = endLabel)
                    code += VmCodeInstruction(Opcode.STOREM, VmDataType.BYTE, reg1=0, value = loopvarAddress)
                    code += translateNode(forLoop.statements)
                    code += VmCodeInstruction(Opcode.INC, VmDataType.BYTE, reg1=indexReg)
                    code += VmCodeInstruction(Opcode.JUMP, symbol = loopLabel)
                    code += VmCodeLabel(endLabel)
                } else {
                    // iterate over array
                    val elementDt = ArrayToElementTypes.getValue(iterable.type)
                    val elementSize = program.memsizer.memorySize(elementDt)
                    val lengthBytes = iterableVar.length!! * elementSize
                    val lengthReg = vmRegisters.nextFree()
                    code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=indexReg, value=0)
                    code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=lengthReg, value=lengthBytes)
                    code += VmCodeLabel(loopLabel)
                    code += VmCodeInstruction(Opcode.BEQ, VmDataType.BYTE, reg1=indexReg, reg2=lengthReg, symbol = endLabel)
                    code += VmCodeInstruction(Opcode.LOADX, vmType(elementDt), reg1=0, reg2=indexReg, value=arrayAddress)
                    code += VmCodeInstruction(Opcode.STOREM, vmType(elementDt), reg1=0, value = loopvarAddress)
                    code += translateNode(forLoop.statements)
                    code += addConstReg(VmDataType.BYTE, indexReg, elementSize)
                    code += VmCodeInstruction(Opcode.JUMP, symbol = loopLabel)
                    code += VmCodeLabel(endLabel)
                }
            }
            else -> throw AssemblyError("weird for iterable")
        }
        return code
    }

    private fun translateForInNonConstantRange(forLoop: PtForLoop, loopvar: StStaticVariable): VmCodeChunk {
        val iterable = forLoop.iterable as PtRange
        val step = iterable.step.number.toInt()
        if (step==0)
            throw AssemblyError("step 0")
        val indexReg = vmRegisters.nextFree()
        val endvalueReg = vmRegisters.nextFree()
        val loopvarAddress = allocations.get(loopvar.scopedName)
        val loopvarDt = vmType(loopvar.dt)
        val loopLabel = createLabelName()
        val code = VmCodeChunk()

        code += expressionEval.translateExpression(iterable.to, endvalueReg)
        code += expressionEval.translateExpression(iterable.from, indexReg)
        code += VmCodeInstruction(Opcode.STOREM, loopvarDt, reg1=indexReg, value=loopvarAddress)
        code += VmCodeLabel(loopLabel)
        code += translateNode(forLoop.statements)
        if(step<3) {
            code += addConstMem(loopvarDt, loopvarAddress.toUInt(), step)
            code += VmCodeInstruction(Opcode.LOADM, loopvarDt, reg1 = indexReg, value = loopvarAddress)
        } else {
            code += VmCodeInstruction(Opcode.LOADM, loopvarDt, reg1 = indexReg, value = loopvarAddress)
            code += addConstReg(loopvarDt, indexReg, step)
            code += VmCodeInstruction(Opcode.STOREM, loopvarDt, reg1 = indexReg, value = loopvarAddress)
        }
        val branchOpcode = if(loopvar.dt in SignedDatatypes) Opcode.BLES else Opcode.BLE
        code += VmCodeInstruction(branchOpcode, loopvarDt, reg1=indexReg, reg2=endvalueReg, symbol=loopLabel)
        return code
    }

    private fun translateForInConstantRange(forLoop: PtForLoop, loopvar: StStaticVariable): VmCodeChunk {
        val iterable = forLoop.iterable as PtRange
        val step = iterable.step.number.toInt()
        val range = IntProgression.fromClosedRange(
            (iterable.from as PtNumber).number.toInt(),
            (iterable.to as PtNumber).number.toInt() + step,
            step)
        if (range.isEmpty() || range.step==0)
            throw AssemblyError("empty range or step 0")

        val loopLabel = createLabelName()
        val loopvarAddress = allocations.get(loopvar.scopedName)
        val indexReg = vmRegisters.nextFree()
        val endvalueReg = vmRegisters.nextFree()
        val loopvarDt = vmType(loopvar.dt)
        val code = VmCodeChunk()
        code += VmCodeInstruction(Opcode.LOAD, loopvarDt, reg1=endvalueReg, value=range.last)
        code += VmCodeInstruction(Opcode.LOAD, loopvarDt, reg1=indexReg, value=range.first)
        code += VmCodeInstruction(Opcode.STOREM, loopvarDt, reg1=indexReg, value=loopvarAddress)
        code += VmCodeLabel(loopLabel)
        code += translateNode(forLoop.statements)
        if(range.step<3) {
            code += addConstMem(loopvarDt, loopvarAddress.toUInt(), range.step)
            code += VmCodeInstruction(Opcode.LOADM, loopvarDt, reg1 = indexReg, value = loopvarAddress)
        } else {
            code += VmCodeInstruction(Opcode.LOADM, loopvarDt, reg1 = indexReg, value = loopvarAddress)
            code += addConstReg(loopvarDt, indexReg, range.step)
            code += VmCodeInstruction(Opcode.STOREM, loopvarDt, reg1 = indexReg, value = loopvarAddress)
        }
        // TODO more optimal loop instruction for loops ending on 0 (BNZ?)
        code += VmCodeInstruction(Opcode.BNE, loopvarDt, reg1=indexReg, reg2=endvalueReg, symbol=loopLabel)
        return code
    }

    private fun addConstReg(dt: VmDataType, reg: Int, value: Int): VmCodeChunk {
        val code = VmCodeChunk()
        when(value) {
            0 -> { /* do nothing */ }
            1 -> {
                code += VmCodeInstruction(Opcode.INC, dt, reg1=reg)
            }
            2 -> {
                code += VmCodeInstruction(Opcode.INC, dt, reg1=reg)
                code += VmCodeInstruction(Opcode.INC, dt, reg1=reg)
            }
            -1 -> {
                code += VmCodeInstruction(Opcode.DEC, dt, reg1=reg)
            }
            -2 -> {
                code += VmCodeInstruction(Opcode.DEC, dt, reg1=reg)
                code += VmCodeInstruction(Opcode.DEC, dt, reg1=reg)
            }
            else -> {
                val valueReg = vmRegisters.nextFree()
                if(value>0) {
                    code += VmCodeInstruction(Opcode.LOAD, dt, reg1=valueReg, value= value)
                    code += VmCodeInstruction(Opcode.ADD, dt, reg1 = reg, reg2 = reg, reg3 = valueReg)
                }
                else {
                    code += VmCodeInstruction(Opcode.LOAD, dt, reg1=valueReg, value= -value)
                    code += VmCodeInstruction(Opcode.SUB, dt, reg1 = reg, reg2 = reg, reg3 = valueReg)
                }
            }
        }
        return code
    }

    private fun addConstMem(dt: VmDataType, address: UInt, value: Int): VmCodeChunk {
        val code = VmCodeChunk()
        when(value) {
            0 -> { /* do nothing */ }
            1 -> {
                code += VmCodeInstruction(Opcode.INCM, dt, value=address.toInt())
            }
            2 -> {
                code += VmCodeInstruction(Opcode.INCM, dt, value=address.toInt())
                code += VmCodeInstruction(Opcode.INCM, dt, value=address.toInt())
            }
            -1 -> {
                code += VmCodeInstruction(Opcode.DECM, dt, value=address.toInt())
            }
            -2 -> {
                code += VmCodeInstruction(Opcode.DECM, dt, value=address.toInt())
                code += VmCodeInstruction(Opcode.DECM, dt, value=address.toInt())
            }
            else -> {
                val valueReg = vmRegisters.nextFree()
                val operandReg = vmRegisters.nextFree()
                if(value>0) {
                    code += VmCodeInstruction(Opcode.LOADM, dt, reg1=valueReg, value=address.toInt())
                    code += VmCodeInstruction(Opcode.LOAD, dt, reg1=operandReg, value=value)
                    code += VmCodeInstruction(Opcode.ADD, dt, reg1 = valueReg, reg2 = valueReg, reg3 = operandReg)
                    code += VmCodeInstruction(Opcode.STOREM, dt, reg1=valueReg, value=address.toInt())
                }
                else {
                    code += VmCodeInstruction(Opcode.LOADM, dt, reg1=valueReg, value=address.toInt())
                    code += VmCodeInstruction(Opcode.LOAD, dt, reg1=operandReg, value=-value)
                    code += VmCodeInstruction(Opcode.SUB, dt, reg1 = valueReg, reg2 = valueReg, reg3 = operandReg)
                    code += VmCodeInstruction(Opcode.STOREM, dt, reg1=valueReg, value=address.toInt())
                }
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
            code += VmCodeInstruction(Opcode.LSL, dt, reg1=reg, reg2=reg, reg3=pow2)
        } else {
            when(factor) {
                0u -> {
                    code += VmCodeInstruction(Opcode.LOAD, dt, reg1=reg, value=0)
                }
                1u -> { /* do nothing */ }
                else -> {
                    val factorReg = vmRegisters.nextFree()
                    code += VmCodeInstruction(Opcode.LOAD, dt, reg1=factorReg, value=factor.toInt())
                    code += VmCodeInstruction(Opcode.MUL, dt, reg1=reg, reg2=reg, reg3=factorReg)
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
            code += VmCodeInstruction(branch, vmDt, reg1=conditionReg, symbol = elseLabel)
            code += translateNode(ifElse.ifScope)
            code += VmCodeInstruction(Opcode.JUMP, symbol = afterIfLabel)
            code += VmCodeLabel(elseLabel)
            code += translateNode(ifElse.elseScope)
            code += VmCodeLabel(afterIfLabel)
        } else {
            // only if part
            val afterIfLabel = createLabelName()
            code += VmCodeInstruction(branch, vmDt, reg1=conditionReg, symbol = afterIfLabel)
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
            code += VmCodeInstruction(Opcode.LOADM, vmDt, reg1=resultReg, value = address)
            code += VmCodeInstruction(operation, vmDt, reg1=resultReg)
            code += VmCodeInstruction(Opcode.STOREM, vmDt, reg1=resultReg, value = address)
        } else if(memory!=null) {
            val addressReg = vmRegisters.nextFree()
            code += expressionEval.translateExpression(memory.address, addressReg)
            code += VmCodeInstruction(Opcode.LOADI, vmDt, reg1=resultReg, reg2=addressReg)
            code += VmCodeInstruction(operation, vmDt, reg1=resultReg)
            code += VmCodeInstruction(Opcode.STOREI, vmDt, reg1=resultReg, reg2=addressReg)
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
        code += VmCodeInstruction(Opcode.DEC, vmDt, reg1=counterReg)
        code += VmCodeInstruction(Opcode.BNZ, vmDt, reg1=counterReg, symbol = repeatLabel)
        return code
    }

    private fun translate(jump: PtJump): VmCodeChunk {
        val code = VmCodeChunk()
        if(jump.address!=null)
            throw AssemblyError("cannot jump to memory location in the vm target")
        code += if(jump.generatedLabel!=null)
            VmCodeInstruction(Opcode.JUMP, symbol = listOf(jump.generatedLabel!!))
        else if(jump.identifier!=null)
            VmCodeInstruction(Opcode.JUMP, symbol = jump.identifier!!.targetName)
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
            code += VmCodeInstruction(Opcode.STOREM, vmDt, reg1=resultRegister, value=address)
        }
        else if(array!=null) {
            val variable = array.variable.targetName
            var variableAddr = allocations.get(variable)
            val itemsize = program.memsizer.memorySize(array.type)
            val fixedIndex = (array.index as? PtNumber)?.number?.toInt()
            val vmDtArrayIdx = vmType(array.type)
            if(fixedIndex!=null) {
                variableAddr += fixedIndex*itemsize
                code += VmCodeInstruction(Opcode.STOREM, vmDtArrayIdx, reg1 = resultRegister, value=variableAddr)
            } else {
                val indexReg = vmRegisters.nextFree()
                code += expressionEval.translateExpression(array.index, indexReg)
                code += VmCodeInstruction(Opcode.STOREX, vmDtArrayIdx, reg1 = resultRegister, reg2=indexReg, value=variableAddr)
            }
        }
        else if(memory!=null) {
            if(memory.address is PtNumber) {
                code += VmCodeInstruction(Opcode.STOREM, vmDt, reg1=resultRegister, value=(memory.address as PtNumber).number.toInt())
            } else {
                val addressRegister = vmRegisters.nextFree()
                code += expressionEval.translateExpression(assignment.value, addressRegister)
                code += VmCodeInstruction(Opcode.STOREI, vmDt, reg1=resultRegister, reg2=addressRegister)
            }
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
        code += VmCodeInstruction(Opcode.RETURN)
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
