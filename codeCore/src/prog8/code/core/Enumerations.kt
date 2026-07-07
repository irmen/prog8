package prog8.code.core


enum class CpuRegister {
    A,
    X,
    Y,
    // m68k:
    D0, D1, D2, D3, D4, D5, D6, D7,
    A0, A1, A2, A3, A4, A5, A6,
    FP0, FP1, FP2, FP3, FP4, FP5, FP6, FP7
}

enum class RegisterOrPair {
    A,
    X,
    Y,
    AX,
    AY,
    XY,
    FAC1,
    FAC2,
    // cx16 virtual registers:
    R0, R1, R2, R3, R4, R5, R6, R7,
    R8, R9, R10, R11, R12, R13, R14, R15,
    // combined virtual registers to store 32 bits longs:
    R0R1, R2R3, R4R5, R6R7, R8R9, R10R11, R12R13, R14R15,
    // m68k data & address registers:
    D0, D1, D2, D3, D4, D5, D6, D7,
    A0, A1, A2, A3, A4, A5, A6,
    // m68k FPU registers:
    FP0, FP1, FP2, FP3, FP4, FP5, FP6, FP7;

    companion object {
        val names: Set<String> = entries.map { it.toString() }.toSet()
        fun fromCpuRegister(cpu: CpuRegister): RegisterOrPair {
            return when(cpu) {
                CpuRegister.A -> A
                CpuRegister.X -> X
                CpuRegister.Y -> Y
                CpuRegister.D0 -> D0
                CpuRegister.D1 -> D1
                CpuRegister.D2 -> D2
                CpuRegister.D3 -> D3
                CpuRegister.D4 -> D4
                CpuRegister.D5 -> D5
                CpuRegister.D6 -> D6
                CpuRegister.D7 -> D7
                CpuRegister.A0 -> A0
                CpuRegister.A1 -> A1
                CpuRegister.A2 -> A2
                CpuRegister.A3 -> A3
                CpuRegister.A4 -> A4
                CpuRegister.A5 -> A5
                CpuRegister.A6 -> A6
                CpuRegister.FP0 -> FP0
                CpuRegister.FP1 -> FP1
                CpuRegister.FP2 -> FP2
                CpuRegister.FP3 -> FP3
                CpuRegister.FP4 -> FP4
                CpuRegister.FP5 -> FP5
                CpuRegister.FP6 -> FP6
                CpuRegister.FP7 -> FP7
            }
        }
    }

    /**
     * Returns the starting virtual register name for the current 32-bit combined virtual register
     * @return The starting register name as a string, WITHOUT THE cx16 block scope prefix!
     */
    fun startregname() = when(this) {
        R0R1 -> "r0"
        R2R3 -> "r2"
        R4R5 -> "r4"
        R6R7 -> "r6"
        R8R9 -> "r8"
        R10R11 -> "r10"
        R12R13 -> "r12"
        R14R15 -> "r14"
        else -> throw IllegalArgumentException("must be a combined virtual register $this")
    }

    fun asCpuRegister(): CpuRegister = when(this) {
        A -> CpuRegister.A
        X -> CpuRegister.X
        Y -> CpuRegister.Y
        else -> throw IllegalArgumentException("no cpu hardware register for $this")
    }

    fun asScopedNameVirtualReg(type: DataType?): List<String> {
        require(this in Cx16VirtualRegisters || this in CombinedLongRegisters)
        val suffix = when(type?.base) {
            BaseDataType.UBYTE, BaseDataType.BOOL -> "L"
            BaseDataType.BYTE -> "sL"
            BaseDataType.WORD -> "s"
            BaseDataType.UWORD, BaseDataType.POINTER, null -> ""
            BaseDataType.LONG -> "sl"
            else -> throw IllegalArgumentException("invalid register param type for cx16 virtual reg")
        }
        return listOf("cx16", name.lowercase()+suffix)
    }

    fun isWord() = this==AX || this == AY || this==XY || this in Cx16VirtualRegisters
    fun isLong() = this in CombinedLongRegisters

}       // only used in parameter and return value specs in asm subroutines

enum class Statusflag {
    Pc,
    Pz,     // don't use
    Pv,
    Pn;     // don't use

    companion object {
        val names: Set<String> = entries.map { it.toString() }.toSet()
    }
}

enum class BranchCondition {
    CS,
    CC,
    EQ,     // EQ == Z
    Z,
    NE,     // NE == NZ
    NZ,
    MI,     // MI == NEG
    NEG,
    PL,     // PL == POS
    POS,
    VS,
    VC
}

val Cx16VirtualRegisters = setOf(
    RegisterOrPair.R0, RegisterOrPair.R1, RegisterOrPair.R2, RegisterOrPair.R3,
    RegisterOrPair.R4, RegisterOrPair.R5, RegisterOrPair.R6, RegisterOrPair.R7,
    RegisterOrPair.R8, RegisterOrPair.R9, RegisterOrPair.R10, RegisterOrPair.R11,
    RegisterOrPair.R12, RegisterOrPair.R13, RegisterOrPair.R14, RegisterOrPair.R15
)

val CombinedLongRegisters = setOf(
    RegisterOrPair.R0R1,
    RegisterOrPair.R2R3,
    RegisterOrPair.R4R5,
    RegisterOrPair.R6R7,
    RegisterOrPair.R8R9,
    RegisterOrPair.R10R11,
    RegisterOrPair.R12R13,
    RegisterOrPair.R14R15
)

val M68kRegisters = setOf(
    RegisterOrPair.D0, RegisterOrPair.D1, RegisterOrPair.D2, RegisterOrPair.D3,
    RegisterOrPair.D4, RegisterOrPair.D5, RegisterOrPair.D6, RegisterOrPair.D7,
    RegisterOrPair.A0, RegisterOrPair.A1, RegisterOrPair.A2, RegisterOrPair.A3,
    RegisterOrPair.A4, RegisterOrPair.A5, RegisterOrPair.A6,
    RegisterOrPair.FP0, RegisterOrPair.FP1, RegisterOrPair.FP2, RegisterOrPair.FP3,
    RegisterOrPair.FP4, RegisterOrPair.FP5, RegisterOrPair.FP6, RegisterOrPair.FP7
)

val CpuRegisters = setOf(
    RegisterOrPair.A, RegisterOrPair.X, RegisterOrPair.Y,
    RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY
)


enum class OutputType {
    RAW,
    PRG,
    XEX,
    ELF,
    LIBRARY
}

enum class CbmPrgLauncherType {
    BASIC,
    NONE
}

enum class ZeropageType {
    BASICSAFE,
    FLOATSAFE,
    KERNALSAFE,
    FULL,
    DONTUSE
}

enum class ZeropageWish {
    REQUIRE_ZEROPAGE,
    PREFER_ZEROPAGE,
    DONTCARE,
    NOT_IN_ZEROPAGE
}

enum class SplitWish {
    DONTCARE,
    NOSPLIT
}
