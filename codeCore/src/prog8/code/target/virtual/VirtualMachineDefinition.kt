package prog8.code.target.virtual

import prog8.code.core.CompilationOptions
import prog8.code.core.CpuType
import prog8.code.core.IMachineDefinition
import prog8.code.core.Zeropage
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

    override lateinit var zeropage: Zeropage     // not actually used

    override fun getFloatAsmBytes(num: Number) = TODO("float asm bytes from number")

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

    override fun initializeZeropage(compilerOptions: CompilationOptions) {}

    override val opcodeNames = emptySet<String>()
}

interface IVirtualMachineRunner {
    fun runProgram(irSource: String)
}
