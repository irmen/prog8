package prog8.intermediate

import prog8.code.core.RegisterOrStatusflag
import prog8.code.core.toHex

/*

Intermediate Representation instructions for the IR Virtual machine.
--------------------------------------------------------------------

Specs of the virtual machine this will run on:
Program to execute is not stored in the system memory, it's just a separate list of instructions.
65536 virtual registers, 16 bits wide, can also be used as 8 bits. r0-r65535
65536 virtual floating point registers (64 bits double precision)  fr0-fr65535
65536 bytes of memory. Thus memory pointers (addresses) are limited to 16 bits.
Value stack, max 128 entries of 1 byte each.
Status flags: Carry, Zero, Negative.   NOTE: status flags are only affected by the CMP instruction or explicit CLC/SEC,
                                             LOAD instructions DO affect the Z and N flags.
                                             INC/DEC/NEG instructions DO affect the Z and N flags,
                                             other instructions only affect Z an N flags if the value in a result register is written.
                                             See OpcodesThatSetStatusbits

Instruction set is mostly a load/store architecture, there are few instructions operating on memory directly.

Value types: integers (.b=byte=8 bits, .w=word=16 bits) and float (.f=64 bits). Omitting it defaults to b if the instruction requires a type.
Currently ther is NO support for 24 or 32 bits integers.
There is no distinction between signed and unsigned integers.
Instead, a different instruction is used if a distinction should be made (for example div and divs).
Floating point operations are just 'f' typed regular instructions, however there are a few unique fp conversion instructions.

NOTE: Labels in source text should always start with an underscore.


LOAD/STORE
----------
All have type b or w or f.

load        reg1,         value       - load immediate value into register. If you supply a symbol, loads the *address* of the symbol! (variable values are loaded from memory via the loadm instruction)
loadm       reg1,         address     - load reg1 with value at memory address
loadi       reg1, reg2                - load reg1 with value at memory indirect, memory pointed to by reg2
loadx       reg1, reg2,   address     - load reg1 with value at memory address indexed by value in reg2 (only the lsb part used for indexing)
loadix      reg1, reg2,   pointeraddr - load reg1 with value at memory indirect, pointed to by pointeraddr indexed by value in reg2 (only the lsb part used for indexing)
loadr       reg1, reg2                - load reg1 with value in register reg2,  "reg1 = reg2"
loadha      reg1                      - load cpu hardware register A into reg1.b
loadhx      reg1                      - load cpu hardware register X into reg1.b
loadhy      reg1                      - load cpu hardware register Y into reg1.b
loadhax     reg1                      - load cpu hardware register pair AX into reg1.w
loadhay     reg1                      - load cpu hardware register pair AY into reg1.w
loadhxy     reg1                      - load cpu hardware register pair XY into reg1.w
loadfaczero       fpreg1              - load "cpu hardware register" fac0 into freg1.f
loadfacone        fpreg1              - load "cpu hardware register" fac1 into freg1.f
storem      reg1,         address     - store reg1 at memory address
storei      reg1, reg2                - store reg1 at memory indirect, memory pointed to by reg2
storex      reg1, reg2,   address     - store reg1 at memory address, indexed by value in reg2 (only the lsb part used for indexing)
storeix     reg1, reg2,   pointeraddr - store reg1 at memory indirect, pointed to by pointeraddr indexed by value in reg2 (only the lsb part used for indexing)
storezm                   address     - store zero at memory address
storezi     reg1                      - store zero at memory pointed to by reg1
storezx     reg1,         address     - store zero at memory address, indexed by value in reg1 (only the lsb part used for indexing)
storeha     reg1                      - store reg1.b into cpu hardware register A
storehx     reg1                      - store reg1.b into cpu hardware register X
storehy     reg1                      - store reg1.b into cpu hardware register Y
storehax    reg1                      - store reg1.w into cpu hardware register pair AX
storehay    reg1                      - store reg1.w into cpu hardware register pair AY
storehxy    reg1                      - store reg1.w into cpu hardware register pair XY
storehfaczero        fpreg1           - store fpreg1.f into "cpu register" fac0
storehfacone         fpreg1           - store fpreg1.f into "cpu register" fac1


CONTROL FLOW
------------
jump                    location      - continue running at instruction at 'location' (label/memory address)
jumpi       reg1                      - continue running at memory address in reg1  (indirect jump)
preparecall numparams                 - indicator that the next instructions are the param setup and function call/syscall with <numparams> parameters
calli       reg1                      - calls a subroutine (without arguments and without return valus) at memory addres in reg1 (indirect jsr)
call   label(argument register list) [: resultreg.type]
                                      - calls a subroutine with the given arguments and return value (optional).
                                        save current instruction location+1, continue execution at instruction nr of the label.
                                        the argument register list is positional and includes the datatype, ex.: r4.b,r5.w,fp1.f
                                        If the call is to a rom-routine, 'label' will be a hexadecimal address instead such as $ffd2
                                        If the arguments should be passed in CPU registers, they'll have a @REGISTER postfix.
                                        For example: call $ffd2(r5.b@A)
                                        Always preceded by parameter setup and preparecall instructions
syscall   number (argument register list) [: resultreg.type]
                                      - do a systemcall identified by number, result value(s) are pushed on value stack by the syscall code so
                                        will be POPped off into the given resultregister if any.
                                        Always preceded by parameter setup and preparecall instructions
return                                - restore last saved instruction location and continue at that instruction. No return value.
returnr     reg1                      - like return, but also returns the value in reg1 to the caller
returni            number             - like return, but also returns the immediate value to the caller



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

(unsigned comparison branches:)
bgt         reg1, value,      address   - jump to location in program given by location, if reg1 > immediate value (unsigned)
blt         reg1, value,      address   - jump to location in program given by location, if reg1 < immediate value (unsigned)
bgtr        reg1, reg2,       address   - jump to location in program given by location, if reg1 > reg2 (unsigned)
'bltr'      reg1, reg2,       address   - jump to location in program given by location, if reg1 < reg2 (unsigned) ==> use bgtr with swapped operands
bge         reg1, value,      address   - jump to location in program given by location, if reg1 >= immediate value (unsigned)
ble         reg1, value,      address   - jump to location in program given by location, if reg1 <= immediate value (unsigned)
bger        reg1, reg2,       address   - jump to location in program given by location, if reg1 >= reg2 (unsigned)
'bler'      reg1, reg2,       address   - jump to location in program given by location, if reg1 <= reg2 (unsigned) ==> use bger with swapped operands

(signed comparison branches:)
bgts        reg1, value,      address   - jump to location in program given by location, if reg1 > immediate value (signed)
blts        reg1, value,      address   - jump to location in program given by location, if reg1 < immediate value (signed)
bgtsr       reg1, reg2,       address   - jump to location in program given by location, if reg1 > reg2 (signed)
'bltsr'     reg1, reg2,       address   - jump to location in program given by location, if reg1 < reg2 (signed) ==> use bgtsr with swapped operands
bges        reg1, value,      address   - jump to location in program given by location, if reg1 >= immediate value (signed)
bles        reg1, value,      address   - jump to location in program given by location, if reg1 <= immediate value (signed)
bgesr       reg1, reg2,       address   - jump to location in program given by location, if reg1 >= reg2 (signed)
'blesr'     reg1, reg2,       address   - jump to location in program given by location, if reg1 <= reg2 (signed) ==> use bgesr with swapped operands


ARITHMETIC
----------
All have type b or w or f. Note: result types are the same as operand types! E.g. byte*byte->byte.

exts        reg1, reg2                      - reg1 = signed extension of reg2 (byte to word, or word to long)  (note: unlike M68k, exts.b -> word and exts.w -> long. The latter is not yet implemented yet as we don't have longs yet)
ext         reg1, reg2                      - reg1 = unsigned extension of reg2 (which in practice just means clearing the MSB / MSW) (note: unlike M68k, ext.b -> word and ext.w -> long. The latter is not yet implemented yet as we don't have longs yet)
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
sqrt        reg1, reg2                      - reg1 is the square root of reg2 (reg2 can be .w or .b, result type in reg1 is always .b)  you can also use it with floating point types, fpreg1 and fpreg2 (result is also .f)
square      reg1, reg2                      - reg1 is the square of reg2 (reg2 can be .w or .b, result type in reg1 is always .b)  you can also use it with floating point types, fpreg1 and fpreg2 (result is also .f)
sgn         reg1, reg2                      - reg1.b is the sign of reg2 (or fpreg1, if sgn.f) (0.b, 1.b or -1.b)
cmp         reg1, reg2                      - set processor status bits C, N, Z according to comparison of reg1 with reg2. (semantics taken from 6502/68000 CMP instruction)
cmpi        reg1,              value        - set processor status bits C, N, Z according to comparison of reg1 with immediate value. (semantics taken from 6502/68000 CMP instruction)

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
bit                      address             - test bits in byte value at address, this is a special instruction available on other systems to optimize testing and branching on bits 7 and 6


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
cli                                       - clear interrupt disable flag
sei                                       - set interrupt disable flag
nop                                       - do nothing
breakpoint                                - trigger a breakpoint
align        alignmentvalue               - represents a memory alignment directive
lsig [b, w]   reg1, reg2                  - reg1 becomes the least significant byte (or word) of the word (or int) in reg2  (.w not yet implemented; requires 32 bits regs)
msig [b, w]   reg1, reg2                  - reg1 becomes the most significant byte (or word) of the word (or int) in reg2  (.w not yet implemented; requires 32 bits regs)
concat [b, w] reg1, reg2, reg3            - reg1.w = 'concatenate' two registers: lsb/lsw of reg2 (as msb) and lsb/lsw of reg3 (as lsb) into word or int (int not yet implemented; requires 32bits regs)
push [b, w, f]   reg1                     - push value in reg1 on the stack
pop [b, w, f]    reg1                     - pop value from stack into reg1
pushst                                    - push status register bits to stack
popst                                     - pop status register bits from stack
 */

enum class Opcode {
    NOP,
    LOAD,       // note: LOAD <symbol>  gets you the address of the symbol, whereas LOADM <symbol> would get you the value stored at that location
    LOADM,
    LOADI,
    LOADX,
    LOADIX,
    LOADR,
    LOADHA,
    LOADHX,
    LOADHY,
    LOADHAX,
    LOADHAY,
    LOADHXY,
    LOADHFACZERO,
    LOADHFACONE,
    STOREM,
    STOREI,
    STOREX,
    STOREIX,
    STOREZM,
    STOREZI,
    STOREZX,
    STOREHA,
    STOREHX,
    STOREHY,
    STOREHAX,
    STOREHAY,
    STOREHXY,
    STOREHFACZERO,
    STOREHFACONE,

    JUMP,
    JUMPI,
    PREPARECALL,
    CALLI,
    CALL,
    SYSCALL,
    RETURN,
    RETURNR,
    RETURNI,

    BSTCC,
    BSTCS,
    BSTEQ,
    BSTNE,
    BSTNEG,
    BSTPOS,
    BSTVC,
    BSTVS,
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
    SQUARE,
    SGN,
    CMP,
    CMPI,
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
    BIT,

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
    CLI,
    SEI,
    PUSH,
    POP,
    PUSHST,
    POPST,
    LSIG,
    MSIG,
    CONCAT,
    BREAKPOINT,
    ALIGN
}

val OpcodesThatJump = arrayOf(
    Opcode.JUMP,
    Opcode.JUMPI,
    Opcode.RETURN,
    Opcode.RETURNR,
    Opcode.RETURNI
)

val OpcodesThatBranch = arrayOf(
    Opcode.JUMP,
    Opcode.JUMPI,
    Opcode.RETURN,
    Opcode.RETURNR,
    Opcode.RETURNI,
    Opcode.CALLI,
    Opcode.CALL,
    Opcode.SYSCALL,
    Opcode.BSTCC,
    Opcode.BSTCS,
    Opcode.BSTEQ,
    Opcode.BSTNE,
    Opcode.BSTNEG,
    Opcode.BSTPOS,
    Opcode.BSTVC,
    Opcode.BSTVS,
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

val OpcodesThatSetStatusbitsIncludingCarry = arrayOf(
    Opcode.BIT,
    Opcode.CMP,
    Opcode.CMPI
)
val OpcodesThatSetStatusbitsButNotCarry = arrayOf(
    Opcode.LOAD,
    Opcode.LOADM,
    Opcode.LOADI,
    Opcode.LOADX,
    Opcode.LOADIX,
    Opcode.LOADR,
    Opcode.LOADHA,
    Opcode.LOADHX,
    Opcode.LOADHY,
    Opcode.LOADHAX,
    Opcode.LOADHAY,
    Opcode.LOADHXY,
    Opcode.NEG,
    Opcode.NEGM,
    Opcode.INC,
    Opcode.INCM,
    Opcode.DEC,
    Opcode.DECM,
    Opcode.ANDM,
    Opcode.ANDR,
    Opcode.AND,
    Opcode.ORM,
    Opcode.ORR,
    Opcode.OR,
    Opcode.XORM,
    Opcode.XORR,
    Opcode.XOR,
)

val OpcodesThatDependOnCarry = arrayOf(
    Opcode.BSTCC,
    Opcode.BSTCS,
    Opcode.BSTPOS,
    Opcode.BSTNEG,
    Opcode.ROXL,
    Opcode.ROXLM,
    Opcode.ROXR,
    Opcode.ROXRM,
)

val OpcodesThatSetStatusbits = OpcodesThatSetStatusbitsButNotCarry + OpcodesThatSetStatusbitsIncludingCarry


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
                             val reg3: OperandDirection,
                             val fpReg1: OperandDirection,
                             val fpReg2: OperandDirection,
                             val address: OperandDirection,
                             val immediate: Boolean,
                             val funcCall: Boolean,
                             val sysCall: Boolean) {
    companion object {
        fun from(spec: String): Map<IRDataType?, InstructionFormat> {
            val result = mutableMapOf<IRDataType?, InstructionFormat>()
            for(part in spec.split('|').map{ it.trim() }) {
                var reg1 = OperandDirection.UNUSED
                var reg2 = OperandDirection.UNUSED
                var reg3 = OperandDirection.UNUSED
                var fpreg1 = OperandDirection.UNUSED
                var fpreg2 = OperandDirection.UNUSED
                var address = OperandDirection.UNUSED
                var immediate = false
                val splits = part.splitToSequence(',').iterator()
                val typespec = splits.next()
                var funcCall = false
                var sysCall = false
                while(splits.hasNext()) {
                    when(splits.next()) {
                        "<r1" -> reg1 = OperandDirection.READ
                        ">r1" -> reg1 = OperandDirection.WRITE
                        "<>r1" -> reg1 = OperandDirection.READWRITE
                        "<r2" -> reg2 = OperandDirection.READ
                        "<r3" -> reg3 = OperandDirection.READ
                        "<fr1" -> fpreg1 = OperandDirection.READ
                        ">fr1" -> fpreg1 = OperandDirection.WRITE
                        "<>fr1" -> fpreg1 = OperandDirection.READWRITE
                        "<fr2" -> fpreg2 = OperandDirection.READ
                        ">i", "<>i" -> throw IllegalArgumentException("can't write into an immediate value")
                        "<i" -> immediate = true
                        "<a" -> address = OperandDirection.READ
                        ">a" -> address = OperandDirection.WRITE
                        "<>a" -> address = OperandDirection.READWRITE
                        "call" -> funcCall = true
                        "syscall" -> sysCall = true
                        else -> throw IllegalArgumentException(spec)
                    }
                }

                if(typespec=="N")
                    result[null] = InstructionFormat(null, reg1, reg2, reg3, fpreg1, fpreg2, address, immediate, funcCall, sysCall)
                if('B' in typespec)
                    result[IRDataType.BYTE] = InstructionFormat(IRDataType.BYTE, reg1, reg2, reg3, fpreg1, fpreg2, address, immediate, funcCall, sysCall)
                if('W' in typespec)
                    result[IRDataType.WORD] = InstructionFormat(IRDataType.WORD, reg1, reg2, reg3, fpreg1, fpreg2, address, immediate, funcCall, sysCall)
                if('F' in typespec)
                    result[IRDataType.FLOAT] = InstructionFormat(IRDataType.FLOAT, reg1, reg2, reg3, fpreg1, fpreg2, address, immediate, funcCall, sysCall)
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
    Opcode.LOADHA     to InstructionFormat.from("B,>r1"),
    Opcode.LOADHA     to InstructionFormat.from("B,>r1"),
    Opcode.LOADHX     to InstructionFormat.from("B,>r1"),
    Opcode.LOADHY     to InstructionFormat.from("B,>r1"),
    Opcode.LOADHAX    to InstructionFormat.from("W,>r1"),
    Opcode.LOADHAY    to InstructionFormat.from("W,>r1"),
    Opcode.LOADHXY    to InstructionFormat.from("W,>r1"),
    Opcode.LOADHFACZERO to InstructionFormat.from("F,>fr1"),
    Opcode.LOADHFACONE  to InstructionFormat.from("F,>fr1"),
    Opcode.STOREM     to InstructionFormat.from("BW,<r1,>a     | F,<fr1,>a"),
    Opcode.STOREI     to InstructionFormat.from("BW,<r1,<r2    | F,<fr1,<r1"),
    Opcode.STOREX     to InstructionFormat.from("BW,<r1,<r2,>a | F,<fr1,<r1,>a"),
    Opcode.STOREIX    to InstructionFormat.from("BW,<r1,<r2,>a | F,<fr1,<r1,>a"),
    Opcode.STOREZM    to InstructionFormat.from("BW,>a         | F,>a"),
    Opcode.STOREZI    to InstructionFormat.from("BW,<r1        | F,<r1"),
    Opcode.STOREZX    to InstructionFormat.from("BW,<r1,>a     | F,<r1,>a"),
    Opcode.STOREHA    to InstructionFormat.from("B,<r1"),
    Opcode.STOREHA    to InstructionFormat.from("B,<r1"),
    Opcode.STOREHX    to InstructionFormat.from("B,<r1"),
    Opcode.STOREHY    to InstructionFormat.from("B,<r1"),
    Opcode.STOREHAX   to InstructionFormat.from("W,<r1"),
    Opcode.STOREHAY   to InstructionFormat.from("W,<r1"),
    Opcode.STOREHXY   to InstructionFormat.from("W,<r1"),
    Opcode.STOREHFACZERO  to InstructionFormat.from("F,<fr1"),
    Opcode.STOREHFACONE  to InstructionFormat.from("F,<fr1"),
    Opcode.JUMP       to InstructionFormat.from("N,<a"),
    Opcode.JUMPI      to InstructionFormat.from("N,<r1"),
    Opcode.PREPARECALL to InstructionFormat.from("N,<i"),
    Opcode.CALLI      to InstructionFormat.from("N,<r1"),
    Opcode.CALL       to InstructionFormat.from("N,call"),
    Opcode.SYSCALL    to InstructionFormat.from("N,syscall"),
    Opcode.RETURN     to InstructionFormat.from("N"),
    Opcode.RETURNR    to InstructionFormat.from("BW,<r1        | F,<fr1"),
    Opcode.RETURNI    to InstructionFormat.from("BW,<i         | F,<i"),
    Opcode.BSTCC      to InstructionFormat.from("N,<a"),
    Opcode.BSTCS      to InstructionFormat.from("N,<a"),
    Opcode.BSTEQ      to InstructionFormat.from("N,<a"),
    Opcode.BSTNE      to InstructionFormat.from("N,<a"),
    Opcode.BSTNEG     to InstructionFormat.from("N,<a"),
    Opcode.BSTPOS     to InstructionFormat.from("N,<a"),
    Opcode.BSTVC      to InstructionFormat.from("N,<a"),
    Opcode.BSTVS      to InstructionFormat.from("N,<a"),
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
    Opcode.SQUARE     to InstructionFormat.from("BW,>r1,<r2   | F,>fr1,<fr2"),
    Opcode.SGN        to InstructionFormat.from("BW,>r1,<r2   | F,>r1,<fr1"),
    Opcode.MODR       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.MOD        to InstructionFormat.from("BW,<>r1,<i"),
    Opcode.DIVMODR    to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.DIVMOD     to InstructionFormat.from("BW,<>r1,<i"),
    Opcode.CMP        to InstructionFormat.from("BW,<r1,<r2"),
    Opcode.CMPI       to InstructionFormat.from("BW,<r1,<i"),
    Opcode.EXT        to InstructionFormat.from("BW,>r1,<r2"),
    Opcode.EXTS       to InstructionFormat.from("BW,>r1,<r2"),
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
    Opcode.BIT        to InstructionFormat.from("B,<a"),

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

    Opcode.LSIG       to InstructionFormat.from("BW,>r1,<r2"),
    Opcode.MSIG       to InstructionFormat.from("BW,>r1,<r2"),
    Opcode.PUSH       to InstructionFormat.from("BW,<r1       | F,<fr1"),
    Opcode.POP        to InstructionFormat.from("BW,>r1       | F,>fr1"),
    Opcode.PUSHST     to InstructionFormat.from("N"),
    Opcode.POPST      to InstructionFormat.from("N"),
    Opcode.CONCAT     to InstructionFormat.from("BW,<>r1,<r2,<r3"),
    Opcode.CLC        to InstructionFormat.from("N"),
    Opcode.SEC        to InstructionFormat.from("N"),
    Opcode.CLI        to InstructionFormat.from("N"),
    Opcode.SEI        to InstructionFormat.from("N"),
    Opcode.BREAKPOINT to InstructionFormat.from("N"),
    Opcode.ALIGN      to InstructionFormat.from("N,<i"),
)


class FunctionCallArgs(
    var arguments: List<ArgumentSpec>,
    val returns: List<RegSpec>
) {
    class RegSpec(val dt: IRDataType, val registerNum: Int, val cpuRegister: RegisterOrStatusflag?)
    class ArgumentSpec(val name: String, val address: Int?, val reg: RegSpec) {
        init {
            require(address==null || address>=0) {
                "address must be >=0"
            }
        }
    }
}

data class IRInstruction(
    val opcode: Opcode,
    val type: IRDataType?=null,
    val reg1: Int?=null,        // 0-$ffff
    val reg2: Int?=null,        // 0-$ffff
    val reg3: Int?=null,        // 0-$ffff
    val fpReg1: Int?=null,      // 0-$ffff
    val fpReg2: Int?=null,      // 0-$ffff
    val immediate: Int?=null,   // 0-$ff or $ffff if word
    val immediateFp: Double?=null,
    val address: Int?=null,       // 0-$ffff
    val labelSymbol: String?=null,          // symbolic label name as alternative to address (so only for Branch/jump/call Instructions!)
    private val symbolOffset: Int? = null,     // offset to add on labelSymbol (used to index into an array variable)
    var branchTarget: IRCodeChunkBase? = null,    // Will be linked after loading in IRProgram.linkChunks()! This is the chunk that the branch labelSymbol points to.
    val fcallArgs: FunctionCallArgs? = null       // will be set for the CALL and SYSCALL instructions.
) {
    // reg1 and fpreg1 can be IN/OUT/INOUT (all others are readonly INPUT)
    // This knowledge is useful in IL assembly optimizers to see how registers are used.
    val reg1direction: OperandDirection
    val reg2direction: OperandDirection
    val reg3direction: OperandDirection
    val fpReg1direction: OperandDirection
    val fpReg2direction: OperandDirection
    val labelSymbolOffset = if(symbolOffset==0) null else symbolOffset

    init {
        if(labelSymbol!=null) {
            require(labelSymbol.first() != '_') { "label/symbol should not start with underscore $labelSymbol" }
            require(labelSymbol.all { it.isJavaIdentifierStart() || it.isJavaIdentifierPart() || it=='.' }) {
                "label/symbol contains invalid character $labelSymbol"
            }
        }
        if(labelSymbolOffset!=null) require(labelSymbolOffset>0 && labelSymbol!=null) {"labelsymbol offset inconsistency"}
        require(reg1==null || reg1 in 0..65536) {"reg1 out of bounds"}
        require(reg2==null || reg2 in 0..65536) {"reg2 out of bounds"}
        require(reg3==null || reg3 in 0..65536) {"reg3 out of bounds"}
        require(fpReg1==null || fpReg1 in 0..65536) {"fpReg1 out of bounds"}
        require(fpReg2==null || fpReg2 in 0..65536) {"fpReg2 out of bounds"}
        if(reg1!=null && reg2!=null) require(reg1!=reg2) {"reg1 must not be same as reg2"}  // note: this is ok for fpRegs as these are always the same type
        if(reg1!=null && reg3!=null) require(reg1!=reg3) {"reg1 must not be same as reg3"}  // note: this is ok for fpRegs as these are always the same type
        if(reg2!=null && reg3!=null) require(reg2!=reg3) {"reg2 must not be same as reg3"}  // note: this is ok for fpRegs as these are always the same type

        val formats = instructionFormats.getValue(opcode)
        require (type != null || formats.containsKey(null)) { "missing type" }

        val format = formats.getOrElse(type) { throw IllegalArgumentException("type $type invalid for $opcode") }
        if(format.reg1!=OperandDirection.UNUSED) require(reg1!=null) { "missing reg1" }
        if(format.reg2!=OperandDirection.UNUSED) require(reg2!=null) { "missing reg2" }
        if(format.reg3!=OperandDirection.UNUSED) require(reg3!=null) { "missing reg3" }
        if(format.fpReg1!=OperandDirection.UNUSED) require(fpReg1!=null) { "missing fpReg1" }
        if(format.fpReg2!=OperandDirection.UNUSED) require(fpReg2!=null) { "missing fpReg2" }
        if(format.reg1==OperandDirection.UNUSED) require(reg1==null) { "invalid reg1" }
        if(format.reg2==OperandDirection.UNUSED) require(reg2==null) { "invalid reg2" }
        if(format.reg3==OperandDirection.UNUSED) require(reg3==null) { "invalid reg3" }
        if(format.fpReg1==OperandDirection.UNUSED) require(fpReg1==null) { "invalid fpReg1" }
        if(format.fpReg2==OperandDirection.UNUSED) require(fpReg2==null) { "invalid fpReg2" }
        if(format.immediate) {
            if(type==IRDataType.FLOAT)
                requireNotNull(immediateFp) {"missing immediate fp value"}
            else
                require(immediate!=null || labelSymbol!=null) {"missing immediate value or labelsymbol"}
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
        if(format.immediate) {
            if(opcode==Opcode.LOAD)
                require(immediate != null || immediateFp != null || labelSymbol!=null) { "missing immediate value or labelsymbol" }
            else
                require(immediate != null || immediateFp != null) { "missing immediate value" }
        }
        require(address==null || address>=0) {
            "address must be >=0"
        }

        reg1direction = format.reg1
        reg2direction = format.reg2
        reg3direction = format.reg3
        fpReg1direction = format.fpReg1
        fpReg2direction = format.fpReg2

        if(opcode==Opcode.SYSCALL) {
            requireNotNull(immediate) { "syscall needs immediate integer for the syscall number" }
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
                val actualtype = determineReg1Type()
                if(actualtype!=null) {
                    var types = regsTypes[this.reg1]
                    if(types==null) types = mutableSetOf()
                    types += actualtype
                    regsTypes[this.reg1] = types
                }
            }
            OperandDirection.WRITE -> {
                writeRegsCounts[this.reg1!!] = writeRegsCounts.getValue(this.reg1)+1
                val actualtype = determineReg1Type()
                if(actualtype!=null) {
                    var types = regsTypes[this.reg1]
                    if(types==null) types = mutableSetOf()
                    types += actualtype
                    regsTypes[this.reg1] = types
                }
            }
            OperandDirection.READWRITE -> {
                readRegsCounts[this.reg1!!] = readRegsCounts.getValue(this.reg1)+1
                writeRegsCounts[this.reg1] = writeRegsCounts.getValue(this.reg1)+1
                val actualtype = determineReg1Type()
                if(actualtype!=null) {
                    var types = regsTypes[this.reg1]
                    if(types==null) types = mutableSetOf()
                    types += actualtype
                    regsTypes[this.reg1] = types
                }
            }
        }
        when (this.reg2direction) {
            OperandDirection.UNUSED -> {}
            OperandDirection.READ -> {
                writeRegsCounts[this.reg2!!] = writeRegsCounts.getValue(this.reg2)+1
                val actualtype = determineReg2Type()
                if(actualtype!=null) {
                    var types = regsTypes[this.reg2]
                    if(types==null) types = mutableSetOf()
                    types += actualtype
                    regsTypes[this.reg2] = types
                }
            }
            else -> throw IllegalArgumentException("reg2 can only be read")
        }
        when (this.reg3direction) {
            OperandDirection.UNUSED -> {}
            OperandDirection.READ -> {
                writeRegsCounts[this.reg3!!] = writeRegsCounts.getValue(this.reg3)+1
                val actualtype = determineReg3Type()
                if(actualtype!=null) {
                    var types = regsTypes[this.reg3]
                    if(types==null) types = mutableSetOf()
                    types += actualtype
                    regsTypes[this.reg3] = types
                }
            }
            else -> throw IllegalArgumentException("reg3 can only be read")
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

        if(fcallArgs!=null) {
            fcallArgs.returns.forEach {
                if (it.dt == IRDataType.FLOAT)
                    writeFpRegsCounts[it.registerNum] = writeFpRegsCounts.getValue(it.registerNum) + 1
                else {
                    writeRegsCounts[it.registerNum] = writeRegsCounts.getValue(it.registerNum) + 1
                    val types = regsTypes[it.registerNum]
                    if (types == null) {
                        regsTypes[it.registerNum] = mutableSetOf(it.dt)
                    } else {
                        types += it.dt
                        regsTypes[it.registerNum] = types
                    }
                }
            }
            fcallArgs.arguments.forEach {
                if(it.reg.dt==IRDataType.FLOAT)
                    readFpRegsCounts[it.reg.registerNum] = readFpRegsCounts.getValue(it.reg.registerNum)+1
                else {
                    readRegsCounts[it.reg.registerNum] = readRegsCounts.getValue(it.reg.registerNum) + 1
                    val types = regsTypes[it.reg.registerNum]
                    if(types==null) {
                        regsTypes[it.reg.registerNum] = mutableSetOf(it.reg.dt)
                    } else {
                        types += it.reg.dt
                        regsTypes[it.reg.registerNum] = types
                    }
                }
            }
        }
    }

    private fun determineReg1Type(): IRDataType? {
        if(type==IRDataType.FLOAT) {
            // some float instructions have an integer register as well.
            if(opcode in arrayOf(Opcode.FFROMUB, Opcode.FFROMSB, Opcode.FTOUB, Opcode.FTOSB, Opcode.FCOMP))
                return IRDataType.BYTE
            else
                return IRDataType.WORD
        }
        if(opcode==Opcode.JUMPI || opcode==Opcode.CALLI || opcode==Opcode.STOREZI)
            return IRDataType.WORD
        if(opcode==Opcode.EXT || opcode==Opcode.EXTS)
            return when(type) {
                IRDataType.BYTE -> IRDataType.WORD
                IRDataType.WORD -> TODO("ext.w into long type")
                else -> null
            }
        if(opcode==Opcode.CONCAT)
            return when(type) {
                IRDataType.BYTE -> IRDataType.WORD
                IRDataType.WORD -> TODO("concat.w from long type")
                else -> null
            }
        if(opcode==Opcode.ASRNM || opcode==Opcode.LSRNM || opcode==Opcode.LSLNM || opcode==Opcode.SQRT)
            return IRDataType.BYTE
        return this.type
    }

    private fun determineReg2Type(): IRDataType? {
        if(opcode==Opcode.LOADI || opcode==Opcode.STOREI)
            return IRDataType.WORD
        if(opcode==Opcode.MSIG || opcode==Opcode.LSIG)
            return when(type) {
                IRDataType.BYTE -> IRDataType.WORD
                IRDataType.WORD -> TODO("msig/lsig.w from long type")
                else -> null
            }
        if(opcode==Opcode.ASRN || opcode==Opcode.LSRN || opcode==Opcode.LSLN)
            return IRDataType.BYTE
        return this.type
    }

    private fun determineReg3Type(): IRDataType? {
        return this.type
    }

    override fun toString(): String {
        val result = mutableListOf(opcode.name.lowercase())

        when(type) {
            IRDataType.BYTE -> result.add(".b ")
            IRDataType.WORD -> result.add(".w ")
            IRDataType.FLOAT -> result.add(".f ")
            else -> result.add(" ")
        }

        if(this.fcallArgs!=null) {
            immediate?.let { result.add(it.toHex()) }       // syscall
            if(labelSymbol!=null) {
                // regular subroutine call
                result.add(labelSymbol)
                if(labelSymbolOffset!=null)
                    result.add("+$labelSymbolOffset")
            }
            address?.let { result.add(address.toHex()) }    // romcall
            result.add("(")
            fcallArgs.arguments.forEach {
                val location = if(it.address==null) {
                    if(it.name.isBlank()) "" else it.name+"="
                } else "${it.address}="

                val cpuReg = if(it.reg.cpuRegister==null) "" else {
                    if(it.reg.cpuRegister.registerOrPair!=null)
                        "@"+it.reg.cpuRegister.registerOrPair.toString()
                    else
                        "@"+it.reg.cpuRegister.statusflag.toString()
                }

                when(it.reg.dt) {
                    IRDataType.BYTE -> result.add("${location}r${it.reg.registerNum}.b$cpuReg,")
                    IRDataType.WORD -> result.add("${location}r${it.reg.registerNum}.w$cpuReg,")
                    IRDataType.FLOAT -> result.add("${location}fr${it.reg.registerNum}.f$cpuReg,")
                }
            }
            if(result.last().endsWith(',')) {
                result.add(result.removeLast().trimEnd(','))
            }
            result.add(")")
            val returns = fcallArgs.returns
            if(returns.isNotEmpty()) {
                result.add(":")
                val resultParts = returns.map { returnspec ->
                    val cpuReg = if (returnspec.cpuRegister == null) "" else {
                        if (returnspec.cpuRegister.registerOrPair != null)
                            returnspec.cpuRegister.registerOrPair.toString()
                        else
                            returnspec.cpuRegister.statusflag.toString()
                    }
                    if (cpuReg.isEmpty()) {
                        when (returnspec.dt) {
                            IRDataType.BYTE -> "r${returnspec.registerNum}.b"
                            IRDataType.WORD -> "r${returnspec.registerNum}.w"
                            IRDataType.FLOAT -> "fr${returnspec.registerNum}.f"
                        }
                    } else {
                        when (returnspec.dt) {
                            IRDataType.BYTE -> "r${returnspec.registerNum}.b@" + cpuReg
                            IRDataType.WORD -> "r${returnspec.registerNum}.w@" + cpuReg
                            IRDataType.FLOAT -> "r${returnspec.registerNum}.f@" + cpuReg
                        }
                    }
                }
                result.add(resultParts.joinToString(","))
            }
        } else {

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
            fpReg1?.let {
                result.add("fr$it")
                result.add(",")
            }
            fpReg2?.let {
                result.add("fr$it")
                result.add(",")
            }
            immediate?.let {
                result.add("#${it.toHex()}")
                result.add(",")
            }
            immediateFp?.let {
                result.add("#${it}")
                result.add(",")
            }
            address?.let {
                result.add(it.toHex())
                result.add(",")
            }
            labelSymbol?.let {
                result.add(it)
                if(labelSymbolOffset!=null)
                    result.add("+$labelSymbolOffset")
            }
        }
        if(result.last() == ",")
            result.removeLast()
        return result.joinToString("").trimEnd()
    }
}
