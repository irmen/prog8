package prog8.optimizer

import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.*
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.ast.statements.VarDecl

class ExpressionSplitter(private val program: Program) : AstWalker() {

    // TODO once this works, integrate it back into expressionsimplifier
    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {

        if(decl.type==VarDeclType.VAR) {
            val binExpr = decl.value as? BinaryExpression
            if (binExpr != null) {
                // split into a vardecl with just the left expression, and an aug. assignment with the right expression.
                val augExpr = BinaryExpression(IdentifierReference(listOf(decl.name), decl.position), binExpr.operator, binExpr.right, binExpr.position)
                val target = AssignTarget(IdentifierReference(listOf(decl.name), decl.position), null, null, decl.position)
                val assign = Assignment(target, augExpr, binExpr.position)
                return listOf(
                        IAstModification.SetExpression({ decl.value = it }, binExpr.left, decl),
                        IAstModification.InsertAfter(decl, assign, parent)
                )
            }
        }

        return emptyList()
    }

    // TODO once this works, integrate it back into expressionsimplifier
    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {

        val binExpr = assignment.value as? BinaryExpression
        if (binExpr != null) {
/*

reduce the complexity of a (binary) expression that has to be evaluated on the eval stack,
by attempting to splitting it up into individual simple steps:


X =      BinExpr                                   X   =   LeftExpr
        <operator>                                   followed by
          /   \              IF 'X' not used       X   =   BinExpr
         /     \               IN LEFTEXPR ==>             <operator>
        /       \                                           /   \
    LeftExpr.  RightExpr.                                  /     \
      /  \       /  \                                     X     RightExpr.
    ..  ..      ..  ..



X =      BinExpr                                   X   =   RightExpr
        <operator>                                   followed by
          /   \              IF ASSOCIATIVE        X   =   BinExpr
         /     \               <operator>    ==>          <operator>
        /       \                                           /   \
    LeftExpr.  SimpleExpr.                                 /     \
      /  \       (not X)                                  X     LeftExpr.
     ..  ..

 */
            if(!assignment.isAugmentable && isSimpleTarget(assignment.target, program.namespace)) {
                val firstAssign = Assignment(assignment.target, binExpr.left, binExpr.left.position)
                val targetExpr = assignment.target.toExpression()
                val augExpr = BinaryExpression(targetExpr, binExpr.operator, binExpr.right, binExpr.right.position)
                return listOf(
                        IAstModification.InsertBefore(assignment, firstAssign, parent),
                        IAstModification.ReplaceNode(assignment.value, augExpr, assignment))
            }

            if(binExpr.operator in associativeOperators && binExpr.left is BinaryExpression) {
                if (binExpr.right !is BinaryExpression && !(binExpr.right isSameAs assignment.target)) {
                    val firstAssign = Assignment(assignment.target, binExpr.right, binExpr.right.position)
                    val targetExpr = assignment.target.toExpression()
                    val augExpr = BinaryExpression(targetExpr, binExpr.operator, binExpr.left, binExpr.left.position)
                    return listOf(
                            IAstModification.InsertBefore(assignment, firstAssign, parent),
                            IAstModification.ReplaceNode(assignment.value, augExpr, assignment))
                }
            }

            // TODO further unraveling of binary expression trees into flat statements.
            // however this should probably be done in a more generic way to also service
            // the expressiontrees that are not used in an assignment statement...
        }

        return emptyList()
    }

    private fun isSimpleTarget(target: AssignTarget, namespace: INameScope): Boolean {
        return when {
            target.identifier!=null -> target.isInRegularRAM(namespace)
            target.memoryAddress!=null -> target.isInRegularRAM(namespace)
            target.arrayindexed!=null -> {
                val index = target.arrayindexed!!.arrayspec.index
                if(index is NumericLiteralValue)
                    target.isInRegularRAM(namespace)
                else
                    false
            }
            else -> false
        }
    }
}
