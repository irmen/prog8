package prog8.code.target.virtual

import prog8.code.core.*
import java.nio.file.Path
import kotlin.io.path.isReadable
import kotlin.io.path.name
import kotlin.io.path.readText

class VirtualMachineDefinition: IMachineDefinition {

    override val cpu = CpuType.VIRTUAL

    override val FLOAT_MAX_POSITIVE = Float.MAX_VALUE.toDouble()
    override val FLOAT_MAX_NEGATIVE = -Float.MAX_VALUE.toDouble()
    override val FLOAT_MEM_SIZE = 8             // 64-bits double
    override val STARTUP_CODE_RESERVED_SIZE = 0u  // not actually used
    override val PROGRAM_LOAD_ADDRESS = 0u      // not actually used
    override val PROGRAM_MEMTOP_ADDRESS = 0xffffu  // not actually used

    override val BSSHIGHRAM_START = 0u          // not actually used
    override val BSSHIGHRAM_END = 0u            // not actually used
    override val BSSGOLDENRAM_START = 0u        // not actually used
    override val BSSGOLDENRAM_END = 0u          // not actually used
    override lateinit var zeropage: Zeropage    // not actually used
    override lateinit var golden: GoldenRam     // not actually used

    override fun getFloatAsmBytes(num: Number): String {
        // little endian binary representation
        val bits = num.toDouble().toBits().toULong()
        val hexStr = bits.toString(16).padStart(16, '0')
        val parts = hexStr.chunked(2).map { "\$" + it }
        return parts.joinToString(", ")
    }

    override fun convertFloatToBytes(num: Double): List<UByte> {
        val bits = num.toBits().toULong()
        val hexStr = bits.toString(16).padStart(16, '0')
        val parts = hexStr.chunked(2).map { it.toInt(16).toUByte() }
        return parts
    }

    override fun convertBytesToFloat(bytes: List<UByte>): Double {
        require(bytes.size==8) { "need 8 bytes" }
        val b0 = bytes[0].toLong() shl (8*7)
        val b1 = bytes[1].toLong() shl (8*6)
        val b2 = bytes[2].toLong() shl (8*5)
        val b3 = bytes[3].toLong() shl (8*4)
        val b4 = bytes[4].toLong() shl (8*3)
        val b5 = bytes[5].toLong() shl (8*2)
        val b6 = bytes[6].toLong() shl (8*1)
        val b7 = bytes[7].toLong() shl (8*0)
        return Double.fromBits(b0 or b1 or b2 or b3 or b4 or b5 or b6 or b7)
    }

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path) {
        println("\nStarting Virtual Machine...")
        // to not have external module dependencies in our own module, we launch the virtual machine via reflection
        val vm = Class.forName("prog8.vm.VmRunner").getDeclaredConstructor().newInstance() as IVirtualMachineRunner
        val filename = programNameWithPath.name
        if(programNameWithPath.isReadable()) {
            vm.runProgram(programNameWithPath.readText())
        } else {
            val withExt = programNameWithPath.resolveSibling("$filename.p8ir")
            if(withExt.isReadable())
                vm.runProgram(withExt.readText())
            else
                throw NoSuchFileException(withExt.toFile(), reason="not a .p8ir file")
        }
    }

    override fun isIOAddress(address: UInt): Boolean = false

    override fun initializeMemoryAreas(compilerOptions: CompilationOptions) {
        zeropage = VirtualZeropage(compilerOptions)
    }
}

interface IVirtualMachineRunner {
    fun runProgram(irSource: String)
}

private class VirtualZeropage(options: CompilationOptions): Zeropage(options) {
    override val SCRATCH_B1: UInt
        get() = throw IllegalStateException("virtual shouldn't use this zeropage variable")
    override val SCRATCH_REG: UInt
        get() = throw IllegalStateException("virtual shouldn't use this zeropage variable")
    override val SCRATCH_W1: UInt
        get() = throw IllegalStateException("virtual shouldn't use this zeropage variable")
    override val SCRATCH_W2: UInt
        get() = throw IllegalStateException("virtual shouldn't use this zeropage variable")

    override fun allocateCx16VirtualRegisters() { /* there is no actual zero page in this target to allocate thing in */ }
}
