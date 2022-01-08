package prog8.ast.antlr

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.parser.Prog8ANTLRParser
import prog8.parser.SourceCode
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.math.exp


/***************** Antlr Extension methods to create AST ****************/

private data class NumericLiteral(val number: Double, val datatype: DataType)


private fun ParserRuleContext.toPosition() : Position {
    val pathString = start.inputStream.sourceName
    val filename = if(SourceCode.isRegularFilesystemPath(pathString)) {
        val path = Path.of(pathString)
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
            it.labeldef()!=null -> it.labeldef().toAst()
            else -> throw FatalAstException("weird block node $it")
        }
    }
    return Block(identifier().text, integerliteral()?.toAst()?.number?.toUInt(), blockstatements.toMutableList(), isInLibrary, toPosition())
}

private fun Prog8ANTLRParser.Statement_blockContext.toAst(): MutableList<Statement> =
        statement().asSequence().map { it.toAst() }.toMutableList()

private fun Prog8ANTLRParser.VariabledeclarationContext.toAst() : Statement {
    vardecl()?.let { return it.toAst() }

    varinitializer()?.let {
        val vd = it.vardecl()
        return VarDecl(
                VarDeclType.VAR,
                vd.datatype()?.toAst() ?: DataType.UNDEFINED,
                if (vd.ZEROPAGE() != null) ZeropageWish.PREFER_ZEROPAGE else ZeropageWish.DONTCARE,
                vd.arrayindex()?.toAst(),
                vd.varname.text,
                it.expression().toAst(),
                vd.ARRAYSIG() != null || vd.arrayindex() != null,
                false,
                vd.SHARED()!=null,
                null,
                it.toPosition()
        )
    }

    constdecl()?.let {
        val cvarinit = it.varinitializer()
        val vd = cvarinit.vardecl()
        return VarDecl(
                VarDeclType.CONST,
                vd.datatype()?.toAst() ?: DataType.UNDEFINED,
                if (vd.ZEROPAGE() != null) ZeropageWish.PREFER_ZEROPAGE else ZeropageWish.DONTCARE,
                vd.arrayindex()?.toAst(),
                vd.varname.text,
                cvarinit.expression().toAst(),
                vd.ARRAYSIG() != null || vd.arrayindex() != null,
                false,
                vd.SHARED() != null,
                null,
                cvarinit.toPosition()
        )
    }

    memoryvardecl()?.let {
        val mvarinit = it.varinitializer()
        val vd = mvarinit.vardecl()
        return VarDecl(
                VarDeclType.MEMORY,
                vd.datatype()?.toAst() ?: DataType.UNDEFINED,
                if (vd.ZEROPAGE() != null) ZeropageWish.PREFER_ZEROPAGE else ZeropageWish.DONTCARE,
                vd.arrayindex()?.toAst(),
                vd.varname.text,
                mvarinit.expression().toAst(),
                vd.ARRAYSIG() != null || vd.arrayindex() != null,
                false,
                vd.SHARED()!=null,
                null,
                mvarinit.toPosition()
        )
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
        return Assignment(it.assign_target().toAst(), it.expression().toAst(), it.toPosition())
    }

    augassignment()?.let {
        // replace A += X  with  A = A + X
        val target = it.assign_target().toAst()
        val oper = it.operator.text.substringBefore('=')
        val expression = BinaryExpression(target.toExpression(), oper, it.expression().toAst(), it.expression().toPosition())
        return Assignment(it.assign_target().toAst(), expression, it.toPosition())
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

    val pipestmt = pipestmt()?.toAst()
    if(pipestmt!=null) return pipestmt

    throw FatalAstException("unprocessed source text (are we missing ast conversion rules for parser elements?): $text")
}

private fun Prog8ANTLRParser.AsmsubroutineContext.toAst(): Subroutine {
    val inline = this.inline()!=null
    val subdecl = asmsub_decl().toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf()
    return Subroutine(subdecl.name, subdecl.parameters.toMutableList(), subdecl.returntypes,
            subdecl.asmParameterRegisters, subdecl.asmReturnvaluesRegisters,
            subdecl.asmClobbers, null, true, inline, statements, toPosition())
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
                                  val statusflag: Statusflag?,
                                  val position: Position)

private fun Prog8ANTLRParser.Asmsub_returnsContext.toAst(): List<AsmSubroutineReturn>
        = asmsub_return().map {
            val register = it.register().text
            var registerorpair: RegisterOrPair? = null
            var statusregister: Statusflag? = null
            if(register!=null) {
                when (register) {
                    in RegisterOrPair.names -> registerorpair = RegisterOrPair.valueOf(register)
                    in Statusflag.names -> statusregister = Statusflag.valueOf(register)
                    else -> throw FatalAstException("invalid register or status flag in $it")
                }
            }
            AsmSubroutineReturn(
                    it.datatype().toAst(),
                    registerorpair,
                    statusregister,
                    toPosition())
        }

private fun Prog8ANTLRParser.Asmsub_paramsContext.toAst(): List<AsmSubroutineParameter>
        = asmsub_param().map {
    val vardecl = it.vardecl()
    var datatype = vardecl.datatype()?.toAst() ?: DataType.UNDEFINED
    if(vardecl.ARRAYSIG()!=null || vardecl.arrayindex()!=null)
        datatype = ElementToArrayTypes.getValue(datatype)
    val register = it.register().text
    var registerorpair: RegisterOrPair? = null
    var statusregister: Statusflag? = null
    if(register!=null) {
        when (register) {
            in RegisterOrPair.names -> registerorpair = RegisterOrPair.valueOf(register)
            in Statusflag.names -> statusregister = Statusflag.valueOf(register)
            else -> throw FatalAstException("invalid register or status flag '$register'")
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
    return InlineAssembly(text.substring(2, text.length-2), toPosition())
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
    val inline = inline()!=null
    val returntypes = sub_return_part()?.toAst() ?: emptyList()
    return Subroutine(identifier().text,
            sub_params()?.toAst()?.toMutableList() ?: mutableListOf(),
            returntypes,
            statement_block()?.toAst() ?: mutableListOf(),
            inline,
            toPosition())
}

private fun Prog8ANTLRParser.Sub_return_partContext.toAst(): List<DataType> {
    val returns = sub_returns() ?: return emptyList()
    return returns.datatype().map { it.toAst() }
}

private fun Prog8ANTLRParser.Sub_paramsContext.toAst(): List<SubroutineParameter> =
        vardecl().map {
            var datatype = it.datatype()?.toAst() ?: DataType.UNDEFINED
            if(it.ARRAYSIG()!=null || it.arrayindex()!=null)
                datatype = ElementToArrayTypes.getValue(datatype)
            SubroutineParameter(it.varname.text, datatype, it.toPosition())
        }

private fun Prog8ANTLRParser.Assign_targetContext.toAst() : AssignTarget {
    val identifier = scoped_identifier()
    return when {
        identifier!=null -> AssignTarget(identifier.toAst(), null, null, toPosition())
        arrayindexed()!=null -> AssignTarget(null, arrayindexed().toAst(), null, toPosition())
        directmemory()!=null -> AssignTarget(null, null, DirectMemoryWrite(directmemory().expression().toAst(), toPosition()), toPosition())
        else -> AssignTarget(scoped_identifier()?.toAst(), null, null, toPosition())
    }
}

private fun Prog8ANTLRParser.ClobberContext.toAst() : Set<CpuRegister> {
    val names = this.cpuregister().map { it.text }
    return names.map { CpuRegister.valueOf(it) }.toSet()
}

private fun Prog8ANTLRParser.DatatypeContext.toAst() = DataType.valueOf(text.uppercase())

private fun Prog8ANTLRParser.ArrayindexContext.toAst() : ArrayIndex =
        ArrayIndex(expression().toAst(), toPosition())

internal fun Prog8ANTLRParser.DirectiveContext.toAst() : Directive =
        Directive(directivename.text, directivearg().map { it.toAst() }, toPosition())

private fun Prog8ANTLRParser.DirectiveargContext.toAst() : DirectiveArg {
    val str = stringliteral()
    if(str?.ALT_STRING_ENCODING() != null)
        throw SyntaxError("can't use alternate string s for directive arguments", toPosition())
    return DirectiveArg(str?.text?.substring(1, text.length-1), identifier()?.text, integerliteral()?.toAst()?.number?.toUInt(), toPosition())
}

private fun Prog8ANTLRParser.IntegerliteralContext.toAst(): NumericLiteral {
    fun makeLiteral(text: String, radix: Int): NumericLiteral {
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
        return NumericLiteral(integer.toDouble(), datatype)
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
            NumericLiteralValue.fromBoolean(booleanlit, litval.toPosition())
        }
        else {
            val intLit = litval.integerliteral()?.toAst()
            when {
                intLit!=null -> when(intLit.datatype) {
                    DataType.UBYTE -> NumericLiteralValue(DataType.UBYTE, intLit.number, litval.toPosition())
                    DataType.BYTE -> NumericLiteralValue(DataType.BYTE, intLit.number, litval.toPosition())
                    DataType.UWORD -> NumericLiteralValue(DataType.UWORD, intLit.number, litval.toPosition())
                    DataType.WORD -> NumericLiteralValue(DataType.WORD, intLit.number, litval.toPosition())
                    DataType.FLOAT -> NumericLiteralValue(DataType.FLOAT, intLit.number, litval.toPosition())
                    else -> throw FatalAstException("invalid datatype for numeric literal")
                }
                litval.floatliteral()!=null -> NumericLiteralValue(DataType.FLOAT, litval.floatliteral().toAst(), litval.toPosition())
                litval.stringliteral()!=null -> litval.stringliteral().toAst()
                litval.charliteral()!=null -> litval.charliteral().toAst()
                litval.arrayliteral()!=null -> {
                    val array = litval.arrayliteral().toAst()
                    // the actual type of the arraysize can not yet be determined here (missing namespace & heap)
                    // the ConstantFold takes care of that and converts the type if needed.
                    ArrayLiteralValue(InferredTypes.InferredType.unknown(), array, position = litval.toPosition())
                }
                else -> throw FatalAstException("invalid parsed literal")
            }
        }
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
        val step = rangestep?.toAst() ?: NumericLiteralValue(DataType.UBYTE, defaultstep.toDouble(), toPosition())
        return RangeExpression(rangefrom.toAst(), rangeto.toAst(), step, toPosition())
    }

    if(childCount==3 && children[0].text=="(" && children[2].text==")")
        return expression(0).toAst()        // expression within ( )

    if(arrayindexed()!=null)
        return arrayindexed().toAst()

    if(typecast()!=null)
        return TypecastExpression(expression(0).toAst(), typecast().datatype().toAst(), false, toPosition())

    if(directmemory()!=null)
        return DirectMemoryRead(directmemory().expression().toAst(), toPosition())

    if(addressof()!=null)
        return AddressOf(addressof().scoped_identifier().toAst(), toPosition())

    if(pipe!=null)
        return PipeExpression(pipesource.toAst(), pipetarget.toAst(), toPosition())

    throw FatalAstException(text)
}

private fun Prog8ANTLRParser.CharliteralContext.toAst(): CharLiteral {
    val text = this.SINGLECHAR().text
    return CharLiteral(unescape(text.substring(1, text.length-1), toPosition())[0], this.ALT_STRING_ENCODING() != null, toPosition())
}

private fun Prog8ANTLRParser.StringliteralContext.toAst(): StringLiteralValue {
    val text=this.STRING().text
    return StringLiteralValue(unescape(text.substring(1, text.length-1), toPosition()), ALT_STRING_ENCODING() != null, toPosition())
}

private fun Prog8ANTLRParser.ArrayindexedContext.toAst(): ArrayIndexedExpression {
    return ArrayIndexedExpression(scoped_identifier().toAst(),
            arrayindex().toAst(),
            toPosition())
}

private fun Prog8ANTLRParser.Expression_listContext.toAst() = expression().map{ it.toAst() }

private fun Prog8ANTLRParser.IdentifierContext.toAst() : IdentifierReference =
        IdentifierReference(listOf(text), toPosition())

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

private fun Prog8ANTLRParser.Branch_stmtContext.toAst(): Branch {
    val branchcondition = branchcondition().toAst()
    val trueStatements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val elseStatements = else_part()?.toAst() ?: mutableListOf()
    val trueScope = AnonymousScope(trueStatements, statement_block()?.toPosition()
            ?: statement().toPosition())
    val elseScope = AnonymousScope(elseStatements, else_part()?.toPosition() ?: toPosition())
    return Branch(branchcondition, trueScope, elseScope, toPosition())
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

private fun Prog8ANTLRParser.VardeclContext.toAst(): VarDecl {
    return VarDecl(
            VarDeclType.VAR,
            datatype()?.toAst() ?: DataType.UNDEFINED,
            if(ZEROPAGE() != null) ZeropageWish.PREFER_ZEROPAGE else ZeropageWish.DONTCARE,
            arrayindex()?.toAst(),
            varname.text,
            null,
            ARRAYSIG() != null || arrayindex() != null,
            false,
            SHARED()!=null,
            null,
            toPosition()
    )
}

private fun Prog8ANTLRParser.PipestmtContext.toAst(): Pipe {
    val source = this.source.toAst()
    val target = this.target.toAst()
    return Pipe(source, target, toPosition())
}
