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
