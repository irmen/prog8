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

        val chunks = translateRegularAssign(assignment)
        chunks.filterIsInstance<IRCodeChunk>().firstOrNull()?.appendSrcPosition(assignment.position)
        return chunks
    }

    internal fun translate(augAssign: PtAugmentedAssign): IRCodeChunks {
        if(augAssign.target.children.single() is PtMachineRegister)
            throw AssemblyError("assigning to a register should be done by just evaluating the expression into resultregister")

        val ident = augAssign.target.identifier
        val memory = augAssign.target.memory
        val array = augAssign.target.array

        val chunks = if(ident!=null) {
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
        chunks.filterIsInstance<IRCodeChunk>().firstOrNull()?.appendSrcPosition(augAssign.position)
        return chunks
    }

    private fun assignMemoryAugmented(
        address: Int,
        assignment: PtAugmentedAssign
    ): IRCodeChunks {
        val value = assignment.value
        val targetDt = irType(assignment.target.type)
        val signed = assignment.target.type in SignedDatatypes
        return when(assignment.operator) {
            "+=" -> expressionEval.operatorPlusInplace(address, null, targetDt, value)
            "-=" -> expressionEval.operatorMinusInplace(address, null, targetDt, value)
            "*=" -> expressionEval.operatorMultiplyInplace(address, null, targetDt, value)
            "/=" -> expressionEval.operatorDivideInplace(address, null, targetDt, signed, value)
            "|=" -> expressionEval.operatorOrInplace(address, null, targetDt, value)
            "&=" -> expressionEval.operatorAndInplace(address, null, targetDt, value)
            "^=" -> expressionEval.operatorXorInplace(address, null, targetDt, value)
            "<<=" -> expressionEval.operatorShiftLeftInplace(address, null, targetDt, value)
            ">>=" -> expressionEval.operatorShiftRightInplace(address, null, targetDt, signed, value)
            "%=" -> expressionEval.operatorModuloInplace(address, null, targetDt, value)
            "==" -> expressionEval.operatorEqualsInplace(address, null, targetDt, value)
            "!=" -> expressionEval.operatorNotEqualsInplace(address, null, targetDt, value)
            "<" -> expressionEval.operatorLessInplace(address, null, targetDt, signed, value)
            ">" -> expressionEval.operatorGreaterInplace(address, null, targetDt, signed, value)
            "<=" -> expressionEval.operatorLessEqualInplace(address, null, targetDt, signed, value)
            ">=" -> expressionEval.operatorGreaterEqualInplace(address, null, targetDt, signed, value)
            in PrefixOperators -> inplacePrefix(assignment.operator, targetDt, address, null)

            else -> throw AssemblyError("invalid augmented assign operator ${assignment.operator}")
        }
    }

    private fun assignVarAugmented(symbol: String, assignment: PtAugmentedAssign): IRCodeChunks {
        val value = assignment.value
        val signed = assignment.target.type in SignedDatatypes
        val targetDt = irType(assignment.target.type)
        return when(assignment.operator) {
            "+=" -> expressionEval.operatorPlusInplace(null, symbol, targetDt, value)
            "-=" -> expressionEval.operatorMinusInplace(null, symbol, targetDt, value)
            "*=" -> expressionEval.operatorMultiplyInplace(null, symbol, targetDt, value)
            "/=" -> expressionEval.operatorDivideInplace(null, symbol, targetDt, signed, value)
            "|=" -> expressionEval.operatorOrInplace(null, symbol, targetDt, value)
            "or=" -> expressionEval.operatorLogicalOrInplace(null, symbol, targetDt, value)
            "&=" -> expressionEval.operatorAndInplace(null, symbol, targetDt, value)
            "and=" -> expressionEval.operatorLogicalAndInplace(null, symbol, targetDt, value)
            "^=", "xor=" -> expressionEval.operatorXorInplace(null, symbol, targetDt, value)
            "<<=" -> expressionEval.operatorShiftLeftInplace(null, symbol, targetDt, value)
            ">>=" -> expressionEval.operatorShiftRightInplace(null, symbol, targetDt, signed, value)
            "%=" -> expressionEval.operatorModuloInplace(null, symbol, targetDt, value)
            "==" -> expressionEval.operatorEqualsInplace(null, symbol, targetDt, value)
            "!=" -> expressionEval.operatorNotEqualsInplace(null, symbol, targetDt, value)
            "<" -> expressionEval.operatorLessInplace(null, symbol, targetDt, signed, value)
            ">" -> expressionEval.operatorGreaterInplace(null, symbol, targetDt, signed, value)
            "<=" -> expressionEval.operatorLessEqualInplace(null, symbol, targetDt, signed, value)
            ">=" -> expressionEval.operatorGreaterEqualInplace(null, symbol, targetDt, signed, value)
            in PrefixOperators -> inplacePrefix(assignment.operator, targetDt, null, symbol)
            else -> throw AssemblyError("invalid augmented assign operator ${assignment.operator}")
        }
    }

    private fun fallbackAssign(origAssign: PtAugmentedAssign): IRCodeChunks {
        val value: PtExpression
        if(origAssign.operator in PrefixOperators) {
            value = PtPrefix(origAssign.operator, origAssign.target.type, origAssign.value.position)
            value.add(origAssign.value)
        } else {
            require(origAssign.operator.endsWith('='))
            value = PtBinaryExpression(origAssign.operator.dropLast(1), origAssign.target.type, origAssign.value.position)
            val left: PtExpression = origAssign.target.children.single() as PtExpression
            value.add(left)
            value.add(origAssign.value)
        }
        val normalAssign = PtAssignment(origAssign.position)
        normalAssign.add(origAssign.target)
        normalAssign.add(value)
        return translateRegularAssign(normalAssign)
    }

    private fun inplacePrefix(operator: String, vmDt: IRDataType, address: Int?, symbol: String?): IRCodeChunks {
        val code= IRCodeChunk(null, null)
        when(operator) {
            "+" -> { }
            "-" -> {
                code += if(address!=null)
                    IRInstruction(Opcode.NEGM, vmDt, address = address)
                else
                    IRInstruction(Opcode.NEGM, vmDt, labelSymbol = symbol)
            }
            "~" -> {
                val regMask = codeGen.registers.nextFree()
                val mask = if(vmDt==IRDataType.BYTE) 0x00ff else 0xffff
                code += IRInstruction(Opcode.LOAD, vmDt, reg1=regMask, immediate = mask)
                code += if(address!=null)
                    IRInstruction(Opcode.XORM, vmDt, reg1=regMask, address = address)
                else
                    IRInstruction(Opcode.XORM, vmDt, reg1=regMask, labelSymbol = symbol)
            }
            "not" -> {
                val regMask = codeGen.registers.nextFree()
                code += IRInstruction(Opcode.LOAD, vmDt, reg1=regMask, immediate = 1)
                code += if(address!=null)
                    IRInstruction(Opcode.XORM, vmDt, reg1=regMask, address = address)
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
        val valueDt = irType(assignment.value.type)
        val targetDt = irType(assignment.target.type)
        val result = mutableListOf<IRCodeChunkBase>()

        var valueRegister = -1
        var valueFpRegister = -1
        val zero = codeGen.isZero(assignment.value)
        if(!zero) {
            // calculate the assignment value
            if (valueDt == IRDataType.FLOAT) {
                val tr = expressionEval.translateExpression(assignment.value)
                valueFpRegister = tr.resultFpReg
                addToResult(result, tr, -1, valueFpRegister)
            } else {
                val extendByteToWord = if(targetDt != valueDt) {
                    // usually an error EXCEPT when a byte is assigned to a word.
                    if(targetDt==IRDataType.WORD && valueDt==IRDataType.BYTE)
                        true
                    else
                        throw AssemblyError("assignment value and target dt mismatch")
                } else false
                if (assignment.value is PtMachineRegister) {
                    valueRegister = (assignment.value as PtMachineRegister).register
                    if(extendByteToWord) {
                        valueRegister = codeGen.registers.nextFree()
                        addInstr(result, IRInstruction(Opcode.EXT, IRDataType.BYTE, reg1=valueRegister, reg2=(assignment.value as PtMachineRegister).register), null)
                    }
                } else {
                    val tr = expressionEval.translateExpression(assignment.value)
                    valueRegister = tr.resultReg
                    addToResult(result, tr, valueRegister, -1)
                    if(extendByteToWord) {
                        valueRegister = codeGen.registers.nextFree()
                        val opcode = if(assignment.value.type in SignedDatatypes) Opcode.EXTS else Opcode.EXT
                        addInstr(result, IRInstruction(opcode, IRDataType.BYTE, reg1=valueRegister, reg2=tr.resultReg), null)
                    }
                }
            }
        }

        if(targetIdent!=null) {
            val instruction = if(zero) {
                IRInstruction(Opcode.STOREZM, targetDt, labelSymbol = targetIdent.name)
            } else {
                if (targetDt == IRDataType.FLOAT)
                    IRInstruction(Opcode.STOREM, targetDt, fpReg1 = valueFpRegister, labelSymbol = targetIdent.name)
                else
                    IRInstruction(Opcode.STOREM, targetDt, reg1 = valueRegister, labelSymbol = targetIdent.name)
            }
            result += IRCodeChunk(null, null).also { it += instruction }
            return result
        }
        else if(targetArray!=null) {
            val variable = targetArray.variable.name
            val itemsize = codeGen.program.memsizer.memorySize(targetArray.type)

            if(targetArray.usesPointerVariable) {
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
                    code += IRInstruction(Opcode.LOAD, targetDt, reg1=valueRegister, immediate = 0)
                }
                code += IRInstruction(Opcode.STOREIX, targetDt, reg1=valueRegister, reg2=idxReg, labelSymbol = variable)
                result += code
                return result
            }

            val fixedIndex = constIntValue(targetArray.index)
            val arrayLength = codeGen.symbolTable.getLength(targetArray.variable.name)
            if(zero) {
                if(fixedIndex!=null) {
                    val chunk = IRCodeChunk(null, null).also {
                        if(targetArray.splitWords) {
                            it += IRInstruction(Opcode.STOREZM, IRDataType.BYTE, immediate = arrayLength, labelSymbol = "${variable}_lsb", symbolOffset = fixedIndex)
                            it += IRInstruction(Opcode.STOREZM, IRDataType.BYTE, immediate = arrayLength, labelSymbol = "${variable}_msb", symbolOffset = fixedIndex)
                        }
                        else
                            it += IRInstruction(Opcode.STOREZM, targetDt, labelSymbol = variable, symbolOffset = fixedIndex*itemsize)
                    }
                    result += chunk
                } else {
                    val (code, indexReg) = loadIndexReg(targetArray, itemsize)
                    result += code
                    result += IRCodeChunk(null, null).also {
                        if(targetArray.splitWords) {
                            it += IRInstruction(Opcode.STOREZX, IRDataType.BYTE, reg1 = indexReg, immediate = arrayLength, labelSymbol = variable+"_lsb")
                            it += IRInstruction(Opcode.STOREZX, IRDataType.BYTE, reg1 = indexReg, immediate = arrayLength, labelSymbol = variable+"_msb")
                        }
                        else
                            it += IRInstruction(Opcode.STOREZX, targetDt, reg1=indexReg, labelSymbol = variable)
                    }
                }
            } else {
                if(targetDt== IRDataType.FLOAT) {
                    if(fixedIndex!=null) {
                        val offset = fixedIndex*itemsize
                        val chunk = IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.STOREM, targetDt, fpReg1 = valueFpRegister, labelSymbol = variable, symbolOffset = offset)
                        }
                        result += chunk
                    } else {
                        val (code, indexReg) = loadIndexReg(targetArray, itemsize)
                        result += code
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.STOREX, targetDt, reg1 = indexReg, fpReg1 = valueFpRegister, labelSymbol = variable)
                        }
                    }
                } else {
                    if(fixedIndex!=null) {
                        val chunk = IRCodeChunk(null, null).also {
                            if(targetArray.splitWords) {
                                val msbReg = codeGen.registers.nextFree()
                                it += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1 = valueRegister, immediate = arrayLength, labelSymbol = "${variable}_lsb", symbolOffset = fixedIndex)
                                it += IRInstruction(Opcode.MSIG, IRDataType.BYTE, reg1 = msbReg, reg2 = valueRegister)
                                it += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1 = msbReg, immediate = arrayLength, labelSymbol = "${variable}_msb", symbolOffset = fixedIndex)
                            }
                            else
                                it += IRInstruction(Opcode.STOREM, targetDt, reg1 = valueRegister, labelSymbol = variable, symbolOffset = fixedIndex*itemsize)
                        }
                        result += chunk
                    } else {
                        val (code, indexReg) = loadIndexReg(targetArray, itemsize)
                        result += code
                        result += IRCodeChunk(null, null).also {
                            if(targetArray.splitWords) {
                                val msbReg = codeGen.registers.nextFree()
                                it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1 = valueRegister, reg2=indexReg, immediate = arrayLength, labelSymbol = "${variable}_lsb")
                                it += IRInstruction(Opcode.MSIG, IRDataType.BYTE, reg1 = msbReg, reg2 = valueRegister)
                                it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1 = msbReg, reg2=indexReg, immediate = arrayLength, labelSymbol = "${variable}_msb")
                            }
                            else
                                it += IRInstruction(Opcode.STOREX, targetDt, reg1 = valueRegister, reg2=indexReg, labelSymbol = variable)
                        }
                    }
                }
            }
            return result
        }
        else if(targetMemory!=null) {
            require(targetDt == IRDataType.BYTE) { "must be byte type ${targetMemory.position}"}
            if(zero) {
                if(targetMemory.address is PtNumber) {
                    val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZM, targetDt, address = (targetMemory.address as PtNumber).number.toInt()) }
                    result += chunk
                } else {
                    val tr = expressionEval.translateExpression(targetMemory.address)
                    val addressReg = tr.resultReg
                    addToResult(result, tr, tr.resultReg, -1)
                    result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZI, targetDt, reg1=addressReg) }
                }
            } else {
                if(targetMemory.address is PtNumber) {
                    val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREM, targetDt, reg1=valueRegister, address=(targetMemory.address as PtNumber).number.toInt()) }
                    result += chunk
                } else {
                    val tr = expressionEval.translateExpression(targetMemory.address)
                    val addressReg = tr.resultReg
                    addToResult(result, tr, tr.resultReg, -1)
                    result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREI, targetDt, reg1=valueRegister, reg2=addressReg) }
                }
            }

            return result
        }
        else
            throw AssemblyError("weird assigntarget")
    }

    private fun loadIndexReg(array: PtArrayIndexer, itemsize: Int): Pair<IRCodeChunks, Int> {
        // returns the code to load the Index into the register, which is also returned.

        val result = mutableListOf<IRCodeChunkBase>()
        if(itemsize==1 || array.splitWords) {
            val tr = expressionEval.translateExpression(array.index)
            addToResult(result, tr, tr.resultReg, -1)
            return Pair(result, tr.resultReg)
        }

        val mult: PtExpression
        mult = PtBinaryExpression("*", DataType.UBYTE, array.position)
        mult.children += array.index
        mult.children += PtNumber(DataType.UBYTE, itemsize.toDouble(), array.position)
        val tr = expressionEval.translateExpression(mult)
        addToResult(result, tr, tr.resultReg, -1)
        return Pair(result, tr.resultReg)
    }
}