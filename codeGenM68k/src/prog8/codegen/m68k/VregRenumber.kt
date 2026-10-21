package prog8.codegen.m68k

import prog8.intermediate.*

// Stage 2 step 10: per-subroutine virtual-register reuse.
//
// The IR generator assigns globally unique vreg numbers (RegisterPool never
// resets), so a program with many subroutines accumulates one distinct number
// per temporary. The m68k allocator colors each subroutine independently, so
// global uniqueness buys nothing: renumbering every subroutine's vregs into a
// compact per-sub range shrinks the program-wide register file and the BSS
// derived from it.
//
// Soundness notes:
// - Only runs behind -m68kregalloc, so default output is untouched.
// - Reserved vregs (>= 99000, syscall argument/result slots) keep their numbers.
// - Subroutine vregs are numbered from above the highest number used outside
//   subroutines (global inits, top-level chunks), so renumbered sub vregs can
//   never collide with those. Different subroutines intentionally share numbers;
//   every consumer of vreg-number-keyed state is per-subroutine (the allocation
//   map is swapped per sub in AsmGen, spill slots and callee-saved sets are
//   per-sub, the d0 cache is cleared on sub entry).
// - Rewriting goes through IRInstruction.mapRegisters, which covers every place
//   a vreg can occur: dest/src, memory operands, indirect targets, and call
//   sites (arguments, results, bank registers).
// - Under allocation no subroutine vreg resolves to BSS: allocated vregs resolve
//   to physical registers and spilled vregs to per-activation frame slots (see
//   operand() and regAddr()). registersUsed() therefore runs in its permissive
//   (wasPackingApplied) mode, which is exactly the "same slot number, different
//   types in different subroutines" case.
// - Raw (non-IR) inline assembly that names p8_regfile/p8_fregfile textually
//   would observe the new layout, so renumbering bails out (returns null) then.

/** vreg numbers at or above this are reserved (syscall slots) and never renumbered. */
private const val RESERVED_VREG_START = 99000

internal fun renumberVregsPerSubroutine(program: IRProgram): Map<VirtualRegister, IRDataType>? {
    var rawRegfileRef = false
    program.forEachChunk { chunk ->
        if (chunk is IRInlineAsmChunk && !chunk.isIR &&
            (AsmGen.REGFILE_LABEL in chunk.assembly || AsmGen.FLOAT_REGFILE_LABEL in chunk.assembly))
            rawRegfileRef = true
    }
    if (rawRegfileRef) return null
    // Highest vreg number used outside subroutines; sub ranges start above it.
    var maxOutside = 0
    fun scanOutside(chunk: IRCodeChunkBase) {
        for (ins in chunk.instructions)
            for (ra in ins.registerAccesses)
                if (ra.register.num < RESERVED_VREG_START && ra.register.num > maxOutside)
                    maxOutside = ra.register.num
    }
    program.globalInits.forEachChunkRecursive(::scanOutside)
    for (block in program.blocks)
        for (child in block.children)
            if (child is IRCodeChunkBase) child.forEachChunkRecursive(::scanOutside)
    for (sub in program.allSubs()) {
        val intNums = mutableSetOf<Int>()
        val floatNums = mutableSetOf<Int>()
        sub.forEachChunk { chunk ->
            for (ins in chunk.instructions) {
                for (ra in ins.registerAccesses) {
                    if (ra.register.num >= RESERVED_VREG_START) continue
                    if (ra.register.isFloat) floatNums.add(ra.register.num)
                    else intNums.add(ra.register.num)
                }
            }
        }
        if (intNums.isEmpty() && floatNums.isEmpty()) continue
        val intMap = intNums.sorted().withIndex()
            .associate { (i, old) -> VirtualRegister.int(old) to VirtualRegister.int(maxOutside + i + 1) }
        val floatMap = floatNums.sorted().withIndex()
            .associate { (i, old) -> VirtualRegister.float(old) to VirtualRegister.float(maxOutside + i + 1) }
        require(intMap.values.all { it.num < RESERVED_VREG_START }) { "renumbered int vreg collides with reserved range" }
        require(floatMap.values.all { it.num < RESERVED_VREG_START }) { "renumbered float vreg collides with reserved range" }
        if (intMap.values.toSet() == intMap.keys && floatMap.values.toSet() == floatMap.keys) continue
        sub.forEachChunk { chunk ->
            for (i in chunk.instructions.indices) {
                val ins = chunk.instructions[i]
                val newIns = ins.mapRegisters { vr -> intMap[vr] ?: floatMap[vr] ?: vr }
                if (newIns != ins) chunk.instructions[i] = newIns
            }
        }
    }
    program.wasPackingApplied = true
    val types = mutableMapOf<VirtualRegister, IRDataType>()
    program.forEachInstruction { ins ->
        for (ra in ins.registerAccesses) types.putIfAbsent(ra.register, ra.type)
    }
    return types
}
