package prog8.compilerinterface

import prog8.ast.base.FatalAstException
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.RangeExpr
import prog8.ast.statements.AssignTarget
import kotlin.math.abs

fun AssignTarget.isIOAddress(machine: IMachineDefinition): Boolean {
    val memAddr = memoryAddress
    val arrayIdx = arrayindexed
    val ident = identifier
    when {
        memAddr != null -> {
            val addr = memAddr.addressExpression.constValue(definingModule.program)
            if(addr!=null)
                return machine.isIOAddress(addr.number.toInt())
            return when (memAddr.addressExpression) {
                is IdentifierReference -> {
                    val decl = (memAddr.addressExpression as IdentifierReference).targetVarDecl(definingModule.program)
                    val result = if ((decl?.type == VarDeclType.MEMORY || decl?.type == VarDeclType.CONST) && decl.value is NumericLiteralValue)
                        machine.isIOAddress((decl.value as NumericLiteralValue).number.toInt())
                    else
                        false
                    result
                }
                else -> false
            }
        }
        arrayIdx != null -> {
            val targetStmt = arrayIdx.arrayvar.targetVarDecl(definingModule.program)
            return if (targetStmt?.type == VarDeclType.MEMORY) {
                val addr = targetStmt.value as? NumericLiteralValue
                if (addr != null)
                    machine.isIOAddress(addr.number.toInt())
                else
                    false
            } else false
        }
        ident != null -> {
            val decl = ident.targetVarDecl(definingModule.program) ?: throw FatalAstException("invalid identifier ${ident.nameInSource}")
            return if (decl.type == VarDeclType.MEMORY && decl.value is NumericLiteralValue)
                machine.isIOAddress((decl.value as NumericLiteralValue).number.toInt())
            else
                false
        }
        else -> return false
    }
}

fun RangeExpr.toConstantIntegerRange(): IntProgression? {

    fun makeRange(fromVal: Int, toVal: Int, stepVal: Int): IntProgression {
        return when {
            fromVal <= toVal -> when {
                stepVal <= 0 -> IntRange.EMPTY
                stepVal == 1 -> fromVal..toVal
                else -> fromVal..toVal step stepVal
            }
            else -> when {
                stepVal >= 0 -> IntRange.EMPTY
                stepVal == -1 -> fromVal downTo toVal
                else -> fromVal downTo toVal step abs(stepVal)
            }
        }
    }

    val fromLv = from as? NumericLiteralValue
    val toLv = to as? NumericLiteralValue
    if(fromLv==null || toLv==null)
        return null
    val fromVal = fromLv.number.toInt()
    val toVal = toLv.number.toInt()
    val stepVal = (step as? NumericLiteralValue)?.number?.toInt() ?: 1
    return makeRange(fromVal, toVal, stepVal)
}

fun RangeExpr.size(): Int? {
    val fromLv = (from as? NumericLiteralValue)
    val toLv = (to as? NumericLiteralValue)
    if(fromLv==null || toLv==null)
        return null
    return toConstantIntegerRange()?.count()
}
