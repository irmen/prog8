package prog8.ast.processing

import prog8.ast.IFunctionCall
import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.functions.BuiltinFunctions
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification


class TypecastsAdder(val program: Program, val errors: ErrorReporter) : AstWalker() {
    /*
     * Make sure any value assignments get the proper type casts if needed to cast them into the target variable's type.
     * (this includes function call arguments)
     */

    private val noModifications = emptyList<IAstModification>()

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val declValue = decl.value
        if(decl.type==VarDeclType.VAR && declValue!=null && decl.struct==null) {
            val valueDt = declValue.inferType(program)
            if(!valueDt.istype(decl.datatype)) {

                // don't add a typecast on an array initializer value
                if(valueDt.typeOrElse(DataType.STRUCT) in IntegerDatatypes && decl.datatype in ArrayDatatypes)
                    return noModifications

                return listOf(IAstModification.ReplaceNode(
                        declValue,
                        TypecastExpression(declValue, decl.datatype, true, declValue.position),
                        decl
                ))
            }
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        val leftDt = expr.left.inferType(program)
        val rightDt = expr.right.inferType(program)
        if(leftDt.isKnown && rightDt.isKnown && leftDt!=rightDt) {
            // determine common datatype and add typecast as required to make left and right equal types
            val (commonDt, toFix) = BinaryExpression.commonDatatype(leftDt.typeOrElse(DataType.STRUCT), rightDt.typeOrElse(DataType.STRUCT), expr.left, expr.right)
            if(toFix!=null) {
                return when {
                    toFix===expr.left -> listOf(IAstModification.ReplaceNode(
                            expr.left, TypecastExpression(expr.left, commonDt, true, expr.left.position), expr))
                    toFix===expr.right -> listOf(IAstModification.ReplaceNode(
                            expr.right, TypecastExpression(expr.right, commonDt, true, expr.right.position), expr))
                    else -> throw FatalAstException("confused binary expression side")
                }
            }
        }
        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        // see if a typecast is needed to convert the value's type into the proper target type
        val valueItype = assignment.value.inferType(program)
        val targetItype = assignment.target.inferType(program)
        if(targetItype.isKnown && valueItype.isKnown) {
            val targettype = targetItype.typeOrElse(DataType.STRUCT)
            val valuetype = valueItype.typeOrElse(DataType.STRUCT)
            if (valuetype != targettype) {
                if (valuetype isAssignableTo targettype) {
                    if(valuetype in IterableDatatypes && targettype==DataType.UWORD)
                        // special case, don't typecast STR/arrays to UWORD, we support those assignments "directly"
                        return noModifications
                    return listOf(IAstModification.ReplaceNode(
                            assignment.value,
                            TypecastExpression(assignment.value, targettype, true, assignment.value.position),
                            assignment))
                } else {
                    fun castLiteral(cvalue: NumericLiteralValue): List<IAstModification.ReplaceNode> {
                        val cast = cvalue.cast(targettype)
                        return if(cast.isValid)
                            listOf(IAstModification.ReplaceNode(cvalue, cast.valueOrZero(), cvalue.parent))
                        else
                            emptyList()
                    }
                    val cvalue = assignment.value.constValue(program)
                    if(cvalue!=null) {
                        val number = cvalue.number.toDouble()
                        // more complex comparisons if the type is different, but the constant value is compatible
                        if (valuetype == DataType.BYTE && targettype == DataType.UBYTE) {
                            if(number>0)
                                return castLiteral(cvalue)
                        } else if (valuetype == DataType.WORD && targettype == DataType.UWORD) {
                            if(number>0)
                                return castLiteral(cvalue)
                        } else if (valuetype == DataType.UBYTE && targettype == DataType.BYTE) {
                            if(number<0x80)
                                return castLiteral(cvalue)
                        } else if (valuetype == DataType.UWORD && targettype == DataType.WORD) {
                            if(number<0x8000)
                                return castLiteral(cvalue)
                        }
                    }
                }
            }
        }
        return noModifications
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        return afterFunctionCallArgs(functionCallStatement, functionCallStatement.definingScope())
    }

    override fun after(functionCall: FunctionCall, parent: Node): Iterable<IAstModification> {
        return afterFunctionCallArgs(functionCall, functionCall.definingScope())
    }

    private fun afterFunctionCallArgs(call: IFunctionCall, scope: INameScope): Iterable<IAstModification> {
        // see if a typecast is needed to convert the arguments into the required parameter's type
        val modifications = mutableListOf<IAstModification>()

        when(val sub = call.target.targetStatement(scope)) {
            is Subroutine -> {
                sub.parameters.zip(call.args).forEachIndexed { index, pair ->
                    val argItype = pair.second.inferType(program)
                    if(argItype.isKnown) {
                        val argtype = argItype.typeOrElse(DataType.STRUCT)
                        val requiredType = pair.first.type
                        if (requiredType != argtype) {
                            if (argtype isAssignableTo requiredType) {
                                modifications += IAstModification.ReplaceNode(
                                        call.args[index],
                                        TypecastExpression(pair.second, requiredType, true, pair.second.position),
                                        call as Node)
                            } else if(requiredType == DataType.UWORD && argtype in PassByReferenceDatatypes) {
                                // we allow STR/ARRAY values in place of UWORD parameters. Take their address instead.
                                if(pair.second is IdentifierReference) {
                                    modifications += IAstModification.ReplaceNode(
                                            call.args[index],
                                            AddressOf(pair.second as IdentifierReference, pair.second.position),
                                            call as Node)
                                }
                            } else if(pair.second is NumericLiteralValue) {
                                val cast = (pair.second as NumericLiteralValue).cast(requiredType)
                                if(cast.isValid)
                                    modifications += IAstModification.ReplaceNode(
                                            call.args[index],
                                            cast.valueOrZero(),
                                            call as Node)
                            }
                        }
                    }
                }
            }
            is BuiltinFunctionStatementPlaceholder -> {
                val func = BuiltinFunctions.getValue(sub.name)
                func.parameters.zip(call.args).forEachIndexed { index, pair ->
                    val argItype = pair.second.inferType(program)
                    if (argItype.isKnown) {
                        val argtype = argItype.typeOrElse(DataType.STRUCT)
                        if (pair.first.possibleDatatypes.all { argtype != it }) {
                            for (possibleType in pair.first.possibleDatatypes) {
                                if (argtype isAssignableTo possibleType) {
                                    modifications += IAstModification.ReplaceNode(
                                            call.args[index],
                                            TypecastExpression(pair.second, possibleType, true, pair.second.position),
                                            call as Node)
                                    break
                                }
                            }
                        }
                    }
                }
            }
            else -> { }
        }

        return modifications
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        // warn about any implicit type casts to Float, because that may not be intended
        if(typecast.implicit && typecast.type in setOf(DataType.FLOAT, DataType.ARRAY_F)) {
            errors.warn("integer implicitly converted to float. Suggestion: use float literals, add an explicit cast, or revert to integer arithmetic", typecast.position)
        }
        return noModifications
    }

    override fun after(memread: DirectMemoryRead, parent: Node): Iterable<IAstModification> {
        // make sure the memory address is an uword
        val dt = memread.addressExpression.inferType(program)
        if(dt.isKnown && dt.typeOrElse(DataType.UWORD)!=DataType.UWORD) {
            val typecast = (memread.addressExpression as? NumericLiteralValue)?.cast(DataType.UWORD)?.valueOrZero()
                    ?: TypecastExpression(memread.addressExpression, DataType.UWORD, true, memread.addressExpression.position)
            return listOf(IAstModification.ReplaceNode(memread.addressExpression, typecast, memread))
        }
        return noModifications
    }

    override fun after(memwrite: DirectMemoryWrite, parent: Node): Iterable<IAstModification> {
        // make sure the memory address is an uword
        val dt = memwrite.addressExpression.inferType(program)
        if(dt.isKnown && dt.typeOrElse(DataType.UWORD)!=DataType.UWORD) {
            val typecast = (memwrite.addressExpression as? NumericLiteralValue)?.cast(DataType.UWORD)?.valueOrZero()
                    ?: TypecastExpression(memwrite.addressExpression, DataType.UWORD, true, memwrite.addressExpression.position)
            return listOf(IAstModification.ReplaceNode(memwrite.addressExpression, typecast, memwrite))
        }
        return noModifications
    }

    override fun after(returnStmt: Return, parent: Node): Iterable<IAstModification> {
        // add a typecast to the return type if it doesn't match the subroutine's signature
        val returnValue = returnStmt.value
        if(returnValue!=null) {
            val subroutine = returnStmt.definingSubroutine()!!
            if(subroutine.returntypes.size==1) {
                val subReturnType = subroutine.returntypes.first()
                if (returnValue.inferType(program).istype(subReturnType))
                    return noModifications
                if (returnValue is NumericLiteralValue) {
                    val cast = returnValue.cast(subroutine.returntypes.single())
                    if(cast.isValid)
                        returnStmt.value = cast.valueOrZero()
                } else {
                    return listOf(IAstModification.ReplaceNode(
                            returnValue,
                            TypecastExpression(returnValue, subReturnType, true, returnValue.position),
                            returnStmt))
                }
            }
        }
        return noModifications
    }
}
