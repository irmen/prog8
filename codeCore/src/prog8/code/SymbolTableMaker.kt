package prog8.code

import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.VMTarget
import java.util.*

class SymbolTableMaker(private val program: PtProgram, private val options: CompilationOptions) {
    fun make(): SymbolTable {
        val st = SymbolTable(program)

        BuiltinFunctions.forEach {
            st.add(StNode(it.key, StNodeType.BUILTINFUNC, PtIdentifier(it.key, it.value.returnType ?: DataType.UNDEFINED, Position.DUMMY)))
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
            ).forEach {
                it.parent = program
                st.add(StMemVar(it.name, it.type, it.address, it.arraySize?.toInt(), it))
            }
        }

        return st
    }

    private fun addToSt(node: PtNode, scope: Stack<StNode>) {
        val stNode = when(node) {
            is PtAsmSub -> {
                val parameters = node.parameters.map { StRomSubParameter(it.first, it.second.type) }
                val returns = node.returns.map { StRomSubParameter(it.first, it.second) }
                StRomSub(node.name, node.address, parameters, returns, node)
            }
            is PtBlock -> {
                StNode(node.name, StNodeType.BLOCK, node)
            }
            is PtConstant -> {
                StConstant(node.name, node.type, node.value, node)
            }
            is PtLabel -> {
                StNode(node.name, StNodeType.LABEL, node)
            }
            is PtMemMapped -> {
                StMemVar(node.name, node.type, node.address, node.arraySize?.toInt(), node)
            }
            is PtSub -> {
                val params = node.parameters.map {StSubroutineParameter(it.name, it.type) }
                StSub(node.name, params, node.returntype, node)
            }
            is PtVariable -> {
                val initialNumeric: Double?
                val initialString: StString?
                val initialArray: StArray?
                val numElements: Int?
                val value = node.value
                if(value!=null) {
                    val number = (value as? PtNumber)?.number
                    initialNumeric = if(number==0.0) null else number       // 0 as init value -> just uninitialized
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
//                if(node.type in SplitWordArrayTypes) {
//                    ... split array also add _lsb and _msb to symboltable?
//                }
                StStaticVariable(node.name, node.type, initialNumeric, initialString, initialArray, numElements, node.zeropage, node)
            }
            is PtBuiltinFunctionCall -> {
                if(node.name=="memory") {
                    // memory slab allocations are a builtin functioncall in the program, but end up named as well in the symboltable
                    require(node.name.all { it.isLetterOrDigit() || it=='_' }) {"memory name should be a valid symbol name"}
                    val slabname = (node.args[0] as PtString).value
                    val size = (node.args[1] as PtNumber).number.toUInt()
                    val align = (node.args[2] as PtNumber).number.toUInt()
                    // don't add memory slabs in nested scope, just put them in the top level of the ST
                    scope.firstElement().add(StMemorySlab("prog8_memoryslab_$slabname", size, align, node))
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
                is PtAddressOf -> {
                    if(it.isFromArrayElement)
                        TODO("address-of array element $it in initial array value")
                    StArrayElement(null, it.identifier.name, null)
                }
                is PtIdentifier -> StArrayElement(null, it.name, null)
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