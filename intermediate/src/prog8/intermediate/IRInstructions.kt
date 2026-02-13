package prog8.intermediate

import prog8.code.core.AssemblyError
import prog8.code.core.RegisterOrStatusflag
import prog8.code.core.toHex

/*

Intermediate Representation instructions for the IR Virtual machine.
--------------------------------------------------------------------

Specs of the virtual machine this will run on:
Program to execute is not stored in the system memory, it's just a separate list of instructions.
100K virtual registers, 16 bits wide, can also be used as 8 bits. r0-r99999
    reserved 99000 - 99099 : WORD registers for syscall arguments and response value(s)
    reserved 99100 - 99199 : BYTE registers for syscall arguments and response value(s)
    reseverd 99200 - 99299 : LONG registers for syscall arguments and response value(s)
100K virtual floating point registers (64 bits double precision)  fr0-fr99999
65536 bytes of memory. Thus memory pointers (addresses) are limited to 16 bits.
Value stack, max 128 entries of 1 byte each.
Status flags: Carry, Zero, Negative, Overflow.
NOTE: status flags are only affected by the CMP instruction or explicit CLC/SEC,
      LOAD instructions also DO affect the Z and N flags.
      INC/DEC/NEG instructions also DO affect the Z and N flags,
      other instructions also only affect Z an N flags if the value in a result register is written.
      See OpcodesThatSetStatusbits.

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
loadx       reg1, reg2,   address     - load reg1 with value at memory address indexed by value in reg2 (0-255, a byte)
loadr       reg1, reg2                - load reg1 with value in register reg2,  "reg1 = reg2"
loadfield   reg1, reg2,   value       - load reg1 with value in memory indirect, pointed to by reg2 + value 0-65535 (gets a field from a pointer to a struct, like LOADI with additional field offset 0-65535)
loadha      reg1                      - load cpu hardware register A into reg1.b
loadhx      reg1                      - load cpu hardware register X into reg1.b
loadhy      reg1                      - load cpu hardware register Y into reg1.b
loadhax     reg1                      - load cpu hardware register pair AX into reg1.w
loadhay     reg1                      - load cpu hardware register pair AY into reg1.w
loadhxy     reg1                      - load cpu hardware register pair XY into reg1.w
loadhfaczero       fpreg1             - load "cpu hardware register" fac0 into freg1.f
loadhfacone        fpreg1             - load "cpu hardware register" fac1 into freg1.f
storem      reg1,         address     - store reg1 at memory address
storei      reg1, reg2                - store reg1 at memory indirect, memory pointed to by reg2
storex      reg1, reg2,   address     - store reg1 at memory address, indexed by value in reg2 (0-255, a byte)
storezm                   address     - store zero at memory address
storezi     reg1                      - store zero at memory pointed to by reg1
storezx     reg1,         address     - store zero at memory address, indexed by value in reg1 (0-255, a byte)
storefield  reg1, reg2,   value       - store reg1 in memory indirect, pointed to by reg2 + value 0-65535 (set a field from a pointer to a struct, like STOREI with additional field offset 0-65535)
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
calli       reg1                      - calls a subroutine (without arguments and without return values) at memory address in reg1 (indirect jsr) (possible uword return value in cpu regs AY)
call   label(argument register list) [: resultreg.type]
                                      - calls a subroutine with the given arguments and return value (optional).
                                        save current instruction location+1, continue execution at instruction nr of the label.
                                        the argument register list is positional and includes the datatype, ex.: r4.b,r5.w,fp1.f
                                        If the call is to a rom-routine, 'label' will be a hexadecimal address instead such as $ffd2
                                        If the arguments should be passed in CPU registers, they'll have a @REGISTER postfix.
                                        For example: call $ffd2(r5.b@A)
                                        Always preceded by parameter setup
callfar             bank,  address      Call a subroutine at the given memory address, in the given RAM/ROM bank (switches both banks at the same time)
callfarvb   reg1           address      Call a subroutine at the given memory address, in the RAM/ROM bank in reg1.b  (switches both banks at the same time)
syscall   number (argument register list) [: resultreg.type]
                                      - do a systemcall identified by number, result value(s) are pushed on value stack by the syscall code so
                                        will be POPped off into the given resultregister if any.
                                        Always preceded by parameter setup
                                        All register types (arguments + result register) are ALWAYS WORDS.
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
'bltr'      reg1, reg2,       address   - jump to location in program given by location, if reg1 < reg2 (unsigned) ==> this opcode doesn't exist: use bgtr with swapped operands
bge         reg1, value,      address   - jump to location in program given by location, if reg1 >= immediate value (unsigned)
ble         reg1, value,      address   - jump to location in program given by location, if reg1 <= immediate value (unsigned)
bger        reg1, reg2,       address   - jump to location in program given by location, if reg1 >= reg2 (unsigned)
'bler'      reg1, reg2,       address   - jump to location in program given by location, if reg1 <= reg2 (unsigned) ==> this opcode doesn't exist: use bger with swapped operands

(signed comparison branches:)
bgts        reg1, value,      address   - jump to location in program given by location, if reg1 > immediate value (signed)
blts        reg1, value,      address   - jump to location in program given by location, if reg1 < immediate value (signed)
bgtsr       reg1, reg2,       address   - jump to location in program given by location, if reg1 > reg2 (signed)
'bltsr'     reg1, reg2,       address   - jump to location in program given by location, if reg1 < reg2 (signed) ==> this opcode doesn't exist: use bgtsr with swapped operands
bges        reg1, value,      address   - jump to location in program given by location, if reg1 >= immediate value (signed)
bles        reg1, value,      address   - jump to location in program given by location, if reg1 <= immediate value (signed)
bgesr       reg1, reg2,       address   - jump to location in program given by location, if reg1 >= reg2 (signed)
'blesr'     reg1, reg2,       address   - jump to location in program given by location, if reg1 <= reg2 (signed) ==> this opcode doesn't exist: use bgesr with swapped operands


ARITHMETIC
----------
All have type b or w or f. Note: result types are the same as operand types! E.g. byte*byte->byte.

exts        reg1, reg2                      - reg1 = signed extension of reg2 (byte to word, or word to long)  (note: unlike M68k, exts.b -> word and exts.w -> long.)
ext         reg1, reg2                      - reg1 = unsigned extension of reg2 (which in practice just means clearing the MSB / MSW) (note: unlike M68k, ext.b -> word and ext.w -> long. )
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
mulsr       reg1, reg2                      - signed multiply reg1 *= reg2  note: byte*byte->byte, no type extension to word!
mul         reg1,              value        - unsigned multiply reg1 *= value  note: byte*byte->byte, no type extension to word!
muls        reg1,              value        - signed multiply reg1 *= value  note: byte*byte->byte, no type extension to word!
mulm        reg1,              address      - unsigned memory at address  *= reg2  note: byte*byte->byte, no type extension to word!
mulsm       reg1,              address      - signed memory at address  *= reg2  note: byte*byte->byte, no type extension to word!
divr        reg1, reg2                      - unsigned division reg1 /= reg2  note: division by zero yields max int $ff/$ffff
divsr       reg1, reg2                      - signed division reg1 /= reg2  note: division by zero yields max signed int 127 / 32767
div         reg1,              value        - unsigned division reg1 /= value  note: division by zero yields max int $ff/$ffff
divs        reg1,              value        - signed division reg1 /= value  note: division by zero yields max signed int 127 / 32767
divm        reg1,              address      - memory at address /= reg2  note: division by zero yields max int $ff/$ffff
divsm       reg1,              address      - signed memory at address /= reg2  note: division by zero yields max signed int 127 / 32767
modr        reg1, reg2                      - remainder (modulo) of unsigned division reg1 %= reg2  note: division by zero yields max signed int $ff/$ffff
mod         reg1,              value        - remainder (modulo) of unsigned division reg1 %= value  note: division by zero yields max signed int $ff/$ffff
divmodr     reg1, reg2                      - unsigned division reg1/reg2, storing division and remainder on value stack (so need to be POPped off)
divmod      reg1,              value        - unsigned division reg1/value, storing division and remainder on value stack (so need to be POPped off)
sqrt        reg1, reg2                      - reg1 is the square root of reg2 (reg2 can be l.1, .w or .b, result type in reg1 is .w or .b)  you can also use it with floating point types, fpreg1 and fpreg2 (result is also .f)
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
asrn        reg1, reg2                       - reg1 = multi-shift reg1 right by reg2 bits (signed)  + Carry is undefined
lsrn        reg1, reg2                       - reg1 = multi-shift reg1 right by reg2 bits + Carry is undefined
lsln        reg1, reg2                       - reg1 = multi-shift reg1 left by reg2 bits + Carry is undefined
asrnm       reg1,        address             - multi-shift memory right by reg1 bits (signed) + Carry is undefined
lsrnm       reg1,        address             - multi-shift memory right by reg1 bits + Carry is undefined
lslnm       reg1,        address             - multi-shift memory left by reg1 bits + Carry is undefined
asr         reg1                             - shift reg1 right by 1 bits (signed) + set Carry to shifted bit
lsr         reg1                             - shift reg1 right by 1 bits + set Carry to shifted bit
lsl         reg1                             - shift reg1 left by 1 bits + set Carry to shifted bit
lsrm                     address             - shift memory right by 1 bits + set Carry to shifted bit
asrm                     address             - shift memory right by 1 bits (signed) + set Carry to shifted bit
lslm                     address             - shift memory left by 1 bits + set Carry to shifted bit
ror         reg1                             - rotate reg1 right by 1 bits, not using carry  + set Carry to shifted bit
roxr        reg1                             - rotate reg1 right by 1 bits, using carry  + set Carry to shifted bit  (maps to 6502 CPU instruction ror)
rol         reg1                             - rotate reg1 left by 1 bits, not using carry  + set Carry to shifted bit
roxl        reg1                             - rotate reg1 left by 1 bits, using carry,  + set Carry to shifted bit  (maps to 6502 CPU instruction rol)
rorm                     address             - rotate memory right by 1 bits, not using carry  + set Carry to shifted bit
roxrm                    address             - rotate memory right by 1 bits, using carry  + set Carry to shifted bit    (maps to 6502 CPU instruction ror)
rolm                     address             - rotate memory left by 1 bits, not using carry  + set Carry to shifted bit
roxlm                    address             - rotate memory left by 1 bits, using carry,  + set Carry to shifted bit    (maps to 6502 CPU instruction rol)
bit                      address             - test bits in byte value at address, this is a special instruction available on other systems to optimize testing and branching on bits 7 and 6


FLOATING POINT CONVERSIONS AND FUNCTIONS
----------------------------------------
ffromub      fpreg1, reg1               - fpreg1 = reg1 from usigned byte
ffromsb      fpreg1, reg1               - fpreg1 = reg1 from signed byte
ffromuw      fpreg1, reg1               - fpreg1 = reg1 from unsigned word
ffromsw      fpreg1, reg1               - fpreg1 = reg1 from signed word
ffromsl      fpreg1, reg1               - fpreg1 = reg1 from signed long
ftoub        reg1, fpreg1               - reg1 = fpreg1 as unsigned byte
ftosb        reg1, fpreg1               - reg1 = fpreg1 as signed byte
ftouw        reg1, fpreg1               - reg1 = fpreg1 as unsigned word
ftosw        reg1, fpreg1               - reg1 = fpreg1 as signed word
ftosl        reg1, fpreg1               - reg1 = fpreg1 as signed long
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
lsigb [w, l]  reg1, reg2                  - reg1 becomes the least significant byte of the word (or long) in reg2
lsigw [l]     reg1, reg2                  - reg1 becomes the least significant word of the long in reg2
msigb [w, l]  reg1, reg2                  - reg1 becomes the most significant byte of the word (or long) in reg2
msigw [l]     reg1, reg2                  - reg1 becomes the most significant word of the long in reg2
bsigb [l]     reg1, reg2                  - reg1 becomes the bank byte of the long in reg2 (bits 16-23)
concat [b, w] reg1, reg2, reg3            - reg1.w/l = 'concatenate' two registers: lsb/lsw of reg2 (as msb) and lsb/lsw of reg3 (as lsb) into word or int)
push [b, w, f]   reg1                     - push value in reg1 on the stack
pop [b, w, f]    reg1                     - pop value from stack into reg1
pushst                                    - push status register bits to stack
popst                                     - pop status register bits from stack
 */

enum class Opcode {
    NOP,
    LOAD,       // note: LOAD <symbol>  gets you the address of the symbol, whereas LOADM <symbol> would get you the value stored at that location
    LOADM,
    LOADI,      // the only opcode that allows r1 and r2 to be the same; because this saves a lot of intermediary registers and loads to dereference a pointer chain
    LOADX,
    LOADR,
    LOADHA,
    LOADHX,
    LOADHY,
    LOADHAX,
    LOADHAY,
    LOADHXY,
    LOADFIELD,
    LOADHFACZERO,
    LOADHFACONE,
    STOREM,
    STOREI,
    STOREX,
    STOREZM,
    STOREZI,
    STOREZX,
    STOREHA,
    STOREHX,
    STOREHY,
    STOREHAX,
    STOREHAY,
    STOREHXY,
    STOREFIELD,
    STOREHFACZERO,
    STOREHFACONE,

    JUMP,
    JUMPI,
    CALLI,
    CALL,
    CALLFAR,
    CALLFARVB,
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
    MULSR,
    MULS,
    MULSM,
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
    FFROMSL,
    FTOUB,
    FTOSB,
    FTOUW,
    FTOSW,
    FTOSL,
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
    LSIGB,
    LSIGW,
    MSIGB,
    MSIGW,
    BSIGB,
    CONCAT,
    BREAKPOINT,
    ALIGN
}

val OpcodesThatBranchUnconditionally = setOf(
    Opcode.JUMP,
    Opcode.JUMPI,
    Opcode.RETURN,
    Opcode.RETURNR,
    Opcode.RETURNI
)

val OpcodesThatBranch = OpcodesThatBranchUnconditionally + setOf(
    Opcode.CALLI,
    Opcode.CALL,
    Opcode.CALLFAR,
    Opcode.CALLFARVB,
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

val OpcodesThatEndSSAblock = OpcodesThatBranchUnconditionally + setOf(
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
    Opcode.CMPI,
    Opcode.SGN
)
val OpcodesThatSetStatusbitsButNotCarry = arrayOf(
    Opcode.LOAD,
    Opcode.LOADM,
    Opcode.LOADI,
    Opcode.LOADX,
    Opcode.LOADR,
    Opcode.LOADHA,
    Opcode.LOADHX,
    Opcode.LOADHY,
    Opcode.LOADHAX,
    Opcode.LOADHAY,
    Opcode.LOADHXY,
    Opcode.LOADFIELD,
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
    Opcode.LSIGB,
    Opcode.LSIGW,
    Opcode.MSIGB,
    Opcode.MSIGW,
    Opcode.BSIGB,
    Opcode.CONCAT
)

val OpcodesThatDependOnCarry = arrayOf(
    Opcode.BSTCC,
    Opcode.BSTCS,
    Opcode.BSTPOS,
    Opcode.BSTNEG,
    Opcode.ROXL,
    Opcode.ROXLM,
    Opcode.ROXR,
    Opcode.ROXRM
)

val OpcodesThatLoad = arrayOf(
    Opcode.LOAD,
    Opcode.LOADM,
    Opcode.LOADI,
    Opcode.LOADX,
    Opcode.LOADR,
    Opcode.LOADHA,
    Opcode.LOADHX,
    Opcode.LOADHY,
    Opcode.LOADHAX,
    Opcode.LOADHAY,
    Opcode.LOADHXY,
    Opcode.LOADFIELD,
    Opcode.LOADHFACZERO,
    Opcode.LOADHFACONE
)
val OpcodesThatSetStatusbits = OpcodesThatSetStatusbitsButNotCarry + OpcodesThatSetStatusbitsIncludingCarry


enum class IRDataType {
    BYTE,
    WORD,
    FLOAT,
    LONG        // 32 bits integer
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
                        ">r2" -> reg2 = OperandDirection.WRITE
                        "<>r2" -> reg2 = OperandDirection.READWRITE
                        "<r3" -> reg3 = OperandDirection.READ
                        "<fr1" -> fpreg1 = OperandDirection.READ
                        ">fr1" -> fpreg1 = OperandDirection.WRITE
                        "<>fr1" -> fpreg1 = OperandDirection.READWRITE
                        "<fr2" -> fpreg2 = OperandDirection.READ
                        ">fr2" -> fpreg2 = OperandDirection.WRITE
                        "<>fr2" -> fpreg2 = OperandDirection.READWRITE
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
                if('L' in typespec)
                    result[IRDataType.LONG] = InstructionFormat(IRDataType.LONG, reg1, reg2, reg3, fpreg1, fpreg2, address, immediate, funcCall, sysCall)
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
    Opcode.LOAD       to InstructionFormat.from("BWL,>r1,<i     | F,>fr1,<i"),
    Opcode.LOADM      to InstructionFormat.from("BWL,>r1,<a     | F,>fr1,<a"),
    Opcode.LOADI      to InstructionFormat.from("BWL,>r1,<r2    | F,>fr1,<r1"),
    Opcode.LOADX      to InstructionFormat.from("BWL,>r1,<r2,<a | F,>fr1,<r1,<a"),
    Opcode.LOADR      to InstructionFormat.from("BWL,>r1,<r2    | F,>fr1,<fr2"),
    Opcode.LOADHA     to InstructionFormat.from("B,>r1"),
    Opcode.LOADHA     to InstructionFormat.from("B,>r1"),
    Opcode.LOADHX     to InstructionFormat.from("B,>r1"),
    Opcode.LOADHY     to InstructionFormat.from("B,>r1"),
    Opcode.LOADHAX    to InstructionFormat.from("W,>r1"),
    Opcode.LOADHAY    to InstructionFormat.from("W,>r1"),
    Opcode.LOADHXY    to InstructionFormat.from("W,>r1"),
    Opcode.LOADFIELD  to InstructionFormat.from("BWL,>r1,<r2,<i | F,>fr1,<r1,<i"),
    Opcode.LOADHFACZERO to InstructionFormat.from("F,>fr1"),
    Opcode.LOADHFACONE  to InstructionFormat.from("F,>fr1"),
    Opcode.STOREM     to InstructionFormat.from("BWL,<r1,>a     | F,<fr1,>a"),
    Opcode.STOREI     to InstructionFormat.from("BWL,<r1,<r2    | F,<fr1,<r1"),
    Opcode.STOREX     to InstructionFormat.from("BWL,<r1,<r2,>a | F,<fr1,<r1,>a"),
    Opcode.STOREZM    to InstructionFormat.from("BWL,>a         | F,>a"),
    Opcode.STOREZI    to InstructionFormat.from("BWL,<r1        | F,<r1"),
    Opcode.STOREZX    to InstructionFormat.from("BWL,<r1,>a     | F,<r1,>a"),
    Opcode.STOREHA    to InstructionFormat.from("B,<r1"),
    Opcode.STOREHA    to InstructionFormat.from("B,<r1"),
    Opcode.STOREHX    to InstructionFormat.from("B,<r1"),
    Opcode.STOREHY    to InstructionFormat.from("B,<r1"),
    Opcode.STOREHAX   to InstructionFormat.from("W,<r1"),
    Opcode.STOREHAY   to InstructionFormat.from("W,<r1"),
    Opcode.STOREHXY   to InstructionFormat.from("W,<r1"),
    Opcode.STOREFIELD to InstructionFormat.from("BWL,<r1,<r2,<i | F,<fr1,<r1,<i"),
    Opcode.STOREHFACZERO  to InstructionFormat.from("F,<fr1"),
    Opcode.STOREHFACONE  to InstructionFormat.from("F,<fr1"),
    Opcode.JUMP       to InstructionFormat.from("N,<a"),
    Opcode.JUMPI      to InstructionFormat.from("N,<r1"),
    Opcode.CALLI      to InstructionFormat.from("N,<r1"),
    Opcode.CALL       to InstructionFormat.from("N,call"),
    Opcode.CALLFAR    to InstructionFormat.from("N,<i,<a"),
    Opcode.CALLFARVB  to InstructionFormat.from("N,<r1,<a"),
    Opcode.SYSCALL    to InstructionFormat.from("N,syscall"),
    Opcode.RETURN     to InstructionFormat.from("N"),
    Opcode.RETURNR    to InstructionFormat.from("BWL,<r1        | F,<fr1"),
    Opcode.RETURNI    to InstructionFormat.from("BWL,<i         | F,<i"),
    Opcode.BSTCC      to InstructionFormat.from("N,<a"),
    Opcode.BSTCS      to InstructionFormat.from("N,<a"),
    Opcode.BSTEQ      to InstructionFormat.from("N,<a"),
    Opcode.BSTNE      to InstructionFormat.from("N,<a"),
    Opcode.BSTNEG     to InstructionFormat.from("N,<a"),
    Opcode.BSTPOS     to InstructionFormat.from("N,<a"),
    Opcode.BSTVC      to InstructionFormat.from("N,<a"),
    Opcode.BSTVS      to InstructionFormat.from("N,<a"),
    Opcode.BGTR       to InstructionFormat.from("BWL,<r1,<r2,<a"),
    Opcode.BGT        to InstructionFormat.from("BWL,<r1,<i,<a"),
    Opcode.BLT        to InstructionFormat.from("BWL,<r1,<i,<a"),
    Opcode.BGTSR      to InstructionFormat.from("BWL,<r1,<r2,<a"),
    Opcode.BGTS       to InstructionFormat.from("BWL,<r1,<i,<a"),
    Opcode.BLTS       to InstructionFormat.from("BWL,<r1,<i,<a"),
    Opcode.BGER       to InstructionFormat.from("BWL,<r1,<r2,<a"),
    Opcode.BGE        to InstructionFormat.from("BWL,<r1,<i,<a"),
    Opcode.BLE        to InstructionFormat.from("BWL,<r1,<i,<a"),
    Opcode.BGESR      to InstructionFormat.from("BWL,<r1,<r2,<a"),
    Opcode.BGES       to InstructionFormat.from("BWL,<r1,<i,<a"),
    Opcode.BLES       to InstructionFormat.from("BWL,<r1,<i,<a"),
    Opcode.INC        to InstructionFormat.from("BWL,<>r1      | F,<>fr1"),
    Opcode.INCM       to InstructionFormat.from("BWL,<>a       | F,<>a"),
    Opcode.DEC        to InstructionFormat.from("BWL,<>r1      | F,<>fr1"),
    Opcode.DECM       to InstructionFormat.from("BWL,<>a       | F,<>a"),
    Opcode.NEG        to InstructionFormat.from("BWL,<>r1      | F,<>fr1"),
    Opcode.NEGM       to InstructionFormat.from("BWL,<>a       | F,<>a"),
    Opcode.ADDR       to InstructionFormat.from("BWL,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.ADD        to InstructionFormat.from("BWL,<>r1,<i   | F,<>fr1,<i"),
    Opcode.ADDM       to InstructionFormat.from("BWL,<r1,<>a   | F,<fr1,<>a"),
    Opcode.SUBR       to InstructionFormat.from("BWL,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.SUB        to InstructionFormat.from("BWL,<>r1,<i   | F,<>fr1,<i"),
    Opcode.SUBM       to InstructionFormat.from("BWL,<r1,<>a   | F,<fr1,<>a"),
    Opcode.MULR       to InstructionFormat.from("BW,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.MUL        to InstructionFormat.from("BW,<>r1,<i   | F,<>fr1,<i"),
    Opcode.MULM       to InstructionFormat.from("BW,<r1,<>a   | F,<fr1,<>a"),
    Opcode.MULSR      to InstructionFormat.from("BWL,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.MULS       to InstructionFormat.from("BWL,<>r1,<i   | F,<>fr1,<i"),
    Opcode.MULSM      to InstructionFormat.from("BWL,<r1,<>a   | F,<fr1,<>a"),
    Opcode.DIVR       to InstructionFormat.from("BW,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.DIV        to InstructionFormat.from("BW,<>r1,<i   | F,<>fr1,<i"),
    Opcode.DIVM       to InstructionFormat.from("BW,<r1,<>a   | F,<fr1,<>a"),
    Opcode.DIVSR      to InstructionFormat.from("BWL,<>r1,<r2  | F,<>fr1,<fr2"),
    Opcode.DIVS       to InstructionFormat.from("BWL,<>r1,<i   | F,<>fr1,<i"),
    Opcode.DIVSM      to InstructionFormat.from("BWL,<r1,<>a   | F,<fr1,<>a"),
    Opcode.SQRT       to InstructionFormat.from("BWL,>r1,<r2   | F,>fr1,<fr2"),
    Opcode.SQUARE     to InstructionFormat.from("BWL,>r1,<r2   | F,>fr1,<fr2"),
    Opcode.SGN        to InstructionFormat.from("BWL,>r1,<r2   | F,>r1,<fr1"),
    Opcode.MODR       to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.MOD        to InstructionFormat.from("BW,<>r1,<i"),
    Opcode.DIVMODR    to InstructionFormat.from("BW,<>r1,<r2"),
    Opcode.DIVMOD     to InstructionFormat.from("BW,<>r1,<i"),
    Opcode.CMP        to InstructionFormat.from("BWL,<r1,<r2"),
    Opcode.CMPI       to InstructionFormat.from("BWL,<r1,<i"),
    Opcode.EXT        to InstructionFormat.from("BWL,>r1,<r2"),
    Opcode.EXTS       to InstructionFormat.from("BWL,>r1,<r2"),
    Opcode.ANDR       to InstructionFormat.from("BWL,<>r1,<r2"),
    Opcode.AND        to InstructionFormat.from("BWL,<>r1,<i"),
    Opcode.ANDM       to InstructionFormat.from("BWL,<r1,<>a"),
    Opcode.ORR        to InstructionFormat.from("BWL,<>r1,<r2"),
    Opcode.OR         to InstructionFormat.from("BWL,<>r1,<i"),
    Opcode.ORM        to InstructionFormat.from("BWL,<r1,<>a"),
    Opcode.XORR       to InstructionFormat.from("BWL,<>r1,<r2"),
    Opcode.XOR        to InstructionFormat.from("BWL,<>r1,<i"),
    Opcode.XORM       to InstructionFormat.from("BWL,<r1,<>a"),
    Opcode.INV        to InstructionFormat.from("BWL,<>r1"),
    Opcode.INVM       to InstructionFormat.from("BWL,<>a"),
    Opcode.ASRN       to InstructionFormat.from("BWL,<>r1,<r2"),
    Opcode.ASRNM      to InstructionFormat.from("BWL,<r1,<>a"),
    Opcode.LSRN       to InstructionFormat.from("BWL,<>r1,<r2"),
    Opcode.LSRNM      to InstructionFormat.from("BWL,<r1,<>a"),
    Opcode.LSLN       to InstructionFormat.from("BWL,<>r1,<r2"),
    Opcode.LSLNM      to InstructionFormat.from("BWL,<r1,<>a"),
    Opcode.ASR        to InstructionFormat.from("BWL,<>r1"),
    Opcode.ASRM       to InstructionFormat.from("BWL,<>a"),
    Opcode.LSR        to InstructionFormat.from("BWL,<>r1"),
    Opcode.LSRM       to InstructionFormat.from("BWL,<>a"),
    Opcode.LSL        to InstructionFormat.from("BWL,<>r1"),
    Opcode.LSLM       to InstructionFormat.from("BWL,<>a"),
    Opcode.ROR        to InstructionFormat.from("BWL,<>r1"),
    Opcode.RORM       to InstructionFormat.from("BWL,<>a"),
    Opcode.ROXR       to InstructionFormat.from("BWL,<>r1"),
    Opcode.ROXRM      to InstructionFormat.from("BWL,<>a"),
    Opcode.ROL        to InstructionFormat.from("BWL,<>r1"),
    Opcode.ROLM       to InstructionFormat.from("BWL,<>a"),
    Opcode.ROXL       to InstructionFormat.from("BWL,<>r1"),
    Opcode.ROXLM      to InstructionFormat.from("BWL,<>a"),
    Opcode.BIT        to InstructionFormat.from("B,<a"),

    Opcode.FFROMUB    to InstructionFormat.from("F,>fr1,<r1"),
    Opcode.FFROMSB    to InstructionFormat.from("F,>fr1,<r1"),
    Opcode.FFROMUW    to InstructionFormat.from("F,>fr1,<r1"),
    Opcode.FFROMSW    to InstructionFormat.from("F,>fr1,<r1"),
    Opcode.FFROMSL    to InstructionFormat.from("F,>fr1,<r1"),
    Opcode.FTOUB      to InstructionFormat.from("F,>r1,<fr1"),
    Opcode.FTOSB      to InstructionFormat.from("F,>r1,<fr1"),
    Opcode.FTOUW      to InstructionFormat.from("F,>r1,<fr1"),
    Opcode.FTOSW      to InstructionFormat.from("F,>r1,<fr1"),
    Opcode.FTOSL      to InstructionFormat.from("F,>r1,<fr1"),
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

    Opcode.LSIGB      to InstructionFormat.from("WL,>r1,<r2"),
    Opcode.LSIGW      to InstructionFormat.from("L,>r1,<r2"),
    Opcode.MSIGB      to InstructionFormat.from("WL,>r1,<r2"),
    Opcode.MSIGW      to InstructionFormat.from("L,>r1,<r2"),
    Opcode.BSIGB      to InstructionFormat.from("L,>r1,<r2"),
    Opcode.PUSH       to InstructionFormat.from("BWL,<r1       | F,<fr1"),
    Opcode.POP        to InstructionFormat.from("BWL,>r1       | F,>fr1"),
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
        require(reg1==null || reg1 in 0..99999) {"reg1 out of bounds"}
        require(reg2==null || reg2 in 0..99999) {"reg2 out of bounds"}
        require(reg3==null || reg3 in 0..99999) {"reg3 out of bounds"}
        require(fpReg1==null || fpReg1 in 0..99999) {"fpReg1 out of bounds"}
        require(fpReg2==null || fpReg2 in 0..99999) {"fpReg2 out of bounds"}
        if(reg1!=null && reg2!=null) require(reg1!=reg2 || opcode==Opcode.LOADI) {"reg1 must not be same as reg2"}  // note: this is ok for fpRegs as these are always the same type.  LOADI is also an exception, hopefully this can work, because it saves a lot of intermediary registers when dereferencing a pointer chain
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
            if(type==IRDataType.FLOAT) {
                if(opcode!=Opcode.LOADFIELD && opcode!=Opcode.STOREFIELD)
                    requireNotNull(immediateFp) { "missing immediate fp value" }
            }
            else
                require(immediate!=null || labelSymbol!=null) {"missing immediate value or labelsymbol"}
        }
        if(opcode==Opcode.LOADFIELD || opcode==Opcode.STOREFIELD) {
            require(immediate != null) {
                "missing immediate value for $opcode" }
        }
        if(type!=IRDataType.FLOAT)
            require(fpReg1==null && fpReg2==null) {"int instruction can't use fp reg"}
        if(format.address!=OperandDirection.UNUSED)
            require(address!=null || labelSymbol!=null) {
                "missing an address or labelsymbol"}
        if(format.immediate && (immediate!=null || immediateFp!=null)) {
            if(opcode==Opcode.LOADFIELD || opcode==Opcode.STOREFIELD) {
                require(immediate in 0..65535) { "immediate value out of range for loadfield/storefield: $immediate" }
            } else if(opcode!=Opcode.SYSCALL) {
                when (type) {
                    IRDataType.BYTE -> require(immediate in -128..255) { "immediate value out of range for byte: $immediate" }
                    IRDataType.WORD -> require(immediate in -32768..65535) { "immediate value out of range for word: $immediate" }
                    IRDataType.LONG -> require(immediate in -2147483648..2147483647) { "immediate value out of range for long: $immediate" }
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
            val callRegisters = fcallArgs?.arguments?.map { it.reg.registerNum } ?: emptyList()
            val returnRegisters = fcallArgs?.returns?.map { it.registerNum } ?: emptyList()

            val reused = callRegisters.intersect(returnRegisters)
            if(reused.isNotEmpty()) {
                for(r in reused) {
                    val argType = fcallArgs!!.arguments.single { it.reg.registerNum==r }.reg.dt
                    val returnType = fcallArgs.returns.single { it.registerNum==r }.dt
                    if (argType!=IRDataType.FLOAT && returnType!=IRDataType.FLOAT) {
                        if(argType!=returnType)
                            throw AssemblyError("syscall cannot reuse argument register as return register with different type $this")
                    }
                }
            }
        }
    }

    fun addUsedRegistersCounts(
        readRegsCounts: MutableMap<Int, Int>,
        writeRegsCounts: MutableMap<Int, Int>,
        readFpRegsCounts: MutableMap<Int, Int>,
        writeFpRegsCounts: MutableMap<Int, Int>,
        regsTypes: MutableMap<Int, IRDataType>,
        chunk: IRCodeChunk?
    ) {
        when (this.reg1direction) {
            OperandDirection.UNUSED -> {}
            OperandDirection.READ -> {
                readRegsCounts[this.reg1!!] = readRegsCounts.getValue(this.reg1)+1
                val actualtype = determineReg1Type()
                if(actualtype!=null) {
                    val existingType = regsTypes[reg1]
                    if (existingType!=null) {
                        if (existingType != actualtype)
                            throw IllegalArgumentException("register $reg1 given multiple types! $existingType and $actualtype  in $chunk")
                    } else
                        regsTypes[reg1] = actualtype
                }
            }
            OperandDirection.WRITE -> {
                writeRegsCounts[this.reg1!!] = writeRegsCounts.getValue(this.reg1)+1
                val actualtype = determineReg1Type()
                if(actualtype!=null) {
                    val existingType = regsTypes[reg1]
                    if (existingType!=null) {
                        if (existingType != actualtype)
                            throw IllegalArgumentException("register $reg1 given multiple types! $existingType and $actualtype  in chunk $chunk")
                    } else
                        regsTypes[reg1] = actualtype

                }
            }
            OperandDirection.READWRITE -> {
                readRegsCounts[this.reg1!!] = readRegsCounts.getValue(this.reg1)+1
                writeRegsCounts[this.reg1] = writeRegsCounts.getValue(this.reg1)+1
                val actualtype = determineReg1Type()
                if(actualtype!=null) {
                    val existingType = regsTypes[reg1]
                    if (existingType!=null) {
                        if (existingType != actualtype)
                            throw IllegalArgumentException("register $reg1 given multiple types! $existingType and $actualtype  in $chunk")
                    } else
                        regsTypes[reg1] = actualtype

                }
            }
        }
        when (this.reg2direction) {
            OperandDirection.UNUSED -> {}
            OperandDirection.READ -> {
                readRegsCounts[this.reg2!!] = readRegsCounts.getValue(this.reg2)+1
                val actualtype = determineReg2Type()
                if(actualtype!=null) {
                    val existingType = regsTypes[reg2]
                    if (existingType!=null) {
                        if (existingType != actualtype)
                            throw IllegalArgumentException("register $reg2 given multiple types! $existingType and $actualtype  in $chunk")
                    } else
                        regsTypes[reg2] = actualtype
                }
            }
            OperandDirection.READWRITE -> {
                readRegsCounts[this.reg2!!] = readRegsCounts.getValue(this.reg2)+1
                writeRegsCounts[this.reg2] = writeRegsCounts.getValue(this.reg2)+1
                val actualtype = determineReg2Type()
                if(actualtype!=null) {
                    val existingType = regsTypes[reg2]
                    if (existingType!=null) {
                        if (existingType != actualtype)
                            throw IllegalArgumentException("register $reg2 given multiple types! $existingType and $actualtype  in $chunk")
                    } else
                        regsTypes[reg2] = actualtype
                }
            }
            else -> throw IllegalArgumentException("reg2 can only be read or readwrite")
        }
        when (this.reg3direction) {
            OperandDirection.UNUSED -> {}
            OperandDirection.READ -> {
                readRegsCounts[this.reg3!!] = readRegsCounts.getValue(this.reg3)+1
                val actualtype = determineReg3Type()
                if(actualtype!=null) {
                    val existingType = regsTypes[reg3]
                    if (existingType!=null) {
                        if (existingType != actualtype)
                            throw IllegalArgumentException("register $reg3 given multiple types! $existingType and $actualtype  in $chunk")
                    } else
                        regsTypes[reg3] = actualtype
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
            OperandDirection.READWRITE -> {
                readFpRegsCounts[this.fpReg2!!] = readFpRegsCounts.getValue(this.fpReg2)+1
                writeFpRegsCounts[this.fpReg2] = writeFpRegsCounts.getValue(this.fpReg2)+1
            }
            else -> throw IllegalArgumentException("fpReg2 can only be read or readwrite")
        }

        if(fcallArgs!=null) {
            fcallArgs.returns.forEach {
                if (it.dt == IRDataType.FLOAT)
                    writeFpRegsCounts[it.registerNum] = writeFpRegsCounts.getValue(it.registerNum) + 1
                else {
                    writeRegsCounts[it.registerNum] = writeRegsCounts.getValue(it.registerNum) + 1
                    val existingType = regsTypes[it.registerNum]
                    if (existingType!=null) {
                        if (existingType != it.dt)
                            throw IllegalArgumentException("register ${it.registerNum} given multiple types! $existingType and ${it.dt}  in $chunk")
                    } else
                        regsTypes[it.registerNum] = it.dt
                }
            }
            fcallArgs.arguments.forEach {
                if(it.reg.dt==IRDataType.FLOAT)
                    readFpRegsCounts[it.reg.registerNum] = readFpRegsCounts.getValue(it.reg.registerNum)+1
                else {
                    readRegsCounts[it.reg.registerNum] = readRegsCounts.getValue(it.reg.registerNum) + 1
                    val existingType = regsTypes[it.reg.registerNum]
                    if (existingType!=null) {
                        if (existingType != it.reg.dt)
                            throw IllegalArgumentException("register ${it.reg.registerNum} given multiple types! $existingType and ${it.reg.dt}  in $chunk")
                    } else
                        regsTypes[it.reg.registerNum] = it.reg.dt
                }
            }
        }
    }

    private fun determineReg1Type(): IRDataType? {
        if(type==IRDataType.FLOAT) {
            // some float instructions have an integer (byte or word) register as well in reg1
            return when (opcode) {
                Opcode.FFROMUB,
                Opcode.FFROMSB,
                Opcode.FTOUB,
                Opcode.FTOSB,
                Opcode.FCOMP,
                Opcode.LOADX,
                Opcode.STOREX,
                Opcode.STOREZX,
                Opcode.SGN -> IRDataType.BYTE
                Opcode.FFROMSL, Opcode.FTOSL -> IRDataType.LONG
                else -> IRDataType.WORD
            }
        }
        if(type==IRDataType.WORD) {
            // some word instructions have byte reg1
            when (opcode) {
                Opcode.SGN, Opcode.STOREZX, Opcode.SQRT -> return IRDataType.BYTE
                Opcode.EXT, Opcode.EXTS, Opcode.CONCAT -> return IRDataType.LONG
                else -> {}
            }
        }
        if(type==IRDataType.LONG) {
            if(opcode==Opcode.SGN)
                return IRDataType.BYTE
            if(opcode==Opcode.SQRT)
                return IRDataType.WORD
        }
        if(opcode in setOf(Opcode.JUMPI, Opcode.CALLI, Opcode.STOREZI, Opcode.LSIGW, Opcode.MSIGW))
            return IRDataType.WORD
        if(opcode==Opcode.EXT || opcode==Opcode.EXTS)
            return if (type == IRDataType.BYTE) IRDataType.WORD else null
        if(opcode==Opcode.CONCAT)
            return if (type == IRDataType.BYTE) IRDataType.WORD else null
        if(opcode in setOf(Opcode.ASRNM, Opcode.LSRNM, Opcode.LSLNM, Opcode.SQRT, Opcode.LSIGB, Opcode.MSIGB, Opcode.BSIGB))
            return IRDataType.BYTE
        return this.type
    }

    private fun determineReg2Type(): IRDataType? {
        if(opcode==Opcode.LOADX || opcode==Opcode.STOREX)
            return IRDataType.BYTE
        if(opcode==Opcode.LOADI || opcode==Opcode.STOREI || opcode==Opcode.LOADFIELD || opcode==Opcode.STOREFIELD)
            return IRDataType.WORD
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
            IRDataType.LONG -> result.add(".l ")
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
                    IRDataType.LONG -> result.add("${location}r${it.reg.registerNum}.l$cpuReg,")
                    IRDataType.FLOAT -> result.add("${location}fr${it.reg.registerNum}.f$cpuReg,")
                }
            }
            if(result.last().endsWith(',')) {
                result.add(result.removeLastOrNull()!!.trimEnd(','))
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
                            IRDataType.LONG -> "r${returnspec.registerNum}.l"
                            IRDataType.FLOAT -> "fr${returnspec.registerNum}.f"
                        }
                    } else {
                        when (returnspec.dt) {
                            IRDataType.BYTE -> "r${returnspec.registerNum}.b@" + cpuReg
                            IRDataType.WORD -> "r${returnspec.registerNum}.w@" + cpuReg
                            IRDataType.LONG -> "r${returnspec.registerNum}.l@" + cpuReg
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
            result.removeLastOrNull()
        return result.joinToString("").trimEnd()
    }
}
