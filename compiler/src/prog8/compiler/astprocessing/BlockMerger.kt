package prog8.compiler.astprocessing

import prog8.ast.Program
import prog8.ast.statements.Block
import prog8.ast.statements.Directive
import prog8.ast.statements.Subroutine
import prog8.code.core.IErrorReporter

class BlockMerger(val errors: IErrorReporter) {
    // Move everything into the first other block with the same name that does not have %option merge
    // (if there is no block without that option, just select the first occurrence)
    // Libraries are considered first. Don't merge into blocks that have already been merged elsewhere.
    // If a merge is done, remove the source block altogether.

    private val mergedBlocks = mutableSetOf<Block>()        // to make sure blocks aren't merged more than once

    fun visit(program: Program) {
        val allBlocks = program.allBlocks

        // It should be an error for the same block to be declared twice without either declaration having %option merge
        // If all occurrences of the block have %option merge, the first in the list is chosen as the 'main' occurrence.
        val multiples = allBlocks.groupBy { it.name }.filter { it.value.size > 1 }
        for((name, blocks) in multiples) {
            val withoutMerge = blocks.filter { "merge" !in it.options() }
            if(withoutMerge.size>1) {
                val positions = withoutMerge.joinToString(", ") { it.position.toString() }
                errors.err("block '$name' is declared multiple times without %option merge: $positions", withoutMerge[0].position)
            }
            if(withoutMerge.isEmpty()) {
                errors.err("all declarations of block '$name' have %option merge", blocks[0].position)
            }
        }

        for(block in allBlocks) {
            if("merge" in block.options() && block.isNotEmpty()) {
                val libraryBlockCandidates =
                    allBlocks.filter { it !== block && it.isInLibrary && it.name == block.name }
                val (withMerge, withoutMerge) = libraryBlockCandidates.partition { "merge" in it.options() }
                if (withoutMerge.isNotEmpty() && withoutMerge.any { it !in mergedBlocks}) {
                    merge(block, withoutMerge.first { it !in mergedBlocks })
                } else if (withMerge.isNotEmpty() && withMerge.any { it !in mergedBlocks}) {
                    merge(block, withMerge.first { it !in mergedBlocks })
                } else {
                    val regularBlockCandidates = allBlocks.filter { it !== block && it.name == block.name }
                    val (withMerge, withoutMerge) = regularBlockCandidates.partition { "merge" in it.options() }
                    if (withoutMerge.isNotEmpty() && withoutMerge.any { it !in mergedBlocks}) {
                        merge(block, withoutMerge.first { it !in mergedBlocks })
                    } else if (withMerge.isNotEmpty() && withMerge.any { it !in mergedBlocks}) {
                        merge(block, withMerge.first { it !in mergedBlocks })
                    }
                }
            }
        }
    }

    private fun merge(block: Block, target: Block) {
        if(block===target || block in mergedBlocks || target in mergedBlocks)
            return

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
        mergedBlocks.add(block)
    }
}
