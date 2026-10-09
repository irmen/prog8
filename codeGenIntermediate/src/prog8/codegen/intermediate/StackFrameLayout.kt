package prog8.codegen.intermediate

import prog8.code.core.*
import prog8.intermediate.*

/**
 * Stack frame layout for m68k targets.
 *
 * Classifies subroutines as "frameable" and, for those:
 *  - moves their plain-data locals into A5-relative frame slots (negative offsets),
 *  - moves their parameters into the caller-pushed incoming argument area (positive offsets,
 *    4 bytes per parameter, first parameter at the highest offset),
 *  - rewrites every memory reference to AddressBase.FrameSlot and every call site's argument
 *    locations to CallLocation.FrameSlot,
 *  - removes the moved variables from the static (BSS) symbol table,
 *  - emits coalesced prologue zero-clears for clean locals (parameters are written by the caller),
 *  - stamps frameSize / incomingSize on the IRSubroutine.
 *
 * Everything else keeps the existing static path verbatim. A subroutine is frameable when ALL of:
 *  - it is not marked %option noframe; inline assembly is allowed and does not by itself prevent
 *    framing, so asm that manipulates the machine stack or A5 must opt out explicitly,
 *  - it makes no indirect calls and its address is never taken (subptr dispatch tables, interrupt
 *    handler registration, ...): an unknown caller cannot push arguments into a frame,
 *  - it cannot reach a subroutine whose assembly code writes to A5: such a call would overwrite
 *    the frame pointer while this subroutine's frame slots are still live,
 *  - all its parameters are frameable (a parameter that cannot move forces the whole
 *    subroutine to keep the static convention, since the caller must then write static cells),
 *  - every local it moves is plain data (byte/word/long/pointer scalar or numeric/bool array)
 *    that is not address-taken, inline-asm referenced, static-initialized or explicitly aligned.
 *    Locals that fail this test simply stay static; they do not disqualify the subroutine.
 *
 * A subroutine that can have two live activations (it lies on a cycle of the call graph, with
 * indirect calls conservatively assumed to reach every dispatch target) must additionally move
 * *everything* into its frame: its virtual registers become frame slots and each remaining
 * static variable is a compile error, since two activations would share that storage.
 * Such a subroutine with nothing left to share is fine on the static path.
 */
class StackFrameLayout(private val program: IRProgram, private val errors: IErrorReporter) {

    companion object {
        const val MAX_FRAME_SIZE = 16384      // §3: per-frame limit, far below the 16-bit displacement range

        /** bytes reserved per incoming parameter (padded longword slot, §6.1) */
        const val PARAM_SLOT_SIZE = 4

        /** virtual register numbers from here on are reserved for the runtime and cannot be relocated */
        const val RESERVED_VREG_RANGE_START = 99000

        /** frame-pointer offset of the last-pushed (rightmost) argument slot: above saved a5 + return address */
        const val INCOMING_BASE = 8
    }

    private val target = program.options.compTarget

    private class FrameVar(val name: String, val slot: Int, val elemDt: DataType, val count: Int, val dirty: Boolean)

    fun apply() {
        val allSubs = program.allSubs().toList()
        val subLabels = allSubs.map { it.label }.toSet()

        val referencedIn = mutableMapOf<String, MutableSet<String>>()
        val addressTaken = mutableSetOf<String>()
        val asmTexts = mutableListOf<String>()

        fun noteRefs(chunk: IRCodeChunkBase, sub: String?) {
            chunk.instructions.forEach { instr ->
                instr.memory?.symbolName?.let { referencedIn.getOrPut(it) { mutableSetOf() } += (sub ?: "<top>") }
                (instr.immediate as? ImmediateOperand.SymbolAddress)?.let { addressTaken += it.symbol }
            }
        }

        for (sub in allSubs) sub.forEachChunk { noteRefs(it, sub.label) }
        program.globalInits.forEachChunkRecursive { noteRefs(it, null) }
        program.blocks.forEach { block ->
            block.children.filterIsInstance<IRCodeChunkBase>().forEach { chunk ->
                chunk.forEachChunkRecursive { noteRefs(it, null) }
            }
        }
        program.allAsmSubs().forEach { asmSub -> asmTexts += asmSub.asmChunk.assembly }
        program.forEachChunk { chunk -> if (chunk is IRInlineAsmChunk) asmTexts += chunk.assembly }

        val callSites = collectCallSites(subLabels)
        val dispatchTargets = collectDispatchTargets(subLabels, addressTaken)
        val indirectCallers = allSubs.filter { sub -> containsIndirectCall(sub) }.map { it.label }.toSet()
        val reentrant = subsWithMultipleLiveActivations(callSites, indirectCallers, dispatchTargets)
        val framePointerClobberers = framePointerClobberingSubs()
        val reachesFramePointerClobber = subsReachingFramePointerClobber(framePointerClobberers, indirectCallers)

        val framedSubs = mutableMapOf<String, FrameLayoutResult>()
        for (sub in allSubs) {
            val outcome = frameableSubroutine(sub, referencedIn, addressTaken, asmTexts, callSites, dispatchTargets, indirectCallers, reentrant, reachesFramePointerClobber, framePointerClobberers)
            if (outcome is Frameability.Frameable)
                framedSubs[sub.label] = outcome.layout
        }

        if (framedSubs.isNotEmpty())
            rewriteCallArgumentLocations(framedSubs)

        reportUnsoundStaticState(framedSubs, reentrant, referencedIn, addressTaken, asmTexts)
        rewriteOutgoingJumps(framedSubs.keys)
    }

    private class CallSiteRef(val caller: String, val site: CallSite)

    /** all direct calls between normal subroutines, grouped by callee label */
    private fun collectCallSites(subLabels: Set<String>): Map<String, List<CallSiteRef>> {
        val result = mutableMapOf<String, MutableList<CallSiteRef>>()
        for (sub in program.allSubs()) {
            sub.forEachChunk { chunk ->
                val instrs = chunk.instructions
                for (i in instrs.indices) {
                    val site = instrs[i].callSite ?: continue
                    val ref = site.codeReference
                    if (ref is CodeReference.Label && ref.name in subLabels) {
                        result.getOrPut(ref.name) { mutableListOf() } += CallSiteRef(sub.label, site)
                    }
                }
            }
        }
        return result
    }

    /**
     * Subroutine labels whose address is taken somewhere in the program: dispatch tables
     * (static variable initializers holding symbolic references), address-taken immediates,
     * or referenced from inline assembly. Such a subroutine can be entered indirectly and
     * therefore possibly re-entered while an activation is live.
     */
    private fun collectDispatchTargets(subLabels: Set<String>, addressTaken: Set<String>): Set<String> {
        val targets = mutableSetOf<String>()
        addressTaken.forEach { sym -> if (sym in subLabels) targets += sym }
        program.st.allVariables().forEach { v ->
            val init = v.initializationValue ?: return@forEach
            val refs = when (init) {
                is IRVariableInitializer.Array -> init.elements.mapNotNull { (it as? IRStSymbolicReference.Symbol)?.name }
                is IRVariableInitializer.Str, is IRVariableInitializer.Numeric -> emptyList()
            }
            refs.forEach { name -> if (name in subLabels) targets += name }
        }
        return targets
    }

    private fun containsIndirectCall(sub: IRSubroutine): Boolean {
        var found = false
        sub.forEachInstruction { instr ->
            val site = instr.callSite ?: return@forEachInstruction
            if (site.codeReference is CodeReference.Indirect)
                found = true
        }
        return found
    }

    /**
     * Labels of subroutines whose code writes to A5, the frame pointer register of a framed
     * subroutine. Assembly subroutines are matched on their assembly text, ordinary subroutines on
     * the text of their inline assembly chunks (a subroutine without assembly cannot write A5).
     *
     * Such a subroutine invalidates the frame of its caller: every frame-slot reference in the
     * caller then uses a different base register, and the caller's `unlk a5` restores a frame
     * pointer that was never saved.
     */
    private fun framePointerClobberingSubs(): Set<String> {
        val result = mutableSetOf<String>()
        program.allAsmSubs().forEach { asmSub ->
            if (writesFramePointerRegister(asmSub.asmChunk.assembly))
                result += asmSub.label
        }
        for (sub in program.allSubs()) {
            sub.forEachChunk { chunk ->
                if (chunk is IRInlineAsmChunk && writesFramePointerRegister(chunk.assembly))
                    result += sub.label
            }
        }
        return result
    }

    /**
     * Subroutine labels that can reach a frame-pointer-clobbering subroutine through a real
     * call chain, or that can reach an indirect call (which may dispatch to any address-taken
     * subroutine including a clobberer). Calls into %option noframe subroutines are a barrier:
     * the mark asserts a save/restore discipline that leaves the caller's A5 intact.
     */
    private fun subsReachingFramePointerClobber(clobberers: Set<String>, indirectCallers: Set<String>): Set<String> {
        if (clobberers.isEmpty())
            return emptySet()
        // Calls into %option noframe subroutines are a barrier for both propagations below:
        // the mark asserts a save/restore discipline that leaves the caller's A5 intact when
        // the call returns (or abandons the caller, as when switching away), so clobber reach
        // through such a call is a false positive. The marked subroutines themselves stay
        // classified by their own assembly.
        val noframeSubs = program.allSubs().filter { it.noframe }.map { it.label }.toSet()
        val callTargets = mutableMapOf<String, MutableSet<String>>()
        for (sub in program.allSubs()) {
            val targets = callTargets.getOrPut(sub.label) { mutableSetOf() }
            sub.forEachChunk { chunk ->
                chunk.instructions.forEach { instr ->
                    when (val ref = instr.callSite?.codeReference) {
                        is CodeReference.Label -> targets += ref.name
                        else -> {}
                    }
                }
            }
        }
        val reaching = mutableSetOf<String>()
        var changed = true
        while (changed) {
            changed = false
            for ((label, targets) in callTargets) {
                if (label in reaching)
                    continue
                if (targets.filter { it !in noframeSubs }.any { it in clobberers || it in reaching }) {
                    reaching += label
                    changed = true
                }
            }
        }
        // no real call chain reaches a clobberer: if an indirect call is reachable (here or
        // downstream), it may dispatch to any address-taken subroutine including a clobberer
        val reachesIndirect = mutableSetOf<String>()
        changed = true
        while (changed) {
            changed = false
            for ((label, targets) in callTargets) {
                if (label in reachesIndirect)
                    continue
                if (label in indirectCallers || targets.filter { it !in noframeSubs }.any { it in reachesIndirect }) {
                    reachesIndirect += label
                    changed = true
                }
            }
        }
        for (label in callTargets.keys) {
            if (label !in reaching && label in reachesIndirect)
                reaching += label
        }
        return reaching
    }

    /**
     * True when the assembly text writes to A5 without protecting the caller's frame pointer, in a
     * form this textual check recognises: as the destination operand of an instruction, as the frame
     * register of a `link`, or anywhere in a `movem` register list (whose mask syntax is not parsed
     * here). The usual `move.l a5,-(sp)` ... `move.l (sp)+,a5` prologue/epilogue pair is recognised
     * as balanced and does not count; a write in between does.
     *
     * The scan is linear and ignores branches, so a subroutine that saves A5 on one path and
     * overwrites it on another is reported conservatively as a clobber. Being over-cautious only
     * costs the calling subroutine its stack frame (it keeps the static convention instead).
     */
    private fun writesFramePointerRegister(asm: String): Boolean {
        var saved = false
        for (rawLine in asm.lines()) {
            // drop comments and a leading label, so a labelled instruction is still examined
            val line = rawLine.substringBefore(';')
                .replace(Regex("^[.0-9A-Za-z_$]+:"), "")
                .trim().replace(Regex("[ \t]+"), " ")
            if (line.isEmpty() || line.startsWith('.'))    // equates and directives
                continue
            val words = line.split(' ')
            val mnemonic = words[0].lowercase()
            val operands = words.drop(1).joinToString(",").split(',')
            if (operands.any { it.isEmpty() })
                continue
            if (mnemonic.startsWith("movem")) {
                val pushes = operands.last().lowercase() == "-(sp)"
                val mentionsA5 = operands.any { it.lowercase().contains("a5") }
                if (pushes && mentionsA5) {
                    saved = true
                    continue
                }
                if (!pushes && operands.first().lowercase() == "(sp)+" && mentionsA5) {
                    saved = false
                    continue
                }
                if (mentionsA5 && !saved)
                    return true
                continue
            }
            if (mnemonic == "link" && operands[0].lowercase().removePrefix("-") == "a5")
                return true
            val source = operands.first().lowercase()
            val destination = operands.last().lowercase()
            val writesA5 = destination == "a5" || destination == "-a5"
            if (destination == "-(sp)" && source == "a5") {
                saved = true
                continue
            }
            if (source == "(sp)+" && writesA5 && operands.size == 2) {
                saved = false
                continue
            }
            if (writesA5 && !saved)
                return true
        }
        return false
    }

    /**
     * Labels of subroutines that can have two live activations at the same time: they lie on a
     * cycle of direct calls (direct recursion or mutual recursion), or on a cycle that only closes
     * through an indirect call. Uses an iterative Tarjan SCC pass over the call graph.
     */
    private fun subsWithMultipleLiveActivations(
        callSites: Map<String, List<CallSiteRef>>,
        indirectCallers: Set<String>,
        dispatchTargets: Set<String>
    ): Set<String> {
        val edges = mutableMapOf<String, MutableSet<String>>()
        for (sub in program.allSubs())
            edges.getOrPut(sub.label) { mutableSetOf() }
        for ((callee, refs) in callSites) {
            for (ref in refs) {
                val outs = edges.getOrPut(ref.caller) { mutableSetOf() }
                if (outs.add(callee))
                    edges.getOrPut(callee) { mutableSetOf() }
            }
        }
        // an indirect call could dispatch to any subroutine whose address is taken
        for (caller in indirectCallers) {
            val outs = edges.getOrPut(caller) { mutableSetOf() }
            for (target in dispatchTargets)
                outs.add(target)
        }

        val index = mutableMapOf<String, Int>()
        val lowLink = mutableMapOf<String, Int>()
        val onStack = mutableSetOf<String>()
        val sccStack = ArrayDeque<String>()
        var counter = 0
        val reentrant = mutableSetOf<String>()

        for (root in edges.keys) {
            if (root in index) continue
            index[root] = counter
            lowLink[root] = counter
            counter++
            sccStack.addLast(root)
            onStack += root
            val work = ArrayDeque<Pair<String, Iterator<String>>>()
            work.addLast(root to edges.getValue(root).iterator())
            while (work.isNotEmpty()) {
                val (node, children) = work.last()
                if (children.hasNext()) {
                    val next = children.next()
                    when {
                        next !in index -> {
                            index[next] = counter
                            lowLink[next] = counter
                            counter++
                            sccStack.addLast(next)
                            onStack += next
                            work.addLast(next to edges.getValue(next).iterator())
                        }
                        next in onStack -> {
                            lowLink[node] = minOf(lowLink.getValue(node), index.getValue(next))
                        }
                    }
                } else {
                    work.removeLast()
                    if (lowLink.getValue(node) == index.getValue(node)) {
                        val members = mutableListOf<String>()
                        while (true) {
                            val w = sccStack.removeLast()
                            onStack -= w
                            members += w
                            if (w == node) break
                        }
                        val cyclic = members.size > 1 || node in edges.getValue(node)
                        if (cyclic) reentrant += members
                    }
                    if (work.isNotEmpty()) {
                        val parent = work.last().first
                        lowLink[parent] = minOf(lowLink.getValue(parent), lowLink.getValue(node))
                    }
                }
            }
        }
        return reentrant
    }

    private class FrameLayoutResult(
        //. parameter variable name -> frame offset of its incoming slot
        val paramSlots: Map<String, Int>,
        // parameter variable names that keep a static cell and get an entry-time copy-in (§6.1)
        val copyInParams: Set<String>
    )

    /** outcome of the frameability decision for one subroutine */
    private sealed interface Frameability {
        class Frameable(val layout: FrameLayoutResult) : Frameability
        class NotFrameable() : Frameability
    }

    private fun frameableSubroutine(
        sub: IRSubroutine,
        referencedIn: Map<String, Set<String>>,
        addressTaken: Set<String>,
        asmTexts: List<String>,
        callSites: Map<String, List<CallSiteRef>>,
        dispatchTargets: Set<String>,
        indirectCallers: Set<String>,
        reentrant: Set<String>,
        reachesFramePointerClobber: Set<String>,
        framePointerClobberers: Set<String>
    ): Frameability {
        val static = { Frameability.NotFrameable() }
        // a %option noframe subroutine requires the legacy static convention (it manipulates
        // the machine stack itself), so it never gets a frame
        if (sub.noframe) return static()
        if (sub.hasFrame) return static()
        if (sub.label in indirectCallers) return static()
        // An address-taken subroutine with parameters must stay static: an indirect call site
        // cannot know which convention to pass arguments in. Without parameters there is nothing
        // to pass (indirect calls never carry arguments), so framing is transparent to every
        // entry path and only the re-entrancy check below still applies.
        if (sub.label in dispatchTargets && sub.parameters.isNotEmpty()) return static()
        if (isLabelReferencedInAsm(sub.label, asmTexts)) return static()
        if (sub.label in reachesFramePointerClobber) return static()
        if (sub.label in framePointerClobberers) return static()

        // every parameter must be able to move: otherwise the caller keeps writing static cells
        // IRParam.name is the scoped variable name; call sites refer to the same parameter by its
        // last name segment only, so accept both forms.
        val scopedParamNames = sub.parameters.map { param ->
            if (param.name.startsWith("${sub.label}.")) param.name else "${sub.label}.${param.name}"
        }
        val paramSlots = mutableMapOf<String, Int>()
        val paramDisplacements = mutableMapOf<String, Int>()
        val copyInParams = mutableSetOf<String>()
        for ((index, _) in sub.parameters.withIndex()) {
            val name = scopedParamNames[index]
            val v = program.st.lookup(name) as? IRStStaticVariable ?: return static()
            val elemDt = isFrameableParameter(v, name, addressTaken, asmTexts) ?: return static()
            if (v.length != null) return static()
            // a parameter referenced from outside the subroutine (defer handler, inline assembly)
            // keeps its static cell for ALL references (including the body's own uses) and gets
            // an entry-time copy-in from its incoming slot (§6.1); its address may not be taken
            // (that stays a hard error)
            val refs = referencedIn[name]
            if ((refs != null && refs.any { it != sub.label }) || isLabelReferencedInAsm(name, asmTexts))
                copyInParams += name
            // first parameter ends up at the highest offset, last one just above the return address
            val offset = INCOMING_BASE + PARAM_SLOT_SIZE * (sub.parameters.size - 1 - index)
            paramSlots[name] = offset
            // narrow values are right-justified in their longword slot
            paramDisplacements[name] = when (irTypeFor(elemDt)) {
                IRDataType.BYTE -> 3
                IRDataType.WORD -> 2
                else -> 0
            }
        }

        // all call sites must use the plain parameter-memory convention of this convention
        val sites = callSites[sub.label].orEmpty()
        if (paramSlots.isNotEmpty()) {
            if (sites.isEmpty()) return static()
            for (siteRef in sites) {
                val args = siteRef.site.arguments
                if (args.size != sub.parameters.size) return static()
                for ((argIndex, arg) in args.withIndex()) {
                    val loc = arg.location
                    if (loc !is CallLocation.ParameterMemory) return static()
                    if (loc.address != null) return static()
                    val scoped = scopedParamNames[argIndex]
                    if (loc.name.isNotBlank() && loc.name != scoped && loc.name != scoped.substringAfterLast('.'))
                        return static()
                }
            }
        }

        // candidate locals: symbols referenced only from this sub, scoped under the sub label
        val prefix = sub.label + "."
        val candidates = referencedIn.keys.filter { name ->
            name.startsWith(prefix) && referencedIn.getValue(name) == setOf(sub.label) && name !in paramSlots
        }.sorted()
        // a re-entrant subroutine can still qualify with nothing but its virtual registers
        if (candidates.isEmpty() && paramSlots.isEmpty() && sub.label !in reentrant)
            return static()

        // Collect layout info first, then place clean (zero-clear) locals together so they
        // form one contiguous region that emitZeroRegions and the m68k backend can clear
        // efficiently.  Non-clean locals (dirty, initialized, float) are placed afterwards.
        data class LocalLayout(
            val name: String,
            val elemDt: DataType,
            val count: Int,
            val size: Int,
            val alignment: Int,
            val dirty: Boolean,
            val initialized: Boolean,
            val isFloat: Boolean
        )

        val locals = mutableListOf<LocalLayout>()
        for (name in candidates) {
            val v = program.st.lookup(name) as? IRStStaticVariable ?: continue
            val elemDt = isFrameableVariable(v, name, addressTaken, asmTexts) ?: continue
            val count = v.length?.toInt() ?: 1
            val size = target.memorySize(v.dt, v.length?.toInt())
            if (size <= 0 || size >= MAX_FRAME_SIZE) continue
            val alignment = when {
                size >= 4 -> 4
                size >= 2 -> 2
                else -> 1
            }
            val initialized = v.initializationValue != null
            val isFloat = irTypeFor(elemDt) == IRDataType.FLOAT
            locals += LocalLayout(name, elemDt, count, size, alignment, v.dirty, initialized, isFloat)
            if (v.length != null && size > 8 && !v.dirty && v.initializationValue == null) {
                val shortName = name.substringAfterLast('.').removePrefix("p8v_")
                errors.warn("local array '$shortName' ($size bytes) in subroutine '${userName(sub)}' is zero-initialized on every call which can be costly; consider @dirty if you assign it before use", sub.position)
            }
        }

        val sortedLocals = locals.sortedWith(
            compareBy<LocalLayout> { when {
                it.dirty || it.initialized || it.isFloat -> 1
                else -> 0
            } }
                .thenByDescending { it.alignment }
                .thenBy { if (it.size % it.alignment == 0) 0 else 1 }  // size-aligned locals leave no gap
                .thenByDescending { it.size }
                .thenBy { it.name }
        )

        val frameVars = mutableListOf<FrameVar>()
        var cursor = 0
        for (local in sortedLocals) {
            cursor = cursor.floorDiv(local.alignment) * local.alignment
            cursor -= local.size
            frameVars += FrameVar(local.name, cursor, local.elemDt, local.count, local.dirty)
        }
        // a subroutine that can be re-entered needs its virtual registers per-activation as well:
        // the flat program-static register file is shared by all activations of the same subroutine
        val vregSlots = mutableMapOf<Int, Int>()
        if (sub.label in reentrant) {
            val vregs = vregsUsedIn(sub)
            if (vregs.keys.any { it >= RESERVED_VREG_RANGE_START })
                return static()
            val (newCursor, slots) = allocateVregSlots(sub, vregs, cursor)
            cursor = newCursor
            vregSlots.putAll(slots)
        }
        if (frameVars.isEmpty() && paramSlots.isEmpty() && vregSlots.isEmpty())
            return static()

        val frameSize = (-cursor + 1) / 2 * 2
        if (frameSize > MAX_FRAME_SIZE) {
            errors.err("frame size $frameSize of subroutine ${userName(sub)} exceeds the $MAX_FRAME_SIZE byte limit; " +
                    "reduce local variable usage", sub.position)
            return static()
        }

        // every static (base slot + displacement) access combination must stay in the 16-bit displacement range
        val slotBySymbol = frameVars.associate { it.name to it.slot }
        var outOfRange = false
        sub.forEachInstruction { instr ->
            val mem = instr.memory
            val sym = mem?.symbolName
            val slot = if (sym != null) slotOf(sym, slotBySymbol, paramSlots) else null
            // parameters are right-justified in their padded longword slot, so include that displacement
            val extra = paramDisplacements[sym] ?: 0
            val disp = when (mem) {
                is MemoryReference.Direct -> slot?.plus(mem.displacement)?.plus(extra)
                is MemoryReference.Indexed -> slot?.plus(mem.displacement)?.plus(extra)
                else -> null
            }
            if (disp != null && (disp < -32768 || disp > 32767)) outOfRange = true
            // relocated virtual registers must stay in 16-bit displacement range as well
            for (access in instr.registerAccesses) {
                val regOffset = vregSlots[access.register.num]
                if (regOffset != null && (regOffset < -32768 || regOffset > 32767)) outOfRange = true
            }
        }
        if (outOfRange) {
            errors.err("frame-relative displacement out of 16-bit range in subroutine ${userName(sub)}", sub.position)
            return static()
        }

        // rewrite references: Symbol base -> FrameSlot base (parameters additionally get the
        // right-justification displacement of their padded slot).
        // Copy-in parameters keep their static cell for ALL references (§6.1): the entry-time
        // copy-in loads the incoming value into that cell, and the body reads and writes it
        // there - this also keeps the externally referencing code (defer handler, inline
        // assembly) observing the body's current value instead of a stale one.
        sub.forEachChunk { chunk ->
            val instrs = chunk.instructions
            for (i in instrs.indices) {
                val instr = instrs[i]
                val mem = instr.memory ?: continue
                val sym = mem.symbolName ?: continue
                if (sym in copyInParams) continue
                val slot = slotOf(sym, slotBySymbol, paramSlots) ?: continue
                val newMem = when (mem) {
                    is MemoryReference.Direct ->
                        mem.copy(base = AddressBase.FrameSlot(slot), displacement = mem.displacement + (paramDisplacements[sym] ?: 0))
                    is MemoryReference.Indexed ->
                        mem.copy(base = AddressBase.FrameSlot(slot), displacement = mem.displacement + (paramDisplacements[sym] ?: 0))
                    else -> continue
                }
                instrs[i] = instr.mapMemoryReferences { newMem }
            }
        }

        // prologue: entry-time copy-in for externally referenced parameters (§6.1), then the
        // numeric initializers of static-initialized locals, then zeroing for clean (non-dirty)
        // locals; incoming parameters are written by the caller
        val prologueInstrs = mutableListOf<IRInstruction>()
        if (copyInParams.isNotEmpty()) {
            val used = program.registersUsed()
            var nextIntScratch = (used.intRegsTypes.keys.maxOfOrNull { it.value } ?: 0) + 1
            var nextFloatScratch = ((used.floatRegsRead.keys + used.floatRegsWritten.keys).maxOfOrNull { it.value } ?: 0) + 1
            require(nextIntScratch < RESERVED_VREG_RANGE_START) { "copy-in integer scratch register would collide with reserved vreg range" }
            require(nextFloatScratch < RESERVED_VREG_RANGE_START) { "copy-in float scratch register would collide with reserved vreg range" }
            for (name in copyInParams.sorted()) {
                val v = program.st.lookup(name) as? IRStStaticVariable ?: continue
                val elemDt = if (v.dt.isArray) baseToScalar(v.dt.sub!!) else v.dt
                val irType = elemDt?.let { irTypeFor(it) } ?: continue
                val offset = paramSlots[name] ?: continue
                val displacement = paramDisplacements[name] ?: 0
                val scratch = if (irType == IRDataType.FLOAT) nextFloatScratch++ else nextIntScratch++
                prologueInstrs += IRInstructions.loadMemory(Opcode.LOADM, irType, scratch, IRMemory.frameDirect(offset, displacement))
                prologueInstrs += IRInstructions.storeMemory(Opcode.STOREM, irType, scratch, IRMemory.direct(name))
            }
        }
        // Zero regions: byte ranges [start, end) of clean (non-dirty, non-initialized)
        // non-float locals. Regions are merged across variables wherever exactly
        // contiguous, then emitted as aligned stores below.
        val zeroRegions = mutableListOf<Pair<Int, Int>>()
        for (fv in frameVars) {
            val init = (program.st.lookup(fv.name) as? IRStStaticVariable)?.initializationValue as? IRVariableInitializer.Numeric
            if (init != null) {
                // a static-initialized local gets its initializer stored into the frame slot;
                // this replaces the zero-clear for it
                val irType = irTypeFor(fv.elemDt) ?: continue
                if (irType == IRDataType.FLOAT)
                    prologueInstrs += IRInstructions.storeImmediateFloat(init.value, IRMemory.frameDirect(fv.slot))
                else
                    prologueInstrs += IRInstructions.storeImmediate(irType, init.value.toInt(), IRMemory.frameDirect(fv.slot))
                continue
            }
            if (fv.dirty) continue
            val irType = irTypeFor(fv.elemDt) ?: continue
            val elemSize = target.memorySize(fv.elemDt, null)
            if (irType == IRDataType.FLOAT) {
                // float scalars and float array elements are zero-filled with STOREZM float stores
                // (the m68k backend supports them); the VM does not run this pass.
                // Floats never join zero regions, so they also break adjacency between them.
                for (element in 0 until fv.count) {
                    prologueInstrs += IRInstructions.storeZero(Opcode.STOREZM, irType, IRMemory.frameDirect(fv.slot, element * elemSize))
                }
                continue
            }
            zeroRegions += fv.slot to fv.slot + fv.count * elemSize
        }
        emitZeroRegions(prologueInstrs, zeroRegions)

        // the moved variables now live per-activation; remove them from the static (BSS) symbol table.
        // copy-in parameters keep their static cell for the external reference (§6.1)
        (slotBySymbol.keys + paramSlots.keys.filter { it !in copyInParams }).forEach { program.st.removeIfExists(it) }

        if (prologueInstrs.isNotEmpty()) {
            // prepend into the existing first chunk (it carries the sub label; the chunk list
            // structure and its label invariant must not change)
            val firstChunk = sub.chunks.first() as IRCodeChunk
            firstChunk.instructions.addAll(0, prologueInstrs)
        }
        sub.frameSize = frameSize
        sub.incomingSize = paramSlots.size * PARAM_SLOT_SIZE
        sub.frameVregSlots = vregSlots
        return Frameability.Frameable(FrameLayoutResult(paramSlots, copyInParams))
    }

    /**
     * Emits the merged zero-clear stores for [regions]. Exactly-contiguous ranges merge
     * regardless of owning variable; the frameVars walk above is name-ordered rather than
     * address-ordered, so regions are sorted first. Each merged run becomes one contiguous,
     * address-ascending STOREZM block: the m68k backend's clear-loop detection relies on
     * that shape, so nothing may be interleaved inside a run.
     */
    private fun emitZeroRegions(prologueInstrs: MutableList<IRInstruction>, regions: List<Pair<Int, Int>>) {
        val merged = mutableListOf<Pair<Int, Int>>()
        for ((start, end) in regions.sortedBy { it.first }) {
            val last = merged.lastOrNull()
            if (last != null && last.second == start)
                merged[merged.lastIndex] = last.first to end
            else
                merged += start to end
        }
        for ((lo, hi) in merged) {
            var offset = lo
            // a5-relative word/longword access requires an even address; a run starting
            // odd clears its first byte separately, which restores evenness
            if (offset % 2 != 0) {
                prologueInstrs += IRInstructions.storeZero(Opcode.STOREZM, IRDataType.BYTE, IRMemory.frameDirect(offset))
                offset++
            }
            while (hi - offset >= 4) {
                prologueInstrs += IRInstructions.storeZero(Opcode.STOREZM, IRDataType.LONG, IRMemory.frameDirect(offset))
                offset += 4
            }
            if (hi - offset >= 2) {
                prologueInstrs += IRInstructions.storeZero(Opcode.STOREZM, IRDataType.WORD, IRMemory.frameDirect(offset))
                offset += 2
            }
            if (hi - offset == 1)
                prologueInstrs += IRInstructions.storeZero(Opcode.STOREZM, IRDataType.BYTE, IRMemory.frameDirect(offset))
        }
    }

    /** the virtual registers used by the instructions of a subroutine, by register number */
    private fun vregsUsedIn(sub: IRSubroutine): Map<Int, IRDataType> {
        val result = mutableMapOf<Int, IRDataType>()
        sub.forEachChunk { chunk ->
            // a loop chunk aggregates its own body, which forEachChunk visits separately anyway
            if (chunk is IRLoopChunk) return@forEachChunk
            val used = chunk.usedRegisters()
            for ((reg, type) in used.regsTypes)
                result[reg.num] = type
            // float registers are not typed in regsTypes; all of them are floats
            for (reg in used.readRegs.keys + used.writeRegs.keys)
                result.getOrPut(reg.num) { IRDataType.FLOAT }
        }
        return result
    }

    private class VregSlot(val offset: Int, val size: Int, var occupant: Int)

    /**
     * Frame slots for the virtual registers of a re-entrant subroutine. Unless the subroutine
     * has a backward branch, register lifetimes are intervals over the flattened instruction
     * order and a slot whose previous occupant is already dead is reused by a later register.
     * A back edge (any loop chunk, or a branch/jump to a label at or before its own position,
     * including a jump into another subroutine) can keep a value live across the jump, which
     * plain last-use intervals would get wrong, so such a subroutine keeps one dedicated slot
     * per register.
     */
    private fun allocateVregSlots(sub: IRSubroutine, vregs: Map<Int, IRDataType>, cursor: Int): Pair<Int, Map<Int, Int>> {
        if (vregs.isEmpty())
            return cursor to emptyMap()

        // flatten the non-loop chunks into a linear instruction list; loop chunks force the
        // conservative one-slot-per-register fallback and are skipped here
        val instrList = mutableListOf<IRInstruction>()
        val chunkFirstIndex = mutableMapOf<String, Int>()
        var containsLoop = false
        sub.forEachChunk { chunk ->
            if (chunk is IRLoopChunk) {
                containsLoop = true
                return@forEachChunk
            }
            val label = chunk.label
            if (label != null)
                chunkFirstIndex[label] = instrList.size
            chunk.instructions.forEach { instrList.add(it) }
        }

        if (containsLoop || branchesBackwards(sub, instrList, chunkFirstIndex))
            return oneSlotPerRegister(vregs, cursor)

        val firstUse = mutableMapOf<Int, Int>()
        val lastUse = mutableMapOf<Int, Int>()
        instrList.forEachIndexed { index, instr ->
            for (access in instr.registerAccesses) {
                val num = access.register.num
                firstUse[num] = minOf(firstUse[num] ?: index, index)
                lastUse[num] = maxOf(lastUse[num] ?: index, index)
            }
        }

        // greedy linear scan: a register takes the first free slot it fits in, otherwise a
        // fresh slot below the locals; registers live at the same time can never share
        val slots = mutableListOf<VregSlot>()
        var c = cursor
        val vregSlots = mutableMapOf<Int, Int>()
        instrList.forEachIndexed { index, instr ->
            for (regNum in instr.registerAccesses.map { it.register.num }.distinct().sorted()) {
                if (firstUse[regNum] != index) continue
                val size = slotSizeFor(vregs[regNum] ?: continue)
                val alignment = when {
                    size >= 4 -> 4
                    size >= 2 -> 2
                    else -> 1
                }
                val slot = slots.firstOrNull { s ->
                    (s.occupant < 0 || lastUse[s.occupant]!! < index) && s.offset % alignment == 0 && s.size >= size
                }
                if (slot != null) {
                    slot.occupant = regNum
                    vregSlots[regNum] = slot.offset
                } else {
                    c = c.floorDiv(alignment) * alignment
                    c -= size
                    vregSlots[regNum] = c
                    slots += VregSlot(c, size, regNum)
                }
            }
        }
        return c to vregSlots
    }

    /** true when any branch/jump instruction targets a label at or before its own position,
     *  a label outside this subroutine, an unresolvable label, or an indirect target */
    private fun branchesBackwards(sub: IRSubroutine, instrList: List<IRInstruction>, chunkFirstIndex: Map<String, Int>): Boolean {
        val subChunks = mutableSetOf<IRCodeChunkBase>()
        sub.forEachChunk { subChunks.add(it) }
        for ((index, instr) in instrList.withIndex()) {
            // an indirect jump can target arbitrary code, including a backward edge;
            // be conservative and treat it like a backward branch
            if (instr.opcode == Opcode.JUMPI)
                return true
            val target = instr.target as? CodeReference.Label ?: continue
            val targetChunk = program.resolveCodeTarget(target) ?: return true
            if (targetChunk !in subChunks) return true
            val targetIndex = chunkFirstIndex[target.name] ?: return true
            if (targetIndex <= index) return true
        }
        return false
    }

    private fun oneSlotPerRegister(vregs: Map<Int, IRDataType>, cursor: Int): Pair<Int, Map<Int, Int>> {
        var c = cursor
        val vregSlots = mutableMapOf<Int, Int>()
        for ((regNum, irType) in vregs.entries.sortedBy { it.key }) {
            val size = slotSizeFor(irType)
            val alignment = when {
                size >= 4 -> 4
                size >= 2 -> 2
                else -> 1
            }
            c = c.floorDiv(alignment) * alignment
            c -= size
            vregSlots[regNum] = c
        }
        return c to vregSlots
    }

    private fun slotSizeFor(irType: IRDataType): Int = when (irType) {
        IRDataType.BYTE -> 1
        IRDataType.WORD -> 2
        IRDataType.FLOAT -> target.FLOAT_MEM_SIZE.toInt()
        else -> target.POINTER_MEM_SIZE.toInt()
    }

    /**
     * A re-entrant subroutine that keeps part of its state in program-static storage shares that
     * storage between its live activations, which silently corrupts data. There is no sound way to
     * compile that, so reject it: every variable it touches and every virtual register it uses must
     * live in its stack frame.
     *
     * Only variables that the subroutine body itself uses are reported. A variable that is merely
     * read by a defer handler holds no per-activation data; the compiler rejects defer in a
     * recursive subroutine separately.
     */
    private fun reportUnsoundStaticState(
        framedSubs: Map<String, FrameLayoutResult>,
        reentrant: Set<String>,
        referencedIn: Map<String, Set<String>>,
        addressTaken: Set<String>,
        asmTexts: List<String>
    ) {
        for (sub in program.allSubs()) {
            if (sub.label !in reentrant) continue
            // a %option noframe subroutine manages its own stack discipline by design;
            // the programmer accepts that its overlapping runs share data
            if (sub.noframe) continue
            val framed = sub.label in framedSubs
            val sharedVars = staticVarsOwnedBy(sub).filter { referencedIn[it]?.contains(sub.label) == true }
            // a copy-in parameter keeps a static cell for its external references (§6.1),
            // shared between overlapping runs of a re-entrant subroutine
            val sharedCopyIns = (framedSubs[sub.label]?.copyInParams ?: emptySet()).filter { it !in sharedVars }
            // an unframed subroutine also shares its virtual registers; a framed one relocated them all
            val sharedVregs = if (framed) emptyList() else vregsUsedIn(sub).keys.toList()
            if (sharedVars.isEmpty() && sharedVregs.isEmpty() && sharedCopyIns.isEmpty()) continue
            val details = mutableListOf<String>()
            for (varName in sharedVars) {
                val v = program.st.lookup(varName) as? IRStStaticVariable
                val why = v?.let { frameableVariableProblem(it, varName, addressTaken, asmTexts) }
                    ?: if (framed) "it is shared with other code" else null
                val name = "variable '${varName.substringAfterLast('.').removePrefix("p8v_")}'"
                details += if (why == null) name else "$name ($why)"
            }
            for (paramName in sharedCopyIns) {
                details += "parameter '${paramName.substringAfterLast('.').removePrefix("p8v_")}' (shared with other code)"
            }
            if (sharedVregs.isNotEmpty())
                details += if (sharedVregs.size == 1) "1 temporary value" else "${sharedVregs.size} temporary values"
            errors.err("subroutine '${userName(sub)}' cannot be compiled: it may be running twice at the same time, " +
                    "while sharing data between those runs: ${details.joinToString(", ")}. " +
                    "Restructure it so that each run has its own private data, " +
                    "or mark it %option noframe if the sharing is intentional",
                sub.position)
        }
    }

    /** the scoped IR label without its internal scope markers, for use in user-facing messages */
    private fun userName(sub: IRSubroutine): String =
        sub.label.split('.').joinToString(".") { it.removePrefix("p8b_").removePrefix("p8s_") }

    /** the static variables that belong to this subroutine (the longest matching label prefix owns the name) */
    private fun staticVarsOwnedBy(sub: IRSubroutine): List<String> {
        val subLabels = program.allSubs().map { it.label }
        fun ownerOf(varName: String): String? =
            subLabels.filter { varName.startsWith("$it.") }.maxByOrNull { it.length }
        return program.st.allVariables().map { it.name }.filter { ownerOf(it) == sub.label }.toList()
    }

    /**
     * A framed subroutine cannot branch out to another subroutine: its `link` has already adjusted
     * the stack pointer, so the other subroutine's `rts` would return into the middle of the local
     * area. Turn such a tail jump into a call followed by the normal return of this subroutine.
     * A conditional branch out (`if cc goto <sub>`) becomes an inverted branch over the tail call:
     * when the condition holds the call is executed, otherwise the skip label resumes the
     * not-taken path with the frame still live.
     */
    private fun rewriteOutgoingJumps(framedSubLabels: Set<String>) {
        val subLabels = program.allSubs().map { it.label }.toSet()
        for (sub in program.allSubs()) {
            if (sub.label !in framedSubLabels) continue
            // collect first: rewriting below inserts chunks into the chunk lists of this sub
            val rewrites = mutableListOf<OutgoingJump>()
            sub.forEachChunk { chunk ->
                chunk.instructions.forEachIndexed { i, instr ->
                    val target = instr.target as? CodeReference.Label ?: return@forEachIndexed
                    if (target.name == sub.label || target.name !in subLabels) return@forEachIndexed
                    when {
                        instr.opcode == Opcode.JUMP -> rewrites += OutgoingJump(chunk, i, target)
                        instr.opcode in InvertedBranchOpcodes -> rewrites += OutgoingJump(chunk, i, target)
                    }
                }
            }
            if (rewrites.isEmpty()) continue

            val parentLists = mutableListOf<MutableList<IRCodeChunkBase>>()
            fun collectLists(chunks: MutableList<IRCodeChunkBase>) {
                parentLists += chunks
                chunks.filterIsInstance<IRLoopChunk>().forEach { collectLists(it.body) }
            }
            collectLists(sub.chunks)

            for (rew in rewrites) {
                val instrs = rew.chunk.instructions
                if (instrs[rew.index].opcode == Opcode.JUMP) {
                    instrs[rew.index] = IRInstructions.call(CallSite(
                        target = CallTarget.Direct(rew.target),
                        arguments = emptyList()
                    ))
                    instrs.add(rew.index + 1, IRInstructions.returnVoid())
                    // the RETURN is now the terminator; anything that followed the
                    // original JUMP in this chunk is unreachable dead code
                    while (instrs.size > rew.index + 2)
                        instrs.removeAt(instrs.size - 1)
                } else {
                    val skipLabel = nextGeneratedLabel(sub)
                    // the not-taken path continues in a fresh chunk right after this one
                    val tail = IRCodeChunk(skipLabel, rew.chunk.next)
                    tail.instructions.addAll(instrs.subList(rew.index + 1, instrs.size))
                    val original = instrs[rew.index]
                    while (instrs.size > rew.index + 1)
                        instrs.removeAt(instrs.size - 1)
                    instrs[rew.index] = invertedBranch(original, skipLabel)
                    instrs += IRInstructions.call(CallSite(
                        target = CallTarget.Direct(rew.target),
                        arguments = emptyList()
                    ))
                    instrs += IRInstructions.returnVoid()
                    rew.chunk.next = null        // the chunk now ends with RETURN
                    val parent = parentLists.first { rew.chunk in it }
                    parent.add(parent.indexOf(rew.chunk) + 1, tail)
                }
            }
        }
    }

    private class OutgoingJump(val chunk: IRCodeChunkBase, val index: Int, val target: CodeReference.Label)

    private var generatedLabelCounter = 0

    /** unique label scoped under the subroutine, for compiler-generated control flow */
    private fun nextGeneratedLabel(sub: IRSubroutine): String =
        "${sub.label}.p8_tailskip${generatedLabelCounter++}"

    private val InvertedStatusBranch = mapOf(
        Opcode.BSTCC to Opcode.BSTCS, Opcode.BSTCS to Opcode.BSTCC,
        Opcode.BSTEQ to Opcode.BSTNE, Opcode.BSTNE to Opcode.BSTEQ,
        Opcode.BSTNEG to Opcode.BSTPOS, Opcode.BSTPOS to Opcode.BSTNEG,
        Opcode.BSTVC to Opcode.BSTVS, Opcode.BSTVS to Opcode.BSTVC)

    // immediate-form comparison branches keep their (register, immediate) operands
    private val InvertedImmediateBranch = mapOf(
        Opcode.BGT to Opcode.BLE, Opcode.BLE to Opcode.BGT,
        Opcode.BLT to Opcode.BGE, Opcode.BGE to Opcode.BLT,
        Opcode.BGTS to Opcode.BLES, Opcode.BLES to Opcode.BGTS,
        Opcode.BLTS to Opcode.BGES, Opcode.BGES to Opcode.BLTS)

    // register-form comparison branches swap both operands: bgtr a,b inverts to bger b,a
    // (not(a>b) <=> b>=a); the m68k backend supports all of these opcodes directly
    private val InvertedRegisterBranch = mapOf(
        Opcode.BGTR to Opcode.BGER, Opcode.BGER to Opcode.BGTR,
        Opcode.BGTSR to Opcode.BGESR, Opcode.BGESR to Opcode.BGTSR)

    private val InvertedBranchOpcodes: Set<Opcode> =
        InvertedStatusBranch.keys + InvertedImmediateBranch.keys + InvertedRegisterBranch.keys

    /** the same conditional branch with the inverted condition, retargeted to [skipLabel] */
    private fun invertedBranch(instr: IRInstruction, skipLabel: String): IRInstruction {
        val skip = codeLabel(skipLabel)
        return when (val opcode = instr.opcode) {
            in InvertedStatusBranch -> instr.copy(opcode = InvertedStatusBranch.getValue(opcode), target = skip)
            in InvertedImmediateBranch -> instr.copy(opcode = InvertedImmediateBranch.getValue(opcode), target = skip)
            in InvertedRegisterBranch -> instr.copy(
                opcode = InvertedRegisterBranch.getValue(opcode),
                srcA = instr.srcB?.copy(role = OperandRole.LEFT),
                srcB = instr.srcA?.copy(role = OperandRole.RIGHT),
                target = skip)
            else -> throw AssemblyError("cannot invert conditional branch ${instr.opcode}")
        }
    }

    private fun slotOf(symbol: String?, slotBySymbol: Map<String, Int>, paramSlots: Map<String, Int>): Int? {
        if (symbol == null) return null
        return slotBySymbol[symbol] ?: paramSlots[symbol]
    }

    /** rewrite the argument locations of every call to a framed subroutine */
    private fun rewriteCallArgumentLocations(framedSubs: Map<String, FrameLayoutResult>) {
        if (framedSubs.isEmpty()) return
        for (sub in program.allSubs()) {
            val offsetsByCallee = mutableMapOf<String, List<Int>>()
            for ((callee, layout) in framedSubs) {
                val params = calleeParameters(callee) ?: continue
                if (params.isEmpty()) continue
                offsetsByCallee[callee] = params.mapIndexed { _, param ->
                    val scoped = if (param.startsWith("$callee.")) param else "$callee.$param"
                    layout.paramSlots[scoped] ?: 0
                }
            }
            if (offsetsByCallee.isEmpty()) continue
            sub.forEachChunk { chunk ->
                val instrs = chunk.instructions
                for (i in instrs.indices) {
                    val instr = instrs[i]
                    val site = instr.callSite ?: continue
                    val callee = (site.codeReference as? CodeReference.Label)?.name ?: continue
                    val offsets = offsetsByCallee[callee] ?: continue
                    if (site.arguments.size != offsets.size) continue
                    var changed = false
                    val rewritten = site.arguments.mapIndexed { argIndex, arg ->
                        if (arg.location is CallLocation.FrameSlot) {
                            arg
                        } else {
                            changed = true
                            arg.copy(location = CallLocation.FrameSlot(offsets[argIndex]))
                        }
                    }
                    if (changed)
                        instrs[i] = instr.copy(callSite = site.copy(arguments = rewritten))
                }
            }
        }
    }

    private fun calleeParameters(callee: String): List<String>? =
        program.allSubs().firstOrNull { it.label == callee }?.parameters?.map { it.name }

    /** returns the element data type when the variable is frameable, null otherwise */
    private fun isFrameableVariable(v: IRStStaticVariable, name: String, addressTaken: Set<String>, asmTexts: List<String>): DataType? {
        if (frameableVariableProblem(v, name, addressTaken, asmTexts) != null)
            return null
        // an array is described by its element data type, a scalar by its own
        return if (v.dt.isArray) baseToScalar(v.dt.sub!!) else v.dt
    }

    /** like [isFrameableVariable], but an inline-assembly reference is acceptable: the parameter
     *  keeps its static cell and gets an entry-time copy-in from its incoming slot (§6.1) */
    private fun isFrameableParameter(v: IRStStaticVariable, name: String, addressTaken: Set<String>, asmTexts: List<String>): DataType? {
        if (frameableVariableProblem(v, name, addressTaken, asmTexts, asmAllowed = true) != null)
            return null
        // an array is described by its element data type, a scalar by its own
        return if (v.dt.isArray) baseToScalar(v.dt.sub!!) else v.dt
    }

    /** returns why the variable cannot be moved into a frame, or null when it can */
    private fun frameableVariableProblem(v: IRStStaticVariable, name: String, addressTaken: Set<String>, asmTexts: List<String>, asmAllowed: Boolean = false): String? {
        if (v.shared) return "it is marked @shared"    // @shared guarantees a stable, externally visible address
        if (name in addressTaken) return "its address is taken"
        if (!asmAllowed && isLabelReferencedInAsm(name, asmTexts)) return "inline assembly refers to it"
        if (v.initializationValue != null && v.initializationValue !is IRVariableInitializer.Numeric) return "it has a static initializer"
        if (v.align > 0u) return "it requests an explicit alignment"
        if (v.zpwish == ZeropageWish.REQUIRE_ZEROPAGE || v.zpwish == ZeropageWish.PREFER_ZEROPAGE) return "it wants to be in the zeropage"
        return when {
            v.dt.isPointer -> null                  // pointer storage is always just an address
            v.dt.isBasic && v.dt.isNumericOrBool -> null
            v.dt.isArray && v.dt.sub != null && v.dt.sub != BaseDataType.POINTER -> {
                val subDt = baseToScalar(v.dt.sub!!)
                if (subDt == null) "it is not a plain numeric or boolean array"
                else if (subDt.isStructInstance || subDt.isString || subDt.isPointer) "it is not plain data"
                else null
            }
            else -> "it is not plain data"
        }
    }

    /** true if [label] occurs as a whole symbol reference in any inline assembly text */
    private fun isLabelReferencedInAsm(label: String, asmTexts: List<String>): Boolean {
        // Avoid false positives where one label is a prefix/suffix of another (e.g. main.foo
        // inside main.foobar). Treat label characters as letters, digits, underscore and dot.
        val escaped = Regex.escape(label)
        val pattern = "(?<![A-Za-z0-9_.])$escaped(?![A-Za-z0-9_.])".toRegex()
        return asmTexts.any { pattern.containsMatchIn(it) }
    }

    private fun baseToScalar(base: BaseDataType): DataType? = when (base) {
        BaseDataType.UBYTE -> DataType.UBYTE
        BaseDataType.BYTE -> DataType.BYTE
        BaseDataType.BOOL -> DataType.BOOL
        BaseDataType.UWORD -> DataType.UWORD
        BaseDataType.WORD -> DataType.WORD
        BaseDataType.LONG -> DataType.LONG
        BaseDataType.FLOAT -> DataType.FLOAT
        else -> null
    }

    private fun irTypeFor(dt: DataType): IRDataType? = when {
        dt.isByteOrBool -> IRDataType.BYTE
        dt.isWord -> IRDataType.WORD
        dt.isLong || dt.isPointer -> IRDataType.LONG
        dt.isFloat -> IRDataType.FLOAT
        else -> null
    }
}
