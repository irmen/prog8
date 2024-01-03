package prog8.vm

import prog8.code.core.*
import prog8.intermediate.IRSymbolTable

internal class VmVariableAllocator(st: IRSymbolTable, val encoding: IStringEncoding, memsizer: IMemSizer) {

    internal val allocations = mutableMapOf<String, Int>()
    private var freeMemoryStart: Int

    val freeMem: Int
        get() = freeMemoryStart


    init {
        var nextLocation = 0
        for (variable in st.allVariables()) {
            val memsize =
                when (variable.dt) {
                    DataType.STR -> variable.onetimeInitializationStringValue!!.first.length + 1  // include the zero byte
                    in NumericDatatypes, DataType.BOOL -> memsizer.memorySize(variable.dt)
                    in ArrayDatatypes -> memsizer.memorySize(variable.dt, variable.length!!)
                    else -> throw InternalCompilerException("weird dt")
                }

            require(memsize>0)
            allocations[variable.name] = nextLocation
            nextLocation += memsize
        }
        for(slab in st.allMemorySlabs()) {
            // we ignore the alignment for the VM.
            allocations[slab.name] = nextLocation
            nextLocation += slab.size.toInt()
        }

        freeMemoryStart = nextLocation
    }
}
