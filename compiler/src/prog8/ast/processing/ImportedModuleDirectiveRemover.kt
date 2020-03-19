package prog8.ast.processing

import prog8.ast.Node
import prog8.ast.statements.Directive


internal class ImportedModuleDirectiveRemover: AstWalker() {
    /**
     * Most global directives don't apply for imported modules, so remove them
     */

    private val moduleLevelDirectives = listOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address")

    override fun before(directive: Directive, parent: Node): Iterable<AstModification> {
        if(directive.directive in moduleLevelDirectives) {
            return listOf(AstModification.Remove(directive, parent))
        }
        return emptyList()
    }
}
