package prog8.codegen.intermediate

import prog8.code.StExtSub
import prog8.code.StExtSubParameter
import prog8.code.StSub
import prog8.code.ast.*
import prog8.code.core.*
import prog8.intermediate.*


internal class AssignmentGen(private val codeGen: IRCodeGen, private val expressionEval: ExpressionGen) {

    internal fun translate(assignment: PtAssignment): IRCodeChunks {
        if(assignment.multiTarget) {
            val values = assignment.value as? PtFunctionCall
                ?: throw AssemblyError("only function calls can return multiple values in a multi-assign")

            val result = mutableListOf<IRCodeChunkBase>()
            val funcCall = expressionEval.translate(values)
            val assignmentTargets = assignment.children.dropLast(1)
            addToResult(result, funcCall, funcCall.resultReg, funcCall.resultFpReg)

            val extsub = codeGen.symbolTable.lookup(values.name) as? StExtSub
            if(extsub!=null) {
                require(funcCall.multipleResultRegs.size + funcCall.multipleResultFpRegs.size >= 2)
                if (funcCall.multipleResultFpRegs.isNotEmpty())
                    TODO("deal with (multiple?) FP return registers ${assignment.position}")
                if (extsub.returns.size == assignmentTargets.size) {
                    // Targets and values match. Assign all the things. Skip 'void' targets.
                    extsub.returns.zip(assignmentTargets).zip(funcCall.multipleResultRegs).forEach {
                        val target = it.first.second as PtAssignTarget
                        if (!target.void) {
                            val regNumber = it.second
                            val returns = it.first.first
                            result += assignCpuRegister(returns, regNumber, target)
                        }
                    }
                } else {
                    throw AssemblyError("number of values and targets don't match")
                }
            } else {
                val normalsub = codeGen.symbolTable.lookup(values.name) as? StSub
                if (normalsub != null) {
                    // note: multi-value returns are passed throug A or AY (for the first value) then cx16.R15 down to R0
                    // (this allows unencumbered use of many Rx registers if you don't return that many values)
                    val returnregs = (normalsub.astNode!! as IPtSubroutine).returnsWhatWhere()
                    normalsub.returns.zip(assignmentTargets).zip(returnregs).forEach {
                        val target = it.first.second as PtAssignTarget
                        if(!target.void) {
                            val reg = it.second.first
                            val regnum = codeGen.registers.next(irType(it.second.second))
                            val p = StExtSubParameter(reg, it.second.second)
                            result += assignCpuRegister(p, regnum, target)
                        }
                    }
                }
                else throw AssemblyError("expected extsub or normal sub")
            }

            return result
        } else {
            if (assignment.target.children.single() is PtIrRegister)
                throw AssemblyError("assigning to a register should be done by just evaluating the expression into resultregister")

            return translateRegularAssign(assignment)
        }
    }

    private fun assignCpuRegister(returns: StExtSubParameter, regNum: Int, target: PtAssignTarget): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        when(returns.register.registerOrPair) {
            RegisterOrPair.A -> addInstr(result, IRInstruction(Opcode.LOADHA, IRDataType.BYTE, reg1=regNum), null)
            RegisterOrPair.X -> addInstr(result, IRInstruction(Opcode.LOADHX, IRDataType.BYTE, reg1=regNum), null)
            RegisterOrPair.Y -> addInstr(result, IRInstruction(Opcode.LOADHY, IRDataType.BYTE, reg1=regNum), null)
            RegisterOrPair.AX -> addInstr(result, IRInstruction(Opcode.LOADHAX, IRDataType.WORD, reg1=regNum), null)
            RegisterOrPair.AY -> addInstr(result, IRInstruction(Opcode.LOADHAY, IRDataType.WORD, reg1=regNum), null)
            RegisterOrPair.XY -> addInstr(result, IRInstruction(Opcode.LOADHXY, IRDataType.WORD, reg1=regNum), null)
            in Cx16VirtualRegisters -> addInstr(result, IRInstruction(Opcode.LOADM, irType(returns.type), reg1=regNum, labelSymbol = "cx16.${returns.register.registerOrPair.toString().lowercase()}"), null)
            in combinedLongRegisters -> {
                require(returns.type.isLong)
                val startreg = returns.register.registerOrPair!!.startregname()
                addInstr(result, IRInstruction(Opcode.LOADM, IRDataType.LONG, reg1=regNum, labelSymbol = "cx16.${startreg}"), null)
            }
            RegisterOrPair.FAC1 -> addInstr(result, IRInstruction(Opcode.LOADHFACZERO, IRDataType.FLOAT, fpReg1 = regNum), null)
            RegisterOrPair.FAC2 -> addInstr(result, IRInstruction(Opcode.LOADHFACONE, IRDataType.FLOAT, fpReg1 = regNum), null)
            null -> if(returns.register.statusflag!=null)
                result += assignCpuStatusFlagReturnvalue(returns.register.statusflag!!, regNum)
            else
                throw AssemblyError("weird CPU register")
            else -> throw AssemblyError("weird CPU register")
        }

        // build an assignment to store the value in the actual target.
        val assign = PtAssignment(target.position)
        assign.add(target)
        assign.add(PtIrRegister(regNum, target.type, target.position))
        result += translate(assign)
        return result
    }

    private fun assignCpuStatusFlagReturnvalue(statusflag: Statusflag, regNum: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        when(statusflag) {
            Statusflag.Pc -> {
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=regNum, immediate = 0)
                    it += IRInstruction(Opcode.ROXL, IRDataType.BYTE, reg1=regNum)
                }
            }
            Statusflag.Pz -> TODO("find a way to assign cpu Z status bit to reg $regNum but it can already be clobbered by other return values")
            Statusflag.Pn -> TODO("find a way to assign cpu Z status bit to reg $regNum but it can already be clobbered by other return values")
            Statusflag.Pv -> {
                val skipLabel = codeGen.createLabelName()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=regNum, immediate = 0)
                    it += IRInstruction(Opcode.BSTVC, labelSymbol = skipLabel)
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=regNum, immediate = 1)
                }
                result += IRCodeChunk(skipLabel, null)
            }
        }
        return result
    }

    internal fun translate(augAssign: PtAugmentedAssign): IRCodeChunks {
        if(augAssign.target.type.isString)
            throw AssemblyError("cannot assign to str type ${augAssign.position}")

        // augmented assignment always has just a single target
        if (augAssign.target.children.single() is PtIrRegister)
            throw AssemblyError("assigning to a register should be done by just evaluating the expression into resultregister")

        val target = augAssign.target
        val targetDt = irType(target.type)
        val value = augAssign.value
        val memTarget = target.memory
        val constAddress = (memTarget?.address as? PtNumber)?.number?.toInt()
        val symbol = target.identifier?.name
        val array = target.array
        val signed = target.type.isSigned
        val pointerDeref = target.pointerDeref
        val chunks: IRCodeChunks

        if(pointerDeref!=null) {
            val inplaceInstrs = mutableListOf<IRCodeChunkBase>()
            val (addressReg, fieldOffset) = codeGen.evaluatePointerAddressIntoReg(inplaceInstrs, pointerDeref)
            val oldvalueReg = codeGen.registers.next(targetDt)

            if((augAssign.operator=="+=" || augAssign.operator=="-=") && value.asConstInteger()==1 || value.asConstInteger()==2) {
                // INC/DEC optimization instead of ADD/SUB

                loadfield(inplaceInstrs, addressReg, fieldOffset, targetDt, oldvalueReg)
                val instr = if(augAssign.operator=="+=") Opcode.INC else Opcode.DEC
                repeat(value.asConstInteger()!!) {
                    addInstr(inplaceInstrs, IRInstruction(instr, targetDt, reg1 = oldvalueReg), null)
                }

            } else {

                var operandTr = ExpressionCodeResult(emptyList(), IRDataType.BYTE, -1, -1)
                if(augAssign.operator!="or=" && augAssign.operator!="and=") {
                    // for everything except the shortcircuit boolean operators, we can evaluate the value here unconditionally
                    operandTr = expressionEval.translateExpression(value)
                    // note: the instructions to load the value will be placed after the LOADFIELD instruction so that later optimizations about what modification is actually done, are easier
                }
                if(targetDt== IRDataType.FLOAT) {

                    loadfield(inplaceInstrs, addressReg, fieldOffset, targetDt, oldvalueReg)
                    inplaceInstrs += operandTr.chunks
                    when(augAssign.operator) {
                        "+=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.ADDR, targetDt, fpReg1 = oldvalueReg, fpReg2 = operandTr.resultFpReg), null)
                        "-=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.SUBR, targetDt, fpReg1 = oldvalueReg, fpReg2 = operandTr.resultFpReg), null)
                        "*=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.MULR, targetDt, fpReg1 = oldvalueReg, fpReg2 = operandTr.resultFpReg), null)
                        "/=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.DIVSR, targetDt, fpReg1 = oldvalueReg, fpReg2 = operandTr.resultFpReg), null)
                        "%=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.MODR, targetDt, fpReg1 = oldvalueReg, fpReg2 = operandTr.resultFpReg), null)
                        "+" -> { /* inplace + is a no-op */ }
                        else -> throw AssemblyError("invalid augmented assign operator for floats ${augAssign.operator}")
                    }

                } else {

                    loadfield(inplaceInstrs, addressReg, fieldOffset, targetDt, oldvalueReg)
                    inplaceInstrs += operandTr.chunks
                    when(augAssign.operator) {
                        "+=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.ADDR, targetDt, reg1 = oldvalueReg, reg2 = operandTr.resultReg), null)
                        "-=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.SUBR, targetDt, reg1 = oldvalueReg, reg2 = operandTr.resultReg), null)
                        "*=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.MULR, targetDt, reg1 = oldvalueReg, reg2 = operandTr.resultReg), null)
                        "/=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.DIVR, targetDt, reg1 = oldvalueReg, reg2 = operandTr.resultReg), null)
                        "%=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.MODR, targetDt, reg1 = oldvalueReg, reg2 = operandTr.resultReg), null)
                        "|=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.ORR, targetDt, reg1 = oldvalueReg, reg2 = operandTr.resultReg), null)
                        "&=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.ANDR, targetDt, reg1 = oldvalueReg, reg2 = operandTr.resultReg), null)
                        "^=", "xor=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.XORR, targetDt, reg1 = oldvalueReg, reg2 = operandTr.resultReg), null)
                        "<<=" -> addInstr(inplaceInstrs, IRInstruction(Opcode.LSLN, targetDt, reg1 = oldvalueReg, reg2 = operandTr.resultReg), null)
                        ">>=" -> {
                            val opc = if (signed) Opcode.ASRN else Opcode.LSRN
                            addInstr(inplaceInstrs, IRInstruction(opc, targetDt, reg1 = oldvalueReg, reg2 = operandTr.resultReg), null)
                        }
                        "or=" -> {
                            val shortcutLabel = codeGen.createLabelName()
                            addInstr(inplaceInstrs, IRInstruction(Opcode.BSTNE, labelSymbol = shortcutLabel), null)
                            val valueTr = expressionEval.translateExpression(value)
                            inplaceInstrs += valueTr.chunks
                            addInstr(inplaceInstrs, IRInstruction(Opcode.ORR, targetDt, reg1=oldvalueReg, reg2=valueTr.resultReg), null)
                            inplaceInstrs += IRCodeChunk(shortcutLabel, null)
                        }
                        "and=" -> {
                            val shortcutLabel = codeGen.createLabelName()
                            addInstr(inplaceInstrs, IRInstruction(Opcode.BSTEQ, labelSymbol = shortcutLabel), null)
                            val valueTr = expressionEval.translateExpression(value)
                            inplaceInstrs += valueTr.chunks
                            addInstr(inplaceInstrs, IRInstruction(Opcode.ANDR, targetDt, reg1=oldvalueReg, reg2=valueTr.resultReg), null)
                            inplaceInstrs += IRCodeChunk(shortcutLabel, null)
                        }
                        "-" -> addInstr(inplaceInstrs, IRInstruction(Opcode.NEG, targetDt, reg1 = oldvalueReg), null)
                        "~" -> addInstr(inplaceInstrs, IRInstruction(Opcode.INV, targetDt, reg1 = oldvalueReg), null)
                        "not" -> addInstr(inplaceInstrs, IRInstruction(Opcode.XOR, targetDt, reg1 = oldvalueReg, immediate = 1), null)
                        "+" -> { /* inplace + is a no-op */ }
                        else -> throw AssemblyError("invalid augmented assign operator ${augAssign.operator}")
                    }
                }
            }

            codeGen.storeValueAtPointersLocation(inplaceInstrs, addressReg, fieldOffset, pointerDeref.type, false, oldvalueReg)
            chunks = inplaceInstrs
        } else {
            chunks = when (augAssign.operator) {
                "+=" -> operatorPlusInplace(symbol, array, constAddress, memTarget, targetDt, value)
                "-=" -> operatorMinusInplace(symbol, array, constAddress, memTarget, targetDt, value)
                "*=" -> operatorMultiplyInplace(symbol, array, constAddress, memTarget, targetDt, value, signed)
                "/=" -> operatorDivideInplace(symbol, array, constAddress, memTarget, targetDt, value, signed)
                "|=" -> operatorOrInplace(symbol, array, constAddress, memTarget, targetDt, value)
                "or=" -> operatorLogicalOrInplace(symbol, array, constAddress, memTarget, targetDt, value)
                "&=" -> operatorAndInplace(symbol, array, constAddress, memTarget, targetDt, value)
                "and=" -> operatorLogicalAndInplace(symbol, array, constAddress, memTarget, targetDt, value)
                "^=", "xor=" -> operatorXorInplace(symbol, array, constAddress, memTarget, targetDt, value)
                "<<=" -> operatorShiftLeftInplace(symbol, array, constAddress, memTarget, targetDt, value)
                ">>=" -> operatorShiftRightInplace(symbol, array, constAddress, memTarget, targetDt, value, signed)
                "%=" -> operatorModuloInplace(symbol, array, constAddress, memTarget, targetDt, value)
                in PrefixOperators -> inplacePrefix(augAssign.operator, symbol, array, constAddress, memTarget, targetDt)
                else -> throw AssemblyError("invalid augmented assign operator ${augAssign.operator}")
            } ?: fallbackAssign(augAssign)
        }

        chunks.filterIsInstance<IRCodeChunk>().firstOrNull()?.appendSrcPosition(augAssign.position)
        return chunks
    }

    private fun loadfield(
        inplaceInstrs: MutableList<IRCodeChunkBase>,
        addressReg: Int,
        fieldOffset: UByte,
        targetDt: IRDataType,
        oldvalueReg: Int
    ) {
        if (targetDt == IRDataType.FLOAT) {
            if (fieldOffset > 0u)
                addInstr(
                    inplaceInstrs,
                    IRInstruction(Opcode.LOADFIELD, targetDt, fpReg1 = oldvalueReg, reg1 = addressReg, immediate = fieldOffset.toInt()),
                    null
                )
            else
                addInstr(
                    inplaceInstrs,
                    IRInstruction(Opcode.LOADI, targetDt, fpReg1 = oldvalueReg, reg1 = addressReg),
                    null
                )
        } else {
            if (fieldOffset > 0u)
                addInstr(
                    inplaceInstrs,
                    IRInstruction(Opcode.LOADFIELD, targetDt, reg1 = oldvalueReg, reg2 = addressReg, immediate = fieldOffset.toInt()),
                    null
                )
            else
                addInstr(
                    inplaceInstrs,
                    IRInstruction(Opcode.LOADI, targetDt, reg1 = oldvalueReg, reg2 = addressReg),
                    null
                )
        }
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
            value = PtBinaryExpression(operator, origAssign.target.type, origAssign.value.position)
            val left: PtExpression = origAssign.target.children.single() as PtExpression
            value.add(left)
            value.add(origAssign.value)
        }
        val normalAssign = PtAssignment(origAssign.position)
        normalAssign.add(origAssign.target)
        normalAssign.add(value)
        return translateRegularAssign(normalAssign)
    }

    private fun inplacePrefix(operator: String, symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType): IRCodeChunks {
        if(operator=="+")
            return emptyList()

        if(array!=null)
            return inplacePrefixArray(operator, array)

        val result = mutableListOf<IRCodeChunkBase>()
        if(constAddress==null && memory!=null) {
            val register = codeGen.registers.next(vmDt)
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
                "not" -> {
                    val regMask = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, vmDt, reg1=regMask, immediate = 1)
                        it += IRInstruction(Opcode.XORM, vmDt, reg1=regMask, address = constAddress, labelSymbol = symbol)
                    }
                }
                else -> throw AssemblyError("weird prefix operator")
            }
        }
        return result
    }

    private fun inplacePrefixArray(operator: String, array: PtArrayIndexer): IRCodeChunks {
        val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
        val result = mutableListOf<IRCodeChunkBase>()
        val vmDt = irType(array.type)
        val constIndex = array.index.asConstInteger()

        fun loadIndex(): Int {
            val tr = expressionEval.translateExpression(array.index)
            addToResult(result, tr, tr.resultReg, -1)
            if(!array.splitWords && eltSize>1)
                result += codeGen.multiplyByConst(DataType.UBYTE, tr.resultReg, eltSize)
            return tr.resultReg
        }

        if(array.splitWords) {
            // handle split LSB/MSB arrays
            when(operator) {
                "+" -> { }
                "-" -> {
                    val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")
                    val skipCarryLabel = codeGen.createLabelName()
                    if(constIndex!=null) {
                        addInstr(result, IRInstruction(Opcode.NEGM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_lsb", symbolOffset = constIndex), null)
                        addInstr(result, IRInstruction(Opcode.BSTEQ, labelSymbol = skipCarryLabel), null)
                        addInstr(result, IRInstruction(Opcode.INCM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_msb", symbolOffset = constIndex), null)
                        addInstr(result, IRInstruction(Opcode.NEGM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_msb", symbolOffset = constIndex), skipCarryLabel)
                    } else {
                        val indexReg = loadIndex()
                        val registerLsb = codeGen.registers.next(IRDataType.BYTE)
                        val registerMsb = codeGen.registers.next(IRDataType.BYTE)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1 = registerLsb, reg2 = indexReg, labelSymbol = arrayVariableName+"_lsb")
                            it += IRInstruction(Opcode.NEG, IRDataType.BYTE, reg1 = registerLsb)
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1 = registerLsb, reg2 = indexReg, labelSymbol = arrayVariableName+"_lsb")
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1 = registerMsb, reg2 = indexReg, labelSymbol = arrayVariableName+"_msb")
                            it += IRInstruction(Opcode.NEG, IRDataType.BYTE, reg1 = registerMsb)
                            it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1 = registerLsb, immediate = 0)
                            it += IRInstruction(Opcode.BSTEQ, labelSymbol = skipCarryLabel)
                            it += IRInstruction(Opcode.DEC, IRDataType.BYTE, reg1 = registerMsb)
                        }
                        result += IRCodeChunk(skipCarryLabel, null).also {
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1 = registerMsb, reg2 = indexReg, labelSymbol = arrayVariableName+"_msb")
                        }
                    }
                }
                "~" -> {
                    val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")
                    if(constIndex!=null) {
                        addInstr(result, IRInstruction(Opcode.INVM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_lsb", symbolOffset = constIndex), null)
                        addInstr(result, IRInstruction(Opcode.INVM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_msb", symbolOffset = constIndex), null)
                    } else {
                        val indexReg = loadIndex()
                        val register = codeGen.registers.next(IRDataType.BYTE)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1 = register, reg2 = indexReg, labelSymbol = arrayVariableName+"_lsb")
                            it += IRInstruction(Opcode.INV, IRDataType.BYTE, reg1 = register)
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1 = register, reg2 = indexReg, labelSymbol = arrayVariableName+"_lsb")
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1 = register, reg2 = indexReg, labelSymbol = arrayVariableName+"_msb")
                            it += IRInstruction(Opcode.INV, IRDataType.BYTE, reg1 = register)
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1 = register, reg2 = indexReg, labelSymbol = arrayVariableName+"_msb")
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
                val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")
                if(constIndex!=null) {
                    addInstr(result, IRInstruction(Opcode.NEGM, vmDt, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize), null)
                } else {
                    val indexReg = loadIndex()
                    val register = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOADX, vmDt, reg1 = register, reg2 = indexReg, labelSymbol = arrayVariableName)
                        it += IRInstruction(Opcode.NEG, vmDt, reg1 = register)
                        it += IRInstruction(Opcode.STOREX, vmDt, reg1 = register, reg2 = indexReg, labelSymbol = arrayVariableName)
                    }
                }
            }
            "~" -> {
                val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")
                if(constIndex!=null) {
                    addInstr(result, IRInstruction(Opcode.INVM, vmDt, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize), null)
                } else {
                    val indexReg = loadIndex()
                    val register = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOADX, vmDt, reg1 = register, reg2 = indexReg, labelSymbol = arrayVariableName)
                        it += IRInstruction(Opcode.INV, vmDt, reg1 = register)
                        it += IRInstruction(Opcode.STOREX, vmDt, reg1 = register, reg2 = indexReg, labelSymbol = arrayVariableName)
                    }
                }
            }
            "not" -> {
                val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")
                val register = codeGen.registers.next(vmDt)
                if(constIndex!=null) {
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, vmDt, reg1=register, immediate = 1)
                        it += IRInstruction(Opcode.XORM, vmDt, reg1=register, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                    }
                } else {
                    val indexReg = loadIndex()
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOADX, vmDt, reg1 = register, reg2 = indexReg, labelSymbol = arrayVariableName)
                        it += IRInstruction(Opcode.XOR, vmDt, reg1 = register, immediate = 1)
                        it += IRInstruction(Opcode.STOREX, vmDt, reg1 = register, reg2 = indexReg, labelSymbol = arrayVariableName)
                    }
                }
            }
            else -> throw AssemblyError("weird prefix operator")
        }
        return result
    }

    private fun translateRegularAssign(assignment: PtAssignment): IRCodeChunks {
        if(assignment.target.type.isString) {
            // assigning array and string values is done via an explicit memcopy/stringcopy function calls.
            throw AssemblyError("cannot assign to str type ${assignment.position}")
        }

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
                if (assignment.value is PtIrRegister) {
                    valueRegister = (assignment.value as PtIrRegister).register
                    if(extendByteToWord) {
                        valueRegister = codeGen.registers.next(IRDataType.WORD)
                        addInstr(result, IRInstruction(Opcode.EXT, IRDataType.BYTE, reg1=valueRegister, reg2=(assignment.value as PtIrRegister).register), null)
                    }
                } else {
                    val tr = expressionEval.translateExpression(assignment.value)
                    valueRegister = tr.resultReg
                    addToResult(result, tr, valueRegister, -1)
                    if(extendByteToWord) {
                        valueRegister = codeGen.registers.next(IRDataType.WORD)
                        val opcode = if(assignment.value.type.isSigned) Opcode.EXTS else Opcode.EXT
                        addInstr(result, IRInstruction(opcode, IRDataType.BYTE, reg1=valueRegister, reg2=tr.resultReg), null)
                    }
                }
            }
        }

        with(assignment.target) {
            when {
                identifier != null -> {
                    val instruction = if(zero) {
                        IRInstruction(Opcode.STOREZM, targetDt, labelSymbol = identifier!!.name)
                    } else {
                        if (targetDt == IRDataType.FLOAT) {
                            require(valueFpRegister>=0)
                            IRInstruction(Opcode.STOREM, targetDt, fpReg1 = valueFpRegister, labelSymbol = identifier!!.name)
                        }
                        else {
                            require(valueRegister>=0)
                            IRInstruction(Opcode.STOREM, targetDt, reg1 = valueRegister, labelSymbol = identifier!!.name)
                        }
                    }
                    result += IRCodeChunk(null, null).also { it += instruction }
                    return result
                }
                memory != null -> {
                    require(targetDt == IRDataType.BYTE) { "must be byte type ${memory!!.position}"}
                    if(zero) {
                        if(memory!!.address is PtNumber) {
                            val chunk = IRCodeChunk(null, null).also { it += IRInstruction(Opcode.STOREZM, targetDt, address = (memory!!.address as PtNumber).number.toInt()) }
                            result += chunk
                        } else {
                            val tr = expressionEval.translateExpression(memory!!.address)
                            val addressReg = tr.resultReg
                            addToResult(result, tr, tr.resultReg, -1)
                            result += IRCodeChunk(null, null).also {
                                it += IRInstruction(Opcode.STOREZI, targetDt, reg1=addressReg)
                            }
                        }
                    } else {
                        val constAddress = memory!!.address as? PtNumber
                        if(constAddress!=null) {
                            addInstr(result, IRInstruction(Opcode.STOREM, targetDt, reg1=valueRegister, address=constAddress.number.toInt()), null)
                            return result
                        }
                        val ptrWithOffset = memory!!.address as? PtBinaryExpression
                        if(ptrWithOffset!=null) {
                            if(ptrWithOffset.operator=="+" && ptrWithOffset.left is PtIdentifier) {
                                val constOffset = (ptrWithOffset.right as? PtNumber)?.number?.toInt()
                                if(constOffset in 0..255) {
                                    val ptrName = (ptrWithOffset.left as PtIdentifier).name
                                    val pointerReg = codeGen.registers.next(IRDataType.WORD)
                                    result += IRCodeChunk(null, null).also {
                                        it += IRInstruction(Opcode.LOADM, IRDataType.WORD, reg1=pointerReg, labelSymbol = ptrName)
                                        it += IRInstruction(Opcode.STOREFIELD, IRDataType.BYTE, reg1=valueRegister, reg2=pointerReg, immediate = constOffset)
                                    }
                                    return result
                                }
                            }
                        }

                        val tr = expressionEval.translateExpression(memory!!.address)
                        val addressReg = tr.resultReg
                        addToResult(result, tr, tr.resultReg, -1)
                        addInstr(result, IRInstruction(Opcode.STOREI, targetDt, reg1=valueRegister, reg2=addressReg), null)
                        return result
                    }

                    return result
                }
                array != null -> {
                    val eltSize = codeGen.program.memsizer.memorySize(array!!.type, null)
                    val variable = array!!.variable
                    if(variable==null)
                        translateRegularAssignPointerIndexed(result, array!!.pointerderef!!, eltSize, array!!, zero, targetDt, valueRegister, valueFpRegister)
                    else if(variable.type.isPointer)
                        assignToIndexedSimplePointer(result, variable, eltSize, array!!, zero, targetDt, valueRegister, valueFpRegister)
                    else
                        translateRegularAssignArrayIndexed(result, variable.name, eltSize, array!!, zero, targetDt, valueRegister, valueFpRegister)
                    return result
                }
                pointerDeref != null -> {
                    val (addressReg, offset) = codeGen.evaluatePointerAddressIntoReg(result, pointerDeref!!)
                    val actualValueReg = if(pointerDeref!!.type.isFloat) valueFpRegister else valueRegister
                    codeGen.storeValueAtPointersLocation(result, addressReg, offset, pointerDeref!!.type, zero, actualValueReg)
                    return result

                }
                else -> {
                    throw AssemblyError("weird assigntarget")
                }
            }
        }
    }

    private fun assignToIndexedSimplePointer(
        result: MutableList<IRCodeChunkBase>,
        targetIdent: PtIdentifier,
        eltSize: Int,
        targetArray: PtArrayIndexer,
        zeroValue: Boolean,
        targetDt: IRDataType,
        valueRegister: Int,
        valueFpRegister: Int
    ) {
        val pointerTr = expressionEval.translateExpression(targetIdent)
        result += pointerTr.chunks
        val pointerReg = pointerTr.resultReg

        val constIndex = targetArray.index.asConstInteger()
        if(zeroValue) {
            if(constIndex!=null) {
                val offset = eltSize * constIndex
                addInstr(result, IRInstruction(Opcode.ADD, IRDataType.WORD, reg1=pointerReg, immediate = offset), null)
            } else {
                val (code, indexReg) = codeGen.loadIndexReg(targetArray.index, eltSize, true, targetArray.splitWords)
                result += code
                addInstr(result, IRInstruction(Opcode.ADDR, IRDataType.WORD, reg1=pointerReg, reg2=indexReg), null)
            }
            codeGen.storeValueAtPointersLocation(result, pointerReg, 0u, targetIdent.type.dereference(), true, -1)
        } else {
            if(constIndex!=null) {
                val offset = eltSize * constIndex
                addInstr(result, IRInstruction(Opcode.ADD, IRDataType.WORD, reg1=pointerReg, immediate = offset), null)
            } else {
                val (code, indexReg) = codeGen.loadIndexReg(targetArray.index, eltSize, true, targetArray.splitWords)
                result += code
                addInstr(result, IRInstruction(Opcode.ADDR, IRDataType.WORD, reg1=pointerReg, reg2=indexReg), null)
            }
            val realValueReg = if(targetDt == IRDataType.FLOAT) valueFpRegister else valueRegister
            codeGen.storeValueAtPointersLocation(result, pointerReg, 0u, targetIdent.type.dereference(), false, realValueReg)
        }
    }

    private fun translateRegularAssignArrayIndexed(
        result: MutableList<IRCodeChunkBase>,
        variable: String,
        eltSize: Int,
        targetArray: PtArrayIndexer,
        zero: Boolean,
        targetDt: IRDataType,
        valueRegister: Int,
        valueFpRegister: Int
    ) {
        val fixedIndex = targetArray.index.asConstInteger()
        val arrayLength = codeGen.symbolTable.getLength(variable)
        if(zero) {
            if(fixedIndex!=null) {
                val chunk = IRCodeChunk(null, null).also {
                    if(targetArray.splitWords) {
                        it += IRInstruction(Opcode.STOREZM, IRDataType.BYTE, immediate = arrayLength, labelSymbol = "${variable}_lsb", symbolOffset = fixedIndex)
                        it += IRInstruction(Opcode.STOREZM, IRDataType.BYTE, immediate = arrayLength, labelSymbol = "${variable}_msb", symbolOffset = fixedIndex)
                    }
                    else
                        it += IRInstruction(Opcode.STOREZM, targetDt, labelSymbol = variable, symbolOffset = fixedIndex*eltSize)
                }
                result += chunk
            } else {
                val (code, indexReg) = codeGen.loadIndexReg(targetArray.index, eltSize, false, targetArray.splitWords)
                result += code
                result += IRCodeChunk(null, null).also {
                    if(targetArray.splitWords) {
                        it += IRInstruction(Opcode.STOREZX, IRDataType.BYTE, reg1 = indexReg, labelSymbol = variable+"_lsb")
                        it += IRInstruction(Opcode.STOREZX, IRDataType.BYTE, reg1 = indexReg, labelSymbol = variable+"_msb")
                    }
                    else
                        it += IRInstruction(Opcode.STOREZX, targetDt, reg1=indexReg, labelSymbol = variable)
                }
            }
        } else {
            if(targetDt== IRDataType.FLOAT) {
                if(fixedIndex!=null) {
                    val offset = fixedIndex*eltSize
                    val chunk = IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.STOREM, targetDt, fpReg1 = valueFpRegister, labelSymbol = variable, symbolOffset = offset)
                    }
                    result += chunk
                } else {
                    val (code, indexReg) = codeGen.loadIndexReg(targetArray.index, eltSize, false, targetArray.splitWords)
                    result += code
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.STOREX, targetDt, reg1 = indexReg, fpReg1 = valueFpRegister, labelSymbol = variable)
                    }
                }
            } else {
                if(fixedIndex!=null) {
                    val chunk = IRCodeChunk(null, null).also {
                        if(targetArray.splitWords) {
                            val lsbmsbReg = codeGen.registers.next(IRDataType.BYTE)
                            it += IRInstruction(Opcode.LSIGB, IRDataType.WORD, reg1 = lsbmsbReg, reg2 = valueRegister)
                            it += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1 = lsbmsbReg, immediate = arrayLength, labelSymbol = "${variable}_lsb", symbolOffset = fixedIndex)
                            it += IRInstruction(Opcode.MSIGB, IRDataType.WORD, reg1 = lsbmsbReg, reg2 = valueRegister)
                            it += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1 = lsbmsbReg, immediate = arrayLength, labelSymbol = "${variable}_msb", symbolOffset = fixedIndex)
                        }
                        else
                            it += IRInstruction(Opcode.STOREM, targetDt, reg1 = valueRegister, labelSymbol = variable, symbolOffset = fixedIndex*eltSize)
                    }
                    result += chunk
                } else {
                    val (code, indexReg) = codeGen.loadIndexReg(targetArray.index, eltSize, false, targetArray.splitWords)
                    result += code
                    result += IRCodeChunk(null, null).also {
                        if(targetArray.splitWords) {
                            val lsbmsbReg = codeGen.registers.next(IRDataType.BYTE)
                            it += IRInstruction(Opcode.LSIGB, IRDataType.WORD, reg1 = lsbmsbReg, reg2 = valueRegister)
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1 = lsbmsbReg, reg2=indexReg, immediate = arrayLength, labelSymbol = "${variable}_lsb")
                            it += IRInstruction(Opcode.MSIGB, IRDataType.WORD, reg1 = lsbmsbReg, reg2 = valueRegister)
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1 = lsbmsbReg, reg2=indexReg, immediate = arrayLength, labelSymbol = "${variable}_msb")
                        }
                        else
                            it += IRInstruction(Opcode.STOREX, targetDt, reg1 = valueRegister, reg2=indexReg, labelSymbol = variable)
                    }
                }
            }
        }
    }

    private fun translateRegularAssignPointerIndexed(
        result: MutableList<IRCodeChunkBase>,
        pointerderef: PtPointerDeref,
        eltSize: Int,
        targetArray: PtArrayIndexer,
        zero: Boolean,
        targetDt: IRDataType,
        valueRegister: Int,
        valueFpRegister: Int
    ) {
        val pointerTr = expressionEval.translateExpression(pointerderef)
        result += pointerTr.chunks
        val pointerReg = pointerTr.resultReg

        val fixedIndex = targetArray.index.asConstInteger()
        if(fixedIndex!=null) {
            val offset = fixedIndex*eltSize
            addInstr(result, IRInstruction(Opcode.ADD, IRDataType.WORD, reg1 = pointerReg, immediate = offset), null)
            if(zero) {
                addInstr(result, IRInstruction(Opcode.STOREZI, targetDt, reg1 = pointerReg), null)
            } else {
                addInstr(
                    result,
                    if (targetDt == IRDataType.FLOAT)
                        IRInstruction(Opcode.STOREI, IRDataType.FLOAT, fpReg1 = valueFpRegister, reg1 = pointerReg)
                    else
                        IRInstruction(Opcode.STOREI, targetDt, reg1 = valueRegister, reg2 = pointerReg), null
                )
            }
        } else {
            // index is an expression
            val (code, indexReg) = codeGen.loadIndexReg(targetArray.index, eltSize, true, targetArray.splitWords)
            result += code
            addInstr(result, IRInstruction(Opcode.ADDR, IRDataType.WORD, reg1 = pointerReg, reg2 = indexReg), null)
            if(zero) {
                addInstr(result, IRInstruction(Opcode.STOREZI, targetDt, reg1 = pointerReg), null)
            } else {
                addInstr(result,
                    if(targetDt== IRDataType.FLOAT)
                    IRInstruction(Opcode.STOREI, IRDataType.FLOAT, fpReg1 =valueFpRegister, reg1 = pointerReg)
                else
                    IRInstruction(Opcode.STOREI, targetDt, reg1 = valueRegister, reg2 = pointerReg)
                    , null)
            }
        }
    }

    private fun operatorAndInplace(symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")
            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            if(constIndex!=null && constValue!=null) {
                if(array.splitWords) {
                    val valueRegLsb = codeGen.registers.next(IRDataType.BYTE)
                    val valueRegMsb = codeGen.registers.next(IRDataType.BYTE)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=valueRegLsb, immediate=constValue and 255)
                        it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=valueRegMsb, immediate=constValue shr 8)
                        it += IRInstruction(Opcode.ANDM, IRDataType.BYTE, reg1=valueRegLsb, labelSymbol = arrayVariableName+"_lsb", symbolOffset = constIndex)
                        it += IRInstruction(Opcode.ANDM, IRDataType.BYTE, reg1=valueRegMsb, labelSymbol = arrayVariableName+"_msb", symbolOffset = constIndex)
                    }
                } else {
                    val valueReg = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, vmDt, reg1=valueReg, immediate=constValue)
                        it += IRInstruction(Opcode.ANDM, vmDt, reg1=valueReg, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                    }
                }
                return result
            }
            return null // TODO("optimized inplace word array &")
        }
        if(constAddress==null && memory!=null)
            return null // TODO("optimized memory in-place &")

        val result = mutableListOf<IRCodeChunkBase>()
        val tr = expressionEval.translateExpression(operand)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, if(constAddress!=null)
            IRInstruction(Opcode.ANDM, vmDt, reg1=tr.resultReg, address = constAddress)
        else
            IRInstruction(Opcode.ANDM, vmDt, reg1=tr.resultReg, labelSymbol = symbol),null)
        return result
    }

    private fun operatorLogicalAndInplace(symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")
            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            if(constIndex!=null && constValue!=null) {
                if(array.splitWords) {
                    throw AssemblyError("logical and on (split) word array should not happen")
                } else {
                    val valueReg = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, vmDt, reg1=valueReg, immediate=constValue)
                        it += IRInstruction(Opcode.ANDM, vmDt, reg1=valueReg, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                    }
                }
                return result
            }
            return null // TODO("optimized inplace word array and")
        }
        if(constAddress==null && memory!=null)
            return null // TODO("optimized memory in-place and")

        val result = mutableListOf<IRCodeChunkBase>()
        val tr = expressionEval.translateExpression(operand)
        if(!operand.isSimple()) {
            // short-circuit  LEFT and RIGHT  -->  if LEFT then RIGHT else LEFT   (== if !LEFT then LEFT else RIGHT)
            val inplaceReg = codeGen.registers.next(vmDt)
            val shortcutLabel = codeGen.createLabelName()
            result += IRCodeChunk(null, null).also {
                it += if(constAddress!=null)
                    IRInstruction(Opcode.LOADM, vmDt, reg1=inplaceReg, address = constAddress)
                else
                    IRInstruction(Opcode.LOADM, vmDt, reg1=inplaceReg, labelSymbol = symbol)
                it += IRInstruction(Opcode.BSTEQ, labelSymbol = shortcutLabel)
            }
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, if(constAddress!=null)
                IRInstruction(Opcode.STOREM, vmDt, reg1=tr.resultReg, address = constAddress)
            else
                IRInstruction(Opcode.STOREM, vmDt, reg1=tr.resultReg, labelSymbol = symbol), null)
            result += IRCodeChunk(shortcutLabel, null)
        } else {
            // normal evaluation, it is *likely* shorter and faster because of the simple operands.
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, if(constAddress!=null)
                IRInstruction(Opcode.ANDM, vmDt, reg1=tr.resultReg, address = constAddress)
            else
                IRInstruction(Opcode.ANDM, vmDt, reg1=tr.resultReg, labelSymbol = symbol),null)
        }
        return result
    }

    private fun operatorOrInplace(symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            if(constIndex!=null && constValue!=null) {
                val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")
                if(array.splitWords) {
                    val valueRegLsb = codeGen.registers.next(IRDataType.BYTE)
                    val valueRegMsb = codeGen.registers.next(IRDataType.BYTE)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=valueRegLsb, immediate=constValue and 255)
                        it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=valueRegMsb, immediate=constValue shr 8)
                        it += IRInstruction(Opcode.ORM, IRDataType.BYTE, reg1=valueRegLsb, labelSymbol = arrayVariableName+"_lsb", symbolOffset = constIndex)
                        it += IRInstruction(Opcode.ORM, IRDataType.BYTE, reg1=valueRegMsb, labelSymbol = arrayVariableName+"_msb", symbolOffset = constIndex)
                    }
                } else {
                    val valueReg = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, vmDt, reg1=valueReg, immediate=constValue)
                        it += IRInstruction(Opcode.ORM, vmDt, reg1=valueReg, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                    }
                }
                return result
            }
            return null // TODO("optimized inplace word array |")
        }
        if(constAddress==null && memory!=null)
            return null // TODO("optimized memory in-place |")

        val result = mutableListOf<IRCodeChunkBase>()
        val tr = expressionEval.translateExpression(operand)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, if(constAddress!=null)
            IRInstruction(Opcode.ORM, vmDt, reg1=tr.resultReg, address = constAddress)
        else
            IRInstruction(Opcode.ORM, vmDt, reg1=tr.resultReg, labelSymbol = symbol), null)
        return result
    }

    private fun operatorLogicalOrInplace(symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            if(constIndex!=null && constValue!=null) {
                if(array.splitWords) {
                    throw AssemblyError("logical or on (split) word array should not happen")
                } else {
                    val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")
                    val valueReg = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, vmDt, reg1=valueReg, immediate=constValue)
                        it += IRInstruction(Opcode.ORM, vmDt, reg1=valueReg, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                    }
                }
                return result
            }
            return null  // TODO("optimized inplace word array or")
        }
        if(constAddress==null && memory!=null)
            return null  // TODO("optimized memory in-place or"")

        val result = mutableListOf<IRCodeChunkBase>()
        val tr = expressionEval.translateExpression(operand)
        if(!operand.isSimple()) {
            // short-circuit  LEFT or RIGHT  -->  if LEFT then LEFT else RIGHT
            val inplaceReg = codeGen.registers.next(vmDt)
            val shortcutLabel = codeGen.createLabelName()
            result += IRCodeChunk(null, null).also {
                it += if(constAddress!=null)
                    IRInstruction(Opcode.LOADM, vmDt, reg1=inplaceReg, address = constAddress)
                else
                    IRInstruction(Opcode.LOADM, vmDt, reg1=inplaceReg, labelSymbol = symbol)
                it += IRInstruction(Opcode.BSTNE, labelSymbol = shortcutLabel)
            }
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, if(constAddress!=null)
                IRInstruction(Opcode.STOREM, vmDt, reg1=tr.resultReg, address = constAddress)
            else
                IRInstruction(Opcode.STOREM, vmDt, reg1=tr.resultReg, labelSymbol = symbol), null)
            result += IRCodeChunk(shortcutLabel, null)
        } else {
            // normal evaluation, it is *likely* shorter and faster because of the simple operands.
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, if(constAddress!=null)
                IRInstruction(Opcode.ORM, vmDt, reg1=tr.resultReg, address = constAddress)
            else
                IRInstruction(Opcode.ORM, vmDt, reg1=tr.resultReg, labelSymbol = symbol), null)
        }
        return result
    }

    private fun operatorDivideInplace(symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression, signed: Boolean): IRCodeChunks? {
        if(array!=null) {
            TODO("/ in array ${array.position}")
        }
        if(constAddress==null && memory!=null)
            return null  // TODO("optimized memory in-place /"")

        val result = mutableListOf<IRCodeChunkBase>()
        val constFactorRight = operand as? PtNumber
        if(vmDt==IRDataType.FLOAT) {
            if(constFactorRight!=null && !constFactorRight.type.isFloat) {
                val factor = constFactorRight.number
                result += codeGen.divideByConstFloatInplace(constAddress, symbol, factor)
            } else {
                val tr = expressionEval.translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                val ins = if(signed) {
                    if(constAddress!=null)
                        IRInstruction(Opcode.DIVSM, vmDt, fpReg1 = tr.resultFpReg, address = constAddress)
                    else
                        IRInstruction(Opcode.DIVSM, vmDt, fpReg1 = tr.resultFpReg, labelSymbol = symbol)
                }
                else {
                    if(constAddress!=null)
                        IRInstruction(Opcode.DIVM, vmDt, fpReg1 = tr.resultFpReg, address = constAddress)
                    else
                        IRInstruction(Opcode.DIVM, vmDt, fpReg1 = tr.resultFpReg, labelSymbol = symbol)
                }
                addInstr(result, ins, null)
            }
        } else {
            if(constFactorRight!=null && !constFactorRight.type.isFloat) {
                val factor = constFactorRight.number.toInt()
                result += codeGen.divideByConstInplace(vmDt, constAddress, symbol, factor, signed)
            } else {
                val tr = expressionEval.translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                val ins = if(signed) {
                    if(constAddress!=null)
                        IRInstruction(Opcode.DIVSM, vmDt, reg1 = tr.resultReg, address = constAddress)
                    else
                        IRInstruction(Opcode.DIVSM, vmDt, reg1 = tr.resultReg, labelSymbol = symbol)
                }
                else {
                    if(constAddress!=null)
                        IRInstruction(Opcode.DIVM, vmDt, reg1 = tr.resultReg, address = constAddress)
                    else
                        IRInstruction(Opcode.DIVM, vmDt, reg1 = tr.resultReg, labelSymbol = symbol)
                }
                addInstr(result, ins, null)
            }
        }
        return result
    }

    private fun operatorMultiplyInplace(symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression, signed: Boolean): IRCodeChunks? {
        if(array!=null) {
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            val result = mutableListOf<IRCodeChunkBase>()
            if(array.splitWords)
                return operatorMultiplyInplaceSplitArray(array, operand)
            val eltDt = irType(array.type)
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            if(constIndex!=null && constValue!=null) {
                if(constValue!=1) {
                    val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")
                    val valueReg=codeGen.registers.next(eltDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, eltDt, reg1=valueReg, immediate = constValue)
                        val opcode = if(signed) Opcode.MULSM else Opcode.MULM
                        it += IRInstruction(opcode, eltDt, reg1=valueReg, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                    }
                }
                return result
            }
            return null  // TODO("optimized inplace array * non-const")
        }
        if(constAddress==null && memory!=null)
            return null  // TODO("optimized memory in-place *"")

        val result = mutableListOf<IRCodeChunkBase>()
        val constFactorRight = operand as? PtNumber
        if(vmDt==IRDataType.FLOAT) {
            if(constFactorRight!=null) {
                val factor = constFactorRight.number
                result += codeGen.multiplyByConstFloatInplace(constAddress, symbol, factor)
            } else {
                val tr = expressionEval.translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, if(constAddress!=null)
                    IRInstruction(Opcode.MULSM, vmDt, fpReg1 = tr.resultFpReg, address = constAddress)
                else
                    IRInstruction(Opcode.MULSM, vmDt, fpReg1 = tr.resultFpReg, labelSymbol = symbol)
                    , null)
            }
        } else {
            if(constFactorRight!=null && !constFactorRight.type.isFloat) {
                val factor = constFactorRight.number.toInt()
                result += codeGen.multiplyByConstInplace(vmDt, signed, constAddress, symbol, factor)
            } else {
                val tr = expressionEval.translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                val opcode = if(signed) Opcode.MULSM else Opcode.MULM
                addInstr(result, if(constAddress!=null)
                    IRInstruction(opcode, vmDt, reg1=tr.resultReg, address = constAddress)
                else
                    IRInstruction(opcode, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
                    , null)
            }
        }
        return result
    }

    private fun operatorMinusInplace(symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            val result = mutableListOf<IRCodeChunkBase>()
            if(array.splitWords)
                return operatorMinusInplaceSplitArray(array, operand)
            val eltDt = irType(array.type)
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            if(constIndex!=null && constValue!=null) {
                val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")

                if(constValue==1) {
                    addInstr(result, IRInstruction(Opcode.DECM, eltDt, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize), null)
                } else {
                    val valueReg=codeGen.registers.next(eltDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, eltDt, reg1=valueReg, immediate = constValue)
                        it += IRInstruction(Opcode.SUBM, eltDt, reg1=valueReg, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                    }
                }
                return result
            }
            return null  // TODO("optimized inplace array -")
        }
        if(constAddress==null && memory!=null)
            return null  // TODO("optimized memory in-place -"")

        val constValue = (operand as? PtNumber)?.number
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            if(constValue==1.0) {
                addInstr(result, if(constAddress!=null)
                    IRInstruction(Opcode.DECM, vmDt, address = constAddress)
                else
                    IRInstruction(Opcode.DECM, vmDt, labelSymbol = symbol), null)
            }
            else {
                val tr = expressionEval.translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, if(constAddress!=null)
                    IRInstruction(Opcode.SUBM, vmDt, fpReg1=tr.resultFpReg, address = constAddress)
                else
                    IRInstruction(Opcode.SUBM, vmDt, fpReg1=tr.resultFpReg, labelSymbol = symbol), null)
            }
        } else {
            if(constValue==1.0) {
                addInstr(result, if(constAddress!=null)
                    IRInstruction(Opcode.DECM, vmDt, address = constAddress)
                else
                    IRInstruction(Opcode.DECM, vmDt, labelSymbol = symbol), null)
            }
            else {
                val tr = expressionEval.translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                addInstr(result, if(constAddress!=null)
                    IRInstruction(Opcode.SUBM, vmDt, reg1=tr.resultReg, address = constAddress)
                else
                    IRInstruction(Opcode.SUBM, vmDt, reg1=tr.resultReg, labelSymbol = symbol), null)
            }
        }
        return result
    }

    private fun operatorMultiplyInplaceSplitArray(array: PtArrayIndexer, operand: PtExpression): IRCodeChunks? {
        return null  // fallback to slow method   // TODO("inplace split word array *")
    }

    private fun operatorMinusInplaceSplitArray(array: PtArrayIndexer, operand: PtExpression): IRCodeChunks? {
        val result = mutableListOf<IRCodeChunkBase>()
        val constIndex = array.index.asConstInteger()
        val constValue = operand.asConstInteger()
        if(constIndex!=null) {
            val skip = codeGen.createLabelName()
            if(constValue==1) {
                val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")

                val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = lsbReg, labelSymbol = arrayVariableName+"_lsb", symbolOffset = constIndex)
                    it += IRInstruction(Opcode.BSTNE, labelSymbol = skip)
                    it += IRInstruction(Opcode.DECM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_msb", symbolOffset = constIndex)
                }
                result += IRCodeChunk(skip, null).also {
                    it += IRInstruction(Opcode.DECM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_lsb", symbolOffset = constIndex)
                }
                return result
            } else {
                return null  // fallback to slow method   // TODO("inplace split word array -")
            }
        }
        return null  // fallback to slow method   // TODO("inplace split word array -")
    }

    private fun operatorPlusInplace(symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?,
                                    vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val result = mutableListOf<IRCodeChunkBase>()
            if(array.splitWords)
                return operatorPlusInplaceSplitArray(array, operand)
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            val elementDt = irType(array.type)
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            if(constIndex!=null && constValue!=null) {
                val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")

                if(constValue==1) {
                    addInstr(result, IRInstruction(Opcode.INCM, elementDt, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize), null)
                } else {
                    val valueReg=codeGen.registers.next(elementDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, elementDt, reg1=valueReg, immediate = constValue)
                        it += IRInstruction(Opcode.ADDM, elementDt, reg1=valueReg, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                    }
                }
                return result
            }
            return null  // TODO("optimized inplace array +")
        }
        if(constAddress==null && memory!=null)
            return null  // TODO("optimized memory in-place +"")

        val result = mutableListOf<IRCodeChunkBase>()
        val constValue = (operand as? PtNumber)?.number

        if(vmDt==IRDataType.FLOAT) {
            if(constValue==1.0) {
                addInstr(result, if (constAddress != null)
                    IRInstruction(Opcode.INCM, vmDt, address = constAddress)
                else
                    IRInstruction(Opcode.INCM, vmDt, labelSymbol = symbol) , null)
            }
            else {
                val tr = expressionEval.translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, if (constAddress != null)
                    IRInstruction(Opcode.ADDM, vmDt, fpReg1 = tr.resultFpReg, address = constAddress)
                else
                    IRInstruction(Opcode.ADDM, vmDt, fpReg1 = tr.resultFpReg, labelSymbol = symbol) , null)
            }
        } else {
            if(constValue==1.0) {
                addInstr(result, if (constAddress != null)
                    IRInstruction(Opcode.INCM, vmDt, address = constAddress)
                else
                    IRInstruction(Opcode.INCM, vmDt, labelSymbol = symbol) , null)
            }
            else {
                val tr = expressionEval.translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                if (constAddress != null)
                    addInstr(result, IRInstruction(Opcode.ADDM, vmDt, reg1 = tr.resultReg, address = constAddress), null)
                else
                    addInstr(result, IRInstruction(Opcode.ADDM, vmDt, reg1 = tr.resultReg, labelSymbol = symbol) , null)
            }
        }
        return result
    }

    private fun operatorPlusInplaceSplitArray(array: PtArrayIndexer, operand: PtExpression): IRCodeChunks? {
        val result = mutableListOf<IRCodeChunkBase>()
        val constIndex = array.index.asConstInteger()
        val constValue = operand.asConstInteger()
        if(constIndex!=null) {
            val skip = codeGen.createLabelName()
            if(constValue==1) {
                val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")

                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.INCM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_lsb", symbolOffset = constIndex)
                    it += IRInstruction(Opcode.BSTNE, labelSymbol = skip)
                    it += IRInstruction(Opcode.INCM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_msb", symbolOffset = constIndex)
                }
                result += IRCodeChunk(skip, null)
                return result
            } else {
                return null  // fallback to slow method   // TODO("inplace split word array +")
            }
        }
        return null  // fallback to slow method   // TODO("inplace split word array +")
    }

    private fun operatorShiftRightInplace(symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression, signed: Boolean): IRCodeChunks? {
        if(array!=null) {
            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()

            if(constIndex!=null && constValue!=null) {
                val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")

                if(array.splitWords) {
                    repeat(constValue) {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LSRM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_msb", symbolOffset = constIndex)
                            it += IRInstruction(Opcode.ROXRM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_lsb", symbolOffset = constIndex)
                        }
                    }
                } else {
                    val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
                    if(constValue==1) {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LSRM, vmDt, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                        }
                    } else {
                        val valueReg = codeGen.registers.next(IRDataType.BYTE)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=valueReg, immediate=constValue and 255)
                            it += IRInstruction(Opcode.LSRNM, vmDt, reg1=valueReg, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                        }
                    }
                }
                return result
            }
            return null   // TODO("optimized >> in array")
        }
        if(constAddress==null && memory!=null)
            return null  //  TODO("optimized memory in-place >>"")

        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isOne(operand)) {
            val opc = if (signed) Opcode.ASRM else Opcode.LSRM
            val ins = if(constAddress!=null)
                IRInstruction(opc, vmDt, address = constAddress)
            else
                IRInstruction(opc, vmDt, labelSymbol = symbol)
            addInstr(result, ins, null)
        } else {
            val shiftTr = expressionEval.translateExpression(operand)
            addToResult(result, shiftTr, shiftTr.resultReg, -1)
            val shiftReg = shiftTr.resultReg
            val opc = if (signed) Opcode.ASRNM else Opcode.LSRNM
            val ins = if(constAddress!=null)
                IRInstruction(opc, vmDt, reg1 = shiftReg, address = constAddress)
            else
                IRInstruction(opc, vmDt, reg1 = shiftReg, labelSymbol = symbol)
            addInstr(result, ins, null)
        }
        return result
    }

    private fun operatorShiftLeftInplace(symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()

            if(constIndex!=null && constValue!=null) {
                val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")

                if(array.splitWords) {
                    repeat(constValue) {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LSLM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_lsb", symbolOffset = constIndex)
                            it += IRInstruction(Opcode.ROXLM, IRDataType.BYTE, labelSymbol = arrayVariableName+"_msb", symbolOffset = constIndex)
                        }
                    }
                } else {
                    val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
                    if(constValue==1) {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LSLM, vmDt, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                        }
                    } else {
                        val valueReg = codeGen.registers.next(IRDataType.BYTE)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=valueReg, immediate=constValue and 255)
                            it += IRInstruction(Opcode.LSLNM, vmDt, reg1=valueReg, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                        }
                    }
                }
                return result
            }
            return null   // TODO("optimized << in array")
        }
        if(constAddress==null && memory!=null)
            return null  // TODO("optimized memory in-place <<"")

        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isOne(operand)){
            addInstr(result, if(constAddress!=null)
                IRInstruction(Opcode.LSLM, vmDt, address = constAddress)
            else
                IRInstruction(Opcode.LSLM, vmDt, labelSymbol = symbol)
                , null)
        } else {
            val shiftTr = expressionEval.translateExpression(operand)
            addToResult(result, shiftTr, shiftTr.resultReg, -1)
            val shiftReg = shiftTr.resultReg
            addInstr(result, if(constAddress!=null)
                IRInstruction(Opcode.LSLNM, vmDt, reg1=shiftReg, address = constAddress)
            else
                IRInstruction(Opcode.LSLNM, vmDt, reg1=shiftReg, labelSymbol = symbol)
                ,null)
        }
        return result
    }

    private fun operatorXorInplace(symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val arrayVariableName = array.variable?.name ?: TODO("support for ptr indexing ${array.position}")

            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            if(constIndex!=null && constValue!=null) {
                if(array.splitWords) {
                    val valueRegLsb = codeGen.registers.next(IRDataType.BYTE)
                    val valueRegMsb = codeGen.registers.next(IRDataType.BYTE)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=valueRegLsb, immediate=constValue and 255)
                        it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=valueRegMsb, immediate=constValue shr 8)
                        it += IRInstruction(Opcode.XORM, IRDataType.BYTE, reg1=valueRegLsb, labelSymbol = arrayVariableName+"_lsb", symbolOffset = constIndex)
                        it += IRInstruction(Opcode.XORM, IRDataType.BYTE, reg1=valueRegMsb, labelSymbol = arrayVariableName+"_msb", symbolOffset = constIndex)
                    }
                } else {
                    val valueReg = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOAD, vmDt, reg1=valueReg, immediate=constValue)
                        it += IRInstruction(Opcode.XORM, vmDt, reg1=valueReg, labelSymbol = arrayVariableName, symbolOffset = constIndex*eltSize)
                    }
                }
                return result
            }
            return null  // TODO("optimized inplace word array xor")
        }
        if(constAddress==null && memory!=null)
            return null  // TODO("optimized memory in-place xor"")

        val result = mutableListOf<IRCodeChunkBase>()
        val tr = expressionEval.translateExpression(operand)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, if(constAddress!=null)
            IRInstruction(Opcode.XORM, vmDt, reg1=tr.resultReg, address = constAddress)
        else
            IRInstruction(Opcode.XORM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
            ,null)
        return result
    }

    private fun operatorModuloInplace(symbol: String?, array: PtArrayIndexer?, constAddress: Int?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            TODO("% in array  ${array.position}")
        }
        if(constAddress==null && memory!=null)
            return null  // TODO("optimized memory in-place %"")

        val result = mutableListOf<IRCodeChunkBase>()
        val resultReg = codeGen.registers.next(vmDt)
        if(operand is PtNumber) {
            val number = operand.number.toInt()
            if (constAddress != null) {
                // @(address) = @(address) %= operand
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, vmDt, reg1 = resultReg, address = constAddress)
                    it += IRInstruction(Opcode.MOD, vmDt, reg1 = resultReg, immediate = number)
                    it += IRInstruction(Opcode.STOREM, vmDt, reg1 = resultReg, address = constAddress)
                }
            } else {
                // symbol = symbol %= operand
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, vmDt, reg1 = resultReg, labelSymbol = symbol)
                    it += IRInstruction(Opcode.MOD, vmDt, reg1 = resultReg, immediate = number)
                    it += IRInstruction(Opcode.STOREM, vmDt, reg1 = resultReg, labelSymbol = symbol)
                }
            }
        } else {
            val tr = expressionEval.translateExpression(operand)
            result += tr.chunks
            if (constAddress != null) {
                // @(address) = @(address) %= operand
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, vmDt, reg1 = resultReg, address = constAddress)
                    it += IRInstruction(Opcode.MODR, vmDt, reg1 = resultReg, reg2 = tr.resultReg)
                    it += IRInstruction(Opcode.STOREM, vmDt, reg1 = resultReg, address = constAddress)
                }
            } else {
                // symbol = symbol %= operand
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, vmDt, reg1 = resultReg, labelSymbol = symbol)
                    it += IRInstruction(Opcode.MODR, vmDt, reg1 = resultReg, reg2 = tr.resultReg)
                    it += IRInstruction(Opcode.STOREM, vmDt, reg1 = resultReg, labelSymbol = symbol)
                }
            }
        }
        return result
    }
}