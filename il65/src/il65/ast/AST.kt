package il65.ast

import il65.parser.il65Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

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

data class Position(val line: Int, val startCol:Int, val endCol: Int)

interface Node
{
    val position: Position
}

interface IStatement : Node

data class Module(val lines: List<IStatement>,
                  override val position: Position) : Node

data class Block(val name: String, val address: Int?, val statements: List<IStatement>,
                 override val position: Position) : IStatement

data class Directive(val directive: String, val args: List<DirectiveArg>,
                     override val position: Position) : IStatement

data class DirectiveArg(val str: String?, val name: String?, val int: Int?,
                        override val position: Position) : Node

data class Label(val name: String,
                 override val position: Position) : IStatement

interface IVarDecl : IStatement {
    val datatype: DataType
    val arrayspec: ArraySpec?
    val name: String
    val value: IExpression?
}

data class ArraySpec(val x: IExpression, val y: IExpression?,
                     override val position: Position) : Node

data class VarDecl(override val datatype: DataType,
                   override val arrayspec: ArraySpec?,
                   override val name: String,
                   override val value: IExpression?,
                   override val position: Position) : IVarDecl

data class ConstDecl(override val datatype: DataType,
                     override val arrayspec: ArraySpec?,
                     override val name: String,
                     override val value: IExpression,
                     override val position: Position) : IVarDecl

data class MemoryVarDecl(override val datatype: DataType,
                         override val arrayspec: ArraySpec?,
                         override val name: String,
                         override val value: IExpression,
                         override val position: Position) : IVarDecl

data class Assignment(val target: AssignTarget, val aug_op : String?, val value: IExpression,
                      override val position: Position) : IStatement

data class AssignTarget(val register: Register?, val identifier: Identifier?,
                        override val position: Position) : Node


interface IExpression: Node

data class PrefixExpression(val operator: String, val expression: IExpression,
                            override val position: Position) : IExpression

data class BinaryExpression(val left: IExpression, val operator: String, val right: IExpression,
                            override val position: Position) : IExpression

data class LiteralValue(val intvalue: Int?,
                        val floatvalue: Double?,
                        val strvalue: String?,
                        val boolvalue: Boolean?,
                        val arrayvalue: List<IExpression>?,
                        override val position: Position) : IExpression

data class RangeExpr(val from: IExpression, val to: IExpression,
                     override val position: Position) : IExpression

data class RegisterExpr(val register: Register,
                        override val position: Position) : IExpression

data class Identifier(val name: String, val scope: List<String>,
                      override val position: Position) : IExpression

data class CallTarget(val address: Int?, val identifier: Identifier?,
                      override val position: Position) : Node

data class PostIncrDecr(val target: AssignTarget, val operator: String,
                        override val position: Position) : IStatement

data class Jump(val target: CallTarget,
                override val position: Position) : IStatement

data class FunctionCall(val target: CallTarget, val arglist: List<IExpression>,
                        override val position: Position) : IExpression

data class InlineAssembly(val assembly: String,
                          override val position: Position) : IStatement


/***************** Antlr Extension methods to create AST ****************/

fun ParserRuleContext.toPosition(withPosition: Boolean) : Position {
    return if (withPosition)
        Position(start.line, start.charPositionInLine, stop.charPositionInLine)
    else
        Position(start.line, start.charPositionInLine, stop.charPositionInLine)     // @todo null
}


fun il65Parser.ModuleContext.toAst(withPosition: Boolean) =
        Module(this.modulestatement().map { it.toAst(withPosition) },
                this.toPosition(withPosition))


fun il65Parser.ModulestatementContext.toAst(withPosition: Boolean) : IStatement {
    val directive = this.directive()?.toAst(withPosition)
    if(directive!=null) return directive

    val block = this.block()?.toAst(withPosition)
    if(block!=null) return block

    throw UnsupportedOperationException(this.text)
}


fun il65Parser.BlockContext.toAst(withPosition: Boolean) : IStatement {
    return Block(this.identifier().text, this.integerliteral()?.toAst(),
                 this.statement().map { it.toAst(withPosition) }, this.toPosition(withPosition))
}


fun il65Parser.StatementContext.toAst(withPosition: Boolean) : IStatement {
    val vardecl = this.vardecl()
    if(vardecl!=null) {
        return VarDecl(vardecl.datatype().toAst(),
                vardecl.arrayspec()?.toAst(withPosition),
                vardecl.identifier().text,
                null,
                vardecl.toPosition(withPosition))
    }

    val varinit = this.varinitializer()
    if(varinit!=null) {
        return VarDecl(varinit.datatype().toAst(),
                varinit.arrayspec()?.toAst(withPosition),
                varinit.identifier().text,
                varinit.expression().toAst(withPosition),
                varinit.toPosition(withPosition))
    }

    val constdecl = this.constdecl()
    if(constdecl!=null) {
        val cvarinit = constdecl.varinitializer()
        return ConstDecl(cvarinit.datatype().toAst(),
                cvarinit.arrayspec()?.toAst(withPosition),
                cvarinit.identifier().text,
                cvarinit.expression().toAst(withPosition),
                cvarinit.toPosition(withPosition))
    }

    val memdecl = this.memoryvardecl()
    if(memdecl!=null) {
        val mvarinit = memdecl.varinitializer()
        return MemoryVarDecl(mvarinit.datatype().toAst(),
                mvarinit.arrayspec()?.toAst(withPosition),
                mvarinit.identifier().text,
                mvarinit.expression().toAst(withPosition),
                mvarinit.toPosition(withPosition))
    }

    val assign = this.assignment()
    if (assign!=null) {
        return Assignment(assign.assign_target().toAst(withPosition),
                  null, assign.expression().toAst(withPosition),
                assign.toPosition(withPosition))
    }

    val augassign = this.augassignment()
    if (augassign!=null)
        return Assignment(augassign.assign_target().toAst(withPosition),
                augassign.operator.text,
                augassign.expression().toAst(withPosition),
                augassign.toPosition(withPosition))

    val post = this.postincrdecr()
    if(post!=null)
        return PostIncrDecr(post.assign_target().toAst(withPosition),
                post.operator.text, post.toPosition(withPosition))

    val directive = this.directive()?.toAst(withPosition)
    if(directive!=null) return directive

    val label=this.label()
    if(label!=null)
        return Label(label.text, label.toPosition(withPosition))

    val jump = this.unconditionaljump()
    if(jump!=null)
        return Jump(jump.call_location().toAst(withPosition), jump.toPosition(withPosition))

    val asm = this.inlineasm()
    if(asm!=null)
        return InlineAssembly(asm.INLINEASMBLOCK().text, asm.toPosition(withPosition))

    throw UnsupportedOperationException(this.text)
}


fun il65Parser.Call_locationContext.toAst(withPosition: Boolean) : CallTarget {
    val address = integerliteral()?.toAst()
    val identifier = identifier()
    return if(identifier!=null)
        CallTarget(address, identifier.toAst(withPosition), toPosition(withPosition))
    else
        CallTarget(address, scoped_identifier().toAst(withPosition), toPosition(withPosition))
}


fun il65Parser.Assign_targetContext.toAst(withPosition: Boolean) : AssignTarget {
    val register = this.register()?.toAst()
    val identifier = this.identifier()
    return if(identifier!=null)
        AssignTarget(register, identifier.toAst(withPosition), this.toPosition(withPosition))
    else
        AssignTarget(register, this.scoped_identifier()?.toAst(withPosition), this.toPosition(withPosition))
}


fun il65Parser.RegisterContext.toAst() = Register.valueOf(this.text.toUpperCase())


fun il65Parser.DatatypeContext.toAst() = DataType.valueOf(this.text.toUpperCase())


fun il65Parser.ArrayspecContext.toAst(withPosition: Boolean) = ArraySpec(
        this.expression(0).toAst(withPosition),
        if (this.expression().size > 1) this.expression(1).toAst(withPosition) else null,
        this.toPosition(withPosition)
)


fun il65Parser.DirectiveContext.toAst(withPosition: Boolean) =
        Directive(this.directivename.text,
                this.directivearg().map { it.toAst(withPosition) },
                this.toPosition(withPosition))


fun il65Parser.DirectiveargContext.toAst(withPosition: Boolean) =
        DirectiveArg(this.stringliteral()?.text,
                this.identifier()?.text,
                this.integerliteral()?.toAst(),
                this.toPosition(withPosition))


fun il65Parser.IntegerliteralContext.toAst(): Int {
    val terminal: TerminalNode = this.children[0] as TerminalNode
    return when (terminal.symbol.type) {
        il65Parser.DEC_INTEGER -> this.text.toInt()
        il65Parser.HEX_INTEGER -> this.text.substring(1).toInt(16)
        il65Parser.BIN_INTEGER -> this.text.substring(1).toInt(2)
        else -> throw UnsupportedOperationException(this.text)
    }
}


fun il65Parser.ExpressionContext.toAst(withPosition: Boolean) : IExpression {

    val litval = this.literalvalue()
    if(litval!=null)
        return LiteralValue(litval.integerliteral()?.toAst(),
                litval.floatliteral()?.toAst(),
                litval.stringliteral()?.text,
                litval.booleanliteral()?.toAst(),
                litval.arrayliteral()?.toAst(withPosition),
                litval.toPosition(withPosition)
        )

    if(this.register()!=null)
        return RegisterExpr(this.register().toAst(), this.register().toPosition(withPosition))

    if(this.identifier()!=null)
        return this.identifier().toAst(withPosition)

    if(this.scoped_identifier()!=null)
        return this.scoped_identifier().toAst(withPosition)

    if(this.bop!=null)
        return BinaryExpression(this.left.toAst(withPosition),
                this.bop.text,
                this.right.toAst(withPosition),
                this.toPosition(withPosition))

    if(this.prefix!=null)
        return PrefixExpression(this.prefix.text,
                this.expression(0).toAst(withPosition),
                this.toPosition(withPosition))

    val funcall = this.functioncall()
    if(funcall!=null) {
        val location = funcall.call_location().toAst(withPosition)
        return if(funcall.expression()!=null)
            FunctionCall(location, listOf(funcall.expression().toAst(withPosition)), funcall.toPosition(withPosition))
        else
            FunctionCall(location, emptyList(), funcall.toPosition(withPosition))
    }

    if (this.rangefrom!=null && this.rangeto!=null)
        return RangeExpr(this.rangefrom.toAst(withPosition),
                this.rangeto.toAst(withPosition),
                this.toPosition(withPosition))

    throw UnsupportedOperationException(this.text)
}


fun il65Parser.IdentifierContext.toAst(withPosition: Boolean) : Identifier {
    return Identifier(this.text, emptyList(), this.toPosition(withPosition))
}


fun il65Parser.Scoped_identifierContext.toAst(withPosition: Boolean) : Identifier {
    val names = this.NAME()
    val name = names.last().text
    val scope = names.take(names.size-1)
    return Identifier(name, scope.map { it.text }, toPosition(withPosition))
}


fun il65Parser.FloatliteralContext.toAst() = this.text.toDouble()


fun il65Parser.BooleanliteralContext.toAst() = when(this.text) {
    "true" -> true
    "false" -> false
    else -> throw UnsupportedOperationException(this.text)
}


fun il65Parser.ArrayliteralContext.toAst(withPosition: Boolean) =
        this.expression().map { it.toAst(withPosition) }

