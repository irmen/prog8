package prog8.code.target

import prog8.code.core.InternalCompilerException
import kotlin.math.absoluteValue
import kotlin.math.pow

data class Mflpt5(val b0: UByte, val b1: UByte, val b2: UByte, val b3: UByte, val b4: UByte) {

    companion object {
        const val FLOAT_MAX_POSITIVE = 1.7014118345e+38         // bytes: 255,127,255,255,255
        const val FLOAT_MAX_NEGATIVE = -1.7014118345e+38        // bytes: 255,255,255,255,255
        const val FLOAT_MEM_SIZE = 5

        val zero = Mflpt5(0u, 0u, 0u, 0u, 0u)
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
                        exponent.toUByte(),
                        (mantLong.and(0x7f000000L) ushr 24).or(sign).toUByte(),
                        (mantLong.and(0x00ff0000L) ushr 16).toUByte(),
                        (mantLong.and(0x0000ff00L) ushr 8).toUByte(),
                        (mantLong.and(0x000000ffL)).toUByte()
                    )
                }
            }
        }
    }

    fun toDouble(): Double {
        if (this == zero) return 0.0
        val exp = b0.toInt() - 128
        val sign = (b1.toInt() and 0x80) > 0
        val number = 0x80000000L.or(b1.toLong() shl 24).or(b2.toLong() shl 16).or(b3.toLong() shl 8).or(b4.toLong())
        val result = number.toDouble() * (2.0).pow(exp) / 0x100000000
        return if (sign) -result else result
    }

    fun makeFloatFillAsm(): String {
        val b0 = "$" + b0.toString(16).padStart(2, '0')
        val b1 = "$" + b1.toString(16).padStart(2, '0')
        val b2 = "$" + b2.toString(16).padStart(2, '0')
        val b3 = "$" + b3.toString(16).padStart(2, '0')
        val b4 = "$" + b4.toString(16).padStart(2, '0')
        return "$b0, $b1, $b2, $b3, $b4"
    }
}