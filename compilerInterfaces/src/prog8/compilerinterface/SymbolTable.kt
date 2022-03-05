package prog8.compilerinterface

import prog8.ast.Node
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.statements.ZeropageWish
import prog8.ast.toHex


/**
 * Tree structure containing all symbol definitions in the program
 * (blocks, subroutines, variables (all types) and labels).
 */
class SymbolTable : StNode("", StNodeType.GLOBAL, Position.DUMMY) {
    fun print() = printIndented(0)

    override fun printProperties() { }

    val origAstLinks = mutableMapOf<Node, StNode>()     // links of the original Ast nodes to the symbol table node.
}


enum class StNodeType {
    GLOBAL,
    // MODULE,     // not used with current scoping rules
    BLOCK,
    SUBROUTINE,
    LABEL,
    STATICVAR,
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
        if(type== StNodeType.GLOBAL)
            emptyList()
        else
            parent.scopedName + name
    }

    fun lookup(name: String) =
        lookupUnqualified(name)
    fun lookup(scopedName: List<String>) =
        if(scopedName.size>1) lookupQualified(scopedName) else lookupUnqualified(scopedName[0])
    fun lookupOrElse(name: String, default: () -> StNode) =
        lookupUnqualified(name) ?: default()
    fun lookupOrElse(scopedName: List<String>, default: () -> StNode) =
        lookup(scopedName) ?: default()

    private fun lookupQualified(scopedName: List<String>): StNode? {
        // a scoped name refers to a name in another namespace, and always stars from the root.
        var node = this
        while(node.type!=StNodeType.GLOBAL)
            node = node.parent

        for(name in scopedName) {
            if(name in node.children)
                node = node.children.getValue(name)
            else
                return null
        }
        return node
    }

    private fun lookupUnqualified(name: String): StNode? {
        // first consider the builtin functions
        var globalscope = this
        while(globalscope.type!=StNodeType.GLOBAL)
            globalscope = globalscope.parent
        val globalNode = globalscope.children[name]
        if(globalNode!=null && globalNode.type==StNodeType.BUILTINFUNC)
            return globalNode

        // search for the unqualified name in the current scope or its parent scopes
        var scope=this
        while(true) {
            val node = scope.children[name]
            if(node!=null)
                return node
            if(scope.type==StNodeType.GLOBAL)
                return null
            else
                scope = scope.parent
        }
    }

    protected fun printIndented(indent: Int) {
        print("    ".repeat(indent))
        when(type) {
            StNodeType.GLOBAL -> print("SYMBOL-TABLE:")
            StNodeType.BLOCK -> print("(B) ")
            StNodeType.SUBROUTINE -> print("(S) ")
            StNodeType.LABEL -> print("(L) ")
            StNodeType.STATICVAR -> print("(V) ")
            StNodeType.MEMVAR -> print("(M) ")
            StNodeType.CONSTANT -> print("(C) ")
            StNodeType.BUILTINFUNC -> print("(F) ")
        }
        printProperties()
        println()
        children.forEach { (_, node) -> node.printIndented(indent+1) }
    }

    open fun printProperties() {
        print("$name  ")
    }

    fun add(child: StNode) {
        children[child.name] = child
        child.parent = this
    }
}

class StStaticVariable(name: String,
                       val dt: DataType,
                       val initialvalue: Expression?,
                       val arraysize: Int?,
                       val zpw: ZeropageWish,
                       position: Position) : StNode(name, StNodeType.STATICVAR, position) {
    override fun printProperties() {
        print("$name  dt=$dt initialval=$initialvalue arraysize=$arraysize zpw=$zpw")
    }
}

class StConstant(name: String, val dt: DataType, val value: Double, position: Position) :
    StNode(name, StNodeType.CONSTANT, position) {
    override fun printProperties() {
        print("$name  dt=$dt value=$value")
    }
}

class StMemVar(name: String, val dt: DataType, val address: UInt, position: Position) :
    StNode(name, StNodeType.MEMVAR, position
) {
    override fun printProperties() {
        print("$name  dt=$dt address=${address.toHex()}")
    }
}
