package prog8.vm

internal fun ArrayDeque<UByte>.pushw(value: UShort) {
    add((value and 255u).toUByte())
    add((value.toInt() ushr 8).toUByte())
}

internal fun ArrayDeque<UByte>.pushl(value: Int) {
    val uint = value.toUInt()
    add((uint and 255u).toUByte())
    add((uint shr 8 and 255u).toUByte())
    add((uint shr 16 and 255u).toUByte())
    add((uint shr 24 and 255u).toUByte())
}

internal fun ArrayDeque<UByte>.pushf(value: Double) {
    // push float; lsb first, msb last
    var bits = value.toBits()
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
}

internal fun ArrayDeque<UByte>.popw(): UShort {
    val msb = removeLast()
    val lsb = removeLast()
    return ((msb.toInt() shl 8) + lsb.toInt()).toUShort()
}

internal fun ArrayDeque<UByte>.popl(): Int {
    val b0 = removeLast().toUInt()
    val b1 = removeLast().toUInt()
    val b2 = removeLast().toUInt()
    val b3 = removeLast().toUInt()
    return ((b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3).toInt()
}

internal fun ArrayDeque<UByte>.popf(): Double {
    // pop float; lsb is on bottom, msb on top
    val b0 = removeLast().toLong()
    val b1 = removeLast().toLong()
    val b2 = removeLast().toLong()
    val b3 = removeLast().toLong()
    val b4 = removeLast().toLong()
    val b5 = removeLast().toLong()
    val b6 = removeLast().toLong()
    val b7 = removeLast().toLong()
    val bits = b7 +
            (1L shl 8)*b6 +
            (1L shl 16)*b5 +
            (1L shl 24)*b4 +
            (1L shl 32)*b3 +
            (1L shl 40)*b2 +
            (1L shl 48)*b1 +
            (1L shl 56)*b0
    return Double.fromBits(bits)
}
