package prog8.intermediate

// Calls to builtin operations that are too complex to be implemented as an IR instruction
// these use the SYSCALL instruction instead.
// Note that in the VM these are translated into whatever the corresponding Syscall number in the VM is.

enum class IMSyscall(val number: Int) {
    SORT_UBYTE(0x1000),
    SORT_BYTE(0x1001),
    SORT_UWORD(0x1002),
    SORT_WORD(0x1003),
    ANY_BYTE(0x1004),
    ANY_WORD(0x1005),
    ANY_FLOAT(0x1006),
    ALL_BYTE(0x1007),
    ALL_WORD(0x1008),
    ALL_FLOAT(0x1009),
    REVERSE_BYTES(0x100a),
    REVERSE_WORDS(0x100b),
    REVERSE_FLOATS(0x100c),
    COMPARE_STRINGS(0x100d),
    STRING_CONTAINS(0x100e),
    BYTEARRAY_CONTAINS(0x100f),
    WORDARRAY_CONTAINS(0x1010)
}
