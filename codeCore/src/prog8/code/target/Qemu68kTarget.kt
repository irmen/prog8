package prog8.code.target

import prog8.code.core.*
import prog8.code.target.encodings.Encoder
import java.nio.file.Path

/**
 * For now target a m68020 cpu
 * Eventually the goal is to be able to create programs for the Amiga A1200 (68020 cpu) or A500 (68000 cpu)
 */

class Qemu68kTarget: ICompilationTarget,
    IStringEncoding by Encoder(false),
    IMemSizer by NormalMemSizer(4, pointerSize = 4) {

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

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path, quiet: Boolean) {
        if(selectedEmulator!=1) {
            System.err.println("The qemu68k target only supports the main emulator (Qemu).")
            return
        }
        val elfFile = programNameWithPath.resolveSibling("${programNameWithPath.fileName}.elf")
        val binFile = programNameWithPath.resolveSibling("${programNameWithPath.fileName}.bin")
        val isElf = elfFile.toFile().exists()
        val cpuStr = this.cpu.toString().lowercase()

        val cmd = if (isElf) {
            listOf(
                "qemu-system-m68k",
                "-M", "virt",
                "-cpu", cpuStr,
                "-m", "1M",
                "-kernel", elfFile.toString(),
                "-nographic"
            )
        } else if (binFile.toFile().exists()) {
            val loadAddr = PROGRAM_LOAD_ADDRESS.toInt()
            listOf(
                "qemu-system-m68k",
                "-M", "virt",
                "-cpu", cpuStr,
                "-m", "1M",
                "-device", "loader,file=${binFile},addr=0x${loadAddr.toString(16)},cpu-num=0",
                "-nographic"
            )
        } else {
            System.err.println("No .elf or .bin file found for ${programNameWithPath.fileName}")
            return
        }
        val launchMsg = if (isElf) "ELF" else "raw binary"
        println("Launching QEMU (press Ctrl-A X to exit)... (from $launchMsg)")
        val pb = ProcessBuilder(cmd).inheritIO()
        if (quiet)
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
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
