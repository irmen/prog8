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
            "<=" -> translateByteLessEqual(stmt, signed, jumpAfterIf)
            ">" -> translateByteGreater(stmt, signed, jumpAfterIf)
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

    private fun translateByteLessEqual(stmt: PtIfElse, signed: Boolean, jumpAfterIf: PtJump?) {

        fun bodies(greaterBranches: Pair<String, String>, stmt: PtIfElse) {
            // comparison value is already in A
            val afterIfLabel = asmgen.makeLabel("afterif")
            if(stmt.hasElse()) {
                // if and else blocks
                val elseLabel = asmgen.makeLabel("else")
                asmgen.out("""
                    ${greaterBranches.first}  +
                    ${greaterBranches.second}  $elseLabel
+""")
                asmgen.translate(stmt.ifScope)
                asmgen.jmp(afterIfLabel, false)
                asmgen.out(elseLabel)
                asmgen.translate(stmt.elseScope)
            } else {
                // no else block
                asmgen.out("""
                    ${greaterBranches.first}  +
                    ${greaterBranches.second}  $afterIfLabel
+""")
                asmgen.translate(stmt.ifScope)
            }
            asmgen.out(afterIfLabel)
        }

        fun bodies(
            branches: Pair<String, String>,
            greaterBranches: Pair<String, String>,
            jump: PtJump,
            elseBlock: PtNodeGroup
        ) {
            // comparison value is already in A
            val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
            if(indirect) {
                asmgen.out("""
                    ${greaterBranches.first}  +
                    ${greaterBranches.second}  ++
+                   jmp  ($asmLabel)
+""")
            } else {
                asmgen.out("  ${branches.first}  $asmLabel |  ${branches.second}  $asmLabel")
            }
            asmgen.translate(elseBlock)
        }

        val condition = stmt.condition as PtBinaryExpression
        asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
        cmpAwithByteValue(condition.right)
        if(signed) {
            if(jumpAfterIf!=null) {
                bodies("bmi" to "beq", "beq" to "bpl", jumpAfterIf, stmt.elseScope)
            } else {
                bodies("beq" to "bpl", stmt)
            }
        } else {
            if(jumpAfterIf!=null) {
                bodies("bcc" to "beq", "beq" to "bcs", jumpAfterIf, stmt.elseScope)
            } else {
                bodies("beq" to "bcs", stmt)
            }
        }
    }

    private fun translateByteGreater(stmt: PtIfElse, signed: Boolean, jumpAfterIf: PtJump?) {

        fun bodies(lessEqBranches: Pair<String, String>, stmt: PtIfElse) {
            // comparison value is already in A
            val afterIfLabel = asmgen.makeLabel("afterif")
            if(stmt.hasElse()) {
                // if and else blocks
                val elseLabel = asmgen.makeLabel("else")
                asmgen.out("  ${lessEqBranches.first}  $elseLabel |  ${lessEqBranches.second}  $elseLabel")
                asmgen.translate(stmt.ifScope)
                asmgen.jmp(afterIfLabel, false)
                asmgen.out(elseLabel)
                asmgen.translate(stmt.elseScope)
            } else {
                // no else block
                asmgen.out("  ${lessEqBranches.first}  $afterIfLabel |  ${lessEqBranches.second}  $afterIfLabel")
                asmgen.translate(stmt.ifScope)
            }
            asmgen.out(afterIfLabel)
        }

        fun bodies(
            branches: Pair<String, String>,
            lessEqBranches: Pair<String, String>,
            jump: PtJump,
            elseBlock: PtNodeGroup
        ) {
            // comparison value is already in A
            val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
            if(indirect) {
                asmgen.out("""
                ${lessEqBranches.first}  +
                ${lessEqBranches.second}  +
                jmp  ($asmLabel)
+""")
            } else {
                asmgen.out("""
                ${branches.first}  +
                ${branches.second}  $asmLabel
+""")
            }
            asmgen.translate(elseBlock)
        }

        val condition = stmt.condition as PtBinaryExpression
        asmgen.assignExpressionToRegister(condition.left, RegisterOrPair.A, signed)
        cmpAwithByteValue(condition.right)
        if(signed) {
            if(jumpAfterIf!=null) {
                bodies("beq" to "bpl", "bmi" to "beq", jumpAfterIf, stmt.elseScope)
            } else {
                bodies("bmi" to "beq", stmt)
            }
        } else {
            if(jumpAfterIf!=null) {
                bodies("beq" to "bcs", "bcc" to "beq", jumpAfterIf, stmt.elseScope)
            } else {
                bodies("bcc" to "beq", stmt)
            }
        }
    }

    private fun translateIfWord(stmt: PtIfElse, condition: PtBinaryExpression, jumpAfterIf: PtJump?) {
        val signed = condition.left.type in SignedDatatypes
        val constValue = condition.right.asConstInteger()
        if(constValue==0) {
            // optimized comparisons with zero
            when (condition.operator) {
                "==" -> return wordEqualsZero(condition.left, false, signed, jumpAfterIf, stmt)
                "!=" -> return wordEqualsZero(condition.left, true, signed, jumpAfterIf, stmt)
                "<" -> return wordLessZero(condition.left, signed, jumpAfterIf, stmt)
                "<=" -> return wordLessEqualsZero(condition.left, signed, jumpAfterIf, stmt)
                ">" -> return wordGreaterZero(condition.left, signed, jumpAfterIf, stmt)
                ">=" -> return wordGreaterEqualsZero(condition.left, signed, jumpAfterIf, stmt)
            }
        }

        // non-zero comparisons
        when(condition.operator) {
            "==" -> wordEqualsValue(condition.left, condition.right, false, signed, jumpAfterIf, stmt)
            "!=" -> wordEqualsValue(condition.left, condition.right, true, signed, jumpAfterIf, stmt)
            "<" -> wordLessValue(condition.left, condition.right, signed, jumpAfterIf, stmt)
            "<=" -> wordLessEqualsValue(condition.left, condition.right, signed, jumpAfterIf, stmt)
            ">" -> wordGreaterValue(condition.left, condition.right, signed, jumpAfterIf, stmt)
            ">=" -> wordGreaterEqualsValue(condition.left, condition.right, signed, jumpAfterIf, stmt)
            else -> fallbackTranslate(stmt, false)
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
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bcc   +
                            jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bcc  $asmLabel""")
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
            return code("#<$variable", "#>$variable")
        }

        // TODO optimize for simple array value

        // generic case via scratch register
        asmgen.assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")
        code("P8ZP_SCRATCH_W2", "P8ZP_SCRATCH_W2+1")
    }

    private fun wordGreaterValue(left: PtExpression, right: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {
        fun code(valueLsb: String, valueMsb: String) {
            if(signed) {
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            ldy  $valueLsb
                            lda  $valueMsb
                            cpy  P8ZP_SCRATCH_W1
                            sbc  P8ZP_SCRATCH_W1+1
                            bvc  +
                            eor  #128
+                           bmi  +
                            jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            ldy  $valueLsb
                            lda  $valueMsb
                            cpy  P8ZP_SCRATCH_W1       
                            sbc  P8ZP_SCRATCH_W1+1
                            bvc  +
                            eor  #128
+                           bpl  $asmLabel""")
                    }
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            ldy  $valueLsb
                            lda  $valueMsb
                            cpy  P8ZP_SCRATCH_W1
                            sbc  P8ZP_SCRATCH_W1+1
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
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            ldy  $valueLsb
                            lda  $valueMsb
                            cpy  P8ZP_SCRATCH_W1
                            sbc  P8ZP_SCRATCH_W1+1
                            bvc  +
                            eor  #128
+                           bmi  $afterIfLabel""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
            } else {
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            cpy  $valueMsb
                            beq  +
                            bcc  _gt
                            bcs  _not
+                           cmp  $valueLsb
                            bcc  _gt
                            bne  _not
_gt                         jmp  ($asmLabel)
_not""")
                    } else {
                        asmgen.out("""
                            sec
                            sbc  $valueLsb
                            sta  P8ZP_SCRATCH_REG
                            tya
                            sbc  $valueMsb
                            ora  P8ZP_SCRATCH_REG
                            beq  +
                            bcs  $asmLabel
+""")
                    }
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            cpy  $valueMsb
                            beq  +
                            bcc  _gt
                            bcs  $elseLabel
+                           cmp  $valueLsb
                            bcc  _gt
                            bne  $elseLabel
_gt""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel, false)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            cpy  $valueMsb
                            beq  +
                            bcc  _gt
                            bcs  $afterIfLabel
+                           cmp  $valueLsb
                            bcc  _gt
                            bne  $afterIfLabel
_gt""")
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
            return code("#<$variable", "#>$variable")
        }

        // TODO optimize for simple array value

        // generic case via scratch register
        asmgen.assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")
        code("P8ZP_SCRATCH_W2", "P8ZP_SCRATCH_W2+1")
    }

    private fun wordLessEqualsValue(left: PtExpression, right: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {
        fun code(valueLsb: String, valueMsb: String) {
            if(signed) {
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            ldy  $valueLsb
                            lda  $valueMsb
                            cpy  P8ZP_SCRATCH_W1
                            sbc  P8ZP_SCRATCH_W1+1
                            bvc  +
                            eor  #128
+                           bmi  +
                            jmp  ($asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            sta    P8ZP_SCRATCH_W1
                            sty    P8ZP_SCRATCH_W1+1
                            ldy    $valueLsb
                            lda    $valueMsb
                            cpy    P8ZP_SCRATCH_W1
                            sbc    P8ZP_SCRATCH_W1+1
                            bvc    +
                            eor    #128
+                           bpl    $asmLabel""")
                    }
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            ldy  $valueLsb
                            lda  $valueMsb
                            cpy  P8ZP_SCRATCH_W1
                            sbc  P8ZP_SCRATCH_W1+1
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
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            ldy  $valueLsb
                            lda  $valueMsb
                            cpy  P8ZP_SCRATCH_W1
                            sbc  P8ZP_SCRATCH_W1+1
                            bvc  +
                            eor  #128
+                           bmi  $afterIfLabel""")
                        asmgen.translate(stmt.ifScope)
                    }
                    asmgen.out(afterIfLabel)
                }
            } else {
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            sec
                            sbc  $valueLsb
                            sta  P8ZP_SCRATCH_REG
                            tya
                            sbc  $valueMsb
                            ora  P8ZP_SCRATCH_REG
                            beq  +
                            bcc  +
                            jmp  $(asmLabel)
+""")
                    } else {
                        asmgen.out("""
                            sec
                            sbc  $valueLsb
                            sta  P8ZP_SCRATCH_REG
                            tya
                            sbc  $valueMsb
                            ora  P8ZP_SCRATCH_REG
                            beq  +
                            bcs  $asmLabel
+""")
                    }
                } else {
                    val afterIfLabel = asmgen.makeLabel("afterif")
                    if(stmt.hasElse()) {
                        // if and else blocks
                        val elseLabel = asmgen.makeLabel("else")
                        asmgen.out("""
                            sec
                            sbc  $valueLsb
                            sta  P8ZP_SCRATCH_REG
                            tya
                            sbc  $valueMsb
                            ora  P8ZP_SCRATCH_REG
                            beq  +
                            bcc  $elseLabel
+""")
                        asmgen.translate(stmt.ifScope)
                        asmgen.jmp(afterIfLabel, false)
                        asmgen.out(elseLabel)
                        asmgen.translate(stmt.elseScope)
                    } else {
                        // no else block
                        asmgen.out("""
                            sec
                            sbc  $valueLsb
                            sta  P8ZP_SCRATCH_REG
                            tya
                            sbc  $valueMsb
                            ora  P8ZP_SCRATCH_REG
                            beq  +
                            bcc  $afterIfLabel
+""")
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
            return code("#<$variable", "#>$variable")
        }

        // TODO optimize for simple array value

        // generic case via scratch register
        asmgen.assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")
        code("P8ZP_SCRATCH_W2", "P8ZP_SCRATCH_W2+1")
    }

    private fun wordGreaterEqualsValue(left: PtExpression, right: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {

        fun code(valueLsb: String, valueMsb: String) {
            if(signed) {
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
                if(jump!=null) {
                    val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                    if(indirect) {
                        asmgen.out("""
                            cmp  $valueLsb
                            tya
                            sbc  $valueMsb
                            bcs  +
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
            return code("#<$variable", "#>$variable")
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
                asmgen.out("  lda  $varname+1,y")
            }
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(value)
                asmgen.out("  lda  $varname+1")
            }
            else -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.AY, true)
                asmgen.out("  cmp  #0")
            }
        }
    }

    private fun wordLessEqualsZero(value: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {
        return if(signed) {
            asmgen.assignExpressionToRegister(value, RegisterOrPair.AY, true)  // TODO optimize this even further by doing MSB/LSB separately
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
            wordEqualsZero(value, true, false, jump, stmt)
        }
    }

    private fun wordGreaterZero(value: PtExpression, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {
        if(signed) {
            asmgen.assignExpressionToRegister(value, RegisterOrPair.AY, true)  // TODO optimize this even further by doing MSB/LSB separately
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
            wordEqualsZero(value, true, false, jump, stmt)
        }
    }

    private fun wordEqualsZero(value: PtExpression, notEquals: Boolean, signed: Boolean, jump: PtJump?, stmt: PtIfElse) {

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

        if(notEquals) {
            when(value) {
                is PtArrayIndexer -> {
                    val constIndex = value.index.asConstInteger()
                    if(constIndex!=null) {
                        val offset = constIndex * program.memsizer.memorySize(value.type)
                        if(offset<256) {
                            val varName = asmgen.asmVariableName(value.variable)
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
                    if(constIndex!=null) {
                        val offset = constIndex * program.memsizer.memorySize(value.type)
                        if(offset<256) {
                            val varName = asmgen.asmVariableName(value.variable)
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

        fun translateLoadFromVarNotEquals(varname: String) {
            if(jump!=null) {
                val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                if(indirect) {
                    asmgen.out("""
                        cmp  $varname
                        bne  +
                        cpy  $varname+1
                        beq  ++
+                       jmp  ($asmLabel)
+""")
                } else {
                    asmgen.out("""
                        cmp  $varname
                        bne  $asmLabel
                        cpy  $varname+1
                        bne  $asmLabel""")
                }
                asmgen.translate(stmt.elseScope)
            } else {
                val afterIfLabel = asmgen.makeLabel("afterif")
                if(stmt.hasElse()) {
                    // if and else blocks
                    val elseLabel = asmgen.makeLabel("else")
                    asmgen.out("""
                        cmp  $varname
                        bne  +
                        cpy  $varname+1
                        beq  $elseLabel
+""")
                    asmgen.translate(stmt.ifScope)
                    asmgen.jmp(afterIfLabel, false)
                    asmgen.out(elseLabel)
                    asmgen.translate(stmt.elseScope)
                } else {
                    // no else block
                    asmgen.out("""
                        cmp  $varname
                        bne  +
                        cpy  $varname+1
                        beq  $afterIfLabel
+""")
                    asmgen.translate(stmt.ifScope)
                }
                asmgen.out(afterIfLabel)
            }
        }

        fun translateLoadFromVarEquals(varname: String) {
            return if(jump!=null) {
                val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
                if(indirect) {
                    asmgen.out("""
                        cmp  $varname
                        bne  +
                        cpy  $varname+1
                        bne  +
                        jmp  ($asmLabel)
+""")
                } else {
                    asmgen.out("""
                        cmp  $varname
                        bne  +
                        cpy  $varname+1
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
                        cmp  $varname
                        bne  $elseLabel
                        cpy  $varname+1
                        bne  $elseLabel""")
                    asmgen.translate(stmt.ifScope)
                    asmgen.jmp(afterIfLabel, false)
                    asmgen.out(elseLabel)
                    asmgen.translate(stmt.elseScope)
                } else {
                    // no else block
                    asmgen.out("""
                        cmp  $varname
                        bne  $afterIfLabel
                        cpy  $varname+1
                        bne  $afterIfLabel""")
                    asmgen.translate(stmt.ifScope)
                }
                asmgen.out(afterIfLabel)
            }
        }

        if(notEquals) {
            when(left) {
                is PtArrayIndexer -> {
                    asmgen.assignExpressionToRegister(right, RegisterOrPair.AY, signed)
                    val constIndex = left.index.asConstInteger()
                    if(constIndex!=null) {
                        val offset = constIndex * program.memsizer.memorySize(left.type)
                        if(offset<256) {
                            val varName = asmgen.asmVariableName(left.variable)
                            return translateLoadFromVarNotEquals("$varName+$offset")
                        }
                    }
                    fallbackTranslate(stmt, false)
                }
                is PtIdentifier -> {
                    asmgen.assignExpressionToRegister(right, RegisterOrPair.AY, signed)
                    return translateLoadFromVarNotEquals(asmgen.asmVariableName(left))
                }
                else -> fallbackTranslate(stmt, false)
            }
        } else {
            when(left) {
                is PtArrayIndexer -> {
                    asmgen.assignExpressionToRegister(right, RegisterOrPair.AY, signed)
                    val constIndex = left.index.asConstInteger()
                    if(constIndex!=null) {
                        val offset = constIndex * program.memsizer.memorySize(left.type)
                        if(offset<256) {
                            val varName = asmgen.asmVariableName(left.variable)
                            return translateLoadFromVarEquals("$varName+$offset")
                        }
                    }
                    fallbackTranslate(stmt, false)
                }
                is PtIdentifier -> {
                    asmgen.assignExpressionToRegister(right, RegisterOrPair.AY, signed)
                    return translateLoadFromVarEquals(asmgen.asmVariableName(left))
                }
                else -> fallbackTranslate(stmt, false)
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
                    asmgen.subroutineExtra(left.definingISub()!!).usedFloatEvalResultVar1 = true
                }
            }
        } else {
            when (right) {
                is PtIdentifier -> lessf(left, asmgen.asmVariableName(right))
                is PtNumber -> lessf(left, allocator.getFloatAsmConst(right.number))
                else -> {
                    asmgen.assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
                    lessf(left, subroutineFloatEvalResultVar1)
                    asmgen.subroutineExtra(left.definingISub()!!).usedFloatEvalResultVar1 = true
                }
            }
        }
    }

}