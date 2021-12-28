package prog8.codegen.target.c128

import prog8.ast.base.DataType
import prog8.codegen.target.c64.normal6502instructions
import prog8.codegen.target.cbm.Mflpt5
import prog8.codegen.target.cbm.viceMonListPostfix
import prog8.compilerinterface.*
import java.io.IOException
import java.nio.file.Path


class C128MachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU6502

    override val FLOAT_MAX_POSITIVE = Mflpt5.FLOAT_MAX_POSITIVE
    override val FLOAT_MAX_NEGATIVE = Mflpt5.FLOAT_MAX_NEGATIVE
    override val FLOAT_MEM_SIZE = Mflpt5.FLOAT_MEM_SIZE
    override val POINTER_MEM_SIZE = 2
    override val BASIC_LOAD_ADDRESS = 0x1c01u
    override val RAW_LOAD_ADDRESS = 0x1300u

    // the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
    override val ESTACK_LO = 0x1a00u     //  $1a00-$1aff inclusive
    override val ESTACK_HI = 0x1b00u     //  $1b00-$1bff inclusive

    override lateinit var zeropage: Zeropage

    override fun getFloat(num: Number) = Mflpt5.fromNumber(num)

    override fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String> {
        return if (compilerOptions.launcher == LauncherType.BASIC || compilerOptions.output == OutputType.PRG)
            listOf("syslib")
        else
            emptyList()
    }

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path) {
        if(selectedEmulator!=1) {
            System.err.println("The c128 target only supports the main emulator (Vice).")
            return
        }

        for(emulator in listOf("x128")) {
            println("\nStarting C-128 emulator $emulator...")
            val cmdline = listOf(emulator, "-silent", "-moncommands", "${programNameWithPath}.$viceMonListPostfix",
                    "-autostartprgmode", "1", "-autostart-warp", "-autostart", "${programNameWithPath}.prg")
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

    override fun isIOAddress(address: UInt): Boolean = address==0u || address==1u || address in 0xd000u..0xdfffu
    override fun getPreallocatedZeropageVars(): Map<String, Pair<UInt, DataType>> = emptyMap()

    override fun initializeZeropage(compilerOptions: CompilationOptions) {
        zeropage = C128Zeropage(compilerOptions)
    }

    override val opcodeNames = normal6502instructions
}
