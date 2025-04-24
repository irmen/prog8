package prog8.codegen.cpu6502.assignment

import prog8.code.ast.PtBinaryExpression
import prog8.code.ast.PtExpression
import prog8.code.core.AssemblyError
import prog8.code.core.ComparisonOperators
import prog8.code.core.DataType
import prog8.code.core.RegisterOrPair
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
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
        when {
            expr.type.isByteOrBool -> {
                if(expr.left.type.isByteOrBool && expr.right.type.isByteOrBool)
                    return assignByteBinExpr(expr, assign)
                if (expr.left.type.isWord && expr.right.type.isWord) {
                    require(expr.operator in ComparisonOperators)
                    throw AssemblyError("words operands comparison -> byte, should have been handled elsewhere")
                }
                if (expr.left.type.isFloat && expr.right.type.isFloat) {
                    require(expr.operator in ComparisonOperators)
                    return assignFloatBinExpr(expr, assign)
                }
                throw AssemblyError("weird expr operand types: ${expr.left.type} and ${expr.right.type}")
            }
            expr.type.isWord -> {
                require(expr.left.type.isWord && expr.right.type.isWord) {
                    "both operands must be words"
                }
                throw AssemblyError("expression should have been handled otherwise: word ${expr.operator} at ${expr.position}")
            }
            expr.type.isFloat -> {
                require(expr.left.type.isFloat && expr.right.type.isFloat) {
                    "both operands must be floats"
                }
                return assignFloatBinExpr(expr, assign)
            }
            else -> throw AssemblyError("weird expression type in assignment")
        }
    }

    private fun assignByteBinExpr(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        when(expr.operator) {
            "+" -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A)
                asmgen.out("  pha")
                asmgen.assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.out("  pla |  clc |  adc  P8ZP_SCRATCH_B1")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "-" -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A)
                asmgen.out("  pha")
                asmgen.assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.out("  pla |  sec |  sbc  P8ZP_SCRATCH_B1")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "*" -> TODO("byte * at ${expr.position}")
            "/" -> TODO("byte / at ${expr.position}")
            "<<" -> TODO("byte << at ${expr.position}")
            ">>" -> TODO("byte >> at ${expr.position}")
            "%" -> TODO("byte % at ${expr.position}")
            "and" -> TODO("logical and (with optional shortcircuit) ${expr.position}")
            "or" -> TODO("logical or (with optional shortcircuit) ${expr.position}")
            "&" -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A)
                asmgen.out("  pha")
                asmgen.assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.out("  pla |  and  P8ZP_SCRATCH_B1")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "|" -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A)
                asmgen.out("  pha")
                asmgen.assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.out("  pla |  ora  P8ZP_SCRATCH_B1")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "^", "xor" -> {
                asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.A)
                asmgen.out("  pha")
                asmgen.assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.out("  pla |  eor  P8ZP_SCRATCH_B1")
                asmgen.assignRegister(RegisterOrPair.A, assign.target)
                return true
            }
            "==" -> TODO("byte == at ${expr.position}")
            "!=" -> TODO("byte != at ${expr.position}")
            "<" -> TODO("byte < at ${expr.position}")
            "<=" -> TODO("byte <= at ${expr.position}")
            ">" -> TODO("byte > at ${expr.position}")
            ">=" -> TODO("byte >= at ${expr.position}")
            else -> return false
        }
    }

    private fun assignFloatBinExpr(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        when(expr.operator) {
            "+" -> {
                assignFloatOperandsToFACandARG(expr.left, expr.right)
                asmgen.out("  jsr  floats.FADDT")
                asmgen.assignRegister(RegisterOrPair.FAC1, assign.target)
                return true
            }
            "-" -> {
                assignFloatOperandsToFACandARG(expr.right, expr.left)
                asmgen.out("  jsr  floats.FSUBT")
                asmgen.assignRegister(RegisterOrPair.FAC1, assign.target)
                return true
            }
            "*" -> {
                assignFloatOperandsToFACandARG(expr.left, expr.right)
                asmgen.out("  jsr  floats.FMULTT")
                asmgen.assignRegister(RegisterOrPair.FAC1, assign.target)
                return true
            }
            "/" -> {
                assignFloatOperandsToFACandARG(expr.right, expr.left)
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
    }

    private fun assignFloatOperandsToFACandARG(left: PtExpression, right: PtExpression) {
        when(asmgen.options.compTarget.name) {
            C64Target.NAME -> {
                // C64 math library has a quirk: you have always make sure FAC2/ARG is loaded last (done using CONUPK)
                // otherwise the result of certain floating point operations such as FDIVT will be wrong.
                // see https://www.c64-wiki.com/wiki/CONUPK
                // Unfortunately this means we have to push and pop an intermediary floating point value to and from memory.
                asmgen.assignExpressionToRegister(right, RegisterOrPair.FAC1, true)
                asmgen.pushFAC1()
                asmgen.assignExpressionToRegister(left, RegisterOrPair.FAC1, true)
                asmgen.popFAC2()
            }
            Cx16Target.NAME -> {
                asmgen.assignExpressionToRegister(left, RegisterOrPair.FAC1, true)
                if (!right.isSimple()) asmgen.pushFAC1()
                asmgen.assignExpressionToRegister(right, RegisterOrPair.FAC2, true)
                if (!right.isSimple()) asmgen.popFAC1()
            }
            else -> TODO("don't know how to evaluate float expression for selected compilation target  ${left.position}")
        }
    }

    private fun setupFloatComparisonFAC1vsVarAY(expr: PtBinaryExpression) {
        asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.FAC1, true)
        if(!expr.right.isSimple()) asmgen.pushFAC1()
        asmgen.assignExpressionToVariable(expr.right, "floats.floats_temp_var", DataType.FLOAT)
        if(!expr.right.isSimple()) asmgen.popFAC1()
        asmgen.out("  lda  #<floats.floats_temp_var |  ldy  #>floats.floats_temp_var")
    }
}