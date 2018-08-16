package il65.optimizing

import il65.ast.*


fun Module.optimizeStatements(globalNamespace: INameScope) {
    val optimizer = StatementOptimizer(globalNamespace)
    this.process(optimizer)
    if(optimizer.optimizationsDone==0)
        println("[${this.name}] 0 optimizations performed")

    while(optimizer.optimizationsDone>0) {
        println("[${this.name}] ${optimizer.optimizationsDone} optimizations performed")
        optimizer.reset()
        this.process(optimizer)
    }
    this.linkParents()  // re-link in final configuration
}


class StatementOptimizer(private val globalNamespace: INameScope) : IAstProcessor {
    var optimizationsDone: Int = 0
        private set

    fun reset() {
        optimizationsDone = 0
    }

    override fun process(ifStatement: IfStatement): IStatement {
        super.process(ifStatement)
        val constvalue = ifStatement.condition.constValue(globalNamespace)
        if(constvalue!=null) {
            return if(constvalue.asBoolean()) {
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
}