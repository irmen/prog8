package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.expressions.DirectMemoryRead
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.PrefixExpression
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.IErrorReporter
import prog8.code.core.Position


internal class CodeDesugarer(val program: Program,
                             private val errors: IErrorReporter) : AstWalker() {

    // Some more code shuffling to simplify the Ast that the codegenerator has to process.
    // Several changes have already been done by the StatementReorderer !
    // But the ones here are simpler and are repeated once again after all optimization steps
    // have been performed (because those could re-introduce nodes that have to be desugared)
    //
    // List of modifications:
    // - replace 'break' statements by a goto + generated after label.
    // - replace while and do-until loops by just jumps.
    // - replace peek() and poke() by direct memory accesses.
    // - repeat-forever loops replaced by label+jump.


    override fun before(breakStmt: Break, parent: Node): Iterable<IAstModification> {
        fun jumpAfter(stmt: Statement): Iterable<IAstModification> {
            val label = program.makeLabel("after", breakStmt.position)
            return listOf(
                IAstModification.ReplaceNode(breakStmt, program.jumpLabel(label), parent),
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
        val loopLabel = program.makeLabel("untilloop", pos)
        val notCondition = PrefixExpression("not", untilLoop.condition, pos)
        val replacement = AnonymousScope(mutableListOf(
            loopLabel,
            untilLoop.body,
            IfElse(notCondition,
                AnonymousScope(mutableListOf(program.jumpLabel(loopLabel)), pos),
                AnonymousScope(mutableListOf(), pos),
                pos)
        ), pos)
        return listOf(IAstModification.ReplaceNode(untilLoop, replacement, parent))
    }

    override fun after(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {
        /*
while CONDITION { STUFF }
    ==>
_whileloop:
  if NOT CONDITION goto _after
  STUFF
  goto _whileloop
_after:
         */
        val pos = whileLoop.position
        val loopLabel = program.makeLabel("whileloop", pos)
        val afterLabel = program.makeLabel("afterwhile", pos)
        val notCondition = PrefixExpression("not", whileLoop.condition, pos)
        val replacement = AnonymousScope(mutableListOf(
            loopLabel,
            IfElse(notCondition,
                AnonymousScope(mutableListOf(program.jumpLabel(afterLabel)), pos),
                AnonymousScope(mutableListOf(), pos),
                pos),
            whileLoop.body,
            program.jumpLabel(loopLabel),
            afterLabel
        ), pos)
        return listOf(IAstModification.ReplaceNode(whileLoop, replacement, parent))
    }

    override fun before(functionCallStatement: FunctionCallStatement, parent: Node) =
        before(functionCallStatement as IFunctionCall, parent, functionCallStatement.position)

    override fun before(functionCallExpr: FunctionCallExpression, parent: Node) =
        before(functionCallExpr as IFunctionCall, parent, functionCallExpr.position)

    private fun before(functionCall: IFunctionCall, parent: Node, position: Position): Iterable<IAstModification> {
        if(functionCall.target.nameInSource==listOf("peek")) {
            // peek(a) is synonymous with @(a)
            val memread = DirectMemoryRead(functionCall.args.single(), position)
            return listOf(IAstModification.ReplaceNode(functionCall as Node, memread, parent))
        }
        if(functionCall.target.nameInSource==listOf("poke")) {
            // poke(a, v) is synonymous with @(a) = v
            val tgt = AssignTarget(null, null, DirectMemoryWrite(functionCall.args[0], position), position)
            val assign = Assignment(tgt, functionCall.args[1], AssignmentOrigin.OPTIMIZER, position)
            return listOf(IAstModification.ReplaceNode(functionCall as Node, assign, parent))
        }
        return noModifications
    }

    override fun after(repeatLoop: RepeatLoop, parent: Node): Iterable<IAstModification> {
        if(repeatLoop.iterations==null) {
            val label = program.makeLabel("repeat", repeatLoop.position)
            val jump = program.jumpLabel(label)
            return listOf(
                IAstModification.InsertFirst(label, repeatLoop.body),
                IAstModification.InsertLast(jump, repeatLoop.body),
                IAstModification.ReplaceNode(repeatLoop, repeatLoop.body, parent)
            )
        }
        return noModifications
    }
}
