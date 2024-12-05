package prog8.code.target.c128

import prog8.code.core.CompilationOptions
import prog8.code.core.InternalCompilerException
import prog8.code.core.Zeropage
import prog8.code.core.ZeropageType


// reference: "Mapping the C128" zeropage chapter.

class C128Zeropage(options: CompilationOptions) : Zeropage(options) {

    override val SCRATCH_B1 = 0x74u      // temp storage for a single byte
    override val SCRATCH_REG = 0x75u     // temp storage for a register, must be B1+1
    override val SCRATCH_W1 = 0xfbu      // temp storage 1 for a word  $fb+$fc
    override val SCRATCH_W2 = 0xfdu      // temp storage 2 for a word  $fd+$fe

    init {
        if (options.floats) {
            throw InternalCompilerException("C128 target doesn't yet support floating point routines")
            // note: in git commit labeled 'c128: remove floats module' the floats.p8 and floats.asm files are removed,
            //       they could be retrieved again at a later time if the compiler somehow *does* store the fp variables in bank1.
        }

        if (options.floats && options.zeropage !in arrayOf(
                ZeropageType.FLOATSAFE,
                ZeropageType.BASICSAFE,
                ZeropageType.DONTUSE
            ))
            throw InternalCompilerException("when floats are enabled, zero page type should be 'floatsafe' or 'basicsafe' or 'dontuse'")

        when (options.zeropage) {
            ZeropageType.FULL -> {
                // $00/$01 are data port IO registers, // $02-$09 are storage locations for JSRFAR and such
                free.addAll(0x0au..0xffu)
                free.removeAll(arrayOf(0x90u, 0x91u, 0xa0u, 0xa1u, 0xa2u, 0xc0u, 0xccu, 0xcdu, 0xd0u, 0xd1u, 0xd2u, 0xd3u, 0xd4u, 0xd5u, 0xf7u))        // these are updated/used by IRQ
            }
            ZeropageType.KERNALSAFE -> {
                free.addAll(0x0au..0x8fu)       // BASIC variables
                free.addAll(arrayOf(0x92u, 0x96u, 0x9bu, 0x9cu, 0x9eu, 0x9fu, 0xa4u, 0xa7u, 0xa8u, 0xa9u, 0xaau, 0xabu,
                    0xb0u, 0xb1u, 0xb4u, 0xb5u, 0xb6u))
            }
            ZeropageType.FLOATSAFE,
            ZeropageType.BASICSAFE -> {
                free.addAll(arrayOf(0x0bu, 0x0cu, 0x0du, 0x0eu, 0x0fu, 0x10u, 0x11u, 0x12u, 0x16u, 0x17u, 0x18u, 0x19u, 0x1au))
                free.addAll(0x1bu..0x23u)
                free.addAll(arrayOf(0x3fu, 0x40u, 0x41u, 0x42u, 0x43u, 0x44u, 0x47u, 0x48u, 0x49u, 0x4au, 0x4bu, 0x4cu, 0x4fu,
                    0x55u, 0x56u, 0x57u, 0x58u,
                    0x74u, 0x75u, 0x78u, 0x80u, 0x83u, 0x87u, 0x88u, 0x89u, 0x8au, 0x8bu, 0x8cu, 0x8du, 0x8eu, 0x8fu,
                    0x92u, 0x96u, 0x9bu, 0x9cu, 0x9eu, 0x9fu, 0xa4u, 0xa7u, 0xa8u, 0xa9u, 0xaau, 0xabu,
                    0xb0u, 0xb1u, 0xb4u, 0xb5u, 0xb6u
                ))

                // if(options.zeropage==ZeropageType.BASICSAFE) {
                    // can also clobber the FP locations (unconditionally, because the C128 target doesn't support floating point calculations in prog8 at this time0
                    free.addAll(arrayOf(0x14u, 0x28u, 0x29u, 0x2au, 0x2bu, 0x2cu,
                        0x50u, 0x51u, 0x52u, 0x53u, 0x54u, 0x59u, 0x5au, 0x5bu, 0x5cu, 0x5du, 0x5eu, 0x5fu, 0x60u, 0x61u, 0x62u,
                        0x63u, 0x64u, 0x65u, 0x66u, 0x67u, 0x68u,
                        0x6au, 0x6bu, 0x6cu, 0x6du, 0x6eu, 0x6fu, 0x71u))
                // }
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
        TODO("Not known if C128 can put the virtual regs in ZP")
    }
}