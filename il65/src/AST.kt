package il65.ast

import net.razorvine.il65.parser.tinybasicParser

interface Node
interface Statement : Node

data class Program(val lines: List<Line>) : Node
data class Line(val number: Int?, val statement: Statement) : Node

data class Print(val exprlist: List<Expression>) : Statement
data class If(val expression1: Expression, val relop: Char, val expression2: Expression, val then: Statement) : Statement
data class Goto(val number: Int) : Statement
data class Input(val varlist: List<Var>) : Statement
data class Let(val vara: Var, val expression: Expression) : Statement
data class Gosub(val expression: Expression): Statement
class Return: Statement
class Clear : Statement
class ListStmt : Statement
class Run : Statement
class End : Statement

data class TermListTerm(val operator: Char, val term: Term) : Node
data class FactorListFactor(val operator: Char, val factor: Factor) : Node
data class Expression(val unaryOp: Char?, val term: Term, val terms: List<TermListTerm>) : Node
data class Term(val factor: Factor, val factors: List<FactorListFactor>) : Node
data class Factor(val thing: String) : Node
data class Var(val thing: String): Node


fun tinybasicParser.ProgramContext.toAst(): Program = Program(this.line().map {
    Line(it.number().toAst(), it.statement().toAst())
})

fun tinybasicParser.NumberContext.toAst(): Int = this.DIGIT().joinToString(separator = "").toInt()

fun tinybasicParser.StatementContext.toAst(): Statement =
    when (this.children[0].text) {
        "INPUT" -> Input(this.varlist().toAst())
        "IF" -> If(this.expression(0).toAst(), this.relop().toAst(), this.expression(1).toAst(), this.statement().toAst())
        "PRINT" -> Print(this.exprlist().toAst())
        "GOTO" -> Goto(this.number().toAst())
        "LET" -> Let(this.vara().toAst(), this.expression(0).toAst())
        "GOSUB" -> Gosub(this.expression(0).toAst())
        "RETURN" -> Return()
        "CLEAR" -> Clear()
        "LIST" -> ListStmt()
        "RUN" -> Run()
        "END" -> End()
        else -> Let(this.vara().toAst(), this.expression(0).toAst())
}

fun tinybasicParser.RelopContext.toAst() : Char = this.text[0]

fun tinybasicParser.VarlistContext.toAst() : List<Var> {
    return emptyList()
}

fun tinybasicParser.ExprlistContext.toAst() : List<Expression> {
    return emptyList()
}

fun tinybasicParser.VaraContext.toAst() : Var = Var(this.text)

fun tinybasicParser.ExpressionContext.toAst() : Expression {
    val unaryOp = '+'
    val term = Term(Factor("derp"), emptyList())
    return Expression(unaryOp, term, emptyList())
}

