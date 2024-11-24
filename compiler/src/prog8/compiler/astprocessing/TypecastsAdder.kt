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
import kotlin.math.sign


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

                if(valueDt.isInteger && decl.isArray) {
                    if(decl.datatype == DataType.ARRAY_BOOL) {
                        val integer = declValue.constValue(program)?.number
                        if(integer!=null) {
                            val num = NumericLiteral(DataType.BOOL, if(integer==0.0) 0.0 else 1.0, declValue.position)
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

            if(leftDt!=rightDt) {
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
                        if(commonDt==DataType.BOOL) {
                            // don't automatically cast to bool
                            errors.err("left and right operands aren't the same type: $leftDt vs $rightDt", expr.position)
                        } else {
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
                    val modifications = mutableListOf<IAstModification>()
                    addTypecastOrCastedValueModification(modifications, assignment.value, targettype, assignment)
                    return modifications
                } else {
                    fun castLiteral(cvalue2: NumericLiteral): List<IAstModification.ReplaceNode> {
                        val cast = cvalue2.cast(targettype, true)
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
            is BuiltinFunctionPlaceholder -> BuiltinFunctions.getValue(sub.name).parameters
            is Subroutine -> sub.parameters.map { FParam(it.name, listOf(it.type).toTypedArray()) }
            else -> emptyList()
        }

        params.zip(call.args).forEach {
            val targetDt = it.first.possibleDatatypes.first()
            val argIdt = it.second.inferType(program)
            if (argIdt.isKnown) {
                val argDt = argIdt.getOr(DataType.UNDEFINED)
                if (argDt !in it.first.possibleDatatypes) {
                    val identifier = it.second as? IdentifierReference
                    val number = it.second as? NumericLiteral
                    if(number!=null) {
                        addTypecastOrCastedValueModification(modifications, it.second, targetDt, call as Node)
                    } else if(identifier!=null && targetDt==DataType.UWORD && argDt in PassByReferenceDatatypes) {
                        if(!identifier.isSubroutineParameter(program)) {
                            // We allow STR/ARRAY values for UWORD parameters.
                            // If it's an array (not STR), take the address.
                            if(argDt != DataType.STR) {
                                modifications += IAstModification.ReplaceNode(
                                    identifier,
                                    AddressOf(identifier, null, it.second.position),
                                    call as Node
                                )
                            }
                        }
                    } else if(targetDt==DataType.BOOL) {
                        addTypecastOrCastedValueModification(modifications, it.second, DataType.BOOL, call as Node)
                    } else if(argDt isAssignableTo targetDt) {
                        if(argDt!=DataType.STR || targetDt!=DataType.UWORD)
                            addTypecastOrCastedValueModification(modifications, it.second, targetDt, call as Node)
                    }
                }
            } else {
                val identifier = it.second as? IdentifierReference
                if(identifier!=null && targetDt==DataType.UWORD) {
                    // take the address of the identifier
                    modifications += IAstModification.ReplaceNode(
                        identifier,
                        AddressOf(identifier, null, it.second.position),
                        call as Node
                    )
                }
            }
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
            val castedValue = (memread.addressExpression as? NumericLiteral)?.cast(DataType.UWORD, true)?.valueOrZero()
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
            val castedValue = (memwrite.addressExpression as? NumericLiteral)?.cast(DataType.UWORD, true)?.valueOrZero()
            if(castedValue!=null)
                modifications += IAstModification.ReplaceNode(memwrite.addressExpression, castedValue, memwrite)
            else
                addTypecastOrCastedValueModification(modifications, memwrite.addressExpression, DataType.UWORD, memwrite)
        }
        return modifications
    }

    override fun after(returnStmt: Return, parent: Node): Iterable<IAstModification> {
        // add a typecast to the return type if it doesn't match the subroutine's signature
        // but only if no data loss occurs
        val returnValue = returnStmt.value
        if(returnValue!=null) {
            val subroutine = returnStmt.definingSubroutine!!
            if(subroutine.returntypes.size==1) {
                val subReturnType = subroutine.returntypes.first()
                val returnDt = returnValue.inferType(program)
                if(returnDt isnot subReturnType && returnValue is NumericLiteral) {
                    // see if we might change the returnvalue into the expected type
                    val castedValue = returnValue.convertTypeKeepValue(subReturnType)
                    if(castedValue.isValid) {
                        return listOf(IAstModification.ReplaceNode(returnValue, castedValue.valueOrZero(), returnStmt))
                    }
                }
                if (returnDt istype subReturnType or returnDt.isNotAssignableTo(subReturnType))
                    return noModifications
                if (returnValue is NumericLiteral) {
                    val cast = returnValue.cast(subReturnType, true)
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
        val conditionDt = (whenChoice.parent as When).condition.inferType(program)
        if(conditionDt.isKnown) {
            val values = whenChoice.values
            values?.toTypedArray()?.withIndex()?.forEach { (index, value) ->
                val valueDt = value.inferType(program)
                if(valueDt.isKnown && valueDt!=conditionDt) {
                    val castedValue = value.typecastTo(conditionDt.getOr(DataType.UNDEFINED), valueDt.getOr(DataType.UNDEFINED), true)
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
        val fromDt = range.from.inferType(program).getOr(DataType.UNDEFINED)
        val toDt = range.to.inferType(program).getOr(DataType.UNDEFINED)
        val fromConst = range.from.constValue(program)
        val toConst = range.to.constValue(program)
        val varDt = if (parent is ContainmentCheck)
                parent.element.inferType(program)
            else if (parent is ForLoop)
                parent.loopVarDt(program)
            else
                InferredTypes.InferredType.unknown()
        return adjustRangeDts(range, fromConst, fromDt, toConst, toDt, varDt.getOr(DataType.UNDEFINED), parent)
    }

    override fun after(array: ArrayLiteral, parent: Node): Iterable<IAstModification> {
        // Arrays can contain booleans, numbers, or address-ofs.
        // if there is an identifier here (that is of a pass-by-reference type), take its address explicitly.

        for((index, elt) in array.value.withIndex()) {
            if (elt is IdentifierReference) {
                val eltType = elt.inferType(program)
                val tgt = elt.targetStatement(program)
                if(eltType.isPassByReference || tgt is Subroutine || tgt is Label || tgt is Block)  {
                    val addressof = AddressOf(elt, null, elt.position)
                    addressof.linkParents(array)
                    array.value[index] = addressof
                }
            }
        }

        return noModifications
    }

    override fun after(ifExpr: IfExpression, parent: Node): Iterable<IAstModification> {
        val trueDt = ifExpr.truevalue.inferType(program)
        val falseDt = ifExpr.falsevalue.inferType(program)
        if (trueDt != falseDt) {
            val (commonDt, toFix) = BinaryExpression.commonDatatype(
                trueDt.getOr(DataType.UNDEFINED),
                falseDt.getOr(DataType.UNDEFINED),
                ifExpr.truevalue,
                ifExpr.falsevalue
            )
            if (toFix != null) {
                val modifications = mutableListOf<IAstModification>()
                addTypecastOrCastedValueModification(modifications, toFix, commonDt, ifExpr)
                return modifications
            }
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

        if(varDt!=DataType.UNDEFINED) {
            if(fromDt==varDt && toDt==varDt) {
                return noModifications
            } else if(fromDt==toDt && fromDt.isAssignableTo(varDt)) {
                return noModifications
            }
        }

        if(fromConst!=null) {
            val smaller = NumericLiteral.optimalInteger(fromConst.number.toInt(), fromConst.position)
            if(fromDt.largerThan(smaller.type)) {
                val toType = range.to.inferType(program)
                if(toType isnot smaller.type) {
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
            if(toDt.largerThan(smaller.type)) {
                val fromType = range.from.inferType(program)
                if(fromType isnot smaller.type) {
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
        val (commonDt, toChange) = BinaryExpression.commonDatatype(fromDt, toDt, range.from, range.to)
        if(toChange!=null)
            addTypecastOrCastedValueModification(modifications, toChange, commonDt, range)

        return modifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        val constIdx = arrayIndexedExpression.indexer.constIndex()
        if(constIdx!=null) {
            val smaller = NumericLiteral.optimalInteger(constIdx, arrayIndexedExpression.indexer.position)
            val idxDt = arrayIndexedExpression.indexer.indexExpr.inferType(program).getOr(DataType.UNDEFINED)
            if(idxDt.largerThan(smaller.type)) {
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
        requiredType: DataType,
        parent: Node
    ) {
        val sourceDt = expressionToCast.inferType(program).getOr(DataType.UNDEFINED)
        if(sourceDt == requiredType)
            return
        if(requiredType==DataType.BOOL) {
            return
        }
        if(expressionToCast is NumericLiteral && expressionToCast.type!=DataType.FLOAT) { // refuse to automatically truncate floats
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
        val cast = TypecastExpression(expressionToCast, requiredType, true, expressionToCast.position)
        modifications += IAstModification.ReplaceNode(expressionToCast, cast, parent)
    }
}
