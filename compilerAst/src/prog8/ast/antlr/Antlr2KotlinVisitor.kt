package prog8.ast.antlr

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor
import org.antlr.v4.runtime.tree.TerminalNode
import prog8.ast.FatalAstException
import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.SyntaxError
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.code.core.*
import prog8.code.source.SourceCode
import prog8.parser.Prog8ANTLRParser.*
import prog8.parser.Prog8ANTLRVisitor
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile


class Antlr2KotlinVisitor(val source: SourceCode): AbstractParseTreeVisitor<Node>(), Prog8ANTLRVisitor<Node> {

    // Cached resolved filename - computed once per visitor since it never changes during a single parse
    private var cachedFileName: String? = null

    companion object {
        private val WHITESPACE_RE = Regex("\\s+")
        private val ENCODING_BY_PREFIX = Encoding.entries.associateBy { it.prefix }
        private val BASE_DATATYPE_MAP = BaseDataType.entries.associateBy { it.name.lowercase() }
        private val BRANCH_CONDITION_MAP = BranchCondition.entries.associateBy { "if_" + it.name.lowercase() }
    }

    override fun visitModule(ctx: ModuleContext): Module {
        val statements = ctx.module_element().mapTo(mutableListOf()) { it.accept(this) as Statement }
        return Module(statements, ctx.toPosition(), source)
    }

    override fun visitBlock(ctx: BlockContext): Block {
        val name = getname(ctx.identifier())
        val address = (ctx.integerliteral()?.accept(this) as NumericLiteral?)?.number?.toUInt()
        val statements = ctx.block_statement().mapTo(mutableListOf()) { it.accept(this) as Statement }
        return Block(name, address, statements, source.isFromLibrary, ctx.toPosition())
    }

    override fun visitExpression(ctx: ExpressionContext): Expression {
        if(ctx.sizeof_expression!=null) {
            // Handle pointer type argument: sizeof(^^float)
            if(ctx.sizeof_argument().pointertype()!=null)
                return IdentifierReference(listOf("sys", "SIZEOF_POINTER"), ctx.toPosition())

            // Handle address-of argument: sizeof(&var) or sizeof(&&var)
            val addressofCtx = ctx.sizeof_argument().addressof()
            if(addressofCtx != null) {
                val addressof = addressofCtx.accept(this) as AddressOf
                val sizeof = IdentifierReference(listOf("sizeof"), ctx.toPosition())
                return FunctionCallExpression(sizeof, mutableListOf(addressof), ctx.toPosition())
            }

            // Handle basedatatype argument: sizeof(byte)
            val sdt = ctx.sizeof_argument().basedatatype()
            val datatype = if(sdt != null) baseDatatypeFor(sdt) else null
            if(datatype != null) {
                val arg = IdentifierReference(listOf(datatype.name.lowercase()), ctx.toPosition())
                val sizeof = IdentifierReference(listOf("sizeof"), ctx.toPosition())
                return FunctionCallExpression(sizeof, mutableListOf(arg), ctx.toPosition())
            }

            // Handle scoped_identifier argument: sizeof(myvar) or sizeof(MyStruct)
            val identifier = ctx.sizeof_argument().scoped_identifier()?.accept(this) as IdentifierReference?
            if(identifier != null) {
                val sizeof = IdentifierReference(listOf("sizeof"), ctx.toPosition())
                return FunctionCallExpression(sizeof, mutableListOf(identifier), ctx.toPosition())
            }

            // Should not reach here - grammar should ensure one of the above cases matches
            throw FatalAstException("invalid sizeof argument at ${ctx.toPosition()}")
        }

        if(ctx.bop!=null) {
            val operator = ctx.bop.text.trim().replace(WHITESPACE_RE, " ")
            return BinaryExpression(
                ctx.left.accept(this) as Expression,
                operator,
                ctx.right.accept(this) as Expression,
                ctx.toPosition()
            )
        }

        if(ctx.prefix!=null) {
            return PrefixExpression(ctx.prefix.text, ctx.expression(0).accept(this) as Expression, ctx.toPosition())
        }

        if(ctx.rangefrom!=null && ctx.rangeto!=null) {
            val defaultstep = if(ctx.rto.text == "to") 1 else -1
            return RangeExpression(
                ctx.rangefrom.accept(this) as Expression,
                ctx.rangeto.accept(this) as Expression,
                ctx.rangestep?.accept(this) as Expression? ?: NumericLiteral.optimalInteger(defaultstep, ctx.toPosition()),
                ctx.toPosition())
        }

        if(ctx.typecast()!=null) {
            val dt = dataTypeFor(ctx.typecast().datatype())!!
            return TypecastExpression(ctx.expression(0).accept(this) as Expression, dt, false, ctx.toPosition())
        }

        if(ctx.childCount==3 && ctx.children[0].text=="(" && ctx.children[2].text==")")
            return ctx.expression(0).accept(this) as Expression        // expression within ( )

        return visitChildren(ctx) as Expression
    }

    override fun visitTuple_expression(ctx: Tuple_expressionContext): ExpressionTuple {
        val expressions  = ctx.expression().map { it.accept(this) as Expression }
        return ExpressionTuple(expressions, ctx.toPosition())
    }

    override fun visitSubroutinedeclaration(ctx: SubroutinedeclarationContext): Subroutine {
        if(ctx.subroutine()!=null)
            return ctx.subroutine().accept(this) as Subroutine
        if(ctx.asmsubroutine()!=null)
            return ctx.asmsubroutine().accept(this) as Subroutine
        if(ctx.extsubroutine()!=null)
            return ctx.extsubroutine().accept(this) as Subroutine
        throw FatalAstException("weird subroutine")
    }

    override fun visitAlias(ctx: AliasContext): Alias {
        val identifier = getname(ctx.identifier())
        val target = ctx.scoped_identifier().accept(this) as IdentifierReference
        return Alias(identifier, target, ctx.toPosition())
    }

    override fun visitDefer(ctx: DeferContext): Defer {
        val statements = stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
        return Defer(statements, ctx.toPosition())
    }

    override fun visitLabeldef(ctx: LabeldefContext): Label {
        return Label(getname(ctx.identifier()), ctx.toPosition())
    }

    override fun visitUnconditionaljump(ctx: UnconditionaljumpContext): Jump {
        return Jump(ctx.expression().accept(this) as Expression, ctx.toPosition())
    }

    override fun visitDirective(ctx: DirectiveContext): Directive {
        val pos = ctx.toPosition()
        val end = ctx.directivename().UNICODEDNAME().symbol.stopIndex - ctx.directivename().UNICODEDNAME().symbol.startIndex
        val position = Position(pos.file, pos.line, pos.startCol, pos.startCol+end+1)
        if(ctx.directivenamelist() != null) {
            val namelist = ctx.directivenamelist().scoped_identifier().map { it.accept(this) as IdentifierReference }
            val identifiers = namelist.map { DirectiveArg(it.nameInSource.joinToString("."), null, it.position) }
            return Directive(ctx.directivename().text, identifiers, position)
        }
        else
            return Directive(ctx.directivename().text, ctx.directivearg().map { it.accept(this) as DirectiveArg }, position)
    }

    override fun visitDirectivearg(ctx: DirectiveargContext): DirectiveArg {
        val integer = (ctx.integerliteral()?.accept(this) as NumericLiteral?)?.number?.toUInt()
        val str = ctx.stringliteral()
        if(str!=null) {
            if (str.encoding?.text != null)
                throw SyntaxError("don't use a string encoding for directive arguments", ctx.toPosition())
            return DirectiveArg(str.text.substring(1, str.text.length-1), integer, ctx.toPosition())
        }
        val identifier = ctx.identifier()?.accept(this) as IdentifierReference?
        return DirectiveArg(identifier?.nameInSource?.single(), integer, identifier?.position ?: ctx.toPosition())
    }

    override fun visitVardecl(ctx: VardeclContext): VarDecl {
        val isPrivate = ctx.PRIVATE() != null
        val tags = ctx.TAG().map { it.text }
        val validTags = arrayOf("@zp", "@requirezp", "@nozp", "@nosplit", "@shared", "@alignword", "@alignpage", "@align64", "@dirty")
        for(tag in tags) {
            if(tag !in validTags)
                throw SyntaxError("invalid variable tag '$tag'", ctx.toPosition())
        }
        val zp = getZpOption(tags)
        val split = getSplitOption(tags)
        val alignword = "@alignword" in tags
        val align64 = "@align64" in tags
        val alignpage = "@alignpage" in tags
        if(alignpage && alignword)
            throw SyntaxError("choose a single alignment option", ctx.toPosition())

        val identifiers = ctx.identifierlist().identifier().map { getname(it) }

        // Handle 0, 1, or 2 array indices
        val arrayIndices = ctx.arrayindex()
        val isArray = ctx.EMPTYARRAYSIG() != null || arrayIndices.isNotEmpty()
        
        val (arraySize, matrixNumCols) = if(arrayIndices.size == 2) {
            // 2D array: [rows][cols]
            val rowIndex = arrayIndices[0].accept(this) as ArrayIndex
            val colIndex = arrayIndices[1].accept(this) as ArrayIndex
            val rows = rowIndex.indexExpr as? NumericLiteral
            val cols = colIndex.indexExpr as? NumericLiteral
            if(rows == null || cols == null) {
                throw SyntaxError("2D array dimensions must be constant expressions", ctx.toPosition())
            }
            val totalElements = (rows.number.toInt() * cols.number.toInt())
            val totalSize = ArrayIndex(NumericLiteral.optimalNumeric(totalElements, ctx.toPosition()), ctx.toPosition())
            Pair(totalSize, colIndex.indexExpr)
        } else if(arrayIndices.isNotEmpty()) {
            // 1D array
            val arrayIndex = arrayIndices[0].accept(this) as ArrayIndex
            Pair(arrayIndex, null)
        } else {
            Pair(null, null)
        }

        val baseDt = dataTypeFor(ctx.datatype()) ?: DataType.UNDEFINED
        val dt = if(!isArray) baseDt else {
            if(baseDt.isPointer)
                DataType.arrayOfPointersFromAntlrTo(baseDt.sub, baseDt.subTypeFromAntlr)
            else if(baseDt.isStructInstance)
                throw SyntaxError("array of structures not allowed (use array of pointers)", ctx.toPosition())
            else
                DataType.arrayFor(baseDt.base, split!=SplitWish.NOSPLIT)
        }

        return VarDecl.builder(dt, ctx.toPosition())
            .names(identifiers)
            .alignment(if(alignword) 2u else if(align64) 64u else if(alignpage) 256u else 0u)
            .arraysize(arraySize)
            .dirty("@dirty" in tags)
            .isPrivate(isPrivate)
            .matrixNumCols(matrixNumCols)
            .sharedWithAsm("@shared" in tags)
            .splitwordarray(split)
            .zeropage(zp)
            .build()
    }

    override fun visitVarinitializer(ctx: VarinitializerContext): VarDecl {
        val vardecl = ctx.vardecl().accept(this) as VarDecl
        val tuple = ctx.tuple_expression()
        vardecl.value = if(tuple!=null)
            tuple.accept(this) as ExpressionTuple
        else
            ctx.expression().accept(this) as Expression
        vardecl.hasExplicitInitializer = true
        return vardecl
    }

    override fun visitConstdecl(ctx: ConstdeclContext): VarDecl {
        if(ctx.datatype()==null)  // semantic check instead of grammar rule to have a better error message
            throw SyntaxError("datatype missing", ctx.identifierlist().toPosition())
        val isPrivate = ctx.PRIVATE() != null
        val datatype = dataTypeFor(ctx.datatype()) ?: DataType.LONG
        val identifiers = ctx.identifierlist().identifier().map { getname(it) }
        val initialvalue = ctx.expression().accept(this) as Expression
        val actualValue = if(initialvalue is NumericLiteral && datatype.base.largerSizeThan(initialvalue.type))
                NumericLiteral(datatype.base, initialvalue.number, initialvalue.position)
            else
                initialvalue

        return VarDecl.builder(datatype, ctx.toPosition())
            .names(identifiers)
            .isPrivate(isPrivate)
            .type(VarDeclType.CONST)
            .value(actualValue)
            .build()
            .apply { hasExplicitInitializer = true }
    }

    override fun visitMemoryvardecl(ctx: MemoryvardeclContext): VarDecl {
        val vardecl = ctx.varinitializer().accept(this) as VarDecl
        vardecl.type = VarDeclType.MEMORY
        return vardecl
    }

    override fun visitArrayindex(ctx: ArrayindexContext): ArrayIndex {
        return ArrayIndex(ctx.expression().accept(this) as Expression, ctx.toPosition())
    }

    override fun visitAssignment(ctx: AssignmentContext): Statement {
        val tuple = ctx.tuple_expression()
        if(tuple!=null) {
            val targets = ctx.multi_assign_target().assign_target().map { it.accept(this) as AssignTarget }
            val values = tuple.accept(this) as ExpressionTuple
            if(targets.size!=values.expressions.size) {
                throw SyntaxError("multivalue assignment: number of values does not match number of targets", ctx.toPosition())
            } else {

                if (values.expressions.all { it is NumericLiteral }) {
                    val firstValue = (values.expressions.first() as NumericLiteral).number
                    if(values.expressions.all { (it as NumericLiteral).number == firstValue }) {
                        // replace by a chained assignment a=b=c = 42
                        val value = values.expressions[0]
                        var chain: Statement = Assignment(targets.last(), value, AssignmentOrigin.USERCODE, ctx.toPosition())
                        for (target in targets.reversed().drop(1)) {
                            chain = ChainedAssignment(target, chain, ctx.toPosition())
                        }
                        return chain
                    }
                }

                val assigns = targets.zip(values.expressions).mapTo(mutableListOf()) { (target, value) ->
                    Assignment(target, value, AssignmentOrigin.USERCODE, ctx.toPosition()) as Statement
                }
                return AnonymousScope(assigns, ctx.toPosition())
            }
        }

        val multiAssign = ctx.multi_assign_target()
        if(multiAssign!=null) {
            return Assignment(multiAssign.accept(this) as AssignTarget, ctx.expression().accept(this) as Expression, AssignmentOrigin.USERCODE, ctx.toPosition())
        }

        val nestedAssign = ctx.assignment()
        return if(nestedAssign==null)
            Assignment(ctx.assign_target().accept(this) as AssignTarget, ctx.expression().accept(this) as Expression, AssignmentOrigin.USERCODE, ctx.toPosition())
        else
            ChainedAssignment(ctx.assign_target().accept(this) as AssignTarget, nestedAssign.accept(this) as Statement, ctx.toPosition())
    }

    override fun visitAugassignment(ctx: AugassignmentContext): Assignment {
        // replace A += X  with  A = A + X
        val target = ctx.assign_target().accept(this) as AssignTarget
        val oper = ctx.operator.text.substringBefore('=')
        val expression = BinaryExpression(target.toExpression(), oper, ctx.expression().accept(this) as Expression, ctx.toPosition())
        return Assignment(target, expression, AssignmentOrigin.USERCODE, ctx.toPosition())
    }

    override fun visitIdentifierTarget(ctx: IdentifierTargetContext): AssignTarget {
        val identifier = ctx.scoped_identifier().accept(this) as IdentifierReference
        return AssignTarget(identifier, null, null, null, false, position=ctx.toPosition())
    }

    override fun visitArrayindexedTarget(ctx: ArrayindexedTargetContext): AssignTarget {
        val ax = ctx.arrayindexed()
        val arrayindexed = ax.accept(this) as ArrayIndexedExpression
        return AssignTarget(null, arrayindexed, null, null, false, position=ctx.toPosition())
    }

    override fun visitMemoryTarget(ctx: MemoryTargetContext): AssignTarget {
        return AssignTarget(null, null,
            DirectMemoryWrite(ctx.directmemory().expression().accept(this) as Expression, ctx.toPosition()),
            null, false, position=ctx.toPosition())
    }

    override fun visitVoidTarget(ctx: VoidTargetContext): AssignTarget {
        return AssignTarget(null, null, null, null, true, position=ctx.toPosition())
    }

    override fun visitMulti_assign_target(ctx: Multi_assign_targetContext): AssignTarget {
        val targets = ctx.assign_target().map { it.accept(this) as AssignTarget }
        return AssignTarget(null, null, null, targets, false, position=ctx.toPosition())
    }

    override fun visitPostincrdecr(ctx: PostincrdecrContext): Assignment {
        val tgt = ctx.assign_target().accept(this) as AssignTarget
        val operator = ctx.operator.text
        val pos = ctx.toPosition()
        val addSubOne = BinaryExpression(tgt.toExpression(), if(operator=="++") "+" else "-", NumericLiteral.optimalInteger(1, pos), pos)
        return Assignment(tgt, addSubOne, AssignmentOrigin.USERCODE, pos)
    }

    override fun visitArrayindexed(ctx: ArrayindexedContext): ArrayIndexedExpression {
        val identifier = ctx.scoped_identifier().accept(this) as IdentifierReference
        val indices = ctx.arrayindex()
        // Build nested ArrayIndexedExpression for chained indexing
        // For matrix[i][j], create: ArrayIndexedExpression(ArrayIndexedExpression(matrix, i), j)
        var result: ArrayIndexedExpression? = null
        for(arrayIndexCtx in indices) {
            val index = arrayIndexCtx.accept(this) as ArrayIndex
            result = if(result == null) {
                ArrayIndexedExpression(identifier, null, null, index, ctx.toPosition())
            } else {
                ArrayIndexedExpression(null, result, null, index, ctx.toPosition())
            }
        }
        return result!!
    }

    override fun visitDirectmemory(ctx: DirectmemoryContext): DirectMemoryRead {
        return DirectMemoryRead(ctx.expression().accept(this) as Expression, ctx.toPosition())
    }

    override fun visitAddressof(ctx: AddressofContext): AddressOf {
        val identifier = ctx.scoped_identifier().accept(this) as IdentifierReference
        val msb = ctx.ADDRESS_OF_MSB()!=null
        // note: &<  (ADDRESS_OF_LSB)  is equivalent to a regular &.
        val index = ctx.arrayindex()?.accept(this) as? ArrayIndex
        var typed = false
        if(ctx.TYPED_ADDRESS_OF()!=null) {
            // new typed AddressOf
            if(msb)
                throw SyntaxError("typed address of not allowed with msb", ctx.toPosition())
            typed = true
        }
        return if (index != null) {
            AddressOf(identifier, index, null, msb, typed, ctx.toPosition())
        } else {
            AddressOf(identifier, null, null, msb, typed, ctx.toPosition())
        }
    }

    override fun visitFunctioncall(ctx: FunctioncallContext): FunctionCallExpression {
        val name = ctx.scoped_identifier().accept(this) as IdentifierReference
        val args = ctx.expression_list()?.expression()?.mapTo(mutableListOf()) { it.accept(this) as Expression } ?: mutableListOf()
        return FunctionCallExpression(name, args, ctx.toPosition())
    }

    override fun visitFunctioncall_stmt(ctx: Functioncall_stmtContext): FunctionCallStatement {
        val void = ctx.VOID() != null
        val name = ctx.scoped_identifier().accept(this) as IdentifierReference
        val args = ctx.expression_list()?.expression()?.mapTo(mutableListOf()) { it.accept(this) as Expression } ?: mutableListOf()
        return FunctionCallStatement(name, args, void, ctx.toPosition())
    }

    override fun visitReturnstmt(ctx: ReturnstmtContext): Return {
        val cvalues = ctx.returnvalues()
        val values = if(cvalues==null || cvalues.expression().isEmpty()) arrayOf() else cvalues.expression().map { it.accept(this) as Expression }.toTypedArray()
        return Return(values, ctx.toPosition())
    }

    override fun visitBreakstmt(ctx: BreakstmtContext): Break {
        return Break(ctx.toPosition())
    }

    override fun visitContinuestmt(ctx: ContinuestmtContext): Continue {
        return Continue(ctx.toPosition())
    }

    override fun visitIdentifier(ctx: IdentifierContext): IdentifierReference {
        return IdentifierReference(listOf(getname(ctx)), ctx.toPosition())
    }

    override fun visitScoped_identifier(ctx: Scoped_identifierContext): IdentifierReference {
        val identifiers = ctx.identifier()
        val children = if(identifiers.size == 1)
            listOf(identifiers[0].text)
        else
            identifiers.map { it.text }
        return IdentifierReference(children, ctx.toPosition())
    }

    override fun visitIntegerliteral(ctx: IntegerliteralContext): NumericLiteral {

        fun makeLiteral(literalTextWithGrouping: String, radix: Int): Pair<Double, BaseDataType> {
            val literalText = literalTextWithGrouping.replace("_", "")
            val integer: Long
            var datatype = BaseDataType.UBYTE
            when (radix) {
                10 -> {
                    integer = try {
                        literalText.toLong()
                    } catch(x: NumberFormatException) {
                        throw SyntaxError("invalid decimal literal ${x.message}", ctx.toPosition())
                    }
                    datatype = when(integer) {
                        in 0..255 -> BaseDataType.UBYTE
                        in -128..127 -> BaseDataType.BYTE
                        in 0..65535 -> BaseDataType.UWORD
                        in -32768..32767 -> BaseDataType.WORD
                        in -2147483648L..0xffffffffL -> BaseDataType.LONG   // TODO "hack" to allow unsigned long constants to be used as values for signed longs, without needing a cast (max value)
                        else -> BaseDataType.FLOAT
                    }
                }
                2 -> {
                    if(literalText.length>16)
                        datatype = BaseDataType.LONG
                    else if(literalText.length>8)
                        datatype = BaseDataType.UWORD
                    try {
                        integer = literalText.toLong(2)
                    } catch(x: NumberFormatException) {
                        throw SyntaxError("invalid binary literal ${x.message}", ctx.toPosition())
                    }
                }
                16 -> {
                    if(literalText.length>4)
                        datatype = BaseDataType.LONG
                    else if(literalText.length>2)
                        datatype = BaseDataType.UWORD
                    try {
                        integer = literalText.lowercase().toLong(16)
                    } catch(x: NumberFormatException) {
                        throw SyntaxError("invalid hexadecimal literal ${x.message}", ctx.toPosition())
                    }
                }
                else -> throw FatalAstException("invalid radix")
            }
            return integer.toDouble() to datatype
        }

        val terminal: TerminalNode = ctx.children[0] as TerminalNode
        val integerPart = ctx.intpart.text
        val integer = when (terminal.symbol.type) {
            DEC_INTEGER -> makeLiteral(integerPart, 10)
            HEX_INTEGER -> makeLiteral(integerPart.substring(1), 16)
            BIN_INTEGER -> makeLiteral(integerPart.substring(1), 2)
            else -> throw FatalAstException(terminal.text)
        }

        // TODO "hack" to allow unsigned long constants to be used as values for signed longs, without needing a cast
        if(integer.second.isLong && integer.first > Integer.MAX_VALUE && integer.first <= 0xffffffff) {
            val signedLong = integer.first.toLong().toInt()
            return NumericLiteral(integer.second, signedLong.toDouble(), ctx.toPosition())
        }

        return NumericLiteral(integer.second, integer.first, ctx.toPosition())
    }

    override fun visitBooleanliteral(ctx: BooleanliteralContext): NumericLiteral {
        val boolean = when(ctx.text) {
            "true" -> true
            "false" -> false
            else -> throw FatalAstException(ctx.text)
        }
        return NumericLiteral.fromBoolean(boolean, ctx.toPosition())
    }

    override fun visitArrayliteral(ctx: ArrayliteralContext): ArrayLiteral {
        val array = ctx.expression().map { it.accept(this) as Expression }.toTypedArray()
        // the actual type of the arraysize can not yet be determined here
        // the ConstantFold takes care of that and converts the type if needed.
        return ArrayLiteral(InferredTypes.InferredType.unknown(), array, position = ctx.toPosition())
    }

    override fun visitStringliteral(ctx: StringliteralContext): StringLiteral {
        val text = ctx.STRING().text
        val enc = ctx.encoding?.text
        val encoding =
            if(enc!=null)
                ENCODING_BY_PREFIX[enc]
                    ?: throw SyntaxError("invalid encoding", ctx.toPosition())
            else
                Encoding.DEFAULT
        val raw = text.substring(1, text.length-1)
        try {
            return StringLiteral.fromEscaped(raw, encoding, ctx.toPosition())
        } catch(ex: IllegalArgumentException) {
            throw SyntaxError(ex.message!!, ctx.toPosition())
        }
    }

    override fun visitCharliteral(ctx: CharliteralContext): CharLiteral {
        val text = ctx.SINGLECHAR().text
        val enc = ctx.encoding?.text
        val encoding =
            if(enc!=null)
                ENCODING_BY_PREFIX[enc]
                    ?: throw SyntaxError("invalid encoding", ctx.toPosition())
            else
                Encoding.DEFAULT
        val raw = text.substring(1, text.length - 1)
        try {
            return CharLiteral.fromEscaped(raw, encoding, ctx.toPosition())
        } catch(ex: IllegalArgumentException) {
            throw SyntaxError(ex.message!!, ctx.toPosition())
        }
    }

    override fun visitFloatliteral(ctx: FloatliteralContext): NumericLiteral {
        val floatvalue = ctx.text.replace("_","").toDouble()
        return NumericLiteral(BaseDataType.FLOAT, floatvalue, ctx.toPosition())
    }

    override fun visitInlineasm(ctx: InlineasmContext): InlineAssembly {
        val isIR = when(val type = ctx.directivename().UNICODEDNAME().text) {
            "asm" -> false
            "ir" -> true
            else -> throw SyntaxError("unknown inline asm type $type", ctx.toPosition())
        }
        val text = ctx.INLINEASMBLOCK().text
        return InlineAssembly(text.substring(2, text.length-2), isIR, ctx.toPosition())
    }

    override fun visitSubroutine(ctx: SubroutineContext): Subroutine {
        val isPrivate = ctx.PRIVATE() != null
        val name = getname(ctx.identifier())
        val parameters = ctx.sub_params()?.sub_param()?.mapTo(mutableListOf()) { it.accept(this) as SubroutineParameter } ?: mutableListOf()
        val returntypes = ctx.sub_return_part()?.datatype()?.mapTo(mutableListOf()) { dataTypeFor(it)!! } ?: mutableListOf()
        val statements = ctx.statement_block().accept(this) as AnonymousScope
        return Subroutine(
            name,
            parameters,
            returntypes,
            emptyList(),
            emptyList(),
            emptySet(),
            asmAddress = null,
            isAsmSubroutine = false,
            inline = ctx.INLINE() != null,
            isPrivate = isPrivate,
            statements = statements.statements,
            position = ctx.toPosition()
        )
    }

    override fun visitStatement_block(ctx: Statement_blockContext): AnonymousScope {
        val statements = ctx.statement().mapTo(mutableListOf()) { it.accept(this) as Statement }
        return AnonymousScope(statements, ctx.toPosition())
    }

    override fun visitSub_param(pctx: Sub_paramContext): SubroutineParameter {
        val decl = pctx.vardecl()
        val tags = decl.TAG().map { t -> t.text }
        val validTags = arrayOf("@zp", "@requirezp", "@nozp", "@nosplit", "@shared")
        for(tag in tags) {
            if(tag !in validTags)
                throw SyntaxError("invalid parameter tag '$tag'", pctx.toPosition())
        }
        val zp = getZpOption(tags)
        var datatype = dataTypeFor(decl.datatype()) ?: DataType.UNDEFINED
        val arrayIndices = decl.arrayindex()
        if(decl.EMPTYARRAYSIG()!=null || arrayIndices.isNotEmpty()) {
            if(arrayIndices.size > 1) {
                throw SyntaxError("2D arrays cannot be used as subroutine parameters", decl.toPosition())
            }
            datatype = datatype.elementToArray()
        }

        val identifiers = decl.identifierlist().identifier()
        if(identifiers.size>1)
            throw SyntaxError("parameter name must be singular", identifiers[0].toPosition())
        val identifiername = getname(identifiers[0])

        val (registerorpair, statusregister) = parseParamRegister(pctx.register, pctx.toPosition())
        if(statusregister!=null) {
            throw SyntaxError("can't use status register as param for normal subroutines", Position(pctx.toPosition().file, pctx.register.line, pctx.register.charPositionInLine+1, pctx.register.charPositionInLine+1))
        }
        return SubroutineParameter(identifiername, datatype, zp, registerorpair, pctx.toPosition())
    }

    override fun visitAsmsubroutine(ctx: AsmsubroutineContext): Subroutine {
        val isPrivate = ctx.PRIVATE() != null
        val inline = ctx.INLINE()!=null
        val ad = asmSubDecl(ctx.asmsub_decl())
        val statements = ctx.statement_block().accept(this) as AnonymousScope

        return Subroutine(ad.name,
            ad.parameters,
            ad.returntypes,
            ad.asmParameterRegisters,
            ad.asmReturnvaluesRegisters,
            ad.asmClobbers, null, true, inline, false, isPrivate,
            statements.statements, ctx.toPosition()
        )
    }

    override fun visitExtsubroutine(ctx: ExtsubroutineContext): Subroutine {
        val subdecl = asmSubDecl(ctx.asmsub_decl())
        val constbank = (ctx.constbank?.accept(this) as NumericLiteral?)?.number?.toUInt()?.toUByte()
        val varbank = ctx.varbank?.accept(this) as IdentifierReference?
        val addr = ctx.address.accept(this) as Expression
        val address = Subroutine.Address(constbank, varbank, addr)
        return Subroutine(subdecl.name, subdecl.parameters, subdecl.returntypes,
            subdecl.asmParameterRegisters, subdecl.asmReturnvaluesRegisters,
            subdecl.asmClobbers, address, true, inline = false, isPrivate = false, statements = mutableListOf(), position = ctx.toPosition()
        )
    }

    override fun visitIf_stmt(ctx: If_stmtContext): IfElse {
        val condition = ctx.expression().accept(this) as Expression
        val truepart = stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
        val elsepart = ctx.else_part()?.accept(this) as AnonymousScope? ?: AnonymousScope.empty()
        return IfElse(condition, truepart, elsepart, ctx.toPosition())
    }

    override fun visitElse_part(ctx: Else_partContext): AnonymousScope {
        return stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
    }

    override fun visitIf_expression(ctx: If_expressionContext): IfExpression {
        val (condition, truevalue, falsevalue) = ctx.expression().map { it.accept(this) as Expression }
        return IfExpression(condition, truevalue, falsevalue, ctx.toPosition())
    }

    override fun visitBranchcondition_expression(ctx: Branchcondition_expressionContext): BranchConditionExpression {
        val condition = branchCondition(ctx.branchcondition())
        val (truevalue, falsevalue) = ctx.expression().map { it.accept(this) as Expression }
        return BranchConditionExpression(condition, truevalue, falsevalue, ctx.toPosition())
    }

    override fun visitBranch_stmt(ctx: Branch_stmtContext): ConditionalBranch {
        val branchcondition = branchCondition(ctx.branchcondition())
        val truepart = stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
        val elsepart = ctx.else_part()?.accept(this) as AnonymousScope? ?: AnonymousScope.empty()
        return ConditionalBranch(branchcondition, truepart, elsepart, ctx.toPosition())
    }

    override fun visitForloop(ctx: ForloopContext): ForLoop {
        val loopvar = ctx.scoped_identifier().accept(this) as IdentifierReference
        val iterable = ctx.expression().accept(this) as Expression
        val scope = stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
        return ForLoop(loopvar, iterable, scope, ctx.toPosition())
    }

    override fun visitWhileloop(ctx: WhileloopContext): WhileLoop {
        val condition = ctx.expression().accept(this) as Expression
        val statements = stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
        return WhileLoop(condition, statements, ctx.toPosition())
    }

    override fun visitUntilloop(ctx: UntilloopContext): UntilLoop {
        val condition = ctx.expression().accept(this) as Expression
        val statements = stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
        return UntilLoop(statements, condition, ctx.toPosition())
    }

    override fun visitRepeatloop(ctx: RepeatloopContext): RepeatLoop {
        val iterations = ctx.expression()?.accept(this) as Expression?
        val statements = stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
        return RepeatLoop(iterations, statements, ctx.toPosition())
    }

    override fun visitUnrollloop(ctx: UnrollloopContext): UnrollLoop {
        val iterations = ctx.expression().accept(this) as Expression
        val statements = stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
        return UnrollLoop(iterations, statements, ctx.toPosition())
    }

    override fun visitWhenstmt(ctx: WhenstmtContext): When {
        val condition = ctx.expression().accept(this) as Expression
        val choices = ctx.when_choice()?.mapTo(mutableListOf()) { it.accept(this) as WhenChoice } ?: mutableListOf()
        return When(condition, choices, ctx.toPosition())
    }

    override fun visitWhen_choice(ctx: When_choiceContext): WhenChoice {
        val values = ctx.expression_list()?.expression()?.mapTo(mutableListOf()) { it.accept(this) as Expression }
        val statements = stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
        return WhenChoice(values, statements, ctx.toPosition())
    }

    override fun visitOngoto(ctx: OngotoContext): OnGoto {
        val elsepart = ctx.else_part()?.accept(this) as AnonymousScope? ?: AnonymousScope.empty()
        val isCall = ctx.kind.text == "call"
        val index = ctx.expression().accept(this) as Expression
        val labels = ctx.directivenamelist().scoped_identifier().map { it.accept(this) as IdentifierReference }
        return OnGoto(isCall, index, labels, elsepart, ctx.toPosition())
    }

    override fun visitStaticstructinitializer(ctx: StaticstructinitializerContext): StaticStructInitializer {
        if(ctx.POINTER()==null)
            throw SyntaxError("struct initializer requires '^^' before struct name", ctx.toPosition())
        val struct = ctx.scoped_identifier().accept(this) as IdentifierReference
        val array = ctx.arrayliteral()
        val args = if(array==null) mutableListOf() else (array.accept(this) as ArrayLiteral).value.toMutableList()
        return StaticStructInitializer(struct, args, ctx.toPosition())
    }

    override fun visitPointerDereferenceTarget(ctx: PointerDereferenceTargetContext): AssignTarget {
        return when (val deref = ctx.pointerdereference().accept(this)) {
            is PtrDereference -> AssignTarget(null, null, null, null, false, pointerDereference = deref, position = deref.position)
            is ArrayIndexedPtrDereference -> AssignTarget(null, null, null, null, false, arrayIndexedDereference = deref, position = deref.position)
            else -> throw FatalAstException("weird dereference ${ctx.toPosition()}")
        }
    }

    /**
     * Visits a pointer dereference expression.
     *
     * The grammar structure is: (prefix '.')? derefchain ('.' field)?
     * This normalizes everything into a single chain of identifiers with optional array indices.
     *
     * Examples:
     * - `foo^^` → PtrDereference(["foo"], derefLast=true)
     * - `foo.bar^^` → PtrDereference(["foo", "bar"], derefLast=true)
     * - `foo[0]^^` → ArrayIndexedPtrDereference([("foo", ArrayIndex), ("", null)], derefLast=true)
     * - `foo.bar[0]^^.baz` → ArrayIndexedPtrDereference([("foo", null), ("bar", ArrayIndex), ("baz", null)], derefLast=false)
     */
    override fun visitPointerdereference(ctx: PointerdereferenceContext): Expression {
        // Step 1: Extract prefix identifiers (if any)
        val scopeprefix = ctx.prefix?.accept(this) as IdentifierReference?
        val prefixNames = scopeprefix?.nameInSource ?: emptyList()

        // Step 2: Extract derefchain as (name, arrayIndex?) pairs
        val derefs = ctx.derefchain()!!.singlederef().map {
            it.identifier().text to it.arrayindex()?.accept(this) as ArrayIndex?
        }

        // Step 3: Merge prefix with derefchain - prefix elements have no array index
        val fullChain = prefixNames.map { it to (null as ArrayIndex?) } + derefs

        // Step 4: Add optional field identifier (if present)
        val finalChain = if (ctx.field != null) fullChain + (ctx.field.text to null) else fullChain

        // Step 5: Create appropriate AST node based on whether there are array indices
        return if (finalChain.all { it.second == null }) {
            // No array indexing - use simple PtrDereference
            PtrDereference(finalChain.map { it.first }, ctx.field == null, ctx.toPosition())
        } else {
            // Has array indexing - use ArrayIndexedPtrDereference
            ArrayIndexedPtrDereference(finalChain, ctx.field == null, ctx.toPosition())
        }
    }

    override fun visitStructdeclaration(ctx: StructdeclarationContext): StructDecl {
        val name = getname(ctx.identifier())
        val fields: List<Pair<DataType, List<String>>> = ctx.structfielddecl().map { getStructField(it) }
        val flattened = fields.flatMap { (dt, names) -> names.map { dt to it}}
        return StructDecl(name, flattened.toTypedArray(), ctx.toPosition())
    }

    private fun getStructField(ctx: StructfielddeclContext): Pair<DataType, List<String>> {
        val identifiers = ctx.identifierlist()?.identifier() ?: emptyList()
        val dt = dataTypeFor(ctx.datatype())!!
        return dt to identifiers.map { getname(it) }
    }

    override fun visitSwap(ctx: SwapContext): Swap {
        val (t1, t2) = ctx.assign_target().map { it.accept(this) as AssignTarget }
        return Swap(t1, t2, ctx.toPosition())
    }

    override fun visitEnum(ctx: EnumContext): Enumeration {
        val name = getname(ctx.identifier())
        val members1 = ctx.enum_member().map {
            getname(it.identifier()) to it.integerliteral()?.accept(this) as NumericLiteral?
        }
        val datatypes = members1.mapNotNull { it.second?.type }
        val largestType = datatypes.fold(BaseDataType.UBYTE) { acc, dt -> if (dt.largerSizeThan(acc)) dt else acc }
        val members = members1.map {
            it.first to it.second?.number?.toInt()
        }.toTypedArray()
        return Enumeration(name, largestType, members, ctx.toPosition())
    }


    override fun visitModule_element(ctx: Module_elementContext): Node = visitChildren(ctx)
    override fun visitBlock_statement(ctx: Block_statementContext): Statement = visitChildren(ctx) as Statement
    override fun visitStatement(ctx: StatementContext): Statement = visitChildren(ctx) as Statement

    override fun visitVariabledeclaration(ctx: VariabledeclarationContext): VarDecl = visitChildren(ctx) as VarDecl
    override fun visitLiteralvalue(ctx: LiteralvalueContext): Expression = visitChildren(ctx) as Expression

    override fun visitEnum_member(ctx: Enum_memberContext?) = throw FatalAstException("should not be called")
    override fun visitBasedatatype(ctx: BasedatatypeContext) = throw FatalAstException("should not be called")
    override fun visitDirectivenamelist(ctx: DirectivenamelistContext) = throw FatalAstException("should not be called")
    override fun visitAsmsub_decl(ctx: Asmsub_declContext) = throw FatalAstException("should not be called")
    override fun visitAsmsub_params(ctx: Asmsub_paramsContext) = throw FatalAstException("should not be called")
    override fun visitExpression_list(ctx: Expression_listContext) = throw FatalAstException("should not be called")
    override fun visitBranchcondition(ctx: BranchconditionContext) = throw FatalAstException("should not be called")
    override fun visitDatatype(ctx: DatatypeContext) = throw FatalAstException("should not be called")
    override fun visitIdentifierlist(ctx: IdentifierlistContext) = throw FatalAstException("should not be called")
    override fun visitDirectivename(ctx: DirectivenameContext) = throw FatalAstException("should not be called")
    override fun visitSizeof_argument(ctx: Sizeof_argumentContext) = throw FatalAstException("should not be called")
    override fun visitReturnvalues(ctx: ReturnvaluesContext) = throw FatalAstException("should not be called")
    override fun visitTypecast(ctx: TypecastContext) = throw FatalAstException("should not be called")
    override fun visitSub_params(ctx: Sub_paramsContext) = throw FatalAstException("should not be called")
    override fun visitAsmsub_param(ctx: Asmsub_paramContext) = throw FatalAstException("should not be called")
    override fun visitAsmsub_clobbers(ctx: Asmsub_clobbersContext) = throw FatalAstException("should not be called")
    override fun visitClobber(ctx: ClobberContext) = throw FatalAstException("should not be called")
    override fun visitAsmsub_returns(ctx: Asmsub_returnsContext) = throw FatalAstException("should not be called")
    override fun visitAsmsub_return(ctx: Asmsub_returnContext) = throw FatalAstException("should not be called")
    override fun visitSub_return_part(ctx: Sub_return_partContext) = throw FatalAstException("should not be called")
    override fun visitStructfielddecl(ctx: StructfielddeclContext) = throw FatalAstException("should not be called")
    override fun visitDerefchain(ctx: DerefchainContext) = throw FatalAstException("should not be called")
    override fun visitSinglederef(ctx: SinglederefContext) = throw FatalAstException("should not be called")
    override fun visitPointertype(ctx: PointertypeContext) = throw FatalAstException("should not be called")
    override fun visitAsmsub_signature(ctx: Asmsub_signatureContext?)= throw FatalAstException("should not be called")

    private fun getname(identifier: IdentifierContext): String = identifier.children[0].text

    private fun ParserRuleContext.toPosition() : Position {
        val filename = cachedFileName ?: run {
            val pathString = start.inputStream.sourceName
            val resolved = if(SourceCode.isRegularFilesystemPath(pathString)) {
                val path = Path(pathString)
                if(path.isRegularFile()) {
                    SourceCode.relative(path).toString()
                } else {
                    path.toString()
                }
            } else {
                pathString
            }
            cachedFileName = resolved
            resolved
        }

        val startToken = this.start
        val stopToken = this.stop ?: startToken

        // Handle edge case: empty file or invalid tokens
        if (startToken.line <= 0 || startToken.charPositionInLine < 0) {
            return Position(filename, 1, 1, 1)
        }

        // Simple column calculation (no tab expansion - Prog8 source rarely has tabs)
        val startCol = startToken.charPositionInLine + 1

        // For empty tokens or EOF, use startCol as endCol
        val endCol = if (stopToken.type == Token.EOF ||
                         stopToken.startIndex < 0 || stopToken.stopIndex < 0) {
            startCol
        } else if (startToken.line == stopToken.line) {
            // Same line: column of the last character of the token
            stopToken.charPositionInLine + (stopToken.stopIndex - stopToken.startIndex) + 1
        } else {
            // Multi-line token: since Position is single-line only, use startCol as minimum
            maxOf(startCol, stopToken.charPositionInLine + 1)
        }

        return Position(filename, startToken.line, startCol, endCol)
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
            else -> SplitWish.DONTCARE
        }
    }

    private fun asmSubroutineParam(pctx: Asmsub_paramContext): AsmSubroutineParameter {
        val vardecl = pctx.vardecl()
        var datatype = dataTypeFor(vardecl.datatype()) ?: DataType.UNDEFINED
        val arrayIndices = vardecl.arrayindex()
        if(vardecl.EMPTYARRAYSIG()!=null || arrayIndices.isNotEmpty()) {
            if(arrayIndices.size > 1) {
                throw SyntaxError("2D arrays cannot be used as subroutine parameters", vardecl.toPosition())
            }
            datatype = datatype.elementToArray()
        }
        val (registerorpair, statusregister) = parseParamRegister(pctx.register, pctx.toPosition())
        val identifiers = vardecl.identifierlist().identifier()
        if(identifiers.size>1)
            throw SyntaxError("parameter name must be singular", identifiers[0].toPosition())
        val identifiername = getname(identifiers[0])
        return AsmSubroutineParameter(identifiername, datatype, registerorpair, statusregister, pctx.toPosition())
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
                    throw SyntaxError("invalid register or status flag", Position(pos.file, registerTok.line, registerTok.charPositionInLine+1, registerTok.charPositionInLine+1))
                }
            }
        }
        return Pair(registerorpair, statusregister)
    }

    private fun asmReturn(rctx: Asmsub_returnContext): AsmSubroutineReturn {
        val register = rctx.register.text
        var registerorpair: RegisterOrPair? = null
        var statusregister: Statusflag? = null
        if(register!=null) {
            when (register) {
                in RegisterOrPair.names -> registerorpair = RegisterOrPair.valueOf(register)
                in Statusflag.names -> statusregister = Statusflag.valueOf(register)
                else -> throw SyntaxError("invalid register or status flag", rctx.toPosition())
            }
        }
        return AsmSubroutineReturn(
            dataTypeFor(rctx.datatype())!!,
            registerorpair,
            statusregister)
    }

    private fun cpuRegister(text: String, pos: Position): CpuRegister {
        try {
            return CpuRegister.valueOf(text)
        } catch(_: IllegalArgumentException) {
            throw SyntaxError("invalid cpu register", pos)
        }
    }

    private fun dataTypeFor(dtctx: DatatypeContext?): DataType? {
        if(dtctx==null)
            return null
        val base = baseDatatypeFor(dtctx.basedatatype())
        if(base!=null)
            return DataType.forDt(base)
        val pointer = pointerDatatypeFor(dtctx.pointertype())
        if(pointer!=null)
            return pointer
        val struct = dtctx.structtype.identifier().map { dtctx.text }
        return DataType.structInstanceFromAntlr(struct)
    }

    private fun pointerDatatypeFor(pointertype: PointertypeContext?): DataType? {
        if(pointertype==null)
            return null
        val base = baseDatatypeFor(pointertype.basedatatype())
        if(base!=null)
            return DataType.pointer(base)
        val identifier = pointertype.scoped_identifier().identifier().map { it.text}
        return DataType.pointerFromAntlr(identifier)
    }

    private fun baseDatatypeFor(ctx: BasedatatypeContext?) = if(ctx==null) null else BASE_DATATYPE_MAP[ctx.text]

    private fun stmtBlockOrSingle(statementBlock: Statement_blockContext?, statement: StatementContext?): AnonymousScope {
        return if(statementBlock!=null)
            statementBlock.accept(this) as AnonymousScope
        else if(statement!=null)
            AnonymousScope(mutableListOf(statement.accept(this) as Statement), statement.toPosition())
        else
            AnonymousScope.empty()
    }

    private fun branchCondition(ctx: BranchconditionContext) = BRANCH_CONDITION_MAP[ctx.text]
        ?: throw FatalAstException("invalid branch condition: ${ctx.text}")

    private fun asmSubDecl(ad: Asmsub_declContext): AsmsubDecl {
        val name = getname(ad.identifier())
        val sig = ad.asmsub_signature()
        val params = sig.asmsub_params()?.asmsub_param()?.map { asmSubroutineParam(it) } ?: emptyList()
        val returns = sig.asmsub_returns()?.asmsub_return()?.map { asmReturn(it) } ?: emptyList()
        val clobbers = sig.asmsub_clobbers()?.clobber()?.UNICODEDNAME()?.mapTo(mutableSetOf()) { cpuRegister(it.text, ad.toPosition()) } ?: mutableSetOf()
        val normalParameters = params.mapTo(mutableListOf()) { SubroutineParameter(it.name, it.type, it.zp, it.registerOrPair, it.position) }
        val normalReturntypes = returns.mapTo(mutableListOf()) { it.type }
        val paramRegisters = params.mapTo(mutableListOf()) { RegisterOrStatusflag(it.registerOrPair, it.statusflag) }
        val returnRegisters = returns.mapTo(mutableListOf()) { RegisterOrStatusflag(it.registerOrPair, it.statusflag) }
        return AsmsubDecl(name, normalParameters, normalReturntypes, paramRegisters, returnRegisters, clobbers)
    }

    private class AsmsubDecl(val name: String,
                             val parameters: MutableList<SubroutineParameter>,
                             val returntypes: MutableList<DataType>,
                             val asmParameterRegisters: List<RegisterOrStatusflag>,
                             val asmReturnvaluesRegisters: List<RegisterOrStatusflag>,
                             val asmClobbers: Set<CpuRegister>)

    private class AsmSubroutineParameter(name: String,
                                         type: DataType,
                                         registerOrPair: RegisterOrPair?,
                                         val statusflag: Statusflag?,
                                         position: Position) : SubroutineParameter(name, type, ZeropageWish.DONTCARE, registerOrPair, position)

    private class AsmSubroutineReturn(val type: DataType,
                                      val registerOrPair: RegisterOrPair?,
                                      val statusflag: Statusflag?)
}
