package prog8.code.ast

import prog8.code.core.*

/**
 * Produces readable text from a [PtNode] (AST node, usually starting with PtProgram as root),
 * passing it as a String to the specified receiver function.
 */
fun printAst(root: PtNode, output: (text: String) -> Unit) {
    fun type(dt: DataType) = "!${dt.name.lowercase()}!"
    fun txt(node: PtNode): String {
        return when(node) {
            is PtAssignTarget -> "<target>"
            is PtAssignment -> "<assign>"
            is PtAugmentedAssign -> "<inplace-assign> ${node.operator}"
            is PtBreakpoint -> "%breakpoint"
            is PtConditionalBranch -> "if_${node.condition.name.lowercase()}"
            is PtAddressOf -> "&"
            is PtArray -> "array len=${node.children.size} ${type(node.type)}"
            is PtArrayIndexer -> "<arrayindexer> ${type(node.type)}"
            is PtBinaryExpression -> "<expr> ${node.operator} ${type(node.type)}"
            is PtBuiltinFunctionCall -> {
                val str = if(node.void) "void " else ""
                str + node.name + "()"
            }
            is PtContainmentCheck -> "in"
            is PtFunctionCall -> {
                val str = if(node.void) "void " else ""
                str + node.name + "()"
            }
            is PtIdentifier -> "${node.name} ${type(node.type)}"
            is PtMachineRegister -> "VMREG#${node.register} ${type(node.type)}"
            is PtMemoryByte -> "@()"
            is PtNumber -> {
                val numstr = if(node.type == DataType.FLOAT) node.number.toString() else node.number.toHex()
                "$numstr ${type(node.type)}"
            }
            is PtPrefix -> node.operator
            is PtRange -> "<range>"
            is PtString -> "\"${node.value.escape()}\""
            is PtTypeCast -> "as ${node.type.name.lowercase()}"
            is PtForLoop -> "for"
            is PtIfElse -> "ifelse"
            is PtIncludeBinary -> "%incbin '${node.file}', ${node.offset}, ${node.length}"
            is PtInlineAssembly -> {
                if(node.isIR)
                    "%ir {{ ...${node.assembly.length} characters... }}"
                else
                    "%asm {{ ...${node.assembly.length} characters... }}"
            }
            is PtJump -> {
                if(node.identifier!=null)
                    "goto ${node.identifier.name}"
                else if(node.address!=null)
                    "goto ${node.address.toHex()}"
                else if(node.generatedLabel!=null)
                    "goto ${node.generatedLabel}"
                else
                    "???"
            }
            is PtAsmSub -> {
                val params = if (node.parameters.isEmpty()) "" else "...TODO ${node.parameters.size} PARAMS..."
                val clobbers = if (node.clobbers.isEmpty()) "" else "clobbers ${node.clobbers}"
                val returns = if (node.returns.isEmpty()) "" else (if (node.returns.size == 1) "-> ${node.returns[0].second.name.lowercase()}" else "-> ${node.returns.map { it.second.name.lowercase() }}")
                val str = if (node.inline) "inline " else ""
                if(node.address==null) {
                    str + "asmsub ${node.name}($params) $clobbers $returns"
                } else {
                    str + "romsub ${node.address.toHex()} = ${node.name}($params) $clobbers $returns"
                }
            }
            is PtBlock -> {
                val addr = if(node.address==null) "" else "@${node.address.toHex()}"
                val align = if(node.alignment==PtBlock.BlockAlignment.NONE) "" else "align=${node.alignment}"
                "\nblock '${node.name}' $addr $align"
            }
            is PtConstant -> {
                val value = if(node.type in IntegerDatatypes) node.value.toInt().toString() else node.value.toString()
                "const ${node.type.name.lowercase()} ${node.name} = $value"
            }
            is PtLabel -> "${node.name}:"
            is PtMemMapped -> {
                if(node.type in ArrayDatatypes) {
                    val arraysize = if(node.arraySize==null) "" else node.arraySize.toString()
                    val eltType = ArrayToElementTypes.getValue(node.type)
                    "&${eltType.name.lowercase()}[$arraysize] ${node.name} = ${node.address.toHex()}"
                } else {
                    "&${node.type.name.lowercase()} ${node.name} = ${node.address.toHex()}"
                }
            }
            is PtSub -> {
                val params = if (node.parameters.isEmpty()) "" else "...TODO ${node.parameters.size} PARAMS..."
                var str = "sub ${node.name}($params) "
                if(node.returntype!=null)
                    str += "-> ${node.returntype.name.lowercase()}"
                str
            }
            is PtVariable -> {
                val str = if(node.arraySize!=null) {
                    val eltType = ArrayToElementTypes.getValue(node.type)
                    "${eltType.name.lowercase()}[${node.arraySize}] ${node.name}"
                }
                else if(node.type in ArrayDatatypes) {
                    val eltType = ArrayToElementTypes.getValue(node.type)
                    "${eltType.name.lowercase()}[] ${node.name}"
                }
                else
                    "${node.type.name.lowercase()} ${node.name}"
                if(node.value!=null)
                    str + " = " + txt(node.value)
                else
                    str
            }
            is PtNodeGroup -> "<group>"
            is PtNop -> "nop"
            is PtPostIncrDecr -> "<post> ${node.operator}"
            is PtProgram -> "PROGRAM ${node.name}"
            is PtRepeatLoop -> "repeat"
            is PtReturn -> "return"
            is PtSubroutineParameter -> "${node.type.name.lowercase()} ${node.name}"
            is PtWhen -> "when"
            is PtWhenChoice -> {
                if(node.isElse)
                    "else"
                else
                    "->"
            }
            else -> throw InternalCompilerException("unrecognised ast node $node")
        }
    }

    if(root is PtProgram) {
        output(txt(root))
        root.children.forEach {
            walkAst(it) { node, depth ->
                val txt = txt(node)
                if(txt.isNotEmpty())
                    output("    ".repeat(depth) + txt(node))
            }
        }
        println()
    } else {
        walkAst(root) { node, depth ->
            val txt = txt(node)
            if(txt.isNotEmpty())
                output("    ".repeat(depth) + txt(node))
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
