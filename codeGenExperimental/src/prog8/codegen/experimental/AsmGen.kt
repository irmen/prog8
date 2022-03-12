package prog8.codegen.experimental

import prog8.code.SymbolTable
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


class AsmGen(internal val program: PtProgram,
             internal val errors: IErrorReporter,
             internal val symbolTable: SymbolTable,
             internal val options: CompilationOptions
): IAssemblyGenerator {

    private lateinit var xml: IndentingXmlWriter

    override fun compileToAssembly(): IAssemblyProgram? {

        println("\n** experimental code generator **\n")

        val writer = (options.outputDir / Path(program.name+"-ast.xml")).toFile().printWriter()
        xml = IndentingXmlWriter(XMLOutputFactory.newFactory().createXMLStreamWriter(writer))
        xml.doc()
        xml.elt("program")
        xml.attr("name", program.name)
        xml.startChildren()
        write(program.options)
        program.children.forEach { writeNode(it) }
        xml.endElt()
        xml.endDoc()
        xml.close()
        println("..todo: create assembly code into ${options.outputDir.toAbsolutePath()}..")
        return AssemblyProgram("dummy")
    }

    private fun write(options: ProgramOptions) {
        xml.elt("options")
        xml.attr("output", options.output.name)
        xml.attr("launcher", options.launcher.name)
        xml.attr("zeropage", options.zeropage.name)
        if(options.loadAddress!=null)
            xml.attr("loadaddress", options.loadAddress.toString())
        xml.attr("floatsenabled", options.floatsEnabled.toString())
        xml.attr("nosysinit", options.noSysInit.toString())
        xml.attr("dontreinitglobals", options.dontReinitGlobals.toString())
        xml.attr("optimize", options.optimize.toString())
        if(options.zpReserved.isNotEmpty()) {
            xml.startChildren()
            options.zpReserved.forEach {
                xml.elt("zpreserved")
                xml.attr("from", it.first.toString())
                xml.attr("to", it.last.toString())
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
            is PtArrayLiteral -> write(it)
            is PtBinaryExpression -> write(it)
            is PtBuiltinFunctionCall -> write(it)
            is PtConditionalBranch -> write(it)
            is PtContainmentCheck -> write(it)
            is PtForLoop -> write(it)
            is PtFunctionCall -> write(it)
            is PtGosub -> write(it)
            is PtIdentifier -> write(it)
            is PtIfElse -> write(it)
            is PtInlineAssembly -> write(it)
            is PtInlineBinary -> write(it)
            is PtJump -> write(it)
            is PtMemoryByte -> write(it)
            is PtMemMapped -> write(it)
            is PtNumber -> write(it)
            is PtPipe -> write(it)
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
            is PtNodeGroup -> it.children.forEach { writeNode(it) }
            else -> TODO("$it")
        }
    }

    private fun write(breakPt: PtBreakpoint) {
        xml.elt("breakpoint")
        xml.startChildren()
        xml.pos(breakPt.position)
        xml.endElt()
    }

    private fun write(pipe: PtPipe) {
        xml.elt("pipe")
        xml.attr("type", pipe.type.name)
        xml.startChildren()
        pipe.children.forEach { writeNode(it) }
        xml.endElt()
    }

    private fun write(array: PtArrayLiteral) {
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
        xml.writeTextNode("string", listOf(Pair("encoding", string.encoding.name)), string.value)

    private fun write(rept: PtRepeatLoop) {
        xml.elt("repeat")
        xml.startChildren()
        xml.pos(rept.position)
        xml.elt("count")
        xml.startChildren()
        writeNode(rept.count)
        xml.endElt()
        xml.elt("statements")
        writeNode(rept.statements)
        xml.endElt()
        xml.endElt()
    }

    private fun write(branch: PtConditionalBranch) {
        xml.elt("conditionalbranch")
        xml.attr("condition", branch.condition.name)
        xml.startChildren()
        xml.pos(branch.position)
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
        writeNode(check.element)
        xml.endElt()
        xml.elt("iterable")
        xml.startChildren()
        writeNode(check.iterable)
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
        xml.attr("var", strTargetName(forLoop.variable))
        xml.startChildren()
        xml.pos(forLoop.position)
        xml.elt("iterable")
        xml.startChildren()
        writeNode(forLoop.iterable)
        xml.endElt()
        xml.elt("statements")
        xml.startChildren()
        writeNode(forLoop.statements)
        xml.endElt()
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
        xml.startChildren()
        xml.pos(whenStmt.position)
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
        xml.elt("statements")
        xml.startChildren()
        writeNode(choice.statements)
        xml.endElt()
        xml.endElt()
    }

    private fun write(inlineAsm: PtInlineAssembly) {
        xml.elt("assembly")
        xml.startChildren()
        xml.pos(inlineAsm.position)
        xml.writeTextNode("code", emptyList(), inlineAsm.assembly)
        xml.endElt()
    }

    private fun write(inlineBinary: PtInlineBinary) {
        xml.elt("binary")
        xml.attr("filename", inlineBinary.file.absolutePathString())
        if(inlineBinary.offset!=null)
            xml.attr("offset", inlineBinary.offset!!.toString())
        if(inlineBinary.length!=null)
            xml.attr("length", inlineBinary.length!!.toString())
        xml.startChildren()
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
        xml.elt("addrof")
        xml.startChildren()
        write(addrof.identifier)
        xml.endElt()
    }

    private fun write(fcall: PtFunctionCall) {
        xml.elt("fcall")
        xml.attr("name", strTargetName(fcall))
        if(fcall.void)
            xml.attr("type", "VOID")
        else
            xml.attr("type", fcall.type.name)
        xml.startChildren()
        xml.pos(fcall.position)
        fcall.children.forEach { writeNode(it) }
        xml.endElt()
    }

    private fun write(number: PtNumber) {
        xml.elt("number")
        xml.attr("type", number.type.name)
        xml.attr("value", intOrDouble(number.type, number.number).toString())
        xml.endElt()
    }

    private fun write(symbol: PtIdentifier) {
        xml.elt("symbol")
        xml.attr("name", strTargetName(symbol))
        xml.attr("type", symbol.type.name)
        xml.endElt()
    }

    private fun write(assign: PtAssignment) {
        xml.elt("assign")
        xml.attr("aug", assign.augmentable.toString())
        xml.startChildren()
        xml.pos(assign.position)
        write(assign.target)
        writeNode(assign.value)
        xml.endElt()
    }

    private fun write(ifElse: PtIfElse) {
        xml.elt("ifelse")
        xml.startChildren()
        xml.pos(ifElse.position)
        xml.elt("condition")
        xml.startChildren()
        writeNode(ifElse.condition)
        xml.endElt()
        xml.elt("true")
        xml.startChildren()
        xml.pos(ifElse.ifScope.position)
        writeNode(ifElse.ifScope)
        xml.endElt()
        if(ifElse.elseScope.children.isNotEmpty()) {
            xml.elt("false")
            xml.startChildren()
            xml.pos(ifElse.elseScope.position)
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
        xml.attr("name", label.name)
        xml.startChildren()
        xml.pos(label.position)
        xml.endElt()
    }

    private fun write(block: PtBlock) {
        xml.elt("block")
        xml.attr("name", block.name)
        if(block.address!=null)
            xml.attr("address", block.address!!.toString())
        xml.attr("library", block.library.toString())
        xml.startChildren()
        xml.pos(block.position)
        block.children.forEach { writeNode(it) }
        xml.endElt()
    }

    private fun write(memMapped: PtMemMapped) {
        xml.elt("memvar")
        xml.attr("type", memMapped.type.name)
        xml.attr("name", memMapped.name)
        xml.attr("address", memMapped.address.toString())
        xml.endElt()
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

    private fun write(gosub: PtGosub) {
        xml.elt("gosub")
        if(gosub.identifier!=null) xml.attr("symbol", strTargetName(gosub.identifier!!))
        else if(gosub.address!=null) xml.attr("address", gosub.address!!.toString())
        else if(gosub.generatedLabel!=null) xml.attr("label", gosub.generatedLabel!!)
        else
            throw InternalCompilerException("weird jump target")
        xml.endElt()
    }

    private fun write(sub: PtSub) {
        xml.elt("sub")
        xml.attr("name", sub.name)
        if(sub.inline)
            xml.attr("inline", "true")
        xml.attr("returntype", sub.returntype?.toString() ?: "VOID")
        xml.startChildren()
        xml.pos(sub.position)
        if(sub.parameters.isNotEmpty()) {
            xml.elt("parameters")
            xml.startChildren()
            sub.parameters.forEach { write(it) }
            xml.endElt()
        }
        xml.elt("statements")
        xml.startChildren()
        sub.children.forEach { writeNode(it) }
        xml.endElt()
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
            xml.attr("name", asmSub.name)
            xml.attr("address", asmSub.address!!.toString())
            if(asmSub.inline)
                xml.attr("inline", "true")
            xml.startChildren()
            xml.pos(asmSub.position)
            paramsEtcetera(asmSub)
            xml.endElt()
        }
        else {
            xml.elt("asmsub")
            xml.attr("name", asmSub.name)
            if(asmSub.inline)
                xml.attr("inline", "true")
            xml.startChildren()
            xml.pos(asmSub.position)
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
            xml.attr("registers", asmSub.clobbers.map {it.name}.joinToString(","))
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
        xml.elt("const")
        xml.attr("name", constant.name)
        xml.attr("type", constant.type.name)
        xml.attr("value", intOrDouble(constant.type, constant.value).toString())
        xml.endElt()
    }

    private fun write(variable: PtVariable) {
        // TODO get this from the AST only?
        xml.elt("var")
        xml.attr("name", variable.name)
        xml.attr("type", variable.type.name)
        if(variable.value!=null) {
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