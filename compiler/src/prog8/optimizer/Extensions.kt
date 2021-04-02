package prog8.optimizer

import prog8.ast.IBuiltinFunctions
import prog8.ast.Program
import prog8.compiler.IErrorReporter
import prog8.compiler.target.ICompilationTarget
import java.nio.file.Path


internal fun Program.constantFold(errors: IErrorReporter, compTarget: ICompilationTarget) {
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

                val optimizer = ConstantFoldingOptimizer(this, compTarget)
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


internal fun Program.optimizeStatements(errors: IErrorReporter,
                                        functions: IBuiltinFunctions,
                                        compTarget: ICompilationTarget,
                                        asmFileLoader: (filename: String, source: Path)->String): Int {
    val optimizer = StatementOptimizer(this, errors, functions, compTarget, asmFileLoader)
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

internal fun Program.splitBinaryExpressions(compTarget: ICompilationTarget) : Int {
    val opti = BinExprSplitter(this, compTarget)
    opti.visit(this)
    return opti.applyModifications()
}
