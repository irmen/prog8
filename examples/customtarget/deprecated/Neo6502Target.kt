package prog8.code.target

import prog8.code.core.*
import prog8.code.target.encodings.Encoder
import prog8.code.target.zp.Neo6502Zeropage
import java.nio.file.Path


class Neo6502Target: ICompilationTarget, IStringEncoding by Encoder, IMemSizer by NormalMemSizer(FLOAT_MEM_SIZE) {
    override val name = NAME
    override val defaultEncoding = Encoding.ISO
    override val libraryPath = null
    override val customLauncher: List<String> = emptyList()
    override val additionalAssemblerOptions = null

    companion object {
        const val NAME = "neo"
        const val FLOAT_MEM_SIZE = 6
    }


    override val cpu = CpuType.CPU65C02
    override val programType = ProgramType.NEORAW

    override val FLOAT_MAX_POSITIVE = 9.999999999e97
    override val FLOAT_MAX_NEGATIVE = -9.999999999e97
    override val FLOAT_MEM_SIZE = Neo6502Target.FLOAT_MEM_SIZE
    override val STARTUP_CODE_RESERVED_SIZE = 20u
    override val PROGRAM_LOAD_ADDRESS = 0x0800u
    override val PROGRAM_MEMTOP_ADDRESS = 0xfc00u       // kernal starts here

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
        if(selectedEmulator!=1) {
            System.err.println("The neo target only supports the main emulator (neo).")
            return
        }

        val cmdline = listOf("neo", "${programNameWithPath}.bin@800", "cold")

        println("\nStarting Neo6502 emulator...")
        val processb = ProcessBuilder(cmdline).inheritIO()
        val process: Process = processb.start()
        process.waitFor()
    }

    override fun isIOAddress(address: UInt): Boolean = address in 0xff00u..0xff0fu

    override fun initializeMemoryAreas(compilerOptions: CompilationOptions) {
        zeropage = Neo6502Zeropage(compilerOptions)
        golden = GoldenRam(compilerOptions, UIntRange.EMPTY)
    }
}
