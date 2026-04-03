package prog8.code.core


enum class CpuRegister {
    A,
    X,
    Y
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
    R0R1, R2R3, R4R5, R6R7, R8R9, R10R11, R12R13, R14R15;

    companion object {
        val names: Set<String> = entries.map { it.toString() }.toSet()
        fun fromCpuRegister(cpu: CpuRegister): RegisterOrPair {
            return when(cpu) {
                CpuRegister.A -> A
                CpuRegister.X -> X
                CpuRegister.Y -> Y
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

val Cx16VirtualRegisters = arrayOf(
    RegisterOrPair.R0, RegisterOrPair.R1, RegisterOrPair.R2, RegisterOrPair.R3,
    RegisterOrPair.R4, RegisterOrPair.R5, RegisterOrPair.R6, RegisterOrPair.R7,
    RegisterOrPair.R8, RegisterOrPair.R9, RegisterOrPair.R10, RegisterOrPair.R11,
    RegisterOrPair.R12, RegisterOrPair.R13, RegisterOrPair.R14, RegisterOrPair.R15
)

val CombinedLongRegisters = arrayOf(
    RegisterOrPair.R0R1,
    RegisterOrPair.R2R3,
    RegisterOrPair.R4R5,
    RegisterOrPair.R6R7,
    RegisterOrPair.R8R9,
    RegisterOrPair.R10R11,
    RegisterOrPair.R12R13,
    RegisterOrPair.R14R15
)

val CpuRegisters = arrayOf(
    RegisterOrPair.A, RegisterOrPair.X, RegisterOrPair.Y,
    RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY
)


enum class OutputType {
    RAW,
    PRG,
    XEX,
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
