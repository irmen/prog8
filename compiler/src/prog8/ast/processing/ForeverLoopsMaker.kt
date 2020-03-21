package prog8.ast.processing

import prog8.ast.Node
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.ForeverLoop
import prog8.ast.statements.RepeatLoop
import prog8.ast.statements.WhileLoop


internal class ForeverLoopsMaker: AstWalker() {
    override fun before(repeatLoop: RepeatLoop, parent: Node): Iterable<IAstModification> {
        val numeric = repeatLoop.untilCondition as? NumericLiteralValue
        if(numeric!=null && numeric.number.toInt() == 0) {
            val forever = ForeverLoop(repeatLoop.body, repeatLoop.position)
            return listOf(IAstModification.ReplaceNode(repeatLoop, forever, parent))
        }
        return emptyList()
    }

    override fun before(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {
        val numeric = whileLoop.condition as? NumericLiteralValue
        if(numeric!=null && numeric.number.toInt() != 0) {
            val forever = ForeverLoop(whileLoop.body, whileLoop.position)
            return listOf(IAstModification.ReplaceNode(whileLoop, forever, parent))
        }
        return emptyList()
    }
}
