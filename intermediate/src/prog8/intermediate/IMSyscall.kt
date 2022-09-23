package prog8.intermediate

// calls to builtin operations that are too complex to be implemented as an IR instruction
// these use the SYSCALL instruction instead.

enum class IMSyscall {
    SORT_UBYTE,
    SORT_BYTE,
    SORT_UWORD,
    SORT_WORD,
    ANY_BYTE,
    ANY_WORD,
    ANY_FLOAT,
    ALL_BYTE,
    ALL_WORD,
    ALL_FLOAT,
    REVERSE_BYTES,
    REVERSE_WORDS,
    REVERSE_FLOATS
}