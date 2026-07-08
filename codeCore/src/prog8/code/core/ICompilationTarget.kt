package prog8.code.core

import java.nio.file.Path

/**
 * Identifies the target CPU type and its key characteristics that affect IR semantics.
 *
 * The most important property is [statusBitsOnMultiByteOps], which controls the IR
 * status-bit contract (see IRInstructions.kt for the full spec):
 *
 * - When `true`, the IR generator is allowed to skip emitting explicit CMP/CMPI before
 *   branches that depend on the result of a previous "status-setting" instruction
 *   (LOAD, INC, DEC, AND, OR, XOR, etc.). The contract is: "this instruction sets
 *   Z and N based on the full multi-byte value." Backends for these targets must
 *   honor this contract (e.g., M68000's `move.w #imm,d0` naturally sets Z based on
 *   the 16-bit value, so the IR can rely on it).
 *
 * - When `false`, multi-byte operations on this target do NOT set status bits
 *   correctly for the full value (e.g., 6502's `dec lo` only sets Z based on the
 *   low byte of a 16-bit decrement). The IR generator must therefore always emit
 *   an explicit CMP/CMPI before any branch that depends on the result. Backends
 *   for these targets should NOT add `ora` postambles to set Z; instead they
 *   rely on the explicit CMP/CMPI emitted by the IR generator.
 *
 *   For 8-bit CPUs (6502, 65C02) the natural status flags from a multi-byte
 *   cascade are misleading: Z is set based on the LAST byte operated on, not
 *   the full value. So we treat the contract as "no instruction sets status
 *   bits except CMP/CMPI/SEC/CLC/SGN/BITTST" and have the IR generator emit
 *   explicit compares. This avoids the trap of "this works for BYTE but
 *   silently breaks for WORD/LONG".
 */
enum class CpuType(val statusBitsOnMultiByteOps: Boolean) {
    /** 8-bit NMOS 6502. Multi-byte ops don't set Z based on full value. */
    CPU6502(false),

    /** 8-bit CMOS 65C02. Same status-bit issue as plain 6502. */
    CPU65C02(false),
    
    /** 16/32 bit Motorola 68020. has single instructions that set status bits when dealing with multi byte operands */
    M68020(true),

    /**
     * Virtual machine target. We use `false` here so the IR generator always
     * emits explicit CMP/CMPI; the VM's setResultReg then does NOT set Z/N
     * flags (only CMP/CMPI/SEC/CLC/SGN/BITTST do). This matches the VM's
     * existing documentation (VirtualMachine.kt top comment) and keeps the
     * behavior consistent across all current targets.
     */
    VIRTUAL(false)
}

interface ICompilationTarget: IStringEncoding, IMemSizer {
    val name: String

    val FLOAT_MAX_NEGATIVE: Double
    val FLOAT_MAX_POSITIVE: Double
    val FLOAT_MEM_SIZE: UInt
    val POINTER_MEM_SIZE: UInt
    val PROGRAM_LOAD_ADDRESS : UInt
    val PROGRAM_MEMTOP_ADDRESS: UInt
    val BSSHIGHRAM_START: UInt
    val BSSHIGHRAM_END: UInt
    val BSSGOLDENRAM_START: UInt
    val BSSGOLDENRAM_END: UInt

    val cpu: CpuType
    var zeropage: Zeropage
    val libraryPath: Path?
    val customLauncher: List<String>
    val additionalAssemblerOptions: List<String>
    val defaultOutputType: OutputType
    val defaultLauncherType: CbmPrgLauncherType
    val supportsBankedCalls: Boolean

    fun initializeMemoryAreas(compilerOptions: CompilationOptions)
    fun getFloatAsmBytes(num: Number): String

    fun convertFloatToBytes(num: Double): List<UByte>
    fun convertBytesToFloat(bytes: List<UByte>): Double

    fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path, quiet: Boolean)
    fun isIOAddress(address: UInt): Boolean

    override fun encodeString(str: String, encoding: Encoding): List<UByte>
    override fun decodeString(bytes: Iterable<UByte>, encoding: Encoding): String
}
