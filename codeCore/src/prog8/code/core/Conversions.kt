package prog8.code.core

import kotlin.math.abs
import kotlin.math.pow

val powersOfTwoFloat = (0..16).map { (2.0).pow(it) }.toTypedArray()
val negativePowersOfTwoFloat = powersOfTwoFloat.map { -it }.toTypedArray()
val powersOfTwoInt = (0..16).map { 2.0.pow(it).toInt() }.toTypedArray()

fun Number.toHex(): String {
    //  0..15 -> "0".."15"
    //  16..255 -> "$10".."$ff"
    //  256..65536 -> "$0100".."$ffff"
    //  larger -> "$12345678"
    // negative values are prefixed with '-'.
    val integer = this.toLong()
    if(integer<0) {
        if(integer==-2147483648L)
            return "$80000000"      // the exception to the rule, because -$80000000 is not a valid hex number
        return '-' + abs(integer).toHex()
    }
    return when (integer) {
        in 0 until 16 -> integer.toString()
        in 0 until 0x100 -> "$"+integer.toString(16).padStart(2,'0')
        in 0 until 0x10000 -> "$"+integer.toString(16).padStart(4,'0')
        else -> "$"+integer.toString(16).padStart(8,'0')
    }
}

fun UInt.toHex(): String {
    //  0..15 -> "0".."15"
    //  16..255 -> "$10".."$ff"
    //  256..65536 -> "$0100".."$ffff"
    //  larger -> "$12345678"
    return when (this) {
        in 0u until 16u -> this.toString()
        in 0u until 0x100u -> "$"+this.toString(16).padStart(2,'0')
        in 0u until 0x10000u -> "$"+this.toString(16).padStart(4,'0')
        else -> "$"+this.toString(16).padStart(8,'0')
    }
}

fun Char.escape(): Char = this.toString().escape()[0]

fun String.escape(): String {
    val es = this.map {
        when(it) {
            '\t' -> "\\t"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '"' -> "\\\""
            in '\u8000'..'\u80ff' -> "\\x" + (it.code - 0x8000).toString(16).padStart(2, '0')       // 'ugly' passthrough hack
            in '\u0000'..'\u00ff' -> it.toString()
            else -> "\\u" + it.code.toString(16).padStart(4, '0')
        }
    }
    return es.joinToString("")
}

fun String.unescape(): String {
    val result = mutableListOf<Char>()
    val iter = this.iterator()
    while(iter.hasNext()) {
        val c = iter.nextChar()
        if(c=='\\') {
            val ec = iter.nextChar()
            result.add(when(ec) {
                '\\' -> '\\'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                '"' -> '"'
                '\'' -> '\''
                'u' -> {
                    try {
                        "${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}".toInt(16).toChar()
                    } catch (sb: StringIndexOutOfBoundsException) {
                        throw IllegalArgumentException("invalid \\u escape sequence")
                    } catch (nf: NumberFormatException) {
                        throw IllegalArgumentException("invalid \\u escape sequence")
                    }
                }
                'x' -> {
                    try {
                        val hex = ("" + iter.nextChar() + iter.nextChar()).toInt(16)
                        (0x8000 + hex).toChar()         // 'ugly' pass-through hack
                    } catch (sb: StringIndexOutOfBoundsException) {
                        throw IllegalArgumentException("invalid \\x escape sequence")
                    } catch (nf: NumberFormatException) {
                        throw IllegalArgumentException("invalid \\x escape sequence")
                    }
                }
                else -> throw IllegalArgumentException("invalid escape char in string: \\$ec")
            })
        } else {
            result.add(c)
        }
    }
    return result.joinToString("")
}

// === Symbol name prefixing ===
// These are used by code generators to add p8b_/p8s_/p8v_/etc. prefixes to symbol names.
// The prefix character depends on the symbol type (block='b', sub='s', variable='v', etc.)
// Scoped names (containing dots) have each part prefixed according to its role.

fun prefixScopedName(name: String, type: Char): String {
    val generatedPrefix = "p8_label_gen_"
    if('.' !in name) {
        if(name.startsWith(generatedPrefix))
            return name
        return "p8${type}_$name"
    }
    val parts = name.split('.')
    val firstPrefixed = "p8b_${parts[0]}"
    val lastPart = parts.last()
    val lastPrefixed = if(lastPart.startsWith(generatedPrefix)) lastPart else "p8${type}_$lastPart"
    val inbetweenPrefixed = parts.drop(1).dropLast(1).map{ "p8s_$it" }
    val prefixed = listOf(firstPrefixed) + inbetweenPrefixed + listOf(lastPrefixed)
    return prefixed.joinToString(".")
}
