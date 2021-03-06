package prog8.compiler.target

import prog8.compiler.CompilationOptions
import prog8.compiler.Zeropage


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
    val POINTER_MEM_SIZE: Int
    val ESTACK_LO: Int
    val ESTACK_HI: Int
    val BASIC_LOAD_ADDRESS : Int
    val RAW_LOAD_ADDRESS : Int

    val opcodeNames: Set<String>
    var zeropage: Zeropage
    val cpu: CpuType

    fun initializeZeropage(compilerOptions: CompilationOptions)
    fun getFloat(num: Number): IMachineFloat

    fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String>
    fun launchEmulator(programName: String)
    fun isRegularRAMaddress(address: Int): Boolean
}
