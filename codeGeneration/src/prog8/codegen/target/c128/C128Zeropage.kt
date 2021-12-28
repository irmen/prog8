package prog8.codegen.target.c128

import prog8.compilerinterface.CompilationOptions
import prog8.compilerinterface.InternalCompilerException
import prog8.compilerinterface.Zeropage
import prog8.compilerinterface.ZeropageType

class C128Zeropage(options: CompilationOptions) : Zeropage(options) {

    override val SCRATCH_B1 = 0x9bu      // temp storage for a single byte
    override val SCRATCH_REG = 0x9cu     // temp storage for a register, must be B1+1
    override val SCRATCH_W1 = 0xfbu      // temp storage 1 for a word  $fb+$fc
    override val SCRATCH_W2 = 0xfdu      // temp storage 2 for a word  $fd+$fe


    init {
        if (options.floats && options.zeropage !in arrayOf(
                ZeropageType.FLOATSAFE,
                ZeropageType.BASICSAFE,
                ZeropageType.DONTUSE
            ))
            throw InternalCompilerException("when floats are enabled, zero page type should be 'floatsafe' or 'basicsafe' or 'dontuse'")

        when (options.zeropage) {
            ZeropageType.FULL -> {
                // TODO all c128 usable zero page locations, except the ones used by the system's IRQ routine
                free.addAll(0x0au..0xffu)       // TODO c128 what about $02-$09?
                free.removeAll(setOf(SCRATCH_B1, SCRATCH_REG, SCRATCH_W1, SCRATCH_W1+1u, SCRATCH_W2, SCRATCH_W2+1u))
                // TODO c128  free.removeAll(setOf(0xa0u, 0xa1u, 0xa2u, 0x91u, 0xc0u, 0xc5u, 0xcbu, 0xf5u, 0xf6u))        // these are updated by IRQ
            }
            ZeropageType.KERNALSAFE,
            ZeropageType.FLOATSAFE,
            ZeropageType.BASICSAFE -> {
                free.clear()   // TODO c128 usable zero page addresses
            }
            ZeropageType.DONTUSE -> {
                free.clear()  // don't use zeropage at all
            }
        }

        removeReservedFromFreePool()
    }
}