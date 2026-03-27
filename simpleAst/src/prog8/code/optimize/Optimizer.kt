package prog8.code.optimize

import prog8.code.StExtSub
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.VMTarget
import kotlin.math.log2

/**
 * Context object passed to all optimization functions.
 * Consolidates the common parameters to reduce boilerplate.
 */
private data class OptimizerContext(
    val st: SymbolTable,
    val options: CompilationOptions,
    val errors: IErrorReporter
)

/**
 * Simple AST optimizer for the simplified AST.
 *
 * **Design note:** This optimizer uses simple tree-walks and pattern matching.
 * **No Control Flow Analysis (CFA) or dataflow analysis is (or will be) done** to keep
 * the implementation simple. This means some optimization opportunities may be missed
 * (e.g., jumps like break/continue are treated conservatively, function calls are assumed
 * to potentially reference any variable), but the generated code remains correct.
 *
 * **Redundant optimizations NOT done here** (already handled in compilerAST phase):
 * - **Constant folding** (e.g., `5 + 3` → `8`) - done by `ConstantFoldingOptimizer` in compilerAST
 * - **General expression simplification** - done by `ExpressionSimplifier` in compilerAST
 * - **Statement optimization** - done by `StatementOptimizer` in compilerAST
 * - **Dead code elimination** - done by `UnusedCodeRemover` in compilerAST
 * - **Subroutine inlining** - done in compilerAST optimization loop
 *
 * This simpleAst optimizer focuses on:
 * - Pattern-specific optimizations that may emerge after AST simplification
 * - Algebraic identities (x+0, x*1, x&0, etc.)
 * - Boolean/logic simplifications (x and true, not(not x), etc.)
 * - Bitwise identities (x&-1, x|-1, x^-1, etc.)
 * - Comparison simplifications (unsigned>=0→true, x<=y-1→x<y, etc.)
 * - Comparison identities (x==x→true, x<x→false)
 * - Expression rearrangement (x+(-y)→x-y, factoring common terms)
 * - Strength reduction (x/2^n→x>>n for unsigned, x%2^n→x&(2^n-1))
 * - Address-of/dereference cancellation (@(&x)→x for byte types)
 * - Conditional simplification (if true/false branches)
 */

fun optimizeSimplifiedAst(program: PtProgram, options: CompilationOptions, st: SymbolTable, errors: IErrorReporter) {
    if (!options.optimize)
        return
    
    val ctx = OptimizerContext(st, options, errors)
    
    // Run fixpoint optimizations until no more changes occur
    runFixpointOptimizations(program, ctx)
    
    // Run single-pass optimizations (only need to run once)
    runSinglePassOptimizations(program, ctx)
}

/**
 * Runs optimizations that may create opportunities for each other.
 * These are run in a loop until no more changes occur.
 */
private fun runFixpointOptimizations(program: PtProgram, ctx: OptimizerContext) {
    while (ctx.errors.noErrors() &&
        optimizeAssignTargets(program, ctx.st)
        + optimizeFloatComparesToZero(program)
        + optimizeLsbMsbOnStructfields(program)
        + optimizeSingleWhens(program, ctx.errors)
        + optimizeSgnComparisons(program, ctx.errors)
        + optimizeBinaryExpressions(program, ctx.options)
        + optimizeAlgebraicIdentities(program)
        + optimizeAddressOfDereference(program)
        + optimizeBooleanExpressions(program)
        + optimizeBitwisePrefix(program)
        + optimizeBitwiseComplementBinary(program)
        + optimizeComparisonSimplifications(program)
        + optimizeExpressionRearrangement(program)
        + optimizeComparisonIdentities(program)
        + optimizeConditionalExpressions(program, ctx.errors)
        + optimizeDeadConditionalBranches(program)
        + optimizeStrengthReduction(program, ctx.options) > 0) {
        // keep rolling
    }
}

/**
 * Runs optimizations that only need to execute once.
 * These don't create opportunities for other optimizations, so no fixpoint loop needed.
 */
private fun runSinglePassOptimizations(program: PtProgram, ctx: OptimizerContext) {
    optimizeRedundantVarInits(program)
}


// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

private fun walkAst(root: PtNode, act: (node: PtNode, depth: Int) -> Boolean) {
    fun recurse(node: PtNode, depth: Int) {
        if(act(node, depth))
            node.children.forEach { recurse(it, depth+1) }
    }
    recurse(root, 0)
}


// ============================================================================
// VARIABLE OPTIMIZATIONS
// ============================================================================

private fun optimizeAssignTargets(program: PtProgram, st: SymbolTable): Int {
    var changes = 0
    walkAst(program) { node: PtNode, depth: Int ->
        if(node is PtAssignment) {
            val value = node.value
            val functionName = if (value is PtFunctionCall) value.name else null
            if(functionName!=null) {
                val stNode = st.lookup(functionName)
                if (stNode is StExtSub) {
                    require(node.children.size==stNode.returns.size+1) {
                        "number of targets must match return values"
                    }
                    node.children.zip(stNode.returns).withIndex().forEach { (index, xx) ->
                        val target = xx.first as PtAssignTarget
                        val returnedRegister = xx.second.register.registerOrPair
                        if(returnedRegister!=null && !target.void && target.identifier!=null) {
                            if(Helpers.isSame(target.identifier!!, xx.second.type, returnedRegister)) {
                                // output register is already identical to target register, so it can become void
                                val voidTarget = PtAssignTarget(true, target.position)
                                node.children[index] = voidTarget
                                voidTarget.parent = node
                                changes++
                            }
                        }
                    }
                }
                if(node.children.dropLast(1).all { (it as PtAssignTarget).void }) {
                    // all targets are now void, the whole assignment can be discarded and replaced by just a (void) call to the subroutine
                    val index = node.parent.children.indexOf(node)
                    val voidCall = PtFunctionCall(functionName, false, false, emptyArray(), value.position)
                    value.children.forEach { voidCall.add(it) }
                    node.parent.children[index] = voidCall
                    voidCall.parent = node.parent
                    changes++
                }
            }
        }
        true
    }
    return changes
}


// ============================================================================
// EXPRESSION OPTIMIZATIONS
// ============================================================================

private fun optimizeBinaryExpressions(program: PtProgram, options: CompilationOptions): Int {
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
                        parent.children[index] = addSecond
                        addSecond.parent = parent
                        changes++
                    }
                }
            }
            else if (node.operator=="*" && !node.right.type.isFloat) {
                if (constvalue in powersOfTwoFloat) {
                    // x * power-of-two -> bitshift
                    val numshifts = log2(constvalue!!)
                    val shift = PtBinaryExpression("<<", node.type, node.position)
                    shift.add(node.left)
                    shift.add(PtNumber(BaseDataType.UBYTE, numshifts, node.position))
                    shift.parent = node.parent
                    val index = node.parent.children.indexOf(node)
                    node.parent.children[index] = shift
                    changes++
                } else if(constvalue in negativePowersOfTwoFloat) {
                    TODO("x * negative power-of-two -> bitshift  ${node.position}")
                }
            } else if(node.operator=="*" && !node.right.type.isFloat && constvalue in negativePowersOfTwoFloat) {
                TODO("x * negative power-of-two -> bitshift  ${node.position}")
            }
        }
        true
    }
    return changes
}


private fun optimizeAlgebraicIdentities(program: PtProgram): Int {
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
                        node.parent.children[index] = left
                        changes++
                    }
                    // 0 + x -> x
                    else if (leftConst == 0.0) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = right
                        changes++
                    }
                }
                "-" -> {
                    // x - 0 -> x
                    if (rightConst == 0.0) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = left
                        changes++
                    }
                    // 0 - x -> -x
                    if (leftConst == 0.0) {
                        val negation = PtPrefix("-", right.type, node.position)
                        negation.add(right)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = negation
                        negation.parent = node.parent
                        changes++
                    }
                }
                "*" -> {
                    // x * 0 -> 0
                    if (rightConst == 0.0) {
                        val zero = PtNumber(node.type.base, 0.0, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = zero
                        zero.parent = node.parent
                        changes++
                    }
                    // 0 * x -> 0
                    else if (leftConst == 0.0) {
                        val zero = PtNumber(node.type.base, 0.0, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = zero
                        zero.parent = node.parent
                        changes++
                    }
                    // x * 1 -> x
                    else if (rightConst == 1.0) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = left
                        changes++
                    }
                    // 1 * x -> x
                    else if (leftConst == 1.0) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = right
                        changes++
                    }
                }
                "/" -> {
                    // x / 1 -> x
                    if (rightConst == 1.0) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = left
                        changes++
                    }
                    // 0 / x -> 0 (when x is not zero - but we can't check that here safely)
                    // Only do this for integer types where division by zero is undefined anyway
                    if (leftConst == 0.0 && !node.type.isFloat) {
                        val zero = PtNumber(node.type.base, 0.0, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = zero
                        zero.parent = node.parent
                        changes++
                    }
                }
                "xor" -> {
                    // x xor x -> 0
                    if (left isSameAs right) {
                        val zero = PtNumber(node.type.base, 0.0, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = zero
                        zero.parent = node.parent
                        changes++
                    }
                }
                "&" -> {
                    // x & 0 -> 0
                    if (rightConst == 0.0) {
                        val zero = PtNumber(node.type.base, 0.0, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = zero
                        zero.parent = node.parent
                        changes++
                    }
                    // 0 & x -> 0
                    else if (leftConst == 0.0) {
                        val zero = PtNumber(node.type.base, 0.0, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = zero
                        zero.parent = node.parent
                        changes++
                    }
                    // x & -1 -> x (all bits set = identity for AND)
                    else if (Helpers.isAllOnesForType(right, node.type)) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = left
                        changes++
                    }
                    // -1 & x -> x
                    else if (Helpers.isAllOnesForType(left, node.type)) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = right
                        changes++
                    }
                    // x & x -> x (idempotent)
                    else if (left isSameAs right) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = left
                        changes++
                    }
                }
                "|" -> {
                    // x | 0 -> x
                    if (rightConst == 0.0) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = left
                        changes++
                    }
                    // 0 | x -> x
                    else if (leftConst == 0.0) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = right
                        changes++
                    }
                    // x | -1 -> -1 (all bits set = absorbing element for OR)
                    else if (Helpers.isAllOnesForType(right, node.type)) {
                        val allOnes = right
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = allOnes
                        changes++
                    }
                    // -1 | x -> -1
                    else if (Helpers.isAllOnesForType(left, node.type)) {
                        val allOnes = left
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = allOnes
                        changes++
                    }
                    // x | x -> x (idempotent)
                    else if (left isSameAs right) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = left
                        changes++
                    }
                }
                "^" -> {
                    // x ^ 0 -> x
                    if (rightConst == 0.0) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = left
                        changes++
                    }
                    // 0 ^ x -> x
                    else if (leftConst == 0.0) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = right
                        changes++
                    }
                    // x ^ -1 -> ~x (XOR with all 1s = bitwise NOT)
                    else if (Helpers.isAllOnesForType(right, node.type)) {
                        val negation = PtPrefix("~", node.type, node.position)
                        negation.add(left)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = negation
                        negation.parent = node.parent
                        changes++
                    }
                    // -1 ^ x -> ~x
                    else if (Helpers.isAllOnesForType(left, node.type)) {
                        val negation = PtPrefix("~", node.type, node.position)
                        negation.add(right)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = negation
                        negation.parent = node.parent
                        changes++
                    }
                    // x ^ x -> 0 (idempotent)
                    else if (left isSameAs right) {
                        val zero = PtNumber(node.type.base, 0.0, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = zero
                        zero.parent = node.parent
                        changes++
                    }
                }
            }
        }
        true
    }
    return changes
}


// ============================================================================
// MEMORY/POINTER OPTIMIZATIONS
// ============================================================================

private fun optimizeAddressOfDereference(program: PtProgram): Int {
    var changes = 0
    walkAst(program) { node: PtNode, depth: Int ->
        if (node is PtMemoryByte) {
            // @(&x) -> x  (only if x is a byte type)
            val addressOf = node.address as? PtAddressOf
            if (addressOf != null && addressOf.identifier != null) {
                val identifier = addressOf.identifier!!
                if (identifier.type.isByteOrBool) {
                    val index = node.parent.children.indexOf(node)
                    node.parent.children[index] = identifier
                    changes++
                }
            }
        }
        true
    }
    return changes
}


private fun optimizeBooleanExpressions(program: PtProgram): Int {
    var changes = 0
    walkAst(program) { node: PtNode, depth: Int ->
        // Handle PtPrefix (not, -, ~)
        if (node is PtPrefix && node.operator == "not" && node.type.isBool) {
            val value = node.value
            // Double negation: not(not(x)) -> x
            if (value is PtPrefix && value.operator == "not" && value.type.isBool) {
                val index = node.parent.children.indexOf(node)
                node.parent.children[index] = value.value
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
                    node.parent.children[index] = replacement
                    replacement.parent = node.parent
                    changes++
                }
            }
            // not(true) -> false, not(false) -> true
            else if (value is PtBool) {
                val replacement = PtBool(!value.value, node.position)
                val index = node.parent.children.indexOf(node)
                node.parent.children[index] = replacement
                replacement.parent = node.parent
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
                        node.parent.children[index] = left
                        changes++
                    }
                    // true and x -> x
                    else if (left is PtBool && left.value) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = right
                        changes++
                    }
                    // x and false -> false
                    else if (right is PtBool && !right.value) {
                        val replacement = PtBool(false, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = replacement
                        replacement.parent = node.parent
                        changes++
                    }
                    // false and x -> false
                    else if (left is PtBool && !left.value) {
                        val replacement = PtBool(false, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = replacement
                        replacement.parent = node.parent
                        changes++
                    }
                    // x and x -> x (idempotent)
                    else if (left isSameAs right) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = left
                        changes++
                    }
                    // x and not(x) -> false (complement)
                    else if (Helpers.isNegationOf(left, right) || Helpers.isNegationOf(right, left)) {
                        val replacement = PtBool(false, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = replacement
                        replacement.parent = node.parent
                        changes++
                    }
                }
                "or" -> {
                    // x or true -> true
                    if (right is PtBool && right.value) {
                        val replacement = PtBool(true, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = replacement
                        replacement.parent = node.parent
                        changes++
                    }
                    // true or x -> true
                    else if (left is PtBool && left.value) {
                        val replacement = PtBool(true, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = replacement
                        replacement.parent = node.parent
                        changes++
                    }
                    // x or false -> x
                    else if (right is PtBool && !right.value) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = left
                        changes++
                    }
                    // false or x -> x
                    else if (left is PtBool && !left.value) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = right
                        changes++
                    }
                    // x or x -> x (idempotent)
                    else if (left isSameAs right) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = left
                        changes++
                    }
                    // x or not(x) -> true (complement)
                    else if (Helpers.isNegationOf(left, right) || Helpers.isNegationOf(right, left)) {
                        val replacement = PtBool(true, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = replacement
                        replacement.parent = node.parent
                        changes++
                    }
                }
                "xor" -> {
                    // x xor true -> not(x)
                    if (right is PtBool && right.value) {
                        val negation = PtPrefix("not", DataType.BOOL, node.position)
                        negation.add(left)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = negation
                        negation.parent = node.parent
                        changes++
                    }
                    // true xor x -> not(x)
                    else if (left is PtBool && left.value) {
                        val negation = PtPrefix("not", DataType.BOOL, node.position)
                        negation.add(right)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = negation
                        negation.parent = node.parent
                        changes++
                    }
                    // x xor false -> x
                    else if (right is PtBool && !right.value) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = left
                        changes++
                    }
                    // false xor x -> x
                    else if (left is PtBool && !left.value) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = right
                        changes++
                    }
                    // x xor x -> false (idempotent)
                    else if (left isSameAs right) {
                        val replacement = PtBool(false, node.position)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = replacement
                        replacement.parent = node.parent
                        changes++
                    }
                }
            }
        }
        true
    }
    return changes
}

private fun optimizeBitwisePrefix(program: PtProgram): Int {
    var changes = 0
    walkAst(program) { node: PtNode, depth: Int ->
        // Handle PtPrefix for bitwise NOT (~)
        if (node is PtPrefix && node.operator == "~" && node.type.isInteger) {
            val value = node.value
            // Double bitwise NOT: ~~x -> x
            if (value is PtPrefix && value.operator == "~" && value.type.isInteger) {
                val index = node.parent.children.indexOf(node)
                node.parent.children[index] = value.value
                changes++
            }
        }
        true
    }
    return changes
}

private fun optimizeBitwiseComplementBinary(program: PtProgram): Int {
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
                    node.parent.children[index] = zero
                    zero.parent = node.parent
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
                    node.parent.children[index] = allOnes
                    allOnes.parent = node.parent
                    changes++
                }
            }
        }
        true
    }
    return changes
}


private fun optimizeComparisonSimplifications(program: PtProgram): Int {
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
                    node.parent.children[index] = replacement
                    replacement.parent = node.parent
                    changes++
                }
                // x < 1 -> x <= 0 (for integers)
                else if (node.operator == "<" && rightConst == 1.0) {
                    val replacement = PtBinaryExpression("<=", node.type, node.position)
                    replacement.add(left)
                    replacement.add(PtNumber(rightType.base, 0.0, right.position))
                    val index = node.parent.children.indexOf(node)
                    node.parent.children[index] = replacement
                    replacement.parent = node.parent
                    changes++
                }
                // x <= -1 -> x < 0 (for signed integers only, NOT float)
                else if (node.operator == "<=" && rightConst == -1.0 && leftType.isSignedInteger) {
                    val replacement = PtBinaryExpression("<", node.type, node.position)
                    replacement.add(left)
                    replacement.add(PtNumber(rightType.base, 0.0, right.position))
                    val index = node.parent.children.indexOf(node)
                    node.parent.children[index] = replacement
                    replacement.parent = node.parent
                    changes++
                }
                // x > -1 -> x >= 0 (for signed integers only, NOT float)
                else if (node.operator == ">" && rightConst == -1.0 && leftType.isSignedInteger) {
                    val replacement = PtBinaryExpression(">=", node.type, node.position)
                    replacement.add(left)
                    replacement.add(PtNumber(rightType.base, 0.0, right.position))
                    val index = node.parent.children.indexOf(node)
                    node.parent.children[index] = replacement
                    replacement.parent = node.parent
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
                        node.parent.children[index] = replacement
                        replacement.parent = node.parent
                        changes++
                    }
                    "<" -> {
                        // unsigned < 0 -> false
                        val index = node.parent.children.indexOf(node)
                        val replacement = PtBool(false, node.position)
                        node.parent.children[index] = replacement
                        replacement.parent = node.parent
                        changes++
                    }
                    "<=" -> {
                        // unsigned <= 0 -> unsigned == 0
                        val replacement = PtBinaryExpression("==", node.type, node.position)
                        replacement.add(left)
                        replacement.add(right)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = replacement
                        replacement.parent = node.parent
                        changes++
                    }
                    ">" -> {
                        // unsigned > 0 -> unsigned != 0
                        val replacement = PtBinaryExpression("!=", node.type, node.position)
                        replacement.add(left)
                        replacement.add(right)
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = replacement
                        replacement.parent = node.parent
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
                            node.parent.children[index] = replacement
                            replacement.parent = node.parent
                            changes++
                        } else if (node.operator == ">=" && rightExpr.operator == "+") {
                            val replacement = PtBinaryExpression(">", node.type, node.position)
                            replacement.add(left)
                            replacement.add(rightExpr.left)
                            val index = node.parent.children.indexOf(node)
                            node.parent.children[index] = replacement
                            replacement.parent = node.parent
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


private fun optimizeExpressionRearrangement(program: PtProgram): Int {
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
                node.parent.children[index] = replacement
                replacement.parent = node.parent
                changes++
            }
            // x + (-y) -> x - y
            else if (node.operator == "+" && right is PtPrefix && right.operator == "-") {
                val replacement = PtBinaryExpression("-", node.type, node.position)
                replacement.add(left)
                replacement.add(right.value)
                val index = node.parent.children.indexOf(node)
                node.parent.children[index] = replacement
                replacement.parent = node.parent
                changes++
            }
            // x - (-y) -> x + y
            else if (node.operator == "-" && right is PtPrefix && right.operator == "-") {
                val replacement = PtBinaryExpression("+", node.type, node.position)
                replacement.add(left)
                replacement.add(right.value)
                val index = node.parent.children.indexOf(node)
                node.parent.children[index] = replacement
                replacement.parent = node.parent
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
                        node.parent.children[index] = replacement
                        replacement.parent = node.parent
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
                            node.parent.children[index] = replacement
                            replacement.parent = node.parent
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


private fun optimizeComparisonIdentities(program: PtProgram): Int {
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
                node.parent.children[index] = replacement
                replacement.parent = node.parent
                changes++
            }
        }
        true
    }
    return changes
}


private fun optimizeConditionalExpressions(program: PtProgram, errors: IErrorReporter): Int {
    var changes = 0
    walkAst(program) { node: PtNode, depth: Int ->
        if (node is PtIfElse) {
            val condition = node.condition
            val trueConst = condition.asConstValue()
            val parent = node.parent
            val index = parent.children.indexOf(node)

            // if true { A } else { B } -> A
            if (trueConst == 1.0 || (condition is PtBool && condition.value)) {
                val ifScope = node.ifScope
                // Replace if-else with the true part statements
                parent.children.removeAt(index)
                // Insert the if scope statements in reverse order to maintain order
                ifScope.children.reversed().forEach { stmt ->
                    parent.children.add(index, stmt)
                    stmt.parent = parent
                }
                changes++
            }
            // if false { A } else { B } -> B
            else if (trueConst == 0.0 || (condition is PtBool && !condition.value)) {
                val elseScope = node.elseScope
                // Replace if-else with the false part statements
                parent.children.removeAt(index)
                // Insert the else scope statements in reverse order to maintain order
                elseScope.children.reversed().forEach { stmt ->
                    parent.children.add(index, stmt)
                    stmt.parent = parent
                }
                changes++
            }
        }
        true
    }
    return changes
}


private fun optimizeDeadConditionalBranches(program: PtProgram): Int {
    var changes = 0
    walkAst(program) { node: PtNode, depth: Int ->
        if (node is PtWhen) {
            val choices = node.choices.children
            // If we have a when with only an else choice, convert to if-else or just the else block
            if (choices.size == 1 && choices[0] is PtWhenChoice && (choices[0] as PtWhenChoice).isElse) {
                val elseChoice = choices[0] as PtWhenChoice
                val index = node.parent.children.indexOf(node)
                val parent = node.parent
                parent.children.removeAt(index)
                // Insert the else part statements
                elseChoice.statements.children.reversed().forEach { stmt ->
                    parent.children.add(index, stmt)
                    stmt.parent = parent
                }
                changes++
            }
        }
        true
    }
    return changes
}


private fun optimizeStrengthReduction(program: PtProgram, options: CompilationOptions): Int {
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
                    node.parent.children[index] = shift
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
                    node.parent.children[index] = andExpr
                    andExpr.parent = node.parent
                    changes++
                }
                // x % 1 -> 0
                else if (rightConst == 1.0) {
                    val zero = PtNumber(node.type.base, 0.0, node.position)
                    val index = node.parent.children.indexOf(node)
                    node.parent.children[index] = zero
                    zero.parent = node.parent
                    changes++
                }
            }
        }
        true
    }
    return changes
}


// ============================================================================
// SPECIALIZED OPTIMIZATIONS
// ============================================================================

private fun optimizeFloatComparesToZero(program: PtProgram): Int {
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
                replacement.parent = node.parent
                val index = node.parent.children.indexOf(node)
                node.parent.children[index] = replacement
                changes++
            }
        }
        true
    }
    return changes
}


private fun optimizeLsbMsbOnStructfields(program: PtProgram): Int {
    var changes = 0
    walkAst(program) { node: PtNode, depth: Int ->
        if (node is PtFunctionCall && node.builtin && (node.name=="msb" || node.name=="lsb")) {
            if(node.args[0] is PtPointerDeref) {
                if(!node.args[0].type.isByteOrBool) {
                    // msb(struct.field) -->  @(&struct.field+1)
                    // lsb(struct.field) -->  @(&struct.field)
                    val addressOfDeref = PtAddressOf(DataType.UWORD, false, node.args[0].position)
                    addressOfDeref.add(node.args[0])
                    val address: PtExpression
                    if(node.name=="msb") {
                        address = PtBinaryExpression("+", addressOfDeref.type, addressOfDeref.position)
                        address.add(addressOfDeref)
                        address.add(PtNumber(BaseDataType.UWORD, 1.0, addressOfDeref.position))
                    } else {
                        address = addressOfDeref
                    }
                    val memread = PtMemoryByte(address.position)
                    memread.add(address)
                    memread.parent = node.parent
                    val index = node.parent.children.indexOf(node)
                    node.parent.children[index] = memread
                    changes++
                }
            }
        }
        true
    }

    return changes
}


private fun optimizeSingleWhens(program: PtProgram, errors: IErrorReporter): Int {
    var changes = 0

    walkAst(program) { node: PtNode, depth: Int ->
        if(node is PtWhen && node.choices.children.size==2) {
            val choice1 = node.choices.children[0] as PtWhenChoice
            val choice2 = node.choices.children[1] as PtWhenChoice
            if(choice1.isElse && choice2.values.children.size==1 || choice2.isElse && choice1.values.children.size==1) {
                errors.info("when can be simplified into an if-else", node.position)
                val truescope: PtNodeGroup
                val elsescope: PtNodeGroup
                val comparisonValue : PtNumber
                if(choice1.isElse) {
                    truescope = choice2.statements
                    elsescope = choice1.statements
                    comparisonValue = choice2.values.children.single() as PtNumber
                } else {
                    truescope = choice1.statements
                    elsescope = choice2.statements
                    comparisonValue = choice1.values.children.single() as PtNumber
                }
                val ifelse = PtIfElse(node.position)
                val condition = PtBinaryExpression("==", DataType.BOOL, node.position)
                condition.add(node.value)
                condition.add(comparisonValue)
                ifelse.add(condition)
                ifelse.add(truescope)
                ifelse.add(elsescope)
                ifelse.parent = node.parent
                val index = node.parent.children.indexOf(node)
                node.parent.children[index] = ifelse
                changes++
            }
        }
        true
    }

    return changes
}


private fun optimizeSgnComparisons(program: PtProgram, errors: IErrorReporter): Int {
    // NOTE: do *not* optimize away sgn() comparisons on floats! Those ARE more efficient than the normal compares!
    var changes = 0

    walkAst(program) { node: PtNode, depth: Int ->
        if(node is PtFunctionCall && node.builtin && node.name=="sgn" && node.args[0].type.isInteger) {
            val comparison = node.parent as? PtBinaryExpression
            if(comparison!=null && comparison.right.asConstInteger()==0 && comparison.operator in ComparisonOperators) {
                //  sgn(integer) >= 0   -> just use   integer >= 0
                val replacement = PtBinaryExpression(comparison.operator, DataType.BOOL, comparison.position)
                replacement.add(node.args[0])
                replacement.add(PtNumber(node.args[0].type.base, 0.0, comparison.position))
                replacement.parent = comparison.parent
                val index = comparison.parent.children.indexOf(comparison)
                comparison.parent.children[index] = replacement
                changes++
            }
        }
        true
    }

    return changes
}

// ============================================================================
// VARIABLE OPTIMIZATIONS (Single-Pass)
// ============================================================================

private fun optimizeRedundantVarInits(program: PtProgram): Int {
    fun statementsFromVarInitToFirstAssignment(varInit: PtAssignment, variable: PtIdentifier, parent: PtNode): Pair<Int, Int> {
        val varInitIndex = parent.children.indexOf(varInit)
        for (stmt in parent.children.asSequence().withIndex().drop(varInitIndex)) {
            (stmt.value as? PtAssignment)?.let { assignment ->
                if(!assignment.isVarInitializer) {
                    if (assignment.multiTarget) {
                        assignment.children.dropLast(1).forEach { target ->
                            target as PtAssignTarget
                            if(!target.void && variable.same(target.identifier))
                                return varInitIndex to stmt.index
                        }
                    } else {
                        if (!assignment.target.void && variable.same(assignment.target.identifier))
                            return varInitIndex to stmt.index
                    }
                }
            }
        }
        return -1 to -1
    }

    val removeInitializations = mutableListOf<Pair<PtNode, PtAssignment>>()

    fun potentiallyOptimize(identifier: PtIdentifier, parent: PtNode, initializerIndex: Int, assignIndex: Int) {
        if (assignIndex>initializerIndex) {
            val inbetween = parent.children.subList(initializerIndex+1, assignIndex)
            if(!inbetween.any { stmt -> referencesIdentifier(stmt, identifier) }) {
                // var initializer is redundant, it will be overwritten by an assignment later. remove the initializer
                removeInitializations.add(parent to parent.children[initializerIndex] as PtAssignment)
            }
        }
    }

    walkAst(program) { node: PtNode, depth: Int ->
        if(node is PtAssignment && node.isVarInitializer) {
            if(node.multiTarget) {
                node.children.dropLast(1).forEach { target ->
                    target as PtAssignTarget
                    if(!target.void) {
                        target.identifier?.let { identifier ->
                            val statements = statementsFromVarInitToFirstAssignment(node, identifier, node.parent)
                            if(statements.first>=0)
                                potentiallyOptimize(identifier, node.parent, statements.first, statements.second)
                        }
                    }
                }
            } else {
                node.target.identifier?.let { identifier ->
                    val statements = statementsFromVarInitToFirstAssignment(node, identifier, node.parent)
                    if(statements.first>=0)
                        potentiallyOptimize(identifier, node.parent, statements.first, statements.second)
                }
            }
        }
        true
    }

    removeInitializations.forEach { (parent, varInit) ->
        parent.children.remove(varInit)
    }

    return removeInitializations.size
}

// ============================================================================
// HELPER FUNCTIONS (encapsulated in object for organization)
// ============================================================================

/**
 * Helper functions used by various optimization passes.
 * These are grouped together for better code organization.
 */
private object Helpers {
    /**
     * Check if an identifier matches a CX16 virtual register.
     * Used by optimizeAssignTargets to detect when a function returns to the same register.
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
     * Used by optimizeExpressionRearrangement for factoring common terms.
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
 * Used by optimizeRedundantVarInits to determine if a variable initialization
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

