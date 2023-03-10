package prog8.optimizer

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*
import prog8.code.target.VMTarget
import kotlin.math.floor


class StatementOptimizer(private val program: Program,
                         private val errors: IErrorReporter,
                         private val functions: IBuiltinFunctions,
                         private val compTarget: ICompilationTarget
) : AstWalker() {

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        if(functionCallStatement.target.nameInSource.size==1) {
            val functionName = functionCallStatement.target.nameInSource[0]
            if (functionName in functions.purefunctionNames) {
                errors.warn("statement has no effect (function return value is discarded)", functionCallStatement.position)
                return listOf(IAstModification.Remove(functionCallStatement, parent as IStatementContainer))
            }
        }

        // printing a literal string of just 2 or 1 characters is replaced by directly outputting those characters
        // only do this optimization if the arg is a known-constant string literal instead of a user defined variable.
        if(functionCallStatement.target.nameInSource==listOf("txt", "print")) {
            val arg = functionCallStatement.args.single()
            val stringVar: IdentifierReference? = if(arg is AddressOf) {
                arg.identifier
            } else {
                arg as? IdentifierReference
            }
            if(stringVar!=null && stringVar.wasStringLiteral(program)) {
                val string = stringVar.targetVarDecl(program)?.value as? StringLiteral
                if(string!=null) {
                    val pos = functionCallStatement.position
                    if (string.value.length == 1) {
                        val firstCharEncoded = compTarget.encodeString(string.value, string.encoding)[0]
                        val chrout = FunctionCallStatement(
                            IdentifierReference(listOf("txt", "chrout"), pos),
                            mutableListOf(NumericLiteral(DataType.UBYTE, firstCharEncoded.toDouble(), pos)),
                            functionCallStatement.void, pos
                        )
                        val stringDecl = string.parent as VarDecl
                        return listOf(
                            IAstModification.ReplaceNode(functionCallStatement, chrout, parent),
                            IAstModification.Remove(stringDecl, stringDecl.parent as IStatementContainer)
                        )
                    } else if (string.value.length == 2) {
                        val firstTwoCharsEncoded = compTarget.encodeString(string.value.take(2), string.encoding)
                        val chrout1 = FunctionCallStatement(
                            IdentifierReference(listOf("txt", "chrout"), pos),
                            mutableListOf(NumericLiteral(DataType.UBYTE, firstTwoCharsEncoded[0].toDouble(), pos)),
                            functionCallStatement.void, pos
                        )
                        val chrout2 = FunctionCallStatement(
                            IdentifierReference(listOf("txt", "chrout"), pos),
                            mutableListOf(NumericLiteral(DataType.UBYTE, firstTwoCharsEncoded[1].toDouble(), pos)),
                            functionCallStatement.void, pos
                        )
                        val stringDecl = string.parent as VarDecl
                        return listOf(
                            IAstModification.InsertBefore(functionCallStatement, chrout1, parent as IStatementContainer),
                            IAstModification.ReplaceNode(functionCallStatement, chrout2, parent),
                            IAstModification.Remove(stringDecl, stringDecl.parent as IStatementContainer)
                        )
                    }
                }
            }
        }

        return noModifications
    }

    override fun after(ifElse: IfElse, parent: Node): Iterable<IAstModification> {
        // remove empty if statements
        if(ifElse.truepart.isEmpty() && ifElse.elsepart.isEmpty())
            return listOf(IAstModification.Remove(ifElse, parent as IStatementContainer))

        // empty true part? switch with the else part
        if(ifElse.truepart.isEmpty() && ifElse.elsepart.isNotEmpty()) {
            val invertedCondition = BinaryExpression(ifElse.condition, "==", NumericLiteral(DataType.UBYTE, 0.0, ifElse.condition.position), ifElse.condition.position)
            val emptyscope = AnonymousScope(mutableListOf(), ifElse.elsepart.position)
            val truepart = AnonymousScope(ifElse.elsepart.statements, ifElse.truepart.position)
            return listOf(
                    IAstModification.ReplaceNode(ifElse.condition, invertedCondition, ifElse),
                    IAstModification.ReplaceNode(ifElse.truepart, truepart, ifElse),
                    IAstModification.ReplaceNode(ifElse.elsepart, emptyscope, ifElse)
            )
        }

        val constvalue = ifElse.condition.constValue(program)
        if(constvalue!=null) {
            return if(constvalue.asBooleanValue){
                // always true -> keep only if-part
                if(!ifElse.definingModule.isLibrary)
                    errors.warn("condition is always true", ifElse.condition.position)
                listOf(IAstModification.ReplaceNode(ifElse, ifElse.truepart, parent))
            } else {
                // always false -> keep only else-part
                if(!ifElse.definingModule.isLibrary)
                    errors.warn("condition is always false", ifElse.condition.position)
                listOf(IAstModification.ReplaceNode(ifElse, ifElse.elsepart, parent))
            }
        }

        return noModifications
    }

    override fun after(forLoop: ForLoop, parent: Node): Iterable<IAstModification> {
        if(forLoop.body.isEmpty()) {
            errors.warn("removing empty for loop", forLoop.position)
            return listOf(IAstModification.Remove(forLoop, parent as IStatementContainer))
        } else if(forLoop.body.statements.size==1) {
            val loopvar = forLoop.body.statements[0] as? VarDecl
            if(loopvar!=null && loopvar.name==forLoop.loopVar.nameInSource.singleOrNull()) {
                // remove empty for loop (only loopvar decl in it)
                return listOf(IAstModification.Remove(forLoop, parent as IStatementContainer))
            }
        }

        val range = forLoop.iterable as? RangeExpression
        if(range!=null) {
            if (range.size() == 1) {
                // for loop over a (constant) range of just a single value-- optimize the loop away
                // loopvar/reg = range value , follow by block
                val scope = AnonymousScope(mutableListOf(), forLoop.position)
                scope.statements.add(Assignment(AssignTarget(forLoop.loopVar, null, null, forLoop.position), range.from, AssignmentOrigin.OPTIMIZER, forLoop.position))
                scope.statements.addAll(forLoop.body.statements)
                return listOf(IAstModification.ReplaceNode(forLoop, scope, parent))
            }
        }
        val iterable = (forLoop.iterable as? IdentifierReference)?.targetVarDecl(program)
        if(iterable!=null) {
            if(iterable.datatype==DataType.STR) {
                val sv = iterable.value as StringLiteral
                val size = sv.value.length
                if(size==1) {
                    // loop over string of length 1 -> just assign the single character
                    val character = compTarget.encodeString(sv.value, sv.encoding)[0]
                    val byte = NumericLiteral(DataType.UBYTE, character.toDouble(), iterable.position)
                    val scope = AnonymousScope(mutableListOf(), forLoop.position)
                    scope.statements.add(Assignment(AssignTarget(forLoop.loopVar, null, null, forLoop.position), byte, AssignmentOrigin.OPTIMIZER, forLoop.position))
                    scope.statements.addAll(forLoop.body.statements)
                    return listOf(IAstModification.ReplaceNode(forLoop, scope, parent))
                }
            }
            else if(iterable.datatype in ArrayDatatypes) {
                val size = iterable.arraysize!!.constIndex()
                if(size==1) {
                    // loop over array of length 1 -> just assign the single value
                    val av = (iterable.value as ArrayLiteral).value[0].constValue(program)?.number
                    if(av!=null) {
                        val scope = AnonymousScope(mutableListOf(), forLoop.position)
                        scope.statements.add(Assignment(
                                AssignTarget(forLoop.loopVar, null, null, forLoop.position), NumericLiteral.optimalInteger(av.toInt(), iterable.position),
                                AssignmentOrigin.OPTIMIZER, forLoop.position))
                        scope.statements.addAll(forLoop.body.statements)
                        return listOf(IAstModification.ReplaceNode(forLoop, scope, parent))
                    }
                }
            }
        }

        return noModifications
    }

    override fun before(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> {
        val constvalue = untilLoop.condition.constValue(program)
        if(constvalue!=null) {
            return if(constvalue.asBooleanValue) {
                // always true -> keep only the statement block
                errors.warn("condition is always true", untilLoop.condition.position)
                listOf(IAstModification.ReplaceNode(untilLoop, untilLoop.body, parent))
            } else {
                // always false
                val forever = RepeatLoop(null, untilLoop.body, untilLoop.position)
                listOf(IAstModification.ReplaceNode(untilLoop, forever, parent))
            }
        }
        return noModifications
    }

    override fun before(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {
        val constvalue = whileLoop.condition.constValue(program)
        if(constvalue!=null) {
            return if(constvalue.asBooleanValue) {
                // always true
                val forever = RepeatLoop(null, whileLoop.body, whileLoop.position)
                listOf(IAstModification.ReplaceNode(whileLoop, forever, parent))
            } else {
                // always false -> remove the while statement altogether
                errors.warn("condition is always false", whileLoop.condition.position)
                listOf(IAstModification.Remove(whileLoop, parent as IStatementContainer))
            }
        }
        return noModifications
    }

    override fun after(repeatLoop: RepeatLoop, parent: Node): Iterable<IAstModification> {
        val iter = repeatLoop.iterations
        if(iter!=null) {
            if(repeatLoop.body.isEmpty()) {
                errors.warn("empty loop removed", repeatLoop.position)
                return listOf(IAstModification.Remove(repeatLoop, parent as IStatementContainer))
            }
            val iterations = iter.constValue(program)?.number?.toInt()
            if (iterations == 0) {
                errors.warn("iterations is always 0, removed loop", iter.position)
                return listOf(IAstModification.Remove(repeatLoop, parent as IStatementContainer))
            }
            if (iterations == 1) {
                errors.warn("iterations is always 1", iter.position)
                return listOf(IAstModification.ReplaceNode(repeatLoop, repeatLoop.body, parent))
            }
        }
        return noModifications
    }

    override fun before(assignment: Assignment, parent: Node): Iterable<IAstModification> {

        val binExpr = assignment.value as? BinaryExpression
        if(binExpr!=null) {
            if(binExpr.left isSameAs assignment.target) {
                val rExpr = binExpr.right as? BinaryExpression
                if(rExpr!=null) {
                    val op1 = binExpr.operator
                    val op2 = rExpr.operator

                    if(rExpr.left is NumericLiteral && op2 in AssociativeOperators && maySwapOperandOrder(binExpr)) {
                        // associative operator, make sure the constant numeric value is second (right)
                        return listOf(IAstModification.SwapOperands(rExpr))
                    }

                    val rNum = (rExpr.right as? NumericLiteral)?.number
                    if(rNum!=null) {
                        if (op1 == "+" || op1 == "-") {
                            if (op2 == "+") {
                                // A = A +/- B + N  --->  A = A +/- B  ;  A = A + N
                                val expr2 = BinaryExpression(binExpr.left, binExpr.operator, rExpr.left, binExpr.position)
                                val addConstant = Assignment(
                                        assignment.target.copy(),
                                        BinaryExpression(binExpr.left.copy(), "+", rExpr.right, rExpr.position),
                                        AssignmentOrigin.OPTIMIZER, assignment.position
                                )
                                return listOf(
                                        IAstModification.ReplaceNode(binExpr, expr2, binExpr.parent),
                                        IAstModification.InsertAfter(assignment, addConstant, parent as IStatementContainer))
                            } else if (op2 == "-") {
                                // A = A +/- B - N  --->  A = A +/- B  ;  A = A - N
                                val expr2 = BinaryExpression(binExpr.left, binExpr.operator, rExpr.left, binExpr.position)
                                val subConstant = Assignment(
                                        assignment.target.copy(),
                                        BinaryExpression(binExpr.left.copy(), "-", rExpr.right, rExpr.position),
                                        AssignmentOrigin.OPTIMIZER, assignment.position
                                )
                                return listOf(
                                        IAstModification.ReplaceNode(binExpr, expr2, binExpr.parent),
                                        IAstModification.InsertAfter(assignment, subConstant, parent as IStatementContainer))
                            }
                        }
                    }
                }
            }

            if(binExpr.operator in AssociativeOperators && binExpr.right isSameAs assignment.target) {
                // associative operator, swap the operands so that the assignment target is first (left)
                // unless the other operand is the same in which case we don't swap (endless loop!)
                if (!(binExpr.left isSameAs binExpr.right) && maySwapOperandOrder(binExpr))
                    return listOf(IAstModification.SwapOperands(binExpr))
            }

        }

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        if(assignment.target isSameAs assignment.value) {
            // remove assignment to self
            return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
        }

        val targetIDt = assignment.target.inferType(program)
        if(!targetIDt.isKnown)
            return noModifications

        // optimize binary expressions a bit
        val bexpr=assignment.value as? BinaryExpression
        if(bexpr!=null) {
            val rightCv = bexpr.right.constValue(program)?.number
            if(bexpr.operator=="-" && rightCv==null && targetIDt.isInteger) {
                if(bexpr.right.isSimple && bexpr.right isSameAs assignment.target) {
                    // X = value - X  -->  X = -X ; X += value  (to avoid need of stack-evaluation, for integers)
                    val negation = PrefixExpression("-", bexpr.right.copy(), bexpr.position)
                    val addValue = Assignment(assignment.target.copy(), BinaryExpression(bexpr.right, "+", bexpr.left, bexpr.position), AssignmentOrigin.OPTIMIZER, assignment.position)
                    return listOf(
                        IAstModification.ReplaceNode(bexpr, negation, assignment),
                        IAstModification.InsertAfter(assignment, addValue, parent as IStatementContainer)
                    )
                }
            }

            if (rightCv != null && assignment.target isSameAs bexpr.left) {
                // assignments of the form:  X = X <operator> <expr>
                // remove assignments that have no effect (such as X=X+0)
                // optimize/rewrite some other expressions
                val targetDt = targetIDt.getOr(DataType.UNDEFINED)
                val vardeclDt = (assignment.target.identifier?.targetVarDecl(program))?.type
                when (bexpr.operator) {
                    "+" -> {
                        if (rightCv == 0.0) {
                            return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
                        } else if (targetDt in IntegerDatatypes && floor(rightCv) == rightCv) {
                            if (vardeclDt != VarDeclType.MEMORY && rightCv in 1.0..3.0 && compTarget.name!=VMTarget.NAME) {
                                // replace by several INCs if it's not a memory address (inc on a memory mapped register doesn't work very well)
                                val incs = AnonymousScope(mutableListOf(), assignment.position)
                                repeat(rightCv.toInt()) {
                                    incs.statements.add(PostIncrDecr(assignment.target.copy(), "++", assignment.position))
                                }
                                return listOf(IAstModification.ReplaceNode(assignment, if(incs.statements.size==1) incs.statements[0] else incs, parent))
                            }
                        }
                    }
                    "-" -> {
                        if (rightCv == 0.0) {
                            return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
                        } else if (targetDt in IntegerDatatypes && floor(rightCv) == rightCv) {
                            if (vardeclDt != VarDeclType.MEMORY && rightCv in 1.0..3.0 && compTarget.name!=VMTarget.NAME) {
                                // replace by several DECs if it's not a memory address (dec on a memory mapped register doesn't work very well)
                                val decs = AnonymousScope(mutableListOf(), assignment.position)
                                repeat(rightCv.toInt()) {
                                    decs.statements.add(PostIncrDecr(assignment.target.copy(), "--", assignment.position))
                                }
                                return listOf(IAstModification.ReplaceNode(assignment, decs, parent))
                            }
                        }
                    }
                    "*" -> if (rightCv == 1.0) return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
                    "/" -> if (rightCv == 1.0) return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
                    "|" -> if (rightCv == 0.0) return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
                    "^" -> if (rightCv == 0.0) return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
                    "<<" -> {
                        if (rightCv == 0.0)
                            return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
                    }
                    ">>" -> {
                        if (rightCv == 0.0)
                            return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
                    }
                }

            }
        }

        // word = lsb(word)
        if(assignment.target.inferType(program).isWords) {
            var fcall = assignment.value as? FunctionCallExpression
            if (fcall == null)
                fcall = (assignment.value as? TypecastExpression)?.expression as? FunctionCallExpression
            if (fcall != null && (fcall.target.nameInSource == listOf("lsb"))) {
                if (fcall.args.single() isSameAs assignment.target) {
                    // optimize word=lsb(word) ==>  word &= $00ff
                    val and255 = BinaryExpression(fcall.args[0], "&", NumericLiteral(DataType.UWORD, 255.0, fcall.position), fcall.position)
                    val newAssign = Assignment(assignment.target, and255, AssignmentOrigin.OPTIMIZER, fcall.position)
                    return listOf(IAstModification.ReplaceNode(assignment, newAssign, parent))
                }
            }
        }

        return noModifications
    }
}
