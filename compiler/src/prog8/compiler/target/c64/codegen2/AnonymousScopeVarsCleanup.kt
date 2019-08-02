package prog8.compiler.target.c64.codegen2

import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.Expression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.processing.IAstModifyingVisitor
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.Statement
import prog8.ast.statements.VarDecl
import java.util.*

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

    private val currentAnonScope: Stack<AnonymousScope> = Stack()

    override fun visit(scope: AnonymousScope): Statement {
        currentAnonScope.push(scope)
        val scope2 = super.visit(scope) as AnonymousScope
        currentAnonScope.pop()
        val vardecls = scope2.statements.filterIsInstance<VarDecl>()
        varsToMove[scope2] = vardecls
        return scope2
    }

    private fun nameprefix(scope: AnonymousScope) = scope.name.replace("<", "").replace(">", "").replace("-", "") + "_"

    override fun visit(decl: VarDecl): Statement {
        val decl2 = super.visit(decl) as VarDecl
        if(currentAnonScope.isEmpty())
            return decl2
        return decl2.withPrefixedName(nameprefix(currentAnonScope.peek()))
    }

    override fun visit(identifier: IdentifierReference): Expression {
        val ident = super.visit(identifier)
        if(ident !is IdentifierReference)
            return ident
        if(currentAnonScope.isEmpty())
            return ident
        val vardecl = ident.targetVarDecl(program.namespace)
        return if(vardecl!=null && vardecl.definingScope() === ident.definingScope()) {
            // prefix the variable name reference that is defined inside the anon scope
            ident.withPrefixedName(nameprefix(currentAnonScope.peek()))
        } else {
            ident
        }
    }
    /*
; @todo FIX Symbol lookup over anon scopes
;    sub start()  {
;        for ubyte i in 0 to 10 {
;            word rz = 4
;            if rz >= 1 {
;                word persp = rz+1
;            }
;        }
;    }
     */
}

