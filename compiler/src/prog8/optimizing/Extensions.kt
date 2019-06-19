package prog8.optimizing

import prog8.ast.AstException
import prog8.ast.Program
import prog8.parser.ParsingFailedError


fun Program.constantFold() {
    val optimizer = ConstantFolding(this.namespace, heap)
    try {
        optimizer.process(this)
    } catch (ax: AstException) {
        optimizer.addError(ax)
    }

    while(optimizer.errors.isEmpty() && optimizer.optimizationsDone>0) {
        optimizer.optimizationsDone = 0
        optimizer.process(this)
    }

    if(optimizer.errors.isNotEmpty()) {
        optimizer.errors.forEach { System.err.println(it) }
        throw ParsingFailedError("There are ${optimizer.errors.size} errors.")
    } else {
        modules.forEach { it.linkParents() }   // re-link in final configuration
    }
}


fun Program.optimizeStatements(): Int {
    val optimizer = StatementOptimizer(namespace, heap)
    optimizer.process(this)
    for(stmt in optimizer.statementsToRemove) {
        val scope=stmt.definingScope()
        scope.remove(stmt)
    }
    modules.forEach { it.linkParents() }   // re-link in final configuration

    return optimizer.optimizationsDone
}

fun Program.simplifyExpressions() : Int {
    val optimizer = SimplifyExpressions(namespace, heap)
    optimizer.process(this)
    return optimizer.optimizationsDone
}
