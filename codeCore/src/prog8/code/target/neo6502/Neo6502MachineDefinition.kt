package prog8.code.target.neo6502

import prog8.code.core.*
import java.nio.file.Path


class Neo6502MachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU65c02

    override val FLOAT_MAX_POSITIVE = 9.999999999e97
    override val FLOAT_MAX_NEGATIVE = -9.999999999e97
    override val FLOAT_MEM_SIZE = 6
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
