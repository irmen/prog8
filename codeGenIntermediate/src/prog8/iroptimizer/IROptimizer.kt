package prog8.iroptimizer

import prog8.intermediate.*

// TODO integrate into peephole optimizer

internal class IROptimizer(val program: IRProgram) {
    fun optimize() {
        program.blocks.forEach { block ->
            block.children.forEach { elt ->
                process(elt)
            }
        }
    }

    private fun process(elt: IIRBlockElement) {
        when(elt) {
            is IRCodeChunkBase -> {
                optimizeInstructions(elt)
                // TODO renumber registers that are only used within the code chunk
                // val used = elt.usedRegisters()
            }
            is IRAsmSubroutine -> {
                if(elt.asmChunk.isIR) {
                    optimizeInstructions(elt.asmChunk)
                }
                // TODO renumber registers that are only used within the code chunk
                // val used = elt.usedRegisters()
            }
            is IRSubroutine -> {
                elt.chunks.forEach { process(it) }
            }
        }
    }

    private fun optimizeInstructions(elt: IRCodeChunkBase) {
        elt.instructions.withIndex().windowed(2).forEach {(first, second) ->
            val i1 = first.value
            val i2 = second.value
            // replace call + return --> jump
            if((i1.opcode==Opcode.CALL || i1.opcode==Opcode.CALLRVAL) && i2.opcode==Opcode.RETURN) {
                elt.instructions[first.index] = IRInstruction(Opcode.JUMP, value=i1.value, labelSymbol = i1.labelSymbol, branchTarget = i1.branchTarget)
                elt.instructions[second.index] = IRInstruction(Opcode.NOP)
                if(second.index==elt.instructions.size-1) {
                    // it was the last instruction, so the link to the next chunk needs to be cleared
                    elt.next = null
                }
            }

            // replace subsequent opcodes that jump by just the first
            if(i1.opcode in OpcodesThatJump && i2.opcode in OpcodesThatJump) {
                elt.instructions[second.index] = IRInstruction(Opcode.NOP)
            }
        }

        // remove nops
        elt.instructions.withIndex()
            .filter { it.value.opcode==Opcode.NOP }
            .reversed()
            .forEach {
                elt.instructions.removeAt(it.index)
            }
    }
}