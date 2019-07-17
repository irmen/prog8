package prog8.ast.processing

import prog8.ast.Module
import prog8.ast.base.SyntaxError
import prog8.ast.base.printWarning
import prog8.ast.statements.Directive
import prog8.ast.statements.Statement

internal class ImportedModuleDirectiveRemover : IAstModifyingVisitor {
    private val checkResult: MutableList<SyntaxError> = mutableListOf()

    internal fun result(): List<SyntaxError> {
        return checkResult
    }

    /**
     * Most global directives don't apply for imported modules, so remove them
     */
    override fun visit(module: Module) {
        super.visit(module)
        val newStatements : MutableList<Statement> = mutableListOf()

        val moduleLevelDirectives = listOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address")
        for (sourceStmt in module.statements) {
            val stmt = sourceStmt.accept(this)
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
