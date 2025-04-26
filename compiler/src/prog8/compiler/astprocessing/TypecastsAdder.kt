package prog8.compiler.astprocessing

import prog8.ast.FatalAstException
import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*
import kotlin.math.sign


class TypecastsAdder(val program: Program, val options: CompilationOptions, val errors: IErrorReporter) : AstWalker() {
    /*
     * Make sure any value assignments get the proper type casts if needed to cast them into the target variable's type.
     * (this includes function call arguments)
     */

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val declValue = decl.value
        if(decl.type== VarDeclType.VAR && declValue!=null) {

            if(declValue is ArrayLiteral && declValue.type.isUnknown) {
                var guessed = declValue.inferType(program)
                if(guessed.isKnown) {
                    if(decl.datatype.isWordArray && guessed.getOrUndef().isWordArray) {
                        // just follow the type of the vardecl
                        guessed = InferredTypes.knownFor(decl.datatype)
                    }
                    val typedArray = ArrayLiteral(guessed, declValue.value, declValue.position)
                    return listOf(IAstModification.ReplaceNode(declValue, typedArray, decl))
                }
            }


            val valueDt = declValue.inferType(program)
            if(!(valueDt istype decl.datatype)) {

                if(valueDt.isInteger && decl.isArray) {
                    if(decl.datatype.isBoolArray) {
                        val integer = declValue.constValue(program)?.number
                        if(integer!=null) {
                            val num = NumericLiteral(BaseDataType.BOOL, if(integer==0.0) 0.0 else 1.0, declValue.position)
                            num.parent = decl
                            decl.value = num
                        }
                    } else {
                        return noModifications
                    }
                }
                if(valueDt.isArray && decl.isArray) {
                    // if the only difference in their array types is split vs non-split word arrays,
                    // we accept that (a cleanup of this is done elsewhere)
                    if(valueDt.getOrUndef().isWordArray && decl.datatype.isWordArray) {
                        val valueIsSplit = valueDt.getOrUndef().isSplitWordArray
                        val declIsSplit = decl.datatype.isSplitWordArray
                        if (valueIsSplit != declIsSplit) {
                            return noModifications
                        }
                    }
                }

                // don't add a typecast if the initializer value is inherently not assignable
                if(valueDt isNotAssignableTo decl.datatype)
                    return noModifications

                // uwords are allowed to be assigned to pointers without a cast
                if(decl.datatype.isPointer && valueDt.isUnsignedWord)
                    return noModifications

                val modifications = mutableListOf<IAstModification>()
                addTypecastOrCastedValueModification(modifications, declValue, decl.datatype.base, decl)
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
                        if(leftDt issimpletype BaseDataType.UBYTE)
                            NumericLiteral(BaseDataType.UWORD, leftConst.number, leftConst.position)
                        else
                            NumericLiteral(BaseDataType.WORD, leftConst.number, leftConst.position)
                    val modifications = mutableListOf<IAstModification>()
                    if (parent is Assignment) {
                        if (parent.target.inferType(program).isWords) {
                            modifications += IAstModification.ReplaceNode(expr.left, leftConstAsWord, expr)
//                            if(rightDt.isBytes)
//                                modifications += IAstModification.ReplaceNode(expr.right, TypecastExpression(expr.right, leftConstAsWord.type, true, expr.right.position), expr)
                        }
                    } else if (parent is TypecastExpression && parent.type.isUnsignedWord && parent.parent is Assignment) {
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

            if(leftDt!=rightDt) {
                // convert a negative operand for bitwise operator to the 2's complement positive number instead
                if(expr.operator in BitwiseOperators && leftDt.isInteger && rightDt.isInteger) {
                    if(leftCv!=null && leftCv.number<0) {
                        val value = if(rightDt.isBytes) 256+leftCv.number else 65536+leftCv.number
                        return listOf(IAstModification.ReplaceNode(
                            expr.left,
                            NumericLiteral(rightDt.getOrUndef().base, value, expr.left.position),
                            expr))
                    }
                    if(rightCv!=null && rightCv.number<0) {
                        val value = if(leftDt.isBytes) 256+rightCv.number else 65536+rightCv.number
                        return listOf(IAstModification.ReplaceNode(
                            expr.right,
                            NumericLiteral(leftDt.getOrUndef().base, value, expr.right.position),
                            expr))
                    }

                    if(leftDt issimpletype BaseDataType.BYTE && (rightDt issimpletype BaseDataType.UBYTE || rightDt issimpletype BaseDataType.UWORD)) {
                        // cast left to unsigned
                        val cast = TypecastExpression(expr.left, rightDt.getOrUndef(), true, expr.left.position)
                        return listOf(IAstModification.ReplaceNode(expr.left, cast, expr))
                    }
                    if(leftDt issimpletype BaseDataType.WORD && (rightDt issimpletype BaseDataType.UBYTE || rightDt issimpletype BaseDataType.UWORD)) {
                        // cast left to unsigned word. Cast right to unsigned word if it is ubyte
                        val mods = mutableListOf<IAstModification>()
                        val cast = TypecastExpression(expr.left, DataType.UWORD, true, expr.left.position)
                        mods += IAstModification.ReplaceNode(expr.left, cast, expr)
                        if(rightDt issimpletype BaseDataType.UBYTE) {
                            mods += IAstModification.ReplaceNode(expr.right,
                                TypecastExpression(expr.right, DataType.UWORD, true, expr.right.position),
                                expr)
                        }
                        return mods
                    }
                    if(rightDt issimpletype BaseDataType.BYTE && (leftDt issimpletype BaseDataType.UBYTE || leftDt issimpletype BaseDataType.UWORD)) {
                        // cast right to unsigned
                        val cast = TypecastExpression(expr.right, leftDt.getOrUndef(), true, expr.right.position)
                        return listOf(IAstModification.ReplaceNode(expr.right, cast, expr))
                    }
                    if(rightDt issimpletype BaseDataType.WORD && (leftDt issimpletype BaseDataType.UBYTE || leftDt issimpletype BaseDataType.UWORD)) {
                        // cast right to unsigned word. Cast left to unsigned word if it is ubyte
                        val mods = mutableListOf<IAstModification>()
                        val cast = TypecastExpression(expr.right, DataType.UWORD, true, expr.right.position)
                        mods += IAstModification.ReplaceNode(expr.right, cast, expr)
                        if(leftDt issimpletype BaseDataType.UBYTE) {
                            mods += IAstModification.ReplaceNode(expr.left,
                                TypecastExpression(expr.left, DataType.UWORD, true, expr.left.position),
                                expr)
                        }
                        return mods
                    }
                }


                if((expr.operator!="<<" && expr.operator!=">>") || !leftDt.isWords || !rightDt.isBytes) {
                    // determine common datatype and add typecast as required to make left and right equal types
                    val (commonDt, toFix) = BinaryExpression.commonDatatype(leftDt.getOrUndef(), rightDt.getOrUndef(), expr.left, expr.right)
                    if(toFix!=null) {
                        if(commonDt.isBool) {
                            // don't automatically cast to bool
                            errors.err("left and right operands aren't the same type: $leftDt vs $rightDt", expr.position)
                        } else {
                            val modifications = mutableListOf<IAstModification>()
                            when {
                                toFix===expr.left -> addTypecastOrCastedValueModification(modifications, expr.left, commonDt.base, expr)
                                toFix===expr.right -> addTypecastOrCastedValueModification(modifications, expr.right, commonDt.base, expr)
                                else -> throw FatalAstException("confused binary expression side")
                            }
                            return modifications
                        }
                    }
                }

                // comparison of a pointer with a number will simply treat the pointer as the uword that it is
                // this may require casting the other operand to uword as well
                if(expr.operator in ComparisonOperators) {
                    val modifications = mutableListOf<IAstModification>()
                    if(leftDt.isNumeric && rightDt.isPointer) {
                        val cast = TypecastExpression(expr.right, DataType.UWORD, true, expr.right.position)
                        modifications += IAstModification.ReplaceNode(expr.right, cast, expr)
                        if(!leftDt.isUnsignedWord && leftDt isAssignableTo InferredTypes.knownFor(BaseDataType.UWORD)) {
                            val cast2 = TypecastExpression(expr.left, DataType.UWORD, true, expr.left.position)
                            modifications += IAstModification.ReplaceNode(expr.left, cast2, expr)
                        }
                    }
                    else if(leftDt.isPointer && rightDt.isNumeric) {
                        val cast = TypecastExpression(expr.left, DataType.UWORD, true, expr.left.position)
                        modifications += IAstModification.ReplaceNode(expr.left, cast, expr)
                        if(!rightDt.isUnsignedWord && rightDt isAssignableTo InferredTypes.knownFor(BaseDataType.UWORD)) {
                            val cast2 = TypecastExpression(expr.right, DataType.UWORD, true, expr.right.position)
                            modifications += IAstModification.ReplaceNode(expr.right, cast2, expr)
                        }
                    }
                    return modifications
                }
            }

            // check if shifts have a positive integer shift type
            if(expr.operator=="<<" || expr.operator==">>") {
                if(rightDt.isInteger) {
                    val rconst = expr.right.constValue(program)
                    if(rconst!=null && rconst.number<0)
                        errors.err("can only shift by a positive amount", expr.right.position)
                } else
                    errors.err("right operand of bit shift must be an integer", expr.right.position)
            }
        }

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        // see if a typecast is needed to convert the value's type into the proper target type
        val valueItype = assignment.value.inferType(program)
        val targetItype = assignment.target.inferType(program)
        if(targetItype.isKnown && valueItype.isKnown) {
            val targettype = targetItype.getOrUndef()
            val valuetype = valueItype.getOrUndef()
            if (valuetype != targettype) {
                if (valuetype isAssignableTo targettype) {
                    if(valuetype.isIterable && targettype.isUnsignedWord)
                        // special case, don't typecast STR/arrays to UWORD, we support those assignments "directly"
                        return noModifications
                    val modifications = mutableListOf<IAstModification>()
                    addTypecastOrCastedValueModification(modifications, assignment.value, targettype.base, assignment)
                    return modifications
                } else {
                    fun castLiteral(cvalue2: NumericLiteral): List<IAstModification.ReplaceNode> {
                        val cast = cvalue2.cast(targettype.base, true)
                        return if(cast.isValid)
                            listOf(IAstModification.ReplaceNode(assignment.value, cast.valueOrZero(), assignment))
                        else
                            emptyList()
                    }
                    val cvalue = assignment.value.constValue(program)
                    if(cvalue!=null) {
                        return castLiteral(cvalue)
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
        // see if a typecast is needed to convert the arguments into the required parameter type
        val modifications = mutableListOf<IAstModification>()
        val params = when(val sub = call.target.targetStatement(program)) {
            is BuiltinFunctionPlaceholder -> BuiltinFunctions.getValue(sub.name).parameters.toList()
            is Subroutine -> sub.parameters.map { FParam(it.name, it.type.base) }
            else -> emptyList()
        }

        params.zip(call.args).forEach {
            val targetDt = it.first.possibleDatatypes.first()
            val argIdt = it.second.inferType(program)
            if (argIdt.isKnown) {
                val argDt = argIdt.getOrUndef()
                if (argDt.base !in it.first.possibleDatatypes) {
                    val identifier = it.second as? IdentifierReference
                    val number = it.second as? NumericLiteral
                    if(number!=null) {
                        addTypecastOrCastedValueModification(modifications, it.second, targetDt, call as Node)
                    } else if(identifier!=null && targetDt==BaseDataType.UWORD && argDt.isPassByRef) {
                        if(!identifier.isSubroutineParameter()) {
                            // We allow STR/ARRAY values for UWORD parameters.
                            // If it's an array (not STR), take the address.
                            if(!argDt.isString) {
                                modifications += IAstModification.ReplaceNode(
                                    identifier,
                                    AddressOf(identifier, null, null, false, it.second.position),
                                    call as Node
                                )
                            }
                        }
                    } else if(targetDt==BaseDataType.BOOL) {
                        addTypecastOrCastedValueModification(modifications, it.second, BaseDataType.BOOL, call as Node)
                    } else if(!targetDt.isIterable && argDt isAssignableTo DataType.forDt(targetDt)) {
                        if(!argDt.isString || targetDt!=BaseDataType.UWORD)
                            addTypecastOrCastedValueModification(modifications, it.second, targetDt, call as Node)
                    }
                }
            } else {
                val identifier = it.second as? IdentifierReference
                if(identifier!=null && targetDt==BaseDataType.UWORD) {
                    // take the address of the identifier
                    modifications += IAstModification.ReplaceNode(
                        identifier,
                        AddressOf(identifier, null, null, false, it.second.position),
                        call as Node
                    )
                }
            }
        }
        return modifications
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        // warn about any implicit type casts to Float, because that may not be intended
        if(typecast.implicit && typecast.type.isFloat) {
            if(options.floats)
                errors.info("integer implicitly converted to float. Suggestion: use float literals, add an explicit cast, or revert to integer arithmetic", typecast.position)
            else
                errors.err("integer implicitly converted to float but floating point is not enabled via options", typecast.position)
        }

        return noModifications
    }

    override fun after(memread: DirectMemoryRead, parent: Node): Iterable<IAstModification> {
        // make sure the memory address is an uword
        val modifications = mutableListOf<IAstModification>()
        val dt = memread.addressExpression.inferType(program)
        if(dt.isKnown && !dt.getOr(DataType.UWORD).isUnsignedWord) {
            val castedValue = (memread.addressExpression as? NumericLiteral)?.cast(BaseDataType.UWORD, true)?.valueOrZero()
            if(castedValue!=null)
                modifications += IAstModification.ReplaceNode(memread.addressExpression, castedValue, memread)
            else
                addTypecastOrCastedValueModification(modifications, memread.addressExpression, BaseDataType.UWORD, memread)
        }
        return modifications
    }

    override fun after(memwrite: DirectMemoryWrite, parent: Node): Iterable<IAstModification> {
        // make sure the memory address is an uword
        val modifications = mutableListOf<IAstModification>()
        val dt = memwrite.addressExpression.inferType(program)
        if(dt.isKnown && !dt.getOr(DataType.UWORD).isUnsignedWord) {
            val castedValue = (memwrite.addressExpression as? NumericLiteral)?.cast(BaseDataType.UWORD, true)?.valueOrZero()
            if(castedValue!=null)
                modifications += IAstModification.ReplaceNode(memwrite.addressExpression, castedValue, memwrite)
            else
                addTypecastOrCastedValueModification(modifications, memwrite.addressExpression, BaseDataType.UWORD, memwrite)
        }
        return modifications
    }

    override fun after(returnStmt: Return, parent: Node): Iterable<IAstModification> {
        // add a typecast to the return type if it doesn't match the subroutine's signature
        // but only if no data loss occurs
        if (returnStmt.values.isEmpty())
            return noModifications
        val subroutine = returnStmt.definingSubroutine!!
        if (subroutine.returntypes.size != returnStmt.values.size)
            return noModifications

        val modifications = mutableListOf<IAstModification>()
        for((index, pair) in returnStmt.values.zip(subroutine.returntypes).withIndex()) {
            val (returnValue, subReturnType) = pair
            val returnDt = returnValue.inferType(program)
            if(!(returnDt istype subReturnType) && returnValue is NumericLiteral) {
                // see if we might change the returnvalue into the expected type
                val castedValue = returnValue.convertTypeKeepValue(subReturnType.base)
                if(castedValue.isValid) {
                    modifications += listOf(IAstModification.ReplaceNode(returnValue, castedValue.valueOrZero(), returnStmt))
                    continue
                }
            }
            if (returnDt istype subReturnType or returnDt.isNotAssignableTo(subReturnType))
                continue
            if (returnValue is NumericLiteral) {
                val cast = returnValue.cast(subReturnType.base, true)
                if(cast.isValid) {
                    returnStmt.values[index] = cast.valueOrZero()
                }
            } else {
                addTypecastOrCastedValueModification(modifications, returnValue, subReturnType.base, returnStmt)
                continue
            }
        }
        return modifications
    }

    override fun after(whenChoice: WhenChoice, parent: Node): Iterable<IAstModification> {
        val conditionDt = (whenChoice.parent as When).condition.inferType(program)
        if(conditionDt.isKnown) {
            val values = whenChoice.values
            values?.toTypedArray()?.withIndex()?.forEach { (index, value) ->
                val valueDt = value.inferType(program)
                if(valueDt.isKnown && valueDt!=conditionDt) {
                    val castedValue = value.typecastTo(conditionDt.getOrUndef().base, valueDt.getOrUndef(), true)
                    if(castedValue.first) {
                        castedValue.second.linkParents(whenChoice)
                        values[index] = castedValue.second
                    }
                }
            }
        }
        return noModifications
    }

    override fun after(range: RangeExpression, parent: Node): Iterable<IAstModification> {
        val fromDt = range.from.inferType(program).getOrUndef()
        val toDt = range.to.inferType(program).getOrUndef()
        val fromConst = range.from.constValue(program)
        val toConst = range.to.constValue(program)
        val varDt = when (parent) {
            is ContainmentCheck -> parent.element.inferType(program)
            is ForLoop -> parent.loopVarDt(program)
            else -> InferredTypes.InferredType.unknown()
        }
        return adjustRangeDts(range, fromConst, fromDt, toConst, toDt, varDt.getOrUndef(), parent)
    }

    override fun after(array: ArrayLiteral, parent: Node): Iterable<IAstModification> {
        // Arrays can contain booleans, numbers, or address-ofs.
        // if there is an identifier here (that is of a pass-by-reference type), take its address explicitly.

        for((index, elt) in array.value.withIndex()) {
            if (elt is IdentifierReference) {
                val eltType = elt.inferType(program)
                val tgt = elt.targetStatement(program)
                if(eltType.isIterable || tgt is Subroutine || tgt is Label || tgt is Block)  {
                    val addressof = AddressOf(elt, null, null, false, elt.position)
                    addressof.linkParents(array)
                    array.value[index] = addressof
                }
            }
        }

        return noModifications
    }

    override fun after(ifExpr: IfExpression, parent: Node): Iterable<IAstModification> {
        val ct = ifExpr.condition.inferType(program)
        if(!ct.isBool && (ct.isNumeric || ct.isPointer)) {
            val cast = TypecastExpression(ifExpr.condition, DataType.BOOL, true, ifExpr.condition.position)
            return listOf(IAstModification.ReplaceNode(ifExpr.condition, cast, ifExpr))
        }

        val trueDt = ifExpr.truevalue.inferType(program)
        val falseDt = ifExpr.falsevalue.inferType(program)
        if (trueDt != falseDt) {
            val (commonDt, toFix) = BinaryExpression.commonDatatype(
                trueDt.getOrUndef(),
                falseDt.getOrUndef(),
                ifExpr.truevalue,
                ifExpr.falsevalue
            )
            if (toFix != null) {
                val modifications = mutableListOf<IAstModification>()
                addTypecastOrCastedValueModification(modifications, toFix, commonDt.base, ifExpr)
                return modifications
            }
        }
        return noModifications
    }

    override fun after(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {
        val ct = whileLoop.condition.inferType(program)
        if(!ct.isBool && (ct.isNumeric || ct.isPointer)) {
            val cast = TypecastExpression(whileLoop.condition, DataType.BOOL, true, whileLoop.condition.position)
            return listOf(IAstModification.ReplaceNode(whileLoop.condition, cast, whileLoop))
        }
        return noModifications
    }

    override fun after(ifElse: IfElse, parent: Node): Iterable<IAstModification> {
        val ct = ifElse.condition.inferType(program)
        if(!ct.isBool && (ct.isNumeric || ct.isPointer)) {
            val cast = TypecastExpression(ifElse.condition, DataType.BOOL, true, ifElse.condition.position)
            return listOf(IAstModification.ReplaceNode(ifElse.condition, cast, ifElse))
        }
        return noModifications
    }

    override fun after(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> {
        val ct = untilLoop.condition.inferType(program)
        if(!ct.isBool && (ct.isNumeric || ct.isPointer)) {
            val cast = TypecastExpression(untilLoop.condition, DataType.BOOL, true, untilLoop.condition.position)
            return listOf(IAstModification.ReplaceNode(untilLoop.condition, cast, untilLoop))
        }
        return noModifications
    }

    private fun adjustRangeDts(
        range: RangeExpression,
        fromConst: NumericLiteral?,
        fromDt: DataType,
        toConst: NumericLiteral?,
        toDt: DataType,
        varDt: DataType,
        parent: Node
    ): List<IAstModification> {

        if(!varDt.isUndefined && fromDt==varDt && toDt==varDt) {
            return noModifications
        }

        if(fromConst!=null) {
            val smaller = NumericLiteral.optimalInteger(fromConst.number.toInt(), fromConst.position)
            if(fromDt.base.largerSizeThan(smaller.type)) {
                val toType = range.to.inferType(program)
                if(!(toType issimpletype smaller.type)) {
                    if(toConst!=null) {
                        // can we make the to value into the same smaller type?
                        val smallerTo = NumericLiteral.optimalInteger(toConst.number.toInt(), toConst.position)
                        if(smaller.type==smallerTo.type) {
                            val newRange = RangeExpression(smaller, smallerTo, range.step, range.position)
                            return listOf(IAstModification.ReplaceNode(range, newRange, parent))
                        }
                    }
                } else {
                    val newRange = RangeExpression(smaller, range.to, range.step, range.position)
                    return listOf(IAstModification.ReplaceNode(range, newRange, parent))
                }
            }
        }
        if(toConst!=null) {
            val smaller = NumericLiteral.optimalInteger(toConst.number.toInt(), toConst.position)
            if(toDt.base.largerSizeThan(smaller.type)) {
                val fromType = range.from.inferType(program)
                if(!(fromType issimpletype  smaller.type)) {
                    if(fromConst!=null) {
                        // can we make the from value into the same smaller type?
                        val smallerFrom = NumericLiteral.optimalInteger(fromConst.number.toInt(), fromConst.position)
                        if(smaller.type==smallerFrom.type) {
                            val newRange = RangeExpression(smallerFrom, smaller, range.step, range.position)
                            return listOf(IAstModification.ReplaceNode(range, newRange, parent))
                        }
                    }
                } else {
                    val newRange = RangeExpression(range.from, smaller, range.step, range.position)
                    return listOf(IAstModification.ReplaceNode(range, newRange, parent))
                }
            }
        }

        val modifications = mutableListOf<IAstModification>()

        if(!varDt.isUndefined) {
            // adjust from value
            if (fromDt!=varDt) {
                if (!fromDt.isUndefined && !fromDt.isAssignableTo(varDt)) {
                    if(fromConst!=null) {
                        val cast = fromConst.cast(varDt.base, true)
                        if(cast.isValid)
                            modifications += IAstModification.ReplaceNode(range.from, cast.valueOrZero(), range)
                        else
                            errors.err("incompatible range value type", range.from.position)
                    } else {
                        errors.err("incompatible range value type", range.from.position)
                    }
                } else if(fromConst!=null) {
                    modifications += IAstModification.ReplaceNode(range.from, NumericLiteral(varDt.base, fromConst.number, fromConst.position), range)
                }
            }

            // adjust to value
            if (toDt!=varDt) {
                if (!toDt.isUndefined && !toDt.isAssignableTo(varDt)) {
                    if(toConst!=null) {
                        val cast = toConst.cast(varDt.base, true)
                        if(cast.isValid)
                            modifications += IAstModification.ReplaceNode(range.to, cast.valueOrZero(), range)
                        else
                            errors.err("incompatible range value type", range.to.position)
                    } else {
                        errors.err("incompatible range value type", range.to.position)
                    }
                } else if(toConst!=null) {
                    modifications += IAstModification.ReplaceNode(range.to, NumericLiteral(varDt.base, toConst.number, toConst.position), range)
                }
            }
            if(modifications.isNotEmpty())
                return modifications
        }

        val (commonDt, toChange) = BinaryExpression.commonDatatype(fromDt, toDt, range.from, range.to)
        if(toChange!=null)
            addTypecastOrCastedValueModification(modifications, toChange, commonDt.base, range)

        return modifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        val constIdx = arrayIndexedExpression.indexer.constIndex()
        if(constIdx!=null) {
            val smaller = NumericLiteral.optimalInteger(constIdx, arrayIndexedExpression.indexer.position)
            val idxDt = arrayIndexedExpression.indexer.indexExpr.inferType(program).getOrUndef()
            if(idxDt.base.largerSizeThan(smaller.type)) {
                val newIdx = ArrayIndex(smaller, smaller.position)
                val newIndexer = ArrayIndexedExpression(arrayIndexedExpression.arrayvar, newIdx, arrayIndexedExpression.position)
                return listOf(IAstModification.ReplaceNode(arrayIndexedExpression, newIndexer, parent))
            }
        }
        return noModifications
    }

    private fun addTypecastOrCastedValueModification(
        modifications: MutableList<IAstModification>,
        expressionToCast: Expression,
        requiredType: BaseDataType,     // TODO DataType?
        parent: Node
    ) {
        val sourceDt = expressionToCast.inferType(program).getOrUndef()
        if(sourceDt.base == requiredType)
            return
        if(requiredType == BaseDataType.BOOL) {
            if(sourceDt.isNumeric || sourceDt.isPointer) {
                // only allow numerics and pointers to be implicitly cast to bool
                val cast = TypecastExpression(expressionToCast, DataType.BOOL, true, expressionToCast.position)
                modifications += IAstModification.ReplaceNode(expressionToCast, cast, parent)
            }
            return
        }

        // uwords are allowed to be assigned to pointers without a cast
        if(requiredType.isPointer && sourceDt.isUnsignedWord)
            return

        if(expressionToCast is NumericLiteral && expressionToCast.type!=BaseDataType.FLOAT) { // refuse to automatically truncate floats
            val castedValue = expressionToCast.cast(requiredType, true)
            if (castedValue.isValid) {
                val signOriginal = sign(expressionToCast.number)
                val signCasted = sign(castedValue.valueOrZero().number)
                if(signOriginal==signCasted) {
                    modifications += IAstModification.ReplaceNode(expressionToCast, castedValue.valueOrZero(), parent)
                }
                return
            }
        }

        val cast = TypecastExpression(expressionToCast, DataType.forDt(requiredType), true, expressionToCast.position)
        modifications += IAstModification.ReplaceNode(expressionToCast, cast, parent)
    }
}
