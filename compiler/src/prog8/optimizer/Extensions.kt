package prog8.optimizer

import prog8.ast.*
import prog8.ast.base.AstException
import prog8.ast.statements.NopStatement
import prog8.parser.ParsingFailedError


internal fun Program.constantFold() {
    val optimizer = ConstantFolding(this)
    try {
        optimizer.visit(this)
    } catch (ax: AstException) {
        optimizer.addError(ax)
    }

    while(optimizer.errors.isEmpty() && optimizer.optimizationsDone>0) {
        optimizer.optimizationsDone = 0
        optimizer.visit(this)
    }

    if(optimizer.errors.isNotEmpty()) {
        optimizer.errors.forEach { System.err.println(it) }
        throw ParsingFailedError("There are ${optimizer.errors.size} errors.")
    } else {
        modules.forEach { it.linkParents(namespace) }   // re-link in final configuration
    }
}


internal fun Program.optimizeStatements(optimizeInlining: Boolean): Int {
    val optimizer = StatementOptimizer(this, optimizeInlining)
    optimizer.visit(this)
    modules.forEach { it.linkParents(this.namespace) }   // re-link in final configuration

    return optimizer.optimizationsDone
}

internal fun Program.simplifyExpressions() : Int {
    val optimizer = SimplifyExpressions(this)
    optimizer.visit(this)
    return optimizer.optimizationsDone
}
