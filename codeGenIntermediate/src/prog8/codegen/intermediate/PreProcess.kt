package prog8.codegen.intermediate

import prog8.code.ast.*


internal fun makeAllNodenamesScoped(program: PtProgram) {
    val renames = mutableListOf<Pair<PtNamedNode, String>>()
    fun recurse(node: PtNode) {
        node.children.forEach {
            if(it is PtNamedNode)
                renames.add(it to it.scopedName)
            recurse(it)
        }
    }
    recurse(program)
    renames.forEach { it.first.name = it.second }
}

internal fun moveAllNestedSubroutinesToBlockScope(program: PtProgram) {
    val movedSubs = mutableListOf<Pair<PtBlock, PtSub>>()
    val removedSubs = mutableListOf<Pair<PtSub, PtSub>>()

    fun moveToBlock(block: PtBlock, parent: PtSub, asmsub: PtAsmSub) {
        block.add(asmsub)
        parent.children.remove(asmsub)
    }

    fun moveToBlock(block: PtBlock, parent: PtSub, sub: PtSub) {
        sub.children.filterIsInstance<PtSub>().forEach { subsub -> moveToBlock(block, sub, subsub) }
        sub.children.filterIsInstance<PtAsmSub>().forEach { asmsubsub -> moveToBlock(block, sub, asmsubsub) }
        movedSubs += Pair(block, sub)
        removedSubs += Pair(parent, sub)
    }

    program.allBlocks().forEach { block ->
        block.children.toList().forEach {
            if (it is PtSub) {
                // Only regular subroutines can have nested subroutines.
                it.children.filterIsInstance<PtSub>().forEach { subsub -> moveToBlock(block, it, subsub) }
                it.children.filterIsInstance<PtAsmSub>().forEach { asmsubsub -> moveToBlock(block, it, asmsubsub) }
            }
        }
    }

    removedSubs.forEach { (parent, sub) -> parent.children.remove(sub) }
    movedSubs.forEach { (block, sub) -> block.add(sub) }
}
