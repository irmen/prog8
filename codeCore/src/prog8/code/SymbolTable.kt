package prog8.code

import prog8.code.core.*


/**
 * Tree structure containing all symbol definitions in the program
 * (blocks, subroutines, variables (all types), memoryslabs, and labels).
 */
class SymbolTable : StNode("", StNodeType.GLOBAL, Position.DUMMY) {
    /**
     * The table as a flat mapping of scoped names to the StNode.
     * This gives the fastest lookup possible (no need to traverse tree nodes)
     */

    val flat: Map<String, StNode> by lazy {
        val result = mutableMapOf<String, StNode>()
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

    val allMemorySlabs: Collection<StMemorySlab> by lazy {
        children.mapNotNull { if (it.value.type == StNodeType.MEMORYSLAB) it.value as StMemorySlab else null }
    }

    override fun lookup(scopedName: String) = flat[scopedName]
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
    BUILTINFUNC,
    MEMORYSLAB
}


open class StNode(val name: String,
                  val type: StNodeType,
                  val position: Position,
                  val children: MutableMap<String, StNode> = mutableMapOf()
) {

    lateinit var parent: StNode

    val scopedName: String by lazy { scopedNameList.joinToString(".") }

    open fun lookup(scopedName: String) =
        lookup(scopedName.split('.'))

    fun lookupUnscopedOrElse(name: String, default: () -> StNode) =
        lookupUnscoped(name) ?: default()

    fun lookupOrElse(scopedName: String, default: () -> StNode): StNode =
        lookup(scopedName.split('.')) ?: default()

    fun lookupUnscoped(name: String): StNode? {
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

    fun add(child: StNode) {
        children[child.name] = child
        child.parent = this
    }

    private val scopedNameList: List<String> by lazy {
        if(type== StNodeType.GLOBAL)
            emptyList()
        else
            parent.scopedNameList + name
    }

    private fun lookup(scopedName: List<String>): StNode? {
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
}

class StStaticVariable(name: String,
                       val dt: DataType,
                       val bss: Boolean,
                       val onetimeInitializationNumericValue: Double?,      // regular (every-run-time) initialization is done via regular assignments
                       val onetimeInitializationStringValue: StString?,
                       val onetimeInitializationArrayValue: StArray?,
                       val length: Int?,             // for arrays: the number of elements, for strings: number of characters *including* the terminating 0-byte
                       val zpwish: ZeropageWish,
                       position: Position) : StNode(name, StNodeType.STATICVAR, position) {

    init {
        if(bss) {
            require(onetimeInitializationNumericValue==null)
            require(onetimeInitializationStringValue==null)
            require(onetimeInitializationArrayValue.isNullOrEmpty())
        } else {
            require(onetimeInitializationNumericValue!=null ||
                    onetimeInitializationStringValue!=null ||
                    onetimeInitializationArrayValue!=null)
        }
        if(length!=null) {
            require(onetimeInitializationNumericValue == null)
            if(onetimeInitializationArrayValue!=null)
                require(onetimeInitializationArrayValue.isEmpty() ||onetimeInitializationArrayValue.size==length)
        }
        if(onetimeInitializationNumericValue!=null)
            require(dt in NumericDatatypes)
        if(onetimeInitializationArrayValue!=null)
            require(dt in ArrayDatatypes)
        if(onetimeInitializationStringValue!=null) {
            require(dt == DataType.STR)
            require(length == onetimeInitializationStringValue.first.length+1)
        }
    }
}


class StConstant(name: String, val dt: DataType, val value: Double, position: Position) :
    StNode(name, StNodeType.CONSTANT, position) {
}


class StMemVar(name: String,
               val dt: DataType,
               val address: UInt,
               val length: Int?,             // for arrays: the number of elements, for strings: number of characters *including* the terminating 0-byte
               position: Position) :
    StNode(name, StNodeType.MEMVAR, position) {
}

class StMemorySlab(
    name: String,
    val size: UInt,
    val align: UInt,
    position: Position
):
    StNode(name, StNodeType.MEMORYSLAB, position) {
}

class StSub(name: String, val parameters: List<StSubroutineParameter>, val returnType: DataType?, position: Position) :
        StNode(name, StNodeType.SUBROUTINE, position) {
}


class StRomSub(name: String,
               val address: UInt,
               val parameters: List<StRomSubParameter>,
               val returns: List<RegisterOrStatusflag>,
               position: Position) :
    StNode(name, StNodeType.ROMSUB, position) {
}


class StSubroutineParameter(val name: String, val type: DataType)
class StRomSubParameter(val register: RegisterOrStatusflag, val type: DataType)
class StArrayElement(val number: Double?, val addressOfSymbol: String?)

typealias StString = Pair<String, Encoding>
typealias StArray = List<StArrayElement>
