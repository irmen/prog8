package prog8.codegen.cpu6502

import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.assignment.AsmAssignTarget
import prog8.codegen.cpu6502.assignment.AssignmentAsmGen
import prog8.codegen.cpu6502.assignment.PointerAssignmentsGen
import prog8.codegen.cpu6502.assignment.TargetStorageKind

internal class IfExpressionAsmGen(private val asmgen: AsmGen6502Internal, private val pointergen: PointerAssignmentsGen, private val assignmentAsmGen: AssignmentAsmGen, private val errors: IErrorReporter) {

    internal fun assignIfExpression(target: AsmAssignTarget, expr: PtIfExpression) {
        require(target.datatype==expr.type ||
                target.datatype.isUnsignedWord && (expr.type.isString || expr.type.isPointer) ||
                target.datatype.isPointer && (expr.type.isUnsignedWord || expr.type.isPointer || expr.type.isString))
        val falseLabel = asmgen.makeLabel("ifexpr_false")
        val endLabel = asmgen.makeLabel("ifexpr_end")
        evalIfExpressionConditonAndBranchWhenFalse(expr.condition, falseLabel)

        if(expr.type.isByteOrBool) {
            asmgen.assignExpressionToRegister(expr.truevalue, RegisterOrPair.A)
            asmgen.jmp(endLabel)
            asmgen.out(falseLabel)
            asmgen.assignExpressionToRegister(expr.falsevalue, RegisterOrPair.A)
            asmgen.out(endLabel)
            assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, false, false)
        } else if(expr.type.isFloat) {
            asmgen.assignExpressionToRegister(expr.truevalue, RegisterOrPair.FAC1, true)
            asmgen.jmp(endLabel)
            asmgen.out(falseLabel)
            asmgen.assignExpressionToRegister(expr.falsevalue, RegisterOrPair.FAC1, true)
            asmgen.out(endLabel)
            asmgen.assignRegister(RegisterOrPair.FAC1, target)
        } else {
            asmgen.assignExpressionTo(expr.truevalue, target)
            asmgen.jmp(endLabel)
            asmgen.out(falseLabel)
            asmgen.assignExpressionTo(expr.falsevalue, target)
            asmgen.out(endLabel)
        }
    }

    internal fun assignBranchCondExpression(target: AsmAssignTarget, expr: PtBranchCondExpression) {
        require(target.datatype==expr.type ||
                target.datatype.isUnsignedWord && (expr.type.isString || expr.type.isPointer) ||
                target.datatype.isPointer && (expr.type.isUnsignedWord || expr.type.isPointer || expr.type.isString))

        if(target.kind==TargetStorageKind.REGISTER && target.datatype.isUnsignedByte && target.register==RegisterOrPair.A) {
            if(expr.condition==BranchCondition.CC) {
                if(expr.truevalue.asConstInteger()==0 && expr.falsevalue.asConstInteger()==1) {
                    asmgen.out("  lda  #0 |  rol a")
                    return
                }
                else if(expr.truevalue.asConstInteger()==1 && expr.falsevalue.asConstInteger()==0) {
                    asmgen.out("  lda  #0 |  rol a |  eor  #1")
                    return
                }
            }
            else if(expr.condition==BranchCondition.CS) {
                if(expr.truevalue.asConstInteger()==0 && expr.falsevalue.asConstInteger()==1) {
                    asmgen.out("  lda  #0 |  rol a |  eor  #1")
                    return
                }
                else if(expr.truevalue.asConstInteger()==1 && expr.falsevalue.asConstInteger()==0) {
                    asmgen.out("  lda  #0 |  rol a")
                    return
                }
            }
        }

        val trueLabel = asmgen.makeLabel("branchexpr_true")
        val endLabel = asmgen.makeLabel("branchexpr_end")
        val branch = asmgen.branchInstruction(expr.condition, false)

        asmgen.out("  $branch  $trueLabel")

        when {
            expr.type.isByteOrBool -> {
                asmgen.assignExpressionToRegister(expr.falsevalue, RegisterOrPair.A)
                asmgen.jmp(endLabel)
                asmgen.out(trueLabel)
                asmgen.assignExpressionToRegister(expr.truevalue, RegisterOrPair.A)
                asmgen.out(endLabel)
                assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, false, false)
            }
            expr.type.isWord || expr.type.isString -> {
                asmgen.assignExpressionToRegister(expr.falsevalue, RegisterOrPair.AY)
                asmgen.jmp(endLabel)
                asmgen.out(trueLabel)
                asmgen.assignExpressionToRegister(expr.truevalue, RegisterOrPair.AY)
                asmgen.out(endLabel)
                assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
            }
            expr.type.isFloat -> {
                asmgen.assignExpressionToRegister(expr.falsevalue, RegisterOrPair.FAC1, true)
                asmgen.jmp(endLabel)
                asmgen.out(trueLabel)
                asmgen.assignExpressionToRegister(expr.truevalue, RegisterOrPair.FAC1, true)
                asmgen.out(endLabel)
                asmgen.assignRegister(RegisterOrPair.FAC1, target)
            }
            else -> throw AssemblyError("weird dt")
        }
    }

    private fun evalIfExpressionConditonAndBranchWhenFalse(condition: PtExpression, falseLabel: String) {
        when (condition) {
            is PtBinaryExpression -> {
                val rightDt = condition.right.type
                return when {
                    rightDt.isByteOrBool -> translateIfExpressionByteConditionBranch(condition, falseLabel)
                    rightDt.isWord -> translateIfExpressionWordConditionBranch(condition, falseLabel)
                    rightDt.isLong -> translateIfExpressionLongConditionBranch(condition, falseLabel)
                    rightDt.isFloat -> translateIfExpressionFloatConditionBranch(condition, falseLabel)
                    else -> throw AssemblyError("weird dt")
                }
            }

            is PtPrefix if condition.operator=="not" -> {
                throw AssemblyError("not prefix in ifexpression should have been replaced by swapped values")
            }

            is PtPointerDeref -> {
                val (zpPtrVar, offset) = pointergen.deref(condition)
                asmgen.loadIndirectByte(zpPtrVar, offset)
                asmgen.out("  beq  $falseLabel")
            }

            else -> {
                // the condition is "simple" enough to just assign its 0/1 value to a register and branch on that
                asmgen.assignConditionValueToRegisterAndTest(condition)
                asmgen.out("  beq  $falseLabel")
            }
        }
    }

    private fun translateIfExpressionByteConditionBranch(condition: PtBinaryExpression, falseLabel: String) {
        val useBIT = asmgen.checkIfConditionCanUseBIT(condition)
        if(useBIT!=null) {
            // use a BIT instruction to test for bit 7 or 6 set/clear
            val (testForBitSet, variable, bitmask) = useBIT
            when (bitmask) {
                128 -> {
                    // test via bit + N flag
                    asmgen.out("  bit  ${asmgen.asmVariableName(variable)}")
                    if(testForBitSet) asmgen.out("  bpl  $falseLabel")
                    else asmgen.out("  bmi  $falseLabel")
                    return
                }
                64 -> {
                    // test via bit + V flag
                    asmgen.out("  bit  ${asmgen.asmVariableName(variable)}")
                    if(testForBitSet) asmgen.out("  bvc  $falseLabel")
                    else asmgen.out("  bvs  $falseLabel")
                    return
                }
                else -> throw AssemblyError("BIT can only work on bits 7 and 6")
            }
        }

        val (left, right, operator) = swapOperandsIfSimpler(condition.left, condition.right, condition.operator)
        val signed = left.type.isSigned
        val constValue = right.asConstInteger()
        if(constValue==0) {
            return translateIfCompareWithZeroByteBranch(left, operator, signed, falseLabel)
        }

        when(operator) {
            "==" -> {
                // if X==value
                asmgen.assignExpressionToRegister(left, RegisterOrPair.A, signed)
                asmgen.cmpAwithByteValue(right, false)
                asmgen.out("  bne  $falseLabel")
            }
            "!=" -> {
                // if X!=value
                asmgen.assignExpressionToRegister(left, RegisterOrPair.A, signed)
                asmgen.cmpAwithByteValue(right, false)
                asmgen.out("  beq  $falseLabel")
            }
            "<" -> {
                asmgen.assignExpressionToRegister(left, RegisterOrPair.A, signed)
                if (signed) {
                    asmgen.cmpAwithByteValue(right, true)
                    asmgen.out("  bvc  + | eor  #128 | + | bpl  $falseLabel")
                } else {
                    asmgen.cmpAwithByteValue(right, false)
                    asmgen.out("  bcs  $falseLabel")
                }
            }
            "<=" -> {
                // X<=Y -> Y>=X
                asmgen.assignExpressionToRegister(right, RegisterOrPair.A, signed)
                if (signed) {
                    asmgen.cmpAwithByteValue(left, true)
                    asmgen.out("  bvc  + | eor  #128 | + | bmi  $falseLabel")
                } else {
                    asmgen.cmpAwithByteValue(left, false)
                    asmgen.out("  bcc  $falseLabel")
                }
            }
            ">" -> {
                // X>Y -> Y<X
                asmgen.assignExpressionToRegister(right, RegisterOrPair.A, signed)
                if (signed) {
                    asmgen.cmpAwithByteValue(left, true)
                    asmgen.out("  bvc  + | eor  #128 | + | bpl  $falseLabel")
                } else {
                    asmgen.cmpAwithByteValue(left, false)
                    asmgen.out("  bcs  $falseLabel")
                }
            }
            ">=" -> {
                asmgen.assignExpressionToRegister(left, RegisterOrPair.A, signed)
                if (signed) {
                    asmgen.cmpAwithByteValue(right, true)
                    asmgen.out("  bvc  + | eor  #128 | + | bmi  $falseLabel")
                } else {
                    asmgen.cmpAwithByteValue(right, false)
                    asmgen.out("  bcc  $falseLabel")
                }
            }
            in LogicalOperators -> {
                val regAtarget = AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, DataType.BOOL, condition.definingISub(), condition.position, register=RegisterOrPair.A)
                if (assignmentAsmGen.optimizedLogicalExpr(condition, regAtarget)) {
                    asmgen.out("  beq  $falseLabel")
                } else {
                    errors.warn("SLOW FALLBACK FOR 'IFEXPR' CODEGEN - ask for support", condition.position)      //  should not occur ;-)
                    asmgen.assignConditionValueToRegisterAndTest(condition)
                    asmgen.out("  beq  $falseLabel")
                }
            }
            else -> {
                // TODO don't store condition as expression result but just use the flags, like a normal PtIfElse translation does
                // TODO: special cases for <, <=, >, >= above.
                asmgen.assignConditionValueToRegisterAndTest(condition)
                asmgen.out("  beq  $falseLabel")
            }
        }
    }

    private fun translateIfExpressionWordConditionBranch(condition: PtBinaryExpression, falseLabel: String) {
        val (left, right, operator) = swapOperandsIfSimpler(condition.left, condition.right, condition.operator)
        val signed = left.type.isSigned
        val constValue = right.asConstInteger()
        if (constValue == 0) {
            return when (operator) {
                "==" -> translateWordExprIsZero(left, falseLabel)
                "!=" -> translateWordExprIsNotZero(left, falseLabel)
                "<" -> translateWordExprLessZero(left, signed, falseLabel)
                "<=" -> translateWordExprLessEqualsZero(left, signed, falseLabel)
                ">" -> translateWordExprGreaterZero(left, signed, falseLabel)
                ">=" -> translateWordExprGreaterEqualsZero(left, signed, falseLabel)
                else -> throw AssemblyError("weird word comparison operator $operator")
            }
        }

        when (operator) {
            "==" -> wordEqualsValue(left, right, false, signed, falseLabel)
            "!=" -> wordEqualsValue(left, right, true, signed, falseLabel)
            "<" -> wordLessValue(left, right, signed, falseLabel)
            ">=" -> wordGreaterEqualsValue(left, right, signed, falseLabel)
            ">" -> {
                // X > Y - swap operands to use < with reversed sense
                wordLessValue(right, left, signed, falseLabel)
            }
            "<=" -> {
                // X <= Y - use >= with swapped operands
                wordGreaterEqualsValue(right, left, signed, falseLabel)
            }
            else -> throw AssemblyError("expected comparison operator for word, got '${operator}'")
        }
    }

    private fun translateIfExpressionLongConditionBranch(condition: PtBinaryExpression, falseLabel: String) {
        val (left, right, operator) = swapOperandsIfSimpler(condition.left, condition.right, condition.operator)
        val constValue = right.asConstInteger()
        if (constValue == 0) {
            // optimized comparisons with zero
            return when (operator) {
                "==" -> translateLongExprIsZero(left, falseLabel)
                "!=" -> translateLongExprIsNotZero(left, falseLabel)
                "<" -> translateLongExprLessZero(left, false, falseLabel)
                "<=" -> translateLongExprLessZero(left, true, falseLabel)
                ">" -> translateLongExprGreaterZero(left, false, falseLabel)
                ">=" -> translateLongExprGreaterZero(left, true, falseLabel)
                else -> throw AssemblyError("weird long comparison operator $operator")
            }
        }

        return when (operator) {
            "==" -> longEqualsValue(left, right, false, falseLabel)
            "!=" -> longEqualsValue(left, right, true, falseLabel)
            "<", "<=", ">", ">=" -> compareLongValues(left, operator, right, falseLabel)
            else -> throw AssemblyError("expected comparison operator for long, got '${operator}'")
        }
    }

    private fun translateIfExpressionFloatConditionBranch(condition: PtBinaryExpression, elseLabel: String) {
        val constValue = (condition.right as? PtNumber)?.number
        if(constValue==0.0) {
            if (condition.operator == "==") {
                // if FL==0.0
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.FAC1, true)
                asmgen.out("  jsr  floats.SIGN |  cmp  #0 |  bne  $elseLabel")
                return
            } else if(condition.operator=="!=") {
                // if FL!=0.0
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.FAC1, true)
                asmgen.out("  jsr  floats.SIGN |  cmp  #0 |  beq  $elseLabel")
                return
            }
        }

        when(condition.operator) {
            "==" -> {
                asmgen.translateFloatsEqualsConditionIntoA(condition.left, condition.right)
                asmgen.out("  beq  $elseLabel")
            }
            "!=" -> {
                asmgen.translateFloatsEqualsConditionIntoA(condition.left, condition.right)
                asmgen.out("  bne  $elseLabel")
            }
            "<" -> {
                asmgen.translateFloatsLessConditionIntoA(condition.left, condition.right, false)
                asmgen.out("  beq  $elseLabel")
            }
            "<=" -> {
                asmgen.translateFloatsLessConditionIntoA(condition.left, condition.right, true)
                asmgen.out("  beq  $elseLabel")
            }
            ">" -> {
                asmgen.translateFloatsLessConditionIntoA(condition.left, condition.right, true)
                asmgen.out("  bne  $elseLabel")
            }
            ">=" -> {
                asmgen.translateFloatsLessConditionIntoA(condition.left, condition.right, false)
                asmgen.out("  bne  $elseLabel")
            }
            else -> throw AssemblyError("expected comparison operator")
        }
    }

    private fun wordEqualsValue(left: PtExpression, right: PtExpression, notEquals: Boolean, signed: Boolean, falseLabel: String) {
        asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
        getWordOperands(right) { valueLsb, valueMsb ->
            if (notEquals) {
                val skipLabel = asmgen.makeLabel("skip")
                asmgen.out("  cmp  $valueLsb | bne  $skipLabel | cpy  $valueMsb | beq  $falseLabel | $skipLabel")
            } else {
                asmgen.out("  cmp  $valueLsb | bne  $falseLabel | cpy  $valueMsb | bne  $falseLabel")
            }
        }
    }

    private fun wordLessValue(left: PtExpression, right: PtExpression, signed: Boolean, falseLabel: String) {
        asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
        getWordOperands(right) { valueLsb, valueMsb ->
            if (signed) {
                asmgen.out("""
                    cmp  $valueLsb
                    tya
                    sbc  $valueMsb
                    bvc  +
                    eor  #128
+                   bpl  $falseLabel""")
            } else {
                asmgen.out("""
                    cmp  $valueLsb
                    tya
                    sbc  $valueMsb
                    bcs  $falseLabel""")
            }
        }
    }

    private fun wordGreaterEqualsValue(left: PtExpression, right: PtExpression, signed: Boolean, falseLabel: String) {
        asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
        getWordOperands(right) { valueLsb, valueMsb ->
            if (signed) {
                asmgen.out("""
                    cmp  $valueLsb
                    tya
                    sbc  $valueMsb
                    bvc  +
                    eor  #128
+                   bmi  $falseLabel""")
            } else {
                asmgen.out("""
                    cmp  $valueLsb
                    tya
                    sbc  $valueMsb
                    bcc  $falseLabel""")
            }
        }
    }

    private fun longEqualsValue(left: PtExpression, right: PtExpression, notEquals: Boolean, falseLabel: String) {
        // Optimized long equals/not-equals comparison
        if (left is PtIdentifier) {
            val varL = asmgen.asmVariableName(left)
            val constR = right.asConstInteger()
            val varR = if (right is PtIdentifier) asmgen.asmVariableName(right as PtIdentifier) else null

            if (notEquals) {
                // Long != comparison
                if (constR != null) {
                    val hex = constR.toLongHex()
                    val skipLabel = asmgen.makeLabel("skip")
                    asmgen.out("""
                        lda  $varL
                        cmp  #$${hex.substring(6, 8)}
                        bne  $skipLabel
                        lda  $varL+1
                        cmp  #$${hex.substring(4, 6)}
                        bne  $skipLabel
                        lda  $varL+2
                        cmp  #$${hex.substring(2, 4)}
                        bne  $skipLabel
                        lda  $varL+3
                        cmp  #$${hex.take(2)}
                        beq  $falseLabel
$skipLabel""")
                } else if (varR != null) {
                    val skipLabel = asmgen.makeLabel("skip")
                    asmgen.out("""
                        lda  $varL
                        cmp  $varR
                        bne  $skipLabel
                        lda  $varL+1
                        cmp  ${varR}+1
                        bne  $skipLabel
                        lda  $varL+2
                        cmp  ${varR}+2
                        bne  $skipLabel
                        lda  $varL+3
                        cmp  ${varR}+3
                        beq  $falseLabel
$skipLabel""")
                } else {
                    // fallback for complex right expression
                    asmgen.assignExpressionToRegister(left, RegisterOrPair.R14R15, true)
                    right.asConstInteger()?.let { c ->
                        val hex = c.toLongHex()
                        asmgen.out("""
                            lda  cx16.r14
                            cmp  #$${hex.substring(6, 8)}
                            bne  $falseLabel
                            lda  cx16.r14+1
                            cmp  #$${hex.substring(4, 6)}
                            bne  $falseLabel
                            lda  cx16.r14+2
                            cmp  #$${hex.substring(2, 4)}
                            bne  $falseLabel
                            lda  cx16.r14+3
                            cmp  #$${hex.take(2)}
                            bne  $falseLabel""")
                    } ?: run {
                        asmgen.assignConditionValueToRegisterAndTest(
                            PtBinaryExpression("!=", DataType.BOOL, left.position).apply {
                                add(left); add(right)
                            }
                        )
                        asmgen.out("  beq  $falseLabel")
                    }
                }
            } else {
                // Long == comparison - use optimized pattern with variable var, old pattern for constant
                if (constR != null) {
                    // Old pattern: branch to false after each compare when they differ
                    val hex = constR.toLongHex()
                    asmgen.out("""
                        lda  $varL
                        cmp  #$${hex.substring(6, 8)}
                        bne  $falseLabel
                        lda  $varL+1
                        cmp  #$${hex.substring(4, 6)}
                        bne  $falseLabel
                        lda  $varL+2
                        cmp  #$${hex.substring(2, 4)}
                        bne  $falseLabel
                        lda  $varL+3
                        cmp  #$${hex.take(2)}
                        bne  $falseLabel""")
                } else if (varR != null) {
                    // Optimized pattern for variable: branch to skip when differ, fall through when equal
                    val skipLabel = asmgen.makeLabel("skip")
                    asmgen.out("""
                        lda  $varL
                        cmp  $varR
                        bne  $skipLabel
                        lda  $varL+1
                        cmp  ${varR}+1
                        bne  $skipLabel
                        lda  $varL+2
                        cmp  ${varR}+2
                        bne  $skipLabel
                        lda  $varL+3
                        cmp  ${varR}+3
$skipLabel""")
                } else {
                    // fallback for complex right expression
                    asmgen.assignExpressionToRegister(left, RegisterOrPair.R14R15, true)
                    right.asConstInteger()?.let { c ->
                        val hex = c.toLongHex()
                        asmgen.out("""
                            lda  cx16.r14
                            cmp  #$${hex.substring(6, 8)}
                            bne  $falseLabel
                            lda  cx16.r14+1
                            cmp  #$${hex.substring(4, 6)}
                            bne  $falseLabel
                            lda  cx16.r14+2
                            cmp  #$${hex.substring(2, 4)}
                            bne  $falseLabel
                            lda  cx16.r14+3
                            cmp  #$${hex.take(2)}
                            bne  $falseLabel""")
                    } ?: run {
                        asmgen.assignConditionValueToRegisterAndTest(
                            PtBinaryExpression("==", DataType.BOOL, left.position).apply {
                                add(left); add(right)
                            }
                        )
                        asmgen.out("  beq  $falseLabel")
                    }
                }
            }
            return
        }

        // Fallback for non-PtIdentifier left: use generic approach
        val varL = if (left is PtIdentifier) asmgen.asmVariableName(left) else {
            asmgen.assignExpressionToRegister(left, RegisterOrPair.R14R15, true)
            "cx16.r14"
        }
        val constR = right.asConstInteger()
        val varR = if (right is PtIdentifier) asmgen.asmVariableName(right as PtIdentifier) else null

        if (notEquals) {
            val skipLabel = asmgen.makeLabel("skip")
            if (constR != null) {
                val hex = constR.toLongHex()
                asmgen.out("""
                    lda  $varL
                    cmp  #$${hex.substring(6, 8)}
                    bne  $skipLabel
                    lda  $varL+1
                    cmp  #$${hex.substring(4, 6)}
                    bne  $skipLabel
                    lda  $varL+2
                    cmp  #$${hex.substring(2, 4)}
                    bne  $skipLabel
                    lda  $varL+3
                    cmp  #$${hex.take(2)}
                    beq  $falseLabel
$skipLabel""")
            } else if (varR != null) {
                asmgen.out("""
                    lda  $varL
                    cmp  $varR
                    bne  $skipLabel
                    lda  $varL+1
                    cmp  ${varR}+1
                    bne  $skipLabel
                    lda  $varL+2
                    cmp  ${varR}+2
                    bne  $skipLabel
                    lda  $varL+3
                    cmp  ${varR}+3
                    beq  $falseLabel
$skipLabel""")
            } else {
                val cond = PtBinaryExpression("!=", DataType.BOOL, left.position)
                cond.add(left)
                cond.add(right)
                asmgen.assignConditionValueToRegisterAndTest(cond)
                asmgen.out("  beq  $falseLabel")
            }
        } else {
            if (constR != null) {
                val hex = constR.toLongHex()
                val skipLabel = asmgen.makeLabel("skip")
                asmgen.out("""
                    lda  $varL
                    cmp  #$${hex.substring(6, 8)}
                    bne  $skipLabel
                    lda  $varL+1
                    cmp  #$${hex.substring(4, 6)}
                    bne  $skipLabel
                    lda  $varL+2
                    cmp  #$${hex.substring(2, 4)}
                    bne  $skipLabel
                    lda  $varL+3
                    cmp  #$${hex.take(2)}
$skipLabel""")
            } else if (varR != null) {
                val skipLabel = asmgen.makeLabel("skip")
                asmgen.out("""
                    lda  $varL
                    cmp  $varR
                    bne  $skipLabel
                    lda  $varL+1
                    cmp  ${varR}+1
                    bne  $skipLabel
                    lda  $varL+2
                    cmp  ${varR}+2
                    bne  $skipLabel
                    lda  $varL+3
                    cmp  ${varR}+3
$skipLabel""")
            } else {
                val cond = PtBinaryExpression("==", DataType.BOOL, left.position)
                cond.add(left)
                cond.add(right)
                asmgen.assignConditionValueToRegisterAndTest(cond)
                asmgen.out("  beq  $falseLabel")
            }
        }
    }

    private fun compareLongValues(left: PtExpression, operator: String, right: PtExpression, falseLabel: String) {
        val (l, r, op) = if (operator == ">" || operator == "<=") {
            Triple(right, left, if (operator == ">") "<" else ">=")
        } else {
            Triple(left, right, operator)
        }
        // op is either "<" or ">="

        val varL = if (l is PtIdentifier) asmgen.asmVariableName(l) else {
            asmgen.assignExpressionToRegister(l, RegisterOrPair.R12R13, true)
            "cx16.r12"
        }
        val constR = r.asConstInteger()
        if (constR != null) {
            val hex = constR.toLongHex()
            asmgen.out("""
                sec
                lda  $varL
                sbc  #$${hex.substring(6, 8)}
                lda  $varL+1
                sbc  #$${hex.substring(4, 6)}
                lda  $varL+2
                sbc  #$${hex.substring(2, 4)}
                lda  $varL+3
                sbc  #$${hex.take(2)}""")
        } else if (r is PtIdentifier) {
            val varR = asmgen.asmVariableName(r)
            asmgen.out("""
                sec
                lda  $varL
                sbc  $varR
                lda  $varL+1
                sbc  $varR+1
                lda  $varL+2
                sbc  $varR+2
                lda  $varL+3
                sbc  $varR+3""")
        } else {
            asmgen.assignExpressionToRegister(r, RegisterOrPair.R14R15, true)
            asmgen.out("""
                sec
                lda  $varL
                sbc  cx16.r14
                lda  $varL+1
                sbc  cx16.r14+1
                lda  $varL+2
                sbc  cx16.r14+2
                lda  $varL+3
                sbc  cx16.r14+3""")
        }

        asmgen.out("  bvc  + | eor  #128 | +")

        if (op == "<") {
            asmgen.out("  bpl  $falseLabel")
        } else {
            asmgen.out("  bmi  $falseLabel")
        }
    }

    private fun translateWordExprIsZero(expr: PtExpression, falseLabel: String) {
        // if w==0
        if(expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  $varname
                ora  $varname+1
                bne  $falseLabel""")
        } else {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.AY)
            asmgen.out("  sty  P8ZP_SCRATCH_REG |  ora  P8ZP_SCRATCH_REG |  bne  $falseLabel")
        }
    }

    private fun translateWordExprIsNotZero(expr: PtExpression, falseLabel: String) {
        // if w!=0
        if(expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  $varname
                ora  $varname+1
                beq  $falseLabel""")
        } else {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.AY)
            asmgen.out("  sty  P8ZP_SCRATCH_REG |  ora  P8ZP_SCRATCH_REG |  beq  $falseLabel")
        }
    }

    private fun translateWordExprLessZero(expr: PtExpression, signed: Boolean, falseLabel: String) {
        // if w<0 (signed only)
        if (!signed) {
            asmgen.jmp(falseLabel)
            return
        }
        loadAndCmp0MSB(expr, false)
        asmgen.out("  bpl  $falseLabel")
    }

    private fun translateWordExprLessEqualsZero(expr: PtExpression, signed: Boolean, falseLabel: String) {
        // if w<=0 (signed only)
        if (!signed) {
            translateWordExprIsZero(expr, falseLabel)
            return
        }
        if(expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            val isLeLabel = asmgen.makeLabel("is_le")
            asmgen.out("""
                lda  ${varname}+1
                bmi  $isLeLabel
                ora  $varname
                bne  $falseLabel
$isLeLabel""")
        } else {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.AY)
            val isLeLabel = asmgen.makeLabel("is_le")
            asmgen.out("""
                sta  P8ZP_SCRATCH_REG
                tya
                bmi  $isLeLabel
                ora  P8ZP_SCRATCH_REG
                bne  $falseLabel
$isLeLabel""")
        }
    }

    private fun translateWordExprGreaterZero(expr: PtExpression, signed: Boolean, falseLabel: String) {
        // if w>0 (signed only)
        if (!signed) {
            translateWordExprIsNotZero(expr, falseLabel)
            return
        }
        if(expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  ${varname}+1
                bmi  $falseLabel
                ora  $varname
                beq  $falseLabel""")
        } else {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.AY)
            asmgen.out("""
                sta  P8ZP_SCRATCH_REG
                tya
                bmi  $falseLabel
                ora  P8ZP_SCRATCH_REG
                beq  $falseLabel""")
        }
    }

    private fun translateWordExprGreaterEqualsZero(expr: PtExpression, signed: Boolean, falseLabel: String) {
        // if w>=0 (signed only)
        if (!signed) {
            return
        }
        loadAndCmp0MSB(expr, false)
        asmgen.out("  bmi  $falseLabel")
    }









    private fun translateLongExprIsZero(expr: PtExpression, falseLabel: String) {
        // if L==0
        if(expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  $varname
                ora  $varname+1
                ora  $varname+2
                ora  $varname+3
                bne  $falseLabel""")
        } else if(expr is PtArrayIndexer) {
            val varname = asmgen.asmVariableName(expr.variable!!)
            asmgen.loadScaledArrayIndexIntoRegister(expr, CpuRegister.Y)
            asmgen.out("""
                lda  $varname,y
                ora  $varname+1,y
                ora  $varname+2,y
                ora  $varname+3,y
                bne  $falseLabel""")
        } else {
            asmgen.pushLongRegisters(RegisterOrPair.R14R15, 1)
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.R14R15, expr.type.isSigned)
            asmgen.out("""
                lda  cx16.r14
                ora  cx16.r14+1
                ora  cx16.r14+2
                ora  cx16.r14+3
                sta  P8ZP_SCRATCH_REG""")
            asmgen.popLongRegisters(RegisterOrPair.R14R15, 1)
            asmgen.out("  lda  P8ZP_SCRATCH_REG |  bne  $falseLabel")
        }
    }

    private fun translateLongExprIsNotZero(expr: PtExpression, falseLabel: String) {
        // if L!=0
        if(expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  $varname
                ora  $varname+1
                ora  $varname+2
                ora  $varname+3
                beq  $falseLabel""")
        } else if(expr is PtArrayIndexer) {
            val varname = asmgen.asmVariableName(expr.variable!!)
            asmgen.loadScaledArrayIndexIntoRegister(expr, CpuRegister.Y)
            asmgen.out("""
                lda  $varname,y
                ora  $varname+1,y
                ora  $varname+2,y
                ora  $varname+3,y
                beq  $falseLabel""")
        } else {
            asmgen.pushLongRegisters(RegisterOrPair.R14R15, 1)
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.R14R15, expr.type.isSigned)
            asmgen.out("""
                lda  cx16.r14
                ora  cx16.r14+1
                ora  cx16.r14+2
                ora  cx16.r14+3
                sta  P8ZP_SCRATCH_REG""")
            asmgen.popLongRegisters(RegisterOrPair.R14R15, 1)
            asmgen.out("  lda  P8ZP_SCRATCH_REG |  beq  $falseLabel")
        }
    }

    private fun translateLongExprLessZero(expr: PtExpression, lessEquals: Boolean, falseLabel: String) {
        if(lessEquals) {
            if(expr is PtIdentifier) {
                val varname = asmgen.asmVariableName(expr)
                val isLeLabel = asmgen.makeLabel("is_le")
                asmgen.out("""
                    lda  $varname+3
                    bmi  $isLeLabel
                    ora  $varname+2
                    ora  $varname+1
                    ora  $varname
                    bne  $falseLabel
$isLeLabel""")
            } else if(expr is PtArrayIndexer) {
                val varname = asmgen.asmVariableName(expr.variable!!)
                asmgen.loadScaledArrayIndexIntoRegister(expr, CpuRegister.Y)
                val isLeLabel = asmgen.makeLabel("is_le")
                asmgen.out("""
                    lda  $varname+3,y
                    bmi  $isLeLabel
                    ora  $varname+2,y
                    ora  $varname+1,y
                    ora  $varname,y
                    bne  $falseLabel
$isLeLabel""")
            } else {
                asmgen.assignExpressionToRegister(expr, RegisterOrPair.R14R15, expr.type.isSigned)
                val isLeLabel = asmgen.makeLabel("is_le")
                asmgen.out("""
                    lda  cx16.r14+3
                    bmi  $isLeLabel
                    ora  cx16.r14+2
                    ora  cx16.r14+1
                    ora  cx16.r14
                    bne  $falseLabel
$isLeLabel""")
            }
        } else {
            loadAndCmp0MSB(expr, true)
            asmgen.out("  bpl  $falseLabel")
        }
    }

    private fun translateLongExprGreaterZero(expr: PtExpression, greaterEquals: Boolean, falseLabel: String) {
        if(greaterEquals) {
            loadAndCmp0MSB(expr, true)
            asmgen.out("  bmi  $falseLabel")
        } else {
            if(expr is PtIdentifier) {
                val varname = asmgen.asmVariableName(expr)
                asmgen.out("""
                    lda  $varname+3
                    bmi  $falseLabel
                    ora  $varname+2
                    ora  $varname+1
                    ora  $varname
                    beq  $falseLabel""")
            } else if(expr is PtArrayIndexer) {
                val varname = asmgen.asmVariableName(expr.variable!!)
                asmgen.loadScaledArrayIndexIntoRegister(expr, CpuRegister.Y)
                asmgen.out("""
                    lda  $varname+3,y
                    bmi  $falseLabel
                    ora  $varname+2,y
                    ora  $varname+1,y
                    ora  $varname,y
                    beq  $falseLabel""")
            } else {
                asmgen.assignExpressionToRegister(expr, RegisterOrPair.R14R15, expr.type.isSigned)
                asmgen.out("""
                    lda  cx16.r14+3
                    bmi  $falseLabel
                    ora  cx16.r14+2
                    ora  cx16.r14+1
                    ora  cx16.r14
                    beq  $falseLabel""")
            }
        }
    }

    private fun translateIfCompareWithZeroByteBranch(expr: PtExpression, operator: String, signed: Boolean, falseLabel: String) {
        // optimized code for byte comparisons with 0

        asmgen.assignConditionValueToRegisterAndTest(expr)
        when (operator) {
            "==" -> asmgen.out("  bne  $falseLabel")
            "!=" -> asmgen.out("  beq  $falseLabel")
            ">" -> {
                if(signed) asmgen.out("  bmi  $falseLabel |  beq  $falseLabel")
                else asmgen.out("  beq  $falseLabel")
            }
            ">=" -> {
                if(signed) asmgen.out("  bmi  $falseLabel")
                else { /* always true for unsigned */ }
            }
            "<" -> {
                if(signed) asmgen.out("  bpl  $falseLabel")
                else asmgen.jmp(falseLabel)
            }
            "<=" -> {
                if(signed) {
                    val isLeLabel = asmgen.makeLabel("is_le")
                    asmgen.out("""
                        beq  $isLeLabel
                        bpl  $falseLabel
$isLeLabel""")
                } else asmgen.out("  bne  $falseLabel")
            }
            else -> throw AssemblyError("expected comparison operator")
        }
    }

    private fun loadAndCmp0MSB(value: PtExpression, long: Boolean) {
        when(value) {
            is PtArrayIndexer -> {
                if(value.variable==null)
                    throw AssemblyError("support for ptr indexing ${value.position}")
                val varname = asmgen.asmVariableName(value.variable!!)
                asmgen.loadScaledArrayIndexIntoRegister(value, CpuRegister.Y)
                if(value.splitWords) {
                    require(!long)
                    asmgen.out("  lda  ${varname}_msb,y")
                }
                else if(long)
                    asmgen.out("  lda  $varname+3,y")
                else
                    asmgen.out("  lda  $varname+1,y")
            }
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(value)
                if(long)
                    asmgen.out("  lda  $varname+3")
                else
                    asmgen.out("  lda  $varname+1")
            }
            is PtAddressOf -> {
                require(!long) {"addresses must still be words not longs"}
                if(value.isFromArrayElement) {
                    asmgen.assignExpressionToRegister(value, RegisterOrPair.AY, true)
                    asmgen.out("  cpy  #0")
                } else {
                    var varname = asmgen.asmVariableName(value.identifier!!)
                    if(value.identifier!!.type.isSplitWordArray) {
                        varname += if(value.isMsbForSplitArray) "_msb" else "_lsb"
                    }
                    asmgen.out("  lda  #>$varname")
                }
            }
            else -> {
                if(long) {
                    // note: clobbers R14+R15
                    asmgen.assignExpressionToRegister(value, RegisterOrPair.R14R15, true)
                    asmgen.out("  lda  cx16.r14+3")
                } else {
                    asmgen.assignExpressionToRegister(value, RegisterOrPair.AY, true)
                    asmgen.out("  cpy  #0")
                }
            }
        }
    }

    private fun swapOperandsIfSimpler(left: PtExpression, right: PtExpression, operator: String): Triple<PtExpression, PtExpression, String> {
        if (complexity(left) < complexity(right)) {
            val newOp = when (operator) {
                "<" -> ">"
                "<=" -> ">="
                ">" -> "<"
                ">=" -> "<="
                else -> operator
            }
            return Triple(right, left, newOp)
        }
        return Triple(left, right, operator)
    }

    private fun complexity(e: PtExpression): Int {
        if (e.asConstInteger() != null) return 0
        if (e is PtIdentifier) return 1
        if (e is PtAddressOf && !e.isFromArrayElement) return 1
        if (e is PtMemoryByte) return 2
        if (e is PtArrayIndexer && e.index.asConstInteger() != null) return 2
        return 10
    }

    private fun getWordOperands(expr: PtExpression, block: (lsb: String, msb: String) -> Unit) {
        val constValue = expr.asConstInteger()
        if (constValue != null) {
            block("#<${constValue}", "#>${constValue}")
        } else if (expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            block(varname, "$varname+1")
        } else if (expr is PtAddressOf && !expr.isFromArrayElement) {
            val identifier = expr.identifier!!
            val varname = if (identifier.type.isSplitWordArray) {
                if (expr.isMsbForSplitArray) identifier.name + "_msb" else identifier.name + "_lsb"
            } else {
                identifier.name
            }
            block("#<$varname", "#>$varname")
        } else {
            asmgen.saveRegisterStack(CpuRegister.A, false)
            asmgen.saveRegisterStack(CpuRegister.Y, false)
            asmgen.assignExpressionToVariable(expr, "P8ZP_SCRATCH_W1", expr.type)
            asmgen.restoreRegisterStack(CpuRegister.Y, false)
            asmgen.restoreRegisterStack(CpuRegister.A, false)
            block("P8ZP_SCRATCH_W1", "P8ZP_SCRATCH_W1+1")
        }
    }
}
