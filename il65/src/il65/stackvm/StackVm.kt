package il65.stackvm

import kotlin.experimental.and

enum class Opcode {

    // pushing values on the (evaluation) stack
    PUSH,           // push constant byte value
    PUSH_W,         // push constant word value
    PUSH_F,         // push constant float value
    PUSH_LOCAL,     // push block-local variable (byte)
    PUSH_LOCAL_W,     // push block-local variable (word)
    PUSH_LOCAL_F,     // push block-local variable (float)

    // popping values off the (evaluation) stack, possibly storing them in another location
    DISCARD,        // discard X bytes from the top of the stack
    POP_MEM,        // pop byte value into destination memory address
    POP_MEM_W,      // pop word value into destination memory address
    POP_MEM_F,      // pop float value into destination memory address
    POP_LOCAL,      // pop byte value into block-local variable
    POP_LOCAL_W,    // pop word value into block-local variable
    POP_LOCAL_F,    // pop float value into block-local variable

    // integer and bitwise arithmetic
    ADD,
    SUB,
    MUL,
    DIV,
    ADD_W,
    SUB_W,
    MUL_W,
    DIV_W,
    SHL,
    SHR,
    ROL,
    ROR,
    SHL_W,
    SHR_W,
    ROL_W,
    ROR_W,
    AND,
    OR,
    XOR,
    NOT,
    AND_W,
    OR_W,
    XOR_W,
    NOT_W,

    // floating point arithmetic
    ADD_F,
    SUB_F,
    MUL_F,
    DIV_F,
    NEG_F,

    // logical operations (?)

    // increment, decrement
    INC,
    INC_W,
    INC_F,
    INC_MEM,
    INC_MEM_W,
    INC_MEM_F,
    INC_LOCAL,
    INC_LOCAL_W,
    INC_LOCAL_F,
    DEC,
    DEC_W,
    DEC_F,
    DEC_MEM,
    DEC_MEM_W,
    DEC_MEM_F,
    DEC_LOCAL,
    DEC_LOCAL_W,
    DEC_LOCAL_F,

    // conversions
    CV_F_B,     // float -> byte (truncated)
    CV_F_W,     // float -> word (truncated)
    CV_B_F,     // byte -> float
    CV_W_F,     // word -> float
    CV_B_W,     // byte -> word
    CV_W_B,     // word -> byte

    // comparisons (?)

    // branching
    JUMP,

    // subroutine calling
    CALL,
    RETURN,
    SYSCALL,

    // misc
    TERMINATE
}

private class Memory {
    private val mem = ShortArray(65536)         // shorts because byte is signed and we store values 0..255

    fun getByte(address: Int): Short {
        return mem[address]
    }

    fun setByte(address: Int, value: Short) {
        mem[address] = value
    }

    fun getWord(address: Int): Int {
        return 256*mem[address] + mem[address+1]
    }

    fun setWord(address: Int, value: Short) {
        mem[address] = (value / 256).toShort()
        mem[address+1] = value.and(255)
    }

    fun getCopy() = mem.copyOf()
}


data class Instruction(val opcode: Opcode, val args: List<Any>) {
    lateinit var next: Instruction
    lateinit var nextAlt: Instruction
}


class StackVm {
    private val mem = Memory()

    fun memDump() = mem.getCopy()

    init {
        val x=Instruction(Opcode.ADD, emptyList())
    }
}

