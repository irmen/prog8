package prog8.intermediate

/**
 * Abstract calling convention slot index, used to decouple IR code from specific 6502 CPU register names.
 *
 * The original IR had separate opcodes for each 6502 hardware register (LOADHA, LOADHX, STOREHAY, etc.),
 * which tied the IR directly to the 6502 architecture.  These were replaced by the generic LOADHR/STOREHR
 * opcodes that use a slot number instead.  This makes it easier to add code generators for other CPU
 * architectures later on - they just need to map slot numbers to their own register files.
 *
 * Slots 0-5 are integer CPU registers.  LOADHR/STOREHR with these slots affect CPU status flags (Z, N).
 * Slots 6-7 are float FAC registers - they do NOT affect CPU status flags.
 *
 * Multi-architecture slot convention:
 *
 * +------------+-------------------------+-------------------------+---------------------------+
 * | Slot index | 6502 (default)          | Z80                     | 68000                     |
 * +============+=========================+=========================+===========================+
 * | 0          | A (8-bit)               | A (8-bit)               | D0 (32-bit, low byte)     |
 * | 1          | X (8-bit)               | F (flags)               | D1                        |
 * | 2          | Y (8-bit)               | B (8-bit)               | D2                        |
 * | 3          | AX (16-bit: A low, X hi)| BC (16-bit: B hi, C lo) | D0 (32-bit, low word)     |
 * | 4          | AY (16-bit: A low, Y hi)| DE (16-bit)             | D0+D1                     |
 * | 5          | XY (16-bit: X low, Y hi)| HL (16-bit)             | D1+D2                     |
 * | 6          | FAC0 (64-bit float)     | (soft float)            | FP0 / soft float          |
 * | 7          | FAC1 (64-bit float)     | (soft float)            | FP1 / soft float          |
 * +------------+-------------------------+-------------------------+---------------------------+
 */
@JvmInline
value class CallingConventionSlot(val value: Int) {
    init {
        require(value in 0..7) { "calling convention slot must be 0-7, got $value" }
    }
    override fun toString(): String = "s$value"
}
