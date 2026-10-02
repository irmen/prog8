package prog8.code.target

import prog8.code.core.*
import prog8.code.target.encodings.Encoder
import prog8.code.target.zp.M68kZeropage
import java.nio.file.Path

/**
 * For now target a m68020 cpu
 * Eventually the goal is to be able to create programs for the Amiga A1200 (68020 cpu) or A500 (68000 cpu)
 */

class Qemu68kTarget: ICompilationTarget,
    IStringEncoding by Encoder(false),
    IMemSizer by NormalMemSizer(4u, 4u) {

    override val name = NAME
    override val supportsBankedCalls = false
    override val defaultEncoding = Encoding.ISO
    override val libraryPath = null
    override val customLauncher = emptyList<String>()
    override val additionalAssemblerOptions = emptyList<String>()
    override val defaultOutputType = OutputType.ELF
    override val defaultLauncherType = CbmPrgLauncherType.NONE

    companion object {
        const val NAME = "qemu68k"
    }

    override val cpu = CpuType.M68020

    override val FLOAT_MAX_POSITIVE = Float.MAX_VALUE.toDouble()
    override val FLOAT_MAX_NEGATIVE = -Float.MAX_VALUE.toDouble()
    override val FLOAT_MEM_SIZE = 4u
    override val POINTER_MEM_SIZE = 4u
    override val ARRAY_SIZE_LIMIT = 32768u
    override val PROGRAM_LOAD_ADDRESS = 0x10000u      
    override val PROGRAM_MEMTOP_ADDRESS = 0x00100000u       // TODO hardcoded at 1 Mb of RAM for now... it starts at $0

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

    override fun launchEmulator(selectedEmulator: Int, programFile: Path, quiet: Boolean) {
        if(selectedEmulator!=1) {
            System.err.println("The qemu68k target only supports the main emulator (Qemu).")
            return
        }
        if(!programFile.toFile().exists()) {
            System.err.println("No program file found: $programFile")
            return
        }
        val cpuStr = this.cpu.toString().lowercase()

        val cmd = listOf(
            "qemu-system-m68k",
            "-M", "virt",
            "-cpu", cpuStr,
            "-m", "1M",
            "-kernel", programFile.toString(),
            "-nographic"
        )
        if(!quiet) {
            println("Launching QEMU (press Ctrl-A X to exit)...")
        }
        val pb = ProcessBuilder(cmd).inheritIO()
        try {
            pb.start().waitFor()
        } catch (_: java.io.IOException) {
            System.err.println("Cannot launch qemu-system-m68k. Install it via your package manager, e.g.:")
            System.err.println("  sudo apt install qemu-system-m68k       # Debian/Ubuntu")
            System.err.println("  sudo pacman -S qemu-system-m68k         # Arch Linux")
            System.err.println("or build it from source: https://www.qemu.org/download/")
        }
    }

    override fun isIOAddress(address: UInt): Boolean = address>=0xff000000u

    override fun initializeMemoryAreas(compilerOptions: CompilationOptions) {
        zeropage = M68kZeropage(compilerOptions)
    }
}

