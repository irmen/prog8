package prog8.optimizing

import prog8.ast.*
import prog8.compiler.HeapValues
import prog8.compiler.target.c64.Petscii
import prog8.functions.BuiltinFunctions
import sun.font.TrueTypeFont
import kotlin.math.floor


/*
    todo remove unused blocks
    todo remove unused variables
    todo remove unused subroutines
    todo remove unused strings and arrays from the heap
    todo analyse for unreachable code and remove that (f.i. code after goto or return that has no label so can never be jumped to)

    todo regular subroutines that have 1 or 2 (u)byte  or 1 (u)word parameters -> change to asmsub to accept these in A/Y registers instead of on stack

    todo merge sequence of assignments into one to avoid repeated value loads (as long as the value is a constant and the target not a MEMORY type!)

    todo inline subroutines that are called exactly once (regardless of their size)
    todo inline subroutines that are only called a few times (3?) and that are "sufficiently small" (0-3 statements)
*/

class StatementOptimizer(private val namespace: INameScope, private val heap: HeapValues) : IAstProcessor {
    var optimizationsDone: Int = 0
        private set
    var statementsToRemove = mutableListOf<IStatement>()
        private set
    private val pureBuiltinFunctions = BuiltinFunctions.filter { it.value.pure }

    override fun process(block: Block): IStatement {
        if(block.statements.isEmpty()) {
            // remove empty block
            optimizationsDone++
            statementsToRemove.add(block)
        }
        return super.process(block)
    }

    override fun process(subroutine: Subroutine): IStatement {
        if(subroutine.asmAddress==null) {
            if(subroutine.statements.isEmpty()) {
                // remove empty subroutine
                optimizationsDone++
                statementsToRemove.add(subroutine)
            } else if(subroutine.statements.size==1) {
                val stmt = subroutine.statements[0]
                if(stmt is ReturnFromIrq || stmt is Return) {
                    // remove empty subroutine
                    optimizationsDone++
                    statementsToRemove.add(subroutine)
                }
            }
        }
        return super.process(subroutine)
    }

    override fun process(functionCall: FunctionCallStatement): IStatement {
        if(functionCall.target.nameInSource.size==1 && functionCall.target.nameInSource[0] in BuiltinFunctions) {
            val functionName = functionCall.target.nameInSource[0]
            if (functionName in pureBuiltinFunctions) {
                printWarning("statement has no effect (function return value is discarded)", functionCall.position)
                statementsToRemove.add(functionCall)
                return functionCall
            }
        }

        if(functionCall.target.nameInSource==listOf("c64scr", "print") ||
                functionCall.target.nameInSource==listOf("c64scr", "print_p")) {
            // printing a literal string of just 2 or 1 characters is replaced by directly outputting those characters
            if(functionCall.arglist.single() is LiteralValue)
                throw AstException("string argument should be on heap already")
            val stringVar = functionCall.arglist.single() as? IdentifierReference
            if(stringVar!=null) {
                val heapId = stringVar.heapId(namespace)
                val string = heap.get(heapId).str!!
                if(string.length==1) {
                    val petscii = Petscii.encodePetscii(string, true)[0]
                    functionCall.arglist.clear()
                    functionCall.arglist.add(LiteralValue.optimalInteger(petscii, functionCall.position))
                    functionCall.target = IdentifierReference(listOf("c64", "CHROUT"), functionCall.target.position)
                    optimizationsDone++
                    return functionCall
                } else if(string.length==2) {
                    val petscii = Petscii.encodePetscii(string, true)
                    val scope = AnonymousScope(mutableListOf(), functionCall.position)
                    scope.statements.add(FunctionCallStatement(IdentifierReference(listOf("c64", "CHROUT"), functionCall.target.position),
                            mutableListOf(LiteralValue.optimalInteger(petscii[0], functionCall.position)), functionCall.position))
                    scope.statements.add(FunctionCallStatement(IdentifierReference(listOf("c64", "CHROUT"), functionCall.target.position),
                            mutableListOf(LiteralValue.optimalInteger(petscii[1], functionCall.position)), functionCall.position))
                    optimizationsDone++
                    return scope
                }
            }
        }

        // if it calls a subroutine,
        // and the first instruction in the subroutine is a jump, call that jump target instead
        val subroutine = functionCall.target.targetStatement(namespace) as? Subroutine
        if(subroutine!=null) {
            val first = subroutine.statements.asSequence().filterNot { it is VarDecl || it is Directive }.firstOrNull()
            if(first is Jump && first.identifier!=null) {
                optimizationsDone++
                return FunctionCallStatement(first.identifier, functionCall.arglist, functionCall.position)
            }
        }

        return super.process(functionCall)
    }

    override fun process(functionCall: FunctionCall): IExpression {
        // if it calls a subroutine,
        // and the first instruction in the subroutine is a jump, call that jump target instead
        val subroutine = functionCall.target.targetStatement(namespace) as? Subroutine
        if(subroutine!=null) {
            val first = subroutine.statements.asSequence().filterNot { it is VarDecl || it is Directive }.firstOrNull()
            if(first is Jump && first.identifier!=null) {
                optimizationsDone++
                return FunctionCall(first.identifier, functionCall.arglist, functionCall.position)
            }
        }
        return super.process(functionCall)
    }

    override fun process(ifStatement: IfStatement): IStatement {
        super.process(ifStatement)

        if(ifStatement.truepart.isEmpty() && ifStatement.elsepart.isEmpty()) {
            statementsToRemove.add(ifStatement)
            optimizationsDone++
            return ifStatement
        }

        if(ifStatement.truepart.isEmpty() && ifStatement.elsepart.isNotEmpty()) {
            // invert the condition and move else part to true part
            ifStatement.truepart = ifStatement.elsepart
            ifStatement.elsepart = AnonymousScope(mutableListOf(), ifStatement.elsepart.position)
            ifStatement.condition = PrefixExpression("not", ifStatement.condition, ifStatement.condition.position)
            optimizationsDone++
            return ifStatement
        }

        val constvalue = ifStatement.condition.constValue(namespace, heap)
        if(constvalue!=null) {
            return if(constvalue.asBooleanValue){
                // always true -> keep only if-part
                printWarning("condition is always true", ifStatement.position)
                optimizationsDone++
                ifStatement.truepart
            } else {
                // always false -> keep only else-part
                printWarning("condition is always false", ifStatement.position)
                optimizationsDone++
                ifStatement.elsepart
            }
        }
        return ifStatement
    }

    override fun process(forLoop: ForLoop): IStatement {
        super.process(forLoop)
        if(forLoop.body.isEmpty()) {
            // remove empty for loop
            statementsToRemove.add(forLoop)
            optimizationsDone++
            return forLoop
        } else if(forLoop.body.statements.size==1) {
            val loopvar = forLoop.body.statements[0] as? VarDecl
            if(loopvar!=null && loopvar.name==forLoop.loopVar?.nameInSource?.singleOrNull()) {
                // remove empty for loop
                statementsToRemove.add(forLoop)
                optimizationsDone++
                return forLoop
            }
        }


        val range = forLoop.iterable as? RangeExpr
        if(range!=null) {
            if(range.size(heap)==1) {
                // for loop over a (constant) range of just a single value-- optimize the loop away
                // loopvar/reg = range value , follow by block
                val assignment = Assignment(listOf(AssignTarget(forLoop.loopRegister, forLoop.loopVar, null, null, forLoop.position)), null, range.from, forLoop.position)
                forLoop.body.statements.add(0, assignment)
                optimizationsDone++
                return forLoop.body
            }
        }
        return forLoop
    }

    override fun process(whileLoop: WhileLoop): IStatement {
        super.process(whileLoop)
        if(whileLoop.body.isEmpty()) {
            statementsToRemove.add(whileLoop)
            optimizationsDone++
            return whileLoop
        }

        val constvalue = whileLoop.condition.constValue(namespace, heap)
        if(constvalue!=null) {
            return if(constvalue.asBooleanValue){
                // always true -> print a warning, and optimize into body + jump
                printWarning("condition is always true", whileLoop.position)
                val label = Label("__back", whileLoop.condition.position)
                whileLoop.body.statements.add(0, label)
                whileLoop.body.statements.add(Jump(null,
                        IdentifierReference(listOf("__back"), whileLoop.condition.position),
                        null, whileLoop.condition.position))
                optimizationsDone++
                return whileLoop.body
            } else {
                // always false -> ditch whole statement
                printWarning("condition is always false", whileLoop.position)
                optimizationsDone++
                NopStatement(whileLoop.position)
            }
        }
        return whileLoop
    }

    override fun process(repeatLoop: RepeatLoop): IStatement {
        super.process(repeatLoop)
        if(repeatLoop.body.isEmpty()) {
            statementsToRemove.add(repeatLoop)
            optimizationsDone++
            return repeatLoop
        }
        val constvalue = repeatLoop.untilCondition.constValue(namespace, heap)
        if(constvalue!=null) {
            return if(constvalue.asBooleanValue){
                // always true -> keep only the statement block
                printWarning("condition is always true", repeatLoop.position)
                optimizationsDone++
                repeatLoop.body
            } else {
                // always false -> print a warning, and optimize into body + jump
                printWarning("condition is always false", repeatLoop.position)
                val label = Label("__back", repeatLoop.untilCondition.position)
                repeatLoop.body.statements.add(0, label)
                repeatLoop.body.statements.add(Jump(null,
                        IdentifierReference(listOf("__back"), repeatLoop.untilCondition.position),
                        null, repeatLoop.untilCondition.position))
                optimizationsDone++
                return repeatLoop.body
            }
        }
        return repeatLoop
    }

    override fun process(jump: Jump): IStatement {
        val subroutine = jump.identifier?.targetStatement(namespace) as? Subroutine
        if(subroutine!=null) {
            // if the first instruction in the subroutine is another jump, shortcut this one
            val first = subroutine.statements.asSequence().filterNot { it is VarDecl || it is Directive }.firstOrNull()
            if(first is Jump) {
                optimizationsDone++
                return first
            }
        }
        return jump
    }

    override fun process(assignment: Assignment): IStatement {
        if(assignment.aug_op!=null)
            throw AstException("augmented assignments should have been converted to normal assignments before this optimizer")

        if(assignment.targets.size==1) {
            val target=assignment.targets[0]
            if(same(target, assignment.value)) {
                optimizationsDone++
                return NopStatement(assignment.position)
            }
            val targetDt = target.determineDatatype(namespace, heap, assignment)!!
            val bexpr=assignment.value as? BinaryExpression
            if(bexpr!=null) {
                val cv = bexpr.right.constValue(namespace, heap)?.asNumericValue?.toDouble()
                if(cv==null) {
                    if(bexpr.operator=="+" && targetDt!=DataType.FLOAT) {
                        if (same(bexpr.left, bexpr.right) && same(target, bexpr.left)) {
                            bexpr.operator = "*"
                            bexpr.right = LiteralValue.optimalInteger(2, assignment.value.position)
                            optimizationsDone++
                            return assignment
                        }
                    }
                } else {
                    if (same(target, bexpr.left)) {
                        // remove assignments that have no effect  X=X , X+=0, X-=0, X*=1, X/=1, X//=1, A |= 0, A ^= 0, A<<=0, etc etc
                        // A = A <operator> B
                        val vardeclDt = (target.identifier?.targetStatement(namespace) as? VarDecl)?.type

                        when (bexpr.operator) {
                            "+" -> {
                                if (cv == 0.0) {
                                    optimizationsDone++
                                    return NopStatement(assignment.position)
                                } else if (targetDt in IntegerDatatypes && floor(cv) == cv) {
                                    if((vardeclDt == VarDeclType.MEMORY && cv in 1.0..3.0) || (vardeclDt!=VarDeclType.MEMORY && cv in 1.0..8.0)) {
                                        // replace by several INCs (a bit less when dealing with memory targets)
                                        val decs = AnonymousScope(mutableListOf(), assignment.position)
                                        repeat(cv.toInt()) {
                                            decs.statements.add(PostIncrDecr(target, "++", assignment.position))
                                        }
                                        return decs
                                    }
                                }
                            }
                            "-" -> {
                                if (cv == 0.0) {
                                    optimizationsDone++
                                    return NopStatement(assignment.position)
                                } else if (targetDt in IntegerDatatypes && floor(cv) == cv) {
                                    if((vardeclDt == VarDeclType.MEMORY && cv in 1.0..3.0) || (vardeclDt!=VarDeclType.MEMORY && cv in 1.0..8.0)) {
                                        // replace by several DECs (a bit less when dealing with memory targets)
                                        val decs = AnonymousScope(mutableListOf(), assignment.position)
                                        repeat(cv.toInt()) {
                                            decs.statements.add(PostIncrDecr(target, "--", assignment.position))
                                        }
                                        return decs
                                    }
                                }
                            }
                            "*" -> if (cv == 1.0) {
                                optimizationsDone++
                                return NopStatement(assignment.position)
                            }
                            "/" -> if (cv == 1.0) {
                                optimizationsDone++
                                return NopStatement(assignment.position)
                            }
                            "**" -> if (cv == 1.0) {
                                optimizationsDone++
                                return NopStatement(assignment.position)
                            }
                            "|" -> if (cv == 0.0) {
                                optimizationsDone++
                                return NopStatement(assignment.position)
                            }
                            "^" -> if (cv == 0.0) {
                                optimizationsDone++
                                return NopStatement(assignment.position)
                            }
                            "<<" -> {
                                if (cv == 0.0) {
                                    optimizationsDone++
                                    return NopStatement(assignment.position)
                                }
                                if (((targetDt == DataType.UWORD || targetDt == DataType.WORD) && cv > 15.0) ||
                                        ((targetDt == DataType.UBYTE || targetDt == DataType.BYTE) && cv > 7.0)) {
                                    assignment.value = LiteralValue.optimalInteger(0, assignment.value.position)
                                    assignment.value.linkParents(assignment)
                                    optimizationsDone++
                                } else {
                                    // replace by in-place lsl(...) call
                                    val scope = AnonymousScope(mutableListOf(), assignment.position)
                                    var numshifts = cv.toInt()
                                    while (numshifts > 0) {
                                        scope.statements.add(FunctionCallStatement(IdentifierReference(listOf("lsl"), assignment.position), mutableListOf(bexpr.left), assignment.position))
                                        numshifts--
                                    }
                                    optimizationsDone++
                                    return scope
                                }
                            }
                            ">>" -> {
                                if (cv == 0.0) {
                                    optimizationsDone++
                                    return NopStatement(assignment.position)
                                }
                                if (((targetDt == DataType.UWORD || targetDt == DataType.WORD) && cv > 15.0) ||
                                        ((targetDt == DataType.UBYTE || targetDt == DataType.BYTE) && cv > 7.0)) {
                                    assignment.value = LiteralValue.optimalInteger(0, assignment.value.position)
                                    assignment.value.linkParents(assignment)
                                    optimizationsDone++
                                } else {
                                    // replace by in-place lsr(...) call
                                    val scope = AnonymousScope(mutableListOf(), assignment.position)
                                    var numshifts = cv.toInt()
                                    while (numshifts > 0) {
                                        scope.statements.add(FunctionCallStatement(IdentifierReference(listOf("lsr"), assignment.position), mutableListOf(bexpr.left), assignment.position))
                                        numshifts--
                                    }
                                    optimizationsDone++
                                    return scope
                                }
                            }
                        }
                    }
                }
            }
        }

        return super.process(assignment)
    }


    private fun same(left: IExpression, right: IExpression): Boolean {
        if(left===right)
            return true
        when(left) {
            is RegisterExpr ->
                return (right is RegisterExpr && right.register==left.register)
            is IdentifierReference ->
                return (right is IdentifierReference && right.nameInSource==left.nameInSource)
            is ArrayIndexedExpression ->
                return (right is ArrayIndexedExpression && right.identifier==left.identifier && right.arrayspec==left.arrayspec)
        }
        return false
    }

    private fun same(target: AssignTarget, value: IExpression): Boolean {
        return when {
            target.memoryAddress!=null -> false
            target.register!=null -> value is RegisterExpr && value.register==target.register
            target.identifier!=null -> value is IdentifierReference && value.nameInSource==target.identifier.nameInSource
            target.arrayindexed!=null -> value is ArrayIndexedExpression &&
                    value.identifier.nameInSource==target.arrayindexed.identifier.nameInSource &&
                    value.arrayspec.size()!=null &&
                    target.arrayindexed.arrayspec.size()!=null &&
                    value.arrayspec.size()==target.arrayindexed.arrayspec.size()
            else -> false
        }
    }
}
