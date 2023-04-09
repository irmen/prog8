package prog8.intermediate

import prog8.code.*
import prog8.code.ast.PtVariable
import prog8.code.core.DataType
import prog8.code.core.ZeropageWish
import prog8.code.core.internedStringsModuleName


// In the Intermediate Representation, all nesting has been removed.
// So the symbol table is just a big flat mapping of (scoped)name to node.

class IRSymbolTable(sourceSt: SymbolTable?) {
    private val table = mutableMapOf<String, StNode>()
    private val asmSymbols = mutableMapOf<String, String>()

    init {
        if(sourceSt!=null) {
            sourceSt.allVariables.forEach {
                add(it)
            }
            sourceSt.allMemMappedVariables.forEach {
                add(it)
            }
            sourceSt.allMemorySlabs.forEach {
                add(it)
            }

            require(table.all { it.key==it.value.name })

            allVariables().forEach {variable ->
                variable.onetimeInitializationArrayValue?.let {
                    it.forEach { arrayElt ->
                        if(arrayElt.addressOfSymbol!=null) {
                            require(arrayElt.addressOfSymbol!!.contains('.')) {
                                "pointer var in array should be properly scoped: ${arrayElt.addressOfSymbol} in ${variable.name}"
                            }
                        }
                    }
                }
            }
        }
    }

    fun allVariables(): Sequence<StStaticVariable> =
        table.asSequence().map { it.value }.filterIsInstance<StStaticVariable>()

    fun allMemMappedVariables(): Sequence<StMemVar> =
        table.asSequence().map { it.value }.filterIsInstance<StMemVar>()

    fun allMemorySlabs(): Sequence<StMemorySlab> =
        table.asSequence().map { it.value }.filterIsInstance<StMemorySlab>()

    fun lookup(name: String) = table[name]

    fun add(variable: StStaticVariable) {
        val scopedName: String
        val varToadd: StStaticVariable
        if('.' in variable.name) {
            scopedName = variable.name
            varToadd = variable
        } else {
            fun fixupAddressOfInArray(array: List<StArrayElement>?): List<StArrayElement>? {
                if(array==null)
                    return null
                val newArray = mutableListOf<StArrayElement>()
                array.forEach {
                    if(it.addressOfSymbol!=null) {
                        val target = variable.lookup(it.addressOfSymbol!!)!!
                        newArray.add(StArrayElement(null, target.scopedName))
                    } else {
                        newArray.add(it)
                    }
                }
                return newArray
            }
            scopedName = variable.scopedName
            val dummyNode = PtVariable(scopedName, variable.dt, variable.zpwish, null, null, variable.astNode.position)
            varToadd = StStaticVariable(scopedName, variable.dt,
                variable.onetimeInitializationNumericValue,
                variable.onetimeInitializationStringValue,
                fixupAddressOfInArray(variable.onetimeInitializationArrayValue),
                variable.length,
                variable.zpwish,
                dummyNode
            )
        }
        table[scopedName] = varToadd
    }


    fun add(variable: StMemVar) {
        val scopedName: String
        val varToadd: StMemVar
        if('.' in variable.name) {
            scopedName = variable.name
            varToadd = variable
        } else {
            scopedName = try {
                variable.scopedName
            } catch (ux: UninitializedPropertyAccessException) {
                variable.name
            }
            varToadd = StMemVar(scopedName, variable.dt, variable.address, variable.length, variable.astNode)
        }
        table[scopedName] = varToadd
    }

    fun add(variable: StMemorySlab) {
        val varToadd = if('.' in variable.name)
            variable
        else {
            val dummyNode = PtVariable(variable.name, DataType.ARRAY_UB, ZeropageWish.NOT_IN_ZEROPAGE, null, null, variable.astNode.position)
            StMemorySlab("prog8_slabs.${variable.name}", variable.size, variable.align, dummyNode)
        }
        table[varToadd.name] = varToadd
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
}
