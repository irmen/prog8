package prog8.optimizer

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.*
import prog8.code.core.*
import prog8.compiler.CallGraph

// Fix up the literal value's type to match that of the vardecl
//   (also check range literal operands types before they get expanded into arrays for instance)
class VarConstantValueTypeAdjuster(
    private val program: Program,
    private val options: CompilationOptions,
    private val errors: IErrorReporter
) : AstWalker() {

    private lateinit var callGraph : CallGraph

    override fun before(program: Program) : Iterable<AstModification> {
        callGraph = CallGraph(program)
        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<AstModification> {

        if(decl.parent is AnonymousScope)
            throw FatalAstException("vardecl may no longer occur in anonymousscope ${decl.position}")

        val declConstValue = decl.value?.constValue(program)
        if(declConstValue!=null && (decl.type== VarDeclType.VAR || decl.type==VarDeclType.CONST)
            && declConstValue.type != decl.datatype.base) {
            if(decl.datatype.isInteger && declConstValue.type == BaseDataType.FLOAT) {
                // avoid silent float roundings
                errors.err("refused truncating of float to avoid loss of precision", decl.value!!.position)
            }
        }

        // replace variables by constants, if possible
        if(options.optimize) {
            if (decl.sharedWithAsm || decl.type != VarDeclType.VAR || decl.origin != VarDeclOrigin.USERCODE || !decl.datatype.isNumericOrBool)
                return noModifications
            if (decl.value != null && decl.value!!.constValue(program) == null)
                return noModifications
            val usages = callGraph.usages(decl)
            val (writes, reads) = usages
                .partition {
                    it is InlineAssembly  // can't really tell if it's written to or only read, assume the worst
                            || it.parent is AssignTarget
                            || it.parent is ForLoop
                            || (it.parent as? IFunctionCall)?.target?.nameInSource?.singleOrNull() in InplaceModifyingBuiltinFunctions
                }

            // If the address of the variable is taken, we cannot replace it with a constant.
            if (reads.any { it.parent is AddressOf })
                return noModifications

            var singleAssignment: Assignment? = null
            val singleWrite=writes.singleOrNull()
            if(singleWrite!=null) {
                singleAssignment = singleWrite.parent as? Assignment
                if(singleAssignment==null) {
                    singleAssignment = singleWrite.parent.parent as? Assignment
                    if(singleAssignment==null) {
                        // we could be part of a multi-assign
                        if(singleWrite.parent is AssignTarget && singleWrite.parent.parent is AssignTarget)
                            singleAssignment = singleWrite.parent.parent.parent as? Assignment
                    }
                }
            }

            if (singleAssignment == null) {
                if (writes.isEmpty()) {
                    if(reads.isEmpty()) {
                        if(decl.names.size>1) {
                            errors.info("unused variable '${decl.name}'", decl.position)
                        } else {
                            // variable is never used AT ALL so we just remove it altogether
                            if ("ignore_unused" !in decl.definingBlock.options())
                                errors.info("removing unused variable '${decl.name}'", decl.position)
                            return listOf(AstRemove(decl, parent as IStatementContainer))
                        }
                    }
                    val declValue = decl.value?.constValue(program)
                    if (declValue != null) {
                        // variable is never written to, so it can be replaced with a constant, IF the value is a constant
                        errors.info("variable '${decl.name}' is never written to and was replaced by a constant", decl.position)
                        val const = VarDecl.builder(decl.datatype, decl.position)
                            .copyFrom(decl)
                            .type(VarDeclType.CONST)
                            .value(declValue)
                            .build()
                        decl.value = null
                        return listOf(
                            AstReplaceNode(decl, const, parent)
                        )
                    }
                }
            } else {
                if (singleAssignment.origin == AssignmentOrigin.VARINIT && singleAssignment.value.constValue(program) != null) {
                    if(reads.isEmpty()) {
                        if(decl.names.size>1) {
                            errors.info("unused variable '${decl.name}'", decl.position)
                        } else {
                            // variable is never used AT ALL so we just remove it altogether, including the single assignment
                            if("ignore_unused" !in decl.definingBlock.options())
                                errors.info("removing unused variable '${decl.name}'", decl.position)
                            return listOf(
                                AstRemove(decl, parent as IStatementContainer),
                                AstRemove(singleAssignment, singleAssignment.parent as IStatementContainer)
                            )
                        }
                    }

                    // variable only has a single write, and it is the initialization value, so it can be replaced with a constant, but only IF the value is a constant
                    errors.info("variable '${decl.name}' is never written to and was replaced by a constant", decl.position)
                    val const = VarDecl.builder(decl.datatype, decl.position)
                        .copyFrom(decl)
                        .type(VarDeclType.CONST)
                        .value(singleAssignment.value)
                        .build()
                    return listOf(
                        AstReplaceNode(decl, const, parent),
                        AstRemove(singleAssignment, singleAssignment.parent as IStatementContainer)
                    )
                }
            }
        }

        return noModifications
    }

    override fun after(range: RangeExpression, parent: Node): Iterable<AstModification> {
        val from = range.from.constValue(program)?.number
        val to = range.to.constValue(program)?.number
        val step = range.step.constValue(program)?.number

        if(from==null) {
            if(!range.from.inferType(program).isInteger)
                errors.err("range expression from value must be integer", range.from.position)
        } else if(from-from.toInt()>0) {
            errors.err("range expression from value must be integer", range.from.position)
        }

        if(to==null) {
            val toType = range.to.inferType(program)
            if(toType.isKnown && !range.to.inferType(program).isInteger)
                errors.err("range expression to value must be integer", range.to.position)
        } else if(to-to.toInt()>0) {
            errors.err("range expression to value must be integer", range.to.position)
        }

        if(step==null) {
            if(!range.step.inferType(program).isInteger)
                errors.err("range expression step value must be integer", range.step.position)
        } else if(step-step.toInt()>0) {
            errors.err("range expression step value must be integer", range.step.position)
        }

        return noModifications
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<AstModification> {
        // choose specific builtin function for the given types
        val func = functionCallExpr.target.nameInSource
        if(func==listOf("clamp")) {
            val t1 = functionCallExpr.args[0].inferType(program)
            if(t1.isKnown) {
                val replaceFunc = if(t1.isBytes) {
                    if(t1 issimpletype BaseDataType.BYTE) "clamp__byte" else "clamp__ubyte"
                } else if(t1.isInteger) {
                    when {
                        t1 issimpletype BaseDataType.WORD -> "clamp__word"
                        t1 issimpletype BaseDataType.UWORD -> "clamp__uword"
                        t1 issimpletype BaseDataType.LONG -> "clamp__long"
                        else -> throw FatalAstException("clamp type")
                    }
                } else {
                    errors.err("clamp builtin not supported for floats, use floats.clamp", functionCallExpr.position)
                    return noModifications
                }
                return listOf(AstSetExpression({functionCallExpr.target = it as IdentifierReference},
                    IdentifierReference(listOf(replaceFunc), functionCallExpr.target.position),
                    functionCallExpr))
            }
        }
        else if(func==listOf("min") || func==listOf("max")) {
            val t1 = functionCallExpr.args[0].inferType(program)
            val t2 = functionCallExpr.args[1].inferType(program)
            if(t1.isKnown && t2.isKnown) {
                val funcName = func[0]
                val replaceFunc: String
                if(t1.isBytes && t2.isBytes) {
                    replaceFunc = if(t1 issimpletype BaseDataType.BYTE || t2 issimpletype BaseDataType.BYTE)
                        "${funcName}__byte"
                    else
                        "${funcName}__ubyte"
                } else if(t1.isInteger && t2.isInteger) {
                    replaceFunc = when {
                        t1 issimpletype BaseDataType.LONG || t2 issimpletype BaseDataType.LONG -> "${funcName}__long"
                        t1 issimpletype BaseDataType.WORD || t2 issimpletype BaseDataType.WORD -> "${funcName}__word"
                        t1 issimpletype BaseDataType.UWORD || t2 issimpletype BaseDataType.UWORD -> "${funcName}__uword"
                        else -> throw FatalAstException("min/max type")
                    }
                } else if(t1.isNumeric && t2.isNumeric) {
                    errors.err("min/max not supported for floats, use floats.minf/maxf instead", functionCallExpr.position)
                    return noModifications
                } else {
                    errors.err("expected numeric arguments", functionCallExpr.args[0].position)
                    return noModifications
                }
                return listOf(AstSetExpression({functionCallExpr.target = it as IdentifierReference},
                    IdentifierReference(listOf(replaceFunc), functionCallExpr.target.position),
                    functionCallExpr))
            }
        }
        else if(func==listOf("abs")) {
            val t1 = functionCallExpr.args[0].inferType(program)
            if(t1.isKnown) {
                val dt = t1.getOrElse { throw InternalCompilerException("invalid dt") }
                val replaceFunc = when {
                    dt.isSignedByte -> "abs__byte"
                    dt.isSignedWord -> "abs__word"
                    dt.isLong -> "abs__long"
                    dt.isFloat -> "abs__float"
                    dt.isUnsignedByte || dt.isUnsignedWord -> {
                        return listOf(AstReplaceNode(functionCallExpr, functionCallExpr.args[0], parent))
                    }
                    else -> {
                        errors.err("expected numeric argument", functionCallExpr.args[0].position)
                        return noModifications
                    }
                }
                return listOf(AstSetExpression({functionCallExpr.target = it as IdentifierReference},
                    IdentifierReference(listOf(replaceFunc), functionCallExpr.target.position),
                    functionCallExpr))
            }
        }
        else if(func==listOf("sqrt")) {
            val t1 = functionCallExpr.args[0].inferType(program)
            if(t1.isKnown) {
                val dt = t1.getOrElse { throw InternalCompilerException("invalid dt") }
                val replaceFunc = when {
                    dt.isUnsignedByte -> "sqrt__ubyte"
                    dt.isUnsignedWord -> "sqrt__uword"
                    dt.isFloat -> "sqrt__float"
                    dt.isLong -> {
                        val value = functionCallExpr.args[0].constValue(program)?.number
                        if(value!=null && value<0) {
                            errors.err("expected positive integer or float numeric argument", functionCallExpr.args[0].position)
                            return noModifications
                        }
                        "sqrt__long"
                    }
                    dt.isSigned -> {
                        errors.err("expected unsigned (positive) numeric argument", functionCallExpr.args[0].position)
                        return noModifications
                    }
                    else -> {
                        errors.err("expected numeric argument", functionCallExpr.args[0].position)
                        return noModifications
                    }
                }
                return listOf(AstSetExpression({functionCallExpr.target = it as IdentifierReference},
                    IdentifierReference(listOf(replaceFunc), functionCallExpr.target.position),
                    functionCallExpr))
            }
        }
        else if(func==listOf("lsb") || func==listOf("msb")) {
            val t1 = functionCallExpr.args[0].inferType(program)
            if(t1.isLong) {
                val replaceFunc = func[0]+"__long"
                return listOf(AstSetExpression({functionCallExpr.target = it as IdentifierReference},
                    IdentifierReference(listOf(replaceFunc), functionCallExpr.target.position),
                    functionCallExpr))
            }
        }
        if(func==listOf("divmod")) {
            val argTypes = functionCallExpr.args.mapTo(mutableSetOf()) { it.inferType(program) }
            if(argTypes.size!=1) {
                errors.err("expected all ubyte/uword or all byte/word arguments", functionCallExpr.args[0].position)
                return noModifications
            }
            val t1 = argTypes.single()
            if(t1.isKnown) {
                val dt = t1.getOrElse { throw InternalCompilerException("invalid dt") }
                val replaceFunc = when {
                    dt.isUnsignedByte -> "divmod__ubyte"
                    dt.isUnsignedWord -> "divmod__uword"
                    dt.isSignedByte -> "divmod__byte"
                    dt.isSignedWord -> "divmod__word"
                    else -> {
                        errors.err("expected all ubyte/uword or all byte/word arguments", functionCallExpr.args[0].position)
                        return noModifications
                    }
                }
                return listOf(AstSetExpression({functionCallExpr.target = it as IdentifierReference},
                    IdentifierReference(listOf(replaceFunc), functionCallExpr.target.position),
                    functionCallExpr))
            }
        }
        return noModifications
    }
}


// Replace all constant identifiers with their actual value,
// This is needed because further constant optimizations depend on those.
internal class ConstantIdentifierReplacer(
    private val program: Program,
    private val errors: IErrorReporter
) : AstWalker() {

    override fun before(addressOf: AddressOf, parent: Node): Iterable<AstModification> {
        val constValue = addressOf.constValue(program)
        if(constValue!=null) {
            return listOf(AstReplaceNode(addressOf, constValue, parent))
        }
        return noModifications
    }

    override fun after(identifier: IdentifierReference, parent: Node): Iterable<AstModification> {
        // replace identifiers that refer to const value, with the value itself
        // if it's a simple type and if it's not a left hand side variable
        if (identifier.parent is AssignTarget || identifier.parent is Alias)
            return noModifications
        var forloop = identifier.parent as? ForLoop
        if (forloop == null)
            forloop = identifier.parent.parent as? ForLoop
        if (forloop != null && identifier === forloop.loopVar)
            return noModifications

        val dt = identifier.inferType(program)
        if (!dt.isKnown || !(dt.isNumeric || dt.isBool || dt.isPointer))
            return noModifications

        // Special case: CONST variable holding memory() call - replace with the FunctionCallExpression itself
        // This must be checked BEFORE constValue() since memory() returns null from constValue()
        val targetNode = identifier.definingScope.lookup(identifier.nameInSource)
        if (targetNode is VarDecl && targetNode.type == VarDeclType.CONST &&
            targetNode.value is FunctionCallExpression &&
            (targetNode.value as FunctionCallExpression).target.nameInSource == listOf("memory")
        ) {
            // Replace IdentifierReference with the memory() FunctionCallExpression
            // Update position to match the identifier's position (where it's used), not the original declaration
            val memCall = targetNode.value as FunctionCallExpression
            val memCallWithNewPos = FunctionCallExpression(
                memCall.target.copy(),
                memCall.args.map { it.copy() }.toMutableList(),
                identifier.position  // Use the position where it's referenced, not where it was declared
            )
            return listOf(
                AstReplaceNode(
                    identifier,
                    memCallWithNewPos,
                    identifier.parent
                )
            )
        }

        // handle const pointers
        if (targetNode is VarDecl && targetNode.type == VarDeclType.CONST) {
            val pointerValue = targetNode.value?.constValue(program)!!
            if (isPartOfPointerArithmetic(identifier, dt)) {
                // pointer is part of pointer arithmetic expression, must evaluate it.
                // TODO do we do this here, or postpone it to ConstantFoldingOptimizer?
                val binexpr = identifier.parent as? BinaryExpression
                if (binexpr != null) {
                    val constLeft = binexpr.left.constValue(program)
                    val constRight = binexpr.right.constValue(program)
                    when(binexpr.operator) {
                        "-" -> {
                            println("TODO: try to const-evaluate pointer arithmetic on a const pointer identifier: $identifier at ${identifier.position} pointer arithmetic expression = ${binexpr}")
                        }
                        "+" -> {
                            println("TODO: try to const-evaluate pointer arithmetic on a const pointer identifier: $identifier at ${identifier.position} pointer arithmetic expression = ${binexpr}")
                        }
                    }
                }
            } else {
                // pointer is not part of pointer arithmetic expression, can immediately replace it with its value
                return listOf(AstReplaceNode(identifier, NumericLiteral(BaseDataType.UWORD, pointerValue.number, identifier.position), parent))
            }
        }

        val cval = identifier.constValue(program) ?: return noModifications
        val arrayIdx = identifier.parent as? ArrayIndexedExpression
        
        if(arrayIdx!=null && cval.type.isNumeric) {
            // special case when the identifier is used as a pointer var
            // var = constpointer[x] --> var = @(constvalue+x) [directmemoryread]
            // constpointer[x] = var -> @(constvalue+x) [directmemorywrite] = var
            val add = BinaryExpression(NumericLiteral(cval.type, cval.number, identifier.position), "+", arrayIdx.indexer.indexExpr, identifier.position)
            return if(arrayIdx.parent is AssignTarget) {
                val memwrite = DirectMemoryWrite(add, identifier.position)
                val assignTarget = AssignTarget(null, null, memwrite, null, false, position = identifier.position)
                listOf(AstReplaceNode(arrayIdx.parent, assignTarget, arrayIdx.parent.parent))
            } else {
                val memread = DirectMemoryRead(add, identifier.position)
                listOf(AstReplaceNode(arrayIdx, memread, arrayIdx.parent))
            }
        }
        when {
            cval.type.isNumericOrBool -> {
                if(parent is AddressOf)
                    return noModifications      // cannot replace the identifier INSIDE the addr-of here, let's do it later.
                return listOf(
                    AstReplaceNode(
                        identifier,
                        NumericLiteral(cval.type, cval.number, identifier.position),
                        identifier.parent
                    )
                )
            }
            cval.type.isPointer -> {
                return listOf(
                    AstReplaceNode(
                        identifier,
                        NumericLiteral(BaseDataType.UWORD, cval.number!!, identifier.position),
                        identifier.parent
                    )
                )
            }
            cval.type.isPassByRef -> throw InternalCompilerException("pass-by-reference type should not be considered a constant")
            else -> return noModifications
        }
    }

    private fun isPartOfPointerArithmetic(identifier: IdentifierReference, dt: InferredTypes.InferredType): Boolean {
        if(dt.isPointer) {
            if (identifier.parent is ArrayIndexedExpression || identifier.parent is AddressOf)
                return true

            val binexpr = identifier.parent as? BinaryExpression
            if (binexpr != null)
                return binexpr.operator == "+" || binexpr.operator == "-"
        }
        
        return false
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<AstModification> {
        // the initializer value can't refer to the variable itself (recursive definition)
        if(decl.value?.referencesIdentifier(listOf(decl.name)) == true || decl.arraysize?.indexExpr?.referencesIdentifier(listOf(decl.name)) == true) {
            errors.err("recursive var declaration", decl.position)
            return noModifications
        }

        if(decl.isArray && decl.type==VarDeclType.MEMORY && decl.value !is IdentifierReference) {
            val memaddr = decl.value?.constValue(program)
            if(memaddr!=null && memaddr !== decl.value) {
                return listOf(AstSetExpression(
                    { decl.value = it }, memaddr, decl
                ))
            }
        }

        if(decl.type==VarDeclType.CONST || decl.type==VarDeclType.VAR) {
            if(decl.isArray){
                val arraysize = decl.arraysize
                if(arraysize==null) {
                    // for arrays that have no size specifier attempt to deduce the size
                    val arrayval = decl.value as? ArrayLiteral
                    if(arrayval!=null) {
                        return listOf(AstSetExpression(
                                { decl.arraysize = ArrayIndex(it, decl.position) },
                                NumericLiteral.optimalInteger(arrayval.value.size, decl.position),
                                decl
                        ))
                    }
                }
            }

            when {
                decl.datatype.isFloat -> {
                    // vardecl: for scalar float vars, promote constant integer initialization values to floats
                    val litval = decl.value as? NumericLiteral
                    if (litval!=null && litval.type.isIntegerOrBool) {
                        val newValue = NumericLiteral(BaseDataType.FLOAT, litval.number, litval.position)
                        return listOf(AstReplaceNode(decl.value!!, newValue, decl))
                    }
                }
                decl.datatype.isArray -> {
                    val replacedArrayInitializer = createConstArrayInitializerValue(decl)
                    if(replacedArrayInitializer!=null)
                        return listOf(AstReplaceNode(decl.value!!, replacedArrayInitializer, decl))
                }
                else -> {
                    // nothing to do for this type
                }
            }
        }

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<AstModification> {
        // convert a range expression that is assigned to an array, to an array literal instead.
        val range = assignment.value as? RangeExpression
        if(range!=null) {
            val targetDatatype = assignment.target.inferType(program)
            if(targetDatatype.isArray) {
                val decl = VarDecl.builder(targetDatatype.getOrUndef(), assignment.value.position)
                    .names("dummy")
                    .origin(VarDeclOrigin.ARRAYLITERAL)
                    .splitwordarray(SplitWish.NOSPLIT)
                    .value(assignment.value)
                    .build()
                val replaceValue = createConstArrayInitializerValue(decl)
                if(replaceValue!=null) {
                    return listOf(AstReplaceNode(assignment.value, replaceValue, assignment))
                }
            }
        }
        return noModifications
    }

    private fun createConstArrayInitializerValue(decl: VarDecl): ArrayLiteral? {

        if(decl.type==VarDeclType.MEMORY)
            return null     // memory mapped arrays can never have an initializer value other than the address where they're mapped.

        val rangeSize=(decl.value as? RangeExpression)?.size()
        if(rangeSize!=null && rangeSize>65535) {
            errors.err("range size overflow", decl.value!!.position)
            return null
        }

        val rangeExpr = decl.value as? RangeExpression ?: return null

        // convert the initializer range expression from a range, to an actual array literal.
        val declArraySize = decl.arraysize?.constIndex()
        val constRange = rangeExpr.toConstantIntegerRange()
        if(constRange?.isEmpty()==true) {
            if(constRange.first>constRange.last && constRange.step>=0)
                errors.err("descending range with positive step", decl.value?.position!!)
            else if(constRange.first<constRange.last && constRange.step<=0)
                errors.err("ascending range with negative step", decl.value?.position!!)
        }
        val dt = decl.datatype
        when {
            dt.isUnsignedByteArray || dt.isSignedByteArray || dt.isUnsignedWordArray || dt.isSignedWordArray -> {
                if(declArraySize!=null && declArraySize!=rangeExpr.size())
                    errors.err("range expression size (${rangeExpr.size()}) doesn't match declared array size ($declArraySize)", decl.value?.position!!)
                if(constRange!=null) {
                    val rangeType = rangeExpr.inferType(program).getOr(DataType.UBYTE)
                    return if(rangeType.isByte) {
                        ArrayLiteral(InferredTypes.InferredType.known(decl.datatype),
                            constRange.map { NumericLiteral(rangeType.base, it.toDouble(), decl.value!!.position) }.toTypedArray(),
                            position = decl.value!!.position)
                    } else {
                        require(rangeType.sub!=null)
                        ArrayLiteral(InferredTypes.InferredType.known(decl.datatype),
                            constRange.map { NumericLiteral(rangeType.sub!!, it.toDouble(), decl.value!!.position) }.toTypedArray(),
                            position = decl.value!!.position)
                    }
                }
            }
            dt.isFloatArray -> {
                if(declArraySize!=null && declArraySize!=rangeExpr.size())
                    errors.err("range expression size (${rangeExpr.size()}) doesn't match declared array size ($declArraySize)", decl.value?.position!!)
                if(constRange!=null) {
                    return ArrayLiteral(InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.FLOAT, false)),
                        constRange.map { NumericLiteral(BaseDataType.FLOAT, it.toDouble(), decl.value!!.position) }.toTypedArray(),
                        position = decl.value!!.position)
                }
            }
            else -> return null
        }
        return null
    }
}
