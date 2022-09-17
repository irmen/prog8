package prog8.intermediate

/*

Intermediate Representation instructions for the IR Virtual machine.
--------------------------------------------------------------------

Specs of the virtual machine this will run on:
Program to execute is not stored in the system memory, it's just a separate list of instructions.
65536 virtual registers, 16 bits wide, can also be used as 8 bits. r0-r65535
65536 virtual floating point registers (32 bits single precision floats)  fr0-fr65535
65536 bytes of memory. Thus memory pointers (addresses) are limited to 16 bits.
Value stack, max 128 entries of 1 byte each.
Status flags: Carry, Zero, Negative.   NOTE: status flags are only affected by the CMP instruction or explicit CLC/SEC!!!
                                             logical or arithmetic operations DO NOT AFFECT THE STATUS FLAGS UNLESS EXPLICITLY NOTED!

Most instructions have an associated data type 'b','w','f'. (omitting it means 'b'/byte).
Currently NO support for 24 or 32 bits integers.
Floating point operations are just 'f' typed regular instructions, and additionally there are a few fp conversion instructions


LOAD/STORE
----------
All have type b or w or f.

load        reg1,         value       - load immediate value into register
loadm       reg1,         address     - load reg1 with value at memory address
loadi       reg1, reg2                - load reg1 with value at memory indirect, memory pointed to by reg2
loadx       reg1, reg2,   address     - load reg1 with value at memory address indexed by value in reg2
loadix      reg1, reg2,   pointeraddr - load reg1 with value at memory indirect, pointed to by pointeraddr indexed by value in reg2
loadr       reg1, reg2                - load reg1 with value in register reg2
loadcpu     reg1,         cpureg      - load reg1 with value from cpu register (register/registerpair/statusflag)

storem      reg1,         address     - store reg1 at memory address
storecpu    reg1,         cpureg      - store reg1 in cpu register (register/registerpair/statusflag)
storei      reg1, reg2                - store reg1 at memory indirect, memory pointed to by reg2
storex      reg1, reg2,   address     - store reg1 at memory address, indexed by value in reg2
storeix     reg1, reg2,   pointeraddr - store reg1 at memory indirect, pointed to by pointeraddr indexed by value in reg2
storezm                   address     - store zero at memory address
storezcpu                 cpureg      - store zero in cpu register (register/registerpair/statusflag)
storezi     reg1                      - store zero at memory pointed to by reg1
storezx     reg1,         address     - store zero at memory address, indexed by value in reg


CONTROL FLOW
------------
Possible subroutine call convention:
Set parameters in Reg 0, 1, 2... before call. Return value set in Reg 0 before return.
But you can decide whatever you want because here we just care about jumping and returning the flow of control.
Saving/restoring registers is possible with PUSH and POP instructions.

jump                    location      - continue running at instruction number given by location
call                    location      - save current instruction location+1, continue execution at instruction nr given by location
calli       reg1                      - save current instruction location+1, continue execution at instruction number in reg1
syscall                 value         - do a systemcall identified by call number
return                                - restore last saved instruction location and continue at that instruction


BRANCHING
---------
All have type b or w except the branches that only check status bits.

bstcc                         location  - branch to location if Status bit Carry is Clear
bstcs                         location  - branch to location if Status bit Carry is Set
bsteq                         location  - branch to location if Status bit Zero is set
bstne                         location  - branch to location if Status bit Zero is not set
bstneg                        location  - branch to location if Status bit Negative is not set
bstpos                        location  - branch to location if Status bit Negative is not set
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
seq         reg1, reg2                  - set reg=1 if reg1 == reg2,  otherwise set reg1=0
sne         reg1, reg2                  - set reg=1 if reg1 != reg2,  otherwise set reg1=0
slt         reg1, reg2                  - set reg=1 if reg1 < reg2 (unsigned),  otherwise set reg1=0
slts        reg1, reg2                  - set reg=1 if reg1 < reg2 (signed),  otherwise set reg1=0
sle         reg1, reg2                  - set reg=1 if reg1 <= reg2 (unsigned),  otherwise set reg1=0
sles        reg1, reg2                  - set reg=1 if reg1 <= reg2 (signed),  otherwise set reg1=0
sgt         reg1, reg2                  - set reg=1 if reg1 > reg2 (unsigned),  otherwise set reg1=0
sgts        reg1, reg2                  - set reg=1 if reg1 > reg2 (signed),  otherwise set reg1=0
sge         reg1, reg2                  - set reg=1 if reg1 >= reg2 (unsigned),  otherwise set reg1=0
sges        reg1, reg2                  - set reg=1 if reg1 >= reg2 (signed),  otherwise set reg1=0


ARITHMETIC
----------
All have type b or w or f. Note: result types are the same as operand types! E.g. byte*byte->byte.

ext         reg1                            - reg1 = unsigned extension of reg1 (which in practice just means clearing the MSB / MSW) (ext.w not yet implemented as we don't have longs yet)
exts        reg1                            - reg1 = signed extension of reg1 (byte to word, or word to long)  (note: ext.w is not yet implemented as we don't have longs yet)
inc         reg1                            - reg1 = reg1+1
incm                           address      - memory at address += 1
dec         reg1                            - reg1 = reg1-1
decm                           address      - memory at address -= 1
neg         reg1                            - reg1 = sign negation of reg1
negm                           address      - sign negate memory at address
addr        reg1, reg2                      - reg1 += reg2 (unsigned + signed)
add         reg1,              value        - reg1 += value (unsigned + signed)
addm        reg1,              address      - memory at address += reg1 (unsigned + signed)
subr        reg1, reg2                      - reg1 -= reg2 (unsigned + signed)
sub         reg1,              value        - reg1 -= value (unsigned + signed)
subm        reg1,              address      - memory at address -= reg1 (unsigned + signed)
mulr        reg1, reg2                      - unsigned multiply reg1 *= reg2  note: byte*byte->byte, no type extension to word!
mul         reg1,              value        - unsigned multiply reg1 *= value  note: byte*byte->byte, no type extension to word!
mulm        reg1,              address      - memory at address  *= reg2  note: byte*byte->byte, no type extension to word!
divr        reg1, reg2                      - unsigned division reg1 /= reg2  note: division by zero yields max int $ff/$ffff
div         reg1,              value        - unsigned division reg1 /= value  note: division by zero yields max int $ff/$ffff
divm        reg1,              address      - memory at address /= reg2  note: division by zero yields max int $ff/$ffff
divsr       reg1, reg2                      - signed division reg1 /= reg2  note: division by zero yields max signed int 127 / 32767
divs        reg1,              value        - signed division reg1 /= value  note: division by zero yields max signed int 127 / 32767
divsm       reg1,              address      - signed memory at address /= reg2  note: division by zero yields max signed int 127 / 32767
modr        reg1, reg2                      - remainder (modulo) of unsigned division reg1 %= reg2  note: division by zero yields max signed int $ff/$ffff
mod         reg1,              value        - remainder (modulo) of unsigned division reg1 %= value  note: division by zero yields max signed int $ff/$ffff
sqrt        reg1, reg2                      - reg1 is the square root of reg2
sgn         reg1, reg2                      - reg1 is the sign of reg2 (0, 1 or -1)
cmp         reg1, reg2                      - set processor status bits C, N, Z according to comparison of reg1 with reg2. (semantics taken from 6502/68000 CMP instruction)
rnd         reg1                            - get a random number (byte, word or float)

NOTE: because mul/div are constrained (truncated) to remain in 8 or 16 bits, there is NO NEED for separate signed/unsigned mul and div instructions. The result is identical.


LOGICAL/BITWISE
---------------
All have type b or w.

andr        reg1, reg2                       - reg1 = reg1 bitwise and reg2
and         reg1,          value             - reg1 = reg1 bitwise and value
andm        reg1         address             - memory = memory bitwise and reg1
orr         reg1, reg2                       - reg1 = reg1 bitwise or reg2
or          reg1,          value             - reg1 = reg1 bitwise or value
orm         reg1,        address             - memory = memory bitwise or reg1
xorr        reg1, reg2                       - reg1 = reg1 bitwise xor reg2
xor         reg1,          value             - reg1 = reg1 bitwise xor value
xorm        reg1,        address             - memory = memory bitwise xor reg1
inv         reg1                             - reg1 = bitwise invert of reg1 (all bits flipped)
invm                     address             - memory = bitwise invert of that memory (all bits flipped)
asrn        reg1, reg2                       - reg1 = multi-shift reg1 right by reg2 bits (signed)  + set Carry to shifted bit
lsrn        reg1, reg2                       - reg1 = multi-shift reg1 right by reg2 bits + set Carry to shifted bit
lsln        reg1, reg2                       - reg1 = multi-shift reg1 left by reg2 bits  + set Carry to shifted bit
asrnm       reg1,        address             - multi-shift memory right by reg1 bits (signed)  + set Carry to shifted bit
lsrnm       reg1,        address             - multi-shift memoryright by reg1 bits + set Carry to shifted bit
lslnm       reg1,        address             - multi-shift memory left by reg1 bits  + set Carry to shifted bit
asr         reg1                             - shift reg1 right by 1 bits (signed) + set Carry to shifted bit
lsr         reg1                             - shift reg1 right by 1 bits + set Carry to shifted bit
lsl         reg1                             - shift reg1 left by 1 bits + set Carry to shifted bit
lsrm                     address             - shift memory right by 1 bits + set Carry to shifted bit
asrm                     address             - shift memory right by 1 bits (signed) + set Carry to shifted bit
lslm                     address             - shift memory left by 1 bits + set Carry to shifted bit
ror         reg1                             - rotate reg1 right by 1 bits, not using carry  + set Carry to shifted bit
roxr        reg1                             - rotate reg1 right by 1 bits, using carry  + set Carry to shifted bit
rol         reg1                             - rotate reg1 left by 1 bits, not using carry  + set Carry to shifted bit
roxl        reg1                             - rotate reg1 left by 1 bits, using carry,  + set Carry to shifted bit
rorm                     address             - rotate memory right by 1 bits, not using carry  + set Carry to shifted bit
roxrm                    address             - rotate memory right by 1 bits, using carry  + set Carry to shifted bit
rolm                     address             - rotate memory left by 1 bits, not using carry  + set Carry to shifted bit
roxlm                    address             - rotate memory left by 1 bits, using carry,  + set Carry to shifted bit


FLOATING POINT CONVERSIONS AND FUNCTIONS
----------------------------------------
ffromub      fpreg1, reg1               - fpreg1 = reg1 from usigned byte
ffromsb      fpreg1, reg1               - fpreg1 = reg1 from signed byte
ffromuw      fpreg1, reg1               - fpreg1 = reg1 from unsigned word
ffromsw      fpreg1, reg1               - fpreg1 = reg1 from signed word
ftoub        reg1, fpreg1               - reg1 = fpreg1 as unsigned byte
ftosb        reg1, fpreg1               - reg1 = fpreg1 as signed byte
ftouw        reg1, fpreg1               - reg1 = fpreg1 as unsigned word
ftosw        reg1, fpreg1               - reg1 = fpreg1 as signed word
fpow         fpreg1, fpreg2             - fpreg1 = fpreg1 to the power of fpreg2
fabs         fpreg1, fpreg2             - fpreg1 = abs(fpreg2)
fcomp        reg1, fpreg1, fpreg2       - reg1 = result of comparison of fpreg1 and fpreg2: 0=equal, 1=fpreg1 is greater, -1=fpreg1 is smaller
fsin         fpreg1, fpreg2             - fpreg1 = sin(fpreg2)
fcos         fpreg1, fpreg2             - fpreg1 = cos(fpreg2)
ftan         fpreg1, fpreg2             - fpreg1 = tan(fpreg2)
fatan        fpreg1, fpreg2             - fpreg1 = atan(fpreg2)
fln          fpreg1, fpreg2             - fpreg1 = ln(fpreg2)       ; natural logarithm
flog         fpreg1, fpreg2             - fpreg1 = log(fpreg2)      ; base 2 logarithm
fround       fpreg1, fpreg2             - fpreg1 = round(fpreg2)
ffloor       fpreg1, fpreg2             - fpreg1 = floor(fpreg2)
fceil        fpreg1, fpreg2             - fpreg1 = ceil(fpreg2)


MISC
----

clc                                       - clear Carry status bit
sec                                       - set Carry status bit
nop                                       - do nothing
breakpoint                                - trigger a breakpoint
msig [b, w]   reg1, reg2                  - reg1 becomes the most significant byte (or word) of the word (or int) in reg2  (.w not yet implemented; requires 32 bits regs)
concat [b, w] reg1, reg2                  - reg1 = concatenated lsb/lsw of reg1 (as lsb) and lsb/lsw of reg2 (as msb) into word or int (int not yet implemented; requires 32bits regs)
push [b, w]   reg1                        - push value in reg1 on the stack
pop [b, w]    reg1                        - pop value from stack into reg1
binarydata                                - 'instruction' to hold inlined binary data bytes
 */

enum class Opcode {
    NOP,
    LOAD,
    LOADM,
    LOADI,
    LOADX,
    LOADIX,
    LOADR,
    LOADCPU,
    STOREM,
    STORECPU,
    STOREI,
    STOREX,
    STOREIX,
    STOREZM,
    STOREZCPU,
    STOREZI,
    STOREZX,

    JUMP,
    CALL,
    SYSCALL,
    RETURN,

    BSTCC,
    BSTCS,
    BSTEQ,
    BSTNE,
    BSTNEG,
    BSTPOS,
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
    NEGM,
    ADDR,
    ADD,
    ADDM,
    SUBR,
    SUB,
    SUBM,
    MULR,
    MUL,
    MULM,
    DIVR,
    DIV,
    DIVM,
    DIVSR,
    DIVS,
    DIVSM,
    MODR,
    MOD,
    SQRT,
    SGN,
    CMP,
    RND,
    EXT,
    EXTS,

    ANDR,
    AND,
    ANDM,
    ORR,
    OR,
    ORM,
    XORR,
    XOR,
    XORM,
    INV,
    INVM,
    ASRN,
    ASRNM,
    LSRN,
    LSRNM,
    LSLN,
    LSLNM,
    ASR,
    ASRM,
    LSR,
    LSRM,
    LSL,
    LSLM,
    ROR,
    RORM,
    ROXR,
    ROXRM,
    ROL,
    ROLM,
    ROXL,
    ROXLM,

    FFROMUB,
    FFROMSB,
    FFROMUW,
    FFROMSW,
    FTOUB,
    FTOSB,
    FTOUW,
    FTOSW,
    FPOW,
    FABS,
    FSIN,
    FCOS,
    FTAN,
    FATAN,
    FLN,
    FLOG,
    FROUND,
    FFLOOR,
    FCEIL,
    FCOMP,

    CLC,
    SEC,
    PUSH,
    POP,
    MSIG,
    CONCAT,
    BREAKPOINT,
    BINARYDATA
}

val OpcodesWithAddress = setOf(
    Opcode.LOADM,
    Opcode.LOADX,
    Opcode.LOADIX,
    Opcode.STOREM,
    Opcode.STOREX,
    Opcode.STOREIX,
    Opcode.STOREZM,
    Opcode.STOREZX,
    Opcode.JUMP,
    Opcode.CALL,
    Opcode.INCM,
    Opcode.DECM,
    Opcode.NEGM,
    Opcode.ADDM,
    Opcode.SUBM,
    Opcode.MULM,
    Opcode.DIVM,
    Opcode.DIVSM,
    Opcode.INVM,
    Opcode.ORM,
    Opcode.XORM,
    Opcode.ANDM,
    Opcode.ASRM,
    Opcode.LSRM,
    Opcode.LSLM,
    Opcode.LSLNM,
    Opcode.LSRNM,
    Opcode.ASRNM,
    Opcode.ROLM,
    Opcode.RORM,
    Opcode.ROXLM,
    Opcode.ROXRM
)


enum class VmDataType {
    BYTE,
    WORD,
    FLOAT
    // TODO add INT (32-bit)?   INT24 (24-bit)?
}

data class Instruction(
    val opcode: Opcode,
    val type: VmDataType?=null,
    val reg1: Int?=null,        // 0-$ffff
    val reg2: Int?=null,        // 0-$ffff
    val fpReg1: Int?=null,      // 0-$ffff
    val fpReg2: Int?=null,      // 0-$ffff
    val value: Int?=null,       // 0-$ffff
    val fpValue: Float?=null,
    val labelSymbol: List<String>?=null,    // symbolic label name as alternative to value (so only for Branch/jump/call Instructions!)
    val binaryData: ByteArray?=null
) {
    // reg1 and fpreg1 can be IN/OUT/INOUT (all others are readonly INPUT)
    // This knowledge is useful in IL assembly optimizers to see how registers are used.
    val reg1direction: OperandDirection
    val fpReg1direction: OperandDirection

    init {
        if(opcode==Opcode.BINARYDATA && binaryData==null || binaryData!=null && opcode!=Opcode.BINARYDATA)
            throw IllegalArgumentException("binarydata inconsistency")

        val formats = instructionFormats.getValue(opcode)
        if(type==null && !formats.containsKey(null))
            throw IllegalArgumentException("missing type")

        val format = formats.getValue(type)
        if(format.reg1 && reg1==null || format.reg2 && reg2==null)
            throw IllegalArgumentException("missing a register (int)")

        if(format.fpReg1 && fpReg1==null || format.fpReg2 && fpReg2==null)
            throw IllegalArgumentException("missing a register (float)")

        if(!format.reg1 && reg1!=null || !format.reg2 && reg2!=null)
            throw IllegalArgumentException("too many registers (int)")

        if(!format.fpReg1 && fpReg1!=null || !format.fpReg2 && fpReg2!=null)
            throw IllegalArgumentException("too many registers (float)")

        if (type==VmDataType.FLOAT) {
            if(format.fpValue && (fpValue==null && labelSymbol==null))
                throw IllegalArgumentException("$opcode: missing a fp-value or labelsymbol")
        } else {
            if(format.value && (value==null && labelSymbol==null))
                throw IllegalArgumentException("$opcode: missing a value or labelsymbol")
            if (fpReg1 != null || fpReg2 != null)
                throw IllegalArgumentException("$opcode: integer point instruction can't use floating point registers")
        }

        reg1direction = format.reg1direction
        fpReg1direction = format.fpReg1direction

        if(opcode in setOf(Opcode.BEQ, Opcode.BNE, Opcode.BLT, Opcode.BLTS,
                Opcode.BGT, Opcode.BGTS, Opcode.BLE, Opcode.BLES,
                Opcode.BGE, Opcode.BGES,
                Opcode.SEQ, Opcode.SNE, Opcode.SLT, Opcode.SLTS,
                Opcode.SGT, Opcode.SGTS, Opcode.SLE, Opcode.SLES,
                Opcode.SGE, Opcode.SGES)) {
            if((type==VmDataType.FLOAT && fpReg1==fpReg2) || reg1==reg2) {
                throw IllegalArgumentException("$opcode: reg1 and reg2 should be different")
            }
        }
    }

    override fun toString(): String {
        val result = mutableListOf(opcode.name.lowercase())

        when(type) {
            VmDataType.BYTE -> result.add(".b ")
            VmDataType.WORD -> result.add(".w ")
            VmDataType.FLOAT -> result.add(".f ")
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
        fpReg1?.let {
            result.add("fr$it")
            result.add(",")
        }
        fpReg2?.let {
            result.add("fr$it")
            result.add(",")
        }
        value?.let {
            result.add(it.toString())
            result.add(",")
        }
        fpValue?.let {
            result.add(it.toString())
            result.add(",")
        }
        labelSymbol?.let {
            if(labelSymbol[0].startsWith('&'))
                result.add(it.joinToString("."))    // address-of something
            else
                result.add("_" + it.joinToString("."))
        }
        if(result.last() == ",")
            result.removeLast()
        return result.joinToString("").trimEnd()
    }
}

enum class OperandDirection {
    INPUT,
    OUTPUT,
    INOUT
}

data class InstructionFormat(val datatype: VmDataType?,
                             val reg1: Boolean, val reg1direction: OperandDirection,        // reg1 can be IN/OUT/INOUT
                             val reg2: Boolean,         // always only IN
                             val fpReg1: Boolean, val fpReg1direction: OperandDirection,    // fpreg1 can be IN/OUT/INOUT
                             val fpReg2: Boolean,       // always only IN
                             val value: Boolean,        // always only IN
                             val fpValue: Boolean       // always only IN
                             ) {
    companion object {
        fun from(spec: String): Map<VmDataType?, InstructionFormat> {
            val result = mutableMapOf<VmDataType?, InstructionFormat>()
            for(part in spec.split('|').map{ it.trim() }) {
                var reg1 = false        // read/write/modify possible
                var reg1Direction = OperandDirection.INPUT
                var reg2 = false        // strictly read-only
                var fpreg1 = false      // read/write/modify possible
                var fpreg1Direction = OperandDirection.INPUT
                var fpreg2 = false      // strictly read-only
                var value = false       // strictly read-only
                var fpvalue = false     // strictly read-only
                val splits = part.splitToSequence(',').iterator()
                val typespec = splits.next()
                while(splits.hasNext()) {
                    when(splits.next()) {
                        "<r1" -> { reg1=true; reg1Direction=OperandDirection.INPUT }
                        ">r1" -> { reg1=true; reg1Direction=OperandDirection.OUTPUT }
                        "<>r1" -> { reg1=true; reg1Direction=OperandDirection.INOUT }
                        "<r2" -> reg2 = true
                        "<fr1" -> { fpreg1=true; fpreg1Direction=OperandDirection.INPUT }
                        ">fr1" -> { fpreg1=true; fpreg1Direction=OperandDirection.OUTPUT }
                        "<>fr1" -> { fpreg1=true; fpreg1Direction=OperandDirection.INOUT }
                        "<fr2" -> fpreg2=true
                        "<v" -> value = true
                        "<fv" -> fpvalue = true
                        else -> throw IllegalArgumentException(spec)
                    }
                }
                if(typespec=="N")
                    result[null] = InstructionFormat(null, reg1, reg1Direction, reg2, fpreg1, fpreg1Direction, fpreg2, value, fpvalue)
                if('B' in typespec)
                    result[VmDataType.BYTE] = InstructionFormat(VmDataType.BYTE, reg1, reg1Direction, reg2, fpreg1, fpreg1Direction, fpreg2, value, fpvalue)
                if('W' in typespec)
                    result[VmDataType.WORD] = InstructionFormat(VmDataType.WORD, reg1, reg1Direction, reg2, fpreg1, fpreg1Direction, fpreg2, value, fpvalue)
                if('F' in typespec)
                    result[VmDataType.FLOAT] = InstructionFormat(VmDataType.FLOAT, reg1, reg1Direction, reg2, fpreg1, fpreg1Direction, fpreg2, value, fpvalue)
            }
            return result
        }
    }
}

/*
  <X  =  X is not modified (input/readonly value)
  >X  =  X is overwritten with output value (output value)
  <>X =  X is modified (input + output)
  TODO: also encode if memory is read/written/modified?
 */
@Suppress("BooleanLiteralArgument")
val instructionFormats = mutableMapOf(
    Opcode.NOP        to InstructionFormat.from("N"),
    Opcode.LOAD       to InstructionFormat.from("BW,>r1,<v     | F,>fr1,<fv"),
    Opcode.LOADM      to InstructionFormat.from("BW,>r1,<v     | F,>fr1,<v"),
    Opcode.LOADI      to InstructionFormat.from("BW,>r1,<r2    | F,>fr1,<r1"),
    Opcode.LOADX      to InstructionFormat.from("BW,>r1,<r2,<v | F,>fr1,<r1,<v"),
    Opcode.LOADIX     to InstructionFormat.from("BW,>r1,<r2,<v | F,>fr1,<r1,<v"),
    Opcode.LOADR      to InstructionFormat.from("BW,>r1,<r2    | F,>fr1,<fr2"),
    Opcode.LOADCPU    to InstructionFormat.from("BW,>r1"),
    Opcode.STOREM     to InstructionFormat.from("BW,<r1,<v     | F,<fr1,<v"),
    Opcode.STORECPU   to InstructionFormat.from("BW,<r1"),
    Opcode.STOREI     to InstructionFormat.from("BW,<r1,<r2    | F,<fr1,<r1"),
    Opcode.STOREX     to InstructionFormat.from("BW,<r1,<r2,<v | F,<fr1,<r1,<v"),
    Opcode.STOREIX    to InstructionFormat.from("BW,<r1,<r2,<v | F,<fr1,<r1,<v"),
    Opcode.STOREZM    to InstructionFormat.from("BW,<v         | F,<v"),
    Opcode.STOREZCPU  to InstructionFormat.from("BW"),
    Opcode.STOREZI    to InstructionFormat.from("BW,<r1        | F,<r1"),
    Opcode.STOREZX    to InstructionFormat.from("BW,<r1,<v     | F,<r1,<v"),
    Opcode.JUMP       to InstructionFormat.from("N,<v"),
    Opcode.CALL       to InstructionFormat.from("N,<v"),
    Opcode.SYSCALL    to InstructionFormat.from("N,<v"),
    Opcode.RETURN     to InstructionFormat.from("N"),
    Opcode.BSTCC      to InstructionFormat.from("N,<v"),
    Opcode.BSTCS      to InstructionFormat.from("N,<v"),
    Opcode.BSTEQ      to InstructionFormat.from("N,<v"),
    Opcode.BSTNE      to InstructionFormat.from("N,<v"),
    Opcode.BSTNEG     to InstructionFormat.from("N,<v"),
    Opcode.BSTPOS     to InstructionFormat.from("N,<v"),
    Opcode.BZ         to InstructionFormat.from("BW,<r1,<v"),
    Opcode.BNZ        to InstructionFormat.from("BW,<r1,<v"),
    Opcode.BEQ        to InstructionFormat.from("BW,<r1,<r2,<v"),
    Opcode.BNE        to InstructionFormat.from("BW,<r1,<r2,<v"),
    Opcode.BLT        to InstructionFormat.from("BW,<r1,<r2,<v"),
    Opcode.BLTS       to InstructionFormat.from("BW,<r1,<r2,<v"),
    Opcode.BGT        to InstructionFormat.from("BW,<r1,<r2,<v"),
    Opcode.BGTS       to InstructionFormat.from("BW,<r1,<r2,<v"),
    Opcode.BLE        to InstructionFormat.from("BW,<r1,<r2,<v"),
    Opcode.BLES       to InstructionFormat.from("BW,<r1,<r2,<v"),
    Opcode.BGE        to InstructionFormat.from("BW,<r1,<r2,<v"),
    Opcode.BGES       to InstructionFormat.from("BW,<r1,<r2,<v"),
    Opcode.SEQ        to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.SNE        to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.SLT        to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.SLTS       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.SGT        to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.SGTS       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.SLE        to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.SLES       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.SGE        to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.SGES       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.INC        to InstructionFormat.from("BW,<>r1"),
    Opcode.INCM       to InstructionFormat.from("BW,<v"),
    Opcode.DEC        to InstructionFormat.from("BW,<>r1"),
    Opcode.DECM       to InstructionFormat.from("BW,<v"),
    Opcode.NEG        to InstructionFormat.from("BW,<>r1      | F,<>fr1"),
    Opcode.NEGM       to InstructionFormat.from("BW,<v        | F,<v"),
    Opcode.ADDR       to InstructionFormat.from("BW,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.ADD        to InstructionFormat.from("BW,<>r1,<v   | F,<>fr1,<v"),
    Opcode.ADDM       to InstructionFormat.from("BW,<r1,<v    | F,<fr1,<v"),
    Opcode.SUBR       to InstructionFormat.from("BW,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.SUB        to InstructionFormat.from("BW,<>r1,<v   | F,<>fr1,<v"),
    Opcode.SUBM       to InstructionFormat.from("BW,<r1,<v    | F,<fr1,<v"),
    Opcode.MULR       to InstructionFormat.from("BW,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.MUL        to InstructionFormat.from("BW,<>r1,<v   | F,<>fr1,<v"),
    Opcode.MULM       to InstructionFormat.from("BW,<r1,<v    | F,<fr1,<v"),
    Opcode.DIVR       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.DIV        to InstructionFormat.from("BW,<>r1,<v"),
    Opcode.DIVM       to InstructionFormat.from("BW,<r1,<v"),
    Opcode.DIVSR      to InstructionFormat.from("BW,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.DIVS       to InstructionFormat.from("BW,<>r1,<v   | F,<>fr1,<v"),
    Opcode.DIVSM      to InstructionFormat.from("BW,<r1,<v    | F,<fr1,<v"),
    Opcode.SQRT       to InstructionFormat.from("BW,>r1,<r2   | F,>fr1,<fr2"),
    Opcode.SGN        to InstructionFormat.from("BW,>r1,<r2   | F,>fr1,<fr2"),
    Opcode.RND        to InstructionFormat.from("BW,>r1       | F,>fr1"),
    Opcode.MODR       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.MOD        to InstructionFormat.from("BW,<>r1,<v"),
    Opcode.CMP        to InstructionFormat.from("BW,<r1,<r2"),
    Opcode.EXT        to InstructionFormat.from("BW,<>r1"),
    Opcode.EXTS       to InstructionFormat.from("BW,<>r1"),
    Opcode.ANDR       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.AND        to InstructionFormat.from("BW,<>r1,<v"),
    Opcode.ANDM       to InstructionFormat.from("BW,<r1,<v"),
    Opcode.ORR        to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.OR         to InstructionFormat.from("BW,<>r1,<v"),
    Opcode.ORM        to InstructionFormat.from("BW,<r1,<v"),
    Opcode.XORR       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.XOR        to InstructionFormat.from("BW,<>r1,<v"),
    Opcode.XORM       to InstructionFormat.from("BW,<r1,<v"),
    Opcode.INV        to InstructionFormat.from("BW,<>r1"),
    Opcode.INVM       to InstructionFormat.from("BW,<v"),
    Opcode.ASRN       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.ASRNM      to InstructionFormat.from("BW,<r1,<v"),
    Opcode.LSRN       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.LSRNM      to InstructionFormat.from("BW,<r1,<v"),
    Opcode.LSLN       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.LSLNM      to InstructionFormat.from("BW,<r1,<v"),
    Opcode.ASR        to InstructionFormat.from("BW,<>r1"),
    Opcode.ASRM       to InstructionFormat.from("BW,<v"),
    Opcode.LSR        to InstructionFormat.from("BW,<>r1"),
    Opcode.LSRM       to InstructionFormat.from("BW,<v"),
    Opcode.LSL        to InstructionFormat.from("BW,<>r1"),
    Opcode.LSLM       to InstructionFormat.from("BW,<v"),
    Opcode.ROR        to InstructionFormat.from("BW,<>r1"),
    Opcode.RORM       to InstructionFormat.from("BW,<v"),
    Opcode.ROXR       to InstructionFormat.from("BW,<>r1"),
    Opcode.ROXRM      to InstructionFormat.from("BW,<v"),
    Opcode.ROL        to InstructionFormat.from("BW,<>r1"),
    Opcode.ROLM       to InstructionFormat.from("BW,<v"),
    Opcode.ROXL       to InstructionFormat.from("BW,<>r1"),
    Opcode.ROXLM      to InstructionFormat.from("BW,<v"),

    Opcode.FFROMUB    to InstructionFormat.from("F,>fr1,<r1"),
    Opcode.FFROMSB    to InstructionFormat.from("F,>fr1,<r1"),
    Opcode.FFROMUW    to InstructionFormat.from("F,>fr1,<r1"),
    Opcode.FFROMSW    to InstructionFormat.from("F,>fr1,<r1"),
    Opcode.FTOUB      to InstructionFormat.from("F,>r1,<fr1"),
    Opcode.FTOSB      to InstructionFormat.from("F,>r1,<fr1"),
    Opcode.FTOUW      to InstructionFormat.from("F,>r1,<fr1"),
    Opcode.FTOSW      to InstructionFormat.from("F,>r1,<fr1"),
    Opcode.FPOW       to InstructionFormat.from("F,<>fr1,<fr2"),
    Opcode.FABS       to InstructionFormat.from("F,>fr1,<fr2"),
    Opcode.FCOMP      to InstructionFormat.from("F,>r1,<fr1,<fr2"),
    Opcode.FSIN       to InstructionFormat.from("F,>fr1,<fr2"),
    Opcode.FCOS       to InstructionFormat.from("F,>fr1,<fr2"),
    Opcode.FTAN       to InstructionFormat.from("F,>fr1,<fr2"),
    Opcode.FATAN      to InstructionFormat.from("F,>fr1,<fr2"),
    Opcode.FLN        to InstructionFormat.from("F,>fr1,<fr2"),
    Opcode.FLOG       to InstructionFormat.from("F,>fr1,<fr2"),
    Opcode.FROUND     to InstructionFormat.from("F,>fr1,<fr2"),
    Opcode.FFLOOR     to InstructionFormat.from("F,>fr1,<fr2"),
    Opcode.FCEIL      to InstructionFormat.from("F,>fr1,<fr2"),

    Opcode.MSIG       to InstructionFormat.from("BW,>r1,<r2"),
    Opcode.PUSH       to InstructionFormat.from("BW,<r1"),
    Opcode.POP        to InstructionFormat.from("BW,>r1"),
    Opcode.CONCAT     to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.CLC        to InstructionFormat.from("N"),
    Opcode.SEC        to InstructionFormat.from("N"),
    Opcode.BREAKPOINT to InstructionFormat.from("N"),
    Opcode.BINARYDATA to InstructionFormat.from("N"),
)
