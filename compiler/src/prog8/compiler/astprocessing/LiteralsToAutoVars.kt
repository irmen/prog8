package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.expressions.ArrayLiteralValue
import prog8.ast.expressions.ContainmentCheck
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.VarDecl
import prog8.ast.statements.WhenChoice
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification


internal class LiteralsToAutoVars(private val program: Program) : AstWalker() {

    override fun after(string: StringLiteralValue, parent: Node): Iterable<IAstModification> {
        if(string.parent !is VarDecl
            && string.parent !is WhenChoice
            && (string.parent !is ContainmentCheck || string.value.length>ContainmentCheck.max_inlined_string_length)) {
            // replace the literal string by an identifier reference to the interned string

            val parentFunc = (string.parent as? IFunctionCall)?.target
            if(parentFunc!=null) {
                if(parentFunc.nameInSource.size==1 && parentFunc.nameInSource[0]=="memory") {
                    // memory() builtin function just uses the string as a label name
                    return noModifications
                }
            }
            val scopedName = program.internString(string)
            val identifier = IdentifierReference(scopedName, string.position)
            return listOf(IAstModification.ReplaceNode(string, identifier, parent))
        }
        return noModifications
    }

    override fun after(array: ArrayLiteralValue, parent: Node): Iterable<IAstModification> {
        val vardecl = array.parent as? VarDecl
        if(vardecl!=null) {
            // adjust the datatype of the array (to an educated guess from the vardecl type)
            val arrayDt = array.type
            if(arrayDt isnot vardecl.datatype) {
                val cast = array.cast(vardecl.datatype)
                if(cast!=null && cast !== array)
                    return listOf(IAstModification.ReplaceNode(vardecl.value!!, cast, vardecl))
            }
        } else {
            if(array.parent is ContainmentCheck && array.value.size<ContainmentCheck.max_inlined_string_length)
                return noModifications

            val arrayDt = array.guessDatatype(program)
            if(arrayDt.isKnown) {
                // turn the array literal it into an identifier reference
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
