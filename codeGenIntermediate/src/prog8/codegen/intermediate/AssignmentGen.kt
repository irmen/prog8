package prog8.codegen.intermediate

import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.code.core.PrefixOperators
import prog8.code.core.SignedDatatypes
import prog8.intermediate.*

internal class AssignmentGen(private val codeGen: IRCodeGen, private val expressionEval: ExpressionGen) {

    internal fun translate(assignment: PtAssignment): IRCodeChunks {
        if(assignment.target.children.single() is PtMachineRegister)
            throw AssemblyError("assigning to a register should be done by just evaluating the expression into resultregister")

        return translateRegularAssign(assignment)
    }

    internal fun translate(augmentedAssign: PtAugmentedAssign): IRCodeChunks {
        if(augmentedAssign.target.children.single() is PtMachineRegister)
            throw AssemblyError("assigning to a register should be done by just evaluating the expression into resultregister")

        return translateInplaceAssign(augmentedAssign)
    }

    private fun translateInplaceAssign(assignment: PtAugmentedAssign): IRCodeChunks {
        val ident = assignment.target.identifier
        val memory = assignment.target.memory
        val array = assignment.target.array

        return if(ident!=null) {
            assignVarAugmented(ident.name, assignment)
        } else if(memory != null) {
            if(memory.address is PtNumber)
                assignMemoryAugmented((memory.address as PtNumber).number.toInt(), assignment)
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

    private fun assignMemoryAugmented(
        address: Int,
        assignment: PtAugmentedAssign
    ): IRCodeChunks {
        val value = assignment.value
        val vmDt = codeGen.irType(value.type)
        return when(assignment.operator) {
            "+" -> expressionEval.operatorPlusInplace(address, null, vmDt, value)
            "-" -> expressionEval.operatorMinusInplace(address, null, vmDt, value)
            "*" -> expressionEval.operatorMultiplyInplace(address, null, vmDt, value)
            "/" -> expressionEval.operatorDivideInplace(address, null, vmDt, value.type in SignedDatatypes, value)
            "|" -> expressionEval.operatorOrInplace(address, null, vmDt, value)
            "&" -> expressionEval.operatorAndInplace(address, null, vmDt, value)
            "^" -> expressionEval.operatorXorInplace(address, null, vmDt, value)
            "<<" -> expressionEval.operatorShiftLeftInplace(address, null, vmDt, value)
            ">>" -> expressionEval.operatorShiftRightInplace(address, null, vmDt, value.type in SignedDatatypes, value)
            in PrefixOperators -> inplacePrefix(assignment.operator, vmDt, address, null)
            else -> throw AssemblyError("invalid augmented assign operator ${assignment.operator}")
        }
    }

    private fun assignVarAugmented(symbol: String, assignment: PtAugmentedAssign): IRCodeChunks {
        val value = assignment.value
        val valueVmDt = codeGen.irType(value.type)
        return when (assignment.operator) {
            "+=" -> expressionEval.operatorPlusInplace(null, symbol, valueVmDt, value)
            "-=" -> expressionEval.operatorMinusInplace(null, symbol, valueVmDt, value)
            "*=" -> expressionEval.operatorMultiplyInplace(null, symbol, valueVmDt, value)
            "/=" -> expressionEval.operatorDivideInplace(null, symbol, valueVmDt, value.type in SignedDatatypes, value)
            "|=" -> expressionEval.operatorOrInplace(null, symbol, valueVmDt, value)
            "&=" -> expressionEval.operatorAndInplace(null, symbol, valueVmDt, value)
            "^=" -> expressionEval.operatorXorInplace(null, symbol, valueVmDt, value)
            "<<=" -> expressionEval.operatorShiftLeftInplace(null, symbol, valueVmDt, value)
            ">>=" -> expressionEval.operatorShiftRightInplace(null, symbol, valueVmDt, value.type in SignedDatatypes, value)
            in PrefixOperators -> inplacePrefix(assignment.operator, valueVmDt, null, symbol)
            else -> throw AssemblyError("invalid augmented assign operator ${assignment.operator}")
        }
    }

    private fun fallbackAssign(origAssign: PtAugmentedAssign): IRCodeChunks {
        if (codeGen.options.slowCodegenWarnings)
            codeGen.errors.warn("indirect code for in-place assignment", origAssign.position)
        val normalAssign = PtAssignment(origAssign.position)
        normalAssign.add(origAssign.target)
        val value: PtExpression
        if(origAssign.operator in PrefixOperators) {
            value = PtPrefix(origAssign.operator, origAssign.value.type, origAssign.value.position)
            value.add(origAssign.value)
        } else {
            require(origAssign.operator.endsWith('='))
            value = PtBinaryExpression(origAssign.operator.dropLast(1), origAssign.value.type, origAssign.value.position)
            val left: PtExpression = origAssign.target.children.single() as PtExpression
            value.add(left)
            value.add(origAssign.value)
        }
        normalAssign.add(value)
        return translateRegularAssign(normalAssign)
    }

    private fun inplacePrefix(operator: String, vmDt: IRDataType, address: Int?, symbol: String?): IRCodeChunks {
        val code= IRCodeChunk(null, null)
        when(operator) {
            "+" -> { }
            "-" -> {
                code += if(address!=null)
                    IRInstruction(Opcode.NEGM, vmDt, value = address)
                else
                    IRInstruction(Opcode.NEGM, vmDt, labelSymbol = symbol)
            }
            "~" -> {
                val regMask = codeGen.registers.nextFree()
                val mask = if(vmDt==IRDataType.BYTE) 0x00ff else 0xffff
                code += IRInstruction(Opcode.LOAD, vmDt, reg1=regMask, value = mask)
                code += if(address!=null)
                    IRInstruction(Opcode.XORM, vmDt, reg1=regMask, value = address)
                else
                    IRInstruction(Opcode.XORM, vmDt, reg1=regMask, labelSymbol = symbol)
            }
            else -> throw AssemblyError("weird prefix operator")
        }
        return listOf(code)
    }

    private fun translateRegularAssign(assignment: PtAssignment): IRCodeChunks {
        // note: assigning array and string values is done via an explicit memcopy/stringcopy function call.
        val ident = assignment.target.identifier
        val memory = assignment.target.memory
        val array = assignment.target.array
        val vmDt = codeGen.irType(assignment.value.type)
        val result = mutableListOf<IRCodeChunkBase>()
        var resultRegister = -1
        var resultFpRegister = -1
        val zero = codeGen.isZero(assignment.value)
        if(!zero) {
            // calculate the assignment value
            if (vmDt == IRDataType.FLOAT) {
                resultFpRegister = codeGen.registers.nextFreeFloat()
                result += expressionEval.translateExpression(assignment.value, -1, resultFpRegister)
            } else {
                resultRegister = if (assignment.value is PtMachineRegister) {
                    (assignment.value as PtMachineRegister).register
                } else {
                    val reg = codeGen.registers.nextFree()
                    result += expressionEval.translateExpression(assignment.value, reg, -1)
                    reg
                }
            }
        }
        if(ident!=null) {
            val instruction = if(zero) {
                IRInstruction(Opcode.STOREZM, vmDt, labelSymbol = ident.name)
            } else {
                if (vmDt == IRDataType.FLOAT)
                    IRInstruction(Opcode.STOREM, vmDt, fpReg1 = resultFpRegister, labelSymbol = ident.name)
                else
                    IRInstruction(Opcode.STOREM, vmDt, reg1 = resultRegister, labelSymbol = ident.name)
            }
            result += IRCodeChunk(null, null).also { it += instruction }
            return result
        }
        else if(array!=null) {
            val variable = array.variable.name
            val itemsize = codeGen.program.memsizer.memorySize(array.type)

            if(array.variable.type==DataType.UWORD) {
                // indexing a pointer var instead of a real array or string
                if(itemsize!=1)
                    throw AssemblyError("non-array var indexing requires bytes dt")
                if(array.index.type!=DataType.UBYTE)
                    throw AssemblyError("non-array var indexing requires bytes index")
                val idxReg = codeGen.registers.nextFree()
                result += expressionEval.translateExpression(array.index, idxReg, -1)
                val code = IRCodeChunk(null, null)
                if(zero) {
                    // there's no STOREZIX instruction
                    resultRegister = codeGen.registers.nextFree()
                    code += IRInstruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=0)
                }
                code += IRInstruction(Opcode.STOREIX, vmDt, reg1=resultRegister, reg2=idxReg, labelSymbol = variable)
                result += code
                return result
            }

            val fixedIndex = constIntValue(array.index)
            if(zero) {
                if(fixedIndex!=null) {
                    val offset = fixedIndex*itemsize
                    val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZM, vmDt, labelSymbol = "$variable+$offset") }
                    result += chunk
                } else {
                    val indexReg = codeGen.registers.nextFree()
                    result += loadIndexReg(array, itemsize, indexReg)
                    result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZX, vmDt, reg1=indexReg, labelSymbol = variable) }
                }
            } else {
                if(vmDt== IRDataType.FLOAT) {
                    if(fixedIndex!=null) {
                        val offset = fixedIndex*itemsize
                        val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREM, vmDt, fpReg1 = resultFpRegister, labelSymbol = "$variable+$offset") }
                        result += chunk
                    } else {
                        val indexReg = codeGen.registers.nextFree()
                        result += loadIndexReg(array, itemsize, indexReg)
                        result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREX, vmDt, reg1 = indexReg, fpReg1 = resultFpRegister, labelSymbol = variable) }
                    }
                } else {
                    if(fixedIndex!=null) {
                        val offset = fixedIndex*itemsize
                        val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREM, vmDt, reg1 = resultRegister, labelSymbol = "$variable+$offset") }
                        result += chunk
                    } else {
                        val indexReg = codeGen.registers.nextFree()
                        result += loadIndexReg(array, itemsize, indexReg)
                        result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREX, vmDt, reg1 = resultRegister, reg2=indexReg, labelSymbol = variable) }
                    }
                }
            }
            return result
        }
        else if(memory!=null) {
            require(vmDt== IRDataType.BYTE) { "must be byte type ${memory.position}"}
            if(zero) {
                if(memory.address is PtNumber) {
                    val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZM, vmDt, value=(memory.address as PtNumber).number.toInt()) }
                    result += chunk
                } else {
                    val addressReg = codeGen.registers.nextFree()
                    result += expressionEval.translateExpression(memory.address, addressReg, -1)
                    result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZI, vmDt, reg1=addressReg) }
                }
            } else {
                if(memory.address is PtNumber) {
                    val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREM, vmDt, reg1=resultRegister, value=(memory.address as PtNumber).number.toInt()) }
                    result += chunk
                } else {
                    val addressReg = codeGen.registers.nextFree()
                    result += expressionEval.translateExpression(memory.address, addressReg, -1)
                    result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREI, vmDt, reg1=resultRegister, reg2=addressReg) }
                }
            }

            return result
        }
        else
            throw AssemblyError("weird assigntarget")
    }

    private fun loadIndexReg(array: PtArrayIndexer, itemsize: Int, indexReg: Int): IRCodeChunks {
        return if(itemsize==1) {
            expressionEval.translateExpression(array.index, indexReg, -1)
        } else {
            val mult = PtBinaryExpression("*", DataType.UBYTE, array.position)
            mult.children += array.index
            mult.children += PtNumber(DataType.UBYTE, itemsize.toDouble(), array.position)
            expressionEval.translateExpression(mult, indexReg, -1)
        }
    }
}