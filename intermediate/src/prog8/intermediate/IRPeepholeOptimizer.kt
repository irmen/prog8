package prog8.intermediate

class IRPeepholeOptimizer(private val vmprog: IRProgram) {
    fun optimize() {
        vmprog.blocks.asSequence().flatMap { it.subroutines }.forEach { sub ->
            sub.chunks.forEach { chunk ->
                // we don't optimize Inline Asm chunks here.
                if(chunk is IRCodeChunk) {
                    do {
                        val indexedInstructions = chunk.lines.withIndex()
                            .filter { it.value is IRCodeInstruction }
                            .map { IndexedValue(it.index, (it.value as IRCodeInstruction).ins) }
                        val changed = removeNops(chunk, indexedInstructions)
                                || removeDoubleLoadsAndStores(chunk, indexedInstructions)       // TODO not yet implemented
                                || removeUselessArithmetic(chunk, indexedInstructions)
                                || removeWeirdBranches(chunk, indexedInstructions)
                                || removeDoubleSecClc(chunk, indexedInstructions)
                                || cleanupPushPop(chunk, indexedInstructions)
                        // TODO other optimizations:
                        //  more complex optimizations such as unused registers
                    } while (changed)
                }
            }
        }
    }

    private fun cleanupPushPop(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        //  push followed by pop to same target, or different target->replace with load
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(ins.opcode==Opcode.PUSH) {
                if(idx < chunk.lines.size-1) {
                    val insAfter = chunk.lines[idx+1] as? IRCodeInstruction
                    if(insAfter!=null && insAfter.ins.opcode ==Opcode.POP) {
                        if(ins.reg1==insAfter.ins.reg1) {
                            chunk.lines.removeAt(idx)
                            chunk.lines.removeAt(idx)
                        } else {
                            chunk.lines[idx] = IRCodeInstruction(Opcode.LOADR, ins.type, reg1=insAfter.ins.reg1, reg2=ins.reg1)
                            chunk.lines.removeAt(idx+1)
                        }
                        changed = true
                    }
                }
            }
        }
        return changed
    }

    private fun removeDoubleSecClc(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        //  double sec, clc
        //  sec+clc or clc+sec
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(ins.opcode==Opcode.SEC || ins.opcode==Opcode.CLC) {
                if(idx < chunk.lines.size-1) {
                    val insAfter = chunk.lines[idx+1] as? IRCodeInstruction
                    if(insAfter?.ins?.opcode == ins.opcode) {
                        chunk.lines.removeAt(idx)
                        changed = true
                    }
                    else if(ins.opcode==Opcode.SEC && insAfter?.ins?.opcode==Opcode.CLC) {
                        chunk.lines.removeAt(idx)
                        changed = true
                    }
                    else if(ins.opcode==Opcode.CLC && insAfter?.ins?.opcode==Opcode.SEC) {
                        chunk.lines.removeAt(idx)
                        changed = true
                    }
                }
            }
        }
        return changed
    }

    private fun removeWeirdBranches(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        //  jump/branch to label immediately below
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(ins.opcode==Opcode.JUMP && ins.labelSymbol!=null) {
                // if jumping to label immediately following this
                if(idx < chunk.lines.size-1) {
                    val label = chunk.lines[idx+1] as? IRCodeLabel
                    if(ins.labelSymbol.size==1 && label?.name == ins.labelSymbol[0]) {
                        chunk.lines.removeAt(idx)
                        changed = true
                    }
                }
            }
        }
        return changed
    }

    private fun removeUselessArithmetic(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        // note: this is hard to solve for the non-immediate instructions atm because the values are loaded into registers first
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            when (ins.opcode) {
                Opcode.DIV, Opcode.DIVS, Opcode.MUL, Opcode.MOD -> {
                    if (ins.value == 1) {
                        chunk.lines.removeAt(idx)
                        changed = true
                    }
                }
                Opcode.ADD, Opcode.SUB -> {
                    if (ins.value == 1) {
                        chunk.lines[idx] = IRCodeInstruction(
                            if (ins.opcode == Opcode.ADD) Opcode.INC else Opcode.DEC,
                            ins.type,
                            ins.reg1
                        )
                        changed = true
                    } else if (ins.value == 0) {
                        chunk.lines.removeAt(idx)
                        changed = true
                    }
                }
                Opcode.AND -> {
                    if (ins.value == 0) {
                        chunk.lines[idx] = IRCodeInstruction(Opcode.LOAD, ins.type, reg1 = ins.reg1, value = 0)
                        changed = true
                    } else if (ins.value == 255 && ins.type == VmDataType.BYTE) {
                        chunk.lines.removeAt(idx)
                        changed = true
                    } else if (ins.value == 65535 && ins.type == VmDataType.WORD) {
                        chunk.lines.removeAt(idx)
                        changed = true
                    }
                }
                Opcode.OR -> {
                    if (ins.value == 0) {
                        chunk.lines.removeAt(idx)
                        changed = true
                    } else if ((ins.value == 255 && ins.type == VmDataType.BYTE) || (ins.value == 65535 && ins.type == VmDataType.WORD)) {
                        chunk.lines[idx] = IRCodeInstruction(Opcode.LOAD, ins.type, reg1 = ins.reg1, value = ins.value)
                        changed = true
                    }
                }
                Opcode.XOR -> {
                    if (ins.value == 0) {
                        chunk.lines.removeAt(idx)
                        changed = true
                    }
                }
                else -> {}
            }
        }
        return changed
    }

    private fun removeNops(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if (ins.opcode == Opcode.NOP) {
                changed = true
                chunk.lines.removeAt(idx)
            }
        }
        return changed
    }

    private fun removeDoubleLoadsAndStores(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<Instruction>>): Boolean {
        var changed = false
        indexedInstructions.forEach { (idx, ins) ->

            // TODO: detect multiple loads to the same target registers, only keep first (if source is not I/O memory)
            // TODO: detect multiple stores to the same target, only keep first (if target is not I/O memory)
            // TODO: detect multiple float ffrom/fto to the same target, only keep first
            // TODO: detect multiple sequential rnd with same reg1, only keep one
            // TODO: detect subsequent same xors/nots/negs, remove the pairs completely as they cancel out
            // TODO: detect multiple same ands, ors; only keep first
            // TODO: (hard) detect multiple registers being assigned the same value (and not changed) - use only 1 of them
            // ...
        }
        return changed
    }
}