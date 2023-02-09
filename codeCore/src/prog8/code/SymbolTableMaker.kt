package prog8.code

import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.VMTarget
import java.util.*

class SymbolTableMaker(private val program: PtProgram, private val options: CompilationOptions) {
    fun make(): SymbolTable {
        val st = SymbolTable(program)

        BuiltinFunctions.forEach {
            st.add(StNode(it.key, StNodeType.BUILTINFUNC, Position.DUMMY, PtIdentifier(it.key, it.value.returnType ?: DataType.UNDEFINED, Position.DUMMY)))
        }

        val scopestack = Stack<StNode>()
        scopestack.push(st)
        program.children.forEach {
            addToSt(it, scopestack)
        }
        require(scopestack.size==1)

        if(options.compTarget.name != VMTarget.NAME) {
            listOf(
                PtMemMapped("P8ZP_SCRATCH_B1", DataType.UBYTE, options.compTarget.machine.zeropage.SCRATCH_B1, null, Position.DUMMY),
                PtMemMapped("P8ZP_SCRATCH_REG", DataType.UBYTE, options.compTarget.machine.zeropage.SCRATCH_REG, null, Position.DUMMY),
                PtMemMapped("P8ZP_SCRATCH_W1", DataType.UWORD, options.compTarget.machine.zeropage.SCRATCH_W1, null, Position.DUMMY),
                PtMemMapped("P8ZP_SCRATCH_W2", DataType.UWORD, options.compTarget.machine.zeropage.SCRATCH_W2, null, Position.DUMMY),
                PtMemMapped("P8ESTACK_LO", DataType.ARRAY_UB, options.compTarget.machine.ESTACK_LO, 256u, Position.DUMMY),
                PtMemMapped("P8ESTACK_HI", DataType.ARRAY_UB, options.compTarget.machine.ESTACK_HI, 256u, Position.DUMMY)
            ).forEach {
                st.add(StMemVar(it.name, it.type, it.address, null, it, Position.DUMMY))
            }
        }

        return st
    }

    private fun addToSt(node: PtNode, scope: Stack<StNode>) {
        val stNode = when(node) {
            is PtAsmSub -> {
                if(node.address==null) {
                    val params = node.parameters.map { StSubroutineParameter(it.second.name, it.second.type) }
                    StSub(node.name, params, node.returns.singleOrNull()?.second, node, node.position)
                } else {
                    val parameters = node.parameters.map { StRomSubParameter(it.first, it.second.type) }
                    val returns = node.returns.map { StRomSubParameter(it.first, it.second) }
                    StRomSub(node.name, node.address, parameters, returns, node, node.position)
                }
            }
            is PtBlock -> {
                StNode(node.name, StNodeType.BLOCK, node.position, node)
            }
            is PtConstant -> {
                StConstant(node.name, node.type, node.value, node, node.position)
            }
            is PtLabel -> {
                StNode(node.name, StNodeType.LABEL, node.position, node)
            }
            is PtMemMapped -> {
                StMemVar(node.name, node.type, node.address, node.arraySize?.toInt(), node, node.position)
            }
            is PtSub -> {
                val params = node.parameters.map {StSubroutineParameter(it.name, it.type) }
                StSub(node.name, params, node.returntype, node, node.position)
            }
            is PtVariable -> {
                val bss = when (node.type) {
                    // TODO should bss be a computed property on PtVariable?
                    DataType.STR -> false
                    in ArrayDatatypes -> node.value==null || node.arraySize==0u
                    else -> node.value==null
                }
                val initialNumeric: Double?
                val initialString: StString?
                val initialArray: StArray?
                val numElements: Int?
                val value = node.value
                if(value!=null) {
                    initialNumeric = (value as? PtNumber)?.number
                    when (value) {
                        is PtString -> {
                            initialString = StString(value.value, value.encoding)
                            initialArray = null
                            numElements = value.value.length + 1   // include the terminating 0-byte
                        }
                        is PtArray -> {
                            initialArray = makeInitialArray(value)
                            initialString = null
                            numElements = initialArray.size
                            require(node.arraySize?.toInt()==numElements)
                        }
                        else -> {
                            initialString = null
                            initialArray = null
                            numElements = node.arraySize?.toInt()
                        }
                    }
                } else {
                    initialNumeric = null
                    initialArray = null
                    initialString = null
                    numElements = node.arraySize?.toInt()
                }
                val zeropage = ZeropageWish.DONTCARE // TODO how, can this be removed from the ST perhaps? Or is it required in the variable allocator later
                StStaticVariable(node.name, node.type, bss, initialNumeric, initialString, initialArray, numElements, zeropage, node, node.position)
            }
            is PtBuiltinFunctionCall -> {
                if(node.name=="memory") {
                    // memory slab allocations are a builtin functioncall in the program, but end up named as well in the symboltable
                    require(node.name.all { it.isLetterOrDigit() || it=='_' }) {"memory name should be a valid symbol name"}
                    val slabname = (node.args[0] as PtString).value
                    val size = (node.args[1] as PtNumber).number.toUInt()
                    val align = (node.args[2] as PtNumber).number.toUInt()
                    // don't add memory slabs in nested scope, just put them in the top level of the ST
                    scope.firstElement().add(StMemorySlab("prog8_memoryslab_$slabname", size, align, node, node.position))
                }
                null
            }
            else -> null // node is not present in the ST
        }

        if(stNode!=null) {
            scope.peek().add(stNode)
            scope.push(stNode)
        }
        node.children.forEach {
            addToSt(it, scope)
        }
        if(stNode!=null)
            scope.pop()
    }

    private fun makeInitialArray(value: PtArray): List<StArrayElement> {
        return value.children.map {
            when(it) {
                is PtAddressOf -> StArrayElement(null, it.identifier.name)
                is PtIdentifier -> StArrayElement(null, it.name)
                is PtNumber -> StArrayElement(it.number, null)
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
//                        if(decl.datatype in ArrayDatatypes)
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