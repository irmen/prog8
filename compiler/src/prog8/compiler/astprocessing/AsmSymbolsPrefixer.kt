package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.IdentifierReference
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification

class AsmSymbolsPrefixer(val program: Program): AstWalker() {

    override fun after(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> {
        if("no_symbol_prefixing" in identifier.targetStatement(program)!!.definingBlock.options())
            return noModifications
        if(identifier.nameInSource.size==1 && identifier.nameInSource[0] in program.builtinFunctions.names)
            return noModifications

        val newName = identifier.nameInSource.map { part -> "p8_$part" }
        return listOf(IAstModification.ReplaceNode(identifier, identifier.renamed(newName), parent))
    }

    override fun after(label: Label, parent: Node): Iterable<IAstModification> {
        return if("no_symbol_prefixing" in label.definingBlock.options())
            noModifications
        else
            listOf(IAstModification.ReplaceNode(label, label.renamed("p8_${label.name}"), parent))
    }

    override fun after(block: Block, parent: Node): Iterable<IAstModification> {
        return if("no_symbol_prefixing" in block.options())
            noModifications
        else
            listOf(IAstModification.ReplaceNode(block, block.renamed("p8_${block.name}"), parent))
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        return if("no_symbol_prefixing" in decl.definingBlock.options())
            noModifications
        else
            listOf(IAstModification.ReplaceNode(decl, decl.renamed("p8_${decl.name}"), parent))
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        if("no_symbol_prefixing" in subroutine.definingBlock.options())
            return noModifications

        val changedParams = mutableListOf<Pair<Int, SubroutineParameter>>()
        subroutine.parameters.withIndex().forEach { (index, param) ->
            if((param.name.length==3 || param.name.length==1) && param.name.all { it.isLetter() } && !param.definingModule.isLibrary) {
                changedParams.add(index to SubroutineParameter("p8_${param.name}", param.type, param.position))
            }
        }

        changedParams.forEach { (index, newParam) -> subroutine.parameters[index] = newParam }
        val newName = "p8_${subroutine.name}"

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
}
