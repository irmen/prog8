package prog8.code.core

import java.nio.file.Path

enum class CpuType {
    CPU6502,
    CPU65C02,
    VIRTUAL
}

enum class ProgramType {
    CBMPRG,
    ATARIXEX,
    NEORAW,
    VIRTUALIR
}

interface ICompilationTarget: IStringEncoding, IMemSizer {
    val name: String

    val FLOAT_MAX_NEGATIVE: Double
    val FLOAT_MAX_POSITIVE: Double
    val FLOAT_MEM_SIZE: Int
    val STARTUP_CODE_RESERVED_SIZE: UInt        // this is here, so that certain compiler targets are able to tune this
    val PROGRAM_LOAD_ADDRESS : UInt
    val PROGRAM_MEMTOP_ADDRESS: UInt
    val BSSHIGHRAM_START: UInt
    val BSSHIGHRAM_END: UInt
    val BSSGOLDENRAM_START: UInt
    val BSSGOLDENRAM_END: UInt

    val cpu: CpuType
    val programType: ProgramType
    var zeropage: Zeropage
    var golden: GoldenRam
    val libraryPath: Path?
    val customLauncher: List<String>?

    fun initializeMemoryAreas(compilerOptions: CompilationOptions)
    fun getFloatAsmBytes(num: Number): String

    fun convertFloatToBytes(num: Double): List<UByte>
    fun convertBytesToFloat(bytes: List<UByte>): Double

    fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path)
    fun isIOAddress(address: UInt): Boolean

    override fun encodeString(str: String, encoding: Encoding): List<UByte>
    override fun decodeString(bytes: Iterable<UByte>, encoding: Encoding): String
}
