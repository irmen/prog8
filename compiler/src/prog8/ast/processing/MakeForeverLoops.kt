package prog8.ast.processing

import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.ForeverLoop
import prog8.ast.statements.RepeatLoop
import prog8.ast.statements.Statement
import prog8.ast.statements.WhileLoop

internal class MakeForeverLoops : IAstModifyingVisitor {
    override fun visit(whileLoop: WhileLoop): Statement {
        val numeric = whileLoop.condition as? NumericLiteralValue
        if(numeric!=null && numeric.number.toInt() != 0) {
            return ForeverLoop(whileLoop.body, whileLoop.position)
        }
        return super.visit(whileLoop)
    }

    override fun visit(repeatLoop: RepeatLoop): Statement {
        val numeric = repeatLoop.untilCondition as? NumericLiteralValue
        if(numeric!=null && numeric.number.toInt() == 0)
            return ForeverLoop(repeatLoop.body, repeatLoop.position)
        return super.visit(repeatLoop)
    }
}
