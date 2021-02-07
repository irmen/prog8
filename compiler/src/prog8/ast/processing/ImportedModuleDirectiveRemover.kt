package prog8.ast.processing

import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.statements.Directive
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification


internal class ImportedModuleDirectiveRemover: AstWalker() {
    /**
     * Most global directives don't apply for imported modules, so remove them
     */

    private val moduleLevelDirectives = listOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address")
    private val noModifications = emptyList<IAstModification>()

    override fun before(directive: Directive, parent: Node): Iterable<IAstModification> {
        if(directive.directive in moduleLevelDirectives) {
            return listOf(IAstModification.Remove(directive, parent as INameScope))
        }
        return noModifications
    }
}
