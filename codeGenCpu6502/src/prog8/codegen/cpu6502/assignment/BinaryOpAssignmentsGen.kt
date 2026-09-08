package prog8.codegen.cpu6502.assignment

import prog8.code.StExtSub
import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal
import prog8.codegen.cpu6502.toLongHex
import kotlin.math.log2

internal class BinaryOpAssignmentsGen(
    private val program: PtProgram,
    private val asmgen: AsmGen6502Internal,
    private val pointergen: PointerAssignmentsGen,
    private val anyExprGen: AnyExprAsmGen
) {
    lateinit var assignmentAsmGen: AssignmentAsmGen

    internal fun attemptAssignOptimizedBinexpr(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        val translatedOk = when (expr.operator) {
            in ComparisonOperators -> optimizedComparison(expr, assign)
            in BitwiseOperators -> optimizedBitwiseExpr(expr, assign.target)
            in LogicalOperators -> optimizedLogicalExpr(expr, assign.target)
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


    internal fun optimizedComparison(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        if(expr.right.asConstInteger() == 0) {
            if(expr.operator == "==" || expr.operator=="!=") {
                if (assign.target.datatype.isByteOrBool) {
                    if(attemptAssignToByteCompareZero(expr, assign)) return true
                } else {
                    // do nothing, this is handled by a type cast.
                }
            }
        }

        if(expr.left.type.isUnsignedByte) {
            if(expr.operator=="<") {
                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                when(val right = expr.right) {
                    is PtIdentifier -> {
                        asmgen.out("""
                            cmp  ${right.name}
                            rol  a
                            and  #1
                            eor  #1""")
                        assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    is PtMemoryByte -> {
                        val addr = right.address as? PtNumber
                        if(addr!=null) {
                            asmgen.out("""
                                cmp  ${addr.number.toHex()}
                                rol  a
                                and  #1
                                eor  #1""")
                            assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                            return true
                        }
                        if(asmgen.isTargetCpu(CpuType.CPU65C02)) {
                            val ptrvar = right.address as? PtIdentifier
                            if (ptrvar != null && asmgen.isZpVar(ptrvar)) {
                                asmgen.out("""
                                    cmp  (${ptrvar.name})
                                    rol  a
                                    and  #1
                                    eor  #1""")
                                assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                                return true
                            }
                        }
                    }
                    is PtNumber -> {
                        asmgen.out("""
                            cmp  #${right.number.toInt()}
                            rol  a
                            and  #1
                            eor  #1""")
                        assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    else -> { /* not optimizable */ }
                }
            }
            else if(expr.operator==">=") {
                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                when(val right = expr.right) {
                    is PtIdentifier -> {
                        asmgen.out("""
                            cmp  ${right.name}
                            rol  a
                            and  #1""")
                        assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    is PtMemoryByte -> {
                        val addr = right.address as? PtNumber
                        if(addr!=null) {
                            asmgen.out("""
                                cmp  ${addr.number.toHex()}
                                rol  a
                                and  #1""")
                            assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                            return true
                        }
                        if(asmgen.isTargetCpu(CpuType.CPU65C02)) {
                            val ptrvar = right.address as? PtIdentifier
                            if (ptrvar != null && asmgen.isZpVar(ptrvar)) {
                                asmgen.out("""
                                    cmp  (${ptrvar.name})
                                    rol  a
                                    and  #1""")
                                assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                                return true
                            }
                        }
                    }
                    is PtNumber -> {
                        asmgen.out("""
                            cmp  #${right.number.toInt()}
                            rol  a
                            and  #1""")
                        assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    else -> { /* not optimizable */ }
                }
            }
        }

        // b = v > 99  -->  b=false ,  if v>99  b=true
        val targetReg=assign.target.register
        if(targetReg!=null) {
            // a register as target should be handled slightly differently to avoid overwriting the value
            val ifPart = PtNodeGroup()
            val elsePart = PtNodeGroup()
            val reg = when(targetReg) {
                RegisterOrPair.A -> "a"
                RegisterOrPair.X -> "x"
                RegisterOrPair.Y -> "y"
                else -> TODO("comparison to word register  ${expr.position}")
            }
            val assignTrue = PtInlineAssembly("\tld${reg}  #1", false, assign.target.position)
            val assignFalse = PtInlineAssembly("\tld${reg}  #0", false, assign.target.position)
            ifPart.add(assignTrue)
            elsePart.add(assignFalse)
            val ifelse = PtIfElse(assign.position)
            val exprClone: PtBinaryExpression
            if(!asmgen.isTargetCpu(CpuType.VIRTUAL)
                && (expr.operator==">" || expr.operator=="<=")
                && expr.right.type.isWord) {
                // word X>Y -> X<Y, X<=Y -> Y>=X  , easier to do in 6502  (codegen also expects these to no longe exist!)
                exprClone = PtBinaryExpression(if(expr.operator==">") "<" else ">=", expr.type, expr.position)
                exprClone.add(expr.children[1]) // doesn't seem to need a deep clone
                exprClone.add(expr.children[0]) // doesn't seem to need a deep clone
            } else {
                exprClone = PtBinaryExpression(expr.operator, expr.type, expr.position)
                exprClone.add(expr.children[0]) // doesn't seem to need a deep clone
                exprClone.add(expr.children[1]) // doesn't seem to need a deep clone
            }
            ifelse.add(exprClone)
            ifelse.add(ifPart)
            ifelse.add(elsePart)
            ifelse.parent = expr.parent
            asmgen.translate(ifelse)
            return true
        }
        val target = assign.target.origAstTarget
        assignmentAsmGen.assignConstantByte(assign.target, 0)
        val ifPart = PtNodeGroup()
        val assignTrue: PtNode
        if(target!=null) {
            // set target to true
            assignTrue = PtAssignment(assign.position)
            assignTrue.add(target)
            assignTrue.add(PtNumber.fromBoolean(true, assign.position))
        } else {
            when(assign.target.kind) {
                TargetStorageKind.VARIABLE -> {
                    if(assign.target.datatype.isWord) {
                        assignTrue = if(asmgen.isTargetCpu(CpuType.CPU65C02)) {
                            PtInlineAssembly("""  
  lda  #1
  sta  ${assign.target.asmVarname}
  stz  ${assign.target.asmVarname}+1""", false, assign.target.position)
                        } else {
                            PtInlineAssembly("""
  lda  #1
  sta  ${assign.target.asmVarname}
  lda  #0
  sta  ${assign.target.asmVarname}+1""", false, assign.target.position)
                        }
                    } else {
                        assignTrue = PtInlineAssembly("\tlda  #1\n  sta  ${assign.target.asmVarname}", false, assign.target.position)
                    }
                }
                TargetStorageKind.MEMORY -> {
                    val tgt = PtAssignTarget(false, assign.target.position)
                    val targetmem = assign.target.memory!!
                    val mem = PtMemoryByte(targetmem.position)
                    mem.add(targetmem.address)
                    tgt.add(mem)
                    assignTrue = PtAssignment(assign.position)
                    assignTrue.add(tgt)
                    assignTrue.add(PtNumber.fromBoolean(true, assign.position))
                }
                TargetStorageKind.ARRAY -> {
                    val deref = assign.target.array!!.pointerderef
                    if(deref!=null) {
                        TODO("array indexed pointer deref ${assign.position}")
                    } else {
                        val tgt = PtAssignTarget(false, assign.target.position)
                        val targetarray = assign.target.array!!
                        val targetArrayVar = targetarray.variable
                        if (targetArrayVar == null) {
                            TODO("optimized comparison on pointer ${targetarray.position}")
                        } else {
                            val array = PtArrayIndexer(assign.target.datatype, targetArrayVar.type.isSplitWordArray(program.memsizer), targetarray.position)
                            array.add(targetArrayVar)
                            array.add(targetarray.index)
                            tgt.add(array)
                            assignTrue = PtAssignment(assign.position)
                            assignTrue.add(tgt)
                            assignTrue.add(PtNumber.fromBoolean(true, assign.position))
                        }
                    }
                }
                TargetStorageKind.POINTER -> TODO("optimized comparison for pointer-deref $expr.position")
                TargetStorageKind.REGISTER -> { /* handled earlier */ return true }
                TargetStorageKind.VOID -> { /* do nothing */ return true }
            }
        }
        ifPart.add(assignTrue)
        val ifelse = PtIfElse(assign.position)
        val exprClone: PtBinaryExpression
        if(!asmgen.isTargetCpu(CpuType.VIRTUAL)
            && (expr.operator==">" || expr.operator=="<=")
            && expr.right.type.isWord) {
            // word X>Y -> X<Y, X<=Y -> Y>=X  , easier to do in 6502  (codegen also expects these to no longe exist!)
            exprClone = PtBinaryExpression(if(expr.operator==">") "<" else ">=", expr.type, expr.position)
            exprClone.add(expr.children[1]) // doesn't seem to need a deep clone
            exprClone.add(expr.children[0]) // doesn't seem to need a deep clone
        } else {
            exprClone = PtBinaryExpression(expr.operator, expr.type, expr.position)
            exprClone.add(expr.children[0]) // doesn't seem to need a deep clone
            exprClone.add(expr.children[1]) // doesn't seem to need a deep clone
        }
        ifelse.add(exprClone)
        ifelse.add(ifPart)
        ifelse.add(PtNodeGroup())
        ifelse.parent = expr.parent
        asmgen.translate(ifelse)
        return true
    }


    internal fun directIntoY(expr: PtExpression): Boolean {
        return when(expr) {
            is PtIdentifier -> true
            is PtIrRegister -> true
            is PtNumber -> true
            is PtFunctionCall -> expr.builtin && expr.name in arrayOf("lsb", "msb")
            else -> false
        }
    }


    internal fun optimizedRemainderExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        when {
            expr.type.isUnsignedByte -> {
                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                if(!directIntoY(expr.right)) asmgen.out("  pha")
                assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.Y, false)
                if(!directIntoY(expr.right)) asmgen.out("  pla")
                asmgen.out("  jsr  prog8_math.remainder_ub_asm")
                if(target.register==RegisterOrPair.A)
                    asmgen.out("  cmp  #0")     // fix the status register
                else
                    assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, false, true)
                return true
            }
            expr.type.isUnsignedWord -> {
                asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                asmgen.out("  jsr  prog8_math.divmod_uw_asm")
                assignmentAsmGen.assignVariableWord(target, "P8ZP_SCRATCH_W2", DataType.UWORD)
                return true
            }
            expr.type.isSignedByte -> {
                asmgen.errors.err("remainder can only be used on unsigned integer operands on 6502 target for now", expr.right.position)
                return true
                // TODO implement the signed remainder asm routine
//                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, true)
//                if(!directIntoY(expr.right)) asmgen.out("  pha")
//                assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.Y, true)
//                if(!directIntoY(expr.right)) asmgen.out("  pla")
//                asmgen.out("  jsr  prog8_math.divmod_b_asm")
//                if(target.register==RegisterOrPair.A)
//                    asmgen.out("  cmp  #0")     // fix the status register
//                else
//                    assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, true, true)
//                return true
            }
            expr.type.isSignedWord -> {
                asmgen.errors.err("remainder can only be used on unsigned integer operands on 6502 target for now", expr.right.position)
                return true
                // TODO implement the signed remainder asm routine
//                asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
//                asmgen.out("  jsr  prog8_math.divmod_w_asm")
//                assignmentAsmGen.assignVariableWord(target, "cx16.r15", DataType.WORD)
//                return true
            }
            expr.type.isLong -> {
                asmgen.errors.err("remainder can only be used on unsigned integer operands on 6502 target for now", expr.right.position)
                return true
                // TODO implement the signed long remainder asm routine
            }
            else -> return false
        }
    }


    internal fun optimizedDivideExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        // replacing division by shifting is done in an optimizer step.
        when {
            expr.type.isUnsignedByte -> {
                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                if(!directIntoY(expr.right)) asmgen.out("  pha")
                assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.Y, false)
                if(!directIntoY(expr.right)) asmgen.out("  pla")
                asmgen.out("  jsr  prog8_math.divmod_ub_asm")
                assignmentAsmGen.assignRegisterByte(target, CpuRegister.Y, false, true)
                return true
            }
            expr.type.isSignedByte -> {
                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, true)
                if(!directIntoY(expr.right)) asmgen.out("  pha")
                assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.Y, true)
                if(!directIntoY(expr.right)) asmgen.out("  pla")
                asmgen.out("  jsr  prog8_math.divmod_b_asm")
                assignmentAsmGen.assignRegisterByte(target, CpuRegister.Y, true, true)
                return true
            }
            expr.type.isUnsignedWord -> {
                asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                asmgen.out("  jsr  prog8_math.divmod_uw_asm")
                assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                return true
            }
            expr.type.isSignedWord -> {
                asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                asmgen.out("  jsr  prog8_math.divmod_w_asm")
                assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                return true
            }
            expr.type.isLong -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.R12R13, true)
                if(!assignmentAsmGen.isRightTrivial(expr.right))
                    asmgen.pushLongRegisters(RegisterOrPair.R12R13, 1)
                asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.R14R15, true)
                if(!assignmentAsmGen.isRightTrivial(expr.right))
                    asmgen.popLongRegisters(RegisterOrPair.R12R13, 1)
                asmgen.out("  jsr  prog8_math.div_longs")
                assignmentAsmGen.assignRegisterLong(target, RegisterOrPair.R14R15)
                return true
            }
            else -> return false
        }
    }


    internal fun optimizedMultiplyExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        val value = expr.right.asConstInteger()
        if(value==null) {
            when {
                expr.type.isByte -> {
                    assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, expr.type.isSigned)
                    if(!directIntoY(expr.right)) asmgen.out("  pha")
                    assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.Y, expr.type.isSigned)
                    if(!directIntoY(expr.right)) asmgen.out("  pla")
                    asmgen.out("  jsr  prog8_math.multiply_bytes")
                    assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, false, true)
                    return true
                }
                expr.type.isWord -> {
                    if(expr.definingBlock()!!.options.veraFxMuls) {
                        // cx16 verafx hardware mul
                        if(expr.right.isSimple()) {
                            asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.R0, expr.left.type.isSigned)
                            asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.R1, expr.left.type.isSigned)
                        } else {
                            asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.left.type.isSigned)
                            asmgen.out("  pha")
                            asmgen.saveRegisterStack(CpuRegister.Y, false)
                            asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.R1, expr.left.type.isSigned)
                            asmgen.restoreRegisterStack(CpuRegister.Y, false)
                            asmgen.out("  pla")
                            asmgen.out("  sta  cx16.r0 |  sty  cx16.r0+1")
                        }
                        asmgen.out("  jsr  verafx.muls16")
                        assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    } else {
                        asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "prog8_math.multiply_words.multiplier")
                        asmgen.out("  jsr  prog8_math.multiply_words")
                        assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                    }
                    return true
                }
                expr.type.isLong -> {
                    asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.R12R13, true)
                    if(!assignmentAsmGen.isRightTrivial(expr.right))
                        asmgen.pushLongRegisters(RegisterOrPair.R12R13, 1)
                    asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.R14R15, true)
                    if(!assignmentAsmGen.isRightTrivial(expr.right))
                        asmgen.popLongRegisters(RegisterOrPair.R12R13, 1)
                    asmgen.out("  jsr  prog8_math.multiply_longs")
                    assignmentAsmGen.assignRegisterLong(target, RegisterOrPair.R14R15)
                    return true
                }
                else -> return false
            }
        } else {
            if(!expr.right.type.isFloat && (value==0 || value==1))
                throw AssemblyError("multiplication by 0 or 1 should not happen ${expr.position}")

            when {
                expr.type.isByte -> {
                    assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, expr.type.isSigned)
                    if (value in asmgen.optimizedByteMultiplications)
                        asmgen.out("  jsr  prog8_math.mul_byte_${value}")
                    else if(value in powersOfTwoInt) {
                        val shifts = log2(value.toDouble()).toInt()
                        if(shifts>=8) {
                            asmgen.out("  lda  #0")
                        } else {
                            repeat(shifts) { asmgen.out("  asl  a") }
                        }
                    }
                    else
                        asmgen.out("  ldy  #$value |  jsr  prog8_math.multiply_bytes")
                    assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, false, true)
                    return true
                }
                expr.type.isWord -> {
                    if (value in asmgen.optimizedWordMultiplications) {
                        assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.type.isSigned)
                        asmgen.out("  jsr  prog8_math.mul_word_${value}")
                    }
                    else if(value in powersOfTwoInt) {
                        val shifts = log2(value.toDouble()).toInt()
                        if(shifts>=16) {
                            assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.type.isSigned)
                            asmgen.out("  lda  #0 |  ldy  #0")
                        } else {
                            if(target.kind==TargetStorageKind.VARIABLE && target.datatype.isWord) {
                                assignmentAsmGen.assignExpressionToVariable(expr.left, target.asmVarname, target.datatype)
                                repeat(shifts) { asmgen.out("  asl  ${target.asmVarname} |  rol  ${target.asmVarname}+1") }
                                return true
                            } else {
                                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.type.isSigned)
                                asmgen.out("  sty  P8ZP_SCRATCH_REG")
                                repeat(shifts) { asmgen.out("  asl  a |  rol  P8ZP_SCRATCH_REG") }
                                asmgen.out("  ldy  P8ZP_SCRATCH_REG")
                            }
                        }
                    }
                    else {
                        if(expr.definingBlock()!!.options.veraFxMuls){
                            // cx16 verafx hardware mul
                            asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "cx16.r1")
                            asmgen.out("""
                                sta  cx16.r0
                                sty  cx16.r0+1
                                jsr  verafx.muls16""")
                        } else {
                            asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "prog8_math.multiply_words.multiplier")
                            asmgen.out("  jsr  prog8_math.multiply_words")
                        }
                    }
                    assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }
                expr.type.isLong -> {
                    // long * constant
                    asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.R12R13, true)
                    asmgen.out("  lda  #<${value} |  sta  cx16.r14")
                    asmgen.out("  lda  #>${value} |  sta  cx16.r14+1")
                    asmgen.out("  lda  #<(${value}>>16) |  sta  cx16.r15")
                    asmgen.out("  lda  #>(${value}>>16) |  sta  cx16.r15+1")
                    asmgen.out("  jsr  prog8_math.multiply_longs")
                    assignmentAsmGen.assignRegisterLong(target, RegisterOrPair.R14R15)
                    return true
                }
                else -> return false
            }
        }
    }


    internal fun optimizedBitshiftExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        val signed = expr.left.type.isSigned
        val shifts = expr.right.asConstInteger()
        val dt = expr.left.type
        if(shifts==null) {
            // bit shifts with variable shifts
            when {
                expr.right.type.isByte -> {
                    assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.A, false)
                }
                expr.right.type.isWord -> {
                    assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.AY, false)
                    asmgen.out("""
                        cpy  #0
                        beq  +
                        lda  #127
+""")
                }
                else -> throw AssemblyError("weird shift value type")
            }
            asmgen.out("  pha")
            if(dt.isByte) {
                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                asmgen.restoreRegisterStack(CpuRegister.Y, true)
                if(expr.operator==">>")
                    if(signed)
                        asmgen.out("  jsr  prog8_math.lsr_byte_A")
                    else
                        asmgen.out("  jsr  prog8_math.lsr_ubyte_A")
                else
                    asmgen.out("  jsr  prog8_math.asl_byte_A")
                assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, signed, true)
                return true
            } else if(dt.isWord) {
                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.AY, signed)
                asmgen.restoreRegisterStack(CpuRegister.X, true)
                if(expr.operator==">>")
                    if(signed)
                        asmgen.out("  jsr  prog8_math.lsr_word_AY")
                    else
                        asmgen.out("  jsr  prog8_math.lsr_uword_AY")
                else
                    asmgen.out("  jsr  prog8_math.asl_word_AY")
                assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                return true
            } else if(dt.isLong) {

                if(target.kind==TargetStorageKind.VARIABLE) {
                    asmgen.out("  pla")
                    asmgen.assignExpressionTo(expr.left, target)
                    require(expr.right.type.isByte)
                    asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.X)
                    asmgen.out("  lda  #<${target.asmVarname} |  ldy  #>${target.asmVarname}")
                    if (expr.operator == "<<") {
                        asmgen.out("  jsr  prog8_lib.long_shiftleftX_inplace")
                    } else {
                        asmgen.out("  jsr  prog8_lib.long_shiftrightX_inplace")
                    }
                    return true
                }

                // TODO: cannot preserve R14:R15 on the stack here because the stack already contains a value that we need to pop
                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.R14R15, signed)
                asmgen.out("  pla |  sta  P8ZP_SCRATCH_REG")
                assignmentAsmGen.augmentableAsmGen.inplacemodificationLongWithVariable("cx16.r14", expr.operator, "P8ZP_SCRATCH_REG")
                assignmentAsmGen.assignRegisterLong(target, RegisterOrPair.R14R15)
                return true
            }
        }
        else {
            // bit shift with constant value
            if(dt.isByte) {
                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                when (shifts) {
                    in 0..7 -> {
                        if (expr.operator == "<<") {
                            repeat(shifts) {
                                asmgen.out("  asl  a")
                            }
                        } else {
                            if (signed && shifts > 0) {
                                if(shifts==1)
                                    asmgen.out("  cmp  #$80 |  ror  a")
                                else
                                    asmgen.out("  ldy  #$shifts |  jsr  prog8_math.lsr_byte_A")
                            } else {
                                repeat(shifts) {
                                    asmgen.out("  lsr  a")
                                }
                            }
                        }
                        assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, signed, true)
                        return true
                    }
                    else -> {
                        if(signed && expr.operator==">>") {
                            asmgen.out("  ldy  #$shifts |  jsr  prog8_math.lsr_byte_A")
                        } else {
                            asmgen.out("  lda  #0")
                        }
                        assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, signed, true)
                        return true
                    }
                }
            } else if(dt.isWord) {
                if(shifts==7 && expr.operator == "<<") {
                    // optimized shift left 7 (*128) by swapping the lsb/msb and then doing just one final shift
                    assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.AY, signed)
                    asmgen.out("""
                        ; shift left 7
                        sty  P8ZP_SCRATCH_REG
                        lsr  P8ZP_SCRATCH_REG
                        ror  a
                        sta  P8ZP_SCRATCH_REG
                        lda  #0
                        ror  a
                        ldy  P8ZP_SCRATCH_REG""")
                    assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }

                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.AY, signed)
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
                                if(shifts==1) {
                                    asmgen.out("""
                                        pha
                                        tya
                                        cmp  #$80
                                        ror  a
                                        tay
                                        pla
                                        ror  a""")
                                }
                                else
                                    asmgen.out("  ldx  #$shifts |  jsr  prog8_math.lsr_word_AY")
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
                        assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
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
                            asmgen.out("  ldx  #$shifts")
                            if(signed)
                                asmgen.out("  jsr  prog8_math.lsr_word_AY")
                            else
                                asmgen.out("  jsr  prog8_math.lsr_uword_AY")
                        }
                        assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                    else -> {
                        if(signed && expr.operator==">>") {
                            asmgen.out("  ldx  #$shifts |  jsr  prog8_math.lsr_word_AY")
                        } else {
                            asmgen.out("  lda  #0 |  ldy  #0")
                        }
                        assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                }
            } else if(dt.isLong) {

                if(target.kind==TargetStorageKind.VARIABLE) {
                    asmgen.assignExpressionTo(expr.left, target)
                    require(expr.right.type.isByte)
                    asmgen.out("  lda  #<${target.asmVarname} |  ldy  #>${target.asmVarname}")
                    asmgen.out("  ldx  #$shifts")
                    if (expr.operator == "<<") {
                        asmgen.out("  jsr  prog8_lib.long_shiftleftX_inplace")
                    } else {
                        asmgen.out("  jsr  prog8_lib.long_shiftrightX_inplace")
                    }
                    return true
                }

                if(target.register!=RegisterOrPair.R14R15)
                    asmgen.pushLongRegisters(RegisterOrPair.R14R15, 1)
                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.R14R15, signed)
                assignmentAsmGen.augmentableAsmGen.inplacemodificationLongWithLiteralval("cx16.r14", expr.operator, shifts)
                assignmentAsmGen.assignRegisterLong(target, RegisterOrPair.R14R15)
                if(target.register!=RegisterOrPair.R14R15)
                    asmgen.popLongRegisters(RegisterOrPair.R14R15, 1)
                return true
            }
        }
        return false
    }


    internal fun getIdentForNoopCastOrIdent(expr: PtExpression): Pair<String, DataType>? {
        // Returns the variable name and type if the expression is:
        // 1. A plain identifier (returns its name and type)
        // 2. A no-op type cast (word<->uword) - unwraps and returns the inner identifier
        // Otherwise returns null
        val identifier = when (expr) {
            is PtIdentifier -> expr
            is PtTypeCast -> {
                // Check if this is a no-op cast (word<->uword)
                if (expr.value is PtIdentifier &&
                    ((expr.type.isWord && expr.value.type.isUnsignedWord) ||
                     (expr.type.isUnsignedWord && expr.value.type.isWord) ||
                     (expr.type.isByte && expr.value.type.isByte))) {
                    expr.value as PtIdentifier
                } else {
                    return null
                }
            }
            else -> return null
        }
        return Pair(asmgen.asmVariableName(identifier), identifier.type)
    }


    internal fun optimizedPlusMinExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        val dt = expr.type
        val left = expr.left
        val right = expr.right
        if(dt.isByte) {
            when (right) {
                is PtIdentifier -> {
                    assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.A, dt.isSigned)
                    val symname = asmgen.asmVariableName(right)
                    if(expr.operator=="+")
                        asmgen.out("  clc |  adc  $symname")
                    else
                        asmgen.out("  sec |  sbc  $symname")
                    assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, dt.isSigned, true)
                    return true
                }
                is PtNumber -> {
                    assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.A, dt.isSigned)
                    if(right.number==1.0 && asmgen.isTargetCpu(CpuType.CPU65C02)) {
                        if (expr.operator == "+")
                            asmgen.out("  inc  a")
                        else
                            asmgen.out("  dec  a")
                    } else {
                        if (expr.operator == "+")
                            asmgen.out("  clc |  adc  #${right.number.toHex()}")
                        else
                            asmgen.out("  sec |  sbc  #${right.number.toHex()}")
                    }
                    assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, dt.isSigned, target.datatype.isWord)
                    return true
                }
                else -> {
                    val leftMemByte = expr.left as? PtMemoryByte
                    val rightMemByte = expr.right as? PtMemoryByte
                    val leftArrayIndexer = expr.left as? PtArrayIndexer
                    val rightArrayIndexer = expr.right as? PtArrayIndexer
                    if(expr.operator=="+" && leftArrayIndexer!=null && leftArrayIndexer.type.isByte && right.type.isByte) {
                        // special optimization for  bytearray[y] + bytevalue :  no need to use a tempvar, just use adc array,y
                        val leftArrayVar = leftArrayIndexer.variable
                        if(leftArrayVar==null) {
                            TODO("optimized plusmin pointer ${leftArrayIndexer.position}")
                        } else {
                            assignmentAsmGen.assignExpressionToRegister(right, RegisterOrPair.A, right.type.isSigned)
                            if (!leftArrayIndexer.index.isSimple()) asmgen.out("  pha")
                            asmgen.assignExpressionToRegister(leftArrayIndexer.index, RegisterOrPair.Y)
                            if (!leftArrayIndexer.index.isSimple()) asmgen.out("  pla")
                            val arrayvarname = asmgen.asmSymbolName(leftArrayVar)
                            asmgen.out("  clc |  adc  $arrayvarname,y")
                            assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, dt.isSigned, true)
                        }
                    } else if(rightArrayIndexer!=null && rightArrayIndexer.type.isByte && left.type.isByte) {
                        // special optimization for  bytevalue +/- bytearray[y] :  no need to use a tempvar, just use adc array,y or sbc array,y
                        val rightArrayVar = rightArrayIndexer.variable
                        if(rightArrayVar==null) {
                            TODO("optimized plusmin pointer ${rightArrayIndexer.position}")
                        } else {
                            assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.A, left.type.isSigned)
                            if (!rightArrayIndexer.index.isSimple()) asmgen.out("  pha")
                            asmgen.assignExpressionToRegister(rightArrayIndexer.index, RegisterOrPair.Y)
                            if (!rightArrayIndexer.index.isSimple()) asmgen.out("  pla")
                            val arrayvarname = asmgen.asmSymbolName(rightArrayVar)
                            if (expr.operator == "+")
                                asmgen.out("  clc |  adc  $arrayvarname,y")
                            else
                                asmgen.out("  sec |  sbc  $arrayvarname,y")
                            assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, dt.isSigned, true)
                        }
                    } else if(expr.operator=="+" && leftMemByte!=null && right.type.isByte && optimizedPointerIndexPlusMinusByteIntoA(right, "+", leftMemByte)) {
                        assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, dt.isSigned, true)
                        return true
                    } else if(rightMemByte!=null && left.type.isByte && optimizedPointerIndexPlusMinusByteIntoA(left, expr.operator, rightMemByte)) {
                        assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, dt.isSigned, true)
                        return true
                    } else {
                        assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.A, left.type.isSigned)
                        if(directIntoY(right)) {
                            assignmentAsmGen.assignExpressionToRegister(right, RegisterOrPair.Y, left.type.isSigned)
                            asmgen.out("  sty  P8ZP_SCRATCH_B1")
                        } else {
                            asmgen.out("  pha")
                            assignmentAsmGen.assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", right.type)
                            asmgen.out("  pla")
                        }
                        if (expr.operator == "+")
                            asmgen.out("  clc |  adc  P8ZP_SCRATCH_B1")
                        else
                            asmgen.out("  sec |  sbc  P8ZP_SCRATCH_B1")
                        assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, dt.isSigned, true)
                    }
                    return true
                }
            }
        } else if(dt.isWord || dt.isPointer) {

            fun doAddOrSubWordExpr() {
                val leftAddr = asmgen.getStaticAddressLowHigh(expr.left)
                val rightAddr = asmgen.getStaticAddressLowHigh(expr.right)

                if (expr.operator == "+" && leftAddr != null) {
                    assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.AY, expr.right.type.isSigned)
                    asmgen.out("""
                            clc
                            adc  ${leftAddr.first}
                            tax
                            tya
                            adc  ${leftAddr.second}
                            tay
                            txa""")
                } else if (expr.operator == "+" && rightAddr != null) {
                    assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.left.type.isSigned)
                    asmgen.out("""
                            clc
                            adc  ${rightAddr.first}
                            tax
                            tya
                            adc  ${rightAddr.second}
                            tay
                            txa""")
                } else if (expr.operator == "-" && rightAddr != null) {
                    assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.left.type.isSigned)
                    asmgen.out("""
                            sec
                            sbc  ${rightAddr.first}
                            tax
                            tya
                            sbc  ${rightAddr.second}
                            tay
                            txa""")
                } else {
                    asmgen.assignWordOperandsToAYAndVar(expr.left, expr.right, "P8ZP_SCRATCH_W1")
                    if (expr.operator == "+")
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
                }
                assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
            }

            // Handle when left is PtAddressOf of a split word array
            val leftAddressOf = left as? PtAddressOf
            if(leftAddressOf?.identifier != null && !leftAddressOf.isFromArrayElement && leftAddressOf.dereference==null) {
                if(leftAddressOf.identifier!!.type.isSplitWordArray(program.memsizer)) {
                    var symbol = asmgen.asmVariableName(leftAddressOf.identifier!!)
                    symbol = if(leftAddressOf.isMsbForSplitArray) symbol+"_msb" else symbol+"_lsb"
                    assignmentAsmGen.assignExpressionToRegister(right, RegisterOrPair.AY, right.type.isSigned)
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
                    assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }
            }

            // Memory-to-memory optimization: when both operands and target are static variables
            // Also handles no-op type casts (word<->uword) by unwrapping them
            if(target.kind == TargetStorageKind.VARIABLE) {
                val leftInfo = getIdentForNoopCastOrIdent(expr.left)
                val rightInfo = getIdentForNoopCastOrIdent(expr.right)
                if(leftInfo!=null && rightInfo!=null) {
                    val targetSym = target.asmVarname
                    if(expr.operator=="+") {
                        asmgen.out("""
                                lda  ${leftInfo.first}
                                clc
                                adc  ${rightInfo.first}
                                sta  $targetSym
                                lda  ${leftInfo.first}+1
                                adc  ${rightInfo.first}+1
                                sta  $targetSym+1""")
                    } else {
                        asmgen.out("""
                                lda  ${leftInfo.first}
                                sec
                                sbc  ${rightInfo.first}
                                sta  $targetSym
                                lda  ${leftInfo.first}+1
                                sbc  ${rightInfo.first}+1
                                sta  $targetSym+1""")
                    }
                    return true
                }
            }

            when (right) {
                is PtAddressOf -> {
                    if(right.isFromArrayElement) {
                        TODO("address-of array element at ${right.position}")
                    } else if(right.dereference!=null) {
                        TODO("read &dereference ${right.position}")
                    } else {
                        var symbol = asmgen.asmVariableName(right.identifier!!)
                        if(right.identifier!!.type.isSplitWordArray(program.memsizer)) {
                            symbol = if(right.isMsbForSplitArray) symbol+"_msb" else symbol+"_lsb"
                        }
                        assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.AY, dt.isSigned)
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
                        assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                }
                is PtIdentifier -> {
                    val symname = asmgen.asmVariableName(right)
                    assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.AY, dt.isSigned)
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
                    assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }
                is PtConstant -> {
                    val addr = asmgen.getStaticAddressLowHigh(right)
                    if (addr != null) {
                        assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.AY, dt.isSigned)
                        if (expr.operator == "+")
                            asmgen.out("""
                                clc
                                adc  ${addr.first}
                                tax
                                tya
                                adc  ${addr.second}
                                tay
                                txa""")
                        else
                            asmgen.out("""
                                sec
                                sbc  ${addr.first}
                                tax
                                tya
                                sbc  ${addr.second}
                                tay
                                txa""")
                        assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                    doAddOrSubWordExpr()
                    return true
                }
                is PtNumber -> {
                    assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.AY, dt.isSigned)
                    if(right.number==1.0 && asmgen.isTargetCpu(CpuType.CPU65C02)) {
                        if(expr.operator=="+") {
                            asmgen.out("""
                                inc  a
                                bne  +
                                iny
+""")
                        } else {
                            asmgen.out("""
                                dec  a
                                cmp  #255
                                bne  +
                                dey
+""")
                        }
                    } else if(!dt.isSignedWord && right.number.toInt() in 0..255) {
                        if(expr.operator=="+") {
                            asmgen.out("""
                                clc
                                adc  #${right.number.toHex()}
                                bcc  +
                                iny
+""")                   } else if(expr.operator=="-") {
                            asmgen.out("""
                                sec
                                sbc  #${right.number.toHex()}
                                bcs  +
                                dey
+""")
                        }
                    } else {
                        if(expr.operator=="+") {
                            if(right.number.toInt() and 255 == 0) {
                                // can add only to the msb, which is in Y
                                if(right.number==256.0)
                                    asmgen.out("  iny")
                                else if(right.number==512.0)
                                    asmgen.out("  iny |  iny")
                                else
                                    asmgen.out("""
                                        tax
                                        tya
                                        clc
                                        adc  #>${right.number.toHex()}
                                        tay
                                        txa""")
                            } else {
                                asmgen.out("""
                                    clc
                                    adc  #<${right.number.toHex()}
                                    tax
                                    tya
                                    adc  #>${right.number.toHex()}
                                    tay
                                    txa""")
                            }
                        } else if(expr.operator=="-") {
                            if(right.number.toInt() and 255 == 0) {
                                // can sub only from the msb, which is in Y
                                if(right.number==256.0)
                                    asmgen.out("  dey")
                                else if(right.number==512.0)
                                    asmgen.out("  dey |  dey")
                                else
                                    asmgen.out("""
                                        tax
                                        tya
                                        sec
                                        sbc  #>${right.number.toHex()}
                                        tay
                                        txa""")
                                } else {
                                asmgen.out("""
                                    sec
                                    sbc  #<${right.number.toHex()}
                                    tax
                                    tya
                                    sbc  #>${right.number.toHex()}
                                    tay
                                    txa""")
                            }
                        }
                    }
                    assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }
                is PtTypeCast -> {
                    val castedValue = right.value
                    if(right.type.isWord && castedValue.type.isByte && castedValue is PtIdentifier) {
                        if(right.type.isSigned) {
                            // we need to sign extend, do this via temporary word variable
                            asmgen.assignExpressionToVariable(right, "P8ZP_SCRATCH_W1", DataType.WORD)
                            assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.AY, left.type.isSigned)
                            if(expr.operator=="+") {
                                asmgen.out("""
                                clc
                                adc  P8ZP_SCRATCH_W1
                                tax
                                tya
                                adc  P8ZP_SCRATCH_W1+1
                                tay
                                txa""")
                            } else if(expr.operator=="-") {
                                asmgen.out("""
                                sec
                                sbc  P8ZP_SCRATCH_W1
                                tax
                                tya
                                sbc  P8ZP_SCRATCH_W1+1
                                tay
                                txa""")
                            }
                        } else {
                            assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.AY, left.type.isSigned)
                            val castedSymname = asmgen.asmVariableName(castedValue)
                            if (expr.operator == "+")
                                asmgen.out("""
                                    clc
                                    adc  $castedSymname
                                    bcc  +
                                    iny
+"""
                            )
                            else
                                asmgen.out("""
                                    sec
                                    sbc  $castedSymname
                                    bcs  +
                                    dey
+"""
                            )
                        }
                        assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
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
        else if(dt.isLong) {
            return optimizedPlusMinLongExpr(expr, target)
        }
        return false
    }


    internal fun optimizedPlusMinLongExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        // Generic fallback for indexed pointer arrays: route to generic codegen (less efficient but avoids missing specialized cases)
        if(target.kind==TargetStorageKind.ARRAY && target.array?.pointerderef!=null) {
            asmgen.assignExpressionToRegister(expr, RegisterOrPair.R12R13, true)
            assignmentAsmGen.assignRegisterLong(target, RegisterOrPair.R12R13)
            return true
        }
        val left = expr.left
        val right = expr.right
        when(right) {
            is PtNumber -> {
                asmgen.assignExpressionTo(left, target)
                val hex = right.number.toLongHex()
                if (target.kind == TargetStorageKind.VARIABLE) {
                    if (expr.operator == "+") {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            clc
                            adc  #$${hex.substring(6,8)}
                            sta  ${target.asmVarname}
                            lda  ${target.asmVarname}+1
                            adc  #$${hex.substring(4, 6)}
                            sta  ${target.asmVarname}+1
                            lda  ${target.asmVarname}+2
                            adc  #$${hex.substring(2, 4)}
                            sta  ${target.asmVarname}+2
                            lda  ${target.asmVarname}+3
                            adc  #$${hex.take(2)}
                            sta  ${target.asmVarname}+3""")
                    } else {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            sec
                            sbc  #$${hex.substring(6, 8)}
                            sta  ${target.asmVarname}
                            lda  ${target.asmVarname}+1
                            sbc  #$${hex.substring(4, 6)}
                            sta  ${target.asmVarname}+1
                            lda  ${target.asmVarname}+2
                            sbc  #$${hex.substring(2, 4)}
                            sta  ${target.asmVarname}+2
                            lda  ${target.asmVarname}+3
                            sbc  #$${hex.take(2)}
                            sta  ${target.asmVarname}+3""")
                    }
                    return true
                } else if(target.kind == TargetStorageKind.REGISTER) {
                    val startreg = target.register!!.startregname()
                    if(expr.operator=="+") {
                        asmgen.out("""
                            lda  cx16.$startreg
                            clc
                            adc  #$${hex.substring(6,8)}
                            sta  cx16.$startreg
                            lda  cx16.$startreg+1
                            adc  #$${hex.substring(4, 6)}
                            sta  cx16.$startreg+1
                            lda  cx16.$startreg+2
                            adc  #$${hex.substring(2, 4)}
                            sta  cx16.$startreg+2
                            lda  cx16.$startreg+3
                            adc  #$${hex.take(2)}
                            sta  cx16.$startreg+3""")
                    } else {
                        asmgen.out("""
                            lda  cx16.$startreg
                            sec
                            sbc  #$${hex.substring(6,8)}
                            sta  cx16.$startreg
                            lda  cx16.$startreg+1
                            sbc  #$${hex.substring(4, 6)}
                            sta  cx16.$startreg+1
                            lda  cx16.$startreg+2
                            sbc  #$${hex.substring(2, 4)}
                            sta  cx16.$startreg+2
                            lda  cx16.$startreg+3
                            sbc  #$${hex.take(2)}
                            sta  cx16.$startreg+3""")
                    }
                    return true
                } else if(target.kind==TargetStorageKind.POINTER) {
                    // not an expression, no need to save R14/R15
                    assignmentAsmGen.assignExpressionToRegister(expr, RegisterOrPair.R14R15, target.datatype.isSigned)
                    pointergen.assignLongVar(target.pointer!!, "cx16.r14")
                    return true
                } else {
                    TODO("add/subtract long const into ${target.kind} ${target.position} - please report this issue. Use simple expressions and temporary variables for now")
                }
            }
            is PtIdentifier -> {
                if(target.kind == TargetStorageKind.VARIABLE) {
                    asmgen.assignExpressionTo(left, target)
                    val rightsym = asmgen.asmVariableName(right)
                    asmgen.out("""
                        lda  #<$rightsym
                        ldy  #>$rightsym
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #<${target.asmVarname}
                        ldy  #>${target.asmVarname}""")
                    if (expr.operator == "+") {
                        asmgen.out("  jsr  prog8_lib.long_add_inplace")
                    } else {
                        asmgen.out("  jsr  prog8_lib.long_sub_inplace")
                    }
                    return true
                } else if(target.kind == TargetStorageKind.REGISTER) {
                    val startreg = target.register!!.startregname()
                    asmgen.assignExpressionTo(left, target)
                    val rightsym = asmgen.asmVariableName(right)
                    asmgen.out(
                        """
                        lda  #<$rightsym
                        ldy  #>$rightsym
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #<cx16.$startreg
                        ldy  #>cx16.$startreg"""
                    )
                    if (expr.operator == "+") {
                        asmgen.out("  jsr  prog8_lib.long_add_inplace")
                    } else {
                        asmgen.out("  jsr  prog8_lib.long_sub_inplace")
                    }
                    return true
                } else {
                    // isn't an expression, so no need to preserve R12-R15
                    asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.R12R13, left.type.isSigned)
                    asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.R14R15, left.type.isSigned)
                    if(expr.operator=="+") {
                        asmgen.out("""
                            clc
                            lda  cx16.r12
                            adc  cx16.r14
                            sta  cx16.r12
                            lda  cx16.r12+1
                            adc  cx16.r14+1
                            sta  cx16.r12+1
                            lda  cx16.r12+2
                            adc  cx16.r14+2
                            sta  cx16.r12+2
                            lda  cx16.r12+3
                            adc  cx16.r14+3
                            sta  cx16.r12+3""")
                    } else {
                        asmgen.out("""
                            sec
                            lda  cx16.r12
                            sbc  cx16.r14
                            sta  cx16.r12
                            lda  cx16.r12+1
                            sbc  cx16.r14+1
                            sta  cx16.r12+1
                            lda  cx16.r12+2
                            sbc  cx16.r14+2
                            sta  cx16.r12+2
                            lda  cx16.r12+3
                            sbc  cx16.r14+3
                            sta  cx16.r12+3""")
                    }
                    asmgen.assignRegister(RegisterOrPair.R12R13, target)
                    return true
                }
            }
            else -> {
                val targetreg = target.register
                if(targetreg==RegisterOrPair.R14R15)
                    asmgen.pushLongRegisters(RegisterOrPair.R12R13, 1)
                else
                    asmgen.pushLongRegisters(RegisterOrPair.R12R13, 2)
                assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.R14R15, expr.left.type.isSigned)
                // save left operand because right eval may clobber R14R15
                asmgen.pushLongRegisters(RegisterOrPair.R14R15, 1)
                assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.R12R13, expr.right.type.isSigned)
                // restore left operand
                asmgen.popLongRegisters(RegisterOrPair.R14R15, 1)
                assignmentAsmGen.augmentableAsmGen.inplacemodificationLongWithVariable("cx16.r14", expr.operator, "cx16.r12")
                assignmentAsmGen.assignRegisterLong(target, RegisterOrPair.R14R15)
                if(targetreg==RegisterOrPair.R14R15)
                    asmgen.popLongRegisters(RegisterOrPair.R12R13, 1)
                else
                    asmgen.popLongRegisters(RegisterOrPair.R12R13, 2)
                return true
            }
        }
    }


    internal fun optimizedPointerIndexPlusMinusByteIntoA(value: PtExpression, operator: String, mem: PtMemoryByte): Boolean {
        // special optimization for  bytevalue +/- pointervar[y]  (actually: bytevalue +/-  @(address) )
        val address = mem.address as? PtBinaryExpression
        if(address is PtBinaryExpression) {
            val constOffset = address.right.asConstInteger()
            // check that the offset is actually a byte (so that it fits in a single register)
            if(constOffset==null && !address.right.type.isByte)
                return false
            if(constOffset!=null && constOffset !in -128..255)
                return false
            val ptrVar = address.left as? PtIdentifier
            if(ptrVar!=null && asmgen.isZpVar(ptrVar)) {
                assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.A, false)
                val pointername = asmgen.asmVariableName(ptrVar)
                if (constOffset != null) {
                    // we have value + @(zpptr + 255), or value - @(zpptr+255).   the offset is always <256.
                    asmgen.out("  ldy  #$constOffset")
                    if (operator == "+")
                        asmgen.out("  clc |  adc  ($pointername),y")
                    else
                        asmgen.out("  sec |  sbc  ($pointername),y")
                } else if (address.right.type.isByte) {
                    // we have @(ptr + bytevar) ++ , or  @(ptr+bytevar)--
                    asmgen.out("  pha")
                    assignmentAsmGen.assignExpressionToRegister(address.right, RegisterOrPair.Y, false)
                    asmgen.out("  pla")
                    if (operator == "+")
                        asmgen.out("  clc |  adc  ($pointername),y")
                    else
                        asmgen.out("  sec |  sbc  ($pointername),y")
                } else if ((address.right as? PtTypeCast)?.value?.type?.isByte==true) {
                    // we have @(ptr + bytevar as uword) ++ , or  @(ptr+bytevar as uword)--
                    asmgen.out("  pha")
                    assignmentAsmGen.assignExpressionToRegister((address.right as PtTypeCast).value, RegisterOrPair.Y, false)
                    asmgen.out("  pla")
                    if (operator == "+")
                        asmgen.out("  clc |  adc  ($pointername),y")
                    else
                        asmgen.out("  sec |  sbc  ($pointername),y")
                }
                return true
            }
        }
        return false
    }


    internal fun optimizedBitwiseExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        if (expr.left.type.isByte && expr.right.type.isByte) {
            if (expr.right.isSimple()) {
                if (expr.right is PtNumber || expr.right is PtIdentifier) {
                    assignBitwiseWithSimpleRightOperandByte(target, expr.left, expr.operator, expr.right)
                    return true
                }
                else if (expr.left is PtNumber || expr.left is PtIdentifier) {
                    assignBitwiseWithSimpleRightOperandByte(target, expr.right, expr.operator, expr.left)
                    return true
                }
            }
            val rightArray = expr.right as? PtArrayIndexer
            if(rightArray!=null) {
                val rightArrayVar = rightArray.variable
                if(rightArrayVar==null) {
                    TODO("optimized bitwise assignment from not a variable ${rightArray.position}")
                    return false
                }

                val constIndex = rightArray.index.asConstInteger()
                if(constIndex!=null) {
                    assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                    val valueVarname = "${asmgen.asmSymbolName(rightArrayVar)} + ${program.memsizer.memorySize(rightArray.type, constIndex)}"
                    when(expr.operator) {
                        "&" -> asmgen.out("  and  $valueVarname")
                        "|" -> asmgen.out("  ora  $valueVarname")
                        "^" -> asmgen.out("  eor  $valueVarname")
                        else -> throw AssemblyError("invalid logical operator")
                    }
                    assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, false, true)
                    return true
                }
            }

            assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
            if(directIntoY(expr.right)) {
                assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.Y, false)
                asmgen.out("  sty  P8ZP_SCRATCH_B1")
            } else {
                asmgen.out("  pha")
                assignmentAsmGen.assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.out("  pla")
            }
            when (expr.operator) {
                "&" -> asmgen.out("  and  P8ZP_SCRATCH_B1")
                "|" -> asmgen.out("  ora  P8ZP_SCRATCH_B1")
                "^" -> asmgen.out("  eor  P8ZP_SCRATCH_B1")
                else -> throw AssemblyError("invalid bitwise operator")
            }
            assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, false, true)
            return true
        }
        else if (expr.left.type.isWord && expr.right.type.isWord) {
            if (expr.right.isSimple()) {
                if (expr.right is PtNumber || expr.right is PtIdentifier) {
                    assignBitwiseWithSimpleRightOperandWord(target, expr.left, expr.operator, expr.right)
                    return true
                }
                else if (expr.left is PtNumber || expr.left is PtIdentifier) {
                    assignBitwiseWithSimpleRightOperandWord(target, expr.right, expr.operator, expr.left)
                    return true
                }
            }
            asmgen.assignWordOperandsToAYAndVar(expr.left, expr.right, "P8ZP_SCRATCH_W1")
            when (expr.operator) {
                "&" -> asmgen.out("  and  P8ZP_SCRATCH_W1 |  tax |  tya |  and  P8ZP_SCRATCH_W1+1 |  tay |  txa")
                "|" -> asmgen.out("  ora  P8ZP_SCRATCH_W1 |  tax |  tya |  ora  P8ZP_SCRATCH_W1+1 |  tay |  txa")
                "^" -> asmgen.out("  eor  P8ZP_SCRATCH_W1 |  tax |  tya |  eor  P8ZP_SCRATCH_W1+1 |  tay |  txa")
                else -> throw AssemblyError("invalid bitwise operator")
            }
            assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
            return true
        }
        else if (expr.left.type.isLong && expr.right.type.isLong) {

            if(target.kind == TargetStorageKind.VARIABLE) {
                if(expr.left is PtIdentifier) {
                    asmgen.assignExpressionTo(expr.right, target)
                    val varname = asmgen.asmVariableName(expr.left as PtIdentifier)
                    asmgen.out("""
                        lda  #<$varname
                        ldy  #>$varname
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #<${target.asmVarname}
                        ldy  #>${target.asmVarname}""")
                    when(expr.operator) {
                        "|" -> asmgen.out("  jsr  prog8_lib.long_or_inplace")
                        "&" -> asmgen.out("  jsr  prog8_lib.long_and_inplace")
                        "^" -> asmgen.out("  jsr  prog8_lib.long_xor_inplace")
                        else -> throw AssemblyError("wrong bitwise operator")
                    }
                    return true
                } else if(expr.right is PtIdentifier) {
                    asmgen.assignExpressionTo(expr.left, target)
                    val varname = asmgen.asmVariableName(expr.right as PtIdentifier)
                    asmgen.out("""
                        lda  #<$varname
                        ldy  #>$varname
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #<${target.asmVarname}
                        ldy  #>${target.asmVarname}""")
                    when(expr.operator) {
                        "|" -> asmgen.out("  jsr  prog8_lib.long_or_inplace")
                        "&" -> asmgen.out("  jsr  prog8_lib.long_and_inplace")
                        "^" -> asmgen.out("  jsr  prog8_lib.long_xor_inplace")
                        else -> throw AssemblyError("wrong bitwise operator")
                    }
                    return true
                }
            }

            val targetreg = target.register
            if(targetreg!=RegisterOrPair.R14R15) {
                asmgen.pushLongRegisters(RegisterOrPair.R14R15, 1)
            }
            asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.R14R15, expr.left.type.isSigned)
            val constval = expr.right.asConstInteger()
            val varname = (expr.right as? PtIdentifier)?.name
            if(constval!=null)
                assignmentAsmGen.augmentableAsmGen.inplacemodificationLongWithLiteralval("cx16.r14", expr.operator, constval)
            else if(varname!=null)
                assignmentAsmGen.augmentableAsmGen.inplacemodificationLongWithVariable("cx16.r14", expr.operator, varname)
            else {
                if(!assignmentAsmGen.isRightTrivial(expr.right))
                    asmgen.pushLongRegisters(RegisterOrPair.R14R15, 1)
                assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.R12R13, expr.right.type.isSigned)
                if(!assignmentAsmGen.isRightTrivial(expr.right))
                    asmgen.popLongRegisters(RegisterOrPair.R14R15, 1)
                assignmentAsmGen.augmentableAsmGen.inplacemodificationLongWithVariable("cx16.r14", expr.operator, "cx16.r12")
            }
            assignmentAsmGen.assignRegisterLong(target, RegisterOrPair.R14R15)
            if(targetreg!=RegisterOrPair.R14R15) {
                asmgen.popLongRegisters(RegisterOrPair.R14R15, 1)
            }
            return true
        }
        return false
    }


    internal fun optimizedLogicalExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {

        fun swapOperands(): Boolean =
            if(expr.right is PtIdentifier || expr.right is PtMemoryByte)
                false
            else
                expr.left is PtIdentifier || expr.left is PtMemoryByte

        fun assignResultIntoA(left: PtExpression, operator: String, right: PtExpression) {
            // non short-circuit evaluation it is *likely* shorter and faster because of the simple operands.

            fun assignViaScratch() {
                if(directIntoY(right)) {
                    assignmentAsmGen.assignExpressionToRegister(right, RegisterOrPair.Y, false)
                    asmgen.out("  sty  P8ZP_SCRATCH_B1")
                } else {
                    asmgen.out("  pha")
                    assignmentAsmGen.assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                    asmgen.out("  pla")
                }
                when (operator) {
                    "and" -> asmgen.out("  and  P8ZP_SCRATCH_B1")
                    "or" -> asmgen.out("  ora  P8ZP_SCRATCH_B1")
                    "xor" -> asmgen.out("  eor  P8ZP_SCRATCH_B1")
                    else -> throw AssemblyError("invalid logical operator")
                }
            }

            assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.A, false)
            when(right) {
                is PtBool -> {
                    // Handle boolean literals that weren't optimized away (e.g., with -noopt)
                    when {
                        operator == "and" && right.value -> {
                            // left AND true = left (already in A, do nothing)
                        }
                        operator == "and" && !right.value -> {
                            // left AND false = false
                            asmgen.out("  lda  #0")
                        }
                        operator == "or" && right.value -> {
                            // left OR true = true
                            asmgen.out("  lda  #1")
                        }
                        operator == "or" && !right.value -> {
                            // left OR false = left (already in A, do nothing)
                        }
                        operator == "xor" && right.value -> {
                            // left XOR true = NOT left (invert low bit)
                            asmgen.out("  eor  #1")
                        }
                        operator == "xor" && !right.value -> {
                            // left XOR false = left (already in A, do nothing)
                        }
                    }
                }
                is PtIdentifier -> {
                    val varname = asmgen.asmVariableName(right)
                    when (operator) {
                        "and" -> asmgen.out("  and  $varname")
                        "or" -> asmgen.out("  ora  $varname")
                        "xor" -> asmgen.out("  eor  $varname")
                        else -> throw AssemblyError("invalid logical operator")
                    }
                }
                is PtMemoryByte -> {
                    val constAddress = right.address.asConstInteger()
                    if(constAddress!=null) {
                        when (operator) {
                            "and" -> asmgen.out("  and  ${constAddress.toHex()}")
                            "or" -> asmgen.out("  ora  ${constAddress.toHex()}")
                            "xor" -> asmgen.out("  eor  ${constAddress.toHex()}")
                            else -> throw AssemblyError("invalid logical operator")
                        }
                    }
                    else assignViaScratch()
                }
                is PtArrayIndexer -> {
                    val constIndex = right.index.asConstInteger()
                    if(constIndex!=null) {
                        val rightArrayVar = right.variable
                        if(rightArrayVar==null) {
                            TODO("assign result into A pointer ${right.position}")
                        } else {
                            val valueVarname = "${asmgen.asmSymbolName(rightArrayVar)} + ${program.memsizer.memorySize(right.type, constIndex)}"
                            when(operator) {
                                "and" -> asmgen.out("  and  $valueVarname")
                                "or" -> asmgen.out("  ora  $valueVarname")
                                "xor" -> asmgen.out("  eor  $valueVarname")
                                else -> throw AssemblyError("invalid logical operator")
                            }
                        }
                    }
                    else assignViaScratch()
                }
                else -> assignViaScratch()
            }
        }

        fun requiresCmp(expr: PtExpression) =
            when (expr) {
                is PtFunctionCall -> {
                    if(expr.builtin)
                        true
                    else {
                        val function = asmgen.symbolTable.lookup(expr.name)
                        function is StExtSub        // don't assume the extsub/asmsub has set the cpu flags correctly on exit, add an explicit cmp
                    }
                }
                is PtIfExpression -> true
                else -> false
            }

        if(!expr.right.isSimple() && expr.operator!="xor") {
            // shortcircuit evaluation into A
            val shortcutLabel = asmgen.makeLabel("shortcut")
            when (expr.operator) {
                "and" -> {
                    // short-circuit  LEFT and RIGHT  -->  if LEFT then RIGHT else LEFT   (== if !LEFT then LEFT else RIGHT)
                    assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                    if(requiresCmp(expr.left))
                        asmgen.out("  cmp  #0")
                    asmgen.out("  beq  $shortcutLabel")
                    assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.A, false)
                    if(requiresCmp(expr.right))
                        asmgen.out("  cmp  #0")
                    asmgen.out(shortcutLabel)
                }
                "or" -> {
                    // short-circuit  LEFT or RIGHT  -->  if LEFT then LEFT else RIGHT
                    assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                    if(requiresCmp(expr.left))
                        asmgen.out("  cmp  #0")
                    asmgen.out("  bne  $shortcutLabel")
                    assignmentAsmGen.assignExpressionToRegister(expr.right, RegisterOrPair.A, false)
                    if(requiresCmp(expr.right))
                        asmgen.out("  cmp  #0")
                    asmgen.out(shortcutLabel)
                }
                else -> throw AssemblyError("invalid logical operator")
            }
        } else if(swapOperands()) {
            // non short-circuit evaluation is *likely* shorter and faster because of the simple operands.
            assignResultIntoA(expr.right, expr.operator, expr.left)
        } else {
            // non short-circuit evaluation is *likely* shorter and faster because of the simple operands.
            assignResultIntoA(expr.left, expr.operator, expr.right)
        }

        assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, false, true)
        return true
    }


    internal fun loadLowByteOfWordIntoA(expr: PtExpression) {
        // loads only the low byte of a word expression into A (no high byte load)
        when(expr) {
            is PtIdentifier -> asmgen.out("  lda  ${asmgen.asmVariableName(expr)}")
            is PtArrayIndexer -> {
                val arrayVar = if(expr.splitWords) asmgen.asmVariableName(expr.variable!!) + "_lsb"
                                else asmgen.asmVariableName(expr.variable!!)
                asmgen.loadScaledArrayIndexIntoRegister(expr, CpuRegister.Y)
                asmgen.out("  lda  $arrayVar,y")
            }
            else -> assignmentAsmGen.assignExpressionToRegister(expr, RegisterOrPair.AY, false)
        }
    }


    internal fun assignBitwiseWithSimpleRightOperandByte(target: AsmAssignTarget, left: PtExpression, operator: String, right: PtExpression) {
        assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.A, false)
        val operand = when(right) {
            is PtNumber -> "#${right.number.toHex()}"
            is PtIdentifier -> asmgen.asmSymbolName(right)
            else -> throw AssemblyError("wrong right operand type")
        }
        when (operator) {
            "&" -> asmgen.out("  and  $operand")
            "|" -> asmgen.out("  ora  $operand")
            "^" -> asmgen.out("  eor  $operand")
            else -> throw AssemblyError("invalid operator")
        }
        assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, false, true)
    }


    internal fun assignBitwiseWithSimpleRightOperandWord(target: AsmAssignTarget, left: PtExpression, operator: String, right: PtExpression) {
        if(operator=="&" && right is PtNumber && right.number.toInt()==0x00ff) {
            // masking a word with $00ff only keeps its low byte; loading the high
            // byte would be dead code, so load just the low byte into A
            loadLowByteOfWordIntoA(left)
            asmgen.out("  ldy  #0")
            assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
            return
        }
        assignmentAsmGen.assignExpressionToRegister(left, RegisterOrPair.AY, false)
        when(right) {
            is PtNumber -> {
                val value = right.number.toInt()
                when (operator) {
                    "&" -> {
                        when {
                            value == 0 -> asmgen.out("  lda  #0 |  tay")
                            value == 0x00ff -> asmgen.out("  ldy  #0")
                            value == 0xff00 -> asmgen.out("  lda  #0")
                            value and 255 == 0 -> asmgen.out("  tya |  and  #>$value |  tay |  lda  #0")
                            value < 0x0100 -> asmgen.out("  and  #<$value |  ldy  #0")
                            else -> asmgen.out("  and  #<$value |  tax |  tya |  and  #>$value |  tay |  txa")
                        }
                    }
                    "|" -> {
                        when {
                            value == 0 -> {}
                            value and 255 == 0 -> asmgen.out("  tax |  tya |  ora  #>$value |  tay |  txa")
                            value < 0x0100 -> asmgen.out("  ora  #$value")
                            else -> asmgen.out("  ora  #<$value |  tax |  tya |  ora  #>$value |  tay |  txa")
                        }
                    }
                    "^" -> {
                        when {
                            value == 0 -> {}
                            value and 255 == 0 -> asmgen.out("  tax |  tya |  eor  #>$value |  tay |  txa")
                            value < 0x0100 -> asmgen.out("  eor  #$value")
                            else -> asmgen.out("  eor  #<$value |  tax |  tya |  eor  #>$value |  tay |  txa")
                        }
                    }
                    else -> throw AssemblyError("invalid bitwise operator")
                }
            }
            is PtIdentifier -> {
                val name = asmgen.asmSymbolName(right)
                when (operator) {
                    "&" -> asmgen.out("  and  $name |  tax |  tya |  and  $name+1 |  tay |  txa")
                    "|" -> asmgen.out("  ora  $name |  tax |  tya |  ora  $name+1 |  tay |  txa")
                    "^" -> asmgen.out("  eor  $name |  tax |  tya |  eor  $name+1 |  tay |  txa")
                    else -> throw AssemblyError("invalid bitwise operator")
                }
            }
            else -> throw AssemblyError("wrong right operand type")
        }
        assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
    }


    internal fun attemptAssignToByteCompareZero(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        when (expr.operator) {
            "==" -> {
                val dt = expr.left.type
                when {
                    dt.isBool || dt.isByte -> {
                        assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, dt.isSigned)
                        asmgen.out("""
                            cmp  #0
                            beq  +
                            lda  #1
+                           eor  #1""")
                        assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    dt.isWord || dt.isPointer -> {
                        if(expr.left is PtIdentifier) {
                            val varname = asmgen.asmVariableName(expr.left as PtIdentifier)
                            asmgen.out("""
                                lda  $varname
                                ora  $varname+1
                                beq  +
                                lda  #1
+                               eor  #1""")
                        } else {
                            assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.AY, dt.isSigned)
                            asmgen.out("""
                                sty  P8ZP_SCRATCH_B1
                                ora  P8ZP_SCRATCH_B1
                                beq  +
                                lda  #1
+                               eor  #1""")
                        }
                        assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    dt.isFloat -> {
                        assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.FAC1, true)
                        asmgen.out("  jsr  floats.SIGN |  and  #1 |  eor  #1")
                        assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    else->{
                        return false
                    }
                }
            }
            "!=" -> {
                val dt = expr.left.type
                when {
                    dt.isBool || dt.isByte -> {
                        assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.A, dt.isSigned)
                        asmgen.out("  beq  + |  lda  #1")
                        asmgen.out("+")
                        assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    dt.isWord || dt.isPointer -> {
                        if(expr.left is PtIdentifier) {
                            val varname = asmgen.asmVariableName(expr.left as PtIdentifier)
                            asmgen.out("""
                                lda  $varname
                                ora  $varname+1
                                beq  +
                                lda  #1
+""")
                        } else {
                            assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.AY, dt.isSigned)
                            asmgen.out("  sty  P8ZP_SCRATCH_B1 |  ora  P8ZP_SCRATCH_B1")
                            asmgen.out("  beq  + |  lda  #1")
                            asmgen.out("+")
                        }
                        assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    dt.isFloat -> {
                        assignmentAsmGen.assignExpressionToRegister(expr.left, RegisterOrPair.FAC1, true)
                        asmgen.out("  jsr  floats.SIGN")
                        assignmentAsmGen.assignRegisterByte(assign.target, CpuRegister.A, true, false)
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


}
