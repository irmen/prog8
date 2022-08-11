package prog8.optimizer

import prog8.ast.IBuiltinFunctions
import prog8.ast.Program
import prog8.code.core.CompilationOptions
import prog8.code.core.ICompilationTarget
import prog8.code.core.IErrorReporter


fun Program.constantFold(errors: IErrorReporter, compTarget: ICompilationTarget) {
    val valuetypefixer = VarConstantValueTypeAdjuster(this, errors)
    valuetypefixer.visit(this)
    if(errors.noErrors()) {
        valuetypefixer.applyModifications()

        val replacer = ConstantIdentifierReplacer(this, errors, compTarget)
        replacer.visit(this)
        if (errors.noErrors()) {
            replacer.applyModifications()

            valuetypefixer.visit(this)
            if(errors.noErrors()) {
                valuetypefixer.applyModifications()

                val optimizer = ConstantFoldingOptimizer(this)
                optimizer.visit(this)
                while (errors.noErrors() && optimizer.applyModifications() > 0) {
                    optimizer.visit(this)
                }

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
                               compTarget: ICompilationTarget
): Int {
    val optimizer = StatementOptimizer(this, errors, functions, compTarget)
    optimizer.visit(this)
    val optimizationCount = optimizer.applyModifications()

    modules.forEach { it.linkParents(this.namespace) }   // re-link in final configuration

    return optimizationCount
}

fun Program.inlineSubroutines(): Int {
    val inliner = Inliner(this)
    inliner.visit(this)
    return inliner.applyModifications()
}

fun Program.simplifyExpressions(target: ICompilationTarget) : Int {
    val opti = ExpressionSimplifier(this, target)
    opti.visit(this)
    return opti.applyModifications()
}

fun Program.splitBinaryExpressions(options: CompilationOptions) : Int {
    val opti = BinExprSplitter(this, options)
    opti.visit(this)
    return opti.applyModifications()
}
