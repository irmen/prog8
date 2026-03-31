package prog8.code.optimize

import prog8.code.ast.*
import prog8.code.core.BaseDataType
import prog8.code.core.ComparisonOperators
import prog8.code.core.DataType
import prog8.code.core.invertedComparisonOperator

/**
 * Boolean and bitwise expression optimizations.
 * Handles logical simplifications, bitwise identities, and complement operations.
 */
internal object BooleanOptimizers {

    /**
     * Optimizes boolean expression patterns (and/or/xor with constants, double negation, etc.).
     */
    fun optimizeBooleanExpressions(program: PtProgram): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            // Handle PtPrefix (not, -, ~)
            if (node is PtPrefix && node.operator == "not" && node.type.isBool) {
                val value = node.value
                // Double negation: not(not(x)) -> x
                if (value is PtPrefix && value.operator == "not" && value.type.isBool) {
                    val index = node.parent.children.indexOf(node)
                    node.parent.setChild(index, value.value)
                    changes++
                }
                // Negated comparisons: not(x == y) -> x != y, etc.
                else if (value is PtBinaryExpression && value.operator in ComparisonOperators) {
                    val invertedOp = invertedComparisonOperator(value.operator)
                    if (invertedOp != null) {
                        val replacement = PtBinaryExpression(invertedOp, DataType.BOOL, node.position)
                        replacement.add(value.left)
                        replacement.add(value.right)
                        val index = node.parent.children.indexOf(node)
                        node.parent.setChild(index, replacement)
                        changes++
                    }
                }
                // not(true) -> false, not(false) -> true
                else if (value is PtBool) {
                    val replacement = PtBool(!value.value, node.position)
                    val index = node.parent.children.indexOf(node)
                    node.parent.setChild(index, replacement)
                    changes++
                }
            }

            // Handle binary boolean expressions (and, or, xor)
            if (node is PtBinaryExpression && node.type.isBool) {
                val left = node.left
                val right = node.right

                when (node.operator) {
                    "and" -> {
                        // x and true -> x
                        if (right is PtBool && right.value) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                        // true and x -> x
                        else if (left is PtBool && left.value) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, right)
                            changes++
                        }
                        // x and false -> false
                        else if (right is PtBool && !right.value) {
                            val replacement = PtBool(false, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, replacement)
                            changes++
                        }
                        // false and x -> false
                        else if (left is PtBool && !left.value) {
                            val replacement = PtBool(false, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, replacement)
                            changes++
                        }
                        // x and x -> x (idempotent)
                        else if (left isSameAs right) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                        // x and not(x) -> false (complement)
                        else if (Helpers.isNegationOf(left, right) || Helpers.isNegationOf(right, left)) {
                            val replacement = PtBool(false, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, replacement)
                            changes++
                        }
                    }
                    "or" -> {
                        // x or true -> true
                        if (right is PtBool && right.value) {
                            val replacement = PtBool(true, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, replacement)
                            changes++
                        }
                        // true or x -> true
                        else if (left is PtBool && left.value) {
                            val replacement = PtBool(true, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, replacement)
                            changes++
                        }
                        // x or false -> x
                        else if (right is PtBool && !right.value) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                        // false or x -> x
                        else if (left is PtBool && !left.value) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, right)
                            changes++
                        }
                        // x or x -> x (idempotent)
                        else if (left isSameAs right) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                        // x or not(x) -> true (complement)
                        else if (Helpers.isNegationOf(left, right) || Helpers.isNegationOf(right, left)) {
                            val replacement = PtBool(true, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, replacement)
                            changes++
                        }
                    }
                    "xor" -> {
                        // x xor true -> not(x)
                        if (right is PtBool && right.value) {
                            val negation = PtPrefix("not", DataType.BOOL, node.position)
                            negation.add(left)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, negation)
                            negation.parent = node.parent
                            changes++
                        }
                        // true xor x -> not(x)
                        else if (left is PtBool && left.value) {
                            val negation = PtPrefix("not", DataType.BOOL, node.position)
                            negation.add(right)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, negation)
                            negation.parent = node.parent
                            changes++
                        }
                        // x xor false -> x
                        else if (right is PtBool && !right.value) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, left)
                            changes++
                        }
                        // false xor x -> x
                        else if (left is PtBool && !left.value) {
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, right)
                            changes++
                        }
                        // x xor x -> false (idempotent)
                        else if (left isSameAs right) {
                            val replacement = PtBool(false, node.position)
                            val index = node.parent.children.indexOf(node)
                            node.parent.setChild(index, replacement)
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
     * Optimizes double bitwise NOT: ~~x -> x
     */
    fun optimizeBitwisePrefix(program: PtProgram): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            // Handle PtPrefix for bitwise NOT (~)
            if (node is PtPrefix && node.operator == "~" && node.type.isInteger) {
                val value = node.value
                // Double bitwise NOT: ~~x -> x
                if (value is PtPrefix && value.operator == "~" && value.type.isInteger) {
                    val index = node.parent.children.indexOf(node)
                    node.parent.setChild(index, value.value)
                    changes++
                }
            }
            true
        }
        return changes
    }

    /**
     * Optimizes bitwise complement patterns: x & ~x -> 0, x | ~x -> -1
     */
    fun optimizeBitwiseComplementBinary(program: PtProgram): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            // Handle x & ~x -> 0 and x | ~x -> -1
            if (node is PtBinaryExpression && node.type.isInteger && (node.operator == "&" || node.operator == "|")) {
                val left = node.left
                val right = node.right

                if (node.operator == "&") {
                    // x & ~x -> 0
                    if (Helpers.isBitwiseNegationOf(left, right) || Helpers.isBitwiseNegationOf(right, left)) {
                        val zero = PtNumber(node.type.base, 0.0, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.setChild(index, zero)
                        changes++
                    }
                } else if (node.operator == "|") {
                    // x | ~x -> -1 (all bits set)
                    if (Helpers.isBitwiseNegationOf(left, right) || Helpers.isBitwiseNegationOf(right, left)) {
                        val allOnesValue = when (node.type.base) {
                            BaseDataType.BYTE -> -1.0
                            BaseDataType.UBYTE -> 255.0
                            BaseDataType.WORD -> -1.0
                            BaseDataType.UWORD -> 65535.0
                            BaseDataType.LONG -> -1.0
                            else -> -1.0
                        }
                        val allOnes = PtNumber(node.type.base, allOnesValue, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.setChild(index, allOnes)
                        allOnes.parent = node.parent
                        changes++
                    }
                }
            }
            true
        }
        return changes
    }
}
