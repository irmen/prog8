package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.NumericDatatypes
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.RangeExpr
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.ast.statements.VarDecl
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification


class AstPreprocessor(val program: Program) : AstWalker() {

    override fun after(range: RangeExpr, parent: Node): Iterable<IAstModification> {
        // has to be done before the constant folding, otherwise certain checks there will fail on invalid range sizes
        val modifications = mutableListOf<IAstModification>()
        if(range.from !is NumericLiteralValue) {
            val constval = range.from.constValue(program)
            if(constval!=null)
                modifications += IAstModification.ReplaceNode(range.from, constval, range)
        }
        if(range.to !is NumericLiteralValue) {
            val constval = range.to.constValue(program)
            if(constval!=null)
                modifications += IAstModification.ReplaceNode(range.to, constval, range)
        }
        if(range.step !is NumericLiteralValue) {
            val constval = range.step.constValue(program)
            if(constval!=null)
                modifications += IAstModification.ReplaceNode(range.step, constval, range)
        }
        return modifications
    }

    override fun before(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {

        // move vardecls in Anonymous scope up to the containing subroutine
        // and add initialization assignment in its place if needed
        val vars = scope.statements.filterIsInstance<VarDecl>()
        val parentscope = scope.definingScope
        if(vars.any() && parentscope !== parent) {
            val movements = mutableListOf<IAstModification>()
            val replacements = mutableListOf<IAstModification>()

            for(decl in vars) {
                if(decl.type != VarDeclType.VAR) {
                    movements.add(IAstModification.InsertFirst(decl, parentscope))
                    replacements.add(IAstModification.Remove(decl, scope))
                } else {
                    if(decl.value!=null && decl.datatype in NumericDatatypes) {
                        val target = AssignTarget(IdentifierReference(listOf(decl.name), decl.position), null, null, decl.position)
                        val assign = Assignment(target, decl.value!!, decl.position)
                        replacements.add(IAstModification.ReplaceNode(decl, assign, scope))
                        decl.value = null
                        decl.allowInitializeWithZero = false
                    } else {
                        replacements.add(IAstModification.Remove(decl, scope))
                    }
                    movements.add(IAstModification.InsertFirst(decl, parentscope))
                }
            }
            return movements + replacements
        }
        return noModifications
    }
}
