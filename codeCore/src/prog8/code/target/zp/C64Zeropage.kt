package prog8.code.target.zp

import prog8.code.core.*


class C64Zeropage(options: CompilationOptions) : Zeropage(options) {

    override val SCRATCH_B1 = 0x02u      // temp storage for a single byte
    override val SCRATCH_REG = 0x03u     // temp storage for a register byte, must be B1+1
    override val SCRATCH_W1 = 0xfbu      // temp storage 1 for a word  $fb+$fc
    override val SCRATCH_W2 = 0xfdu      // temp storage 2 for a word  $fd+$fe
    override val SCRATCH_PTR = 0x04u     // temp storage for a pointer $04+$05


    init {
        if (options.floats && options.zeropage !in arrayOf(
                ZeropageType.FLOATSAFE,
                ZeropageType.BASICSAFE,
                ZeropageType.DONTUSE
            ))
            throw InternalCompilerException("when floats are enabled, zero page type should be 'floatsafe' or 'basicsafe' or 'dontuse'")

        if (options.zeropage == ZeropageType.FULL) {
            free.addAll(0x02u..0xffu)
            free.removeAll(arrayOf(0xa0u, 0xa1u, 0xa2u, 0x91u, 0xc0u, 0xc5u, 0xcbu, 0xf5u, 0xf6u))        // these are updated by IRQ
        } else {
            if (options.zeropage == ZeropageType.KERNALSAFE || options.zeropage == ZeropageType.FLOATSAFE) {
                free.addAll(arrayOf(
                        0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
                        0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f,
                        0x20, 0x21, 0x22, 0x23, 0x24, 0x25,
                        0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46,
                        0x47, 0x48, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x51, 0x52, 0x53,
                        0x57, 0x58, 0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f, 0x60,
                        0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
                        0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70, 0x71, 0x72,
                        0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x7b, 0x7c,
                        0x7d, 0x7e, 0x7f, 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a,
                        0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0xff
                        // 0x90-0xfa is 'kernal work storage area'
                ).map{it.toUInt()})
            }

            if (options.zeropage == ZeropageType.FLOATSAFE) {
                // remove the zeropage locations used for floating point operations from the free list
                free.removeAll(arrayOf(
                        0x03, 0x04, 0x05, 0x06, 0x10, 0x11, 0x12,
                        0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2a,
                        0x57, 0x58, 0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f, 0x60,
                        0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
                        0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70, 0x71, 0x72,
                        0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0xff
                ).map{it.toUInt()})
            }

            if(options.zeropage != ZeropageType.DONTUSE) {
                // add the free Zp addresses
                // these are valid for the C-64 but allow BASIC to keep running fully *as long as you don't use tape I/O*
                free.addAll(arrayOf(0x02, 0x03, 0x04, 0x05, 0x06, 0x0a, 0x0e,
                        0x92, 0x96, 0x9c, 0x9e, 0x9f, 0xa6,
                        0xb0, 0xb1, 0xbe, 0xbf, 0xf9).map{it.toUInt()})
            } else {
                // don't use the zeropage at all
                free.clear()
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
        val base = 0x04     // Unfortunately it cannot be the same as on the Commander X16 ($02).
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