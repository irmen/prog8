package prog8.ast.processing

import prog8.ast.IFunctionCall
import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.ErrorReporter
import prog8.ast.base.FatalAstException
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
                    toFix===expr.left -> listOf(IAstModification.ReplaceExpr(
                            { newExpr -> expr.left = newExpr },
                            TypecastExpression(expr.left, commonDt, true, expr.left.position),
                            expr))
                    toFix===expr.right -> listOf(IAstModification.ReplaceExpr(
                            { newExpr ->  expr.right = newExpr },
                            TypecastExpression(expr.right, commonDt, true, expr.right.position),
                            expr))
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
                if (valuetype isAssignableTo targettype)
                    return listOf(IAstModification.ReplaceExpr(
                            { newExpr -> assignment.value=newExpr },
                            TypecastExpression(assignment.value, targettype, true, assignment.value.position),
                            assignment))
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
                                return listOf(IAstModification.ReplaceExpr(
                                        { newExpr -> call.args[arg.second.index] = newExpr },
                                        TypecastExpression(arg.second.value, requiredType, true, arg.second.value.position),
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
                                    return listOf(IAstModification.ReplaceExpr(
                                            { newExpr -> call.args[arg.second.index] = newExpr },
                                            TypecastExpression(arg.second.value, possibleType, true, arg.second.value.position),
                                            call as Node
                                    ))
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
            return listOf(IAstModification.ReplaceExpr(
                    { newExpr -> memread.addressExpression = newExpr },
                    typecast,
                    memread
            ))
        }
        return emptyList()
    }

    override fun after(memwrite: DirectMemoryWrite, parent: Node): Iterable<IAstModification> {
        // make sure the memory address is an uword
        val dt = memwrite.addressExpression.inferType(program)
        if(dt.isKnown && dt.typeOrElse(DataType.UWORD)!=DataType.UWORD) {
            val typecast = (memwrite.addressExpression as? NumericLiteralValue)?.cast(DataType.UWORD)
                    ?: TypecastExpression(memwrite.addressExpression, DataType.UWORD, true, memwrite.addressExpression.position)
            return listOf(IAstModification.ReplaceExpr(
                    { newExpr -> memwrite.addressExpression = newExpr },
                    typecast,
                    memwrite
            ))
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

            class Replacer(val targetStructLv: StructLiteralValue, val typecastValues: List<Expression>) : IAstModification {
                override fun perform() {
                    targetStructLv.values = typecastValues
                    typecastValues.forEach { it.linkParents(targetStructLv) }
                }
            }

            return if(structLv.values.zip(newValues).any { (v1, v2) -> v1 !== v2})
                listOf(Replacer(structLv, newValues))
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
}
