package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*


class TypecastsAdder(val program: Program, val options: CompilationOptions, val errors: IErrorReporter) : AstWalker() {
    /*
     * Make sure any value assignments get the proper type casts if needed to cast them into the target variable's type.
     * (this includes function call arguments)
     */

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val declValue = decl.value
        if(decl.type== VarDeclType.VAR && declValue!=null) {
            val valueDt = declValue.inferType(program)
            if(valueDt isnot decl.datatype) {

                // don't add a typecast on an array initializer value, unless booleans
                if(valueDt.isInteger && decl.datatype in ArrayDatatypes) {
                    if(decl.datatype == DataType.ARRAY_BOOL) {
                        val integer = declValue.constValue(program)?.number
                        if(integer!=null) {
                            val num = NumericLiteral(DataType.UBYTE, if(integer==0.0) 0.0 else 1.0, declValue.position)
                            num.parent = decl
                            decl.value = num
                        }
                    } else {
                        return noModifications
                    }
                }

                // don't add a typecast if the initializer value is inherently not assignable
                if(valueDt isNotAssignableTo decl.datatype)
                    return noModifications

                val modifications = mutableListOf<IAstModification>()
                addTypecastOrCastedValueModification(modifications, declValue, decl.datatype, decl)
                return modifications
            }
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        val leftDt = expr.left.inferType(program)
        val rightDt = expr.right.inferType(program)
        val leftCv = expr.left.constValue(program)
        val rightCv = expr.right.constValue(program)

        if(leftDt.isKnown && rightDt.isKnown) {

            if(expr.operator=="<<" && leftDt.isBytes) {
                // uword ww = 1 << shift    -->  make the '1' a word constant
                val leftConst = expr.left.constValue(program)
                if(leftConst!=null) {
                    val leftConstAsWord =
                        if(leftDt.istype(DataType.UBYTE))
                            NumericLiteral(DataType.UWORD, leftConst.number, leftConst.position)
                        else
                            NumericLiteral(DataType.WORD, leftConst.number, leftConst.position)
                    val modifications = mutableListOf<IAstModification>()
                    if (parent is Assignment) {
                        if (parent.target.inferType(program).isWords) {
                            modifications += IAstModification.ReplaceNode(expr.left, leftConstAsWord, expr)
//                            if(rightDt.isBytes)
//                                modifications += IAstModification.ReplaceNode(expr.right, TypecastExpression(expr.right, leftConstAsWord.type, true, expr.right.position), expr)
                        }
                    } else if (parent is TypecastExpression && parent.type == DataType.UWORD && parent.parent is Assignment) {
                        val assign = parent.parent as Assignment
                        if (assign.target.inferType(program).isWords) {
                            modifications += IAstModification.ReplaceNode(expr.left, leftConstAsWord, expr)
//                            if(rightDt.isBytes)
//                                modifications += IAstModification.ReplaceNode(expr.right, TypecastExpression(expr.right, leftConstAsWord.type, true, expr.right.position), expr)
                        }
                    }
                    if(modifications.isNotEmpty())
                        return modifications
                }
            }

            if(expr.operator in LogicalOperators && leftDt.isInteger && rightDt.isInteger) {
                // see if any of the operands needs conversion to bool
                val modifications = mutableListOf<IAstModification>()
                val newLeft = wrapWithBooleanCastIfNeeded(expr.left, program)
                val newRight = wrapWithBooleanCastIfNeeded(expr.right, program)
                if(newLeft!=null)
                    modifications += IAstModification.ReplaceNode(expr.left, newLeft, expr)
                if(newRight!=null)
                    modifications += IAstModification.ReplaceNode(expr.right, newRight, expr)
                if(modifications.isNotEmpty())
                    return modifications
            }
            if(leftDt!=rightDt) {
                // convert bool type to byte if needed
                if(leftDt istype DataType.BOOL && rightDt.isBytes && !rightDt.istype(DataType.BOOL)) {
                    if(rightCv==null || (rightCv.number!=1.0 && rightCv.number!=0.0))
                        return listOf(IAstModification.ReplaceNode(expr.left,
                            TypecastExpression(expr.left, rightDt.getOr(DataType.UNDEFINED),true, expr.left.position), expr))
                } else if(leftDt.isBytes && !leftDt.istype(DataType.BOOL) && rightDt istype DataType.BOOL) {
                    if(leftCv==null || (leftCv.number!=1.0 && leftCv.number!=0.0))
                        return listOf(IAstModification.ReplaceNode(expr.right,
                            TypecastExpression(expr.right, leftDt.getOr(DataType.UNDEFINED),true, expr.right.position), expr))
                }

                // convert a negative operand for bitwise operator to the 2's complement positive number instead
                if(expr.operator in BitwiseOperators && leftDt.isInteger && rightDt.isInteger) {
                    if(leftCv!=null && leftCv.number<0) {
                        val value = if(rightDt.isBytes) 256+leftCv.number else 65536+leftCv.number
                        return listOf(IAstModification.ReplaceNode(
                            expr.left,
                            NumericLiteral(rightDt.getOr(DataType.UNDEFINED), value, expr.left.position),
                            expr))
                    }
                    if(rightCv!=null && rightCv.number<0) {
                        val value = if(leftDt.isBytes) 256+rightCv.number else 65536+rightCv.number
                        return listOf(IAstModification.ReplaceNode(
                            expr.right,
                            NumericLiteral(leftDt.getOr(DataType.UNDEFINED), value, expr.right.position),
                            expr))
                    }

                    if(leftDt istype DataType.BYTE && rightDt.oneOf(DataType.UBYTE, DataType.UWORD)) {
                        // cast left to unsigned
                        val cast = TypecastExpression(expr.left, rightDt.getOr(DataType.UNDEFINED), true, expr.left.position)
                        return listOf(IAstModification.ReplaceNode(expr.left, cast, expr))
                    }
                    if(leftDt istype DataType.WORD && rightDt.oneOf(DataType.UBYTE, DataType.UWORD)) {
                        // cast left to unsigned word. Cast right to unsigned word if it is ubyte
                        val mods = mutableListOf<IAstModification>()
                        val cast = TypecastExpression(expr.left, DataType.UWORD, true, expr.left.position)
                        mods += IAstModification.ReplaceNode(expr.left, cast, expr)
                        if(rightDt istype DataType.UBYTE) {
                            mods += IAstModification.ReplaceNode(expr.right,
                                TypecastExpression(expr.right, DataType.UWORD, true, expr.right.position),
                                expr)
                        }
                        return mods
                    }
                    if(rightDt istype DataType.BYTE && leftDt.oneOf(DataType.UBYTE, DataType.UWORD)) {
                        // cast right to unsigned
                        val cast = TypecastExpression(expr.right, leftDt.getOr(DataType.UNDEFINED), true, expr.right.position)
                        return listOf(IAstModification.ReplaceNode(expr.right, cast, expr))
                    }
                    if(rightDt istype DataType.WORD && leftDt.oneOf(DataType.UBYTE, DataType.UWORD)) {
                        // cast right to unsigned word. Cast left to unsigned word if it is ubyte
                        val mods = mutableListOf<IAstModification>()
                        val cast = TypecastExpression(expr.right, DataType.UWORD, true, expr.right.position)
                        mods += IAstModification.ReplaceNode(expr.right, cast, expr)
                        if(leftDt istype DataType.UBYTE) {
                            mods += IAstModification.ReplaceNode(expr.left,
                                TypecastExpression(expr.left, DataType.UWORD, true, expr.left.position),
                                expr)
                        }
                        return mods
                    }
                }


                if((expr.operator!="<<" && expr.operator!=">>") || !leftDt.isWords || !rightDt.isBytes) {
                    // determine common datatype and add typecast as required to make left and right equal types
                    val (commonDt, toFix) = BinaryExpression.commonDatatype(leftDt.getOr(DataType.UNDEFINED), rightDt.getOr(DataType.UNDEFINED), expr.left, expr.right)
                    if(toFix!=null) {
                        val modifications = mutableListOf<IAstModification>()
                        when {
                            toFix===expr.left -> addTypecastOrCastedValueModification(modifications, expr.left, commonDt, expr)
                            toFix===expr.right -> addTypecastOrCastedValueModification(modifications, expr.right, commonDt, expr)
                            else -> throw FatalAstException("confused binary expression side")
                        }
                        return modifications
                    }
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
            val targettype = targetItype.getOr(DataType.UNDEFINED)
            val valuetype = valueItype.getOr(DataType.UNDEFINED)
            if (valuetype != targettype) {
                if (valuetype isAssignableTo targettype) {
                    if(valuetype in IterableDatatypes && targettype==DataType.UWORD)
                        // special case, don't typecast STR/arrays to UWORD, we support those assignments "directly"
                        return noModifications
                    if((assignment.value as? BinaryExpression)?.operator in ComparisonOperators) {
                        // special case, treat a boolean comparison result as the same type as the target value to avoid needless casts later
                        return noModifications
                    }
                    val modifications = mutableListOf<IAstModification>()
                    addTypecastOrCastedValueModification(modifications, assignment.value, targettype, assignment)
                    return modifications
                } else {
                    fun castLiteral(cvalue2: NumericLiteral): List<IAstModification.ReplaceNode> {
                        val cast = cvalue2.cast(targettype)
                        return if(cast.isValid)
                            listOf(IAstModification.ReplaceNode(assignment.value, cast.valueOrZero(), assignment))
                        else
                            emptyList()
                    }
                    val cvalue = assignment.value.constValue(program)
                    if(cvalue!=null) {
                        val number = cvalue.number
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
        return afterFunctionCallArgs(functionCallStatement)
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        return afterFunctionCallArgs(functionCallExpr)
    }

    private fun afterFunctionCallArgs(call: IFunctionCall): Iterable<IAstModification> {
        // see if a typecast is needed to convert the arguments into the required parameter's type
        val modifications = mutableListOf<IAstModification>()

        when(val sub = call.target.targetStatement(program)) {
            is Subroutine -> {
                sub.parameters.zip(call.args).forEach { (param, arg) ->
                    val argItype = arg.inferType(program)
                    if(argItype.isKnown) {
                        val argtype = argItype.getOr(DataType.UNDEFINED)
                        val requiredType = param.type
                        if (requiredType != argtype) {
                            if (argtype isAssignableTo requiredType) {
                                // don't need a cast for pass-by-reference types that are assigned to UWORD
                                if(requiredType!=DataType.UWORD || argtype !in PassByReferenceDatatypes)
                                    addTypecastOrCastedValueModification(modifications, arg, requiredType, call as Node)
                            } else if(requiredType == DataType.UWORD && argtype in PassByReferenceDatatypes) {
                                // We allow STR/ARRAY values in place of UWORD parameters.
                                // Take their address instead, UNLESS it's a str parameter in the containing subroutine
                                val identifier = arg as? IdentifierReference
                                if(identifier?.isSubroutineParameter(program)==false) {
                                    modifications += IAstModification.ReplaceNode(
                                            identifier,
                                            AddressOf(identifier, arg.position),
                                            call as Node)
                                }
                            } else if(arg is NumericLiteral) {
                                val cast = arg.cast(requiredType)
                                if(cast.isValid)
                                    modifications += IAstModification.ReplaceNode(
                                            arg,
                                            cast.valueOrZero(),
                                            call as Node)
                            } else if(requiredType==DataType.BOOL && argtype!=DataType.BOOL) {
                                // cast to bool
                                addTypecastOrCastedValueModification(modifications, arg, requiredType, call as Node)
                            }
                        }
                    }
                    else {
                        // if the argument is an identifier reference and the param is UWORD, add the missing &.
                        if(arg is IdentifierReference && DataType.UWORD == param.type) {
                            modifications += IAstModification.ReplaceNode(
                                arg,
                                AddressOf(arg, arg.position),
                                call as Node
                            )
                        }
                    }
                }
            }
            is BuiltinFunctionPlaceholder -> {
                val func = BuiltinFunctions.getValue(sub.name)
                func.parameters.zip(call.args).forEachIndexed { index, pair ->
                    val argItype = pair.second.inferType(program)
                    if (argItype.isKnown) {
                        val argtype = argItype.getOr(DataType.UNDEFINED)
                        if (pair.first.possibleDatatypes.all { argtype != it }) {
                            for (possibleType in pair.first.possibleDatatypes) {
                                if (argtype isAssignableTo possibleType) {
                                    addTypecastOrCastedValueModification(modifications, pair.second, possibleType, call as Node)
                                    break
                                }
                                else if(DataType.UWORD in pair.first.possibleDatatypes && argtype in PassByReferenceDatatypes) {
                                    // We allow STR/ARRAY values in place of UWORD parameters.
                                    // Take their address instead, UNLESS it's a str parameter in the containing subroutine
                                    val identifier = pair.second as? IdentifierReference
                                    if(identifier?.isSubroutineParameter(program)==false) {
                                        modifications += IAstModification.ReplaceNode(
                                            call.args[index],
                                            AddressOf(identifier, pair.second.position),
                                            call as Node)
                                        break
                                    }
                                }
                            }
                        }
                    }
                    else {
                        // if the argument is an identifier reference and the param is UWORD, add the missing &.
                        if(pair.second is IdentifierReference && DataType.UWORD in pair.first.possibleDatatypes) {
                            modifications += IAstModification.ReplaceNode(
                                call.args[index],
                                AddressOf(pair.second as IdentifierReference, pair.second.position),
                                call as Node
                            )
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
        if(typecast.implicit && typecast.type.oneOf(DataType.FLOAT, DataType.ARRAY_F)) {
            if(options.floats)
                errors.warn("integer implicitly converted to float. Suggestion: use float literals, add an explicit cast, or revert to integer arithmetic", typecast.position)
            else
                errors.err("integer implicitly converted to float but floating point is not enabled via options", typecast.position)
        }

        return noModifications
    }

    override fun after(memread: DirectMemoryRead, parent: Node): Iterable<IAstModification> {
        // make sure the memory address is an uword
        val modifications = mutableListOf<IAstModification>()
        val dt = memread.addressExpression.inferType(program)
        if(dt.isKnown && dt.getOr(DataType.UWORD)!=DataType.UWORD) {
            val castedValue = (memread.addressExpression as? NumericLiteral)?.cast(DataType.UWORD)?.valueOrZero()
            if(castedValue!=null)
                modifications += IAstModification.ReplaceNode(memread.addressExpression, castedValue, memread)
            else
                addTypecastOrCastedValueModification(modifications, memread.addressExpression, DataType.UWORD, memread)
        }
        return modifications
    }

    override fun after(memwrite: DirectMemoryWrite, parent: Node): Iterable<IAstModification> {
        // make sure the memory address is an uword
        val modifications = mutableListOf<IAstModification>()
        val dt = memwrite.addressExpression.inferType(program)
        if(dt.isKnown && dt.getOr(DataType.UWORD)!=DataType.UWORD) {
            val castedValue = (memwrite.addressExpression as? NumericLiteral)?.cast(DataType.UWORD)?.valueOrZero()
            if(castedValue!=null)
                modifications += IAstModification.ReplaceNode(memwrite.addressExpression, castedValue, memwrite)
            else
                addTypecastOrCastedValueModification(modifications, memwrite.addressExpression, DataType.UWORD, memwrite)
        }
        return modifications
    }

    override fun after(returnStmt: Return, parent: Node): Iterable<IAstModification> {
        // add a typecast to the return type if it doesn't match the subroutine's signature
        val returnValue = returnStmt.value
        if(returnValue!=null) {
            val subroutine = returnStmt.definingSubroutine!!
            if(subroutine.returntypes.size==1) {
                val subReturnType = subroutine.returntypes.first()
                if (returnValue.inferType(program) istype subReturnType)
                    return noModifications
                if (returnValue is NumericLiteral) {
                    val cast = returnValue.cast(subroutine.returntypes.single())
                    if(cast.isValid)
                        returnStmt.value = cast.valueOrZero()
                } else {
                    val modifications = mutableListOf<IAstModification>()
                    addTypecastOrCastedValueModification(modifications, returnValue, subReturnType, returnStmt)
                    return modifications
                }
            }
        }
        return noModifications
    }

    override fun after(whenChoice: WhenChoice, parent: Node): Iterable<IAstModification> {
        if((parent as When).condition.inferType(program).isWords) {
            val values = whenChoice.values
            values?.toTypedArray()?.withIndex()?.forEach { (index, value) ->
                val num = value.constValue(program)
                if(num!=null && num.type in ByteDatatypes) {
                    val wordNum = NumericLiteral(if(num.type==DataType.UBYTE) DataType.UWORD else DataType.WORD, num.number, num.position)
                    wordNum.parent = num.parent
                    values[index] = wordNum
                }
            }
        }
        return noModifications
    }

    private fun addTypecastOrCastedValueModification(
        modifications: MutableList<IAstModification>,
        expressionToCast: Expression,
        requiredType: DataType,
        parent: Node
    ) {
        val sourceDt = expressionToCast.inferType(program).getOr(DataType.UNDEFINED)
        if(sourceDt == requiredType)
            return
        if(expressionToCast is NumericLiteral && expressionToCast.type!=DataType.FLOAT) { // refuse to automatically truncate floats
            val castedValue = expressionToCast.cast(requiredType)
            if (castedValue.isValid) {
                modifications += IAstModification.ReplaceNode(expressionToCast, castedValue.valueOrZero(), parent)
                return
            }
        }
        val cast = TypecastExpression(expressionToCast, requiredType, true, expressionToCast.position)
        modifications += IAstModification.ReplaceNode(expressionToCast, cast, parent)
    }
}
