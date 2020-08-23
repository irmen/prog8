package prog8.optimizer

import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.processing.IAstVisitor
import prog8.ast.statements.*
import prog8.compiler.target.CompilationTarget
import prog8.functions.BuiltinFunctions
import kotlin.math.floor


internal class StatementOptimizer(private val program: Program,
                                  private val errors: ErrorReporter) : AstWalker() {

    private val noModifications = emptyList<IAstModification>()
    private val callgraph = CallGraph(program)
    private val pureBuiltinFunctions = BuiltinFunctions.filter { it.value.pure }

    override fun after(block: Block, parent: Node): Iterable<IAstModification> {
        if("force_output" !in block.options()) {
            if (block.containsNoCodeNorVars()) {
                errors.warn("removing empty block '${block.name}'", block.position)
                return listOf(IAstModification.Remove(block, parent))
            }

            if (block !in callgraph.usedSymbols) {
                errors.warn("removing unused block '${block.name}'", block.position)
                return listOf(IAstModification.Remove(block, parent))
            }
        }
        return noModifications
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        val forceOutput = "force_output" in subroutine.definingBlock().options()
        if(subroutine.asmAddress==null && !forceOutput) {
            if(subroutine.containsNoCodeNorVars()) {
                errors.warn("removing empty subroutine '${subroutine.name}'", subroutine.position)
                val removals = callgraph.calledBy.getValue(subroutine).map {
                    IAstModification.Remove(it, it.parent)
                }.toMutableList()
                removals += IAstModification.Remove(subroutine, parent)
                return removals
            }
        }

        val linesToRemove = deduplicateAssignments(subroutine.statements)
        if(linesToRemove.isNotEmpty()) {
            linesToRemove.reversed().forEach{subroutine.statements.removeAt(it)}
        }

        if(subroutine !in callgraph.usedSymbols && !forceOutput) {
            errors.warn("removing unused subroutine '${subroutine.name}'", subroutine.position)
            return listOf(IAstModification.Remove(subroutine, parent))
        }

        return noModifications
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        val linesToRemove = deduplicateAssignments(scope.statements)
        return linesToRemove.reversed().map { IAstModification.Remove(scope.statements[it], scope) }
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val forceOutput = "force_output" in decl.definingBlock().options()
        if(decl !in callgraph.usedSymbols && !forceOutput) {
            if(decl.type == VarDeclType.VAR)
                errors.warn("removing unused variable '${decl.name}'", decl.position)

            return listOf(IAstModification.Remove(decl, parent))
        }

        return noModifications
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        if(functionCallStatement.target.nameInSource.size==1 && functionCallStatement.target.nameInSource[0] in BuiltinFunctions) {
            val functionName = functionCallStatement.target.nameInSource[0]
            if (functionName in pureBuiltinFunctions) {
                errors.warn("statement has no effect (function return value is discarded)", functionCallStatement.position)
                return listOf(IAstModification.Remove(functionCallStatement, parent))
            }
        }

        // printing a literal string of just 2 or 1 characters is replaced by directly outputting those characters
        // this is a C-64 specific optimization
        if(functionCallStatement.target.nameInSource==listOf("c64scr", "print")) {
            val arg = functionCallStatement.args.single()
            val stringVar: IdentifierReference?
            stringVar = if(arg is AddressOf) {
                arg.identifier
            } else {
                arg as? IdentifierReference
            }
            if(stringVar!=null) {
                val vardecl = stringVar.targetVarDecl(program.namespace)!!
                val string = vardecl.value as? StringLiteralValue
                if(string!=null) {
                    val pos = functionCallStatement.position
                    if (string.value.length == 1) {
                        val firstCharEncoded = CompilationTarget.encodeString(string.value, string.altEncoding)[0]
                        val chrout = FunctionCallStatement(
                                IdentifierReference(listOf("c64", "CHROUT"), pos),
                                mutableListOf(NumericLiteralValue(DataType.UBYTE, firstCharEncoded.toInt(), pos)),
                                functionCallStatement.void, pos
                        )
                        return listOf(IAstModification.ReplaceNode(functionCallStatement, chrout, parent))
                    } else if (string.value.length == 2) {
                        val firstTwoCharsEncoded = CompilationTarget.encodeString(string.value.take(2), string.altEncoding)
                        val chrout1 = FunctionCallStatement(
                                IdentifierReference(listOf("c64", "CHROUT"), pos),
                                mutableListOf(NumericLiteralValue(DataType.UBYTE, firstTwoCharsEncoded[0].toInt(), pos)),
                                functionCallStatement.void, pos
                        )
                        val chrout2 = FunctionCallStatement(
                                IdentifierReference(listOf("c64", "CHROUT"), pos),
                                mutableListOf(NumericLiteralValue(DataType.UBYTE, firstTwoCharsEncoded[1].toInt(), pos)),
                                functionCallStatement.void, pos
                        )
                        val anonscope = AnonymousScope(mutableListOf(), pos)
                        anonscope.statements.add(chrout1)
                        anonscope.statements.add(chrout2)
                        return listOf(IAstModification.ReplaceNode(functionCallStatement, anonscope, parent))
                    }
                }
            }
        }

        // if the first instruction in the called subroutine is a return statement, remove the jump altogeter
        val subroutine = functionCallStatement.target.targetSubroutine(program.namespace)
        if(subroutine!=null) {
            val first = subroutine.statements.asSequence().filterNot { it is VarDecl || it is Directive }.firstOrNull()
            if(first is Return)
                return listOf(IAstModification.Remove(functionCallStatement, parent))
        }

        return noModifications
    }

    override fun before(functionCall: FunctionCall, parent: Node): Iterable<IAstModification> {
        // if the first instruction in the called subroutine is a return statement with constant value, replace with the constant value
        val subroutine = functionCall.target.targetSubroutine(program.namespace)
        if(subroutine!=null) {
            val first = subroutine.statements.asSequence().filterNot { it is VarDecl || it is Directive }.firstOrNull()
            if(first is Return && first.value!=null) {
                val constval = first.value?.constValue(program)
                if(constval!=null)
                    return listOf(IAstModification.ReplaceNode(functionCall, constval, parent))
            }
        }
        return noModifications
    }

    override fun after(ifStatement: IfStatement, parent: Node): Iterable<IAstModification> {
        // remove empty if statements
        if(ifStatement.truepart.containsNoCodeNorVars() && ifStatement.elsepart.containsNoCodeNorVars())
            return listOf(IAstModification.Remove(ifStatement, parent))

        // empty true part? switch with the else part
        if(ifStatement.truepart.containsNoCodeNorVars() && ifStatement.elsepart.containsCodeOrVars()) {
            val invertedCondition = PrefixExpression("not", ifStatement.condition, ifStatement.condition.position)
            val emptyscope = AnonymousScope(mutableListOf(), ifStatement.elsepart.position)
            val truepart = AnonymousScope(ifStatement.elsepart.statements, ifStatement.truepart.position)
            return listOf(
                    IAstModification.ReplaceNode(ifStatement.condition, invertedCondition, ifStatement),
                    IAstModification.ReplaceNode(ifStatement.truepart, truepart, ifStatement),
                    IAstModification.ReplaceNode(ifStatement.elsepart, emptyscope, ifStatement)
            )
        }

        val constvalue = ifStatement.condition.constValue(program)
        if(constvalue!=null) {
            return if(constvalue.asBooleanValue){
                // always true -> keep only if-part
                errors.warn("condition is always true", ifStatement.position)
                listOf(IAstModification.ReplaceNode(ifStatement, ifStatement.truepart, parent))
            } else {
                // always false -> keep only else-part
                errors.warn("condition is always false", ifStatement.position)
                listOf(IAstModification.ReplaceNode(ifStatement, ifStatement.elsepart, parent))
            }
        }

        return noModifications
    }

    override fun after(forLoop: ForLoop, parent: Node): Iterable<IAstModification> {
        if(forLoop.body.containsNoCodeNorVars()) {
            errors.warn("removing empty for loop", forLoop.position)
            return listOf(IAstModification.Remove(forLoop, parent))
        } else if(forLoop.body.statements.size==1) {
            val loopvar = forLoop.body.statements[0] as? VarDecl
            if(loopvar!=null && loopvar.name==forLoop.loopVar.nameInSource.singleOrNull()) {
                // remove empty for loop (only loopvar decl in it)
                return listOf(IAstModification.Remove(forLoop, parent))
            }
        }

        val range = forLoop.iterable as? RangeExpr
        if(range!=null) {
            if(range.size()==1) {
                // for loop over a (constant) range of just a single value-- optimize the loop away
                // loopvar/reg = range value , follow by block
                val scope = AnonymousScope(mutableListOf(), forLoop.position)
                scope.statements.add(Assignment(AssignTarget(forLoop.loopVar, null, null, forLoop.position), range.from, forLoop.position))
                scope.statements.addAll(forLoop.body.statements)
                return listOf(IAstModification.ReplaceNode(forLoop, scope, parent))
            }
        }
        val iterable = (forLoop.iterable as? IdentifierReference)?.targetVarDecl(program.namespace)
        if(iterable!=null) {
            if(iterable.datatype==DataType.STR) {
                val sv = iterable.value as StringLiteralValue
                val size = sv.value.length
                if(size==1) {
                    // loop over string of length 1 -> just assign the single character
                    val character = CompilationTarget.encodeString(sv.value, sv.altEncoding)[0]
                    val byte = NumericLiteralValue(DataType.UBYTE, character, iterable.position)
                    val scope = AnonymousScope(mutableListOf(), forLoop.position)
                    scope.statements.add(Assignment(AssignTarget(forLoop.loopVar, null, null, forLoop.position), byte, forLoop.position))
                    scope.statements.addAll(forLoop.body.statements)
                    return listOf(IAstModification.ReplaceNode(forLoop, scope, parent))
                }
            }
            else if(iterable.datatype in ArrayDatatypes) {
                val size = iterable.arraysize!!.constIndex()
                if(size==1) {
                    // loop over array of length 1 -> just assign the single value
                    val av = (iterable.value as ArrayLiteralValue).value[0].constValue(program)?.number
                    if(av!=null) {
                        val scope = AnonymousScope(mutableListOf(), forLoop.position)
                        scope.statements.add(Assignment(
                                AssignTarget(forLoop.loopVar, null, null, forLoop.position), NumericLiteralValue.optimalInteger(av.toInt(), iterable.position),
                                forLoop.position))
                        scope.statements.addAll(forLoop.body.statements)
                        return listOf(IAstModification.ReplaceNode(forLoop, scope, parent))
                    }
                }
            }
        }

        return noModifications
    }

    override fun before(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> {
        val constvalue = untilLoop.untilCondition.constValue(program)
        if(constvalue!=null) {
            if(constvalue.asBooleanValue) {
                // always true -> keep only the statement block (if there are no break statements)
                errors.warn("condition is always true", untilLoop.untilCondition.position)
                if(!hasBreak(untilLoop.body))
                    return listOf(IAstModification.ReplaceNode(untilLoop, untilLoop.body, parent))
            } else {
                // always false
                val forever = RepeatLoop(null, untilLoop.body, untilLoop.position)
                return listOf(IAstModification.ReplaceNode(untilLoop, forever, parent))
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
                listOf(IAstModification.Remove(whileLoop, parent))
            }
        }
        return noModifications
    }

    override fun after(repeatLoop: RepeatLoop, parent: Node): Iterable<IAstModification> {
        val iter = repeatLoop.iterations
        if(iter!=null) {
            if(repeatLoop.body.containsNoCodeNorVars()) {
                errors.warn("empty loop removed", repeatLoop.position)
                return listOf(IAstModification.Remove(repeatLoop, parent))
            }
            val iterations = iter.constValue(program)?.number?.toInt()
            if (iterations == 0) {
                errors.warn("iterations is always 0, removed loop", iter.position)
                return listOf(IAstModification.Remove(repeatLoop, parent))
            }
            if (iterations == 1) {
                errors.warn("iterations is always 1", iter.position)
                return listOf(IAstModification.ReplaceNode(repeatLoop, repeatLoop.body, parent))
            }
        }
        return noModifications
    }

    override fun after(whenStatement: WhenStatement, parent: Node): Iterable<IAstModification> {
        // remove empty choices
        class ChoiceRemover(val choice: WhenChoice) : IAstModification {
            override fun perform() {
                whenStatement.choices.remove(choice)
            }
        }
        return whenStatement.choices
                .filter { !it.statements.containsCodeOrVars() }
                .map { ChoiceRemover(it) }
    }

    override fun after(jump: Jump, parent: Node): Iterable<IAstModification> {
        // if the jump is to the next statement, remove the jump
        val scope = jump.definingScope()
        val label = jump.identifier?.targetStatement(scope)
        if(label!=null && scope.statements.indexOf(label) == scope.statements.indexOf(jump)+1)
            return listOf(IAstModification.Remove(jump, parent))

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

                    if(rExpr.left is NumericLiteralValue && op2 in setOf("+", "*", "&", "|")) {
                        // associative operator, make sure the constant numeric value is second (right)
                        return listOf(IAstModification.SwapOperands(rExpr))
                    }

                    val rNum = (rExpr.right as? NumericLiteralValue)?.number
                    if(rNum!=null) {
                        if (op1 == "+" || op1 == "-") {
                            if (op2 == "+") {
                                // A = A +/- B + N
                                val expr2 = BinaryExpression(binExpr.left, binExpr.operator, rExpr.left, binExpr.position)
                                val addConstant = Assignment(
                                        assignment.target,
                                        BinaryExpression(binExpr.left, "+", rExpr.right, rExpr.position),
                                        assignment.position
                                )
                                return listOf(
                                        IAstModification.ReplaceNode(binExpr, expr2, binExpr.parent),
                                        IAstModification.InsertAfter(assignment, addConstant, parent))
                            } else if (op2 == "-") {
                                // A = A +/- B - N
                                val expr2 = BinaryExpression(binExpr.left, binExpr.operator, rExpr.left, binExpr.position)
                                val subConstant = Assignment(
                                        assignment.target,
                                        BinaryExpression(binExpr.left, "-", rExpr.right, rExpr.position),
                                        assignment.position
                                )
                                return listOf(
                                        IAstModification.ReplaceNode(binExpr, expr2, binExpr.parent),
                                        IAstModification.InsertAfter(assignment, subConstant, parent))
                            }
                        }
                    }
                }
            }

            if(binExpr.operator in associativeOperators && binExpr.right isSameAs assignment.target) {
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
            return listOf(IAstModification.Remove(assignment, parent))
        }

        val targetIDt = assignment.target.inferType(program, assignment)
        if(!targetIDt.isKnown)
            throw FatalAstException("can't infer type of assignment target")

        // optimize binary expressions a bit
        val targetDt = targetIDt.typeOrElse(DataType.STRUCT)
        val bexpr=assignment.value as? BinaryExpression
        if(bexpr!=null) {
            val cv = bexpr.right.constValue(program)?.number?.toDouble()
            if (cv != null && assignment.target isSameAs bexpr.left) {
                // assignments of the form:  X = X <operator> <expr>
                // remove assignments that have no effect (such as X=X+0)
                // optimize/rewrite some other expressions
                val vardeclDt = (assignment.target.identifier?.targetVarDecl(program.namespace))?.type
                when (bexpr.operator) {
                    "+" -> {
                        if (cv == 0.0) {
                            return listOf(IAstModification.Remove(assignment, parent))
                        } else if (targetDt in IntegerDatatypes && floor(cv) == cv) {
                            if (vardeclDt != VarDeclType.MEMORY && cv in 1.0..4.0) {
                                // replace by several INCs if it's not a memory address (inc on a memory mapped register doesn't work very well)
                                val incs = AnonymousScope(mutableListOf(), assignment.position)
                                repeat(cv.toInt()) {
                                    incs.statements.add(PostIncrDecr(assignment.target, "++", assignment.position))
                                }
                                return listOf(IAstModification.ReplaceNode(assignment, incs, parent))
                            }
                        }
                    }
                    "-" -> {
                        if (cv == 0.0) {
                            return listOf(IAstModification.Remove(assignment, parent))
                        } else if (targetDt in IntegerDatatypes && floor(cv) == cv) {
                            if (vardeclDt != VarDeclType.MEMORY && cv in 1.0..4.0) {
                                // replace by several DECs if it's not a memory address (dec on a memory mapped register doesn't work very well)
                                val decs = AnonymousScope(mutableListOf(), assignment.position)
                                repeat(cv.toInt()) {
                                    decs.statements.add(PostIncrDecr(assignment.target, "--", assignment.position))
                                }
                                return listOf(IAstModification.ReplaceNode(assignment, decs, parent))
                            }
                        }
                    }
                    "*" -> if (cv == 1.0) return listOf(IAstModification.Remove(assignment, parent))
                    "/" -> if (cv == 1.0) return listOf(IAstModification.Remove(assignment, parent))
                    "**" -> if (cv == 1.0) return listOf(IAstModification.Remove(assignment, parent))
                    "|" -> if (cv == 0.0) return listOf(IAstModification.Remove(assignment, parent))
                    "^" -> if (cv == 0.0) return listOf(IAstModification.Remove(assignment, parent))
                    "<<" -> {
                        if (cv == 0.0)
                            return listOf(IAstModification.Remove(assignment, parent))
                    }
                    ">>" -> {
                        if (cv == 0.0)
                            return listOf(IAstModification.Remove(assignment, parent))
                    }
                }

            }
        }
        return noModifications
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

    private fun hasBreak(scope: INameScope): Boolean {

        class Searcher: IAstVisitor
        {
            var count=0

            override fun visit(breakStmt: Break) {
                count++
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

}
