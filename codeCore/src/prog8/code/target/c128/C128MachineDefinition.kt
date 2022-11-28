package prog8.code.target.c128

import prog8.code.core.*
import prog8.code.target.c64.normal6502instructions
import prog8.code.target.cbm.Mflpt5
import java.nio.file.Path


class C128MachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU6502

    override val FLOAT_MAX_POSITIVE = Mflpt5.FLOAT_MAX_POSITIVE
    override val FLOAT_MAX_NEGATIVE = Mflpt5.FLOAT_MAX_NEGATIVE
    override val FLOAT_MEM_SIZE = Mflpt5.FLOAT_MEM_SIZE
    override val PROGRAM_LOAD_ADDRESS = 0x1c01u

    // the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
    override var ESTACK_LO = 0x1a00u     //  $1a00-$1aff inclusive
    override var ESTACK_HI = 0x1b00u     //  $1b00-$1bff inclusive
    override var GOLDEN = UIntRange.EMPTY      // TODO does the c128 have some of this somewhere?

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
            System.err.println("The c128 target only supports the main emulator (Vice).")
            return
        }

        println("\nStarting C-128 emulator x128...")
        val viceMonlist = viceMonListName(programNameWithPath.toString())
        val cmdline = listOf("x128", "-silent", "-moncommands", viceMonlist,
                "-autostartprgmode", "1", "-autostart-warp", "-autostart", "${programNameWithPath}.prg")
        val processb = ProcessBuilder(cmdline).inheritIO()
        val process: Process = processb.start()
        process.waitFor()
    }

    override fun isIOAddress(address: UInt): Boolean = address==0u || address==1u || address in 0xd000u..0xdfffu

    override fun initializeZeropage(compilerOptions: CompilationOptions) {
        zeropage = C128Zeropage(compilerOptions)
    }

    override val opcodeNames = normal6502instructions
}
