package prog8.code

import prog8.code.ast.*
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
                result[child.value.scopedNameString] = child.value
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
        // can't be done with a generic function because those don't support local recursive functions yet
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
        // can't be done with a generic function because those don't support local recursive functions yet
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
        // can't be done with a generic function because those don't support local recursive functions yet
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

    val allStructInstances: Collection<StStructInstance> by lazy {
        val vars = mutableListOf<StStructInstance>()
        fun collect(node: StNode) {
            for(child in node.children) {
                if(child.value.type== StNodeType.STRUCTINSTANCE)
                    vars.add(child.value as StStructInstance)
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
            is StMemVar -> node.length?.toInt()
            is StMemorySlab -> node.size.toInt()
            is StStaticVariable -> node.length?.toInt()
            is StStructInstance -> node.size.toInt()
            else -> null
        }
    }

    companion object {
        fun labelnameForStructInstance(call: PtBuiltinFunctionCall): String {
            require(call.name == "structalloc")
            val structname = (call.type.subType as StStruct).scopedNameString
            // each individual call to the pseudo function structalloc(),
            // needs to generate a separate unique struct instance label.
            // (unlike memory() where the label is not unique and passed as the first argument)
            val scopehash = call.parent.hashCode().toUInt().toString(16)
            val pos = "${call.position.line}_${call.position.startCol}"
            val hash = call.position.file.hashCode().toUInt().toString(16)
            return "prog8_struct_${structname.replace('.', '_')}_${hash}_${pos}_${scopehash}"
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
    MEMORYSLAB,
    STRUCT,
    STRUCTINSTANCE
}


open class StNode(val name: String,
                  val type: StNodeType,
                  val astNode: PtNode?,
                  val children: MutableMap<String, StNode> = mutableMapOf()
) {

    lateinit var parent: StNode

    val scopedNameString: String by lazy { scopedNameList.joinToString(".") }

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
        // a scoped name refers to a name in another namespace, and always starts from the root.
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
                       val length: UInt?,            // for arrays: the number of elements, for strings: number of characters *including* the terminating 0-byte
                       val zpwish: ZeropageWish,    // used in the variable allocator
                       val align: UInt,
                       val dirty: Boolean,
                       astNode: PtNode?) : StNode(name, StNodeType.STATICVAR, astNode) {

    var initializationNumericValue: Double? = null
        private set

    fun setOnetimeInitNumeric(number: Double) {
        // In certain cases the init value of an existing var should be updated,
        // so we can't ask this as a constructor parameter.
        // This has to do with the way Prog8 does the (re)initialization of such variables: via code assignment statements.
        // Certain codegens might want to put them back into the variable directly.
        // For strings and arrays this doesn't occur - these are always already specced at creation time.

        require(number!=0.0 || zpwish!=ZeropageWish.NOT_IN_ZEROPAGE) { "non-zp variable should not be initialized with 0, it will already be zeroed as part of BSS clear" }
        initializationNumericValue = number
    }

    val uninitialized: Boolean
        get() = initializationArrayValue==null && initializationStringValue==null && initializationNumericValue==null

    init {
        if(length!=null) {
            require(initializationNumericValue == null)
            if(initializationArrayValue!=null)
                require(initializationArrayValue.isEmpty() || initializationArrayValue.size==length.toInt())
        }
        if(initializationNumericValue!=null) {
            require(dt.isNumericOrBool)
        }
        if(initializationArrayValue!=null) {
            require(dt.isArray)
            require(length?.toInt() == initializationArrayValue.size)
        }
        if(initializationStringValue!=null) {
            require(dt.isString)
            require(length?.toInt() == initializationStringValue.first.length + 1)
        }
        if(align > 0u) {
            require(dt.isString || dt.isArray)
            require(zpwish != ZeropageWish.REQUIRE_ZEROPAGE && zpwish != ZeropageWish.PREFER_ZEROPAGE)
        }
    }
}


class StConstant(name: String, val dt: BaseDataType, val value: Double, astNode: PtNode?) :
    StNode(name, StNodeType.CONSTANT, astNode)


class StMemVar(name: String,
               val dt: DataType,
               val address: UInt,
               val length: UInt?,             // for arrays: the number of elements, for strings: number of characters *including* the terminating 0-byte
               astNode: PtNode?) :
    StNode(name, StNodeType.MEMVAR, astNode) {

    init{
        require(!dt.isString)
        if(dt.isStringly && !dt.isWord)
            requireNotNull(length)
    }
}


class StStruct(
    name: String,
    val fields: List<Pair<DataType, String>>,
    val size: UInt,
    astNode: PtStructDecl?
) : StNode(name, StNodeType.STRUCT, astNode), ISubType {

    fun getField(name: String, sizer: IMemSizer): Pair<DataType, UInt> {
        // returns type and byte offset of the given field
        var offset = 0
        for((dt, definedname) in fields) {
            if(name==definedname)
                return dt to offset.toUInt()
            offset += sizer.memorySize(dt, null)
        }
        throw NoSuchElementException("field $name not found in struct ${this.name}")
    }

    override fun memsize(sizer: IMemSizer): Int = size.toInt()
    override fun sameas(other: ISubType): Boolean = other is StStruct &&
            scopedNameString == other.scopedNameString &&
            fields == other.fields &&
            size == other.size
}


class StMemorySlab(name: String, val size: UInt, val align: UInt, astNode: PtNode?):
    StNode(name, StNodeType.MEMORYSLAB, astNode)


class StStructInstance(name: String, val structName: String, val initialValues: StArray, val size: UInt, astNode: PtNode?) :
    StNode(name, StNodeType.STRUCTINSTANCE, astNode)


class StSub(name: String, val parameters: List<StSubroutineParameter>, val returns: List<DataType>, astNode: PtNode) :
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
