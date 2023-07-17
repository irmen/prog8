package prog8.codegen.cpu6502.assignment

import prog8.code.ast.PtBinaryExpression
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal

//
// This contains codegen for stack-based evaluation of binary expressions.
// It uses the CPU stack so depth is limited.
// It is called "as a last resort" if the optimized codegen path is unable
// to come up with a special case of the expression.
//
internal class AnyExprAsmGen(
    private val asmgen: AsmGen6502Internal
) {
    fun assignAnyExpressionUsingStack(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        when(expr.type) {
            in ByteDatatypes -> {
                if(expr.left.type in ByteDatatypes && expr.right.type in ByteDatatypes)
                    return assignByteBinExpr(expr, assign)
                if (expr.left.type in WordDatatypes && expr.right.type in WordDatatypes) {
                    require(expr.operator in ComparisonOperators)
                    TODO("words operands comparison -> byte")
                }
                if (expr.left.type==DataType.FLOAT && expr.right.type==DataType.FLOAT) {
                    require(expr.operator in ComparisonOperators)
                    return assignFloatBinExpr(expr, assign)
                }
                TODO("weird expr operand types")
            }
            in WordDatatypes -> {
                require(expr.left.type in WordDatatypes && expr.right.type in WordDatatypes) {
                    "both operands must be words"
                }
                return assignWordBinExpr(expr, assign)
            }
            DataType.FLOAT -> {
                require(expr.left.type==DataType.FLOAT && expr.right.type==DataType.FLOAT) {
                    "both operands must be floats"
                }
                return assignFloatBinExpr(expr, assign)
            }
            else -> throw AssemblyError("weird expression type in assignment")
        }
    }

    private fun assignWordBinExpr(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        when(expr.operator) {
            "+" -> {
                TODO("word + at ${expr.position}")
            }
            "-" -> {
                TODO("word - at ${expr.position}")
            }
            "*" -> {
                TODO("word * at ${expr.position}")
            }
            "/" -> {
                TODO("word / at ${expr.position}")
            }
            "<<" -> {
                TODO("word << at ${expr.position}")
            }
            ">>" -> {
                TODO("word >> at ${expr.position}")
            }
            "%" -> {
                TODO("word % at ${expr.position}")
            }
            "&", "and" -> {
                TODO("word and at ${expr.position}")
            }
            "|", "or" -> {
                TODO("word or at ${expr.position}")
            }
            "^", "xor" -> {
                TODO("word xor at ${expr.position}")
            }
            "==" -> {
                TODO("word == at ${expr.position}")
            }
            "!=" -> {
                TODO("word != at ${expr.position}")
            }
            "<" -> {
                TODO("word < at ${expr.position}")
            }
            "<=" -> {
                TODO("word <= at ${expr.position}")
            }
            ">" -> {
                TODO("word > at ${expr.position}")
            }
            ">=" -> {
                TODO("word >= at ${expr.position}")
            }
            else -> return false
        }
    }

    private fun assignByteBinExpr(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        when(expr.operator) {
            "+" -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                asmgen.out("  pha")
                asmgen.assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.out("  pla |  clc |  adc  P8ZP_SCRATCH_B1")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "-" -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                asmgen.out("  pha")
                asmgen.assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.out("  pla |  sec |  sbc  P8ZP_SCRATCH_B1")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "*" -> {
                TODO("byte * at ${expr.position}")
            }
            "/" -> {
                TODO("byte / at ${expr.position}")
            }
            "<<" -> {
                TODO("byte << at ${expr.position}")
            }
            ">>" -> {
                TODO("byte >> at ${expr.position}")
            }
            "%" -> {
                TODO("byte % at ${expr.position}")
            }
            "&", "and" -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                asmgen.out("  pha")
                asmgen.assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.out("  pla |  and  P8ZP_SCRATCH_B1")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "|", "or" -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                asmgen.out("  pha")
                asmgen.assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.out("  pla |  ora  P8ZP_SCRATCH_B1")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "^", "xor" -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                asmgen.out("  pha")
                asmgen.assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.out("  pla |  eor  P8ZP_SCRATCH_B1")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "==" -> {
                TODO("byte == at ${expr.position}")
            }
            "!=" -> {
                TODO("byte != at ${expr.position}")
            }
            "<" -> {
                TODO("byte < at ${expr.position}")
            }
            "<=" -> {
                TODO("byte <= at ${expr.position}")
            }
            ">" -> {
                TODO("byte > at ${expr.position}")
            }
            ">=" -> {
                TODO("byte >= at ${expr.position}")
            }
            else -> return false
        }
    }

    private fun assignFloatBinExpr(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        when(expr.operator) {
            "+" -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.FAC1, true)
                if(!expr.right.isSimple()) asmgen.pushFAC1()
                asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.FAC2, true)
                if(!expr.right.isSimple()) asmgen.popFAC1()
                asmgen.out("  jsr  floats.FADDT")
                asmgen.assignRegister(RegisterOrPair.FAC1, assign.target)
                return true
            }
            "-" -> {
                asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.FAC1, true)
                if(!expr.left.isSimple()) asmgen.pushFAC1()
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.FAC2, true)
                if(!expr.left.isSimple()) asmgen.popFAC1()
                asmgen.out("  jsr  floats.FSUBT")
                asmgen.assignRegister(RegisterOrPair.FAC1, assign.target)
                return true
            }
            "*" -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.FAC1, true)
                if(!expr.right.isSimple()) asmgen.pushFAC1()
                asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.FAC2, true)
                if(!expr.right.isSimple()) asmgen.popFAC1()
                asmgen.out("  jsr  floats.FMULTT")
                asmgen.assignRegister(RegisterOrPair.FAC1, assign.target)
                return true
            }
            "/" -> {
                asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.FAC1, true)
                if(!expr.left.isSimple()) asmgen.pushFAC1()
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.FAC2, true)
                if(!expr.left.isSimple()) asmgen.popFAC1()
                asmgen.out("  jsr  floats.FDIVT")
                asmgen.assignRegister(RegisterOrPair.FAC1, assign.target)
                return true
            }
            "==" -> {
                setupFloatComparisonFAC1vsVarAY(expr)
                asmgen.out("  jsr  floats.var_fac1_equal_f")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "!=" -> {
                setupFloatComparisonFAC1vsVarAY(expr)
                asmgen.out("  jsr  floats.var_fac1_notequal_f")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "<" -> {
                setupFloatComparisonFAC1vsVarAY(expr)
                asmgen.out("  jsr  floats.var_fac1_less_f")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            ">" -> {
                setupFloatComparisonFAC1vsVarAY(expr)
                asmgen.out("  jsr  floats.var_fac1_greater_f")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "<=" -> {
                setupFloatComparisonFAC1vsVarAY(expr)
                asmgen.out("  jsr  floats.var_fac1_lesseq_f")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            ">=" -> {
                setupFloatComparisonFAC1vsVarAY(expr)
                asmgen.out("  jsr  floats.var_fac1_greatereq_f")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            else -> TODO("float expression operator ${expr.operator}")
        }
        return false
    }

    private fun setupFloatComparisonFAC1vsVarAY(expr: PtBinaryExpression) {
        asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.FAC1, true)
        if(!expr.right.isSimple()) asmgen.pushFAC1()
        asmgen.assignExpressionToVariable(expr.right, "floats.floats_temp_var", DataType.FLOAT)
        if(!expr.right.isSimple()) asmgen.popFAC1()
        asmgen.out("  lda  #<floats.floats_temp_var |  ldy  #>floats.floats_temp_var")
    }
}