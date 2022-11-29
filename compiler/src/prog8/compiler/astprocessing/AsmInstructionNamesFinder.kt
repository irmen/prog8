package prog8.compiler.astprocessing

import prog8.ast.statements.Block
import prog8.ast.statements.Label
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.ast.walk.IAstVisitor
import prog8.code.core.ICompilationTarget
import prog8.code.target.VMTarget

class  AsmInstructionNamesFinder(val target: ICompilationTarget): IAstVisitor {

    val blocks = mutableSetOf<Block>()
    val variables = mutableSetOf<VarDecl>()
    val labels = mutableSetOf<Label>()
    val subroutines = mutableSetOf<Subroutine>()

    private fun isPossibleInstructionName(name: String) = name.length==3 && name.all { it.isLetter() }

    fun foundAny(): Boolean = blocks.isNotEmpty() || variables.isNotEmpty() || subroutines.isNotEmpty() || labels.isNotEmpty()

    override fun visit(block: Block) {
        if(target.name!=VMTarget.NAME && !block.isInLibrary && isPossibleInstructionName(block.name)) {
            blocks += block
        }
        super.visit(block)
    }

    override fun visit(decl: VarDecl) {
        if(target.name!=VMTarget.NAME && !decl.definingModule.isLibrary && isPossibleInstructionName(decl.name)) {
            variables += decl
        }
        super.visit(decl)
    }

    override fun visit(label: Label) {
        if(target.name!=VMTarget.NAME && !label.definingModule.isLibrary && isPossibleInstructionName(label.name)) {
            labels += label
        }
        super.visit(label)
    }

    override fun visit(subroutine: Subroutine) {
        if(target.name!=VMTarget.NAME && !subroutine.definingModule.isLibrary && isPossibleInstructionName(subroutine.name)) {
            subroutines += subroutine
        }
        super.visit(subroutine)
    }
}
