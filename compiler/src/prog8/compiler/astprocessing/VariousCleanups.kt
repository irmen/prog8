package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.*
import prog8.code.core.*


internal class VariousCleanups(val program: Program, val errors: IErrorReporter, val options: CompilationOptions): AstWalker() {

    override fun after(block: Block, parent: Node): Iterable<AstModification> {
        val inheritOptions = block.definingModule.options() intersect setOf("no_symbol_prefixing", "ignore_unused") subtract block.options()
        if(inheritOptions.isNotEmpty()) {
            val directive = Directive("%option", inheritOptions.map{ DirectiveArg(it, null, block.position) }, block.position)
            return listOf(AstInsert.first(block, directive))
        }

        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<AstModification> {
        // check and possibly adjust value datatype vs decl datatype
        val valueType = decl.value?.inferType(program)
        if(valueType!=null && !valueType.istype(decl.datatype)) {
            if(valueType.isUnknown)
                return noModifications
            val valueDt = valueType.getOrUndef()
            if(!(valueDt.isWord && decl.datatype.isPointer)) {
                when(decl.type) {
                    VarDeclType.VAR -> {
                        if(decl.isArray) {
                            // using a array of words as initializer to a pointer array is fine
                            if (!valueDt.isSplitWordArray || !decl.datatype.isPointerArray)
                                errors.err("value has incompatible type ($valueType) for the variable (${decl.datatype})", decl.value!!.position)
                        } else if(!decl.datatype.isString) {
                            if (valueDt.largerSizeThan(decl.datatype)) {
                                val constValue = decl.value?.constValue(program)
                                if (constValue != null)
                                    errors.err("value '$constValue' out of range for ${decl.datatype}", constValue.position)
                                else
                                    errors.err("value out of range for ${decl.datatype}", decl.value!!.position)
                            }
                        }
                    }
                    VarDeclType.CONST -> {
                        // change the vardecl type itself as well, but only if new type is smaller
                        if(valueDt.largerSizeThan(decl.datatype)) {
                            val constValue = decl.value!!.constValue(program)!!
                            errors.err("value '${constValue.number}' out of range for ${decl.datatype}", constValue.position)
                        } else if(decl.datatype.largerSizeThan(valueDt)) {
                            // don't make it signed if it was unsigned and vice versa, except when it is a long const declaration
                            if(!decl.datatype.isLong &&
                                (valueDt.isSigned && decl.datatype.isUnsigned ||
                                valueDt.isUnsigned && decl.datatype.isSigned))
                            {
                                val constValue = decl.value!!.constValue(program)!!
                                errors.err("value '${constValue.number}' out of range for ${decl.datatype}", constValue.position)
                            } else {
                                val changed = decl.copy(valueDt)
                                return listOf(AstReplaceNode(decl, changed, parent))
                            }
                        }
                    }
                    VarDeclType.MEMORY -> { }
                }
            }
        }

        // check splitting of word arrays
        if(decl.splitwordarray != SplitWish.DONTCARE && !decl.datatype.isWordArray && !decl.datatype.isPointerArray) {
            if(decl.origin != VarDeclOrigin.ARRAYLITERAL)
                errors.err("@nosplit is for word or pointer arrays only", decl.position)
        }

        if(decl.datatype.isWordArray) {
            var changeDataType: DataType?
            when(decl.splitwordarray) {
                SplitWish.DONTCARE -> {
                    changeDataType = if(decl.datatype.isSplitWordArray) null else {
                        val eltDt = decl.datatype.elementType()
                        if(eltDt.isPointer)
                            TODO("convert array of pointers to split words array type  ${decl.position}")
                        else
                            DataType.arrayFor(eltDt.base)
                    }
                }
                SplitWish.NOSPLIT -> {
                    changeDataType = if(decl.datatype.isSplitWordArray && !decl.datatype.elementType().isPointer)
                        DataType.arrayFor(decl.datatype.elementType().base, false)
                    else null
                }
            }
            if(changeDataType!=null) {
                var value = decl.value
                if(value is ArrayLiteral && !(value.type istype changeDataType)) {
                    value = ArrayLiteral(InferredTypes.knownFor(changeDataType), value.value, value.position)
                }
                val newDecl = VarDecl(decl.type, decl.origin, changeDataType, decl.zeropage,
                    decl.splitwordarray, decl.arraysize, decl.matrixNumCols?.copy(), decl.name, decl.names,
                    value, decl.sharedWithAsm, decl.alignment, decl.dirty, decl.position)
                return listOf(AstReplaceNode(decl, newDecl, parent))
            }
        }

        // handle @nosplit on pointer arrays: convert to regular non-split word array
        if(decl.datatype.isPointerArray && decl.splitwordarray == SplitWish.NOSPLIT) {
            val newDt = DataType.arrayFor(BaseDataType.UWORD, false)  // false = not split (sequential)
            var value = decl.value
            if(value is ArrayLiteral && !(value.type istype newDt)) {
                value = ArrayLiteral(InferredTypes.knownFor(newDt), value.value, value.position)
            }
            val newDecl = VarDecl(decl.type, decl.origin, newDt, decl.zeropage,
                decl.splitwordarray, decl.arraysize, decl.matrixNumCols?.copy(), decl.name, decl.names,
                value, decl.sharedWithAsm, decl.alignment, decl.dirty, decl.position)
            return listOf(AstReplaceNode(decl, newDecl, parent))
        }
        return noModifications
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<AstModification> {
        return if(parent is IStatementContainer)
            listOf(AstScopeFlatten(scope, parent as IStatementContainer))
        else {
            if(scope.statements.any {it is VarDecl}) {
                throw FatalAstException("there are leftover vardecls in the nested scope at ${scope.position}, they should have been moved/placed in the declaration scope (subroutine) by now")
            }
            noModifications
        }
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<AstModification> {
        val constValue = typecast.constValue(program)
        if(constValue!=null)
            return listOf(AstReplaceNode(typecast, constValue, parent))

        val number = typecast.expression as? NumericLiteral
        if(number!=null) {
            if(typecast.type.isBasic) {
                val value = number.cast(typecast.type.base, typecast.implicit)
                if (value.isValid)
                    return listOf(AstReplaceNode(typecast, value.valueOrZero(), parent))
            }
        }

        val sourceDt = typecast.expression.inferType(program)
        if(sourceDt istype typecast.type)
            return listOf(AstReplaceNode(typecast, typecast.expression, parent))

        if(parent is Assignment) {
            val targetDt = parent.target.inferType(program).getOrUndef()
            if(!targetDt.isUndefined && sourceDt istype targetDt) {
                // we can get rid of this typecast because the type is already the target type
                return listOf(AstReplaceNode(typecast, typecast.expression, parent))
            }
        }

        // number cast to bool -> number!=0
        if (typecast.type.isBool) {
            val et = typecast.expression.inferType(program)
            if (et.isNumeric) {
                if(typecast.expression is NumericLiteral) {
                    val boolean = NumericLiteral.fromBoolean((typecast.expression as NumericLiteral).asBooleanValue, typecast.expression.position)
                    return listOf(AstReplaceNode(typecast, boolean, parent))
                } else {
                    val zero = defaultZero(et.getOrUndef().base, typecast.position)
                    val cmp = BinaryExpression(typecast.expression, "!=", zero, typecast.position)
                    return listOf(AstReplaceNode(typecast, cmp, parent))
                }
            }
        }

        if(typecast.type.isWord && parent is Assignment && typecast.expression.inferType(program).isPointer) {
            // a cast to uword can be removed if the assignment target type is uword (because any pointer can be assigned to uword)
            if(parent.target.inferType(program).isUnsignedWord)
                return listOf(AstReplaceNode(typecast, typecast.expression, parent))
        }

        // remove typecasts of arguments to builtin function like swap()
        if(parent is IFunctionCall) {
            if(parent.target.nameInSource.singleOrNull() in InplaceModifyingBuiltinFunctions) {
                return listOf(AstReplaceNode(typecast, typecast.expression, parent))
            }
        }

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<AstModification> {
        if(assignment.target isSameAs assignment.value) {
            // remove assignment to self
            return listOf(AstRemove(assignment, parent as IStatementContainer))
        }

        return noModifications
    }

    override fun after(expr: PrefixExpression, parent: Node): Iterable<AstModification> {
        if(expr.operator=="+") {
            // +X --> X
            return listOf(AstReplaceNode(expr, expr.expression, parent))
        }

        if(expr.operator=="<<") {
            // << X --> X   (X is long or word or byte)
            val valueDt = expr.expression.inferType(program)
            if(valueDt.isInteger) {
                return listOf(AstReplaceNode(expr, expr.expression, parent))
            }
        }

        if(expr.operator=="^") {
            // ^ X --> 0  (X is long word or byte)
            val valueDt = expr.expression.inferType(program)
            if(valueDt.isInteger) {
                val zero = NumericLiteral(BaseDataType.UBYTE, 0.0, expr.expression.position)
                return listOf(AstReplaceNode(expr, zero, parent))
            }
        }
        return noModifications
    }

    override fun before(expr: BinaryExpression, parent: Node): Iterable<AstModification> {

        if(expr.operator in ComparisonOperators) {
            if((expr.right as? NumericLiteral)?.number?.toInt() in -128..255 && expr.right.inferType(program).isWords) {
                val cast = expr.left as? TypecastExpression
                if(cast != null && cast.type.isWord && cast.expression.inferType(program).isBytes) {
                    val small = (expr.right as NumericLiteral).cast(cast.expression.inferType(program).getOrUndef().base, true)
                    if(small.isValid) {
                        return listOf(
                            AstReplaceNode(expr.left, cast.expression, expr),
                            AstReplaceNode(expr.right, small.valueOrZero(), expr)
                        )
                    }
                }
            }
            else if((expr.left as? NumericLiteral)?.number?.toInt() in -128..255 && expr.left.inferType(program).isWords) {
                val cast = expr.right as? TypecastExpression
                if(cast != null && cast.type.isWord && cast.expression.inferType(program).isBytes) {
                    val small = (expr.left as NumericLiteral).cast(cast.expression.inferType(program).getOrUndef().base, true)
                    if(small.isValid) {
                        return listOf(
                            AstReplaceNode(expr.right, cast.expression, expr),
                            AstReplaceNode(expr.left, small.valueOrZero(), expr)
                        )
                    }
                }
            }
        }

        // try to replace a multi-comparison expression (if x==1 | x==2 | x==3 ... ) by a simple containment check.
        // but only if the containment check is the top-level expression.
        if(parent is BinaryExpression)
            return noModifications
        if(expr.operator == "|" || expr.operator=="or") {
            val leftBinExpr1 = expr.left as? BinaryExpression
            val rightBinExpr1 = expr.right as? BinaryExpression

            if(rightBinExpr1?.operator=="==" && rightBinExpr1.right is NumericLiteral && leftBinExpr1!=null) {
                val needle = rightBinExpr1.left
                val values = mutableListOf(rightBinExpr1.right as NumericLiteral)

                fun isMultiComparisonRecurse(expr: BinaryExpression): Boolean {
                    if(expr.operator=="==") {
                        if(expr.right is NumericLiteral && expr.left isSameAs needle) {
                            values.add(expr.right as NumericLiteral)
                            return true
                        }
                        return false
                    }
                    if(expr.operator!="|" && expr.operator!="or")
                        return false
                    val leftBinExpr = expr.left as? BinaryExpression
                    val rightBinExpr = expr.right as? BinaryExpression
                    if(leftBinExpr==null || rightBinExpr==null || rightBinExpr.right !is NumericLiteral || !rightBinExpr.left.isSameAs(needle))
                        return false
                    if(rightBinExpr.operator=="==")
                        values.add(rightBinExpr.right as NumericLiteral)
                    else
                        return false
                    return isMultiComparisonRecurse(leftBinExpr)
                }

                if(isMultiComparisonRecurse(leftBinExpr1)) {
                    val elementIType = needle.inferType(program)
                    if(elementIType.isUnknown) return noModifications
                    val elementType = elementIType.getOrUndef()
                    if(values.size==2 || values.size==3 && (elementType.isUnsignedByte || elementType.isUnsignedWord)) {
                        val numbers = values.mapTo(mutableSetOf()) { it.number }
                        if(numbers == setOf(0.0, 1.0)) {
                            // we can replace unsigned  x==0 or x==1 with x<2
                            val compare = BinaryExpression(needle, "<", NumericLiteral(elementType.base, 2.0, expr.position), expr.position)
                            return listOf(AstReplaceNode(expr, compare, parent))
                        }
                        if(numbers == setOf(0.0, 1.0, 2.0)) {
                            // we can replace unsigned  x==0 or x==1 or x==2 with x<3
                            val compare = BinaryExpression(needle, "<", NumericLiteral(elementType.base, 3.0, expr.position), expr.position)
                            return listOf(AstReplaceNode(expr, compare, parent))
                        }
                    }
                    if(values.size<2)
                        return noModifications

                    // replace x==1 or x==2 or x==3  with a containment check  x in [1,2,3]
                    val valueCopies = values.sortedBy { it.number }.map { it.copy() }
                    val arrayType = DataType.arrayFor(elementType.base)
                    val valuesArray = ArrayLiteral(InferredTypes.InferredType.known(arrayType), valueCopies.toTypedArray(), expr.position)
                    val containment = ContainmentCheck(needle, valuesArray, expr.position)
                    return listOf(AstReplaceNode(expr, containment, parent))
                }
            }
        }

        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<AstModification> {
        if(expr.operator in ComparisonOperators) {
            val leftConstVal = expr.left.constValue(program)
            val rightConstVal = expr.right.constValue(program)
            // make sure the constant value is on the right of the comparison expression
            if(rightConstVal==null && leftConstVal!=null) {
                val newOperator =
                    when(expr.operator) {
                        "<" -> ">"
                        "<=" -> ">="
                        ">" -> "<"
                        ">=" -> "<="
                        else -> expr.operator
                    }
                val replacement = BinaryExpression(expr.right, newOperator, expr.left, expr.position)
                return listOf(AstReplaceNode(expr, replacement, parent))
            }


            // optimize boolean constant comparisons
            val leftDt = expr.left.inferType(program).getOrUndef()
            val rightDt = expr.right.inferType(program).getOrUndef()
            if(expr.operator=="==") {
                if(rightDt.isBool && leftDt.isBool) {
                    val rightConstBool = rightConstVal?.asBooleanValue
                    if(rightConstBool==true) {
                        return listOf(AstReplaceNode(expr, expr.left, parent))
                    }
                }
                if (rightConstVal?.number == 1.0) {
                    if (rightDt != leftDt && !(leftDt.isPointer && rightDt.isUnsignedWord)) {
                        val dt = if(leftDt.isPointer) BaseDataType.UWORD else leftDt.base
                        if(dt.isNumeric && !dt.isLong && dt!=BaseDataType.UNDEFINED) {
                            val right = NumericLiteral(dt, rightConstVal.number, rightConstVal.position)
                            return listOf(AstReplaceNode(expr.right, right, expr))
                        }
                    }
                }
                else if (rightConstVal?.number == 0.0) {
                    if (rightDt != leftDt && !(leftDt.isPointer && rightDt.isUnsignedWord)) {
                        val dt = if(leftDt.isPointer) BaseDataType.UWORD else leftDt.base
                        if(dt.isNumeric && !dt.isLong && dt!=BaseDataType.UNDEFINED) {
                            val right = NumericLiteral(dt, rightConstVal.number, rightConstVal.position)
                            return listOf(AstReplaceNode(expr.right, right, expr))
                        }
                    }
                }
            }
            if (expr.operator=="!=") {
                if(rightDt.isBool && leftDt.isBool) {
                    val rightConstBool = rightConstVal?.asBooleanValue
                    if(rightConstBool==false) {
                        listOf(AstReplaceNode(expr, expr.left, parent))
                    }
                }
                if (rightConstVal?.number == 1.0) {
                    if(rightDt!=leftDt && !(leftDt.isPointer && rightDt.isUnsignedWord)) {
                        val dt = if(leftDt.isPointer) BaseDataType.UWORD else leftDt.base
                        if(!dt.isLong && dt!=BaseDataType.UNDEFINED) {
                            val right = NumericLiteral(dt, rightConstVal.number, rightConstVal.position)
                            return listOf(AstReplaceNode(expr.right, right, expr))
                        }
                    }
                }
                else if (rightConstVal?.number == 0.0) {
                    if(rightDt!=leftDt && !(leftDt.isPointer && rightDt.isUnsignedWord)) {
                        val dt = if(leftDt.isPointer) BaseDataType.UWORD else leftDt.base
                        if(!dt.isLong && dt!=BaseDataType.UNDEFINED) {
                            val right = NumericLiteral(dt, rightConstVal.number, rightConstVal.position)
                            return listOf(AstReplaceNode(expr.right, right, expr))
                        }
                    }
                }
            }
        }
        return noModifications
    }

    override fun after(containment: ContainmentCheck, parent: Node): Iterable<AstModification> {
        // replace trivial containment checks with just false or a single comparison
        fun replaceWithEquals(value: NumericLiteral): Iterable<AstModification> {
            errors.info("containment could be written as just a single comparison", containment.position)
            val equals = BinaryExpression(containment.element, "==", value, containment.position)
            return listOf(AstReplaceNode(containment, equals, parent))
        }

        fun replaceWithFalse(): Iterable<AstModification> {
            errors.warn("condition is always false", containment.position)
            return listOf(AstReplaceNode(containment, NumericLiteral(BaseDataType.UBYTE, 0.0, containment.position), parent))
        }

        fun checkArray(array: Array<Expression>): Iterable<AstModification> {
            if(array.isEmpty())
                return replaceWithFalse()
            if(array.size==1) {
                val constVal = array[0].constValue(program)
                if(constVal!=null)
                    return replaceWithEquals(constVal)
            }
            return noModifications
        }

        fun checkString(stringVal: StringLiteral): Iterable<AstModification> {
            if(stringVal.value.isEmpty())
                return replaceWithFalse()
            if(stringVal.value.length==1) {
                val string = program.encoding.encodeString(stringVal.value, stringVal.encoding)
                return replaceWithEquals(NumericLiteral(BaseDataType.UBYTE, string[0].toDouble(), stringVal.position))
            }
            return noModifications
        }

        when(containment.iterable) {
            is ArrayLiteral -> {
                val array = (containment.iterable as ArrayLiteral).value
                return checkArray(array)
            }
            is RangeExpression -> {
                val constValues = (containment.iterable as RangeExpression).toConstantIntegerRange()
                if(constValues!=null) {
                    if (constValues.isEmpty())
                        return replaceWithFalse()
                    if (constValues.count()==1)
                        return replaceWithEquals(NumericLiteral.optimalNumeric(constValues.first, containment.position))
                }
            }
            is StringLiteral -> {
                val stringVal = containment.iterable as StringLiteral
                return checkString(stringVal)
            }
            else -> {}
        }
        return noModifications
    }

    override fun after(branch: ConditionalBranch, parent: Node): Iterable<AstModification> {
        if(branch.truepart.isEmpty() && branch.elsepart.isEmpty()) {
            errors.info("removing empty conditional branch", branch.position)
            return listOf(AstRemove(branch, parent as IStatementContainer))
        }

        return noModifications
    }

    override fun after(ifElse: IfElse, parent: Node): Iterable<AstModification> {
        if(ifElse.truepart.isEmpty() && ifElse.elsepart.isEmpty()) {
            errors.info("removing empty if-else statement", ifElse.position)
            return listOf(AstRemove(ifElse, parent as IStatementContainer))
        }
        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<AstModification> {
        val index = arrayIndexedExpression.indexer.constIndex()
        if(index!=null && index<0) {
            if(arrayIndexedExpression.plainarrayvar!=null) {
                val target = arrayIndexedExpression.plainarrayvar!!.targetVarDecl()
                val arraysize = target?.arraysize?.constIndex()
                if(arraysize!=null) {
                    if(arraysize+index < 0) {
                        errors.err("index out of bounds", arrayIndexedExpression.position)
                        return noModifications
                    }
                    // replace the negative index by the normal index
                    val newIndex = NumericLiteral.optimalNumeric(arraysize+index, arrayIndexedExpression.indexer.position)
                    arrayIndexedExpression.indexer.indexExpr = newIndex
                    newIndex.linkParents(arrayIndexedExpression.indexer)
                } else if(target?.datatype?.isString==true) {
                    val stringsize = (target.value as StringLiteral).value.length
                    if(stringsize+index < 0) {
                        errors.err("index out of bounds", arrayIndexedExpression.position)
                        return noModifications
                    }
                    // replace the negative index by the normal index
                    val newIndex = NumericLiteral.optimalNumeric(stringsize+index, arrayIndexedExpression.indexer.position)
                    arrayIndexedExpression.indexer.indexExpr = newIndex
                    newIndex.linkParents(arrayIndexedExpression.indexer)
                }
            } else if(arrayIndexedExpression.pointerderef!=null) {
                TODO("cleanup pointer indexing ${arrayIndexedExpression.position}")
            }
        }
        return noModifications
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<AstModification> {
        val name = functionCallExpr.target.nameInSource
        if(name==listOf("msw")) {
            val valueDt = functionCallExpr.args[0].inferType(program)
            if(valueDt.isWords || valueDt.isBytes || valueDt.isPointer) {
                val zero = NumericLiteral(BaseDataType.UWORD, 0.0, functionCallExpr.position)
                return listOf(AstReplaceNode(functionCallExpr, zero, parent))
            }
        } else if(name==listOf("lsw")) {
            val valueDt = functionCallExpr.args[0].inferType(program)
            if(valueDt.isWords || valueDt.isPointer)
                return listOf(AstReplaceNode(functionCallExpr, functionCallExpr.args[0], parent))
            if(valueDt.isBytes) {
                val cast = TypecastExpression(functionCallExpr.args[0], DataType.UWORD, true, functionCallExpr.position)
                return listOf(AstReplaceNode(functionCallExpr, cast, parent))
            }
        }

        if(parent is IStatementContainer) {
            val targetStruct = functionCallExpr.target.targetStructDecl()
            if (targetStruct != null) {
                // static struct instance allocation can only occur as an initializer for a pointer variable
                return listOf(AstRemove(functionCallExpr, parent as IStatementContainer))
            }
        }
        return noModifications
    }

    override fun after(addressOf: AddressOf, parent: Node): Iterable<AstModification> {
        if(addressOf.arrayIndex!=null) {
            val tgt = addressOf.identifier
            if(tgt!=null) {
                val constAddress = tgt.constValue(program)
                if (constAddress != null && constAddress.type.isInteger) {
                    // &constant[idx]  -->  constant + idx
                    val indexExpr = addressOf.arrayIndex!!.indexExpr
                    val right = if (indexExpr.inferType(program) issimpletype constAddress.type)
                        indexExpr
                    else
                        TypecastExpression(indexExpr, DataType.forDt(constAddress.type), true, indexExpr.position)
                    val add = BinaryExpression(constAddress, "+", right, addressOf.position)
                    return listOf(AstReplaceNode(addressOf, add, parent))
                } else {
                    val decl = tgt.targetVarDecl()
                    if(decl!=null && decl.datatype.isInteger) {
                        // &addressvar[idx]  -->  addressvar + idx
                        val indexExpr = addressOf.arrayIndex!!.indexExpr
                        val right = if (indexExpr.inferType(program) istype decl.datatype)
                            indexExpr
                        else
                            TypecastExpression(indexExpr, decl.datatype, true, indexExpr.position)
                        val add = BinaryExpression(tgt, "+", right, addressOf.position)
                        return listOf(AstReplaceNode(addressOf, add, parent))
                    }
                }
            }
        }
        return noModifications
    }

    override fun after(deref: PtrDereference, parent: Node): Iterable<AstModification> {
        fun peekFunc(dt: BaseDataType): Pair<String, DataType?> {
            return when {
                dt.isBool -> "peekbool" to null
                dt.isUnsignedByte -> "peek" to null
                dt.isSignedByte -> "peek" to DataType.BYTE
                dt.isUnsignedWord -> "peekw" to null
                dt.isSignedWord -> "peekw" to DataType.WORD
                dt.isLong -> "peekl" to null
                dt.isFloat -> "peekf" to null
                else -> throw FatalAstException("unexpected peek type $dt")
            }
        }

        if(parent is ArrayIndexedExpression && parent.parent !is AssignTarget && !partOfAugmentedAssignment(deref)) {
            val constIndex = parent.indexer.constIndex()
            if(constIndex==0) {
                // ptr1.field[0]  -->  peek(ptr.field)
                val dt=deref.inferType(program).getOrUndef()
                if(dt.sub!=null) {
                    val (peek, valueCast) = peekFunc(dt.sub!!)
                    val peekF = FunctionCallExpression(IdentifierReference(listOf(peek), deref.position), mutableListOf(replaceDerefWithIdentifier(deref)), deref.position)
                    val typedPeek = if(valueCast==null) peekF else TypecastExpression(peekF, valueCast, true, deref.position)
                    return listOf(AstReplaceNode(parent, typedPeek, parent.parent))
                }
            } else {
                // ptr1.field[index]  -->  peek(ptr.field + index)    (making sure the index is uword typed)
                val dt=deref.inferType(program).getOrUndef()
                if(dt.sub!=null) {
                    val (peek, valueCast) = peekFunc(dt.sub!!)
                    val indexer = if(constIndex==null) {
                        if(parent.indexer.indexExpr.inferType(program).isUnsignedWord)
                            parent.indexer.indexExpr
                        else
                            TypecastExpression(parent.indexer.indexExpr, DataType.UWORD, true, deref.position)
                    } else {
                        NumericLiteral(BaseDataType.UWORD, constIndex.toDouble(), deref.position)
                    }
                    val plusOffset = BinaryExpression(replaceDerefWithIdentifier(deref), "+", indexer, deref.position)
                    val peekF = FunctionCallExpression(IdentifierReference(listOf(peek), deref.position), mutableListOf(plusOffset), deref.position)
                    val typedPeek = if(valueCast==null) peekF else TypecastExpression(peekF, valueCast, true, deref.position)
                    return listOf(AstReplaceNode(parent, typedPeek, parent.parent))
                }
            }
        } else if(deref.derefLast && parent !is AssignTarget && !partOfAugmentedAssignment(deref)) {
            // ptr1.field^^  -->  peek(ptr1.field)
            val dt=deref.inferType(program).getOrUndef()
            if(!dt.isUndefined && dt.base.isNumericOrBool) {
                val (peek, valueCast) = peekFunc(dt.base)
                val peekF = FunctionCallExpression(IdentifierReference(listOf(peek), deref.position), mutableListOf(replaceDerefWithIdentifier(deref)), deref.position)
                val typedPeek = if(valueCast==null) peekF else TypecastExpression(peekF, valueCast, true, deref.position)
                return listOf(AstReplaceNode(deref, typedPeek, parent))
            }
        }
        return noModifications
    }

    private fun partOfAugmentedAssignment(deref: PtrDereference): Boolean {
        var parent = deref.parent
        while(parent !is Module) {
            if(parent is Assignment && parent.isAugmentable)
                return true
            parent = parent.parent
        }
        return false
    }

    private fun replaceDerefWithIdentifier(deref: PtrDereference): IdentifierReference {
        val ident = IdentifierReference(deref.chain, deref.position)
        val target = deref.definingScope.lookup(ident.nameInSource)
        when (target) {
            is VarDecl -> require(target.datatype.isPointer || target.datatype.isUnsignedWord)
            is StructFieldRef -> require(target.type.isPointer || target.type.isUnsignedWord)
            else -> throw FatalAstException("requires pointer or uword dereference target at ${deref.position}")
        }
        return ident
    }
}
