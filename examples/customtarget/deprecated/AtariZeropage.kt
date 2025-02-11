package prog8.code.target.zp

import prog8.code.core.CompilationOptions
import prog8.code.core.InternalCompilerException
import prog8.code.core.Zeropage
import prog8.code.core.ZeropageType

class AtariZeropage(options: CompilationOptions) : Zeropage(options) {

    override val SCRATCH_B1 = 0xcbu      // temp storage for a single byte
    override val SCRATCH_REG = 0xccu     // temp storage for a register byte, must be B1+1
    override val SCRATCH_W1 = 0xcdu      // temp storage 1 for a word  $cd+$ce
    override val SCRATCH_W2 = 0xcfu      // temp storage 2 for a word  $cf+$d0        TODO is $d0 okay to use?

    init {
        if (options.floats) {
            throw InternalCompilerException("Atari target doesn't yet support floating point routines")
        }

        if (options.floats && options.zeropage !in arrayOf(
                ZeropageType.FLOATSAFE,
                ZeropageType.BASICSAFE,
                ZeropageType.DONTUSE
            ))
            throw InternalCompilerException("when floats are enabled, zero page type should be 'floatsafe' or 'basicsafe' or 'dontuse'")

        when (options.zeropage) {
            ZeropageType.FULL -> {
                // TODO all atari usable zero page locations, except the ones used by the system's IRQ routine
                free.addAll(0x00u..0xffu)
                // TODO atari  free.removeAll(arrayOf(0xa0u, 0xa1u, 0xa2u, 0x91u, 0xc0u, 0xc5u, 0xcbu, 0xf5u, 0xf6u))        // these are updated by IRQ
            }
            ZeropageType.KERNALSAFE -> {
                free.addAll(0x80u..0xffu)       // TODO
            }
            ZeropageType.BASICSAFE,
            ZeropageType.FLOATSAFE -> {
                free.addAll(0x80u..0xffu)       // TODO
                free.removeAll(0xd4u .. 0xefu)      // floating point storage
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
}