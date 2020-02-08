package prog8.ast.processing

import prog8.ast.IFunctionCall
import prog8.ast.INameScope
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.FatalAstException
import prog8.ast.base.printWarning
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.functions.BuiltinFunctions


internal class TypecastsAdder(private val program: Program): IAstModifyingVisitor {
    // Make sure any value assignments get the proper type casts if needed to cast them into the target variable's type.
    // (this includes function call arguments)

    override fun visit(expr: BinaryExpression): Expression {
        val expr2 = super.visit(expr)
        if(expr2 !is BinaryExpression)
            return expr2
        val leftDt = expr2.left.inferType(program)
        val rightDt = expr2.right.inferType(program)
        if(leftDt.isKnown && rightDt.isKnown && leftDt!=rightDt) {
            // determine common datatype and add typecast as required to make left and right equal types
            val (commonDt, toFix) = BinaryExpression.commonDatatype(leftDt.typeOrElse(DataType.STRUCT), rightDt.typeOrElse(DataType.STRUCT), expr2.left, expr2.right)
            if(toFix!=null) {
                when {
                    toFix===expr2.left -> {
                        expr2.left = TypecastExpression(expr2.left, commonDt, true, expr2.left.position)
                        expr2.left.linkParents(expr2)
                    }
                    toFix===expr2.right -> {
                        expr2.right = TypecastExpression(expr2.right, commonDt, true, expr2.right.position)
                        expr2.right.linkParents(expr2)
                    }
                    else -> throw FatalAstException("confused binary expression side")
                }
            }
        }
        return expr2
    }

    override fun visit(assignment: Assignment): Statement {
        val assg = super.visit(assignment)
        if(assg !is Assignment)
            return assg

        // see if a typecast is needed to convert the value's type into the proper target type
        val valueItype = assg.value.inferType(program)
        val targetItype = assg.target.inferType(program, assg)

        if(targetItype.isKnown && valueItype.isKnown) {
            val targettype = targetItype.typeOrElse(DataType.STRUCT)
            val valuetype = valueItype.typeOrElse(DataType.STRUCT)
            if (valuetype != targettype) {
                if (valuetype isAssignableTo targettype) {
                    assg.value = TypecastExpression(assg.value, targettype, true, assg.value.position)
                    assg.value.linkParents(assg)
                }
                // if they're not assignable, we'll get a proper error later from the AstChecker
            }
        }
        return assg
    }

    override fun visit(functionCallStatement: FunctionCallStatement): Statement {
        checkFunctionCallArguments(functionCallStatement, functionCallStatement.definingScope())
        return super.visit(functionCallStatement)
    }

    override fun visit(functionCall: FunctionCall): Expression {
        checkFunctionCallArguments(functionCall, functionCall.definingScope())
        return super.visit(functionCall)
    }

    private fun checkFunctionCallArguments(call: IFunctionCall, scope: INameScope) {
        // see if a typecast is needed to convert the arguments into the required parameter's type
        when(val sub = call.target.targetStatement(scope)) {
            is Subroutine -> {
                for(arg in sub.parameters.zip(call.args.withIndex())) {
                    val argItype = arg.second.value.inferType(program)
                    if(argItype.isKnown) {
                        val argtype = argItype.typeOrElse(DataType.STRUCT)
                        val requiredType = arg.first.type
                        if (requiredType != argtype) {
                            if (argtype isAssignableTo requiredType) {
                                val typecasted = TypecastExpression(arg.second.value, requiredType, true, arg.second.value.position)
                                typecasted.linkParents(arg.second.value.parent)
                                call.args[arg.second.index] = typecasted
                            }
                            // if they're not assignable, we'll get a proper error later from the AstChecker
                        }
                    }
                }
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
                                    val typecasted = TypecastExpression(arg.second.value, possibleType, true, arg.second.value.position)
                                    typecasted.linkParents(arg.second.value.parent)
                                    call.args[arg.second.index] = typecasted
                                    break
                                }
                            }
                        }
                    }
                }
            }
            null -> {}
            else -> throw FatalAstException("call to something weird $sub   ${call.target}")
        }
    }

    override fun visit(typecast: TypecastExpression): Expression {
        // warn about any implicit type casts to Float, because that may not be intended
        if(typecast.implicit && typecast.type in setOf(DataType.FLOAT, DataType.ARRAY_F)) {
            printWarning("byte or word value implicitly converted to float. Suggestion: use explicit cast as float, a float number, or revert to integer arithmetic", typecast.position)
        }
        return super.visit(typecast)
    }

    override fun visit(memread: DirectMemoryRead): Expression {
        // make sure the memory address is an uword
        val dt = memread.addressExpression.inferType(program)
        if(dt.isKnown && dt.typeOrElse(DataType.UWORD)!=DataType.UWORD) {
            val literaladdr = memread.addressExpression as? NumericLiteralValue
            if(literaladdr!=null) {
                memread.addressExpression = literaladdr.cast(DataType.UWORD)
            } else {
                memread.addressExpression = TypecastExpression(memread.addressExpression, DataType.UWORD, true, memread.addressExpression.position)
                memread.addressExpression.parent = memread
            }
        }
        return super.visit(memread)
    }

    override fun visit(memwrite: DirectMemoryWrite) {
        val dt = memwrite.addressExpression.inferType(program)
        if(dt.isKnown && dt.typeOrElse(DataType.UWORD)!=DataType.UWORD) {
            val literaladdr = memwrite.addressExpression as? NumericLiteralValue
            if(literaladdr!=null) {
                memwrite.addressExpression = literaladdr.cast(DataType.UWORD)
            } else {
                memwrite.addressExpression = TypecastExpression(memwrite.addressExpression, DataType.UWORD, true, memwrite.addressExpression.position)
                memwrite.addressExpression.parent = memwrite
            }
        }
        super.visit(memwrite)
    }

    override fun visit(structLv: StructLiteralValue): Expression {
        val litval = super.visit(structLv)
        if(litval !is StructLiteralValue)
            return litval

        val decl = litval.parent as? VarDecl
        if(decl != null) {
            val struct = decl.struct
            if(struct != null) {
                addTypecastsIfNeeded(litval, struct)
            }
        } else {
            val assign = litval.parent as? Assignment
            if (assign != null) {
                val decl2 = assign.target.identifier?.targetVarDecl(program.namespace)
                if(decl2 != null) {
                    val struct = decl2.struct
                    if(struct != null) {
                        addTypecastsIfNeeded(litval, struct)
                    }
                }
            }
        }

        return litval
    }

    private fun addTypecastsIfNeeded(structLv: StructLiteralValue, struct: StructDecl) {
        structLv.values = struct.statements.zip(structLv.values).map {
            val memberDt = (it.first as VarDecl).datatype
            val valueDt = it.second.inferType(program)
            if (valueDt.typeOrElse(memberDt) != memberDt)
                TypecastExpression(it.second, memberDt, true, it.second.position)
            else
                it.second
        }
    }
}
