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

        if (options.compTarget.supportsStackFrames)
            StackFrameLayout(irProgram, errors).apply()

        val allocation = M68kRegisterAllocator(irProgram).allocate()
        for ((sub, spilled) in allocation.spilledVregsBySub) {
            if (spilled.isNotEmpty())
                appendSpillSlotsToFrame(sub, spilled, virtualRegisterTypes, irProgram.options.compTarget, errors)
        }

        if (options.dumpVariables)
            dumpVariables(irProgram)

        IRFileWriter(irProgram, null).write()

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
