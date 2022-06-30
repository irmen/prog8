package prog8.code

import prog8.code.core.*


/**
 * Tree structure containing all symbol definitions in the program
 * (blocks, subroutines, variables (all types) and labels).
 */
class SymbolTable : StNode("", StNodeType.GLOBAL, Position.DUMMY) {
    fun print() = printIndented(0)

    override fun printProperties() { }

    /**
     * The table as a flat mapping of scoped names to the StNode.
     * This gives the fastest lookup possible (no need to traverse tree nodes)
     */
    val flat: Map<List<String>, StNode> by lazy {
        val result = mutableMapOf<List<String>, StNode>()
        fun flatten(node: StNode) {
            result[node.scopedName] = node
            node.children.values.forEach { flatten(it) }
        }
        children.values.forEach { flatten(it) }
        result
    }

    val allVariables: Collection<StStaticVariable> by lazy {
        val vars = mutableListOf<StStaticVariable>()
        fun collect(node: StNode) {
            for(child in node.children) {
                if(child.value.type== StNodeType.STATICVAR)
                    vars.add(child.value as StStaticVariable)
                else
                    collect(child.value)
            }
        }
        collect(this)
        vars
    }

    val allMemMappedVariables: Collection<StMemVar> by lazy {
        val vars = mutableListOf<StMemVar>()
        fun collect(node: StNode) {
            for(child in node.children) {
                if(child.value.type== StNodeType.MEMVAR)
                    vars.add(child.value as StMemVar)
                else
                    collect(child.value)
            }
        }
        collect(this)
        vars
    }

    override fun lookup(scopedName: List<String>) = flat[scopedName]
}


enum class StNodeType {
    GLOBAL,
    // MODULE,     // not used with current scoping rules
    BLOCK,
    SUBROUTINE,
    ROMSUB,
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
    open fun lookup(scopedName: List<String>) =
        if(scopedName.size>1) lookupQualified(scopedName) else lookupUnqualified(scopedName[0])
    fun lookupOrElse(name: String, default: () -> StNode) =
        lookupUnqualified(name) ?: default()
    fun lookupOrElse(scopedName: List<String>, default: () -> StNode) =
        lookup(scopedName) ?: default()

    private fun lookupQualified(scopedName: List<String>): StNode? {
        // a scoped name refers to a name in another namespace, and always stars from the root.
        var node = this
        while(node.type!= StNodeType.GLOBAL)
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
        while(globalscope.type!= StNodeType.GLOBAL)
            globalscope = globalscope.parent
        val globalNode = globalscope.children[name]
        if(globalNode!=null && globalNode.type== StNodeType.BUILTINFUNC)
            return globalNode

        // search for the unqualified name in the current scope or its parent scopes
        var scope=this
        while(true) {
            val node = scope.children[name]
            if(node!=null)
                return node
            if(scope.type== StNodeType.GLOBAL)
                return null
            else
                scope = scope.parent
        }
    }

    fun printIndented(indent: Int) {
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
            StNodeType.ROMSUB -> print("(R) ")
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
                       val initialNumericValue: Double?,
                       val initialStringValue: StString?,
                       val initialArrayValue: StArray?,
                       val length: Int?,             // for arrays: the number of elements, for strings: number of characters *including* the terminating 0-byte
                       val zpwish: ZeropageWish,
                       position: Position) : StNode(name, StNodeType.STATICVAR, position) {

    init {
        if(length!=null) {
            require(initialNumericValue == null)
            if(initialArrayValue!=null)
                require(length == initialArrayValue.size)
        }
        if(initialNumericValue!=null)
            require(dt in NumericDatatypes)
        if(initialArrayValue!=null)
            require(dt in ArrayDatatypes)
        if(initialStringValue!=null) {
            require(dt == DataType.STR)
            require(length == initialStringValue.first.length+1)
        }
    }

    override fun printProperties() {
        print("$name  dt=$dt  zpw=$zpwish")
    }
}


class StConstant(name: String, val dt: DataType, val value: Double, position: Position) :
    StNode(name, StNodeType.CONSTANT, position) {
    override fun printProperties() {
        print("$name  dt=$dt value=$value")
    }
}


class StMemVar(name: String,
               val dt: DataType,
               val address: UInt,
               val length: Int?,             // for arrays: the number of elements, for strings: number of characters *including* the terminating 0-byte
               position: Position) :
    StNode(name, StNodeType.MEMVAR, position) {
    override fun printProperties() {
        print("$name  dt=$dt address=${address.toHex()}")
    }
}


class StSub(name: String, val parameters: List<StSubroutineParameter>, position: Position) :
        StNode(name, StNodeType.SUBROUTINE, position) {
    override fun printProperties() {
        print(name)
    }
}


class StRomSub(name: String, val address: UInt, parameters: List<StSubroutineParameter>, position: Position) :
    StNode(name, StNodeType.ROMSUB, position) {
    override fun printProperties() {
        print("$name  address=${address.toHex()}")
    }
}


class StSubroutineParameter(val name: String, val type: DataType)
class StArrayElement(val number: Double?, val addressOf: List<String>?)

typealias StString = Pair<String, Encoding>
typealias StArray = List<StArrayElement>
