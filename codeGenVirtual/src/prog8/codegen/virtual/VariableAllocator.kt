package prog8.codegen.virtual

import prog8.code.StMemorySlab
import prog8.code.StNodeType
import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.*

class VariableAllocator(private val st: SymbolTable, private val program: PtProgram) {

    private val allocations = mutableMapOf<List<String>, Int>()
    private var freeMemoryStart: Int

    val freeMem: Int
        get() = freeMemoryStart

    init {
        var nextLocation = 0
        for (variable in st.allVariables) {
            val memsize =
                when (variable.dt) {
                    DataType.STR -> variable.onetimeInitializationStringValue!!.first.length + 1  // include the zero byte
                    in NumericDatatypes -> program.memsizer.memorySize(variable.dt)
                    in ArrayDatatypes -> program.memsizer.memorySize(variable.dt, variable.length!!)
                    else -> throw InternalCompilerException("weird dt")
                }

            allocations[variable.scopedName] = nextLocation
            nextLocation += memsize
        }
        for (memvar in st.allMemMappedVariables) {
            // TODO virtual machine doesn't have memory mapped variables, so treat them as regular allocated variables for now
            val memsize =
                when (memvar.dt) {
                    in NumericDatatypes -> program.memsizer.memorySize(memvar.dt)
                    in ArrayDatatypes -> program.memsizer.memorySize(memvar.dt, memvar.length!!)
                    else -> throw InternalCompilerException("weird dt")
                }

            allocations[memvar.scopedName] = nextLocation
            nextLocation += memsize
        }

        freeMemoryStart = nextLocation
    }

    fun get(name: List<String>) = allocations.getValue(name)

    fun asVmMemory(): List<Pair<List<String>, String>> {
        val mm = mutableListOf<Pair<List<String>, String>>()
        for (variable in st.allVariables) {
            val location = allocations.getValue(variable.scopedName)
            val typeStr = when(variable.dt) {
                DataType.UBYTE, DataType.ARRAY_UB, DataType.STR -> "ubyte"
                DataType.BYTE, DataType.ARRAY_B -> "byte"
                DataType.UWORD, DataType.ARRAY_UW -> "uword"
                DataType.WORD, DataType.ARRAY_W -> "word"
                DataType.FLOAT, DataType.ARRAY_F -> "float"
                else -> throw InternalCompilerException("weird dt")
            }
            val value = when(variable.dt) {
                DataType.FLOAT -> (variable.onetimeInitializationNumericValue ?: 0.0).toString()
                in NumericDatatypes -> (variable.onetimeInitializationNumericValue ?: 0).toHex()
                DataType.STR -> {
                    val encoded = program.encoding.encodeString(variable.onetimeInitializationStringValue!!.first, variable.onetimeInitializationStringValue!!.second) + listOf(0u)
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
                        variable.onetimeInitializationArrayValue!!.joinToString(",") { it.number!!.toHex() }
                    } else {
                        (1..variable.length!!).joinToString(",") { "0" }
                    }
                }
                else -> throw InternalCompilerException("weird dt")
            }
            mm.add(Pair(variable.scopedName, "$location $typeStr $value"))
        }
        for (variable in st.allMemMappedVariables) {
            val location = allocations.getValue(variable.scopedName)
            val typeStr = when(variable.dt) {
                DataType.UBYTE, DataType.ARRAY_UB, DataType.STR -> "ubyte"
                DataType.BYTE, DataType.ARRAY_B -> "byte"
                DataType.UWORD, DataType.ARRAY_UW -> "uword"
                DataType.WORD, DataType.ARRAY_W -> "word"
                DataType.FLOAT, DataType.ARRAY_F -> "float"
                else -> throw InternalCompilerException("weird dt")
            }
            val value = when(variable.dt) {
                DataType.FLOAT -> "0.0"
                in NumericDatatypes -> "0"
                DataType.ARRAY_F -> (1..variable.length!!).joinToString(",") { "0.0" }
                in ArrayDatatypes -> (1..variable.length!!).joinToString(",") { "0" }
                else -> throw InternalCompilerException("weird dt for mem mapped var")
            }
            mm.add(Pair(variable.scopedName, "$location $typeStr $value"))
        }
        return mm
    }

    fun allocateMemorySlab(name: String, size: UInt, align: UInt, position: Position): StMemorySlab {
        val address =
            if(align==0u || align==1u)
                freeMemoryStart.toUInt()
            else
                (freeMemoryStart.toUInt() + align-1u) and (0xffffffffu xor (align-1u))
        freeMemoryStart = (address + size).toInt()

        val slab = StMemorySlab(name, size, align, address, position)
        st.add(slab)
        return slab
    }

    fun getMemorySlab(name: String): StMemorySlab? {
        val existing = st.children[name]
        return if(existing==null || existing.type!=StNodeType.MEMORYSLAB)
            null
        else
            existing as StMemorySlab
    }
}
