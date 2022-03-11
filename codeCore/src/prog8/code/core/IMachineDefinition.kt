package prog8.code.core

import java.nio.file.Path


interface IMachineFloat {
    fun toDouble(): Double
    fun makeFloatFillAsm(): String
}

enum class CpuType {
    CPU6502,
    CPU65c02
}

interface IMachineDefinition {
    val FLOAT_MAX_NEGATIVE: Double
    val FLOAT_MAX_POSITIVE: Double
    val FLOAT_MEM_SIZE: Int
    val ESTACK_LO: UInt
    val ESTACK_HI: UInt
    val PROGRAM_LOAD_ADDRESS : UInt

    val opcodeNames: Set<String>
    var zeropage: Zeropage
    val cpu: CpuType

    fun initializeZeropage(compilerOptions: CompilationOptions)
    fun getFloat(num: Number): IMachineFloat

    fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String>
    fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path)
    fun isIOAddress(address: UInt): Boolean
}
