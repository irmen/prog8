package prog8.optimizer

import prog8.ast.IBuiltinFunctions
import prog8.ast.Program
import prog8.code.core.CompilationOptions
import prog8.code.core.IErrorReporter


fun Program.constantFold(errors: IErrorReporter, options: CompilationOptions) {
    val valuetypefixer = VarConstantValueTypeAdjuster(this, options, errors)
    valuetypefixer.visit(this)
    if(errors.noErrors()) {
        valuetypefixer.applyModifications()

        val replacer = ConstantIdentifierReplacer(this, errors)
        replacer.visit(this)
        if (errors.noErrors()) {
            replacer.applyModifications()

            valuetypefixer.visit(this)
            if(errors.noErrors()) {
                valuetypefixer.applyModifications()

                val optimizer = ConstantFoldingOptimizer(this, errors)
                optimizer.visit(this)
                var tries=0
                while (errors.noErrors() && optimizer.applyModifications() > 0 && tries++ < 100000) {
                    optimizer.visit(this)
                }
                require(tries<100000) { "endless loop in constantfold" }

                if (errors.noErrors()) {
                    replacer.visit(this)
                    replacer.applyModifications()
                }
            }
        }
    }

    if(errors.noErrors())
        modules.forEach { it.linkParents(namespace) }   // re-link in final configuration
}


fun Program.optimizeStatements(errors: IErrorReporter,
                               functions: IBuiltinFunctions,
                               options: CompilationOptions
): Int {
    val optimizer = StatementOptimizer(this, errors, functions, options)
    optimizer.visit(this)
    val optimizationCount = optimizer.applyModifications()

    modules.forEach { it.linkParents(this.namespace) }   // re-link in final configuration

    return optimizationCount
}

fun Program.inlineSubroutines(options: CompilationOptions): Int {
    val inliner = Inliner(this, options)
    inliner.visit(this)
    return inliner.applyModifications()
}

fun Program.simplifyExpressions(errors: IErrorReporter) : Int {
    val opti = ExpressionSimplifier(this, errors)
    opti.visit(this)
    return opti.applyModifications()
}
