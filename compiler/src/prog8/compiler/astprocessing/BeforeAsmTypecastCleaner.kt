package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.ByteDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.PassByReferenceDatatypes
import prog8.ast.base.WordDatatypes
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.Expression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.TypecastExpression
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compilerinterface.IErrorReporter

internal class BeforeAsmTypecastCleaner(val program: Program,
                                        private val errors: IErrorReporter
) : AstWalker() {

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        // see if we can remove redundant typecasts (outside of expressions)
        // such as casting byte<->ubyte,  word<->uword  or even redundant casts (sourcetype = target type).
        // Also the special typecast of a reference type (str, array) to an UWORD will be changed into address-of,
        //   UNLESS it's a str parameter in the containing subroutine - then we remove the typecast altogether
        val sourceDt = typecast.expression.inferType(program).getOr(DataType.UNDEFINED)
        if (typecast.type in ByteDatatypes && sourceDt in ByteDatatypes
            || typecast.type in WordDatatypes && sourceDt in WordDatatypes
        ) {
            if(typecast.parent !is Expression) {
                return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))
            }
        }

        if(typecast.type==sourceDt)
            return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))

        if(sourceDt in PassByReferenceDatatypes) {
            if(typecast.type== DataType.UWORD) {
                val identifier = typecast.expression as? IdentifierReference
                if(identifier!=null) {
                    return if(identifier.isSubroutineParameter(program)) {
                        listOf(
                            IAstModification.ReplaceNode(
                                typecast,
                                typecast.expression,
                                parent
                            )
                        )
                    } else {
                        listOf(
                            IAstModification.ReplaceNode(
                                typecast,
                                AddressOf(identifier, typecast.position),
                                parent
                            )
                        )
                    }
                } else if(typecast.expression is IFunctionCall) {
                    return listOf(
                        IAstModification.ReplaceNode(
                            typecast,
                            typecast.expression,
                            parent
                        )
                    )
                }
            } else {
                errors.err("cannot cast pass-by-reference value to type ${typecast.type} (only to UWORD)", typecast.position)
            }
        }

        return noModifications
    }
}