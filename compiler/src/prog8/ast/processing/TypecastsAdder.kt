package prog8.ast.processing

import prog8.ast.IFunctionCall
import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.functions.BuiltinFunctions


class TypecastsAdder(val program: Program, val errors: ErrorReporter) : AstWalker() {
    /*
     * Make sure any value assignments get the proper type casts if needed to cast them into the target variable's type.
     * (this includes function call arguments)
     */

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
        return emptyList()
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        // see if a typecast is needed to convert the value's type into the proper target type
        val valueItype = assignment.value.inferType(program)
        val targetItype = assignment.target.inferType(program, assignment)
        if(targetItype.isKnown && valueItype.isKnown) {
            val targettype = targetItype.typeOrElse(DataType.STRUCT)
            val valuetype = valueItype.typeOrElse(DataType.STRUCT)
            if (valuetype != targettype) {
                if (valuetype isAssignableTo targettype) {
                    return listOf(IAstModification.ReplaceNode(
                            assignment.value,
                            TypecastExpression(assignment.value, targettype, true, assignment.value.position),
                            assignment))
                } else {
                    fun castLiteral(cvalue: NumericLiteralValue): List<IAstModification.ReplaceNode> =
                        listOf(IAstModification.ReplaceNode(cvalue, cvalue.cast(targettype), cvalue.parent))
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
        return emptyList()
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        return afterFunctionCallArgs(functionCallStatement, functionCallStatement.definingScope())
    }

    override fun after(functionCall: FunctionCall, parent: Node): Iterable<IAstModification> {
        return afterFunctionCallArgs(functionCall, functionCall.definingScope())
    }

    private fun afterFunctionCallArgs(call: IFunctionCall, scope: INameScope): Iterable<IAstModification> {
        // see if a typecast is needed to convert the arguments into the required parameter's type
        return when(val sub = call.target.targetStatement(scope)) {
            is Subroutine -> {
                for(arg in sub.parameters.zip(call.args.withIndex())) {
                    val argItype = arg.second.value.inferType(program)
                    if(argItype.isKnown) {
                        val argtype = argItype.typeOrElse(DataType.STRUCT)
                        val requiredType = arg.first.type
                        if (requiredType != argtype) {
                            if (argtype isAssignableTo requiredType) {
                                return listOf(IAstModification.ReplaceNode(
                                        call.args[arg.second.index],
                                        TypecastExpression(arg.second.value, requiredType, true, arg.second.value.position),
                                        call as Node))
                            } else if(requiredType == DataType.UWORD && argtype in PassByReferenceDatatypes) {
                                // we allow STR/ARRAY values in place of UWORD parameters. Take their address instead.
                                return listOf(IAstModification.ReplaceNode(
                                        call.args[arg.second.index],
                                        AddressOf(arg.second.value as IdentifierReference, arg.second.value.position),
                                        call as Node))
                            }
                        }
                    }
                }
                emptyList()
            }
            is BuiltinFunctionStatementPlaceholder -> {
                val func = BuiltinFunctions.getValue(sub.name)
                if(func.pure) {
                    // non-pure functions don't get automatic typecasts because sometimes they act directly on their parameters
                    for (arg in func.parameters.zip(call.args.withIndex())) {
                        val argItype = arg.second.value.inferType(program)
                        if (argItype.isKnown) {
                            val argtype = argItype.typeOrElse(DataType.STRUCT)
                            if (arg.first.possibleDatatypes.any { argtype == it })
                                continue
                            for (possibleType in arg.first.possibleDatatypes) {
                                if (argtype isAssignableTo possibleType) {
                                    return listOf(IAstModification.ReplaceNode(
                                            call.args[arg.second.index],
                                            TypecastExpression(arg.second.value, possibleType, true, arg.second.value.position),
                                            call as Node))
                                }
                            }
                        }
                    }
                }
                emptyList()
            }
            null -> emptyList()
            else -> throw FatalAstException("call to something weird $sub   ${call.target}")
        }
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        // warn about any implicit type casts to Float, because that may not be intended
        if(typecast.implicit && typecast.type in setOf(DataType.FLOAT, DataType.ARRAY_F)) {
            errors.warn("byte or word value implicitly converted to float. Suggestion: use explicit cast as float, a float number, or revert to integer arithmetic", typecast.position)
        }
        return emptyList()
    }

    override fun after(memread: DirectMemoryRead, parent: Node): Iterable<IAstModification> {
        // make sure the memory address is an uword
        val dt = memread.addressExpression.inferType(program)
        if(dt.isKnown && dt.typeOrElse(DataType.UWORD)!=DataType.UWORD) {
            val typecast = (memread.addressExpression as? NumericLiteralValue)?.cast(DataType.UWORD)
                    ?: TypecastExpression(memread.addressExpression, DataType.UWORD, true, memread.addressExpression.position)
            return listOf(IAstModification.ReplaceNode(memread.addressExpression, typecast, memread))
        }
        return emptyList()
    }

    override fun after(memwrite: DirectMemoryWrite, parent: Node): Iterable<IAstModification> {
        // make sure the memory address is an uword
        val dt = memwrite.addressExpression.inferType(program)
        if(dt.isKnown && dt.typeOrElse(DataType.UWORD)!=DataType.UWORD) {
            val typecast = (memwrite.addressExpression as? NumericLiteralValue)?.cast(DataType.UWORD)
                    ?: TypecastExpression(memwrite.addressExpression, DataType.UWORD, true, memwrite.addressExpression.position)
            return listOf(IAstModification.ReplaceNode(memwrite.addressExpression, typecast, memwrite))
        }
        return emptyList()
    }

    override fun after(structLv: StructLiteralValue, parent: Node): Iterable<IAstModification> {
        // assignment of a struct literal value, some member values may need proper typecast

        fun addTypecastsIfNeeded(struct: StructDecl): Iterable<IAstModification> {
            val newValues = struct.statements.zip(structLv.values).map { (structMemberDecl, memberValue) ->
                val memberDt = (structMemberDecl as VarDecl).datatype
                val valueDt = memberValue.inferType(program)
                if (valueDt.typeOrElse(memberDt) != memberDt)
                    TypecastExpression(memberValue, memberDt, true, memberValue.position)
                else
                    memberValue
            }

            class StructLvValueReplacer(val targetStructLv: StructLiteralValue, val typecastValues: List<Expression>) : IAstModification {
                override fun perform() {
                    targetStructLv.values = typecastValues
                    typecastValues.forEach { it.linkParents(targetStructLv) }
                }
            }

            return if(structLv.values.zip(newValues).any { (v1, v2) -> v1 !== v2})
                listOf(StructLvValueReplacer(structLv, newValues))
            else
                emptyList()
        }

        val decl = structLv.parent as? VarDecl
        if(decl != null) {
            val struct = decl.struct
            if(struct != null)
                return addTypecastsIfNeeded(struct)
        } else {
            val assign = structLv.parent as? Assignment
            if (assign != null) {
                val decl2 = assign.target.identifier?.targetVarDecl(program.namespace)
                if(decl2 != null) {
                    val struct = decl2.struct
                    if(struct != null)
                        return addTypecastsIfNeeded(struct)
                }
            }
        }
        return emptyList()
    }

    override fun after(returnStmt: Return, parent: Node): Iterable<IAstModification> {
        // add a typecast to the return type if it doesn't match the subroutine's signature
        val returnValue = returnStmt.value
        if(returnValue!=null) {
            val subroutine = returnStmt.definingSubroutine()!!
            if(subroutine.returntypes.size==1) {
                val subReturnType = subroutine.returntypes.first()
                if (returnValue.inferType(program).istype(subReturnType))
                    return emptyList()
                if (returnValue is NumericLiteralValue) {
                    returnStmt.value = returnValue.cast(subroutine.returntypes.single())
                } else {
                    return listOf(IAstModification.ReplaceNode(
                            returnValue,
                            TypecastExpression(returnValue, subReturnType, true, returnValue.position),
                            returnStmt))
                }
            }
        }
        return emptyList()
    }
}
