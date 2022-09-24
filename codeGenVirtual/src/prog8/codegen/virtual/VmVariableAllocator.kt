package prog8.codegen.virtual

import prog8.code.SymbolTable
import prog8.code.core.*
import prog8.intermediate.getTypeString

internal class VmVariableAllocator(val st: SymbolTable, val encoding: IStringEncoding, memsizer: IMemSizer) {

    internal val allocations = mutableMapOf<List<String>, Int>()
    private var freeMemoryStart: Int

    val freeMem: Int
        get() = freeMemoryStart


    init {
        var nextLocation = 0
        for (variable in st.allVariables) {
            val memsize =
                when (variable.dt) {
                    DataType.STR -> variable.onetimeInitializationStringValue!!.first.length + 1  // include the zero byte
                    in NumericDatatypes -> memsizer.memorySize(variable.dt)
                    in ArrayDatatypes -> memsizer.memorySize(variable.dt, variable.length!!)
                    else -> throw InternalCompilerException("weird dt")
                }

            allocations[variable.scopedName] = nextLocation
            nextLocation += memsize
        }
        for(slab in st.allMemorySlabs) {
            // we ignore the alignment for the VM.
            allocations[slab.scopedName] = nextLocation
            nextLocation += slab.size.toInt()
        }

        freeMemoryStart = nextLocation
    }

    fun asVmMemory(): List<Pair<List<String>, String>> {
        val mm = mutableListOf<Pair<List<String>, String>>()

        // normal variables
        for (variable in st.allVariables) {
            val location = allocations.getValue(variable.scopedName)
            val value = when(variable.dt) {
                DataType.FLOAT -> (variable.onetimeInitializationNumericValue ?: 0.0).toString()
                in NumericDatatypes -> (variable.onetimeInitializationNumericValue ?: 0).toHex()
                DataType.STR -> {
                    val encoded = encoding.encodeString(variable.onetimeInitializationStringValue!!.first, variable.onetimeInitializationStringValue!!.second) + listOf(0u)
                    encoded.joinToString(",") { it.toInt().toHex() }
                }
                DataType.ARRAY_F -> {
                    if(variable.onetimeInitializationArrayValue!=null) {
                        variable.onetimeInitializationArrayValue!!.joinToString(",") { it.number!!.toString() }
                    } else {
                        (1..variable.length!!).joinToString(",") { "0" }
                    }
                }
                in ArrayDatatypes -> {
                    if(variable.onetimeInitializationArrayValue!==null) {
                        variable.onetimeInitializationArrayValue!!.joinToString(",") {
                            if(it.number!=null)
                                it.number!!.toHex()
                            else
                                "&${it.addressOf!!.joinToString(".")}"
                        }
                    } else {
                        (1..variable.length!!).joinToString(",") { "0" }
                    }
                }
                else -> throw InternalCompilerException("weird dt")
            }
            mm.add(Pair(variable.scopedName, "@$location ${getTypeString(variable)} $value"))
        }

        // memory mapped variables
        for (variable in st.allMemMappedVariables) {
            val value = when(variable.dt) {
                DataType.FLOAT -> "0.0"
                in NumericDatatypes -> "0"
                DataType.ARRAY_F -> (1..variable.length!!).joinToString(",") { "0.0" }
                in ArrayDatatypes -> (1..variable.length!!).joinToString(",") { "0" }
                else -> throw InternalCompilerException("weird dt for mem mapped var")
            }
            mm.add(Pair(variable.scopedName, "@${variable.address} ${getTypeString(variable)} $value"))
        }

        // memory slabs.
        for(slab in st.allMemorySlabs) {
            val address = allocations.getValue(slab.scopedName)
            mm.add(Pair(slab.scopedName, "@$address ubyte[${slab.size}] 0"))
        }

        return mm
    }

}
