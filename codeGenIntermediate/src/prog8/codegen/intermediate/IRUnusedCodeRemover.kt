package prog8.codegen.intermediate

import prog8.code.core.IErrorReporter
import prog8.code.core.SourceCode.Companion.libraryFilePrefix
import prog8.intermediate.IRCodeChunkBase
import prog8.intermediate.IRProgram


internal class IRUnusedCodeRemover(private val irprog: IRProgram, private val errors: IErrorReporter) {
    fun optimize(): Int {
        var numRemoved = removeSimpleUnlinked() + removeUnreachable()

        // remove empty subs
        irprog.blocks.forEach { block ->
            block.subroutines.reversed().forEach { sub ->
                if(sub.isEmpty()) {
                    if(!sub.position.file.startsWith(libraryFilePrefix))
                        errors.warn("unused subroutine ${sub.name}", sub.position)
                    block.subroutines.remove(sub)
                    numRemoved++
                }
            }
        }

        // remove empty blocks
        irprog.blocks.reversed().forEach { block ->
            if(block.isEmpty()) {
                irprog.blocks.remove(block)
                numRemoved++
            }
        }

        return numRemoved
    }

    private fun removeUnreachable(): Int {
        val reachable = mutableSetOf(irprog.blocks.single { it.name=="main" }.subroutines.single { it.name=="main.start" }.chunks.first())

        fun grow() {
            val new = mutableSetOf<IRCodeChunkBase>()
            reachable.forEach {
                it.next?.let { next -> new += next }
                it.instructions.forEach { it.branchTarget?.let { target -> new += target} }
            }
            reachable += new
        }

        var previousCount = reachable.size
        while(true) {
            grow()
            if(reachable.size<=previousCount)
                break
            previousCount = reachable.size
        }

        return removeUnlinkedChunks(reachable)
    }

    private fun removeSimpleUnlinked(): Int {
        val linkedChunks = mutableSetOf<IRCodeChunkBase>()

        irprog.blocks.asSequence().flatMap { it.subroutines }.forEach { sub ->
            sub.chunks.forEach { chunk ->
                chunk.next?.let { next -> linkedChunks += next }
                chunk.instructions.forEach { it.branchTarget?.let { target -> linkedChunks += target } }
                if (chunk.label == "main.start")
                    linkedChunks += chunk
            }
        }

        return removeUnlinkedChunks(linkedChunks)
    }

    private fun removeUnlinkedChunks(
        linkedChunks: MutableSet<IRCodeChunkBase>
    ): Int {
        var numRemoved = 0
        irprog.blocks.asSequence().flatMap { it.subroutines }.forEach { sub ->
            sub.chunks.reversed().forEach { chunk ->
                if (chunk !in linkedChunks) {
                    if (chunk === sub.chunks[0]) {
                        if (chunk.instructions.isNotEmpty()) {
                            // don't remove the first chunk of the sub itself because it has to have the name of the sub as label
                            chunk.instructions.clear()
                            numRemoved++
                        }
                    } else {
                        sub.chunks.remove(chunk)
                        numRemoved++
                    }
                }
            }
        }
        return numRemoved
    }
}