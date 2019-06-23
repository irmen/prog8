package prog8.ast


/**
 * Checks that are specific for imported modules.
 */

internal fun Module.checkImportedValid() {
    this.linkParents()
    val checker = ImportedAstChecker()
    checker.process(this)
    printErrors(checker.result(), name)
}


private class ImportedAstChecker : IAstProcessor {
    private val checkResult: MutableList<SyntaxError> = mutableListOf()

    internal fun result(): List<SyntaxError> {
        return checkResult
    }

    /**
     * Module check: most global directives don't apply for imported modules
     */
    override fun process(module: Module) {
        super.process(module)
        val newStatements : MutableList<IStatement> = mutableListOf()

        val moduleLevelDirectives = listOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address")
        for (sourceStmt in module.statements) {
            val stmt = sourceStmt.process(this)
            if(stmt is Directive && stmt.parent is Module) {
                if(stmt.directive in moduleLevelDirectives) {
                    printWarning("ignoring module directive because it was imported", stmt.position, stmt.directive)
                    continue
                }
            }
            newStatements.add(stmt)
        }
        module.statements = newStatements
    }
}
