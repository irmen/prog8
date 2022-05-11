package prog8.vm

/*

Virtual machine:

65536 virtual registers, 16 bits wide, can also be used as 8 bits. r0-r65535
65536 virtual floating point registers (32 bits single precision floats)  fr0-fr65535
65536 bytes of memory. Thus memory pointers (addresses) are limited to 16 bits.
Value stack, max 128 entries of 1 byte each.
Status registers: Carry, Zero, Negative.

Most instructions have an associated data type 'b','w','f'. (omitting it means 'b'/byte).
Currently NO support for 24 or 32 bits integers.
Floating point operations are just 'f' typed regular instructions, and additionally there are
a few fp conversion instructions to

*only* LOAD AND STORE instructions have a possible memory operand, all other instructions use only registers or immediate value.


LOAD/STORE
----------
All have type b or w or f.

load        reg1,         value       - load immediate value into register
loadm       reg1,         address     - load reg1 with value at memory address
loadi       reg1, reg2                - load reg1 with value at memory indirect, memory pointed to by reg2
loadx       reg1, reg2,   address     - load reg1 with value at memory address, indexed by value in reg2
loadr       reg1, reg2                - load reg1 with value at register reg2

storem      reg1,         address     - store reg1 at memory address
storei      reg1, reg2                - store reg1 at memory indirect, memory pointed to by reg2
storex      reg1, reg2,   address     - store reg1 at memory address, indexed by value in reg2
storezm                   address     - store zero at memory address
storezi     reg1                      - store zero at memory pointed to by reg1
storezx     reg1,         address     - store zero at memory address, indexed by value in reg


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
add         reg1, reg2, reg3                - reg1 = reg2+reg3 (unsigned + signed)
sub         reg1, reg2, reg3                - reg1 = reg2-reg3 (unsigned + signed)
mul         reg1, reg2, reg3                - unsigned multiply reg1=reg2*reg3  note: byte*byte->byte, no type extension to word!
div         reg1, reg2, reg3                - unsigned division reg1=reg2/reg3  note: division by zero yields max signed int $ff/$ffff
mod         reg1, reg2, reg3                - remainder (modulo) of unsigned division reg1=reg2%reg3  note: division by zero yields max signed int $ff/$ffff
sqrt        reg1, reg2                      - reg1 is the square root of reg2
sgn         reg1, reg2                      - reg1 is the sign of reg2 (0, 1 or -1)
cmp         reg1, reg2                      - set processor status bits C, N, Z according to comparison of reg1 with reg2. (semantics taken from 6502/68000 CMP instruction)
rnd         reg1                            - get a random number (byte, word or float)

NOTE: because mul/div are constrained (truncated) to remain in 8 or 16 bits, there is NO NEED for separate signed/unsigned mul and div instructions. The result is identical.


LOGICAL/BITWISE
---------------
All have type b or w.

and         reg1, reg2, reg3                 - reg1 = reg2 bitwise and reg3
or          reg1, reg2, reg3                 - reg1 = reg2 bitwise or reg3
xor         reg1, reg2, reg3                 - reg1 = reg2 bitwise xor reg3
lsrn        reg1, reg2, reg3                 - reg1 = multi-shift reg2 right by reg3 bits + set Carry to shifted bit
asrn        reg1, reg2, reg3                 - reg1 = multi-shift reg2 right by reg3 bits (signed)  + set Carry to shifted bit
lsln        reg1, reg2, reg3                 - reg1 = multi-shift reg2 left by reg3 bits  + set Carry to shifted bit
lsr         reg1                             - shift reg1 right by 1 bits + set Carry to shifted bit
asr         reg1                             - shift reg1 right by 1 bits (signed) + set Carry to shifted bit
lsl         reg1                             - shift reg1 left by 1 bits + set Carry to shifted bit
ror         reg1                             - rotate reg1 right by 1 bits, not using carry  + set Carry to shifted bit
roxr        reg1                             - rotate reg1 right by 1 bits, using carry  + set Carry to shifted bit
rol         reg1                             - rotate reg1 left by 1 bits, not using carry  + set Carry to shifted bit
roxl        reg1                             - rotate reg1 left by 1 bits, using carry,  + set Carry to shifted bit


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
fpow         fpreg1, fpreg2, fpreg3     - fpreg1 = fpreg2 to the power of fpreg3
fabs         fpreg1, fpreg2             - fpreg1 = abs(fpreg2)


MISC
----

clc                                       - clear Carry status bit
sec                                       - set Carry status bit
nop                                       - do nothing
breakpoint                                - trigger a breakpoint
msig [b, w]   reg1, reg2                  - reg1 becomes the most significant byte (or word) of the word (or int) in reg2  (.w not yet implemented; requires 32 bits regs)
concat [b, w] reg1, reg2, reg3            - reg1 = concatenated lsb/lsw of reg2 and lsb/lsw of reg3 into new word or int (int not yet implemented; requires 32bits regs)
push [b, w]   reg1                        - push value in reg1 on the stack
pop [b, w]    reg1                        - pop value from stack into reg1

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
    STOREZM,
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
    BLT,        // TODO not used in codegen??? <
    BLTS,       // TODO not used in codegen??? <
    BGT,        // TODO not used in codegen??? >
    BGTS,       // TODO not used in codegen??? >
    BLE,        // TODO should be used in codegen conditional branch too
    BLES,       // TODO should be used in codegen conditional branch too
    BGE,        // TODO not used in codegen??? >=
    BGES,       // TODO not used in codegen??? >=
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
    SQRT,
    SGN,
    CMP,
    RND,
    EXT,
    EXTS,

    AND,
    OR,
    XOR,
    ASRN,
    LSRN,
    LSLN,
    ASR,
    LSR,
    LSL,
    ROR,
    ROXR,
    ROL,
    ROXL,

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
    FSQRT,
    FROUND,
    FFLOOR,
    FCEIL,

    CLC,
    SEC,
    PUSH,
    POP,
    MSIG,
    CONCAT,
    BREAKPOINT
}

val OpcodesWithAddress = setOf(
    Opcode.LOADM,
    Opcode.LOADX,
    Opcode.STOREM,
    Opcode.STOREX,
    Opcode.STOREZM,
    Opcode.STOREZX
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
    val reg3: Int?=null,        // 0-$ffff
    val fpReg1: Int?=null,      // 0-$ffff
    val fpReg2: Int?=null,      // 0-$ffff
    val fpReg3: Int?=null,      // 0-$ffff
    val value: Int?=null,       // 0-$ffff
    val fpValue: Float?=null,
    val symbol: List<String>?=null    // alternative to value
) {
    init {
        val formats = instructionFormats.getValue(opcode)
        if(type==null && !formats.containsKey(null))
            throw IllegalArgumentException("missing type")

        val format = formats.getValue(type)
        if(format.reg1 && reg1==null ||
            format.reg2 && reg2==null ||
            format.reg3 && reg3==null)
            throw IllegalArgumentException("missing a register (int)")

        if(format.fpReg1 && fpReg1==null ||
            format.fpReg2 && fpReg2==null ||
            format.fpReg3 && fpReg3==null)
            throw IllegalArgumentException("missing a register (float)")

        if(!format.reg1 && reg1!=null ||
            !format.reg2 && reg2!=null ||
            !format.reg3 && reg3!=null)
            throw IllegalArgumentException("too many registers (int)")

        if(!format.fpReg1 && fpReg1!=null ||
            !format.fpReg2 && fpReg2!=null ||
            !format.fpReg3 && fpReg3!=null)
            throw IllegalArgumentException("too many registers (float)")

        if (type==VmDataType.FLOAT) {
            if(format.fpValue && (fpValue==null && symbol==null))
                throw IllegalArgumentException("$opcode: missing a fp-value or symbol")
        } else {
            if(format.value && (value==null && symbol==null))
                throw IllegalArgumentException("$opcode: missing a value or symbol")
            if (fpReg1 != null || fpReg2 != null || fpReg3 != null)
                throw java.lang.IllegalArgumentException("$opcode: integer point instruction can't use floating point registers")
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
        fpReg3?.let {
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
        symbol?.let {
            result.add("_" + it.joinToString("."))
        }
        if(result.last() == ",")
            result.removeLast()
        return result.joinToString("").trimEnd()
    }
}

data class InstructionFormat(val datatype: VmDataType?,
                             val reg1: Boolean, val reg2: Boolean, val reg3: Boolean,
                             val fpReg1: Boolean, val fpReg2: Boolean, val fpReg3: Boolean,
                             val value: Boolean,
                             val fpValue: Boolean) {
    companion object {
        fun from(spec: String): Map<VmDataType?, InstructionFormat> {
            val result = mutableMapOf<VmDataType?, InstructionFormat>()
            for(part in spec.split('|').map{ it.trim() }) {
                var reg1 = false
                var reg2 = false
                var reg3 = false
                var fpreg1 = false
                var fpreg2 = false
                var fpreg3 = false
                var value = false
                var fpvalue = false
                val splits = part.splitToSequence(',').iterator()
                val typespec = splits.next()
                while(splits.hasNext()) {
                    when(splits.next()) {
                        "r1" -> reg1=true
                        "r2" -> reg2=true
                        "r3" -> reg3=true
                        "fr1" -> fpreg1=true
                        "fr2" -> fpreg2=true
                        "fr3" -> fpreg3=true
                        "v" -> value = true
                        "fv" -> fpvalue = true
                        else -> throw IllegalArgumentException(spec)
                    }
                }
                if(typespec=="N")
                    result[null] = InstructionFormat(null, reg1=reg1, reg2=reg2, reg3=reg3, fpReg1=fpreg1, fpReg2=fpreg2, fpReg3=fpreg3, value=value, fpValue=fpvalue)
                if('B' in typespec)
                    result[VmDataType.BYTE] = InstructionFormat(VmDataType.BYTE, reg1=reg1, reg2=reg2, reg3=reg3, fpReg1=fpreg1, fpReg2=fpreg2, fpReg3=fpreg3, value=value, fpValue=fpvalue)
                if('W' in typespec)
                    result[VmDataType.WORD] = InstructionFormat(VmDataType.WORD, reg1=reg1, reg2=reg2, reg3=reg3, fpReg1=fpreg1, fpReg2=fpreg2, fpReg3=fpreg3, value=value, fpValue=fpvalue)
                if('F' in typespec)
                    result[VmDataType.FLOAT] = InstructionFormat(VmDataType.FLOAT, reg1=reg1, reg2=reg2, reg3=reg3, fpReg1=fpreg1, fpReg2=fpreg2, fpReg3=fpreg3, value=value, fpValue=fpvalue)
            }
            return result
        }
    }
}


@Suppress("BooleanLiteralArgument")
val instructionFormats = mutableMapOf(
    Opcode.NOP        to InstructionFormat.from("N"),
    Opcode.LOAD       to InstructionFormat.from("BW,r1,v    | F,fr1,fv"),
    Opcode.LOADM      to InstructionFormat.from("BW,r1,v    | F,fr1,v"),
    Opcode.LOADI      to InstructionFormat.from("BW,r1,r2   | F,fr1,r1"),
    Opcode.LOADX      to InstructionFormat.from("BW,r1,r2,v | F,fr1,r1,v"),
    Opcode.LOADR      to InstructionFormat.from("BW,r1,r2   | F,fr1,fr2"),
    Opcode.STOREM     to InstructionFormat.from("BW,r1,v    | F,fr1,v"),
    Opcode.STOREI     to InstructionFormat.from("BW,r1,r2   | F,fr1,r1"),
    Opcode.STOREX     to InstructionFormat.from("BW,r1,r2,v | F,fr1,r1,v"),
    Opcode.STOREZM     to InstructionFormat.from("BW,v       | F,v"),
    Opcode.STOREZI    to InstructionFormat.from("BW,r1      | F,r1"),
    Opcode.STOREZX    to InstructionFormat.from("BW,r1,v    | F,r1,v"),
    Opcode.JUMP       to InstructionFormat.from("N,v"),
    Opcode.CALL       to InstructionFormat.from("N,v"),
    Opcode.SYSCALL    to InstructionFormat.from("N,v"),
    Opcode.RETURN     to InstructionFormat.from("N"),
    Opcode.BSTCC      to InstructionFormat.from("N,v"),
    Opcode.BSTCS      to InstructionFormat.from("N,v"),
    Opcode.BSTEQ      to InstructionFormat.from("N,v"),
    Opcode.BSTNE      to InstructionFormat.from("N,v"),
    Opcode.BSTNEG     to InstructionFormat.from("N,v"),
    Opcode.BSTPOS     to InstructionFormat.from("N,v"),
    Opcode.BZ         to InstructionFormat.from("BW,r1,v"),
    Opcode.BNZ        to InstructionFormat.from("BW,r1,v"),
    Opcode.BEQ        to InstructionFormat.from("BW,r1,r2,v"),
    Opcode.BNE        to InstructionFormat.from("BW,r1,r2,v"),
    Opcode.BLT        to InstructionFormat.from("BW,r1,r2,v"),
    Opcode.BLTS       to InstructionFormat.from("BW,r1,r2,v"),
    Opcode.BGT        to InstructionFormat.from("BW,r1,r2,v"),
    Opcode.BGTS       to InstructionFormat.from("BW,r1,r2,v"),
    Opcode.BLE        to InstructionFormat.from("BW,r1,r2,v"),
    Opcode.BLES       to InstructionFormat.from("BW,r1,r2,v"),
    Opcode.BGE        to InstructionFormat.from("BW,r1,r2,v"),
    Opcode.BGES       to InstructionFormat.from("BW,r1,r2,v"),
    Opcode.SEQ        to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.SNE        to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.SLT        to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.SLTS       to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.SGT        to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.SGTS       to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.SLE        to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.SLES       to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.SGE        to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.SGES       to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.INC        to InstructionFormat.from("BW,r1"),
    Opcode.INCM       to InstructionFormat.from("BW,v"),
    Opcode.DEC        to InstructionFormat.from("BW,r1"),
    Opcode.DECM       to InstructionFormat.from("BW,v"),
    Opcode.NEG        to InstructionFormat.from("BW,r1          | F,fr1"),
    Opcode.ADD        to InstructionFormat.from("BW,r1,r2,r3    | F,fr1,fr2,fr3"),
    Opcode.SUB        to InstructionFormat.from("BW,r1,r2,r3    | F,fr1,fr2,fr3"),
    Opcode.MUL        to InstructionFormat.from("BW,r1,r2,r3    | F,fr1,fr2,fr3"),
    Opcode.DIV        to InstructionFormat.from("BW,r1,r2,r3    | F,fr1,fr2,fr3"),
    Opcode.SQRT       to InstructionFormat.from("BW,r1,r2       | F,fr1,fr2"),
    Opcode.SGN        to InstructionFormat.from("BW,r1,r2       | F,fr1,fr2"),
    Opcode.RND        to InstructionFormat.from("BW,r1          | F,fr1"),
    Opcode.MOD        to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.CMP        to InstructionFormat.from("BW,r1,r2"),
    Opcode.EXT        to InstructionFormat.from("BW,r1"),
    Opcode.EXTS       to InstructionFormat.from("BW,r1"),
    Opcode.AND        to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.OR         to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.XOR        to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.ASRN       to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.LSRN       to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.LSLN       to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.ASR        to InstructionFormat.from("BW,r1"),
    Opcode.LSR        to InstructionFormat.from("BW,r1"),
    Opcode.LSL        to InstructionFormat.from("BW,r1"),
    Opcode.ROR        to InstructionFormat.from("BW,r1"),
    Opcode.ROXR       to InstructionFormat.from("BW,r1"),
    Opcode.ROL        to InstructionFormat.from("BW,r1"),
    Opcode.ROXL       to InstructionFormat.from("BW,r1"),

    Opcode.FFROMUB    to InstructionFormat.from("F,fr1,r1"),
    Opcode.FFROMSB    to InstructionFormat.from("F,fr1,r1"),
    Opcode.FFROMUW    to InstructionFormat.from("F,fr1,r1"),
    Opcode.FFROMSW    to InstructionFormat.from("F,fr1,r1"),
    Opcode.FTOUB      to InstructionFormat.from("F,r1,fr1"),
    Opcode.FTOSB      to InstructionFormat.from("F,r1,fr1"),
    Opcode.FTOUW      to InstructionFormat.from("F,r1,fr1"),
    Opcode.FTOSW      to InstructionFormat.from("F,r1,fr1"),
    Opcode.FPOW       to InstructionFormat.from("F,fr1,fr2,fr3"),
    Opcode.FABS       to InstructionFormat.from("F,fr1,fr2"),
    Opcode.FSIN       to InstructionFormat.from("F,fr1,fr2"),
    Opcode.FCOS       to InstructionFormat.from("F,fr1,fr2"),
    Opcode.FTAN       to InstructionFormat.from("F,fr1,fr2"),
    Opcode.FATAN      to InstructionFormat.from("F,fr1,fr2"),
    Opcode.FLN        to InstructionFormat.from("F,fr1,fr2"),
    Opcode.FLOG       to InstructionFormat.from("F,fr1,fr2"),
    Opcode.FSQRT      to InstructionFormat.from("F,fr1,fr2"),
    Opcode.FROUND     to InstructionFormat.from("F,fr1,fr2"),
    Opcode.FFLOOR     to InstructionFormat.from("F,fr1,fr2"),
    Opcode.FCEIL      to InstructionFormat.from("F,fr1,fr2"),

    Opcode.MSIG       to InstructionFormat.from("BW,r1,r2"),
    Opcode.PUSH       to InstructionFormat.from("BW,r1"),
    Opcode.POP        to InstructionFormat.from("BW,r1"),
    Opcode.CONCAT     to InstructionFormat.from("BW,r1,r2,r3"),
    Opcode.CLC        to InstructionFormat.from("N"),
    Opcode.SEC        to InstructionFormat.from("N"),
    Opcode.BREAKPOINT to InstructionFormat.from("N"),
)
