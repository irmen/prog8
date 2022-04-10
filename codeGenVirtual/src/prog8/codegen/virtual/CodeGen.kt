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

    override fun compileToAssembly(): IAssemblyProgram? {
        val vmprog = AssemblyProgram(program.name, allocations)

        if(!options.dontReinitGlobals) {
            // collect global variables initializers
            program.allBlocks().forEach {
                val code = VmCodeChunk()
                it.children.filterIsInstance<PtAssignment>().forEach { assign -> code += translate(assign) }
                vmprog.addGlobalInits(code)
            }
        }

        for (block in program.allBlocks()) {
            vmprog.addBlock(translate(block))
        }

        println("Vm codegen: amount of vm registers=${vmRegisters.peekNext()}")

        return vmprog
    }


    internal fun translateNode(node: PtNode): VmCodeChunk {
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
            is PtWhen -> translate(node)
            is PtPipe -> expressionEval.translate(node, 0)
            is PtForLoop -> translate(node)
            is PtIfElse -> translate(node)
            is PtPostIncrDecr -> translate(node)
            is PtRepeatLoop -> translate(node)
            is PtLabel -> VmCodeChunk(VmCodeLabel(node.scopedName))
            is PtBreakpoint -> VmCodeChunk(VmCodeInstruction(Opcode.BREAKPOINT))
            is PtConditionalBranch -> translate(node)
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
            else -> TODO("missing codegen for $node")
        }
        if(code.lines.isNotEmpty() && node.position.line!=0)
            code.lines.add(0, VmCodeComment(node.position.toString()))
        return code
    }

    private fun translate(branch: PtConditionalBranch): VmCodeChunk {
        val code = VmCodeChunk()
        val elseLabel = createLabelName()
        when(branch.condition) {
            BranchCondition.CS -> {
                code += VmCodeInstruction(Opcode.BSTCC, symbol = elseLabel)
            }
            BranchCondition.CC -> {
                code += VmCodeInstruction(Opcode.BSTCS, symbol = elseLabel)
            }
            else -> {
                throw AssemblyError("conditional branch ${branch.condition} not supported in vm target due to lack of cpu flags ${branch.position}")
            }
        }
        code += translateNode(branch.trueScope)
        if(branch.falseScope.children.isNotEmpty()) {
            val endLabel = createLabelName()
            code += VmCodeInstruction(Opcode.JUMP, symbol = endLabel)
            code += VmCodeLabel(elseLabel)
            code += translateNode(branch.falseScope)
            code += VmCodeLabel(endLabel)
        } else {
            code += VmCodeLabel(elseLabel)
        }
        return code
    }

    private fun translate(whenStmt: PtWhen): VmCodeChunk {
        if(whenStmt.choices.children.isEmpty())
            return VmCodeChunk()
        val code = VmCodeChunk()
        val valueReg = vmRegisters.nextFree()
        val choiceReg = vmRegisters.nextFree()
        val valueDt = vmType(whenStmt.value.type)
        code += expressionEval.translateExpression(whenStmt.value, valueReg)
        val choices = whenStmt.choices.children.map {it as PtWhenChoice }
        val endLabel = createLabelName()
        for (choice in choices) {
            if(choice.isElse) {
                code += translateNode(choice.statements)
            } else {
                val skipLabel = createLabelName()
                val values = choice.values.children.map {it as PtNumber}
                if(values.size==1) {
                    code += VmCodeInstruction(Opcode.LOAD, valueDt, reg1=choiceReg, value=values[0].number.toInt())
                    code += VmCodeInstruction(Opcode.BNE, valueDt, reg1=valueReg, reg2=choiceReg, symbol = skipLabel)
                    code += translateNode(choice.statements)
                    if(choice.statements.children.last() !is PtReturn)
                        code += VmCodeInstruction(Opcode.JUMP, symbol = endLabel)
                } else {
                    val matchLabel = createLabelName()
                    for (value in values) {
                        code += VmCodeInstruction(Opcode.LOAD, valueDt, reg1=choiceReg, value=value.number.toInt())
                        code += VmCodeInstruction(Opcode.BEQ, valueDt, reg1=valueReg, reg2=choiceReg, symbol = matchLabel)
                    }
                    code += VmCodeInstruction(Opcode.JUMP, symbol = skipLabel)
                    code += VmCodeLabel(matchLabel)
                    code += translateNode(choice.statements)
                    if(choice.statements.children.last() !is PtReturn)
                        code += VmCodeInstruction(Opcode.JUMP, symbol = endLabel)
                }
                code += VmCodeLabel(skipLabel)
            }
        }
        code += VmCodeLabel(endLabel)
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
        val loopLabel = createLabelName()
        val loopvarAddress = allocations.get(loopvar.scopedName)
        val indexReg = vmRegisters.nextFree()
        val loopvarDt = vmType(loopvar.dt)
        val iterable = forLoop.iterable as PtRange
        val step = iterable.step.number.toInt()
        val rangeStart = (iterable.from as PtNumber).number.toInt()
        val rangeEndUntyped = (iterable.to as PtNumber).number.toInt() + step
        if(step==0)
            throw AssemblyError("step 0")
        if(step>0 && rangeEndUntyped<rangeStart || step<0 && rangeEndUntyped>rangeStart)
            throw AssemblyError("empty range")
        val rangeEndWrapped = if(loopvarDt==VmDataType.BYTE) rangeEndUntyped and 255 else rangeEndUntyped and 65535
        val code = VmCodeChunk()
        val endvalueReg: Int
        if(rangeEndWrapped!=0) {
            endvalueReg = vmRegisters.nextFree()
            code += VmCodeInstruction(Opcode.LOAD, loopvarDt, reg1 = endvalueReg, value = rangeEndWrapped)
        } else {
            endvalueReg = 0
        }
        code += VmCodeInstruction(Opcode.LOAD, loopvarDt, reg1=indexReg, value=rangeStart)
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
        code += if(rangeEndWrapped==0) {
            VmCodeInstruction(Opcode.BNZ, loopvarDt, reg1 = indexReg, symbol = loopLabel)
        } else {
            VmCodeInstruction(Opcode.BNE, loopvarDt, reg1 = indexReg, reg2 = endvalueReg, symbol = loopLabel)
        }
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

    internal fun multiplyByConst(dt: VmDataType, reg: Int, factor: Int): VmCodeChunk {
        require(factor>=0)
        val code = VmCodeChunk()
        if(factor==1)
            return code
        val pow2 = powersOfTwo.indexOf(factor)
        if(pow2==1) {
            // just shift 1 bit
            code += VmCodeInstruction(Opcode.LSL, dt, reg1=reg)
        }
        else if(pow2>=1) {
            // just shift multiple bits
            val pow2reg = vmRegisters.nextFree()
            code += VmCodeInstruction(Opcode.LOAD, dt, reg1=pow2reg, value=pow2)
            code += VmCodeInstruction(Opcode.LSLM, dt, reg1=reg, reg2=reg, reg3=pow2reg)
        } else {
            if (factor == 0) {
                code += VmCodeInstruction(Opcode.LOAD, dt, reg1=reg, value=0)
            }
            else {
                val factorReg = vmRegisters.nextFree()
                code += VmCodeInstruction(Opcode.LOAD, dt, reg1=factorReg, value= factor)
                code += VmCodeInstruction(Opcode.MUL, dt, reg1=reg, reg2=reg, reg3=factorReg)
            }
        }
        return code
    }

    private fun translate(ifElse: PtIfElse): VmCodeChunk {
        var branch = Opcode.BZ
        var condition = ifElse.condition

        val cond = ifElse.condition as? PtBinaryExpression
        if(cond!=null && constValue(cond.right)==0.0) {
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
            val variable = array.variable.targetName
            var variableAddr = allocations.get(variable)
            val itemsize = program.memsizer.memorySize(array.type)
            val fixedIndex = constIntValue(array.index)
            val memOp = when(postIncrDecr.operator) {
                "++" -> Opcode.INCM
                "--" -> Opcode.DECM
                else -> throw AssemblyError("weird operator")
            }
            if(fixedIndex!=null) {
                variableAddr += fixedIndex*itemsize
                code += VmCodeInstruction(memOp, vmDt, value=variableAddr)
            } else {
                val indexReg = vmRegisters.nextFree()
                code += expressionEval.translateExpression(array.index, indexReg)
                code += VmCodeInstruction(Opcode.LOADX, vmDt, reg1=resultReg, reg2=indexReg, value=variableAddr)
                code += VmCodeInstruction(operation, vmDt, reg1=resultReg)
                code += VmCodeInstruction(Opcode.STOREX, vmDt, reg1=resultReg, reg2=indexReg, value=variableAddr)
            }
        } else
            throw AssemblyError("weird assigntarget")

        return code
    }

    private fun translate(repeat: PtRepeatLoop): VmCodeChunk {
        when (constIntValue(repeat.count)) {
            0 -> return VmCodeChunk()
            1 -> return translateGroup(repeat.children)
            256 -> {
                // 256 iterations can still be done with just a byte counter if you set it to zero as starting value.
                repeat.children[0] = PtNumber(DataType.UBYTE, 0.0, repeat.count.position)
            }
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
        // TODO can in-place assignments be optimized more?

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
            val fixedIndex = constIntValue(array.index)
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
        // TODO actually inline subroutines marked as inline (but at this time only asmsub can be inline)
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
        return listOf("prog8_label_gen_$labelSequenceNumber")
    }

    internal fun translateBuiltinFunc(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk =
        builtinFuncGen.translate(call, resultRegister)
}
