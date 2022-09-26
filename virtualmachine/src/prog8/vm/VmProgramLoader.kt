package prog8.vm

import prog8.intermediate.*
import java.lang.IllegalArgumentException

class VmProgramLoader {

    fun load(irProgram: IRProgram, memory: Memory): Array<Instruction> {

        // at long last, allocate the variables in memory.
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget)
        val program = mutableListOf<Instruction>()

        // TODO stuff the allocated variables into memory

        if(!irProgram.options.dontReinitGlobals) {
            irProgram.globalInits.forEach {
                // TODO put global init line into program
            }
        }

        // make sure that if there is a "main.start" entrypoint, we jump to it
        irProgram.blocks.firstOrNull()?.let {
            if(it.subroutines.any { sub -> sub.name=="main.start" }) {
                program.add(Instruction(Opcode.JUMP, labelSymbol = "main.start"))
            }
        }

        irProgram.blocks.forEach { block ->
            if(block.address!=null)
                throw IllegalArgumentException("blocks cannot have a load address for vm: ${block.name}")

            block.inlineAssembly.forEach {

                // TODO put it.assembly into program
            }
            block.subroutines.forEach {
                // TODO subroutine label ?
                it.chunks.forEach { chunk ->
                    if(chunk is IRInlineAsmChunk) {
                        // TODO put it.assembly into program
                    } else {
                        chunk.lines.forEach {
                            // TODO put line into program
                        }
                    }
                }
            }
            block.asmSubroutines.forEach {
                // TODO add asmsub to program
            }
        }
        return emptyArray()
    }

}
