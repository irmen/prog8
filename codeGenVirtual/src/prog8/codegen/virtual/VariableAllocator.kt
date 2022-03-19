package prog8.codegen.virtual

import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.*

class VariableAllocator(private val st: SymbolTable, private val program: PtProgram, errors: IErrorReporter) {

    private val allocations = mutableMapOf<List<String>, Int>()
    val freeStart: Int

    init {
        var nextLocation = 0
        for (variable in st.allVariables) {
            val memsize =
                when (variable.dt) {
                    DataType.STR -> variable.initialStringValue!!.first.length + 1  // include the zero byte
                    in NumericDatatypes -> program.memsizer.memorySize(variable.dt)
                    in ArrayDatatypes -> program.memsizer.memorySize(variable.dt, variable.arraysize!!)
                    else -> throw InternalCompilerException("weird dt")
                }

            allocations[variable.scopedName] = nextLocation
            nextLocation += memsize
        }

        freeStart = nextLocation
    }

    fun asVmMemory(): List<String> {
/*
$4000 strz "Hello from program! "derp" bye.\n"
$2000 ubyte 65,66,67,68,0
$2100 uword $1111,$2222,$3333,$4444
 */
        val mm = mutableListOf<String>()
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
                DataType.FLOAT -> (variable.initialNumericValue ?: 0.0).toString()
                in NumericDatatypes -> (variable.initialNumericValue ?: 0).toHex()
                DataType.STR -> {
                    val encoded = program.encoding.encodeString(variable.initialStringValue!!.first, variable.initialStringValue!!.second)
                    encoded.joinToString(",") { it.toInt().toHex() } + ",0"
                }
                DataType.ARRAY_F -> {
                    if(variable.initialArrayValue!=null) {
                        variable.initialArrayValue!!.joinToString(",") { it.number!!.toString() }
                    } else {
                        (1..variable.arraysize!!).joinToString(",") { "0" }
                    }
                }
                in ArrayDatatypes -> {
                    if(variable.initialArrayValue!==null) {
                        variable.initialArrayValue!!.joinToString(",") { it.number!!.toHex() }
                    } else {
                        (1..variable.arraysize!!).joinToString(",") { "0" }
                    }
                }
                else -> throw InternalCompilerException("weird dt")
            }
            mm.add("${location.toHex()} $typeStr $value")
        }
        return mm
    }
}
