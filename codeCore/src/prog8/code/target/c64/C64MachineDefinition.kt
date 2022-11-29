package prog8.code.target.c64

import prog8.code.core.*
import prog8.code.target.cbm.Mflpt5
import java.io.IOException
import java.nio.file.Path


class C64MachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU6502

    override val FLOAT_MAX_POSITIVE = Mflpt5.FLOAT_MAX_POSITIVE
    override val FLOAT_MAX_NEGATIVE = Mflpt5.FLOAT_MAX_NEGATIVE
    override val FLOAT_MEM_SIZE = Mflpt5.FLOAT_MEM_SIZE
    override val PROGRAM_LOAD_ADDRESS = 0x0801u

    // the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
    override var ESTACK_LO = 0xce00u     //  $ce00-$ceff inclusive
    override var ESTACK_HI = 0xcf00u     //  $ce00-$ceff inclusive
    override var GOLDEN = 0xc000u until ESTACK_LO

    override lateinit var zeropage: Zeropage

    override fun getFloatAsmBytes(num: Number) = Mflpt5.fromNumber(num).makeFloatFillAsm()

    override fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String> {
        return if (compilerOptions.launcher == CbmPrgLauncherType.BASIC || compilerOptions.output == OutputType.PRG)
            listOf("syslib")
        else
            emptyList()
    }

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path) {
        if(selectedEmulator!=1) {
            System.err.println("The c64 target only supports the main emulator (Vice).")
            return
        }

        for(emulator in listOf("x64sc", "x64")) {
            println("\nStarting C-64 emulator $emulator...")
            val viceMonlist = viceMonListName(programNameWithPath.toString())
            val cmdline = listOf(emulator, "-silent", "-moncommands", viceMonlist,
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

    override fun initializeZeropage(compilerOptions: CompilationOptions) {
        zeropage = C64Zeropage(compilerOptions)
    }

}
