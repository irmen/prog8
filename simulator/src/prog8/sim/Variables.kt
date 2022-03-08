package prog8.sim

import prog8.ast.base.ArrayDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.FatalAstException
import prog8.ast.base.NumericDatatypes
import prog8.compilerinterface.Encoding
import prog8.compilerinterface.StStaticVariable
import prog8.compilerinterface.SymbolTable

class Variables(symboltable: SymbolTable) {

    class Value(
        val number: Double?,
        val string: Pair<String, Encoding>?,
        val array: DoubleArray?
    )

    val allVars = symboltable.allVariables
    val flatSymbolTable = symboltable.flat
    val allocations = mutableMapOf<StStaticVariable, UInt>()
    val values = mutableMapOf<StStaticVariable, Value>()

    init {
        var address = 0x1000u
        for (variable in allVars) {
            allocations[variable] = address
            address += MemSizer.memorySize(variable.dt).toUInt()
            when(variable.dt) {
                in NumericDatatypes -> {
                    val number = if(variable.initialNumericValue==null) 0.0 else variable.initialNumericValue!!
                    values[variable] = Value(number, null, null)
                }
                DataType.STR -> {
                    values[variable] = Value(null, variable.initialStringValue!!, null)
                }
                in ArrayDatatypes -> {
                    val array = if(variable.initialArrayValue==null) DoubleArray(variable.arraysize!!) else variable.initialArrayValue!!
                    values[variable] = Value(null, null, array)
                }
                else -> throw FatalAstException("weird dt")
            }
        }
    }

    fun getAddress(variable: StStaticVariable): UInt  = allocations.getValue(variable)

    fun getValue(variable: StStaticVariable): Value = values.getValue(variable)

    fun setValue(variable: StStaticVariable, value: Value) {
        when(variable.dt) {
            in NumericDatatypes-> require(value.number!=null)
            DataType.STR -> require(value.string!=null)
            in ArrayDatatypes -> require(value.array!=null)
            else -> throw FatalAstException("weird dt")
        }
        values[variable] = value
    }
}