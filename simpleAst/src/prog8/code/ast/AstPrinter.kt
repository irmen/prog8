package prog8.code.ast

import prog8.code.core.DataType
import prog8.code.core.escape
import prog8.code.core.toHex

/**
 * Produces readable text from a [PtNode] (AST node, usually starting with PtProgram as root),
 * passing it as a String to the specified receiver function.
 */
fun printAst(root: PtNode, skipLibraries: Boolean, output: (text: String) -> Unit) {
    fun type(dt: DataType) = "!${dt}!"
    fun txt(node: PtNode): String {
        return when(node) {
            is PtAlign -> "%align ${node.align}"
            is PtAssignTarget -> if(node.void) "<void>" else "<target>"
            is PtAssignment -> "<assign>"
            is PtAugmentedAssign -> "<inplace-assign> ${node.operator}"
            is PtBreakpoint -> "%breakpoint"
            is PtConditionalBranch -> "if_${node.condition.name.lowercase()}"
            is PtAddressOf -> {
                if(node.isMsbForSplitArray)
                    "&>"
                else if(node.isFromArrayElement)
                    "& array-element"
                else
                    "&"
            }
            is PtArray -> {
                val valuelist = node.children.joinToString(", ") {
                    when (it) {
                        is PtBool -> it.toString()
                        is PtNumber -> it.number.toString()
                        is PtIdentifier -> it.name
                        is PtAddressOf -> {
                            if(it.identifier!=null)
                                "& ${it.identifier!!.name}"
                            else
                                "& ${txt(it.dereference!!)}"
                        }
                        else -> "invalid array element $it"
                    }
                }
                "array len=${node.children.size} ${type(node.type)} [ $valuelist ]"
            }
            is PtArrayIndexer -> "<arrayindexer> ${type(node.type)} ${if(node.splitWords) "[splitwords]" else ""}"
            is PtBinaryExpression -> "<expr> ${node.operator} ${type(node.type)}"
            is PtBuiltinFunctionCall -> {
                if(node.name=="structalloc") {
                    node.type.subType!!.scopedNameString+"()"
                } else {
                    val str = if (node.void) "void " else ""
                    str + node.name + "()"
                }
            }
            is PtContainmentCheck -> "in"
            is PtFunctionCall -> {
                val str = if(node.void) "void " else ""
                str + node.name + "()"
            }
            is PtIdentifier -> "${node.name} ${type(node.type)}"
            is PtIrRegister -> "IRREG#${node.register} ${type(node.type)}"
            is PtMemoryByte -> "@()"
            is PtNumber -> {
                val numstr = if(node.type.isFloat) node.number.toString() else node.number.toHex()
                "$numstr ${type(node.type)}"
            }
            is PtBool -> node.value.toString()
            is PtPrefix -> node.operator
            is PtRange -> "<range>"
            is PtString -> "\"${node.value.escape()}\""
            is PtTypeCast -> "as ${node.type}"
            is PtForLoop -> "for"
            is PtIfElse -> "ifelse"
            is PtIncludeBinary -> "%incbin '${node.file}', ${node.offset}, ${node.length}"
            is PtInlineAssembly -> {
                if(node.isIR)
                    "%ir {{ ...${node.assembly.length} characters... }}"
                else
                    "%asm {{ ...${node.assembly.length} characters... }}"
            }
            is PtJump -> "goto"
            is PtAsmSub -> {
                val params = node.parameters.joinToString(", ") {
                    val register = it.first.registerOrPair
                    val statusflag = it.first.statusflag
                    "${it.second.type} ${it.second.name} @${register ?: statusflag}"
                }
                val clobbers = if (node.clobbers.isEmpty()) "" else "clobbers ${node.clobbers}"
                val returns = if (node.returns.isEmpty()) "" else {
                    "-> ${node.returns.joinToString(", ") {
                            val register = it.first.registerOrPair
                            val statusflag = it.first.statusflag
                            "${it.second} @${register ?: statusflag}"
                        }
                    }"
                }
                val str = if (node.inline) "inline " else ""
                if(node.address == null) {
                    str + "asmsub ${node.name}($params) $clobbers $returns"
                } else {
                    val bank = if(node.address.constbank!=null) "@bank ${node.address.constbank}"
                        else if(node.address.varbank!=null) "@bank ${node.address.varbank?.name}"
                        else ""
                    str + "extsub $bank ${node.address.address.toHex()} = ${node.name}($params) $clobbers $returns"
                }
            }
            is PtBlock -> {
                val addr = if(node.options.address==null) "" else "@${node.options.address.toHex()}"
                "\nblock '${node.name}' $addr"
            }
            is PtConstant -> {
                val value = when {
                    node.type.isBool -> if(node.value==0.0) "false" else "true"
                    node.type.isInteger -> node.value.toInt().toString()
                    else -> node.value.toString()
                }
                "const ${node.type} ${node.name} = $value"
            }
            is PtLabel -> "${node.name}:"
            is PtMemMapped -> {
                if(node.type.isArray) {
                    val arraysize = if(node.arraySize==null) "" else node.arraySize.toString()
                    "&${node.type.elementType()}[$arraysize] ${node.name} = ${node.address.toHex()}"
                } else {
                    "&${node.type} ${node.name} = ${node.address.toHex()}"
                }
            }
            is PtSub -> {
                val params = node.signature.children.joinToString(", ") {
                    it as PtSubroutineParameter
                    val reg = if(it.register!=null) "@${it.register}" else ""
                    "${it.type} ${it.name} $reg"
                }
                var str = "sub ${node.name}($params) "
                if(node.signature.returns.isNotEmpty())
                    str += "-> ${node.signature.returns.joinToString(",")}"
                str
            }
            is PtVariable -> {
                val split = if(node.type.isSplitWordArray) "" else "@nosplit"
                val align = when(node.align) {
                    0u -> ""
                    2u -> "@alignword"
                    64u -> "@align64"
                    256u -> "@alignpage"
                    else -> throw IllegalArgumentException("invalid alignment size")
                }
                val str = when {
                    node.arraySize!=null -> {
                        if (node.type.isArray) {
                            val eltType = node.type.elementType()
                            "${eltType}[${node.arraySize}] $split $align ${node.name}"
                        }
                        else throw IllegalArgumentException("invalid array dt")
                    }
                    node.type.isArray -> {
                        val eltType = node.type.elementType()
                        "${eltType}[] $split $align ${node.name}"
                    }
                    else -> "${node.type} ${node.name}"
                }
                if(node.value!=null)
                    str + " = " + txt(node.value)
                else
                    str
            }
            is PtNodeGroup -> if(node.children.isNotEmpty()) "<group>" else ""
            is PtNop -> "nop"
            is PtProgram -> "PROGRAM ${node.name}"
            is PtRepeatLoop -> "repeat"
            is PtReturn -> "return"
            is PtSubroutineParameter -> {
                val reg = if(node.register!=null) "@${node.register}" else ""
                "${node.type} ${node.name} $reg"
            }
            is PtSubSignature -> "(signature)"
            is PtWhen -> "when"
            is PtWhenChoice -> {
                if(node.isElse)
                    "else"
                else
                    "->"
            }
            is PtDefer -> "<defer>"
            is PtIfExpression -> "<ifexpr>"
            is PtJmpTable -> "<jmptable>"
            is PtStructDecl -> {
                "struct ${node.name} { " + node.fields.joinToString("  ") { "${it.first} ${it.second}" } + " }"
            }
            is PtPointerDeref -> {
                val chain = if(node.chain.isEmpty()) "" else "${node.chain}"
                val field = if(node.field==null) "" else ".${node.field}"
                "deref  {child} $chain $field ${type(node.type)}"
            }
        }
    }

    if(root is PtProgram) {
        output(txt(root))
        root.children.forEach {
            walkAst(it) { node, depth ->
                val txt = txt(node)
                val library = if(node is PtBlock) node.library else node.definingBlock()?.library==true
                if(!library || !skipLibraries) {
                    if (txt.isNotEmpty())
                        output("    ".repeat(depth) + txt(node))
                }
            }
        }
        println()
    } else {
        walkAst(root) { node, depth ->
            val txt = txt(node)
            val library = if(node is PtBlock) node.library else node.definingBlock()?.library==true
            if(!library || !skipLibraries) {
                if (txt.isNotEmpty())
                    output("    ".repeat(depth) + txt(node))
            }
        }
    }
}

fun walkAst(root: PtNode, act: (node: PtNode, depth: Int) -> Unit) {
    fun recurse(node: PtNode, depth: Int) {
        act(node, depth)
        node.children.forEach { recurse(it, depth+1) }
    }
    recurse(root, 0)
}
