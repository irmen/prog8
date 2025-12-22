package prog8.code.target.zp

import prog8.code.core.CompilationOptions
import prog8.code.core.DataType
import prog8.code.core.Zeropage
import prog8.code.core.ZeropageType

class ConfigurableZeropage(
    override val SCRATCH_B1: UInt,      // temp storage for a single byte
    override val SCRATCH_REG: UInt,     // temp storage for a register byte, must be B1+1
    override val SCRATCH_W1: UInt,      // temp storage 1 for a word
    override val SCRATCH_W2: UInt,      // temp storage 2 for a word
    override val SCRATCH_PTR: UInt,     // temp storage for a pointer

    val virtualRegistersStart: UInt,        // location of 32 bytes for the r0-r15 virtual registers
    basicsafe: List<UIntRange>,
    kernalsafe: List<UIntRange>,
    fullsafe: List<UIntRange>,
    options: CompilationOptions
) : Zeropage(options) {

    init {
        if (options.floats) {
            TODO("floats in configurable target zp")
        }

        if(SCRATCH_REG!=SCRATCH_B1+1u)
            throw IllegalArgumentException("Zero page scratch variable REG should be B1+1")

        when (options.zeropage) {
            ZeropageType.DONTUSE -> { /* don't use any zeropage at all */ }
            ZeropageType.FULL -> fullsafe.forEach { free.addAll(it) }
            ZeropageType.BASICSAFE -> basicsafe.forEach { free.addAll(it) }
            ZeropageType.KERNALSAFE -> kernalsafe.forEach { free.addAll(it) }
            ZeropageType.FLOATSAFE -> TODO("floatsafe in configurable target zp")
        }

        val distinctFree = free.distinct()
        free.clear()
        free.addAll(distinctFree)

        removeReservedFromFreePool()
        allocateCx16VirtualRegisters()
        retainAllowed()
    }

    private fun allocateCx16VirtualRegisters() {
        // Note: the 16 virtual registers R0-R15 are not regular allocated variables, they're *memory mapped* elsewhere to fixed addresses.
        // However, to be able for the compiler to "see" them as zeropage variables, we have to register them here as well.
        // This is important because the compiler sometimes treats ZP variables more efficiently (for example if it's a pointer)
        for(reg in 0..15) {
            val address = virtualRegistersStart + (2*reg).toUInt()
            if(address<=0xffu) {
                allocatedVariables["cx16.r${reg}"]   = VarAllocation(address, DataType.UWORD, 2)       // cx16.r0 .. cx16.r15
                allocatedVariables["cx16.r${reg}s"]  = VarAllocation(address, DataType.WORD, 2)        // cx16.r0s .. cx16.r15s
                allocatedVariables["cx16.r${reg}L"]  = VarAllocation(address, DataType.UBYTE, 1)       // cx16.r0L .. cx16.r15L
                allocatedVariables["cx16.r${reg}H"]  = VarAllocation(address+1u, DataType.UBYTE, 1)    // cx16.r0H .. cx16.r15H
                allocatedVariables["cx16.r${reg}sL"] = VarAllocation(address, DataType.BYTE, 1)        // cx16.r0sL .. cx16.r15sL
                allocatedVariables["cx16.r${reg}sH"] = VarAllocation(address+1u, DataType.BYTE, 1)     // cx16.r0sH .. cx16.r15sH
                allocatedVariables["cx16.r${reg}bL"] = VarAllocation(address, DataType.BOOL, 1)        // cx16.r0bL .. cx16.r15bL
                allocatedVariables["cx16.r${reg}bH"] = VarAllocation(address+1u, DataType.BOOL, 1)     // cx16.r0bH .. cx16.r15bH
                free.remove(address)
                free.remove(address+1u)
            }
        }

        if(virtualRegistersStart<0xffu) {
            // 32 bits combined register pairs cx16.r0r1 .. cx16.r14r15
            val start = virtualRegistersStart.toInt()
            allocatedVariables["cx16.r0r1sl"] = VarAllocation((start + 0 * 4).toUInt(), DataType.LONG, 4)
            allocatedVariables["cx16.r2r3sl"] = VarAllocation((start + 1 * 4).toUInt(), DataType.LONG, 4)
            allocatedVariables["cx16.r4r5sl"] = VarAllocation((start + 2 * 4).toUInt(), DataType.LONG, 4)
            allocatedVariables["cx16.r6r7sl"] = VarAllocation((start + 3 * 4).toUInt(), DataType.LONG, 4)
            allocatedVariables["cx16.r8r9sl"] = VarAllocation((start + 4 * 4).toUInt(), DataType.LONG, 4)
            allocatedVariables["cx16.r10r11sl"] = VarAllocation((start + 5 * 4).toUInt(), DataType.LONG, 4)
            allocatedVariables["cx16.r12r13sl"] = VarAllocation((start + 6 * 4).toUInt(), DataType.LONG, 4)
            allocatedVariables["cx16.r14r15sl"] = VarAllocation((start + 7 * 4).toUInt(), DataType.LONG, 4)
        }
    }
}