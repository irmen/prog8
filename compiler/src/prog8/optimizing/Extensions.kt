package prog8.optimizing

import prog8.ast.AstException
import prog8.ast.INameScope
import prog8.ast.IStatement
import prog8.ast.Module
import prog8.parser.ParsingFailedError

fun Module.constantFold(globalNamespace: INameScope) {
    val optimizer = ConstantFolding(globalNamespace)
    try {
        this.process(optimizer)
    } catch (ax: AstException) {
        optimizer.addError(ax)
    }

    while(optimizer.errors.isEmpty() && optimizer.optimizationsDone>0) {
        println("[${this.name}] ${optimizer.optimizationsDone} constant folds performed")
        optimizer.optimizationsDone = 0
        this.process(optimizer)
    }

    if(optimizer.errors.isNotEmpty()) {
        optimizer.errors.forEach { System.err.println(it) }
        throw ParsingFailedError("There are ${optimizer.errors.size} errors.")
    } else {
        this.linkParents()  // re-link in final configuration
    }
}


fun Module.optimizeStatements(globalNamespace: INameScope, allScopedSymbolDefinitions: MutableMap<String, IStatement>): Int {
    val optimizer = StatementOptimizer(globalNamespace)
    this.process(optimizer)
    optimizer.removeUnusedNodes(globalNamespace.usedNames(), allScopedSymbolDefinitions)
    if(optimizer.optimizationsDone > 0)
        println("[${this.name}] ${optimizer.optimizationsDone} statement optimizations performed")
    this.linkParents()  // re-link in final configuration
    return optimizer.optimizationsDone
}

fun Module.simplifyExpressions(namespace: INameScope) : Int {
    val optimizer = SimplifyExpressions(namespace)
    this.process(optimizer)
    if(optimizer.optimizationsDone > 0)
        println("[${this.name}] ${optimizer.optimizationsDone} expression optimizations performed")
    return optimizer.optimizationsDone
}
