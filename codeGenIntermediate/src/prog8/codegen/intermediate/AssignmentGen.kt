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
            "+" -> expressionEval.operatorPlusInplace(address, null, vmDt, value)
            "-" -> expressionEval.operatorMinusInplace(address, null, vmDt, value)
            "*" -> expressionEval.operatorMultiplyInplace(address, null, vmDt, value)
            "/" -> expressionEval.operatorDivideInplace(address, null, vmDt, value.type in SignedDatatypes, value)
            "|" -> expressionEval.operatorOrInplace(address, null, vmDt, value)
            "&" -> expressionEval.operatorAndInplace(address, null, vmDt, value)
            "^" -> expressionEval.operatorXorInplace(address, null, vmDt, value)
            "<<" -> expressionEval.operatorShiftLeftInplace(address, null, vmDt, value)
            ">>" -> expressionEval.operatorShiftRightInplace(address, null, vmDt, value.type in SignedDatatypes, value)
            "%=" -> expressionEval.operatorModuloInplace(address, null, vmDt, value)
            "==" -> expressionEval.operatorEqualsInplace(address, null, vmDt, value)
            "!=" -> expressionEval.operatorNotEqualsInplace(address, null, vmDt, value)
            "<" -> expressionEval.operatorLessInplace(address, null, vmDt, value.type in SignedDatatypes, value)
            ">" -> expressionEval.operatorGreaterInplace(address, null, vmDt, value.type in SignedDatatypes, value)
            "<=" -> expressionEval.operatorLessEqualInplace(address, null, vmDt, value.type in SignedDatatypes, value)
            ">=" -> expressionEval.operatorGreaterEqualInplace(address, null, vmDt, value.type in SignedDatatypes, value)
            in PrefixOperators -> inplacePrefix(assignment.operator, vmDt, address, null)

            else -> throw AssemblyError("invalid augmented assign operator ${assignment.operator}")
        }
    }

    private fun assignVarAugmented(symbol: String, assignment: PtAugmentedAssign): IRCodeChunks {
        val value = assignment.value
        val targetDt = codeGen.irType(assignment.target.type)
        return when (assignment.operator) {
            "+=" -> expressionEval.operatorPlusInplace(null, symbol, targetDt, value)
            "-=" -> expressionEval.operatorMinusInplace(null, symbol, targetDt, value)
            "*=" -> expressionEval.operatorMultiplyInplace(null, symbol, targetDt, value)
            "/=" -> expressionEval.operatorDivideInplace(null, symbol, targetDt, value.type in SignedDatatypes, value)
            "|=" -> expressionEval.operatorOrInplace(null, symbol, targetDt, value)
            "&=" -> expressionEval.operatorAndInplace(null, symbol, targetDt, value)
            "^=" -> expressionEval.operatorXorInplace(null, symbol, targetDt, value)
            "<<=" -> expressionEval.operatorShiftLeftInplace(null, symbol, targetDt, value)
            ">>=" -> expressionEval.operatorShiftRightInplace(null, symbol, targetDt, value.type in SignedDatatypes, value)
            "%=" -> expressionEval.operatorModuloInplace(null, symbol, targetDt, value)
            "==" -> expressionEval.operatorEqualsInplace(null, symbol, targetDt, value)
            "!=" -> expressionEval.operatorNotEqualsInplace(null, symbol, targetDt, value)
            "<" -> expressionEval.operatorLessInplace(null, symbol, targetDt, value.type in SignedDatatypes, value)
            ">" -> expressionEval.operatorGreaterInplace(null, symbol, targetDt, value.type in SignedDatatypes, value)
            "<=" -> expressionEval.operatorLessEqualInplace(null, symbol, targetDt, value.type in SignedDatatypes, value)
            ">=" -> expressionEval.operatorGreaterEqualInplace(null, symbol, targetDt, value.type in SignedDatatypes, value)
            in PrefixOperators -> inplacePrefix(assignment.operator, targetDt, null, symbol)
            else -> throw AssemblyError("invalid augmented assign operator ${assignment.operator}")
        }
    }

    private fun fallbackAssign(origAssign: PtAugmentedAssign): IRCodeChunks {
        if (codeGen.options.slowCodegenWarnings)
            codeGen.errors.warn("indirect code for in-place assignment", origAssign.position)
        val value: PtExpression
        if(origAssign.operator in PrefixOperators) {
            value = PtPrefix(origAssign.operator, origAssign.value.type, origAssign.value.position)
            value.add(origAssign.value)
        } else {
            require(origAssign.operator.endsWith('='))
            if(codeGen.options.useNewExprCode) {
                // X += Y  ->   temp = X,  temp += Y,  X = temp
                val tempvar = codeGen.getReusableTempvar(origAssign.definingSub()!!, origAssign.target.type)
                val assign = PtAssignment(origAssign.position)
                val target = PtAssignTarget(origAssign.position)
                target.add(tempvar)
                assign.add(target)
                assign.add(origAssign.target.children.single())
                val augAssign = PtAugmentedAssign(origAssign.operator, origAssign.position)
                augAssign.add(target)
                augAssign.add(origAssign.value)
                val assignBack = PtAssignment(origAssign.position)
                assignBack.add(origAssign.target)
                assignBack.add(tempvar)
                return translateRegularAssign(assign) + translate(augAssign) + translateRegularAssign(assignBack)
            } else {
                value = PtBinaryExpression(origAssign.operator.dropLast(1), origAssign.value.type, origAssign.value.position)
                val left: PtExpression = origAssign.target.children.single() as PtExpression
                value.add(left)
                value.add(origAssign.value)
            }
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
            else -> throw AssemblyError("weird prefix operator")
        }
        return listOf(code)
    }

    private fun translateRegularAssign(assignment: PtAssignment): IRCodeChunks {
        // note: assigning array and string values is done via an explicit memcopy/stringcopy function call.
        val targetIdent = assignment.target.identifier
        val targetMemory = assignment.target.memory
        val targetArray = assignment.target.array
        val valueDt = codeGen.irType(assignment.value.type)
        val targetDt = codeGen.irType(assignment.target.type)
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
                    if(extendByteToWord)
                        addInstr(result, IRInstruction(Opcode.EXT, IRDataType.BYTE, reg1=valueRegister), null)
                } else {
                    val tr = expressionEval.translateExpression(assignment.value)
                    valueRegister = tr.resultReg
                    addToResult(result, tr, valueRegister, -1)
                    if(extendByteToWord) {
                        val opcode = if(assignment.value.type in SignedDatatypes) Opcode.EXTS else Opcode.EXT
                        addInstr(result, IRInstruction(opcode, IRDataType.BYTE, reg1 = valueRegister), null)
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
                    code += IRInstruction(Opcode.LOAD, targetDt, reg1=valueRegister, immediate = 0)
                }
                code += IRInstruction(Opcode.STOREIX, targetDt, reg1=valueRegister, reg2=idxReg, labelSymbol = variable)
                result += code
                return result
            }

            val fixedIndex = constIntValue(targetArray.index)
            if(zero) {
                if(fixedIndex!=null) {
                    val offset = fixedIndex*itemsize
                    val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZM, targetDt, labelSymbol = "$variable+$offset") }
                    result += chunk
                } else {
                    val (code, indexReg) = loadIndexReg(targetArray, itemsize)
                    result += code
                    result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZX, targetDt, reg1=indexReg, labelSymbol = variable) }
                }
            } else {
                if(targetDt== IRDataType.FLOAT) {
                    if(fixedIndex!=null) {
                        val offset = fixedIndex*itemsize
                        val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREM, targetDt, fpReg1 = valueFpRegister, labelSymbol = "$variable+$offset") }
                        result += chunk
                    } else {
                        val (code, indexReg) = loadIndexReg(targetArray, itemsize)
                        result += code
                        result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREX, targetDt, reg1 = indexReg, fpReg1 = valueFpRegister, labelSymbol = variable) }
                    }
                } else {
                    if(fixedIndex!=null) {
                        val offset = fixedIndex*itemsize
                        val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREM, targetDt, reg1 = valueRegister, labelSymbol = "$variable+$offset") }
                        result += chunk
                    } else {
                        val (code, indexReg) = loadIndexReg(targetArray, itemsize)
                        result += code
                        result += IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREX, targetDt, reg1 = valueRegister, reg2=indexReg, labelSymbol = variable) }
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
        // returns the code to load the Index into the register, which is also return\ed.

        val result = mutableListOf<IRCodeChunkBase>()
        if(itemsize==1) {
            val tr = expressionEval.translateExpression(array.index)
            addToResult(result, tr, tr.resultReg, -1)
            return Pair(result, tr.resultReg)
        }

        if(codeGen.options.useNewExprCode) {
            val tr = expressionEval.translateExpression(array.index)
            result += tr.chunks
            addInstr(result, IRInstruction(Opcode.MUL, tr.dt, reg1=tr.resultReg, immediate = itemsize), null)
            return Pair(result, tr.resultReg)
        } else {
            val mult: PtExpression
            mult = PtBinaryExpression("*", DataType.UBYTE, array.position)
            mult.children += array.index
            mult.children += PtNumber(DataType.UBYTE, itemsize.toDouble(), array.position)
            val tr = expressionEval.translateExpression(mult)
            addToResult(result, tr, tr.resultReg, -1)
            return Pair(result, tr.resultReg)
        }
    }
}