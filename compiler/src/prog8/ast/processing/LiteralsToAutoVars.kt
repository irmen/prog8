package prog8.ast.processing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*


internal class LiteralsToAutoVars(private val program: Program) : AstWalker() {
    private val noModifications = emptyList<IAstModification>()

    override fun after(string: StringLiteralValue, parent: Node): Iterable<IAstModification> {
        if(string.parent !is VarDecl) {
            // replace the literal string by a identifier reference to a new local vardecl
            val vardecl = VarDecl.createAuto(string)
            val identifier = IdentifierReference(listOf(vardecl.name), vardecl.position)
            return listOf(
                    IAstModification.ReplaceNode(string, identifier, parent),
                    IAstModification.InsertFirst(vardecl, string.definingScope() as Node)
            )
        }
        return noModifications
    }

    override fun after(array: ArrayLiteralValue, parent: Node): Iterable<IAstModification> {
        val vardecl = array.parent as? VarDecl
        if(vardecl!=null) {
            // adjust the datatype of the array (to an educated guess)
            val arrayDt = array.type
            if(!arrayDt.istype(vardecl.datatype)) {
                val cast = array.cast(vardecl.datatype)
                if (cast != null && cast !== array)
                    return listOf(IAstModification.ReplaceNode(vardecl.value!!, cast, vardecl))
            }
        } else {
            val arrayDt = array.guessDatatype(program)
            if(arrayDt.isKnown) {
                // this array literal is part of an expression, turn it into an identifier reference
                val litval2 = array.cast(arrayDt.typeOrElse(DataType.STRUCT))
                if(litval2!=null) {
                    val vardecl2 = VarDecl.createAuto(litval2)
                    val identifier = IdentifierReference(listOf(vardecl2.name), vardecl2.position)
                    return listOf(
                            IAstModification.ReplaceNode(array, identifier, parent),
                            IAstModification.InsertFirst(vardecl2, array.definingScope() as Node)
                    )
                }
            }
        }
        return noModifications
    }
}
