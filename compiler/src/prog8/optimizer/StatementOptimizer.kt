package prog8.optimizer

import prog8.ast.INameScope
import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.processing.IAstModifyingVisitor
import prog8.ast.processing.IAstVisitor
import prog8.ast.statements.*
import prog8.compiler.target.c64.Petscii
import prog8.compiler.target.c64.codegen2.AssemblyError
import prog8.functions.BuiltinFunctions
import kotlin.math.floor


/*
    TODO: analyse for unreachable code and remove that (f.i. code after goto or return that has no label so can never be jumped to) + print warning about this
    TODO: proper inlining of small subroutines (correctly renaming/relocating all variables in them and refs to those as well, or restrict to subs without variables?)
*/


internal class StatementOptimizer(private val program: Program) : IAstModifyingVisitor {
    var optimizationsDone: Int = 0
        private set

    private val pureBuiltinFunctions = BuiltinFunctions.filter { it.value.pure }
    private val callgraph = CallGraph(program)

    override fun visit(program: Program) {
        removeUnusedCode(callgraph)
        super.visit(program)
    }

    private fun removeUnusedCode(callgraph: CallGraph) {
        // remove all subroutines that aren't called, or are empty
        val removeSubroutines = mutableSetOf<Subroutine>()
        val entrypoint = program.entrypoint()
        program.modules.forEach {
            callgraph.forAllSubroutines(it) { sub ->
                if (sub !== entrypoint && !sub.keepAlways && (sub.calledBy.isEmpty() || (sub.containsNoCodeNorVars() && !sub.isAsmSubroutine)))
                    removeSubroutines.add(sub)
            }
        }

        if (removeSubroutines.isNotEmpty()) {
            removeSubroutines.forEach {
                it.definingScope().remove(it)
            }
        }

        val removeBlocks = mutableSetOf<Block>()
        program.modules.flatMap { it.statements }.filterIsInstance<Block>().forEach { block ->
            if (block.containsNoCodeNorVars() && "force_output" !in block.options())
                removeBlocks.add(block)
        }

        if (removeBlocks.isNotEmpty()) {
            removeBlocks.forEach { it.definingScope().remove(it) }
        }

        // remove modules that are not imported, or are empty (unless it's a library modules)
        val removeModules = mutableSetOf<Module>()
        program.modules.forEach {
            if (!it.isLibraryModule && (it.importedBy.isEmpty() || it.containsNoCodeNorVars()))
                removeModules.add(it)
        }

        if (removeModules.isNotEmpty()) {
            program.modules.removeAll(removeModules)
        }
    }

    override fun visit(block: Block): Statement {
        if("force_output" !in block.options()) {
            if (block.containsNoCodeNorVars()) {
                optimizationsDone++
                printWarning("removing empty block '${block.name}'", block.position)
                return NopStatement.insteadOf(block)
            }

            if (block !in callgraph.usedSymbols) {
                optimizationsDone++
                printWarning("removing unused block '${block.name}'", block.position)
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
                printWarning("removing empty subroutine '${subroutine.name}'", subroutine.position)
                optimizationsDone++
                return NopStatement.insteadOf(subroutine)
            }
        }

        val linesToRemove = deduplicateAssignments(subroutine.statements)
        if(linesToRemove.isNotEmpty()) {
            linesToRemove.reversed().forEach{subroutine.statements.removeAt(it)}
        }

        if(subroutine !in callgraph.usedSymbols && !forceOutput) {
            printWarning("removing unused subroutine '${subroutine.name}'", subroutine.position)
            optimizationsDone++
            return NopStatement.insteadOf(subroutine)
        }

        return subroutine
    }

    override fun visit(decl: VarDecl): Statement {
        val forceOutput = "force_output" in decl.definingBlock().options()
        if(decl !in callgraph.usedSymbols && !forceOutput) {
            if(decl.type == VarDeclType.VAR)
                printWarning("removing unused variable ${decl.type} '${decl.name}'", decl.position)
            optimizationsDone++
            return NopStatement.insteadOf(decl)
        }

        return super.visit(decl)
    }

    private fun deduplicateAssignments(statements: List<Statement>): MutableList<Int> {
        // removes 'duplicate' assignments that assign the isSameAs target
        val linesToRemove = mutableListOf<Int>()
        var previousAssignmentLine: Int? = null
        for (i in 0 until statements.size) {
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
                printWarning("statement has no effect (function return value is discarded)", functionCallStatement.position)
                optimizationsDone++
                return NopStatement.insteadOf(functionCallStatement)
            }
        }

        if(functionCallStatement.target.nameInSource==listOf("c64scr", "print") ||
                functionCallStatement.target.nameInSource==listOf("c64scr", "print_p")) {
            // printing a literal string of just 2 or 1 characters is replaced by directly outputting those characters
            val stringVar = functionCallStatement.arglist.single() as? IdentifierReference
            if(stringVar!=null) {
                val heapId = stringVar.heapId(program.namespace)
                val string = program.heap.get(heapId).str!!
                if(string.length==1) {
                    val petscii = Petscii.encodePetscii(string, true)[0]
                    functionCallStatement.arglist.clear()
                    functionCallStatement.arglist.add(NumericLiteralValue.optimalInteger(petscii.toInt(), functionCallStatement.position))
                    functionCallStatement.target = IdentifierReference(listOf("c64", "CHROUT"), functionCallStatement.target.position)
                    optimizationsDone++
                    return functionCallStatement
                } else if(string.length==2) {
                    val petscii = Petscii.encodePetscii(string, true)
                    val scope = AnonymousScope(mutableListOf(), functionCallStatement.position)
                    scope.statements.add(FunctionCallStatement(IdentifierReference(listOf("c64", "CHROUT"), functionCallStatement.target.position),
                            mutableListOf(NumericLiteralValue.optimalInteger(petscii[0].toInt(), functionCallStatement.position)), functionCallStatement.position))
                    scope.statements.add(FunctionCallStatement(IdentifierReference(listOf("c64", "CHROUT"), functionCallStatement.target.position),
                            mutableListOf(NumericLiteralValue.optimalInteger(petscii[1].toInt(), functionCallStatement.position)), functionCallStatement.position))
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
                return FunctionCallStatement(first.identifier, functionCallStatement.arglist, functionCallStatement.position)
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
                return FunctionCall(first.identifier, functionCall.arglist, functionCall.position)
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
                // always true -> print a warning, and optimize into body + jump (if there are no continue and break statements)
                printWarning("condition is always true", whileLoop.position)
                if(hasContinueOrBreak(whileLoop.body))
                    return whileLoop
                val label = Label("_prog8_back", whileLoop.condition.position)
                whileLoop.body.statements.add(0, label)
                whileLoop.body.statements.add(Jump(null,
                        IdentifierReference(listOf("_prog8_back"), whileLoop.condition.position),
                        null, whileLoop.condition.position))
                optimizationsDone++
                return whileLoop.body
            } else {
                // always false -> ditch whole statement
                printWarning("condition is always false", whileLoop.position)
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
                printWarning("condition is always true", repeatLoop.position)
                if(hasContinueOrBreak(repeatLoop.body))
                    repeatLoop
                else {
                    optimizationsDone++
                    repeatLoop.body
                }
            } else {
                // always false -> print a warning, and optimize into body + jump (if there are no continue and break statements)
                printWarning("condition is always false", repeatLoop.position)
                if(hasContinueOrBreak(repeatLoop.body))
                    return repeatLoop
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
            throw AstException("augmented assignments should have been converted to normal assignments before this optimizer")

        if(assignment.target isSameAs assignment.value) {
            if(assignment.target.isNotMemory(program.namespace)) {
                optimizationsDone++
                return NopStatement.insteadOf(assignment)
            }
        }
        val targetIDt = assignment.target.inferType(program, assignment)
        if(!targetIDt.isKnown)
            throw AssemblyError("can't infer type of assignment target")
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
                                return NopStatement.insteadOf(assignment)
                            }
                            if (((targetDt == DataType.UWORD || targetDt == DataType.WORD) && cv > 15.0) ||
                                    ((targetDt == DataType.UBYTE || targetDt == DataType.BYTE) && cv > 7.0)) {
                                assignment.value = NumericLiteralValue.optimalInteger(0, assignment.value.position)
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
        if(startIdx<(stmts.size-1) && stmts[startIdx+1] == label)
            return NopStatement.insteadOf(label)

        return super.visit(label)
    }
}



internal class FlattenAnonymousScopesAndRemoveNops: IAstVisitor {
    private var scopesToFlatten = mutableListOf<INameScope>()
    private val nopStatements = mutableListOf<NopStatement>()

    override fun visit(program: Program) {
        super.visit(program)
        for(scope in scopesToFlatten.reversed()) {
            val namescope = scope.parent as INameScope
            val idx = namescope.statements.indexOf(scope as Statement)
            if(idx>=0) {
                val nop = NopStatement.insteadOf(namescope.statements[idx])
                nop.parent = namescope as Node
                namescope.statements[idx] = nop
                namescope.statements.addAll(idx, scope.statements)
                scope.statements.forEach { it.parent = namescope }
                visit(nop)
            }
        }

        this.nopStatements.forEach {
            it.definingScope().remove(it)
        }
    }

    override fun visit(scope: AnonymousScope) {
        if(scope.parent is INameScope) {
            scopesToFlatten.add(scope)  // get rid of the anonymous scope
        }

        return super.visit(scope)
    }

    override fun visit(nopStatement: NopStatement) {
        nopStatements.add(nopStatement)
    }
}
