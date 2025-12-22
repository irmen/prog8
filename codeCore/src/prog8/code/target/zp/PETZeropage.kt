package prog8.code.target.zp

import prog8.code.core.*


// reference: http://www.zimmers.net/cbmpics/cbm/PETx/petmem.txt

class PETZeropage(options: CompilationOptions) : Zeropage(options) {

    override val SCRATCH_B1 = 0xb3u      // temp storage for a single byte
    override val SCRATCH_REG = 0xb4u     // temp storage for a register byte, must be B1+1
    override val SCRATCH_W1 = 0xb6u      // temp storage 1 for a word
    override val SCRATCH_W2 = 0xb8u      // temp storage 2 for a word
    override val SCRATCH_PTR = 0xb1u     // temp storage for a pointer $b1+$b2

    init {
        if (options.floats && options.zeropage !in arrayOf(
                ZeropageType.FLOATSAFE,
                ZeropageType.BASICSAFE,
                ZeropageType.DONTUSE
            ))
            throw InternalCompilerException("when floats are enabled, zero page type should be 'floatsafe' or 'basicsafe' or 'dontuse'")

        when (options.zeropage) {
            ZeropageType.FULL -> {
                free.addAll(0x00u..0xffu)
                free.removeAll(listOf(0x8du, 0x8eu, 0x8fu, 0x97u, 0x98u, 0x99u, 0x9au, 0x9bu, 0x9eu, 0xa7u, 0xa8u, 0xa9u, 0xaau))        // these are updated/used by IRQ
            }
            ZeropageType.KERNALSAFE -> {
                free.addAll(0x00u..0xffu)
                free.removeAll(listOf(0x8du, 0x8eu, 0x8fu, 0x97u, 0x98u, 0x99u, 0x9au, 0x9bu, 0x9eu, 0xa7u, 0xa8u, 0xa9u, 0xaau))        // these are updated/used by IRQ
            }
            ZeropageType.FLOATSAFE,
            ZeropageType.BASICSAFE -> {
                free.addAll(0xb1u..0xbau)       // TODO more?
            }
            ZeropageType.DONTUSE -> {
                free.clear()  // don't use zeropage at all
            }
        }

        val distinctFree = free.distinct()
        free.clear()
        free.addAll(distinctFree)

        removeReservedFromFreePool()

        if(options.zeropage==ZeropageType.FULL || options.zeropage==ZeropageType.KERNALSAFE) {
            // in these cases there is enough space on the zero page to stick the cx16 virtual registers in there as well.
            allocateCx16VirtualRegisters()
        }

        retainAllowed()
    }

    private fun allocateCx16VirtualRegisters() {
        // Note: the 16 virtual registers R0-R15 are not regular allocated variables, they're *memory mapped* elsewhere to fixed addresses.
        // However, to be able for the compiler to "see" them as zeropage variables, we have to register them here as well.
        // This is important because the compiler sometimes treats ZP variables more efficiently (for example if it's a pointer)
        val base = 0x04     // Unfortunately it cannot be the same as on the Commander X16 ($02).  TODO: is this valid on PET?
        for(reg in 0..15) {
            allocatedVariables["cx16.r${reg}"]   = VarAllocation((base+reg*2).toUInt(), DataType.UWORD, 2)       // cx16.r0 .. cx16.r15
            allocatedVariables["cx16.r${reg}s"]  = VarAllocation((base+reg*2).toUInt(), DataType.WORD, 2)        // cx16.r0s .. cx16.r15s
            allocatedVariables["cx16.r${reg}L"]  = VarAllocation((base+reg*2).toUInt(), DataType.UBYTE, 1)       // cx16.r0L .. cx16.r15L
            allocatedVariables["cx16.r${reg}H"]  = VarAllocation((base+1+reg*2).toUInt(), DataType.UBYTE, 1)     // cx16.r0H .. cx16.r15H
            allocatedVariables["cx16.r${reg}sL"] = VarAllocation((base+reg*2).toUInt(), DataType.BYTE, 1)        // cx16.r0sL .. cx16.r15sL
            allocatedVariables["cx16.r${reg}sH"] = VarAllocation((base+1+reg*2).toUInt(), DataType.BYTE, 1)      // cx16.r0sH .. cx16.r15sH
            allocatedVariables["cx16.r${reg}bL"] = VarAllocation((base+reg*2).toUInt(), DataType.BOOL, 1)        // cx16.r0bL .. cx16.r15bL
            allocatedVariables["cx16.r${reg}bH"] = VarAllocation((base+1+reg*2).toUInt(), DataType.BOOL, 1)      // cx16.r0bH .. cx16.r15bH
            free.remove((base+reg*2).toUInt())
            free.remove((base+1+reg*2).toUInt())
        }

        // 32 bits combined register pairs cx16.r0r1 .. cx16.r14r15
        allocatedVariables["cx16.r0r1sl"]   = VarAllocation((base+0*4).toUInt(), DataType.LONG, 4)
        allocatedVariables["cx16.r2r3sl"]   = VarAllocation((base+1*4).toUInt(), DataType.LONG, 4)
        allocatedVariables["cx16.r4r5sl"]   = VarAllocation((base+2*4).toUInt(), DataType.LONG, 4)
        allocatedVariables["cx16.r6r7sl"]   = VarAllocation((base+3*4).toUInt(), DataType.LONG, 4)
        allocatedVariables["cx16.r8r9sl"]   = VarAllocation((base+4*4).toUInt(), DataType.LONG, 4)
        allocatedVariables["cx16.r10r11sl"] = VarAllocation((base+5*4).toUInt(), DataType.LONG, 4)
        allocatedVariables["cx16.r12r13sl"] = VarAllocation((base+6*4).toUInt(), DataType.LONG, 4)
        allocatedVariables["cx16.r14r15sl"] = VarAllocation((base+7*4).toUInt(), DataType.LONG, 4)
    }
}