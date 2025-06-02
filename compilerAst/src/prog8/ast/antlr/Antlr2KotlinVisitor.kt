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

    override fun visitModule(ctx: ModuleContext): Module {
        val statements = ctx.module_element().map { it.accept(this) as Statement }
        return Module(statements.toMutableList(), ctx.toPosition(), source)
    }

    override fun visitBlock(ctx: BlockContext): Block {
        val name = getname(ctx.identifier())
        val address = (ctx.integerliteral()?.accept(this) as NumericLiteral?)?.number?.toUInt()
        val statements = ctx.block_statement().map { it.accept(this) as Statement }
        return Block(name, address, statements.toMutableList(), source.isFromLibrary, ctx.toPosition())
    }

    override fun visitExpression(ctx: ExpressionContext): Expression {
        if(ctx.sizeof_expression!=null) {
            val sdt = ctx.sizeof_argument().datatype()
            val datatype = if(sdt!=null) baseDatatypeFor(sdt) else null
            val expression = ctx.sizeof_argument().expression()?.accept(this) as Expression?
            val sizeof = IdentifierReference(listOf("sizeof"), ctx.toPosition())
            val arg = if (expression != null) expression else {
                require(datatype != null)
                IdentifierReference(listOf(datatype.name.lowercase()), ctx.toPosition())
            }
            return FunctionCallExpression(sizeof, mutableListOf(arg), ctx.toPosition())
        }

        if(ctx.bop!=null) {
            val operator = ctx.bop.text.trim().replace("\\s+".toRegex(), " ")
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
            // typecast is always to a base datatype
            val baseDt = baseDatatypeFor(ctx.typecast().datatype())
            return TypecastExpression(ctx.expression(0).accept(this) as Expression, baseDt, false, ctx.toPosition())
        }

        if(ctx.childCount==3 && ctx.children[0].text=="(" && ctx.children[2].text==")")
            return ctx.expression(0).accept(this) as Expression        // expression within ( )

        return visitChildren(ctx) as Expression
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
        if(ctx.directivenamelist() != null) {
            val namelist = ctx.directivenamelist().scoped_identifier().map { it.accept(this) as IdentifierReference }
            val identifiers = namelist.map { DirectiveArg(it.nameInSource.joinToString("."), null, ctx.toPosition()) }
            return Directive(ctx.directivename.text, identifiers, ctx.toPosition())
        }
        else
            return Directive(ctx.directivename.text, ctx.directivearg().map { it.accept(this) as DirectiveArg }, ctx.toPosition())
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
        return DirectiveArg(identifier?.nameInSource?.single(), integer, ctx.toPosition())
    }

    override fun visitVardecl(ctx: VardeclContext): VarDecl {
        val tags = ctx.TAG().map { it.text }
        val validTags = arrayOf("@zp", "@requirezp", "@nozp", "@split", "@nosplit", "@shared", "@alignword", "@alignpage", "@align64", "@dirty")
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

        val identifiers = ctx.identifier().map { getname(it) }
        val identifiername = identifiers[0]
        val name = if(identifiers.size==1) identifiername else "<multiple>"

        val arrayIndex = ctx.arrayindex()?.accept(this) as ArrayIndex?
        val isArray = ctx.ARRAYSIG() != null || arrayIndex != null
        val baseDt = baseDatatypeFor(ctx.datatype())
        val dt = if(isArray) DataType.arrayFor(baseDt, split!=SplitWish.NOSPLIT) else DataType.forDt(baseDt)

        return VarDecl(
            VarDeclType.VAR,        // can be changed to MEMORY or CONST as required
            VarDeclOrigin.USERCODE,
            dt,
            zp,
            split,
            arrayIndex,
            name,
            if(identifiers.size==1) emptyList() else identifiers,
            null,
            "@shared" in tags,
            if(alignword) 2u else if(align64) 64u else if(alignpage) 256u else 0u,
            "@dirty" in tags,
            ctx.toPosition()
        )
    }

    override fun visitVarinitializer(ctx: VarinitializerContext): VarDecl {
        val vardecl = ctx.vardecl().accept(this) as VarDecl
        vardecl.value = ctx.expression().accept(this) as Expression
        return vardecl
    }

    override fun visitConstdecl(ctx: ConstdeclContext): VarDecl {
        val vardecl = ctx.varinitializer().accept(this) as VarDecl
        vardecl.type = VarDeclType.CONST
        return vardecl
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
        return AssignTarget(identifier, null, null, null, false, ctx.toPosition())
    }

    override fun visitArrayindexedTarget(ctx: ArrayindexedTargetContext): AssignTarget {
        val ax = ctx.arrayindexed()
        val arrayvar = ax.scoped_identifier().accept(this) as IdentifierReference
        val index = ax.arrayindex().accept(this) as ArrayIndex
        val arrayindexed = ArrayIndexedExpression(arrayvar, index, ax.toPosition())
        return AssignTarget(null, arrayindexed, null, null, false, ctx.toPosition())
    }

    override fun visitMemoryTarget(ctx: MemoryTargetContext): AssignTarget {
        return AssignTarget(null, null,
            DirectMemoryWrite(ctx.directmemory().expression().accept(this) as Expression, ctx.toPosition()),
            null, false, ctx.toPosition())
    }

    override fun visitVoidTarget(ctx: VoidTargetContext): AssignTarget {
        return AssignTarget(null, null, null, null, true, ctx.toPosition())
    }

    override fun visitMulti_assign_target(ctx: Multi_assign_targetContext): AssignTarget {
        val targets = ctx.assign_target().map { it.accept(this) as AssignTarget }
        return AssignTarget(null, null, null, targets, false, ctx.toPosition())
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
        val index = ctx.arrayindex().accept(this) as ArrayIndex
        return ArrayIndexedExpression(identifier, index, ctx.toPosition())
    }

    override fun visitDirectmemory(ctx: DirectmemoryContext): DirectMemoryRead {
        return DirectMemoryRead(ctx.expression().accept(this) as Expression, ctx.toPosition())
    }

    override fun visitAddressof(ctx: AddressofContext): AddressOf {
        val identifier = ctx.scoped_identifier()?.accept(this) as IdentifierReference?
        val msb = ctx.ADDRESS_OF_MSB()!=null
        // note: &<  (ADDRESS_OF_LSB)  is equivalent to a regular &.
        return if (identifier != null)
            AddressOf(identifier, null, msb, ctx.toPosition())
        else {
            val array = ctx.arrayindexed()
            AddressOf(array.scoped_identifier().accept(this) as IdentifierReference,
                array.arrayindex().accept(this) as ArrayIndex,
                msb, ctx.toPosition())
        }
    }

    override fun visitFunctioncall(ctx: FunctioncallContext): FunctionCallExpression {
        val name = ctx.scoped_identifier().accept(this) as IdentifierReference
        val args = ctx.expression_list()?.expression()?.map { it.accept(this) as Expression } ?: emptyList()
        return FunctionCallExpression(name, args.toMutableList(), ctx.toPosition())
    }

    override fun visitFunctioncall_stmt(ctx: Functioncall_stmtContext): FunctionCallStatement {
        val void = ctx.VOID() != null
        val name = ctx.scoped_identifier().accept(this) as IdentifierReference
        val args = ctx.expression_list()?.expression()?.map { it.accept(this) as Expression } ?: emptyList()
        return FunctionCallStatement(name, args.toMutableList(), void, ctx.toPosition())
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
        val children = ctx.identifier().map { it.text }
        return IdentifierReference(children, ctx.toPosition())
    }

    override fun visitIntegerliteral(ctx: IntegerliteralContext): NumericLiteral {

        fun makeLiteral(literalTextWithGrouping: String, radix: Int): Pair<Double, BaseDataType> {
            val literalText = literalTextWithGrouping.replace("_", "")
            val integer: Int
            var datatype = BaseDataType.UBYTE
            when (radix) {
                10 -> {
                    integer = try {
                        literalText.toInt()
                    } catch(x: NumberFormatException) {
                        throw SyntaxError("invalid decimal literal ${x.message}", ctx.toPosition())
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
                        throw SyntaxError("invalid binary literal ${x.message}", ctx.toPosition())
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
                Encoding.entries.singleOrNull { it.prefix == enc }
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
                Encoding.entries.singleOrNull { it.prefix == enc }
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
        val text = ctx.INLINEASMBLOCK().text
        return InlineAssembly(text.substring(2, text.length-2), false, ctx.toPosition())
    }

    override fun visitInlineir(ctx: InlineirContext): InlineAssembly {
        val text = ctx.INLINEASMBLOCK().text
        return InlineAssembly(text.substring(2, text.length-2), true, ctx.toPosition())
    }

    override fun visitSubroutine(ctx: SubroutineContext): Subroutine {
        val name = getname(ctx.identifier())
        val parameters = ctx.sub_params()?.sub_param()?.map { it.accept(this) as SubroutineParameter } ?: emptyList()
        val returntypes = ctx.sub_return_part()?.datatype()?. map { dataTypeFor(it) } ?: emptyList()
        val statements = ctx.statement_block().accept(this) as AnonymousScope
        return Subroutine(
            name,
            parameters.toMutableList(),
            returntypes.toMutableList(),
            emptyList(),
            emptyList(),
            emptySet(),
            asmAddress = null,
            isAsmSubroutine = false,
            inline = false,
            statements = statements.statements,
            position = ctx.toPosition()
        )
    }

    override fun visitStatement_block(ctx: Statement_blockContext): AnonymousScope {
        val statements = ctx.statement().map { it.accept(this) as Statement }
        return AnonymousScope(statements.toMutableList(), ctx.toPosition())
    }

    override fun visitSub_param(pctx: Sub_paramContext): SubroutineParameter {
        val decl = pctx.vardecl()
        val tags = decl.TAG().map { t -> t.text }
        val validTags = arrayOf("@zp", "@requirezp", "@nozp", "@split", "@nosplit", "@shared")
        for(tag in tags) {
            if(tag !in validTags)
                throw SyntaxError("invalid parameter tag '$tag'", pctx.toPosition())
        }
        val zp = getZpOption(tags)
        val decldt = decl.datatype()
        var datatype = if(decldt!=null) dataTypeFor(decldt) else DataType.UNDEFINED
        if(decl.ARRAYSIG()!=null || decl.arrayindex()!=null)
            datatype = datatype.elementToArray()

        val identifiers = decl.identifier()
        if(identifiers.size>1)
            throw SyntaxError("parameter name must be singular", identifiers[0].toPosition())
        val identifiername = getname(identifiers[0])

        val (registerorpair, statusregister) = parseParamRegister(pctx.register, pctx.toPosition())
        if(statusregister!=null) {
            throw SyntaxError("can't use status register as param for normal subroutines", Position(pctx.toPosition().file, pctx.register.line, pctx.register.charPositionInLine, pctx.register.charPositionInLine+1))
        }
        return SubroutineParameter(identifiername, datatype, zp, registerorpair, pctx.toPosition())
    }

    override fun visitAsmsubroutine(ctx: AsmsubroutineContext): Subroutine {
        val inline = ctx.INLINE()!=null
        val ad = asmSubDecl(ctx.asmsub_decl())
        val statements = ctx.statement_block().accept(this) as AnonymousScope

        return Subroutine(ad.name,
            ad.parameters.toMutableList(),
            ad.returntypes.toMutableList(),
            ad.asmParameterRegisters,
            ad.asmReturnvaluesRegisters,
            ad.asmClobbers, null, true, inline,
            statements = statements.statements, position = ctx.toPosition()
        )
    }

    override fun visitExtsubroutine(ctx: ExtsubroutineContext): Subroutine {
        val subdecl = asmSubDecl(ctx.asmsub_decl())
        val constbank = (ctx.constbank?.accept(this) as NumericLiteral?)?.number?.toUInt()?.toUByte()
        val varbank = ctx.varbank?.accept(this) as IdentifierReference?
        val addr = ctx.address.accept(this) as Expression
        val address = Subroutine.Address(constbank, varbank, addr)
        return Subroutine(subdecl.name, subdecl.parameters.toMutableList(), subdecl.returntypes.toMutableList(),
            subdecl.asmParameterRegisters, subdecl.asmReturnvaluesRegisters,
            subdecl.asmClobbers, address, true, inline = false, statements = mutableListOf(), position = ctx.toPosition()
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
        val choices = ctx.when_choice()?.map { it.accept(this) as WhenChoice }?.toMutableList() ?: mutableListOf()
        return When(condition, choices, ctx.toPosition())
    }

    override fun visitWhen_choice(ctx: When_choiceContext): WhenChoice {
        val values = ctx.expression_list()?.expression()?.map { it.accept(this) as Expression }
        val statements = stmtBlockOrSingle(ctx.statement_block(), ctx.statement())
        return WhenChoice(values?.toMutableList(), statements, ctx.toPosition())
    }

    override fun visitOngoto(ctx: OngotoContext): OnGoto {
        val elsepart = ctx.else_part()?.accept(this) as AnonymousScope? ?: AnonymousScope.empty()
        val isCall = ctx.kind.text == "call"
        val index = ctx.expression().accept(this) as Expression
        val labels = ctx.directivenamelist().scoped_identifier().map { it.accept(this) as IdentifierReference }
        return OnGoto(isCall, index, labels, elsepart, ctx.toPosition())
    }


    override fun visitModule_element(ctx: Module_elementContext): Node = visitChildren(ctx)
    override fun visitBlock_statement(ctx: Block_statementContext): Statement = visitChildren(ctx) as Statement
    override fun visitStatement(ctx: StatementContext): Statement = visitChildren(ctx) as Statement
    override fun visitVariabledeclaration(ctx: VariabledeclarationContext): VarDecl = visitChildren(ctx) as VarDecl
    override fun visitLiteralvalue(ctx: LiteralvalueContext): Expression = visitChildren(ctx) as Expression


    override fun visitDirectivenamelist(ctx: DirectivenamelistContext) = throw FatalAstException("should not be called")
    override fun visitAsmsub_decl(ctx: Asmsub_declContext?) = throw FatalAstException("should not be called")
    override fun visitAsmsub_params(ctx: Asmsub_paramsContext) = throw FatalAstException("should not be called")
    override fun visitExpression_list(ctx: Expression_listContext) = throw FatalAstException("should not be called")
    override fun visitBranchcondition(ctx: BranchconditionContext) = throw FatalAstException("should not be called")
    override fun visitDatatype(ctx: DatatypeContext) = throw FatalAstException("should not be called")
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


    private fun getname(identifier: IdentifierContext): String = identifier.children[0].text

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

    private fun asmSubroutineParam(pctx: Asmsub_paramContext): AsmSubroutineParameter {
        val vardecl = pctx.vardecl()
        val decldt = vardecl.datatype()
        var datatype = if(decldt!=null) dataTypeFor(decldt) else DataType.UNDEFINED
        if(vardecl.ARRAYSIG()!=null || vardecl.arrayindex()!=null)
            datatype = datatype.elementToArray()
        val (registerorpair, statusregister) = parseParamRegister(pctx.register, pctx.toPosition())
        val identifiers = vardecl.identifier()
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
                    throw SyntaxError("invalid register or status flag", Position(pos.file, registerTok.line, registerTok.charPositionInLine, registerTok.charPositionInLine+1))
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
            dataTypeFor(rctx.datatype()),
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

    private fun dataTypeFor(it: DatatypeContext) = DataType.forDt(baseDatatypeFor(it))

    private fun baseDatatypeFor(it: DatatypeContext) = BaseDataType.valueOf(it.text.uppercase())

    private fun stmtBlockOrSingle(statementBlock: Statement_blockContext?, statement: StatementContext?): AnonymousScope {
        return if(statementBlock!=null)
            statementBlock.accept(this) as AnonymousScope
        else if(statement!=null)
            AnonymousScope(mutableListOf(statement.accept(this) as Statement), statement.toPosition())
        else
            AnonymousScope.empty()
    }

    private fun branchCondition(ctx: BranchconditionContext) = BranchCondition.valueOf(ctx.text.substringAfter('_').uppercase())

    private fun asmSubDecl(ad: Asmsub_declContext): AsmsubDecl {
        val name = getname(ad.identifier())
        val params = ad.asmsub_params()?.asmsub_param()?.map { asmSubroutineParam(it) } ?: emptyList()
        val returns = ad.asmsub_returns()?.asmsub_return()?.map { asmReturn(it) } ?: emptyList()
        val clobbers = ad.asmsub_clobbers()?.clobber()?.NAME()?.map { cpuRegister(it.text, ad.toPosition()) } ?: emptyList()
        val normalParameters = params.map { SubroutineParameter(it.name, it.type, it.zp, it.registerOrPair, it.position) }
        val normalReturntypes = returns.map { it.type }
        val paramRegisters = params.map { RegisterOrStatusflag(it.registerOrPair, it.statusflag) }
        val returnRegisters = returns.map { RegisterOrStatusflag(it.registerOrPair, it.statusflag) }
        return AsmsubDecl(name, normalParameters, normalReturntypes, paramRegisters, returnRegisters, clobbers.toSet())
    }

    private class AsmsubDecl(val name: String,
                             val parameters: List<SubroutineParameter>,
                             val returntypes: List<DataType>,
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
