package prog8.code

import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.VMTarget


class SymbolTableMaker(private val program: PtProgram, private val options: CompilationOptions) {
    fun make(): SymbolTable {
        val st = SymbolTable(program)

        BuiltinFunctions.forEach {
            val dt = DataType.forDt(it.value.returnType ?: BaseDataType.UNDEFINED)
            st.add(StNode(it.key, StNodeType.BUILTINFUNC, PtIdentifier(it.key, dt, Position.DUMMY)))
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
                StConstant(node.name, node.type.base, node.value, node)
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
                StStruct(node.name, node.fields, size.toUInt(), node)
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
                            initialArray = makeInitialArray(value)
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
                val stVar = StStaticVariable(node.name, node.type, initialString, initialArray, numElements, node.zeropage, node.align, node)
                if(initialNumeric!=null)
                    stVar.setOnetimeInitNumeric(initialNumeric)
                stVar
            }
            is PtBuiltinFunctionCall -> {
                if(node.name=="memory") {
                    // memory slab allocations are a builtin functioncall in the program, but end up named as well in the symboltable
                    require(node.name.all { it.isLetterOrDigit() || it=='_' }) {"memory name should be a valid symbol name"}
                    val slabname = (node.args[0] as PtString).value
                    val size = (node.args[1] as PtNumber).number.toUInt()
                    val align = (node.args[2] as PtNumber).number.toUInt()
                    // don't add memory slabs in nested scope, just put them in the top level of the ST
                    scope.first().add(StMemorySlab("prog8_memoryslab_$slabname", size, align, node))
                }
                else if(node.name=="structalloc") {
                    val struct = node.type.subType!!
                    if(struct is StStruct) {
                        val label =  SymbolTable.labelnameForStructInstance(node)
                        val initialValues = node.args.map {
                            when(it) {
                                is PtAddressOf -> StArrayElement(null, it.identifier!!.name, null)
                                is PtBool -> StArrayElement(null, null, it.value)
                                is PtNumber -> StArrayElement(it.number, null, null)
                                else -> throw AssemblyError("invalid structalloc argument type $it")
                            }
                        }
                        scope.first().add(StStructInstance(label, struct.scopedNameString, initialValues, struct.size, null))
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

    private fun makeInitialArray(value: PtArray): List<StArrayElement> {
        return value.children.map {
            when(it) {
                is PtAddressOf -> {
                    when {
                        it.isFromArrayElement -> TODO("address-of array element $it in initial array value")
                        else -> StArrayElement(null, it.identifier!!.name, null)
                    }
                }
                is PtNumber -> StArrayElement(it.number, null, null)
                is PtBool -> StArrayElement(null, null, it.value)
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