package prog8.compiler.astprocessing

import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.ParentSentinel
import prog8.ast.Program
import prog8.ast.findParentNode
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
                is VarDecl -> checkAccess(identifier, stmt.visibility, stmt.definingBlock)
                is Subroutine -> checkAccess(identifier, stmt.visibility, stmt.definingBlock)
                is StructDecl -> checkAccess(identifier, stmt.visibility, stmt.definingBlock)
                is Enumeration -> checkAccess(identifier, stmt.visibility, stmt.definingBlock)
                else -> null
            }
            if (privateError != null) {
                errors.err("cannot access $privateError from outside its block", identifier.position)
            }
        }

        super.visit(identifier)
    }

    private fun checkAccess(identifier: IdentifierReference, visibility: Visibility?, definingBlock: Block): String? {
        val definingBlockHasOption = "private_symbols" in definingBlock.options()
        val moduleHasOption = if (!definingBlockHasOption) {
            val module = findParentNode<Module>(definingBlock)
            module != null && "private_symbols" in module.options()
        } else false

        val isPrivateSymbolsMode = definingBlockHasOption || moduleHasOption

        return if (isPrivateSymbolsMode) {
            // private_symbols mode: accessible if PUBLIC, or within same block
            if (visibility == Visibility.PUBLIC || isAccessWithinSameBlock(identifier, definingBlock))
                null
            else {
                val kind = getSymbolKind(identifier)
                "$kind '${identifier.nameInSource.joinToString(".")}' is not public"
            }
        } else {
            // default mode: accessible if NOT PRIVATE, or within same block
            if (visibility != Visibility.PRIVATE || isAccessWithinSameBlock(identifier, definingBlock))
                null
            else {
                val kind = getSymbolKind(identifier)
                "private $kind '${identifier.nameInSource.joinToString(".")}'"
            }
        }
    }

    private fun getSymbolKind(identifier: IdentifierReference): String {
        return when (identifier.targetStatement(program.builtinFunctions)) {
            is VarDecl -> "variable"
            is Subroutine -> "subroutine"
            is StructDecl -> "struct"
            is Enumeration -> "enum"
            else -> "symbol"
        }
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
