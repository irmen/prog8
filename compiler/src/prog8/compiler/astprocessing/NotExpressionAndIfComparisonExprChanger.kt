package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.ast.statements.AssignmentOrigin
import prog8.ast.statements.IfElse
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*
import prog8.code.target.VMTarget

internal class NotExpressionAndIfComparisonExprChanger(val program: Program, val errors: IErrorReporter, val compTarget: ICompilationTarget) : AstWalker() {

    override fun before(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator=="==" || expr.operator=="!=") {
            val left = expr.left as? BinaryExpression
            if (left != null) {
                val rightValue = expr.right.constValue(program)
                if (rightValue?.number == 0.0 && rightValue.type in IntegerDatatypes) {
                    if (left.operator == "==" && expr.operator == "==") {
                        // (x==something)==0  -->  x!=something
                        left.operator = "!="
                        return listOf(IAstModification.ReplaceNode(expr, left, parent))
                    } else if (left.operator == "!=" && expr.operator == "==") {
                        // (x!=something)==0  -->  x==something
                        left.operator = "=="
                        return listOf(IAstModification.ReplaceNode(expr, left, parent))
                    } else if (left.operator == "==" && expr.operator == "!=") {
                        // (x==something)!=0 --> x==something
                        left.operator = "=="
                        return listOf(IAstModification.ReplaceNode(expr, left, parent))
                    } else if (left.operator == "!=" && expr.operator == "!=") {
                        // (x!=something)!=0 --> x!=something
                        left.operator = "!="
                        return listOf(IAstModification.ReplaceNode(expr, left, parent))
                    }
                }
            }
        }
        return noModifications
    }

    override fun after(expr: PrefixExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator == "not") {
            // not(not(x)) -> x
            if((expr.expression as? PrefixExpression)?.operator=="not")
                return listOf(IAstModification.ReplaceNode(expr, expr.expression, parent))
            // not(~x) -> x!=0
            if((expr.expression as? PrefixExpression)?.operator=="~") {
                val x = (expr.expression as PrefixExpression).expression
                val dt = x.inferType(program).getOrElse { throw FatalAstException("invalid dt") }
                val notZero = BinaryExpression(x, "!=", NumericLiteral(dt, 0.0, expr.position), expr.position)
                return listOf(IAstModification.ReplaceNode(expr, notZero, parent))
            }
            val subBinExpr = expr.expression as? BinaryExpression
            if(subBinExpr?.operator=="==") {
                if(subBinExpr.right.constValue(program)?.number==0.0) {
                    // not(x==0) -> x!=0
                    subBinExpr.operator = "!="
                    return listOf(IAstModification.ReplaceNode(expr, subBinExpr, parent))
                }
            } else if(subBinExpr?.operator=="!=") {
                if(subBinExpr.right.constValue(program)?.number==0.0) {
                    // not(x!=0) -> x==0
                    subBinExpr.operator = "=="
                    return listOf(IAstModification.ReplaceNode(expr, subBinExpr, parent))
                }
            }

            // all other not(x)  -->  x==0
            // this means that "not" will never occur anywhere again in the ast after this
            val replacement = BinaryExpression(expr.expression, "==", NumericLiteral(DataType.UBYTE,0.0, expr.position), expr.position)
            return listOf(IAstModification.ReplaceNodeSafe(expr, replacement, parent))
        }
        return noModifications
    }


    override fun before(ifElse: IfElse, parent: Node): Iterable<IAstModification> {
        if(compTarget.name == VMTarget.NAME)  // don't apply this optimization for Vm target
            return noModifications

        val binExpr = ifElse.condition as? BinaryExpression
        if(binExpr==null || binExpr.operator !in ComparisonOperators)
            return noModifications

        // Simplify the conditional expression, introduce simple assignments if required.
        // This is REQUIRED for correct code generation on 6502 because evaluating certain expressions
        // clobber the handful of temporary variables in the zeropage and leaving everything in one
        // expression then results in invalid values being compared. VM Codegen doesn't suffer from this.
        // NOTE: sometimes this increases code size because additional stores/loads are generated for the
        //       intermediate variables. We assume these are optimized away from the resulting assembly code later.
        val simplify = simplifyConditionalExpression(binExpr)
        val modifications = mutableListOf<IAstModification>()
        if(simplify.rightVarAssignment!=null) {
            modifications += IAstModification.ReplaceNode(binExpr.right, simplify.rightOperandReplacement!!, binExpr)
            modifications += IAstModification.InsertBefore(
                ifElse,
                simplify.rightVarAssignment,
                parent as IStatementContainer
            )
        }
        if(simplify.leftVarAssignment!=null) {
            modifications += IAstModification.ReplaceNode(binExpr.left, simplify.leftOperandReplacement!!, binExpr)
            modifications += IAstModification.InsertBefore(
                ifElse,
                simplify.leftVarAssignment,
                parent as IStatementContainer
            )
        }

        return modifications
    }

    private class CondExprSimplificationResult(
        val leftVarAssignment: Assignment?,
        val leftOperandReplacement: Expression?,
        val rightVarAssignment: Assignment?,
        val rightOperandReplacement: Expression?
    )

    private fun simplifyConditionalExpression(expr: BinaryExpression): CondExprSimplificationResult {

        // TODO: somehow figure out if the expr will result in stack-evaluation STILL after being split off,
        //       in that case: do *not* split it off but just keep it as it is (otherwise code size increases)
        // NOTE: do NOT move this to an earler ast transform phase (such as StatementReorderer or StatementOptimizer) - it WILL result in larger code.

        if(compTarget.name == VMTarget.NAME)  // don't apply this optimization for Vm target
            return CondExprSimplificationResult(null, null, null, null)

        var leftAssignment: Assignment? = null
        var leftOperandReplacement: Expression? = null
        var rightAssignment: Assignment? = null
        var rightOperandReplacement: Expression? = null

        val separateLeftExpr = !expr.left.isSimple
                && expr.left !is IFunctionCall
                && expr.left !is ContainmentCheck
        val separateRightExpr = !expr.right.isSimple
                && expr.right !is IFunctionCall
                && expr.right !is ContainmentCheck
        val leftDt = expr.left.inferType(program)
        val rightDt = expr.right.inferType(program)

        if(!leftDt.isInteger || !rightDt.isInteger) {
            // we can't reasonably simplify non-integer expressions
            return CondExprSimplificationResult(null, null, null, null)
        }

        if(separateLeftExpr) {
            val name = getTempRegisterName(leftDt)
            leftOperandReplacement = IdentifierReference(name, expr.position)
            leftAssignment = Assignment(
                AssignTarget(IdentifierReference(name, expr.position), null, null, expr.position),
                expr.left.copy(),
                AssignmentOrigin.BEFOREASMGEN, expr.position
            )
        }
        if(separateRightExpr) {
            val (tempVarName, _) = program.getTempVar(rightDt.getOrElse { throw FatalAstException("invalid dt") }, true)
            rightOperandReplacement = IdentifierReference(tempVarName, expr.position)
            rightAssignment = Assignment(
                AssignTarget(IdentifierReference(tempVarName, expr.position), null, null, expr.position),
                expr.right.copy(),
                AssignmentOrigin.BEFOREASMGEN, expr.position
            )
        }
        return CondExprSimplificationResult(
            leftAssignment, leftOperandReplacement,
            rightAssignment, rightOperandReplacement
        )
    }
    
    fun getTempRegisterName(dt: InferredTypes.InferredType): List<String> {
        return when {
            // TODO assume (hope) cx16.r9 isn't used for anything else during the use of this temporary variable...
            dt istype DataType.UBYTE -> listOf("cx16", "r9L")
            dt istype DataType.BOOL -> listOf("cx16", "r9L")
            dt istype DataType.BYTE -> listOf("cx16", "r9sL")
            dt istype DataType.UWORD -> listOf("cx16", "r9")
            dt istype DataType.WORD -> listOf("cx16", "r9s")
            dt.isPassByReference -> listOf("cx16", "r9")
            else -> throw FatalAstException("invalid dt $dt")
        }
    }
}
