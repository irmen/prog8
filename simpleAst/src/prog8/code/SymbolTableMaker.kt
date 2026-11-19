package prog8.code

import prog8.code.ast.*
import prog8.code.core.*


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

        if(options.compTarget.cpu.is6502) {
            listOf(
                PtMemMapped("P8ZP_SCRATCH_B1", DataType.UBYTE, options.compTarget.zeropage.SCRATCH_B1, null, Position.DUMMY),
                PtMemMapped("P8ZP_SCRATCH_REG", DataType.UBYTE, options.compTarget.zeropage.SCRATCH_REG, null, Position.DUMMY),
                PtMemMapped("P8ZP_SCRATCH_W1", DataType.UWORD, options.compTarget.zeropage.SCRATCH_W1, null, Position.DUMMY),
                PtMemMapped("P8ZP_SCRATCH_W2", DataType.UWORD, options.compTarget.zeropage.SCRATCH_W2, null, Position.DUMMY),
            ).forEach {
                it.parent=program
                st.add(StMemVar(it.name, it.type, it.address, it.arraySize, it))
            }
        }

        return st
    }

    private fun addToSt(node: PtNode, scope: ArrayDeque<StNode>) {
        val stNode = when(node) {
            is PtAsmSub -> {
                val parameters = node.parameters.map { StExtSubParameter(it.second.name, it.second.type, it.first) }
                val returns = node.returns.map { StExtSubParameter("", it.second, it.first) }
                StExtSub(node.name, node.address, parameters, returns, node)
            }
            is PtBlock -> {
                StNode(node.name, StNodeType.BLOCK, node)
            }
            is PtConstant -> {
                require(node.type.isNumericOrBool || node.type.isPointer) {
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
                StStruct(node.name, node.fields, structSize(node), node, node.isUnion)
            }
            is PtMemorySlabReservation -> {
                val slab = StMemorySlab("memory_${node.slabName}", node.size, node.align, node)
                scope.first().add(slab)
                null
            }
            is PtVariable if node.type.isStructInstance -> makeStructInstanceVariable(node, scope)
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
                val stVar = StStaticVariable(node.name, node.type, initialString, initialArray, numElements, node.zeropage, node.align, node.dirty, node, node.shared)
                if(initialNumeric!=null)
                    stVar.setOnetimeInitNumeric(initialNumeric)
                stVar
            }
            is PtFunctionCall if node.builtin && node.name=="prog8_lib_structalloc" -> {
                val instance = handleStructAllocation(node)
                if(instance!=null) {
                    scope.first().add(instance)
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

    private fun makeStructInstanceVariable(node: PtVariable, scope: ArrayDeque<StNode>): StStructInstance {
        val struct = node.type.subType as? StStruct ?: resolveStructForVariable(node, scope)
        val scopedStructName = if(struct.astNode!=null) (struct.astNode as PtNamedNode).scopedName else struct.scopedNameString
        val values = when(val value = node.value) {
            null -> emptyList()
            is PtFunctionCall if value.builtin && value.name=="prog8_lib_structalloc" ->
                handleStructAllocation(value, struct)?.initialValues ?: emptyList()
            else -> throw InternalCompilerException("invalid struct instance initializer for '${node.name}'")
        }
        return StStructInstance(node.name, scopedStructName, values, struct.size, node)
    }

    private fun structSize(decl: PtStructDecl): UInt =
        if(decl.isUnion) {
            decl.fields.maxOfOrNull { field ->
                if(field.isArray) program.memsizer.memorySize(field.type, field.arraySize!!)
                else program.memsizer.memorySize(field.type, 1)
            } ?: 0
        } else {
            decl.fields.sumOf { field ->
                if(field.isArray) program.memsizer.memorySize(field.type, field.arraySize!!)
                else program.memsizer.memorySize(field.type, 1)
            }
        }.toUInt()

    private fun resolveStructForVariable(node: PtVariable, scope: ArrayDeque<StNode>): StStruct {
        val wanted = node.type.subType?.scopedNameString ?: node.type.subTypeFromAntlr?.joinToString(".")
            ?: throw InternalCompilerException("struct type not resolved for '${node.name}' at ${node.position}")

        // matches when the candidate and the wanted name are equal after prefix stripping,
        // or when one is the trailing part of the other (a bare 'Point' reference matches
        // a struct registered as 'main.Point', and vice versa)
        fun matches(candidateName: String): Boolean {
            val candidate = SymbolNames.stripPrefixes(candidateName)
            val ref = SymbolNames.stripPrefixes(wanted)
            return candidate==ref || candidate.endsWith(".$ref") || ref.endsWith(".$candidate")
        }

        // enclosing scopes first, most specific one wins (shadowing semantics)
        for(level in scope.reversed()) {
            (level.children.values.firstOrNull { it is StStruct && matches(it.scopedNameString) })?.let { return it as StStruct }
        }

        // fall back to the rest of the program: structs that are already in the symbol table
        // elsewhere are preferred (reuse keeps struct identity stable), AST declarations that
        // have not been turned into symbol table nodes yet come last
        val stCandidates = collectStStructs(scope.first()).filter { matches(it.scopedNameString) }
        if(stCandidates.size==1)
            return stCandidates.single()

        val declsInSymbolTable = stCandidates.mapNotNull { it.astNode }.toSet()
        val astCandidates = program.allBlocks().flatMap { it.children.filterIsInstance<PtStructDecl>() }
            .filter { it !in declsInSymbolTable && matches(it.scopedName) }
            .toList()

        val foundNames = (stCandidates.map { SymbolNames.stripPrefixes(it.scopedNameString) } +
                astCandidates.map { SymbolNames.stripPrefixes(it.scopedName) }).distinct()
        when {
            stCandidates.isEmpty() && astCandidates.size==1 -> {
                val decl = astCandidates.single()
                return StStruct(decl.name, decl.fields, structSize(decl), decl, decl.isUnion)
            }
            foundNames.isEmpty() ->
                throw InternalCompilerException("struct type '$wanted' not found for '${node.name}' at ${node.position}")
            else ->
                throw InternalCompilerException(
                    "ambiguous struct type '$wanted' for '${node.name}' at ${node.position}, matching: ${foundNames.joinToString()}")
        }
    }

    private fun collectStStructs(root: StNode): List<StStruct> {
        val result = mutableListOf<StStruct>()
        fun walk(node: StNode) {
            node.children.values.forEach {
                if(it is StStruct) result.add(it)
                walk(it)
            }
        }
        walk(root)
        return result
    }

    private fun handleStructAllocation(node: PtFunctionCall, structOverride: StStruct? = null): StStructInstance? {
        require(node.builtin)
        val struct = structOverride ?: (node.type.subType as? StStruct ?: return null)
        val initialValues = node.args.map {
            when(it) {
                is PtAddressOf -> StArrayElement.AddressOf(it.identifier!!.name)
                is PtBool -> StArrayElement.BoolValue(it.value)
                is PtNumber -> StArrayElement.Number(it.number)
                is PtConstant -> {
                    val slab = it.memorySlab
                        ?: throw InternalCompilerException(
                            "prog8_lib_structalloc() argument must be memory() call at ${it.position}"
                        )
                    StArrayElement.MemorySlab(slab.name)
                }
                is PtFunctionCall -> {
                    if(it.builtin && it.name=="memory") throw InternalCompilerException("memory() call should have been desugared into dedicated nodes at ${it.position}")
                    throw InternalCompilerException("Invalid argument type '${it::class.simpleName}' for prog8_lib_structalloc() at ${it.position}")
                }
                is PtTypeCast -> {
                    // string literals (and other casts) are wrapped in a type cast to the field's pointer/word type;
                    // unwrap and handle the underlying expression
                    when (val v = it.value) {
                        is PtAddressOf -> StArrayElement.AddressOf(v.identifier!!.name)
                        is PtNumber -> StArrayElement.Number(v.number)
                        is PtBool -> StArrayElement.BoolValue(v.value)
                        is PtConstant -> {
                            val slab = v.memorySlab
                                ?: throw InternalCompilerException(
                                    "prog8_lib_structalloc() argument must be memory() call at ${v.position}"
                                )
                            StArrayElement.MemorySlab(slab.name)
                        }
                        else -> throw InternalCompilerException(
                            "Invalid argument type '${v::class.simpleName}' for prog8_lib_structalloc() at ${v.position}"
                        )
                    }
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
        val instance = handleStructAllocation(call)
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
                is PtConstant -> {
                    val slab = it.memorySlab
                        ?: throw InternalCompilerException(
                            "Constant in array initial value must be a memory slab reference at ${it.position}"
                        )
                    StArrayElement.MemorySlab(slab.name)
                }
                is PtFunctionCall -> {
                    require(it.builtin)
                    if(it.name=="memory") throw InternalCompilerException("memory() call should have been desugared into dedicated nodes at ${it.position}")
                    if(it.name=="prog8_lib_structalloc") {
                        handleStructallocAsArrayElement(it, scope)
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
