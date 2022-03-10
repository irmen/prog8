package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.BuiltinFunctionCallStatement
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.ByteDatatypes
import prog8.code.core.DataType
import prog8.code.core.PassByReferenceDatatypes
import prog8.code.core.WordDatatypes
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


    // also convert calls to builtin functions to BuiltinFunctionCall nodes to make things easier for codegen

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        if(functionCallStatement.target.nameInSource.size==1
            && functionCallStatement.target.nameInSource[0] in program.builtinFunctions.names) {
            return listOf(IAstModification.ReplaceNode(
                functionCallStatement,
                BuiltinFunctionCallStatement(functionCallStatement.target, functionCallStatement.args, functionCallStatement.position),
                parent
            ))
        }

        return noModifications
    }


    override fun before(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        if(functionCallExpr.target.nameInSource.size==1
            && functionCallExpr.target.nameInSource[0] in program.builtinFunctions.names) {
            return listOf(IAstModification.ReplaceNode(
                functionCallExpr,
                BuiltinFunctionCall(functionCallExpr.target, functionCallExpr.args, functionCallExpr.position),
                parent
            ))
        }

        return noModifications
    }

    override fun after(bfcs: BuiltinFunctionCallStatement, parent: Node): Iterable<IAstModification> {
        if(bfcs.name=="cmp") {
            // if the datatype of the arguments of cmp() are different, cast the byte one to word.
            val arg1 = bfcs.args[0]
            val arg2 = bfcs.args[1]
            val dt1 = arg1.inferType(program).getOr(DataType.UNDEFINED)
            val dt2 = arg2.inferType(program).getOr(DataType.UNDEFINED)
            if(dt1 in ByteDatatypes) {
                if(dt2 in ByteDatatypes)
                    return noModifications
                val (replaced, cast) = arg1.typecastTo(if(dt1== DataType.UBYTE) DataType.UWORD else DataType.WORD, dt1, true)
                if(replaced)
                    return listOf(IAstModification.ReplaceNode(arg1, cast, bfcs))
            } else {
                if(dt2 in WordDatatypes)
                    return noModifications
                val (replaced, cast) = arg2.typecastTo(if(dt2== DataType.UBYTE) DataType.UWORD else DataType.WORD, dt2, true)
                if(replaced)
                    return listOf(IAstModification.ReplaceNode(arg2, cast, bfcs))
            }
        }
        return noModifications
    }

}