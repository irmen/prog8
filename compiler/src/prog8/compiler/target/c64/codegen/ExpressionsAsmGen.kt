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
            is DirectMemoryRead -> translateDirectMemReadExpression(expression, true)
            is NumericLiteralValue -> translateExpression(expression)
            is IdentifierReference -> translateExpression(expression)
            is FunctionCall -> translateFunctionCallResultOntoStack(expression)
            is ArrayLiteralValue, is StringLiteralValue -> throw AssemblyError("no asm gen for string/array literal value assignment - should have been replaced by a variable")
            is RangeExpr -> throw AssemblyError("range expression should have been changed into array values")
        }
    }

    internal fun translateComparisonExpressionWithJumpIfFalse(expr: BinaryExpression, jumpIfFalseLabel: String) {
        // first, if it is of the form:   <constvalue> <comparison> X  ,  swap the operands around
        var left = expr.left
        var right = expr.right
        var operator = expr.operator
        var leftConstVal = left.constValue(program)
        var rightConstVal = right.constValue(program)
        if(leftConstVal!=null) {
            val tmp = left
            left = right
            right = tmp
            val tmp2 = leftConstVal
            leftConstVal = rightConstVal
            rightConstVal = tmp2
            when(expr.operator) {
                "<" -> operator = ">"
                "<=" -> operator = ">="
                ">" -> operator = "<"
                ">=" -> operator = "<="
            }
        }

        val dt = left.inferType(program).typeOrElse(DataType.STRUCT)
        when (operator) {
            "==" -> {
                // if the left operand is an expression, and the right is 0, we can just evaluate that expression.
                // (the extra comparison is not required as the result of the expression is already the required boolean value)
                if(rightConstVal?.number?.toDouble() == 0.0) {
                    when(left) {
                        is PrefixExpression,
                        is BinaryExpression,
                        is ArrayIndexedExpression,
                        is TypecastExpression,
                        is AddressOf,
                        is RangeExpr,
                        is FunctionCall -> {
                            translateExpression(left)
                            if(dt in ByteDatatypes) {
                                asmgen.out("""
                                    inx
                                    lda  P8ESTACK_LO,x
                                    bne  $jumpIfFalseLabel""")
                                return
                            }
                            else if(dt in WordDatatypes) {
                                asmgen.out("""
                                    inx
                                    lda  P8ESTACK_LO,x
                                    clc
                                    adc  P8ESTACK_HI,x
                                    bne  $jumpIfFalseLabel""")
                                return
                            }
                        }
                        else -> {}
                    }
                }

                when (dt) {
                    in ByteDatatypes -> translateByteEquals(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    in WordDatatypes -> translateWordEquals(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatEquals(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            "!=" -> {
                // if the left operand is an expression, and the right is 0, we can just evaluate that expression.
                // (the extra comparison is not required as the result of the expression is already the required boolean value)
                if(rightConstVal?.number?.toDouble() == 0.0) {
                    when(left) {
                        is PrefixExpression,
                        is BinaryExpression,
                        is ArrayIndexedExpression,
                        is TypecastExpression,
                        is AddressOf,
                        is RangeExpr,
                        is FunctionCall -> {
                            translateExpression(left)
                            if(dt in ByteDatatypes) {
                                asmgen.out("""
                                    inx
                                    lda  P8ESTACK_LO,x
                                    beq  $jumpIfFalseLabel""")
                                return
                            }
                            else if(dt in WordDatatypes) {
                                asmgen.out("""
                                    inx
                                    lda  P8ESTACK_LO,x
                                    clc
                                    adc  P8ESTACK_HI,x
                                    beq  $jumpIfFalseLabel""")
                                return
                            }
                        }
                        else -> {}
                    }
                }

                when (dt) {
                    in ByteDatatypes -> translateByteNotEquals(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    in WordDatatypes -> translateWordNotEquals(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatNotEquals(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            "<" -> {
                when(dt) {
                    DataType.UBYTE -> translateUbyteLess(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.BYTE -> translateByteLess(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.UWORD -> translateUwordLess(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.WORD -> translateWordLess(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> {
                        translateExpression(left)
                        translateExpression(right)
                        asmgen.out("  jsr  floats.less_f |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
                    }
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            "<=" -> {
                when(dt) {
                    DataType.UBYTE -> translateUbyteLessOrEqual(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.BYTE -> translateByteLessOrEqual(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.UWORD -> translateUwordLessOrEqual(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.WORD -> translateWordLessOrEqual(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> {
                        translateExpression(left)
                        translateExpression(right)
                        asmgen.out("  jsr  floats.lesseq_f |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
                    }
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            ">" -> {
                when(dt) {
                    DataType.UBYTE -> translateUbyteGreater(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.BYTE -> translateByteGreater(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.UWORD -> translateUwordGreater(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.WORD -> translateWordGreater(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> {
                        translateExpression(left)
                        translateExpression(right)
                        asmgen.out("  jsr  floats.greater_f |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
                    }
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            ">=" -> {
                when(dt) {
                    DataType.UBYTE -> translateUbyteGreaterOrEqual(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.BYTE -> translateByteGreaterOrEqual(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.UWORD -> translateUwordGreaterOrEqual(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.WORD -> translateWordGreaterOrEqual(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> {
                        translateExpression(left)
                        translateExpression(right)
                        asmgen.out("  jsr  floats.greatereq_f |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
                    }
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
        }
    }

    private fun translateUbyteLess(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>=leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  $name
                            cmp  #${rightConstVal.number}
                            bcs  $jumpIfFalseLabel""")
                    else
                        asmgen.out("  jmp  $jumpIfFalseLabel")
                    return
                }
                else if (left is DirectMemoryRead) {
                    if(rightConstVal.number.toInt()!=0) {
                        translateDirectMemReadExpression(left, false)
                        asmgen.out("  cmp  #${rightConstVal.number} |  bcs  $jumpIfFalseLabel")
                    }
                    else
                        asmgen.out("  jmp  $jumpIfFalseLabel")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.less_ub |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateByteLess(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>=leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  $name
                            sec
                            sbc  #${rightConstVal.number}
                            bvc  +
                            eor  #$80
+                           bpl  $jumpIfFalseLabel""")
                    else
                        asmgen.out("  lda  $name |  bpl  $jumpIfFalseLabel")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.less_b |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateUwordLess(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>=leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  $name+1
                            cmp  #>${rightConstVal.number}
                            bcc  +
                            bne  $jumpIfFalseLabel
                            lda  $name
                            cmp  #<${rightConstVal.number}
                            bcs  $jumpIfFalseLabel
+""")
                    else
                        asmgen.out("  jmp  $jumpIfFalseLabel")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.less_uw |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateWordLess(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>=leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  $name
                            cmp  #<${rightConstVal.number}
                            lda  $name+1
                            sbc  #>${rightConstVal.number}
                            bvc  +
                            eor  #$80
+                           bpl  $jumpIfFalseLabel""")
                    else
                        asmgen.out("  lda  $name+1 |  bpl  $jumpIfFalseLabel")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.less_w |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateUbyteGreater(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<=leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  $name
                            cmp  #${rightConstVal.number}
                            bcc  $jumpIfFalseLabel
                            beq  $jumpIfFalseLabel""")
                    else
                        asmgen.out(" lda  $name |  beq $jumpIfFalseLabel")
                    return
                }
                else if (left is DirectMemoryRead) {
                    translateDirectMemReadExpression(left, false)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("  cmp  #${rightConstVal.number} |  bcc  $jumpIfFalseLabel |  beq  $jumpIfFalseLabel")
                    else
                        asmgen.out("  beq  $jumpIfFalseLabel")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.greater_ub |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateByteGreater(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<=leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  $name
                            clc
                            sbc  #${rightConstVal.number}
                            bvc  +
                            eor  #$80
+                           bpl  +
                            bmi  $jumpIfFalseLabel
+""")
                    else
                        asmgen.out(" lda  $name |  bmi  $jumpIfFalseLabel |  beq  $jumpIfFalseLabel")
                    return
                }
            }
        }
        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.greater_b |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateUwordGreater(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<=leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  $name+1
                            cmp  #>${rightConstVal.number}
                            bcc  $jumpIfFalseLabel
                            bne  +
                            lda  $name
                            cmp  #<${rightConstVal.number}
                            bcc  $jumpIfFalseLabel
                            beq  $jumpIfFalseLabel
+""")
                    else
                        asmgen.out("""
                            lda  $name
                            ora  $name+1
                            beq  $jumpIfFalseLabel""")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.greater_uw |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateWordGreater(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<=leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  #<${rightConstVal.number}
                            cmp  $name
                            lda  #>${rightConstVal.number}
                            sbc  $name+1
                            bvc  +
                            eor  #$80
+                           bpl  $jumpIfFalseLabel""")
                    else
                        asmgen.out("""
                            lda  #0
                            cmp  $name
                            sbc  $name+1
                            bvc  +
                            eor  #$80
+                           bpl  $jumpIfFalseLabel""")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.greater_w |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateUbyteLessOrEqual(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  $name
                            cmp  #${rightConstVal.number}
                            beq  +
                            bcs  $jumpIfFalseLabel
+""")
                    else
                        asmgen.out("""
                            lda  $name
                            bne  $jumpIfFalseLabel""")
                    return
                }
                else if (left is DirectMemoryRead) {
                    translateDirectMemReadExpression(left, false)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            cmp  #${rightConstVal.number} 
                            beq  +
                            bcs  $jumpIfFalseLabel
+""")
                    else
                        asmgen.out("  bne  $jumpIfFalseLabel")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.lesseq_ub |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateByteLessOrEqual(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  $name
                            clc
                            sbc  #${rightConstVal.number}
                            bvc  +
                            eor  #$80
+                           bpl  $jumpIfFalseLabel""")
                    else
                        asmgen.out("""
                            lda  $name
                            beq  +
                            bpl  $jumpIfFalseLabel
+""")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.lesseq_b |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateUwordLessOrEqual(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  #>${rightConstVal.number}
                            cmp  $name+1
                            bcc  $jumpIfFalseLabel
                            bne  +
                            lda  #<${rightConstVal.number}
                            cmp  $name
                            bcc  $jumpIfFalseLabel
+""")
                    else
                        asmgen.out("""
                            lda  $name
                            ora  $name+1
                            bne  $jumpIfFalseLabel""")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.lesseq_uw |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateWordLessOrEqual(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  #<${rightConstVal.number}
                            cmp  $name
                            lda  #>${rightConstVal.number}
                            sbc  $name+1
                            bvc  +
                            eor  #$80
    +                       bmi  $jumpIfFalseLabel""")
                    else
                        asmgen.out("""
                            lda  #0
                            cmp  $name
                            sbc  $name+1
                            bvc  +
                            eor  #$80
    +                       bmi  $jumpIfFalseLabel""")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.lesseq_w |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateUbyteGreaterOrEqual(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  $name
                            cmp  #${rightConstVal.number}
                            bcc  $jumpIfFalseLabel""")
                    return
                }
                else if (left is DirectMemoryRead) {
                    translateDirectMemReadExpression(left, false)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("  cmp  #${rightConstVal.number} |  bcc  $jumpIfFalseLabel")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.greatereq_ub |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateByteGreaterOrEqual(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0) {
                        asmgen.out("""
                            lda  $name
                            sec
                            sbc  #${rightConstVal.number}
                            bvc  +
                            eor  #$80
+                           bmi  $jumpIfFalseLabel""")
                         return
                    }
                    else {
                        asmgen.out(" lda  $name |  bmi  $jumpIfFalseLabel")
                        return
                    }
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.greatereq_b |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateUwordGreaterOrEqual(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  $name+1
                            cmp  #>${rightConstVal.number}
                            bcc  $jumpIfFalseLabel
                            bne  +
                            lda  $name
                            cmp  #<${rightConstVal.number}
                            bcc  $jumpIfFalseLabel
+""")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.greatereq_uw |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateWordGreaterOrEqual(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                            lda  $name
                            cmp  #<${rightConstVal.number}
                            lda  $name+1
                            sbc  #>${rightConstVal.number}
                            bvc  +
                            eor  #$80
+                           bmi  $jumpIfFalseLabel""")
                    else {
                        asmgen.out(" lda  $name+1 |  bmi  $jumpIfFalseLabel")
                    }
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.greatereq_w |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateByteEquals(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal!=leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("  lda  $name |  cmp  #${rightConstVal.number} |  bne  $jumpIfFalseLabel")
                    else
                        asmgen.out("  lda  $name |  bne  $jumpIfFalseLabel")
                    return
                }
                else if (left is DirectMemoryRead) {
                    translateDirectMemReadExpression(left, false)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("  cmp  #${rightConstVal.number} |  bne  $jumpIfFalseLabel")
                    else
                        asmgen.out("  bne  $jumpIfFalseLabel")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.equal_b |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateByteNotEquals(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal==leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("  lda  $name |  cmp  #${rightConstVal.number} |  beq  $jumpIfFalseLabel")
                    else
                        asmgen.out("  lda  $name |  beq  $jumpIfFalseLabel")
                    return
                }
                else if (left is DirectMemoryRead) {
                    translateDirectMemReadExpression(left, false)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("  cmp  #${rightConstVal.number} |  beq  $jumpIfFalseLabel")
                    else
                        asmgen.out("  beq  $jumpIfFalseLabel")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.notequal_b |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateWordEquals(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal!=leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                        lda  $name
                        cmp  #<${rightConstVal.number}
                        bne  $jumpIfFalseLabel
                        lda  $name+1
                        cmp  #>${rightConstVal.number}
                        bne  $jumpIfFalseLabel""")
                    else
                        asmgen.out("""
                        lda  $name
                        bne  $jumpIfFalseLabel
                        lda  $name+1
                        bne  $jumpIfFalseLabel""")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.equal_w |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateWordNotEquals(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal==leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0)
                        asmgen.out("""
                        lda  $name
                        cmp  #<${rightConstVal.number}
                        bne  +
                        lda  $name+1
                        cmp  #>${rightConstVal.number}
                        beq  $jumpIfFalseLabel
+""")
                    else
                        asmgen.out("""
                        lda  $name
                        bne  +
                        lda  $name+1
                        beq  $jumpIfFalseLabel
+""")
                    return
                }
            }
        }

        asmgen.translateExpression(left)
        asmgen.translateExpression(right)
        asmgen.out("  jsr  prog8_lib.notequal_w |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateFloatEquals(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal!=leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    when(rightConstVal.number.toDouble())
                    {
                        0.0 -> {
                            asmgen.out("""
                                lda  $name
                                clc
                                adc  $name+1
                                adc  $name+2
                                adc  $name+3
                                adc  $name+4
                                bne  $jumpIfFalseLabel""")
                            return
                        }
                        1.0 -> {
                            asmgen.out("""
                                lda  $name
                                cmp  #129
                                bne  $jumpIfFalseLabel
                                lda  $name+1
                                clc
                                adc  $name+2
                                adc  $name+3
                                adc  $name+4
                                bne  $jumpIfFalseLabel""")
                            return
                        }
                    }
                }
            }
        }

        translateExpression(left)
        translateExpression(right)
        asmgen.out("  jsr  floats.equal_f |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateFloatNotEquals(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal==leftConstVal)
                    asmgen.out("  jmp  $jumpIfFalseLabel")
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmgen.asmVariableName(left)
                    when(rightConstVal.number.toDouble())
                    {
                        0.0 -> {
                            asmgen.out("""
                                lda  $name
                                clc
                                adc  $name+1
                                adc  $name+2
                                adc  $name+3
                                adc  $name+4
                                beq  $jumpIfFalseLabel""")
                            return
                        }
                        1.0 -> {
                            asmgen.out("""
                                lda  $name
                                cmp  #129
                                bne  +
                                lda  $name+1
                                clc
                                adc  $name+2
                                adc  $name+3
                                adc  $name+4
                                beq  $jumpIfFalseLabel
+""")
                            return
                        }
                    }
                }
            }
        }

        translateExpression(left)
        translateExpression(right)
        asmgen.out("  jsr  floats.notequal_f |  inx |  lda  P8ESTACK_LO,x |  beq  $jumpIfFalseLabel")
    }

    private fun translateFunctionCallResultOntoStack(expression: FunctionCall) {
        val functionName = expression.target.nameInSource.last()
        val builtinFunc = BuiltinFunctions[functionName]
        if (builtinFunc != null) {
            asmgen.translateFunctioncallExpression(expression, builtinFunc)
        } else {
            val sub = expression.target.targetSubroutine(program.namespace)!!
            val preserveStatusRegisterAfterCall = sub.asmReturnvaluesRegisters.any {it.statusflag!=null}
            asmgen.translateFunctionCall(expression, preserveStatusRegisterAfterCall)
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

    private fun translateExpression(typecast: TypecastExpression) {
        translateExpression(typecast.expression)
        when(typecast.expression.inferType(program).typeOrElse(DataType.STRUCT)) {
            DataType.UBYTE -> {
                when(typecast.type) {
                    DataType.UBYTE, DataType.BYTE -> {}
                    DataType.UWORD, DataType.WORD -> {
                        if(CompilationTarget.instance.machine.cpu==CpuType.CPU65c02)
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
                    DataType.UWORD, DataType.WORD -> {
                        // sign extend
                        asmgen.out("""
                            lda  P8ESTACK_LO+1,x
                            ora  #$7f
                            bmi  +
                            lda  #0
+                           sta  P8ESTACK_HI+1,x""")
                    }
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

    internal fun translateDirectMemReadExpression(expr: DirectMemoryRead, pushResultOnEstack: Boolean) {
        when(expr.addressExpression) {
            is NumericLiteralValue -> {
                val address = (expr.addressExpression as NumericLiteralValue).number.toInt()
                asmgen.out("  lda  ${address.toHex()}")
                if(pushResultOnEstack)
                    asmgen.out("  sta  P8ESTACK_LO,x |  dex")
            }
            is IdentifierReference -> {
                // the identifier is a pointer variable, so read the value from the address in it
                asmgen.loadByteFromPointerIntoA(expr.addressExpression as IdentifierReference)
                if(pushResultOnEstack)
                    asmgen.out("  sta  P8ESTACK_LO,x |  dex")
            }
            else -> {
                translateExpression(expr.addressExpression)
                asmgen.out("  jsr  prog8_lib.read_byte_from_address_on_stack")
                if(pushResultOnEstack)
                    asmgen.out("  sta  P8ESTACK_LO+1,x")
                else
                    asmgen.out("  php |  inx |  plp")
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
                asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  floats.push_float")
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
                asmgen.out(" lda  #<$varname |  ldy  #>$varname|  jsr  floats.push_float")
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
}
