package prog8.code.ast

import prog8.code.core.*


sealed interface IPtSubroutine {
    val name: String
    val scopedName: String
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


class PtSub(
    name: String,
    val parameters: List<PtSubroutineParameter>,
    val returntype: DataType?,
    position: Position
) : PtNamedNode(name, position), IPtSubroutine, IPtStatementContainer {
    init {
        // params and return value should not be str
        if(parameters.any{ !it.type.isNumericOrBool })
            throw AssemblyError("non-numeric/non-bool parameter")
        if(returntype!=null && !returntype.isNumericOrBool)
            throw AssemblyError("non-numeric/non-bool returntype $returntype")
        parameters.forEach { it.parent=this }
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

class PtAssignment(position: Position) : PtNode(position), IPtAssignment

class PtAugmentedAssign(val operator: String, position: Position) : PtNode(position), IPtAssignment


class PtAssignTarget(val void: Boolean, position: Position) : PtNode(position) {
    val identifier: PtIdentifier?
        get() = children.single() as? PtIdentifier
    val array: PtArrayIndexer?
        get() = children.single() as? PtArrayIndexer
    val memory: PtMemoryByte?
        get() = children.single() as? PtMemoryByte

    val type: DataType
        get() {
            return when(val tgt = children.single()) {
                is PtIdentifier -> tgt.type
                is PtArrayIndexer -> tgt.type
                is PtMemoryByte -> tgt.type
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
    val value: PtExpression?,
    val arraySize: UInt?,
    position: Position
) : PtNamedNode(name, position), IPtVariable {
    init {
        value?.let {it.parent=this}
    }
}


class PtConstant(name: String, override val type: DataType, val value: Double, position: Position) : PtNamedNode(name, position), IPtVariable


class PtMemMapped(name: String, override val type: DataType, val address: UInt, val arraySize: UInt?, position: Position) : PtNamedNode(name, position), IPtVariable {
    init {
        require(!type.isString)
    }
}


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
}


class PtDefer(position: Position): PtNode(position), IPtStatementContainer
