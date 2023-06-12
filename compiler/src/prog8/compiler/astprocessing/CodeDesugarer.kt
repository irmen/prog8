package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.DataType
import prog8.code.core.IErrorReporter
import prog8.code.core.Position


internal class CodeDesugarer(val program: Program, private val errors: IErrorReporter) : AstWalker() {

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
    // - pointer[word] replaced by @(pointer+word)


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
if CONDITION==0
   goto _loop
         */
        val pos = untilLoop.position
        val loopLabel = program.makeLabel("untilloop", pos)
        val ifCondition = invertCondition(untilLoop.condition)
            ?: BinaryExpression(untilLoop.condition, "==", NumericLiteral(DataType.UBYTE, 0.0, pos), pos)
        val replacement = AnonymousScope(mutableListOf(
            loopLabel,
            untilLoop.body,
            IfElse(ifCondition,
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
  if INVERTED-CONDITION goto _after
  STUFF
  goto _whileloop
_after:
         */
        val pos = whileLoop.position
        val loopLabel = program.makeLabel("whileloop", pos)
        val afterLabel = program.makeLabel("afterwhile", pos)
        val ifCondition = invertCondition(whileLoop.condition)
            ?: BinaryExpression(whileLoop.condition, "==", NumericLiteral(DataType.UBYTE, 0.0, pos), pos)
        val replacement = AnonymousScope(mutableListOf(
            loopLabel,
            IfElse(ifCondition,
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
            // replace with a jump at the end, but make sure the jump is inserted *before* any subroutines that may occur inside this block
            val subroutineMovements = mutableListOf<IAstModification>()
            val subroutines = repeatLoop.body.statements.filterIsInstance<Subroutine>()
            subroutines.forEach { sub ->
                subroutineMovements += IAstModification.Remove(sub, sub.parent as IStatementContainer)
                subroutineMovements += IAstModification.InsertLast(sub, sub.parent as IStatementContainer)
            }

            val label = program.makeLabel("repeat", repeatLoop.position)
            val jump = program.jumpLabel(label)
            return listOf(
                IAstModification.InsertFirst(label, repeatLoop.body),
                IAstModification.InsertLast(jump, repeatLoop.body),
                IAstModification.ReplaceNode(repeatLoop, repeatLoop.body, parent)
            ) + subroutineMovements
        }
        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        // replace pointervar[word] by @(pointervar+word) to avoid the
        // "array indexing is limited to byte size 0..255" error for pointervariables.
        val indexExpr = arrayIndexedExpression.indexer.indexExpr
        val indexerDt = indexExpr.inferType(program)
        if(indexerDt.isWords) {
            val arrayVar = arrayIndexedExpression.arrayvar.targetVarDecl(program)
            if(arrayVar!=null && arrayVar.datatype==DataType.UWORD) {
                val add: Expression =
                    if(indexExpr.constValue(program)?.number==0.0)
                        arrayIndexedExpression.arrayvar.copy()
                    else
                        BinaryExpression(arrayIndexedExpression.arrayvar.copy(), "+", indexExpr, arrayIndexedExpression.position)
                return if(parent is AssignTarget) {
                    // assignment to array
                    val memwrite = DirectMemoryWrite(add, arrayIndexedExpression.position)
                    val newtarget = AssignTarget(null, null, memwrite, arrayIndexedExpression.position)
                    listOf(IAstModification.ReplaceNode(parent, newtarget, parent.parent))
                } else {
                    // read from array
                    val memread = DirectMemoryRead(add, arrayIndexedExpression.position)
                    listOf(IAstModification.ReplaceNode(arrayIndexedExpression, memread, parent))
                }
            }
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator=="in") {
            val containment = ContainmentCheck(expr.left, expr.right, expr.position)
            return listOf(IAstModification.ReplaceNode(expr, containment, parent))
        }
        return noModifications
    }
}
