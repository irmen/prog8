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

class M68kCodeGenerator(val retainSSA: Boolean, val allocateRegisters: Boolean = false): ICodeGeneratorBackend {
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

        if (allocateRegisters) {
            // Step 10: per-subroutine vreg reuse. When it renumbers, the type map
            // is rebuilt from the renumbered program. No second verifyRegisterTypes:
            // like RegisterPacker.rebuildTypeMap, the rebuilt map is consistent per
            // subroutine but the strict program-wide check no longer applies once
            // numbers are reused across subroutines.
            renumberVregsPerSubroutine(irProgram)?.let {
                virtualRegisterTypes = it
            }
        }

        if (options.compTarget.supportsStackFrames)
            StackFrameLayout(irProgram, errors).apply()

        val allocation = if (allocateRegisters) M68kRegisterAllocator(irProgram).allocate() else null
        if (allocation != null) {
            for ((sub, spilled) in allocation.spilledVregsBySub) {
                if (spilled.isNotEmpty())
                    appendSpillSlotsToFrame(sub, spilled, virtualRegisterTypes, irProgram.options.compTarget, errors)
            }
        }

        if (options.dumpVariables)
            dumpVariables(irProgram)

        IRFileWriter(irProgram, null).write()

        val gen = AsmGen(irProgram, irProgram.options.compTarget, errors)
        if (allocation != null) {
            gen.registerAllocationEnabled = true
            gen.regAllocationBySub = allocation.mapBySub
            gen.calleeSavedBySub = allocation.calleeSavedBySub
            gen.callSiteEffects = allocation.callSiteEffects
        }
        if (!gen.generate())
            throw RuntimeException("M68k assembly generation failed")

        return AssemblyProgramM68k(irProgram.name)
    }
}

// Stage 2 step 6: append allocator spill slots to a subroutine's A5 frame.
// A framed subroutine grows its existing local area downward; a frameless one
// gains a spill-only frame. Slot geometry mirrors StackFrameLayout (natural
// sizes, 4/2/1-byte alignment, even total). Vregs that already own a frame
// slot (re-entrant subs) need no extra slot.
// A %option noframe subroutine manages the machine stack itself and must never
// gain a frame: spilling there is rejected with an error instead. Falling back
// to the static register file would be unsound (shared slots under vreg reuse),
// so failing loudly is the only safe option.
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
    // Build a per-subroutine type map: after per-subroutine vreg reuse the same vreg
    // number can have different types in different subroutines, so the global
    // virtualRegisterTypes map is not reliable here.
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
