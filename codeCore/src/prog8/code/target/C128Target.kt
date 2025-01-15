package prog8.code.target

import prog8.code.core.CompilationOptions
import prog8.code.core.CpuType
import prog8.code.core.Encoding
import prog8.code.core.GoldenRam
import prog8.code.core.ICompilationTarget
import prog8.code.core.IMemSizer
import prog8.code.core.IStringEncoding
import prog8.code.core.ProgramType
import prog8.code.core.Zeropage
import prog8.code.target.zp.C128Zeropage
import prog8.code.target.encodings.Encoder
import java.nio.file.Path


class C128Target: ICompilationTarget, IStringEncoding by Encoder, IMemSizer by NormalMemSizer(Mflpt5.FLOAT_MEM_SIZE) {
    override val name = NAME
    override val defaultEncoding = Encoding.PETSCII
    override val libraryPath = null

    companion object {
        const val NAME = "c128"
    }


    override val cpu = CpuType.CPU6502
    override val programType = ProgramType.CBMPRG

    override val FLOAT_MAX_POSITIVE = Mflpt5.FLOAT_MAX_POSITIVE
    override val FLOAT_MAX_NEGATIVE = Mflpt5.FLOAT_MAX_NEGATIVE
    override val FLOAT_MEM_SIZE = Mflpt5.FLOAT_MEM_SIZE
    override val STARTUP_CODE_RESERVED_SIZE = 20u
    override val PROGRAM_LOAD_ADDRESS = 0x1c01u
    override val PROGRAM_MEMTOP_ADDRESS = 0xc000u

    override val BSSHIGHRAM_START = 0u    // TODO
    override val BSSHIGHRAM_END = 0u      // TODO
    override val BSSGOLDENRAM_START = 0u  // TODO
    override val BSSGOLDENRAM_END = 0u    // TODO

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
            System.err.println("The c128 target only supports the main emulator (Vice).")
            return
        }

        println("\nStarting C-128 emulator x128...")
        val viceMonlist = C64Target.viceMonListName(programNameWithPath.toString())
        val cmdline = listOf("x128", "-silent", "-moncommands", viceMonlist,
            "-autostartprgmode", "1", "-autostart-warp", "-autostart", "${programNameWithPath}.prg")
        val processb = ProcessBuilder(cmdline).inheritIO()
        val process: Process = processb.start()
        process.waitFor()
    }

    override fun isIOAddress(address: UInt): Boolean = address==0u || address==1u || address in 0xd000u..0xdfffu

    override fun initializeMemoryAreas(compilerOptions: CompilationOptions) {
        zeropage = C128Zeropage(compilerOptions)
        golden = GoldenRam(compilerOptions, UIntRange.EMPTY)    // TODO does the c128 have some of this somewhere?
    }

}
