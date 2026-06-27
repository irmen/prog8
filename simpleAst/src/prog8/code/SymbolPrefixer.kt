package prog8.code

import prog8.code.ast.*
import prog8.code.core.*
import kotlin.math.absoluteValue

fun prefixSymbols(program: PtProgram, options: CompilationOptions, st: SymbolTable): SymbolTable {
    val nodesToPrefix = mutableListOf<Pair<PtNode, Int>>()
    val functionCallsToPrefix = mutableListOf<Pair<PtNode, Int>>()
    val expressionsToFixSubtype = mutableListOf<PtExpression>()
    val variablesToFixSubtype = mutableListOf<IPtVariable>()

    fun prefixNamedNode(node: PtNamedNode) {
        when(node) {
            is PtAsmSub, is PtSub -> node.name = prefixScopedName(node.name, 's')
            is PtBlock -> node.name = prefixScopedName(node.name, 'b')
            is PtLabel -> if(!node.name.startsWith(GENERATED_LABEL_PREFIX)) node.name = prefixScopedName(node.name, 'l')
            is PtVariable, is PtMemMapped, is PtSubroutineParameter -> node.name = prefixScopedName(node.name, 'v')
            is PtStructDecl -> node.name = prefixScopedName(node.name, 't')
        }
    }

    fun collectRefs(node: PtNode) {
        when(node) {
            is PtAsmSub -> {
                prefixNamedNode(node)
                node.parameters.forEach { (_, param) -> prefixNamedNode(param) }
                val address = node.address
                if (address?.varbank != null) {
                    address.varbank = address.varbank!!.prefixIdentifier(node, st)
                }
            }
            is PtSub -> prefixNamedNode(node)
            is PtFunctionCall -> {
                if(node.builtin) {
                    if(node.name=="prog8_lib_structalloc") {
                        val struct = node.type.subType!!
                        if(struct is StStruct) {
                            node.type.subType = st.lookup(struct.scopedNameString) as StStruct
                        }
                    }
                } else {
                    val stNode = st.lookup(node.name)!!
                    if (stNode.astNode?.definingBlock()?.options?.noSymbolPrefixing != true) {
                        functionCallsToPrefix += node.parent to node.parent.children.indexOf(node)
                    }
                }
            }
            is PtIdentifier -> {
                val pexpr = node.parent as? PtBinaryExpression
                if(pexpr?.operator==".") {
                    if(pexpr.left is PtArrayIndexer) {
                        val arrayvar = (pexpr.left as PtArrayIndexer).variable!!
                        if(arrayvar.type.subType!=null) {
                            return
                        } else
                            throw AssemblyError("can only use deref expression on struct type")
                    } else {
                        TODO("handle other left operand ${pexpr.left} in dereferencing of struct fields ${node.position}")
                    }
                }
                var lookupName = node.name
                if(node.type.isSplitWordArray && (lookupName.endsWith("_lsb") || lookupName.endsWith("_msb"))) {
                    lookupName = lookupName.dropLast(4)
                }
                val stNode = st.lookup(lookupName) ?:
                    throw AssemblyError("unknown identifier $node")
                if(stNode.astNode!!.definingBlock()?.options?.noSymbolPrefixing!=true) {
                    nodesToPrefix += node.parent to node.parent.children.indexOf(node)
                }
            }
            is PtBlock -> prefixNamedNode(node)
            is PtConstant -> node.name = prefixScopedName(node.name, 'c')
            is PtLabel -> prefixNamedNode(node)
            is PtMemMapped -> prefixNamedNode(node)
            is PtSubroutineParameter -> prefixNamedNode(node)
            is PtVariable -> {
                nodesToPrefix += node.parent to node.parent.children.indexOf(node)
            }
            is PtStructDecl -> prefixNamedNode(node)
            else -> {}
        }

        if(node is IPtVariable) {
            if(node.type.subType!=null)
                variablesToFixSubtype.add(node)
        } else if(node is PtExpression) {
            if(node.type.subType!=null)
                expressionsToFixSubtype.add(node)
        }

        node.children.forEach { collectRefs(it) }
    }

    fun maybePrefixRefs(node: PtNode) {
        if(node is PtFunctionCall && !node.builtin) {
            val stNode = st.lookup(node.name)!!
            if(stNode.astNode!!.definingBlock()?.options?.noSymbolPrefixing!=true) {
                functionCallsToPrefix += node.parent to node.parent.children.indexOf(node)
            }
        }
        else if (node is PtIdentifier) {
            var lookupName = node.name
            if(node.type.isSplitWordArray && (lookupName.endsWith("_lsb") || lookupName.endsWith("_msb"))) {
                lookupName = lookupName.dropLast(4)
            }
            val stNode = st.lookup(lookupName) ?: throw AssemblyError("unknown identifier $node")
            if(stNode.astNode!!.definingBlock()?.options?.noSymbolPrefixing!=true) {
                nodesToPrefix += node.parent to node.parent.children.indexOf(node)
            }
        }
        node.children.forEach { maybePrefixRefs(it) }
    }

    program.allBlocks().forEach { block ->
        if (!block.options.noSymbolPrefixing) {
            collectRefs(block)
        } else {
            maybePrefixRefs(block)
        }
    }

    nodesToPrefix.forEach { (parent, index) ->
        when(val node = parent.children[index]) {
            is PtIdentifier -> parent.setChild(index, node.prefixIdentifier(parent, st))
            is PtFunctionCall ->  throw AssemblyError("PtFunctionCall should be processed in their own list, last")
            is PtVariable -> parent.setChild(index, node.prefixVariable(parent, st))
            else -> {}
        }
    }

    functionCallsToPrefix.reversed().forEach { (parent, index) ->
        val node = parent.children[index]
        if(node is PtFunctionCall) {
            val prefixedName = PtIdentifier(node.name, DataType.UNDEFINED, node.position).prefixIdentifier(parent, st)
            val prefixedNode = node.withNewName(prefixedName.name)
            parent.setChild(index, prefixedNode)
        }
    }

    val updatedSt = SymbolTableMaker(program, options).make()

    fun findSubtypeReplacement(sub: ISubType): StStruct? {
        if(updatedSt.lookup(sub.scopedNameString)!=null)
            return null
        val old = st.lookup(sub.scopedNameString) ?: throw AssemblyError("old subtype not found: ${sub.scopedNameString}")

        val prefixed = ArrayDeque<String>()
        var node: StNode = old
        while(node.type!= StNodeType.GLOBAL) {
            val typeChar = typePrefixChar(node.type)
            prefixed.addFirst("p8${typeChar}_${node.name}")
            node=node.parent
        }
        return updatedSt.lookup(prefixed.joinToString(".")) as StStruct
    }

    expressionsToFixSubtype.forEach { node ->
        findSubtypeReplacement(node.type.subType!!)?.let {
            node.type.subType = it
        }
    }
    variablesToFixSubtype.forEach { node ->
        findSubtypeReplacement(node.type.subType!!)?.let {
            node.type.subType = it
        }
    }

    return updatedSt
}

fun typePrefixChar(targetNt: StNodeType): Char = when(targetNt) {
    StNodeType.BLOCK -> 'b'
    StNodeType.SUBROUTINE, StNodeType.EXTSUB -> 's'
    StNodeType.LABEL -> 'l'
    StNodeType.STATICVAR, StNodeType.MEMVAR -> 'v'
    StNodeType.CONSTANT -> 'c'
    StNodeType.BUILTINFUNC -> 's'
    StNodeType.MEMORYSLAB -> 'v'
    StNodeType.STRUCT -> 't'
    StNodeType.STRUCTINSTANCE -> 'i'
    else -> '?'
}

fun prefixScopedName(name: String, type: Char): String {
    if('.' !in name) {
        if(name.startsWith(GENERATED_LABEL_PREFIX))
            return name
        return "p8${type}_$name"
    }
    val parts = name.split('.')
    val firstPrefixed = "p8b_${parts[0]}"
    val lastPart = parts.last()
    val lastPrefixed = if(lastPart.startsWith(GENERATED_LABEL_PREFIX)) lastPart else "p8${type}_$lastPart"
    val inbetweenPrefixed = parts.drop(1).dropLast(1).map{ "p8s_$it" }
    val prefixed = listOf(firstPrefixed) + inbetweenPrefixed + listOf(lastPrefixed)
    return prefixed.joinToString(".")
}

fun PtIdentifier.prefixIdentifier(parent: PtNode, st: SymbolTable): PtIdentifier {
    val targetNt: StNodeType
    val target = st.lookup(name)
    if(target?.astNode?.definingBlock()?.options?.noSymbolPrefixing==true)
        return this

    if(target==null) {
        if(name.endsWith("_lsb") || name.endsWith("_msb")) {
            val target2 = st.lookup(name.dropLast(4))
            if(target2?.astNode?.definingBlock()?.options?.noSymbolPrefixing==true)
                return this
            targetNt = target2!!.type
        } else {
            targetNt = StNodeType.STATICVAR
        }
    } else {
        targetNt = target.type
    }

    val prefixType = typePrefixChar(targetNt)
    val newName = prefixScopedName(name, prefixType)
    val node = PtIdentifier(newName, type, position)
    node.parent = parent
    return node
}

fun PtVariable.prefixVariable(parent: PtNode, st: SymbolTable): PtVariable {
    name = prefixScopedName(name, 'v')
    if(value==null)
        return this

    val arrayValue = value as? PtArray
    return if(arrayValue!=null && arrayValue.children.any { it !is PtNumber && it !is PtBool } ) {
        val newValue = PtArray(arrayValue.type, arrayValue.position)
        arrayValue.children.forEach { elt ->
            when(elt) {
                is PtBool -> newValue.add(elt)
                is PtNumber -> newValue.add(elt)
                is PtConstant -> newValue.add(elt)
                is PtAddressOf -> {
                    if(elt.definingBlock()?.options?.noSymbolPrefixing==true)
                        newValue.add(elt)
                    else {
                        val newAddr = PtAddressOf(elt.type, false, elt.position)
                        newAddr.add(elt.identifier!!.prefixIdentifier(newAddr, st))
                        if (elt.arrayIndexExpr != null)
                            newAddr.add(elt.arrayIndexExpr!!)
                        newAddr.parent = arrayValue
                        newValue.add(newAddr)
                    }
                }
                is PtFunctionCall if elt.builtin -> {
                    if (elt.name != "prog8_lib_structalloc")
                        throw AssemblyError("weird array value element $elt")
                    else
                        newValue.add(elt)
                }
                else -> throw AssemblyError("weird array value element $elt")
            }
        }
        val result = PtVariable(name, type, zeropage, align, dirty, newValue, arraySize, position)
        result.parent = parent
        result
    }
    else this
}

fun PtFunctionCall.withNewName(name: String): PtFunctionCall {
    val call = PtFunctionCall(name, builtin, hasNoSideEffects, returntypes, position)
    call.addAll(children)
    call.children.forEach { it.parent = call }
    call.parent = parent
    return call
}
