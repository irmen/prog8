package prog8.compiler.target.cpu6502.codegen

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.BuiltinFunctionPlaceholder
import prog8.ast.statements.Subroutine
import prog8.ast.toHex
import prog8.compiler.target.AssemblyError
import prog8.compilerinterface.BuiltinFunctions
import prog8.compilerinterface.CpuType
import kotlin.math.absoluteValue

internal class ExpressionsAsmGen(private val program: Program, private val asmgen: AsmGen) {

    @Deprecated("avoid calling this as it generates slow evalstack based code")
    internal fun translateExpression(expression:Expression) {
        if (this.asmgen.options.slowCodegenWarnings) {
            asmgen.errors.warn("slow stack evaluation used for expression $expression", expression.position)
        }
        translateExpressionInternal(expression)
    }


    // the rest of the methods are all PRIVATE


    private fun translateExpressionInternal(expression: Expression) {

        when(expression) {
            is PrefixExpression -> translateExpression(expression)
            is BinaryExpression -> translateExpression(expression)
            is ArrayIndexedExpression -> translateExpression(expression)
            is TypecastExpression -> translateExpression(expression)
            is AddressOf -> translateExpression(expression)
            is DirectMemoryRead -> asmgen.translateDirectMemReadExpressionToRegAorStack(expression, true)
            is NumericLiteralValue -> translateExpression(expression)
            is IdentifierReference -> translateExpression(expression)
            is FunctionCallExpr -> translateFunctionCallResultOntoStack(expression)
            is ArrayLiteralValue, is StringLiteralValue -> throw AssemblyError("no asm gen for string/array literal value assignment - should have been replaced by a variable")
            is RangeExpr -> throw AssemblyError("range expression should have been changed into array values")
            is CharLiteral -> throw AssemblyError("charliteral should have been replaced by ubyte using certain encoding")
        }
    }

    private fun translateFunctionCallResultOntoStack(call: FunctionCallExpr) {
        // only for use in nested expression evaluation

        val sub = call.target.targetStatement(program)
        if(sub is BuiltinFunctionPlaceholder) {
            val builtinFunc = BuiltinFunctions.getValue(sub.name)
            asmgen.translateBuiltinFunctionCallExpression(call, builtinFunc, true, null)
        } else {
            sub as Subroutine
            asmgen.saveXbeforeCall(call)
            asmgen.translateFunctionCall(call, true)
            if(sub.regXasResult()) {
                // store the return value in X somewhere that we can acces again below
                asmgen.out("  stx  P8ZP_SCRATCH_REG")
            }
            asmgen.restoreXafterCall(call)

            val returns = sub.returntypes.zip(sub.asmReturnvaluesRegisters)
            for ((_, reg) in returns) {
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
    }

    private fun translateExpression(typecast: TypecastExpression) {
        translateExpressionInternal(typecast.expression)
        when(typecast.expression.inferType(program).getOr(DataType.UNDEFINED)) {
            DataType.UBYTE -> {
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

    private fun translateExpression(expr: AddressOf) {
        val name = asmgen.asmVariableName(expr.identifier)
        asmgen.out("  lda  #<$name |  sta  P8ESTACK_LO,x |  lda  #>$name  |  sta  P8ESTACK_HI,x  | dex")
    }

    private fun translateExpression(expr: NumericLiteralValue) {
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
                val floatConst = asmgen.getFloatAsmConst(expr.number)
                asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  floats.push_float")
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun translateExpression(expr: IdentifierReference) {
        val varname = asmgen.asmVariableName(expr)
        when(expr.inferType(program).getOr(DataType.UNDEFINED)) {
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

    private fun translateExpression(expr: BinaryExpression) {
        // Uses evalstack to evaluate the given expression.
        // TODO we're slowly reducing the number of places where this is called and instead replace that by more efficient assignment-form code (using temp var or register for instance).
        val leftIDt = expr.left.inferType(program)
        val rightIDt = expr.right.inferType(program)
        if(!leftIDt.isKnown || !rightIDt.isKnown)
            throw AssemblyError("can't infer type of both expression operands")

        val leftDt = leftIDt.getOrElse { throw AssemblyError("unknown dt") }
        val rightDt = rightIDt.getOrElse { throw AssemblyError("unknown dt") }
        // see if we can apply some optimized routines
        when(expr.operator) {
            "+" -> {
                if(leftDt in IntegerDatatypes && rightDt in IntegerDatatypes) {
                    val leftVal = expr.left.constValue(program)?.number?.toInt()
                    val rightVal = expr.right.constValue(program)?.number?.toInt()
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
                    val rightVal = expr.right.constValue(program)?.number?.toInt()
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
                val amount = expr.right.constValue(program)?.number?.toInt()
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
                val amount = expr.right.constValue(program)?.number?.toInt()
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
                    val leftVar = expr.left as? IdentifierReference
                    val rightVar = expr.right as? IdentifierReference
                    if(leftVar!=null && rightVar!=null && leftVar==rightVar)
                        return translateSquared(leftVar, leftDt)
                }

                val value = expr.right.constValue(program)
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
                    val rightVal = expr.right.constValue(program)?.number?.toInt()
                    if(rightVal!=null && rightVal==2) {
                        translateExpressionInternal(expr.left)
                        when(leftDt) {
                            DataType.UBYTE -> asmgen.out("  lsr  P8ESTACK_LO+1,x")
                            DataType.BYTE -> asmgen.out("  lda  P8ESTACK_LO+1,x |  asl  a |  ror  P8ESTACK_LO+1,x")
                            DataType.UWORD -> asmgen.out("  lsr  P8ESTACK_HI+1,x |  ror  P8ESTACK_LO+1,x")
                            DataType.WORD -> asmgen.out("  lda  P8ESTACK_HI+1,x |  asl  a |  ror  P8ESTACK_HI+1,x |  ror  P8ESTACK_LO+1,x")
                            else -> throw AssemblyError("wrong dt")
                        }
                        return
                    }
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
            // the general, non-optimized cases  TODO optimize more cases.... (or one day just don't use the evalstack at all anymore)
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

    private fun translateSquared(variable: IdentifierReference, dt: DataType) {
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

    private fun translateExpression(expr: PrefixExpression) {
        translateExpressionInternal(expr.expression)
        val itype = expr.inferType(program)
        val type = itype.getOrElse { throw AssemblyError("unknown dt") }
        when(expr.operator) {
            "+" -> {}
            "-" -> {
                when(type) {
                    in ByteDatatypes -> asmgen.out("  jsr  prog8_lib.neg_b")
                    in WordDatatypes -> asmgen.out("  jsr  prog8_lib.neg_w")
                    DataType.FLOAT -> asmgen.out("  jsr  floats.neg_f")
                    else -> throw AssemblyError("weird type")
                }
            }
            "~" -> {
                when(type) {
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
            "not" -> {
                when(type) {
                    in ByteDatatypes -> asmgen.out("  jsr  prog8_lib.not_byte")
                    in WordDatatypes -> asmgen.out("  jsr  prog8_lib.not_word")
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("invalid prefix operator ${expr.operator}")
        }
    }

    private fun translateExpression(arrayExpr: ArrayIndexedExpression) {
        val elementIDt = arrayExpr.inferType(program)
        if(!elementIDt.isKnown)
            throw AssemblyError("unknown dt")
        val elementDt = elementIDt.getOr(DataType.UNDEFINED)
        val arrayVarName = asmgen.asmVariableName(arrayExpr.arrayvar)
        val constIndexNum = arrayExpr.indexer.constIndex()
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
                    asmgen.out("  lda  #<$arrayVarName+$indexValue |  ldy  #>$arrayVarName+$indexValue |  jsr  floats.push_float")
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
            "**" -> throw AssemblyError("** operator requires floats")
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
            "and" -> asmgen.out("  jsr  prog8_lib.and_b")
            "or" -> asmgen.out("  jsr  prog8_lib.or_b")
            "xor" -> asmgen.out("  jsr  prog8_lib.xor_b")
            else -> throw AssemblyError("invalid operator $operator")
        }
    }

    private fun translateBinaryOperatorWords(operator: String, dt: DataType) {
        when(operator) {
            "**" -> throw AssemblyError("** operator requires floats")
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
            "!=" -> asmgen.out("  jsr  prog8_lib.notequal_w")            "&" -> asmgen.out("  jsr  prog8_lib.bitand_w")
            "^" -> asmgen.out("  jsr  prog8_lib.bitxor_w")
            "|" -> asmgen.out("  jsr  prog8_lib.bitor_w")
            "and" -> asmgen.out("  jsr  prog8_lib.and_w")
            "or" -> asmgen.out("  jsr  prog8_lib.or_w")
            "xor" -> asmgen.out("  jsr  prog8_lib.xor_w")
            else -> throw AssemblyError("invalid operator $operator")
        }
    }

    private fun translateBinaryOperatorFloats(operator: String) {
        when(operator) {
            "**" -> asmgen.out(" jsr  floats.pow_f")
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
            "%", "<<", ">>", "&", "^", "|", "and", "or", "xor" -> throw AssemblyError("requires integer datatype")
            else -> throw AssemblyError("invalid operator $operator")
        }
    }

    private fun translateCompareStrings(s1: Expression, operator: String, s2: Expression) {
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
