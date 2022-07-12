package prog8.codegen.virtual

import prog8.vm.Instruction
import prog8.vm.Opcode

internal class VmOptimizerException(msg: String): Exception(msg)


class VmPeepholeOptimizer(private val vmprog: AssemblyProgram, private val allocations: VariableAllocator) {
    fun optimize() {
        vmprog.getBlocks().forEach { block ->
            do {
                val indexedInstructions = block.lines.withIndex()
                    .filter { it.value is VmCodeInstruction }
                    .map { IndexedValue(it.index, (it.value as VmCodeInstruction).ins) }
                val changed = removeNops(block, indexedInstructions)
                        || removeDoubleLoadsAndStores(block, indexedInstructions)
                        // || removeUselessArithmetic(block, indexedInstructions)   // TODO enable
                        || removeWeirdBranches(block, indexedInstructions)
                        || removeDoubleSecClc(block, indexedInstructions)
                        || cleanupPushPop(block, indexedInstructions)
                // TODO other optimizations:
                //  other useless logical?
                //  conditional set instructions with reg1==reg2
                //  move complex optimizations such as unused registers, ...
            } while(changed)
        }
    }

    private fun cleanupPushPop(block: VmCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        //  push followed by pop to same target, or different target->replace with load
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(ins.opcode==Opcode.PUSH) {
                if(idx < block.lines.size-1) {
                    val insAfter = block.lines[idx+1] as? VmCodeInstruction
                    if(insAfter!=null && insAfter.ins.opcode ==Opcode.POP) {
                        if(ins.reg1==insAfter.ins.reg1) {
                            block.lines.removeAt(idx)
                            block.lines.removeAt(idx)
                        } else {
                            block.lines[idx] = VmCodeInstruction(Opcode.LOADR, ins.type, reg1=insAfter.ins.reg1, reg2=ins.reg1)
                            block.lines.removeAt(idx+1)
                        }
                        changed = true
                    }
                }
            }
        }
        return changed
    }


    private fun removeDoubleSecClc(block: VmCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        //  double sec, clc
        //  sec+clc or clc+sec
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(ins.opcode==Opcode.SEC || ins.opcode==Opcode.CLC) {
                if(idx < block.lines.size-1) {
                    val insAfter = block.lines[idx+1] as? VmCodeInstruction
                    if(insAfter?.ins?.opcode == ins.opcode) {
                        block.lines.removeAt(idx)
                        changed = true
                    }
                    else if(ins.opcode==Opcode.SEC && insAfter?.ins?.opcode==Opcode.CLC) {
                        block.lines.removeAt(idx)
                        changed = true
                    }
                    else if(ins.opcode==Opcode.CLC && insAfter?.ins?.opcode==Opcode.SEC) {
                        block.lines.removeAt(idx)
                        changed = true
                    }
                }
            }
        }
        return changed
    }

    private fun removeWeirdBranches(block: VmCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        //  jump/branch to label immediately below
        //  branch instructions with reg1==reg2
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(ins.opcode==Opcode.JUMP && ins.labelSymbol!=null) {
                // if jumping to label immediately following this
                if(idx < block.lines.size-1) {
                    val label = block.lines[idx+1] as? VmCodeLabel
                    if(label?.name == ins.labelSymbol) {
                        block.lines.removeAt(idx)
                        changed = true
                    }
                }
            }
/*
beq         reg1, reg2,       location  - jump to location in program given by location, if reg1 == reg2
bne         reg1, reg2,       location  - jump to location in program given by location, if reg1 != reg2
blt         reg1, reg2,       location  - jump to location in program given by location, if reg1 < reg2 (unsigned)
blts        reg1, reg2,       location  - jump to location in program given by location, if reg1 < reg2 (signed)
ble         reg1, reg2,       location  - jump to location in program given by location, if reg1 <= reg2 (unsigned)
bles        reg1, reg2,       location  - jump to location in program given by location, if reg1 <= reg2 (signed)
bgt         reg1, reg2,       location  - jump to location in program given by location, if reg1 > reg2 (unsigned)
bgts        reg1, reg2,       location  - jump to location in program given by location, if reg1 > reg2 (signed)
bge         reg1, reg2,       location  - jump to location in program given by location, if reg1 >= reg2 (unsigned)
bges        reg1, reg2,       location  - jump to location in program given by location, if reg1 >= reg2 (signed)
 */
        }
        return changed
    }

    private fun removeUselessArithmetic(block: VmCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        // TODO this is hard to solve atm because the values are loaded into registers first
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            when (ins.opcode) {
                Opcode.DIV, Opcode.DIVS, Opcode.MUL, Opcode.MOD -> {
                    TODO("remove div/mul by 1")
                }
                Opcode.ADD, Opcode.SUB -> {
                    TODO("remove add/sub by 1 -> inc/dec, by 0->remove")
                }
                Opcode.AND -> {
                    TODO("and 0 -> 0, and ffff -> remove")
                }
                Opcode.OR -> {
                    TODO("or 0 -> remove, of ffff -> ffff")
                }
                Opcode.XOR -> {
                    TODO("xor 0 -> remove")
                }
                else -> {}
            }
        }
        return changed
    }

    private fun removeNops(block: VmCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if (ins.opcode == Opcode.NOP) {
                changed = true
                block.lines.removeAt(idx)
            }
        }
        return changed
    }

    private fun removeDoubleLoadsAndStores(block: VmCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        var changed = false
        indexedInstructions.forEach { (idx, ins) ->

            // TODO: detect multiple loads to the same target registers, only keep first (if source is not I/O memory)
            // TODO: detect multiple stores to the same target, only keep first (if target is not I/O memory)
            // TODO: detect multiple float ffrom/fto to the same target, only keep first
            // TODO: detect multiple sequential rnd with same reg1, only keep one
            // TODO: detect subsequent same xors/nots/negs, remove the pairs completely as they cancel out
            // TODO: detect multiple same ands, ors; only keep first
            // ...
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