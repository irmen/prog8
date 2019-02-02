package prog8.compiler.target.c64

import prog8.compiler.CompilationOptions
import prog8.compiler.CompilerException
import prog8.compiler.Zeropage
import prog8.compiler.ZeropageType
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.absoluteValue
import kotlin.math.pow


// 5-byte cbm MFLPT format limitations:
const val FLOAT_MAX_POSITIVE = 1.7014118345e+38         // bytes: 255,127,255,255,255
const val FLOAT_MAX_NEGATIVE = -1.7014118345e+38        // bytes: 255,255,255,255,255

const val BASIC_LOAD_ADDRESS = 0x0801
const val RAW_LOAD_ADDRESS = 0xc000

// the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
const val ESTACK_LO = 0xce00        //  $ce00-$ceff inclusive
const val ESTACK_HI = 0xcf00        //  $cf00-$cfff inclusive


class C64Zeropage(options: CompilationOptions) : Zeropage(options) {

    companion object {
        const val SCRATCH_B1 = 0x02
        const val SCRATCH_REG = 0x03    // temp storage for a register
        const val SCRATCH_REG_X = 0xfa    // temp storage for register X (the evaluation stack pointer)
        const val SCRATCH_W1 = 0xfb     // $fb/$fc
        const val SCRATCH_W2 = 0xfd     // $fd/$fe
    }

    override val exitProgramStrategy: ExitProgramStrategy = when(options.zeropage) {
        ZeropageType.BASICSAFE, ZeropageType.FLOATSAFE -> ExitProgramStrategy.CLEAN_EXIT
        ZeropageType.KERNALSAFE, ZeropageType.FULL -> ExitProgramStrategy.SYSTEM_RESET
    }


    init {
        if(options.floats && options.zeropage!=ZeropageType.FLOATSAFE && options.zeropage!=ZeropageType.BASICSAFE)
            throw CompilerException("when floats are enabled, zero page type should be 'floatsafe' or 'basicsafe'")

        if(options.zeropage == ZeropageType.FULL) {
            free.addAll(0x04 .. 0xf9)
            free.add(0xff)
            free.removeAll(listOf(SCRATCH_B1, SCRATCH_REG, SCRATCH_REG_X, SCRATCH_W1, SCRATCH_W1+1, SCRATCH_W2, SCRATCH_W2+1))
            free.removeAll(listOf(0xa0, 0xa1, 0xa2, 0x91, 0xc0, 0xc5, 0xcb, 0xf5, 0xf6))        // these are updated by IRQ
        } else {
            if(options.zeropage == ZeropageType.KERNALSAFE) {
                // add the Zp addresses that are just used by BASIC routines to the free list
                free.addAll(listOf(0x09, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11,
                        0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20, 0x21,
                        0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46,
                        0x47, 0x48, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x51, 0x52, 0x53, 0x6f, 0x70))
            }
            else if(options.zeropage == ZeropageType.FLOATSAFE) {
                TODO("reserve float zp locations")
            }
            // add the other free Zp addresses
            // these are valid for the C-64 (when no RS232 I/O is performed):
            free.addAll(listOf(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0d, 0x0e,
                    0x94, 0x95, 0xa7, 0xa8, 0xa9, 0xaa,
                    0xb5, 0xb6, 0xf7, 0xf8, 0xf9))
        }
        assert(SCRATCH_B1 !in free)
        assert(SCRATCH_REG !in free)
        assert(SCRATCH_REG_X !in free)
        assert(SCRATCH_W1 !in free)
        assert(SCRATCH_W2 !in free)

        for(reserved in options.zpReserved)
            reserve(reserved)
    }
}


data class Mflpt5(val b0: Short, val b1: Short, val b2: Short, val b3: Short, val b4: Short) {

    companion object {
        const val MemorySize = 5

        val zero = Mflpt5(0, 0,0,0,0)
        fun fromNumber(num: Number): Mflpt5 {
            // see https://en.wikipedia.org/wiki/Microsoft_Binary_Format
            // and https://sourceforge.net/p/acme-crossass/code-0/62/tree/trunk/ACME_Lib/cbm/mflpt.a
            // and https://en.wikipedia.org/wiki/IEEE_754-1985

            val flt = num.toDouble()
            if(flt < FLOAT_MAX_NEGATIVE || flt > FLOAT_MAX_POSITIVE)
                throw CompilerException("floating point number out of 5-byte mflpt range: $this")
            if(flt==0.0)
                return zero

            val sign = if(flt<0.0) 0x80L else 0x00L
            var exponent = 128 + 32	// 128 is cbm's bias, 32 is this algo's bias
            var mantissa = flt.absoluteValue

            // if mantissa is too large, shift right and adjust exponent
            while(mantissa >= 0x100000000) {
                mantissa /= 2.0
                exponent ++
            }
            // if mantissa is too small, shift left and adjust exponent
            while(mantissa < 0x80000000) {
                mantissa *= 2.0
                exponent --
            }

            return when {
                exponent<0 -> zero  // underflow, use zero instead
                exponent>255 -> throw CompilerException("floating point overflow: $this")
                exponent==0 -> zero
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
        if(this == zero) return 0.0
        val exp = b0 - 128
        val sign = (b1.toInt() and 0x80) > 0
        val number = 0x80000000L.or(b1.toLong() shl 24).or(b2.toLong() shl 16).or(b3.toLong() shl 8).or(b4.toLong())
        val result = number.toDouble() * (2.0).pow(exp) / 0x100000000
        return if(sign) -result else result
    }
}

object Charset {
    private val normalImg = ImageIO.read(javaClass.getResource("/charset/c64/charset-normal.png"))
    private val shiftedImg = ImageIO.read(javaClass.getResource("/charset/c64/charset-shifted.png"))

    private fun scanChars(img: BufferedImage): Array<BufferedImage> {

        val transparent = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
        transparent.createGraphics().drawImage(img, 0, 0, null)

        val black = Color(0,0,0).rgb
        val nopixel = Color(0,0,0,0).rgb
        for(y in 0 until transparent.height) {
            for(x in 0 until transparent.width) {
                val col = transparent.getRGB(x, y)
                if(col==black)
                    transparent.setRGB(x, y, nopixel)
            }
        }

        val numColumns = transparent.width / 8
        val charImages = (0..255).map {
            val charX = it % numColumns
            val charY = it/ numColumns
            transparent.getSubimage(charX*8, charY*8, 8, 8)
        }
        return charImages.toTypedArray()
    }

    val normalChars = scanChars(normalImg)
    val shiftedChars = scanChars(shiftedImg)
}
