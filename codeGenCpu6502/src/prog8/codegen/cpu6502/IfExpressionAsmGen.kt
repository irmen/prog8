package prog8.codegen.cpu6502

import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.assignment.AsmAssignTarget
import prog8.codegen.cpu6502.assignment.AssignmentAsmGen
import prog8.codegen.cpu6502.assignment.TargetStorageKind

internal class IfExpressionAsmGen(private val asmgen: AsmGen6502Internal, private val assignmentAsmGen: AssignmentAsmGen, private val errors: IErrorReporter) {

    internal fun assignIfExpression(target: AsmAssignTarget, expr: PtIfExpression) {
        require(target.datatype==expr.type ||
                target.datatype.isUnsignedWord && (expr.type.isString || expr.type.isPointer) ||
                target.datatype.isPointer && (expr.type.isUnsignedWord || expr.type.isPointer || expr.type.isString))
        val falseLabel = asmgen.makeLabel("ifexpr_false")
        val endLabel = asmgen.makeLabel("ifexpr_end")
        evalIfExpressionConditonAndBranchWhenFalse(expr.condition, falseLabel)
        when {
            expr.type.isByteOrBool -> {
                asmgen.assignExpressionToRegister(expr.truevalue, RegisterOrPair.A)
                asmgen.jmp(endLabel)
                asmgen.out(falseLabel)
                asmgen.assignExpressionToRegister(expr.falsevalue, RegisterOrPair.A)
                asmgen.out(endLabel)
                assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, false, false)
            }
            expr.type.isWord || expr.type.isString -> {
                asmgen.assignExpressionToRegister(expr.truevalue, RegisterOrPair.AY)
                asmgen.jmp(endLabel)
                asmgen.out(falseLabel)
                asmgen.assignExpressionToRegister(expr.falsevalue, RegisterOrPair.AY)
                asmgen.out(endLabel)
                assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
            }
            expr.type.isLong -> {
                asmgen.assignExpressionToRegister(expr.truevalue, RegisterOrPair.R0R1_32, true)
                asmgen.jmp(endLabel)
                asmgen.out(falseLabel)
                asmgen.assignExpressionToRegister(expr.falsevalue, RegisterOrPair.R0R1_32, true)
                asmgen.out(endLabel)
                assignmentAsmGen.assignRegisterLong(target, RegisterOrPair.R0R1_32)
            }
            expr.type.isFloat -> {
                asmgen.assignExpressionToRegister(expr.truevalue, RegisterOrPair.FAC1, true)
                asmgen.jmp(endLabel)
                asmgen.out(falseLabel)
                asmgen.assignExpressionToRegister(expr.falsevalue, RegisterOrPair.FAC1, true)
                asmgen.out(endLabel)
                asmgen.assignRegister(RegisterOrPair.FAC1, target)
            }
            else -> throw AssemblyError("weird dt")
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

            else -> {
                // the condition is "simple" enough to just assign its 0/1 value to a register and branch on that
                asmgen.assignConditionValueToRegisterAndTest(condition)
                asmgen.out("  beq  $falseLabel")
            }
        }
    }

    private fun translateIfExpressionByteConditionBranch(condition: PtBinaryExpression, falseLabel: String) {
        val signed = condition.left.type.isSigned
        val constValue = condition.right.asConstInteger()
        if(constValue==0) {
            return translateIfCompareWithZeroByteBranch(condition, signed, falseLabel)
        }

        when(condition.operator) {
            "==" -> {
                // if X==value
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
                asmgen.cmpAwithByteValue(condition.right, false)
                asmgen.out("  bne  $falseLabel")
            }
            "!=" -> {
                // if X!=value
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
                asmgen.cmpAwithByteValue(condition.right, false)
                asmgen.out("  beq  $falseLabel")
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
        // TODO can we reuse this whole thing from IfElse ?
        val constValue = condition.right.asConstInteger()
        if(constValue!=null) {
            if (constValue == 0) {
                when (condition.operator) {
                    "==" -> return translateWordExprIsZero(condition.left, falseLabel)
                    "!=" -> return translateWordExprIsNotZero(condition.left, falseLabel)
                }
            }
            if (constValue != 0) {
                when (condition.operator) {
                    "==" -> return translateWordExprEqualsNumber(condition.left, constValue, falseLabel)
                    "!=" -> return translateWordExprNotEqualsNumber(condition.left, constValue, falseLabel)
                }
            }
        }
        val variable = condition.right as? PtIdentifier
        if(variable!=null) {
            when (condition.operator) {
                "==" -> return translateWordExprEqualsVariable(condition.left, variable, falseLabel)
                "!=" -> return translateWordExprNotEqualsVariable(condition.left, variable, falseLabel)
            }
        }

        // TODO don't store condition as expression result but just use the flags, like a normal PtIfElse translation does
        asmgen.assignConditionValueToRegisterAndTest(condition)
        asmgen.out("  beq  $falseLabel")
    }

    private fun translateIfExpressionLongConditionBranch(condition: PtBinaryExpression, falseLabel: String) {
        // TODO can we reuse this whole thing from IfElse ?
        val constValue = condition.right.asConstInteger()
        if(constValue!=null) {
            if (constValue == 0) {
                when (condition.operator) {
                    "==" -> return translateLongExprIsZero(condition.left, falseLabel)
                    "!=" -> return translateLongExprIsNotZero(condition.left, falseLabel)
                }
            }
            if (constValue != 0) {
                when (condition.operator) {
                    "==" -> return translateLongExprEqualsNumber(condition.left, constValue, falseLabel)
                    "!=" -> return translateLongExprNotEqualsNumber(condition.left, constValue, falseLabel)
                }
            }
        }
        val variable = condition.right as? PtIdentifier
        if(variable!=null) {
            when (condition.operator) {
                "==" -> return translateLongExprEqualsVariable(condition.left, variable, falseLabel)
                "!=" -> return translateLongExprNotEqualsVariable(condition.left, variable, falseLabel)
            }
        }

        // TODO don't store condition as expression result but just use the flags, like a normal PtIfElse translation does
        asmgen.assignConditionValueToRegisterAndTest(condition)
        asmgen.out("  beq  $falseLabel")
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

    private fun translateWordExprNotEqualsVariable(expr: PtExpression, variable: PtIdentifier, falseLabel: String) {
        // if w!=variable
        // TODO reuse code from ifElse?
        val varRight = asmgen.asmVariableName(variable)
        if(expr is PtIdentifier) {
            val varLeft = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  $varLeft
                cmp  $varRight
                bne  +
                lda  $varLeft+1
                cmp  $varRight+1
                beq  $falseLabel
+""")
        } else {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.AY)
            asmgen.out("""
                cmp  $varRight
                bne  +
                cpy  $varRight+1
                beq  $falseLabel
+""")
        }
    }

    private fun translateWordExprEqualsVariable(expr: PtExpression, variable: PtIdentifier, falseLabel: String) {
        // if w==variable
        // TODO reuse code from ifElse?
        val varRight = asmgen.asmVariableName(variable)
        if(expr is PtIdentifier) {
            val varLeft = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  $varLeft
                cmp  $varRight
                bne  $falseLabel
                lda  $varLeft+1
                cmp  $varRight+1
                bne  $falseLabel""")
        } else {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.AY)
            asmgen.out("""
                cmp  $varRight
                bne  $falseLabel
                cpy  $varRight+1
                bne  $falseLabel""")
        }
    }

    private fun translateWordExprNotEqualsNumber(expr: PtExpression, number: Int, falseLabel: String) {
        // if w!=number
        // TODO reuse code from ifElse?
        if(expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  $varname
                cmp  #<$number
                bne  +
                lda  $varname+1
                cmp  #>$number
                beq  $falseLabel
+""")
        } else {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.AY)
            asmgen.out("""
                cmp  #<$number
                bne  +
                cpy  #>$number
                beq  $falseLabel
+""")
        }
    }

    private fun translateWordExprEqualsNumber(expr: PtExpression, number: Int, falseLabel: String) {
        // if w==number
        // TODO reuse code from ifElse?
        if(expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  $varname
                cmp  #<$number
                bne  $falseLabel
                lda  $varname+1
                cmp  #>$number
                bne  $falseLabel""")
        } else {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.AY)
            asmgen.out(                """
                cmp  #<$number
                bne  $falseLabel
                cpy  #>$number
                bne  $falseLabel""")
        }
    }

    private fun translateLongExprEqualsNumber(expr: PtExpression, number: Int, falseLabel: String) {
        // if L==number
        // TODO reuse code from ifElse?
        val hex = number.toUInt().toString(16).padStart(8, '0')
        if(expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  $varname
                cmp  #${hex.substring(6, 8)}
                bne  $falseLabel
                lda  $varname+1
                cmp  #${hex.substring(4, 6)}
                bne  $falseLabel
                lda  $varname+2
                cmp  #${hex.substring(2, 4)}
                bne  $falseLabel
                lda  $varname+3
                cmp  #${hex.take(2)}
                bne  $falseLabel""")
        } else {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.R0R1_32)
            asmgen.out("""
                lda  cx16.r0
                cmp  #${hex.substring(6, 8)}
                bne  $falseLabel
                lda  cx16.r0+1
                cmp  #${hex.substring(4, 6)}
                bne  $falseLabel
                lda  cx16.r0+2
                cmp  #${hex.substring(2, 4)}
                bne  $falseLabel
                lda  cx16.r0+3
                cmp  #${hex.take(2)}
                bne  $falseLabel""")
        }
    }

    private fun translateLongExprEqualsVariable(expr: PtExpression, variable: PtIdentifier, falseLabel: String) {
        // if L==variable
        // TODO reuse code from ifElse?
        val varname2 = asmgen.asmVariableName(variable)
        if(expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  $varname
                cmp  $varname2
                bne  $falseLabel
                lda  $varname+1
                cmp  $varname2+1
                bne  $falseLabel
                lda  $varname+2
                cmp  $varname2+2
                bne  $falseLabel
                lda  $varname+3
                cmp  $varname2+3
                bne  $falseLabel""")
        } else {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.R0R1_32)
            asmgen.out("""
                lda  cx16.r0
                cmp  $varname2
                bne  $falseLabel
                lda  cx16.r0+1
                cmp  $varname2+1
                bne  $falseLabel
                lda  cx16.r0+2
                cmp  $varname2+2
                bne  $falseLabel
                lda  cx16.r0+3
                cmp  $varname2+3
                bne  $falseLabel""")
        }
    }

    private fun translateLongExprNotEqualsNumber(expr: PtExpression, number: Int, falseLabel: String) {
        TODO("if expression LONG != number ${expr.position}")
    }

    private fun translateLongExprNotEqualsVariable(expr: PtExpression, variable: PtIdentifier, falseLabel: String) {
        // if L!=variable
        TODO("if expression LONG != variable ${expr.position}")
    }

    private fun translateWordExprIsNotZero(expr: PtExpression, falseLabel: String) {
        // if w!=0
        // TODO reuse code from ifElse?
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

    private fun translateWordExprIsZero(expr: PtExpression, falseLabel: String) {
        // if w==0
        // TODO reuse code from ifElse?
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

    private fun translateLongExprIsZero(expr: PtExpression, falseLabel: String) {
        // if L==0
        // TODO reuse code from ifElse?
        if(expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  $varname
                ora  $varname+1
                ora  $varname+2
                ora  $varname+3
                bne  $falseLabel""")
        } else {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.R0R1_32)
            asmgen.out("""
                lda  cx16.r0
                ora  cx16.r0+1
                ora  cx16.r0+2
                ora  cx16.r0+3
                bne  $falseLabel""")
        }
    }

    private fun translateLongExprIsNotZero(expr: PtExpression, falseLabel: String) {
        // if L!=0
        // TODO reuse code from ifElse?
        if(expr is PtIdentifier) {
            val varname = asmgen.asmVariableName(expr)
            asmgen.out("""
                lda  $varname
                ora  $varname+1
                ora  $varname+2
                ora  $varname+3
                beq  $falseLabel""")
        } else {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.R0R1_32)
            asmgen.out("""
                lda  cx16.r0
                ora  cx16.r0+1
                ora  cx16.r0+2
                ora  cx16.r0+3
                beq  $falseLabel""")
        }
    }

    private fun translateIfCompareWithZeroByteBranch(condition: PtBinaryExpression, signed: Boolean, falseLabel: String) {
        // optimized code for byte comparisons with 0

        val useBIT = asmgen.checkIfConditionCanUseBIT(condition)
        if(useBIT!=null) {
            // use a BIT instruction to test for bit 7 or 6 set/clear
            val (testForBitSet, variable, bitmask) = useBIT
            when (bitmask) {
                128 -> {
                    // test via bit + N flag
                    asmgen.out("  bit  ${variable.name}")
                    if(testForBitSet) asmgen.out("  bpl  $falseLabel")
                    else asmgen.out("  bmi  $falseLabel")
                    return
                }
                64 -> {
                    // test via bit + V flag
                    asmgen.out("  bit  ${variable.name}")
                    if(testForBitSet) asmgen.out("  bvc  $falseLabel")
                    else asmgen.out("  bvs  $falseLabel")
                    return
                }
                else -> throw AssemblyError("BIT can only work on bits 7 and 6")
            }
        }

        asmgen.assignConditionValueToRegisterAndTest(condition.left)
        when (condition.operator) {
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
                    // inverted '>'
                    asmgen.out("""
                        beq  +
                        bpl  $falseLabel
+""")
                } else asmgen.out("  bne  $falseLabel")
            }
            else -> throw AssemblyError("expected comparison operator")
        }
    }

}