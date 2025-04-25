package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*


internal class CodeDesugarer(val program: Program, private val errors: IErrorReporter) : AstWalker() {

    // Some more code shuffling to simplify the Ast that the codegenerator has to process.
    // Several changes have already been done by the StatementReorderer !
    // But the ones here are simpler and are repeated once again after all optimization steps
    // have been performed (because those could re-introduce nodes that have to be desugared)
    //
    // List of modifications:
    // - replace 'break' and 'continue' statements by a goto + generated after label.
    // - replace while and do-until loops by just jumps.
    // - replace peek() and poke() by direct memory accesses.
    // - repeat-forever loops replaced by label+jump.
    // - pointer[word] replaced by @(pointer+word)
    // - @(&var) and @(&var+1) replaced by lsb(var) and msb(var) if var is a word
    // - flatten chained assignments
    // - remove alias nodes

    override fun after(alias: Alias, parent: Node): Iterable<IAstModification> {
        return listOf(IAstModification.Remove(alias, parent as IStatementContainer))
    }

    override fun before(breakStmt: Break, parent: Node): Iterable<IAstModification> {
        fun jumpAfter(stmt: Statement): Iterable<IAstModification> {
            val label = program.makeLabel("after", breakStmt.position)
            return listOf(
                IAstModification.ReplaceNode(breakStmt, program.jumpLabel(label), parent),
                IAstModification.InsertAfter(stmt, label, stmt.parent as IStatementContainer)
            )
        }

        var partof = parent
        while(true) {
            when (partof) {
                is Subroutine, is Block, is ParentSentinel -> {
                    errors.err("break in wrong scope", breakStmt.position)
                    return noModifications
                }
                is ForLoop,
                is RepeatLoop,
                is UntilLoop,
                is WhileLoop -> return jumpAfter(partof)
                else -> partof = partof.parent
            }
        }
    }

    override fun before(continueStmt: Continue, parent: Node): Iterable<IAstModification> {
        fun jumpToBottom(scope: IStatementContainer): Iterable<IAstModification> {
            val label = program.makeLabel("cont", continueStmt.position)
            return listOf(
                IAstModification.ReplaceNode(continueStmt, program.jumpLabel(label), parent),
                IAstModification.InsertLast(label, scope)
            )
        }

        fun jumpToBefore(loop: WhileLoop): Iterable<IAstModification> {
            val label = program.makeLabel("cont", continueStmt.position)
            return listOf(
                IAstModification.ReplaceNode(continueStmt, program.jumpLabel(label), parent),
                IAstModification.InsertBefore(loop, label, loop.parent as IStatementContainer)
            )
        }

        var partof = parent
        while(true) {
            when (partof) {
                is Subroutine, is Block, is ParentSentinel -> {
                    errors.err("continue in wrong scope", continueStmt.position)
                    return noModifications
                }
                is ForLoop -> return jumpToBottom(partof.body)
                is RepeatLoop -> return jumpToBottom(partof.body)
                is UntilLoop -> return jumpToBottom(partof.body)
                is WhileLoop -> return jumpToBefore(partof)
                else -> partof = partof.parent
            }
        }
    }

    override fun after(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> {
        /*
do { STUFF } until CONDITION
    ===>
_loop:
  STUFF
if not CONDITION
   goto _loop
         */
        val pos = untilLoop.position
        val loopLabel = program.makeLabel("untilloop", pos)
        val replacement = AnonymousScope(mutableListOf(
            loopLabel,
            untilLoop.body,
            IfElse(invertCondition(untilLoop.condition, program),
                AnonymousScope(mutableListOf(program.jumpLabel(loopLabel)), pos),
                AnonymousScope(mutableListOf(), pos),
                pos)
        ), pos)
        return listOf(IAstModification.ReplaceNode(untilLoop, replacement, parent))
    }

    override fun after(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {

        if(!whileLoop.condition.inferType(program).isBool) {
            errors.err("condition should be a boolean", whileLoop.condition.position)
        }

        /*
        while true -> repeat
        while false -> discard
         */

        val constCondition = whileLoop.condition.constValue(program)?.asBooleanValue
        if(constCondition==true) {
            errors.warn("condition is always true", whileLoop.condition.position)
            val repeat = RepeatLoop(null, whileLoop.body, whileLoop.position)
            return listOf(IAstModification.ReplaceNode(whileLoop, repeat, parent))
        } else if(constCondition==false) {
            errors.warn("condition is always false", whileLoop.condition.position)
            return listOf(IAstModification.Remove(whileLoop, parent as IStatementContainer))
        }


        /*
while CONDITION { STUFF }
    ==>
_whileloop:
  if not CONDITION goto _after
  STUFF
  goto _whileloop
_after:
         */
        val pos = whileLoop.position
        val loopLabel = program.makeLabel("whileloop", pos)
        val afterLabel = program.makeLabel("afterwhile", pos)
        val replacement = AnonymousScope(mutableListOf(
            loopLabel,
            IfElse(invertCondition(whileLoop.condition, program),
                AnonymousScope(mutableListOf(program.jumpLabel(afterLabel)), pos),
                AnonymousScope(mutableListOf(), pos),
                pos),
            whileLoop.body,
            program.jumpLabel(loopLabel),
            afterLabel
        ), pos)
        return listOf(IAstModification.ReplaceNode(whileLoop, replacement, parent))
    }

    override fun before(functionCallStatement: FunctionCallStatement, parent: Node) =
        before(functionCallStatement as IFunctionCall, parent, functionCallStatement.position)

    override fun before(functionCallExpr: FunctionCallExpression, parent: Node) =
        before(functionCallExpr as IFunctionCall, parent, functionCallExpr.position)

    private fun before(functionCall: IFunctionCall, parent: Node, position: Position): Iterable<IAstModification> {
        if(functionCall.target.nameInSource==listOf("peek")) {
            // peek(a) is synonymous with @(a)
            val memread = DirectMemoryRead(functionCall.args.single(), position)
            return listOf(IAstModification.ReplaceNode(functionCall as Node, memread, parent))
        }
        if(functionCall.target.nameInSource==listOf("poke")) {
            // poke(a, v) is synonymous with @(a) = v
            val tgt = AssignTarget(null, null, DirectMemoryWrite(functionCall.args[0], position), null, false, position)
            val assign = Assignment(tgt, functionCall.args[1], AssignmentOrigin.OPTIMIZER, position)
            return listOf(IAstModification.ReplaceNode(functionCall as Node, assign, parent))
        }
        return noModifications
    }

    override fun after(repeatLoop: RepeatLoop, parent: Node): Iterable<IAstModification> {
        if(repeatLoop.iterations==null) {
            // replace with a jump at the end, but make sure the jump is inserted *before* any subroutines that may occur inside this block
            val subroutineMovements = mutableListOf<IAstModification>()
            val subroutines = repeatLoop.body.statements.filterIsInstance<Subroutine>()
            subroutines.forEach { sub ->
                subroutineMovements += IAstModification.Remove(sub, sub.parent as IStatementContainer)
                subroutineMovements += IAstModification.InsertLast(sub, sub.parent as IStatementContainer)
            }

            val label = program.makeLabel("repeat", repeatLoop.position)
            val jump = program.jumpLabel(label)
            return listOf(
                IAstModification.InsertFirst(label, repeatLoop.body),
                IAstModification.InsertLast(jump, repeatLoop.body),
                IAstModification.ReplaceNode(repeatLoop, repeatLoop.body, parent)
            ) + subroutineMovements
        }
        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        // replace pointervar[word] by @(pointervar+word) to avoid the
        // "array indexing is limited to byte size 0..255" error for pointervariables.
        val indexExpr = arrayIndexedExpression.indexer.indexExpr
        val arrayVar = arrayIndexedExpression.arrayvar.targetVarDecl()
        if(arrayVar!=null && arrayVar.datatype.isUnsignedWord) {
            val wordIndex = TypecastExpression(indexExpr, DataType.UWORD, true, indexExpr.position)
            val address = BinaryExpression(arrayIndexedExpression.arrayvar.copy(), "+", wordIndex, arrayIndexedExpression.position)
            return if(parent is AssignTarget) {
                // assignment to array
                val memwrite = DirectMemoryWrite(address, arrayIndexedExpression.position)
                val newtarget = AssignTarget(null, null, memwrite, null, false, arrayIndexedExpression.position)
                listOf(IAstModification.ReplaceNode(parent, newtarget, parent.parent))
            } else {
                // read from array
                val memread = DirectMemoryRead(address, arrayIndexedExpression.position)
                listOf(IAstModification.ReplaceNode(arrayIndexedExpression, memread, parent))
            }
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        fun isStringComparison(leftDt: InferredTypes.InferredType, rightDt: InferredTypes.InferredType): Boolean =
            if(leftDt issimpletype BaseDataType.STR && rightDt issimpletype BaseDataType.STR)
                true
            else
                leftDt issimpletype BaseDataType.UWORD && rightDt issimpletype BaseDataType.STR || leftDt issimpletype BaseDataType.STR && rightDt issimpletype BaseDataType.UWORD

        if(expr.operator=="in") {
            val containment = ContainmentCheck(expr.left, expr.right, expr.position)
            return listOf(IAstModification.ReplaceNode(expr, containment, parent))
        }

        if(expr.operator in ComparisonOperators) {
            val leftDt = expr.left.inferType(program)
            val rightDt = expr.right.inferType(program)

            if(isStringComparison(leftDt, rightDt)) {
                // replace string comparison expressions with calls to string.compare()
                val stringCompare = FunctionCallExpression(
                    IdentifierReference(listOf("prog8_lib_stringcompare"), expr.position),
                    mutableListOf(expr.left.copy(), expr.right.copy()), expr.position)
                val zero = NumericLiteral.optimalInteger(0, expr.position)
                val comparison = BinaryExpression(stringCompare, expr.operator, zero, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, comparison, parent))
            }
        }

        if(expr.operator=="*" && expr.inferType(program).isInteger && expr.left isSameAs expr.right) {
            // replace squaring with call to builtin function to do this in a more optimized way
            val function = if(expr.left.inferType(program).isBytes) "prog8_lib_square_byte" else "prog8_lib_square_word"
            val squareCall = FunctionCallExpression(
                IdentifierReference(listOf(function), expr.position),
                mutableListOf(expr.left.copy()), expr.position)
            return listOf(IAstModification.ReplaceNode(expr, squareCall, parent))
        }

        return noModifications
    }

    override fun after(memread: DirectMemoryRead, parent: Node): Iterable<IAstModification> {
        // for word variables:
        // @(&var) --> lsb(var)
        // @(&var+1) --> msb(var)           NOTE: ONLY WHEN VAR IS AN ACTUAL WORD VARIABLE (POINTER)

        val addrOf = memread.addressExpression as? AddressOf
        if(addrOf?.arrayIndex!=null)
            return noModifications
        if(addrOf!=null && addrOf.identifier.inferType(program).isWords) {
            val lsb = FunctionCallExpression(IdentifierReference(listOf("lsb"), memread.position), mutableListOf(addrOf.identifier), memread.position)
            return listOf(IAstModification.ReplaceNode(memread, lsb, parent))
        }
        val expr = memread.addressExpression as? BinaryExpression
        if(expr!=null && expr.operator=="+") {
            val addressOf = expr.left as? AddressOf
            val offset = (expr.right as? NumericLiteral)?.number?.toInt()
            if(addressOf!=null && offset==1) {
                val variable = addressOf.identifier.targetVarDecl()
                if(variable!=null && variable.datatype.isWord) {
                    val msb = FunctionCallExpression(IdentifierReference(listOf("msb"), memread.position), mutableListOf(
                        addressOf.identifier
                    ), memread.position)
                    return listOf(IAstModification.ReplaceNode(memread, msb, parent))
                }
            }
        }

        return noModifications
    }

    override fun after(chainedAssignment: ChainedAssignment, parent: Node): Iterable<IAstModification> {
        val assign = chainedAssignment.nested as? Assignment
        if(assign!=null) {
            // unpack starting from last in the chain
            val assigns = mutableListOf<Statement>(assign)
            var lastChained: ChainedAssignment = chainedAssignment
            var pc: ChainedAssignment? = chainedAssignment

            if(assign.value.isSimple) {
                // simply copy the RHS value to each component's assignment
                while (pc != null) {
                    lastChained = pc
                    assigns.add(Assignment(pc.target.copy(), assign.value.copy(), assign.origin, pc.position))
                    pc = pc.parent as? ChainedAssignment
                }
            } else if(pc!=null) {
                // need to evaluate RHS once and reuse that in each component's assignment
                val firstComponentAsValue = assign.target.toExpression()
                while (pc != null) {
                    lastChained = pc
                    assigns.add(Assignment(pc.target.copy(), firstComponentAsValue.copy(), assign.origin, pc.position))
                    pc = pc.parent as? ChainedAssignment
                }
            }
            return listOf(IAstModification.ReplaceNode(lastChained,
                AnonymousScope(assigns, chainedAssignment.position), lastChained.parent))
        }
        return noModifications
    }

    override fun after(whenChoice: WhenChoice, parent: Node): Iterable<IAstModification> {
        // replace a range expression in a when by the actual list of numbers it represents
        val values = whenChoice.values
        if(values!=null && values.size==1) {
            val conditionType = (whenChoice.parent as When).condition.inferType(program)
            val intRange = (values[0] as? RangeExpression)?.toConstantIntegerRange()
            if(conditionType.isKnown && intRange != null) {
                if(intRange.count()>255)
                    errors.err("values list too long", values[0].position)
                else {
                    val dt = conditionType.getOrUndef().base
                    val newValues = intRange.map {
                        val num = NumericLiteral(BaseDataType.LONG, it.toDouble(), values[0].position)
                        num.linkParents(whenChoice)
                        val cast = num.cast(dt, true)
                        if (cast.isValid) cast.valueOrZero() else null
                    }
                    if(null !in newValues) {
                        if(newValues.size>=10)
                            errors.warn("long list of values, checking will not be very efficient", values[0].position)
                        values.clear()
                        for(num in newValues)
                            values.add(num!!)
                    }
                }
            }
        }

        return noModifications
    }
}
