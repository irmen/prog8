package prog8.codegen.m68k

import prog8.intermediate.*

/**
 * m68k register allocator: intraprocedural liveness
 * analysis plus class-aware greedy coloring onto the allocatable physical
 * registers (D2-D6, A2-A4, FP2-FP7). Produces a per-subroutine vreg ->
 * physical-register map that can be handed to [AsmGen.regAllocationBySub]
 * (AsmGen swaps the current subroutine's entries into [AsmGen.regAllocation]).
 *
 * It does not modify IR. The map may be partial under register pressure;
 * callers must treat a missing entry as memory-resident (frame slot or regfile).
 */
class M68kRegisterAllocator(val program: IRProgram) {

    /** Physical registers a fixed-register call boundary reads / defines / destroys. */
    data class CallSiteRegisterEffects(
        val reads: Set<String>,
        val defined: Set<String>,
        val destroys: Set<String>
    )

    /** Per-subroutine allocation quality metrics. */
    data class SubAllocStats(
        /** total register-operand references (reads + writes; USE_DEF counts as both) */
        val totalRefs: Int,
        /** references whose vreg was colored to a physical register */
        val allocatedRefs: Int,
        val spillCount: Int,
        /** max simultaneously-live vregs per class, measured at instruction granularity */
        val maxLiveData: Int,
        val maxLiveAddress: Int,
        val maxLiveFloat: Int
    ) {
        /** fraction of operand references that resolve to a CPU register via operand() */
        val residency: Double get() = if (totalRefs == 0) 1.0 else allocatedRefs.toDouble() / totalRefs
    }

    /** vreg -> physical register per subroutine, plus per-subroutine callee-saved usage and spill demand. */
    data class AllocationResult(
        // Authoritative mapping, keyed per subroutine: vreg numbers may be reused
        // across subroutines, so no program-wide flat map is provided.
        val mapBySub: Map<IRSubroutine, Map<VirtualRegister, String>>,
        val calleeSavedBySub: Map<IRSubroutine, Set<String>>,
        val spillDemandBySub: Map<IRSubroutine, Int>,
        val spilledVregsBySub: Map<IRSubroutine, List<VirtualRegister>> = emptyMap(),
        // Per-call-site fixed-register boundary metadata, keyed by subroutine and by the
        // ordinal of the call instruction in flattened chunk order.
        // Ordinary Prog8-subroutine calls use the uniform caller-saved kill set and get no entry.
        val callSiteEffects: Map<IRSubroutine, Map<Int, CallSiteRegisterEffects>> = emptyMap(),
        val statsBySub: Map<IRSubroutine, SubAllocStats> = emptyMap()
    )

    private val dataRegs = ALLOCATABLE_DATA_REGS
    private val addrRegs = ALLOCATABLE_ADDR_REGS
    private val fpRegs = ALLOCATABLE_FP_REGS

    private val allocatablePool = (dataRegs + addrRegs + fpRegs).toSet()

    private val ASM_WORD = Regex("[A-Za-z0-9_.]+")
    private val ASM_SIZE_SUFFIXES = setOf('b', 'w', 'l', 's')

    // IRAsmSubroutines by label, for fixed-register call boundary lookup.
    private val asmSubByLabel: Map<String, IRAsmSubroutine> by lazy {
        program.allAsmSubs().associateBy { it.label }
    }

    // IRSubroutines by label, for ordinary-call target lookup.
    private val subByLabel: Map<String, IRSubroutine> by lazy {
        program.allSubs().associateBy { it.label }
    }

    // The label of the ordinary Prog8 subroutine a call targets, or null for every
    // boundary that is not an ordinary direct call (asmsub, banked, absolute,
    // indirect, unknown).
    private fun ordinaryCallLabel(cs: CallSite): String? {
        val target = cs.target as? CallTarget.Direct ?: return null
        val label = (target.reference as? CodeReference.Label)?.name ?: return null
        if (label !in subByLabel || label in asmSubByLabel) return null
        return label
    }

    // Subroutines that can be entered other than through a known ordinary direct call.
    // These keep their full callee-saved set: the caller's demand is not observable.
    private fun externalEntryLabels(): Set<String> {
        val subLabels = subByLabel.keys
        val subSymbols = subLabels.associateWith { setOf(it, M68kRegisterConvention.asmSymbolName(it)) }
        val external = mutableSetOf<String>()
        // Fixed-address blocks may be entered from outside the compiled program.
        for (block in program.blocks) {
            if (block.options.address != null)
                block.children.filterIsInstance<IRSubroutine>().forEach { external.add(it.label) }
        }
        program.forEachChunk { chunk ->
            if (chunk is IRInlineAsmChunk) {
                // raw asm can call or take the address of a subroutine symbol
                val words = asmSymbolWords(chunk.assembly)
                for ((label, symbols) in subSymbols) if (symbols.any { it in words }) external.add(label)
            } else {
                for (ins in chunk.instructions) {
                    // a branch/jump to a subroutine label is an entry that is not a known call
                    (ins.target as? CodeReference.Label)?.name?.let { if (it in subLabels) external.add(it) }
                    (ins.immediate as? ImmediateOperand.SymbolAddress)?.symbol?.let { if (it in subLabels) external.add(it) }
                    val base = when (val mem = ins.memory) {
                        is MemoryReference.Direct -> mem.base
                        is MemoryReference.Indexed -> mem.base
                        is MemoryReference.Indirect, null -> null
                    }
                    (base as? AddressBase.Symbol)?.name?.let { if (it in subLabels) external.add(it) }
                    val cs = ins.callSite
                    if (cs != null) {
                        val ref = cs.codeReference
                        if (ref is CodeReference.Label && ref.name in subLabels && ordinaryCallLabel(cs) == null)
                            external.add(ref.name)
                    }
                }
            }
        }
        // Mirrors AsmOptimizer.optimizeTailCall (bsr/jsr + rts -> bra): such a callee returns on our behalf.
        for (sub in program.allSubs()) {
            val instructions = allChunks(sub).flatMap { it.instructions }
            for (index in 0 until instructions.lastIndex) {
                val call = instructions[index]
                val callSite = call.callSite
                if (callSite == null || instructions[index + 1].opcode != Opcode.RETURN)
                    continue
                ordinaryCallLabel(callSite)?.let { external.add(it) }
            }
        }
        return external
    }

    // Symbol-like words of raw assembly text; a size-suffixed word such as "label.w" also yields "label".
    private fun asmSymbolWords(text: String): Set<String> {
        val words = mutableSetOf<String>()
        for (match in ASM_WORD.findAll(text)) {
            val word = match.value
            words.add(word)
            val dot = word.lastIndexOf('.')
            if (dot > 0 && word.length - dot == 2 && word[dot + 1].lowercaseChar() in ASM_SIZE_SUFFIXES)
                words.add(word.substring(0, dot))
        }
        return words
    }

    // Per-call-site fixed-register boundary metadata, or null for ordinary Prog8
    // subroutine calls (uniform caller-saved kill set, no table entry needed).
    private fun fixedBoundaryEffects(cs: CallSite): CallSiteRegisterEffects? {
        val target = cs.target
        val asmSub = (target as? CallTarget.Direct)?.reference?.let {
            (it as? CodeReference.Label)?.name?.let { name -> asmSubByLabel[name] }
        }
        val reads = cs.arguments
            .mapNotNull { (it.location as? CallLocation.HardwareRegister)?.slot }
            .map { M68kRegisterConvention.fixedRegister(it) }
            .toSet()
        val defined = cs.results
            .mapNotNull { (it.location as? CallLocation.HardwareRegister)?.slot }
            .map { M68kRegisterConvention.fixedRegister(it) }
            .toSet()
        val destroys = buildSet {
            // The AmigaOS library ABI preserves only d2-d5/a2-a5/a6: d6 is volatile, so it must not stay live.
            if (target is CallTarget.AmigaLibrary) {
                add("a6")
                add("d6")
            }
        }
        val isFixed = asmSub != null || target is CallTarget.AmigaLibrary ||
                target is CallTarget.Banked || target is CallTarget.BankedVariable ||
                (target as? CallTarget.Direct)?.reference is CodeReference.Absolute ||
                reads.isNotEmpty() || defined.isNotEmpty()
        if (!isFixed) return null
        return CallSiteRegisterEffects(reads, defined, destroys)
    }

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

    // Chunk CFG: fall-through plus branch-target edges; unknown labels leave the sub, indirect jumps land anywhere.
    private fun buildCfg(chunks: List<IRCodeChunkBase>): List<Set<Int>> {
        val succ = MutableList(chunks.size) { mutableSetOf<Int>() }
        val labelIndex = mutableMapOf<String, Int>()
        for (i in chunks.indices) {
            val label = chunks[i].label
            if (label != null) labelIndex.putIfAbsent(label, i)
        }
        for (i in chunks.indices) {
            var fallsThrough = true
            for (ins in chunks[i].instructions) {
                val target = ins.target as? CodeReference.Label
                when {
                    ins.opcode == Opcode.JUMPI -> {
                        // Computed goto: any labeled chunk may be the landing site; control never continues past it.
                        succ[i].addAll(labelIndex.values)
                        fallsThrough = false
                    }
                    ins.opcode in OpcodesThatBranchUnconditionally -> {
                        // JUMP to a known in-sub label goes only there; elsewhere and RETURN* end the path.
                        val idx = target?.let { labelIndex[it.name] }
                        if (ins.opcode == Opcode.JUMP && idx != null) succ[i].add(idx)
                        fallsThrough = false
                    }
                    ins.opcode in OpcodesThatEndSSAblock -> {
                        // Conditional branch: taken edge plus fall-through; unknown labels leave the sub.
                        target?.let { labelIndex[it.name]?.let { idx -> succ[i].add(idx) } }
                    }
                }
            }
            if (fallsThrough && i + 1 < chunks.size) succ[i].add(i + 1)
            if (chunks[i] is IRLoopChunk) {
                // back edge from last body chunk to loop header (approx)
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
        val mapBySub = mutableMapOf<IRSubroutine, MutableMap<VirtualRegister, String>>()
        val calleeSavedBySub = mutableMapOf<IRSubroutine, Set<String>>()
        val spillDemandBySub = mutableMapOf<IRSubroutine, Int>()
        val spilledVregsBySub = mutableMapOf<IRSubroutine, List<VirtualRegister>>()
        val callSiteEffects = mutableMapOf<IRSubroutine, MutableMap<Int, CallSiteRegisterEffects>>()
        val statsBySub = mutableMapOf<IRSubroutine, SubAllocStats>()
        // Physical registers each ordinary callee must preserve, demanded by its known ordinary callers.
        val demandedByCallee = mutableMapOf<String, MutableSet<String>>()
        for (block in program.blocks) {
            for (child in block.children) {
                if (child is IRSubroutine)
                    allocateSubroutine(child, mapBySub, calleeSavedBySub, spillDemandBySub, spilledVregsBySub,
                        callSiteEffects, statsBySub, demandedByCallee)
            }
        }
        // Second pass: a callee with only known ordinary callers preserves just what they keep live; any other entry keeps its full set.
        val externalEntries = externalEntryLabels()
        for (sub in program.allSubs()) {
            val colored = calleeSavedBySub[sub] ?: continue
            if (sub.label in externalEntries || sub.label !in demandedByCallee) continue
            calleeSavedBySub[sub] = colored.intersect(demandedByCallee[sub.label] ?: emptySet())
        }
        // global inits chunk (treat as a subroutine-less scope) - skip for now
        return AllocationResult(mapBySub, calleeSavedBySub, spillDemandBySub, spilledVregsBySub, callSiteEffects, statsBySub)
    }

    private fun allocateSubroutine(
        sub: IRSubroutine,
        mapBySub: MutableMap<IRSubroutine, MutableMap<VirtualRegister, String>>,
        calleeSavedBySub: MutableMap<IRSubroutine, Set<String>>,
        spillDemandBySub: MutableMap<IRSubroutine, Int>,
        spilledVregsBySub: MutableMap<IRSubroutine, List<VirtualRegister>>,
        callSiteEffects: MutableMap<IRSubroutine, MutableMap<Int, CallSiteRegisterEffects>>,
        statsBySub: MutableMap<IRSubroutine, SubAllocStats>,
        demandedByCallee: MutableMap<String, MutableSet<String>>
    ) {
        val chunks = allChunks(sub)
        if (chunks.isEmpty()) return
        if (sub.noframe) {
            calleeSavedBySub[sub] = emptySet()
            spillDemandBySub[sub] = 0
            spilledVregsBySub[sub] = emptyList()
            return
        }
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

        // ---- class constraints from allocation hints: PREFER_ADDRESS wins, PREFER_DATA is the index default ----
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

        // M1: pointer vregs used only as a-register call arguments get PREFER_ADDRESS hint,
        // so they are allocated to an address register instead of a data register.
        val accessCounts = mutableMapOf<VirtualRegister, Int>()
        for (chunk in chunks)
            for (ins in chunk.instructions)
                for (ra in ins.registerAccesses)
                    accessCounts.merge(ra.register, 1, Int::plus)
        for (chunk in chunks) {
            for (ins in chunk.instructions) {
                val cs = ins.callSite ?: continue
                if (ins.opcode != Opcode.CALL) continue
                for (arg in cs.arguments) {
                    val slot = (arg.location as? CallLocation.HardwareRegister)?.slot ?: continue
                    if (slot.value !in 18..24) continue
                    if (arg.source.type != IRDataType.POINTER) continue
                    val reg = arg.source.register
                    if (accessCounts[reg] != 2) continue
                    hints[reg] = AllocationHint.PREFER_ADDRESS
                }
            }
        }

        // ---- CALL clobbering + return-value pinning: the pool is callee-saved, so only results need pinning ----
        val callerSavedKillSet = M68kRegisterConvention.CALLER_SAVED
        val returnPin = mutableMapOf<VirtualRegister, String>()
        for (chunk in chunks) {
            for (ins in chunk.instructions) {
                val cs = ins.callSite ?: continue
                cs.results.forEach { r ->
                    r.destination?.let { d ->
                        val pinned = when (val loc = r.location) {
                            is CallLocation.HardwareRegister -> M68kRegisterConvention.fixedRegister(loc.slot)
                            is CallLocation.Default ->
                                if (d.register.isFloat) M68kRegisterConvention.RETURN_FLOAT
                                else M68kRegisterConvention.RETURN_INT
                            else -> null
                        }
                        if (pinned != null)
                            returnPin[d.register] = pinned
                    }
                }
                // caller-saved kill: nothing to do for allocated vregs (pool is callee-saved), but assert no overlap.
                require(callerSavedKillSet.none { it in dataRegs || it in addrRegs || it in fpRegs }) {
                    "allocatable pool overlaps caller-saved kill set"
                }
            }
        }

        fun vregClass(vr: VirtualRegister): Int {
            returnPin[vr]?.let { pinned ->
                return when {
                    pinned.startsWith("fp") -> 2
                    pinned.startsWith("a") -> 1
                    else -> 0
                }
            }
            return when {
                vr.isFloat -> 2                     // FPU
                hints[vr] == AllocationHint.PREFER_ADDRESS -> 1  // ADDRESS
                else -> 0                           // DATA
            }
        }

        val interference = mutableMapOf<VirtualRegister, MutableSet<VirtualRegister>>()
        for (v in vregs) interference[v] = mutableSetOf()
        // Instruction-level interference: chunk-granular liveness misses conflicts, a coarser clique forbids valid coalescing.
        val forbiddenByVreg = mutableMapOf<VirtualRegister, MutableSet<String>>()
        val subCallEffects = mutableMapOf<Int, CallSiteRegisterEffects>()
        // Ordinary-call targets -> vregs this caller keeps live across the call (mapped to physical regs later).
        val liveAcrossByTarget = mutableMapOf<String, MutableList<Set<VirtualRegister>>>()
        // Ordinals count every instruction carrying a callSite, in flattened chunk order.
        val callOrdinal = java.util.IdentityHashMap<IRInstruction, Int>()
        var nextOrdinal = 0
        for (chunk in chunks)
            for (ins in chunk.instructions)
                if (ins.callSite != null) callOrdinal[ins] = nextOrdinal++
        var maxLiveData = 0
        var maxLiveAddress = 0
        var maxLiveFloat = 0
        for (ci in chunks.indices) {
            val chunk = chunks[ci]
            val live = liveOut[ci].toMutableSet()
            for (ins in chunk.instructions.reversed()) {
                val uses = mutableSetOf<VirtualRegister>()
                val defs = mutableSetOf<VirtualRegister>()
                for (ra in ins.registerAccesses) {
                    when (ra.direction) {
                        OperandDirection.USE, OperandDirection.USE_DEF -> uses.add(ra.register)
                        else -> {}
                    }
                    when (ra.direction) {
                        OperandDirection.DEF, OperandDirection.USE_DEF -> defs.add(ra.register)
                        else -> {}
                    }
                }
                val liveAtEntry = (live + uses).toSet()
                // A pure definition interferes too, else it could share with a value live across it.
                val interferers = (liveAtEntry + defs).toSet()
                var nData = 0
                var nAddr = 0
                var nFp = 0
                for (v in liveAtEntry) {
                    when (vregClass(v)) {
                        0 -> nData++
                        1 -> nAddr++
                        else -> nFp++
                    }
                }
                if (nData > maxLiveData) maxLiveData = nData
                if (nAddr > maxLiveAddress) maxLiveAddress = nAddr
                if (nFp > maxLiveFloat) maxLiveFloat = nFp
                val list = interferers.toList()
                for (a in list.indices) for (b in a + 1 until list.size) {
                    interference.getOrPut(list[a]) { mutableSetOf() }.add(list[b])
                    interference.getOrPut(list[b]) { mutableSetOf() }.add(list[a])
                }
                // Fixed-register call boundaries: values surviving the call cannot sit in registers it touches.
                ins.callSite?.let { cs ->
                    val ordinaryTarget = ordinaryCallLabel(cs)
                    if (ordinaryTarget != null) {
                        demandedByCallee.getOrPut(ordinaryTarget) { mutableSetOf() }
                        val liveAcross = (live - defs).toSet()
                        if (liveAcross.isNotEmpty())
                            liveAcrossByTarget.getOrPut(ordinaryTarget) { mutableListOf() }.add(liveAcross)
                    }
                    val effects = fixedBoundaryEffects(cs)
                    if (effects != null) {
                        subCallEffects[callOrdinal[ins]!!] = effects
                        // An asmsub body follows the m68k register rule (it saves every callee-saved register it writes),
                        // so only unresolved targets such as extsubs may destroy any pool register.
                        val target = cs.target
                        val asmSub = (target as? CallTarget.Direct)?.reference?.let {
                            (it as? CodeReference.Label)?.name?.let { name -> asmSubByLabel[name] }
                        }
                        val knownOrdinary = ordinaryTarget != null
                        val unknownCallee = asmSub == null && !knownOrdinary && target !is CallTarget.AmigaLibrary
                        val clobberedPool = if (unknownCallee) allocatablePool
                            else (effects.reads + effects.defined + effects.destroys).intersect(allocatablePool)
                        if (clobberedPool.isNotEmpty()) {
                            for (v in live - defs) {
                                forbiddenByVreg.getOrPut(v) { mutableSetOf() }.addAll(clobberedPool)
                            }
                        }
                    }
                }
                live.removeAll(defs)
                live.addAll(uses)
            }
        }
        if (subCallEffects.isNotEmpty()) callSiteEffects[sub] = subCallEffects

        val forwarded = forwardedCallArgVregs(chunks)
        // Greedy coloring per class, highest degree first
        val ordered = vregs.filter { it !in forwarded }.sortedByDescending { interference[it]!!.size }
        val color = mutableMapOf<VirtualRegister, String>()
        var spills = 0
        for (v in ordered) {
            val cls = vregClass(v)
            val pool = when (cls) { 0 -> dataRegs; 1 -> addrRegs; 2 -> fpRegs; else -> dataRegs }
            val usedByNeighbors = interference[v]!!.mapNotNull { color[it] }.toSet()
            val forbidden = forbiddenByVreg[v] ?: emptySet()
            val pick = pool.firstOrNull { it !in usedByNeighbors && it !in forbidden }
            if (pick != null) color[v] = pick
            else spills++ // no register available for this class: spilled to a frame slot
        }

        for ((v, p) in color) mapBySub.getOrPut(sub) { mutableMapOf() }[v] = p
        calleeSavedBySub[sub] = color.values.toSet()
        // The caller's demand is what it actually placed in registers across each call; spilled values need nothing.
        for ((targetLabel, liveSets) in liveAcrossByTarget) {
            val demanded = demandedByCallee.getOrPut(targetLabel) { mutableSetOf() }
            for (liveSet in liveSets)
                for (v in liveSet) color[v]?.let { demanded.add(it) }
        }
        spillDemandBySub[sub] = spills
        val spilled = vregs.filter { it !in color && it !in forwarded }.toList()
        spilledVregsBySub[sub] = spilled
        var totalRefs = 0
        var allocatedRefs = 0
        for (chunk in chunks) {
            for (ins in chunk.instructions) {
                for (ra in ins.registerAccesses) {
                    val refs = if (ra.direction == OperandDirection.USE_DEF) 2 else 1
                    totalRefs += refs
                    if (ra.register in color) allocatedRefs += refs
                }
            }
        }
        statsBySub[sub] = SubAllocStats(totalRefs, allocatedRefs, spilled.size, maxLiveData, maxLiveAddress, maxLiveFloat)
    }

    // Vregs whose only accesses are one immediate LOAD and one hardware-register call argument, in the
    // same chunk. AsmGen forwards such constants straight into the argument register, so they take no
    // register and no frame slot. Must stay a subset of what AsmGen.immediateLoadsForCall forwards.
    private fun forwardedCallArgVregs(chunks: List<IRCodeChunkBase>): Set<VirtualRegister> {
        val accesses = mutableMapOf<VirtualRegister, Int>()
        for (chunk in chunks)
            for (ins in chunk.instructions)
                for (ra in ins.registerAccesses)
                    accesses.merge(ra.register, 1, Int::plus)
        val forwarded = mutableSetOf<VirtualRegister>()
        for (chunk in chunks) {
            val instructions = chunk.instructions
            for (callIndex in instructions.indices) {
                val call = instructions[callIndex]
                val callSite = call.callSite ?: continue
                if (call.opcode != Opcode.CALL || callSite.arguments.isEmpty() ||
                    callSite.arguments.any { it.location !is CallLocation.HardwareRegister }) continue
                for (arg in callSite.arguments) {
                    val reg = arg.source.register
                    if (accesses[reg] != 2) continue
                    val definition = (callIndex - 1 downTo 0).firstNotNullOfOrNull { idx ->
                        instructions[idx].takeIf { it.exclusiveDestination() == reg }
                    } ?: continue
                    if (definition.forwardableImmediateFor(reg, arg.source.type) != null) forwarded.add(reg)
                }
            }
        }
        return forwarded
    }
}
