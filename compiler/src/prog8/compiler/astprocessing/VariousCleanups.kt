package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*


internal class VariousCleanups(val program: Program, val errors: IErrorReporter, val options: CompilationOptions): AstWalker() {

    override fun after(block: Block, parent: Node): Iterable<IAstModification> {
        val inheritOptions = block.definingModule.options() intersect setOf("no_symbol_prefixing", "ignore_unused") subtract block.options()
        if(inheritOptions.isNotEmpty()) {
            val directive = Directive("%option", inheritOptions.map{ DirectiveArg(it, null, block.position) }, block.position)
            return listOf(IAstModification.InsertFirst(directive, block))
        }

        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        // check and possibly adjust value datatype vs decl datatype
        val valueType = decl.value?.inferType(program)
        if(valueType!=null && !valueType.istype(decl.datatype)) {
            if(valueType.isUnknown) {
                errors.err("value has incompatible type for ${decl.datatype}", decl.value!!.position)
                return noModifications
            }
            val valueDt = valueType.getOrUndef()
            when(decl.type) {
                VarDeclType.VAR -> {
                    if(decl.isArray) {
                        if(decl.datatype.isSplitWordArray)
                            errors.err("value has incompatible type ($valueType) for the variable (${decl.datatype})", decl.value!!.position)
                    } else if(!decl.datatype.isString) {
                        if (valueDt.largerSizeThan(decl.datatype)) {
                            val constValue = decl.value?.constValue(program)
                            if (constValue != null)
                                errors.err("value '$constValue' out of range for ${decl.datatype}", constValue.position)
                            else
                                errors.err("value out of range for ${decl.datatype}", decl.value!!.position)
                        } else {
                            throw FatalAstException("value dt differs from decl dt ${decl.position}")
                        }
                    }
                }
                VarDeclType.CONST -> {
                    // change the vardecl type itself as well, but only if new type is smaller
                    if(valueDt.largerSizeThan(decl.datatype)) {
                        val constValue = decl.value!!.constValue(program)!!
                        errors.err("value '${constValue.number}' out of range for ${decl.datatype}", constValue.position)
                    } else {
                        // don't make it signed if it was unsigned and vice versa
                        if(valueDt.isSigned && decl.datatype.isUnsigned ||
                            valueDt.isUnsigned && decl.datatype.isSigned) {
                            val constValue = decl.value!!.constValue(program)!!
                            errors.err("value '${constValue.number}' out of range for ${decl.datatype}", constValue.position)
                        } else {
                            val changed = decl.copy(valueDt)
                            return listOf(IAstModification.ReplaceNode(decl, changed, parent))
                        }
                    }
                }
                VarDeclType.MEMORY -> if(!valueType.isWords && !valueType.isBytes)
                    throw FatalAstException("value type for a memory var should be word or byte (address)")
            }

        }

        // check splitting of word arrays
        if(!decl.datatype.isWordArray && decl.splitwordarray != SplitWish.DONTCARE) {
            if(decl.origin != VarDeclOrigin.ARRAYLITERAL)
                errors.err("@split and @nosplit are for word arrays only", decl.position)
        }
        else if(decl.datatype.isWordArray) {
            var changeDataType: DataType? = null
            var changeSplit: SplitWish = decl.splitwordarray
            when(decl.splitwordarray) {
                SplitWish.DONTCARE -> {
                    if(options.dontSplitWordArrays) {
                        changeDataType = if(decl.datatype.isSplitWordArray) DataType.arrayFor(decl.datatype.elementType().base, false) else null
                        changeSplit = SplitWish.NOSPLIT
                    }
                    else {
                        changeDataType = if(decl.datatype.isSplitWordArray) null else DataType.arrayFor(decl.datatype.elementType().base, true)
                        changeSplit = SplitWish.SPLIT
                    }
                }
                SplitWish.SPLIT -> {
                    changeDataType = if(decl.datatype.isSplitWordArray) null else DataType.arrayFor(decl.datatype.elementType().base, true)
                }
                SplitWish.NOSPLIT -> {
                    changeDataType = if(decl.datatype.isSplitWordArray) DataType.arrayFor(decl.datatype.elementType().base, false) else null
                }
            }
            if(changeDataType!=null) {
                var value = decl.value
                if(value is ArrayLiteral && !(value.type istype changeDataType)) {
                    value = ArrayLiteral(InferredTypes.knownFor(changeDataType), value.value, value.position)
                }
                val newDecl = VarDecl(decl.type, decl.origin, changeDataType, decl.zeropage,
                    changeSplit, decl.arraysize, decl.name, decl.names,
                    value, decl.sharedWithAsm, decl.alignment, decl.dirty, decl.position)
                return listOf(IAstModification.ReplaceNode(decl, newDecl, parent))
            }
        }
        return noModifications
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        return if(parent is IStatementContainer)
            listOf(ScopeFlatten(scope, parent as IStatementContainer))
        else
            noModifications
    }

    private class ScopeFlatten(val scope: AnonymousScope, val into: IStatementContainer) : IAstModification {
        override fun perform() {
            val idx = into.statements.indexOf(scope)
            if(idx>=0) {
                into.statements.addAll(idx+1, scope.statements)
                scope.statements.forEach { it.parent = into as Node }
                into.statements.remove(scope)
            }
        }
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        val constValue = typecast.constValue(program)
        if(constValue!=null)
            return listOf(IAstModification.ReplaceNode(typecast, constValue, parent))

        if(typecast.expression is NumericLiteral) {
            val value = (typecast.expression as NumericLiteral).cast(typecast.type, typecast.implicit)
            if(value.isValid)
                return listOf(IAstModification.ReplaceNode(typecast, value.valueOrZero(), parent))
        }

        val sourceDt = typecast.expression.inferType(program)
        if(sourceDt issimpletype typecast.type)
            return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))

        if(parent is Assignment) {
            val targetDt = parent.target.inferType(program).getOrUndef()
            if(!targetDt.isUndefined && sourceDt istype targetDt) {
                // we can get rid of this typecast because the type is already the target type
                return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))
            }
        }

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        if(assignment.target isSameAs assignment.value) {
            // remove assignment to self
            return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
        }

        // remove duplicated assignments, but not if it's a memory mapped IO register
        val isIO = try {
            assignment.target.isIOAddress(options.compTarget)
        } catch (_: FatalAstException) {
            false
        }
        if(!isIO) {
            val nextAssign = assignment.nextSibling() as? Assignment
            if (nextAssign != null && nextAssign.target.isSameAs(assignment.target, program)) {
                if (!nextAssign.isAugmentable && nextAssign.value isSameAs assignment.value && assignment.value !is IFunctionCall)    // don't remove function calls even when they're duplicates
                    return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
            }
        }

        return noModifications
    }

    override fun after(expr: PrefixExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator=="+") {
            // +X --> X
            return listOf(IAstModification.ReplaceNode(expr, expr.expression, parent))
        }

        if(expr.operator=="<<") {
            // << X --> X   (X is word or byte)
            val valueDt = expr.expression.inferType(program)
            if(valueDt.isBytes || valueDt.isWords) {
                return listOf(IAstModification.ReplaceNode(expr, expr.expression, parent))
            }
        }

        if(expr.operator=="^") {
            // ^ X --> 0  (X is word or byte)
            val valueDt = expr.expression.inferType(program)
            if(valueDt.isBytes || valueDt.isWords) {
                val zero = NumericLiteral(BaseDataType.UBYTE, 0.0, expr.expression.position)
                return listOf(IAstModification.ReplaceNode(expr, zero, parent))
            }
        }
        return noModifications
    }

    override fun before(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {

        if(expr.operator in ComparisonOperators) {
            if((expr.right as? NumericLiteral)?.number?.toInt() in -128..255 && expr.right.inferType(program).isWords) {
                val cast = expr.left as? TypecastExpression
                if(cast != null && cast.type.isWord && cast.expression.inferType(program).isBytes) {
                    val small = (expr.right as NumericLiteral).cast(cast.expression.inferType(program).getOrUndef().base, true)
                    if(small.isValid) {
                        return listOf(
                            IAstModification.ReplaceNode(expr.left, cast.expression, expr),
                            IAstModification.ReplaceNode(expr.right, small.valueOrZero(), expr)
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
                            IAstModification.ReplaceNode(expr.right, cast.expression, expr),
                            IAstModification.ReplaceNode(expr.left, small.valueOrZero(), expr)
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
                        val numbers = values.map{it.number}.toSet()
                        if(numbers == setOf(0.0, 1.0)) {
                            // we can replace unsigned  x==0 or x==1 with x<2
                            val compare = BinaryExpression(needle, "<", NumericLiteral(elementType.base, 2.0, expr.position), expr.position)
                            return listOf(IAstModification.ReplaceNode(expr, compare, parent))
                        }
                        if(numbers == setOf(0.0, 1.0, 2.0)) {
                            // we can replace unsigned  x==0 or x==1 or x==2 with x<3
                            val compare = BinaryExpression(needle, "<", NumericLiteral(elementType.base, 3.0, expr.position), expr.position)
                            return listOf(IAstModification.ReplaceNode(expr, compare, parent))
                        }
                    }
                    if(values.size<2)
                        return noModifications

                    // replace x==1 or x==2 or x==3  with a containment check  x in [1,2,3]
                    val valueCopies = values.sortedBy { it.number }.map { it.copy() }
                    val arrayType = DataType.arrayFor(elementType.base, true)
                    val valuesArray = ArrayLiteral(InferredTypes.InferredType.known(arrayType), valueCopies.toTypedArray(), expr.position)
                    val containment = ContainmentCheck(needle, valuesArray, expr.position)
                    return listOf(IAstModification.ReplaceNode(expr, containment, parent))
                }
            }
        }

        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
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
                return listOf(IAstModification.ReplaceNode(expr, replacement, parent))
            }
        }
        return noModifications
    }

    override fun after(containment: ContainmentCheck, parent: Node): Iterable<IAstModification> {
        // replace trivial containment checks with just false or a single comparison
        fun replaceWithEquals(value: NumericLiteral): Iterable<IAstModification> {
            errors.info("containment could be written as just a single comparison", containment.position)
            val equals = BinaryExpression(containment.element, "==", value, containment.position)
            return listOf(IAstModification.ReplaceNode(containment, equals, parent))
        }

        fun replaceWithFalse(): Iterable<IAstModification> {
            errors.warn("condition is always false", containment.position)
            return listOf(IAstModification.ReplaceNode(containment, NumericLiteral(BaseDataType.UBYTE, 0.0, containment.position), parent))
        }

        fun checkArray(array: Array<Expression>): Iterable<IAstModification> {
            if(array.isEmpty())
                return replaceWithFalse()
            if(array.size==1) {
                val constVal = array[0].constValue(program)
                if(constVal!=null)
                    return replaceWithEquals(constVal)
            }
            return noModifications
        }

        fun checkString(stringVal: StringLiteral): Iterable<IAstModification> {
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

    override fun after(branch: ConditionalBranch, parent: Node): Iterable<IAstModification> {
        if(branch.truepart.isEmpty() && branch.elsepart.isEmpty()) {
            errors.info("removing empty conditional branch", branch.position)
            return listOf(IAstModification.Remove(branch, parent as IStatementContainer))
        }

        return noModifications
    }

    override fun after(ifElse: IfElse, parent: Node): Iterable<IAstModification> {
        if(ifElse.truepart.isEmpty() && ifElse.elsepart.isEmpty()) {
            errors.info("removing empty if-else statement", ifElse.position)
            return listOf(IAstModification.Remove(ifElse, parent as IStatementContainer))
        }
        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        val index = arrayIndexedExpression.indexer.constIndex()
        if(index!=null && index<0) {
            val target = arrayIndexedExpression.arrayvar.targetVarDecl(program)
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
            }
        }
        return noModifications
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        val name = functionCallExpr.target.nameInSource
        if(name==listOf("msw")) {
            val valueDt = functionCallExpr.args[0].inferType(program)
            if(valueDt.isWords || valueDt.isBytes) {
                val zero = NumericLiteral(BaseDataType.UWORD, 0.0, functionCallExpr.position)
                return listOf(IAstModification.ReplaceNode(functionCallExpr, zero, parent))
            }
        } else if(name==listOf("lsw")) {
            val valueDt = functionCallExpr.args[0].inferType(program)
            if(valueDt.isWords)
                return listOf(IAstModification.ReplaceNode(functionCallExpr, functionCallExpr.args[0], parent))
            if(valueDt.isBytes) {
                val cast = TypecastExpression(functionCallExpr.args[0], BaseDataType.UWORD, true, functionCallExpr.position)
                return listOf(IAstModification.ReplaceNode(functionCallExpr, cast, parent))
            }
        }
        return noModifications
    }
}

