package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.ArrayIndexedExpression
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification


internal class AstVariousTransforms(private val program: Program) : AstWalker() {

    // TODO can this be integrated in another walker

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

    // TODO move this / remove this.  Needed here atm otherwise a replacement error occurs later in StatementReorderer
    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        return replacePointerVarIndexWithMemreadOrMemwrite(program, arrayIndexedExpression, parent)
    }
}
