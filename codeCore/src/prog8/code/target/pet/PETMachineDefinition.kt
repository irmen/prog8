package prog8.code.target.pet

import prog8.code.core.*
import prog8.code.target.C64Target
import prog8.code.target.cbm.Mflpt5
import java.nio.file.Path


class PETMachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU6502

    override val FLOAT_MAX_POSITIVE = Mflpt5.FLOAT_MAX_POSITIVE
    override val FLOAT_MAX_NEGATIVE = Mflpt5.FLOAT_MAX_NEGATIVE
    override val FLOAT_MEM_SIZE = Mflpt5.FLOAT_MEM_SIZE
    override val PROGRAM_LOAD_ADDRESS = 0x0401u
    override val PROGRAM_MEMTOP_ADDRESS = 0x8000u

    override val BSSHIGHRAM_START = 0u
    override val BSSHIGHRAM_END = 0u
    override val BSSGOLDENRAM_START = 0u
    override val BSSGOLDENRAM_END = 0u

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
        if(selectedEmulator!=1) {
            System.err.println("The pet target only supports the main emulator (Vice).")
            return
        }

        println("\nStarting PET emulator...")
        val viceMonlist = C64Target.viceMonListName(programNameWithPath.toString())
        val cmdline = listOf("xpet", "-model", "4032", "-ramsize", "32", "-videosize", "40", "-silent", "-moncommands", viceMonlist,
                "-autostartprgmode", "1", "-autostart-warp", "-autostart", "${programNameWithPath}.prg")
        val processb = ProcessBuilder(cmdline).inheritIO()
        val process=processb.start()
        process.waitFor()
    }

    override fun isIOAddress(address: UInt): Boolean = address in 0xe800u..0xe8ffu

    override fun initializeMemoryAreas(compilerOptions: CompilationOptions) {
        zeropage = PETZeropage(compilerOptions)
        // there's no golden ram.
    }

}
