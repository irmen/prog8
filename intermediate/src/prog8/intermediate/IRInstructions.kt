package prog8.intermediate

import prog8.code.core.AssemblyError
import prog8.code.core.Statusflag
import prog8.code.core.toHex

/**
 * Inline value class representing a memory address (up to 32-bit).
 */
@JvmInline
value class MemoryAddress(val value: UInt) {
    init { require(value <= 0xffffffffu) { "address out of range: $value" } }
    override fun toString(): String = "$" + value.toString(16).padStart(2, '0')
    fun toHex(): String = "$" + value.toString(16)
    operator fun plus(other: UInt): MemoryAddress = MemoryAddress(value + other)
    operator fun plus(other: Int): MemoryAddress = MemoryAddress(value + other.toUInt())
    operator fun minus(other: UInt): MemoryAddress = MemoryAddress(value - other)
    operator fun minus(other: Int): MemoryAddress = MemoryAddress(value - other.toUInt())
}

/** Convert a UInt to a MemoryAddress. */
fun UInt.toAddress(): MemoryAddress = MemoryAddress(this)

/**
 * Inline value class representing a virtual register number (0-99999).
 * Supports comparison with Int and other RegisterNum values.
 */
@JvmInline
value class RegisterNum(val value: Int): Comparable<RegisterNum> {
    init { require(value in 0..99999) { "register number out of range: $value" } }
    override fun toString(): String = "r$value"
    override fun compareTo(other: RegisterNum): Int = value.compareTo(other.value)
    operator fun compareTo(other: Int): Int = value.compareTo(other)
}

/*
 
Intermediate Representation instructions for the IR Virtual machine.
--------------------------------------------------------------------

Specs of the virtual machine this will run on:
Program to execute is not stored in the system memory, it's just a separate list of instructions.
100K virtual registers, can be used as 8, 16 or 32 bits. r0-r99999
    reserved 99000 - 99099 : WORD registers for syscall arguments and response value(s)
    reserved 99100 - 99199 : BYTE registers for syscall arguments and response value(s)
    reseverd 99200 - 99299 : LONG registers for syscall arguments and response value(s)
100K virtual floating point registers (64 bits double precision)  fr0-fr99999
16 MB of memory (on the virtual target). On other targets, memory size and pointer width are target-specific.
Value stack, max 128 entries of 1 byte each.

Status flags: Carry, Zero, Negative, Overflow.

Status bit contract (see CpuType.statusBitsOnMultiByteOps for the rationale):
  The contract is enforced by the IR generator and the backends. It is NOT a property
  of individual instructions (other than the explicit ones listed below) but of the
  IR stream as a whole.

  - Only the following instructions are GUARANTEED to set status flags:
      CMP, CMPI        - set Z, N, C (and V via the comparison cascade)
      SEC, CLC         - set/clear C
      SGN              - sets Z, N, C based on sign of operand
      BITTST           - sets Z based on tested bit
      PUSHST, POPST    - read/restore the full status byte (not a normal consumer)

  - The shift/rotate operations (ASR, LSR, LSL, ROL, ROR) and their memory variants
    set the CARRY flag (the bit shifted out becomes the new carry) for use by
    ROXL/ROXR (rotate through carry). They do NOT set Z or N.
    
    Note for M68k backends: M68k has separate C (carry/CCR bit 0) and X (extend/CCR bit 4)
    bits. On M68k, the X bit serves as the "rotate carry" for ROXL/ROXR, while C is
    used for comparisons. ROL/ROR on M68k are implemented as "clear X + roxl/roxr"
    (logical rotate: inject 0 via X=0), while ROXL/ROXR use roxl/roxr directly
    (rotate through X). CLC/SEC manage both C and X to keep them in sync.

  - ALL other instructions (LOAD, LOADM, LOADX, LOADR, LOADI, INC, INCM, DEC, DECM,
    NEG, NEGM, ADD, SUB, MUL, DIV, AND, OR, XOR, INV, BITSET, BITCLR, BITTOG,
    LSIGB/W, MSIGB/W, BSIGB, MIDB, CONCAT, PUSH, POP, ...) DO NOT modify the
    status flags. Their effect on the carry, zero, negative, and overflow flags
    is UNDEFINED and must not be relied upon by any subsequent branch.

  - This means the IR generator MUST emit an explicit CMP/CMPI before any branch
    (BSTEQ, BSTNE, BSTCC, BSTCS, BSTPOS, BSTNEG, BGT, BGE, BLT, BLE, and their
    signed/register variants) that depends on the result of an operation.

  - For backends that target CPUs where multi-byte ops naturally set Z based on
    the full value (e.g. M68000's `move.w #imm, d0`), the CpuType contract
    (statusBitsOnMultiByteOps = true) can be honored to skip the explicit CMP/CMPI.
    For 8-bit targets (6502, 65C02), this contract is false, and explicit
    CMP/CMPI is required for every branch that depends on a multi-byte result.

See CpuType.statusBitsOnMultiByteOps for the per-CPU configuration.

Instruction set is mostly a load/store architecture, there are few instructions operating on memory directly.

Value types: integers (.b=byte=8 bits, .w=word=16 bits, .l=long=32 bits), float (.f=64 bits), and pointer (.p=target specific pointer width: 2 bytes on 6502, 4 bytes on m68k and virtual). Omitting it defaults to b if the instruction requires a type.
There is no distinction between signed and unsigned for many instructions. Instead, a different instruction is used if a distinction should be made (for example div and divs).
Floating point operations are just 'f' typed regular instructions, however there are a few unique fp conversion instructions.

Standalone label definitions start with an underscore; structured code references omit it.


CANONICAL TEXT FORMAT
---------------------
Instructions are written as:  opcode[.type] operand,operand,...
The operands are written in the canonical slot order of the instruction's OpcodeSchema
(destinations first, then sources, then immediate/memory/target). Operand syntax:

    r5.w            integer register 5, used as a word
    fr5.f           floating point register 5
    #$1234.w        immediate value (with its data type)
    #main.items     the address of a symbol (optionally with +offset)
    [main.items]    memory at a symbol (optionally [main.items+4])
    [$d020]         memory at a fixed address
    [r2.p+8]        memory pointed to by a register, with a displacement
    [main.items+4+r2.w*2]     memory at symbol + displacement + index register * scale
    s10.w           cpu hardware register, addressed by calling convention slot
    main.label      a code location (branch/jump/call target), optionally main.label+4
    (r2.p)          an indirect code location (address in a register)

Calls are written as:  call target(argument,...) : result,... !memory=effect,status=effect
where an argument is [name=]register[@slot|@statusflag] and a result is
register[@slot|@statusflag] or just @slot / @statusflag when the result isn't captured.
The effect suffix is omitted only when both effects are UNKNOWN.


LOAD/STORE
----------
The following descriptions use historical positional names solely to describe instruction
semantics. They are not accepted as IRFORMAT=2 text; use the canonical syntax above.

All have type b or w or l or f.

load        reg1,         value       - load immediate value into register. If you supply a symbol, loads the *address* of the symbol! (variable values are loaded from memory via the loadm instruction)
loadm       reg1,         address     - load reg1 with value at memory address
loadi       reg1, reg2,   value       - load reg1 with value in memory indirect, pointed to by reg2 + offsetvalue 0-65535 (often used to read a field from a pointer to a struct, or with offset=0 just straight from the pointer)
loadx       reg1, reg2,   address [,S=scale] - load reg1 with value at effective address (address + offset) + reg2*scale  (offset is symbolOffset, scale defaults to 1). Text form example: loadx.b r5,r2,arr,S=2  or  loadx.b r5,r3,arr+4,S=12 for struct-array field. Index is element index (not byte offset): byte 0-255 on 8-bit targets, word 0-32767 on 32-bit targets. Scale encodes element size / struct size (1..65535).
loadr       reg1, reg2                - load reg1 with value in register reg2,  "reg1 = reg2"
loadhr      reg1, slot                - load cpu hardware register from calling convention slot (s0=A, s1=X, s2=Y, s3=AX, s4=AY, s5=XY, s6=FAC1, s7=FAC2) into reg1
loadhfaczero       fpreg1             - load "cpu hardware register" fac0 into freg1.f
loadhfacone        fpreg1             - load "cpu hardware register" fac1 into freg1.f
loadp_inc   reg1,         address     - load reg1 with value in memory indirect via pointer variable at address, then post-increment the pointer variable by sizeof(type) (r1 = *a; a += sizeof(type), uses (a)+ on m68k)
storep_inc  reg1,         address     - store reg1 to memory indirect via pointer variable at address, then post-increment the pointer variable by sizeof(type) (*a = r1; a += sizeof(type), uses (a)+ on m68k)
storem      reg1,         address     - store reg1 at memory address
storei      reg1, reg2,   value       - store reg1 in memory indirect, pointed to by reg2 + offsetvalue 0-65535 (often used to write a field from a pointer to a struct, or with offset=0 just straight to the pointer)
storeim     value,        address     - store an immediate value (constant) at memory address (the constant goes in the value field, NOT via a register).
storezi     reg1,         value       - store zero at memory pointed to by reg1 + offsetvalue 0-65535  (just like storei, but a shorthand to store a constant 0)
storex      reg1, reg2,   address [,S=scale] - store reg1 at effective address (address + offset) + reg2*scale  (same scaling as loadx). Text form: storx.b r1,r2,arr,S=2 . Index is element index.
storezm                   address     - store zero at memory address
storezx     reg1,         address [,S=scale] - store zero at effective address (address + offset) + reg1*scale  (same scaling as loadx). Index is element index: byte 0-255 or word 0-32767. Text form: storezx.b r1,arr,S=4 .
storehr     reg1, slot                - store reg1 into cpu hardware register for calling convention slot (s0=A, s1=X, s2=Y, s3=AX, s4=AY, s5=XY, s6=FAC1, s7=FAC2)
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
callfar             bank,  address      Call a subroutine at the given memory address, in the given RAM/ROM bank (switches both banks at the same time).
                                        On the amiga target, this is repurposed for automatic Amiga library LVO calls:
                                        bank = library number (1=exec,2=dos,3=graphics,4=intuition,...),
                                        address = negative LVO offset (displayed as signed decimal in the IR text).
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
All have type b or w or l except the branches that only check status bits.

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
All have type b or w or l or f. Note: result types are the same as operand types! E.g. byte*byte->byte.

exts        reg1, reg2                      - reg1 = signed extension of reg2 (byte to word, or word to long)  (note: unlike M68k, exts.b -> word and exts.w -> long.)
ext         reg1, reg2                      - reg1 = unsigned extension of reg2 (which in practice just means clearing the MSB / MSW) (note: unlike M68k, ext.b -> word and ext.w -> long. )
extls       reg1, reg2                      - reg1 = signed extension of reg2 from byte directly to long (single step, no intermediate word register)
extl        reg1, reg2                      - reg1 = unsigned extension of reg2 from byte directly to long (single step, no intermediate word register)
inc         reg1                            - reg1 = reg1+1
incm                           address      - memory at address += 1
dec         reg1                            - reg1 = reg1-1
decm                           address      - memory at address -= 1
neg         reg1                            - reg1 = sign negation of reg1
negm                           address      - sign negate memory at address
addr        reg1, reg2                      - reg1 += reg2 
add         reg1,              value        - reg1 += value 
addm        reg1,              address      - memory at address += reg1 
addim                value,    address      - memory at address += value
subr        reg1, reg2                      - reg1 -= reg2 
sub         reg1,              value        - reg1 -= value 
subm        reg1,              address      - memory at address -= reg1 
subim                value,    address      - memory at address -= value
mulr        reg1, reg2                      - unsigned multiply reg1 *= reg2  note: byte*byte->byte, no type extension!
mulsr       reg1, reg2                      - signed multiply reg1 *= reg2  note: byte*byte->byte, no type extension!
mul         reg1,              value        - unsigned multiply reg1 *= value  note: byte*byte->byte, no type extension!
muls        reg1,              value        - signed multiply reg1 *= value  note: byte*byte->byte, no type extension!
mulm        reg1,              address      - unsigned memory at address  *= reg2  note: byte*byte->byte, no type extension!
mulsm       reg1,              address      - signed memory at address  *= reg2  note: byte*byte->byte, no type extension!
divr        reg1, reg2                      - unsigned division reg1 /= reg2  note: division by zero yields max int $ff/$ffff
divsr       reg1, reg2                      - signed division reg1 /= reg2  note: division by zero yields max signed int 127 / 32767 / 2147483647
div         reg1,              value        - unsigned division reg1 /= value  note: division by zero yields max int $ff/$ffff
divs        reg1,              value        - signed division reg1 /= value  note: division by zero yields max signed int 127 / 32767 / 2147483647
divm        reg1,              address      - memory at address /= reg2  note: division by zero yields max int $ff/$ffff
divsm       reg1,              address      - signed memory at address /= reg2  note: division by zero yields max signed int 127 / 32767 / 2147483647
modr        reg1, reg2                      - remainder (modulo) of unsigned division reg1 %= reg2  note: division by zero yields max signed int $ff/$ffff
mod         reg1,              value        - remainder (modulo) of unsigned division reg1 %= value  note: division by zero yields max signed int $ff/$ffff
modsr       reg1, reg2                      - remainder (modulo) of signed division reg1 %= reg2  note: division by zero yields max signed long
mods       reg1,              value        - remainder (modulo) of signed division reg1 %= value  note: division by zero yields max signed long
divmodr     reg1, reg2                      - unsigned division reg1/reg2, storing quotient in reg1 and remainder in reg2
divmod      reg1, reg2, value               - unsigned division reg1/value, storing quotient in reg1 and remainder in reg2
sdivmodr    reg1, reg2                      - signed division reg1/reg2, storing quotient in reg1 and remainder in reg2
sdivmod     reg1, reg2, value               - signed division reg1/value, storing quotient in reg1 and remainder in reg2
sqrt        reg1, reg2                      - reg1 is the square root of reg2 (reg2 can be l.1, .w or .b, result type in reg1 is .w or .b)  you can also use it with floating point types, fpreg1 and fpreg2 (result is also .f)
square      reg1, reg2                      - reg1 is the square of reg2 (reg2 can be .w or .b, result type in reg1 is always .b)  you can also use it with floating point types, fpreg1 and fpreg2 (result is also .f)
sgn         reg1, reg2                      - reg1.b is the sign of reg2 (or fpreg1, if sgn.f) (0.b, 1.b or -1.b)
cmp         reg1, reg2                      - set processor status bits C, N, Z according to comparison of reg1 with reg2. (semantics taken from 6502/68000 CMP instruction)
cmpi        reg1,              value        - set processor status bits C, N, Z according to comparison of reg1 with immediate value. (semantics taken from 6502/68000 CMP instruction)

NOTE: because mul/div are constrained (truncated) to remain in 8 or 16 or 32 bits, there is NO NEED for separate signed/unsigned mul and div instructions. The result is identical.


LOGICAL/BITWISE
---------------
All have type b or w or l.

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
asri        reg1,            value          - reg1 = multi-shift reg1 right by value bits (signed, immediate count) + Carry is undefined
lsri        reg1,            value          - reg1 = multi-shift reg1 right by value bits (immediate count) + Carry is undefined
lsli        reg1,            value          - reg1 = multi-shift reg1 left by value bits (immediate count) + Carry is undefined
asr         reg1                             - shift reg1 right by 1 bits (signed) + set Carry to shifted bit
lsr         reg1                             - shift reg1 right by 1 bits + set Carry to shifted bit
lsl         reg1                             - shift reg1 left by 1 bits + set Carry to shifted bit
lsrm                     address             - shift memory right by 1 bits + set Carry to shifted bit
asrm                     address             - shift memory right by 1 bits (signed) + set Carry to shifted bit
lslm                     address             - shift memory left by 1 bits + set Carry to shifted bit
ror         reg1                             - rotate reg1 right by 1 bits, not using carry (logical rotate, inject 0 into MSB) + set Carry to shifted bit
roxr        reg1                             - rotate reg1 right by 1 bits, using carry (inject carry into MSB) + set Carry to shifted bit  (maps to 6502 ror; M68k uses X as rotate-carry)
rol         reg1                             - rotate reg1 left by 1 bits, not using carry (logical rotate, inject 0 into LSB) + set Carry to shifted bit
roxl        reg1                             - rotate reg1 left by 1 bits, using carry (inject carry into LSB) + set Carry to shifted bit  (maps to 6502 rol; M68k uses X as rotate-carry)
rorm                     address             - rotate memory right by 1 bits, not using carry (logical rotate) + set Carry to shifted bit
roxrm                    address             - rotate memory right by 1 bits, using carry + set Carry to shifted bit    (maps to 6502 ror; M68k uses X as rotate-carry)
rolm                     address             - rotate memory left by 1 bits, not using carry (logical rotate) + set Carry to shifted bit
roxlm                    address             - rotate memory left by 1 bits, using carry + set Carry to shifted bit    (maps to 6502 rol; M68k uses X as rotate-carry)

SINGLE-BIT MANIPULATIONS
-------------------------
All have type b or w or l for the register forms, byte only for memory form.

bittst      reg1, bitpos            - test bit at position bitpos in register reg1 (Z=1 if bit clear, Z=0 if bit set)
bitset      reg1, bitpos            - set bit at position bitpos in register reg1 to 1
bitclr      reg1, bitpos            - clear bit at position bitpos in register reg1 to 0
bittog      reg1, bitpos            - toggle (complement) bit at position bitpos in register reg1


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
midb  [l]     reg1, reg2                  - reg1 becomes the 'mid' byte of the long in reg2 (bits 8-15)
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
    LOADX,
    LOADR,
    LOADHR,
    LOADI,
    LOADHFACZERO,
    LOADHFACONE,
    STOREM,
    STOREX,
    STOREZM,
    STOREZI,
    STOREIM,
    STOREZX,
    STOREHR,
    STOREI,
    STOREHFACZERO,
    STOREHFACONE,
    LOADP_INC,
    STOREP_INC,

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
    ADDIM,
    SUBR,
    SUB,
    SUBM,
    SUBIM,
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
    MODSR,
    MODS,
    DIVMODR,
    DIVMOD,
    SDIVMODR,
    SDIVMOD,
    SQRT,
    SQUARE,
    SGN,
    CMP,
    CMPI,
    EXT,
    EXTS,
    EXTL,
    EXTLS,

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
    ASRI,
    LSRI,
    LSLI,
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
    BITTST,
    BITSET,
    BITCLR,
    BITTOG,

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
    // Byte/word extraction from larger registers.
    // IMPORTANT: the destination register (reg1) has a DIFFERENT data type
    // than the source register (reg2). reg1 is always BYTE or WORD, while
    // reg2 is WORD or LONG (determined by the instruction's type field).
    //   LSIGB: least significant byte  (reg1=BYTE, reg2=type from insn)
    //   LSIGW: least significant word  (reg1=WORD, reg2=LONG)
    //   MSIGB: most significant byte   (reg1=BYTE, reg2=type from insn)
    //   MSIGW: most significant word   (reg1=WORD, reg2=LONG)
    //   BSIGB: "bits 16-23" byte of long (reg1=BYTE, reg2=LONG)
    //   MIDB:  "bits 8-15" byte of long (reg1=BYTE, reg2=LONG)
    LSIGB,
    LSIGW,
    MSIGB,
    MSIGW,
    BSIGB,
    MIDB,
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

// Opcodes that DO set the status flags (Z, N, C, V where applicable).
// These are the ONLY instructions the IR generator can rely on for branch
// conditions without emitting an explicit CMP/CMPI first. See the class-level
// contract comment at the top of this file for the full specification and
// CpuType.statusBitsOnMultiByteOps for how the contract interacts with
// multi-byte types on 8-bit vs 16/32-bit targets.
val OpcodesThatSetStatusbits = setOf(
    Opcode.BITTST,    // sets Z based on the tested bit
    Opcode.CMP,       // sets Z, N, C (and V via the comparison cascade)
    Opcode.CMPI,      // sets Z, N, C (and V via the comparison cascade)
    Opcode.SGN        // sets Z, N, C based on sign of operand
)

// On m68k (statusBitsOnMultiByteOps=true) many more opcodes set Z/N for free
// (MOVE, ADD, SUB, AND, OR, XOR, etc. set Z/N based on the full value). This
// set is used by the IR generator and peephole to skip redundant CMPI #0
// before a BSTEQ/BSTNE on m68k. On 6502 this set is NOT used.
val OpcodesThatSetZeroFlagOnM68k = OpcodesThatSetStatusbits + setOf(
    Opcode.LOAD, Opcode.LOADM, Opcode.LOADX, Opcode.LOADI, Opcode.LOADR,
    Opcode.INC, Opcode.DEC, Opcode.NEG,
    Opcode.ADDR, Opcode.ADD, Opcode.SUBR, Opcode.SUB,
    Opcode.ANDR, Opcode.AND, Opcode.ORR, Opcode.OR, Opcode.XORR, Opcode.XOR,
    Opcode.INV, Opcode.EXT, Opcode.EXTS, Opcode.EXTL, Opcode.EXTLS,
    Opcode.MULR, Opcode.MUL, Opcode.MULSR, Opcode.MULS,
    Opcode.DIVR, Opcode.DIV, Opcode.DIVSR, Opcode.DIVS,
    Opcode.ASR, Opcode.LSR, Opcode.LSL,
    Opcode.ASRN, Opcode.LSRN, Opcode.LSLN,
    Opcode.ASRI, Opcode.LSRI, Opcode.LSLI,
    Opcode.LSIGB, Opcode.LSIGW, Opcode.MSIGB, Opcode.MSIGW, Opcode.BSIGB, Opcode.MIDB, Opcode.CONCAT
)

val OpcodesThatDependOnCarry = setOf(
    Opcode.BSTCC,
    Opcode.BSTCS,
    Opcode.BSTPOS,
    Opcode.BSTNEG,
    Opcode.ROXL,
    Opcode.ROXLM,
    Opcode.ROXR,
    Opcode.ROXRM
)

val OpcodesThatLoad = setOf(
    Opcode.LOAD,
    Opcode.LOADM,
    Opcode.LOADX,
    Opcode.LOADR,
    Opcode.LOADHR,
    Opcode.LOADI,
    Opcode.LOADHFACZERO,
    Opcode.LOADHFACONE
)

val OpcodesWithSideEffects = OpcodesThatBranch + setOf(
    Opcode.PUSH,
    Opcode.POP,
    Opcode.PUSHST,
    Opcode.POPST,
    Opcode.BREAKPOINT
)


enum class IRDataType {
    BYTE,
    WORD,
    FLOAT,
    LONG,        // 32 bits integer
    POINTER      // pointer (size depends on target)
}

/**
 * A single IR instruction with structured, typed operands.
 *
 * The operands an instruction has are declared by its [OpcodeSchema]; the schema also declares
 * the role, direction (use/def) and data type of every operand. There are no positional
 * "reg1/reg2/address/immediate" fields: every operand is a typed value that knows what it is.
 */
data class IRInstruction(
    val opcode: Opcode,
    val type: IRDataType? = null,
    val dest: RegisterOperand? = null,
    val destB: RegisterOperand? = null,
    val srcA: RegisterOperand? = null,
    val srcB: RegisterOperand? = null,
    val immediate: ImmediateOperand? = null,
    val hardwareSlot: HardwareSlotOperand? = null,
    val memory: MemoryReference? = null,
    val target: CodeReference? = null,
    val callSite: CallSite? = null
) {
    init {
        OpcodeSchemas.validate(this)
    }

    val schema: OpcodeSchema
        get() = OpcodeSchemas.get(opcode, type)

    /** all register accesses of this instruction, including the ones inside memory references and call sites */
    val registerAccesses: List<RegisterOperand>
        get() = buildList {
            dest?.let { add(it) }
            destB?.let { add(it) }
            srcA?.let { add(it) }
            srcB?.let { add(it) }
            memory?.let { addAll(it.registers) }
            (target as? CodeReference.Indirect)?.let { add(it.pointer) }
            callSite?.let { addAll(it.registerAccesses) }
        }

    /** the registers that are read by this instruction */
    val uses: Set<VirtualRegister>
        get() = registerAccesses.filter { it.direction != OperandDirection.DEF }.mapTo(mutableSetOf()) { it.register }

    /** the registers that are written by this instruction */
    val definitions: Set<VirtualRegister>
        get() = registerAccesses.filter { it.direction != OperandDirection.USE }.mapTo(mutableSetOf()) { it.register }

    val memoryEffect: MemoryEffect
        get() = callSite?.effects?.memoryEffect ?: schema.memoryEffect

    val statusEffect: StatusEffect
        get() = callSite?.effects?.statusEffect ?: schema.statusEffect

    val controlFlow: ControlFlowEffect
        get() = schema.controlFlow

    /** the code location this instruction transfers control to (branch/jump/call), if it is a static one */
    val codeTarget: CodeReference?
        get() = target ?: callSite?.codeReference

    /** the label this instruction branches/jumps/calls to, if any */
    val labelTarget: String?
        get() = (codeTarget as? CodeReference.Label)?.name

    /** replace every virtual register in this instruction (registers keep their type, role and direction) */
    fun mapRegisters(transform: (VirtualRegister) -> VirtualRegister): IRInstruction {
        fun map(operand: RegisterOperand?) = operand?.withRegister(transform(operand.register))
        return copy(
            dest = map(dest),
            destB = map(destB),
            srcA = map(srcA),
            srcB = map(srcB),
            memory = memory?.mapRegisters(transform),
            target = (target as? CodeReference.Indirect)?.let { it.copy(pointer = it.pointer.withRegister(transform(it.pointer.register))) } ?: target,
            callSite = callSite?.mapRegisters(transform)
        )
    }

    /** transform the memory reference of this instruction (if it has one) */
    fun mapMemoryReferences(transform: (MemoryReference) -> MemoryReference): IRInstruction =
        if (memory == null) this else copy(memory = transform(memory))

    /** set the code location this instruction transfers control to */
    fun withTarget(reference: CodeReference): IRInstruction = when {
        target != null -> copy(target = reference)
        callSite != null -> copy(callSite = callSite.withTarget(reference))
        else -> throw IllegalArgumentException("$opcode has no code target")
    }

    /**
     * Count the register reads and writes of this instruction, and record the data type of every
     * integer register that is used. Float registers always have the float type so they're not recorded.
     */
    fun addUsedRegistersCounts(
        readRegsCounts: MutableMap<VirtualRegister, Int>,
        writeRegsCounts: MutableMap<VirtualRegister, Int>,
        regsTypes: MutableMap<VirtualRegister, IRDataType>,
        chunk: IRCodeChunk?
    ) {
        fun setRegType(register: VirtualRegister, type: IRDataType) {
            if (type == IRDataType.FLOAT)
                return
            val existingType = regsTypes[register]
            if (existingType == null) {
                regsTypes[register] = type
            } else if (existingType != type) {
                // POINTER is compatible with WORD or LONG (size depends on target)
                val compatible = (existingType == IRDataType.POINTER && type in setOf(IRDataType.WORD, IRDataType.LONG)) ||
                        (type == IRDataType.POINTER && existingType in setOf(IRDataType.WORD, IRDataType.LONG))
                if (!compatible)
                    throw IllegalArgumentException("register $register given multiple types! $existingType and $type while processing $this in $chunk")
            }
        }

        for (access in registerAccesses) {
            when (access.direction) {
                OperandDirection.USE -> readRegsCounts.merge(access.register, 1, Int::plus)
                OperandDirection.DEF -> writeRegsCounts.merge(access.register, 1, Int::plus)
                OperandDirection.USE_DEF -> {
                    readRegsCounts.merge(access.register, 1, Int::plus)
                    writeRegsCounts.merge(access.register, 1, Int::plus)
                }
            }
            setRegType(access.register, access.type)
        }
    }

    override fun toString(): String = IRTextCodec.print(this)
}
