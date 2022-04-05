package prog8.vm

/*

Virtual machine:

65536 virtual registers, 16 bits wide, can also be used as 8 bits. r0-r65535
65536 bytes of memory. Thus memory pointers (addresses) are limited to 16 bits.
Value stack, max 128 entries.


Instruction serialization format possibility:

OPCODE:             1 byte
TYPECODE:           1 byte
REGISTER 1:         2 bytes
REGISTER 2:         2 bytes
REG3/MEMORY/VALUE:  2 bytes

Instructions with Type come in variants 'b' and 'w' (omitting it in the instruction means 'b' by default)
Currently NO support for 24 or 32 bits, and FLOATING POINT is not implemented yet either. FP would be
a separate set of registers and instructions/routines anyway.

*only* LOAD AND STORE instructions have a possible memory operand, all other instructions use only registers or immediate value.


LOAD/STORE
----------
All have type b or w.

load        reg1,         value       - load immediate value into register
loadm       reg1,         address     - load reg1 with value in memory address
loadi       reg1, reg2                - load reg1 with value in memory indirect, memory pointed to by reg2
loadx       reg1, reg2,   address     - load reg1 with value in memory address, indexed by value in reg2
loadr       reg1, reg2                - load reg1 with value in register reg2

storem      reg1,         address     - store reg1 in memory address
storei      reg1, reg2                - store reg1 in memory indirect, memory pointed to by reg2
storex      reg1, reg2,   address     - store reg1 in memory address, indexed by value in reg2
storez                    address     - store zero in memory address
storezi     reg1                      - store zero in memory pointed to by reg
storezx     reg1,         address     - store zero in memory address, indexed by value in reg


CONTROL FLOW
------------
Possible subroutine call convention:
Set parameters in Reg 0, 1, 2... before call. Return value set in Reg 0 before return.
But you can decide whatever you want because here we just care about jumping and returning the flow of control.
Saving/restoring registers is possible with PUSH and POP instructions.

jump                    location      - continue running at instruction number given by location
jumpi       reg1                      - continue running at instruction number in reg1
call                    location      - save current instruction location+1, continue execution at instruction nr given by location
calli       reg1                      - save current instruction location+1, continue execution at instruction number in reg1
syscall                 value         - do a systemcall identified by call number
return                                - restore last saved instruction location and continue at that instruction


BRANCHING
---------
All have type b or w.

bz          reg1,             location  - branch to location if reg1 is zero
bnz         reg1,             location  - branch to location if reg1 is not zero
beq         reg1, reg2,       location  - jump to location in program given by location, if reg1 == reg2
bne         reg1, reg2,       location  - jump to location in program given by location, if reg1 != reg2
blt         reg1, reg2,       location  - jump to location in program given by location, if reg1 < reg2 (unsigned)
blts        reg1, reg2,       location  - jump to location in program given by location, if reg1 < reg2 (signed)
ble         reg1, reg2,       location  - jump to location in program given by location, if reg1 <= reg2 (unsigned)
bles        reg1, reg2,       location  - jump to location in program given by location, if reg1 <= reg2 (signed)
bgt         reg1, reg2,       location  - jump to location in program given by location, if reg1 > reg2 (unsigned)
bgts        reg1, reg2,       location  - jump to location in program given by location, if reg1 > reg2 (signed)
bge         reg1, reg2,       location  - jump to location in program given by location, if reg1 >= reg2 (unsigned)
bges        reg1, reg2,       location  - jump to location in program given by location, if reg1 >= reg2 (signed)
seq         reg1, reg2, reg3            - set reg=1 if reg2 == reg3,  otherwise set reg1=0
sne         reg1, reg2, reg3            - set reg=1 if reg2 != reg3,  otherwise set reg1=0
slt         reg1, reg2, reg3            - set reg=1 if reg2 < reg3 (unsigned),  otherwise set reg1=0
slts        reg1, reg2, reg3            - set reg=1 if reg2 < reg3 (signed),  otherwise set reg1=0
sle         reg1, reg2, reg3            - set reg=1 if reg2 <= reg3 (unsigned),  otherwise set reg1=0
sles        reg1, reg2, reg3            - set reg=1 if reg2 <= reg3 (signed),  otherwise set reg1=0
sgt         reg1, reg2, reg3            - set reg=1 if reg2 > reg3 (unsigned),  otherwise set reg1=0
sgts        reg1, reg2, reg3            - set reg=1 if reg2 > reg3 (signed),  otherwise set reg1=0
sge         reg1, reg2, reg3            - set reg=1 if reg2 >= reg3 (unsigned),  otherwise set reg1=0
sges        reg1, reg2, reg3            - set reg=1 if reg2 >= reg3 (signed),  otherwise set reg1=0

TODO: support for the prog8 special branching instructions if_XX (bcc, bcs etc.)
      but we don't have any 'processor flags' whatsoever in the vm so it's a bit weird


INTEGER ARITHMETIC
------------------
All have type b or w. Note: result types are the same as operand types! E.g. byte*byte->byte.

ext         reg1                            - reg1 = unsigned extension of reg1 (which in practice just means clearing the MSB / MSW) (latter not yet implemented as we don't have longs yet)
exts        reg1                            - reg1 = signed extension of reg1 (byte to word, or word to long)  (note: latter ext.w, not yet implemented as we don't have longs yet)
inc         reg1                            - reg1 = reg1+1
incm                           address      - memory at address += 1
dec         reg1                            - reg1 = reg1-1
decm                           address      - memory at address -= 1
neg         reg1                            - reg1 = sign negation of reg1
add         reg1, reg2, reg3                - reg1 = reg2+reg3 (unsigned + signed)
sub         reg1, reg2, reg3                - reg1 = reg2-reg3 (unsigned + signed)
mul         reg1, reg2, reg3                - unsigned multiply reg1=reg2*reg3  note: byte*byte->byte, no type extension to word!
div         reg1, reg2, reg3                - unsigned division reg1=reg2/reg3  note: division by zero yields max signed int $ff/$ffff
mod         reg1, reg2, reg3                - remainder (modulo) of unsigned division reg1=reg2%reg3  note: division by zero yields max signed int $ff/$ffff

TODO signed mul/div/mod?


LOGICAL/BITWISE
---------------
All have type b or w.

and         reg1, reg2, reg3                 - reg1 = reg2 bitwise and reg3
or          reg1, reg2, reg3                 - reg1 = reg2 bitwise or reg3
xor         reg1, reg2, reg3                 - reg1 = reg2 bitwise xor reg3
lsr         reg1, reg2, reg3                 - reg1 = shift reg2 right by reg3 bits
asr         reg1, reg2, reg3                 - reg1 = shift reg2 right by reg3 bits (signed)
lsl         reg1, reg2, reg3                 - reg1 = shift reg2 left by reg3 bits
ror         reg1, reg2, reg3                 - reg1 = rotate reg2 right by reg3 bits, not using carry
rol         reg1, reg2, reg3                 - reg1 = rotate reg2 left by reg3 bits, not using carry

TODO also add ror/rol variants using the carry bit? These do map directly on 6502 and 68k instructions. But the VM doesn't have carry status bit yet.


MISC
----

nop                                   - do nothing
breakpoint                            - trigger a breakpoint
copy        reg1, reg2,   length      - copy memory from ptrs in reg1 to reg3, length bytes
copyz       reg1, reg2                - copy memory from ptrs in reg1 to reg3, stop after first 0-byte
swap [b, w] reg1                      - swap lsb and msb in register reg1 (16 bits) or lsw and msw (32 bits)
swapreg     reg1, reg2                - swap values in reg1 and reg2
concat [b, w] reg1, reg2, reg3        - reg1 = concatenated lsb/lsw of reg2 and lsb/lsw of reg3 into new word or int (int not yet implemented, requires 32bits regs)
push [b, w] reg1                      - push value in reg1 on the stack
pop [b, w]  reg1                      - pop value from stack into reg1

 */

enum class Opcode {
    NOP,
    LOAD,
    LOADM,
    LOADI,
    LOADX,
    LOADR,
    STOREM,
    STOREI,
    STOREX,
    STOREZ,
    STOREZI,
    STOREZX,

    JUMP,
    JUMPI,
    CALL,
    CALLI,
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
    SEQ,
    SNE,
    SLT,
    SLTS,
    SGT,
    SGTS,
    SLE,
    SLES,
    SGE,
    SGES,

    INC,
    INCM,
    DEC,
    DECM,
    NEG,
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    EXT,
    EXTS,

    AND,
    OR,
    XOR,
    ASR,
    LSR,
    LSL,
    ROR,
    ROL,

    PUSH,
    POP,
    SWAP,
    SWAPREG,
    CONCAT,
    COPY,
    COPYZ,
    BREAKPOINT
}

val OpcodesWithAddress = setOf(
    Opcode.LOADM,
    Opcode.LOADX,
    Opcode.STOREM,
    Opcode.STOREX,
    Opcode.STOREZ,
    Opcode.STOREZX
)


enum class VmDataType {
    BYTE,
    WORD
    // TODO add INT (32-bit)?   INT24 (24-bit)?
}

data class Instruction(
    val opcode: Opcode,
    val type: VmDataType?=null,
    val reg1: Int?=null,        // 0-$ffff
    val reg2: Int?=null,        // 0-$ffff
    val reg3: Int?=null,        // 0-$ffff
    val value: Int?=null,       // 0-$ffff
    val symbol: List<String>?=null    // alternative to value
) {
    init {
        val format = instructionFormats.getValue(opcode)
        if(format.datatypes.isNotEmpty() && type==null)
            throw IllegalArgumentException("missing type")

        if(format.reg1 && reg1==null ||
            format.reg2 && reg2==null ||
            format.reg3 && reg3==null)
            throw IllegalArgumentException("missing a register")

        if(format.value && (value==null && symbol==null))
            throw IllegalArgumentException("missing a value or symbol")
    }

    override fun toString(): String {
        val result = mutableListOf(opcode.name.lowercase())

        when(type) {
            VmDataType.BYTE -> result.add(".b ")
            VmDataType.WORD -> result.add(".w ")
            else -> result.add(" ")
        }
        reg1?.let {
            result.add("r$it")
            result.add(",")
        }
        reg2?.let {
            result.add("r$it")
            result.add(",")
        }
        reg3?.let {
            result.add("r$it")
            result.add(",")
        }
        value?.let {
            result.add(it.toString())
            result.add(",")
        }
        symbol?.let {
            result.add("_" + it.joinToString("."))
        }
        if(result.last() == ",")
            result.removeLast()
        return result.joinToString("").trimEnd()
    }
}

data class InstructionFormat(val datatypes: Set<VmDataType>, val reg1: Boolean, val reg2: Boolean, val reg3: Boolean, val value: Boolean)

private val NN = emptySet<VmDataType>()
private val BW = setOf(VmDataType.BYTE, VmDataType.WORD)

@Suppress("BooleanLiteralArgument")
val instructionFormats = mutableMapOf(
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
        Opcode.CALL to       InstructionFormat(NN, false, false, false, true ),
        Opcode.CALLI to      InstructionFormat(NN, true,  false, false, false),
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
        Opcode.SEQ to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.SNE to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.SLT to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.SLTS to       InstructionFormat(BW, true,  true,  true,  false),
        Opcode.SGT to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.SGTS to       InstructionFormat(BW, true,  true,  true,  false),
        Opcode.SLE to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.SLES to       InstructionFormat(BW, true,  true,  true,  false),
        Opcode.SGE to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.SGES to       InstructionFormat(BW, true,  true,  true,  false),

        Opcode.INC to        InstructionFormat(BW, true,  false, false, false),
        Opcode.INCM to       InstructionFormat(BW, false, false, false, true ),
        Opcode.DEC to        InstructionFormat(BW, true,  false, false, false),
        Opcode.DECM to       InstructionFormat(BW, false, false, false, true ),
        Opcode.NEG to        InstructionFormat(BW, true,  false, false, false),
        Opcode.ADD to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.SUB to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.MUL to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.DIV to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.MOD to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.EXT to        InstructionFormat(BW, true,  false, false, false),
        Opcode.EXTS to       InstructionFormat(BW, true,  false, false, false),

        Opcode.AND to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.OR to         InstructionFormat(BW, true,  true,  true,  false),
        Opcode.XOR to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.ASR to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.LSR to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.LSL to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.ROR to        InstructionFormat(BW, true,  true,  true,  false),
        Opcode.ROL to        InstructionFormat(BW, true,  true,  true,  false),

        Opcode.COPY to       InstructionFormat(NN, true,  true,  false, true ),
        Opcode.COPYZ to      InstructionFormat(NN, true,  true,  false, false),
        Opcode.SWAP to       InstructionFormat(BW, true,  false, false, false),
        Opcode.PUSH to       InstructionFormat(BW, true,  false, false, false),
        Opcode.POP to        InstructionFormat(BW, true,  false, false, false),
        Opcode.CONCAT to     InstructionFormat(BW, true,  true,  true,  false),
        Opcode.BREAKPOINT to InstructionFormat(NN, false, false, false, false)
)
