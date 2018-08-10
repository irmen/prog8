package il65.ast

import il65.parser.il65Parser
import org.antlr.v4.runtime.tree.TerminalNode

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

interface Node

interface IStatement : Node

data class Module(val lines: List<IStatement>) : Node

data class Directive(val directive: String, val args: List<DirectiveArg>) : IStatement

data class DirectiveArg(val strval: String?, val intval: Int?) : Node

interface IVarDecl : IStatement {
    val datatype: DataType
    val arrayspec: ArraySpec?
    val name: String
    val value: IExpression?
}

data class ArraySpec(val x: IExpression, val y: IExpression?) : Node

data class VarDecl(override val datatype: DataType,
                   override val arrayspec: ArraySpec?,
                   override val name: String,
                   override val value: IExpression?) : IVarDecl

data class ConstDecl(override val datatype: DataType,
                     override val arrayspec: ArraySpec?,
                     override val name: String,
                     override val value: IExpression) : IVarDecl

data class MemoryVarDecl(override val datatype: DataType,
                         override val arrayspec: ArraySpec?,
                         override val name: String,
                         override val value: IExpression) : IVarDecl

data class Assignment(val target: AssignTarget, val aug_op : String?, val value: IExpression) : IStatement

data class AssignTarget(val register: Register?, val identifier: String?, val scoped_identifier: String?) : Node


interface IExpression: Node

data class UnaryExpression(val operator: String, val expression: IExpression) : IExpression

data class BinaryExpression(val left: IExpression, val operator: String, val right: IExpression) : IExpression

data class LiteralValue(val intvalue: Int?,
                        val floatvalue: Double?,
                        val strvalue: String?,
                        val boolvalue: Boolean?,
                        val arrayvalue: List<IExpression>?) : IExpression

data class RegisterExpr(val register: Register) : IExpression

data class Identifier(val name: String, val scope: String?) : IExpression



fun il65Parser.ModuleContext.toAst() = Module(this.statement().map { it.toAst() })

fun il65Parser.StatementContext.toAst() : IStatement {
    val vardecl = this.vardecl()
    if(vardecl!=null) {
        return VarDecl(vardecl.datatype().toAst(),
                vardecl.arrayspec()?.toAst(),
                vardecl.identifier().text,
                null)
    }

    val varinit = this.varinitializer()
    if(varinit!=null) {
        return VarDecl(varinit.datatype().toAst(),
                varinit.arrayspec()?.toAst(),
                varinit.identifier().text,
                varinit.expression().toAst())
    }

    val constdecl = this.constdecl()
    if(constdecl!=null) {
        val cvarinit = constdecl.varinitializer()
        return ConstDecl(cvarinit.datatype().toAst(),
                cvarinit.arrayspec()?.toAst(),
                cvarinit.identifier().text,
                cvarinit.expression().toAst())
    }

    val memdecl = this.memoryvardecl()
    if(memdecl!=null) {
        val mvarinit = memdecl.varinitializer()
        return MemoryVarDecl(mvarinit.datatype().toAst(),
                mvarinit.arrayspec()?.toAst(),
                mvarinit.identifier().text,
                mvarinit.expression().toAst())
    }

    val assign = this.assignment()
    if (assign!=null) {
        return Assignment(assign.assign_target().toAst(), null, assign.expression().toAst())
    }

    val augassign = this.augassignment()
    if (augassign!=null)
        return Assignment(
            augassign.assign_target().toAst(),
            augassign.operator.text,
            augassign.expression().toAst())

    val directive = this.directive()?.toAst()
    if(directive!=null) return directive

    throw UnsupportedOperationException(this.text)
}

fun il65Parser.Assign_targetContext.toAst() =
        AssignTarget(this.register()?.toAst(), this.identifier()?.text, this.scoped_identifier()?.text)

fun il65Parser.RegisterContext.toAst() = Register.valueOf(this.text.toUpperCase())

fun il65Parser.DatatypeContext.toAst() = DataType.valueOf(this.text.toUpperCase())

fun il65Parser.ArrayspecContext.toAst() = ArraySpec(
        this.expression(0).toAst(),
        if (this.expression().size > 1) this.expression(1).toAst() else null
)


fun il65Parser.DirectiveContext.toAst() = Directive(this.identifier().text, this.directivearg().map { it.toAst() })

fun il65Parser.DirectiveargContext.toAst() = DirectiveArg(this.identifier()?.text, this.integerliteral()?.toAst())

fun il65Parser.IntegerliteralContext.toAst(): Int {
    val terminal: TerminalNode = this.children[0] as TerminalNode
    return when (terminal.symbol.type) {
        il65Parser.DEC_INTEGER -> this.text.toInt()
        il65Parser.HEX_INTEGER -> this.text.toInt(16)
        il65Parser.BIN_INTEGER -> this.text.toInt(2)
        else -> throw UnsupportedOperationException(this.text)
    }
}

fun il65Parser.ExpressionContext.toAst() : IExpression {

    if(this.identifier()!=null)
        return Identifier(this.identifier().text, null)

    val litval = this.literalvalue()
    if(litval!=null)
        return LiteralValue(litval.integerliteral()?.toAst(),
            litval.floatliteral()?.toAst(),
            litval.stringliteral()?.text,
            litval.booleanliteral()?.toAst(),
            litval.arrayliteral()?.toAst()
            )

    if(this.scoped_identifier()!=null)
        return Identifier(this.scoped_identifier().text, "SCOPE????")   // todo!

    if(this.register()!=null)
        return RegisterExpr(this.register().toAst())

    if(this.unaryexp!=null)
        return UnaryExpression(this.unaryexp.operator.text, this.unaryexp.expression().toAst())

    if(this.left != null && this.right != null)
        return BinaryExpression(this.left.toAst(), this.text, this.right.toAst())

    //  ( expression )
    if(this.precedence_expr!=null)
        return this.precedence_expr.toAst()

    throw UnsupportedOperationException(this.text)
}

fun il65Parser.FloatliteralContext.toAst() = this.text.toDouble()

fun il65Parser.BooleanliteralContext.toAst() = when(this.text) {
    "true" -> true
    "false" -> false
    else -> throw UnsupportedOperationException(this.text)
}

fun il65Parser.ArrayliteralContext.toAst() = this.expression().map { it.toAst() }

