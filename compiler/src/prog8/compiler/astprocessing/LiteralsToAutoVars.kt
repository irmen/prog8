package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.expressions.ArrayLiteralValue
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.VarDecl
import prog8.ast.statements.WhenChoice
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification


internal class LiteralsToAutoVars(private val program: Program) : AstWalker() {

    override fun after(string: StringLiteralValue, parent: Node): Iterable<IAstModification> {
        if(string.parent !is VarDecl && string.parent !is WhenChoice) {
            // replace the literal string by an identifier reference to the interned string
            val scopedName = program.internString(string)
            val identifier = IdentifierReference(scopedName, string.position)
            return listOf(IAstModification.ReplaceNode(string, identifier, parent))
        }
        return noModifications
    }

    override fun after(array: ArrayLiteralValue, parent: Node): Iterable<IAstModification> {
        val vardecl = array.parent as? VarDecl
        if(vardecl!=null) {
            // adjust the datatype of the array (to an educated guess)
            val arrayDt = array.type
            if(arrayDt isnot vardecl.datatype) {
                val cast = array.cast(vardecl.datatype)
                if (cast != null && cast !== array)
                    return listOf(IAstModification.ReplaceNode(vardecl.value!!, cast, vardecl))
            }
        } else {
            val arrayDt = array.guessDatatype(program)
            if(arrayDt.isKnown) {
                // this array literal is part of an expression, turn it into an identifier reference
                val litval2 = array.cast(arrayDt.getOr(DataType.UNDEFINED))
                if(litval2!=null) {
                    val vardecl2 = VarDecl.createAuto(litval2)
                    val identifier = IdentifierReference(listOf(vardecl2.name), vardecl2.position)
                    return listOf(
                            IAstModification.ReplaceNode(array, identifier, parent),
                            IAstModification.InsertFirst(vardecl2, array.definingScope)
                    )
                }
            }
        }
        return noModifications
    }
}
