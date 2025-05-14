package prog8.vm

import prog8.code.core.DataType
import prog8.code.core.IMemSizer
import prog8.code.core.IStringEncoding
import prog8.code.core.InternalCompilerException
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
                when {
                    variable.dt.isPointer -> memsizer.memorySize(DataType.UWORD, null)  // a pointer is just a word address
                    variable.dt.isString -> variable.onetimeInitializationStringValue!!.first.length + 1  // include the zero byte
                    variable.dt.isNumericOrBool -> memsizer.memorySize(variable.dt, null)
                    variable.dt.isArray -> memsizer.memorySize(variable.dt, variable.length!!.toInt())
                    variable.dt.isStructInstance -> throw InternalCompilerException("struct instances cannot be directly declared")
                    else -> throw InternalCompilerException("weird dt")
                }

            allocations[variable.name] = nextLocation
            nextLocation += memsize
        }
        for(slab in st.allMemorySlabs()) {
            // we ignore the alignment for the VM.
            allocations[slab.name] = nextLocation
            nextLocation += slab.size.toInt()
        }
        for(struct in st.allStructInstances()) {
            allocations[struct.name] = nextLocation
            nextLocation += struct.size.toInt()
        }

        freeMemoryStart = nextLocation
    }
}
