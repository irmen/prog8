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
            stmt.condition is PtBuiltinFunctionCall ||
            stmt.condition is PtFunctionCall ||
            stmt.condition is PtMemoryByte ||
            stmt.condition is PtContainmentCheck)
                return fallbackTranslateForSimpleCondition(stmt)

        val compareCond = stmt.condition as? PtBinaryExpression
        if(compareCond!=null) {

            val useBIT = asmgen.checkIfConditionCanUseBIT(compareCond)
            if(useBIT!=null) {
                // use a BIT instruction to test for bit 7 or 6 set/clear
                val (testBitSet, variable, bitmask) = useBIT
                return translateIfBIT(stmt, jumpAfterIf, testBitSet, variable, bitmask)
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

    private fun translateIfBIT(ifElse: PtIfElse, jumpAfterIf: PtJump?, testForBitSet: Boolean, variable: PtIdentifier, bitmask: Int) {
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
                asmgen.out("  bit  ${variable.name}")
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
                asmgen.out("  bit  ${variable.name}")
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
            "<=" -> translateByteLessEqual(stmt, signed, jumpAfterIf)
            ">" -> translateByteGreater(stmt, signed, jumpAfterIf)
            ">=" -> translateByteGreaterEqual(stmt, signed, jumpAfterIf)
            in LogicalOperators -> {
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
            else -> throw AssemblyError("expected comparison operator")
        }
    }

    private fun translateByteLess(stmt: PtIfElse, signed: Boolean, jumpAfterIf: PtJump?) {
        val condition = stmt.condition as PtBinaryExpression
        asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
        if(signed) {
            if(jumpAfterIf!=null)
                translateJumpElseBodiesSignedByte("<", condition.right, jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodiesSignedByte("<", condition.right, stmt)
        } else {
            asmgen.cmpAwithByteValue(condition.right, false)
            if(jumpAfterIf!=null)
                translateJumpElseBodies("bcc", "bcs", jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodies("bcs", stmt)
        }
    }

    private fun translateByteLessEqual(stmt: PtIfElse, signed: Boolean, jumpAfterIf: PtJump?) {
        // X<=Y -> Y>=X (reverse of >=)
        val condition = stmt.condition as PtBinaryExpression
        asmgen.assignExpressionToRegister(condition.right, RegisterOrPair.A, signed)
        return if(signed) {
            if(jumpAfterIf!=null)
                translateJumpElseBodiesSignedByte(">=", condition.left, jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodiesSignedByte(">=", condition.left, stmt)
        } else {
            asmgen.cmpAwithByteValue(condition.left, false)
            if(jumpAfterIf!=null)
                translateJumpElseBodies("bcs", "bcc", jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodies("bcc", stmt)
        }
    }

    private fun translateByteGreater(stmt: PtIfElse, signed: Boolean, jumpAfterIf: PtJump?) {
        val condition = stmt.condition as PtBinaryExpression
        if(signed) {
            // X>Y --> Y<X
            asmgen.assignExpressionToRegister(condition.right, RegisterOrPair.A, true)
            if (jumpAfterIf != null)
                translateJumpElseBodiesSignedByte("<", condition.left, jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodiesSignedByte("<", condition.left, stmt)
        } else {
            asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A)
            asmgen.cmpAwithByteValue(condition.right, false)
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

    private fun translateByteGreaterEqual(stmt: PtIfElse, signed: Boolean, jumpAfterIf: PtJump?) {
        val condition = stmt.condition as PtBinaryExpression
        asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
        return if(signed) {
            if(jumpAfterIf!=null)
                translateJumpElseBodiesSignedByte(">=", condition.right, jumpAfterIf, stmt.elseScope)
            else
                translateIfElseBodiesSignedByte(">=", condition.right, stmt)
        } else {
            asmgen.cmpAwithByteValue(condition.right, false)
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
                else -> throw AssemblyError("expected comparison operator")
            }
        }

        return when (condition.operator) {
            "==" -> longEqualsValue(condition.left, condition.right, false, jumpAfterIf, stmt)
            "!=" -> longEqualsValue(condition.left, condition.right, true, jumpAfterIf, stmt)
            "<" -> TODO("long < 0")
            "<=" -> TODO("long <= 0")
            ">" -> TODO("long > 0")
            ">=" -> TODO("long >= 0")
            else -> throw AssemblyError("expected comparison operator")
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
                require(!long)
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
                require(!long)
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
                            bne  +
                            lda  $valueLsb
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
                            lda  $valueMsb
                            bmi  +
                            bne  $elseLabel
                            lda  $valueLsb
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
            wordEqualsZero(value, true, false, jump, stmt)
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
                            bne  ${target.asmLabel}
                            lda  $valueLsb
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
                            bne  +
                            lda  $valueLsb
                            beq  $elseLabel
+""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel)
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
        asmgen.assignExpressionToRegister(value, RegisterOrPair.R0R1_32, value.type.isSigned)
        asmgen.out("""
            lda  cx16.r0
            ora  cx16.r0+1
            ora  cx16.r0+2
            ora  cx16.r0+3""")
        if(notEquals) {
            if (jump != null)
                translateJumpElseBodies("bne", "beq", jump, stmt.elseScope)
            else
                translateIfElseBodies("bne", stmt)
        } else {
            if (jump != null)
                translateJumpElseBodies("beq", "bne", jump, stmt.elseScope)
            else
                translateIfElseBodies("beq", stmt)
        }
    }

    private fun longLessZero(value: PtExpression, lessEquals: Boolean, jump: PtJump?, stmt: PtIfElse) {
        if(lessEquals) {
            TODO("long <= 0")
        } else {
            loadAndCmp0MSB(value, true)
            if (jump != null)
                translateJumpElseBodies("bmi", "bpl", jump, stmt.elseScope)
            else
                translateIfElseBodies("bpl", stmt)
        }
    }

    private fun longGreaterZero(value: PtExpression, lessEquals: Boolean, jump: PtJump?, stmt: PtIfElse) {
        if(lessEquals) {
            TODO("long >= 0")
        } else {
            loadAndCmp0MSB(value, true)
            if (jump != null)
                translateJumpElseBodies("bpl", "bmi", jump, stmt.elseScope)
            else
                translateIfElseBodies("bmi", stmt)
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
                        if(value.variable==null)
                            TODO("support for ptr indexing ${value.position}")
                        val varName = asmgen.asmVariableName(value.variable!!)
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
                        if(value.variable==null)
                            TODO("support for ptr indexing ${value.position}")
                        val varName = asmgen.asmVariableName(value.variable!!)
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

    private fun longEqualsValue(
        left: PtExpression,
        right: PtExpression,
        notEquals: Boolean,
        jump: PtJump?,
        stmt: PtIfElse
    ) {
        // TODO this can be optimized somewhat more when the left operand is a variable as well
        // we only optimize for a const right value for now

        val constRight = right.asConstInteger()
        val variableRight = (right as? PtIdentifier)?.name
        if(constRight!=null) {
            asmgen.assignExpressionToRegister(left, RegisterOrPair.R0R1_32, left.type.isSigned)
            val hex = constRight.toUInt().toString(16).padStart(8, '0')
            asmgen.out("""
                lda  cx16.r0
                cmp  #$${hex.substring(6,8)}
                bne  +
                lda  cx16.r0+1
                cmp  #$${hex.substring(4, 6)}
                bne  +
                lda  cx16.r0+2
                cmp  #$${hex.substring(2, 4)}
                bne  +
                lda  cx16.r0+3
                cmp  #$${hex.take(2)}
+""")
        } else if(variableRight!=null) {
            require(right.type.isLong)
            asmgen.assignExpressionToRegister(left, RegisterOrPair.R0R1_32, left.type.isSigned)
            asmgen.out("""
                lda  cx16.r0
                cmp  $variableRight
                bne  +
                lda  cx16.r0+1
                cmp  $variableRight+1
                bne  +
                lda  cx16.r0+2
                cmp  $variableRight+2
                bne  +
                lda  cx16.r0+3
                cmp  $variableRight+3
+""")
        } else {
            asmgen.assignExpressionToRegister(left, RegisterOrPair.R2R3_32, left.type.isSigned)
            asmgen.assignExpressionToRegister(right, RegisterOrPair.R0R1_32, right.type.isSigned)
            asmgen.out("""
                lda  cx16.r0
                cmp  cx16.r2
                bne  +
                lda  cx16.r0+1
                cmp  cx16.r2+1
                bne  +
                lda  cx16.r0+2
                cmp  cx16.r2+2
                bne  +
                lda  cx16.r0+3
                cmp  cx16.r2+3
+""")
        }

        if(notEquals) {
            if (jump != null)
                translateJumpElseBodies("beq", "bne", jump, stmt.elseScope)
            else
                translateIfElseBodies("beq", stmt)
        } else {
            if (jump != null)
                translateJumpElseBodies("bne", "beq", jump, stmt.elseScope)
            else
                translateIfElseBodies("bne", stmt)
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
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  +
                        cpy  $valueMsb
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
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  +
                        cpy  $valueMsb
                        beq  $elseLabel
+""")
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
                    asmgen.out("""
                        cmp  $valueLsb
                        bne  +
                        cpy  $valueMsb
                        bne  +""")
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
                        bne  +
                        cpy  $valueMsb
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

        fun translateEqualsVarVar(left: PtIdentifier, right: PtIdentifier) {
            if(notEquals) {
                if(jump!=null) {
                    var target = asmgen.getJumpTarget(jump, false)
                    if(target.indirect) {
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  +
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
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
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  ${target.asmLabel}
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
                            bne  ${target.asmLabel}""")
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
                        asmgen.jmp(afterIfLabel)
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
                    var target = asmgen.getJumpTarget(jump, false)
                    if(target.indirect) {
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  +
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
                            bne  +""")
                        if(target.needsExpressionEvaluation)
                            target = asmgen.getJumpTarget(jump)
                        require(!target.indexedX)
                        asmgen.out("""
                            jmp  (${target.asmLabel})
+""")
                    } else {
                        require(!target.needsExpressionEvaluation)
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  +
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
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
                            lda  ${left.name}
                            cmp  ${right.name}
                            bne  $elseLabel
                            lda  ${left.name}+1
                            cmp  ${right.name}+1
                            bne  $elseLabel""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel)
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
                    var target = asmgen.getJumpTarget(jump, false)
                    if(target.indirect) {
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  #<$value
                            bne  +
                            lda  ${left.name}+1
                            cmp  #>$value
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
                            lda  ${left.name}
                            cmp  #<$value
                            bne  ${target.asmLabel}
                            lda  ${left.name}+1
                            cmp  #>$value
                            bne  ${target.asmLabel}""")
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
                        asmgen.jmp(afterIfLabel)
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
                    var target = asmgen.getJumpTarget(jump, false)
                    if(target.indirect) {
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  #<$value
                            bne  +
                            lda  ${left.name}+1
                            cmp  #>$value
                            bne  +""")
                        if(target.needsExpressionEvaluation)
                            target = asmgen.getJumpTarget(jump)
                        require(!target.indexedX)
                        asmgen.out("""
                            jmp  (${target.asmLabel})
+""")
                    } else {
                        require(!target.needsExpressionEvaluation)
                        asmgen.out("""
                            lda  ${left.name}
                            cmp  #<$value
                            bne  +
                            lda  ${left.name}+1
                            cmp  #>$value
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
                            lda  ${left.name}
                            cmp  #<$value
                            bne  $elseLabel
                            lda  ${left.name}+1
                            cmp  #>$value
                            bne  $elseLabel""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel)
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
                if(left.variable==null)
                    TODO("support for ptr indexing ${left.position}")
                asmgen.assignExpressionToRegister(right, RegisterOrPair.AY, signed)
                val varName = asmgen.asmVariableName(left.variable!!)
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
                    if(left.isFromArrayElement) {
                        fallbackTranslateForSimpleCondition(stmt)
                    } else {
                        val varname = if(left.identifier!!.type.isSplitWordArray) {
                            if(left.isMsbForSplitArray) left.identifier!!.name+"_msb" else left.identifier!!.name+"_lsb"
                        } else {
                            left.identifier!!.name
                        }
                        asmgen.assignExpressionToRegister(right, RegisterOrPair.AY, signed)
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
                    if(left.isFromArrayElement) {
                        fallbackTranslateForSimpleCondition(stmt)
                    } else {
                        val varname = if(left.identifier!!.type.isSplitWordArray) {
                            if(left.isMsbForSplitArray) left.identifier!!.name+"_msb" else left.identifier!!.name+"_lsb"
                        } else {
                            left.identifier!!.name
                        }
                        asmgen.assignExpressionToRegister(right, RegisterOrPair.AY, signed)
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