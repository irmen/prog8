package prog8.codegen.target.atari

import prog8.codegen.target.c64.normal6502instructions
import prog8.compilerinterface.*
import java.io.IOException
import java.nio.file.Path


class AtariMachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU6502

    override val FLOAT_MAX_POSITIVE = 9.999999999e97
    override val FLOAT_MAX_NEGATIVE = -9.999999999e97
    override val FLOAT_MEM_SIZE = 6
    override val BASIC_LOAD_ADDRESS = 0x2000u

    // the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
    override val ESTACK_LO = 0x1a00u     //  $1a00-$1aff inclusive      // TODO
    override val ESTACK_HI = 0x1b00u     //  $1b00-$1bff inclusive      // TODO

    override lateinit var zeropage: Zeropage

    override fun getFloat(num: Number) = TODO("float from number")

    override fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String> {
        return if (compilerOptions.launcher == LauncherType.CBMBASIC || compilerOptions.output == OutputType.PRG)
            listOf("syslib")
        else
            emptyList()
    }

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path) {
        if(selectedEmulator!=1) {
            System.err.println("The atari target only supports the main emulator (atari800).")
            return
        }

        for(emulator in listOf("atari800")) {
            println("\nStarting Atari800XL emulator $emulator...")
            val cmdline = listOf(emulator, "-xl", "-nobasic", "-run", "${programNameWithPath}.xex")
            val processb = ProcessBuilder(cmdline).inheritIO()
            val process: Process
            try {
                process=processb.start()
            } catch(x: IOException) {
                continue  // try the next emulator executable
            }
            process.waitFor()
            break
        }
    }

    override fun isIOAddress(address: UInt): Boolean = address==0u || address==1u || address in 0xd000u..0xdfffu        // TODO

    override fun initializeZeropage(compilerOptions: CompilationOptions) {
        zeropage = AtariZeropage(compilerOptions)
    }

    override val opcodeNames = normal6502instructions
}
