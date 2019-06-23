package prog8.optimizing

import prog8.ast.*
import prog8.parser.ParsingFailedError


fun Program.constantFold() {
    val optimizer = ConstantFolding(this)
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


fun Program.optimizeStatements(optimizeInlining: Boolean): Int {
    val optimizer = StatementOptimizer(this, optimizeInlining)
    optimizer.process(this)
    for(scope in optimizer.scopesToFlatten.reversed()) {
        val namescope = scope.parent as INameScope
        val idx = namescope.statements.indexOf(scope as IStatement)
        if(idx>=0) {
            namescope.statements[idx] = NopStatement(scope.position)
            namescope.statements.addAll(idx, scope.statements)
        }
    }
    modules.forEach { it.linkParents() }   // re-link in final configuration

    return optimizer.optimizationsDone
}

fun Program.simplifyExpressions() : Int {
    val optimizer = SimplifyExpressions(this)
    optimizer.process(this)
    return optimizer.optimizationsDone
}
