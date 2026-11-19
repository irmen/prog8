package prog8.codegen.intermediate

import prog8.code.*
import prog8.code.ast.PtStructField
import prog8.code.core.Position
import prog8.intermediate.*

/**
 * Converter object for transforming SymbolTable nodes to IR symbol table nodes.
 * Groups all conversion functions together for better organization.
 */
private class StToIrConverter(val romable: Boolean, val st: SymbolTable?) {
    fun convert(struct: StStruct): IRStStructDef =
        IRStStructDef(struct.scopedNameString, makeIrStructFieldsFrom(struct.fields), struct.size, struct.isUnion)

    private fun makeIrStructFieldsFrom(fields: List<PtStructField>): List<IRStStructField> =
        fields.map { IRStStructField(it.type, it.name, it.arraySize) }

    private fun convertArrayElt(elt: StArrayElement): IRStSymbolicReference {
        return when(elt) {
            is StArrayElement.BoolValue -> IRStSymbolicReference.BoolValue(elt.value)
            is StArrayElement.Number -> IRStSymbolicReference.Numeric(elt.value)
            // Memory slabs and address-of are both stored as symbol references in the IR
            is StArrayElement.MemorySlab -> IRStSymbolicReference.Symbol("$StMemorySlabBlockName.${elt.name}")
            is StArrayElement.AddressOf -> IRStSymbolicReference.Symbol(structInstanceAddressOfSymbol(elt.symbol) ?: elt.symbol)
            is StArrayElement.StructInstance -> {
                // Struct instances in arrays are stored as symbol references to the struct instance block
                val symbol = StStructInstanceBlockName + "." + (if(elt.uninitialized) elt.name else elt.name)
                IRStSymbolicReference.Symbol(symbol)
            }
        }
    }

    // Symbol-table name under which the IR registers the given struct instance's storage.
    // References to struct instances must use exactly this form to be resolvable.
    private fun structInstanceIRName(instance: StStructInstance): String {
        if('.' in instance.name)
            return instance.name
        if(instance.astNode is prog8.code.ast.PtVariable) {
            val parts = instance.scopedNameString.split('.').toMutableList()
            val last = parts.last()
            if(last.startsWith("p8v_"))
                parts[parts.lastIndex] = "p8i_" + last.drop(4)
            return "$StStructInstanceBlockName.${parts.joinToString("_")}"
        }
        return "$StStructInstanceBlockName.${instance.name}"
    }

    // An address-of of a direct struct instance variable is named with an 'i' prefix while its
    // declaration is a 'v' prefixed variable; map such a symbol to the instance's IR symbol name.
    // Symbols without prefixes (the VM target doesn't prefix them) resolve directly.
    // Returns null when the symbol is not a struct instance reference.
    private fun structInstanceAddressOfSymbol(symbol: String): String? {
        val candidates = mutableListOf(symbol)
        val parts = symbol.split('.').toMutableList()
        val last = parts.last()
        if(last.startsWith("p8i_")) {
            parts[parts.lastIndex] = "p8v_" + last.drop(4)
            candidates += parts.joinToString(".")
        }
        for(candidate in candidates) {
            val target = st?.lookup(candidate) as? StStructInstance ?: continue
            return structInstanceIRName(target)
        }
        return null
    }

    private fun makeInitializer(
        variable: StStaticVariable,
        array: List<StArrayElement>?
    ): IRVariableInitializer? {
        if(array==null && variable.initializationNumericValue==null && variable.initializationStringValue==null)
            return null

        return when {
            variable.initializationStringValue != null -> {
                val str = variable.initializationStringValue!!
                IRVariableInitializer.Str(str.first, str.second)
            }
            variable.initializationNumericValue != null -> {
                IRVariableInitializer.Numeric(variable.initializationNumericValue!!)
            }
            array != null -> {
                val scopedArray = array.map {
                    when(it) {
                        is StArrayElement.AddressOf -> {
                            val instanceSymbol = structInstanceAddressOfSymbol(it.symbol)
                            if(instanceSymbol!=null)
                                IRStSymbolicReference.Symbol(instanceSymbol)
                            else {
                                val target = variable.lookup(it.symbol) ?:
                                    throw NoSuchElementException("can't find variable ${it.symbol}")
                                IRStSymbolicReference.Symbol(target.scopedNameString)
                            }
                        }
                        else -> convertArrayElt(it)
                    }
                }
                IRVariableInitializer.Array(scopedArray)
            }
            else -> null
        }
    }

    fun convert(variable: StStaticVariable): IRStStaticVariable {
        val initValue = if('.' in variable.name) {
            when {
                variable.initializationStringValue != null -> IRVariableInitializer.Str(variable.initializationStringValue!!.first, variable.initializationStringValue!!.second)
                variable.initializationNumericValue != null -> IRVariableInitializer.Numeric(variable.initializationNumericValue!!)
                variable.initializationArrayValue != null -> IRVariableInitializer.Array(variable.initializationArrayValue!!.map { convertArrayElt(it) })
                else -> null
            }
        } else {
            makeInitializer(variable, variable.initializationArrayValue)
        }

        val name = if('.' in variable.name) variable.name else variable.scopedNameString
        val hasInit = initValue != null
        val inBss = !hasInit || romable
        val readonly = romable && hasInit
        return IRStStaticVariable(name, variable.dt, initValue, variable.length, variable.zpwish, variable.align, variable.dirty, inBss, readonly, variable.shared, variable.astNode?.position ?: Position.DUMMY)
    }

    fun convert(variable: StMemVar): IRStMemVar {
        val readonly = romable && variable.address > 255u
        if('.' in variable.name) {
            return IRStMemVar(
                variable.name,
                variable.dt,
                variable.address,
                variable.length,
                readonly
            )
        } else {
            val scopedName = try {
                variable.scopedNameString
            } catch (_: UninitializedPropertyAccessException) {
                variable.name
            }
            return IRStMemVar(scopedName, variable.dt, variable.address, variable.length, readonly)
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
        val noPrefix = constant.astNode?.definingBlock()?.options?.noSymbolPrefixing == true
        if(constant.value != null)
            return IRStConstant(scopedName, constant.dt, constant.value, noPrefix = noPrefix)
        else if(constant.memorySlab != null) {
            // For memory() constants, store reference to slab name
            // The actual address will be resolved during code generation
            val slabName = constant.memorySlab!!.name
            return IRStConstant(scopedName, constant.dt, null, slabName, noPrefix)
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

    fun convert(instance: StStructInstance, fields: Iterable<PtStructField>): IRStStructInstance {
        val expanded = fields.flatMap { field ->
            val arraySz = field.arraySize
            if(arraySz!=null)
                List(arraySz) { field.type.sub!! }
            else
                listOf(field.type.base)
        }
        val values = expanded.zip(instance.initialValues).map { (baseType, value) ->
            val elt = convertArrayElt(value)
            IRStructInitValue(baseType, elt)
        }
        return structInstanceIRName(instance).let { IRStStructInstance(it, instance.structName, values, instance.size) }
    }
}

fun convertStToIRSt(sourceSt: SymbolTable?, romable: Boolean = false): IRSymbolTable {
    val st = IRSymbolTable()
    val converter = StToIrConverter(romable, sourceSt)
    if (sourceSt != null) {
        sourceSt.flat.forEach {
            when(it.value.type) {
                StNodeType.STATICVAR -> st.add(converter.convert(it.value as StStaticVariable))
                StNodeType.MEMVAR -> st.add(converter.convert(it.value as StMemVar))
                StNodeType.CONSTANT -> {
                    val constant = it.value as StConstant
                    // If the constant has a memory() slab, add the slab to the IR symbol table first
                    constant.memorySlab?.let { slab ->
                        val converted = converter.convert(slab)
                        st.add(converted)
                    }
                    st.add(converter.convert(constant))
                }
                StNodeType.MEMORYSLAB -> {
                    val slab = it.value as StMemorySlab
                    val converted = converter.convert(slab)
                    st.add(converted)
                }
                StNodeType.STRUCTINSTANCE -> {
                    val instance = it.value as StStructInstance
                    val struct = sourceSt.lookup(instance.structName) as StStruct
                    st.add(converter.convert(instance, struct.fields))
                }
                StNodeType.STRUCT -> st.add(converter.convert(it.value as StStruct))
                else -> { }
            }
        }

        st.validate()

        st.allVariables().forEach { variable ->
            if(variable.initializationValue is IRVariableInitializer.Array) {
                val initValue = variable.initializationValue as IRVariableInitializer.Array
                initValue.elements.forEach { arrayElt ->
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
