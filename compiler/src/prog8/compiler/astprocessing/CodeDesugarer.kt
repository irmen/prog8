package prog8.compiler.astprocessing

import com.github.michaelbull.result.toResultOr
import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.ParentSentinel
import prog8.ast.base.Position
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.PrefixExpression
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compilerinterface.*


internal class CodeDesugarer(val program: Program, private val errors: IErrorReporter) : AstWalker() {

    // Some more code shuffling to simplify the Ast that the codegenerator has to process.
    // Several changes have already been done by the StatementReorderer !
    // But the ones here are simpler and are repeated once again after all optimization steps
    // have been performed (because those could re-introduce nodes that have to be desugared)
    //
    // List of modifications:
    // - replace 'break' statements by a goto + generated after label.


    private var generatedLabelSequenceNumber: Int = 0
    private val generatedLabelPrefix = "prog8_label_"

    private fun makeLabel(postfix: String, position: Position): Label {
        generatedLabelSequenceNumber++
        return Label("${generatedLabelPrefix}${generatedLabelSequenceNumber}_$postfix", position)
    }

    private fun jumpLabel(label: Label): Jump {
        val ident = IdentifierReference(listOf(label.name), label.position)
        return Jump(null, ident, null, label.position)
    }

    override fun before(breakStmt: Break, parent: Node): Iterable<IAstModification> {
        fun jumpAfter(stmt: Statement): Iterable<IAstModification> {
            val label = makeLabel("after", breakStmt.position)
            return listOf(
                IAstModification.ReplaceNode(breakStmt, jumpLabel(label), parent),
                IAstModification.InsertAfter(stmt, label, stmt.parent as IStatementContainer)
            )
        }

        var partof = parent
        while(true) {
            when (partof) {
                is Subroutine, is Block, is ParentSentinel -> {
                    errors.err("break in wrong scope", breakStmt.position)
                    return noModifications
                }
                is ForLoop,
                is RepeatLoop,
                is UntilLoop,
                is WhileLoop -> return jumpAfter(partof as Statement)
                else -> partof = partof.parent
            }
        }
    }

    override fun after(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> {
        /*
do { STUFF } until CONDITION
    ===>
_loop:
  STUFF
if not CONDITION
   goto _loop
         */
        val pos = untilLoop.position
        val loopLabel = makeLabel("untilloop", pos)
        val notCondition = PrefixExpression("not", untilLoop.condition, pos)
        val replacement = AnonymousScope(mutableListOf(
            loopLabel,
            untilLoop.body,
            IfStatement(notCondition,
                AnonymousScope(mutableListOf(jumpLabel(loopLabel)), pos),
                AnonymousScope(mutableListOf(), pos),
                pos)
        ), pos)
        return listOf(IAstModification.ReplaceNode(untilLoop, replacement, parent))
    }

    override fun after(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {
        /*
while CONDITION { STUFF }
    ==>
  goto _whilecond
_whileloop:
  STUFF
_whilecond:
  if CONDITION goto _whileloop
         */
        val pos = whileLoop.position
        val condLabel = makeLabel("whilecond", pos)
        val loopLabel = makeLabel("whileloop", pos)
        val replacement = AnonymousScope(mutableListOf(
            jumpLabel(condLabel),
            loopLabel,
            whileLoop.body,
            condLabel,
            IfStatement(whileLoop.condition,
                AnonymousScope(mutableListOf(jumpLabel(loopLabel)), pos),
                AnonymousScope(mutableListOf(), pos),
                pos)
        ), pos)
        return listOf(IAstModification.ReplaceNode(whileLoop, replacement, parent))
    }
}
