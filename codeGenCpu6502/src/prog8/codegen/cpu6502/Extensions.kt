package prog8.codegen.cpu6502

import prog8.code.ast.*
import prog8.code.core.CpuRegister
import prog8.code.core.RegisterOrPair
import kotlin.math.abs

// TODO include this in the node class directly?

internal fun PtExpression.asConstInteger(): Int? =
    (this as? PtNumber)?.number?.toInt()


internal fun PtRange.toConstantIntegerRange(): IntProgression? {
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

    val fromLv = from as? PtNumber
    val toLv = to as? PtNumber
    val stepLv = step as? PtNumber
    if(fromLv==null || toLv==null || stepLv==null)
        return null
    val fromVal = fromLv.number.toInt()
    val toVal = toLv.number.toInt()
    val stepVal = stepLv.number.toInt()
    return makeRange(fromVal, toVal, stepVal)
}


fun PtExpression.isSimple(): Boolean {
    when(this) {
        is PtAddressOf -> TODO()
        is PtArray -> TODO()
        is PtArrayIndexer -> TODO()
        is PtBinaryExpression -> TODO()
        is PtBuiltinFunctionCall -> TODO()
        is PtContainmentCheck -> TODO()
        is PtFunctionCall -> TODO()
        is PtIdentifier -> TODO()
        is PtMachineRegister -> TODO()
        is PtMemoryByte -> TODO()
        is PtNumber -> TODO()
        is PtPrefix -> TODO()
        is PtRange -> TODO()
        is PtString -> TODO()
        is PtTypeCast -> TODO()
    }
}

internal fun PtIdentifier.targetStatement(program: PtProgram): PtNode? {
    TODO("Not yet implemented")
}

internal fun PtIdentifier.targetVarDecl(program: PtProgram): PtVariable? =
    this.targetStatement(program) as? PtVariable

internal fun IPtSubroutine.regXasResult(): Boolean =
    (this is PtAsmSub) && this.retvalRegisters.any { it.registerOrPair in arrayOf(RegisterOrPair.X, RegisterOrPair.AX, RegisterOrPair.XY) }

internal fun IPtSubroutine.shouldSaveX(): Boolean =
    this.regXasResult() || (this is PtAsmSub && (CpuRegister.X in this.clobbers || regXasParam()))

internal fun PtAsmSub.regXasParam(): Boolean =
    parameters.any { it.second.registerOrPair in arrayOf(RegisterOrPair.X, RegisterOrPair.AX, RegisterOrPair.XY) }

internal class KeepAresult(val saveOnEntry: Boolean, val saveOnReturn: Boolean)

internal fun PtAsmSub.shouldKeepA(): KeepAresult {
    // determine if A's value should be kept when preparing for calling the subroutine, and when returning from it

    // it seems that we never have to save A when calling? will be loaded correctly after setup.
    // but on return it depends on wether the routine returns something in A.
    val saveAonReturn = retvalRegisters.any { it.registerOrPair==RegisterOrPair.A || it.registerOrPair==RegisterOrPair.AY || it.registerOrPair==RegisterOrPair.AX }
    return KeepAresult(false, saveAonReturn)
}

internal fun PtFunctionCall.targetSubroutine(program: PtProgram): IPtSubroutine? {
    TODO()
}
