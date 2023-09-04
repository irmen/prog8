package prog8.codegen.cpu6502.assignment

import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal
import prog8.codegen.cpu6502.VariableAllocator
import prog8.codegen.cpu6502.returnsWhatWhere


internal class AssignmentAsmGen(private val program: PtProgram,
                                private val asmgen: AsmGen6502Internal,
                                private val anyExprGen: AnyExprAsmGen,
                                private val allocator: VariableAllocator) {
    private val augmentableAsmGen = AugmentableAssignmentAsmGen(program, this, asmgen, allocator)

    fun translate(assignment: PtAssignment) {
        val target = AsmAssignTarget.fromAstAssignment(assignment.target, assignment.definingISub(), asmgen)
        val source = AsmAssignSource.fromAstSource(assignment.value, program, asmgen).adjustSignedUnsigned(target)
        val pos = if(assignment.position !== Position.DUMMY) assignment.position else if(assignment.target.position !== Position.DUMMY) assignment.target.position else assignment.value.position
        val assign = AsmAssignment(source, target, program.memsizer, pos)
        translateNormalAssignment(assign, assignment.definingISub())
    }

    fun translate(augmentedAssign: PtAugmentedAssign) {
        val target = AsmAssignTarget.fromAstAssignment(augmentedAssign.target, augmentedAssign.definingISub(), asmgen)
        val source = AsmAssignSource.fromAstSource(augmentedAssign.value, program, asmgen).adjustSignedUnsigned(target)
        val pos = if(augmentedAssign.position !== Position.DUMMY) augmentedAssign.position else if(augmentedAssign.target.position !== Position.DUMMY) augmentedAssign.target.position else augmentedAssign.value.position
        val assign = AsmAugmentedAssignment(source, augmentedAssign.operator, target, program.memsizer, pos)
        augmentableAsmGen.translate(assign, augmentedAssign.definingISub())
    }

    fun translateNormalAssignment(assign: AsmAssignment, scope: IPtSubroutine?) {
        when(assign.source.kind) {
            SourceStorageKind.LITERALNUMBER -> {
                // simple case: assign a constant number
                val num = assign.source.number!!.number
                when (assign.target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> assignConstantByte(assign.target, num.toInt())
                    DataType.UWORD, DataType.WORD -> assignConstantWord(assign.target, num.toInt())
                    DataType.FLOAT -> assignConstantFloat(assign.target, num)
                    else -> throw AssemblyError("weird numval type")
                }
            }
            SourceStorageKind.VARIABLE -> {
                // simple case: assign from another variable
                val variable = assign.source.asmVarname
                when (assign.target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> assignVariableByte(assign.target, variable)
                    DataType.WORD -> assignVariableWord(assign.target, variable, assign.source.datatype)
                    DataType.UWORD -> {
                        if(assign.source.datatype in PassByReferenceDatatypes)
                            assignAddressOf(assign.target, variable)
                        else
                            assignVariableWord(assign.target, variable, assign.source.datatype)
                    }
                    DataType.FLOAT -> assignVariableFloat(assign.target, variable)
                    DataType.STR -> assignVariableString(assign.target, variable)
                    else -> throw AssemblyError("unsupported assignment target type ${assign.target.datatype}")
                }
            }
            SourceStorageKind.ARRAY -> {
                val value = assign.source.array!!
                val elementDt = assign.source.datatype
                val arrayVarName = asmgen.asmVariableName(value.variable)

                if(value.usesPointerVariable) {
                    if(elementDt !in ByteDatatypes)
                        throw AssemblyError("non-array var indexing requires bytes dt")
                    if(value.type != DataType.UBYTE)
                        throw AssemblyError("non-array var indexing requires bytes index")
                    asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                    if(asmgen.isZpVar(value.variable)) {
                        asmgen.out("  lda  ($arrayVarName),y")
                    } else {
                        asmgen.out("  lda  $arrayVarName |  sta  P8ZP_SCRATCH_W1 |  lda  $arrayVarName+1 |  sta  P8ZP_SCRATCH_W1+1")
                        asmgen.out("  lda  (P8ZP_SCRATCH_W1),y")
                    }
                    assignRegisterByte(assign.target, CpuRegister.A, elementDt in SignedDatatypes)
                    return
                }

                val constIndex = value.index.asConstInteger()

                if(value.splitWords) {
                    require(elementDt in WordDatatypes)
                    if(constIndex!=null) {
                        asmgen.out("  lda  ${arrayVarName}_lsb+$constIndex |  ldy  ${arrayVarName}_msb+$constIndex")
                        assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                    } else {
                        asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                        asmgen.out("  lda  ${arrayVarName}_lsb,y |  tax |  lda  ${arrayVarName}_msb,y |  tay |  txa")
                        assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                    }
                    return
                }

                if (constIndex!=null) {
                    // constant array index value
                    val indexValue = constIndex * program.memsizer.memorySize(elementDt)
                    when (elementDt) {
                        in ByteDatatypes -> {
                            asmgen.out("  lda  $arrayVarName+$indexValue")
                            assignRegisterByte(assign.target, CpuRegister.A, elementDt in SignedDatatypes)
                        }
                        in WordDatatypes -> {
                            asmgen.out("  lda  $arrayVarName+$indexValue |  ldy  $arrayVarName+$indexValue+1")
                            assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                        }
                        DataType.FLOAT -> {
                            asmgen.out("  lda  #<($arrayVarName+$indexValue) |  ldy  #>($arrayVarName+$indexValue)")
                            assignFloatFromAY(assign.target)
                        }
                        else ->
                            throw AssemblyError("weird array type")
                    }
                } else {
                    when (elementDt) {
                        in ByteDatatypes -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y")
                            assignRegisterByte(assign.target, CpuRegister.A, elementDt in SignedDatatypes)
                        }
                        in WordDatatypes  -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y |  tax |  lda  $arrayVarName+1,y |  tay |  txa")
                            assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                        }
                        DataType.FLOAT -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.A)
                            asmgen.out("""
                                ldy  #>$arrayVarName
                                clc
                                adc  #<$arrayVarName
                                bcc  +
                                iny
+""")
                            assignFloatFromAY(assign.target)
                        }
                        else ->
                            throw AssemblyError("weird array elt type")
                    }
                }
            }
            SourceStorageKind.MEMORY -> {
                fun assignViaExprEval(expression: PtExpression) {
                    assignExpressionToVariable(expression, "P8ZP_SCRATCH_W2", DataType.UWORD)
                    asmgen.loadAFromZpPointerVar("P8ZP_SCRATCH_W2")
                    assignRegisterByte(assign.target, CpuRegister.A, false)
                }

                val value = assign.source.memory!!
                when (value.address) {
                    is PtNumber -> {
                        val address = (value.address as PtNumber).number.toUInt()
                        assignMemoryByte(assign.target, address, null)
                    }
                    is PtIdentifier -> {
                        assignMemoryByte(assign.target, null, value.address as PtIdentifier)
                    }
                    is PtBinaryExpression -> {
                        val addrExpr = value.address as PtBinaryExpression
                        if(asmgen.tryOptimizedPointerAccessWithA(addrExpr, addrExpr.operator, false)) {
                            assignRegisterByte(assign.target, CpuRegister.A, false)
                        } else {
                            assignViaExprEval(value.address)
                        }
                    }
                    else -> assignViaExprEval(value.address)
                }
            }
            SourceStorageKind.EXPRESSION -> {
                assignExpression(assign, scope)
            }
            SourceStorageKind.REGISTER -> {
                asmgen.assignRegister(assign.source.register!!, assign.target)
            }
        }
    }

    private fun assignExpression(assign: AsmAssignment, scope: IPtSubroutine?) {
        when(val value = assign.source.expression!!) {
            is PtAddressOf -> {
                val sourceName = asmgen.asmSymbolName(value.identifier)
                assignAddressOf(assign.target, sourceName)
            }
            is PtNumber -> throw AssemblyError("source kind should have been literalnumber")
            is PtIdentifier -> throw AssemblyError("source kind should have been variable")
            is PtArrayIndexer -> throw AssemblyError("source kind should have been array")
            is PtMemoryByte -> throw AssemblyError("source kind should have been memory")
            is PtTypeCast -> assignTypeCastedValue(assign.target, value.type, value.value, value)
            is PtFunctionCall -> {
                val symbol = asmgen.symbolTable.lookup(value.name)
                val sub = symbol!!.astNode as IPtSubroutine
                asmgen.translateFunctionCall(value)
                val returnValue = sub.returnsWhatWhere().singleOrNull { it.first.registerOrPair!=null } ?: sub.returnsWhatWhere().single { it.first.statusflag!=null }
                when (returnValue.second) {
                    DataType.STR -> {
                        when(assign.target.datatype) {
                            DataType.UWORD -> {
                                // assign the address of the string result value
                                assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                            }
                            DataType.STR, DataType.ARRAY_UB, DataType.ARRAY_B -> {
                                throw AssemblyError("stringvalue assignment should have been replaced by a call to strcpy")
                            }
                            else -> throw AssemblyError("weird target dt")
                        }
                    }
                    DataType.FLOAT -> {
                        // float result from function sits in FAC1
                        assignFAC1float(assign.target)
                    }
                    else -> {
                        // do NOT restore X register before assigning the result values first
                        when (returnValue.first.registerOrPair) {
                            RegisterOrPair.A -> assignRegisterByte(assign.target, CpuRegister.A, returnValue.second in SignedDatatypes)
                            RegisterOrPair.X -> assignRegisterByte(assign.target, CpuRegister.X, returnValue.second in SignedDatatypes)
                            RegisterOrPair.Y -> assignRegisterByte(assign.target, CpuRegister.Y, returnValue.second in SignedDatatypes)
                            RegisterOrPair.AX -> assignVirtualRegister(assign.target, RegisterOrPair.AX)
                            RegisterOrPair.AY -> assignVirtualRegister(assign.target, RegisterOrPair.AY)
                            RegisterOrPair.XY -> assignVirtualRegister(assign.target, RegisterOrPair.XY)
                            RegisterOrPair.R0 -> assignVirtualRegister(assign.target, RegisterOrPair.R0)
                            RegisterOrPair.R1 -> assignVirtualRegister(assign.target, RegisterOrPair.R1)
                            RegisterOrPair.R2 -> assignVirtualRegister(assign.target, RegisterOrPair.R2)
                            RegisterOrPair.R3 -> assignVirtualRegister(assign.target, RegisterOrPair.R3)
                            RegisterOrPair.R4 -> assignVirtualRegister(assign.target, RegisterOrPair.R4)
                            RegisterOrPair.R5 -> assignVirtualRegister(assign.target, RegisterOrPair.R5)
                            RegisterOrPair.R6 -> assignVirtualRegister(assign.target, RegisterOrPair.R6)
                            RegisterOrPair.R7 -> assignVirtualRegister(assign.target, RegisterOrPair.R7)
                            RegisterOrPair.R8 -> assignVirtualRegister(assign.target, RegisterOrPair.R8)
                            RegisterOrPair.R9 -> assignVirtualRegister(assign.target, RegisterOrPair.R9)
                            RegisterOrPair.R10 -> assignVirtualRegister(assign.target, RegisterOrPair.R10)
                            RegisterOrPair.R11 -> assignVirtualRegister(assign.target, RegisterOrPair.R11)
                            RegisterOrPair.R12 -> assignVirtualRegister(assign.target, RegisterOrPair.R12)
                            RegisterOrPair.R13 -> assignVirtualRegister(assign.target, RegisterOrPair.R13)
                            RegisterOrPair.R14 -> assignVirtualRegister(assign.target, RegisterOrPair.R14)
                            RegisterOrPair.R15 -> assignVirtualRegister(assign.target, RegisterOrPair.R15)
                            else -> {
                                val sflag = returnValue.first.statusflag
                                if(sflag!=null)
                                    assignStatusFlagByte(assign.target, sflag)
                                else
                                    throw AssemblyError("should be just one register byte result value")
                            }
                        }
                    }
                }
            }
            is PtBuiltinFunctionCall -> {
                val returnDt = asmgen.translateBuiltinFunctionCallExpression(value, assign.target.register)
                if(assign.target.register==null) {
                    // still need to assign the result to the target variable/etc.
                    when(returnDt) {
                        in ByteDatatypes -> assignRegisterByte(assign.target, CpuRegister.A, returnDt in SignedDatatypes)            // function's byte result is in A
                        in WordDatatypes -> assignRegisterpairWord(assign.target, RegisterOrPair.AY)    // function's word result is in AY
                        DataType.STR -> {
                            when (assign.target.datatype) {
                                DataType.STR -> {
                                    asmgen.out("""
                                        tax
                                        lda  #<${assign.target.asmVarname}
                                        sta  P8ZP_SCRATCH_W1
                                        lda  #>${assign.target.asmVarname}
                                        sta  P8ZP_SCRATCH_W1+1
                                        txa
                                        jsr  prog8_lib.strcpy""")
                                }
                                DataType.UWORD -> assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                                else -> throw AssemblyError("str return value type mismatch with target")
                            }
                        }
                        DataType.FLOAT -> {
                            // float result from function sits in FAC1
                            assignFAC1float(assign.target)
                        }
                        else -> throw AssemblyError("weird result type")
                    }
                }
            }
            is PtPrefix -> {
                if(assign.target.array==null) {
                    if(assign.source.datatype==assign.target.datatype) {
                        if(assign.source.datatype in IntegerDatatypes) {
                            val signed = assign.source.datatype in SignedDatatypes
                            if(assign.source.datatype in ByteDatatypes) {
                                assignExpressionToRegister(value.value, RegisterOrPair.A, signed)
                                when(value.operator) {
                                    "+" -> {}
                                    "-" -> {
                                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                            asmgen.out("  eor  #255 |  ina")
                                        else
                                            asmgen.out("  eor  #255 |  clc |  adc  #1")
                                    }
                                    "~" -> asmgen.out("  eor  #255")
                                    "not" -> throw AssemblyError("not should have been replaced in the Ast by ==0")
                                    else -> throw AssemblyError("invalid prefix operator")
                                }
                                assignRegisterByte(assign.target, CpuRegister.A, signed)
                            } else {
                                assignExpressionToRegister(value.value, RegisterOrPair.AY, signed)
                                when(value.operator) {
                                    "+" -> {}
                                    "-" -> {
                                        asmgen.out("""
                                            sec
                                            eor  #255
                                            adc  #0
                                            tax
                                            tya
                                            eor  #255
                                            adc  #0
                                            tay
                                            txa""")
                                    }
                                    "~" -> asmgen.out("  tax |  tya |  eor  #255 |  tay |  txa |  eor  #255")
                                    "not" -> throw AssemblyError("not should have been replaced in the Ast by ==0")
                                    else -> throw AssemblyError("invalid prefix operator")
                                }
                                assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                            }
                        } else {
                            // First assign the value to the target then apply the operator in place on the target.
                            // This saves a temporary variable
                            translateNormalAssignment(
                                AsmAssignment(
                                    AsmAssignSource.fromAstSource(value.value, program, asmgen),
                                    assign.target, program.memsizer, assign.position
                                ), scope
                            )
                            when (value.operator) {
                                "+" -> {}
                                "-" -> inplaceNegate(assign, true, scope)
                                "~" -> inplaceInvert(assign, scope)
                                "not" -> throw AssemblyError("not should have been replaced in the Ast by ==0")
                                else -> throw AssemblyError("invalid prefix operator")
                            }
                        }
                    } else {
                        // use a temporary variable
                        val tempvar = if(value.type in ByteDatatypes) "P8ZP_SCRATCH_B1" else "P8ZP_SCRATCH_W1"
                        assignExpressionToVariable(value.value, tempvar, value.type)
                        when (value.operator) {
                            "+" -> {}
                            "-", "~" -> {
                                val assignTempvar = AsmAssignment(
                                    AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, value.type, variableAsmName = tempvar),
                                    AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, value.type, scope, assign.position, variableAsmName = tempvar),
                                    program.memsizer, assign.position)
                                if(value.operator=="-")
                                    inplaceNegate(assignTempvar, true, scope)
                                else
                                    inplaceInvert(assignTempvar, scope)
                            }
                            "not" -> throw AssemblyError("not should have been replaced in the Ast by ==0")
                            else -> throw AssemblyError("invalid prefix operator")
                        }
                        if(value.type in ByteDatatypes)
                            assignVariableByte(assign.target, tempvar)
                        else
                            assignVariableWord(assign.target, tempvar, value.type)
                    }
                } else {
                    assignPrefixedExpressionToArrayElt(assign, scope)
                }
            }
            is PtContainmentCheck -> {
                containmentCheckIntoA(value)
                assignRegisterByte(assign.target, CpuRegister.A, false)
            }
            is PtBinaryExpression -> {
                if(!attemptAssignOptimizedBinexpr(value, assign)) {
                    // TOO BAD: the expression was too complex to translate into assembly.
                    val pos = if(value.position!==Position.DUMMY) value.position else assign.position
                    throw AssemblyError("Expression is too complex to translate into assembly. Split it up into several separate statements, introduce a temporary variable, or otherwise rewrite it. Location: $pos")
                }
            }
            else -> throw AssemblyError("weird assignment value type $value")
        }
    }

    private fun assignPrefixedExpressionToArrayElt(assign: AsmAssignment, scope: IPtSubroutine?) {
        require(assign.source.expression is PtPrefix)
        if(assign.source.datatype==DataType.FLOAT) {
            // floatarray[x] = -value   ... just use FAC1 to calculate the expression into and then store that back into the array.
            assignExpressionToRegister(assign.source.expression, RegisterOrPair.FAC1, true)
            assignFAC1float(assign.target)
        } else {
            // array[x] = -value   ... use a tempvar then store that back into the array.
            val tempvar = asmgen.getTempVarName(assign.target.datatype)
            val assignToTempvar = AsmAssignment(assign.source,
                AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, assign.target.datatype, assign.target.scope, assign.target.position,
                    variableAsmName=tempvar, origAstTarget = assign.target.origAstTarget), program.memsizer, assign.position)
            asmgen.translateNormalAssignment(assignToTempvar, scope)
            when(assign.target.datatype) {
                in ByteDatatypes -> assignVariableByte(assign.target, tempvar)
                in WordDatatypes -> assignVariableWord(assign.target, tempvar, assign.source.datatype)
                DataType.FLOAT -> assignVariableFloat(assign.target, tempvar)
                else -> throw AssemblyError("weird dt")
            }
        }
    }

    private fun assignVirtualRegister(target: AsmAssignTarget, register: RegisterOrPair) {
        when(target.datatype) {
            in ByteDatatypes -> {
                asmgen.out("  lda  cx16.${register.toString().lowercase()}L")
                assignRegisterByte(target, CpuRegister.A, false)
            }
            in WordDatatypes -> assignRegisterpairWord(target, register)
            else -> throw AssemblyError("expected byte or word")
        }
    }

    private fun attemptAssignOptimizedBinexpr(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        val translatedOk = when (expr.operator) {
            in ComparisonOperators -> optimizedComparison(expr, assign)
            in setOf("&", "|", "^", "and", "or", "xor") -> optimizedLogicalOrBitwiseExpr(expr, assign.target)
            "+", "-" -> optimizedPlusMinExpr(expr, assign.target)
            "<<", ">>" -> optimizedBitshiftExpr(expr, assign.target)
            "*" -> optimizedMultiplyExpr(expr, assign.target)
            "/" -> optimizedDivideExpr(expr, assign.target)
            "%" -> optimizedRemainderExpr(expr, assign.target)
            else -> false
        }

        return if(translatedOk)
            true
        else
            anyExprGen.assignAnyExpressionUsingStack(expr, assign)
    }

    private fun optimizedComparison(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        if(expr.right.asConstInteger() == 0) {
            if(expr.operator == "==" || expr.operator=="!=") {
                when(assign.target.datatype) {
                    in ByteDatatypes -> if(attemptAssignToByteCompareZero(expr, assign)) return true
                    else -> {
                        // do nothing, this is handled by a type cast.
                    }
                }
            }
        }

        if(expr.left.type in ByteDatatypes && expr.right.type in ByteDatatypes) {
            if(assignOptimizedComparisonBytes(expr, assign))
                return true
        } else  if(expr.left.type in WordDatatypes && expr.right.type in WordDatatypes) {
            if(assignOptimizedComparisonWords(expr, assign))
                return true
        }

        val origTarget = assign.target.origAstTarget
        if(origTarget!=null) {
            assignConstantByte(assign.target, 0)
            val assignTrue = PtNodeGroup()
            val assignment = PtAssignment(assign.position)
            assignment.add(origTarget)
            assignment.add(PtNumber.fromBoolean(true, assign.position))
            assignTrue.add(assignment)
            val assignFalse = PtNodeGroup()
            val ifelse = PtIfElse(assign.position)
            val exprClone = PtBinaryExpression(expr.operator, expr.type, expr.position)
            expr.children.forEach { exprClone.children.add(it) }        // doesn't seem to need a deep clone
            ifelse.add(exprClone)
            ifelse.add(assignTrue)
            ifelse.add(assignFalse)
            ifelse.parent = expr.parent
            asmgen.translate(ifelse)
            return true
        }

        return false
    }

    private fun optimizedRemainderExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        when(expr.type) {
            DataType.UBYTE -> {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                asmgen.out("  pha")
                assignExpressionToRegister(expr.right, RegisterOrPair.Y, false)
                asmgen.out("  pla |  jsr  math.divmod_ub_asm")
                if(target.register==RegisterOrPair.A)
                    asmgen.out("  cmp  #0")     // fix the status register
                else
                    assignRegisterByte(target, CpuRegister.A, false)
                return true
            }
            DataType.UWORD -> {
                asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                asmgen.out("  jsr  math.divmod_uw_asm")
                assignVariableWord(target, "P8ZP_SCRATCH_W2", DataType.UWORD)
                return true
            }
            else -> return false
        }
    }

    private fun optimizedDivideExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        when(expr.type) {
            DataType.UBYTE -> {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                asmgen.out("  pha")
                assignExpressionToRegister(expr.right, RegisterOrPair.Y, false)
                asmgen.out("  pla |  jsr  math.divmod_ub_asm")
                assignRegisterByte(target, CpuRegister.Y, false)
                return true
            }
            DataType.BYTE -> {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, true)
                asmgen.out("  pha")
                assignExpressionToRegister(expr.right, RegisterOrPair.Y, true)
                asmgen.out("  pla |  jsr  math.divmod_b_asm")
                assignRegisterByte(target, CpuRegister.Y, true)
                return true
            }
            DataType.UWORD -> {
                asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                asmgen.out("  jsr  math.divmod_uw_asm")
                assignRegisterpairWord(target, RegisterOrPair.AY)
                return true
            }
            DataType.WORD -> {
                asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                asmgen.out("  jsr  math.divmod_w_asm")
                assignRegisterpairWord(target, RegisterOrPair.AY)
                return true
            }
            else -> return false
        }
    }

    private fun optimizedMultiplyExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        val value = expr.right.asConstInteger()
        if(value==null) {
            when(expr.type) {
                in ByteDatatypes -> {
                    assignExpressionToRegister(expr.left, RegisterOrPair.A, expr.type in SignedDatatypes)
                    asmgen.out("  pha")
                    assignExpressionToRegister(expr.right, RegisterOrPair.Y, expr.type in SignedDatatypes)
                    asmgen.out("  pla |  jsr  math.multiply_bytes")
                    assignRegisterByte(target, CpuRegister.A, false)
                    return true
                }
                in WordDatatypes -> {
                    asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "math.multiply_words.multiplier")
                    asmgen.out("  jsr  math.multiply_words")
                    assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }
                else -> return false
            }
        } else {
            when (expr.type) {
                in ByteDatatypes -> {
                    assignExpressionToRegister(expr.left, RegisterOrPair.A, expr.type in SignedDatatypes)
                    if (value in asmgen.optimizedByteMultiplications)
                        asmgen.out("  jsr  math.mul_byte_${value}")
                    else
                        asmgen.out("  ldy  #$value |  jsr  math.multiply_bytes")
                    assignRegisterByte(target, CpuRegister.A, false)
                    return true
                }
                in WordDatatypes -> {
                    if (value in asmgen.optimizedWordMultiplications) {
                        assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.type in SignedDatatypes)
                        asmgen.out("  jsr  math.mul_word_${value}")
                    }
                    else {
                        asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "math.multiply_words.multiplier")
                        asmgen.out("  jsr  math.multiply_words")
                    }
                    assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }
                else -> return false
            }
        }
    }

    private fun optimizedBitshiftExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        val signed = expr.left.type in SignedDatatypes
        val shifts = expr.right.asConstInteger()
        val dt = expr.left.type
        if(shifts==null) {
            // bit shifts with variable shifts
            when(expr.right.type) {
                in ByteDatatypes -> {
                    assignExpressionToRegister(expr.right, RegisterOrPair.A, false)
                }
                in WordDatatypes -> {
                    assignExpressionToRegister(expr.right, RegisterOrPair.AY, false)
                    asmgen.out("""
                        cpy  #0
                        beq  +
                        lda  #127
+""")
                }
                else -> throw AssemblyError("weird shift value type")
            }
            asmgen.out("  pha")
            if(dt in ByteDatatypes) {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                asmgen.restoreRegisterStack(CpuRegister.Y, true)
                if(expr.operator==">>")
                    if(signed)
                        asmgen.out("  jsr  math.lsr_byte_A")
                    else
                        asmgen.out("  jsr  math.lsr_ubyte_A")
                else
                    asmgen.out("  jsr  math.asl_byte_A")
                assignRegisterByte(target, CpuRegister.A, signed)
                return true
            } else {
                assignExpressionToRegister(expr.left, RegisterOrPair.AY, signed)
                asmgen.restoreRegisterStack(CpuRegister.X, true)
                if(expr.operator==">>")
                    if(signed)
                        asmgen.out("  jsr  math.lsr_word_AY")
                    else
                        asmgen.out("  jsr  math.lsr_uword_AY")
                else
                    asmgen.out("  jsr  math.asl_word_AY")
                assignRegisterpairWord(target, RegisterOrPair.AY)
                return true
            }
        }
        else {
            // bit shift with constant value
            if(dt in ByteDatatypes) {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                when (shifts) {
                    in 0..7 -> {
                        if (expr.operator == "<<") {
                            repeat(shifts) {
                                asmgen.out("  asl  a")
                            }
                        } else {
                            if (signed && shifts > 0) {
                                asmgen.out("  ldy  #$shifts |  jsr  math.lsr_byte_A")
                            } else {
                                repeat(shifts) {
                                    asmgen.out("  lsr  a")
                                }
                            }
                        }
                        assignRegisterByte(target, CpuRegister.A, signed)
                        return true
                    }
                    else -> {
                        if(signed && expr.operator==">>") {
                            asmgen.out("  ldy  #$shifts |  jsr  math.lsr_byte_A")
                        } else {
                            asmgen.out("  lda  #0")
                        }
                        assignRegisterByte(target, CpuRegister.A, signed)
                        return true
                    }
                }
            } else if(dt in WordDatatypes) {
                assignExpressionToRegister(expr.left, RegisterOrPair.AY, signed)
                when (shifts) {
                    in 0..7 -> {
                        if(expr.operator=="<<") {
                            if(shifts>0) {
                                asmgen.out("  sty  P8ZP_SCRATCH_B1")
                                repeat(shifts) {
                                    asmgen.out("  asl  a |  rol  P8ZP_SCRATCH_B1")
                                }
                                asmgen.out("  ldy  P8ZP_SCRATCH_B1")
                            }
                        } else {
                            if(signed && shifts>0) {
                                asmgen.out("  ldx  #$shifts |  jsr  math.lsr_word_AY")
                            } else {
                                if(shifts>0) {
                                    asmgen.out("  sty  P8ZP_SCRATCH_B1")
                                    repeat(shifts) {
                                        asmgen.out("  lsr  P8ZP_SCRATCH_B1 |  ror  a")
                                    }
                                    asmgen.out("  ldy  P8ZP_SCRATCH_B1")
                                }
                            }
                        }
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                    in 8..15 -> {
                        if(expr.operator == "<<") {
                            // msb = lsb << (shifts-8),   lsb = 0
                            repeat(shifts-8) {
                                asmgen.out("  asl  a")
                            }
                            asmgen.out("  tay |  lda  #0")
                        } else {
                            asmgen.out("  ldx  #$shifts |  jsr  math.lsr_word_AY")
                        }
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                    else -> {
                        if(signed && expr.operator==">>") {
                            asmgen.out("  ldx  #$shifts |  jsr  math.lsr_word_AY")
                        } else {
                            asmgen.out("  lda  #0 |  ldy  #0")
                        }
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun optimizedPlusMinExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        val dt = expr.type
        val left = expr.left
        val right = expr.right
        if(dt in ByteDatatypes) {
            when (right) {
                is PtIdentifier -> {
                    assignExpressionToRegister(left, RegisterOrPair.A, dt==DataType.BYTE)
                    val symname = asmgen.asmVariableName(right)
                    if(expr.operator=="+")
                        asmgen.out("  clc |  adc  $symname")
                    else
                        asmgen.out("  sec |  sbc  $symname")
                    assignRegisterByte(target, CpuRegister.A, dt in SignedDatatypes)
                    return true
                }
                is PtNumber -> {
                    assignExpressionToRegister(left, RegisterOrPair.A, dt==DataType.BYTE)
                    if(expr.operator=="+")
                        asmgen.out("  clc |  adc  #${right.number.toHex()}")
                    else
                        asmgen.out("  sec |  sbc  #${right.number.toHex()}")
                    assignRegisterByte(target, CpuRegister.A, dt in SignedDatatypes)
                    return true
                }
                else -> {
                    val rightArrayIndexer = expr.right as? PtArrayIndexer
                    if(rightArrayIndexer!=null && rightArrayIndexer.type in ByteDatatypes && left.type in ByteDatatypes) {
                        // special optimization for  bytevalue +/- bytearr[y] :  no need to use a tempvar, just use adc array,y or sbc array,y
                        assignExpressionToRegister(left, RegisterOrPair.A, left.type==DataType.BYTE)
                        asmgen.out("  pha")
                        asmgen.assignExpressionToRegister(rightArrayIndexer.index, RegisterOrPair.Y, false)
                        asmgen.out("  pla")
                        val arrayvarname = if(rightArrayIndexer.usesPointerVariable)
                                "(${rightArrayIndexer.variable.name})"
                            else
                                asmgen.asmSymbolName(rightArrayIndexer.variable)
                        if (expr.operator == "+")
                            asmgen.out("  clc |  adc  $arrayvarname,y")
                        else
                            asmgen.out("  sec |  sbc  $arrayvarname,y")
                        assignRegisterByte(target, CpuRegister.A, dt in SignedDatatypes)
                    } else {
                        assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", right.type)
                        assignExpressionToRegister(left, RegisterOrPair.A, left.type==DataType.BYTE)
                        if (expr.operator == "+")
                            asmgen.out("  clc |  adc  P8ZP_SCRATCH_B1")
                        else
                            asmgen.out("  sec |  sbc  P8ZP_SCRATCH_B1")
                        assignRegisterByte(target, CpuRegister.A, dt in SignedDatatypes)
                    }
                    return true
                }
            }
        } else if(dt in WordDatatypes) {

            fun doAddOrSubWordExpr() {
                asmgen.assignWordOperandsToAYAndVar(expr.left, expr.right, "P8ZP_SCRATCH_W1")
                if(expr.operator=="+")
                    asmgen.out("""
                                clc
                                adc  P8ZP_SCRATCH_W1
                                tax
                                tya
                                adc  P8ZP_SCRATCH_W1+1
                                tay
                                txa""")
                else
                    asmgen.out("""
                                sec
                                sbc  P8ZP_SCRATCH_W1
                                tax
                                tya
                                sbc  P8ZP_SCRATCH_W1+1
                                tay
                                txa""")
                assignRegisterpairWord(target, RegisterOrPair.AY)
            }

            when (right) {
                is PtAddressOf -> {
                    assignExpressionToRegister(left, RegisterOrPair.AY, dt==DataType.WORD)
                    val symbol = asmgen.asmVariableName(right.identifier)
                    if(expr.operator=="+")
                        asmgen.out("""
                                clc
                                adc  #<$symbol
                                tax
                                tya
                                adc  #>$symbol
                                tay
                                txa""")
                    else
                        asmgen.out("""
                                sec
                                sbc  #<$symbol
                                tax
                                tya
                                sbc  #>$symbol
                                tay
                                txa""")
                    assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }
                is PtIdentifier -> {
                    val symname = asmgen.asmVariableName(right)
                    assignExpressionToRegister(left, RegisterOrPair.AY, dt==DataType.WORD)
                    if(expr.operator=="+")
                        asmgen.out("""
                                clc
                                adc  $symname
                                tax
                                tya
                                adc  $symname+1
                                tay
                                txa""")
                    else
                        asmgen.out("""
                                sec
                                sbc  $symname
                                tax
                                tya
                                sbc  $symname+1
                                tay
                                txa""")
                    assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }
                is PtNumber -> {
                    assignExpressionToRegister(left, RegisterOrPair.AY, dt==DataType.WORD)
                    if(expr.operator=="+") {
                        asmgen.out("""
                                clc
                                adc  #<${right.number.toHex()}
                                tax
                                tya
                                adc  #>${right.number.toHex()}
                                tay
                                txa""")
                    } else if(expr.operator=="-") {
                        asmgen.out("""
                                sec
                                sbc  #<${right.number.toHex()}
                                tax
                                tya
                                sbc  #>${right.number.toHex()}
                                tay
                                txa""")
                    }
                    assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }
                is PtTypeCast -> {
                    val castedValue = right.value
                    if(right.type in WordDatatypes && castedValue.type in ByteDatatypes && castedValue is PtIdentifier) {
                        val castedSymname = asmgen.asmVariableName(castedValue)
                        assignExpressionToRegister(left, RegisterOrPair.AY, dt == DataType.WORD)
                        if (expr.operator == "+")
                            asmgen.out(
                                """
                                    clc
                                    adc  $castedSymname
                                    bcc  +
                                    iny
+"""
                            )
                        else
                            asmgen.out(
                                """
                                    sec
                                    sbc  $castedSymname
                                    bcs  +
                                    dey
+"""
                            )
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                    doAddOrSubWordExpr()
                    return true
                }
                else -> {
                    doAddOrSubWordExpr()
                    return true
                }
            }
        }
        return false
    }

    private fun optimizedLogicalOrBitwiseExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        if (expr.left.type in ByteDatatypes && expr.right.type in ByteDatatypes) {
            if (expr.right.isSimple()) {
                if (expr.right is PtNumber || expr.right is PtIdentifier) {
                    assignLogicalWithSimpleRightOperandByte(target, expr.left, expr.operator, expr.right)
                    return true
                }
                else if (expr.left is PtNumber || expr.left is PtIdentifier) {
                    assignLogicalWithSimpleRightOperandByte(target, expr.right, expr.operator, expr.left)
                    return true
                }
            }

            assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
            asmgen.saveRegisterStack(CpuRegister.A, false)
            assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
            asmgen.restoreRegisterStack(CpuRegister.A, false)
            when (expr.operator) {
                "&", "and" -> asmgen.out("  and  P8ZP_SCRATCH_B1")
                "|", "or" -> asmgen.out("  ora  P8ZP_SCRATCH_B1")
                "^", "xor" -> asmgen.out("  eor  P8ZP_SCRATCH_B1")
                else -> throw AssemblyError("invalid operator")
            }
            assignRegisterByte(target, CpuRegister.A, false)
            return true
        }
        else if (expr.left.type in WordDatatypes && expr.right.type in WordDatatypes) {
            if (expr.right.isSimple()) {
                if (expr.right is PtNumber || expr.right is PtIdentifier) {
                    assignLogicalWithSimpleRightOperandWord(target, expr.left, expr.operator, expr.right)
                    return true
                }
                else if (expr.left is PtNumber || expr.left is PtIdentifier) {
                    assignLogicalWithSimpleRightOperandWord(target, expr.right, expr.operator, expr.left)
                    return true
                }
            }
            asmgen.assignWordOperandsToAYAndVar(expr.left, expr.right, "P8ZP_SCRATCH_W1")
            when (expr.operator) {
                "&", "and" -> asmgen.out("  and  P8ZP_SCRATCH_W1 |  tax |  tya |  and  P8ZP_SCRATCH_W1+1 |  tay |  txa")
                "|", "or" -> asmgen.out("  ora  P8ZP_SCRATCH_W1 |  tax |  tya |  ora  P8ZP_SCRATCH_W1+1 |  tay |  txa")
                "^", "xor" -> asmgen.out("  eor  P8ZP_SCRATCH_W1 |  tax |  tya |  eor  P8ZP_SCRATCH_W1+1 |  tay |  txa")
                else -> throw AssemblyError("invalid operator")
            }
            assignRegisterpairWord(target, RegisterOrPair.AY)
            return true
        }
        return false
    }

    private fun assignOptimizedComparisonBytes(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        val signed = expr.left.type == DataType.BYTE || expr.right.type ==  DataType.BYTE
        when(expr.operator) {
            "==" -> byteEquals(expr)
            "!=" -> byteNotEquals(expr)
            "<" -> byteLess(expr, signed)
            "<=" -> byteLessEquals(expr, signed)
            ">" -> byteGreater(expr, signed)
            ">=" -> byteGreaterEquals(expr, signed)
            else -> return false
        }

        assignRegisterByte(assign.target, CpuRegister.A, false)
        return true
    }

    private fun byteEquals(expr: PtBinaryExpression) {
        when (expr.right) {
            is PtNumber -> {
                val number = (expr.right as PtNumber).number.toInt()
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A)
                asmgen.out("""
                    cmp  #$number
                    beq  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
            is PtIdentifier -> {
                val varname = (expr.right as PtIdentifier).name
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A)
                asmgen.out("""
                    cmp  $varname
                    beq  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
            else -> {
                asmgen.assignByteOperandsToAAndVar(expr.right, expr.left, "P8ZP_SCRATCH_B1")
                asmgen.out("""
                    cmp  P8ZP_SCRATCH_B1
                    beq  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
        }
    }

    private fun byteNotEquals(expr: PtBinaryExpression) {
        when(expr.right) {
            is PtNumber -> {
                val number = (expr.right as PtNumber).number.toInt()
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A)
                asmgen.out("""
                    cmp  #$number
                    bne  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
            is PtIdentifier -> {
                val varname = (expr.right as PtIdentifier).name
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A)
                asmgen.out("""
                    cmp  $varname
                    bne  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
            else -> {
                asmgen.assignByteOperandsToAAndVar(expr.right, expr.left, "P8ZP_SCRATCH_B1")
                asmgen.out("""
                    cmp  P8ZP_SCRATCH_B1
                    bne  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
        }
    }

    private fun byteLess(expr: PtBinaryExpression, signed: Boolean) {
        // note: this is the inverse of byteGreaterEqual
        when(expr.right) {
            is PtNumber -> {
                val number = (expr.right as PtNumber).number.toInt()
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                if(signed) {
                    asmgen.out("""
                        sec
                        sbc  #$number
                        bvs  +
                        eor  #$80
+                       asl  a
                        rol  a
                        and  #1
                        eor  #1""")
                }
                else
                    asmgen.out("""
                        cmp  #$number
                        rol  a
                        and  #1
                        eor  #1""")
            }
            is PtIdentifier -> {
                val varname = (expr.right as PtIdentifier).name
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                if(signed) {
                    asmgen.out("""
                        sec
                        sbc  $varname
                        bvs  +
                        eor  #$80
+                       asl  a
                        rol  a
                        and  #1
                        eor  #1""")
                }
                else
                    asmgen.out("""
                        cmp  $varname
                        rol  a
                        and  #1
                        eor  #1""")
            }
            else -> {
                // note: left and right operands get reversed here to reduce code size
                asmgen.assignByteOperandsToAAndVar(expr.right, expr.left, "P8ZP_SCRATCH_B1")
                if(signed)
                    asmgen.out("""
                        clc
                        sbc  P8ZP_SCRATCH_B1
                        bvs  +
                        eor  #$80
+                       asl  a
                        rol  a
                        and  #1""")
                else
                    asmgen.out("""
                        cmp  P8ZP_SCRATCH_B1
                        bcc  +
                        beq  +
                        lda  #1
                        bne  ++
+                       lda  #0
+""")
            }
        }
    }

    private fun byteLessEquals(expr: PtBinaryExpression, signed: Boolean) {
        // note: this is the inverse of byteGreater
        when(expr.right) {
            is PtNumber -> {
                val number = (expr.right as PtNumber).number.toInt()
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                if(signed)
                    asmgen.out("""
                        sec
                        sbc  #$number
                        bvc  +
                        eor  #$80
+                       bmi  +
                        beq  +
                        lda  #0
                        beq  ++
+                       lda  #1                        
+""")
                else
                    asmgen.out("""
                        cmp  #$number
                        bcc  +
                        beq  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
            }
            is PtIdentifier -> {
                val varname = (expr.right as PtIdentifier).name
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                if(signed)
                    asmgen.out("""
                        sec
                        sbc  $varname
                        bvc  +
                        eor  #$80
+                       bmi  +
                        beq  +
                        lda  #0
                        beq  ++
+                       lda  #1                        
+""")
                else
                    asmgen.out("""
                        cmp  $varname
                        bcc  +
                        beq  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
            }
            else -> {
                // note: left and right operands get reversed here to reduce code size
                asmgen.assignByteOperandsToAAndVar(expr.right, expr.left, "P8ZP_SCRATCH_B1")
                if(signed)
                    asmgen.out("""
                        sec
                        sbc  P8ZP_SCRATCH_B1
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  #1
                        bne  ++
+                       lda  #0                        
+""")
                else
                    asmgen.out("""
                        cmp  P8ZP_SCRATCH_B1
                        lda  #0
                        rol  a""")
            }
        }
    }

    private fun byteGreater(expr: PtBinaryExpression, signed: Boolean) {
        // note: this is the inverse of byteLessEqual
        when(expr.right) {
            is PtNumber -> {
                val number = (expr.right as PtNumber).number.toInt()
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                if(signed)
                    asmgen.out("""
                        sec
                        sbc  #$number
                        beq  +++
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  #1
                        bne  ++
+                       lda  #0                        
+""")
                else
                    asmgen.out("""
                        cmp  #$number
                        bcc  +
                        beq  +
                        lda  #1
                        bne  ++
+                       lda  #0
+""")
            }
            is PtIdentifier -> {
                val varname = (expr.right as PtIdentifier).name
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                if(signed)
                    asmgen.out("""
                        sec
                        sbc  $varname
                        beq  +++
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  #1
                        bne  ++
+                       lda  #0                        
+""")
                else
                    asmgen.out("""
                        cmp  $varname
                        bcc  +
                        beq  +
                        lda  #1
                        bne  ++
+                       lda  #0
+""")
            }
            else -> {
                // note: left and right operands get reversed here to reduce code size
                asmgen.assignByteOperandsToAAndVar(expr.right, expr.left, "P8ZP_SCRATCH_B1")
                if(signed)
                    asmgen.out("""
                        sec
                        sbc  P8ZP_SCRATCH_B1
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  #0
                        beq  ++
+                       lda  #1                        
+""")
                else
                    asmgen.out("""
                        cmp  P8ZP_SCRATCH_B1
                        lda  #0
                        rol  a
                        eor  #1""")
            }
        }
    }

    private fun byteGreaterEquals(expr: PtBinaryExpression, signed: Boolean) {
        // note: this is the inverse of byteLess
        when(expr.right) {
            is PtNumber -> {
                val number = (expr.right as PtNumber).number.toInt()
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                if(signed) {
                    asmgen.out("""
                        sec
                        sbc  #$number
                        bvs  +
                        eor  #$80
+                       asl  a
                        rol  a
                        and  #1""")
                }
                else
                    asmgen.out("""
                        cmp  #$number
                        rol  a
                        and  #1""")
            }
            is PtIdentifier -> {
                val varname = (expr.right as PtIdentifier).name
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                if(signed) {
                    asmgen.out("""
                        sec
                        sbc  $varname
                        bvs  +
                        eor  #$80
+                       asl  a
                        rol  a
                        and  #1""")
                }
                else
                    asmgen.out("""
                        cmp  $varname
                        rol  a
                        and  #1""")
            }
            else -> {
                // note: left and right operands get reversed here to reduce code size
                asmgen.assignByteOperandsToAAndVar(expr.right, expr.left, "P8ZP_SCRATCH_B1")
                if(signed)
                    asmgen.out("""
                        clc
                        sbc  P8ZP_SCRATCH_B1
                        bvs  +
                        eor  #$80
+                       asl  a
                        rol  a
                        and  #1
                        eor  #1""")
                else
                    asmgen.out("""
                        cmp  P8ZP_SCRATCH_B1
                        bcc  +
                        beq  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
            }
        }
    }

    private fun assignOptimizedComparisonWords(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        val signed = expr.left.type == DataType.WORD || expr.right.type ==  DataType.WORD
        when(expr.operator) {
            "==" -> {
                asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                asmgen.out("""
                    cmp  P8ZP_SCRATCH_W1
                    bne  +
                    cpy  P8ZP_SCRATCH_W1+1
                    bne  +
                    lda  #1
                    bne  ++
+                   lda  #0
+""")
            }
            "!=" -> {
                asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                asmgen.out("""
                    cmp  P8ZP_SCRATCH_W1
                    bne  +
                    cpy  P8ZP_SCRATCH_W1+1
                    bne  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
            "<" -> {
                if(signed) {
                    asmgen.assignWordOperandsToAYAndVar(expr.left, expr.right, "P8ZP_SCRATCH_W1")
                    asmgen.out("""
		cmp  P8ZP_SCRATCH_W1
        tya
		sbc  P8ZP_SCRATCH_W1+1
		bvc  +
		eor  #${'$'}80
+		bpl  ++
+       lda  #1
        bne  ++
+       lda  #0
+""")
                }
                else {
                    asmgen.assignWordOperandsToAYAndVar(expr.left, expr.right, "P8ZP_SCRATCH_W1")
                    asmgen.out("""
		cpy  P8ZP_SCRATCH_W1+1
		bcc  +
        bne  ++
		cmp  P8ZP_SCRATCH_W1
		bcs  ++
+       lda  #1
        bne  ++
+       lda  #0
+""")
                }
            }
            "<=" -> {
                if(signed) {
                    asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                    asmgen.out("""
		cmp  P8ZP_SCRATCH_W1
        tya
		sbc  P8ZP_SCRATCH_W1+1
		bvc  +
		eor  #${'$'}80
+		bmi  +
        lda  #1
        bne  ++
+       lda  #0
+""")
                }
                else {
                    asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                    asmgen.out("""
		cpy  P8ZP_SCRATCH_W1+1
		bcc  ++
		bne  +
		cmp  P8ZP_SCRATCH_W1
		bcc  ++                        
+       lda  #1
        bne  ++
+       lda  #0
+""")
                }
            }
            ">" -> {
                if(signed) {
                    asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                    asmgen.out("""
		cmp  P8ZP_SCRATCH_W1
        tya
		sbc  P8ZP_SCRATCH_W1+1
		bvc  +
		eor  #${'$'}80
+		bpl  ++
+       lda  #1
        bne  ++
+       lda  #0
+""")
                }
                else {
                    asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                    asmgen.out("""
		cpy  P8ZP_SCRATCH_W1+1
		bcc  +
        bne  ++
		cmp  P8ZP_SCRATCH_W1
		bcs  ++
+       lda  #1
        bne  ++
+       lda  #0
+""")
                }
            }
            ">=" -> {
                if(signed) {
                    asmgen.assignWordOperandsToAYAndVar(expr.left, expr.right, "P8ZP_SCRATCH_W1")
                    asmgen.out("""
		cmp  P8ZP_SCRATCH_W1
        tya
		sbc  P8ZP_SCRATCH_W1+1
		bvc  +
		eor  #${'$'}80
+		bmi  +
        lda  #1
        bne  ++
+       lda  #0
+""")
                }
                else {
                    asmgen.assignWordOperandsToAYAndVar(expr.left, expr.right, "P8ZP_SCRATCH_W1")
                    asmgen.out("""
		cpy  P8ZP_SCRATCH_W1+1
		bcc  ++
		bne  +
		cmp  P8ZP_SCRATCH_W1
		bcc  ++                        
+       lda  #1
        bne  ++
+       lda  #0
+""")
                }
            }
            else -> return false
        }

        assignRegisterByte(assign.target, CpuRegister.A, signed)
        return true
    }

    private fun assignLogicalWithSimpleRightOperandByte(target: AsmAssignTarget, left: PtExpression, operator: String, right: PtExpression) {
        assignExpressionToRegister(left, RegisterOrPair.A, false)
        val operand = when(right) {
            is PtNumber -> "#${right.number.toHex()}"
            is PtIdentifier -> asmgen.asmSymbolName(right)
            else -> throw AssemblyError("wrong right operand type")
        }
        when (operator) {
            "&", "and" -> asmgen.out("  and  $operand")
            "|", "or" -> asmgen.out("  ora  $operand")
            "^", "xor" -> asmgen.out("  eor  $operand")
            else -> throw AssemblyError("invalid operator")
        }
        assignRegisterByte(target, CpuRegister.A, false)
    }

    private fun assignLogicalWithSimpleRightOperandWord(target: AsmAssignTarget, left: PtExpression, operator: String, right: PtExpression) {
        assignExpressionToRegister(left, RegisterOrPair.AY, false)
        when(right) {
            is PtNumber -> {
                val number = right.number.toHex()
                when (operator) {
                    "&", "and" -> asmgen.out("  and  #<$number |  tax |  tya |  and  #>$number |  tay |  txa")
                    "|", "or" -> asmgen.out("  ora  #<$number |  tax |  tya |  ora  #>$number |  tay |  txa")
                    "^", "xor" -> asmgen.out("  eor  #<$number |  tax |  tya |  eor  #>$number |  tay |  txa")
                    else -> throw AssemblyError("invalid operator")
                }
            }
            is PtIdentifier -> {
                val name = asmgen.asmSymbolName(right)
                when (operator) {
                    "&", "and" -> asmgen.out("  and  $name |  tax |  tya |  and  $name+1 |  tay |  txa")
                    "|", "or" -> asmgen.out("  ora  $name |  tax |  tya |  ora  $name+1 |  tay |  txa")
                    "^", "xor" -> asmgen.out("  eor  $name |  tax |  tya |  eor  $name+1 |  tay |  txa")
                    else -> throw AssemblyError("invalid operator")
                }
            }
            else -> throw AssemblyError("wrong right operand type")
        }
        assignRegisterpairWord(target, RegisterOrPair.AY)
    }

    private fun attemptAssignToByteCompareZero(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        // TODO optimized code for   (word1 & word2) == 0  :
//        if(expr.left.type in WordDatatypes && (expr.operator=="==" || expr.operator=="!=") && expr.left is PtBinaryExpression) {
//            ...
//        }

        when (expr.operator) {
            "==" -> {
                when(val dt = expr.left.type) {
                    in ByteDatatypes -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.A, dt==DataType.BYTE)
                        asmgen.out("""
                            beq  +
                            lda  #1
+                           eor  #1""")
                        assignRegisterByte(assign.target, CpuRegister.A, false)
                        return true
                    }
                    in WordDatatypes -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.AY, dt==DataType.WORD)
                        asmgen.out("""
                            sty  P8ZP_SCRATCH_B1
                            ora  P8ZP_SCRATCH_B1
                            beq  +
                            lda  #1
+                           eor  #1""")
                        assignRegisterByte(assign.target, CpuRegister.A, false)
                        return true
                    }
                    DataType.FLOAT -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.FAC1, true)
                        asmgen.out("  jsr  floats.SIGN |  and  #1 |  eor  #1")
                        assignRegisterByte(assign.target, CpuRegister.A, false)
                        return true
                    }
                    else->{
                        return false
                    }
                }
            }
            "!=" -> {
                when(val dt = expr.left.type) {
                    in ByteDatatypes -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.A, dt==DataType.BYTE)
                        asmgen.out("  beq  + |  lda  #1")
                        asmgen.out("+")
                        assignRegisterByte(assign.target, CpuRegister.A, false)
                        return true
                    }
                    in WordDatatypes -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.AY, dt==DataType.WORD)
                        asmgen.out("  sty  P8ZP_SCRATCH_B1 |  ora  P8ZP_SCRATCH_B1")
                        asmgen.out("  beq  + |  lda  #1")
                        asmgen.out("+")
                        assignRegisterByte(assign.target, CpuRegister.A, false)
                        return true
                    }
                    DataType.FLOAT -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.FAC1, true)
                        asmgen.out("  jsr  floats.SIGN")
                        assignRegisterByte(assign.target, CpuRegister.A, true)
                        return true
                    }
                    else->{
                        return false
                    }
                }
            }
            else -> return false
        }
    }

    private fun containmentCheckIntoA(containment: PtContainmentCheck) {
        val elementDt = containment.element.type
        val symbol = asmgen.symbolTable.lookup(containment.iterable.name)
        val variable = symbol!!.astNode as IPtVariable
        val varname = asmgen.asmVariableName(containment.iterable)
        val numElements = when(variable) {
            is PtConstant -> null
            is PtMemMapped -> variable.arraySize
            is PtVariable -> variable.arraySize
        }
        when(variable.type) {
            DataType.STR -> {
                // use subroutine
                assignExpressionToRegister(containment.element, RegisterOrPair.A, elementDt == DataType.BYTE)
                asmgen.saveRegisterStack(CpuRegister.A, true)
                assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), symbol.astNode.position,"P8ZP_SCRATCH_W1"), varname)
                asmgen.restoreRegisterStack(CpuRegister.A, false)
                val stringVal = (variable as PtVariable).value as PtString
                asmgen.out("  ldy  #${stringVal.value.length}")
                asmgen.out("  jsr  prog8_lib.containment_bytearray")
                return
            }
            DataType.ARRAY_F -> {
                throw AssemblyError("containment check of floats not supported")
            }
            DataType.ARRAY_B, DataType.ARRAY_UB -> {
                assignExpressionToRegister(containment.element, RegisterOrPair.A, elementDt == DataType.BYTE)
                asmgen.saveRegisterStack(CpuRegister.A, true)
                assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), symbol.astNode.position, "P8ZP_SCRATCH_W1"), varname)
                asmgen.restoreRegisterStack(CpuRegister.A, false)
                asmgen.out("  ldy  #$numElements")
                asmgen.out("  jsr  prog8_lib.containment_bytearray")
                return
            }
            DataType.ARRAY_W, DataType.ARRAY_UW -> {
                assignExpressionToVariable(containment.element, "P8ZP_SCRATCH_W1", elementDt)
                assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), symbol.astNode.position, "P8ZP_SCRATCH_W2"), varname)
                asmgen.out("  ldy  #$numElements")
                asmgen.out("  jsr  prog8_lib.containment_wordarray")
                return
            }
            else -> throw AssemblyError("invalid dt")
        }
    }

    private fun assignStatusFlagByte(target: AsmAssignTarget, statusflag: Statusflag) {
        when(statusflag) {
            Statusflag.Pc -> {
                asmgen.out("  lda  #0 |  rol  a")
            }
            Statusflag.Pv -> {
                asmgen.out("""
                    bvs  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
            else -> throw AssemblyError("can't use Z or N flags as return 'values'")
        }
        assignRegisterByte(target, CpuRegister.A, false)
    }

    private fun assignTypeCastedValue(target: AsmAssignTarget, targetDt: DataType, value: PtExpression, origTypeCastExpression: PtTypeCast) {
        val valueDt = value.type
        if(valueDt==targetDt)
            throw AssemblyError("type cast to identical dt should have been removed")

        when(value) {
            is PtIdentifier -> {
                if(targetDt in WordDatatypes) {
                    if(valueDt==DataType.UBYTE) {
                        assignVariableUByteIntoWord(target, value)
                        return
                    }
                    if(valueDt==DataType.BYTE) {
                        assignVariableByteIntoWord(target, value)
                        return
                    }
                }
            }
            is PtMemoryByte -> {
                if(targetDt in WordDatatypes) {

                    fun assignViaExprEval(addressExpression: PtExpression) {
                        asmgen.assignExpressionToVariable(addressExpression, "P8ZP_SCRATCH_W2", DataType.UWORD)
                        asmgen.loadAFromZpPointerVar("P8ZP_SCRATCH_W2")
                        asmgen.out("  ldy  #0")
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                    }

                    when (value.address) {
                        is PtNumber -> {
                            val address = (value.address as PtNumber).number.toUInt()
                            assignMemoryByteIntoWord(target, address, null)
                        }
                        is PtIdentifier -> {
                            assignMemoryByteIntoWord(target, null, value.address as PtIdentifier)
                        }
                        is PtBinaryExpression -> {
                            val addrExpr = value.address as PtBinaryExpression
                            if(asmgen.tryOptimizedPointerAccessWithA(addrExpr, addrExpr.operator, false)) {
                                asmgen.out("  ldy  #0")
                                assignRegisterpairWord(target, RegisterOrPair.AY)
                            } else {
                                assignViaExprEval(value.address)
                            }
                        }
                        else -> {
                            assignViaExprEval(value.address)
                        }
                    }
                    return
                }
            }
            is PtNumber -> throw AssemblyError("a cast of a literal value should have been const-folded away")
            else -> {}
        }


        // special case optimizations
        if(target.kind == TargetStorageKind.VARIABLE) {
            if(value is PtIdentifier && valueDt != DataType.UNDEFINED)
                return assignTypeCastedIdentifier(target.asmVarname, targetDt, asmgen.asmVariableName(value), valueDt)

            when (valueDt) {
                in ByteDatatypes -> {
                    assignExpressionToRegister(value, RegisterOrPair.A, valueDt==DataType.BYTE)
                    assignTypeCastedRegisters(target.asmVarname, targetDt, RegisterOrPair.A, valueDt)
                }
                in WordDatatypes -> {
                    assignExpressionToRegister(value, RegisterOrPair.AY, valueDt==DataType.WORD)
                    assignTypeCastedRegisters(target.asmVarname, targetDt, RegisterOrPair.AY, valueDt)
                }
                DataType.FLOAT -> {
                    assignExpressionToRegister(value, RegisterOrPair.FAC1, true)
                    assignTypeCastedFloatFAC1(target.asmVarname, targetDt)
                }
                in PassByReferenceDatatypes -> {
                    // str/array value cast (most likely to UWORD, take address-of)
                    assignExpressionToVariable(value, target.asmVarname, targetDt)
                }
                else -> throw AssemblyError("strange dt in typecast assign to var: $valueDt  -->  $targetDt")
            }
            return
        }

        if(valueDt in WordDatatypes && origTypeCastExpression.type == DataType.UBYTE) {
            val parentTc = origTypeCastExpression.parent as? PtTypeCast
            if(parentTc!=null && parentTc.type==DataType.UWORD) {
                // typecast a word value to ubyte and directly back to uword
                // generate code for lsb(value) here instead of the ubyte typecast
                return assignCastViaLsbFunc(value, target)
            }
        }

        if(valueDt==DataType.UBYTE || valueDt==DataType.BOOL) {
            when(target.register) {
                RegisterOrPair.A,
                RegisterOrPair.X,
                RegisterOrPair.Y -> {
                    // 'cast' an ubyte value to a byte register; no cast needed at all
                    return assignExpressionToRegister(value, target.register, false)
                }
                RegisterOrPair.AX,
                RegisterOrPair.AY,
                RegisterOrPair.XY,
                in Cx16VirtualRegisters -> {
                    // cast an ubyte value to a 16 bits register, just assign it and make use of the value extension
                    return assignExpressionToRegister(value, target.register!!, false)
                }
                else -> {}
            }
        } else if(valueDt==DataType.UWORD) {
            when(target.register) {
                RegisterOrPair.A,
                RegisterOrPair.X,
                RegisterOrPair.Y -> {
                    // cast an uword to a byte register, do this via lsb(value)
                    // generate code for lsb(value) here instead of the ubyte typecast
                    return assignCastViaLsbFunc(value, target)
                }
                RegisterOrPair.AX,
                RegisterOrPair.AY,
                RegisterOrPair.XY,
                in Cx16VirtualRegisters -> {
                    // 'cast' uword into a 16 bits register, just assign it
                    return assignExpressionToRegister(value, target.register!!, false)
                }
                else -> {}
            }
        }

        if(target.kind==TargetStorageKind.REGISTER) {
            if(valueDt==DataType.FLOAT && target.datatype!=DataType.FLOAT) {
                // have to typecast the float number on the fly down to an integer
                assignExpressionToRegister(value, RegisterOrPair.FAC1, target.datatype in SignedDatatypes)
                assignTypeCastedFloatFAC1("P8ZP_SCRATCH_W1", target.datatype)
                assignVariableToRegister("P8ZP_SCRATCH_W1", target.register!!, target.datatype in SignedDatatypes, origTypeCastExpression.definingISub(), target.position)
                return
            } else {
                if(!(valueDt isAssignableTo targetDt)) {
                    return if(valueDt in WordDatatypes && targetDt in ByteDatatypes) {
                        // word to byte, just take the lsb
                        assignCastViaLsbFunc(value, target)
                    } else if(valueDt in WordDatatypes && targetDt in WordDatatypes) {
                        // word to word, just assign
                        assignExpressionToRegister(value, target.register!!, targetDt==DataType.BYTE || targetDt==DataType.WORD)
                    } else if(valueDt in ByteDatatypes && targetDt in ByteDatatypes) {
                        // byte to byte, just assign
                        assignExpressionToRegister(value, target.register!!, targetDt==DataType.BYTE || targetDt==DataType.WORD)
                    } else if(valueDt in ByteDatatypes && targetDt in WordDatatypes) {
                        // byte to word, just assign
                        assignExpressionToRegister(value, target.register!!, targetDt==DataType.WORD)
                    } else
                        throw AssemblyError("can't cast $valueDt to $targetDt, this should have been checked in the astchecker")
                }
            }
        }

        if(targetDt in IntegerDatatypes && valueDt in IntegerDatatypes && valueDt!=targetDt && valueDt.isAssignableTo(targetDt)) {
            require(targetDt in WordDatatypes && valueDt in ByteDatatypes) {
                "should be byte to word assignment ${origTypeCastExpression.position}"
            }
            when(target.kind) {
//                TargetStorageKind.VARIABLE -> {
//                    This has been handled already earlier on line 961.
//                    // byte to word, just assign to registers first, then assign to variable
//                    assignExpressionToRegister(value, RegisterOrPair.AY, targetDt==DataType.WORD)
//                    assignTypeCastedRegisters(target.asmVarname, targetDt, RegisterOrPair.AY, targetDt)
//                    return
//                }
                TargetStorageKind.ARRAY -> {
                    // byte to word, just assign to registers first, then assign into array
                    assignExpressionToRegister(value, RegisterOrPair.AY, targetDt==DataType.WORD)
                    assignRegisterpairWord(target, RegisterOrPair.AY)
                    return
                }
                TargetStorageKind.REGISTER -> {
                    // byte to word, just assign to registers
                    assignExpressionToRegister(value, target.register!!, targetDt==DataType.WORD)
                    return
                }
                else -> throw AssemblyError("weird target")
            }
        }

        if(targetDt==DataType.FLOAT && (target.register==RegisterOrPair.FAC1 || target.register==RegisterOrPair.FAC2)) {
            if(target.register==RegisterOrPair.FAC2)
                asmgen.pushFAC1()
            when(valueDt) {
                DataType.UBYTE -> {
                    assignExpressionToRegister(value, RegisterOrPair.Y, false)
                    asmgen.out("  jsr  floats.FREADUY")
                }
                DataType.BYTE -> {
                    assignExpressionToRegister(value, RegisterOrPair.A, true)
                    asmgen.out("  jsr  floats.FREADSA")
                }
                DataType.UWORD -> {
                    assignExpressionToRegister(value, RegisterOrPair.AY, false)
                    asmgen.out("  jsr  floats.GIVUAYFAY")
                }
                DataType.WORD -> {
                    assignExpressionToRegister(value, RegisterOrPair.AY, true)
                    asmgen.out("  jsr  floats.GIVAYFAY")
                }
                else -> throw AssemblyError("invalid dt")
            }
            if(target.register==RegisterOrPair.FAC2) {
                asmgen.out("  jsr  floats.MOVEF")
                asmgen.popFAC1()
            }
            return
        }

        // No more special optimized cases yet. Do the rest via more complex evaluation
        // note: cannot use assignTypeCastedValue because that is ourselves :P
        // NOTE: THIS MAY TURN INTO A STACK OVERFLOW ERROR IF IT CAN'T SIMPLIFY THE TYPECAST..... :-/
        asmgen.assignExpressionTo(origTypeCastExpression, target)
    }

    private fun assignCastViaLsbFunc(value: PtExpression, target: AsmAssignTarget) {
        val lsb = PtBuiltinFunctionCall("lsb", false, true, DataType.UBYTE, value.position)
        lsb.parent = value.parent
        lsb.add(value)
        val src = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, DataType.UBYTE, expression = lsb)
        val assign = AsmAssignment(src, target, program.memsizer, value.position)
        translateNormalAssignment(assign, value.definingISub())
    }

    private fun assignTypeCastedFloatFAC1(targetAsmVarName: String, targetDt: DataType) {

        if(targetDt==DataType.FLOAT)
            throw AssemblyError("typecast to identical type")

        when(targetDt) {
            DataType.UBYTE -> asmgen.out("  jsr  floats.cast_FAC1_as_uw_into_ya |  sty  $targetAsmVarName")
            DataType.BYTE -> asmgen.out("  jsr  floats.cast_FAC1_as_w_into_ay |  sta  $targetAsmVarName")
            DataType.UWORD -> asmgen.out("  jsr  floats.cast_FAC1_as_uw_into_ya |  sty  $targetAsmVarName |  sta  $targetAsmVarName+1")
            DataType.WORD -> asmgen.out("  jsr  floats.cast_FAC1_as_w_into_ay |  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
            else -> throw AssemblyError("weird type")
        }
    }


    private fun assignTypeCastedIdentifier(targetAsmVarName: String, targetDt: DataType,
                                           sourceAsmVarName: String, sourceDt: DataType) {
        if(sourceDt == targetDt)
            throw AssemblyError("typecast to identical type")

        // also see: PtExpressionAsmGen,   fun translateExpression(typecast: PtTypeCast)
        when(sourceDt) {
            DataType.UBYTE -> {
                when(targetDt) {
                    DataType.BYTE -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                    }
                    DataType.UWORD, DataType.WORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    DataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<$targetAsmVarName
                            ldy  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            ldy  $sourceAsmVarName
                            jsr  floats.cast_from_ub""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.BYTE -> {
                when(targetDt) {
                    DataType.UBYTE -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                    }
                    DataType.UWORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    DataType.WORD -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                        asmgen.signExtendVariableLsb(targetAsmVarName, DataType.BYTE)
                    }
                    DataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<$targetAsmVarName
                            ldy  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            lda  $sourceAsmVarName
                            jsr  floats.cast_from_b""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when(targetDt) {
                    DataType.BYTE, DataType.UBYTE -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                    }
                    DataType.WORD -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  $sourceAsmVarName+1 |  sta  $targetAsmVarName+1")
                    }
                    DataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<$targetAsmVarName
                            ldy  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            lda  $sourceAsmVarName
                            ldy  $sourceAsmVarName+1
                            jsr  floats.cast_from_uw""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.WORD -> {
                when(targetDt) {
                    DataType.BYTE, DataType.UBYTE -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                    }
                    DataType.UWORD -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  $sourceAsmVarName+1 |  sta  $targetAsmVarName+1")
                    }
                    DataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<$targetAsmVarName
                            ldy  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            lda  $sourceAsmVarName
                            ldy  $sourceAsmVarName+1
                            jsr  floats.cast_from_w""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.FLOAT -> {
                asmgen.out("  lda  #<$sourceAsmVarName |  ldy  #>$sourceAsmVarName")
                when(targetDt) {
                    DataType.UBYTE -> asmgen.out("  jsr  floats.cast_as_uw_into_ya |  sty  $targetAsmVarName")
                    DataType.BYTE -> asmgen.out("  jsr  floats.cast_as_w_into_ay |  sta  $targetAsmVarName")
                    DataType.UWORD -> asmgen.out("  jsr  floats.cast_as_uw_into_ya |  sty  $targetAsmVarName |  sta  $targetAsmVarName+1")
                    DataType.WORD -> asmgen.out("  jsr  floats.cast_as_w_into_ay |  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.STR -> throw AssemblyError("cannot typecast a string value")
            else -> throw AssemblyError("weird type")
        }
    }


    private fun assignTypeCastedRegisters(targetAsmVarName: String, targetDt: DataType,
                                          regs: RegisterOrPair, sourceDt: DataType) {
        if(sourceDt == targetDt)
            throw AssemblyError("typecast to identical type")

        // also see: PtExpressionAsmGen,   fun translateExpression(typecast: PtTypeCast)
        when(sourceDt) {
            DataType.UBYTE -> {
                when(targetDt) {
                    DataType.BYTE -> {
                        asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName")
                    }
                    DataType.UWORD, DataType.WORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out(
                                "  st${regs.toString().lowercase()}  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out(
                                "  st${regs.toString().lowercase()}  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    DataType.FLOAT -> {
                        when(regs) {
                            RegisterOrPair.A -> asmgen.out("  tay")
                            RegisterOrPair.X -> asmgen.out("  txa |  tay")
                            RegisterOrPair.Y -> {}
                            else -> throw AssemblyError("non-byte regs")
                        }
                        asmgen.out("""
                            lda  #<$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            lda  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2+1
                            jsr  floats.cast_from_ub""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.BYTE -> {
                when(targetDt) {
                    DataType.UBYTE -> {
                        asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName")
                    }
                    DataType.UWORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out(
                                "  st${
                                    regs.toString().lowercase()
                                }  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out(
                                "  st${
                                    regs.toString().lowercase()
                                }  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    DataType.WORD -> {
                        when(regs) {
                            RegisterOrPair.A -> {}
                            RegisterOrPair.X -> asmgen.out("  txa")
                            RegisterOrPair.Y -> asmgen.out("  tya")
                            else -> throw AssemblyError("non-byte regs")
                        }
                        asmgen.signExtendAYlsb(sourceDt)
                        asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                    }
                    DataType.FLOAT -> {
                        when(regs) {
                            RegisterOrPair.A -> {}
                            RegisterOrPair.X -> asmgen.out("  txa")
                            RegisterOrPair.Y -> asmgen.out("  tya")
                            else -> throw AssemblyError("non-byte regs")
                        }
                        asmgen.out("""
                            ldy  #<$targetAsmVarName
                            sty  P8ZP_SCRATCH_W2
                            ldy  #>$targetAsmVarName
                            sty  P8ZP_SCRATCH_W2+1
                            jsr  floats.cast_from_b""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when(targetDt) {
                    DataType.BYTE, DataType.UBYTE -> {
                        asmgen.out("  st${regs.toString().lowercase().first()}  $targetAsmVarName")
                    }
                    DataType.WORD -> {
                        when(regs) {
                            RegisterOrPair.AX -> asmgen.out("  sta  $targetAsmVarName |  stx  $targetAsmVarName+1")
                            RegisterOrPair.AY -> asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            RegisterOrPair.XY -> asmgen.out("  stx  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            else -> throw AssemblyError("non-word regs")
                        }
                    }
                    DataType.FLOAT -> {
                        if(regs!=RegisterOrPair.AY)
                            throw AssemblyError("only supports AY here")
                        asmgen.out("""
                            tax
                            lda  #<$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            lda  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2+1
                            txa
                            jsr  floats.cast_from_uw""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.WORD -> {
                when(targetDt) {
                    DataType.BYTE, DataType.UBYTE -> {
                        asmgen.out("  st${regs.toString().lowercase().first()}  $targetAsmVarName")
                    }
                    DataType.UWORD -> {
                        when(regs) {
                            RegisterOrPair.AX -> asmgen.out("  sta  $targetAsmVarName |  stx  $targetAsmVarName+1")
                            RegisterOrPair.AY -> asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            RegisterOrPair.XY -> asmgen.out("  stx  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            else -> throw AssemblyError("non-word regs")
                        }
                    }
                    DataType.FLOAT -> {
                        if(regs!=RegisterOrPair.AY)
                            throw AssemblyError("only supports AY here")
                        asmgen.out("""
                            tax
                            lda  #<$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            lda  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2+1
                            txa
                            jsr  floats.cast_from_w""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.STR -> throw AssemblyError("cannot typecast a string value")
            else -> throw AssemblyError("weird type")
        }
    }

    private fun assignAddressOf(target: AsmAssignTarget, sourceName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                        lda  #<$sourceName
                        ldy  #>$sourceName
                        sta  ${target.asmVarname}
                        sty  ${target.asmVarname}+1
                    """)
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("can't store word into memory byte")
            }
            TargetStorageKind.ARRAY -> {
                asmgen.out("  lda  #<$sourceName |  ldy #>$sourceName")
                assignRegisterpairWord(target, RegisterOrPair.AY)
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #>$sourceName |  lda  #<$sourceName")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #>$sourceName |  lda  #<$sourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #>$sourceName |  ldx  #<$sourceName")
                    in Cx16VirtualRegisters -> {
                        asmgen.out(
                            """
                            lda  #<$sourceName
                            sta  cx16.${target.register.toString().lowercase()}
                            lda  #>$sourceName
                            sta  cx16.${target.register.toString().lowercase()}+1
                        """)
                    }
                    else -> throw AssemblyError("can't load address in a single 8-bit register")
                }
            }
        }
    }

    private fun assignVariableString(target: AsmAssignTarget, sourceName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when(target.datatype) {
                    DataType.UWORD -> {
                        asmgen.out("""
                            lda  #<$sourceName
                            ldy  #>$sourceName
                            sta  ${target.asmVarname}
                            sty  ${target.asmVarname}+1
                        """)
                    }
                    DataType.STR, DataType.ARRAY_UB, DataType.ARRAY_B -> {
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            ldy  #>${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  #<$sourceName
                            ldy  #>$sourceName
                            jsr  prog8_lib.strcpy""")
                    }
                    else -> throw AssemblyError("assign string to incompatible variable type")
                }
            }
            else -> throw AssemblyError("string-assign to weird target")
        }
    }

    private fun assignVariableWord(target: AsmAssignTarget, sourceName: String, sourceDt: DataType) {
        require(sourceDt in WordDatatypes || sourceDt==DataType.UBYTE)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                if(sourceDt==DataType.UBYTE) {
                    asmgen.out("  lda  $sourceName |  sta  ${target.asmVarname}")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  ${target.asmVarname}")
                    else
                        asmgen.out("  lda  #0 |  sta  ${target.asmVarname}")
                }
                else
                    asmgen.out("""
                        lda  $sourceName
                        ldy  $sourceName+1
                        sta  ${target.asmVarname}
                        sty  ${target.asmVarname}+1""")
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("assign word to memory ${target.memory} should have gotten a typecast")
            }
            TargetStorageKind.ARRAY -> {
                if(sourceDt==DataType.UBYTE) TODO("assign byte to word array")
                target.array!!
                if(target.constArrayIndexValue!=null) {
                    val scaledIdx = target.constArrayIndexValue!! * program.memsizer.memorySize(target.datatype).toUInt()
                    when(target.datatype) {
                        in ByteDatatypes -> {
                            asmgen.out(" lda  $sourceName  | sta  ${target.asmVarname}+$scaledIdx")
                        }
                        in WordDatatypes -> {
                            if(target.array.splitWords)
                                asmgen.out("""
                                    lda  $sourceName
                                    sta  ${target.asmVarname}_lsb+${target.constArrayIndexValue}
                                    lda  $sourceName+1
                                    sta  ${target.asmVarname}_msb+${target.constArrayIndexValue}""")
                            else
                                asmgen.out("""
                                    lda  $sourceName
                                    sta  ${target.asmVarname}+$scaledIdx
                                    lda  $sourceName+1
                                    sta  ${target.asmVarname}+$scaledIdx+1""")
                        }
                        DataType.FLOAT -> {
                            asmgen.out("""
                                lda  #<$sourceName
                                ldy  #>$sourceName
                                sta  P8ZP_SCRATCH_W1
                                sty  P8ZP_SCRATCH_W1+1
                                lda  #<(${target.asmVarname}+$scaledIdx)
                                ldy  #>(${target.asmVarname}+$scaledIdx)
                                jsr  floats.copy_float""")
                        }
                        else -> throw AssemblyError("weird target variable type ${target.datatype}")
                    }
                }
                else
                {
                    when(target.datatype) {
                        DataType.UBYTE, DataType.BYTE -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                            asmgen.out(" lda  $sourceName |  sta  ${target.asmVarname},y")
                        }
                        DataType.UWORD, DataType.WORD -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                            if(target.array.splitWords)
                                asmgen.out("""
                                    lda  $sourceName
                                    sta  ${target.asmVarname}_lsb,y
                                    lda  $sourceName+1
                                    sta  ${target.asmVarname}_msb,y""")
                            else
                                asmgen.out("""
                                    lda  $sourceName
                                    sta  ${target.asmVarname},y
                                    lda  $sourceName+1
                                    sta  ${target.asmVarname}+1,y""")
                        }
                        DataType.FLOAT -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.A)
                            asmgen.out("""
                                ldy  #<$sourceName
                                sty  P8ZP_SCRATCH_W1
                                ldy  #>$sourceName
                                sty  P8ZP_SCRATCH_W1+1
                                ldy  #>${target.asmVarname}
                                clc
                                adc  #<${target.asmVarname}
                                bcc  +
                                iny
+                               jsr  floats.copy_float""")
                        }
                        else -> throw AssemblyError("weird dt")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                if(sourceDt==DataType.UBYTE) {
                    when(target.register!!) {
                        RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  $sourceName")
                        RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  $sourceName")
                        RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldx  $sourceName")
                        in Cx16VirtualRegisters -> {
                            asmgen.out("  lda  $sourceName |  sta  cx16.${target.register.toString().lowercase()}")
                            if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                asmgen.out("  stz  cx16.${target.register.toString().lowercase()}+1")
                            else
                                asmgen.out("  lda  #0 |  sta  cx16.${target.register.toString().lowercase()}+1")
                        }
                        else -> throw AssemblyError("can't load word in a single 8-bit register")
                    }
                } else {
                    when(target.register!!) {
                        RegisterOrPair.AX -> asmgen.out("  ldx  $sourceName+1 |  lda  $sourceName")
                        RegisterOrPair.AY -> asmgen.out("  ldy  $sourceName+1 |  lda  $sourceName")
                        RegisterOrPair.XY -> asmgen.out("  ldy  $sourceName+1 |  ldx  $sourceName")
                        in Cx16VirtualRegisters -> {
                            asmgen.out(
                                """
                                lda  $sourceName
                                sta  cx16.${target.register.toString().lowercase()}
                                lda  $sourceName+1
                                sta  cx16.${target.register.toString().lowercase()}+1
                            """)
                        }
                        else -> throw AssemblyError("can't load word in a single 8-bit register")
                    }
                }
            }
        }
    }

    internal fun assignFAC2float(target: AsmAssignTarget) {
        asmgen.out("  jsr  floats.MOVFA")
        if(target.register != RegisterOrPair.FAC1)
            assignFAC1float(target)
    }

    internal fun assignFAC1float(target: AsmAssignTarget) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    ldx  #<${target.asmVarname}
                    ldy  #>${target.asmVarname}
                    jsr  floats.MOVMF
                """)
            }
            TargetStorageKind.ARRAY -> {
                asmgen.out("""
                    lda  #<${target.asmVarname} 
                    ldy  #>${target.asmVarname}
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1""")
                val constIndex = target.array!!.index.asConstInteger()
                if(constIndex!=null) {
                    asmgen.out(" lda  #$constIndex")
                } else {
                    val asmvarname = asmgen.asmVariableName(target.array.index as PtIdentifier)
                    asmgen.out(" lda  $asmvarname")
                }
                asmgen.out("  jsr  floats.set_array_float_from_fac1")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to mem byte")
            TargetStorageKind.REGISTER -> {
                if(target.register==RegisterOrPair.FAC2)
                    asmgen.out("  jsr  floats.MOVAF")
                else if (target.register!! != RegisterOrPair.FAC1)
                    throw AssemblyError("can't assign Fac1 float to another register")
            }
        }
    }

    private fun assignFloatFromAY(target: AsmAssignTarget) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    sta  P8ZP_SCRATCH_W1                    
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<${target.asmVarname} 
                    ldy  #>${target.asmVarname}
                    jsr  floats.copy_float""")
            }
            TargetStorageKind.ARRAY -> {
                asmgen.out("""
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<${target.asmVarname} 
                    ldy  #>${target.asmVarname}
                    sta  P8ZP_SCRATCH_W2
                    sty  P8ZP_SCRATCH_W2+1""")
                val constIndex = target.array!!.index.asConstInteger()
                if(constIndex!=null) {
                    asmgen.out(" lda  #$constIndex")
                } else {
                    val asmvarname = asmgen.asmVariableName(target.array.index as PtIdentifier)
                    asmgen.out(" lda  $asmvarname")
                }
                asmgen.out(" jsr  floats.set_array_float")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to mem byte")
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.FAC1 -> asmgen.out("  jsr  floats.MOVFM")
                    RegisterOrPair.FAC2 -> asmgen.out("  jsr  floats.CONUPK")
                    else -> throw AssemblyError("can only assign float to Fac1 or 2")
                }
            }
        }
    }

    private fun assignVariableFloat(target: AsmAssignTarget, sourceName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  #<$sourceName
                    ldy  #>$sourceName
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<${target.asmVarname}
                    ldy  #>${target.asmVarname}
                    jsr  floats.copy_float""")
            }
            TargetStorageKind.ARRAY -> {
                asmgen.out("""
                    lda  #<$sourceName
                    ldy  #>$sourceName
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<${target.asmVarname} 
                    ldy  #>${target.asmVarname}
                    sta  P8ZP_SCRATCH_W2
                    sty  P8ZP_SCRATCH_W2+1""")
                val constIndex = target.array!!.index.asConstInteger()
                if(constIndex!=null) {
                    asmgen.out(" lda  #$constIndex")
                } else {
                    val asmvarname = asmgen.asmVariableName(target.array.index as PtIdentifier)
                    asmgen.out(" lda  $asmvarname")
                }
                asmgen.out(" jsr  floats.set_array_float")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to mem byte")
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.FAC1 -> asmgen.out("  lda  #<$sourceName  | ldy  #>$sourceName |  jsr  floats.MOVFM")
                    RegisterOrPair.FAC2 -> asmgen.out("  lda  #<$sourceName  | ldy  #>$sourceName |  jsr  floats.CONUPK")
                    else -> throw AssemblyError("can only assign float to Fac1 or 2")
                }
            }
        }
    }

    private fun assignVariableByte(target: AsmAssignTarget, sourceName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${target.asmVarname}
                    """)
            }
            TargetStorageKind.MEMORY -> {
                asmgen.out("  lda  $sourceName")
                storeRegisterAInMemoryAddress(target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if(assignsIndexedPointerVar(target)) {
                    if (target.constArrayIndexValue==0u) {
                        asmgen.out("  lda  $sourceName")
                        asmgen.storeAIntoPointerVar(target.origAstTarget!!.array!!.variable)
                    } else {
                        asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UBYTE, CpuRegister.Y)
                        if (asmgen.isZpVar(target.origAstTarget!!.array!!.variable)) {
                            asmgen.out("  lda  $sourceName |  sta  (${target.asmVarname}),y")
                        } else {
                            asmgen.out("  lda  ${target.asmVarname} |  sta  P8ZP_SCRATCH_W2 |  lda  ${target.asmVarname}+1 |  sta  P8ZP_SCRATCH_W2+1")
                            asmgen.out("  lda  $sourceName |  sta  (P8ZP_SCRATCH_W2),y")
                        }
                    }
                    return
                }
                if(target.array!!.splitWords)
                    TODO("assign into split words ${target.position}")
                if (target.constArrayIndexValue!=null) {
                    val scaledIdx = target.constArrayIndexValue!! * program.memsizer.memorySize(target.datatype).toUInt()
                    asmgen.out(" lda  $sourceName  | sta  ${target.asmVarname}+$scaledIdx")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                    asmgen.out(" lda  $sourceName |  sta  ${target.asmVarname},y")
                }
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.A -> asmgen.out("  lda  $sourceName")
                    RegisterOrPair.X -> asmgen.out("  ldx  $sourceName")
                    RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName")
                    RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  $sourceName")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  $sourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldx  $sourceName")
                    RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
                    in Cx16VirtualRegisters -> {
                        asmgen.out(
                            """
                            lda  $sourceName
                            sta  cx16.${target.register.toString().lowercase()}
                            lda  #0
                            sta  cx16.${target.register.toString().lowercase()}+1
                            """)
                    }
                    else -> throw AssemblyError("weird register")
                }
            }
        }
    }

    private fun assignVariableByteIntoWord(wordtarget: AsmAssignTarget, bytevar: PtIdentifier) {
        val sourceName = asmgen.asmVariableName(bytevar)
        when (wordtarget.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${wordtarget.asmVarname}
                    ora  #$7f
                    bmi  +
                    lda  #0
+                   sta  ${wordtarget.asmVarname}+1
                    """)
            }
            TargetStorageKind.ARRAY -> {
                if(wordtarget.array!!.splitWords)
                    TODO("assign byte into split words ${wordtarget.position}")
                if (wordtarget.constArrayIndexValue!=null) {
                    val scaledIdx = wordtarget.constArrayIndexValue!! * 2u
                    asmgen.out("  lda  $sourceName")
                    asmgen.signExtendAYlsb(DataType.BYTE)
                    asmgen.out("  sta  ${wordtarget.asmVarname}+$scaledIdx |  sty  ${wordtarget.asmVarname}+$scaledIdx+1")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array, wordtarget.datatype, CpuRegister.X)
                    asmgen.out("  lda  $sourceName")
                    asmgen.signExtendAYlsb(DataType.BYTE)
                    asmgen.out("  sta  ${wordtarget.asmVarname},x |  inx |  tya |  sta  ${wordtarget.asmVarname},x")
                }
            }
            TargetStorageKind.REGISTER -> {
                when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("""
                        lda  $sourceName
                        pha
                        ora  #$7f
                        bmi  +
                        lda  #0
+                       tax
                        pla""")
                    RegisterOrPair.AY -> asmgen.out("""
                        lda  $sourceName
                        tax
                        ora  #$7f
                        bmi  +
                        lda  #0
+                       tay
                        txa""")
                    RegisterOrPair.XY -> asmgen.out("""
                        lda  $sourceName
                        tax 
                        ora  #$7f
                        bmi  +
                        lda  #0
+                       tay""")
                    in Cx16VirtualRegisters -> {
                        val regname = wordtarget.register.name.lowercase()
                        asmgen.out("""
                            lda  $sourceName
                            sta  cx16.$regname
                            ora  #$7f
                            bmi  +
                            lda  #0
+                           sta  cx16.$regname+1""")
                    }
                    else -> throw AssemblyError("only reg pairs allowed as word target ${wordtarget.register}")
                }
            }
            else -> throw AssemblyError("target type isn't word")
        }
    }

    private fun assignVariableUByteIntoWord(wordtarget: AsmAssignTarget, bytevar: PtIdentifier) {
        val sourceName = asmgen.asmVariableName(bytevar)
        when(wordtarget.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  lda  $sourceName |  sta  ${wordtarget.asmVarname}")
                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                    asmgen.out("  stz  ${wordtarget.asmVarname}+1")
                else
                    asmgen.out("  lda  #0 |  sta  ${wordtarget.asmVarname}+1")
            }
            TargetStorageKind.ARRAY -> {
                if(wordtarget.array!!.splitWords) {
                    if (wordtarget.constArrayIndexValue!=null) {
                        val scaledIdx = wordtarget.constArrayIndexValue!!
                        asmgen.out("  lda  $sourceName  | sta  ${wordtarget.asmVarname}_lsb+$scaledIdx")
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  ${wordtarget.asmVarname}_msb+$scaledIdx")
                        else
                            asmgen.out("  lda  #0  | sta  ${wordtarget.asmVarname}_msb+$scaledIdx")
                    }
                    else {
                        asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array, wordtarget.datatype, CpuRegister.Y)
                        asmgen.out("""
                            lda  $sourceName
                            sta  ${wordtarget.asmVarname}_lsb,y
                            lda  #0
                            sta  ${wordtarget.asmVarname}_msb,y""")
                    }
                } else {
                    if (wordtarget.constArrayIndexValue!=null) {
                        val scaledIdx = wordtarget.constArrayIndexValue!! * 2u
                        asmgen.out("  lda  $sourceName  | sta  ${wordtarget.asmVarname}+$scaledIdx")
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  ${wordtarget.asmVarname}+$scaledIdx+1")
                        else
                            asmgen.out("  lda  #0  | sta  ${wordtarget.asmVarname}+$scaledIdx+1")
                    }
                    else {
                        asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array, wordtarget.datatype, CpuRegister.Y)
                        asmgen.out("""
                            lda  $sourceName
                            sta  ${wordtarget.asmVarname},y
                            iny
                            lda  #0
                            sta  ${wordtarget.asmVarname},y""")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  $sourceName")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  $sourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldx  $sourceName")
                    in Cx16VirtualRegisters -> {
                        val regname = wordtarget.register.name.lowercase()
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  lda  $sourceName |  sta  cx16.$regname |  stz  cx16.$regname+1")
                        else
                            asmgen.out("  lda  $sourceName |  sta  cx16.$regname |  lda  #0 |  sta  cx16.$regname+1")
                    }
                    else -> throw AssemblyError("only reg pairs allowed as word target")
                }
            }
            else -> throw AssemblyError("target type isn't word")
        }
    }

    internal fun assignRegisterByte(target: AsmAssignTarget, register: CpuRegister, signed: Boolean) {
        val assignAsWord = target.datatype in WordDatatypes

        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  st${register.name.lowercase()}  ${target.asmVarname}")
                if(assignAsWord) {
                    if(target.datatype in SignedDatatypes) {
                        if(register!=CpuRegister.A)
                            asmgen.out("  t${register.name.lowercase()}a")
                        asmgen.signExtendAYlsb(if(target.datatype in SignedDatatypes) DataType.BYTE else DataType.UBYTE)
                        asmgen.out("  sty  ${target.asmVarname}+1")
                    } else {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  ${target.asmVarname}+1")
                        else
                            asmgen.out("  lda  #0 |  sta  ${target.asmVarname}+1")
                    }
                }
            }
            TargetStorageKind.MEMORY -> {
                when(register) {
                    CpuRegister.A -> {}
                    CpuRegister.X -> asmgen.out(" txa")
                    CpuRegister.Y -> asmgen.out(" tya")
                }
                storeRegisterAInMemoryAddress(target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if(assignAsWord)
                    TODO("assign register as word into Array not yet supported")
                if(target.array!!.splitWords)
                    TODO("assign register into split words ${target.position}")
                if(assignsIndexedPointerVar(target)) {
                    if (target.constArrayIndexValue!=null) {
                        when (register) {
                            CpuRegister.A -> {}
                            CpuRegister.X -> asmgen.out(" txa")
                            CpuRegister.Y -> asmgen.out(" tya")
                        }
                        if(asmgen.isZpVar(target.origAstTarget!!.array!!.variable)) {
                            asmgen.out("  ldy  #${target.constArrayIndexValue} |  sta  (${target.asmVarname}),y")
                        } else {
                            asmgen.out("""
                                ldy  ${target.asmVarname}
                                sty  P8ZP_SCRATCH_W1
                                ldy  ${target.asmVarname}+1
                                sty  P8ZP_SCRATCH_W1+1
                                ldy  #${target.constArrayIndexValue}
                                sta  (P8ZP_SCRATCH_W1),y""")
                        }
                    }
                    else {
                        when (register) {
                            CpuRegister.A -> {}
                            CpuRegister.X -> asmgen.out(" txa")
                            CpuRegister.Y -> asmgen.out(" tya")
                        }
                        val indexVar = target.array.index as PtIdentifier
                        if(asmgen.isZpVar(target.origAstTarget!!.array!!.variable)) {
                            asmgen.out("  ldy  ${asmgen.asmVariableName(indexVar)} |  sta  (${target.asmVarname}),y")
                        } else {
                            asmgen.out("""
                                ldy  ${target.asmVarname}
                                sty  P8ZP_SCRATCH_W1
                                ldy  ${target.asmVarname}+1
                                sty  P8ZP_SCRATCH_W1+1
                                ldy  ${asmgen.asmVariableName(indexVar)}
                                sta  (P8ZP_SCRATCH_W1),y""")
                        }
                    }
                    return
                } else {
                    // assign regular array indexing
                    if (target.constArrayIndexValue!=null) {
                        when (register) {
                            CpuRegister.A -> {}
                            CpuRegister.X -> asmgen.out(" txa")
                            CpuRegister.Y -> asmgen.out(" tya")
                        }
                        asmgen.out("  sta  ${target.asmVarname}+${target.constArrayIndexValue}")
                    }
                    else {
                        when (register) {
                            CpuRegister.A -> {}
                            CpuRegister.X -> asmgen.out(" txa")
                            CpuRegister.Y -> asmgen.out(" tya")
                        }
                        val indexVar = target.array.index as PtIdentifier
                        asmgen.out("  ldy  ${asmgen.asmVariableName(indexVar)} |  sta  ${target.asmVarname},y")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(register) {
                    CpuRegister.A -> when(target.register!!) {
                        RegisterOrPair.A -> {}
                        RegisterOrPair.X -> { asmgen.out("  tax") }
                        RegisterOrPair.Y -> { asmgen.out("  tay") }
                        RegisterOrPair.AY -> {
                            if(signed)
                                asmgen.out("""
                ldy  #0
                cmp  #${'$'}80
                bcc  +
                dey
+""")
                            else
                                asmgen.out("  ldy  #0")
                        }
                        RegisterOrPair.AX -> {
                            if(signed)
                                asmgen.out("""
                ldx  #0
                cmp  #${'$'}80
                bcc  +
                dex
+""")
                            else
                                asmgen.out("  ldx  #0")
                        }
                        RegisterOrPair.XY -> {
                            if(signed)
                                asmgen.out("""
                tax
                ldy  #0
                cpx  #${'$'}80
                bcc  +
                dey
+""")
                            else
                                asmgen.out("  tax |  ldy  #0")
                        }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected type cast to float")
                        in Cx16VirtualRegisters -> {
                            // only assign a single byte to the virtual register's Lsb
                            asmgen.out("  sta  cx16.${target.register.toString().lowercase()}")
                        }
                        else -> throw AssemblyError("weird register")
                    }
                    CpuRegister.X -> when(target.register!!) {
                        RegisterOrPair.A -> { asmgen.out("  txa") }
                        RegisterOrPair.X -> {  }
                        RegisterOrPair.Y -> { asmgen.out("  txy") }
                        RegisterOrPair.AY -> {
                            if(signed)
                                asmgen.out("""
                txa
                ldy  #0
                cmp  #${'$'}80
                bcc  +
                dey
+""")
                            else
                                asmgen.out("  txa |  ldy  #0")
                        }
                        RegisterOrPair.AX -> {
                            if(signed)
                                asmgen.out("""
                txa
                ldx  #0
                cmp  #${'$'}80
                bcc  +
                dex
+""")
                            else
                                asmgen.out("  txa |  ldx  #0")
                        }
                        RegisterOrPair.XY -> {
                            if(signed)
                                asmgen.out("""
                ldy  #0
                cpx  #${'$'}80
                bcc  +
                dey
+""")
                            else
                                asmgen.out("  ldy  #0")
                        }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected type cast to float")
                        in Cx16VirtualRegisters -> {
                            // only assign a single byte to the virtual register's Lsb
                            asmgen.out("  stx  cx16.${target.register.toString().lowercase()}")
                        }
                        else -> throw AssemblyError("weird register")
                    }
                    CpuRegister.Y -> when(target.register!!) {
                        RegisterOrPair.A -> { asmgen.out("  tya") }
                        RegisterOrPair.X -> { asmgen.out("  tyx") }
                        RegisterOrPair.Y -> { }
                        RegisterOrPair.AY -> {
                            if(signed)
                                asmgen.out("""
                tya
                ldy  #0
                cmp  #${'$'}80
                bcc  +
                dey
+""")
                            else
                                asmgen.out("  tya |  ldy  #0")
                        }
                        RegisterOrPair.AX -> {
                            if(signed)
                                asmgen.out("""
                tya
                ldx  #0
                cmp  #${'$'}80
                bcc  +
                dex
+""")
                            else
                                asmgen.out("  tya |  ldx  #0")
                        }
                        RegisterOrPair.XY -> {
                            if(signed)
                                asmgen.out("""
                tya
                tax
                ldy  #0
                cpx  #${'$'}80
                bcc  +
                dey
+""")
                            else
                                asmgen.out("  tya |  tax |  ldy  #0")
                        }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected type cast to float")
                        in Cx16VirtualRegisters -> {
                            // only assign a single byte to the virtual register's Lsb
                            asmgen.out("  sty  cx16.${target.register.toString().lowercase()}")
                        }
                        else -> throw AssemblyError("weird register")
                    }
                }
            }
        }
    }

    internal fun assignRegisterpairWord(target: AsmAssignTarget, regs: RegisterOrPair) {
        require(target.datatype in NumericDatatypes || target.datatype in PassByReferenceDatatypes) {
            "assign target must be word type ${target.position}"
        }
        if(target.datatype==DataType.FLOAT)
            throw AssemblyError("float value should be from FAC1 not from registerpair memory pointer")

        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when(regs) {
                    RegisterOrPair.AX -> asmgen.out("  sta  ${target.asmVarname} |  stx  ${target.asmVarname}+1")
                    RegisterOrPair.AY -> asmgen.out("  sta  ${target.asmVarname} |  sty  ${target.asmVarname}+1")
                    RegisterOrPair.XY -> asmgen.out("  stx  ${target.asmVarname} |  sty  ${target.asmVarname}+1")
                    in Cx16VirtualRegisters -> {
                        val srcReg = asmgen.asmSymbolName(regs)
                        asmgen.out("""
                            lda  $srcReg
                            sta  ${target.asmVarname}
                            lda  $srcReg+1
                            sta  ${target.asmVarname}+1""")
                    }
                    else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                }
            }
            TargetStorageKind.ARRAY -> {
                if(target.array!!.splitWords) {
                    // assign to split lsb/msb word array
                    if (target.constArrayIndexValue!=null) {
                        val idx = target.constArrayIndexValue!!
                        when (regs) {
                            RegisterOrPair.AX -> asmgen.out("  sta  ${target.asmVarname}_lsb+$idx |  stx  ${target.asmVarname}_msb+$idx")
                            RegisterOrPair.AY -> asmgen.out("  sta  ${target.asmVarname}_lsb+$idx |  sty  ${target.asmVarname}_msb+$idx")
                            RegisterOrPair.XY -> asmgen.out("  stx  ${target.asmVarname}_lsb+$idx |  sty  ${target.asmVarname}_msb+$idx")
                            in Cx16VirtualRegisters -> {
                                val srcReg = asmgen.asmSymbolName(regs)
                                asmgen.out("""
                                    lda  $srcReg
                                    sta  ${target.asmVarname}_lsb+$idx
                                    lda  $srcReg+1
                                    sta  ${target.asmVarname}_msb+$idx""")
                            }
                            else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                        }
                    }
                    else {
                        if (regs !in Cx16VirtualRegisters) {
                            when (regs) {
                                RegisterOrPair.AX -> asmgen.out("  pha |  txa |  pha")
                                RegisterOrPair.AY -> asmgen.out("  pha |  tya |  pha")
                                RegisterOrPair.XY -> asmgen.out("  txa |  pha |  tya |  pha")
                                else -> throw AssemblyError("expected reg pair")
                            }
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, DataType.UWORD, CpuRegister.Y)
                            asmgen.out("""
                                pla
                                sta  ${target.asmVarname}_msb,y
                                pla
                                sta  ${target.asmVarname}_lsb,y""")
                        } else {
                            val srcReg = asmgen.asmSymbolName(regs)
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, DataType.UWORD, CpuRegister.Y)
                            asmgen.out("""
                                lda  $srcReg
                                sta  ${target.asmVarname}_lsb,y
                                lda  $srcReg+1
                                sta  ${target.asmVarname}_msb,y""")
                        }
                    }
                } else {
                    // assign to normal word array
                    if (target.constArrayIndexValue!=null) {
                        val idx = target.constArrayIndexValue!! * 2u
                        when (regs) {
                            RegisterOrPair.AX -> asmgen.out("  sta  ${target.asmVarname}+$idx |  stx  ${target.asmVarname}+$idx+1")
                            RegisterOrPair.AY -> asmgen.out("  sta  ${target.asmVarname}+$idx |  sty  ${target.asmVarname}+$idx+1")
                            RegisterOrPair.XY -> asmgen.out("  stx  ${target.asmVarname}+$idx |  sty  ${target.asmVarname}+$idx+1")
                            in Cx16VirtualRegisters -> {
                                val srcReg = asmgen.asmSymbolName(regs)
                                asmgen.out("""
                                    lda  $srcReg
                                    sta  ${target.asmVarname}+$idx
                                    lda  $srcReg+1
                                    sta  ${target.asmVarname}+$idx+1""")
                            }
                            else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                        }
                    }
                    else {
                        if (regs !in Cx16VirtualRegisters) {
                            when (regs) {
                                RegisterOrPair.AX -> asmgen.out("  pha |  txa |  pha")
                                RegisterOrPair.AY -> asmgen.out("  pha |  tya |  pha")
                                RegisterOrPair.XY -> asmgen.out("  txa |  pha |  tya |  pha")
                                else -> throw AssemblyError("expected reg pair")
                            }
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, DataType.UWORD, CpuRegister.Y)
                            asmgen.out("""
                                iny
                                pla
                                sta  ${target.asmVarname},y
                                dey
                                pla
                                sta  ${target.asmVarname},y""")
                        } else {
                            val srcReg = asmgen.asmSymbolName(regs)
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, DataType.UWORD, CpuRegister.Y)
                            asmgen.out("""
                                iny
                                lda  $srcReg+1
                                sta  ${target.asmVarname},y
                                dey
                                lda  $srcReg
                                sta  ${target.asmVarname},y""")
                        }
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(regs) {
                    RegisterOrPair.AX -> when(target.register!!) {
                        RegisterOrPair.AY -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.AX -> { }
                        RegisterOrPair.XY -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG |  tax") }
                        in Cx16VirtualRegisters -> {
                            asmgen.out(
                                """
                                    sta  cx16.${target.register.toString().lowercase()}
                                    stx  cx16.${target.register.toString().lowercase()}+1
                                """)
                        }
                        else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                    }
                    RegisterOrPair.AY -> when(target.register!!) {
                        RegisterOrPair.AY -> { }
                        RegisterOrPair.AX -> { asmgen.out("  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.XY -> { asmgen.out("  tax") }
                        in Cx16VirtualRegisters -> {
                            asmgen.out(
                                """
                                    sta  cx16.${target.register.toString().lowercase()}
                                    sty  cx16.${target.register.toString().lowercase()}+1
                                """)
                        }
                        else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                    }
                    RegisterOrPair.XY -> when(target.register!!) {
                        RegisterOrPair.AY -> { asmgen.out("  txa") }
                        RegisterOrPair.AX -> { asmgen.out("  txa |  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.XY -> { }
                        in Cx16VirtualRegisters -> {
                            asmgen.out(
                                """
                                    stx  cx16.${target.register.toString().lowercase()}
                                    sty  cx16.${target.register.toString().lowercase()}+1
                                """)
                        }
                        else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                    }
                    in Cx16VirtualRegisters -> {
                        val srcReg = asmgen.asmSymbolName(regs)
                        if(regs!=target.register) {
                            when(target.register) {
                                RegisterOrPair.AX -> asmgen.out("  lda  $srcReg |  ldx  $srcReg+1")
                                RegisterOrPair.AY -> asmgen.out("  lda  $srcReg |  ldy  $srcReg+1")
                                RegisterOrPair.XY -> asmgen.out("  ldx  $srcReg |  ldy  $srcReg+1")
                                in Cx16VirtualRegisters -> {
                                    val targetReg = asmgen.asmSymbolName(target.register!!)
                                    asmgen.out("  lda  $srcReg |  sta  $targetReg |  lda  $srcReg+1 |  sta  $targetReg+1")
                                }
                                else -> throw AssemblyError("invalid reg")
                            }
                        }
                    }
                    else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                }
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't store word into memory byte")
        }
    }

    private fun assignConstantWord(target: AsmAssignTarget, word: Int) {
        if(word==0 && asmgen.isTargetCpu(CpuType.CPU65c02)) {
            // optimize setting zero value for this processor
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("  stz  ${target.asmVarname} |  stz  ${target.asmVarname}+1")
                }
                TargetStorageKind.MEMORY -> {
                    throw AssemblyError("memory is bytes not words")
                }
                TargetStorageKind.ARRAY -> {
                    asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UWORD, CpuRegister.Y)
                    if(target.array.splitWords)
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmVarname}_lsb,y
                            sta  ${target.asmVarname}_msb,y""")
                    else
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmVarname},y
                            sta  ${target.asmVarname}+1,y""")
                }
                TargetStorageKind.REGISTER -> {
                    when(target.register!!) {
                        RegisterOrPair.AX -> asmgen.out("  lda  #0 |  tax")
                        RegisterOrPair.AY -> asmgen.out("  lda  #0 |  tay")
                        RegisterOrPair.XY -> asmgen.out("  ldx  #0 |  ldy  #0")
                        in Cx16VirtualRegisters -> {
                            asmgen.out(
                                "  stz  cx16.${
                                    target.register.toString().lowercase()
                                } |  stz  cx16.${target.register.toString().lowercase()}+1")
                        }
                        else -> throw AssemblyError("invalid register for word value")
                    }
                }
            }

            return
        }


        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                if (word ushr 8 == word and 255) {
                    // lsb=msb
                    asmgen.out("""
                    lda  #${(word and 255).toHex()}
                    sta  ${target.asmVarname}
                    sta  ${target.asmVarname}+1
                """)
                } else {
                    asmgen.out("""
                    lda  #<${word.toHex()}
                    ldy  #>${word.toHex()}
                    sta  ${target.asmVarname}
                    sty  ${target.asmVarname}+1
                """)
                }
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("assign word to memory ${target.memory} should have gotten a typecast")
            }
            TargetStorageKind.ARRAY -> {
                asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UWORD, CpuRegister.Y)
                if(target.array.splitWords)
                    asmgen.out("""
                        lda  #<${word.toHex()}
                        sta  ${target.asmVarname}_lsb,y
                        lda  #>${word.toHex()}
                        sta  ${target.asmVarname}_msb,y""")
                else
                    asmgen.out("""
                        lda  #<${word.toHex()}
                        sta  ${target.asmVarname},y
                        lda  #>${word.toHex()}
                        sta  ${target.asmVarname}+1,y""")
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #>${word.toHex()} |  lda  #<${word.toHex()}")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #>${word.toHex()} |  lda  #<${word.toHex()}")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #>${word.toHex()} |  ldx  #<${word.toHex()}")
                    in Cx16VirtualRegisters -> {
                        asmgen.out(
                            """
                            lda  #<${word.toHex()}
                            sta  cx16.${target.register.toString().lowercase()}
                            lda  #>${word.toHex()}
                            sta  cx16.${target.register.toString().lowercase()}+1
                            """)
                    }
                    else -> throw AssemblyError("invalid register for word value")
                }
            }
        }
    }

    private fun assignConstantByte(target: AsmAssignTarget, byte: Int) {
        if(byte==0 && asmgen.isTargetCpu(CpuType.CPU65c02)) {
            // optimize setting zero value for this cpu
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("  stz  ${target.asmVarname} ")
                }
                TargetStorageKind.MEMORY -> {
                    asmgen.out("  lda  #0")
                    storeRegisterAInMemoryAddress(target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    if(assignsIndexedPointerVar(target)) {
                        if (target.constArrayIndexValue==0u) {
                            asmgen.out("  lda  #0")
                            asmgen.storeAIntoPointerVar(target.origAstTarget!!.array!!.variable)
                        } else {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UBYTE, CpuRegister.Y)
                            if (asmgen.isZpVar(target.origAstTarget!!.array!!.variable)) {
                                asmgen.out("  lda  #0 |  sta  (${target.asmVarname}),y")
                            } else {
                                asmgen.out("  lda  ${target.asmVarname} |  sta  P8ZP_SCRATCH_W2 |  lda  ${target.asmVarname}+1 |  sta  P8ZP_SCRATCH_W2+1")
                                asmgen.out("  lda  #0 |  sta  (P8ZP_SCRATCH_W2),y")
                            }
                        }
                        return
                    }
                    if(target.array!!.splitWords)
                        TODO("assign into split words ${target.position}")
                    if (target.constArrayIndexValue!=null) {
                        val indexValue = target.constArrayIndexValue!!
                        asmgen.out("  stz  ${target.asmVarname}+$indexValue")
                    }
                    else {
                        asmgen.loadScaledArrayIndexIntoRegister(target.array, DataType.UBYTE, CpuRegister.Y)
                        asmgen.out("  lda  #0 |  sta  ${target.asmVarname},y")
                    }
                }
                TargetStorageKind.REGISTER -> when(target.register!!) {
                    RegisterOrPair.A -> asmgen.out("  lda  #0")
                    RegisterOrPair.X -> asmgen.out("  ldx  #0")
                    RegisterOrPair.Y -> asmgen.out("  ldy  #0")
                    RegisterOrPair.AX -> asmgen.out("  lda  #0 |  tax")
                    RegisterOrPair.AY -> asmgen.out("  lda  #0 |  tay")
                    RegisterOrPair.XY -> asmgen.out("  ldx  #0 |  ldy  #0")
                    RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
                    in Cx16VirtualRegisters -> {
                        asmgen.out("  stz  cx16.${target.register.toString().lowercase()} |  stz  cx16.${target.register.toString().lowercase()}+1")
                    }
                    else -> throw AssemblyError("weird register")
                }
            }

            return
        }


        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname} ")
            }
            TargetStorageKind.MEMORY -> {
                asmgen.out("  lda  #${byte.toHex()}")
                storeRegisterAInMemoryAddress(target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if(assignsIndexedPointerVar(target)) {
                    if (target.constArrayIndexValue==0u) {
                        asmgen.out("  lda  #${byte.toHex()}")
                        asmgen.storeAIntoPointerVar(target.origAstTarget!!.array!!.variable)
                    } else {
                        asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UBYTE, CpuRegister.Y)
                        if (asmgen.isZpVar(target.origAstTarget!!.array!!.variable)) {
                            asmgen.out("  lda  #${byte.toHex()} |  sta  (${target.asmVarname}),y")
                        } else {
                            asmgen.out("  lda  ${target.asmVarname} |  sta  P8ZP_SCRATCH_W2 |  lda  ${target.asmVarname}+1 |  sta  P8ZP_SCRATCH_W2+1")
                            asmgen.out("  lda  #${byte.toHex()} |  sta  (P8ZP_SCRATCH_W2),y")
                        }
                    }
                    return
                }
                if(target.array!!.splitWords)
                    TODO("assign into split words ${target.position}")
                if (target.constArrayIndexValue!=null) {
                    val indexValue = target.constArrayIndexValue!!
                    asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname}+$indexValue")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(target.array, DataType.UBYTE, CpuRegister.Y)
                    asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname},y")
                }
            }
            TargetStorageKind.REGISTER -> when(target.register!!) {
                RegisterOrPair.A -> asmgen.out("  lda  #${byte.toHex()}")
                RegisterOrPair.X -> asmgen.out("  ldx  #${byte.toHex()}")
                RegisterOrPair.Y -> asmgen.out("  ldy  #${byte.toHex()}")
                RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  #${byte.toHex()}")
                RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  #${byte.toHex()}")
                RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldx  #${byte.toHex()}")
                RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
                in Cx16VirtualRegisters -> {
                    asmgen.out(
                        "  lda  #${byte.toHex()} |  sta  cx16.${
                            target.register.toString().lowercase()
                        }")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  cx16.${target.register.toString().lowercase()}+1\n")
                    else
                        asmgen.out(
                            "  lda  #0 |  sta  cx16.${
                                target.register.toString().lowercase()
                            }+1\n")
                }
                else -> throw AssemblyError("weird register")
            }
        }
    }

    private fun assignsIndexedPointerVar(target: AsmAssignTarget): Boolean =
        target.origAstTarget?.array?.variable?.type==DataType.UWORD

    internal fun assignConstantFloat(target: AsmAssignTarget, float: Double) {
        if (float == 0.0) {
            // optimized case for float zero
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("""
                            stz  ${target.asmVarname}
                            stz  ${target.asmVarname}+1
                            stz  ${target.asmVarname}+2
                            stz  ${target.asmVarname}+3
                            stz  ${target.asmVarname}+4
                        """)
                    else
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmVarname}
                            sta  ${target.asmVarname}+1
                            sta  ${target.asmVarname}+2
                            sta  ${target.asmVarname}+3
                            sta  ${target.asmVarname}+4
                        """)
                }
                TargetStorageKind.ARRAY -> {
                    val constIndex = target.array!!.index.asConstInteger()
                    if (constIndex!=null) {
                        val indexValue = constIndex * program.memsizer.memorySize(DataType.FLOAT)
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("""
                                stz  ${target.asmVarname}+$indexValue
                                stz  ${target.asmVarname}+$indexValue+1
                                stz  ${target.asmVarname}+$indexValue+2
                                stz  ${target.asmVarname}+$indexValue+3
                                stz  ${target.asmVarname}+$indexValue+4
                            """)
                        else
                            asmgen.out("""
                                lda  #0
                                sta  ${target.asmVarname}+$indexValue
                                sta  ${target.asmVarname}+$indexValue+1
                                sta  ${target.asmVarname}+$indexValue+2
                                sta  ${target.asmVarname}+$indexValue+3
                                sta  ${target.asmVarname}+$indexValue+4
                            """)
                    } else {
                        val asmvarname = asmgen.asmVariableName(target.array.index as PtIdentifier)
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1
                            lda  #>${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1+1
                            lda  $asmvarname  
                            jsr  floats.set_0_array_float
                        """)
                    }
                }
                TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to memory byte")
                TargetStorageKind.REGISTER -> {
                    val floatConst = allocator.getFloatAsmConst(float)
                    when(target.register!!) {
                        RegisterOrPair.FAC1 -> asmgen.out("  lda  #<$floatConst  | ldy  #>$floatConst |  jsr  floats.MOVFM")
                        RegisterOrPair.FAC2 -> asmgen.out("  lda  #<$floatConst  | ldy  #>$floatConst |  jsr  floats.CONUPK")
                        else -> throw AssemblyError("can only assign float to Fac1 or 2")
                    }
                }
            }
        } else {
            // non-zero value
            val constFloat = allocator.getFloatAsmConst(float)
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                        lda  #<$constFloat
                        ldy  #>$constFloat
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #<${target.asmVarname}
                        ldy  #>${target.asmVarname}
                        jsr  floats.copy_float""")
                }
                TargetStorageKind.ARRAY -> {
                    val arrayVarName = target.asmVarname
                    val constIndex = target.array!!.index.asConstInteger()
                    if (constIndex!=null) {
                        val indexValue = constIndex * program.memsizer.memorySize(DataType.FLOAT)
                        asmgen.out("""
                            lda  #<$constFloat
                            ldy  #>$constFloat
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  #<($arrayVarName+$indexValue)
                            ldy  #>($arrayVarName+$indexValue)
                            jsr  floats.copy_float""")
                    } else {
                        val asmvarname = asmgen.asmVariableName(target.array.index as PtIdentifier)
                        asmgen.out("""
                            lda  #<${constFloat}
                            sta  P8ZP_SCRATCH_W1
                            lda  #>${constFloat}
                            sta  P8ZP_SCRATCH_W1+1
                            lda  #<${arrayVarName}
                            sta  P8ZP_SCRATCH_W2
                            lda  #>${arrayVarName}
                            sta  P8ZP_SCRATCH_W2+1
                            lda  $asmvarname
                            jsr  floats.set_array_float
                        """)
                    }
                }
                TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to memory byte")
                TargetStorageKind.REGISTER -> {
                    val floatConst = allocator.getFloatAsmConst(float)
                    when(target.register!!) {
                        RegisterOrPair.FAC1 -> asmgen.out("  lda  #<$floatConst  | ldy  #>$floatConst |  jsr  floats.MOVFM")
                        RegisterOrPair.FAC2 -> asmgen.out("  lda  #<$floatConst  | ldy  #>$floatConst |  jsr  floats.CONUPK")
                        else -> throw AssemblyError("can only assign float to Fac1 or 2")
                    }
                }
            }
        }
    }

    private fun assignMemoryByte(target: AsmAssignTarget, address: UInt?, identifier: PtIdentifier?) {
        if (address != null) {
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  ${target.asmVarname}
                        """)
                }
                TargetStorageKind.MEMORY -> {
                    asmgen.out("  lda  ${address.toHex()}")
                    storeRegisterAInMemoryAddress(target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    asmgen.out("  lda  ${address.toHex()}")
                    assignRegisterByte(target, CpuRegister.A, false)
                }
                TargetStorageKind.REGISTER -> when(target.register!!) {
                    RegisterOrPair.A -> asmgen.out("  lda  ${address.toHex()}")
                    RegisterOrPair.X -> asmgen.out("  ldx  ${address.toHex()}")
                    RegisterOrPair.Y -> asmgen.out("  ldy  ${address.toHex()}")
                    RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  ${address.toHex()}")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  ${address.toHex()}")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldy  ${address.toHex()}")
                    RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
                    in Cx16VirtualRegisters -> {
                        asmgen.out(
                            """
                            lda  ${address.toHex()}
                            sta  cx16.${target.register.toString().lowercase()}
                            lda  #0
                            sta  cx16.${target.register.toString().lowercase()}+1
                        """)
                    }
                    else -> throw AssemblyError("weird register")
                }
            }
        } else if (identifier != null) {
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  ${target.asmVarname}")
                }
                TargetStorageKind.MEMORY -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    storeRegisterAInMemoryAddress(target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    assignRegisterByte(target, CpuRegister.A, false)
                }
                TargetStorageKind.REGISTER -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    when(target.register!!) {
                        RegisterOrPair.A -> {}
                        RegisterOrPair.X -> asmgen.out("  tax")
                        RegisterOrPair.Y -> asmgen.out("  tay")
                        RegisterOrPair.AX -> asmgen.out("  ldx  #0")
                        RegisterOrPair.AY -> asmgen.out("  ldy  #0")
                        RegisterOrPair.XY -> asmgen.out("  tax |  ldy  #0")
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
                        in Cx16VirtualRegisters -> {
                            asmgen.out(
                                """
                                sta  cx16.${target.register.toString().lowercase()}
                                lda  #0
                                sta  cx16.${target.register.toString().lowercase()}+1
                            """)
                        }
                        else -> throw AssemblyError("weird register")
                    }
                }
            }
        }
    }

    private fun assignMemoryByteIntoWord(wordtarget: AsmAssignTarget, address: UInt?, identifier: PtIdentifier?) {
        if (address != null) {
            when(wordtarget.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("  lda  ${address.toHex()} |  sta  ${wordtarget.asmVarname}")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  ${wordtarget.asmVarname}+1")
                    else
                        asmgen.out("  lda  #0 |  sta  ${wordtarget.asmVarname}+1")
                }
                TargetStorageKind.ARRAY -> {
                    asmgen.out("  lda  ${address.toHex()} |  ldy  #0")
                    assignRegisterpairWord(wordtarget, RegisterOrPair.AY)
                }
                TargetStorageKind.REGISTER -> when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  ${address.toHex()}")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  ${address.toHex()}")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldy  ${address.toHex()}")
                    in Cx16VirtualRegisters -> {
                        asmgen.out("  lda  ${address.toHex()} |  sta  cx16.${wordtarget.register.toString().lowercase()}")
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  cx16.${wordtarget.register.toString().lowercase()}+1")
                        else
                            asmgen.out("  lda  #0 |  sta  cx16.${wordtarget.register.toString().lowercase()}+1")
                    }
                    else -> throw AssemblyError("word regs can only be pair")
                }
                else -> throw AssemblyError("other types aren't word")
            }
        } else if (identifier != null) {
            when(wordtarget.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  ${wordtarget.asmVarname}")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  ${wordtarget.asmVarname}+1")
                    else
                        asmgen.out("  lda  #0 |  sta  ${wordtarget.asmVarname}+1")
                }
                TargetStorageKind.ARRAY -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out("  ldy  #0")
                    assignRegisterpairWord(wordtarget, RegisterOrPair.AY)
                }
                TargetStorageKind.REGISTER -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    when(wordtarget.register!!) {
                        RegisterOrPair.AX -> asmgen.out("  ldx  #0")
                        RegisterOrPair.AY -> asmgen.out("  ldy  #0")
                        RegisterOrPair.XY -> asmgen.out("  tax |  ldy  #0")
                        in Cx16VirtualRegisters -> {
                            asmgen.out("  sta  cx16.${wordtarget.register.toString().lowercase()}")
                            if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                asmgen.out("  stz  cx16.${wordtarget.register.toString().lowercase()}+1")
                            else
                                asmgen.out("  lda  #0 |  sta  cx16.${wordtarget.register.toString().lowercase()}+1")
                        }
                        else -> throw AssemblyError("word regs can only be pair")
                    }
                }
                else -> throw AssemblyError("other types aren't word")
            }
        }
    }

    private fun storeRegisterAInMemoryAddress(memoryAddress: PtMemoryByte) {
        val addressExpr = memoryAddress.address
        val addressLv = addressExpr as? PtNumber

        fun storeViaExprEval() {
            when(addressExpr) {
                is PtNumber, is PtIdentifier -> {
                    assignExpressionToVariable(addressExpr, "P8ZP_SCRATCH_W2", DataType.UWORD)
                    asmgen.storeAIntoZpPointerVar("P8ZP_SCRATCH_W2")
                }
                else -> {
                    // same as above but we need to save the A register
                    asmgen.out("  pha")
                    assignExpressionToVariable(addressExpr, "P8ZP_SCRATCH_W2", DataType.UWORD)
                    asmgen.out("  pla")
                    asmgen.storeAIntoZpPointerVar("P8ZP_SCRATCH_W2")
                }
            }
        }

        when {
            addressLv != null -> {
                asmgen.out("  sta  ${addressLv.number.toHex()}")
            }
            addressExpr is PtIdentifier -> {
                asmgen.storeAIntoPointerVar(addressExpr)
            }
            addressExpr is PtBinaryExpression -> {
                if(!asmgen.tryOptimizedPointerAccessWithA(addressExpr, addressExpr.operator, true))
                    storeViaExprEval()
            }
            else -> storeViaExprEval()
        }
    }

    internal fun assignExpressionToRegister(expr: PtExpression, register: RegisterOrPair, signed: Boolean) {
        val src = AsmAssignSource.fromAstSource(expr, program, asmgen)
        val tgt = AsmAssignTarget.fromRegisters(register, signed, expr.position, null, asmgen)
        val assign = AsmAssignment(src, tgt, program.memsizer, expr.position)
        translateNormalAssignment(assign, expr.definingISub())
    }

    internal fun assignExpressionToVariable(expr: PtExpression, asmVarName: String, dt: DataType) {
        if(expr.type==DataType.FLOAT && dt!=DataType.FLOAT) {
            throw AssemblyError("can't directly assign a FLOAT expression to an integer variable $expr")
        } else {
            val src = AsmAssignSource.fromAstSource(expr, program, asmgen)
            val tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, dt, expr.definingISub(), expr.position, variableAsmName = asmVarName)
            val assign = AsmAssignment(src, tgt, program.memsizer, expr.position)
            translateNormalAssignment(assign, expr.definingISub())
        }
    }

    internal fun assignVariableToRegister(asmVarName: String, register: RegisterOrPair, signed: Boolean, scope: IPtSubroutine?, pos: Position) {
        val tgt = AsmAssignTarget.fromRegisters(register, signed, pos, null, asmgen)
        val src = AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, tgt.datatype, variableAsmName = asmVarName)
        val assign = AsmAssignment(src, tgt, program.memsizer, Position.DUMMY)
        translateNormalAssignment(assign, scope)
    }

    internal fun inplaceInvert(assign: AsmAssignment, scope: IPtSubroutine?) {
        val target = assign.target
        when (assign.target.datatype) {
            DataType.UBYTE -> {
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            eor  #255
                            sta  ${target.asmVarname}""")
                    }
                    TargetStorageKind.MEMORY -> {
                        val memory = target.memory!!
                        when (memory.address) {
                            is PtNumber -> {
                                val addr = (memory.address as PtNumber).number.toHex()
                                asmgen.out("""
                                    lda  $addr
                                    eor  #255
                                    sta  $addr""")
                            }
                            is PtIdentifier -> {
                                val sourceName = asmgen.loadByteFromPointerIntoA(memory.address as PtIdentifier)
                                asmgen.out("  eor  #255")
                                asmgen.out("  sta  ($sourceName),y")
                            }
                            else -> {
                                asmgen.assignExpressionToVariable(memory.address, "P8ZP_SCRATCH_W2", DataType.UWORD)
                                asmgen.out("""
                                    ldy  #0
                                    lda  (P8ZP_SCRATCH_W2),y
                                    eor  #255""")
                                asmgen.storeAIntoZpPointerVar("P8ZP_SCRATCH_W2")
                            }
                        }
                    }
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) {
                            RegisterOrPair.A -> asmgen.out("  eor  #255")
                            RegisterOrPair.X -> asmgen.out("  txa |  eor  #255 |  tax")
                            RegisterOrPair.Y -> asmgen.out("  tya |  eor  #255 |  tay")
                            else -> throw AssemblyError("invalid reg dt for byte invert")
                        }
                    }
                    TargetStorageKind.ARRAY -> assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("~", assign), scope)
                }
            }
            DataType.UWORD -> {
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            eor  #255
                            sta  ${target.asmVarname}
                            lda  ${target.asmVarname}+1
                            eor  #255
                            sta  ${target.asmVarname}+1""")
                    }
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) {
                            RegisterOrPair.AX -> asmgen.out("  pha |  txa |  eor  #255 |  tax |  pla |  eor  #255")
                            RegisterOrPair.AY -> asmgen.out("  pha |  tya |  eor  #255 |  tay |  pla |  eor  #255")
                            RegisterOrPair.XY -> asmgen.out("  txa |  eor  #255 |  tax |  tya |  eor  #255 |  tay")
                            in Cx16VirtualRegisters -> throw AssemblyError("cx16 virtual regs should be variables, not real registers")
                            else -> throw AssemblyError("invalid reg dt for word invert")
                        }
                    }
                    TargetStorageKind.ARRAY -> assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("~", assign), scope)
                    else -> throw AssemblyError("weird target")
                }
            }
            else -> throw AssemblyError("invert of invalid type")
        }
    }

    internal fun inplaceNegate(assign: AsmAssignment, ignoreDatatype: Boolean, scope: IPtSubroutine?) {
        val target = assign.target
        val datatype = if(ignoreDatatype) {
            when(target.datatype) {
                DataType.UBYTE, DataType.BYTE -> DataType.BYTE
                DataType.UWORD, DataType.WORD -> DataType.WORD
                else -> target.datatype
            }
        } else target.datatype
        when (datatype) {
            DataType.BYTE -> {
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("""
                                lda  ${target.asmVarname}
                                eor  #255
                                ina
                                sta  ${target.asmVarname}""")
                        else
                            asmgen.out("""
                                lda  #0
                                sec
                                sbc  ${target.asmVarname}
                                sta  ${target.asmVarname}""")
                    }
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) {
                            RegisterOrPair.A -> {
                                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                    asmgen.out("  eor  #255 |  ina")
                                else
                                    asmgen.out("  eor  #255 |  clc |  adc  #1")
                            }
                            RegisterOrPair.X -> asmgen.out("  txa |  eor  #255 |  tax |  inx")
                            RegisterOrPair.Y -> asmgen.out("  tya |  eor  #255 |  tay |  iny")
                            else -> throw AssemblyError("invalid reg dt for byte negate")
                        }
                    }
                    TargetStorageKind.MEMORY -> throw AssemblyError("memory is ubyte, can't negate that")
                    TargetStorageKind.ARRAY -> assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("-", assign), scope)
                }
            }
            DataType.WORD -> {
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  #0
                            sec
                            sbc  ${target.asmVarname}
                            sta  ${target.asmVarname}
                            lda  #0
                            sbc  ${target.asmVarname}+1
                            sta  ${target.asmVarname}+1""")
                    }
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) { //P8ZP_SCRATCH_REG
                            RegisterOrPair.AX -> {
                                asmgen.out("""
                                    sec
                                    eor  #255
                                    adc  #0
                                    pha
                                    txa
                                    eor  #255
                                    adc  #0
                                    tax
                                    pla""")
                            }
                            RegisterOrPair.AY -> {
                                asmgen.out("""
                                    sec
                                    eor  #255
                                    adc  #0
                                    tax
                                    tya
                                    eor  #255
                                    adc  #0
                                    tay
                                    txa""")
                            }
                            RegisterOrPair.XY -> {
                                asmgen.out("""
                                    sec
                                    txa
                                    eor  #255
                                    adc  #0
                                    tax
                                    tya
                                    eor  #255
                                    adc  #0
                                    tay""")
                            }
                            in Cx16VirtualRegisters -> throw AssemblyError("cx16 virtual regs should be variables, not real registers")
                            else -> throw AssemblyError("invalid reg dt for word neg")
                        }
                    }
                    TargetStorageKind.MEMORY -> throw AssemblyError("memory is ubyte, can't negate that")
                    TargetStorageKind.ARRAY -> assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("-", assign), scope)
                }
            }
            DataType.FLOAT -> {
                when (target.kind) {
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) {
                            RegisterOrPair.FAC1 -> asmgen.out("  jsr  floats.NEGOP")
                            RegisterOrPair.FAC2 -> asmgen.out("  jsr  floats.MOVFA |  jsr floats.NEGOP |  jsr  floats.MOVEF")
                            else -> throw AssemblyError("invalid float register")
                        }
                    }
                    TargetStorageKind.VARIABLE -> {
                        // simply flip the sign bit in the float
                        asmgen.out("""
                            lda  ${target.asmVarname}+1
                            eor  #$80
                            sta  ${target.asmVarname}+1
                        """)
                    }
                    TargetStorageKind.ARRAY -> assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("-", assign), scope)
                    else -> throw AssemblyError("weird target for in-place float negation")
                }
            }
            else -> throw AssemblyError("negate of invalid type")
        }
    }

    private fun makePrefixedExprFromArrayExprAssign(operator: String, assign: AsmAssignment): AsmAssignment {
        val prefix = PtPrefix(operator, assign.source.datatype, assign.source.array!!.position)
        prefix.add(assign.source.array)
        prefix.parent = assign.target.origAstTarget ?: program
        val prefixSrc = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, assign.source.datatype, expression=prefix)
        return AsmAssignment(prefixSrc, assign.target, assign.memsizer, assign.position)
    }
}
