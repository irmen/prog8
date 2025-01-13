package prog8.code.target.zp

import prog8.code.core.CompilationOptions
import prog8.code.core.InternalCompilerException
import prog8.code.core.Zeropage
import prog8.code.core.ZeropageType


// reference: http://www.zimmers.net/cbmpics/cbm/PETx/petmem.txt

class PETZeropage(options: CompilationOptions) : Zeropage(options) {

    override val SCRATCH_B1 = 0xb3u      // temp storage for a single byte
    override val SCRATCH_REG = 0xb4u     // temp storage for a register, must be B1+1
    override val SCRATCH_W1 = 0xb6u      // temp storage 1 for a word
    override val SCRATCH_W2 = 0xb8u      // temp storage 2 for a word

    init {
        if (options.floats) {
            throw InternalCompilerException("PET target doesn't yet support floating point routines")
        }

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
                free.addAll(0xb3u..0xbau)       // TODO more?
            }
            ZeropageType.DONTUSE -> {
                free.clear()  // don't use zeropage at all
            }
        }

        val distinctFree = free.distinct()
        free.clear()
        free.addAll(distinctFree)

        removeReservedFromFreePool()
        retainAllowed()
    }

    override fun allocateCx16VirtualRegisters() {
        TODO("Not known if PET can put the virtual regs in ZP")
    }
}