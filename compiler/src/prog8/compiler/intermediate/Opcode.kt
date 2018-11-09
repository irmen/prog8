package prog8.compiler.intermediate

enum class Opcode {

    // pushing values on the (evaluation) stack
    PUSH_BYTE,       // push byte value
    PUSH_WORD,       // push word value   (or 'address' of string / array)
    PUSH_FLOAT,      // push float value
    PUSH_MEM_B,      // push byte value from memory to stack
    PUSH_MEM_UB,     // push unsigned byte value from memory to stack
    PUSH_MEM_W,      // push word value from memory to stack
    PUSH_MEM_UW,     // push unsigned word value from memory to stack
    PUSH_MEM_FLOAT,  // push float value from memory to stack
    PUSH_VAR_BYTE,   // push byte variable (ubyte, byte)
    PUSH_VAR_WORD,   // push word variable (uword, word)
    PUSH_VAR_FLOAT,  // push float variable

    // popping values off the (evaluation) stack, possibly storing them in another location
    DISCARD_BYTE,    // discard top byte value
    DISCARD_WORD,    // discard top word value
    DISCARD_FLOAT,   // discard top float value
    POP_MEM_BYTE,    // pop (u)byte value into destination memory address
    POP_MEM_WORD,    // pop (u)word value into destination memory address
    POP_MEM_FLOAT,   // pop float value into destination memory address
    POP_VAR_BYTE,    // pop (u)byte value into variable
    POP_VAR_WORD,    // pop (u)word value into variable
    POP_VAR_FLOAT,   // pop float value into variable

    // numeric arithmetic
    ADD_UB,
    ADD_B,
    ADD_UW,
    ADD_W,
    ADD_F,
    SUB_UB,
    SUB_B,
    SUB_UW,
    SUB_W,
    SUB_F,
    MUL_UB,
    MUL_B,
    MUL_UW,
    MUL_W,
    MUL_F,
    DIV_UB,
    DIV_B,
    DIV_UW,
    DIV_W,
    DIV_F,
    FLOORDIV_UB,
    FLOORDIV_B,
    FLOORDIV_UW,
    FLOORDIV_W,
    FLOORDIV_F,
    REMAINDER_UB,
    REMAINDER_B,
    REMAINDER_UW,
    REMAINDER_W,
    REMAINDER_F,
    POW_UB,
    POW_B,
    POW_UW,
    POW_W,
    POW_F,
    NEG_B,
    NEG_W,
    NEG_F,

    // bit shifts and bitwise arithmetic
    SHL_BYTE,
    SHL_WORD,
    SHL_MEM_BYTE,
    SHL_MEM_WORD,
    SHL_VAR_BYTE,
    SHL_VAR_WORD,
    SHR_BYTE,
    SHR_WORD,
    SHR_MEM_BYTE,
    SHR_MEM_WORD,
    SHR_VAR_BYTE,
    SHR_VAR_WORD,
    ROL_BYTE,
    ROL_WORD,
    ROL_MEM_BYTE,
    ROL_MEM_WORD,
    ROL_VAR_BYTE,
    ROL_VAR_WORD,
    ROR_BYTE,
    ROR_WORD,
    ROR_MEM_BYTE,
    ROR_MEM_WORD,
    ROR_VAR_BYTE,
    ROR_VAR_WORD,
    ROL2_BYTE,
    ROL2_WORD,
    ROL2_MEM_BYTE,
    ROL2_MEM_WORD,
    ROL2_VAR_BYTE,
    ROL2_VAR_WORD,
    ROR2_BYTE,
    ROR2_WORD,
    ROR2_MEM_BYTE,
    ROR2_MEM_WORD,
    ROR2_VAR_BYTE,
    ROR2_VAR_WORD,
    BITAND_BYTE,
    BITAND_WORD,
    BITOR_BYTE,
    BITOR_WORD,
    BITXOR_BYTE,
    BITXOR_WORD,
    INV_BYTE,
    INV_WORD,

    // numeric type conversions
    LSB,
    MSB,
    B2UB,
    UB2B,
    B2WORD,         // convert a byte into a word where it is the lower eight bits $ssxx with sign extension
    UB2UWORD,       // convert a byte into a word where it is the lower eight bits $00xx
    B2FLOAT,        // convert byte into floating point
    UB2FLOAT,       // convert unsigned byte into floating point
    W2FLOAT,        // convert word into floating point
    UW2FLOAT,       // convert unsigned word into floating point

    // logical operations
    AND_BYTE,
    AND_WORD,
    OR_BYTE,
    OR_WORD,
    XOR_BYTE,
    XOR_WORD,
    NOT_BYTE,
    NOT_WORD,

    // increment, decrement
    INC_VAR_B,
    INC_VAR_UB,
    INC_VAR_W,
    INC_VAR_UW,
    INC_VAR_F,
    DEC_VAR_B,
    DEC_VAR_UB,
    DEC_VAR_W,
    DEC_VAR_UW,
    DEC_VAR_F,

    // comparisons
    // @todo the comparisons (apart from TEST) push the result back on the stack. Optimize this to work with processor flags exclusively. This does mean you can no longer use a logical boolean result as a byte 0/1 value ?
    TEST,           // pop top value from stack and test it. Sets cpu flags (zero, negative, overflow) accordingly.
    LESS_B,
    LESS_UB,
    LESS_W,
    LESS_UW,
    LESS_F,
    GREATER_B,
    GREATER_UB,
    GREATER_W,
    GREATER_UW,
    GREATER_F,
    LESSEQ_B,
    LESSEQ_UB,
    LESSEQ_W,
    LESSEQ_UW,
    LESSEQ_F,
    GREATEREQ_B,
    GREATEREQ_UB,
    GREATEREQ_W,
    GREATEREQ_UW,
    GREATEREQ_F,
    EQUAL_BYTE,
    EQUAL_WORD,
    EQUAL_F,
    NOTEQUAL_BYTE,
    NOTEQUAL_WORD,
    NOTEQUAL_F,

    // arrayspec access
    READ_INDEXED_VAR_BYTE,
    READ_INDEXED_VAR_WORD,
    READ_INDEXED_VAR_FLOAT,
    WRITE_INDEXED_VAR_BYTE,
    WRITE_INDEXED_VAR_WORD,
    WRITE_INDEXED_VAR_FLOAT,

    // branching, without consuming a value from the stack
    JUMP,
    BCS,       // branch if carry set
    BCC,       // branch if carry clear
    BZ,        // branch if zero flag
    BNZ,       // branch if not zero flag
    BNEG,      // branch if negative flag
    BPOS,      // branch if not negative flag
    BVS,       // branch if overflow flag
    BVC,       // branch if not overflow flag

    // subroutine calling
    CALL,
    RETURN,
    SYSCALL,

    // misc
    SEC,        // set carry status flag  NOTE: is mostly fake, carry flag is not affected by any numeric operations
    CLC,        // clear carry status flag  NOTE: is mostly fake, carry flag is not affected by any numeric operations
    SEI,        // set irq-disable status flag
    CLI,        // clear irq-disable status flag
    RSAVE,      // save all internal registers and status flags
    RRESTORE,   // restore all internal registers and status flags
    NOP,        // do nothing
    BREAKPOINT, // breakpoint
    TERMINATE,  // end the program
    LINE,       // track source file line number
    INLINE_ASSEMBLY         // container to hold inline raw assembly code
}

val opcodesWithVarArgument = setOf(
        Opcode.INC_VAR_B, Opcode.INC_VAR_W, Opcode.DEC_VAR_B, Opcode.DEC_VAR_W,
        Opcode.INC_VAR_UB, Opcode.INC_VAR_UW, Opcode.DEC_VAR_UB, Opcode.DEC_VAR_UW,
        Opcode.SHR_VAR_BYTE, Opcode.SHR_VAR_WORD, Opcode.SHL_VAR_BYTE, Opcode.SHL_VAR_WORD,
        Opcode.ROL_VAR_BYTE, Opcode.ROL_VAR_WORD, Opcode.ROR_VAR_BYTE, Opcode.ROR_VAR_WORD,
        Opcode.ROL2_VAR_BYTE, Opcode.ROL2_VAR_WORD, Opcode.ROR2_VAR_BYTE, Opcode.ROR2_VAR_WORD,
        Opcode.POP_VAR_BYTE, Opcode.POP_VAR_WORD, Opcode.POP_VAR_FLOAT,
        Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_FLOAT,
        Opcode.READ_INDEXED_VAR_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.READ_INDEXED_VAR_FLOAT,
        Opcode.WRITE_INDEXED_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD, Opcode.WRITE_INDEXED_VAR_FLOAT
)
