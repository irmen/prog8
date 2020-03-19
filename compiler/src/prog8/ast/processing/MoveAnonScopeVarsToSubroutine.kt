package prog8.ast.processing

import prog8.ast.Program
import prog8.ast.base.ErrorReporter
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.Statement
import prog8.ast.statements.VarDecl

class MoveAnonScopeVarsToSubroutine(private val errors: ErrorReporter): IAstModifyingVisitor {
    private val varsToMove: MutableMap<AnonymousScope, List<VarDecl>> = mutableMapOf()

    override fun visit(program: Program) {
        varsToMove.clear()
        super.visit(program)
        for((scope, decls) in varsToMove) {
            val sub = scope.definingSubroutine()!!
            val existingVariables = sub.statements.filterIsInstance<VarDecl>().associateBy { it.name }
            var conflicts = false
            decls.forEach {
                val existing = existingVariables[it.name]
                if (existing!=null) {
                    errors.err("variable ${it.name} already defined in subroutine ${sub.name} at ${existing.position}", it.position)
                    conflicts = true
                }
            }
            if (!conflicts) {
                decls.forEach { scope.remove(it) }
                sub.statements.addAll(0, decls)
                decls.forEach { it.parent = sub }
            }
        }
    }

    override fun visit(scope: AnonymousScope): Statement {
        val scope2 = super.visit(scope) as AnonymousScope
        val vardecls = scope2.statements.filterIsInstance<VarDecl>()
        varsToMove[scope2] = vardecls
        return scope2
    }
}

