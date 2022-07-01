package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.Assignment
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*


internal class VariousCleanups(val program: Program, val errors: IErrorReporter, val options: CompilationOptions): AstWalker() {

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        return if(parent is IStatementContainer)
            listOf(ScopeFlatten(scope, parent as IStatementContainer))
        else
            noModifications
    }

    private class ScopeFlatten(val scope: AnonymousScope, val into: IStatementContainer) : IAstModification {
        override fun perform() {
            val idx = into.statements.indexOf(scope)
            if(idx>=0) {
                into.statements.addAll(idx+1, scope.statements)
                scope.statements.forEach { it.parent = into as Node }
                into.statements.remove(scope)
            }
        }
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        if(typecast.expression is NumericLiteral) {
            val value = (typecast.expression as NumericLiteral).cast(typecast.type)
            if(value.isValid)
                return listOf(IAstModification.ReplaceNode(typecast, value.valueOrZero(), parent))
        }

        val sourceDt = typecast.expression.inferType(program)
        if(sourceDt istype typecast.type)
            return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))

        if(parent is Assignment) {
            val targetDt = (parent).target.inferType(program).getOrElse { throw FatalAstException("invalid dt") }
            if(sourceDt istype targetDt) {
                // we can get rid of this typecast because the type is already the target type
                return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))
            }
        }

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        val nextAssign = assignment.nextSibling() as? Assignment
        if(nextAssign!=null && nextAssign.target.isSameAs(assignment.target, program)) {
            if(!nextAssign.isAugmentable && nextAssign.value isSameAs assignment.value && assignment.value !is IFunctionCall)    // don't remove function calls even when they're duplicates
                return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
        }

        return noModifications
    }

    override fun after(expr: PrefixExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator=="+") {
            // +X --> X
            return listOf(IAstModification.ReplaceNode(expr, expr.expression, parent))
        }
        return noModifications
    }

    override fun before(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        // try to replace a multi-comparison expression (if x==1 | x==2 | x==3 ... ) by a simple containment check.
        // but only if the containment check is the top-level expression.
        if(parent is BinaryExpression)
            return noModifications
        if(expr.operator == "|") {
            val leftBinExpr1 = expr.left as? BinaryExpression
            val rightBinExpr1 = expr.right as? BinaryExpression

            if(rightBinExpr1?.operator=="==" && rightBinExpr1.right is NumericLiteral && leftBinExpr1!=null) {
                val needle = rightBinExpr1.left
                val values = mutableListOf(rightBinExpr1.right as NumericLiteral)

                fun isMultiComparisonRecurse(expr: BinaryExpression): Boolean {
                    if(expr.operator=="==") {
                        if(expr.right is NumericLiteral && expr.left isSameAs needle) {
                            values.add(expr.right as NumericLiteral)
                            return true
                        }
                        return false
                    }
                    if(expr.operator!="|")
                        return false
                    val leftBinExpr = expr.left as? BinaryExpression
                    val rightBinExpr = expr.right as? BinaryExpression
                    if(leftBinExpr==null || rightBinExpr==null || rightBinExpr.right !is NumericLiteral || !rightBinExpr.left.isSameAs(needle))
                        return false
                    if(rightBinExpr.operator=="==")
                        values.add(rightBinExpr.right as NumericLiteral)
                    else
                        return false
                    return isMultiComparisonRecurse(leftBinExpr)
                }

                if(isMultiComparisonRecurse(leftBinExpr1)) {
                    // replace it!
                    val valueCopies = values.sortedBy { it.number }.map { it.copy() }
                    val elementType = needle.inferType(program).getOrElse { throw FatalAstException("invalid needle dt") }
                    val arrayType = ElementToArrayTypes.getValue(elementType)
                    val valuesArray = ArrayLiteral(InferredTypes.InferredType.known(arrayType), valueCopies.toTypedArray(), expr.position)
                    val containment = ContainmentCheck(needle, valuesArray, expr.position)
                    return listOf(IAstModification.ReplaceNode(expr, containment, parent))
                }
            }
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator in ComparisonOperators) {
            val leftConstVal = expr.left.constValue(program)
            val rightConstVal = expr.right.constValue(program)
            // make sure the constant value is on the right of the comparison expression
            if(rightConstVal==null && leftConstVal!=null) {
                val newOperator =
                    when(expr.operator) {
                        "<" -> ">"
                        "<=" -> ">="
                        ">" -> "<"
                        ">=" -> "<="
                        else -> expr.operator
                    }
                val replacement = BinaryExpression(expr.right, newOperator, expr.left, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, replacement, parent))
            }
        }
        return noModifications
    }

    override fun after(containment: ContainmentCheck, parent: Node): Iterable<IAstModification> {
        // replace trivial containment checks with just false or a single comparison
        fun replaceWithEquals(value: NumericLiteral): Iterable<IAstModification> {
            errors.warn("containment could be written as just a single comparison", containment.position)
            val equals = BinaryExpression(containment.element, "==", value, containment.position)
            return listOf(IAstModification.ReplaceNode(containment, equals, parent))
        }

        fun replaceWithFalse(): Iterable<IAstModification> {
            errors.warn("condition is always false", containment.position)
            return listOf(IAstModification.ReplaceNode(containment, NumericLiteral.fromBoolean(false, containment.position), parent))
        }

        fun checkArray(array: Array<Expression>): Iterable<IAstModification> {
            if(array.isEmpty())
                return replaceWithFalse()
            if(array.size==1) {
                val constVal = array[0].constValue(program)
                if(constVal!=null)
                    return replaceWithEquals(constVal)
            }
            return noModifications
        }

        fun checkString(stringVal: StringLiteral): Iterable<IAstModification> {
            if(stringVal.value.isEmpty())
                return replaceWithFalse()
            if(stringVal.value.length==1) {
                val string = program.encoding.encodeString(stringVal.value, stringVal.encoding)
                return replaceWithEquals(NumericLiteral(DataType.UBYTE, string[0].toDouble(), stringVal.position))
            }
            return noModifications
        }

        when(containment.iterable) {
            is ArrayLiteral -> {
                val array = (containment.iterable as ArrayLiteral).value
                return checkArray(array)
            }
            is IdentifierReference -> {
                val variable = (containment.iterable as IdentifierReference).targetVarDecl(program)!!
                when(variable.datatype) {
                    DataType.STR -> {
                        val stringVal = (variable.value as StringLiteral)
                        return checkString(stringVal)
                    }
                    in ArrayDatatypes -> {
                        val array = (variable.value as ArrayLiteral).value
                        return checkArray(array)
                    }
                    else -> {}
                }
            }
            is RangeExpression -> {
                val constValues = (containment.iterable as RangeExpression).toConstantIntegerRange()
                if(constValues!=null) {
                    if (constValues.isEmpty())
                        return replaceWithFalse()
                    if (constValues.count()==1)
                        return replaceWithEquals(NumericLiteral.optimalNumeric(constValues.first, containment.position))
                }
            }
            is StringLiteral -> {
                val stringVal = containment.iterable as StringLiteral
                return checkString(stringVal)
            }
            else -> {}
        }
        return noModifications
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        return tryReplaceCallWithGosub(functionCallStatement, parent, program, options)
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        if(functionCallExpr.target.nameInSource==listOf("boolean")) {
            // boolean(expr) can be removed if expr is a comparison expression, or nested boolean()
            val binexpr = functionCallExpr.args.single() as? BinaryExpression
            if(binexpr!=null && binexpr.operator in ComparisonOperators) {
                return listOf(IAstModification.ReplaceNode(functionCallExpr, binexpr, parent))
            }
            val fcall = functionCallExpr.args.single() as? IFunctionCall
            if(fcall!=null && fcall.target.nameInSource==listOf("boolean")) {
                return listOf(IAstModification.ReplaceNode(functionCallExpr, fcall as Node, parent))
            }
        }
        return noModifications
    }
}

