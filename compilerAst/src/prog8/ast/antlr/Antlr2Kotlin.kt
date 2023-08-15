package prog8.ast.antlr

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import prog8.ast.base.FatalAstException
import prog8.ast.base.SyntaxError
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.code.core.*
import prog8.parser.Prog8ANTLRParser
import prog8.parser.Prog8ANTLRParser.*
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile


/***************** Antlr Extension methods to create AST ****************/

private data class NumericLiteralNode(val number: Double, val datatype: DataType)


private fun ParserRuleContext.toPosition() : Position {
    val pathString = start.inputStream.sourceName
    val filename = if(SourceCode.isRegularFilesystemPath(pathString)) {
        val path = Path(pathString)
        if(path.isRegularFile()) {
            SourceCode.relative(path).toString()
        } else {
            path.toString()
        }
    } else {
        pathString
    }
    // note: beware of TAB characters in the source text, they count as 1 column...
    return Position(filename, start.line, start.charPositionInLine+1, start.charPositionInLine + 1 + start.stopIndex - start.startIndex)
}

internal fun Prog8ANTLRParser.BlockContext.toAst(isInLibrary: Boolean) : Block {
    val blockstatements = block_statement().map {
        when {
            it.variabledeclaration()!=null -> it.variabledeclaration().toAst()
            it.subroutinedeclaration()!=null -> it.subroutinedeclaration().toAst()
            it.directive()!=null -> it.directive().toAst()
            it.inlineasm()!=null -> it.inlineasm().toAst()
            it.inlineir()!=null -> it.inlineir().toAst()
            it.labeldef()!=null -> it.labeldef().toAst()
            else -> throw FatalAstException("weird block node $it")
        }
    }
    return Block(identifier().text, integerliteral()?.toAst()?.number?.toUInt(), blockstatements.toMutableList(), isInLibrary, toPosition())
}

private fun Prog8ANTLRParser.Statement_blockContext.toAst(): MutableList<Statement> =
        statement().asSequence().map { it.toAst() }.toMutableList()

private fun Prog8ANTLRParser.VariabledeclarationContext.toAst() : Statement {
    vardecl()?.let {
        return it.toAst(VarDeclType.VAR, null)
    }

    varinitializer()?.let {
        return it.vardecl().toAst(VarDeclType.VAR, it.expression().toAst())
    }

    constdecl()?.let {
        val cvarinit = it.varinitializer()
        return cvarinit.vardecl().toAst(VarDeclType.CONST, cvarinit.expression().toAst())
    }

    memoryvardecl()?.let {
        val mvarinit = it.varinitializer()
        return mvarinit.vardecl().toAst(VarDeclType.MEMORY, mvarinit.expression().toAst())
    }

    throw FatalAstException("weird variable decl $this")
}

private fun Prog8ANTLRParser.SubroutinedeclarationContext.toAst() : Subroutine {
    return when {
        subroutine()!=null -> subroutine().toAst()
        asmsubroutine()!=null -> asmsubroutine().toAst()
        romsubroutine()!=null -> romsubroutine().toAst()
        else -> throw FatalAstException("weird subroutine decl $this")
    }
}

private fun Prog8ANTLRParser.StatementContext.toAst() : Statement {
    val vardecl = variabledeclaration()?.toAst()
    if(vardecl!=null) return vardecl

    assignment()?.let {
        return Assignment(it.assign_target().toAst(), it.expression().toAst(), AssignmentOrigin.USERCODE, it.toPosition())
    }

    augassignment()?.let {
        // replace A += X  with  A = A + X
        val target = it.assign_target().toAst()
        val oper = it.operator.text.substringBefore('=')
        val expression = BinaryExpression(target.toExpression(), oper, it.expression().toAst(), it.expression().toPosition())
        return Assignment(it.assign_target().toAst(), expression, AssignmentOrigin.USERCODE, it.toPosition())
    }

    postincrdecr()?.let {
        return PostIncrDecr(it.assign_target().toAst(), it.operator.text, it.toPosition())
    }

    val directive = directive()?.toAst()
    if(directive!=null) return directive

    val label = labeldef()?.toAst()
    if(label!=null) return label

    val jump = unconditionaljump()?.toAst()
    if(jump!=null) return jump

    val fcall = functioncall_stmt()?.toAst()
    if(fcall!=null) return fcall

    val ifstmt = if_stmt()?.toAst()
    if(ifstmt!=null) return ifstmt

    val returnstmt = returnstmt()?.toAst()
    if(returnstmt!=null) return returnstmt

    val subroutine = subroutinedeclaration()?.toAst()
    if(subroutine!=null) return subroutine

    val asm = inlineasm()?.toAst()
    if(asm!=null) return asm

    val ir = inlineir()?.toAst()
    if(ir!=null) return ir

    val branchstmt = branch_stmt()?.toAst()
    if(branchstmt!=null) return branchstmt

    val forloop = forloop()?.toAst()
    if(forloop!=null) return forloop

    val untilloop = untilloop()?.toAst()
    if(untilloop!=null) return untilloop

    val whileloop = whileloop()?.toAst()
    if(whileloop!=null) return whileloop

    val repeatloop = repeatloop()?.toAst()
    if(repeatloop!=null) return repeatloop

    val breakstmt = breakstmt()?.toAst()
    if(breakstmt!=null) return breakstmt

    val whenstmt = whenstmt()?.toAst()
    if(whenstmt!=null) return whenstmt

    val unrollstmt = unrollloop()?.toAst()
    if(unrollstmt!=null) return unrollstmt

    throw FatalAstException("unprocessed source text (are we missing ast conversion rules for parser elements?): $text")
}

private fun Prog8ANTLRParser.AsmsubroutineContext.toAst(): Subroutine {
    val inline = this.inline()!=null
    val subdecl = asmsub_decl().toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf()
    return Subroutine(subdecl.name, subdecl.parameters.toMutableList(), subdecl.returntypes,
            subdecl.asmParameterRegisters, subdecl.asmReturnvaluesRegisters,
            subdecl.asmClobbers, null, true, inline, false, statements, toPosition())
}

private fun Prog8ANTLRParser.RomsubroutineContext.toAst(): Subroutine {
    val subdecl = asmsub_decl().toAst()
    val address = integerliteral().toAst().number.toUInt()
    return Subroutine(subdecl.name, subdecl.parameters.toMutableList(), subdecl.returntypes,
            subdecl.asmParameterRegisters, subdecl.asmReturnvaluesRegisters,
            subdecl.asmClobbers, address, true, inline = false, statements = mutableListOf(), position = toPosition()
    )
}

private class AsmsubDecl(val name: String,
                         val parameters: List<SubroutineParameter>,
                         val returntypes: List<DataType>,
                         val asmParameterRegisters: List<RegisterOrStatusflag>,
                         val asmReturnvaluesRegisters: List<RegisterOrStatusflag>,
                         val asmClobbers: Set<CpuRegister>)

private fun Prog8ANTLRParser.Asmsub_declContext.toAst(): AsmsubDecl {
    val name = identifier().text
    val params = asmsub_params()?.toAst() ?: emptyList()
    val returns = asmsub_returns()?.toAst() ?: emptyList()
    val clobbers = asmsub_clobbers()?.clobber()?.toAst() ?: emptySet()
    val normalParameters = params.map { SubroutineParameter(it.name, it.type, it.position) }
    val normalReturntypes = returns.map { it.type }
    val paramRegisters = params.map { RegisterOrStatusflag(it.registerOrPair, it.statusflag) }
    val returnRegisters = returns.map { RegisterOrStatusflag(it.registerOrPair, it.statusflag) }
    return AsmsubDecl(name, normalParameters, normalReturntypes, paramRegisters, returnRegisters, clobbers)
}

private class AsmSubroutineParameter(name: String,
                                     type: DataType,
                                     val registerOrPair: RegisterOrPair?,
                                     val statusflag: Statusflag?,
                                     position: Position) : SubroutineParameter(name, type, position)

private class AsmSubroutineReturn(val type: DataType,
                                  val registerOrPair: RegisterOrPair?,
                                  val statusflag: Statusflag?)

private fun Prog8ANTLRParser.Asmsub_returnsContext.toAst(): List<AsmSubroutineReturn>
        = asmsub_return().map {
            val register = it.register.text
            var registerorpair: RegisterOrPair? = null
            var statusregister: Statusflag? = null
            if(register!=null) {
                when (register) {
                    in RegisterOrPair.names -> registerorpair = RegisterOrPair.valueOf(register)
                    in Statusflag.names -> statusregister = Statusflag.valueOf(register)
                    else -> throw SyntaxError("invalid register or status flag", toPosition())
                }
            }
            AsmSubroutineReturn(
                    it.datatype().toAst(),
                    registerorpair,
                    statusregister)
        }

private fun Prog8ANTLRParser.Asmsub_paramsContext.toAst(): List<AsmSubroutineParameter>
        = asmsub_param().map {
    val vardecl = it.vardecl()
    var datatype = vardecl.datatype()?.toAst() ?: DataType.UNDEFINED
    if(vardecl.ARRAYSIG()!=null || vardecl.arrayindex()!=null)
        datatype = ElementToArrayTypes.getValue(datatype)
    val register = it.register.text
    var registerorpair: RegisterOrPair? = null
    var statusregister: Statusflag? = null
    if(register!=null) {
        when (register) {
            in RegisterOrPair.names -> registerorpair = RegisterOrPair.valueOf(register)
            in Statusflag.names -> statusregister = Statusflag.valueOf(register)
            else -> {
                val p = toPosition()
                throw SyntaxError("invalid register or status flag", Position(p.file, it.register.line, it.register.charPositionInLine, it.register.charPositionInLine+1))
            }
        }
    }
    AsmSubroutineParameter(vardecl.varname.text, datatype, registerorpair, statusregister, toPosition())
}

private fun Prog8ANTLRParser.Functioncall_stmtContext.toAst(): Statement {
    val void = this.VOID() != null
    val location = scoped_identifier().toAst()
    return if(expression_list() == null)
        FunctionCallStatement(location, mutableListOf(), void, toPosition())
    else
        FunctionCallStatement(location, expression_list().toAst().toMutableList(), void, toPosition())
}

private fun Prog8ANTLRParser.FunctioncallContext.toAst(): FunctionCallExpression {
    val location = scoped_identifier().toAst()
    return if(expression_list() == null)
        FunctionCallExpression(location, mutableListOf(), toPosition())
    else
        FunctionCallExpression(location, expression_list().toAst().toMutableList(), toPosition())
}

private fun Prog8ANTLRParser.InlineasmContext.toAst(): InlineAssembly {
    val text = INLINEASMBLOCK().text
    return InlineAssembly(text.substring(2, text.length-2), false, toPosition())
}

private fun Prog8ANTLRParser.InlineirContext.toAst(): InlineAssembly {
    val text = INLINEASMBLOCK().text
    return InlineAssembly(text.substring(2, text.length-2), true, toPosition())
}

private fun Prog8ANTLRParser.ReturnstmtContext.toAst() : Return {
    return Return(expression()?.toAst(), toPosition())
}

private fun Prog8ANTLRParser.UnconditionaljumpContext.toAst(): Jump {
    val address = integerliteral()?.toAst()?.number?.toUInt()
    val identifier = scoped_identifier()?.toAst()
    return Jump(address, identifier, null, toPosition())
}

private fun Prog8ANTLRParser.LabeldefContext.toAst(): Statement =
        Label(children[0].text, toPosition())

private fun Prog8ANTLRParser.SubroutineContext.toAst() : Subroutine {
    // non-asm subroutine
    val returntype = sub_return_part()?.datatype()?.toAst()
    return Subroutine(
        identifier().text,
        sub_params()?.toAst()?.toMutableList() ?: mutableListOf(),
        if (returntype == null) emptyList() else listOf(returntype),
        emptyList(),
        emptyList(),
        emptySet(),
        asmAddress = null,
        isAsmSubroutine = false,
        inline = false,
        statements = statement_block()?.toAst() ?: mutableListOf(),
        position = toPosition()
    )
}

private fun Prog8ANTLRParser.Sub_paramsContext.toAst(): List<SubroutineParameter> =
        vardecl().map {
            var datatype = it.datatype()?.toAst() ?: DataType.UNDEFINED
            if(it.ARRAYSIG()!=null || it.arrayindex()!=null)
                datatype = ElementToArrayTypes.getValue(datatype)
            SubroutineParameter(it.varname.text, datatype, it.toPosition())
        }

private fun Prog8ANTLRParser.Assign_targetContext.toAst() : AssignTarget {
    return when(this) {
        is IdentifierTargetContext ->
            AssignTarget(scoped_identifier().toAst(), null, null, scoped_identifier().toPosition())
        is MemoryTargetContext ->
            AssignTarget(null, null, DirectMemoryWrite(directmemory().expression().toAst(), directmemory().toPosition()), toPosition())
        is ArrayindexedTargetContext -> {
            val arrayvar = scoped_identifier().toAst()
            val index = arrayindex().toAst()
            val arrayindexed = ArrayIndexedExpression(arrayvar, index, scoped_identifier().toPosition())
            AssignTarget(null, arrayindexed, null, toPosition())
        }
        else -> throw FatalAstException("weird assign target node $this")
    }
}

private fun Prog8ANTLRParser.ClobberContext.toAst() : Set<CpuRegister> {
    val names = this.NAME().map { it.text }
    try {
        return names.map { CpuRegister.valueOf(it) }.toSet()
    } catch(ax: IllegalArgumentException) {
        throw SyntaxError("invalid pu register", toPosition())
    }
}

private fun Prog8ANTLRParser.DatatypeContext.toAst() = DataType.valueOf(text.uppercase())

private fun Prog8ANTLRParser.ArrayindexContext.toAst() : ArrayIndex =
        ArrayIndex(expression().toAst(), toPosition())

internal fun Prog8ANTLRParser.DirectiveContext.toAst() : Directive =
        Directive(directivename.text, directivearg().map { it.toAst() }, toPosition())

private fun Prog8ANTLRParser.DirectiveargContext.toAst() : DirectiveArg {
    val str = stringliteral()
    if(str?.encoding?.text!=null)
        throw SyntaxError("don't use a string encoding for directive arguments", toPosition())
    return DirectiveArg(str?.text?.substring(1, text.length-1), identifier()?.text, integerliteral()?.toAst()?.number?.toUInt(), toPosition())
}

private fun Prog8ANTLRParser.IntegerliteralContext.toAst(): NumericLiteralNode {
    fun makeLiteral(text: String, radix: Int): NumericLiteralNode {
        val integer: Int
        var datatype = DataType.UBYTE
        when (radix) {
            10 -> {
                integer = try {
                    text.toInt()
                } catch(x: NumberFormatException) {
                    throw SyntaxError("invalid decimal literal ${x.message}", toPosition())
                }
                datatype = when(integer) {
                    in 0..255 -> DataType.UBYTE
                    in -128..127 -> DataType.BYTE
                    in 0..65535 -> DataType.UWORD
                    in -32768..32767 -> DataType.WORD
                    else -> DataType.FLOAT
                }
            }
            2 -> {
                if(text.length>8)
                    datatype = DataType.UWORD
                try {
                    integer = text.toInt(2)
                } catch(x: NumberFormatException) {
                    throw SyntaxError("invalid binary literal ${x.message}", toPosition())
                }
            }
            16 -> {
                if(text.length>2)
                    datatype = DataType.UWORD
                try {
                    integer = text.toInt(16)
                } catch(x: NumberFormatException) {
                    throw SyntaxError("invalid hexadecimal literal ${x.message}", toPosition())
                }
            }
            else -> throw FatalAstException("invalid radix")
        }
        return NumericLiteralNode(integer.toDouble(), datatype)
    }
    val terminal: TerminalNode = children[0] as TerminalNode
    val integerPart = this.intpart.text
    return when (terminal.symbol.type) {
        Prog8ANTLRParser.DEC_INTEGER -> makeLiteral(integerPart, 10)
        Prog8ANTLRParser.HEX_INTEGER -> makeLiteral(integerPart.substring(1), 16)
        Prog8ANTLRParser.BIN_INTEGER -> makeLiteral(integerPart.substring(1), 2)
        else -> throw FatalAstException(terminal.text)
    }
}

private fun Prog8ANTLRParser.ExpressionContext.toAst() : Expression {

    val litval = literalvalue()
    if(litval!=null) {
        val booleanlit = litval.booleanliteral()?.toAst()
        return if(booleanlit!=null) {
            NumericLiteral.fromBoolean(booleanlit, litval.toPosition())
        }
        else {
            val intLit = litval.integerliteral()?.toAst()
            when {
                intLit!=null -> when(intLit.datatype) {
                    DataType.UBYTE -> NumericLiteral(DataType.UBYTE, intLit.number, litval.toPosition())
                    DataType.BYTE -> NumericLiteral(DataType.BYTE, intLit.number, litval.toPosition())
                    DataType.UWORD -> NumericLiteral(DataType.UWORD, intLit.number, litval.toPosition())
                    DataType.WORD -> NumericLiteral(DataType.WORD, intLit.number, litval.toPosition())
                    DataType.FLOAT -> NumericLiteral(DataType.FLOAT, intLit.number, litval.toPosition())
                    else -> throw FatalAstException("invalid datatype for numeric literal")
                }
                litval.floatliteral()!=null -> NumericLiteral(DataType.FLOAT, litval.floatliteral().toAst(), litval.toPosition())
                litval.stringliteral()!=null -> litval.stringliteral().toAst()
                litval.charliteral()!=null -> litval.charliteral().toAst()
                litval.arrayliteral()!=null -> {
                    val array = litval.arrayliteral().toAst()
                    // the actual type of the arraysize can not yet be determined here (missing namespace & heap)
                    // the ConstantFold takes care of that and converts the type if needed.
                    ArrayLiteral(InferredTypes.InferredType.unknown(), array, position = litval.toPosition())
                }
                else -> throw FatalAstException("invalid parsed literal")
            }
        }
    }

    if(arrayindex()!=null) {
        val identifier = scoped_identifier().toAst()
        val index = arrayindex().toAst()
        return ArrayIndexedExpression(identifier, index, scoped_identifier().toPosition())
    }

    if(scoped_identifier()!=null)
        return scoped_identifier().toAst()

    if(bop!=null)
        return BinaryExpression(left.toAst(), bop.text, right.toAst(), toPosition())

    if(prefix!=null)
        return PrefixExpression(prefix.text, expression(0).toAst(), toPosition())

    val funcall = functioncall()?.toAst()
    if(funcall!=null) return funcall

    if (rangefrom!=null && rangeto!=null) {
        val defaultstep = if(rto.text == "to") 1 else -1
        val step = rangestep?.toAst() ?: NumericLiteral(DataType.UBYTE, defaultstep.toDouble(), toPosition())
        return RangeExpression(rangefrom.toAst(), rangeto.toAst(), step, toPosition())
    }

    if(childCount==3 && children[0].text=="(" && children[2].text==")")
        return expression(0).toAst()        // expression within ( )

    if(typecast()!=null)
        return TypecastExpression(expression(0).toAst(), typecast().datatype().toAst(), false, toPosition())

    if(directmemory()!=null)
        return DirectMemoryRead(directmemory().expression().toAst(), toPosition())

    if(addressof()!=null)
        return AddressOf(addressof().scoped_identifier().toAst(), toPosition())

    throw FatalAstException(text)
}

private fun Prog8ANTLRParser.CharliteralContext.toAst(): CharLiteral {
    val text = this.SINGLECHAR().text
    val enc = this.encoding?.text
    val encoding =
        if(enc!=null)
            Encoding.entries.singleOrNull { it.prefix == enc }
                ?: throw SyntaxError("invalid encoding", toPosition())
        else
            Encoding.DEFAULT
    val raw = text.substring(1, text.length - 1)
    try {
        return CharLiteral.fromEscaped(raw, encoding, toPosition())
    } catch(ex: IllegalArgumentException) {
        throw SyntaxError(ex.message!!, toPosition())
    }
}

private fun Prog8ANTLRParser.StringliteralContext.toAst(): StringLiteral {
    val text=this.STRING().text
    val enc = encoding?.text
    val encoding =
        if(enc!=null)
            Encoding.entries.singleOrNull { it.prefix == enc }
                ?: throw SyntaxError("invalid encoding", toPosition())
        else
            Encoding.DEFAULT
    val raw = text.substring(1, text.length-1)
    try {
        return StringLiteral.fromEscaped(raw, encoding, toPosition())
    } catch(ex: IllegalArgumentException) {
        throw SyntaxError(ex.message!!, toPosition())
    }
}

private fun Prog8ANTLRParser.Expression_listContext.toAst() = expression().map{ it.toAst() }

private fun Prog8ANTLRParser.Scoped_identifierContext.toAst() : IdentifierReference =
        IdentifierReference(NAME().map { it.text }, toPosition())

private fun Prog8ANTLRParser.FloatliteralContext.toAst() = text.toDouble()

private fun Prog8ANTLRParser.BooleanliteralContext.toAst() = when(text) {
    "true" -> true
    "false" -> false
    else -> throw FatalAstException(text)
}

private fun Prog8ANTLRParser.ArrayliteralContext.toAst() : Array<Expression> =
        expression().map { it.toAst() }.toTypedArray()

private fun Prog8ANTLRParser.If_stmtContext.toAst(): IfElse {
    val condition = expression().toAst()
    val trueStatements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val elseStatements = else_part()?.toAst() ?: mutableListOf()
    val trueScope = AnonymousScope(trueStatements, statement_block()?.toPosition()
            ?: statement().toPosition())
    val elseScope = AnonymousScope(elseStatements, else_part()?.toPosition() ?: toPosition())
    return IfElse(condition, trueScope, elseScope, toPosition())
}

private fun Prog8ANTLRParser.Else_partContext.toAst(): MutableList<Statement> {
    return statement_block()?.toAst() ?: mutableListOf(statement().toAst())
}

private fun Prog8ANTLRParser.Branch_stmtContext.toAst(): ConditionalBranch {
    val branchcondition = branchcondition().toAst()
    val trueStatements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val elseStatements = else_part()?.toAst() ?: mutableListOf()
    val trueScope = AnonymousScope(trueStatements, statement_block()?.toPosition()
            ?: statement().toPosition())
    val elseScope = AnonymousScope(elseStatements, else_part()?.toPosition() ?: toPosition())
    return ConditionalBranch(branchcondition, trueScope, elseScope, toPosition())
}

private fun Prog8ANTLRParser.BranchconditionContext.toAst() = BranchCondition.valueOf(
    text.substringAfter('_').uppercase()
)

private fun Prog8ANTLRParser.ForloopContext.toAst(): ForLoop {
    val loopvar = scoped_identifier().toAst()
    val iterable = expression()!!.toAst()
    val scope =
            if(statement()!=null)
                AnonymousScope(mutableListOf(statement().toAst()), statement().toPosition())
            else
                AnonymousScope(statement_block().toAst(), statement_block().toPosition())
    return ForLoop(loopvar, iterable, scope, toPosition())
}

private fun Prog8ANTLRParser.BreakstmtContext.toAst() = Break(toPosition())

private fun Prog8ANTLRParser.WhileloopContext.toAst(): WhileLoop {
    val condition = expression().toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
            ?: statement().toPosition())
    return WhileLoop(condition, scope, toPosition())
}

private fun Prog8ANTLRParser.RepeatloopContext.toAst(): RepeatLoop {
    val iterations = expression()?.toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
            ?: statement().toPosition())
    return RepeatLoop(iterations, scope, toPosition())
}

private fun Prog8ANTLRParser.UnrollloopContext.toAst(): UnrollLoop {
    val iterations = integerliteral().toAst().number.toInt()
    val statements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
        ?: statement().toPosition())
    return UnrollLoop(iterations, scope, toPosition())
}

private fun Prog8ANTLRParser.UntilloopContext.toAst(): UntilLoop {
    val untilCondition = expression().toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
            ?: statement().toPosition())
    return UntilLoop(scope, untilCondition, toPosition())
}

private fun Prog8ANTLRParser.WhenstmtContext.toAst(): When {
    val condition = expression().toAst()
    val choices = this.when_choice()?.map { it.toAst() }?.toMutableList() ?: mutableListOf()
    return When(condition, choices, toPosition())
}

private fun Prog8ANTLRParser.When_choiceContext.toAst(): WhenChoice {
    val values = expression_list()?.toAst()
    val stmt = statement()?.toAst()
    val stmtBlock = statement_block()?.toAst()?.toMutableList() ?: mutableListOf()
    if(stmt!=null)
        stmtBlock.add(stmt)
    val scope = AnonymousScope(stmtBlock, toPosition())
    return WhenChoice(values?.toMutableList(), scope, toPosition())
}

private fun Prog8ANTLRParser.VardeclContext.toAst(type: VarDeclType, value: Expression?): VarDecl {
    val options = decloptions()
    val zp = when {
        options.ZEROPAGEREQUIRE().isNotEmpty() -> ZeropageWish.REQUIRE_ZEROPAGE
        options.ZEROPAGE().isNotEmpty() -> ZeropageWish.PREFER_ZEROPAGE
        else -> ZeropageWish.DONTCARE
    }
    return VarDecl(
            type, VarDeclOrigin.USERCODE,
            datatype()?.toAst() ?: DataType.UNDEFINED,
            zp,
            arrayindex()?.toAst(),
            varname.text,
            value,
            ARRAYSIG() != null || arrayindex() != null,
            options.SHARED().isNotEmpty(),
            options.SPLIT().isNotEmpty(),
            toPosition()
    )
}
