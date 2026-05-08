package prog8.codegen.intermediate

import prog8.code.ast.*


internal fun makeAllNodenamesScoped(program: PtProgram) {
    val renames = mutableListOf<Pair<PtNamedNode, String>>()
    val constRenames = mutableListOf<Pair<PtConstant, String>>()
    fun computeScopedName(node: PtNode): String {
        val name = if(node is PtNamedNode) node.name else (node as? PtConstant)?.name ?: return ""
        var parent = node.parent
        while(true) {
            when(parent) {
                is PtNamedNode -> return "${parent.scopedName}.$name"
                is PtConstant -> return "${parent.name}.$name"
                is PtProgram -> return name
                else -> {}
            }
            parent = parent.parent
        }
    }
    fun recurse(node: PtNode) {
        node.children.forEach {
            when(it) {
                is PtNamedNode -> renames.add(it to it.scopedName)
                is PtConstant -> constRenames.add(it to computeScopedName(it))
                else -> {}
            }
            recurse(it)
        }
    }
    recurse(program)
    renames.forEach { it.first.name = it.second }
    constRenames.forEach { it.first.name = it.second }
}

internal fun moveAllNestedSubroutinesToBlockScope(program: PtProgram) {
    val movedSubs = mutableListOf<Pair<PtBlock, PtSub>>()
    val removedSubs = mutableListOf<Pair<PtSub, PtSub>>()

    fun moveToBlock(block: PtBlock, parent: PtSub, asmsub: PtAsmSub) {
        block.add(asmsub)
        parent.removeChild(asmsub)
    }

    fun moveToBlock(block: PtBlock, parent: PtSub, sub: PtSub) {
        // First recursively move nested subroutines
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

    removedSubs.forEach { (parent, sub) -> parent.removeChild(sub) }
    movedSubs.forEach { (block, sub) -> block.add(sub) }
}
