package prog8.optimizer

import prog8.ast.Program
import prog8.ast.base.ErrorReporter


internal fun Program.constantFold(errors: ErrorReporter) {
    val valuetypefixer = VarConstantValueTypeAdjuster(this, errors)
    valuetypefixer.visit(this)
    if(errors.isEmpty()) {
        valuetypefixer.applyModifications()

        val replacer = ConstantIdentifierReplacer(this, errors)
        replacer.visit(this)
        if (errors.isEmpty()) {
            replacer.applyModifications()

            valuetypefixer.visit(this)
            if(errors.isEmpty()) {
                valuetypefixer.applyModifications()

                val optimizer = ConstantFoldingOptimizer(this)
                optimizer.visit(this)
                while (errors.isEmpty() && optimizer.applyModifications() > 0) {
                    optimizer.visit(this)
                }

                if (errors.isEmpty()) {
                    replacer.visit(this)
                    replacer.applyModifications()
                }
            }
        }
    }

    if(errors.isEmpty())
        modules.forEach { it.linkParents(namespace) }   // re-link in final configuration
}


internal fun Program.optimizeStatements(errors: ErrorReporter): Int {
    val optimizer = StatementOptimizer(this, errors)
    optimizer.visit(this)
    val optimizationCount = optimizer.applyModifications()

    modules.forEach { it.linkParents(this.namespace) }   // re-link in final configuration

    return optimizationCount
}

internal fun Program.simplifyExpressions() : Int {
    val opti = ExpressionSimplifier(this)
    opti.visit(this)
    return opti.applyModifications()
}

internal fun Program.splitBinaryExpressions() : Int {
    val opti = BinExprSplitter(this)
    opti.visit(this)
    return opti.applyModifications()
}
