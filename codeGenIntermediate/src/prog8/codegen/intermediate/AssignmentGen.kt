package prog8.codegen.intermediate

import prog8.code.StExtSub
import prog8.code.StExtSubParameter
import prog8.code.StNodeType
import prog8.code.StSub
import prog8.code.ast.*
import prog8.code.core.*
import prog8.intermediate.*


internal class AssignmentGen(private val codeGen: IRCodeGen, private val exprGen: ExpressionGen) {

    private fun normalizeArrayIndex(result: MutableList<IRCodeChunkBase>, indexTr: ExpressionCodeResult): Pair<Int, DataType> {
        val indexReg = codeGen.canonicalizeIndexReg(result, indexTr)
        val indexDt = if(codeGen.options.compTarget.indexRegType==IRDataType.WORD) DataType.UWORD else DataType.UBYTE
        return indexReg to indexDt
    }

    internal fun translate(assignment: PtAssignment): IRCodeChunks {
        if(assignment.multiTarget) {
            val values = assignment.value as? PtFunctionCall
                ?: throw AssemblyError("only function calls can return multiple values in a multi-assign")

            val result = mutableListOf<IRCodeChunkBase>()
            val funcCall = exprGen.translate(values)
            val assignmentTargets = assignment.children.dropLast(1)
            addToResult(result, funcCall, funcCall.resultReg, funcCall.resultFpReg)

            val extsub = codeGen.symbolTable.lookup(values.name) as? StExtSub
            if(extsub!=null) {
                require(funcCall.multipleResultRegs.size + funcCall.multipleResultFpRegs.size >= 2)
                if (extsub.returns.size == assignmentTargets.size) {
                    // Targets and values match. Assign all the things. Skip 'void' targets.
                    // We need to handle both FP registers (FAC1/FAC2) and regular CPU registers
                    // Status flag returns MUST be processed first, because their branch-based
                    // code needs to read CPU flags immediately after the call, before any
                    // LOADHR/STOREM instructions clobber them.
                    val fpRegs = funcCall.multipleResultFpRegs.toMutableList()
                    val cpuRegs = funcCall.multipleResultRegs.toMutableList()

                    // Pre-consume register numbers in original order to keep indices aligned
                    val regNumbers = extsub.returns.map { returns ->
                        val isFpRegister = returns.register.registerOrPair in listOf(RegisterOrPair.FAC1, RegisterOrPair.FAC2)
                        if(isFpRegister) fpRegs.removeAt(0) else cpuRegs.removeAt(0)
                    }

                    val paired = extsub.returns.zip(assignmentTargets).zip(regNumbers)
                    val (flagPairs, otherPairs) = paired.partition { (pair, _) ->
                        pair.first.register.statusflag != null
                    }

                    fun processPair(pair: Pair<Pair<StExtSubParameter, PtNode>, Int>) {
                        val (returns, targetStmt) = pair.first
                        val regNumber = pair.second
                        val target = targetStmt as PtAssignTarget
                        if (!target.void) {
                            result += assignCpuRegister(returns, regNumber, target)
                        }
                    }

                    flagPairs.forEach(::processPair)
                    otherPairs.forEach(::processPair)
                } else {
                    throw AssemblyError("number of values and targets don't match")
                }
            } else {
                val thing = codeGen.symbolTable.lookup(values.name)
                val normalsub = thing as? StSub
                if (normalsub!=null) {
                    // note: multi-value returns are passed throug A or AY (for the first value) then cx16.R15 down to R0
                    // (this allows unencumbered use of many Rx registers if you don't return that many values)
                    val returnregs = (normalsub.astNode!! as IPtSubroutine).returnsWhatWhere(codeGen.options.compTarget)
                    assignmentTargets.zip(returnregs).forEach {
                        val target = it.first as PtAssignTarget
                        if(!target.void) {
                            val reg = it.second.first
                            val regnum = codeGen.registers.next(codeGen.irType(it.second.second))
                            val p = StExtSubParameter("", it.second.second, reg)
                            result += assignCpuRegister(p, regnum, target)
                        }
                    }
                }
                else if(thing?.type==StNodeType.BUILTINFUNC) {
                    // note: multi-value returns are passed throug A or AY (for the first value) then cx16.R15 down to R0
                    // (this allows unencumbered use of many Rx registers if you don't return that many values)
                    val returntypes = BuiltinFunctions.getValue(thing.name).returnTypes
                    val signature = PtSubSignature(returntypes.map { DataType.forDt(it) }, values.position)
                    val returnregs = signature.returnsWhatWhere(codeGen.options.compTarget)
                    assignmentTargets.zip(returnregs).forEach {
                        val target = it.first as PtAssignTarget
                        if(!target.void) {
                            val reg = it.second.first
                            val regnum = codeGen.registers.next(codeGen.irType(it.second.second))
                            val p = StExtSubParameter("", it.second.second, reg)
                            result += assignCpuRegister(p, regnum, target)
                        }
                    }
                }
                else throw AssemblyError("expected extsub or normal sub or builtin func")
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
        val reg3 = returns.register.registerOrPair
        val (slot3, _) = if (reg3 != null && reg3 !in setOf(RegisterOrPair.FAC1, RegisterOrPair.FAC2))
            exprGen.registerOrStatusflagToSlotAndFlag(RegisterOrStatusflag(reg3, null))
        else null to null
        val m68kSlot3 = slot3?.takeIf { it.value >= 10 }
        if (m68kSlot3 != null) {
            addInstr(result, IRInstructions.hardwareLoad(codeGen.irType(returns.type), regNum, m68kSlot3), null)
        } else when(returns.register.registerOrPair) {
            RegisterOrPair.A -> addInstr(result, IRInstructions.hardwareLoad(IRDataType.BYTE, regNum, CallingConventionSlot(0)), null)
            RegisterOrPair.X -> addInstr(result, IRInstructions.hardwareLoad(IRDataType.BYTE, regNum, CallingConventionSlot(1)), null)
            RegisterOrPair.Y -> addInstr(result, IRInstructions.hardwareLoad(IRDataType.BYTE, regNum, CallingConventionSlot(2)), null)
            RegisterOrPair.AX -> addInstr(result, IRInstructions.hardwareLoad(IRDataType.WORD, regNum, CallingConventionSlot(3)), null)
            RegisterOrPair.AY -> addInstr(result, IRInstructions.hardwareLoad(IRDataType.WORD, regNum, CallingConventionSlot(4)), null)
            RegisterOrPair.XY -> addInstr(result, IRInstructions.hardwareLoad(IRDataType.WORD, regNum, CallingConventionSlot(5)), null)
            in Cx16VirtualRegisters -> addInstr(result, IRInstructions.loadMemory(Opcode.LOADM, codeGen.irType(returns.type), regNum, IRMemory.direct("cx16.${returns.register.registerOrPair.toString().lowercase()}")), null)
            in CombinedLongRegisters -> {
                require(returns.type.isLong)
                val startreg = returns.register.registerOrPair!!.startregname()
                addInstr(result, IRInstructions.loadMemory(Opcode.LOADM, IRDataType.LONG, regNum, IRMemory.direct("cx16.${startreg}")), null)
            }
            RegisterOrPair.FAC1 -> addInstr(result, IRInstructions.unary(Opcode.LOADHFACZERO, IRDataType.FLOAT, regNum), null)
            RegisterOrPair.FAC2 -> addInstr(result, IRInstructions.unary(Opcode.LOADHFACONE, IRDataType.FLOAT, regNum), null)
            null -> if(returns.register.statusflag!=null)
                result += assignCpuStatusFlagReturnvalue(returns.register.statusflag!!, regNum)
            else
                throw AssemblyError("weird CPU register")
            else -> throw AssemblyError("weird CPU register")
        }

        // build an assignment to store the value in the actual target.
        // Use the return type for the register (matching the LOAD instruction), not the target type.
        // translateRegularAssign will handle byte-to-word extension if needed.
        val assign = PtAssignment(target.position)
        assign.add(target)
        assign.add(PtIrRegister(regNum, returns.type, target.position))
        result += translate(assign)
        return result
    }

    private fun assignCpuStatusFlagReturnvalue(statusflag: Statusflag, regNum: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        when(statusflag) {
            Statusflag.Pc -> {
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.load(IRDataType.BYTE, regNum, 0)
                    it += IRInstructions.unary(Opcode.ROXL, IRDataType.BYTE, regNum)
                }
            }
            Statusflag.Pz -> {
                val setLabel = codeGen.createLabelName()
                val endLabel = codeGen.createLabelName()
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.branch(Opcode.BSTEQ, codeLabel(setLabel))
                    it += IRInstructions.load(IRDataType.BYTE, regNum, 0)
                    it += IRInstructions.jump(codeLabel(endLabel))
                }
                result += IRCodeChunk(setLabel, null).also {
                    it += IRInstructions.load(IRDataType.BYTE, regNum, 1)
                }
                result += IRCodeChunk(endLabel, null)
            }
            Statusflag.Pn -> {
                val setLabel = codeGen.createLabelName()
                val endLabel = codeGen.createLabelName()
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.branch(Opcode.BSTNEG, codeLabel(setLabel))
                    it += IRInstructions.load(IRDataType.BYTE, regNum, 0)
                    it += IRInstructions.jump(codeLabel(endLabel))
                }
                result += IRCodeChunk(setLabel, null).also {
                    it += IRInstructions.load(IRDataType.BYTE, regNum, 1)
                }
                result += IRCodeChunk(endLabel, null)
            }
            Statusflag.Pv -> {
                val skipLabel = codeGen.createLabelName()
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.load(IRDataType.BYTE, regNum, 0)
                    it += IRInstructions.branch(Opcode.BSTVC, codeLabel(skipLabel))
                    it += IRInstructions.load(IRDataType.BYTE, regNum, 1)
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
        val targetDt = codeGen.irType(target.type)
        val value = augAssign.value
        val memTarget = target.memory
        val constAddress: UInt? = (memTarget?.address as? PtNumber)?.number?.toUInt()
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
                    addInstr(inplaceInstrs, IRInstructions.unary(instr, targetDt, oldvalueReg), null)
                }

            } else {

                var operandTr = ExpressionCodeResult(emptyList(), IRDataType.BYTE, -1, -1)
                if(augAssign.operator!="or=" && augAssign.operator!="and=") {
                    // for everything except the shortcircuit boolean operators, we can evaluate the value here unconditionally
                    operandTr = exprGen.translateExpression(value)
                    // note: the instructions to load the value will be placed after the LOADI instruction so that later optimizations about what modification is actually done, are easier
                }
                if(targetDt== IRDataType.FLOAT) {

                    loadfield(inplaceInstrs, addressReg, fieldOffset, targetDt, oldvalueReg)
                    inplaceInstrs += operandTr.chunks
                    when(augAssign.operator) {
                        "+=" -> addInstr(inplaceInstrs, IRInstructions.binary(Opcode.ADDR, targetDt, oldvalueReg, operandTr.resultFpReg), null)
                        "-=" -> addInstr(inplaceInstrs, IRInstructions.binary(Opcode.SUBR, targetDt, oldvalueReg, operandTr.resultFpReg), null)
                        "*=" -> addInstr(inplaceInstrs, IRInstructions.binary(Opcode.MULR, targetDt, oldvalueReg, operandTr.resultFpReg), null)
                        "/=" -> addInstr(inplaceInstrs, IRInstructions.binary(Opcode.DIVSR, targetDt, oldvalueReg, operandTr.resultFpReg), null)
                        "%=" -> addInstr(inplaceInstrs, IRInstructions.binary(Opcode.MODR, targetDt, oldvalueReg, operandTr.resultFpReg), null)
                        "+" -> { /* inplace + is a no-op */ }
                        else -> throw AssemblyError("invalid augmented assign operator for floats ${augAssign.operator}")
                    }

                } else {

                    loadfield(inplaceInstrs, addressReg, fieldOffset, targetDt, oldvalueReg)
                    inplaceInstrs += operandTr.chunks
                    when(augAssign.operator) {
                        "+=" -> addInstr(inplaceInstrs, IRInstructions.binary(Opcode.ADDR, targetDt, oldvalueReg, operandTr.resultReg), null)
                        "-=" -> addInstr(inplaceInstrs, IRInstructions.binary(Opcode.SUBR, targetDt, oldvalueReg, operandTr.resultReg), null)
                        "*=" -> addInstr(inplaceInstrs, IRInstructions.binary(Opcode.MULR, targetDt, oldvalueReg, operandTr.resultReg), null)
                        "/=" -> addInstr(inplaceInstrs, IRInstructions.binary(Opcode.DIVR, targetDt, oldvalueReg, operandTr.resultReg), null)
                        "%=" -> {
                            val opc = if(signed) Opcode.MODSR else Opcode.MODR
                            addInstr(inplaceInstrs, IRInstructions.binary(opc, targetDt, oldvalueReg, operandTr.resultReg), null)
                        }
                        "|=" -> addInstr(inplaceInstrs, IRInstructions.binary(Opcode.ORR, targetDt, oldvalueReg, operandTr.resultReg), null)
                        "&=" -> addInstr(inplaceInstrs, IRInstructions.binary(Opcode.ANDR, targetDt, oldvalueReg, operandTr.resultReg), null)
                        "^=", "xor=" -> addInstr(inplaceInstrs, IRInstructions.binary(Opcode.XORR, targetDt, oldvalueReg, operandTr.resultReg), null)
                        "<<=" -> {
                            val constCount = value.asConstInteger()
                            if(constCount != null) {
                                addInstr(inplaceInstrs, IRInstructions.binaryImmediate(Opcode.LSLI, targetDt, oldvalueReg, constCount), null)
                            } else {
                                addInstr(inplaceInstrs, IRInstructions.binary(Opcode.LSLN, targetDt, oldvalueReg, operandTr.resultReg), null)
                            }
                        }
                        ">>=" -> {
                            val constCount = value.asConstInteger()
                            if(constCount != null) {
                                val opc = if (signed) Opcode.ASRI else Opcode.LSRI
                                addInstr(inplaceInstrs, IRInstructions.binaryImmediate(opc, targetDt, oldvalueReg, constCount), null)
                            } else {
                                val opc = if (signed) Opcode.ASRN else Opcode.LSRN
                                addInstr(inplaceInstrs, IRInstructions.binary(opc, targetDt, oldvalueReg, operandTr.resultReg), null)
                            }
                        }
                        "or=" -> {
                            val shortcutLabel = codeGen.createLabelName()
                            if(!codeGen.options.compTarget.cpu.statusBitsOnMultiByteOps)
                                addInstr(inplaceInstrs, IRInstructions.compareImmediate(targetDt, oldvalueReg, 0), null)
                            addInstr(inplaceInstrs, IRInstructions.branch(Opcode.BSTNE, codeLabel(shortcutLabel)), null)
                            val valueTr = exprGen.translateExpression(value)
                            inplaceInstrs += valueTr.chunks
                            addInstr(inplaceInstrs, IRInstructions.binary(Opcode.ORR, targetDt, oldvalueReg, valueTr.resultReg), null)
                            inplaceInstrs += IRCodeChunk(shortcutLabel, null)
                        }
                        "and=" -> {
                            val shortcutLabel = codeGen.createLabelName()
                            if(!codeGen.options.compTarget.cpu.statusBitsOnMultiByteOps)
                                addInstr(inplaceInstrs, IRInstructions.compareImmediate(targetDt, oldvalueReg, 0), null)
                            addInstr(inplaceInstrs, IRInstructions.branch(Opcode.BSTEQ, codeLabel(shortcutLabel)), null)
                            val valueTr = exprGen.translateExpression(value)
                            inplaceInstrs += valueTr.chunks
                            addInstr(inplaceInstrs, IRInstructions.binary(Opcode.ANDR, targetDt, oldvalueReg, valueTr.resultReg), null)
                            inplaceInstrs += IRCodeChunk(shortcutLabel, null)
                        }
                        "-" -> addInstr(inplaceInstrs, IRInstructions.unary(Opcode.NEG, targetDt, oldvalueReg), null)
                        "~" -> addInstr(inplaceInstrs, IRInstructions.unary(Opcode.INV, targetDt, oldvalueReg), null)
                        "not" -> addInstr(inplaceInstrs, IRInstructions.binaryImmediate(Opcode.XOR, targetDt, oldvalueReg, 1), null)
                        "+" -> { /* inplace + is a no-op */ }
                        else -> throw AssemblyError("invalid augmented assign operator ${augAssign.operator}")
                    }
                }
            }

            codeGen.storeValueAtPointersLocation(inplaceInstrs, addressReg, fieldOffset, pointerDeref.type, false, oldvalueReg)
            chunks = inplaceInstrs
        } else if(array?.pointerderef != null) {
            chunks = fallbackAssign(augAssign)
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
                "%=" -> operatorModuloInplace(symbol, array, constAddress, memTarget, targetDt, value, signed)
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
            addInstr(
                inplaceInstrs,
                IRInstructions.loadMemory(Opcode.LOADI, targetDt, oldvalueReg, IRMemory.indirect(addressReg, fieldOffset.toInt())),
                null
            )
        } else {
            addInstr(
                inplaceInstrs,
                IRInstructions.loadMemory(Opcode.LOADI, targetDt, oldvalueReg, IRMemory.indirect(addressReg, fieldOffset.toInt())),
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

    private fun inplacePrefix(operator: String, symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?, vmDt: IRDataType): IRCodeChunks {
        if(operator=="+")
            return emptyList()

        if(array!=null)
            return inplacePrefixArray(operator, array)

        val result = mutableListOf<IRCodeChunkBase>()
        if(constAddress==null && memory!=null) {
            val register = codeGen.registers.next(vmDt)
            val tr = exprGen.translateExpression(memory.address)
            addToResult(result, tr, tr.resultReg, -1)
            when(operator) {
                "-" -> {
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADI, vmDt, register, IRMemory.indirect(tr.resultReg, 0))
                        it += IRInstructions.unary(Opcode.NEG, vmDt, register)
                        it += IRInstructions.storeMemory(Opcode.STOREI, vmDt, register, IRMemory.indirect(tr.resultReg, 0))
                    }
                }
                "~" -> {
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADI, vmDt, register, IRMemory.indirect(tr.resultReg, 0))
                        it += IRInstructions.unary(Opcode.INV, vmDt, register)
                        it += IRInstructions.storeMemory(Opcode.STOREI, vmDt, register, IRMemory.indirect(tr.resultReg, 0))
                    }
                }
            }
        } else {
            val directMem = if(constAddress!=null) IRMemory.direct(constAddress) else IRMemory.direct(symbol!!)
            when (operator) {
                "-" -> addInstr(result, IRInstructions.memoryOp(Opcode.NEGM, vmDt, directMem), null)
                "~" -> addInstr(result, IRInstructions.memoryOp(Opcode.INVM, vmDt, directMem), null)
                "not" -> {
                    val regMask = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(vmDt, regMask, 1)
                        it += IRInstructions.memoryOp(Opcode.XORM, vmDt, directMem, regMask)
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
        val vmDt = codeGen.irType(array.type)
        val constIndex = array.index.asConstInteger()

        fun loadIndex(): Int {
            val tr = exprGen.translateExpression(array.index)
            addToResult(result, tr, tr.resultReg, -1)
            val (indexReg, _) = normalizeArrayIndex(result, tr)
            return indexReg
        }

        if(array.splitWords) {
            // handle split LSB/MSB arrays
            when(operator) {
                "+" -> { }
                "-" -> {
                    val arrayVariableName = array.variable!!.name
                    val skipCarryLabel = codeGen.createLabelName()
                    if(constIndex!=null) {
                        val negLsbReg = codeGen.registers.next(IRDataType.BYTE)
                        addInstr(result, IRInstructions.memoryOp(Opcode.NEGM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_lsb", constIndex)), null)
                        addInstr(result, IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, negLsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex)), null)
                        if(!codeGen.options.compTarget.cpu.statusBitsOnMultiByteOps)
                            addInstr(result, IRInstructions.compareImmediate(IRDataType.BYTE, negLsbReg, 0), null)
                        addInstr(result, IRInstructions.branch(Opcode.BSTEQ, codeLabel(skipCarryLabel)), null)
                        addInstr(result, IRInstructions.memoryOp(Opcode.INCM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex)), null)
                        addInstr(result, IRInstructions.memoryOp(Opcode.NEGM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex)), skipCarryLabel)
                    } else {
                        val indexReg = loadIndex()
                        val registerLsb = codeGen.registers.next(IRDataType.BYTE)
                        val registerMsb = codeGen.registers.next(IRDataType.BYTE)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, registerLsb, IRMemory.indexed(arrayVariableName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                            it += IRInstructions.unary(Opcode.NEG, IRDataType.BYTE, registerLsb)
                            it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, registerLsb, IRMemory.indexed(arrayVariableName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                            it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, registerMsb, IRMemory.indexed(arrayVariableName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
                            it += IRInstructions.unary(Opcode.NEG, IRDataType.BYTE, registerMsb)
                            it += IRInstructions.compareImmediate(IRDataType.BYTE, registerLsb, 0)
                            it += IRInstructions.branch(Opcode.BSTEQ, codeLabel(skipCarryLabel))
                            it += IRInstructions.unary(Opcode.DEC, IRDataType.BYTE, registerMsb)
                        }
                        result += IRCodeChunk(skipCarryLabel, null).also {
                            it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, registerMsb, IRMemory.indexed(arrayVariableName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
                        }
                    }
                }
                "~" -> {
                    val arrayVariableName = array.variable!!.name
                    if(constIndex!=null) {
                        addInstr(result, IRInstructions.memoryOp(Opcode.INVM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_lsb", constIndex)), null)
                        addInstr(result, IRInstructions.memoryOp(Opcode.INVM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex)), null)
                    } else {
                        val indexReg = loadIndex()
                        val register = codeGen.registers.next(IRDataType.BYTE)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, register, IRMemory.indexed(arrayVariableName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                            it += IRInstructions.unary(Opcode.INV, IRDataType.BYTE, register)
                            it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, register, IRMemory.indexed(arrayVariableName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                            it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, register, IRMemory.indexed(arrayVariableName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
                            it += IRInstructions.unary(Opcode.INV, IRDataType.BYTE, register)
                            it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, register, IRMemory.indexed(arrayVariableName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
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
                val arrayVariableName = array.variable!!.name
                if(constIndex!=null) {
                    addInstr(result, IRInstructions.memoryOp(Opcode.NEGM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize)), null)
                } else {
                    val indexReg = loadIndex()
                    val register = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, register, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType, scale=eltSize))
                        it += IRInstructions.unary(Opcode.NEG, vmDt, register)
                        it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, register, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType, scale=eltSize))
                    }
                }
            }
            "~" -> {
                val arrayVariableName = array.variable!!.name
                if(constIndex!=null) {
                    addInstr(result, IRInstructions.memoryOp(Opcode.INVM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize)), null)
                } else {
                    val indexReg = loadIndex()
                    val register = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, register, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType, scale=eltSize))
                        it += IRInstructions.unary(Opcode.INV, vmDt, register)
                        it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, register, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType, scale=eltSize))
                    }
                }
            }
            "not" -> {
                val arrayVariableName = array.variable!!.name
                val register = codeGen.registers.next(vmDt)
                if(constIndex!=null) {
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(vmDt, register, 1)
                        it += IRInstructions.memoryOp(Opcode.XORM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), register)
                    }
                } else {
                    val indexReg = loadIndex()
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, register, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                        it += IRInstructions.binaryImmediate(Opcode.XOR, vmDt, register, 1)
                        it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, register, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
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

        val valueDt = codeGen.irType(assignment.value.type)
        val targetDt = codeGen.irType(assignment.target.type)
        val result = mutableListOf<IRCodeChunkBase>()

        var valueRegister = -1
        var valueFpRegister = -1
        val zero = codeGen.isZero(assignment.value)
        // STOREIM only helps when the target is a simple identifier or a fixed address;
        // for computed memory targets we still need a register to hold the value.
        val canUseStoreIm = assignment.target.identifier != null ||
                (assignment.target.memory?.address is PtNumber)
        val constInt = if(canUseStoreIm && targetDt != IRDataType.FLOAT && !zero) assignment.value.asConstInteger() else null
        val constFloat = if(canUseStoreIm && targetDt == IRDataType.FLOAT && !zero) assignment.value.asConstValue() else null
        if(!zero && constInt==null && constFloat==null) {
            // calculate the assignment value
            if (valueDt == IRDataType.FLOAT) {
                val tr = exprGen.translateExpression(assignment.value)
                valueFpRegister = tr.resultFpReg
                addToResult(result, tr, -1, valueFpRegister)
            } else {
                // determine if value needs extension or truncation to match target type
                // extension happens for byte→word, byte→long/pointer, or word→long/pointer
                // dst implied by opcode: EXT B/W = b->w / w->l, EXTL B = b->l (single type specifier, see IRInstructions.kt:834)
                val isPointerLong = codeGen.options.compTarget.POINTER_MEM_SIZE > 2u
                val extendSourceDt: IRDataType?
                val extendDestDt: IRDataType?
                val extendSigned: Boolean
                run {
                    val valueIsSigned = assignment.value.type.isSigned
                    when {
                        targetDt == valueDt -> { extendSourceDt = null; extendDestDt = null; extendSigned = false }
                        targetDt==IRDataType.WORD && valueDt==IRDataType.BYTE -> { extendSourceDt = IRDataType.BYTE; extendDestDt = IRDataType.WORD; extendSigned = valueIsSigned }
                        targetDt==IRDataType.POINTER && valueDt==IRDataType.BYTE -> {
                            if(isPointerLong) { extendSourceDt = IRDataType.BYTE; extendDestDt = IRDataType.LONG; extendSigned = valueIsSigned }
                            else { extendSourceDt = IRDataType.BYTE; extendDestDt = IRDataType.WORD; extendSigned = valueIsSigned }
                        }
                        targetDt==IRDataType.LONG && valueDt==IRDataType.BYTE -> { extendSourceDt = IRDataType.BYTE; extendDestDt = IRDataType.LONG; extendSigned = valueIsSigned }
                        targetDt==IRDataType.LONG && valueDt==IRDataType.WORD -> { extendSourceDt = IRDataType.WORD; extendDestDt = IRDataType.LONG; extendSigned = valueIsSigned }
                        targetDt==IRDataType.POINTER && valueDt==IRDataType.WORD -> {
                            if(isPointerLong) { extendSourceDt = IRDataType.WORD; extendDestDt = IRDataType.LONG; extendSigned = valueIsSigned }
                            else { extendSourceDt = null; extendDestDt = null; extendSigned = false }
                        }
                        targetDt==IRDataType.POINTER && valueDt==IRDataType.LONG -> { extendSourceDt = null; extendDestDt = null; extendSigned = false }
                        valueDt==IRDataType.POINTER && targetDt==IRDataType.WORD -> { extendSourceDt = null; extendDestDt = null; extendSigned = false } // truncation via LSIG elsewhere, or no op if same size
                        valueDt==IRDataType.POINTER && targetDt==IRDataType.LONG -> {
                            if(isPointerLong) { extendSourceDt = null; extendDestDt = null; extendSigned = false }
                            else { extendSourceDt = IRDataType.WORD; extendDestDt = IRDataType.LONG; extendSigned = valueIsSigned }
                        }
                        valueDt==IRDataType.LONG && targetDt==IRDataType.WORD -> { extendSourceDt = null; extendDestDt = null; extendSigned = false }
                        valueDt==IRDataType.LONG && targetDt==IRDataType.POINTER -> {
                            if(isPointerLong) { extendSourceDt = null; extendDestDt = null; extendSigned = false }
                            else { extendSourceDt = null; extendDestDt = null; extendSigned = false }
                        }
                        else -> throw AssemblyError("assignment value and target dt mismatch: $valueDt -> $targetDt")
                    }
                }
                fun extOpcode(src: IRDataType, dest: IRDataType, signed: Boolean): Opcode = when {
                    src==IRDataType.BYTE && dest==IRDataType.WORD -> if(signed) Opcode.EXTS else Opcode.EXT
                    src==IRDataType.WORD && dest==IRDataType.LONG -> if(signed) Opcode.EXTS else Opcode.EXT
                    src==IRDataType.BYTE && dest==IRDataType.LONG -> if(signed) Opcode.EXTLS else Opcode.EXTL
                    else -> throw AssemblyError("unsupported extension $src -> $dest")
                }
                if (assignment.value is PtIrRegister) {
                    valueRegister = (assignment.value as PtIrRegister).register
                    if(extendSourceDt != null) {
                        val opcode = extOpcode(extendSourceDt, extendDestDt!!, extendSigned)
                        valueRegister = codeGen.registers.next(extendDestDt)
                        addInstr(result, IRInstructions.binary(opcode, extendSourceDt, valueRegister, (assignment.value as PtIrRegister).register), null)
                    }
                } else {
                    val tr = exprGen.translateExpression(assignment.value)
                    valueRegister = tr.resultReg
                    addToResult(result, tr, valueRegister, -1)
                    if(extendSourceDt != null) {
                        val opcode = extOpcode(extendSourceDt, extendDestDt!!, extendSigned)
                        valueRegister = codeGen.registers.next(extendDestDt)
                        addInstr(result, IRInstructions.binary(opcode, extendSourceDt, valueRegister, tr.resultReg), null)
                    }
                }
            }
        }

        with(assignment.target) {
            when {
                identifier != null -> {
                    val instruction = when {
                        zero -> IRInstructions.storeZero(Opcode.STOREZM, targetDt, IRMemory.direct(identifier!!.name))
                        constInt != null -> {
                            val v = when(targetDt) {
                                IRDataType.BYTE -> constInt and 0xff
                                IRDataType.WORD, IRDataType.POINTER -> constInt and 0xffff
                                IRDataType.LONG -> constInt
                                else -> throw AssemblyError("invalid target dt $targetDt for const store")
                            }
                            IRInstructions.storeImmediate(targetDt, v, IRMemory.direct(identifier!!.name))
                        }
                        constFloat != null -> {
                            IRInstructions.storeImmediateFloat(constFloat, IRMemory.direct(identifier!!.name))
                        }
                        targetDt == IRDataType.FLOAT -> {
                            require(valueFpRegister>=0)
                            IRInstructions.storeMemory(Opcode.STOREM, targetDt, valueFpRegister, IRMemory.direct(identifier!!.name))
                        }
                        else -> {
                            require(valueRegister>=0)
                            IRInstructions.storeMemory(Opcode.STOREM, targetDt, valueRegister, IRMemory.direct(identifier!!.name))
                        }
                    }
                    result += IRCodeChunk(null, null).also { it += instruction }
                    return result
                }
                memory != null -> {
                    if(tryFoldStructArrayDirectWrite(memory!!.address, zero, valueRegister, targetDt, result)) return result
                    require(targetDt == IRDataType.BYTE) { "must be byte type ${memory!!.position}"}
                    if(zero) {
                        if(memory!!.address is PtNumber) {
                            val chunk = IRCodeChunk(null, null).also { it += IRInstructions.storeZero(Opcode.STOREZM, targetDt, IRMemory.direct((memory!!.address as PtNumber).number.toUInt().toAddress())) }
                            result += chunk
                        } else {
                            val (address, offset) = exprGen.getAddressAndOffset(memory!!.address)
                            if(address!=null) {
                                val tr = exprGen.translateExpression(address)
                                addToResult(result, tr, tr.resultReg, -1)
                                addInstr(result, IRInstructions.storeZero(Opcode.STOREZI, targetDt, IRMemory.indirect(tr.resultReg, offset!!)), null)
                            } else {
                                val tr = exprGen.translateExpression(memory!!.address)
                                addToResult(result, tr, tr.resultReg, -1)
                                addInstr(result, IRInstructions.storeZero(Opcode.STOREZI, targetDt, IRMemory.indirect(tr.resultReg, 0)), null)
                            }
                        }
                    } else {
                        val constAddress = memory!!.address as? PtNumber
                        if(constAddress!=null) {
                            val storeIns = when {
                                constInt != null -> {
                                    val v = when(targetDt) {
                                        IRDataType.BYTE -> constInt and 0xff
                                        IRDataType.WORD, IRDataType.POINTER -> constInt and 0xffff
                                        IRDataType.LONG -> constInt
                                    }
                                    IRInstructions.storeImmediate(targetDt, v, IRMemory.direct(constAddress.number.toUInt().toAddress()))
                                }
                                else -> null
                            } ?: IRInstructions.storeMemory(Opcode.STOREM, targetDt, valueRegister, IRMemory.direct(constAddress.number.toUInt().toAddress()))
                            addInstr(result, storeIns, null)
                            return result
                        }
                        val ptrWithOffset = memory!!.address as? PtBinaryExpression
                        if(ptrWithOffset!=null) {
                            if(ptrWithOffset.operator=="+" && ptrWithOffset.left is PtIdentifier) {
                                val constOffset = (ptrWithOffset.right as? PtNumber)?.number?.toInt()
                                if(constOffset in 0..65535) {
                                    val ptrIdentifier = ptrWithOffset.left as PtIdentifier
                                    val dt = if(codeGen.options.compTarget.POINTER_MEM_SIZE > 2u && ptrIdentifier.type.isLong)
                                        IRDataType.POINTER else IRDataType.WORD
                                    val pointerReg = codeGen.registers.next(dt)
                                    result += IRCodeChunk(null, null).also {
                                        it += IRInstructions.loadMemory(Opcode.LOADM, dt, pointerReg, IRMemory.direct(ptrIdentifier.name))
                                        it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, valueRegister, IRMemory.indirect(pointerReg, constOffset!!))
                                    }
                                    return result
                                }
                            }
                        }

                        val tr = exprGen.translateExpression(memory!!.address)
                        val addressReg = tr.resultReg
                        addToResult(result, tr, tr.resultReg, -1)
                        addInstr(result, IRInstructions.storeMemory(Opcode.STOREI, targetDt, valueRegister, IRMemory.indirect(addressReg, 0)), null)
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
        val pointerTr = exprGen.translateExpression(targetIdent)
        result += pointerTr.chunks
        val pointerReg = pointerTr.resultReg

        val constIndex = targetArray.index.asConstInteger()
        if(zeroValue) {
            if(constIndex!=null) {
                val offset = eltSize * constIndex
                addInstr(result, IRInstructions.binaryImmediate(Opcode.ADD, IRDataType.POINTER, pointerReg, offset), null)
            } else {
                val (code, indexReg) = codeGen.loadIndexReg(targetArray.index, eltSize, true, targetArray.splitWords)
                result += code
                if(eltSize!=1)
                    result += codeGen.multiplyByConst(DataType.UWORD, indexReg, eltSize)
                addInstr(result, IRInstructions.binary(Opcode.ADDR, IRDataType.POINTER, pointerReg, indexReg), null)
            }
            codeGen.storeValueAtPointersLocation(result, pointerReg, 0u, targetIdent.type.dereference(), true, -1)
        } else {
            if(constIndex!=null) {
                val offset = eltSize * constIndex
                addInstr(result, IRInstructions.binaryImmediate(Opcode.ADD, IRDataType.POINTER, pointerReg, offset), null)
            } else {
                val (code, indexReg) = codeGen.loadIndexReg(targetArray.index, eltSize, true, targetArray.splitWords)
                result += code
                if(eltSize!=1)
                    result += codeGen.multiplyByConst(DataType.UWORD, indexReg, eltSize)
                addInstr(result, IRInstructions.binary(Opcode.ADDR, IRDataType.POINTER, pointerReg, indexReg), null)
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
                        it += IRInstructions.storeZero(Opcode.STOREZM, IRDataType.BYTE, IRMemory.direct("${variable}_lsb", fixedIndex))
                        it += IRInstructions.storeZero(Opcode.STOREZM, IRDataType.BYTE, IRMemory.direct("${variable}_msb", fixedIndex))
                    }
                    else
                        it += IRInstructions.storeZero(Opcode.STOREZM, targetDt, IRMemory.direct(variable, fixedIndex*eltSize))
                }
                result += chunk
            } else {
                val (code, indexReg) = codeGen.loadIndexReg(targetArray.index, eltSize, codeGen.wordArrayIndex, targetArray.splitWords)
                result += code
                val scaleZX = if(targetArray.splitWords) 1 else eltSize
                result += IRCodeChunk(null, null).also {
                    if(targetArray.splitWords) {
                        it += IRInstructions.storeZero(Opcode.STOREZX, IRDataType.BYTE, IRMemory.indexed(variable+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                        it += IRInstructions.storeZero(Opcode.STOREZX, IRDataType.BYTE, IRMemory.indexed(variable+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
                    }
                    else
                        it += IRInstructions.storeZero(Opcode.STOREZX, targetDt, IRMemory.indexed(variable, indexReg, codeGen.options.compTarget.indexRegType, scale=scaleZX))
                }
            }
        } else {
            if(targetDt== IRDataType.FLOAT) {
                if(fixedIndex!=null) {
                    val offset = fixedIndex*eltSize
                    val chunk = IRCodeChunk(null, null).also {
                        it += IRInstructions.storeMemory(Opcode.STOREM, targetDt, valueFpRegister, IRMemory.direct(variable, offset))
                    }
                    result += chunk
                } else {
                    val (code, indexReg) = codeGen.loadIndexReg(targetArray.index, eltSize, codeGen.wordArrayIndex, targetArray.splitWords)
                    result += code
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.storeMemory(Opcode.STOREX, targetDt, valueFpRegister, IRMemory.indexed(variable, indexReg, codeGen.options.compTarget.indexRegType, scale=eltSize))
                    }
                }
            } else {
                if(fixedIndex!=null) {
                    val chunk = IRCodeChunk(null, null).also {
                        if(targetArray.splitWords) {
                            val lsbmsbReg = codeGen.registers.next(IRDataType.BYTE)
                            it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, lsbmsbReg, valueRegister)
                            it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, lsbmsbReg, IRMemory.direct("${variable}_lsb", fixedIndex))
                            it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, lsbmsbReg, valueRegister)
                            it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, lsbmsbReg, IRMemory.direct("${variable}_msb", fixedIndex))
                        }
                        else
                            it += IRInstructions.storeMemory(Opcode.STOREM, targetDt, valueRegister, IRMemory.direct(variable, fixedIndex*eltSize))
                    }
                    result += chunk
                } else {
                    val (code, indexReg) = codeGen.loadIndexReg(targetArray.index, eltSize, codeGen.wordArrayIndex, targetArray.splitWords)
                    result += code
                    result += IRCodeChunk(null, null).also {
                        if(targetArray.splitWords) {
                            val lsbmsbReg = codeGen.registers.next(IRDataType.BYTE)
                            it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, lsbmsbReg, valueRegister)
                            it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, lsbmsbReg, IRMemory.indexed("${variable}_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                            it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, lsbmsbReg, valueRegister)
                            it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, lsbmsbReg, IRMemory.indexed("${variable}_msb", indexReg, codeGen.options.compTarget.indexRegType))
                        }
                        else
                            it += IRInstructions.storeMemory(Opcode.STOREX, targetDt, valueRegister, IRMemory.indexed(variable, indexReg, codeGen.options.compTarget.indexRegType, scale=eltSize))
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
        val pointerTr = exprGen.translateExpression(pointerderef)
        result += pointerTr.chunks
        val pointerReg = pointerTr.resultReg

        val fixedIndex = targetArray.index.asConstInteger()
        if(fixedIndex!=null) {
            val offset = fixedIndex*eltSize
            if(zero) {
                addInstr(result, IRInstructions.storeZero(Opcode.STOREZI, targetDt, IRMemory.indirect(pointerReg, offset)), null)
            } else {
                addInstr(
                    result,
                    if (targetDt == IRDataType.FLOAT)
                        IRInstructions.storeMemory(Opcode.STOREI, IRDataType.FLOAT, valueFpRegister, IRMemory.indirect(pointerReg, offset))
                    else
                        IRInstructions.storeMemory(Opcode.STOREI, targetDt, valueRegister, IRMemory.indirect(pointerReg, offset)), null
                )
            }
        } else {
            // index is an expression
            val (code, indexReg) = codeGen.loadIndexReg(targetArray.index, eltSize, true, targetArray.splitWords)
            result += code
            if(eltSize!=1)
                result += codeGen.multiplyByConst(DataType.UWORD, indexReg, eltSize)
            addInstr(result, IRInstructions.binary(Opcode.ADDR, IRDataType.POINTER, pointerReg, indexReg), null)
            if(zero) {
                addInstr(result, IRInstructions.storeZero(Opcode.STOREZI, targetDt, IRMemory.indirect(pointerReg, 0)), null)
            } else {
                addInstr(result, if(targetDt== IRDataType.FLOAT)
                        IRInstructions.storeMemory(Opcode.STOREI, IRDataType.FLOAT, valueFpRegister, IRMemory.indirect(pointerReg, 0))
                    else
                        IRInstructions.storeMemory(Opcode.STOREI, targetDt, valueRegister, IRMemory.indirect(pointerReg, 0))
                    , null)
            }
        }
    }

    private fun operatorAndInplace(symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val arrayVariableName = array.variable!!.name
            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            if(constIndex!=null && constValue!=null) {
                if(array.splitWords) {
                    val valueRegLsb = codeGen.registers.next(IRDataType.BYTE)
                    val valueRegMsb = codeGen.registers.next(IRDataType.BYTE)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(IRDataType.BYTE, valueRegLsb, constValue and 255)
                        it += IRInstructions.load(IRDataType.BYTE, valueRegMsb, constValue shr 8)
                        it += IRInstructions.memoryOp(Opcode.ANDM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_lsb", constIndex), valueRegLsb)
                        it += IRInstructions.memoryOp(Opcode.ANDM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex), valueRegMsb)
                    }
                } else {
                    val valueReg = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(vmDt, valueReg, constValue)
                        it += IRInstructions.memoryOp(Opcode.ANDM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueReg)
                    }
                }
                return result
            }
            // Non-const cases - handle split and non-split arrays differently
            if(array.splitWords) {
                // Split word array: extract operand bytes and AND separately
                if(constIndex!=null) {
                    // Constant index, non-constant value
                    val valueTr = exprGen.translateExpression(operand)
                    addToResult(result, valueTr, valueTr.resultReg, -1)
                    if(valueTr.resultReg < 0) return null
                    val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                    val msbReg = codeGen.registers.next(IRDataType.BYTE)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, lsbReg, valueTr.resultReg)
                        it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, msbReg, valueTr.resultReg)
                        it += IRInstructions.memoryOp(Opcode.ANDM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_lsb", constIndex), lsbReg)
                        it += IRInstructions.memoryOp(Opcode.ANDM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex), msbReg)
                    }
                    return result
                }
                // Non-constant index - use LOADX for each byte array
                val indexTr = exprGen.translateExpression(array.index)
                addToResult(result, indexTr, indexTr.resultReg, -1)
                val (indexReg, _) = normalizeArrayIndex(result, indexTr)
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                if(valueTr.resultReg < 0) return null
                val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                val msbReg = codeGen.registers.next(IRDataType.BYTE)
                val valLsbReg = codeGen.registers.next(IRDataType.BYTE)
                val valMsbReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, valLsbReg, valueTr.resultReg)
                    it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, valMsbReg, valueTr.resultReg)
                    it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, lsbReg, IRMemory.indexed(arrayVariableName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, msbReg, IRMemory.indexed(arrayVariableName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.binary(Opcode.ANDR, IRDataType.BYTE, lsbReg, valLsbReg)
                    it += IRInstructions.binary(Opcode.ANDR, IRDataType.BYTE, msbReg, valMsbReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, lsbReg, IRMemory.indexed(arrayVariableName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, msbReg, IRMemory.indexed(arrayVariableName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
                }
                return result
            }
            // Non-split array cases
            if(constIndex!=null) {
                // Constant index, non-constant value
                val arrayVariableName = array.variable!!.name
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                addInstr(result, IRInstructions.memoryOp(Opcode.ANDM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueTr.resultReg), null)
                return result
            }
            // Non-constant index - use LOADX/ANDR/STOREX
            val indexTr = exprGen.translateExpression(array.index)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            val (indexReg, indexDt) = normalizeArrayIndex(result, indexTr)
            val arrayVarName = array.variable!!.name
            // Multiply index by element size for LOADX/STOREX
            if(eltSize > 1) {
                result += codeGen.multiplyByConst(indexDt, indexReg, eltSize)
            }
            if(constValue!=null) {
                val loadReg = codeGen.registers.next(vmDt)
                val constReg = codeGen.registers.next(vmDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, loadReg, IRMemory.indexed(arrayVarName, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.load(vmDt, constReg, constValue)
                    it += IRInstructions.binary(Opcode.ANDR, vmDt, loadReg, constReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(arrayVarName, indexReg, codeGen.options.compTarget.indexRegType))
                }
            } else {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(vmDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, loadReg, IRMemory.indexed(arrayVarName, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.binary(Opcode.ANDR, vmDt, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(arrayVarName, indexReg, codeGen.options.compTarget.indexRegType))
                }
            }
            return result
        }
        if(constAddress==null && memory!=null) {
            // Memory target with non-constant address
            val result = mutableListOf<IRCodeChunkBase>()
            val addrTr = exprGen.translateExpression(memory.address)
            addToResult(result, addrTr, addrTr.resultReg, -1)
            val addressReg = addrTr.resultReg
            val operandConstValue = (operand as? PtNumber)?.number
            
            if(operandConstValue==null) {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.binary(Opcode.ANDR, IRDataType.BYTE, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                }
            } else {
                val loadReg = codeGen.registers.next(IRDataType.BYTE)
                val valueReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.load(IRDataType.BYTE, valueReg, operandConstValue.toInt())
                    it += IRInstructions.binary(Opcode.ANDR, IRDataType.BYTE, loadReg, valueReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                }
            }
            return result
        }

        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(operand)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, if(constAddress!=null)
            IRInstructions.memoryOp(Opcode.ANDM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultReg)
        else
            IRInstructions.memoryOp(Opcode.ANDM, vmDt, IRMemory.direct(symbol!!), tr.resultReg),null)
        return result
    }

    private fun operatorLogicalAndInplace(symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        if(array!=null) {
            val arrayVariableName = array.variable!!.name
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
                        it += IRInstructions.load(vmDt, valueReg, constValue)
                        it += IRInstructions.memoryOp(Opcode.ANDM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueReg)
                    }
                }
                return result
            }
            // Non-const cases for non-split arrays (logical and = bitwise and for arrays)
            if(constIndex!=null) {
                // Constant index, non-constant value
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                addInstr(result, IRInstructions.memoryOp(Opcode.ANDM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueTr.resultReg), null)
                return result
            }
            // Non-constant index
            val indexTr = exprGen.translateExpression(array.index)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            val (indexReg, _) = normalizeArrayIndex(result, indexTr)
            if(constValue!=null) {
                val loadReg = codeGen.registers.next(vmDt)
                val constReg = codeGen.registers.next(vmDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.load(vmDt, constReg, constValue)
                    it += IRInstructions.binary(Opcode.ANDR, vmDt, loadReg, constReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                }
            } else {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(vmDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.binary(Opcode.ANDR, vmDt, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                }
            }
            return result
        }
        if(constAddress==null && memory!=null) {
            // Memory target with non-constant address
            val memResult = mutableListOf<IRCodeChunkBase>()
            val addrTr = exprGen.translateExpression(memory.address)
            addToResult(memResult, addrTr, addrTr.resultReg, -1)
            val addressReg = addrTr.resultReg
            val operandConstValue = (operand as? PtNumber)?.number
            
            if(operandConstValue==null) {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(memResult, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(IRDataType.BYTE)
                memResult += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.simple(Opcode.ANDR)
                    it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                }
            } else {
                val loadReg = codeGen.registers.next(IRDataType.BYTE)
                val valueReg = codeGen.registers.next(IRDataType.BYTE)
                memResult += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.load(IRDataType.BYTE, valueReg, operandConstValue.toInt())
                    it += IRInstructions.binary(Opcode.ANDR, IRDataType.BYTE, loadReg, valueReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                }
            }
            return memResult
        }

        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(operand)
        if(!operand.isSimple()) {
            // short-circuit  LEFT and RIGHT  -->  if LEFT then RIGHT else LEFT   (== if !LEFT then LEFT else RIGHT)
            val inplaceReg = codeGen.registers.next(vmDt)
            val shortcutLabel = codeGen.createLabelName()
            result += IRCodeChunk(null, null).also {
                it += if(constAddress!=null)
                    IRInstructions.loadMemory(Opcode.LOADM, vmDt, inplaceReg, IRMemory.direct(constAddress.toAddress()))
                else
                    IRInstructions.loadMemory(Opcode.LOADM, vmDt, inplaceReg, IRMemory.direct(symbol!!))
                if(!codeGen.options.compTarget.cpu.statusBitsOnMultiByteOps)
                    it += IRInstructions.compareImmediate(vmDt, inplaceReg, 0)
                it += IRInstructions.branch(Opcode.BSTEQ, codeLabel(shortcutLabel))
            }
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, if(constAddress!=null)
                IRInstructions.storeMemory(Opcode.STOREM, vmDt, tr.resultReg, IRMemory.direct(constAddress.toAddress()))
            else
                IRInstructions.storeMemory(Opcode.STOREM, vmDt, tr.resultReg, IRMemory.direct(symbol!!)), null)
            result += IRCodeChunk(shortcutLabel, null)
        } else {
            // normal evaluation, it is *likely* shorter and faster because of the simple operands.
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, if(constAddress!=null)
                IRInstructions.memoryOp(Opcode.ANDM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultReg)
            else
                IRInstructions.memoryOp(Opcode.ANDM, vmDt, IRMemory.direct(symbol!!), tr.resultReg),null)
        }
        return result
    }

    private fun operatorOrInplace(symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            if(constIndex!=null && constValue!=null) {
                val arrayVariableName = array.variable!!.name
                if(array.splitWords) {
                    val valueRegLsb = codeGen.registers.next(IRDataType.BYTE)
                    val valueRegMsb = codeGen.registers.next(IRDataType.BYTE)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(IRDataType.BYTE, valueRegLsb, constValue and 255)
                        it += IRInstructions.load(IRDataType.BYTE, valueRegMsb, constValue shr 8)
                        it += IRInstructions.memoryOp(Opcode.ORM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_lsb", constIndex), valueRegLsb)
                        it += IRInstructions.memoryOp(Opcode.ORM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex), valueRegMsb)
                    }
                } else {
                    val valueReg = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(vmDt, valueReg, constValue)
                        it += IRInstructions.memoryOp(Opcode.ORM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueReg)
                    }
                }
                return result
            }
            // Non-const cases - handle split and non-split arrays differently
            if(array.splitWords) {
                val arrayVariableName = array.variable!!.name
                if(constIndex!=null) {
                    val valueTr = exprGen.translateExpression(operand)
                    addToResult(result, valueTr, valueTr.resultReg, -1)
                    if(valueTr.resultReg < 0) return null
                    val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                    val msbReg = codeGen.registers.next(IRDataType.BYTE)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, lsbReg, valueTr.resultReg)
                        it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, msbReg, valueTr.resultReg)
                        it += IRInstructions.memoryOp(Opcode.ORM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_lsb", constIndex), lsbReg)
                        it += IRInstructions.memoryOp(Opcode.ORM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex), msbReg)
                    }
                    return result
                }
                val indexTr = exprGen.translateExpression(array.index)
                addToResult(result, indexTr, indexTr.resultReg, -1)
                val (indexReg, _) = normalizeArrayIndex(result, indexTr)
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                if(valueTr.resultReg < 0) return null
                val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                val msbReg = codeGen.registers.next(IRDataType.BYTE)
                val valLsbReg = codeGen.registers.next(IRDataType.BYTE)
                val valMsbReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, valLsbReg, valueTr.resultReg)
                    it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, valMsbReg, valueTr.resultReg)
                    it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, lsbReg, IRMemory.indexed(arrayVariableName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, msbReg, IRMemory.indexed(arrayVariableName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.binary(Opcode.ORR, IRDataType.BYTE, lsbReg, valLsbReg)
                    it += IRInstructions.binary(Opcode.ORR, IRDataType.BYTE, msbReg, valMsbReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, lsbReg, IRMemory.indexed(arrayVariableName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, msbReg, IRMemory.indexed(arrayVariableName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
                }
                return result
            }
            // Non-split array cases
            if(constIndex!=null) {
                val arrayVarName = array.variable!!.name
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                addInstr(result, IRInstructions.memoryOp(Opcode.ORM, vmDt, IRMemory.direct(arrayVarName, constIndex*eltSize), valueTr.resultReg), null)
                return result
            }
            // Non-constant index - use LOADX/ORR/STOREX
            val indexTr = exprGen.translateExpression(array.index)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            val (indexReg, indexDt) = normalizeArrayIndex(result, indexTr)
            val arrayVarName2 = array.variable!!.name
            // Multiply index by element size for LOADX/STOREX
            if(eltSize > 1) {
                result += codeGen.multiplyByConst(indexDt, indexReg, eltSize)
            }
            if(constValue!=null) {
                val loadReg = codeGen.registers.next(vmDt)
                val constReg = codeGen.registers.next(vmDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, loadReg, IRMemory.indexed(arrayVarName2, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.load(vmDt, constReg, constValue)
                    it += IRInstructions.binary(Opcode.ORR, vmDt, loadReg, constReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(arrayVarName2, indexReg, codeGen.options.compTarget.indexRegType))
                }
            } else {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(vmDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, loadReg, IRMemory.indexed(arrayVarName2, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.binary(Opcode.ORR, vmDt, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(arrayVarName2, indexReg, codeGen.options.compTarget.indexRegType))
                }
            }
            return result
        }
        if(constAddress==null && memory!=null) {
            // Memory target with non-constant address
            val memResult = mutableListOf<IRCodeChunkBase>()
            val addrTr = exprGen.translateExpression(memory.address)
            addToResult(memResult, addrTr, addrTr.resultReg, -1)
            val addressReg = addrTr.resultReg
            val operandConstValue = (operand as? PtNumber)?.number
            
            if(operandConstValue==null) {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(memResult, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(IRDataType.BYTE)
                memResult += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.binary(Opcode.ORR, IRDataType.BYTE, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                }
            } else {
                val loadReg = codeGen.registers.next(IRDataType.BYTE)
                val valueReg = codeGen.registers.next(IRDataType.BYTE)
                memResult += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.load(IRDataType.BYTE, valueReg, operandConstValue.toInt())
                    it += IRInstructions.binary(Opcode.ORR, IRDataType.BYTE, loadReg, valueReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                }
            }
            return memResult
        }

        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(operand)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, if(constAddress!=null)
            IRInstructions.memoryOp(Opcode.ORM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultReg)
        else
            IRInstructions.memoryOp(Opcode.ORM, vmDt, IRMemory.direct(symbol!!), tr.resultReg), null)
        return result
    }

    private fun operatorLogicalOrInplace(symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        if(array!=null) {
            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            if(constIndex!=null && constValue!=null) {
                if(array.splitWords) {
                    throw AssemblyError("logical or on (split) word array should not happen")
                } else {
                    val arrayVariableName = array.variable!!.name
                    val valueReg = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(vmDt, valueReg, constValue)
                        it += IRInstructions.memoryOp(Opcode.ORM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueReg)
                    }
                }
                return result
            }
            // Non-const cases - split words not supported for logical or
            if(array.splitWords) {
                throw AssemblyError("logical or on (split) word array with non-const operand should not happen")
            }
            // Non-split array cases
            if(constIndex!=null) {
                val arrayVarName = array.variable!!.name
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                addInstr(result, IRInstructions.memoryOp(Opcode.ORM, vmDt, IRMemory.direct(arrayVarName, constIndex*eltSize), valueTr.resultReg), null)
                return result
            }
            // Non-constant index
            val indexTr = exprGen.translateExpression(array.index)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            val (indexReg, _) = normalizeArrayIndex(result, indexTr)
            val arrayVarName2 = array.variable!!.name
            if(constValue!=null) {
                val loadReg = codeGen.registers.next(vmDt)
                val constReg = codeGen.registers.next(vmDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, loadReg, IRMemory.indexed(arrayVarName2, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.load(vmDt, constReg, constValue)
                    it += IRInstructions.binary(Opcode.ORR, vmDt, loadReg, constReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(arrayVarName2, indexReg, codeGen.options.compTarget.indexRegType))
                }
            } else {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(vmDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, loadReg, IRMemory.indexed(arrayVarName2, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.binary(Opcode.ORR, vmDt, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(arrayVarName2, indexReg, codeGen.options.compTarget.indexRegType))
                }
            }
            return result
        }
        if(constAddress==null && memory!=null) {
            // Memory target with non-constant address
            val memResult = mutableListOf<IRCodeChunkBase>()
            val addrTr = exprGen.translateExpression(memory.address)
            addToResult(memResult, addrTr, addrTr.resultReg, -1)
            val addressReg = addrTr.resultReg
            val operandConstValue = (operand as? PtNumber)?.number
            
            if(operandConstValue==null) {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(memResult, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(IRDataType.BYTE)
                memResult += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.binary(Opcode.ORR, IRDataType.BYTE, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                }
            } else {
                val loadReg = codeGen.registers.next(IRDataType.BYTE)
                val valueReg = codeGen.registers.next(IRDataType.BYTE)
                memResult += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.load(IRDataType.BYTE, valueReg, operandConstValue.toInt())
                    it += IRInstructions.binary(Opcode.ORR, IRDataType.BYTE, loadReg, valueReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                }
            }
            return memResult
        }

        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(operand)
        if(!operand.isSimple()) {
            // short-circuit  LEFT or RIGHT  -->  if LEFT then LEFT else RIGHT
            val inplaceReg = codeGen.registers.next(vmDt)
            val shortcutLabel = codeGen.createLabelName()
            result += IRCodeChunk(null, null).also {
                it += if(constAddress!=null)
                    IRInstructions.loadMemory(Opcode.LOADM, vmDt, inplaceReg, IRMemory.direct(constAddress.toAddress()))
                else
                    IRInstructions.loadMemory(Opcode.LOADM, vmDt, inplaceReg, IRMemory.direct(symbol!!))
                if(!codeGen.options.compTarget.cpu.statusBitsOnMultiByteOps)
                    it += IRInstructions.compareImmediate(vmDt, inplaceReg, 0)
                it += IRInstructions.branch(Opcode.BSTNE, codeLabel(shortcutLabel))
            }
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, if(constAddress!=null)
                IRInstructions.storeMemory(Opcode.STOREM, vmDt, tr.resultReg, IRMemory.direct(constAddress.toAddress()))
            else
                IRInstructions.storeMemory(Opcode.STOREM, vmDt, tr.resultReg, IRMemory.direct(symbol!!)), null)
            result += IRCodeChunk(shortcutLabel, null)
        } else {
            // normal evaluation, it is *likely* shorter and faster because of the simple operands.
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, if(constAddress!=null)
                IRInstructions.memoryOp(Opcode.ORM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultReg)
            else
                IRInstructions.memoryOp(Opcode.ORM, vmDt, IRMemory.direct(symbol!!), tr.resultReg), null)
        }
        return result
    }

    private fun operatorDivideInplace(symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression, signed: Boolean): IRCodeChunks {
        if(array!=null) {
            if(array.splitWords)
                return operatorDivideInplaceSplitArray(array, operand, signed)
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            val result = mutableListOf<IRCodeChunkBase>()
            val eltDt = codeGen.irType(array.type)
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            val arrayVariableName = array.variable!!.name
            val divOpcode = if(signed) Opcode.DIVSM else Opcode.DIVM
            val divROpcode = if(signed) Opcode.DIVSR else Opcode.DIVR
            if(eltDt==IRDataType.FLOAT) {
                val constFloat = (operand as? PtNumber)?.number
                if(constIndex!=null) {
                    val valueTr = if(constFloat==null) exprGen.translateExpression(operand) else null
                    val result = mutableListOf<IRCodeChunkBase>()
                    if(valueTr!=null)
                        addToResult(result, valueTr, -1, valueTr.resultFpReg)
                    result += IRCodeChunk(null, null).also {
                        val valueReg = if(constFloat!=null) {
                            val reg = codeGen.registers.next(IRDataType.FLOAT)
                            it += IRInstructions.loadFloat(reg, constFloat)
                            reg
                        } else
                            valueTr!!.resultFpReg
                        it += IRInstructions.memoryOp(divOpcode, IRDataType.FLOAT, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueReg)
                    }
                    return result
                }
                val indexTr = exprGen.translateExpression(array.index)
                val result = mutableListOf<IRCodeChunkBase>()
                addToResult(result, indexTr, indexTr.resultReg, -1)
                val (indexReg, indexDt) = normalizeArrayIndex(result, indexTr)
                if(eltSize > 1)
                    result += codeGen.multiplyByConst(indexDt, indexReg, eltSize)
                val loadReg = codeGen.registers.next(IRDataType.FLOAT)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.FLOAT, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                }
                val valueTr = if(constFloat==null) exprGen.translateExpression(operand) else null
                if(valueTr!=null)
                    addToResult(result, valueTr, -1, valueTr.resultFpReg)
                result += IRCodeChunk(null, null).also {
                    if(constFloat!=null)
                        it += IRInstructions.binaryImmediateFloat(divROpcode, loadReg, constFloat)
                    else
                        it += IRInstructions.binary(divROpcode, IRDataType.FLOAT, loadReg, valueTr!!.resultFpReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.FLOAT, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                }
                return result
            }
            if(constIndex!=null && constValue!=null) {
                if(constValue!=1) {
                    val valueReg = codeGen.registers.next(eltDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(eltDt, valueReg, constValue)
                        it += IRInstructions.memoryOp(divOpcode, eltDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueReg)
                    }
                }
                return result
            }
            if(constIndex!=null) {
                // Constant index, non-constant value
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                addInstr(result, IRInstructions.memoryOp(divOpcode, eltDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueTr.resultReg), null)
                return result
            }
            // Non-constant index - use LOADX/DIVR/STOREX
            val indexTr = exprGen.translateExpression(array.index)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            val (indexReg, indexDt) = normalizeArrayIndex(result, indexTr)
            if(eltSize > 1) {
                result += codeGen.multiplyByConst(indexDt, indexReg, eltSize)
            }
            val loadReg = codeGen.registers.next(eltDt)
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.loadMemory(Opcode.LOADX, eltDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
            }
            if(constValue!=null) {
                val constReg = codeGen.registers.next(eltDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.load(eltDt, constReg, constValue)
                    it += IRInstructions.binary(divROpcode, eltDt, loadReg, constReg)
                }
            } else {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.binary(divROpcode, eltDt, loadReg, valueTr.resultReg)
                }
            }
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.storeMemory(Opcode.STOREX, eltDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
            }
            return result
        }
        if(constAddress==null && memory!=null) {
            val resultVar = mutableListOf<IRCodeChunkBase>()
            val addrTr = exprGen.translateExpression(memory.address)
            addToResult(resultVar, addrTr, addrTr.resultReg, -1)
            val addressReg = addrTr.resultReg
            if(vmDt==IRDataType.FLOAT) {
                val tr = exprGen.translateExpression(operand)
                addToResult(resultVar, tr, -1, tr.resultFpReg)
                val loadReg = codeGen.registers.next(IRDataType.FLOAT)
                resultVar += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.binary(Opcode.DIVR, vmDt, loadReg, tr.resultFpReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
                }
            } else {
                val loadReg = codeGen.registers.next(vmDt)
                resultVar += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
                }
                val constVal = (operand as? PtNumber)?.number?.toInt()
                if(constVal!=null) {
                    val constReg = codeGen.registers.next(vmDt)
                    resultVar += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(vmDt, constReg, constVal)
                        val opc = if(signed) Opcode.DIVSR else Opcode.DIVR
                        it += IRInstructions.binary(opc, vmDt, loadReg, constReg)
                        it += IRInstructions.storeMemory(Opcode.STOREI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
                    }
                } else {
                    val opTr = exprGen.translateExpression(operand)
                    addToResult(resultVar, opTr, opTr.resultReg, -1)
                    resultVar += IRCodeChunk(null, null).also {
                        val opc = if(signed) Opcode.DIVSR else Opcode.DIVR
                        it += IRInstructions.binary(opc, vmDt, loadReg, opTr.resultReg)
                        it += IRInstructions.storeMemory(Opcode.STOREI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
                    }
                }
            }
            return resultVar
        }

        val result = mutableListOf<IRCodeChunkBase>()
        val constFactorRight = operand as? PtNumber
        if(vmDt==IRDataType.FLOAT) {
            if(constFactorRight!=null && !constFactorRight.type.isFloat) {
                val factor = constFactorRight.number
                result += codeGen.divideByConstFloatInplace(constAddress, symbol, factor)
            } else {
                val tr = exprGen.translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                val ins = if(signed) {
                    if(constAddress!=null)
                        IRInstructions.memoryOp(Opcode.DIVSM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultFpReg)
                    else
                        IRInstructions.memoryOp(Opcode.DIVSM, vmDt, IRMemory.direct(symbol!!), tr.resultFpReg)
                }
                else {
                    if(constAddress!=null)
                        IRInstructions.memoryOp(Opcode.DIVM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultFpReg)
                    else
                        IRInstructions.memoryOp(Opcode.DIVM, vmDt, IRMemory.direct(symbol!!), tr.resultFpReg)
                }
                addInstr(result, ins, null)
            }
        } else {
            if(constFactorRight!=null && !constFactorRight.type.isFloat) {
                val factor = constFactorRight.number.toInt()
                result += codeGen.divideByConstInplace(vmDt, constAddress, symbol, factor, signed)
            } else {
                val tr = exprGen.translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                val ins = if(signed) {
                    if(constAddress!=null)
                        IRInstructions.memoryOp(Opcode.DIVSM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultReg)
                    else
                        IRInstructions.memoryOp(Opcode.DIVSM, vmDt, IRMemory.direct(symbol!!), tr.resultReg)
                }
                else {
                    if(constAddress!=null)
                        IRInstructions.memoryOp(Opcode.DIVM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultReg)
                    else
                        IRInstructions.memoryOp(Opcode.DIVM, vmDt, IRMemory.direct(symbol!!), tr.resultReg)
                }
                addInstr(result, ins, null)
            }
        }
        return result
    }

    private fun operatorMultiplyInplace(symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression, signed: Boolean): IRCodeChunks? {
        if(array!=null) {
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            val result = mutableListOf<IRCodeChunkBase>()
            if(array.splitWords)
                return operatorMultiplyInplaceSplitArray(array, operand)
            val eltDt = codeGen.irType(array.type)
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            if(constIndex!=null && constValue!=null) {
                if(constValue!=1) {
                    val arrayVariableName = array.variable!!.name
                    val valueReg=codeGen.registers.next(eltDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(eltDt, valueReg, constValue)
                        val opcode = if(signed) Opcode.MULSM else Opcode.MULM
                        it += IRInstructions.memoryOp(opcode, eltDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueReg)
                    }
                }
                return result
            }
            // Non-const cases for non-split arrays
            if(constIndex!=null) {
                // Constant index, non-constant value
                val arrayVariableName = array.variable!!.name
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                val opcode = if(signed) Opcode.MULSM else Opcode.MULM
                addInstr(result, IRInstructions.memoryOp(opcode, eltDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueTr.resultReg), null)
                return result
            }
            // Non-constant index - use LOADX/MULR/STOREX
            val indexTr = exprGen.translateExpression(array.index)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            val (indexReg, indexDt) = normalizeArrayIndex(result, indexTr)
            val arrayVariableName = array.variable!!.name
            // Multiply index by element size for LOADX/STOREX
            if(eltSize > 1) {
                result += codeGen.multiplyByConst(indexDt, indexReg, eltSize)
            }
            if(constValue!=null) {
                val loadReg = codeGen.registers.next(eltDt)
                val constReg = codeGen.registers.next(eltDt)
                val opcode = if(signed) Opcode.MULSR else Opcode.MULR
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, eltDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.load(eltDt, constReg, constValue)
                    it += IRInstructions.binary(opcode, eltDt, loadReg, constReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, eltDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                }
            } else {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(eltDt)
                val opcode = if(signed) Opcode.MULSR else Opcode.MULR
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, eltDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.binary(opcode, eltDt, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, eltDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                }
            }
            return result
        }
        if(constAddress==null && memory!=null) {
            val resultVar = mutableListOf<IRCodeChunkBase>()
            val addrTr = exprGen.translateExpression(memory.address)
            addToResult(resultVar, addrTr, addrTr.resultReg, -1)
            val addressReg = addrTr.resultReg
            if(vmDt==IRDataType.FLOAT) {
                val tr = exprGen.translateExpression(operand)
                addToResult(resultVar, tr, -1, tr.resultFpReg)
                val loadReg = codeGen.registers.next(IRDataType.FLOAT)
                resultVar += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.binary(Opcode.MULR, vmDt, loadReg, tr.resultFpReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
                }
            } else {
                val loadReg = codeGen.registers.next(vmDt)
                resultVar += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
                }
                val constVal = (operand as? PtNumber)?.number?.toInt()
                if(constVal!=null) {
                    val constReg = codeGen.registers.next(vmDt)
                    resultVar += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(vmDt, constReg, constVal)
                        val opc = if(signed) Opcode.MULSR else Opcode.MULR
                        it += IRInstructions.binary(opc, vmDt, loadReg, constReg)
                        it += IRInstructions.storeMemory(Opcode.STOREI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
                    }
                } else {
                    val opTr = exprGen.translateExpression(operand)
                    addToResult(resultVar, opTr, opTr.resultReg, -1)
                    resultVar += IRCodeChunk(null, null).also {
                        val opc = if(signed) Opcode.MULSR else Opcode.MULR
                        it += IRInstructions.binary(opc, vmDt, loadReg, opTr.resultReg)
                        it += IRInstructions.storeMemory(Opcode.STOREI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
                    }
                }
            }
            return resultVar
        }

        val result = mutableListOf<IRCodeChunkBase>()
        val constFactorRight = operand as? PtNumber
        if(vmDt==IRDataType.FLOAT) {
            if(constFactorRight!=null) {
                val factor = constFactorRight.number
                result += codeGen.multiplyByConstFloatInplace(constAddress, symbol, factor)
            } else {
                val tr = exprGen.translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, if(constAddress!=null)
                    IRInstructions.memoryOp(Opcode.MULSM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultFpReg)
                else
                    IRInstructions.memoryOp(Opcode.MULSM, vmDt, IRMemory.direct(symbol!!), tr.resultFpReg)
                    , null)
            }
        } else {
            if(constFactorRight!=null && !constFactorRight.type.isFloat) {
                val factor = constFactorRight.number.toInt()
                result += codeGen.multiplyByConstInplace(vmDt, signed, constAddress, symbol, factor)
            } else {
                val tr = exprGen.translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                val opcode = if(signed) Opcode.MULSM else Opcode.MULM
                addInstr(result, if(constAddress!=null)
                    IRInstructions.memoryOp(opcode, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultReg)
                else
                    IRInstructions.memoryOp(opcode, vmDt, IRMemory.direct(symbol!!), tr.resultReg)
                    , null)
            }
        }
        return result
    }

    private fun operatorMinusInplace(symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            val result = mutableListOf<IRCodeChunkBase>()
            if(array.splitWords)
                return operatorMinusInplaceSplitArray(array, operand)
            val eltDt = codeGen.irType(array.type)
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            if(constIndex!=null && constValue!=null) {
                val arrayVariableName = array.variable!!.name

                if(constValue==1) {
                    addInstr(result, IRInstructions.memoryOp(Opcode.DECM, eltDt, IRMemory.direct(arrayVariableName, constIndex*eltSize)), null)
                } else {
                    addInstr(result, IRInstructions.memoryOpImmediate(Opcode.SUBIM, eltDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), constValue), null)
                }
                return result
            }
            // Optimized path for non-const cases
            if(constIndex!=null) {
                // Constant index, non-constant value
                val arrayVariableName = array.variable!!.name
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                addInstr(result, IRInstructions.memoryOp(Opcode.SUBM, eltDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueTr.resultReg), null)
                return result
            }
            // Non-constant index - use LOADX/SUBR/STOREX
            val indexTr = exprGen.translateExpression(array.index)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            val (indexReg, indexDt) = normalizeArrayIndex(result, indexTr)
            val arrayVariableName = array.variable!!.name
            // Multiply index by element size for LOADX/STOREX
            if(eltSize > 1) {
                result += codeGen.multiplyByConst(indexDt, indexReg, eltSize)
            }
            if(constValue!=null) {
                // Non-constant index, constant value
                val loadReg = codeGen.registers.next(eltDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, eltDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                    val constReg = codeGen.registers.next(eltDt)
                    it += IRInstructions.load(eltDt, constReg, constValue)
                    it += IRInstructions.binary(Opcode.SUBR, eltDt, loadReg, constReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, eltDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                }
            } else {
                // Non-constant index, non-constant value
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(eltDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, eltDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.binary(Opcode.SUBR, eltDt, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, eltDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                }
            }
            return result
        }
        if(constAddress==null && memory!=null) {
            // Memory target: translate address and subtract
            val memResult = mutableListOf<IRCodeChunkBase>()
            
            // Check if address is a constant number
            val constMemAddress = memory.address as? PtNumber
            if(constMemAddress!=null) {
                val addr = constMemAddress.number.toUInt()
                val operandConstValue = (operand as? PtNumber)?.number
                if(operandConstValue==null) {
                    val valueTr = exprGen.translateExpression(operand)
                    addToResult(memResult, valueTr, valueTr.resultReg, -1)
                    val loadReg = codeGen.registers.next(IRDataType.BYTE)
                    memResult += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, loadReg, IRMemory.direct(addr.toAddress()))
                        it += IRInstructions.binary(Opcode.SUBR, IRDataType.BYTE, loadReg, valueTr.resultReg)
                        it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, loadReg, IRMemory.direct(addr.toAddress()))
                    }
                } else if(operandConstValue==1.0) {
                    addInstr(memResult, IRInstructions.memoryOp(Opcode.DECM, IRDataType.BYTE, IRMemory.direct(addr.toAddress())), null)
                } else {
                    val loadReg = codeGen.registers.next(IRDataType.BYTE)
                    val valueReg = codeGen.registers.next(IRDataType.BYTE)
                    memResult += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, loadReg, IRMemory.direct(addr.toAddress()))
                        it += IRInstructions.load(IRDataType.BYTE, valueReg, operandConstValue.toInt())
                        it += IRInstructions.binary(Opcode.SUBR, IRDataType.BYTE, loadReg, valueReg)
                        it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, loadReg, IRMemory.direct(addr.toAddress()))
                    }
                }
                return memResult
            }

            val addrTr = exprGen.translateExpression(memory.address)
            addToResult(memResult, addrTr, addrTr.resultReg, -1)
            val addressReg = addrTr.resultReg
            val operandConstValue = (operand as? PtNumber)?.number

            if(operandConstValue==null) {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(memResult, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(IRDataType.BYTE)
                memResult += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.binary(Opcode.SUBR, IRDataType.BYTE, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                }
            } else {
                // Constant value, non-const address
                if(operandConstValue==1.0) {
                    val loadReg = codeGen.registers.next(IRDataType.BYTE)
                    memResult += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                        it += IRInstructions.unary(Opcode.DEC, IRDataType.BYTE, loadReg)
                        it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    }
                } else {
                    val loadReg = codeGen.registers.next(IRDataType.BYTE)
                    val valueReg = codeGen.registers.next(IRDataType.BYTE)
                    memResult += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                        it += IRInstructions.load(IRDataType.BYTE, valueReg, operandConstValue.toInt())
                        it += IRInstructions.binary(Opcode.SUBR, IRDataType.BYTE, loadReg, valueReg)
                        it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    }
                }
            }
            return memResult
        }

        val constValue = (operand as? PtNumber)?.number
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            if(constValue==1.0) {
                addInstr(result, if(constAddress!=null)
                    IRInstructions.memoryOp(Opcode.DECM, vmDt, IRMemory.direct(constAddress.toAddress()))
                else
                    IRInstructions.memoryOp(Opcode.DECM, vmDt, IRMemory.direct(symbol!!)), null)
            } else if(constValue!=null) {
                addInstr(result, if(constAddress!=null)
                    IRInstructions.memoryOpImmediateFloat(Opcode.SUBIM, IRMemory.direct(constAddress.toAddress()), constValue)
                else
                    IRInstructions.memoryOpImmediateFloat(Opcode.SUBIM, IRMemory.direct(symbol!!), constValue), null)
            } else {
                val tr = exprGen.translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, if(constAddress!=null)
                    IRInstructions.memoryOp(Opcode.SUBM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultFpReg)
                else
                    IRInstructions.memoryOp(Opcode.SUBM, vmDt, IRMemory.direct(symbol!!), tr.resultFpReg), null)
            }
        } else {
            if(constValue==1.0) {
                addInstr(result, if(constAddress!=null)
                    IRInstructions.memoryOp(Opcode.DECM, vmDt, IRMemory.direct(constAddress.toAddress()))
                else
                    IRInstructions.memoryOp(Opcode.DECM, vmDt, IRMemory.direct(symbol!!)), null)
            } else if(constValue!=null) {
                addInstr(result, if(constAddress!=null)
                    IRInstructions.memoryOpImmediate(Opcode.SUBIM, vmDt, IRMemory.direct(constAddress.toAddress()), constValue.toInt())
                else
                    IRInstructions.memoryOpImmediate(Opcode.SUBIM, vmDt, IRMemory.direct(symbol!!), constValue.toInt()), null)
            } else {
                val tr = exprGen.translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                addInstr(result, if(constAddress!=null)
                    IRInstructions.memoryOp(Opcode.SUBM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultReg)
                else
                    IRInstructions.memoryOp(Opcode.SUBM, vmDt, IRMemory.direct(symbol!!), tr.resultReg), null)
            }
        }
        return result
    }

    private fun operatorMultiplyInplaceSplitArray(array: PtArrayIndexer, operand: PtExpression): IRCodeChunks? {
        val result = mutableListOf<IRCodeChunkBase>()
        val constIndex = array.index.asConstInteger()
        val constValue = operand.asConstInteger()
        if(constIndex!=null) {
            val arrayVariableName = array.variable!!.name
            
            if(constValue==1) {
                // Multiplying by 1 is a no-op
                return emptyList()
            } else if(constValue!=null) {
                // Handle constant value using CONCAT/MUL/LSIGB/MSIGB
                val wordReg = codeGen.registers.next(IRDataType.WORD)
                val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                val msbReg = codeGen.registers.next(IRDataType.BYTE)
                
                result += IRCodeChunk(null, null).also {
                    // Load current LSB and MSB
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, lsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, msbReg, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                    // Concatenate into word register
                    it += IRInstructions.concat(IRDataType.BYTE, wordReg, msbReg, lsbReg)
                    // Multiply by constant value
                    it += IRInstructions.binaryImmediate(Opcode.MUL, IRDataType.WORD, wordReg, constValue)
                    // Extract bytes back into NEW registers
                    val newLsbReg = codeGen.registers.next(IRDataType.BYTE)
                    val newMsbReg = codeGen.registers.next(IRDataType.BYTE)
                    it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, newLsbReg, wordReg)
                    it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, newMsbReg, wordReg)
                    // Store back
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newLsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newMsbReg, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                }
                return result
            } else {
                // Non-constant operand: translate expression, then multiply using CONCAT/MUL
                val tr = exprGen.translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                if(tr.resultReg<0) {
                    return null  // fallback to slow method
                }
                val wordReg = codeGen.registers.next(IRDataType.WORD)
                val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                val msbReg = codeGen.registers.next(IRDataType.BYTE)

                result += IRCodeChunk(null, null).also {
                    // Load current LSB and MSB from array
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, lsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, msbReg, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                    // Concatenate into word register
                    it += IRInstructions.concat(IRDataType.BYTE, wordReg, msbReg, lsbReg)
                    // Multiply by operand value (use MULR for register-to-register)
                    it += IRInstructions.binary(Opcode.MULR, IRDataType.WORD, wordReg, tr.resultReg)
                    // Extract bytes back into NEW registers
                    val newLsbReg = codeGen.registers.next(IRDataType.BYTE)
                    val newMsbReg = codeGen.registers.next(IRDataType.BYTE)
                    it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, newLsbReg, wordReg)
                    it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, newMsbReg, wordReg)
                    // Store back
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newLsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newMsbReg, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                }
                return result
            }
        }
        return null  // fallback to slow method for non-constant index
    }

    private fun operatorMinusInplaceSplitArray(array: PtArrayIndexer, operand: PtExpression): IRCodeChunks? {
        val result = mutableListOf<IRCodeChunkBase>()
        val constIndex = array.index.asConstInteger()
        val constValue = operand.asConstInteger()
        if(constIndex!=null) {
            val arrayVariableName = array.variable!!.name
            
            if(constValue==1) {
                val skip = codeGen.createLabelName()
                val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, lsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    if(!codeGen.options.compTarget.cpu.statusBitsOnMultiByteOps)
                        it += IRInstructions.compareImmediate(IRDataType.BYTE, lsbReg, 0)
                    it += IRInstructions.branch(Opcode.BSTNE, codeLabel(skip))
                    it += IRInstructions.memoryOp(Opcode.DECM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                }
                result += IRCodeChunk(skip, null).also {
                    it += IRInstructions.memoryOp(Opcode.DECM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                }
                return result
            } else if(constValue!=null) {
                // Handle constant value != 1 using CONCAT/SUB/LSIGB/MSIGB
                val wordReg = codeGen.registers.next(IRDataType.WORD)
                val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                val msbReg = codeGen.registers.next(IRDataType.BYTE)
                
                result += IRCodeChunk(null, null).also {
                    // Load current LSB and MSB
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, lsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, msbReg, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                    // Concatenate into word register
                    it += IRInstructions.concat(IRDataType.BYTE, wordReg, msbReg, lsbReg)
                    // Subtract the constant value
                    it += IRInstructions.binaryImmediate(Opcode.SUB, IRDataType.WORD, wordReg, constValue)
                    // Extract bytes back into NEW registers
                    val newLsbReg = codeGen.registers.next(IRDataType.BYTE)
                    val newMsbReg = codeGen.registers.next(IRDataType.BYTE)
                    it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, newLsbReg, wordReg)
                    it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, newMsbReg, wordReg)
                    // Store back
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newLsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newMsbReg, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                }
                return result
            } else {
                // Non-constant operand: translate expression, then subtract using CONCAT/SUB
                val tr = exprGen.translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                if(tr.resultReg<0) {
                    return null  // fallback to slow method
                }
                val wordReg = codeGen.registers.next(IRDataType.WORD)
                val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                val msbReg = codeGen.registers.next(IRDataType.BYTE)

                result += IRCodeChunk(null, null).also {
                    // Load current LSB and MSB from array
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, lsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, msbReg, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                    // Concatenate into word register
                    it += IRInstructions.concat(IRDataType.BYTE, wordReg, msbReg, lsbReg)
                    // Subtract the operand value (use SUBR for register-to-register)
                    it += IRInstructions.binary(Opcode.SUBR, IRDataType.WORD, wordReg, tr.resultReg)
                    // Extract bytes back into NEW registers
                    val newLsbReg = codeGen.registers.next(IRDataType.BYTE)
                    val newMsbReg = codeGen.registers.next(IRDataType.BYTE)
                    it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, newLsbReg, wordReg)
                    it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, newMsbReg, wordReg)
                    // Store back
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newLsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newMsbReg, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                }
                return result
            }
        }
        return null  // fallback to slow method for non-constant index
    }

    private fun operatorPlusInplace(symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?,
                                    vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val result = mutableListOf<IRCodeChunkBase>()
            if(array.splitWords)
                return operatorPlusInplaceSplitArray(array, operand)
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            val elementDt = codeGen.irType(array.type)
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            if(constIndex!=null && constValue!=null) {
                val arrayVariableName = array.variable!!.name

                if(constValue==1) {
                    addInstr(result, IRInstructions.memoryOp(Opcode.INCM, elementDt, IRMemory.direct(arrayVariableName, constIndex*eltSize)), null)
                } else {
                    addInstr(result, IRInstructions.memoryOpImmediate(Opcode.ADDIM, elementDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), constValue), null)
                }
                return result
            }
            // Optimized path for non-const cases
            if(constIndex!=null) {
                // Constant index, non-constant value
                val arrayVariableName = array.variable!!.name
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                addInstr(result, IRInstructions.memoryOp(Opcode.ADDM, elementDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueTr.resultReg), null)
                return result
            }
            // Non-constant index - use LOADX/ADDR/STOREX
            val indexTr = exprGen.translateExpression(array.index)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            val (indexReg, indexDt) = normalizeArrayIndex(result, indexTr)
            val arrayVariableName = array.variable!!.name
            // Multiply index by element size for LOADX/STOREX
            if(eltSize > 1) {
                result += codeGen.multiplyByConst(indexDt, indexReg, eltSize)
            }
            if(constValue!=null) {
                // Non-constant index, constant value
                val loadReg = codeGen.registers.next(elementDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, elementDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                    val constReg = codeGen.registers.next(elementDt)
                    it += IRInstructions.load(elementDt, constReg, constValue)
                    it += IRInstructions.binary(Opcode.ADDR, elementDt, loadReg, constReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, elementDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                }
            } else {
                // Non-constant index, non-constant value
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(elementDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, elementDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.binary(Opcode.ADDR, elementDt, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, elementDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
                }
            }
            return result
        }
        if(constAddress==null && memory!=null) {
            // Memory target: translate address and add
            val memResult = mutableListOf<IRCodeChunkBase>()
            
            // Check if address is a constant number
            val constMemAddress = memory.address as? PtNumber
            if(constMemAddress!=null) {
                // Direct constant address - shouldn't normally happen but handle it
                val addr = constMemAddress.number.toUInt()
                val operandConstValue = (operand as? PtNumber)?.number
                if(operandConstValue==null) {
                    val valueTr = exprGen.translateExpression(operand)
                    addToResult(memResult, valueTr, valueTr.resultReg, -1)
                    val loadReg = codeGen.registers.next(IRDataType.BYTE)
                    memResult += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, loadReg, IRMemory.direct(addr.toAddress()))
                        it += IRInstructions.binary(Opcode.ADDR, IRDataType.BYTE, loadReg, valueTr.resultReg)
                        it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, loadReg, IRMemory.direct(addr.toAddress()))
                    }
                } else if(operandConstValue==1.0) {
                    addInstr(memResult, IRInstructions.memoryOp(Opcode.INCM, IRDataType.BYTE, IRMemory.direct(addr.toAddress())), null)
                } else {
                    val loadReg = codeGen.registers.next(IRDataType.BYTE)
                    val valueReg = codeGen.registers.next(IRDataType.BYTE)
                    memResult += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, loadReg, IRMemory.direct(addr.toAddress()))
                        it += IRInstructions.load(IRDataType.BYTE, valueReg, operandConstValue.toInt())
                        it += IRInstructions.binary(Opcode.ADDR, IRDataType.BYTE, loadReg, valueReg)
                        it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, loadReg, IRMemory.direct(addr.toAddress()))
                    }
                }
                return memResult
            }

            val addrTr = exprGen.translateExpression(memory.address)
            addToResult(memResult, addrTr, addrTr.resultReg, -1)
            val addressReg = addrTr.resultReg
            val operandConstValue = (operand as? PtNumber)?.number

            if(operandConstValue==null) {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(memResult, valueTr, valueTr.resultReg, -1)
                // LOADI current value, ADD, STOREI back
                val loadReg = codeGen.registers.next(IRDataType.BYTE)
                memResult += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.binary(Opcode.ADDR, IRDataType.BYTE, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                }
            } else {
                // Constant value, non-const address
                if(operandConstValue==1.0) {
                    val loadReg = codeGen.registers.next(IRDataType.BYTE)
                    memResult += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                        it += IRInstructions.unary(Opcode.INC, IRDataType.BYTE, loadReg)
                        it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    }
                } else {
                    val loadReg = codeGen.registers.next(IRDataType.BYTE)
                    val valueReg = codeGen.registers.next(IRDataType.BYTE)
                    memResult += IRCodeChunk(null, null).also {
                        it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                        it += IRInstructions.load(IRDataType.BYTE, valueReg, operandConstValue.toInt())
                        it += IRInstructions.binary(Opcode.ADDR, IRDataType.BYTE, loadReg, valueReg)
                        it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    }
                }
            }
            return memResult
        }

        val result = mutableListOf<IRCodeChunkBase>()
        val constValue = (operand as? PtNumber)?.number

        if(vmDt==IRDataType.FLOAT) {
            if(constValue==1.0) {
                addInstr(result, if (constAddress != null)
                    IRInstructions.memoryOp(Opcode.INCM, vmDt, IRMemory.direct(constAddress.toAddress()))
                else
                    IRInstructions.memoryOp(Opcode.INCM, vmDt, IRMemory.direct(symbol!!)) , null)
            } else if(constValue!=null) {
                addInstr(result, if (constAddress != null)
                    IRInstructions.memoryOpImmediateFloat(Opcode.ADDIM, IRMemory.direct(constAddress.toAddress()), constValue)
                else
                    IRInstructions.memoryOpImmediateFloat(Opcode.ADDIM, IRMemory.direct(symbol!!), constValue) , null)
            } else {
                val tr = exprGen.translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, if (constAddress != null)
                    IRInstructions.memoryOp(Opcode.ADDM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultFpReg)
                else
                    IRInstructions.memoryOp(Opcode.ADDM, vmDt, IRMemory.direct(symbol!!), tr.resultFpReg) , null)
            }
        } else {
            if(constValue==1.0) {
                addInstr(result, if (constAddress != null)
                    IRInstructions.memoryOp(Opcode.INCM, vmDt, IRMemory.direct(constAddress.toAddress()))
                else
                    IRInstructions.memoryOp(Opcode.INCM, vmDt, IRMemory.direct(symbol!!)) , null)
            } else if(constValue==2.0 && codeGen.options.compTarget.cpu.is6502) {
                result += IRCodeChunk(null, null).also {
                    if (constAddress != null) {
                        it += IRInstructions.memoryOp(Opcode.INCM, vmDt, IRMemory.direct(constAddress.toAddress()))
                        it += IRInstructions.memoryOp(Opcode.INCM, vmDt, IRMemory.direct(constAddress.toAddress()))
                    } else {
                        val symbolName = symbol!!
                        it += IRInstructions.memoryOp(Opcode.INCM, vmDt, IRMemory.direct(symbolName))
                        it += IRInstructions.memoryOp(Opcode.INCM, vmDt, IRMemory.direct(symbolName))
                    }
                }
            } else if(constValue!=null) {
                addInstr(result, if (constAddress != null)
                    IRInstructions.memoryOpImmediate(Opcode.ADDIM, vmDt, IRMemory.direct(constAddress.toAddress()), constValue.toInt())
                else
                    IRInstructions.memoryOpImmediate(Opcode.ADDIM, vmDt, IRMemory.direct(symbol!!), constValue.toInt()), null)
            } else {
                val tr = exprGen.translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                if (constAddress != null)
                    addInstr(result, IRInstructions.memoryOp(Opcode.ADDM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultReg), null)
                else
                    addInstr(result, IRInstructions.memoryOp(Opcode.ADDM, vmDt, IRMemory.direct(symbol!!), tr.resultReg) , null)
            }
        }
        return result
    }

    private fun operatorPlusInplaceSplitArray(array: PtArrayIndexer, operand: PtExpression): IRCodeChunks? {
        val result = mutableListOf<IRCodeChunkBase>()
        val constIndex = array.index.asConstInteger()
        val constValue = operand.asConstInteger()
        if(constIndex!=null) {
            val arrayVariableName = array.variable!!.name
            
            if(constValue==1) {
                val skip = codeGen.createLabelName()
                val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.memoryOp(Opcode.INCM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, lsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    if(!codeGen.options.compTarget.cpu.statusBitsOnMultiByteOps)
                        it += IRInstructions.compareImmediate(IRDataType.BYTE, lsbReg, 0)
                    it += IRInstructions.branch(Opcode.BSTNE, codeLabel(skip))
                    it += IRInstructions.memoryOp(Opcode.INCM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                }
                result += IRCodeChunk(skip, null)
                return result
            } else if(constValue!=null) {
                // Handle constant value != 1 using CONCAT/ADD/LSIGB/MSIGB
                val wordReg = codeGen.registers.next(IRDataType.WORD)
                val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                val msbReg = codeGen.registers.next(IRDataType.BYTE)
                
                result += IRCodeChunk(null, null).also {
                    // Load current LSB and MSB
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, lsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, msbReg, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                    // Concatenate into word register
                    it += IRInstructions.concat(IRDataType.BYTE, wordReg, msbReg, lsbReg)
                    // Add the constant value
                    it += IRInstructions.binaryImmediate(Opcode.ADD, IRDataType.WORD, wordReg, constValue)
                    // Extract bytes back into NEW registers
                    val newLsbReg = codeGen.registers.next(IRDataType.BYTE)
                    val newMsbReg = codeGen.registers.next(IRDataType.BYTE)
                    it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, newLsbReg, wordReg)
                    it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, newMsbReg, wordReg)
                    // Store back
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newLsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newMsbReg, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                }
                return result
            } else {
                // Non-constant operand: translate expression, then add using CONCAT/ADD
                val tr = exprGen.translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                if(tr.resultReg<0) {
                    return null  // fallback to slow method
                }
                val wordReg = codeGen.registers.next(IRDataType.WORD)
                val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                val msbReg = codeGen.registers.next(IRDataType.BYTE)

                result += IRCodeChunk(null, null).also {
                    // Load current LSB and MSB from array
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, lsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, msbReg, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                    // Concatenate into word register
                    it += IRInstructions.concat(IRDataType.BYTE, wordReg, msbReg, lsbReg)
                    // Add the operand value (use ADDR for register-to-register)
                    it += IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, wordReg, tr.resultReg)
                    // Extract bytes back into NEW registers
                    val newLsbReg = codeGen.registers.next(IRDataType.BYTE)
                    val newMsbReg = codeGen.registers.next(IRDataType.BYTE)
                    it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, newLsbReg, wordReg)
                    it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, newMsbReg, wordReg)
                    // Store back
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newLsbReg, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newMsbReg, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                }
                return result
            }
        }
        return null  // fallback to slow method for non-constant index
    }

    private fun operatorShiftRightInplace(symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression, signed: Boolean): IRCodeChunks? {
        if(array!=null) {
            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()

            if(constIndex!=null && constValue!=null) {
                val arrayVariableName = array.variable!!.name

                if(array.splitWords) {
                    repeat(constValue) {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstructions.memoryOp(Opcode.LSRM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                            it += IRInstructions.memoryOp(Opcode.ROXRM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                        }
                    }
                } else {
                    val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
                    if(constValue==1) {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstructions.memoryOp(Opcode.LSRM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize))
                        }
                    } else {
                        val valueReg = codeGen.registers.next(IRDataType.BYTE)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstructions.load(IRDataType.BYTE, valueReg, constValue and 255)
                            it += IRInstructions.memoryOp(Opcode.LSRNM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueReg)
                        }
                    }
                }
                return result
            }
            // optimized handling for variable index or variable shift count (non-split)
            if(!array.splitWords) {
                val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
                val resultVar = mutableListOf<IRCodeChunkBase>()
                val indexTr = exprGen.translateExpression(array.index)
                addToResult(resultVar, indexTr, indexTr.resultReg, -1)
                val (indexReg, indexDt) = normalizeArrayIndex(resultVar, indexTr)
                if(eltSize > 1)
                    resultVar += codeGen.multiplyByConst(indexDt, indexReg, eltSize)
                val loadReg = codeGen.registers.next(vmDt)
                resultVar += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, loadReg, IRMemory.indexed(array.variable!!.name, indexReg, codeGen.options.compTarget.indexRegType))
                }
                val constShift = operand.asConstInteger()
                if(constShift!=null) {
                    if(constShift==1) {
                        val opc = if(signed) Opcode.ASR else Opcode.LSR
                        resultVar += IRCodeChunk(null, null).also {
                            it += IRInstructions.unary(opc, vmDt, loadReg)
                            it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(array.variable!!.name, indexReg, codeGen.options.compTarget.indexRegType))
                        }
                    } else {
                        val shiftReg = codeGen.registers.next(IRDataType.BYTE)
                        resultVar += IRCodeChunk(null, null).also {
                            it += IRInstructions.load(IRDataType.BYTE, shiftReg, constShift and 255)
                            val opc = if(signed) Opcode.ASRN else Opcode.LSRN
                            it += IRInstructions.binary(opc, vmDt, loadReg, shiftReg)
                            it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(array.variable!!.name, indexReg, codeGen.options.compTarget.indexRegType))
                        }
                    }
                } else {
                    val shiftTr = exprGen.translateExpression(operand)
                    addToResult(resultVar, shiftTr, shiftTr.resultReg, -1)
                    resultVar += IRCodeChunk(null, null).also {
                        val opc = if(signed) Opcode.ASRN else Opcode.LSRN
                        it += IRInstructions.binary(opc, vmDt, loadReg, shiftTr.resultReg)
                        it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(array.variable!!.name, indexReg, codeGen.options.compTarget.indexRegType))
                    }
                }
                return resultVar
            }
            return null
        }
        if(constAddress==null && memory!=null) {
            // optimized memory in-place >> with variable address
            val resultVar = mutableListOf<IRCodeChunkBase>()
            val addrTr = exprGen.translateExpression(memory.address)
            addToResult(resultVar, addrTr, addrTr.resultReg, -1)
            val addressReg = addrTr.resultReg
            val loadReg = codeGen.registers.next(vmDt)
            val constShift = operand.asConstInteger()
            resultVar += IRCodeChunk(null, null).also {
                it += IRInstructions.loadMemory(Opcode.LOADI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
            }
            if(constShift!=null) {
                if(constShift==1) {
                    val opc = if(signed) Opcode.ASR else Opcode.LSR
                    resultVar += IRCodeChunk(null, null).also {
                        it += IRInstructions.unary(opc, vmDt, loadReg)
                    }
                } else {
                    val shiftReg = codeGen.registers.next(IRDataType.BYTE)
                    resultVar += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(IRDataType.BYTE, shiftReg, constShift and 255)
                        val opc = if(signed) Opcode.ASRN else Opcode.LSRN
                        it += IRInstructions.binary(opc, vmDt, loadReg, shiftReg)
                    }
                }
            } else {
                val shiftTr = exprGen.translateExpression(operand)
                addToResult(resultVar, shiftTr, shiftTr.resultReg, -1)
                resultVar += IRCodeChunk(null, null).also {
                    val opc = if(signed) Opcode.ASRN else Opcode.LSRN
                    it += IRInstructions.binary(opc, vmDt, loadReg, shiftTr.resultReg)
                }
            }
            resultVar += IRCodeChunk(null, null).also {
                it += IRInstructions.storeMemory(Opcode.STOREI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
            }
            return resultVar
        }

        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isOne(operand)) {
            val opc = if (signed) Opcode.ASRM else Opcode.LSRM
            val ins = if(constAddress!=null)
                IRInstructions.memoryOp(opc, vmDt, IRMemory.direct(constAddress.toAddress()))
            else
                IRInstructions.memoryOp(opc, vmDt, IRMemory.direct(symbol!!))
            addInstr(result, ins, null)
        } else {
            val shiftTr = exprGen.translateExpression(operand)
            addToResult(result, shiftTr, shiftTr.resultReg, -1)
            val shiftReg = shiftTr.resultReg
            val opc = if (signed) Opcode.ASRNM else Opcode.LSRNM
            val ins = if(constAddress!=null)
                IRInstructions.memoryOp(opc, vmDt, IRMemory.direct(constAddress.toAddress()), shiftReg)
            else
                IRInstructions.memoryOp(opc, vmDt, IRMemory.direct(symbol!!), shiftReg)
            addInstr(result, ins, null)
        }
        return result
    }

    private fun operatorShiftLeftInplace(symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()

            if(constIndex!=null && constValue!=null) {
                val arrayVariableName = array.variable!!.name

                if(array.splitWords) {
                    repeat(constValue) {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstructions.memoryOp(Opcode.LSLM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_lsb", constIndex))
                            it += IRInstructions.memoryOp(Opcode.ROXLM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex))
                        }
                    }
                } else {
                    val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
                    if(constValue==1) {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstructions.memoryOp(Opcode.LSLM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize))
                        }
                    } else {
                        val valueReg = codeGen.registers.next(IRDataType.BYTE)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstructions.load(IRDataType.BYTE, valueReg, constValue and 255)
                            it += IRInstructions.memoryOp(Opcode.LSLNM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueReg)
                        }
                    }
                }
                return result
            }
            if(!array.splitWords) {
                val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
                val resultVar = mutableListOf<IRCodeChunkBase>()
                val indexTr = exprGen.translateExpression(array.index)
                addToResult(resultVar, indexTr, indexTr.resultReg, -1)
                val (indexReg, indexDt) = normalizeArrayIndex(resultVar, indexTr)
                if(eltSize > 1)
                    resultVar += codeGen.multiplyByConst(indexDt, indexReg, eltSize)
                val loadReg = codeGen.registers.next(vmDt)
                resultVar += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, loadReg, IRMemory.indexed(array.variable!!.name, indexReg, codeGen.options.compTarget.indexRegType))
                }
                val constShift = operand.asConstInteger()
                if(constShift!=null) {
                    if(constShift==1) {
                        resultVar += IRCodeChunk(null, null).also {
                            it += IRInstructions.unary(Opcode.LSL, vmDt, loadReg)
                            it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(array.variable!!.name, indexReg, codeGen.options.compTarget.indexRegType))
                        }
                    } else {
                        val shiftReg = codeGen.registers.next(IRDataType.BYTE)
                        resultVar += IRCodeChunk(null, null).also {
                            it += IRInstructions.load(IRDataType.BYTE, shiftReg, constShift and 255)
                            it += IRInstructions.binary(Opcode.LSLN, vmDt, loadReg, shiftReg)
                            it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(array.variable!!.name, indexReg, codeGen.options.compTarget.indexRegType))
                        }
                    }
                } else {
                    val shiftTr = exprGen.translateExpression(operand)
                    addToResult(resultVar, shiftTr, shiftTr.resultReg, -1)
                    resultVar += IRCodeChunk(null, null).also {
                        it += IRInstructions.binary(Opcode.LSLN, vmDt, loadReg, shiftTr.resultReg)
                        it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(array.variable!!.name, indexReg, codeGen.options.compTarget.indexRegType))
                    }
                }
                return resultVar
            }
            return null
        }
        if(constAddress==null && memory!=null) {
            val resultVar = mutableListOf<IRCodeChunkBase>()
            val addrTr = exprGen.translateExpression(memory.address)
            addToResult(resultVar, addrTr, addrTr.resultReg, -1)
            val addressReg = addrTr.resultReg
            val loadReg = codeGen.registers.next(vmDt)
            val constShift = operand.asConstInteger()
            resultVar += IRCodeChunk(null, null).also {
                it += IRInstructions.loadMemory(Opcode.LOADI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
            }
            if(constShift!=null) {
                if(constShift==1) {
                    resultVar += IRCodeChunk(null, null).also {
                        it += IRInstructions.unary(Opcode.LSL, vmDt, loadReg)
                    }
                } else {
                    val shiftReg = codeGen.registers.next(IRDataType.BYTE)
                    resultVar += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(IRDataType.BYTE, shiftReg, constShift and 255)
                        it += IRInstructions.binary(Opcode.LSLN, vmDt, loadReg, shiftReg)
                    }
                }
            } else {
                val shiftTr = exprGen.translateExpression(operand)
                addToResult(resultVar, shiftTr, shiftTr.resultReg, -1)
                resultVar += IRCodeChunk(null, null).also {
                    it += IRInstructions.binary(Opcode.LSLN, vmDt, loadReg, shiftTr.resultReg)
                }
            }
            resultVar += IRCodeChunk(null, null).also {
                it += IRInstructions.storeMemory(Opcode.STOREI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
            }
            return resultVar
        }

        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isOne(operand)){
            addInstr(result, if(constAddress!=null)
                IRInstructions.memoryOp(Opcode.LSLM, vmDt, IRMemory.direct(constAddress.toAddress()))
            else
                IRInstructions.memoryOp(Opcode.LSLM, vmDt, IRMemory.direct(symbol!!))
                , null)
        } else {
            val shiftTr = exprGen.translateExpression(operand)
            addToResult(result, shiftTr, shiftTr.resultReg, -1)
            val shiftReg = shiftTr.resultReg
            addInstr(result, if(constAddress!=null)
                IRInstructions.memoryOp(Opcode.LSLNM, vmDt, IRMemory.direct(constAddress.toAddress()), shiftReg)
            else
                IRInstructions.memoryOp(Opcode.LSLNM, vmDt, IRMemory.direct(symbol!!), shiftReg)
                ,null)
        }
        return result
    }

    private fun operatorXorInplace(symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks? {
        if(array!=null) {
            val arrayVariableName = array.variable!!.name

            val result = mutableListOf<IRCodeChunkBase>()
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            if(constIndex!=null && constValue!=null) {
                if(array.splitWords) {
                    val valueRegLsb = codeGen.registers.next(IRDataType.BYTE)
                    val valueRegMsb = codeGen.registers.next(IRDataType.BYTE)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(IRDataType.BYTE, valueRegLsb, constValue and 255)
                        it += IRInstructions.load(IRDataType.BYTE, valueRegMsb, constValue shr 8)
                        it += IRInstructions.memoryOp(Opcode.XORM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_lsb", constIndex), valueRegLsb)
                        it += IRInstructions.memoryOp(Opcode.XORM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex), valueRegMsb)
                    }
                } else {
                    val valueReg = codeGen.registers.next(vmDt)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.load(vmDt, valueReg, constValue)
                        it += IRInstructions.memoryOp(Opcode.XORM, vmDt, IRMemory.direct(arrayVariableName, constIndex*eltSize), valueReg)
                    }
                }
                return result
            }
            // Non-const cases - handle split and non-split arrays differently
            if(array.splitWords) {
                if(constIndex!=null) {
                    val valueTr = exprGen.translateExpression(operand)
                    addToResult(result, valueTr, valueTr.resultReg, -1)
                    if(valueTr.resultReg < 0) return null
                    val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                    val msbReg = codeGen.registers.next(IRDataType.BYTE)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, lsbReg, valueTr.resultReg)
                        it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, msbReg, valueTr.resultReg)
                        it += IRInstructions.memoryOp(Opcode.XORM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_lsb", constIndex), lsbReg)
                        it += IRInstructions.memoryOp(Opcode.XORM, IRDataType.BYTE, IRMemory.direct(arrayVariableName+"_msb", constIndex), msbReg)
                    }
                    return result
                }
                val indexTr = exprGen.translateExpression(array.index)
                addToResult(result, indexTr, indexTr.resultReg, -1)
                val (indexReg, _) = normalizeArrayIndex(result, indexTr)
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                if(valueTr.resultReg < 0) return null
                val lsbReg = codeGen.registers.next(IRDataType.BYTE)
                val msbReg = codeGen.registers.next(IRDataType.BYTE)
                val valLsbReg = codeGen.registers.next(IRDataType.BYTE)
                val valMsbReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, valLsbReg, valueTr.resultReg)
                    it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, valMsbReg, valueTr.resultReg)
                    it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, lsbReg, IRMemory.indexed(arrayVariableName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, msbReg, IRMemory.indexed(arrayVariableName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.binary(Opcode.XORR, IRDataType.BYTE, lsbReg, valLsbReg)
                    it += IRInstructions.binary(Opcode.XORR, IRDataType.BYTE, msbReg, valMsbReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, lsbReg, IRMemory.indexed(arrayVariableName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, msbReg, IRMemory.indexed(arrayVariableName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
                }
                return result
            }
            // Non-split array cases
            if(constIndex!=null) {
                val arrayVarName = array.variable!!.name
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                addInstr(result, IRInstructions.memoryOp(Opcode.XORM, vmDt, IRMemory.direct(arrayVarName, constIndex*eltSize), valueTr.resultReg), null)
                return result
            }
            // Non-constant index - use LOADX/XORR/STOREX
            val indexTr = exprGen.translateExpression(array.index)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            val (indexReg, _) = normalizeArrayIndex(result, indexTr)
            val arrayVarName2 = array.variable!!.name
            if(constValue!=null) {
                val loadReg = codeGen.registers.next(vmDt)
                val constReg = codeGen.registers.next(vmDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, loadReg, IRMemory.indexed(arrayVarName2, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.load(vmDt, constReg, constValue)
                    it += IRInstructions.binary(Opcode.XORR, vmDt, loadReg, constReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(arrayVarName2, indexReg, codeGen.options.compTarget.indexRegType))
                }
            } else {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(vmDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADX, vmDt, loadReg, IRMemory.indexed(arrayVarName2, indexReg, codeGen.options.compTarget.indexRegType))
                    it += IRInstructions.binary(Opcode.XORR, vmDt, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREX, vmDt, loadReg, IRMemory.indexed(arrayVarName2, indexReg, codeGen.options.compTarget.indexRegType))
                }
            }
            return result
        }
        if(constAddress==null && memory!=null) {
            // Memory target with non-constant address
            val memResult = mutableListOf<IRCodeChunkBase>()
            val addrTr = exprGen.translateExpression(memory.address)
            addToResult(memResult, addrTr, addrTr.resultReg, -1)
            val addressReg = addrTr.resultReg
            val operandConstValue = (operand as? PtNumber)?.number
            
            if(operandConstValue==null) {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(memResult, valueTr, valueTr.resultReg, -1)
                val loadReg = codeGen.registers.next(IRDataType.BYTE)
                memResult += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.binary(Opcode.XORR, IRDataType.BYTE, loadReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                }
            } else {
                val loadReg = codeGen.registers.next(IRDataType.BYTE)
                val valueReg = codeGen.registers.next(IRDataType.BYTE)
                memResult += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                    it += IRInstructions.load(IRDataType.BYTE, valueReg, operandConstValue.toInt())
                    it += IRInstructions.binary(Opcode.XORR, IRDataType.BYTE, loadReg, valueReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, loadReg, IRMemory.indirect(addressReg, 0))
                }
            }
            return memResult
        }

        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(operand)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, if(constAddress!=null)
            IRInstructions.memoryOp(Opcode.XORM, vmDt, IRMemory.direct(constAddress.toAddress()), tr.resultReg)
        else
            IRInstructions.memoryOp(Opcode.XORM, vmDt, IRMemory.direct(symbol!!), tr.resultReg)
            ,null)
        return result
    }

    private fun operatorDivideInplaceSplitArray(array: PtArrayIndexer, operand: PtExpression, signed: Boolean): IRCodeChunks {
        val constIndex = array.index.asConstInteger()
        val constValue = operand.asConstInteger()
        val arrayName = array.variable!!.name
        val opcode = if(signed) Opcode.DIVS else Opcode.DIV
        val regOpcode = if(signed) Opcode.DIVSR else Opcode.DIVR
        val result = mutableListOf<IRCodeChunkBase>()
        val indexReg: Int

        if(constIndex!=null) {
            indexReg = -1
        } else {
            val indexTr = exprGen.translateExpression(array.index)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            indexReg = normalizeArrayIndex(result, indexTr).first
        }

        val wordReg = codeGen.registers.next(IRDataType.WORD)
        val lsbReg = codeGen.registers.next(IRDataType.BYTE)
        val msbReg = codeGen.registers.next(IRDataType.BYTE)
        result += IRCodeChunk(null, null).also {
            if(constIndex!=null) {
                it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, lsbReg, IRMemory.direct(arrayName+"_lsb", constIndex))
                it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, msbReg, IRMemory.direct(arrayName+"_msb", constIndex))
            } else {
                it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, lsbReg, IRMemory.indexed(arrayName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, msbReg, IRMemory.indexed(arrayName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
            }
            it += IRInstructions.concat(IRDataType.BYTE, wordReg, msbReg, lsbReg)
            if(constValue!=null) {
                if(constValue!=1)
                    it += IRInstructions.binaryImmediate(opcode, IRDataType.WORD, wordReg, constValue)
            }
        }

        if(constValue==null) {
            val valueTr = exprGen.translateExpression(operand)
            addToResult(result, valueTr, valueTr.resultReg, -1)
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.binary(regOpcode, IRDataType.WORD, wordReg, valueTr.resultReg)
            }
        }

        val newLsbReg = codeGen.registers.next(IRDataType.BYTE)
        val newMsbReg = codeGen.registers.next(IRDataType.BYTE)
        result += IRCodeChunk(null, null).also {
            it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, newLsbReg, wordReg)
            it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, newMsbReg, wordReg)
            if(constIndex!=null) {
                it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newLsbReg, IRMemory.direct(arrayName+"_lsb", constIndex))
                it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newMsbReg, IRMemory.direct(arrayName+"_msb", constIndex))
            } else {
                it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, newLsbReg, IRMemory.indexed(arrayName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, newMsbReg, IRMemory.indexed(arrayName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
            }
        }
        return result
    }

    private fun operatorModuloInplace(symbol: String?, array: PtArrayIndexer?, constAddress: UInt?, memory: PtMemoryByte?, vmDt: IRDataType, operand: PtExpression, signed: Boolean): IRCodeChunks {
        if(array!=null) {
            if(array.splitWords)
                return operatorModuloInplaceSplitArray(array, operand, signed)
            val eltSize = codeGen.program.memsizer.memorySize(array.type, null)
            val result = mutableListOf<IRCodeChunkBase>()
            val eltDt = codeGen.irType(array.type)
            val constIndex = array.index.asConstInteger()
            val constValue = operand.asConstInteger()
            val arrayVariableName = array.variable!!.name
            val modOpcode = if(signed) Opcode.MODS else Opcode.MOD
            val modROpcode = if(signed) Opcode.MODSR else Opcode.MODR
            if(constIndex!=null && constValue!=null) {
                val resultReg = codeGen.registers.next(eltDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADM, eltDt, resultReg, IRMemory.direct(arrayVariableName, constIndex*eltSize))
                    it += IRInstructions.binaryImmediate(modOpcode, eltDt, resultReg, constValue)
                    it += IRInstructions.storeMemory(Opcode.STOREM, eltDt, resultReg, IRMemory.direct(arrayVariableName, constIndex*eltSize))
                }
                return result
            }
            if(constIndex!=null) {
                // Constant index, non-constant value
                val resultReg = codeGen.registers.next(eltDt)
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADM, eltDt, resultReg, IRMemory.direct(arrayVariableName, constIndex*eltSize))
                    it += IRInstructions.binary(modROpcode, eltDt, resultReg, valueTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREM, eltDt, resultReg, IRMemory.direct(arrayVariableName, constIndex*eltSize))
                }
                return result
            }
            // Non-constant index - use LOADX/MODR/STOREX
            val indexTr = exprGen.translateExpression(array.index)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            val (indexReg, indexDt) = normalizeArrayIndex(result, indexTr)
            if(eltSize > 1) {
                result += codeGen.multiplyByConst(indexDt, indexReg, eltSize)
            }
            val loadReg = codeGen.registers.next(eltDt)
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.loadMemory(Opcode.LOADX, eltDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
            }
            if(constValue!=null) {
                val constReg = codeGen.registers.next(eltDt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.load(eltDt, constReg, constValue)
                    it += IRInstructions.binary(modROpcode, eltDt, loadReg, constReg)
                }
            } else {
                val valueTr = exprGen.translateExpression(operand)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.binary(modROpcode, eltDt, loadReg, valueTr.resultReg)
                }
            }
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.storeMemory(Opcode.STOREX, eltDt, loadReg, IRMemory.indexed(arrayVariableName, indexReg, codeGen.options.compTarget.indexRegType))
            }
            return result
        }
        if(constAddress==null && memory!=null) {
            val resultVar = mutableListOf<IRCodeChunkBase>()
            val addrTr = exprGen.translateExpression(memory.address)
            addToResult(resultVar, addrTr, addrTr.resultReg, -1)
            val addressReg = addrTr.resultReg
            val loadReg = codeGen.registers.next(vmDt)
            resultVar += IRCodeChunk(null, null).also {
                it += IRInstructions.loadMemory(Opcode.LOADI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
            }
            val modROpcode = if(signed) Opcode.MODSR else Opcode.MODR
            val constVal = (operand as? PtNumber)?.number?.toInt()
            if(constVal!=null) {
                val constReg = codeGen.registers.next(vmDt)
                resultVar += IRCodeChunk(null, null).also {
                    it += IRInstructions.load(vmDt, constReg, constVal)
                    it += IRInstructions.binary(modROpcode, vmDt, loadReg, constReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
                }
            } else {
                val opTr = exprGen.translateExpression(operand)
                addToResult(resultVar, opTr, opTr.resultReg, -1)
                resultVar += IRCodeChunk(null, null).also {
                    it += IRInstructions.binary(modROpcode, vmDt, loadReg, opTr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREI, vmDt, loadReg, IRMemory.indirect(addressReg, 0))
                }
            }
            return resultVar
        }

        val result = mutableListOf<IRCodeChunkBase>()
        val resultReg = codeGen.registers.next(vmDt)
        val modOpcode = if(signed) Opcode.MODS else Opcode.MOD
        val modROpcode = if(signed) Opcode.MODSR else Opcode.MODR
        if(operand is PtNumber) {
            val number = operand.number.toInt()
            if (constAddress != null) {
                // @(address) = @(address) %= operand
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADM, vmDt, resultReg, IRMemory.direct(constAddress.toAddress()))
                    it += IRInstructions.binaryImmediate(modOpcode, vmDt, resultReg, number)
                    it += IRInstructions.storeMemory(Opcode.STOREM, vmDt, resultReg, IRMemory.direct(constAddress.toAddress()))
                }
            } else {
                // symbol = symbol %= operand
                result += IRCodeChunk(null, null).also {
                    val symbolName = symbol!!
                    it += IRInstructions.loadMemory(Opcode.LOADM, vmDt, resultReg, IRMemory.direct(symbolName))
                    it += IRInstructions.binaryImmediate(modOpcode, vmDt, resultReg, number)
                    it += IRInstructions.storeMemory(Opcode.STOREM, vmDt, resultReg, IRMemory.direct(symbolName))
                }
            }
        } else {
            val tr = exprGen.translateExpression(operand)
            result += tr.chunks
            if (constAddress != null) {
                // @(address) = @(address) %= operand
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADM, vmDt, resultReg, IRMemory.direct(constAddress.toAddress()))
                    it += IRInstructions.binary(modROpcode, vmDt, resultReg, tr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREM, vmDt, resultReg, IRMemory.direct(constAddress.toAddress()))
                }
            } else {
                // symbol = symbol %= operand
                result += IRCodeChunk(null, null).also {
                    val symbolName = symbol!!
                    it += IRInstructions.loadMemory(Opcode.LOADM, vmDt, resultReg, IRMemory.direct(symbolName))
                    it += IRInstructions.binary(modROpcode, vmDt, resultReg, tr.resultReg)
                    it += IRInstructions.storeMemory(Opcode.STOREM, vmDt, resultReg, IRMemory.direct(symbolName))
                }
            }
        }
        return result
    }

    private fun operatorModuloInplaceSplitArray(array: PtArrayIndexer, operand: PtExpression, signed: Boolean): IRCodeChunks {
        val constIndex = array.index.asConstInteger()
        val constValue = operand.asConstInteger()
        val arrayName = array.variable!!.name
        val opcode = if(signed) Opcode.MODS else Opcode.MOD
        val regOpcode = if(signed) Opcode.MODSR else Opcode.MODR
        val result = mutableListOf<IRCodeChunkBase>()
        val indexReg: Int

        if(constIndex!=null) {
            indexReg = -1
        } else {
            val indexTr = exprGen.translateExpression(array.index)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            indexReg = normalizeArrayIndex(result, indexTr).first
        }

        val wordReg = codeGen.registers.next(IRDataType.WORD)
        val lsbReg = codeGen.registers.next(IRDataType.BYTE)
        val msbReg = codeGen.registers.next(IRDataType.BYTE)
        result += IRCodeChunk(null, null).also {
            if(constIndex!=null) {
                it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, lsbReg, IRMemory.direct(arrayName+"_lsb", constIndex))
                it += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, msbReg, IRMemory.direct(arrayName+"_msb", constIndex))
            } else {
                it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, lsbReg, IRMemory.indexed(arrayName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                it += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, msbReg, IRMemory.indexed(arrayName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
            }
            it += IRInstructions.concat(IRDataType.BYTE, wordReg, msbReg, lsbReg)
            if(constValue!=null) {
                it += IRInstructions.binaryImmediate(opcode, IRDataType.WORD, wordReg, constValue)
            }
        }

        if(constValue==null) {
            val valueTr = exprGen.translateExpression(operand)
            addToResult(result, valueTr, valueTr.resultReg, -1)
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.binary(regOpcode, IRDataType.WORD, wordReg, valueTr.resultReg)
            }
        }

        val newLsbReg = codeGen.registers.next(IRDataType.BYTE)
        val newMsbReg = codeGen.registers.next(IRDataType.BYTE)
        result += IRCodeChunk(null, null).also {
            it += IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, newLsbReg, wordReg)
            it += IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, newMsbReg, wordReg)
            if(constIndex!=null) {
                it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newLsbReg, IRMemory.direct(arrayName+"_lsb", constIndex))
                it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newMsbReg, IRMemory.direct(arrayName+"_msb", constIndex))
            } else {
                it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, newLsbReg, IRMemory.indexed(arrayName+"_lsb", indexReg, codeGen.options.compTarget.indexRegType))
                it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, newMsbReg, IRMemory.indexed(arrayName+"_msb", indexReg, codeGen.options.compTarget.indexRegType))
            }
        }
        return result
    }

    private fun tryFoldStructArrayDirectWrite(addressExpr: PtExpression, zero: Boolean, valueReg: Int, targetDt: IRDataType, result: MutableList<IRCodeChunkBase>): Boolean {
        val info = extractStructArrayIndexInfo(addressExpr, codeGen) ?: return false
        val (idxCode, indexReg) = codeGen.loadIndexReg(info.idxExpr, info.structSize, codeGen.wordArrayIndex, false)
        result += idxCode
        if(zero) {
            result += IRCodeChunk(null, null).also {
                if(info.fieldOffset==0) it += IRInstructions.storeZero(Opcode.STOREZX, targetDt, IRMemory.indexed(info.arrayName, indexReg, codeGen.options.compTarget.indexRegType, scale=info.structSize))
                else it += IRInstructions.storeZero(Opcode.STOREZX, targetDt, IRMemory.indexed(info.arrayName, indexReg, codeGen.options.compTarget.indexRegType, scale=info.structSize, displacement=info.fieldOffset))
            }
        } else {
            if(valueReg<0) return false
            result += IRCodeChunk(null, null).also {
                if(info.fieldOffset==0) it += IRInstructions.storeMemory(Opcode.STOREX, targetDt, valueReg, IRMemory.indexed(info.arrayName, indexReg, codeGen.options.compTarget.indexRegType, scale=info.structSize))
                else it += IRInstructions.storeMemory(Opcode.STOREX, targetDt, valueReg, IRMemory.indexed(info.arrayName, indexReg, codeGen.options.compTarget.indexRegType, scale=info.structSize, displacement=info.fieldOffset))
            }
        }
        return true
    }
}
