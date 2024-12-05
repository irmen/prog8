package prog8.code

import prog8.code.ast.PtAsmSub
import prog8.code.ast.PtNode
import prog8.code.ast.PtProgram
import prog8.code.core.*


/**
 * Tree structure containing all symbol definitions in the program
 * (blocks, subroutines, variables (all types), memoryslabs, and labels).
 */
class SymbolTable(astProgram: PtProgram) : StNode(astProgram.name, StNodeType.GLOBAL, astProgram) {
    /**
     * The table as a flat mapping of scoped names to the StNode.
     * This gives the fastest lookup possible (no need to traverse tree nodes)
     */

    private var cachedFlat: Map<String, StNode>? = null

    val flat: Map<String, StNode> get()  {
        if(cachedFlat!=null)
            return cachedFlat!!

        val result = mutableMapOf<String, StNode>()
        fun collect(node: StNode) {
            for(child in node.children) {
                result[child.value.scopedName] = child.value
                collect(child.value)
            }
        }
        collect(this)
        cachedFlat = result
        return result
    }

    fun resetCachedFlat() {
        cachedFlat = null
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
        val vars = mutableListOf<StMemorySlab>()
        fun collect(node: StNode) {
            for(child in node.children) {
                if(child.value.type== StNodeType.MEMORYSLAB)
                    vars.add(child.value as StMemorySlab)
                else
                    collect(child.value)
            }
        }
        collect(this)
        vars
    }

    override fun lookup(scopedName: String) = flat[scopedName]

    fun getLength(name: String): Int? {
        return when(val node = flat[name]) {
            is StMemVar -> node.length
            is StMemorySlab -> node.size.toInt()
            is StStaticVariable -> node.length
            else -> null
        }
    }
}


enum class StNodeType {
    GLOBAL,
    // MODULE,     // not used with current scoping rules
    BLOCK,
    SUBROUTINE,
    EXTSUB,
    LABEL,
    STATICVAR,
    MEMVAR,
    CONSTANT,
    BUILTINFUNC,
    MEMORYSLAB
}


open class StNode(val name: String,
                  val type: StNodeType,
                  val astNode: PtNode,
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
        if(type==StNodeType.GLOBAL)
            emptyList()
        else
            parent.scopedNameList + name
    }

    private fun lookup(scopedName: List<String>): StNode? {
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
}

class StStaticVariable(name: String,
                       val dt: DataType,
                       val initializationStringValue: StString?,
                       val initializationArrayValue: StArray?,
                       val length: Int?,            // for arrays: the number of elements, for strings: number of characters *including* the terminating 0-byte
                       val zpwish: ZeropageWish,    // used in the variable allocator
                       val align: Int,
                       astNode: PtNode) : StNode(name, StNodeType.STATICVAR, astNode) {

    var initializationNumericValue: Double? = null
        private set

    fun setOnetimeInitNumeric(number: Double) {
        // In certain cases the init value of an existing var should be updated,
        // so we can't ask this as a constructor parameter.
        // This has to do with the way Prog8 does the (re)initialization of such variables: via code assignment statements.
        // Certain codegens might want to put them back into the variable directly.
        // For strings and arrays this doesn't occur - these are always already specced at creation time.
        initializationNumericValue = number
    }

    val uninitialized: Boolean
        get() = initializationArrayValue==null && initializationStringValue==null && initializationNumericValue==null

    init {
        if(length!=null) {
            require(initializationNumericValue == null)
            if(initializationArrayValue!=null)
                require(initializationArrayValue.isEmpty() ||initializationArrayValue.size==length)
        }
        if(initializationNumericValue!=null) {
            require(dt.isNumericOrBool)
        }
        if(initializationArrayValue!=null) {
            require(dt.isArray)
            require(length == initializationArrayValue.size)
        }
        if(initializationStringValue!=null) {
            require(dt.isString)
            require(length == initializationStringValue.first.length + 1)
        }
        if(align > 0) {
            require(dt.isString || dt.isArray)
            require(zpwish != ZeropageWish.REQUIRE_ZEROPAGE && zpwish != ZeropageWish.PREFER_ZEROPAGE)
        }
    }
}


class StConstant(name: String, val dt: BaseDataType, val value: Double, astNode: PtNode) :
    StNode(name, StNodeType.CONSTANT, astNode)


class StMemVar(name: String,
               val dt: DataType,
               val address: UInt,
               val length: Int?,             // for arrays: the number of elements, for strings: number of characters *including* the terminating 0-byte
               astNode: PtNode) :
    StNode(name, StNodeType.MEMVAR, astNode) {

    init{
        require(!dt.isBool && !dt.isBoolArray)
        if(dt.isStringly && !dt.isWord)
            requireNotNull(length)
    }
}

class StMemorySlab(
    name: String,
    val size: UInt,
    val align: UInt,
    astNode: PtNode
):
    StNode(name, StNodeType.MEMORYSLAB, astNode)


class StSub(name: String, val parameters: List<StSubroutineParameter>, val returnType: DataType?, astNode: PtNode) :
        StNode(name, StNodeType.SUBROUTINE, astNode)


class StExtSub(name: String,
               val address: PtAsmSub.Address?,      // null in case of asmsub, specified in case of extsub.
               val parameters: List<StExtSubParameter>,
               val returns: List<StExtSubParameter>,
               astNode: PtNode) :
    StNode(name, StNodeType.EXTSUB, astNode)



class StSubroutineParameter(val name: String, val type: DataType, val register: RegisterOrPair?)
class StExtSubParameter(val register: RegisterOrStatusflag, val type: DataType)
class StArrayElement(val number: Double?, val addressOfSymbol: String?, val boolean: Boolean?) {
    init {
        if(number!=null) require(addressOfSymbol==null && boolean==null)
        if(addressOfSymbol!=null) require(number==null && boolean==null)
        if(boolean!=null) require(addressOfSymbol==null && number==null)
    }
}

typealias StString = Pair<String, Encoding>
typealias StArray = List<StArrayElement>
