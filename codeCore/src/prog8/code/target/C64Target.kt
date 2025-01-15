package prog8.code.target

import prog8.code.core.*
import prog8.code.target.encodings.Encoder
import prog8.code.target.zp.C64Zeropage
import java.io.IOException
import java.nio.file.Path


class C64Target: ICompilationTarget, IStringEncoding by Encoder, IMemSizer by NormalMemSizer(Mflpt5.Companion.FLOAT_MEM_SIZE) {
    override val name = NAME
    override val defaultEncoding = Encoding.PETSCII
    override val libraryPath = null

    companion object {
        const val NAME = "c64"

        fun viceMonListName(baseFilename: String) = "$baseFilename.vice-mon-list"
    }


    override val cpu = CpuType.CPU6502
    override val programType = ProgramType.CBMPRG

    override val FLOAT_MAX_POSITIVE = Mflpt5.FLOAT_MAX_POSITIVE
    override val FLOAT_MAX_NEGATIVE = Mflpt5.FLOAT_MAX_NEGATIVE
    override val FLOAT_MEM_SIZE = Mflpt5.FLOAT_MEM_SIZE
    override val STARTUP_CODE_RESERVED_SIZE = 20u
    override val PROGRAM_LOAD_ADDRESS = 0x0801u
    override val PROGRAM_MEMTOP_ADDRESS = 0xcfe0u      // $a000  if floats are used
    // note that at $cfe0-$cfff are the 16 'virtual registers' R0-R15

    override val BSSHIGHRAM_START = 0xc000u
    override val BSSHIGHRAM_END = 0xcfdfu
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
            } catch(_: IOException) {
                continue  // try the next emulator executable
            }
            process.waitFor()
            break
        }
    }

    override fun isIOAddress(address: UInt): Boolean = address==0u || address==1u || address in 0xd000u..0xdfffu

    override fun initializeMemoryAreas(compilerOptions: CompilationOptions) {
        zeropage = C64Zeropage(compilerOptions)
        golden = GoldenRam(compilerOptions, 0xc000u until 0xd000u)
    }

}


val CompilationTargets = listOf(
    C64Target.NAME,
    C128Target.NAME,
    Cx16Target.NAME,
    PETTarget.NAME,
    AtariTarget.NAME,
    Neo6502Target.NAME,
    VMTarget.NAME
)

fun getCompilationTargetByName(name: String) = when(name.lowercase()) {
    C64Target.NAME -> C64Target()
    C128Target.NAME -> C128Target()
    Cx16Target.NAME -> Cx16Target()
    PETTarget.NAME -> PETTarget()
    AtariTarget.NAME -> AtariTarget()
    VMTarget.NAME -> VMTarget()
    Neo6502Target.NAME -> Neo6502Target()
    else -> throw IllegalArgumentException("invalid compilation target")
}
