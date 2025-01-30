package prog8.code.target

import prog8.code.core.*
import prog8.code.target.encodings.Encoder
import prog8.code.target.zp.AtariZeropage
import java.nio.file.Path



class AtariTarget: ICompilationTarget, IStringEncoding by Encoder, IMemSizer by NormalMemSizer(FLOAT_MEM_SIZE) {
    override val name = NAME
    override val defaultEncoding = Encoding.ATASCII
    override val libraryPath = null
    override val customLauncher = null

    companion object {
        const val NAME = "atari"
        const val FLOAT_MEM_SIZE = 6
    }

    override val cpu = CpuType.CPU6502
    override val programType = ProgramType.ATARIXEX

    override val FLOAT_MAX_POSITIVE = 9.999999999e97
    override val FLOAT_MAX_NEGATIVE = -9.999999999e97
    override val FLOAT_MEM_SIZE = AtariTarget.FLOAT_MEM_SIZE
    override val STARTUP_CODE_RESERVED_SIZE = 20u
    override val PROGRAM_LOAD_ADDRESS = 0x2000u
    override val PROGRAM_MEMTOP_ADDRESS = 0xffffu  // TODO what's memtop?

    override val BSSHIGHRAM_START = 0u    // TODO
    override val BSSHIGHRAM_END = 0u      // TODO
    override val BSSGOLDENRAM_START = 0u  // TODO
    override val BSSGOLDENRAM_END = 0u    // TODO

    override lateinit var zeropage: Zeropage
    override lateinit var golden: GoldenRam

    override fun getFloatAsmBytes(num: Number) = TODO("atari float asm bytes from number")
    override fun convertFloatToBytes(num: Double): List<UByte> = TODO("atari float to bytes")
    override fun convertBytesToFloat(bytes: List<UByte>): Double = TODO("atari bytes to float")

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path) {
        val emulatorName: String
        val cmdline: List<String>
        when(selectedEmulator) {
            1 -> {
                emulatorName = "atari800"
                cmdline = listOf(emulatorName, "-xl", "-xl-rev", "2", "-nobasic", "-run", "${programNameWithPath}.xex")
            }
            2 -> {
                emulatorName = "altirra"
                cmdline = listOf("Altirra64.exe", "${programNameWithPath.normalize()}.xex")
            }
            else -> {
                System.err.println("Atari target only supports atari800 and altirra emulators.")
                return
            }
        }

        // TODO monlist?

        println("\nStarting Atari800XL emulator $emulatorName...")
        val processb = ProcessBuilder(cmdline).inheritIO()
        val process: Process = processb.start()
        process.waitFor()
    }

    override fun isIOAddress(address: UInt): Boolean = address==0u || address==1u || address in 0xd000u..0xdfffu        // TODO

    override fun initializeMemoryAreas(compilerOptions: CompilationOptions) {
        zeropage = AtariZeropage(compilerOptions)
        golden = GoldenRam(compilerOptions, UIntRange.EMPTY)
    }
}
