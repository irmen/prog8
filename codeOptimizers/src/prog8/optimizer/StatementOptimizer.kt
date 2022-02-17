package prog8.optimizer

import prog8.ast.*
import prog8.ast.base.ArrayDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.IntegerDatatypes
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compilerinterface.ICompilationTarget
import prog8.compilerinterface.IErrorReporter
import kotlin.math.floor


class StatementOptimizer(private val program: Program,
                                  private val errors: IErrorReporter,
                                  private val functions: IBuiltinFunctions,
                                  private val compTarget: ICompilationTarget
) : AstWalker() {

    override fun before(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        // if the first instruction in the called subroutine is a return statement with a simple value (NOT being a parameter),
        // remove the jump altogeter and inline the returnvalue directly.

        fun scopePrefix(variable: IdentifierReference): IdentifierReference {
            val target = variable.targetStatement(program) as INamedStatement
            return IdentifierReference(target.scopedName, variable.position)
        }

        val subroutine = functionCallExpr.target.targetSubroutine(program)
        if(subroutine!=null) {
            val first = subroutine.statements.asSequence().filterNot { it is VarDecl || it is Directive }.firstOrNull()
            if(first is Return && first.value?.isSimple==true) {
                val copy = when(val orig = first.value!!) {
                    is AddressOf -> {
                        val scoped = scopePrefix(orig.identifier)
                        AddressOf(scoped, orig.position)
                    }
                    is DirectMemoryRead -> {
                        when(val expr = orig.addressExpression) {
                            is NumericLiteral -> DirectMemoryRead(expr.copy(), orig.position)
                            else -> return noModifications
                        }
                    }
                    is IdentifierReference -> {
                        if(orig.targetVarDecl(program)?.origin == VarDeclOrigin.SUBROUTINEPARAM)
                            return noModifications
                        else
                            scopePrefix(orig)
                    }
                    is NumericLiteral -> orig.copy()
                    is StringLiteral -> orig.copy()
                    else -> return noModifications
                }
                return listOf(IAstModification.ReplaceNode(functionCallExpr, copy, parent))
            }
        }
        return noModifications
    }

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

        // if the first instruction in the called subroutine is a return statement, remove the jump altogeter
        val subroutine = functionCallStatement.target.targetSubroutine(program)
        if(subroutine!=null) {
            val first = subroutine.statements.asSequence().filterNot { it is VarDecl || it is Directive }.firstOrNull()
            if(first is Return)
                return listOf(IAstModification.Remove(functionCallStatement, parent as IStatementContainer))
        }

        // see if we can optimize any complex arguments
        // TODO for now, only works for single-argument functions because we use just 1 temp var: R9
        if(functionCallStatement.target.nameInSource !in listOf(listOf("pop"), listOf("popw")) && functionCallStatement.args.size==1) {
            val arg = functionCallStatement.args[0]
            if(!arg.isSimple && arg !is TypecastExpression && arg !is IFunctionCall) {
                val name = getTempRegisterName(arg.inferType(program))
                val tempvar = IdentifierReference(name, functionCallStatement.position)
                val assignTempvar = Assignment(AssignTarget(tempvar.copy(), null, null, functionCallStatement.position), arg, AssignmentOrigin.OPTIMIZER, functionCallStatement.position)
                return listOf(
                    IAstModification.InsertBefore(functionCallStatement, assignTempvar, parent as IStatementContainer),
                    IAstModification.ReplaceNode(arg, tempvar, functionCallStatement)
                )
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
            val invertedCondition = PrefixExpression("not", ifElse.condition, ifElse.condition.position)
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
                errors.warn("condition is always true", ifElse.condition.position)
                listOf(IAstModification.ReplaceNode(ifElse, ifElse.truepart, parent))
            } else {
                // always false -> keep only else-part
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


    // NOTE: do NOT remove a jump to the next statement, because this will lead to wrong code when this occurs at the end of a subroutine
    // if we want to optimize this away, it can be done later at code generation time.

    override fun after(gosub: GoSub, parent: Node): Iterable<IAstModification> {
        // if the next statement is return with no returnvalue, change into a regular jump if there are no parameters as well.
        val subroutineParams = gosub.identifier?.targetSubroutine(program)?.parameters
        if(subroutineParams!=null && subroutineParams.isEmpty()) {
            val returnstmt = gosub.nextSibling() as? Return
            if(returnstmt!=null && returnstmt.value==null) {
                return listOf(
                    IAstModification.Remove(returnstmt, parent as IStatementContainer),
                    IAstModification.ReplaceNode(gosub, Jump(gosub.address, gosub.identifier, gosub.generatedLabel, gosub.position), parent)
                )
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

                    if(rExpr.left is NumericLiteral && op2 in AssociativeOperators) {
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
                if (!(binExpr.left isSameAs binExpr.right))
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
            if(bexpr.operator=="-" && rightCv==null) {
                if(bexpr.right isSameAs assignment.target) {
                    // X = value - X  -->  X = -X ; X += value  (to avoid need of stack-evaluation)
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
                            if (vardeclDt != VarDeclType.MEMORY && rightCv in 1.0..4.0) {
                                // replace by several INCs if it's not a memory address (inc on a memory mapped register doesn't work very well)
                                val incs = AnonymousScope(mutableListOf(), assignment.position)
                                repeat(rightCv.toInt()) {
                                    incs.statements.add(PostIncrDecr(assignment.target.copy(), "++", assignment.position))
                                }
                                listOf(IAstModification.ReplaceNode(assignment, if(incs.statements.size==1) incs.statements[0] else incs, parent))
                            }
                        }
                    }
                    "-" -> {
                        if (rightCv == 0.0) {
                            return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
                        } else if (targetDt in IntegerDatatypes && floor(rightCv) == rightCv) {
                            if (vardeclDt != VarDeclType.MEMORY && rightCv in 1.0..4.0) {
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
                    "**" -> if (rightCv == 1.0) return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
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

        // word = msb(word) , word=lsb(word)
        if(assignment.target.inferType(program).isWords) {
            var fcall = assignment.value as? FunctionCallExpression
            if (fcall == null)
                fcall = (assignment.value as? TypecastExpression)?.expression as? FunctionCallExpression
            if (fcall != null && (fcall.target.nameInSource == listOf("lsb") || fcall.target.nameInSource == listOf("msb"))) {
                if (fcall.args.single() isSameAs assignment.target) {
                    return if (fcall.target.nameInSource == listOf("lsb")) {
                        // optimize word=lsb(word) ==>  word &= $00ff
                        val and255 = BinaryExpression(fcall.args[0], "&", NumericLiteral(DataType.UWORD, 255.0, fcall.position), fcall.position)
                        val newAssign = Assignment(assignment.target, and255, AssignmentOrigin.OPTIMIZER, fcall.position)
                        listOf(IAstModification.ReplaceNode(assignment, newAssign, parent))
                    } else {
                        // optimize word=msb(word) ==>  word >>= 8
                        val shift8 = BinaryExpression(fcall.args[0], ">>", NumericLiteral(DataType.UBYTE, 8.0, fcall.position), fcall.position)
                        val newAssign = Assignment(assignment.target, shift8, AssignmentOrigin.OPTIMIZER, fcall.position)
                        listOf(IAstModification.ReplaceNode(assignment, newAssign, parent))
                    }
                }
            }
        }

        return noModifications
    }

    override fun after(returnStmt: Return, parent: Node): Iterable<IAstModification> {
        fun returnViaIntermediaryVar(value: Expression): Iterable<IAstModification>? {
            val subr = returnStmt.definingSubroutine!!
            val returnDt = subr.returntypes.single()
            if (returnDt in IntegerDatatypes) {
                // first assign to intermediary variable, then return that
                val returnVarName = program.getTempVar(returnDt)
                val returnValueIntermediary = IdentifierReference(returnVarName, returnStmt.position)
                val tgt = AssignTarget(returnValueIntermediary, null, null, returnStmt.position)
                val assign = Assignment(tgt, value, AssignmentOrigin.OPTIMIZER, returnStmt.position)
                val returnReplacement = Return(returnValueIntermediary.copy(), returnStmt.position)
                return listOf(
                    IAstModification.InsertBefore(returnStmt, assign, parent as IStatementContainer),
                    IAstModification.ReplaceNode(returnStmt, returnReplacement, parent)
                )
            }
            return null
        }

        // TODO decision when to use intermediary variable to calculate returnvalue seems a bit arbitrary...
        val returnvalue = returnStmt.value
        if (returnvalue!=null) {
            if (returnvalue is BinaryExpression || (returnvalue is TypecastExpression && !returnvalue.expression.isSimple)) {
                val mod = returnViaIntermediaryVar(returnvalue)
                if(mod!=null)
                    return mod
            }
        }

        return noModifications
    }
}
