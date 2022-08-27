package prog8.codegen.virtual

import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.code.core.SignedDatatypes
import prog8.intermediate.Opcode
import prog8.intermediate.VmDataType

internal class AssignmentGen(private val codeGen: CodeGen, private val expressionEval: ExpressionGen) {

    internal fun translate(assignment: PtAssignment): VmCodeChunk {
        if(assignment.target.children.single() is PtMachineRegister)
            throw AssemblyError("assigning to a register should be done by just evaluating the expression into resultregister")

        return if (assignment.isInplaceAssign)
            translateInplaceAssign(assignment)
        else
            translateRegularAssign(assignment)
    }

    private fun translateInplaceAssign(assignment: PtAssignment): VmCodeChunk {
        val ident = assignment.target.identifier
        val memory = assignment.target.memory
        val array = assignment.target.array

        return if(ident!=null) {
            val address = codeGen.allocations.get(ident.targetName)
            assignSelfInMemory(address, assignment.value, assignment)
        } else if(memory != null) {
            if(memory.address is PtNumber)
                assignSelfInMemory((memory.address as PtNumber).number.toInt(), assignment.value, assignment)
            else
                fallbackAssign(assignment)
        } else if(array!=null) {
            // NOTE: naive fallback assignment here will sometimes generate code that loads the index value multiple times
            // in a register. It's way too much work to optimize that here - instead, we trust that the generated IL assembly
            // will be optimized later and have the double assignments removed.
            fallbackAssign(assignment)
        } else {
            fallbackAssign(assignment)
        }
    }

    private fun assignSelfInMemory(
        address: Int,
        value: PtExpression,
        origAssign: PtAssignment
    ): VmCodeChunk {
        val vmDt = codeGen.vmType(value.type)
        val code = VmCodeChunk()
        when(value) {
            is PtIdentifier -> return code // do nothing, x=x null assignment.
            is PtMachineRegister -> return code // do nothing, reg=reg null assignment
            is PtPrefix -> return inplacePrefix(value.operator, vmDt, address)
            is PtBinaryExpression -> return inplaceBinexpr(value.operator, value.right, vmDt, value.type in SignedDatatypes, address, origAssign)
            is PtMemoryByte -> {
                return if (!codeGen.options.compTarget.machine.isIOAddress(address.toUInt()))
                    code // do nothing, mem=mem null assignment.
                else {
                    // read and write a (i/o) memory location to itself.
                    val tempReg = codeGen.vmRegisters.nextFree()
                    code += VmCodeInstruction(Opcode.LOADM, vmDt, reg1 = tempReg, value = address)
                    code += VmCodeInstruction(Opcode.STOREM, vmDt, reg1 = tempReg, value = address)
                    code
                }
            }
            else -> return fallbackAssign(origAssign)
        }

    }

    private fun fallbackAssign(origAssign: PtAssignment): VmCodeChunk {
        if (codeGen.options.slowCodegenWarnings)
            codeGen.errors.warn("indirect code for in-place assignment", origAssign.position)
        return translateRegularAssign(origAssign)
    }

    private fun inplaceBinexpr(
        operator: String,
        operand: PtExpression,
        vmDt: VmDataType,
        signed: Boolean,
        address: Int,
        origAssign: PtAssignment
    ): VmCodeChunk {
        when(operator) {
            "+" -> return expressionEval.operatorPlusInplace(address, vmDt, operand)
            "-" -> return expressionEval.operatorMinusInplace(address, vmDt, operand)
            "*" -> return expressionEval.operatorMultiplyInplace(address, vmDt, operand)
            "/" -> return expressionEval.operatorDivideInplace(address, vmDt, signed, operand)
            "|" -> return expressionEval.operatorOrInplace(address, vmDt, operand)
            "&" -> return expressionEval.operatorAndInplace(address, vmDt, operand)
            "^" -> return expressionEval.operatorXorInplace(address, vmDt, operand)
            "<<" -> return expressionEval.operatorShiftLeftInplace(address, vmDt, operand)
            ">>" -> return expressionEval.operatorShiftRightInplace(address, vmDt, signed, operand)
            else -> {}
        }
        return fallbackAssign(origAssign)
    }

    private fun inplacePrefix(operator: String, vmDt: VmDataType, address: Int): VmCodeChunk {
        val code= VmCodeChunk()
        when(operator) {
            "+" -> { }
            "-" -> {
                code += VmCodeInstruction(Opcode.NEGM, vmDt, value = address)
            }
            "~" -> {
                val regMask = codeGen.vmRegisters.nextFree()
                val mask = if(vmDt==VmDataType.BYTE) 0x00ff else 0xffff
                code += VmCodeInstruction(Opcode.LOAD, vmDt, reg1=regMask, value = mask)
                code += VmCodeInstruction(Opcode.XORM, vmDt, reg1=regMask, value = address)
            }
            else -> throw AssemblyError("weird prefix operator")
        }
        return code
    }

    private fun translateRegularAssign(assignment: PtAssignment): VmCodeChunk {
        // note: assigning array and string values is done via an explicit memcopy/stringcopy function call.
        val ident = assignment.target.identifier
        val memory = assignment.target.memory
        val array = assignment.target.array
        val vmDt = codeGen.vmType(assignment.value.type)

        val code = VmCodeChunk()
        var resultRegister = -1
        var resultFpRegister = -1
        val zero = codeGen.isZero(assignment.value)
        if(!zero) {
            // calculate the assignment value
            if (vmDt == VmDataType.FLOAT) {
                resultFpRegister = codeGen.vmRegisters.nextFreeFloat()
                code += expressionEval.translateExpression(assignment.value, -1, resultFpRegister)
            } else {
                resultRegister = if (assignment.value is PtMachineRegister) {
                    (assignment.value as PtMachineRegister).register
                } else {
                    val reg = codeGen.vmRegisters.nextFree()
                    code += expressionEval.translateExpression(assignment.value, reg, -1)
                    reg
                }
            }
        }
        if(ident!=null) {
            val address = codeGen.allocations.get(ident.targetName)
            code += if(zero) {
                VmCodeInstruction(Opcode.STOREZM, vmDt, value = address)
            } else {
                if (vmDt == VmDataType.FLOAT)
                    VmCodeInstruction(Opcode.STOREM, vmDt, fpReg1 = resultFpRegister, value = address)
                else
                    VmCodeInstruction(Opcode.STOREM, vmDt, reg1 = resultRegister, value = address)
            }
        }
        else if(array!=null) {
            val variable = array.variable.targetName
            var variableAddr = codeGen.allocations.get(variable)
            val itemsize = codeGen.program.memsizer.memorySize(array.type)

            if(array.variable.type==DataType.UWORD) {
                // indexing a pointer var instead of a real array or string
                if(itemsize!=1)
                    throw AssemblyError("non-array var indexing requires bytes dt")
                if(array.index.type!=DataType.UBYTE)
                    throw AssemblyError("non-array var indexing requires bytes index")
                val idxReg = codeGen.vmRegisters.nextFree()
                code += expressionEval.translateExpression(array.index, idxReg, -1)
                if(zero) {
                    // there's no STOREZIX instruction
                    resultRegister = codeGen.vmRegisters.nextFree()
                    code += VmCodeInstruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=0)
                }
                code += VmCodeInstruction(Opcode.STOREIX, vmDt, reg1=resultRegister, reg2=idxReg, value = variableAddr)
                return code
            }

            val fixedIndex = constIntValue(array.index)
            if(zero) {
                if(fixedIndex!=null) {
                    variableAddr += fixedIndex*itemsize
                    code += VmCodeInstruction(Opcode.STOREZM, vmDt, value=variableAddr)
                } else {
                    val indexReg = codeGen.vmRegisters.nextFree()
                    code += loadIndexReg(array, itemsize, indexReg)
                    code += VmCodeInstruction(Opcode.STOREZX, vmDt, reg1=indexReg, value=variableAddr)
                }
            } else {
                if(vmDt== VmDataType.FLOAT) {
                    if(fixedIndex!=null) {
                        variableAddr += fixedIndex*itemsize
                        code += VmCodeInstruction(Opcode.STOREM, vmDt, fpReg1 = resultFpRegister, value=variableAddr)
                    } else {
                        val indexReg = codeGen.vmRegisters.nextFree()
                        code += loadIndexReg(array, itemsize, indexReg)
                        code += VmCodeInstruction(Opcode.STOREX, vmDt, reg1 = resultRegister, reg2=indexReg, value=variableAddr)
                    }
                } else {
                    if(fixedIndex!=null) {
                        variableAddr += fixedIndex*itemsize
                        code += VmCodeInstruction(Opcode.STOREM, vmDt, reg1 = resultRegister, value=variableAddr)
                    } else {
                        val indexReg = codeGen.vmRegisters.nextFree()
                        code += loadIndexReg(array, itemsize, indexReg)
                        code += VmCodeInstruction(Opcode.STOREX, vmDt, reg1 = resultRegister, reg2=indexReg, value=variableAddr)
                    }
                }
            }
        }
        else if(memory!=null) {
            require(vmDt== VmDataType.BYTE)
            if(zero) {
                if(memory.address is PtNumber) {
                    code += VmCodeInstruction(Opcode.STOREZM, vmDt, value=(memory.address as PtNumber).number.toInt())
                } else {
                    val addressReg = codeGen.vmRegisters.nextFree()
                    code += expressionEval.translateExpression(memory.address, addressReg, -1)
                    code += VmCodeInstruction(Opcode.STOREZI, vmDt, reg1=addressReg)
                }
            } else {
                if(memory.address is PtNumber) {
                    code += VmCodeInstruction(Opcode.STOREM, vmDt, reg1=resultRegister, value=(memory.address as PtNumber).number.toInt())
                } else {
                    val addressReg = codeGen.vmRegisters.nextFree()
                    code += expressionEval.translateExpression(memory.address, addressReg, -1)
                    code += VmCodeInstruction(Opcode.STOREI, vmDt, reg1=resultRegister, reg2=addressReg)
                }
            }
        }
        else
            throw AssemblyError("weird assigntarget")
        return code
    }

    private fun loadIndexReg(array: PtArrayIndexer, itemsize: Int, indexReg: Int): VmCodeChunk {
        val code = VmCodeChunk()
        if(itemsize==1) {
            code += expressionEval.translateExpression(array.index, indexReg, -1)
        }
        else {
            val mult = PtBinaryExpression("*", DataType.UBYTE, array.position)
            mult.children += array.index
            mult.children += PtNumber(DataType.UBYTE, itemsize.toDouble(), array.position)
            code += expressionEval.translateExpression(mult, indexReg, -1)
        }
        return code
    }
}