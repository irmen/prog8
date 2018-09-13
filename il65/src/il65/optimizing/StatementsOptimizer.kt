package il65.optimizing

import il65.ast.*
import il65.functions.BuiltinFunctionNames
import il65.functions.BuiltinFunctionsWithoutSideEffects


fun Module.optimizeStatements(globalNamespace: INameScope, allScopedSymbolDefinitions: MutableMap<String, IStatement>) {
    val optimizer = StatementOptimizer(globalNamespace)
    this.process(optimizer)
    optimizer.removeUnusedNodes(globalNamespace.usedNames(), allScopedSymbolDefinitions)
    if(optimizer.optimizationsDone==0)
        println("[${this.name}] 0 statement optimizations performed")

    while(optimizer.optimizationsDone>0) {
        println("[${this.name}] ${optimizer.optimizationsDone} statement optimizations performed")
        optimizer.reset()
        this.process(optimizer)
    }
    this.linkParents()  // re-link in final configuration
}

/*
    todo statement optimization: create augmented assignment from assignment that only refers to its lvalue (A=A+10, A=4*A, ...)
    todo statement optimization: X+=1, X-=1  --> X++/X--  ,
    todo remove statements that have no effect  X=X , X+=0, X-=0, X*=1, X/=1, X//=1, A |= 0, A ^= 0, A<<=0, etc etc
    todo optimize addition with self into shift 1  (A+=A -> A<<=1)
    todo assignment optimization: optimize some simple multiplications into shifts  (A*=8 -> A<<=3)
    todo analyse for unreachable code and remove that (f.i. code after goto or return that has no label so can never be jumped to)
    todo merge sequence of assignments into one (as long as the value is a constant and the target not a MEMORY type!)
    todo report more always true/always false conditions
*/

class StatementOptimizer(private val globalNamespace: INameScope) : IAstProcessor {
    var optimizationsDone: Int = 0
        private set

    private var statementsToRemove = mutableListOf<IStatement>()

    fun reset() {
        optimizationsDone = 0
    }

    override fun process(functionCall: FunctionCall): IExpression {
        val target = globalNamespace.lookup(functionCall.target.nameInSource, functionCall)
        if(target!=null)
            used(target)
        return super.process(functionCall)
    }

    override fun process(functionCall: FunctionCallStatement): IStatement {
        val target = globalNamespace.lookup(functionCall.target.nameInSource, functionCall)
        if(target!=null)
            used(target)

        if(functionCall.target.nameInSource.size==1 && BuiltinFunctionNames.contains(functionCall.target.nameInSource[0])) {
            val functionName = functionCall.target.nameInSource[0]
            if (BuiltinFunctionsWithoutSideEffects.contains(functionName)) {
                println("${functionCall.position} Warning: statement has no effect (function return value is discarded)")
                statementsToRemove.add(functionCall)
            }
        }

        return super.process(functionCall)
    }

    override fun process(jump: Jump): IStatement {
        if(jump.identifier!=null) {
            val target = globalNamespace.lookup(jump.identifier.nameInSource, jump)
            if (target != null)
                used(target)
        }
        return super.process(jump)
    }

    override fun process(ifStatement: IfStatement): IStatement {
        super.process(ifStatement)
        val constvalue = ifStatement.condition.constValue(globalNamespace)
        if(constvalue!=null) {
            return if(constvalue.asBooleanValue){
                // always true -> keep only if-part
                println("${ifStatement.position} Warning: condition is always true")
                AnonymousStatementList(ifStatement.parent, ifStatement.statements)
            } else {
                // always false -> keep only else-part
                println("${ifStatement.position} Warning: condition is always false")
                AnonymousStatementList(ifStatement.parent, ifStatement.elsepart)
            }
        }
        return ifStatement
    }

    private fun used(stmt: IStatement) {
        val scopedName = when (stmt) {
            is Label -> stmt.scopedname
            is Subroutine -> stmt.scopedname
            else -> throw AstException("invalid call target node type: ${stmt::class}")
        }
        globalNamespace.registerUsedName(scopedName)
    }

    fun removeUnusedNodes(usedNames: Set<String>, allScopedSymbolDefinitions: MutableMap<String, IStatement>) {
        for ((name, value) in allScopedSymbolDefinitions) {
            if(!usedNames.contains(name)) {
                val parentScope = value.parent as INameScope
                val localname = name.substringAfterLast(".")
                // printing every possible node that is removed can result in many dozens of warnings.
                // we chose to just print the blocks that aren't used.
                if(value is Block)
                    println("${value.position} Info: block '$localname' is never used")
                parentScope.removeStatement(value)
                optimizationsDone++
            }
        }

        for(stmt in statementsToRemove) {
            stmt.definingScope().removeStatement(stmt)
        }
    }
}