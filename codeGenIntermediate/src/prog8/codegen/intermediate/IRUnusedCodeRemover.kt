package prog8.codegen.intermediate

import prog8.code.core.IErrorReporter
import prog8.intermediate.IRCodeChunkBase
import prog8.intermediate.IRProgram

internal class IRUnusedCodeRemover(private val irprog: IRProgram, private val errors: IErrorReporter) {
    fun optimize(): Int {
        val linkedChunks = mutableSetOf<IRCodeChunkBase>()
        var numRemoved = 0

        irprog.blocks.asSequence().flatMap { it.subroutines }.forEach { sub ->
            sub.chunks.forEach { chunk ->
                if(chunk.next!=null)
                    linkedChunks += chunk.next!!
                chunk.instructions.forEach {
                    if(it.branchTarget!=null)
                        linkedChunks += it.branchTarget!!
                }
                if(chunk.label=="main.start")
                    linkedChunks += chunk
            }
        }

        irprog.blocks.asSequence().flatMap { it.subroutines }.forEach { sub ->
            sub.chunks.reversed().forEach { chunk ->
                if(chunk !in linkedChunks) {
                    sub.chunks.remove(chunk)
                    numRemoved++
                }
            }
        }

        return numRemoved
    }
}