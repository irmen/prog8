package prog8.compiler.astprocessing

import prog8.ast.Program
import prog8.ast.statements.Block
import prog8.ast.statements.Directive
import prog8.ast.statements.Subroutine
import prog8.code.core.IErrorReporter

class BlockMerger(val errors: IErrorReporter) {
    // All blocks having a 'merge' option,
    // will be joined into a block with the same name, coming from a library.
    // (or a normal block if no library block with that name was found)

    fun visit(program: Program) {
        val allBlocks = program.allBlocks
        for(block in allBlocks) {
            if("merge" in block.options()) {
                // move everything into the first other block with the same name
                // and remove the source block altogether
                val libraryBlock = allBlocks.firstOrNull { it !== block && it.isInLibrary && it.name==block.name }
                if(libraryBlock!=null) {
                    merge(block, libraryBlock)
                } else {
                    val regularBlock = allBlocks.firstOrNull { it !== block && it.name==block.name }
                    if(regularBlock!=null) {
                        merge(block, regularBlock)
                    }
                }
            }
        }
    }

    private fun merge(block: Block, target: Block) {
        val named = target.statements.filterIsInstance<Subroutine>().associateBy { it.name }

        for(stmt in block.statements.filter { it !is Directive }) {
            if(stmt is Subroutine && stmt.name in named) {
                val existing = named.getValue(stmt.name)
                if(stmt.returntypes==existing.returntypes) {
                    if(stmt.parameters == existing.parameters) {
                        // overwrite the target subroutine, everything is identical!
                        existing.definingScope.remove(existing)
                        errors.info("monkeypatched subroutine '${existing.scopedName.joinToString(".")}' in ${existing.definingModule.name}", stmt.position)
                    }
                }
            }
            target.statements.add(stmt)
            stmt.parent = target
        }
        block.statements.clear()
        block.definingScope.remove(block)
    }
}
