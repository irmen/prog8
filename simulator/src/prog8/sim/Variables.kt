package prog8.sim

import prog8.compilerinterface.IMemSizer
import prog8.compilerinterface.StNode
import prog8.compilerinterface.StStaticVariable
import prog8.compilerinterface.SymbolTable

class Variables(symboltable: SymbolTable, memsizer: IMemSizer) {

    val allVars = symboltable.allVariables
    val flatSymbolTable = symboltable.flat

    operator fun get(target: StNode): UInt? {
        return null
    }

    fun getValue(variable: StStaticVariable): Double {
        println("warning: returning dummy value for staticvar ${variable.scopedName}")
        return 0.0
    }

    fun setValue(variable: StStaticVariable, value: Double) {
        println("warning: discarding value for staticvar ${variable.scopedName}")
    }
}