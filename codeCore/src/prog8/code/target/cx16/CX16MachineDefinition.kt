package prog8.code.target.cx16

import prog8.code.core.*
import prog8.code.target.C64Target
import prog8.code.target.cbm.Mflpt5
import java.nio.file.Path


class CX16MachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU65c02

    override val FLOAT_MAX_POSITIVE = Mflpt5.FLOAT_MAX_POSITIVE
    override val FLOAT_MAX_NEGATIVE = Mflpt5.FLOAT_MAX_NEGATIVE
    override val FLOAT_MEM_SIZE = Mflpt5.FLOAT_MEM_SIZE
    override val STARTUP_CODE_RESERVED_SIZE = 20u
    override val PROGRAM_LOAD_ADDRESS = 0x0801u
    override val PROGRAM_MEMTOP_ADDRESS = 0x9f00u

    override val BSSHIGHRAM_START = 0xa000u     // hiram bank 1, 8Kb, assumed to be active
    override val BSSHIGHRAM_END = 0xbfffu       // Rom starts at $c000
    override val BSSGOLDENRAM_START = 0x0400u
    override val BSSGOLDENRAM_END = 0x07ffu

    override lateinit var zeropage: Zeropage
    override lateinit var golden: GoldenRam

    override fun getFloatAsmBytes(num: Number) = Mflpt5.fromNumber(num).makeFloatFillAsm()

    override fun convertFloatToBytes(num: Double): List<UByte> {
        val m5 = Mflpt5.fromNumber(num)
        return listOf(m5.b0, m5.b1, m5.b2, m5.b3, m5.b4)
    }

    override fun convertBytesToFloat(bytes: List<UByte>): Double {
        require(bytes.size==5) { "need 5 bytes" }
        val m5 = Mflpt5(bytes[0], bytes[1], bytes[2], bytes[3], bytes[4])
        return m5.toDouble()
    }

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path) {
        val emulator: String
        val extraArgs: List<String>

        when(selectedEmulator) {
            1 -> {
                emulator = "x16emu"
                extraArgs = listOf("-debug")
            }
            2 -> {
                emulator = "box16"
                extraArgs = listOf("-sym", C64Target.viceMonListName(programNameWithPath.toString()))
            }
            else -> {
                System.err.println("Cx16 target only supports x16emu and box16 emulators.")
                return
            }
        }

        println("\nStarting Commander X16 emulator $emulator...")
        val cmdline = listOf(emulator, "-scale", "2", "-rtc", "-run", "-prg", "${programNameWithPath}.prg") + extraArgs
        val processb = ProcessBuilder(cmdline).inheritIO()
        processb.environment()["PULSE_LATENCY_MSEC"] = "10"
        val process: Process = processb.start()
        process.waitFor()
    }

    override fun isIOAddress(address: UInt): Boolean = address==0u || address==1u || address in 0x9f00u..0x9fffu

    override fun initializeMemoryAreas(compilerOptions: CompilationOptions) {
        zeropage = CX16Zeropage(compilerOptions)
        golden = GoldenRam(compilerOptions, 0x0400u until 0x0800u)
    }

}
