package prog8.code.ast

import prog8.code.core.*


sealed interface IPtSubroutine {
    val name: String
}

class PtAsmSub(
    name: String,
    val address: UInt?,
    val clobbers: Set<CpuRegister>,
    val parameters: List<Pair<RegisterOrStatusflag, PtSubroutineParameter>>,
    val returns: List<Pair<RegisterOrStatusflag, DataType>>,
    val inline: Boolean,
    position: Position
) : PtNamedNode(name, position), IPtSubroutine


class PtSub(
    name: String,
    val parameters: List<PtSubroutineParameter>,
    val returntype: DataType?,
    position: Position
) : PtNamedNode(name, position), IPtSubroutine, IPtStatementContainer {
    init {
        // params and return value should not be str
        if(parameters.any{ it.type !in NumericDatatypes && it.type!=DataType.BOOL })
            throw AssemblyError("non-numeric/non-bool parameter")
        if(returntype!=null && returntype !in NumericDatatypes && returntype!=DataType.BOOL)
            throw AssemblyError("non-numeric/non-bool returntype $returntype")
        parameters.forEach { it.parent=this }
    }
}


class PtSubroutineParameter(name: String, val type: DataType, position: Position): PtNamedNode(name, position)


sealed interface IPtAssignment {
    val children: MutableList<PtNode>
    val target: PtAssignTarget
        get() = children[0] as PtAssignTarget
    val value: PtExpression
        get() = children[1] as PtExpression
}

class PtAssignment(position: Position) : PtNode(position), IPtAssignment

class PtAugmentedAssign(val operator: String, position: Position) : PtNode(position), IPtAssignment


class PtAssignTarget(position: Position) : PtNode(position) {
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

    infix fun isSameAs(expression: PtExpression): Boolean = expression.isSameAs(this)
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
}


class PtJump(val identifier: PtIdentifier?,     // note: even ad-hoc labels are wrapped as an Identifier to simplify code. Just use dummy type and position.
             val address: UInt?,
             position: Position) : PtNode(position) {
    init {
        identifier?.let {it.parent = this }
    }
}


class PtPostIncrDecr(val operator: String, position: Position) : PtNode(position) {
    val target: PtAssignTarget
        get() = children.single() as PtAssignTarget
}


class PtRepeatLoop(position: Position) : PtNode(position) {
    val count: PtExpression
        get() = children[0] as PtExpression
    val statements: PtNodeGroup
        get() = children[1] as PtNodeGroup
}


class PtReturn(position: Position) : PtNode(position) {
    val hasValue = children.any()
    val value: PtExpression?
        get() {
            return if(children.any())
                children.single() as PtExpression
            else
                null
        }
}


sealed interface IPtVariable {
    val name: String
    val type: DataType
}


class PtVariable(name: String, override val type: DataType, val zeropage: ZeropageWish, val value: PtExpression?, val arraySize: UInt?, position: Position) : PtNamedNode(name, position), IPtVariable {
    init {
        value?.let {it.parent=this}
    }
}


class PtConstant(name: String, override val type: DataType, val value: Double, position: Position) : PtNamedNode(name, position), IPtVariable


class PtMemMapped(name: String, override val type: DataType, val address: UInt, val arraySize: UInt?, position: Position) : PtNamedNode(name, position), IPtVariable


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
