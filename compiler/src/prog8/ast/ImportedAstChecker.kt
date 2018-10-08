package prog8.ast


/**
 * Checks that are specific for imported modules.
 */

fun Module.checkImportedValid() {
    val checker = ImportedAstChecker()
    this.linkParents()
    this.process(checker)
    printErrors(checker.result(), name)
}


class ImportedAstChecker : IAstProcessor {
    private val checkResult: MutableList<SyntaxError> = mutableListOf()

    fun result(): List<SyntaxError> {
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
