package prog8.optimizer

import prog8.ast.INameScope
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.processing.IAstModifyingVisitor
import prog8.ast.statements.*
import prog8.compiler.target.CompilationTarget
import prog8.functions.BuiltinFunctions
import kotlin.math.floor


/*
    TODO: remove unreachable code after return and exit()
    TODO: proper inlining of tiny subroutines (at first, restrict to subs without parameters and variables in them, and build it up from there: correctly renaming/relocating all variables in them and refs to those as well)
*/


internal class StatementOptimizer(private val program: Program,
                                  private val errors: ErrorReporter) : IAstModifyingVisitor {
    var optimizationsDone: Int = 0
        private set

    private val pureBuiltinFunctions = BuiltinFunctions.filter { it.value.pure }
    private val callgraph = CallGraph(program)
    private val vardeclsToRemove = mutableListOf<VarDecl>()

    override fun visit(program: Program) {
        super.visit(program)

        for(decl in vardeclsToRemove) {
            decl.definingScope().remove(decl)
        }
    }

    override fun visit(block: Block): Statement {
        if("force_output" !in block.options()) {
            if (block.containsNoCodeNorVars()) {
                optimizationsDone++
                errors.warn("removing empty block '${block.name}'", block.position)
                return NopStatement.insteadOf(block)
            }

            if (block !in callgraph.usedSymbols) {
                optimizationsDone++
                errors.warn("removing unused block '${block.name}'", block.position)
                return NopStatement.insteadOf(block)  // remove unused block
            }
        }

        return super.visit(block)
    }

    override fun visit(subroutine: Subroutine): Statement {
        super.visit(subroutine)
        val forceOutput = "force_output" in subroutine.definingBlock().options()
        if(subroutine.asmAddress==null && !forceOutput) {
            if(subroutine.containsNoCodeNorVars()) {
                errors.warn("removing empty subroutine '${subroutine.name}'", subroutine.position)
                optimizationsDone++
                return NopStatement.insteadOf(subroutine)
            }
        }

        val linesToRemove = deduplicateAssignments(subroutine.statements)
        if(linesToRemove.isNotEmpty()) {
            linesToRemove.reversed().forEach{subroutine.statements.removeAt(it)}
        }

        if(subroutine !in callgraph.usedSymbols && !forceOutput) {
            errors.warn("removing unused subroutine '${subroutine.name}'", subroutine.position)
            optimizationsDone++
            return NopStatement.insteadOf(subroutine)
        }

        return subroutine
    }

    override fun visit(decl: VarDecl): Statement {
        val forceOutput = "force_output" in decl.definingBlock().options()
        if(decl !in callgraph.usedSymbols && !forceOutput) {
            if(decl.type == VarDeclType.VAR)
                errors.warn("removing unused variable ${decl.type} '${decl.name}'", decl.position)
            optimizationsDone++
            return NopStatement.insteadOf(decl)
        }

        return super.visit(decl)
    }

    private fun deduplicateAssignments(statements: List<Statement>): MutableList<Int> {
        // removes 'duplicate' assignments that assign the isSameAs target
        val linesToRemove = mutableListOf<Int>()
        var previousAssignmentLine: Int? = null
        for (i in statements.indices) {
            val stmt = statements[i] as? Assignment
            if (stmt != null && stmt.value is NumericLiteralValue) {
                if (previousAssignmentLine == null) {
                    previousAssignmentLine = i
                    continue
                } else {
                    val prev = statements[previousAssignmentLine] as Assignment
                    if (prev.target.isSameAs(stmt.target, program)) {
                        // get rid of the previous assignment, if the target is not MEMORY
                        if (prev.target.isNotMemory(program.namespace))
                            linesToRemove.add(previousAssignmentLine)
                    }
                    previousAssignmentLine = i
                }
            } else
                previousAssignmentLine = null
        }
        return linesToRemove
    }

    override fun visit(functionCallStatement: FunctionCallStatement): Statement {
        if(functionCallStatement.target.nameInSource.size==1 && functionCallStatement.target.nameInSource[0] in BuiltinFunctions) {
            val functionName = functionCallStatement.target.nameInSource[0]
            if (functionName in pureBuiltinFunctions) {
                errors.warn("statement has no effect (function return value is discarded)", functionCallStatement.position)
                optimizationsDone++
                return NopStatement.insteadOf(functionCallStatement)
            }
        }

        if(functionCallStatement.target.nameInSource==listOf("c64scr", "print") ||
                functionCallStatement.target.nameInSource==listOf("c64scr", "print_p")) {
            // printing a literal string of just 2 or 1 characters is replaced by directly outputting those characters
            val arg = functionCallStatement.args.single()
            val stringVar: IdentifierReference?
            stringVar = if(arg is AddressOf) {
                arg.identifier
            } else {
                arg as? IdentifierReference
            }
            if(stringVar!=null) {
                val vardecl = stringVar.targetVarDecl(program.namespace)!!
                val string = vardecl.value!! as StringLiteralValue
                if(string.value.length==1) {
                    val firstCharEncoded = CompilationTarget.encodeString(string.value, string.altEncoding)[0]
                    functionCallStatement.args.clear()
                    functionCallStatement.args.add(NumericLiteralValue.optimalInteger(firstCharEncoded.toInt(), functionCallStatement.position))
                    functionCallStatement.target = IdentifierReference(listOf("c64", "CHROUT"), functionCallStatement.target.position)
                    vardeclsToRemove.add(vardecl)
                    optimizationsDone++
                    return functionCallStatement
                } else if(string.value.length==2) {
                    val firstTwoCharsEncoded = CompilationTarget.encodeString(string.value.take(2), string.altEncoding)
                    val scope = AnonymousScope(mutableListOf(), functionCallStatement.position)
                    scope.statements.add(FunctionCallStatement(IdentifierReference(listOf("c64", "CHROUT"), functionCallStatement.target.position),
                            mutableListOf(NumericLiteralValue.optimalInteger(firstTwoCharsEncoded[0].toInt(), functionCallStatement.position)),
                            functionCallStatement.void, functionCallStatement.position))
                    scope.statements.add(FunctionCallStatement(IdentifierReference(listOf("c64", "CHROUT"), functionCallStatement.target.position),
                            mutableListOf(NumericLiteralValue.optimalInteger(firstTwoCharsEncoded[1].toInt(), functionCallStatement.position)),
                            functionCallStatement.void, functionCallStatement.position))
                    vardeclsToRemove.add(vardecl)
                    optimizationsDone++
                    return scope
                }
            }
        }

        // if it calls a subroutine,
        // and the first instruction in the subroutine is a jump, call that jump target instead
        // if the first instruction in the subroutine is a return statement, replace with a nop instruction
        val subroutine = functionCallStatement.target.targetSubroutine(program.namespace)
        if(subroutine!=null) {
            val first = subroutine.statements.asSequence().filterNot { it is VarDecl || it is Directive }.firstOrNull()
            if(first is Jump && first.identifier!=null) {
                optimizationsDone++
                return FunctionCallStatement(first.identifier, functionCallStatement.args, functionCallStatement.void, functionCallStatement.position)
            }
            if(first is ReturnFromIrq || first is Return) {
                optimizationsDone++
                return NopStatement.insteadOf(functionCallStatement)
            }
        }

        return super.visit(functionCallStatement)
    }

    override fun visit(functionCall: FunctionCall): Expression {
        // if it calls a subroutine,
        // and the first instruction in the subroutine is a jump, call that jump target instead
        // if the first instruction in the subroutine is a return statement with constant value, replace with the constant value
        val subroutine = functionCall.target.targetSubroutine(program.namespace)
        if(subroutine!=null) {
            val first = subroutine.statements.asSequence().filterNot { it is VarDecl || it is Directive }.firstOrNull()
            if(first is Jump && first.identifier!=null) {
                optimizationsDone++
                return FunctionCall(first.identifier, functionCall.args, functionCall.position)
            }
            if(first is Return && first.value!=null) {
                val constval = first.value?.constValue(program)
                if(constval!=null)
                    return constval
            }
        }
        return super.visit(functionCall)
    }

    override fun visit(ifStatement: IfStatement): Statement {
        super.visit(ifStatement)

        if(ifStatement.truepart.containsNoCodeNorVars() && ifStatement.elsepart.containsNoCodeNorVars()) {
            optimizationsDone++
            return NopStatement.insteadOf(ifStatement)
        }

        if(ifStatement.truepart.containsNoCodeNorVars() && ifStatement.elsepart.containsCodeOrVars()) {
            // invert the condition and move else part to true part
            ifStatement.truepart = ifStatement.elsepart
            ifStatement.elsepart = AnonymousScope(mutableListOf(), ifStatement.elsepart.position)
            ifStatement.condition = PrefixExpression("not", ifStatement.condition, ifStatement.condition.position)
            optimizationsDone++
            return ifStatement
        }

        val constvalue = ifStatement.condition.constValue(program)
        if(constvalue!=null) {
            return if(constvalue.asBooleanValue){
                // always true -> keep only if-part
                errors.warn("condition is always true", ifStatement.position)
                optimizationsDone++
                ifStatement.truepart
            } else {
                // always false -> keep only else-part
                errors.warn("condition is always false", ifStatement.position)
                optimizationsDone++
                ifStatement.elsepart
            }
        }
        return ifStatement
    }

    override fun visit(forLoop: ForLoop): Statement {
        super.visit(forLoop)
        if(forLoop.body.containsNoCodeNorVars()) {
            // remove empty for loop
            optimizationsDone++
            return NopStatement.insteadOf(forLoop)
        } else if(forLoop.body.statements.size==1) {
            val loopvar = forLoop.body.statements[0] as? VarDecl
            if(loopvar!=null && loopvar.name==forLoop.loopVar?.nameInSource?.singleOrNull()) {
                // remove empty for loop
                optimizationsDone++
                return NopStatement.insteadOf(forLoop)
            }
        }


        val range = forLoop.iterable as? RangeExpr
        if(range!=null) {
            if(range.size()==1) {
                // for loop over a (constant) range of just a single value-- optimize the loop away
                // loopvar/reg = range value , follow by block
                val assignment = Assignment(AssignTarget(forLoop.loopRegister, forLoop.loopVar, null, null, forLoop.position), null, range.from, forLoop.position)
                forLoop.body.statements.add(0, assignment)
                optimizationsDone++
                return forLoop.body
            }
        }
        return forLoop
    }

    override fun visit(whileLoop: WhileLoop): Statement {
        super.visit(whileLoop)
        val constvalue = whileLoop.condition.constValue(program)
        if(constvalue!=null) {
            return if(constvalue.asBooleanValue){
                // always true -> print a warning, and optimize into a forever-loop
                errors.warn("condition is always true", whileLoop.condition.position)
                optimizationsDone++
                ForeverLoop(whileLoop.body, whileLoop.position)
            } else {
                // always false -> remove the while statement altogether
                errors.warn("condition is always false", whileLoop.condition.position)
                optimizationsDone++
                NopStatement.insteadOf(whileLoop)
            }
        }
        return whileLoop
    }

    override fun visit(repeatLoop: RepeatLoop): Statement {
        super.visit(repeatLoop)
        val constvalue = repeatLoop.untilCondition.constValue(program)
        if(constvalue!=null) {
            return if(constvalue.asBooleanValue){
                // always true -> keep only the statement block (if there are no continue and break statements)
                errors.warn("condition is always true", repeatLoop.untilCondition.position)
                if(hasContinueOrBreak(repeatLoop.body))
                    repeatLoop
                else {
                    optimizationsDone++
                    repeatLoop.body
                }
            } else {
                // always false -> print a warning, and optimize into a forever loop
                errors.warn("condition is always false", repeatLoop.untilCondition.position)
                optimizationsDone++
                ForeverLoop(repeatLoop.body, repeatLoop.position)
            }
        }
        return repeatLoop
    }

    override fun visit(whenStatement: WhenStatement): Statement {
        val choices = whenStatement.choices.toList()
        for(choice in choices) {
            if(choice.statements.containsNoCodeNorVars())
                whenStatement.choices.remove(choice)
        }
        return super.visit(whenStatement)
    }

    private fun hasContinueOrBreak(scope: INameScope): Boolean {

        class Searcher: IAstModifyingVisitor
        {
            var count=0

            override fun visit(breakStmt: Break): Statement {
                count++
                return super.visit(breakStmt)
            }

            override fun visit(contStmt: Continue): Statement {
                count++
                return super.visit(contStmt)
            }
        }
        val s=Searcher()
        for(stmt in scope.statements) {
            stmt.accept(s)
            if(s.count>0)
                return true
        }
        return s.count > 0
    }

    override fun visit(jump: Jump): Statement {
        val subroutine = jump.identifier?.targetSubroutine(program.namespace)
        if(subroutine!=null) {
            // if the first instruction in the subroutine is another jump, shortcut this one
            val first = subroutine.statements.asSequence().filterNot { it is VarDecl || it is Directive }.firstOrNull()
            if(first is Jump) {
                optimizationsDone++
                return first
            }
        }

        // if the jump is to the next statement, remove the jump
        val scope = jump.definingScope()
        val label = jump.identifier?.targetStatement(scope)
        if(label!=null) {
            if(scope.statements.indexOf(label) == scope.statements.indexOf(jump)+1) {
                optimizationsDone++
                return NopStatement.insteadOf(jump)
            }
        }

        return jump
    }

    override fun visit(assignment: Assignment): Statement {
        if(assignment.aug_op!=null)
            throw FatalAstException("augmented assignments should have been converted to normal assignments before this optimizer: $assignment")

        if(assignment.target isSameAs assignment.value) {
            if(assignment.target.isNotMemory(program.namespace)) {
                optimizationsDone++
                return NopStatement.insteadOf(assignment)
            }
        }
        val targetIDt = assignment.target.inferType(program, assignment)
        if(!targetIDt.isKnown)
            throw FatalAstException("can't infer type of assignment target")
        val targetDt = targetIDt.typeOrElse(DataType.STRUCT)
        val bexpr=assignment.value as? BinaryExpression
        if(bexpr!=null) {
            val cv = bexpr.right.constValue(program)?.number?.toDouble()
            if (cv == null) {
                if (bexpr.operator == "+" && targetDt != DataType.FLOAT) {
                    if (bexpr.left isSameAs bexpr.right && assignment.target isSameAs bexpr.left) {
                        bexpr.operator = "*"
                        bexpr.right = NumericLiteralValue.optimalInteger(2, assignment.value.position)
                        optimizationsDone++
                        return assignment
                    }
                }
            } else {
                if (assignment.target isSameAs bexpr.left) {
                    // remove assignments that have no effect  X=X , X+=0, X-=0, X*=1, X/=1, X//=1, A |= 0, A ^= 0, A<<=0, etc etc
                    // A = A <operator> B
                    val vardeclDt = (assignment.target.identifier?.targetVarDecl(program.namespace))?.type

                    when (bexpr.operator) {
                        "+" -> {
                            if (cv == 0.0) {
                                optimizationsDone++
                                return NopStatement.insteadOf(assignment)
                            } else if (targetDt in IntegerDatatypes && floor(cv) == cv) {
                                if ((vardeclDt == VarDeclType.MEMORY && cv in 1.0..3.0) || (vardeclDt != VarDeclType.MEMORY && cv in 1.0..8.0)) {
                                    // replace by several INCs (a bit less when dealing with memory targets)
                                    val decs = AnonymousScope(mutableListOf(), assignment.position)
                                    repeat(cv.toInt()) {
                                        decs.statements.add(PostIncrDecr(assignment.target, "++", assignment.position))
                                    }
                                    return decs
                                }
                            }
                        }
                        "-" -> {
                            if (cv == 0.0) {
                                optimizationsDone++
                                return NopStatement.insteadOf(assignment)
                            } else if (targetDt in IntegerDatatypes && floor(cv) == cv) {
                                if ((vardeclDt == VarDeclType.MEMORY && cv in 1.0..3.0) || (vardeclDt != VarDeclType.MEMORY && cv in 1.0..8.0)) {
                                    // replace by several DECs (a bit less when dealing with memory targets)
                                    val decs = AnonymousScope(mutableListOf(), assignment.position)
                                    repeat(cv.toInt()) {
                                        decs.statements.add(PostIncrDecr(assignment.target, "--", assignment.position))
                                    }
                                    return decs
                                }
                            }
                        }
                        "*" -> if (cv == 1.0) {
                            optimizationsDone++
                            return NopStatement.insteadOf(assignment)
                        }
                        "/" -> if (cv == 1.0) {
                            optimizationsDone++
                            return NopStatement.insteadOf(assignment)
                        }
                        "**" -> if (cv == 1.0) {
                            optimizationsDone++
                            return NopStatement.insteadOf(assignment)
                        }
                        "|" -> if (cv == 0.0) {
                            optimizationsDone++
                            return NopStatement.insteadOf(assignment)
                        }
                        "^" -> if (cv == 0.0) {
                            optimizationsDone++
                            return NopStatement.insteadOf(assignment)
                        }
                        "<<" -> {
                            if (cv == 0.0) {
                                optimizationsDone++
                                return NopStatement.insteadOf(assignment)
                            }
                            if (((targetDt == DataType.UWORD || targetDt == DataType.WORD) && cv > 15.0) ||
                                    ((targetDt == DataType.UBYTE || targetDt == DataType.BYTE) && cv > 7.0)) {
                                assignment.value = NumericLiteralValue.optimalInteger(0, assignment.value.position)
                                assignment.value.linkParents(assignment)
                                optimizationsDone++
                            } else {
                                // replace by in-place lsl(...) call
                                val scope = AnonymousScope(mutableListOf(), assignment.position)
                                var numshifts = cv.toInt()
                                while (numshifts > 0) {
                                    scope.statements.add(FunctionCallStatement(IdentifierReference(listOf("lsl"), assignment.position),
                                            mutableListOf(bexpr.left), true, assignment.position))
                                    numshifts--
                                }
                                optimizationsDone++
                                return scope
                            }
                        }
                        ">>" -> {
                            if (cv == 0.0) {
                                optimizationsDone++
                                return NopStatement.insteadOf(assignment)
                            }
                            if ((targetDt == DataType.UWORD && cv > 15.0) || (targetDt == DataType.UBYTE && cv > 7.0)) {
                                assignment.value = NumericLiteralValue.optimalInteger(0, assignment.value.position)
                                assignment.value.linkParents(assignment)
                                optimizationsDone++
                            } else {
                                // replace by in-place lsr(...) call
                                val scope = AnonymousScope(mutableListOf(), assignment.position)
                                var numshifts = cv.toInt()
                                while (numshifts > 0) {
                                    scope.statements.add(FunctionCallStatement(IdentifierReference(listOf("lsr"), assignment.position),
                                            mutableListOf(bexpr.left), true, assignment.position))
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


        return super.visit(assignment)
    }

    override fun visit(scope: AnonymousScope): Statement {
        val linesToRemove = deduplicateAssignments(scope.statements)
        if(linesToRemove.isNotEmpty()) {
            linesToRemove.reversed().forEach{scope.statements.removeAt(it)}
        }
        return super.visit(scope)
    }

    override fun visit(label: Label): Statement {
        // remove duplicate labels
        val stmts = label.definingScope().statements
        val startIdx = stmts.indexOf(label)
        if(startIdx< stmts.lastIndex && stmts[startIdx+1] == label)
            return NopStatement.insteadOf(label)

        return super.visit(label)
    }
}



