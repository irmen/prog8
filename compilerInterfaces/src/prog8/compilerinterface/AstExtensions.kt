package prog8.compilerinterface

import prog8.ast.base.FatalAstException
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.RangeExpr
import prog8.ast.statements.AssignTarget
import kotlin.math.abs

fun AssignTarget.isInRegularRAMof(machine: IMachineDefinition): Boolean {
    val memAddr = memoryAddress
    val arrayIdx = arrayindexed
    val ident = identifier
    when {
        memAddr != null -> {
            return when (memAddr.addressExpression) {
                is NumericLiteralValue -> {
                    machine.isRegularRAMaddress((memAddr.addressExpression as NumericLiteralValue).number.toInt())
                }
                is IdentifierReference -> {
                    val program = definingModule.program
                    val decl = (memAddr.addressExpression as IdentifierReference).targetVarDecl(program)
                    if ((decl?.type == VarDeclType.VAR || decl?.type == VarDeclType.CONST) && decl.value is NumericLiteralValue)
                        machine.isRegularRAMaddress((decl.value as NumericLiteralValue).number.toInt())
                    else
                        false
                }
                else -> false
            }
        }
        arrayIdx != null -> {
            val program = definingModule.program
            val targetStmt = arrayIdx.arrayvar.targetVarDecl(program)
            return if (targetStmt?.type == VarDeclType.MEMORY) {
                val addr = targetStmt.value as? NumericLiteralValue
                if (addr != null)
                    machine.isRegularRAMaddress(addr.number.toInt())
                else
                    false
            } else true
        }
        ident != null -> {
            val program = definingModule.program
            val decl = ident.targetVarDecl(program) ?: throw FatalAstException("invalid identifier ${ident.nameInSource}")
            return if (decl.type == VarDeclType.MEMORY && decl.value is NumericLiteralValue)
                machine.isRegularRAMaddress((decl.value as NumericLiteralValue).number.toInt())
            else
                true
        }
        else -> return true
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
