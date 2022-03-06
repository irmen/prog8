package prog8.compilerinterface.intermediate

import prog8.ast.IBuiltinFunctions
import prog8.ast.base.Position
import prog8.compilerinterface.IMemSizer
import prog8.compilerinterface.IStringEncoding
import prog8.parser.SourceCode

// TODO : once the CodeGen doesn't need the old Ast anymore, get rid of the 'Pt' prefixes.


abstract class PtNode(val position: Position, val children: MutableList<PtNode> = mutableListOf()) {

    lateinit var parent: PtNode

    protected fun printIndented(indent: Int) {
        print("    ".repeat(indent))
        print("${this.javaClass.simpleName}  ")
        printProperties()
        println()
        children.forEach { it.printIndented(indent+1) }
    }

    abstract fun printProperties()

    fun add(child: PtNode) {
        children.add(child)
        child.parent = this
    }

    fun add(index: Int, child: PtNode) {
        children.add(index, child)
        child.parent = this
    }
}


class PtNodeGroup(): PtNode(Position.DUMMY) {
    override fun printProperties() {}
}


class PtProgram(
    val name: String,
    val builtinFunctions: IBuiltinFunctions,
    val memsizer: IMemSizer,
    val encoding: IStringEncoding
) : PtNode(Position.DUMMY) {
    fun print() = printIndented(0)
    override fun printProperties() {
        print("'$name'")
    }
}


class PtModule(
    val name: String,
    val source: SourceCode,
    val loadAddress: UInt,
    val library: Boolean,
    position: Position
) : PtNode(position) {
    override fun printProperties() {
        print("$name  addr=$loadAddress  library=$library")
    }
}


class PtBlock(val name: String,
              val address: UInt?,
              val library: Boolean,
              position: Position) : PtNode(position) {
    override fun printProperties() {
        print("$name  addr=$address  library=$library")
    }
}


class PtDirective(val name: String, position: Position) : PtNode(position) {
    val args: List<PtDirectiveArg>
        get() = children.map { it as PtDirectiveArg }

    override fun printProperties() {
        print(name)
    }
}


class PtDirectiveArg(val str: String?,
                     val name: String?,
                     val int: UInt?,
                     position: Position
): PtNode(position) {
    override fun printProperties() {
        print("str=$str name=$name int=$int")
    }
}


class PtInlineAssembly(val assembly: String, position: Position) : PtNode(position) {
    override fun printProperties() {}
}


class PtLabel(val name: String, position: Position) : PtNode(position) {
    override fun printProperties() {
        print(name)
    }
}

