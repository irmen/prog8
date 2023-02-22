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
    override val FLOAT_MEM_SIZE = 4             // 32-bits floating point
    override val PROGRAM_LOAD_ADDRESS = 0u      // not actually used

    override var ESTACK_LO = 0u                 // not actually used
    override var ESTACK_HI = 0u                 // not actually used
    override val BSSHIGHRAM_START = 0u          // not actually used
    override val BSSHIGHRAM_END = 0u            // not actually used
    override lateinit var zeropage: Zeropage    // not actually used
    override lateinit var golden: GoldenRam     // not actually used

    override fun getFloatAsmBytes(num: Number): String {
        // little endian binary representation
        val bits = num.toFloat().toBits().toUInt()
        val hexStr = bits.toString(16).padStart(8, '0')
        val parts = hexStr.chunked(2).map { "\$" + it }
        return parts.joinToString(", ")
    }

    override fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String> {
        return listOf("syslib")
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
