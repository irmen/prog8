package prog8.optimizer

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.base.UndefinedSymbolError
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*

// Fix up the literal value's type to match that of the vardecl
//   (also check range literal operands types before they get expanded into arrays for instance)
class VarConstantValueTypeAdjuster(private val program: Program, private val errors: IErrorReporter) : AstWalker() {

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {

        if(decl.parent is AnonymousScope)
            throw FatalAstException("vardecl may no longer occur in anonymousscope")

        try {
            val declConstValue = decl.value?.constValue(program)
            if(declConstValue!=null && (decl.type== VarDeclType.VAR || decl.type==VarDeclType.CONST)
                && declConstValue.type != decl.datatype) {
                // avoid silent float roundings
                if(decl.datatype in IntegerDatatypes && declConstValue.type == DataType.FLOAT) {
                    errors.err("refused rounding of float to avoid loss of precision", decl.value!!.position)
                } else {
                    // cast the numeric literal to the appropriate datatype of the variable
                    val cast = declConstValue.cast(decl.datatype)
                    if (cast.isValid)
                        return listOf(IAstModification.ReplaceNode(decl.value!!, cast.valueOrZero(), decl))
                }
            }
        } catch (x: UndefinedSymbolError) {
            errors.err(x.message, x.position)
        }

        return noModifications
    }

    override fun after(range: RangeExpression, parent: Node): Iterable<IAstModification> {
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

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        // choose specific builtin function for the given types
        val func = functionCallExpr.target.nameInSource
        if(func==listOf("clamp")) {
            val t1 = functionCallExpr.args[0].inferType(program)
            if(t1.isKnown) {
                val replaceFunc: String
                if(t1.isBytes) {
                    replaceFunc = if(t1.istype(DataType.BYTE)) "clamp__byte" else "clamp__ubyte"
                } else if(t1.isInteger) {
                    replaceFunc = if(t1.istype(DataType.WORD)) "clamp__word" else "clamp__uword"
                } else {
                    errors.err("clamp builtin not supported for floats, use floats.clamp", functionCallExpr.position)
                    return noModifications
                }
                return listOf(IAstModification.SetExpression({functionCallExpr.target = it as IdentifierReference},
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
                    replaceFunc = if(t1.istype(DataType.BYTE) || t2.istype(DataType.BYTE))
                        "${funcName}__byte"
                    else
                        "${funcName}__ubyte"
                } else if(t1.isInteger && t2.isInteger) {
                    replaceFunc = if(t1.istype(DataType.WORD) || t2.istype(DataType.WORD))
                        "${funcName}__word"
                    else
                        "${funcName}__uword"
                } else if(t1.isNumeric && t2.isNumeric) {
                    errors.err("min/max not supported for floats", functionCallExpr.position)
                    return noModifications
                } else {
                    errors.err("expected numeric arguments", functionCallExpr.args[0].position)
                    return noModifications
                }
                return listOf(IAstModification.SetExpression({functionCallExpr.target = it as IdentifierReference},
                    IdentifierReference(listOf(replaceFunc), functionCallExpr.target.position),
                    functionCallExpr))
            }
        }
        else if(func==listOf("abs")) {
            val t1 = functionCallExpr.args[0].inferType(program)
            if(t1.isKnown) {
                val dt = t1.getOrElse { throw InternalCompilerException("invalid dt") }
                val replaceFunc = when(dt) {
                    DataType.BYTE -> "abs__byte"
                    DataType.WORD -> "abs__word"
                    DataType.FLOAT -> "abs__float"
                    DataType.UBYTE, DataType.UWORD -> {
                        return listOf(IAstModification.ReplaceNode(functionCallExpr, functionCallExpr.args[0], parent))
                    }
                    else -> {
                        errors.err("expected numeric argument", functionCallExpr.args[0].position)
                        return noModifications
                    }
                }
                return listOf(IAstModification.SetExpression({functionCallExpr.target = it as IdentifierReference},
                    IdentifierReference(listOf(replaceFunc), functionCallExpr.target.position),
                    functionCallExpr))
            }
        }
        else if(func==listOf("sqrt")) {
            val t1 = functionCallExpr.args[0].inferType(program)
            if(t1.isKnown) {
                val dt = t1.getOrElse { throw InternalCompilerException("invalid dt") }
                val replaceFunc = when(dt) {
                    DataType.UBYTE -> "sqrt__ubyte"
                    DataType.UWORD -> "sqrt__uword"
                    DataType.FLOAT -> "sqrt__float"
                    else -> {
                        errors.err("expected unsigned or float numeric argument", functionCallExpr.args[0].position)
                        return noModifications
                    }
                }
                return listOf(IAstModification.SetExpression({functionCallExpr.target = it as IdentifierReference},
                    IdentifierReference(listOf(replaceFunc), functionCallExpr.target.position),
                    functionCallExpr))
            }
        }
        return noModifications
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        // choose specific builtin function for the given types
        val func = functionCallStatement.target.nameInSource
        if(func==listOf("divmod")) {
            val argTypes = functionCallStatement.args.map {it.inferType(program)}.toSet()
            if(argTypes.size!=1) {
                errors.err("expected all ubyte or all uword arguments", functionCallStatement.args[0].position)
                return noModifications
            }
            val t1 = argTypes.single()
            if(t1.isKnown) {
                val dt = t1.getOrElse { throw InternalCompilerException("invalid dt") }
                val replaceFunc = when(dt) {
                    DataType.UBYTE -> "divmod__ubyte"
                    DataType.UWORD -> "divmod__uword"
                    else -> {
                        errors.err("expected all ubyte or all uword arguments", functionCallStatement.args[0].position)
                        return noModifications
                    }
                }
                return listOf(IAstModification.SetExpression({functionCallStatement.target = it as IdentifierReference},
                    IdentifierReference(listOf(replaceFunc), functionCallStatement.target.position),
                    functionCallStatement))
            }
        }
        return noModifications
    }
}


// Replace all constant identifiers with their actual value,
// and the array var initializer values and sizes.
// This is needed because further constant optimizations depend on those.
internal class ConstantIdentifierReplacer(private val program: Program, private val errors: IErrorReporter, private val compTarget: ICompilationTarget) : AstWalker() {

    override fun after(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> {
        // replace identifiers that refer to const value, with the value itself
        // if it's a simple type and if it's not a left hand side variable
        if(identifier.parent is AssignTarget)
            return noModifications
        var forloop = identifier.parent as? ForLoop
        if(forloop==null)
            forloop = identifier.parent.parent as? ForLoop
        if(forloop!=null && identifier===forloop.loopVar)
            return noModifications

        val dt = identifier.inferType(program)
        if(!dt.isKnown || !dt.isNumeric)
            return noModifications

        try {
            val cval = identifier.constValue(program) ?: return noModifications
            val arrayIdx = identifier.parent as? ArrayIndexedExpression
            if(arrayIdx!=null && cval.type in NumericDatatypes) {
                // special case when the identifier is used as a pointer var
                // var = constpointer[x] --> var = @(constvalue+x) [directmemoryread]
                // constpointer[x] = var -> @(constvalue+x) [directmemorywrite] = var
                val add = BinaryExpression(NumericLiteral(cval.type, cval.number, identifier.position), "+", arrayIdx.indexer.indexExpr, identifier.position)
                return if(arrayIdx.parent is AssignTarget) {
                    val memwrite = DirectMemoryWrite(add, identifier.position)
                    val assignTarget = AssignTarget(null, null, memwrite, identifier.position)
                    listOf(IAstModification.ReplaceNode(arrayIdx.parent, assignTarget, arrayIdx.parent.parent))
                } else {
                    val memread = DirectMemoryRead(add, identifier.position)
                    listOf(IAstModification.ReplaceNode(arrayIdx, memread, arrayIdx.parent))
                }
            }
            return when (cval.type) {
                in NumericDatatypes -> listOf(
                    IAstModification.ReplaceNode(
                        identifier,
                        NumericLiteral(cval.type, cval.number, identifier.position),
                        identifier.parent
                    )
                )
                in PassByReferenceDatatypes -> throw InternalCompilerException("pass-by-reference type should not be considered a constant")
                else -> noModifications
            }
        } catch (x: UndefinedSymbolError) {
            errors.err(x.message, x.position)
            return noModifications
        }
    }

    override fun before(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        // the initializer value can't refer to the variable itself (recursive definition)
        if(decl.value?.referencesIdentifier(listOf(decl.name)) == true || decl.arraysize?.indexExpr?.referencesIdentifier(listOf(decl.name)) == true) {
            errors.err("recursive var declaration", decl.position)
            return noModifications
        }

        if(decl.isArray && decl.type==VarDeclType.MEMORY) {
            val memaddr = decl.value?.constValue(program)
            if(memaddr!=null && memaddr !== decl.value) {
                return listOf(IAstModification.SetExpression(
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
                        return listOf(IAstModification.SetExpression(
                                { decl.arraysize = ArrayIndex(it, decl.position) },
                                NumericLiteral.optimalInteger(arrayval.value.size, decl.position),
                                decl
                        ))
                    }
                }
            }

            when(decl.datatype) {
                DataType.FLOAT -> {
                    // vardecl: for scalar float vars, promote constant integer initialization values to floats
                    val litval = decl.value as? NumericLiteral
                    if (litval!=null && litval.type in IntegerDatatypes) {
                        val newValue = NumericLiteral(DataType.FLOAT, litval.number, litval.position)
                        return listOf(IAstModification.ReplaceNode(decl.value!!, newValue, decl))
                    }
                }
                in ArrayDatatypes -> {
                    val replacedArrayInitializer = createConstArrayInitializerValue(decl)
                    if(replacedArrayInitializer!=null)
                        return listOf(IAstModification.ReplaceNode(decl.value!!, replacedArrayInitializer, decl))
                }
                else -> {
                    // nothing to do for this type
                }
            }
        }

        return noModifications
    }

    private fun createConstArrayInitializerValue(decl: VarDecl): ArrayLiteral? {

        if(decl.type==VarDeclType.MEMORY)
            return null     // memory mapped arrays can never have an initializer value other than the address where they're mapped.

        // convert the initializer range expression from a range or int, to an actual array.
        when(decl.datatype) {
            DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W, DataType.ARRAY_W_SPLIT, DataType.ARRAY_UW_SPLIT -> {
                val rangeExpr = decl.value as? RangeExpression
                if(rangeExpr!=null) {
                    val constRange = rangeExpr.toConstantIntegerRange()
                    if(constRange?.isEmpty()==true) {
                        if(constRange.first>constRange.last && constRange.step>=0)
                            errors.err("descending range with positive step", decl.value?.position!!)
                        else if(constRange.first<constRange.last && constRange.step<=0)
                            errors.err("ascending range with negative step", decl.value?.position!!)
                    }
                    val declArraySize = decl.arraysize?.constIndex()
                    if(declArraySize!=null && declArraySize!=rangeExpr.size())
                        errors.err("range expression size (${rangeExpr.size()}) doesn't match declared array size ($declArraySize)", decl.value?.position!!)
                    if(constRange!=null) {
                        val eltType = rangeExpr.inferType(program).getOr(DataType.UBYTE)
                        return if(eltType in ByteDatatypes) {
                            ArrayLiteral(InferredTypes.InferredType.known(decl.datatype),
                                constRange.map { NumericLiteral(eltType, it.toDouble(), decl.value!!.position) }.toTypedArray(),
                                position = decl.value!!.position)
                        } else {
                            ArrayLiteral(InferredTypes.InferredType.known(decl.datatype),
                                constRange.map { NumericLiteral(eltType, it.toDouble(), decl.value!!.position) }.toTypedArray(),
                                position = decl.value!!.position)
                        }
                    }
                }
                val numericLv = decl.value as? NumericLiteral
                if(numericLv!=null && numericLv.type== DataType.FLOAT)
                    errors.err("arraysize requires only integers here", numericLv.position)
                val size = decl.arraysize?.constIndex() ?: return null
                if (rangeExpr==null && numericLv!=null) {
                    // arraysize initializer is empty or a single int, and we know the size; create the arraysize.
                    val fillvalue = numericLv.number.toInt()
                    when(decl.datatype){
                        DataType.ARRAY_UB -> {
                            if(fillvalue !in 0..255)
                                errors.err("ubyte value overflow", numericLv.position)
                        }
                        DataType.ARRAY_B -> {
                            if(fillvalue !in -128..127)
                                errors.err("byte value overflow", numericLv.position)
                        }
                        DataType.ARRAY_UW -> {
                            if(fillvalue !in 0..65535)
                                errors.err("uword value overflow", numericLv.position)
                        }
                        DataType.ARRAY_W -> {
                            if(fillvalue !in -32768..32767)
                                errors.err("word value overflow", numericLv.position)
                        }
                        else -> {}
                    }
                    // create the array itself, filled with the fillvalue.
                    val array = Array(size) {fillvalue}.map { NumericLiteral(ArrayToElementTypes.getValue(decl.datatype), it.toDouble(), numericLv.position) }.toTypedArray<Expression>()
                    return ArrayLiteral(InferredTypes.InferredType.known(decl.datatype), array, position = numericLv.position)
                }
            }
            DataType.ARRAY_F -> {
                val rangeExpr = decl.value as? RangeExpression
                if(rangeExpr!=null) {
                    // convert the initializer range expression to an actual array of floats
                    val declArraySize = decl.arraysize?.constIndex()
                    if(declArraySize!=null && declArraySize!=rangeExpr.size())
                        errors.err("range expression size (${rangeExpr.size()}) doesn't match declared array size ($declArraySize)", decl.value?.position!!)
                    val constRange = rangeExpr.toConstantIntegerRange()
                    if(constRange!=null) {
                        return ArrayLiteral(InferredTypes.InferredType.known(DataType.ARRAY_F),
                            constRange.map { NumericLiteral(DataType.FLOAT, it.toDouble(), decl.value!!.position) }.toTypedArray(),
                            position = decl.value!!.position)
                    }
                }

                val numericLv = decl.value as? NumericLiteral
                val size = decl.arraysize?.constIndex() ?: return null
                if(rangeExpr==null && numericLv!=null) {
                    // arraysize initializer is a single int, and we know the size.
                    val fillvalue = numericLv.number
                    if (fillvalue < compTarget.machine.FLOAT_MAX_NEGATIVE || fillvalue > compTarget.machine.FLOAT_MAX_POSITIVE)
                        errors.err("float value overflow", numericLv.position)
                    else {
                        // create the array itself, filled with the fillvalue.
                        val array = Array(size) {fillvalue}.map { NumericLiteral(DataType.FLOAT, it, numericLv.position) }.toTypedArray<Expression>()
                        return ArrayLiteral(InferredTypes.InferredType.known(DataType.ARRAY_F), array, position = numericLv.position)
                    }
                }
            }
            DataType.ARRAY_BOOL -> {
                val numericLv = decl.value as? NumericLiteral
                val size = decl.arraysize?.constIndex() ?: return null
                if(numericLv!=null) {
                    // arraysize initializer is a single int, and we know the size.
                    val fillvalue = if(numericLv.number==0.0) 0.0 else 1.0
                    val array = Array(size) {fillvalue}.map { NumericLiteral(DataType.UBYTE, fillvalue, numericLv.position) }.toTypedArray<Expression>()
                    return ArrayLiteral(InferredTypes.InferredType.known(DataType.ARRAY_BOOL), array, position = numericLv.position)
                }
            }
            else -> return null
        }
        return null
    }
}
