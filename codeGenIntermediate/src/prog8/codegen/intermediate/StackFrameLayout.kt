package prog8.codegen.intermediate

import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.IErrorReporter
import prog8.code.core.ZeropageWish
import prog8.intermediate.*

/**
 * Vertical Slice Prototype (m68k-stack-memory-model.md §17): classify leaf subroutines as
 * "frameable", move their plain-data locals into A5-relative stack frame slots, rewrite all
 * references to AddressBase.FrameSlot, remove them from the static (BSS) variable list,
 * zero-clear them in the prologue and stamp the frame size on IRSubroutine.
 *
 * A subroutine is frameable when ALL of:
 *  - it has no parameters,
 *  - it contains no CALL/CALLI/CALLFAR/CALLFARVB/SYSCALL (leaf; also rules out self-recursion),
 *  - none of its locals is address-taken (SymbolAddress immediate), referenced by inline
 *    assembly or static-initialized,
 *  - every local is a byte/word/long/pointer scalar or a plain numeric/bool array.
 *
 * Everything else keeps the existing static path verbatim. Deferring subroutines gain a
 * handler-invoke call during DeferProcessor flattening, so they are never frameable here;
 * call-live vregs cannot exist in a leaf, so the flat p8_regfile may stay program-static
 * in the prototype.
 */
class StackFrameLayout(private val program: IRProgram, private val errors: IErrorReporter) {

    companion object {
        const val MAX_FRAME_SIZE = 16384      // §3: per-frame limit, far below the 16-bit displacement range
    }

    private val target = program.options.compTarget

    private class FrameVar(val name: String, val slot: Int, val elemDt: DataType, val count: Int, val dirty: Boolean)

    fun apply() {
        val allSubs = program.allSubs().toList()

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

        allSubs.forEach { frameableSubroutine(it, referencedIn, addressTaken, asmTexts) }
    }

    private fun frameableSubroutine(
        sub: IRSubroutine,
        referencedIn: Map<String, Set<String>>,
        addressTaken: Set<String>,
        asmTexts: List<String>
    ) {
        if (sub.parameters.isNotEmpty()) return
        if (sub.frameSize != 0) return
        var hasInlineAsm = false
        sub.forEachChunk { chunk -> if (chunk is IRInlineAsmChunk) hasInlineAsm = true }
        if (hasInlineAsm) return
        val callOpcodes = setOf(Opcode.CALL, Opcode.CALLI, Opcode.CALLFAR, Opcode.CALLFARVB, Opcode.SYSCALL)
        if (sub.chunks.any { containsCall(it, callOpcodes) }) return

        // candidate locals: symbols referenced only from this sub, scoped under the sub label
        val prefix = sub.label + "."
        val candidates = referencedIn.keys.filter { name ->
            name.startsWith(prefix) && referencedIn.getValue(name) == setOf(sub.label)
        }.sorted()
        if (candidates.isEmpty()) return

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
        if (frameVars.isEmpty()) return

        val frameSize = (-cursor + 1) / 2 * 2
        if (frameSize > MAX_FRAME_SIZE) {
            errors.err("frame size $frameSize of subroutine ${sub.label} exceeds the $MAX_FRAME_SIZE byte limit; " +
                    "reduce local variable usage", sub.position)
            return
        }

        // every static (base slot + displacement) access combination must stay in the 16-bit displacement range
        val slotBySymbol = frameVars.associate { it.name to it.slot }
        var outOfRange = false
        sub.forEachInstruction { instr ->
            val disp = when (val mem = instr.memory) {
                is MemoryReference.Direct -> slotBySymbol[mem.symbolName]?.plus(mem.displacement)
                is MemoryReference.Indexed -> slotBySymbol[mem.symbolName]?.plus(mem.displacement)
                else -> null
            }
            if (disp != null && (disp < -32768 || disp > 32767)) outOfRange = true
        }
        if (outOfRange) {
            errors.err("frame-relative displacement out of 16-bit range in subroutine ${sub.label}", sub.position)
            return
        }

        // rewrite references: Symbol base -> FrameSlot base
        sub.forEachChunk { chunk ->
            val instrs = chunk.instructions
            for (i in instrs.indices) {
                val instr = instrs[i]
                val mem = instr.memory ?: continue
                val slot = slotBySymbol[mem.symbolName] ?: continue
                val newMem = when (mem) {
                    is MemoryReference.Direct -> mem.copy(base = AddressBase.FrameSlot(slot))
                    is MemoryReference.Indexed -> mem.copy(base = AddressBase.FrameSlot(slot))
                    else -> continue
                }
                instrs[i] = instr.mapMemoryReferences { newMem }
            }
        }

        // prologue zeroing for clean (non-dirty) frame vars; DIRTY vars stay intentionally uninitialized
        val clearInstrs = mutableListOf<IRInstruction>()
        for (fv in frameVars) {
            if (fv.dirty) continue
            val irType = irTypeFor(fv.elemDt) ?: continue
            val elemSize = target.memorySize(fv.elemDt, null)
            for (element in 0 until fv.count) {
                clearInstrs += IRInstructions.storeZero(Opcode.STOREZM, irType, IRMemory.frameDirect(fv.slot, element * elemSize))
            }
        }

        // the variables now live per-activation; remove them from the static (BSS) symbol table
        slotBySymbol.keys.forEach { program.st.removeIfExists(it) }

        if (clearInstrs.isNotEmpty()) {
            // prepend into the existing first chunk (it carries the sub label; the chunk list
            // structure and its label invariant must not change)
            val firstChunk = sub.chunks.first() as IRCodeChunk
            firstChunk.instructions.addAll(0, clearInstrs)
        }
        sub.frameSize = frameSize
    }

    private fun containsCall(chunk: IRCodeChunkBase, callOpcodes: Set<Opcode>): Boolean {
        var found = false
        chunk.forEachChunkRecursive { c -> if (c.instructions.any { it.opcode in callOpcodes }) found = true }
        return found
    }

    /** returns the element data type when the variable is frameable, null otherwise */
    private fun isFrameableVariable(v: IRStStaticVariable, name: String, addressTaken: Set<String>, asmTexts: List<String>): DataType? {
        if (name in addressTaken) return null
        if (asmTexts.any { it.contains(name) }) return null
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
