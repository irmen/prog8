package prog8.code.optimize

import prog8.code.ast.*
import prog8.code.core.BaseDataType
import prog8.code.core.Cx16VirtualRegisters
import prog8.code.core.DataType
import prog8.code.core.RegisterOrPair

/**
 * Helper functions used by various optimization passes.
 * These are grouped together for better code organization.
 */
internal object Helpers {
    /**
     * Check if an identifier matches a CX16 virtual register.
     * Used by VariableOptimizers.optimizeAssignTargets to detect when a function returns to the same register.
     */
    fun isSame(identifier: PtIdentifier, type: DataType, returnedRegister: RegisterOrPair): Boolean {
        if(returnedRegister in Cx16VirtualRegisters) {
            val regname = returnedRegister.name.lowercase()
            val identifierRegName = identifier.name.substringAfterLast('.')
            /*
                cx16.r?    UWORD
                cx16.r?s   WORD
                cx16.r?L   UBYTE
                cx16.r?H   UBYTE
                cx16.r?sL  BYTE
                cx16.r?sH  BYTE
             */
            if(identifier.type.isByte && type.isByte) {
                if(identifier.name.startsWith("cx16.$regname") && identifierRegName.startsWith(regname)) {
                    return identifierRegName.substring(2) in arrayOf("", "L", "sL")     // note: not the -H (msb) variants!
                }
            }
            else if(identifier.type.isWord && type.isWord) {
                if(identifier.name.startsWith("cx16.$regname") && identifierRegName.startsWith(regname)) {
                    return identifierRegName.substring(2) in arrayOf("", "s")
                }
            }
        }
        return false   // there are no identifiers directly corresponding to cpu registers
    }

    /**
     * Check if expr2 is the negation of expr1 (i.e., expr2 == not(expr1))
     */
    fun isNegationOf(expr1: PtExpression, expr2: PtExpression): Boolean {
        if (expr2 is PtPrefix && expr2.operator == "not" && expr2.type.isBool) {
            return expr1 isSameAs expr2.value
        }
        return false
    }

    /**
     * Check if a numeric expression represents "all ones" for its type (-1 for signed, max value for unsigned)
     */
    fun isAllOnesForType(expr: PtExpression, type: DataType): Boolean {
        val num = expr.asConstValue() ?: return false
        return when (type.base) {
            BaseDataType.BYTE -> num == -1.0
            BaseDataType.UBYTE -> num == 255.0
            BaseDataType.WORD -> num == -1.0
            BaseDataType.UWORD -> num == 65535.0
            BaseDataType.LONG -> num == -1.0
            else -> false
        }
    }

    /**
     * Check if expr2 is the bitwise negation of expr1 (i.e., expr2 == ~expr1)
     */
    fun isBitwiseNegationOf(expr1: PtExpression, expr2: PtExpression): Boolean {
        if (expr2 is PtPrefix && expr2.operator == "~" && expr2.type.isInteger) {
            return expr1 isSameAs expr2.value
        }
        return false
    }

    /**
     * Helper to find Y in expressions like Y*X or X*Y given X
     * Used by ExpressionOptimizers.optimizeExpressionRearrangement for factoring common terms.
     */
    fun determineYForFactoring(x: PtExpression, binExpr: PtBinaryExpression): PtExpression? {
        if (binExpr.left isSameAs x) {
            return binExpr.right
        } else if (binExpr.right isSameAs x) {
            return binExpr.left
        }
        return null
    }
}

/**
 * Check if a statement or expression references a specific identifier.
 * Used by VariableOptimizers.optimizeRedundantVarInits to determine if a variable initialization
 * can be safely removed because the variable isn't read before being overwritten.
 *
 * NOTE: This function uses conservative assumptions:
 * - PtFunctionCall always returns true (may reference any variable via nested scope)
 * - PtJump always returns true (conservative: jump target may depend on value)
 * - PtInlineAssembly always returns true (can't analyze assembly code)
 *
 * This is intentional - no Control Flow Analysis or dataflow analysis is done
 * to keep the implementation simple.
 */
fun referencesIdentifier(node: PtNode, identifier: PtIdentifier): Boolean {

    fun refsIdentifier(expr: PtExpression): Boolean = when(expr) {
        is PtBool,
        is PtIrRegister,
        is PtNumber,
        is PtString -> false
        is PtIdentifier -> expr.name==identifier.name
        is PtAddressOf -> expr.children.any { referencesIdentifier(it, identifier) }
        is PtArray -> expr.children.any { referencesIdentifier(it, identifier) }
        is PtArrayIndexer -> expr.children.any { referencesIdentifier(it, identifier) }
        is PtBinaryExpression -> expr.children.any { referencesIdentifier(it, identifier) }
        is PtBranchCondExpression -> expr.children.any { referencesIdentifier(it, identifier) }
        is PtContainmentCheck -> expr.children.any { referencesIdentifier(it, identifier) }
        is PtIfExpression -> expr.children.any { referencesIdentifier(it, identifier) }
        is PtFunctionCall -> true
        is PtMemoryByte -> expr.children.any { referencesIdentifier(it, identifier) }
        is PtPointerDeref -> false
        is PtPrefix -> expr.children.any { referencesIdentifier(it, identifier) }
        is PtRange -> expr.children.any { referencesIdentifier(it, identifier) }
        is PtTypeCast -> expr.children.any { referencesIdentifier(it, identifier) }
        is PtConstant -> false
    }

    return when(node) {
        is PtAssignment -> node.children.any { referencesIdentifier(it, identifier) }
        is PtAugmentedAssign -> node.children.any { referencesIdentifier(it, identifier) }
        is PtIdentifier -> node.name==identifier.name
        is PtVariable -> node.name==identifier.name || node.value!=null && refsIdentifier(node.value)
        is PtSwap -> referencesIdentifier(node.target1, identifier) || referencesIdentifier(node.target2, identifier)
        is PtNodeGroup -> node.children.any { referencesIdentifier(it, identifier) }
        is PtRepeatLoop -> node.children.any { referencesIdentifier(it, identifier) }
        is PtJmpTable -> node.children.any { referencesIdentifier(it, identifier) }
        is PtWhen -> node.children.any { referencesIdentifier(it, identifier) }
        is PtForLoop -> node.children.any { referencesIdentifier(it, identifier) }
        is PtIfElse -> node.children.any { referencesIdentifier(it, identifier) }
        is PtWhenChoice -> node.children.any { referencesIdentifier(it, identifier) }
        is PtAssignTarget -> node.children.any { referencesIdentifier(it, identifier) }
        is PtConditionalBranch -> node.children.any { referencesIdentifier(it, identifier) }
        is PtDefer -> node.children.any { referencesIdentifier(it, identifier) }
        is PtFunctionCall -> true
        is PtJump -> true           // Conservative: jump target may depend on the value
        is PtInlineAssembly -> true
        is PtExpression -> refsIdentifier(node)
        else -> false   // everything else is a node that cannot ever contain the variable, so false
    }
}
