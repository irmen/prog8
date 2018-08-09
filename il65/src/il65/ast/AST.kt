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

data class AssignTarget(val register: Register?, val singlename: String?, val dottedname: String?) : Node


interface IExpression: Node

data class UnaryExpression(val operator: String, val expression: IExpression) : IExpression

data class BinaryExpression(val left: IExpression, val operator: String, val right: IExpression) : IExpression

data class LiteralValue(val intvalue: Int?,
                        val floatvalue: Double?,
                        val strvalue: String?,
                        val boolvalue: Boolean?,
                        val arrayvalue: List<IExpression>?) : IExpression

data class RegisterExpr(val register: Register) : IExpression

data class DottedNameExpr(val dottedname: String) : IExpression

data class SingleNameExpr(val name: String) : IExpression



fun il65Parser.ModuleContext.toAst() = Module(this.statement().map { it.toAst() })

fun il65Parser.StatementContext.toAst() : IStatement {
    val directive = this.directive()?.toAst()
    if(directive!=null) return directive

    val vardecl = this.vardecl()
    if(vardecl!=null) {
        return VarDecl(vardecl.datatype().toAst(),
                vardecl.arrayspec()?.toAst(),
                vardecl.singlename().text,
                null)
    }

    val constdecl = this.constdecl()
    if(constdecl!=null) {
        val varinit = constdecl.varinitializer()
        return ConstDecl(varinit.datatype().toAst(),
                varinit.arrayspec()?.toAst(),
                varinit.singlename().text,
                varinit.expression().toAst())
    }

    val memdecl = this.memoryvardecl()
    if(memdecl!=null) {
        val varinit = memdecl.varinitializer()
        return MemoryVarDecl(varinit.datatype().toAst(),
                varinit.arrayspec()?.toAst(),
                varinit.singlename().text,
                varinit.expression().toAst())
    }

    val assign = this.assignment()
    if (assign!=null) {
        return Assignment(assign.assign_target().toAst(), null, assign.expression().toAst())
    }

    val augassign = this.augassignment()
    if (augassign!=null) {
        return Assignment(
                augassign.assign_target().toAst(),
                augassign.children[1].text,
                augassign.expression().toAst())
    }

    throw UnsupportedOperationException(this.text)
}

fun il65Parser.Assign_targetContext.toAst() =
        AssignTarget(this.register()?.toAst(), this.singlename()?.text, this.dottedname()?.text)

fun il65Parser.RegisterContext.toAst() = Register.valueOf(this.text)

fun il65Parser.DatatypeContext.toAst() = DataType.valueOf(this.text)

fun il65Parser.ArrayspecContext.toAst() = ArraySpec(
        this.expression(0).toAst(),
        if (this.expression().size > 1) this.expression(1).toAst() else null
)


fun il65Parser.DirectiveContext.toAst() = Directive(this.singlename().text, this.directivearg().map { it.toAst() })

fun il65Parser.DirectiveargContext.toAst() = DirectiveArg(this.singlename()?.text, this.integerliteral()?.toAst())

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

    if(this.singlename()!=null) {
        return SingleNameExpr(this.singlename().text)
    }

    val litval = this.literalvalue()
    if(litval!=null) {
        return LiteralValue(litval.integerliteral()?.toAst(),
                litval.floatliteral()?.toAst(),
                litval.stringliteral()?.text,
                litval.booleanliteral()?.toAst(),
                litval.arrayliteral()?.toAst()
                )
    }

    if(this.dottedname()!=null) {
        return DottedNameExpr(this.dottedname().text)
    }

    if(this.register()!=null) {
        return RegisterExpr(this.register().toAst())
    }

    if(this.unary_expression()!=null) {
        return UnaryExpression(this.unary_expression().children[0].text, this.unary_expression().expression().toAst())
    }

    if(this.expression().size == 2) {
        return BinaryExpression(this.expression(0).toAst(), this.text, this.expression(1).toAst())
    }

    // (....)
    if(this.childCount == 3 && this.children[0].text=="(" && this.children[2].text==")") {
        return this.expression(0).toAst()
    }

    throw UnsupportedOperationException(this.text)
}

fun il65Parser.FloatliteralContext.toAst() = this.text.toDouble()

fun il65Parser.BooleanliteralContext.toAst() = when(this.text) {
    "true" -> true
    "false" -> false
    else -> throw UnsupportedOperationException(this.text)
}

fun il65Parser.ArrayliteralContext.toAst() = this.expression().map { it.toAst() }

