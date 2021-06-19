package prog8.ast.antlr

import org.antlr.v4.runtime.IntStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import prog8.ast.IStringEncoding
import prog8.ast.Module
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.parser.Prog8ANTLRParser
import java.io.CharConversionException
import java.io.File
import java.nio.file.Path


/***************** Antlr Extension methods to create AST ****************/

private data class NumericLiteral(val number: Number, val datatype: DataType)

internal fun Prog8ANTLRParser.ModuleContext.toAst(name: String, source: Path, encoding: IStringEncoding) : Module {
    val nameWithoutSuffix = if(name.endsWith(".p8")) name.substringBeforeLast('.') else name
    val directives = this.directive().map { it.toAst() }
    val blocks = this.block().map { it.toAst(Module.isLibrary(source), encoding) }
    return Module(nameWithoutSuffix, (directives + blocks).toMutableList(), toPosition(), source)
}

private fun ParserRuleContext.toPosition() : Position {
    /*
    val customTokensource = this.start.tokenSource as? CustomLexer
    val filename =
            when {
                customTokensource!=null -> customTokensource.modulePath.toString()
                start.tokenSource.sourceName == IntStream.UNKNOWN_SOURCE_NAME -> "@internal@"
                else -> File(start.inputStream.sourceName).name
            }
    */
    val filename = start.inputStream.sourceName

    // note: be ware of TAB characters in the source text, they count as 1 column...
    return Position(filename, start.line, start.charPositionInLine, stop.charPositionInLine + stop.text.length)
}

private fun Prog8ANTLRParser.BlockContext.toAst(isInLibrary: Boolean, encoding: IStringEncoding) : Statement {
    val blockstatements = block_statement().map {
        when {
            it.variabledeclaration()!=null -> it.variabledeclaration().toAst(encoding)
            it.subroutinedeclaration()!=null -> it.subroutinedeclaration().toAst(encoding)
            it.directive()!=null -> it.directive().toAst()
            it.inlineasm()!=null -> it.inlineasm().toAst()
            it.labeldef()!=null -> it.labeldef().toAst()
            else -> throw FatalAstException("weird block node $it")
        }
    }
    return Block(identifier().text, integerliteral()?.toAst()?.number?.toInt(), blockstatements.toMutableList(), isInLibrary, toPosition())
}

private fun Prog8ANTLRParser.Statement_blockContext.toAst(encoding: IStringEncoding): MutableList<Statement> =
        statement().asSequence().map { it.toAst(encoding) }.toMutableList()

private fun Prog8ANTLRParser.VariabledeclarationContext.toAst(encoding: IStringEncoding) : Statement {
    vardecl()?.let { return it.toAst(encoding) }

    varinitializer()?.let {
        val vd = it.vardecl()
        return VarDecl(
                VarDeclType.VAR,
                vd.datatype()?.toAst() ?: DataType.UNDEFINED,
                if (vd.ZEROPAGE() != null) ZeropageWish.PREFER_ZEROPAGE else ZeropageWish.DONTCARE,
                vd.arrayindex()?.toAst(encoding),
                vd.varname.text,
                it.expression().toAst(encoding),
                vd.ARRAYSIG() != null || vd.arrayindex() != null,
                false,
                vd.SHARED()!=null,
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
                vd.arrayindex()?.toAst(encoding),
                vd.varname.text,
                cvarinit.expression().toAst(encoding),
                vd.ARRAYSIG() != null || vd.arrayindex() != null,
                false,
                vd.SHARED() != null,
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
                vd.arrayindex()?.toAst(encoding),
                vd.varname.text,
                mvarinit.expression().toAst(encoding),
                vd.ARRAYSIG() != null || vd.arrayindex() != null,
                false,
                vd.SHARED()!=null,
                mvarinit.toPosition()
        )
    }

    throw FatalAstException("weird variable decl $this")
}

private fun Prog8ANTLRParser.SubroutinedeclarationContext.toAst(encoding: IStringEncoding) : Subroutine {
    return when {
        subroutine()!=null -> subroutine().toAst(encoding)
        asmsubroutine()!=null -> asmsubroutine().toAst(encoding)
        romsubroutine()!=null -> romsubroutine().toAst()
        else -> throw FatalAstException("weird subroutine decl $this")
    }
}

private fun Prog8ANTLRParser.StatementContext.toAst(encoding: IStringEncoding) : Statement {
    val vardecl = variabledeclaration()?.toAst(encoding)
    if(vardecl!=null) return vardecl

    assignment()?.let {
        return Assignment(it.assign_target().toAst(encoding), it.expression().toAst(encoding), it.toPosition())
    }

    augassignment()?.let {
        // replace A += X  with  A = A + X
        val target = it.assign_target().toAst(encoding)
        val oper = it.operator.text.substringBefore('=')
        val expression = BinaryExpression(target.toExpression(), oper, it.expression().toAst(encoding), it.expression().toPosition())
        return Assignment(it.assign_target().toAst(encoding), expression, it.toPosition())
    }

    postincrdecr()?.let {
        return PostIncrDecr(it.assign_target().toAst(encoding), it.operator.text, it.toPosition())
    }

    val directive = directive()?.toAst()
    if(directive!=null) return directive

    val label = labeldef()?.toAst()
    if(label!=null) return label

    val jump = unconditionaljump()?.toAst()
    if(jump!=null) return jump

    val fcall = functioncall_stmt()?.toAst(encoding)
    if(fcall!=null) return fcall

    val ifstmt = if_stmt()?.toAst(encoding)
    if(ifstmt!=null) return ifstmt

    val returnstmt = returnstmt()?.toAst(encoding)
    if(returnstmt!=null) return returnstmt

    val subroutine = subroutinedeclaration()?.toAst(encoding)
    if(subroutine!=null) return subroutine

    val asm = inlineasm()?.toAst()
    if(asm!=null) return asm

    val branchstmt = branch_stmt()?.toAst(encoding)
    if(branchstmt!=null) return branchstmt

    val forloop = forloop()?.toAst(encoding)
    if(forloop!=null) return forloop

    val untilloop = untilloop()?.toAst(encoding)
    if(untilloop!=null) return untilloop

    val whileloop = whileloop()?.toAst(encoding)
    if(whileloop!=null) return whileloop

    val repeatloop = repeatloop()?.toAst(encoding)
    if(repeatloop!=null) return repeatloop

    val breakstmt = breakstmt()?.toAst()
    if(breakstmt!=null) return breakstmt

    val whenstmt = whenstmt()?.toAst(encoding)
    if(whenstmt!=null) return whenstmt

    throw FatalAstException("unprocessed source text (are we missing ast conversion rules for parser elements?): $text")
}

private fun Prog8ANTLRParser.AsmsubroutineContext.toAst(encoding: IStringEncoding): Subroutine {
    val inline = this.inline()!=null
    val subdecl = asmsub_decl().toAst()
    val statements = statement_block()?.toAst(encoding) ?: mutableListOf()
    return Subroutine(subdecl.name, subdecl.parameters, subdecl.returntypes,
            subdecl.asmParameterRegisters, subdecl.asmReturnvaluesRegisters,
            subdecl.asmClobbers, null, true, inline, statements, toPosition())
}

private fun Prog8ANTLRParser.RomsubroutineContext.toAst(): Subroutine {
    val subdecl = asmsub_decl().toAst()
    val address = integerliteral().toAst().number.toInt()
    return Subroutine(subdecl.name, subdecl.parameters, subdecl.returntypes,
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
    val datatype = vardecl.datatype()?.toAst() ?: DataType.UNDEFINED
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

private fun Prog8ANTLRParser.Functioncall_stmtContext.toAst(encoding: IStringEncoding): Statement {
    val void = this.VOID() != null
    val location = scoped_identifier().toAst()
    return if(expression_list() == null)
        FunctionCallStatement(location, mutableListOf(), void, toPosition())
    else
        FunctionCallStatement(location, expression_list().toAst(encoding).toMutableList(), void, toPosition())
}

private fun Prog8ANTLRParser.FunctioncallContext.toAst(encoding: IStringEncoding): FunctionCall {
    val location = scoped_identifier().toAst()
    return if(expression_list() == null)
        FunctionCall(location, mutableListOf(), toPosition())
    else
        FunctionCall(location, expression_list().toAst(encoding).toMutableList(), toPosition())
}

private fun Prog8ANTLRParser.InlineasmContext.toAst() =
        InlineAssembly(INLINEASMBLOCK().text, toPosition())

private fun Prog8ANTLRParser.ReturnstmtContext.toAst(encoding: IStringEncoding) : Return {
    return Return(expression()?.toAst(encoding), toPosition())
}

private fun Prog8ANTLRParser.UnconditionaljumpContext.toAst(): Jump {
    val address = integerliteral()?.toAst()?.number?.toInt()
    val identifier = scoped_identifier()?.toAst()
    return Jump(address, identifier, null, toPosition())
}

private fun Prog8ANTLRParser.LabeldefContext.toAst(): Statement =
        Label(children[0].text, toPosition())

private fun Prog8ANTLRParser.SubroutineContext.toAst(encoding: IStringEncoding) : Subroutine {
    // non-asm subroutine
    val inline = inline()!=null
    val returntypes = sub_return_part()?.toAst() ?: emptyList()
    return Subroutine(identifier().text,
            sub_params()?.toAst() ?: emptyList(),
            returntypes,
            statement_block()?.toAst(encoding) ?: mutableListOf(),
            inline,
            toPosition())
}

private fun Prog8ANTLRParser.Sub_return_partContext.toAst(): List<DataType> {
    val returns = sub_returns() ?: return emptyList()
    return returns.datatype().map { it.toAst() }
}

private fun Prog8ANTLRParser.Sub_paramsContext.toAst(): List<SubroutineParameter> =
        vardecl().map {
            val datatype = it.datatype()?.toAst() ?: DataType.UNDEFINED
            SubroutineParameter(it.varname.text, datatype, it.toPosition())
        }

private fun Prog8ANTLRParser.Assign_targetContext.toAst(encoding: IStringEncoding) : AssignTarget {
    val identifier = scoped_identifier()
    return when {
        identifier!=null -> AssignTarget(identifier.toAst(), null, null, toPosition())
        arrayindexed()!=null -> AssignTarget(null, arrayindexed().toAst(encoding), null, toPosition())
        directmemory()!=null -> AssignTarget(null, null, DirectMemoryWrite(directmemory().expression().toAst(encoding), toPosition()), toPosition())
        else -> AssignTarget(scoped_identifier()?.toAst(), null, null, toPosition())
    }
}

private fun Prog8ANTLRParser.ClobberContext.toAst() : Set<CpuRegister> {
    val names = this.cpuregister().map { it.text }
    return names.map { CpuRegister.valueOf(it) }.toSet()
}

private fun Prog8ANTLRParser.DatatypeContext.toAst() = DataType.valueOf(text.uppercase())

private fun Prog8ANTLRParser.ArrayindexContext.toAst(encoding: IStringEncoding) : ArrayIndex =
        ArrayIndex(expression().toAst(encoding), toPosition())

private fun Prog8ANTLRParser.DirectiveContext.toAst() : Directive =
        Directive(directivename.text, directivearg().map { it.toAst() }, toPosition())

private fun Prog8ANTLRParser.DirectiveargContext.toAst() : DirectiveArg {
    val str = stringliteral()
    if(str?.ALT_STRING_ENCODING() != null)
        throw AstException("${toPosition()} can't use alternate string encodings for directive arguments")

    return DirectiveArg(stringliteral()?.text, identifier()?.text, integerliteral()?.toAst()?.number?.toInt(), toPosition())
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
                    throw AstException("${toPosition()} invalid decimal literal ${x.message}")
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
                    throw AstException("${toPosition()} invalid binary literal ${x.message}")
                }
            }
            16 -> {
                if(text.length>2)
                    datatype = DataType.UWORD
                try {
                    integer = text.toInt(16)
                } catch(x: NumberFormatException) {
                    throw AstException("${toPosition()} invalid hexadecimal literal ${x.message}")
                }
            }
            else -> throw FatalAstException("invalid radix")
        }
        return NumericLiteral(integer, datatype)
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

private fun Prog8ANTLRParser.ExpressionContext.toAst(encoding: IStringEncoding) : Expression {

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
                    DataType.UBYTE -> NumericLiteralValue(DataType.UBYTE, intLit.number.toShort(), litval.toPosition())
                    DataType.BYTE -> NumericLiteralValue(DataType.BYTE, intLit.number.toShort(), litval.toPosition())
                    DataType.UWORD -> NumericLiteralValue(DataType.UWORD, intLit.number.toInt(), litval.toPosition())
                    DataType.WORD -> NumericLiteralValue(DataType.WORD, intLit.number.toInt(), litval.toPosition())
                    DataType.FLOAT -> NumericLiteralValue(DataType.FLOAT, intLit.number.toDouble(), litval.toPosition())
                    else -> throw FatalAstException("invalid datatype for numeric literal")
                }
                litval.floatliteral()!=null -> NumericLiteralValue(DataType.FLOAT, litval.floatliteral().toAst(), litval.toPosition())
                litval.stringliteral()!=null -> litval.stringliteral().toAst()
                litval.charliteral()!=null -> {
                    try {
                        NumericLiteralValue(DataType.UBYTE, encoding.encodeString(
                                unescape(litval.charliteral().SINGLECHAR().text, litval.toPosition()),
                                litval.charliteral().ALT_STRING_ENCODING()!=null)[0], litval.toPosition())
                    } catch (ce: CharConversionException) {
                        throw SyntaxError(ce.message ?: ce.toString(), litval.toPosition())
                    }
                }
                litval.arrayliteral()!=null -> {
                    val array = litval.arrayliteral().toAst(encoding)
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
        return BinaryExpression(left.toAst(encoding), bop.text, right.toAst(encoding), toPosition())

    if(prefix!=null)
        return PrefixExpression(prefix.text, expression(0).toAst(encoding), toPosition())

    val funcall = functioncall()?.toAst(encoding)
    if(funcall!=null) return funcall

    if (rangefrom!=null && rangeto!=null) {
        val defaultstep = if(rto.text == "to") 1 else -1
        val step = rangestep?.toAst(encoding) ?: NumericLiteralValue(DataType.UBYTE, defaultstep, toPosition())
        return RangeExpr(rangefrom.toAst(encoding), rangeto.toAst(encoding), step, encoding, toPosition())
    }

    if(childCount==3 && children[0].text=="(" && children[2].text==")")
        return expression(0).toAst(encoding)        // expression within ( )

    if(arrayindexed()!=null)
        return arrayindexed().toAst(encoding)

    if(typecast()!=null)
        return TypecastExpression(expression(0).toAst(encoding), typecast().datatype().toAst(), false, toPosition())

    if(directmemory()!=null)
        return DirectMemoryRead(directmemory().expression().toAst(encoding), toPosition())

    if(addressof()!=null)
        return AddressOf(addressof().scoped_identifier().toAst(), toPosition())

    throw FatalAstException(text)
}

private fun Prog8ANTLRParser.StringliteralContext.toAst(): StringLiteralValue =
    StringLiteralValue(unescape(this.STRING().text, toPosition()), ALT_STRING_ENCODING()!=null, toPosition())

private fun Prog8ANTLRParser.ArrayindexedContext.toAst(encoding: IStringEncoding): ArrayIndexedExpression {
    return ArrayIndexedExpression(scoped_identifier().toAst(),
            arrayindex().toAst(encoding),
            toPosition())
}

private fun Prog8ANTLRParser.Expression_listContext.toAst(encoding: IStringEncoding) = expression().map{ it.toAst(encoding) }

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

private fun Prog8ANTLRParser.ArrayliteralContext.toAst(encoding: IStringEncoding) : Array<Expression> =
        expression().map { it.toAst(encoding) }.toTypedArray()

private fun Prog8ANTLRParser.If_stmtContext.toAst(encoding: IStringEncoding): IfStatement {
    val condition = expression().toAst(encoding)
    val trueStatements = statement_block()?.toAst(encoding) ?: mutableListOf(statement().toAst(encoding))
    val elseStatements = else_part()?.toAst(encoding) ?: mutableListOf()
    val trueScope = AnonymousScope(trueStatements, statement_block()?.toPosition()
            ?: statement().toPosition())
    val elseScope = AnonymousScope(elseStatements, else_part()?.toPosition() ?: toPosition())
    return IfStatement(condition, trueScope, elseScope, toPosition())
}

private fun Prog8ANTLRParser.Else_partContext.toAst(encoding: IStringEncoding): MutableList<Statement> {
    return statement_block()?.toAst(encoding) ?: mutableListOf(statement().toAst(encoding))
}

private fun Prog8ANTLRParser.Branch_stmtContext.toAst(encoding: IStringEncoding): BranchStatement {
    val branchcondition = branchcondition().toAst()
    val trueStatements = statement_block()?.toAst(encoding) ?: mutableListOf(statement().toAst(encoding))
    val elseStatements = else_part()?.toAst(encoding) ?: mutableListOf()
    val trueScope = AnonymousScope(trueStatements, statement_block()?.toPosition()
            ?: statement().toPosition())
    val elseScope = AnonymousScope(elseStatements, else_part()?.toPosition() ?: toPosition())
    return BranchStatement(branchcondition, trueScope, elseScope, toPosition())
}

private fun Prog8ANTLRParser.BranchconditionContext.toAst() = BranchCondition.valueOf(
    text.substringAfter('_').uppercase()
)

private fun Prog8ANTLRParser.ForloopContext.toAst(encoding: IStringEncoding): ForLoop {
    val loopvar = identifier().toAst()
    val iterable = expression()!!.toAst(encoding)
    val scope =
            if(statement()!=null)
                AnonymousScope(mutableListOf(statement().toAst(encoding)), statement().toPosition())
            else
                AnonymousScope(statement_block().toAst(encoding), statement_block().toPosition())
    return ForLoop(loopvar, iterable, scope, toPosition())
}

private fun Prog8ANTLRParser.BreakstmtContext.toAst() = Break(toPosition())

private fun Prog8ANTLRParser.WhileloopContext.toAst(encoding: IStringEncoding): WhileLoop {
    val condition = expression().toAst(encoding)
    val statements = statement_block()?.toAst(encoding) ?: mutableListOf(statement().toAst(encoding))
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
            ?: statement().toPosition())
    return WhileLoop(condition, scope, toPosition())
}

private fun Prog8ANTLRParser.RepeatloopContext.toAst(encoding: IStringEncoding): RepeatLoop {
    val iterations = expression()?.toAst(encoding)
    val statements = statement_block()?.toAst(encoding) ?: mutableListOf(statement().toAst(encoding))
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
            ?: statement().toPosition())
    return RepeatLoop(iterations, scope, toPosition())
}

private fun Prog8ANTLRParser.UntilloopContext.toAst(encoding: IStringEncoding): UntilLoop {
    val untilCondition = expression().toAst(encoding)
    val statements = statement_block()?.toAst(encoding) ?: mutableListOf(statement().toAst(encoding))
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
            ?: statement().toPosition())
    return UntilLoop(scope, untilCondition, toPosition())
}

private fun Prog8ANTLRParser.WhenstmtContext.toAst(encoding: IStringEncoding): WhenStatement {
    val condition = expression().toAst(encoding)
    val choices = this.when_choice()?.map { it.toAst(encoding) }?.toMutableList() ?: mutableListOf()
    return WhenStatement(condition, choices, toPosition())
}

private fun Prog8ANTLRParser.When_choiceContext.toAst(encoding: IStringEncoding): WhenChoice {
    val values = expression_list()?.toAst(encoding)
    val stmt = statement()?.toAst(encoding)
    val stmtBlock = statement_block()?.toAst(encoding)?.toMutableList() ?: mutableListOf()
    if(stmt!=null)
        stmtBlock.add(stmt)
    val scope = AnonymousScope(stmtBlock, toPosition())
    return WhenChoice(values?.toMutableList(), scope, toPosition())
}

private fun Prog8ANTLRParser.VardeclContext.toAst(encoding: IStringEncoding): VarDecl {
    return VarDecl(
            VarDeclType.VAR,
            datatype()?.toAst() ?: DataType.UNDEFINED,
            if(ZEROPAGE() != null) ZeropageWish.PREFER_ZEROPAGE else ZeropageWish.DONTCARE,
            arrayindex()?.toAst(encoding),
            varname.text,
            null,
            ARRAYSIG() != null || arrayindex() != null,
            false,
            SHARED()!=null,
            toPosition()
    )
}
