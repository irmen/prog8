package prog8.codegen.cpu6502

import prog8.code.ast.*
import prog8.code.core.*
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

internal fun PtIdentifier.targetStatement(program: PtProgram): PtNode {
    return if(name in BuiltinFunctions)
        this     // just reuse the node itself to refer to the builtin function
    else
        program.lookup(name)
}

internal fun PtProgram.lookup(name: String): PtNode {
    val remainder = name.split('.').toMutableList()     // TODO optimize split to not use memory allocations

    fun recurse(node: PtNode): PtNode {
        when(node) {
            is PtProgram -> {
                val blockName = remainder.removeFirst()
                return recurse(allBlocks().single { it.name==blockName })
            }
            is PtAsmSub -> {
                require(remainder.isEmpty())
                return node
            }
            is PtSub -> {
                if(remainder.isEmpty())
                    return node
                if(remainder.size==1) {
                    // look to see if there is a block of vardecls
                    val scopevars = node.children.firstOrNull() as? PtScopeVarsDecls
                    if(scopevars!=null)
                        return recurse(scopevars)
                }
                val childName = remainder.removeFirst()
                return recurse(node.children.filterIsInstance<PtNamedNode>().single { it.name==childName})
            }
            is PtBlock, is PtScopeVarsDecls, is PtNamedNode -> {
                if(remainder.isEmpty())
                    return node
                val childName = remainder.removeFirst()
                return recurse(node.children.filterIsInstance<PtNamedNode>().single { it.name==childName})
            }
            else -> throw IllegalArgumentException("invalid name $name in parent $node")
        }
    }

    return recurse(this)
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

internal fun PtFunctionCall.targetSubroutine(program: PtProgram): IPtSubroutine? =
    this.targetStatement(program) as? IPtSubroutine

internal fun PtFunctionCall.targetStatement(program: PtProgram): PtNode {
    return if(name in BuiltinFunctions)
        this     // just reuse the node itself to refer to the builtin function
    else
        program.lookup(name)
}

internal fun IPtSubroutine.returnsWhatWhere(): List<Pair<DataType, RegisterOrStatusflag>> {
    when(this) {
        is PtAsmSub -> {
            return returnTypes.zip(this.retvalRegisters)
        }
        is PtSub -> {
            // for non-asm subroutines, determine the return registers based on the type of the return value
            return if(returntype==null)
                emptyList()
            else {
                val register = when (returntype!!) {
                    in ByteDatatypes -> RegisterOrStatusflag(RegisterOrPair.A, null)
                    in WordDatatypes -> RegisterOrStatusflag(RegisterOrPair.AY, null)
                    DataType.FLOAT -> RegisterOrStatusflag(RegisterOrPair.FAC1, null)
                    else -> RegisterOrStatusflag(RegisterOrPair.AY, null)
                }
                listOf(Pair(returntype!!, register))
            }
        }
    }
}
