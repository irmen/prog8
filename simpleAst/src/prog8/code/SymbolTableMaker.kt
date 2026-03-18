package prog8.code

import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.VMTarget


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
                it.parent = program
                st.add(StMemVar(it.name, it.type, it.address, it.arraySize, it))
            }
        }

        return st
    }

    // Helper function to create a memory slab from a memory() function call
    private fun createMemorySlabFromCall(memCall: PtFunctionCall, scope: ArrayDeque<StNode>): String {
        require(memCall.args[0] is PtString) {"memory() first arg must be string"}
        require(memCall.args[1] is PtNumber) {"memory() second arg must be number"}
        require(memCall.args[2] is PtNumber) {"memory() third arg must be number"}
        val slabname = (memCall.args[0] as PtString).value
        val size = (memCall.args[1] as PtNumber).number.toUInt()
        val align = (memCall.args[2] as PtNumber).number.toUInt()
        val slab = StMemorySlab("memory_$slabname", size, align, memCall)
        // don't add memory slabs in nested scope, just put them in the top level of the ST
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
                require(node.type.isNumericOrBool)
                if(node.value != null)
                    StConstant(node.name, node.type, node.value, null, node)
                else if(node.memorySlab != null) {
                    // Handle memory() constant - the value will be resolved to the slab's address
                    StConstant(node.name, node.type, null, node.memorySlab, node)
                } else {
                    throw InternalCompilerException("constant without value or memory slab ${node.position}")
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
                            require(node.arraySize==numElements)
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
                    // memory slab allocations are a builtin functioncall in the program, but end up named as well in the symboltable
                    require(node.name.all { it.isLetterOrDigit() || it=='_' }) {"memory name should be a valid symbol name"}
                    createMemorySlabFromCall(node, scope)
                }
                else if(node.name=="prog8_lib_structalloc") {
                    val instance = handleStructAllocation(node, scope)
                    if(instance!=null) {
                        scope.first().add(instance)  // don't add struct instances in nested scope, just put them in the top level of the ST
                    }
                }
                null
            }
            else -> null // node is not present in the ST
        }

        if(stNode!=null) {
            scope.last().add(stNode)
            scope.add(stNode)
        }
        node.children.forEach {
            addToSt(it, scope)
        }
        if(stNode!=null)
            scope.removeLast()
    }

    private fun handleStructAllocation(node: PtFunctionCall, scope: ArrayDeque<StNode>): StStructInstance? {
        require(node.builtin)
        val struct = node.type.subType as? StStruct ?: return null
        val initialValues = node.args.map {
            when(it) {
                is PtAddressOf -> StArrayElement(null, it.identifier!!.name, null, null,null)
                is PtBool -> StArrayElement(null, null, null, null, it.value)
                is PtNumber -> StArrayElement(it.number, null, null, null, null)
                is PtFunctionCall -> {
                    // Handle memory() call in struct initializer
                    require(it.builtin && it.name == "memory")
                    val slabname = createMemorySlabFromCall(it, scope)
                    StArrayElement(null, null, null, null, null, slabname)
                }
                else -> throw AssemblyError("invalid structalloc argument type $it")
            }
        }
        val label =  SymbolTable.labelnameForStructInstance(node)
        val scopedStructName = if(struct.astNode!=null) (struct.astNode as PtNamedNode).scopedName else struct.scopedNameString
        return StStructInstance(label, scopedStructName, initialValues, struct.size, null)
    }

    private fun makeInitialArray(value: PtArray, scope: ArrayDeque<StNode>): List<StArrayElement> {
        return value.children.map {
            when(it) {
                is PtAddressOf -> {
                    when {
                        it.isFromArrayElement -> TODO("address-of array element $it in initial array value  ${it.position}")
                        else -> StArrayElement(null, it.identifier!!.name, null, null,null)
                    }
                }
                is PtNumber -> StArrayElement(it.number, null, null,null,null)
                is PtBool -> StArrayElement(null, null, null,null,it.value)
                is PtFunctionCall -> {
                    require(it.builtin)
                    if(it.name=="prog8_lib_structalloc") {
                        val instance = handleStructAllocation(it, scope)
                        if(instance==null) {
                            val label = SymbolTable.labelnameForStructInstance(it)
                            if (it.args.isEmpty())
                                StArrayElement(null, null, null, label, null)
                            else
                                StArrayElement(null, null, label, null, null)
                        } else {
                            scope.first().add(instance)  // don't add struct instances in nested scope, just put them in the top level of the ST
                            if (it.args.isEmpty())
                                StArrayElement(null, null, null, instance.name, null)
                            else
                                StArrayElement(null, null, instance.name, null, null)
                        }
                    } else if(it.name=="memory") {
                        // Handle memory() call in array initializer
                        val slabname = createMemorySlabFromCall(it, scope)
                        StArrayElement(null, null, null, null, null, slabname)
                    } else
                        TODO("support for initial array element via ${it.name}  ${it.position}")
                }
                else -> throw AssemblyError("invalid array element $it")
            }
        }
    }

}

//    override fun visit(decl: VarDecl) {
//        val node =
//            when(decl.type) {
//                VarDeclType.VAR -> {
//                    var initialNumeric = (decl.value as? NumericLiteral)?.number
//                    if(initialNumeric==0.0)
//                        initialNumeric=null     // variable will go into BSS and this will be set to 0
//                    val initialStringLit = decl.value as? StringLiteral
//                    val initialString = if(initialStringLit==null) null else Pair(initialStringLit.value, initialStringLit.encoding)
//                    val initialArrayLit = decl.value as? ArrayLiteral
//                    val initialArray = makeInitialArray(initialArrayLit)
//                    if(decl.isArray && decl.datatype !in ArrayDatatypes)
//                        throw FatalAstException("array vardecl has mismatched dt ${decl.datatype}")
//                    val numElements =
//                        if(decl.isArray)
//                            decl.arraysize!!.constIndex()
//                        else if(initialStringLit!=null)
//                            initialStringLit.value.length+1  // include the terminating 0-byte
//                        else
//                            null
//                    val bss = if(decl.datatype==DataType.STR)
//                        false
//                    else if(decl.isArray)
//                        initialArray.isNullOrEmpty()
//                    else
//                        initialNumeric == null
//                    val astNode = PtVariable(decl.name, decl.datatype, null, null, decl.position)
//                    StStaticVariable(decl.name, decl.datatype, bss, initialNumeric, initialString, initialArray, numElements, decl.zeropage, astNode, decl.position)
//                }
//                VarDeclType.CONST -> {
//                    val astNode = PtVariable(decl.name, decl.datatype, null, null, decl.position)
//                    StConstant(decl.name, decl.datatype, (decl.value as NumericLiteral).number, astNode, decl.position)
//                }
//                VarDeclType.MEMORY -> {
//                    val numElements =
//                        if(decl.isArray)
//                            decl.arraysize!!.constIndex()
//                        else null
//                    val astNode = PtVariable(decl.name, decl.datatype, null, null, decl.position)
//                    StMemVar(decl.name, decl.datatype, (decl.value as NumericLiteral).number.toUInt(), numElements, astNode, decl.position)
//                }
//            }
//        scopestack.peek().add(node)
//        // st.origAstLinks[decl] = node
//    }
//
//    private fun makeInitialArray(arrayLit: ArrayLiteral?): StArray? {
//        if(arrayLit==null)
//            return null
//        return arrayLit.value.map {
//            when(it){
//                is AddressOf -> {
//                    val scopedName = it.identifier.targetNameAndType(program).first
//                    StArrayElement(null, scopedName)
//                }
//                is IdentifierReference -> {
//                    val scopedName = it.targetNameAndType(program).first
//                    StArrayElement(null, scopedName)
//                }
//                is NumericLiteral -> StArrayElement(it.number, null)
//                else -> throw FatalAstException("weird element dt in array literal")
//            }
//        }.toList()
//    }
//