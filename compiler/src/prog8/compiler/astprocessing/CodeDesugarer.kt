package prog8.compiler.astprocessing

import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.ParentSentinel
import prog8.ast.expressions.IdentifierReference
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

    private fun makeLabel(postfix: String): String {
        generatedLabelSequenceNumber++
        return "${generatedLabelPrefix}${generatedLabelSequenceNumber}_$postfix"
    }

    override fun before(breakStmt: Break, parent: Node): Iterable<IAstModification> {
        fun jumpAfter(stmt: Statement): Iterable<IAstModification> {
            val labelName = makeLabel("after")
            val ident = IdentifierReference(listOf(labelName), breakStmt.position)
            return listOf(
                IAstModification.ReplaceNode(breakStmt, Jump(null, ident, null, breakStmt.position), parent),
                IAstModification.InsertAfter(stmt, Label(labelName, breakStmt.position), stmt.parent as IStatementContainer)
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

}
