package prog8.optimizer

import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment


internal class BinExprSplitter(private val program: Program) : AstWalker() {
    private val noModifications = emptyList<IAstModification>()

//    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
// TODO somehow if we do this, the resulting code for some programs (cube3d.p8) gets hundreds of bytes larger...:
//        if(decl.type==VarDeclType.VAR ) {
//            val binExpr = decl.value as? BinaryExpression
//            if (binExpr != null && binExpr.operator in augmentAssignmentOperators) {
//                // split into a vardecl with just the left expression, and an aug. assignment with the right expression.
//                val augExpr = BinaryExpression(IdentifierReference(listOf(decl.name), decl.position), binExpr.operator, binExpr.right, binExpr.position)
//                val target = AssignTarget(IdentifierReference(listOf(decl.name), decl.position), null, null, decl.position)
//                val assign = Assignment(target, augExpr, binExpr.position)
//                println("SPLIT VARDECL $decl")
//                return listOf(
//                        IAstModification.SetExpression({ decl.value = it }, binExpr.left, decl),
//                        IAstModification.InsertAfter(decl, assign, parent)
//                )
//            }
//        }
//        return noModifications
//    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {

        val binExpr = assignment.value as? BinaryExpression
        if (binExpr != null) {
/*

Reduce the complexity of a (binary) expression that has to be evaluated on the eval stack,
by attempting to splitting it up into individual simple steps.
We only consider a binary expression *one* level deep (so the operands must not be a combined expression)


X =      BinExpr                                    X   =   LeftExpr
        <operator>                                     followed by
          /   \             IF 'X' not used         X   =   BinExpr
         /     \             IN expression ==>             <operator>
        /       \                                           /   \
    LeftExpr.  RightExpr.                                  /     \
                                                          X     RightExpr.


 */
            if(binExpr.operator in augmentAssignmentOperators && isSimpleTarget(assignment.target, program.namespace)) {
                if(assignment.target isSameAs binExpr.left || assignment.target isSameAs binExpr.right)
                    return noModifications

                if(isSimpleExpression(binExpr.right) && !assignment.isAugmentable) {
                    val firstAssign = Assignment(assignment.target, binExpr.left, binExpr.left.position)
                    val targetExpr = assignment.target.toExpression()
                    val augExpr = BinaryExpression(targetExpr, binExpr.operator, binExpr.right, binExpr.right.position)
                    return listOf(
                            IAstModification.InsertBefore(assignment, firstAssign, assignment.definingScope()),
                            IAstModification.ReplaceNode(assignment.value, augExpr, assignment))
                }
            }

            // TODO further unraveling of binary expression trees into flat statements.
            // however this should probably be done in a more generic way to also service
            // the expressiontrees that are not used in an assignment statement...
        }

        return noModifications
    }

    private fun isSimpleExpression(expr: Expression) =
            expr is IdentifierReference || expr is NumericLiteralValue || expr is AddressOf || expr is DirectMemoryRead || expr is StringLiteralValue || expr is ArrayLiteralValue || expr is RangeExpr

    private fun isSimpleTarget(target: AssignTarget, namespace: INameScope) =
            if (target.identifier!=null || target.memoryAddress!=null || target.arrayindexed!=null)
                target.isInRegularRAM(namespace)
            else
                false

}
