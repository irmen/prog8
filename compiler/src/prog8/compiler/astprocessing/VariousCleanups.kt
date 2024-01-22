package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*


internal class VariousCleanups(val program: Program, val errors: IErrorReporter, val options: CompilationOptions): AstWalker() {

    override fun after(block: Block, parent: Node): Iterable<IAstModification> {
        val inheritOptions = block.definingModule.options() intersect setOf("splitarrays", "no_symbol_prefixing", "ignore_unused") subtract block.options()
        if(inheritOptions.isNotEmpty()) {
            val directive = Directive("%option", inheritOptions.map{ DirectiveArg(null, it, null, block.position) }, block.position)
            return listOf(IAstModification.InsertFirst(directive, block))
        }

        return noModifications
    }

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
        val constValue = typecast.constValue(program)
        if(constValue!=null)
            return listOf(IAstModification.ReplaceNode(typecast, constValue, parent))

        if(typecast.expression is NumericLiteral) {
            val value = (typecast.expression as NumericLiteral).cast(typecast.type)
            if(value.isValid)
                return listOf(IAstModification.ReplaceNode(typecast, value.valueOrZero(), parent))
        }

        val sourceDt = typecast.expression.inferType(program)
        if(sourceDt istype typecast.type)
            return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))

        if(parent is Assignment) {
            val targetDt = parent.target.inferType(program).getOrElse { throw FatalAstException("invalid dt ${parent.target.position}") }
            if(sourceDt istype targetDt) {
                // we can get rid of this typecast because the type is already the target type
                return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))
            }
        }

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        if(assignment.target isSameAs assignment.value) {
            // remove assignment to self
            return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
        }

        // remove duplicated assignments, but not if it's a memory mapped IO register
        val isIO = try {
            assignment.target.isIOAddress(options.compTarget.machine)
        } catch (_: FatalAstException) {
            false
        }
        if(!isIO) {
            val nextAssign = assignment.nextSibling() as? Assignment
            if (nextAssign != null && nextAssign.target.isSameAs(assignment.target, program)) {
                if (!nextAssign.isAugmentable && nextAssign.value isSameAs assignment.value && assignment.value !is IFunctionCall)    // don't remove function calls even when they're duplicates
                    return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
            }
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
        if(expr.operator == "|" || expr.operator=="or") {
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
                    if(expr.operator!="|" && expr.operator!="or")
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
            errors.info("containment could be written as just a single comparison", containment.position)
            val equals = BinaryExpression(containment.element, "==", value, containment.position)
            return listOf(IAstModification.ReplaceNode(containment, equals, parent))
        }

        fun replaceWithFalse(): Iterable<IAstModification> {
            errors.warn("condition is always false", containment.position)
            return listOf(IAstModification.ReplaceNode(containment, NumericLiteral(DataType.UBYTE, 0.0, containment.position), parent))
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

        fun checkArray(variable: VarDecl): Iterable<IAstModification> {
            return if(variable.value==null) {
                val arraySpec = variable.arraysize!!
                val size = arraySpec.indexExpr.constValue(program)?.number?.toInt() ?: throw FatalAstException("no array size")
                return if(size==0)
                    replaceWithFalse()
                else
                    noModifications
            }
            else if(variable.value is ArrayLiteral) {
                checkArray((variable.value as ArrayLiteral).value)
            }
            else noModifications
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

    override fun after(branch: ConditionalBranch, parent: Node): Iterable<IAstModification> {
        if(branch.truepart.isEmpty() && branch.elsepart.isEmpty()) {
            errors.info("removing empty conditional branch", branch.position)
            return listOf(IAstModification.Remove(branch, parent as IStatementContainer))
        }

        return noModifications
    }

    override fun after(ifElse: IfElse, parent: Node): Iterable<IAstModification> {
        if(ifElse.truepart.isEmpty() && ifElse.elsepart.isEmpty()) {
            errors.info("removing empty if-else statement", ifElse.position)
            return listOf(IAstModification.Remove(ifElse, parent as IStatementContainer))
        }
        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        val index = arrayIndexedExpression.indexer.constIndex()
        if(index!=null && index<0) {
            val target = arrayIndexedExpression.arrayvar.targetVarDecl(program)
            val arraysize = target?.arraysize?.constIndex()
            if(arraysize!=null) {
                // replace the negative index by the normal index
                val newIndex = NumericLiteral.optimalNumeric(arraysize+index, arrayIndexedExpression.indexer.position)
                arrayIndexedExpression.indexer.indexExpr = newIndex
                newIndex.linkParents(arrayIndexedExpression.indexer)
            }
        }
        return noModifications
    }
}

