package prog8.codegen.intermediate

import prog8.code.core.AssemblyError
import prog8.intermediate.*

internal class IRChunkLinker(private val irprog: IRProgram) {

    private val labeledChunks = irprog.blocks.flatMap { it.subroutines }.flatMap { it.chunks }.associateBy { it.label }

    fun link() {

        irprog.blocks.asSequence().flatMap { it.subroutines }.forEach { sub ->
            sub.chunks.withIndex().forEach { (index, chunk) ->

                if(chunk is IRCodeChunk) {
                    // link sequential chunks
                    val jump = chunk.instructions.lastOrNull()?.opcode
                    if (jump == null || jump !in setOf(Opcode.JUMP, Opcode.JUMPA, Opcode.RETURN)) {
                        // no jump at the end, so link to next chunk (if it exists)
                        if(index<sub.chunks.size-1) {
                            val nextChunk = sub.chunks[index + 1]
                            if (nextChunk is IRCodeChunk)
                                chunk.next = nextChunk
                            else
                                throw AssemblyError("code chunk flows into following non-code chunk")
                        }
                    }

                    // link all jump and branching instructions to their target
                    chunk.instructions.forEach {
                        if(it.opcode in OpcodesThatBranch && it.opcode!=Opcode.RETURN && it.labelSymbol!=null) {
                            val targetChunk = labeledChunks.getValue(it.labelSymbol)
                            require(targetChunk is IRCodeChunk) { "target $targetChunk with label ${targetChunk.label} has to be a code chunk" }
                            it.branchTarget = targetChunk
                        }
                        // note: branches with an address value cannot be linked to something...
                    }
                }
            }
        }
    }
}

