package prog8.code.optimize

import prog8.code.ast.*
import prog8.code.core.BaseDataType
import prog8.code.core.ComparisonOperators
import prog8.code.core.DataType
import prog8.code.core.IErrorReporter

/**
 * Comparison pattern optimizations.
 * Handles comparison simplifications, identities, and special cases.
 */
internal object ComparisonOptimizers {

    /**
     * Simplifies comparison patterns based on operand values and types.
     */
    fun optimizeComparisonSimplifications(program: PtProgram): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            if (node is PtBinaryExpression && node.operator in ComparisonOperators) {
                val left = node.left
                val right = node.right
                val rightConst = right.asConstValue()
                val leftType = left.type
                val rightType = right.type

                // Integer comparison simplifications
                if (!leftType.isFloat && rightConst != null) {
                    // x >= 1 -> x > 0 (for integers)
                    if (node.operator == ">=" && rightConst == 1.0) {
                        val replacement = PtBinaryExpression(">", node.type, node.position)
                        replacement.add(left)
                        replacement.add(PtNumber(rightType.base, 0.0, right.position))
                        val index = node.parent.children.indexOf(node)
                        node.parent.setChild(index, replacement)
                        changes++
                    }
                    // x < 1 -> x <= 0 (for integers)
                    else if (node.operator == "<" && rightConst == 1.0) {
                        val replacement = PtBinaryExpression("<=", node.type, node.position)
                        replacement.add(left)
                        replacement.add(PtNumber(rightType.base, 0.0, right.position))
                        val index = node.parent.children.indexOf(node)
                        node.parent.setChild(index, replacement)
                        changes++
                    }
                    // x <= -1 -> x < 0 (for signed integers only, NOT float)
                    else if (node.operator == "<=" && rightConst == -1.0 && leftType.isSignedInteger) {
                        val replacement = PtBinaryExpression("<", node.type, node.position)
                        replacement.add(left)
                        replacement.add(PtNumber(rightType.base, 0.0, right.position))
                        val index = node.parent.children.indexOf(node)
                        node.parent.setChild(index, replacement)
                        changes++
                    }
                    // x > -1 -> x >= 0 (for signed integers only, NOT float)
                    else if (node.operator == ">" && rightConst == -1.0 && leftType.isSignedInteger) {
                        val replacement = PtBinaryExpression(">=", node.type, node.position)
                        replacement.add(left)
                        replacement.add(PtNumber(rightType.base, 0.0, right.position))
                        val index = node.parent.children.indexOf(node)
                        node.parent.setChild(index, replacement)
                        changes++
                    }
                }

                // Unsigned-specific optimizations (for unsigned integers only)
                if (leftType.isUnsignedInteger && rightConst == 0.0) {
                    when (node.operator) {
                        ">=" -> {
                            // unsigned >= 0 -> true
                            val index = node.parent.children.indexOf(node)
                            val replacement = PtBool(true, node.position)
                            node.parent.setChild(index, replacement)
                            changes++
                        }
                        "<" -> {
                            // unsigned < 0 -> false
                            val index = node.parent.children.indexOf(node)
                            val replacement = PtBool(false, node.position)
                            node.parent.setChild(index, replacement)
                            changes++
                        }
                        "<=" -> {
                            // unsigned <= 0 -> unsigned == 0
                            val replacement = PtBinaryExpression("==", node.type, node.position)
                            replacement.add(left)
                            replacement.add(right)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, replacement)
                            changes++
                        }
                        ">" -> {
                            // unsigned > 0 -> unsigned != 0
                            val replacement = PtBinaryExpression("!=", node.type, node.position)
                            replacement.add(left)
                            replacement.add(right)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, replacement)
                            changes++
                        }
                    }
                }

                // Comparison with arithmetic: x <= y-1 -> x < y, x >= y+1 -> x > y
                if (leftType.isInteger && rightType.isInteger) {
                    val rightExpr = right as? PtBinaryExpression
                    if (rightExpr != null) {
                        val rightRightConst = rightExpr.right.asConstValue()
                        if (rightRightConst == 1.0) {
                            if (node.operator == "<=" && rightExpr.operator == "-") {
                                val replacement = PtBinaryExpression("<", node.type, node.position)
                                replacement.add(left)
                                replacement.add(rightExpr.left)
                                val index = node.parent.children.indexOf(node)
                                node.parent.setChild(index, replacement)
                                changes++
                            } else if (node.operator == ">=" && rightExpr.operator == "+") {
                                val replacement = PtBinaryExpression(">", node.type, node.position)
                                replacement.add(left)
                                replacement.add(rightExpr.left)
                                val index = node.parent.children.indexOf(node)
                                node.parent.setChild(index, replacement)
                                changes++
                            }
                        }
                    }
                }
            }
            true
        }
        return changes
    }

    /**
     * Optimizes comparison identities (x==x, x<x, etc.).
     */
    fun optimizeComparisonIdentities(program: PtProgram): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            if (node is PtBinaryExpression && node.operator in ComparisonOperators) {
                val left = node.left
                val right = node.right

                // x == x -> true, x != x -> false, x < x -> false, etc.
                // (constant-vs-constant is already folded in compilerAST phase)
                if (left isSameAs right) {
                    val result = when (node.operator) {
                        "==" -> true
                        "!=" -> false
                        "<" -> false
                        ">" -> false
                        "<=" -> true
                        ">=" -> true
                        else -> return@walkAst true
                    }
                    val index = node.parent.children.indexOf(node)
                    val replacement = PtBool(result, node.position)
                    node.parent.setChild(index, replacement)
                    changes++
                }
            }
            true
        }
        return changes
    }

    /**
     * Optimizes sgn() comparisons on integers.
     * Note: Does NOT optimize sgn() on floats - those ARE more efficient than normal compares!
     */
    fun optimizeSgnComparisons(program: PtProgram, errors: IErrorReporter): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            if(node is PtFunctionCall && node.builtin && node.name=="sgn" && node.args[0].type.isInteger) {
                val comparison = node.parent as? PtBinaryExpression
                if(comparison!=null && comparison.right.asConstInteger()==0 && comparison.operator in ComparisonOperators) {
                    //  sgn(integer) >= 0   -> just use   integer >= 0
                    val replacement = PtBinaryExpression(comparison.operator, DataType.BOOL, comparison.position)
                    replacement.add(node.args[0])
                    replacement.add(PtNumber(node.args[0].type.base, 0.0, comparison.position))
                    val index = comparison.parent.children.indexOf(comparison)
                    comparison.parent.setChild(index, replacement)
                    changes++
                }
            }
            true
        }
        return changes
    }

    /**
     * Optimizes float comparisons to zero using sgn().
     */
    fun optimizeFloatComparesToZero(program: PtProgram): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            if (node is PtBinaryExpression) {
                val constvalue = node.right.asConstValue()
                if(node.type.isBool && constvalue==0.0 && node.left.type.isFloat && node.operator in ComparisonOperators) {
                    // float == 0 --> sgn(float) == 0
                    val sign = PtFunctionCall("sgn", true, true, arrayOf(DataType.BYTE), node.position)
                    sign.add(node.left)
                    val replacement = PtBinaryExpression(node.operator, DataType.BOOL, node.position)
                    replacement.add(sign)
                    replacement.add(PtNumber(BaseDataType.BYTE, 0.0, node.position))
                    val index = node.parent.children.indexOf(node)
                    node.parent.setChild(index, replacement)
                    changes++
                }
            }
            true
        }
        return changes
    }
}
