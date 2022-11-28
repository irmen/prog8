package prog8.code.core

import java.nio.file.Path


enum class CpuType {
    CPU6502,
    CPU65c02,
    VIRTUAL
}

interface IMachineDefinition {
    val FLOAT_MAX_NEGATIVE: Double
    val FLOAT_MAX_POSITIVE: Double
    val FLOAT_MEM_SIZE: Int
    var ESTACK_LO: UInt
    var ESTACK_HI: UInt
    val PROGRAM_LOAD_ADDRESS : UInt
    var GOLDEN: UIntRange

    val opcodeNames: Set<String>
    var zeropage: Zeropage
    val cpu: CpuType

    fun initializeZeropage(compilerOptions: CompilationOptions)
    fun getFloatAsmBytes(num: Number): String

    fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String>
    fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path)
    fun isIOAddress(address: UInt): Boolean
    fun overrideEvalStack(evalStackBaseAddress: UInt) {
        require(evalStackBaseAddress and 255u == 0u)
        ESTACK_LO = evalStackBaseAddress
        ESTACK_HI = evalStackBaseAddress + 256u
        require(ESTACK_LO !in GOLDEN && ESTACK_HI !in GOLDEN) { "user-set ESTACK can't be in GOLDEN ram" }
    }

}
