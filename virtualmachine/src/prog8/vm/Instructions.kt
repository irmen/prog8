package prog8.vm

/*

65536 virtual registers, maximum 16 bits wide.
65536 bytes of memory.
So a memory pointer is also limited to 16 bits.


TODO: also make a 3-address-code instructionset?


Instruction serialization format possibility:

OPCODE:             1 byte
TYPECODE:           1 byte
REGISTER 1:         2 bytes
REGISTER 2:         2 bytes
REG3/MEMORY/VALUE:  2 bytes

Instructions with Type come in variants b/w/f (omitting it in the instruction means '8' if instruction otherwise has a T)
Currently NO support for 24 or 32 bits, and FLOATING POINT is not implemented yet either.

*only* LOAD AND STORE instructions have a possible memory operand, all other instructions use only registers or immediate value.


LOAD/STORE  -- all have type b/w/f.  (note: float not yet implemented)


load        reg1,         value       - load immediate value into register
loadm       reg1,         value       - load reg1 with value in memory address given by value
loadi       reg1, reg2                - load reg1 with value in memory indirect, memory pointed to by reg2
loadx       reg1, reg2,   value       - load reg1 with value in memory address given by value, indexed by value in reg2
loadr       reg1, reg2                - load reg1 with value in register reg2
swapreg     reg1, reg2                - swap values in reg1 and reg2

storem      reg1,         value       - store reg1 in memory address given by value
storei      reg1, reg2                - store reg1 in memory indirect, memory pointed to by reg2
storex      reg1, reg2,   value       - store reg1 in memory address given by value, indexed by value in reg2
storez                    value       - store zero in memory given by value
storezi     reg1                      - store zero in memory pointed to by reg
storezx     reg1,         value       - store zero in memory given by value, indexed by value in reg


FLOW CONTROL

Subroutine call convention:
Subroutine parameters set in Reg 0, 1, 2... before gosub.
Return value in Reg 0 before return.

jump                    location      - continue running at instruction number given by location
jumpi       reg1                      - continue running at instruction number given by reg1
gosub                   location      - save current instruction location+1, continue execution at location
gosubi      reg1                      - gosub to subroutine at instruction number given by reg1
syscall                 value         - do a systemcall identified by call number
return                                - restore last saved instruction location and continue at that instruction

branch instructions have b/w/f  types (f not implemented)
bz          reg1, value               - branch if reg1 is zero
bnz         reg1, value               - branch if reg1 is not zero
beq         reg1, reg2, value         - jump to location in program given by value, if reg1 == reg2
bne         reg1, reg2, value         - jump to location in program given by value, if reg1 != reg2
blt         reg1, reg2, value         - jump to location in program given by value, if reg1 < reg2 (unsigned)
blts        reg1, reg2, value         - jump to location in program given by value, if reg1 < reg2 (signed)
bgt         reg1, reg2, value         - jump to location in program given by value, if reg1 > reg2 (unsigned)
bgts        reg1, reg2, value         - jump to location in program given by value, if reg1 > reg2 (signed)
ble         reg1, reg2, value         - jump to location in program given by value, if reg1 <= reg2 (unsigned)
bles        reg1, reg2, value         - jump to location in program given by value, if reg1 <= reg2 (signed)
bge         reg1, reg2, value         - jump to location in program given by value, if reg1 >= reg2 (unsigned)
bges        reg1, reg2, value         - jump to location in program given by value, if reg1 >= reg2 (signed)


ARITHMETIC - all have a type of b/w/f. (note: float not yet implemented)
(note: for calculations, all types -result, and both operands- are identical)

neg         reg1, reg2                      - reg1 = sign negation of reg2
add         reg1, reg2, reg3                - reg1 = reg2+reg3 (unsigned + signed)
addi        reg1, reg2,         value       - reg1 = reg2+value (unsigned + signed)
sub         reg1, reg2, reg3                - reg1 = reg2-reg3 (unsigned + signed)
subi        reg1, reg2,         value       - reg1 = reg2-value (unsigned + signed)
ext         reg1, reg2                      - reg1 = unsigned extension of reg2 (which in practice just means clearing the MSB / MSW) (latter not yet implemented as we don't have longs yet)
exts        reg1, reg2                      - reg1 = signed extension of reg2 (byte to word, or word to long)  (note: latter ext.w, not yet implemented as we don't have longs yet)
mul         reg1, reg2, reg3                - unsigned multiply reg1=reg2*reg3  note: byte*byte->byte, no type extension to word!
div         reg1, reg2, reg3                - unsigned division reg1=reg2/reg3  note: division by zero yields max signed int $ff/$ffff


LOGICAL/BITWISE - all have a type of b/w. but never f
inv         reg1, reg2                       - reg1 = bitwise invert of reg2
and         reg1, reg2, reg3                 - reg1 = reg2 bitwise and reg3
or          reg1, reg2, reg3                 - reg1 = reg2 bitwise or reg3
xor         reg1, reg2, reg3                 - reg1 = reg2 bitwise xor reg3
lsr         reg1, reg2                       - reg1 = shift reg2 right by 1 bit
lsl         reg1, reg2                       - reg1 = shift reg2 left by 1 bit
ror         reg1, reg2                       - reg1 = rotate reg2 right by 1 bit, not using carry
rol         reg1, reg2                       - reg1 = rotate reg2 left by 1 bit, not using carry

Not sure if the variants using the carry bit should be added, for the ror/rol instructions.
These do map directly on 6502 and 68k instructions though.


MISC

nop                                   - do nothing
breakpoint                            - trigger a breakpoint
copy        reg1, reg2,   length      - copy memory from ptrs in reg1 to reg3, length bytes
copyz       reg1, reg2                - copy memory from ptrs in reg1 to reg3, stop after first 0-byte
swap [b, w] reg1, reg2                - reg1 = swapped lsb and msb from register reg2 (16 bits) or lsw and msw (32 bits)


 */

enum class Opcode {
    NOP,
    LOAD,
    LOADM,
    LOADI,
    LOADX,
    LOADR,
    SWAPREG,
    STOREM,
    STOREI,
    STOREX,
    STOREZ,
    STOREZI,
    STOREZX,

    JUMP,
    JUMPI,
    GOSUB,
    GOSUBI,
    SYSCALL,
    RETURN,
    BZ,
    BNZ,
    BEQ,
    BNE,
    BLT,
    BLTS,
    BGT,
    BGTS,
    BLE,
    BLES,
    BGE,
    BGES,

    NEG,
    ADD,
    ADDI,
    SUB,
    SUBI,
    MUL,
    DIV,
    EXT,
    EXTS,

    INV,
    AND,
    OR,
    XOR,
    LSR,
    LSL,
    ROR,
    ROL,

    COPY,
    COPYZ,
    SWAP,
    BREAKPOINT
}

enum class DataType {
    BYTE,
    WORD
    // TODO add LONG?  FLOAT?
}

data class Instruction(
    val opcode: Opcode,
    val type: DataType?=null,
    val reg1: Int?=null,       // 0-$ffff
    val reg2: Int?=null,       // 0-$ffff
    val reg3: Int?=null,       // 0-$ffff
    val value: Int?=null,      // 0-$ffff
)

data class InstructionFormat(val datatypes: Set<DataType>, val reg1: Boolean, val reg2: Boolean, val reg3: Boolean, val value: Boolean)

private val NN = emptySet<DataType>()
private val BW = setOf(DataType.BYTE, DataType.WORD)

@Suppress("BooleanLiteralArgument")
val instructionFormats = mutableMapOf(
        //  opcode     to                   types, reg1,  reg2,  reg3,  value
        Opcode.NOP to        InstructionFormat(NN, false, false, false, false),
        Opcode.LOAD to       InstructionFormat(BW, true,  false, false, true ),
        Opcode.LOADM to      InstructionFormat(BW, true,  false, false, true ),
        Opcode.LOADI to      InstructionFormat(BW, true,  true,  false, false),
        Opcode.LOADX to      InstructionFormat(BW, true,  true,  false, true ),
        Opcode.LOADR to      InstructionFormat(BW, true,  true,  false, false),
        Opcode.SWAPREG to    InstructionFormat(BW, true,  true,  false, false),
        Opcode.STOREM to     InstructionFormat(BW, true,  false, false, true ),
        Opcode.STOREI to     InstructionFormat(BW, true,  true,  false, false),
        Opcode.STOREX to     InstructionFormat(BW, true,  true,  false, true ),
        Opcode.STOREZ to     InstructionFormat(BW, false, false, false, true ),
        Opcode.STOREZI to    InstructionFormat(BW, true,  false, false, false),
        Opcode.STOREZX to    InstructionFormat(BW, true,  false, false, true ),

        Opcode.JUMP to       InstructionFormat(NN, false, false, false, true ),
        Opcode.JUMPI to      InstructionFormat(NN, true,  false, false, false),
        Opcode.GOSUB to      InstructionFormat(NN, false, false, false, true ),
        Opcode.GOSUBI to     InstructionFormat(NN, true,  false, false, false),
        Opcode.SYSCALL to    InstructionFormat(NN, false, false, false, true ),
        Opcode.RETURN to     InstructionFormat(NN, false, false, false, false),
        Opcode.BZ to         InstructionFormat(BW, true,  false, false, true ),
        Opcode.BNZ to        InstructionFormat(BW, true,  false, false, true ),
        Opcode.BEQ to        InstructionFormat(BW, true,  true,  false, true ),
        Opcode.BNE to        InstructionFormat(BW, true,  true,  false, true ),
        Opcode.BLT to        InstructionFormat(BW, true,  true,  false, true ),
        Opcode.BLTS to       InstructionFormat(BW, true,  true,  false, true ),
        Opcode.BGT to        InstructionFormat(BW, true,  true,  false, true ),
        Opcode.BGTS to       InstructionFormat(BW, true,  true,  false, true ),
        Opcode.BLE to        InstructionFormat(BW, true,  true,  false, true ),
        Opcode.BLES to       InstructionFormat(BW, true,  true,  false, true ),
        Opcode.BGE to        InstructionFormat(BW, true,  true,  false, true ),
        Opcode.BGES to       InstructionFormat(BW, true,  true,  false, true ),

        Opcode.NEG to        InstructionFormat(BW, true,  true,  false, false),
        Opcode.ADD to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.ADDI to       InstructionFormat(BW, true,  true,  false, true ),
        Opcode.SUB to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.SUBI to       InstructionFormat(BW, true,  true,  false, true ),
        Opcode.MUL to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.DIV to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.EXT to        InstructionFormat(BW, true,  true,  false, false),
        Opcode.EXTS to       InstructionFormat(BW, true,  true,  false, false),

        Opcode.INV to        InstructionFormat(BW, true,  true,  false, false),
        Opcode.AND to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.OR to         InstructionFormat(BW, true,  true,  true,  false),
        Opcode.XOR to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.LSR to        InstructionFormat(BW, true,  true,  false, false),
        Opcode.LSL to        InstructionFormat(BW, true,  true,  false, false),
        Opcode.ROR to        InstructionFormat(BW, true,  true,  false, false),
        Opcode.ROL to        InstructionFormat(BW, true,  true,  false, false),

        Opcode.COPY to       InstructionFormat(NN, true,  true,  false, true ),
        Opcode.COPYZ to      InstructionFormat(NN, true,  true,  false, false),
        Opcode.SWAP to       InstructionFormat(BW, true,  true,  false, false),
        Opcode.BREAKPOINT to InstructionFormat(NN, false, false, false, false)
)
