package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.expressions.IdentifierReference
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification

class AsmInstructionNamesReplacer(
    val blocks: Set<Block>,
    val subroutines: Set<Subroutine>,
    val variables: Set<VarDecl>,
    val labels: Set<Label>): AstWalker() {

    override fun after(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> {
        val newName = identifier.nameInSource.map { ident ->
            if(ident.length==3 && !identifier.definingModule.isLibrary) {
                val blockTarget = blocks.firstOrNull { it.name==ident }
                val subTarget = subroutines.firstOrNull {it.name==ident }
                val varTarget = variables.firstOrNull { it.name==ident }
                val labelTarget = labels.firstOrNull { it.name==ident}

                if(blockTarget!=null || subTarget!=null || varTarget!=null || labelTarget!=null) {
                    "p8p_$ident"
                } else
                     ident
            } else
                 ident
        }

        return if(newName!=identifier.nameInSource)
            listOf(IAstModification.ReplaceNode(identifier, IdentifierReference(newName, identifier.position), parent))
        else
            noModifications
    }

    override fun after(label: Label, parent: Node): Iterable<IAstModification> {
        return if(label in labels)
            listOf(IAstModification.ReplaceNode(label, Label("p8p_${label.name}", label.position), parent))
        else
            noModifications
    }

    override fun after(block: Block, parent: Node): Iterable<IAstModification> {
        return if(block in blocks)
            listOf(IAstModification.ReplaceNode(block, Block("p8p_${block.name}", block.address, block.statements, block.isInLibrary, block.position), parent))
        else
            noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        return if(decl in variables)
            listOf(IAstModification.ReplaceNode(decl, decl.renamed("p8p_${decl.name}"), parent))
        else
            noModifications
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        val parameters = subroutine.parameters.map {
            if(it.name.length==3 && it.name.all { it.isLetter() } && !it.definingModule.isLibrary)
                SubroutineParameter("p8p_${it.name}", it.type, it.position) else it
        }

        // TODO for all decls in the subroutine, update their subroutineParameter if something changed

        val newName = if(subroutine in subroutines) "p8p_${subroutine.name}" else subroutine.name

        return if(newName!=subroutine.name || parameters.map{ it.name} != subroutine.parameters.map {it.name})
            listOf(IAstModification.ReplaceNode(subroutine,
                Subroutine(newName, parameters.toMutableList(), subroutine.returntypes,
                    subroutine.asmParameterRegisters, subroutine.asmReturnvaluesRegisters, subroutine.asmClobbers, subroutine.asmAddress, subroutine.isAsmSubroutine,
                    subroutine.inline, subroutine.statements, subroutine.position), parent))
        else
            noModifications
    }

}
