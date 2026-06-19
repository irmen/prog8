package prog8.optimizer

import prog8.ast.IBuiltinFunctions
import prog8.ast.ParentSentinel
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
                    optimizer.linkAffectedParents(namespace)
                    optimizer.visit(this)
                }
                require(tries<100000) { "endless loop in constantfold" }

                if (errors.noErrors()) {
                    replacer.visit(this)
                    replacer.applyModifications()
                    replacer.linkAffectedParents(namespace)
                }
            }
        }
    }

    if(errors.noErrors())
        namespace.linkParents(ParentSentinel)   // re-link in final configuration
}


fun Program.optimizeStatements(errors: IErrorReporter,
                               functions: IBuiltinFunctions,
                               options: CompilationOptions
): Int {
    val optimizer = StatementOptimizer(this, errors, functions, options)
    optimizer.visit(this)
    val optimizationCount = optimizer.applyModifications()
    optimizer.linkAffectedParents(this.namespace)

    return optimizationCount
}

fun Program.inlineSubroutines(options: CompilationOptions): Int {
    // Skip inlining when optimizations are disabled (-noopt flag)
    if (!options.optimize) return 0

    val inliner = Inliner(this, options)
    inliner.visit(this)
    val mods = inliner.applyModifications()
    inliner.linkAffectedParents(namespace)
    return mods
}

fun Program.simplifyExpressions(errors: IErrorReporter, options: CompilationOptions) : Int {
    val opti = ExpressionSimplifier(this, errors, options)
    opti.visit(this)
    val mods = opti.applyModifications()
    opti.linkAffectedParents(namespace)
    return mods
}
