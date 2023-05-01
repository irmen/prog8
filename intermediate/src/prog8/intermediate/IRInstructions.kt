package prog8.intermediate

import prog8.code.core.toHex

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

Instruction set is mostly a load/store architecture, there are few instructions operating on memory directly.

Value types: integers (.b=byte=8 bits, .w=word=16 bits) and float (.f=32 bits). Omitting it defaults to b.
Currently ther is NO support for 24 or 32 bits integers.
There is no distinction between signed and unsigned integers.
Instead, a different instruction is used if a distinction should be made (for example div and divs).
Floating point operations are just 'f' typed regular instructions, however there are a few unique fp conversion instructions.


LOAD/STORE
----------
All have type b or w or f.

load        reg1,         value       - load immediate value into register. If you supply a symbol, loads the *address* of the symbol! (variable values are loaded from memory via the loadm instruction)
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
jump                    location      - continue running at instruction number given by location
jumpa                   address       - continue running at memory address (note: only used to encode a physical cpu jump to fixed address instruction)
setparam    reg1,       argpos        - sets reg1 as the value for the parameter in the given position for an upcoming function call (call, callr, syscall, or even jump opcode).
call                    location      - save current instruction location+1, continue execution at instruction nr given by location. No return value is expected.
callr       reg1,       location      - like call but expects the routine to  return a value with a returnr instruction, it then puts that in reg1
syscall                 value         - do a systemcall identified by call number, result value(s) are pushed on value stack so need to be POPped off (depends on syscall)
return                                - restore last saved instruction location and continue at that instruction. No return value.
returnr     reg1                      - like return, but also returns the value in reg1 to the caller


BRANCHING and CONDITIONALS
--------------------------
All have type b or w except the branches that only check status bits.

bstcc                         address   - branch to location if Status bit Carry is clear
bstcs                         address   - branch to location if Status bit Carry is set
bstne                         address   - branch to location if Status bit Zero is clear
bsteq                         address   - branch to location if Status bit Zero is set
bstpos                        address   - branch to location if Status bit Negative is clear
bstneg                        address   - branch to location if Status bit Negative is set
bstvc                         address   - branch to location if Status bit Overflow is clear
bstvs                         address   - branch to location if Status bit Overflow is set
beqr        reg1, reg2,       address   - jump to location in program given by location, if reg1 == reg2
beq         reg1, value,      address   - jump to location in program given by location, if reg1 == immediate value
bner        reg1, reg2,       address   - jump to location in program given by location, if reg1 != reg2
bne         reg1, value,      address   - jump to location in program given by location, if reg1 != immediate value
bgt         reg1, value,      address   - jump to location in program given by location, if reg1 > immediate value (unsigned)
bgts        reg1, value,      address   - jump to location in program given by location, if reg1 > immediate value (signed)
bgtr        reg1, reg2,       address   - jump to location in program given by location, if reg1 > reg2 (unsigned)
bgtsr       reg1, reg2,       address   - jump to location in program given by location, if reg1 > reg2 (signed)
blt         reg1, value,      address   - jump to location in program given by location, if reg1 < immediate value (unsigned)
blts        reg1, value,      address   - jump to location in program given by location, if reg1 < immediate value (signed)
bge         reg1, value,      address   - jump to location in program given by location, if reg1 >= immediate value (unsigned)
bges        reg1, value,      address   - jump to location in program given by location, if reg1 >= immediate value (signed)
bger        reg1, reg2,       address   - jump to location in program given by location, if reg1 >= reg2 (unsigned)
bgesr       reg1, reg2,       address   - jump to location in program given by location, if reg1 >= reg2 (signed)
ble         reg1, value,      address   - jump to location in program given by location, if reg1 <= immediate value (unsigned)
bles        reg1, value,      address   - jump to location in program given by location, if reg1 <= immediate value (signed)
( NOTE: there are no bltr/bler instructions because these are equivalent to bgtr/bger with the register operands swapped around.)
sz          reg1, reg2                  - set reg1=1 if reg2==0,  otherwise set reg1=0
snz         reg1, reg2                  - set reg1=1 if reg2!=0,  otherwise set reg1=0
seq         reg1, reg2                  - set reg1=1 if reg1 == reg2,  otherwise set reg1=0
sne         reg1, reg2                  - set reg1=1 if reg1 != reg2,  otherwise set reg1=0
slt         reg1, reg2                  - set reg1=1 if reg1 < reg2 (unsigned),  otherwise set reg1=0
slts        reg1, reg2                  - set reg1=1 if reg1 < reg2 (signed),  otherwise set reg1=0
sle         reg1, reg2                  - set reg1=1 if reg1 <= reg2 (unsigned),  otherwise set reg1=0
sles        reg1, reg2                  - set reg1=1 if reg1 <= reg2 (signed),  otherwise set reg1=0
sgt         reg1, reg2                  - set reg1=1 if reg1 > reg2 (unsigned),  otherwise set reg1=0
sgts        reg1, reg2                  - set reg1=1 if reg1 > reg2 (signed),  otherwise set reg1=0
sge         reg1, reg2                  - set reg1=1 if reg1 >= reg2 (unsigned),  otherwise set reg1=0
sges        reg1, reg2                  - set reg1=1 if reg1 >= reg2 (signed),  otherwise set reg1=0


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
addr        reg1, reg2                      - reg1 += reg2 
add         reg1,              value        - reg1 += value 
addm        reg1,              address      - memory at address += reg1 
subr        reg1, reg2                      - reg1 -= reg2 
sub         reg1,              value        - reg1 -= value 
subm        reg1,              address      - memory at address -= reg1 
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
divmodr     reg1, reg2                      - unsigned division reg1/reg2, storing division and remainder on value stack (so need to be POPped off)
divmod      reg1,              value        - unsigned division reg1/value, storing division and remainder on value stack (so need to be POPped off)
sqrt        reg1, reg2                      - reg1 is the square root of reg2
sgn         reg1, reg2                      - reg1 is the sign of reg2 (0, 1 or -1)
cmp         reg1, reg2                      - set processor status bits C, N, Z according to comparison of reg1 with reg2. (semantics taken from 6502/68000 CMP instruction)

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
fcomp        reg1, fpreg1, fpreg2       - reg1 = result of comparison of fpreg1 and fpreg2: 0.b=equal, 1.b=fpreg1 is greater, -1.b=fpreg1 is smaller
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
push [b, w, f]   reg1                     - push value in reg1 on the stack
pop [b, w, f]    reg1                     - pop value from stack into reg1
binarydata                                - 'instruction' to hold inlined binary data bytes
 */

enum class Opcode {
    NOP,
    LOAD,       // note: LOAD <symbol>  gets you the address of the symbol, whereas LOADM <symbol> would get you the value stored at that location
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
    JUMPA,
    SETPARAM,
    CALL,
    CALLR,
    SYSCALL,
    RETURN,
    RETURNR,

    BSTCC,
    BSTCS,
    BSTEQ,
    BSTNE,
    BSTNEG,
    BSTPOS,
    BSTVC,
    BSTVS,
    BEQR,
    BEQ,
    BNER,
    BNE,
    BGTR,
    BGT,
    BLT,
    BGTSR,
    BGTS,
    BLTS,
    BGER,
    BGE,
    BLE,
    BGESR,
    BGES,
    BLES,
    SZ,
    SNZ,
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
    DIVMODR,
    DIVMOD,
    SQRT,
    SGN,
    CMP,
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

val OpcodesThatJump = setOf(
    Opcode.JUMP,
    Opcode.JUMPA,
    Opcode.RETURN,
    Opcode.RETURNR
)

val OpcodesThatBranch = setOf(
    Opcode.JUMP,
    Opcode.JUMPA,
    Opcode.RETURN,
    Opcode.RETURNR,
    Opcode.CALL,
    Opcode.CALLR,
    Opcode.SYSCALL,
    Opcode.BSTCC,
    Opcode.BSTCS,
    Opcode.BSTEQ,
    Opcode.BSTNE,
    Opcode.BSTNEG,
    Opcode.BSTPOS,
    Opcode.BSTVC,
    Opcode.BSTVS,
    Opcode.BEQR,
    Opcode.BEQ,
    Opcode.BNER,
    Opcode.BNE,
    Opcode.BGTR,
    Opcode.BGT,
    Opcode.BLT,
    Opcode.BGTSR,
    Opcode.BGTS,
    Opcode.BLTS,
    Opcode.BGER,
    Opcode.BGE,
    Opcode.BLE,
    Opcode.BGESR,
    Opcode.BGES,
    Opcode.BLES
)

val OpcodesForCpuRegisters = setOf(
    Opcode.LOADCPU,
    Opcode.STORECPU,
    Opcode.STOREZCPU
)

enum class IRDataType {
    BYTE,
    WORD,
    FLOAT
    // TODO add INT (32-bit)?   INT24 (24-bit)?
}

enum class OperandDirection {
    UNUSED,
    READ,
    WRITE,
    READWRITE
}

data class InstructionFormat(val datatype: IRDataType?,
                             val reg1: OperandDirection,
                             val reg2: OperandDirection,
                             val fpReg1: OperandDirection,
                             val fpReg2: OperandDirection,
                             val address: OperandDirection,
                             val immediate: Boolean) {
    companion object {
        fun from(spec: String): Map<IRDataType?, InstructionFormat> {
            val result = mutableMapOf<IRDataType?, InstructionFormat>()
            for(part in spec.split('|').map{ it.trim() }) {
                var reg1 = OperandDirection.UNUSED
                var reg2 = OperandDirection.UNUSED
                var fpreg1 = OperandDirection.UNUSED
                var fpreg2 = OperandDirection.UNUSED
                var address = OperandDirection.UNUSED
                var immediate = false
                val splits = part.splitToSequence(',').iterator()
                val typespec = splits.next()
                while(splits.hasNext()) {
                    when(splits.next()) {
                        "<r1" -> { reg1=OperandDirection.READ }
                        ">r1" -> { reg1=OperandDirection.WRITE }
                        "<>r1" -> { reg1=OperandDirection.READWRITE }
                        "<r2" -> reg2 = OperandDirection.READ
                        "<fr1" -> { fpreg1=OperandDirection.READ }
                        ">fr1" -> { fpreg1=OperandDirection.WRITE }
                        "<>fr1" -> { fpreg1=OperandDirection.READWRITE }
                        "<fr2" -> fpreg2 = OperandDirection.READ
                        ">i", "<>i" -> throw IllegalArgumentException("can't write into an immediate value")
                        "<i" -> immediate = true
                        "<a" -> address = OperandDirection.READ
                        ">a" -> address = OperandDirection.WRITE
                        "<>a" -> address = OperandDirection.READWRITE
                        else -> throw IllegalArgumentException(spec)
                    }
                }

                if(typespec=="N")
                    result[null] = InstructionFormat(null, reg1, reg2, fpreg1, fpreg2, address, immediate)
                if('B' in typespec)
                    result[IRDataType.BYTE] = InstructionFormat(IRDataType.BYTE, reg1, reg2, fpreg1, fpreg2, address, immediate)
                if('W' in typespec)
                    result[IRDataType.WORD] = InstructionFormat(IRDataType.WORD, reg1, reg2, fpreg1, fpreg2, address, immediate)
                if('F' in typespec)
                    result[IRDataType.FLOAT] = InstructionFormat(IRDataType.FLOAT, reg1, reg2, fpreg1, fpreg2, address, immediate)
            }
            return result
        }
    }
}

/*
  <X  =  X is not modified (readonly value)
  >X  =  X is overwritten with output value (write value)
  <>X =  X is modified (read + written)
  where X is one of:
     r0... = integer register
     fr0... = fp register
     a = memory address
     i = immediate value
 */
val instructionFormats = mutableMapOf(
    Opcode.NOP        to InstructionFormat.from("N"),
    Opcode.LOAD       to InstructionFormat.from("BW,>r1,<i     | F,>fr1,<i"),
    Opcode.LOADM      to InstructionFormat.from("BW,>r1,<a     | F,>fr1,<a"),
    Opcode.LOADI      to InstructionFormat.from("BW,>r1,<r2    | F,>fr1,<r1"),
    Opcode.LOADX      to InstructionFormat.from("BW,>r1,<r2,<a | F,>fr1,<r1,<a"),
    Opcode.LOADIX     to InstructionFormat.from("BW,>r1,<r2,<a | F,>fr1,<r1,<a"),
    Opcode.LOADR      to InstructionFormat.from("BW,>r1,<r2    | F,>fr1,<fr2"),
    Opcode.LOADCPU    to InstructionFormat.from("BW,>r1"),
    Opcode.STOREM     to InstructionFormat.from("BW,<r1,>a     | F,<fr1,>a"),
    Opcode.STORECPU   to InstructionFormat.from("BW,<r1"),
    Opcode.STOREI     to InstructionFormat.from("BW,<r1,<r2    | F,<fr1,<r1"),
    Opcode.STOREX     to InstructionFormat.from("BW,<r1,<r2,>a | F,<fr1,<r1,>a"),
    Opcode.STOREIX    to InstructionFormat.from("BW,<r1,<r2,>a | F,<fr1,<r1,>a"),
    Opcode.STOREZM    to InstructionFormat.from("BW,>a         | F,>a"),
    Opcode.STOREZCPU  to InstructionFormat.from("BW"),
    Opcode.STOREZI    to InstructionFormat.from("BW,<r1        | F,<r1"),
    Opcode.STOREZX    to InstructionFormat.from("BW,<r1,>a     | F,<r1,>a"),
    Opcode.JUMP       to InstructionFormat.from("N,<a"),
    Opcode.JUMPA      to InstructionFormat.from("N,<a"),
    Opcode.SETPARAM     to InstructionFormat.from("BW,<r1,<i     | F,<fr1,<i"),
    Opcode.CALL       to InstructionFormat.from("N,<a"),
    Opcode.CALLR      to InstructionFormat.from("BW,>r1,<a     | F,>fr1,<a"),
    Opcode.SYSCALL    to InstructionFormat.from("N,<i"),
    Opcode.RETURN     to InstructionFormat.from("N"),
    Opcode.RETURNR    to InstructionFormat.from("BW,>r1        | F,>fr1"),
    Opcode.BSTCC      to InstructionFormat.from("N,<a"),
    Opcode.BSTCS      to InstructionFormat.from("N,<a"),
    Opcode.BSTEQ      to InstructionFormat.from("N,<a"),
    Opcode.BSTNE      to InstructionFormat.from("N,<a"),
    Opcode.BSTNEG     to InstructionFormat.from("N,<a"),
    Opcode.BSTPOS     to InstructionFormat.from("N,<a"),
    Opcode.BSTVC      to InstructionFormat.from("N,<a"),
    Opcode.BSTVS      to InstructionFormat.from("N,<a"),
    Opcode.BEQR       to InstructionFormat.from("BW,<r1,<r2,<a"),
    Opcode.BEQ        to InstructionFormat.from("BW,<r1,<i,<a"),
    Opcode.BNER       to InstructionFormat.from("BW,<r1,<r2,<a"),
    Opcode.BNE        to InstructionFormat.from("BW,<r1,<i,<a"),
    Opcode.BGTR       to InstructionFormat.from("BW,<r1,<r2,<a"),
    Opcode.BGT        to InstructionFormat.from("BW,<r1,<i,<a"),
    Opcode.BLT        to InstructionFormat.from("BW,<r1,<i,<a"),
    Opcode.BGTSR      to InstructionFormat.from("BW,<r1,<r2,<a"),
    Opcode.BGTS       to InstructionFormat.from("BW,<r1,<i,<a"),
    Opcode.BLTS       to InstructionFormat.from("BW,<r1,<i,<a"),
    Opcode.BGER       to InstructionFormat.from("BW,<r1,<r2,<a"),
    Opcode.BGE        to InstructionFormat.from("BW,<r1,<i,<a"),
    Opcode.BLE        to InstructionFormat.from("BW,<r1,<i,<a"),
    Opcode.BGESR      to InstructionFormat.from("BW,<r1,<r2,<a"),
    Opcode.BGES       to InstructionFormat.from("BW,<r1,<i,<a"),
    Opcode.BLES       to InstructionFormat.from("BW,<r1,<i,<a"),
    Opcode.SZ         to InstructionFormat.from("BW,>r1,<r2"),
    Opcode.SNZ        to InstructionFormat.from("BW,>r1,<r2"),
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
    Opcode.INC        to InstructionFormat.from("BW,<>r1      | F,<>fr1"),
    Opcode.INCM       to InstructionFormat.from("BW,<>a       | F,<>a"),
    Opcode.DEC        to InstructionFormat.from("BW,<>r1      | F,<>fr1"),
    Opcode.DECM       to InstructionFormat.from("BW,<>a       | F,<>a"),
    Opcode.NEG        to InstructionFormat.from("BW,<>r1      | F,<>fr1"),
    Opcode.NEGM       to InstructionFormat.from("BW,<>a       | F,<>a"),
    Opcode.ADDR       to InstructionFormat.from("BW,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.ADD        to InstructionFormat.from("BW,<>r1,<i   | F,<>fr1,<i"),
    Opcode.ADDM       to InstructionFormat.from("BW,<r1,<>a   | F,<fr1,<>a"),
    Opcode.SUBR       to InstructionFormat.from("BW,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.SUB        to InstructionFormat.from("BW,<>r1,<i   | F,<>fr1,<i"),
    Opcode.SUBM       to InstructionFormat.from("BW,<r1,<>a   | F,<fr1,<>a"),
    Opcode.MULR       to InstructionFormat.from("BW,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.MUL        to InstructionFormat.from("BW,<>r1,<i   | F,<>fr1,<i"),
    Opcode.MULM       to InstructionFormat.from("BW,<r1,<>a   | F,<fr1,<>a"),
    Opcode.DIVR       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.DIV        to InstructionFormat.from("BW,<>r1,<i"),
    Opcode.DIVM       to InstructionFormat.from("BW,<r1,<>a"),
    Opcode.DIVSR      to InstructionFormat.from("BW,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.DIVS       to InstructionFormat.from("BW,<>r1,<i   | F,<>fr1,<i"),
    Opcode.DIVSM      to InstructionFormat.from("BW,<r1,<>a   | F,<fr1,<>a"),
    Opcode.SQRT       to InstructionFormat.from("BW,>r1,<r2   | F,>fr1,<fr2"),
    Opcode.SGN        to InstructionFormat.from("BW,>r1,<r2   | F,>fr1,<fr2"),
    Opcode.MODR       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.MOD        to InstructionFormat.from("BW,<>r1,<i"),
    Opcode.DIVMODR    to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.DIVMOD     to InstructionFormat.from("BW,<>r1,<i"),
    Opcode.CMP        to InstructionFormat.from("BW,<r1,<r2"),
    Opcode.EXT        to InstructionFormat.from("BW,<>r1"),
    Opcode.EXTS       to InstructionFormat.from("BW,<>r1"),
    Opcode.ANDR       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.AND        to InstructionFormat.from("BW,<>r1,<i"),
    Opcode.ANDM       to InstructionFormat.from("BW,<r1,<>a"),
    Opcode.ORR        to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.OR         to InstructionFormat.from("BW,<>r1,<i"),
    Opcode.ORM        to InstructionFormat.from("BW,<r1,<>a"),
    Opcode.XORR       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.XOR        to InstructionFormat.from("BW,<>r1,<i"),
    Opcode.XORM       to InstructionFormat.from("BW,<r1,<>a"),
    Opcode.INV        to InstructionFormat.from("BW,<>r1"),
    Opcode.INVM       to InstructionFormat.from("BW,<>a"),
    Opcode.ASRN       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.ASRNM      to InstructionFormat.from("BW,<r1,<>a"),
    Opcode.LSRN       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.LSRNM      to InstructionFormat.from("BW,<r1,<>a"),
    Opcode.LSLN       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.LSLNM      to InstructionFormat.from("BW,<r1,<>a"),
    Opcode.ASR        to InstructionFormat.from("BW,<>r1"),
    Opcode.ASRM       to InstructionFormat.from("BW,<>a"),
    Opcode.LSR        to InstructionFormat.from("BW,<>r1"),
    Opcode.LSRM       to InstructionFormat.from("BW,<>a"),
    Opcode.LSL        to InstructionFormat.from("BW,<>r1"),
    Opcode.LSLM       to InstructionFormat.from("BW,<>a"),
    Opcode.ROR        to InstructionFormat.from("BW,<>r1"),
    Opcode.RORM       to InstructionFormat.from("BW,<>a"),
    Opcode.ROXR       to InstructionFormat.from("BW,<>r1"),
    Opcode.ROXRM      to InstructionFormat.from("BW,<>a"),
    Opcode.ROL        to InstructionFormat.from("BW,<>r1"),
    Opcode.ROLM       to InstructionFormat.from("BW,<>a"),
    Opcode.ROXL       to InstructionFormat.from("BW,<>r1"),
    Opcode.ROXLM      to InstructionFormat.from("BW,<>a"),

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
    Opcode.PUSH       to InstructionFormat.from("BW,<r1       | F,<fr1"),
    Opcode.POP        to InstructionFormat.from("BW,>r1       | F,>fr1"),
    Opcode.CONCAT     to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.CLC        to InstructionFormat.from("N"),
    Opcode.SEC        to InstructionFormat.from("N"),
    Opcode.BREAKPOINT to InstructionFormat.from("N"),
    Opcode.BINARYDATA to InstructionFormat.from("N"),
)


data class IRInstruction(
    val opcode: Opcode,
    val type: IRDataType?=null,
    val reg1: Int?=null,        // 0-$ffff
    val reg2: Int?=null,        // 0-$ffff
    val fpReg1: Int?=null,      // 0-$ffff
    val fpReg2: Int?=null,      // 0-$ffff
    val immediate: Int?=null,   // 0-$ff or $ffff if word
    val immediateFp: Float?=null,
    val address: Int?=null,       // 0-$ffff
    val labelSymbol: String?=null,          // symbolic label name as alternative to value (so only for Branch/jump/call Instructions!)
    val binaryData: Collection<UByte>?=null,
    var branchTarget: IRCodeChunkBase? = null    // will be linked after loading
) {
    // reg1 and fpreg1 can be IN/OUT/INOUT (all others are readonly INPUT)
    // This knowledge is useful in IL assembly optimizers to see how registers are used.
    val reg1direction: OperandDirection
    val reg2direction: OperandDirection
    val fpReg1direction: OperandDirection
    val fpReg2direction: OperandDirection

    init {
        require(labelSymbol?.first()!='_') {"label/symbol should not start with underscore $labelSymbol"}
        require(reg1==null || reg1 in 0..65536) {"reg1 out of bounds"}
        require(reg2==null || reg2 in 0..65536) {"reg2 out of bounds"}
        require(fpReg1==null || fpReg1 in 0..65536) {"fpReg1 out of bounds"}
        require(fpReg2==null || fpReg2 in 0..65536) {"fpReg2 out of bounds"}
        if(reg1!=null && reg2!=null) require(reg1!=reg2) {"reg1 must not be same as reg2"}  // note: this is ok for fpRegs as these are always the same type

        require((opcode==Opcode.BINARYDATA && binaryData!=null) || (opcode!=Opcode.BINARYDATA && binaryData==null)) {
            "binarydata inconsistency"
        }

        val formats = instructionFormats.getValue(opcode)
        require (type != null || formats.containsKey(null)) { "missing type" }

        val format = formats.getOrElse(type) { throw IllegalArgumentException("type $type invalid for $opcode") }
        if(format.reg1!=OperandDirection.UNUSED) require(reg1!=null) { "missing reg1" }
        if(format.reg2!=OperandDirection.UNUSED) require(reg2!=null) { "missing reg2" }
        if(format.fpReg1!=OperandDirection.UNUSED) require(fpReg1!=null) { "missing fpReg1" }
        if(format.fpReg2!=OperandDirection.UNUSED) require(fpReg2!=null) { "missing fpReg2" }
        if(format.reg1==OperandDirection.UNUSED) require(reg1==null) { "invalid reg1" }
        if(format.reg2==OperandDirection.UNUSED) require(reg2==null) { "invalid reg2" }
        if(format.fpReg1==OperandDirection.UNUSED) require(fpReg1==null) { "invalid fpReg1" }
        if(format.fpReg2==OperandDirection.UNUSED) require(fpReg2==null) { "invalid fpReg2" }
        if(format.immediate) {
            if(type==IRDataType.FLOAT) {
                if(opcode!=Opcode.SETPARAM)
                    require(immediateFp !=null) {"missing immediate fp value"}
                else
                    require(immediateFp==null) {"setparam never has immediateFp only immediate"}
            }
            else require(immediate!=null || labelSymbol!=null) {"missing immediate value or labelsymbol"}
        }
        if(type!=IRDataType.FLOAT)
            require(fpReg1==null && fpReg2==null) {"int instruction can't use fp reg"}
        if(format.address!=OperandDirection.UNUSED)
            require(address!=null || labelSymbol!=null) {"missing an address or labelsymbol"}
        if(format.immediate && (immediate!=null || immediateFp!=null)) {
            if(opcode!=Opcode.SYSCALL) {
                when (type) {
                    IRDataType.BYTE -> require(immediate in -128..255) { "immediate value out of range for byte: $immediate" }
                    IRDataType.WORD -> require(immediate in -32768..65535) { "immediate value out of range for word: $immediate" }
                    IRDataType.FLOAT, null -> {}
                }
            }
        }
        reg1direction = format.reg1
        reg2direction = format.reg2
        fpReg1direction = format.fpReg1
        fpReg2direction = format.fpReg2

        if(opcode==Opcode.SYSCALL) {
            require(immediate!=null) {
                "syscall needs immediate integer for the syscall number"
            }
        }
    }

    fun addUsedRegistersCounts(
        readRegsCounts: MutableMap<Int, Int>,
        writeRegsCounts: MutableMap<Int, Int>,
        readFpRegsCounts: MutableMap<Int, Int>,
        writeFpRegsCounts: MutableMap<Int, Int>,
        regsTypes: MutableMap<Int, MutableSet<IRDataType>>
    ) {
        when (this.reg1direction) {
            OperandDirection.UNUSED -> {}
            OperandDirection.READ -> {
                readRegsCounts[this.reg1!!] = readRegsCounts.getValue(this.reg1)+1
                if(type!=null) {
                    var types = regsTypes[this.reg1]
                    if(types==null) types = mutableSetOf()
                    types += type
                    regsTypes[this.reg1] = types
                }
            }
            OperandDirection.WRITE -> {
                writeRegsCounts[this.reg1!!] = writeRegsCounts.getValue(this.reg1)+1
                if(type!=null) {
                    var types = regsTypes[this.reg1]
                    if(types==null) types = mutableSetOf()
                    types += type
                    regsTypes[this.reg1] = types
                }
            }
            OperandDirection.READWRITE -> {
                readRegsCounts[this.reg1!!] = readRegsCounts.getValue(this.reg1)+1
                if(type!=null) {
                    var types = regsTypes[this.reg1]
                    if(types==null) types = mutableSetOf()
                    types += type
                    regsTypes[this.reg1] = types
                }
                writeRegsCounts[this.reg1] = writeRegsCounts.getValue(this.reg1)+1
                if(type!=null) {
                    var types = regsTypes[this.reg1]
                    if(types==null) types = mutableSetOf()
                    types += type
                    regsTypes[this.reg1] = types
                }
            }
        }
        when (this.reg2direction) {
            OperandDirection.UNUSED -> {}
            OperandDirection.READ -> {
                writeRegsCounts[this.reg2!!] = writeRegsCounts.getValue(this.reg2)+1
                if(type!=null) {
                    var types = regsTypes[this.reg2]
                    if(types==null) types = mutableSetOf()
                    types += type
                    regsTypes[this.reg2] = types
                }
            }
            else -> throw IllegalArgumentException("reg2 can only be read")
        }
        when (this.fpReg1direction) {
            OperandDirection.UNUSED -> {}
            OperandDirection.READ -> {
                readFpRegsCounts[this.fpReg1!!] = readFpRegsCounts.getValue(this.fpReg1)+1
            }
            OperandDirection.WRITE -> writeFpRegsCounts[this.fpReg1!!] = writeFpRegsCounts.getValue(this.fpReg1)+1
            OperandDirection.READWRITE -> {
                readFpRegsCounts[this.fpReg1!!] = readFpRegsCounts.getValue(this.fpReg1)+1
                writeFpRegsCounts[this.fpReg1] = writeFpRegsCounts.getValue(this.fpReg1)+1
            }
        }
        when (this.fpReg2direction) {
            OperandDirection.UNUSED -> {}
            OperandDirection.READ -> readFpRegsCounts[this.fpReg2!!] = readFpRegsCounts.getValue(this.fpReg2)+1
            else -> throw IllegalArgumentException("fpReg2 can only be read")
        }
    }

    override fun toString(): String {
        val result = mutableListOf(opcode.name.lowercase())

        when(type) {
            IRDataType.BYTE -> result.add(".b ")
            IRDataType.WORD -> result.add(".w ")
            IRDataType.FLOAT -> result.add(".f ")
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
        immediate?.let {
            result.add(it.toHex())
            result.add(",")
        }
        immediateFp?.let {
            result.add(it.toString())
            result.add(",")
        }
        address?.let {
            result.add(it.toHex())
            result.add(",")
        }
        labelSymbol?.let {
            result.add(it)
        }
        if(result.last() == ",")
            result.removeLast()
        return result.joinToString("").trimEnd()
    }
}
