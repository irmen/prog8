package prog8.intermediate

import prog8.code.*
import prog8.code.core.*


// In the Intermediate Representation, all nesting has been removed.
// So the symbol table is just a big flat mapping of (scoped)name to node.
// We define a stripped down symbol table for use in the IR phase only, rather than reuse the codegen symboltable

class IRSymbolTable {
    private val table = mutableMapOf<String, IRStNode>()
    private val asmSymbols = mutableMapOf<String, String>()

    companion object {
        fun fromAstSymboltable(sourceSt: SymbolTable?): IRSymbolTable {
            val st = IRSymbolTable()
            if (sourceSt != null) {
                sourceSt.flat.forEach {
                    when(it.value.type) {
                        StNodeType.STATICVAR -> st.add(it.value as StStaticVariable)
                        StNodeType.MEMVAR -> st.add(it.value as StMemVar)
                        StNodeType.CONSTANT -> st.add(it.value as StConstant)
                        StNodeType.MEMORYSLAB -> st.add(it.value as StMemorySlab)
                        else -> { }
                    }
                }

                require(st.table.all { it.key == it.value.name })

                st.allVariables().forEach { variable ->
                    variable.onetimeInitializationArrayValue?.let {
                        it.forEach { arrayElt ->
                            if (arrayElt.addressOfSymbol != null) {
                                require(arrayElt.addressOfSymbol.contains('.')) {
                                    "pointer var in array should be properly scoped: ${arrayElt.addressOfSymbol} in ${variable.name}"
                                }
                            }
                        }
                    }
                }
            }
            return st
        }
    }

    fun allConstants(): Sequence<IRStConstant> =
        table.asSequence().map { it.value }.filterIsInstance<IRStConstant>()

    fun allVariables(): Sequence<IRStStaticVariable> =
        table.asSequence().map { it.value }.filterIsInstance<IRStStaticVariable>()

    fun allMemMappedVariables(): Sequence<IRStMemVar> =
        table.asSequence().map { it.value }.filterIsInstance<IRStMemVar>()

    fun allMemorySlabs(): Sequence<IRStMemorySlab> =
        table.asSequence().map { it.value }.filterIsInstance<IRStMemorySlab>()

    fun lookup(name: String) = table[name]

    fun add(variable: StStaticVariable) {
        val scopedName: String
        val varToadd: IRStStaticVariable
        if('.' in variable.name) {
            scopedName = variable.name
            varToadd = IRStStaticVariable(variable.name,
                variable.dt,
                variable.initializationNumericValue,
                variable.initializationStringValue,
                variable.initializationArrayValue?.map { convertArrayElt(it) },
                variable.length,
                variable.zpwish,
                variable.align)
        } else {
            fun fixupAddressOfInArray(array: List<StArrayElement>?): List<IRStArrayElement>? {
                if(array==null)
                    return null
                val newArray = mutableListOf<IRStArrayElement>()
                array.forEach {
                    if(it.addressOfSymbol!=null) {
                        val target = variable.lookup(it.addressOfSymbol!!) ?: throw NoSuchElementException("can't find variable ${it.addressOfSymbol}")
                        newArray.add(IRStArrayElement(null, null, target.scopedName))
                    } else {
                        newArray.add(convertArrayElt(it))
                    }
                }
                return newArray
            }
            scopedName = variable.scopedName
            varToadd = IRStStaticVariable(scopedName,
                variable.dt,
                variable.initializationNumericValue,
                variable.initializationStringValue,
                fixupAddressOfInArray(variable.initializationArrayValue),
                variable.length,
                variable.zpwish,
                variable.align
            )
        }
        table[scopedName] = varToadd
    }

    fun add(variable: StMemVar) {
        val scopedName: String
        val varToadd: IRStMemVar
        if('.' in variable.name) {
            scopedName = variable.name
            varToadd = IRStMemVar(
                variable.name,
                variable.dt,
                variable.address,
                variable.length
            )
        } else {
            scopedName = try {
                variable.scopedName
            } catch (_: UninitializedPropertyAccessException) {
                variable.name
            }
            varToadd = IRStMemVar(scopedName, variable.dt, variable.address, variable.length)
        }
        table[scopedName] = varToadd
    }

    fun add(variable: StMemorySlab) {
        val varToadd = if('.' in variable.name)
            IRStMemorySlab(variable.name, variable.size, variable.align)
        else {
            IRStMemorySlab("prog8_slabs.${variable.name}", variable.size, variable.align)
        }
        table[varToadd.name] = varToadd
    }

    fun add(constant: StConstant) {
        val scopedName: String
        val dt = DataType.forDt(constant.dt)
        if('.' in constant.name) {
            scopedName = constant.name
        } else {
            scopedName = try {
                constant.scopedName
            } catch (_: UninitializedPropertyAccessException) {
                constant.name
            }
        }
        table[scopedName] = IRStConstant(scopedName, dt, constant.value)
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
            if(!it.key.startsWith(internedStringsModuleName)) {
                table.remove(it.key)
            }
        }
    }


    private fun convertArrayElt(elt: StArrayElement): IRStArrayElement = if(elt.boolean!=null)
        IRStArrayElement(elt.boolean, null, elt.addressOfSymbol)
    else
        IRStArrayElement(null, elt.number, elt.addressOfSymbol)
}


enum class IRStNodeType {
    STATICVAR,
    MEMVAR,
    MEMORYSLAB,
    CONST
}

open class IRStNode(val name: String,
                  val type: IRStNodeType,
                  val children: MutableMap<String, StNode> = mutableMapOf()
)

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
                       val onetimeInitializationNumericValue: Double?,      // regular (every-run-time) initialization is done via regular assignments
                       val onetimeInitializationStringValue: IRStString?,
                       val onetimeInitializationArrayValue: IRStArray?,
                       val length: Int?,            // for arrays: the number of elements, for strings: number of characters *including* the terminating 0-byte
                       val zpwish: ZeropageWish,    // used in the variable allocator
                       val align: Int
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
