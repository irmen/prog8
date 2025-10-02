package prog8.intermediate

// Calls to builtin operations that are too complex to be implemented as an IR instruction
// these use the SYSCALL instruction instead.
// Note that in the VM these are translated into whatever the corresponding Syscall number in the VM is.

enum class IMSyscall(val number: Int) {
    COMPARE_STRINGS(0x100d),
    STRING_CONTAINS(0x100e),
    BYTEARRAY_CONTAINS(0x100f),
    WORDARRAY_CONTAINS(0x1010),
    FLOATARRAY_CONTAINS(0x1011),
    CLAMP_UBYTE(0x1012),
    CLAMP_BYTE(0x1013),
    CLAMP_UWORD(0x1014),
    CLAMP_WORD(0x1015),
    CLAMP_FLOAT(0x1016),
    CALLFAR(0x1017),
    CALLFAR2(0x1018),
    MEMCOPY(0x1019),
    CLAMP_LONG(0x101a),
}
