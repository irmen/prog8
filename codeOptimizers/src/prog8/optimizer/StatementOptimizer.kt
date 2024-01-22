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
                         private val options: CompilationOptions
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
                if(arg.arrayIndex==null) arg.identifier else null
            } else {
                arg as? IdentifierReference
            }
            if(stringVar!=null && stringVar.wasStringLiteral(program)) {
                val string = stringVar.targetVarDecl(program)?.value as? StringLiteral
                if(string!=null) {
                    val pos = functionCallStatement.position
                    if (string.value.length == 1) {
                        val firstCharEncoded = options.compTarget.encodeString(string.value, string.encoding)[0]
                        val chrout = FunctionCallStatement(
                            IdentifierReference(listOf("txt", "chrout"), pos),
                            mutableListOf(NumericLiteral(DataType.UBYTE, firstCharEncoded.toDouble(), pos)),
                            functionCallStatement.void, pos
                        )
                        return listOf(IAstModification.ReplaceNode(functionCallStatement, chrout, parent))
                    } else if (string.value.length == 2) {
                        val firstTwoCharsEncoded = options.compTarget.encodeString(string.value.take(2), string.encoding)
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
                        return listOf(
                            IAstModification.InsertBefore(functionCallStatement, chrout1, parent as IStatementContainer),
                            IAstModification.ReplaceNode(functionCallStatement, chrout2, parent)
                        )
                    }
                }
            }
        }

        return noModifications
    }

    override fun after(ifElse: IfElse, parent: Node): Iterable<IAstModification> {
        val constvalue = ifElse.condition.constValue(program)
        if(constvalue!=null) {
            errors.warn("condition is always ${constvalue.asBooleanValue}", ifElse.condition.position)
        }

        // remove empty if statements
        if(ifElse.truepart.isEmpty() && ifElse.elsepart.isEmpty())
            return listOf(IAstModification.Remove(ifElse, parent as IStatementContainer))

        // empty true part? switch with the else part
        if(ifElse.truepart.isEmpty() && ifElse.elsepart.isNotEmpty()) {
            val invertedCondition = invertCondition(ifElse.condition, program)
            val emptyscope = AnonymousScope(mutableListOf(), ifElse.elsepart.position)
            val truepart = AnonymousScope(ifElse.elsepart.statements, ifElse.truepart.position)
            return listOf(
                    IAstModification.ReplaceNode(ifElse.condition, invertedCondition, ifElse),
                    IAstModification.ReplaceNode(ifElse.truepart, truepart, ifElse),
                    IAstModification.ReplaceNode(ifElse.elsepart, emptyscope, ifElse)
            )
        }

        if(constvalue!=null) {
            return if(constvalue.asBooleanValue){
                // always true -> keep only if-part
                listOf(IAstModification.ReplaceNode(ifElse, ifElse.truepart, parent))
            } else {
                // always false -> keep only else-part
                listOf(IAstModification.ReplaceNode(ifElse, ifElse.elsepart, parent))
            }
        }

        // remove obvious dangling elses (else after a return)
        if(ifElse.elsepart.isNotEmpty() && ifElse.truepart.statements.singleOrNull() is Return) {
            val elsePart = AnonymousScope(ifElse.elsepart.statements, ifElse.elsepart.position)
            return listOf(
                IAstModification.ReplaceNode(ifElse.elsepart, AnonymousScope(mutableListOf(), ifElse.elsepart.position), ifElse),
                IAstModification.InsertAfter(ifElse, elsePart, parent as IStatementContainer)
            )
        }

        // switch if/else around if the else is just a jump or branch
        if(ifElse.elsepart.isNotEmpty() && ifElse.elsepart.statements.size==1) {
            val jump = ifElse.elsepart.statements[0]
            if(jump is Jump) {
                val newTruePart = AnonymousScope(mutableListOf(jump), ifElse.elsepart.position)
                val newElsePart = AnonymousScope(ifElse.truepart.statements, ifElse.truepart.position)
                return listOf(
                    IAstModification.ReplaceNode(ifElse.elsepart, newElsePart, ifElse),
                    IAstModification.ReplaceNode(ifElse.truepart, newTruePart, ifElse),
                    IAstModification.ReplaceNode(ifElse.condition, invertCondition(ifElse.condition, program), ifElse)
                )
            }
        }

        return noModifications
    }

    override fun after(forLoop: ForLoop, parent: Node): Iterable<IAstModification> {
        if(forLoop.body.isEmpty()) {
            errors.info("removing empty for loop", forLoop.position)
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
                    val character = options.compTarget.encodeString(sv.value, sv.encoding)[0]
                    val byte = NumericLiteral(DataType.UBYTE, character.toDouble(), iterable.position)
                    val scope = AnonymousScope(mutableListOf(), forLoop.position)
                    scope.statements.add(Assignment(AssignTarget(forLoop.loopVar, null, null, forLoop.position), byte, AssignmentOrigin.OPTIMIZER, forLoop.position))
                    scope.statements.addAll(forLoop.body.statements)
                    return listOf(IAstModification.ReplaceNode(forLoop, scope, parent))
                }
            }
            else if(iterable.isArray) {
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

        val loopvarDt = forLoop.loopVarDt(program)
        if(loopvarDt.istype(DataType.UWORD) || loopvarDt.istype(DataType.UBYTE)) {
            if (range != null && range.from.constValue(program)?.number == 0.0 && range.step.constValue(program)?.number==1.0) {
                val toBinExpr = range.to as? BinaryExpression
                if(toBinExpr!=null && toBinExpr.operator=="-" && toBinExpr.right.constValue(program)?.number==1.0) {
                    // FOR var IN 0 TO X-1 .... ---> var=0, DO {... , var++} UNTIL var==X
                    val pos = forLoop.position
                    val condition = BinaryExpression(forLoop.loopVar.copy(), "==", toBinExpr.left, pos)
                    val incOne = PostIncrDecr(AssignTarget(forLoop.loopVar.copy(), null, null, pos), "++", pos)
                    forLoop.body.statements.add(incOne)
                    val replacement = AnonymousScope(mutableListOf(
                        Assignment(AssignTarget(forLoop.loopVar.copy(), null, null, pos),
                            NumericLiteral.optimalNumeric(0.0, pos),
                            AssignmentOrigin.OPTIMIZER, pos),
                        UntilLoop(forLoop.body, condition, pos)
                    ), pos)
                    return listOf(IAstModification.ReplaceNode(forLoop, replacement, parent))
                }

                if(options.compTarget.name!=VMTarget.NAME) {
                    // this optimization is not effective for the VM target.
                    val toConst = range.to.constValue(program)
                    if (toConst == null) {
                        // FOR var in 0 TO X ... --->  var=0, REPEAT { ... , IF var==X break , var++ }
                        val pos = forLoop.position
                        val incOne = PostIncrDecr(AssignTarget(forLoop.loopVar.copy(), null, null, pos), "++", pos)
                        val breakCondition = IfElse(
                            BinaryExpression(forLoop.loopVar, "==", range.to, pos),
                            AnonymousScope(mutableListOf(Break(pos)), pos),
                            AnonymousScope(mutableListOf(), pos),
                            pos
                        )
                        forLoop.body.statements.add(breakCondition)
                        forLoop.body.statements.add(incOne)
                        val replacement = AnonymousScope(mutableListOf(
                            Assignment(AssignTarget(forLoop.loopVar.copy(), null, null, pos),
                                NumericLiteral.optimalNumeric(0.0, pos),
                                AssignmentOrigin.OPTIMIZER, pos),
                            RepeatLoop(null, forLoop.body, pos)
                        ), pos)
                        return listOf(IAstModification.ReplaceNode(forLoop, replacement, parent))
                    }
                }
            }

            if (range != null && range.to.constValue(program)?.number == 0.0 && range.step.constValue(program)?.number==-1.0) {
                val fromExpr = range.from
                if(fromExpr.constValue(program)==null) {
                    // FOR X = something DOWNTO 0 {...} -->  X=something,  DO { ... , X-- } UNTIL X=255 (or 65535 if uword)
                    val pos = forLoop.position
                    val checkValue = NumericLiteral(loopvarDt.getOr(DataType.UNDEFINED), if(loopvarDt.istype(DataType.UBYTE)) 255.0 else 65535.0, pos)
                    val condition = BinaryExpression(forLoop.loopVar.copy(), "==", checkValue, pos)
                    val decOne = PostIncrDecr(AssignTarget(forLoop.loopVar.copy(), null, null, pos), "--", pos)
                    forLoop.body.statements.add(decOne)
                    val replacement = AnonymousScope(mutableListOf(
                        Assignment(AssignTarget(forLoop.loopVar.copy(), null, null, pos),
                            fromExpr, AssignmentOrigin.OPTIMIZER, pos),
                        UntilLoop(forLoop.body, condition, pos)
                    ), pos)
                    return listOf(IAstModification.ReplaceNode(forLoop, replacement, parent))
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
                errors.info("empty loop removed", repeatLoop.position)
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
                            if (vardeclDt != VarDeclType.MEMORY && rightCv in 1.0..3.0 && options.compTarget.name!=VMTarget.NAME) {
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
                            if (vardeclDt != VarDeclType.MEMORY && rightCv in 1.0..3.0 && options.compTarget.name!=VMTarget.NAME) {
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

    override fun before(unrollLoop: UnrollLoop, parent: Node): Iterable<IAstModification> {
        val iterations = unrollLoop.iterations.constValue(program)?.number?.toInt()
        return if(iterations!=null && iterations<1)
            listOf(IAstModification.Remove(unrollLoop, parent as IStatementContainer))
        else
            noModifications
    }

    override fun after(whenStmt: When, parent: Node): Iterable<IAstModification> {

        fun replaceWithIf(condition: Expression, trueBlock: AnonymousScope, elseBlock: AnonymousScope?): List<IAstModification> {
            val ifStmt = IfElse(condition, trueBlock, elseBlock ?: AnonymousScope(mutableListOf(), whenStmt.position), whenStmt.position)
            errors.info("for boolean condition a normal if statement is preferred", whenStmt.position)
            return listOf(IAstModification.ReplaceNode(whenStmt, ifStmt, parent))
        }

        val constantValue = whenStmt.condition.constValue(program)?.number
        if(constantValue!=null) {
            // when condition is a constant
            var matchingChoice: WhenChoice? = null
            loop@ for(choice in whenStmt.choices) {
                for(value in choice.values ?: emptyList()) {
                    if(value.constValue(program)?.number == constantValue) {
                        matchingChoice = choice
                        break@loop
                    }
                }
            }
            if(matchingChoice==null)
                matchingChoice = whenStmt.choices.singleOrNull { it.values==null }
            if(matchingChoice!=null) {
                // get rid of the whole when-statement and just leave the matching choice
                return listOf(IAstModification.ReplaceNode(whenStmt, matchingChoice.statements, parent))
            }
        }

        return noModifications
    }

}
