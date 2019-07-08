package prog8.optimizing

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.processing.IAstProcessor
import prog8.ast.statements.*
import prog8.compiler.target.c64.Petscii
import prog8.functions.BuiltinFunctions
import kotlin.math.floor


/*
    todo: subroutines with 1 or 2 byte args or 1 word arg can be converted to asm sub calling convention (args in registers)
    todo analyse for unreachable code and remove that (f.i. code after goto or return that has no label so can never be jumped to) + print warning about this
*/

internal class StatementOptimizer(private val program: Program, private val optimizeInlining: Boolean) : IAstProcessor {
    var optimizationsDone: Int = 0
        private set
    var scopesToFlatten = mutableListOf<INameScope>()

    private val pureBuiltinFunctions = BuiltinFunctions.filter { it.value.pure }
    private val callgraph = CallGraph(program)

    companion object {
        private var generatedLabelSequenceNumber = 0
    }

    override fun process(program: Program) {
        removeUnusedCode(callgraph)
        if(optimizeInlining) {
            inlineSubroutines(callgraph)
        }
        super.process(program)
    }

    private fun inlineSubroutines(callgraph: CallGraph) {
        val entrypoint = program.entrypoint()
        program.modules.forEach {
            callgraph.forAllSubroutines(it) { sub ->
                if(sub!==entrypoint && !sub.isAsmSubroutine) {
                    if (sub.statements.size <= 3 && !sub.expensiveToInline) {
                        sub.calledBy.toList().forEach { caller -> inlineSubroutine(sub, caller) }
                    } else if (sub.calledBy.size==1 && sub.statements.size < 50) {
                        inlineSubroutine(sub, sub.calledBy[0])
                    } else if(sub.calledBy.size<=3 && sub.statements.size < 10 && !sub.expensiveToInline) {
                        sub.calledBy.toList().forEach { caller -> inlineSubroutine(sub, caller) }
                    }
                }
            }
        }
    }

    private fun inlineSubroutine(sub: Subroutine, caller: Node) {
        // if the sub is called multiple times from the isSameAs scope, we can't inline (would result in duplicate definitions)
        // (unless we add a sequence number to all vars/labels and references to them in the inlined code, but I skip that for now)
        val scope = caller.definingScope()
        if(sub.calledBy.count { it.definingScope()===scope } > 1)
            return
        if(caller !is IFunctionCall || caller !is IStatement || sub.statements.any { it is Subroutine })
            return

        if(sub.parameters.isEmpty() && sub.returntypes.isEmpty()) {
            // sub without params and without return value can be easily inlined
            val parent = caller.parent as INameScope
            val inlined = AnonymousScope(sub.statements.toMutableList(), caller.position)
            parent.statements[parent.statements.indexOf(caller)] = inlined
            // replace return statements in the inlined sub by a jump to the end of it
            var endlabel = inlined.statements.last() as? Label
            if(endlabel==null) {
                endlabel = makeLabel("_prog8_auto_sub_end", inlined.statements.last().position)
                inlined.statements.add(endlabel)
                endlabel.parent = inlined
            }
            val returns = inlined.statements.withIndex().filter { iv -> iv.value is Return }.map { iv -> Pair(iv.index, iv.value as Return)}
            for(returnIdx in returns) {
                assert(returnIdx.second.values.isEmpty())
                val jump = Jump(null, IdentifierReference(listOf(endlabel.name), returnIdx.second.position), null, returnIdx.second.position)
                inlined.statements[returnIdx.first] = jump
            }
            inlined.linkParents(caller.parent)
            sub.calledBy.remove(caller)     // if there are no callers left, the sub will be removed automatically later
            optimizationsDone++
        } else {
            // TODO inline subroutine that has params or returnvalues or both
        }
    }

    private fun makeLabel(name: String, position: Position): Label {
        generatedLabelSequenceNumber++
        return Label("${name}_$generatedLabelSequenceNumber", position)
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

    override fun process(block: Block): IStatement {
        if("force_output" !in block.options()) {
            if (block.containsNoCodeNorVars()) {
                optimizationsDone++
                printWarning("removing empty block '${block.name}'", block.position)
                return NopStatement(block.position)
            }

            if (block !in callgraph.usedSymbols) {
                optimizationsDone++
                printWarning("removing unused block '${block.name}'", block.position)
                return NopStatement(block.position)  // remove unused block
            }
        }

        return super.process(block)
    }

    override fun process(subroutine: Subroutine): IStatement {
        super.process(subroutine)
        val forceOutput = "force_output" in subroutine.definingBlock().options()
        if(subroutine.asmAddress==null && !forceOutput) {
            if(subroutine.containsNoCodeNorVars()) {
                printWarning("removing empty subroutine '${subroutine.name}'", subroutine.position)
                optimizationsDone++
                return NopStatement(subroutine.position)
            }
        }

        val linesToRemove = deduplicateAssignments(subroutine.statements)
        if(linesToRemove.isNotEmpty()) {
            linesToRemove.reversed().forEach{subroutine.statements.removeAt(it)}
        }

        if(subroutine.canBeAsmSubroutine) {
            optimizationsDone++
            return subroutine.intoAsmSubroutine()   // TODO this doesn't work yet due to parameter vardecl issue

            // TODO fix parameter passing so this also works:
//            asmsub aa(byte arg @ Y) -> clobbers() -> () {
//                byte local = arg            ; @todo fix 'undefined symbol arg' by some sort of alias name for the parameter
//                A=44
//            }

        }

        if(subroutine !in callgraph.usedSymbols && !forceOutput) {
            printWarning("removing unused subroutine '${subroutine.name}'", subroutine.position)
            optimizationsDone++
            return NopStatement(subroutine.position)        // remove unused subroutine
        }

        return subroutine
    }

    override fun process(decl: VarDecl): IStatement {
        val forceOutput = "force_output" in decl.definingBlock().options()
        if(decl !in callgraph.usedSymbols && !forceOutput) {
            if(decl.type!=VarDeclType.CONST)
                printWarning("removing unused variable '${decl.name}'", decl.position)
            optimizationsDone++
            return NopStatement(decl.position)        // remove unused variable
        }

        return super.process(decl)
    }

    private fun deduplicateAssignments(statements: List<IStatement>): MutableList<Int> {
        // removes 'duplicate' assignments that assign the isSameAs target
        val linesToRemove = mutableListOf<Int>()
        var previousAssignmentLine: Int? = null
        for (i in 0 until statements.size) {
            val stmt = statements[i] as? Assignment
            if (stmt != null && stmt.value is LiteralValue) {
                if (previousAssignmentLine == null) {
                    previousAssignmentLine = i
                    continue
                } else {
                    val prev = statements[previousAssignmentLine] as Assignment
                    if (prev.targets.size == 1 && stmt.targets.size == 1 && prev.targets[0].isSameAs(stmt.targets[0], program)) {
                        // get rid of the previous assignment, if the target is not MEMORY
                        if (prev.targets[0].isNotMemory(program.namespace))
                            linesToRemove.add(previousAssignmentLine)
                    }
                    previousAssignmentLine = i
                }
            } else
                previousAssignmentLine = null
        }
        return linesToRemove
    }

    override fun process(functionCallStatement: FunctionCallStatement): IStatement {
        if(functionCallStatement.target.nameInSource.size==1 && functionCallStatement.target.nameInSource[0] in BuiltinFunctions) {
            val functionName = functionCallStatement.target.nameInSource[0]
            if (functionName in pureBuiltinFunctions) {
                printWarning("statement has no effect (function return value is discarded)", functionCallStatement.position)
                optimizationsDone++
                return NopStatement(functionCallStatement.position)
            }
        }

        if(functionCallStatement.target.nameInSource==listOf("c64scr", "print") ||
                functionCallStatement.target.nameInSource==listOf("c64scr", "print_p")) {
            // printing a literal string of just 2 or 1 characters is replaced by directly outputting those characters
            if(functionCallStatement.arglist.single() is LiteralValue)
                throw AstException("string argument should be on heap already")
            val stringVar = functionCallStatement.arglist.single() as? IdentifierReference
            if(stringVar!=null) {
                val heapId = stringVar.heapId(program.namespace)
                val string = program.heap.get(heapId).str!!
                if(string.length==1) {
                    val petscii = Petscii.encodePetscii(string, true)[0]
                    functionCallStatement.arglist.clear()
                    functionCallStatement.arglist.add(LiteralValue.optimalInteger(petscii, functionCallStatement.position))
                    functionCallStatement.target = IdentifierReference(listOf("c64", "CHROUT"), functionCallStatement.target.position)
                    optimizationsDone++
                    return functionCallStatement
                } else if(string.length==2) {
                    val petscii = Petscii.encodePetscii(string, true)
                    val scope = AnonymousScope(mutableListOf(), functionCallStatement.position)
                    scope.statements.add(FunctionCallStatement(IdentifierReference(listOf("c64", "CHROUT"), functionCallStatement.target.position),
                            mutableListOf(LiteralValue.optimalInteger(petscii[0], functionCallStatement.position)), functionCallStatement.position))
                    scope.statements.add(FunctionCallStatement(IdentifierReference(listOf("c64", "CHROUT"), functionCallStatement.target.position),
                            mutableListOf(LiteralValue.optimalInteger(petscii[1], functionCallStatement.position)), functionCallStatement.position))
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
                return NopStatement(functionCallStatement.position)
            }
        }

        return super.process(functionCallStatement)
    }

    override fun process(functionCall: FunctionCall): IExpression {
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
            if(first is Return && first.values.size==1) {
                val constval = first.values[0].constValue(program)
                if(constval!=null)
                    return constval
            }
        }
        return super.process(functionCall)
    }

    override fun process(ifStatement: IfStatement): IStatement {
        super.process(ifStatement)

        if(ifStatement.truepart.containsNoCodeNorVars() && ifStatement.elsepart.containsNoCodeNorVars()) {
            optimizationsDone++
            return NopStatement(ifStatement.position)
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

    override fun process(forLoop: ForLoop): IStatement {
        super.process(forLoop)
        if(forLoop.body.containsNoCodeNorVars()) {
            // remove empty for loop
            optimizationsDone++
            return NopStatement(forLoop.position)
        } else if(forLoop.body.statements.size==1) {
            val loopvar = forLoop.body.statements[0] as? VarDecl
            if(loopvar!=null && loopvar.name==forLoop.loopVar?.nameInSource?.singleOrNull()) {
                // remove empty for loop
                optimizationsDone++
                return NopStatement(forLoop.position)
            }
        }


        val range = forLoop.iterable as? RangeExpr
        if(range!=null) {
            if(range.size()==1) {
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
        val constvalue = whileLoop.condition.constValue(program)
        if(constvalue!=null) {
            return if(constvalue.asBooleanValue){
                // always true -> print a warning, and optimize into body + jump (if there are no continue and break statements)
                printWarning("condition is always true", whileLoop.position)
                if(hasContinueOrBreak(whileLoop.body))
                    return whileLoop
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

    private fun hasContinueOrBreak(scope: INameScope): Boolean {

        class Searcher: IAstProcessor
        {
            var count=0

            override fun process(breakStmt: Break): IStatement {
                count++
                return super.process(breakStmt)
            }

            override fun process(contStmt: Continue): IStatement {
                count++
                return super.process(contStmt)
            }
        }
        val s=Searcher()
        for(stmt in scope.statements) {
            stmt.process(s)
            if(s.count>0)
                return true
        }
        return s.count > 0
    }

    override fun process(jump: Jump): IStatement {
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
                return NopStatement(jump.position)
            }
        }

        return jump
    }

    override fun process(assignment: Assignment): IStatement {
        if(assignment.aug_op!=null)
            throw AstException("augmented assignments should have been converted to normal assignments before this optimizer")

        if(assignment.targets.size==1) {
            val target=assignment.targets[0]
            if(target isSameAs assignment.value) {
                optimizationsDone++
                return NopStatement(assignment.position)
            }
            val targetDt = target.inferType(program, assignment)
            val bexpr=assignment.value as? BinaryExpression
            if(bexpr!=null) {
                val cv = bexpr.right.constValue(program)?.asNumericValue?.toDouble()
                if(cv==null) {
                    if(bexpr.operator=="+" && targetDt!= DataType.FLOAT) {
                        if (bexpr.left isSameAs bexpr.right && target isSameAs bexpr.left) {
                            bexpr.operator = "*"
                            bexpr.right = LiteralValue.optimalInteger(2, assignment.value.position)
                            optimizationsDone++
                            return assignment
                        }
                    }
                } else {
                    if (target isSameAs bexpr.left) {
                        // remove assignments that have no effect  X=X , X+=0, X-=0, X*=1, X/=1, X//=1, A |= 0, A ^= 0, A<<=0, etc etc
                        // A = A <operator> B
                        val vardeclDt = (target.identifier?.targetVarDecl(program.namespace))?.type

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

    override fun process(scope: AnonymousScope): IStatement {
        val linesToRemove = deduplicateAssignments(scope.statements)
        if(linesToRemove.isNotEmpty()) {
            linesToRemove.reversed().forEach{scope.statements.removeAt(it)}
        }

        if(scope.parent is INameScope) {
            scopesToFlatten.add(scope)  // get rid of the anonymous scope
        }

        return super.process(scope)
    }

    override fun process(label: Label): IStatement {
        // remove duplicate labels
        val stmts = label.definingScope().statements
        val startIdx = stmts.indexOf(label)
        if(startIdx<(stmts.size-1) && stmts[startIdx+1] == label)
            return NopStatement(label.position)

        return super.process(label)
    }
}



