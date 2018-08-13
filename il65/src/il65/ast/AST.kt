package il65.ast

import il65.parser.il65Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import java.nio.file.Paths


/**************************** AST Data classes ****************************/

enum class DataType {
    BYTE,
    WORD,
    FLOAT,
    STR,
    STR_P,
    STR_S,
    STR_PS
}

enum class Register {
    A,
    X,
    Y,
    AX,
    AY,
    XY,
    SI,
    SC,
    SZ
}


open class AstException(override var message: String) : Exception(message)
class ExpressionException(override var message: String) : AstException(message)

class SyntaxError(override var message: String, val position: Position?) : AstException(message) {
    fun printError() {
        val location = if(position == null) "" else position.toString()
        System.err.println("$location $message")
    }
}


data class Position(val file: String, val line: Int, val startCol: Int, val endCol: Int) {
    override fun toString(): String = "[$file: line $line col $startCol-$endCol]"
}


interface IAstProcessor {
    // override the ones you want to act upon
    fun process(module: Module) {
    }
    fun process(expr: PrefixExpression): IExpression {
        return expr
    }
    fun process(expr: BinaryExpression): IExpression {
        return expr
    }
    fun process(directive: Directive): IStatement {
        return directive
    }
    fun process(block: Block): IStatement {
        return block
    }
    fun process(decl: VarDecl): IStatement {
        return decl
    }
    fun process(subroutine: Subroutine): IStatement {
        return subroutine
    }
    fun process(jump: Jump): IStatement {
        return jump
    }
    fun process(functionCall: FunctionCall): IExpression {
        return functionCall
    }
}


interface Node {
    var position: Position?     // optional for the sake of easy unit testing
    var parent: Node?           // will be linked correctly later
    fun linkParents(parent: Node)
}


interface IStatement : Node {
    fun process(processor: IAstProcessor) : IStatement
}


data class Module(val name: String,
                  var lines: List<IStatement>) : Node {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent=parent
    }
    fun linkParents() {
        parent = null
        lines.forEach {it.linkParents(this)}
    }

    fun process(processor: IAstProcessor) {
        processor.process(this)
    }
}


data class Block(val name: String,
                 val address: Int?,
                 var statements: List<IStatement>) : IStatement {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        statements.forEach {it.linkParents(this)}
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


data class Directive(val directive: String, val args: List<DirectiveArg>) : IStatement {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        args.forEach{it.linkParents(this)}
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


data class DirectiveArg(val str: String?, val name: String?, val int: Int?) : Node {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}


data class Label(val name: String) : IStatement {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun process(processor: IAstProcessor) = this
}


data class Return(var values: List<IExpression>) : IStatement {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        values.forEach {it.linkParents(this)}
    }

    override fun process(processor: IAstProcessor): IStatement {
        values = values.map { it.process(processor) }
        return this
    }
}


data class ArraySpec(var x: IExpression, var y: IExpression?) : Node {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        x.linkParents(this)
        y?.linkParents(this)
    }

    fun process(processor: IAstProcessor) {
        x = x.process(processor)
        y = y?.process(processor)
    }
}


enum class VarDeclType {
    VAR,
    CONST,
    MEMORY
}

data class VarDecl(val type: VarDeclType,
                   val datatype: DataType,
                   val arrayspec: ArraySpec?,
                   val name: String,
                   var value: IExpression?) : IStatement {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        arrayspec?.linkParents(this)
        value?.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    val isScalar = arrayspec==null
    val isArray = arrayspec!=null && arrayspec.y==null
    val isMatrix = arrayspec?.y != null
    val arraySizeX : Int?
        get() = arrayspec?.x?.constValue()?.intvalue
    val arraySizeY : Int?
        get() = arrayspec?.y?.constValue()?.intvalue
}


data class Assignment(var target: AssignTarget, val aug_op : String?, var value: IExpression) : IStatement {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        value.linkParents(this)
    }

    override fun process(processor: IAstProcessor): IStatement {
        target = target.process(processor)
        value = value.process(processor)
        return this
    }
}

data class AssignTarget(val register: Register?, val identifier: Identifier?) : Node {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
    }

    fun process(processor: IAstProcessor) = this
}


interface IExpression: Node {
    fun constValue() : LiteralValue?
    fun process(processor: IAstProcessor): IExpression
}


// note: some expression elements are mutable, to be able to rewrite/process the expression tree

data class PrefixExpression(val operator: String, var expression: IExpression) : IExpression {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override fun constValue(): LiteralValue? {
        throw ExpressionException("should have been optimized away before const value was asked")
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


data class BinaryExpression(var left: IExpression, val operator: String, var right: IExpression) : IExpression {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        left.linkParents(this)
        right.linkParents(this)
    }

    override fun constValue(): LiteralValue? {
        throw ExpressionException("should have been optimized away before const value was asked")
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}

data class LiteralValue(val intvalue: Int? = null,
                        val floatvalue: Double? = null,
                        val strvalue: String? = null,
                        val arrayvalue: List<IExpression>? = null) : IExpression {
    override var position: Position? = null
    override var parent: Node? = null

    fun asInt(errorIfNotNumeric: Boolean=true): Int? {
        return when {
            intvalue!=null -> intvalue
            floatvalue!=null -> floatvalue.toInt()
            else -> {
                if((strvalue!=null || arrayvalue!=null) && errorIfNotNumeric)
                    throw AstException("attempt to get int value from non-integer $this")
                else null
            }
        }
    }

    fun asFloat(errorIfNotNumeric: Boolean=true): Double? {
        return when {
            floatvalue!=null -> floatvalue
            intvalue!=null -> intvalue.toDouble()
            else -> {
                if((strvalue!=null || arrayvalue!=null) && errorIfNotNumeric)
                    throw AstException("attempt to get float value from non-integer $this")
                else null
            }
        }
    }

    override fun linkParents(parent: Node) {
        this.parent = parent
        arrayvalue?.forEach {it.linkParents(this)}
    }

    override fun constValue(): LiteralValue?  = this
    override fun process(processor: IAstProcessor) = this
}


data class RangeExpr(var from: IExpression, var to: IExpression) : IExpression {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        from.linkParents(this)
        to.linkParents(this)
    }

    override fun constValue(): LiteralValue? = null
    override fun process(processor: IAstProcessor): IExpression {
        from = from.process(processor)
        to = to.process(processor)
        return this
    }
}


data class RegisterExpr(val register: Register) : IExpression {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun constValue(): LiteralValue? = null
    override fun process(processor: IAstProcessor) = this
}


data class Identifier(val name: String, val scope: List<String>) : IExpression {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun constValue(): LiteralValue? {
        // @todo should look up the location and return its value if that is a compile time const
        return null
    }

    override fun process(processor: IAstProcessor) = this
}


data class PostIncrDecr(var target: AssignTarget, val operator: String) : IStatement {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
    }

    override fun process(processor: IAstProcessor): IStatement {
        target = target.process(processor)
        return this
    }
}


data class Jump(val address: Int?, val identifier: Identifier?) : IStatement {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


data class FunctionCall(var location: Identifier, var arglist: List<IExpression>) : IExpression {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        location.linkParents(this)
        arglist.forEach { it.linkParents(this) }
    }

    override fun constValue(): LiteralValue? {
        // if the function is a built-in function and the args are consts, should evaluate!
        return null
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


data class InlineAssembly(val assembly: String) : IStatement {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun process(processor: IAstProcessor) = this
}


data class Subroutine(val name: String,
                      val parameters: List<SubroutineParameter>,
                      val returnvalues: List<SubroutineReturnvalue>,
                      val address: Int?,
                      var statements: List<IStatement>) : IStatement {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
        parameters.forEach { it.parent=this }
        returnvalues.forEach { it.parent=this }
        statements.forEach { it.parent=this }
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


data class SubroutineParameter(val name: String, val register: Register) : Node {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}


data class SubroutineReturnvalue(val register: Register, val clobbered: Boolean) : Node {
    override var position: Position? = null
    override var parent: Node? = null

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}



/***************** Antlr Extension methods to create AST ****************/

fun il65Parser.ModuleContext.toAst(name: String, withPosition: Boolean) : Module {
    val module = Module(name, modulestatement().map { it.toAst(withPosition) })
    module.position = toPosition(withPosition)
    return module
}


/************** Helper extesion methods (private) ************/

private fun ParserRuleContext.toPosition(withPosition: Boolean) : Position? {
    val file = Paths.get(this.start.inputStream.sourceName).fileName.toString()
    return if (withPosition)
        // note: be ware of TAB characters in the source text, they count as 1 column...
        Position(file, start.line, start.charPositionInLine, stop.charPositionInLine+stop.text.length)
    else
        null
}


private fun il65Parser.ModulestatementContext.toAst(withPosition: Boolean) : IStatement {
    val directive = directive()?.toAst(withPosition)
    if(directive!=null) return directive

    val block = block()?.toAst(withPosition)
    if(block!=null) return block

    throw UnsupportedOperationException(text)
}


private fun il65Parser.BlockContext.toAst(withPosition: Boolean) : IStatement {
    val block= Block(identifier().text,
            integerliteral()?.toAst(),
            statement().map { it.toAst(withPosition) })
    block.position = toPosition(withPosition)
    return block
}


private fun il65Parser.StatementContext.toAst(withPosition: Boolean) : IStatement {
    val vardecl = vardecl()
    if(vardecl!=null) {
        val decl= VarDecl(VarDeclType.VAR,
                vardecl.datatype().toAst(),
                vardecl.arrayspec()?.toAst(withPosition),
                vardecl.identifier().text,
                null)
        decl.position = vardecl.toPosition(withPosition)
        return decl
    }

    val varinit = varinitializer()
    if(varinit!=null) {
        val decl= VarDecl(VarDeclType.VAR,
                varinit.datatype().toAst(),
                varinit.arrayspec()?.toAst(withPosition),
                varinit.identifier().text,
                varinit.expression().toAst(withPosition))
        decl.position = varinit.toPosition(withPosition)
        return decl
    }

    val constdecl = constdecl()
    if(constdecl!=null) {
        val cvarinit = constdecl.varinitializer()
        val decl = VarDecl(VarDeclType.CONST,
                cvarinit.datatype().toAst(),
                cvarinit.arrayspec()?.toAst(withPosition),
                cvarinit.identifier().text,
                cvarinit.expression().toAst(withPosition))
        decl.position = cvarinit.toPosition(withPosition)
        return decl
    }

    val memdecl = memoryvardecl()
    if(memdecl!=null) {
        val mvarinit = memdecl.varinitializer()
        val decl = VarDecl(VarDeclType.MEMORY,
                mvarinit.datatype().toAst(),
                mvarinit.arrayspec()?.toAst(withPosition),
                mvarinit.identifier().text,
                mvarinit.expression().toAst(withPosition))
        decl.position = mvarinit.toPosition(withPosition)
        return decl
    }

    val assign = assignment()
    if (assign!=null) {
        val ast =Assignment(assign.assign_target().toAst(withPosition),
                null, assign.expression().toAst(withPosition))
        ast.position = assign.toPosition(withPosition)
        return ast
    }

    val augassign = augassignment()
    if (augassign!=null) {
        val aug= Assignment(augassign.assign_target().toAst(withPosition),
                augassign.operator.text,
                augassign.expression().toAst(withPosition))
        aug.position = augassign.toPosition(withPosition)
        return aug
    }

    val post = postincrdecr()
    if(post!=null) {
        val ast = PostIncrDecr(post.assign_target().toAst(withPosition), post.operator.text)
        ast.position = post.toPosition(withPosition)
        return ast
    }

    val directive = directive()?.toAst(withPosition)
    if(directive!=null) return directive

    val label = labeldef()?.toAst(withPosition)
    if(label!=null) return label

    val jump = unconditionaljump()?.toAst(withPosition)
    if(jump!=null) return jump

    val returnstmt = returnstmt()
    if(returnstmt!=null) return Return(returnstmt.expression_list().toAst(withPosition))

    val sub = subroutine()?.toAst(withPosition)
    if(sub!=null) return sub

    val asm = inlineasm()?.toAst(withPosition)
    if(asm!=null) return asm

    throw UnsupportedOperationException(text)
}


private fun il65Parser.InlineasmContext.toAst(withPosition: Boolean): IStatement {
    val asm = InlineAssembly(INLINEASMBLOCK().text)
    asm.position = toPosition(withPosition)
    return asm
}


private fun il65Parser.UnconditionaljumpContext.toAst(withPosition: Boolean): IStatement {

    val address = integerliteral()?.toAst()
    val identifier =
            if(identifier()!=null) identifier()?.toAst(withPosition)
            else scoped_identifier()?.toAst(withPosition)

    val jump = Jump(address, identifier)
    jump.position = toPosition(withPosition)
    return jump
}


private fun il65Parser.LabeldefContext.toAst(withPosition: Boolean): IStatement {
    val lbl = Label(this.children[0].text)
    lbl.position = toPosition(withPosition)
    return lbl
}


private fun il65Parser.SubroutineContext.toAst(withPosition: Boolean) : Subroutine {
    val sub = Subroutine(identifier().text,
            if(sub_params()==null) emptyList() else sub_params().toAst(),
            if(sub_returns()==null) emptyList() else sub_returns().toAst(),
            sub_address()?.integerliteral()?.toAst(),
            if(sub_body()==null) emptyList() else sub_body().statement().map {it.toAst(withPosition)})
    sub.position = toPosition(withPosition)
    return sub
}


private fun il65Parser.Sub_paramsContext.toAst(): List<SubroutineParameter> =
        sub_param().map { SubroutineParameter(it.identifier().text, it.register().toAst()) }


private fun il65Parser.Sub_returnsContext.toAst(): List<SubroutineReturnvalue> =
        sub_return().map {
            val isClobber = it.childCount==2 && it.children[1].text == "?"
            SubroutineReturnvalue(it.register().toAst(), isClobber)
        }


private fun il65Parser.Assign_targetContext.toAst(withPosition: Boolean) : AssignTarget {
    val register = register()?.toAst()
    val identifier = identifier()
    val result = if(identifier!=null)
        AssignTarget(register, identifier.toAst(withPosition))
    else
        AssignTarget(register, scoped_identifier()?.toAst(withPosition))
    result.position = toPosition(withPosition)
    return result
}


private fun il65Parser.RegisterContext.toAst() = Register.valueOf(text.toUpperCase())


private fun il65Parser.DatatypeContext.toAst() = DataType.valueOf(text.toUpperCase())


private fun il65Parser.ArrayspecContext.toAst(withPosition: Boolean) : ArraySpec {
    val spec = ArraySpec(
            expression(0).toAst(withPosition),
            if (expression().size > 1) expression(1).toAst(withPosition) else null)
    spec.position = toPosition(withPosition)
    return spec
}


private fun il65Parser.DirectiveContext.toAst(withPosition: Boolean) : Directive {
    val dir = Directive(directivename.text, directivearg().map { it.toAst(withPosition) })
    dir.position = toPosition(withPosition)
    return dir
}


private fun il65Parser.DirectiveargContext.toAst(withPosition: Boolean) : DirectiveArg {
    val darg = DirectiveArg(stringliteral()?.text,
            identifier()?.text,
            integerliteral()?.toAst())
    darg.position = toPosition(withPosition)
    return darg
}


private fun il65Parser.IntegerliteralContext.toAst(): Int {
    val terminal: TerminalNode = children[0] as TerminalNode
    return when (terminal.symbol.type) {
        il65Parser.DEC_INTEGER -> text.toInt()
        il65Parser.HEX_INTEGER -> text.substring(1).toInt(16)
        il65Parser.BIN_INTEGER -> text.substring(1).toInt(2)
        else -> throw UnsupportedOperationException(text)
    }
}


private fun il65Parser.ExpressionContext.toAst(withPosition: Boolean) : IExpression {

    val litval = literalvalue()
    if(litval!=null) {
        val booleanlit = litval.booleanliteral()?.toAst()
        val value =
                if(booleanlit!=null)
                    LiteralValue(intvalue = if(booleanlit) 1 else 0)
                else
                    LiteralValue(litval.integerliteral()?.toAst(),
                            litval.floatliteral()?.toAst(),
                            litval.stringliteral()?.text,
                            litval.arrayliteral()?.toAst(withPosition))
        value.position = litval.toPosition(withPosition)
        return value
    }

    if(register()!=null) {
        val reg = RegisterExpr(register().toAst())
        reg.position = register().toPosition(withPosition)
        return reg
    }

    if(identifier()!=null)
        return identifier().toAst(withPosition)

    if(scoped_identifier()!=null)
        return scoped_identifier().toAst(withPosition)

    if(bop!=null) {
        val expr = BinaryExpression(left.toAst(withPosition),
                bop.text,
                right.toAst(withPosition))
        expr.position = toPosition(withPosition)
        return expr
    }

    if(prefix!=null) {
        val expr = PrefixExpression(prefix.text,
                expression(0).toAst(withPosition))
        expr.position = toPosition(withPosition)
        return expr
    }

    val funcall = functioncall()
    if(funcall!=null) {
        val location = funcall.identifier().toAst(withPosition)
        val fcall = if(funcall.expression_list()==null)
            FunctionCall(location, emptyList())
        else
            FunctionCall(location, funcall.expression_list().toAst(withPosition))
        fcall.position = funcall.toPosition(withPosition)
        return fcall
    }

    if (rangefrom!=null && rangeto!=null) {
        val rexp = RangeExpr(rangefrom.toAst(withPosition), rangeto.toAst(withPosition))
        rexp.position = toPosition(withPosition)
        return rexp
    }

    if(childCount==3 && children[0].text=="(" && children[2].text==")")
        return expression(0).toAst(withPosition)        // expression within ( )

    throw UnsupportedOperationException(text)
}


private fun il65Parser.Expression_listContext.toAst(withPosition: Boolean) = expression().map{ it.toAst(withPosition) }


private fun il65Parser.IdentifierContext.toAst(withPosition: Boolean) : Identifier {
    val ident = Identifier(text, emptyList())
    ident.position = toPosition(withPosition)
    return ident
}


private fun il65Parser.Scoped_identifierContext.toAst(withPosition: Boolean) : Identifier {
    val names = NAME()
    val name = names.last().text
    val scope = names.take(names.size-1)
    val ident = Identifier(name, scope.map { it.text })
    ident.position = toPosition(withPosition)
    return ident
}


private fun il65Parser.FloatliteralContext.toAst() = text.toDouble()


private fun il65Parser.BooleanliteralContext.toAst() = when(text) {
    "true" -> true
    "false" -> false
    else -> throw UnsupportedOperationException(text)
}


private fun il65Parser.ArrayliteralContext.toAst(withPosition: Boolean) =
        expression().map { it.toAst(withPosition) }

