package prog8.code.target.cx16

import prog8.code.core.*
import prog8.code.target.cbm.Mflpt5
import java.nio.file.Path


class CX16MachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU65c02

    override val FLOAT_MAX_POSITIVE = Mflpt5.FLOAT_MAX_POSITIVE
    override val FLOAT_MAX_NEGATIVE = Mflpt5.FLOAT_MAX_NEGATIVE
    override val FLOAT_MEM_SIZE = Mflpt5.FLOAT_MEM_SIZE
    override val PROGRAM_LOAD_ADDRESS = 0x0801u

    // the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
    override var ESTACK_LO = 0x0400u        //  $0400-$04ff inclusive
    override var ESTACK_HI = 0x0500u        //  $0500-$05ff inclusive
    override lateinit var zeropage: Zeropage
    override lateinit var golden: GoldenRam

    override fun getFloatAsmBytes(num: Number) = Mflpt5.fromNumber(num).makeFloatFillAsm()
    override fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String> {
        return if (compilerOptions.launcher == CbmPrgLauncherType.BASIC || compilerOptions.output == OutputType.PRG)
            listOf("syslib")
        else
            emptyList()
    }

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path) {
        val emulator: String
        val extraArgs: List<String>

        when(selectedEmulator) {
            1 -> {
                emulator = "x16emu"
                extraArgs = emptyList()
            }
            2 -> {
                emulator = "box16"
                extraArgs = listOf("-sym", viceMonListName(programNameWithPath.toString()))
            }
            else -> {
                System.err.println("Cx16 target only supports x16emu and box16 emulators.")
                return
            }
        }

        println("\nStarting Commander X16 emulator $emulator...")
        val cmdline = listOf(emulator, "-scale", "2", "-run", "-prg", "${programNameWithPath}.prg") + extraArgs
        val processb = ProcessBuilder(cmdline).inheritIO()
        processb.environment()["PULSE_LATENCY_MSEC"] = "10"
        val process: Process = processb.start()
        process.waitFor()
    }

    override fun isIOAddress(address: UInt): Boolean = address==0u || address==1u || address in 0x9f00u..0x9fffu

    override fun initializeMemoryAreas(compilerOptions: CompilationOptions) {
        zeropage = CX16Zeropage(compilerOptions)
        golden = GoldenRam(compilerOptions, 0x0600u until 0x0800u)
    }

}
