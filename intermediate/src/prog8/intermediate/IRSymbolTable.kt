package prog8.intermediate

import prog8.code.INTERNED_STRINGS_MODULENAME
import prog8.code.core.DataType
import prog8.code.core.Encoding
import prog8.code.core.ZeropageWish


// In the Intermediate Representation, all nesting has been removed.
// So the symbol table is just a big flat mapping of (scoped)name to node.
// We define a stripped down symbol table for use in the IR phase only, rather than reuse the codegen symboltable

class IRSymbolTable {
    private val table = mutableMapOf<String, IRStNode>()
    private val asmSymbols = mutableMapOf<String, String>()

    fun allConstants(): Sequence<IRStConstant> =
        table.asSequence().map { it.value }.filterIsInstance<IRStConstant>()

    fun allVariables(): Sequence<IRStStaticVariable> =
        table.asSequence().map { it.value }.filterIsInstance<IRStStaticVariable>()

    fun allMemMappedVariables(): Sequence<IRStMemVar> =
        table.asSequence().map { it.value }.filterIsInstance<IRStMemVar>()

    fun allMemorySlabs(): Sequence<IRStMemorySlab> =
        table.asSequence().map { it.value }.filterIsInstance<IRStMemorySlab>()

    fun lookup(name: String) = table[name]

    fun add(node: IRStNode) {
        table[node.name] = node
    }

    fun addAsmSymbol(name: String, value: String) {
        asmSymbols[name] = value
    }

    fun getAsmSymbols(): Map<String, String> = asmSymbols

    fun removeTree(label: String) {
        val prefix = "$label."
        val vars = table.filter { it.key.startsWith(prefix) }
        vars.forEach {
            // check if attempt is made to delete interned strings, if so, refuse that.
            if(!it.key.startsWith(INTERNED_STRINGS_MODULENAME)) {
                table.remove(it.key)
            }
        }
    }

    fun validate() {
        require(table.all { it.key == it.value.name })
    }

    fun removeIfExists(labelSymbol: String) {
        table.remove(labelSymbol)
    }
}


enum class IRStNodeType {
    STATICVAR,
    MEMVAR,
    MEMORYSLAB,
    CONST
}

open class IRStNode(val name: String, val type: IRStNodeType)

class IRStMemVar(name: String,
                 val dt: DataType,
                 val address: UInt,
                 val length: Int?             // for arrays: the number of elements, for strings: number of characters *including* the terminating 0-byte
               ) :  IRStNode(name, IRStNodeType.MEMVAR) {
    init {
        require(!dt.isString)
    }

    val typeString: String = dt.irTypeString(length)
}

class IRStMemorySlab(
    name: String,
    val size: UInt,
    val align: UInt
):  IRStNode(name, IRStNodeType.MEMORYSLAB)


class IRStConstant(name: String, val dt: DataType, val value: Double) : IRStNode(name, IRStNodeType.CONST) {
    val typeString: String = dt.irTypeString(null)
}


class IRStStaticVariable(name: String,
                       val dt: DataType,
                       val onetimeInitializationNumericValue: Double?,      // TODO still needed? Or can go?   regular (every-run-time) initialization is done via regular assignments
                       val onetimeInitializationStringValue: IRStString?,
                       val onetimeInitializationArrayValue: IRStArray?,
                       val length: Int?,            // for arrays: the number of elements, for strings: number of characters *including* the terminating 0-byte
                       val zpwish: ZeropageWish,    // used in the variable allocator
                       val align: Int,
                       val dirty: Boolean
) : IRStNode(name, IRStNodeType.STATICVAR) {
    init {
        if(align > 0) {
            require(dt.isString || dt.isArray)
            require(zpwish != ZeropageWish.REQUIRE_ZEROPAGE && zpwish != ZeropageWish.PREFER_ZEROPAGE)
        }
    }

    val uninitialized = onetimeInitializationArrayValue==null && onetimeInitializationStringValue==null && onetimeInitializationNumericValue==null

    val typeString: String = dt.irTypeString(length)
}

class IRStArrayElement(val bool: Boolean?, val number: Double?, val addressOfSymbol: String?) {
    init {
        if(bool!=null) require(number==null && addressOfSymbol==null)
        if(number!=null) require(bool==null && addressOfSymbol==null)
        if(addressOfSymbol!=null) require(number==null || bool==null)
    }
}

typealias IRStArray = List<IRStArrayElement>
typealias IRStString = Pair<String, Encoding>
