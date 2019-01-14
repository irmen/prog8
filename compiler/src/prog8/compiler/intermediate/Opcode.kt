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
    PUSH_MEMREAD,    // push memory value from address that's on the stack
    PUSH_VAR_BYTE,   // push byte variable (ubyte, byte)
    PUSH_VAR_WORD,   // push word variable (uword, word)
    PUSH_VAR_FLOAT,  // push float variable
    PUSH_REGAX_WORD, // push registers A/X as a 16-bit word
    PUSH_REGAY_WORD, // push registers A/Y as a 16-bit word
    PUSH_REGXY_WORD, // push registers X/Y as a 16-bit word
    PUSH_ADDR_HEAPVAR,  // push the address of the variable that's on the heap (string or array)

    // popping values off the (evaluation) stack, possibly storing them in another location
    DISCARD_BYTE,    // discard top byte value
    DISCARD_WORD,    // discard top word value
    DISCARD_FLOAT,   // discard top float value
    POP_MEM_BYTE,    // pop (u)byte value into destination memory address
    POP_MEM_WORD,    // pop (u)word value into destination memory address
    POP_MEM_FLOAT,   // pop float value into destination memory address
    POP_MEMWRITE,    // pop address and byte stack and write the byte to the memory address
    POP_VAR_BYTE,    // pop (u)byte value into variable
    POP_VAR_WORD,    // pop (u)word value into variable
    POP_VAR_FLOAT,   // pop float value into variable
    POP_REGAX_WORD,  // pop uword from stack into A/X registers
    POP_REGAY_WORD,  // pop uword from stack into A/Y registers
    POP_REGXY_WORD,  // pop uword from stack into X/Y registers

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
    IDIV_UB,
    IDIV_B,
    IDIV_UW,
    IDIV_W,
    DIV_F,
    REMAINDER_UB,   // signed remainder is undefined/unimplemented
    REMAINDER_UW,   // signed remainder is undefined/unimplemented
    POW_UB,
    POW_B,
    POW_UW,
    POW_W,
    POW_F,
    NEG_B,
    NEG_W,
    NEG_F,
    ABS_B,
    ABS_W,
    ABS_F,

    // bit shifts and bitwise arithmetic
    SHIFTEDL_BYTE,      // shifts stack value rather than in-place mem/var
    SHIFTEDL_WORD,      // shifts stack value rather than in-place mem/var
    SHIFTEDR_UBYTE,     // shifts stack value rather than in-place mem/var
    SHIFTEDR_SBYTE,     // shifts stack value rather than in-place mem/var
    SHIFTEDR_UWORD,     // shifts stack value rather than in-place mem/var
    SHIFTEDR_SWORD,     // shifts stack value rather than in-place mem/var
    SHL_BYTE,
    SHL_WORD,
    SHL_MEM_BYTE,
    SHL_MEM_WORD,
    SHL_VAR_BYTE,
    SHL_VAR_WORD,
    SHR_UBYTE,
    SHR_SBYTE,
    SHR_UWORD,
    SHR_SWORD,
    SHR_MEM_UBYTE,
    SHR_MEM_SBYTE,
    SHR_MEM_UWORD,
    SHR_MEM_SWORD,
    SHR_VAR_UBYTE,
    SHR_VAR_SBYTE,
    SHR_VAR_UWORD,
    SHR_VAR_SWORD,
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
    MSB,        // note: lsb is equivalent to  CAST_UW_TO_UB  or CAST_W_TO_UB
    MKWORD,        // create a word from lsb + msb
    CAST_UB_TO_B,
    CAST_UB_TO_UW,
    CAST_UB_TO_W,
    CAST_UB_TO_F,
    CAST_B_TO_UB,
    CAST_B_TO_UW,
    CAST_B_TO_W,
    CAST_B_TO_F,
    CAST_W_TO_UB,
    CAST_W_TO_B,
    CAST_W_TO_UW,
    CAST_W_TO_F,
    CAST_UW_TO_UB,
    CAST_UW_TO_B,
    CAST_UW_TO_W,
    CAST_UW_TO_F,
    CAST_F_TO_UB,
    CAST_F_TO_B,
    CAST_F_TO_UW,
    CAST_F_TO_W,

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
    INC_MEMORY,             // increment direct address
    DEC_MEMORY,             // decrement direct address
    POP_INC_MEMORY,         // increment address from stack
    POP_DEC_MEMORY,         // decrement address from address

    // comparisons
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
    CMP_B,          // sets processor status flags based on comparison, instead of pushing a result value
    CMP_UB,         // sets processor status flags based on comparison, instead of pushing a result value
    CMP_W,          // sets processor status flags based on comparison, instead of pushing a result value
    CMP_UW,         // sets processor status flags based on comparison, instead of pushing a result value

    // array access and simple manipulations
    READ_INDEXED_VAR_BYTE,
    READ_INDEXED_VAR_WORD,
    READ_INDEXED_VAR_FLOAT,
    WRITE_INDEXED_VAR_BYTE,
    WRITE_INDEXED_VAR_WORD,
    WRITE_INDEXED_VAR_FLOAT,
    INC_INDEXED_VAR_B,
    INC_INDEXED_VAR_UB,
    INC_INDEXED_VAR_W,
    INC_INDEXED_VAR_UW,
    INC_INDEXED_VAR_FLOAT,
    DEC_INDEXED_VAR_B,
    DEC_INDEXED_VAR_UB,
    DEC_INDEXED_VAR_W,
    DEC_INDEXED_VAR_UW,
    DEC_INDEXED_VAR_FLOAT,

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
    // branching, based on value on the stack (which is consumed)
    JZ,         // branch if value is zero (byte)
    JNZ,        // branch if value is not zero (byte)
    JZW,         // branch if value is zero (word)
    JNZW,        // branch if value is not zero (word)


    // subroutines
    CALL,
    RETURN,
    SYSCALL,
    START_PROCDEF,
    END_PROCDEF,

    // misc
    SEC,        // set carry status flag  NOTE: is mostly fake, carry flag is not affected by any numeric operations
    CLC,        // clear carry status flag  NOTE: is mostly fake, carry flag is not affected by any numeric operations
    SEI,        // set irq-disable status flag
    CLI,        // clear irq-disable status flag
    RSAVE,      // save all internal registers and status flags
    RSAVEX,     // save just X (the evaluation stack pointer)
    RSAVEY,     // save just Y (used in for loops for instance)
    RRESTORE,   // restore all internal registers and status flags
    RRESTOREX,  // restore just X (the evaluation stack pointer)
    RRESTOREY,  // restore just Y (used in for loops for instance)

    NOP,        // do nothing
    BREAKPOINT, // breakpoint
    TERMINATE,  // end the program
    LINE,       // track source file line number
    INLINE_ASSEMBLY         // container to hold inline raw assembly code
}

val opcodesWithVarArgument = setOf(
        Opcode.INC_VAR_B, Opcode.INC_VAR_W, Opcode.DEC_VAR_B, Opcode.DEC_VAR_W,
        Opcode.INC_VAR_UB, Opcode.INC_VAR_UW, Opcode.DEC_VAR_UB, Opcode.DEC_VAR_UW,
        Opcode.SHR_VAR_SBYTE, Opcode.SHR_VAR_UBYTE, Opcode.SHR_VAR_SWORD, Opcode.SHR_VAR_UWORD,
        Opcode.SHL_VAR_BYTE, Opcode.SHL_VAR_WORD,
        Opcode.ROL_VAR_BYTE, Opcode.ROL_VAR_WORD, Opcode.ROR_VAR_BYTE, Opcode.ROR_VAR_WORD,
        Opcode.ROL2_VAR_BYTE, Opcode.ROL2_VAR_WORD, Opcode.ROR2_VAR_BYTE, Opcode.ROR2_VAR_WORD,
        Opcode.POP_VAR_BYTE, Opcode.POP_VAR_WORD, Opcode.POP_VAR_FLOAT,
        Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_FLOAT, Opcode.PUSH_ADDR_HEAPVAR,
        Opcode.READ_INDEXED_VAR_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.READ_INDEXED_VAR_FLOAT,
        Opcode.WRITE_INDEXED_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD, Opcode.WRITE_INDEXED_VAR_FLOAT,
        Opcode.INC_INDEXED_VAR_UB, Opcode.INC_INDEXED_VAR_B, Opcode.INC_INDEXED_VAR_UW,
        Opcode.INC_INDEXED_VAR_W, Opcode.INC_INDEXED_VAR_FLOAT,
        Opcode.DEC_INDEXED_VAR_UB, Opcode.DEC_INDEXED_VAR_B, Opcode.DEC_INDEXED_VAR_UW,
        Opcode.DEC_INDEXED_VAR_W, Opcode.DEC_INDEXED_VAR_FLOAT
)
