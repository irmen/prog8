package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.ArrayIndexedExpression
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.DirectMemoryRead
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.DirectMemoryWrite
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.DataType


internal class AstVariousTransforms(private val program: Program) : AstWalker() {

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        // For non-kernal subroutines and non-asm parameters:
        // inject subroutine params as local variables (if they're not there yet).
        val symbolsInSub = subroutine.allDefinedSymbols
        val namesInSub = symbolsInSub.map{ it.first }.toSet()
        if(subroutine.asmAddress==null) {
            if(!subroutine.isAsmSubroutine && subroutine.parameters.isNotEmpty()) {
                val vars = subroutine.statements.asSequence().filterIsInstance<VarDecl>().map { it.name }.toSet()
                if(!vars.containsAll(subroutine.parameters.map{it.name})) {
                    return subroutine.parameters
                            .filter { it.name !in namesInSub }
                            .map {
                                val vardecl = VarDecl.fromParameter(it)
                                IAstModification.InsertFirst(vardecl, subroutine)
                            }
                }
            }
        }

        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        return replacePointerVarIndexWithMemreadOrMemwrite(program, arrayIndexedExpression, parent)
    }
}



internal fun replacePointerVarIndexWithMemreadOrMemwrite(program: Program, arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
    val arrayVar = arrayIndexedExpression.arrayvar.targetVarDecl(program)
    if(arrayVar!=null && arrayVar.datatype == DataType.UWORD) {
        // rewrite   pointervar[index]  into  @(pointervar+index)
        val indexer = arrayIndexedExpression.indexer
        val add = BinaryExpression(arrayIndexedExpression.arrayvar.copy(), "+", indexer.indexExpr, arrayIndexedExpression.position)
        return if(parent is AssignTarget) {
            // we're part of the target of an assignment, we have to actually change the assign target itself
            val memwrite = DirectMemoryWrite(add, arrayIndexedExpression.position)
            val newtarget = AssignTarget(null, null, memwrite, arrayIndexedExpression.position)
            listOf(IAstModification.ReplaceNode(parent, newtarget, parent.parent))
        } else {
            val memread = DirectMemoryRead(add, arrayIndexedExpression.position)
            listOf(IAstModification.ReplaceNode(arrayIndexedExpression, memread, parent))
        }
    }

    return emptyList()
}
