package prog8.optimizer

import prog8.ast.Program
import prog8.ast.base.ErrorReporter


internal fun Program.constantFold(errors: ErrorReporter) {
    val optimizer = ConstantFoldingOptimizer(this, errors)
    optimizer.visit(this)

    while(errors.isEmpty() && optimizer.optimizationsDone>0) {
        optimizer.optimizationsDone = 0
        optimizer.visit(this)
    }

    if(errors.isEmpty())
        modules.forEach { it.linkParents(namespace) }   // re-link in final configuration
}


internal fun Program.optimizeStatements(errors: ErrorReporter): Int {
    val optimizer = StatementOptimizer(this, errors)
    optimizer.visit(this)
    modules.forEach { it.linkParents(this.namespace) }   // re-link in final configuration

    return optimizer.optimizationsDone
}

internal fun Program.simplifyExpressions() : Int {
    val opti = ExpressionSimplifier2(this)
    opti.visit(this)
    opti.applyModifications()

    val optimizer = ExpressionSimplifier(this)
    optimizer.visit(this)
    return opti.optimizationsDone + optimizer.optimizationsDone
}
