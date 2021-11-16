package prog8.compiler.target.c64

import prog8.compiler.target.cbm.viceMonListPostfix
import prog8.compilerinterface.*
import java.io.IOException
import java.nio.file.Path
import kotlin.math.absoluteValue
import kotlin.math.pow

object C64MachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU6502

    // 5-byte cbm MFLPT format limitations:
    override val FLOAT_MAX_POSITIVE = 1.7014118345e+38         // bytes: 255,127,255,255,255
    override val FLOAT_MAX_NEGATIVE = -1.7014118345e+38        // bytes: 255,255,255,255,255
    override val FLOAT_MEM_SIZE = 5
    override val POINTER_MEM_SIZE = 2
    override val BASIC_LOAD_ADDRESS = 0x0801
    override val RAW_LOAD_ADDRESS = 0xc000

    // the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
    override val ESTACK_LO = 0xce00        //  $ce00-$ceff inclusive
    override val ESTACK_HI = 0xcf00        //  $ce00-$ceff inclusive

    override lateinit var zeropage: Zeropage

    override fun getFloat(num: Number) = Mflpt5.fromNumber(num)

    override fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String> {
        return if (compilerOptions.launcher == LauncherType.BASIC || compilerOptions.output == OutputType.PRG)
            listOf("syslib")
        else
            emptyList()
    }

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path) {
        if(selectedEmulator!=1) {
            System.err.println("The c64 target only supports the main emulator (Vice).")
            return
        }

        for(emulator in listOf("x64sc", "x64")) {
            println("\nStarting C-64 emulator $emulator...")
            val cmdline = listOf(emulator, "-silent", "-moncommands", "${programNameWithPath}.$viceMonListPostfix",
                    "-autostartprgmode", "1", "-autostart-warp", "-autostart", "${programNameWithPath}.prg")
            val processb = ProcessBuilder(cmdline).inheritIO()
            val process: Process
            try {
                process=processb.start()
            } catch(x: IOException) {
                continue  // try the next emulator executable
            }
            process.waitFor()
            break
        }
    }

    override fun isRegularRAMaddress(address: Int): Boolean = address<0xa000 || address in 0xc000..0xcfff

    override fun initializeZeropage(compilerOptions: CompilationOptions) {
        zeropage = C64Zeropage(compilerOptions)
    }

    // 6502 opcodes (including aliases and illegal opcodes), these cannot be used as variable or label names
    override val opcodeNames = setOf("adc", "ahx", "alr", "anc", "and", "ane", "arr", "asl", "asr", "axs", "bcc", "bcs",
            "beq", "bge", "bit", "blt", "bmi", "bne", "bpl", "brk", "bvc", "bvs", "clc",
            "cld", "cli", "clv", "cmp", "cpx", "cpy", "dcm", "dcp", "dec", "dex", "dey",
            "eor", "gcc", "gcs", "geq", "gge", "glt", "gmi", "gne", "gpl", "gvc", "gvs",
            "inc", "ins", "inx", "iny", "isb", "isc", "jam", "jmp", "jsr", "lae", "las",
            "lax", "lda", "lds", "ldx", "ldy", "lsr", "lxa", "nop", "ora", "pha", "php",
            "pla", "plp", "rla", "rol", "ror", "rra", "rti", "rts", "sax", "sbc", "sbx",
            "sec", "sed", "sei", "sha", "shl", "shr", "shs", "shx", "shy", "slo", "sre",
            "sta", "stx", "sty", "tas", "tax", "tay", "tsx", "txa", "txs", "tya", "xaa")


    class C64Zeropage(options: CompilationOptions) : Zeropage(options) {

        override val SCRATCH_B1 = 0x02      // temp storage for a single byte
        override val SCRATCH_REG = 0x03     // temp storage for a register, must be B1+1
        override val SCRATCH_W1 = 0xfb      // temp storage 1 for a word  $fb+$fc
        override val SCRATCH_W2 = 0xfd      // temp storage 2 for a word  $fb+$fc


        init {
            if (options.floats && options.zeropage !in arrayOf(ZeropageType.FLOATSAFE, ZeropageType.BASICSAFE, ZeropageType.DONTUSE ))
                throw InternalCompilerException("when floats are enabled, zero page type should be 'floatsafe' or 'basicsafe' or 'dontuse'")

            if (options.zeropage == ZeropageType.FULL) {
                free.addAll(0x04..0xf9)
                free.add(0xff)
                free.removeAll(setOf(0xa0, 0xa1, 0xa2, 0x91, 0xc0, 0xc5, 0xcb, 0xf5, 0xf6))        // these are updated by IRQ
            } else {
                if (options.zeropage == ZeropageType.KERNALSAFE || options.zeropage == ZeropageType.FLOATSAFE) {
                    free.addAll(listOf(
                            0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11,
                            0x16, 0x17, 0x18, 0x19, 0x1a,
                            0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20, 0x21,
                            0x22, 0x23, 0x24, 0x25,
                            0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46,
                            0x47, 0x48, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x51, 0x52, 0x53,
                            0x57, 0x58, 0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f, 0x60,
                            0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
                            0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70, 0x71, 0x72,
                            0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x7b, 0x7c,
                            0x7d, 0x7e, 0x7f, 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a,
                            0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0xff
                            // 0x90-0xfa is 'kernal work storage area'
                    ))
                }

                if (options.zeropage == ZeropageType.FLOATSAFE) {
                    // remove the zeropage locations used for floating point operations from the free list
                    free.removeAll(setOf(
                            0x22, 0x23, 0x24, 0x25,
                            0x10, 0x11, 0x12, 0x26, 0x27, 0x28, 0x29, 0x2a,
                            0x57, 0x58, 0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f, 0x60,
                            0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
                            0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70, 0x71, 0x72,
                            0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0xff
                    ))
                }

                if(options.zeropage!= ZeropageType.DONTUSE) {
                    // add the free Zp addresses
                    // these are valid for the C-64 but allow BASIC to keep running fully *as long as you don't use tape I/O*
                    free.addAll(listOf(0x04, 0x05, 0x06, 0x0a, 0x0e,
                            0x92, 0x96, 0x9b, 0x9c, 0x9e, 0x9f, 0xa5, 0xa6,
                            0xb0, 0xb1, 0xbe, 0xbf, 0xf9))
                } else {
                    // don't use the zeropage at all
                    free.clear()
                }
            }

            removeReservedFromFreePool()
        }
    }

    data class Mflpt5(val b0: Short, val b1: Short, val b2: Short, val b3: Short, val b4: Short):
        IMachineFloat {

        companion object {
            val zero = Mflpt5(0, 0, 0, 0, 0)
            fun fromNumber(num: Number): Mflpt5 {
                // see https://en.wikipedia.org/wiki/Microsoft_Binary_Format
                // and https://sourceforge.net/p/acme-crossass/code-0/62/tree/trunk/ACME_Lib/cbm/mflpt.a
                // and https://en.wikipedia.org/wiki/IEEE_754-1985

                val flt = num.toDouble()
                if (flt < FLOAT_MAX_NEGATIVE || flt > FLOAT_MAX_POSITIVE)
                    throw InternalCompilerException("floating point number out of 5-byte mflpt range: $this")
                if (flt == 0.0)
                    return zero

                val sign = if (flt < 0.0) 0x80L else 0x00L
                var exponent = 128 + 32    // 128 is cbm's bias, 32 is this algo's bias
                var mantissa = flt.absoluteValue

                // if mantissa is too large, shift right and adjust exponent
                while (mantissa >= 0x100000000) {
                    mantissa /= 2.0
                    exponent++
                }
                // if mantissa is too small, shift left and adjust exponent
                while (mantissa < 0x80000000) {
                    mantissa *= 2.0
                    exponent--
                }

                return when {
                    exponent < 0 -> zero  // underflow, use zero instead
                    exponent > 255 -> throw InternalCompilerException("floating point overflow: $this")
                    exponent == 0 -> zero
                    else -> {
                        val mantLong = mantissa.toLong()
                        Mflpt5(
                                exponent.toShort(),
                                (mantLong.and(0x7f000000L) ushr 24).or(sign).toShort(),
                                (mantLong.and(0x00ff0000L) ushr 16).toShort(),
                                (mantLong.and(0x0000ff00L) ushr 8).toShort(),
                                (mantLong.and(0x000000ffL)).toShort())
                    }
                }
            }
        }

        override fun toDouble(): Double {
            if (this == zero) return 0.0
            val exp = b0 - 128
            val sign = (b1.toInt() and 0x80) > 0
            val number = 0x80000000L.or(b1.toLong() shl 24).or(b2.toLong() shl 16).or(b3.toLong() shl 8).or(b4.toLong())
            val result = number.toDouble() * (2.0).pow(exp) / 0x100000000
            return if (sign) -result else result
        }

        override fun makeFloatFillAsm(): String {
            val b0 = "$" + b0.toString(16).padStart(2, '0')
            val b1 = "$" + b1.toString(16).padStart(2, '0')
            val b2 = "$" + b2.toString(16).padStart(2, '0')
            val b3 = "$" + b3.toString(16).padStart(2, '0')
            val b4 = "$" + b4.toString(16).padStart(2, '0')
            return "$b0, $b1, $b2, $b3, $b4"
        }
    }
}
