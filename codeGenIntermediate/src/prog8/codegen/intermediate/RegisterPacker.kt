package prog8.codegen.intermediate

import prog8.intermediate.*

/*
 * ============================================================================
 * PROTOTYPE - NOT FOR PRODUCTION USE
 * ============================================================================
 *
 * This is a PROTOTYPE memory-slot packer and the intended reusable foundation
 * for a future hardware-register allocator targeting the m68k backend. It is
 * NOT a complete or production-ready register allocator in its current form.
 * It is DISABLED in production: its only call site in IRCodeGen.generate() is
 * commented out, and it currently runs only under TestRegisterPacker.
 *
 * What this prototype demonstrates:
 *   - A working graph-coloring register allocator skeleton: CFG construction,
 *     intraprocedural liveness analysis (gen/kill fixed-point), live interval
 *     derivation, conflict-graph construction, greedy coloring, and IR rewrite.
 *   - Coalescing of virtual registers into shared memory slots in the flat
 *     `p8_regfile` BSS block (vregs with non-overlapping live ranges share a
 *     slot).
 *
 * What this prototype DOES NOT do (and why it is not production-ready):
 *   - No hardware-register allocation: slots are memory addresses in a flat
 *     BSS regfile, not physical D/A/FP registers.
 *   - No CALL clobbering: a callee packed into the same slot as a caller's
 *     live value silently overwrites it (see bug 3 below).
 *   - No register classes: no distinction between DATA, ADDRESS, and FPU
 *     registers (required by the m68k backend).
 *   - No spilling, prologue/epilogue emission, or CALL-aware interference.
 *   - Known-unsound liveness for complex control flow (nested loops,
 *     conditionals, early returns) - see bug 2 below.
 *
 * Intended reuse path (per ideas/m68k-register-allocation.md):
 *   - Stage 1: Make the m68k backend's instruction selection location-agnostic
 *     (operand() indirection returning "d3" if allocated, "p8_regfile+N" if
 *     spilled) - prerequisite bulk work, no behavior change.
 *   - Stage 2: Replace this packer's interference model with a correct one
 *     (CALL-aware, class-constrained) and point the coloring step at real
 *     D2-D6 / A2-A4 / FP2-FP7 registers. Six open design decisions remain
 *     in Stage 2 (see design doc section 7.1).
 *   - Stage 3: Delete subsumed peephole optimizations, update test assertions.
 */

/*
 * Known bugs / unsoundness (carried over from the removed register-packing.md):
 *
 * 1. Disjoint-interval value clobbering (observed on cx16 Fibonacci).
 *    A register with multiple DISJOINT live ranges (e.g. a loop counter written
 *    at loop entry and read at loop exit, with no uses in between) was split
 *    into separate intervals; the packer allowed another register to share the
 *    same slot during the gap, overwriting the value before its next read.
 *    Fix applied (see packSubroutine interval merging): always merge ALL
 *    intervals of the same register into one contiguous range, regardless of
 *    overlap, so the value persists across gaps.
 *
 * 2. Complex control flow (observed on m68k TextElite).
 *    The iterative gen/kill fixed-point liveness (computeLiveness) produced
 *    incorrect live ranges for subroutines with complex CFGs (nested
 *    conditionals, loops, early returns). Two registers with genuinely
 *    overlapping live ranges were assigned the same slot, so one clobbered the
 *    other at runtime (symptom: infinite loop printing spaces — galaxy map
 *    data read from the wrong memory location). Root cause: the dataflow does
 *    not converge to the true meet-over-all-paths solution for all loop /
 *    conditional structures. The liveness MUST be validated against nested
 *    loops, conditionals, early returns, and switch-like dispatch before reuse.
 *
 * 3. Cross-subroutine slot collision (fundamental design issue; see header
 *    comment above). Per-subroutine packing into ONE flat shared regfile is
 *    unsound: a callee packed into the same slot as a caller's live value
 *    clobbers it. The packer does not model CALL clobbering. Soundness would
 *    require the full-program call tree (call-graph-aware allocation, depth
 *    ranges, or save/restore around calls) — which is exactly what the m68k
 *    allocator with a calling convention avoids by allocating per-subroutine.
 *
 * 4. Interval-merge type loss / silent skips. When merging a register's
 *    intervals, the merged interval keeps only the FIRST interval's type; if a
 *    later interval has an incompatible type, the register is dropped via
 *    skipRegs (and its original number is preserved via the startSlot =
 *    maxReg + 1 invariant). When reusing this code, merged intervals must carry
 *    the WIDEST type, not just the first, to avoid under-sized slots.
 */

object RegisterPacker {

    data class Interval(val register: VirtualRegister, val start: Int, val end: Int, val type: IRDataType)

    fun pack(irProg: IRProgram) {
        val allRegTypes = mutableMapOf<VirtualRegister, IRDataType>()
        irProg.forEachInstruction { instr ->
            for (access in instr.registerAccesses)
                allRegTypes.putIfAbsent(access.register, access.type)
        }
        val beforeCount = allRegTypes.size

        // Start packing slots after the highest original register number to avoid
        // collisions between packed slot numbers and original (non-packed) register numbers.
        val maxReg = allRegTypes.keys.maxOfOrNull { it.num } ?: 0
        val startSlot = maxReg + 1
        val globalSlotTypes = mutableMapOf<VirtualRegister, IRDataType>()
        irProg.foreachSub { sub -> packSubroutine(irProg, sub, allRegTypes, globalSlotTypes, startSlot) }

        val afterTypes = rebuildTypeMap(irProg)
        val afterCount = afterTypes.size
        if (!irProg.options.quiet)
            println("Register packing: $beforeCount -> $afterCount registers")
    }

    // Rebuild the register type map after packing, using the same type determination as the packer.
    // This avoids the strict per-instruction validation in usedRegisters() that POINTER↔LONG↔WORD
    // cross-type packing can trigger.
    fun rebuildTypeMap(irProg: IRProgram): Map<VirtualRegister, IRDataType> {
        val newTypes = mutableMapOf<VirtualRegister, IRDataType>()
        irProg.forEachInstruction { instr ->
            for (access in instr.registerAccesses)
                newTypes.putIfAbsent(access.register, access.type)
        }
        return newTypes
    }

    private fun packSubroutine(irProg: IRProgram, sub: IRSubroutine, allRegTypes: Map<VirtualRegister, IRDataType>, globalSlotTypes: MutableMap<VirtualRegister, IRDataType>, startSlot: Int) {
        if (sub.chunks.isEmpty())
            return

        val successors = buildCFG(sub, irProg)
        val (_, liveOut) = computeLiveness(sub, successors)

        val codeChunks = sub.chunks.filterIsInstance<IRCodeChunk>()
        if (codeChunks.isEmpty())
            return

        val chunkRanges = mutableMapOf<IRCodeChunk, IntRange>()
        var globalIdx = 0
        for (chunk in codeChunks) {
            val start = globalIdx
            val end = globalIdx + chunk.instructions.size - 1
            chunkRanges[chunk] = start..end
            globalIdx += chunk.instructions.size
        }

        val registerIntervals = mutableMapOf<VirtualRegister, MutableList<Interval>>()
        val registerTypes = mutableMapOf<VirtualRegister, IRDataType>()

        for (chunk in codeChunks) {
            val range = chunkRanges[chunk]!!
            val chunkStart = range.first
            if (chunk.instructions.isEmpty())
                continue

            val liveSet = liveOut[chunk]?.toMutableSet() ?: mutableSetOf()
            val lastUse = mutableMapOf<VirtualRegister, Int>()

            // Scan backward through instructions
            for (i in chunk.instructions.indices.reversed()) {
                val instr = chunk.instructions[i]
                val globalI = chunkStart + i
                val accesses = instr.registerAccesses

                // Process READ (USE / USE_DEF) first so types are set before WRITE processing
                for (access in accesses) {
                    if (access.direction != OperandDirection.DEF) {
                        val r = access.register
                        registerTypes.putIfAbsent(r, access.type)
                        if (r !in liveSet) {
                            lastUse[r] = globalI
                            liveSet.add(r)
                        }
                    }
                }

                // Process WRITTEN (DEF / USE_DEF) registers: end their live range (start of interval going forward)
                for (access in accesses) {
                    if (access.direction != OperandDirection.USE) {
                        val r = access.register
                        if (r in liveSet) {
                            val end = lastUse.getOrElse(r) { globalI }
                            registerIntervals.getOrPut(r) { mutableListOf() }
                                .add(Interval(r, globalI, end, access.type))
                            liveSet.remove(r)
                            lastUse.remove(r)
                        }
                    }
                }
            }

            // Registers still live at start of chunk are live-in
            for (r in liveSet) {
                val end = lastUse.getOrElse(r) { chunkStart }
                registerIntervals.getOrPut(r) { mutableListOf() }
                    .add(Interval(r, chunkStart, end, registerTypes.getOrElse(r) { IRDataType.BYTE }))
            }
        }

        if (registerIntervals.isEmpty())
            return

        // Merge adjacent/overlapping intervals for each register.
        // Also merge non-overlapping intervals of the same register into a single
        // contiguous range, because the value in the slot must persist across the
        // gap to the next use (otherwise another register packed to the same slot
        // would clobber it between intervals).
        val mergedIntervals = mutableListOf<Interval>()
        val skipRegs = mutableSetOf<VirtualRegister>()
        for ((reg, intervals) in registerIntervals) {
            val sorted = intervals.sortedBy { it.start }
            var current = sorted.first()
            for (next in sorted.drop(1)) {
                if (current.type != next.type && !typesCompatible(current.type, next.type)) {
                    skipRegs.add(reg)
                }
                // Always merge: same register's intervals are merged into a single
                // contiguous range to preserve the value across gaps.
                current = Interval(reg, current.start, maxOf(current.end, next.end), current.type)
            }
            mergedIntervals.add(current)
        }
        if (skipRegs.isNotEmpty()) {
            mergedIntervals.removeAll { it.register in skipRegs }
        }

        val conflictGraph = buildConflictGraph(sub)
        val packing = greedyColor(mergedIntervals, conflictGraph, allRegTypes, globalSlotTypes, startSlot)

        if (packing.isNotEmpty())
            rewrite(sub, packing)
    }

    private fun buildConflictGraph(sub: IRSubroutine): Map<VirtualRegister, Set<VirtualRegister>> {
        val conflicts = mutableMapOf<VirtualRegister, MutableSet<VirtualRegister>>()
        for (chunk in sub.chunks.filterIsInstance<IRCodeChunk>()) {
            val typeRegs = mutableMapOf<IRDataType, MutableSet<VirtualRegister>>()
            for (instr in chunk.instructions) {
                val accesses = instr.registerAccesses
                for (access in accesses)
                    typeRegs.getOrPut(access.type) { mutableSetOf() }.add(access.register)

                // Registers accessed together within a single instruction must not share a slot
                // (generalizes the old reg1/reg2-only conflict rule to every register access of
                // the instruction, now that they are all enumerable via registerAccesses).
                val regsInInstr = accesses.map { it.register }.distinct()
                for (i in regsInInstr.indices) {
                    for (j in i + 1 until regsInInstr.size) {
                        val r1 = regsInInstr[i]
                        val r2 = regsInInstr[j]
                        conflicts.getOrPut(r1) { mutableSetOf() }.add(r2)
                        conflicts.getOrPut(r2) { mutableSetOf() }.add(r1)
                    }
                }
            }
            // Add conflicts between registers with incompatible types in the same chunk
            val typeList = typeRegs.entries.toList()
            for (i in typeList.indices) {
                for (j in i + 1 until typeList.size) {
                    val (t1, regs1) = typeList[i]
                    val (t2, regs2) = typeList[j]
                    if (t1 != t2 && !typesCompatible(t1, t2)) {
                        for (r1 in regs1) {
                            for (r2 in regs2) {
                                if (r1 != r2) {
                                    conflicts.getOrPut(r1) { mutableSetOf() }.add(r2)
                                    conflicts.getOrPut(r2) { mutableSetOf() }.add(r1)
                                }
                            }
                        }
                    }
                }
            }
        }
        return conflicts
    }

    private fun buildCFG(sub: IRSubroutine, irProg: IRProgram): Map<IRCodeChunkBase, List<IRCodeChunkBase>> {
        val successors = mutableMapOf<IRCodeChunkBase, MutableList<IRCodeChunkBase>>()

        val conditionals = setOf(
            Opcode.BSTCC, Opcode.BSTCS, Opcode.BSTEQ, Opcode.BSTNE,
            Opcode.BSTNEG, Opcode.BSTPOS, Opcode.BSTVC, Opcode.BSTVS,
            Opcode.BGTR, Opcode.BGT, Opcode.BLT,
            Opcode.BGTSR, Opcode.BGTS, Opcode.BLTS,
            Opcode.BGER, Opcode.BGE, Opcode.BLE,
            Opcode.BGESR, Opcode.BGES, Opcode.BLES
        )

        for (chunk in sub.chunks) {
            val succ = mutableListOf<IRCodeChunkBase>()

            if (chunk is IRCodeChunk) {
                val lastInstr = chunk.instructions.lastOrNull()
                val endsWithUnconditional = lastInstr != null && lastInstr.opcode in OpcodesThatBranchUnconditionally
                val endsWithConditional = lastInstr != null && lastInstr.opcode in conditionals

                if (!endsWithUnconditional) {
                    chunk.next?.let { succ.add(it) }
                }
                if (endsWithConditional) {
                    lastInstr.codeTarget?.let { irProg.resolveCodeTarget(it) }?.let { target ->
                        if (!succ.contains(target))
                            succ.add(target)
                    }
                } else if (lastInstr?.opcode == Opcode.JUMP) {
                    succ.clear()
                    lastInstr.codeTarget?.let { irProg.resolveCodeTarget(it) }?.let { succ.add(it) }
                }
            } else {
                chunk.next?.let { succ.add(it) }
            }

            successors[chunk] = succ
        }

        return successors
    }

    private fun computeLiveness(
        sub: IRSubroutine,
        successors: Map<IRCodeChunkBase, List<IRCodeChunkBase>>
    ): Pair<Map<IRCodeChunk, Set<VirtualRegister>>, Map<IRCodeChunk, Set<VirtualRegister>>> {

        val liveIn = mutableMapOf<IRCodeChunk, MutableSet<VirtualRegister>>()
        val liveOut = mutableMapOf<IRCodeChunk, MutableSet<VirtualRegister>>()
        val gen = mutableMapOf<IRCodeChunk, MutableSet<VirtualRegister>>()
        val kill = mutableMapOf<IRCodeChunk, MutableSet<VirtualRegister>>()

        for (chunk in sub.chunks) {
            if (chunk !is IRCodeChunk)
                continue

            val genSet = mutableSetOf<VirtualRegister>()
            val killSet = mutableSetOf<VirtualRegister>()

            for (instr in chunk.instructions) {
                val written = instr.definitions
                val read = instr.uses
                for (r in read) {
                    if (r !in killSet)
                        genSet.add(r)
                }
                for (r in written) {
                    killSet.add(r)
                }
            }

            gen[chunk] = genSet
            kill[chunk] = killSet
            liveIn[chunk] = mutableSetOf()
            liveOut[chunk] = mutableSetOf()
        }

        var changed = true

        while (changed) {
            changed = false

            for (chunk in sub.chunks.reversed()) {
                if (chunk !is IRCodeChunk)
                    continue

                val newLiveOut = mutableSetOf<VirtualRegister>()
                for (succ in successors[chunk].orEmpty()) {
                    if (succ is IRCodeChunk)
                        newLiveOut.addAll(liveIn[succ].orEmpty())
                }

                if (newLiveOut != liveOut[chunk]) {
                    liveOut[chunk] = newLiveOut
                    changed = true
                }

                val newLiveIn = mutableSetOf<VirtualRegister>()
                newLiveIn.addAll(gen[chunk].orEmpty())
                newLiveIn.addAll(liveOut[chunk].orEmpty() - kill[chunk].orEmpty())

                if (newLiveIn != liveIn[chunk]) {
                    liveIn[chunk] = newLiveIn
                    changed = true
                }
            }
        }

        return liveIn to liveOut
    }

    private fun greedyColor(
        intervals: List<Interval>,
        conflictGraph: Map<VirtualRegister, Set<VirtualRegister>>,
        allRegTypes: Map<VirtualRegister, IRDataType>,
        slotTypes: MutableMap<VirtualRegister, IRDataType>,
        startSlot: Int
    ): Map<VirtualRegister, VirtualRegister> {
        if (intervals.isEmpty())
            return emptyMap()

        val packing = mutableMapOf<VirtualRegister, VirtualRegister>()
        // Reserve slots for non-packed registers
        val packedRegs = intervals.map { it.register }.toSet()
        for ((reg, type) in allRegTypes) {
            if (reg !in packedRegs)
                slotTypes.putIfAbsent(reg, type)
        }

        val sorted = intervals.sortedWith(compareBy<Interval> { it.type.ordinal }.thenBy { it.start })
        val activeSlots = mutableMapOf<VirtualRegister, Pair<VirtualRegister, Int>>()

        for (interval in sorted) {
            activeSlots.entries.removeAll { it.value.second < interval.start }

            var slotNum = startSlot
            var slot: VirtualRegister
            while (true) {
                // a packed slot stays in the same register file (int/float) as the register it replaces
                slot = if (interval.register is VirtualRegister.FloatReg) VirtualRegister.float(slotNum) else VirtualRegister.int(slotNum)
                if (slot !in activeSlots) {
                    val existingType = slotTypes[slot]
                    if (existingType == null || existingType == interval.type || typesCompatible(existingType, interval.type)) {
                        if (!conflictsWithRegister(slot, interval.register, conflictGraph, packing))
                            break
                    }
                }
                slotNum++
            }

            packing[interval.register] = slot
            slotTypes.putIfAbsent(slot, interval.type)  // keep the first-assigned type to prevent type narrowing via typesCompatible chain
            activeSlots[slot] = Pair(interval.register, interval.end)
        }
        return packing
    }

    private fun typesCompatible(t1: IRDataType, t2: IRDataType): Boolean {
        if (t1 == t2) return true
        return (t1 == IRDataType.POINTER && t2 in setOf(IRDataType.WORD, IRDataType.LONG)) ||
                (t2 == IRDataType.POINTER && t1 in setOf(IRDataType.WORD, IRDataType.LONG))
    }

    private fun conflictsWithRegister(
        slot: VirtualRegister,
        register: VirtualRegister,
        conflictGraph: Map<VirtualRegister, Set<VirtualRegister>>,
        packing: Map<VirtualRegister, VirtualRegister>
    ): Boolean {
        val conflictingRegs = conflictGraph[register] ?: return false
        for (cr in conflictingRegs) {
            val crSlot = packing[cr]
            if (crSlot != null && crSlot == slot)
                return true
            if (cr !in packing && cr == slot)
                return true
        }
        return false
    }

    private fun rewrite(sub: IRSubroutine, packing: Map<VirtualRegister, VirtualRegister>) {
        for (chunk in sub.chunks) {
            if (chunk !is IRCodeChunk)
                continue

            for (i in chunk.instructions.indices) {
                val instr = chunk.instructions[i]
                val newInstr = instr.mapRegisters { vr -> packing[vr] ?: vr }
                if (newInstr != instr)
                    chunk.instructions[i] = newInstr
            }
        }
    }
}
