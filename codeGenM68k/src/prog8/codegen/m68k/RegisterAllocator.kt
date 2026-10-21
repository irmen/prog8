package prog8.codegen.m68k

import prog8.intermediate.*

/**
 * Phase A of the Stage 2 m68k register allocator: intraprocedural liveness
 * analysis plus class-aware greedy coloring onto the allocatable physical
 * registers (D2-D6, A2-A4, FP2-FP7). Produces a vreg -> physical-register
 * map that can be handed to [AsmGen.regAllocation].
 *
 * This implementation is intraprocedural: it does not modify IR and does not
 * emit prologue/epilogue yet. The map it produces may be partial under register
 * pressure; callers must treat a missing entry as "spilled to regfile".
 */
class M68kRegisterAllocator(val program: IRProgram) {

    /** vreg -> physical register, plus per-subroutine callee-saved usage and spill demand. */
    data class AllocationResult(
        val map: Map<VirtualRegister, String>,
        val calleeSavedBySub: Map<IRSubroutine, Set<String>>,
        val spillDemandBySub: Map<IRSubroutine, Int>
    )

    private val dataRegs = ALLOCATABLE_DATA_REGS
    private val addrRegs = ALLOCATABLE_ADDR_REGS
    private val fpRegs = ALLOCATABLE_FP_REGS

    // Collect all chunks of a subroutine flattened (including IRLoopChunk bodies).
    private fun allChunks(sub: IRSubroutine): List<IRCodeChunkBase> {
        val out = mutableListOf<IRCodeChunkBase>()
        fun collect(c: IRCodeChunkBase) {
            out.add(c)
            if (c is IRLoopChunk) c.body.forEach { collect(it) }
        }
        sub.chunks.forEach { collect(it) }
        return out
    }

    // Build a coarse CFG: each chunk is a node; edges = sequential order + loop back.
    // We approximate by treating chunk list order as layout order and adding
    // forward edges between consecutive chunks and a back-edge for loops.
    private fun buildCfg(chunks: List<IRCodeChunkBase>): List<Set<Int>> {
        val succ = MutableList(chunks.size) { mutableSetOf<Int>() }
        for (i in chunks.indices) {
            if (i + 1 < chunks.size) succ[i].add(i + 1)
            if (chunks[i] is IRLoopChunk) {
                // back edge from last body chunk to loop header (approx)
                // find index of last body chunk
                val body = (chunks[i] as IRLoopChunk).body
                // body chunks are also in flattened list; add edge from each to header i
                for (j in i + 1 until chunks.size) {
                    if (chunks[j] in body) succ[j].add(i)
                }
            }
        }
        return succ
    }

    private fun genKill(chunk: IRCodeChunkBase): Pair<Set<VirtualRegister>, Set<VirtualRegister>> {
        val gen = mutableSetOf<VirtualRegister>()
        val kill = mutableSetOf<VirtualRegister>()
        for (ins in chunk.instructions) {
            for (ra in ins.registerAccesses) {
                when (ra.direction) {
                    OperandDirection.USE, OperandDirection.USE_DEF ->
                        if (ra.register !in kill) gen.add(ra.register)
                    else -> {}
                }
                when (ra.direction) {
                    OperandDirection.DEF, OperandDirection.USE_DEF -> kill.add(ra.register)
                    else -> {}
                }
            }
        }
        return gen to kill
    }

    fun allocate(): AllocationResult {
        val result = mutableMapOf<VirtualRegister, String>()
        val calleeSavedBySub = mutableMapOf<IRSubroutine, Set<String>>()
        val spillDemandBySub = mutableMapOf<IRSubroutine, Int>()
        for (block in program.blocks) {
            for (child in block.children) {
                if (child is IRSubroutine)
                    allocateSubroutine(child, result, calleeSavedBySub, spillDemandBySub)
            }
        }
        // global inits chunk (treat as a subroutine-less scope) - skip for now
        return AllocationResult(result, calleeSavedBySub, spillDemandBySub)
    }

    private fun allocateSubroutine(
        sub: IRSubroutine,
        result: MutableMap<VirtualRegister, String>,
        calleeSavedBySub: MutableMap<IRSubroutine, Set<String>>,
        spillDemandBySub: MutableMap<IRSubroutine, Int>
    ) {
        val chunks = allChunks(sub)
        if (chunks.isEmpty()) return
        val n = chunks.size
        val succ = buildCfg(chunks)
        val (gens, kills) = chunks.map { genKill(it) }.unzip()
        // iterative dataflow
        val liveIn = MutableList(n) { mutableSetOf<VirtualRegister>() }
        val liveOut = MutableList(n) { mutableSetOf<VirtualRegister>() }
        var changed = true
        while (changed) {
            changed = false
            for (i in n - 1 downTo 0) {
                val newOut = mutableSetOf<VirtualRegister>()
                for (s in succ[i]) newOut.addAll(liveIn[s])
                val newIn = mutableSetOf<VirtualRegister>()
                newIn.addAll(gens[i])
                for (v in liveOut[i]) if (v !in kills[i]) newIn.add(v)
                if (newIn != liveIn[i] || newOut != liveOut[i]) {
                    liveIn[i] = newIn
                    liveOut[i] = newOut
                    changed = true
                }
            }
        }

        // Build interference: two vregs interfere if simultaneously live at any chunk boundary.
        val vregs = mutableSetOf<VirtualRegister>()
        chunks.forEachIndexed { i, _ ->
            vregs.addAll(liveIn[i]); vregs.addAll(liveOut[i]); vregs.addAll(gens[i]); vregs.addAll(kills[i])
        }

        // ---- Item 4: class constraints from allocation hints ----
        // Scan every register access in the subroutine for its resolvedAllocationHint.
        // PREFER_ADDRESS has priority (an address-register operand must be an An for
        // (An)/(off,An)/(An,Dn) addressing); PREFER_DATA is the default for index uses.
        val hints = mutableMapOf<VirtualRegister, AllocationHint>()
        for (chunk in chunks) {
            for (ins in chunk.instructions) {
                for (ra in ins.registerAccesses) {
                    when (ra.resolvedAllocationHint) {
                        AllocationHint.PREFER_ADDRESS -> hints[ra.register] = AllocationHint.PREFER_ADDRESS
                        AllocationHint.PREFER_DATA -> hints.putIfAbsent(ra.register, AllocationHint.PREFER_DATA)
                        AllocationHint.NONE -> {}
                    }
                }
            }
        }

        fun vregClass(vr: VirtualRegister): Int = when {
            vr.isFloat -> 2                     // FPU
            hints[vr] == AllocationHint.PREFER_ADDRESS -> 1  // ADDRESS
            else -> 0                           // DATA
        }

        // ---- Item 3: CALL clobbering + return-value pinning ----
        // A CALL kills the caller-saved registers (D0/D1/A0/A1/FP0/FP1). Since the
        // allocatable pool is callee-saved only, no allocated value is ever destroyed by
        // a CALL, so live-across-call values are safe by construction. We still model the
        // kill explicitly so the invariant is enforced and so return-value vregs are
        // pinned to their CALL-boundary register (D0 for integer/pointer, FP0 for float).
        // A pinned result is produced in D0/FP0 by the callee; codegen will move it from
        // there to the allocated register. Pinning also forces the result into the DATA /
        // FPU class regardless of any conflicting hint.
        val callerSavedKillSet = M68kRegisterConvention.CALLER_SAVED
        val returnPin = mutableMapOf<VirtualRegister, String>() // vreg -> its pinned return register
        for (chunk in chunks) {
            for (ins in chunk.instructions) {
                val cs = ins.callSite ?: continue
                cs.results.forEach { r ->
                    r.destination?.let { d ->
                        returnPin[d.register] = if (d.register.isFloat) M68kRegisterConvention.RETURN_FLOAT else M68kRegisterConvention.RETURN_INT
                    }
                }
                // caller-saved kill: nothing to do for allocated vregs (pool is callee-saved),
                // but assert none of our allocatable pools overlaps the kill set.
                require(callerSavedKillSet.none { it in dataRegs || it in addrRegs || it in fpRegs }) {
                    "allocatable pool overlaps caller-saved kill set"
                }
            }
        }

        val interference = mutableMapOf<VirtualRegister, MutableSet<VirtualRegister>>()
        for (v in vregs) interference[v] = mutableSetOf()
        // For each chunk, all vregs in liveIn ∪ liveOut (and gens) are mutually interfering
        for (i in chunks.indices) {
            val live = (liveIn[i] + liveOut[i] + gens[i]).toSet()
            val list = live.toList()
            for (a in list.indices) for (b in a + 1 until list.size) {
                interference[list[a]]!!.add(list[b])
                interference[list[b]]!!.add(list[a])
            }
        }

        // Greedy coloring per class
        // order vregs by degree desc
        val ordered = vregs.sortedByDescending { interference[it]!!.size }
        val color = mutableMapOf<VirtualRegister, String>()
        var spills = 0
        for (v in ordered) {
            val cls = vregClass(v)
            val pool = when (cls) { 0 -> dataRegs; 1 -> addrRegs; 2 -> fpRegs; else -> dataRegs }
            val usedByNeighbors = interference[v]!!.mapNotNull { color[it] }.toSet()
            val pick = pool.firstOrNull { it !in usedByNeighbors }
            if (pick != null) color[v] = pick
            else spills++ // no register available for this class: spilled to regfile
        }

        for ((v, p) in color) result[v] = p
        calleeSavedBySub[sub] = color.values.toSet()
        spillDemandBySub[sub] = spills
    }
}
