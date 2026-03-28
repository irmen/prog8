package prog8.code.ast

/**
 * Walk an AST tree depth-first, calling [act] for each node.
 *
 * @param root The root node to start walking from
 * @param act Callback for each node. Return true to continue traversing children,
 *            false to prune the subtree at this node.
 */
fun walkAst(root: PtNode, act: (node: PtNode, depth: Int) -> Boolean) {
    fun recurse(node: PtNode, depth: Int) {
        if (act(node, depth)) {
            node.children.forEach { recurse(it, depth + 1) }
        }
    }
    recurse(root, 0)
}

/**
 * NOTE: AstRewriter class was removed from this codebase.
 *
 * **What AstRewriter was:** A base class for AST transformation passes that automatically
 * tracked parent nodes while rebuilding the tree. It created new copies of nodes during
 * transformations, maintaining parent pointers automatically.
 *
 * **Why it was removed:** The SimpleAST optimizer refactoring initially used AstRewriter
 * to modernize the optimization passes. However, the migration was incomplete, leading to
 * critical problems:
 *
 * 1. **Mixed paradigms**: Some code used AstRewriter (tree-copying) while other code
 *    used `walkAst()` with in-place modifications. The code generator's `prefixSymbols()`
 *    function modifies node names in-place on the ORIGINAL tree, but AstRewriter produces
 *    a NEW tree with copied nodes. This caused modifications to be lost or applied twice.
 *
 * 2. **Double-prefixing bug**: When the optimizer created copies of blocks that had
 *    already been prefixed, the code generator would prefix them again, resulting in
 *    symbols like `p8b_p8b_fileselector` instead of `p8b_fileselector`.
 *
 * 3. **Incomplete migration**: Not all optimization passes were converted to use
 *    AstRewriter, and the symbol table was still built from the original tree while
 *    code generation expected the transformed tree.
 *
 * **Could AstRewriter work with a proper implementation?**
 *
 * Yes, potentially. The issues weren't fundamental flaws in the tree-rewriting approach,
 * but rather incomplete adoption. A working implementation would require:
 *
 * 1. **Full commitment**: ALL transformations must go through AstRewriter - no mixing
 *    with in-place modifications via `walkAst()`.
 *
 * 2. **Single source of truth**: The result tree from AstRewriter must be used for ALL
 *    subsequent phases (symbol table rebuild, code generation, etc.).
 *
 * 3. **No in-place modifications after rewriting**: Functions like `prefixSymbols()`
 *    would need to operate on the NEW tree, or be rewritten to use AstRewriter too.
 *
 * 4. **Clear phase boundaries**:
 *    ```
 *    parse → optimize (AstRewriter) → rebuild ST → prefixSymbols (on new tree) → codegen
 *    ```
 *
 * **Alternative approaches that could work:**
 *
 * 1. **Hybrid with clear boundaries**: Use AstRewriter for pure optimizations, then
 *    discard the original tree entirely. Ensure all later phases use only the new tree.
 *
 * 2. **Parent-tracking helpers**: Add utility functions to `walkAst()` that help with
 *    common parent-tracking patterns without full tree copying:
 *    ```kotlin
 *    fun walkAstWithParent(root: PtNode, act: (node: PtNode, parent: PtNode?) -> Boolean)
 *    ```
 *
 * 3. **Lens-based transformations**: Use functional lenses for focused updates that
 *    automatically rebuild parent chains. More complex but very safe.
 *
 * 4. **Mutable parent with invalidation**: Keep parent pointers mutable but add a
 *    `parentInvalidated` flag that forces rebuild before codegen.
 *
 * **Why we kept it simple:**
 *
 * For the current codebase, direct tree manipulation with `walkAst()` is sufficient
 * because:
 * - Most optimizations are simple pattern replacements
 * - The SimpleAST is already a flattened representation
 * - The overhead of full tree copying isn't justified for the optimization gains
 * - Manual parent tracking, while error-prone, is manageable with careful code review
 *
 * See git history for the removed AstRewriter.kt file and related optimizer refactoring.
 */
