package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.compiler.AssemblyError
import prog8.compiler.target.CompilationTarget
import prog8.compiler.target.CpuType
import prog8.compiler.toHex
import prog8.functions.BuiltinFunctions
import kotlin.math.absoluteValue

internal class ExpressionsAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translateExpression(expression: Expression) {
        when(expression) {
            is PrefixExpression -> translateExpression(expression)
            is BinaryExpression -> translateExpression(expression)
            is ArrayIndexedExpression -> translateExpression(expression)
            is TypecastExpression -> translateExpression(expression)
            is AddressOf -> translateExpression(expression)
            is DirectMemoryRead -> translateExpression(expression)
            is NumericLiteralValue -> translateExpression(expression)
            is IdentifierReference -> translateExpression(expression)
            is FunctionCall -> translateExpression(expression)
            is ArrayLiteralValue, is StringLiteralValue -> throw AssemblyError("no asm gen for string/array literal value assignment - should have been replaced by a variable")
            is RangeExpr -> throw AssemblyError("range expression should have been changed into array values")
        }
    }

    private fun translateExpression(expression: FunctionCall) {
        val functionName = expression.target.nameInSource.last()
        val builtinFunc = BuiltinFunctions[functionName]
        if (builtinFunc != null) {
            asmgen.translateFunctioncallExpression(expression, builtinFunc)
        } else {
            val sub = expression.target.targetSubroutine(program.namespace)!!
            asmgen.translateFunctionCall(expression)
            val returns = sub.returntypes.zip(sub.asmReturnvaluesRegisters)
            for ((_, reg) in returns) {
                if (!reg.stack) {
                    // result value in cpu or status registers, put it on the stack
                    if (reg.registerOrPair != null) {
                        when (reg.registerOrPair) {
                            RegisterOrPair.A -> asmgen.out("  sta  P8ESTACK_LO,x |  dex")
                            RegisterOrPair.Y -> asmgen.out("  tya |  sta  P8ESTACK_LO,x |  dex")
                            RegisterOrPair.AY -> asmgen.out("  sta  P8ESTACK_LO,x |  tya |  sta  P8ESTACK_HI,x |  dex")
                            RegisterOrPair.X -> {
                                // return value in X register has been discarded, just push a zero
                                if(CompilationTarget.instance.machine.cpu==CpuType.CPU65c02)
                                    asmgen.out("  stz  P8ESTACK_LO,x")
                                else
                                    asmgen.out("  lda  #0 |  sta  P8ESTACK_LO,x")
                                asmgen.out("  dex")
                            }
                            RegisterOrPair.AX -> {
                                // return value in X register has been discarded, just push a zero in this place
                                asmgen.out("  sta  P8ESTACK_LO,x")
                                if(CompilationTarget.instance.machine.cpu==CpuType.CPU65c02)
                                    asmgen.out("  stz  P8ESTACK_HI,x")
                                else
                                    asmgen.out("  lda  #0 |  sta  P8ESTACK_HI,x")
                                asmgen.out("  dex")
                            }
                            RegisterOrPair.XY -> {
                                // return value in X register has been discarded, just push a zero in this place
                                if(CompilationTarget.instance.machine.cpu==CpuType.CPU65c02)
                                    asmgen.out("  stz  P8ESTACK_LO,x")
                                else
                                    asmgen.out("  lda  #0 |  sta  P8ESTACK_LO,x")
                                asmgen.out("  tya |  sta  P8ESTACK_HI,x |  dex")
                            }
                        }
                    }
                    // return value from a statusregister is not put on the stack, it should be acted on via a conditional branch such as if_cc
                }
            }
        }
    }

    private fun translateExpression(expr: TypecastExpression) {
        translateExpression(expr.expression)
        when(expr.expression.inferType(program).typeOrElse(DataType.STRUCT)) {
            DataType.UBYTE -> {
                when(expr.type) {
                    DataType.UBYTE, DataType.BYTE -> {}
                    DataType.UWORD, DataType.WORD -> {
                        if(CompilationTarget.instance.machine.cpu==CpuType.CPU65c02)
                            asmgen.out("  stz  P8ESTACK_HI+1,x")
                        else
                            asmgen.out("  lda  #0  |  sta  P8ESTACK_HI+1,x")
                    }
                    DataType.FLOAT -> asmgen.out(" jsr  c64flt.stack_ub2float")
                    in PassByReferenceDatatypes -> throw AssemblyError("cannot cast to a pass-by-reference datatype")
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.BYTE -> {
                when(expr.type) {
                    DataType.UBYTE, DataType.BYTE -> {}
                    DataType.UWORD, DataType.WORD -> {
                        // sign extend
                        asmgen.out(""" 
                            lda  P8ESTACK_LO+1,x
                            ora  #$7f
                            bmi  +
                            lda  #0
+                           sta  P8ESTACK_HI+1,x""")
                    }
                    DataType.FLOAT -> asmgen.out(" jsr  c64flt.stack_b2float")
                    in PassByReferenceDatatypes -> throw AssemblyError("cannot cast to a pass-by-reference datatype")
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when(expr.type) {
                    DataType.BYTE, DataType.UBYTE -> {}
                    DataType.WORD, DataType.UWORD -> {}
                    DataType.FLOAT -> asmgen.out(" jsr  c64flt.stack_uw2float")
                    in PassByReferenceDatatypes -> throw AssemblyError("cannot cast to a pass-by-reference datatype")
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.WORD -> {
                when(expr.type) {
                    DataType.BYTE, DataType.UBYTE -> {}
                    DataType.WORD, DataType.UWORD -> {}
                    DataType.FLOAT -> asmgen.out(" jsr  c64flt.stack_w2float")
                    in PassByReferenceDatatypes -> throw AssemblyError("cannot cast to a pass-by-reference datatype")
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.FLOAT -> {
                when(expr.type) {
                    DataType.UBYTE -> asmgen.out(" jsr  c64flt.stack_float2uw")
                    DataType.BYTE -> asmgen.out(" jsr  c64flt.stack_float2w")
                    DataType.UWORD -> asmgen.out(" jsr  c64flt.stack_float2uw")
                    DataType.WORD -> asmgen.out(" jsr  c64flt.stack_float2w")
                    DataType.FLOAT -> {}
                    in PassByReferenceDatatypes -> throw AssemblyError("cannot cast to a pass-by-reference datatype")
                    else -> throw AssemblyError("weird type")
                }
            }
            in PassByReferenceDatatypes -> throw AssemblyError("cannot cast pass-by-reference value into another type")
            else -> throw AssemblyError("weird type")
        }
    }

    private fun translateExpression(expr: AddressOf) {
        val name = asmgen.asmVariableName(expr.identifier)
        asmgen.out("  lda  #<$name |  sta  P8ESTACK_LO,x |  lda  #>$name  |  sta  P8ESTACK_HI,x  | dex")
    }

    private fun translateExpression(expr: DirectMemoryRead) {
        when(expr.addressExpression) {
            is NumericLiteralValue -> {
                val address = (expr.addressExpression as NumericLiteralValue).number.toInt()
                asmgen.out("  lda  ${address.toHex()} |  sta  P8ESTACK_LO,x |  dex")
            }
            is IdentifierReference -> {
                // the identifier is a pointer variable, so read the value from the address in it
                asmgen.loadByteFromPointerIntoA(expr.addressExpression as IdentifierReference)
                asmgen.out(" sta  P8ESTACK_LO,x |  dex")
            }
            else -> {
                translateExpression(expr.addressExpression)
                asmgen.out("  jsr  prog8_lib.read_byte_from_address_on_stack")
                asmgen.out("  sta  P8ESTACK_LO+1,x")
            }
        }
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
                val floatConst = asmgen.getFloatAsmConst(expr.number.toDouble())
                asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  c64flt.push_float")
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun translateExpression(expr: IdentifierReference) {
        val varname = asmgen.asmVariableName(expr)
        when(expr.inferType(program).typeOrElse(DataType.STRUCT)) {
            DataType.UBYTE, DataType.BYTE -> {
                asmgen.out("  lda  $varname  |  sta  P8ESTACK_LO,x  |  dex")
            }
            DataType.UWORD, DataType.WORD -> {
                asmgen.out("  lda  $varname  |  sta  P8ESTACK_LO,x  |  lda  $varname+1 |  sta  P8ESTACK_HI,x |  dex")
            }
            DataType.FLOAT -> {
                asmgen.out(" lda  #<$varname |  ldy  #>$varname|  jsr  c64flt.push_float")
            }
            in IterableDatatypes -> {
                asmgen.out("  lda  #<$varname  |  sta  P8ESTACK_LO,x  |  lda  #>$varname |  sta  P8ESTACK_HI,x |  dex")
            }
            else -> throw AssemblyError("stack push weird variable type $expr")
        }
    }

    private fun translateExpression(expr: BinaryExpression) {
        val leftIDt = expr.left.inferType(program)
        val rightIDt = expr.right.inferType(program)
        if(!leftIDt.isKnown || !rightIDt.isKnown)
            throw AssemblyError("can't infer type of both expression operands")

        val leftDt = leftIDt.typeOrElse(DataType.STRUCT)
        val rightDt = rightIDt.typeOrElse(DataType.STRUCT)
        // see if we can apply some optimized routines
        when(expr.operator) {
            ">>" -> {
                translateExpression(expr.left)
                val amount = expr.right.constValue(program)?.number?.toInt()
                if(amount!=null) {
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
                            var left = amount
                            while (left >= 7) {
                                asmgen.out(" jsr  math.shift_right_w_7")
                                left -= 7
                            }
                            if (left in 0..2)
                                repeat(left) { asmgen.out(" lda  P8ESTACK_HI+1,x |  asl a  |  ror  P8ESTACK_HI+1,x |  ror  P8ESTACK_LO+1,x") }
                            else
                                asmgen.out(" jsr  math.shift_right_w_$left")
                        }
                        else -> throw AssemblyError("weird type")
                    }
                    return
                }
            }
            "<<" -> {
                translateExpression(expr.left)
                val amount = expr.right.constValue(program)?.number?.toInt()
                if(amount!=null) {
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
                val value = expr.right.constValue(program)
                if(value!=null) {
                    if(rightDt in IntegerDatatypes) {
                        val amount = value.number.toInt()
                        when(rightDt) {
                            DataType.UBYTE -> {
                                if(amount in asmgen.optimizedByteMultiplications) {
                                    translateExpression(expr.left)
                                    asmgen.out(" jsr  math.stack_mul_byte_$amount")
                                    return
                                }
                            }
                            DataType.BYTE -> {
                                if(amount in asmgen.optimizedByteMultiplications) {
                                    translateExpression(expr.left)
                                    asmgen.out(" jsr  math.stack_mul_byte_$amount")
                                    return
                                }
                                if(amount.absoluteValue in asmgen.optimizedByteMultiplications) {
                                    translateExpression(expr.left)
                                    asmgen.out(" jsr  prog8_lib.neg_b |  jsr  math.stack_mul_byte_${amount.absoluteValue}")
                                    return
                                }
                            }
                            DataType.UWORD -> {
                                if(amount in asmgen.optimizedWordMultiplications) {
                                    translateExpression(expr.left)
                                    asmgen.out(" jsr  math.stack_mul_word_$amount")
                                    return
                                }
                            }
                            DataType.WORD -> {
                                if(amount in asmgen.optimizedWordMultiplications) {
                                    translateExpression(expr.left)
                                    asmgen.out(" jsr  math.stack_mul_word_$amount")
                                    return
                                }
                                if(amount.absoluteValue in asmgen.optimizedWordMultiplications) {
                                    translateExpression(expr.left)
                                    asmgen.out(" jsr  prog8_lib.neg_w |  jsr  math.stack_mul_word_${amount.absoluteValue}")
                                    return
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // the general, non-optimized cases
        translateExpression(expr.left)
        translateExpression(expr.right)
        if((leftDt in ByteDatatypes && rightDt !in ByteDatatypes)
                || (leftDt in WordDatatypes && rightDt !in WordDatatypes))
            throw AssemblyError("binary operator ${expr.operator} left/right dt not identical")

        when (leftDt) {
            in ByteDatatypes -> translateBinaryOperatorBytes(expr.operator, leftDt)
            in WordDatatypes -> translateBinaryOperatorWords(expr.operator, leftDt)
            DataType.FLOAT -> translateBinaryOperatorFloats(expr.operator)
            else -> throw AssemblyError("non-numerical datatype")
        }
    }

    private fun translateExpression(expr: PrefixExpression) {
        translateExpression(expr.expression)
        val type = expr.inferType(program).typeOrElse(DataType.STRUCT)
        when(expr.operator) {
            "+" -> {}
            "-" -> {
                when(type) {
                    in ByteDatatypes -> asmgen.out("  jsr  prog8_lib.neg_b")
                    in WordDatatypes -> asmgen.out("  jsr  prog8_lib.neg_w")
                    DataType.FLOAT -> asmgen.out("  jsr  c64flt.neg_f")
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
        val index = arrayExpr.arrayspec.index
        val elementDt = arrayExpr.inferType(program).typeOrElse(DataType.STRUCT)
        val arrayVarName = asmgen.asmVariableName(arrayExpr.identifier)
        if(index is NumericLiteralValue) {
            val indexValue = index.number.toInt() * elementDt.memorySize()
            when(elementDt) {
                in ByteDatatypes -> {
                    asmgen.out("  lda  $arrayVarName+$indexValue |  sta  P8ESTACK_LO,x |  dex")
                }
                in WordDatatypes -> {
                    asmgen.out("  lda  $arrayVarName+$indexValue |  sta  P8ESTACK_LO,x |  lda  $arrayVarName+$indexValue+1 |  sta  P8ESTACK_HI,x |  dex")
                }
                DataType.FLOAT -> {
                    asmgen.out("  lda  #<$arrayVarName+$indexValue |  ldy  #>$arrayVarName+$indexValue |  jsr  c64flt.push_float")
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
+                       jsr  c64flt.push_float""")
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

    private fun translateBinaryOperatorWords(operator: String, types: DataType) {
        when(operator) {
            "**" -> throw AssemblyError("** operator requires floats")
            "*" -> asmgen.out("  jsr  prog8_lib.mul_word")
            "/" -> asmgen.out(if(types==DataType.UWORD) "  jsr  prog8_lib.idiv_uw" else "  jsr  prog8_lib.idiv_w")
            "%" -> {
                if(types==DataType.WORD)
                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                asmgen.out("  jsr prog8_lib.remainder_uw")
            }
            "+" -> asmgen.out("  jsr  prog8_lib.add_w")
            "-" -> asmgen.out("  jsr  prog8_lib.sub_w")
            "<<" -> throw AssemblyError("<< should not operate via stack")
            ">>" -> throw AssemblyError(">> should not operate via stack")
            "<" -> asmgen.out(if(types==DataType.UWORD) "  jsr  prog8_lib.less_uw" else "  jsr  prog8_lib.less_w")
            ">" -> asmgen.out(if(types==DataType.UWORD) "  jsr  prog8_lib.greater_uw" else "  jsr  prog8_lib.greater_w")
            "<=" -> asmgen.out(if(types==DataType.UWORD) "  jsr  prog8_lib.lesseq_uw" else "  jsr  prog8_lib.lesseq_w")
            ">=" -> asmgen.out(if(types==DataType.UWORD) "  jsr  prog8_lib.greatereq_uw" else "  jsr  prog8_lib.greatereq_w")
            "==" -> asmgen.out("  jsr  prog8_lib.equal_w")
            "!=" -> asmgen.out("  jsr  prog8_lib.notequal_w")
            "&" -> asmgen.out("  jsr  prog8_lib.bitand_w")
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
            "**" -> asmgen.out(" jsr  c64flt.pow_f")
            "*" -> asmgen.out("  jsr  c64flt.mul_f")
            "/" -> asmgen.out("  jsr  c64flt.div_f")
            "+" -> asmgen.out("  jsr  c64flt.add_f")
            "-" -> asmgen.out("  jsr  c64flt.sub_f")
            "<" -> asmgen.out("  jsr  c64flt.less_f")
            ">" -> asmgen.out("  jsr  c64flt.greater_f")
            "<=" -> asmgen.out("  jsr  c64flt.lesseq_f")
            ">=" -> asmgen.out("  jsr  c64flt.greatereq_f")
            "==" -> asmgen.out("  jsr  c64flt.equal_f")
            "!=" -> asmgen.out("  jsr  c64flt.notequal_f")
            "%", "<<", ">>", "&", "^", "|", "and", "or", "xor" -> throw AssemblyError("requires integer datatype")
            else -> throw AssemblyError("invalid operator $operator")
        }
    }
}
