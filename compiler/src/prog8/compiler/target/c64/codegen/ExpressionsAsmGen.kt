package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.compiler.target.c64.MachineDefinition
import prog8.compiler.toHex
import prog8.functions.BuiltinFunctions
import kotlin.math.absoluteValue

internal class ExpressionsAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translateExpression(expression: Expression) {
        when(expression) {
            is PrefixExpression -> translateExpression(expression)
            is BinaryExpression -> translateExpression(expression)
            is ArrayIndexedExpression -> translatePushFromArray(expression)
            is TypecastExpression -> translateExpression(expression)
            is AddressOf -> translateExpression(expression)
            is DirectMemoryRead -> translateExpression(expression)
            is NumericLiteralValue -> translateExpression(expression)
            is RegisterExpr -> translateExpression(expression)
            is IdentifierReference -> translateExpression(expression)
            is FunctionCall -> translateExpression(expression)
            is ArrayLiteralValue, is StringLiteralValue -> TODO("string/array/struct assignment?")
            is StructLiteralValue -> throw AssemblyError("struct literal value assignment should have been flattened")
            is RangeExpr -> throw AssemblyError("range expression should have been changed into array values")
        }
    }

    private fun translateExpression(expression: FunctionCall) {
        val functionName = expression.target.nameInSource.last()
        val builtinFunc = BuiltinFunctions[functionName]
        if (builtinFunc != null) {
            asmgen.translateFunctioncallExpression(expression, builtinFunc)
        } else {
            asmgen.translateFunctionCall(expression)
            val sub = expression.target.targetSubroutine(program.namespace)!!
            val returns = sub.returntypes.zip(sub.asmReturnvaluesRegisters)
            for ((_, reg) in returns) {
                if (!reg.stack) {
                    // result value in cpu or status registers, put it on the stack
                    if (reg.registerOrPair != null) {
                        when (reg.registerOrPair) {
                            RegisterOrPair.A -> asmgen.out("  sta  ${MachineDefinition.ESTACK_LO_HEX},x |  dex")
                            RegisterOrPair.Y -> asmgen.out("  tya |  sta  ${MachineDefinition.ESTACK_LO_HEX},x |  dex")
                            RegisterOrPair.AY -> asmgen.out("  sta  ${MachineDefinition.ESTACK_LO_HEX},x |  tya |  sta  ${MachineDefinition.ESTACK_HI_HEX},x |  dex")
                            RegisterOrPair.X, RegisterOrPair.AX, RegisterOrPair.XY -> throw AssemblyError("can't push X register - use a variable")
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
                    DataType.UWORD, DataType.WORD -> asmgen.out("  lda  #0  |  sta  ${MachineDefinition.ESTACK_HI_PLUS1_HEX},x")
                    DataType.FLOAT -> asmgen.out(" jsr  c64flt.stack_ub2float")
                    in PassByReferenceDatatypes -> throw AssemblyError("cannot cast to a pass-by-reference datatype")
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.BYTE -> {
                when(expr.type) {
                    DataType.UBYTE, DataType.BYTE -> {}
                    DataType.UWORD, DataType.WORD -> asmgen.out("  lda  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x  |  ${asmgen.signExtendAtoMsb("${MachineDefinition.ESTACK_HI_PLUS1_HEX},x")}")
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
            in PassByReferenceDatatypes -> throw AssemblyError("cannot case a pass-by-reference datatypes into something else")
            else -> throw AssemblyError("weird type")
        }
    }

    private fun translateExpression(expr: AddressOf) {
        val name = asmgen.asmIdentifierName(expr.identifier)
        asmgen.out("  lda  #<$name |  sta  ${MachineDefinition.ESTACK_LO_HEX},x |  lda  #>$name  |  sta  ${MachineDefinition.ESTACK_HI_HEX},x  | dex")
    }

    private fun translateExpression(expr: DirectMemoryRead) {
        when(expr.addressExpression) {
            is NumericLiteralValue -> {
                val address = (expr.addressExpression as NumericLiteralValue).number.toInt()
                asmgen.out("  lda  ${address.toHex()} |  sta  ${MachineDefinition.ESTACK_LO_HEX},x |  dex")
            }
            is IdentifierReference -> {
                val sourceName = asmgen.asmIdentifierName(expr.addressExpression as IdentifierReference)
                asmgen.out("  lda  $sourceName |  sta  ${MachineDefinition.ESTACK_LO_HEX},x |  dex")
            }
            else -> {
                translateExpression(expr.addressExpression)
                asmgen.out("  jsr  prog8_lib.read_byte_from_address")
                asmgen.out("  sta  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x")
            }
        }
    }

    private fun translateExpression(expr: NumericLiteralValue) {
        when(expr.type) {
            DataType.UBYTE, DataType.BYTE -> asmgen.out(" lda  #${expr.number.toHex()}  | sta  ${MachineDefinition.ESTACK_LO_HEX},x  | dex")
            DataType.UWORD, DataType.WORD -> asmgen.out("""
                lda  #<${expr.number.toHex()}
                sta  ${MachineDefinition.ESTACK_LO_HEX},x
                lda  #>${expr.number.toHex()}
                sta  ${MachineDefinition.ESTACK_HI_HEX},x
                dex
            """)
            DataType.FLOAT -> {
                val floatConst = asmgen.getFloatConst(expr.number.toDouble())
                asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  c64flt.push_float")
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun translateExpression(expr: RegisterExpr) {
        when(expr.register) {
            Register.A -> asmgen.out(" sta  ${MachineDefinition.ESTACK_LO_HEX},x | dex")
            Register.X -> throw AssemblyError("cannot push X - use a variable instead of the X register")
            Register.Y -> asmgen.out(" tya |  sta  ${MachineDefinition.ESTACK_LO_HEX},x | dex")
        }
    }

    private fun translateExpression(expr: IdentifierReference) {
        val varname = asmgen.asmIdentifierName(expr)
        when(expr.inferType(program).typeOrElse(DataType.STRUCT)) {
            DataType.UBYTE, DataType.BYTE -> {
                asmgen.out("  lda  $varname  |  sta  ${MachineDefinition.ESTACK_LO_HEX},x  |  dex")
            }
            DataType.UWORD, DataType.WORD, in ArrayDatatypes, in StringDatatypes -> {
                // (for arrays and strings, push their address)
                asmgen.out("  lda  $varname  |  sta  ${MachineDefinition.ESTACK_LO_HEX},x  |  lda  $varname+1 |  sta  ${MachineDefinition.ESTACK_HI_HEX},x |  dex")
            }
            DataType.FLOAT -> {
                asmgen.out(" lda  #<$varname |  ldy  #>$varname|  jsr  c64flt.push_float")
            }
            else -> throw AssemblyError("stack push weird variable type $expr")
        }
    }

    private val optimizedByteMultiplications = setOf(3,5,6,7,9,10,11,12,13,14,15,20,25,40)
    private val optimizedWordMultiplications = setOf(3,5,6,7,9,10,12,15,20,25,40)
    private val powersOfTwo = setOf(0,1,2,4,8,16,32,64,128,256)

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
                // bit-shifts are always by a constant number (for now)
                translateExpression(expr.left)
                val amount = expr.right.constValue(program)!!.number.toInt()
                when (leftDt) {
                    DataType.UBYTE -> repeat(amount) { asmgen.out("  lsr  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x") }
                    DataType.BYTE -> repeat(amount) { asmgen.out("  lda  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x |  asl  a |  ror  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x") }
                    DataType.UWORD -> repeat(amount) { asmgen.out("  lsr  ${MachineDefinition.ESTACK_HI_PLUS1_HEX},x |  ror  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x") }
                    DataType.WORD -> repeat(amount) { asmgen.out("  lda  ${MachineDefinition.ESTACK_HI_PLUS1_HEX},x |  asl a  |  ror  ${MachineDefinition.ESTACK_HI_PLUS1_HEX},x |  ror  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x") }
                    else -> throw AssemblyError("weird type")
                }
                return
            }
            "<<" -> {
                // bit-shifts are always by a constant number (for now)
                translateExpression(expr.left)
                val amount = expr.right.constValue(program)!!.number.toInt()
                if (leftDt in ByteDatatypes)
                    repeat(amount) { asmgen.out("  asl  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x") }
                else
                    repeat(amount) { asmgen.out("  asl  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x |  rol  ${MachineDefinition.ESTACK_HI_PLUS1_HEX},x") }
                return
            }
            "*" -> {
                val value = expr.right.constValue(program)
                if(value!=null) {
                    if(rightDt in IntegerDatatypes) {
                        val amount = value.number.toInt()
                        if(amount in powersOfTwo)
                            printWarning("${expr.right.position} multiplication by power of 2 should have been optimized into a left shift instruction: $amount")
                        when(rightDt) {
                            DataType.UBYTE -> {
                                if(amount in optimizedByteMultiplications) {
                                    translateExpression(expr.left)
                                    asmgen.out(" jsr  math.mul_byte_$amount")
                                    return
                                }
                            }
                            DataType.BYTE -> {
                                if(amount in optimizedByteMultiplications) {
                                    translateExpression(expr.left)
                                    asmgen.out(" jsr  math.mul_byte_$amount")
                                    return
                                }
                                if(amount.absoluteValue in optimizedByteMultiplications) {
                                    translateExpression(expr.left)
                                    asmgen.out(" jsr  prog8_lib.neg_b |  jsr  math.mul_byte_${amount.absoluteValue}")
                                    return
                                }
                            }
                            DataType.UWORD -> {
                                if(amount in optimizedWordMultiplications) {
                                    translateExpression(expr.left)
                                    asmgen.out(" jsr  math.mul_word_$amount")
                                    return
                                }
                            }
                            DataType.WORD -> {
                                if(amount in optimizedWordMultiplications) {
                                    translateExpression(expr.left)
                                    asmgen.out(" jsr  math.mul_word_$amount")
                                    return
                                }
                                if(amount.absoluteValue in optimizedWordMultiplications) {
                                    translateExpression(expr.left)
                                    asmgen.out(" jsr  prog8_lib.neg_w |  jsr  math.mul_word_${amount.absoluteValue}")
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
        if(leftDt!=rightDt)
            throw AssemblyError("binary operator ${expr.operator} left/right dt not identical")     // is this strictly required always?
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
                            lda  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x
                            eor  #255
                            sta  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x
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

    private fun translatePushFromArray(arrayExpr: ArrayIndexedExpression) {
        // assume *reading* from an array
        val index = arrayExpr.arrayspec.index
        val arrayDt = arrayExpr.identifier.targetVarDecl(program.namespace)!!.datatype
        val arrayVarName = asmgen.asmIdentifierName(arrayExpr.identifier)
        if(index is NumericLiteralValue) {
            val elementDt = ArrayElementTypes.getValue(arrayDt)
            val indexValue = index.number.toInt() * elementDt.memorySize()
            when(elementDt) {
                in ByteDatatypes -> {
                    asmgen.out("  lda  $arrayVarName+$indexValue |  sta  ${MachineDefinition.ESTACK_LO_HEX},x |  dex")
                }
                in WordDatatypes -> {
                    asmgen.out("  lda  $arrayVarName+$indexValue |  sta  ${MachineDefinition.ESTACK_LO_HEX},x |  lda  $arrayVarName+$indexValue+1 |  sta  ${MachineDefinition.ESTACK_HI_HEX},x |  dex")
                }
                DataType.FLOAT -> {
                    asmgen.out("  lda  #<$arrayVarName+$indexValue |  ldy  #>$arrayVarName+$indexValue |  jsr  c64flt.push_float")
                }
                else -> throw AssemblyError("weird type")
            }
        } else {
            asmgen.translateArrayIndexIntoA(arrayExpr)
            asmgen.readAndPushArrayvalueWithIndexA(arrayDt, arrayExpr.identifier)
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
                lda  ${MachineDefinition.ESTACK_LO_PLUS2_HEX},x
                clc
                adc  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x
                inx
                sta  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x
                """)
            "-" -> asmgen.out("""
                lda  ${MachineDefinition.ESTACK_LO_PLUS2_HEX},x
                sec
                sbc  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x
                inx
                sta  ${MachineDefinition.ESTACK_LO_PLUS1_HEX},x
                """)
            "<<", ">>" -> throw AssemblyError("bit-shifts not via stack")
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
