package prog8.ast.antlr

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode
import prog8.ast.FatalAstException
import prog8.ast.SyntaxError
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.code.core.*
import prog8.code.source.SourceCode
import prog8.parser.Prog8ANTLRParser.*
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile


/***************** Antlr Extension methods to create AST ****************/

private data class NumericLiteralNode(val number: Double, val datatype: BaseDataType)


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

internal fun BlockContext.toAst(isInLibrary: Boolean) : Block {
    val blockstatements = block_statement().map {
        when {
            it.variabledeclaration()!=null -> it.variabledeclaration().toAst()
            it.subroutinedeclaration()!=null -> it.subroutinedeclaration().toAst()
            it.directive()!=null -> it.directive().toAst()
            it.inlineasm()!=null -> it.inlineasm().toAst()
            it.inlineir()!=null -> it.inlineir().toAst()
            it.labeldef()!=null -> it.labeldef().toAst()
            it.alias()!=null -> it.alias().toAst()
            it.structdeclaration()!=null -> it.structdeclaration().toAst()
            else -> throw FatalAstException("weird block node $it")
        }
    }
    return Block(identifier().text, integerliteral()?.toAst()?.number?.toUInt(), blockstatements.toMutableList(), isInLibrary, toPosition())
}

private fun Statement_blockContext.toAst(): MutableList<Statement> =
        statement().asSequence().map { it.toAst() }.toMutableList()

private fun VariabledeclarationContext.toAst() : VarDecl {
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

private fun StructdeclarationContext.toAst(): Statement {
    val name = identifier().text
    val members = structfielddecl().map { it.toAst() }
    return StructDecl(name, members, toPosition())
}

private fun SubroutinedeclarationContext.toAst() : Subroutine {
    return when {
        subroutine()!=null -> subroutine().toAst()
        asmsubroutine()!=null -> asmsubroutine().toAst()
        extsubroutine()!=null -> extsubroutine().toAst()
        else -> throw FatalAstException("weird subroutine decl $this")
    }
}

private fun StatementContext.toAst() : Statement {
    val vardecl = variabledeclaration()?.toAst()
    if(vardecl!=null) return vardecl

    val assignment = assignment()?.toAst()
    if(assignment!=null) return assignment

    val augassign = augassignment()?.toAst()
    if(augassign!=null) return augassign

    postincrdecr()?.let {
        val tgt = it.assign_target().toAst()
        val operator = it.operator.text
        val pos = it.toPosition()
//        print("\u001b[92mINFO\u001B[0m  ")  // bright green
//        println("${pos}: ++ and -- will be removed in a future version, please use +=1 or -=1 instead.")    // .... if we decode to remove them one day
        val addSubOne = BinaryExpression(tgt.toExpression(), if(operator=="++") "+" else "-", NumericLiteral.optimalInteger(1, pos), pos)
        return Assignment(tgt, addSubOne, AssignmentOrigin.USERCODE, pos)
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

    val whenstmt = whenstmt()?.toAst()
    if(whenstmt!=null) return whenstmt

    val breakstmt = breakstmt()?.toAst()
    if(breakstmt!=null) return breakstmt

    val continuestmt = continuestmt()?.toAst()
    if(continuestmt!=null) return continuestmt

    val unrollstmt = unrollloop()?.toAst()
    if(unrollstmt!=null) return unrollstmt

    val deferstmt = defer()?.toAst()
    if(deferstmt!=null) return deferstmt

    val aliasstmt = alias()?.toAst()
    if(aliasstmt!=null) return aliasstmt

    val structdecl = structdeclaration()?.toAst()
    if(structdecl!=null) return structdecl

    throw FatalAstException("unprocessed source text (are we missing ast conversion rules for parser elements?): $text")
}

private fun AsmsubroutineContext.toAst(): Subroutine {
    val inline = this.inline()!=null
    val subdecl = asmsub_decl().toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf()
    return Subroutine(subdecl.name, subdecl.parameters.toMutableList(), subdecl.returntypes.toMutableList(),
            subdecl.asmParameterRegisters, subdecl.asmReturnvaluesRegisters,
            subdecl.asmClobbers, null, true, inline, statements = statements, position = toPosition()
    )
}

private fun ExtsubroutineContext.toAst(): Subroutine {
    val subdecl = asmsub_decl().toAst()
    val constbank = constbank?.toAst()?.number?.toUInt()?.toUByte()
    val varbank = varbank?.toAst()
    val addr = address.toAst()
    val address = Subroutine.Address(constbank, varbank, addr)
    return Subroutine(subdecl.name, subdecl.parameters.toMutableList(), subdecl.returntypes.toMutableList(),
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

private fun Asmsub_declContext.toAst(): AsmsubDecl {
    val name = identifier().text
    val params = asmsub_params()?.toAst() ?: emptyList()
    val returns = asmsub_returns()?.toAst() ?: emptyList()
    val clobbers = asmsub_clobbers()?.clobber()?.toAst() ?: emptySet()
    val normalParameters = params.map { SubroutineParameter(it.name, it.type, it.zp, it.registerOrPair, it.position) }
    val normalReturntypes = returns.map { it.type }
    val paramRegisters = params.map { RegisterOrStatusflag(it.registerOrPair, it.statusflag) }
    val returnRegisters = returns.map { RegisterOrStatusflag(it.registerOrPair, it.statusflag) }
    return AsmsubDecl(name, normalParameters, normalReturntypes, paramRegisters, returnRegisters, clobbers)
}

private class AsmSubroutineParameter(name: String,
                                     type: DataType,
                                     registerOrPair: RegisterOrPair?,
                                     val statusflag: Statusflag?,
                                     position: Position) : SubroutineParameter(name, type, ZeropageWish.DONTCARE, registerOrPair, position)

private class AsmSubroutineReturn(val type: DataType,
                                  val registerOrPair: RegisterOrPair?,
                                  val statusflag: Statusflag?)

private fun Asmsub_returnsContext.toAst(): List<AsmSubroutineReturn>
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
            AsmSubroutineReturn(it.datatype().toAst(), registerorpair, statusregister)
        }

private fun Asmsub_paramsContext.toAst(): List<AsmSubroutineParameter> = asmsub_param().map {
    val vardecl = it.vardecl()
    var datatype = vardecl.datatype()?.toAst() ?: DataType.UNDEFINED
    if(vardecl.ARRAYSIG()!=null || vardecl.arrayindex()!=null)
        datatype = datatype.elementToArray()
    val (registerorpair, statusregister) = parseParamRegister(it.register, it.toPosition())
    val identifiers = vardecl.identifier()
    if(identifiers.size>1)
        throw SyntaxError("parameter name must be singular", identifiers[0].toPosition())
    val identifiername = identifiers[0].NAME() ?: identifiers[0].UNDERSCORENAME()
    AsmSubroutineParameter(identifiername.text, datatype, registerorpair, statusregister, toPosition())
}

private fun parseParamRegister(registerTok: Token?, pos: Position): Pair<RegisterOrPair?, Statusflag?> {
    if(registerTok==null)
        return Pair(null, null)
    val register = registerTok.text
    var registerorpair: RegisterOrPair? = null
    var statusregister: Statusflag? = null
    if(register!=null) {
        when (register) {
            in RegisterOrPair.names -> registerorpair = RegisterOrPair.valueOf(register)
            in Statusflag.names -> statusregister = Statusflag.valueOf(register)
            else -> {
                throw SyntaxError("invalid register or status flag", Position(pos.file, registerTok.line, registerTok.charPositionInLine, registerTok.charPositionInLine+1))
            }
        }
    }
    return Pair(registerorpair, statusregister)
}

private fun Functioncall_stmtContext.toAst(): Statement {
    val void = this.VOID() != null
    val location = scoped_identifier().toAst()
    return if(expression_list() == null)
        FunctionCallStatement(location, mutableListOf(), void, toPosition())
    else
        FunctionCallStatement(location, expression_list().toAst().toMutableList(), void, toPosition())
}

private fun FunctioncallContext.toAst(): FunctionCallExpression {
    val location = scoped_identifier().toAst()
    return if(expression_list() == null)
        FunctionCallExpression(location, mutableListOf(), toPosition())
    else
        FunctionCallExpression(location, expression_list().toAst().toMutableList(), toPosition())
}

private fun InlineasmContext.toAst(): InlineAssembly {
    val text = INLINEASMBLOCK().text
    return InlineAssembly(text.substring(2, text.length-2), false, toPosition())
}

private fun InlineirContext.toAst(): InlineAssembly {
    val text = INLINEASMBLOCK().text
    return InlineAssembly(text.substring(2, text.length-2), true, toPosition())
}

private fun ReturnstmtContext.toAst() : Return {
    val values = if(returnvalues()==null || returnvalues().expression().isEmpty()) arrayOf() else returnvalues().expression().map { it.toAst() }.toTypedArray()
    return Return(values, toPosition())
}

private fun UnconditionaljumpContext.toAst(): Jump {
    return Jump(expression().toAst(), toPosition())
}

private fun LabeldefContext.toAst(): Statement =
    Label(children[0].text, toPosition())

private fun AliasContext.toAst(): Statement =
    Alias(identifier().text, scoped_identifier().toAst(), toPosition())

private fun SubroutineContext.toAst() : Subroutine {
    // non-asm subroutine
    val returntypes = sub_return_part()?.datatype()?.map { it.toAst() } ?: emptyList()
    return Subroutine(
        identifier().text,
        sub_params()?.toAst()?.toMutableList() ?: mutableListOf(),
        returntypes.toMutableList(),
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

private fun Sub_paramsContext.toAst(): List<SubroutineParameter> =
        sub_param().map {
            val decl = it.vardecl()
            val tags = decl.TAG().map { t -> t.text }
            val validTags = arrayOf("@zp", "@requirezp", "@nozp", "@split", "@nosplit", "@shared")
            for(tag in tags) {
                if(tag !in validTags)
                    throw SyntaxError("invalid parameter tag '$tag'", toPosition())
            }
            val zp = getZpOption(tags)
            var datatype = decl.datatype()?.toAst() ?: DataType.UNDEFINED
            if(decl.ARRAYSIG()!=null || decl.arrayindex()!=null)
                datatype = datatype.elementToArray()

            val identifiers = decl.identifier()
            if(identifiers.size>1)
                throw SyntaxError("parameter name must be singular", identifiers[0].toPosition())
            val identifiername = identifiers[0].NAME() ?: identifiers[0].UNDERSCORENAME()

            val (registerorpair, statusregister) = parseParamRegister(it.register, it.toPosition())
            if(statusregister!=null) {
                throw SyntaxError("can't use status register as param for normal subroutines", Position(toPosition().file, it.register.line, it.register.charPositionInLine, it.register.charPositionInLine+1))
            }
            SubroutineParameter(identifiername.text, datatype, zp, registerorpair, it.toPosition())
        }

private fun getZpOption(tags: List<String>): ZeropageWish = when {
    "@requirezp" in tags -> ZeropageWish.REQUIRE_ZEROPAGE
    "@zp" in tags -> ZeropageWish.PREFER_ZEROPAGE
    "@nozp" in tags -> ZeropageWish.NOT_IN_ZEROPAGE
    else -> ZeropageWish.DONTCARE
}

private fun getSplitOption(tags: List<String>): SplitWish {
    return when {
        "@nosplit" in tags -> SplitWish.NOSPLIT
        "@split" in tags -> SplitWish.SPLIT
        else -> SplitWish.DONTCARE
    }
}

private fun Assign_targetContext.toAst() : AssignTarget {
    return when(this) {
        is IdentifierTargetContext -> {
            val identifier = scoped_identifier().toAst()
            AssignTarget(identifier, null, null, null, false, position = scoped_identifier().toPosition())
        }
        is MemoryTargetContext ->
            AssignTarget(
                null,
                null,
                DirectMemoryWrite(directmemory().expression().toAst(), directmemory().toPosition()),
                null,
                false,
                position = toPosition()
            )
        is ArrayindexedTargetContext -> {
            val ax = arrayindexed()
            val arrayvar = ax.scoped_identifier().toAst()
            val index = ax.arrayindex().toAst()
            val arrayindexed = ArrayIndexedExpression(arrayvar, index, ax.toPosition())
            AssignTarget(null, arrayindexed, null, null, false, position = toPosition())
        }
        is VoidTargetContext -> {
            AssignTarget(null, null, null, null, true, position = void_().toPosition())
        }
        is PointerDereferenceTargetContext -> {
            val deref = this.pointerdereference().toAst()
            AssignTarget(null, null, null, null, false, deref, deref.position)
        }
        else -> throw FatalAstException("weird assign target node $this")
    }
}

private fun Multi_assign_targetContext.toAst() : AssignTarget {
    val targets = this.assign_target().map { it.toAst() }
    return AssignTarget(null, null, null, targets, false, position = toPosition())
}

private fun ClobberContext.toAst() : Set<CpuRegister> {
    val names = this.NAME().map { it.text }
    try {
        return names.map { CpuRegister.valueOf(it) }.toSet()
    } catch(_: IllegalArgumentException) {
        throw SyntaxError("invalid cpu register", toPosition())
    }
}

private fun AssignmentContext.toAst(): Statement {
    val multiAssign = multi_assign_target()
    if(multiAssign!=null) {
        return Assignment(multiAssign.toAst(), expression().toAst(), AssignmentOrigin.USERCODE, toPosition())
    }

    val nestedAssign = assignment()
    return if(nestedAssign==null)
        Assignment(assign_target().toAst(), expression().toAst(), AssignmentOrigin.USERCODE, toPosition())
    else
        ChainedAssignment(assign_target().toAst(), nestedAssign.toAst(), toPosition())
}

private fun AugassignmentContext.toAst(): Assignment {
    // replace A += X  with  A = A + X
    val target = assign_target().toAst()
    val oper = operator.text.substringBefore('=')
    val expression = BinaryExpression(target.toExpression(), oper, expression().toAst(), expression().toPosition())
    return Assignment(assign_target().toAst(), expression, AssignmentOrigin.USERCODE, toPosition())
}

private fun BasedatatypeContext.toAst(): BaseDataType {
    return try {
        BaseDataType.valueOf(text.uppercase())
    } catch (_: IllegalArgumentException) {
        BaseDataType.UNDEFINED
    }
}

private fun DatatypeContext.toAst(): DataType {
    val base = basedatatype()?.toAst()
    if(base!=null)
        return DataType.forDt(base)
    val pointer = pointertype().toAst()
    return pointer
}

private fun PointertypeContext.toAst(): DataType {
    val base = basedatatype()?.toAst()
    if(base!=null)
        return DataType.pointer(base)
    val identifier = scoped_identifier().identifier().map { it.text}
    return DataType.pointer(identifier)
}

private fun ArrayindexContext.toAst() : ArrayIndex =
        ArrayIndex(expression().toAst(), toPosition())

internal fun DirectiveContext.toAst() : Directive {
    if(directivenamelist() != null) {
        val identifiers = directivenamelist().scoped_identifier().map { DirectiveArg(it.text, null, it.toPosition()) }
        return Directive(directivename.text, identifiers, toPosition())
    }
    else
        return Directive(directivename.text, directivearg().map { it.toAst() }, toPosition())
}

private fun DirectiveargContext.toAst() : DirectiveArg {
    val str = stringliteral()
    if(str!=null) {
        if (str.encoding?.text != null)
            throw SyntaxError("don't use a string encoding for directive arguments", toPosition())
        return DirectiveArg(str.text.substring(1, text.length-1), integerliteral()?.toAst()?.number?.toUInt(), toPosition())
    }

    return DirectiveArg(identifier()?.text, integerliteral()?.toAst()?.number?.toUInt(), toPosition())
}

private fun IntegerliteralContext.toAst(): NumericLiteralNode {
    fun makeLiteral(literalTextWithGrouping: String, radix: Int): NumericLiteralNode {
        val literalText = literalTextWithGrouping.replace("_", "")
        val integer: Int
        var datatype = BaseDataType.UBYTE
        when (radix) {
            10 -> {
                integer = try {
                    literalText.toInt()
                } catch(x: NumberFormatException) {
                    throw SyntaxError("invalid decimal literal ${x.message}", toPosition())
                }
                datatype = when(integer) {
                    in 0..255 -> BaseDataType.UBYTE
                    in -128..127 -> BaseDataType.BYTE
                    in 0..65535 -> BaseDataType.UWORD
                    in -32768..32767 -> BaseDataType.WORD
                    in -2147483647..2147483647 -> BaseDataType.LONG
                    else -> BaseDataType.FLOAT
                }
            }
            2 -> {
                if(literalText.length>16)
                    datatype = BaseDataType.LONG
                else if(literalText.length>8)
                    datatype = BaseDataType.UWORD
                try {
                    integer = literalText.toInt(2)
                } catch(x: NumberFormatException) {
                    throw SyntaxError("invalid binary literal ${x.message}", toPosition())
                }
            }
            16 -> {
                if(literalText.length>4)
                    datatype = BaseDataType.LONG
                else if(literalText.length>2)
                    datatype = BaseDataType.UWORD
                try {
                    integer = literalText.toInt(16)
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
        DEC_INTEGER -> makeLiteral(integerPart, 10)
        HEX_INTEGER -> makeLiteral(integerPart.substring(1), 16)
        BIN_INTEGER -> makeLiteral(integerPart.substring(1), 2)
        else -> throw FatalAstException(terminal.text)
    }
}

private fun ExpressionContext.toAst(insideParentheses: Boolean=false) : Expression {

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
                    BaseDataType.UBYTE -> NumericLiteral(BaseDataType.UBYTE, intLit.number, litval.toPosition())
                    BaseDataType.BYTE -> NumericLiteral(BaseDataType.BYTE, intLit.number, litval.toPosition())
                    BaseDataType.UWORD -> NumericLiteral(BaseDataType.UWORD, intLit.number, litval.toPosition())
                    BaseDataType.WORD -> NumericLiteral(BaseDataType.WORD, intLit.number, litval.toPosition())
                    BaseDataType.LONG -> NumericLiteral(BaseDataType.LONG, intLit.number, litval.toPosition())
                    BaseDataType.FLOAT -> NumericLiteral(BaseDataType.FLOAT, intLit.number, litval.toPosition())
                    else -> throw FatalAstException("invalid datatype for numeric literal")
                }
                litval.floatliteral()!=null -> NumericLiteral(BaseDataType.FLOAT, litval.floatliteral().toAst(), litval.toPosition())
                litval.stringliteral()!=null -> litval.stringliteral().toAst()
                litval.charliteral()!=null -> litval.charliteral().toAst()
                litval.arrayliteral()!=null -> {
                    val array = litval.arrayliteral().toAst()
                    // the actual type of the arraysize can not yet be determined here
                    // the ConstantFold takes care of that and converts the type if needed.
                    ArrayLiteral(InferredTypes.InferredType.unknown(), array, position = litval.toPosition())
                }
                else -> throw FatalAstException("invalid parsed literal")
            }
        }
    }

    if(arrayindexed()!=null) {
        val ax = arrayindexed()
        val identifier = ax.scoped_identifier().toAst()
        val index = ax.arrayindex().toAst()
        return ArrayIndexedExpression(identifier, index, ax.toPosition())
    }

    if(scoped_identifier()!=null)
        return scoped_identifier().toAst()

    if(bop!=null) {
        val operator = bop.text.trim().replace("\\s+".toRegex(), " ")
        return BinaryExpression(
            left.toAst(),
            operator,
            right.toAst(),
            toPosition(),
            insideParentheses = insideParentheses
        )
    }

    if(prefix!=null)
        return PrefixExpression(prefix.text, expression(0).toAst(), toPosition())

    val funcall = functioncall()?.toAst()
    if(funcall!=null) return funcall

    if (rangefrom!=null && rangeto!=null) {
        val defaultstep = if(rto.text == "to") 1 else -1
        val step = rangestep?.toAst() ?: NumericLiteral.optimalInteger(defaultstep, toPosition())
        return RangeExpression(rangefrom.toAst(), rangeto.toAst(), step, toPosition())
    }

    if(childCount==3 && children[0].text=="(" && children[2].text==")")
        return expression(0).toAst(insideParentheses=true)        // expression within ( )

    if(typecast()!=null) {
        val dt = typecast().datatype().toAst()
        return TypecastExpression(expression(0).toAst(), dt, false, toPosition())
    }

    if(directmemory()!=null)
        return DirectMemoryRead(directmemory().expression().toAst(), toPosition())

    if(addressof()!=null) {
        val addressOf = addressof()
        val identifier = addressOf.scoped_identifier()
        val msb = addressOf.ADDRESS_OF_MSB()!=null
        // note: &<  (ADDRESS_OF_LSB)  is equivalent to a regular &.
        return if (identifier != null)
            AddressOf(addressof().scoped_identifier().toAst(),null, msb, toPosition())
        else {
            val array = addressOf.arrayindexed()
            AddressOf(array.scoped_identifier().toAst(), array.arrayindex().toAst(), msb, toPosition())
        }
    }

    if(if_expression()!=null) {
        val ifex = if_expression()
        val (condition, truevalue, falsevalue) = ifex.expression()
        return IfExpression(condition.toAst(), truevalue.toAst(), falsevalue.toAst(), toPosition())
    }

    val deref = pointerdereference()?.toAst()
    if(deref!=null) return deref

    throw FatalAstException(text)
}


private fun PointerdereferenceContext.toAst(): PtrDereference {
    val scopeprefix = prefix?.toAst()
    val derefchain = derefchain()!!.singlederef()!!.map { it.identifier().text }
    val firstIdentifier =
        if(scopeprefix!=null)
            IdentifierReference(scopeprefix.nameInSource + derefchain.first(), toPosition())
        else
            IdentifierReference(listOf(derefchain.first()), toPosition())
    return PtrDereference(firstIdentifier, derefchain.drop(1), field?.text, toPosition())
}

private fun CharliteralContext.toAst(): CharLiteral {
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

private fun StringliteralContext.toAst(): StringLiteral {
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

private fun Expression_listContext.toAst() = expression().map{ it.toAst() }

private fun Scoped_identifierContext.toAst() : IdentifierReference {
    return IdentifierReference(identifier().map { it.text }, toPosition())
}

private fun FloatliteralContext.toAst() = text.replace("_","").toDouble()

private fun BooleanliteralContext.toAst() = when(text) {
    "true" -> true
    "false" -> false
    else -> throw FatalAstException(text)
}

private fun ArrayliteralContext.toAst() : Array<Expression> =
        expression().map { it.toAst() }.toTypedArray()

private fun If_stmtContext.toAst(): IfElse {
    val condition = expression().toAst()
    val trueStatements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val elseStatements = else_part()?.toAst() ?: mutableListOf()
    val trueScope = AnonymousScope(trueStatements, statement_block()?.toPosition()
            ?: statement().toPosition())
    val elseScope = AnonymousScope(elseStatements, else_part()?.toPosition() ?: toPosition())
    return IfElse(condition, trueScope, elseScope, toPosition())
}

private fun Else_partContext.toAst(): MutableList<Statement> {
    return statement_block()?.toAst() ?: mutableListOf(statement().toAst())
}

private fun Branch_stmtContext.toAst(): ConditionalBranch {
    val branchcondition = branchcondition().toAst()
    val trueStatements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val elseStatements = else_part()?.toAst() ?: mutableListOf()
    val trueScope = AnonymousScope(trueStatements, statement_block()?.toPosition()
            ?: statement().toPosition())
    val elseScope = AnonymousScope(elseStatements, else_part()?.toPosition() ?: toPosition())
    return ConditionalBranch(branchcondition, trueScope, elseScope, toPosition())
}

private fun BranchconditionContext.toAst() = BranchCondition.valueOf(
    text.substringAfter('_').uppercase()
)

private fun ForloopContext.toAst(): ForLoop {
    val loopvar = scoped_identifier().toAst()
    val iterable = expression()!!.toAst()
    val scope =
            if(statement()!=null)
                AnonymousScope(mutableListOf(statement().toAst()), statement().toPosition())
            else
                AnonymousScope(statement_block().toAst(), statement_block().toPosition())
    return ForLoop(loopvar, iterable, scope, toPosition())
}

private fun BreakstmtContext.toAst() = Break(toPosition())

private fun ContinuestmtContext.toAst() = Continue(toPosition())

private fun DeferContext.toAst(): Defer {
    val block = statement_block()?.toAst()
    if(block!=null) {
        val scope = AnonymousScope(block, statement_block()?.toPosition() ?: toPosition())
        return Defer(scope, toPosition())
    }
    val singleStmt = statement()!!.toAst()
    val scope = AnonymousScope(mutableListOf(singleStmt), statement().toPosition())
    return Defer(scope, toPosition())
}

private fun WhileloopContext.toAst(): WhileLoop {
    val condition = expression().toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
            ?: statement().toPosition())
    return WhileLoop(condition, scope, toPosition())
}

private fun RepeatloopContext.toAst(): RepeatLoop {
    val iterations = expression()?.toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
            ?: statement().toPosition())
    return RepeatLoop(iterations, scope, toPosition())
}

private fun UnrollloopContext.toAst(): UnrollLoop {
    val iterations = expression().toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
        ?: statement().toPosition())
    return UnrollLoop(iterations, scope, toPosition())
}

private fun UntilloopContext.toAst(): UntilLoop {
    val untilCondition = expression().toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val scope = AnonymousScope(statements, statement_block()?.toPosition()
            ?: statement().toPosition())
    return UntilLoop(scope, untilCondition, toPosition())
}

private fun WhenstmtContext.toAst(): When {
    val condition = expression().toAst()
    val choices = this.when_choice()?.map { it.toAst() }?.toMutableList() ?: mutableListOf()
    return When(condition, choices, toPosition())
}

private fun When_choiceContext.toAst(): WhenChoice {
    val values = expression_list()?.toAst()
    val stmt = statement()?.toAst()
    val stmtBlock = statement_block()?.toAst()?.toMutableList() ?: mutableListOf()
    if(stmt!=null)
        stmtBlock.add(stmt)
    val scope = AnonymousScope(stmtBlock, toPosition())
    return WhenChoice(values?.toMutableList(), scope, toPosition())
}

private fun StructfielddeclContext.toAst(): Pair<DataType, String> {
    val identifier = identifier().NAME().text
    val dt = datatype().toAst()
    return dt to identifier
}

private fun VardeclContext.toAst(type: VarDeclType, value: Expression?): VarDecl {
    val tags = TAG().map { it.text }
    val validTags = arrayOf("@zp", "@requirezp", "@nozp", "@split", "@nosplit", "@shared", "@alignword", "@alignpage", "@align64", "@dirty")
    for(tag in tags) {
        if(tag !in validTags)
            throw SyntaxError("invalid variable tag '$tag'", toPosition())
    }
    val zp = getZpOption(tags)
    val split = getSplitOption(tags)
    val identifiers = identifier()
    val identifiername = identifiers[0].NAME() ?: identifiers[0].UNDERSCORENAME()
    val name = if(identifiers.size==1) identifiername.text else "<multiple>"
    val isArray = ARRAYSIG() != null || arrayindex() != null
    val alignword = "@alignword" in tags
    val align64 = "@align64" in tags
    val alignpage = "@alignpage" in tags
    if(alignpage && alignword)
        throw SyntaxError("choose a single alignment option", toPosition())
    val baseDt = datatype()?.toAst() ?: DataType.UNDEFINED
    val dt = if(!isArray) baseDt else {
        if(baseDt.isPointer)
            DataType.arrayOfPointersTo(baseDt.sub, baseDt.subIdentifier)
        else
            DataType.arrayFor(baseDt.base, split!=SplitWish.NOSPLIT)
    }

    return VarDecl(
            type, VarDeclOrigin.USERCODE,
            dt,
            zp,
            split,
            arrayindex()?.toAst(),
            name,
            if(identifiers.size==1) emptyList() else identifiers.map {
                val idname = it.NAME() ?: it.UNDERSCORENAME()
                idname.text
            },
            value,
            "@shared" in tags,
            if(alignword) 2u else if(align64) 64u else if(alignpage) 256u else 0u,
            "@dirty" in tags,
            toPosition()
    )
}
