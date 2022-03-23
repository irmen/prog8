package prog8.code.target.virtual

import prog8.code.core.CompilationOptions
import prog8.code.core.CpuType
import prog8.code.core.IMachineDefinition
import prog8.code.core.Zeropage
import prog8.vm.Assembler
import prog8.vm.Memory
import prog8.vm.VirtualMachine
import java.io.File
import java.nio.file.Path


class VirtualMachineDefinition: IMachineDefinition {

    override val cpu = CpuType.VIRTUAL

    override val FLOAT_MAX_POSITIVE = Float.MAX_VALUE.toDouble()
    override val FLOAT_MAX_NEGATIVE = -Float.MAX_VALUE.toDouble()
    override val FLOAT_MEM_SIZE = 4
    override val PROGRAM_LOAD_ADDRESS = 0u      // not actually used

    override val ESTACK_LO = 0u                 // not actually used
    override val ESTACK_HI = 0u                 // not actually used

    override lateinit var zeropage: Zeropage     // not actually used

    override fun getFloat(num: Number) = TODO("float from number")

    override fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String> {
        return listOf("syslib")
    }

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path) {
        println("\nStarting Virtual Machine...")
        val source = File("$programNameWithPath.p8virt").readText()
        val (memsrc, programsrc) = source.split("------PROGRAM------".toRegex(), 2)
        val memory = Memory()
        val assembler = Assembler()
        assembler.initializeMemory(memsrc, memory)
        val program = assembler.assembleProgram(programsrc)
        val vm = VirtualMachine(memory, program)
        vm.run(throttle = true)
    }

    override fun isIOAddress(address: UInt): Boolean = false

    override fun initializeZeropage(compilerOptions: CompilationOptions) {}

    override val opcodeNames = emptySet<String>()
}
