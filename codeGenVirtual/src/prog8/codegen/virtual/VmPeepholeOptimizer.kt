package prog8.codegen.virtual

import prog8.vm.Instruction
import prog8.vm.Opcode

internal class VmPeepholeOptimizer(private val vmprog: AssemblyProgram, private val allocations: VariableAllocator) {
    fun optimize() {
        vmprog.getBlocks().forEach { block ->
            do {
                val indexedInstructions = block.lines.withIndex()
                    .filter { it.value is VmCodeInstruction }
                    .map { IndexedValue(it.index, (it.value as VmCodeInstruction).ins)}
                val changed = optimizeRemoveNops(block, indexedInstructions)
                        || optimizeDoubleLoadsAndStores(block, indexedInstructions)
                        // TODO other optimizations:
                        //  useless arithmethic (div/mul by 1, add/sub 0, ...)
                        //  useless logical (bitwise (x)or 0, bitwise and by ffff, shl followed by shr or vice versa (no carry)... )
                        //  jump/branch to label immediately below
                        //  branch instructions with reg1==reg2
                        //  conditional set instructions with reg1==reg2
                        //  push followed by pop to same target, or different target replace with load
                        //  double sec, clc
                        //  sec+clc or clc+sec
                        //  move complex optimizations such as unused registers, ...
            } while(changed)
        }
    }

    private fun optimizeRemoveNops(block: VmCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if (ins.opcode == Opcode.NOP) {
                changed = true
                block.lines.removeAt(idx)
            }
        }
        return changed
    }

    private fun optimizeDoubleLoadsAndStores(block: VmCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        var changed = false
        indexedInstructions.forEach { (idx, ins) ->

            // TODO: detect multiple loads to the same target, only keep first
            // TODO: detect multiple stores to the same target, only keep first
            // TODO: detect multiple ffrom/fto to the same target, only keep first
            // TODO: detect multiple sequential rnd with same reg1, only keep one
            // TODO: double same xors/nots/negs, remove the pair completely as they cancel out
            // TODO: multiple same ands, ors, only keep first
        }
        return changed
    }
}

private interface ICodeChange { // TODO not used? remove?
    fun perform(block: VmCodeChunk)

    class Remove(val idx: Int): ICodeChange {
        override fun perform(block: VmCodeChunk) {
            block.lines.removeAt(idx)
        }
    }
}