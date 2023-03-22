package prog8.codegen.cpu6502.assignment

import prog8.code.StStaticVariable
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal
import prog8.codegen.cpu6502.ExpressionsAsmGen
import prog8.codegen.cpu6502.VariableAllocator
import prog8.codegen.cpu6502.returnsWhatWhere
import java.util.*


internal class AssignmentAsmGen(private val program: PtProgram,
                                private val symbolTable: SymbolTable,
                                private val asmgen: AsmGen6502Internal,
                                private val allocator: VariableAllocator) {
    private val augmentableAsmGen = AugmentableAssignmentAsmGen(program, this, asmgen, allocator)

    fun translate(assignment: PtAssignment) {
        val target = AsmAssignTarget.fromAstAssignment(assignment.target, assignment.definingISub(), asmgen)
        val source = AsmAssignSource.fromAstSource(assignment.value, program, asmgen).adjustSignedUnsigned(target)
        val assign = AsmAssignment(source, target, program.memsizer, assignment.position)
        translateNormalAssignment(assign, assignment.definingISub())
    }

    fun translate(augmentedAssign: PtAugmentedAssign) {
        val target = AsmAssignTarget.fromAstAssignment(augmentedAssign.target, augmentedAssign.definingISub(), asmgen)
        val source = AsmAssignSource.fromAstSource(augmentedAssign.value, program, asmgen).adjustSignedUnsigned(target)
        val assign = AsmAugmentedAssignment(source, augmentedAssign.operator, target, program.memsizer, augmentedAssign.position)
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
                    DataType.WORD -> assignVariableWord(assign.target, variable)
                    DataType.UWORD -> {
                        if(assign.source.datatype in PassByReferenceDatatypes)
                            assignAddressOf(assign.target, variable)
                        else
                            assignVariableWord(assign.target, variable)
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

                if(value.variable.type==DataType.UWORD) {
                    // indexing a pointer var instead of a real array or string
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
                    assignRegisterByte(assign.target, CpuRegister.A)
                    return
                }

                val constIndex = value.index.asConstInteger()
                if (constIndex!=null) {
                    // constant array index value
                    val indexValue = constIndex * program.memsizer.memorySize(elementDt)
                    when (elementDt) {
                        in ByteDatatypes -> {
                            asmgen.out("  lda  $arrayVarName+$indexValue")
                            assignRegisterByte(assign.target, CpuRegister.A)
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
                            assignRegisterByte(assign.target, CpuRegister.A)
                        }
                        in WordDatatypes  -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y |  pha |  lda  $arrayVarName+1,y |  tay |  pla")
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
                    assignRegisterByte(assign.target, CpuRegister.A)
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
                    is PtRpn -> {
                        val addrExpr = value.address as PtRpn
                        if(asmgen.tryOptimizedPointerAccessWithA(addrExpr, addrExpr.finalOperator().operator, false)) {
                            assignRegisterByte(assign.target, CpuRegister.A)
                        } else {
                            assignViaExprEval(value.address)
                        }
                    }
                    is PtBinaryExpression -> {
                        val addrExpr = value.address as PtBinaryExpression
                        if(asmgen.tryOptimizedPointerAccessWithA(addrExpr, addrExpr.operator, false)) {
                            assignRegisterByte(assign.target, CpuRegister.A)
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
            SourceStorageKind.STACK -> {
                if(assign.target.kind!=TargetStorageKind.STACK || assign.target.datatype != assign.source.datatype)
                    assignStackValue(assign.target)
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
                asmgen.saveXbeforeCall(value)
                asmgen.translateFunctionCall(value)
                val returnValue = sub.returnsWhatWhere().singleOrNull { it.first.registerOrPair!=null } ?: sub.returnsWhatWhere().single { it.first.statusflag!=null }
                when (returnValue.second) {
                    DataType.STR -> {
                        asmgen.restoreXafterCall(value)
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
                        asmgen.restoreXafterCall(value)
                        assignFAC1float(assign.target)
                    }
                    else -> {
                        // do NOT restore X register before assigning the result values first
                        when (returnValue.first.registerOrPair) {
                            RegisterOrPair.A -> assignRegisterByte(assign.target, CpuRegister.A)
                            RegisterOrPair.X -> assignRegisterByte(assign.target, CpuRegister.X)
                            RegisterOrPair.Y -> assignRegisterByte(assign.target, CpuRegister.Y)
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
                        // we've processed the result value in the X register by now, so it's now finally safe to restore it
                        asmgen.restoreXafterCall(value)
                    }
                }
            }
            is PtBuiltinFunctionCall -> {
                val returnDt = asmgen.translateBuiltinFunctionCallExpression(value, false, assign.target.register)
                if(assign.target.register==null) {
                    // still need to assign the result to the target variable/etc.
                    when(returnDt) {
                        in ByteDatatypes -> assignRegisterByte(assign.target, CpuRegister.A)            // function's byte result is in A
                        in WordDatatypes -> assignRegisterpairWord(assign.target, RegisterOrPair.AY)    // function's word result is in AY
                        DataType.STR -> {
                            when (assign.target.datatype) {
                                DataType.STR -> {
                                    asmgen.out("""
                                                        pha
                                                        lda  #<${assign.target.asmVarname}
                                                        sta  P8ZP_SCRATCH_W1
                                                        lda  #>${assign.target.asmVarname}
                                                        sta  P8ZP_SCRATCH_W1+1
                                                        pla
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
                } else {
                    assignPrefixedExpressionToArrayElt(assign, scope)
                }
            }
            is PtContainmentCheck -> {
                containmentCheckIntoA(value)
                assignRegisterByte(assign.target, CpuRegister.A)
            }
            is PtBinaryExpression -> {
                require(!program.binaryExpressionsAreRPN)
                if(!attemptAssignOptimizedBinexpr(value, assign)) {
                    // All remaining binary expressions just evaluate via the stack for now.
                    // (we can't use the assignment helper functions (assignExpressionTo...) to do it via registers here,
                    // because the code here is the implementation of exactly that...)
                    fallbackToStackEval(assign)
                }
            }
            is PtRpn -> {
                if(!attemptAssignOptimizedExprRPN(assign, scope!!)) {
                    // All remaining binary expressions just evaluate via the stack for now.
                    // TODO: For RPN expressions this should never occur anymore and the eval stack should be removed when we achieve this
                    // (we can't use the assignment helper functions (assignExpressionTo...) to do it via registers here,
                    // because the code here is the implementation of exactly that...)
                    fallbackToStackEval(assign)
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
                in WordDatatypes -> assignVariableWord(assign.target, tempvar)
                DataType.FLOAT -> assignVariableFloat(assign.target, tempvar)
                else -> throw AssemblyError("weird dt")
            }
        }
    }

    private fun assignVirtualRegister(target: AsmAssignTarget, register: RegisterOrPair) {
        when(target.datatype) {
            in ByteDatatypes -> {
                asmgen.out("  lda  cx16.${register.toString().lowercase()}L")
                assignRegisterByte(target, CpuRegister.A)
            }
            in WordDatatypes -> assignRegisterpairWord(target, register)
            else -> throw AssemblyError("expected byte or word")
        }
    }

    private fun attemptAssignOptimizedExprRPN(assign: AsmAssignment, scope: IPtSubroutine): Boolean {
        val value = assign.source.expression as PtRpn
        val (left, oper, right) = value.finalOperation()
        if(oper.type != value.type)
            throw AssemblyError("rpn node type error, expected ${value.type} got ${oper.type}")

        // TODO RPN optimizations for simple cases
//        if(oper.operator in ComparisonOperators) {
//            assignRPNComparison(assign, value)
//            return true
//        }
//
//        if(right is PtExpression) {
//            if (simpleEqualityExprRPN(value, oper, right, assign.target))
//                return true
//            if (simpleLogicalExprRPN(value, oper, right, assign.target))
//                return true
//            if (simplePlusOrMinusExprRPN(value, oper, right, assign.target))
//                return true
//            if (simpleBitshiftExprRPN(value, oper, right, assign.target))
//                return true
//        }

        val asmExtra = asmgen.subroutineExtra(scope)
        val evalVars = mutableMapOf (
            DataType.UBYTE to Stack<String>(),
            DataType.UWORD to Stack<String>(),
            DataType.FLOAT to Stack<String>()
        )

        fun getVarDt(dt: DataType) =
            when(dt) {
                in ByteDatatypes -> DataType.UBYTE
                in WordDatatypes, in PassByReferenceDatatypes -> DataType.UWORD
                else -> dt
            }

        fun evalVarName(dt: DataType, depth: Int): String {
            val name: String
            val varDt: DataType
            when(dt) {
                in ByteDatatypes -> {
                    name = "p8_rpn_eval_byte_$depth"
                    varDt = DataType.UBYTE
                }
                in WordDatatypes, in PassByReferenceDatatypes -> {
                    name = "p8_rpn_eval_word_$depth"
                    varDt = DataType.UWORD
                }
                DataType.FLOAT -> {
                    name = "p8_rpn_eval_float_$depth"
                    varDt = DataType.FLOAT
                }
                else -> throw AssemblyError("weird dt")
            }
            evalVars.getValue(varDt).push(name)
            if(!asmExtra.extraVars.any { it.second==name }) {
                val stScope = symbolTable.lookup((scope as PtNamedNode).scopedName)!!
                val dummyNode = PtVariable(name, varDt, ZeropageWish.DONTCARE, null, null, Position.DUMMY)
                dummyNode.parent = scope
                stScope.add(StStaticVariable(name, varDt, null, null, null, null, ZeropageWish.DONTCARE, dummyNode))
                asmExtra.extraVars.add(Triple(varDt, name, null))
            }
            return name
        }

        val startDepth = asmExtra.rpnDepth
        value.children.forEach {
            when (it) {
                is PtRpnOperator -> {
                    val rightvar = evalVars.getValue(getVarDt(it.rightType)).pop()
                    val leftvar = evalVars.getValue(getVarDt(it.leftType)).pop()
                    asmExtra.rpnDepth -= 2
                    val resultVarname = evalVarName(it.type, asmExtra.rpnDepth)
                    asmExtra.rpnDepth++
                    symbolTable.resetCachedFlat()

                    val scopeName = (scope as PtNamedNode).scopedName
                    val leftVarPt = PtIdentifier("$scopeName.$leftvar", it.leftType, it.position)
                    leftVarPt.parent = scope
                    if(it.rightType largerThan it.type) {
                        if(it.operator !in ComparisonOperators)
                            throw AssemblyError("only expected a boolean comparison, got ${it.operator}")
                        if(it.rightType !in WordDatatypes || it.type !in ByteDatatypes) {
                            when (it.rightType) {
                                DataType.STR -> {
                                    val rightString = PtIdentifier("$scopeName.$rightvar", DataType.STR, it.position)
                                    rightString.parent = scope
                                    asmgen.assignExpressionToVariable(leftVarPt, "prog8_lib.strcmp_expression._arg_s1", DataType.UWORD)
                                    asmgen.assignExpressionToVariable(rightString, "prog8_lib.strcmp_expression._arg_s2", DataType.UWORD)
                                    asmgen.out(" jsr  prog8_lib.strcmp_expression")    // result  of compare is in A
                                    when(it.operator) {
                                        "==" -> asmgen.out(" and  #1 |  eor  #1")
                                        "!=" -> asmgen.out(" and  #1")
                                        "<=" -> asmgen.out("""
                bpl  +
                lda  #1
                bne  ++
+               lda  #0
+""")
                                        ">=" -> asmgen.out("""
                bmi  +
                lda  #1
                bne  ++
+               lda  #0
+""")
                                        "<" -> asmgen.out("""
                bmi  +
                lda  #0
                beq  ++
+               lda  #1
+""")
                                        ">" -> asmgen.out("""
                bpl  +
                lda  #0
                beq  ++
+               lda  #1
+""")
                                    }
                                    asmgen.out("  sta  $resultVarname")
                                }
                                in NumericDatatypes -> {
                                    val jumpIfFalseLabel = asmgen.makeLabel("cmp")
                                    val rightVarPt = PtIdentifier("$scopeName.$rightvar", it.rightType, it.position)
                                    rightVarPt.parent = scope
                                    asmgen.testNonzeroComparisonAndJump(leftVarPt, it.operator, rightVarPt, jumpIfFalseLabel, null, null)
                                    asmgen.out("""
                                        lda  #1
                                        bne  +
$jumpIfFalseLabel                           lda  #0
        +                               sta  $resultVarname""")
                                }
                                else -> throw AssemblyError("weird type for operator: ${it.rightType}")
                            }
                        } else {
                            // use in-place assignment with optional cast to the target variable
                            val src = AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, it.rightType, variableAsmName = rightvar)
                            val target = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, it.leftType, scope, assign.position, variableAsmName = leftvar)
                            val operator = if(it.operator in ComparisonOperators) it.operator else it.operator+'='
                            val augAssign = AsmAugmentedAssignment(src, operator, target, program.memsizer, assign.position)
                            augmentableAsmGen.translate(augAssign, scope)
                            if(resultVarname!=leftvar)
                                assignTypeCastedIdentifier(resultVarname, it.type, leftvar, it.leftType)
                        }
                    } else {
                        // use in-place assignment with optional cast to the target variable
                        val src = AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, it.rightType, variableAsmName = rightvar)
                        val target = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, it.leftType, scope, assign.position, variableAsmName = leftvar)
                        val operator = if(it.operator in ComparisonOperators) it.operator else it.operator+'='
                        val augAssign = AsmAugmentedAssignment(src, operator, target, program.memsizer, assign.position)
                        augmentableAsmGen.translate(augAssign, scope)
                        if(resultVarname!=leftvar)
                            assignTypeCastedIdentifier(resultVarname, it.type, leftvar, it.leftType)
                    }
                }
                is PtExpression -> {
                    val varname = evalVarName(it.type, asmExtra.rpnDepth)
                    assignExpressionToVariable(it, varname, it.type)
                    asmExtra.rpnDepth++
                }
                else -> throw AssemblyError("weird rpn node")
            }
        }
        require(asmExtra.rpnDepth-startDepth == 1) {
            "unbalanced RPN ${value.position}"
        }

        val resultVariable = evalVars.getValue(getVarDt(value.type)).pop()
        asmExtra.rpnDepth--
        if(!(assign.target.datatype equalsSize  value.type)) {
            // we only allow for transparent byte -> word / ubyte -> uword assignments
            // any other type difference is an error
            if(assign.target.datatype in WordDatatypes && value.type in ByteDatatypes) {
                assignVariableToRegister(resultVariable, RegisterOrPair.A, value.type==DataType.BYTE, scope, assign.position)
                asmgen.signExtendAYlsb(value.type)
                assignRegisterpairWord(assign.target, RegisterOrPair.AY)
            } else {
                throw AssemblyError("data type mismatch, missing typecast ${value.type} -> ${assign.target.datatype}")
            }
        } else {
            when (value.type) {
                in ByteDatatypes -> assignVariableByte(assign.target, resultVariable)
                in WordDatatypes -> assignVariableWord(assign.target, resultVariable)
                DataType.FLOAT -> assignVariableFloat(assign.target, resultVariable)
                else -> throw AssemblyError("weird dt")
            }
        }
        require(evalVars.all { it.value.isEmpty() }) { "invalid rpn evaluation" }

        return true
    }

    private fun attemptAssignOptimizedBinexpr(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        if(expr.operator in ComparisonOperators) {
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
        }

        if(expr.type !in IntegerDatatypes)
            return false

        fun simpleLogicalBytesExpr() {
            // both left and right expression operands are simple.
            if (expr.right is PtNumber || expr.right is PtIdentifier)
                assignLogicalWithSimpleRightOperandByte(assign.target, expr.left, expr.operator, expr.right)
            else if (expr.left is PtNumber || expr.left is PtIdentifier)
                assignLogicalWithSimpleRightOperandByte(assign.target, expr.right, expr.operator, expr.left)
            else {
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
                assignRegisterByte(assign.target, CpuRegister.A)
            }
        }

        fun simpleLogicalWordsExpr() {
            // both left and right expression operands are simple.
            if (expr.right is PtNumber || expr.right is PtIdentifier)
                assignLogicalWithSimpleRightOperandWord(assign.target, expr.left, expr.operator, expr.right)
            else if (expr.left is PtNumber || expr.left is PtIdentifier)
                assignLogicalWithSimpleRightOperandWord(assign.target, expr.right, expr.operator, expr.left)
            else {
                assignExpressionToRegister(expr.left, RegisterOrPair.AY, false)
                asmgen.saveRegisterStack(CpuRegister.A, false)
                asmgen.saveRegisterStack(CpuRegister.Y, false)
                assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_W1", DataType.UWORD)
                when (expr.operator) {
                    "&", "and" -> asmgen.out("  pla |  and  P8ZP_SCRATCH_W1+1 |  tay |  pla |  and  P8ZP_SCRATCH_W1")
                    "|", "or" -> asmgen.out("  pla |  ora  P8ZP_SCRATCH_W1+1 |  tay |  pla |  ora  P8ZP_SCRATCH_W1")
                    "^", "xor" -> asmgen.out("  pla |  eor  P8ZP_SCRATCH_W1+1 |  tay |  pla |  eor  P8ZP_SCRATCH_W1")
                    else -> throw AssemblyError("invalid operator")
                }
                assignRegisterpairWord(assign.target, RegisterOrPair.AY)
            }
        }

        if(expr.operator in setOf("&", "|", "^", "and", "or", "xor")) {
            if (expr.left.type in ByteDatatypes && expr.right.type in ByteDatatypes) {
                if (expr.right.isSimple()) {
                    simpleLogicalBytesExpr()
                    return true
                }
            }
            if (expr.left.type in WordDatatypes && expr.right.type in WordDatatypes) {
                if (expr.right.isSimple()) {
                    simpleLogicalWordsExpr()
                    return true
                }
            }
            return false
        }

        if(expr.operator == "==" || expr.operator == "!=") {
            // expression datatype is BOOL (ubyte) but operands can be anything
            if(expr.left.type in ByteDatatypes && expr.right.type in ByteDatatypes &&
                    expr.left.isSimple() && expr.right.isSimple()) {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                asmgen.saveRegisterStack(CpuRegister.A, false)
                assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.restoreRegisterStack(CpuRegister.A, false)
                if(expr.operator=="==") {
                    asmgen.out("""
                        cmp  P8ZP_SCRATCH_B1
                        bne  +
                        lda  #1
                        bne  ++
+                       lda  #0
+""")
                } else {
                    asmgen.out("""
                        cmp  P8ZP_SCRATCH_B1
                        beq  +
                        lda  #1
                        bne  ++
+                       lda  #0
+""")
                }
                assignRegisterByte(assign.target, CpuRegister.A)
                return true
            } else if(expr.left.type in WordDatatypes && expr.right.type in WordDatatypes &&
                    expr.left.isSimple() && expr.right.isSimple()) {
                assignExpressionToRegister(expr.left, RegisterOrPair.AY, false)
                asmgen.saveRegisterStack(CpuRegister.A, false)
                asmgen.saveRegisterStack(CpuRegister.Y, false)
                assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_W1", DataType.UWORD)
                asmgen.restoreRegisterStack(CpuRegister.Y, false)
                asmgen.restoreRegisterStack(CpuRegister.A, false)
                if(expr.operator=="==") {
                    asmgen.out("""
                        cmp  P8ZP_SCRATCH_W1
                        bne  +
                        cpy  P8ZP_SCRATCH_W1+1
                        bne  +
                        lda  #1
                        bne  ++
+                       lda  #0
+""")
                } else {
                    asmgen.out("""
                        cmp  P8ZP_SCRATCH_W1
                        bne  +
                        cpy  P8ZP_SCRATCH_W1+1
                        bne  +
                        lda  #0
                        bne  ++
+                       lda  #1
+""")
                }
                assignRegisterByte(assign.target, CpuRegister.A)
                return true
            }
            return false
        }
        else if(expr.operator=="+" || expr.operator=="-") {
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
                        assignRegisterByte(assign.target, CpuRegister.A)
                        return true
                    }
                    is PtNumber -> {
                        assignExpressionToRegister(left, RegisterOrPair.A, dt==DataType.BYTE)
                        if(expr.operator=="+")
                            asmgen.out("  clc |  adc  #${right.number.toHex()}")
                        else
                            asmgen.out("  sec |  sbc  #${right.number.toHex()}")
                        assignRegisterByte(assign.target, CpuRegister.A)
                        return true
                    }
                    else -> return false
                }
            } else if(dt in WordDatatypes) {
                when (right) {
                    is PtAddressOf -> {
                        assignExpressionToRegister(left, RegisterOrPair.AY, dt==DataType.WORD)
                        val symbol = asmgen.asmVariableName(right.identifier)
                        if(expr.operator=="+")
                            asmgen.out("""
                                clc
                                adc  #<$symbol
                                pha
                                tya
                                adc  #>$symbol
                                tay
                                pla""")
                        else
                            asmgen.out("""
                                sec
                                sbc  #<$symbol
                                pha
                                tya
                                sbc  #>$symbol
                                tay
                                pla""")
                        assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                        return true
                    }
                    is PtIdentifier -> {
                        val symname = asmgen.asmVariableName(right)
                        assignExpressionToRegister(left, RegisterOrPair.AY, dt==DataType.WORD)
                        if(expr.operator=="+")
                            asmgen.out("""
                                clc
                                adc  $symname
                                pha
                                tya
                                adc  $symname+1
                                tay
                                pla""")
                        else
                            asmgen.out("""
                                sec
                                sbc  $symname
                                pha
                                tya
                                sbc  $symname+1
                                tay
                                pla""")
                        assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                        return true
                    }
                    is PtNumber -> {
                        assignExpressionToRegister(left, RegisterOrPair.AY, dt==DataType.WORD)
                        if(expr.operator=="+") {
                            asmgen.out("""
                                clc
                                adc  #<${right.number.toHex()}
                                pha
                                tya
                                adc  #>${right.number.toHex()}
                                tay
                                pla""")
                        } else if(expr.operator=="-") {
                            asmgen.out("""
                                sec
                                sbc  #<${right.number.toHex()}
                                pha
                                tya
                                sbc  #>${right.number.toHex()}
                                tay
                                pla""")
                        }
                        assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                        return true
                    }
                    is PtTypeCast -> {
                        val castedValue = right.value
                        if(right.type in WordDatatypes && castedValue.type in ByteDatatypes) {
                            if(castedValue is PtIdentifier) {
                                val castedSymname = asmgen.asmVariableName(castedValue)
                                assignExpressionToRegister(left, RegisterOrPair.AY, dt==DataType.WORD)
                                if(expr.operator=="+")
                                    asmgen.out("""
                                        clc
                                        adc  $castedSymname
                                        bcc  +
                                        iny
    +""")
                                else
                                    asmgen.out("""
                                        sec
                                        sbc  $castedSymname
                                        bcs  +
                                        dey
    +""")
                                assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                                return true
                            }
                        }
                    }
                    else -> return false
                }
            }
        }
        else if(expr.operator=="<<" || expr.operator==">>") {
            val shifts = expr.right.asConstInteger()
            if(shifts!=null) {
                val dt = expr.left.type
                if(dt in ByteDatatypes && shifts in 0..7) {
                    val signed = dt == DataType.BYTE
                    assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                    if(expr.operator=="<<") {
                        repeat(shifts) {
                            asmgen.out("  asl  a")
                        }
                    } else {
                        if(signed && shifts>0) {
                            asmgen.out("  ldy  #$shifts |  jsr  math.lsr_byte_A")
                        } else {
                            repeat(shifts) {
                                asmgen.out("  lsr  a")
                            }
                        }
                    }
                    assignRegisterByte(assign.target, CpuRegister.A)
                    return true
                } else if(dt in WordDatatypes && shifts in 0..7) {
                    val signed = dt == DataType.WORD
                    assignExpressionToRegister(expr.left, RegisterOrPair.AY, signed)
                    if(expr.operator=="<<") {
                        if(shifts>0) {
                            asmgen.out("  sty  P8ZP_SCRATCH_B1")
                            repeat(shifts) {
                                asmgen.out("  asl  a |  rol  P8ZP_SCRATCH_B1")
                            }
                            asmgen.out("  ldy  P8ZP_SCRATCH_B1")
                        }
                    } else {
                        if(signed) {
                            // TODO("shift AY >> $shifts signed")
                            return false
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
                    assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                    return true
                }
            }
        }
        return false
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
        assignRegisterByte(target, CpuRegister.A)
    }

    private fun assignLogicalWithSimpleRightOperandWord(target: AsmAssignTarget, left: PtExpression, operator: String, right: PtExpression) {
        assignExpressionToRegister(left, RegisterOrPair.AY, false)
        when(right) {
            is PtNumber -> {
                val number = right.number.toHex()
                when (operator) {
                    "&", "and" -> asmgen.out("  and  #<$number |  pha |  tya |  and  #>$number |  tay |  pla")
                    "|", "or" -> asmgen.out("  ora  #<$number |  pha |  tya |  ora  #>$number |  tay |  pla")
                    "^", "xor" -> asmgen.out("  eor  #<$number |  pha |  tya |  eor  #>$number |  tay |  pla")
                    else -> throw AssemblyError("invalid operator")
                }
            }
            is PtIdentifier -> {
                val name = asmgen.asmSymbolName(right)
                when (operator) {
                    "&", "and" -> asmgen.out("  and  $name |  pha |  tya |  and  $name+1 |  tay |  pla")
                    "|", "or" -> asmgen.out("  ora  $name |  pha |  tya |  ora  $name+1 |  tay |  pla")
                    "^", "xor" -> asmgen.out("  eor  $name |  pha |  tya |  eor  $name+1 |  tay |  pla")
                    else -> throw AssemblyError("invalid operator")
                }
            }
            else -> throw AssemblyError("wrong right operand type")
        }
        assignRegisterpairWord(target, RegisterOrPair.AY)
    }

    private fun attemptAssignToByteCompareZeroRPN(expr: PtRpn, assign: AsmAssignment): Boolean {
        val (leftRpn, oper, right) = expr.finalOperation()
        val left = if(expr.children.size!=3 || leftRpn !is PtExpression)
            expr.truncateLastOperator()
        else
            leftRpn
        when (oper.operator) {
            "==" -> {
                when(val dt = left.type) {
                    in ByteDatatypes -> {
                        assignExpressionToRegister(left, RegisterOrPair.A, dt==DataType.BYTE)
                        asmgen.out("""
                            beq  +
                            lda  #0
                            beq  ++
+                           lda  #1
+""")
                        assignRegisterByte(assign.target, CpuRegister.A)
                        return true
                    }
                    in WordDatatypes -> {
                        assignExpressionToRegister(left, RegisterOrPair.AY, dt==DataType.WORD)
                        asmgen.out("""
                            sty  P8ZP_SCRATCH_B1
                            ora  P8ZP_SCRATCH_B1
                            beq  +
                            lda  #0
                            beq  ++
+                           lda  #1
+""")
                        assignRegisterByte(assign.target, CpuRegister.A)
                        return true
                    }
                    DataType.FLOAT -> {
                        assignExpressionToRegister(left, RegisterOrPair.FAC1, true)
                        asmgen.out("  jsr  floats.SIGN |  and  #1 |  eor  #1")
                        assignRegisterByte(assign.target, CpuRegister.A)
                        return true
                    }
                    else->{
                        return false
                    }
                }
            }
            "!=" -> {
                when(val dt = left.type) {
                    in ByteDatatypes -> {
                        assignExpressionToRegister(left, RegisterOrPair.A, dt==DataType.BYTE)
                        asmgen.out("  beq  + |  lda  #1")
                        asmgen.out("+")
                        assignRegisterByte(assign.target, CpuRegister.A)
                        return true
                    }
                    in WordDatatypes -> {
                        assignExpressionToRegister(left, RegisterOrPair.AY, dt==DataType.WORD)
                        asmgen.out("  sty  P8ZP_SCRATCH_B1 |  ora  P8ZP_SCRATCH_B1")
                        asmgen.out("  beq  + |  lda  #1")
                        asmgen.out("+")
                        assignRegisterByte(assign.target, CpuRegister.A)
                        return true
                    }
                    DataType.FLOAT -> {
                        assignExpressionToRegister(left, RegisterOrPair.FAC1, true)
                        asmgen.out("  jsr  floats.SIGN")
                        assignRegisterByte(assign.target, CpuRegister.A)
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

    private fun attemptAssignToByteCompareZero(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        when (expr.operator) {
            "==" -> {
                when(val dt = expr.left.type) {
                    in ByteDatatypes -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.A, dt==DataType.BYTE)
                        asmgen.out("""
                            beq  +
                            lda  #0
                            beq  ++
+                           lda  #1
+""")
                        assignRegisterByte(assign.target, CpuRegister.A)
                        return true
                    }
                    in WordDatatypes -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.AY, dt==DataType.WORD)
                        asmgen.out("""
                            sty  P8ZP_SCRATCH_B1
                            ora  P8ZP_SCRATCH_B1
                            beq  +
                            lda  #0
                            beq  ++
+                           lda  #1
+""")
                        assignRegisterByte(assign.target, CpuRegister.A)
                        return true
                    }
                    DataType.FLOAT -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.FAC1, true)
                        asmgen.out("  jsr  floats.SIGN |  and  #1 |  eor  #1")
                        assignRegisterByte(assign.target, CpuRegister.A)
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
                        assignRegisterByte(assign.target, CpuRegister.A)
                        return true
                    }
                    in WordDatatypes -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.AY, dt==DataType.WORD)
                        asmgen.out("  sty  P8ZP_SCRATCH_B1 |  ora  P8ZP_SCRATCH_B1")
                        asmgen.out("  beq  + |  lda  #1")
                        asmgen.out("+")
                        assignRegisterByte(assign.target, CpuRegister.A)
                        return true
                    }
                    DataType.FLOAT -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.FAC1, true)
                        asmgen.out("  jsr  floats.SIGN")
                        assignRegisterByte(assign.target, CpuRegister.A)
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

    private fun fallbackToStackEval(assign: AsmAssignment) {
        // this routine is called for assigning a binaryexpression value that has no optimized code path.
        asmgen.translateExpression(assign.source.expression!!)
        if (assign.target.datatype in WordDatatypes && assign.source.datatype in ByteDatatypes)
            asmgen.signExtendStackLsb(assign.source.datatype)
        if (assign.target.kind != TargetStorageKind.STACK || assign.target.datatype != assign.source.datatype)
            assignStackValue(assign.target)
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
                asmgen.saveRegisterLocal(CpuRegister.A, containment.definingISub()!!)
                assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), symbol.astNode.position,"P8ZP_SCRATCH_W1"), varname)
                asmgen.restoreRegisterLocal(CpuRegister.A)
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
                asmgen.saveRegisterLocal(CpuRegister.A, containment.definingISub()!!)
                assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), symbol.astNode.position, "P8ZP_SCRATCH_W1"), varname)
                asmgen.restoreRegisterLocal(CpuRegister.A)
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
        assignRegisterByte(target, CpuRegister.A)
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
                        is PtRpn -> {
                            val addrExpr = value.address as PtRpn
                            if(asmgen.tryOptimizedPointerAccessWithA(addrExpr, addrExpr.finalOperator().operator, false)) {
                                asmgen.out("  ldy  #0")
                                assignRegisterpairWord(target, RegisterOrPair.AY)
                            } else {
                                assignViaExprEval(value.address)
                            }
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
                TargetStorageKind.STACK -> {
                    // byte to word, just assign to registers first, then push onto stack
                    assignExpressionToRegister(value, RegisterOrPair.AY, targetDt==DataType.WORD)
                    asmgen.out("""
                        sta  P8ESTACK_LO,x
                        tya
                        sta  P8ESTACK_HI,x
                        dex""")
                    return
                }
                else -> throw AssemblyError("weird target")
            }
        }

        if(targetDt==DataType.FLOAT && (target.register==RegisterOrPair.FAC1 || target.register==RegisterOrPair.FAC2)) {
            when(valueDt) {
                DataType.UBYTE -> {
                    assignExpressionToRegister(value, RegisterOrPair.Y, false)
                    asmgen.saveRegisterLocal(CpuRegister.X, origTypeCastExpression.definingISub()!!)
                    asmgen.out("  jsr  floats.FREADUY")
                    asmgen.restoreRegisterLocal(CpuRegister.X)
                }
                DataType.BYTE -> {
                    assignExpressionToRegister(value, RegisterOrPair.A, true)
                    asmgen.saveRegisterLocal(CpuRegister.X, origTypeCastExpression.definingISub()!!)
                    asmgen.out("  jsr  floats.FREADSA")
                    asmgen.restoreRegisterLocal(CpuRegister.X)
                }
                DataType.UWORD -> {
                    assignExpressionToRegister(value, RegisterOrPair.AY, false)
                    asmgen.saveRegisterLocal(CpuRegister.X, origTypeCastExpression.definingISub()!!)
                    asmgen.out("  jsr  floats.GIVUAYFAY")
                    asmgen.restoreRegisterLocal(CpuRegister.X)
                }
                DataType.WORD -> {
                    assignExpressionToRegister(value, RegisterOrPair.AY, true)
                    asmgen.saveRegisterLocal(CpuRegister.X, origTypeCastExpression.definingISub()!!)
                    asmgen.out("  jsr  floats.GIVAYFAY")
                    asmgen.restoreRegisterLocal(CpuRegister.X)
                }
                else -> throw AssemblyError("invalid dt")
            }
            if(target.register==RegisterOrPair.FAC2) {
                asmgen.out("  jsr floats.MOVEF")
            }
            return
        }

        // No more special optmized cases yet. Do the rest via more complex evaluation
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
                            pha
                            lda  #<$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            lda  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2+1
                            pla
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
                            pha
                            lda  #<$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            lda  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2+1
                            pla
                            jsr  floats.cast_from_w""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.STR -> throw AssemblyError("cannot typecast a string value")
            else -> throw AssemblyError("weird type")
        }
    }


    private fun assignStackValue(target: AsmAssignTarget) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when (target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> {
                        asmgen.out(" inx | lda  P8ESTACK_LO,x  | sta  ${target.asmVarname}")
                    }
                    DataType.UWORD, DataType.WORD -> {
                        asmgen.out("""
                            inx
                            lda  P8ESTACK_LO,x
                            sta  ${target.asmVarname}
                            lda  P8ESTACK_HI,x
                            sta  ${target.asmVarname}+1
                        """)
                    }
                    DataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            ldy  #>${target.asmVarname}
                            jsr  floats.pop_float
                        """)
                    }
                    DataType.STR -> {
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            ldy  #>${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            inx
                            lda  P8ESTACK_HI,x
                            tay
                            lda  P8ESTACK_LO,x
                            jsr  prog8_lib.strcpy""")
                    }
                    else -> throw AssemblyError("weird target variable type ${target.datatype}")
                }
            }
            TargetStorageKind.MEMORY -> {
                asmgen.out("  inx |  lda  P8ESTACK_LO,x")
                storeRegisterAInMemoryAddress(target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if(target.constArrayIndexValue!=null) {
                    val scaledIdx = target.constArrayIndexValue!! * program.memsizer.memorySize(target.datatype).toUInt()
                    when(target.datatype) {
                        in ByteDatatypes -> {
                            asmgen.out(" inx | lda  P8ESTACK_LO,x  | sta  ${target.asmVarname}+$scaledIdx")
                        }
                        in WordDatatypes -> {
                            asmgen.out("""
                                inx
                                lda  P8ESTACK_LO,x
                                sta  ${target.asmVarname}+$scaledIdx
                                lda  P8ESTACK_HI,x
                                sta  ${target.asmVarname}+$scaledIdx+1
                            """)
                        }
                        DataType.FLOAT -> {
                            asmgen.out("""
                                lda  #<(${target.asmVarname}+$scaledIdx)
                                ldy  #>(${target.asmVarname}+$scaledIdx)
                                jsr  floats.pop_float
                            """)
                        }
                        else -> throw AssemblyError("weird target variable type ${target.datatype}")
                    }
                }
                else
                {
                    target.array!!
                    when(target.datatype) {
                        DataType.UBYTE, DataType.BYTE -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                            asmgen.out(" inx |  lda  P8ESTACK_LO,x |  sta  ${target.asmVarname},y")
                        }
                        DataType.UWORD, DataType.WORD -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                            asmgen.out("""
                                inx
                                lda  P8ESTACK_LO,x
                                sta  ${target.asmVarname},y
                                lda  P8ESTACK_HI,x
                                sta  ${target.asmVarname}+1,y
                            """)
                        }
                        DataType.FLOAT -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.A)
                            asmgen.out("""
                                ldy  #>${target.asmVarname}
                                clc
                                adc  #<${target.asmVarname}
                                bcc  +
                                iny
+                               jsr  floats.pop_float""")
                        }
                        else -> throw AssemblyError("weird dt")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when (target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> {
                        when(target.register!!) {
                            RegisterOrPair.A -> asmgen.out(" inx |  lda  P8ESTACK_LO,x")
                            RegisterOrPair.X -> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.position}")
                            RegisterOrPair.Y -> asmgen.out(" inx |  ldy  P8ESTACK_LO,x")
                            RegisterOrPair.AX -> asmgen.out(" inx |  txy |  ldx  #0 |  lda  P8ESTACK_LO,y")
                            RegisterOrPair.AY -> asmgen.out(" inx |  ldy  #0 |  lda  P8ESTACK_LO,x")
                            in Cx16VirtualRegisters -> {
                                asmgen.out(
                                    """
                                    inx
                                    lda  P8ESTACK_LO,x
                                    sta  cx16.${target.register.toString().lowercase()}
                                    lda  #0
                                    sta  cx16.${target.register.toString().lowercase()}+1
                                """)
                            }
                            else -> throw AssemblyError("can't assign byte from stack to register pair XY")
                        }
                    }
                    DataType.UWORD, DataType.WORD, in PassByReferenceDatatypes -> {
                        when(target.register!!) {
                            RegisterOrPair.AX -> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.position}")
                            RegisterOrPair.AY-> asmgen.out(" inx |  ldy  P8ESTACK_HI,x |  lda  P8ESTACK_LO,x")
                            RegisterOrPair.XY-> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.position}")
                            in Cx16VirtualRegisters -> {
                                asmgen.out(
                                    """
                                    inx
                                    lda  P8ESTACK_LO,x
                                    sta  cx16.${target.register.toString().lowercase()}
                                    lda  P8ESTACK_HI,x
                                    sta  cx16.${target.register.toString().lowercase()}+1
                                """)
                            }
                            else -> throw AssemblyError("can't assign word to single byte register")
                        }
                    }
                    DataType.FLOAT -> {
                        when(target.register!!) {
                            RegisterOrPair.FAC1 -> asmgen.out("  jsr  floats.pop_float_fac1")
                            RegisterOrPair.FAC2 -> asmgen.out("  jsr  floats.pop_float_fac2")
                            else -> throw AssemblyError("can only assign float to Fac1 or 2")
                        }
                    }

                    else -> throw AssemblyError("weird dt")
                }
            }
            TargetStorageKind.STACK -> {}
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
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #<$sourceName
                    sta  P8ESTACK_LO,x
                    lda  #>$sourceName
                    sta  P8ESTACK_HI,x
                    dex""")
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
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #<$sourceName
                    ldy  #>$sourceName+1
                    sta  P8ESTACK_LO,x
                    tya
                    sta  P8ESTACK_HI,x
                    dex""")
            }
            else -> throw AssemblyError("string-assign to weird target")
        }
    }

    private fun assignVariableWord(target: AsmAssignTarget, sourceName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    ldy  $sourceName+1
                    sta  ${target.asmVarname}
                    sty  ${target.asmVarname}+1
                """)
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("assign word to memory ${target.memory} should have gotten a typecast")
            }
            TargetStorageKind.ARRAY -> {
                target.array!!
                if(target.constArrayIndexValue!=null) {
                    val scaledIdx = target.constArrayIndexValue!! * program.memsizer.memorySize(target.datatype).toUInt()
                    when(target.datatype) {
                        in ByteDatatypes -> {
                            asmgen.out(" lda  $sourceName  | sta  ${target.asmVarname}+$scaledIdx")
                        }
                        in WordDatatypes -> {
                            asmgen.out("""
                                lda  $sourceName
                                sta  ${target.asmVarname}+$scaledIdx
                                lda  $sourceName+1
                                sta  ${target.asmVarname}+$scaledIdx+1
                            """)
                        }
                        DataType.FLOAT -> {
                            asmgen.out("""
                                lda  #<$sourceName
                                ldy  #>$sourceName
                                sta  P8ZP_SCRATCH_W1
                                sty  P8ZP_SCRATCH_W1+1
                                lda  #<(${target.asmVarname}+$scaledIdx)
                                ldy  #>(${target.asmVarname}+$scaledIdx)
                                jsr  floats.copy_float
                            """)
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
                            asmgen.out("""
                                lda  $sourceName
                                sta  ${target.asmVarname},y
                                lda  $sourceName+1
                                sta  ${target.asmVarname}+1,y
                            """)
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
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  P8ESTACK_LO,x
                    lda  $sourceName+1
                    sta  P8ESTACK_HI,x
                    dex""")
            }
        }
    }

    internal fun assignFAC2float(target: AsmAssignTarget) {
        asmgen.out("  jsr  floats.MOVFA")       // fac2 -> fac1
        assignFAC1float(target)
    }

    internal fun assignFAC1float(target: AsmAssignTarget) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    stx  P8ZP_SCRATCH_REG
                    ldx  #<${target.asmVarname}
                    ldy  #>${target.asmVarname}
                    jsr  floats.MOVMF
                    ldx  P8ZP_SCRATCH_REG
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
                if (target.register!! != RegisterOrPair.FAC1)
                    throw AssemblyError("can't assign Fac1 float to another register")
            }
            TargetStorageKind.STACK -> asmgen.out("  jsr  floats.push_fac1")
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
            TargetStorageKind.STACK -> asmgen.out("  jsr  floats.push_float")
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
            TargetStorageKind.STACK -> asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName |  jsr  floats.push_float")
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
                if(target.origAstTarget?.array?.variable?.type==DataType.UWORD) {
                    // assigning an indexed pointer var
                    if (target.constArrayIndexValue==0u) {
                        asmgen.out("  lda  $sourceName")
                        asmgen.storeAIntoPointerVar(target.origAstTarget.array!!.variable)
                    } else {
                        asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UBYTE, CpuRegister.Y)
                        if (asmgen.isZpVar(target.origAstTarget.array!!.variable)) {
                            asmgen.out("  lda  $sourceName |  sta  (${target.asmVarname}),y")
                        } else {
                            asmgen.out("  lda  ${target.asmVarname} |  sta  P8ZP_SCRATCH_W2 |  lda  ${target.asmVarname}+1 |  sta  P8ZP_SCRATCH_W2+1")
                            asmgen.out("  lda  $sourceName |  sta  (P8ZP_SCRATCH_W2),y")
                        }
                    }
                    return
                }
                if (target.constArrayIndexValue!=null) {
                    val scaledIdx = target.constArrayIndexValue!! * program.memsizer.memorySize(target.datatype).toUInt()
                    asmgen.out(" lda  $sourceName  | sta  ${target.asmVarname}+$scaledIdx")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(target.array!!, target.datatype, CpuRegister.Y)
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
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  P8ESTACK_LO,x
                    dex""")
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
                if (wordtarget.constArrayIndexValue!=null) {
                    val scaledIdx = wordtarget.constArrayIndexValue!! * 2u
                    asmgen.out("  lda  $sourceName")
                    asmgen.signExtendAYlsb(DataType.BYTE)
                    asmgen.out("  sta  ${wordtarget.asmVarname}+$scaledIdx |  sty  ${wordtarget.asmVarname}+$scaledIdx+1")
                }
                else {
                    asmgen.saveRegisterLocal(CpuRegister.X, wordtarget.scope!!)
                    asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array!!, wordtarget.datatype, CpuRegister.X)
                    asmgen.out("  lda  $sourceName")
                    asmgen.signExtendAYlsb(DataType.BYTE)
                    asmgen.out("  sta  ${wordtarget.asmVarname},x |  inx |  tya |  sta  ${wordtarget.asmVarname},x")
                    asmgen.restoreRegisterLocal(CpuRegister.X)
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
                        pha
                        ora  #$7f
                        bmi  +
                        lda  #0
+                       tay
                        pla""")
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
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  P8ESTACK_LO,x
                    ora  #$7f
                    bmi  +                    
                    lda  #0
+                   sta  P8ESTACK_HI,x
                    dex""")
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
                if (wordtarget.constArrayIndexValue!=null) {
                    val scaledIdx = wordtarget.constArrayIndexValue!! * 2u
                    asmgen.out("  lda  $sourceName  | sta  ${wordtarget.asmVarname}+$scaledIdx")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  ${wordtarget.asmVarname}+$scaledIdx+1")
                    else
                        asmgen.out("  lda  #0  | sta  ${wordtarget.asmVarname}+$scaledIdx+1")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array!!, wordtarget.datatype, CpuRegister.Y)
                    asmgen.out("""
                        lda  $sourceName
                        sta  ${wordtarget.asmVarname},y
                        iny
                        lda  #0
                        sta  ${wordtarget.asmVarname},y""")
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
            TargetStorageKind.STACK -> {
                asmgen.out("  lda  $sourceName |  sta  P8ESTACK_LO,x")
                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                    asmgen.out("  stz  P8ESTACK_HI,x |  dex")
                else
                    asmgen.out("  lda  #0 |  sta  P8ESTACK_HI,x |  dex")
            }
            else -> throw AssemblyError("target type isn't word")
        }
    }

    internal fun assignRegisterByte(target: AsmAssignTarget, register: CpuRegister) {
        // we make an exception in the type check for assigning something to a register pair AX, AY or XY
        // these will be correctly typecasted from a byte to a word value here
        if(target.register !in setOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY))
            require(target.datatype in ByteDatatypes) { "assign target must be byte type ${target.position}"}

        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  st${register.name.lowercase()}  ${target.asmVarname}")
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
                if (target.constArrayIndexValue!=null) {
                    when (register) {
                        CpuRegister.A -> asmgen.out("  sta  ${target.asmVarname}+${target.constArrayIndexValue}")
                        CpuRegister.X -> asmgen.out("  stx  ${target.asmVarname}+${target.constArrayIndexValue}")
                        CpuRegister.Y -> asmgen.out("  sty  ${target.asmVarname}+${target.constArrayIndexValue}")
                    }
                }
                else {
                    when (register) {
                        CpuRegister.A -> {}
                        CpuRegister.X -> asmgen.out(" txa")
                        CpuRegister.Y -> asmgen.out(" tya")
                    }
                    val indexVar = target.array!!.index as PtIdentifier
                    asmgen.out(" ldy  ${asmgen.asmVariableName(indexVar)} |  sta  ${target.asmVarname},y")
                }
            }
            TargetStorageKind.REGISTER -> {
                when(register) {
                    CpuRegister.A -> when(target.register!!) {
                        RegisterOrPair.A -> {}
                        RegisterOrPair.X -> { asmgen.out("  tax") }
                        RegisterOrPair.Y -> { asmgen.out("  tay") }
                        RegisterOrPair.AY -> { asmgen.out("  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  tax |  ldy  #0") }
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
                        RegisterOrPair.AY -> { asmgen.out("  txa |  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  txa |  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  ldy  #0") }
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
                        RegisterOrPair.AY -> { asmgen.out("  tya |  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  tya |  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  tya |  tax |  ldy  #0") }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected type cast to float")
                        in Cx16VirtualRegisters -> {
                            // only assign a single byte to the virtual register's Lsb
                            asmgen.out("  sty  cx16.${target.register.toString().lowercase()}")
                        }
                        else -> throw AssemblyError("weird register")
                    }
                }
            }
            TargetStorageKind.STACK -> {
                when(register) {
                    CpuRegister.A -> asmgen.out(" sta  P8ESTACK_LO,x |  dex")
                    CpuRegister.X -> throw AssemblyError("can't use X here")
                    CpuRegister.Y -> asmgen.out(" tya |  sta  P8ESTACK_LO,x |  dex")
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
                        asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UWORD, CpuRegister.Y, true)
                        asmgen.out("""
                            pla
                            sta  ${target.asmVarname},y
                            dey
                            pla
                            sta  ${target.asmVarname},y""")
                    } else {
                        val srcReg = asmgen.asmSymbolName(regs)
                        asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UWORD, CpuRegister.Y, true)
                        asmgen.out("""
                            lda  $srcReg+1
                            sta  ${target.asmVarname},y
                            dey
                            lda  $srcReg
                            sta  ${target.asmVarname},y""")
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
            TargetStorageKind.STACK -> {
                when(regs) {
                    RegisterOrPair.AY -> asmgen.out("  sta  P8ESTACK_LO,x |  tya |  sta  P8ESTACK_HI,x |  dex")
                    RegisterOrPair.AX, RegisterOrPair.XY -> throw AssemblyError("can't use X here")
                    in Cx16VirtualRegisters -> {
                        val srcReg = asmgen.asmSymbolName(regs)
                        asmgen.out("""
                            lda  $srcReg
                            sta  P8ESTACK_LO,x
                            lda  $srcReg+1
                            sta  P8ESTACK_HI,x
                            dex""")
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
                    asmgen.out("""
                        lda  #0
                        sta  ${target.asmVarname},y
                        sta  ${target.asmVarname}+1,y
                    """)
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
                TargetStorageKind.STACK -> {
                    asmgen.out("  stz  P8ESTACK_LO,x |  stz  P8ESTACK_HI,x |  dex")
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
                asmgen.out("""
                    lda  #<${word.toHex()}
                    sta  ${target.asmVarname},y
                    lda  #>${word.toHex()}
                    sta  ${target.asmVarname}+1,y
                """)
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
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #<${word.toHex()}
                    sta  P8ESTACK_LO,x
                    lda  #>${word.toHex()}
                    sta  P8ESTACK_HI,x
                    dex""")
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
                    if(target.origAstTarget?.array?.variable?.type==DataType.UWORD) {
                        // assigning an indexed pointer var
                        if (target.constArrayIndexValue==0u) {
                            asmgen.out("  lda  #0")
                            asmgen.storeAIntoPointerVar(target.origAstTarget.array!!.variable)
                        } else {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UBYTE, CpuRegister.Y)
                            if (asmgen.isZpVar(target.origAstTarget.array!!.variable)) {
                                asmgen.out("  lda  #0 |  sta  (${target.asmVarname}),y")
                            } else {
                                asmgen.out("  lda  ${target.asmVarname} |  sta  P8ZP_SCRATCH_W2 |  lda  ${target.asmVarname}+1 |  sta  P8ZP_SCRATCH_W2+1")
                                asmgen.out("  lda  #0 |  sta  (P8ZP_SCRATCH_W2),y")
                            }
                        }
                        return
                    }
                    if (target.constArrayIndexValue!=null) {
                        val indexValue = target.constArrayIndexValue!!
                        asmgen.out("  stz  ${target.asmVarname}+$indexValue")
                    }
                    else {
                        asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UBYTE, CpuRegister.Y)
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
                TargetStorageKind.STACK -> {
                    asmgen.out("  stz  P8ESTACK_LO,x |  dex")
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
                if(target.origAstTarget?.array?.variable?.type==DataType.UWORD) {
                    // assigning an indexed pointer var
                    if (target.constArrayIndexValue==0u) {
                        asmgen.out("  lda  #${byte.toHex()}")
                        asmgen.storeAIntoPointerVar(target.origAstTarget.array!!.variable)
                    } else {
                        asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UBYTE, CpuRegister.Y)
                        if (asmgen.isZpVar(target.origAstTarget.array!!.variable)) {
                            asmgen.out("  lda  #${byte.toHex()} |  sta  (${target.asmVarname}),y")
                        } else {
                            asmgen.out("  lda  ${target.asmVarname} |  sta  P8ZP_SCRATCH_W2 |  lda  ${target.asmVarname}+1 |  sta  P8ZP_SCRATCH_W2+1")
                            asmgen.out("  lda  #${byte.toHex()} |  sta  (P8ZP_SCRATCH_W2),y")
                        }
                    }
                    return
                }
                if (target.constArrayIndexValue!=null) {
                    val indexValue = target.constArrayIndexValue!!
                    asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname}+$indexValue")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UBYTE, CpuRegister.Y)
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
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #${byte.toHex()}
                    sta  P8ESTACK_LO,x
                    dex""")
            }
        }
    }

    private fun assignConstantFloat(target: AsmAssignTarget, float: Double) {
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
                TargetStorageKind.STACK -> {
                    val floatConst = allocator.getFloatAsmConst(float)
                    asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  floats.push_float")
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
                TargetStorageKind.STACK -> {
                    val floatConst = allocator.getFloatAsmConst(float)
                    asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  floats.push_float")
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
                    assignRegisterByte(target, CpuRegister.A)
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
                TargetStorageKind.STACK -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  P8ESTACK_LO,x
                        dex""")
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
                    assignRegisterByte(target, CpuRegister.A)
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
                TargetStorageKind.STACK -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  P8ESTACK_LO,x |  dex")
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
                    else -> throw AssemblyError("word regs can only be pair")
                }
                TargetStorageKind.STACK -> {
                    asmgen.out("  lda  ${address.toHex()} |  sta  P8ESTACK_LO,x")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  P8ESTACK_HI,x |  dex")
                    else
                        asmgen.out("  lda  #0 |  sta  P8ESTACK_HI,x |  dex")
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
                        else -> throw AssemblyError("word regs can only be pair")
                    }
                }
                TargetStorageKind.STACK -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out("  sta  P8ESTACK_LO,x")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  P8ESTACK_HI,x |  dex")
                    else
                        asmgen.out("  lda  #0 |  sta  P8ESTACK_HI,x |  dex")
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
            addressExpr is PtRpn -> {
                if(!asmgen.tryOptimizedPointerAccessWithA(addressExpr, addressExpr.finalOperator().operator, true))
                    storeViaExprEval()
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
                    TargetStorageKind.STACK -> TODO("no asm gen for byte stack invert")
                    TargetStorageKind.ARRAY -> assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("~", assign), scope)
                    else -> throw AssemblyError("weird target")
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
                    TargetStorageKind.STACK -> TODO("no asm gen for word stack invert")
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
                    TargetStorageKind.STACK -> TODO("no asm gen for byte stack negate")
                    TargetStorageKind.ARRAY -> assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("-", assign), scope)
                    else -> throw AssemblyError("weird target")
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
                                    pha
                                    tya
                                    eor  #255
                                    adc  #0
                                    tay
                                    pla""")
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
                    TargetStorageKind.STACK -> TODO("no asm gen for word stack negate")
                    TargetStorageKind.ARRAY -> assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("-", assign), scope)
                    else -> throw AssemblyError("weird target")
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
                    TargetStorageKind.STACK -> TODO("no asm gen for float stack negate")
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
