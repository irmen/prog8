package prog8.compiler.target.c64.codegen2

import prog8.ast.Program
import prog8.ast.base.AstException
import prog8.ast.base.NameError
import prog8.ast.processing.IAstModifyingVisitor
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.Statement
import prog8.ast.statements.VarDecl

class AnonymousScopeVarsCleanup(val program: Program): IAstModifyingVisitor {
    private val checkResult: MutableList<AstException> = mutableListOf()
    private val varsToMove: MutableMap<AnonymousScope, List<VarDecl>> = mutableMapOf()

    fun result(): List<AstException> {
        return checkResult
    }

    override fun visit(program: Program) {
        varsToMove.clear()
        super.visit(program)
        for((scope, decls) in varsToMove) {
            val sub = scope.definingSubroutine()!!
            val existingVariables = sub.statements.filterIsInstance<VarDecl>().associate { it.name to it }
            var conflicts = false
            decls.forEach {
                val existing = existingVariables[it.name]
                if (existing!=null) {
                    checkResult.add(NameError("variable ${it.name} already defined in subroutine ${sub.name} at ${existing.position}", it.position))
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

