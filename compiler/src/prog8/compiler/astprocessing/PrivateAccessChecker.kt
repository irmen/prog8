package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.ParentSentinel
import prog8.ast.Program
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.core.IErrorReporter

internal class PrivateAccessChecker(
    private val program: Program,
    private val errors: IErrorReporter
) : IAstVisitor {

    override fun visit(identifier: IdentifierReference) {
        val parentExpr = identifier.parent as? BinaryExpression
        if (parentExpr?.operator == ".") {
            return  // identifiers will be checked over at the BinaryExpression itself
        }

        val stmt = identifier.targetStatement(program.builtinFunctions)
        if (stmt != null) {
            val privateError = when (stmt) {
                is VarDecl -> {
                    if (stmt.isPrivate && !isAccessWithinSameBlock(identifier, stmt.definingBlock))
                        "private variable '${stmt.scopedName.joinToString(".")}'"
                    else null
                }
                is Subroutine -> {
                    if (stmt.isPrivate && !isAccessWithinSameBlock(identifier, stmt.definingBlock))
                        "private subroutine '${stmt.scopedName.joinToString(".")}'"
                    else null
                }
                is StructDecl -> {
                    if (stmt.isPrivate && !isAccessWithinSameBlock(identifier, stmt.definingBlock))
                        "private struct '${stmt.scopedName.joinToString(".")}'"
                    else null
                }
                is Enumeration -> {
                    if (stmt.isPrivate && !isAccessWithinSameBlock(identifier, stmt.definingBlock))
                        "private enum '${stmt.scopedName.joinToString(".")}'"
                    else null
                }
                else -> null
            }
            if (privateError != null) {
                errors.err("cannot access $privateError from outside its block", identifier.position)
            }
        }

        super.visit(identifier)
    }

    private fun isAccessWithinSameBlock(identifier: IdentifierReference, definingBlock: Block): Boolean {
        var node: Node = identifier
        while (node !is Block) {
            node = node.parent
            if (node is ParentSentinel)
                return false
        }
        return node === definingBlock
    }
}
