package prog8.compiler.target.c64.codegen2

import prog8.ast.Program
import prog8.ast.processing.IAstVisitor
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.VarDecl

class AnonymousScopeCleanup(val program: Program): IAstVisitor {
    companion object {
        fun moveVarsFromAnonymousScopesToSubroutines(programAst: Program) {
            val cleanup = AnonymousScopeCleanup(programAst)
            cleanup.visit(programAst)

            for((scope, decls) in cleanup.varsToMove) {
                decls.forEach { scope.remove(it) }
                val sub = scope.definingSubroutine()!!
                sub.statements.addAll(0, decls)
                decls.forEach { it.parent=sub }
            }
        }
    }

    private val varsToMove: MutableMap<AnonymousScope, List<VarDecl>> = mutableMapOf()

    override fun visit(scope: AnonymousScope) {
        val vardecls = scope.statements.filterIsInstance<VarDecl>()
        varsToMove[scope] = vardecls
        super.visit(scope)
    }
}

