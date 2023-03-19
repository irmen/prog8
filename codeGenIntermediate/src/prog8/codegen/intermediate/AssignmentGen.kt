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

    internal fun translate(augAssign: PtAugmentedAssign): IRCodeChunks {
        if(augAssign.target.children.single() is PtMachineRegister)
            throw AssemblyError("assigning to a register should be done by just evaluating the expression into resultregister")

        val ident = augAssign.target.identifier
        val memory = augAssign.target.memory
        val array = augAssign.target.array

        return if(ident!=null) {
            assignVarAugmented(ident.name, augAssign)
        } else if(memory != null) {
            if(memory.address is PtNumber)
                assignMemoryAugmented((memory.address as PtNumber).number.toInt(), augAssign)
            else
                fallbackAssign(augAssign)
        } else if(array!=null) {
            // NOTE: naive fallback assignment here will sometimes generate code that loads the index value multiple times
            // in a register. It's way too much work to optimize that here - instead, we trust that the generated IL assembly
            // will be optimized later and have the double assignments removed.
            fallbackAssign(augAssign)
        } else {
            fallbackAssign(augAssign)
        }
    }

    private fun assignMemoryAugmented(
        address: Int,
        assignment: PtAugmentedAssign
    ): IRCodeChunks {
        val value = assignment.value
        val vmDt = codeGen.irType(value.type)
        return when(assignment.operator) {
            "+" -> expressionEval.operatorPlusInplace(address, null, vmDt, value).chunks
            "-" -> expressionEval.operatorMinusInplace(address, null, vmDt, value).chunks
            "*" -> expressionEval.operatorMultiplyInplace(address, null, vmDt, value).chunks
            "/" -> expressionEval.operatorDivideInplace(address, null, vmDt, value.type in SignedDatatypes, value).chunks
            "|" -> expressionEval.operatorOrInplace(address, null, vmDt, value).chunks
            "&" -> expressionEval.operatorAndInplace(address, null, vmDt, value).chunks
            "^" -> expressionEval.operatorXorInplace(address, null, vmDt, value).chunks
            "<<" -> expressionEval.operatorShiftLeftInplace(address, null, vmDt, value).chunks
            ">>" -> expressionEval.operatorShiftRightInplace(address, null, vmDt, value.type in SignedDatatypes, value).chunks
            in PrefixOperators -> inplacePrefix(assignment.operator, vmDt, address, null)

            else -> throw AssemblyError("invalid augmented assign operator ${assignment.operator}")
        }
    }

    private fun assignVarAugmented(symbol: String, assignment: PtAugmentedAssign): IRCodeChunks {
        val value = assignment.value
        val valueVmDt = codeGen.irType(value.type)
        return when (assignment.operator) {
            "+=" -> expressionEval.operatorPlusInplace(null, symbol, valueVmDt, value).chunks
            "-=" -> expressionEval.operatorMinusInplace(null, symbol, valueVmDt, value).chunks
            "*=" -> expressionEval.operatorMultiplyInplace(null, symbol, valueVmDt, value).chunks
            "/=" -> expressionEval.operatorDivideInplace(null, symbol, valueVmDt, value.type in SignedDatatypes, value).chunks
            "|=" -> expressionEval.operatorOrInplace(null, symbol, valueVmDt, value).chunks
            "&=" -> expressionEval.operatorAndInplace(null, symbol, valueVmDt, value).chunks
            "^=" -> expressionEval.operatorXorInplace(null, symbol, valueVmDt, value).chunks
            "<<=" -> expressionEval.operatorShiftLeftInplace(null, symbol, valueVmDt, value).chunks
            ">>=" -> expressionEval.operatorShiftRightInplace(null, symbol, valueVmDt, value.type in SignedDatatypes, value).chunks
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
            if(codeGen.program.binaryExpressionsAreRPN) {
                value = PtRpn(origAssign.value.type, origAssign.value.position)
                val left = origAssign.target.children.single() as PtExpression
                val right = origAssign.value
                value.add(left)
                value.add(right)
                value.add(PtRpnOperator(origAssign.operator.dropLast(1), origAssign.target.type, left.type, right.type, origAssign.position))
            } else {
                value = PtBinaryExpression(origAssign.operator.dropLast(1), origAssign.value.type, origAssign.value.position)
                val left: PtExpression = origAssign.target.children.single() as PtExpression
                value.add(left)
                value.add(origAssign.value)
            }
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
        val targetIdent = assignment.target.identifier
        val targetMemory = assignment.target.memory
        val targetArray = assignment.target.array
        val vmDt = codeGen.irType(assignment.value.type)
        val result = mutableListOf<IRCodeChunkBase>()

        var valueRegister = -1
        var valueFpRegister = -1
        val zero = codeGen.isZero(assignment.value)
        if(!zero) {
            // calculate the assignment value
            if (vmDt == IRDataType.FLOAT) {
                val tr = expressionEval.translateExpression(assignment.value)
                valueFpRegister = tr.resultFpReg
                addToResult(result, tr, -1, valueFpRegister)
            } else {
                if (assignment.value is PtMachineRegister) {
                    valueRegister = (assignment.value as PtMachineRegister).register
                } else {
                    val tr = expressionEval.translateExpression(assignment.value)
                    valueRegister = tr.resultReg
                    addToResult(result, tr, valueRegister, -1)
                }
            }
        }

        if(targetIdent!=null) {
            val instruction = if(zero) {
                IRInstruction(Opcode.STOREZM, vmDt, labelSymbol = targetIdent.name)
            } else {
                if (vmDt == IRDataType.FLOAT) {
                    IRInstruction(Opcode.STOREM, vmDt, fpReg1 = valueFpRegister, labelSymbol = targetIdent.name)
                }
                else
                    IRInstruction(Opcode.STOREM, vmDt, reg1 = valueRegister, labelSymbol = targetIdent.name)
            }
            result += IRCodeChunk(null, null).also { it += instruction }
            return result
        }
        else if(targetArray!=null) {
            val variable = targetArray.variable.name
            val itemsize = codeGen.program.memsizer.memorySize(targetArray.type)

            if(targetArray.variable.type==DataType.UWORD) {
                // indexing a pointer var instead of a real array or string
                if(itemsize!=1)
                    throw AssemblyError("non-array var indexing requires bytes dt")
                if(targetArray.index.type!=DataType.UBYTE)
                    throw AssemblyError("non-array var indexing requires bytes index")
                val tr = expressionEval.translateExpression(targetArray.index)
                val idxReg = tr.resultReg
                addToResult(result, tr, tr.resultReg, -1)
                val code = IRCodeChunk(null, null)
                if(zero) {
                    // there's no STOREZIX instruction
                    valueRegister = codeGen.registers.nextFree()
                    code += IRInstruction(Opcode.LOAD, vmDt, reg1=valueRegister, value=0)
                }
                code += IRInstruction(Opcode.STOREIX, vmDt, reg1=valueRegister, reg2=idxReg, labelSymbol = variable)
                result += code
                return result
            }

            val fixedIndex = constIntValue(targetArray.index)
            if(zero) {
                if(fixedIndex!=null) {
                    val offset = fixedIndex*itemsize
                    val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZM, vmDt, labelSymbol = "$variable+$offset") }
                    result += chunk
                } else {
                    val (code, indexReg) = loadIndexReg(targetArray, itemsize)
                    result += code
                    result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZX, vmDt, reg1=indexReg, labelSymbol = variable) }
                }
            } else {
                if(vmDt== IRDataType.FLOAT) {
                    if(fixedIndex!=null) {
                        val offset = fixedIndex*itemsize
                        val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREM, vmDt, fpReg1 = valueFpRegister, labelSymbol = "$variable+$offset") }
                        result += chunk
                    } else {
                        val (code, indexReg) = loadIndexReg(targetArray, itemsize)
                        result += code
                        result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREX, vmDt, reg1 = indexReg, fpReg1 = valueFpRegister, labelSymbol = variable) }
                    }
                } else {
                    if(fixedIndex!=null) {
                        val offset = fixedIndex*itemsize
                        val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREM, vmDt, reg1 = valueRegister, labelSymbol = "$variable+$offset") }
                        result += chunk
                    } else {
                        val (code, indexReg) = loadIndexReg(targetArray, itemsize)
                        result += code
                        result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREX, vmDt, reg1 = valueRegister, reg2=indexReg, labelSymbol = variable) }
                    }
                }
            }
            return result
        }
        else if(targetMemory!=null) {
            require(vmDt== IRDataType.BYTE) { "must be byte type ${targetMemory.position}"}
            if(zero) {
                if(targetMemory.address is PtNumber) {
                    val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZM, vmDt, value=(targetMemory.address as PtNumber).number.toInt()) }
                    result += chunk
                } else {
                    val tr = expressionEval.translateExpression(targetMemory.address)
                    val addressReg = tr.resultReg
                    addToResult(result, tr, tr.resultReg, -1)
                    result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZI, vmDt, reg1=addressReg) }
                }
            } else {
                if(targetMemory.address is PtNumber) {
                    val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREM, vmDt, reg1=valueRegister, value=(targetMemory.address as PtNumber).number.toInt()) }
                    result += chunk
                } else {
                    val tr = expressionEval.translateExpression(targetMemory.address)
                    val addressReg = tr.resultReg
                    addToResult(result, tr, tr.resultReg, -1)
                    result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREI, vmDt, reg1=valueRegister, reg2=addressReg) }
                }
            }

            return result
        }
        else
            throw AssemblyError("weird assigntarget")
    }

    private fun loadIndexReg(array: PtArrayIndexer, itemsize: Int): Pair<IRCodeChunks, Int> {
        // returns the code to load the Index into the register, which is also return\ed.
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = if(itemsize==1) {
            expressionEval.translateExpression(array.index)
        } else {
            val mult : PtExpression
            if(codeGen.program.binaryExpressionsAreRPN) {
                mult = PtRpn(DataType.UBYTE, array.position)
                val left = array.index
                val right = PtNumber(DataType.UBYTE, itemsize.toDouble(), array.position)
                mult.add(left)
                mult.add(right)
                mult.add(PtRpnOperator("*", DataType.UBYTE, left.type, right.type, array.position))
            } else {
                mult = PtBinaryExpression("*", DataType.UBYTE, array.position)
                mult.children += array.index
                mult.children += PtNumber(DataType.UBYTE, itemsize.toDouble(), array.position)
            }
            expressionEval.translateExpression(mult)
        }
        addToResult(result, tr, tr.resultReg, -1)
        return Pair(result, tr.resultReg)
    }
}