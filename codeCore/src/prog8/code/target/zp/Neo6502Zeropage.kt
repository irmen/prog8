package prog8.code.target.zp

import prog8.code.core.*

class Neo6502Zeropage(options: CompilationOptions) : Zeropage(options) {

    override val SCRATCH_B1 = 0xfau      // temp storage for a single byte
    override val SCRATCH_REG = 0xfbu     // temp storage for a register, must be B1+1
    override val SCRATCH_W1 = 0xfcu      // temp storage 1 for a word  $fc+$fd
    override val SCRATCH_W2 = 0xfeu      // temp storage 2 for a word  $fe+$ff

    init {
        if (options.floats) {
            throw InternalCompilerException("Neo6502 target doesn't support floating point routines")
        }

        when (options.zeropage) {
            ZeropageType.DONTUSE -> {
                free.clear()  // don't use zeropage at all
            }
            else -> {
                free.addAll(0x22u..0xffu)
            }
        }

        val distinctFree = free.distinct()
        free.clear()
        free.addAll(distinctFree)

        removeReservedFromFreePool()
        allocateCx16VirtualRegisters()
        retainAllowed()
    }

    override fun allocateCx16VirtualRegisters() {
        // Note: the 16 virtual registers R0-R15 are not regular allocated variables, they're *memory mapped* elsewhere to fixed addresses.
        // However, to be able for the compiler to "see" them as zeropage variables, we have to register them here as well.
        // This is important because the compiler sometimes treats ZP variables more efficiently (for example if it's a pointer)
        for(reg in 0..15) {
            allocatedVariables["cx16.r${reg}"]   = VarAllocation((2+reg*2).toUInt(), DataType.forDt(BaseDataType.UWORD), 2)       // cx16.r0 .. cx16.r15
            allocatedVariables["cx16.r${reg}s"]  = VarAllocation((2+reg*2).toUInt(), DataType.forDt(BaseDataType.WORD), 2)        // cx16.r0s .. cx16.r15s
            allocatedVariables["cx16.r${reg}L"]  = VarAllocation((2+reg*2).toUInt(), DataType.forDt(BaseDataType.UBYTE), 1)       // cx16.r0L .. cx16.r15L
            allocatedVariables["cx16.r${reg}H"]  = VarAllocation((3+reg*2).toUInt(), DataType.forDt(BaseDataType.UBYTE), 1)       // cx16.r0H .. cx16.r15H
            allocatedVariables["cx16.r${reg}sL"] = VarAllocation((2+reg*2).toUInt(), DataType.forDt(BaseDataType.BYTE), 1)        // cx16.r0sL .. cx16.r15sL
            allocatedVariables["cx16.r${reg}sH"] = VarAllocation((3+reg*2).toUInt(), DataType.forDt(BaseDataType.BYTE), 1)        // cx16.r0sH .. cx16.r15sH
        }
    }
}