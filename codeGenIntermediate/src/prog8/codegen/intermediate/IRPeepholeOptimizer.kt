package prog8.codegen.intermediate

import prog8.code.core.IErrorReporter
import prog8.code.core.Position
import prog8.intermediate.*

class IRPeepholeOptimizer(private val irprog: IRProgram, private val retainSSA: Boolean) {
    // NOTE: Several peephole ideas have been deliberately NOT enabled or were reverted:
    // - foldStoremLoadmToLoadr: goal was to avoid redundant memory traffic for `storem rX,addr ; loadm rY,addr`
    //   by replacing the load with `loadr rY,rX` (keep value in register). Removed because on m68k/amiga
    //   the regfile is memory, so keeping the store's source register live for the loadr forces an extra
    //   spill (move.b d0,regfile ; move.b regfile,mem ; move.b regfile,regfile2) vs the original 2 moves
    //   via memory (move.b d0,mem ; move.b mem,regfile). This increased textelite by ~344 bytes and other
    //   amiga examples by 20-230 bytes. Re-enable only with a cost model that proves it shrinks the m68k
    //   output (e.g. when source is already in regfile and not a call result in d0).
    // - removeLoadrForwarding (goal: coalesce `load r1,#imm ; loadr r2,r1` chains and forward constants) was
    //   previously disabled (half-assed) because it ignored cross-chunk liveness, call side-effects, and
    //   indexRegType for LOADX/STOREX. It is now enabled with a conservative single-chunk
    //   scope, side-effect barrier (OpcodesThatBranch + OpcodesWithSideEffects), and live-out check.
    fun optimize(optimizationsEnabled: Boolean, errors: IErrorReporter) {
        if(!optimizationsEnabled)
            return optimizeOnlyJoinChunks(retainSSA)

        peepholeOptimize(retainSSA)
        val remover = IRUnusedCodeRemover(irprog, errors)
        var totalRemovals = 0
        do {
            val numRemoved = remover.optimize()
            totalRemovals += numRemoved
        } while(numRemoved>0 && errors.noErrors())
        errors.report()

        if(totalRemovals>0) {
            irprog.linkChunks()  // re-link again.
        }
    }

    private fun optimizeOnlyJoinChunks(retainSSA: Boolean) {
        // this chunk-joining is REQUIRED (optimization or no) to end up with a structurally sound chunk list
        irprog.foreachSub { sub ->
            joinChunks(sub, retainSSA)
            removeEmptyChunks(sub)
            joinChunks(sub, retainSSA)
        }
        irprog.linkChunks() // re-link
    }

    private fun peepholeOptimize(retainSSA: Boolean) {
        irprog.foreachSub { sub ->
            joinChunks(sub, retainSSA)
            removeEmptyChunks(sub)
            joinChunks(sub, retainSSA)
            optimizeLoopCounters(sub)

            sub.chunks.withIndex().forEach { (index, chunk1) ->
                // we don't optimize Inline Asm chunks here.
                val chunk2 = if(index<sub.chunks.size-1) sub.chunks[index+1] else null
                if(chunk1 is IRCodeChunk) {
                    do {
                        val indexedInstructions = chunk1.instructions.withIndex()
                            .map { IndexedValue(it.index, it.value) }
                        val changed = removeNops(chunk1, indexedInstructions)
                                || replaceConcatZeroMsbWithExt(chunk1, indexedInstructions)
                                || removeDoubleLoadsAndStores(chunk1, indexedInstructions)
                                || removeDoubleLoadsAndStoresIndexed(chunk1, indexedInstructions)
                                || foldLoadStoremToStoreim(chunk1, indexedInstructions)
                                // foldStoremLoadmToLoadr (storem+loadm -> loadr) intentionally not called: see class comment
                                || collapseConversions(chunk1, indexedInstructions)
                                || deduplicateAddressComputations(chunk1)
                                || removeUselessArithmetic(chunk1, indexedInstructions)
                                || removeNeedlessCompares(chunk1, indexedInstructions)
                                || removeWeirdBranches(chunk1, chunk2, indexedInstructions)
                                || removeDoubleSecClc(chunk1, indexedInstructions)
                                || cleanupPushPop(chunk1, indexedInstructions)
                                || simplifyConstantReturns(chunk1, indexedInstructions)
                                || removeNeedlessLoads(chunk1, indexedInstructions)
                                || collapseAdjacentLoadrChains(chunk1, indexedInstructions)
                                || removeDeadStores(chunk1, indexedInstructions)
                                || removeSelfIdentityOps(chunk1, indexedInstructions)
                                || simplifyShiftByZero(chunk1, indexedInstructions)
                                || cancelAdjacentOps(chunk1, indexedInstructions)
                                || fusePointerPostInc(chunk1, indexedInstructions)
                                || removeNops(chunk1, indexedInstructions)   // last time, in case one of the optimizers replaced something with a nop
                    } while (changed)
                }
            }
            removeEmptyChunks(sub)
            optimizeLoopBodies(sub, retainSSA)
        }

        irprog.linkChunks()  // re-link
    }

    private fun optimizeLoopBodies(sub: IRSubroutine, retainSSA: Boolean) {
        var loopId = 0
        fun recurse(loop: IRLoopChunk) {
            loop.body.filterIsInstance<IRLoopChunk>().forEach { recurse(it) }
            val bodySub = IRSubroutine("${sub.label}.loop${loopId++}", emptyList(), emptyList(), Position.DUMMY)
            bodySub.chunks += loop.body
            joinChunks(bodySub, retainSSA)
            removeEmptyChunks(bodySub)
            joinChunks(bodySub, retainSSA)
            bodySub.chunks.forEach { chunk ->
                if (chunk is IRCodeChunk) {
                    do {
                        val indexed = chunk.instructions.withIndex().map { IndexedValue(it.index, it.value) }
                        val changed = fusePointerPostInc(chunk, indexed) || removeNops(chunk, indexed)
                    } while (changed)
                }
            }
            loop.body.clear()
            loop.body += bodySub.chunks
        }
        sub.chunks.filterIsInstance<IRLoopChunk>().forEach { recurse(it) }
    }

    private fun replaceConcatZeroMsbWithExt(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if (ins.opcode == Opcode.CONCAT && idx>0) {
                // if the previous instruction loads a zero in the msb, this can be turned into EXT.B instead
                val msbRegister = ins.srcA?.register
                var loadIndex: Int? = null
                var safe = true
                for (scanIndex in idx - 1 downTo 0) {
                    val candidate = indexedInstructions[scanIndex].value
                    if (candidate.opcode in OpcodesWithSideEffects) {
                        safe = false
                        break
                    }
                    val writesMsbRegister = msbRegister != null && msbRegister in candidate.definitions
                    if (writesMsbRegister) {
                        if (candidate.opcode == Opcode.LOAD && candidate.immediate?.integerValue == 0 && candidate.dest?.register == msbRegister) {
                            loadIndex = scanIndex
                        }
                        break
                    }
                    if (candidate.opcode == Opcode.LOAD && candidate.immediate?.integerValue == 0 && candidate.dest?.register == msbRegister) {
                        loadIndex = scanIndex
                        break
                    }
                }
                if (safe && loadIndex != null) {
                    chunk.instructions[idx] = IRInstructions.binary(Opcode.EXT, IRDataType.BYTE, ins.requireDest().registerNumber, ins.requireSrcB().registerNumber)
                    chunk.instructions.removeAt(loadIndex)
                    changed = true
                }
            }
        }
        return changed
    }

    private fun removeEmptyChunks(sub: IRSubroutine) {
        if(sub.chunks.isEmpty())
            return

        /*
        Empty Code chunk with label ->
            If next chunk has no label -> move label to next chunk, remove original
            If next chunk has label -> label name should be the same, in which case remove original, otherwise leave everything untouched.
            If is last chunk -> keep chunk in place because of the label.
        Empty Code chunk without label ->
            should not have been generated! ERROR.
         */


        val relabelChunks = mutableListOf<Pair<Int, String>>()
        val removeChunks = mutableListOf<Int>()
        val replaceLabels = mutableMapOf<String, String>()

        sub.chunks.withIndex().forEach { (index, chunk) ->
            if(chunk is IRCodeChunk && chunk.instructions.isEmpty()) {
                if(chunk.label==null) {
                    removeChunks += index
                } else {
                    if (index < sub.chunks.size - 1) {
                        val nextchunk = sub.chunks[index + 1]
                        if (nextchunk.label == null) {
                            // can transplant label to next chunk and remove this empty one.
                            relabelChunks += Pair(index + 1, chunk.label!!)
                            removeChunks += index
                        } else {
                            // merge both labels into 1 except if this is the label chunk at the start of the subroutine
                            if(index>0) {
                                if (chunk.label == nextchunk.label)
                                    removeChunks += index
                            }
                        }
                    }
                }
            }
        }

        relabelChunks.forEach { (index, label) ->
            val chunk = IRCodeChunk(label, null)
            val subChunk = sub.chunks[index]
            chunk.instructions += subChunk.instructions
            if(subChunk is IRCodeChunk)
                chunk.appendSrcPositions(subChunk.sourceLinesPositions)
            sub.chunks[index] = chunk
        }
        removeChunks.reversed().forEach { index -> sub.chunks.removeAt(index) }

        sub.chunks.forEach { chunk ->
            chunk.instructions.withIndex().forEach { (idx, instr) ->
                if(instr.opcode in OpcodesThatBranch) {
                    val label = instr.labelTarget
                    if(label != null) {
                        replaceLabels.forEach { (from, to) ->
                            if (label == from) {
                                chunk.instructions[idx] = instr.withTarget(codeLabel(to))
                            }
                            else {
                                val actualPrefix = "$from."
                                if (label.startsWith(actualPrefix))
                                    chunk.instructions[idx] = instr.withTarget(codeLabel("$to."))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun joinChunks(sub: IRSubroutine, retainSSA: Boolean) {
        // Subroutine contains a list of chunks. Some can be joined into one.

        if(sub.chunks.isEmpty())
            return

        fun mayJoinCodeChunks(previous: IRCodeChunkBase, chunk: IRCodeChunkBase): Boolean {
            if(chunk.label!=null)
                return false
            if(previous is IRCodeChunk && chunk is IRCodeChunk) {
                if(retainSSA) {
                    // if the previous chunk doesn't end in a SSA branching instruction, flow continues into the next chunk, so they may be joined
                    val lastInstruction = previous.instructions.lastOrNull()
                    if (lastInstruction != null)
                        return lastInstruction.opcode !in OpcodesThatEndSSAblock
                }
                return true
            }
            return false
        }

        val chunks = mutableListOf<IRCodeChunkBase>()
        chunks += sub.chunks[0]
        for(ix in 1 until sub.chunks.size) {
            val lastChunk = chunks.last()
            when(val candidate = sub.chunks[ix]) {
                is IRCodeChunk -> {
                    if(mayJoinCodeChunks(lastChunk, candidate)) {
                        lastChunk.instructions += candidate.instructions
                        lastChunk.next = candidate.next
                        if(lastChunk is IRCodeChunk)
                            lastChunk.appendSrcPositions(candidate.sourceLinesPositions)
                    }
                    else
                        chunks += candidate
                }
                is IRInlineAsmChunk -> {
                    if(candidate.label!=null)
                        chunks += candidate
                    else if(lastChunk.isEmpty()) {
                        val label = lastChunk.label
                        chunks += if(label!=null)
                            IRInlineAsmChunk(label, candidate.assembly, candidate.isIR, candidate.next)
                        else
                            candidate
                    } else {
                        chunks += candidate
                    }
                }
                is IRInlineBinaryChunk -> {
                    if(candidate.label!=null)
                        chunks += candidate
                    else if(lastChunk.isEmpty()) {
                        val label = lastChunk.label
                        chunks += if(label!=null)
                            IRInlineBinaryChunk(label, candidate.data, candidate.next)
                        else
                            candidate
                    } else {
                        chunks += candidate
                    }
                }
                is IRLoopChunk -> chunks += candidate
            }
        }
        sub.chunks.clear()
        sub.chunks += chunks
    }

    private fun cleanupPushPop(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        //  push followed by pop to same target, or different target->replace with load
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(ins.opcode== Opcode.PUSH) {
                if(idx < chunk.instructions.size-1) {
                    val insAfter = chunk.instructions[idx+1]
                    if(insAfter.opcode == Opcode.POP) {
                        val srcReg = ins.requireSrcA()
                        val destReg = insAfter.requireDest()
                        if(srcReg.register==destReg.register) {
                            chunk.instructions.removeAt(idx)
                            chunk.instructions.removeAt(idx)
                        } else {
                            chunk.instructions[idx] = IRInstructions.move(destReg, srcReg)
                            chunk.instructions.removeAt(idx+1)
                        }
                        changed = true
                    }
                }
            }
        }
        return changed
    }

    private fun removeDoubleSecClc(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        //  double sec, clc
        //  sec+clc or clc+sec
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(ins.opcode== Opcode.SEC || ins.opcode== Opcode.CLC) {
                if(idx < chunk.instructions.size-1) {
                    val insAfter = chunk.instructions[idx+1]
                    if(insAfter.opcode == ins.opcode) {
                        chunk.instructions.removeAt(idx)
                        changed = true
                    }
                    else if(ins.opcode== Opcode.SEC && insAfter.opcode== Opcode.CLC) {
                        chunk.instructions.removeAt(idx)
                        changed = true
                    }
                    else if(ins.opcode== Opcode.CLC && insAfter.opcode== Opcode.SEC) {
                        chunk.instructions.removeAt(idx)
                        changed = true
                    }
                }
            }

            if(ins.opcode== Opcode.SEI || ins.opcode== Opcode.CLI) {
                if(idx < chunk.instructions.size-1) {
                    val insAfter = chunk.instructions[idx+1]
                    if(insAfter.opcode == ins.opcode) {
                        chunk.instructions.removeAt(idx)
                        changed = true
                    }
                    else if(ins.opcode== Opcode.SEI && insAfter.opcode== Opcode.CLI) {
                        chunk.instructions.removeAt(idx)
                        changed = true
                    }
                    else if(ins.opcode== Opcode.CLI && insAfter.opcode== Opcode.SEI) {
                        chunk.instructions.removeAt(idx)
                        changed = true
                    }
                }
            }
        }
        return changed
    }

    private fun removeWeirdBranches(chunk: IRCodeChunk, nextChunk: IRCodeChunkBase?, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            val labelSymbol = ins.labelTarget

            // remove jump/branch to label immediately below (= next chunk if it has that label)
            if(ins.opcode== Opcode.JUMP && labelSymbol!=null) {
                if(idx==chunk.instructions.size-1 && irprog.resolveCodeTarget(ins.codeTarget!!)===nextChunk) {
                    chunk.instructions.removeAt(idx)
                    changed = true
                }
            }

            // remove useless RETURN
            if(idx>0 && (ins.opcode == Opcode.RETURN || ins.opcode==Opcode.RETURNR || ins.opcode==Opcode.RETURNI)) {
                val previous = chunk.instructions[idx-1]
                if(previous.opcode in OpcodesThatBranchUnconditionally && idx<chunk.instructions.size) {
                    chunk.instructions.removeAt(idx)
                    changed = true
                }
            }

            // replace subsequent opcodes that jump by just the first
            if(idx>0 && (ins.opcode in OpcodesThatBranchUnconditionally)) {
                val previous = chunk.instructions[idx-1]
                if(previous.opcode in OpcodesThatBranchUnconditionally && idx<chunk.instructions.size) {
                    chunk.instructions.removeAt(idx)
                    changed = true
                }
            }

            // replace call + return --> jump
            // This can no longer be done here on the IR level, with the current CALL opcode that encodes the full subroutine call setup.
            // If machine code is ever generated from this IR, *that* should possibly optimize the JSR + RTS into a JMP.
        }
        return changed
    }

    private fun removeNeedlessCompares(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        // A CMPI #0 after an instruction that already sets the status bits can be removed.
        // The optimization is ONLY safe if the target CPU honors the "multi-byte ops set
        // status bits based on the full value" contract (CpuType.statusBitsOnMultiByteOps).
        // On 8-bit targets like 6502/65C02 a 16/32-bit DEC/INC/AND/OR/XOR/LOAD only sets
        // Z from the last byte, so a following CMPI #0 is NOT redundant - it tests the
        // full multi-byte value. In that case we keep the explicit CMPI.
        // See CpuType.statusBitsOnMultiByteOps for the rationale.
        val targetHonorsContract = irprog.options.compTarget.cpu.statusBitsOnMultiByteOps
        if (!targetHonorsContract) return false
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(idx>0 && idx<(indexedInstructions.size-1) && ins.opcode==Opcode.CMPI && ins.immediate?.integerValue==0) {
                val previous = indexedInstructions[idx-1].value
                if(previous.dest?.register==ins.srcA?.register) {
                    if (previous.opcode in OpcodesThatSetZeroFlagOnM68k) {
                        chunk.instructions.removeAt(idx)
                        changed = true
                    }
                }
            }
        }
        return changed
    }

    private fun removeNeedlessLoads(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        /*
load.b r2,#2
loadr.b r1,r2
jump p8_label_gen_2
         */
        var changed=false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(idx>=2 && ins.opcode in OpcodesThatBranchUnconditionally) {
                val previous = indexedInstructions[idx-1].value
                val previous2 = indexedInstructions[idx-2].value
                if(previous.opcode==Opcode.LOADR && previous2.opcode in OpcodesThatLoad) {
                    if(previous.srcA?.register==previous2.dest?.register) {
                        chunk.instructions[idx-2] = previous2.copy(dest = previous2.dest!!.withRegister(previous.dest!!.register))
                        chunk.instructions.removeAt(idx-1)
                        changed=true
                    }
                }
            }
        }
        return changed
    }

    private fun removeUselessArithmetic(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        // note: this is hard to solve for the non-immediate instructions atm because the values are loaded into registers first
        var changed = false

        fun arithmeticDelta(instruction: IRInstruction): Int? = when(instruction.opcode) {
            Opcode.ADD -> instruction.immediate?.integerValue
            Opcode.SUB -> instruction.immediate?.integerValue?.let { -it }
            Opcode.INC -> 1
            Opcode.DEC -> -1
            else -> null
        }

        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(idx < chunk.instructions.size-1) {
                val nextInstr = chunk.instructions[idx+1]
                val sameTarget = ins.dest != null && ins.dest?.register == nextInstr.dest?.register && ins.type == nextInstr.type
                if(sameTarget && ins.opcode in setOf(Opcode.MUL, Opcode.MULS) &&
                    ins.opcode == nextInstr.opcode && ins.type in setOf(IRDataType.BYTE, IRDataType.WORD, IRDataType.LONG) &&
                    ins.immediate?.integerValue != null && nextInstr.immediate?.integerValue != null) {
                    val product = ins.immediate!!.integerValue!!.toLong() * nextInstr.immediate!!.integerValue!!.toLong()
                    val foldedImmediate = when(ins.type) {
                        IRDataType.BYTE -> product.toInt() and 0xff
                        IRDataType.WORD -> product.toInt() and 0xffff
                        IRDataType.LONG -> product.toInt()
                        else -> error("unexpected integer multiplication type")
                    }
                    chunk.instructions[idx] = ins.copy(immediate = ImmediateOperand.Integer(foldedImmediate, ins.type!!))
                    chunk.instructions.removeAt(idx + 1)
                    changed = true
                    return@forEach
                }
                if(sameTarget) {
                    val delta = arithmeticDelta(ins)
                    val nextDelta = arithmeticDelta(nextInstr)
                    if(delta != null && nextDelta != null) {
                        val newDelta = delta + nextDelta
                        when(newDelta) {
                            0 -> {
                                chunk.instructions[idx] = IRInstructions.simple(Opcode.NOP)
                                chunk.instructions[idx+1] = IRInstructions.simple(Opcode.NOP)
                            }
                            1 -> chunk.instructions[idx] = IRInstructions.unary(Opcode.INC, ins.type!!, ins.dest!!.registerNumber)
                            -1 -> chunk.instructions[idx] = IRInstructions.unary(Opcode.DEC, ins.type!!, ins.dest!!.registerNumber)
                            else -> chunk.instructions[idx] = IRInstructions.binaryImmediate(
                                if(newDelta > 0) Opcode.ADD else Opcode.SUB,
                                ins.type!!,
                                ins.dest!!.registerNumber,
                                kotlin.math.abs(newDelta)
                            )
                        }
                        chunk.instructions.removeAt(idx+1)
                        changed = true
                        return@forEach
                    }

                    if(ins.opcode == Opcode.AND && nextInstr.opcode == Opcode.AND) {
                        chunk.instructions[idx] = ins.copy(immediate = ImmediateOperand.Integer(ins.immediate!!.integerValue!! and nextInstr.immediate!!.integerValue!!, ins.type!!))
                        chunk.instructions.removeAt(idx+1)
                        changed = true
                        return@forEach
                    }
                    if(ins.opcode == Opcode.OR && nextInstr.opcode == Opcode.OR) {
                        chunk.instructions[idx] = ins.copy(immediate = ImmediateOperand.Integer(ins.immediate!!.integerValue!! or nextInstr.immediate!!.integerValue!!, ins.type!!))
                        chunk.instructions.removeAt(idx+1)
                        changed = true
                        return@forEach
                    }
                    if(ins.opcode == Opcode.XOR && nextInstr.opcode == Opcode.XOR && ins.immediate?.integerValue == nextInstr.immediate?.integerValue) {
                        chunk.instructions[idx] = IRInstructions.simple(Opcode.NOP)
                        chunk.instructions[idx+1] = IRInstructions.simple(Opcode.NOP)
                        changed = true
                        return@forEach
                    }
                }
            }

            when (ins.opcode) {
                Opcode.DIV, Opcode.DIVS, Opcode.MUL, Opcode.MULS, Opcode.MOD -> {
                    val imm = ins.immediate?.integerValue
                    if (imm == 0 && ins.opcode in setOf(Opcode.MUL, Opcode.MULS) &&
                        ins.type in setOf(IRDataType.BYTE, IRDataType.WORD, IRDataType.LONG)) {
                        chunk.instructions[idx] = IRInstructions.load(ins.type!!, ins.dest!!.registerNumber, 0)
                        changed = true
                    } else if (imm == 1) {
                        chunk.instructions.removeAt(idx)
                        changed = true
                    }
                }
                Opcode.ADD, Opcode.SUB -> {
                    val imm = ins.immediate?.integerValue
                    if (imm == 1) {
                        chunk.instructions[idx] = IRInstructions.unary(
                            if (ins.opcode == Opcode.ADD) Opcode.INC else Opcode.DEC,
                            ins.type!!,
                            ins.dest!!.registerNumber
                        )
                        changed = true
                    } else if (imm == 0) {
                        chunk.instructions.removeAt(idx)
                        changed = true
                    }

                    if(!changed && idx < chunk.instructions.size-1) {
                        val nextInstr = chunk.instructions[idx+1]
                        if(nextInstr.dest?.register==ins.dest?.register) {
                            when (nextInstr.opcode) {
                                Opcode.INC -> {
                                    // INC after ADD or SUB
                                    val newValue = if (ins.opcode == Opcode.ADD) imm!! + 1 else imm!! - 1
                                    chunk.instructions[idx] = IRInstructions.binaryImmediate(ins.opcode, ins.type!!, ins.dest!!.registerNumber, newValue)
                                    chunk.instructions.removeAt(idx + 1)
                                    changed = true
                                }
                                Opcode.DEC -> {
                                    // DEC after ADD or SUB
                                    val newValue = if (ins.opcode == Opcode.ADD) imm!! - 1 else imm!! + 1
                                    chunk.instructions[idx] = IRInstructions.binaryImmediate(ins.opcode, ins.type!!, ins.dest!!.registerNumber, newValue)
                                    chunk.instructions.removeAt(idx + 1)
                                    changed = true
                                }
                                Opcode.ADD -> {
                                    // ADD after ADD or SUB
                                    val nextImm = nextInstr.immediate!!.integerValue!!
                                    val newValue = if (ins.opcode == Opcode.ADD) imm!! + nextImm else imm!! - nextImm
                                    chunk.instructions[idx] = IRInstructions.binaryImmediate(ins.opcode, ins.type!!, ins.dest!!.registerNumber, newValue)
                                    chunk.instructions.removeAt(idx + 1)
                                    changed = true
                                }
                                Opcode.SUB -> {
                                    // SUB after ADD or SUB
                                    val nextImm = nextInstr.immediate!!.integerValue!!
                                    val newValue = if (ins.opcode == Opcode.ADD) imm!! - nextImm else imm!! + nextImm
                                    chunk.instructions[idx] = IRInstructions.binaryImmediate(ins.opcode, ins.type!!, ins.dest!!.registerNumber, newValue)
                                    chunk.instructions.removeAt(idx + 1)
                                    changed = true
                                }
                                else -> {}
                            }
                        }
                    }
                }
                Opcode.AND -> {
                    val imm = ins.immediate?.integerValue
                    when (imm) {
                        0 -> {
                            chunk.instructions[idx] = IRInstructions.load(ins.type!!, ins.dest!!.registerNumber, 0)
                            changed = true
                        }
                        255 if ins.type == IRDataType.BYTE -> {
                            chunk.instructions.removeAt(idx)
                            changed = true
                        }
                        65535 if ins.type == IRDataType.WORD -> {
                            chunk.instructions.removeAt(idx)
                            changed = true
                        }
                        -1 if ins.type == IRDataType.LONG -> {
                            chunk.instructions.removeAt(idx)
                            changed = true
                        }
                    }
                    // convert AND with all-ones-except-one-bit into BITCLR
                    if(!changed) {
                        val immv = imm ?: return@forEach
                        val clearedBits = when(ins.type) {
                            IRDataType.BYTE -> immv xor 0xFF
                            IRDataType.WORD -> immv xor 0xFFFF
                            IRDataType.LONG -> immv xor -1
                            else -> 0
                        }
                        if(clearedBits > 0 && clearedBits and (clearedBits - 1) == 0) {
                            val bitPos = Integer.numberOfTrailingZeros(clearedBits)
                            chunk.instructions[idx] = IRInstructions.binaryImmediate(Opcode.BITCLR, ins.type!!, ins.dest!!.registerNumber, bitPos)
                            changed = true
                        }
                    }
                }
                Opcode.OR -> {
                    val imm = ins.immediate?.integerValue
                    if (imm == null) return@forEach
                    if (imm == 0) {
                        chunk.instructions.removeAt(idx)
                        changed = true
                    } else if ((imm == 255 && ins.type == IRDataType.BYTE) ||
                               (imm == 65535 && ins.type == IRDataType.WORD) ||
                               (imm == -1 && ins.type == IRDataType.LONG)) {
                        chunk.instructions[idx] = IRInstructions.load(ins.type!!, ins.dest!!.registerNumber, imm)
                        changed = true
                    }
                    // convert OR with power-of-2 into BITSET
                    if(!changed && imm > 0 && imm and (imm - 1) == 0) {
                        val bitPos = Integer.numberOfTrailingZeros(imm)
                        chunk.instructions[idx] = IRInstructions.binaryImmediate(Opcode.BITSET, ins.type!!, ins.dest!!.registerNumber, bitPos)
                        changed = true
                    }
                }
                Opcode.XOR -> {
                    val imm = ins.immediate?.integerValue
                    if (imm == null) return@forEach
                    if (imm == 0) {
                        chunk.instructions.removeAt(idx)
                        changed = true
                    }
                    // convert XOR with power-of-2 into BITTOG
                    if(!changed && imm > 0 && imm and (imm - 1) == 0) {
                        val bitPos = Integer.numberOfTrailingZeros(imm)
                        chunk.instructions[idx] = IRInstructions.binaryImmediate(Opcode.BITTOG, ins.type!!, ins.dest!!.registerNumber, bitPos)
                        changed = true
                    }
                }
                else -> {}
            }

            fun optimizeImmediateLoad(replacementOpcode: Opcode, isCommutative: Boolean) {

                fun getImmediateLoad(reg: VirtualRegister): Pair<Int, Int>? {
                    // look if the given register gets an immediate value 1 or 2 istructions back
                    // returns (index of load instruction, immediate value) or null.
                    if(idx>=1) {
                        val previous = indexedInstructions[idx-1].value
                        if(previous.opcode==Opcode.LOAD && previous.dest?.register==reg && previous.immediate?.integerValue!=null)
                            return idx-1 to previous.immediate!!.integerValue!!
                    }
                    if(idx>=2) {
                        val previous = indexedInstructions[idx-2].value
                        if(previous.opcode==Opcode.LOAD && previous.dest?.register==reg && previous.immediate?.integerValue!=null)
                            return idx - 2 to previous.immediate!!.integerValue!!
                    }
                    return null
                }

                val destOperand = ins.dest
                val srcOperand = ins.srcA
                if(destOperand!=null) {
                    if (isCommutative) {
                        val immediate1 = getImmediateLoad(destOperand.register)
                        if (immediate1 != null) {
                            chunk.instructions[idx] = IRInstructions.binaryImmediate(replacementOpcode, ins.type!!, srcOperand!!.registerNumber, immediate1.second)
                            chunk.instructions.removeAt(immediate1.first)
                            changed = true
                            return
                        }
                    }
                    val immediate2 = getImmediateLoad(srcOperand!!.register)
                    if (immediate2 != null) {
                        chunk.instructions[idx] = IRInstructions.binaryImmediate(replacementOpcode, ins.type!!, destOperand.registerNumber, immediate2.second)
                        chunk.instructions.removeAt(immediate2.first)
                        changed = true
                    }
                }
            }

            // try to use immediate arithmetic instruction if possible
            when(ins.opcode) {
                Opcode.ADDR -> optimizeImmediateLoad(Opcode.ADD, true)
                Opcode.MULR -> optimizeImmediateLoad(Opcode.MUL, true)
                Opcode.MULSR -> optimizeImmediateLoad(Opcode.MULS, true)
                Opcode.SUBR -> optimizeImmediateLoad(Opcode.SUB, false)
                Opcode.DIVR -> optimizeImmediateLoad(Opcode.DIV, false)
                Opcode.DIVSR -> optimizeImmediateLoad(Opcode.DIVS, false)
                Opcode.MODR -> optimizeImmediateLoad(Opcode.MOD, false)
                // Opcode.DIVMODR - skipped, no immediate DIVMOD variant exists
                else -> {}
            }
        }
        return changed
    }

    private fun collapseAdjacentLoadrChains(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(idx >= chunk.instructions.size - 1 || ins.opcode != Opcode.LOADR)
                return@forEach

            val nextInstr = indexedInstructions[idx + 1].value
            if(nextInstr.opcode != Opcode.LOADR || ins.type != nextInstr.type)
                return@forEach

            if(nextInstr.srcA?.register == ins.dest?.register) {
                chunk.instructions[idx + 1] = nextInstr.copy(srcA = nextInstr.srcA!!.withRegister(ins.srcA!!.register))
                chunk.instructions[idx] = IRInstructions.simple(Opcode.NOP)
                changed = true
            }
        }
        return changed
    }

    private fun removeSelfIdentityOps(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            when(ins.opcode) {
                Opcode.LOADR -> {
                    if(ins.dest?.register == ins.srcA?.register) {
                        chunk.instructions[idx] = IRInstructions.simple(Opcode.NOP)
                        changed = true
                    }
                }
                Opcode.ANDR, Opcode.ORR -> {
                    if(ins.dest?.register == ins.srcA?.register) {
                        chunk.instructions[idx] = IRInstructions.simple(Opcode.NOP)
                        changed = true
                    }
                }
                Opcode.XORR -> {
                    if(ins.dest?.register == ins.srcA?.register) {
                        chunk.instructions[idx] = IRInstructions.load(ins.type!!, ins.dest!!.registerNumber, 0)
                        changed = true
                    }
                }
                else -> {}
            }
        }
        return changed
    }

    private fun simplifyShiftByZero(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(idx>0 && ins.opcode in setOf(Opcode.LSLN, Opcode.LSRN, Opcode.ASRN) && ins.srcA != null) {
                val prev = indexedInstructions[idx-1].value
                if(prev.opcode == Opcode.LOAD && prev.dest?.register == ins.srcA?.register && prev.immediate?.integerValue == 0) {
                    chunk.instructions[idx] = IRInstructions.simple(Opcode.NOP)
                    chunk.instructions.removeAt(idx-1)
                    changed = true
                }
            }
        }
        return changed
    }

    private fun cancelAdjacentOps(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(idx >= chunk.instructions.size - 1)
                return@forEach
            val insAfter = chunk.instructions[idx+1]

            when(ins.opcode) {
                Opcode.INV -> {
                    if(insAfter.opcode == Opcode.INV && insAfter.dest?.register == ins.dest?.register) {
                        chunk.instructions[idx] = IRInstructions.simple(Opcode.NOP)
                        chunk.instructions[idx+1] = IRInstructions.simple(Opcode.NOP)
                        changed = true
                    }
                }
                Opcode.NEG -> {
                    if(insAfter.opcode == Opcode.NEG && insAfter.dest?.register == ins.dest?.register) {
                        chunk.instructions[idx] = IRInstructions.simple(Opcode.NOP)
                        chunk.instructions[idx+1] = IRInstructions.simple(Opcode.NOP)
                        changed = true
                    }
                }
                Opcode.EXT, Opcode.EXTS, Opcode.EXTL, Opcode.EXTLS -> {
                    if(insAfter.opcode == ins.opcode && insAfter.dest?.register == ins.dest?.register && insAfter.type == ins.type) {
                        chunk.instructions[idx+1] = IRInstructions.simple(Opcode.NOP)
                        changed = true
                    }
                }
                Opcode.INC -> {
                    if(insAfter.opcode == Opcode.DEC && insAfter.dest?.register == ins.dest?.register) {
                        chunk.instructions[idx] = IRInstructions.simple(Opcode.NOP)
                        chunk.instructions[idx+1] = IRInstructions.simple(Opcode.NOP)
                        changed = true
                    }
                }
                Opcode.DEC -> {
                    if(insAfter.opcode == Opcode.INC && insAfter.dest?.register == ins.dest?.register) {
                        chunk.instructions[idx] = IRInstructions.simple(Opcode.NOP)
                        chunk.instructions[idx+1] = IRInstructions.simple(Opcode.NOP)
                        changed = true
                    }
                }
                else -> {}
            }
        }
        return changed
    }

    private fun fusePointerPostInc(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        // Fuse loadm + loadi/storei + incm on same pointer variable into LOADP_INC/STOREP_INC
        // Pattern: loadm.l rX, P  ; loadi.b rY,rX,#0  ; ... ; incm.l P  =>  loadp_inc.b rY,P
        //          loadm.l rX, P  ; storei.b rY,rX,#0 ; ... ; incm.l P => storep_inc.b rY,P
        // Only if rX is defined once and used once (the loadi/storei), and incm is after.
        if(chunk.instructions.size < 3) return false
        // Count register uses
        val readCounts = mutableMapOf<VirtualRegister, Int>()
        for(ins in chunk.instructions) {
            ins.uses.forEach { reg -> readCounts[reg] = (readCounts[reg] ?: 0) + 1 }
        }

        var changed = false
        // Find loadm indices
        val loadmByAddr = mutableMapOf<String, MutableList<Int>>() // address string -> list of loadm indices
        val loadmRegByIdx = mutableMapOf<Int, VirtualRegister>() // loadm idx -> rX
        for((idx, ins) in indexedInstructions) {
            if(ins.opcode == Opcode.LOADM && ins.type != IRDataType.FLOAT) {
                // only integer loadm (float uses a float register, not fusible into a pointer)
                val addr = ins.memory?.symbolName ?: continue
                loadmByAddr.getOrPut(addr) { mutableListOf() }.add(idx)
                loadmRegByIdx[idx] = ins.requireDest().register
            }
        }
        // For each incm, try to find matching loadm+loadi/storei
        val toRemove = mutableSetOf<Int>()
        val toReplace = mutableMapOf<Int, IRInstruction>()
        for((incIdx, incIns) in indexedInstructions) {
            if(incIns.opcode != Opcode.INCM) continue
            val addr = incIns.memory?.symbolName ?: continue
            val loadmIndices = loadmByAddr[addr] ?: continue
            // Find the latest loadm before incm that has a matching loadi/storei using its reg
            for(loadmIdx in loadmIndices.reversed()) {
                if(loadmIdx >= incIdx) continue
                if(loadmIdx in toRemove) continue
                val baseReg = loadmRegByIdx[loadmIdx] ?: continue
                // Check baseReg is used exactly once (in loadi/storei) and not otherwise
                if((readCounts[baseReg] ?: 0) != 1) continue
                // Find loadi/storei that uses baseReg between loadm and incm
                var foundIdx: Int? = null
                var foundIns: IRInstruction? = null
                for((midIdx, midIns) in indexedInstructions) {
                    if(midIdx <= loadmIdx || midIdx >= incIdx) continue
                    if(midIdx in toRemove || midIdx in toReplace) continue
                    if(midIns.type == IRDataType.FLOAT) continue
                    val indirect = midIns.memory as? MemoryReference.Indirect ?: continue
                    if(indirect.pointer.register != baseReg) continue
                    if(midIns.opcode == Opcode.LOADI) {
                        // LOADI must have offset 0 to be fusible to post-inc (otherwise need add)
                        if(indirect.displacement != 0) continue
                        foundIdx = midIdx
                        foundIns = midIns
                        break
                    }
                    if(midIns.opcode == Opcode.STOREI) {
                        if(indirect.displacement != 0) continue
                        foundIdx = midIdx
                        foundIns = midIns
                        break
                    }
                }
                if(foundIdx == null || foundIns == null) continue
                // Also ensure no other use of baseReg between loadm and incm besides this one
                // Already ensured readCounts==1, but also ensure no other loadm defines same reg
                // Fuse
                val newType = foundIns.type!!
                val newIns = if(foundIns.opcode == Opcode.LOADI)
                    IRInstructions.loadMemory(Opcode.LOADP_INC, newType, foundIns.requireDest().registerNumber, IRMemory.direct(addr))
                else
                    IRInstructions.storeMemory(Opcode.STOREP_INC, newType, foundIns.requireSrcA().registerNumber, IRMemory.direct(addr))
                toReplace[foundIdx] = newIns
                toRemove.add(loadmIdx)
                toRemove.add(incIdx)
                changed = true
                break // one incm fuses one loadm
            }
        }
        if(!changed) return false
        // Apply replacements and removals in reverse order to keep indices stable
        val allIndices = (toRemove + toReplace.keys).sortedDescending()
        for(idx in allIndices) {
            if(idx in toRemove) {
                chunk.instructions[idx] = IRInstructions.simple(Opcode.NOP)
            }
        }
        for((idx, newIns) in toReplace) {
            chunk.instructions[idx] = newIns
        }
        return true
    }

    private fun removeNops(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if (ins.opcode == Opcode.NOP) {
                changed = true
                chunk.instructions.removeAt(idx)
            }
        }
        return changed
    }

    private fun foldLoadStoremToStoreim(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        /*
        load.b rX, #imm
        storem.b rX, addr           ->  storeim.b #imm, addr
         */
        var changed = false
        indexedInstructions.forEach { (idx, ins) ->
            if(ins.opcode==Opcode.STOREM && idx>0) {
                val prev = indexedInstructions[idx-1].value
                if(prev.opcode==Opcode.LOAD && prev.immediate !is ImmediateOperand.SymbolAddress) {
                    val sameReg = ins.srcA?.register == prev.dest?.register
                    if(sameReg) {
                        val newIns = when(val immediate = prev.immediate) {
                            is ImmediateOperand.Integer -> IRInstructions.storeImmediate(ins.type!!, immediate.value, ins.requireMemory())
                            is ImmediateOperand.FloatValue -> IRInstructions.storeImmediateFloat(immediate.value, ins.requireMemory())
                            else -> null
                        }
                        if(newIns != null) {
                            chunk.instructions[idx] = newIns
                            chunk.instructions[idx-1] = IRInstructions.simple(Opcode.NOP)
                            changed = true
                        }
                    }
                }
            }
        }
        return changed
    }

    private fun removeDoubleLoadsAndStores(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        var changed = false
        indexedInstructions.forEach { (idx, ins) ->
            if(ins.opcode==Opcode.STOREM && idx>0) {
                val prev = indexedInstructions[idx-1].value
                if(prev.opcode==Opcode.LOADM) {
                    // loadm.X rX,something | storem.X rX,something ?? -> get rid of the store.
                    if(ins.memory == prev.memory) {
                        changed=true
                        chunk.instructions.removeAt(idx)
                    }
                }
            }

/*
    Possible other optimizations:
            // detect multiple loads to the same target registers, only keep first (if source is not I/O memory)
            // detect multiple stores to the same target, only keep first (if target is not I/O memory)
            // detect multiple float ffrom/fto to the same target, only keep first
            // detect subsequent same xors/nots/negs, remove the pairs completely as they cancel out
            // detect multiple same ands, ors; only keep first
            // detect multiple registers being assigned the same value (and not changed) - use only 1 of them  (hard!)
            // ...
*/
        }
        return changed
    }

    private fun removeDoubleLoadsAndStoresIndexed(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        // Coalesce indexed LOADX/STOREX/STOREZX pairs to the same array element with same index register
        // within a single chunk when there is no intervening use of the value.
        // Analogous to removeDoubleLoadsAndStores for non-indexed LOADM/STOREM but allows a gap as long
        // as the value register is not read/written and the index register is not redefined, and no
        // side-effecting instruction occurs between. Keeps the optimization single-chunk.
        if(chunk.instructions.size < 2) return false

        fun indexRegOf(ins: IRInstruction): VirtualRegister? = (ins.memory as? MemoryReference.Indexed)?.index?.register
        fun valueRegOf(ins: IRInstruction): VirtualRegister? = when(ins.opcode) {
            Opcode.LOADX -> ins.dest?.register
            Opcode.STOREX -> ins.srcA?.register
            else -> null
        }
        fun locationKey(ins: IRInstruction): Any? {
            val mem = ins.memory as? MemoryReference.Indexed ?: return null
            return Triple(ins.type, mem.base, mem.displacement)
        }

        val toRemove = mutableSetOf<Int>()

        // Phase 1: LOADX -> STOREX redundant store (same value, same index, same address, no intervening value use)
        for((currPos, currEntry) in indexedInstructions.withIndex()) {
            val currIdx = currEntry.index
            val currIns = currEntry.value
            if(currIdx in toRemove) continue
            if(currIns.opcode != Opcode.STOREX) continue
            val currIndexReg = indexRegOf(currIns) ?: continue
            val currValueReg = valueRegOf(currIns) ?: continue
            val currKey = locationKey(currIns)
            // search backwards for matching LOADX
            for(prevPos in currPos - 1 downTo 0) {
                val prevEntry = indexedInstructions[prevPos]
                val prevIdx = prevEntry.index
                val prevIns = prevEntry.value
                if(prevIdx in toRemove) continue
                if(prevIns.opcode != Opcode.LOADX) continue
                if(prevIns.type != currIns.type) continue
                if(locationKey(prevIns) != currKey) continue
                if(indexRegOf(prevIns) != currIndexReg) continue
                if(valueRegOf(prevIns) != currValueReg) continue

                var blocked = false
                for(midPos in prevPos + 1 until currPos) {
                    val midIns = indexedInstructions[midPos].value
                    if(midIns.opcode in OpcodesWithSideEffects) { blocked = true; break }
                    if(currValueReg in midIns.definitions || currValueReg in midIns.uses) { blocked = true; break }
                    if(currIndexReg in midIns.definitions) { blocked = true; break }
                    // intervening indexed access to same location breaks direct coalescing (let closer pair handle it)
                    if(locationKey(midIns) == currKey && midIns.opcode in setOf(Opcode.LOADX, Opcode.STOREX, Opcode.STOREZX)) {
                        blocked = true; break
                    }
                }
                if(!blocked) {
                    toRemove.add(currIdx)
                    break
                }
            }
        }

        // Phase 2: dead store for indexed ops - earlier STOREX/STOREZX overwritten by later STOREX/STOREZX with same location/index
        for((currPos, currEntry) in indexedInstructions.withIndex()) {
            val currIdx = currEntry.index
            val currIns = currEntry.value
            if(currIdx in toRemove) continue
            if(currIns.opcode !in setOf(Opcode.STOREX, Opcode.STOREZX)) continue
            val currIndexReg = indexRegOf(currIns) ?: continue
            val currKey = locationKey(currIns)
            for(prevPos in currPos - 1 downTo 0) {
                val prevEntry = indexedInstructions[prevPos]
                val prevIdx = prevEntry.index
                val prevIns = prevEntry.value
                if(prevIdx in toRemove) continue
                if(prevIns.opcode !in setOf(Opcode.STOREX, Opcode.STOREZX)) continue
                if(prevIns.type != currIns.type) {
                    // require exact same type for indexed store coalescing
                    continue
                }
                if(locationKey(prevIns) != currKey) continue
                if(indexRegOf(prevIns) != currIndexReg) continue
                var blocked = false
                for(midPos in prevPos + 1 until currPos) {
                    val midIns = indexedInstructions[midPos].value
                    if(midIns.opcode in OpcodesWithSideEffects) { blocked = true; break }
                    if(currIndexReg in midIns.definitions) { blocked = true; break }
                    // any LOADX to same location is a read of memory - prevents dead store elimination
                    if(midIns.opcode == Opcode.LOADX && locationKey(midIns) == currKey && indexRegOf(midIns) == currIndexReg) { blocked = true; break }
                }
                if(!blocked) {
                    toRemove.add(prevIdx)
                    break
                }
            }
        }

        if(toRemove.isEmpty()) return false
        toRemove.sortedDescending().forEach { idx ->
            if(idx < chunk.instructions.size) chunk.instructions.removeAt(idx)
        }
        return true
    }

    private fun simplifyConstantReturns(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        //  use a RETURNI when a RETURNR is just returning a constant that was loaded into a register just before
        var changed = false
        indexedInstructions.reversed().forEach { (idx, ins) ->
            if(ins.opcode==Opcode.RETURNR) {
                if(idx>0) {
                    val insBefore = chunk.instructions[idx-1]
                    if(insBefore.opcode == Opcode.LOAD && insBefore.immediate!=null) {
                        val newIns = when(val imm = insBefore.immediate!!) {
                            is ImmediateOperand.Integer -> IRInstructions.returnImmediate(ins.type!!, imm.value)
                            is ImmediateOperand.FloatValue -> IRInstructions.returnImmediateFloat(imm.value)
                            is ImmediateOperand.SymbolAddress -> null
                        }
                        if(newIns != null) {
                            chunk.instructions[idx] = newIns
                            chunk.instructions.removeAt(idx-1)
                            changed = true
                        }
                    }
                }
            }
        }
        return changed
    }

    private fun collapseConversions(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        // Collapse adjacent conversions that compose into a single cheaper conversion:
        //   ext.b  rX,rA + ext.w  rY,rX -> extl.b  rY,rA     (zero-extends compose)
        //   exts.b rX,rA + ext.w  rY,rX -> extl.b  rY,rA     (the outer zero-extend only sees the low
        //                                                     byte that the inner instruction defined)
        //   ext.b  rX,rA + exts.w rY,rX -> extl.b  rY,rA     (sign-extending a zero-extended byte
        //                                                     yields the same as zero-extending it)
        //   exts.b rX,rA + exts.w rY,rX -> extls.b rY,rA     (sign-extends compose)
        // and truncation/widening pairs that cancel into a single mask:
        //   lsigw.l rX,rA + ext.w  rY,rX -> loadr.l rY,rA ; and.l rY,#$ffff
        //   lsigb.? rX,rA + ext.b  rY,rX -> loadr.? rY,rA ; and.? rY,#$ff
        // Only applied when the intermediate register is not read anywhere else in this chunk.
        if(chunk.instructions.size<2) return false
        val readCounts = chunk.usedRegisters().readRegs.withDefault { 0 }

        class Mod(val idx: Int, val replacement: IRInstruction?)   // null replacement = delete
        val mods = mutableListOf<Mod>()
        val claimed = mutableSetOf<Int>()

        for(k in 0 until indexedInstructions.size-1) {
            val (i, inner) = indexedInstructions[k]
            val (_, outer) = indexedInstructions[k+1]
            val mid = inner.dest?.register

            fun singleResult(opcode: Opcode) =
                IRInstructions.binary(opcode, IRDataType.BYTE, outer.requireDest().registerNumber, inner.requireSrcA().registerNumber)

            if(i in claimed || i+1 in claimed)
                continue

            when {
                // composed sign/zero extension chains on bytes
                outer.opcode in setOf(Opcode.EXT, Opcode.EXTS) && outer.type==IRDataType.WORD
                        && inner.opcode in setOf(Opcode.EXT, Opcode.EXTS) && inner.type==IRDataType.BYTE
                        && mid!=null && mid==outer.srcA?.register
                        && readCounts.getValue(mid)==1 -> {
                    val newOp = if(inner.opcode==Opcode.EXTS && outer.opcode==Opcode.EXTS) Opcode.EXTLS else Opcode.EXTL
                    mods.add(Mod(i+1, null))
                    mods.add(Mod(i, singleResult(newOp)))
                }
                // truncate-long-to-word followed by zero-extend word-to-long == mask with $ffff
                inner.opcode==Opcode.LSIGW && inner.type==IRDataType.LONG
                        && outer.opcode==Opcode.EXT && outer.type==IRDataType.WORD
                        && mid!=null && mid==outer.srcA?.register && inner.srcA!=null && outer.dest!=null
                        && readCounts.getValue(mid)==1 -> {
                    mods.add(Mod(i+1, IRInstructions.binaryImmediate(Opcode.AND, IRDataType.LONG, outer.requireDest().registerNumber, 0xffff)))
                    mods.add(Mod(i, IRInstructions.move(IRDataType.LONG, outer.requireDest().registerNumber, inner.requireSrcA().registerNumber)))
                }
                // truncate-long/word-to-byte followed by zero-extend byte-to-word == mask with $ff
                inner.opcode==Opcode.LSIGB && inner.type in setOf(IRDataType.WORD, IRDataType.LONG)
                        && outer.opcode==Opcode.EXT && outer.type==IRDataType.BYTE
                        && mid!=null && mid==outer.srcA?.register && inner.srcA!=null && outer.dest!=null
                        && readCounts.getValue(mid)==1 -> {
                    mods.add(Mod(i+1, IRInstructions.binaryImmediate(Opcode.AND, inner.type!!, outer.requireDest().registerNumber, 0xff)))
                    mods.add(Mod(i, IRInstructions.move(inner.type!!, outer.requireDest().registerNumber, inner.requireSrcA().registerNumber)))
                }
                else -> continue
            }
            claimed.add(i)
            claimed.add(i+1)
        }
        var changed = false
        for(mod in mods.sortedByDescending { it.idx }) {
            if(mod.replacement==null) {
                chunk.instructions.removeAt(mod.idx)
                changed = true
            }
            else {
                chunk.instructions[mod.idx] = mod.replacement
                changed = true
            }
        }
        return changed
    }

    private fun deduplicateAddressComputations(chunk: IRCodeChunk): Boolean {
        // IR3: collapse repeated buf+i address computations inside one chunk.
        // Looks for the 4-instruction pattern that computes buf+i into a pointer:
        //   loadm.p  ptrReg, buf
        //   loadm.w/b idxReg, idxVar
        //   ext*     extReg, idxReg   (any EXT/EXTS/EXTL/EXTLS)
        //   addr.p   destReg, otherReg where destReg/otherReg are ptrReg and extReg (either order)
        // If two such groups with identical (buf, idxVar) occur in the same chunk with no barrier
        // between them, the second group is replaced by a single loadr.p copy from the first group's dest.
        if(chunk.instructions.size < 8) return false

        data class MemKey(val type: IRDataType?, val base: AddressBase, val displacement: Int)

        data class AddrGroup(
            val startIdx: Int,
            val ptrReg: Int,
            val idxReg: Int,
            val extReg: Int,
            val destReg: Int,
            val bufKey: MemKey,
            val idxKey: MemKey
        )

        fun memKey(ins: IRInstruction): MemKey? {
            // type/base/displacement uniquely identify the variable
            val mem = ins.memory as? MemoryReference.Direct ?: return null
            return MemKey(ins.type, mem.base, mem.displacement)
        }

        fun writesMem(ins: IRInstruction, key: MemKey): Boolean {
            if(memKey(ins) != key) return false
            return ins.memoryEffect == MemoryEffect.WRITE || ins.memoryEffect == MemoryEffect.READ_WRITE
        }

        val groups = mutableListOf<AddrGroup>()
        var i = 0
        while(i <= chunk.instructions.size - 4) {
            val a = chunk.instructions[i]
            val b = chunk.instructions[i+1]
            val c = chunk.instructions[i+2]
            val d = chunk.instructions[i+3]
            val isA = a.opcode == Opcode.LOADM && a.type == IRDataType.POINTER && a.dest != null
            val isB = b.opcode == Opcode.LOADM && b.type in setOf(IRDataType.BYTE, IRDataType.WORD) && b.dest != null
            val isC = c.opcode in setOf(Opcode.EXT, Opcode.EXTS, Opcode.EXTL, Opcode.EXTLS) && c.dest != null && c.srcA?.register == b.dest?.register
            val isD = d.opcode == Opcode.ADDR && d.type == IRDataType.POINTER && d.dest != null && d.srcA != null
            if(isA && isB && isC && isD) {
                val ptrReg = a.requireDest().register
                val extReg = c.requireDest().register
                val d1 = d.requireDest().register
                val d2 = d.requireSrcA().register
                val destOk = (d1 == ptrReg && d2 == extReg) || (d1 == extReg && d2 == ptrReg)
                if(destOk) {
                    val bufKey = memKey(a)
                    val idxKey = memKey(b)
                    if(bufKey != null && idxKey != null) {
                        groups.add(AddrGroup(i, ptrReg.num, b.requireDest().registerNumber, extReg.num, d1.num, bufKey, idxKey))
                        i += 4
                        continue
                    }
                }
            }
            i++
        }
        if(groups.size < 2) return false

        // For each later group, find earliest earlier group with same (buf,idx) and no barrier; replace it
        class Mod(val idx: Int, val replacement: IRInstruction?)
        val mods = mutableListOf<Mod>()
        val claimed = mutableSetOf<Int>()
        for(j in groups.indices) {
            val gj = groups[j]
            if(gj.startIdx in claimed) continue
            // find earliest i<j with same keys
            var found: AddrGroup? = null
            for(k in 0 until j) {
                val gk = groups[k]
                if(gk.startIdx in claimed) continue  // don't use a replaced group as source; keep earliest non-replaced
                if(gk.bufKey != gj.bufKey || gk.idxKey != gj.idxKey) continue
                // barrier check between gk and gj
                var barrier = false
                for(t in gk.startIdx + 4 until gj.startIdx) {
                    if(t in claimed) continue // already slated for removal, ignore? but we haven't applied mods yet
                    val ins = chunk.instructions[t]
                    if(VirtualRegister.int(gk.destReg) in ins.definitions) { barrier = true; break }
                    if(writesMem(ins, gk.bufKey) || writesMem(ins, gk.idxKey)) { barrier = true; break }
                }
                if(!barrier) { found = gk; break }
            }
            if(found != null) {
                // replace gj's 4 insns with single loadr.p
                if(gj.startIdx in claimed || gj.startIdx+1 in claimed || gj.startIdx+2 in claimed || gj.startIdx+3 in claimed) continue
                mods.add(Mod(gj.startIdx + 3, null))
                mods.add(Mod(gj.startIdx + 2, null))
                mods.add(Mod(gj.startIdx + 1, null))
                mods.add(Mod(gj.startIdx, IRInstructions.move(IRDataType.POINTER, gj.destReg, found.destReg)))
                claimed.add(gj.startIdx); claimed.add(gj.startIdx+1); claimed.add(gj.startIdx+2); claimed.add(gj.startIdx+3)
            }
        }
        if(mods.isEmpty()) return false
        for(mod in mods.sortedByDescending { it.idx }) {
            if(mod.replacement == null) chunk.instructions.removeAt(mod.idx)
            else chunk.instructions[mod.idx] = mod.replacement
        }
        return true
    }

    private fun removeDeadStores(chunk: IRCodeChunk, indexedInstructions: List<IndexedValue<IRInstruction>>): Boolean {
        // Detect and remove dead stores: when a register is written but never read before being overwritten.
        // Example:
        //   LOAD r1, #5      <- dead store (r1 overwritten before use)
        //   LOAD r1, #10
        //   USE r1

        // Track for each register: (index of last write, whether value was read since)
        val pendingWrites = mutableMapOf<VirtualRegister, Pair<Int, Boolean>>()  // reg -> (writeIdx, wasRead)
        val deadStores = mutableSetOf<Int>()

        indexedInstructions.forEach { (idx, ins) ->
            if (ins.opcode in OpcodesWithSideEffects) {
                // Instruction has side effects, must never be removed as dead store,
                // AND it also counts as a "read" of all current pending registers to avoid them being removed
                pendingWrites.clear()
                return@forEach
            }
            // Reads and writes are taken from ALL operand slots (including the index and pointer
            // registers inside a memory reference), so a value that is only read through a
            // secondary slot is never mistaken for a dead store.
            val accesses = ins.registerAccesses

            // First, mark the registers that this instruction READS
            for(access in accesses) {
                if(access.direction != OperandDirection.DEF) {
                    val existing = pendingWrites[access.register]
                    if(existing != null)
                        pendingWrites[access.register] = existing.first to true
                }
            }

            // Then, record the registers that this instruction WRITES
            for(access in accesses) {
                if(access.direction != OperandDirection.USE) {
                    // Check if previous write to this reg was dead (never read before this overwrite)
                    val existing = pendingWrites[access.register]
                    if(existing != null && !existing.second) {
                        deadStores.add(existing.first)
                    }
                    // Record this new write as pending (not yet read)
                    pendingWrites[access.register] = idx to false
                }
            }
        }
        
        // Any remaining pending writes that were never read are also dead
        // (unless they're the final value needed - but we can't know that here)
        // Actually, we should NOT remove these because they might be the final value
        
        // Remove dead stores (in reverse order to preserve indices)
        var changed = false
        deadStores.sortedDescending().forEach { idx ->
            if(idx < chunk.instructions.size) {
                chunk.instructions.removeAt(idx)
                changed = true
            }
        }
        return changed
    }

    private fun optimizeLoopCounters(sub: IRSubroutine): Boolean {
        // M4: keep loop counter in register instead of round-tripping through memory.
        // Looks for the non-constant range for-loop pattern emitted by IRCodeGen.translateForInNonConstantRange
        // with step 1: STORM loopvar, loopLabel: body..., LOADM loopvar + CMP + BSTEQ, INCM loopvar + JUMP loopLabel.
        // If the body does not write to loopvar, replace the memory round-trip with register operations.
        var changed = false
        var idx = 0
        while (idx < sub.chunks.size) {
            val stChunk = sub.chunks[idx]
            if (stChunk !is IRCodeChunk || stChunk.instructions.size != 1) { idx++; continue }
            val stInstr = stChunk.instructions[0]
            if (stInstr.opcode != Opcode.STOREM || stInstr.srcA == null) { idx++; continue }
            val loopvarSymbol = stInstr.memory?.symbolName
            if (loopvarSymbol == null) { idx++; continue }
            val loopvar = loopvarSymbol
            val loopReg = stInstr.requireSrcA().register
            val loopType = stInstr.type ?: IRDataType.WORD
            // next chunk should be the loop body start with a label
            if (idx + 1 >= sub.chunks.size) { idx++; continue }
            val bodyStart = sub.chunks[idx + 1]
            if (bodyStart.label == null) { idx++; continue }
            val loopLabel = bodyStart.label!!

            // find inc chunk: contains INCM loopvar and JUMP loopLabel (possibly same chunk or inc then jump)
            var incIdx = -1
            for (k in idx + 1 until sub.chunks.size) {
                val c = sub.chunks[k]
                if (c !is IRCodeChunk) continue
                val hasInc = c.instructions.any { it.opcode == Opcode.INCM && it.memory?.symbolName == loopvar }
                if (!hasInc) continue
                val hasJumpInSame = c.instructions.any { it.opcode == Opcode.JUMP && it.labelTarget == loopLabel }
                if (hasJumpInSame) { incIdx = k; break }
                if (k + 1 < sub.chunks.size) {
                    val nxt = sub.chunks[k + 1]
                    if (nxt is IRCodeChunk && nxt.instructions.size == 1 && nxt.instructions[0].opcode == Opcode.JUMP && nxt.instructions[0].labelTarget == loopLabel) {
                        incIdx = k; break
                    }
                }
            }
            if (incIdx == -1) { idx++; continue }

            // body range is idx+1 until incIdx exclusive
            var bodyWrites = false
            for (b in idx + 1 until incIdx) {
                val ch = sub.chunks[b]
                if (ch !is IRCodeChunk) continue
                for (ins in ch.instructions) {
                    // any store/inc/dec that writes to loopvar memory
                    if (ins.memory?.symbolName == loopvar && ins.opcode in setOf(Opcode.STOREM, Opcode.STOREIM, Opcode.STOREZM, Opcode.STOREX, Opcode.STOREZX, Opcode.INCM, Opcode.DECM, Opcode.ADDIM, Opcode.SUBIM)) {
                        // exclude the initial STORM itself (not in body) and the inc chunk (not in body)
                        bodyWrites = true; break
                    }
                    // also check generic memory write via address? Loopvar is always via labelSymbol, so covered
                }
                if (bodyWrites) break
            }
            if (bodyWrites) { idx++; continue }

            // Check live-out: if loopvar is read after the loop, don't optimize (needs spill handling)
            var labelAfter: String? = null
            for (b in idx + 1 until incIdx) {
                val ch = sub.chunks[b] as? IRCodeChunk ?: continue
                for (ins in ch.instructions) {
                    if (ins.opcode == Opcode.BSTEQ && ins.labelTarget != null) {
                        // This is likely the loop's exit branch (LOADM+CMP+BSTEQ pattern)
                        // Verify it follows a CMP that uses loopvar (heuristic: preceding LOADM loopvar)
                        labelAfter = ins.labelTarget
                        break
                    }
                }
                if (labelAfter != null) break
            }
            var exitIdx = -1
            if (labelAfter != null) {
                for (k in incIdx + 1 until sub.chunks.size) {
                    if (sub.chunks[k].label == labelAfter) { exitIdx = k; break }
                }
            }
            var liveOut = false
            if (exitIdx != -1) {
                for (k in exitIdx until sub.chunks.size) {
                    val ch = sub.chunks[k] as? IRCodeChunk ?: continue
                    for (ins in ch.instructions) {
                        if (ins.opcode == Opcode.LOADM && ins.memory?.symbolName == loopvar) { liveOut = true; break }
                    }
                    if (liveOut) break
                }
            }
            if (liveOut) { idx++; continue }

            // Found a candidate loop; perform rewrite
            // 1. Remove STORM chunk
            sub.chunks.removeAt(idx)
            // incIdx shifts by -1 after removal
            incIdx--
            // 2. In body chunks, replace LOADM loopvar with LOADR loopReg (keep type)
            for (b in idx until incIdx) {
                val ch = sub.chunks[b] as? IRCodeChunk ?: continue
                for (i in ch.instructions.indices) {
                    val ins = ch.instructions[i]
                    if (ins.opcode == Opcode.LOADM && ins.memory?.symbolName == loopvar) {
                        val dest = ins.requireDest().register
                        if (dest == loopReg) {
                            // Already holds the correct value; remove the reload
                            ch.instructions[i] = IRInstructions.simple(Opcode.NOP)
                        } else {
                            val tp = ins.type ?: loopType
                            ch.instructions[i] = IRInstructions.move(tp, dest.num, loopReg.num)
                        }
                    }
                }
            }
            // 3. In inc chunk, replace INCM loopvar with INC loopReg
            val incChunk = sub.chunks[incIdx] as IRCodeChunk
            for (i in incChunk.instructions.indices) {
                val ins = incChunk.instructions[i]
                if (ins.opcode == Opcode.INCM && ins.memory?.symbolName == loopvar) {
                    incChunk.instructions[i] = IRInstructions.unary(Opcode.INC, loopType, loopReg.num)
                    break
                }
            }
            changed = true
            // Do not increment idx, re-evaluate at same position (now next loop)
            continue
        }
        if (changed) {
            // Need to re-link chunks after structural changes (labels still valid)
//            sub.chunks.forEachIndexed { i, ch ->
//                // ensure next pointers will be recomputed via linkChunks later; for now keep as is
//            }
        }
        return changed
    }
}
