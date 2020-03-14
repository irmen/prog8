package prog8.ast.processing

import prog8.ast.Module
import prog8.ast.base.ErrorReporter
import prog8.ast.statements.Directive
import prog8.ast.statements.Statement

internal class ImportedModuleDirectiveRemover(private val errors: ErrorReporter) : IAstModifyingVisitor {
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
                    errors.warn("ignoring module directive because it was imported", stmt.position)
                    continue
                }
            }
            newStatements.add(stmt)
        }
        module.statements = newStatements
    }
}
