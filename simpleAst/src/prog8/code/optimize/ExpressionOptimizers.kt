package prog8.code.optimize

import prog8.code.ast.*
import prog8.code.core.BaseDataType
import prog8.code.core.CompilationOptions
import prog8.code.core.negativePowersOfTwoFloat
import prog8.code.core.powersOfTwoFloat
import prog8.code.target.VMTarget
import kotlin.math.log2

/**
 * Expression pattern optimizations.
 * Handles algebraic identities, expression rearrangement, and strength reduction.
 */
internal object ExpressionOptimizers {

    /**
     * Optimizes binary expression patterns (special 6502 addressing mode optimization).
     */
    fun optimizeBinaryExpressions(program: PtProgram, options: CompilationOptions): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            if (node is PtBinaryExpression) {
                val constvalue = node.right.asConstValue()
                if(node.operator=="<<" && constvalue==1.0 && options.compTarget.name!=VMTarget.NAME) {
                    val typecast=node.left as? PtTypeCast
                    if(typecast!=null && typecast.type.isWord && typecast.value is PtIdentifier) {
                        val addition = node.parent as? PtBinaryExpression
                        if(addition!=null && addition.operator=="+" && addition.type.isWord) {
                            // word + (byte<<1 as uword) (== word + byte*2)  -->  (word + (byte as word)) + (byte as word)
                            val parent = addition.parent
                            val index = parent.children.indexOf(addition)
                            val addFirst = PtBinaryExpression(addition.operator, addition.type, addition.position)
                            val addSecond = PtBinaryExpression(addition.operator, addition.type, addition.position)
                            if(addition.left===node)
                                addFirst.add(addition.right)
                            else
                                addFirst.add(addition.left)
                            addFirst.add(typecast)
                            addSecond.add(addFirst)
                            addSecond.add(typecast.copy())
                            parent.setChild(index, addSecond)
                            addSecond.parent = parent
                            changes++
                        }
                    }
                }
            }
            true
        }
        return changes
    }

    /**
     * Optimizes algebraic identity patterns (x+0, x*1, x*0, etc.).
     */
    fun optimizeAlgebraicIdentities(program: PtProgram): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            if (node is PtBinaryExpression) {
                val left = node.left
                val right = node.right
                val leftConst = left.asConstValue()
                val rightConst = right.asConstValue()

                when (node.operator) {
                    "+" -> {
                        // x + 0 -> x
                        if (rightConst == 0.0) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                        // 0 + x -> x
                        else if (leftConst == 0.0) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, right)
                            changes++
                        }
                    }
                    "-" -> {
                        // x - 0 -> x
                        if (rightConst == 0.0) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                        // 0 - x -> -x
                        if (leftConst == 0.0) {
                            val negation = PtPrefix("-", right.type, node.position)
                            negation.add(right)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, negation)
                            negation.parent = node.parent
                            changes++
                        }
                    }
                    "*" -> {
                        // x * 0 -> 0
                        if (rightConst == 0.0) {
                            val zero = PtNumber(node.type.base, 0.0, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, zero)
                            changes++
                        }
                        // 0 * x -> 0
                        else if (leftConst == 0.0) {
                            val zero = PtNumber(node.type.base, 0.0, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, zero)
                            changes++
                        }
                        // x * 1 -> x
                        else if (rightConst == 1.0) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                        // 1 * x -> x
                        else if (leftConst == 1.0) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, right)
                            changes++
                        }
                        // x * power-of-two -> bitshift
                        else if (!node.right.type.isFloat) {
                            if (rightConst in powersOfTwoFloat) {
                                val numshifts = log2(rightConst!!)
                                val shift = PtBinaryExpression("<<", node.type, node.position)
                                shift.add(node.left)
                                shift.add(PtNumber(BaseDataType.UBYTE, numshifts, node.position))
                                shift.parent = node.parent
                                val index = node.parent.children.indexOf(node)
                                node.parent.setChild(index, shift)
                                changes++
                            } else if (rightConst in negativePowersOfTwoFloat) {
                                val numshifts = log2(-rightConst!!)
                                val negation = PtPrefix("-", node.left.type, node.position)
                                negation.add(node.left)
                                val shift = PtBinaryExpression("<<", node.type, node.position)
                                shift.add(negation)
                                shift.add(PtNumber(BaseDataType.UBYTE, numshifts, node.position))
                                shift.parent = node.parent
                                val index = node.parent.children.indexOf(node)
                                node.parent.setChild(index, shift)
                                changes++
                            }
                        }
                    }
                    "/" -> {
                        // x / 1 -> x
                        if (rightConst == 1.0) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                        // 0 / x -> 0 (when x is not zero - but we can't check that here safely)
                        // Only do this for integer types where division by zero is undefined anyway
                        if (leftConst == 0.0 && !node.type.isFloat) {
                            val zero = PtNumber(node.type.base, 0.0, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, zero)
                            changes++
                        }
                    }
                    "xor" -> {
                        // x xor x -> 0
                        if (left isSameAs right) {
                            val zero = PtNumber(node.type.base, 0.0, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, zero)
                            changes++
                        }
                    }
                    "&" -> {
                        // x & 0 -> 0
                        if (rightConst == 0.0) {
                            val zero = PtNumber(node.type.base, 0.0, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, zero)
                            changes++
                        }
                        // 0 & x -> 0
                        else if (leftConst == 0.0) {
                            val zero = PtNumber(node.type.base, 0.0, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, zero)
                            changes++
                        }
                        // x & -1 -> x (all bits set = identity for AND)
                        else if (Helpers.isAllOnesForType(right, node.type)) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                        // -1 & x -> x
                        else if (Helpers.isAllOnesForType(left, node.type)) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, right)
                            changes++
                        }
                        // x & x -> x (idempotent)
                        else if (left isSameAs right) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                    }
                    "|" -> {
                        // x | 0 -> x
                        if (rightConst == 0.0) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                        // 0 | x -> x
                        else if (leftConst == 0.0) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, right)
                            changes++
                        }
                        // x | -1 -> -1 (all bits set = absorbing element for OR)
                        else if (Helpers.isAllOnesForType(right, node.type)) {
                            val allOnes = right
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, allOnes)
                            changes++
                        }
                        // -1 | x -> -1
                        else if (Helpers.isAllOnesForType(left, node.type)) {
                            val allOnes = left
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, allOnes)
                            changes++
                        }
                        // x | x -> x (idempotent)
                        else if (left isSameAs right) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                    }
                    "^" -> {
                        // x ^ 0 -> x
                        if (rightConst == 0.0) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                        // 0 ^ x -> x
                        else if (leftConst == 0.0) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, right)
                            changes++
                        }
                        // x ^ -1 -> ~x (XOR with all 1s = bitwise NOT)
                        else if (Helpers.isAllOnesForType(right, node.type)) {
                            val negation = PtPrefix("~", node.type, node.position)
                            negation.add(left)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, negation)
                            negation.parent = node.parent
                            changes++
                        }
                        // -1 ^ x -> ~x
                        else if (Helpers.isAllOnesForType(left, node.type)) {
                            val negation = PtPrefix("~", node.type, node.position)
                            negation.add(right)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, negation)
                            negation.parent = node.parent
                            changes++
                        }
                        // x ^ x -> 0 (idempotent)
                        else if (left isSameAs right) {
                            val zero = PtNumber(node.type.base, 0.0, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, zero)
                            changes++
                        }
                    }
                }
            }
            true
        }
        return changes
    }

    /**
     * Rearranges expressions for better optimization opportunities.
     * Moves negations, factors out common terms.
     */
    fun optimizeExpressionRearrangement(program: PtProgram): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            if (node is PtBinaryExpression) {
                val left = node.left
                val right = node.right

                // Move negation: (-x) + y -> y - x
                if (node.operator == "+" && left is PtPrefix && left.operator == "-") {
                    val negated = left.value
                    val replacement = PtBinaryExpression("-", node.type, node.position)
                    replacement.add(right)
                    replacement.add(negated)
                    val index = node.parent.children.indexOf(node)
                    node.parent.setChild(index, replacement)
                    changes++
                }
                // x + (-y) -> x - y
                else if (node.operator == "+" && right is PtPrefix && right.operator == "-") {
                    val replacement = PtBinaryExpression("-", node.type, node.position)
                    replacement.add(left)
                    replacement.add(right.value)
                    val index = node.parent.children.indexOf(node)
                    node.parent.setChild(index, replacement)
                    changes++
                }
                // x - (-y) -> x + y
                else if (node.operator == "-" && right is PtPrefix && right.operator == "-") {
                    val replacement = PtBinaryExpression("+", node.type, node.position)
                    replacement.add(left)
                    replacement.add(right.value)
                    val index = node.parent.children.indexOf(node)
                    node.parent.setChild(index, replacement)
                    changes++
                }

                // Factor out common terms: y*x + x -> x*(y + 1), x*y + x -> x*(y + 1)
                if ((node.operator == "+" || node.operator == "-") && left.type.isNumeric && right.type.isNumeric) {
                    val leftBinExpr = left as? PtBinaryExpression
                    if (leftBinExpr != null && leftBinExpr.operator == "*") {
                        // Y*X + X -> X*(Y + 1) or Y*X - X -> X*(Y - 1)
                        val x = right
                        val y = Helpers.determineYForFactoring(x, leftBinExpr)
                        if (y != null) {
                            val op = if (node.operator == "+") "+" else "-"
                            val factor = PtBinaryExpression(op, left.type, node.position)
                            factor.add(y)
                            factor.add(PtNumber(left.type.base, 1.0, node.position))
                            val replacement = PtBinaryExpression("*", node.type, node.position)
                            replacement.add(x)
                            replacement.add(factor)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, replacement)
                            changes++
                        }
                    }
                    // X + Y*X -> X*(Y + 1)
                    else if (node.operator == "+") {
                        val rightBinExpr = right as? PtBinaryExpression
                        if (rightBinExpr != null && rightBinExpr.operator == "*") {
                            val x = left
                            val y = Helpers.determineYForFactoring(x, rightBinExpr)
                            if (y != null) {
                                val factor = PtBinaryExpression("+", left.type, node.position)
                                factor.add(y)
                                factor.add(PtNumber(left.type.base, 1.0, node.position))
                                val replacement = PtBinaryExpression("*", node.type, node.position)
                                replacement.add(x)
                                replacement.add(factor)
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
     * Strength reduction: replaces expensive operations with cheaper equivalents.
     * Division/modulo by powers of two become shifts/masks.
     */
    fun optimizeStrengthReduction(program: PtProgram, options: CompilationOptions): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            if (node is PtBinaryExpression && node.type.isInteger) {
                val rightConst = node.right.asConstValue()

                // Division by power of two: x / 2^n -> x >> n (for unsigned integers only)
                if (node.operator == "/" && rightConst != null && node.type.isUnsignedInteger) {
                    if (rightConst in powersOfTwoFloat) {
                        val numshifts = log2(rightConst)
                        val shift = PtBinaryExpression(">>", node.type, node.position)
                        shift.add(node.left)
                        shift.add(PtNumber(BaseDataType.UBYTE, numshifts, node.position))
                        val index = node.parent.children.indexOf(node)
                        node.parent.setChild(index, shift)
                        shift.parent = node.parent
                        changes++
                    }
                }

                // Modulo by power of two: x % 2^n -> x & (2^n - 1) (for all integers)
                if (node.operator == "%" && rightConst != null) {
                    if (rightConst in powersOfTwoFloat) {
                        val mask = rightConst - 1.0
                        val andExpr = PtBinaryExpression("&", node.type, node.position)
                        andExpr.add(node.left)
                        andExpr.add(PtNumber(node.type.base, mask, node.position))
                        val index = node.parent.children.indexOf(node)
                        node.parent.setChild(index, andExpr)
                        andExpr.parent = node.parent
                        changes++
                    }
                    // x % 1 -> 0
                    else if (rightConst == 1.0) {
                        val zero = PtNumber(node.type.base, 0.0, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.setChild(index, zero)
                        changes++
                    }
                }
            }
            true
        }
        return changes
    }

    /**
     * Calculates the "complexity" of an expression.
     * Lower values = simpler expressions.
     */
    private fun complexity(e: PtExpression): Int {
        return when {
            e.asConstInteger() != null -> 0
            e is PtIdentifier -> 1
            e is PtTypeCast -> complexity(e.value)
            e is PtAddressOf && !e.isFromArrayElement -> 1
            e is PtMemoryByte -> 2
            e is PtArrayIndexer && e.index.asConstInteger() != null -> 2
            else -> 10
        }
    }

    /**
     * Returns the swapped comparison operator for swapped operands.
     * For example: a > b becomes b < a, so > becomes <
     * == and != remain the same.
     * Returns null for non-comparison operators.
     */
    private fun swappedComparisonOperator(operator: String): String? {
        return when (operator) {
            ">" -> "<"
            "<" -> ">"
            ">=" -> "<="
            "<=" -> ">="
            "==", "!=" -> operator
            else -> null
        }
    }

    /**
     * Optimizes operand order by moving simpler expressions to the right.
     * Only swaps if maySwapOperandOrder() returns true and left is simpler than right.
     * For comparison operators, also swaps the operator appropriately.
     */
    fun optimizeOperandOrder(program: PtProgram): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            if (node is PtBinaryExpression && node.maySwapOperandOrder()) {
                val leftComplexity = complexity(node.left)
                val rightComplexity = complexity(node.right)

                // Want simplest term on the right, so swap if left is simpler than right
                if (leftComplexity < rightComplexity) {
                    println("SWAP:  $leftComplexity vs $rightComplexity  :  ${node.left}   ${node.operator}  ${node.right}")
                    // Determine the new operator (may need to swap for comparisons)
                    val newOperator = swappedComparisonOperator(node.operator) ?: node.operator

                    // Create replacement with swapped operands
                    val replacement = PtBinaryExpression(newOperator, node.type, node.position)
                    replacement.add(node.right)
                    replacement.add(node.left)
                    replacement.parent = node.parent

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
