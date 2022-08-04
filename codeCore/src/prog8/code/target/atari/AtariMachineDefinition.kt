package prog8.code.target.atari

import prog8.code.core.*
import prog8.code.target.c64.normal6502instructions
import java.nio.file.Path


class AtariMachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU6502

    override val FLOAT_MAX_POSITIVE = 9.999999999e97
    override val FLOAT_MAX_NEGATIVE = -9.999999999e97
    override val FLOAT_MEM_SIZE = 6
    override val PROGRAM_LOAD_ADDRESS = 0x2000u

    // the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
    override var ESTACK_LO = 0x1a00u     //  $1a00-$1aff inclusive      // TODO
    override var ESTACK_HI = 0x1b00u     //  $1b00-$1bff inclusive      // TODO

    override lateinit var zeropage: Zeropage

    override fun getFloatAsmBytes(num: Number) = TODO("float asm bytes from number")

    override fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String> {
        return if (compilerOptions.output == OutputType.XEX)
            listOf("syslib")
        else
            emptyList()
    }

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

    override fun initializeZeropage(compilerOptions: CompilationOptions) {
        zeropage = AtariZeropage(compilerOptions)
    }

    override val opcodeNames = normal6502instructions
}
