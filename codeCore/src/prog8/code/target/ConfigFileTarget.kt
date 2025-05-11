package prog8.code.target

import prog8.code.core.*
import prog8.code.source.ImportFileSystem.expandTilde
import prog8.code.target.encodings.Encoder
import prog8.code.target.zp.ConfigurableZeropage
import java.io.IOException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.nameWithoutExtension


class ConfigFileTarget(
    override val name: String,
    override val defaultEncoding: Encoding,
    override val cpu: CpuType,
    override val PROGRAM_LOAD_ADDRESS: UInt,
    override val PROGRAM_MEMTOP_ADDRESS: UInt,
    override val STARTUP_CODE_RESERVED_SIZE: UInt,
    override val BSSHIGHRAM_START: UInt,
    override val BSSHIGHRAM_END: UInt,
    override val BSSGOLDENRAM_START: UInt,
    override val BSSGOLDENRAM_END: UInt,
    override val defaultOutputType: OutputType,
    override val libraryPath: Path,
    override val customLauncher: List<String>,
    override val additionalAssemblerOptions: String?,
    val ioAddresses: List<UIntRange>,
    val zpScratchB1: UInt,
    val zpScratchReg: UInt,
    val zpScratchW1: UInt,
    val zpScratchW2: UInt,
    val virtualregistersStart: UInt,
    val zpFullsafe: List<UIntRange>,
    val zpKernalsafe: List<UIntRange>,
    val zpBasicsafe: List<UIntRange>
): ICompilationTarget, IStringEncoding by Encoder(true), IMemSizer by NormalMemSizer(8) {

    companion object {

        private fun Properties.getString(property: String): String {
            val value = this.getProperty(property, null)
            if(value!=null)
                return value
            throw NoSuchElementException("string property '$property' not found in config file")
        }

        private fun Properties.getInteger(property: String): UInt {
            val value = this.getProperty(property, null)
            if(value!=null) return parseInt(value)
            throw NoSuchElementException("integer property '$property' not found in config file")
        }

        private fun parseInt(value: String): UInt {
            if(value.startsWith("0x"))
                return value.drop(2).toUInt(16)
            if(value.startsWith("$"))
                return value.drop(1).toUInt(16)
            if(value.startsWith("%"))
                return value.drop(1).toUInt(2)
            return value.toUInt()
        }

        private fun parseAddressRanges(key: String, props: Properties): List<UIntRange> {
            val rangesStr = props.getString(key)
            if(rangesStr.isBlank())
                return emptyList()
            val result = mutableListOf<UIntRange>()
            val ranges = rangesStr.split(",").map { it.trim() }
            for(r in ranges) {
                if ('-' in r) {
                    val (fromStr, toStr) = r.split("-")
                    val from = parseInt(fromStr.trim())
                    val to = parseInt(toStr.trim())
                    result.add(from..to)
                } else {
                    val address = parseInt(r)
                    result.add(address..address)
                }
            }
            return result
        }

        fun fromConfigFile(configfile: Path): ConfigFileTarget {
            val props = Properties()
            props.load(configfile.inputStream())

            val cpuString = props.getString("cpu").uppercase()
            val cpuType = try {
                CpuType.valueOf(cpuString)
            } catch (_: IllegalArgumentException) {
                CpuType.valueOf("CPU$cpuString")
            }
            val ioAddresses = parseAddressRanges("io_regions", props)
            val zpFullsafe = parseAddressRanges("zp_fullsafe", props)
            val zpKernalsafe = parseAddressRanges("zp_kernalsafe", props)
            val zpBasicsafe = parseAddressRanges("zp_basicsafe", props)

            val libraryPath = expandTilde(Path(props.getString("library")))
            if(!libraryPath.isDirectory())
                throw IOException("invalid library path: $libraryPath")

            val customLauncherStr = props.getProperty("custom_launcher_code", null)
            val customLauncher =
                if(customLauncherStr?.isNotBlank()==true)
                    (customLauncherStr+"\n").lines().map { it.trimEnd() }
                else emptyList()
            val assemblerOptionsStr = props.getProperty("assembler_options", "").trim()
            val assemblerOptions = assemblerOptionsStr.ifBlank { null }

            val outputTypeString = props.getProperty("output_type", "PRG")
            val defaultOutputType = OutputType.valueOf(outputTypeString.uppercase())

            return ConfigFileTarget(
                configfile.nameWithoutExtension,
                Encoding.entries.first { it.prefix==props.getString("encoding") },
                cpuType,
                props.getInteger("load_address"),
                props.getInteger("memtop"),
                0u,         // used only in a very specific error condition check in a certain scenario...
                props.getInteger("bss_highram_start"),
                props.getInteger("bss_highram_end"),
                props.getInteger("bss_goldenram_start"),
                props.getInteger("bss_goldenram_end"),
                defaultOutputType,
                libraryPath,
                customLauncher,
                assemblerOptions,
                ioAddresses,
                props.getInteger("zp_scratch_b1"),
                props.getInteger("zp_scratch_reg"),
                props.getInteger("zp_scratch_w1"),
                props.getInteger("zp_scratch_w2"),
                props.getInteger("virtual_registers"),
                zpFullsafe,
                zpKernalsafe,
                zpBasicsafe,
            )
        }
    }

    // TODO floats are not yet supported here, just enter some values
    override val FLOAT_MAX_POSITIVE = 9.999999999e97
    override val FLOAT_MAX_NEGATIVE = -9.999999999e97
    override val FLOAT_MEM_SIZE = 8

    override lateinit var zeropage: Zeropage
    override lateinit var golden: GoldenRam     // TODO this is not yet used

    override fun getFloatAsmBytes(num: Number) = TODO("floats")
    override fun convertFloatToBytes(num: Double): List<UByte> = TODO("floats")
    override fun convertBytesToFloat(bytes: List<UByte>): Double = TODO("floats")
    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path, quiet: Boolean) {
        throw IllegalArgumentException("Custom compiler target cannot automatically launch an emulator. Do this manually.")
    }

    override fun isIOAddress(address: UInt): Boolean = ioAddresses.any { address in it }

    override fun initializeMemoryAreas(compilerOptions: CompilationOptions) {
        zeropage = ConfigurableZeropage(
            zpScratchB1, zpScratchReg, zpScratchW1, zpScratchW2,
            virtualregistersStart,
            zpBasicsafe,
            zpKernalsafe,
            zpFullsafe,
            compilerOptions
        )
        // note: there's no golden ram yet
    }
}
