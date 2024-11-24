package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*


internal class BeforeAsmTypecastCleaner(val program: Program,
                                        private val errors: IErrorReporter
) : AstWalker() {

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        // see if we can remove redundant typecasts (outside of expressions)
        // such as casting byte<->ubyte,  word<->uword  or even redundant casts (sourcetype = target type).
        // the special typecast of a reference type (str, array) to an UWORD will be changed into address-of,
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
                                AddressOf(identifier, null, typecast.position),
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

    override fun after(fcs: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        if(fcs.target.nameInSource==listOf("cmp")) {
            // if the datatype of the arguments of cmp() are different, cast the byte one to word.
            val arg1 = fcs.args[0]
            val arg2 = fcs.args[1]
            val dt1 = arg1.inferType(program).getOr(DataType.UNDEFINED)
            val dt2 = arg2.inferType(program).getOr(DataType.UNDEFINED)
            if(dt1==DataType.BOOL && dt2==DataType.BOOL)
                return noModifications
            else if(dt1 in ByteDatatypes) {
                if(dt2 in ByteDatatypes)
                    return noModifications
                val (replaced, cast) = arg1.typecastTo(if(dt1== DataType.UBYTE) DataType.UWORD else DataType.WORD, dt1, true)
                if(replaced)
                    return listOf(IAstModification.ReplaceNode(arg1, cast, fcs))
            } else {
                if(dt2 in WordDatatypes)
                    return noModifications
                val (replaced, cast) = arg2.typecastTo(if(dt2== DataType.UBYTE) DataType.UWORD else DataType.WORD, dt2, true)
                if(replaced)
                    return listOf(IAstModification.ReplaceNode(arg2, cast, fcs))
            }
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator=="<<" || expr.operator==">>") {
            val shifts = expr.right.constValue(program)
            if(shifts!=null) {
                val dt = expr.left.inferType(program)
                if(dt.istype(DataType.UBYTE) && shifts.number>=8.0)
                    errors.info("shift always results in 0", expr.position)
                if(dt.istype(DataType.UWORD) && shifts.number>=16.0)
                    errors.info("shift always results in 0", expr.position)
                if(shifts.number<=255.0 && shifts.type in WordDatatypes) {
                    val byteVal = NumericLiteral(DataType.UBYTE, shifts.number, shifts.position)
                    return listOf(IAstModification.ReplaceNode(expr.right, byteVal, expr))
                }
            }
        }
        return noModifications
    }
}