package prog8.codegen.experimental

import prog8.code.SymbolTable
import prog8.code.core.*

class VariableAllocator(st: SymbolTable, memsizer: IMemSizer) {

    internal val memorySlabs = mutableListOf<MemorySlab>()      // TODO move this to the SymbolTable instead
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
        for (memvar in st.allMemMappedVariables) {
            // TODO virtual machine doesn't have memory mapped variables, so treat them as regular allocated variables for now
            val memsize =
                when (memvar.dt) {
                    in NumericDatatypes -> memsizer.memorySize(memvar.dt)
                    in ArrayDatatypes -> memsizer.memorySize(memvar.dt, memvar.length!!)
                    else -> throw InternalCompilerException("weird dt")
                }

            allocations[memvar.scopedName] = nextLocation
            nextLocation += memsize
        }

        freeMemoryStart = nextLocation
    }

    fun get(name: List<String>) = allocations.getValue(name)

    fun addMemorySlab(name: String, size: Int, align: Int): List<String>? {
        val label = listOf("prog8_memoryslabs", name)
        memorySlabs.add(MemorySlab(name, size, align, label))
        return label
    }
}
