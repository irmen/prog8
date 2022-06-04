package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.ArrayIndexedExpression
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.DirectMemoryRead
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.DirectMemoryWrite
import prog8.ast.statements.VarDecl
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.DataType
import prog8.compiler.InplaceModifyingBuiltinFunctions


internal class AstOnetimeTransforms(private val program: Program) : AstWalker() {

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        if(parent !is VarDecl) {
            // TODO move this / remove this, and make the codegen better instead.
            // If the expression is pointervar[idx] where pointervar is uword and not a real array,
            // replace it by a @(pointervar+idx) expression.
            // Don't replace the initializer value in a vardecl - this will be moved to a separate
            // assignment statement soon in after(VarDecl)
            return replacePointerVarIndexWithMemreadOrMemwrite(arrayIndexedExpression, parent)
        }
        return noModifications
    }

    private fun replacePointerVarIndexWithMemreadOrMemwrite(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        val arrayVar = arrayIndexedExpression.arrayvar.targetVarDecl(program)
        if(arrayVar!=null && arrayVar.datatype == DataType.UWORD) {
            // rewrite   pointervar[index]  into  @(pointervar+index)
            val indexer = arrayIndexedExpression.indexer
            val add = BinaryExpression(arrayIndexedExpression.arrayvar.copy(), "+", indexer.indexExpr, arrayIndexedExpression.position)
            if(parent is AssignTarget) {
                // we're part of the target of an assignment, we have to actually change the assign target itself
                val memwrite = DirectMemoryWrite(add, arrayIndexedExpression.position)
                val newtarget = AssignTarget(null, null, memwrite, arrayIndexedExpression.position)
                return listOf(IAstModification.ReplaceNode(parent, newtarget, parent.parent))
            } else {
                val fcall = parent as? IFunctionCall
                return if(fcall!=null) {
                    val fname = fcall.target.nameInSource
                    if(fname.size==1 && fname[0] in InplaceModifyingBuiltinFunctions) {
                        // TODO for now, swap() etc don't work on pointer var indexed args
                        val memread = DirectMemoryRead(add, arrayIndexedExpression.position)
                        listOf(IAstModification.ReplaceNode(arrayIndexedExpression, memread, parent))
                    } else {
                        // TODO first candidate for optimization is to remove this:
                        val memread = DirectMemoryRead(add, arrayIndexedExpression.position)
                        listOf(IAstModification.ReplaceNode(arrayIndexedExpression, memread, parent))
                    }
                } else {
                    val memread = DirectMemoryRead(add, arrayIndexedExpression.position)
                    listOf(IAstModification.ReplaceNode(arrayIndexedExpression, memread, parent))
                }
            }
        }

        return noModifications
    }
}


