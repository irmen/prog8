package prog8.code

import prog8.code.ast.*
import prog8.code.core.*


/**
 * Tree structure containing all symbol definitions in the program
 * (blocks, subroutines, variables (all types), memoryslabs, and labels).
 */
class SymbolTable(
    astProgram: PtProgram,
    private val disableCache: Boolean = false  // Disable caching for -noopt debugging
) : StNode(astProgram.name, StNodeType.GLOBAL, astProgram) {

    private var cachedFlat: Map<String, StNode>? = null

    val flat: Map<String, StNode> get() {
        fun buildFlatMap(): Map<String, StNode> {
            val result = mutableMapOf<String, StNode>()
            fun collect(node: StNode) {
                for(child in node.children) {
                    result[child.value.scopedNameString] = child.value
                    collect(child.value)
                }
            }
            collect(this)
            return result
        }

        if(disableCache)
            return buildFlatMap()
        if(cachedFlat!=null)
            return cachedFlat!!

        cachedFlat = buildFlatMap()
        return cachedFlat!!
    }

    fun resetCachedFlat() {
        if(!disableCache) cachedFlat = null
    }

    private fun <T : StNode> collectAll(node: StNode, clazz: Class<T>): List<T> {
        val result = mutableListOf<T>()
        fun collect(n: StNode) {
            for(child in n.children) {
                if(clazz.isInstance(child.value)) result.add(clazz.cast(child.value))
                else collect(child.value)
            }
        }
        collect(node)
        return result
    }

    val allVariables: Collection<StStaticVariable> by lazy { collectAll(this, StStaticVariable::class.java) }

    val allMemMappedVariables: Collection<StMemVar> by lazy { collectAll(this, StMemVar::class.java) }

    val allMemorySlabs: Collection<StMemorySlab> by lazy { collectAll(this, StMemorySlab::class.java) }

    fun allStructInstances(): Collection<StStructInstance> = collectAll(this, StStructInstance::class.java)

    fun allStructTypes(): Collection<StStruct> = collectAll(this, StStruct::class.java)

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
        fun labelnameForStructInstance(call: PtFunctionCall): String {
            require(call.name == "prog8_lib_structalloc")
            var structname = call.type.subType!!.scopedNameString
            val parts = structname.split('.')
            val prefixed = parts.all { it.length>5 && it.startsWith("p8") && it[3]=='_' }
            if(prefixed) {
                structname = parts.joinToString(".") { it.substring(4) }
            }
            val scopehash = call.parent.hashCode().toUInt().toString(16)
            val pos = "${call.position.line}_${call.position.startCol}"
            val hash = call.position.file.hashCode().toUInt().toString(16)
            return "${structname.replace('.', '_')}_${hash}_${scopehash}_${pos}"
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
        lookupScoped(scopedName.split('.'))

    fun lookupUnscopedOrElse(name: String, default: () -> StNode) =
        lookupUnscoped(name) ?: default()

    fun lookupOrElse(scopedName: String, default: () -> StNode): StNode =
        lookupScoped(scopedName.split('.')) ?: default()

    fun lookupUnscoped(name: String): StNode? {
        var globalscope = this
        while(globalscope.type!= StNodeType.GLOBAL)
            globalscope = globalscope.parent
        val globalNode = globalscope.children[name]
        if(globalNode!=null && globalNode.type== StNodeType.BUILTINFUNC)
            return globalNode

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
        if(child.name !in children) {
            children[child.name] = child
            child.parent = this
        }
    }

    private val scopedNameList: List<String> by lazy {
        if(type==StNodeType.GLOBAL)
            emptyList()
        else
            parent.scopedNameList + name
    }

    private fun lookupScoped(scopedName: List<String>): StNode? {
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


class StConstant(name: String, val dt: DataType, val value: Double?, val memorySlab: StMemorySlab?, astNode: PtNode?) :
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
    val logicalScopedNameString: String,  // The scoped name without symbol prefixes (e.g., "plane.Point" instead of "p8b_plane.p8t_Point") - used for equality checks
    astNode: PtStructDecl?
) : StNode(name, StNodeType.STRUCT, astNode), ISubType {

    fun getField(name: String, sizer: IMemSizer): Pair<DataType, UByte> {
        // returns type and byte offset of the given field
        var offset = 0
        for((dt, definedname) in fields) {
            if(name==definedname) {
                require(offset<=255)
                return dt to offset.toUByte()
            }
            offset += sizer.memorySize(dt, null)
        }
        throw NoSuchElementException("field $name not found in struct ${this.name}")
    }

    override fun getFieldType(name: String): DataType? = fields.firstOrNull { it.second == name }?.first

    override fun memsize(sizer: IMemSizer): Int = size.toInt()
    override fun sameas(other: ISubType): Boolean {
        if(other is StStruct) {
            return logicalScopedNameString == other.logicalScopedNameString &&
                   fields == other.fields
        }
        return false
    }
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

sealed class StArrayElement {
    data class Number(val value: Double) : StArrayElement()
    data class AddressOf(val symbol: String) : StArrayElement()
    data class StructInstance(val name: String, val uninitialized: Boolean = false) : StArrayElement()
    data class BoolValue(val value: Boolean) : StArrayElement()
    data class MemorySlab(val name: String) : StArrayElement()
}

typealias StString = Pair<String, Encoding>
typealias StArray = List<StArrayElement>

const val StMemorySlabBlockName = "prog8_slabs"
const val StStructInstanceBlockName = "prog8_struct_instances"
