package prog8.codegen.cpu6502

import prog8.code.StExtSub
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.assignment.AsmAssignTarget
import prog8.codegen.cpu6502.assignment.AssignmentAsmGen
import prog8.codegen.cpu6502.assignment.TargetStorageKind

internal class IfElseAsmGen(private val program: PtProgram,
                            private val st: SymbolTable,
                            private val asmgen: AsmGen6502Internal,
                            private val assignmentAsmGen: AssignmentAsmGen,
                            private val errors: IErrorReporter) {

    fun translate(stmt: PtIfElse) {
        require(stmt.condition.type.isBool)
        checkNotExtsubReturnsStatusReg(stmt.condition)

        val jumpAfterIf = stmt.ifScope.children.singleOrNull() as? PtJump

        if(stmt.condition is PtIdentifier ||
            stmt.condition is PtBool ||
            stmt.condition is PtArrayIndexer ||
            stmt.condition is PtTypeCast ||
            stmt.condition is PtBuiltinFunctionCall ||
            stmt.condition is PtFunctionCall ||
            stmt.condition is PtMemoryByte ||
            stmt.condition is PtContainmentCheck)
                return fallbackTranslateForSimpleCondition(stmt)

        val compareCond = stmt.condition as? PtBinaryExpression
        if(compareCond!=null) {
            val rightDt = compareCond.right.type
            return when {
                rightDt.isByteOrBool -> translateIfByte(stmt, jumpAfterIf)
                rightDt.isWord -> translateIfWord(stmt, compareCond, jumpAfterIf)
                rightDt.isFloat -> translateIfFloat(stmt, compareCond, jumpAfterIf)
                else -> throw AssemblyError("weird dt")
            }
        }

        val prefixCond = stmt.condition as? PtPrefix
        if(prefixCond?.operator=="not") {
            if(stmt.hasElse())
                throw AssemblyError("not prefix in ifelse should have been replaced by swapped if-else blocks")
            else {
                checkNotExtsubReturnsStatusReg(prefixCond.value)
                asmgen.assignConditionValueToRegisterAndTest(prefixCond.value)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            }
        }

        throw AssemblyError("weird non-boolean condition node type ${stmt.condition} at ${stmt.condition.position}")
    }


    private fun checkNotExtsubReturnsStatusReg(condition: PtExpression) {
        val fcall = condition as? PtFunctionCall
        if(fcall!=null && fcall.type.isBool) {
            val extsub = st.lookup(fcall.name) as? StExtSub
            if(extsub!=null && extsub.returns.any { it.register.statusflag!=null }) {
                throw AssemblyError("if extsub() returning a status register boolean should have been changed into a Conditional branch such as if_cc")
            }
        }
    }

    private fun fallbackTranslateForSimpleCondition(ifElse: PtIfElse) {
        val bittest = ifElse.condition as? PtBuiltinFunctionCall
        val jumpAfterIf = ifElse.ifScope.children.singleOrNull() as? PtJump

        if(bittest!=null && bittest.name.startsWith("prog8_ifelse_bittest_")) {
            val variable = bittest.args[0] as PtIdentifier
            val bitnumber = (bittest.args[1] as PtNumber).number.toInt()
            val testForBitSet = bittest.name.endsWith("_set")
            when (bitnumber) {
                7 -> {
                    // test via bit + N flag
                    asmgen.out("  bit  ${variable.name}")
                    if(testForBitSet) {
                        if(jumpAfterIf!=null) {
                            val (asmLabel, indirect) = asmgen.getJumpTarget(jumpAfterIf)
                            if(indirect)
                                throw AssemblyError("cannot BIT to indirect label ${ifElse.position}")
                            if(ifElse.hasElse())
                                throw AssemblyError("didn't expect else part here ${ifElse.position}")
                            else
                                asmgen.out("  bmi  $asmLabel")
                        }
                        else
                            translateIfElseBodies("bpl", ifElse)
                    } else {
                        if(jumpAfterIf!=null) {
                            val (asmLabel, indirect) = asmgen.getJumpTarget(jumpAfterIf)
                            if(indirect)
                                throw AssemblyError("cannot BIT to indirect label ${ifElse.position}")
                            if(ifElse.hasElse())
                                throw AssemblyError("didn't expect else part here ${ifElse.position}")
                            else
                                asmgen.out("  bpl  $asmLabel")
                        }
                        else
                            translateIfElseBodies("bmi", ifElse)
                    }
                    return
                }
                6 -> {
                    // test via bit + V flag
                    asmgen.out("  bit  ${variable.name}")
                    if(testForBitSet) {
                        if(jumpAfterIf!=null) {
                            val (asmLabel, indirect) = asmgen.getJumpTarget(jumpAfterIf)
                            if(indirect)
                                throw AssemblyError("cannot BIT to indirect label ${ifElse.position}")
                            if(ifElse.hasElse())
                                throw AssemblyError("didn't expect else part here ${ifElse.position}")
                            else
                                asmgen.out("  bvs  $asmLabel")
                        }
                        else
                            translateIfElseBodies("bvc", ifElse)
                    } else {
                        if(jumpAfterIf!=null) {
                            val (asmLabel, indirect) = asmgen.getJumpTarget(jumpAfterIf)
                            if(indirect)
                                throw AssemblyError("cannot BIT to indirect label ${ifElse.position}")
                            if(ifElse.hasElse())
                                throw AssemblyError("didn't expect else part here ${ifElse.position}")
                            else
                                asmgen.out("  bvc  $asmLabel")
                        }
                        else
                            translateIfElseBodies("bvs", ifElse)
                    }
                    return
                }
                else -> throw AssemblyError("prog8_ifelse_bittest can only work on bits 7 and 6")
            }
        }

        // the condition is "simple" enough to just assign its 0/1 value to a register and branch on that
        asmgen.assignConditionValueToRegisterAndTest(ifElse.condition)
        if(jumpAfterIf!=null)
            translateJumpElseBodies("bne", "beq", jumpAfterIf, ifElse.elseScope)
        else
            translateIfElseBodies("beq", ifElse)
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

    private fun translateIfByte(stmt: PtIfElse, jumpAfterIf: PtJump?) {
        val condition = stmt.condition as PtBinaryExpression
        val signed = condition.left.type.isSigned
        val constValue = condition.right.asConstInteger()
        if(constValue==0) {
            return translateIfCompareWithZeroByte(stmt, signed, jumpAfterIf)
        }

        when (condition.operator) {
            "==" -> {
                // if X==value
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
                asmgen.cmpAwithByteValue(condition.right, false)
                return if(jumpAfterIf!=null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            }
            "!=" -> {
                // if X!=value
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
                asmgen.cmpAwithByteValue(condition.right, false)
                return if(jumpAfterIf!=null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
            "<" -> translateByteLess(stmt, signed, jumpAfterIf)
            "<=" -> {
                // X<=Y -> Y>=X (reverse of >=)
                asmgen.assignExpressionToRegister(condition.right, RegisterOrPair.A, signed)
                asmgen.cmpAwithByteValue(condition.left, false)
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
            ">" -> translateByteGreater(stmt, signed, jumpAfterIf)
            ">=" -> {
                asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
                asmgen.cmpAwithByteValue(condition.right, false)
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
            in LogicalOperators -> {
                val regAtarget = AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, DataType.forDt(BaseDataType.BOOL), stmt.definingISub(), condition.position, register=RegisterOrPair.A)
                if (assignmentAsmGen.optimizedLogicalExpr(condition, regAtarget)) {
                    if (jumpAfterIf != null)
                        translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                    else
                        translateIfElseBodies("beq", stmt)
                } else {
                    errors.info("SLOW FALLBACK FOR 'IF' CODEGEN - ask for support", stmt.position)      //  should not occur ;-)
                    asmgen.assignConditionValueToRegisterAndTest(stmt.condition)
                    if(jumpAfterIf!=null)
                        translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                    else
                        translateIfElseBodies("beq", stmt)
                }
            }
            else -> throw AssemblyError("expected comparison or logical operator")
        }
    }

    private fun translateIfCompareWithZeroByte(stmt: PtIfElse, signed: Boolean, jumpAfterIf: PtJump?) {
        // optimized code for byte comparisons with 0
        val condition = stmt.condition as PtBinaryExpression
        asmgen.assignConditionValueToRegisterAndTest(condition.left)
        when (condition.operator) {
            "==" -> {
                return if(jumpAfterIf!=null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            }
            "!=" -> {
                return if(jumpAfterIf!=null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
            ">" -> {
                if(signed) {
                    return if (jumpAfterIf != null) {
                        val (asmLabel, indirect) = asmgen.getJumpTarget(jumpAfterIf)
                        if(indirect) {
                            asmgen.out("""
                                    bmi  +
                                    beq  +
                                    jmp  ($asmLabel)
+""")
                        } else {
                            asmgen.out("""
                                    beq  +
                                    bpl  $asmLabel
+""")
                        }
                        asmgen.translate(stmt.elseScope)
                    }
                    else {
                        val afterIfLabel = asmgen.makeLabel("afterif")
                        if(stmt.hasElse()) {
                            // if and else blocks
                            val elseLabel = asmgen.makeLabel("else")
                            asmgen.out("  bmi  $elseLabel |  beq  $elseLabel")
                            asmgen.translate(stmt.ifScope)
                            asmgen.jmp(afterIfLabel, false)
                            asmgen.out(elseLabel)
                            asmgen.translate(stmt.elseScope)
                        } else {
                            // no else block
                            asmgen.out("  bmi  $afterIfLabel |  beq  $afterIfLabel")
                            asmgen.translate(stmt.ifScope)
                        }
                        asmgen.out(afterIfLabel)
                    }
                } else {
                    return if (jumpAfterIf != null)
                        translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                    else
                        translateIfElseBodies("beq", stmt)
                }
            }
            ">=" -> {
                if(signed) {
                    return if (jumpAfterIf != null)
                        translateJumpElseBodies("bpl", "bmi", jumpAfterIf, stmt.elseScope)
                    else
                        translateIfElseBodies("bmi", stmt)
                } else {
                    // always true for unsigned
                    asmgen.translate(stmt.ifScope)
                }
            }
            "<" -> {
                if(signed) {
                    return if (jumpAfterIf != null)
                        translateJumpElseBodies("bmi", "bpl", jumpAfterIf, stmt.elseScope)
                    else
                        translateIfElseBodies("bpl", stmt)
                } else {
                    // always false for unsigned
                    asmgen.translate(stmt.elseScope)
                }
            }
            "<=" -> {
                if(signed) {
                    // inverted '>'
                    return if (jumpAfterIf != null) {
                        val (asmLabel, indirect) = asmgen.getJumpTarget(jumpAfterIf)
                        if(indirect) {
                            asmgen.out("""
                                    bmi  +
                                    bne  ++
+                                   jmp  ($asmLabel)
+""")
                        } else {
                            asmgen.out("""
                                    bmi  $asmLabel
                                    beq  $asmLabel""")
                        }
                        asmgen.translate(stmt.elseScope)
                    }
                    else {
                        val afterIfLabel = asmgen.makeLabel("afterif")
                        if(stmt.hasElse()) {
                            // if and else blocks
                            val elseLabel = asmgen.makeLabel("else")
                            asmgen.out("""
                                beq  +
                                bpl  $elseLabel
+""")
                            asmgen.translate(stmt.ifScope)
                            asmgen.jmp(afterIfLabel, false)
                            asmgen.out(elseLabel)
                            asmgen.translate(stmt.elseScope)
                        } else {
                            // no else block
                            asmgen.out("""
                                beq  +
                                bpl  $afterIfLabel
+""")
                            asmgen.translate(stmt.ifScope)
                        }
                        asmgen.out(afterIfLabel)
                    }
                } else {
                    return if(jumpAfterIf!=null)
                        translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                    else
                        translateIfElseBodies("bne", stmt)
                }
            }
            else -> throw AssemblyError("expected comparison operator")
        }
    }

    private fun translateByteLess(stmt: PtIfElse, signed: Boolean, jumpAfterIf: PtJump?) {
        val condition = stmt.condition as PtBinaryExpression
        asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
        asmgen.cmpAwithByteValue(condition.right, false)
        if(signed) {
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

    private fun translateByteGreater(stmt: PtIfElse, signed: Boolean, jumpAfterIf: PtJump?) {
        val condition = stmt.condition as PtBinaryExpression
        if(signed) {
            // X>Y --> Y<X
            asmgen.assignExpressionToRegister(condition.right, RegisterOrPair.A, true)
            asmgen.cmpAwithByteValue(condition.left, true)
            if (jumpAfterIf != null)
                translateJumpElseBodies("bmi", "bpl", jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodies("bpl", stmt)
        } else {
            asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, false)
            asmgen.cmpAwithByteValue(condition.right, false)
            if(jumpAfterIf!=null) {
                val (asmLabel, indirect) = asmgen.getJumpTarget(jumpAfterIf)
                if(indirect) {
                    asmgen.out("""
                        bcc  +
                        beq  +
                        jmp  ($asmLabel)
+""")
                } else {
                    asmgen.out("""
                        beq  +
                        bcs  $asmLabel
+""")
                }
                asmgen.translate(stmt.elseScope)
            } else {
                val afterIfLabel = asmgen.makeLabel("afterif")
                if(stmt.hasElse()) {
                    // if and else blocks
                    val elseLabel = asmgen.makeLabel("else")
                    asmgen.out("  bcc  $elseLabel |  beq  $elseLabel")
                    asmgen.translate(stmt.ifScope)
                    asmgen.jmp(afterIfLabel, false)
                    asmgen.out(elseLabel)
                    asmgen.translate(stmt.elseScope)
                } else {
                    // no else block
                    asmgen.out("  bcc  $afterIfLabel |  beq  $afterIfLabel")
                    asmgen.translate(stmt.ifScope)
                }
                asmgen.out(afterIfLabel)
            }
        }
    }

    private fun translateIfWord(stmt: PtIfElse, condition: PtBinaryExpression, jumpAfterIf: PtJump?) {
        val signed = condition.left.type.isSigned
        val constValue = condition.right.asConstInteger()
        if(constValue==0) {
            // optimized comparisons with zero
            return when (condition.operator) {
                "==" -> wordEqualsZero(condition.left, false, signed, jumpAfterIf, stmt)
                "!=" -> wordEqualsZero(condition.left, true, signed, jumpAfterIf, stmt)
                "<" -> wordLessZero(condition.left, signed, jumpAfterIf, stmt)
                "<=" -> wordLessEqualsZero(condition.left, signed, jumpAfterIf, stmt)
                ">" -> wordGreaterZero(condition.left, signed, jumpAfterIf, stmt)
                ">=" -> wordGreaterEqualsZero(condition.left, signed, jumpAfterIf, stmt)
                else -> throw AssemblyError("expected comparison operator")
            }
        }

        // non-zero comparisons
        when(condition.operator) {
            "==" -> wordEqualsValue(condition.left, condition.right, false, signed, jumpAfterIf, stmt)
            "!=" -> wordEqualsValue(condition.left, condition.right, true, signed, jumpAfterIf, stmt)
            "<" -> wordLessValue(condition.left, condition.right, signed, jumpAfterIf, stmt)
            "<=" -> throw AssemblyError("X<=Y should have been replaced by Y>=X")
            ">" -> throw AssemblyError("X>Y should have been replaced by Y<X")
            ">=" -> wordGreaterEqualsValue(condition.left, condition.right, signed, jumpAfterIf, stmt)
            else -> throw AssemblyError("expected comparison operator")
        }
    }

    private fun wordGreaterEqualsZero(value: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {
        // special case for word>=0
        if(signed) {
            loadAndCmp0MSB(value)
            if (jump != null)
                translateJumpElseBodies("bpl", "bmi", jump, stmt.elseScope)
            else
                translateIfElseBodies("bmi", stmt)
        } else {
            asmgen.translate(stmt.ifScope)
        }
    }

    private fun wordLessZero(value: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {
        // special case for word<0
        if(signed) {
            loadAndCmp0MSB(value)
            if (jump != null)
                translateJumpElseBodies("bmi", "bpl", jump, stmt.elseScope)
            else
                translateIfElseBodies("bpl", stmt)
        } else {
            asmgen.translate(stmt.elseScope)
        }
    }

    private fun wordLessValue(left: PtExpression, right: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {
        fun code(valueLsb: String, valueMsb: String) {
            if(signed) {
                // word < X
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bvc  +
                            eor  #128
+                           bpl  +
                            jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bvc  +
                            eor  #128
+                           bmi  $asmLabel""")
                    }
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bvs  +
                            eor  #128
+                           bmi  $elseLabel""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel, false)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bvs  +
                            eor  #128
+                           bmi  $afterIfLabel""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
            } else {
                // uword < X
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            cpy  $valueMsb
                            bcc  _jump
+                           bne  +
                            cmp  $valueLsb
                            bcs  +
_jump                       jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            cpy  $valueMsb
                            bcc  $asmLabel
                            bne  +
                            cmp  $valueLsb
                            bcc  $asmLabel
+""")
                    }
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bcs  $elseLabel""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel, false)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bcs  $afterIfLabel""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
            }
        }

        if(right is PtNumber) {
            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
            val number = right.number.toHex()
            return code("#<$number", "#>$number")
        }

        if(right is PtIdentifier) {
            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
            val variable = asmgen.asmVariableName(right)
            return code(variable, "$variable+1")
        }

        // TODO optimize for simple array value

        // generic case via scratch register
        asmgen.assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")
        code("P8ZP_SCRATCH_W2", "P8ZP_SCRATCH_W2+1")
    }

    private fun wordGreaterEqualsValue(left: PtExpression, right: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {

        fun code(valueLsb: String, valueMsb: String) {
            if(signed) {
                // word >= X
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bvs  +
                            eor  #128
+                           bpl  +
                            jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bvs  +
                            eor  #128
+                           bmi  $asmLabel""")
                    }
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bvc  +
                            eor  #128
+                           bmi  $elseLabel""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel, false)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bvc  +
                            eor  #128
+                           bmi  $afterIfLabel""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
            } else {
                // uword >= X
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bcc  +
                            jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bcs  $asmLabel""")
                    }
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bcc  $elseLabel""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel, false)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bcc  $afterIfLabel""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
            }
        }

        if(right is PtNumber) {
            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
            val number = right.number.toHex()
            return code("#<$number", "#>$number")
        }

        if(right is PtIdentifier) {
            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
            val variable = asmgen.asmVariableName(right)
            return code(variable, "$variable+1")
        }

        // TODO optimize for simple array value

        // generic case via scratch register
        asmgen.assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")
        code("P8ZP_SCRATCH_W2", "P8ZP_SCRATCH_W2+1")
    }


    private fun loadAndCmp0MSB(value: PtExpression) {
        when(value) {
            is PtArrayIndexer -> {
                val varname = asmgen.asmVariableName(value.variable)
                asmgen.loadScaledArrayIndexIntoRegister(value, CpuRegister.Y)
                if(value.splitWords)
                    asmgen.out("  lda  ${varname}_msb,y")
                else
                    asmgen.out("  lda  $varname+1,y")
            }
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(value)
                asmgen.out("  lda  $varname+1")
            }
            is PtAddressOf -> {
                if(value.isFromArrayElement) {
                    asmgen.assignExpressionToRegister(value, RegisterOrPair.AY, true)
                    asmgen.out("  cpy  #0")
                } else {
                    asmgen.out("  lda  #>${asmgen.asmVariableName(value.identifier)}")
                }
            }
            else -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.AY, true)
                asmgen.out("  cpy  #0")
            }
        }
    }

    private fun wordLessEqualsZero(value: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {
        if(signed) {
            // word <= 0

            fun compareLsbMsb(valueLsb: String, valueMsb: String) {
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  +
                            bne  ++
                            lda  $valueLsb
                            bne  ++
+                           jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  $asmLabel
                            bne  +
                            lda  $valueLsb
                            beq  $asmLabel
+""")
                    }
                    asmgen.translate(stmt.elseScope)
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  +
                            bne  $elseLabel
                            lda  $valueLsb
                            bne  $elseLabel
+""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel, false)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  +
                            bne  $afterIfLabel
                            lda  $valueLsb
                            bne  $afterIfLabel
+""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
            }
            
            if(value is PtIdentifier)
                return compareLsbMsb(value.name, value.name+"+1")
            if(value is PtArrayIndexer) {
                val constIndex = value.index.asConstInteger()
                val varname = asmgen.asmVariableName(value.variable)
                if(constIndex!=null) {
                    if(value.splitWords) {
                        return compareLsbMsb("${varname}_lsb+$constIndex", "${varname}_msb+$constIndex")
                    } else {
                        val offset = program.memsizer.memorySize(value.type, constIndex)
                        return compareLsbMsb("$varname+$offset", "$varname+$offset+1")
                    }
                } else {
                    asmgen.loadScaledArrayIndexIntoRegister(value, CpuRegister.Y)
                    return if(value.splitWords) {
                        compareLsbMsb("${varname}_lsb,y", "${varname}_msb,y")
                    } else {
                        compareLsbMsb("$varname,y", "$varname+1,y")
                    }
                }
            }
                
            asmgen.assignExpressionToRegister(value, RegisterOrPair.AY, true)
            if(jump!=null) {
                val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                if(indirect) {
                    asmgen.out("""
                        cpy  #0
                        bmi  +
                        bne  ++
                        cmp  #0
                        bne  ++
+                       jmp  ($asmLabel)
+""")
                } else {
                    asmgen.out("""
                        cpy  #0
                        bmi  $asmLabel
                        bne  +
                        cmp  #0
                        beq  $asmLabel
+""")
                }
                asmgen.translate(stmt.elseScope)
            } else {
                val afterIfLabel = asmgen.makeLabel("afterif")
                if(stmt.hasElse()) {
                    // if and else blocks
                    val elseLabel = asmgen.makeLabel("else")
                    asmgen.out("""
                            cpy  #0
                            bmi  +
                            bne  $elseLabel
                            cmp  #0
                            bne  $elseLabel
+""")
                    asmgen.translate(stmt.ifScope)
                    asmgen.jmp(afterIfLabel, false)
                    asmgen.out(elseLabel)
                    asmgen.translate(stmt.elseScope)
                } else {
                    // no else block
                    asmgen.out("""
                            cpy  #0
                            bmi  +
                            bne  $afterIfLabel
                            cmp  #0
                            bne  $afterIfLabel
+""")
                    asmgen.translate(stmt.ifScope)
                }
                asmgen.out(afterIfLabel)
            }
        } else {
            // uword <= 0 --> uword == 0
            wordEqualsZero(value, true, false, jump, stmt)
        }
    }

    private fun wordGreaterZero(value: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {
        if(signed) {
            // word > 0

            fun compareLsbMsb(valueLsb: String, valueMsb: String) {
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  ++
                            bne  +
                            lda  $valueLsb
                            beq  ++
+                           jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  +
                            bne  $asmLabel
                            lda  $valueLsb
                            bne  $asmLabel
+""")
                    }
                    asmgen.translate(stmt.elseScope)
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  $elseLabel
                            bne  +
                            lda  $valueLsb
                            beq  $elseLabel
+""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel, false)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  $afterIfLabel
                            bne  +
                            lda  $valueLsb
                            beq  $afterIfLabel
+""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
                return
            }

            if(value is PtIdentifier)
                return compareLsbMsb(value.name, value.name+"+1")
            if(value is PtArrayIndexer) {
                val constIndex = value.index.asConstInteger()
                val varname = asmgen.asmVariableName(value.variable)
                if(constIndex!=null) {
                    if(value.splitWords) {
                        return compareLsbMsb("${varname}_lsb+$constIndex", "${varname}_msb+$constIndex")
                    } else {
                        val offset = program.memsizer.memorySize(value.type, constIndex)
                        return compareLsbMsb("$varname+$offset", "$varname+$offset+1")
                    }
                } else {
                    asmgen.loadScaledArrayIndexIntoRegister(value, CpuRegister.Y)
                    return if(value.splitWords) {
                        compareLsbMsb("${varname}_lsb,y", "${varname}_msb,y")
                    } else {
                        compareLsbMsb("$varname,y", "$varname+1,y")
                    }
                }
            }

            asmgen.assignExpressionToRegister(value, RegisterOrPair.AY, true)
            if(jump!=null) {
                val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                if(indirect) {
                    asmgen.out("""
                            cpy  #0
                            bmi  ++
                            bne  +
                            cmp  #0
                            beq  ++
+                           jmp  ($asmLabel)
+""")
                } else {
                    asmgen.out("""
                            cpy  #0
                            bmi  +
                            bne  $asmLabel
                            cmp  #0
                            bne  $asmLabel
+""")
                }
                asmgen.translate(stmt.elseScope)
            } else {
                val afterIfLabel = asmgen.makeLabel("afterif")
                if(stmt.hasElse()) {
                    // if and else blocks
                    val elseLabel = asmgen.makeLabel("else")
                    asmgen.out("""
                            cpy  #0
                            bmi  $elseLabel
                            bne  +
                            cmp  #0
                            beq  $elseLabel
+""")
                    asmgen.translate(stmt.ifScope)
                    asmgen.jmp(afterIfLabel, false)
                    asmgen.out(elseLabel)
                    asmgen.translate(stmt.elseScope)
                } else {
                    // no else block
                    asmgen.out("""
                            cpy  #0
                            bmi  $afterIfLabel
                            bne  +
                            cmp  #0
                            beq  $afterIfLabel
+""")
                    asmgen.translate(stmt.ifScope)
                }
                asmgen.out(afterIfLabel)
            }
        } else {
            // uword > 0 --> uword != 0
            wordEqualsZero(value, true, false, jump, stmt)
        }
    }

    private fun wordEqualsZero(value: PtExpression, notEquals: Boolean, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {

        // special case for (u)word == 0

        fun viaScratchReg(branchInstr: String, falseBranch: String) {
            asmgen.assignExpressionToRegister(value, RegisterOrPair.AY, signed)
            asmgen.out("  sty  P8ZP_SCRATCH_REG |  ora  P8ZP_SCRATCH_REG")
            if(jump!=null)
                translateJumpElseBodies(branchInstr, falseBranch, jump, stmt.elseScope)
            else
                translateIfElseBodies(falseBranch, stmt)
        }

        fun translateLoadFromVar(variable: String, branch: String, falseBranch: String) {
            asmgen.out("  lda  $variable |  ora  $variable+1")
            return if(jump!=null)
                translateJumpElseBodies(branch, falseBranch, jump, stmt.elseScope)
            else
                translateIfElseBodies(falseBranch, stmt)
        }
        fun translateLoadFromVarSplitw(variable: String, constIndex: Int, branch: String, falseBranch: String) {
            asmgen.out("  lda  ${variable}_lsb+$constIndex |  ora  ${variable}_msb+$constIndex")
            return if(jump!=null)
                translateJumpElseBodies(branch, falseBranch, jump, stmt.elseScope)
            else
                translateIfElseBodies(falseBranch, stmt)
        }

        if(notEquals) {
            when(value) {
                is PtArrayIndexer -> {
                    val constIndex = value.index.asConstInteger()
                    if(constIndex!=null) {
                        val varName = asmgen.asmVariableName(value.variable)
                        if(value.splitWords) {
                            return translateLoadFromVarSplitw(varName, constIndex, "bne", "beq")
                        }
                        val offset = program.memsizer.memorySize(value.type, constIndex)
                        if (offset < 256) {
                            return translateLoadFromVar("$varName+$offset", "bne", "beq")
                        }
                    }
                    viaScratchReg("bne", "beq")
                }
                is PtIdentifier -> {
                    return translateLoadFromVar(asmgen.asmVariableName(value), "bne", "beq")
                }
                else -> viaScratchReg("bne", "beq")
            }
        } else {
            when (value) {
                is PtArrayIndexer -> {
                    val constIndex = value.index.asConstInteger()
                    if (constIndex != null) {
                        val varName = asmgen.asmVariableName(value.variable)
                        if(value.splitWords) {
                            return translateLoadFromVarSplitw(varName, constIndex, "beq", "bne")
                        }
                        val offset = program.memsizer.memorySize(value.type, constIndex)
                        if (offset < 256) {
                            return translateLoadFromVar("$varName+$offset", "beq", "bne")
                        }
                    }
                    viaScratchReg("beq", "bne")
                }
                is PtIdentifier -> {
                    return translateLoadFromVar(asmgen.asmVariableName(value), "beq", "bne")
                }
                else -> viaScratchReg("beq", "bne")
            }
        }
    }

    private fun wordEqualsValue(
        left: PtExpression,
        right: PtExpression,
        notEquals: Boolean,
        signed: Boolean,
        jump: PtJump?,
        stmt: PtIfElse
    ) {
        // special case for (u)word == X  and (u)word != X

        fun translateAYNotEquals(valueLsb: String, valueMsb: String) {
            if(jump!=null) {
                val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                if(indirect) {
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  +
                        cpy  $valueMsb
                        beq  ++
+                       jmp  ($asmLabel)
+""")
                } else {
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  $asmLabel
                        cpy  $valueMsb
                        bne  $asmLabel""")
                }
                asmgen.translate(stmt.elseScope)
            } else {
                val afterIfLabel = asmgen.makeLabel("afterif")
                if(stmt.hasElse()) {
                    // if and else blocks
                    val elseLabel = asmgen.makeLabel("else")
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  +
                        cpy  $valueMsb
                        beq  $elseLabel
+""")
                    asmgen.translate(stmt.ifScope)
                    asmgen.jmp(afterIfLabel, false)
                    asmgen.out(elseLabel)
                    asmgen.translate(stmt.elseScope)
                } else {
                    // no else block
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  +
                        cpy  $valueMsb
                        beq  $afterIfLabel
+""")
                    asmgen.translate(stmt.ifScope)
                }
                asmgen.out(afterIfLabel)
            }
        }

        fun translateAYEquals(valueLsb: String, valueMsb: String) {
            if(jump!=null) {
                val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                if(indirect) {
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  +
                        cpy  $valueMsb
                        bne  +
                        jmp  ($asmLabel)
+""")
                } else {
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  +
                        cpy  $valueMsb
                        beq  $asmLabel
+""")
                }
                asmgen.translate(stmt.elseScope)
            } else {
                val afterIfLabel = asmgen.makeLabel("afterif")
                if(stmt.hasElse()) {
                    // if and else blocks
                    val elseLabel = asmgen.makeLabel("else")
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  $elseLabel
                        cpy  $valueMsb
                        bne  $elseLabel""")
                    asmgen.translate(stmt.ifScope)
                    asmgen.jmp(afterIfLabel, false)
                    asmgen.out(elseLabel)
                    asmgen.translate(stmt.elseScope)
                } else {
                    // no else block
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  $afterIfLabel
                        cpy  $valueMsb
                        bne  $afterIfLabel""")
                    asmgen.translate(stmt.ifScope)
                }
                asmgen.out(afterIfLabel)
            }
        }

        fun translateEqualsVarVar(left: PtIdentifier, right: PtIdentifier) {
            if(notEquals) {
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  +
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
                            beq  ++
+                           jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  $asmLabel
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
                            bne  $asmLabel""")
                    }
                    asmgen.translate(stmt.elseScope)
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  +
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
                            beq  $elseLabel
+""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel, false)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  +
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
                            beq  $afterIfLabel
+""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
            } else {
                // XXX translateAYEquals(right.name, right.name + "+1")
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  +
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
                            bne  +
                            jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  +
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
                            beq  $asmLabel
+""")
                    }
                    asmgen.translate(stmt.elseScope)
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  $elseLabel
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
                            bne  $elseLabel""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel, false)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  $afterIfLabel
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
                            bne  $afterIfLabel""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
            }
        }

        fun translateEqualsVarNum(left: PtIdentifier, right: PtNumber) {
            val value = right.number.toInt()
            if(notEquals) {
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  #<$value
                            bne  +
                            lda  ${left.name}+1
                            cmp  #>$value
                            beq  ++
+                           jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  #<$value
                            bne  $asmLabel
                            lda  ${left.name}+1
                            cmp  #>$value
                            bne  $asmLabel""")
                    }
                    asmgen.translate(stmt.elseScope)
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  #<$value
                            bne  +
                            lda  ${left.name}+1
                            cmp  #>$value
                            beq  $elseLabel
+""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel, false)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  #<$value
                            bne  +
                            lda  ${left.name}+1
                            cmp  #>$value
                            beq  $afterIfLabel
+""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
            } else {
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  #<$value
                            bne  +
                            lda  ${left.name}+1
                            cmp  #>$value
                            bne  +
                            jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  #<$value
                            bne  +
                            lda  ${left.name}+1
                            cmp  #>$value
                            beq  $asmLabel
+""")
                    }
                    asmgen.translate(stmt.elseScope)
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  #<$value
                            bne  $elseLabel
                            lda  ${left.name}+1
                            cmp  #>$value
                            bne  $elseLabel""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel, false)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  #<$value
                            bne  $afterIfLabel
                            lda  ${left.name}+1
                            cmp  #>$value
                            bne  $afterIfLabel""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
            }
        }

        fun translateEqualsArray(left: PtArrayIndexer, right: PtExpression) {
            val constIndex = left.index.asConstInteger()
            if(constIndex!=null) {
                asmgen.assignExpressionToRegister(right, RegisterOrPair.AY, signed)
                val varName = asmgen.asmVariableName(left.variable)
                if(left.splitWords) {
                    return if(notEquals)
                        translateAYNotEquals("${varName}_lsb+$constIndex", "${varName}_msb+$constIndex")
                    else
                        translateAYEquals("${varName}_lsb+$constIndex", "${varName}_msb+$constIndex")
                }
                val offset = program.memsizer.memorySize(left.type, constIndex)
                if(offset<256) {
                    return if(notEquals)
                        translateAYNotEquals("$varName+$offset", "$varName+$offset+1")
                    else
                        translateAYEquals("$varName+$offset", "$varName+$offset+1")
                }
            }
            else return when(right) {
                is PtNumber -> {
                    asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
                    val value = right.number.toInt()
                    if(notEquals)
                        translateAYNotEquals("#<$value", "#>$value")
                    else
                        translateAYEquals("#<$value", "#>$value")
                }
                is PtIdentifier -> {
                    asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
                    if(notEquals)
                        translateAYNotEquals(right.name,right.name + "+1")
                    else
                        translateAYEquals(right.name, right.name + "+1")
                }
                else -> {
                    asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
                    asmgen.saveRegisterStack(CpuRegister.A, false)
                    asmgen.saveRegisterStack(CpuRegister.Y, false)
                    asmgen.assignExpressionToVariable(right,"P8ZP_SCRATCH_W1", right.type)
                    asmgen.restoreRegisterStack(CpuRegister.Y, false)
                    asmgen.restoreRegisterStack(CpuRegister.A, false)
                    if(notEquals)
                        translateAYNotEquals("P8ZP_SCRATCH_W1", "P8ZP_SCRATCH_W1+1")
                    else
                        translateAYNotEquals("P8ZP_SCRATCH_W1", "P8ZP_SCRATCH_W1+1")
                }
            }
        }

        if(notEquals) {
            when(left) {
                is PtArrayIndexer -> {
                    translateEqualsArray(left, right)
                }
                is PtIdentifier -> {
                    when(right) {
                        is PtNumber -> translateEqualsVarNum(left, right)
                        is PtIdentifier -> translateEqualsVarVar(left, right)
                        else -> {
                            asmgen.assignExpressionToRegister(right, RegisterOrPair.AY, signed)
                            val varname = asmgen.asmVariableName(left)
                            translateAYNotEquals(varname, "$varname+1")
                        }
                    }
                }
                is PtAddressOf -> {
                    if(left.isFromArrayElement)
                        fallbackTranslateForSimpleCondition(stmt)
                    else {
                        asmgen.assignExpressionToRegister(right, RegisterOrPair.AY, signed)
                        val varname = left.identifier.name
                        translateAYNotEquals("#<$varname", "#>$varname")
                    }
                }
                else -> {
                    when(right) {
                        is PtNumber -> {
                            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
                            val value = right.number.toInt()
                            translateAYNotEquals("#<$value", "#>$value")
                        }
                        is PtIdentifier -> {
                            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
                            translateAYNotEquals(right.name, right.name + "+1")
                        }
                        else -> {
                            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
                            asmgen.saveRegisterStack(CpuRegister.A, false)
                            asmgen.saveRegisterStack(CpuRegister.Y, false)
                            asmgen.assignExpressionToVariable(right,"P8ZP_SCRATCH_W1", right.type)
                            asmgen.restoreRegisterStack(CpuRegister.Y, false)
                            asmgen.restoreRegisterStack(CpuRegister.A, false)
                            translateAYNotEquals("P8ZP_SCRATCH_W1", "P8ZP_SCRATCH_W1+1")
                        }
                    }
                }
            }
        } else {
            when(left) {
                is PtArrayIndexer -> {
                    translateEqualsArray(left, right)
                }
                is PtIdentifier -> {
                    when(right) {
                        is PtNumber -> translateEqualsVarNum(left, right)
                        is PtIdentifier -> translateEqualsVarVar(left, right)
                        else -> {
                            asmgen.assignExpressionToRegister(right, RegisterOrPair.AY, signed)
                            val varname = asmgen.asmVariableName(left)
                            translateAYEquals(varname, "$varname+1")
                        }
                    }
                }
                is PtAddressOf -> {
                    if(left.isFromArrayElement)
                        fallbackTranslateForSimpleCondition(stmt)
                    else {
                        asmgen.assignExpressionToRegister(right, RegisterOrPair.AY, signed)
                        val varname = left.identifier.name
                        translateAYEquals("#<$varname", "#>$varname")
                    }
                }
                else -> {
                    when(right) {
                        is PtNumber -> {
                            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
                            val value = right.number.toInt()
                            translateAYEquals("#<$value", "#>$value")
                        }
                        is PtIdentifier -> {
                            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
                            translateAYEquals(right.name, right.name + "+1")
                        }
                        else -> {
                            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
                            asmgen.saveRegisterStack(CpuRegister.A, false)
                            asmgen.saveRegisterStack(CpuRegister.Y, false)
                            asmgen.assignExpressionToVariable(right,"P8ZP_SCRATCH_W1", right.type)
                            asmgen.restoreRegisterStack(CpuRegister.Y, false)
                            asmgen.restoreRegisterStack(CpuRegister.A, false)
                            translateAYEquals("P8ZP_SCRATCH_W1", "P8ZP_SCRATCH_W1+1")
                        }
                    }
                }
            }
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
                asmgen.translateFloatsEqualsConditionIntoA(condition.left, condition.right)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
            "!=" -> {
                asmgen.translateFloatsEqualsConditionIntoA(condition.left, condition.right)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            }
            "<" -> {
                asmgen.translateFloatsLessConditionIntoA(condition.left, condition.right, false)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
            "<=" -> {
                asmgen.translateFloatsLessConditionIntoA(condition.left, condition.right, true)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
            ">" -> {
                asmgen.translateFloatsLessConditionIntoA(condition.left, condition.right, true)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            }
            ">=" -> {
                asmgen.translateFloatsLessConditionIntoA(condition.left, condition.right, false)
                return if (jumpAfterIf != null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            }
            else -> throw AssemblyError("expected comparison operator")
        }
    }
}