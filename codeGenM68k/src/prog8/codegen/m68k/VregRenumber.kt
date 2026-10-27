package prog8.codegen.m68k

import prog8.intermediate.*

// Per-subroutine virtual-register reuse. The IR generator assigns globally unique vreg numbers
// (RegisterPool never resets) while the m68k allocator colors each subroutine independently, so
// renumbering every sub's vregs into a compact per-sub range shrinks the program-wide register
// file and the BSS derived from it.
//
// Reserved vregs (>= 99000, syscall slots) keep their numbers; sub ranges start above the highest
// number used outside subroutines, so they can never collide. Different subs intentionally share
// numbers: every consumer of vreg-number-keyed state is per-subroutine (allocation map, spill slots,
// callee-saved sets, d0 cache). Under allocation no sub vreg resolves to BSS, hence the permissive
// registersUsed() mode set via wasPackingApplied. Raw (non-IR) inline asm naming
// p8_regfile/p8_fregfile would observe the new layout, so renumbering bails out then.

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
    for (sub in program.allSubs())
        if (sub.noframe) sub.forEachChunk(::scanOutside)
    for (sub in program.allSubs()) {
        if (sub.noframe) continue
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
