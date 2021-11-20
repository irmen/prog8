package prog8.ast

import kotlin.math.abs

fun Number.toHex(): String {
    //  0..15 -> "0".."15"
    //  16..255 -> "$10".."$ff"
    //  256..65536 -> "$0100".."$ffff"
    // negative values are prefixed with '-'.
    val integer = this.toInt()
    if(integer<0)
        return '-' + abs(integer).toHex()
    return when (integer) {
        in 0 until 16 -> integer.toString()
        in 0 until 0x100 -> "$"+integer.toString(16).padStart(2,'0')
        in 0 until 0x10000 -> "$"+integer.toString(16).padStart(4,'0')
        else -> throw IllegalArgumentException("number too large for 16 bits $this")
    }
}

fun UInt.toHex(): String {
    //  0..15 -> "0".."15"
    //  16..255 -> "$10".."$ff"
    //  256..65536 -> "$0100".."$ffff"
    return when (this) {
        in 0u until 16u -> this.toString()
        in 0u until 0x100u -> "$"+this.toString(16).padStart(2,'0')
        in 0u until 0x10000u -> "$"+this.toString(16).padStart(4,'0')
        else -> throw IllegalArgumentException("number too large for 16 bits $this")
    }
}
