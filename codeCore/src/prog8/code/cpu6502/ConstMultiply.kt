package prog8.code.cpu6502

// Horner ("shift-add") expansion of a multiply by a small compile-time constant, for the 6502 and
// 65C02 backends. The runtime routines prog8_math.multiply_words / multiply_bytes cost the same
// whatever the constant is (~442 and ~130 cycles), while this expansion costs O(bit length), so
// for a small constant inlining wins. It generalises the hand-written prog8_math.mul_word_N /
// mul_byte_N routines, which are themselves unrolled Horner chains (mul_word_3 is "AY = AY*2 + AY",
// one Horner step for 11).
//
// The sequences live here rather than in either backend because the register bookkeeping is easy to
// get subtly wrong and the two backends must not drift apart.
//
// The gate is on generated size, not on the constant's magnitude: cycles alone would argue for
// expanding nearly every multiply and bloating every program. Budgets are the max instruction count
// the expansion may take; the call sequence each replaces is 6 instructions.

private const val WORD_BUDGET = 32
private const val BYTE_BUDGET = 24

// --- byte ---
// Contract: A holds the multiplicand. Result in A. Only A and one scratch byte are live, so there
// is no second register to keep in sync (unlike the word case, where a bare doubling leaves the
// high byte stale in Y).

fun byteShiftAddExpansion(value: Int): List<String>? {
    if (value < 3 || value > 255)
        return null
    val bitlen = 31 - Integer.numberOfLeadingZeros(value)
    val popcount = Integer.bitCount(value)
    if (1 + (bitlen-1) + 2*(popcount-1) > BYTE_BUDGET)
        return null
    val result = ArrayList<String>(1 + 3*bitlen)
    result.add("sta  P8ZP_SCRATCH_REG")                     // keep x for the conditional adds
    for (bit in (bitlen-1) downTo 0) {
        result.add("asl  a")                                // r = r*2
        if (value and (1 shl bit) != 0) {                   // r += x
            result.add("clc")
            result.add("adc  P8ZP_SCRATCH_REG")
        }
    }
    return result
}

// --- word ---
// Contract on entry: A = r.lo, Y = r.hi, P8ZP_SCRATCH_W1 = r, P8ZP_SCRATCH_W2 = x (all 16-bit,
// little-endian: byte 0 is the low byte). On exit: A = result.lo, Y = result.hi.
//
// Two things this sequence must get right, both learned the hard way:
//  * the add step must store the new high byte back to W1+1 as well as to Y. Writing it only to Y
//    leaves W1+1 stale, and the next step's 'rol P8ZP_SCRATCH_W1+1' would then double the wrong
//    high byte.
//  * a trailing bare doubling (any even constant) updates A and W1+1 but not Y, so Y needs an
//    explicit reload at the end. It cannot be 'tay', because at that point A holds the low byte.

fun wordShiftAddExpansion(value: Int): List<String>? {
    if (value < 3)
        return null
    val bitlen = 31 - Integer.numberOfLeadingZeros(value)
    val popcount = Integer.bitCount(value)
    if (1 + 2*(bitlen-1) + 7*(popcount-1) > WORD_BUDGET)
        return null
    val result = ArrayList<String>(2 + 9*bitlen)
    for (bit in (bitlen-1) downTo 0) {
        result.add("asl  a")                                // r = r*2
        result.add("rol  P8ZP_SCRATCH_W1+1")
        if (value and (1 shl bit) != 0) {                   // r += x
            result.add("clc")
            result.add("adc  P8ZP_SCRATCH_W2")
            result.add("sta  P8ZP_SCRATCH_W1")
            result.add("lda  P8ZP_SCRATCH_W1+1")
            result.add("adc  P8ZP_SCRATCH_W2+1")
            result.add("sta  P8ZP_SCRATCH_W1+1")
            result.add("tay")
            result.add("lda  P8ZP_SCRATCH_W1")
        }
    }
    if (value and 1 == 0)
        result.add("ldy  P8ZP_SCRATCH_W1+1")
    return result
}
