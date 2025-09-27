package prog8.code.ast

import prog8.code.core.*


sealed interface IPtSubroutine {
    val name: String
    val scopedName: String

    fun returnsWhatWhere(): List<Pair<RegisterOrStatusflag, DataType>> {
        when(this) {
            is PtAsmSub -> {
                return returns
            }
            is PtSub -> {
                // for non-asm subroutines, determine the return registers based on the type of the return values

                fun cpuRegisterFor(returntype: DataType): RegisterOrStatusflag = when {
                    returntype.isByteOrBool -> RegisterOrStatusflag(RegisterOrPair.A, null)
                    returntype.isWord -> RegisterOrStatusflag(RegisterOrPair.AY, null)
                    returntype.isFloat -> RegisterOrStatusflag(RegisterOrPair.FAC1, null)
                    else -> RegisterOrStatusflag(RegisterOrPair.AY, null)
                }

                val returns = signature.returns
                when(returns.size) {
                    0 -> return emptyList()
                    1 -> {
                        val returntype = returns.single()
                        val register = cpuRegisterFor(returntype)
                        return listOf(Pair(register, returntype))
                    }
                    else -> {
                        // for multi-value results, put the first one in A or AY cpu register(s) and the rest in the virtual registers starting from R15 and counting down
                        // a floating point return values is returned in FAC1. Only a single fp value is possible.
                        // The reason FAC2 cannot be used as well to support 2 fp values is that working with both FACs interferes with another.
                        val firstRegister = cpuRegisterFor(returns.first()) to returns.first()

                        val availableIntegerRegisters = Cx16VirtualRegisters.toMutableList()
                        val availableFloatRegisters = mutableListOf(RegisterOrPair.FAC1)        // just one value is possible
                        val others = returns.drop(1).map { type ->
                            when {
                                type.isFloat -> RegisterOrStatusflag(availableFloatRegisters.removeLastOrNull()!!, null) to type
                                type.isIntegerOrBool -> RegisterOrStatusflag(availableIntegerRegisters.removeLastOrNull()!!, null) to type
                                else -> throw AssemblyError("unsupported return type $type")
                            }
                        }

                        return listOf(firstRegister) + others
                    }
                }
            }
        }
    }
}

class PtAsmSub(
    name: String,
    val address: Address?,
    val clobbers: Set<CpuRegister>,
    val parameters: List<Pair<RegisterOrStatusflag, PtSubroutineParameter>>,
    val returns: List<Pair<RegisterOrStatusflag, DataType>>,
    val inline: Boolean,
    position: Position
) : PtNamedNode(name, position), IPtSubroutine {

    class Address(val constbank: UByte?, var varbank: PtIdentifier?, val address: UInt)
}


class PtSub(name: String, position: Position) : PtNamedNode(name, position), IPtSubroutine {

    constructor(name: String, params: List<PtSubroutineParameter>, returns: List<DataType>, position: Position) : this(name, position) {
        val signature = PtSubSignature(returns, position)
        params.forEach { signature.add(it) }
        add(signature)
    }

    val signature: PtSubSignature
        get() = children[0] as PtSubSignature
}


class PtSubSignature(val returns: List<DataType>, position: Position): PtNode(position) {
    // has all parameters PtSubroutineParameter as children.
    init {
        if(returns.any { !it.isNumericOrBool && !it.isPointer })
            throw AssemblyError("returntype is not a bool, number or pointer")
    }
}


class PtSubroutineParameter(name: String, val type: DataType, val register: RegisterOrPair?, position: Position): PtNamedNode(name, position)


sealed interface IPtAssignment {
    val children: MutableList<PtNode>
    val target: PtAssignTarget
        get() {
            if(children.size==2)
                return children[0] as PtAssignTarget
            else if(children.size<2)
                throw AssemblyError("incomplete node")
            else
                throw AssemblyError("no singular target")
        }
    val value: PtExpression
        get() = children.last() as PtExpression
    val multiTarget: Boolean
        get() = children.size>2
}

class PtAssignment(position: Position, val isVarInitializer: Boolean=false) : PtNode(position), IPtAssignment

class PtAugmentedAssign(val operator: String, position: Position) : PtNode(position), IPtAssignment


class PtAssignTarget(val void: Boolean, position: Position) : PtNode(position) {
    val identifier: PtIdentifier?
        get() = children.single() as? PtIdentifier
    val array: PtArrayIndexer?
        get() = children.single() as? PtArrayIndexer
    val memory: PtMemoryByte?
        get() = children.single() as? PtMemoryByte
    val pointerDeref: PtPointerDeref?
        get() = children.single() as? PtPointerDeref
    val indexedPointerDeref: PtArrayIndexedPointerDeref?
        get() = children.single() as? PtArrayIndexedPointerDeref

    val type: DataType
        get() {
            return when(val tgt = children.single()) {
                is PtIdentifier -> tgt.type
                is PtArrayIndexer -> tgt.type
                is PtMemoryByte -> tgt.type
                is PtPointerDeref -> tgt.type
                is PtArrayIndexedPointerDeref -> tgt.type
                else -> throw AssemblyError("weird target $tgt")
            }
        }

    infix fun isSameAs(expression: PtExpression): Boolean = !void && expression.isSameAs(this)
}


class PtConditionalBranch(val condition: BranchCondition, position: Position) : PtNode(position) {
    val trueScope: PtNodeGroup
        get() = children[0] as PtNodeGroup
    val falseScope: PtNodeGroup
        get() = children[1] as PtNodeGroup
}


class PtForLoop(position: Position) : PtNode(position) {
    val variable: PtIdentifier
        get() = children[0] as PtIdentifier
    val iterable: PtExpression
        get() = children[1] as PtExpression
    val statements: PtNodeGroup
        get() = children[2] as PtNodeGroup
}


class PtIfElse(position: Position) : PtNode(position) {
    val condition: PtExpression
        get() = children[0] as PtExpression
    val ifScope: PtNodeGroup
        get() = children[1] as PtNodeGroup
    val elseScope: PtNodeGroup
        get() = children[2] as PtNodeGroup

    fun hasElse(): Boolean = children.size==3 && elseScope.children.isNotEmpty()
}


class PtJump(position: Position) : PtNode(position) {
    val target: PtExpression
        get() = children.single() as PtExpression
}


class PtRepeatLoop(position: Position) : PtNode(position) {
    val count: PtExpression
        get() = children[0] as PtExpression
    val statements: PtNodeGroup
        get() = children[1] as PtNodeGroup
}


class PtReturn(position: Position) : PtNode(position)  // children are all expressions


sealed interface IPtVariable {
    val name: String
    val type: DataType
}


class PtVariable(
    name: String,
    override val type: DataType,
    val zeropage: ZeropageWish,
    val align: UInt,
    val dirty: Boolean,
    val value: PtExpression?,
    val arraySize: UInt?,
    position: Position
) : PtNamedNode(name, position), IPtVariable {
    init {

        if(value!=null) {
            require(value is PtArray || value is PtString) {
                "variable initializer value must only be array or string"
            }
            // NOTE: the 6502 code generator expects numerical variables to not have an initialization value,
            // because that is done via assignment statements. There are no "inline" variables with a given value.
            // All variables are put into zeropage or into the BSS section and initialized afterwards during program
            // startup or at the start of the subroutine.
            // The IR codegen however is different it has a special section <VARIABLESWITHINIT> for all variables
            // that have a non-zero initialization value, regardless of the datatype. It removes the initialization
            // assignment and puts the value back into the variable (but only in the symboltable).

            require(!dirty) { "dirty var cannot have init value" }
        }

        value?.let {it.parent=this}
    }
}


class PtConstant(name: String, override val type: DataType, val value: Double, position: Position) : PtNamedNode(name, position), IPtVariable
// note: a constant is a value but IS NOT a PtExpression node; all constants must have been replaced by their actual value


class PtMemMapped(name: String, override val type: DataType, val address: UInt, val arraySize: UInt?, position: Position) : PtNamedNode(name, position), IPtVariable {
    init {
        require(!type.isString)
    }
}


class PtStructDecl(name: String, val fields: List<Pair<DataType, String>>, position: Position) : PtNamedNode(name, position)


class PtWhen(position: Position) : PtNode(position) {
    val value: PtExpression
        get() = children[0] as PtExpression
    val choices: PtNodeGroup
        get() = children[1] as PtNodeGroup
}


class PtWhenChoice(val isElse: Boolean, position: Position) : PtNode(position) {
    val values: PtNodeGroup
        get() = children[0] as PtNodeGroup
    val statements: PtNodeGroup
        get() = children[1] as PtNodeGroup

    fun isOnlyGotoOrReturn(): Boolean {
        val c = statements.children
        if(c.size!=1)
            return false
        if(c[0] is PtJump || c[0] is PtReturn)
            return true
        val group = c[0] as? PtNodeGroup
        return group != null && group.children.size == 1 && (group.children[0] is PtJump || group.children[0] is PtReturn)
    }
}


class PtDefer(position: Position): PtNode(position)


class PtJmpTable(position: Position) : PtNode(position)     // contains only PtIdentifier nodes
