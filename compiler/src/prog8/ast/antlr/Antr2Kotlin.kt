package prog8.ast.antlr

import org.antlr.v4.runtime.IntStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import java.io.CharConversionException
import java.io.File
import java.nio.file.Path
import prog8.compiler.target.c64.Petscii
import prog8.parser.CustomLexer
import prog8.parser.prog8Parser


/***************** Antlr Extension methods to create AST ****************/

private data class NumericLiteral(val number: Number, val datatype: DataType)


fun prog8Parser.ModuleContext.toAst(name: String, isLibrary: Boolean, source: Path) : Module {
    val nameWithoutSuffix = if(name.endsWith(".p8")) name.substringBeforeLast('.') else name
    return Module(nameWithoutSuffix, modulestatement().asSequence().map { it.toAst(isLibrary) }.toMutableList(), toPosition(), isLibrary, source)
}


private fun ParserRuleContext.toPosition() : Position {
    val customTokensource = this.start.tokenSource as? CustomLexer
    val filename =
            when {
                customTokensource!=null -> customTokensource.modulePath.fileName.toString()
                start.tokenSource.sourceName == IntStream.UNKNOWN_SOURCE_NAME -> "@internal@"
                else -> File(start.inputStream.sourceName).name
            }
    // note: be ware of TAB characters in the source text, they count as 1 column...
    return Position(filename, start.line, start.charPositionInLine, stop.charPositionInLine + stop.text.length)
}


private fun prog8Parser.ModulestatementContext.toAst(isInLibrary: Boolean) : IStatement {
    val directive = directive()?.toAst()
    if(directive!=null) return directive

    val block = block()?.toAst(isInLibrary)
    if(block!=null) return block

    throw FatalAstException(text)
}


private fun prog8Parser.BlockContext.toAst(isInLibrary: Boolean) : IStatement =
        Block(identifier().text, integerliteral()?.toAst()?.number?.toInt(), statement_block().toAst(), isInLibrary, toPosition())


private fun prog8Parser.Statement_blockContext.toAst(): MutableList<IStatement> =
        statement().asSequence().map { it.toAst() }.toMutableList()


private fun prog8Parser.StatementContext.toAst() : IStatement {
    vardecl()?.let { return it.toAst() }

    varinitializer()?.let {
        val vd = it.vardecl()
        return VarDecl(
                VarDeclType.VAR,
                vd.datatype()?.toAst() ?: DataType.STRUCT,
                vd.ZEROPAGE() != null,
                vd.arrayindex()?.toAst(),
                vd.varname.text,
                vd.structname?.text,
                it.expression().toAst(),
                vd.ARRAYSIG() != null || vd.arrayindex() != null,
                false,
                it.toPosition()
        )
    }

    constdecl()?.let {
        val cvarinit = it.varinitializer()
        val vd = cvarinit.vardecl()
        return VarDecl(
                VarDeclType.CONST,
                vd.datatype()?.toAst() ?: DataType.STRUCT,
                vd.ZEROPAGE() != null,
                vd.arrayindex()?.toAst(),
                vd.varname.text,
                vd.structname?.text,
                cvarinit.expression().toAst(),
                vd.ARRAYSIG() != null || vd.arrayindex() != null,
                false,
                cvarinit.toPosition()
        )
    }

    memoryvardecl()?.let {
        val mvarinit = it.varinitializer()
        val vd = mvarinit.vardecl()
        return VarDecl(
                VarDeclType.MEMORY,
                vd.datatype()?.toAst() ?: DataType.STRUCT,
                vd.ZEROPAGE() != null,
                vd.arrayindex()?.toAst(),
                vd.varname.text,
                vd.structname?.text,
                mvarinit.expression().toAst(),
                vd.ARRAYSIG() != null || vd.arrayindex() != null,
                false,
                mvarinit.toPosition()
        )
    }

    assignment()?.let {
        return Assignment(it.assign_target().toAst(), null, it.expression().toAst(), it.toPosition())
    }

    augassignment()?.let {
        return Assignment(it.assign_target().toAst(),
                it.operator.text,
                it.expression().toAst(),
                it.toPosition())
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

    val sub = subroutine()?.toAst()
    if(sub!=null) return sub

    val asm = inlineasm()?.toAst()
    if(asm!=null) return asm

    val branchstmt = branch_stmt()?.toAst()
    if(branchstmt!=null) return branchstmt

    val forloop = forloop()?.toAst()
    if(forloop!=null) return forloop

    val repeatloop = repeatloop()?.toAst()
    if(repeatloop!=null) return repeatloop

    val whileloop = whileloop()?.toAst()
    if(whileloop!=null) return whileloop

    val breakstmt = breakstmt()?.toAst()
    if(breakstmt!=null) return breakstmt

    val continuestmt = continuestmt()?.toAst()
    if(continuestmt!=null) return continuestmt

    val asmsubstmt = asmsubroutine()?.toAst()
    if(asmsubstmt!=null) return asmsubstmt

    val whenstmt = whenstmt()?.toAst()
    if(whenstmt!=null) return whenstmt

    structdecl()?.let {
        return StructDecl(it.identifier().text,
                it.vardecl().map { vd->vd.toAst() }.toMutableList(),
                toPosition())
    }

    throw FatalAstException("unprocessed source text (are we missing ast conversion rules for parser elements?): $text")
}

private fun prog8Parser.AsmsubroutineContext.toAst(): IStatement {
    val name = identifier().text
    val address = asmsub_address()?.address?.toAst()?.number?.toInt()
    val params = asmsub_params()?.toAst() ?: emptyList()
    val returns = asmsub_returns()?.toAst() ?: emptyList()
    val normalParameters = params.map { SubroutineParameter(it.name, it.type, it.position) }
    val normalReturnvalues = returns.map { it.type }
    val paramRegisters = params.map { RegisterOrStatusflag(it.registerOrPair, it.statusflag, it.stack) }
    val returnRegisters = returns.map { RegisterOrStatusflag(it.registerOrPair, it.statusflag, it.stack) }
    val clobbers = asmsub_clobbers()?.clobber()?.toAst() ?: emptySet()
    val statements = statement_block()?.toAst() ?: mutableListOf()
    return Subroutine(name, normalParameters, normalReturnvalues,
            paramRegisters, returnRegisters, clobbers, address, true, statements, toPosition())
}

private class AsmSubroutineParameter(name: String,
                                     type: DataType,
                                     val registerOrPair: RegisterOrPair?,
                                     val statusflag: Statusflag?,
                                     val stack: Boolean,
                                     position: Position) : SubroutineParameter(name, type, position)

private class AsmSubroutineReturn(val type: DataType,
                                  val registerOrPair: RegisterOrPair?,
                                  val statusflag: Statusflag?,
                                  val stack: Boolean,
                                  val position: Position)

private fun prog8Parser.ClobberContext.toAst(): Set<Register>
        = this.register().asSequence().map { it.toAst() }.toSet()


private fun prog8Parser.Asmsub_returnsContext.toAst(): List<AsmSubroutineReturn>
        = asmsub_return().map { AsmSubroutineReturn(it.datatype().toAst(), it.registerorpair()?.toAst(), it.statusregister()?.toAst(), !it.stack?.text.isNullOrEmpty(), toPosition()) }

private fun prog8Parser.Asmsub_paramsContext.toAst(): List<AsmSubroutineParameter>
        = asmsub_param().map {
    val vardecl = it.vardecl()
    val datatype = vardecl.datatype()?.toAst() ?: DataType.STRUCT
    AsmSubroutineParameter(vardecl.varname.text, datatype,
            it.registerorpair()?.toAst(),
            it.statusregister()?.toAst(),
            !it.stack?.text.isNullOrEmpty(), toPosition())
}


private fun prog8Parser.StatusregisterContext.toAst() = Statusflag.valueOf(text)


private fun prog8Parser.Functioncall_stmtContext.toAst(): IStatement {
    val location = scoped_identifier().toAst()
    return if(expression_list() == null)
        FunctionCallStatement(location, mutableListOf(), toPosition())
    else
        FunctionCallStatement(location, expression_list().toAst().toMutableList(), toPosition())
}


private fun prog8Parser.FunctioncallContext.toAst(): FunctionCall {
    val location = scoped_identifier().toAst()
    return if(expression_list() == null)
        FunctionCall(location, mutableListOf(), toPosition())
    else
        FunctionCall(location, expression_list().toAst().toMutableList(), toPosition())
}


private fun prog8Parser.InlineasmContext.toAst() =
        InlineAssembly(INLINEASMBLOCK().text, toPosition())


private fun prog8Parser.ReturnstmtContext.toAst() : Return {
    return Return(expression()?.toAst(), toPosition())
}

private fun prog8Parser.UnconditionaljumpContext.toAst(): Jump {
    val address = integerliteral()?.toAst()?.number?.toInt()
    val identifier = scoped_identifier()?.toAst()
    return Jump(address, identifier, null, toPosition())
}


private fun prog8Parser.LabeldefContext.toAst(): IStatement =
        Label(children[0].text, toPosition())


private fun prog8Parser.SubroutineContext.toAst() : Subroutine {
    return Subroutine(identifier().text,
            sub_params()?.toAst() ?: emptyList(),
            sub_return_part()?.toAst() ?: emptyList(),
            emptyList(),
            emptyList(),
            emptySet(),
            null,
            false,
            statement_block()?.toAst() ?: mutableListOf(),
            toPosition())
}

private fun prog8Parser.Sub_return_partContext.toAst(): List<DataType> {
    val returns = sub_returns() ?: return emptyList()
    return returns.datatype().map { it.toAst() }
}


private fun prog8Parser.Sub_paramsContext.toAst(): List<SubroutineParameter> =
        vardecl().map {
            val datatype = it.datatype()?.toAst() ?: DataType.STRUCT
            SubroutineParameter(it.varname.text, datatype, it.toPosition())
        }


private fun prog8Parser.Assign_targetContext.toAst() : AssignTarget {
    val register = register()?.toAst()
    val identifier = scoped_identifier()
    return when {
        register!=null -> AssignTarget(register, null, null, null, toPosition())
        identifier!=null -> AssignTarget(null, identifier.toAst(), null, null, toPosition())
        arrayindexed()!=null -> AssignTarget(null, null, arrayindexed().toAst(), null, toPosition())
        directmemory()!=null -> AssignTarget(null, null, null, DirectMemoryWrite(directmemory().expression().toAst(), toPosition()), toPosition())
        else -> AssignTarget(null, scoped_identifier()?.toAst(), null, null, toPosition())
    }
}

private fun prog8Parser.RegisterContext.toAst() = Register.valueOf(text.toUpperCase())

private fun prog8Parser.DatatypeContext.toAst() = DataType.valueOf(text.toUpperCase())

private fun prog8Parser.RegisterorpairContext.toAst() = RegisterOrPair.valueOf(text.toUpperCase())


private fun prog8Parser.ArrayindexContext.toAst() : ArrayIndex =
        ArrayIndex(expression().toAst(), toPosition())


private fun prog8Parser.DirectiveContext.toAst() : Directive =
        Directive(directivename.text, directivearg().map { it.toAst() }, toPosition())


private fun prog8Parser.DirectiveargContext.toAst() : DirectiveArg =
        DirectiveArg(stringliteral()?.text, identifier()?.text, integerliteral()?.toAst()?.number?.toInt(), toPosition())


private fun prog8Parser.IntegerliteralContext.toAst(): NumericLiteral {
    fun makeLiteral(text: String, radix: Int, forceWord: Boolean): NumericLiteral {
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
        return NumericLiteral(integer, if (forceWord) DataType.UWORD else datatype)
    }
    val terminal: TerminalNode = children[0] as TerminalNode
    val integerPart = this.intpart.text
    return when (terminal.symbol.type) {
        prog8Parser.DEC_INTEGER -> makeLiteral(integerPart, 10, wordsuffix()!=null)
        prog8Parser.HEX_INTEGER -> makeLiteral(integerPart.substring(1), 16, wordsuffix()!=null)
        prog8Parser.BIN_INTEGER -> makeLiteral(integerPart.substring(1), 2, wordsuffix()!=null)
        else -> throw FatalAstException(terminal.text)
    }
}


private fun prog8Parser.ExpressionContext.toAst() : IExpression {

    val litval = literalvalue()
    if(litval!=null) {
        val booleanlit = litval.booleanliteral()?.toAst()
        return if(booleanlit!=null) {
            LiteralValue.fromBoolean(booleanlit, litval.toPosition())
        }
        else {
            val intLit = litval.integerliteral()?.toAst()
            when {
                intLit!=null -> when(intLit.datatype) {
                    DataType.UBYTE -> LiteralValue(DataType.UBYTE, bytevalue = intLit.number.toShort(), position = litval.toPosition())
                    DataType.BYTE -> LiteralValue(DataType.BYTE, bytevalue = intLit.number.toShort(), position = litval.toPosition())
                    DataType.UWORD -> LiteralValue(DataType.UWORD, wordvalue = intLit.number.toInt(), position = litval.toPosition())
                    DataType.WORD -> LiteralValue(DataType.WORD, wordvalue = intLit.number.toInt(), position = litval.toPosition())
                    DataType.FLOAT -> LiteralValue(DataType.FLOAT, floatvalue = intLit.number.toDouble(), position = litval.toPosition())
                    else -> throw FatalAstException("invalid datatype for numeric literal")
                }
                litval.floatliteral()!=null -> LiteralValue(DataType.FLOAT, floatvalue = litval.floatliteral().toAst(), position = litval.toPosition())
                litval.stringliteral()!=null -> LiteralValue(DataType.STR, strvalue = unescape(litval.stringliteral().text, litval.toPosition()), position = litval.toPosition())
                litval.charliteral()!=null -> {
                    try {
                        LiteralValue(DataType.UBYTE, bytevalue = Petscii.encodePetscii(unescape(litval.charliteral().text, litval.toPosition()), true)[0], position = litval.toPosition())
                    } catch (ce: CharConversionException) {
                        throw SyntaxError(ce.message ?: ce.toString(), litval.toPosition())
                    }
                }
                litval.arrayliteral()!=null -> {
                    val array = litval.arrayliteral()?.toAst()
                    // the actual type of the arraysize can not yet be determined here (missing namespace & heap)
                    // the ConstantFolder takes care of that and converts the type if needed.
                    LiteralValue(DataType.ARRAY_UB, arrayvalue = array, position = litval.toPosition())
                }
                else -> throw FatalAstException("invalid parsed literal")
            }
        }
    }

    if(register()!=null)
        return RegisterExpr(register().toAst(), register().toPosition())

    if(scoped_identifier()!=null)
        return scoped_identifier().toAst()

    if(bop!=null)
        return BinaryExpression(left.toAst(), bop.text, right.toAst(), toPosition())

    if(prefix!=null)
        return PrefixExpression(prefix.text, expression(0).toAst(), toPosition())

    val funcall = functioncall()?.toAst()
    if(funcall!=null) return funcall

    if (rangefrom!=null && rangeto!=null) {
        val step = rangestep?.toAst() ?: LiteralValue(DataType.UBYTE, 1, position = toPosition())
        return RangeExpr(rangefrom.toAst(), rangeto.toAst(), step, toPosition())
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

    throw FatalAstException(text)
}


private fun prog8Parser.ArrayindexedContext.toAst(): ArrayIndexedExpression {
    return ArrayIndexedExpression(scoped_identifier().toAst(),
            arrayindex().toAst(),
            toPosition())
}


private fun prog8Parser.Expression_listContext.toAst() = expression().map{ it.toAst() }


private fun prog8Parser.IdentifierContext.toAst() : IdentifierReference =
        IdentifierReference(listOf(text), toPosition())


private fun prog8Parser.Scoped_identifierContext.toAst() : IdentifierReference =
        IdentifierReference(NAME().map { it.text }, toPosition())


private fun prog8Parser.FloatliteralContext.toAst() = text.toDouble()


private fun prog8Parser.BooleanliteralContext.toAst() = when(text) {
    "true" -> true
    "false" -> false
    else -> throw FatalAstException(text)
}


private fun prog8Parser.ArrayliteralContext.toAst() : Array<IExpression> =
        expression().map { it.toAst() }.toTypedArray()


private fun prog8Parser.If_stmtContext.toAst(): IfStatement {
    val condition = expression().toAst()
    val trueStatements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val elseStatements = else_part()?.toAst() ?: mutableListOf()
    val trueScope = AnonymousScope(trueStatements, statement_block()?.toPosition()
            ?: statement().toPosition())
    val elseScope = AnonymousScope(elseStatements, else_part()?.toPosition() ?: toPosition())
    return IfStatement(condition, trueScope, elseScope, toPosition())
}

private fun prog8Parser.Else_partContext.toAst(): MutableList<IStatement> {
    return statement_block()?.toAst() ?: mutableListOf(statement().toAst())
}


private fun prog8Parser.Branch_stmtContext.toAst(): BranchStatement {
    val branchcondition = branchcondition().toAst()
    val trueStatements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val elseStatements = else_part()?.toAst() ?: mutableListOf()
    val trueScope = AnonymousScope(trueStatements, statement_block()?.toPosition()
            ?: statement().toPosition())
    val elseScope = AnonymousScope(elseStatements, else_part()?.toPosition() ?: toPosition())
    return BranchStatement(branchcondition, trueScope, elseScope, toPosition())
}

private fun prog8Parser.BranchconditionContext.toAst() = BranchCondition.valueOf(text.substringAfter('_').toUpperCase())


private fun prog8Parser.ForloopContext.toAst(): ForLoop {
    val loopregister = register()?.toAst()
    val datatype = datatype()?.toAst()
    val zeropage = ZEROPAGE()!=null
    val loopvar = identifier()?.toAst()
    val iterable = expression()!!.toAst()
    val scope =
            if(statement()!=null)
                AnonymousScope(mutableListOf(statement().toAst()), statement().toPosition())
            else
                AnonymousScope(statement_block().toAst(), statement_block().toPosition())
    return ForLoop(loopregister, datatype, zeropage, loopvar, iterable, scope, toPosition())
}


private fun prog8Parser.ContinuestmtContext.toAst() = Continue(toPosition())

private fun prog8Parser.BreakstmtContext.toAst() = Break(toPosition())


private fun prog8Parser.WhileloopContext.toAst(): WhileLoop {
    val condition = expression().toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
            ?: statement().toPosition())
    return WhileLoop(condition, scope, toPosition())
}


private fun prog8Parser.RepeatloopContext.toAst(): RepeatLoop {
    val untilCondition = expression().toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
            ?: statement().toPosition())
    return RepeatLoop(scope, untilCondition, toPosition())
}

private fun prog8Parser.WhenstmtContext.toAst(): WhenStatement {
    val condition = expression().toAst()
    val choices = this.when_choice()?.map { it.toAst() }?.toMutableList() ?: mutableListOf()
    return WhenStatement(condition, choices, toPosition())
}

private fun prog8Parser.When_choiceContext.toAst(): WhenChoice {
    val values = expression_list()?.toAst()
    val stmt = statement()?.toAst()
    val stmt_block = statement_block()?.toAst()?.toMutableList() ?: mutableListOf()
    if(stmt!=null)
        stmt_block.add(stmt)
    val scope = AnonymousScope(stmt_block, toPosition())
    return WhenChoice(values, scope, toPosition())
}

private fun prog8Parser.VardeclContext.toAst(): VarDecl {
    return VarDecl(
            VarDeclType.VAR,
            datatype()?.toAst() ?: DataType.STRUCT,
            ZEROPAGE() != null,
            arrayindex()?.toAst(),
            varname.text,
            structname?.text,
            null,
            ARRAYSIG() != null || arrayindex() != null,
            false,
            toPosition()
    )
}

internal fun escape(str: String) = str.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

internal fun unescape(str: String, position: Position): String {
    val result = mutableListOf<Char>()
    val iter = str.iterator()
    while(iter.hasNext()) {
        val c = iter.nextChar()
        if(c=='\\') {
            val ec = iter.nextChar()
            result.add(when(ec) {
                '\\' -> '\\'
                'n' -> '\n'
                'r' -> '\r'
                '"' -> '"'
                'u' -> {
                    "${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}".toInt(16).toChar()
                }
                else -> throw SyntaxError("invalid escape char in string: \\$ec", position)
            })
        } else {
            result.add(c)
        }
    }
    return result.joinToString("")
}

