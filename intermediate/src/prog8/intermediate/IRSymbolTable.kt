package prog8.intermediate

import prog8.code.*


// In the Intermediate Representation, all nesting has been removed.
// So the symbol table is just a big flat mapping of (scoped)name to node.

class IRSymbolTable(sourceSt: SymbolTable?) {
    private val table = mutableMapOf<String, StNode>()

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
                        if(arrayElt.addressOf!=null) {
                            require(arrayElt.addressOf!!.size > 1) {
                                "pointer var in array should be properly scoped: ${arrayElt.addressOf!!} in ${variable.name}"
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
                    if(it.addressOf!=null) {
                        val target = variable.lookup(it.addressOf!!)!!
                        newArray.add(StArrayElement(null, target.scopedName))
                    } else {
                        newArray.add(it)
                    }
                }
                return newArray
            }
            scopedName = variable.scopedName.joinToString(".")
            varToadd = StStaticVariable(scopedName, variable.dt,
                variable.onetimeInitializationNumericValue,
                variable.onetimeInitializationStringValue,
                fixupAddressOfInArray(variable.onetimeInitializationArrayValue),
                variable.length,
                variable.zpwish,
                variable.position
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
            scopedName = variable.scopedName.joinToString(".")
            varToadd = StMemVar(scopedName, variable.dt, variable.address, variable.length, variable.position)
        }
        table[scopedName] = varToadd
    }

    fun add(variable: StMemorySlab) {
        val varToadd = if('.' in variable.name)
            variable
        else
            StMemorySlab("prog8_slabs.${variable.name}", variable.size, variable.align, variable.position)
        table[varToadd.name] = varToadd
    }
}
