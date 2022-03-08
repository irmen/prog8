package prog8.compilerinterface.intermediate

import prog8.ast.base.BranchCondition
import prog8.ast.base.CpuRegister
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.statements.AssignmentOrigin
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.SubroutineParameter
import prog8.ast.toHex


class PtAsmSub(
    name: String,
    val address: UInt?,
    val clobbers: Set<CpuRegister>,
    val paramRegisters: List<RegisterOrStatusflag>,
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
    val parameters: List<SubroutineParameter>,
    val returntypes: List<DataType>,
    val inline: Boolean,
    position: Position
) : PtNamedNode(name, position) {
    override fun printProperties() {
        print(name)
    }
}


class PtAssignment(val augmentable: Boolean,
                   val origin: AssignmentOrigin,        // TODO is this ever used in the codegen?
                   position: Position) : PtNode(position) {
    val target: PtAssignTarget
        get() = children[0] as PtAssignTarget
    val value: PtNode
        get() = children[1]

    override fun printProperties() {
        print("aug=$augmentable  origin=$origin")
    }
}


class PtAssignTarget(position: Position) : PtNode(position) {
    val identifier: PtIdentifier?
        get() = children.single() as? PtIdentifier
    val array: PtArrayIndexer?
        get() = children.single() as? PtArrayIndexer
    val memory: PtMemoryByte?
        get() = children.single() as? PtMemoryByte

    override fun printProperties() {}
}


class PtBuiltinFunctionCall(val name: String, position: Position) : PtNode(position) {
    override fun printProperties() {
        print(name)
    }
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
    val iterable: PtNode
        get() = children[1]
    val statements: PtNodeGroup
        get() = children[2] as PtNodeGroup

    override fun printProperties() {}
}


class PtFunctionCall(val void: Boolean, position: Position) : PtNode(position) {
    val target: PtIdentifier
        get() = children[0] as PtIdentifier
    val args: PtNodeGroup
        get() = children[1] as PtNodeGroup

    override fun printProperties() {
        print("void=$void")
    }
}


class PtGosub(val identifier: PtIdentifier?,
              val address: UInt?,
              val generatedLabel: String?,
              position: Position) : PtNode(position) {
    override fun printProperties() {
        identifier?.printProperties()
        if(address!=null) print(address.toHex())
        if(generatedLabel!=null) print(generatedLabel)
    }
}


class PtIfElse(position: Position) : PtNode(position) {
    val condition: PtNode
        get() = children[0]
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


class PtPipe(position: Position) : PtNode(position) {
    override fun printProperties() {}
}


class PtPostIncrDecr(val operator: String, position: Position) : PtNode(position) {
    val target: PtAssignTarget
        get() = children.single() as PtAssignTarget

    override fun printProperties() {
        print(operator)
    }
}


class PtRepeatLoop(position: Position) : PtNode(position) {
    val count: PtNode
        get() = children.single()

    override fun printProperties() {}
}


class PtReturn(position: Position) : PtNode(position) {
    val hasValue = children.any()
    val value: PtNode?
        get() {
            return if(children.any())
                children.single()
            else
                null
        }

    override fun printProperties() {}
}


class PtVariable(name: String, val type: DataType, position: Position) : PtNamedNode(name, position) {
    override fun printProperties() {
        print("$type  $name")
    }
}


class PtConstant(val name: String, val type: DataType, val value: Double, position: Position) : PtNode(position) {
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
    val value: PtNode
        get() = children[0]
    val choices: PtNodeGroup
        get() = children[1] as PtNodeGroup

    override fun printProperties() {}
}


class PtWhenChoice(val isElse: Boolean, position: Position) : PtNode(position) {
    override fun printProperties() {}
}
