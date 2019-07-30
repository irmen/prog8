package prog8.compiler.target.c64.codegen2

import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.Expression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.processing.IAstModifyingVisitor
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.Statement
import prog8.ast.statements.VarDecl

class AnonymousScopeVarsCleanup(val program: Program): IAstModifyingVisitor {
    companion object {
        fun moveVarsToSubroutine(programAst: Program) {
            val cleanup = AnonymousScopeVarsCleanup(programAst)
            cleanup.visit(programAst)

            for((scope, decls) in cleanup.varsToMove) {
                decls.forEach { scope.remove(it) }
                val sub = scope.definingSubroutine()!!
                val existingVariables = sub.statements.filterIsInstance<VarDecl>().map { it.name }.toSet()
                sub.statements.addAll(0, decls)
                decls.forEach {
                    it.parent=sub
                    if(it.name in existingVariables) {
                        throw FatalAstException("variable ${it.name} already exists in ${sub.name}")
                    }
                }
            }
        }
    }

    private val varsToMove: MutableMap<AnonymousScope, List<VarDecl>> = mutableMapOf()

    override fun visit(scope: AnonymousScope): Statement {
        val scope2 = super.visit(scope) as AnonymousScope
        val vardecls = scope2.statements.filterIsInstance<VarDecl>()
        varsToMove[scope2] = vardecls
        return scope2
    }

    private fun nameprefix(scope: AnonymousScope) = scope.name.replace("<", "").replace(">", "").replace("-", "") + "_"

    override fun visit(decl: VarDecl): Statement {
        val decl2 = super.visit(decl) as VarDecl
        val scope = decl2.definingScope()
        if(scope is AnonymousScope) {
            return decl2.withPrefixedName(nameprefix(scope))
        }
        return decl2
    }

    override fun visit(identifier: IdentifierReference): Expression {
        val ident = super.visit(identifier)
        if(ident !is IdentifierReference)
            return ident

        val scope = ident.definingScope() as? AnonymousScope ?: return ident
        val vardecl = ident.targetVarDecl(program.namespace)
        return if(vardecl!=null && vardecl.definingScope() == ident.definingScope()) {
            // prefix the variable name reference that is defined inside the anon scope
            ident.withPrefixedName(nameprefix(scope))
        } else {
            ident
        }
    }
}

