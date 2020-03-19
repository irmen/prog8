package prog8.optimizer

import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.processing.IAstVisitor
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.NopStatement
import prog8.ast.statements.Statement

internal class FlattenAnonymousScopesAndNopRemover: IAstVisitor {
    private var scopesToFlatten = mutableListOf<INameScope>()
    private val nopStatements = mutableListOf<NopStatement>()

    override fun visit(program: Program) {
        super.visit(program)
        for(scope in scopesToFlatten.reversed()) {
            val namescope = scope.parent as INameScope
            val idx = namescope.statements.indexOf(scope as Statement)
            if(idx>=0) {
                val nop = NopStatement.insteadOf(namescope.statements[idx])
                nop.parent = namescope as Node
                namescope.statements[idx] = nop
                namescope.statements.addAll(idx, scope.statements)
                scope.statements.forEach { it.parent = namescope }
                visit(nop)
            }
        }

        this.nopStatements.forEach {
            it.definingScope().remove(it)
        }
    }

    override fun visit(scope: AnonymousScope) {
        if(scope.parent is INameScope) {
            scopesToFlatten.add(scope)  // get rid of the anonymous scope
        }

        return super.visit(scope)
    }

    override fun visit(nopStatement: NopStatement) {
        nopStatements.add(nopStatement)
    }
}
