package prog8.codegen.intermediate

import prog8.code.*
import prog8.code.core.DataType
import prog8.intermediate.*


fun convertStToIRSt(sourceSt: SymbolTable?): IRSymbolTable {
    val st = IRSymbolTable()
    if (sourceSt != null) {
        sourceSt.flat.forEach {
            when(it.value.type) {
                StNodeType.STATICVAR -> st.add(convert(it.value as StStaticVariable))
                StNodeType.MEMVAR -> st.add(convert(it.value as StMemVar))
                StNodeType.CONSTANT -> st.add(convert(it.value as StConstant))
                StNodeType.MEMORYSLAB -> st.add(convert(it.value as StMemorySlab))
                else -> { }
            }
        }

        st.validate()

        st.allVariables().forEach { variable ->
            variable.onetimeInitializationArrayValue?.let {
                it.forEach { arrayElt ->
                    val addrOfSymbol = arrayElt.addressOfSymbol
                    if (addrOfSymbol != null) {
                        require(addrOfSymbol.contains('.')) {
                            "pointer var in array should be properly scoped: $addrOfSymbol in ${variable.name}"
                        }
                    }
                }
            }
        }
    }
    return st
}


private fun convert(variable: StStaticVariable): IRStStaticVariable {

    fun convertArrayElt(elt: StArrayElement): IRStArrayElement = if(elt.boolean!=null)
        IRStArrayElement(elt.boolean, null, elt.addressOfSymbol)
    else
        IRStArrayElement(null, elt.number, elt.addressOfSymbol)

    if('.' in variable.name) {
        return IRStStaticVariable(variable.name,
            variable.dt,
            variable.initializationNumericValue,
            variable.initializationStringValue,
            variable.initializationArrayValue?.map { convertArrayElt(it) },
            variable.length,
            variable.zpwish,
            variable.align)
    } else {
        fun fixupAddressOfInArray(array: List<StArrayElement>?): List<IRStArrayElement>? {
            if(array==null)
                return null
            val newArray = mutableListOf<IRStArrayElement>()
            array.forEach {
                if(it.addressOfSymbol!=null) {
                    val target = variable.lookup(it.addressOfSymbol!!) ?: throw NoSuchElementException("can't find variable ${it.addressOfSymbol}")
                    newArray.add(IRStArrayElement(null, null, target.scopedName))
                } else {
                    newArray.add(convertArrayElt(it))
                }
            }
            return newArray
        }
        val scopedName = variable.scopedName
        return IRStStaticVariable(scopedName,
            variable.dt,
            variable.initializationNumericValue,
            variable.initializationStringValue,
            fixupAddressOfInArray(variable.initializationArrayValue),
            variable.length,
            variable.zpwish,
            variable.align
        )
    }
}


private fun convert(variable: StMemVar): IRStMemVar {
    if('.' in variable.name) {
        return IRStMemVar(
            variable.name,
            variable.dt,
            variable.address,
            variable.length
        )
    } else {
        val scopedName = try {
            variable.scopedName
        } catch (_: UninitializedPropertyAccessException) {
            variable.name
        }
        return IRStMemVar(scopedName, variable.dt, variable.address, variable.length)
    }
}


private fun convert(constant: StConstant): IRStConstant {
    val dt = DataType.forDt(constant.dt)
    val scopedName = if('.' in constant.name) {
        constant.name
    } else {
        try {
            constant.scopedName
        } catch (_: UninitializedPropertyAccessException) {
            constant.name
        }
    }
    return IRStConstant(scopedName, dt, constant.value)
}


private fun convert(variable: StMemorySlab): IRStMemorySlab {
    return if('.' in variable.name)
        IRStMemorySlab(variable.name, variable.size, variable.align)
    else
        IRStMemorySlab("prog8_slabs.${variable.name}", variable.size, variable.align)
}

/*




 */