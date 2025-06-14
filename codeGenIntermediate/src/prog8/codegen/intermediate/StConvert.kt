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
                StNodeType.STRUCTINSTANCE -> {
                    val instance = it.value as StStructInstance
                    val struct = sourceSt.lookup(instance.structName) as StStruct
                    st.add(convert(instance, struct.fields))
                }
                StNodeType.STRUCT -> st.add(convert(it.value as StStruct))
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


private fun convert(struct: StStruct): IRStStructDef =
    IRStStructDef(struct.scopedNameString, struct.fields, struct.size)


private fun convertArrayElt(elt: StArrayElement): IRStArrayElement = if(elt.boolean!=null)
    IRStArrayElement(elt.boolean, null, elt.addressOfSymbol)
else
    IRStArrayElement(null, elt.number, elt.addressOfSymbol)


private fun convert(variable: StStaticVariable): IRStStaticVariable {

    if('.' in variable.name) {
        return IRStStaticVariable(variable.name,
            variable.dt,
            variable.initializationNumericValue,
            variable.initializationStringValue,
            variable.initializationArrayValue?.map { convertArrayElt(it) },
            variable.length,
            variable.zpwish,
            variable.align,
            variable.dirty)
    } else {
        fun fixupAddressOfInArray(array: List<StArrayElement>?): List<IRStArrayElement>? {
            if(array==null)
                return null
            val newArray = mutableListOf<IRStArrayElement>()
            array.forEach {
                if(it.addressOfSymbol!=null) {
                    val target = variable.lookup(it.addressOfSymbol!!) ?: throw NoSuchElementException("can't find variable ${it.addressOfSymbol}")
                    newArray.add(IRStArrayElement(null, null, target.scopedNameString))
                } else {
                    newArray.add(convertArrayElt(it))
                }
            }
            return newArray
        }
        val scopedName = variable.scopedNameString
        return IRStStaticVariable(scopedName,
            variable.dt,
            variable.initializationNumericValue,
            variable.initializationStringValue,
            fixupAddressOfInArray(variable.initializationArrayValue),
            variable.length,
            variable.zpwish,
            variable.align,
            variable.dirty
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
            variable.scopedNameString
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
            constant.scopedNameString
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
        IRStMemorySlab("$StMemorySlabPrefix.${variable.name}", variable.size, variable.align)
}


private fun convert(instance: StStructInstance, fields: Iterable<Pair<DataType, String>>): IRStStructInstance {
    val values = fields.zip(instance.initialValues).map { (field, value) ->
        val elt = convertArrayElt(value)
        IRStructInitValue(field.first.base, elt)
    }
    return IRStStructInstance(instance.name, instance.structName, values, instance.size)
}


internal const val StMemorySlabPrefix = "prog8_slabs"       // TODO also add  ".prog8_memoryslab_"  ?
