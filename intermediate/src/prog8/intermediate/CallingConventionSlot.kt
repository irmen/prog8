package prog8.intermediate

/**
 * Abstract calling convention slot index, used to decouple IR code from specific CPU register names.
 *
 * LOADHR/STOREHR opcodes use slot numbers instead of hardcoded register names, so code generators
 * for different CPU architectures can map slots to their own register files.
 *
 * Slot convention by architecture:
 *
 * 6502 (slots 0-7):
 *   +-------+------------------+
 *   | Slot  | 6502             |
 *   +-------+------------------+
 *   | 0     | A (8-bit)        |
 *   | 1     | X (8-bit)        |
 *   | 2     | Y (8-bit)        |
 *   | 3     | AX (16-bit)      |
 *   | 4     | AY (16-bit)      |
 *   | 5     | XY (16-bit)      |
 *   | 6     | FAC0 (float)     |
 *   | 7     | FAC1 (float)     |
 *   +-------+------------------+
 *
 * 68000 (slots 10-32 only):
 *   +-------+-----------------------------+
 *   | Slot  | 68000                      |
 *   +-------+-----------------------------+
 *   | 10-17 | D0-D7 (32-bit data regs)   |
 *   | 18-24 | A0-A6 (32-bit addr regs)  |
 *   | 25-32 | FP0-FP7 (64-bit float)     |
 *   +-------+-----------------------------+
 */
@JvmInline
value class CallingConventionSlot(val value: Int) {
    init {
        require(value in 0..32) { "calling convention slot must be 0-32, got $value" }
    }
    override fun toString(): String = "s$value"
}
