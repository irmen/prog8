package il65.ast

import il65.parser.ParsingFailedError


/**
 * Checks that are specific for imported modules.
 */

fun Module.checkImportedValid() {
    val checker = ImportedAstChecker()
    this.linkParents()
    this.process(checker)
    val result = checker.result()
    result.forEach {
        System.err.println(it)
    }
    if(result.isNotEmpty())
        throw ParsingFailedError("There are ${result.size} errors in imported module '$name'.")
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

        val moduleLevelDirectives = listOf("%output", "%launcher", "%zeropage", "%address")
        for (sourceStmt in module.statements) {
            val stmt = sourceStmt.process(this)
            if(stmt is Directive && stmt.parent is Module) {
                if(moduleLevelDirectives.contains(stmt.directive)) {
                    println("${stmt.position} Warning: ignoring module directive because it was imported: ${stmt.directive}")
                    continue
                }
            }
            newStatements.add(stmt)
        }
        module.statements = newStatements
    }
}
