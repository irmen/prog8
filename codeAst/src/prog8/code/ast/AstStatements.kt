package prog8.code.ast

import prog8.code.core.*


class PtAsmSub(
    name: String,
    val address: UInt?,
    val clobbers: Set<CpuRegister>,
    val parameters: List<Pair<PtSubroutineParameter, RegisterOrStatusflag>>,
    val retvalRegisters: List<RegisterOrStatusflag>,
    val inline: Boolean,
    position: Position
) : PtNamedNode(name, position) {
    override fun printProperties() {
        print("$name  inline=$inline")
    }
}


class PtSub(
    name: String,
    val parameters: List<PtSubroutineParameter>,
    val returntype: DataType?,
    val inline: Boolean,
    position: Position
) : PtNamedNode(name, position) {
    override fun printProperties() {
        print(name)
    }
}


class PtSubroutineParameter(val name: String, val type: DataType, position: Position): PtNode(position) {
    override fun printProperties() {
        print("$type $name")
    }
}


class PtAssignment(position: Position) : PtNode(position) {
    val target: PtAssignTarget
        get() = children[0] as PtAssignTarget
    val value: PtExpression
        get() = children[1] as PtExpression

    override fun printProperties() { }
}


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
                is PtArrayIndexer -> tgt.type       // TODO array to elt type?
                is PtMemoryByte -> tgt.type
                else -> throw AssemblyError("weird dt")
            }
        }

    override fun printProperties() {}
}


class PtConditionalBranch(val condition: BranchCondition, position: Position) : PtNode(position) {
    val trueScope: PtNodeGroup
        get() = children[0] as PtNodeGroup
    val falseScope: PtNodeGroup
        get() = children[1] as PtNodeGroup

    override fun printProperties() {
        print(condition)
    }
}


class PtForLoop(position: Position) : PtNode(position) {
    val variable: PtIdentifier
        get() = children[0] as PtIdentifier
    val iterable: PtExpression
        get() = children[1] as PtExpression
    val statements: PtNodeGroup
        get() = children[2] as PtNodeGroup

    override fun printProperties() {}
}


class PtIfElse(position: Position) : PtNode(position) {
    val condition: PtExpression
        get() = children[0] as PtExpression
    val ifScope: PtNodeGroup
        get() = children[1] as PtNodeGroup
    val elseScope: PtNodeGroup
        get() = children[2] as PtNodeGroup

    override fun printProperties() {}
}


class PtJump(val identifier: PtIdentifier?,
             val address: UInt?,
             val generatedLabel: String?,
             position: Position) : PtNode(position) {
    override fun printProperties() {
        identifier?.printProperties()
        if(address!=null) print(address.toHex())
        if(generatedLabel!=null) print(generatedLabel)
    }
}


class PtPostIncrDecr(val operator: String, position: Position) : PtNode(position) {
    val target: PtAssignTarget
        get() = children.single() as PtAssignTarget

    override fun printProperties() {
        print(operator)
    }
}


class PtRepeatLoop(position: Position) : PtNode(position) {
    val count: PtExpression
        get() = children[0] as PtExpression
    val statements: PtNodeGroup
        get() = children[1] as PtNodeGroup

    override fun printProperties() {}
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

    override fun printProperties() {}
}


class PtVariable(name: String, val type: DataType, var value: PtExpression?, var arraySize: UInt?, position: Position) : PtNamedNode(name, position) {
    override fun printProperties() {
        print("$type  $name")
    }
}


class PtConstant(name: String, val type: DataType, val value: Double, position: Position) : PtNamedNode(name, position) {
    override fun printProperties() {
        print("$type $name = $value")
    }
}


class PtMemMapped(name: String, val type: DataType, val address: UInt, position: Position) : PtNamedNode(name, position) {
    override fun printProperties() {
        print("&$type $name = ${address.toHex()}")
    }
}


class PtWhen(position: Position) : PtNode(position) {
    val value: PtExpression
        get() = children[0] as PtExpression
    val choices: PtNodeGroup
        get() = children[1] as PtNodeGroup

    override fun printProperties() {}
}


class PtWhenChoice(val isElse: Boolean, position: Position) : PtNode(position) {
    val values: PtNodeGroup
        get() = children[0] as PtNodeGroup
    val statements: PtNodeGroup
        get() = children[1] as PtNodeGroup
    override fun printProperties() {}
}
