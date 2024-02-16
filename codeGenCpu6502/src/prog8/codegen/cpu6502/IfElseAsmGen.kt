package prog8.codegen.cpu6502

import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.assignment.AssignmentAsmGen

internal class IfElseAsmGen(private val program: PtProgram,
                            private val asmgen: AsmGen6502Internal,
                            private val allocator: VariableAllocator,
                            private val assignmentAsmGen: AssignmentAsmGen) {

    fun translate(stmt: PtIfElse) {
        require(stmt.condition.type== DataType.BOOL)

        val jumpAfterIf = stmt.ifScope.children.singleOrNull() as? PtJump

        if(stmt.condition is PtIdentifier ||
            stmt.condition is PtFunctionCall ||
            stmt.condition is PtBuiltinFunctionCall ||
            stmt.condition is PtContainmentCheck)
                return fallbackTranslate(stmt, false)  // the fallback code for these is optimal, so no warning.

        val compareCond = stmt.condition as? PtBinaryExpression
        if(compareCond!=null) {
            return when(compareCond.right.type) {
                in ByteDatatypesWithBoolean -> translateIfByte(stmt, compareCond, jumpAfterIf)
                in WordDatatypes -> translateIfWord(stmt, compareCond, jumpAfterIf)
                DataType.FLOAT -> translateIfFloat(stmt, compareCond, jumpAfterIf)
                else -> throw AssemblyError("weird dt")
            }
        }

        val prefixCond = stmt.condition as? PtPrefix
        if(prefixCond?.operator=="not") {
            assignConditionValueToRegisterAndTest(prefixCond.value)
            return if(jumpAfterIf!=null)
                translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodies("bne", stmt)
        }

        // TODO more optimized ifs
        println("(if with special condition... ${stmt.condition})")
        fallbackTranslate(stmt)
    }

    private fun assignConditionValueToRegisterAndTest(condition: PtExpression) {
        asmgen.assignExpressionToRegister(condition, RegisterOrPair.A, false)
        when(condition) {
            is PtNumber,
            is PtBool,
            is PtIdentifier,
            is PtMachineRegister,
            is PtArrayIndexer,
            is PtPrefix,
            is PtBinaryExpression -> { /* no cmp necessary the lda has been done just prior */ }
            else -> asmgen.out("  cmp  #0")
        }
    }

    private fun fallbackTranslate(stmt: PtIfElse, warning: Boolean=true) {
        if(warning) println("WARN: FALLBACK IF: ${stmt.position}")      // TODO no more of these
        val jumpAfterIf = stmt.ifScope.children.singleOrNull() as? PtJump
        assignConditionValueToRegisterAndTest(stmt.condition)
        if(jumpAfterIf!=null)
            translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
        else
            translateIfElseBodies("beq", stmt)
    }

    private fun translateIfElseBodies(elseBranchInstr: String, stmt: PtIfElse) {
        // comparison value is already in A
        val afterIfLabel = asmgen.makeLabel("afterif")
        if(stmt.hasElse()) {
            // if and else blocks
            val elseLabel = asmgen.makeLabel("else")
            asmgen.out("  $elseBranchInstr  $elseLabel")
            asmgen.translate(stmt.ifScope)
            asmgen.jmp(afterIfLabel, false)
            asmgen.out(elseLabel)
            asmgen.translate(stmt.elseScope)
        } else {
            // no else block
            asmgen.out("  $elseBranchInstr  $afterIfLabel")
            asmgen.translate(stmt.ifScope)
        }
        asmgen.out(afterIfLabel)
    }

    private fun translateJumpElseBodies(branchInstr: String, falseBranch: String, jump: PtJump, elseBlock: PtNodeGroup) {
        // comparison value is already in A
        val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
        if(indirect) {
            asmgen.out("""
                $falseBranch  +
                jmp  ($asmLabel)
+""")
        } else {
            asmgen.out("  $branchInstr  $asmLabel")
        }
        asmgen.translate(elseBlock)
    }

    private fun translateIfByte(stmt: PtIfElse, condition: PtBinaryExpression, jumpAfterIf: PtJump?) {
        val signed = condition.left.type in SignedDatatypes
        val constValue = condition.right.asConstInteger()
        if(constValue==0) {
            if(condition.operator=="==") {
                // if X==0
                assignConditionValueToRegisterAndTest(condition.left)
                return if(jumpAfterIf!=null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            } else if(condition.operator=="!=") {
                // if X!=0
                assignConditionValueToRegisterAndTest(condition.left)
                return if(jumpAfterIf!=null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
        }

        when (condition.operator) {
            "==" -> {
                // if X==value
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
                cmpAwithByteValue(condition.right)
                return if(jumpAfterIf!=null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            }
            "!=" -> {
                // if X!=value
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
                cmpAwithByteValue(condition.right)
                return if(jumpAfterIf!=null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
            "<" -> {
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
                cmpAwithByteValue(condition.right)
                return if(signed) {
                    if(jumpAfterIf!=null)
                        translateJumpElseBodies("bmi", "bpl", jumpAfterIf, stmt.elseScope)
                    else
                        translateIfElseBodies("bpl", stmt)
                } else {
                    if(jumpAfterIf!=null)
                        translateJumpElseBodies("bcc", "bcs", jumpAfterIf, stmt.elseScope)
                    else
                        translateIfElseBodies("bcs", stmt)
                }
            }
            "<=" -> {
                fallbackTranslate(stmt, true)
//                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
//                cmpAwithByteValue(condition.right)
//                if(signed) {
//                    TODO("byte if <=")
//                } else {
//                    TODO("ubyte if <=")
//                }
            }
            ">" -> {
                fallbackTranslate(stmt, true)
//                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
//                cmpAwithByteValue(condition.right)
//                return if(signed) {
//                    TODO("byte if >")
//                } else {
//                    TODO("ubyte if >")
//                }
            }
            ">=" -> {
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
                cmpAwithByteValue(condition.right)
                return if(signed) {
                    if(jumpAfterIf!=null)
                        translateJumpElseBodies("bpl", "bmi", jumpAfterIf, stmt.elseScope)
                    else
                        translateIfElseBodies("bmi", stmt)
                } else {
                    if(jumpAfterIf!=null)
                        translateJumpElseBodies("bcs", "bcc", jumpAfterIf, stmt.elseScope)
                    else
                        translateIfElseBodies("bcc", stmt)
                }
            }
            else -> fallbackTranslate(stmt, false)
        }
    }

    private fun cmpAwithByteValue(value: PtExpression) {
        fun cmpViaScratch() {
            if(assignmentAsmGen.directIntoY(value)) {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.Y, false)
                asmgen.out("  sty  P8ZP_SCRATCH_REG")
            } else {
                asmgen.out("  pha")
                asmgen.assignExpressionToVariable(value, "P8ZP_SCRATCH_REG", value.type)
                asmgen.out("  pla")
            }
            asmgen.out("  cmp  P8ZP_SCRATCH_REG")
        }

        when(value) {
            is PtArrayIndexer -> {
                val constIndex = value.index.asConstInteger()
                if(constIndex!=null) {
                    val offset = constIndex * program.memsizer.memorySize(value.type)
                    if(offset<256) {
                        return asmgen.out("  ldy  #$offset |  cmp  ${asmgen.asmVariableName(value.variable.name)},y")
                    }
                }
                cmpViaScratch()
            }
            is PtMemoryByte -> {
                val constAddr = value.address.asConstInteger()
                if(constAddr!=null) {
                    asmgen.out("  cmp  ${constAddr.toHex()}")
                } else {
                    cmpViaScratch()
                }
            }
            is PtIdentifier -> {
                asmgen.out("  cmp  ${asmgen.asmVariableName(value.name)}")
            }
            is PtNumber -> {
                if(value.number!=0.0)
                    asmgen.out("  cmp  #${value.number.toInt()}")
            }
            else -> {
                cmpViaScratch()
            }
        }
    }

    private fun translateIfWord(stmt: PtIfElse, condition: PtBinaryExpression, jumpAfterIf: PtJump?) {
        val signed = condition.left.type in SignedDatatypes
        val constValue = condition.right.asConstInteger()
        if(constValue==0) {
            if(condition.operator=="==") {
                // if X==0
                // TODO even more optimized code by comparing lsb and msb separately
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.AY, signed)
                asmgen.out("  sty  P8ZP_SCRATCH_REG |  ora  P8ZP_SCRATCH_REG")
                return if(jumpAfterIf!=null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            } else if(condition.operator=="!=") {
                // if X!=0
                // TODO even more optimized code by comparing lsb and msb separately
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.AY, signed)
                asmgen.out("  sty  P8ZP_SCRATCH_REG |  ora  P8ZP_SCRATCH_REG")
                return if(jumpAfterIf!=null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
        }

        when(condition.operator) {
            "==" -> {
                // TODO
                println("word if ==")
                fallbackTranslate(stmt)
            }
            "!=" -> {
                // TODO
                println("word if !=")
                fallbackTranslate(stmt)
            }
            "<" -> {
                // TODO()
                println("word if <")
                fallbackTranslate(stmt)
            }
            "<=" -> {
                // TODO()
                println("word if <=")
                fallbackTranslate(stmt)
            }
            ">" -> {
                // TODO()
                println("word if >")
                fallbackTranslate(stmt)
            }
            ">=" -> {
                // TODO()
                println("word if >=")
                fallbackTranslate(stmt)
            }
            else -> fallbackTranslate(stmt, false)
        }
    }

    private fun translateIfFloat(stmt: PtIfElse, condition: PtBinaryExpression, jumpAfterIf: PtJump?) {
        val constValue = (condition.right as? PtNumber)?.number
        if(constValue==0.0) {
            if (condition.operator == "==") {
                // if FL==0.0
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.FAC1, true)
                asmgen.out("  jsr  floats.SIGN |  cmp  #0")
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            } else if(condition.operator=="!=") {
                // if FL!=0.0
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.FAC1, true)
                asmgen.out("  jsr  floats.SIGN |  cmp  #0")
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
        }

        when(condition.operator) {
            "==" -> {
                translateFloatsEqualsConditionIntoA(condition.left, condition.right)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
            "!=" -> {
                translateFloatsEqualsConditionIntoA(condition.left, condition.right)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            }
            "<" -> {
                translateFloatsLessConditionIntoA(condition.left, condition.right, false)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
            "<=" -> {
                translateFloatsLessConditionIntoA(condition.left, condition.right, true)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
            ">" -> {
                translateFloatsLessConditionIntoA(condition.left, condition.right, true)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            }
            ">=" -> {
                translateFloatsLessConditionIntoA(condition.left, condition.right, false)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            }
            else -> fallbackTranslate(stmt, false)
        }
    }

    private fun translateFloatsEqualsConditionIntoA(left: PtExpression, right: PtExpression) {
        fun equalf(leftName: String, rightName: String) {
            asmgen.out("""
                    lda  #<$leftName
                    ldy  #>$leftName
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<$rightName
                    ldy  #>$rightName
                    jsr  floats.vars_equal_f""")
        }
        fun equalf(expr: PtExpression, rightName: String) {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.FAC1, true)
            asmgen.out("""
                    lda  #<$rightName
                    ldy  #>$rightName
                    jsr  floats.var_fac1_equal_f""")
        }
        if(left is PtIdentifier) {
            when (right) {
                is PtIdentifier -> equalf(asmgen.asmVariableName(left), asmgen.asmVariableName(right))
                is PtNumber -> equalf(asmgen.asmVariableName(left), allocator.getFloatAsmConst(right.number))
                else -> {
                    asmgen.assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
                    equalf(asmgen.asmVariableName(left), subroutineFloatEvalResultVar1)
                    asmgen.subroutineExtra(left.definingISub()!!).usedFloatEvalResultVar1 = true
                }
            }
        } else {
            when (right) {
                is PtIdentifier -> equalf(left, asmgen.asmVariableName(right))
                is PtNumber -> equalf(left, allocator.getFloatAsmConst(right.number))
                else -> {
                    asmgen.assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
                    equalf(left, subroutineFloatEvalResultVar1)
                    asmgen.subroutineExtra(left.definingISub()!!).usedFloatEvalResultVar1 = true
                }
            }
        }
    }

    private fun translateFloatsLessConditionIntoA(left: PtExpression, right: PtExpression, lessOrEquals: Boolean) {
        fun lessf(leftName: String, rightName: String) {
            asmgen.out("""
                lda  #<$rightName
                ldy  #>$rightName
                sta  P8ZP_SCRATCH_W2
                sty  P8ZP_SCRATCH_W2+1
                lda  #<$leftName
                ldy  #>$leftName""")
            if(lessOrEquals)
                asmgen.out("jsr  floats.vars_lesseq_f")
            else
                asmgen.out("jsr  floats.vars_less_f")
        }
        fun lessf(expr: PtExpression, rightName: String) {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.FAC1, true)
            asmgen.out("  lda  #<$rightName |  ldy  #>$rightName")
            if(lessOrEquals)
                asmgen.out("  jsr  floats.var_fac1_lesseq_f")
            else
                asmgen.out("  jsr  floats.var_fac1_less_f")
        }
        if(left is PtIdentifier) {
            when (right) {
                is PtIdentifier -> lessf(asmgen.asmVariableName(left), asmgen.asmVariableName(right))
                is PtNumber -> lessf(asmgen.asmVariableName(left), allocator.getFloatAsmConst(right.number))
                else -> {
                    asmgen.assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
                    lessf(asmgen.asmVariableName(left), subroutineFloatEvalResultVar1)
                }
            }
        } else {
            when (right) {
                is PtIdentifier -> lessf(left, asmgen.asmVariableName(right))
                is PtNumber -> lessf(left, allocator.getFloatAsmConst(right.number))
                else -> {
                    asmgen.assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
                    lessf(left, subroutineFloatEvalResultVar1)
                }
            }
        }
    }

}