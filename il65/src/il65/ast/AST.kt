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

data class Block(val name: String, val address: Int?, val statements: List<IStatement>) : IStatement

data class Directive(val directive: String, val args: List<DirectiveArg>) : IStatement

data class DirectiveArg(val str: String?, val name: String?, val int: Int?) : Node

data class Label(val name: String) : IStatement

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

data class AssignTarget(val register: Register?, val identifier: Identifier?) : Node


interface IExpression: Node

data class PrefixExpression(val operator: String, val expression: IExpression) : IExpression

data class BinaryExpression(val left: IExpression, val operator: String, val right: IExpression) : IExpression

data class LiteralValue(val intvalue: Int?,
                        val floatvalue: Double?,
                        val strvalue: String?,
                        val boolvalue: Boolean?,
                        val arrayvalue: List<IExpression>?) : IExpression

data class RangeExpr(val from: IExpression, val to: IExpression) : IExpression

data class RegisterExpr(val register: Register) : IExpression

data class Identifier(val name: String, val scope: List<String>) : IExpression

data class CallTarget(val address: Int?, val identifier: Identifier?) : Node

data class PostIncrDecr(val target: AssignTarget, val operator: String) : IStatement

data class Jump(val target: CallTarget) : IStatement

data class FunctionCall(val target: CallTarget, val arglist: List<IExpression>) : IExpression

data class InlineAssembly(val assembly: String) : IStatement


fun il65Parser.ModuleContext.toAst() = Module(this.modulestatement().map { it.toAst() })

fun il65Parser.ModulestatementContext.toAst() : IStatement {
    val directive = this.directive()?.toAst()
    if(directive!=null) return directive

    val block = this.block()?.toAst()
    if(block!=null) return block

    throw UnsupportedOperationException(this.text)
}

fun il65Parser.BlockContext.toAst() : IStatement {
    return Block(this.identifier().text, this.integerliteral()?.toAst(), this.statement().map { it.toAst() })
}

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

    val post = this.postincrdecr()
    if(post!=null)
        return PostIncrDecr(post.assign_target().toAst(), post.operator.text)

    val directive = this.directive()?.toAst()
    if(directive!=null) return directive

    val label=this.label()
    if(label!=null)
        return Label(label.text)

    val jump = this.unconditionaljump()
    if(jump!=null)
        return Jump(jump.call_location().toAst())

    val asm = this.inlineasm()
    if(asm!=null)
        return InlineAssembly(asm.INLINEASMBLOCK().text)

    throw UnsupportedOperationException(this.text)
}

fun il65Parser.Call_locationContext.toAst() : CallTarget {
    val address = this.integerliteral()?.toAst()
    if(this.identifier()!=null) return CallTarget(address, Identifier(this.identifier().text, emptyList()))
    return CallTarget(address, this.scoped_identifier().toAst())
}

fun il65Parser.Assign_targetContext.toAst() : AssignTarget {
    val register = this.register()?.toAst()
    val identifier = this.identifier()?.text
    if(identifier!=null) return AssignTarget(register, Identifier(identifier, emptyList()))
    return AssignTarget(register, this.scoped_identifier()?.toAst())
}

fun il65Parser.RegisterContext.toAst() = Register.valueOf(this.text.toUpperCase())

fun il65Parser.DatatypeContext.toAst() = DataType.valueOf(this.text.toUpperCase())

fun il65Parser.ArrayspecContext.toAst() = ArraySpec(
        this.expression(0).toAst(),
        if (this.expression().size > 1) this.expression(1).toAst() else null
)


fun il65Parser.DirectiveContext.toAst() = Directive(this.directivename.text, this.directivearg().map { it.toAst() })

fun il65Parser.DirectiveargContext.toAst() =
        DirectiveArg(this.stringliteral()?.text, this.identifier()?.text, this.integerliteral()?.toAst())

fun il65Parser.IntegerliteralContext.toAst(): Int {
    val terminal: TerminalNode = this.children[0] as TerminalNode
    return when (terminal.symbol.type) {
        il65Parser.DEC_INTEGER -> this.text.toInt()
        il65Parser.HEX_INTEGER -> this.text.substring(1).toInt(16)
        il65Parser.BIN_INTEGER -> this.text.substring(1).toInt(2)
        else -> throw UnsupportedOperationException(this.text)
    }
}

fun il65Parser.ExpressionContext.toAst() : IExpression {

    val litval = this.literalvalue()
    if(litval!=null)
        return LiteralValue(litval.integerliteral()?.toAst(),
                litval.floatliteral()?.toAst(),
                litval.stringliteral()?.text,
                litval.booleanliteral()?.toAst(),
                litval.arrayliteral()?.toAst()
        )

    if(this.register()!=null)
        return RegisterExpr(this.register().toAst())

    if(this.identifier()!=null)
        return Identifier(this.identifier().text, emptyList())

    if(this.scoped_identifier()!=null)
        return this.scoped_identifier().toAst()

    if(this.bop!=null)
        return BinaryExpression(this.left.toAst(), this.bop.text, this.right.toAst())

    if(this.prefix!=null)
        return PrefixExpression(this.prefix.text, this.expression(0).toAst())

    val funcall = this.functioncall()
    if(funcall!=null) {
        val location = funcall.call_location().toAst()
        if(funcall.expression()!=null) return FunctionCall(location, listOf(funcall.expression().toAst()))
        return FunctionCall(location, emptyList())
    }

    if (this.rangefrom!=null && this.rangeto!=null)
        return RangeExpr(this.rangefrom.toAst(), this.rangeto.toAst())

    throw UnsupportedOperationException(this.text)
}

fun il65Parser.Scoped_identifierContext.toAst() : Identifier {
    val names = this.NAME()
    val name = names.last().text
    val scope = names.take(names.size-1)
    return Identifier(name, scope.map { it.text })
}

fun il65Parser.FloatliteralContext.toAst() = this.text.toDouble()

fun il65Parser.BooleanliteralContext.toAst() = when(this.text) {
    "true" -> true
    "false" -> false
    else -> throw UnsupportedOperationException(this.text)
}

fun il65Parser.ArrayliteralContext.toAst() = this.expression().map { it.toAst() }

