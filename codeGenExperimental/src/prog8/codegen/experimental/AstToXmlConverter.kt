package prog8.codegen.experimental

import prog8.code.*
import prog8.code.ast.*
import prog8.code.core.*
import javax.xml.stream.XMLOutputFactory
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

/*

    NOTE: The goal is to keep the dependencies as lean as possible! For now, we depend only on:
         - codeAst (the 'lean' new AST and the SymbolTable)
         - codeCore (various base enums and interfaces)

    This *should* be enough to build a complete code generator with. But we'll see :)

 */


class AstToXmlConverter(internal val program: PtProgram,
                        internal val symbolTable: SymbolTable,
                        internal val options: CompilationOptions
) {

    private lateinit var xml: IndentingXmlWriter

    fun writeXml() {
        val writer = (options.outputDir / Path(program.name+"-ast.xml")).toFile().printWriter()
        xml = IndentingXmlWriter(XMLOutputFactory.newFactory().createXMLStreamWriter(writer))
        xml.doc()
        xml.elt("program")
        xml.attr("name", program.name)
        xml.startChildren()
        writeOptions(options)
        program.children.forEach { writeNode(it) }
        writeSymboltable(symbolTable)
        xml.endElt()
        xml.endDoc()
        xml.close()
    }

    private fun writeSymboltable(st: SymbolTable) {
        xml.elt("symboltable")
        xml.startChildren()
        st.flat.forEach{ (name, entry) ->
            xml.elt("entry")
            xml.attr("name", name.joinToString("."))
            xml.attr("type", entry.type.name)
            xml.startChildren()
            writeStNode(entry)
            xml.endElt()
        }
        xml.endElt()
    }

    private fun writeStNode(node: StNode) {
        when(node.type) {
            StNodeType.GLOBAL,
            StNodeType.LABEL,
            StNodeType.BLOCK,
            StNodeType.BUILTINFUNC,
            StNodeType.SUBROUTINE -> {/* no additional info*/}
            StNodeType.ROMSUB -> {
                node as StRomSub
                xml.elt("romsub")
                xml.attr("address", node.address.toString())
                xml.endElt()
            }
            StNodeType.STATICVAR -> {
                node as StStaticVariable
                xml.elt("var")
                xml.attr("type", node.dt.name)
                xml.attr("zpwish", node.zpwish.name)
                if(node.length!=null)
                    xml.attr("length", node.length.toString())
                if(node.onetimeInitializationNumericValue!=null || node.onetimeInitializationArrayValue!=null || node.onetimeInitializationStringValue!=null) {
                    xml.startChildren()
                    if(node.onetimeInitializationNumericValue!=null) {
                        writeNumber(node.dt, node.onetimeInitializationNumericValue!!)
                    }
                    if(node.onetimeInitializationStringValue!=null) {
                        xml.writeTextNode(
                            "string",
                            listOf(Pair("encoding", node.onetimeInitializationStringValue!!.second.name)),
                            node.onetimeInitializationStringValue!!.first,
                            false
                        )
                    }
                    if(node.onetimeInitializationArrayValue!=null) {
                        xml.elt("array")
                        xml.startChildren()
                        val eltDt = ArrayToElementTypes.getValue(node.dt)
                        node.onetimeInitializationArrayValue!!.forEach {
                            if(it.number!=null) {
                                writeNumber(eltDt, it.number!!)
                            }
                            if(it.addressOf!=null) {
                                xml.elt("addressof")
                                xml.attr("symbol", it.addressOf!!.joinToString("."))
                                xml.endElt()
                            }
                        }
                        xml.endElt()
                    }
                }
                xml.endElt()
            }
            StNodeType.MEMVAR -> {
                node as StMemVar
                xml.writeTextNode("memvar",
                    listOf(Pair("type", node.dt.name)),
                    node.address.toString(),
                    false)
            }
            StNodeType.CONSTANT -> {
                node as StConstant
                xml.writeTextNode("const",
                    listOf(Pair("type", node.dt.name)),
                    intOrDouble(node.dt, node.value).toString(),
                    false)
            }
        }
    }

    private fun writeOptions(options: CompilationOptions) {
        xml.elt("options")
        xml.attr("target", options.compTarget.name)
        xml.attr("output", options.output.name)
        xml.attr("launcher", options.launcher.name)
        xml.attr("zeropage", options.zeropage.name)
        xml.attr("loadaddress", options.loadAddress.toString())
        xml.attr("floatsenabled", options.floats.toString())
        xml.attr("nosysinit", options.noSysInit.toString())
        xml.attr("dontreinitglobals", options.dontReinitGlobals.toString())
        xml.attr("optimize", options.optimize.toString())
        if(options.evalStackBaseAddress!=null)
            xml.attr("evalstackbase", options.evalStackBaseAddress!!.toString())
        if(options.zpReserved.isNotEmpty()) {
            xml.startChildren()
            options.zpReserved.forEach {
                xml.elt("zpreserved")
                xml.attr("from", it.first.toString())
                xml.attr("to", it.last.toString())
                xml.endElt()
            }
        }
        if(options.symbolDefs.isNotEmpty()) {
            xml.startChildren()
            options.symbolDefs.forEach { name, value ->
                xml.elt("symboldef")
                xml.attr("name", name)
                xml.attr("value", value)
                xml.endElt()
            }
        }
        xml.endElt()
    }

    private fun writeNode(it: PtNode) {
        when(it) {
            is PtBlock -> write(it)
            is PtSub -> write(it)
            is PtVariable -> write(it)
            is PtAssignment -> write(it)
            is PtConstant -> write(it)
            is PtAsmSub -> write(it)
            is PtAddressOf -> write(it)
            is PtArrayIndexer -> write(it)
            is PtArray -> write(it)
            is PtBinaryExpression -> write(it)
            is PtBuiltinFunctionCall -> write(it)
            is PtConditionalBranch -> write(it)
            is PtContainmentCheck -> write(it)
            is PtForLoop -> write(it)
            is PtFunctionCall -> write(it)
            is PtIdentifier -> write(it)
            is PtIfElse -> write(it)
            is PtInlineAssembly -> write(it)
            is PtIncludeBinary -> write(it)
            is PtJump -> write(it)
            is PtMemoryByte -> write(it)
            is PtMemMapped -> write(it)
            is PtNumber -> write(it)
            is PtPostIncrDecr -> write(it)
            is PtPrefix -> write(it)
            is PtRange -> write(it)
            is PtRepeatLoop -> write(it)
            is PtReturn -> write(it)
            is PtString -> write(it)
            is PtTypeCast -> write(it)
            is PtWhen -> write(it)
            is PtWhenChoice -> write(it)
            is PtLabel -> write(it)
            is PtNop -> {}
            is PtBreakpoint -> write(it)
            is PtScopeVarsDecls -> write(it)
            is PtNodeGroup -> it.children.forEach { writeNode(it) }
            else -> TODO("$it")
        }
    }

    private fun write(vars: PtScopeVarsDecls) {
        xml.elt("vars")
        xml.startChildren()
        vars.children.forEach { writeNode(it) }
        xml.endElt()
    }

    private fun write(breakPt: PtBreakpoint) {
        xml.elt("breakpoint")
        xml.pos(breakPt.position)
        xml.endElt()
    }

    private fun write(array: PtArray) {
        xml.elt("array")
        xml.attr("type", array.type.name)
        xml.startChildren()
        array.children.forEach { writeNode(it) }
        xml.endElt()
    }

    private fun write(prefix: PtPrefix) {
        xml.elt("prefix")
        xml.attr("op", prefix.operator)
        xml.attr("type", prefix.type.name)
        xml.startChildren()
        xml.elt("value")
        xml.startChildren()
        writeNode(prefix.value)
        xml.endElt()
        xml.endElt()
    }

    private fun write(string: PtString) =
        xml.writeTextNode("string", listOf(Pair("encoding", string.encoding.name)), string.value, false)

    private fun write(rept: PtRepeatLoop) {
        xml.elt("repeat")
        xml.pos(rept.position)
        xml.startChildren()
        xml.elt("count")
        xml.startChildren()
        writeNode(rept.count)
        xml.endElt()
        writeNode(rept.statements)
        xml.endElt()
    }

    private fun write(branch: PtConditionalBranch) {
        xml.elt("conditionalbranch")
        xml.attr("condition", branch.condition.name)
        xml.pos(branch.position)
        xml.startChildren()
        xml.elt("true")
        xml.startChildren()
        writeNode(branch.trueScope)
        xml.endElt()
        if(branch.falseScope.children.isNotEmpty()) {
            xml.elt("false")
            xml.startChildren()
            writeNode(branch.falseScope)
            xml.endElt()
        }
        xml.endElt()
    }

    private fun write(check: PtContainmentCheck) {
        xml.elt("containment")
        xml.attr("type", check.type.name)
        xml.startChildren()
        xml.elt("element")
        xml.startChildren()
        writeNode(check.children[0])
        xml.endElt()
        xml.elt("iterable")
        xml.startChildren()
        writeNode(check.children[1])
        xml.endElt()
        xml.endElt()
    }

    private fun write(range: PtRange) {
        xml.elt("range")
        xml.attr("type", range.type.name)
        xml.startChildren()
        xml.elt("from")
        xml.startChildren()
        writeNode(range.from)
        xml.endElt()
        xml.elt("to")
        xml.startChildren()
        writeNode(range.to)
        xml.endElt()
        xml.elt("step")
        xml.startChildren()
        writeNode(range.step)
        xml.endElt()
        xml.endElt()
    }

    private fun write(forLoop: PtForLoop) {
        xml.elt("for")
        xml.attr("loopvar", strTargetName(forLoop.variable))
        xml.pos(forLoop.position)
        xml.startChildren()
        xml.elt("iterable")
        xml.startChildren()
        writeNode(forLoop.iterable)
        xml.endElt()
        writeNode(forLoop.statements)
        xml.endElt()
    }

    private fun write(membyte: PtMemoryByte) {
        xml.elt("membyte")
        xml.attr("type", membyte.type.name)
        xml.startChildren()
        xml.elt("address")
        xml.startChildren()
        writeNode(membyte.address)
        xml.endElt()
        xml.endElt()
    }

    private fun write(whenStmt: PtWhen) {
        xml.elt("when")
        xml.pos(whenStmt.position)
        xml.startChildren()
        xml.elt("value")
        xml.startChildren()
        writeNode(whenStmt.value)
        xml.endElt()
        xml.elt("choices")
        xml.startChildren()
        writeNode(whenStmt.choices)
        xml.endElt()
        xml.endElt()
    }

    private fun write(choice: PtWhenChoice) {
        xml.elt("choice")
        if(choice.isElse) {
            xml.attr("else", "true")
            xml.startChildren()
        } else {
            xml.startChildren()
            xml.elt("values")
            xml.startChildren()
            writeNode(choice.values)
            xml.endElt()
        }
        writeNode(choice.statements)
        xml.endElt()
    }

    private fun write(inlineAsm: PtInlineAssembly) {
        xml.elt("assembly")
        xml.pos(inlineAsm.position)
        xml.startChildren()
        xml.writeTextNode("code", emptyList(), inlineAsm.assembly)
        xml.endElt()
    }

    private fun write(inlineBinary: PtIncludeBinary) {
        xml.elt("binary")
        xml.attr("filename", inlineBinary.file.absolutePathString())
        if(inlineBinary.offset!=null)
            xml.attr("offset", inlineBinary.offset!!.toString())
        if(inlineBinary.length!=null)
            xml.attr("length", inlineBinary.length!!.toString())
        xml.pos(inlineBinary.position)
        xml.endElt()
    }

    private fun write(fcall: PtBuiltinFunctionCall) {
        xml.elt("builtinfcall")
        xml.attr("name", fcall.name)
        if(fcall.void)
            xml.attr("type", "VOID")
        else
            xml.attr("type", fcall.type.name)
        xml.startChildren()
        fcall.children.forEach { writeNode(it) }
        xml.endElt()
    }

    private fun write(cast: PtTypeCast) {
        xml.elt("cast")
        xml.attr("type", cast.type.name)
        xml.startChildren()
        writeNode(cast.value)
        xml.endElt()
    }

    private fun write(aix: PtArrayIndexer) {
        xml.elt("arrayindexed")
        xml.attr("type", aix.type.name)
        xml.startChildren()
        write(aix.variable)
        writeNode(aix.index)
        xml.endElt()
    }

    private fun write(binexpr: PtBinaryExpression) {
        xml.elt("binexpr")
        xml.attr("op", binexpr.operator)
        xml.attr("type", binexpr.type.name)
        xml.startChildren()
        writeNode(binexpr.left)
        writeNode(binexpr.right)
        xml.endElt()
    }

    private fun write(addrof: PtAddressOf) {
        xml.elt("addressof")
        xml.attr("symbol", strTargetName(addrof.identifier))
        xml.endElt()
    }

    private fun write(fcall: PtFunctionCall) {
        xml.elt("fcall")
        xml.attr("name", strTargetName(fcall))
        if(fcall.void)
            xml.attr("type", "VOID")
        else
            xml.attr("type", fcall.type.name)
        xml.pos(fcall.position)
        xml.startChildren()
        fcall.children.forEach { writeNode(it) }
        xml.endElt()
    }

    private fun write(number: PtNumber) = writeNumber(number.type, number.number)

    private fun writeNumber(type: DataType, number: Double) =
        xml.writeTextNode("number", listOf(Pair("type", type.name)), intOrDouble(type, number).toString(), false)

    private fun write(symbol: PtIdentifier) {
        xml.elt("symbol")
        xml.attr("name", strTargetName(symbol))
        xml.attr("type", symbol.type.name)
        xml.endElt()
    }

    private fun write(assign: PtAssignment) {
        xml.elt("assign")
        xml.pos(assign.position)
        xml.startChildren()
        write(assign.target)
        writeNode(assign.value)
        xml.endElt()
    }

    private fun write(ifElse: PtIfElse) {
        xml.elt("ifelse")
        xml.pos(ifElse.position)
        xml.startChildren()
        xml.elt("condition")
        xml.startChildren()
        writeNode(ifElse.condition)
        xml.endElt()
        xml.elt("true")
        xml.pos(ifElse.ifScope.position)
        xml.startChildren()
        writeNode(ifElse.ifScope)
        xml.endElt()
        if(ifElse.elseScope.children.isNotEmpty()) {
            xml.elt("false")
            xml.pos(ifElse.elseScope.position)
            xml.startChildren()
            writeNode(ifElse.elseScope)
            xml.endElt()
        }
        xml.endElt()
    }

    private fun write(ret: PtReturn) {
        xml.elt("return")
        if(ret.hasValue) {
            xml.startChildren()
            writeNode(ret.value!!)
        }
        xml.endElt()
    }

    private fun write(incdec: PtPostIncrDecr) {
        if(incdec.operator=="++") xml.elt("inc") else xml.elt("dec")
        xml.startChildren()
        write(incdec.target)
        xml.endElt()
    }

    private fun write(label: PtLabel) {
        xml.elt("label")
        xml.attr("name", label.scopedName.joinToString("."))
        xml.pos(label.position)
        xml.endElt()
    }

    private fun write(block: PtBlock) {
        xml.elt("block")
        xml.attr("name", block.scopedName.joinToString("."))
        if(block.address!=null)
            xml.attr("address", block.address!!.toString())
        xml.attr("library", block.library.toString())
        xml.pos(block.position)
        xml.startChildren()
        block.children.forEach { writeNode(it) }
        xml.endElt()
    }

    private fun write(memMapped: PtMemMapped) {
        xml.writeTextNode("memvar",
            listOf(
                Pair("name", memMapped.scopedName.joinToString(".")),
                Pair("type", memMapped.type.name)
            ),
            memMapped.address.toString(),
            false)
    }

    private fun write(target: PtAssignTarget) {
        xml.elt("target")
        xml.startChildren()
        if(target.identifier!=null) {
            writeNode(target.identifier!!)
        } else if(target.memory!=null) {
            writeNode(target.memory!!)
        } else if(target.array!=null) {
            writeNode(target.array!!)
        } else
            throw InternalCompilerException("weird assign target")
        xml.endElt()
    }

    private fun write(jump: PtJump) {
        xml.elt("jump")
        if(jump.identifier!=null) xml.attr("symbol", strTargetName(jump.identifier!!))
        else if(jump.address!=null) xml.attr("address", jump.address!!.toString())
        else if(jump.generatedLabel!=null) xml.attr("label", jump.generatedLabel!!)
        else
            throw InternalCompilerException("weird jump target")
        xml.endElt()
    }

    private fun write(sub: PtSub) {
        xml.elt("sub")
        xml.attr("name", sub.scopedName.joinToString("."))
        if(sub.inline)
            xml.attr("inline", "true")
        xml.attr("returntype", sub.returntype?.toString() ?: "VOID")
        xml.pos(sub.position)
        xml.startChildren()
        if(sub.parameters.isNotEmpty()) {
            xml.elt("parameters")
            xml.startChildren()
            sub.parameters.forEach { write(it) }
            xml.endElt()
        }
        sub.children.forEach { writeNode(it) }
        xml.endElt()
    }

    private fun write(parameter: PtSubroutineParameter, registerOrStatusflag: RegisterOrStatusflag? = null) {
        xml.elt("param")
        xml.attr("name", parameter.name)
        xml.attr("type", parameter.type.name)
        if(registerOrStatusflag?.statusflag!=null) {
            xml.attr("statusflag", registerOrStatusflag.statusflag!!.toString())
        }
        if(registerOrStatusflag?.registerOrPair!=null){
            xml.attr("registers", registerOrStatusflag.registerOrPair!!.name)
        }
        xml.endElt()
    }

    private fun write(asmSub: PtAsmSub) {
        if(asmSub.address!=null) {
            xml.elt("romsub")
            xml.attr("name", asmSub.scopedName.joinToString("."))
            xml.attr("address", asmSub.address!!.toString())
            if(asmSub.inline)
                xml.attr("inline", "true")
            xml.pos(asmSub.position)
            xml.startChildren()
            paramsEtcetera(asmSub)
            xml.endElt()
        }
        else {
            xml.elt("asmsub")
            xml.attr("name", asmSub.scopedName.joinToString("."))
            if(asmSub.inline)
                xml.attr("inline", "true")
            xml.pos(asmSub.position)
            xml.startChildren()
            paramsEtcetera(asmSub)
            xml.elt("code")
            xml.startChildren()
            asmSub.children.forEach { writeNode(it) }
            xml.endElt()
            xml.endElt()
        }
    }

    private fun paramsEtcetera(asmSub: PtAsmSub) {
        if(asmSub.parameters.isNotEmpty()) {
            xml.elt("parameters")
            xml.startChildren()
            asmSub.parameters.forEach { (param, reg) -> write(param, reg) }
            xml.endElt()
        }
        if(asmSub.clobbers.isNotEmpty()) {
            xml.elt("clobbers")
            xml.attr("registers", asmSub.clobbers.joinToString(",") { it.name })
            xml.endElt()
        }
        if(asmSub.retvalRegisters.isNotEmpty()) {
            xml.elt("returns")
            xml.startChildren()
            asmSub.retvalRegisters.forEach {
                xml.elt("register")
                if(it.statusflag!=null)
                    xml.attr("statusflag", it.statusflag!!.toString())
                if(it.registerOrPair!=null)
                    xml.attr("registers", it.registerOrPair!!.toString())
                xml.endElt()
            }
            xml.endElt()
        }
    }

    private fun write(constant: PtConstant) {
        xml.writeTextNode("const",
            listOf(
                Pair("name", constant.scopedName.joinToString(".")),
                Pair("type", constant.type.name)
            ),
            intOrDouble(constant.type, constant.value).toString(), false)
    }

    private fun write(variable: PtVariable) {
        // the variable declaration nodes are still present in the Ast,
        // but the Symboltable should be used look up their details.
        xml.elt("vardecl")
        xml.attr("name", variable.scopedName.joinToString("."))
        xml.attr("type", variable.type.name)
        if(variable.arraySize!=null)
            xml.attr("arraysize", variable.arraySize.toString())
        if(variable.value!=null) {
            // static initialization value
            xml.startChildren()
            writeNode(variable.value!!)
        }
        xml.endElt()
    }



    private fun strTargetName(ident: PtIdentifier): String = ident.targetName.joinToString(".")

    private fun strTargetName(call: PtFunctionCall): String = call.functionName.joinToString(".")

    private fun intOrDouble(type: DataType, value: Double): Number =
        if(type in IntegerDatatypes) value.toInt() else value
}
