package prog8.codegen.intermediate

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import prog8.code.ast.*
import prog8.code.core.*
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

        val target = augAssign.target
        val targetDt = irType(target.type)
        val memTarget = target.memory
        val constAddress = (memTarget?.address as? PtNumber)?.number?.toInt()
        val symbol = target.identifier?.name
        val array = target.array
        val value = augAssign.value
        val signed = target.type in SignedDatatypes
        val result: Result<IRCodeChunks, NotImplementedError> = when(augAssign.operator) {
            "+=" -> expressionEval.operatorPlusInplace(symbol, array, constAddress, memTarget, targetDt, value)
            "-=" -> expressionEval.operatorMinusInplace(symbol, array, constAddress, memTarget, targetDt, value)
            "*=" -> expressionEval.operatorMultiplyInplace(symbol, array, constAddress, memTarget, targetDt, value)
            "/=" -> expressionEval.operatorDivideInplace(symbol, array, constAddress, memTarget, targetDt, value, signed)
            "|=" -> expressionEval.operatorOrInplace(symbol, array, constAddress, memTarget, targetDt, value)
            "or=" -> expressionEval.operatorLogicalOrInplace(symbol, array, constAddress, memTarget, targetDt, value)
            "&=" -> expressionEval.operatorAndInplace(symbol, array, constAddress, memTarget, targetDt, value)
            "and=" -> expressionEval.operatorLogicalAndInplace(symbol, array, constAddress, memTarget, targetDt, value)
            "^=", "xor=" -> expressionEval.operatorXorInplace(symbol, array, constAddress, memTarget, targetDt, value)
            "<<=" -> expressionEval.operatorShiftLeftInplace(symbol, array, constAddress, memTarget, targetDt, value)
            ">>=" -> expressionEval.operatorShiftRightInplace(symbol, array, constAddress, memTarget, targetDt, value, signed)
            "%=" -> expressionEval.operatorModuloInplace(symbol, array, constAddress, memTarget, targetDt, value)
            "==" -> expressionEval.operatorEqualsInplace(symbol, array, constAddress, memTarget, targetDt, value)
            "!=" -> expressionEval.operatorNotEqualsInplace(symbol, array, constAddress, memTarget, targetDt, value)
            "<" -> expressionEval.operatorLessInplace(symbol, array, constAddress, memTarget, targetDt, value, signed)
            ">" -> expressionEval.operatorGreaterInplace(symbol, array, constAddress, memTarget, targetDt, value, signed)
            "<=" -> expressionEval.operatorLessEqualInplace(symbol, array, constAddress, memTarget, targetDt, value, signed)
            ">=" -> expressionEval.operatorGreaterEqualInplace(symbol, array, constAddress, memTarget, targetDt, value, signed)
            in PrefixOperators -> inplacePrefix(augAssign.operator, symbol, array, constAddress, memTarget, targetDt)

            else -> throw AssemblyError("invalid augmented assign operator ${augAssign.operator}")
        }

        val chunks = result.getOrElse { fallbackAssign(augAssign) }
        chunks.filterIsInstance<IRCodeChunk>().firstOrNull()?.appendSrcPosition(augAssign.position)
        return chunks
    }

    private fun fallbackAssign(origAssign: PtAugmentedAssign): IRCodeChunks {
        val value: PtExpression
        if(origAssign.operator in PrefixOperators) {
            value = PtPrefix(origAssign.operator, origAssign.value.type, origAssign.value.position)
            value.add(origAssign.value)
        } else {
            val operator = when(origAssign.operator) {
                in ComparisonOperators -> origAssign.operator
                else -> {
                    require(origAssign.operator.endsWith('='))
                    origAssign.operator.dropLast(1)
                }
            }
            value = PtBinaryExpression(operator, origAssign.value.type, origAssign.value.position)
            val left: PtExpression = origAssign.target.children.single() as PtExpression
            value.add(left)
            value.add(origAssign.value)
        }
        val normalAssign = PtAssignment(origAssign.position)
        normalAssign.add(origAssign.target)
        normalAssign.add(value)
        return translateRegularAssign(normalAssign)
    }

    private fun inplacePrefix(operator: String, symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType): Result<IRCodeChunks, NotImplementedError> {
        if(operator=="+")
            return Ok(emptyList())

        if(array!=null)
            return Ok(inplacePrefixArray(operator, array))

        val result = mutableListOf<IRCodeChunkBase>()
        if(constAddress==null && memory!=null) {
            val register = codeGen.registers.nextFree()
            val tr = expressionEval.translateExpression(memory.address)
            addToResult(result, tr, tr.resultReg, -1)
            when(operator) {
                "-" -> {
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOADI, vmDt, reg1=register, reg2=tr.resultReg)
                        it += IRInstruction(Opcode.NEG, vmDt, reg1=register)
                        it += IRInstruction(Opcode.STOREI, vmDt, reg1=register, reg2=tr.resultReg)
                    }
                }
                "~" -> {
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOADI, vmDt, reg1=register, reg2=tr.resultReg)
                        it += IRInstruction(Opcode.INV, vmDt, reg1=register)
                        it += IRInstruction(Opcode.STOREI, vmDt, reg1=register, reg2=tr.resultReg)
                    }
                }
            }
        } else {
            when (operator) {
                "-" -> addInstr(result, IRInstruction(Opcode.NEGM, vmDt, address = constAddress, labelSymbol = symbol), null)
                "~" -> addInstr(result, IRInstruction(Opcode.INVM, vmDt, address = constAddress, labelSymbol = symbol), null)
                // TODO: in boolean branch, how is 'not' handled here?
                else -> throw AssemblyError("weird prefix operator")
            }
        }
        return Ok(result)
    }

    private fun inplacePrefixArray(operator: String, array: PtArrayIndexer): IRCodeChunks {
        val eltSize = codeGen.program.memsizer.memorySize(array.type)
        val result = mutableListOf<IRCodeChunkBase>()
        val vmDt = irType(array.type)
        val constIndex = array.index.asConstInteger()

        fun loadIndex(): Int {
            val tr = expressionEval.translateExpression(array.index)
            addToResult(result, tr, tr.resultReg, -1)
            if(!array.splitWords && eltSize>1)
                result += codeGen.multiplyByConst(IRDataType.BYTE, tr.resultReg, eltSize)
            return tr.resultReg
        }

        if(array.splitWords) {
            // handle split LSB/MSB arrays
            when(operator) {
                "+" -> { }
                "-" -> {
                    val skipCarryLabel = codeGen.createLabelName()
                    if(constIndex!=null) {
                        addInstr(result, IRInstruction(Opcode.NEGM, IRDataType.BYTE, labelSymbol = array.variable.name+"_lsb", symbolOffset = constIndex), null)
                        addInstr(result, IRInstruction(Opcode.BSTEQ, labelSymbol = skipCarryLabel), null)
                        addInstr(result, IRInstruction(Opcode.INCM, IRDataType.BYTE, labelSymbol = array.variable.name+"_msb", symbolOffset = constIndex), null)
                        addInstr(result, IRInstruction(Opcode.NEGM, IRDataType.BYTE, labelSymbol = array.variable.name+"_msb", symbolOffset = constIndex), skipCarryLabel)
                    } else {
                        val indexReg = loadIndex()
                        val registerLsb = codeGen.registers.nextFree()
                        val registerMsb = codeGen.registers.nextFree()
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1 = registerLsb, reg2 = indexReg, labelSymbol = array.variable.name+"_lsb")
                            it += IRInstruction(Opcode.NEG, IRDataType.BYTE, reg1 = registerLsb)
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1 = registerLsb, reg2 = indexReg, labelSymbol = array.variable.name+"_lsb")
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1 = registerMsb, reg2 = indexReg, labelSymbol = array.variable.name+"_msb")
                            it += IRInstruction(Opcode.NEG, IRDataType.BYTE, reg1 = registerMsb)
                            it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1 = registerLsb, immediate = 0)
                            it += IRInstruction(Opcode.BSTEQ, labelSymbol = skipCarryLabel)
                            it += IRInstruction(Opcode.DEC, IRDataType.BYTE, reg1 = registerMsb)
                        }
                        result += IRCodeChunk(skipCarryLabel, null).also {
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1 = registerMsb, reg2 = indexReg, labelSymbol = array.variable.name+"_msb")
                        }
                    }
                }
                "~" -> {
                    if(constIndex!=null) {
                        addInstr(result, IRInstruction(Opcode.INVM, IRDataType.BYTE, labelSymbol = array.variable.name+"_lsb", symbolOffset = constIndex), null)
                        addInstr(result, IRInstruction(Opcode.INVM, IRDataType.BYTE, labelSymbol = array.variable.name+"_msb", symbolOffset = constIndex), null)
                    } else {
                        val indexReg = loadIndex()
                        val register = codeGen.registers.nextFree()
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1 = register, reg2 = indexReg, labelSymbol = array.variable.name+"_lsb")
                            it += IRInstruction(Opcode.INV, IRDataType.BYTE, reg1 = register)
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1 = register, reg2 = indexReg, labelSymbol = array.variable.name+"_lsb")
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1 = register, reg2 = indexReg, labelSymbol = array.variable.name+"_msb")
                            it += IRInstruction(Opcode.INV, IRDataType.BYTE, reg1 = register)
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1 = register, reg2 = indexReg, labelSymbol = array.variable.name+"_msb")
                        }
                    }
                }
                else -> throw AssemblyError("weird prefix operator")
            }
            return result
        }

        // normal array.

        when(operator) {
            "+" -> { }
            "-" -> {
                if(constIndex!=null) {
                    addInstr(result, IRInstruction(Opcode.NEGM, vmDt, labelSymbol = array.variable.name, symbolOffset = constIndex*eltSize), null)
                } else {
                    val indexReg = loadIndex()
                    val register = codeGen.registers.nextFree()
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOADX, vmDt, reg1 = register, reg2 = indexReg, labelSymbol = array.variable.name)
                        it += IRInstruction(Opcode.NEG, vmDt, reg1 = register)
                        it += IRInstruction(Opcode.STOREX, vmDt, reg1 = register, reg2 = indexReg, labelSymbol = array.variable.name)
                    }
                }
            }
            "~" -> {
                if(constIndex!=null) {
                    addInstr(result, IRInstruction(Opcode.INVM, vmDt, labelSymbol = array.variable.name, symbolOffset = constIndex*eltSize), null)
                } else {
                    val indexReg = loadIndex()
                    val register = codeGen.registers.nextFree()
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOADX, vmDt, reg1 = register, reg2 = indexReg, labelSymbol = array.variable.name)
                        it += IRInstruction(Opcode.INV, vmDt, reg1 = register)
                        it += IRInstruction(Opcode.STOREX, vmDt, reg1 = register, reg2 = indexReg, labelSymbol = array.variable.name)
                    }
                }
            }
            "not" -> {
                // TODO: in boolean branch, is 'not' handled ok like this?
                val register = codeGen.registers.nextFree()
                if(constIndex!=null) {
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, vmDt, reg1=register, immediate = 1)
                        it += IRInstruction(Opcode.XORM, vmDt, reg1=register, labelSymbol = array.variable.name, symbolOffset = constIndex*eltSize)
                    }
                } else {
                    val indexReg = loadIndex()
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOADX, vmDt, reg1 = register, reg2 = indexReg, labelSymbol = array.variable.name)
                        it += IRInstruction(Opcode.XOR, vmDt, reg1 = register, immediate = 1)
                        it += IRInstruction(Opcode.STOREX, vmDt, reg1 = register, reg2 = indexReg, labelSymbol = array.variable.name)
                    }
                }
            }
            else -> throw AssemblyError("weird prefix operator")
        }
        return result
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