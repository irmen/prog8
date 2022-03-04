package prog8.codegen.experimental6502

import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.toHex


enum class StNodeType {
    GLOBAL,
    // MODULE,     // not used with current scoping rules
    BLOCK,
    SUBROUTINE,
    LABEL,
    VARIABLE,
    MEMVAR,
    CONSTANT,
    BUILTINFUNC
}

open class StNode(val name: String,
                  val type: StNodeType,
                  val position: Position,
                  val children: MutableMap<String, StNode> = mutableMapOf()
) {

    lateinit var parent: StNode

    val scopedName: List<String> by lazy {
        if(type==StNodeType.GLOBAL)
            emptyList()
        else
            parent.scopedName + name
    }

    fun printIndented(indent: Int) {
        print("    ".repeat(indent))
        when(type) {
            StNodeType.GLOBAL -> print("SYMBOL-TABLE:")
            StNodeType.BLOCK -> print("[B] ")
            StNodeType.SUBROUTINE -> print("[S] ")
            StNodeType.LABEL -> print("[L] ")
            StNodeType.VARIABLE -> print("[V] ")
            StNodeType.MEMVAR -> print("[M] ")
            StNodeType.CONSTANT -> print("[C] ")
            StNodeType.BUILTINFUNC -> print("[F] ")
        }
        printProperties()
        println("  pos=$position  sn=$scopedName")
        children.forEach { (_, node) -> node.printIndented(indent+1) }
    }

    open fun printProperties() {
        print("$name  ")
    }
}

class SymbolTable() : StNode("", StNodeType.GLOBAL, Position.DUMMY) {
    fun print() = printIndented(0)

    override fun printProperties() { }
}

class StVariable(name: String, val dt: DataType, position: Position) : StNode(name, StNodeType.VARIABLE, position) {
    override fun printProperties() {
        print("dt=$dt")
    }
}

class StConstant(name: String, val dt: DataType, val value: Double, position: Position) :
    StNode(name, StNodeType.CONSTANT, position) {
    override fun printProperties() {
        print("dt=$dt value=$value")
    }
}

class StMemVar(name: String, val dt: DataType, val address: UInt, position: Position) :
    StNode(name, StNodeType.MEMVAR, position
) {
    override fun printProperties() {
        print("dt=$dt address=${address.toHex()}")
    }
}
