package prog8.ast.processing

import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.TypecastExpression
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.NopStatement


internal class VariousCleanups: AstWalker() {
    private val noModifications = emptyList<IAstModification>()

    override fun before(nopStatement: NopStatement, parent: Node): Iterable<IAstModification> {
        return listOf(IAstModification.Remove(nopStatement, parent))
    }

    override fun before(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        return if(parent is INameScope)
            listOf(ScopeFlatten(scope, parent as INameScope))
        else
            noModifications
    }

    class ScopeFlatten(val scope: AnonymousScope, val into: INameScope) : IAstModification {
        override fun perform() {
            val idx = into.statements.indexOf(scope)
            if(idx>=0) {
                into.statements.addAll(idx+1, scope.statements)
                into.statements.remove(scope)
            }
        }
    }

    override fun before(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        if(typecast.expression is NumericLiteralValue) {
            val value = (typecast.expression as NumericLiteralValue).cast(typecast.type)
            if(value.isValid)
                return listOf(IAstModification.ReplaceNode(typecast, value.valueOrZero(), parent))
        }

        return noModifications
    }
}
