package prog8.compiler.target.c64

import prog8.compiler.*
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.absoluteValue
import kotlin.math.pow

object MachineDefinition {

    // 5-byte cbm MFLPT format limitations:
    const val FLOAT_MAX_POSITIVE = 1.7014118345e+38         // bytes: 255,127,255,255,255
    const val FLOAT_MAX_NEGATIVE = -1.7014118345e+38        // bytes: 255,255,255,255,255

    const val BASIC_LOAD_ADDRESS = 0x0801
    const val RAW_LOAD_ADDRESS = 0xc000

    // the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
    // and some heavily used string constants derived from the two values above
    const val ESTACK_LO_VALUE       = 0xce00        //  $ce00-$ceff inclusive
    const val ESTACK_HI_VALUE       = 0xcf00        //  $cf00-$cfff inclusive
    const val ESTACK_LO_HEX         = "\$ce00"
    const val ESTACK_LO_PLUS1_HEX   = "\$ce01"
    const val ESTACK_LO_PLUS2_HEX   = "\$ce02"
    const val ESTACK_HI_HEX         = "\$cf00"
    const val ESTACK_HI_PLUS1_HEX   = "\$cf01"
    const val ESTACK_HI_PLUS2_HEX   = "\$cf02"


    class C64Zeropage(options: CompilationOptions) : Zeropage(options) {

        companion object {
            const val SCRATCH_B1 = 0x02
            const val SCRATCH_REG = 0x03    // temp storage for a register
            const val SCRATCH_REG_X = 0xfa    // temp storage for register X (the evaluation stack pointer)
            const val SCRATCH_W1 = 0xfb     // $fb+$fc
            const val SCRATCH_W2 = 0xfd     // $fd+$fe
        }

        override val exitProgramStrategy: ExitProgramStrategy = when (options.zeropage) {
            ZeropageType.BASICSAFE -> ExitProgramStrategy.CLEAN_EXIT
            ZeropageType.FLOATSAFE, ZeropageType.KERNALSAFE, ZeropageType.FULL -> ExitProgramStrategy.SYSTEM_RESET
        }


        init {
            if (options.floats && options.zeropage != ZeropageType.FLOATSAFE && options.zeropage != ZeropageType.BASICSAFE)
                throw CompilerException("when floats are enabled, zero page type should be 'floatsafe' or 'basicsafe'")

            if (options.zeropage == ZeropageType.FULL) {
                free.addAll(0x04..0xf9)
                free.add(0xff)
                free.removeAll(listOf(SCRATCH_B1, SCRATCH_REG, SCRATCH_REG_X, SCRATCH_W1, SCRATCH_W1 + 1, SCRATCH_W2, SCRATCH_W2 + 1))
                free.removeAll(listOf(0xa0, 0xa1, 0xa2, 0x91, 0xc0, 0xc5, 0xcb, 0xf5, 0xf6))        // these are updated by IRQ
            } else {
                if (options.zeropage == ZeropageType.KERNALSAFE || options.zeropage == ZeropageType.FLOATSAFE) {
                    free.addAll(listOf(0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11,
                            0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20, 0x21,
                            0x22, 0x23, 0x24, 0x25,
                            0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46,
                            0x47, 0x48, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x51, 0x52, 0x53,
                            0x57, 0x58, 0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f, 0x60,
                            0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
                            0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70, 0x71, 0x72,
                            0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x7b, 0x7c,
                            0x7d, 0x7e, 0x7f, 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a,
                            0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0xff
                            // 0x90-0xfa is 'kernel work storage area'
                    ))
                }

                if (options.zeropage == ZeropageType.FLOATSAFE) {
                    // remove the zero page locations used for floating point operations from the free list
                    free.removeAll(listOf(
                            0x12, 0x26, 0x27, 0x28, 0x29, 0x2a,
                            0x57, 0x58, 0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f, 0x60,
                            0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
                            0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70, 0x71, 0x72,
                            0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0xf
                    ))
                }

                // add the other free Zp addresses,
                // these are valid for the C-64 (when no RS232 I/O is performed) but to keep BASIC running fully:
                free.addAll(listOf(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0d, 0x0e,
                        0x94, 0x95, 0xa7, 0xa8, 0xa9, 0xaa,
                        0xb5, 0xb6, 0xf7, 0xf8, 0xf9))
            }
            assert(SCRATCH_B1 !in free)
            assert(SCRATCH_REG !in free)
            assert(SCRATCH_REG_X !in free)
            assert(SCRATCH_W1 !in free)
            assert(SCRATCH_W2 !in free)

            for (reserved in options.zpReserved)
                reserve(reserved)
        }
    }


    data class Mflpt5(val b0: Short, val b1: Short, val b2: Short, val b3: Short, val b4: Short) {

        companion object {
            const val MemorySize = 5

            val zero = Mflpt5(0, 0, 0, 0, 0)
            fun fromNumber(num: Number): Mflpt5 {
                // see https://en.wikipedia.org/wiki/Microsoft_Binary_Format
                // and https://sourceforge.net/p/acme-crossass/code-0/62/tree/trunk/ACME_Lib/cbm/mflpt.a
                // and https://en.wikipedia.org/wiki/IEEE_754-1985

                val flt = num.toDouble()
                if (flt < FLOAT_MAX_NEGATIVE || flt > FLOAT_MAX_POSITIVE)
                    throw CompilerException("floating point number out of 5-byte mflpt range: $this")
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
                    exponent > 255 -> throw CompilerException("floating point overflow: $this")
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

        fun toDouble(): Double {
            if (this == zero) return 0.0
            val exp = b0 - 128
            val sign = (b1.toInt() and 0x80) > 0
            val number = 0x80000000L.or(b1.toLong() shl 24).or(b2.toLong() shl 16).or(b3.toLong() shl 8).or(b4.toLong())
            val result = number.toDouble() * (2.0).pow(exp) / 0x100000000
            return if (sign) -result else result
        }
    }

    object Charset {
        private val normalImg = ImageIO.read(javaClass.getResource("/charset/c64/charset-normal.png"))
        private val shiftedImg = ImageIO.read(javaClass.getResource("/charset/c64/charset-shifted.png"))

        private fun scanChars(img: BufferedImage): Array<BufferedImage> {

            val transparent = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
            transparent.createGraphics().drawImage(img, 0, 0, null)

            val black = Color(0, 0, 0).rgb
            val nopixel = Color(0, 0, 0, 0).rgb
            for (y in 0 until transparent.height) {
                for (x in 0 until transparent.width) {
                    val col = transparent.getRGB(x, y)
                    if (col == black)
                        transparent.setRGB(x, y, nopixel)
                }
            }

            val numColumns = transparent.width / 8
            val charImages = (0..255).map {
                val charX = it % numColumns
                val charY = it / numColumns
                transparent.getSubimage(charX * 8, charY * 8, 8, 8)
            }
            return charImages.toTypedArray()
        }

        val normalChars = scanChars(normalImg)
        val shiftedChars = scanChars(shiftedImg)

        private val coloredNormalChars = mutableMapOf<Short, Array<BufferedImage>>()

        fun getColoredChar(screenCode: Short, color: Short): BufferedImage {
            val colorIdx = (color % colorPalette.size).toShort()
            val chars = coloredNormalChars[colorIdx]
            if (chars != null)
                return chars[screenCode.toInt()]

            val coloredChars = mutableListOf<BufferedImage>()
            val transparent = Color(0, 0, 0, 0).rgb
            val rgb = colorPalette[colorIdx.toInt()].rgb
            for (c in normalChars) {
                val colored = c.copy()
                for (y in 0 until colored.height)
                    for (x in 0 until colored.width) {
                        if (colored.getRGB(x, y) != transparent) {
                            colored.setRGB(x, y, rgb)
                        }
                    }
                coloredChars.add(colored)
            }
            coloredNormalChars[colorIdx] = coloredChars.toTypedArray()
            return coloredNormalChars.getValue(colorIdx)[screenCode.toInt()]
        }

    }

    private fun BufferedImage.copy(): BufferedImage {
        val bcopy = BufferedImage(this.width, this.height, this.type)
        val g = bcopy.graphics
        g.drawImage(this, 0, 0, null)
        g.dispose()
        return bcopy
    }


    val colorPalette = listOf(         // this is Pepto's Commodore-64 palette  http://www.pepto.de/projects/colorvic/
            Color(0x000000),  // 0 = black
            Color(0xFFFFFF),  // 1 = white
            Color(0x813338),  // 2 = red
            Color(0x75cec8),  // 3 = cyan
            Color(0x8e3c97),  // 4 = purple
            Color(0x56ac4d),  // 5 = green
            Color(0x2e2c9b),  // 6 = blue
            Color(0xedf171),  // 7 = yellow
            Color(0x8e5029),  // 8 = orange
            Color(0x553800),  // 9 = brown
            Color(0xc46c71),  // 10 = light red
            Color(0x4a4a4a),  // 11 = dark grey
            Color(0x7b7b7b),  // 12 = medium grey
            Color(0xa9ff9f),  // 13 = light green
            Color(0x706deb),  // 14 = light blue
            Color(0xb2b2b2)   // 15 = light grey
    )

}
