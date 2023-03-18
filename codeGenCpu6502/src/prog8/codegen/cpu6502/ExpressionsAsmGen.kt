package prog8.codegen.cpu6502

import prog8.code.ast.*
import prog8.code.core.*
import kotlin.math.absoluteValue

internal class ExpressionsAsmGen(private val program: PtProgram,
                                 private val asmgen: AsmGen6502Internal,
                                 private val allocator: VariableAllocator) {

    @Deprecated("avoid calling this as it generates slow evalstack based code")
    internal fun translateExpression(expression: PtExpression) {
        if (this.asmgen.options.slowCodegenWarnings) {
            asmgen.errors.warn("slow stack evaluation used for expression", expression.position)
        }
        translateExpressionInternal(expression)
    }


    // the rest of the methods are all PRIVATE


    private fun translateExpressionInternal(expression: PtExpression) {

        when(expression) {
            is PtPrefix -> translateExpression(expression)
            is PtBinaryExpression -> translateExpression(expression)
            is PtArrayIndexer -> translateExpression(expression)
            is PtTypeCast -> translateExpression(expression)
            is PtAddressOf -> translateExpression(expression)
            is PtMemoryByte -> asmgen.translateDirectMemReadExpressionToRegAorStack(expression, true)
            is PtNumber -> translateExpression(expression)
            is PtIdentifier -> translateExpression(expression)
            is PtFunctionCall -> translateFunctionCallResultOntoStack(expression)
            is PtBuiltinFunctionCall -> asmgen.translateBuiltinFunctionCallExpression(expression, true, null)
            is PtContainmentCheck -> throw AssemblyError("containment check as complex expression value is not supported")
            is PtArray, is PtString -> throw AssemblyError("string/array literal value assignment should have been replaced by a variable")
            is PtRange -> throw AssemblyError("range expression should have been changed into array values")
            is PtMachineRegister -> throw AssemblyError("machine register ast node should not occur in 6502 codegen it is for IR code")
            else -> TODO("missing expression asmgen for $expression")
        }
    }

    private fun translateFunctionCallResultOntoStack(call: PtFunctionCall) {
        // only for use in nested expression evaluation

        val symbol = asmgen.symbolTable.lookup(call.name)
        val sub = symbol!!.astNode as IPtSubroutine
        asmgen.saveXbeforeCall(call)
        asmgen.translateFunctionCall(call)
        if(sub.regXasResult()) {
            // store the return value in X somewhere that we can access again below
            asmgen.out("  stx  P8ZP_SCRATCH_REG")
        }
        asmgen.restoreXafterCall(call)

        val returns: List<Pair<RegisterOrStatusflag, DataType>> = sub.returnsWhatWhere()
        for ((reg, _) in returns) {
            // result value is in cpu or status registers, put it on the stack instead (as we're evaluating an expression tree)
            if (reg.registerOrPair != null) {
                when (reg.registerOrPair!!) {
                    RegisterOrPair.A -> asmgen.out("  sta  P8ESTACK_LO,x |  dex")
                    RegisterOrPair.Y -> asmgen.out("  tya |  sta  P8ESTACK_LO,x |  dex")
                    RegisterOrPair.AY -> asmgen.out("  sta  P8ESTACK_LO,x |  tya |  sta  P8ESTACK_HI,x |  dex")
                    RegisterOrPair.X -> asmgen.out("  lda  P8ZP_SCRATCH_REG |  sta  P8ESTACK_LO,x |  dex")
                    RegisterOrPair.AX -> asmgen.out("  sta  P8ESTACK_LO,x |  lda  P8ZP_SCRATCH_REG |  sta  P8ESTACK_HI,x |  dex")
                    RegisterOrPair.XY -> asmgen.out("  tya |  sta  P8ESTACK_HI,x |  lda  P8ZP_SCRATCH_REG |  sta  P8ESTACK_LO,x |  dex")
                    RegisterOrPair.FAC1 -> asmgen.out("  jsr  floats.push_fac1")
                    RegisterOrPair.FAC2 -> asmgen.out("  jsr  floats.push_fac2")
                    RegisterOrPair.R0,
                    RegisterOrPair.R1,
                    RegisterOrPair.R2,
                    RegisterOrPair.R3,
                    RegisterOrPair.R4,
                    RegisterOrPair.R5,
                    RegisterOrPair.R6,
                    RegisterOrPair.R7,
                    RegisterOrPair.R8,
                    RegisterOrPair.R9,
                    RegisterOrPair.R10,
                    RegisterOrPair.R11,
                    RegisterOrPair.R12,
                    RegisterOrPair.R13,
                    RegisterOrPair.R14,
                    RegisterOrPair.R15 -> {
                        asmgen.out(
                            """
                            lda  cx16.${reg.registerOrPair.toString().lowercase()}
                            sta  P8ESTACK_LO,x
                            lda  cx16.${reg.registerOrPair.toString().lowercase()}+1
                            sta  P8ESTACK_HI,x
                            dex
                        """)
                    }
                }
            } else when(reg.statusflag) {
                Statusflag.Pc -> {
                    asmgen.out("""
                        lda  #0
                        rol  a
                        sta  P8ESTACK_LO,x
                        dex""")
                }
                Statusflag.Pz -> {
                    asmgen.out("""
                        beq  +
                        lda  #0
                        beq  ++
+                           lda  #1
+                           sta  P8ESTACK_LO,x
                        dex""")
                }
                Statusflag.Pv -> {
                    asmgen.out("""
                        bvs  +
                        lda  #0
                        beq  ++
+                           lda  #1
+                           sta  P8ESTACK_LO,x
                        dex""")
                }
                Statusflag.Pn -> {
                    asmgen.out("""
                        bmi  +
                        lda  #0
                        beq  ++
+                           lda  #1
+                           sta  P8ESTACK_LO,x
                        dex""")
                }
                null -> {}
            }
        }
    }

    private fun translateExpression(typecast: PtTypeCast) {
        translateExpressionInternal(typecast.value)
        when(typecast.value.type) {
            DataType.UBYTE, DataType.BOOL -> {
                when(typecast.type) {
                    DataType.UBYTE, DataType.BYTE -> {}
                    DataType.UWORD, DataType.WORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  P8ESTACK_HI+1,x")
                        else
                            asmgen.out("  lda  #0  |  sta  P8ESTACK_HI+1,x")
                    }
                    DataType.FLOAT -> asmgen.out(" jsr  floats.stack_ub2float")
                    in PassByReferenceDatatypes -> throw AssemblyError("cannot cast to a pass-by-reference datatype")
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.BYTE -> {
                when(typecast.type) {
                    DataType.UBYTE, DataType.BYTE -> {}
                    DataType.UWORD, DataType.WORD -> asmgen.signExtendStackLsb(DataType.BYTE)
                    DataType.FLOAT -> asmgen.out(" jsr  floats.stack_b2float")
                    in PassByReferenceDatatypes -> throw AssemblyError("cannot cast to a pass-by-reference datatype")
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when(typecast.type) {
                    DataType.BYTE, DataType.UBYTE -> {}
                    DataType.WORD, DataType.UWORD -> {}
                    DataType.FLOAT -> asmgen.out(" jsr  floats.stack_uw2float")
                    in PassByReferenceDatatypes -> throw AssemblyError("cannot cast to a pass-by-reference datatype")
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.WORD -> {
                when(typecast.type) {
                    DataType.BYTE, DataType.UBYTE -> {}
                    DataType.WORD, DataType.UWORD -> {}
                    DataType.FLOAT -> asmgen.out(" jsr  floats.stack_w2float")
                    in PassByReferenceDatatypes -> throw AssemblyError("cannot cast to a pass-by-reference datatype")
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.FLOAT -> {
                when(typecast.type) {
                    DataType.UBYTE -> asmgen.out(" jsr  floats.stack_float2uw")
                    DataType.BYTE -> asmgen.out(" jsr  floats.stack_float2w")
                    DataType.UWORD -> asmgen.out(" jsr  floats.stack_float2uw")
                    DataType.WORD -> asmgen.out(" jsr  floats.stack_float2w")
                    DataType.FLOAT -> {}
                    in PassByReferenceDatatypes -> throw AssemblyError("cannot cast to a pass-by-reference datatype")
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.STR -> {
                if (typecast.type != DataType.UWORD && typecast.type == DataType.STR)
                    throw AssemblyError("cannot typecast a string into another incompatitble type")
            }
            in PassByReferenceDatatypes -> throw AssemblyError("cannot cast pass-by-reference value into another type")
            else -> throw AssemblyError("weird type")
        }
    }

    private fun translateExpression(expr: PtAddressOf) {
        val name = asmgen.asmVariableName(expr.identifier)
        asmgen.out("  lda  #<$name |  sta  P8ESTACK_LO,x |  lda  #>$name  |  sta  P8ESTACK_HI,x  | dex")
    }

    private fun translateExpression(expr: PtNumber) {
        when(expr.type) {
            DataType.UBYTE, DataType.BYTE -> asmgen.out(" lda  #${expr.number.toHex()}  | sta  P8ESTACK_LO,x  | dex")
            DataType.UWORD, DataType.WORD -> asmgen.out("""
                lda  #<${expr.number.toHex()}
                sta  P8ESTACK_LO,x
                lda  #>${expr.number.toHex()}
                sta  P8ESTACK_HI,x
                dex
            """)
            DataType.FLOAT -> {
                val floatConst = allocator.getFloatAsmConst(expr.number)
                asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  floats.push_float")
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun translateExpression(expr: PtIdentifier) {
        val varname = asmgen.asmVariableName(expr)
        when(expr.type) {
            DataType.UBYTE, DataType.BYTE -> {
                asmgen.out("  lda  $varname  |  sta  P8ESTACK_LO,x  |  dex")
            }
            DataType.UWORD, DataType.WORD -> {
                asmgen.out("  lda  $varname  |  sta  P8ESTACK_LO,x  |  lda  $varname+1 |  sta  P8ESTACK_HI,x |  dex")
            }
            DataType.FLOAT -> {
                asmgen.out(" lda  #<$varname |  ldy  #>$varname|  jsr  floats.push_float")
            }
            in IterableDatatypes -> {
                asmgen.out("  lda  #<$varname  |  sta  P8ESTACK_LO,x  |  lda  #>$varname |  sta  P8ESTACK_HI,x |  dex")
            }
            else -> throw AssemblyError("stack push weird variable type $expr")
        }
    }

    private fun translateExpression(expr: PtBinaryExpression) {
        // Uses evalstack to evaluate the given expression.  THIS IS SLOW AND SHOULD BE AVOIDED!
        val leftDt = expr.left.type
        val rightDt = expr.right.type
        // see if we can apply some optimized routines still
        when(expr.operator) {
            "+" -> {
                if(leftDt in IntegerDatatypes && rightDt in IntegerDatatypes) {
                    val leftVal = expr.left.asConstInteger()
                    val rightVal = expr.right.asConstInteger()
                    if (leftVal!=null && leftVal in -4..4) {
                        translateExpressionInternal(expr.right)
                        if(rightDt in ByteDatatypes) {
                            val incdec = if(leftVal<0) "dec" else "inc"
                            repeat(leftVal.absoluteValue) {
                                asmgen.out("  $incdec  P8ESTACK_LO+1,x")
                            }
                        } else {
                            // word
                            if(leftVal<0) {
                                repeat(leftVal.absoluteValue) {
                                    asmgen.out("""
                                        lda  P8ESTACK_LO+1,x
                                        bne  +
                                        dec  P8ESTACK_HI+1,x
+                                       dec  P8ESTACK_LO+1,x""")
                                }
                            } else {
                                repeat(leftVal) {
                                    asmgen.out("""
                                        inc  P8ESTACK_LO+1,x
                                        bne  +
                                        inc  P8ESTACK_HI+1,x
+""")
                                }
                            }
                        }
                        return
                    }
                    else if (rightVal!=null && rightVal in -4..4)
                    {
                        translateExpressionInternal(expr.left)
                        if(leftDt in ByteDatatypes) {
                            val incdec = if(rightVal<0) "dec" else "inc"
                            repeat(rightVal.absoluteValue) {
                                asmgen.out("  $incdec  P8ESTACK_LO+1,x")
                            }
                        } else {
                            // word
                            if(rightVal<0) {
                                repeat(rightVal.absoluteValue) {
                                    asmgen.out("""
                                        lda  P8ESTACK_LO+1,x
                                        bne  +
                                        dec  P8ESTACK_HI+1,x
+                                       dec  P8ESTACK_LO+1,x""")
                                }
                            } else {
                                repeat(rightVal) {
                                    asmgen.out("""
                                        inc  P8ESTACK_LO+1,x
                                        bne  +
                                        inc  P8ESTACK_HI+1,x
+""")
                                }
                            }
                        }
                        return
                    }
                }
            }
            "-" -> {
                if(leftDt in IntegerDatatypes && rightDt in IntegerDatatypes) {
                    val rightVal = expr.right.asConstInteger()
                    if (rightVal!=null && rightVal in -4..4)
                    {
                        translateExpressionInternal(expr.left)
                        if(leftDt in ByteDatatypes) {
                            val incdec = if(rightVal<0) "inc" else "dec"
                            repeat(rightVal.absoluteValue) {
                                asmgen.out("  $incdec  P8ESTACK_LO+1,x")
                            }
                        } else {
                            // word
                            if(rightVal>0) {
                                repeat(rightVal.absoluteValue) {
                                    asmgen.out("""
                                        lda  P8ESTACK_LO+1,x
                                        bne  +
                                        dec  P8ESTACK_HI+1,x
+                                       dec  P8ESTACK_LO+1,x""")
                                }
                            } else {
                                repeat(rightVal) {
                                    asmgen.out("""
                                        inc  P8ESTACK_LO+1,x
                                        bne  +
                                        inc  P8ESTACK_HI+1,x
+""")
                                }
                            }
                        }
                        return
                    }
                }
            }
            ">>" -> {
                val amount = expr.right.asConstInteger()
                if(amount!=null) {
                    translateExpressionInternal(expr.left)
                    when (leftDt) {
                        DataType.UBYTE -> {
                            if (amount <= 2)
                                repeat(amount) { asmgen.out(" lsr  P8ESTACK_LO+1,x") }
                            else {
                                asmgen.out(" lda  P8ESTACK_LO+1,x")
                                repeat(amount) { asmgen.out(" lsr  a") }
                                asmgen.out(" sta  P8ESTACK_LO+1,x")
                            }
                        }
                        DataType.BYTE -> {
                            if (amount <= 2)
                                repeat(amount) { asmgen.out(" lda  P8ESTACK_LO+1,x |  asl  a |  ror  P8ESTACK_LO+1,x") }
                            else {
                                asmgen.out(" lda  P8ESTACK_LO+1,x |  sta  P8ZP_SCRATCH_B1")
                                repeat(amount) { asmgen.out(" asl  a |  ror  P8ZP_SCRATCH_B1 |  lda  P8ZP_SCRATCH_B1") }
                                asmgen.out(" sta  P8ESTACK_LO+1,x")
                            }
                        }
                        DataType.UWORD -> {
                            if(amount>=16) {
                                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                    asmgen.out("  stz  P8ESTACK_LO+1,x |  stz  P8ESTACK_HI+1,x")
                                else
                                    asmgen.out("  lda  #0 |  sta  P8ESTACK_LO+1,x |  sta  P8ESTACK_HI+1,x")
                                return
                            }
                            var left = amount
                            while (left >= 7) {
                                asmgen.out(" jsr  math.shift_right_uw_7")
                                left -= 7
                            }
                            if (left in 0..2)
                                repeat(left) { asmgen.out(" lsr  P8ESTACK_HI+1,x |  ror  P8ESTACK_LO+1,x") }
                            else
                                asmgen.out(" jsr  math.shift_right_uw_$left")
                        }
                        DataType.WORD -> {
                            if(amount>=16) {
                                asmgen.out("""
                                    lda  P8ESTACK_HI+1,x
                                    bmi  +
                                    lda  #0
                                    sta  P8ESTACK_LO+1,x
                                    sta  P8ESTACK_HI+1,x
                                    beq  ++
+                                   lda  #255
                                    sta  P8ESTACK_LO+1,x
                                    sta  P8ESTACK_HI+1,x
+""")
                                return
                            }
                            var left = amount
                            while (left >= 7) {
                                asmgen.out(" jsr  math.shift_right_w_7")
                                left -= 7
                            }
                            if (left in 0..2)
                                repeat(left) { asmgen.out(" lda  P8ESTACK_HI+1,x |  asl  a  |  ror  P8ESTACK_HI+1,x |  ror  P8ESTACK_LO+1,x") }
                            else
                                asmgen.out(" jsr  math.shift_right_w_$left")
                        }
                        else -> throw AssemblyError("weird type")
                    }
                    return
                }
            }
            "<<" -> {
                val amount = expr.right.asConstInteger()
                if(amount!=null) {
                    translateExpressionInternal(expr.left)
                    if (leftDt in ByteDatatypes) {
                        if (amount <= 2)
                            repeat(amount) { asmgen.out("  asl  P8ESTACK_LO+1,x") }
                        else {
                            asmgen.out("  lda  P8ESTACK_LO+1,x")
                            repeat(amount) { asmgen.out("  asl  a") }
                            asmgen.out("  sta  P8ESTACK_LO+1,x")
                        }
                    } else {
                        var left = amount
                        while (left >= 7) {
                            asmgen.out(" jsr  math.shift_left_w_7")
                            left -= 7
                        }
                        if (left in 0..2)
                            repeat(left) { asmgen.out("  asl  P8ESTACK_LO+1,x |  rol  P8ESTACK_HI+1,x") }
                        else
                            asmgen.out(" jsr  math.shift_left_w_$left")
                    }
                    return
                }
            }
            "*" -> {
                if(leftDt in IntegerDatatypes && rightDt in IntegerDatatypes) {
                    val leftVar = expr.left as? PtIdentifier
                    val rightVar = expr.right as? PtIdentifier
                    if(leftVar!=null && rightVar!=null && leftVar==rightVar)
                        return translateSquared(leftVar, leftDt)
                }

                val value = expr.right as? PtNumber
                if(value!=null) {
                    if(rightDt in IntegerDatatypes) {
                        val amount = value.number.toInt()
                        if(amount==2) {
                            // optimize x*2 common case
                            translateExpressionInternal(expr.left)
                            if(leftDt in ByteDatatypes) {
                                asmgen.out("  asl  P8ESTACK_LO+1,x")
                            } else {
                                asmgen.out("  asl  P8ESTACK_LO+1,x |  rol  P8ESTACK_HI+1,x")
                            }
                            return
                        }
                        when(rightDt) {
                            DataType.UBYTE -> {
                                if(amount in asmgen.optimizedByteMultiplications) {
                                    translateExpressionInternal(expr.left)
                                    asmgen.out(" jsr  math.stack_mul_byte_$amount")
                                    return
                                }
                            }
                            DataType.BYTE -> {
                                if(amount in asmgen.optimizedByteMultiplications) {
                                    translateExpressionInternal(expr.left)
                                    asmgen.out(" jsr  math.stack_mul_byte_$amount")
                                    return
                                }
                                if(amount.absoluteValue in asmgen.optimizedByteMultiplications) {
                                    translateExpressionInternal(expr.left)
                                    asmgen.out(" jsr  prog8_lib.neg_b |  jsr  math.stack_mul_byte_${amount.absoluteValue}")
                                    return
                                }
                            }
                            DataType.UWORD -> {
                                if(amount in asmgen.optimizedWordMultiplications) {
                                    translateExpressionInternal(expr.left)
                                    asmgen.out(" jsr  math.stack_mul_word_$amount")
                                    return
                                }
                            }
                            DataType.WORD -> {
                                if(amount in asmgen.optimizedWordMultiplications) {
                                    translateExpressionInternal(expr.left)
                                    asmgen.out(" jsr  math.stack_mul_word_$amount")
                                    return
                                }
                                if(amount.absoluteValue in asmgen.optimizedWordMultiplications) {
                                    translateExpressionInternal(expr.left)
                                    asmgen.out(" jsr  prog8_lib.neg_w |  jsr  math.stack_mul_word_${amount.absoluteValue}")
                                    return
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
            "/" -> {
                if(leftDt in IntegerDatatypes && rightDt in IntegerDatatypes) {
                    val rightVal = expr.right.asConstInteger()
                    if(rightVal!=null && rightVal==2) {
                        translateExpressionInternal(expr.left)
                        when (leftDt) {
                            DataType.UBYTE -> {
                                asmgen.out("  lsr  P8ESTACK_LO+1,x")
                            }
                            DataType.UWORD -> {
                                asmgen.out("  lsr  P8ESTACK_HI+1,x |  ror  P8ESTACK_LO+1,x")
                            }
                            DataType.BYTE -> {
                                // signed divide using shift needs adjusting of negative value to get correct rounding towards zero
                                asmgen.out("""
                                    lda  P8ESTACK_LO+1,x
                                    bpl  +
                                    inc  P8ESTACK_LO+1,x
                                    lda  P8ESTACK_LO+1,x
+                                   asl  a
                                    ror  P8ESTACK_LO+1,x""")
                            }
                            DataType.WORD -> {
                                // signed divide using shift needs adjusting of negative value to get correct rounding towards zero
                                asmgen.out("""
                                    lda  P8ESTACK_HI+1,x
                                    bpl  ++
                                    inc  P8ESTACK_LO+1,x
                                    bne  +
                                    inc  P8ESTACK_HI+1,x
+                                   lda  P8ESTACK_HI+1,x
+                                   asl  a
                                    ror  P8ESTACK_HI+1,x
                                    ror  P8ESTACK_LO+1,x""")
                            }
                            else -> throw AssemblyError("weird dt")
                        }
                        return
                    }
                }
            }
            in ComparisonOperators -> {
                if(leftDt in NumericDatatypes && rightDt in NumericDatatypes) {
                    val rightVal = expr.right.asConstInteger()
                    if(rightVal==0)
                        return translateComparisonWithZero(expr.left, leftDt, expr.operator)
                }
            }
        }

        if((leftDt in ByteDatatypes && rightDt !in ByteDatatypes)
                || (leftDt in WordDatatypes && rightDt !in WordDatatypes))
            throw AssemblyError("binary operator ${expr.operator} left/right dt not identical")

        if(leftDt==DataType.STR && rightDt==DataType.STR && expr.operator in ComparisonOperators) {
            translateCompareStrings(expr.left, expr.operator, expr.right)
        }
        else {
            // the general, non-optimized cases
            // TODO optimize more cases.... (or one day just don't use the evalstack at all anymore)
            translateExpressionInternal(expr.left)
            translateExpressionInternal(expr.right)
            when (leftDt) {
                in ByteDatatypes -> translateBinaryOperatorBytes(expr.operator, leftDt)
                in WordDatatypes -> translateBinaryOperatorWords(expr.operator, leftDt)
                DataType.FLOAT -> translateBinaryOperatorFloats(expr.operator)
                else -> throw AssemblyError("non-numerical datatype")
            }
        }
    }

    private fun translateComparisonWithZero(expr: PtExpression, dt: DataType, operator: String) {
        if(expr.isSimple()) {
            if(operator=="!=") {
                when (dt) {
                    in ByteDatatypes -> {
                        asmgen.assignExpressionToRegister(expr, RegisterOrPair.A, dt == DataType.BYTE)
                        asmgen.out("""
                            beq  +
                            lda  #1
+                           sta  P8ESTACK_LO,x
                            dex""")
                        return
                    }
                    in WordDatatypes -> {
                        asmgen.assignExpressionToRegister(expr, RegisterOrPair.AY, dt == DataType.WORD)
                        asmgen.out("""
                            sty  P8ZP_SCRATCH_B1 
                            ora  P8ZP_SCRATCH_B1
                            beq  +
                            lda  #1
+                           sta  P8ESTACK_LO,x
                            dex""")
                        return
                    }
                    DataType.FLOAT -> {
                        asmgen.assignExpressionToRegister(expr, RegisterOrPair.FAC1, true)
                        asmgen.out("""
                            jsr  floats.SIGN
                            sta  P8ESTACK_LO,x
                            dex""")
                        return
                    }
                    else -> {}
                }
            }
            /* operator == is not worth it to special case, the code is mostly larger */
        }
        translateExpressionInternal(expr)
        when(operator) {
            "==" -> {
                when(dt) {
                    DataType.UBYTE, DataType.BYTE -> asmgen.out("  jsr  prog8_lib.equalzero_b")
                    DataType.UWORD, DataType.WORD -> asmgen.out("  jsr  prog8_lib.equalzero_w")
                    DataType.FLOAT -> asmgen.out("  jsr  floats.equal_zero")
                    else -> throw AssemblyError("wrong dt")
                }
            }
            "!=" -> {
                when(dt) {
                    DataType.UBYTE, DataType.BYTE -> asmgen.out("  jsr  prog8_lib.notequalzero_b")
                    DataType.UWORD, DataType.WORD -> asmgen.out("  jsr  prog8_lib.notequalzero_w")
                    DataType.FLOAT -> asmgen.out("  jsr  floats.notequal_zero")
                    else -> throw AssemblyError("wrong dt")
                }
            }
            "<" -> {
                if(dt==DataType.UBYTE || dt==DataType.UWORD)
                    return translateExpressionInternal(PtNumber.fromBoolean(false, expr.position))
                when(dt) {
                    DataType.BYTE -> asmgen.out("  jsr  prog8_lib.lesszero_b")
                    DataType.WORD -> asmgen.out("  jsr  prog8_lib.lesszero_w")
                    DataType.FLOAT -> asmgen.out("  jsr  floats.less_zero")
                    else -> throw AssemblyError("wrong dt")
                }
            }
            ">" -> {
                when(dt) {
                    DataType.UBYTE -> asmgen.out("  jsr  prog8_lib.greaterzero_ub")
                    DataType.BYTE -> asmgen.out("  jsr  prog8_lib.greaterzero_sb")
                    DataType.UWORD -> asmgen.out("  jsr  prog8_lib.greaterzero_uw")
                    DataType.WORD -> asmgen.out("  jsr  prog8_lib.greaterzero_sw")
                    DataType.FLOAT -> asmgen.out("  jsr  floats.greater_zero")
                    else -> throw AssemblyError("wrong dt")
                }
            }
            "<=" -> {
                when(dt) {
                    DataType.UBYTE -> asmgen.out("  jsr  prog8_lib.equalzero_b")
                    DataType.BYTE -> asmgen.out("  jsr  prog8_lib.lessequalzero_sb")
                    DataType.UWORD -> asmgen.out("  jsr  prog8_lib.equalzero_w")
                    DataType.WORD -> asmgen.out("  jsr  prog8_lib.lessequalzero_sw")
                    DataType.FLOAT -> asmgen.out("  jsr  floats.lessequal_zero")
                    else -> throw AssemblyError("wrong dt")
                }
            }
            ">=" -> {
                if(dt==DataType.UBYTE || dt==DataType.UWORD)
                    return translateExpressionInternal(PtNumber.fromBoolean(true, expr.position))
                when(dt) {
                    DataType.BYTE -> asmgen.out("  jsr  prog8_lib.greaterequalzero_sb")
                    DataType.WORD -> asmgen.out("  jsr  prog8_lib.greaterequalzero_sw")
                    DataType.FLOAT -> asmgen.out("  jsr  floats.greaterequal_zero")
                    else -> throw AssemblyError("wrong dt")
                }
            }
            else -> throw AssemblyError("invalid comparison operator")
        }
    }

    private fun translateSquared(variable: PtIdentifier, dt: DataType) {
        val asmVar = asmgen.asmVariableName(variable)
        when(dt) {
            DataType.BYTE, DataType.UBYTE -> {
                asmgen.out("  lda  $asmVar")
                asmgen.signExtendAYlsb(dt)
                asmgen.out("  jsr  math.square")
            }
            DataType.UWORD, DataType.WORD -> {
                asmgen.out("  lda  $asmVar |  ldy  $asmVar+1 |  jsr  math.square")
            }
            else -> throw AssemblyError("require integer dt for square")
        }
        asmgen.out("  sta  P8ESTACK_LO,x |  tya |  sta  P8ESTACK_HI,x |  dex")
    }

    private fun translateExpression(expr: PtPrefix) {
        translateExpressionInternal(expr.value)
        when(expr.operator) {
            "+" -> {}
            "-" -> {
                when(expr.type) {
                    in ByteDatatypes -> asmgen.out("  jsr  prog8_lib.neg_b")
                    in WordDatatypes -> asmgen.out("  jsr  prog8_lib.neg_w")
                    DataType.FLOAT -> asmgen.out("  jsr  floats.neg_f")
                    else -> throw AssemblyError("weird type")
                }
            }
            "~" -> {
                when(expr.type) {
                    in ByteDatatypes ->
                        asmgen.out("""
                            lda  P8ESTACK_LO+1,x
                            eor  #255
                            sta  P8ESTACK_LO+1,x
                            """)
                    in WordDatatypes -> asmgen.out("  jsr  prog8_lib.inv_word")
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("invalid prefix operator ${expr.operator}")
        }
    }

    private fun translateExpression(arrayExpr: PtArrayIndexer) {
        val elementDt = arrayExpr.type
        val arrayVarName = asmgen.asmVariableName(arrayExpr.variable)

        if(arrayExpr.variable.type==DataType.UWORD) {
            // indexing a pointer var instead of a real array or string
            if(elementDt !in ByteDatatypes)
                throw AssemblyError("non-array var indexing requires bytes dt")
            if(arrayExpr.index.type != DataType.UBYTE)
                throw AssemblyError("non-array var indexing requires bytes index")
            asmgen.loadScaledArrayIndexIntoRegister(arrayExpr, elementDt, CpuRegister.Y)
            if(asmgen.isZpVar(arrayExpr.variable)) {
                asmgen.out("  lda  ($arrayVarName),y")
            } else {
                asmgen.out("  lda  $arrayVarName |  sta  P8ZP_SCRATCH_W1 |  lda  $arrayVarName+1 |  sta  P8ZP_SCRATCH_W1+1")
                asmgen.out("  lda  (P8ZP_SCRATCH_W1),y")
            }
            asmgen.out("  sta  P8ESTACK_LO,x |  dex")
            return
        }

        val constIndexNum = arrayExpr.index.asConstInteger()
        if(constIndexNum!=null) {
            val indexValue = constIndexNum * program.memsizer.memorySize(elementDt)
            when(elementDt) {
                in ByteDatatypes -> {
                    asmgen.out("  lda  $arrayVarName+$indexValue |  sta  P8ESTACK_LO,x |  dex")
                }
                in WordDatatypes -> {
                    asmgen.out("  lda  $arrayVarName+$indexValue |  sta  P8ESTACK_LO,x |  lda  $arrayVarName+$indexValue+1 |  sta  P8ESTACK_HI,x |  dex")
                }
                DataType.FLOAT -> {
                    asmgen.out("  lda  #<($arrayVarName+$indexValue) |  ldy  #>($arrayVarName+$indexValue) |  jsr  floats.push_float")
                }
                else -> throw AssemblyError("weird element type")
            }
        } else {
            when(elementDt) {
                in ByteDatatypes -> {
                    asmgen.loadScaledArrayIndexIntoRegister(arrayExpr, elementDt, CpuRegister.Y)
                    asmgen.out(" lda  $arrayVarName,y |  sta  P8ESTACK_LO,x |  dex")
                }
                in WordDatatypes -> {
                    asmgen.loadScaledArrayIndexIntoRegister(arrayExpr, elementDt, CpuRegister.Y)
                    asmgen.out(" lda  $arrayVarName,y |  sta  P8ESTACK_LO,x |  lda  $arrayVarName+1,y |  sta  P8ESTACK_HI,x |  dex")
                }
                DataType.FLOAT -> {
                    asmgen.loadScaledArrayIndexIntoRegister(arrayExpr, elementDt, CpuRegister.A)
                    asmgen.out("""
                        ldy  #>$arrayVarName
                        clc
                        adc  #<$arrayVarName
                        bcc  +
                        iny
+                       jsr  floats.push_float""")
                }
                else -> throw AssemblyError("weird dt")
            }

        }
    }

    private fun translateBinaryOperatorBytes(operator: String, types: DataType) {
        when(operator) {
            "*" -> asmgen.out("  jsr  prog8_lib.mul_byte")  //  the optimized routines should have been checked earlier
            "/" -> asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.idiv_ub" else "  jsr  prog8_lib.idiv_b")
            "%" -> {
                if(types==DataType.BYTE)
                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                asmgen.out("  jsr prog8_lib.remainder_ub")
            }
            "+" -> asmgen.out("""
                lda  P8ESTACK_LO+2,x
                clc
                adc  P8ESTACK_LO+1,x
                inx
                sta  P8ESTACK_LO+1,x
                """)
            "-" -> asmgen.out("""
                lda  P8ESTACK_LO+2,x
                sec
                sbc  P8ESTACK_LO+1,x
                inx
                sta  P8ESTACK_LO+1,x
                """)
            "<<" -> asmgen.out("  jsr  prog8_lib.shiftleft_b")
            ">>" -> asmgen.out("  jsr  prog8_lib.shiftright_b")
            "<" -> asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.less_ub" else "  jsr  prog8_lib.less_b")
            ">" -> asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.greater_ub" else "  jsr  prog8_lib.greater_b")
            "<=" -> asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.lesseq_ub" else "  jsr  prog8_lib.lesseq_b")
            ">=" -> asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.greatereq_ub" else "  jsr  prog8_lib.greatereq_b")
            "==" -> asmgen.out("  jsr  prog8_lib.equal_b")
            "!=" -> asmgen.out("  jsr  prog8_lib.notequal_b")
            "&" -> asmgen.out("  jsr  prog8_lib.bitand_b")
            "^" -> asmgen.out("  jsr  prog8_lib.bitxor_b")
            "|" -> asmgen.out("  jsr  prog8_lib.bitor_b")
            else -> throw AssemblyError("invalid operator $operator")
        }
    }

    private fun translateBinaryOperatorWords(operator: String, dt: DataType) {
        when(operator) {
            "*" -> asmgen.out("  jsr  prog8_lib.mul_word")
            "/" -> asmgen.out(if(dt==DataType.UWORD) "  jsr  prog8_lib.idiv_uw" else "  jsr  prog8_lib.idiv_w")
            "%" -> {
                if(dt==DataType.WORD)
                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                asmgen.out("  jsr prog8_lib.remainder_uw")
            }
            "+" -> asmgen.out("  jsr  prog8_lib.add_w")
            "-" -> asmgen.out("  jsr  prog8_lib.sub_w")
            "<<" -> asmgen.out("  jsr  math.shift_left_w")
            ">>" -> {
                if(dt==DataType.UWORD)
                    asmgen.out("  jsr  math.shift_right_uw")
                else
                    asmgen.out("  jsr  math.shift_right_w")
            }
            "<" -> asmgen.out(if(dt==DataType.UWORD) "  jsr  prog8_lib.less_uw" else "  jsr  prog8_lib.less_w")
            ">" -> asmgen.out(if(dt==DataType.UWORD) "  jsr  prog8_lib.greater_uw" else "  jsr  prog8_lib.greater_w")
            "<=" -> asmgen.out(if(dt==DataType.UWORD) "  jsr  prog8_lib.lesseq_uw" else "  jsr  prog8_lib.lesseq_w")
            ">=" -> asmgen.out(if(dt==DataType.UWORD) "  jsr  prog8_lib.greatereq_uw" else "  jsr  prog8_lib.greatereq_w")
            "==" -> asmgen.out("  jsr  prog8_lib.equal_w")
            "!=" -> asmgen.out("  jsr  prog8_lib.notequal_w")
            "&" -> asmgen.out("  jsr  prog8_lib.bitand_w")
            "^" -> asmgen.out("  jsr  prog8_lib.bitxor_w")
            "|" -> asmgen.out("  jsr  prog8_lib.bitor_w")
            else -> throw AssemblyError("invalid operator $operator")
        }
    }

    private fun translateBinaryOperatorFloats(operator: String) {
        when(operator) {
            "*" -> asmgen.out("  jsr  floats.mul_f")
            "/" -> asmgen.out("  jsr  floats.div_f")
            "+" -> asmgen.out("  jsr  floats.add_f")
            "-" -> asmgen.out("  jsr  floats.sub_f")
            "<" -> asmgen.out("  jsr  floats.less_f")
            ">" -> asmgen.out("  jsr  floats.greater_f")
            "<=" -> asmgen.out("  jsr  floats.lesseq_f")
            ">=" -> asmgen.out("  jsr  floats.greatereq_f")
            "==" -> asmgen.out("  jsr  floats.equal_f")
            "!=" -> asmgen.out("  jsr  floats.notequal_f")
            "%", "<<", ">>", "&", "^", "|" -> throw AssemblyError("requires integer datatype")
            else -> throw AssemblyError("invalid operator $operator")
        }
    }

    private fun translateCompareStrings(s1: PtExpression, operator: String, s2: PtExpression) {
        asmgen.assignExpressionToVariable(s1, "prog8_lib.strcmp_expression._arg_s1", DataType.UWORD, null)
        asmgen.assignExpressionToVariable(s2, "prog8_lib.strcmp_expression._arg_s2", DataType.UWORD, null)
        asmgen.out(" jsr  prog8_lib.strcmp_expression")    // result  of compare is in A
        when(operator) {
            "==" -> asmgen.out(" and  #1 |  eor  #1 |  sta  P8ESTACK_LO,x")
            "!=" -> asmgen.out(" and  #1 |  sta  P8ESTACK_LO,x")
            "<=" -> asmgen.out("""
                bpl  +
                lda  #1
                bne  ++
+               lda  #0
+               sta  P8ESTACK_LO,x""")
            ">=" -> asmgen.out("""
                bmi  +
                lda  #1
                bne  ++
+               lda  #0
+               sta  P8ESTACK_LO,x""")
            "<" -> asmgen.out("""
                bmi  +
                lda  #0
                beq  ++
+               lda  #1
+               sta  P8ESTACK_LO,x""")
            ">" -> asmgen.out("""
                bpl  +
                lda  #0
                beq  ++
+               lda  #1
+               sta  P8ESTACK_LO,x""")
        }
        asmgen.out("  dex")
    }
}
