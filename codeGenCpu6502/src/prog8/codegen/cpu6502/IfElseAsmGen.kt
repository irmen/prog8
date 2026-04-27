package prog8.codegen.cpu6502

import prog8.code.StExtSub
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.assignment.AsmAssignTarget
import prog8.codegen.cpu6502.assignment.AssignmentAsmGen
import prog8.codegen.cpu6502.assignment.PointerAssignmentsGen
import prog8.codegen.cpu6502.assignment.TargetStorageKind

internal class IfElseAsmGen(private val program: PtProgram,
                            private val st: SymbolTable,
                            private val asmgen: AsmGen6502Internal,
                            private val pointergen: PointerAssignmentsGen,
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
            stmt.condition is PtFunctionCall ||
            stmt.condition is PtMemoryByte ||
            stmt.condition is PtContainmentCheck)
                return fallbackTranslateForSimpleCondition(stmt)

        val compareCond = stmt.condition as? PtBinaryExpression
        if(compareCond!=null) {

            val useBIT = asmgen.checkIfConditionCanUseBIT(compareCond)
            if(useBIT!=null) {
                val (testBitSet, variableName, bitmask) = useBIT
                return translateIfBIT(stmt, jumpAfterIf, testBitSet, variableName, bitmask)
            }

            val rightDt = compareCond.right.type
            return when {
                rightDt.isByteOrBool -> translateIfByte(stmt, jumpAfterIf)
                rightDt.isWord || rightDt.isPointer -> translateIfWord(stmt, compareCond, jumpAfterIf)
                rightDt.isLong -> translateIfLong(stmt, compareCond, jumpAfterIf)
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

        val dereference = stmt.condition as? PtPointerDeref
        if(dereference!=null) {
            val (zpPtrVar, offset) = pointergen.deref(dereference)
            asmgen.loadIndirectByte(zpPtrVar, offset)
            return if (jumpAfterIf != null)
                translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodies("beq", stmt)
        }

        throw AssemblyError("weird non-boolean condition node type ${stmt.condition} at ${stmt.condition.position}")
    }

    private fun translateIfBIT(ifElse: PtIfElse, jumpAfterIf: PtJump?, testForBitSet: Boolean, variableName: String, bitmask: Int) {
        // use a BIT instruction to test for bit 7 or 6 set/clear

        fun branch(branchInstr: String, target: AsmGen6502Internal.JumpTarget) {
            require(!target.needsExpressionEvaluation)
            if(target.indirect)
                throw AssemblyError("cannot BIT to indirect label ${ifElse.position}")
            if(ifElse.hasElse())
                throw AssemblyError("didn't expect else part here ${ifElse.position}")
            else
                asmgen.out("  $branchInstr  ${target.asmLabel}")
        }

        when (bitmask) {
            128 -> {
                // test via bit + N flag
                asmgen.out("  bit  $variableName")
                if(testForBitSet) {
                    if(jumpAfterIf!=null) {
                        val target = asmgen.getJumpTarget(jumpAfterIf)
                        require(!target.indexedX)
                        branch("bmi", target)
                    }
                    else
                        translateIfElseBodies("bpl", ifElse)
                } else {
                    if(jumpAfterIf!=null) {
                        val target = asmgen.getJumpTarget(jumpAfterIf)
                        require(!target.indexedX)
                        branch("bpl", target)
                    }
                    else
                        translateIfElseBodies("bmi", ifElse)
                }
                return
            }
            64 -> {
                // test via bit + V flag
                asmgen.out("  bit  $variableName")
                if(testForBitSet) {
                    if(jumpAfterIf!=null) {
                        val target = asmgen.getJumpTarget(jumpAfterIf)
                        require(!target.indexedX)
                        branch("bvs", target)
                    }
                    else
                        translateIfElseBodies("bvc", ifElse)
                } else {
                    if(jumpAfterIf!=null) {
                        val target = asmgen.getJumpTarget(jumpAfterIf)
                        require(!target.indexedX)
                        branch("bvc", target)
                    }
                    else
                        translateIfElseBodies("bvs", ifElse)
                }
                return
            }
            else -> throw AssemblyError("BIT only works for bits 6 and 7")
        }
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
        val jumpAfterIf = ifElse.ifScope.children.singleOrNull() as? PtJump

        // the condition is "simple" enough to just assign its 0/1 value to a register and branch on that
        asmgen.assignConditionValueToRegisterAndTest(ifElse.condition)
        if(jumpAfterIf!=null)
            translateJumpElseBodies("bne", "beq", jumpAfterIf, ifElse.elseScope)
        else
            translateIfElseBodies("beq", ifElse)
    }

    private fun translateIfElseBodiesSignedByte(elseConditional: String, value: PtExpression, stmt: PtIfElse) {
        fun branchElse(label: String) {
            when (elseConditional) {
                "<" -> {
                    asmgen.out("""
                        bvc  +
                        eor  #$80
+                       bpl  $label""")
                }
                ">=" -> {
                    asmgen.out("""
                        bvc  +
                        eor  #$80
+                       bmi  $label""")
                }
                else -> throw AssemblyError("wrong conditional $elseConditional")
            }
        }
        val afterIfLabel = asmgen.makeLabel("afterif")
        asmgen.cmpAwithByteValue(value, true)
        if(stmt.hasElse()) {
            // if and else blocks
            val elseLabel = asmgen.makeLabel("else")
            branchElse(elseLabel)
            asmgen.translate(stmt.ifScope)
            asmgen.jmp(afterIfLabel)
            asmgen.out(elseLabel)
            asmgen.translate(stmt.elseScope)
        } else {
            // no else block
            branchElse(afterIfLabel)
            asmgen.translate(stmt.ifScope)
        }
        asmgen.out(afterIfLabel)
    }

    private fun translateJumpElseBodiesSignedByte(elseConditional: String, value: PtExpression, jump: PtJump, elseBlock: PtNodeGroup) {
        fun branchTarget(label: String) {
            when (elseConditional) {
                "<" -> {
                    asmgen.out("""
                        bvc  +
                        eor  #$80
+                       bmi  $label""")
                }
                ">=" -> {
                    asmgen.out("""
                        bvc  +
                        eor  #$80
+                       bpl  $label""")
                }
                else -> throw AssemblyError("wrong conditional $elseConditional")
            }
        }
        fun branchElse(label: String) {
            when (elseConditional) {
                "<" -> {
                    asmgen.out("""
                        bvc  +
                        eor  #$80
+                       bpl  $label""")
                }
                ">=" -> {
                    asmgen.out("""
                        bvc  +
                        eor  #$80
+                       bmi  $label""")
                }
                else -> throw AssemblyError("wrong conditional $elseConditional")
            }
        }

        var target = asmgen.getJumpTarget(jump, false)
        asmgen.cmpAwithByteValue(value, true)
        if(target.indirect) {
            branchElse("+")
            if(target.needsExpressionEvaluation)
                target = asmgen.getJumpTarget(jump)
            asmgen.jmp(target.asmLabel, target.indirect, target.indexedX)
            asmgen.out("+")
        } else {
            require(!target.needsExpressionEvaluation)
            branchTarget(target.asmLabel)
        }
        asmgen.translate(elseBlock)
    }

    private fun translateIfElseBodies(elseBranchInstr: String, stmt: PtIfElse) {
        // comparison value is already in A
        val afterIfLabel = asmgen.makeLabel("afterif")
        if(stmt.hasElse()) {
            // if and else blocks
            val elseLabel = asmgen.makeLabel("else")
            asmgen.out("  $elseBranchInstr  $elseLabel")
            asmgen.translate(stmt.ifScope)
            asmgen.jmp(afterIfLabel)
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
        var target = asmgen.getJumpTarget(jump, false)
        if(target.indirect) {
            asmgen.out("  $falseBranch  +")
            if(target.needsExpressionEvaluation)
                target = asmgen.getJumpTarget(jump)
            asmgen.jmp(target.asmLabel, target.indirect, target.indexedX)
            asmgen.out("+")
        } else {
            require(!target.needsExpressionEvaluation)
            asmgen.out("  $branchInstr  ${target.asmLabel}")
        }
        asmgen.translate(elseBlock)
    }

    private fun translateIfByte(stmt: PtIfElse, jumpAfterIf: PtJump?) {
        val condition = stmt.condition as PtBinaryExpression
        val signed = condition.left.type.isSigned
        
        // Check for logical operators first before checking for constant values
        if (condition.operator in LogicalOperators) {
            val regAtarget = AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, DataType.BOOL, stmt.definingISub(), condition.position, register=RegisterOrPair.A)
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
            return
        }
        
        val constValue = condition.right.asConstInteger()
        if(constValue==0) {
            return translateIfCompareWithZeroByte(stmt, signed, jumpAfterIf)
        }

        val (left, right, operator) = swapOperandsIfSimpler(condition.left, condition.right, condition.operator)

        when (operator) {
            "==" -> {
                // if X==value
                asmgen.assignExpressionToRegister(left, RegisterOrPair.A, signed)
                asmgen.cmpAwithByteValue(right, false)
                return if(jumpAfterIf!=null)
                    translateJumpElseBodies("beq", "bne", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("bne", stmt)
            }
            "!=" -> {
                // if X!=value
                asmgen.assignExpressionToRegister(left, RegisterOrPair.A, signed)
                asmgen.cmpAwithByteValue(right, false)
                return if(jumpAfterIf!=null)
                    translateJumpElseBodies("bne", "beq", jumpAfterIf, stmt.elseScope)
                else
                    translateIfElseBodies("beq", stmt)
            }
            "<" -> translateByteLess(left, right, signed, stmt, jumpAfterIf)
            "<=" -> translateByteLessEqual(left, right, signed, stmt, jumpAfterIf)
            ">" -> translateByteGreater(left, right, signed, stmt, jumpAfterIf)
            ">=" -> translateByteGreaterEqual(left, right, signed, stmt, jumpAfterIf)
            else -> throw AssemblyError("expected comparison operator '${operator}' at ${stmt.position}")
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
                        var target = asmgen.getJumpTarget(jumpAfterIf, false)
                        if(target.indirect) {
                            asmgen.out("  bmi  + |  beq +")
                            if(target.needsExpressionEvaluation)
                                target = asmgen.getJumpTarget(jumpAfterIf)
                            require(!target.indexedX)
                            asmgen.out("""
                                jmp  (${target.asmLabel})
+""")
                        } else {
                            require(!target.needsExpressionEvaluation)
                            asmgen.out("""
                                    beq  +
                                    bpl  ${target.asmLabel}
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
                            asmgen.jmp(afterIfLabel)
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
                        var target = asmgen.getJumpTarget(jumpAfterIf, false)
                        if(target.indirect) {
                            asmgen.out("  bmi  + |  bne  ++")
                            if(target.needsExpressionEvaluation)
                                target = asmgen.getJumpTarget(jumpAfterIf)
                            require(!target.indexedX)
                            asmgen.out("""
+                               jmp  (${target.asmLabel})
+""")
                        } else {
                            require(!target.needsExpressionEvaluation)
                            asmgen.out("""
                                    bmi  ${target.asmLabel}
                                    beq  ${target.asmLabel}""")
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
                            asmgen.jmp(afterIfLabel)
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
            else -> throw AssemblyError("expected comparison operator in 'if' condition, got '${condition.operator}' at ${stmt.position}")
        }
    }

    private fun translateByteLess(left: PtExpression, right: PtExpression, signed: Boolean, stmt: PtIfElse, jumpAfterIf: PtJump?) {
        asmgen.assignExpressionToRegister(left, RegisterOrPair.A, signed)
        if(signed) {
            if(jumpAfterIf!=null)
                translateJumpElseBodiesSignedByte("<", right, jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodiesSignedByte("<", right, stmt)
        } else {
            asmgen.cmpAwithByteValue(right, false)
            if(jumpAfterIf!=null)
                translateJumpElseBodies("bcc", "bcs", jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodies("bcs", stmt)
        }
    }

    private fun translateByteLessEqual(left: PtExpression, right: PtExpression, signed: Boolean, stmt: PtIfElse, jumpAfterIf: PtJump?) {
        // X<=Y -> Y>=X (reverse of >=)
        asmgen.assignExpressionToRegister(right, RegisterOrPair.A, signed)
        return if(signed) {
            if(jumpAfterIf!=null)
                translateJumpElseBodiesSignedByte(">=", left, jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodiesSignedByte(">=", left, stmt)
        } else {
            asmgen.cmpAwithByteValue(left, false)
            if(jumpAfterIf!=null)
                translateJumpElseBodies("bcs", "bcc", jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodies("bcc", stmt)
        }
    }

    private fun translateByteGreater(left: PtExpression, right: PtExpression, signed: Boolean, stmt: PtIfElse, jumpAfterIf: PtJump?) {
        if(signed) {
            // X>Y --> Y<X
            asmgen.assignExpressionToRegister(right, RegisterOrPair.A, true)
            if (jumpAfterIf != null)
                translateJumpElseBodiesSignedByte("<", left, jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodiesSignedByte("<", left, stmt)
        } else {
            asmgen.assignExpressionToRegister(left, RegisterOrPair.A)
            asmgen.cmpAwithByteValue(right, false)
            if(jumpAfterIf!=null) {
                var target = asmgen.getJumpTarget(jumpAfterIf, false)
                if(target.indirect) {
                    asmgen.out("  bcc  + |  beq  +")
                    if(target.needsExpressionEvaluation)
                        target = asmgen.getJumpTarget(jumpAfterIf)
                    require(!target.indexedX)
                    asmgen.out("""
                        jmp  (${target.asmLabel})
+""")
                } else {
                    require(!target.needsExpressionEvaluation)
                    asmgen.out("""
                        beq  +
                        bcs  ${target.asmLabel}
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
                    asmgen.jmp(afterIfLabel)
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

    private fun translateByteGreaterEqual(left: PtExpression, right: PtExpression, signed: Boolean, stmt: PtIfElse, jumpAfterIf: PtJump?) {
        asmgen.assignExpressionToRegister(left, RegisterOrPair.A, signed)
        return if(signed) {
            if(jumpAfterIf!=null)
                translateJumpElseBodiesSignedByte(">=", right, jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodiesSignedByte(">=", right, stmt)
        } else {
            asmgen.cmpAwithByteValue(right, false)
            if(jumpAfterIf!=null)
                translateJumpElseBodies("bcs", "bcc", jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodies("bcc", stmt)
        }
    }

    private fun translateIfLong(stmt: PtIfElse, condition: PtBinaryExpression, jumpAfterIf: PtJump?) {
        val constValue = condition.right.asConstInteger()
        if(constValue==0) {
            // optimized comparisons with zero
            return when (condition.operator) {
                "==" -> longEqualsZero(condition.left, false, jumpAfterIf, stmt)
                "!=" -> longEqualsZero(condition.left, true, jumpAfterIf, stmt)
                "<" -> longLessZero(condition.left, false, jumpAfterIf, stmt)
                "<=" -> longLessZero(condition.left, true, jumpAfterIf, stmt)
                ">" -> longGreaterZero(condition.left, false, jumpAfterIf, stmt)
                ">=" -> longGreaterZero(condition.left, true, jumpAfterIf, stmt)
                else -> throw AssemblyError("expected comparison operator for long, got '${condition.operator}' at ${stmt.position}")
            }
        }

        val (left, right, operator) = swapOperandsIfSimpler(condition.left, condition.right, condition.operator)

        return when (operator) {
            "==" -> longEqualsValue(left, right, false, jumpAfterIf, stmt)
            "!=" -> longEqualsValue(left, right, true, jumpAfterIf, stmt)
            "<", "<=", ">", ">=" -> compareLongValues(left, operator, right, jumpAfterIf, stmt)
            else -> throw AssemblyError("expected comparison operator for long, got '${operator}' at ${stmt.position}")
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
                else -> throw AssemblyError("expected comparison operator for word, got '${condition.operator}' at ${stmt.position}")
            }
        }

        val (left, right, operator) = swapOperandsIfSimpler(condition.left, condition.right, condition.operator)

        // non-zero comparisons
        when(operator) {
            "==" -> wordEqualsValue(left, right, false, signed, jumpAfterIf, stmt)
            "!=" -> wordEqualsValue(left, right, true, signed, jumpAfterIf, stmt)
            "<" -> wordLessValue(left, right, signed, jumpAfterIf, stmt)
            ">=" -> wordGreaterEqualsValue(left, right, signed, jumpAfterIf, stmt)
            ">" -> {
                // X > Y - swap operands to use < with reversed sense
                wordLessValue(right, left, signed, jumpAfterIf, stmt)
            }
            "<=" -> {
                // X <= Y - use >= with swapped operands
                wordGreaterEqualsValue(right, left, signed, jumpAfterIf, stmt)
            }
            else -> throw AssemblyError("expected comparison operator for word, got '${operator}' at ${stmt.position}")
        }
    }

    private fun wordGreaterEqualsZero(value: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {
        // special case for word>=0
        if(signed) {
            loadAndCmp0MSB(value, false)
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
            loadAndCmp0MSB(value, false)
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
                    var target = asmgen.getJumpTarget(jump, false)
                    if(target.indirect) {
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bvc  +
                            eor  #128
+                           bpl  +""")
                        if(target.needsExpressionEvaluation)
                            target = asmgen.getJumpTarget(jump)
                        require(!target.indexedX)
                        asmgen.out("""
                            jmp  (${target.asmLabel})
+""")
                    } else {
                        require(!target.needsExpressionEvaluation)
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bvc  +
                            eor  #128
+                           bmi  ${target.asmLabel}""")
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
                        asmgen.jmp(afterIfLabel)
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
                    var target = asmgen.getJumpTarget(jump, false)
                    if(target.indirect) {
                        asmgen.out("""
                            cpy  $valueMsb
                            bcc  _jump
+                           bne  +
                            cmp  $valueLsb
                            bcs  +""")
                        if(target.needsExpressionEvaluation)
                            target = asmgen.getJumpTarget(jump)
                        require(!target.indexedX)
                        asmgen.out("""
_jump                       jmp  (${target.asmLabel})
+""")
                    } else {
                        require(!target.needsExpressionEvaluation)
                        asmgen.out("""
                            cpy  $valueMsb
                            bcc  ${target.asmLabel}
                            bne  +
                            cmp  $valueLsb
                            bcc  ${target.asmLabel}
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
                        asmgen.jmp(afterIfLabel)
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

        val varR = asmgen.tryGetStaticAddress(right, 2)
        if (varR != null) {
            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
            return code(varR, "$varR+1")
        }

        val constValue = right.asConstInteger()
        if (constValue != null) {
            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
            val hex = constValue.toHex()
            return code("#<$hex", "#>$hex")
        }

        // generic case via scratch register
        asmgen.assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")
        code("P8ZP_SCRATCH_W2", "P8ZP_SCRATCH_W2+1")
    }

    private fun wordGreaterEqualsValue(left: PtExpression, right: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {

        fun code(valueLsb: String, valueMsb: String) {
            if(signed) {
                // word >= X
                if(jump!=null) {
                    var target = asmgen.getJumpTarget(jump, false)
                    if(target.indirect) {
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bvs  +
                            eor  #128
+                           bpl  +""")
                        if(target.needsExpressionEvaluation)
                            target = asmgen.getJumpTarget(jump)
                        require(!target.indexedX)
                        asmgen.out("""
                           jmp  (${target.asmLabel})
+""")
                    } else {
                        require(!target.needsExpressionEvaluation)
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bvs  +
                            eor  #128
+                           bmi  ${target.asmLabel}""")
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
                        asmgen.jmp(afterIfLabel)
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
                    var target = asmgen.getJumpTarget(jump, false)
                    if(target.indirect) {
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bcc  +""")
                        if(target.needsExpressionEvaluation)
                            target = asmgen.getJumpTarget(jump)
                        require(!target.indexedX)
                        asmgen.out("""
                            jmp  (${target.asmLabel})
+""")
                    } else {
                        require(!target.needsExpressionEvaluation)
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bcs  ${target.asmLabel}""")
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
                        asmgen.jmp(afterIfLabel)
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

        val varR = asmgen.tryGetStaticAddress(right, 2)
        if (varR != null) {
            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
            return code(varR, "$varR+1")
        }

        val constValue = right.asConstInteger()
        if (constValue != null) {
            asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
            val hex = constValue.toHex()
            return code("#<$hex", "#>$hex")
        }

        // generic case via scratch register
        asmgen.assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")
        code("P8ZP_SCRATCH_W2", "P8ZP_SCRATCH_W2+1")
    }


    private fun loadAndCmp0MSB(value: PtExpression, long: Boolean) {
        when(value) {
            is PtArrayIndexer -> {
                if(value.variable==null)
                    TODO("support for ptr indexing ${value.position}")
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

    private fun wordLessEqualsZero(value: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {
        if(signed) {
            // word <= 0

            fun compareLsbMsb(valueLsb: String, valueMsb: String) {
                if(jump!=null) {
                    var target = asmgen.getJumpTarget(jump, false)
                    if(target.indirect) {
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  +
                            bne  ++
                            lda  $valueLsb
                            bne  ++""")
                        if(target.needsExpressionEvaluation)
                            target = asmgen.getJumpTarget(jump)
                        require(!target.indexedX)
                        asmgen.out("""
+                           jmp  (${target.asmLabel})
+""")
                    } else {
                        require(!target.needsExpressionEvaluation)
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  ${target.asmLabel}
                            ora  $valueLsb
                            beq  ${target.asmLabel}""")
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
                            ora  $valueLsb
                            bne  $elseLabel
+""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  +
                            ora  $valueLsb
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
                if(value.variable==null)
                    TODO("support for ptr indexing ${value.position}")
                val constIndex = value.index.asConstInteger()
                val varname = asmgen.asmVariableName(value.variable!!)
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
                var target = asmgen.getJumpTarget(jump, false)
                if(target.indirect) {
                    asmgen.out("""
                        cpy  #0
                        bmi  +
                        bne  ++
                        cmp  #0
                        bne  ++""")
                    if(target.needsExpressionEvaluation)
                        target = asmgen.getJumpTarget(jump)
                    require(!target.indexedX)
                    asmgen.out("""
+                       jmp  (${target.asmLabel})
+""")
                } else {
                    require(!target.needsExpressionEvaluation)
                    asmgen.out("""
                        cpy  #0
                        bmi  ${target.asmLabel}
                        bne  +
                        cmp  #0
                        beq  ${target.asmLabel}
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
                    asmgen.jmp(afterIfLabel)
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
            wordEqualsZero(value, false, false, jump, stmt)
        }
    }

    private fun wordGreaterZero(value: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {
        if(signed) {
            // word > 0

            fun compareLsbMsb(valueLsb: String, valueMsb: String) {
                if(jump!=null) {
                    var target = asmgen.getJumpTarget(jump, false)
                    if(target.indirect) {
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  ++
                            bne  +
                            lda  $valueLsb
                            beq  ++""")
                        if(target.needsExpressionEvaluation)
                            target = asmgen.getJumpTarget(jump)
                        require(!target.indexedX)
                        asmgen.out("""
+                           jmp  (${target.asmLabel})
+""")
                    } else {
                        require(!target.needsExpressionEvaluation)
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  +
                            ora  $valueLsb
                            bne  ${target.asmLabel}
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
                            ora  $valueLsb
                            beq  $elseLabel""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            lda  $valueMsb
                            bmi  $afterIfLabel
                            ora  $valueLsb
                            beq  $afterIfLabel""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
                return
            }

            if(value is PtIdentifier)
                return compareLsbMsb(value.name, value.name+"+1")
            if(value is PtArrayIndexer) {
                if(value.variable==null)
                    TODO("support for ptr indexing ${value.position}")
                val constIndex = value.index.asConstInteger()
                val varname = asmgen.asmVariableName(value.variable!!)
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
                var target = asmgen.getJumpTarget(jump, false)
                if(target.indirect) {
                    asmgen.out("""
                        cpy  #0
                        bmi  ++
                        bne  +
                        cmp  #0
                        beq  ++""")
                    if(target.needsExpressionEvaluation)
                        target = asmgen.getJumpTarget(jump)
                    require(!target.indexedX)
                    asmgen.out("""
+                       jmp  (${target.asmLabel})
+""")
                } else {
                    require(!target.needsExpressionEvaluation)
                    asmgen.out("""
                        cpy  #0
                        bmi  +
                        bne  ${target.asmLabel}
                        cmp  #0
                        bne  ${target.asmLabel}
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
                    asmgen.jmp(afterIfLabel)
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

    private fun longEqualsZero(value: PtExpression, notEquals: Boolean, jump: PtJump?, stmt: PtIfElse) {
        if(value is PtIdentifier) {
            val varname = asmgen.asmVariableName(value)
            asmgen.out("""
                lda  $varname
                ora  $varname+1
                ora  $varname+2
                ora  $varname+3""")
        } else {
            asmgen.pushLongRegisters(RegisterOrPair.R14R15, 1)
            asmgen.assignExpressionToRegister(value, RegisterOrPair.R14R15, value.type.isSigned)
            asmgen.out("""
                lda  cx16.r14
                ora  cx16.r14+1
                ora  cx16.r14+2
                ora  cx16.r14+3
                sta  P8ZP_SCRATCH_REG""")
            asmgen.popLongRegisters(RegisterOrPair.R14R15, 1)
            asmgen.out("  lda  P8ZP_SCRATCH_REG  ; restore flags")
        }
        if(notEquals) {
            if (jump != null)
                translateJumpElseBodies("bne", "beq", jump, stmt.elseScope)
            else
                translateIfElseBodies("beq", stmt)
        } else {
            if (jump != null)
                translateJumpElseBodies("beq", "bne", jump, stmt.elseScope)
            else
                translateIfElseBodies("bne", stmt)
        }
    }

    private fun longLessZero(value: PtExpression, lessEquals: Boolean, jump: PtJump?, stmt: PtIfElse) {
        if(lessEquals) {
            checkLongLeZeroIntoCarry(value)
            if (jump != null)
                translateJumpElseBodies("bcs", "bcc", jump, stmt.elseScope)
            else
                translateIfElseBodies("bcc", stmt)
        } else {
            loadAndCmp0MSB(value, true)
            if (jump != null)
                translateJumpElseBodies("bmi", "bpl", jump, stmt.elseScope)
            else
                translateIfElseBodies("bpl", stmt)
        }
    }

    private fun longGreaterZero(value: PtExpression, greaterEquals: Boolean, jump: PtJump?, stmt: PtIfElse) {
        if(greaterEquals) {
            loadAndCmp0MSB(value, true)
            if (jump != null)
                translateJumpElseBodies("bpl", "bmi", jump, stmt.elseScope)
            else
                translateIfElseBodies("bmi", stmt)
        } else {
            checkLongGtZeroIntoCarry(value)
            if (jump != null)
                translateJumpElseBodies("bcs", "bcc", jump, stmt.elseScope)
            else
                translateIfElseBodies("bcc", stmt)
        }
    }

    private fun checkLongGtZeroIntoCarry(value: PtExpression) {
        val notGtLabel = asmgen.makeLabel("not_gt")
        val doneLabel = asmgen.makeLabel("done")
        if(value is PtIdentifier) {
            val varname = asmgen.asmVariableName(value)
            asmgen.out("""
                lda  $varname+3
                bmi  $notGtLabel
                ora  $varname+2
                ora  $varname+1
                ora  $varname
                beq  $notGtLabel
                sec
                bcs  $doneLabel
$notGtLabel     clc
$doneLabel""")
        } else {
            assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R14R15, value.type.isSigned)
            asmgen.out("""
                lda  cx16.r14+3
                bmi  $notGtLabel
                ora  cx16.r14+2
                ora  cx16.r14+1
                ora  cx16.r14
                beq  $notGtLabel
                sec
                bcs  $doneLabel
$notGtLabel     clc
$doneLabel""")
        }
    }

    private fun checkLongLeZeroIntoCarry(value: PtExpression) {
        val leLabel = asmgen.makeLabel("le")
        val doneLabel = asmgen.makeLabel("done")
        if(value is PtIdentifier) {
            val varname = asmgen.asmVariableName(value)
            asmgen.out("""
                lda  $varname+3
                bmi  $leLabel
                ora  $varname+2
                ora  $varname+1
                ora  $varname
                beq  $leLabel
                clc
                bcc  $doneLabel
$leLabel        sec
$doneLabel""")
        } else {
            assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R14R15, value.type.isSigned)
            asmgen.out("""
                lda  cx16.r14+3
                bmi  $leLabel
                ora  cx16.r14+2
                ora  cx16.r14+1
                ora  cx16.r14
                beq  $leLabel
                clc
                bcc  $doneLabel
$leLabel        sec
$doneLabel""")
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
            val e = asmgen.unwrapCasts(value)
            when(e) {
                is PtArrayIndexer -> {
                    val constIndex = e.index.asConstInteger()
                    if(constIndex!=null) {
                        if(e.variable==null)
                            TODO("support for ptr indexing ${e.position}")
                        val varName = asmgen.asmVariableName(e.variable!!)
                        if(e.splitWords) {
                            return translateLoadFromVarSplitw(varName, constIndex, "bne", "beq")
                        }
                        val offset = program.memsizer.memorySize(e.type, constIndex)
                        if (offset < 256) {
                            return translateLoadFromVar("$varName+$offset", "bne", "beq")
                        }
                    }
                    viaScratchReg("bne", "beq")
                }
                is PtIdentifier -> {
                    return translateLoadFromVar(asmgen.asmVariableName(e), "bne", "beq")
                }
                else -> viaScratchReg("bne", "beq")
            }
        } else {
            val e = asmgen.unwrapCasts(value)
            when (e) {
                is PtArrayIndexer -> {
                    val constIndex = e.index.asConstInteger()
                    if (constIndex != null) {
                        if(e.variable==null)
                            TODO("support for ptr indexing ${e.position}")
                        val varName = asmgen.asmVariableName(e.variable!!)
                        if(e.splitWords) {
                            return translateLoadFromVarSplitw(varName, constIndex, "beq", "bne")
                        }
                        val offset = program.memsizer.memorySize(e.type, constIndex)
                        if (offset < 256) {
                            return translateLoadFromVar("$varName+$offset", "beq", "bne")
                        }
                    }
                    viaScratchReg("beq", "bne")
                }
                is PtIdentifier -> {
                    return translateLoadFromVar(asmgen.asmVariableName(e), "beq", "bne")
                }
                else -> viaScratchReg("beq", "bne")
            }
        }
    }

    private fun longEqualsValue(
        left: PtExpression,
        right: PtExpression,
        notEquals: Boolean,
        jump: PtJump?,
        stmt: PtIfElse
    ) {
        val constRight = right.asConstInteger()
        val eRight = asmgen.unwrapCasts(right)
        val variableRight = (eRight as? PtIdentifier)?.name

        val leftvar = asmgen.tryGetStaticAddress(left, 4)
        if (leftvar != null) {
            if (constRight != null) {
                val hex = constRight.toLongHex()
                asmgen.out("""
                    lda  $leftvar
                    cmp  #$${hex.substring(6, 8)}
                    bne  +
                    lda  $leftvar+1
                    cmp  #$${hex.substring(4, 6)}
                    bne  +
                    lda  $leftvar+2
                    cmp  #$${hex.substring(2, 4)}
                    bne  +
                    lda  $leftvar+3
                    cmp  #$${hex.take(2)}
+""")
            } else if (variableRight != null) {
                require(right.type.isLong)
                asmgen.out("""
                    lda  $leftvar
                    cmp  $variableRight
                    bne  +
                    lda  $leftvar+1
                    cmp  $variableRight+1
                    bne  +
                    lda  $leftvar+2
                    cmp  $variableRight+2
                    bne  +
                    lda  $leftvar+3
                    cmp  $variableRight+3
+""")
            }
        } else {
            // TODO cannot easily preserve R14:R15 on stack because we need the status flags of the comparison in between...
            asmgen.assignExpressionToRegister(left, RegisterOrPair.R14R15, left.type.isSigned)
            if(constRight!=null) {
                val hex = constRight.toLongHex()
                asmgen.out("""
                    lda  cx16.r14
                    cmp  #$${hex.substring(6,8)}
                    bne  +
                    lda  cx16.r14+1
                    cmp  #$${hex.substring(4, 6)}
                    bne  +
                    lda  cx16.r14+2
                    cmp  #$${hex.substring(2, 4)}
                    bne  +
                    lda  cx16.r14+3
                    cmp  #$${hex.take(2)}
+""")
            } else if(variableRight!=null) {
                require(right.type.isLong)
                asmgen.out("""
                    lda  cx16.r14
                    cmp  $variableRight
                    bne  +
                    lda  cx16.r14+1
                    cmp  $variableRight+1
                    bne  +
                    lda  cx16.r14+2
                    cmp  $variableRight+2
                    bne  +
                    lda  cx16.r14+3
                    cmp  $variableRight+3
+""")
            } else {
                TODO("long == value expression ${right.position}")
            }
        }

        if(notEquals) {
            if (jump != null)
                translateJumpElseBodies("bne", "beq", jump, stmt.elseScope)
            else
                translateIfElseBodies("beq", stmt)
        } else {
            if (jump != null)
                translateJumpElseBodies("beq", "bne", jump, stmt.elseScope)
            else
                translateIfElseBodies("bne", stmt)
        }
    }

    private fun compareLongValues(
        left: PtExpression,
        operator: String,
        right: PtExpression,
        jump: PtJump?,
        stmt: PtIfElse
    ) {
        val (l, r, op) = if (operator == ">" || operator == "<=") {
            Triple(right, left, if (operator == ">") "<" else ">=")
        } else {
            Triple(left, right, operator)
        }
        // op is either "<" or ">="

        val varL = asmgen.tryGetStaticAddress(l, 4)
        if (varL == null) {
            asmgen.assignExpressionToRegister(l, RegisterOrPair.R12R13, true)
        }
        val opL = varL ?: "cx16.r12"

        val constR = r.asConstInteger()
        if (constR != null) {
            val hex = constR.toLongHex()
            asmgen.out("""
                sec
                lda  $opL
                sbc  #$${hex.substring(6,8)}
                lda  $opL+1
                sbc  #$${hex.substring(4,6)}
                lda  $opL+2
                sbc  #$${hex.substring(2,4)}
                lda  $opL+3
                sbc  #$${hex.take(2)}""")
        } else if (r is PtIdentifier) {
            val varR = asmgen.asmVariableName(r)
            asmgen.out("""
                sec
                lda  $opL
                sbc  $varR
                lda  $opL+1
                sbc  $varR+1
                lda  $opL+2
                sbc  $varR+2
                lda  $opL+3
                sbc  $varR+3""")
        } else {
            asmgen.assignExpressionToRegister(r, RegisterOrPair.R14R15, true)
            asmgen.out("""
                sec
                lda  $opL
                sbc  cx16.r14
                lda  $opL+1
                sbc  cx16.r14+1
                lda  $opL+2
                sbc  cx16.r14+2
                lda  $opL+3
                sbc  cx16.r14+3""")
        }

        asmgen.out("  bvc  + | eor  #128 | +")

        if (op == "<") {
            if (jump != null)
                translateJumpElseBodies("bmi", "bpl", jump, stmt.elseScope)
            else
                translateIfElseBodies("bpl", stmt)
        } else {
            if (jump != null)
                translateJumpElseBodies("bpl", "bmi", jump, stmt.elseScope)
            else
                translateIfElseBodies("bmi", stmt)
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
                var target = asmgen.getJumpTarget(jump, false)
                if(target.indirect) {
                    val jumpLabel = asmgen.makeLabel("do_jump")
                    val skipLabel = asmgen.makeLabel("skip_jump")
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  $jumpLabel
                        cpy  $valueMsb
                        beq  $skipLabel""")
                    if(target.needsExpressionEvaluation)
                        target = asmgen.getJumpTarget(jump)
                    require(!target.indexedX)
                    asmgen.out("""
$jumpLabel              jmp  (${target.asmLabel})
$skipLabel""")
                } else {
                    require(!target.needsExpressionEvaluation)
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  ${target.asmLabel}
                        cpy  $valueMsb
                        bne  ${target.asmLabel}""")
                }
                asmgen.translate(stmt.elseScope)
            } else {
                val afterIfLabel = asmgen.makeLabel("afterif")
                if(stmt.hasElse()) {
                    // if and else blocks
                    val elseLabel = asmgen.makeLabel("else")
                    val ifLabel = asmgen.makeLabel("if")
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  $ifLabel
                        cpy  $valueMsb
                        beq  $elseLabel
$ifLabel""")
                    asmgen.translate(stmt.ifScope)
                    asmgen.jmp(afterIfLabel)
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
                var target = asmgen.getJumpTarget(jump, false)
                if(target.indirect) {
                    val skipLabel = asmgen.makeLabel("skip_jump")
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  $skipLabel
                        cpy  $valueMsb
                        bne  $skipLabel""")
                    if(target.needsExpressionEvaluation)
                        target = asmgen.getJumpTarget(jump)
                    require(!target.indexedX)
                    asmgen.out("""
                        jmp  (${target.asmLabel})
$skipLabel""")
                } else {
                    require(!target.needsExpressionEvaluation)
                    val skipLabel = asmgen.makeLabel("skip_jump")
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  $skipLabel
                        cpy  $valueMsb
                        beq  ${target.asmLabel}
$skipLabel""")
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
                    asmgen.jmp(afterIfLabel)
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

        if (left is PtIdentifier && (right is PtIdentifier || right is PtNumber)) {
            val leftVar = asmgen.asmVariableName(left)
            getWordOperands(right) { rightLsb, rightMsb ->
                val skipLabel = asmgen.makeLabel("skip")
                asmgen.out("  lda  $leftVar | cmp  $rightLsb | bne  $skipLabel | lda  $leftVar+1 | cmp  $rightMsb")
                asmgen.out(skipLabel)
                if (notEquals) {
                    if (jump != null)
                        translateJumpElseBodies("bne", "beq", jump, stmt.elseScope)
                    else
                        translateIfElseBodies("beq", stmt)
                } else {
                    if (jump != null)
                        translateJumpElseBodies("beq", "bne", jump, stmt.elseScope)
                    else
                        translateIfElseBodies("bne", stmt)
                }
            }
            return
        }

        asmgen.assignExpressionToRegister(left, RegisterOrPair.AY, signed)
        getWordOperands(right) { valueLsb, valueMsb ->
            if (notEquals)
                translateAYNotEquals(valueLsb, valueMsb)
            else
                translateAYEquals(valueLsb, valueMsb)
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
            else -> throw AssemblyError("expected comparison operator for float, got '${condition.operator}' at ${stmt.position}")
        }
    }

    private fun getWordOperands(expr: PtExpression, block: (lsb: String, msb: String) -> Unit) {
        val constValue = expr.asConstInteger()
        if (constValue != null) {
            block("#<${constValue}", "#>${constValue}")
        } else {
            val e = asmgen.unwrapCasts(expr)
            if (e is PtIdentifier) {
                val varname = asmgen.asmVariableName(e)
                block(varname, "$varname+1")
            } else if (e is PtAddressOf && !e.isFromArrayElement) {
                val identifier = e.identifier!!
                val varname = if (identifier.type.isSplitWordArray) {
                    if (e.isMsbForSplitArray) identifier.name + "_msb" else identifier.name + "_lsb"
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

    private fun complexity(expr: PtExpression): Int {
        val e = asmgen.unwrapCasts(expr)
        if (e.asConstInteger() != null) return 0
        if (e is PtIdentifier) return 1
        if (e is PtAddressOf && !e.isFromArrayElement) return 1
        if (e is PtMemoryByte) return 2
        if (e is PtArrayIndexer && e.index.asConstInteger() != null) return 2
        return 10
    }
}

