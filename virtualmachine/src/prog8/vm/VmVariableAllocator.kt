package prog8.vm

import prog8.code.core.DataType
import prog8.code.core.IMemSizer
import prog8.code.core.IStringEncoding
import prog8.code.core.InternalCompilerException
import prog8.intermediate.IRSymbolTable
import prog8.intermediate.IRVariableInitializer

class VmVariableAllocator(st: IRSymbolTable, val encoding: IStringEncoding, memsizer: IMemSizer) {

    val allocations = mutableMapOf<String, UInt>()
    private var freeMemoryStart: UInt

    val freeMem: UInt
        get() = freeMemoryStart


    init {
        var nextLocation = 0u
        for (variable in st.allVariables()) {
            val memsize =
                when {
                    variable.dt.isPointer -> memsizer.memorySize(DataType.UWORD, null)  // a pointer is just a word address
                    variable.dt.isString -> {
                        val strInit = variable.initializationValue as? IRVariableInitializer.Str
                            ?: error("String variable missing initialization value")
                        strInit.text.length + 1  // include the zero byte
                    }
                    variable.dt.isNumericOrBool -> memsizer.memorySize(variable.dt, null)
                    variable.dt.isArray -> memsizer.memorySize(variable.dt, variable.length!!.toInt())
                    variable.dt.isStructInstance -> throw InternalCompilerException("struct instances cannot be directly declared")
                    else -> throw InternalCompilerException("weird dt")
                }

            allocations[variable.name] = nextLocation
            nextLocation += memsize.toUInt()
        }
        for(slab in st.allMemorySlabs()) {
            // we ignore the alignment for the VM.
            allocations[slab.name] = nextLocation
            nextLocation += slab.size
        }
        for(struct in st.allStructInstances()) {
            allocations[struct.name] = nextLocation
            nextLocation += struct.size
        }

        freeMemoryStart = nextLocation
    }
}
