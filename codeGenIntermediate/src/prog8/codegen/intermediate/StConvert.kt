package prog8.codegen.intermediate

import prog8.code.*
import prog8.code.core.DataType
import prog8.intermediate.*

/**
 * Converter object for transforming SymbolTable nodes to IR symbol table nodes.
 * Groups all conversion functions together for better organization.
 */
private object StToIrConverter {
    fun convert(struct: StStruct): IRStStructDef =
        IRStStructDef(struct.scopedNameString, struct.fields, struct.size)

    private fun convertArrayElt(elt: StArrayElement): IRStSymbolicReference {
        return when(elt) {
            is StArrayElement.BoolValue -> IRStSymbolicReference.BoolValue(elt.value)
            is StArrayElement.Number -> IRStSymbolicReference.Numeric(elt.value)
            // Memory slabs and address-of are both stored as symbol references in the IR
            is StArrayElement.MemorySlab -> IRStSymbolicReference.Symbol("$StMemorySlabBlockName.${elt.name}")
            is StArrayElement.AddressOf -> IRStSymbolicReference.Symbol(elt.symbol)
            is StArrayElement.StructInstance -> {
                // Struct instances in arrays are stored as symbol references to the struct instance block
                val symbol = StStructInstanceBlockName + "." + (if(elt.uninitialized) elt.name else elt.name)
                IRStSymbolicReference.Symbol(symbol)
            }
        }
    }

    fun convert(variable: StStaticVariable): IRStStaticVariable {

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
            fun fixupAddressOfInArray(array: List<StArrayElement>?): List<IRStSymbolicReference>? {
                if(array==null)
                    return null
                return array.map {
                    when(it) {
                        is StArrayElement.AddressOf -> {
                            val target = variable.lookup(it.symbol) ?:
                                throw NoSuchElementException("can't find variable ${it.symbol}")
                            IRStSymbolicReference.Symbol(target.scopedNameString)
                        }
                        else -> convertArrayElt(it)
                    }
                }
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

    fun convert(variable: StMemVar): IRStMemVar {
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

    fun convert(constant: StConstant): IRStConstant {
        val scopedName = if('.' in constant.name) {
            constant.name
        } else {
            try {
                constant.scopedNameString
            } catch (_: UninitializedPropertyAccessException) {
                constant.name
            }
        }
        if(constant.value != null)
            return IRStConstant(scopedName, constant.dt, constant.value)
        else if(constant.memorySlab != null) {
            // For memory() constants, store reference to slab name
            // The actual address will be resolved during code generation
            val slabName = constant.memorySlab!!.name
            return IRStConstant(scopedName, constant.dt, null, slabName)
        }
        else
            TODO("constant without value or memory slab: $constant")
    }

    fun convert(mem: StMemorySlab): IRStMemorySlab {
        return if('.' in mem.name)
            IRStMemorySlab(mem.name, mem.size, mem.align)
        else
            IRStMemorySlab("$StMemorySlabBlockName.${mem.name}", mem.size, mem.align)
    }

    fun convert(instance: StStructInstance, fields: Iterable<Pair<DataType, String>>): IRStStructInstance {
        val values = fields.zip(instance.initialValues).map { (field, value) ->
            val elt = convertArrayElt(value)
            IRStructInitValue(field.first.base, elt)
        }
        return if('.' in instance.name)
            IRStStructInstance(instance.name, instance.structName, values, instance.size)
        else
            IRStStructInstance("${StStructInstanceBlockName}.${instance.name}", instance.structName, values, instance.size)
    }
}

fun convertStToIRSt(sourceSt: SymbolTable?): IRSymbolTable {
    val st = IRSymbolTable()
    if (sourceSt != null) {
        sourceSt.flat.forEach {
            when(it.value.type) {
                StNodeType.STATICVAR -> st.add(StToIrConverter.convert(it.value as StStaticVariable))
                StNodeType.MEMVAR -> st.add(StToIrConverter.convert(it.value as StMemVar))
                StNodeType.CONSTANT -> {
                    val constant = it.value as StConstant
                    // If the constant has a memory() slab, add the slab to the IR symbol table first
                    constant.memorySlab?.let { slab ->
                        st.add(StToIrConverter.convert(slab))
                    }
                    st.add(StToIrConverter.convert(constant))
                }
                StNodeType.MEMORYSLAB -> st.add(StToIrConverter.convert(it.value as StMemorySlab))
                StNodeType.STRUCTINSTANCE -> {
                    val instance = it.value as StStructInstance
                    val struct = sourceSt.lookup(instance.structName) as StStruct
                    st.add(StToIrConverter.convert(instance, struct.fields))
                }
                StNodeType.STRUCT -> st.add(StToIrConverter.convert(it.value as StStruct))
                else -> { }
            }
        }

        st.validate()

        st.allVariables().forEach { variable ->
            variable.onetimeInitializationArrayValue?.let {
                it.forEach { arrayElt ->
                    if (arrayElt is IRStSymbolicReference.Symbol) {
                        require(arrayElt.name.contains('.')) {
                            "pointer var in array should be properly scoped: ${arrayElt.name} in ${variable.name}"
                        }
                    }
                }
            }
        }
    }
    return st
}
