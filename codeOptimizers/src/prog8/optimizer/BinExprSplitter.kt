package prog8.optimizer

import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.expressions.AugmentAssignmentOperators
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.TypecastExpression
import prog8.ast.getTempVar
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.ast.statements.AssignmentOrigin
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compilerinterface.CompilationOptions
import prog8.compilerinterface.ICompilationTarget
import prog8.compilerinterface.isIOAddress


class BinExprSplitter(private val program: Program, private val options: CompilationOptions, private val compTarget: ICompilationTarget) : AstWalker() {

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {

        if(assignment.value.inferType(program) istype DataType.FLOAT && !options.optimizeFloatExpressions)
            return noModifications

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
            if(binExpr.operator in AugmentAssignmentOperators && isSimpleTarget(assignment.target)) {
                if(assignment.target isSameAs binExpr.right)
                    return noModifications
                if(assignment.target isSameAs binExpr.left) {
                    if(binExpr.right.isSimple)
                        return noModifications
                    val leftBx = binExpr.left as? BinaryExpression
                    if(leftBx!=null && (!leftBx.left.isSimple || !leftBx.right.isSimple))
                        return noModifications
                    val rightBx = binExpr.right as? BinaryExpression
                    if(rightBx!=null && (!rightBx.left.isSimple || !rightBx.right.isSimple))
                        return noModifications

                      // TODO below attempts to remove stack-based evaluated expressions, but often the resulting code is BIGGER, and SLOWER.
//                    val dt = assignment.target.inferType(program)
//                    if(!dt.isInteger)
//                        return noModifications
//                    val tempVar = IdentifierReference(getTempVarName(dt), binExpr.right.position)
//                    val assignTempVar = Assignment(
//                        AssignTarget(tempVar, null, null, binExpr.right.position),
//                        binExpr.right, binExpr.right.position
//                    )
//                    return listOf(
//                        IAstModification.InsertBefore(assignment, assignTempVar, assignment.parent as IStatementContainer),
//                        IAstModification.ReplaceNode(binExpr.right, tempVar.copy(), binExpr)
//                    )
                }

                if(binExpr.right.isSimple) {
                    val firstAssign = Assignment(assignment.target.copy(), binExpr.left, AssignmentOrigin.OPTIMIZER, binExpr.left.position)
                    val targetExpr = assignment.target.toExpression()
                    val augExpr = BinaryExpression(targetExpr, binExpr.operator, binExpr.right, binExpr.right.position)
                    return listOf(
                        IAstModification.ReplaceNode(binExpr, augExpr, assignment),
                        IAstModification.InsertBefore(assignment, firstAssign, assignment.parent as IStatementContainer)
                    )
                }
            }

            // TODO further unraveling of binary expression trees into flat statements.
            // however this should probably be done in a more generic way to also work on
            // the expressiontrees that are not used in an assignment statement...
        }

        val typecast = assignment.value as? TypecastExpression
        if(typecast!=null) {
            val origExpr = typecast.expression as? BinaryExpression
            if(origExpr!=null) {
                // it's a typecast of a binary expression.
                // we can see if we can unwrap the binary expression by working on a new temporary variable
                // (that has the type of the expression), and then finally doing the typecast.
                // Once it's outside the typecast, the regular splitting can commence.
                val tempVar = program.getTempVar(origExpr.inferType(program).getOr(DataType.UNDEFINED))
                val assignTempVar = Assignment(
                    AssignTarget(IdentifierReference(tempVar, typecast.position), null, null, typecast.position),
                    typecast.expression, AssignmentOrigin.OPTIMIZER, typecast.position
                )
                return listOf(
                    IAstModification.InsertBefore(assignment, assignTempVar, parent as IStatementContainer),
                    IAstModification.ReplaceNode(typecast.expression, IdentifierReference(tempVar, typecast.position), typecast)
                )
            }
        }

        return noModifications
    }

    private fun isSimpleTarget(target: AssignTarget) =
            if (target.identifier!=null || target.memoryAddress!=null)
                !target.isIOAddress(compTarget.machine)
            else
                false

}
