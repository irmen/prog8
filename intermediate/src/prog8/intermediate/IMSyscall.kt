package prog8.intermediate

// Calls to builtin operations that are too complex to be implemented as an IR instruction
// these use the SYSCALL instruction instead.
// Note that in the VM these are translated into whatever the corresponding Syscall number in the VM is.

enum class IMSyscall(val number: Int) {
    SORT_UBYTE(10000),
    SORT_BYTE(10001),
    SORT_UWORD(10002),
    SORT_WORD(10003),
    ANY_BYTE(10004),
    ANY_WORD(10005),
    ANY_FLOAT(10006),
    ALL_BYTE(10007),
    ALL_WORD(10008),
    ALL_FLOAT(10009),
    REVERSE_BYTES(10010),
    REVERSE_WORDS(10011),
    REVERSE_FLOATS(10012),
    COMPARE_STRINGS(10013),
    STRING_CONTAINS(10014),
    BYTEARRAY_CONTAINS(10015),
    WORDARRAY_CONTAINS(10016)
}