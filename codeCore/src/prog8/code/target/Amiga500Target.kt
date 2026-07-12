package prog8.code.target

import prog8.code.core.*
import prog8.code.target.encodings.Encoder
import java.nio.file.Path


class Amiga500Target: ICompilationTarget,
    IStringEncoding by Encoder(false),
    IMemSizer by NormalMemSizer(4, pointerSize = 4) {

    override val name = NAME
    override val supportsBankedCalls = true
    override val defaultEncoding = Encoding.ISO
    override val libraryPath = null
    override val customLauncher = emptyList<String>()
    override val additionalAssemblerOptions = emptyList<String>()
    override val defaultOutputType = OutputType.AMIGAHUNK
    override val defaultLauncherType = CbmPrgLauncherType.NONE

    companion object {
        const val NAME = "amiga500"
        
        /** needed to map bank numbers to libraries in extsub/CALLFAR library calls */
        val LibraryNumbers = mapOf(
             1 to "exec",
             2 to "dos",
             3 to "graphics",
             4 to "intuition",
             5 to "gadtools",
             6 to "layers",
             7 to "asl",
             8 to "console",
             9 to "utility",
            10 to "expansion",
            11 to "icon",
            12 to "wb",
            13 to "diskfont",
            14 to "iffparse",
            15 to "input",
            16 to "keymap",
            17 to "locale",
            18 to "timer",
            19 to "lowlevel",
            20 to "mathffp",
            21 to "mathieeesingbas",
            22 to "mathieeesingtrans",
            23 to "mathieeedoubbas",
            24 to "mathieeedoubtrans",
            25 to "mathtrans",
            26 to "nonvolatile",
            27 to "realtime",
            28 to "translator",
            29 to "rexxsyslib",
            30 to "commodities",
            31 to "datatypes",
            32 to "disk",
            33 to "amigaguide",
            34 to "arexx",
            35 to "battclock",
            36 to "battmem"
        )
    }

    override val cpu = CpuType.M68000

    override val FLOAT_MAX_POSITIVE = Float.MAX_VALUE.toDouble()
    override val FLOAT_MAX_NEGATIVE = -Float.MAX_VALUE.toDouble()
    override val FLOAT_MEM_SIZE = 4u
    override val POINTER_MEM_SIZE = 4u
    override val PROGRAM_LOAD_ADDRESS = 0u      // amiga executables are relocatable
    override val PROGRAM_MEMTOP_ADDRESS = 0xfffffffeu       // not actually used

    override val BSSHIGHRAM_START = 0u          // not used
    override val BSSHIGHRAM_END = 0u            // not used
    override val BSSGOLDENRAM_START = 0u        // not used    
    override val BSSGOLDENRAM_END = 0u          // not used
    override lateinit var zeropage: Zeropage

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
            System.err.println("The amiga500 target only supports the main emulator (Amitools's vamos).")
            return
        }
        val cpuStr = when(this.cpu) {
            CpuType.M68000 -> "68000"
            CpuType.M68020 -> "68020"
            else -> error("invalid cpu type")
        }
        val exeFile = programNameWithPath.resolveSibling("${programNameWithPath.fileName}")
        val cmd = 
            listOf(
                "vamos",
                "--cpu", cpuStr,
                exeFile.toString(),
            )
        println("Launching Amitools's Vamos...")
        val pb = ProcessBuilder(cmd).inheritIO()
        if (quiet)
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.start().waitFor()
    }

    override fun isIOAddress(address: UInt): Boolean = false        // TODO add amiga I/O ranges

    override fun initializeMemoryAreas(compilerOptions: CompilationOptions) {
        zeropage = AmigaZeropage(compilerOptions)
    }
}


private class AmigaZeropage(options: CompilationOptions): Zeropage(options) {
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
