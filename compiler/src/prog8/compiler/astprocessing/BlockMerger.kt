package prog8.compiler.astprocessing

import prog8.ast.Program
import prog8.ast.statements.Block
import prog8.ast.statements.Directive

class BlockMerger {
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
        for(stmt in block.statements.filter { it !is Directive }) {
            target.statements.add(stmt)
            stmt.linkParents(target)
        }
        block.statements.clear()
        block.definingScope.remove(block)
    }
}
