package prog8.codegen.virtual

import prog8.code.StStaticVariable
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.vm.Opcode
import prog8.vm.VmDataType
import kotlin.math.pow


internal class VmRegisterPool {
    private var firstFree: Int=3        // integer registers 0,1,2 are reserved
    private var firstFreeFloat: Int=0

    fun peekNext() = firstFree
    fun peekNextFloat() = firstFreeFloat

    fun nextFree(): Int {
        val result = firstFree
        firstFree++
        if(firstFree>65535)
            throw AssemblyError("out of virtual registers (int)")
        return result
    }

    fun nextFreeFloat(): Int {
        val result = firstFreeFloat
        firstFreeFloat++
        if(firstFreeFloat>65535)
            throw AssemblyError("out of virtual registers (fp)")
        return result
    }
}


class CodeGen(internal val program: PtProgram,
              internal val symbolTable: SymbolTable,
              internal val options: CompilationOptions,
              internal val errors: IErrorReporter
): IAssemblyGenerator {

    internal val allocations = VariableAllocator(symbolTable, program)
    private val expressionEval = ExpressionGen(this)
    private val builtinFuncGen = BuiltinFuncGen(this, expressionEval)
    private val assignmentGen = AssignmentGen(this, expressionEval)
    internal val vmRegisters = VmRegisterPool()

    override fun compileToAssembly(): IAssemblyProgram? {
        val vmprog = AssemblyProgram(program.name, allocations)

        if(!options.dontReinitGlobals) {
            // collect global variables initializers
            program.allBlocks().forEach {
                val code = VmCodeChunk()
                it.children.filterIsInstance<PtAssignment>().forEach { assign -> code += assignmentGen.translate(assign) }
                vmprog.addGlobalInits(code)
            }
        }

        if(options.symbolDefs.isNotEmpty())
            throw AssemblyError("virtual target doesn't support symbols defined on the commandline")
        if(options.evalStackBaseAddress!=null)
            throw AssemblyError("virtual target doesn't use eval-stack")

        for (block in program.allBlocks()) {
            vmprog.addBlock(translate(block))
        }

        if(options.optimize) {
            val optimizer = VmPeepholeOptimizer(vmprog, allocations)
            optimizer.optimize()
        }

        println("Vm codegen: virtual registers=${vmRegisters.peekNext()} memory usage=${allocations.freeMem}")

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
            is PtAssignment -> assignmentGen.translate(node)
            is PtNodeGroup -> translateGroup(node.children)
            is PtBuiltinFunctionCall -> translateBuiltinFunc(node, 0)
            is PtFunctionCall -> expressionEval.translate(node, 0, 0)
            is PtNop -> VmCodeChunk()
            is PtReturn -> translate(node)
            is PtJump -> translate(node)
            is PtWhen -> translate(node)
            is PtForLoop -> translate(node)
            is PtIfElse -> translate(node)
            is PtPostIncrDecr -> translate(node)
            is PtRepeatLoop -> translate(node)
            is PtLabel -> VmCodeChunk(VmCodeLabel(node.scopedName))
            is PtBreakpoint -> VmCodeChunk(VmCodeInstruction(Opcode.BREAKPOINT))
            is PtConditionalBranch -> translate(node)
            is PtInlineAssembly -> VmCodeChunk(VmCodeInlineAsm(node.assembly))
            is PtIncludeBinary -> VmCodeChunk(VmCodeInlineBinary(node.file, node.offset, node.length))
            is PtAsmSub -> TODO("asmsub not yet supported on virtual machine target ${node.position}")
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
            is PtString -> throw AssemblyError("should not occur as separate statement node ${node.position}")
            else -> TODO("missing codegen for $node")
        }
        if(code.lines.isNotEmpty() && node.position.line!=0)
            code.lines.add(0, VmCodeComment(node.position.toString()))
        return code
    }

    private fun translate(branch: PtConditionalBranch): VmCodeChunk {
        val code = VmCodeChunk()
        val elseLabel = createLabelName()
        // note that the branch opcode used is the opposite as the branch condition, because the generated code jumps to the 'else' part
        code += when(branch.condition) {
            BranchCondition.CS -> VmCodeInstruction(Opcode.BSTCC, labelSymbol = elseLabel)
            BranchCondition.CC -> VmCodeInstruction(Opcode.BSTCS, labelSymbol = elseLabel)
            BranchCondition.EQ, BranchCondition.Z -> VmCodeInstruction(Opcode.BSTNE, labelSymbol = elseLabel)
            BranchCondition.NE, BranchCondition.NZ -> VmCodeInstruction(Opcode.BSTEQ, labelSymbol = elseLabel)
            BranchCondition.MI, BranchCondition.NEG -> VmCodeInstruction(Opcode.BSTPOS, labelSymbol = elseLabel)
            BranchCondition.PL, BranchCondition.POS -> VmCodeInstruction(Opcode.BSTNEG, labelSymbol = elseLabel)
            BranchCondition.VC,
            BranchCondition.VS -> throw AssemblyError("conditional branch ${branch.condition} not supported in vm target due to lack of cpu V flag ${branch.position}")
        }
        code += translateNode(branch.trueScope)
        if(branch.falseScope.children.isNotEmpty()) {
            val endLabel = createLabelName()
            code += VmCodeInstruction(Opcode.JUMP, labelSymbol = endLabel)
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
        code += expressionEval.translateExpression(whenStmt.value, valueReg, -1)
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
                    code += VmCodeInstruction(Opcode.BNE, valueDt, reg1=valueReg, reg2=choiceReg, labelSymbol = skipLabel)
                    code += translateNode(choice.statements)
                    if(choice.statements.children.last() !is PtReturn)
                        code += VmCodeInstruction(Opcode.JUMP, labelSymbol = endLabel)
                } else {
                    val matchLabel = createLabelName()
                    for (value in values) {
                        code += VmCodeInstruction(Opcode.LOAD, valueDt, reg1=choiceReg, value=value.number.toInt())
                        code += VmCodeInstruction(Opcode.BEQ, valueDt, reg1=valueReg, reg2=choiceReg, labelSymbol = matchLabel)
                    }
                    code += VmCodeInstruction(Opcode.JUMP, labelSymbol = skipLabel)
                    code += VmCodeLabel(matchLabel)
                    code += translateNode(choice.statements)
                    if(choice.statements.children.last() !is PtReturn)
                        code += VmCodeInstruction(Opcode.JUMP, labelSymbol = endLabel)
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
                val tmpReg = vmRegisters.nextFree()
                val loopLabel = createLabelName()
                val endLabel = createLabelName()
                if(iterableVar.dt==DataType.STR) {
                    // iterate over a zero-terminated string
                    code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=indexReg, value=0)
                    code += VmCodeLabel(loopLabel)
                    code += VmCodeInstruction(Opcode.LOADX, VmDataType.BYTE, reg1=tmpReg, reg2=indexReg, value = arrayAddress)
                    code += VmCodeInstruction(Opcode.BZ, VmDataType.BYTE, reg1=tmpReg, labelSymbol = endLabel)
                    code += VmCodeInstruction(Opcode.STOREM, VmDataType.BYTE, reg1=tmpReg, value = loopvarAddress)
                    code += translateNode(forLoop.statements)
                    code += VmCodeInstruction(Opcode.INC, VmDataType.BYTE, reg1=indexReg)
                    code += VmCodeInstruction(Opcode.JUMP, labelSymbol = loopLabel)
                    code += VmCodeLabel(endLabel)
                } else {
                    // iterate over array
                    val elementDt = ArrayToElementTypes.getValue(iterable.type)
                    val elementSize = program.memsizer.memorySize(elementDt)
                    val lengthBytes = iterableVar.length!! * elementSize
                    if(lengthBytes<256) {
                        val lengthReg = vmRegisters.nextFree()
                        code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=indexReg, value=0)
                        code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=lengthReg, value=lengthBytes)
                        code += VmCodeLabel(loopLabel)
                        code += VmCodeInstruction(Opcode.LOADX, vmType(elementDt), reg1=tmpReg, reg2=indexReg, value=arrayAddress)
                        code += VmCodeInstruction(Opcode.STOREM, vmType(elementDt), reg1=tmpReg, value = loopvarAddress)
                        code += translateNode(forLoop.statements)
                        code += addConstReg(VmDataType.BYTE, indexReg, elementSize)
                        code += VmCodeInstruction(Opcode.BNE, VmDataType.BYTE, reg1=indexReg, reg2=lengthReg, labelSymbol = loopLabel)
                    } else if(lengthBytes==256) {
                        code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=indexReg, value=0)
                        code += VmCodeLabel(loopLabel)
                        code += VmCodeInstruction(Opcode.LOADX, vmType(elementDt), reg1=tmpReg, reg2=indexReg, value=arrayAddress)
                        code += VmCodeInstruction(Opcode.STOREM, vmType(elementDt), reg1=tmpReg, value = loopvarAddress)
                        code += translateNode(forLoop.statements)
                        code += addConstReg(VmDataType.BYTE, indexReg, elementSize)
                        code += VmCodeInstruction(Opcode.BNZ, VmDataType.BYTE, reg1=indexReg, labelSymbol = loopLabel)
                    } else {
                        throw AssemblyError("iterator length should never exceed 256")
                    }
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

        code += expressionEval.translateExpression(iterable.to, endvalueReg, -1)
        code += expressionEval.translateExpression(iterable.from, indexReg, -1)
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
        code += VmCodeInstruction(branchOpcode, loopvarDt, reg1=indexReg, reg2=endvalueReg, labelSymbol=loopLabel)
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
            endvalueReg = -1 // not used
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
            VmCodeInstruction(Opcode.BNZ, loopvarDt, reg1 = indexReg, labelSymbol = loopLabel)
        } else {
            VmCodeInstruction(Opcode.BNE, loopvarDt, reg1 = indexReg, reg2 = endvalueReg, labelSymbol = loopLabel)
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
                code += if(value>0) {
                    VmCodeInstruction(Opcode.ADD, dt, reg1 = reg, value=value)
                } else {
                    VmCodeInstruction(Opcode.SUB, dt, reg1 = reg, value=-value)
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
                    code += VmCodeInstruction(Opcode.ADDR, dt, reg1 = valueReg, reg2 = operandReg)
                    code += VmCodeInstruction(Opcode.STOREM, dt, reg1=valueReg, value=address.toInt())
                }
                else {
                    code += VmCodeInstruction(Opcode.LOADM, dt, reg1=valueReg, value=address.toInt())
                    code += VmCodeInstruction(Opcode.LOAD, dt, reg1=operandReg, value=-value)
                    code += VmCodeInstruction(Opcode.SUBR, dt, reg1 = valueReg, reg2 = operandReg)
                    code += VmCodeInstruction(Opcode.STOREM, dt, reg1=valueReg, value=address.toInt())
                }
            }
        }
        return code
    }

    internal fun multiplyByConstFloat(fpReg: Int, factor: Float): VmCodeChunk {
        val code = VmCodeChunk()
        if(factor==1f)
            return code
        code += if(factor==0f) {
            VmCodeInstruction(Opcode.LOAD, VmDataType.FLOAT, fpReg1 = fpReg, fpValue = 0f)
        } else {
            VmCodeInstruction(Opcode.MUL, VmDataType.FLOAT, fpReg1 = fpReg, fpValue=factor)
        }
        return code
    }

    internal fun multiplyByConstFloatInplace(address: Int, factor: Float): VmCodeChunk {
        val code = VmCodeChunk()
        if(factor==1f)
            return code
        if(factor==0f) {
            code += VmCodeInstruction(Opcode.STOREZM, VmDataType.FLOAT, value = address)
        } else {
            val factorReg = vmRegisters.nextFreeFloat()
            code += VmCodeInstruction(Opcode.LOAD, VmDataType.FLOAT, fpReg1=factorReg, fpValue = factor)
            code += VmCodeInstruction(Opcode.MULM, VmDataType.FLOAT, fpReg1 = factorReg, value = address)
        }
        return code
    }

    internal val powersOfTwo = (0..16).map { 2.0.pow(it.toDouble()).toInt() }

    internal fun multiplyByConst(dt: VmDataType, reg: Int, factor: Int): VmCodeChunk {
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
            code += VmCodeInstruction(Opcode.LSLN, dt, reg1=reg, reg2=pow2reg)
        } else {
            code += if (factor == 0) {
                VmCodeInstruction(Opcode.LOAD, dt, reg1=reg, value=0)
            } else {
                VmCodeInstruction(Opcode.MUL, dt, reg1=reg, value=factor)
            }
        }
        return code
    }

    internal fun multiplyByConstInplace(dt: VmDataType, address: Int, factor: Int): VmCodeChunk {
        val code = VmCodeChunk()
        if(factor==1)
            return code
        val pow2 = powersOfTwo.indexOf(factor)
        if(pow2==1) {
            // just shift 1 bit
            code += VmCodeInstruction(Opcode.LSLM, dt, value = address)
        }
        else if(pow2>=1) {
            // just shift multiple bits
            val pow2reg = vmRegisters.nextFree()
            code += VmCodeInstruction(Opcode.LOAD, dt, reg1=pow2reg, value=pow2)
            code += VmCodeInstruction(Opcode.LSLNM, dt, reg1=pow2reg, value=address)
        } else {
            if (factor == 0) {
                code += VmCodeInstruction(Opcode.STOREZM, dt, value=address)
            }
            else {
                val factorReg = vmRegisters.nextFree()
                code += VmCodeInstruction(Opcode.LOAD, dt, reg1=factorReg, value = factor)
                code += VmCodeInstruction(Opcode.MULM, dt, reg1=factorReg, value = address)
            }
        }
        return code
    }

    internal fun divideByConstFloat(fpReg: Int, factor: Float): VmCodeChunk {
        val code = VmCodeChunk()
        if(factor==1f)
            return code
        code += if(factor==0f) {
            VmCodeInstruction(Opcode.LOAD, VmDataType.FLOAT, fpReg1 = fpReg, fpValue = Float.MAX_VALUE)
        } else {
            VmCodeInstruction(Opcode.DIVS, VmDataType.FLOAT, fpReg1 = fpReg, fpValue=factor)
        }
        return code
    }

    internal fun divideByConstFloatInplace(address: Int, factor: Float): VmCodeChunk {
        val code = VmCodeChunk()
        if(factor==1f)
            return code
        if(factor==0f) {
            val maxvalueReg = vmRegisters.nextFreeFloat()
            code += VmCodeInstruction(Opcode.LOAD, VmDataType.FLOAT, fpReg1 = maxvalueReg, fpValue = Float.MAX_VALUE)
            code += VmCodeInstruction(Opcode.STOREM, VmDataType.FLOAT, fpReg1 = maxvalueReg, value=address)
        } else {
            val factorReg = vmRegisters.nextFreeFloat()
            code += VmCodeInstruction(Opcode.LOAD, VmDataType.FLOAT, fpReg1=factorReg, fpValue = factor)
            code += VmCodeInstruction(Opcode.DIVSM, VmDataType.FLOAT, fpReg1 = factorReg, value=address)
        }
        return code
    }

    internal fun divideByConst(dt: VmDataType, reg: Int, factor: Int, signed: Boolean): VmCodeChunk {
        val code = VmCodeChunk()
        if(factor==1)
            return code
        val pow2 = powersOfTwo.indexOf(factor)
        if(pow2==1) {
            // just shift 1 bit
            code += if(signed)
                VmCodeInstruction(Opcode.ASR, dt, reg1=reg)
            else
                VmCodeInstruction(Opcode.LSR, dt, reg1=reg)
        }
        else if(pow2>=1) {
            // just shift multiple bits
            val pow2reg = vmRegisters.nextFree()
            code += VmCodeInstruction(Opcode.LOAD, dt, reg1=pow2reg, value=pow2)
            code += if(signed)
                VmCodeInstruction(Opcode.ASRN, dt, reg1=reg, reg2=pow2reg)
            else
                VmCodeInstruction(Opcode.LSRN, dt, reg1=reg, reg2=pow2reg)
        } else {
            code += if (factor == 0) {
                VmCodeInstruction(Opcode.LOAD, dt, reg1=reg, value=0xffff)
            } else {
                if(signed)
                    VmCodeInstruction(Opcode.DIVS, dt, reg1=reg, value=factor)
                else
                    VmCodeInstruction(Opcode.DIV, dt, reg1=reg, value=factor)
            }
        }
        return code
    }

    internal fun divideByConstInplace(dt: VmDataType, address: Int, factor: Int, signed: Boolean): VmCodeChunk {
        val code = VmCodeChunk()
        if(factor==1)
            return code
        val pow2 = powersOfTwo.indexOf(factor)
        if(pow2==1) {
            // just shift 1 bit
            code += if(signed)
                VmCodeInstruction(Opcode.ASRM, dt, value=address)
            else
                VmCodeInstruction(Opcode.LSRM, dt, value=address)
        }
        else if(pow2>=1) {
            // just shift multiple bits
            val pow2reg = vmRegisters.nextFree()
            code += VmCodeInstruction(Opcode.LOAD, dt, reg1=pow2reg, value=pow2)
            code += if(signed)
                VmCodeInstruction(Opcode.ASRNM, dt, reg1=pow2reg, value=address)
            else
                VmCodeInstruction(Opcode.LSRNM, dt, reg1=pow2reg, value=address)
        } else {
            if (factor == 0) {
                val reg = vmRegisters.nextFree()
                code += VmCodeInstruction(Opcode.LOAD, dt, reg1=reg, value=0xffff)
                code += VmCodeInstruction(Opcode.STOREM, dt, reg1=reg, value=address)
            }
            else {
                val factorReg = vmRegisters.nextFree()
                code += VmCodeInstruction(Opcode.LOAD, dt, reg1=factorReg, value= factor)
                code += if(signed)
                    VmCodeInstruction(Opcode.DIVSM, dt, reg1=factorReg, value=address)
                else
                    VmCodeInstruction(Opcode.DIVM, dt, reg1=factorReg, value=address)
            }
        }
        return code
    }

    private fun translate(ifElse: PtIfElse): VmCodeChunk {
        if(ifElse.condition.operator !in ComparisonOperators)
            throw AssemblyError("if condition should only be a binary comparison expression")

        val signed = ifElse.condition.left.type in arrayOf(DataType.BYTE, DataType.WORD, DataType.FLOAT)
        val vmDt = vmType(ifElse.condition.left.type)
        val code = VmCodeChunk()

        fun translateNonZeroComparison(): VmCodeChunk {
            val elseBranch = when(ifElse.condition.operator) {
                "==" -> Opcode.BNE
                "!=" -> Opcode.BEQ
                "<" -> if(signed) Opcode.BGES else Opcode.BGE
                ">" -> if(signed) Opcode.BLES else Opcode.BLE
                "<=" -> if(signed) Opcode.BGTS else Opcode.BGT
                ">=" -> if(signed) Opcode.BLTS else Opcode.BLT
                else -> throw AssemblyError("invalid comparison operator")
            }

            val leftReg = vmRegisters.nextFree()
            val rightReg = vmRegisters.nextFree()
            code += expressionEval.translateExpression(ifElse.condition.left, leftReg, -1)
            code += expressionEval.translateExpression(ifElse.condition.right, rightReg, -1)
            if(ifElse.elseScope.children.isNotEmpty()) {
                // if and else parts
                val elseLabel = createLabelName()
                val afterIfLabel = createLabelName()
                code += VmCodeInstruction(elseBranch, vmDt, reg1=leftReg, reg2=rightReg, labelSymbol = elseLabel)
                code += translateNode(ifElse.ifScope)
                code += VmCodeInstruction(Opcode.JUMP, labelSymbol = afterIfLabel)
                code += VmCodeLabel(elseLabel)
                code += translateNode(ifElse.elseScope)
                code += VmCodeLabel(afterIfLabel)
            } else {
                // only if part
                val afterIfLabel = createLabelName()
                code += VmCodeInstruction(elseBranch, vmDt, reg1=leftReg, reg2=rightReg, labelSymbol = afterIfLabel)
                code += translateNode(ifElse.ifScope)
                code += VmCodeLabel(afterIfLabel)
            }
            return code
        }

        fun translateZeroComparison(): VmCodeChunk {
            fun equalOrNotEqualZero(elseBranch: Opcode): VmCodeChunk {
                val leftReg = vmRegisters.nextFree()
                code += expressionEval.translateExpression(ifElse.condition.left, leftReg, -1)
                if(ifElse.elseScope.children.isNotEmpty()) {
                    // if and else parts
                    val elseLabel = createLabelName()
                    val afterIfLabel = createLabelName()
                    code += VmCodeInstruction(elseBranch, vmDt, reg1=leftReg, labelSymbol = elseLabel)
                    code += translateNode(ifElse.ifScope)
                    code += VmCodeInstruction(Opcode.JUMP, labelSymbol = afterIfLabel)
                    code += VmCodeLabel(elseLabel)
                    code += translateNode(ifElse.elseScope)
                    code += VmCodeLabel(afterIfLabel)
                } else {
                    // only if part
                    val afterIfLabel = createLabelName()
                    code += VmCodeInstruction(elseBranch, vmDt, reg1=leftReg, labelSymbol = afterIfLabel)
                    code += translateNode(ifElse.ifScope)
                    code += VmCodeLabel(afterIfLabel)
                }
                return code
            }

            return when (ifElse.condition.operator) {
                "==" -> {
                    // if X==0 ...   so we just branch on left expr is Not-zero.
                    equalOrNotEqualZero(Opcode.BNZ)
                }
                "!=" -> {
                    // if X!=0 ...   so we just branch on left expr is Zero.
                    equalOrNotEqualZero(Opcode.BZ)
                }
                else -> {
                    // another comparison against 0, just use regular codegen for this.
                    translateNonZeroComparison()
                }
            }
        }

        return if(constValue(ifElse.condition.right)==0.0)
            translateZeroComparison()
        else
            translateNonZeroComparison()
    }


    private fun translate(postIncrDecr: PtPostIncrDecr): VmCodeChunk {
        val code = VmCodeChunk()
        val operationMem: Opcode
        val operationRegister: Opcode
        when(postIncrDecr.operator) {
            "++" -> {
                operationMem = Opcode.INCM
                operationRegister = Opcode.INC
            }
            "--" -> {
                operationMem = Opcode.DECM
                operationRegister = Opcode.DEC
            }
            else -> throw AssemblyError("weird operator")
        }
        val ident = postIncrDecr.target.identifier
        val memory = postIncrDecr.target.memory
        val array = postIncrDecr.target.array
        val vmDt = vmType(postIncrDecr.target.type)
        if(ident!=null) {
            val address = allocations.get(ident.targetName)
            code += VmCodeInstruction(operationMem, vmDt, value = address)
        } else if(memory!=null) {
            if(memory.address is PtNumber) {
                val address = (memory.address as PtNumber).number.toInt()
                code += VmCodeInstruction(operationMem, vmDt, value = address)
            } else {
                val incReg = vmRegisters.nextFree()
                val addressReg = vmRegisters.nextFree()
                code += expressionEval.translateExpression(memory.address, addressReg, -1)
                code += VmCodeInstruction(Opcode.LOADI, vmDt, reg1 = incReg, reg2 = addressReg)
                code += VmCodeInstruction(operationRegister, vmDt, reg1 = incReg)
                code += VmCodeInstruction(Opcode.STOREI, vmDt, reg1 = incReg, reg2 = addressReg)
            }
        } else if (array!=null) {
            val variable = array.variable.targetName
            var variableAddr = allocations.get(variable)
            val itemsize = program.memsizer.memorySize(array.type)
            val fixedIndex = constIntValue(array.index)
            if(fixedIndex!=null) {
                variableAddr += fixedIndex*itemsize
                code += VmCodeInstruction(operationMem, vmDt, value=variableAddr)
            } else {
                val incReg = vmRegisters.nextFree()
                val indexReg = vmRegisters.nextFree()
                code += expressionEval.translateExpression(array.index, indexReg, -1)
                code += VmCodeInstruction(Opcode.LOADX, vmDt, reg1=incReg, reg2=indexReg, value=variableAddr)
                code += VmCodeInstruction(operationRegister, vmDt, reg1=incReg)
                code += VmCodeInstruction(Opcode.STOREX, vmDt, reg1=incReg, reg2=indexReg, value=variableAddr)
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
        code += expressionEval.translateExpression(repeat.count, counterReg, -1)
        val repeatLabel = createLabelName()
        code += VmCodeLabel(repeatLabel)
        code += translateNode(repeat.statements)
        code += VmCodeInstruction(Opcode.DEC, vmDt, reg1=counterReg)
        code += VmCodeInstruction(Opcode.BNZ, vmDt, reg1=counterReg, labelSymbol = repeatLabel)
        return code
    }

    private fun translate(jump: PtJump): VmCodeChunk {
        val code = VmCodeChunk()
        if(jump.address!=null)
            throw AssemblyError("cannot jump to memory location in the vm target")
        code += if(jump.generatedLabel!=null)
            VmCodeInstruction(Opcode.JUMP, labelSymbol = listOf(jump.generatedLabel!!))
        else if(jump.identifier!=null)
            VmCodeInstruction(Opcode.JUMP, labelSymbol = jump.identifier!!.targetName)
        else
            throw AssemblyError("weird jump")
        return code
    }

    private fun translateGroup(group: List<PtNode>): VmCodeChunk {
        val code = VmCodeChunk()
        group.forEach { code += translateNode(it) }
        return code
    }

    private fun translate(ret: PtReturn): VmCodeChunk {
        val code = VmCodeChunk()
        val value = ret.value
        if(value!=null) {
            // Call Convention: return value is always returned in r0 (or fr0 if float)
            code += if(value.type==DataType.FLOAT)
                expressionEval.translateExpression(value, -1, 0)
            else
                expressionEval.translateExpression(value, 0, -1)
        }
        code += VmCodeInstruction(Opcode.RETURN)
        return code
    }

    private fun translate(sub: PtSub): VmCodeChunk {
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
            DataType.BOOL,
            DataType.UBYTE,
            DataType.BYTE -> VmDataType.BYTE
            DataType.UWORD,
            DataType.WORD -> VmDataType.WORD
            DataType.FLOAT -> VmDataType.FLOAT
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

    internal fun isZero(expression: PtExpression): Boolean = expression is PtNumber && expression.number==0.0

    internal fun isOne(expression: PtExpression): Boolean = expression is PtNumber && expression.number==1.0
}

