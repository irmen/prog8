package prog8.codegen.intermediate

import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.IErrorReporter
import prog8.code.core.ZeropageWish
import prog8.intermediate.*

/**
 * Stack frame layout for m68k targets (m68k-stack-memory-model.md §3/§6.1/§7/§17).
 *
 * Classifies subroutines as "frameable" and, for those:
 *  - moves their plain-data locals into A5-relative frame slots (negative offsets),
 *  - moves their parameters into the caller-pushed incoming argument area (positive offsets,
 *    4 bytes per parameter, first parameter at the highest offset),
 *  - rewrites every memory reference to AddressBase.FrameSlot and every call site's argument
 *    locations to CallLocation.FrameSlot,
 *  - removes the moved variables from the static (BSS) symbol table,
 *  - emits prologue zero-clears for clean locals (parameters are written by the caller),
 *  - stamps frameSize / incomingSize on the IRSubroutine.
 *
 * Everything else keeps the existing static path verbatim. A subroutine is frameable when ALL of:
 *  - it contains no inline assembly chunk,
 *  - it is not on a call-graph cycle and makes no indirect calls: until call-live virtual
 *    registers move into frames (design-doc slice 3), a re-entrant activation would share the
 *    flat program-static p8_regfile slots,
 *  - its address is never taken (subptr dispatch tables, interrupt handler registration, ...),
 *  - all its parameters are frameable (a parameter that cannot move forces the whole
 *    subroutine to keep the static convention, since the caller must then write static cells),
 *  - every local it moves is plain data (byte/word/long/pointer scalar or numeric/bool array)
 *    that is not address-taken, inline-asm referenced, static-initialized or explicitly aligned.
 *    Locals that fail this test simply stay static; they do not disqualify the subroutine.
 */
class StackFrameLayout(private val program: IRProgram, private val errors: IErrorReporter) {

    companion object {
        const val MAX_FRAME_SIZE = 16384      // §3: per-frame limit, far below the 16-bit displacement range

        /** bytes reserved per incoming parameter (padded longword slot, §6.1) */
        const val PARAM_SLOT_SIZE = 4

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
        val onCycle = subsOnCallCycles(callSites)

        val framedSubs = mutableMapOf<String, FrameLayoutResult>()
        for (sub in allSubs) {
            val result = frameableSubroutine(sub, referencedIn, addressTaken, asmTexts, callSites, dispatchTargets, indirectCallers, onCycle)
            if (result != null)
                framedSubs[sub.label] = result
        }

        if (framedSubs.isNotEmpty())
            rewriteCallArgumentLocations(framedSubs)
    }

    private class CallSiteRef(val chunk: IRCodeChunkBase, val index: Int, val caller: String, val site: CallSite)

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
                        result.getOrPut(ref.name) { mutableListOf() } += CallSiteRef(chunk, i, sub.label, site)
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
     * Labels of subroutines that lie on a cycle of direct calls (direct recursion or mutual
     * recursion), using an iterative Tarjan SCC pass. Those subroutines can have two live
     * activations at once, which the flat program-static regfile cannot support yet (slice 3).
     */
    private fun subsOnCallCycles(callSites: Map<String, List<CallSiteRef>>): Set<String> {
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

        val index = mutableMapOf<String, Int>()
        val lowLink = mutableMapOf<String, Int>()
        val onStack = mutableSetOf<String>()
        val sccStack = ArrayDeque<String>()
        var counter = 0
        val onCycle = mutableSetOf<String>()

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
                        if (cyclic) onCycle += members
                    }
                    if (work.isNotEmpty()) {
                        val parent = work.last().first
                        lowLink[parent] = minOf(lowLink.getValue(parent), lowLink.getValue(node))
                    }
                }
            }
        }
        return onCycle
    }

    private class FrameLayoutResult(
        val frameSize: Int,
        val incomingSize: Int,
        /** parameter variable name -> frame offset of its incoming slot */
        val paramSlots: Map<String, Int>,
        /** parameter variable name -> right-justification displacement within its slot */
        val paramDisplacements: Map<String, Int>
    )

    private fun frameableSubroutine(
        sub: IRSubroutine,
        referencedIn: Map<String, Set<String>>,
        addressTaken: Set<String>,
        asmTexts: List<String>,
        callSites: Map<String, List<CallSiteRef>>,
        dispatchTargets: Set<String>,
        indirectCallers: Set<String>,
        onCycle: Set<String>
    ): FrameLayoutResult? {
        if (sub.hasFrame) return null
        var hasInlineAsm = false
        sub.forEachChunk { chunk -> if (chunk is IRInlineAsmChunk) hasInlineAsm = true }
        if (hasInlineAsm) return null
        if (sub.label in onCycle || sub.label in indirectCallers || sub.label in dispatchTargets) return null
        if (isLabelReferencedInAsm(sub.label, asmTexts)) return null

        // every parameter must be able to move: otherwise the caller keeps writing static cells
        // IRParam.name is the scoped variable name; call sites refer to the same parameter by its
        // last name segment only, so accept both forms.
        val scopedParamNames = sub.parameters.map { param ->
            if (param.name.startsWith("${sub.label}.")) param.name else "${sub.label}.${param.name}"
        }
        val paramSlots = mutableMapOf<String, Int>()
        val paramDisplacements = mutableMapOf<String, Int>()
        for ((index, _) in sub.parameters.withIndex()) {
            val name = scopedParamNames[index]
            // a parameter referenced from outside the subroutine (defer handler, external code)
            // cannot become per-activation storage: the whole subroutine keeps the static convention
            val refs = referencedIn[name]
            if (refs != null && refs != setOf(sub.label)) return null
            val v = program.st.lookup(name) as? IRStStaticVariable ?: return null
            val elemDt = isFrameableVariable(v, name, addressTaken, asmTexts) ?: return null
            if (v.length != null) return null                     // array parameters don't exist in the language, but be safe
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
            if (sites.isEmpty()) return null                      // no caller found: keep the static convention
            for (siteRef in sites) {
                val args = siteRef.site.arguments
                if (args.size != sub.parameters.size) return null
                for ((argIndex, arg) in args.withIndex()) {
                    val loc = arg.location
                    if (loc !is CallLocation.ParameterMemory) return null
                    if (loc.address != null) return null
                    val scoped = scopedParamNames[argIndex]
                    if (loc.name.isNotBlank() && loc.name != scoped && loc.name != scoped.substringAfterLast('.'))
                        return null
                }
            }
        }

        // candidate locals: symbols referenced only from this sub, scoped under the sub label
        val prefix = sub.label + "."
        val candidates = referencedIn.keys.filter { name ->
            name.startsWith(prefix) && referencedIn.getValue(name) == setOf(sub.label) && name !in paramSlots
        }.sorted()
        if (candidates.isEmpty() && paramSlots.isEmpty()) return null

        val frameVars = mutableListOf<FrameVar>()
        var cursor = 0
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
            cursor = cursor.floorDiv(alignment) * alignment      // align down (cursor grows negative)
            cursor -= size
            frameVars += FrameVar(name, cursor, elemDt, count, v.dirty)
        }
        if (frameVars.isEmpty() && paramSlots.isEmpty()) return null

        val frameSize = (-cursor + 1) / 2 * 2
        if (frameSize > MAX_FRAME_SIZE) {
            errors.err("frame size $frameSize of subroutine ${sub.label} exceeds the $MAX_FRAME_SIZE byte limit; " +
                    "reduce local variable usage", sub.position)
            return null
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
        }
        if (outOfRange) {
            errors.err("frame-relative displacement out of 16-bit range in subroutine ${sub.label}", sub.position)
            return null
        }

        // rewrite references: Symbol base -> FrameSlot base (parameters additionally get the
        // right-justification displacement of their padded slot)
        sub.forEachChunk { chunk ->
            val instrs = chunk.instructions
            for (i in instrs.indices) {
                val instr = instrs[i]
                val mem = instr.memory ?: continue
                val sym = mem.symbolName ?: continue
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

        // prologue zeroing for clean (non-dirty) locals; incoming parameters are written by the caller
        val clearInstrs = mutableListOf<IRInstruction>()
        for (fv in frameVars) {
            if (fv.dirty) continue
            val irType = irTypeFor(fv.elemDt) ?: continue
            val elemSize = target.memorySize(fv.elemDt, null)
            for (element in 0 until fv.count) {
                clearInstrs += IRInstructions.storeZero(Opcode.STOREZM, irType, IRMemory.frameDirect(fv.slot, element * elemSize))
            }
        }

        // the moved variables now live per-activation; remove them from the static (BSS) symbol table
        (slotBySymbol.keys + paramSlots.keys).forEach { program.st.removeIfExists(it) }

        if (clearInstrs.isNotEmpty()) {
            // prepend into the existing first chunk (it carries the sub label; the chunk list
            // structure and its label invariant must not change)
            val firstChunk = sub.chunks.first() as IRCodeChunk
            firstChunk.instructions.addAll(0, clearInstrs)
        }
        sub.frameSize = frameSize
        sub.incomingSize = paramSlots.size * PARAM_SLOT_SIZE
        return FrameLayoutResult(frameSize, sub.incomingSize, paramSlots, paramDisplacements)
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
                offsetsByCallee[callee] = params.mapIndexed { index, param ->
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
        if (name in addressTaken) return null
        if (isLabelReferencedInAsm(name, asmTexts)) return null
        if (v.initializationValue != null) return null            // static-initialized: must keep static storage
        if (v.align > 0u) return null                             // explicit alignment request: keep static
        if (v.zpwish == ZeropageWish.REQUIRE_ZEROPAGE || v.zpwish == ZeropageWish.PREFER_ZEROPAGE) return null
        return when {
            v.dt.isPointer -> v.dt                      // pointer storage is always just an address
            v.dt.isBasic && v.dt.isNumericOrBool -> v.dt
            v.dt.isArray && v.dt.sub != null && v.dt.sub != BaseDataType.POINTER -> {
                val subDt = baseToScalar(v.dt.sub!!) ?: return null
                if (subDt.isFloat || subDt.isStructInstance || subDt.isString || subDt.isPointer) null else subDt
            }
            else -> null
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
        else -> null
    }

    private fun irTypeFor(dt: DataType): IRDataType? = when {
        dt.isByteOrBool -> IRDataType.BYTE
        dt.isWord -> IRDataType.WORD
        dt.isLong || dt.isPointer -> IRDataType.LONG
        else -> null
    }
}
