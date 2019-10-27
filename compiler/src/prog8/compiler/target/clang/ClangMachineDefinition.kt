package prog8.compiler.target.clang

import prog8.compiler.CompilationOptions
import prog8.compiler.Zeropage
import prog8.compiler.target.IMachineDefinition
import prog8.compiler.target.c64.C64MachineDefinition

object ClangMachineDefinition: IMachineDefinition {
    override val FLOAT_MAX_NEGATIVE: Double = C64MachineDefinition.FLOAT_MAX_NEGATIVE
    override val FLOAT_MAX_POSITIVE: Double = C64MachineDefinition.FLOAT_MAX_POSITIVE
    override val FLOAT_MEM_SIZE: Int = C64MachineDefinition.FLOAT_MEM_SIZE
    override val opcodeNames: Set<String> = C64MachineDefinition.opcodeNames

    override fun getZeropage(compilerOptions: CompilationOptions): Zeropage = ClangZeropage(compilerOptions)

    class ClangZeropage(options: CompilationOptions) : Zeropage(options) {
        override val exitProgramStrategy: ExitProgramStrategy = ExitProgramStrategy.SYSTEM_RESET
        init {
            for (reserved in options.zpReserved)
                reserve(reserved)
        }
    }
}
