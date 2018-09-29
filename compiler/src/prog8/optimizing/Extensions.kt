package prog8.optimizing

import prog8.ast.AstException
import prog8.ast.INameScope
import prog8.ast.Module
import prog8.compiler.HeapValues
import prog8.parser.ParsingFailedError


fun Module.constantFold(globalNamespace: INameScope, heap: HeapValues) {
    val optimizer = ConstantFolding(globalNamespace, heap)
    try {
        this.process(optimizer)
    } catch (ax: AstException) {
        optimizer.addError(ax)
    }

    while(optimizer.errors.isEmpty() && optimizer.optimizationsDone>0) {
        println("[${this.name}] Debug: ${optimizer.optimizationsDone} constant folds performed")
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


fun Module.optimizeStatements(globalNamespace: INameScope, heap: HeapValues): Int {
    val optimizer = StatementOptimizer(globalNamespace, heap)
    this.process(optimizer)
    if(optimizer.optimizationsDone > 0)
        println("[${this.name}] Debug: ${optimizer.optimizationsDone} statement optimizations performed")
    this.linkParents()  // re-link in final configuration
    return optimizer.optimizationsDone
}

fun Module.simplifyExpressions(namespace: INameScope, heap: HeapValues) : Int {
    val optimizer = SimplifyExpressions(namespace, heap)
    this.process(optimizer)
    if(optimizer.optimizationsDone > 0)
        println("[${this.name}] Debug: ${optimizer.optimizationsDone} expression optimizations performed")
    return optimizer.optimizationsDone
}
