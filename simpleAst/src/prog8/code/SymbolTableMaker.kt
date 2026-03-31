package prog8.code

import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.VMTarget


/**
 * Helper to execute a block with a symbol pushed onto the scope stack,
 * ensuring proper cleanup even if an exception occurs.
 */
private inline fun <T> withScope(scope: ArrayDeque<StNode>, symbol: StNode, action: () -> T): T {
    scope.addLast(symbol)
    try {
        return action()
    } finally {
        scope.removeLast()
    }
}


class SymbolTableMaker(private val program: PtProgram, private val options: CompilationOptions) {
    fun make(): SymbolTable {
        // Disable cache with -noopt for easier debugging
        val st = SymbolTable(program, disableCache = !options.optimize)
        BuiltinFunctions.forEach {
            st.add(StNode(it.key, StNodeType.BUILTINFUNC, null))
        }

        val scopestack = ArrayDeque<StNode>()
        scopestack.add(st)
        program.children.forEach {
            addToSt(it, scopestack)
        }
        require(scopestack.size==1)

        if(options.compTarget.name != VMTarget.NAME) {
            listOf(
                PtMemMapped("P8ZP_SCRATCH_B1", DataType.UBYTE, options.compTarget.zeropage.SCRATCH_B1, null, Position.DUMMY),
                PtMemMapped("P8ZP_SCRATCH_REG", DataType.UBYTE, options.compTarget.zeropage.SCRATCH_REG, null, Position.DUMMY),
                PtMemMapped("P8ZP_SCRATCH_W1", DataType.UWORD, options.compTarget.zeropage.SCRATCH_W1, null, Position.DUMMY),
                PtMemMapped("P8ZP_SCRATCH_W2", DataType.UWORD, options.compTarget.zeropage.SCRATCH_W2, null, Position.DUMMY),
            ).forEach {
                st.add(StMemVar(it.name, it.type, it.address, it.arraySize, it))  // add() sets parent automatically
            }
        }

        return st
    }

    // Helper function to create a memory slab from a memory() function call
    private fun createMemorySlabFromCall(memCall: PtFunctionCall, scope: ArrayDeque<StNode>): String {
        require(memCall.args[0] is PtString) {
            "memory() first argument must be a string name at ${memCall.position}"
        }
        require(memCall.args[1] is PtNumber) {
            "memory() second argument must be a number (size) at ${memCall.position}"
        }
        require(memCall.args[2] is PtNumber) {
            "memory() third argument must be a number (alignment) at ${memCall.position}"
        }
        val slabname = (memCall.args[0] as PtString).value
        val size = (memCall.args[1] as PtNumber).number.toUInt()
        val align = (memCall.args[2] as PtNumber).number.toUInt()
        val slab = StMemorySlab("memory_$slabname", size, align, memCall)
        // Memory slabs are global memory regions, not scope-bound symbols.
        // They must be added at the top level of the symbol table regardless of where
        // the memory() call appears in the source code.
        scope.first().add(slab)
        return "memory_$slabname"
    }

    // Strip symbol prefixes (p8b_, p8t_, p8s_, etc.) from a scoped name for type comparison
    private fun stripSymbolPrefixes(name: String): String {
        return name.split('.')
            .map { part ->
                // Check for pattern: p8 + any letter + underscore (e.g., p8b_, p8t_, p8s_, p8v_, etc.)
                if(part.length >= 4 && part.startsWith("p8") && part[2].isLetter() && part[3] == '_')
                    part.drop(4)
                else part
            }
            .joinToString(".")
    }

    private fun addToSt(node: PtNode, scope: ArrayDeque<StNode>) {
        val stNode = when(node) {
            is PtAsmSub -> {
                val parameters = node.parameters.map { StExtSubParameter(it.first, it.second.type) }
                val returns = node.returns.map { StExtSubParameter(it.first, it.second) }
                StExtSub(node.name, node.address, parameters, returns, node)
            }
            is PtBlock -> {
                StNode(node.name, StNodeType.BLOCK, node)
            }
            is PtConstant -> {
                require(node.type.isNumericOrBool) {
                    "Constant '${node.name}' must have numeric or bool type at ${node.position}"
                }
                if(node.value != null)
                    StConstant(node.name, node.type, node.value, null, node)
                else if(node.memorySlab != null) {
                    // Handle memory() constant - the value will be resolved to the slab's address
                    StConstant(node.name, node.type, null, node.memorySlab, node)
                } else {
                    throw InternalCompilerException(
                        "Constant '${node.name}' has no value or memory slab at ${node.position}"
                    )
                }
            }
            is PtLabel -> {
                StNode(node.name, StNodeType.LABEL, node)
            }
            is PtMemMapped -> {
                StMemVar(node.name, node.type, node.address, node.arraySize, node)
            }
            is PtSub -> {
                val params = node.signature.children.map {
                    it as PtSubroutineParameter
                    StSubroutineParameter(it.name, it.type, it.register)
                }
                StSub(node.name, params, node.signature.returns, node)
            }
            is PtStructDecl -> {
                val size = node.fields.sumOf { program.memsizer.memorySize(it.first, 1) }
                // Compute the logical scoped name (without symbol prefixes like p8b_, p8t_)
                val scopedName = node.scopedName
                val logicalScopedName = stripSymbolPrefixes(scopedName)
                StStruct(node.name, node.fields, size.toUInt(), logicalScopedName, node)
            }
            is PtVariable -> {
                val initialNumeric: Double?
                val initialString: StString?
                val initialArray: StArray?
                val numElements: UInt?
                val value = node.value
                if(value!=null) {
                    when (value) {
                        is PtString -> {
                            initialString = StString(value.value, value.encoding)
                            initialArray = null
                            initialNumeric = null
                            numElements = (value.value.length + 1).toUInt()   // include the terminating 0-byte
                        }
                        is PtArray -> {
                            initialArray = makeInitialArray(value, scope)
                            initialString = null
                            initialNumeric = null
                            numElements = initialArray.size.toUInt()
                            require(node.arraySize==numElements) {
                                "Array size mismatch for '${node.name}': declared ${node.arraySize} but initialized with $numElements elements at ${node.position}"
                            }
                        }
                        else -> {
                            require(value is PtNumber)
                            initialString = null
                            initialArray = null
                            val number = value.number
                            initialNumeric = number
                            numElements = node.arraySize
                        }
                    }
                } else {
                    initialNumeric = null
                    initialArray = null
                    initialString = null
                    numElements = node.arraySize
                }
//                if(node.type in SplitWordArrayTypes) {
//                    ... split array also add _lsb and _msb to symboltable?
//                }
                val stVar = StStaticVariable(node.name, node.type, initialString, initialArray, numElements, node.zeropage, node.align, node.dirty,node)
                if(initialNumeric!=null)
                    stVar.setOnetimeInitNumeric(initialNumeric)
                stVar
            }
            is PtFunctionCall if node.builtin -> {
                if(node.name=="memory") {
                    createMemorySlabFromCall(node, scope)
                }
                else if(node.name=="prog8_lib_structalloc") {
                    val instance = handleStructAllocation(node, scope)
                    if(instance!=null) {
                        scope.first().add(instance)
                    }
                }
                null
            }
            else -> null // node is not present in the ST
        }

        if(stNode!=null) {
            scope.last().add(stNode)
            withScope(scope, stNode) {
                node.children.forEach {
                    addToSt(it, scope)
                }
            }
        } else {
            node.children.forEach {
                addToSt(it, scope)
            }
        }
    }

    private fun handleStructAllocation(node: PtFunctionCall, scope: ArrayDeque<StNode>): StStructInstance? {
        require(node.builtin)
        val struct = node.type.subType as? StStruct ?: return null
        val initialValues = node.args.map {
            when(it) {
                is PtAddressOf -> StArrayElement.AddressOf(it.identifier!!.name)
                is PtBool -> StArrayElement.BoolValue(it.value)
                is PtNumber -> StArrayElement.Number(it.number)
                is PtFunctionCall -> {
                    require(it.builtin && it.name == "memory") {
                        "prog8_lib_structalloc() argument must be memory() call at ${it.position}"
                    }
                    val slabname = createMemorySlabFromCall(it, scope)
                    StArrayElement.MemorySlab(slabname)
                }
                else -> throw InternalCompilerException(
                    "Invalid argument type '${it::class.simpleName}' for prog8_lib_structalloc() at ${it.position}"
                )
            }
        }
        val label = SymbolTable.labelnameForStructInstance(node)
        val scopedStructName = if(struct.astNode!=null) (struct.astNode as PtNamedNode).scopedName else struct.scopedNameString
        return StStructInstance(label, scopedStructName, initialValues, struct.size, null)
    }

    private fun handleStructallocAsArrayElement(call: PtFunctionCall, scope: ArrayDeque<StNode>): StArrayElement {
        val instance = handleStructAllocation(call, scope)
        return if(instance==null) {
            val label = SymbolTable.labelnameForStructInstance(call)
            if (call.args.isEmpty())
                StArrayElement.StructInstance(label, uninitialized = true)
            else
                StArrayElement.StructInstance(label)
        } else {
            scope.first().add(instance)
            if (call.args.isEmpty())
                StArrayElement.StructInstance(instance.name, uninitialized = true)
            else
                StArrayElement.StructInstance(instance.name)
        }
    }

    private fun makeInitialArray(value: PtArray, scope: ArrayDeque<StNode>): List<StArrayElement> {
        return value.children.map {
            when(it) {
                is PtAddressOf -> {
                    when {
                        it.isFromArrayElement -> TODO("address-of array element $it in initial array value  ${it.position}")
                        else -> StArrayElement.AddressOf(it.identifier!!.name)
                    }
                }
                is PtNumber -> StArrayElement.Number(it.number)
                is PtBool -> StArrayElement.BoolValue(it.value)
                is PtFunctionCall -> {
                    require(it.builtin)
                    if(it.name=="prog8_lib_structalloc") {
                        handleStructallocAsArrayElement(it, scope)
                    } else if(it.name=="memory") {
                        val slabname = createMemorySlabFromCall(it, scope)
                        StArrayElement.MemorySlab(slabname)
                    } else
                        TODO("support for initial array element via ${it.name}  ${it.position}")
                }
                else -> throw InternalCompilerException(
                    "Invalid array element type '${it::class.simpleName}' at ${it.position}"
                )
            }
        }
    }

}