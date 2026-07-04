package prog8.code.target

import prog8.code.core.*
import prog8.code.target.encodings.Encoder
import java.nio.file.Path

class Qemu68kTarget: ICompilationTarget,
    IStringEncoding by Encoder(false),
    IMemSizer by NormalMemSizer(FLOAT_MEM_SIZE) {

    override val name = NAME
    override val supportsBankedCalls = false
    override val defaultEncoding = Encoding.ISO
    override val libraryPath = null
    override val customLauncher = emptyList<String>()
    override val additionalAssemblerOptions = emptyList<String>()
    override val defaultOutputType = OutputType.RAW
    override val defaultLauncherType = CbmPrgLauncherType.NONE

    companion object {
        const val NAME = "qemu68k"
        const val FLOAT_MEM_SIZE = 4             // 32-bits double
    }

    override val cpu = CpuType.M68030

    override val FLOAT_MAX_POSITIVE = Float.MAX_VALUE.toDouble()
    override val FLOAT_MAX_NEGATIVE = -Float.MAX_VALUE.toDouble()
    override val FLOAT_MEM_SIZE = Qemu68kTarget.FLOAT_MEM_SIZE.toUInt()
    override val PROGRAM_LOAD_ADDRESS = 0x10000u      
    override val PROGRAM_MEMTOP_ADDRESS = 0xfffffffeu 

    override val BSSHIGHRAM_START = 0u          // not actually used
    override val BSSHIGHRAM_END = 0u            // not actually used
    override val BSSGOLDENRAM_START = 0u        // not actually used
    override val BSSGOLDENRAM_END = 0u          // not actually used
    override lateinit var zeropage: Zeropage    // not actually used

    override fun getFloatAsmBytes(num: Number): String {
        TODO("float asm bytes")
    }

    override fun convertFloatToBytes(num: Double): List<UByte> {
        TODO("convert float to bytes")
    }

    override fun convertBytesToFloat(bytes: List<UByte>): Double {
        require(bytes.size==4) { "need 4 bytes" }
        TODO("convert bytes to float")
    }

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path, quiet: Boolean) {
        if(selectedEmulator!=1) {
            System.err.println("The qemu68k target only supports the main emulator (Qemu).")
            return
        }
        val elfFile = programNameWithPath.resolveSibling("${programNameWithPath.fileName}.elf")
        val cmd = listOf(
            "qemu-system-m68k",
            "-M", "virt",
            "-cpu", "m68030",
            "-m", "128M",
            "-kernel", elfFile.toString(),
            "-nographic"
        )
        val pb = ProcessBuilder(cmd).inheritIO()
        if (quiet)
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.start().waitFor()
    }

    override fun isIOAddress(address: UInt): Boolean = address>=0xff000000u

    override fun initializeMemoryAreas(compilerOptions: CompilationOptions) {
        zeropage = M68kZeropage(compilerOptions)
    }
}


private class M68kZeropage(options: CompilationOptions): Zeropage(options) {
    override val SCRATCH_B1: UInt
        get() = throw IllegalStateException("m68k shouldn't use this zeropage variable")
    override val SCRATCH_REG: UInt
        get() = throw IllegalStateException("m68k shouldn't use this zeropage variable")
    override val SCRATCH_W1: UInt
        get() = throw IllegalStateException("m68k shouldn't use this zeropage variable")
    override val SCRATCH_W2: UInt
        get() = throw IllegalStateException("m68k shouldn't use this zeropage variable")
    override val SCRATCH_PTR: UInt
        get() = throw IllegalStateException("m68k shouldn't use this zeropage variable")
}
