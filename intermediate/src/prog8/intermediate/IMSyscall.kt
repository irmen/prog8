package prog8.intermediate

// Calls to builtin operations that are too complex to be implemented as an IR instruction
// these use the SYSCALL instruction instead.
// Note that in the VM these are translated into whatever the corresponding Syscall number in the VM is.

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