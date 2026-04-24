package prog8.code.optimize

import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.IErrorReporter
import prog8.code.core.Position

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
 *
 * **Optimizer organization:**
 * - [ExpressionOptimizers] - Algebraic identities, rearrangement, strength reduction
 * - [ComparisonOptimizers] - Comparison simplifications and identities
 * - [BooleanOptimizers] - Boolean logic and bitwise operations
 * - [ControlFlowOptimizers] - If/else, when statements
 * - [MemoryOptimizers] - Address-of, struct field access
 * - [VariableOptimizers] - Assignment targets, redundant initializations
 *
 * **Implementation approach:**
 *
 * The optimizer functions use direct tree manipulation with `walkAst()` rather than a
 * tree-rewriting base class. This was a deliberate design decision after attempting
 * to use an `AstRewriter` class (see AstWalker.kt for details).
 *
 * Key lessons learned:
 *
 * 1. **In-place modifications are common**: The code generator's `prefixSymbols()` function
 *    modifies node names in-place. Tree-rewriting approaches that create copies can conflict
 *    with this, causing bugs like double-prefixing of symbol names.
 *
 * 2. **Pattern matching needs original structure**: Some optimizations (like pointer arithmetic)
 *    need to see specific AST patterns including typecasts. Removing these patterns too early
 *    (e.g., in a postprocess phase before optimization) prevents the optimizer from matching
 *    and improving the code.
 *
 * 3. **Order matters**: The compilation pipeline has a specific order that must be maintained:
 *    - postprocessSimplifiedAst() - subtype resolution only (no typecast removal)
 *    - optimizeSimplifiedAst() - all optimizations (needs to see original patterns)
 *    - removeRedundantPointerCasts() - cleanup typecasts (after optimizer is done)
 *    - code generation - produces final assembly
 *
 * See Compiler.kt for the full pipeline with detailed comments.
 */

/**
 * Context object passed to all optimization functions.
 * Consolidates the common parameters to reduce boilerplate.
 */
private data class OptimizerContext(
    val st: SymbolTable,
    val options: CompilationOptions,
    val errors: IErrorReporter
)

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
 * NOTE: some optimizations here may seem redundant because they're already done in a Compiler AST optimizer step,
 * but they're done again here to catch cases where rewriting the AST after optimization may have introduced optimizable changes again.
 * 
 * Maximum iteration limit prevents infinite loops from buggy optimizations.
 */
private fun runFixpointOptimizations(program: PtProgram, ctx: OptimizerContext) {
    val MAX_FIXPOINT_ITERATIONS = 50
    var iteration = 0
    
    while (ctx.errors.noErrors() &&
        VariableOptimizers.optimizeAssignTargets(program, ctx.st)
        + ExpressionOptimizers.optimizeAlgebraicIdentities(program)
        + ExpressionOptimizers.optimizeExpressionRearrangement(program)
        + BooleanOptimizers.optimizeBitwisePrefix(program)
        + MemoryOptimizers.optimizeAddressOfDereference(program)
        + MemoryOptimizers.optimizeLsbMsbOnStructfields(program)
        + ComparisonOptimizers.optimizeFloatComparesToZero(program)
        + ComparisonOptimizers.optimizeSgnComparisons(program, ctx.errors)
        + ComparisonOptimizers.optimizeComparisonSimplifications(program)
        + ComparisonOptimizers.optimizeComparisonIdentities(program)
        + BooleanOptimizers.optimizeBooleanExpressions(program)
        + BooleanOptimizers.optimizeBitwiseComplementBinary(program)
        + ExpressionOptimizers.optimizeBinaryExpressions(program, ctx.options)
        + ControlFlowOptimizers.optimizeSingleWhens(program, ctx.errors)
        + ControlFlowOptimizers.optimizeConditionalExpressions(program, ctx.errors)
        + ControlFlowOptimizers.optimizeDeadConditionalBranches(program) > 0) {
        iteration++
        if (iteration >= MAX_FIXPOINT_ITERATIONS) {
            ctx.errors.warn("Optimization hit iteration limit ($MAX_FIXPOINT_ITERATIONS), may be incomplete", Position.DUMMY)
            break
        }
    }
}

/**
 * Runs optimizations that only need to execute once.
 * These don't create opportunities for other optimizations, so no fixpoint loop needed.
 */
private fun runSinglePassOptimizations(program: PtProgram, ctx: OptimizerContext) {
    // Variable optimizations
    VariableOptimizers.optimizeRedundantVarInits(program)

    // Strength reduction (x/2^n→x>>n, x%2^n→x&(2^n-1)) doesn't create opportunities for other opts
    ExpressionOptimizers.optimizeStrengthReduction(program, ctx.options)
}
