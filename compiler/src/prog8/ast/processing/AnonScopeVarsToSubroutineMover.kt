package prog8.ast.processing

import prog8.ast.Node
import prog8.ast.base.ErrorReporter
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl


class AnonScopeVarsToSubroutineMover(val errors: ErrorReporter): AstWalker() {
    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        val decls = scope.statements.filterIsInstance<VarDecl>()
        val sub = scope.definingSubroutine()
        if(sub!=null) {
            val existingVariables = sub.statements.filterIsInstance<VarDecl>().associateBy { it.name }
            var conflicts = false
            decls.forEach {
                val existing = existingVariables[it.name]
                if (existing!=null) {
                    errors.err("variable ${it.name} already defined in subroutine ${sub.name} at ${existing.position}", it.position)
                    conflicts = true
                }
            }
            if(!conflicts)
                return listOf(MoveVardecls(decls, scope, sub))
        }
        return emptyList()
    }

    private class MoveVardecls(val decls: Collection<VarDecl>,
                               val scope: AnonymousScope,
                               val sub: Subroutine) : IAstModification {
        override fun perform() {
            decls.forEach { scope.remove(it) }
            sub.statements.addAll(0, decls)
            decls.forEach { it.parent = sub }
        }
    }
}
