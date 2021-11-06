package prog8.optimizer

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compilerinterface.ICompilationTarget
import prog8.compilerinterface.IErrorReporter
import prog8.compilerinterface.size
import prog8.compilerinterface.toConstantIntegerRange

// Fix up the literal value's type to match that of the vardecl
//   (also check range literal operands types before they get expanded into arrays for instance)
class VarConstantValueTypeAdjuster(private val program: Program, private val errors: IErrorReporter) : AstWalker() {

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {

        if(decl.parent is AnonymousScope)
            throw FatalAstException("vardecl may no longer occur in anonymousscope")

        try {
            val declConstValue = decl.value?.constValue(program)
            if(declConstValue!=null && (decl.type==VarDeclType.VAR || decl.type==VarDeclType.CONST)
                && declConstValue.inferType(program) isnot decl.datatype) {
                // cast the numeric literal to the appropriate datatype of the variable
                val cast = declConstValue.cast(decl.datatype)
                if(cast.isValid)
                    return listOf(IAstModification.ReplaceNode(decl.value!!, cast.valueOrZero(), decl))
            }
        } catch (x: UndefinedSymbolError) {
            errors.err(x.message, x.position)
        }

        return noModifications
    }

    override fun after(range: RangeExpr, parent: Node): Iterable<IAstModification> {
        val from = range.from.constValue(program)?.number?.toDouble()
        val to = range.to.constValue(program)?.number?.toDouble()
        val step = range.step.constValue(program)?.number?.toDouble()

        if(from==null) {
            if(!range.from.inferType(program).isInteger)
                errors.err("range expression from value must be integer", range.from.position)
        } else if(from-from.toInt()>0) {
            errors.err("range expression from value must be integer", range.from.position)
        }

        if(to==null) {
            if(!range.to.inferType(program).isInteger)
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

        try {
            val cval = identifier.constValue(program) ?: return noModifications
            return when (cval.type) {
                in NumericDatatypes -> listOf(
                    IAstModification.ReplaceNode(
                        identifier,
                        NumericLiteralValue(cval.type, cval.number, identifier.position),
                        identifier.parent
                    )
                )
                in PassByReferenceDatatypes -> throw FatalAstException("pass-by-reference type should not be considered a constant")
                else -> noModifications
            }
        } catch (x: UndefinedSymbolError) {
            errors.err(x.message, x.position)
            return noModifications
        }
    }

    override fun before(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        // the initializer value can't refer to the variable itself (recursive definition)
        // TODO: use call graph for this?
        if(decl.value?.referencesIdentifier(decl.name) == true || decl.arraysize?.indexExpr?.referencesIdentifier(decl.name) == true) {
            errors.err("recursive var declaration", decl.position)
            return noModifications
        }

        if(decl.type== VarDeclType.CONST || decl.type== VarDeclType.VAR) {
            if(decl.isArray){
                val arraysize = decl.arraysize
                if(arraysize==null) {
                    // for arrays that have no size specifier attempt to deduce the size
                    val arrayval = decl.value as? ArrayLiteralValue
                    if(arrayval!=null) {
                        return listOf(IAstModification.SetExpression(
                                { decl.arraysize = ArrayIndex(it, decl.position) },
                                NumericLiteralValue.optimalInteger(arrayval.value.size, decl.position),
                                decl
                        ))
                    }
                }
            }

            when(decl.datatype) {
                DataType.FLOAT -> {
                    // vardecl: for scalar float vars, promote constant integer initialization values to floats
                    val litval = decl.value as? NumericLiteralValue
                    if (litval!=null && litval.type in IntegerDatatypes) {
                        val newValue = NumericLiteralValue(DataType.FLOAT, litval.number.toDouble(), litval.position)
                        return listOf(IAstModification.ReplaceNode(decl.value!!, newValue, decl))
                    }
                }
                DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W -> {
                    val rangeExpr = decl.value as? RangeExpr
                    if(rangeExpr!=null) {
                        // convert the initializer range expression to an actual array
                        val declArraySize = decl.arraysize?.constIndex()
                        if(declArraySize!=null && declArraySize!=rangeExpr.size())
                            errors.err("range expression size doesn't match declared array size", decl.value?.position!!)
                        val constRange = rangeExpr.toConstantIntegerRange()
                        if(constRange!=null) {
                            val eltType = rangeExpr.inferType(program).getOr(DataType.UBYTE)
                            val newValue = if(eltType in ByteDatatypes) {
                                ArrayLiteralValue(InferredTypes.InferredType.known(decl.datatype),
                                        constRange.map { NumericLiteralValue(eltType, it.toShort(), decl.value!!.position) }.toTypedArray(),
                                        position = decl.value!!.position)
                            } else {
                                ArrayLiteralValue(InferredTypes.InferredType.known(decl.datatype),
                                        constRange.map { NumericLiteralValue(eltType, it, decl.value!!.position) }.toTypedArray(),
                                        position = decl.value!!.position)
                            }
                            return listOf(IAstModification.ReplaceNode(decl.value!!, newValue, decl))
                        }
                    }
                    val numericLv = decl.value as? NumericLiteralValue
                    if(numericLv!=null && numericLv.type== DataType.FLOAT)
                        errors.err("arraysize requires only integers here", numericLv.position)
                    val size = decl.arraysize?.constIndex() ?: return noModifications
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
                        val array = Array(size) {fillvalue}.map { NumericLiteralValue(ArrayToElementTypes.getValue(decl.datatype), it, numericLv.position) }.toTypedArray<Expression>()
                        val refValue = ArrayLiteralValue(InferredTypes.InferredType.known(decl.datatype), array, position = numericLv.position)
                        return listOf(IAstModification.ReplaceNode(decl.value!!, refValue, decl))
                    }
                }
                DataType.ARRAY_F -> {
                    val rangeExpr = decl.value as? RangeExpr
                    if(rangeExpr!=null) {
                        // convert the initializer range expression to an actual array of floats
                        val declArraySize = decl.arraysize?.constIndex()
                        if(declArraySize!=null && declArraySize!=rangeExpr.size())
                            errors.err("range expression size (${rangeExpr.size()}) doesn't match declared array size ($declArraySize)", decl.value?.position!!)
                        val constRange = rangeExpr.toConstantIntegerRange()
                        if(constRange!=null) {
                            val newValue = ArrayLiteralValue(InferredTypes.InferredType.known(DataType.ARRAY_F),
                                    constRange.map { NumericLiteralValue(DataType.FLOAT, it.toDouble(), decl.value!!.position) }.toTypedArray(),
                                    position = decl.value!!.position)
                            return listOf(IAstModification.ReplaceNode(decl.value!!, newValue, decl))
                        }
                    }

                    val numericLv = decl.value as? NumericLiteralValue
                    val size = decl.arraysize?.constIndex() ?: return noModifications
                    if(rangeExpr==null && numericLv!=null) {
                        // arraysize initializer is a single int, and we know the size.
                        val fillvalue = numericLv.number.toDouble()
                        if (fillvalue < compTarget.machine.FLOAT_MAX_NEGATIVE || fillvalue > compTarget.machine.FLOAT_MAX_POSITIVE)
                            errors.err("float value overflow", numericLv.position)
                        else {
                            // create the array itself, filled with the fillvalue.
                            val array = Array(size) {fillvalue}.map { NumericLiteralValue(DataType.FLOAT, it, numericLv.position) }.toTypedArray<Expression>()
                            val refValue = ArrayLiteralValue(InferredTypes.InferredType.known(DataType.ARRAY_F), array, position = numericLv.position)
                            return listOf(IAstModification.ReplaceNode(decl.value!!, refValue, decl))
                        }
                    }
                }
                else -> {
                    // nothing to do for this type
                }
            }
        }

        return noModifications
    }
}
