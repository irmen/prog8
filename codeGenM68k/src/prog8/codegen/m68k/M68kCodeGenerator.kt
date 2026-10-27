package prog8.codegen.m68k

import prog8.code.ICodeGeneratorBackend
import prog8.code.SymbolTable
import prog8.code.assembly.IAssemblyProgram
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.ICompilationTarget
import prog8.code.core.IErrorReporter
import prog8.codegen.intermediate.IRCodeGen
import prog8.codegen.intermediate.StackFrameLayout
import prog8.intermediate.*

class M68kCodeGenerator(val retainSSA: Boolean): ICodeGeneratorBackend {
    override fun generate(
        program: PtProgram,
        symbolTable: SymbolTable,
        options: CompilationOptions,
        errors: IErrorReporter
    ): IAssemblyProgram {

        val irCodeGen = IRCodeGen(program, symbolTable, options, errors, retainSSA)
        val irProgram = irCodeGen.generate()
        var virtualRegisterTypes = irCodeGen.registerTypes().entries.associate { (num, type) ->
            val register: VirtualRegister = if (type == IRDataType.FLOAT) VirtualRegister.float(num.value) else VirtualRegister.int(num.value)
            register to type
        }
        irProgram.verifyRegisterTypes(virtualRegisterTypes)

        // Per-subroutine vreg reuse: renumbering rebuilds the type map; the program-wide check no longer applies.
        renumberVregsPerSubroutine(irProgram)?.let {
            virtualRegisterTypes = it
        }

        if (options.compTarget.supportsStackFrames) {
            // Use the pre-promotion allocation as a pressure baseline. M9 may add several
            // long-lived vregs to a subroutine; candidates that cross calls are only admitted
            // when their register class still has room in this baseline allocation.
            val baseline = M68kRegisterAllocator(irProgram).allocate()
            val pressure = baseline.statsBySub.mapValues { (_, stats) ->
                StackFrameLayout.PromotionPressure(stats.maxLiveData, stats.maxLiveAddress, stats.maxLiveFloat)
            }.mapKeys { (sub, _) -> sub.label }
            StackFrameLayout(irProgram, errors, pressure).apply()
        }

        if (options.dumpVariables)
            dumpVariables(irProgram)

        // Register allocation is done as late as possible: after frame layout and variable dump,
        // but before the IR is serialized (spill slots are patched into frames first) and before AsmGen.
        val allocation = M68kRegisterAllocator(irProgram).allocate()
        for ((sub, spilled) in allocation.spilledVregsBySub) {
            if (spilled.isNotEmpty())
                appendSpillSlotsToFrame(sub, spilled, virtualRegisterTypes, irProgram.options.compTarget, errors)
        }

        IRFileWriter(irProgram, null).write()

        // Aggregate register-allocation statistics (printed for every m68k compile, unless quiet).
        // Safe to print after the IR is written: this block has no side effects.
        if (!options.quiet) {
            val stats = allocation.statsBySub
            val totalSubs = stats.size
            val totalVregs = allocation.mapBySub.values.sumOf { it.size }
            val totalRefs = stats.values.sumOf { it.totalRefs }
            val allocatedRefs = stats.values.sumOf { it.allocatedRefs }
            val residency = if (totalRefs == 0) 1.0 else allocatedRefs.toDouble() / totalRefs
            val totalSpills = stats.values.sumOf { it.spillCount }
            val totalSpillDemand = allocation.spillDemandBySub.values.sum()
            val totalSpilledVregs = allocation.spilledVregsBySub.values.sumOf { it.size }
            val residencyPct = String.format(java.util.Locale.US, "%.1f", residency * 100.0)
            println("m68k register allocation: $totalSubs subs, $totalVregs vregs, $allocatedRefs/$totalRefs refs ($residencyPct% residency), spills=$totalSpills ($totalSpilledVregs vregs, demand $totalSpillDemand)")
        }

        val gen = AsmGen(irProgram, irProgram.options.compTarget, errors)
        gen.regAllocationBySub = allocation.mapBySub
        gen.calleeSavedBySub = allocation.calleeSavedBySub
        gen.callSiteEffects = allocation.callSiteEffects
        if (!gen.generate())
            throw RuntimeException("M68k assembly generation failed")

        return AssemblyProgramM68k(irProgram.name)
    }
}

// Append allocator spill slots to a subroutine's A5 frame; a frameless sub gains a spill-only
// frame. A %option noframe sub manages the machine stack itself and must never gain a frame:
// failing loudly is the only sound option, as the static register file shares slots under vreg reuse.
internal fun appendSpillSlotsToFrame(
    sub: IRSubroutine,
    spilled: List<VirtualRegister>,
    types: Map<VirtualRegister, IRDataType>,
    target: ICompilationTarget,
    errors: IErrorReporter
) {
    if (sub.noframe) {
        errors.err("subroutine ${sub.label} is marked %option noframe and cannot take register-allocator spill slots; reduce register pressure (fewer live values)", sub.position)
        return
    }
    val fresh = spilled.filter { it.num !in sub.frameVregSlots }.sortedBy { it.num }
    if (fresh.isEmpty()) return
    var cursor = -sub.frameSize
    val slots = mutableMapOf<Int, Int>()
    // Per-sub vreg reuse gives one number different types per sub, so the global map is not reliable.
    val subTypes = mutableMapOf<VirtualRegister, IRDataType>()
    sub.forEachInstruction { ins ->
        for (ra in ins.registerAccesses) {
            subTypes.putIfAbsent(ra.register, ra.type)
        }
    }
    for (vr in fresh) {
        val size = when (requireNotNull(subTypes[vr] ?: types[vr]) { "spilled $vr of ${sub.label} has no type info" }) {
            IRDataType.BYTE -> 1
            IRDataType.WORD -> 2
            IRDataType.FLOAT -> target.FLOAT_MEM_SIZE.toInt()
            else -> target.POINTER_MEM_SIZE.toInt()
        }
        val alignment = when {
            size >= 4 -> 4
            size >= 2 -> 2
            else -> 1
        }
        cursor = cursor.floorDiv(alignment) * alignment
        cursor -= size
        slots[vr.num] = cursor
    }
    val newSize = (-cursor + 1) / 2 * 2
    if (newSize > 16384)
        errors.err("frame size $newSize of subroutine ${sub.label} exceeds the 16384 byte limit", sub.position)
    sub.frameSize = newSize
    sub.frameVregSlots = sub.frameVregSlots + slots
}
