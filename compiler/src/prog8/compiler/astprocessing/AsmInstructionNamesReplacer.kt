package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.IdentifierReference
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification

class AsmInstructionNamesReplacer(
    val program: Program,
    val blocks: Set<Block>,
    val subroutines: Set<Subroutine>,
    val variables: Set<VarDecl>,
    val labels: Set<Label>): AstWalker() {

    override fun after(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> {
        if(identifier.nameInSource.size>1) {
            val tgt = identifier.targetStatement(program)
            if(tgt==null || tgt.definingModule.isLibrary)
                return noModifications
        }

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

    private val subsWithParamRefsToFix = mutableListOf<Subroutine>()

    override fun applyModifications(): Int {
        var count = super.applyModifications()
        subsWithParamRefsToFix.forEach { subroutine ->
            subroutine.statements.withIndex().reversed().forEach { (index,stmt) ->
                if(stmt is VarDecl && stmt.origin==VarDeclOrigin.SUBROUTINEPARAM) {
                    val param = subroutine.parameters.single { it.name == stmt.name}
                    val decl = VarDecl.fromParameter(param)
                    subroutine.statements[index] = decl
                    decl.linkParents(subroutine)
                    count++
                }
            }
        }
        return count
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        val changedParams = mutableListOf<Pair<Int, SubroutineParameter>>()
        subroutine.parameters.withIndex().forEach { (index, param) ->
            if(param.name.length==3 && param.name.all { it.isLetter() } && !param.definingModule.isLibrary) {
                changedParams.add(index to SubroutineParameter("p8p_${param.name}", param.type, param.position))
            }
        }

        changedParams.forEach { (index, newParam) -> subroutine.parameters[index] = newParam }
        val newName = if(subroutine in subroutines) "p8p_${subroutine.name}" else subroutine.name

        return if(newName!=subroutine.name || changedParams.isNotEmpty()) {
            val newSub = Subroutine(newName, subroutine.parameters, subroutine.returntypes,
                subroutine.asmParameterRegisters, subroutine.asmReturnvaluesRegisters, subroutine.asmClobbers, subroutine.asmAddress, subroutine.isAsmSubroutine,
                subroutine.inline, false, subroutine.statements, subroutine.position)
            if(changedParams.isNotEmpty())
                subsWithParamRefsToFix += newSub
            listOf(IAstModification.ReplaceNode(subroutine, newSub, parent))
        } else {
            if(changedParams.isNotEmpty())
                subsWithParamRefsToFix += subroutine
            noModifications
        }
    }

}
